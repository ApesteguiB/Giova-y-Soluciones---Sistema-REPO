package pe.giova.document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.document.api.DocumentDtos.DocumentMetadata;
import pe.giova.document.DocumentRepository.StoredDocument;

public class DocumentStorageService {
    private final DocumentRepository repository;
    private final DocumentValidator validator;
    private final Path root;

    public DocumentStorageService(DocumentRepository repository, DocumentValidator validator, Path root) {
        this.repository = repository;
        this.validator = validator;
        this.root = root.toAbsolutePath().normalize();
    }

    @Transactional
    public DocumentMetadata store(MultipartFile file, DocumentKind kind, WorkerPrincipal actor) {
        if (file == null) {
            throw new DocumentValidationException("El archivo es obligatorio");
        }
        try {
            return store(kind, file.getOriginalFilename(), file.getContentType(), file.getInputStream(),
                    file.getSize(), actor);
        } catch (IOException exception) {
            throw new DocumentStorageException("No se pudo leer el archivo recibido", exception);
        }
    }

    @Transactional
    public DocumentMetadata store(DocumentKind kind, String originalFilename, String contentType,
                                  InputStream content, long declaredSize, WorkerPrincipal actor) {
        requireActor(actor);
        if (content == null) {
            throw new DocumentValidationException("El contenido del archivo es obligatorio");
        }
        if (declaredSize > validator.maxBytes()) {
            throw new DocumentValidationException("El archivo supera el límite permitido");
        }
        Path temporary = null;
        Path destination = null;
        try {
            Files.createDirectories(root);
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new DocumentStorageException("La raíz de documentos no es un directorio");
            }
            temporary = Files.createTempFile(root, ".upload-", ".part");
            long size = copyAndHash(content, temporary, validator.maxBytes());
            DocumentValidator.ValidatedDocument validated = validator.validate(kind, originalFilename, contentType,
                    temporary, size);
            String publicId = UUID.randomUUID().toString();
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            String storageKey = "tenants/" + actor.tenantId() + "/documents/" + kind.storageSegment() + "/" + today.getYear() + "/"
                    + String.format("%02d", today.getMonthValue()) + "/" + publicId + "." + validated.extension();
            destination = resolve(storageKey);
            Files.createDirectories(destination.getParent());
            moveIntoPlace(temporary, destination);
            temporary = null;
            Instant now = Instant.now();
            StoredDocument document = new StoredDocument(0L, publicId, kind, storageKey, validated.originalName(),
                    validated.mediaType(), validated.extension(), validated.sizeBytes(), sha256(destination),
                    actor.id(), true, 0L, now, now);
            repository.insert(document);
            registerRollbackCleanup(destination);
            return toMetadata(document);
        } catch (DocumentValidationException | DocumentStorageException exception) {
            deleteQuietly(temporary);
            deleteQuietly(destination);
            throw exception;
        } catch (RuntimeException exception) {
            deleteQuietly(temporary);
            deleteQuietly(destination);
            throw exception;
        } catch (IOException exception) {
            deleteQuietly(temporary);
            deleteQuietly(destination);
            throw new DocumentStorageException("No se pudo almacenar el documento", exception);
        }
    }

    public DocumentDownload openForDownload(String publicId, WorkerPrincipal actor) {
        return openForDownload(publicId, actor, false);
    }

    /**
     * Used by a domain module after it has already checked ownership of the
     * parent entity (for example, a seller's own quotation).
     */
    public DocumentDownload openForDownload(String publicId, WorkerPrincipal actor, boolean relationshipAuthorized) {
        requireActor(actor);
        if (publicId == null || publicId.isBlank()) {
            throw new DocumentNotFoundException("Documento no encontrado");
        }
        StoredDocument document = repository.findByPublicId(publicId)
                .orElseThrow(() -> new DocumentNotFoundException("Documento no encontrado"));
        if (!document.active()) {
            throw new DocumentNotFoundException("Documento no encontrado");
        }
        if (!relationshipAuthorized && !canRead(document, actor)) {
            throw new DocumentAccessDeniedException("No tiene permiso para descargar este documento");
        }
        Path path = resolve(document.storageKey());
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) != document.sizeBytes()) {
                throw new DocumentNotFoundException("Documento no encontrado");
            }
        } catch (IOException exception) {
            throw new DocumentStorageException("No se pudo comprobar el documento", exception);
        }
        return new DocumentDownload(document, new FileSystemResource(path));
    }

    Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new DocumentStorageException("La clave de almacenamiento es inválida");
        }
        Path relative;
        try {
            relative = Path.of(storageKey);
        } catch (RuntimeException exception) {
            throw new DocumentStorageException("La clave de almacenamiento es inválida", exception);
        }
        if (relative.isAbsolute()) {
            throw new DocumentStorageException("La clave de almacenamiento debe ser relativa");
        }
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new DocumentStorageException("La clave de almacenamiento está fuera de la raíz");
        }
        return resolved;
    }

    private long copyAndHash(InputStream content, Path temporary, long maxBytes) throws IOException {
        long total = 0;
        byte[] buffer = new byte[8192];
        try (InputStream input = content; var output = Files.newOutputStream(temporary)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                total += read;
                if (total > maxBytes) {
                    throw new DocumentValidationException("El archivo supera el límite permitido");
                }
                output.write(buffer, 0, read);
            }
        }
        return total;
    }

    private static void moveIntoPlace(Path temporary, Path destination) throws IOException {
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, destination);
        } catch (FileAlreadyExistsException exception) {
            throw new DocumentStorageException("La clave generada ya existe", exception);
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no disponible", exception);
        }
    }

    private void registerRollbackCleanup(Path destination) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) deleteQuietly(destination);
            }
        });
    }

    private static boolean canRead(StoredDocument document, WorkerPrincipal actor) {
        RoleCode role = actor.roleCode();
        return role == RoleCode.ADMIN || role == RoleCode.ACCOUNTANT || document.createdByWorkerId() == actor.id();
    }

    private static void requireActor(WorkerPrincipal actor) {
        if (actor == null || actor.roleCode() == null || actor.tenantId() == null) {
            throw new DocumentAccessDeniedException("Se requiere una sesión válida");
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A failed cleanup is reconciled by the read-only storage check in P30.
        }
    }

    private static DocumentMetadata toMetadata(StoredDocument document) {
        return new DocumentMetadata(document.publicId(), document.documentKind().name(), document.originalName(),
                document.mediaType(), document.extension(), document.sizeBytes(), document.sha256Hex(),
                document.createdByWorkerId(), document.active(), document.version(), document.createdAt(),
                document.updatedAt());
    }

    public record DocumentDownload(StoredDocument document, Resource resource) {
    }
}

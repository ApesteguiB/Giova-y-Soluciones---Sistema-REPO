package pe.giova.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;
import pe.giova.document.DocumentRepository.StoredDocument;

class DocumentStorageServiceTest {
    @TempDir
    Path temp;

    private final DocumentRepository repository = mock(DocumentRepository.class);

    @Test
    void storesPdfWithRelativeKeyAndSha256() throws Exception {
        DocumentStorageService service = service(1024 * 1024);
        MockMultipartFile file = new MockMultipartFile("file", "..\\quote.pdf", "application/pdf",
                "%PDF-1.7\ncontent".getBytes(StandardCharsets.US_ASCII));

        var result = service.store(file, DocumentKind.QUOTATION_PDF, principal(8, RoleCode.SELLER));

        assertEquals("quote.pdf", result.originalName());
        assertTrue(result.sha256Hex().matches("[0-9a-f]{64}"));
        assertEquals(1, Files.walk(temp).filter(Files::isRegularFile).count());
        assertTrue(Files.walk(temp).anyMatch(path -> path.toString().endsWith(".pdf")));
        ArgumentCaptor<StoredDocument> stored = ArgumentCaptor.forClass(StoredDocument.class);
        verify(repository).insert(stored.capture());
        assertTrue(stored.getValue().storageKey().startsWith("tenants/3/documents/"));
    }

    @Test
    void rejectsInvalidUploadWithoutLeavingTemporaryFile() throws Exception {
        DocumentStorageService service = service(1024);
        MockMultipartFile file = new MockMultipartFile("file", "bad.pdf", "application/pdf", "not a pdf".getBytes());

        assertThrows(DocumentValidationException.class,
                () -> service.store(file, DocumentKind.OTHER_PDF, principal(8, RoleCode.SELLER)));
        assertEquals(0, Files.walk(temp).filter(Files::isRegularFile).count());
    }

    @Test
    void removesMovedFileWhenDatabaseInsertFails() throws Exception {
        DocumentStorageService service = service(1024);
        doThrow(new IllegalStateException("forced rollback")).when(repository).insert(any());
        MockMultipartFile file = new MockMultipartFile("file", "rollback.pdf", "application/pdf",
                "%PDF-1.7\ncontent".getBytes(StandardCharsets.US_ASCII));

        assertThrows(IllegalStateException.class,
                () -> service.store(file, DocumentKind.OTHER_PDF, principal(8, RoleCode.SELLER)));
        assertEquals(0, Files.walk(temp).filter(Files::isRegularFile).count());
    }

    @Test
    void allowsOwnerAndAdminButRejectsAnotherSeller() throws Exception {
        Path stored = temp.resolve("other-pdf/2026/09/doc.pdf");
        Files.createDirectories(stored.getParent());
        byte[] bytes = "%PDF-1.7\ncontent".getBytes(StandardCharsets.US_ASCII);
        Files.write(stored, bytes);
        StoredDocument document = new StoredDocument(1, "doc-id", DocumentKind.OTHER_PDF,
                "other-pdf/2026/09/doc.pdf", "doc.pdf", "application/pdf", "pdf", bytes.length,
                "hash", 8, true, 0, Instant.now(), Instant.now());
        when(repository.findByPublicId("doc-id")).thenReturn(Optional.of(document));
        DocumentStorageService service = service(1024);

        assertTrue(service.openForDownload("doc-id", principal(8, RoleCode.SELLER)).resource().exists());
        assertTrue(service.openForDownload("doc-id", principal(2, RoleCode.ADMIN)).resource().exists());
        assertThrows(DocumentAccessDeniedException.class,
                () -> service.openForDownload("doc-id", principal(9, RoleCode.SELLER)));
    }

    @Test
    void returnsNotFoundWhenMetadataExistsButFileIsMissing() {
        StoredDocument document = new StoredDocument(1, "missing", DocumentKind.OTHER_PDF,
                "other-pdf/2026/09/missing.pdf", "missing.pdf", "application/pdf", "pdf", 10,
                "hash", 8, true, 0, Instant.now(), Instant.now());
        when(repository.findByPublicId("missing")).thenReturn(Optional.of(document));
        DocumentStorageService service = service(1024);

        assertThrows(DocumentNotFoundException.class,
                () -> service.openForDownload("missing", principal(8, RoleCode.SELLER)));
    }

    private DocumentStorageService service(long maxBytes) {
        return new DocumentStorageService(repository, new DocumentValidator(maxBytes), temp);
    }

    private static WorkerPrincipal principal(long id, RoleCode role) {
        WorkerRepository.WorkerRecord worker = new WorkerRepository.WorkerRecord(id,
                role.name().toLowerCase() + "@example.test", role.label(), "hash", true, null);
        WorkerRepository.Membership membership = new WorkerRepository.Membership(1L, 3L, "TENANT-3",
                "Empresa de prueba", role);
        return new WorkerPrincipal(worker, java.util.List.of(membership), membership);
    }
}

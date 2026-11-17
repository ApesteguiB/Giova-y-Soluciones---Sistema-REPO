package pe.giova.document;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import pe.giova.admin.NotFoundException;

/** Reads only raster logos for the active tenant and strips metadata by re-encoding as PNG. */
public final class TenantLogoStorage {
    private static final long MAX_LOGO_BYTES = 5L * 1024 * 1024;
    private static final int MAX_LOGO_DIMENSION = 4096;
    private static final long MAX_LOGO_PIXELS = 16_777_216L;
    private final Path root;

    public TenantLogoStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** Validates a raster upload, strips metadata, and stores a generated PNG key under one tenant. */
    public String store(long tenantId, byte[] upload) {
        if (tenantId < 1 || upload == null || upload.length < 1 || upload.length > MAX_LOGO_BYTES) {
            throw invalidUpload();
        }
        BufferedImage image;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            if (input == null) throw invalidUpload();
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalidUpload();
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg")) throw invalidUpload();
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_LOGO_DIMENSION || height > MAX_LOGO_DIMENSION
                        || (long) width * height > MAX_LOGO_PIXELS) throw invalidUpload();
                image = reader.read(0);
                if (image == null) throw invalidUpload();
            } finally {
                reader.dispose();
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalidUpload();
        }

        byte[] png;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) throw invalidUpload();
            png = output.toByteArray();
        } catch (IOException exception) {
            throw invalidUpload();
        }
        if (png.length < 1 || png.length > MAX_LOGO_BYTES) throw invalidUpload();

        String key = "tenants/" + tenantId + "/branding/logo-" + UUID.randomUUID() + ".png";
        Path file = resolveForWrite(tenantId, key);
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            rejectSymlinkPath(file);
            temporary = Files.createTempFile(file.getParent(), ".logo-", ".tmp");
            Files.write(temporary, png, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            rejectSymlinkPath(temporary);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file);
            }
            return key;
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo guardar el logo", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // A stray temp file is never referenced by the database or served by the API.
                }
            }
        }
    }

    public Logo read(long tenantId, String storageKey) {
        Path file = resolve(tenantId, storageKey);
        try {
            long bytes = Files.size(file);
            if (bytes < 1 || bytes > MAX_LOGO_BYTES) throw missing();
            try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
                if (input == null) throw missing();
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw missing();
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    String filename = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    if ((filename.endsWith(".png") && !format.equals("png"))
                            || ((filename.endsWith(".jpg") || filename.endsWith(".jpeg")) && !format.equals("jpeg"))) {
                        throw missing();
                    }
                    if (!format.equals("png") && !format.equals("jpeg")) throw missing();
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > MAX_LOGO_DIMENSION || height > MAX_LOGO_DIMENSION
                            || (long) width * height > MAX_LOGO_PIXELS) throw missing();
                    BufferedImage image = reader.read(0);
                    if (image == null) throw missing();
                    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                        if (!ImageIO.write(image, "png", output)) throw missing();
                        return new Logo("image/png", output.toByteArray());
                    }
                } finally {
                    reader.dispose();
                }
            }
        } catch (NotFoundException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw missing();
        }
    }

    private Path resolve(long tenantId, String storageKey) {
        if (tenantId < 1 || storageKey == null || storageKey.contains("\\") || storageKey.contains("..")) {
            throw missing();
        }
        String prefix = "tenants/" + tenantId + "/branding/logo-";
        if (!storageKey.startsWith(prefix)) throw missing();
        String name = storageKey.substring(prefix.length());
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || !(name.substring(dot + 1).equalsIgnoreCase("png")
                || name.substring(dot + 1).equalsIgnoreCase("jpg")
                || name.substring(dot + 1).equalsIgnoreCase("jpeg"))) throw missing();
        try {
            String uuid = name.substring(0, dot);
            if (!UUID.fromString(uuid).toString().equalsIgnoreCase(uuid)) throw missing();
            Path relative = Path.of(storageKey);
            if (relative.isAbsolute()) throw missing();
            Path file = root.resolve(relative).normalize();
            if (!file.startsWith(root)) throw missing();
            rejectSymlinkPath(file);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw missing();
            return file;
        } catch (IllegalArgumentException exception) {
            throw missing();
        }
    }

    private Path resolveForWrite(long tenantId, String storageKey) {
        if (tenantId < 1 || storageKey == null || storageKey.contains("\\") || storageKey.contains("..")) {
            throw invalidUpload();
        }
        Path relative = Path.of(storageKey);
        Path file = root.resolve(relative).normalize();
        if (relative.isAbsolute() || !file.startsWith(root)
                || !storageKey.startsWith("tenants/" + tenantId + "/branding/logo-")) {
            throw invalidUpload();
        }
        rejectSymlinkPath(file.getParent());
        return file;
    }

    private void rejectSymlinkPath(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path cursor = absolute.getRoot();
        for (Path part : absolute) {
            cursor = cursor.resolve(part);
            if (Files.isSymbolicLink(cursor)) throw invalidUpload();
        }
    }

    private static IllegalArgumentException invalidUpload() {
        return new IllegalArgumentException("El logo debe ser una imagen PNG o JPEG válida de hasta 5 MiB");
    }

    private static NotFoundException missing() {
        return new NotFoundException("Logo no encontrado");
    }

    public record Logo(String mediaType, byte[] bytes) {}
}

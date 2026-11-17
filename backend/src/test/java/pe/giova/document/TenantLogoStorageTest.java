package pe.giova.document;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.giova.admin.NotFoundException;

class TenantLogoStorageTest {
    @TempDir Path root;

    @Test
    void servesReencodedPngOnlyFromTheRequestedTenantDirectory() throws Exception {
        String key = storeLogo(2L);
        TenantLogoStorage storage = new TenantLogoStorage(root);

        TenantLogoStorage.Logo logo = storage.read(2L, key);
        BufferedImage decoded = ImageIO.read(logo.bytes().length == 0 ? null
                : new java.io.ByteArrayInputStream(logo.bytes()));

        assertTrue(logo.mediaType().equals("image/png"));
        assertTrue(decoded != null);
        assertArrayEquals(new int[]{2, 2}, new int[]{decoded.getWidth(), decoded.getHeight()});
        assertThrows(NotFoundException.class, () -> storage.read(3L, key));
    }

    @Test
    void rejectsExecutableAndTraversalKeys() throws Exception {
        String executableKey = "tenants/2/branding/logo-" + UUID.randomUUID() + ".png";
        Path executable = root.resolve(executableKey);
        Files.createDirectories(executable.getParent());
        Files.writeString(executable, "<svg onload=alert(1)></svg>");

        TenantLogoStorage storage = new TenantLogoStorage(root);
        assertThrows(NotFoundException.class, () -> storage.read(2L, executableKey));
        assertThrows(NotFoundException.class, () -> storage.read(2L,
                "tenants/2/branding/logo-../../outside.png"));
    }

    @Test
    void storesOnlyValidatedRasterImagesAsGeneratedPngKeys() throws Exception {
        TenantLogoStorage storage = new TenantLogoStorage(root);
        byte[] upload = imageBytes(20, 10, "jpeg");

        String key = storage.store(7L, upload);
        TenantLogoStorage.Logo served = storage.read(7L, key);
        BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(served.bytes()));

        assertTrue(key.matches("tenants/7/branding/logo-[0-9a-fA-F-]{36}\\.png"));
        assertTrue(served.mediaType().equals("image/png"));
        assertArrayEquals(new int[] {20, 10}, new int[] {decoded.getWidth(), decoded.getHeight()});
        assertThrows(IllegalArgumentException.class, () -> storage.store(7L, "<svg/>".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> storage.store(7L, imageBytesUnchecked(4097, 1)));
    }

    private String storeLogo(long tenantId) throws Exception {
        String key = "tenants/" + tenantId + "/branding/logo-" + UUID.randomUUID() + ".png";
        Path file = root.resolve(key);
        Files.createDirectories(file.getParent());
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, Color.ORANGE.getRGB());
        ImageIO.write(image, "png", file.toFile());
        return key;
    }

    private static byte[] imageBytes(int width, int height, String format) throws Exception {
        return imageBytesUnchecked(width, height, format);
    }

    private static byte[] imageBytesUnchecked(int width, int height) {
        return imageBytesUnchecked(width, height, "png");
    }

    private static byte[] imageBytesUnchecked(int width, int height, String format) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            int type = format.equals("jpeg") ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
            ImageIO.write(new BufferedImage(width, height, type), format, output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}

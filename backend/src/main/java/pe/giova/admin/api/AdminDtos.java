package pe.giova.admin.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class AdminDtos {
    private AdminDtos() {
    }

    public record BrandInput(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 80) String slug,
            @Size(max = 2048) @Pattern(regexp = "https?://.+", message = "logoUrl debe ser una URL http(s)") String logoUrl,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "displayColor debe ser hexadecimal") String displayColor,
            boolean featured,
            @Min(0) Integer displayOrder) {
    }

    public record BrandUpdate(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 2048) @Pattern(regexp = "https?://.+", message = "logoUrl debe ser una URL http(s)") String logoUrl,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "displayColor debe ser hexadecimal") String displayColor,
            boolean featured,
            @Min(0) Integer displayOrder,
            @Min(0) long version) {
    }

    public record CategoryInput(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 80) String slug,
            @NotBlank @Size(max = 40) String iconKey,
            @Min(0) Integer displayOrder) {
    }

    public record CategoryUpdate(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 40) String iconKey,
            @Min(0) Integer displayOrder,
            @Min(0) long version) {
    }

    public record ProductInput(
            @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 100) String slug,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,80}") String sku,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 2000) String description,
            @NotNull @Min(1) Long brandId,
            @NotNull @Min(1) Long categoryId,
            @Size(max = 40) String badge,
            @Size(max = 2048) @Pattern(regexp = "https?://.+", message = "imageUrl debe ser una URL http(s)") String imageUrl,
            @Min(0) Integer displayOrder) {
    }

    public record ProductUpdate(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,80}") String sku,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 2000) String description,
            @NotNull @Min(1) Long brandId,
            @NotNull @Min(1) Long categoryId,
            @Size(max = 40) String badge,
            @Size(max = 2048) @Pattern(regexp = "https?://.+", message = "imageUrl debe ser una URL http(s)") String imageUrl,
            @Min(0) Integer displayOrder,
            @Min(0) long version) {
    }

    public record StatusInput(@NotNull Boolean active, @Min(0) long version) {
    }

    public record BrandView(long id, String name, String slug, String logoUrl, String displayColor,
                            boolean featured, int displayOrder, boolean active, long version,
                            Instant createdAt, Instant updatedAt) {
    }

    public record CategoryView(long id, String name, String slug, String iconKey, int displayOrder,
                               boolean active, long version, Instant createdAt, Instant updatedAt) {
    }

    public record ProductView(long id, String slug, String sku, String name, String description,
                              long brandId, String brandName, long categoryId, String categoryName,
                              String badge, String imageUrl, int displayOrder, boolean active, long version,
                              Instant createdAt, Instant updatedAt) {
    }

    public record AdminPage<T>(List<T> items, long totalItems, int page, int size, int totalPages) {
    }
}

package pe.giova.warranty.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import pe.giova.warranty.WarrantyDocumentRole;
import pe.giova.warranty.WarrantyStatus;

public final class WarrantyDtos {
    private WarrantyDtos() {}

    public record Input(@NotBlank String customerPublicId, @Size(max = 36) String purchaseOrderPublicId,
                        Integer purchaseOrderLineNumber, Long productId, @Size(max = 120) String serialNumber,
                        @NotNull LocalDate startsOn, @NotNull LocalDate endsOn,
                        @Size(max = 1500) String conditions, @Size(max = 1500) String notes) {}

    public record Update(@NotBlank String customerPublicId, @Size(max = 36) String purchaseOrderPublicId,
                         Integer purchaseOrderLineNumber, Long productId, @Size(max = 120) String serialNumber,
                         @NotNull LocalDate startsOn, @NotNull LocalDate endsOn,
                         @Size(max = 1500) String conditions, @Size(max = 1500) String notes,
                         @NotNull Long version) {}

    public record StatusChange(@NotNull WarrantyStatus targetStatus, @NotNull Long version,
                               @Size(max = 500) String reason) {}

    public record DocumentView(String publicId, WarrantyDocumentRole role, String originalName,
                               String mediaType, String extension, long sizeBytes, String sha256Hex,
                               long version, Instant createdAt, String downloadUrl) {}

    public record View(String publicId, String customerPublicId, String customerName,
                       String purchaseOrderPublicId, String orderNumber, Integer purchaseOrderLineNumber,
                       Long productId, String productSku, String productName, String serialNumber,
                       LocalDate startsOn, LocalDate endsOn, WarrantyStatus status, String conditions,
                       String notes, long version, Instant createdAt, Instant updatedAt,
                       List<DocumentView> documents) {}

    public record Page(List<View> items, long totalItems, int page, int size, int totalPages) {}
}

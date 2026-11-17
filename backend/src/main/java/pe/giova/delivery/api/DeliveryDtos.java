package pe.giova.delivery.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import pe.giova.delivery.DeliveryDocumentRole;
import pe.giova.delivery.DeliveryStatus;

public final class DeliveryDtos {
    private DeliveryDtos() {}

    public record Input(@NotBlank @Size(max = 40) String deliveryNumber,
                        @NotBlank @Size(max = 36) String purchaseOrderPublicId,
                        Instant deliveredAt, @Size(max = 160) String receivedByName,
                        @Size(max = 40) String receivedByDocument, @Size(max = 1500) String notes) {}

    public record Update(@NotBlank @Size(max = 40) String deliveryNumber,
                         @NotBlank @Size(max = 36) String purchaseOrderPublicId,
                         Instant deliveredAt, @Size(max = 160) String receivedByName,
                         @Size(max = 40) String receivedByDocument, @Size(max = 1500) String notes,
                         @NotNull Long version) {}

    public record StatusChange(@NotNull DeliveryStatus targetStatus, @NotNull Long version,
                               Instant deliveredAt, @Size(max = 500) String reason) {}

    public record DocumentView(String publicId, DeliveryDocumentRole role, String originalName,
                               String mediaType, String extension, long sizeBytes, String sha256Hex,
                               long version, Instant createdAt, String downloadUrl) {}

    public record View(String publicId, String deliveryNumber, String purchaseOrderPublicId,
                       String orderNumber, String customerPublicId, String customerName, Instant deliveredAt,
                       String receivedByName, String receivedByDocument, DeliveryStatus status, String notes,
                       long version, Instant createdAt, Instant updatedAt, List<DocumentView> documents) {}

    public record Page(List<View> items, long totalItems, int page, int size, int totalPages) {}
}

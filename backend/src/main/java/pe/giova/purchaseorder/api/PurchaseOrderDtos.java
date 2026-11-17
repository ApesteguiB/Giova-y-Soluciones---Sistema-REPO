package pe.giova.purchaseorder.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import pe.giova.purchaseorder.PurchaseOrderDocumentRole;
import pe.giova.purchaseorder.PurchaseOrderStatus;

public final class PurchaseOrderDtos {
    private PurchaseOrderDtos() {}

    public record ItemInput(Long productId, @NotBlank @Size(max = 80) String sku,
                            @NotBlank @Size(max = 500) String description, @Size(max = 30) String unit,
                            @NotNull @DecimalMin("0.0001") BigDecimal quantity,
                            @NotNull @DecimalMin("0.00") BigDecimal unitPrice) {}

    public record Input(@NotBlank @Size(max = 40) String orderNumber, @NotBlank String customerPublicId,
                         @Size(max = 36) String quotationPublicId, @Size(max = 36) String invoicePublicId,
                         @NotNull LocalDate issueDate, LocalDate expectedDeliveryDate, LocalDate deliveredDate,
                         @NotBlank String currencyCode, @NotNull @DecimalMin("0.000001") BigDecimal exchangeRateToPen,
                         @NotNull Boolean includesTax, @NotNull @DecimalMin("0.00") BigDecimal taxRate,
                         @Size(max = 1500) String notes, List<@Valid ItemInput> items) {}

    public record Update(@NotBlank @Size(max = 40) String orderNumber, @NotBlank String customerPublicId,
                         @Size(max = 36) String quotationPublicId, @Size(max = 36) String invoicePublicId,
                         @NotNull LocalDate issueDate, LocalDate expectedDeliveryDate, LocalDate deliveredDate,
                         @NotBlank String currencyCode, @NotNull @DecimalMin("0.000001") BigDecimal exchangeRateToPen,
                         @NotNull Boolean includesTax, @NotNull @DecimalMin("0.00") BigDecimal taxRate,
                         @Size(max = 1500) String notes, @NotNull Long version, List<@Valid ItemInput> items) {}

    public record StatusChange(@NotNull PurchaseOrderStatus targetStatus, @NotNull Long version,
                               LocalDate deliveredDate, @Size(max = 500) String reason) {}

    public record ItemView(int lineNumber, Long productId, String sku, String description, String unit,
                           BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineSubtotal,
                           BigDecimal lineTax, BigDecimal lineTotal) {}

    public record DocumentView(String publicId, PurchaseOrderDocumentRole role, String originalName,
                               String mediaType, String extension, long sizeBytes, String sha256Hex,
                               long version, Instant createdAt, String downloadUrl) {}

    public record View(String publicId, String orderNumber, String customerPublicId, String customerName,
                       String customerDocumentType, String customerDocumentNumber, String quotationPublicId,
                       String quotationNumber, String invoicePublicId, String invoiceNumber,
                       long responsibleWorkerId, String responsibleWorkerDisplayName, PurchaseOrderStatus status,
                       LocalDate issueDate, LocalDate expectedDeliveryDate, LocalDate deliveredDate,
                       String currencyCode, BigDecimal exchangeRateToPen, boolean includesTax, BigDecimal taxRate,
                       BigDecimal subtotalAmount, BigDecimal taxAmount, BigDecimal totalAmount, String notes,
                       long version, Instant createdAt, Instant updatedAt, List<ItemView> items,
                       List<DocumentView> documents) {}

    public record Page(List<View> items, long totalItems, int page, int size, int totalPages) {}
}

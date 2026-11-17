package pe.giova.quotation.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import pe.giova.quotation.OutcomeSource;
import pe.giova.quotation.ProcurementType;
import pe.giova.quotation.QuotationOutcome;
import pe.giova.quotation.QuotationStatus;

public final class QuotationDtos {
    private QuotationDtos() {}

    public record ItemInput(
            Long productId,
            @NotBlank @Size(max = 80) String sku,
            @NotBlank @Size(max = 500) String description,
            @Size(max = 30) String unit,
            @NotNull @DecimalMin("0.0001") BigDecimal quantity,
            @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
            /** Precio con el que compite esta línea. Opcional: no siempre se conoce. */
            @DecimalMin("0.00") BigDecimal competitorPrice,
            /** Costo de compra usado para armar el precio. Opcional. */
            @DecimalMin("0.00") BigDecimal purchasePrice) {}

    public record Input(
            @NotBlank String customerPublicId,
            @NotBlank String procurementType,
            @NotBlank String currencyCode,
            @NotNull @DecimalMin("0.000001") BigDecimal exchangeRateToPen,
            @NotNull Boolean includesTax,
            @NotNull @DecimalMin("0.00") BigDecimal taxRate,
            LocalDate validUntil,
            @Min(1) Integer deliveryTermDays,
            @Min(0) Integer warrantyMonths,
            @Size(max = 1500) String notes,
            @NotEmpty List<@Valid ItemInput> items) {}

    public record Update(
            @NotBlank String customerPublicId,
            @NotBlank String procurementType,
            @NotBlank String currencyCode,
            @NotNull @DecimalMin("0.000001") BigDecimal exchangeRateToPen,
            @NotNull Boolean includesTax,
            @NotNull @DecimalMin("0.00") BigDecimal taxRate,
            LocalDate validUntil,
            @Min(1) Integer deliveryTermDays,
            @Min(0) Integer warrantyMonths,
            @Size(max = 1500) String notes,
            @NotNull Long version,
            @NotEmpty List<@Valid ItemInput> items) {}

    public record Issue(@NotNull Long version) {}

    public record StatusChange(@NotNull QuotationStatus targetStatus, @NotNull Long version,
                               @Size(max = 500) String reason) {}

    /**
     * Cierre del proceso. El motivo es obligatorio cuando el resultado es
     * negativo, y `source` distingue si el cierre lo decidió la entidad o
     * nosotros: sin eso no se puede aprender de los rechazos.
     */
    public record OutcomeChange(
            @NotBlank String outcome,
            @Size(max = 500) String reason,
            String source,
            @NotNull Long version) {}

    /** Orden de Compra de Acuerdo Marco. Puede llegar días después de ganar. */
    public record OcamInput(
            @Size(max = 60) String ocamNumber,
            LocalDate ocamDate,
            @NotNull Long version) {}

    public record ItemView(int lineNumber, Long productId, String sku, String description, String unit,
                           BigDecimal quantity, BigDecimal unitPrice, BigDecimal competitorPrice,
                           BigDecimal purchasePrice, BigDecimal lineSubtotal, BigDecimal lineTax,
                           BigDecimal lineTotal) {}

    public record View(String publicId, String quotationNumber, String customerPublicId,
                       String customerName, String customerDocumentType, String customerDocumentNumber,
                       long ownerWorkerId, String ownerDisplayName, QuotationStatus status,
                       ProcurementType procurementType, QuotationOutcome outcome, String outcomeReason,
                       OutcomeSource outcomeSource, Instant outcomeAt, Instant issuedAt,
                       LocalDate validUntil, Integer deliveryTermDays, Integer warrantyMonths,
                       String ocamNumber, LocalDate ocamDate, String currencyCode,
                       BigDecimal exchangeRateToPen, boolean includesTax, BigDecimal taxRate,
                       BigDecimal subtotalAmount, BigDecimal taxAmount, BigDecimal totalAmount,
                       String notes, long version, Instant createdAt, Instant updatedAt,
                       String issuedPdfPublicId, List<ItemView> items) {}

    public record Page(List<View> items, long totalItems, int page, int size, int totalPages) {}
}

package pe.giova.price.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PriceReferenceDtos {
    private PriceReferenceDtos() {
    }

    public record PriceReferenceInput(
            @NotNull @Min(1) Long productId,
            @Size(max = 180) String supplierName,
            @Size(max = 300) String sourceReference,
            @NotNull @Size(min = 3, max = 3) String currencyCode,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal unitPrice,
            boolean includesTax,
            @NotNull @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") BigDecimal taxRate,
            @DecimalMin(value = "0.000001") BigDecimal exchangeRateToPen,
            @NotNull Instant effectiveAt,
            @Size(max = 500) String notes) {
    }

    public record PriceReferenceView(
            long id,
            long productId,
            String productSku,
            String productName,
            long recordedByWorkerId,
            String recordedByDisplayName,
            String supplierName,
            String sourceReference,
            String currencyCode,
            BigDecimal unitPrice,
            boolean includesTax,
            BigDecimal taxRate,
            BigDecimal exchangeRateToPen,
            Instant effectiveAt,
            String notes,
            Instant createdAt) {
    }

    public record PriceReferencePage(List<PriceReferenceView> items, long totalItems, int page, int size,
                                     int totalPages) {
    }
}

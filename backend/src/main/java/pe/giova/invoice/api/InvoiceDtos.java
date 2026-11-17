package pe.giova.invoice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import pe.giova.invoice.InvoiceDocumentRole;
import pe.giova.invoice.InvoiceStatus;
import pe.giova.invoice.InvoiceType;

public final class InvoiceDtos {
    private InvoiceDtos() {}

    public record ItemInput(
            Long productId,
            @NotBlank @Size(max = 80) String sku,
            @NotBlank @Size(max = 500) String description,
            @Size(max = 30) String unit,
            @NotNull @DecimalMin("0.0001") BigDecimal quantity,
            @NotNull @DecimalMin("0.00") BigDecimal unitPrice) {}

    public record Input(
            @NotBlank @Size(max = 40) String invoiceNumber,
            @NotNull InvoiceType invoiceType,
            @NotBlank String customerPublicId,
            @Size(max = 36) String quotationPublicId,
            @NotNull LocalDate issueDate,
            LocalDate dueDate,
            @NotBlank String currencyCode,
            @NotNull @DecimalMin("0.000001") BigDecimal exchangeRateToPen,
            @NotNull Boolean includesTax,
            @NotNull @DecimalMin("0.00") BigDecimal taxRate,
            @Size(max = 1500) String notes,
            List<@Valid ItemInput> items) {}

    public record Update(
            @NotBlank @Size(max = 40) String invoiceNumber,
            @NotNull InvoiceType invoiceType,
            @NotBlank String customerPublicId,
            @Size(max = 36) String quotationPublicId,
            @NotNull LocalDate issueDate,
            LocalDate dueDate,
            @NotBlank String currencyCode,
            @NotNull @DecimalMin("0.000001") BigDecimal exchangeRateToPen,
            @NotNull Boolean includesTax,
            @NotNull @DecimalMin("0.00") BigDecimal taxRate,
            @Size(max = 1500) String notes,
            @NotNull Long version,
            List<@Valid ItemInput> items) {}

    public record StatusChange(@NotNull InvoiceStatus targetStatus, @NotNull Long version,
                               @Size(max = 500) String reason) {}

    public record ItemView(int lineNumber, Long productId, String sku, String description, String unit,
                           BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineSubtotal,
                           BigDecimal lineTax, BigDecimal lineTotal) {}

    public record DocumentView(String publicId, InvoiceDocumentRole role, String originalName,
                               String mediaType, String extension, long sizeBytes, String sha256Hex,
                               long version, Instant createdAt, String downloadUrl) {}

    public record View(String publicId, String invoiceNumber, InvoiceType invoiceType,
                       String customerPublicId, String customerName, String customerDocumentType,
                       String customerDocumentNumber, String quotationPublicId, String quotationNumber,
                       Long quotationOwnerWorkerId, long registeredByWorkerId, String registeredByDisplayName,
                       InvoiceStatus status, LocalDate issueDate, LocalDate dueDate, String currencyCode,
                       BigDecimal exchangeRateToPen, boolean includesTax, BigDecimal taxRate,
                       BigDecimal subtotalAmount, BigDecimal taxAmount, BigDecimal totalAmount, String notes,
                       long version, Instant createdAt, Instant updatedAt, List<ItemView> items,
                       List<DocumentView> documents) {}

    public record Page(List<View> items, long totalItems, int page, int size, int totalPages) {}

    // ------------------------------------------------------------------------
    // Importación desde XML (UBL 2.1 / SUNAT)
    // ------------------------------------------------------------------------

    /** Una línea leída del XML, con lo que el sistema pudo emparejar del catálogo. */
    public record XmlLinePreview(int lineNumber, String sku, String description, String unit,
                                 BigDecimal quantity, BigDecimal unitPrice, Long matchedProductId,
                                 String matchedProductName, boolean newProduct) {}

    /**
     * Lo que se leyó del archivo antes de guardar nada.
     *
     * @param duplicate     true si ya existe una factura registrada con ese número
     * @param customerFound true si el RUC del adquirente ya está en la cartera de clientes
     */
    public record XmlPreview(String invoiceNumber, InvoiceType invoiceType, LocalDate issueDate,
                             LocalDate dueDate, String currencyCode, String supplierName,
                             String supplierDocumentNumber, String customerName,
                             String customerDocumentNumber, String customerPublicId,
                             boolean customerFound, boolean duplicate, String duplicatePublicId,
                             BigDecimal declaredTotal, BigDecimal calculatedTotal,
                             int newProductCount, List<XmlLinePreview> lines) {}

    /** Resultado de una importación que sí se guardó. */
    public record XmlImportResult(View invoice, boolean customerCreated, int priceReferencesCreated,
                                  List<String> warnings) {}
}

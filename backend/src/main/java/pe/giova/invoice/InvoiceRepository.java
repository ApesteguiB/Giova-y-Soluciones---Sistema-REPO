package pe.giova.invoice;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import pe.giova.admin.NotFoundException;
import pe.giova.auth.TenantScope;

public class InvoiceRepository {
    private static final String HEADER = """
            SELECT i.id, i.public_id, i.invoice_number, i.invoice_type, i.customer_id,
                   c.public_id AS customer_public_id, c.business_name AS customer_name,
                   c.document_type AS customer_document_type, c.document_number AS customer_document_number,
                   q.public_id AS quotation_public_id, q.quotation_number,
                   q.owner_worker_id AS quotation_owner_worker_id, i.registered_by_worker_id,
                   w.display_name AS registered_by_display_name, i.status, i.issue_date, i.due_date,
                   i.currency_code, i.exchange_rate_to_pen, i.includes_tax, i.tax_rate,
                   i.subtotal_amount, i.tax_amount, i.total_amount, i.notes, i.version,
                   i.created_at, i.updated_at
            FROM invoices i
            JOIN customers c ON c.id = i.customer_id AND c.tenant_id=i.tenant_id
            JOIN workers w ON w.id = i.registered_by_worker_id
            LEFT JOIN quotations q ON q.id = i.quotation_id AND q.tenant_id=i.tenant_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public InvoiceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageData find(String query, String status, String type, String customerPublicId,
                         String quotationPublicId, Long quotationOwnerId, int page, int size) {
        String where = """
                WHERE i.tenant_id=:tenantId
                  AND (:pattern IS NULL OR LOWER(CONCAT_WS(' ', i.invoice_number, c.business_name,
                       c.document_number, q.quotation_number)) LIKE :pattern ESCAPE '\\\\')
                  AND (:status IS NULL OR i.status = :status)
                  AND (:type IS NULL OR i.invoice_type = :type)
                  AND (:customerPublicId IS NULL OR c.public_id = :customerPublicId)
                  AND (:quotationPublicId IS NULL OR q.public_id = :quotationPublicId)
                  AND (:quotationOwnerId IS NULL OR q.owner_worker_id = :quotationOwnerId)
                """;
        var params = baseParams(query, status, type, customerPublicId, quotationPublicId, quotationOwnerId)
                .addValue("pageSize", size)
                .addValue("offset", (long) page * size);
        List<InvoiceHeader> headers = jdbc.query(HEADER + where
                        + " ORDER BY i.updated_at DESC, i.id DESC LIMIT :pageSize OFFSET :offset",
                TenantScope.withTenant(params), (rs, row) -> mapHeader(rs));
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM invoices i JOIN customers c ON c.id=i.customer_id AND c.tenant_id=i.tenant_id "
                + "LEFT JOIN quotations q ON q.id=i.quotation_id AND q.tenant_id=i.tenant_id " + where, TenantScope.withTenant(params), Long.class);
        return new PageData(headers.stream().map(this::aggregate).toList(), total, page, size,
                total == 0 ? 0 : (int) Math.ceil((double) total / size));
    }

    public Optional<InvoiceViewData> findByPublicId(String publicId, Long quotationOwnerId) {
        String where = " WHERE i.tenant_id=:tenantId AND i.public_id = :publicId AND (:quotationOwnerId IS NULL OR q.owner_worker_id = :quotationOwnerId)";
        var params = new MapSqlParameterSource().addValue("publicId", publicId)
                .addValue("quotationOwnerId", quotationOwnerId);
        return jdbc.query(HEADER + where, TenantScope.withTenant(params), (rs, row) -> mapHeader(rs)).stream()
                .findFirst().map(this::aggregate);
    }

    public Optional<InvoiceHeader> lockByPublicId(String publicId, Long quotationOwnerId) {
        String where = " WHERE i.tenant_id=:tenantId AND i.public_id = :publicId AND (:quotationOwnerId IS NULL OR q.owner_worker_id = :quotationOwnerId) FOR UPDATE";
        return jdbc.query(HEADER + where, TenantScope.withTenant(new MapSqlParameterSource().addValue("publicId", publicId)
                .addValue("quotationOwnerId", quotationOwnerId)), (rs, row) -> mapHeader(rs)).stream().findFirst();
    }

    public String insert(long registeredByWorkerId, long customerId, Long quotationId, InvoiceInputData input,
                         List<LineInputData> lines, InvoiceCalculation.Result calculation) {
        String publicId = UUID.randomUUID().toString();
        var key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO invoices (tenant_id, public_id, invoice_number, invoice_type, customer_id, quotation_id,
                    registered_by_worker_id, status, issue_date, due_date, currency_code, exchange_rate_to_pen,
                    includes_tax, tax_rate, subtotal_amount, tax_amount, total_amount, notes)
                VALUES (:tenantId, :publicId, :invoiceNumber, :invoiceType, :customerId, :quotationId,
                    :registeredByWorkerId, 'DRAFT', :issueDate, :dueDate, :currencyCode, :exchangeRateToPen,
                    :includesTax, :taxRate, :subtotal, :tax, :total, :notes)
                """, TenantScope.withTenant(headerParams(publicId, registeredByWorkerId, customerId, quotationId, input, calculation)), key);
        long id = key.getKey().longValue();
        insertItems(id, lines, calculation);
        return publicId;
    }

    public int updateDraft(String publicId, long version, long customerId, Long quotationId,
                           InvoiceInputData input, List<LineInputData> lines, InvoiceCalculation.Result calculation) {
        int updated = jdbc.update("""
                UPDATE invoices SET invoice_number=:invoiceNumber, invoice_type=:invoiceType,
                  customer_id=:customerId, quotation_id=:quotationId, issue_date=:issueDate, due_date=:dueDate,
                  currency_code=:currencyCode, exchange_rate_to_pen=:exchangeRateToPen, includes_tax=:includesTax,
                  tax_rate=:taxRate, subtotal_amount=:subtotal, tax_amount=:tax, total_amount=:total, notes=:notes,
                  version=version+1, updated_at=CURRENT_TIMESTAMP(6)
                WHERE public_id=:publicId AND tenant_id=:tenantId AND status='DRAFT' AND version=:version
                """, headerParams(publicId, 0L, customerId, quotationId, input, calculation)
                .addValue("version", version).addValue("tenantId", TenantScope.requireTenantId()));
        if (updated == 1) {
            long id = idOf(publicId);
            jdbc.update("DELETE FROM invoice_items WHERE invoice_id=:id AND EXISTS "
                    + "(SELECT 1 FROM invoices i WHERE i.id=invoice_items.invoice_id AND i.tenant_id=:tenantId)",
                    TenantScope.withTenant(new MapSqlParameterSource("id", id)));
            insertItems(id, lines, calculation);
        }
        return updated;
    }

    public int changeStatus(String publicId, long version, InvoiceStatus target) {
        return jdbc.update("""
                UPDATE invoices SET status=:status, version=version+1, updated_at=CURRENT_TIMESTAMP(6)
                WHERE public_id=:publicId AND tenant_id=:tenantId AND version=:version
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("publicId", publicId)
                .addValue("version", version).addValue("status", target.name())));
    }

    public int touchAfterDocument(String publicId, long version) {
        return jdbc.update("""
                UPDATE invoices SET version=version+1, updated_at=CURRENT_TIMESTAMP(6)
                WHERE public_id=:publicId AND tenant_id=:tenantId AND status='DRAFT' AND version=:version
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("publicId", publicId)
                .addValue("version", version)));
    }

    /**
     * publicId de una factura ya registrada con ese tipo y número, si existe.
     * La tabla tiene UNIQUE(tenant_id, invoice_type, invoice_number); esta consulta permite
     * avisar antes de intentar el INSERT y decir cuál es la factura repetida.
     */
    public Optional<String> findPublicIdByTypeAndNumber(InvoiceType type, String invoiceNumber) {
        return jdbc.query("SELECT public_id FROM invoices WHERE tenant_id=:tenantId AND invoice_type=:type AND invoice_number=:number",
                        TenantScope.withTenant(new MapSqlParameterSource().addValue("type", type.name())
                                .addValue("number", invoiceNumber)),
                        (rs, row) -> rs.getString("public_id"))
                .stream().findFirst();
    }

    /** Busca un producto del catálogo por SKU exacto (sin distinguir mayúsculas). */
    public Optional<ProductMatch> findProductBySku(String sku) {
        return jdbc.query("""
                        SELECT id, sku, name FROM products
                        WHERE LOWER(sku) = LOWER(:sku) AND tenant_id=:tenantId AND active = TRUE
                        ORDER BY id LIMIT 1
                        """, TenantScope.withTenant(new MapSqlParameterSource("sku", sku)),
                        (rs, row) -> new ProductMatch(rs.getLong("id"), rs.getString("sku"), rs.getString("name")))
                .stream().findFirst();
    }

    /** Busca un cliente por número de documento (RUC/DNI). */
    public Optional<CustomerMatch> findCustomerByDocument(String documentNumber) {
        return jdbc.query("""
                        SELECT id, public_id, business_name, active FROM customers
                        WHERE document_number = :documentNumber AND tenant_id=:tenantId
                        ORDER BY id LIMIT 1
                        """, TenantScope.withTenant(new MapSqlParameterSource("documentNumber", documentNumber)),
                        (rs, row) -> new CustomerMatch(rs.getLong("id"), rs.getString("public_id"),
                                rs.getString("business_name"), rs.getBoolean("active")))
                .stream().findFirst();
    }

    /** Registro de precio de compra tomado de una factura importada. */
    public void insertPriceReference(long workerId, long productId, String supplierName, String sourceReference,
                                     String currencyCode, java.math.BigDecimal unitPrice, boolean includesTax,
                                     java.math.BigDecimal taxRate, Instant effectiveAt, String notes) {
        jdbc.update("""
                INSERT INTO product_price_references
                    (tenant_id, product_id, recorded_by_worker_id, supplier_name, source_reference, currency_code,
                     unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
                VALUES (:tenantId, :productId, :workerId, :supplierName, :sourceReference, :currencyCode,
                        :unitPrice, :includesTax, :taxRate, NULL, :effectiveAt, :notes)
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("productId", productId).addValue("workerId", workerId)
                .addValue("supplierName", supplierName).addValue("sourceReference", sourceReference)
                .addValue("currencyCode", currencyCode).addValue("unitPrice", unitPrice)
                .addValue("includesTax", includesTax).addValue("taxRate", taxRate)
                .addValue("effectiveAt", java.sql.Timestamp.from(effectiveAt)).addValue("notes", notes)));
    }

    public record ProductMatch(long id, String sku, String name) {}

    public record CustomerMatch(long id, String publicId, String businessName, boolean active) {}

    public long idOf(String publicId) {
        return jdbc.query("SELECT id FROM invoices WHERE public_id=:publicId AND tenant_id=:tenantId",
                TenantScope.withTenant(new MapSqlParameterSource("publicId", publicId)), (rs, row) -> rs.getLong("id"))
                .stream().findFirst().orElseThrow(() -> new NotFoundException("Factura no encontrada"));
    }

    public int deactivateActiveDocuments(long invoiceId, InvoiceDocumentRole role) {
        return jdbc.update("""
                UPDATE invoice_documents SET active=FALSE
                WHERE invoice_id=:invoiceId AND document_role=:documentRole AND active=TRUE AND EXISTS
                  (SELECT 1 FROM invoices i WHERE i.id=invoice_documents.invoice_id AND i.tenant_id=:tenantId)
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("invoiceId", invoiceId)
                .addValue("documentRole", role.name())));
    }

    public void linkDocument(long invoiceId, long documentId, InvoiceDocumentRole role) {
        jdbc.update("""
                INSERT INTO invoice_documents(invoice_id, document_id, document_role, active)
                SELECT :invoiceId, :documentId, :documentRole, TRUE FROM invoices i
                JOIN stored_documents d ON d.id=:documentId AND d.tenant_id=:tenantId
                WHERE i.id=:invoiceId AND i.tenant_id=:tenantId
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("invoiceId", invoiceId)
                .addValue("documentId", documentId).addValue("documentRole", role.name())));
    }

    public Optional<DocumentLinkData> activeDocument(String invoicePublicId, String documentPublicId) {
        return jdbc.query("""
                SELECT d.public_id, idoc.document_role, d.original_name, d.media_type, d.extension,
                       d.size_bytes, d.sha256_hex, d.version, d.created_at
                FROM invoice_documents idoc
                JOIN invoices i ON i.id=idoc.invoice_id
                JOIN stored_documents d ON d.id=idoc.document_id
                WHERE i.tenant_id=:tenantId AND d.tenant_id=i.tenant_id
                  AND i.public_id=:invoicePublicId AND d.public_id=:documentPublicId
                  AND idoc.active=TRUE AND d.active=TRUE
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("invoicePublicId", invoicePublicId)
                .addValue("documentPublicId", documentPublicId)), (rs, row) -> mapDocument(rs)).stream().findFirst();
    }

    private MapSqlParameterSource baseParams(String query, String status, String type, String customerPublicId,
                                              String quotationPublicId, Long quotationOwnerId) {
        String pattern = query == null || query.isBlank() ? null : "%" + escapeLike(query.trim().toLowerCase()) + "%";
        return new MapSqlParameterSource().addValue("pattern", pattern).addValue("status", status)
                .addValue("type", type).addValue("customerPublicId", customerPublicId)
                .addValue("quotationPublicId", quotationPublicId).addValue("quotationOwnerId", quotationOwnerId);
    }

    private MapSqlParameterSource headerParams(String publicId, long registeredByWorkerId, long customerId,
                                                Long quotationId, InvoiceInputData input,
                                                InvoiceCalculation.Result calculation) {
        return new MapSqlParameterSource().addValue("publicId", publicId)
                .addValue("invoiceNumber", input.invoiceNumber()).addValue("invoiceType", input.invoiceType().name())
                .addValue("registeredByWorkerId", registeredByWorkerId == 0 ? null : registeredByWorkerId)
                .addValue("customerId", customerId).addValue("quotationId", quotationId)
                .addValue("issueDate", input.issueDate()).addValue("dueDate", input.dueDate())
                .addValue("currencyCode", input.currencyCode()).addValue("exchangeRateToPen", input.exchangeRateToPen())
                .addValue("includesTax", input.includesTax()).addValue("taxRate", input.taxRate())
                .addValue("subtotal", calculation.subtotal()).addValue("tax", calculation.tax())
                .addValue("total", calculation.total()).addValue("notes", input.notes());
    }

    private void insertItems(long invoiceId, List<LineInputData> lines, InvoiceCalculation.Result calculation) {
        for (int i = 0; i < lines.size(); i++) {
            LineInputData line = lines.get(i);
            InvoiceCalculation.Line calculated = calculation.lines().get(i);
            jdbc.update("""
                    INSERT INTO invoice_items(invoice_id, line_number, product_id, sku_snapshot,
                        description_snapshot, unit_snapshot, quantity, unit_price, line_subtotal, line_tax, line_total)
                    SELECT :invoiceId, :lineNumber, :productId, :sku, :description, :unit, :quantity,
                        :unitPrice, :subtotal, :tax, :total FROM invoices i
                    WHERE i.id=:invoiceId AND i.tenant_id=:tenantId
                    """, TenantScope.withTenant(new MapSqlParameterSource().addValue("invoiceId", invoiceId)
                    .addValue("lineNumber", calculated.lineNumber()).addValue("productId", line.productId())
                    .addValue("sku", line.sku()).addValue("description", line.description())
                    .addValue("unit", line.unit()).addValue("quantity", line.quantity())
                    .addValue("unitPrice", line.unitPrice()).addValue("subtotal", calculated.subtotal())
                    .addValue("tax", calculated.tax()).addValue("total", calculated.total())));
        }
    }

    private InvoiceViewData aggregate(InvoiceHeader header) {
        List<ItemData> items = jdbc.query("""
                SELECT line_number, product_id, sku_snapshot, description_snapshot, unit_snapshot,
                       quantity, unit_price, line_subtotal, line_tax, line_total
                FROM invoice_items WHERE invoice_id=:id AND EXISTS (SELECT 1 FROM invoices i WHERE i.id=:id AND i.tenant_id=:tenantId)
                ORDER BY line_number
                """, TenantScope.withTenant(new MapSqlParameterSource("id", header.id())), (rs, row) -> new ItemData(
                rs.getInt("line_number"), (Long) rs.getObject("product_id"), rs.getString("sku_snapshot"),
                rs.getString("description_snapshot"), rs.getString("unit_snapshot"), rs.getBigDecimal("quantity"),
                rs.getBigDecimal("unit_price"), rs.getBigDecimal("line_subtotal"), rs.getBigDecimal("line_tax"),
                rs.getBigDecimal("line_total")));
        List<DocumentData> documents = jdbc.query("""
                SELECT d.public_id, idoc.document_role, d.original_name, d.media_type, d.extension,
                       d.size_bytes, d.sha256_hex, d.version, d.created_at
                FROM invoice_documents idoc JOIN stored_documents d ON d.id=idoc.document_id
                WHERE idoc.invoice_id=:id AND d.tenant_id=:tenantId AND idoc.active=TRUE AND d.active=TRUE
                ORDER BY idoc.created_at DESC, d.id DESC
                """, TenantScope.withTenant(new MapSqlParameterSource("id", header.id())), (rs, row) -> new DocumentData(
                rs.getString("public_id"), InvoiceDocumentRole.valueOf(rs.getString("document_role")),
                rs.getString("original_name"), rs.getString("media_type"), rs.getString("extension"),
                rs.getLong("size_bytes"), rs.getString("sha256_hex"), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant()));
        return new InvoiceViewData(header, items, documents);
    }

    private InvoiceHeader mapHeader(ResultSet rs) throws SQLException {
        return new InvoiceHeader(rs.getLong("id"), rs.getString("public_id"), rs.getString("invoice_number"),
                InvoiceType.valueOf(rs.getString("invoice_type")), rs.getLong("customer_id"),
                rs.getString("customer_public_id"), rs.getString("customer_name"), rs.getString("customer_document_type"),
                rs.getString("customer_document_number"), rs.getString("quotation_public_id"), rs.getString("quotation_number"),
                (Long) rs.getObject("quotation_owner_worker_id"), rs.getLong("registered_by_worker_id"),
                rs.getString("registered_by_display_name"), InvoiceStatus.valueOf(rs.getString("status")),
                rs.getObject("issue_date", LocalDate.class), rs.getObject("due_date", LocalDate.class),
                rs.getString("currency_code"), rs.getBigDecimal("exchange_rate_to_pen"), rs.getBoolean("includes_tax"),
                rs.getBigDecimal("tax_rate"), rs.getBigDecimal("subtotal_amount"), rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("total_amount"), rs.getString("notes"), rs.getLong("version"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private DocumentLinkData mapDocument(ResultSet rs) throws SQLException {
        return new DocumentLinkData(rs.getString("public_id"), InvoiceDocumentRole.valueOf(rs.getString("document_role")),
                rs.getString("original_name"), rs.getString("media_type"), rs.getString("extension"),
                rs.getLong("size_bytes"), rs.getString("sha256_hex"), rs.getLong("version"), instant(rs, "created_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public record InvoiceInputData(String invoiceNumber, InvoiceType invoiceType, LocalDate issueDate,
                                   LocalDate dueDate, String currencyCode, BigDecimal exchangeRateToPen,
                                   boolean includesTax, BigDecimal taxRate, String notes) {}

    public record LineInputData(Long productId, String sku, String description, String unit,
                                BigDecimal quantity, BigDecimal unitPrice) {}

    public record InvoiceHeader(long id, String publicId, String invoiceNumber, InvoiceType invoiceType,
                                long customerId, String customerPublicId, String customerName,
                                String customerDocumentType, String customerDocumentNumber,
                                String quotationPublicId, String quotationNumber, Long quotationOwnerWorkerId,
                                long registeredByWorkerId, String registeredByDisplayName, InvoiceStatus status,
                                LocalDate issueDate, LocalDate dueDate, String currencyCode,
                                BigDecimal exchangeRateToPen, boolean includesTax, BigDecimal taxRate,
                                BigDecimal subtotalAmount, BigDecimal taxAmount, BigDecimal totalAmount,
                                String notes, long version, Instant createdAt, Instant updatedAt) {}

    public record ItemData(int lineNumber, Long productId, String sku, String description, String unit,
                           BigDecimal quantity, BigDecimal unitPrice, BigDecimal subtotal,
                           BigDecimal tax, BigDecimal total) {}

    public record DocumentData(String publicId, InvoiceDocumentRole role, String originalName, String mediaType,
                               String extension, long sizeBytes, String sha256Hex, long version, Instant createdAt) {}

    public record DocumentLinkData(String publicId, InvoiceDocumentRole role, String originalName, String mediaType,
                                   String extension, long sizeBytes, String sha256Hex, long version, Instant createdAt) {}

    public record InvoiceViewData(InvoiceHeader header, List<ItemData> items, List<DocumentData> documents) {}
    public record PageData(List<InvoiceViewData> items, long totalItems, int page, int size, int totalPages) {}
}

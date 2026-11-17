package pe.giova.quotation;

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
import pe.giova.quotation.api.QuotationDtos.ItemInput;
import pe.giova.auth.TenantScope;

public class QuotationRepository {
    private static final String HEADER = """
            SELECT q.id, q.public_id, q.quotation_number, q.customer_id, c.public_id AS customer_public_id,
                   c.business_name AS customer_name, c.document_type AS customer_document_type,
                   c.document_number AS customer_document_number, q.owner_worker_id,
                   w.display_name AS owner_display_name, q.status, q.procurement_type,
                   q.outcome, q.outcome_reason, q.outcome_source, q.outcome_at,
                   q.delivery_term_days, q.warranty_months, q.ocam_number, q.ocam_date,
                   q.issued_at, q.valid_until,
                   q.currency_code, q.exchange_rate_to_pen, q.includes_tax, q.tax_rate,
                   q.subtotal_amount, q.tax_amount, q.total_amount, q.notes, q.version,
                   q.created_at, q.updated_at,
                   (SELECT d.public_id FROM quotation_documents qd JOIN stored_documents d ON d.id = qd.document_id
                    WHERE qd.quotation_id = q.id AND qd.document_role = 'ISSUED_PDF'
                      AND d.tenant_id=q.tenant_id LIMIT 1) AS issued_pdf_public_id
            FROM quotations q JOIN customers c ON c.id = q.customer_id AND c.tenant_id=q.tenant_id
            JOIN workers w ON w.id = q.owner_worker_id
            """;
    private final NamedParameterJdbcTemplate jdbc;

    public QuotationRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public PageData find(String query, String status, String procurementType, String outcome, Long customerId,
                     Long ownerId, LocalDate from, LocalDate to, int page, int size) {
        String where = """
                WHERE q.tenant_id=:tenantId
                  AND (:pattern IS NULL OR LOWER(CONCAT_WS(' ', q.quotation_number, c.business_name, c.document_number)) LIKE :pattern ESCAPE '\\\\')
                  AND (:status IS NULL OR q.status = :status)
                  AND (:procurementType IS NULL OR q.procurement_type = :procurementType)
                  AND (:outcome IS NULL OR q.outcome = :outcome)
                  AND (:customerId IS NULL OR q.customer_id = :customerId)
                  AND (:ownerId IS NULL OR q.owner_worker_id = :ownerId)
                  AND (:fromDate IS NULL OR DATE(q.created_at) >= :fromDate)
                  AND (:toDate IS NULL OR DATE(q.created_at) <= :toDate)
                """;
        var params = new MapSqlParameterSource().addValue("pattern", query == null || query.isBlank() ? null : "%" + escapeLike(query.toLowerCase()) + "%")
                .addValue("status", status).addValue("procurementType", procurementType).addValue("outcome", outcome)
                .addValue("customerId", customerId).addValue("ownerId", ownerId)
                .addValue("fromDate", from).addValue("toDate", to).addValue("pageSize", size)
                .addValue("offset", (long) page * size);
        List<QuotationHeader> headers = jdbc.query(HEADER + where + " ORDER BY q.updated_at DESC, q.id DESC LIMIT :pageSize OFFSET :offset", TenantScope.withTenant(params),
                (rs, row) -> mapHeader(rs));
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM quotations q JOIN customers c ON c.id=q.customer_id AND c.tenant_id=q.tenant_id " + where, TenantScope.withTenant(params), Long.class);
        return new PageData(headers.stream().map(this::aggregate).toList(), total, page, size, total == 0 ? 0 : (int) Math.ceil((double) total / size));
    }

    public Optional<QuotationViewData> findByPublicId(String publicId, Long ownerId) {
        String where = " WHERE q.tenant_id=:tenantId AND q.public_id = :publicId AND (:ownerId IS NULL OR q.owner_worker_id = :ownerId)";
        return jdbc.query(HEADER + where, TenantScope.withTenant(new MapSqlParameterSource().addValue("publicId", publicId).addValue("ownerId", ownerId)),
                (rs, row) -> mapHeader(rs)).stream().findFirst().map(this::aggregate);
    }

    public CustomerData customer(String publicId) {
        return jdbc.query("SELECT id, public_id, owner_worker_id, business_name, document_type, document_number, active FROM customers WHERE public_id=:publicId AND tenant_id=:tenantId",
                TenantScope.withTenant(new MapSqlParameterSource("publicId", publicId)), (rs, row) -> new CustomerData(rs.getLong("id"), rs.getString("public_id"),
                        rs.getLong("owner_worker_id"), rs.getString("business_name"), rs.getString("document_type"), rs.getString("document_number"), rs.getBoolean("active")))
                .stream().findFirst().orElseThrow(() -> new NotFoundException("Cliente no encontrado"));
    }

    public ProductData product(long id) {
        return jdbc.query("SELECT id, sku, name, active FROM products WHERE id=:id AND tenant_id=:tenantId", TenantScope.withTenant(new MapSqlParameterSource("id", id)),
                (rs, row) -> new ProductData(rs.getLong("id"), rs.getString("sku"), rs.getString("name"), rs.getBoolean("active")))
                .stream().findFirst().orElseThrow(() -> new NotFoundException("Producto no encontrado"));
    }

    public String insert(long ownerId, CustomerData customer, QuotationInputData input, QuotationCalculation.Result calculation) {
        String publicId = UUID.randomUUID().toString();
        var key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO quotations (tenant_id, public_id, customer_id, owner_worker_id, status, procurement_type,
                    outcome, valid_until, delivery_term_days, warranty_months, currency_code,
                    exchange_rate_to_pen, includes_tax, tax_rate, subtotal_amount, tax_amount, total_amount, notes)
                VALUES (:tenantId,:publicId,:customerId,:ownerId,'DRAFT',:procurementType,NULL,:validUntil,:deliveryTermDays,
                    :warrantyMonths,:currency,:exchange,:includesTax,:taxRate,:subtotal,:tax,:total,:notes)
                """, TenantScope.withTenant(params(publicId, ownerId, customer.id(), input, calculation)), key);
        long id = key.getKey().longValue();
        insertItems(id, input.items(), calculation);
        return publicId;
    }

    public int updateDraft(String publicId, long version, CustomerData customer, QuotationInputData input, QuotationCalculation.Result calculation) {
        int updated = jdbc.update("""
                UPDATE quotations SET customer_id=:customerId, procurement_type=:procurementType,
                  valid_until=:validUntil, delivery_term_days=:deliveryTermDays, warranty_months=:warrantyMonths,
                  currency_code=:currency,
                  exchange_rate_to_pen=:exchange, includes_tax=:includesTax, tax_rate=:taxRate,
                  subtotal_amount=:subtotal, tax_amount=:tax, total_amount=:total, notes=:notes,
                  version=version+1, updated_at=CURRENT_TIMESTAMP(6)
                WHERE public_id=:publicId AND tenant_id=:tenantId AND status='DRAFT' AND version=:version
                """, TenantScope.withTenant(params(publicId, 0, customer.id(), input, calculation).addValue("version", version)));
        if (updated == 1) {
            long id = idOf(publicId);
            jdbc.update("DELETE FROM quotation_items WHERE quotation_id=:id AND EXISTS "
                    + "(SELECT 1 FROM quotations q WHERE q.id=quotation_items.quotation_id AND q.tenant_id=:tenantId)",
                    TenantScope.withTenant(new MapSqlParameterSource("id", id)));
            insertItems(id, input.items(), calculation);
        }
        return updated;
    }

    public long idOf(String publicId) {
        return jdbc.query("SELECT id FROM quotations WHERE public_id=:publicId AND tenant_id=:tenantId", TenantScope.withTenant(new MapSqlParameterSource("publicId", publicId)),
                (rs, row) -> rs.getLong("id")).stream().findFirst().orElseThrow(() -> new NotFoundException("Cotización no encontrada"));
    }

    public Optional<QuotationHeader> header(String publicId) {
        return jdbc.query(HEADER + " WHERE q.tenant_id=:tenantId AND q.public_id=:publicId", TenantScope.withTenant(new MapSqlParameterSource("publicId", publicId)),
                (rs, row) -> mapHeader(rs)).stream().findFirst();
    }

    public int issue(String publicId, long version, String number, Instant issuedAt, LocalDate validUntil) {
        return jdbc.update("""
                UPDATE quotations SET quotation_number=:number, status='ISSUED', issued_at=:issuedAt,
                  valid_until=:validUntil, version=version+1, updated_at=CURRENT_TIMESTAMP(6)
                WHERE public_id=:publicId AND tenant_id=:tenantId AND status='DRAFT' AND version=:version
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("publicId", publicId).addValue("version", version)
                .addValue("number", number).addValue("issuedAt", issuedAt).addValue("validUntil", validUntil)));
    }

    public int changeStatus(String publicId, long version, QuotationStatus target) {
        return jdbc.update("UPDATE quotations SET status=:status, version=version+1, updated_at=CURRENT_TIMESTAMP(6) "
                + "WHERE public_id=:publicId AND tenant_id=:tenantId AND version=:version", TenantScope.withTenant(new MapSqlParameterSource().addValue("status", target.name())
                .addValue("publicId", publicId).addValue("version", version)));
    }

    public long nextSequence(int year) {
        long tenantId = TenantScope.requireTenantId();
        jdbc.update("INSERT IGNORE INTO document_sequences(tenant_id,document_type,year,last_value,version) VALUES (:tenantId,'COT',:year,0,0)",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("year", year));
        Long current = jdbc.queryForObject("SELECT last_value FROM document_sequences WHERE tenant_id=:tenantId AND document_type='COT' AND year=:year FOR UPDATE",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("year", year), Long.class);
        long next = current + 1;
        jdbc.update("UPDATE document_sequences SET last_value=:next,version=version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=:tenantId AND document_type='COT' AND year=:year",
                new MapSqlParameterSource().addValue("next", next).addValue("year", year).addValue("tenantId", tenantId));
        return next;
    }

    public void linkDocument(long quotationId, long documentId) {
        jdbc.update("""
                INSERT INTO quotation_documents(quotation_id,document_id,document_role)
                SELECT :quotationId,:documentId,'ISSUED_PDF' FROM quotations q
                JOIN stored_documents d ON d.id=:documentId AND d.tenant_id=:tenantId
                WHERE q.id=:quotationId AND q.tenant_id=:tenantId
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("quotationId", quotationId).addValue("documentId", documentId)));
    }

    private void insertItems(long quotationId, List<ItemInput> items, QuotationCalculation.Result calculation) {
        for (int i = 0; i < items.size(); i++) {
            ItemInput item = items.get(i);
            ProductData product = item.productId() == null ? null : product(item.productId());
            if (product != null && !product.active()) throw new IllegalArgumentException("El producto no está activo");
            QuotationCalculation.Line line = calculation.lines().get(i);
            jdbc.update("""
                    INSERT INTO quotation_items(quotation_id,line_number,product_id,sku_snapshot,description_snapshot,unit_snapshot,
                      quantity,unit_price,competitor_price,purchase_price,line_subtotal,line_tax,line_total)
                    SELECT :quotationId,:lineNumber,:productId,:sku,:description,:unit,:quantity,:unitPrice,
                      :competitorPrice,:purchasePrice,:subtotal,:tax,:total
                    FROM quotations q WHERE q.id=:quotationId AND q.tenant_id=:tenantId
                    """, TenantScope.withTenant(new MapSqlParameterSource().addValue("quotationId", quotationId).addValue("lineNumber", i + 1)
                    .addValue("productId", product == null ? null : product.id()).addValue("sku", product == null ? item.sku().trim() : product.sku())
                    .addValue("description", product == null ? item.description().trim() : product.name()).addValue("unit", item.unit() == null || item.unit().isBlank() ? "unidad" : item.unit().trim())
                    .addValue("quantity", item.quantity()).addValue("unitPrice", item.unitPrice())
                    .addValue("competitorPrice", item.competitorPrice()).addValue("purchasePrice", item.purchasePrice())
                    .addValue("subtotal", line.subtotal())
                    .addValue("tax", line.tax()).addValue("total", line.total())));
        }
    }

    private MapSqlParameterSource params(String publicId, long ownerId, long customerId, QuotationInputData input, QuotationCalculation.Result calculation) {
        return new MapSqlParameterSource().addValue("publicId", publicId).addValue("ownerId", ownerId).addValue("customerId", customerId)
                .addValue("procurementType", input.procurementType().name())
                .addValue("deliveryTermDays", input.deliveryTermDays()).addValue("warrantyMonths", input.warrantyMonths())
                .addValue("validUntil", input.validUntil()).addValue("currency", input.currencyCode()).addValue("exchange", input.exchangeRateToPen())
                .addValue("includesTax", input.includesTax()).addValue("taxRate", input.taxRate()).addValue("subtotal", calculation.subtotal())
                .addValue("tax", calculation.tax()).addValue("total", calculation.total()).addValue("notes", input.notes());
    }

    private QuotationViewData aggregate(QuotationHeader header) {
            List<ItemData> items = jdbc.query("SELECT line_number,product_id,sku_snapshot,description_snapshot,unit_snapshot,quantity,unit_price,competitor_price,purchase_price,line_subtotal,line_tax,line_total FROM quotation_items WHERE quotation_id=:id AND EXISTS (SELECT 1 FROM quotations q WHERE q.id=:id AND q.tenant_id=:tenantId) ORDER BY line_number",
                TenantScope.withTenant(new MapSqlParameterSource("id", header.id())), (rs, row) -> new ItemData(rs.getInt("line_number"), (Long) rs.getObject("product_id"), rs.getString("sku_snapshot"),
                        rs.getString("description_snapshot"), rs.getString("unit_snapshot"), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("competitor_price"), rs.getBigDecimal("purchase_price"),
                        rs.getBigDecimal("line_subtotal"), rs.getBigDecimal("line_tax"), rs.getBigDecimal("line_total")));
        return new QuotationViewData(header, items);
    }

    private QuotationHeader mapHeader(ResultSet rs) throws SQLException {
        return new QuotationHeader(rs.getLong("id"), rs.getString("public_id"), rs.getString("quotation_number"), rs.getLong("customer_id"),
                rs.getString("customer_public_id"), rs.getString("customer_name"), rs.getString("customer_document_type"), rs.getString("customer_document_number"),
                rs.getLong("owner_worker_id"), rs.getString("owner_display_name"), QuotationStatus.valueOf(rs.getString("status")),
                ProcurementType.valueOf(rs.getString("procurement_type")),
                enumOrNull(rs.getString("outcome")), rs.getString("outcome_reason"),
                sourceOrNull(rs.getString("outcome_source")), instant(rs, "outcome_at"),
                (Integer) rs.getObject("delivery_term_days"), (Integer) rs.getObject("warranty_months"),
                rs.getString("ocam_number"), rs.getObject("ocam_date", LocalDate.class),
                instant(rs, "issued_at"), rs.getObject("valid_until", LocalDate.class), rs.getString("currency_code"), rs.getBigDecimal("exchange_rate_to_pen"),
                rs.getBoolean("includes_tax"), rs.getBigDecimal("tax_rate"), rs.getBigDecimal("subtotal_amount"), rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("total_amount"), rs.getString("notes"), rs.getLong("version"), instant(rs, "created_at"), instant(rs, "updated_at"), rs.getString("issued_pdf_public_id"));
    }

    /** Cierra el proceso: resultado, motivo y quién lo decidió, en un solo movimiento. */
    public int setOutcome(String publicId, long version, QuotationOutcome outcome, String reason,
                          OutcomeSource source, QuotationStatus status) {
        return jdbc.update("""
                UPDATE quotations SET outcome=:outcome, outcome_reason=:reason, outcome_source=:source,
                  outcome_at=CURRENT_TIMESTAMP(6), status=:status, version=version+1,
                  updated_at=CURRENT_TIMESTAMP(6)
                WHERE public_id=:publicId AND tenant_id=:tenantId AND version=:version
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("publicId", publicId).addValue("version", version)
                .addValue("outcome", outcome.name()).addValue("reason", reason)
                .addValue("source", source == null ? null : source.name()).addValue("status", status.name())));
    }

    /** La OCAM puede llegar días después de ganar, por eso se registra aparte. */
    public int setOcam(String publicId, long version, String ocamNumber, LocalDate ocamDate) {
        return jdbc.update("""
                UPDATE quotations SET ocam_number=:ocamNumber, ocam_date=:ocamDate, version=version+1,
                  updated_at=CURRENT_TIMESTAMP(6)
                WHERE public_id=:publicId AND tenant_id=:tenantId AND version=:version
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("publicId", publicId).addValue("version", version)
                .addValue("ocamNumber", ocamNumber).addValue("ocamDate", ocamDate)));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException { var value = rs.getTimestamp(column); return value == null ? null : value.toInstant(); }
    private static QuotationOutcome enumOrNull(String value) { return value == null || value.isBlank() ? null : QuotationOutcome.valueOf(value); }
    private static OutcomeSource sourceOrNull(String value) { return value == null || value.isBlank() ? null : OutcomeSource.valueOf(value); }
    private static String escapeLike(String value) { return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }

    public record CustomerData(long id, String publicId, long ownerWorkerId, String name, String documentType, String documentNumber, boolean active) {}
    public record ProductData(long id, String sku, String name, boolean active) {}
    public record QuotationInputData(String customerPublicId, ProcurementType procurementType, String currencyCode,
                                     BigDecimal exchangeRateToPen, boolean includesTax, BigDecimal taxRate,
                                     LocalDate validUntil, Integer deliveryTermDays, Integer warrantyMonths,
                                     String notes, List<ItemInput> items) {}
    public record QuotationHeader(long id, String publicId, String quotationNumber, long customerId, String customerPublicId, String customerName,
                                  String customerDocumentType, String customerDocumentNumber, long ownerWorkerId, String ownerDisplayName,
                                  QuotationStatus status, ProcurementType procurementType, QuotationOutcome outcome, String outcomeReason,
                                  OutcomeSource outcomeSource, Instant outcomeAt, Integer deliveryTermDays, Integer warrantyMonths,
                                  String ocamNumber, LocalDate ocamDate,
                                  Instant issuedAt, LocalDate validUntil, String currencyCode, BigDecimal exchangeRateToPen,
                                  boolean includesTax, BigDecimal taxRate, BigDecimal subtotalAmount, BigDecimal taxAmount, BigDecimal totalAmount,
                                  String notes, long version, Instant createdAt, Instant updatedAt, String issuedPdfPublicId) {}
    public record ItemData(int lineNumber, Long productId, String sku, String description, String unit, BigDecimal quantity, BigDecimal unitPrice,
                           BigDecimal competitorPrice, BigDecimal purchasePrice,
                           BigDecimal subtotal, BigDecimal tax, BigDecimal total) {}
    public record QuotationViewData(QuotationHeader header, List<ItemData> items) {}
    public record PageData(List<QuotationViewData> items, long totalItems, int page, int size, int totalPages) {}
}

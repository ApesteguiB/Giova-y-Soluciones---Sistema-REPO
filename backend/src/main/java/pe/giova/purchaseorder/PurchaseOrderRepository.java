package pe.giova.purchaseorder;

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

public class PurchaseOrderRepository {
    private static final String HEADER = """
            SELECT po.id, po.public_id, po.order_number, po.customer_id,
                   c.public_id AS customer_public_id, c.business_name AS customer_name,
                   c.document_type AS customer_document_type, c.document_number AS customer_document_number,
                   q.public_id AS quotation_public_id, q.quotation_number,
                   q.owner_worker_id AS quotation_owner_worker_id,
                   i.public_id AS invoice_public_id, i.invoice_number,
                   po.responsible_worker_id, w.display_name AS responsible_worker_display_name,
                   po.status, po.issue_date, po.expected_delivery_date, po.delivered_date,
                   po.currency_code, po.exchange_rate_to_pen, po.includes_tax, po.tax_rate,
                   po.subtotal_amount, po.tax_amount, po.total_amount, po.notes, po.version,
                   po.created_at, po.updated_at
            FROM purchase_orders po
            JOIN customers c ON c.id = po.customer_id AND c.tenant_id=po.tenant_id
            JOIN workers w ON w.id = po.responsible_worker_id
            LEFT JOIN quotations q ON q.id = po.quotation_id AND q.tenant_id=po.tenant_id
            LEFT JOIN invoices i ON i.id = po.invoice_id AND i.tenant_id=po.tenant_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PurchaseOrderRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageData find(String query, String status, String customerPublicId, String quotationPublicId,
                         Long quotationOwnerId, int page, int size) {
        String where = """
                WHERE po.tenant_id=:tenantId
                  AND (:pattern IS NULL OR LOWER(CONCAT_WS(' ', po.order_number, c.business_name,
                       c.document_number, q.quotation_number, i.invoice_number)) LIKE :pattern ESCAPE '\\\\')
                  AND (:status IS NULL OR po.status = :status)
                  AND (:customerPublicId IS NULL OR c.public_id = :customerPublicId)
                  AND (:quotationPublicId IS NULL OR q.public_id = :quotationPublicId)
                  AND (:quotationOwnerId IS NULL OR q.owner_worker_id = :quotationOwnerId)
                """;
        var params = baseParams(query, status, customerPublicId, quotationPublicId, quotationOwnerId)
                .addValue("pageSize", size).addValue("offset", (long) page * size);
        List<PurchaseOrderHeader> headers = jdbc.query(HEADER + where
                        + " ORDER BY po.updated_at DESC, po.id DESC LIMIT :pageSize OFFSET :offset",
                TenantScope.withTenant(params), (rs, row) -> mapHeader(rs));
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM purchase_orders po JOIN customers c ON c.id=po.customer_id "
                + "LEFT JOIN quotations q ON q.id=po.quotation_id LEFT JOIN invoices i ON i.id=po.invoice_id " + where,
                TenantScope.withTenant(params), Long.class);
        return new PageData(headers.stream().map(this::aggregate).toList(), total, page, size,
                total == 0 ? 0 : (int) Math.ceil((double) total / size));
    }

    public Optional<PurchaseOrderViewData> findByPublicId(String publicId, Long quotationOwnerId) {
        return jdbc.query(HEADER + " WHERE po.tenant_id=:tenantId AND po.public_id=:publicId AND (:quotationOwnerId IS NULL OR q.owner_worker_id=:quotationOwnerId)",
                TenantScope.withTenant(new MapSqlParameterSource().addValue("publicId", publicId).addValue("quotationOwnerId", quotationOwnerId)),
                (rs, row) -> mapHeader(rs)).stream().findFirst().map(this::aggregate);
    }

    public Optional<PurchaseOrderHeader> lockByPublicId(String publicId, Long quotationOwnerId) {
        return jdbc.query(HEADER + " WHERE po.tenant_id=:tenantId AND po.public_id=:publicId AND (:quotationOwnerId IS NULL OR q.owner_worker_id=:quotationOwnerId) FOR UPDATE",
                TenantScope.withTenant(new MapSqlParameterSource().addValue("publicId", publicId).addValue("quotationOwnerId", quotationOwnerId)),
                (rs, row) -> mapHeader(rs)).stream().findFirst();
    }

    public String insert(long responsibleWorkerId, long customerId, Long quotationId, Long invoiceId,
                         PurchaseOrderInputData input, List<LineInputData> lines, PurchaseOrderCalculation.Result calculation) {
        String publicId = UUID.randomUUID().toString();
        var key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO purchase_orders (tenant_id, public_id, order_number, customer_id, quotation_id, invoice_id,
                    responsible_worker_id, status, issue_date, expected_delivery_date, delivered_date,
                    currency_code, exchange_rate_to_pen, includes_tax, tax_rate, subtotal_amount, tax_amount,
                    total_amount, notes)
                VALUES (:tenantId,:publicId,:orderNumber,:customerId,:quotationId,:invoiceId,:responsibleWorkerId,'REGISTERED',
                    :issueDate,:expectedDeliveryDate,:deliveredDate,:currencyCode,:exchangeRateToPen,:includesTax,
                    :taxRate,:subtotal,:tax,:total,:notes)
                """, TenantScope.withTenant(params(publicId, responsibleWorkerId, customerId, quotationId, invoiceId, input, calculation)), key);
        long id = key.getKey().longValue();
        insertItems(id, lines, calculation);
        return publicId;
    }

    public int updateRegistered(String publicId, long version, long customerId, Long quotationId, Long invoiceId,
                                PurchaseOrderInputData input, List<LineInputData> lines,
                                PurchaseOrderCalculation.Result calculation) {
        int updated = jdbc.update("""
                UPDATE purchase_orders SET order_number=:orderNumber, customer_id=:customerId,
                  quotation_id=:quotationId, invoice_id=:invoiceId, issue_date=:issueDate,
                  expected_delivery_date=:expectedDeliveryDate, delivered_date=:deliveredDate,
                  currency_code=:currencyCode, exchange_rate_to_pen=:exchangeRateToPen, includes_tax=:includesTax,
                  tax_rate=:taxRate, subtotal_amount=:subtotal, tax_amount=:tax, total_amount=:total, notes=:notes,
                  version=version+1, updated_at=CURRENT_TIMESTAMP(6)
                WHERE public_id=:publicId AND tenant_id=:tenantId AND status='REGISTERED' AND version=:version
                """, TenantScope.withTenant(params(publicId, 0L, customerId, quotationId, invoiceId, input, calculation).addValue("version", version)));
        if (updated == 1) {
            long id = idOf(publicId);
            jdbc.update("DELETE FROM purchase_order_items WHERE purchase_order_id=:id AND EXISTS "
                    + "(SELECT 1 FROM purchase_orders po WHERE po.id=purchase_order_items.purchase_order_id AND po.tenant_id=:tenantId)",
                    TenantScope.withTenant(new MapSqlParameterSource("id", id)));
            insertItems(id, lines, calculation);
        }
        return updated;
    }

    public int changeStatus(String publicId, long version, PurchaseOrderStatus target, LocalDate deliveredDate) {
        return jdbc.update("""
                UPDATE purchase_orders SET status=:status,
                  delivered_date=CASE WHEN :status='DELIVERED' THEN :deliveredDate ELSE delivered_date END,
                  version=version+1, updated_at=CURRENT_TIMESTAMP(6)
                WHERE public_id=:publicId AND tenant_id=:tenantId AND version=:version
                """, new MapSqlParameterSource().addValue("publicId", publicId).addValue("version", version)
                .addValue("status", target.name()).addValue("deliveredDate", deliveredDate).addValue("tenantId", TenantScope.requireTenantId()));
    }

    public int touchAfterDocument(String publicId, long version) {
        return jdbc.update("UPDATE purchase_orders SET version=version+1,updated_at=CURRENT_TIMESTAMP(6) "
                + "WHERE public_id=:publicId AND tenant_id=:tenantId AND status IN ('REGISTERED','IN_PROGRESS') AND version=:version",
                TenantScope.withTenant(new MapSqlParameterSource().addValue("publicId", publicId).addValue("version", version)));
    }

    public long idOf(String publicId) {
        return jdbc.query("SELECT id FROM purchase_orders WHERE public_id=:publicId AND tenant_id=:tenantId",
                TenantScope.withTenant(new MapSqlParameterSource("publicId", publicId)), (rs, row) -> rs.getLong("id"))
                .stream().findFirst().orElseThrow(() -> new NotFoundException("Orden no encontrada"));
    }

    public int deactivateActiveDocuments(long orderId, PurchaseOrderDocumentRole role) {
        return jdbc.update("UPDATE purchase_order_documents SET active=FALSE WHERE purchase_order_id=:orderId "
                + "AND document_role=:documentRole AND active=TRUE AND EXISTS "
                + "(SELECT 1 FROM purchase_orders po WHERE po.id=purchase_order_documents.purchase_order_id AND po.tenant_id=:tenantId)",
                TenantScope.withTenant(new MapSqlParameterSource().addValue("orderId", orderId).addValue("documentRole", role.name())));
    }

    public void linkDocument(long orderId, long documentId, PurchaseOrderDocumentRole role) {
        jdbc.update("""
                INSERT INTO purchase_order_documents(purchase_order_id,document_id,document_role,active)
                SELECT :orderId,:documentId,:documentRole,TRUE FROM purchase_orders po
                JOIN stored_documents d ON d.id=:documentId AND d.tenant_id=:tenantId
                WHERE po.id=:orderId AND po.tenant_id=:tenantId
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("orderId", orderId)
                .addValue("documentId", documentId).addValue("documentRole", role.name())));
    }

    public Optional<DocumentLinkData> activeDocument(String orderPublicId, String documentPublicId) {
        return jdbc.query("""
                SELECT d.public_id, pod.document_role, d.original_name, d.media_type, d.extension,
                       d.size_bytes, d.sha256_hex, d.version, d.created_at
                FROM purchase_order_documents pod JOIN purchase_orders po ON po.id=pod.purchase_order_id
                JOIN stored_documents d ON d.id=pod.document_id
                WHERE po.tenant_id=:tenantId AND d.tenant_id=po.tenant_id AND po.public_id=:orderPublicId AND d.public_id=:documentPublicId
                  AND pod.active=TRUE AND d.active=TRUE
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("orderPublicId", orderPublicId)
                .addValue("documentPublicId", documentPublicId)), (rs, row) -> mapDocument(rs)).stream().findFirst();
    }

    private MapSqlParameterSource baseParams(String query, String status, String customerPublicId,
                                              String quotationPublicId, Long quotationOwnerId) {
        String pattern = query == null || query.isBlank() ? null : "%" + escapeLike(query.trim().toLowerCase()) + "%";
        return new MapSqlParameterSource().addValue("pattern", pattern).addValue("status", status)
                .addValue("customerPublicId", customerPublicId).addValue("quotationPublicId", quotationPublicId)
                .addValue("quotationOwnerId", quotationOwnerId);
    }

    private MapSqlParameterSource params(String publicId, long responsibleWorkerId, long customerId, Long quotationId,
                                         Long invoiceId, PurchaseOrderInputData input, PurchaseOrderCalculation.Result calculation) {
        return new MapSqlParameterSource().addValue("publicId", publicId).addValue("orderNumber", input.orderNumber())
                .addValue("customerId", customerId).addValue("quotationId", quotationId).addValue("invoiceId", invoiceId)
                .addValue("responsibleWorkerId", responsibleWorkerId).addValue("issueDate", input.issueDate())
                .addValue("expectedDeliveryDate", input.expectedDeliveryDate()).addValue("deliveredDate", input.deliveredDate())
                .addValue("currencyCode", input.currencyCode()).addValue("exchangeRateToPen", input.exchangeRateToPen())
                .addValue("includesTax", input.includesTax()).addValue("taxRate", input.taxRate())
                .addValue("subtotal", calculation.subtotal()).addValue("tax", calculation.tax())
                .addValue("total", calculation.total()).addValue("notes", input.notes());
    }

    private void insertItems(long orderId, List<LineInputData> lines, PurchaseOrderCalculation.Result calculation) {
        for (int i = 0; i < lines.size(); i++) {
            LineInputData line = lines.get(i);
            var calculated = calculation.lines().get(i);
            jdbc.update("""
                    INSERT INTO purchase_order_items(purchase_order_id,line_number,product_id,sku_snapshot,
                      description_snapshot,unit_snapshot,quantity,unit_price,line_subtotal,line_tax,line_total)
                    SELECT :orderId,:lineNumber,:productId,:sku,:description,:unit,:quantity,:unitPrice,:subtotal,:tax,:total
                    FROM purchase_orders po WHERE po.id=:orderId AND po.tenant_id=:tenantId
                    """, TenantScope.withTenant(new MapSqlParameterSource().addValue("orderId", orderId).addValue("lineNumber", calculated.lineNumber())
                    .addValue("productId", line.productId()).addValue("sku", line.sku()).addValue("description", line.description())
                    .addValue("unit", line.unit()).addValue("quantity", line.quantity()).addValue("unitPrice", line.unitPrice())
                    .addValue("subtotal", calculated.subtotal()).addValue("tax", calculated.tax()).addValue("total", calculated.total())));
        }
    }

    private PurchaseOrderViewData aggregate(PurchaseOrderHeader header) {
        List<ItemData> items = jdbc.query("""
                SELECT line_number,product_id,sku_snapshot,description_snapshot,unit_snapshot,quantity,unit_price,
                       line_subtotal,line_tax,line_total FROM purchase_order_items
                WHERE purchase_order_id=:id AND EXISTS (SELECT 1 FROM purchase_orders po WHERE po.id=:id AND po.tenant_id=:tenantId)
                ORDER BY line_number
                """, TenantScope.withTenant(new MapSqlParameterSource("id", header.id())), (rs, row) -> new ItemData(
                rs.getInt("line_number"), (Long) rs.getObject("product_id"), rs.getString("sku_snapshot"),
                rs.getString("description_snapshot"), rs.getString("unit_snapshot"), rs.getBigDecimal("quantity"),
                rs.getBigDecimal("unit_price"), rs.getBigDecimal("line_subtotal"), rs.getBigDecimal("line_tax"),
                rs.getBigDecimal("line_total")));
        List<DocumentData> documents = jdbc.query("""
                SELECT d.public_id,pod.document_role,d.original_name,d.media_type,d.extension,d.size_bytes,
                       d.sha256_hex,d.version,d.created_at FROM purchase_order_documents pod
                JOIN stored_documents d ON d.id=pod.document_id
                WHERE pod.purchase_order_id=:id AND d.tenant_id=:tenantId AND pod.active=TRUE AND d.active=TRUE
                ORDER BY pod.created_at DESC,d.id DESC
                """, TenantScope.withTenant(new MapSqlParameterSource("id", header.id())), (rs, row) -> new DocumentData(
                rs.getString("public_id"), PurchaseOrderDocumentRole.valueOf(rs.getString("document_role")),
                rs.getString("original_name"), rs.getString("media_type"), rs.getString("extension"),
                rs.getLong("size_bytes"), rs.getString("sha256_hex"), rs.getLong("version"), instant(rs, "created_at")));
        return new PurchaseOrderViewData(header, items, documents);
    }

    private PurchaseOrderHeader mapHeader(ResultSet rs) throws SQLException {
        return new PurchaseOrderHeader(rs.getLong("id"), rs.getString("public_id"), rs.getString("order_number"),
                rs.getLong("customer_id"), rs.getString("customer_public_id"), rs.getString("customer_name"),
                rs.getString("customer_document_type"), rs.getString("customer_document_number"),
                rs.getString("quotation_public_id"), rs.getString("quotation_number"),
                (Long) rs.getObject("quotation_owner_worker_id"), rs.getString("invoice_public_id"),
                rs.getString("invoice_number"), rs.getLong("responsible_worker_id"),
                rs.getString("responsible_worker_display_name"), PurchaseOrderStatus.valueOf(rs.getString("status")),
                rs.getObject("issue_date", LocalDate.class), rs.getObject("expected_delivery_date", LocalDate.class),
                rs.getObject("delivered_date", LocalDate.class), rs.getString("currency_code"),
                rs.getBigDecimal("exchange_rate_to_pen"), rs.getBoolean("includes_tax"), rs.getBigDecimal("tax_rate"),
                rs.getBigDecimal("subtotal_amount"), rs.getBigDecimal("tax_amount"), rs.getBigDecimal("total_amount"),
                rs.getString("notes"), rs.getLong("version"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private DocumentLinkData mapDocument(ResultSet rs) throws SQLException {
        return new DocumentLinkData(rs.getString("public_id"), PurchaseOrderDocumentRole.valueOf(rs.getString("document_role")),
                rs.getString("original_name"), rs.getString("media_type"), rs.getString("extension"), rs.getLong("size_bytes"),
                rs.getString("sha256_hex"), rs.getLong("version"), instant(rs, "created_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String escapeLike(String value) { return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }

    public record PurchaseOrderInputData(String orderNumber, LocalDate issueDate, LocalDate expectedDeliveryDate,
                                         LocalDate deliveredDate, String currencyCode, BigDecimal exchangeRateToPen,
                                         boolean includesTax, BigDecimal taxRate, String notes) {}
    public record LineInputData(Long productId, String sku, String description, String unit,
                                BigDecimal quantity, BigDecimal unitPrice) {}
    public record PurchaseOrderHeader(long id, String publicId, String orderNumber, long customerId,
                                      String customerPublicId, String customerName, String customerDocumentType,
                                      String customerDocumentNumber, String quotationPublicId, String quotationNumber,
                                      Long quotationOwnerWorkerId, String invoicePublicId, String invoiceNumber,
                                      long responsibleWorkerId, String responsibleWorkerDisplayName,
                                      PurchaseOrderStatus status, LocalDate issueDate, LocalDate expectedDeliveryDate,
                                      LocalDate deliveredDate, String currencyCode, BigDecimal exchangeRateToPen,
                                      boolean includesTax, BigDecimal taxRate, BigDecimal subtotalAmount,
                                      BigDecimal taxAmount, BigDecimal totalAmount, String notes, long version,
                                      Instant createdAt, Instant updatedAt) {}
    public record ItemData(int lineNumber, Long productId, String sku, String description, String unit,
                           BigDecimal quantity, BigDecimal unitPrice, BigDecimal subtotal, BigDecimal tax,
                           BigDecimal total) {}
    public record DocumentData(String publicId, PurchaseOrderDocumentRole role, String originalName, String mediaType,
                               String extension, long sizeBytes, String sha256Hex, long version, Instant createdAt) {}
    public record DocumentLinkData(String publicId, PurchaseOrderDocumentRole role, String originalName, String mediaType,
                                   String extension, long sizeBytes, String sha256Hex, long version, Instant createdAt) {}
    public record PurchaseOrderViewData(PurchaseOrderHeader header, List<ItemData> items, List<DocumentData> documents) {}
    public record PageData(List<PurchaseOrderViewData> items, long totalItems, int page, int size, int totalPages) {}
}

package pe.giova.warranty;

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

public class WarrantyRepository {
    private static final String HEADER = """
            SELECT wa.id,wa.public_id,wa.customer_id,c.public_id AS customer_public_id,c.business_name AS customer_name,
                   wa.purchase_order_id,po.public_id AS purchase_order_public_id,po.order_number,
                   wa.purchase_order_item_id,poi.line_number AS purchase_order_line_number,
                   wa.product_id,p.sku AS product_sku,p.name AS product_name,q.owner_worker_id AS quotation_owner_worker_id,
                   wa.serial_number,wa.starts_on,wa.ends_on,wa.status,wa.conditions,wa.notes,wa.version,
                   wa.created_at,wa.updated_at
            FROM warranties wa JOIN customers c ON c.id=wa.customer_id AND c.tenant_id=wa.tenant_id
            LEFT JOIN purchase_orders po ON po.id=wa.purchase_order_id AND po.tenant_id=wa.tenant_id
            LEFT JOIN purchase_order_items poi ON poi.id=wa.purchase_order_item_id AND poi.purchase_order_id=po.id
            LEFT JOIN products p ON p.id=wa.product_id AND p.tenant_id=wa.tenant_id
            LEFT JOIN quotations q ON q.id=po.quotation_id AND q.tenant_id=wa.tenant_id
            """;
    private final NamedParameterJdbcTemplate jdbc;

    public WarrantyRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public PageData find(String query, String status, String customerPublicId, String purchaseOrderPublicId,
                         Long quotationOwnerId, int page, int size) {
        String where = """
                WHERE wa.tenant_id=:tenantId
                  AND (:pattern IS NULL OR LOWER(CONCAT_WS(' ', c.business_name,c.document_number,
                       po.order_number,wa.serial_number,p.sku,p.name)) LIKE :pattern ESCAPE '\\\\')
                  AND (:status IS NULL OR wa.status=:status)
                  AND (:customerPublicId IS NULL OR c.public_id=:customerPublicId)
                  AND (:purchaseOrderPublicId IS NULL OR po.public_id=:purchaseOrderPublicId)
                  AND (:quotationOwnerId IS NULL OR q.owner_worker_id=:quotationOwnerId)
                """;
        var params = baseParams(query, status, customerPublicId, purchaseOrderPublicId, quotationOwnerId)
                .addValue("pageSize", size).addValue("offset", (long) page * size);
        List<WarrantyViewData> items = jdbc.query(HEADER + where
                + " ORDER BY wa.updated_at DESC,wa.id DESC LIMIT :pageSize OFFSET :offset",
                TenantScope.withTenant(params), (rs, row) -> mapHeader(rs)).stream().map(this::aggregate).toList();
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM warranties wa JOIN customers c ON c.id=wa.customer_id "
                + "LEFT JOIN purchase_orders po ON po.id=wa.purchase_order_id LEFT JOIN products p ON p.id=wa.product_id "
                + "LEFT JOIN quotations q ON q.id=po.quotation_id AND q.tenant_id=wa.tenant_id " + where, TenantScope.withTenant(params), Long.class);
        return new PageData(items, total, page, size, total == 0 ? 0 : (int) Math.ceil((double) total / size));
    }

    public Optional<WarrantyViewData> findByPublicId(String publicId, Long quotationOwnerId) {
        return jdbc.query(HEADER + " WHERE wa.tenant_id=:tenantId AND wa.public_id=:publicId AND (:quotationOwnerId IS NULL OR q.owner_worker_id=:quotationOwnerId)",
                TenantScope.withTenant(new MapSqlParameterSource().addValue("publicId", publicId).addValue("quotationOwnerId", quotationOwnerId)),
                (rs, row) -> mapHeader(rs)).stream().findFirst().map(this::aggregate);
    }

    public Optional<WarrantyHeader> lockByPublicId(String publicId, Long quotationOwnerId) {
        return jdbc.query(HEADER + " WHERE wa.tenant_id=:tenantId AND wa.public_id=:publicId AND (:quotationOwnerId IS NULL OR q.owner_worker_id=:quotationOwnerId) FOR UPDATE",
                TenantScope.withTenant(new MapSqlParameterSource().addValue("publicId", publicId).addValue("quotationOwnerId", quotationOwnerId)),
                (rs, row) -> mapHeader(rs)).stream().findFirst();
    }

    public int expireDue(LocalDate today) {
        return jdbc.update("UPDATE warranties SET status='EXPIRED',version=version+1,updated_at=CURRENT_TIMESTAMP(6) "
                + "WHERE tenant_id=:tenantId AND status='ACTIVE' AND ends_on<:today",
                TenantScope.withTenant(new MapSqlParameterSource("today", today)));
    }

    public PurchaseOrderData purchaseOrder(String publicId) {
        return jdbc.query("""
                SELECT po.id,po.public_id,po.order_number,po.customer_id,c.public_id AS customer_public_id,
                       q.public_id AS quotation_public_id,q.owner_worker_id
                FROM purchase_orders po JOIN customers c ON c.id=po.customer_id AND c.tenant_id=po.tenant_id
                LEFT JOIN quotations q ON q.id=po.quotation_id AND q.tenant_id=po.tenant_id
                WHERE po.tenant_id=:tenantId AND po.public_id=:publicId
                """, TenantScope.withTenant(new MapSqlParameterSource("publicId", publicId)), (rs, row) -> new PurchaseOrderData(
                rs.getLong("id"), rs.getString("public_id"), rs.getString("order_number"), rs.getLong("customer_id"),
                rs.getString("customer_public_id"), rs.getString("quotation_public_id"), (Long) rs.getObject("owner_worker_id")))
                .stream().findFirst().orElseThrow(() -> new NotFoundException("Orden no encontrada"));
    }

    public Optional<PurchaseOrderItemData> purchaseOrderItem(long orderId, int lineNumber) {
        return jdbc.query("SELECT id,purchase_order_id,line_number,product_id,sku_snapshot,description_snapshot FROM purchase_order_items "
                        + "WHERE purchase_order_id=:orderId AND line_number=:lineNumber AND EXISTS "
                        + "(SELECT 1 FROM purchase_orders po WHERE po.id=purchase_order_items.purchase_order_id AND po.tenant_id=:tenantId)",
                TenantScope.withTenant(new MapSqlParameterSource().addValue("orderId", orderId).addValue("lineNumber", lineNumber)), (rs, row) -> new PurchaseOrderItemData(
                        rs.getLong("id"), rs.getLong("purchase_order_id"), rs.getInt("line_number"),
                        (Long) rs.getObject("product_id"), rs.getString("sku_snapshot"), rs.getString("description_snapshot")))
                .stream().findFirst();
    }

    public String insert(long customerId, Long purchaseOrderId, Long purchaseOrderItemId, Long productId,
                         WarrantyInputData input) {
        String publicId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO warranties(tenant_id,public_id,customer_id,purchase_order_id,purchase_order_item_id,product_id,
                    serial_number,starts_on,ends_on,status,conditions,notes)
                VALUES (:tenantId,:publicId,:customerId,:purchaseOrderId,:purchaseOrderItemId,:productId,:serialNumber,
                    :startsOn,:endsOn,'ACTIVE',:conditions,:notes)
                """, TenantScope.withTenant(params(publicId, customerId, purchaseOrderId, purchaseOrderItemId, productId, input)));
        return publicId;
    }

    public int updateActive(String publicId, long version, long customerId, Long purchaseOrderId,
                            Long purchaseOrderItemId, Long productId, WarrantyInputData input) {
        return jdbc.update("""
                UPDATE warranties SET customer_id=:customerId,purchase_order_id=:purchaseOrderId,
                  purchase_order_item_id=:purchaseOrderItemId,product_id=:productId,serial_number=:serialNumber,
                  starts_on=:startsOn,ends_on=:endsOn,conditions=:conditions,notes=:notes,version=version+1,
                  updated_at=CURRENT_TIMESTAMP(6)
                WHERE public_id=:publicId AND tenant_id=:tenantId AND status='ACTIVE' AND version=:version
                """, TenantScope.withTenant(params(publicId, customerId, purchaseOrderId, purchaseOrderItemId, productId, input).addValue("version", version)));
    }

    public int changeStatus(String publicId, long version, WarrantyStatus target) {
        return jdbc.update("UPDATE warranties SET status=:status,version=version+1,updated_at=CURRENT_TIMESTAMP(6) "
                + "WHERE public_id=:publicId AND tenant_id=:tenantId AND version=:version", TenantScope.withTenant(new MapSqlParameterSource().addValue("publicId", publicId)
                .addValue("version", version).addValue("status", target.name())));
    }

    public int touchAfterDocument(String publicId, long version) {
        return jdbc.update("UPDATE warranties SET version=version+1,updated_at=CURRENT_TIMESTAMP(6) "
                + "WHERE public_id=:publicId AND tenant_id=:tenantId AND status='ACTIVE' AND version=:version",
                TenantScope.withTenant(new MapSqlParameterSource().addValue("publicId", publicId).addValue("version", version)));
    }

    public long idOf(String publicId) {
        return jdbc.query("SELECT id FROM warranties WHERE public_id=:publicId AND tenant_id=:tenantId", TenantScope.withTenant(new MapSqlParameterSource("publicId", publicId)),
                (rs, row) -> rs.getLong("id")).stream().findFirst().orElseThrow(() -> new NotFoundException("Garantía no encontrada"));
    }

    public int deactivateActiveDocuments(long warrantyId, WarrantyDocumentRole role) {
        return jdbc.update("""
                UPDATE warranty_documents SET active=FALSE WHERE warranty_id=:warrantyId AND document_role=:documentRole AND active=TRUE
                  AND EXISTS (SELECT 1 FROM warranties wa WHERE wa.id=warranty_documents.warranty_id AND wa.tenant_id=:tenantId)
                """,
                TenantScope.withTenant(new MapSqlParameterSource().addValue("warrantyId", warrantyId).addValue("documentRole", role.name())));
    }

    public void linkDocument(long warrantyId, long documentId, WarrantyDocumentRole role) {
        jdbc.update("""
                INSERT INTO warranty_documents(warranty_id,document_id,document_role,active)
                SELECT :warrantyId,:documentId,:documentRole,TRUE FROM warranties wa
                JOIN stored_documents d ON d.id=:documentId AND d.tenant_id=:tenantId
                WHERE wa.id=:warrantyId AND wa.tenant_id=:tenantId
                """,
                TenantScope.withTenant(new MapSqlParameterSource().addValue("warrantyId", warrantyId).addValue("documentId", documentId).addValue("documentRole", role.name())));
    }

    public Optional<DocumentLinkData> activeDocument(String warrantyPublicId, String documentPublicId) {
        return jdbc.query("""
                SELECT d.public_id,wd.document_role,d.original_name,d.media_type,d.extension,d.size_bytes,d.sha256_hex,d.version,d.created_at
                FROM warranty_documents wd JOIN warranties wa ON wa.id=wd.warranty_id JOIN stored_documents d ON d.id=wd.document_id
                WHERE wa.tenant_id=:tenantId AND d.tenant_id=wa.tenant_id AND wa.public_id=:warrantyPublicId
                  AND d.public_id=:documentPublicId AND wd.active=TRUE AND d.active=TRUE
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("warrantyPublicId", warrantyPublicId).addValue("documentPublicId", documentPublicId)),
                (rs, row) -> mapDocument(rs)).stream().findFirst();
    }

    private MapSqlParameterSource baseParams(String query, String status, String customerPublicId,
                                              String purchaseOrderPublicId, Long quotationOwnerId) {
        String pattern = query == null || query.isBlank() ? null : "%" + escapeLike(query.trim().toLowerCase()) + "%";
        return new MapSqlParameterSource().addValue("pattern", pattern).addValue("status", status)
                .addValue("customerPublicId", customerPublicId).addValue("purchaseOrderPublicId", purchaseOrderPublicId)
                .addValue("quotationOwnerId", quotationOwnerId);
    }

    private MapSqlParameterSource params(String publicId, long customerId, Long purchaseOrderId, Long purchaseOrderItemId,
                                         Long productId, WarrantyInputData input) {
        return new MapSqlParameterSource().addValue("publicId", publicId).addValue("customerId", customerId)
                .addValue("purchaseOrderId", purchaseOrderId).addValue("purchaseOrderItemId", purchaseOrderItemId)
                .addValue("productId", productId).addValue("serialNumber", input.serialNumber())
                .addValue("startsOn", input.startsOn()).addValue("endsOn", input.endsOn())
                .addValue("conditions", input.conditions()).addValue("notes", input.notes());
    }

    private WarrantyViewData aggregate(WarrantyHeader header) {
        List<DocumentData> documents = jdbc.query("""
                SELECT d.public_id,wd.document_role,d.original_name,d.media_type,d.extension,d.size_bytes,d.sha256_hex,d.version,d.created_at
                FROM warranty_documents wd JOIN stored_documents d ON d.id=wd.document_id
                WHERE wd.warranty_id=:id AND wd.active=TRUE AND d.active=TRUE ORDER BY wd.created_at DESC,d.id DESC
                """, TenantScope.withTenant(new MapSqlParameterSource("id", header.id())), (rs, row) -> new DocumentData(
                rs.getString("public_id"), WarrantyDocumentRole.valueOf(rs.getString("document_role")), rs.getString("original_name"),
                rs.getString("media_type"), rs.getString("extension"), rs.getLong("size_bytes"), rs.getString("sha256_hex"),
                rs.getLong("version"), instant(rs, "created_at")));
        return new WarrantyViewData(header, documents);
    }

    private WarrantyHeader mapHeader(ResultSet rs) throws SQLException {
        return new WarrantyHeader(rs.getLong("id"), rs.getString("public_id"), rs.getLong("customer_id"),
                rs.getString("customer_public_id"), rs.getString("customer_name"), rs.getString("purchase_order_public_id"),
                rs.getString("order_number"), (Integer) rs.getObject("purchase_order_line_number"),
                (Long) rs.getObject("product_id"), rs.getString("product_sku"), rs.getString("product_name"),
                rs.getString("serial_number"), rs.getObject("starts_on", LocalDate.class), rs.getObject("ends_on", LocalDate.class),
                WarrantyStatus.valueOf(rs.getString("status")), rs.getString("conditions"), rs.getString("notes"),
                rs.getLong("version"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private DocumentLinkData mapDocument(ResultSet rs) throws SQLException {
        return new DocumentLinkData(rs.getString("public_id"), WarrantyDocumentRole.valueOf(rs.getString("document_role")),
                rs.getString("original_name"), rs.getString("media_type"), rs.getString("extension"), rs.getLong("size_bytes"),
                rs.getString("sha256_hex"), rs.getLong("version"), instant(rs, "created_at"));
    }
    private static Instant instant(ResultSet rs, String column) throws SQLException { var value=rs.getTimestamp(column); return value == null ? null : value.toInstant(); }
    private static String escapeLike(String value) { return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }

    public record WarrantyInputData(String serialNumber, LocalDate startsOn, LocalDate endsOn, String conditions, String notes) {}
    public record PurchaseOrderData(long id, String publicId, String orderNumber, long customerId, String customerPublicId,
                                    String quotationPublicId, Long quotationOwnerWorkerId) {}
    public record PurchaseOrderItemData(long id, long purchaseOrderId, int lineNumber, Long productId, String sku, String description) {}
    public record WarrantyHeader(long id, String publicId, long customerId, String customerPublicId, String customerName,
                                 String purchaseOrderPublicId, String orderNumber, Integer purchaseOrderLineNumber,
                                 Long productId, String productSku, String productName, String serialNumber,
                                 LocalDate startsOn, LocalDate endsOn, WarrantyStatus status, String conditions, String notes,
                                 long version, Instant createdAt, Instant updatedAt) {}
    public record DocumentData(String publicId, WarrantyDocumentRole role, String originalName, String mediaType,
                               String extension, long sizeBytes, String sha256Hex, long version, Instant createdAt) {}
    public record DocumentLinkData(String publicId, WarrantyDocumentRole role, String originalName, String mediaType,
                                   String extension, long sizeBytes, String sha256Hex, long version, Instant createdAt) {}
    public record WarrantyViewData(WarrantyHeader header, List<DocumentData> documents) {}
    public record PageData(List<WarrantyViewData> items, long totalItems, int page, int size, int totalPages) {}
}

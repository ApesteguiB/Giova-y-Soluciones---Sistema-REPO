package pe.giova.delivery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import pe.giova.admin.NotFoundException;
import pe.giova.auth.TenantScope;

public class DeliveryRepository {
    private static final String HEADER = """
            SELECT d.id, d.public_id, d.delivery_number, d.purchase_order_id,
                   po.public_id AS purchase_order_public_id, po.order_number,
                   po.issue_date AS order_issue_date, q.owner_worker_id AS quotation_owner_worker_id,
                   d.customer_id, c.public_id AS customer_public_id, c.business_name AS customer_name,
                   d.delivered_at, d.received_by_name, d.received_by_document, d.status, d.notes, d.version,
                   d.created_at, d.updated_at
            FROM delivery_records d
            JOIN purchase_orders po ON po.id=d.purchase_order_id AND po.tenant_id=d.tenant_id
            JOIN customers c ON c.id=d.customer_id AND c.tenant_id=d.tenant_id
            LEFT JOIN quotations q ON q.id=po.quotation_id AND q.tenant_id=d.tenant_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public DeliveryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public PageData find(String query, String status, String customerPublicId, String purchaseOrderPublicId,
                         Long quotationOwnerId, int page, int size) {
        String where = """
                WHERE d.tenant_id=:tenantId
                  AND (:pattern IS NULL OR LOWER(CONCAT_WS(' ', d.delivery_number, po.order_number,
                       c.business_name, c.document_number)) LIKE :pattern ESCAPE '\\\\')
                  AND (:status IS NULL OR d.status=:status)
                  AND (:customerPublicId IS NULL OR c.public_id=:customerPublicId)
                  AND (:purchaseOrderPublicId IS NULL OR po.public_id=:purchaseOrderPublicId)
                  AND (:quotationOwnerId IS NULL OR q.owner_worker_id=:quotationOwnerId)
                """;
        var params = baseParams(query, status, customerPublicId, purchaseOrderPublicId, quotationOwnerId)
                .addValue("pageSize", size).addValue("offset", (long) page * size);
        List<DeliveryViewData> items = jdbc.query(HEADER + where
                + " ORDER BY d.updated_at DESC,d.id DESC LIMIT :pageSize OFFSET :offset",
                TenantScope.withTenant(params), (rs, row) -> mapHeader(rs)).stream().map(this::aggregate).toList();
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM delivery_records d JOIN purchase_orders po ON po.id=d.purchase_order_id "
                + "JOIN customers c ON c.id=d.customer_id LEFT JOIN quotations q ON q.id=po.quotation_id " + where,
                TenantScope.withTenant(params), Long.class);
        return new PageData(items, total, page, size, total == 0 ? 0 : (int) Math.ceil((double) total / size));
    }

    public Optional<DeliveryViewData> findByPublicId(String publicId, Long quotationOwnerId) {
        return jdbc.query(HEADER + " WHERE d.tenant_id=:tenantId AND d.public_id=:publicId AND (:quotationOwnerId IS NULL OR q.owner_worker_id=:quotationOwnerId)",
                TenantScope.withTenant(new MapSqlParameterSource().addValue("publicId", publicId).addValue("quotationOwnerId", quotationOwnerId)),
                (rs, row) -> mapHeader(rs)).stream().findFirst().map(this::aggregate);
    }

    public Optional<DeliveryHeader> lockByPublicId(String publicId, Long quotationOwnerId) {
        return jdbc.query(HEADER + " WHERE d.tenant_id=:tenantId AND d.public_id=:publicId AND (:quotationOwnerId IS NULL OR q.owner_worker_id=:quotationOwnerId) FOR UPDATE",
                TenantScope.withTenant(new MapSqlParameterSource().addValue("publicId", publicId).addValue("quotationOwnerId", quotationOwnerId)),
                (rs, row) -> mapHeader(rs)).stream().findFirst();
    }

    public DeliveryOrderData purchaseOrder(String publicId) {
        return jdbc.query("""
                SELECT po.id,po.public_id,po.order_number,po.customer_id,c.public_id AS customer_public_id,
                       po.issue_date,po.status,q.public_id AS quotation_public_id,q.owner_worker_id
                FROM purchase_orders po JOIN customers c ON c.id=po.customer_id AND c.tenant_id=po.tenant_id
                LEFT JOIN quotations q ON q.id=po.quotation_id AND q.tenant_id=po.tenant_id
                WHERE po.tenant_id=:tenantId AND po.public_id=:publicId
                """, TenantScope.withTenant(new MapSqlParameterSource("publicId", publicId)), (rs, row) -> new DeliveryOrderData(
                rs.getLong("id"), rs.getString("public_id"), rs.getString("order_number"), rs.getLong("customer_id"),
                rs.getString("customer_public_id"), rs.getObject("issue_date", java.time.LocalDate.class),
                pe.giova.purchaseorder.PurchaseOrderStatus.valueOf(rs.getString("status")),
                rs.getString("quotation_public_id"), (Long) rs.getObject("owner_worker_id")))
                .stream().findFirst().orElseThrow(() -> new NotFoundException("Orden no encontrada"));
    }

    public String insert(String deliveryNumber, DeliveryOrderData order, DeliveryInputData input) {
        String publicId = UUID.randomUUID().toString();
        var key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO delivery_records(tenant_id,public_id,delivery_number,purchase_order_id,customer_id,delivered_at,
                    received_by_name,received_by_document,status,notes)
                VALUES (:tenantId,:publicId,:deliveryNumber,:orderId,:customerId,:deliveredAt,:receivedByName,:receivedByDocument,'DRAFT',:notes)
                """, TenantScope.withTenant(params(publicId, deliveryNumber, order, input)), key);
        return publicId;
    }

    public int updateDraft(String publicId, long version, String deliveryNumber, DeliveryOrderData order,
                           DeliveryInputData input) {
        return jdbc.update("""
                UPDATE delivery_records SET delivery_number=:deliveryNumber,purchase_order_id=:orderId,
                  customer_id=:customerId,delivered_at=:deliveredAt,received_by_name=:receivedByName,
                  received_by_document=:receivedByDocument,notes=:notes,version=version+1,
                  updated_at=CURRENT_TIMESTAMP(6)
                WHERE public_id=:publicId AND tenant_id=:tenantId AND status='DRAFT' AND version=:version
                """, TenantScope.withTenant(params(publicId, deliveryNumber, order, input).addValue("version", version)));
    }

    public int changeStatus(String publicId, long version, DeliveryStatus target, Instant deliveredAt) {
        return jdbc.update("""
                UPDATE delivery_records SET status=:status,
                  delivered_at=CASE WHEN :status='SIGNED' THEN :deliveredAt ELSE delivered_at END,
                  version=version+1,updated_at=CURRENT_TIMESTAMP(6)
                WHERE public_id=:publicId AND tenant_id=:tenantId AND version=:version
                """, new MapSqlParameterSource().addValue("publicId", publicId).addValue("version", version)
                .addValue("status", target.name()).addValue("deliveredAt", deliveredAt).addValue("tenantId", TenantScope.requireTenantId()));
    }

    public int touchAfterDocument(String publicId, long version) {
        return jdbc.update("UPDATE delivery_records SET version=version+1,updated_at=CURRENT_TIMESTAMP(6) "
                + "WHERE public_id=:publicId AND tenant_id=:tenantId AND status='DRAFT' AND version=:version",
                TenantScope.withTenant(new MapSqlParameterSource().addValue("publicId", publicId).addValue("version", version)));
    }

    public long idOf(String publicId) {
        return jdbc.query("SELECT id FROM delivery_records WHERE public_id=:publicId AND tenant_id=:tenantId", TenantScope.withTenant(new MapSqlParameterSource("publicId", publicId)),
                (rs, row) -> rs.getLong("id")).stream().findFirst().orElseThrow(() -> new NotFoundException("Acta no encontrada"));
    }

    public int deactivateActiveDocuments(long deliveryId, DeliveryDocumentRole role) {
        return jdbc.update("""
                UPDATE delivery_documents SET active=FALSE WHERE delivery_id=:deliveryId AND document_role=:documentRole AND active=TRUE
                  AND EXISTS (SELECT 1 FROM delivery_records dr WHERE dr.id=delivery_documents.delivery_id AND dr.tenant_id=:tenantId)
                """,
                TenantScope.withTenant(new MapSqlParameterSource().addValue("deliveryId", deliveryId).addValue("documentRole", role.name())));
    }

    public void linkDocument(long deliveryId, long documentId, DeliveryDocumentRole role) {
        jdbc.update("""
                INSERT INTO delivery_documents(delivery_id,document_id,document_role,active)
                SELECT :deliveryId,:documentId,:documentRole,TRUE FROM delivery_records dr
                JOIN stored_documents d ON d.id=:documentId AND d.tenant_id=:tenantId
                WHERE dr.id=:deliveryId AND dr.tenant_id=:tenantId
                """,
                TenantScope.withTenant(new MapSqlParameterSource().addValue("deliveryId", deliveryId).addValue("documentId", documentId).addValue("documentRole", role.name())));
    }

    public Optional<DocumentLinkData> activeDocument(String deliveryPublicId, String documentPublicId) {
        return jdbc.query("""
                SELECT d.public_id,dd.document_role,d.original_name,d.media_type,d.extension,d.size_bytes,d.sha256_hex,d.version,d.created_at
                FROM delivery_documents dd JOIN delivery_records dr ON dr.id=dd.delivery_id JOIN stored_documents d ON d.id=dd.document_id
                WHERE dr.tenant_id=:tenantId AND d.tenant_id=dr.tenant_id AND dr.public_id=:deliveryPublicId
                  AND d.public_id=:documentPublicId AND dd.active=TRUE AND d.active=TRUE
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("deliveryPublicId", deliveryPublicId).addValue("documentPublicId", documentPublicId)),
                (rs, row) -> mapDocument(rs)).stream().findFirst();
    }

    private MapSqlParameterSource baseParams(String query, String status, String customerPublicId,
                                              String purchaseOrderPublicId, Long quotationOwnerId) {
        String pattern = query == null || query.isBlank() ? null : "%" + escapeLike(query.trim().toLowerCase()) + "%";
        return new MapSqlParameterSource().addValue("pattern", pattern).addValue("status", status)
                .addValue("customerPublicId", customerPublicId).addValue("purchaseOrderPublicId", purchaseOrderPublicId)
                .addValue("quotationOwnerId", quotationOwnerId);
    }

    private MapSqlParameterSource params(String publicId, String deliveryNumber, DeliveryOrderData order, DeliveryInputData input) {
        return new MapSqlParameterSource().addValue("publicId", publicId).addValue("deliveryNumber", deliveryNumber)
                .addValue("orderId", order.id()).addValue("customerId", order.customerId()).addValue("deliveredAt", input.deliveredAt())
                .addValue("receivedByName", input.receivedByName()).addValue("receivedByDocument", input.receivedByDocument())
                .addValue("notes", input.notes());
    }

    private DeliveryViewData aggregate(DeliveryHeader header) {
        List<DocumentData> documents = jdbc.query("""
                SELECT d.public_id,dd.document_role,d.original_name,d.media_type,d.extension,d.size_bytes,d.sha256_hex,d.version,d.created_at
                FROM delivery_documents dd JOIN stored_documents d ON d.id=dd.document_id
                WHERE dd.delivery_id=:id AND dd.active=TRUE AND d.active=TRUE ORDER BY dd.created_at DESC,d.id DESC
                """, TenantScope.withTenant(new MapSqlParameterSource("id", header.id())), (rs, row) -> new DocumentData(
                rs.getString("public_id"), DeliveryDocumentRole.valueOf(rs.getString("document_role")), rs.getString("original_name"),
                rs.getString("media_type"), rs.getString("extension"), rs.getLong("size_bytes"), rs.getString("sha256_hex"),
                rs.getLong("version"), instant(rs, "created_at")));
        return new DeliveryViewData(header, documents);
    }

    private DeliveryHeader mapHeader(ResultSet rs) throws SQLException {
        return new DeliveryHeader(rs.getLong("id"), rs.getString("public_id"), rs.getString("delivery_number"),
                rs.getLong("purchase_order_id"), rs.getString("purchase_order_public_id"), rs.getString("order_number"),
                rs.getLong("customer_id"), rs.getString("customer_public_id"), rs.getString("customer_name"),
                rs.getObject("order_issue_date", java.time.LocalDate.class), (Long) rs.getObject("quotation_owner_worker_id"),
                instant(rs, "delivered_at"), rs.getString("received_by_name"), rs.getString("received_by_document"),
                DeliveryStatus.valueOf(rs.getString("status")), rs.getString("notes"), rs.getLong("version"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private DocumentLinkData mapDocument(ResultSet rs) throws SQLException {
        return new DocumentLinkData(rs.getString("public_id"), DeliveryDocumentRole.valueOf(rs.getString("document_role")),
                rs.getString("original_name"), rs.getString("media_type"), rs.getString("extension"), rs.getLong("size_bytes"),
                rs.getString("sha256_hex"), rs.getLong("version"), instant(rs, "created_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    private static String escapeLike(String value) { return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }

    public record DeliveryInputData(Instant deliveredAt, String receivedByName, String receivedByDocument, String notes) {}
    public record DeliveryOrderData(long id, String publicId, String orderNumber, long customerId, String customerPublicId,
                                   java.time.LocalDate issueDate, pe.giova.purchaseorder.PurchaseOrderStatus status,
                                   String quotationPublicId, Long quotationOwnerWorkerId) {}
    public record DeliveryHeader(long id, String publicId, String deliveryNumber, long purchaseOrderId,
                                 String purchaseOrderPublicId, String orderNumber, long customerId,
                                 String customerPublicId, String customerName, java.time.LocalDate orderIssueDate,
                                 Long quotationOwnerWorkerId, Instant deliveredAt, String receivedByName,
                                 String receivedByDocument, DeliveryStatus status, String notes, long version,
                                 Instant createdAt, Instant updatedAt) {}
    public record DocumentData(String publicId, DeliveryDocumentRole role, String originalName, String mediaType,
                               String extension, long sizeBytes, String sha256Hex, long version, Instant createdAt) {}
    public record DocumentLinkData(String publicId, DeliveryDocumentRole role, String originalName, String mediaType,
                                   String extension, long sizeBytes, String sha256Hex, long version, Instant createdAt) {}
    public record DeliveryViewData(DeliveryHeader header, List<DocumentData> documents) {}
    public record PageData(List<DeliveryViewData> items, long totalItems, int page, int size, int totalPages) {}
}

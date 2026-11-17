package pe.giova.customer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import pe.giova.customer.api.CustomerDtos.CustomerInput;
import pe.giova.customer.api.CustomerDtos.CustomerPage;
import pe.giova.customer.api.CustomerDtos.CustomerUpdate;
import pe.giova.customer.api.CustomerDtos.CustomerView;
import pe.giova.auth.TenantScope;

public class CustomerRepository {
    private static final String SELECT = """
            SELECT c.public_id, c.owner_worker_id, w.display_name AS owner_display_name,
                   c.customer_type, c.document_type, c.document_number, c.business_name,
                   c.contact_name, c.email, c.phone, c.address, c.notes, c.active, c.version,
                   c.created_at, c.updated_at
            FROM customers c
            JOIN workers w ON w.id = c.owner_worker_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public CustomerRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CustomerPage find(String query, Boolean active, Long ownerId, int page, int size) {
        String pattern = query == null || query.isBlank() ? null : "%" + escapeLike(query.trim().toLowerCase()) + "%";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("pattern", pattern)
                .addValue("active", active)
                .addValue("ownerId", ownerId)
                .addValue("pageSize", size)
                .addValue("offset", (long) page * size);
        String where = """
                WHERE c.tenant_id=:tenantId
                  AND (:pattern IS NULL OR LOWER(CONCAT_WS(' ', c.business_name, c.contact_name,
                       c.document_number, c.email, c.phone)) LIKE :pattern ESCAPE '\\\\')
                  AND (:active IS NULL OR c.active = :active)
                  AND (:ownerId IS NULL OR c.owner_worker_id = :ownerId)
                """;
        List<CustomerView> items = jdbc.query(SELECT + where
                + " ORDER BY c.updated_at DESC, c.id DESC LIMIT :pageSize OFFSET :offset", TenantScope.withTenant(params), (rs, row) -> map(rs));
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM customers c " + where, TenantScope.withTenant(params), Long.class);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new CustomerPage(items, total, page, size, totalPages);
    }

    public Optional<CustomerView> findByPublicId(String publicId, Long ownerId) {
        String where = " WHERE c.tenant_id=:tenantId AND c.public_id = :publicId AND (:ownerId IS NULL OR c.owner_worker_id = :ownerId)";
        return jdbc.query(SELECT + where, TenantScope.withTenant(new MapSqlParameterSource().addValue("publicId", publicId)
                .addValue("ownerId", ownerId)), (rs, row) -> map(rs)).stream().findFirst();
    }

    public boolean existsDocument(String documentType, String documentNumber, String excludedPublicId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM customers
                WHERE tenant_id=:tenantId AND document_type = :documentType AND document_number = :documentNumber
                  AND (:excludedPublicId IS NULL OR public_id <> :excludedPublicId)
                """, new MapSqlParameterSource().addValue("documentType", documentType)
                .addValue("documentNumber", documentNumber).addValue("excludedPublicId", excludedPublicId)
                .addValue("tenantId", TenantScope.requireTenantId()), Long.class);
        return count != null && count > 0;
    }

    public String insert(long ownerWorkerId, CustomerInput input) {
        String publicId = UUID.randomUUID().toString();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO customers (tenant_id, public_id, owner_worker_id, customer_type, document_type,
                                       document_number, business_name, contact_name, email, phone, address, notes)
                VALUES (:tenantId, :publicId, :ownerWorkerId, :customerType, :documentType, :documentNumber,
                        :businessName, :contactName, :email, :phone, :address, :notes)
                """, TenantScope.withTenant(params(publicId, ownerWorkerId, input)), keyHolder);
        return publicId;
    }

    public int update(String publicId, Long ownerId, CustomerUpdate input) {
        return jdbc.update("""
                UPDATE customers
                SET customer_type = :customerType, document_type = :documentType,
                    document_number = :documentNumber, business_name = :businessName,
                    contact_name = :contactName, email = :email, phone = :phone,
                    address = :address, notes = :notes, version = version + 1,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE public_id = :publicId AND tenant_id=:tenantId AND (:ownerId IS NULL OR owner_worker_id = :ownerId)
                  AND version = :version
                """, new MapSqlParameterSource()
                .addValue("publicId", publicId).addValue("ownerId", ownerId)
                .addValue("customerType", input.customerType()).addValue("documentType", input.documentType())
                .addValue("documentNumber", input.documentNumber()).addValue("businessName", input.businessName())
                .addValue("contactName", input.contactName()).addValue("email", input.email())
                .addValue("phone", input.phone()).addValue("address", input.address())
                .addValue("notes", input.notes()).addValue("version", input.version()).addValue("tenantId", TenantScope.requireTenantId()));
    }

    public int setActive(String publicId, boolean active, long version) {
        return jdbc.update("""
                UPDATE customers
                SET active = :active, version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE public_id = :publicId AND tenant_id=:tenantId AND version = :version
                """, new MapSqlParameterSource().addValue("publicId", publicId)
                .addValue("active", active).addValue("version", version).addValue("tenantId", TenantScope.requireTenantId()));
    }

    private MapSqlParameterSource params(String publicId, long ownerWorkerId, CustomerInput input) {
        return new MapSqlParameterSource().addValue("publicId", publicId).addValue("ownerWorkerId", ownerWorkerId)
                .addValue("customerType", input.customerType()).addValue("documentType", input.documentType())
                .addValue("documentNumber", input.documentNumber()).addValue("businessName", input.businessName())
                .addValue("contactName", input.contactName()).addValue("email", input.email())
                .addValue("phone", input.phone()).addValue("address", input.address()).addValue("notes", input.notes());
    }

    private CustomerView map(ResultSet rs) throws SQLException {
        return new CustomerView(rs.getString("public_id"), rs.getLong("owner_worker_id"),
                rs.getString("owner_display_name"), rs.getString("customer_type"), rs.getString("document_type"),
                rs.getString("document_number"), rs.getString("business_name"), rs.getString("contact_name"),
                rs.getString("email"), rs.getString("phone"), rs.getString("address"), rs.getString("notes"),
                rs.getBoolean("active"), rs.getLong("version"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

package pe.giova.platform;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformRepository {
    private static final String TENANT_SELECT = """
            SELECT t.id, t.public_id, t.code, t.slug, t.legal_name, t.display_name, t.tax_id,
                   t.address, t.contact_email, t.default_currency, t.tax_rate, t.active, t.version,
                   b.primary_color, b.secondary_color, b.accent_color, b.pdf_header_text,
                   b.logo_storage_key,
                   (SELECT COUNT(*) FROM worker_memberships wm
                    WHERE wm.tenant_id=t.id AND wm.role_code='ADMIN') AS admin_count
            FROM tenants t
            JOIN tenant_branding b ON b.tenant_id=t.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PlatformRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PlatformDtos.TenantView> findAllTenants() {
        return jdbc.query(TENANT_SELECT + " ORDER BY t.display_name, t.id",
                new MapSqlParameterSource(), (rs, row) -> tenant(rs));
    }

    public Optional<PlatformDtos.TenantView> findTenant(long id) {
        return jdbc.query(TENANT_SELECT + " WHERE t.id=:id",
                new MapSqlParameterSource("id", id), (rs, row) -> tenant(rs)).stream().findFirst();
    }

    public Optional<PlatformDtos.TenantView> findTenantByCode(String code) {
        return jdbc.query(TENANT_SELECT + " WHERE t.code=:code",
                new MapSqlParameterSource("code", code), (rs, row) -> tenant(rs)).stream().findFirst();
    }

    public PlatformDtos.TenantView createTenant(TenantData tenant) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO tenants (public_id, code, slug, legal_name, display_name, tax_id, address,
                                     contact_email, default_currency, tax_rate, active, version)
                VALUES (:publicId, :code, :slug, :legalName, :displayName, :taxId, :address,
                        :contactEmail, :currency, :taxRate, TRUE, 0)
                """, tenantParams(tenant).addValue("publicId", UUID.randomUUID().toString()), keyHolder,
                new String[] {"id"});
        Number id = keyHolder.getKey();
        if (id == null) throw new IllegalStateException("No se pudo obtener el id de la empresa creada");
        long tenantId = id.longValue();
        jdbc.update("""
                INSERT INTO tenant_branding (tenant_id, primary_color, secondary_color, accent_color, pdf_header_text)
                VALUES (:tenantId, :primaryColor, :secondaryColor, :accentColor, :pdfHeaderText)
                """, tenantParams(tenant).addValue("tenantId", tenantId));
        return findTenant(tenantId).orElseThrow(() -> new IllegalStateException("No se pudo leer la empresa creada"));
    }

    public boolean updateTenant(long id, TenantData tenant, long expectedVersion) {
        int changed = jdbc.update("""
                UPDATE tenants SET legal_name=:legalName, display_name=:displayName, tax_id=:taxId,
                    address=:address, contact_email=:contactEmail, default_currency=:currency,
                    tax_rate=:taxRate, version=version+1
                WHERE id=:id AND version=:version
                """, tenantParams(tenant).addValue("id", id).addValue("version", expectedVersion));
        if (changed != 1) return false;
        jdbc.update("""
                UPDATE tenant_branding SET primary_color=:primaryColor, secondary_color=:secondaryColor,
                    accent_color=:accentColor, pdf_header_text=:pdfHeaderText, version=version+1
                WHERE tenant_id=:tenantId
                """, tenantParams(tenant).addValue("tenantId", id));
        return true;
    }

    public boolean setTenantActive(long id, boolean active, long expectedVersion) {
        return jdbc.update("""
                UPDATE tenants SET active=:active, version=version+1
                WHERE id=:id AND version=:version
                """, new MapSqlParameterSource().addValue("active", active)
                .addValue("id", id).addValue("version", expectedVersion)) == 1;
    }

    public boolean setLogoKey(long id, String key) {
        return jdbc.update("""
                UPDATE tenant_branding SET logo_storage_key=:key, version=version+1
                WHERE tenant_id=:id
                """, new MapSqlParameterSource().addValue("key", key).addValue("id", id)) == 1;
    }

    public Optional<String> findLogoKey(long id) {
        return Optional.ofNullable(jdbc.query("SELECT logo_storage_key FROM tenant_branding WHERE tenant_id=:id",
                new MapSqlParameterSource("id", id), rs -> rs.next() ? rs.getString(1) : null));
    }

    public boolean isTenantActive(long id) {
        return Boolean.TRUE.equals(jdbc.query("SELECT active FROM tenants WHERE id=:id",
                new MapSqlParameterSource("id", id), rs -> rs.next() ? rs.getBoolean(1) : null));
    }

    public Optional<Long> findActiveTenantId(String code) {
        Long id = jdbc.query("SELECT id FROM tenants WHERE code=:code AND active=TRUE",
                new MapSqlParameterSource("code", code), rs -> rs.next() ? rs.getLong(1) : null);
        return Optional.ofNullable(id);
    }

    private static PlatformDtos.TenantView tenant(ResultSet rs) throws SQLException {
        return new PlatformDtos.TenantView(rs.getLong("id"), rs.getString("public_id"), rs.getString("code"),
                rs.getString("slug"), rs.getString("legal_name"), rs.getString("display_name"),
                rs.getString("tax_id"), rs.getString("address"), rs.getString("contact_email"),
                rs.getString("default_currency"), rs.getBigDecimal("tax_rate"), rs.getBoolean("active"),
                rs.getLong("version"), rs.getString("primary_color"), rs.getString("secondary_color"),
                rs.getString("accent_color"), rs.getString("pdf_header_text"),
                rs.getString("logo_storage_key") != null, rs.getLong("admin_count"));
    }

    private static MapSqlParameterSource tenantParams(TenantData t) {
        return new MapSqlParameterSource().addValue("code", t.code()).addValue("slug", t.slug())
                .addValue("legalName", t.legalName()).addValue("displayName", t.displayName())
                .addValue("taxId", t.taxId()).addValue("address", t.address())
                .addValue("contactEmail", t.contactEmail()).addValue("currency", t.defaultCurrency())
                .addValue("taxRate", t.taxRate()).addValue("primaryColor", t.primaryColor())
                .addValue("secondaryColor", t.secondaryColor()).addValue("accentColor", t.accentColor())
                .addValue("pdfHeaderText", t.pdfHeaderText());
    }

    public record TenantData(String code, String slug, String legalName, String displayName, String taxId,
                             String address, String contactEmail, String defaultCurrency, BigDecimal taxRate,
                             String primaryColor, String secondaryColor, String accentColor, String pdfHeaderText) {}
}

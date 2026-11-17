package pe.giova.site;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Lee la configuración del sitio público y el mapa de dominios a empresas. */
public class PublicSiteRepository {
    private static final String SELECT_SITE = """
            SELECT t.id AS tenant_id, t.code, t.tax_id, s.template_key, s.site_title, s.brand_name,
                   s.legal_name, s.tagline, s.logo_asset, s.monogram_text, s.phone, s.whatsapp_number,
                   s.whatsapp_message, s.email, s.address, s.maps_url, s.hours, s.footer_note,
                   s.facebook_url, s.instagram_url, s.linkedin_url, s.claims_enabled, s.catalog_enabled,
                   b.primary_color, b.secondary_color, b.accent_color, b.logo_storage_key
            FROM tenants t
            JOIN tenant_sites s ON s.tenant_id = t.id AND s.active = TRUE
            LEFT JOIN tenant_branding b ON b.tenant_id = t.id
            WHERE t.active = TRUE
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PublicSiteRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Resuelve la empresa a partir del nombre de host ya normalizado. */
    public Optional<Long> findTenantIdByHostname(String hostname) {
        return jdbc.query("""
                SELECT h.tenant_id FROM tenant_hosts h
                JOIN tenants t ON t.id = h.tenant_id AND t.active = TRUE
                JOIN tenant_sites s ON s.tenant_id = t.id AND s.active = TRUE
                WHERE h.hostname = :hostname
                """, new MapSqlParameterSource("hostname", hostname),
                (rs, row) -> rs.getLong("tenant_id")).stream().findFirst();
    }

    /**
     * Solo para el modo local y para el valor por defecto configurado: acepta
     * código o slug. Los códigos llevan guion bajo (SOLUCIONES_TI) y los slugs
     * guion medio, así que se prueban las dos formas y no hay que recordar cuál
     * usa cada empresa.
     */
    public Optional<Long> findTenantIdByCodeOrSlug(String value) {
        String code = value.replace('-', '_').toUpperCase(Locale.ROOT);
        String slug = value.toLowerCase(Locale.ROOT);
        return jdbc.query("""
                SELECT t.id FROM tenants t
                JOIN tenant_sites s ON s.tenant_id = t.id AND s.active = TRUE
                WHERE t.active = TRUE AND (t.code = :code OR t.slug = :slug)
                """, new MapSqlParameterSource().addValue("code", code).addValue("slug", slug),
                (rs, row) -> rs.getLong("id")).stream().findFirst();
    }

    public Optional<SiteRecord> findSite(long tenantId) {
        return jdbc.query(SELECT_SITE + " AND t.id = :tenantId",
                new MapSqlParameterSource("tenantId", tenantId), (rs, row) -> map(rs)).stream().findFirst();
    }

    private static SiteRecord map(ResultSet rs) throws SQLException {
        return new SiteRecord(rs.getLong("tenant_id"), rs.getString("code"), rs.getString("tax_id"),
                rs.getString("template_key"), rs.getString("site_title"), rs.getString("brand_name"),
                rs.getString("legal_name"), rs.getString("tagline"), rs.getString("logo_asset"),
                rs.getString("monogram_text"), rs.getString("phone"), rs.getString("whatsapp_number"),
                rs.getString("whatsapp_message"), rs.getString("email"), rs.getString("address"),
                rs.getString("maps_url"), rs.getString("hours"), rs.getString("footer_note"),
                rs.getString("facebook_url"), rs.getString("instagram_url"), rs.getString("linkedin_url"),
                rs.getBoolean("claims_enabled"), rs.getBoolean("catalog_enabled"),
                rs.getString("primary_color"), rs.getString("secondary_color"), rs.getString("accent_color"),
                rs.getString("logo_storage_key"));
    }

    public record SiteRecord(long tenantId, String code, String taxId, String templateKey, String siteTitle,
                             String brandName, String legalName, String tagline, String logoAsset,
                             String monogramText, String phone, String whatsappNumber, String whatsappMessage,
                             String email, String address, String mapsUrl, String hours, String footerNote,
                             String facebookUrl, String instagramUrl, String linkedinUrl,
                             boolean claimsEnabled, boolean catalogEnabled, String primaryColor,
                             String secondaryColor, String accentColor, String logoStorageKey) {}
}

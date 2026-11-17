package pe.giova.tenant;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class TenantBrandingRepository {
    private static final String SELECT = """
            SELECT t.id AS tenant_id, t.display_name, b.primary_color, b.secondary_color, b.accent_color,
                   b.logo_storage_key, b.pdf_header_text
            FROM tenants t
            JOIN tenant_branding b ON b.tenant_id = t.id
            WHERE t.id = :tenantId AND t.active = TRUE
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public TenantBrandingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Branding> findActive(long tenantId) {
        return jdbc.query(SELECT, new MapSqlParameterSource("tenantId", tenantId),
                (rs, row) -> map(rs)).stream().findFirst();
    }

    private static Branding map(ResultSet rs) throws SQLException {
        return new Branding(rs.getLong("tenant_id"), rs.getString("display_name"),
                rs.getString("primary_color"), rs.getString("secondary_color"), rs.getString("accent_color"),
                rs.getString("logo_storage_key"), rs.getString("pdf_header_text"));
    }

    public record Branding(long tenantId, String displayName, String primaryColor, String secondaryColor,
                           String accentColor, String logoStorageKey, String pdfHeaderText) {}
}

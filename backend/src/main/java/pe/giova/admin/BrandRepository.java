package pe.giova.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import pe.giova.admin.api.AdminDtos.BrandInput;
import pe.giova.admin.api.AdminDtos.BrandUpdate;
import pe.giova.admin.api.AdminDtos.BrandView;
import pe.giova.auth.TenantScope;

public class BrandRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public BrandRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<BrandView> find(String query, Boolean active) {
        String pattern = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("pattern", pattern)
                .addValue("active", active);
        return jdbc.query("""
                SELECT id, name, slug, logo_url, display_color, featured, display_order, active, version,
                       created_at, updated_at
                FROM brands
                WHERE tenant_id = :tenantId
                  AND (:pattern IS NULL OR LOWER(name) LIKE :pattern OR LOWER(slug) LIKE :pattern)
                  AND (:active IS NULL OR active = :active)
                ORDER BY display_order ASC, id ASC
                """, TenantScope.withTenant(params), (rs, row) -> map(rs));
    }

    public Optional<BrandView> findById(long id) {
        return jdbc.query("""
                SELECT id, name, slug, logo_url, display_color, featured, display_order, active, version,
                       created_at, updated_at
                FROM brands WHERE id = :id AND tenant_id = :tenantId
                """, TenantScope.withTenant(new MapSqlParameterSource("id", id)), (rs, row) -> map(rs)).stream().findFirst();
    }

    public long insert(BrandInput input) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO brands (tenant_id, name, slug, logo_url, display_color, featured, display_order)
                VALUES (:tenantId, :name, :slug, :logoUrl, :displayColor, :featured, :displayOrder)
                """, TenantScope.withTenant(params(input)), keyHolder);
        return keyHolder.getKey().longValue();
    }

    public int update(long id, BrandUpdate input) {
        return jdbc.update("""
                UPDATE brands
                SET name = :name, logo_url = :logoUrl, display_color = :displayColor,
                    featured = :featured, display_order = :displayOrder,
                    version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = :id AND tenant_id = :tenantId AND version = :version
                """, TenantScope.withTenant(new MapSqlParameterSource()
                .addValue("id", id).addValue("name", input.name()).addValue("logoUrl", input.logoUrl())
                .addValue("displayColor", input.displayColor()).addValue("featured", input.featured())
                .addValue("displayOrder", order(input.displayOrder())).addValue("version", input.version())
                ));
    }

    public int setActive(long id, boolean active, long version) {
        jdbc.query("SELECT id FROM brands WHERE id = :id AND tenant_id=:tenantId FOR UPDATE",
                TenantScope.withTenant(new MapSqlParameterSource("id", id)), (rs, row) -> rs.getLong("id"));
        if (!findById(id).isPresent()) {
            return 0;
        }
        if (!active) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE brand_id = :id AND tenant_id=:tenantId AND active = TRUE",
                    TenantScope.withTenant(new MapSqlParameterSource("id", id)), Long.class);
            if (count != null && count > 0) {
                throw new ConflictException("La marca tiene productos activos asociados", "CLASSIFIER_IN_USE");
            }
        }
        return jdbc.update("""
                UPDATE brands SET active = :active, version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = :id AND tenant_id=:tenantId AND version = :version
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("id", id).addValue("active", active)
                .addValue("version", version)));
    }

    private MapSqlParameterSource params(BrandInput input) {
        return new MapSqlParameterSource().addValue("name", input.name().trim())
                .addValue("slug", input.slug().trim()).addValue("logoUrl", input.logoUrl())
                .addValue("displayColor", input.displayColor()).addValue("featured", input.featured())
                .addValue("displayOrder", order(input.displayOrder()));
    }

    private BrandView map(ResultSet rs) throws SQLException {
        return new BrandView(rs.getLong("id"), rs.getString("name"), rs.getString("slug"),
                rs.getString("logo_url"), rs.getString("display_color"), rs.getBoolean("featured"),
                rs.getInt("display_order"), rs.getBoolean("active"), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static int order(Integer value) {
        return value == null ? 0 : value;
    }
}

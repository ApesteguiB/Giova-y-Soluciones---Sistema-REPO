package pe.giova.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import pe.giova.admin.api.AdminDtos.CategoryInput;
import pe.giova.admin.api.AdminDtos.CategoryUpdate;
import pe.giova.admin.api.AdminDtos.CategoryView;
import pe.giova.auth.TenantScope;

public class CategoryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public CategoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CategoryView> find(String query, Boolean active) {
        String pattern = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        return jdbc.query("""
                SELECT id, name, slug, icon_key, display_order, active, version, created_at, updated_at
                FROM categories
                WHERE tenant_id = :tenantId
                  AND (:pattern IS NULL OR LOWER(name) LIKE :pattern OR LOWER(slug) LIKE :pattern)
                  AND (:active IS NULL OR active = :active)
                ORDER BY display_order ASC, id ASC
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("pattern", pattern).addValue("active", active)),
                (rs, row) -> map(rs));
    }

    public Optional<CategoryView> findById(long id) {
        return jdbc.query("""
                SELECT id, name, slug, icon_key, display_order, active, version, created_at, updated_at
                FROM categories WHERE id = :id AND tenant_id=:tenantId
                """, TenantScope.withTenant(new MapSqlParameterSource("id", id)), (rs, row) -> map(rs)).stream().findFirst();
    }

    public long insert(CategoryInput input) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO categories (tenant_id, name, slug, icon_key, display_order)
                VALUES (:tenantId, :name, :slug, :iconKey, :displayOrder)
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("name", input.name().trim())
                .addValue("slug", input.slug().trim()).addValue("iconKey", input.iconKey())
                .addValue("displayOrder", order(input.displayOrder()))), keyHolder);
        return keyHolder.getKey().longValue();
    }

    public int update(long id, CategoryUpdate input) {
        return jdbc.update("""
                UPDATE categories
                SET name = :name, icon_key = :iconKey, display_order = :displayOrder,
                    version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = :id AND tenant_id=:tenantId AND version = :version
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("id", id).addValue("name", input.name().trim())
                .addValue("iconKey", input.iconKey()).addValue("displayOrder", order(input.displayOrder()))
                .addValue("version", input.version())));
    }

    public int setActive(long id, boolean active, long version) {
        jdbc.query("SELECT id FROM categories WHERE id = :id AND tenant_id=:tenantId FOR UPDATE",
                TenantScope.withTenant(new MapSqlParameterSource("id", id)), (rs, row) -> rs.getLong("id"));
        if (findById(id).isEmpty()) {
            return 0;
        }
        if (!active) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE category_id = :id AND tenant_id=:tenantId AND active = TRUE",
                    TenantScope.withTenant(new MapSqlParameterSource("id", id)), Long.class);
            if (count != null && count > 0) {
                throw new ConflictException("La categoría tiene productos activos asociados", "CLASSIFIER_IN_USE");
            }
        }
        return jdbc.update("""
                UPDATE categories SET active = :active, version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = :id AND tenant_id=:tenantId AND version = :version
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("id", id).addValue("active", active)
                .addValue("version", version)));
    }

    private CategoryView map(ResultSet rs) throws SQLException {
        return new CategoryView(rs.getLong("id"), rs.getString("name"), rs.getString("slug"),
                rs.getString("icon_key"), rs.getInt("display_order"), rs.getBoolean("active"),
                rs.getLong("version"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static int order(Integer value) {
        return value == null ? 0 : value;
    }
}

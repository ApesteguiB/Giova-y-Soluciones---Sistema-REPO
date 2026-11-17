package pe.giova.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import pe.giova.admin.api.AdminDtos.AdminPage;
import pe.giova.admin.api.AdminDtos.ProductInput;
import pe.giova.admin.api.AdminDtos.ProductUpdate;
import pe.giova.admin.api.AdminDtos.ProductView;
import pe.giova.auth.TenantScope;

public class ProductRepository {
    private static final String FROM = """
            FROM products p
            JOIN brands b ON b.id = p.brand_id AND b.tenant_id=p.tenant_id
            JOIN categories c ON c.id = p.category_id AND c.tenant_id=p.tenant_id
            WHERE p.tenant_id=:tenantId
              AND (:pattern IS NULL OR LOWER(CONCAT_WS(' ', p.name, p.sku, p.slug, b.name, c.name)) LIKE :pattern ESCAPE '\\\\')
              AND (:active IS NULL OR p.active = :active)
              AND (:brandId IS NULL OR p.brand_id = :brandId)
              AND (:categoryId IS NULL OR p.category_id = :categoryId)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ProductRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AdminPage<ProductView> find(String query, Boolean active, Long brandId, Long categoryId, int page, int size) {
        String pattern = query == null || query.isBlank() ? null : "%" + escapeLike(query.trim().toLowerCase()) + "%";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("pattern", pattern)
                .addValue("active", active).addValue("brandId", brandId).addValue("categoryId", categoryId)
                .addValue("pageSize", size).addValue("offset", (long) page * size);
        String select = """
                SELECT p.id, p.slug, p.sku, p.name, p.description, p.brand_id, b.name AS brand_name,
                       p.category_id, c.name AS category_name, p.badge, p.image_url, p.display_order,
                       p.active, p.version, p.created_at, p.updated_at
                """ + FROM + " ORDER BY p.display_order ASC, p.id ASC LIMIT :pageSize OFFSET :offset";
        List<ProductView> items = jdbc.query(select, TenantScope.withTenant(params), (rs, row) -> map(rs));
        long total = jdbc.queryForObject("SELECT COUNT(*) " + FROM, TenantScope.withTenant(params), Long.class);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new AdminPage<>(items, total, page, size, totalPages);
    }

    public Optional<ProductView> findById(long id) {
        return jdbc.query("""
                SELECT p.id, p.slug, p.sku, p.name, p.description, p.brand_id, b.name AS brand_name,
                       p.category_id, c.name AS category_name, p.badge, p.image_url, p.display_order,
                       p.active, p.version, p.created_at, p.updated_at
                FROM products p JOIN brands b ON b.id = p.brand_id AND b.tenant_id=p.tenant_id
                JOIN categories c ON c.id = p.category_id AND c.tenant_id=p.tenant_id
                WHERE p.id = :id AND p.tenant_id=:tenantId
                """, TenantScope.withTenant(new MapSqlParameterSource("id", id)), (rs, row) -> map(rs)).stream().findFirst();
    }

    public long insert(ProductInput input) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO products (tenant_id, slug, sku, name, description, brand_id, category_id, badge, image_url, display_order)
                VALUES (:tenantId, :slug, :sku, :name, :description, :brandId, :categoryId, :badge, :imageUrl, :displayOrder)
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("slug", input.slug().trim())
                .addValue("sku", input.sku().trim()).addValue("name", input.name().trim())
                .addValue("description", input.description().trim()).addValue("brandId", input.brandId())
                .addValue("categoryId", input.categoryId()).addValue("badge", input.badge())
                .addValue("imageUrl", input.imageUrl()).addValue("displayOrder", order(input.displayOrder()))), keyHolder);
        return keyHolder.getKey().longValue();
    }

    public int update(long id, ProductUpdate input) {
        return jdbc.update("""
                UPDATE products
                SET sku = :sku, name = :name, description = :description, brand_id = :brandId,
                    category_id = :categoryId, badge = :badge, image_url = :imageUrl,
                    display_order = :displayOrder, version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = :id AND tenant_id=:tenantId AND version = :version
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("id", id).addValue("sku", input.sku().trim())
                .addValue("name", input.name().trim()).addValue("description", input.description().trim())
                .addValue("brandId", input.brandId()).addValue("categoryId", input.categoryId())
                .addValue("badge", input.badge()).addValue("imageUrl", input.imageUrl())
                .addValue("displayOrder", order(input.displayOrder())).addValue("version", input.version())));
    }

    public int setActive(long id, boolean active, long version) {
        return jdbc.update("""
                UPDATE products SET active = :active, version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = :id AND tenant_id=:tenantId AND version = :version
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("id", id).addValue("active", active)
                .addValue("version", version)));
    }

    public void lockAndValidateParents(long brandId, long categoryId) {
        Long brand = jdbc.query("SELECT id FROM brands WHERE id = :id AND tenant_id=:tenantId FOR UPDATE",
                TenantScope.withTenant(new MapSqlParameterSource("id", brandId)), (rs, row) -> rs.getLong("id"))
                .stream().findFirst().orElseThrow(() -> new NotFoundException("Marca no encontrada"));
        Long category = jdbc.query("SELECT id FROM categories WHERE id = :id AND tenant_id=:tenantId FOR UPDATE",
                TenantScope.withTenant(new MapSqlParameterSource("id", categoryId)), (rs, row) -> rs.getLong("id"))
                .stream().findFirst().orElseThrow(() -> new NotFoundException("Categoría no encontrada"));
        Boolean brandActive = jdbc.queryForObject("SELECT active FROM brands WHERE id = :id AND tenant_id=:tenantId",
                TenantScope.withTenant(new MapSqlParameterSource("id", brand)), Boolean.class);
        Boolean categoryActive = jdbc.queryForObject("SELECT active FROM categories WHERE id = :id AND tenant_id=:tenantId",
                TenantScope.withTenant(new MapSqlParameterSource("id", category)), Boolean.class);
        if (!Boolean.TRUE.equals(brandActive) || !Boolean.TRUE.equals(categoryActive)) {
            throw new ConflictException("La marca y categoría deben estar activas", "PARENT_INACTIVE");
        }
    }

    public boolean parentsAreActive(long brandId, long categoryId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM brands b JOIN categories c
                WHERE b.id = :brandId AND c.id = :categoryId AND b.tenant_id=:tenantId
                  AND c.tenant_id=:tenantId AND b.active = TRUE AND c.active = TRUE
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("brandId", brandId).addValue("categoryId", categoryId)), Integer.class);
        return count != null && count == 1;
    }

    private ProductView map(ResultSet rs) throws SQLException {
        return new ProductView(rs.getLong("id"), rs.getString("slug"), rs.getString("sku"), rs.getString("name"),
                rs.getString("description"), rs.getLong("brand_id"), rs.getString("brand_name"),
                rs.getLong("category_id"), rs.getString("category_name"), rs.getString("badge"),
                rs.getString("image_url"), rs.getInt("display_order"), rs.getBoolean("active"), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static int order(Integer value) {
        return value == null ? 0 : value;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

package pe.giova.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import pe.giova.catalog.api.CatalogDtos.Brand;
import pe.giova.catalog.api.CatalogDtos.Category;
import pe.giova.catalog.api.CatalogDtos.Page;
import pe.giova.catalog.api.CatalogDtos.Product;
import pe.giova.catalog.api.CatalogDtos.ProductBrand;
import pe.giova.catalog.api.CatalogDtos.ProductCategory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pe.giova.site.PublicSiteResolver;

/**
 * Catálogo del sitio público. La empresa sale del dominio de la petición
 * (ver {@link PublicSiteResolver}), nunca de un parámetro del visitante.
 */
public class CatalogRepository {
    private static final String FROM_PUBLIC = """
            FROM products p
            JOIN brands b ON b.id = p.brand_id AND b.tenant_id=p.tenant_id AND b.active = TRUE
            JOIN categories c ON c.id = p.category_id AND c.tenant_id=p.tenant_id AND c.active = TRUE
            WHERE p.tenant_id=:tenantId AND p.active = TRUE
              AND (:category IS NULL OR c.slug = :category)
              AND (:pattern IS NULL OR LOWER(CONCAT_WS(' ', p.name, p.sku, b.name, p.description)) LIKE :pattern ESCAPE '\\\\')
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PublicSiteResolver siteResolver;

    public CatalogRepository(NamedParameterJdbcTemplate jdbc, PublicSiteResolver siteResolver) {
        this.jdbc = jdbc;
        this.siteResolver = siteResolver;
    }

    public Page<Product> findProducts(String pattern, String category, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", siteResolver.requireTenantId())
                .addValue("pattern", pattern)
                .addValue("category", category)
                .addValue("pageSize", size)
                .addValue("offset", (long) page * size);
        String select = """
                SELECT CAST(p.id AS CHAR) AS product_id, p.slug, p.sku, p.name, p.description,
                       p.badge, p.image_url,
                       CAST(b.id AS CHAR) AS brand_id, b.name AS brand_name, b.slug AS brand_slug,
                       CAST(c.id AS CHAR) AS category_id, c.name AS category_name, c.slug AS category_slug,
                       c.icon_key
                """ + FROM_PUBLIC + " ORDER BY p.display_order ASC, p.id ASC LIMIT :pageSize OFFSET :offset";
        String count = "SELECT COUNT(*) " + FROM_PUBLIC;
        List<Product> items = jdbc.query(select, params, (rs, row) -> mapProduct(rs));
        long total = jdbc.queryForObject(count, params, Long.class);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new Page<>(items, total, page, size, totalPages);
    }

    public List<Category> findCategories() {
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", siteResolver.requireTenantId());
        return jdbc.query("""
                SELECT CAST(c.id AS CHAR) AS category_id, c.name, c.slug, c.icon_key, c.display_order,
                       COUNT(b.id) AS product_count
                FROM categories c
                LEFT JOIN products p ON p.category_id = c.id AND p.tenant_id=c.tenant_id AND p.active = TRUE
                LEFT JOIN brands b ON b.id = p.brand_id AND b.tenant_id=p.tenant_id AND b.active = TRUE
                WHERE c.tenant_id=:tenantId AND c.active = TRUE
                GROUP BY c.id, c.name, c.slug, c.icon_key, c.display_order
                ORDER BY c.display_order ASC, c.id ASC
                """, params, (rs, row) -> new Category(
                rs.getString("category_id"), rs.getString("name"), rs.getString("slug"),
                rs.getString("icon_key"), rs.getInt("display_order"), rs.getLong("product_count")));
    }

    public List<Brand> findBrands(boolean featured) {
        String sql = """
                SELECT CAST(id AS CHAR) AS brand_id, name, slug, logo_url, display_color,
                       featured, display_order
                FROM brands
                WHERE tenant_id=:tenantId AND active = TRUE AND (:featured = FALSE OR featured = TRUE)
                ORDER BY display_order ASC, id ASC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", siteResolver.requireTenantId())
                .addValue("featured", featured);
        return jdbc.query(sql, params, (rs, row) -> new Brand(
                rs.getString("brand_id"), rs.getString("name"), rs.getString("slug"),
                rs.getString("logo_url"), rs.getString("display_color"),
                rs.getBoolean("featured"), rs.getInt("display_order")));
    }

    private Product mapProduct(ResultSet rs) throws SQLException {
        ProductBrand brand = new ProductBrand(rs.getString("brand_id"), rs.getString("brand_name"),
                rs.getString("brand_slug"));
        ProductCategory category = new ProductCategory(rs.getString("category_id"), rs.getString("category_name"),
                rs.getString("category_slug"), rs.getString("icon_key"));
        return new Product(rs.getString("product_id"), rs.getString("slug"), rs.getString("sku"),
                rs.getString("name"), rs.getString("description"), rs.getString("badge"),
                rs.getString("image_url"), brand, category);
    }
}

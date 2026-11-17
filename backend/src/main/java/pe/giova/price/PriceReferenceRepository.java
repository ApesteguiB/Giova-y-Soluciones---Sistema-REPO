package pe.giova.price;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import pe.giova.price.api.PriceReferenceDtos.PriceReferenceInput;
import pe.giova.price.api.PriceReferenceDtos.PriceReferencePage;
import pe.giova.price.api.PriceReferenceDtos.PriceReferenceView;
import pe.giova.auth.TenantScope;

public class PriceReferenceRepository {
    private static final String SELECT = """
            SELECT r.id, r.product_id, p.sku AS product_sku, p.name AS product_name,
                   r.recorded_by_worker_id, w.display_name AS recorded_by_display_name,
                   r.supplier_name, r.source_reference, r.currency_code, r.unit_price,
                   r.includes_tax, r.tax_rate, r.exchange_rate_to_pen, r.effective_at,
                   r.notes, r.created_at
            FROM product_price_references r
            JOIN products p ON p.id = r.product_id AND p.tenant_id=r.tenant_id
            JOIN workers w ON w.id = r.recorded_by_worker_id
            """;
    private final NamedParameterJdbcTemplate jdbc;

    public PriceReferenceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PriceReferencePage find(Long productId, Instant from, Instant to, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("productId", productId)
                .addValue("from", from).addValue("to", to).addValue("pageSize", size)
                .addValue("offset", (long) page * size);
        String where = """
                WHERE r.tenant_id=:tenantId
                  AND (:productId IS NULL OR r.product_id = :productId)
                  AND (:from IS NULL OR r.effective_at >= :from)
                  AND (:to IS NULL OR r.effective_at <= :to)
                """;
        List<PriceReferenceView> items = jdbc.query(SELECT + where
                + " ORDER BY r.effective_at DESC, r.id DESC LIMIT :pageSize OFFSET :offset", TenantScope.withTenant(params), (rs, row) -> map(rs));
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM product_price_references r " + where, TenantScope.withTenant(params), Long.class);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PriceReferencePage(items, total, page, size, totalPages);
    }

    public Optional<PriceReferenceView> findById(long id) {
        return jdbc.query(SELECT + " WHERE r.id = :id AND r.tenant_id=:tenantId", TenantScope.withTenant(new MapSqlParameterSource("id", id)),
                (rs, row) -> map(rs)).stream().findFirst();
    }

    public boolean productExists(long productId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id = :id AND tenant_id=:tenantId",
                TenantScope.withTenant(new MapSqlParameterSource("id", productId)), Long.class);
        return count != null && count > 0;
    }

    public long insert(long workerId, PriceReferenceInput input) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO product_price_references
                    (tenant_id, product_id, recorded_by_worker_id, supplier_name, source_reference, currency_code,
                     unit_price, includes_tax, tax_rate, exchange_rate_to_pen, effective_at, notes)
                VALUES (:tenantId, :productId, :workerId, :supplierName, :sourceReference, :currencyCode,
                        :unitPrice, :includesTax, :taxRate, :exchangeRateToPen, :effectiveAt, :notes)
                """, TenantScope.withTenant(params(workerId, input)), keyHolder);
        Number id = keyHolder.getKey();
        if (id == null) throw new IllegalStateException("No se pudo obtener el id de la referencia de precio");
        return id.longValue();
    }

    public int update(long id, PriceReferenceInput input) {
        return jdbc.update("""
                UPDATE product_price_references
                SET product_id = :productId, supplier_name = :supplierName, source_reference = :sourceReference,
                    currency_code = :currencyCode, unit_price = :unitPrice, includes_tax = :includesTax,
                    tax_rate = :taxRate, exchange_rate_to_pen = :exchangeRateToPen,
                    effective_at = :effectiveAt, notes = :notes
                WHERE id = :id AND tenant_id=:tenantId
                """, TenantScope.withTenant(params(0, input).addValue("id", id)));
    }

    private MapSqlParameterSource params(long workerId, PriceReferenceInput input) {
        return new MapSqlParameterSource().addValue("productId", input.productId())
                .addValue("workerId", workerId).addValue("supplierName", input.supplierName())
                .addValue("sourceReference", input.sourceReference()).addValue("currencyCode", input.currencyCode())
                .addValue("unitPrice", input.unitPrice()).addValue("includesTax", input.includesTax())
                .addValue("taxRate", input.taxRate()).addValue("exchangeRateToPen", input.exchangeRateToPen())
                .addValue("effectiveAt", input.effectiveAt()).addValue("notes", input.notes());
    }

    private PriceReferenceView map(ResultSet rs) throws SQLException {
        return new PriceReferenceView(rs.getLong("id"), rs.getLong("product_id"), rs.getString("product_sku"),
                rs.getString("product_name"), rs.getLong("recorded_by_worker_id"),
                rs.getString("recorded_by_display_name"), rs.getString("supplier_name"),
                rs.getString("source_reference"), rs.getString("currency_code"), rs.getBigDecimal("unit_price"),
                rs.getBoolean("includes_tax"), rs.getBigDecimal("tax_rate"), rs.getBigDecimal("exchange_rate_to_pen"),
                rs.getTimestamp("effective_at").toInstant(), rs.getString("notes"),
                rs.getTimestamp("created_at").toInstant());
    }
}

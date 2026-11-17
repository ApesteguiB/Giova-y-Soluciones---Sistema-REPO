package pe.giova.price;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pe.giova.price.api.PriceHistoryDtos.LastPurchase;
import pe.giova.price.api.PriceHistoryDtos.LostSale;
import pe.giova.price.api.PriceHistoryDtos.WonSale;
import pe.giova.auth.TenantScope;

/**
 * Consultas del historial comercial de un producto.
 *
 * <p>Todo sale de lo que ya se registra al operar: las cotizaciones con su
 * resultado, los precios de competencia que anota el vendedor y las facturas de
 * compra que sube el contador. No hay tabla nueva ni doble digitación.
 */
public class PriceHistoryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public PriceHistoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ProductRow> product(long productId) {
        return jdbc.query("SELECT id, sku, name FROM products WHERE id = :id AND tenant_id=:tenantId",
                        TenantScope.withTenant(new MapSqlParameterSource("id", productId)),
                        (rs, row) -> new ProductRow(rs.getLong("id"), rs.getString("sku"), rs.getString("name")))
                .stream().findFirst();
    }

    public Optional<Long> productIdBySku(String sku) {
        return jdbc.query("SELECT id FROM products WHERE LOWER(sku) = LOWER(:sku) AND tenant_id=:tenantId ORDER BY id LIMIT 1",
                        TenantScope.withTenant(new MapSqlParameterSource("sku", sku)), (rs, row) -> rs.getLong("id"))
                .stream().findFirst();
    }

    /**
     * Ventas ganadas, de la más reciente a la más antigua.
     *
     * <p>Se cuenta como ganada la cotización cuyo resultado es SELECCIONADA.
     * `invoiced` marca las que además ya tienen factura, que son las que de
     * verdad se cobraron.
     */
    public List<WonSale> wonSales(long productId, int limit) {
        return jdbc.query("""
                SELECT q.public_id, q.quotation_number, c.business_name, q.issued_at, q.currency_code,
                       i.unit_price, i.quantity, i.competitor_price, q.procurement_type,
                       EXISTS (SELECT 1 FROM invoices inv WHERE inv.quotation_id = q.id
                               AND inv.tenant_id=q.tenant_id AND inv.status <> 'CANCELLED') AS invoiced
                FROM quotation_items i
                JOIN quotations q ON q.id = i.quotation_id
                JOIN customers c ON c.id = q.customer_id
                WHERE i.product_id = :productId AND q.tenant_id=:tenantId AND q.outcome = 'SELECCIONADA'
                ORDER BY q.issued_at DESC, q.id DESC
                LIMIT :limit
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("productId", productId).addValue("limit", limit)),
                (rs, row) -> new WonSale(rs.getString("public_id"), rs.getString("quotation_number"),
                        rs.getString("business_name"), date(rs, "issued_at"), rs.getString("currency_code"),
                        rs.getBigDecimal("unit_price"), rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("competitor_price"), rs.getString("procurement_type"),
                        rs.getBoolean("invoiced")));
    }

    /**
     * Ventas perdidas donde quedó anotado el precio del que se llevó la orden.
     * Sin ese dato la fila no aporta nada, por eso se filtra.
     */
    public List<LostSale> lostSales(long productId, int limit) {
        return jdbc.query("""
                SELECT q.public_id, q.quotation_number, c.business_name, q.issued_at, q.currency_code,
                       i.unit_price, i.competitor_price, q.outcome, q.outcome_reason, q.procurement_type
                FROM quotation_items i
                JOIN quotations q ON q.id = i.quotation_id
                JOIN customers c ON c.id = q.customer_id
                WHERE i.product_id = :productId AND q.tenant_id=:tenantId
                  AND q.outcome IN ('NO_SELECCIONADA','RECHAZADA')
                  AND i.competitor_price IS NOT NULL
                ORDER BY q.issued_at DESC, q.id DESC
                LIMIT :limit
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("productId", productId).addValue("limit", limit)),
                (rs, row) -> new LostSale(rs.getString("public_id"), rs.getString("quotation_number"),
                        rs.getString("business_name"), date(rs, "issued_at"), rs.getString("currency_code"),
                        rs.getBigDecimal("unit_price"), rs.getBigDecimal("competitor_price"),
                        rs.getString("outcome"), rs.getString("outcome_reason"),
                        rs.getString("procurement_type")));
    }

    /** El costo más reciente registrado para el producto. */
    public Optional<LastPurchase> lastPurchase(long productId) {
        return jdbc.query("""
                SELECT supplier_name, source_reference, currency_code, unit_price, includes_tax,
                       exchange_rate_to_pen, effective_at
                FROM product_price_references
                WHERE product_id = :productId AND tenant_id=:tenantId
                ORDER BY effective_at DESC, id DESC
                LIMIT 1
                """, TenantScope.withTenant(new MapSqlParameterSource("productId", productId)),
                (rs, row) -> new LastPurchase(rs.getString("supplier_name"), rs.getString("source_reference"),
                        rs.getString("currency_code"), rs.getBigDecimal("unit_price"),
                        rs.getBoolean("includes_tax"), rs.getBigDecimal("exchange_rate_to_pen"),
                        date(rs, "effective_at")))
                .stream().findFirst();
    }

    /** Conteos y extremos para el resumen, en una sola consulta. */
    public StatsRow stats(long productId) {
        return jdbc.query("""
                SELECT
                  SUM(CASE WHEN q.outcome IS NOT NULL AND q.outcome <> 'PENDIENTE' THEN 1 ELSE 0 END) AS quoted,
                  SUM(CASE WHEN q.outcome = 'SELECCIONADA' THEN 1 ELSE 0 END) AS won,
                  MAX(CASE WHEN q.outcome = 'SELECCIONADA' THEN i.unit_price END) AS best_won,
                  MIN(CASE WHEN q.outcome IN ('NO_SELECCIONADA','RECHAZADA') THEN i.competitor_price END) AS lowest_competitor
                FROM quotation_items i
                JOIN quotations q ON q.id = i.quotation_id
                WHERE i.product_id = :productId AND q.tenant_id=:tenantId
                """, TenantScope.withTenant(new MapSqlParameterSource("productId", productId)),
                (rs, row) -> new StatsRow(rs.getInt("quoted"), rs.getInt("won"),
                        rs.getBigDecimal("best_won"), rs.getBigDecimal("lowest_competitor")))
                .stream().findFirst().orElse(new StatsRow(0, 0, null, null));
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime().toLocalDate();
    }

    public record ProductRow(long id, String sku, String name) {}

    public record StatsRow(int timesQuoted, int timesWon, BigDecimal bestWonPrice, BigDecimal lowestCompetitor) {}
}

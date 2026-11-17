package pe.giova.price;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import pe.giova.admin.NotFoundException;
import pe.giova.price.api.PriceHistoryDtos.LastPurchase;
import pe.giova.price.api.PriceHistoryDtos.LostSale;
import pe.giova.price.api.PriceHistoryDtos.PriceStats;
import pe.giova.price.api.PriceHistoryDtos.ProductPriceHistory;
import pe.giova.price.api.PriceHistoryDtos.WonSale;

/**
 * Arma el historial comercial de un producto para mostrarlo en el momento de
 * cotizar.
 *
 * <p>La sugerencia de precio es deliberadamente conservadora y siempre dice en
 * qué se basó: la idea es dar contexto al vendedor, no decidir por él.
 */
@org.springframework.stereotype.Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class PriceHistoryService {
    /** Cuántas ventas mostrar de cada lado. Más que esto estorba en pantalla. */
    private static final int MAX_ROWS = 5;
    /** Margen con el que se baja del precio ganador de la competencia. */
    private static final BigDecimal UNDERCUT = new BigDecimal("0.98");

    private final PriceHistoryRepository repository;

    public PriceHistoryService(PriceHistoryRepository repository) {
        this.repository = repository;
    }

    public ProductPriceHistory byProductId(long productId) {
        var product = repository.product(productId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        return build(product.id(), product.sku(), product.name());
    }

    /** Búsqueda por SKU, para cuando la línea se escribió a mano. */
    public ProductPriceHistory bySku(String sku) {
        if (sku == null || sku.isBlank()) throw new IllegalArgumentException("sku es obligatorio");
        long id = repository.productIdBySku(sku.trim())
                .orElseThrow(() -> new NotFoundException("No hay un producto con ese código"));
        return byProductId(id);
    }

    private ProductPriceHistory build(long id, String sku, String name) {
        List<WonSale> won = repository.wonSales(id, MAX_ROWS);
        List<LostSale> lost = repository.lostSales(id, MAX_ROWS);
        LastPurchase purchase = repository.lastPurchase(id).orElse(null);
        var stats = repository.stats(id);

        BigDecimal winRate = stats.timesQuoted() == 0
                ? null
                : BigDecimal.valueOf(stats.timesWon())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(stats.timesQuoted()), 1, RoundingMode.HALF_UP);

        Suggestion suggestion = suggest(stats.bestWonPrice(), stats.lowestCompetitor(), purchase);

        return new ProductPriceHistory(id, sku, name, purchase, won, lost,
                new PriceStats(stats.timesQuoted(), stats.timesWon(), winRate, stats.bestWonPrice(),
                        stats.lowestCompetitor(), suggestion.price(), suggestion.basis()));
    }

    /**
     * Sugerencia de precio, en este orden de preferencia:
     *
     * <ol>
     *   <li>Si nos ganaron con un precio, apuntar apenas por debajo de ese piso.
     *   <li>Si no, repetir el mejor precio con el que sí ganamos.
     *   <li>Si tampoco hay, partir del costo con un margen razonable.
     * </ol>
     *
     * <p>Nunca sugiere por debajo del costo conocido: eso sería recomendar
     * perder plata.
     */
    private static Suggestion suggest(BigDecimal bestWon, BigDecimal lowestCompetitor, LastPurchase purchase) {
        BigDecimal cost = purchase == null ? null : netCost(purchase);

        if (lowestCompetitor != null && lowestCompetitor.signum() > 0) {
            BigDecimal target = lowestCompetitor.multiply(UNDERCUT).setScale(2, RoundingMode.HALF_UP);
            if (cost != null && target.compareTo(cost) <= 0) {
                return new Suggestion(null,
                        "La competencia ganó a " + lowestCompetitor.setScale(2, RoundingMode.HALF_UP)
                                + ", por debajo de nuestro costo. No conviene igualar ese precio.");
            }
            return new Suggestion(target, "Apenas por debajo del precio con el que nos ganaron antes.");
        }
        if (bestWon != null && bestWon.signum() > 0) {
            return new Suggestion(bestWon.setScale(2, RoundingMode.HALF_UP),
                    "Es el mejor precio con el que ya ganamos este producto.");
        }
        if (cost != null && cost.signum() > 0) {
            BigDecimal target = cost.multiply(new BigDecimal("1.25")).setScale(2, RoundingMode.HALF_UP);
            return new Suggestion(target, "Sin historial de ventas: costo más 25 %.");
        }
        return new Suggestion(null, "Todavía no hay datos de este producto.");
    }

    /** Costo sin IGV, para comparar contra precios que tampoco lo incluyen. */
    private static BigDecimal netCost(LastPurchase purchase) {
        BigDecimal price = purchase.unitPrice();
        if (price == null) return null;
        return purchase.includesTax()
                ? price.divide(new BigDecimal("1.18"), 4, RoundingMode.HALF_UP)
                : price;
    }

    private record Suggestion(BigDecimal price, String basis) {}
}

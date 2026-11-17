package pe.giova.price.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Historial comercial de un producto: lo que el vendedor necesita saber en el
 * momento exacto en que está poniendo un precio.
 *
 * <p>Responde tres preguntas que hoy se contestan de memoria o revisando
 * archivos viejos:
 *
 * <ol>
 *   <li>¿A cuánto lo vendimos antes, y a quién?
 *   <li>Cuando lo perdimos, ¿con qué precio nos ganaron?
 *   <li>¿Cuánto nos costó la última vez?
 * </ol>
 */
public final class PriceHistoryDtos {
    private PriceHistoryDtos() {}

    /** Una venta ganada: el precio que sí funcionó. */
    public record WonSale(
            String quotationPublicId,
            String quotationNumber,
            String customerName,
            LocalDate date,
            String currencyCode,
            BigDecimal unitPrice,
            BigDecimal quantity,
            /** Precio del competidor en esa misma cotización, si se registró. */
            BigDecimal competitorPrice,
            String procurementType,
            /** true si además ya se facturó. */
            boolean invoiced) {}

    /** Una venta perdida: con qué precio nos ganaron. */
    public record LostSale(
            String quotationPublicId,
            String quotationNumber,
            String customerName,
            LocalDate date,
            String currencyCode,
            /** Lo que ofrecimos nosotros. */
            BigDecimal ourPrice,
            /** Lo que ofreció quien se llevó la orden. */
            BigDecimal competitorPrice,
            String outcome,
            String reason,
            String procurementType) {}

    /** Última compra registrada, de las facturas del contador o cargada a mano. */
    public record LastPurchase(
            String supplierName,
            String sourceReference,
            String currencyCode,
            BigDecimal unitPrice,
            boolean includesTax,
            BigDecimal exchangeRateToPen,
            LocalDate date) {}

    /**
     * Resumen para decidir rápido.
     *
     * @param timesQuoted      veces que se cotizó este producto (procesos cerrados)
     * @param timesWon         veces que se ganó
     * @param winRate          porcentaje de acierto, 0 a 100
     * @param bestWonPrice     el precio más alto con el que se ganó: hasta dónde se puede estirar
     * @param lowestCompetitor el precio más bajo con el que nos ganaron: el piso a vencer
     * @param suggestedPrice   sugerencia prudente calculada con lo anterior
     */
    public record PriceStats(
            int timesQuoted,
            int timesWon,
            BigDecimal winRate,
            BigDecimal bestWonPrice,
            BigDecimal lowestCompetitor,
            BigDecimal suggestedPrice,
            String suggestionBasis) {}

    public record ProductPriceHistory(
            long productId,
            String sku,
            String name,
            LastPurchase lastPurchase,
            List<WonSale> wonSales,
            List<LostSale> lostSales,
            PriceStats stats) {}
}

package pe.giova.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.giova.price.api.PriceHistoryDtos.LastPurchase;
import pe.giova.price.api.PriceHistoryDtos.ProductPriceHistory;

class PriceHistoryServiceTest {
    private final PriceHistoryRepository repository = mock(PriceHistoryRepository.class);
    private final PriceHistoryService service = new PriceHistoryService(repository);

    @Test
    void suggestsJustBelowThePriceThatBeatUs() {
        // Nos ganaron a 100 y el costo es 60: hay espacio para bajar.
        prepare(new BigDecimal("120"), new BigDecimal("100"), purchase(new BigDecimal("60"), false), 10, 4);

        ProductPriceHistory history = service.byProductId(1L);

        assertEquals(new BigDecimal("98.00"), history.stats().suggestedPrice());
        assertTrue(history.stats().suggestionBasis().toLowerCase().contains("ganaron"));
    }

    @Test
    void refusesToSuggestBelowCost() {
        // Nos ganaron a 50 pero el producto nos cuesta 55: igualar sería perder plata.
        prepare(new BigDecimal("80"), new BigDecimal("50"), purchase(new BigDecimal("55"), false), 6, 1);

        ProductPriceHistory history = service.byProductId(1L);

        assertNull(history.stats().suggestedPrice());
        assertTrue(history.stats().suggestionBasis().toLowerCase().contains("costo"));
    }

    @Test
    void fallsBackToTheBestPriceWeAlreadyWonWith() {
        prepare(new BigDecimal("245.50"), null, purchase(new BigDecimal("180"), false), 5, 5);

        ProductPriceHistory history = service.byProductId(1L);

        assertEquals(new BigDecimal("245.50"), history.stats().suggestedPrice());
        assertTrue(history.stats().suggestionBasis().toLowerCase().contains("ganamos"));
    }

    @Test
    void withoutHistoryUsesCostPlusMargin() {
        prepare(null, null, purchase(new BigDecimal("200"), false), 0, 0);

        ProductPriceHistory history = service.byProductId(1L);

        assertEquals(new BigDecimal("250.00"), history.stats().suggestedPrice());
    }

    @Test
    void discountsTaxFromTheCostBeforeComparing() {
        // El costo viene con IGV: 118 son 100 netos. La sugerencia se compara
        // contra esos 100, no contra los 118, porque los precios de venta que
        // maneja el vendedor tampoco incluyen IGV.
        prepare(null, new BigDecimal("105"), purchase(new BigDecimal("118"), true), 3, 0);

        ProductPriceHistory history = service.byProductId(1L);

        // 105 × 0,98 = 102,90, que sigue por encima de los 100 de costo neto.
        assertEquals(new BigDecimal("102.90"), history.stats().suggestedPrice());
    }

    @Test
    void comparesAgainstTheNetCostNotTheGrossOne() {
        // Mismo costo de 118 con IGV, pero ahora nos ganaron a 100: bajar a 98
        // quedaría por debajo de los 100 netos, así que no se sugiere nada.
        prepare(null, new BigDecimal("100"), purchase(new BigDecimal("118"), true), 3, 0);

        ProductPriceHistory history = service.byProductId(1L);

        assertNull(history.stats().suggestedPrice());
    }

    @Test
    void withNoDataAtAllSaysSo() {
        prepare(null, null, null, 0, 0);

        ProductPriceHistory history = service.byProductId(1L);

        assertNull(history.stats().suggestedPrice());
        assertNull(history.stats().winRate());
    }

    @Test
    void calculatesTheWinRate() {
        prepare(new BigDecimal("100"), null, null, 8, 6);

        ProductPriceHistory history = service.byProductId(1L);

        assertEquals(new BigDecimal("75.0"), history.stats().winRate());
    }

    private void prepare(BigDecimal bestWon, BigDecimal lowestCompetitor, LastPurchase purchase,
                         int quoted, int won) {
        when(repository.product(1L))
                .thenReturn(Optional.of(new PriceHistoryRepository.ProductRow(1L, "SKU-1", "Producto de prueba")));
        when(repository.wonSales(anyLong(), anyInt())).thenReturn(List.of());
        when(repository.lostSales(anyLong(), anyInt())).thenReturn(List.of());
        when(repository.lastPurchase(anyLong())).thenReturn(Optional.ofNullable(purchase));
        when(repository.stats(anyLong()))
                .thenReturn(new PriceHistoryRepository.StatsRow(quoted, won, bestWon, lowestCompetitor));
    }

    private static LastPurchase purchase(BigDecimal price, boolean includesTax) {
        return new LastPurchase("Proveedor", "FACTURA E001-1", "PEN", price, includesTax, null,
                LocalDate.now().minusDays(10));
    }
}

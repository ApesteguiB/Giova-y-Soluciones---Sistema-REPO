package pe.giova.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.giova.invoice.api.InvoiceDtos.ItemInput;

class InvoiceCalculationTest {
    @Test
    void calculatesTaxExclusiveAndRoundsEachLine() {
        var result = InvoiceCalculation.calculate(List.of(
                new ItemInput(null, "A", "A", "unidad", new BigDecimal("3"), new BigDecimal("10.005"))),
                false, new BigDecimal("0.18"));

        assertEquals(new BigDecimal("30.02"), result.subtotal());
        assertEquals(new BigDecimal("5.40"), result.tax());
        assertEquals(new BigDecimal("35.42"), result.total());
    }

    @Test
    void calculatesTaxInclusiveFromGross() {
        var result = InvoiceCalculation.calculate(List.of(
                new ItemInput(null, "A", "A", "unidad", BigDecimal.ONE, new BigDecimal("118"))),
                true, new BigDecimal("0.18"));

        assertEquals(new BigDecimal("100.00"), result.subtotal());
        assertEquals(new BigDecimal("18.00"), result.tax());
        assertEquals(new BigDecimal("118.00"), result.total());
    }

    @Test
    void rejectsEmptyLinesAndInvalidRate() {
        assertThrows(IllegalArgumentException.class,
                () -> InvoiceCalculation.calculate(List.of(), false, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> InvoiceCalculation.calculate(List.of(
                new ItemInput(null, "A", "A", null, BigDecimal.ONE, BigDecimal.ONE)), false,
                new BigDecimal("1.1")));
    }
}

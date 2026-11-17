package pe.giova.quotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.giova.quotation.api.QuotationDtos.ItemInput;

class QuotationCalculationTest {
    @Test
    void calculatesTaxExclusiveAndRoundsEachLine() {
        var result = QuotationCalculation.calculate(List.of(
                new ItemInput(null, "A", "A", "unidad", new BigDecimal("3"), new BigDecimal("10.005"), null, null)), false, new BigDecimal("0.18"));
        assertEquals(new BigDecimal("30.02"), result.subtotal());
        assertEquals(new BigDecimal("5.40"), result.tax());
        assertEquals(new BigDecimal("35.42"), result.total());
    }

    @Test
    void calculatesTaxInclusiveFromGross() {
        var result = QuotationCalculation.calculate(List.of(
                new ItemInput(null, "A", "A", "unidad", new BigDecimal("1"), new BigDecimal("118"), null, null)), true, new BigDecimal("0.18"));
        assertEquals(new BigDecimal("100.00"), result.subtotal());
        assertEquals(new BigDecimal("18.00"), result.tax());
        assertEquals(new BigDecimal("118.00"), result.total());
    }

    @Test
    void rejectsEmptyLinesAndInvalidRate() {
        assertThrows(IllegalArgumentException.class, () -> QuotationCalculation.calculate(List.of(), false, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> QuotationCalculation.calculate(List.of(new ItemInput(null, "A", "A", null, BigDecimal.ONE, BigDecimal.ONE, null, null)), false, new BigDecimal("1.1")));
    }
}

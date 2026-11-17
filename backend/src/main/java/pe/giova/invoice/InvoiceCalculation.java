package pe.giova.invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import pe.giova.invoice.api.InvoiceDtos.ItemInput;

public final class InvoiceCalculation {
    public static final int MONEY_SCALE = 2;
    private static final int INTERNAL_SCALE = 12;

    private InvoiceCalculation() {}

    public static Result calculate(List<ItemInput> inputs, boolean includesTax, BigDecimal taxRate) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("La factura requiere al menos una línea");
        }
        if (inputs.size() > 100) {
            throw new IllegalArgumentException("La factura admite como máximo 100 líneas");
        }
        BigDecimal rate = requiredRate(taxRate);
        var lines = new java.util.ArrayList<Line>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        int number = 1;
        for (ItemInput input : inputs) {
            if (input == null || input.quantity() == null || input.quantity().signum() <= 0
                    || input.unitPrice() == null || input.unitPrice().signum() < 0) {
                throw new IllegalArgumentException("Cantidad y precio deben ser válidos");
            }
            BigDecimal gross = input.quantity().multiply(input.unitPrice());
            BigDecimal lineSubtotal = includesTax
                    ? gross.divide(BigDecimal.ONE.add(rate), INTERNAL_SCALE, RoundingMode.HALF_UP)
                    : gross;
            BigDecimal lineTax = includesTax ? gross.subtract(lineSubtotal) : lineSubtotal.multiply(rate);
            BigDecimal roundedSubtotal = money(lineSubtotal);
            BigDecimal roundedTax = money(lineTax);
            BigDecimal roundedTotal = includesTax ? money(gross) : money(lineSubtotal.add(lineTax));
            lines.add(new Line(number++, roundedSubtotal, roundedTax, roundedTotal));
            subtotal = subtotal.add(roundedSubtotal);
            tax = tax.add(roundedTax);
            total = total.add(roundedTotal);
        }
        return new Result(lines, money(subtotal), money(tax), money(total));
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal requiredRate(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("taxRate debe estar entre 0 y 1");
        }
        return value;
    }

    public record Line(int lineNumber, BigDecimal subtotal, BigDecimal tax, BigDecimal total) {}
    public record Result(List<Line> lines, BigDecimal subtotal, BigDecimal tax, BigDecimal total) {}
}

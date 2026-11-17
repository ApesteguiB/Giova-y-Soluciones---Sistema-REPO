package pe.giova.quotation;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Días hábiles para los plazos de Perú Compras.
 *
 * <p>En los Acuerdos Marco el plazo para cotizar se cuenta en días hábiles —
 * normalmente de 1 a 3— desde la publicación. Contarlo en días corridos hace
 * perder procesos cuando cae fin de semana, así que aquí se cuenta de verdad.
 *
 * <p>Solo se excluyen sábados y domingos. Los feriados nacionales no se
 * descuentan porque cambian cada año y la entidad publica su propia fecha
 * límite; el vendedor siempre puede corregir la fecha a mano.
 */
public final class BusinessDays {
    /** Mínimo y máximo que admite un plazo de Acuerdo Marco. */
    public static final int MIN_FRAMEWORK_DAYS = 1;
    public static final int MAX_FRAMEWORK_DAYS = 3;

    private BusinessDays() {}

    /** Suma días hábiles a una fecha, saltando sábados y domingos. */
    public static LocalDate add(LocalDate start, int businessDays) {
        if (start == null) throw new IllegalArgumentException("La fecha de inicio es obligatoria");
        if (businessDays < 0) throw new IllegalArgumentException("Los días hábiles no pueden ser negativos");
        LocalDate date = start;
        int added = 0;
        while (added < businessDays) {
            date = date.plusDays(1);
            if (isBusinessDay(date)) added++;
        }
        return date;
    }

    /** Días hábiles entre dos fechas, sin contar la de inicio. */
    public static int between(LocalDate start, LocalDate end) {
        if (start == null || end == null) throw new IllegalArgumentException("Ambas fechas son obligatorias");
        if (!end.isAfter(start)) return 0;
        int count = 0;
        LocalDate date = start;
        while (date.isBefore(end)) {
            date = date.plusDays(1);
            if (isBusinessDay(date)) count++;
        }
        return count;
    }

    public static boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }
}

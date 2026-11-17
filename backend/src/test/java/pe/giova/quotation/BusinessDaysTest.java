package pe.giova.quotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BusinessDaysTest {
    // 2026-09-17 es jueves; 18 viernes; 19 sábado; 20 domingo; 21 lunes.
    private static final LocalDate JUEVES = LocalDate.of(2026, 9, 17);
    private static final LocalDate VIERNES = LocalDate.of(2026, 9, 18);
    private static final LocalDate SABADO = LocalDate.of(2026, 9, 19);

    @Test
    void addsDaysWithinTheSameWeek() {
        assertEquals(VIERNES, BusinessDays.add(JUEVES, 1));
    }

    @Test
    void skipsTheWeekend() {
        // Jueves + 2 días hábiles cae lunes, no sábado: contar corrido hace
        // perder procesos de Perú Compras.
        assertEquals(LocalDate.of(2026, 9, 21), BusinessDays.add(JUEVES, 2));
        assertEquals(LocalDate.of(2026, 9, 22), BusinessDays.add(JUEVES, 3));
    }

    @Test
    void countingStartingOnFridayLandsOnMonday() {
        assertEquals(LocalDate.of(2026, 9, 21), BusinessDays.add(VIERNES, 1));
    }

    @Test
    void addingZeroKeepsTheSameDate() {
        assertEquals(JUEVES, BusinessDays.add(JUEVES, 0));
    }

    @Test
    void betweenIgnoresWeekendDays() {
        assertEquals(2, BusinessDays.between(JUEVES, LocalDate.of(2026, 9, 21)));
        assertEquals(0, BusinessDays.between(JUEVES, JUEVES));
        assertEquals(0, BusinessDays.between(LocalDate.of(2026, 9, 21), JUEVES));
    }

    @Test
    void weekendIsNotABusinessDay() {
        assertTrue(BusinessDays.isBusinessDay(VIERNES));
        assertFalse(BusinessDays.isBusinessDay(SABADO));
        assertFalse(BusinessDays.isBusinessDay(LocalDate.of(2026, 9, 20)));
    }
}

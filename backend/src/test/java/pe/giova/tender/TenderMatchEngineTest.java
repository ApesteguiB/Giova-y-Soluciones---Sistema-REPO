package pe.giova.tender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TenderMatchEngineTest {
    private final TenderMatchEngine engine = new TenderMatchEngine();
    private final TenderMatchEngine.Candidate candidate = new TenderMatchEngine.Candidate(
            "Mantenimiento de infraestructura", "Servicio preventivo", "BUY-1", "Municipalidad de Lima",
            "Lima", List.of("72151500"), new BigDecimal("125000"),
            Instant.parse("2026-10-20T23:59:59Z"));

    @Test
    void scoresEveryConfiguredCriterionAndExplainsTheMatch() {
        TenderMatchEngine.Profile profile = new TenderMatchEngine.Profile(
                List.of("mantenimiento"), List.of(), List.of("municipalidad"), List.of("lima"),
                List.of("72151500"), new BigDecimal("100000"), new BigDecimal("200000"),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        TenderMatchEngine.Evaluation result = engine.evaluate(candidate, profile);

        assertTrue(result.matched());
        assertEquals(100, result.score());
        assertEquals(6, result.reasons().size());
    }

    @Test
    void excludedKeywordWinsOverPositiveCriteria() {
        TenderMatchEngine.Profile profile = new TenderMatchEngine.Profile(
                List.of("mantenimiento"), List.of("infraestructura"), List.of(), List.of(), List.of(),
                null, null, null, null);

        TenderMatchEngine.Evaluation result = engine.evaluate(candidate, profile);

        assertFalse(result.matched());
        assertEquals(0, result.score());
        assertTrue(result.reasons().get(0).contains("excluida"));
    }

    @Test
    void missingAmountDoesNotMatchAnAmountBoundedProfile() {
        TenderMatchEngine.Candidate withoutAmount = new TenderMatchEngine.Candidate(
                candidate.title(), candidate.description(), candidate.buyerId(), candidate.buyerName(),
                candidate.region(), candidate.classificationCodes(), null, candidate.tenderEnd());
        TenderMatchEngine.Profile profile = new TenderMatchEngine.Profile(
                List.of(), List.of(), List.of(), List.of(), List.of(), new BigDecimal("1"), null, null, null);

        assertFalse(engine.evaluate(withoutAmount, profile).matched());
    }
}

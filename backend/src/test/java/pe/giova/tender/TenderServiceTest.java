package pe.giova.tender;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import pe.giova.admin.NotFoundException;
import pe.giova.audit.AuditService;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;
import pe.giova.tender.api.TenderDtos;
import pe.giova.tender.config.OeceProperties;
import pe.giova.tender.contract.TenderJsonCodec;
import pe.giova.tender.enums.TenderMatchState;
import tools.jackson.databind.json.JsonMapper;

class TenderServiceTest {
    private final TenderRepository repository = mock(TenderRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final WorkerPrincipal seller = principal(6L, RoleCode.SELLER);
    private final TenderService service = new TenderService(repository, mock(TenderProviderClient.class),
            new OeceProperties(false, URI.create("https://example.test/api"), "0 0 * * * *", 1, 1, 10, 60),
            new TenderMatchEngine(), new TenderJsonCodec(JsonMapper.builder().build()), mock(DataSource.class),
            mock(ExecutorService.class), audit);

    @Test
    void providerNotFoundAfterProcessedPagesEndsPagination() {
        assertTrue(TenderService.isEndOfPagination(
                new TenderProviderException("PROVIDER_NOT_FOUND", "not found", 404, false), 1, false));
    }

    @Test
    void providerNotFoundOnFirstPageEndsPaginationWhenResumingCursor() {
        assertTrue(TenderService.isEndOfPagination(
                new TenderProviderException("PROVIDER_NOT_FOUND", "not found", 404, false), 0, true));
    }

    @Test
    void providerNotFoundOnFirstPageWithoutCursorIsAnError() {
        assertFalse(TenderService.isEndOfPagination(
                new TenderProviderException("PROVIDER_NOT_FOUND", "not found", 404, false), 0, false));
    }

    @Test
    void otherProviderErrorsDoNotEndPagination() {
        assertFalse(TenderService.isEndOfPagination(
                new TenderProviderException("TRANSIENT_PROVIDER_ERROR", "temporary", 503, true), 1, false));
        assertFalse(TenderService.isEndOfPagination(
                new TenderProviderException("PROVIDER_ACCESS_DENIED", "forbidden", 403, false), 1, false));
    }

    @Test
    void sellerCanReviewAnUnassignedMatchAndBecomesItsOwner() {
        TenderRepository.MatchRecord current = match(null, TenderMatchState.NEW, 0L);
        TenderRepository.MatchRecord updated = match(6L, TenderMatchState.REVIEWING, 1L);
        when(repository.findMatch("process", "profile")).thenReturn(Optional.of(current), Optional.of(updated));
        when(repository.updateMatchState(12L, 0L, TenderMatchState.REVIEWING, 6L)).thenReturn(1);

        service.changeMatchState("process", new TenderDtos.MatchStateChange("profile",
                TenderMatchState.REVIEWING, null, 0L), seller);

        verify(repository).appendMatchAction(12L, 6L, "REVIEW", null);
    }

    @Test
    void sellerCannotActOnAMatchOwnedByAnotherSeller() {
        when(repository.findMatch("process", "profile")).thenReturn(Optional.of(match(4L, TenderMatchState.NEW, 0L)));

        assertThrows(NotFoundException.class, () -> service.changeMatchState("process",
                new TenderDtos.MatchStateChange("profile", TenderMatchState.REVIEWING, null, 0L), seller));
    }

    private static TenderRepository.MatchRecord match(Long owner, TenderMatchState state, long version) {
        return new TenderRepository.MatchRecord(12L, "match", 9L, "process", "ocid", "Título", "Descripción",
                "buyer", "Comprador", "Lima", List.of("72151500"), "open", "active", "PEN",
                new BigDecimal("10"), Instant.now(), Instant.now(), "https://example.test/tender", Instant.now(),
                "hash", 8L, "profile", "Perfil", owner, 80, List.of("coincide"), state, Instant.now(), version);
    }

    private static WorkerPrincipal principal(long id, RoleCode role) {
        return new WorkerPrincipal(new WorkerRepository.WorkerRecord(id, role.name().toLowerCase() + "@test.local",
                role.label(), "hash", true, role));
    }
}

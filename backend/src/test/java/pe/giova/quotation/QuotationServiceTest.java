package pe.giova.quotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.giova.audit.AuditService;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;
import pe.giova.document.DocumentRepository;
import pe.giova.document.DocumentStorageService;
import pe.giova.quotation.api.QuotationDtos;

class QuotationServiceTest {
    private final QuotationRepository repository = mock(QuotationRepository.class);
    private final QuotationService service = new QuotationService(repository, mock(DocumentRepository.class),
            mock(DocumentStorageService.class), mock(QuotationPdfService.class), mock(AuditService.class));
    private final WorkerPrincipal admin = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
            4L, "admin@example.test", "Administrador", "hash", true, RoleCode.ADMIN));
    private final WorkerPrincipal seller = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
            7L, "vendedor@example.test", "Vendedor", "hash", true, RoleCode.SELLER));

    @Test
    void adminCanListAllQuotationsWithoutOwnerFilter() {
        when(repository.find(any(), any(), any(), any(), any(), any(), any(), any(), eq(0), eq(10)))
                .thenReturn(new QuotationRepository.PageData(List.of(), 0, 0, 10, 0));

        QuotationDtos.Page page = service.find(null, null, null, null, null, "all", null, null, 0, 10, admin);

        verify(repository).find(null, null, null, null, null, null, null, null, 0, 10);
        assertEquals(0, page.totalItems());
    }

    @Test
    void adminCannotCreateQuotations() {
        // Regla de negocio: solo el vendedor cotiza. El administrador supervisa.
        assertThrows(SecurityException.class, () -> service.create(input(), admin));
        verify(repository, never()).insert(anyLong(), any(), any(), any());
    }

    @Test
    void closingAsNotSelectedRequiresAReason() {
        when(repository.header("q-1")).thenReturn(Optional.of(header(QuotationStatus.ISSUED, 3L)));

        var withoutReason = new QuotationDtos.OutcomeChange("NO_SELECCIONADA", "  ", "ENTIDAD", 3L);
        var error = assertThrows(IllegalArgumentException.class,
                () -> service.changeOutcome("q-1", withoutReason, seller));

        assertTrue(error.getMessage().toLowerCase().contains("motivo"));
        verify(repository, never()).setOutcome(anyString(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void cancellingDefaultsToOurOwnDecision() {
        when(repository.header("q-2")).thenReturn(Optional.of(header(QuotationStatus.ISSUED, 1L)));
        when(repository.setOutcome(eq("q-2"), eq(1L), eq(QuotationOutcome.ANULADA), anyString(),
                eq(OutcomeSource.NOSOTROS), eq(QuotationStatus.CANCELLED))).thenReturn(1);
        when(repository.findByPublicId(eq("q-2"), any()))
                .thenReturn(Optional.of(new QuotationRepository.QuotationViewData(
                        header(QuotationStatus.CANCELLED, 2L), List.of())));

        service.changeOutcome("q-2", new QuotationDtos.OutcomeChange("ANULADA",
                "El proveedor subió el precio y el margen quedaba negativo.", null, 1L), seller);

        verify(repository).setOutcome("q-2", 1L, QuotationOutcome.ANULADA,
                "El proveedor subió el precio y el margen quedaba negativo.",
                OutcomeSource.NOSOTROS, QuotationStatus.CANCELLED);
    }

    @Test
    void selectedOutcomeNeedsNoReason() {
        when(repository.header("q-3")).thenReturn(Optional.of(header(QuotationStatus.ISSUED, 5L)));
        when(repository.setOutcome(eq("q-3"), eq(5L), eq(QuotationOutcome.SELECCIONADA), any(),
                eq(OutcomeSource.ENTIDAD), eq(QuotationStatus.ACCEPTED))).thenReturn(1);
        when(repository.findByPublicId(eq("q-3"), any()))
                .thenReturn(Optional.of(new QuotationRepository.QuotationViewData(
                        header(QuotationStatus.ACCEPTED, 6L), List.of())));

        service.changeOutcome("q-3", new QuotationDtos.OutcomeChange("SELECCIONADA", null, null, 5L), seller);

        verify(repository).setOutcome("q-3", 5L, QuotationOutcome.SELECCIONADA, null,
                OutcomeSource.ENTIDAD, QuotationStatus.ACCEPTED);
    }

    private static QuotationDtos.Input input() {
        return new QuotationDtos.Input("cliente-1", "PERU_COMPRAS", "PEN", BigDecimal.ONE, false,
                new BigDecimal("0.18"), null, 10, 12, null,
                List.of(new QuotationDtos.ItemInput(null, "SKU", "Servicio", "unidad",
                        BigDecimal.ONE, BigDecimal.TEN, null, null)));
    }

    private static QuotationRepository.QuotationHeader header(QuotationStatus status, long version) {
        return new QuotationRepository.QuotationHeader(1L, "q-1", "COT-2026-000001", 2L, "cliente-1",
                "Entidad", "RUC", "20123456789", 7L, "Vendedor", status, ProcurementType.PERU_COMPRAS,
                QuotationOutcome.PENDIENTE, null, null, null, 10, 12, null, null,
                Instant.now(), LocalDate.now().plusDays(2), "PEN", BigDecimal.ONE, false,
                new BigDecimal("0.18"), new BigDecimal("100"), new BigDecimal("18"), new BigDecimal("118"),
                null, version, Instant.now(), Instant.now(), null);
    }
}

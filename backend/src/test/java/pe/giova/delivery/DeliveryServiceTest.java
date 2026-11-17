package pe.giova.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.giova.audit.AuditService;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;
import pe.giova.document.DocumentRepository;
import pe.giova.document.DocumentStorageService;
import pe.giova.delivery.api.DeliveryDtos;

class DeliveryServiceTest {
    private final DeliveryRepository repository = mock(DeliveryRepository.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentStorageService documentStorageService = mock(DocumentStorageService.class);
    private final AuditService audit = mock(AuditService.class);
    private final DeliveryService service = new DeliveryService(repository, documentRepository, documentStorageService, audit);
    private final WorkerPrincipal accountant = principal(5L, RoleCode.ACCOUNTANT);

    @Test
    void signedActRequiresDeliveryTimestamp() {
        when(repository.lockByPublicId("delivery-id", null)).thenReturn(Optional.of(header(0)));

        assertThrows(IllegalArgumentException.class, () -> service.changeStatus("delivery-id",
                new DeliveryDtos.StatusChange(DeliveryStatus.SIGNED, 0L, null, null), accountant));
    }

    @Test
    void signedActRejectsDateBeforeOrderIssue() {
        when(repository.lockByPublicId("delivery-id", null)).thenReturn(Optional.of(header(0)));

        var exception = assertThrows(IllegalArgumentException.class, () -> service.changeStatus("delivery-id",
                new DeliveryDtos.StatusChange(DeliveryStatus.SIGNED, 0L, Instant.parse("2026-09-14T23:59:59Z"), null), accountant));

        assertEquals("deliveredAt no puede ser anterior a issueDate de la orden", exception.getMessage());
    }

    private static DeliveryRepository.DeliveryHeader header(long version) {
        return new DeliveryRepository.DeliveryHeader(1L, "delivery-id", "ACT-1", 2L, "order-id", "OC-1", 8L,
                "customer-id", "Cliente", LocalDate.of(2026, 9, 15), null, null, null, null, DeliveryStatus.DRAFT,
                null, version, Instant.now(), Instant.now());
    }

    private static WorkerPrincipal principal(long id, RoleCode role) {
        return new WorkerPrincipal(new WorkerRepository.WorkerRecord(id, role.name().toLowerCase() + "@example.test", role.label(), "hash", true, role));
    }
}

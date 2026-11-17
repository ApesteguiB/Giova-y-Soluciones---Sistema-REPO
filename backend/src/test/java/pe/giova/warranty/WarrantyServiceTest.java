package pe.giova.warranty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import pe.giova.audit.AuditService;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;
import pe.giova.document.DocumentRepository;
import pe.giova.document.DocumentStorageService;
import pe.giova.quotation.QuotationRepository;
import pe.giova.warranty.api.WarrantyDtos;

class WarrantyServiceTest {
    private final WarrantyRepository repository = mock(WarrantyRepository.class);
    private final QuotationRepository quotationRepository = mock(QuotationRepository.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentStorageService documentStorageService = mock(DocumentStorageService.class);
    private final AuditService audit = mock(AuditService.class);
    private final WarrantyService service = new WarrantyService(repository, quotationRepository, documentRepository, documentStorageService, audit);
    private final WorkerPrincipal accountant = principal(5L, RoleCode.ACCOUNTANT);

    @Test
    void endDateCannotPrecedeStartDate() {
        var input = new WarrantyDtos.Input("customer-id", null, null, null, null,
                LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 19), null, null);

        var exception = assertThrows(IllegalArgumentException.class, () -> service.create(input, accountant));

        assertEquals("endsOn no puede ser anterior a startsOn", exception.getMessage());
    }

    @Test
    void sellerCannotCreateWarranty() {
        var input = new WarrantyDtos.Input("customer-id", null, null, null, null,
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 15), null, null);

        assertThrows(SecurityException.class, () -> service.create(input, principal(6L, RoleCode.SELLER)));
    }

    @Test
    void calculationInputContractKeepsDateAndOptionalRelations() {
        var input = new WarrantyDtos.Input("customer-id", "order-id", 1, 22L, "SN-1",
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 15), "Condiciones", "Notas");

        assertEquals(1, input.purchaseOrderLineNumber());
        assertEquals(new BigDecimal("22"), BigDecimal.valueOf(input.productId()));
    }

    private static WorkerPrincipal principal(long id, RoleCode role) {
        return new WorkerPrincipal(new WorkerRepository.WorkerRecord(id, role.name().toLowerCase() + "@example.test", role.label(), "hash", true, role));
    }
}

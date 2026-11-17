package pe.giova.purchaseorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.giova.admin.ConflictException;
import pe.giova.audit.AuditService;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;
import pe.giova.document.DocumentRepository;
import pe.giova.document.DocumentStorageService;
import pe.giova.invoice.InvoiceRepository;
import pe.giova.purchaseorder.api.PurchaseOrderDtos;
import pe.giova.quotation.QuotationRepository;

class PurchaseOrderServiceTest {
    private final PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
    private final QuotationRepository quotationRepository = mock(QuotationRepository.class);
    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentStorageService documentStorageService = mock(DocumentStorageService.class);
    private final AuditService audit = mock(AuditService.class);
    private final PurchaseOrderService service = new PurchaseOrderService(repository, quotationRepository, invoiceRepository,
            documentRepository, documentStorageService, audit);
    private final WorkerPrincipal accountant = principal(5L, RoleCode.ACCOUNTANT);
    private final WorkerPrincipal seller = principal(6L, RoleCode.SELLER);

    @Test
    void sellerCannotCreateOrder() {
        assertThrows(SecurityException.class, () -> service.create(input(), seller));
    }

    @Test
    void deliveredCannotSkipInProgress() {
        when(repository.lockByPublicId("order-id", null)).thenReturn(Optional.of(header(PurchaseOrderStatus.REGISTERED, 0)));

        var exception = assertThrows(ConflictException.class, () -> service.changeStatus("order-id",
                new PurchaseOrderDtos.StatusChange(PurchaseOrderStatus.DELIVERED, 0L, LocalDate.of(2026, 9, 15), null), accountant));

        assertEquals("INVALID_TRANSITION", exception.code());
    }

    @Test
    void deliveryRequiresDate() {
        when(repository.lockByPublicId("order-id", null)).thenReturn(Optional.of(header(PurchaseOrderStatus.IN_PROGRESS, 0)));

        assertThrows(IllegalArgumentException.class, () -> service.changeStatus("order-id",
                new PurchaseOrderDtos.StatusChange(PurchaseOrderStatus.DELIVERED, 0L, null, null), accountant));
    }

    @Test
    void sellerListUsesQuotationOwnership() {
        when(repository.find(any(), any(), any(), any(), eq(6L), eq(0), eq(20)))
                .thenReturn(new PurchaseOrderRepository.PageData(List.of(), 0, 0, 20, 0));

        service.find(null, null, null, null, 0, 20, seller);

        org.mockito.Mockito.verify(repository).find(null, null, null, null, 6L, 0, 20);
    }

    private static PurchaseOrderDtos.Input input() {
        return new PurchaseOrderDtos.Input("OC-1", "customer-id", null, null, LocalDate.of(2026, 9, 15), null, null,
                "PEN", BigDecimal.ONE, false, new BigDecimal("0.18"), null,
                List.of(new PurchaseOrderDtos.ItemInput(null, "SKU", "Servicio", "unidad", BigDecimal.ONE, BigDecimal.TEN)));
    }

    private static PurchaseOrderRepository.PurchaseOrderHeader header(PurchaseOrderStatus status, long version) {
        return new PurchaseOrderRepository.PurchaseOrderHeader(1L, "order-id", "OC-1", 8L, "customer-id", "Cliente", "DNI", "123",
                null, null, null, null, null, 5L, "Contador", status, LocalDate.of(2026, 9, 15), null, null, "PEN", BigDecimal.ONE,
                false, new BigDecimal("0.18"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, version, Instant.now(), Instant.now());
    }

    private static WorkerPrincipal principal(long id, RoleCode role) {
        return new WorkerPrincipal(new WorkerRepository.WorkerRecord(id, role.name().toLowerCase() + "@example.test", role.label(), "hash", true, role));
    }
}

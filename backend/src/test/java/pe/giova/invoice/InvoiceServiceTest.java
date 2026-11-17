package pe.giova.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import pe.giova.audit.AuditService;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;
import pe.giova.document.DocumentKind;
import pe.giova.document.DocumentRepository;
import pe.giova.document.DocumentStorageService;
import pe.giova.document.DocumentRepository.StoredDocument;
import pe.giova.document.api.DocumentDtos.DocumentMetadata;
import pe.giova.invoice.InvoiceRepository.InvoiceHeader;
import pe.giova.invoice.InvoiceRepository.InvoiceViewData;
import pe.giova.invoice.api.InvoiceDtos;
import pe.giova.customer.CustomerRepository;
import pe.giova.quotation.QuotationRepository;

class InvoiceServiceTest {
    private final InvoiceRepository repository = mock(InvoiceRepository.class);
    private final QuotationRepository quotationRepository = mock(QuotationRepository.class);
    // Se usa al importar un XML cuyo RUC todavía no está en la cartera de clientes.
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentStorageService documentStorageService = mock(DocumentStorageService.class);
    private final AuditService audit = mock(AuditService.class);
    private final InvoiceService service = new InvoiceService(repository, quotationRepository, customerRepository,
            documentRepository, documentStorageService, audit);
    private final WorkerPrincipal admin = principal(4L, RoleCode.ADMIN);
    private final WorkerPrincipal accountant = principal(5L, RoleCode.ACCOUNTANT);
    private final WorkerPrincipal seller = principal(6L, RoleCode.SELLER);

    @Test
    void sellerCannotCreateInvoice() {
        assertThrows(SecurityException.class, () -> service.create(input(null), seller));
    }

    @Test
    void adminListDoesNotApplySellerOwnershipFilter() {
        when(repository.find(any(), any(), any(), any(), any(), eq(null), eq(0), eq(10)))
                .thenReturn(new InvoiceRepository.PageData(List.of(), 0, 0, 10, 0));

        var page = service.find(null, null, null, null, null, 0, 10, admin);

        assertEquals(0, page.totalItems());
        verify(repository).find(null, null, null, null, null, null, 0, 10);
    }

    @Test
    void sellerListIsRestrictedToQuotationOwner() {
        when(repository.find(any(), any(), any(), any(), any(), eq(6L), eq(0), eq(10)))
                .thenReturn(new InvoiceRepository.PageData(List.of(), 0, 0, 10, 0));

        service.find(null, null, null, null, null, 0, 10, seller);

        verify(repository).find(null, null, null, null, null, 6L, 0, 10);
    }

    @Test
    void accountantCreateRecalculatesTotalsOnServer() {
        when(quotationRepository.customer("customer-id")).thenReturn(
                new QuotationRepository.CustomerData(8L, "customer-id", 12L, "Cliente", "DNI", "12345678", true));
        when(repository.insert(eq(5L), eq(8L), eq(null), any(), any(), any())).thenReturn("invoice-id");
        when(repository.findByPublicId("invoice-id", null)).thenReturn(Optional.of(viewData(
                header(0L, "invoice-id", InvoiceStatus.DRAFT, 0L), List.of(), List.of())));

        var result = service.create(input(null), accountant);

        assertEquals("invoice-id", result.publicId());
        verify(repository).insert(eq(5L), eq(8L), eq(null), any(), any(),
                org.mockito.ArgumentMatchers.argThat(calculation ->
                        calculation.total().compareTo(new BigDecimal("35.42")) == 0));
    }

    @Test
    void terminalInvoiceCannotBeEdited() {
        when(repository.lockByPublicId("invoice-id", null)).thenReturn(
                Optional.of(header(11L, "invoice-id", InvoiceStatus.ISSUED, 1L)));

        var update = new InvoiceDtos.Update("FAC-1", InvoiceType.INVOICE, "customer-id", null,
                LocalDate.of(2026, 9, 14), null, "PEN", BigDecimal.ONE, false, new BigDecimal("0.18"),
                null, 1L, List.of(item()));

        var exception = assertThrows(pe.giova.admin.ConflictException.class,
                () -> service.update("invoice-id", update, accountant));
        assertEquals("TERMINAL_DOCUMENT", exception.code());
    }

    @Test
    void xmlAttachmentUsesSecureInvoiceKindAndAdvancesVersion() {
        when(repository.lockByPublicId("invoice-id", null)).thenReturn(
                Optional.of(header(11L, "invoice-id", InvoiceStatus.DRAFT, 0L)));
        var file = new MockMultipartFile("file", "evidence.xml", "application/xml",
                "<?xml version=\"1.0\"?><invoice/>".getBytes());
        var metadata = new DocumentMetadata("doc-id", "INVOICE_XML", "evidence.xml", "application/xml", "xml",
                file.getSize(), "a".repeat(64), accountant.id(), true, 0L, Instant.now(), Instant.now());
        var stored = new StoredDocument(7L, "doc-id", DocumentKind.INVOICE_XML, "invoice-xml/2026/09/doc-id.xml",
                "evidence.xml", "application/xml", "xml", file.getSize(), "a".repeat(64), accountant.id(), true,
                0L, Instant.now(), Instant.now());
        when(documentStorageService.store(file, DocumentKind.INVOICE_XML, accountant)).thenReturn(metadata);
        when(documentRepository.findByPublicId("doc-id")).thenReturn(Optional.of(stored));
        when(repository.touchAfterDocument("invoice-id", 0L)).thenReturn(1);
        when(repository.findByPublicId("invoice-id", null)).thenReturn(Optional.of(viewData(
                header(11L, "invoice-id", InvoiceStatus.DRAFT, 1L), List.of(), List.of())));

        var result = service.attachDocument("invoice-id", file, "xml", 0L, accountant);

        assertEquals(1L, result.version());
        verify(repository).deactivateActiveDocuments(11L, InvoiceDocumentRole.XML);
        verify(repository).linkDocument(11L, 7L, InvoiceDocumentRole.XML);
    }

    private static InvoiceDtos.Input input(String quotationId) {
        return new InvoiceDtos.Input("FAC-1", InvoiceType.INVOICE, "customer-id", quotationId,
                LocalDate.of(2026, 9, 14), null, "PEN", BigDecimal.ONE, false, new BigDecimal("0.18"),
                null, List.of(item()));
    }

    private static InvoiceDtos.ItemInput item() {
        return new InvoiceDtos.ItemInput(null, "A", "Servicio", "unidad", new BigDecimal("3"),
                new BigDecimal("10.005"));
    }

    private static InvoiceViewData viewData(InvoiceHeader header, List<InvoiceRepository.ItemData> items,
                                            List<InvoiceRepository.DocumentData> documents) {
        return new InvoiceViewData(header, items, documents);
    }

    private static InvoiceHeader header(long id, String publicId, InvoiceStatus status, long version) {
        return new InvoiceHeader(id, publicId, "FAC-1", InvoiceType.INVOICE, 8L, "customer-id", "Cliente", "DNI",
                "12345678", null, null, null, 5L, "Contador", status, LocalDate.of(2026, 9, 14), null, "PEN",
                BigDecimal.ONE, false, new BigDecimal("0.18"), new BigDecimal("30.02"), new BigDecimal("5.40"),
                new BigDecimal("35.42"), null, version, Instant.now(), Instant.now());
    }

    private static WorkerPrincipal principal(long id, RoleCode role) {
        return new WorkerPrincipal(new WorkerRepository.WorkerRecord(id, role.name().toLowerCase() + "@example.test",
                role.label(), "hash", true, role));
    }
}

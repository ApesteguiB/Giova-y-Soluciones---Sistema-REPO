package pe.giova.purchaseorder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.giova.admin.ConflictException;
import pe.giova.admin.NotFoundException;
import pe.giova.audit.AuditService;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.document.DocumentKind;
import pe.giova.document.DocumentRepository;
import pe.giova.document.DocumentStorageService;
import pe.giova.document.DocumentStorageService.DocumentDownload;
import pe.giova.document.api.DocumentDtos.DocumentMetadata;
import pe.giova.invoice.InvoiceRepository;
import pe.giova.purchaseorder.PurchaseOrderRepository.DocumentData;
import pe.giova.purchaseorder.PurchaseOrderRepository.PurchaseOrderHeader;
import pe.giova.purchaseorder.PurchaseOrderRepository.PurchaseOrderViewData;
import pe.giova.purchaseorder.api.PurchaseOrderDtos;
import pe.giova.quotation.QuotationRepository;
import pe.giova.quotation.QuotationRepository.CustomerData;

@org.springframework.stereotype.Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class PurchaseOrderService {
    private final PurchaseOrderRepository repository;
    private final QuotationRepository quotationRepository;
    private final InvoiceRepository invoiceRepository;
    private final DocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;
    private final AuditService audit;

    public PurchaseOrderService(PurchaseOrderRepository repository, QuotationRepository quotationRepository,
                                InvoiceRepository invoiceRepository, DocumentRepository documentRepository,
                                DocumentStorageService documentStorageService, AuditService audit) {
        this.repository = repository;
        this.quotationRepository = quotationRepository;
        this.invoiceRepository = invoiceRepository;
        this.documentRepository = documentRepository;
        this.documentStorageService = documentStorageService;
        this.audit = audit;
    }

    public PurchaseOrderDtos.Page find(String query, String status, String customerPublicId,
                                       String quotationPublicId, int page, int size, WorkerPrincipal actor) {
        validatePage(page, size);
        Long ownerId = sellerOwnerId(actor);
        var result = repository.find(normalizeQuery(query), normalizeStatus(status), optionalId(customerPublicId, "customerId"),
                optionalId(quotationPublicId, "quotationId"), ownerId, page, size);
        return new PurchaseOrderDtos.Page(result.items().stream().map(this::view).toList(), result.totalItems(),
                result.page(), result.size(), result.totalPages());
    }

    public PurchaseOrderDtos.View get(String publicId, WorkerPrincipal actor) {
        return view(repository.findByPublicId(publicId, sellerOwnerId(actor))
                .orElseThrow(() -> new NotFoundException("Orden no encontrada")));
    }

    @Transactional
    public PurchaseOrderDtos.View create(PurchaseOrderDtos.Input input, WorkerPrincipal actor) {
        requireWrite(actor);
        Normalized normalized = normalize(input);
        CustomerData customer = customerFor(normalized.customerPublicId());
        ResolvedLines lines = resolveLines(normalized);
        var calculation = PurchaseOrderCalculation.calculate(lines.calculationItems(), normalized.data().includesTax(), normalized.data().taxRate());
        String id = repository.insert(actor.id(), customer.id(), lines.quotationId(), lines.invoiceId(), normalized.data(),
                lines.storageLines(), calculation);
        audit.success(actor, "PURCHASE_ORDER_CREATED", "PURCHASE_ORDER", id, "Orden de compra creada");
        return get(id, actor);
    }

    @Transactional
    public PurchaseOrderDtos.View update(String publicId, PurchaseOrderDtos.Update input, WorkerPrincipal actor) {
        requireWrite(actor);
        PurchaseOrderHeader current = headerFor(publicId, null);
        if (current.status() != PurchaseOrderStatus.REGISTERED) {
            throw new ConflictException("Solo se puede editar una orden registrada", "TERMINAL_DOCUMENT");
        }
        if (input.version() == null || input.version() < 0 || current.version() != input.version()) {
            throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        Normalized normalized = normalize(input);
        CustomerData customer = customerFor(normalized.customerPublicId());
        ResolvedLines lines = resolveLines(normalized);
        var calculation = PurchaseOrderCalculation.calculate(lines.calculationItems(), normalized.data().includesTax(), normalized.data().taxRate());
        if (repository.updateRegistered(publicId, input.version(), customer.id(), lines.quotationId(), lines.invoiceId(),
                normalized.data(), lines.storageLines(), calculation) == 0) {
            throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        audit.success(actor, "PURCHASE_ORDER_UPDATED", "PURCHASE_ORDER", publicId, "Orden de compra actualizada");
        return get(publicId, actor);
    }

    @Transactional
    public PurchaseOrderDtos.View changeStatus(String publicId, PurchaseOrderDtos.StatusChange input, WorkerPrincipal actor) {
        requireWrite(actor);
        PurchaseOrderHeader current = headerFor(publicId, null);
        if (current.status() == input.targetStatus()) return get(publicId, actor);
        if (!allowed(current.status(), input.targetStatus())) {
            throw new ConflictException("Transición de estado no permitida", "INVALID_TRANSITION");
        }
        if (current.version() != input.version()) {
            throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        LocalDate deliveredDate = input.deliveredDate() == null ? current.deliveredDate() : input.deliveredDate();
        if (input.targetStatus() == PurchaseOrderStatus.DELIVERED && deliveredDate == null) {
            throw new IllegalArgumentException("deliveredDate es obligatorio al entregar la orden");
        }
        if (deliveredDate != null && deliveredDate.isBefore(current.issueDate())) {
            throw new IllegalArgumentException("deliveredDate no puede ser anterior a issueDate");
        }
        if (repository.changeStatus(publicId, input.version(), input.targetStatus(), deliveredDate) == 0) {
            throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        String reason = input.reason() == null || input.reason().isBlank() ? "" : ": " + input.reason().trim();
        audit.success(actor, "PURCHASE_ORDER_STATUS_CHANGED", "PURCHASE_ORDER", publicId,
                "Estado " + input.targetStatus() + reason);
        return get(publicId, actor);
    }

    @Transactional
    public PurchaseOrderDtos.View attachDocument(String publicId, MultipartFile file, String roleInput,
                                                 long version, WorkerPrincipal actor) {
        requireWrite(actor);
        PurchaseOrderHeader current = repository.lockByPublicId(publicId, null)
                .orElseThrow(() -> new NotFoundException("Orden no encontrada"));
        if (current.status().terminal()) throw new ConflictException("Una orden terminal no admite cambios documentales", "TERMINAL_DOCUMENT");
        if (current.version() != version) throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        PurchaseOrderDocumentRole role = PurchaseOrderDocumentRole.fromInput(roleInput);
        DocumentKind kind = role == PurchaseOrderDocumentRole.ORDER_PDF ? DocumentKind.PURCHASE_ORDER_PDF : DocumentKind.OTHER_PDF;
        DocumentMetadata metadata = documentStorageService.store(file, kind, actor);
        var stored = documentRepository.findByPublicId(metadata.publicId())
                .orElseThrow(() -> new IllegalStateException("Documento no registrado"));
        int replaced = repository.deactivateActiveDocuments(current.id(), role);
        repository.linkDocument(current.id(), stored.id(), role);
        if (repository.touchAfterDocument(publicId, version) == 0) {
            throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        audit.success(actor, replaced > 0 ? "PURCHASE_ORDER_DOCUMENT_REPLACED" : "PURCHASE_ORDER_DOCUMENT_ATTACHED",
                "PURCHASE_ORDER", publicId, "Documento " + role + " asociado");
        return get(publicId, actor);
    }

    public List<PurchaseOrderDtos.DocumentView> documents(String publicId, WorkerPrincipal actor) {
        return get(publicId, actor).documents();
    }

    public DocumentDownload downloadDocument(String publicId, String documentPublicId, WorkerPrincipal actor) {
        get(publicId, actor);
        repository.activeDocument(publicId, documentPublicId).orElseThrow(() -> new NotFoundException("Documento no encontrado"));
        DocumentDownload download = documentStorageService.openForDownload(documentPublicId, actor, true);
        audit.success(actor, "PURCHASE_ORDER_DOCUMENT_DOWNLOADED", "PURCHASE_ORDER", publicId, "Descarga de documento asociado");
        return download;
    }

    private CustomerData customerFor(String publicId) {
        CustomerData customer = quotationRepository.customer(publicId);
        if (!customer.active()) throw new ConflictException("El cliente está inactivo", "CUSTOMER_INACTIVE");
        return customer;
    }

    private ResolvedLines resolveLines(Normalized normalized) {
        var quotationId = normalized.quotationPublicId();
        var invoiceId = normalized.invoicePublicId();
        if (quotationId != null) {
            var quotation = quotationRepository.findByPublicId(quotationId, null)
                    .orElseThrow(() -> new NotFoundException("Cotización no encontrada"));
            if (!quotation.header().customerPublicId().equals(normalized.customerPublicId())) {
                throw new ConflictException("La cotización no pertenece al cliente indicado", "CUSTOMER_QUOTATION_MISMATCH");
            }
            if (invoiceId != null) {
                var invoice = invoiceRepository.findByPublicId(invoiceId, null)
                        .orElseThrow(() -> new NotFoundException("Factura no encontrada"));
                if (!normalized.customerPublicId().equals(invoice.header().customerPublicId())
                        || (invoice.header().quotationPublicId() != null && !quotationId.equals(invoice.header().quotationPublicId()))) {
                    throw new ConflictException("La factura no corresponde a la cotización y cliente indicados", "RELATION_MISMATCH");
                }
            }
            List<PurchaseOrderDtos.ItemInput> items = quotation.items().stream()
                    .map(i -> new PurchaseOrderDtos.ItemInput(i.productId(), i.sku(), i.description(), i.unit(), i.quantity(), i.unitPrice())).toList();
            List<PurchaseOrderRepository.LineInputData> storage = quotation.items().stream()
                    .map(i -> new PurchaseOrderRepository.LineInputData(i.productId(), i.sku(), i.description(), i.unit(), i.quantity(), i.unitPrice())).toList();
            return new ResolvedLines(quotation.header().id(), invoiceId == null ? null : invoiceRepository.findByPublicId(invoiceId, null).orElseThrow().header().id(), items, storage);
        }
        if (invoiceId != null) {
            var invoice = invoiceRepository.findByPublicId(invoiceId, null)
                    .orElseThrow(() -> new NotFoundException("Factura no encontrada"));
            if (!normalized.customerPublicId().equals(invoice.header().customerPublicId())) {
                throw new ConflictException("La factura no pertenece al cliente indicado", "CUSTOMER_INVOICE_MISMATCH");
            }
            List<PurchaseOrderDtos.ItemInput> items = invoice.items().stream()
                    .map(i -> new PurchaseOrderDtos.ItemInput(i.productId(), i.sku(), i.description(), i.unit(), i.quantity(), i.unitPrice())).toList();
            List<PurchaseOrderRepository.LineInputData> storage = invoice.items().stream()
                    .map(i -> new PurchaseOrderRepository.LineInputData(i.productId(), i.sku(), i.description(), i.unit(), i.quantity(), i.unitPrice())).toList();
            return new ResolvedLines(null, invoice.header().id(), items, storage);
        }
        if (normalized.items() == null || normalized.items().isEmpty()) throw new IllegalArgumentException("La orden requiere líneas o una cotización/factura");
        List<PurchaseOrderRepository.LineInputData> storage = normalized.items().stream().map(this::line).toList();
        return new ResolvedLines(null, null, normalized.items(), storage);
    }

    private PurchaseOrderRepository.LineInputData line(PurchaseOrderDtos.ItemInput item) {
        if (item == null) throw new IllegalArgumentException("La línea de orden es obligatoria");
        if (item.productId() == null) return new PurchaseOrderRepository.LineInputData(null, clean(item.sku(), "sku"),
                clean(item.description(), "description"), cleanUnit(item.unit()), item.quantity(), item.unitPrice());
        var product = quotationRepository.product(item.productId());
        if (!product.active()) throw new ConflictException("El producto no está activo", "PRODUCT_INACTIVE");
        return new PurchaseOrderRepository.LineInputData(product.id(), product.sku(), product.name(), cleanUnit(item.unit()), item.quantity(), item.unitPrice());
    }

    private static Normalized normalize(PurchaseOrderDtos.Input input) {
        if (input == null) throw new IllegalArgumentException("La orden es obligatoria");
        return normalizeData(input.orderNumber(), input.customerPublicId(), input.quotationPublicId(), input.invoicePublicId(), input.issueDate(),
                input.expectedDeliveryDate(), input.deliveredDate(), input.currencyCode(), input.exchangeRateToPen(), input.includesTax(),
                input.taxRate(), input.notes(), input.items());
    }
    private static Normalized normalize(PurchaseOrderDtos.Update input) {
        if (input == null) throw new IllegalArgumentException("La orden es obligatoria");
        if (input.version() == null || input.version() < 0) throw new IllegalArgumentException("version inválida");
        return normalizeData(input.orderNumber(), input.customerPublicId(), input.quotationPublicId(), input.invoicePublicId(), input.issueDate(),
                input.expectedDeliveryDate(), input.deliveredDate(), input.currencyCode(), input.exchangeRateToPen(), input.includesTax(),
                input.taxRate(), input.notes(), input.items());
    }
    private static Normalized normalizeData(String number, String customer, String quotation, String invoice, LocalDate issue,
                                            LocalDate expected, LocalDate delivered, String currency, BigDecimal exchange,
                                            Boolean includesTax, BigDecimal taxRate, String notes, List<PurchaseOrderDtos.ItemInput> items) {
        String cleanNumber = clean(number, "orderNumber");
        String cleanCustomer = clean(customer, "customerPublicId");
        String cleanQuotation = optionalId(quotation, "quotationPublicId");
        String cleanInvoice = optionalId(invoice, "invoicePublicId");
        if (issue == null) throw new IllegalArgumentException("issueDate es obligatorio");
        if (expected != null && expected.isBefore(issue)) throw new IllegalArgumentException("expectedDeliveryDate no puede ser anterior a issueDate");
        if (delivered != null && delivered.isBefore(issue)) throw new IllegalArgumentException("deliveredDate no puede ser anterior a issueDate");
        String code = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (!code.equals("PEN") && !code.equals("USD")) throw new IllegalArgumentException("currencyCode debe ser PEN o USD");
        if (exchange == null || exchange.signum() <= 0 || (code.equals("PEN") && exchange.compareTo(BigDecimal.ONE) != 0)) throw new IllegalArgumentException("exchangeRateToPen no válido");
        if (includesTax == null) throw new IllegalArgumentException("includesTax es obligatorio");
        if (taxRate == null || taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0) throw new IllegalArgumentException("taxRate debe estar entre 0 y 1");
        return new Normalized(new PurchaseOrderRepository.PurchaseOrderInputData(cleanNumber, issue, expected, delivered, code, exchange,
                includesTax, taxRate, notes == null || notes.isBlank() ? null : notes.trim()), cleanCustomer, cleanQuotation, cleanInvoice, items);
    }
    private PurchaseOrderHeader headerFor(String publicId, Long sellerOwnerId) {
        return repository.lockByPublicId(publicId, sellerOwnerId).orElseThrow(() -> new NotFoundException("Orden no encontrada"));
    }
    private PurchaseOrderDtos.View view(PurchaseOrderViewData data) {
        var h = data.header();
        return new PurchaseOrderDtos.View(h.publicId(), h.orderNumber(), h.customerPublicId(), h.customerName(), h.customerDocumentType(),
                h.customerDocumentNumber(), h.quotationPublicId(), h.quotationNumber(), h.invoicePublicId(), h.invoiceNumber(),
                h.responsibleWorkerId(), h.responsibleWorkerDisplayName(), h.status(), h.issueDate(), h.expectedDeliveryDate(), h.deliveredDate(),
                h.currencyCode(), h.exchangeRateToPen(), h.includesTax(), h.taxRate(), h.subtotalAmount(), h.taxAmount(), h.totalAmount(), h.notes(),
                h.version(), h.createdAt(), h.updatedAt(), data.items().stream().map(i -> new PurchaseOrderDtos.ItemView(i.lineNumber(), i.productId(),
                i.sku(), i.description(), i.unit(), i.quantity(), i.unitPrice(), i.subtotal(), i.tax(), i.total())).toList(),
                data.documents().stream().map(d -> documentView(h.publicId(), d)).toList());
    }
    private PurchaseOrderDtos.DocumentView documentView(String orderId, DocumentData d) {
        return new PurchaseOrderDtos.DocumentView(d.publicId(), d.role(), d.originalName(), d.mediaType(), d.extension(), d.sizeBytes(), d.sha256Hex(),
                d.version(), d.createdAt(), "/api/v1/accounting/purchase-orders/" + orderId + "/documents/" + d.publicId() + "/content");
    }
    private static boolean allowed(PurchaseOrderStatus current, PurchaseOrderStatus target) {
        return (current == PurchaseOrderStatus.REGISTERED && (target == PurchaseOrderStatus.IN_PROGRESS || target == PurchaseOrderStatus.CANCELLED))
                || (current == PurchaseOrderStatus.IN_PROGRESS && (target == PurchaseOrderStatus.DELIVERED || target == PurchaseOrderStatus.CANCELLED));
    }
    private static Long sellerOwnerId(WorkerPrincipal actor) { return actor.roleCode() == RoleCode.SELLER ? actor.id() : null; }
    private static void requireWrite(WorkerPrincipal actor) { if (actor == null || (actor.roleCode() != RoleCode.ADMIN && actor.roleCode() != RoleCode.ACCOUNTANT)) throw new SecurityException("Se requiere rol ADMIN o ACCOUNTANT"); }
    private static String optionalId(String value, String field) { if (value == null || value.isBlank()) return null; String clean=value.trim(); if (clean.length()>36) throw new IllegalArgumentException(field+" inválido"); return clean; }
    private static String normalizeStatus(String value) { if (value == null || value.isBlank()) return null; try { return PurchaseOrderStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)).name(); } catch (IllegalArgumentException ex) { throw new IllegalArgumentException("status no permitido"); } }
    private static String normalizeQuery(String value) { if (value != null && value.length() > 80) throw new IllegalArgumentException("q supera el máximo de 80 caracteres"); return value == null || value.isBlank() ? null : value.trim(); }
    private static void validatePage(int page, int size) { if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page/size fuera de rango"); }
    private static String clean(String value, String field) { if (value == null || value.isBlank()) throw new IllegalArgumentException(field+" es obligatorio"); return value.trim(); }
    private static String cleanUnit(String value) { return value == null || value.isBlank() ? "unidad" : value.trim(); }
    private record Normalized(PurchaseOrderRepository.PurchaseOrderInputData data, String customerPublicId, String quotationPublicId,
                              String invoicePublicId, List<PurchaseOrderDtos.ItemInput> items) {}
    private record ResolvedLines(Long quotationId, Long invoiceId, List<PurchaseOrderDtos.ItemInput> calculationItems,
                                 List<PurchaseOrderRepository.LineInputData> storageLines) {}
}

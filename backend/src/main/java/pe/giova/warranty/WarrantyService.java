package pe.giova.warranty;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import pe.giova.quotation.QuotationRepository;
import pe.giova.warranty.WarrantyRepository.DocumentData;
import pe.giova.warranty.WarrantyRepository.PurchaseOrderData;
import pe.giova.warranty.WarrantyRepository.WarrantyHeader;
import pe.giova.warranty.WarrantyRepository.WarrantyViewData;
import pe.giova.warranty.api.WarrantyDtos;

@org.springframework.stereotype.Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class WarrantyService {
    private final WarrantyRepository repository;
    private final QuotationRepository quotationRepository;
    private final DocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;
    private final AuditService audit;

    public WarrantyService(WarrantyRepository repository, QuotationRepository quotationRepository,
                           DocumentRepository documentRepository, DocumentStorageService documentStorageService,
                           AuditService audit) {
        this.repository = repository;
        this.quotationRepository = quotationRepository;
        this.documentRepository = documentRepository;
        this.documentStorageService = documentStorageService;
        this.audit = audit;
    }

    public WarrantyDtos.Page find(String query, String status, String customerPublicId, String purchaseOrderPublicId,
                                  int page, int size, WorkerPrincipal actor) {
        validatePage(page, size);
        repository.expireDue(LocalDate.now(ZoneOffset.UTC));
        var result = repository.find(normalizeQuery(query), normalizeStatus(status), optionalId(customerPublicId, "customerId"),
                optionalId(purchaseOrderPublicId, "purchaseOrderId"), sellerOwnerId(actor), page, size);
        return new WarrantyDtos.Page(result.items().stream().map(this::view).toList(), result.totalItems(), result.page(), result.size(), result.totalPages());
    }

    public WarrantyDtos.View get(String publicId, WorkerPrincipal actor) {
        repository.expireDue(LocalDate.now(ZoneOffset.UTC));
        return view(repository.findByPublicId(publicId, sellerOwnerId(actor)).orElseThrow(() -> new NotFoundException("Garantía no encontrada")));
    }

    @Transactional
    public WarrantyDtos.View create(WarrantyDtos.Input input, WorkerPrincipal actor) {
        requireWrite(actor);
        Normalized normalized = normalize(input);
        Resolved resolved = resolve(normalized);
        String id = repository.insert(resolved.customerId(), resolved.purchaseOrderId(), resolved.purchaseOrderItemId(), resolved.productId(), normalized.data());
        audit.success(actor, "WARRANTY_CREATED", "WARRANTY", id, "Garantía creada");
        return get(id, actor);
    }

    @Transactional
    public WarrantyDtos.View update(String publicId, WarrantyDtos.Update input, WorkerPrincipal actor) {
        requireWrite(actor);
        repository.expireDue(LocalDate.now(ZoneOffset.UTC));
        WarrantyHeader current = headerFor(publicId, null);
        if (current.status() != WarrantyStatus.ACTIVE) throw new ConflictException("Solo se puede editar una garantía activa", "TERMINAL_DOCUMENT");
        if (input.version() == null || input.version() < 0 || current.version() != input.version()) throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        Normalized normalized = normalize(input);
        Resolved resolved = resolve(normalized);
        if (repository.updateActive(publicId, input.version(), resolved.customerId(), resolved.purchaseOrderId(), resolved.purchaseOrderItemId(), resolved.productId(), normalized.data()) == 0) {
            throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        audit.success(actor, "WARRANTY_UPDATED", "WARRANTY", publicId, "Garantía actualizada");
        return get(publicId, actor);
    }

    @Transactional
    public WarrantyDtos.View changeStatus(String publicId, WarrantyDtos.StatusChange input, WorkerPrincipal actor) {
        requireWrite(actor);
        repository.expireDue(LocalDate.now(ZoneOffset.UTC));
        WarrantyHeader current = headerFor(publicId, null);
        if (current.status() == input.targetStatus()) return get(publicId, actor);
        if (!allowed(current.status(), input.targetStatus())) throw new ConflictException("Transición de estado no permitida", "INVALID_TRANSITION");
        if (input.targetStatus() == WarrantyStatus.EXPIRED && !current.endsOn().isBefore(LocalDate.now(ZoneOffset.UTC))) {
            throw new ConflictException("La garantía todavía no ha vencido", "WARRANTY_NOT_EXPIRED");
        }
        if (current.version() != input.version()) throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        if (repository.changeStatus(publicId, input.version(), input.targetStatus()) == 0) throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        String reason = input.reason() == null || input.reason().isBlank() ? "" : ": " + input.reason().trim();
        audit.success(actor, "WARRANTY_STATUS_CHANGED", "WARRANTY", publicId, "Estado " + input.targetStatus() + reason);
        return get(publicId, actor);
    }

    @Transactional
    public WarrantyDtos.View attachDocument(String publicId, MultipartFile file, String roleInput, long version, WorkerPrincipal actor) {
        requireWrite(actor);
        repository.expireDue(LocalDate.now(ZoneOffset.UTC));
        WarrantyHeader current = repository.lockByPublicId(publicId, null).orElseThrow(() -> new NotFoundException("Garantía no encontrada"));
        if (current.status().terminal()) throw new ConflictException("Una garantía terminal no admite cambios documentales", "TERMINAL_DOCUMENT");
        if (current.version() != version) throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        WarrantyDocumentRole role = WarrantyDocumentRole.fromInput(roleInput);
        DocumentMetadata metadata = documentStorageService.store(file, DocumentKind.WARRANTY_PDF, actor);
        var stored = documentRepository.findByPublicId(metadata.publicId()).orElseThrow(() -> new IllegalStateException("Documento no registrado"));
        int replaced = repository.deactivateActiveDocuments(current.id(), role);
        repository.linkDocument(current.id(), stored.id(), role);
        if (repository.touchAfterDocument(publicId, version) == 0) throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        audit.success(actor, replaced > 0 ? "WARRANTY_DOCUMENT_REPLACED" : "WARRANTY_DOCUMENT_ATTACHED", "WARRANTY", publicId, "Documento " + role + " asociado");
        return get(publicId, actor);
    }

    public List<WarrantyDtos.DocumentView> documents(String publicId, WorkerPrincipal actor) { return get(publicId, actor).documents(); }
    public DocumentDownload downloadDocument(String publicId, String documentPublicId, WorkerPrincipal actor) {
        get(publicId, actor);
        repository.activeDocument(publicId, documentPublicId).orElseThrow(() -> new NotFoundException("Documento no encontrado"));
        DocumentDownload download = documentStorageService.openForDownload(documentPublicId, actor, true);
        audit.success(actor, "WARRANTY_DOCUMENT_DOWNLOADED", "WARRANTY", publicId, "Descarga de documento asociado");
        return download;
    }

    private Resolved resolve(Normalized normalized) {
        Long purchaseOrderId = null;
        Long purchaseOrderItemId = null;
        Long productId = normalized.productId();
        if (normalized.purchaseOrderPublicId() == null && normalized.purchaseOrderLineNumber() != null) throw new IllegalArgumentException("purchaseOrderLineNumber requiere purchaseOrderPublicId");
        if (normalized.purchaseOrderPublicId() != null) {
            PurchaseOrderData order = repository.purchaseOrder(normalized.purchaseOrderPublicId());
            if (!order.customerPublicId().equals(normalized.customerPublicId())) throw new ConflictException("La orden no pertenece al cliente indicado", "CUSTOMER_ORDER_MISMATCH");
            purchaseOrderId = order.id();
            if (normalized.purchaseOrderLineNumber() != null) {
                if (normalized.purchaseOrderLineNumber() < 1) throw new IllegalArgumentException("purchaseOrderLineNumber inválido");
                var item = repository.purchaseOrderItem(order.id(), normalized.purchaseOrderLineNumber()).orElseThrow(() -> new NotFoundException("Línea de orden no encontrada"));
                purchaseOrderItemId = item.id();
                if (productId != null && !productId.equals(item.productId())) throw new ConflictException("El producto no coincide con la línea de orden", "RELATION_MISMATCH");
                if (productId == null) productId = item.productId();
            }
        }
        if (productId != null) {
            var product = quotationRepository.product(productId);
            if (!product.active()) throw new ConflictException("El producto no está activo", "PRODUCT_INACTIVE");
        }
        var customer = quotationRepository.customer(normalized.customerPublicId());
        if (!customer.active()) throw new ConflictException("El cliente está inactivo", "CUSTOMER_INACTIVE");
        return new Resolved(customer.id(), purchaseOrderId, purchaseOrderItemId, productId);
    }

    private static Normalized normalize(WarrantyDtos.Input input) { if(input==null) throw new IllegalArgumentException("La garantía es obligatoria"); return normalizeData(input.customerPublicId(),input.purchaseOrderPublicId(),input.purchaseOrderLineNumber(),input.productId(),input.serialNumber(),input.startsOn(),input.endsOn(),input.conditions(),input.notes()); }
    private static Normalized normalize(WarrantyDtos.Update input) { if(input==null) throw new IllegalArgumentException("La garantía es obligatoria"); if(input.version()==null||input.version()<0) throw new IllegalArgumentException("version inválida"); return normalizeData(input.customerPublicId(),input.purchaseOrderPublicId(),input.purchaseOrderLineNumber(),input.productId(),input.serialNumber(),input.startsOn(),input.endsOn(),input.conditions(),input.notes()); }
    private static Normalized normalizeData(String customer,String order,Integer line,Long product,String serial,LocalDate starts,LocalDate ends,String conditions,String notes) {
        String c=clean(customer,"customerPublicId"); String o=optionalId(order,"purchaseOrderPublicId");
        if(starts==null||ends==null) throw new IllegalArgumentException("startsOn y endsOn son obligatorios");
        if(ends.isBefore(starts)) throw new IllegalArgumentException("endsOn no puede ser anterior a startsOn");
        if(line!=null&&line<1) throw new IllegalArgumentException("purchaseOrderLineNumber inválido");
        return new Normalized(c,o,line,product,blankToNull(serial),starts,ends,blankToNull(conditions),blankToNull(notes));
    }
    private WarrantyHeader headerFor(String publicId,Long owner){return repository.lockByPublicId(publicId,owner).orElseThrow(()->new NotFoundException("Garantía no encontrada"));}
    private WarrantyDtos.View view(WarrantyViewData data){var h=data.header();return new WarrantyDtos.View(h.publicId(),h.customerPublicId(),h.customerName(),h.purchaseOrderPublicId(),h.orderNumber(),h.purchaseOrderLineNumber(),h.productId(),h.productSku(),h.productName(),h.serialNumber(),h.startsOn(),h.endsOn(),h.status(),h.conditions(),h.notes(),h.version(),h.createdAt(),h.updatedAt(),data.documents().stream().map(d->documentView(h.publicId(),d)).toList());}
    private WarrantyDtos.DocumentView documentView(String warrantyId,DocumentData d){return new WarrantyDtos.DocumentView(d.publicId(),d.role(),d.originalName(),d.mediaType(),d.extension(),d.sizeBytes(),d.sha256Hex(),d.version(),d.createdAt(),"/api/v1/accounting/warranties/"+warrantyId+"/documents/"+d.publicId()+"/content");}
    private static boolean allowed(WarrantyStatus current,WarrantyStatus target){return current==WarrantyStatus.ACTIVE&&(target==WarrantyStatus.EXPIRED||target==WarrantyStatus.CLAIMED||target==WarrantyStatus.CANCELLED);}
    private static Long sellerOwnerId(WorkerPrincipal actor){return actor.roleCode()==RoleCode.SELLER?actor.id():null;}
    private static void requireWrite(WorkerPrincipal actor){if(actor==null||(actor.roleCode()!=RoleCode.ADMIN&&actor.roleCode()!=RoleCode.ACCOUNTANT))throw new SecurityException("Se requiere rol ADMIN o ACCOUNTANT");}
    private static String normalizeStatus(String value){if(value==null||value.isBlank())return null;try{return WarrantyStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)).name();}catch(IllegalArgumentException ex){throw new IllegalArgumentException("status no permitido");}}
    private static String normalizeQuery(String value){if(value!=null&&value.length()>80)throw new IllegalArgumentException("q supera el máximo de 80 caracteres");return value==null||value.isBlank()?null:value.trim();}
    private static String optionalId(String value,String field){if(value==null||value.isBlank())return null;String clean=value.trim();if(clean.length()>36)throw new IllegalArgumentException(field+" inválido");return clean;}
    private static void validatePage(int page,int size){if(page<0||size<1||size>100)throw new IllegalArgumentException("page/size fuera de rango");}
    private static String clean(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" es obligatorio");return value.trim();}
    private static String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
    private record Normalized(String customerPublicId,String purchaseOrderPublicId,Integer purchaseOrderLineNumber,Long productId,String serialNumber,LocalDate startsOn,LocalDate endsOn,String conditions,String notes){ WarrantyRepository.WarrantyInputData data(){return new WarrantyRepository.WarrantyInputData(serialNumber,startsOn,endsOn,conditions,notes);} }
    private record Resolved(long customerId,Long purchaseOrderId,Long purchaseOrderItemId,Long productId){}
}

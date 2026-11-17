package pe.giova.delivery;

import java.time.Instant;
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
import pe.giova.delivery.DeliveryRepository.DeliveryHeader;
import pe.giova.delivery.DeliveryRepository.DeliveryOrderData;
import pe.giova.delivery.DeliveryRepository.DeliveryViewData;
import pe.giova.delivery.DeliveryRepository.DocumentData;
import pe.giova.delivery.api.DeliveryDtos;

@org.springframework.stereotype.Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class DeliveryService {
    private final DeliveryRepository repository;
    private final DocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;
    private final AuditService audit;

    public DeliveryService(DeliveryRepository repository, DocumentRepository documentRepository,
                           DocumentStorageService documentStorageService, AuditService audit) {
        this.repository = repository;
        this.documentRepository = documentRepository;
        this.documentStorageService = documentStorageService;
        this.audit = audit;
    }

    public DeliveryDtos.Page find(String query, String status, String customerPublicId, String purchaseOrderPublicId,
                                  int page, int size, WorkerPrincipal actor) {
        validatePage(page, size);
        var result = repository.find(normalizeQuery(query), normalizeStatus(status), optionalId(customerPublicId, "customerId"),
                optionalId(purchaseOrderPublicId, "purchaseOrderId"), sellerOwnerId(actor), page, size);
        return new DeliveryDtos.Page(result.items().stream().map(this::view).toList(), result.totalItems(), result.page(), result.size(), result.totalPages());
    }

    public DeliveryDtos.View get(String publicId, WorkerPrincipal actor) {
        return view(repository.findByPublicId(publicId, sellerOwnerId(actor))
                .orElseThrow(() -> new NotFoundException("Acta no encontrada")));
    }

    @Transactional
    public DeliveryDtos.View create(DeliveryDtos.Input input, WorkerPrincipal actor) {
        requireWrite(actor);
        Normalized normalized = normalize(input);
        DeliveryOrderData order = repository.purchaseOrder(normalized.purchaseOrderPublicId());
        validateOrderDate(order, normalized.data().deliveredAt());
        if (order.status() == pe.giova.purchaseorder.PurchaseOrderStatus.CANCELLED) {
            throw new ConflictException("No se puede registrar un acta para una orden cancelada", "ORDER_CANCELLED");
        }
        String id = repository.insert(normalized.deliveryNumber(), order, normalized.repositoryData());
        audit.success(actor, "DELIVERY_CREATED", "DELIVERY", id, "Acta de entrega creada");
        return get(id, actor);
    }

    @Transactional
    public DeliveryDtos.View update(String publicId, DeliveryDtos.Update input, WorkerPrincipal actor) {
        requireWrite(actor);
        DeliveryHeader current = headerFor(publicId, null);
        if (current.status() != DeliveryStatus.DRAFT) throw new ConflictException("Solo se puede editar un acta en borrador", "TERMINAL_DOCUMENT");
        if (input.version() == null || input.version() < 0 || current.version() != input.version()) {
            throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        Normalized normalized = normalize(input);
        DeliveryOrderData order = repository.purchaseOrder(normalized.purchaseOrderPublicId());
        validateOrderDate(order, normalized.data().deliveredAt());
        if (order.status() == pe.giova.purchaseorder.PurchaseOrderStatus.CANCELLED) throw new ConflictException("La orden está cancelada", "ORDER_CANCELLED");
        if (repository.updateDraft(publicId, input.version(), normalized.deliveryNumber(), order, normalized.repositoryData()) == 0) {
            throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        audit.success(actor, "DELIVERY_UPDATED", "DELIVERY", publicId, "Acta de entrega actualizada");
        return get(publicId, actor);
    }

    @Transactional
    public DeliveryDtos.View changeStatus(String publicId, DeliveryDtos.StatusChange input, WorkerPrincipal actor) {
        requireWrite(actor);
        DeliveryHeader current = headerFor(publicId, null);
        if (current.status() == input.targetStatus()) return get(publicId, actor);
        if (!allowed(current.status(), input.targetStatus())) throw new ConflictException("Transición de estado no permitida", "INVALID_TRANSITION");
        if (current.version() != input.version()) throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        Instant deliveredAt = input.deliveredAt() == null ? current.deliveredAt() : input.deliveredAt();
        if (input.targetStatus() == DeliveryStatus.SIGNED) {
            if (deliveredAt == null) throw new IllegalArgumentException("deliveredAt es obligatorio al firmar el acta");
            validateOrderDate(new DeliveryOrderData(current.purchaseOrderId(), current.purchaseOrderPublicId(), current.orderNumber(),
                    current.customerId(), current.customerPublicId(), current.orderIssueDate(), null, null, null), deliveredAt);
            if (deliveredAt.isAfter(Instant.now())) throw new IllegalArgumentException("deliveredAt no puede estar en el futuro");
        }
        if (repository.changeStatus(publicId, input.version(), input.targetStatus(), deliveredAt) == 0) throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        String reason = input.reason() == null || input.reason().isBlank() ? "" : ": " + input.reason().trim();
        audit.success(actor, "DELIVERY_STATUS_CHANGED", "DELIVERY", publicId, "Estado " + input.targetStatus() + reason);
        return get(publicId, actor);
    }

    @Transactional
    public DeliveryDtos.View attachDocument(String publicId, MultipartFile file, String roleInput, long version, WorkerPrincipal actor) {
        requireWrite(actor);
        DeliveryHeader current = repository.lockByPublicId(publicId, null).orElseThrow(() -> new NotFoundException("Acta no encontrada"));
        if (current.status().terminal()) throw new ConflictException("Un acta terminal no admite cambios documentales", "TERMINAL_DOCUMENT");
        if (current.version() != version) throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        DeliveryDocumentRole role = DeliveryDocumentRole.fromInput(roleInput);
        DocumentMetadata metadata = documentStorageService.store(file, DocumentKind.DELIVERY_PDF, actor);
        var stored = documentRepository.findByPublicId(metadata.publicId()).orElseThrow(() -> new IllegalStateException("Documento no registrado"));
        int replaced = repository.deactivateActiveDocuments(current.id(), role);
        repository.linkDocument(current.id(), stored.id(), role);
        if (repository.touchAfterDocument(publicId, version) == 0) throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        audit.success(actor, replaced > 0 ? "DELIVERY_DOCUMENT_REPLACED" : "DELIVERY_DOCUMENT_ATTACHED", "DELIVERY", publicId, "Documento " + role + " asociado");
        return get(publicId, actor);
    }

    public List<DeliveryDtos.DocumentView> documents(String publicId, WorkerPrincipal actor) { return get(publicId, actor).documents(); }

    public DocumentDownload downloadDocument(String publicId, String documentPublicId, WorkerPrincipal actor) {
        get(publicId, actor);
        repository.activeDocument(publicId, documentPublicId).orElseThrow(() -> new NotFoundException("Documento no encontrado"));
        DocumentDownload download = documentStorageService.openForDownload(documentPublicId, actor, true);
        audit.success(actor, "DELIVERY_DOCUMENT_DOWNLOADED", "DELIVERY", publicId, "Descarga de documento asociado");
        return download;
    }

    private DeliveryHeader headerFor(String publicId, Long sellerOwnerId) { return repository.lockByPublicId(publicId, sellerOwnerId).orElseThrow(() -> new NotFoundException("Acta no encontrada")); }
    private static void validateOrderDate(DeliveryOrderData order, Instant deliveredAt) {
        if (deliveredAt == null) return;
        LocalDate date = deliveredAt.atZone(ZoneOffset.UTC).toLocalDate();
        if (date.isBefore(order.issueDate())) throw new IllegalArgumentException("deliveredAt no puede ser anterior a issueDate de la orden");
    }
    private DeliveryDtos.View view(DeliveryViewData data) {
        var h=data.header();
        return new DeliveryDtos.View(h.publicId(),h.deliveryNumber(),h.purchaseOrderPublicId(),h.orderNumber(),h.customerPublicId(),h.customerName(),h.deliveredAt(),h.receivedByName(),h.receivedByDocument(),h.status(),h.notes(),h.version(),h.createdAt(),h.updatedAt(),data.documents().stream().map(d -> documentView(h.publicId(),d)).toList());
    }
    private DeliveryDtos.DocumentView documentView(String deliveryId, DocumentData d) { return new DeliveryDtos.DocumentView(d.publicId(),d.role(),d.originalName(),d.mediaType(),d.extension(),d.sizeBytes(),d.sha256Hex(),d.version(),d.createdAt(),"/api/v1/accounting/deliveries/"+deliveryId+"/documents/"+d.publicId()+"/content"); }
    private static Normalized normalize(DeliveryDtos.Input input) { if (input==null) throw new IllegalArgumentException("El acta es obligatoria"); return normalizeData(input.deliveryNumber(),input.purchaseOrderPublicId(),input.deliveredAt(),input.receivedByName(),input.receivedByDocument(),input.notes()); }
    private static Normalized normalize(DeliveryDtos.Update input) { if (input==null) throw new IllegalArgumentException("El acta es obligatoria"); if(input.version()==null||input.version()<0) throw new IllegalArgumentException("version inválida"); return normalizeData(input.deliveryNumber(),input.purchaseOrderPublicId(),input.deliveredAt(),input.receivedByName(),input.receivedByDocument(),input.notes()); }
    private static boolean allowed(DeliveryStatus current, DeliveryStatus target) { return current==DeliveryStatus.DRAFT && (target==DeliveryStatus.SIGNED || target==DeliveryStatus.CANCELLED); }
    private static Long sellerOwnerId(WorkerPrincipal actor) { return actor.roleCode()==RoleCode.SELLER ? actor.id() : null; }
    private static void requireWrite(WorkerPrincipal actor) { if(actor==null||(actor.roleCode()!=RoleCode.ADMIN&&actor.roleCode()!=RoleCode.ACCOUNTANT)) throw new SecurityException("Se requiere rol ADMIN o ACCOUNTANT"); }
    private static String normalizeStatus(String value) { if(value==null||value.isBlank()) return null; try{return DeliveryStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)).name();}catch(IllegalArgumentException ex){throw new IllegalArgumentException("status no permitido");} }
    private static String normalizeQuery(String value) { if(value!=null&&value.length()>80) throw new IllegalArgumentException("q supera el máximo de 80 caracteres"); return value==null||value.isBlank()?null:value.trim(); }
    private static String optionalId(String value,String field) { if(value==null||value.isBlank()) return null; String clean=value.trim(); if(clean.length()>36) throw new IllegalArgumentException(field+" inválido"); return clean; }
    private static void validatePage(int page,int size) { if(page<0||size<1||size>100) throw new IllegalArgumentException("page/size fuera de rango"); }
    private static String clean(String value,String field) { if(value==null||value.isBlank()) throw new IllegalArgumentException(field+" es obligatorio"); return value.trim(); }
    private static String blankToNull(String value) { return value==null||value.isBlank()?null:value.trim(); }
    private record Normalized(String deliveryNumber, String purchaseOrderPublicId, NormalizedData data) {
        DeliveryRepository.DeliveryInputData repositoryData() {
            return new DeliveryRepository.DeliveryInputData(data.deliveredAt(), data.receivedByName(), data.receivedByDocument(), data.notes());
        }
    }
    private record NormalizedData(Instant deliveredAt,String receivedByName,String receivedByDocument,String notes) {}
    private static Normalized normalizeData(String number,String order,Instant deliveredAt,String name,String document,String notes) { return new Normalized(clean(number,"deliveryNumber"),clean(order,"purchaseOrderPublicId"),new NormalizedData(deliveredAt,blankToNull(name),blankToNull(document),blankToNull(notes))); }
}

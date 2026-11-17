package pe.giova.quotation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import pe.giova.admin.ConflictException;
import pe.giova.admin.NotFoundException;
import pe.giova.audit.AuditService;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.document.DocumentKind;
import pe.giova.document.DocumentRepository;
import pe.giova.document.DocumentStorageService;
import pe.giova.document.DocumentStorageService.DocumentDownload;
import pe.giova.quotation.QuotationRepository.CustomerData;
import pe.giova.quotation.QuotationRepository.QuotationHeader;
import pe.giova.quotation.QuotationRepository.QuotationInputData;
import pe.giova.quotation.QuotationRepository.QuotationViewData;
import pe.giova.quotation.api.QuotationDtos;

@org.springframework.stereotype.Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class QuotationService {
    private final QuotationRepository repository;
    private final DocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;
    private final QuotationPdfService pdfService;
    private final AuditService audit;

    public QuotationService(QuotationRepository repository, DocumentRepository documentRepository,
                            DocumentStorageService documentStorageService, QuotationPdfService pdfService, AuditService audit) {
        this.repository = repository;
        this.documentRepository = documentRepository;
        this.documentStorageService = documentStorageService;
        this.pdfService = pdfService;
        this.audit = audit;
    }

    public QuotationDtos.Page find(String query, String status, String procurementType, String outcome,
                                   String customerPublicId, String owner,
                                   LocalDate from, LocalDate to, int page, int size, WorkerPrincipal actor) {
        validatePage(page, size);
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("from no puede ser posterior a to");
        Long ownerId = actor.roleCode() == RoleCode.SELLER ? Long.valueOf(actor.id()) : ownerFilter(owner, actor);
        Long customerId = customerPublicId == null || customerPublicId.isBlank() ? null : repository.customer(customerPublicId).id();
        String type = procurementType == null || procurementType.isBlank() ? null
                : ProcurementType.fromInput(procurementType).name();
        String result_ = outcome == null || outcome.isBlank() ? null : QuotationOutcome.fromInput(outcome).name();
        var result = repository.find(normalizeQuery(query), normalizeStatus(status), type, result_, customerId,
                ownerId, from, to, page, size);
        return new QuotationDtos.Page(result.items().stream().map(this::view).toList(), result.totalItems(), result.page(), result.size(), result.totalPages());
    }

    public QuotationDtos.View get(String publicId, WorkerPrincipal actor) {
        return view(repository.findByPublicId(publicId, actor.roleCode() == RoleCode.SELLER ? actor.id() : null)
                .orElseThrow(() -> new NotFoundException("Cotización no encontrada")));
    }

    @Transactional
    public QuotationDtos.View create(QuotationDtos.Input input, WorkerPrincipal actor) {
        requireWrite(actor);
        Normalized normalized = normalize(input);
        CustomerData customer = customerFor(normalized.data().customerPublicId(), actor);
        QuotationCalculation.Result calculation = QuotationCalculation.calculate(normalized.data().items(), normalized.data().includesTax(), normalized.data().taxRate());
        String id = repository.insert(actor.id(), customer, normalized.data(), calculation);
        audit.success(actor, "QUOTATION_CREATED", id, "Cotización creada");
        return get(id, actor);
    }

    @Transactional
    public QuotationDtos.View update(String publicId, QuotationDtos.Update input, WorkerPrincipal actor) {
        requireWrite(actor);
        Normalized normalized = normalize(input);
        CustomerData customer = customerFor(normalized.data().customerPublicId(), actor);
        QuotationCalculation.Result calculation = QuotationCalculation.calculate(normalized.data().items(), normalized.data().includesTax(), normalized.data().taxRate());
        int updated = repository.updateDraft(publicId, input.version(), customer, normalized.data(), calculation);
        if (updated == 0) conflictOrNotFound(publicId, actor, "Solo se puede editar un borrador");
        audit.success(actor, "QUOTATION_UPDATED", publicId, "Cotización actualizada");
        return get(publicId, actor);
    }

    @Transactional
    public QuotationDtos.View issue(String publicId, long version, WorkerPrincipal actor) {
        requireWrite(actor);
        QuotationHeader header = headerFor(publicId, actor);
        if (header.status() == QuotationStatus.ISSUED) return get(publicId, actor);
        if (header.status() != QuotationStatus.DRAFT) throw new ConflictException("La cotización no está en borrador", "INVALID_TRANSITION");
        if (header.version() != version) throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        if (header.validUntil() != null && header.validUntil().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("validUntil no puede estar en el pasado al emitir");
        }
        LocalDate validUntil = header.validUntil();
        if (header.procurementType().isFrameworkAgreement()) {
            // Perú Compras: el plazo se cuenta en días hábiles desde hoy. Si el
            // vendedor no puso fecha, se asume el máximo habitual de 3 días.
            if (validUntil == null) {
                validUntil = BusinessDays.add(LocalDate.now(), BusinessDays.MAX_FRAMEWORK_DAYS);
            } else {
                int days = BusinessDays.between(LocalDate.now(), validUntil);
                if (days < BusinessDays.MIN_FRAMEWORK_DAYS || days > BusinessDays.MAX_FRAMEWORK_DAYS) {
                    throw new IllegalArgumentException("En Perú Compras el plazo debe ser de "
                            + BusinessDays.MIN_FRAMEWORK_DAYS + " a " + BusinessDays.MAX_FRAMEWORK_DAYS
                            + " días hábiles. La fecha indicada equivale a " + days + ".");
                }
            }
        }
        int year = Year.now(java.time.ZoneOffset.UTC).getValue();
        long sequence = repository.nextSequence(year);
        String number = String.format(Locale.ROOT, "COT-%d-%06d", year, sequence);
        int updated = repository.issue(publicId, version, number, Instant.now(), validUntil);
        if (updated == 0) throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        QuotationViewData issued = repository.findByPublicId(publicId, null).orElseThrow(() -> new NotFoundException("Cotización no encontrada"));
        byte[] pdf = pdfService.render(issued);
        var metadata = documentStorageService.store(DocumentKind.QUOTATION_PDF, number + ".pdf", "application/pdf",
                new java.io.ByteArrayInputStream(pdf), pdf.length, actor);
        var stored = documentRepository.findByPublicId(metadata.publicId()).orElseThrow(() -> new IllegalStateException("Documento PDF no registrado"));
        repository.linkDocument(issued.header().id(), stored.id());
        repository.setOutcome(publicId, repository.header(publicId).orElseThrow().version(),
                QuotationOutcome.PENDIENTE, null, null, QuotationStatus.ISSUED);
        audit.success(actor, "QUOTATION_ISSUED", publicId,
                "Cotización emitida " + number + " (" + header.procurementType() + ")");
        return get(publicId, actor);
    }

    @Transactional
    public QuotationDtos.View changeStatus(String publicId, QuotationDtos.StatusChange input, WorkerPrincipal actor) {
        requireWrite(actor);
        QuotationHeader header = headerFor(publicId, actor);
        if (!allowed(header.status(), input.targetStatus())) throw new ConflictException("Transición de estado no permitida", "INVALID_TRANSITION");
        if (header.version() != input.version()) throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        if (repository.changeStatus(publicId, input.version(), input.targetStatus()) == 0) throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        audit.success(actor, "QUOTATION_STATUS_CHANGED", publicId, "Estado " + input.targetStatus());
        return get(publicId, actor);
    }

    /**
     * Cierra el proceso con su resultado real.
     *
     * <p>Regla dura del negocio: todo cierre negativo lleva motivo escrito, y hay
     * que decir quién lo decidió. Perder por precio, que la entidad declare
     * desierto el proceso y retirarse uno mismo porque el proveedor subió el
     * costo son tres cosas distintas, y solo se aprende de ellas si se anotan
     * distinto.
     */
    @Transactional
    public QuotationDtos.View changeOutcome(String publicId, QuotationDtos.OutcomeChange input, WorkerPrincipal actor) {
        requireWrite(actor);
        QuotationHeader header = headerFor(publicId, actor);
        if (header.status() == QuotationStatus.DRAFT) {
            throw new ConflictException("Una cotización en borrador todavía no tiene resultado", "INVALID_TRANSITION");
        }
        if (header.version() != input.version()) {
            throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        QuotationOutcome outcome = QuotationOutcome.fromInput(input.outcome());
        String reason = input.reason() == null ? null : input.reason().trim();
        if (outcome.requiresReason() && (reason == null || reason.length() < 5)) {
            throw new IllegalArgumentException(
                    "Indica el motivo del cierre (mínimo 5 caracteres). Es obligatorio para poder revisarlo después.");
        }
        if (reason != null && reason.length() > 500) reason = reason.substring(0, 500);

        OutcomeSource source;
        if (input.source() != null && !input.source().isBlank()) {
            source = OutcomeSource.fromInput(input.source());
        } else {
            // Por defecto, anular es decisión nuestra y el resto viene de la entidad.
            source = outcome == QuotationOutcome.ANULADA ? OutcomeSource.NOSOTROS : OutcomeSource.ENTIDAD;
        }
        if (!outcome.requiresReason()) {
            reason = reason == null || reason.isBlank() ? null : reason;
        }

        if (repository.setOutcome(publicId, input.version(), outcome, reason, source, outcome.matchingStatus()) == 0) {
            throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        audit.success(actor, "QUOTATION_OUTCOME_SET", publicId,
                "Resultado " + outcome + " (" + source + ")" + (reason == null ? "" : ": " + reason));
        return get(publicId, actor);
    }

    /** Registra la OCAM. Puede llegar días después de ganar, por eso va aparte. */
    @Transactional
    public QuotationDtos.View setOcam(String publicId, QuotationDtos.OcamInput input, WorkerPrincipal actor) {
        requireWrite(actor);
        QuotationHeader header = headerFor(publicId, actor);
        if (!header.procurementType().allowsOcam()) {
            throw new ConflictException(
                    "Una venta a empresa privada no lleva OCAM, solo orden de compra.", "OCAM_NOT_APPLICABLE");
        }
        if (header.version() != input.version()) {
            throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        String number = input.ocamNumber() == null || input.ocamNumber().isBlank() ? null : input.ocamNumber().trim();
        if (number != null && number.length() > 60) throw new IllegalArgumentException("ocamNumber supera 60 caracteres");
        if (number == null && input.ocamDate() == null) {
            throw new IllegalArgumentException("Indica al menos el número o la fecha de la OCAM");
        }
        if (repository.setOcam(publicId, input.version(), number, input.ocamDate()) == 0) {
            throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        audit.success(actor, "QUOTATION_OCAM_SET", publicId,
                "OCAM " + (number == null ? "sin número" : number));
        return get(publicId, actor);
    }

    public DocumentDownload pdf(String publicId, WorkerPrincipal actor) {
        QuotationHeader header = headerFor(publicId, actor);
        if (header.issuedPdfPublicId() == null) throw new NotFoundException("La cotización aún no tiene PDF emitido");
        return documentStorageService.openForDownload(header.issuedPdfPublicId(), actor, actor.roleCode() == RoleCode.SELLER);
    }

    private CustomerData customerFor(String publicId, WorkerPrincipal actor) {
        CustomerData customer = repository.customer(publicId);
        if (!customer.active()) throw new ConflictException("El cliente está inactivo", "CUSTOMER_INACTIVE");
        if (actor.roleCode() == RoleCode.SELLER && customer.ownerWorkerId() != actor.id()) throw new NotFoundException("Cliente no encontrado");
        return customer;
    }

    private QuotationHeader headerFor(String publicId, WorkerPrincipal actor) {
        return repository.header(publicId).filter(h -> actor.roleCode() != RoleCode.SELLER || h.ownerWorkerId() == actor.id())
                .orElseThrow(() -> new NotFoundException("Cotización no encontrada"));
    }

    private static boolean allowed(QuotationStatus current, QuotationStatus target) {
        if (current == QuotationStatus.ISSUED) return target != null && target.terminal();
        if (current == QuotationStatus.DRAFT) return target == QuotationStatus.CANCELLED;
        return false;
    }

    private static Long ownerFilter(String owner, WorkerPrincipal actor) {
        if (owner == null || owner.isBlank() || owner.equalsIgnoreCase("all")) return null;
        if (owner.equalsIgnoreCase("me")) return actor.id();
        throw new IllegalArgumentException("owner debe ser me o all");
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try { return QuotationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)).name(); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("status no permitido"); }
    }
    private static String normalizeQuery(String query) { if (query != null && query.length() > 80) throw new IllegalArgumentException("q supera el máximo de 80 caracteres"); return query == null ? null : query.trim(); }
    private static void validatePage(int page, int size) { if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page/size fuera de rango"); }
    private static void requireWrite(WorkerPrincipal actor) { if (actor.roleCode() != RoleCode.SELLER) throw new SecurityException("Solo el rol VENDEDOR puede crear o modificar cotizaciones"); }
    private static void conflictOrNotFound(String id, WorkerPrincipal actor, String message) { if (id == null || id.isBlank()) throw new NotFoundException("Cotización no encontrada"); throw new ConflictException(message, "VERSION_CONFLICT"); }

    private static Normalized normalize(QuotationDtos.Input input) {
        if (input == null) throw new IllegalArgumentException("La cotización es obligatoria");
        return normalizeData(input.customerPublicId(), input.procurementType(), input.currencyCode(),
                input.exchangeRateToPen(), input.includesTax(), input.taxRate(), input.validUntil(),
                input.deliveryTermDays(), input.warrantyMonths(), input.notes(), input.items());
    }
    private static Normalized normalize(QuotationDtos.Update input) {
        if (input == null) throw new IllegalArgumentException("La cotización es obligatoria");
        if (input.version() == null || input.version() < 0) throw new IllegalArgumentException("version inválida");
        return normalizeData(input.customerPublicId(), input.procurementType(), input.currencyCode(),
                input.exchangeRateToPen(), input.includesTax(), input.taxRate(), input.validUntil(),
                input.deliveryTermDays(), input.warrantyMonths(), input.notes(), input.items());
    }
    private static Normalized normalizeData(String customer, String procurementType, String currency, BigDecimal exchange,
                                            Boolean includesTax, BigDecimal taxRate, LocalDate validUntil,
                                            Integer deliveryTermDays, Integer warrantyMonths, String notes,
                                            java.util.List<QuotationDtos.ItemInput> items) {
        if (customer == null || customer.isBlank()) throw new IllegalArgumentException("customerPublicId es obligatorio");
        ProcurementType type = ProcurementType.fromInput(procurementType);
        String code = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (!code.equals("PEN") && !code.equals("USD")) throw new IllegalArgumentException("currencyCode debe ser PEN o USD");
        if (exchange == null || exchange.signum() <= 0 || (code.equals("PEN") && exchange.compareTo(BigDecimal.ONE) != 0)) throw new IllegalArgumentException("exchangeRateToPen no válido");
        if (includesTax == null) throw new IllegalArgumentException("includesTax es obligatorio");
        if (deliveryTermDays != null && (deliveryTermDays < 1 || deliveryTermDays > 365)) {
            throw new IllegalArgumentException("El plazo de entrega debe estar entre 1 y 365 días");
        }
        if (warrantyMonths != null && (warrantyMonths < 0 || warrantyMonths > 120)) {
            throw new IllegalArgumentException("La garantía debe estar entre 0 y 120 meses");
        }
        for (QuotationDtos.ItemInput item : items == null ? java.util.List.<QuotationDtos.ItemInput>of() : items) {
            if (item.competitorPrice() != null && item.competitorPrice().signum() < 0) {
                throw new IllegalArgumentException("El precio de competencia no puede ser negativo");
            }
            if (item.purchasePrice() != null && item.purchasePrice().signum() < 0) {
                throw new IllegalArgumentException("El precio de compra no puede ser negativo");
            }
        }
        String cleanNotes = notes == null ? null : notes.trim();
        return new Normalized(new QuotationInputData(customer.trim(), type, code, exchange, includesTax, taxRate,
                validUntil, deliveryTermDays, warrantyMonths, cleanNotes, items));
    }
    private record Normalized(QuotationInputData data) {}

    private QuotationDtos.View view(QuotationViewData data) {
        var h = data.header();
        return new QuotationDtos.View(h.publicId(), h.quotationNumber(), h.customerPublicId(), h.customerName(), h.customerDocumentType(), h.customerDocumentNumber(),
                h.ownerWorkerId(), h.ownerDisplayName(), h.status(), h.procurementType(), h.outcome(), h.outcomeReason(),
                h.outcomeSource(), h.outcomeAt(), h.issuedAt(), h.validUntil(), h.deliveryTermDays(), h.warrantyMonths(),
                h.ocamNumber(), h.ocamDate(), h.currencyCode(), h.exchangeRateToPen(), h.includesTax(), h.taxRate(),
                h.subtotalAmount(), h.taxAmount(), h.totalAmount(), h.notes(), h.version(), h.createdAt(), h.updatedAt(), h.issuedPdfPublicId(),
                data.items().stream().map(i -> new QuotationDtos.ItemView(i.lineNumber(), i.productId(), i.sku(), i.description(), i.unit(), i.quantity(), i.unitPrice(), i.competitorPrice(), i.purchasePrice(), i.subtotal(), i.tax(), i.total())).toList());
    }
}

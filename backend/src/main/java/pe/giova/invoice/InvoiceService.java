package pe.giova.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
import pe.giova.customer.CustomerRepository;
import pe.giova.customer.api.CustomerDtos;
import pe.giova.document.DocumentKind;
import pe.giova.document.DocumentRepository;
import pe.giova.document.DocumentStorageService;
import pe.giova.document.DocumentStorageService.DocumentDownload;
import pe.giova.document.api.DocumentDtos.DocumentMetadata;
import pe.giova.invoice.InvoiceRepository.DocumentData;
import pe.giova.invoice.InvoiceRepository.InvoiceHeader;
import pe.giova.invoice.InvoiceRepository.InvoiceInputData;
import pe.giova.invoice.InvoiceRepository.InvoiceViewData;
import pe.giova.invoice.InvoiceRepository.LineInputData;
import pe.giova.invoice.api.InvoiceDtos;
import pe.giova.invoice.xml.InvoiceXmlException;
import pe.giova.invoice.xml.UblInvoiceParser;
import pe.giova.quotation.QuotationRepository;
import pe.giova.quotation.QuotationRepository.CustomerData;

@org.springframework.stereotype.Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class InvoiceService {
    /** IGV vigente en Perú. */
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.18");

    private final InvoiceRepository repository;
    private final QuotationRepository quotationRepository;
    private final CustomerRepository customerRepository;
    private final DocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;
    private final AuditService audit;

    public InvoiceService(InvoiceRepository repository, QuotationRepository quotationRepository,
                          CustomerRepository customerRepository, DocumentRepository documentRepository,
                          DocumentStorageService documentStorageService, AuditService audit) {
        this.repository = repository;
        this.quotationRepository = quotationRepository;
        this.customerRepository = customerRepository;
        this.documentRepository = documentRepository;
        this.documentStorageService = documentStorageService;
        this.audit = audit;
    }

    public InvoiceDtos.Page find(String query, String status, String type, String customerPublicId,
                                 String quotationPublicId, int page, int size, WorkerPrincipal actor) {
        validatePage(page, size);
        String customer = optionalId(customerPublicId, "customerPublicId");
        String quotation = optionalId(quotationPublicId, "quotationPublicId");
        Long quotationOwnerId = actor.roleCode() == RoleCode.SELLER ? actor.id() : null;
        var result = repository.find(normalizeQuery(query), normalizeStatus(status), normalizeType(type),
                customer, quotation, quotationOwnerId, page, size);
        return new InvoiceDtos.Page(result.items().stream().map(this::view).toList(), result.totalItems(),
                result.page(), result.size(), result.totalPages());
    }

    public InvoiceDtos.View get(String publicId, WorkerPrincipal actor) {
        return view(repository.findByPublicId(publicId, sellerOwnerId(actor))
                .orElseThrow(() -> new NotFoundException("Factura no encontrada")));
    }

    @Transactional
    public InvoiceDtos.View create(InvoiceDtos.Input input, WorkerPrincipal actor) {
        requireWrite(actor);
        Normalized normalized = normalize(input);
        CustomerData customer = customerFor(normalized.customerPublicId());
        ResolvedLines lines = resolveLines(normalized);
        InvoiceCalculation.Result calculation = InvoiceCalculation.calculate(lines.calculationItems(),
                normalized.data().includesTax(), normalized.data().taxRate());
        String publicId = repository.insert(actor.id(), customer.id(), lines.quotationId(), normalized.data(),
                lines.storageLines(), calculation);
        audit.success(actor, "INVOICE_CREATED", "INVOICE", publicId, "Factura creada como registro interno");
        return get(publicId, actor);
    }

    @Transactional
    public InvoiceDtos.View update(String publicId, InvoiceDtos.Update input, WorkerPrincipal actor) {
        requireWrite(actor);
        InvoiceHeader current = headerFor(publicId, null);
        if (current.status().terminal()) {
            throw new ConflictException("Una factura terminal no se puede editar", "TERMINAL_DOCUMENT");
        }
        if (input.version() == null || input.version() < 0 || current.version() != input.version()) {
            throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        Normalized normalized = normalize(input);
        CustomerData customer = customerFor(normalized.customerPublicId());
        ResolvedLines lines = resolveLines(normalized);
        InvoiceCalculation.Result calculation = InvoiceCalculation.calculate(lines.calculationItems(),
                normalized.data().includesTax(), normalized.data().taxRate());
        if (repository.updateDraft(publicId, input.version(), customer.id(), lines.quotationId(), normalized.data(),
                lines.storageLines(), calculation) == 0) {
            throw new ConflictException("Solo se puede editar una factura en borrador", "VERSION_CONFLICT");
        }
        audit.success(actor, "INVOICE_UPDATED", "INVOICE", publicId, "Factura interna actualizada");
        return get(publicId, actor);
    }

    @Transactional
    public InvoiceDtos.View changeStatus(String publicId, InvoiceDtos.StatusChange input, WorkerPrincipal actor) {
        requireWrite(actor);
        InvoiceHeader current = headerFor(publicId, null);
        if (current.status() == input.targetStatus()) {
            return get(publicId, actor);
        }
        if (!allowed(current.status(), input.targetStatus())) {
            throw new ConflictException("Transición de estado no permitida", "INVALID_TRANSITION");
        }
        if (current.version() != input.version()) {
            throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        if (repository.changeStatus(publicId, input.version(), input.targetStatus()) == 0) {
            throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        String reason = input.reason() == null || input.reason().isBlank() ? "" : ": " + input.reason().trim();
        audit.success(actor, "INVOICE_STATUS_CHANGED", "INVOICE", publicId,
                "Estado " + input.targetStatus() + reason);
        return get(publicId, actor);
    }

    @Transactional
    public InvoiceDtos.View attachDocument(String publicId, MultipartFile file, String roleInput,
                                           long version, WorkerPrincipal actor) {
        requireWrite(actor);
        InvoiceHeader current = repository.lockByPublicId(publicId, null)
                .orElseThrow(() -> new NotFoundException("Factura no encontrada"));
        if (current.status().terminal()) {
            throw new ConflictException("Una factura terminal no admite cambios documentales", "TERMINAL_DOCUMENT");
        }
        if (current.version() != version) {
            throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        InvoiceDocumentRole role = InvoiceDocumentRole.fromInput(roleInput);
        DocumentKind kind = switch (role) {
            case PDF -> DocumentKind.INVOICE_PDF;
            case XML -> DocumentKind.INVOICE_XML;
            case OTHER -> DocumentKind.OTHER_PDF;
        };
        DocumentMetadata metadata = documentStorageService.store(file, kind, actor);
        var stored = documentRepository.findByPublicId(metadata.publicId())
                .orElseThrow(() -> new IllegalStateException("Documento no registrado"));
        int replaced = repository.deactivateActiveDocuments(current.id(), role);
        repository.linkDocument(current.id(), stored.id(), role);
        if (repository.touchAfterDocument(publicId, version) == 0) {
            throw new ConflictException("El registro fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        audit.success(actor, replaced > 0 ? "INVOICE_DOCUMENT_REPLACED" : "INVOICE_DOCUMENT_ATTACHED",
                "INVOICE", publicId, "Documento " + role + " asociado a registro interno");
        return get(publicId, actor);
    }

    // ------------------------------------------------------------------------
    // Importación de comprobantes en XML (UBL 2.1 / SUNAT)
    //
    // La contadora recibe los XML de su proveedor de facturación electrónica.
    // En lugar de volver a digitarlos, los sube aquí: el sistema lee el archivo,
    // muestra lo que encontró, bloquea los repetidos y —al confirmar— registra la
    // factura, da de alta al cliente si hace falta y deja los precios de compra
    // disponibles para que el vendedor cotice.
    // ------------------------------------------------------------------------

    /** Lee el archivo y devuelve lo que trae, sin guardar nada. */
    public InvoiceDtos.XmlPreview previewXml(MultipartFile file, InvoiceType type, WorkerPrincipal actor) {
        requireWrite(actor);
        UblInvoiceParser.ParsedInvoice parsed = UblInvoiceParser.parse(readBytes(file));
        InvoiceType invoiceType = type == null ? InvoiceType.INVOICE : type;
        return preview(parsed, invoiceType);
    }

    /**
     * Registra la factura leída del XML.
     *
     * @param registerPrices  guarda el precio de cada línea como precio de compra de referencia
     * @param createCustomer  da de alta al adquirente si su RUC no está en la cartera
     */
    @Transactional
    public InvoiceDtos.XmlImportResult importXml(MultipartFile file, InvoiceType type, boolean registerPrices,
                                                 boolean createCustomer, String notes, WorkerPrincipal actor) {
        requireWrite(actor);
        requireAttachableXml(file);
        UblInvoiceParser.ParsedInvoice parsed = UblInvoiceParser.parse(readBytes(file));
        InvoiceType invoiceType = type == null ? InvoiceType.INVOICE : type;
        List<String> warnings = new ArrayList<>();

        // 1. Ningún comprobante se registra dos veces.
        repository.findPublicIdByTypeAndNumber(invoiceType, parsed.invoiceNumber())
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "La factura " + parsed.invoiceNumber() + " ya está registrada en el sistema.",
                            "DUPLICATE_INVOICE");
                });

        // 2. Cliente: se busca por RUC y, si no está, se crea con los datos del XML.
        String documentNumber = parsed.customer().documentNumber();
        if (documentNumber == null || documentNumber.isBlank()) {
            throw new InvoiceXmlException("El XML no trae el RUC del adquirente.");
        }
        var match = repository.findCustomerByDocument(documentNumber.trim());
        boolean customerCreated = false;
        String customerPublicId;
        if (match.isPresent()) {
            if (!match.get().active()) {
                throw new ConflictException("El cliente " + match.get().businessName()
                        + " está inactivo. Actívalo antes de registrar su factura.", "CUSTOMER_INACTIVE");
            }
            customerPublicId = match.get().publicId();
        } else {
            if (!createCustomer) {
                throw new ConflictException("El RUC " + documentNumber
                        + " no está registrado como cliente. Marca «dar de alta al cliente» o créalo primero.",
                        "CUSTOMER_NOT_FOUND");
            }
            customerPublicId = createCustomerFromXml(parsed, actor);
            customerCreated = true;
        }

        // 3. Las líneas se enlazan al catálogo por SKU; las que no existan quedan
        //    como concepto manual con su código y descripción tal cual vienen.
        List<InvoiceDtos.ItemInput> items = new ArrayList<>();
        List<InvoiceRepository.ProductMatch> matched = new ArrayList<>();
        for (UblInvoiceParser.ParsedLine line : parsed.lines()) {
            var product = repository.findProductBySku(line.sku());
            matched.add(product.orElse(null));
            items.add(new InvoiceDtos.ItemInput(product.map(InvoiceRepository.ProductMatch::id).orElse(null),
                    line.sku(), line.description(), line.unit(), line.quantity(), line.unitPrice()));
            if (product.isEmpty()) {
                warnings.add("El código " + line.sku() + " no existe en el catálogo; se registró como concepto libre.");
            }
        }

        String importNote = "Importada del XML " + safeName(file) + " el "
                + LocalDate.now() + " por " + actor.displayName() + ".";
        String finalNotes = notes == null || notes.isBlank() ? importNote : notes.trim() + " · " + importNote;
        if (finalNotes.length() > 1500) finalNotes = finalNotes.substring(0, 1500);

        // 4. El XML de SUNAT ya trae los precios SIN IGV en cac:Price/cbc:PriceAmount.
        InvoiceDtos.Input input = new InvoiceDtos.Input(parsed.invoiceNumber(), invoiceType, customerPublicId,
                null, parsed.issueDate(), parsed.dueDate(), parsed.currencyCode(), BigDecimal.ONE,
                false, DEFAULT_TAX_RATE, finalNotes, items);
        if (parsed.currencyCode().equals("USD")) {
            warnings.add("El comprobante está en USD. Revisa el tipo de cambio antes de emitirlo.");
        }

        InvoiceDtos.View created = create(input, actor);

        // 5. El archivo original queda adjunto como evidencia. Si esto falla, la
        //    transacción completa se revierte: no queremos una factura registrada
        //    sin su respaldo.
        created = attachDocument(created.publicId(), file, InvoiceDocumentRole.XML.name(),
                created.version(), actor);

        // 6. Precios de compra para el vendedor.
        int prices = 0;
        if (registerPrices) {
            Instant effectiveAt = parsed.issueDate().atStartOfDay(ZoneId.of("America/Lima")).toInstant();
            String supplier = parsed.supplier().name();
            for (int i = 0; i < parsed.lines().size(); i++) {
                InvoiceRepository.ProductMatch product = matched.get(i);
                if (product == null) continue;
                UblInvoiceParser.ParsedLine line = parsed.lines().get(i);
                repository.insertPriceReference(actor.id(), product.id(), trimTo(supplier, 180),
                        trimTo(invoiceType.name() + " " + parsed.invoiceNumber(), 300), parsed.currencyCode(),
                        line.unitPrice(), false, DEFAULT_TAX_RATE, effectiveAt,
                        trimTo("Precio tomado del XML " + parsed.invoiceNumber(), 500));
                prices++;
            }
        }

        audit.success(actor, "INVOICE_XML_IMPORTED", "INVOICE", created.publicId(),
                "Factura " + parsed.invoiceNumber() + " importada desde XML con "
                        + parsed.lines().size() + " líneas");
        return new InvoiceDtos.XmlImportResult(created, customerCreated, prices, warnings);
    }

    private InvoiceDtos.XmlPreview preview(UblInvoiceParser.ParsedInvoice parsed, InvoiceType type) {
        var duplicate = repository.findPublicIdByTypeAndNumber(type, parsed.invoiceNumber());
        String documentNumber = parsed.customer().documentNumber();
        var customer = documentNumber == null || documentNumber.isBlank()
                ? java.util.Optional.<InvoiceRepository.CustomerMatch>empty()
                : repository.findCustomerByDocument(documentNumber.trim());

        List<InvoiceDtos.XmlLinePreview> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int newProducts = 0;
        for (UblInvoiceParser.ParsedLine line : parsed.lines()) {
            var product = repository.findProductBySku(line.sku());
            if (product.isEmpty()) newProducts++;
            lines.add(new InvoiceDtos.XmlLinePreview(line.lineNumber(), line.sku(), line.description(),
                    line.unit(), line.quantity(), line.unitPrice(),
                    product.map(InvoiceRepository.ProductMatch::id).orElse(null),
                    product.map(InvoiceRepository.ProductMatch::name).orElse(null), product.isEmpty()));
            total = total.add(line.quantity().multiply(line.unitPrice()));
        }
        BigDecimal withTax = total.multiply(BigDecimal.ONE.add(DEFAULT_TAX_RATE))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        return new InvoiceDtos.XmlPreview(parsed.invoiceNumber(), type, parsed.issueDate(), parsed.dueDate(),
                parsed.currencyCode(), parsed.supplier().name(), parsed.supplier().documentNumber(),
                parsed.customer().name(), documentNumber,
                customer.map(InvoiceRepository.CustomerMatch::publicId).orElse(null), customer.isPresent(),
                duplicate.isPresent(), duplicate.orElse(null), parsed.declaredTotal(), withTax,
                newProducts, lines);
    }

    private String createCustomerFromXml(UblInvoiceParser.ParsedInvoice parsed, WorkerPrincipal actor) {
        String document = parsed.customer().documentNumber().trim();
        String name = parsed.customer().name();
        if (name == null || name.isBlank()) name = "Cliente " + document;
        // La base exige document_type en (DNI, RUC, CE, OTHER) con su formato:
        // RUC de 11 dígitos, DNI de 8, y el resto alfanumérico de 3 a 20.
        String documentType;
        if (document.matches("[0-9]{11}")) {
            documentType = "RUC";
        } else if (document.matches("[0-9]{8}")) {
            documentType = "DNI";
        } else if (document.matches("[A-Za-z0-9-]{3,20}")) {
            documentType = "OTHER";
        } else {
            throw new InvoiceXmlException("El documento del adquirente (" + document
                    + ") no tiene un formato válido para registrarlo como cliente.");
        }
        // customer_type solo admite PERSON o COMPANY.
        String customerType = documentType.equals("RUC") ? "COMPANY" : "PERSON";
        return customerRepository.insert(actor.id(), new CustomerDtos.CustomerInput(customerType, documentType,
                document, trimTo(name, 180), null, null, null, null,
                "Alta automática al importar el XML " + parsed.invoiceNumber() + "."));
    }

    /**
     * El XML se adjunta después de crear la factura, y el almacén de documentos
     * exige extensión .xml y MIME application/xml o text/xml. Si el archivo no
     * cumple, conviene detenerse antes de escribir nada: una excepción dentro de
     * la transacción ya no se puede "perdonar" a mitad de camino.
     */
    private static void requireAttachableXml(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".xml")) {
            throw new InvoiceXmlException("El archivo debe tener extensión .xml para poder adjuntarse.");
        }
        String contentType = file.getContentType();
        String media = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!media.equals("application/xml") && !media.equals("text/xml")) {
            throw new InvoiceXmlException("El navegador envió el archivo como «"
                    + (media.isBlank() ? "sin tipo" : media)
                    + "». Vuelve a seleccionarlo: debe subirse como XML.");
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvoiceXmlException("Adjunta el archivo XML del comprobante.");
        }
        try {
            return file.getBytes();
        } catch (java.io.IOException exception) {
            throw new InvoiceXmlException("No se pudo leer el archivo subido.", exception);
        }
    }

    private static String safeName(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name == null || name.isBlank() ? "comprobante.xml" : trimTo(name, 120);
    }

    private static String trimTo(String value, int max) {
        if (value == null) return null;
        String clean = value.trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    public List<InvoiceDtos.DocumentView> documents(String publicId, WorkerPrincipal actor) {
        InvoiceDtos.View invoice = get(publicId, actor);
        return invoice.documents();
    }

    public DocumentDownload downloadDocument(String publicId, String documentPublicId, WorkerPrincipal actor) {
        get(publicId, actor);
        repository.activeDocument(publicId, documentPublicId)
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
        DocumentDownload download = documentStorageService.openForDownload(documentPublicId, actor, true);
        audit.success(actor, "INVOICE_DOCUMENT_DOWNLOADED", "INVOICE", publicId,
                "Descarga de documento interno " + documentPublicId);
        return download;
    }

    private InvoiceHeader headerFor(String publicId, Long sellerOwnerId) {
        return repository.lockByPublicId(publicId, sellerOwnerId)
                .orElseThrow(() -> new NotFoundException("Factura no encontrada"));
    }

    private CustomerData customerFor(String publicId) {
        CustomerData customer = quotationRepository.customer(publicId);
        if (!customer.active()) {
            throw new ConflictException("El cliente está inactivo", "CUSTOMER_INACTIVE");
        }
        return customer;
    }

    private ResolvedLines resolveLines(Normalized normalized) {
        if (normalized.quotationPublicId() != null) {
            var quotation = quotationRepository.findByPublicId(normalized.quotationPublicId(), null)
                    .orElseThrow(() -> new NotFoundException("Cotización no encontrada"));
            if (!quotation.header().customerPublicId().equals(normalized.customerPublicId())) {
                throw new ConflictException("La cotización no pertenece al cliente indicado", "CUSTOMER_QUOTATION_MISMATCH");
            }
            List<InvoiceDtos.ItemInput> items = quotation.items().stream()
                    .map(item -> new InvoiceDtos.ItemInput(item.productId(), item.sku(), item.description(), item.unit(),
                            item.quantity(), item.unitPrice())).toList();
            List<LineInputData> storage = quotation.items().stream()
                    .map(item -> new LineInputData(item.productId(), item.sku(), item.description(), item.unit(),
                            item.quantity(), item.unitPrice())).toList();
            return new ResolvedLines(quotation.header().id(), items, storage);
        }
        if (normalized.items() == null || normalized.items().isEmpty()) {
            throw new IllegalArgumentException("La factura requiere líneas o una cotización");
        }
        List<LineInputData> storage = normalized.items().stream().map(this::line).toList();
        return new ResolvedLines(null, normalized.items(), storage);
    }

    private LineInputData line(InvoiceDtos.ItemInput item) {
        if (item == null) throw new IllegalArgumentException("La línea de factura es obligatoria");
        if (item.productId() == null) {
            return new LineInputData(null, clean(item.sku(), "sku"), clean(item.description(), "description"),
                    cleanUnit(item.unit()), item.quantity(), item.unitPrice());
        }
        var product = quotationRepository.product(item.productId());
        if (!product.active()) throw new ConflictException("El producto no está activo", "PRODUCT_INACTIVE");
        return new LineInputData(product.id(), product.sku(), product.name(), cleanUnit(item.unit()),
                item.quantity(), item.unitPrice());
    }

    private static Normalized normalize(InvoiceDtos.Input input) {
        if (input == null) throw new IllegalArgumentException("La factura es obligatoria");
        return normalizeData(input.invoiceNumber(), input.invoiceType(), input.customerPublicId(),
                input.quotationPublicId(), input.issueDate(), input.dueDate(), input.currencyCode(),
                input.exchangeRateToPen(), input.includesTax(), input.taxRate(), input.notes(), input.items());
    }

    private static Normalized normalize(InvoiceDtos.Update input) {
        if (input == null) throw new IllegalArgumentException("La factura es obligatoria");
        if (input.version() == null || input.version() < 0) throw new IllegalArgumentException("version inválida");
        return normalizeData(input.invoiceNumber(), input.invoiceType(), input.customerPublicId(),
                input.quotationPublicId(), input.issueDate(), input.dueDate(), input.currencyCode(),
                input.exchangeRateToPen(), input.includesTax(), input.taxRate(), input.notes(), input.items());
    }

    private static Normalized normalizeData(String number, InvoiceType type, String customer, String quotation,
                                            LocalDate issueDate, LocalDate dueDate, String currency,
                                            BigDecimal exchange, Boolean includesTax, BigDecimal taxRate,
                                            String notes, List<InvoiceDtos.ItemInput> items) {
        String cleanNumber = clean(number, "invoiceNumber");
        if (cleanNumber.length() > 40) throw new IllegalArgumentException("invoiceNumber supera 40 caracteres");
        if (type == null) throw new IllegalArgumentException("invoiceType es obligatorio");
        String cleanCustomer = clean(customer, "customerPublicId");
        String cleanQuotation = quotation == null || quotation.isBlank() ? null : quotation.trim();
        if (cleanQuotation != null && cleanQuotation.length() > 36) throw new IllegalArgumentException("quotationPublicId inválido");
        if (issueDate == null) throw new IllegalArgumentException("issueDate es obligatorio");
        if (dueDate != null && dueDate.isBefore(issueDate)) throw new IllegalArgumentException("dueDate no puede ser anterior a issueDate");
        String code = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (!code.equals("PEN") && !code.equals("USD")) throw new IllegalArgumentException("currencyCode debe ser PEN o USD");
        if (exchange == null || exchange.signum() <= 0 || (code.equals("PEN") && exchange.compareTo(BigDecimal.ONE) != 0)) {
            throw new IllegalArgumentException("exchangeRateToPen no válido");
        }
        if (includesTax == null) throw new IllegalArgumentException("includesTax es obligatorio");
        if (taxRate == null || taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("taxRate debe estar entre 0 y 1");
        }
        String cleanNotes = notes == null || notes.isBlank() ? null : notes.trim();
        return new Normalized(new InvoiceInputData(cleanNumber, type, issueDate, dueDate, code, exchange,
                includesTax, taxRate, cleanNotes), cleanCustomer, cleanQuotation, items);
    }

    private InvoiceDtos.View view(InvoiceViewData data) {
        var h = data.header();
        return new InvoiceDtos.View(h.publicId(), h.invoiceNumber(), h.invoiceType(), h.customerPublicId(), h.customerName(),
                h.customerDocumentType(), h.customerDocumentNumber(), h.quotationPublicId(), h.quotationNumber(),
                h.quotationOwnerWorkerId(), h.registeredByWorkerId(), h.registeredByDisplayName(), h.status(), h.issueDate(),
                h.dueDate(), h.currencyCode(), h.exchangeRateToPen(), h.includesTax(), h.taxRate(), h.subtotalAmount(),
                h.taxAmount(), h.totalAmount(), h.notes(), h.version(), h.createdAt(), h.updatedAt(),
                data.items().stream().map(i -> new InvoiceDtos.ItemView(i.lineNumber(), i.productId(), i.sku(), i.description(),
                        i.unit(), i.quantity(), i.unitPrice(), i.subtotal(), i.tax(), i.total())).toList(),
                data.documents().stream().map(document -> documentView(h.publicId(), document)).toList());
    }

    private InvoiceDtos.DocumentView documentView(String invoicePublicId, DocumentData document) {
        return new InvoiceDtos.DocumentView(document.publicId(), document.role(), document.originalName(),
                document.mediaType(), document.extension(), document.sizeBytes(), document.sha256Hex(), document.version(),
                document.createdAt(), "/api/v1/accounting/invoices/" + invoicePublicId + "/documents/" + document.publicId() + "/content");
    }

    private static Long sellerOwnerId(WorkerPrincipal actor) {
        return actor.roleCode() == RoleCode.SELLER ? actor.id() : null;
    }

    private static boolean allowed(InvoiceStatus current, InvoiceStatus target) {
        return (current == InvoiceStatus.DRAFT && (target == InvoiceStatus.ISSUED || target == InvoiceStatus.CANCELLED))
                || (current == InvoiceStatus.ISSUED && target == InvoiceStatus.CANCELLED);
    }

    private static void requireWrite(WorkerPrincipal actor) {
        if (actor == null || (actor.roleCode() != RoleCode.ADMIN && actor.roleCode() != RoleCode.ACCOUNTANT)) {
            throw new SecurityException("Se requiere rol ADMIN o ACCOUNTANT");
        }
    }

    private static String optionalId(String value, String field) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        if (clean.length() > 36) throw new IllegalArgumentException(field + " inválido");
        return clean;
    }

    private static String normalizeStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try { return InvoiceStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)).name(); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("status no permitido"); }
    }

    private static String normalizeType(String value) {
        if (value == null || value.isBlank()) return null;
        try { return InvoiceType.valueOf(value.trim().toUpperCase(Locale.ROOT)).name(); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("type no permitido"); }
    }

    private static String normalizeQuery(String value) {
        if (value != null && value.length() > 80) throw new IllegalArgumentException("q supera el máximo de 80 caracteres");
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page/size fuera de rango");
    }

    private static String clean(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " es obligatorio");
        return value.trim();
    }

    private static String cleanUnit(String value) {
        return value == null || value.isBlank() ? "unidad" : value.trim();
    }

    private record Normalized(InvoiceInputData data, String customerPublicId, String quotationPublicId,
                              List<InvoiceDtos.ItemInput> items) {}

    private record ResolvedLines(Long quotationId, List<InvoiceDtos.ItemInput> calculationItems,
                                 List<LineInputData> storageLines) {}
}

package pe.giova.quotation;

import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.quotation.api.QuotationDtos;

@RestController
@RequestMapping("/api/v1/sales/quotations")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class QuotationController {
    private final QuotationService service;
    public QuotationController(QuotationService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public QuotationDtos.Page list(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                   @RequestParam(required = false) String procurementType,
                                   @RequestParam(required = false) String outcome,
                                   @RequestParam(required = false) String customerId, @RequestParam(required = false) String owner,
                                   @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                   @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                   Authentication authentication) {
        return service.find(q, status, procurementType, outcome, customerId, owner, from, to, page, size,
                principal(authentication));
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public QuotationDtos.View get(@PathVariable String publicId, Authentication authentication) { return service.get(publicId, principal(authentication)); }

    @PostMapping
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<QuotationDtos.View> create(@Valid @RequestBody QuotationDtos.Input input, Authentication authentication) {
        var result = service.create(input, principal(authentication));
        return ResponseEntity.created(java.net.URI.create("/api/v1/sales/quotations/" + result.publicId())).body(result);
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasRole('SELLER')")
    public QuotationDtos.View update(@PathVariable String publicId, @Valid @RequestBody QuotationDtos.Update input, Authentication authentication) {
        return service.update(publicId, input, principal(authentication));
    }

    @PostMapping("/{publicId}/issue")
    @PreAuthorize("hasRole('SELLER')")
    public QuotationDtos.View issue(@PathVariable String publicId, @Valid @RequestBody QuotationDtos.Issue input, Authentication authentication) {
        return service.issue(publicId, input.version(), principal(authentication));
    }

    @PostMapping("/{publicId}/status")
    @PreAuthorize("hasRole('SELLER')")
    public QuotationDtos.View status(@PathVariable String publicId, @Valid @RequestBody QuotationDtos.StatusChange input, Authentication authentication) {
        return service.changeStatus(publicId, input, principal(authentication));
    }

    /** Cierra el proceso con su resultado real; el motivo es obligatorio si es negativo. */
    @PostMapping("/{publicId}/outcome")
    @PreAuthorize("hasRole('SELLER')")
    public QuotationDtos.View outcome(@PathVariable String publicId, @Valid @RequestBody QuotationDtos.OutcomeChange input,
                                      Authentication authentication) {
        return service.changeOutcome(publicId, input, principal(authentication));
    }

    /** Registra la OCAM, que suele llegar días después de ganar. */
    @PostMapping("/{publicId}/ocam")
    @PreAuthorize("hasRole('SELLER')")
    public QuotationDtos.View ocam(@PathVariable String publicId, @Valid @RequestBody QuotationDtos.OcamInput input,
                                   Authentication authentication) {
        return service.setOcam(publicId, input, principal(authentication));
    }

    @GetMapping("/{publicId}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public ResponseEntity<Resource> pdf(@PathVariable String publicId, Authentication authentication) {
        var download = service.pdf(publicId, principal(authentication));
        var document = download.document();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).contentLength(document.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(document.originalName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore()).body(download.resource());
    }

    private static WorkerPrincipal principal(Authentication authentication) { return (WorkerPrincipal) authentication.getPrincipal(); }
}

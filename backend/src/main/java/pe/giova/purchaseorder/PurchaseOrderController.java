package pe.giova.purchaseorder;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.purchaseorder.api.PurchaseOrderDtos;

@RestController
@RequestMapping("/api/v1/accounting/purchase-orders")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class PurchaseOrderController {
    private final PurchaseOrderService service;
    public PurchaseOrderController(PurchaseOrderService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public PurchaseOrderDtos.Page list(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String customerId, @RequestParam(required = false) String quotationId,
                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                       Authentication authentication) {
        return service.find(q, status, customerId, quotationId, page, size, principal(authentication));
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public PurchaseOrderDtos.View get(@PathVariable String publicId, Authentication authentication) { return service.get(publicId, principal(authentication)); }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<PurchaseOrderDtos.View> create(@Valid @RequestBody PurchaseOrderDtos.Input input, Authentication authentication) {
        var result = service.create(input, principal(authentication));
        return ResponseEntity.created(java.net.URI.create("/api/v1/accounting/purchase-orders/" + result.publicId())).body(result);
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public PurchaseOrderDtos.View update(@PathVariable String publicId, @Valid @RequestBody PurchaseOrderDtos.Update input, Authentication authentication) {
        return service.update(publicId, input, principal(authentication));
    }

    @PostMapping("/{publicId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public PurchaseOrderDtos.View status(@PathVariable String publicId, @Valid @RequestBody PurchaseOrderDtos.StatusChange input, Authentication authentication) {
        return service.changeStatus(publicId, input, principal(authentication));
    }

    @PostMapping(value = "/{publicId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public PurchaseOrderDtos.View attachDocument(@PathVariable String publicId, @RequestPart("file") MultipartFile file,
                                                 @RequestParam String role, @RequestParam long version, Authentication authentication) {
        return service.attachDocument(publicId, file, role, version, principal(authentication));
    }

    @GetMapping("/{publicId}/documents")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public java.util.List<PurchaseOrderDtos.DocumentView> documents(@PathVariable String publicId, Authentication authentication) {
        return service.documents(publicId, principal(authentication));
    }

    @GetMapping("/{publicId}/documents/{documentPublicId}/content")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public ResponseEntity<Resource> document(@PathVariable String publicId, @PathVariable String documentPublicId, Authentication authentication) {
        var download = service.downloadDocument(publicId, documentPublicId, principal(authentication));
        var document = download.document();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(document.mediaType()));
        headers.setContentLength(document.sizeBytes());
        headers.setContentDisposition(ContentDisposition.attachment().filename(document.originalName(), StandardCharsets.UTF_8).build());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl(CacheControl.noStore());
        return ResponseEntity.ok().headers(headers).body(download.resource());
    }

    private static WorkerPrincipal principal(Authentication authentication) { return (WorkerPrincipal) authentication.getPrincipal(); }
}

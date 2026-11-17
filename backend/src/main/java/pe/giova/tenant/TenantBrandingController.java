package pe.giova.tenant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.giova.document.TenantLogoStorage;

@RestController
@RequestMapping("/api/v1/tenant/branding")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class TenantBrandingController {
    private final TenantBrandingService service;

    public TenantBrandingController(TenantBrandingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public ResponseEntity<TenantBrandingDtos.Response> current() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.current());
    }

    @GetMapping("/logo")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public ResponseEntity<byte[]> logo() {
        TenantLogoStorage.Logo logo = service.currentLogo();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(logo.mediaType()))
                .contentLength(logo.bytes().length)
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore()).body(logo.bytes());
    }
}

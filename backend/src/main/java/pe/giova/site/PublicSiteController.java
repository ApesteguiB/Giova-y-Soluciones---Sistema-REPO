package pe.giova.site;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.giova.document.TenantLogoStorage;

/**
 * Sitio público de la empresa dueña del dominio. Vive bajo /api/v1/catalog
 * porque esa ruta ya es pública en la configuración de seguridad.
 */
@RestController
@RequestMapping("/api/v1/catalog/site")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class PublicSiteController {
    private final PublicSiteService service;

    public PublicSiteController(PublicSiteService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PublicSiteDtos.Site> current() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(service.current());
    }

    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo() {
        TenantLogoStorage.Logo logo = service.currentLogo();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(logo.mediaType()))
                .contentLength(logo.bytes().length)
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noCache()).body(logo.bytes());
    }
}

package pe.giova.document;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.giova.auth.WorkerPrincipal;

@RestController
@RequestMapping("/api/v1/documents")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class DocumentController {
    private final DocumentStorageService service;

    public DocumentController(DocumentStorageService service) {
        this.service = service;
    }

    @GetMapping("/{publicId}/content")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public ResponseEntity<Resource> content(@PathVariable String publicId, Authentication authentication) {
        DocumentStorageService.DocumentDownload download = service.openForDownload(publicId,
                (WorkerPrincipal) authentication.getPrincipal());
        var document = download.document();
        MediaType mediaType = MediaType.parseMediaType(document.mediaType());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentLength(document.sizeBytes());
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(document.originalName(), java.nio.charset.StandardCharsets.UTF_8).build());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl(CacheControl.noStore());
        return ResponseEntity.ok().headers(headers).body(download.resource());
    }
}

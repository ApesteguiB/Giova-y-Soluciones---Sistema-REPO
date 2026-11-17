package pe.giova.report;

import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.giova.auth.WorkerPrincipal;

@RestController
@RequestMapping("/api/v1/reports")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class ReportController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping(value = "/quotations.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ByteArrayResource> quotations(@RequestParam(required = false) LocalDate from,
                                                        @RequestParam(required = false) LocalDate to,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String currency,
                                                        Authentication authentication) {
        return response(service.quotations(from, to, status, currency, principal(authentication)));
    }

    @GetMapping(value = "/accounting.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<ByteArrayResource> accounting(@RequestParam(required = false) LocalDate from,
                                                        @RequestParam(required = false) LocalDate to,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String currency,
                                                        Authentication authentication) {
        return response(service.accounting(from, to, status, currency, principal(authentication)));
    }

    private static ResponseEntity<ByteArrayResource> response(ReportService.ReportFile file) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX);
        headers.setContentLength(file.content().length);
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.fileName(), java.nio.charset.StandardCharsets.UTF_8).build());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl(CacheControl.noStore());
        return ResponseEntity.ok().headers(headers).body(new ByteArrayResource(file.content()));
    }

    private static WorkerPrincipal principal(Authentication authentication) {
        return (WorkerPrincipal) authentication.getPrincipal();
    }
}

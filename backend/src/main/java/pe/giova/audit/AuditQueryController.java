package pe.giova.audit;

import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.giova.audit.api.AuditDtos;
import pe.giova.auth.WorkerPrincipal;

@RestController
@RequestMapping("/api/v1/admin/audit-events")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class AuditQueryController {
    private final AuditQueryService service;

    public AuditQueryController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AuditDtos.Page list(@RequestParam(required = false) String q,
                               @RequestParam(required = false) String action,
                               @RequestParam(required = false) String result,
                               @RequestParam(required = false) Long actorWorkerId,
                               @RequestParam(required = false) LocalDate from,
                               @RequestParam(required = false) LocalDate to,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size,
                               Authentication authentication) {
        return service.find(q, action, result, actorWorkerId, from, to, page, size, principal(authentication));
    }

    private static WorkerPrincipal principal(Authentication authentication) {
        return (WorkerPrincipal) authentication.getPrincipal();
    }
}

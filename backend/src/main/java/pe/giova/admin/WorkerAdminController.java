package pe.giova.admin;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerAccountService;
import pe.giova.auth.WorkerPrincipal;

@RestController
@RequestMapping("/api/v1/admin/workers")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class WorkerAdminController {
    private final WorkerAccountService accounts;

    public WorkerAdminController(WorkerAccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public ResponseEntity<List<AdminWorkerDtos.WorkerView>> list(Authentication authentication) {
        return noStore(HttpStatus.OK, accounts.list(principal(authentication)));
    }

    @PostMapping
    public ResponseEntity<AdminWorkerDtos.WorkerProvisioned> create(
            @Valid @RequestBody AdminWorkerDtos.WorkerInput input, Authentication authentication) {
        return noStore(HttpStatus.CREATED, accounts.create(input, principal(authentication)));
    }

    @PutMapping("/{workerId}")
    public ResponseEntity<AdminWorkerDtos.WorkerView> update(
            @PathVariable long workerId, @Valid @RequestBody AdminWorkerDtos.WorkerUpdate input,
            Authentication authentication) {
        return noStore(HttpStatus.OK, accounts.update(workerId, input, principal(authentication)));
    }

    @PatchMapping("/{workerId}/status")
    public ResponseEntity<AdminWorkerDtos.WorkerView> setStatus(
            @PathVariable long workerId, @Valid @RequestBody AdminWorkerDtos.WorkerStatusInput input,
            Authentication authentication) {
        return noStore(HttpStatus.OK, accounts.setEnabled(workerId, input, principal(authentication)));
    }

    @PostMapping("/{workerId}/password-reset")
    public ResponseEntity<AdminWorkerDtos.WorkerProvisioned> resetPassword(
            @PathVariable long workerId, Authentication authentication) {
        return noStore(HttpStatus.OK, accounts.resetPassword(workerId, principal(authentication)));
    }

    private static WorkerPrincipal principal(Authentication authentication) {
        return (WorkerPrincipal) authentication.getPrincipal();
    }

    private static <T> ResponseEntity<T> noStore(HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache").body(body);
    }
}

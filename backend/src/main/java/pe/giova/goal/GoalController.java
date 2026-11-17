package pe.giova.goal;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import pe.giova.goal.api.GoalDtos;

@RestController
@RequestMapping("/api/v1/goals")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class GoalController {
    private final GoalService service;

    public GoalController(GoalService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public GoalDtos.Page list(@RequestParam(required = false) Long workerId,
                              @RequestParam(required = false) String goalType,
                              @RequestParam(required = false) Boolean active,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size,
                              Authentication authentication) {
        return service.find(workerId, goalType, active, page, size, principal(authentication));
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public GoalDtos.View get(@PathVariable String publicId, Authentication authentication) {
        return service.get(publicId, principal(authentication));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GoalDtos.View> create(@Valid @RequestBody GoalDtos.Input input,
                                                Authentication authentication) {
        GoalDtos.View created = service.create(input, principal(authentication));
        return ResponseEntity.created(URI.create("/api/v1/goals/" + created.publicId())).body(created);
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    public GoalDtos.View update(@PathVariable String publicId, @Valid @RequestBody GoalDtos.Update input,
                                Authentication authentication) {
        return service.update(publicId, input, principal(authentication));
    }

    private static WorkerPrincipal principal(Authentication authentication) {
        return (WorkerPrincipal) authentication.getPrincipal();
    }
}

package pe.giova.task;

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
import pe.giova.task.api.TaskDtos;

@RestController
@RequestMapping("/api/v1/tasks")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class TaskController {
    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public TaskDtos.Page list(@RequestParam(required = false) String q,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) Long assignedToWorkerId,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size,
                              Authentication authentication) {
        return service.find(q, status, assignedToWorkerId, page, size, principal(authentication));
    }

    @GetMapping("/assignees")
    @PreAuthorize("hasRole('ADMIN')")
    public java.util.List<TaskDtos.Assignee> assignees(Authentication authentication) {
        return service.assignees(principal(authentication));
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public TaskDtos.View get(@PathVariable String publicId, Authentication authentication) {
        return service.get(publicId, principal(authentication));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TaskDtos.View> create(@Valid @RequestBody TaskDtos.Input input,
                                                Authentication authentication) {
        TaskDtos.View created = service.create(input, principal(authentication));
        return ResponseEntity.created(URI.create("/api/v1/tasks/" + created.publicId())).body(created);
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public TaskDtos.View update(@PathVariable String publicId, @Valid @RequestBody TaskDtos.Update input,
                                Authentication authentication) {
        return service.update(publicId, input, principal(authentication));
    }

    @PostMapping("/{publicId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public TaskDtos.View status(@PathVariable String publicId, @Valid @RequestBody TaskDtos.StatusChange input,
                                Authentication authentication) {
        return service.changeStatus(publicId, input, principal(authentication));
    }

    private static WorkerPrincipal principal(Authentication authentication) {
        return (WorkerPrincipal) authentication.getPrincipal();
    }
}

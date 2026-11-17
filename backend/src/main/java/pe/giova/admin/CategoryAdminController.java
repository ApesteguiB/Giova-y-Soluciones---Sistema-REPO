package pe.giova.admin;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.giova.admin.api.AdminDtos.CategoryInput;
import pe.giova.admin.api.AdminDtos.CategoryUpdate;
import pe.giova.admin.api.AdminDtos.CategoryView;
import pe.giova.admin.api.AdminDtos.StatusInput;

@RestController
@RequestMapping("/api/v1/admin/categories")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
public class CategoryAdminController {
    private final CategoryService service;

    public CategoryAdminController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<CategoryView> list(@RequestParam(required = false) String q,
                                   @RequestParam(required = false) Boolean active) {
        return service.find(q, active);
    }

    @GetMapping("/{id}")
    public CategoryView get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<CategoryView> create(@Valid @RequestBody CategoryInput input) {
        CategoryView created = service.create(input);
        return ResponseEntity.created(URI.create("/api/v1/admin/categories/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public CategoryView update(@PathVariable long id, @Valid @RequestBody CategoryUpdate input) {
        return service.update(id, input);
    }

    @PatchMapping("/{id}/status")
    public CategoryView status(@PathVariable long id, @Valid @RequestBody StatusInput input) {
        return service.setActive(id, input.active(), input.version());
    }
}

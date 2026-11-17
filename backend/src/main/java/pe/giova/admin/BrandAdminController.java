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
import pe.giova.admin.api.AdminDtos.BrandInput;
import pe.giova.admin.api.AdminDtos.BrandUpdate;
import pe.giova.admin.api.AdminDtos.BrandView;
import pe.giova.admin.api.AdminDtos.StatusInput;

@RestController
@RequestMapping("/api/v1/admin/brands")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
public class BrandAdminController {
    private final BrandService service;

    public BrandAdminController(BrandService service) {
        this.service = service;
    }

    @GetMapping
    public List<BrandView> list(@RequestParam(required = false) String q,
                                @RequestParam(required = false) Boolean active) {
        return service.find(q, active);
    }

    @GetMapping("/{id}")
    public BrandView get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<BrandView> create(@Valid @RequestBody BrandInput input) {
        BrandView created = service.create(input);
        return ResponseEntity.created(URI.create("/api/v1/admin/brands/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public BrandView update(@PathVariable long id, @Valid @RequestBody BrandUpdate input) {
        return service.update(id, input);
    }

    @PatchMapping("/{id}/status")
    public BrandView status(@PathVariable long id, @Valid @RequestBody StatusInput input) {
        return service.setActive(id, input.active(), input.version());
    }
}

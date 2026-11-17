package pe.giova.admin;

import jakarta.validation.Valid;
import java.net.URI;
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
import pe.giova.admin.api.AdminDtos.AdminPage;
import pe.giova.admin.api.AdminDtos.ProductInput;
import pe.giova.admin.api.AdminDtos.ProductUpdate;
import pe.giova.admin.api.AdminDtos.ProductView;
import pe.giova.admin.api.AdminDtos.StatusInput;

@RestController
@RequestMapping("/api/v1/admin/products")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
public class ProductAdminController {
    private final ProductService service;

    public ProductAdminController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public AdminPage<ProductView> list(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) Boolean active,
                                       @RequestParam(required = false) Long brandId,
                                       @RequestParam(required = false) Long categoryId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "60") int size) {
        return service.find(q, active, brandId, categoryId, page, size);
    }

    @GetMapping("/{id}")
    public ProductView get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<ProductView> create(@Valid @RequestBody ProductInput input) {
        ProductView created = service.create(input);
        return ResponseEntity.created(URI.create("/api/v1/admin/products/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ProductView update(@PathVariable long id, @Valid @RequestBody ProductUpdate input) {
        return service.update(id, input);
    }

    @PatchMapping("/{id}/status")
    public ProductView status(@PathVariable long id, @Valid @RequestBody StatusInput input) {
        return service.setActive(id, input.active(), input.version());
    }
}

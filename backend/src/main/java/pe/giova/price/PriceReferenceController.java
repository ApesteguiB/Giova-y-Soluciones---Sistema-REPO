package pe.giova.price;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
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
import pe.giova.price.api.PriceReferenceDtos.PriceReferenceInput;
import pe.giova.price.api.PriceReferenceDtos.PriceReferencePage;
import pe.giova.price.api.PriceReferenceDtos.PriceReferenceView;

@RestController
@RequestMapping("/api/v1/sales/price-references")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class PriceReferenceController {
    private final PriceReferenceService service;

    public PriceReferenceController(PriceReferenceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public PriceReferencePage list(@RequestParam(required = false) Long productId,
                                   @RequestParam(required = false) Instant from,
                                   @RequestParam(required = false) Instant to,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return service.find(productId, from, to, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public PriceReferenceView get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<PriceReferenceView> create(@Valid @RequestBody PriceReferenceInput input,
                                                     Authentication authentication) {
        PriceReferenceView created = service.create(input, principal(authentication));
        return ResponseEntity.created(URI.create("/api/v1/sales/price-references/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PriceReferenceView update(@PathVariable long id, @Valid @RequestBody PriceReferenceInput input,
                                     Authentication authentication) {
        return service.update(id, input, principal(authentication));
    }

    private static WorkerPrincipal principal(Authentication authentication) {
        return (WorkerPrincipal) authentication.getPrincipal();
    }
}

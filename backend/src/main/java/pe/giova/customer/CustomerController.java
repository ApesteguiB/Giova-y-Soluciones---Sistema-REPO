package pe.giova.customer;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.customer.api.CustomerDtos.CustomerInput;
import pe.giova.customer.api.CustomerDtos.CustomerPage;
import pe.giova.customer.api.CustomerDtos.CustomerStatusInput;
import pe.giova.customer.api.CustomerDtos.CustomerUpdate;
import pe.giova.customer.api.CustomerDtos.CustomerView;

@RestController
@RequestMapping("/api/v1/sales/customers")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class CustomerController {
    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public CustomerPage list(@RequestParam(required = false) String q,
                             @RequestParam(required = false) Boolean active,
                             @RequestParam(required = false) String owner,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size,
                             Authentication authentication) {
        return service.find(q, active, owner, page, size, principal(authentication));
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public CustomerView get(@PathVariable String publicId, Authentication authentication) {
        return service.get(publicId, principal(authentication));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<CustomerView> create(@Valid @RequestBody CustomerInput input,
                                               Authentication authentication) {
        CustomerView created = service.create(input, principal(authentication));
        return ResponseEntity.created(URI.create("/api/v1/sales/customers/" + created.publicId())).body(created);
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public CustomerView update(@PathVariable String publicId, @Valid @RequestBody CustomerUpdate input,
                               Authentication authentication) {
        return service.update(publicId, input, principal(authentication));
    }

    @PatchMapping("/{publicId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerView status(@PathVariable String publicId, @Valid @RequestBody CustomerStatusInput input,
                               Authentication authentication) {
        return service.setActive(publicId, input.active(), input.version(), principal(authentication));
    }

    private static WorkerPrincipal principal(Authentication authentication) {
        return (WorkerPrincipal) authentication.getPrincipal();
    }
}

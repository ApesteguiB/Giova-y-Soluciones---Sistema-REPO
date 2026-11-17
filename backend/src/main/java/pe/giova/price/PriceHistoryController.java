package pe.giova.price;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.giova.price.api.PriceHistoryDtos.ProductPriceHistory;

/**
 * Historial comercial de un producto, para consultarlo mientras se cotiza.
 *
 * <p>Lo usa el vendedor, y lo ven también administración y contabilidad para
 * revisar por qué se perdió un proceso.
 */
@RestController
@RequestMapping("/api/v1/sales/price-history")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class PriceHistoryController {
    private final PriceHistoryService service;

    public PriceHistoryController(PriceHistoryService service) {
        this.service = service;
    }

    @GetMapping("/products/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public ProductPriceHistory byProduct(@PathVariable long productId) {
        return service.byProductId(productId);
    }

    /** Para líneas escritas a mano, donde solo se tiene el código. */
    @GetMapping("/by-sku")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public ProductPriceHistory bySku(@RequestParam String sku) {
        return service.bySku(sku);
    }
}

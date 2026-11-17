package pe.giova.catalog;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.giova.catalog.api.CatalogDtos.Brand;
import pe.giova.catalog.api.CatalogDtos.Category;
import pe.giova.catalog.api.CatalogDtos.Page;
import pe.giova.catalog.api.CatalogDtos.Product;

@RestController
@RequestMapping("/api/v1/catalog")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class CatalogController {
    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/products")
    public Page<Product> products(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) String category,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "60") int size) {
        return service.products(q, category, page, size);
    }

    @GetMapping("/categories")
    public List<Category> categories() {
        return service.categories();
    }

    @GetMapping("/brands")
    public List<Brand> brands(@RequestParam(defaultValue = "false") boolean featured) {
        return service.brands(featured);
    }
}

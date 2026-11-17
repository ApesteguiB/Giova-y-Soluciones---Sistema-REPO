package pe.giova.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.giova.admin.api.AdminDtos.AdminPage;
import pe.giova.admin.api.AdminDtos.ProductInput;
import pe.giova.admin.api.AdminDtos.ProductUpdate;
import pe.giova.admin.api.AdminDtos.ProductView;

@Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class ProductService {
    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public AdminPage<ProductView> find(String query, Boolean active, Long brandId, Long categoryId, int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page debe ser mayor o igual que 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size debe estar entre 1 y 100");
        if (brandId != null && brandId < 1 || categoryId != null && categoryId < 1) {
            throw new IllegalArgumentException("Los filtros de clasificador deben ser positivos");
        }
        return repository.find(normalizeQuery(query), active, brandId, categoryId, page, size);
    }

    public ProductView get(long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Producto no encontrado"));
    }

    @Transactional
    public ProductView create(ProductInput input) {
        repository.lockAndValidateParents(input.brandId(), input.categoryId());
        return get(repository.insert(input));
    }

    @Transactional
    public ProductView update(long id, ProductUpdate input) {
        ProductView current = get(id);
        repository.lockAndValidateParents(input.brandId(), input.categoryId());
        if (!current.slug().matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalStateException("El slug almacenado no es válido");
        }
        int updated = repository.update(id, input);
        if (updated == 0) {
            throw new ConflictException("El producto fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        return get(id);
    }

    @Transactional
    public ProductView setActive(long id, boolean active, long version) {
        ProductView current = get(id);
        if (active) repository.lockAndValidateParents(current.brandId(), current.categoryId());
        int updated = repository.setActive(id, active, version);
        if (updated == 0) throw new ConflictException("El producto fue modificado por otro usuario", "VERSION_CONFLICT");
        return get(id);
    }

    private static String normalizeQuery(String query) {
        if (query == null || query.isBlank()) return null;
        String value = query.trim();
        if (value.length() > 60) throw new IllegalArgumentException("q supera el máximo de 60 caracteres");
        return value;
    }
}

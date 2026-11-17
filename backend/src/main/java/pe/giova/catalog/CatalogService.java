package pe.giova.catalog;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pe.giova.catalog.api.CatalogDtos.Brand;
import pe.giova.catalog.api.CatalogDtos.Category;
import pe.giova.catalog.api.CatalogDtos.Page;
import pe.giova.catalog.api.CatalogDtos.Product;

@Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class CatalogService {
    private final CatalogRepository repository;

    public CatalogService(CatalogRepository repository) {
        this.repository = repository;
    }

    public Page<Product> products(String query, String category, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page debe ser mayor o igual que 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size debe estar entre 1 y 100");
        }
        String normalizedQuery = normalize(query, 60, "q");
        String normalizedCategory = normalize(category, 80, "category");
        if ("all".equals(normalizedCategory)) {
            normalizedCategory = null;
        }
        String pattern = normalizedQuery == null ? null : "%" + escapeLike(normalizedQuery.toLowerCase(Locale.ROOT)) + "%";
        return repository.findProducts(pattern, normalizedCategory, page, size);
    }

    public List<Category> categories() {
        return repository.findCategories();
    }

    public List<Brand> brands(boolean featured) {
        return repository.findBrands(featured);
    }

    private static String normalize(String value, int max, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new IllegalArgumentException(field + " supera el máximo de " + max + " caracteres");
        }
        return normalized;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

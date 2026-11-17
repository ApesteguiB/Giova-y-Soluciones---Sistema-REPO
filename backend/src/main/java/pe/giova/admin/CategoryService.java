package pe.giova.admin;

import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.giova.admin.api.AdminDtos.CategoryInput;
import pe.giova.admin.api.AdminDtos.CategoryUpdate;
import pe.giova.admin.api.AdminDtos.CategoryView;

@Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class CategoryService {
    private static final Set<String> ICON_KEYS = Set.of("toner-hp", "toner-kyocera", "equipos", "impresoras",
            "repuestos", "redes", "seguridad", "servicios");
    private final CategoryRepository repository;

    public CategoryService(CategoryRepository repository) {
        this.repository = repository;
    }

    public List<CategoryView> find(String query, Boolean active) {
        return repository.find(normalizeQuery(query), active);
    }

    public CategoryView get(long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Categoría no encontrada"));
    }

    @Transactional
    public CategoryView create(CategoryInput input) {
        validateIcon(input.iconKey());
        return get(repository.insert(input));
    }

    @Transactional
    public CategoryView update(long id, CategoryUpdate input) {
        validateIcon(input.iconKey());
        int updated = repository.update(id, input);
        if (updated == 0) {
            if (repository.findById(id).isEmpty()) throw new NotFoundException("Categoría no encontrada");
            throw new ConflictException("La categoría fue modificada por otro usuario", "VERSION_CONFLICT");
        }
        return get(id);
    }

    @Transactional
    public CategoryView setActive(long id, boolean active, long version) {
        int updated = repository.setActive(id, active, version);
        if (updated == 0) {
            if (repository.findById(id).isEmpty()) throw new NotFoundException("Categoría no encontrada");
            throw new ConflictException("La categoría fue modificada por otro usuario", "VERSION_CONFLICT");
        }
        return get(id);
    }

    private static String normalizeQuery(String query) {
        if (query == null || query.isBlank()) return null;
        String value = query.trim();
        if (value.length() > 60) throw new IllegalArgumentException("q supera el máximo de 60 caracteres");
        return value;
    }

    private static void validateIcon(String iconKey) {
        if (!ICON_KEYS.contains(iconKey)) throw new IllegalArgumentException("iconKey no permitido");
    }
}

package pe.giova.admin;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.giova.admin.api.AdminDtos.BrandInput;
import pe.giova.admin.api.AdminDtos.BrandUpdate;
import pe.giova.admin.api.AdminDtos.BrandView;

@Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class BrandService {
    private final BrandRepository repository;

    public BrandService(BrandRepository repository) {
        this.repository = repository;
    }

    public List<BrandView> find(String query, Boolean active) {
        return repository.find(normalizeQuery(query), active);
    }

    public BrandView get(long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Marca no encontrada"));
    }

    @Transactional
    public BrandView create(BrandInput input) {
        return get(repository.insert(input));
    }

    @Transactional
    public BrandView update(long id, BrandUpdate input) {
        int updated = repository.update(id, input);
        if (updated == 0) {
            if (repository.findById(id).isEmpty()) throw new NotFoundException("Marca no encontrada");
            throw new ConflictException("La marca fue modificada por otro usuario", "VERSION_CONFLICT");
        }
        return get(id);
    }

    @Transactional
    public BrandView setActive(long id, boolean active, long version) {
        int updated = repository.setActive(id, active, version);
        if (updated == 0) {
            if (repository.findById(id).isEmpty()) throw new NotFoundException("Marca no encontrada");
            throw new ConflictException("La marca fue modificada por otro usuario", "VERSION_CONFLICT");
        }
        return get(id);
    }

    private static String normalizeQuery(String query) {
        if (query == null || query.isBlank()) return null;
        String value = query.trim();
        if (value.length() > 60) throw new IllegalArgumentException("q supera el máximo de 60 caracteres");
        return value;
    }
}

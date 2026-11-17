package pe.giova.price;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.giova.admin.NotFoundException;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.price.api.PriceReferenceDtos.PriceReferenceInput;
import pe.giova.price.api.PriceReferenceDtos.PriceReferencePage;
import pe.giova.price.api.PriceReferenceDtos.PriceReferenceView;

@Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class PriceReferenceService {
    private static final BigDecimal ONE = BigDecimal.ONE;
    private final PriceReferenceRepository repository;

    public PriceReferenceService(PriceReferenceRepository repository) {
        this.repository = repository;
    }

    public PriceReferencePage find(Long productId, Instant from, Instant to, int page, int size) {
        validatePage(page, size);
        if (productId != null && productId < 1) throw new IllegalArgumentException("productId debe ser positivo");
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from no puede ser posterior a to");
        }
        return repository.find(productId, from, to, page, size);
    }

    public PriceReferenceView get(long id) {
        if (id < 1) throw new NotFoundException("Referencia de precio no encontrada");
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Referencia de precio no encontrada"));
    }

    @Transactional
    public PriceReferenceView create(PriceReferenceInput input, WorkerPrincipal actor) {
        PriceReferenceInput normalized = normalize(input);
        validateProduct(normalized.productId());
        return get(repository.insert(actor.id(), normalized));
    }

    @Transactional
    public PriceReferenceView update(long id, PriceReferenceInput input, WorkerPrincipal actor) {
        requireAdmin(actor);
        PriceReferenceInput normalized = normalize(input);
        validateProduct(normalized.productId());
        if (repository.update(id, normalized) == 0) {
            throw new NotFoundException("Referencia de precio no encontrada");
        }
        return get(id);
    }

    private void validateProduct(long productId) {
        if (!repository.productExists(productId)) throw new NotFoundException("Producto no encontrado");
    }

    private static PriceReferenceInput normalize(PriceReferenceInput input) {
        if (input == null) throw new IllegalArgumentException("La referencia de precio es obligatoria");
        if (input.productId() == null || input.productId() < 1) {
            throw new IllegalArgumentException("productId debe ser positivo");
        }
        String currency = optional(input.currencyCode());
        if (currency == null) throw new IllegalArgumentException("currencyCode es obligatorio");
        currency = currency.toUpperCase(Locale.ROOT);
        if (!currency.equals("PEN") && !currency.equals("USD")) {
            throw new IllegalArgumentException("currencyCode debe ser PEN o USD");
        }
        if (input.unitPrice() == null || input.unitPrice().signum() <= 0) {
            throw new IllegalArgumentException("unitPrice debe ser mayor que 0");
        }
        if (input.taxRate() == null || input.taxRate().signum() < 0 || input.taxRate().compareTo(ONE) > 0) {
            throw new IllegalArgumentException("taxRate debe estar entre 0 y 1");
        }
        BigDecimal exchange = input.exchangeRateToPen();
        if (currency.equals("PEN") && exchange != null) {
            throw new IllegalArgumentException("exchangeRateToPen debe ser nulo para PEN");
        }
        if (currency.equals("USD") && (exchange == null || exchange.signum() <= 0)) {
            throw new IllegalArgumentException("exchangeRateToPen es obligatorio y positivo para USD");
        }
        if (input.effectiveAt() == null) throw new IllegalArgumentException("effectiveAt es obligatorio");
        return new PriceReferenceInput(input.productId(), optional(input.supplierName()),
                optional(input.sourceReference()), currency, input.unitPrice(), input.includesTax(), input.taxRate(),
                exchange, input.effectiveAt(), optional(input.notes()));
    }

    private static String optional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void validatePage(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page debe ser mayor o igual que 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size debe estar entre 1 y 100");
    }

    private static void requireAdmin(WorkerPrincipal actor) {
        if (actor.roleCode() != RoleCode.ADMIN) throw new SecurityException("Se requiere rol ADMIN");
    }
}

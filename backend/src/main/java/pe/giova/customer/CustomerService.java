package pe.giova.customer;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.giova.admin.ConflictException;
import pe.giova.admin.NotFoundException;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.customer.api.CustomerDtos.CustomerInput;
import pe.giova.customer.api.CustomerDtos.CustomerPage;
import pe.giova.customer.api.CustomerDtos.CustomerUpdate;
import pe.giova.customer.api.CustomerDtos.CustomerView;

@Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class CustomerService {
    private static final Pattern DNI = Pattern.compile("[0-9]{8}");
    private static final Pattern RUC = Pattern.compile("[0-9]{11}");
    private static final Pattern OTHER_DOCUMENT = Pattern.compile("[A-Za-z0-9-]{3,20}");
    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public CustomerPage find(String query, Boolean active, String owner, int page, int size, WorkerPrincipal actor) {
        validatePage(page, size);
        Long ownerId = ownerFilter(owner, actor);
        return repository.find(normalizeQuery(query), active, ownerId, page, size);
    }

    public CustomerView get(String publicId, WorkerPrincipal actor) {
        if (publicId == null || publicId.isBlank()) throw new NotFoundException("Cliente no encontrado");
        Long ownerId = actor.roleCode() == RoleCode.SELLER ? actor.id() : null;
        return repository.findByPublicId(publicId, ownerId)
                .orElseThrow(() -> new NotFoundException("Cliente no encontrado"));
    }

    @Transactional
    public CustomerView create(CustomerInput input, WorkerPrincipal actor) {
        CustomerInput normalized = normalize(input);
        if (repository.existsDocument(normalized.documentType(), normalized.documentNumber(), null)) {
            throw duplicateDocument();
        }
        String publicId = repository.insert(actor.id(), normalized);
        return get(publicId, actor);
    }

    @Transactional
    public CustomerView update(String publicId, CustomerUpdate input, WorkerPrincipal actor) {
        CustomerUpdate normalized = normalize(input);
        if (repository.existsDocument(normalized.documentType(), normalized.documentNumber(), publicId)) {
            throw duplicateDocument();
        }
        Long ownerId = actor.roleCode() == RoleCode.SELLER ? actor.id() : null;
        int updated = repository.update(publicId, ownerId, normalized);
        if (updated == 0) {
            if (repository.findByPublicId(publicId, ownerId).isEmpty()) {
                throw new NotFoundException("Cliente no encontrado");
            }
            throw new ConflictException("El cliente fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        return get(publicId, actor);
    }

    @Transactional
    public CustomerView setActive(String publicId, boolean active, long version, WorkerPrincipal actor) {
        requireAdmin(actor);
        int updated = repository.setActive(publicId, active, version);
        if (updated == 0) {
            if (repository.findByPublicId(publicId, null).isEmpty()) {
                throw new NotFoundException("Cliente no encontrado");
            }
            throw new ConflictException("El cliente fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        return get(publicId, actor);
    }

    private static Long ownerFilter(String owner, WorkerPrincipal actor) {
        String normalized = owner == null || owner.isBlank() ? "all" : owner.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("me") && !normalized.equals("all")) {
            throw new IllegalArgumentException("owner debe ser me o all");
        }
        if (actor.roleCode() == RoleCode.SELLER || normalized.equals("me")) return actor.id();
        return null;
    }

    private static CustomerInput normalize(CustomerInput input) {
        if (input == null) throw new IllegalArgumentException("El cliente es obligatorio");
        String customerType = upper(input.customerType(), "customerType");
        if (!customerType.equals("PERSON") && !customerType.equals("COMPANY")) {
            throw new IllegalArgumentException("customerType debe ser PERSON o COMPANY");
        }
        String documentType = upper(input.documentType(), "documentType");
        String documentNumber = required(input.documentNumber(), "documentNumber");
        validateDocument(documentType, documentNumber);
        return new CustomerInput(customerType, documentType, documentNumber,
                required(input.businessName(), "businessName"), optional(input.contactName()),
                normalizeEmail(input.email()), optional(input.phone()), optional(input.address()), optional(input.notes()));
    }

    private static CustomerUpdate normalize(CustomerUpdate input) {
        if (input == null) throw new IllegalArgumentException("El cliente es obligatorio");
        CustomerInput normalized = normalize(new CustomerInput(input.customerType(), input.documentType(),
                input.documentNumber(), input.businessName(), input.contactName(), input.email(), input.phone(),
                input.address(), input.notes()));
        return new CustomerUpdate(normalized.customerType(), normalized.documentType(), normalized.documentNumber(),
                normalized.businessName(), normalized.contactName(), normalized.email(), normalized.phone(),
                normalized.address(), normalized.notes(), input.version());
    }

    private static void validateDocument(String documentType, String documentNumber) {
        boolean valid = switch (documentType) {
            case "DNI" -> DNI.matcher(documentNumber).matches();
            case "RUC" -> RUC.matcher(documentNumber).matches();
            case "CE", "OTHER" -> OTHER_DOCUMENT.matcher(documentNumber).matches();
            default -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException("documentNumber no coincide con documentType");
        }
    }

    private static String normalizeEmail(String value) {
        String email = optional(value);
        return email == null ? null : email.toLowerCase(Locale.ROOT);
    }

    private static String required(String value, String field) {
        String normalized = optional(value);
        if (normalized == null) throw new IllegalArgumentException(field + " es obligatorio");
        return normalized;
    }

    private static String optional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String upper(String value, String field) {
        String normalized = required(value, field);
        return normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalizeQuery(String query) {
        String normalized = optional(query);
        if (normalized != null && normalized.length() > 60) {
            throw new IllegalArgumentException("q supera el máximo de 60 caracteres");
        }
        return normalized;
    }

    private static void validatePage(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page debe ser mayor o igual que 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size debe estar entre 1 y 100");
    }

    private static ConflictException duplicateDocument() {
        return new ConflictException("Ya existe un cliente con ese documento", "CUSTOMER_DOCUMENT_EXISTS");
    }

    private static void requireAdmin(WorkerPrincipal actor) {
        if (actor.roleCode() != RoleCode.ADMIN) throw new SecurityException("Se requiere rol ADMIN");
    }
}

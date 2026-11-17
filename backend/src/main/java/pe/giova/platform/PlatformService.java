package pe.giova.platform;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.giova.admin.ConflictException;
import pe.giova.admin.NotFoundException;
import pe.giova.audit.AuditService;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.document.TenantLogoStorage;

@Service
public class PlatformService {
    private final PlatformRepository repository;
    private final TenantLogoStorage logoStorage;
    private final AuditService audit;

    public PlatformService(PlatformRepository repository, TenantLogoStorage logoStorage, AuditService audit) {
        this.repository = repository;
        this.logoStorage = logoStorage;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PlatformDtos.TenantView> listTenants() {
        return repository.findAllTenants();
    }

    @Transactional
    public PlatformDtos.TenantCreation createTenant(PlatformDtos.TenantInput input, WorkerPrincipal actor) {
        PlatformRepository.TenantData data = data(input);
        var current = repository.findTenantByCode(data.code());
        if (current.isPresent()) {
            if (!matches(current.get(), data)) {
                throw new ConflictException("El código ya pertenece a una empresa con otros datos",
                        "TENANT_CODE_CONFLICT");
            }
            audit.success(actor, "PLATFORM_TENANT_CREATE_REPLAYED", "TENANT", Long.toString(current.get().id()),
                    "Reintento idempotente para la empresa " + data.code());
            return new PlatformDtos.TenantCreation(current.get(), false);
        }
        try {
            PlatformDtos.TenantView created = repository.createTenant(data);
            audit.success(actor, "PLATFORM_TENANT_CREATED", "TENANT", Long.toString(created.id()),
                    "Empresa creada: " + created.code());
            return new PlatformDtos.TenantCreation(created, true);
        } catch (DuplicateKeyException exception) {
            PlatformDtos.TenantView raced = repository.findTenantByCode(data.code()).orElse(null);
            if (raced != null && matches(raced, data)) {
                audit.success(actor, "PLATFORM_TENANT_CREATE_REPLAYED", "TENANT", Long.toString(raced.id()),
                        "Reintento idempotente para la empresa " + data.code());
                return new PlatformDtos.TenantCreation(raced, false);
            }
            throw new ConflictException("El código o slug ya está registrado", "TENANT_EXISTS");
        }
    }

    @Transactional
    public PlatformDtos.TenantView updateTenant(long id, PlatformDtos.TenantUpdate input, WorkerPrincipal actor) {
        PlatformDtos.TenantView current = requireTenant(id);
        PlatformRepository.TenantData data = data(current.code(), current.slug(), input.legalName(),
                input.displayName(), input.taxId(), input.address(), input.contactEmail(), input.defaultCurrency(),
                input.taxRate(), input.primaryColor(), input.secondaryColor(), input.accentColor(),
                input.pdfHeaderText());
        if (!repository.updateTenant(id, data, input.version())) {
            throw new ConflictException("La empresa cambió. Actualice la lista y vuelva a intentar.",
                    "TENANT_VERSION_CONFLICT");
        }
        PlatformDtos.TenantView updated = requireTenant(id);
        audit.success(actor, "PLATFORM_TENANT_UPDATED", "TENANT", Long.toString(id),
                "Empresa actualizada: " + updated.code());
        return updated;
    }

    @Transactional
    public PlatformDtos.TenantView setTenantActive(long id, PlatformDtos.TenantStatusInput input,
                                                    WorkerPrincipal actor) {
        PlatformDtos.TenantView current = requireTenant(id);
        if (!repository.setTenantActive(id, input.active(), input.version())) {
            throw new ConflictException("La empresa cambió. Actualice la lista y vuelva a intentar.",
                    "TENANT_VERSION_CONFLICT");
        }
        PlatformDtos.TenantView updated = requireTenant(id);
        audit.success(actor, input.active() ? "PLATFORM_TENANT_ENABLED" : "PLATFORM_TENANT_DISABLED",
                "TENANT", Long.toString(id), "Empresa " + current.code());
        return updated;
    }

    @Transactional
    public PlatformDtos.TenantView storeLogo(long id, byte[] upload, WorkerPrincipal actor) {
        PlatformDtos.TenantView tenant = requireTenant(id);
        String newKey = logoStorage.store(id, upload);
        if (!repository.setLogoKey(id, newKey)) {
            throw new NotFoundException("Empresa no encontrada");
        }
        audit.success(actor, "PLATFORM_TENANT_LOGO_UPDATED", "TENANT", Long.toString(id),
                "Logo actualizado para la empresa " + tenant.code());
        return requireTenant(id);
    }

    @Transactional(readOnly = true)
    public TenantLogoStorage.Logo readLogo(long id) {
        requireTenant(id);
        String key = repository.findLogoKey(id).orElseThrow(() -> new NotFoundException("Logo no encontrado"));
        return logoStorage.read(id, key);
    }

    private PlatformDtos.TenantView requireTenant(long id) {
        return repository.findTenant(id).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
    }

    private static PlatformRepository.TenantData data(PlatformDtos.TenantInput input) {
        return data(input.code(), input.slug(), input.legalName(), input.displayName(), input.taxId(),
                input.address(), input.contactEmail(), input.defaultCurrency(), input.taxRate(),
                input.primaryColor(), input.secondaryColor(), input.accentColor(), input.pdfHeaderText());
    }

    private static PlatformRepository.TenantData data(String code, String slug, String legalName,
            String displayName, String taxId, String address, String email, String currency, BigDecimal taxRate,
            String primaryColor, String secondaryColor, String accentColor, String pdfHeaderText) {
        return new PlatformRepository.TenantData(code.trim().toUpperCase(Locale.ROOT),
                slug.trim().toLowerCase(Locale.ROOT), legalName.trim(), displayName.trim(), blankToNull(taxId),
                blankToNull(address), blankToNull(email) == null ? null : email.trim().toLowerCase(Locale.ROOT),
                currency.trim().toUpperCase(Locale.ROOT), taxRate, normalizeColor(primaryColor),
                normalizeColor(secondaryColor), normalizeColor(accentColor), blankToNull(pdfHeaderText));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeColor(String color) {
        return color.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean matches(PlatformDtos.TenantView tenant, PlatformRepository.TenantData data) {
        return tenant.code().equals(data.code()) && tenant.slug().equals(data.slug())
                && tenant.legalName().equals(data.legalName()) && tenant.displayName().equals(data.displayName())
                && equalsNullable(tenant.taxId(), data.taxId()) && equalsNullable(tenant.address(), data.address())
                && equalsNullable(tenant.contactEmail(), data.contactEmail())
                && tenant.defaultCurrency().equals(data.defaultCurrency())
                && tenant.taxRate().compareTo(data.taxRate()) == 0
                && tenant.primaryColor().equalsIgnoreCase(data.primaryColor())
                && tenant.secondaryColor().equalsIgnoreCase(data.secondaryColor())
                && tenant.accentColor().equalsIgnoreCase(data.accentColor())
                && equalsNullable(tenant.pdfHeaderText(), data.pdfHeaderText());
    }

    private static boolean equalsNullable(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }
}

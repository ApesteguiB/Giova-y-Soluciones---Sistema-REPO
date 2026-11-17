package pe.giova.tenant;

import java.awt.Color;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pe.giova.admin.NotFoundException;
import pe.giova.auth.TenantScope;
import pe.giova.document.TenantLogoStorage;
import pe.giova.tenant.TenantBrandingRepository.Branding;

@Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class TenantBrandingService {
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private final TenantBrandingRepository repository;
    private final TenantLogoStorage logoStorage;

    public TenantBrandingService(TenantBrandingRepository repository, TenantLogoStorage logoStorage) {
        this.repository = repository;
        this.logoStorage = logoStorage;
    }

    public TenantBrandingDtos.Response current() {
        Branding branding = currentBranding();
        validate(branding);
        return new TenantBrandingDtos.Response(branding.tenantId(), branding.displayName(), branding.primaryColor(),
                branding.secondaryColor(), branding.accentColor(), branding.pdfHeaderText(),
                hasConfiguredLogo(branding));
    }

    public TenantLogoStorage.Logo currentLogo() {
        Branding branding = currentBranding();
        validate(branding);
        if (!hasConfiguredLogo(branding)) {
            throw new NotFoundException("Logo no encontrado");
        }
        return logoStorage.read(branding.tenantId(), branding.logoStorageKey());
    }

    public PdfBranding currentForPdf() {
        Branding branding = currentBranding();
        validate(branding);
        byte[] logo = !hasConfiguredLogo(branding) ? null
                : logoStorage.read(branding.tenantId(), branding.logoStorageKey()).bytes();
        return new PdfBranding(branding.displayName(), branding.primaryColor(), branding.secondaryColor(),
                branding.accentColor(), branding.pdfHeaderText(), logo);
    }

    private Branding currentBranding() {
        long tenantId = TenantScope.requireTenantId();
        return repository.findActive(tenantId).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
    }

    private static void validate(Branding branding) {
        requireColor("primaryColor", branding.primaryColor());
        requireColor("secondaryColor", branding.secondaryColor());
        requireColor("accentColor", branding.accentColor());
        if (branding.displayName() == null || branding.displayName().isBlank()) {
            throw new TenantBrandingValidationException("displayName es obligatorio");
        }
    }

    public static Color parseColor(String field, String value) {
        if (value == null || !HEX_COLOR.matcher(value).matches()) {
            throw new TenantBrandingValidationException(field + " debe usar el formato #RRGGBB");
        }
        return Color.decode(value);
    }

    private static void requireColor(String field, String value) {
        parseColor(field, value);
    }

    private static boolean hasConfiguredLogo(Branding branding) {
        return branding.logoStorageKey() != null && !branding.logoStorageKey().isBlank();
    }

    public record PdfBranding(String displayName, String primaryColor, String secondaryColor, String accentColor,
                              String pdfHeaderText, byte[] logoPng) {}
}

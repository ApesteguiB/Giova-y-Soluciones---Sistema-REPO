package pe.giova.site;

import pe.giova.admin.NotFoundException;
import pe.giova.document.TenantLogoStorage;
import pe.giova.site.PublicSiteDtos.Colors;
import pe.giova.site.PublicSiteDtos.Site;

public class PublicSiteService {
    private static final String DEFAULT_PRIMARY = "#0a2342";
    private static final String DEFAULT_SECONDARY = "#14a3a8";
    private static final String DEFAULT_ACCENT = "#2ec8dd";

    private final PublicSiteRepository repository;
    private final PublicSiteResolver resolver;
    private final TenantLogoStorage logoStorage;

    public PublicSiteService(PublicSiteRepository repository, PublicSiteResolver resolver,
                             TenantLogoStorage logoStorage) {
        this.repository = repository;
        this.resolver = resolver;
        this.logoStorage = logoStorage;
    }

    public Site current() {
        return toSite(record());
    }

    public TenantLogoStorage.Logo currentLogo() {
        PublicSiteRepository.SiteRecord site = record();
        if (site.logoStorageKey() == null || site.logoStorageKey().isBlank()) {
            throw new NotFoundException("La empresa no tiene logo cargado");
        }
        return logoStorage.read(site.tenantId(), site.logoStorageKey());
    }

    private PublicSiteRepository.SiteRecord record() {
        long tenantId = resolver.requireTenantId();
        return repository.findSite(tenantId).orElseThrow(() ->
                new NotFoundException("No hay un sitio público configurado para este dominio"));
    }

    static Site toSite(PublicSiteRepository.SiteRecord site) {
        Colors colors = new Colors(color(site.primaryColor(), DEFAULT_PRIMARY),
                color(site.secondaryColor(), DEFAULT_SECONDARY),
                color(site.accentColor(), DEFAULT_ACCENT));
        boolean uploadedLogo = site.logoStorageKey() != null && !site.logoStorageKey().isBlank();
        return new Site(site.code(), site.templateKey(), site.siteTitle(), site.brandName(),
                site.legalName(), site.taxId(), site.tagline(), site.logoAsset(), uploadedLogo,
                site.monogramText(), site.phone(), site.whatsappNumber(), site.whatsappMessage(),
                site.email(), site.address(), site.mapsUrl(), site.hours(), site.footerNote(),
                site.facebookUrl(), site.instagramUrl(), site.linkedinUrl(),
                site.claimsEnabled(), site.catalogEnabled(), colors);
    }

    /** Un color inválido en base nunca debe romper la portada: se usa el de la plantilla. */
    private static String color(String value, String fallback) {
        return value != null && value.matches("#[0-9A-Fa-f]{6}") ? value : fallback;
    }
}

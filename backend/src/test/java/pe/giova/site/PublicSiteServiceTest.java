package pe.giova.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.giova.admin.NotFoundException;
import pe.giova.document.TenantLogoStorage;

class PublicSiteServiceTest {
    private final PublicSiteRepository repository = mock(PublicSiteRepository.class);
    private final PublicSiteResolver resolver = mock(PublicSiteResolver.class);
    private final TenantLogoStorage logoStorage = mock(TenantLogoStorage.class);
    private final PublicSiteService service = new PublicSiteService(repository, resolver, logoStorage);

    @Test
    void exposesTheSiteOfTheResolvedCompanyWithoutLeakingTheStorageKey() {
        when(resolver.requireTenantId()).thenReturn(2L);
        when(repository.findSite(2L)).thenReturn(Optional.of(record("#f26b21", "tenants/2/branding/logo-x.png")));

        PublicSiteDtos.Site site = service.current();

        assertEquals("SOLUCIONES_TI", site.tenantCode());
        assertEquals("#f26b21", site.colors().primary());
        assertTrue(site.uploadedLogo());
        assertFalse(site.toString().contains("logo-x.png"));
    }

    @Test
    void anInvalidStoredColourFallsBackToTheTemplateColourInsteadOfBreakingTheSite() {
        when(resolver.requireTenantId()).thenReturn(1L);
        when(repository.findSite(1L)).thenReturn(Optional.of(record("naranja", null)));

        PublicSiteDtos.Site site = service.current();

        assertEquals("#0a2342", site.colors().primary());
        assertFalse(site.uploadedLogo());
    }

    @Test
    void missingSiteOrMissingLogoAreNotFound() {
        when(resolver.requireTenantId()).thenReturn(9L);
        when(repository.findSite(9L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, service::current);

        when(repository.findSite(9L)).thenReturn(Optional.of(record("#0a2342", null)));
        assertThrows(NotFoundException.class, service::currentLogo);
    }

    private static PublicSiteRepository.SiteRecord record(String primaryColor, String logoStorageKey) {
        return new PublicSiteRepository.SiteRecord(2L, "SOLUCIONES_TI", null, "SOLUCIONES",
                "Soluciones Informática TI", "Soluciones Informática TI",
                "SOLUCIONES INFORMATICA TI E.I.R.L.", "Infraestructura y soporte",
                "assets/soluciones-informaticas-ti.svg", "TI", null, null, "Hola", null, null, null,
                "Lunes a viernes", "Infraestructura", null, null, null, true, true,
                primaryColor, "#1f2937", "#f6b847", logoStorageKey);
    }
}

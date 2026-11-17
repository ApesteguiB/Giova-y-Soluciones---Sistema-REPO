package pe.giova.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.giova.admin.NotFoundException;

class PublicSiteResolverTest {
    private final PublicSiteRepository repository = mock(PublicSiteRepository.class);

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void resolvesTheCompanyFromTheRequestHostAndIgnoresThePort() {
        when(repository.findTenantIdByHostname("solucionesti.pe")).thenReturn(Optional.of(2L));
        request("solucionesti.pe", 8088, null);

        assertEquals(2L, resolver(true).requireTenantId());
    }

    @Test
    void fallsBackToTheBareDomainWhenOnlyTheApexIsRegistered() {
        when(repository.findTenantIdByHostname("www.giova.pe")).thenReturn(Optional.empty());
        when(repository.findTenantIdByHostname("giova.pe")).thenReturn(Optional.of(1L));
        request("www.giova.pe", 443, null);

        assertEquals(1L, resolver(true).requireTenantId());
    }

    @Test
    void honoursTheEmpresaOverrideOnlyFromLocalhost() {
        when(repository.findTenantIdByCodeOrSlug("SOLUCIONES_TI")).thenReturn(Optional.of(2L));
        when(repository.findTenantIdByHostname("giova.pe")).thenReturn(Optional.of(1L));

        request("localhost", 8088, "SOLUCIONES_TI");
        assertEquals(2L, resolver(true).requireTenantId());

        // El mismo parámetro desde un dominio real no cambia de empresa.
        request("giova.pe", 443, "SOLUCIONES_TI");
        assertEquals(1L, resolver(true).requireTenantId());
    }

    @Test
    void ignoresTheOverrideWhenItIsDisabledByConfiguration() {
        when(repository.findTenantIdByHostname("localhost")).thenReturn(Optional.of(1L));
        request("localhost", 8088, "SOLUCIONES_TI");

        assertEquals(1L, resolver(false).requireTenantId());
        verify(repository, never()).findTenantIdByCodeOrSlug(anyString());
    }

    @Test
    void unknownDomainsFallBackToTheConfiguredDefaultCompany() {
        when(repository.findTenantIdByHostname("otra-cosa.com")).thenReturn(Optional.empty());
        when(repository.findTenantIdByCodeOrSlug("GIOVA")).thenReturn(Optional.of(1L));
        request("otra-cosa.com", 80, null);

        assertEquals(1L, resolver(true).requireTenantId());
    }

    @Test
    void failsWithNotFoundWhenNeitherTheDomainNorTheDefaultExist() {
        when(repository.findTenantIdByHostname(anyString())).thenReturn(Optional.empty());
        when(repository.findTenantIdByCodeOrSlug(anyString())).thenReturn(Optional.empty());
        request("otra-cosa.com", 80, null);

        assertThrows(NotFoundException.class, () -> resolver(true).requireTenantId());
    }

    @Test
    void cachesTheDomainLookupInsteadOfQueryingOnEveryRequest() {
        when(repository.findTenantIdByHostname("giova.pe")).thenReturn(Optional.of(1L));
        PublicSiteResolver resolver = resolver(true);

        request("giova.pe", 443, null);
        resolver.requireTenantId();
        request("giova.pe", 443, null);
        resolver.requireTenantId();

        verify(repository, times(1)).findTenantIdByHostname("giova.pe");
    }

    @Test
    void normalizesCaseTrailingDotAndPort() {
        assertEquals("giova.pe", PublicSiteResolver.normalizeHost("GIOVA.PE."));
        assertEquals("giova.pe", PublicSiteResolver.normalizeHost("giova.pe:8088"));
        assertEquals("[::1]", PublicSiteResolver.normalizeHost("[::1]"));
        assertEquals("", PublicSiteResolver.normalizeHost(null));
    }

    private PublicSiteResolver resolver(boolean allowLocalOverride) {
        return new PublicSiteResolver(repository, "GIOVA", allowLocalOverride);
    }

    private static void request(String host, int port, String empresa) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/catalog/site");
        request.setServerName(host);
        request.setServerPort(port);
        if (empresa != null) {
            request.setParameter("empresa", empresa);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}

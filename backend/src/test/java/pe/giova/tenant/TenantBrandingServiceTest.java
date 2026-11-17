package pe.giova.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;
import pe.giova.document.TenantLogoStorage;
import pe.giova.tenant.TenantBrandingRepository.Branding;

class TenantBrandingServiceTest {
    private final TenantBrandingRepository repository = mock(TenantBrandingRepository.class);
    private final TenantLogoStorage logoStorage = mock(TenantLogoStorage.class);
    private final TenantBrandingService service = new TenantBrandingService(repository, logoStorage);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsBrandingOnlyForTheActiveTenantAndDoesNotExposeStorageKey() {
        authenticateAsTenant(2L);
        when(repository.findActive(2L)).thenReturn(Optional.of(branding(2L, "tenants/2/branding/logo-test.png")));

        TenantBrandingDtos.Response response = service.current();

        assertEquals(2L, response.tenantId());
        assertEquals("Soluciones Informáticas TI", response.displayName());
        assertEquals("#f26b21", response.primaryColor());
        assertEquals(7, response.getClass().getRecordComponents().length);
        verify(repository).findActive(2L);
    }

    @Test
    void rejectsInvalidColorAndNeverUsesRequestSuppliedTenantId() {
        authenticateAsTenant(2L);
        when(repository.findActive(2L)).thenReturn(Optional.of(new Branding(2L, "Empresa", "orange",
                "#ffffff", "#000000", null, null)));

        assertThrows(TenantBrandingValidationException.class, service::current);
        verify(repository).findActive(2L);
    }

    @Test
    void colorParserRejectsValuesOutsideHexRgb() {
        assertThrows(TenantBrandingValidationException.class,
                () -> TenantBrandingService.parseColor("primaryColor", "orange"));
        assertThrows(TenantBrandingValidationException.class,
                () -> TenantBrandingService.parseColor("primaryColor", "#12345"));
    }

    @Test
    void missingLogoReturnsNotFoundForTheActiveCompany() {
        authenticateAsTenant(2L);
        when(repository.findActive(2L)).thenReturn(Optional.of(branding(2L, null)));

        assertThrows(pe.giova.admin.NotFoundException.class, service::currentLogo);
        verify(repository).findActive(2L);
    }

    private static Branding branding(long tenantId, String key) {
        return new Branding(tenantId, "Soluciones Informáticas TI", "#f26b21", "#ffffff", "#ff9f1c", key,
                "Soluciones Informáticas TI");
    }

    private static void authenticateAsTenant(long tenantId) {
        WorkerRepository.WorkerRecord worker = new WorkerRepository.WorkerRecord(5L, "admin@example.test",
                "Administrador", "hash", true, null);
        WorkerRepository.Membership membership = new WorkerRepository.Membership(9L, tenantId,
                "SIT", "Soluciones Informáticas TI", RoleCode.ADMIN);
        WorkerPrincipal principal = new WorkerPrincipal(worker, java.util.List.of(membership), membership);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
    }
}

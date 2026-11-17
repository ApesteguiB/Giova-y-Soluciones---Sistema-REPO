package pe.giova.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.giova.audit.AuditService;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;
import pe.giova.auth.WorkerRepository.WorkerRecord;

class PlatformAccountServiceTest {
    private final PlatformRepository tenants = mock(PlatformRepository.class);
    private final WorkerRepository workers = mock(WorkerRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuditService audit = mock(AuditService.class);
    private final PlatformAccountService service = new PlatformAccountService(tenants, workers, encoder, audit);
    private final WorkerPrincipal actor = new WorkerPrincipal(new WorkerRecord(
            91L, "platform", "Plataforma", "hash", true, RoleCode.SUPERADMIN));

    @Test
    void createsOnlyAnAdminMembershipAndReturnsItsTemporaryPasswordOnce() {
        when(tenants.findTenant(12L)).thenReturn(Optional.of(tenant(true)));
        when(workers.findByUsername("admin@example.test")).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenReturn("encoded-temporary-password");
        when(workers.insertIdentity("admin@example.test", "Administradora", "encoded-temporary-password",
                null, true, actor.id())).thenReturn(44L);

        PlatformDtos.AdminProvisioned result = service.createOrLinkAdmin(12L,
                new PlatformDtos.AdminInput("Admin@Example.Test", "Administradora"), actor);

        assertTrue(result.created());
        assertNotNull(result.temporaryPassword());
        assertEquals(24, result.temporaryPassword().length());
        assertEquals("admin@example.test", result.admin().username());
        verify(workers).insertIdentity("admin@example.test", "Administradora", "encoded-temporary-password",
                null, true, actor.id());
        verify(workers).insertMembership(44L, 12L, RoleCode.ADMIN, actor.id());
        verify(audit).success(actor, "PLATFORM_ADMIN_CREATED", "TENANT_ADMIN", "44",
                "Cuenta ADMIN creada para la empresa NARANJA");
    }

    @Test
    void linksAnExistingIdentityWithoutChangingItsOtherMembershipsOrReturningPassword() {
        when(tenants.findTenant(12L)).thenReturn(Optional.of(tenant(true)));
        when(workers.findByUsername("shared@example.test")).thenReturn(Optional.of(new WorkerRecord(
                55L, "shared@example.test", "Persona", "hash", true, null)));
        when(workers.findMembership(55L, 12L)).thenReturn(Optional.empty());

        PlatformDtos.AdminProvisioned result = service.createOrLinkAdmin(12L,
                new PlatformDtos.AdminInput("shared@example.test", "Nombre enviado"), actor);

        assertTrue(!result.created());
        assertNull(result.temporaryPassword());
        verify(workers).insertMembership(55L, 12L, RoleCode.ADMIN, actor.id());
    }

    private static PlatformDtos.TenantView tenant(boolean active) {
        return new PlatformDtos.TenantView(12L, "uuid", "NARANJA", "empresa-naranja", "Razón social",
                "Empresa Naranja", null, null, null, "PEN", new BigDecimal("0.18"), active, 0,
                "#E87835", "#FFFFFF", "#F3B247", null, false, 0);
    }
}

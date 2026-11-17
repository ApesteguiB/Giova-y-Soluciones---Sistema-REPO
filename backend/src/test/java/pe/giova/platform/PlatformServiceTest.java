package pe.giova.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.giova.admin.ConflictException;
import pe.giova.audit.AuditService;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;
import pe.giova.document.TenantLogoStorage;

class PlatformServiceTest {
    private final PlatformRepository repository = mock(PlatformRepository.class);
    private final TenantLogoStorage logos = mock(TenantLogoStorage.class);
    private final AuditService audit = mock(AuditService.class);
    private final PlatformService service = new PlatformService(repository, logos, audit);
    private final WorkerPrincipal actor = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
            91L, "platform", "Plataforma", "hash", true, RoleCode.SUPERADMIN));

    @Test
    void createIsIdempotentForTheSameCodeAndData() {
        PlatformDtos.TenantInput input = tenantInput();
        PlatformDtos.TenantView existing = tenantView();
        when(repository.findTenantByCode("NARANJA")).thenReturn(Optional.of(existing));

        PlatformDtos.TenantCreation result = service.createTenant(input, actor);

        assertFalse(result.created());
        assertEquals(existing.id(), result.tenant().id());
        verify(repository).findTenantByCode("NARANJA");
        verify(repository, org.mockito.Mockito.never()).createTenant(any());
        verify(audit).success(actor, "PLATFORM_TENANT_CREATE_REPLAYED", "TENANT", "12",
                "Reintento idempotente para la empresa NARANJA");
    }

    @Test
    void sameCodeWithDifferentPayloadIsAConflict() {
        PlatformDtos.TenantInput input = tenantInput();
        PlatformDtos.TenantView existing = tenantView();
        when(repository.findTenantByCode("NARANJA")).thenReturn(Optional.of(existing));

        PlatformDtos.TenantInput changed = new PlatformDtos.TenantInput(input.code(), input.slug(),
                input.legalName(), "Otro nombre", input.taxId(), input.address(), input.contactEmail(),
                input.defaultCurrency(), input.taxRate(), input.primaryColor(), input.secondaryColor(),
                input.accentColor(), input.pdfHeaderText());
        assertThrows(ConflictException.class, () -> service.createTenant(changed, actor));
    }

    private static PlatformDtos.TenantInput tenantInput() {
        return new PlatformDtos.TenantInput("NARANJA", "empresa-naranja", "Empresa Naranja S.A.C.",
                "Empresa Naranja", "", null, "contacto@example.test", "PEN", new BigDecimal("0.18"),
                "#e87835", "#ffffff", "#f3b247", null);
    }

    private static PlatformDtos.TenantView tenantView() {
        return new PlatformDtos.TenantView(12L, "uuid", "NARANJA", "empresa-naranja",
                "Empresa Naranja S.A.C.", "Empresa Naranja", null, null, "contacto@example.test", "PEN",
                new BigDecimal("0.180000"), true, 0, "#E87835", "#FFFFFF", "#F3B247", null, false, 0);
    }
}

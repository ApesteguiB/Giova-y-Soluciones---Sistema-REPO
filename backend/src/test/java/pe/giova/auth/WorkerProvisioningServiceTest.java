package pe.giova.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;

class WorkerProvisioningServiceTest {
    @Test
    void createsEmailWorkerWithExplicitRole() {
        WorkerRepository repository = mock(WorkerRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("a-safe-password")).thenReturn("hash");
        when(repository.findActiveTenantId("GIOVA")).thenReturn(Optional.of(7L));
        when(repository.insertIdentity(eq("vendedor@gmail.com"), eq("Vendedor"), eq("hash"), isNull(),
                eq(false), isNull())).thenReturn(11L);

        long id = new WorkerProvisioningService(repository, encoder)
                .create("Vendedor@gmail.com", "Vendedor", "a-safe-password", "SELLER", "giova", false);

        assertEquals(11L, id);
        verify(repository).insertMembership(11L, 7L, RoleCode.SELLER, null);
        verify(repository).insertIdentity("vendedor@gmail.com", "Vendedor", "hash", null, false, null);
    }

    @Test
    void rejectsMissingRoleBeforeWriting() {
        WorkerRepository repository = mock(WorkerRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        assertThrows(IllegalArgumentException.class, () -> new WorkerProvisioningService(repository, encoder)
                .create("seller", "Vendedor", "a-safe-password", null, "GIOVA", false));
    }

    @Test
    void superAdminRequiresExplicitConfirmationAndNoTenant() {
        WorkerRepository repository = mock(WorkerRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        WorkerProvisioningService service = new WorkerProvisioningService(repository, encoder);
        assertThrows(IllegalArgumentException.class, () -> service.create("platform", "Plataforma",
                "a-safe-password", "SUPERADMIN", null, false));
        assertThrows(IllegalArgumentException.class, () -> service.create("platform", "Plataforma",
                "a-safe-password", "SUPERADMIN", "GIOVA", true));
        verify(repository, org.mockito.Mockito.never()).insertIdentity(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any());
    }
}

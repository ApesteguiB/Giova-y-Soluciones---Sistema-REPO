package pe.giova.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.giova.admin.AdminWorkerDtos;
import pe.giova.admin.ConflictException;
import pe.giova.admin.NotFoundException;
import pe.giova.audit.AuditService;

class WorkerAccountServiceTest {
    private final WorkerRepository workers = mock(WorkerRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuditService audit = mock(AuditService.class);
    private final WorkerAccountService service = new WorkerAccountService(workers, encoder, audit);
    private final WorkerRepository.Membership activeMembership = new WorkerRepository.Membership(
            19L, 4L, "GIOVA", "GIOVA", RoleCode.ADMIN);
    private final WorkerPrincipal actor = new WorkerPrincipal(
            new WorkerRepository.WorkerRecord(7L, "admin@example.test", "Admin", "admin-hash", true, null),
            List.of(activeMembership), activeMembership);

    @BeforeEach
    void setup() {
        when(encoder.encode(anyString())).thenReturn("encoded-password");
    }

    @Test
    void createsOnlyCompanySubaccountsWithRandomTemporaryPasswordAndAuditWithoutPassword() {
        when(workers.insertIdentity(eq("seller@example.test"), eq("Seller"), eq("encoded-password"),
                isNull(), eq(true), eq(7L))).thenReturn(42L);
        when(workers.findTenantWorker(4L, 42L)).thenReturn(Optional.of(new WorkerRepository.TenantWorkerAccount(
                42L, "seller@example.test", "Seller", RoleCode.SELLER, true, 0L)));

        AdminWorkerDtos.WorkerProvisioned result = service.create(
                new AdminWorkerDtos.WorkerInput("SELLER@example.test", " Seller ", "SELLER"), actor);

        assertEquals("seller@example.test", result.worker().username());
        assertEquals("SELLER", result.worker().roleCode());
        assertEquals(24, result.temporaryPassword().length());
        assertTrue(result.temporaryPassword().chars().allMatch(c ->
                "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".indexOf(c) >= 0));
        verify(workers).insertMembership(42L, 4L, RoleCode.SELLER, 7L);
        verify(audit).success(actor, "ADMIN_WORKER_CREATED", "WORKER_MEMBERSHIP", "42",
                "Cuenta de rol SELLER creada en la empresa activa");
    }

    @Test
    void duplicateUsernameDoesNotRevealWhetherItBelongsToAnotherCompany() {
        when(workers.insertIdentity(eq("used@example.test"), eq("Contador"), eq("encoded-password"),
                isNull(), eq(true), eq(7L))).thenThrow(new DuplicateKeyException("duplicate"));

        ConflictException exception = assertThrows(ConflictException.class, () -> service.create(
                new AdminWorkerDtos.WorkerInput("used@example.test", "Contador", "ACCOUNTANT"), actor));

        assertEquals("USERNAME_UNAVAILABLE", exception.code());
        assertEquals("El nombre de usuario no está disponible", exception.getMessage());
        verify(workers, never()).findTenantWorker(4L, 42L);
    }

    @Test
    void updateAndResetAreScopedToTheActiveCompanyAndPreserveTemporaryPasswordRules() {
        when(workers.findTenantWorker(4L, 42L)).thenReturn(Optional.of(account(
                42L, RoleCode.ACCOUNTANT, true, 3L, "Before")), Optional.of(account(
                42L, RoleCode.ACCOUNTANT, true, 4L, "After")), Optional.of(account(
                42L, RoleCode.ACCOUNTANT, true, 4L, "After")));
        when(workers.updateTenantWorkerDisplayName(4L, 42L, "After", 3L)).thenReturn(1);
        when(workers.updateTenantWorkerPasswordForTemporary(4L, 42L, "encoded-password")).thenReturn(1);

        AdminWorkerDtos.WorkerView updated = service.update(42L,
                new AdminWorkerDtos.WorkerUpdate("After", 3L), actor);
        AdminWorkerDtos.WorkerProvisioned reset = service.resetPassword(42L, actor);

        assertEquals("After", updated.displayName());
        assertEquals(24, reset.temporaryPassword().length());
        verify(workers).updateTenantWorkerDisplayName(4L, 42L, "After", 3L);
        verify(workers).updateTenantWorkerPasswordForTemporary(4L, 42L, "encoded-password");
        verify(audit).success(actor, "ADMIN_WORKER_PASSWORD_RESET", "WORKER_MEMBERSHIP", "42",
                "Contraseña temporal generada para una cuenta de la empresa activa");
    }

    @Test
    void missingForeignCompanyAccountReturnsNotFoundAndSelfCannotBeDisabled() {
        when(workers.findTenantWorker(4L, 88L)).thenReturn(Optional.empty());
        when(workers.findTenantWorker(4L, 7L)).thenReturn(Optional.of(account(
                7L, RoleCode.SELLER, true, 0L, "Admin")));

        assertThrows(NotFoundException.class, () -> service.resetPassword(88L, actor));
        ConflictException selfDisable = assertThrows(ConflictException.class, () -> service.setEnabled(7L,
                new AdminWorkerDtos.WorkerStatusInput(false, 0L), actor));

        assertEquals("SELF_DISABLE_FORBIDDEN", selfDisable.code());
        verify(workers, never()).updateTenantWorkerEnabled(4L, 7L, false, 0L);
    }

    @Test
    void ownPasswordChangeChecksCurrentPasswordAndClearsTheForcedChangeFlag() {
        when(encoder.matches("current-value", "old-hash")).thenReturn(true);
        when(encoder.matches("new-value-123", "old-hash")).thenReturn(false);
        when(encoder.encode("new-value-123")).thenReturn("new-hash");
        when(workers.updateOwnPassword(7L, "old-hash", "new-hash")).thenReturn(1);
        WorkerPrincipal tempActor = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
                7L, "admin@example.test", "Admin", "old-hash", true, null, true),
                List.of(activeMembership), activeMembership);

        WorkerPrincipal updated = service.changeOwnPassword(tempActor, "current-value", "new-value-123");

        assertFalse(updated.mustChangePassword());
        assertEquals("new-hash", updated.getPassword());
        verify(audit).success(updated, "AUTH_PASSWORD_CHANGED", "WORKER", "7",
                "Contraseña propia actualizada");
    }

    @Test
    void ownPasswordChangeRejectsAnIncorrectCurrentPasswordWithoutWriting() {
        when(encoder.matches("wrong-value", "old-hash")).thenReturn(false);
        WorkerPrincipal tempActor = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
                7L, "admin@example.test", "Admin", "old-hash", true, null, true),
                List.of(activeMembership), activeMembership);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.changeOwnPassword(tempActor, "wrong-value", "new-value-123"));

        assertEquals("La contraseña actual no es válida", exception.getMessage());
        verify(workers, never()).updateOwnPassword(eq(7L), anyString(), anyString());
        assertNotNull(exception.getMessage());
    }

    private static WorkerRepository.TenantWorkerAccount account(long id, RoleCode role, boolean enabled,
                                                                long version, String displayName) {
        return new WorkerRepository.TenantWorkerAccount(id, "worker@example.test", displayName, role,
                enabled, version);
    }
}

package pe.giova.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class WorkerUserDetailsServiceTest {
    private final WorkerRepository repository = mock(WorkerRepository.class);
    private final WorkerUserDetailsService service = new WorkerUserDetailsService(repository);

    @Test
    void selectsTheOnlyActiveMembershipAtLogin() {
        when(repository.findByUsername("seller@example.test")).thenReturn(Optional.of(worker("seller@example.test", null)));
        when(repository.findActiveMemberships(17L)).thenReturn(List.of(membership(4L, 2L, RoleCode.SELLER)));

        WorkerPrincipal principal = (WorkerPrincipal) service.loadUserByUsername(" seller@example.test ");

        assertEquals(2L, principal.tenantId());
        assertEquals(RoleCode.SELLER, principal.roleCode());
        assertFalse(principal.membershipSelectionRequired());
        verify(repository).findActiveMemberships(17L);
    }

    @Test
    void leavesSeveralMembershipsPendingSelection() {
        when(repository.findByUsername("shared@example.test")).thenReturn(Optional.of(worker("shared@example.test", null)));
        when(repository.findActiveMemberships(17L)).thenReturn(List.of(
                membership(4L, 2L, RoleCode.ADMIN), membership(5L, 3L, RoleCode.SELLER)));

        WorkerPrincipal principal = (WorkerPrincipal) service.loadUserByUsername("shared@example.test");

        assertTrue(principal.membershipSelectionRequired());
        assertTrue(principal.getAuthorities().isEmpty());
        assertEquals(2, principal.memberships().size());
    }

    @Test
    void refusesWorkersWithoutActiveMembership() {
        when(repository.findByUsername("inactive@example.test")).thenReturn(Optional.of(worker("inactive@example.test", null)));
        when(repository.findActiveMemberships(17L)).thenReturn(List.of());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("inactive@example.test"));
    }

    @Test
    void loadsSuperadminWithoutACompanyMembership() {
        when(repository.findByUsername("root@example.test")).thenReturn(Optional.of(worker("root@example.test", RoleCode.SUPERADMIN)));

        WorkerPrincipal principal = (WorkerPrincipal) service.loadUserByUsername("root@example.test");

        assertEquals(RoleCode.SUPERADMIN, principal.roleCode());
        assertEquals(0, principal.memberships().size());
    }

    private static WorkerRepository.WorkerRecord worker(String username, RoleCode role) {
        return new WorkerRepository.WorkerRecord(17L, username, "Trabajador", "hash", true, role);
    }

    private static WorkerRepository.Membership membership(long id, long tenantId, RoleCode role) {
        return new WorkerRepository.Membership(id, tenantId, "TENANT-" + tenantId,
                "Empresa " + tenantId, role);
    }
}

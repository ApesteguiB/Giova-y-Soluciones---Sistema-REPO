package pe.giova.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorkerPrincipalTest {
    @Test
    void exposesOnlyTheRoleAuthority() {
        WorkerPrincipal principal = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
                7L, "seller@example.test", "Vendedor", "hash", true, RoleCode.SELLER));

        assertEquals("ROLE_SELLER", principal.getAuthorities().iterator().next().getAuthority());
        assertEquals(RoleCode.SELLER, principal.roleCode());
    }

    @Test
    void waitsForSelectionWhenIdentityHasSeveralCompanyMemberships() {
        WorkerRepository.WorkerRecord worker = new WorkerRepository.WorkerRecord(
                9L, "shared@example.test", "Compartido", "hash", true, null);
        WorkerRepository.Membership first = new WorkerRepository.Membership(12L, 2L, "TWO", "Empresa dos", RoleCode.ADMIN);
        WorkerRepository.Membership second = new WorkerRepository.Membership(13L, 3L, "THREE", "Empresa tres", RoleCode.SELLER);

        WorkerPrincipal principal = new WorkerPrincipal(worker, List.of(first, second), null);

        assertTrue(principal.membershipSelectionRequired());
        assertNull(principal.roleCode());
        assertNull(principal.tenantId());
        assertTrue(principal.getAuthorities().isEmpty());
        assertEquals(RoleCode.SELLER, principal.withActiveMembership(second).roleCode());
        assertEquals(3L, principal.withActiveMembership(second).tenantId());
        assertFalse(principal.withActiveMembership(second).membershipSelectionRequired());
    }

    @Test
    void superadminHasPlatformRoleWithoutCompanyMembership() {
        WorkerPrincipal principal = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
                1L, "root@example.test", "Plataforma", "hash", true, RoleCode.SUPERADMIN));

        assertEquals(RoleCode.SUPERADMIN, principal.roleCode());
        assertNull(principal.tenantId());
        assertEquals("ROLE_SUPERADMIN", principal.getAuthorities().iterator().next().getAuthority());
    }
}

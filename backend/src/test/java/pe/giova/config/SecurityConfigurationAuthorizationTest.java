package pe.giova.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;

class SecurityConfigurationAuthorizationTest {
    @Test
    void pendingMembershipCanOnlyUseIdentityAndMembershipEndpoints() {
        WorkerRepository.Membership first = membership(11L, 2L, RoleCode.ADMIN);
        WorkerRepository.Membership second = membership(12L, 3L, RoleCode.SELLER);
        WorkerPrincipal pending = new WorkerPrincipal(worker(), List.of(first, second), null);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(pending, null, pending.getAuthorities());

        assertTrue(SecurityConfiguration.isAllowed(authentication, "/api/v1/auth/me"));
        assertTrue(SecurityConfiguration.isAllowed(authentication, "/api/v1/auth/membership"));
        assertTrue(SecurityConfiguration.isAllowed(authentication, "/api/v1/auth/logout"));
        assertFalse(SecurityConfiguration.isAllowed(authentication, "/api/v1/tasks"));
        assertFalse(SecurityConfiguration.isAllowed(authentication, "/api/v1/tenders/matches"));
    }

    @Test
    void superadminIsLimitedToPlatformAndIdentityRoutes() {
        WorkerPrincipal root = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
                1L, "root@example.test", "Plataforma", "hash", true, RoleCode.SUPERADMIN));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(root, null, root.getAuthorities());

        assertTrue(SecurityConfiguration.isAllowed(authentication, "/api/v1/platform/tenants"));
        assertFalse(SecurityConfiguration.isAllowed(authentication, "/api/v1/reports/quotations.xlsx"));
        assertFalse(SecurityConfiguration.isAllowed(authentication, "/api/v1/tasks"));
    }

    @Test
    void temporaryPasswordSessionCanOnlyReadIdentityChangePasswordOrLogout() {
        WorkerRepository.Membership membership = membership(21L, 2L, RoleCode.ADMIN);
        WorkerRepository.WorkerRecord tempWorker = new WorkerRepository.WorkerRecord(
                7L, "admin@example.test", "Admin", "hash", true, null, true);
        WorkerPrincipal principal = new WorkerPrincipal(tempWorker, List.of(membership), membership);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());

        assertTrue(SecurityConfiguration.isAllowed(authentication, "/api/v1/auth/me"));
        assertTrue(SecurityConfiguration.isAllowed(authentication, "/api/v1/auth/password"));
        assertTrue(SecurityConfiguration.isAllowed(authentication, "/api/v1/auth/logout"));
        assertFalse(SecurityConfiguration.isAllowed(authentication, "/api/v1/auth/membership"));
        assertFalse(SecurityConfiguration.isAllowed(authentication, "/api/v1/tasks"));
        assertFalse(SecurityConfiguration.isAdminAllowed(authentication));
    }

    @Test
    void unauthenticatedAndAnonymousRequestsAreDenied() {
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertFalse(SecurityConfiguration.isAllowed(null, "/api/v1/tasks"));
        assertFalse(SecurityConfiguration.isAllowed(anonymous, "/api/v1/tasks"));
    }

    private static WorkerRepository.WorkerRecord worker() {
        return new WorkerRepository.WorkerRecord(7L, "shared@example.test", "Usuario", "hash", true, null);
    }

    private static WorkerRepository.Membership membership(long id, long tenantId, RoleCode role) {
        return new WorkerRepository.Membership(id, tenantId, "TENANT-" + tenantId, "Empresa", role);
    }
}

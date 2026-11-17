package pe.giova.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import pe.giova.audit.AuditService;

class AuthControllerMembershipTest {
    private final WorkerRepository workers = mock(WorkerRepository.class);
    private final SessionAuthenticationStrategy sessionStrategy = mock(SessionAuthenticationStrategy.class);
    private final SecurityContextRepository contextRepository = mock(SecurityContextRepository.class);
    private final CsrfTokenRepository csrfRepository = mock(CsrfTokenRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final WorkerAccountService accounts = mock(WorkerAccountService.class);
    private final AuthController controller = new AuthController(mock(AuthenticationManager.class), workers,
            accounts,
            mock(LoginRateLimiter.class), sessionStrategy, contextRepository, csrfRepository, audit);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void changesToOwnedActiveMembershipAndRotatesSessionAndCsrf() {
        WorkerRepository.Membership first = membership(21L, 2L, RoleCode.ADMIN);
        WorkerRepository.Membership second = membership(22L, 3L, RoleCode.SELLER);
        WorkerPrincipal pending = new WorkerPrincipal(worker(), List.of(first, second), null);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                pending, null, pending.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(workers.findActiveMembership(7L, 22L)).thenReturn(Optional.of(second));
        CsrfToken csrfToken = new org.springframework.security.web.csrf.DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "rotated");
        when(csrfRepository.generateToken(any())).thenReturn(csrfToken);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AuthController.WorkerResponse result = controller.changeMembership(
                new AuthController.MembershipChange(22L), authentication, request, response);

        assertEquals(3L, result.tenantId());
        assertEquals("SELLER", result.roleCode());
        assertEquals(22L, result.membershipId());
        assertEquals(2, result.memberships().size());
        verify(sessionStrategy).onAuthentication(any(Authentication.class), same(request), same(response));
        verify(contextRepository).saveContext(any(), same(request), same(response));
        verify(csrfRepository).saveToken(same(csrfToken), same(request), same(response));
        verify(audit).success(any(WorkerPrincipal.class), org.mockito.ArgumentMatchers.eq("AUTH_MEMBERSHIP_CHANGED"),
                org.mockito.ArgumentMatchers.eq("WORKER_MEMBERSHIP"), org.mockito.ArgumentMatchers.eq("22"),
                org.mockito.ArgumentMatchers.contains("TENANT-3"));
        assertEquals(3L, ((WorkerPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).tenantId());
    }

    @Test
    void refusesMembershipThatIsNotOwnedOrActiveWithoutRotatingSession() {
        WorkerPrincipal current = new WorkerPrincipal(worker(), List.of(membership(21L, 2L, RoleCode.ADMIN)),
                membership(21L, 2L, RoleCode.ADMIN));
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                current, null, current.getAuthorities());
        when(workers.findActiveMembership(7L, 90L)).thenReturn(Optional.empty());

        assertThrows(AuthenticationServiceException.class, () -> controller.changeMembership(
                new AuthController.MembershipChange(90L), authentication,
                new MockHttpServletRequest(), new MockHttpServletResponse()));

        verifyNoInteractions(sessionStrategy, contextRepository, csrfRepository, audit);
    }

    @Test
    void superadminCannotSelectACompanyMembership() {
        WorkerPrincipal principal = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
                7L, "root@example.test", "Plataforma", "hash", true, RoleCode.SUPERADMIN));
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());

        assertThrows(SecurityException.class, () -> controller.changeMembership(
                new AuthController.MembershipChange(1L), authentication,
                new MockHttpServletRequest(), new MockHttpServletResponse()));
        verifyNoInteractions(workers, sessionStrategy, contextRepository, csrfRepository, audit);
    }

    @Test
    void changesOwnPasswordAndRotatesSessionAndCsrfWithoutCachingTheResponse() {
        WorkerRepository.Membership membership = membership(21L, 2L, RoleCode.ADMIN);
        WorkerPrincipal current = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
                7L, "worker@example.test", "Usuario", "temporary-hash", true, null, true),
                List.of(membership), membership);
        WorkerPrincipal updated = current.withPasswordChanged("new-hash");
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                current, null, current.getAuthorities());
        when(accounts.changeOwnPassword(same(current), org.mockito.ArgumentMatchers.eq("temp-value"),
                org.mockito.ArgumentMatchers.eq("new-password-123"))).thenReturn(updated);
        CsrfToken csrfToken = new org.springframework.security.web.csrf.DefaultCsrfToken(
                "X-XSRF-TOKEN", "_csrf", "rotated");
        when(csrfRepository.generateToken(any())).thenReturn(csrfToken);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.changePassword(new AuthController.PasswordChangeInput(
                "temp-value", "new-password-123"), authentication, request, response);

        assertEquals(200, result.getStatusCode().value());
        assertEquals("no-store", result.getHeaders().getCacheControl());
        assertEquals("no-cache", result.getHeaders().getFirst("Pragma"));
        assertEquals(false, result.getBody().mustChangePassword());
        assertEquals("ADMIN", result.getBody().roleCode());
        verify(sessionStrategy).onAuthentication(any(Authentication.class), same(request), same(response));
        verify(contextRepository).saveContext(any(), same(request), same(response));
        verify(csrfRepository).saveToken(same(csrfToken), same(request), same(response));
    }

    private static WorkerRepository.WorkerRecord worker() {
        return new WorkerRepository.WorkerRecord(7L, "shared@example.test", "Usuario", "hash", true, null);
    }

    private static WorkerRepository.Membership membership(long id, long tenantId, RoleCode role) {
        return new WorkerRepository.Membership(id, tenantId, "TENANT-" + tenantId,
                "Empresa " + tenantId, role);
    }
}

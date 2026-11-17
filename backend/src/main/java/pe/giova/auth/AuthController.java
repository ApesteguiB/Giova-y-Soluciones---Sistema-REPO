package pe.giova.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final WorkerRepository workers;
    private final WorkerAccountService accounts;
    private final LoginRateLimiter rateLimiter;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final pe.giova.audit.AuditService audit;

    public AuthController(AuthenticationManager authenticationManager, WorkerRepository workers,
                          WorkerAccountService accounts,
                          LoginRateLimiter rateLimiter, SessionAuthenticationStrategy sessionStrategy,
                          SecurityContextRepository securityContextRepository,
                          CsrfTokenRepository csrfTokenRepository, pe.giova.audit.AuditService audit) {
        this.authenticationManager = authenticationManager;
        this.workers = workers;
        this.accounts = accounts;
        this.rateLimiter = rateLimiter;
        this.sessionStrategy = sessionStrategy;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.audit = audit;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<WorkerResponse> login(@RequestParam String username, @RequestParam String password,
                                                  HttpServletRequest request, HttpServletResponse response) {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase();
        String key = rateKey(request, normalizedUsername);
        rateLimiter.check(key);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(normalizedUsername, password));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            sessionStrategy.onAuthentication(authentication, request, response);
            securityContextRepository.saveContext(context, request, response);
            rotateCsrfToken(request, response);
            rateLimiter.clear(key);
            WorkerPrincipal principal = (WorkerPrincipal) authentication.getPrincipal();
            return ResponseEntity.ok(workerResponse(principal));
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            rateLimiter.recordFailure(key);
            throw new AuthenticationServiceException("Credenciales inválidas");
        }
    }

    @GetMapping("/me")
    public WorkerResponse me(Authentication authentication) {
        WorkerPrincipal principal = (WorkerPrincipal) authentication.getPrincipal();
        return workerResponse(principal);
    }

    @PostMapping("/password")
    public ResponseEntity<WorkerResponse> changePassword(@Valid @RequestBody PasswordChangeInput input,
                                                         Authentication authentication,
                                                         HttpServletRequest request, HttpServletResponse response) {
        WorkerPrincipal current = (WorkerPrincipal) authentication.getPrincipal();
        WorkerPrincipal updated = accounts.changeOwnPassword(current, input.currentPassword(), input.newPassword());
        Authentication changed = UsernamePasswordAuthenticationToken.authenticated(
                updated, null, updated.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(changed);
        SecurityContextHolder.setContext(context);
        sessionStrategy.onAuthentication(changed, request, response);
        securityContextRepository.saveContext(context, request, response);
        rotateCsrfToken(request, response);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache")
                .body(workerResponse(updated));
    }

    @PostMapping("/membership")
    public WorkerResponse changeMembership(@Valid @RequestBody MembershipChange input, Authentication authentication,
                                           HttpServletRequest request, HttpServletResponse response) {
        WorkerPrincipal current = (WorkerPrincipal) authentication.getPrincipal();
        if (current.roleCode() == RoleCode.SUPERADMIN) {
            throw new SecurityException("El superadministrador no tiene membresías de empresa");
        }
        WorkerRepository.Membership membership = workers.findActiveMembership(current.id(), input.membershipId())
                .orElseThrow(() -> new AuthenticationServiceException("La membresía no está disponible"));
        WorkerPrincipal updated = current.withActiveMembership(membership);
        Authentication changed = UsernamePasswordAuthenticationToken.authenticated(
                updated, null, updated.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(changed);
        SecurityContextHolder.setContext(context);
        sessionStrategy.onAuthentication(changed, request, response);
        securityContextRepository.saveContext(context, request, response);
        rotateCsrfToken(request, response);
        audit.success(updated, "AUTH_MEMBERSHIP_CHANGED", "WORKER_MEMBERSHIP",
                Long.toString(membership.id()), "Empresa activa: " + membership.tenantCode());
        return workerResponse(updated);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        securityContextRepository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private static String rateKey(HttpServletRequest request, String username) {
        String remote = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        return remote + ":" + username;
    }

    private static WorkerResponse workerResponse(WorkerPrincipal principal) {
        RoleCode role = principal.roleCode();
        List<MembershipResponse> memberships = principal.memberships().stream()
                .map(membership -> new MembershipResponse(membership.id(), membership.tenantId(),
                        membership.tenantCode(), membership.tenantName(), membership.roleCode().name(),
                        membership.roleCode().label()))
                .toList();
        return new WorkerResponse(principal.id(), principal.getUsername(), principal.displayName(),
                principal.membershipId(), principal.tenantId(), principal.tenantCode(), principal.tenantName(),
                role == null ? null : role.name(), role == null ? null : role.label(),
                principal.membershipSelectionRequired(), memberships, principal.mustChangePassword());
    }

    private void rotateCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(token, request, response);
    }

    public record MembershipChange(@NotNull @Positive Long membershipId) {}

    public record PasswordChangeInput(@NotBlank @Size(max = 200) String currentPassword,
                                      @NotBlank @Size(min = 12, max = 200) String newPassword) {}

    public record MembershipResponse(long membershipId, long tenantId, String tenantCode, String tenantName,
                                     String roleCode, String roleLabel) {}

    public record WorkerResponse(long id, String username, String displayName, Long membershipId, Long tenantId,
                                 String tenantCode, String tenantName, String roleCode, String roleLabel,
                                 boolean membershipSelectionRequired, List<MembershipResponse> memberships,
                                 boolean mustChangePassword) {}
}

package pe.giova.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class TenantScopeTest {
    @BeforeEach
    void authenticateTenant() {
        WorkerRepository.Membership membership = new WorkerRepository.Membership(8L, 2L, "TWO", "Empresa dos", RoleCode.ADMIN);
        WorkerPrincipal principal = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
                5L, "admin@example.test", "Admin", "hash", true, null), java.util.List.of(membership), membership);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void injectsTenantFromSession() {
        MapSqlParameterSource parameters = TenantScope.withTenant(new MapSqlParameterSource("publicId", "record"));

        assertEquals(2L, ((Number) parameters.getValue("tenantId")).longValue());
    }

    @Test
    void refusesTenantOverrideFromRequestData() {
        MapSqlParameterSource parameters = new MapSqlParameterSource("tenantId", 3L);

        assertThrows(SecurityException.class, () -> TenantScope.withTenant(parameters));
    }
}

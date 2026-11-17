package pe.giova.auth;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Resolves the active company exclusively from the authenticated session. */
public final class TenantScope {
    private TenantScope() {}

    public static long requireTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof WorkerPrincipal principal)
                || principal.tenantId() == null) {
            throw new SecurityException("La sesión no tiene una empresa activa");
        }
        return principal.tenantId();
    }

    public static MapSqlParameterSource withTenant(MapSqlParameterSource parameters) {
        long activeTenantId = requireTenantId();
        if (parameters.hasValue("tenantId")) {
            Object supplied = parameters.getValue("tenantId");
            if (!(supplied instanceof Number number) || number.longValue() != activeTenantId) {
                throw new SecurityException("El tenantId debe coincidir con la empresa activa de la sesión");
            }
            return parameters;
        }
        return parameters.addValue("tenantId", activeTenantId);
    }
}

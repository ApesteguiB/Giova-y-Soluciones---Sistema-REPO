package pe.giova.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.SecurityFilterChain;
import pe.giova.catalog.api.CatalogDtos.Problem;
import pe.giova.auth.WorkerPrincipal;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityContextRepository.class)
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        return repository;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper objectMapper,
                                             SecurityContextRepository securityContextRepository,
                                             CsrfTokenRepository csrfTokenRepository) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/catalog/**", "/api/v1/health", "/api/v1/health/readiness", "/api/v1/auth/csrf",
                                "/api/v1/auth/login", "/api/v1/auth/logout", "/api/v1/contact-submissions",
                                "/api/v1/claims", "/error").permitAll()
                        .requestMatchers("/api/v1/admin/**").access((authentication, context) ->
                                new AuthorizationDecision(isAdminAllowed(authentication.get())))
                        .anyRequest().access((authentication, context) -> new AuthorizationDecision(
                                isAllowed(authentication.get(), context.getRequest().getServletPath())))
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfHandler))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                .requestCache(cache -> cache.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jsonEntryPoint(objectMapper))
                        .accessDeniedHandler(jsonDeniedHandler(objectMapper)))
                .build();
    }

    static boolean isAllowed(org.springframework.security.core.Authentication current, String path) {
        if (current == null || !current.isAuthenticated() || current instanceof AnonymousAuthenticationToken) return false;
        boolean authUtility = path.equals("/api/v1/auth/me") || path.equals("/api/v1/auth/membership")
                || path.equals("/api/v1/auth/password") || path.equals("/api/v1/auth/logout");
        if (!(current.getPrincipal() instanceof WorkerPrincipal worker)) return true;
        if (worker.mustChangePassword()) {
            return path.equals("/api/v1/auth/me") || path.equals("/api/v1/auth/password")
                    || path.equals("/api/v1/auth/logout");
        }
        if (worker.roleCode() == null || worker.membershipSelectionRequired()) return authUtility;
        if (worker.roleCode() == pe.giova.auth.RoleCode.SUPERADMIN) {
            return authUtility || path.equals("/api/v1/platform") || path.startsWith("/api/v1/platform/");
        }
        return true;
    }

    static boolean isAdminAllowed(org.springframework.security.core.Authentication current) {
        if (current == null || !current.isAuthenticated() || current instanceof AnonymousAuthenticationToken) {
            return false;
        }
        if (current.getPrincipal() instanceof WorkerPrincipal worker && worker.mustChangePassword()) return false;
        return current.getAuthorities().stream().anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private AuthenticationEntryPoint jsonEntryPoint(JsonMapper mapper) {
        return (request, response, exception) -> writeProblem(mapper, response, 401,
                "Autenticación requerida", "AUTHENTICATION_REQUIRED");
    }

    private AccessDeniedHandler jsonDeniedHandler(JsonMapper mapper) {
        return (request, response, exception) -> writeProblem(mapper, response, 403,
                "La solicitud no está permitida", "ACCESS_DENIED");
    }

    private void writeProblem(JsonMapper mapper, jakarta.servlet.http.HttpServletResponse response,
                              int status, String detail, String code) throws java.io.IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getWriter(), new Problem("about:blank", status == 401 ? "No autenticado" : "Acceso denegado",
                status, detail, code));
    }
}

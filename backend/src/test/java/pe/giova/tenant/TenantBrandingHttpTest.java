package pe.giova.tenant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.giova.admin.NotFoundException;
import pe.giova.api.ApiExceptionHandler;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;

@SpringBootTest
class TenantBrandingHttpTest {
    @Autowired private FilterChainProxy securityFilterChain;

    @Test
    void logoEndpointUsesHttp401403And404ForAuthAndMissingLogo() throws Exception {
        TenantBrandingService service = mock(TenantBrandingService.class);
        when(service.currentLogo()).thenThrow(new NotFoundException("Logo no encontrado"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TenantBrandingController(service))
                .setControllerAdvice(new ApiExceptionHandler()).addFilters(securityFilterChain).build();

        mvc.perform(get("/api/v1/tenant/branding/logo")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/tenant/branding/logo").session(session(RoleCode.SUPERADMIN, null)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/tenant/branding/logo").session(session(RoleCode.ADMIN, 2L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void brandingEndpointReturnsNoStoreAndBadColorIsHttp400() throws Exception {
        TenantBrandingService service = mock(TenantBrandingService.class);
        when(service.current()).thenReturn(new TenantBrandingDtos.Response(2L, "Empresa B", "#f26b21",
                "#ffffff", "#ff9f1c", null, false));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TenantBrandingController(service))
                .setControllerAdvice(new ApiExceptionHandler()).addFilters(securityFilterChain).build();

        mvc.perform(get("/api/v1/tenant/branding").session(session(RoleCode.ADMIN, 2L)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("displayName")));

        when(service.current()).thenThrow(new TenantBrandingValidationException("primaryColor inválido"));
        mvc.perform(get("/api/v1/tenant/branding").session(session(RoleCode.ADMIN, 2L)))
                .andExpect(status().isBadRequest());
    }

    private static MockHttpSession session(RoleCode role, Long tenantId) {
        WorkerRepository.WorkerRecord worker = new WorkerRepository.WorkerRecord(
                5L, "worker@example.test", "Trabajador", "hash", true,
                role == RoleCode.SUPERADMIN ? RoleCode.SUPERADMIN : null);
        WorkerPrincipal principal;
        if (role == RoleCode.SUPERADMIN) {
            principal = new WorkerPrincipal(worker);
        } else {
            WorkerRepository.Membership membership = new WorkerRepository.Membership(9L, tenantId,
                    "TENANT", "Empresa", role);
            principal = new WorkerPrincipal(worker, List.of(membership), membership);
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}

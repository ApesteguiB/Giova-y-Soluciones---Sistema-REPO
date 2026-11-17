package pe.giova;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;

@SpringBootTest
class GiovaBackendApplicationTests {
    @Autowired
    private FilterChainProxy securityFilterChain;
    @Autowired
    private WebApplicationContext webApplicationContext;

    @Test
    void contextLoads() {
    }

    @Test
    void protectedSingleSegmentBusinessRouteRequiresAuthentication() throws Exception {
        MockHttpServletResponse response = filter("/api/v1/tasks", null);

        org.junit.jupiter.api.Assertions.assertEquals(401, response.getStatus());
    }

    @Test
    void tenantBrandingEndpointsRequireAuthentication() throws Exception {
        org.junit.jupiter.api.Assertions.assertEquals(401,
                filter("/api/v1/tenant/branding", null).getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(401,
                filter("/api/v1/tenant/branding/logo", null).getStatus());
    }

    @Test
    void superadminCannotReadCompanyBranding() throws Exception {
        WorkerPrincipal root = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
                1L, "root@example.test", "Plataforma", "hash", true, RoleCode.SUPERADMIN));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                root, null, root.getAuthorities()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        org.junit.jupiter.api.Assertions.assertEquals(403,
                filter("/api/v1/tenant/branding/logo", session).getStatus());
    }

    @Test
    void pendingMembershipCannotCallBusinessRoutes() throws Exception {
        WorkerRepository.Membership first = new WorkerRepository.Membership(11L, 2L, "TWO", "Empresa dos", RoleCode.ADMIN);
        WorkerRepository.Membership second = new WorkerRepository.Membership(12L, 3L, "THREE", "Empresa tres", RoleCode.SELLER);
        WorkerPrincipal pending = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
                7L, "shared@example.test", "Usuario", "hash", true, null), java.util.List.of(first, second), null);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                pending, null, pending.getAuthorities()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        MockHttpServletResponse response = filter("/api/v1/tasks", session);

        org.junit.jupiter.api.Assertions.assertEquals(403, response.getStatus());
    }

    @Test
    void platformApiRequiresSuperadminAndSuperadminCannotCallBusinessApi() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).addFilters(securityFilterChain).build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/platform/tenants")
                .servletPath("/api/v1/platform/tenants"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        for (RoleCode role : new RoleCode[] {RoleCode.ADMIN, RoleCode.ACCOUNTANT, RoleCode.SELLER}) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/platform/tenants").servletPath("/api/v1/platform/tenants").session(session(role)))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        }
        MockHttpSession rootSession = session(RoleCode.SUPERADMIN);
        SecurityContext rootContext = (SecurityContext) rootSession.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        org.junit.jupiter.api.Assertions.assertTrue(rootContext.getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_SUPERADMIN")));
        var platformResponse = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/v1/platform/tenants").servletPath("/api/v1/platform/tenants").session(rootSession))
                .andReturn().getResponse();
        org.junit.jupiter.api.Assertions.assertEquals(200, platformResponse.getStatus(),
                platformResponse.getContentAsString());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/tasks")
                .servletPath("/api/v1/tasks").session(session(RoleCode.SUPERADMIN)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void companyWorkerApiRequiresAdminAndReadsTheActiveTenant() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).addFilters(securityFilterChain).build();
        String path = "/api/v1/admin/workers";

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).servletPath(path))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        for (RoleCode role : new RoleCode[] {RoleCode.ACCOUNTANT, RoleCode.SELLER, RoleCode.SUPERADMIN}) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                    .servletPath(path).session(session(role)))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                        .servletPath(path).session(session(RoleCode.ADMIN)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Pragma", "no-cache"));
    }

    private static MockHttpSession session(RoleCode role) {
        WorkerRepository.WorkerRecord worker = new WorkerRepository.WorkerRecord(1L, "root@example.test",
                "Plataforma", "hash", true, role == RoleCode.SUPERADMIN ? RoleCode.SUPERADMIN : null);
        WorkerPrincipal principal;
        if (role == RoleCode.SUPERADMIN) {
            principal = new WorkerPrincipal(worker);
        } else {
            WorkerRepository.Membership membership = new WorkerRepository.Membership(1L, 1L, "GIOVA", "GIOVA", role);
            principal = new WorkerPrincipal(worker, java.util.List.of(membership), membership);
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private MockHttpServletResponse filter(String path, MockHttpSession session) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        if (session != null) request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        securityFilterChain.doFilter(request, response, new MockFilterChain());
        return response;
    }
}

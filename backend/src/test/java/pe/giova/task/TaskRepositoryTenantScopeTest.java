package pe.giova.task;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;

/**
 * El public_id de una tarea es un UUID global. Si la consulta no filtra por
 * tenant_id, un ADMIN de otra empresa puede leer la tarea con solo conocer ese
 * identificador: pasar el parámetro no alcanza, tiene que estar en el WHERE.
 */
class TaskRepositoryTenantScopeTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings("unchecked")
    void lookupsByPublicIdFilterByTenantInTheSqlItself() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        authenticateAsTenant(2L);
        TaskRepository repository = new TaskRepository(jdbc);

        repository.findByPublicId("4f0c2c3e-0000-0000-0000-000000000000");
        repository.findRecord("4f0c2c3e-0000-0000-0000-000000000000");

        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).query(statements.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        for (String sql : statements.getAllValues()) {
            assertTrue(sql.contains("t.tenant_id=:tenantId"),
                    "La consulta por public_id debe filtrar por tenant_id: " + sql);
            assertTrue(sql.contains("t.public_id=:publicId"), sql);
        }
    }

    private static void authenticateAsTenant(long tenantId) {
        WorkerRepository.Membership membership = new WorkerRepository.Membership(50L, tenantId,
                "EMPRESA" + tenantId, "Empresa " + tenantId, RoleCode.ADMIN);
        WorkerPrincipal principal = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
                9L, "admin@example.test", "Admin", "hash", true, null, false),
                List.of(membership), membership);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities()));
    }
}

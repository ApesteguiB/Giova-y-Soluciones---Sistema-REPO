package pe.giova.report;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;

class ReportRepositoryTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);

    @BeforeEach
    void setActiveTenant() {
        WorkerRepository.Membership membership = new WorkerRepository.Membership(6L, 9L, "TENANT-9",
                "Empresa nueve", RoleCode.ADMIN);
        WorkerPrincipal principal = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
                7L, "admin@example.test", "Admin", "hash", true, null), List.of(membership), membership);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bothWorkbookQueriesAreScopedToTheActiveTenant() {
        ReportRepository repository = new ReportRepository(jdbc);

        repository.quotations(LocalDateTime.now().minusDays(7), LocalDateTime.now(), null, null, 100);
        repository.accounting(LocalDate.now().minusDays(7), LocalDate.now(), null, null, 100);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(2))
                .query(sql.capture(), params.capture(), any(RowMapper.class));
        assertTrue(sql.getAllValues().get(0).contains("q.tenant_id=:tenantId"));
        assertTrue(sql.getAllValues().get(1).contains("i.tenant_id=:tenantId"));
        assertTrue(params.getAllValues().stream()
                .allMatch(p -> ((Number) p.getValue("tenantId")).longValue() == 9L));
    }
}

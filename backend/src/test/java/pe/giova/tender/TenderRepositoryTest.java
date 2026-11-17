package pe.giova.tender;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;
import pe.giova.tender.contract.TenderJsonCodec;
import tools.jackson.databind.json.JsonMapper;

class TenderRepositoryTest {
    @Test
    void matchUpsertQualifiesVersionAndBindsTheProfileTenant() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            MapSqlParameterSource params = invocation.getArgument(1);
            assertTrue(sql.contains("version=tender_matches.version+1"));
            assertTrue(sql.contains("mp.id=:profileId AND mp.tenant_id=:tenantId"));
            assertTrue(((Number) params.getValue("tenantId")).longValue() == 2L);
            return 1;
        });
        new TenderRepository(jdbc, new TenderJsonCodec(JsonMapper.builder().build()))
                .upsertMatchForTenant(10L, 20L, 2L, 90, "[]");
    }

    @BeforeEach
    void setActiveTenant() {
        WorkerRepository.Membership membership = new WorkerRepository.Membership(4L, 2L, "TENANT-2",
                "Empresa 2", RoleCode.ADMIN);
        WorkerPrincipal principal = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
                7L, "admin@example.test", "Admin", "hash", true, null), List.of(membership), membership);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearActiveTenant() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void usesMariaDbCompatibleBackslashEscapeInTenderFilters() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    assertTrue(sql.contains("ESCAPE '\\\\'"));
                    assertTrue(sql.contains("m.tenant_id=:tenantId"));
                    return List.of();
                });
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), any(Class.class))).thenReturn(0L);

        TenderRepository repository = new TenderRepository(jdbc,
                new TenderJsonCodec(JsonMapper.builder().build()));

        repository.findMatches(null, "hospital", "", "Lima", null, null, null, 0, 20);

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(anyString(), parameters.capture(), any(RowMapper.class));
        verify(jdbc).queryForObject(anyString(), parameters.capture(), org.mockito.ArgumentMatchers.eq(Long.class));
        assertTrue(parameters.getAllValues().stream().allMatch(p -> ((Number) p.getValue("tenantId")).longValue() == 2L));
    }
}

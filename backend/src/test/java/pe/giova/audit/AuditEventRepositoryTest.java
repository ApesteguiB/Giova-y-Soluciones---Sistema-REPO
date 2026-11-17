package pe.giova.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;

class AuditEventRepositoryTest {
    @Test
    void recordsPasswordChangeWhileMultipleMembershipAccountHasNoSelectedRole() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AuditEventRepository repository = new AuditEventRepository(jdbc);
        WorkerRepository.Membership first = new WorkerRepository.Membership(10L, 1L, "ONE", "One",
                pe.giova.auth.RoleCode.ADMIN);
        WorkerRepository.Membership second = new WorkerRepository.Membership(20L, 2L, "TWO", "Two",
                pe.giova.auth.RoleCode.SELLER);
        WorkerPrincipal actor = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
                7L, "shared@example.test", "Shared", "hash", true, null, true), List.of(first, second), null);

        repository.append(actor, "AUTH_PASSWORD_CHANGED", "WORKER", "7", "SUCCESS",
                null, null, "Contraseña propia actualizada");

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), parameters.capture());
        assertEquals(7L, parameters.getValue().getValue("actorId"));
        assertNull(parameters.getValue().getValue("tenantId"));
        assertNull(parameters.getValue().getValue("role"));
        assertEquals("Contraseña propia actualizada", parameters.getValue().getValue("summary"));
    }
}

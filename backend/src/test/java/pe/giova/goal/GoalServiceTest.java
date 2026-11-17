package pe.giova.goal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import pe.giova.audit.AuditService;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;
import pe.giova.goal.api.GoalDtos;

class GoalServiceTest {
    private final GoalService service = new GoalService(mock(GoalRepository.class), mock(AuditService.class));
    private final WorkerPrincipal seller = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
            6L, "seller@test.local", "Vendedor", "hash", true, RoleCode.SELLER));

    @Test
    void onlyAdminCanCreateGoal() {
        GoalDtos.Input input = new GoalDtos.Input(6L, GoalType.TASKS_COMPLETED,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), BigDecimal.TEN, null, true);

        assertThrows(SecurityException.class, () -> service.create(input, seller));
    }

    @Test
    void rejectsPeriodsLongerThan366Days() {
        GoalDtos.Input input = new GoalDtos.Input(6L, GoalType.TASKS_COMPLETED,
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2), BigDecimal.TEN, null, true);

        assertThrows(IllegalArgumentException.class, () -> service.create(input, admin()));
    }

    private static WorkerPrincipal admin() {
        return new WorkerPrincipal(new WorkerRepository.WorkerRecord(
                4L, "admin@test.local", "Administrador", "hash", true, RoleCode.ADMIN));
    }
}

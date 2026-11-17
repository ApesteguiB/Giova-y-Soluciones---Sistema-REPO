package pe.giova.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.giova.audit.AuditService;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;
import pe.giova.task.api.TaskDtos;

class TaskServiceTest {
    private final TaskRepository repository = mock(TaskRepository.class);
    private final TaskService service = new TaskService(repository, mock(AuditService.class));
    private final WorkerPrincipal seller = principal(6L, RoleCode.SELLER);
    private final WorkerPrincipal admin = principal(4L, RoleCode.ADMIN);

    @Test
    void sellerListIsRestrictedToOwnAssignedTasks() {
        when(repository.workerExists(6L)).thenReturn(true);
        when(repository.find(any(), any(), eq(6L), eq(0), eq(10)))
                .thenReturn(new TaskRepository.PageData(java.util.List.of(), 0, 0, 10, 0));

        service.find(null, null, null, 0, 10, seller);

        verify(repository).find(null, null, 6L, 0, 10);
    }

    @Test
    void adminListAllowsAnUnfilteredAssignment() {
        when(repository.find(any(), any(), eq(null), eq(0), eq(10)))
                .thenReturn(new TaskRepository.PageData(java.util.List.of(), 0, 0, 10, 0));

        service.find(null, null, null, 0, 10, admin);

        verify(repository).find(null, null, null, 0, 10);
    }

    @Test
    void sellerCannotReassignAnExistingTask() {
        when(repository.findRecord("task-id")).thenReturn(Optional.of(new TaskRepository.TaskRecord(
                "task-id", 6L, 6L, TaskStatus.OPEN, 0L)));
        when(repository.workerExists(4L)).thenReturn(true);

        TaskDtos.Update update = new TaskDtos.Update("Pendiente", null, 4L, null, null,
                TaskPriority.MEDIUM, null, 0L);

        assertThrows(SecurityException.class, () -> service.update("task-id", update, seller));
    }

    @Test
    void completingTaskAdvancesVersionAndSetsCompletionTime() {
        when(repository.findRecord("task-id")).thenReturn(Optional.of(new TaskRepository.TaskRecord(
                "task-id", 6L, 6L, TaskStatus.IN_PROGRESS, 2L)));
        when(repository.changeStatus(eq("task-id"), eq(2L), eq(TaskStatus.COMPLETED), any(Instant.class)))
                .thenReturn(1);
        when(repository.findByPublicId("task-id")).thenReturn(Optional.of(view(TaskStatus.COMPLETED, 3L)));

        TaskDtos.View result = service.changeStatus("task-id",
                new TaskDtos.StatusChange(TaskStatus.COMPLETED, 2L), seller);

        assertEquals(TaskStatus.COMPLETED, result.status());
        verify(repository).changeStatus(eq("task-id"), eq(2L), eq(TaskStatus.COMPLETED), any(Instant.class));
    }

    @Test
    void adminCanCreateTaskAssignedToAnotherWorker() {
        when(repository.workerExists(6L)).thenReturn(true);
        when(repository.insert(eq(4L), any(TaskRepository.TaskInputData.class))).thenReturn("new-task");
        when(repository.findByPublicId("new-task")).thenReturn(Optional.of(view(TaskStatus.OPEN, 0L)));

        service.create(new TaskDtos.Input("Revisar", null, 6L, null, null, TaskPriority.HIGH, null), admin);

        verify(repository).insert(eq(4L), eq(new TaskRepository.TaskInputData("Revisar", null, 6L,
                null, null, TaskPriority.HIGH, null)));
    }

    private static TaskDtos.View view(TaskStatus status, long version) {
        return new TaskDtos.View("task-id", "Tarea", null, 6L, "Vendedor", 6L, "Vendedor",
                null, null, TaskPriority.MEDIUM, status, null, null, version, Instant.now(), Instant.now());
    }

    private static WorkerPrincipal principal(long id, RoleCode role) {
        return new WorkerPrincipal(new WorkerRepository.WorkerRecord(id, role.name().toLowerCase() + "@test.local",
                role.label(), "hash", true, role));
    }
}

package pe.giova.task;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.giova.admin.ConflictException;
import pe.giova.admin.NotFoundException;
import pe.giova.audit.AuditService;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.task.api.TaskDtos;

@Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class TaskService {
    private final TaskRepository repository;
    private final AuditService audit;

    public TaskService(TaskRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    public TaskDtos.Page find(String query, String status, Long assignedToWorkerId,
                              int page, int size, WorkerPrincipal actor) {
        validatePage(page, size);
        TaskStatus normalizedStatus = TaskStatus.fromInput(status);
        Long visibleAssigned = actor.roleCode() == RoleCode.ADMIN ? assignedToWorkerId : Long.valueOf(actor.id());
        if (visibleAssigned != null && !repository.workerExists(visibleAssigned)) {
            throw new NotFoundException("Trabajador no encontrado");
        }
        TaskRepository.PageData result = repository.find(normalizeQuery(query), normalizedStatus,
                visibleAssigned, page, size);
        return new TaskDtos.Page(result.items(), result.totalItems(), result.page(), result.size(), result.totalPages());
    }

    public TaskDtos.View get(String publicId, WorkerPrincipal actor) {
        TaskDtos.View task = repository.findByPublicId(publicId)
                .orElseThrow(() -> new NotFoundException("Tarea no encontrada"));
        requireVisible(task, actor);
        return task;
    }

    @Transactional
    public TaskDtos.View create(TaskDtos.Input input, WorkerPrincipal actor) {
        TaskRepository.TaskInputData normalized = normalize(input, actor, null);
        String publicId = repository.insert(actor.id(), normalized);
        audit.success(actor, "TASK_CREATED", "TASK", publicId, "Tarea creada");
        return get(publicId, actor);
    }

    @Transactional
    public TaskDtos.View update(String publicId, TaskDtos.Update input, WorkerPrincipal actor) {
        TaskRepository.TaskRecord current = recordFor(publicId, actor);
        if (input.version() == null || input.version() != current.version()) {
            throw new ConflictException("La tarea fue modificada por otro usuario", "VERSION_CONFLICT");
        }
        TaskRepository.TaskInputData normalized = normalize(new TaskDtos.Input(input.title(), input.description(),
                input.assignedToWorkerId(), input.relatedEntityType(), input.relatedEntityId(), input.priority(), input.dueAt()),
                actor, current);
        if (repository.update(publicId, input.version(), normalized) == 0) {
            throw new ConflictException("La tarea fue modificada por otro usuario", "VERSION_CONFLICT");
        }
        audit.success(actor, "TASK_UPDATED", "TASK", publicId, "Tarea actualizada");
        return get(publicId, actor);
    }

    @Transactional
    public TaskDtos.View changeStatus(String publicId, TaskDtos.StatusChange input, WorkerPrincipal actor) {
        TaskRepository.TaskRecord current = recordFor(publicId, actor);
        TaskStatus target = TaskStatus.fromInput(input.targetStatus());
        if (current.status() == target) return get(publicId, actor);
        if (!allowed(current.status(), target)) {
            throw new ConflictException("Transición de tarea no permitida", "INVALID_STATE_TRANSITION");
        }
        if (input.version() == null || input.version() != current.version()) {
            throw new ConflictException("La tarea fue modificada por otro usuario", "VERSION_CONFLICT");
        }
        Instant completedAt = target == TaskStatus.COMPLETED ? Instant.now() : null;
        if (repository.changeStatus(publicId, input.version(), target, completedAt) == 0) {
            throw new ConflictException("La tarea fue modificada por otro usuario", "VERSION_CONFLICT");
        }
        audit.success(actor, "TASK_STATUS_CHANGED", "TASK", publicId, "Estado " + target);
        return get(publicId, actor);
    }

    public List<TaskDtos.Assignee> assignees(WorkerPrincipal actor) {
        requireAdmin(actor);
        return repository.assignees();
    }

    private TaskRepository.TaskRecord recordFor(String publicId, WorkerPrincipal actor) {
        TaskRepository.TaskRecord record = repository.findRecord(publicId)
                .orElseThrow(() -> new NotFoundException("Tarea no encontrada"));
        if (actor.roleCode() != RoleCode.ADMIN
                && record.assignedToWorkerId() != actor.id()
                && record.createdByWorkerId() != actor.id()) {
            throw new NotFoundException("Tarea no encontrada");
        }
        return record;
    }

    private static void requireVisible(TaskDtos.View task, WorkerPrincipal actor) {
        if (actor.roleCode() != RoleCode.ADMIN && task.assignedToWorkerId() != actor.id()
                && task.createdByWorkerId() != actor.id()) {
            throw new NotFoundException("Tarea no encontrada");
        }
    }

    private TaskRepository.TaskInputData normalize(TaskDtos.Input input, WorkerPrincipal actor,
                                                     TaskRepository.TaskRecord current) {
        if (input == null) throw new IllegalArgumentException("La tarea es obligatoria");
        long assigned = input.assignedToWorkerId() == null
                ? current == null ? actor.id() : current.assignedToWorkerId()
                : input.assignedToWorkerId();
        if (actor.roleCode() != RoleCode.ADMIN && current != null && assigned != current.assignedToWorkerId()) {
            throw new SecurityException("Solo ADMIN puede reasignar una tarea");
        }
        if (actor.roleCode() != RoleCode.ADMIN && current == null && assigned != actor.id()) {
            throw new SecurityException("Solo ADMIN puede asignar una tarea a otro usuario");
        }
        if (!repository.workerExists(assigned)) throw new NotFoundException("Trabajador asignado no encontrado");
        return new TaskRepository.TaskInputData(clean(input.title(), "title", 180), optional(input.description(), 1500),
                assigned, optionalUpper(input.relatedEntityType(), 60), optional(input.relatedEntityId(), 80),
                TaskPriority.fromInput(input.priority()), input.dueAt());
    }

    private static String clean(String value, String field, int max) {
        String normalized = optional(value, max);
        if (normalized == null) throw new IllegalArgumentException(field + " es obligatorio");
        return normalized;
    }

    private static String optional(String value, int max) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > max) throw new IllegalArgumentException("El texto supera " + max + " caracteres");
        return normalized;
    }

    private static String optionalUpper(String value, int max) {
        String normalized = optional(value, max);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalizeQuery(String value) {
        return optional(value, 80);
    }

    private static boolean allowed(TaskStatus current, TaskStatus target) {
        return (current == TaskStatus.OPEN && (target == TaskStatus.IN_PROGRESS
                || target == TaskStatus.COMPLETED || target == TaskStatus.CANCELLED))
                || (current == TaskStatus.IN_PROGRESS && (target == TaskStatus.COMPLETED
                || target == TaskStatus.CANCELLED));
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page/size fuera de rango");
    }

    private static void requireAdmin(WorkerPrincipal actor) {
        if (actor.roleCode() != RoleCode.ADMIN) throw new SecurityException("Se requiere rol ADMIN");
    }
}

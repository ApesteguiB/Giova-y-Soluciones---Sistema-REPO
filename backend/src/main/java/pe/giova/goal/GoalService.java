package pe.giova.goal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.giova.admin.ConflictException;
import pe.giova.admin.NotFoundException;
import pe.giova.audit.AuditService;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.goal.api.GoalDtos;

@Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class GoalService {
    private final GoalRepository repository;
    private final AuditService audit;

    public GoalService(GoalRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    public GoalDtos.Page find(Long workerId, String goalType, Boolean active, int page, int size,
                              WorkerPrincipal actor) {
        validatePage(page, size);
        Long visibleWorker = actor.roleCode() == RoleCode.ADMIN ? workerId : Long.valueOf(actor.id());
        if (visibleWorker != null && !repository.workerExists(visibleWorker)) {
            throw new NotFoundException("Trabajador no encontrado");
        }
        GoalRepository.PageData result = repository.find(visibleWorker, GoalType.fromInput(goalType), active, page, size);
        return new GoalDtos.Page(result.items(), result.totalItems(), result.page(), result.size(), result.totalPages());
    }

    public GoalDtos.View get(String publicId, WorkerPrincipal actor) {
        GoalDtos.View goal = repository.findByPublicId(publicId)
                .orElseThrow(() -> new NotFoundException("Meta no encontrada"));
        if (actor.roleCode() != RoleCode.ADMIN && goal.workerId() != actor.id()) {
            throw new NotFoundException("Meta no encontrada");
        }
        return goal;
    }

    @Transactional
    public GoalDtos.View create(GoalDtos.Input input, WorkerPrincipal actor) {
        requireAdmin(actor);
        GoalRepository.GoalInputData normalized = normalize(input);
        ensureWorker(normalized.workerId());
        ensureUnique(normalized, null);
        String publicId = repository.insert(normalized);
        audit.success(actor, "GOAL_CREATED", "GOAL", publicId, "Meta creada");
        return get(publicId, actor);
    }

    @Transactional
    public GoalDtos.View update(String publicId, GoalDtos.Update input, WorkerPrincipal actor) {
        requireAdmin(actor);
        get(publicId, actor);
        if (input.version() == null || input.version() < 0) {
            throw new ConflictException("La meta fue modificada por otro usuario", "VERSION_CONFLICT");
        }
        GoalRepository.GoalInputData normalized = normalize(input);
        ensureWorker(normalized.workerId());
        ensureUnique(normalized, publicId);
        if (repository.update(publicId, input.version(), normalized) == 0) {
            if (repository.findByPublicId(publicId).isEmpty()) throw new NotFoundException("Meta no encontrada");
            throw new ConflictException("La meta fue modificada por otro usuario", "VERSION_CONFLICT");
        }
        audit.success(actor, "GOAL_UPDATED", "GOAL", publicId, "Meta actualizada");
        return get(publicId, actor);
    }

    private GoalRepository.GoalInputData normalize(GoalDtos.Input input) {
        if (input == null) throw new IllegalArgumentException("La meta es obligatoria");
        return normalized(input.workerId(), input.goalType(), input.periodStart(), input.periodEnd(),
                input.targetValue(), input.notes(), input.active());
    }

    private GoalRepository.GoalInputData normalize(GoalDtos.Update input) {
        if (input == null) throw new IllegalArgumentException("La meta es obligatoria");
        return normalized(input.workerId(), input.goalType(), input.periodStart(), input.periodEnd(),
                input.targetValue(), input.notes(), input.active());
    }

    private static GoalRepository.GoalInputData normalized(Long workerId, GoalType goalType, LocalDate start,
                                                           LocalDate end, BigDecimal target, String notes, boolean active) {
        if (workerId == null || workerId < 1) throw new IllegalArgumentException("workerId no válido");
        if (goalType == null) throw new IllegalArgumentException("goalType es obligatorio");
        if (start == null || end == null || end.isBefore(start)) throw new IllegalArgumentException("periodo no válido");
        if (ChronoUnit.DAYS.between(start, end) > 365) throw new IllegalArgumentException("El periodo no puede superar 366 días");
        if (target == null || target.signum() <= 0) throw new IllegalArgumentException("targetValue debe ser mayor que 0");
        String cleanNotes = notes == null || notes.isBlank() ? null : notes.trim();
        if (cleanNotes != null && cleanNotes.length() > 1000) throw new IllegalArgumentException("notes supera 1000 caracteres");
        return new GoalRepository.GoalInputData(workerId, goalType, start, end, target, cleanNotes, active);
    }

    private void ensureUnique(GoalRepository.GoalInputData input, String excludedPublicId) {
        if (repository.existsBusinessKey(input.workerId(), input.goalType(), input.periodStart(), input.periodEnd(), excludedPublicId)) {
            throw new ConflictException("Ya existe una meta para ese trabajador y periodo", "GOAL_EXISTS");
        }
    }

    private void ensureWorker(long workerId) {
        if (!repository.workerExists(workerId)) throw new NotFoundException("Trabajador no encontrado");
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page/size fuera de rango");
    }

    private static void requireAdmin(WorkerPrincipal actor) {
        if (actor.roleCode() != RoleCode.ADMIN) throw new SecurityException("Se requiere rol ADMIN");
    }
}

package pe.giova.audit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pe.giova.audit.api.AuditDtos;
import pe.giova.auth.WorkerPrincipal;

@Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class AuditQueryService {
    private final AuditEventRepository repository;

    public AuditQueryService(AuditEventRepository repository) {
        this.repository = repository;
    }

    public AuditDtos.Page find(String query, String action, String result, Long actorWorkerId,
                               LocalDate from, LocalDate to, int page, int size, WorkerPrincipal actor) {
        if (actor.roleCode() != pe.giova.auth.RoleCode.ADMIN) throw new SecurityException("Se requiere rol ADMIN");
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page/size fuera de rango");
        if (from != null && to != null && to.isBefore(from)) throw new IllegalArgumentException("to no puede ser anterior a from");
        String cleanQuery = optional(query, 80);
        String cleanAction = optional(action, 60);
        String cleanResult = optional(result, 10);
        if (cleanResult != null && !cleanResult.equals("SUCCESS") && !cleanResult.equals("FAILURE")) {
            throw new IllegalArgumentException("result debe ser SUCCESS o FAILURE");
        }
        if (actorWorkerId != null && actorWorkerId < 1) throw new IllegalArgumentException("actorWorkerId no válido");
        LocalDateTime fromAt = from == null ? null : from.atStartOfDay();
        LocalDateTime toExclusive = to == null ? null : to.plusDays(1).atStartOfDay();
        AuditEventRepository.PageData resultData = repository.find(cleanQuery, cleanAction, cleanResult,
                actorWorkerId, fromAt, toExclusive, page, size);
        return new AuditDtos.Page(resultData.items(), resultData.totalItems(), resultData.page(),
                resultData.size(), resultData.totalPages());
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("El filtro supera " + max + " caracteres");
        return normalized;
    }
}

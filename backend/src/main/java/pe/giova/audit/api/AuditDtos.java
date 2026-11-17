package pe.giova.audit.api;

import java.time.Instant;
import java.util.List;

public final class AuditDtos {
    private AuditDtos() {}

    public record View(String eventId, Long actorWorkerId, String actorDisplayName,
                       String roleSnapshot, String action, String entityType, String entityId,
                       String result, String requestId, String summary, Instant createdAt) {}

    public record Page(List<View> items, long totalItems, int page, int size, int totalPages) {}
}

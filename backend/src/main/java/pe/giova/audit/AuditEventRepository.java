package pe.giova.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.audit.api.AuditDtos;
import pe.giova.auth.TenantScope;

public class AuditEventRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public AuditEventRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void append(WorkerPrincipal actor, String action, String entityType, String entityId, String result,
                       String requestId, String ipHash, String summary) {
        String safeSummary = summary == null ? null : summary.substring(0, Math.min(2000, summary.length()));
        jdbc.update("""
                INSERT INTO audit_events(tenant_id,event_id,actor_worker_id,role_snapshot,action,entity_type,entity_id,result,request_id,ip_hash,summary)
                VALUES (:tenantId,:eventId,:actorId,:role,:action,:entityType,:entityId,:result,:requestId,:ipHash,:summary)
                """, new MapSqlParameterSource().addValue("eventId", UUID.randomUUID().toString())
                .addValue("tenantId", actor == null ? null : actor.tenantId())
                .addValue("actorId", actor == null ? null : actor.id())
                // A user with several memberships has no active role until selecting a company.
                .addValue("role", actor == null || actor.roleCode() == null ? null : actor.roleCode().name())
                .addValue("action", action).addValue("entityType", entityType).addValue("entityId", entityId)
                .addValue("result", result).addValue("requestId", requestId).addValue("ipHash", ipHash).addValue("summary", safeSummary));
    }

    public PageData find(String query, String action, String result, Long actorWorkerId,
                         LocalDateTime fromAt, LocalDateTime toExclusive, int page, int size) {
        String pattern = query == null || query.isBlank() ? null : "%" + escapeLike(query.trim().toLowerCase()) + "%";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("pattern", pattern).addValue("action", action).addValue("result", result)
                .addValue("actorWorkerId", actorWorkerId).addValue("fromAt", fromAt == null ? null : Timestamp.valueOf(fromAt))
                .addValue("toExclusive", toExclusive == null ? null : Timestamp.valueOf(toExclusive))
                .addValue("pageSize", size).addValue("offset", (long) page * size);
        String where = """
                WHERE a.tenant_id=:tenantId
                  AND (:pattern IS NULL OR LOWER(CONCAT_WS(' ', a.action, a.entity_type, a.entity_id,
                       a.summary, w.display_name)) LIKE :pattern ESCAPE '\\\\')
                  AND (:action IS NULL OR a.action=:action)
                  AND (:result IS NULL OR a.result=:result)
                  AND (:actorWorkerId IS NULL OR a.actor_worker_id=:actorWorkerId)
                  AND (:fromAt IS NULL OR a.created_at >= :fromAt)
                  AND (:toExclusive IS NULL OR a.created_at < :toExclusive)
                """;
        List<AuditDtos.View> items = jdbc.query("""
                SELECT a.event_id, a.actor_worker_id, w.display_name AS actor_display_name,
                       a.role_snapshot, a.action, a.entity_type, a.entity_id, a.result,
                       a.request_id, a.summary, a.created_at
                FROM audit_events a LEFT JOIN workers w ON w.id=a.actor_worker_id
                """ + where + " ORDER BY a.created_at DESC, a.id DESC LIMIT :pageSize OFFSET :offset",
                TenantScope.withTenant(params), (rs, row) -> map(rs));
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events a LEFT JOIN workers w ON w.id=a.actor_worker_id " + where,
                TenantScope.withTenant(params), Long.class);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageData(items, total, page, size, totalPages);
    }

    private AuditDtos.View map(ResultSet rs) throws SQLException {
        long actorId = rs.getLong("actor_worker_id");
        return new AuditDtos.View(rs.getString("event_id"), rs.wasNull() ? null : actorId,
                rs.getString("actor_display_name"), rs.getString("role_snapshot"), rs.getString("action"),
                rs.getString("entity_type"), rs.getString("entity_id"), rs.getString("result"),
                rs.getString("request_id"), rs.getString("summary"), instant(rs, "created_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public record PageData(List<AuditDtos.View> items, long totalItems, int page, int size, int totalPages) {}
}

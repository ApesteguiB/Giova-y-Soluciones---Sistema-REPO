package pe.giova.goal;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import pe.giova.goal.api.GoalDtos;
import pe.giova.auth.TenantScope;

public class GoalRepository {
    private static final String SELECT = """
            SELECT g.id, g.public_id, g.worker_id, w.display_name AS worker_display_name,
                   g.goal_type, g.period_start, g.period_end, g.target_value,
                   CASE g.goal_type
                     WHEN 'QUOTATIONS_ISSUED' THEN CAST((SELECT COUNT(*) FROM quotations q
                       WHERE q.tenant_id=g.tenant_id AND q.owner_worker_id=g.worker_id AND q.status='ISSUED'
                         AND q.issued_at >= g.period_start
                         AND q.issued_at < DATE_ADD(g.period_end, INTERVAL 1 DAY)) AS DECIMAL(19,4))
                     WHEN 'QUOTATION_AMOUNT' THEN COALESCE((SELECT SUM(q.total_amount) FROM quotations q
                       WHERE q.tenant_id=g.tenant_id AND q.owner_worker_id=g.worker_id AND q.status='ISSUED'
                         AND q.issued_at >= g.period_start
                         AND q.issued_at < DATE_ADD(g.period_end, INTERVAL 1 DAY)), 0)
                     WHEN 'TASKS_COMPLETED' THEN CAST((SELECT COUNT(*) FROM tasks t
                       WHERE t.tenant_id=g.tenant_id AND t.assigned_to_worker_id=g.worker_id AND t.status='COMPLETED'
                         AND t.completed_at >= g.period_start
                         AND t.completed_at < DATE_ADD(g.period_end, INTERVAL 1 DAY)) AS DECIMAL(19,4))
                     ELSE CAST(0 AS DECIMAL(19,4))
                   END AS current_value,
                   g.notes, g.active, g.version, g.created_at, g.updated_at
            FROM goals g JOIN workers w ON w.id=g.worker_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public GoalRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageData find(Long workerId, GoalType goalType, Boolean active, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workerId", workerId)
                .addValue("goalType", goalType == null ? null : goalType.name())
                .addValue("active", active)
                .addValue("pageSize", size)
                .addValue("offset", (long) page * size);
        String where = """
                WHERE g.tenant_id=:tenantId
                  AND (:workerId IS NULL OR g.worker_id=:workerId)
                  AND (:goalType IS NULL OR g.goal_type=:goalType)
                  AND (:active IS NULL OR g.active=:active)
                """;
        List<GoalDtos.View> items = jdbc.query(SELECT + where
                + " ORDER BY g.period_end DESC, g.updated_at DESC, g.id DESC LIMIT :pageSize OFFSET :offset",
                TenantScope.withTenant(params), (rs, row) -> map(rs));
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM goals g " + where, TenantScope.withTenant(params), Long.class);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageData(items, total, page, size, totalPages);
    }

    public Optional<GoalDtos.View> findByPublicId(String publicId) {
        return jdbc.query(SELECT + " WHERE g.tenant_id=:tenantId AND g.public_id=:publicId", TenantScope.withTenant(new MapSqlParameterSource("publicId", publicId)),
                (rs, row) -> map(rs)).stream().findFirst();
    }

    public boolean workerExists(long workerId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workers w JOIN worker_memberships wm ON wm.worker_id=w.id
                JOIN tenants t ON t.id=wm.tenant_id AND t.active=TRUE
                WHERE w.id=:workerId AND w.enabled=TRUE AND wm.tenant_id=:tenantId AND wm.enabled=TRUE
                """, TenantScope.withTenant(new MapSqlParameterSource("workerId", workerId)), Long.class);
        return count != null && count > 0;
    }

    public boolean existsBusinessKey(long workerId, GoalType goalType, LocalDate periodStart,
                                     LocalDate periodEnd, String excludedPublicId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM goals
                WHERE tenant_id=:tenantId AND worker_id=:workerId AND goal_type=:goalType
                  AND period_start=:periodStart AND period_end=:periodEnd
                  AND (:excludedPublicId IS NULL OR public_id<>:excludedPublicId)
                """, new MapSqlParameterSource().addValue("workerId", workerId).addValue("goalType", goalType.name())
                .addValue("periodStart", periodStart).addValue("periodEnd", periodEnd)
                .addValue("excludedPublicId", excludedPublicId).addValue("tenantId", TenantScope.requireTenantId()), Long.class);
        return count != null && count > 0;
    }

    public String insert(GoalInputData input) {
        String publicId = UUID.randomUUID().toString();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO goals(tenant_id,public_id,worker_id,goal_type,period_start,period_end,target_value,notes,active)
                VALUES(:tenantId,:publicId,:workerId,:goalType,:periodStart,:periodEnd,:targetValue,:notes,:active)
                """, TenantScope.withTenant(params(publicId, input)), keyHolder);
        return publicId;
    }

    public int update(String publicId, long version, GoalInputData input) {
        return jdbc.update("""
                UPDATE goals SET worker_id=:workerId,goal_type=:goalType,period_start=:periodStart,
                    period_end=:periodEnd,target_value=:targetValue,notes=:notes,active=:active,
                    version=version+1,updated_at=CURRENT_TIMESTAMP(6)
                WHERE public_id=:publicId AND tenant_id=:tenantId AND version=:version
                """, TenantScope.withTenant(params(publicId, input).addValue("version", version)));
    }

    private MapSqlParameterSource params(String publicId, GoalInputData input) {
        return new MapSqlParameterSource().addValue("publicId", publicId).addValue("workerId", input.workerId())
                .addValue("goalType", input.goalType().name()).addValue("periodStart", input.periodStart())
                .addValue("periodEnd", input.periodEnd()).addValue("targetValue", input.targetValue())
                .addValue("notes", input.notes()).addValue("active", input.active());
    }

    private GoalDtos.View map(ResultSet rs) throws SQLException {
        BigDecimal target = rs.getBigDecimal("target_value");
        BigDecimal current = rs.getBigDecimal("current_value");
        BigDecimal progress = target.signum() == 0 ? BigDecimal.ZERO
                : current.multiply(BigDecimal.valueOf(100)).divide(target, 2, java.math.RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(100));
        return new GoalDtos.View(rs.getString("public_id"), rs.getLong("worker_id"),
                rs.getString("worker_display_name"), GoalType.valueOf(rs.getString("goal_type")),
                rs.getObject("period_start", LocalDate.class), rs.getObject("period_end", LocalDate.class),
                target, current, progress, rs.getString("notes"), rs.getBoolean("active"), rs.getLong("version"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record GoalInputData(long workerId, GoalType goalType, LocalDate periodStart,
                                LocalDate periodEnd, BigDecimal targetValue, String notes, boolean active) {}

    public record PageData(List<GoalDtos.View> items, long totalItems, int page, int size, int totalPages) {}
}

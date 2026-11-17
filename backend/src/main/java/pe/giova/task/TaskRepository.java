package pe.giova.task;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import pe.giova.task.api.TaskDtos;
import pe.giova.auth.TenantScope;

public class TaskRepository {
    private static final String SELECT = """
            SELECT t.id, t.public_id, t.title, t.description,
                   t.assigned_to_worker_id, aw.display_name AS assigned_display_name,
                   t.created_by_worker_id, cw.display_name AS created_display_name,
                   t.related_entity_type, t.related_entity_id, t.priority, t.status,
                   t.due_at, t.completed_at, t.version, t.created_at, t.updated_at
            FROM tasks t
            JOIN workers aw ON aw.id = t.assigned_to_worker_id
            JOIN workers cw ON cw.id = t.created_by_worker_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public TaskRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageData find(String query, TaskStatus status, Long assignedToWorkerId, int page, int size) {
        String pattern = query == null || query.isBlank() ? null : "%" + escapeLike(query.trim().toLowerCase()) + "%";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("pattern", pattern)
                .addValue("status", status == null ? null : status.name())
                .addValue("assignedToWorkerId", assignedToWorkerId)
                .addValue("pageSize", size)
                .addValue("offset", (long) page * size);
        String where = """
                WHERE t.tenant_id=:tenantId
                  AND (:pattern IS NULL OR LOWER(CONCAT_WS(' ', t.title, t.description,
                       t.related_entity_type, t.related_entity_id, aw.display_name)) LIKE :pattern ESCAPE '\\\\')
                  AND (:status IS NULL OR t.status = :status)
                  AND (:assignedToWorkerId IS NULL OR t.assigned_to_worker_id = :assignedToWorkerId)
                """;
        List<TaskDtos.View> items = jdbc.query(SELECT + where
                + " ORDER BY CASE WHEN t.status IN ('COMPLETED','CANCELLED') THEN 1 ELSE 0 END, t.due_at IS NULL, t.due_at, t.updated_at DESC, t.id DESC LIMIT :pageSize OFFSET :offset",
                TenantScope.withTenant(params), (rs, row) -> mapView(rs));
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM tasks t JOIN workers aw ON aw.id=t.assigned_to_worker_id " + where,
                TenantScope.withTenant(params), Long.class);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageData(items, total, page, size, totalPages);
    }

    // El public_id es un UUID global: sin el filtro por tenant_id, un ADMIN de
    // otra empresa podría leer una tarea ajena conociendo su identificador.
    private static final String BY_PUBLIC_ID = " WHERE t.tenant_id=:tenantId AND t.public_id=:publicId";

    public Optional<TaskDtos.View> findByPublicId(String publicId) {
        return jdbc.query(SELECT + BY_PUBLIC_ID,
                TenantScope.withTenant(new MapSqlParameterSource("publicId", publicId)), (rs, row) -> mapView(rs)).stream().findFirst();
    }

    public Optional<TaskRecord> findRecord(String publicId) {
        return jdbc.query(SELECT + BY_PUBLIC_ID,
                TenantScope.withTenant(new MapSqlParameterSource("publicId", publicId)), (rs, row) -> mapRecord(rs)).stream().findFirst();
    }

    public String insert(long createdByWorkerId, TaskInputData input) {
        String publicId = UUID.randomUUID().toString();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO tasks(tenant_id,public_id,title,description,assigned_to_worker_id,created_by_worker_id,
                    related_entity_type,related_entity_id,priority,due_at)
                VALUES(:tenantId,:publicId,:title,:description,:assignedToWorkerId,:createdByWorkerId,
                    :relatedEntityType,:relatedEntityId,:priority,:dueAt)
                """, TenantScope.withTenant(params(publicId, createdByWorkerId, input)), keyHolder);
        return publicId;
    }

    public int update(String publicId, long version, TaskInputData input) {
        return jdbc.update("""
                UPDATE tasks SET title=:title,description=:description,assigned_to_worker_id=:assignedToWorkerId,
                    related_entity_type=:relatedEntityType,related_entity_id=:relatedEntityId,priority=:priority,
                    due_at=:dueAt,version=version+1,updated_at=CURRENT_TIMESTAMP(6)
                WHERE public_id=:publicId AND tenant_id=:tenantId AND version=:version
                """, TenantScope.withTenant(params(publicId, 0, input).addValue("version", version)));
    }

    public int changeStatus(String publicId, long version, TaskStatus status, Instant completedAt) {
        return jdbc.update("""
                UPDATE tasks SET status=:status,completed_at=:completedAt,version=version+1,
                    updated_at=CURRENT_TIMESTAMP(6)
                WHERE public_id=:publicId AND tenant_id=:tenantId AND version=:version
                """, new MapSqlParameterSource().addValue("publicId", publicId).addValue("version", version)
                .addValue("status", status.name()).addValue("completedAt", completedAt == null ? null : Timestamp.from(completedAt))
                .addValue("tenantId", TenantScope.requireTenantId()));
    }

    public boolean workerExists(long workerId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workers w JOIN worker_memberships wm ON wm.worker_id=w.id
                JOIN tenants t ON t.id=wm.tenant_id AND t.active=TRUE
                WHERE w.id=:workerId AND w.enabled=TRUE AND wm.tenant_id=:tenantId AND wm.enabled=TRUE
                """, TenantScope.withTenant(new MapSqlParameterSource("workerId", workerId)), Long.class);
        return count != null && count > 0;
    }

    public List<TaskDtos.Assignee> assignees() {
        return jdbc.query("""
                SELECT w.id, w.display_name, wm.role_code FROM workers w
                JOIN worker_memberships wm ON wm.worker_id=w.id AND wm.tenant_id=:tenantId AND wm.enabled=TRUE
                JOIN tenants t ON t.id=wm.tenant_id AND t.active=TRUE
                WHERE w.enabled=TRUE
                ORDER BY w.display_name, w.id
                """, TenantScope.withTenant(new MapSqlParameterSource()), (rs, row) ->
                new TaskDtos.Assignee(rs.getLong("id"), rs.getString("display_name"), rs.getString("role_code")));
    }

    private MapSqlParameterSource params(String publicId, long createdByWorkerId, TaskInputData input) {
        return new MapSqlParameterSource().addValue("publicId", publicId)
                .addValue("title", input.title()).addValue("description", input.description())
                .addValue("assignedToWorkerId", input.assignedToWorkerId())
                .addValue("createdByWorkerId", createdByWorkerId == 0 ? null : createdByWorkerId)
                .addValue("relatedEntityType", input.relatedEntityType()).addValue("relatedEntityId", input.relatedEntityId())
                .addValue("priority", input.priority().name()).addValue("dueAt", input.dueAt() == null ? null : Timestamp.from(input.dueAt()));
    }

    private TaskDtos.View mapView(ResultSet rs) throws SQLException {
        return new TaskDtos.View(rs.getString("public_id"), rs.getString("title"), rs.getString("description"),
                rs.getLong("assigned_to_worker_id"), rs.getString("assigned_display_name"),
                rs.getLong("created_by_worker_id"), rs.getString("created_display_name"),
                rs.getString("related_entity_type"), rs.getString("related_entity_id"),
                TaskPriority.valueOf(rs.getString("priority")), TaskStatus.valueOf(rs.getString("status")),
                instant(rs, "due_at"), instant(rs, "completed_at"), rs.getLong("version"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private TaskRecord mapRecord(ResultSet rs) throws SQLException {
        TaskDtos.View view = mapView(rs);
        return new TaskRecord(view.publicId(), view.assignedToWorkerId(), view.createdByWorkerId(), view.status(), view.version());
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public record TaskInputData(String title, String description, long assignedToWorkerId,
                                String relatedEntityType, String relatedEntityId,
                                TaskPriority priority, Instant dueAt) {}

    public record TaskRecord(String publicId, long assignedToWorkerId, long createdByWorkerId,
                             TaskStatus status, long version) {}

    public record PageData(List<TaskDtos.View> items, long totalItems, int page, int size, int totalPages) {}
}

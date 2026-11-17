package pe.giova.auth;

import java.util.Optional;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

public class WorkerRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public WorkerRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<WorkerRecord> findByUsername(String username) {
        return jdbc.query("""
                SELECT id, username, display_name, password_hash, enabled, role_code, must_change_password
                FROM workers
                WHERE LOWER(username) = LOWER(:username)
                """, new MapSqlParameterSource("username", username), (rs, row) -> new WorkerRecord(
                rs.getLong("id"), rs.getString("username"), rs.getString("display_name"),
                rs.getString("password_hash"), rs.getBoolean("enabled"), roleCode(rs.getString("role_code")),
                rs.getBoolean("must_change_password")))
                .stream().findFirst();
    }

    public List<Membership> findActiveMemberships(long workerId) {
        return jdbc.query("""
                SELECT wm.id, wm.tenant_id, t.code, t.display_name, wm.role_code
                FROM worker_memberships wm
                JOIN tenants t ON t.id = wm.tenant_id
                WHERE wm.worker_id = :workerId AND wm.enabled = TRUE AND t.active = TRUE
                ORDER BY t.display_name, wm.id
                """, new MapSqlParameterSource("workerId", workerId), (rs, row) -> new Membership(
                rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("code"),
                rs.getString("display_name"), RoleCode.fromInput(rs.getString("role_code"))));
    }

    public Optional<Membership> findActiveMembership(long workerId, long membershipId) {
        return jdbc.query("""
                SELECT wm.id, wm.tenant_id, t.code, t.display_name, wm.role_code
                FROM worker_memberships wm
                JOIN tenants t ON t.id = wm.tenant_id
                WHERE wm.id = :membershipId AND wm.worker_id = :workerId
                  AND wm.enabled = TRUE AND t.active = TRUE
                """, new MapSqlParameterSource().addValue("workerId", workerId)
                .addValue("membershipId", membershipId), (rs, row) -> new Membership(
                rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("code"),
                rs.getString("display_name"), RoleCode.fromInput(rs.getString("role_code"))))
                .stream().findFirst();
    }

    public Optional<WorkerSummary> findSummaryById(long id) {
        return jdbc.query("""
                SELECT id, username, display_name, role_code
                FROM workers
                WHERE id = :id AND enabled = TRUE
                """, new MapSqlParameterSource("id", id), (rs, row) -> new WorkerSummary(
                rs.getLong("id"), rs.getString("username"), rs.getString("display_name"),
                roleCode(rs.getString("role_code")))).stream().findFirst();
    }

    public long insertIdentity(String username, String displayName, String passwordHash, RoleCode platformRole,
                               boolean mustChangePassword, Long createdByWorkerId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO workers (username, display_name, password_hash, enabled, role_code,
                                     must_change_password, password_changed_at, created_by_worker_id)
                VALUES (:username, :displayName, :passwordHash, TRUE, :roleCode,
                        :mustChangePassword, CASE WHEN :mustChangePassword THEN NULL ELSE NOW(6) END,
                        :createdByWorkerId)
                """, new MapSqlParameterSource()
                .addValue("username", username)
                .addValue("displayName", displayName)
                .addValue("passwordHash", passwordHash)
                .addValue("roleCode", platformRole == null ? null : platformRole.name())
                .addValue("mustChangePassword", mustChangePassword)
                .addValue("createdByWorkerId", createdByWorkerId), keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el id del trabajador creado");
        }
        return key.longValue();
    }

    public long insertMembership(long workerId, long tenantId, RoleCode roleCode, Long createdByWorkerId) {
        if (roleCode == null || roleCode == RoleCode.SUPERADMIN) {
            throw new IllegalArgumentException("La membresía requiere un rol de empresa");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO worker_memberships(worker_id, tenant_id, role_code, enabled, created_by_worker_id)
                VALUES (:workerId, :tenantId, :roleCode, TRUE, :createdByWorkerId)
                """, new MapSqlParameterSource().addValue("workerId", workerId).addValue("tenantId", tenantId)
                .addValue("roleCode", roleCode.name()).addValue("createdByWorkerId", createdByWorkerId), keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("No se pudo obtener la membresía creada");
        return key.longValue();
    }

    public Optional<Long> findActiveTenantId(String code) {
        Long id = jdbc.query("SELECT id FROM tenants WHERE code=:code AND active=TRUE",
                new MapSqlParameterSource("code", code), rs -> rs.next() ? rs.getLong(1) : null);
        return Optional.ofNullable(id);
    }

    public Optional<WorkerMembership> findMembership(long workerId, long tenantId) {
        return jdbc.query("""
                SELECT id, worker_id, tenant_id, role_code, enabled, version
                FROM worker_memberships WHERE worker_id=:workerId AND tenant_id=:tenantId
                """, new MapSqlParameterSource().addValue("workerId", workerId).addValue("tenantId", tenantId),
                (rs, row) -> new WorkerMembership(rs.getLong("id"), rs.getLong("worker_id"),
                        rs.getLong("tenant_id"), RoleCode.fromInput(rs.getString("role_code")),
                rs.getBoolean("enabled"), rs.getLong("version"))).stream().findFirst();
    }

    public List<TenantWorkerAccount> findTenantWorkers(long tenantId) {
        return jdbc.query("""
                SELECT w.id, w.username, w.display_name, wm.role_code, wm.enabled, wm.version
                FROM worker_memberships wm JOIN workers w ON w.id=wm.worker_id
                WHERE wm.tenant_id=:tenantId AND wm.role_code IN ('ACCOUNTANT','SELLER')
                ORDER BY w.display_name, w.id
                """, new MapSqlParameterSource("tenantId", tenantId), WorkerRepository::tenantWorker);
    }

    public Optional<TenantWorkerAccount> findTenantWorker(long tenantId, long workerId) {
        return jdbc.query("""
                SELECT w.id, w.username, w.display_name, wm.role_code, wm.enabled, wm.version
                FROM worker_memberships wm JOIN workers w ON w.id=wm.worker_id
                WHERE wm.tenant_id=:tenantId AND wm.worker_id=:workerId
                  AND wm.role_code IN ('ACCOUNTANT','SELLER')
                """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("workerId", workerId),
                WorkerRepository::tenantWorker).stream().findFirst();
    }

    public int updateTenantWorkerDisplayName(long tenantId, long workerId, String displayName,
                                             long expectedVersion) {
        return jdbc.update("""
                UPDATE workers w JOIN worker_memberships wm ON wm.worker_id=w.id
                SET w.display_name=:displayName, wm.version=wm.version+1
                WHERE wm.tenant_id=:tenantId AND wm.worker_id=:workerId
                  AND wm.role_code IN ('ACCOUNTANT','SELLER') AND wm.version=:version
                """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("workerId", workerId)
                .addValue("displayName", displayName).addValue("version", expectedVersion));
    }

    public int updateTenantWorkerEnabled(long tenantId, long workerId, boolean enabled, long expectedVersion) {
        return jdbc.update("""
                UPDATE worker_memberships
                SET enabled=:enabled, version=version+1
                WHERE tenant_id=:tenantId AND worker_id=:workerId AND version=:version
                  AND role_code IN ('ACCOUNTANT','SELLER')
                """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("workerId", workerId)
                .addValue("enabled", enabled).addValue("version", expectedVersion));
    }

    public int updateTenantWorkerPasswordForTemporary(long tenantId, long workerId, String passwordHash) {
        return jdbc.update("""
                UPDATE workers w
                SET password_hash=:passwordHash, must_change_password=TRUE, password_changed_at=NULL
                WHERE w.id=:workerId AND w.enabled=TRUE AND EXISTS (
                    SELECT 1 FROM worker_memberships wm
                    WHERE wm.worker_id=w.id AND wm.tenant_id=:tenantId
                      AND wm.role_code IN ('ACCOUNTANT','SELLER')
                )
                """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("workerId", workerId)
                .addValue("passwordHash", passwordHash));
    }

    public int updateOwnPassword(long workerId, String expectedPasswordHash, String passwordHash) {
        return jdbc.update("""
                UPDATE workers SET password_hash=:passwordHash, must_change_password=FALSE,
                    password_changed_at=NOW(6)
                WHERE id=:workerId AND enabled=TRUE AND password_hash=:expectedPasswordHash
                """, new MapSqlParameterSource().addValue("workerId", workerId)
                .addValue("expectedPasswordHash", expectedPasswordHash).addValue("passwordHash", passwordHash));
    }

    public List<AdminAccount> findTenantAdmins(long tenantId) {
        return jdbc.query("""
                SELECT w.id, w.username, w.display_name, wm.enabled, wm.version
                FROM worker_memberships wm JOIN workers w ON w.id=wm.worker_id
                WHERE wm.tenant_id=:tenantId AND wm.role_code='ADMIN'
                ORDER BY w.display_name, w.id
                """, new MapSqlParameterSource("tenantId", tenantId), (rs, row) -> new AdminAccount(
                rs.getLong("id"), rs.getString("username"), rs.getString("display_name"),
                rs.getBoolean("enabled"), rs.getLong("version")));
    }

    public int updateMembershipEnabled(long workerId, long tenantId, boolean enabled, long expectedVersion) {
        return jdbc.update("""
                UPDATE worker_memberships SET enabled=:enabled, version=version+1
                WHERE worker_id=:workerId AND tenant_id=:tenantId AND version=:version
                """, new MapSqlParameterSource().addValue("enabled", enabled).addValue("workerId", workerId)
                .addValue("tenantId", tenantId).addValue("version", expectedVersion));
    }

    public int updatePasswordForTemporary(long workerId, String passwordHash) {
        return jdbc.update("""
                UPDATE workers SET password_hash=:passwordHash, must_change_password=TRUE, password_changed_at=NULL
                WHERE id=:workerId AND enabled=TRUE
                """, new MapSqlParameterSource().addValue("passwordHash", passwordHash).addValue("workerId", workerId));
    }

    private static RoleCode roleCode(String value) {
        return value == null ? null : RoleCode.fromInput(value);
    }

    private static TenantWorkerAccount tenantWorker(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new TenantWorkerAccount(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"),
                RoleCode.fromInput(rs.getString("role_code")), rs.getBoolean("enabled"), rs.getLong("version"));
    }

    public record WorkerRecord(long id, String username, String displayName, String passwordHash, boolean enabled,
                               RoleCode roleCode, boolean mustChangePassword) {
        public WorkerRecord(long id, String username, String displayName, String passwordHash, boolean enabled,
                            RoleCode roleCode) {
            this(id, username, displayName, passwordHash, enabled, roleCode, false);
        }
    }

    public record WorkerSummary(long id, String username, String displayName, RoleCode roleCode) {
    }

    public record Membership(long id, long tenantId, String tenantCode, String tenantName, RoleCode roleCode)
            implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
    }

    public record WorkerMembership(long id, long workerId, long tenantId, RoleCode roleCode, boolean enabled,
                                   long version) {}

    public record AdminAccount(long workerId, String username, String displayName, boolean enabled, long version) {}

    public record TenantWorkerAccount(long workerId, String username, String displayName, RoleCode roleCode,
                                      boolean enabled, long version) {}
}

package pe.giova.document;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pe.giova.auth.TenantScope;

public class DocumentRepository {
    private static final String SELECT = """
            SELECT id, public_id, document_kind, storage_key, original_name, media_type, extension,
                   size_bytes, sha256_hex, created_by_worker_id, active, version, created_at, updated_at
            FROM stored_documents
            """;
    private final NamedParameterJdbcTemplate jdbc;

    public DocumentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(StoredDocument document) {
        jdbc.update("""
                INSERT INTO stored_documents
                    (tenant_id, public_id, document_kind, storage_key, original_name, media_type, extension,
                     size_bytes, sha256_hex, created_by_worker_id, active, version)
                VALUES (:tenantId, :publicId, :documentKind, :storageKey, :originalName, :mediaType, :extension,
                        :sizeBytes, :sha256Hex, :createdByWorkerId, :active, :version)
                """, TenantScope.withTenant(params(document)));
    }

    public Optional<StoredDocument> findByPublicId(String publicId) {
        return jdbc.query(SELECT + " WHERE public_id = :publicId AND tenant_id=:tenantId",
                TenantScope.withTenant(new MapSqlParameterSource("publicId", publicId)), (rs, row) -> map(rs)).stream().findFirst();
    }

    private MapSqlParameterSource params(StoredDocument document) {
        return new MapSqlParameterSource()
                .addValue("publicId", document.publicId())
                .addValue("documentKind", document.documentKind().name())
                .addValue("storageKey", document.storageKey())
                .addValue("originalName", document.originalName())
                .addValue("mediaType", document.mediaType())
                .addValue("extension", document.extension())
                .addValue("sizeBytes", document.sizeBytes())
                .addValue("sha256Hex", document.sha256Hex())
                .addValue("createdByWorkerId", document.createdByWorkerId())
                .addValue("active", document.active())
                .addValue("version", document.version());
    }

    private StoredDocument map(ResultSet rs) throws SQLException {
        return new StoredDocument(rs.getLong("id"), rs.getString("public_id"),
                DocumentKind.fromInput(rs.getString("document_kind")), rs.getString("storage_key"),
                rs.getString("original_name"), rs.getString("media_type"), rs.getString("extension"),
                rs.getLong("size_bytes"), rs.getString("sha256_hex"), rs.getLong("created_by_worker_id"),
                rs.getBoolean("active"), rs.getLong("version"), instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    public record StoredDocument(
            long id,
            String publicId,
            DocumentKind documentKind,
            String storageKey,
            String originalName,
            String mediaType,
            String extension,
            long sizeBytes,
            String sha256Hex,
            long createdByWorkerId,
            boolean active,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }
}

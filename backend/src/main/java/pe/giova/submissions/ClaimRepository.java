package pe.giova.submissions;

import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pe.giova.site.PublicSiteResolver;
import pe.giova.submissions.SubmissionDtos.ClaimRequest;
import pe.giova.submissions.SubmissionDtos.Stored;

public class ClaimRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final PublicSiteResolver siteResolver;

    public ClaimRepository(NamedParameterJdbcTemplate jdbc, PublicSiteResolver siteResolver) {
        this.jdbc = jdbc;
        this.siteResolver = siteResolver;
    }

    public Optional<Stored> findByRequestId(String requestId) {
        return jdbc.query("SELECT reference_id, request_id, payload_hash, created_at FROM claims WHERE tenant_id=:tenantId AND request_id = :requestId",
                new MapSqlParameterSource("requestId", requestId)
                        .addValue("tenantId", siteResolver.requireTenantId()),
                (rs, row) -> new Stored(rs.getString("reference_id"),
                        rs.getString("request_id"), rs.getString("payload_hash"), rs.getTimestamp("created_at").toInstant()))
                .stream().findFirst();
    }

    public void insert(String reference, String requestId, String hash, ClaimRequest request) {
        jdbc.update("""
                INSERT INTO claims
                    (tenant_id, reference_id, request_id, payload_hash, name, document_number, email, phone, address,
                     kind, good_or_service, detail, requested_solution)
                VALUES (:tenantId, :reference, :requestId, :hash, :name, :documentNumber, :email, :phone, :address,
                        :kind, :goodOrService, :detail, :requestedSolution)
                """, new MapSqlParameterSource().addValue("tenantId", siteResolver.requireTenantId())
                .addValue("reference", reference).addValue("requestId", requestId)
                .addValue("hash", hash).addValue("name", request.name().trim()).addValue("documentNumber", request.documentNumber().trim())
                .addValue("email", request.email().trim().toLowerCase()).addValue("phone", trim(request.phone()))
                .addValue("address", trim(request.address())).addValue("kind", request.kind())
                .addValue("goodOrService", trim(request.goodOrService())).addValue("detail", request.detail().trim())
                .addValue("requestedSolution", trim(request.requestedSolution())));
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

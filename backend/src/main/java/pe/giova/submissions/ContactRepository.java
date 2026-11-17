package pe.giova.submissions;

import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pe.giova.site.PublicSiteResolver;
import pe.giova.submissions.SubmissionDtos.ContactRequest;
import pe.giova.submissions.SubmissionDtos.Stored;

public class ContactRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final PublicSiteResolver siteResolver;

    public ContactRepository(NamedParameterJdbcTemplate jdbc, PublicSiteResolver siteResolver) {
        this.jdbc = jdbc;
        this.siteResolver = siteResolver;
    }

    public Optional<Stored> findByRequestId(String requestId) {
        return jdbc.query("SELECT reference_id, request_id, payload_hash, created_at FROM contact_submissions WHERE tenant_id=:tenantId AND request_id = :requestId",
                new MapSqlParameterSource("requestId", requestId)
                        .addValue("tenantId", siteResolver.requireTenantId()),
                (rs, row) -> new Stored(rs.getString("reference_id"),
                        rs.getString("request_id"), rs.getString("payload_hash"), rs.getTimestamp("created_at").toInstant()))
                .stream().findFirst();
    }

    public void insert(String reference, String requestId, String hash, ContactRequest request) {
        jdbc.update("""
                INSERT INTO contact_submissions
                    (tenant_id, reference_id, request_id, payload_hash, name, company, email, phone, subject, message)
                VALUES (:tenantId, :reference, :requestId, :hash, :name, :company, :email, :phone, :subject, :message)
                """, new MapSqlParameterSource().addValue("tenantId", siteResolver.requireTenantId())
                .addValue("reference", reference).addValue("requestId", requestId)
                .addValue("hash", hash).addValue("name", request.name().trim()).addValue("company", trim(request.company()))
                .addValue("email", request.email().trim().toLowerCase()).addValue("phone", trim(request.phone()))
                .addValue("subject", request.subject().trim()).addValue("message", request.message().trim()));
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

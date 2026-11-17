package pe.giova.submissions;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import pe.giova.submissions.SubmissionDtos.ClaimRequest;
import pe.giova.submissions.SubmissionDtos.Receipt;
import pe.giova.submissions.SubmissionDtos.Stored;

@Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class ClaimService {
    private final ClaimRepository repository;
    private final ClaimWriter writer;

    public ClaimService(ClaimRepository repository, ClaimWriter writer) {
        this.repository = repository;
        this.writer = writer;
    }

    public SubmissionResult submit(ClaimRequest request) {
        String requestId = canonicalUuid(request.requestId());
        if (request.website() != null && !request.website().isBlank()) throw new IllegalArgumentException("Solicitud inválida");
        String hash = ContactService.hash(String.join("|", request.name().trim(), request.documentNumber().trim(),
                request.email().trim().toLowerCase(), safe(request.phone()), safe(request.address()), request.kind(),
                safe(request.goodOrService()), request.detail().trim(), safe(request.requestedSolution())));
        Stored existing = repository.findByRequestId(requestId).orElse(null);
        if (existing != null) return result(existing, hash);
        String reference = UUID.randomUUID().toString();
        try {
            writer.insert(reference, requestId, hash, request);
        } catch (DuplicateKeyException exception) {
            Stored concurrent = repository.findByRequestId(requestId).orElseThrow(() -> exception);
            return result(concurrent, hash);
        }
        return new SubmissionResult(repository.findByRequestId(requestId)
                .map(stored -> new Receipt(stored.reference(), stored.registeredAt()))
                .orElseThrow(), false);
    }

    private SubmissionResult result(Stored existing, String hash) {
        if (!existing.payloadHash().equals(hash)) throw new SubmissionConflictException("requestId ya fue usado con otro contenido");
        return new SubmissionResult(new Receipt(existing.reference(), existing.registeredAt()), true);
    }

    private static String canonicalUuid(String value) {
        try { return UUID.fromString(value.trim()).toString(); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("requestId debe ser UUID"); }
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}

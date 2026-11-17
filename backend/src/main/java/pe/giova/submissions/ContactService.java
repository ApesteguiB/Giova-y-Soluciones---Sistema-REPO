package pe.giova.submissions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import pe.giova.submissions.SubmissionDtos.ContactRequest;
import pe.giova.submissions.SubmissionDtos.Receipt;
import pe.giova.submissions.SubmissionDtos.Stored;

@Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class ContactService {
    private static final Set<String> SUBJECTS = Set.of("Cotización de productos", "Tóners HP / Kyocera",
            "Equipos informáticos", "Redes y seguridad", "Servicios técnicos", "Otro");
    private final ContactRepository repository;
    private final ContactWriter writer;

    public ContactService(ContactRepository repository, ContactWriter writer) {
        this.repository = repository;
        this.writer = writer;
    }

    public SubmissionResult submit(ContactRequest request) {
        String requestId = canonicalUuid(request.requestId());
        if (request.website() != null && !request.website().isBlank()) throw new IllegalArgumentException("Solicitud inválida");
        if (!SUBJECTS.contains(request.subject().trim())) throw new IllegalArgumentException("Asunto no permitido");
        String hash = hash(String.join("|", request.name().trim(), safe(request.company()), request.email().trim().toLowerCase(),
                safe(request.phone()), request.subject().trim(), request.message().trim()));
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

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(64);
            for (byte item : digest) output.append(String.format("%02x", item));
            return output.toString();
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}

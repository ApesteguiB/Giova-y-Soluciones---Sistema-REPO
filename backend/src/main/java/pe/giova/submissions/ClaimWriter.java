package pe.giova.submissions;

import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.giova.submissions.SubmissionDtos.ClaimRequest;

@Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class ClaimWriter {
    private final ClaimRepository repository;

    public ClaimWriter(ClaimRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(String reference, String requestId, String hash, ClaimRequest request) {
        repository.insert(reference, requestId, hash, request);
    }
}

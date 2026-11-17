package pe.giova.audit;

import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import pe.giova.auth.WorkerPrincipal;

@Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class AuditService {
    private final AuditEventRepository repository;
    public AuditService(AuditEventRepository repository) { this.repository = repository; }
    public void success(WorkerPrincipal actor, String action, String entityId, String summary) {
        success(actor, action, "QUOTATION", entityId, summary);
    }
    public void success(WorkerPrincipal actor, String action, String entityType, String entityId, String summary) {
        repository.append(actor, action, entityType, entityId, "SUCCESS", null, null, summary);
    }

    public void failure(WorkerPrincipal actor, String action, String entityType, String entityId, String summary) {
        repository.append(actor, action, entityType, entityId, "FAILURE", null, null, summary);
    }
}

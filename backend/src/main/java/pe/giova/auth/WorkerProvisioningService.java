package pe.giova.auth;

import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class WorkerProvisioningService {
    private final WorkerRepository repository;
    private final PasswordEncoder passwordEncoder;

    public WorkerProvisioningService(WorkerRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public long create(String username, String displayName, String password, String role, String tenantCode,
                       boolean confirmSuperAdmin) {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String normalizedDisplayName = displayName == null ? "" : displayName.trim();
        if (!normalizedUsername.matches("(?:[a-z0-9._-]{3,80}|[a-z0-9._%+-]{1,64}@[a-z0-9.-]+\\.[a-z]{2,63})")) {
            throw new IllegalArgumentException("El usuario debe ser un identificador de 3-80 caracteres o un correo válido");
        }
        if (normalizedDisplayName.length() < 2 || normalizedDisplayName.length() > 100) {
            throw new IllegalArgumentException("El nombre visible debe tener entre 2 y 100 caracteres");
        }
        if (password == null || password.length() < 12 || password.length() > 200) {
            throw new IllegalArgumentException("La contraseña debe tener entre 12 y 200 caracteres");
        }
        RoleCode requestedRole = RoleCode.fromInput(role);
        String normalizedTenantCode = tenantCode == null ? "" : tenantCode.trim().toUpperCase(Locale.ROOT);
        if (requestedRole == RoleCode.SUPERADMIN) {
            if (!confirmSuperAdmin) throw new IllegalArgumentException("Se requiere confirmar la creación de SUPERADMIN");
            if (!normalizedTenantCode.isEmpty()) {
                throw new IllegalArgumentException("SUPERADMIN no puede tener una empresa asignada");
            }
        }
        if (requestedRole != RoleCode.SUPERADMIN && normalizedTenantCode.isEmpty()) {
            throw new IllegalArgumentException("Se requiere el código de empresa para ADMIN, ACCOUNTANT y SELLER");
        }
        try {
            Long tenantId = requestedRole == RoleCode.SUPERADMIN ? null
                    : repository.findActiveTenantId(normalizedTenantCode)
                            .orElseThrow(() -> new IllegalArgumentException("La empresa no existe o está desactivada"));
            long workerId = repository.insertIdentity(normalizedUsername, normalizedDisplayName,
                    passwordEncoder.encode(password), requestedRole == RoleCode.SUPERADMIN ? requestedRole : null,
                    false, null);
            if (tenantId != null) repository.insertMembership(workerId, tenantId, requestedRole, null);
            return workerId;
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("El usuario ya existe");
        }
    }

}

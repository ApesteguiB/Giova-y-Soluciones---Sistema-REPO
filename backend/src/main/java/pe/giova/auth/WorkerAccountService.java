package pe.giova.auth;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.giova.admin.AdminWorkerDtos;
import pe.giova.admin.ConflictException;
import pe.giova.admin.NotFoundException;
import pe.giova.audit.AuditService;

@Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class WorkerAccountService {
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int TEMPORARY_PASSWORD_LENGTH = 24;

    private final WorkerRepository workers;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();

    public WorkerAccountService(WorkerRepository workers, PasswordEncoder passwordEncoder, AuditService audit) {
        this.workers = workers;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AdminWorkerDtos.WorkerView> list(WorkerPrincipal actor) {
        long tenantId = tenantId(actor);
        return workers.findTenantWorkers(tenantId).stream().map(WorkerAccountService::view).toList();
    }

    @Transactional
    public AdminWorkerDtos.WorkerProvisioned create(AdminWorkerDtos.WorkerInput input, WorkerPrincipal actor) {
        long tenantId = tenantId(actor);
        String username = normalizeUsername(input.username());
        String displayName = normalizeDisplayName(input.displayName());
        RoleCode role = companySubaccountRole(input.roleCode());
        String temporaryPassword = temporaryPassword();
        try {
            long workerId = workers.insertIdentity(username, displayName, passwordEncoder.encode(temporaryPassword),
                    null, true, actor.id());
            workers.insertMembership(workerId, tenantId, role, actor.id());
            audit.success(actor, "ADMIN_WORKER_CREATED", "WORKER_MEMBERSHIP", Long.toString(workerId),
                    "Cuenta de rol " + role.name() + " creada en la empresa activa");
            AdminWorkerDtos.WorkerView account = workers.findTenantWorker(tenantId, workerId)
                    .map(WorkerAccountService::view)
                    .orElseThrow(() -> new IllegalStateException("No se pudo leer la cuenta creada"));
            return new AdminWorkerDtos.WorkerProvisioned(account, temporaryPassword);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("El nombre de usuario no está disponible", "USERNAME_UNAVAILABLE");
        }
    }

    @Transactional
    public AdminWorkerDtos.WorkerView update(long workerId, AdminWorkerDtos.WorkerUpdate input,
                                             WorkerPrincipal actor) {
        long tenantId = tenantId(actor);
        WorkerRepository.TenantWorkerAccount account = requireTenantWorker(tenantId, workerId);
        String displayName = normalizeDisplayName(input.displayName());
        if (account.version() != input.version()) {
            throw versionConflict();
        }
        if (account.displayName().equals(displayName)) return view(account);
        if (workers.updateTenantWorkerDisplayName(tenantId, workerId, displayName, input.version()) != 1) {
            throw versionConflict();
        }
        audit.success(actor, "ADMIN_WORKER_UPDATED", "WORKER_MEMBERSHIP", Long.toString(workerId),
                "Nombre visible actualizado");
        return WorkerAccountService.view(requireTenantWorker(tenantId, workerId));
    }

    @Transactional
    public AdminWorkerDtos.WorkerView setEnabled(long workerId, AdminWorkerDtos.WorkerStatusInput input,
                                                  WorkerPrincipal actor) {
        long tenantId = tenantId(actor);
        WorkerRepository.TenantWorkerAccount account = requireTenantWorker(tenantId, workerId);
        boolean enabled = input.enabled();
        if (!enabled && actor.id() == workerId) {
            throw new ConflictException("No puede desactivar su propia cuenta", "SELF_DISABLE_FORBIDDEN");
        }
        if (account.version() != input.version()) throw versionConflict();
        if (account.enabled() == enabled) return view(account);
        if (workers.updateTenantWorkerEnabled(tenantId, workerId, enabled, input.version()) != 1) {
            throw versionConflict();
        }
        audit.success(actor, enabled ? "ADMIN_WORKER_ENABLED" : "ADMIN_WORKER_DISABLED",
                "WORKER_MEMBERSHIP", Long.toString(workerId), enabled
                        ? "Membresía de empresa activada" : "Membresía de empresa desactivada");
        return WorkerAccountService.view(requireTenantWorker(tenantId, workerId));
    }

    @Transactional
    public AdminWorkerDtos.WorkerProvisioned resetPassword(long workerId, WorkerPrincipal actor) {
        long tenantId = tenantId(actor);
        WorkerRepository.TenantWorkerAccount account = requireTenantWorker(tenantId, workerId);
        String temporaryPassword = temporaryPassword();
        if (workers.updateTenantWorkerPasswordForTemporary(tenantId, workerId,
                passwordEncoder.encode(temporaryPassword)) != 1) {
            throw new NotFoundException("Cuenta no encontrada");
        }
        audit.success(actor, "ADMIN_WORKER_PASSWORD_RESET", "WORKER_MEMBERSHIP", Long.toString(workerId),
                "Contraseña temporal generada para una cuenta de la empresa activa");
        return new AdminWorkerDtos.WorkerProvisioned(view(account), temporaryPassword);
    }

    @Transactional
    public WorkerPrincipal changeOwnPassword(WorkerPrincipal actor, String currentPassword, String newPassword) {
        if (currentPassword == null || currentPassword.isBlank()
                || !passwordEncoder.matches(currentPassword, actor.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual no es válida");
        }
        if (newPassword == null || newPassword.length() < 12 || newPassword.length() > 200) {
            throw new IllegalArgumentException("La nueva contraseña debe tener entre 12 y 200 caracteres");
        }
        if (passwordEncoder.matches(newPassword, actor.getPassword())) {
            throw new IllegalArgumentException("La nueva contraseña debe ser distinta de la actual");
        }
        String encoded = passwordEncoder.encode(newPassword);
        if (workers.updateOwnPassword(actor.id(), actor.getPassword(), encoded) != 1) {
            throw new ConflictException("La cuenta cambió durante la operación; vuelva a iniciar sesión",
                    "PASSWORD_VERSION_CONFLICT");
        }
        WorkerPrincipal updated = actor.withPasswordChanged(encoded);
        audit.success(updated, "AUTH_PASSWORD_CHANGED", "WORKER", Long.toString(actor.id()),
                "Contraseña propia actualizada");
        return updated;
    }

    private WorkerRepository.TenantWorkerAccount requireTenantWorker(long tenantId, long workerId) {
        return workers.findTenantWorker(tenantId, workerId)
                .orElseThrow(() -> new NotFoundException("Cuenta no encontrada"));
    }

    private static long tenantId(WorkerPrincipal actor) {
        if (actor == null || actor.tenantId() == null || actor.roleCode() != RoleCode.ADMIN) {
            throw new SecurityException("Se requiere una sesión ADMIN con empresa activa");
        }
        return actor.tenantId();
    }

    private static RoleCode companySubaccountRole(String roleCode) {
        RoleCode role = RoleCode.fromInput(roleCode);
        if (role != RoleCode.ACCOUNTANT && role != RoleCode.SELLER) {
            throw new IllegalArgumentException("Solo puede crear cuentas ACCOUNTANT o SELLER");
        }
        return role;
    }

    private static String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("(?:[a-z0-9._-]{3,80}|[a-z0-9._%+-]{1,64}@[a-z0-9.-]+\\.[a-z]{2,63})")) {
            throw new IllegalArgumentException("El usuario debe ser un identificador válido o un correo electrónico");
        }
        return normalized;
    }

    private static String normalizeDisplayName(String displayName) {
        String normalized = displayName == null ? "" : displayName.trim();
        if (normalized.length() < 2 || normalized.length() > 100) {
            throw new IllegalArgumentException("El nombre visible debe tener entre 2 y 100 caracteres");
        }
        return normalized;
    }

    private String temporaryPassword() {
        StringBuilder password = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
        for (int i = 0; i < TEMPORARY_PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }

    private static ConflictException versionConflict() {
        return new ConflictException("La cuenta cambió. Actualice la lista y vuelva a intentar.",
                "MEMBERSHIP_VERSION_CONFLICT");
    }

    private static AdminWorkerDtos.WorkerView view(WorkerRepository.TenantWorkerAccount account) {
        return new AdminWorkerDtos.WorkerView(account.workerId(), account.username(), account.displayName(),
                account.roleCode().name(), account.enabled(), account.version());
    }
}

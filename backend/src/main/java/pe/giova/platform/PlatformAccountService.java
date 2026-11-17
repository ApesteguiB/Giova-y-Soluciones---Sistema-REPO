package pe.giova.platform;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.giova.admin.ConflictException;
import pe.giova.admin.NotFoundException;
import pe.giova.audit.AuditService;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;
import pe.giova.auth.WorkerRepository.AdminAccount;
import pe.giova.auth.WorkerRepository.WorkerMembership;

@Service
public class PlatformAccountService {
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int TEMPORARY_PASSWORD_LENGTH = 24;

    private final PlatformRepository tenants;
    private final WorkerRepository workers;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();

    public PlatformAccountService(PlatformRepository tenants, WorkerRepository workers,
                                  PasswordEncoder passwordEncoder, AuditService audit) {
        this.tenants = tenants;
        this.workers = workers;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public java.util.List<PlatformDtos.AdminView> listAdmins(long tenantId) {
        requireTenant(tenantId);
        return workers.findTenantAdmins(tenantId).stream().map(PlatformAccountService::view).toList();
    }

    @Transactional
    public PlatformDtos.AdminProvisioned createOrLinkAdmin(long tenantId, PlatformDtos.AdminInput input,
                                                            WorkerPrincipal actor) {
        PlatformDtos.TenantView tenant = requireActiveTenant(tenantId);
        String username = normalizeUsername(input.username());
        String displayName = input.displayName().trim();

        Optional<WorkerRepository.WorkerRecord> existing = workers.findByUsername(username);
        if (existing.isPresent()) {
            WorkerRepository.WorkerRecord worker = existing.get();
            if (!worker.enabled() || worker.roleCode() == RoleCode.SUPERADMIN) {
                throw new ConflictException("No se puede vincular esa cuenta a la empresa", "WORKER_LINK_CONFLICT");
            }
            Optional<WorkerMembership> membership = workers.findMembership(worker.id(), tenantId);
            if (membership.isPresent()) {
                WorkerMembership current = membership.get();
                if (current.roleCode() == RoleCode.ADMIN && current.enabled()) {
                    return new PlatformDtos.AdminProvisioned(
                            new PlatformDtos.AdminView(worker.id(), worker.username(), worker.displayName(), true,
                                    current.version()), false, null);
                }
                throw new ConflictException("La cuenta ya tiene una membresía en esta empresa", "MEMBERSHIP_EXISTS");
            }
            workers.insertMembership(worker.id(), tenantId, RoleCode.ADMIN, actor.id());
            audit.success(actor, "PLATFORM_ADMIN_LINKED", "TENANT_ADMIN", Long.toString(worker.id()),
                    "Cuenta ADMIN vinculada a la empresa " + tenant.code());
            return new PlatformDtos.AdminProvisioned(
                    new PlatformDtos.AdminView(worker.id(), worker.username(), worker.displayName(), true, 0),
                    false, null);
        }

        String temporaryPassword = temporaryPassword();
        long workerId;
        try {
            workerId = workers.insertIdentity(username, displayName, passwordEncoder.encode(temporaryPassword),
                    null, true, actor.id());
            workers.insertMembership(workerId, tenantId, RoleCode.ADMIN, actor.id());
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("El usuario ya existe", "USERNAME_EXISTS");
        }
        audit.success(actor, "PLATFORM_ADMIN_CREATED", "TENANT_ADMIN", Long.toString(workerId),
                "Cuenta ADMIN creada para la empresa " + tenant.code());
        return new PlatformDtos.AdminProvisioned(
                new PlatformDtos.AdminView(workerId, username, displayName, true, 0), true, temporaryPassword);
    }

    @Transactional
    public PlatformDtos.AdminView setAdminEnabled(long tenantId, long workerId,
                                                   PlatformDtos.AdminStatusInput input, WorkerPrincipal actor) {
        PlatformDtos.TenantView tenant = requireTenant(tenantId);
        WorkerMembership membership = requireAdminMembership(tenantId, workerId);
        boolean enabled = input.enabled();
        if (membership.version() != input.version()) {
            throw new ConflictException("La membresía cambió. Actualice la lista y vuelva a intentar.",
                    "MEMBERSHIP_VERSION_CONFLICT");
        }
        if (membership.enabled() != enabled
                && workers.updateMembershipEnabled(workerId, tenantId, enabled, input.version()) != 1) {
            throw new ConflictException("La membresía cambió. Actualice la lista y vuelva a intentar.",
                    "MEMBERSHIP_VERSION_CONFLICT");
        }
        audit.success(actor, enabled ? "PLATFORM_ADMIN_ENABLED" : "PLATFORM_ADMIN_DISABLED",
                "TENANT_ADMIN", Long.toString(workerId), "Empresa: " + tenant.code());
        return findAdmin(tenantId, workerId);
    }

    @Transactional
    public PlatformDtos.AdminProvisioned resetAdminPassword(long tenantId, long workerId, WorkerPrincipal actor) {
        PlatformDtos.TenantView tenant = requireTenant(tenantId);
        requireAdminMembership(tenantId, workerId);
        String temporaryPassword = temporaryPassword();
        if (workers.updatePasswordForTemporary(workerId, passwordEncoder.encode(temporaryPassword)) != 1) {
            throw new NotFoundException("Cuenta ADMIN no encontrada");
        }
        AdminAccount account = findAdminRecord(tenantId, workerId);
        audit.success(actor, "PLATFORM_ADMIN_PASSWORD_RESET", "TENANT_ADMIN", Long.toString(workerId),
                "Restablecimiento de contraseña para la empresa " + tenant.code());
        return new PlatformDtos.AdminProvisioned(view(account), false, temporaryPassword);
    }

    private PlatformDtos.TenantView requireTenant(long tenantId) {
        return tenants.findTenant(tenantId).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
    }

    private PlatformDtos.TenantView requireActiveTenant(long tenantId) {
        PlatformDtos.TenantView tenant = requireTenant(tenantId);
        if (!tenant.active()) throw new ConflictException("La empresa está desactivada", "TENANT_DISABLED");
        return tenant;
    }

    private WorkerMembership requireAdminMembership(long tenantId, long workerId) {
        WorkerMembership membership = workers.findMembership(workerId, tenantId)
                .orElseThrow(() -> new NotFoundException("Cuenta ADMIN no encontrada"));
        if (membership.roleCode() != RoleCode.ADMIN) throw new NotFoundException("Cuenta ADMIN no encontrada");
        return membership;
    }

    private PlatformDtos.AdminView findAdmin(long tenantId, long workerId) {
        return view(findAdminRecord(tenantId, workerId));
    }

    private AdminAccount findAdminRecord(long tenantId, long workerId) {
        return workers.findTenantAdmins(tenantId).stream().filter(account -> account.workerId() == workerId)
                .findFirst().orElseThrow(() -> new NotFoundException("Cuenta ADMIN no encontrada"));
    }

    private static PlatformDtos.AdminView view(AdminAccount account) {
        return new PlatformDtos.AdminView(account.workerId(), account.username(), account.displayName(),
                account.enabled(), account.version());
    }

    private String temporaryPassword() {
        StringBuilder password = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
        for (int i = 0; i < TEMPORARY_PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }

    private static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}

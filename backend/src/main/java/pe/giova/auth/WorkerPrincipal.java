package pe.giova.auth;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class WorkerPrincipal implements UserDetails {
    private static final long serialVersionUID = 1L;
    private final long id;
    private final String username;
    private final String displayName;
    private final String password;
    private final boolean enabled;
    private final boolean mustChangePassword;
    private final RoleCode roleCode;
    private final List<WorkerRepository.Membership> memberships;
    private final WorkerRepository.Membership activeMembership;

    public WorkerPrincipal(WorkerRepository.WorkerRecord worker) {
        this(worker, List.of(), null);
    }

    public WorkerPrincipal(WorkerRepository.WorkerRecord worker, List<WorkerRepository.Membership> memberships,
                           WorkerRepository.Membership activeMembership) {
        this.id = worker.id();
        this.username = worker.username();
        this.displayName = worker.displayName();
        this.password = worker.passwordHash();
        this.enabled = worker.enabled();
        this.mustChangePassword = worker.mustChangePassword();
        this.memberships = List.copyOf(memberships);
        this.activeMembership = activeMembership;
        RoleCode resolvedRole = activeMembership == null ? worker.roleCode() : activeMembership.roleCode();
        if (activeMembership != null && this.memberships.stream().noneMatch(m -> m.id() == activeMembership.id())) {
            throw new IllegalArgumentException("La membresía activa debe pertenecer a la cuenta");
        }
        if (resolvedRole != RoleCode.SUPERADMIN && activeMembership == null && !this.memberships.isEmpty()) {
            resolvedRole = null;
        }
        this.roleCode = resolvedRole;
    }

    public long id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public RoleCode roleCode() {
        return roleCode;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public Long tenantId() {
        return activeMembership == null ? null : activeMembership.tenantId();
    }

    public String tenantCode() {
        return activeMembership == null ? null : activeMembership.tenantCode();
    }

    public String tenantName() {
        return activeMembership == null ? null : activeMembership.tenantName();
    }

    public Long membershipId() {
        return activeMembership == null ? null : activeMembership.id();
    }

    public List<WorkerRepository.Membership> memberships() {
        return memberships;
    }

    public WorkerPrincipal withActiveMembership(WorkerRepository.Membership membership) {
        Objects.requireNonNull(membership, "membership");
        return new WorkerPrincipal(new WorkerRepository.WorkerRecord(id, username, displayName, password, enabled,
                null, mustChangePassword), memberships, membership);
    }

    public WorkerPrincipal withPasswordChanged(String passwordHash) {
        RoleCode platformRole = activeMembership == null ? roleCode : null;
        WorkerRepository.WorkerRecord updated = new WorkerRepository.WorkerRecord(id, username, displayName,
                passwordHash, enabled, platformRole, false);
        return new WorkerPrincipal(updated, memberships, activeMembership);
    }

    public boolean membershipSelectionRequired() {
        return roleCode == null && memberships.size() > 1;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roleCode == null ? List.of() : List.of(new SimpleGrantedAuthority(roleCode.authority()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

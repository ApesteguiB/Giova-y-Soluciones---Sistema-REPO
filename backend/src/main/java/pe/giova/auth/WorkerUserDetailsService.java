package pe.giova.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class WorkerUserDetailsService implements UserDetailsService {
    private final WorkerRepository repository;

    public WorkerUserDetailsService(WorkerRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        WorkerRepository.WorkerRecord worker = repository.findByUsername(username.trim())
                .filter(WorkerRepository.WorkerRecord::enabled)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));
        if (worker.roleCode() == RoleCode.SUPERADMIN) {
            return new WorkerPrincipal(worker, java.util.List.of(), null);
        }
        java.util.List<WorkerRepository.Membership> memberships = repository.findActiveMemberships(worker.id());
        if (memberships.isEmpty()) {
            throw new UsernameNotFoundException("Credenciales inválidas");
        }
        WorkerRepository.Membership active = memberships.size() == 1 ? memberships.get(0) : null;
        return new WorkerPrincipal(worker, memberships, active);
    }
}

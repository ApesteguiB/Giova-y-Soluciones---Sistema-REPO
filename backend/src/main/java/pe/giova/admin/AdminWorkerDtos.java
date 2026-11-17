package pe.giova.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class AdminWorkerDtos {
    private AdminWorkerDtos() {}

    public record WorkerInput(
            @NotBlank @Size(min = 3, max = 254)
            @Pattern(regexp = "(?i)(?:[a-z0-9._-]{3,80}|[a-z0-9._%+-]{1,64}@[a-z0-9.-]+\\.[a-z]{2,63})")
            String username,
            @NotBlank @Size(min = 2, max = 100) String displayName,
            @NotNull @Pattern(regexp = "ACCOUNTANT|SELLER") String roleCode) {}

    public record WorkerUpdate(
            @NotBlank @Size(min = 2, max = 100) String displayName,
            @NotNull @PositiveOrZero Long version) {}

    public record WorkerStatusInput(
            @NotNull Boolean enabled,
            @NotNull @PositiveOrZero Long version) {}

    public record WorkerView(long workerId, String username, String displayName, String roleCode,
                             boolean enabled, long version) {}

    public record WorkerProvisioned(WorkerView worker, String temporaryPassword) {}
}

package pe.giova.platform;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class PlatformDtos {
    private PlatformDtos() {}

    public record TenantInput(
            @NotBlank @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{2,29}") String code,
            @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 40) String slug,
            @NotBlank @Size(min = 2, max = 200) String legalName,
            @NotBlank @Size(min = 2, max = 120) String displayName,
            @Size(max = 20) String taxId,
            @Size(max = 255) String address,
            @Email @Size(max = 254) String contactEmail,
            @NotBlank @Pattern(regexp = "PEN|USD") String defaultCurrency,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal taxRate,
            @NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String primaryColor,
            @NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String secondaryColor,
            @NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String accentColor,
            @Size(max = 120) String pdfHeaderText) {}

    public record TenantUpdate(
            @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(min = 2, max = 200) String legalName,
            @NotBlank @Size(min = 2, max = 120) String displayName,
            @Size(max = 20) String taxId,
            @Size(max = 255) String address,
            @Email @Size(max = 254) String contactEmail,
            @NotBlank @Pattern(regexp = "PEN|USD") String defaultCurrency,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal taxRate,
            @NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String primaryColor,
            @NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String secondaryColor,
            @NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String accentColor,
            @Size(max = 120) String pdfHeaderText) {}

    public record TenantStatusInput(@NotNull Boolean active, @NotNull @PositiveOrZero Long version) {}

    public record TenantView(
            long id, String publicId, String code, String slug, String legalName, String displayName,
            String taxId, String address, String contactEmail, String defaultCurrency, BigDecimal taxRate,
            boolean active, long version, String primaryColor, String secondaryColor, String accentColor,
            String pdfHeaderText, boolean hasLogo, long adminCount) {}

    public record TenantCreation(TenantView tenant, boolean created) {}

    public record AdminInput(
            @NotBlank @Size(min = 3, max = 254)
            @Pattern(regexp = "(?:[a-z0-9._-]{3,80}|[a-z0-9._%+-]{1,64}@[a-z0-9.-]+\\.[a-z]{2,63})") String username,
            @NotBlank @Size(min = 2, max = 100) String displayName) {}

    public record AdminStatusInput(@NotNull Boolean enabled, @NotNull @PositiveOrZero Long version) {}

    public record AdminView(long workerId, String username, String displayName, boolean enabled, long version) {}

    public record AdminProvisioned(AdminView admin, boolean created, String temporaryPassword) {}
}

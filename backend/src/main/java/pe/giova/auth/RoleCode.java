package pe.giova.auth;

import java.util.Locale;

public enum RoleCode {
    ADMIN("Administrador"),
    ACCOUNTANT("Contador"),
    SELLER("Vendedor"),
    SUPERADMIN("Superadministrador");

    private final String label;

    RoleCode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String authority() {
        return "ROLE_" + name();
    }

    public static RoleCode fromInput(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El rol es obligatorio");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Rol no permitido. Use ADMIN, ACCOUNTANT, SELLER o SUPERADMIN");
        }
    }
}

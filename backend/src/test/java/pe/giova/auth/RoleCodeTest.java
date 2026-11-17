package pe.giova.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RoleCodeTest {
    @Test
    void mapsCodeToSpanishLabelAndAuthority() {
        RoleCode role = RoleCode.fromInput("seller");

        assertEquals("SELLER", role.name());
        assertEquals("Vendedor", role.label());
        assertEquals("ROLE_SELLER", role.authority());
    }

    @Test
    void rejectsUnknownRole() {
        assertThrows(IllegalArgumentException.class, () -> RoleCode.fromInput("WORKER"));
    }
}

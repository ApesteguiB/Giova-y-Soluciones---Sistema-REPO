package pe.giova.tender.enums;

import java.util.Locale;

public enum TenderMatchState {
    NEW,
    REVIEWING,
    FOLLOWING,
    DISMISSED,
    CLOSED;

    public static TenderMatchState fromInput(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Estado de coincidencia no permitido");
        }
    }
}

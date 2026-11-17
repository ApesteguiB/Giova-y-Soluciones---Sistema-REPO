package pe.giova.quotation;

import java.util.Locale;

/** Quién cerró el proceso. Distinguir esto es lo que permite aprender del rechazo. */
public enum OutcomeSource {
    /** La decisión vino de la entidad o del cliente. */
    ENTIDAD,
    /** La decisión fue nuestra: nos retiramos. */
    NOSOTROS;

    public static OutcomeSource fromInput(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("outcomeSource es obligatorio");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("outcomeSource debe ser ENTIDAD o NOSOTROS");
        }
    }
}

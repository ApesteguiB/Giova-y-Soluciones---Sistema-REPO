package pe.giova.quotation;

import java.util.Locale;

/**
 * El resultado real del proceso, separado del estado técnico del documento.
 *
 * <p>El estado (`QuotationStatus`) dice en qué punto está el papel: borrador,
 * emitida, aceptada… El resultado dice qué pasó en la calle: si la entidad nos
 * eligió, si eligió a otro, si declaró desierto el proceso o si fuimos nosotros
 * los que nos retiramos. Sin esa distinción no se puede medir nada.
 */
public enum QuotationOutcome {
    /** Publicada y esperando que la entidad decida. */
    PENDIENTE,
    /** Nos eligieron. */
    SELECCIONADA,
    /** La entidad eligió a otro proveedor. */
    NO_SELECCIONADA,
    /** La entidad no adjudicó a nadie dentro del plazo. */
    DESIERTA,
    /** La entidad rechazó o anuló el proceso. */
    RECHAZADA,
    /** Nos retiramos nosotros (precio insostenible, stock, plazo imposible). */
    ANULADA;

    /** Un cierre negativo siempre exige motivo escrito. */
    public boolean requiresReason() {
        return this != PENDIENTE && this != SELECCIONADA;
    }

    /** El proceso ya terminó: deja de contar como cotización activa. */
    public boolean isClosed() {
        return this != PENDIENTE;
    }

    /**
     * Estado del documento que corresponde a este resultado, para que la lista
     * de siempre y los indicadores nuevos no se contradigan.
     */
    public QuotationStatus matchingStatus() {
        return switch (this) {
            case PENDIENTE -> QuotationStatus.ISSUED;
            case SELECCIONADA -> QuotationStatus.ACCEPTED;
            case NO_SELECCIONADA, RECHAZADA -> QuotationStatus.REJECTED;
            case DESIERTA -> QuotationStatus.EXPIRED;
            case ANULADA -> QuotationStatus.CANCELLED;
        };
    }

    public static QuotationOutcome fromInput(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("outcome es obligatorio");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("outcome no permitido");
        }
    }
}

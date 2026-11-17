package pe.giova.quotation;

import java.util.Locale;

/**
 * Cómo llegó la oportunidad. Determina el resto del flujo: Perú Compras tiene
 * plazo corto en días hábiles y termina en OCAM; una venta privada solo necesita
 * orden de compra.
 */
public enum ProcurementType {
    /** Acuerdo Marco de Perú Compras: la entidad publica y el plazo es de 1 a 3 días hábiles. */
    PERU_COMPRAS,
    /** Contratación directa con la entidad (por debajo de 8 UIT). La validez la fija el vendedor. */
    DIRECTA,
    /** Llega por un intermediario o distribuidor para una entidad. */
    INDIRECTA,
    /** Empresa privada: no hay OCAM, solo orden de compra. */
    PRIVADA;

    /** Solo Perú Compras exige el plazo en días hábiles y admite OCAM. */
    public boolean isFrameworkAgreement() {
        return this == PERU_COMPRAS;
    }

    /** Una venta privada no pasa por OCAM. */
    public boolean allowsOcam() {
        return this != PRIVADA;
    }

    public static ProcurementType fromInput(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("procurementType es obligatorio");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "procurementType debe ser PERU_COMPRAS, DIRECTA, INDIRECTA o PRIVADA");
        }
    }
}

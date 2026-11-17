package pe.giova.delivery;

import java.util.Locale;

public enum DeliveryDocumentRole {
    SIGNED_ACT,
    PHOTO_PDF,
    OTHER_PDF;

    public static DeliveryDocumentRole fromInput(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("documentRole es obligatorio");
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("documentRole no permitido");
        }
    }
}

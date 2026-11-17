package pe.giova.warranty;

import java.util.Locale;

public enum WarrantyDocumentRole {
    CERTIFICATE_PDF,
    OTHER_PDF;

    public static WarrantyDocumentRole fromInput(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("documentRole es obligatorio");
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("documentRole no permitido");
        }
    }
}

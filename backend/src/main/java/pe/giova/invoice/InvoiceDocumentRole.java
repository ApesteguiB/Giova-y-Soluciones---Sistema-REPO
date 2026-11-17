package pe.giova.invoice;

import java.util.Locale;

public enum InvoiceDocumentRole {
    PDF, XML, OTHER;

    public static InvoiceDocumentRole fromInput(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("documentRole es obligatorio");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("documentRole debe ser PDF, XML u OTHER");
        }
    }
}

package pe.giova.document;

import java.util.Locale;

public enum DocumentKind {
    QUOTATION_PDF,
    INVOICE_PDF,
    INVOICE_XML,
    PURCHASE_ORDER_PDF,
    DELIVERY_PDF,
    WARRANTY_PDF,
    OTHER_PDF;

    public boolean isPdf() {
        return this != INVOICE_XML;
    }

    public String extension() {
        return isPdf() ? "pdf" : "xml";
    }

    public String storageSegment() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static DocumentKind fromInput(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("documentKind es obligatorio");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("documentKind no permitido");
        }
    }
}

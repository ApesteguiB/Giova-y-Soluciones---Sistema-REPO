package pe.giova.purchaseorder;

import java.util.Locale;

public enum PurchaseOrderDocumentRole {
    ORDER_PDF,
    CUSTOMER_ATTACHMENT,
    OTHER;

    public static PurchaseOrderDocumentRole fromInput(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("documentRole es obligatorio");
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("documentRole no permitido");
        }
    }
}

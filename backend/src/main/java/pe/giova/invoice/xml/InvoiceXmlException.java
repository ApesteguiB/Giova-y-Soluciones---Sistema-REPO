package pe.giova.invoice.xml;

/** El XML no se pudo leer o no trae los datos mínimos de un comprobante. */
public class InvoiceXmlException extends RuntimeException {
    public InvoiceXmlException(String message) {
        super(message);
    }

    public InvoiceXmlException(String message, Throwable cause) {
        super(message, cause);
    }
}

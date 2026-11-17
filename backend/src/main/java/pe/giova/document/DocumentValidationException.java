package pe.giova.document;

public class DocumentValidationException extends IllegalArgumentException {
    public DocumentValidationException(String message) {
        super(message);
    }

    public DocumentValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

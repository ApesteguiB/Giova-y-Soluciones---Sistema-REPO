package pe.giova.admin;

public class ConflictException extends RuntimeException {
    private final String code;

    public ConflictException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

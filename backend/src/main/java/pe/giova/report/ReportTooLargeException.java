package pe.giova.report;

public class ReportTooLargeException extends RuntimeException {
    public ReportTooLargeException(String message) {
        super(message);
    }
}

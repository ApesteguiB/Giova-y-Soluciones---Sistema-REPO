package pe.giova.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.giova.catalog.api.CatalogDtos.Problem;
import pe.giova.auth.RateLimitExceededException;
import pe.giova.admin.ConflictException;
import pe.giova.admin.NotFoundException;
import pe.giova.submissions.SubmissionConflictException;
import pe.giova.document.DocumentAccessDeniedException;
import pe.giova.document.DocumentStorageException;
import pe.giova.document.DocumentValidationException;
import pe.giova.report.ReportTooLargeException;
import pe.giova.tender.TenderProviderException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Problem> badRequest(IllegalArgumentException exception, HttpServletRequest request) {
        Problem problem = new Problem("about:blank", "Solicitud inválida", HttpStatus.BAD_REQUEST.value(),
                exception.getMessage(), "INVALID_PARAMETER");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(DocumentValidationException.class)
    public ResponseEntity<Problem> documentValidation(DocumentValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage(), "DOCUMENT_INVALID");
    }

    @ExceptionHandler(pe.giova.invoice.xml.InvoiceXmlException.class)
    public ResponseEntity<Problem> invoiceXml(pe.giova.invoice.xml.InvoiceXmlException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage(), "INVOICE_XML_INVALID");
    }

    @ExceptionHandler(DocumentAccessDeniedException.class)
    public ResponseEntity<Problem> documentAccessDenied(DocumentAccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, exception.getMessage(), "DOCUMENT_ACCESS_DENIED");
    }

    @ExceptionHandler(DocumentStorageException.class)
    public ResponseEntity<Problem> documentStorage(DocumentStorageException exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo procesar el documento", "DOCUMENT_STORAGE_ERROR");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Problem> typeMismatch(MethodArgumentTypeMismatchException exception) {
        Problem problem = new Problem("about:blank", "Solicitud inválida", HttpStatus.BAD_REQUEST.value(),
                "El parámetro " + exception.getName() + " no tiene un formato válido", "INVALID_PARAMETER");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Problem> validation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("La solicitud contiene campos inválidos");
        return problem(HttpStatus.BAD_REQUEST, detail, "VALIDATION_ERROR");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Problem> authentication(AuthenticationException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Credenciales inválidas", "AUTHENTICATION_FAILED");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Problem> rateLimit(RateLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(exception.retryAfterSeconds()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new Problem("about:blank", "Límite de solicitudes", 429, exception.getMessage(), "RATE_LIMITED"));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Problem> notFound(NotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage(), "NOT_FOUND");
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Problem> conflict(ConflictException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), exception.code());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Problem> integrity(DataIntegrityViolationException exception) {
        return problem(HttpStatus.CONFLICT, "El registro entra en conflicto con otro dato existente", "DATA_CONFLICT");
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Problem> security(SecurityException exception) {
        return problem(HttpStatus.FORBIDDEN, "La solicitud no está permitida", "ACCESS_DENIED");
    }

    @ExceptionHandler(ReportTooLargeException.class)
    public ResponseEntity<Problem> reportTooLarge(ReportTooLargeException exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), "REPORT_TOO_LARGE");
    }

    @ExceptionHandler(SubmissionConflictException.class)
    public ResponseEntity<Problem> submissionConflict(SubmissionConflictException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), "IDEMPOTENCY_CONFLICT");
    }

    @ExceptionHandler(TenderProviderException.class)
    public ResponseEntity<Problem> tenderProvider(TenderProviderException exception) {
        return problem(HttpStatus.BAD_GATEWAY, "El proveedor de licitaciones no está disponible", exception.code());
    }

    private ResponseEntity<Problem> problem(HttpStatus status, String detail, String code) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new Problem("about:blank", status.getReasonPhrase(), status.value(), detail, code));
    }
}

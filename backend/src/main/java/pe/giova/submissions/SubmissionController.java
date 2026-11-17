package pe.giova.submissions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.giova.submissions.SubmissionDtos.ClaimRequest;
import pe.giova.submissions.SubmissionDtos.ContactRequest;
import pe.giova.submissions.SubmissionDtos.Receipt;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class SubmissionController {
    private final ContactService contacts;
    private final ClaimService claims;
    private final SubmissionRateLimiter rateLimiter;

    public SubmissionController(ContactService contacts, ClaimService claims, SubmissionRateLimiter rateLimiter) {
        this.contacts = contacts;
        this.claims = claims;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/contact-submissions")
    public ResponseEntity<Receipt> contact(@Valid @RequestBody ContactRequest request, HttpServletRequest http) {
        rateLimiter.check(key(http, "contact"));
        SubmissionResult result = contacts.submit(request);
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(result.receipt());
    }

    @PostMapping("/claims")
    public ResponseEntity<Receipt> claim(@Valid @RequestBody ClaimRequest request, HttpServletRequest http) {
        rateLimiter.check(key(http, "claim"));
        SubmissionResult result = claims.submit(request);
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(result.receipt());
    }

    private static String key(HttpServletRequest request, String kind) {
        return kind + ":" + (request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr());
    }
}

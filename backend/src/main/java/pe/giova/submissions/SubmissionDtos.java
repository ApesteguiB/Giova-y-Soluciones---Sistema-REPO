package pe.giova.submissions;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class SubmissionDtos {
    private SubmissionDtos() {
    }

    public record ContactRequest(
            @NotBlank @Size(max = 36) String requestId,
            @NotBlank @Size(max = 80) String name,
            @Size(max = 100) String company,
            @NotBlank @Email @Size(max = 100) String email,
            @Size(max = 20) String phone,
            @NotBlank @Size(max = 80) String subject,
            @NotBlank @Size(min = 10, max = 1000) String message,
            @Size(max = 80) String website) {
    }

    public record ClaimRequest(
            @NotBlank @Size(max = 36) String requestId,
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 20) String documentNumber,
            @NotBlank @Email @Size(max = 100) String email,
            @Size(max = 20) String phone,
            @Size(max = 120) String address,
            @NotBlank @Pattern(regexp = "RECLAMO|QUEJA") String kind,
            @Size(max = 100) String goodOrService,
            @NotBlank @Size(min = 15, max = 1500) String detail,
            @Size(max = 800) String requestedSolution,
            @Size(max = 80) String website) {
    }

    public record Receipt(String reference, Instant registeredAt) {
    }

    record Stored(String reference, String requestId, String payloadHash, Instant registeredAt) {
    }
}

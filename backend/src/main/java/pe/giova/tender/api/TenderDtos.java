package pe.giova.tender.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import pe.giova.tender.enums.TenderMatchState;
import pe.giova.tender.enums.TenderSyncStatus;
import pe.giova.tender.enums.TenderSyncTrigger;

public final class TenderDtos {
    private TenderDtos() {}

    public record SourceStatus(String code, String baseUrl, boolean configuredEnabled, boolean integrationEnabled,
                               Instant lastSuccessfulAt, SyncRunView lastRun) {}

    public record SyncAccepted(String runId, TenderSyncStatus status) {}

    public record SyncRunView(String runId, String sourceCode, TenderSyncTrigger trigger, Long requestedBy,
                              Instant startedAt, Instant finishedAt, TenderSyncStatus status, String cursorBefore,
                              String cursorAfter, int pages, int releases, int upserts, int matches,
                              String errorCode, String errorMessage) {}

    public record ProcessView(String publicId, String ocid, String title, String description, String buyerId,
                              String buyerName, String region, List<String> classificationCodes,
                              String procurementMethod, String status, String currencyCode, BigDecimal amount,
                              Instant tenderStart, Instant tenderEnd, String officialUrl,
                              Instant latestReleaseDate, String sourceHash) {}

    public record MatchView(String publicId, ProcessView process, String profilePublicId, String profileName,
                            Long ownerWorkerId, int score, List<String> reasons, TenderMatchState state,
                            Instant matchedAt, long version) {}

    public record Detail(ProcessView process, List<ReleaseView> releases, List<MatchView> matches) {}

    public record ReleaseView(String publicId, String releaseId, String ocid, Instant publishedDate,
                              String releaseTag, String sourceUrl, String sourceHash, Instant firstSeenAt,
                              Instant lastSeenAt) {}

    public record Page(List<MatchView> items, long totalItems, int page, int size, int totalPages) {}

    public record ProfileCreate(
            @NotBlank @Size(max = 180) String name,
            @Size(max = 50) List<@NotBlank @Size(max = 120) String> includeKeywords,
            @Size(max = 50) List<@NotBlank @Size(max = 120) String> excludeKeywords,
            @Size(max = 50) List<@NotBlank @Size(max = 180) String> buyers,
            @Size(max = 50) List<@NotBlank @Size(max = 120) String> regions,
            @Size(max = 50) List<@NotBlank @Size(max = 60) String> classificationCodes,
            @DecimalMin(value = "0", inclusive = true) BigDecimal minAmount,
            @DecimalMin(value = "0", inclusive = true) BigDecimal maxAmount,
            LocalDate closingFrom,
            LocalDate closingTo,
            Boolean active) {}

    public record ProfileUpdate(
            @NotBlank @Size(max = 180) String name,
            @Size(max = 50) List<@NotBlank @Size(max = 120) String> includeKeywords,
            @Size(max = 50) List<@NotBlank @Size(max = 120) String> excludeKeywords,
            @Size(max = 50) List<@NotBlank @Size(max = 180) String> buyers,
            @Size(max = 50) List<@NotBlank @Size(max = 120) String> regions,
            @Size(max = 50) List<@NotBlank @Size(max = 60) String> classificationCodes,
            @DecimalMin(value = "0", inclusive = true) BigDecimal minAmount,
            @DecimalMin(value = "0", inclusive = true) BigDecimal maxAmount,
            LocalDate closingFrom,
            LocalDate closingTo,
            Boolean active,
            @NotNull Long version) {}

    public record ProfileView(String publicId, String name, List<String> includeKeywords,
                              List<String> excludeKeywords, List<String> buyers, List<String> regions,
                              List<String> classificationCodes, BigDecimal minAmount, BigDecimal maxAmount,
                              LocalDate closingFrom, LocalDate closingTo, boolean active, long version) {}

    public record MatchStateChange(
            @NotBlank String profilePublicId,
            @NotNull TenderMatchState targetState,
            @Size(max = 500) String note,
            @NotNull Long version) {}
}

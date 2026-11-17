package pe.giova.tender;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.giova.admin.ConflictException;
import pe.giova.admin.NotFoundException;
import pe.giova.audit.AuditService;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.tender.api.TenderDtos;
import pe.giova.tender.config.OeceProperties;
import pe.giova.tender.contract.OcdsReleasePackageParser;
import pe.giova.tender.contract.TenderHash;
import pe.giova.tender.contract.TenderJsonCodec;
import pe.giova.tender.contract.TenderUrlValidator;
import pe.giova.tender.enums.TenderMatchActionType;
import pe.giova.tender.enums.TenderMatchState;
import pe.giova.tender.enums.TenderSyncStatus;
import pe.giova.tender.enums.TenderSyncTrigger;

@Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class TenderService {
    private static final Logger log = LoggerFactory.getLogger(TenderService.class);
    private static final String SOURCE_CODE = "OECE_OCDS";
    private static final int MAX_PAGES = 1_000;
    private static final String PROCESS_PATH = "/proceso/";
    private static final int MAX_RELEASE_BYTES = 2 * 1024 * 1024;
    private final TenderRepository repository;
    private final TenderProviderClient providerClient;
    private final OeceProperties properties;
    private final TenderMatchEngine matchEngine;
    private final TenderJsonCodec jsonCodec;
    private final DataSource dataSource;
    private final ExecutorService syncExecutor;
    private final AuditService audit;
    private final TenderUrlValidator urlValidator;

    public TenderService(TenderRepository repository, TenderProviderClient providerClient,
                         OeceProperties properties, TenderMatchEngine matchEngine, TenderJsonCodec jsonCodec,
                         DataSource dataSource, ExecutorService syncExecutor, AuditService audit) {
        this.repository = repository;
        this.providerClient = providerClient;
        this.properties = properties;
        this.matchEngine = matchEngine;
        this.jsonCodec = jsonCodec;
        this.dataSource = dataSource;
        this.syncExecutor = syncExecutor;
        this.audit = audit;
        boolean local = "localhost".equalsIgnoreCase(properties.baseUrl().getHost())
                || "127.0.0.1".equals(properties.baseUrl().getHost());
        this.urlValidator = new TenderUrlValidator(properties.baseUrl(), !local);
    }

    public TenderDtos.SourceStatus sourceStatus() {
        TenderRepository.SourceRecord source = source();
        return new TenderDtos.SourceStatus(source.code(), source.baseUrl(), source.enabled(),
                properties.enabled() && source.enabled(), source.lastSuccessfulAt(),
                repository.latestRun(source.id()).map(TenderService::runView).orElse(null));
    }

    public TenderDtos.Page find(String state, String query, String buyer, String region, LocalDate from,
                                LocalDate to, Integer minScore, int page, int size) {
        validatePage(page, size);
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("El rango de cierre es inválido");
        }
        if (minScore != null && (minScore < 0 || minScore > 100)) {
            throw new IllegalArgumentException("minScore debe estar entre 0 y 100");
        }
        TenderMatchState normalizedState = TenderMatchState.fromInput(state);
        TenderRepository.PageData result = repository.findMatches(normalizedState == null ? null : normalizedState.name(),
                query, buyer, region, from, to, minScore, page, size);
        return new TenderDtos.Page(result.items().stream().map(TenderService::matchView).toList(), result.totalItems(),
                result.page(), result.size(), result.totalPages());
    }

    public TenderDtos.Detail get(String publicId) {
        TenderRepository.ProcessRecord process = repository.findProcess(publicId)
                .orElseThrow(() -> new NotFoundException("Licitación no encontrada"));
        List<TenderDtos.MatchView> matches = repository.findMatchesForProcess(process.id()).stream()
                .map(TenderService::matchView).toList();
        List<TenderDtos.ReleaseView> releases = repository.findReleasesForProcess(process.id()).stream()
                .map(TenderService::releaseView).toList();
        return new TenderDtos.Detail(processView(process), releases, matches);
    }

    public TenderDtos.SyncAccepted startSync(TenderSyncTrigger trigger, WorkerPrincipal actor) {
        if (!properties.enabled()) {
            throw new ConflictException("La sincronización OECE está deshabilitada", "TENDER_SYNC_DISABLED");
        }
        TenderRepository.SourceRecord source = source();
        if (!source.enabled()) {
            throw new ConflictException("La fuente OECE está deshabilitada", "TENDER_SYNC_DISABLED");
        }
        Optional<TenderSyncLock> lock = TenderSyncLock.tryAcquire(dataSource, "giova:tender:" + source.code());
        if (lock.isEmpty()) {
            throw new ConflictException("Ya existe una sincronización en curso", "SYNC_ALREADY_RUNNING");
        }
        String runId = UUID.randomUUID().toString();
        try {
            repository.insertRun(runId, source.id(), trigger, actor == null ? null : actor.id(), source.cursorValue());
            if (actor != null) {
                audit.success(actor, "TENDER_SYNC_STARTED", "TENDER_SYNC_RUN", runId, "Sincronización iniciada");
            }
            syncExecutor.submit(() -> executeSync(runId, source, actor, lock.get()));
            return new TenderDtos.SyncAccepted(runId, TenderSyncStatus.RUNNING);
        } catch (RejectedExecutionException exception) {
            repository.markRunFailed(runId, "TENDER_SYNC_REJECTED", "No se pudo iniciar la sincronización", 0, 0, 0, 0);
            lock.get().close();
            throw new ConflictException("No se pudo iniciar la sincronización", "TENDER_SYNC_REJECTED");
        } catch (RuntimeException exception) {
            lock.get().close();
            throw exception;
        }
    }

    public TenderDtos.SyncRunView run(String runId) {
        return repository.findRun(runId).map(TenderService::runView)
                .orElseThrow(() -> new NotFoundException("Ejecución de sincronización no encontrada"));
    }

    public List<TenderDtos.ProfileView> profiles() {
        return repository.findProfiles(false).stream().map(TenderService::profileView).toList();
    }

    @Transactional
    public TenderDtos.ProfileView createProfile(TenderDtos.ProfileCreate input, WorkerPrincipal actor) {
        requireAdmin(actor);
        TenderRepository.ProfileInput normalized = normalize(input);
        String publicId = repository.insertProfile(normalized);
        recomputeMatches();
        audit.success(actor, "TENDER_MATCH_PROFILE_CREATED", "TENDER_MATCH_PROFILE", publicId,
                "Perfil de coincidencia creado");
        return profileView(repository.findProfile(publicId).orElseThrow());
    }

    @Transactional
    public TenderDtos.ProfileView updateProfile(String publicId, TenderDtos.ProfileUpdate input,
                                                WorkerPrincipal actor) {
        requireAdmin(actor);
        TenderRepository.ProfileRecord current = repository.findProfile(publicId)
                .orElseThrow(() -> new NotFoundException("Perfil de coincidencia no encontrado"));
        if (!Objects.equals(input.version(), current.version())) {
            throw new ConflictException("El perfil fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        TenderRepository.ProfileInput normalized = normalize(input, current.active());
        if (repository.updateProfile(publicId, input.version(), normalized) == 0) {
            throw new ConflictException("El perfil fue modificado por otro usuario", "VERSION_CONFLICT");
        }
        recomputeMatches();
        audit.success(actor, "TENDER_MATCH_PROFILE_UPDATED", "TENDER_MATCH_PROFILE", publicId,
                "Perfil de coincidencia actualizado");
        return profileView(repository.findProfile(publicId).orElseThrow());
    }

    @Transactional
    public TenderDtos.MatchView changeMatchState(String processPublicId, TenderDtos.MatchStateChange input,
                                                 WorkerPrincipal actor) {
        TenderRepository.MatchRecord current = repository.findMatch(processPublicId, input.profilePublicId())
                .orElseThrow(() -> new NotFoundException("Coincidencia de licitación no encontrada"));
        if (actor.roleCode() == RoleCode.SELLER && current.ownerWorkerId() != null
                && current.ownerWorkerId() != actor.id()) {
            throw new NotFoundException("Coincidencia de licitación no encontrada");
        }
        TenderMatchState target = input.targetState();
        if (current.state() == target) {
            return matchView(current);
        }
        if (!allowed(current.state(), target)) {
            throw new ConflictException("Transición de coincidencia no permitida", "INVALID_STATE_TRANSITION");
        }
        if (!Objects.equals(input.version(), current.version())) {
            throw new ConflictException("La coincidencia fue modificada por otro usuario", "VERSION_CONFLICT");
        }
        Long owner = actor.roleCode() == RoleCode.SELLER && current.ownerWorkerId() == null ? actor.id() : null;
        if (repository.updateMatchState(current.id(), input.version(), target, owner) == 0) {
            throw new ConflictException("La coincidencia fue modificada por otro usuario", "VERSION_CONFLICT");
        }
        repository.appendMatchAction(current.id(), actor.id(), action(target).name(), cleanNote(input.note()));
        audit.success(actor, "TENDER_MATCH_STATE_CHANGED", "TENDER_MATCH", current.publicId(),
                "Estado de coincidencia actualizado");
        return repository.findMatch(processPublicId, input.profilePublicId()).map(TenderService::matchView).orElseThrow();
    }

    private void executeSync(String runId, TenderRepository.SourceRecord source, WorkerPrincipal actor,
                             TenderSyncLock lock) {
        int pages = 0;
        int releases = 0;
        int upserts = 0;
        int matches = 0;
        try {
            boolean resumedFromCursor = source.cursorValue() != null && !source.cursorValue().isBlank();
            var requestUri = providerClient.initialUri(source.cursorValue(), source.lastSuccessfulAt());
            int pageBudget = Math.min(MAX_PAGES, properties.maxPages());
            Set<String> visited = new HashSet<>();
            while (requestUri != null) {
                if (!visited.add(requestUri.toString())) {
                    throw new TenderProviderException("CONTRACT_INVALID", "La paginación del proveedor no termina", 502, false);
                }
                if (pages >= pageBudget) {
                    break;
                }
                TenderProviderClient.ProviderPage page;
                try {
                    page = providerClient.fetch(requestUri);
                } catch (TenderProviderException exception) {
                    if (!isEndOfPagination(exception, pages, resumedFromCursor)) {
                        throw exception;
                    }
                    requestUri = null;
                    log.info("Fin de paginación OECE para run {} tras {} páginas procesadas", runId, pages);
                    break;
                }
                pages++;
                List<TenderRepository.ProfileRecord> profiles = repository.findProfilesForMatching();
                for (OcdsReleasePackageParser.Release release : page.releasePackage().releases()) {
                    if (release.rawJson() == null || release.rawJson().getBytes(StandardCharsets.UTF_8).length > MAX_RELEASE_BYTES) {
                        throw new TenderProviderException("CONTRACT_INVALID", "Una release supera el límite permitido", 502, false);
                    }
                    String rawJson = release.rawJson();
                    String sourceHash = TenderHash.sha256(rawJson);
                    String officialUrl = officialUrl(release);
                    TenderRepository.ProcessInput process = processInput(release, officialUrl, sourceHash);
                    repository.upsertRelease(source.id(), new TenderRepository.ReleaseInput(
                            truncate(release.releaseId(), 255), truncate(release.ocid(), 255), release.date(),
                            truncate(release.releaseTag(), 255), truncate(officialUrl, 2048), sourceHash, rawJson));
                    long processId = repository.upsertProcess(source.id(), process);
                    upserts += 2;
                    releases++;
                    for (TenderRepository.ProfileRecord profile : profiles) {
                        TenderMatchEngine.Evaluation evaluation = matchEngine.evaluate(process.candidate(), profile.engineProfile());
                        if (!evaluation.matched()) {
                            repository.deleteUntouchedMatchForTenant(processId, profile.id(), profile.tenantId());
                            continue;
                        }
                        repository.upsertMatchForTenant(processId, profile.id(), profile.tenantId(),
                                evaluation.score(), jsonCodec.writeStrings(evaluation.reasons()));
                        matches++;
                    }
                }
                requestUri = page.nextUri();
            }
            String cursorAfter = requestUri == null ? null : truncate(requestUri.toString(), 4096);
            repository.markRunSucceeded(runId, cursorAfter, pages, releases, upserts, matches);
            repository.markSourceSuccessful(source.id(), Instant.now(), cursorAfter);
            if (actor != null) {
                audit.success(actor, "TENDER_SYNC_COMPLETED", "TENDER_SYNC_RUN", runId, "Sincronización completada");
            }
        } catch (TenderProviderException exception) {
            failSync(runId, exception.code(), pages, releases, upserts, matches, actor);
        } catch (RuntimeException exception) {
            log.warn("Fallo controlado de sincronización de licitaciones {}", runId);
            failSync(runId, "TENDER_PROVIDER_ERROR", pages, releases, upserts, matches, actor);
        } finally {
            lock.close();
        }
    }

    private void failSync(String runId, String code, int pages, int releases, int upserts, int matches,
                          WorkerPrincipal actor) {
        repository.markRunFailed(runId, code, "La sincronización no conservó cambios incompletos", pages,
                releases, upserts, matches);
        if (actor != null) {
            audit.failure(actor, "TENDER_SYNC_FAILED", "TENDER_SYNC_RUN", runId, "Sincronización fallida");
        }
    }

    static boolean isEndOfPagination(TenderProviderException exception, int pagesProcessed,
                                     boolean resumedFromCursor) {
        return "PROVIDER_NOT_FOUND".equals(exception.code()) && (pagesProcessed > 0 || resumedFromCursor);
    }

    private void recomputeMatches() {
        List<TenderRepository.ProfileRecord> profiles = repository.findProfiles(true);
        for (TenderRepository.ProcessRecord process : repository.findProcesses()) {
            for (TenderRepository.ProfileRecord profile : profiles) {
                TenderMatchEngine.Evaluation evaluation = matchEngine.evaluate(process.candidate(), profile.engineProfile());
                if (!evaluation.matched()) {
                    repository.deleteUntouchedMatch(process.id(), profile.id());
                    continue;
                }
                repository.upsertMatch(process.id(), profile.id(), evaluation.score(), jsonCodec.writeStrings(evaluation.reasons()));
            }
        }
    }

    private TenderRepository.SourceRecord source() {
        TenderRepository.SourceRecord source = repository.findSource(SOURCE_CODE)
                .orElseThrow(() -> new IllegalStateException("La fuente OECE no está configurada en V10"));
        String configuredUrl = properties.baseUrl().toString();
        if (!configuredUrl.equals(source.baseUrl())) {
            repository.updateSourceUrl(source.id(), configuredUrl);
            source = repository.findSource(SOURCE_CODE).orElse(source);
        }
        return source;
    }

    /**
     * OECE no publica {@code release.url}; el portal expone cada proceso en
     * {@code /proceso/{ocid}} sobre el mismo host configurado. El validador sigue
     * rechazando cualquier enlace ajeno a esa autoridad.
     */
    private String officialUrl(OcdsReleasePackageParser.Release release) {
        String declared = urlValidator.officialUrlOrNull(release.sourceUrl());
        if (declared != null) {
            return declared;
        }
        if (release.ocid() == null || release.ocid().isBlank()) {
            return null;
        }
        URI base = properties.baseUrl();
        String encodedOcid = URLEncoder.encode(release.ocid().trim(), StandardCharsets.UTF_8);
        return urlValidator.officialUrlOrNull(
                base.getScheme() + "://" + base.getRawAuthority() + PROCESS_PATH + encodedOcid);
    }

    private TenderRepository.ProcessInput processInput(OcdsReleasePackageParser.Release release, String officialUrl,
                                                       String sourceHash) {
        BigDecimal amount = release.amount() != null && release.amount().signum() >= 0
                ? release.amount().setScale(4, java.math.RoundingMode.HALF_UP) : null;
        String currency = release.currency() == null ? null : release.currency().trim().toUpperCase(Locale.ROOT);
        if (currency != null && !currency.matches("[A-Z]{3}")) currency = null;
        return new TenderRepository.ProcessInput(truncate(release.ocid(), 255), truncate(release.tenderTitle(), 500),
                truncate(release.tenderDescription(), 4000), truncate(release.buyerId(), 255),
                truncate(release.buyerName(), 255), truncate(release.region(), 255),
                release.classificationCodes() == null ? List.of() : release.classificationCodes().stream()
                        .map(value -> truncate(value, 60)).filter(Objects::nonNull).toList(),
                truncate(release.procurementMethod(), 100), truncate(release.tenderStatus(), 60), currency, amount,
                release.tenderStart(), release.tenderEnd(), officialUrl, release.date(), sourceHash);
    }

    private TenderRepository.ProfileInput normalize(TenderDtos.ProfileCreate input) {
        return normalize(input.name(), input.includeKeywords(), input.excludeKeywords(), input.buyers(), input.regions(),
                input.classificationCodes(), input.minAmount(), input.maxAmount(), input.closingFrom(), input.closingTo(),
                input.active() == null || input.active());
    }

    private TenderRepository.ProfileInput normalize(TenderDtos.ProfileUpdate input, boolean currentActive) {
        return normalize(input.name(), input.includeKeywords(), input.excludeKeywords(), input.buyers(), input.regions(),
                input.classificationCodes(), input.minAmount(), input.maxAmount(), input.closingFrom(), input.closingTo(),
                input.active() == null ? currentActive : input.active());
    }

    private TenderRepository.ProfileInput normalize(String name, List<String> includeKeywords, List<String> excludeKeywords,
                                                    List<String> buyers, List<String> regions, List<String> codes,
                                                    BigDecimal minAmount, BigDecimal maxAmount, LocalDate closingFrom,
                                                    LocalDate closingTo, boolean active) {
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new IllegalArgumentException("El rango de monto es inválido");
        }
        if (closingFrom != null && closingTo != null && closingTo.isBefore(closingFrom)) {
            throw new IllegalArgumentException("El rango de cierre es inválido");
        }
        return new TenderRepository.ProfileInput(clean(name, 180), normalizeTerms(includeKeywords), normalizeTerms(excludeKeywords),
                normalizeTerms(buyers), normalizeTerms(regions), normalizeTerms(codes), scale(minAmount), scale(maxAmount),
                closingFrom, closingTo, active);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private static List<String> normalizeTerms(List<String> values) {
        if (values == null) return List.of();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (!result.contains(normalized)) result.add(normalized);
        }
        return List.copyOf(result);
    }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("El nombre es obligatorio");
        return truncate(value.trim(), max);
    }

    private static String cleanNote(String value) {
        return value == null || value.isBlank() ? null : truncate(value.trim(), 500);
    }

    private static String truncate(String value, int max) {
        return value == null ? null : value.length() <= max ? value : value.substring(0, max);
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page/size fuera de rango");
    }

    private static void requireAdmin(WorkerPrincipal actor) {
        if (actor.roleCode() != RoleCode.ADMIN) throw new SecurityException("Se requiere rol ADMIN");
    }

    private static boolean allowed(TenderMatchState current, TenderMatchState target) {
        return current == TenderMatchState.NEW && target == TenderMatchState.REVIEWING
                || current == TenderMatchState.REVIEWING && (target == TenderMatchState.FOLLOWING
                || target == TenderMatchState.DISMISSED || target == TenderMatchState.CLOSED)
                || current == TenderMatchState.FOLLOWING && target == TenderMatchState.CLOSED
                || current == TenderMatchState.DISMISSED && target == TenderMatchState.CLOSED;
    }

    private static TenderMatchActionType action(TenderMatchState target) {
        return switch (target) {
            case REVIEWING -> TenderMatchActionType.REVIEW;
            case FOLLOWING -> TenderMatchActionType.FOLLOW;
            case DISMISSED -> TenderMatchActionType.DISMISS;
            case CLOSED -> TenderMatchActionType.CLOSE;
            case NEW -> throw new IllegalArgumentException("No se puede volver a NEW");
        };
    }

    private static TenderDtos.ProcessView processView(TenderRepository.ProcessRecord process) {
        return new TenderDtos.ProcessView(process.publicId(), process.ocid(), process.title(), process.description(),
                process.buyerId(), process.buyerName(), process.region(), process.classificationCodes(),
                process.procurementMethod(), process.status(), process.currencyCode(), process.amount(),
                process.tenderStart(), process.tenderEnd(), process.officialUrl(), process.latestReleaseDate(), process.sourceHash());
    }

    private static TenderDtos.MatchView matchView(TenderRepository.MatchRecord match) {
        return new TenderDtos.MatchView(match.publicId(), new TenderDtos.ProcessView(match.processPublicId(), match.ocid(),
                match.title(), match.description(), match.buyerId(), match.buyerName(), match.region(),
                match.classificationCodes(), match.procurementMethod(), match.status(), match.currencyCode(), match.amount(),
                match.tenderStart(), match.tenderEnd(), match.officialUrl(), match.latestReleaseDate(), match.sourceHash()),
                match.profilePublicId(), match.profileName(), match.ownerWorkerId(), match.score(), match.reasons(),
                match.state(), match.matchedAt(), match.version());
    }

    private static TenderDtos.ReleaseView releaseView(TenderRepository.ReleaseRecord release) {
        return new TenderDtos.ReleaseView(release.publicId(), release.releaseId(), release.ocid(), release.publishedDate(),
                release.releaseTag(), release.sourceUrl(), release.sourceHash(), release.firstSeenAt(), release.lastSeenAt());
    }

    private static TenderDtos.ProfileView profileView(TenderRepository.ProfileRecord profile) {
        return new TenderDtos.ProfileView(profile.publicId(), profile.name(), profile.includeKeywords(),
                profile.excludeKeywords(), profile.buyers(), profile.regions(), profile.classificationCodes(),
                profile.minAmount(), profile.maxAmount(), profile.closingFrom(), profile.closingTo(), profile.active(), profile.version());
    }

    private static TenderDtos.SyncRunView runView(TenderRepository.SyncRunRecord run) {
        return new TenderDtos.SyncRunView(run.publicId(), run.sourceCode(), run.trigger(), run.requestedBy(), run.startedAt(),
                run.finishedAt(), run.status(), run.cursorBefore(), run.cursorAfter(), run.pages(), run.releases(),
                run.upserts(), run.matches(), run.errorCode(), run.errorMessage());
    }
}

package pe.giova.tender;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.tender.api.TenderDtos;
import pe.giova.tender.enums.TenderSyncTrigger;

@RestController
@RequestMapping("/api/v1/tenders")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class TenderController {
    private final TenderService service;

    public TenderController(TenderService service) {
        this.service = service;
    }

    @GetMapping("/source-status")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public TenderDtos.SourceStatus sourceStatus() {
        return service.sourceStatus();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public TenderDtos.Page list(@RequestParam(required = false) String state,
                               @RequestParam(required = false) String q,
                               @RequestParam(required = false) String buyer,
                               @RequestParam(required = false) String region,
                               @RequestParam(required = false) LocalDate from,
                               @RequestParam(required = false) LocalDate to,
                               @RequestParam(required = false) Integer minScore,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size) {
        return service.find(state, q, buyer, region, from, to, minScore, page, size);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public TenderDtos.Detail get(@PathVariable String publicId) {
        return service.get(publicId);
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<TenderDtos.SyncAccepted> sync(Authentication authentication) {
        TenderDtos.SyncAccepted accepted = service.startSync(TenderSyncTrigger.MANUAL, principal(authentication));
        return ResponseEntity.accepted().location(URI.create("/api/v1/tenders/sync-runs/" + accepted.runId())).body(accepted);
    }

    @GetMapping("/sync-runs/{runId}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public TenderDtos.SyncRunView run(@PathVariable String runId) {
        return service.run(runId);
    }

    @GetMapping("/match-profiles")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SELLER')")
    public List<TenderDtos.ProfileView> profiles() {
        return service.profiles();
    }

    @PostMapping("/match-profiles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TenderDtos.ProfileView> createProfile(@Valid @RequestBody TenderDtos.ProfileCreate input,
                                                                 Authentication authentication) {
        TenderDtos.ProfileView profile = service.createProfile(input, principal(authentication));
        return ResponseEntity.created(URI.create("/api/v1/tenders/match-profiles/" + profile.publicId())).body(profile);
    }

    @PutMapping("/match-profiles/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    public TenderDtos.ProfileView updateProfile(@PathVariable String publicId,
                                                @Valid @RequestBody TenderDtos.ProfileUpdate input,
                                                Authentication authentication) {
        return service.updateProfile(publicId, input, principal(authentication));
    }

    @PostMapping("/{publicId}/match-state")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public TenderDtos.MatchView changeState(@PathVariable String publicId,
                                            @Valid @RequestBody TenderDtos.MatchStateChange input,
                                            Authentication authentication) {
        return service.changeMatchState(publicId, input, principal(authentication));
    }

    private static WorkerPrincipal principal(Authentication authentication) {
        return (WorkerPrincipal) authentication.getPrincipal();
    }
}

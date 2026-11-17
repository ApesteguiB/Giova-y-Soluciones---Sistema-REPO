package pe.giova.platform;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.document.TenantLogoStorage;

@RestController
@RequestMapping("/api/v1/platform")
@Validated
@PreAuthorize("hasRole('SUPERADMIN')")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class PlatformController {
    private static final long MAX_LOGO_BYTES = 5L * 1024 * 1024;
    private final PlatformService platform;
    private final PlatformAccountService accounts;

    public PlatformController(PlatformService platform, PlatformAccountService accounts) {
        this.platform = platform;
        this.accounts = accounts;
    }

    @GetMapping("/tenants")
    public ResponseEntity<?> listTenants() {
        return noStore().body(platform.listTenants());
    }

    @PostMapping("/tenants")
    public ResponseEntity<PlatformDtos.TenantView> createTenant(@Valid @RequestBody PlatformDtos.TenantInput input,
                                                                 @AuthenticationPrincipal WorkerPrincipal actor) {
        PlatformDtos.TenantCreation result = platform.createTenant(input, actor);
        return noStore(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.tenant());
    }

    @PutMapping("/tenants/{tenantId}")
    public ResponseEntity<PlatformDtos.TenantView> updateTenant(@PathVariable long tenantId,
            @Valid @RequestBody PlatformDtos.TenantUpdate input, @AuthenticationPrincipal WorkerPrincipal actor) {
        return noStore().body(platform.updateTenant(tenantId, input, actor));
    }

    @PatchMapping("/tenants/{tenantId}/status")
    public ResponseEntity<PlatformDtos.TenantView> setTenantActive(@PathVariable long tenantId,
            @Valid @RequestBody PlatformDtos.TenantStatusInput input, @AuthenticationPrincipal WorkerPrincipal actor) {
        return noStore().body(platform.setTenantActive(tenantId, input, actor));
    }

    @PutMapping(value = "/tenants/{tenantId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlatformDtos.TenantView> storeLogo(@PathVariable long tenantId,
            @RequestPart("file") MultipartFile file, @AuthenticationPrincipal WorkerPrincipal actor)
            throws java.io.IOException {
        if (file.isEmpty() || file.getSize() > MAX_LOGO_BYTES) {
            throw new IllegalArgumentException("El logo debe ser una imagen PNG o JPEG válida de hasta 5 MiB");
        }
        return noStore().body(platform.storeLogo(tenantId, file.getBytes(), actor));
    }

    @GetMapping("/tenants/{tenantId}/logo")
    public ResponseEntity<byte[]> readLogo(@PathVariable long tenantId) {
        TenantLogoStorage.Logo logo = platform.readLogo(tenantId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(logo.mediaType()))
                .contentLength(logo.bytes().length).header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore()).body(logo.bytes());
    }

    @GetMapping("/tenants/{tenantId}/admins")
    public ResponseEntity<?> listAdmins(@PathVariable long tenantId) {
        return noStore().body(accounts.listAdmins(tenantId));
    }

    @PostMapping("/tenants/{tenantId}/admins")
    public ResponseEntity<PlatformDtos.AdminProvisioned> createOrLinkAdmin(@PathVariable long tenantId,
            @Valid @RequestBody PlatformDtos.AdminInput input, @AuthenticationPrincipal WorkerPrincipal actor) {
        PlatformDtos.AdminProvisioned result = accounts.createOrLinkAdmin(tenantId, input, actor);
        return noStore(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result);
    }

    @PatchMapping("/tenants/{tenantId}/admins/{workerId}/status")
    public ResponseEntity<PlatformDtos.AdminView> setAdminEnabled(@PathVariable long tenantId,
            @PathVariable long workerId, @Valid @RequestBody PlatformDtos.AdminStatusInput input,
            @AuthenticationPrincipal WorkerPrincipal actor) {
        return noStore().body(accounts.setAdminEnabled(tenantId, workerId, input, actor));
    }

    @PostMapping("/tenants/{tenantId}/admins/{workerId}/password-reset")
    public ResponseEntity<PlatformDtos.AdminProvisioned> resetAdminPassword(@PathVariable long tenantId,
            @PathVariable long workerId, @AuthenticationPrincipal WorkerPrincipal actor) {
        return noStore().body(accounts.resetAdminPassword(tenantId, workerId, actor));
    }

    private static ResponseEntity.BodyBuilder noStore() {
        return noStore(HttpStatus.OK);
    }

    private static ResponseEntity.BodyBuilder noStore(HttpStatus status) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache");
    }
}

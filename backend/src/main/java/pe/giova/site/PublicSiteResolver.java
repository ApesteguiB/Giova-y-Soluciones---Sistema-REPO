package pe.giova.site;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.giova.admin.NotFoundException;

/**
 * Decide a qué empresa pertenece una petición pública.
 *
 * <p>El origen de verdad es el nombre de host: {@code giova.pe} y
 * {@code solucionesti.pe} apuntan al mismo despliegue y cada uno recibe su
 * propio sitio. El parámetro {@code ?empresa=} existe solo para probar en
 * local y se ignora en cuanto el host no es de bucle invertido, así que
 * nadie puede cambiar de empresa desde internet.
 */
public class PublicSiteResolver {
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "0.0.0.0",
            "::1", "[::1]", "host.docker.internal");
    private static final long CACHE_TTL_MILLIS = 300_000L;
    private static final int CACHE_MAX_ENTRIES = 256;

    private final PublicSiteRepository repository;
    private final String defaultTenantCode;
    private final boolean allowLocalOverride;
    private final Map<String, CacheEntry> hostCache = new ConcurrentHashMap<>();

    public PublicSiteResolver(PublicSiteRepository repository, String defaultTenantCode,
                              boolean allowLocalOverride) {
        this.repository = repository;
        this.defaultTenantCode = defaultTenantCode == null || defaultTenantCode.isBlank()
                ? "GIOVA" : defaultTenantCode.trim();
        this.allowLocalOverride = allowLocalOverride;
    }

    public long requireTenantId() {
        return currentTenantId().orElseThrow(() -> new NotFoundException(
                "No hay un sitio público configurado para este dominio"));
    }

    public Optional<Long> currentTenantId() {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            String host = normalizeHost(request.getServerName());
            if (allowLocalOverride && LOOPBACK_HOSTS.contains(host)) {
                Optional<Long> override = override(request);
                if (override.isPresent()) {
                    return override;
                }
            }
            Optional<Long> byHost = lookupHost(host);
            if (byHost.isPresent()) {
                return byHost;
            }
            if (host.startsWith("www.") && host.length() > 4) {
                Optional<Long> withoutWww = lookupHost(host.substring(4));
                if (withoutWww.isPresent()) {
                    return withoutWww;
                }
            }
        }
        return lookupCode(defaultTenantCode);
    }

    /** Invalida la caché de dominios; se usa al dar de alta o cambiar un sitio. */
    public void clearCache() {
        hostCache.clear();
    }

    private Optional<Long> override(HttpServletRequest request) {
        String value = request.getParameter("empresa");
        if (value == null || value.isBlank() || value.length() > 40 || !value.matches("[A-Za-z0-9_-]+")) {
            return Optional.empty();
        }
        return lookupCode(value);
    }

    private Optional<Long> lookupCode(String value) {
        return repository.findTenantIdByCodeOrSlug(value);
    }

    private Optional<Long> lookupHost(String host) {
        if (host.isEmpty()) {
            return Optional.empty();
        }
        CacheEntry cached = hostCache.get(host);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.storedAt() < CACHE_TTL_MILLIS) {
            return Optional.ofNullable(cached.tenantId());
        }
        Optional<Long> resolved = repository.findTenantIdByHostname(host);
        // Un atacante puede enviar cualquier cabecera Host; la caché se limpia
        // antes de crecer para que no sirva como vía de consumo de memoria.
        if (hostCache.size() >= CACHE_MAX_ENTRIES) {
            hostCache.clear();
        }
        hostCache.put(host, new CacheEntry(resolved.orElse(null), now));
        return resolved;
    }

    static String normalizeHost(String serverName) {
        if (serverName == null) {
            return "";
        }
        String host = serverName.trim().toLowerCase(Locale.ROOT);
        int colon = host.lastIndexOf(':');
        if (colon > -1 && host.indexOf(':') == colon) {
            host = host.substring(0, colon);
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host;
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }

    private record CacheEntry(Long tenantId, long storedAt) {}
}

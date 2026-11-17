package pe.giova.tender.config;

import java.net.URI;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "giova.oece")
public record OeceProperties(
        boolean enabled,
        URI baseUrl,
        String syncCron,
        int connectTimeoutSeconds,
        int readTimeoutSeconds,
        int pageSize,
        int maxPages) {

    public OeceProperties {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(syncCron, "syncCron");
        if (connectTimeoutSeconds < 1 || readTimeoutSeconds < 1) {
            throw new IllegalArgumentException("Los timeouts OECE deben ser positivos");
        }
        if (pageSize < 1 || pageSize > 500) {
            throw new IllegalArgumentException("El tamaño de página OECE debe estar entre 1 y 500");
        }
        if (maxPages < 1 || maxPages > 1_000) {
            throw new IllegalArgumentException("El máximo de páginas OECE debe estar entre 1 y 1000");
        }
    }
}

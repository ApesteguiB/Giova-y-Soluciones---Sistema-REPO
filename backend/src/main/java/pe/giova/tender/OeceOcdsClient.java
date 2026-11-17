package pe.giova.tender;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import pe.giova.tender.config.OeceProperties;
import pe.giova.tender.contract.OcdsReleasePackageParser;
import pe.giova.tender.contract.TenderUrlValidator;

public final class OeceOcdsClient implements TenderProviderClient {
    private static final int MAX_RETRIES = 3;
    private static final int MAX_RESPONSE_BYTES = 10 * 1024 * 1024;
    private final OeceProperties properties;
    private final OcdsReleasePackageParser parser;
    private final TenderUrlValidator urlValidator;
    private final HttpClient httpClient;

    public OeceOcdsClient(OeceProperties properties, OcdsReleasePackageParser parser) {
        this.properties = properties;
        this.parser = parser;
        boolean localTestHost = properties.baseUrl().getHost() != null
                && (properties.baseUrl().getHost().equalsIgnoreCase("localhost")
                || properties.baseUrl().getHost().equals("127.0.0.1"));
        this.urlValidator = new TenderUrlValidator(properties.baseUrl(), !localTestHost);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public URI initialUri(String cursor, Instant lastSuccessfulAt) {
        if (cursor != null && !cursor.isBlank()) {
            return urlValidator.nextUri(cursor, properties.baseUrl());
        }
        String separator = properties.baseUrl().getQuery() == null ? "?" : "&";
        String limit = URLEncoder.encode(Integer.toString(properties.pageSize()), StandardCharsets.UTF_8);
        String sinceValue = lastSuccessfulAt == null
                ? Instant.now().minus(Duration.ofHours(24)).toString()
                : lastSuccessfulAt.minus(Duration.ofHours(24)).toString();
        String since = URLEncoder.encode(sinceValue, StandardCharsets.UTF_8);
        return URI.create(properties.baseUrl() + separator + "limit=" + limit + "&since=" + since);
    }

    @Override
    public ProviderPage fetch(URI uri) {
        URI safeUri = urlValidator.nextUri(uri.toString(), properties.baseUrl());
        TenderProviderException lastFailure = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(safeUri)
                        .timeout(Duration.ofSeconds(properties.readTimeoutSeconds()))
                        .header("Accept", "application/json")
                        .header("User-Agent", "GIOVA/1.0 (OECE OCDS client)")
                        .GET()
                        .build();
                HttpResponse<InputStream> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return parsePage(new String(readLimited(body), StandardCharsets.UTF_8), safeUri);
                    }
                    lastFailure = statusFailure(response);
                }
                if (!lastFailure.retryable() || attempt == MAX_RETRIES) {
                    throw lastFailure;
                }
                sleepBeforeRetry(attempt, retryAfterMillis(response));
            } catch (HttpTimeoutException exception) {
                lastFailure = new TenderProviderException("TRANSIENT_PROVIDER_ERROR",
                        "El proveedor OECE agotó el tiempo de espera", 502, true, exception);
                if (attempt == MAX_RETRIES) {
                    throw lastFailure;
                }
                sleepBeforeRetry(attempt, 0L);
            } catch (IOException exception) {
                lastFailure = new TenderProviderException("TRANSIENT_PROVIDER_ERROR",
                        "No se pudo conectar con el proveedor OECE", 502, true, exception);
                if (attempt == MAX_RETRIES) {
                    throw lastFailure;
                }
                sleepBeforeRetry(attempt, 0L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new TenderProviderException("TRANSIENT_PROVIDER_ERROR",
                        "La consulta al proveedor OECE fue interrumpida", 502, true, exception);
            }
        }
        throw lastFailure == null
                ? new TenderProviderException("TENDER_PROVIDER_ERROR", "Proveedor OECE no disponible", 502, true)
                : lastFailure;
    }

    private static byte[] readLimited(InputStream body) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = body.read(buffer)) != -1) {
            if (read > MAX_RESPONSE_BYTES - total) {
                throw new TenderProviderException("CONTRACT_INVALID",
                        "La respuesta del proveedor supera el límite permitido", 502, false);
            }
            output.write(buffer, 0, read);
            total += read;
        }
        return output.toByteArray();
    }

    private ProviderPage parsePage(String body, URI requestUri) {
        try {
            OcdsReleasePackageParser.ReleasePackage releasePackage = parser.parse(body);
            URI next = releasePackage.next() == null
                    ? null
                    : urlValidator.nextUri(releasePackage.next(), properties.baseUrl());
            if (next != null && olderThanWatermark(releasePackage, requestUri)) {
                next = null;
            }
            return new ProviderPage(releasePackage, next);
        } catch (IllegalArgumentException exception) {
            throw new TenderProviderException("CONTRACT_INVALID",
                    "La respuesta del proveedor no cumple el contrato OCDS", 502, false, exception);
        }
    }

    /**
     * El portal OECE acepta {@code since} pero devuelve el histórico completo ordenado de
     * la release más reciente a la más antigua. Sin este corte la paginación recorrería
     * todo el archivo histórico en cada ejecución, así que se detiene en la primera página
     * cuyas releases son anteriores a la marca solicitada.
     */
    private static boolean olderThanWatermark(OcdsReleasePackageParser.ReleasePackage releasePackage, URI requestUri) {
        Instant watermark = sinceParameter(requestUri);
        if (watermark == null) {
            return false;
        }
        return releasePackage.releases().stream()
                .allMatch(release -> release.date() != null && release.date().isBefore(watermark));
    }

    private static Instant sinceParameter(URI requestUri) {
        String query = requestUri.getRawQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            if (separator <= 0 || !"since".equals(pair.substring(0, separator))) {
                continue;
            }
            try {
                return Instant.parse(URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
            } catch (DateTimeParseException | IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private static TenderProviderException statusFailure(HttpResponse<?> response) {
        int status = response.statusCode();
        if (status == 429) {
            return new TenderProviderException("RATE_LIMITED", "El proveedor OECE limitó la consulta", 502, true);
        }
        if (status == 408 || status >= 500) {
            return new TenderProviderException("TRANSIENT_PROVIDER_ERROR",
                    "El proveedor OECE no respondió correctamente", 502, true);
        }
        if (status == 401 || status == 403) {
            return new TenderProviderException("PROVIDER_ACCESS_DENIED",
                    "El proveedor OECE rechazó el acceso", 502, false);
        }
        if (status == 404) {
            return new TenderProviderException("PROVIDER_NOT_FOUND",
                    "La ruta configurada del proveedor OECE no existe", 502, false);
        }
        return new TenderProviderException("CONTRACT_INVALID",
                "El proveedor OECE rechazó la solicitud", 502, false);
    }

    private static long retryAfterMillis(HttpResponse<?> response) {
        Optional<String> header = response.headers().firstValue("Retry-After");
        if (header.isEmpty()) {
            return 0L;
        }
        String value = header.get().trim();
        try {
            return Math.min(5_000L, Math.max(0L, Long.parseLong(value) * 1_000L));
        } catch (NumberFormatException ignored) {
            try {
                long millis = Duration.between(Instant.now(), ZonedDateTime.parse(value).toInstant()).toMillis();
                return Math.min(5_000L, Math.max(0L, millis));
            } catch (DateTimeParseException ignoredDate) {
                return 0L;
            }
        }
    }

    private static void sleepBeforeRetry(int attempt, long retryAfterMillis) {
        long backoff = Math.min(1_000L, 100L * (1L << attempt));
        long jitter = ThreadLocalRandom.current().nextLong(0L, 25L);
        long delay = Math.max(retryAfterMillis, backoff + jitter);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TenderProviderException("TRANSIENT_PROVIDER_ERROR",
                    "La espera de reintento fue interrumpida", 502, true, exception);
        }
    }
}

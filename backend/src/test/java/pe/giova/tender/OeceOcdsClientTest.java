package pe.giova.tender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.giova.tender.config.OeceProperties;
import pe.giova.tender.contract.OcdsReleasePackageParser;

class OeceOcdsClientTest {
    private HttpServer server;
    private URI baseUri;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api");
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void followsAnOpaqueNextLinkFromTheValidatedProviderHost() {
        server.createContext("/api", exchange -> respond(exchange, 200, releasePackage("/api?page=2")));
        server.start();
        OeceOcdsClient client = client();

        TenderProviderClient.ProviderPage page = client.fetch(client.initialUri(null, null));

        assertEquals("page=2", page.nextUri().getQuery());
        assertEquals(1, page.releasePackage().releases().size());
    }

    @Test
    void stopsFollowingNextWhenThePageIsOlderThanTheRequestedWatermark() {
        Instant old = Instant.now().minus(Duration.ofDays(30));
        server.createContext("/api", exchange -> respond(exchange, 200, releasePackage("/api?page=2", old)));
        server.start();
        OeceOcdsClient client = client();

        TenderProviderClient.ProviderPage page = client.fetch(client.initialUri(null, null));

        assertNull(page.nextUri());
        assertEquals(1, page.releasePackage().releases().size());
    }

    @Test
    void retriesRateLimitAndSucceedsWithoutRetryingContractualFourHundred() {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/api", exchange -> {
            if (attempts.incrementAndGet() < 3) {
                exchange.getResponseHeaders().set("Retry-After", "0");
                respond(exchange, 429, "");
            } else {
                respond(exchange, 200, releasePackage(null));
            }
        });
        server.start();

        TenderProviderClient.ProviderPage page = client().fetch(client().initialUri(null, null));

        assertEquals(3, attempts.get());
        assertEquals("ocds-test-1", page.releasePackage().releases().get(0).ocid());
    }

    @Test
    void doesNotRetryAContractualClientError() {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/api", exchange -> {
            attempts.incrementAndGet();
            respond(exchange, 400, "bad request");
        });
        server.start();

        TenderProviderException exception = assertThrows(TenderProviderException.class,
                () -> client().fetch(client().initialUri(null, null)));

        assertEquals(1, attempts.get());
        assertEquals("CONTRACT_INVALID", exception.code());
        assertTrue(exception.getMessage().contains("rechazó"));
    }

    @Test
    void doesNotFollowRedirectOutsideTheProviderHost() {
        server.createContext("/api", exchange -> {
            exchange.getResponseHeaders().set("Location", "https://example.test/outside");
            respond(exchange, 302, "");
        });
        server.start();

        TenderProviderException exception = assertThrows(TenderProviderException.class,
                () -> client().fetch(client().initialUri(null, null)));

        assertEquals("CONTRACT_INVALID", exception.code());
    }

    private OeceOcdsClient client() {
        return new OeceOcdsClient(new OeceProperties(false, baseUri, "0 0 * * * *", 1, 1, 10, 60),
                new OcdsReleasePackageParser());
    }

    private static String releasePackage(String next) {
        return releasePackage(next, Instant.now());
    }

    private static String releasePackage(String next, Instant releaseDate) {
        return "{" +
                "\"uri\":\"https://example.test/package\"," +
                "\"version\":\"1.1\",\"publishedDate\":\"2026-09-15T00:00:00Z\"," +
                "\"publisher\":{\"name\":\"Fixture\"}," +
                (next == null ? "" : "\"next\":\"" + next + "\",") +
                "\"releases\":[{" +
                "\"ocid\":\"ocds-test-1\",\"id\":\"ocds-test-1-2026\",\"date\":\"" + releaseDate + "\"," +
                "\"tag\":[\"tender\"],\"initiationType\":\"tender\",\"tender\":{\"title\":\"Fixture\"}" +
                "}]" +
                "}";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}

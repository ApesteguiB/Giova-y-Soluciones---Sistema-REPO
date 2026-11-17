package pe.giova.tender.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.giova.tender.contract.OcdsReleasePackageParser.ContractViolationException;

class OcdsReleasePackageParserTest {
    private final OcdsReleasePackageParser parser = new OcdsReleasePackageParser();

    @Test
    void parsesMinimalReleasePackage() throws IOException {
        OcdsReleasePackageParser.ReleasePackage result = parser.parse(fixture("minimal-release-package.json"));

        assertEquals("1.1", result.version());
        assertEquals("Publicador de fixture", result.publisherName());
        assertEquals(1, result.releases().size());
        assertEquals("ocds-PE-TEST-000001", result.releases().get(0).ocid());
        assertEquals("Servicio de mantenimiento de fixture", result.releases().get(0).tenderTitle());
        assertTrue(result.extensions().isEmpty());
    }

    @Test
    void mapsOptionalFieldsAndIgnoresExtensionsAndUnknownFields() throws IOException {
        OcdsReleasePackageParser.ReleasePackage result = parser.parse(fixture("full-release-package.json"));
        OcdsReleasePackageParser.Release release = result.releases().get(0);

        assertEquals(List.of("https://example.test/ocds/extensions/fixture/1.0.0/schema.json"),
                result.extensions());
        assertEquals("Entidad compradora de fixture", release.buyerName());
        assertEquals(0, new BigDecimal("125000.50").compareTo(release.amount()));
        assertEquals("PEN", release.currency());
        assertEquals("active", release.tenderStatus());
        assertEquals("https://example.test/ocds/releases/ocds-PE-TEST-000002-20260915", release.sourceUrl());
        assertEquals("2026-09-20T00:00:00Z", release.tenderStart().toString());
        assertEquals("2026-10-20T23:59:59Z", release.tenderEnd().toString());
    }

    @Test
    void rejectsMalformedJsonWithStableReason() throws IOException {
        ContractViolationException exception = assertThrows(ContractViolationException.class,
                () -> parser.parse(fixture("malformed-release-package.json")));

        assertEquals(ContractViolationException.Reason.MALFORMED_JSON, exception.reason());
        assertEquals("/", exception.pointer());
    }

    @Test
    void rejectsMissingCriticalOcid() throws IOException {
        ContractViolationException exception = assertThrows(ContractViolationException.class,
                () -> parser.parse(fixture("missing-required-release-package.json")));

        assertEquals(ContractViolationException.Reason.REQUIRED_FIELD_MISSING, exception.reason());
        assertEquals("/releases/0/ocid", exception.pointer());
    }

    @Test
    void rejectsMissingGioVaTenderTitleEvenThoughTheOcdsFieldCanBeNullable() throws IOException {
        String withoutTitle = fixture("minimal-release-package.json").replace(
                "\"title\": \"Servicio de mantenimiento de fixture\"", "");

        ContractViolationException exception = assertThrows(ContractViolationException.class,
                () -> parser.parse(withoutTitle));

        assertEquals(ContractViolationException.Reason.REQUIRED_FIELD_MISSING, exception.reason());
        assertEquals("/releases/0/tender/title", exception.pointer());
        assertNull(exception.getCause());
    }

    private static String fixture(String name) throws IOException {
        String path = "/fixtures/ocds/" + name;
        try (InputStream input = OcdsReleasePackageParserTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Fixture no encontrada: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

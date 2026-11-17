package pe.giova.tender.contract;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses the OECE/OCDS release-package envelope without coupling the application
 * to a network client, a scheduler or the future V10 schema.
 */
public final class OcdsReleasePackageParser {
    private static final String RELEASES_POINTER = "/releases";
    private final JsonMapper objectMapper;

    public OcdsReleasePackageParser() {
        this(JsonMapper.builder().build());
    }

    OcdsReleasePackageParser(JsonMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public ReleasePackage parse(String json) {
        if (json == null || json.isBlank()) {
            throw ContractViolationException.missing("package", "body");
        }

        final JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JacksonException exception) {
            throw ContractViolationException.malformed(exception.getMessage());
        }
        requireObject(root, "package");

        String uri = requiredUri(root, "/uri");
        String version = requiredText(root, "/version");
        if (!version.matches("\\d+\\.\\d+")) {
            throw ContractViolationException.invalid("/version", "debe usar el formato major.minor");
        }
        Instant publishedDate = requiredDate(root, "/publishedDate");
        String publisherName = requiredText(root, "/publisher/name");
        JsonNode releasesNode = requiredNode(root, RELEASES_POINTER);
        if (!releasesNode.isArray() || releasesNode.isEmpty()) {
            throw ContractViolationException.missing("/releases", "un array no vacío");
        }

        List<String> extensions = optionalTextArray(root, "/extensions");
        String next = optionalLink(root, "next");
        if (next == null) {
            next = optionalLink(root.path("links"), "next");
        }
        List<Release> releases = new ArrayList<>();
        for (int index = 0; index < releasesNode.size(); index++) {
            releases.add(parseRelease(releasesNode.get(index), index));
        }
        return new ReleasePackage(uri, version, publishedDate, publisherName, extensions, releases, next);
    }

    private Release parseRelease(JsonNode release, int index) {
        String prefix = "/releases/" + index;
        requireObject(release, prefix);
        String ocid = requiredText(release, "/ocid", prefix + "/ocid");
        String releaseId = requiredText(release, "/id", prefix + "/id");
        String datePointer = prefix + "/date";
        Instant date = requiredDate(release, "/date", datePointer);
        List<String> tags = requiredTextArray(release, "/tag", prefix + "/tag");
        String initiationType = requiredText(release, "/initiationType", prefix + "/initiationType");

        JsonNode tender = requiredNode(release, "/tender", prefix + "/tender");
        requireObject(tender, prefix + "/tender");
        String title = requiredText(release, "/tender/title", prefix + "/tender/title");
        String description = optionalText(tender, "description");
        String status = optionalText(tender, "status");

        JsonNode buyer = release.path("buyer");
        String buyerId = optionalText(buyer, "id");
        String buyerName = optionalText(buyer, "name");

        JsonNode value = firstObject(tender.path("value"), release.path("value"));
        BigDecimal amount = optionalDecimal(value, "amount");
        String currency = optionalText(value, "currency");

        JsonNode tenderPeriod = tender.path("tenderPeriod");
        Instant tenderStart = optionalDate(tenderPeriod, "startDate");
        Instant tenderEnd = optionalDate(tenderPeriod, "endDate");
        String sourceUrl = optionalText(release, "url");
        if (sourceUrl == null) {
            sourceUrl = optionalText(tender, "url");
        }
        String procurementMethod = optionalText(tender, "procurementMethod");
        String region = optionalRegion(tender);
        List<String> classificationCodes = classificationCodes(tender);
        String releaseTag = String.join(",", tags);

        return new Release(ocid, releaseId, date, tags, initiationType, title, description, status,
                buyerId, buyerName, amount, currency, tenderStart, tenderEnd, sourceUrl, releaseTag,
                procurementMethod, region, classificationCodes, release.toString());
    }

    private static String optionalRegion(JsonNode tender) {
        String direct = optionalText(tender, "region");
        if (direct != null) {
            return direct;
        }
        JsonNode deliveryLocation = tender.path("deliveryLocation");
        String description = optionalText(deliveryLocation, "description");
        if (description != null) {
            return description;
        }
        return optionalText(deliveryLocation.path("address"), "region");
    }

    private static List<String> classificationCodes(JsonNode tender) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        addClassification(result, tender.path("classification"));
        JsonNode additional = tender.path("additionalClassifications");
        if (additional.isArray()) {
            for (JsonNode classification : additional) {
                addClassification(result, classification);
            }
        }
        JsonNode items = tender.path("items");
        if (items.isArray()) {
            for (JsonNode item : items) {
                addClassification(result, item.path("classification"));
            }
        }
        return List.copyOf(result);
    }

    private static void addClassification(LinkedHashSet<String> result, JsonNode classification) {
        String id = optionalText(classification, "id");
        if (id != null) {
            result.add(id);
        }
    }

    private static String optionalLink(JsonNode root, String field) {
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode value = root.path(field);
        if (value.isTextual()) {
            return value.asString().isBlank() ? null : value.asString();
        }
        if (value.isObject()) {
            return optionalText(value, "href");
        }
        return null;
    }

    private static JsonNode firstObject(JsonNode first, JsonNode second) {
        if (first != null && first.isObject()) {
            return first;
        }
        return second != null && second.isObject() ? second : null;
    }

    private static String requiredUri(JsonNode root, String pointer) {
        String value = requiredText(root, pointer);
        try {
            URI uri = URI.create(value);
            if (uri.getScheme() == null) {
                throw new IllegalArgumentException("sin esquema");
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            throw ContractViolationException.invalid(pointer, "debe ser una URI válida");
        }
    }

    private static JsonNode requiredNode(JsonNode root, String pointer) {
        return requiredNode(root, pointer, pointer);
    }

    private static JsonNode requiredNode(JsonNode root, String pointer, String errorPointer) {
        JsonNode value = root.at(pointer);
        if (value == null || value.isMissingNode() || value.isNull()) {
            throw ContractViolationException.missing(errorPointer, "un valor");
        }
        return value;
    }

    private static void requireObject(JsonNode value, String pointer) {
        if (value == null || !value.isObject()) {
            throw ContractViolationException.invalid(pointer, "debe ser un objeto JSON");
        }
    }

    private static String requiredText(JsonNode root, String pointer) {
        return requiredText(root, pointer, pointer);
    }

    private static String requiredText(JsonNode root, String pointer, String errorPointer) {
        JsonNode value = requiredNode(root, pointer, errorPointer);
        if (!value.isTextual() || value.asString().isBlank()) {
            throw ContractViolationException.invalid(errorPointer, "debe ser texto no vacío");
        }
        return value.asString();
    }

    private static String optionalText(JsonNode root, String field) {
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode value = root.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isTextual() && !value.isNumber()) {
            throw ContractViolationException.invalid(field, "debe ser texto o número");
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private static Instant requiredDate(JsonNode root, String pointer) {
        return requiredDate(root, pointer, pointer);
    }

    private static Instant requiredDate(JsonNode root, String pointer, String errorPointer) {
        String value = requiredText(root, pointer, errorPointer);
        return parseDate(value, errorPointer);
    }

    private static Instant optionalDate(JsonNode root, String field) {
        String value = optionalText(root, field);
        return value == null ? null : parseDate(value, field);
    }

    private static Instant parseDate(String value, String pointer) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException ignoredOffset) {
                try {
                    return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
                } catch (DateTimeParseException exception) {
                    throw ContractViolationException.invalid(pointer, "fecha ISO-8601 inválida");
                }
            }
        }
    }

    private static BigDecimal optionalDecimal(JsonNode root, String field) {
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode value = root.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isNumber() && !value.isTextual()) {
            throw ContractViolationException.invalid(field, "debe ser un número");
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException exception) {
            throw ContractViolationException.invalid(field, "debe ser un número decimal");
        }
    }

    private static List<String> requiredTextArray(JsonNode root, String pointer) {
        return requiredTextArray(root, pointer, pointer);
    }

    private static List<String> requiredTextArray(JsonNode root, String pointer, String errorPointer) {
        JsonNode value = requiredNode(root, pointer, errorPointer);
        if (!value.isArray() || value.isEmpty()) {
            throw ContractViolationException.invalid(errorPointer, "debe ser un array no vacío");
        }
        List<String> result = new ArrayList<>();
        for (int index = 0; index < value.size(); index++) {
            JsonNode item = value.get(index);
            if (!item.isTextual() || item.asString().isBlank()) {
                throw ContractViolationException.invalid(errorPointer + "/" + index, "debe ser texto no vacío");
            }
            result.add(item.asString());
        }
        return List.copyOf(result);
    }

    private static List<String> optionalTextArray(JsonNode root, String pointer) {
        JsonNode value = root.at(pointer);
        if (value.isMissingNode() || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw ContractViolationException.invalid(pointer, "debe ser un array");
        }
        List<String> result = new ArrayList<>();
        for (int index = 0; index < value.size(); index++) {
            JsonNode item = value.get(index);
            if (!item.isTextual() || item.asString().isBlank()) {
                throw ContractViolationException.invalid(pointer + "/" + index, "debe ser texto no vacío");
            }
            result.add(item.asString());
        }
        return List.copyOf(result);
    }

    public record ReleasePackage(
            String uri,
            String version,
            Instant publishedDate,
            String publisherName,
            List<String> extensions,
            List<Release> releases,
            String next) {
        public ReleasePackage(String uri, String version, Instant publishedDate, String publisherName,
                              List<String> extensions, List<Release> releases) {
            this(uri, version, publishedDate, publisherName, extensions, releases, null);
        }

        public ReleasePackage {
            extensions = List.copyOf(extensions);
            releases = List.copyOf(releases);
        }
    }

    public record Release(
            String ocid,
            String releaseId,
            Instant date,
            List<String> tags,
            String initiationType,
            String tenderTitle,
            String tenderDescription,
            String tenderStatus,
            String buyerId,
            String buyerName,
            BigDecimal amount,
            String currency,
            Instant tenderStart,
            Instant tenderEnd,
            String sourceUrl,
            String releaseTag,
            String procurementMethod,
            String region,
            List<String> classificationCodes,
            String rawJson) {
        public Release(String ocid, String releaseId, Instant date, List<String> tags, String initiationType,
                       String tenderTitle, String tenderDescription, String tenderStatus, String buyerId,
                       String buyerName, BigDecimal amount, String currency, Instant tenderStart,
                       Instant tenderEnd, String sourceUrl) {
            this(ocid, releaseId, date, tags, initiationType, tenderTitle, tenderDescription, tenderStatus,
                    buyerId, buyerName, amount, currency, tenderStart, tenderEnd, sourceUrl,
                    tags == null ? null : String.join(",", tags), null, null, List.of(), null);
        }

        public Release {
            tags = List.copyOf(tags);
            classificationCodes = List.copyOf(classificationCodes);
        }
    }

    public static final class ContractViolationException extends IllegalArgumentException {
        public enum Reason {
            MALFORMED_JSON,
            REQUIRED_FIELD_MISSING,
            INVALID_FIELD
        }

        private final Reason reason;
        private final String pointer;

        private ContractViolationException(Reason reason, String pointer, String message) {
            super(message);
            this.reason = reason;
            this.pointer = pointer;
        }

        static ContractViolationException malformed(String detail) {
            return new ContractViolationException(Reason.MALFORMED_JSON, "/",
                    "El paquete OCDS no es JSON válido" + (detail == null ? "" : ": " + detail));
        }

        static ContractViolationException missing(String pointer, String expected) {
            return new ContractViolationException(Reason.REQUIRED_FIELD_MISSING, pointer,
                    "Falta el campo requerido " + pointer + "; se esperaba " + expected);
        }

        static ContractViolationException invalid(String pointer, String detail) {
            return new ContractViolationException(Reason.INVALID_FIELD, pointer,
                    "Campo OCDS inválido en " + pointer + ": " + detail);
        }

        public Reason reason() {
            return reason;
        }

        public String pointer() {
            return pointer;
        }
    }
}

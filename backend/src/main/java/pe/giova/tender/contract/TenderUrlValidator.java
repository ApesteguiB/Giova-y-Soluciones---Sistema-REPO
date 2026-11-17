package pe.giova.tender.contract;

import java.net.URI;
import java.util.Locale;

public final class TenderUrlValidator {
    private final URI baseUri;
    private final boolean requireHttps;

    public TenderUrlValidator(URI baseUri) {
        this(baseUri, true);
    }

    public TenderUrlValidator(URI baseUri, boolean requireHttps) {
        this.baseUri = requireValidBase(baseUri, requireHttps);
        this.requireHttps = requireHttps;
    }

    public String officialUrlOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        URI candidate = parse(value.trim());
        if (candidate.getUserInfo() != null
                || (requireHttps && !isHttps(candidate)) || !sameAuthority(candidate, baseUri)) {
            return null;
        }
        return candidate.toString();
    }

    public URI nextUri(String value, URI currentUri) {
        if (value == null || value.isBlank() || value.length() > 4096) {
            throw new IllegalArgumentException("El enlace next del proveedor no es válido");
        }
        URI candidate = parse(value.trim());
        if (!candidate.isAbsolute()) {
            candidate = currentUri.resolve(candidate);
        }
        if (candidate.getUserInfo() != null || candidate.getHost() == null
                || (requireHttps && !isHttps(candidate)) || !sameAuthority(candidate, baseUri)) {
            throw new IllegalArgumentException("El enlace next del proveedor no pertenece al host configurado");
        }
        return candidate;
    }

    public URI baseUri() {
        return baseUri;
    }

    private static URI requireValidBase(URI value, boolean requireHttps) {
        if (value == null || value.getHost() == null || value.getUserInfo() != null
                || (requireHttps && !isHttps(value))) {
            throw new IllegalArgumentException("La URL base OECE debe ser absoluta y segura");
        }
        return value;
    }

    private static URI parse(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("La URL del proveedor no es válida");
        }
    }

    private static boolean isHttps(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme());
    }

    private static boolean sameAuthority(URI left, URI right) {
        int leftPort = effectivePort(left);
        int rightPort = effectivePort(right);
        return left.getHost() != null && right.getHost() != null
                && left.getHost().toLowerCase(Locale.ROOT).equals(right.getHost().toLowerCase(Locale.ROOT))
                && leftPort == rightPort;
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return isHttps(uri) ? 443 : 80;
    }
}

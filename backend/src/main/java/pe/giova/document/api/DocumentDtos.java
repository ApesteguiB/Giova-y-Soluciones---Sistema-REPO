package pe.giova.document.api;

import java.time.Instant;

public final class DocumentDtos {
    private DocumentDtos() {
    }

    public record DocumentMetadata(
            String publicId,
            String documentKind,
            String originalName,
            String mediaType,
            String extension,
            long sizeBytes,
            String sha256Hex,
            long createdByWorkerId,
            boolean active,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }
}

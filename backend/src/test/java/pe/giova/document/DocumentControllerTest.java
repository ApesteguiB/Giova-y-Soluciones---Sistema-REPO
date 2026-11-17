package pe.giova.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;
import pe.giova.document.DocumentRepository.StoredDocument;

class DocumentControllerTest {
    @Test
    void returnsAttachmentHeadersAndNoStore() {
        DocumentStorageService service = mock(DocumentStorageService.class);
        byte[] bytes = "%PDF-1.7\ncontent".getBytes(StandardCharsets.US_ASCII);
        StoredDocument document = new StoredDocument(1, "doc-id", DocumentKind.OTHER_PDF,
                "other-pdf/2026/09/doc-id.pdf", "reporte.pdf", "application/pdf", "pdf", bytes.length,
                "a".repeat(64), 8, true, 0, Instant.now(), Instant.now());
        when(service.openForDownload(eq("doc-id"), org.mockito.ArgumentMatchers.any(WorkerPrincipal.class)))
                .thenReturn(new DocumentStorageService.DocumentDownload(document, new ByteArrayResource(bytes)));
        DocumentController controller = new DocumentController(service);
        WorkerPrincipal principal = principal(8, RoleCode.SELLER);

        var response = controller.content("doc-id", new UsernamePasswordAuthenticationToken(principal, null));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/pdf", response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertEquals(Long.toString(bytes.length), response.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        verify(service).openForDownload("doc-id", principal);
    }

    private static WorkerPrincipal principal(long id, RoleCode role) {
        return new WorkerPrincipal(new WorkerRepository.WorkerRecord(id, "seller@example.test", "Vendedor", "hash",
                true, role));
    }
}

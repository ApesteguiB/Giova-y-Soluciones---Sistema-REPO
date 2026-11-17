package pe.giova.document;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentValidatorTest {
    @TempDir
    Path temp;

    private final DocumentValidator validator = new DocumentValidator(1024);

    @Test
    void acceptsPdfByMimeExtensionAndSignature() throws Exception {
        Path file = Files.write(temp.resolve("document.pdf"), "%PDF-1.7\nbody".getBytes(StandardCharsets.US_ASCII));

        DocumentValidator.ValidatedDocument result = validator.validate(DocumentKind.OTHER_PDF, "..\\document.pdf",
                "application/pdf", file, Files.size(file));

        assertEquals("document.pdf", result.originalName());
        assertEquals("application/pdf", result.mediaType());
    }

    @Test
    void rejectsPdfWithFalseMime() throws Exception {
        Path file = Files.write(temp.resolve("document.pdf"), "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));

        assertThrows(DocumentValidationException.class,
                () -> validator.validate(DocumentKind.OTHER_PDF, "document.pdf", "image/png", file, Files.size(file)));
    }

    @Test
    void rejectsOversizedFile() throws Exception {
        Path file = Files.write(temp.resolve("document.pdf"), new byte[1025]);

        assertThrows(DocumentValidationException.class,
                () -> validator.validate(DocumentKind.OTHER_PDF, "document.pdf", "application/pdf", file, 1025));
    }

    @Test
    void acceptsXmlWithoutExternalEntities() throws Exception {
        Path file = Files.writeString(temp.resolve("document.xml"), "<?xml version=\"1.0\"?><root><value>ok</value></root>");

        assertDoesNotThrow(() -> validator.validate(DocumentKind.INVOICE_XML, "document.xml", "application/xml",
                file, Files.size(file)));
    }

    @Test
    void rejectsXmlWithDoctypeEvenWhenMimetypeIsCorrect() throws Exception {
        Path file = Files.writeString(temp.resolve("document.xml"),
                "<?xml version=\"1.0\"?><!DOCTYPE root [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><root>&xxe;</root>");

        assertThrows(DocumentValidationException.class,
                () -> validator.validate(DocumentKind.INVOICE_XML, "document.xml", "text/xml", file, Files.size(file)));
    }

    @Test
    void stripsPathAndControlCharactersFromDisplayName() {
        assertEquals("report_.pdf", DocumentValidator.sanitizeFilename("C:\\tmp\\report\u0000.pdf"));
    }
}

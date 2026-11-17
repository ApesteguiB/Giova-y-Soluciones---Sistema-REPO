package pe.giova.document;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Locale;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

public final class DocumentValidator {
    private static final String PDF_MEDIA_TYPE = "application/pdf";
    private static final String XML_MEDIA_TYPE = "application/xml";
    private static final String TEXT_XML_MEDIA_TYPE = "text/xml";
    private static final int MAX_NAME_LENGTH = 255;
    private final long maxBytes;

    public DocumentValidator(long maxBytes) {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("El límite de documentos debe ser positivo");
        }
        this.maxBytes = maxBytes;
    }

    public long maxBytes() {
        return maxBytes;
    }

    public ValidatedDocument validate(DocumentKind kind, String originalFilename, String contentType,
                                      Path file, long sizeBytes) {
        if (kind == null) {
            throw new DocumentValidationException("documentKind es obligatorio");
        }
        if (sizeBytes < 1 || sizeBytes > maxBytes) {
            throw new DocumentValidationException("El archivo supera el límite permitido");
        }
        String sanitizedName = sanitizeFilename(originalFilename);
        String mediaType = normalizeMediaType(contentType);
        String extension = extension(sanitizedName);
        if (!extension.equals(kind.extension())) {
            throw new DocumentValidationException("La extensión no coincide con documentKind");
        }
        if (kind.isPdf()) {
            if (!PDF_MEDIA_TYPE.equals(mediaType)) {
                throw new DocumentValidationException("El MIME debe ser application/pdf");
            }
            validatePdfSignature(file);
        } else {
            if (!XML_MEDIA_TYPE.equals(mediaType) && !TEXT_XML_MEDIA_TYPE.equals(mediaType)) {
                throw new DocumentValidationException("El MIME debe ser application/xml o text/xml");
            }
            validateXml(file);
        }
        return new ValidatedDocument(sanitizedName, mediaType, extension, sizeBytes);
    }

    public static String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new DocumentValidationException("El nombre del archivo es obligatorio");
        }
        String flattened = originalFilename.replace('\\', '/');
        int separator = flattened.lastIndexOf('/');
        String name = separator >= 0 ? flattened.substring(separator + 1) : flattened;
        name = Normalizer.normalize(name, Normalizer.Form.NFKC);
        StringBuilder clean = new StringBuilder(name.length());
        name.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint)) {
                clean.append('_');
            } else {
                clean.appendCodePoint(codePoint);
            }
        });
        String result = clean.toString().trim();
        if (result.isBlank() || result.equals(".") || result.equals("..")) {
            throw new DocumentValidationException("El nombre del archivo no es válido");
        }
        if (result.length() > MAX_NAME_LENGTH) {
            String ext = extension(result);
            int keep = Math.max(1, MAX_NAME_LENGTH - ext.length() - 1);
            result = result.substring(0, keep) + "." + ext;
        }
        return result;
    }

    private static String normalizeMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new DocumentValidationException("El MIME del archivo es obligatorio");
        }
        String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new DocumentValidationException("El MIME del archivo no es válido");
        }
        return normalized;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) {
            throw new DocumentValidationException("El archivo debe tener extensión");
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static void validatePdfSignature(Path file) {
        try (var input = Files.newInputStream(file)) {
            byte[] header = input.readNBytes(5);
            String signature = new String(header, StandardCharsets.US_ASCII);
            if (header.length != 5 || !signature.equals("%PDF-")) {
                throw new DocumentValidationException("La firma del PDF no es válida");
            }
        } catch (IOException exception) {
            throw new DocumentStorageException("No se pudo leer el archivo temporal", exception);
        }
    }

    private static void validateXml(Path file) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) {
                }

                @Override
                public void error(SAXParseException exception) throws SAXParseException {
                    throw exception;
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXParseException {
                    throw exception;
                }
            });
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            builder.parse(file.toFile());
        } catch (DocumentValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DocumentValidationException("El XML no es válido o contiene entidades externas", exception);
        }
    }

    public record ValidatedDocument(String originalName, String mediaType, String extension, long sizeBytes) {
    }
}

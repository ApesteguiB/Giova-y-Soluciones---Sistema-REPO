package pe.giova.invoice.xml;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

/**
 * Lector de comprobantes electrónicos en formato UBL 2.1 (el XML que emite SUNAT
 * a través de Nubefact y del resto de proveedores autorizados).
 *
 * <p>Del archivo se extrae lo que el sistema necesita para registrar la factura sin
 * digitarla: número de comprobante, fecha, moneda, emisor, adquirente y el detalle
 * línea por línea (código del vendedor, descripción, cantidad y precio unitario).
 *
 * <p>El parseo es defensivo a propósito: se desactivan DTD y entidades externas
 * (XXE), se ignoran los prefijos de espacio de nombres —cada proveedor usa los
 * suyos— y se acepta tanto {@code Invoice} como {@code CreditNote} y
 * {@code DebitNote}, que comparten estructura.
 */
public final class UblInvoiceParser {
    /** Límite defensivo: un comprobante real no tiene cientos de líneas. */
    private static final int MAX_LINES = 500;

    private UblInvoiceParser() {}

    public static ParsedInvoice parse(byte[] xml) {
        if (xml == null || xml.length == 0) {
            throw new InvoiceXmlException("El archivo XML está vacío.");
        }
        Element root = readRoot(xml);
        String documentName = localName(root);
        if (!documentName.equals("Invoice") && !documentName.equals("CreditNote")
                && !documentName.equals("DebitNote")) {
            throw new InvoiceXmlException(
                    "El XML no es un comprobante UBL. Se encontró <" + documentName + "> en la raíz.");
        }

        String number = requireText(firstChildText(root, "ID"),
                "El XML no trae el número de comprobante (cbc:ID).");
        LocalDate issueDate = parseDate(firstChildText(root, "IssueDate"));
        if (issueDate == null) {
            throw new InvoiceXmlException("El XML no trae una fecha de emisión válida (cbc:IssueDate).");
        }
        LocalDate dueDate = parseDate(firstChildText(root, "DueDate"));
        String currency = normalizeCurrency(firstChildText(root, "DocumentCurrencyCode"));

        Party supplier = party(root, "AccountingSupplierParty");
        Party customer = party(root, "AccountingCustomerParty");
        BigDecimal declaredTotal = amount(deepFirst(root, "LegalMonetaryTotal"), "PayableAmount");

        List<ParsedLine> lines = lines(root);
        if (lines.isEmpty()) {
            throw new InvoiceXmlException("El XML no contiene líneas de detalle.");
        }

        return new ParsedInvoice(number, issueDate, dueDate, currency, supplier, customer,
                declaredTotal, lines);
    }

    // ---------------------------------------------------------------- lectura

    private static Element readRoot(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            builder.setErrorHandler(new ErrorHandler() {
                @Override public void warning(SAXParseException exception) {}
                @Override public void error(SAXParseException exception) throws SAXParseException { throw exception; }
                @Override public void fatalError(SAXParseException exception) throws SAXParseException { throw exception; }
            });
            var document = builder.parse(new ByteArrayInputStream(xml));
            Element root = document.getDocumentElement();
            if (root == null) {
                throw new InvoiceXmlException("El XML no tiene elemento raíz.");
            }
            return root;
        } catch (InvoiceXmlException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvoiceXmlException("El archivo no es un XML válido o contiene entidades externas.",
                    exception);
        }
    }

    private static List<ParsedLine> lines(Element root) {
        List<Element> nodes = new ArrayList<>();
        for (String tag : List.of("InvoiceLine", "CreditNoteLine", "DebitNoteLine")) {
            nodes.addAll(deepAll(root, tag));
        }
        if (nodes.size() > MAX_LINES) {
            throw new InvoiceXmlException("El comprobante supera las " + MAX_LINES + " líneas permitidas.");
        }
        List<ParsedLine> lines = new ArrayList<>(nodes.size());
        int number = 0;
        for (Element node : nodes) {
            number++;
            Element item = deepFirst(node, "Item");
            String description = item == null ? null : deepFirstText(item, "Description");
            if (description == null || description.isBlank()) {
                description = item == null ? null : deepFirstText(item, "Name");
            }
            if (description == null || description.isBlank()) {
                description = "Concepto sin descripción";
            }

            String sku = null;
            if (item != null) {
                // Prioridad: código del vendedor, luego código estándar, luego código de catálogo.
                sku = codeFrom(item, "SellersItemIdentification");
                if (sku == null) sku = codeFrom(item, "StandardItemIdentification");
                if (sku == null) sku = codeFrom(item, "BuyersItemIdentification");
            }
            if (sku == null || sku.isBlank()) {
                sku = "SIN-CODIGO-" + number;
            }

            Quantity quantity = quantity(node);
            BigDecimal unitPrice = amount(deepFirst(node, "Price"), "PriceAmount");
            BigDecimal lineAmount = firstAmount(node, "LineExtensionAmount");
            if (unitPrice == null && lineAmount != null && quantity.value().signum() > 0) {
                // Algunos emisores no mandan cac:Price; se deduce del importe de la línea.
                unitPrice = lineAmount.divide(quantity.value(), 6, RoundingMode.HALF_UP);
            }
            if (unitPrice == null) {
                unitPrice = BigDecimal.ZERO;
            }

            lines.add(new ParsedLine(number, trim(sku, 80), trim(description, 500),
                    quantity.unit(), quantity.value(), unitPrice.setScale(6, RoundingMode.HALF_UP),
                    lineAmount));
        }
        return lines;
    }

    private static Quantity quantity(Element line) {
        for (String tag : List.of("InvoicedQuantity", "CreditedQuantity", "DebitedQuantity", "Quantity")) {
            Element node = deepFirst(line, tag);
            if (node == null) continue;
            BigDecimal value = decimal(node.getTextContent());
            if (value == null || value.signum() <= 0) continue;
            String unitCode = node.getAttribute("unitCode");
            return new Quantity(value, unitLabel(unitCode));
        }
        return new Quantity(BigDecimal.ONE, "unidad");
    }

    /** Traduce los códigos UN/ECE más usados en comprobantes peruanos. */
    private static String unitLabel(String unitCode) {
        if (unitCode == null || unitCode.isBlank()) return "unidad";
        return switch (unitCode.trim().toUpperCase(Locale.ROOT)) {
            case "NIU", "ZZ", "C62", "UN" -> "unidad";
            case "ZZP", "SET" -> "juego";
            case "BX" -> "caja";
            case "KGM" -> "kg";
            case "MTR" -> "metro";
            case "LTR" -> "litro";
            case "HUR" -> "hora";
            case "DAY" -> "día";
            default -> unitCode.trim().toLowerCase(Locale.ROOT);
        };
    }

    private static Party party(Element root, String wrapperTag) {
        Element wrapper = deepFirst(root, wrapperTag);
        if (wrapper == null) return new Party(null, null);
        String document = null;
        Element identification = deepFirst(wrapper, "PartyIdentification");
        if (identification != null) document = deepFirstText(identification, "ID");
        if (document == null || document.isBlank()) document = deepFirstText(wrapper, "CustomerAssignedAccountID");
        if (document == null || document.isBlank()) document = deepFirstText(wrapper, "AdditionalAccountID");

        String name = null;
        Element legal = deepFirst(wrapper, "PartyLegalEntity");
        if (legal != null) name = deepFirstText(legal, "RegistrationName");
        if (name == null || name.isBlank()) {
            Element partyName = deepFirst(wrapper, "PartyName");
            if (partyName != null) name = deepFirstText(partyName, "Name");
        }
        return new Party(document == null ? null : document.trim(), name == null ? null : name.trim());
    }

    private static String codeFrom(Element item, String tag) {
        Element node = deepFirst(item, tag);
        if (node == null) return null;
        String value = deepFirstText(node, "ID");
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ------------------------------------------------------------- utilidades

    /** Nombre del elemento sin prefijo de espacio de nombres (cbc:ID -> ID). */
    private static String localName(Node node) {
        String local = node.getLocalName();
        if (local != null) return local;
        String name = node.getNodeName();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    /** Hijo directo con ese nombre local. */
    private static String firstChildText(Element parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && localName(child).equals(tag)) {
                return child.getTextContent();
            }
        }
        return null;
    }

    /** Primer descendiente con ese nombre local, en orden de documento. */
    private static Element deepFirst(Element parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            Element element = (Element) child;
            if (localName(element).equals(tag)) return element;
            Element nested = deepFirst(element, tag);
            if (nested != null) return nested;
        }
        return null;
    }

    private static String deepFirstText(Element parent, String tag) {
        Element node = deepFirst(parent, tag);
        return node == null ? null : node.getTextContent();
    }

    private static List<Element> deepAll(Element parent, String tag) {
        List<Element> found = new ArrayList<>();
        collect(parent, tag, found);
        return found;
    }

    private static void collect(Element parent, String tag, List<Element> sink) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            Element element = (Element) child;
            if (localName(element).equals(tag)) {
                sink.add(element);
            } else {
                collect(element, tag, sink);
            }
        }
    }

    private static BigDecimal amount(Element parent, String tag) {
        if (parent == null) return null;
        return decimal(deepFirstText(parent, tag));
    }

    private static BigDecimal firstAmount(Element parent, String tag) {
        return decimal(deepFirstText(parent, tag));
    }

    private static BigDecimal decimal(String raw) {
        if (raw == null) return null;
        String clean = raw.trim();
        if (clean.isEmpty()) return null;
        try {
            return new BigDecimal(clean);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String clean = raw.trim();
        if (clean.length() > 10) clean = clean.substring(0, 10);
        try {
            return LocalDate.parse(clean);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static String normalizeCurrency(String raw) {
        if (raw == null || raw.isBlank()) return "PEN";
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (code.equals("PEN") || code.equals("USD")) return code;
        throw new InvoiceXmlException("La moneda " + code + " no está soportada. Solo PEN y USD.");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new InvoiceXmlException(message);
        return value.trim();
    }

    private static String trim(String value, int max) {
        String clean = value.trim().replaceAll("\\s+", " ");
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    private record Quantity(BigDecimal value, String unit) {}

    /** Emisor o adquirente del comprobante. */
    public record Party(String documentNumber, String name) {}

    /** Una línea del detalle, ya normalizada. */
    public record ParsedLine(int lineNumber, String sku, String description, String unit,
                             BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineAmount) {}

    /** Contenido útil del comprobante. */
    public record ParsedInvoice(String invoiceNumber, LocalDate issueDate, LocalDate dueDate,
                                String currencyCode, Party supplier, Party customer,
                                BigDecimal declaredTotal, List<ParsedLine> lines) {}
}

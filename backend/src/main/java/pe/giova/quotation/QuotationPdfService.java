package pe.giova.quotation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.Color;
import java.text.Normalizer;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import pe.giova.tenant.TenantBrandingService;

@Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class QuotationPdfService {
    private final TenantBrandingService brandingService;

    public QuotationPdfService(TenantBrandingService brandingService) {
        this.brandingService = brandingService;
    }

    public byte[] render(QuotationRepository.QuotationViewData quotation) {
        return render(quotation, brandingService.currentForPdf());
    }

    byte[] render(QuotationRepository.QuotationViewData quotation, TenantBrandingService.PdfBranding branding) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                float y = 790;
                float textX = 50;
                if (branding.logoPng() != null) {
                    PDImageXObject logo = PDImageXObject.createFromByteArray(document, branding.logoPng(), "tenant-logo");
                    float width = 84;
                    float height = Math.min(54, width * logo.getHeight() / logo.getWidth());
                    stream.drawImage(logo, 50, y - height + 8, width, height);
                    textX = 150;
                }
                stream.setNonStrokingColor(Color.decode(branding.primaryColor()));
                y = line(stream, text(branding.displayName()), textX, y, 15, true);
                if (branding.pdfHeaderText() != null && !branding.pdfHeaderText().isBlank()) {
                    stream.setNonStrokingColor(Color.decode(branding.accentColor()));
                    y = line(stream, text(branding.pdfHeaderText()), textX, y - 2, 9, false);
                }
                if (branding.logoPng() != null) {
                    y = Math.min(y, 720);
                }
                stream.setNonStrokingColor(Color.decode(branding.accentColor()));
                stream.addRect(50, y - 4, 495, 2);
                stream.fill();
                stream.setNonStrokingColor(Color.BLACK);
                y = line(stream, "COTIZACION", 50, y - 14, 13, true);
                y = line(stream, "Numero: " + text(quotation.header().quotationNumber()), 50, y - 8, 10, false);
                y = line(stream, "Estado: " + quotation.header().status().name(), 50, y - 2, 10, false);
                y = line(stream, "Cliente: " + text(quotation.header().customerName()) + " ("
                        + text(quotation.header().customerDocumentType()) + " " + text(quotation.header().customerDocumentNumber()) + ")", 50, y - 2, 10, false);
                y = line(stream, "Moneda: " + quotation.header().currencyCode() + "  Vigencia: " + text(String.valueOf(quotation.header().validUntil())), 50, y - 2, 10, false);
                y = line(stream, "Procedimiento: " + procurementLabel(quotation.header().procurementType()), 50, y - 2, 10, false);
                // El plazo de entrega y la garantia son parte de la oferta: la
                // entidad los evalua junto con el precio, asi que van impresos.
                String offer = "";
                if (quotation.header().deliveryTermDays() != null) {
                    offer = "Plazo de entrega: " + quotation.header().deliveryTermDays() + " dias";
                }
                if (quotation.header().warrantyMonths() != null) {
                    if (!offer.isEmpty()) offer += "   ";
                    offer += "Garantia: " + quotation.header().warrantyMonths() + " meses";
                }
                if (!offer.isEmpty()) {
                    y = line(stream, offer, 50, y - 2, 10, false);
                }
                if (quotation.header().ocamNumber() != null && !quotation.header().ocamNumber().isBlank()) {
                    y = line(stream, "OCAM: " + text(quotation.header().ocamNumber())
                            + (quotation.header().ocamDate() == null ? "" : "  (" + quotation.header().ocamDate() + ")"),
                            50, y - 2, 10, false);
                }
                y -= 14;
                y = line(stream, "Cant.  SKU                 Descripcion                         Precio       Total", 50, y, 9, true);
                for (QuotationRepository.ItemData item : quotation.items()) {
                    String description = item.description().length() > 32 ? item.description().substring(0, 32) : item.description();
                    y = line(stream, String.format(Locale.ROOT, "%.2f  %-18s %-32s %10.2f %10.2f", item.quantity(), text(item.sku()), text(description), item.unitPrice(), item.total()), 50, y - 2, 8, false);
                    if (y < 90) break;
                }
                y -= 12;
                y = line(stream, "Subtotal: " + quotation.header().subtotalAmount(), 380, y, 10, false);
                y = line(stream, "Impuesto: " + quotation.header().taxAmount(), 380, y - 2, 10, false);
                y = line(stream, "TOTAL: " + quotation.header().totalAmount(), 380, y - 2, 11, true);
                if (quotation.header().notes() != null && !quotation.header().notes().isBlank()) {
                    line(stream, "Notas: " + text(quotation.header().notes()), 50, y - 14, 9, false);
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo generar el PDF de cotizacion", exception);
        }
    }

    private static float line(PDPageContentStream stream, String value, float x, float y, float size, boolean bold) throws IOException {
        stream.beginText();
        stream.setFont(bold ? new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD) : new PDType1Font(Standard14Fonts.FontName.HELVETICA), size);
        stream.newLineAtOffset(x, y);
        stream.showText(text(value));
        stream.endText();
        return y - size - 2;
    }

    /** Nombre legible del procedimiento, tal como lo llama la entidad. */
    private static String procurementLabel(ProcurementType type) {
        return switch (type) {
            case PERU_COMPRAS -> "Peru Compras - Acuerdo Marco";
            case DIRECTA -> "Contratacion directa";
            case INDIRECTA -> "Contratacion indirecta";
            case PRIVADA -> "Empresa privada";
        };
    }

    static String text(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return normalized.replaceAll("[^\\x20-\\x7E]", "?");
    }
}

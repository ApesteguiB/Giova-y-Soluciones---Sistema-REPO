package pe.giova.quotation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import pe.giova.tenant.TenantBrandingService;

class QuotationPdfServiceTest {
    @Test
    void emittedPdfHasExtractableQuotationContent() throws Exception {
        var header = new QuotationRepository.QuotationHeader(1, "id", "COT-2026-000001", 2, "customer", "Empresa Andina", "RUC", "20123456789", 3, "Vendedor", QuotationStatus.ISSUED,
                ProcurementType.PERU_COMPRAS, QuotationOutcome.PENDIENTE, null, null, null, 10, 12, "OCAM 00123-2026", LocalDate.of(2026, 10, 1),
                Instant.now(), LocalDate.of(2026, 12, 31), "PEN", BigDecimal.ONE, false, new BigDecimal("0.18"), new BigDecimal("100.00"), new BigDecimal("18.00"), new BigDecimal("118.00"), "Nota", 1, Instant.now(), Instant.now(), null);
        var data = new QuotationRepository.QuotationViewData(header, List.of(new QuotationRepository.ItemData(1, null, "SKU-1", "Servicio de prueba", "unidad", BigDecimal.ONE, new BigDecimal("100"), null, null, new BigDecimal("100"), new BigDecimal("18"), new BigDecimal("118"))));
        byte[] pdf = renderer("GIOVA", "#0a2342").render(data);
        String text = new PDFTextStripper().getText(Loader.loadPDF(pdf));
        assertTrue(text.contains("COT-2026-000001"));
        assertTrue(text.contains("Empresa Andina"));
        assertTrue(text.contains("118.00"));
        // El procedimiento y la oferta de plazo/garantia son parte del documento
        // que lee la entidad, no solo dato interno.
        assertTrue(text.contains("Acuerdo Marco"));
        assertTrue(text.contains("10 dias"));
        assertTrue(text.contains("OCAM 00123-2026"));
    }

    @Test
    void pdfUsesTenantBrandInsteadOfGIOVAForAnotherCompany() throws Exception {
        byte[] pdf = renderer("SOLUCIONES INFORMÁTICAS TI", "#f26b21").render(data());
        String text = new PDFTextStripper().getText(Loader.loadPDF(pdf));

        assertTrue(text.contains("SOLUCIONES INFORMATICAS TI"));
        org.junit.jupiter.api.Assertions.assertFalse(text.contains("GIOVA"));
    }

    @Test
    void pdfEmbedsTheActiveTenantRasterLogo() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        TenantBrandingService service = mock(TenantBrandingService.class);
        when(service.currentForPdf()).thenReturn(new TenantBrandingService.PdfBranding(
                "Empresa B", "#f26b21", "#ffffff", "#ff9f1c", null, png.toByteArray()));

        try (var document = Loader.loadPDF(new QuotationPdfService(service).render(data()))) {
            assertTrue(document.getPage(0).getResources().getXObjectNames().iterator().hasNext());
        }
    }

    private static QuotationPdfService renderer(String name, String primaryColor) {
        TenantBrandingService service = mock(TenantBrandingService.class);
        when(service.currentForPdf()).thenReturn(new TenantBrandingService.PdfBranding(name, primaryColor,
                "#ffffff", "#f26b21", null, null));
        return new QuotationPdfService(service);
    }

    private static QuotationRepository.QuotationViewData data() {
        var header = new QuotationRepository.QuotationHeader(1, "id", "COT-2026-000001", 2, "customer", "Empresa Andina", "RUC", "20123456789", 3, "Vendedor", QuotationStatus.ISSUED,
                ProcurementType.PERU_COMPRAS, QuotationOutcome.PENDIENTE, null, null, null, 10, 12, "OCAM 00123-2026", LocalDate.of(2026, 10, 1),
                Instant.now(), LocalDate.of(2026, 12, 31), "PEN", BigDecimal.ONE, false, new BigDecimal("0.18"), new BigDecimal("100.00"), new BigDecimal("18.00"), new BigDecimal("118.00"), "Nota", 1, Instant.now(), Instant.now(), null);
        return new QuotationRepository.QuotationViewData(header, List.of(new QuotationRepository.ItemData(1, null, "SKU-1", "Servicio de prueba", "unidad", BigDecimal.ONE, new BigDecimal("100"), null, null, new BigDecimal("100"), new BigDecimal("18"), new BigDecimal("118"))));
    }
}

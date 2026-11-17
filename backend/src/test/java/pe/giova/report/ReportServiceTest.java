package pe.giova.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;

class ReportServiceTest {
    private final ReportRepository repository = mock(ReportRepository.class);
    private final ReportService service = new ReportService(repository);
    private final WorkerPrincipal admin = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
            4L, "admin@test.local", "Administrador", "hash", true, RoleCode.ADMIN));

    @Test
    void quotationWorkbookKeepsNumbersDatesAndNeutralizesFormulaText() throws Exception {
        when(repository.quotations(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(50_001)))
                .thenReturn(List.of(new ReportRepository.QuotationRow("=FORMULA", "Cliente", "Responsable", "ISSUED",
                        Instant.parse("2026-09-15T10:00:00Z"), LocalDate.of(2026, 9, 30), "PEN",
                        new BigDecimal("10.00"), new BigDecimal("1.80"), new BigDecimal("11.80"))));

        ReportService.ReportFile file = service.quotations(null, null, null, null, admin);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(file.content()))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals("Cotizaciones", sheet.getSheetName());
            assertEquals("'=FORMULA", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals(CellType.NUMERIC, sheet.getRow(1).getCell(7).getCellType());
            assertEquals(10.00, sheet.getRow(1).getCell(7).getNumericCellValue());
            assertEquals(CellType.NUMERIC, sheet.getRow(1).getCell(4).getCellType());
            assertEquals(0, sheet.getRow(1).getCell(0).getCellType().compareTo(CellType.STRING));
        }
    }

    @Test
    void rejectsDateRangesLongerThan366Days() {
        assertThrows(ReportTooLargeException.class, () -> service.quotations(
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 9, 15), null, null, admin));
    }
}

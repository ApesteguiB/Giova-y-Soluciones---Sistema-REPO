package pe.giova.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.invoice.InvoiceStatus;
import pe.giova.quotation.QuotationStatus;

@Service
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class ReportService {
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MAX_ROWS = 50_000;
    private static final int MAX_DAYS = 366;
    private final ReportRepository repository;

    public ReportService(ReportRepository repository) {
        this.repository = repository;
    }

    public ReportFile quotations(LocalDate from, LocalDate to, String status, String currency,
                                 WorkerPrincipal actor) {
        requireRole(actor, RoleCode.ADMIN);
        DateRange range = range(from, to);
        String cleanStatus = quotationStatus(status);
        String cleanCurrency = currency(currency);
        List<ReportRepository.QuotationRow> rows = repository.quotations(range.from().atStartOfDay(),
                range.to().plusDays(1).atStartOfDay(), cleanStatus, cleanCurrency, MAX_ROWS + 1);
        ensureLimit(rows.size());
        return workbook("Cotizaciones", List.of("Número", "Cliente", "Responsable", "Estado", "Emitida en",
                "Válida hasta", "Moneda", "Subtotal", "Impuesto", "Total"), rows, this::quotationRow,
                "giova-cotizaciones-" + range.today() + ".xlsx");
    }

    public ReportFile accounting(LocalDate from, LocalDate to, String status, String currency,
                                 WorkerPrincipal actor) {
        requireRole(actor, RoleCode.ADMIN, RoleCode.ACCOUNTANT);
        DateRange range = range(from, to);
        String cleanStatus = invoiceStatus(status);
        String cleanCurrency = currency(currency);
        List<ReportRepository.InvoiceRow> rows = repository.accounting(range.from(), range.to(), cleanStatus,
                cleanCurrency, MAX_ROWS + 1);
        ensureLimit(rows.size());
        return workbook("Facturas internas", List.of("Tipo", "Número", "Cliente", "Estado", "Fecha emisión",
                "Vencimiento", "Moneda", "Subtotal", "Impuesto", "Total", "Registrado por"), rows,
                this::invoiceRow, "giova-contabilidad-" + range.today() + ".xlsx");
    }

    private List<Object[]> quotationRow(ReportRepository.QuotationRow row) {
        return java.util.Collections.singletonList(new Object[]{row.number(), row.customerName(), row.ownerName(), row.status(), row.issuedAt(),
                row.validUntil(), row.currency(), row.subtotal(), row.tax(), row.total()});
    }

    private List<Object[]> invoiceRow(ReportRepository.InvoiceRow row) {
        return java.util.Collections.singletonList(new Object[]{row.invoiceType(), row.number(), row.customerName(), row.status(), row.issueDate(),
                row.dueDate(), row.currency(), row.subtotal(), row.tax(), row.total(), row.registeredByName()});
    }

    private <T> ReportFile workbook(String sheetName, List<String> headers, List<T> rows,
                                    java.util.function.Function<T, List<Object[]>> mapper, String fileName) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle dateStyle = dateStyle(workbook);
            CellStyle numberStyle = numberStyle(workbook);
            Row header = sheet.createRow(0);
            for (int index = 0; index < headers.size(); index++) {
                Cell cell = header.createCell(index);
                cell.setCellValue(headers.get(index));
                cell.setCellStyle(headerStyle);
            }
            int rowIndex = 1;
            for (T value : rows) {
                for (Object[] values : mapper.apply(value)) {
                    Row row = sheet.createRow(rowIndex++);
                    for (int column = 0; column < values.length; column++) {
                        writeCell(row.createCell(column), values[column], dateStyle, numberStyle);
                    }
                }
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, headers.size() - 1));
            for (int index = 0; index < headers.size(); index++) sheet.setColumnWidth(index, width(headers.get(index)));
            sheet.protectSheet("");
            workbook.write(output);
            return new ReportFile(output.toByteArray(), fileName);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo generar el reporte XLSX", exception);
        }
    }

    private static void writeCell(Cell cell, Object value, CellStyle dateStyle, CellStyle numberStyle) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof BigDecimal number) {
            cell.setCellValue(number.doubleValue());
            cell.setCellStyle(numberStyle);
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            cell.setCellStyle(numberStyle);
        } else if (value instanceof LocalDate date) {
            cell.setCellValue(java.sql.Date.valueOf(date));
            cell.setCellStyle(dateStyle);
        } else if (value instanceof Instant instant) {
            cell.setCellValue(java.util.Date.from(instant));
            cell.setCellStyle(dateStyle);
        } else {
            cell.setCellValue(safeText(value.toString()));
        }
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        return style;
    }

    private static CellStyle dateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
        return style;
    }

    private static CellStyle numberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("0.00"));
        return style;
    }

    private static int width(String header) {
        return Math.min(9_000, Math.max(3_000, (header.length() + 4) * 256));
    }

    private static String safeText(String value) {
        if (value.isEmpty()) return value;
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' ? "'" + value : value;
    }

    private static DateRange range(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate normalizedTo = to == null ? today : to;
        LocalDate normalizedFrom = from == null ? normalizedTo.minusDays(MAX_DAYS - 1L) : from;
        long days = ChronoUnit.DAYS.between(normalizedFrom, normalizedTo) + 1;
        if (normalizedTo.isBefore(normalizedFrom)) throw new IllegalArgumentException("to no puede ser anterior a from");
        if (days > MAX_DAYS) throw new ReportTooLargeException("El reporte no puede superar 366 días");
        return new DateRange(normalizedFrom, normalizedTo, today.format(FILE_DATE));
    }

    private static String quotationStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try { return QuotationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)).name(); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("status de cotización no permitido"); }
    }

    private static String invoiceStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try { return InvoiceStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)).name(); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("status de factura no permitido"); }
    }

    private static String currency(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim().toUpperCase(Locale.ROOT);
        if (!clean.equals("PEN") && !clean.equals("USD")) throw new IllegalArgumentException("currency debe ser PEN o USD");
        return clean;
    }

    private static void ensureLimit(int size) {
        if (size > MAX_ROWS) throw new ReportTooLargeException("El reporte supera 50 000 filas");
    }

    private static void requireRole(WorkerPrincipal actor, RoleCode... roles) {
        for (RoleCode role : roles) if (actor.roleCode() == role) return;
        throw new SecurityException("El rol no puede descargar este reporte");
    }

    public record ReportFile(byte[] content, String fileName) {}
    private record DateRange(LocalDate from, LocalDate to, String today) {}
}

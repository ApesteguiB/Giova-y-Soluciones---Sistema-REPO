package pe.giova.report;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pe.giova.auth.TenantScope;

public class ReportRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<QuotationRow> quotations(LocalDateTime fromAt, LocalDateTime toExclusive,
                                         String status, String currency, int maxRows) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fromAt", Timestamp.valueOf(fromAt)).addValue("toExclusive", Timestamp.valueOf(toExclusive))
                .addValue("status", status).addValue("currency", currency).addValue("maxRows", maxRows);
        return jdbc.query("""
                SELECT q.quotation_number, c.business_name AS customer_name, w.display_name AS owner_name,
                       q.status, q.issued_at, q.valid_until, q.currency_code, q.subtotal_amount,
                       q.tax_amount, q.total_amount
                FROM quotations q
                JOIN customers c ON c.id=q.customer_id
                JOIN workers w ON w.id=q.owner_worker_id
                WHERE q.tenant_id=:tenantId
                  AND c.tenant_id=q.tenant_id
                  AND COALESCE(q.issued_at,q.created_at) >= :fromAt
                  AND COALESCE(q.issued_at,q.created_at) < :toExclusive
                  AND (:status IS NULL OR q.status=:status)
                  AND (:currency IS NULL OR q.currency_code=:currency)
                ORDER BY COALESCE(q.issued_at,q.created_at) DESC, q.id DESC
                LIMIT :maxRows
                """, TenantScope.withTenant(params), (rs, row) -> new QuotationRow(rs.getString("quotation_number"),
                rs.getString("customer_name"), rs.getString("owner_name"), rs.getString("status"),
                instant(rs, "issued_at"), rs.getObject("valid_until", LocalDate.class), rs.getString("currency_code"),
                rs.getBigDecimal("subtotal_amount"), rs.getBigDecimal("tax_amount"), rs.getBigDecimal("total_amount")));
    }

    public List<InvoiceRow> accounting(LocalDate from, LocalDate to, String status, String currency, int maxRows) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fromDate", from).addValue("toDate", to).addValue("status", status)
                .addValue("currency", currency).addValue("maxRows", maxRows);
        return jdbc.query("""
                SELECT i.invoice_type, i.invoice_number, c.business_name AS customer_name,
                       i.status, i.issue_date, i.due_date, i.currency_code, i.subtotal_amount,
                       i.tax_amount, i.total_amount, w.display_name AS registered_by_name
                FROM invoices i
                JOIN customers c ON c.id=i.customer_id
                JOIN workers w ON w.id=i.registered_by_worker_id
                WHERE i.tenant_id=:tenantId
                  AND c.tenant_id=i.tenant_id
                  AND i.issue_date >= :fromDate AND i.issue_date <= :toDate
                  AND (:status IS NULL OR i.status=:status)
                  AND (:currency IS NULL OR i.currency_code=:currency)
                ORDER BY i.issue_date DESC, i.id DESC
                LIMIT :maxRows
                """, TenantScope.withTenant(params), (rs, row) -> new InvoiceRow(rs.getString("invoice_type"), rs.getString("invoice_number"),
                rs.getString("customer_name"), rs.getString("status"), rs.getObject("issue_date", LocalDate.class),
                rs.getObject("due_date", LocalDate.class), rs.getString("currency_code"),
                rs.getBigDecimal("subtotal_amount"), rs.getBigDecimal("tax_amount"), rs.getBigDecimal("total_amount"),
                rs.getString("registered_by_name")));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record QuotationRow(String number, String customerName, String ownerName, String status,
                               Instant issuedAt, LocalDate validUntil, String currency,
                               BigDecimal subtotal, BigDecimal tax, BigDecimal total) {}

    public record InvoiceRow(String invoiceType, String number, String customerName, String status,
                             LocalDate issueDate, LocalDate dueDate, String currency,
                             BigDecimal subtotal, BigDecimal tax, BigDecimal total, String registeredByName) {}
}

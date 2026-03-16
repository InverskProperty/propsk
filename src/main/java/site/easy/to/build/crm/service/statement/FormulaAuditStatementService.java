package site.easy.to.build.crm.service.statement;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.config.CommissionConfig;
import site.easy.to.build.crm.dto.statement.LeaseMasterDTO;
import site.easy.to.build.crm.dto.statement.TransactionDTO;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.repository.CustomerRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Formula Audit Statement Generator.
 *
 * KEY DESIGN PRINCIPLES:
 * 1. FIXED ROWS: Every lease occupies the same row across ALL period sheets,
 *    even if the lease has no activity or has ended. This enables cross-sheet formulas.
 * 2. EXCEL FORMULAS: Calculations (commission, net, arrears, closing balance) are
 *    Excel formulas, not Java-computed values. Users can audit and verify.
 * 3. CHAINED BALANCES: Opening balance of period N+1 = closing balance of period N,
 *    via cross-sheet cell references.
 * 4. TOTALS SHEET: Uses SUM across all period sheets for each lease row.
 * 5. FEWER SHEETS: No redundant allocation/reconciliation sheets — just data + periods + totals.
 *
 * Sheet structure:
 *   LEASE_MASTER  — raw lease data (reference sheet)
 *   TRANSACTIONS  — all rent received transactions (data for SUMIFS)
 *   [Period 1]    — e.g. "Dec 25 - Jan 24, 2025" with fixed rows per lease
 *   [Period 2]    — same rows, next period
 *   ...
 *   TOTALS        — SUM formulas across all period sheets
 */
@Service
public class FormulaAuditStatementService {

    private static final Logger log = LoggerFactory.getLogger(FormulaAuditStatementService.class);

    @Autowired
    private StatementDataExtractService dataExtractService;

    @Autowired
    private CommissionConfig commissionConfig;

    @Autowired
    private CustomerRepository customerRepository;

    // Period sheet column layout (fixed across all period sheets):
    // A=lease_reference, B=property_name, C=tenant_name,
    // D=rent_due, E=rent_received, F=arrears,
    // G=commission_rate, H=commission, I=expenses,
    // J=net_to_owner,
    // K=opening_balance, L=closing_balance
    private static final int COL_LEASE_REF = 0;
    private static final int COL_PROPERTY = 1;
    private static final int COL_TENANT = 2;
    private static final int COL_RENT_DUE = 3;
    private static final int COL_RENT_RECEIVED = 4;
    private static final int COL_ARREARS = 5;
    private static final int COL_COMM_RATE = 6;
    private static final int COL_COMMISSION = 7;
    private static final int COL_EXPENSES = 8;
    private static final int COL_NET_TO_OWNER = 9;
    private static final int COL_OPENING_BAL = 10;
    private static final int COL_CLOSING_BAL = 11;
    private static final int PERIOD_COL_COUNT = 12;

    private static final String[] PERIOD_HEADERS = {
        "lease_reference", "property_name", "tenant_name",
        "rent_due", "rent_received", "arrears",
        "commission_rate", "commission", "expenses",
        "net_to_owner", "opening_balance", "closing_balance"
    };

    /**
     * Generate the formula audit workbook.
     */
    public Workbook generateFormulaAuditStatement(Long customerId, LocalDate startDate, LocalDate endDate,
                                                   int periodStartDay, String statementFrequency) {
        long start = System.currentTimeMillis();
        log.info("Formula Audit: Customer {} from {} to {} (day={}, freq={})",
                customerId, startDate, endDate, periodStartDay, statementFrequency);

        Workbook workbook = new XSSFWorkbook();
        Styles styles = new Styles(workbook);

        // 1. Extract data
        List<LeaseMasterDTO> allLeases = dataExtractService.extractLeaseMasterForCustomer(customerId);
        log.info("Formula Audit: {} leases for customer {}", allLeases.size(), customerId);

        // Separate block properties (they get their own handling)
        List<LeaseMasterDTO> leases = allLeases.stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsBlockProperty()))
                .collect(Collectors.toList());

        List<TransactionDTO> transactions = dataExtractService.extractAllRentReceivedForCustomer(customerId);
        log.info("Formula Audit: {} transactions", transactions.size());

        // 2. Create LEASE_MASTER sheet
        createLeaseMasterSheet(workbook, allLeases, styles);

        // 3. Create TRANSACTIONS sheet
        createTransactionsSheet(workbook, transactions, styles);
        transactions = null; // free memory

        // 4. Generate periods
        List<Period> periods = generatePeriods(startDate, endDate, periodStartDay, statementFrequency);
        log.info("Formula Audit: {} periods", periods.size());

        // 5. Create period sheets (fixed row per lease)
        List<String> periodSheetNames = new ArrayList<>();
        for (int i = 0; i < periods.size(); i++) {
            Period period = periods.get(i);
            String sheetName = sanitizeSheetName(
                    period.start.format(DateTimeFormatter.ofPattern("MMM dd")) + " - " +
                    period.end.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
            periodSheetNames.add(sheetName);

            String prevSheetName = (i > 0) ? periodSheetNames.get(i - 1) : null;
            createPeriodSheet(workbook, sheetName, leases, period, prevSheetName, startDate, styles);
        }

        // 6. Create TOTALS sheet
        createTotalsSheet(workbook, leases, periodSheetNames, styles);

        log.info("Formula Audit: Complete in {}ms, {} sheets",
                System.currentTimeMillis() - start, workbook.getNumberOfSheets());

        return workbook;
    }

    // ========================================================================
    // LEASE_MASTER Sheet
    // ========================================================================

    private void createLeaseMasterSheet(Workbook workbook, List<LeaseMasterDTO> leases, Styles styles) {
        Sheet sheet = workbook.createSheet("LEASE_MASTER");

        String[] headers = {
            "lease_id", "lease_reference", "property_name", "property_address",
            "customer_name", "tenant_name", "start_date", "end_date",
            "monthly_rent", "frequency", "frequency_months",
            "commission_%", "service_fee_%"
        };

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header);
        }

        int rowNum = 1;
        for (LeaseMasterDTO lease : leases) {
            Row row = sheet.createRow(rowNum++);
            int col = 0;
            row.createCell(col++).setCellValue(lease.getLeaseId());
            row.createCell(col++).setCellValue(str(lease.getLeaseReference()));
            row.createCell(col++).setCellValue(str(lease.getPropertyName()));
            row.createCell(col++).setCellValue(str(lease.getPropertyAddress()));
            row.createCell(col++).setCellValue(str(lease.getCustomerName()));
            row.createCell(col++).setCellValue(str(lease.getTenantName()));

            Cell startCell = row.createCell(col++);
            if (lease.getStartDate() != null) {
                startCell.setCellValue(lease.getStartDate());
                startCell.setCellStyle(styles.date);
            }

            Cell endCell = row.createCell(col++);
            if (lease.getEndDate() != null) {
                endCell.setCellValue(lease.getEndDate());
                endCell.setCellStyle(styles.date);
            }

            Cell rentCell = row.createCell(col++);
            if (lease.getMonthlyRent() != null) {
                rentCell.setCellValue(lease.getMonthlyRent().doubleValue());
                rentCell.setCellStyle(styles.currency);
            }

            row.createCell(col++).setCellValue(str(lease.getFrequency()));
            row.createCell(col++).setCellValue(lease.getFrequencyMonths());

            Cell commCell = row.createCell(col++);
            if (lease.getCommissionPercentage() != null) {
                commCell.setCellValue(lease.getCommissionPercentage().doubleValue());
            }

            Cell svcCell = row.createCell(col++);
            if (lease.getServiceFeePercentage() != null) {
                svcCell.setCellValue(lease.getServiceFeePercentage().doubleValue());
            }
        }

        applyWidths(sheet, headers.length);
        log.info("LEASE_MASTER: {} rows", leases.size());
    }

    // ========================================================================
    // TRANSACTIONS Sheet
    // ========================================================================

    private void createTransactionsSheet(Workbook workbook, List<TransactionDTO> transactions, Styles styles) {
        Sheet sheet = workbook.createSheet("TRANSACTIONS");

        String[] headers = {
            "transaction_id", "transaction_date", "invoice_id", "property_id", "property_name",
            "customer_id", "category", "transaction_type", "amount", "description", "lease_reference"
        };

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header);
        }

        int rowNum = 1;
        for (TransactionDTO txn : transactions) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(txn.getTransactionId());

            Cell dateCell = row.createCell(1);
            if (txn.getTransactionDate() != null) {
                dateCell.setCellValue(txn.getTransactionDate());
                dateCell.setCellStyle(styles.date);
            }

            row.createCell(2).setCellValue(txn.getInvoiceId() != null ? txn.getInvoiceId() : 0);
            row.createCell(3).setCellValue(txn.getPropertyId() != null ? txn.getPropertyId() : 0);
            row.createCell(4).setCellValue(str(txn.getPropertyName()));
            row.createCell(5).setCellValue(txn.getCustomerId() != null ? txn.getCustomerId() : 0);
            row.createCell(6).setCellValue(str(txn.getCategory()));
            row.createCell(7).setCellValue(str(txn.getTransactionType()));

            Cell amtCell = row.createCell(8);
            if (txn.getAmount() != null) {
                amtCell.setCellValue(txn.getAmount().doubleValue());
                amtCell.setCellStyle(styles.currency);
            }

            row.createCell(9).setCellValue(str(txn.getDescription()));
            row.createCell(10).setCellValue(str(txn.getLeaseReference()));
        }

        applyWidths(sheet, headers.length);
        log.info("TRANSACTIONS: {} rows", transactions.size());
    }

    // ========================================================================
    // Period Sheet (one per statement period, fixed rows per lease)
    // ========================================================================

    private void createPeriodSheet(Workbook workbook, String sheetName,
                                   List<LeaseMasterDTO> leases, Period period,
                                   String prevSheetName, LocalDate overallStartDate, Styles styles) {

        Sheet sheet = workbook.createSheet(sheetName);

        // Header row
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < PERIOD_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(PERIOD_HEADERS[i]);
            cell.setCellStyle(styles.header);
        }

        // Data rows — one per lease, ALWAYS, even if inactive
        for (int leaseIdx = 0; leaseIdx < leases.size(); leaseIdx++) {
            LeaseMasterDTO lease = leases.get(leaseIdx);
            int excelRow = leaseIdx + 2; // 1-based, +1 for header
            Row row = sheet.createRow(leaseIdx + 1);

            // A: lease_reference (always present for identification)
            row.createCell(COL_LEASE_REF).setCellValue(str(lease.getLeaseReference()));

            // B: property_name
            row.createCell(COL_PROPERTY).setCellValue(str(lease.getPropertyName()));

            // C: tenant_name
            row.createCell(COL_TENANT).setCellValue(str(lease.getTenantName()));

            // Determine if lease is active in this period
            boolean active = isLeaseActiveInPeriod(lease, period);

            // D: rent_due — Java-calculated because rent cycle logic is complex
            //    (proration, frequency, payment day alignment)
            Cell rentDueCell = row.createCell(COL_RENT_DUE);
            if (active) {
                BigDecimal rentDue = calculateRentDueForPeriod(lease, period.start, period.end);
                rentDueCell.setCellValue(rentDue.doubleValue());
            } else {
                rentDueCell.setCellValue(0);
            }
            rentDueCell.setCellStyle(styles.currency);

            // E: rent_received — SUMIFS formula against TRANSACTIONS sheet
            //    Matches on lease_reference (col K) and transaction_date within period
            Cell rentRecCell = row.createCell(COL_RENT_RECEIVED);
            String leaseRef = lease.getLeaseReference() != null ? lease.getLeaseReference() : "";
            rentRecCell.setCellFormula(String.format(
                "SUMIFS(TRANSACTIONS!I:I,TRANSACTIONS!K:K,\"%s\",TRANSACTIONS!B:B,\">=\"&DATE(%d,%d,%d),TRANSACTIONS!B:B,\"<=\"&DATE(%d,%d,%d))",
                leaseRef.replace("\"", "\"\""),
                period.start.getYear(), period.start.getMonthValue(), period.start.getDayOfMonth(),
                period.end.getYear(), period.end.getMonthValue(), period.end.getDayOfMonth()
            ));
            rentRecCell.setCellStyle(styles.currency);

            // F: arrears = rent_due - rent_received
            Cell arrearsCell = row.createCell(COL_ARREARS);
            arrearsCell.setCellFormula(String.format("D%d-E%d", excelRow, excelRow));
            arrearsCell.setCellStyle(styles.currency);

            // G: commission_rate
            Cell commRateCell = row.createCell(COL_COMM_RATE);
            double commRate = getCommissionRate(lease);
            commRateCell.setCellValue(commRate);
            commRateCell.setCellStyle(styles.percent);

            // H: commission = rent_received * commission_rate
            Cell commCell = row.createCell(COL_COMMISSION);
            commCell.setCellFormula(String.format("E%d*G%d", excelRow, excelRow));
            commCell.setCellStyle(styles.currency);

            // I: expenses — SUMIFS against TRANSACTIONS for outgoing/expense categories
            //    For now, we use Java-calculated value from data extract
            Cell expCell = row.createCell(COL_EXPENSES);
            if (active) {
                BigDecimal expenses = getExpensesForLease(lease, period.start, period.end);
                expCell.setCellValue(expenses.doubleValue());
            } else {
                expCell.setCellValue(0);
            }
            expCell.setCellStyle(styles.currency);

            // J: net_to_owner = rent_received - commission - expenses
            Cell netCell = row.createCell(COL_NET_TO_OWNER);
            netCell.setCellFormula(String.format("E%d-H%d-I%d", excelRow, excelRow, excelRow));
            netCell.setCellStyle(styles.currency);

            // K: opening_balance
            Cell obCell = row.createCell(COL_OPENING_BAL);
            if (prevSheetName != null) {
                // Chain from previous period's closing balance (same row)
                obCell.setCellFormula(String.format("'%s'!L%d", prevSheetName, excelRow));
            } else {
                // First period: calculate from database
                BigDecimal openingBal = dataExtractService.calculateTenantOpeningBalance(
                        lease.getLeaseId(), lease.getStartDate(), overallStartDate,
                        lease.getMonthlyRent(), lease.getFrequencyMonths(), lease.getEndDate());
                obCell.setCellValue(openingBal != null ? openingBal.doubleValue() : 0);
            }
            obCell.setCellStyle(styles.currency);

            // L: closing_balance = opening_balance + arrears
            Cell cbCell = row.createCell(COL_CLOSING_BAL);
            cbCell.setCellFormula(String.format("K%d+F%d", excelRow, excelRow));
            cbCell.setCellStyle(styles.currency);
        }

        // Totals row
        int totalsRowNum = leases.size() + 1;
        int excelTotalsRow = totalsRowNum + 1;
        Row totalsRow = sheet.createRow(totalsRowNum);

        Cell labelCell = totalsRow.createCell(COL_LEASE_REF);
        labelCell.setCellValue("PERIOD TOTAL");
        labelCell.setCellStyle(styles.bold);

        // Sum columns D through L
        int[] sumCols = {COL_RENT_DUE, COL_RENT_RECEIVED, COL_ARREARS,
                         COL_COMMISSION, COL_EXPENSES, COL_NET_TO_OWNER,
                         COL_OPENING_BAL, COL_CLOSING_BAL};
        for (int col : sumCols) {
            Cell cell = totalsRow.createCell(col);
            String colLetter = colLetter(col);
            cell.setCellFormula(String.format("SUM(%s2:%s%d)", colLetter, colLetter, totalsRowNum));
            cell.setCellStyle(styles.boldCurrency);
        }

        applyWidths(sheet, PERIOD_COL_COUNT);
    }

    // ========================================================================
    // TOTALS Sheet (cross-sheet formulas)
    // ========================================================================

    private void createTotalsSheet(Workbook workbook, List<LeaseMasterDTO> leases,
                                   List<String> periodSheetNames, Styles styles) {

        Sheet sheet = workbook.createSheet("TOTALS");

        // Header row — same as period sheets but with "total_" prefix on numeric columns
        String[] headers = {
            "lease_reference", "property_name", "tenant_name",
            "total_rent_due", "total_rent_received", "total_arrears",
            "avg_commission_rate", "total_commission", "total_expenses",
            "total_net_to_owner", "first_opening_balance", "last_closing_balance"
        };

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header);
        }

        for (int leaseIdx = 0; leaseIdx < leases.size(); leaseIdx++) {
            LeaseMasterDTO lease = leases.get(leaseIdx);
            int excelRow = leaseIdx + 2;
            Row row = sheet.createRow(leaseIdx + 1);

            // A-C: Static identifiers
            row.createCell(COL_LEASE_REF).setCellValue(str(lease.getLeaseReference()));
            row.createCell(COL_PROPERTY).setCellValue(str(lease.getPropertyName()));
            row.createCell(COL_TENANT).setCellValue(str(lease.getTenantName()));

            // D-J: SUM across all period sheets for columns D-J
            int[] sumCols = {COL_RENT_DUE, COL_RENT_RECEIVED, COL_ARREARS,
                             COL_COMMISSION, COL_EXPENSES, COL_NET_TO_OWNER};

            for (int col : sumCols) {
                Cell cell = row.createCell(col);
                String colLetter = colLetter(col);
                StringBuilder formula = new StringBuilder();
                for (int p = 0; p < periodSheetNames.size(); p++) {
                    if (p > 0) formula.append("+");
                    formula.append(String.format("'%s'!%s%d", periodSheetNames.get(p), colLetter, excelRow));
                }
                cell.setCellFormula(formula.toString());
                cell.setCellStyle(styles.currency);
            }

            // G: avg_commission_rate — just show the rate (it's the same across periods)
            Cell commRateCell = row.createCell(COL_COMM_RATE);
            commRateCell.setCellValue(getCommissionRate(lease));
            commRateCell.setCellStyle(styles.percent);

            // K: first_opening_balance — from first period sheet
            Cell obCell = row.createCell(COL_OPENING_BAL);
            if (!periodSheetNames.isEmpty()) {
                obCell.setCellFormula(String.format("'%s'!K%d", periodSheetNames.get(0), excelRow));
            } else {
                obCell.setCellValue(0);
            }
            obCell.setCellStyle(styles.currency);

            // L: last_closing_balance — from last period sheet
            Cell cbCell = row.createCell(COL_CLOSING_BAL);
            if (!periodSheetNames.isEmpty()) {
                String lastSheet = periodSheetNames.get(periodSheetNames.size() - 1);
                cbCell.setCellFormula(String.format("'%s'!L%d", lastSheet, excelRow));
            } else {
                cbCell.setCellValue(0);
            }
            cbCell.setCellStyle(styles.currency);
        }

        // Totals row
        int totalsRowNum = leases.size() + 1;
        Row totalsRow = sheet.createRow(totalsRowNum);

        Cell labelCell = totalsRow.createCell(COL_LEASE_REF);
        labelCell.setCellValue("GRAND TOTAL");
        labelCell.setCellStyle(styles.bold);

        int[] sumCols = {COL_RENT_DUE, COL_RENT_RECEIVED, COL_ARREARS,
                         COL_COMMISSION, COL_EXPENSES, COL_NET_TO_OWNER,
                         COL_OPENING_BAL, COL_CLOSING_BAL};
        for (int col : sumCols) {
            Cell cell = totalsRow.createCell(col);
            String colLetter = colLetter(col);
            cell.setCellFormula(String.format("SUM(%s2:%s%d)", colLetter, colLetter, totalsRowNum));
            cell.setCellStyle(styles.boldCurrency);
        }

        applyWidths(sheet, headers.length);
        log.info("TOTALS: {} leases, {} period sheets", leases.size(), periodSheetNames.size());
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private boolean isLeaseActiveInPeriod(LeaseMasterDTO lease, Period period) {
        LocalDate leaseStart = lease.getStartDate();
        LocalDate leaseEnd = lease.getEndDate();
        if (leaseStart != null && leaseStart.isAfter(period.end)) return false;
        if (leaseEnd != null && leaseEnd.isBefore(period.start)) return false;
        return true;
    }

    private double getCommissionRate(LeaseMasterDTO lease) {
        if (Boolean.TRUE.equals(lease.getIsBlockProperty())) return 0.0;
        if (lease.getCommissionPercentage() != null) {
            double mgmt = lease.getCommissionPercentage().doubleValue() / 100.0;
            double svc = lease.getServiceFeePercentage() != null
                    ? lease.getServiceFeePercentage().doubleValue() / 100.0 : 0.0;
            return mgmt + svc;
        }
        return commissionConfig.getManagementFeePercent().doubleValue()
             + commissionConfig.getServiceFeePercent().doubleValue();
    }

    /**
     * Calculate rent due for a lease in a period.
     * Replicates the existing logic from ExcelStatementGeneratorService.
     */
    private BigDecimal calculateRentDueForPeriod(LeaseMasterDTO lease, LocalDate periodStart, LocalDate periodEnd) {
        LocalDate leaseStart = lease.getStartDate();
        LocalDate leaseEnd = lease.getEndDate();
        BigDecimal monthlyRent = lease.getMonthlyRent();
        int cycleMonths = lease.getFrequencyMonths() != null ? lease.getFrequencyMonths() : 1;

        if (leaseStart == null || monthlyRent == null) return BigDecimal.ZERO;
        if (leaseStart.isAfter(periodEnd)) return BigDecimal.ZERO;
        if (leaseEnd != null && leaseEnd.isBefore(periodStart)) return BigDecimal.ZERO;

        BigDecimal total = BigDecimal.ZERO;

        if (cycleMonths == 1) {
            // Monthly: iterate each month in the period
            java.time.YearMonth current = java.time.YearMonth.from(periodStart);
            java.time.YearMonth endMonth = java.time.YearMonth.from(periodEnd);

            while (!current.isAfter(endMonth)) {
                LocalDate monthStart = current.atDay(1);
                LocalDate monthEnd = current.atEndOfMonth();

                boolean active = !leaseStart.isAfter(monthEnd) &&
                        (leaseEnd == null || !leaseEnd.isBefore(monthStart));

                if (active) {
                    total = total.add(monthlyRent);
                }
                current = current.plusMonths(1);
            }
        } else {
            // Multi-month cycles: rent due when cycle anniversary falls in period
            LocalDate cycleStart = leaseStart;
            while (cycleStart.isBefore(periodStart)) {
                cycleStart = cycleStart.plusMonths(cycleMonths);
            }

            while (!cycleStart.isAfter(periodEnd)) {
                if (leaseEnd != null && cycleStart.isAfter(leaseEnd)) break;

                // Check for proration if lease ends mid-cycle
                LocalDate cycleEnd = cycleStart.plusMonths(cycleMonths).minusDays(1);
                if (leaseEnd != null && leaseEnd.isBefore(cycleEnd)) {
                    long fullDays = ChronoUnit.DAYS.between(cycleStart, cycleEnd) + 1;
                    long actualDays = ChronoUnit.DAYS.between(cycleStart, leaseEnd) + 1;
                    BigDecimal prorated = monthlyRent.multiply(BigDecimal.valueOf(actualDays))
                            .divide(BigDecimal.valueOf(fullDays), 2, java.math.RoundingMode.HALF_UP);
                    total = total.add(prorated);
                } else {
                    total = total.add(monthlyRent);
                }

                cycleStart = cycleStart.plusMonths(cycleMonths);
            }
        }

        return total;
    }

    /**
     * Get expenses for a lease in a period.
     * Uses outgoing transactions from the data extract service.
     */
    private BigDecimal getExpensesForLease(LeaseMasterDTO lease, LocalDate periodStart, LocalDate periodEnd) {
        try {
            List<site.easy.to.build.crm.dto.statement.BatchPaymentGroupDTO> batches =
                    dataExtractService.extractBatchPaymentGroups(lease, periodStart, periodEnd);

            BigDecimal total = BigDecimal.ZERO;
            for (site.easy.to.build.crm.dto.statement.BatchPaymentGroupDTO batch : batches) {
                for (site.easy.to.build.crm.dto.statement.PaymentDetailDTO expense : batch.getExpenses()) {
                    if (expense.getAmount() != null) {
                        total = total.add(expense.getAmount().abs());
                    }
                }
            }
            return total;
        } catch (Exception e) {
            log.warn("Error getting expenses for lease {}: {}", lease.getLeaseId(), e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private static class Period {
        final LocalDate start;
        final LocalDate end;

        Period(LocalDate start, LocalDate end) {
            this.start = start;
            this.end = end;
        }
    }

    private List<Period> generatePeriods(LocalDate startDate, LocalDate endDate,
                                         int periodStartDay, String frequency) {
        List<Period> periods = new ArrayList<>();

        int monthsPerPeriod;
        switch (frequency.toUpperCase()) {
            case "MONTHLY":    monthsPerPeriod = 1;  break;
            case "QUARTERLY":  monthsPerPeriod = 3;  break;
            case "SEMI_ANNUAL": monthsPerPeriod = 6; break;
            case "ANNUAL":     monthsPerPeriod = 12; break;
            default:           monthsPerPeriod = 3;  break;
        }

        LocalDate periodStart = LocalDate.of(startDate.getYear(), startDate.getMonthValue(),
                Math.min(periodStartDay, startDate.lengthOfMonth()));

        if (periodStart.isAfter(startDate)) {
            periodStart = periodStart.minusMonths(1);
            periodStart = LocalDate.of(periodStart.getYear(), periodStart.getMonthValue(),
                    Math.min(periodStartDay, periodStart.lengthOfMonth()));
        }

        while (!periodStart.isAfter(endDate)) {
            LocalDate nextStart = periodStart.plusMonths(monthsPerPeriod);
            nextStart = LocalDate.of(nextStart.getYear(), nextStart.getMonthValue(),
                    Math.min(periodStartDay, nextStart.lengthOfMonth()));
            LocalDate periodEnd = nextStart.minusDays(1);

            if (periodEnd.isAfter(endDate)) {
                periodEnd = endDate;
            }

            periods.add(new Period(periodStart, periodEnd));
            periodStart = nextStart;
        }

        return periods;
    }

    private String sanitizeSheetName(String name) {
        String sanitized = name.replaceAll("[\\\\/:*?\\[\\]]", "-");
        if (sanitized.length() > 31) {
            sanitized = sanitized.substring(0, 31);
        }
        return sanitized;
    }

    private String str(String value) {
        return value != null ? value : "";
    }

    private String colLetter(int colIndex) {
        if (colIndex < 26) return String.valueOf((char) ('A' + colIndex));
        return String.valueOf((char) ('A' + colIndex / 26 - 1)) + (char) ('A' + colIndex % 26);
    }

    private void applyWidths(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            if (i <= 2) {
                sheet.setColumnWidth(i, 25 * 256);
            } else {
                sheet.setColumnWidth(i, 15 * 256);
            }
        }
    }

    // ========================================================================
    // Styles (shared across all sheets)
    // ========================================================================

    private static class Styles {
        final CellStyle header;
        final CellStyle date;
        final CellStyle currency;
        final CellStyle percent;
        final CellStyle bold;
        final CellStyle boldCurrency;

        Styles(Workbook workbook) {
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            DataFormat format = workbook.createDataFormat();

            this.header = workbook.createCellStyle();
            header.setFont(boldFont);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            this.date = workbook.createCellStyle();
            date.setDataFormat(format.getFormat("yyyy-mm-dd"));

            this.currency = workbook.createCellStyle();
            currency.setDataFormat(format.getFormat("£#,##0.00"));

            this.percent = workbook.createCellStyle();
            percent.setDataFormat(format.getFormat("0.00%"));

            this.bold = workbook.createCellStyle();
            bold.setFont(boldFont);

            this.boldCurrency = workbook.createCellStyle();
            boldCurrency.setFont(boldFont);
            boldCurrency.setDataFormat(format.getFormat("£#,##0.00"));
        }
    }
}

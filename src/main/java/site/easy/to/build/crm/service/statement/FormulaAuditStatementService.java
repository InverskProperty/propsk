package site.easy.to.build.crm.service.statement;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.config.CommissionConfig;
import site.easy.to.build.crm.dto.statement.*;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.repository.PaymentBatchRepository;
import site.easy.to.build.crm.repository.UnifiedAllocationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Formula Audit Statement Generator.
 *
 * Builds on the existing Option C structure (same rich column layout with batch detail,
 * rent_due_from/to, individual rent payments, expenses, reconciliation) but adds:
 *
 * 1. FIXED ROWS: Every lease occupies the same row across ALL period sheets,
 *    even if inactive — shows zeroes. Enables cross-sheet formulas.
 * 2. FORMULA TOTALS: Period totals use SUM formulas, not hardcoded values.
 * 3. CHAINED BALANCES: Opening balance of period N+1 references closing balance
 *    of period N via cross-sheet cell reference.
 * 4. TOTALS SHEET: Cross-references all period sheets with SUM formulas per lease.
 *
 * Sheets produced:
 *   LEASE_MASTER, TRANSACTIONS — same as Option C
 *   [Period tabs] — same rich layout, but with fixed rows and formula totals
 *   TOTALS — new, formula-based cross-sheet summary
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

    @Autowired
    private PaymentBatchRepository paymentBatchRepository;

    @Autowired
    private UnifiedAllocationRepository unifiedAllocationRepository;

    // Period sheet headers — same as existing Option C monthly statement
    private static final String[] PERIOD_HEADERS = {
        "lease_id", "lease_reference", "property_name", "customer_name", "tenant_name",
        "rent_due_from", "rent_due_to", "rent_due", "opening_balance", "period_arrears", "closing_balance",
        "batch_id", "owner_payment_date",
        "rent_1_date", "rent_1_amount",
        "rent_2_date", "rent_2_amount",
        "rent_3_date", "rent_3_amount",
        "rent_4_date", "rent_4_amount",
        "total_rent",
        "commission_rate", "total_commission",
        "expense_1_date", "expense_1_amount", "expense_1_category",
        "expense_2_date", "expense_2_amount", "expense_2_category",
        "expense_3_date", "expense_3_amount", "expense_3_category",
        "expense_4_date", "expense_4_amount", "expense_4_category",
        "total_expenses",
        "net_to_owner"
    };

    // Column indices for key fields (matching PERIOD_HEADERS above)
    private static final int COL_LEASE_ID = 0;
    private static final int COL_LEASE_REF = 1;
    private static final int COL_PROPERTY = 2;
    private static final int COL_CUSTOMER = 3;
    private static final int COL_TENANT = 4;
    private static final int COL_RENT_DUE_FROM = 5;
    private static final int COL_RENT_DUE_TO = 6;
    private static final int COL_RENT_DUE = 7;
    private static final int COL_OPENING_BAL = 8;
    private static final int COL_PERIOD_ARREARS = 9;
    private static final int COL_CLOSING_BAL = 10;
    private static final int COL_BATCH_ID = 11;
    private static final int COL_OWNER_PAY_DATE = 12;
    // rent_1..rent_4 date/amount = cols 13-20
    private static final int COL_TOTAL_RENT = 21;
    private static final int COL_COMM_RATE = 22;
    private static final int COL_TOTAL_COMM = 23;
    // expense_1..expense_4 date/amount/category = cols 24-35
    private static final int COL_TOTAL_EXPENSES = 36;
    private static final int COL_NET_TO_OWNER = 37;

    /**
     * Generate the formula audit workbook.
     */
    public Workbook generateFormulaAuditStatement(Long customerId, LocalDate startDate, LocalDate endDate,
                                                   int periodStartDay, String statementFrequency) {
        long start = System.currentTimeMillis();
        log.info("Formula Audit: Customer {} from {} to {} (day={}, freq={})",
                customerId, startDate, endDate, periodStartDay, statementFrequency);

        // Use SXSSF (streaming) for memory efficiency — same as Option C
        // Window size of 100 is enough for ~30 lease rows + headers + totals + reconciliation
        // SXSSF flushes older rows to disk, keeping only the window in memory
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);
        Styles styles = new Styles(workbook);

        // 1. Extract data
        List<LeaseMasterDTO> allLeases = dataExtractService.extractLeaseMasterForCustomer(customerId);
        log.info("Formula Audit: {} leases for customer {}", allLeases.size(), customerId);

        // Separate non-block leases (block properties handled separately in reconciliation)
        List<LeaseMasterDTO> leases = allLeases.stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsBlockProperty()))
                .collect(Collectors.toList());

        List<TransactionDTO> transactions = dataExtractService.extractAllRentReceivedForCustomer(customerId);
        log.info("Formula Audit: {} transactions", transactions.size());

        // 2. Create LEASE_MASTER sheet
        createLeaseMasterSheet(workbook, allLeases, styles);

        // 3. Create TRANSACTIONS sheet
        createTransactionsSheet(workbook, transactions, styles);
        transactions = null;

        // 4. Generate periods
        List<Period> periods = generatePeriods(startDate, endDate, periodStartDay, statementFrequency);
        log.info("Formula Audit: {} periods", periods.size());

        // 5. Create period sheets — fixed row per lease, full batch detail
        List<String> periodSheetNames = new ArrayList<>();
        // Track which row each lease occupies (1-based Excel row, after header)
        // leaseIdx -> excelRow mapping is simple: leaseIdx + 2 (row 1 = header, row 2 = first lease)
        // But because some leases may have multiple batch rows, we need to track the FIRST row per lease
        // and the TOTAL rows used for the totals formula.

        for (int i = 0; i < periods.size(); i++) {
            Period period = periods.get(i);
            String sheetName = sanitizeSheetName(
                    period.start.format(DateTimeFormatter.ofPattern("MMM dd")) + " - " +
                    period.end.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
            periodSheetNames.add(sheetName);

            String prevSheetName = (i > 0) ? periodSheetNames.get(i - 1) : null;
            createPeriodSheet(workbook, sheetName, leases, period, prevSheetName,
                    startDate, styles, customerId);
        }

        // 6. Create TOTALS sheet — cross-references period tabs
        createTotalsSheet(workbook, leases, periodSheetNames, startDate, styles);

        log.info("Formula Audit: Complete in {}ms, {} sheets",
                System.currentTimeMillis() - start, workbook.getNumberOfSheets());

        return workbook;
    }

    // ========================================================================
    // LEASE_MASTER Sheet (same as Option C)
    // ========================================================================

    private void createLeaseMasterSheet(Workbook workbook, List<LeaseMasterDTO> leases, Styles styles) {
        Sheet sheet = workbook.createSheet("LEASE_MASTER");

        String[] headers = {
            "lease_id", "lease_reference", "property_id", "property_name", "property_address",
            "customer_id", "customer_name", "tenant_name", "start_date", "end_date",
            "monthly_rent", "frequency", "frequency_months",
            "commission_%", "service_fee_%", "payment_day"
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
            row.createCell(col++).setCellValue(lease.getPropertyId() != null ? lease.getPropertyId() : 0);
            row.createCell(col++).setCellValue(str(lease.getPropertyName()));
            row.createCell(col++).setCellValue(str(lease.getPropertyAddress()));
            row.createCell(col++).setCellValue(lease.getCustomerId() != null ? lease.getCustomerId() : 0);
            row.createCell(col++).setCellValue(str(lease.getCustomerName()));
            row.createCell(col++).setCellValue(str(lease.getTenantName()));

            Cell startCell = row.createCell(col++);
            if (lease.getStartDate() != null) { startCell.setCellValue(lease.getStartDate()); startCell.setCellStyle(styles.date); }

            Cell endCell = row.createCell(col++);
            if (lease.getEndDate() != null) { endCell.setCellValue(lease.getEndDate()); endCell.setCellStyle(styles.date); }

            Cell rentCell = row.createCell(col++);
            if (lease.getMonthlyRent() != null) { rentCell.setCellValue(lease.getMonthlyRent().doubleValue()); rentCell.setCellStyle(styles.currency); }

            row.createCell(col++).setCellValue(str(lease.getFrequency()));
            row.createCell(col++).setCellValue(lease.getFrequencyMonths());

            Cell commCell = row.createCell(col++);
            if (lease.getCommissionPercentage() != null) commCell.setCellValue(lease.getCommissionPercentage().doubleValue());

            Cell svcCell = row.createCell(col++);
            if (lease.getServiceFeePercentage() != null) svcCell.setCellValue(lease.getServiceFeePercentage().doubleValue());

            row.createCell(col++).setCellValue(lease.getPaymentDay() != null ? lease.getPaymentDay() : 0);
        }

        applyWidths(sheet, headers.length);
    }

    // ========================================================================
    // TRANSACTIONS Sheet (same as Option C)
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
            if (txn.getTransactionDate() != null) { dateCell.setCellValue(txn.getTransactionDate()); dateCell.setCellStyle(styles.date); }
            row.createCell(2).setCellValue(txn.getInvoiceId() != null ? txn.getInvoiceId() : 0);
            row.createCell(3).setCellValue(txn.getPropertyId() != null ? txn.getPropertyId() : 0);
            row.createCell(4).setCellValue(str(txn.getPropertyName()));
            row.createCell(5).setCellValue(txn.getCustomerId() != null ? txn.getCustomerId() : 0);
            row.createCell(6).setCellValue(str(txn.getCategory()));
            row.createCell(7).setCellValue(str(txn.getTransactionType()));
            Cell amtCell = row.createCell(8);
            if (txn.getAmount() != null) { amtCell.setCellValue(txn.getAmount().doubleValue()); amtCell.setCellStyle(styles.currency); }
            row.createCell(9).setCellValue(str(txn.getDescription()));
            row.createCell(10).setCellValue(str(txn.getLeaseReference()));
        }

        applyWidths(sheet, headers.length);
    }

    // ========================================================================
    // Period Sheet — FIXED ONE ROW PER LEASE with full batch detail
    // ========================================================================

    /**
     * Creates a period sheet with one row per lease (fixed position).
     * Each lease always occupies the same row (leaseIndex + 2 in Excel terms).
     * If a lease has no activity, it still gets a row with zeroes.
     * If a lease has multiple batches, they go into additional rows AFTER the
     * fixed lease rows section, so the lease row positions are stable.
     *
     * Layout:
     *   Row 1: Headers
     *   Row 2..N+1: One row per lease (lease summary with first batch if any)
     *   Row N+2: PERIOD TOTAL (SUM formulas)
     *   Row N+2: PAYMENT RECONCILIATION section
     */
    private void createPeriodSheet(Workbook workbook, String sheetName,
                                   List<LeaseMasterDTO> leases, Period period,
                                   String prevSheetName, LocalDate overallStartDate,
                                   Styles styles, Long customerId) {

        Sheet sheet = workbook.createSheet(sheetName);

        // Header row
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < PERIOD_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(PERIOD_HEADERS[i]);
            cell.setCellStyle(styles.header);
        }

        // Fixed rows: one per lease — ALL batches aggregated into single row
        for (int leaseIdx = 0; leaseIdx < leases.size(); leaseIdx++) {
            LeaseMasterDTO lease = leases.get(leaseIdx);
            int excelRow = leaseIdx + 2; // 1-based, +1 for header
            Row row = sheet.createRow(leaseIdx + 1);

            boolean active = isLeaseActiveInPeriod(lease, period);

            // Get ALL batch data for this lease in this period
            List<BatchPaymentGroupDTO> batchGroups = active
                    ? dataExtractService.extractBatchPaymentGroups(lease, period.start, period.end)
                    : Collections.emptyList();

            // Calculate rent due periods
            List<RentDuePeriodInfo> rentDuePeriods = active
                    ? generateRentDuePeriodsForLease(lease, period.start, period.end)
                    : Collections.emptyList();

            BigDecimal leaseRentDue = BigDecimal.ZERO;
            for (RentDuePeriodInfo rp : rentDuePeriods) {
                leaseRentDue = leaseRentDue.add(rp.rentDue);
            }

            // Aggregate ALL rent payments and expenses across ALL batches
            List<PaymentDetailDTO> allRentPayments = new ArrayList<>();
            List<PaymentDetailDTO> allExpenses = new ArrayList<>();
            List<String> batchIds = new ArrayList<>();
            LocalDate firstPaymentDate = null;

            for (BatchPaymentGroupDTO batch : batchGroups) {
                allRentPayments.addAll(batch.getRentPayments());
                allExpenses.addAll(batch.getExpenses());
                if (batch.getBatchId() != null) batchIds.add(batch.getBatchId());
                if (batch.getOwnerPaymentDate() != null && firstPaymentDate == null) {
                    firstPaymentDate = batch.getOwnerPaymentDate();
                }
            }

            double commissionRate = getCommissionRate(lease);

            // A-E: Lease identification (always present)
            row.createCell(COL_LEASE_ID).setCellValue(lease.getLeaseId());
            row.createCell(COL_LEASE_REF).setCellValue(str(lease.getLeaseReference()));
            row.createCell(COL_PROPERTY).setCellValue(str(lease.getPropertyName()));
            row.createCell(COL_CUSTOMER).setCellValue(str(lease.getCustomerName()));
            row.createCell(COL_TENANT).setCellValue(str(lease.getTenantName()));

            // F-G: rent_due_from/to
            Cell rentDueFromCell = row.createCell(COL_RENT_DUE_FROM);
            if (!rentDuePeriods.isEmpty()) { rentDueFromCell.setCellValue(rentDuePeriods.get(0).periodStart); rentDueFromCell.setCellStyle(styles.date); }
            Cell rentDueToCell = row.createCell(COL_RENT_DUE_TO);
            if (!rentDuePeriods.isEmpty()) { rentDueToCell.setCellValue(rentDuePeriods.get(rentDuePeriods.size() - 1).periodEnd); rentDueToCell.setCellStyle(styles.date); }

            // H: rent_due
            Cell rentDueCell = row.createCell(COL_RENT_DUE);
            rentDueCell.setCellValue(leaseRentDue.doubleValue());
            rentDueCell.setCellStyle(styles.currency);

            // I: opening_balance — chained from previous sheet or calculated for first period
            Cell obCell = row.createCell(COL_OPENING_BAL);
            if (prevSheetName != null) {
                obCell.setCellFormula(String.format("'%s'!K%d", prevSheetName, excelRow));
            } else {
                BigDecimal openingBal = dataExtractService.calculateTenantOpeningBalance(
                        lease.getLeaseId(), lease.getStartDate(), overallStartDate,
                        lease.getMonthlyRent(), lease.getFrequencyMonths(), lease.getEndDate());
                obCell.setCellValue(openingBal != null ? openingBal.doubleValue() : 0);
            }
            obCell.setCellStyle(styles.currency);

            // J: period_arrears = rent_due - total_rent (formula)
            Cell arrearsCell = row.createCell(COL_PERIOD_ARREARS);
            arrearsCell.setCellFormula(String.format("H%d-V%d", excelRow, excelRow));
            arrearsCell.setCellStyle(styles.currency);

            // K: closing_balance = opening_balance + period_arrears (formula)
            Cell cbCell = row.createCell(COL_CLOSING_BAL);
            cbCell.setCellFormula(String.format("I%d+J%d", excelRow, excelRow));
            cbCell.setCellStyle(styles.currency);

            // L: batch_id(s) — comma-separated if multiple
            row.createCell(COL_BATCH_ID).setCellValue(String.join(", ", batchIds));

            // M: owner_payment_date (first batch)
            Cell payDateCell = row.createCell(COL_OWNER_PAY_DATE);
            if (firstPaymentDate != null) { payDateCell.setCellValue(firstPaymentDate); payDateCell.setCellStyle(styles.date); }

            // N-U: rent_1..rent_4 (aggregated across all batches, first 4 payments)
            for (int i = 0; i < 4; i++) {
                int dateCol = 13 + (i * 2);
                int amtCol = 14 + (i * 2);
                if (i < allRentPayments.size()) {
                    PaymentDetailDTO p = allRentPayments.get(i);
                    Cell rdCell = row.createCell(dateCol);
                    if (p.getPaymentDate() != null) { rdCell.setCellValue(p.getPaymentDate()); rdCell.setCellStyle(styles.date); }
                    Cell raCell = row.createCell(amtCol);
                    if (p.getAmount() != null) { raCell.setCellValue(p.getAmount().doubleValue()); raCell.setCellStyle(styles.currency); }
                } else {
                    row.createCell(dateCol);
                    row.createCell(amtCol);
                }
            }

            // V: total_rent — SUM formula of rent columns
            Cell totalRentCell = row.createCell(COL_TOTAL_RENT);
            totalRentCell.setCellFormula(String.format("O%d+Q%d+S%d+U%d", excelRow, excelRow, excelRow, excelRow));
            totalRentCell.setCellStyle(styles.currency);

            // W: commission_rate
            Cell commRateCell = row.createCell(COL_COMM_RATE);
            commRateCell.setCellValue(commissionRate);
            commRateCell.setCellStyle(styles.percent);

            // X: total_commission = total_rent * commission_rate (formula)
            Cell totalCommCell = row.createCell(COL_TOTAL_COMM);
            totalCommCell.setCellFormula(String.format("V%d*W%d", excelRow, excelRow));
            totalCommCell.setCellStyle(styles.currency);

            // Y-AJ: expense_1..expense_4 (aggregated across all batches, first 4 expenses)
            for (int i = 0; i < 4; i++) {
                int dateCol = 24 + (i * 3);
                int amtCol = 25 + (i * 3);
                int catCol = 26 + (i * 3);
                if (i < allExpenses.size()) {
                    PaymentDetailDTO exp = allExpenses.get(i);
                    Cell edCell = row.createCell(dateCol);
                    if (exp.getPaymentDate() != null) { edCell.setCellValue(exp.getPaymentDate()); edCell.setCellStyle(styles.date); }
                    Cell eaCell = row.createCell(amtCol);
                    if (exp.getAmount() != null) { eaCell.setCellValue(Math.abs(exp.getAmount().doubleValue())); eaCell.setCellStyle(styles.currency); }
                    row.createCell(catCol).setCellValue(exp.getCategory() != null ? exp.getCategory() : "");
                } else {
                    row.createCell(dateCol);
                    row.createCell(amtCol);
                    row.createCell(catCol);
                }
            }

            // AK: total_expenses — SUM formula of expense amount columns
            Cell totalExpCell = row.createCell(COL_TOTAL_EXPENSES);
            totalExpCell.setCellFormula(String.format("Z%d+AC%d+AF%d+AI%d", excelRow, excelRow, excelRow, excelRow));
            totalExpCell.setCellStyle(styles.currency);

            // AL: net_to_owner = total_rent - total_commission - total_expenses (formula)
            Cell netCell = row.createCell(COL_NET_TO_OWNER);
            netCell.setCellFormula(String.format("V%d-X%d-AK%d", excelRow, excelRow, excelRow));
            netCell.setCellStyle(styles.currency);
        }

        // PERIOD TOTAL row (right after the fixed lease rows)
        int totalsRowIdx = leases.size() + 1;
        int totalsExcelRow = totalsRowIdx + 1;
        Row totalsRow = sheet.createRow(totalsRowIdx);

        Cell labelCell = totalsRow.createCell(COL_LEASE_ID);
        labelCell.setCellValue("PERIOD TOTAL");
        labelCell.setCellStyle(styles.bold);

        writeSumFormula(totalsRow, COL_RENT_DUE, 2, totalsExcelRow - 1, styles.boldCurrency);
        writeSumFormula(totalsRow, COL_OPENING_BAL, 2, totalsExcelRow - 1, styles.boldCurrency);
        writeSumFormula(totalsRow, COL_PERIOD_ARREARS, 2, totalsExcelRow - 1, styles.boldCurrency);
        writeSumFormula(totalsRow, COL_CLOSING_BAL, 2, totalsExcelRow - 1, styles.boldCurrency);
        writeSumFormula(totalsRow, COL_TOTAL_RENT, 2, totalsExcelRow - 1, styles.boldCurrency);
        writeSumFormula(totalsRow, COL_TOTAL_COMM, 2, totalsExcelRow - 1, styles.boldCurrency);
        writeSumFormula(totalsRow, COL_TOTAL_EXPENSES, 2, totalsExcelRow - 1, styles.boldCurrency);
        writeSumFormula(totalsRow, COL_NET_TO_OWNER, 2, totalsExcelRow - 1, styles.boldCurrency);

        // PAYMENT RECONCILIATION section
        int nextRow = totalsRowIdx + 2;
        Long resolvedOwnerId = resolveActualOwnerId(customerId);
        Set<Long> allPropertyIds = leases.stream()
                .map(LeaseMasterDTO::getPropertyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        nextRow = writeReconciliationSection(sheet, nextRow, resolvedOwnerId, period, allPropertyIds, styles);

        applyWidths(sheet, PERIOD_HEADERS.length);
    }

    // writeBatchColumns removed — batch data is now aggregated directly in createPeriodSheet

    // ========================================================================
    // TOTALS Sheet
    // ========================================================================

    private void createTotalsSheet(Workbook workbook, List<LeaseMasterDTO> leases,
                                   List<String> periodSheetNames, LocalDate overallStartDate, Styles styles) {

        Sheet sheet = workbook.createSheet("TOTALS");

        String[] headers = {
            "lease_id", "lease_reference", "property_name", "tenant_name",
            "total_rent_due", "total_rent_received", "total_arrears",
            "commission_rate", "total_commission", "total_expenses",
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
            int periodExcelRow = leaseIdx + 2; // row in period sheets
            int totalsExcelRow = leaseIdx + 2;
            Row row = sheet.createRow(leaseIdx + 1);

            int col = 0;
            // A: lease_id
            row.createCell(col++).setCellValue(lease.getLeaseId());
            // B: lease_reference
            row.createCell(col++).setCellValue(str(lease.getLeaseReference()));
            // C: property_name
            row.createCell(col++).setCellValue(str(lease.getPropertyName()));
            // D: tenant_name
            row.createCell(col++).setCellValue(str(lease.getTenantName()));

            // E: total_rent_due — SUM of col H across period sheets
            Cell trdCell = row.createCell(col++);
            trdCell.setCellFormula(buildCrossSheetSum(periodSheetNames, "H", periodExcelRow));
            trdCell.setCellStyle(styles.currency);

            // F: total_rent_received — SUM of col V across period sheets
            Cell trrCell = row.createCell(col++);
            trrCell.setCellFormula(buildCrossSheetSum(periodSheetNames, "V", periodExcelRow));
            trrCell.setCellStyle(styles.currency);

            // G: total_arrears = total_rent_due - total_rent_received
            Cell taCell = row.createCell(col++);
            taCell.setCellFormula(String.format("E%d-F%d", totalsExcelRow, totalsExcelRow));
            taCell.setCellStyle(styles.currency);

            // H: commission_rate (same across all periods)
            Cell crCell = row.createCell(col++);
            crCell.setCellValue(getCommissionRate(lease));
            crCell.setCellStyle(styles.percent);

            // I: total_commission — SUM of col X across period sheets
            Cell tcCell = row.createCell(col++);
            tcCell.setCellFormula(buildCrossSheetSum(periodSheetNames, "X", periodExcelRow));
            tcCell.setCellStyle(styles.currency);

            // J: total_expenses — SUM of col AK across period sheets
            Cell teCell = row.createCell(col++);
            teCell.setCellFormula(buildCrossSheetSum(periodSheetNames, "AK", periodExcelRow));
            teCell.setCellStyle(styles.currency);

            // K: total_net_to_owner — SUM of col AL across period sheets
            Cell tnCell = row.createCell(col++);
            tnCell.setCellFormula(buildCrossSheetSum(periodSheetNames, "AL", periodExcelRow));
            tnCell.setCellStyle(styles.currency);

            // L: first_opening_balance — from first period sheet col I
            Cell obCell = row.createCell(col++);
            if (!periodSheetNames.isEmpty()) {
                obCell.setCellFormula(String.format("'%s'!I%d", periodSheetNames.get(0), periodExcelRow));
            } else {
                obCell.setCellValue(0);
            }
            obCell.setCellStyle(styles.currency);

            // M: last_closing_balance — from last period sheet col K
            Cell cbCell = row.createCell(col++);
            if (!periodSheetNames.isEmpty()) {
                String lastSheet = periodSheetNames.get(periodSheetNames.size() - 1);
                cbCell.setCellFormula(String.format("'%s'!K%d", lastSheet, periodExcelRow));
            } else {
                cbCell.setCellValue(0);
            }
            cbCell.setCellStyle(styles.currency);
        }

        // GRAND TOTAL row
        int totalsRowNum = leases.size() + 1;
        int totalsExcelRow = totalsRowNum + 1;
        Row totalsRow = sheet.createRow(totalsRowNum);

        Cell labelCell = totalsRow.createCell(0);
        labelCell.setCellValue("GRAND TOTAL");
        labelCell.setCellStyle(styles.bold);

        // SUM columns E through M
        for (int c = 4; c <= 12; c++) {
            if (c == 7) continue; // skip commission_rate
            Cell cell = totalsRow.createCell(c);
            String colLetter = colLetter(c);
            cell.setCellFormula(String.format("SUM(%s2:%s%d)", colLetter, colLetter, totalsExcelRow - 1));
            cell.setCellStyle(styles.boldCurrency);
        }

        applyWidths(sheet, headers.length);
        log.info("TOTALS: {} leases, {} period sheets", leases.size(), periodSheetNames.size());
    }

    // ========================================================================
    // Payment Reconciliation section (embedded in each period sheet)
    // ========================================================================

    @SuppressWarnings("unchecked")
    private int writeReconciliationSection(Sheet sheet, int startRow, Long resolvedOwnerId,
                                            Period period, Set<Long> propertyIds, Styles styles) {
        int rowNum = startRow;

        Row sectionHeader = sheet.createRow(rowNum++);
        sectionHeader.createCell(0).setCellValue("PAYMENT RECONCILIATION");
        sectionHeader.getCell(0).setCellStyle(styles.bold);

        Row periodRow = sheet.createRow(rowNum++);
        periodRow.createCell(0).setCellValue("Period: " + period.start + " to " + period.end);

        rowNum++; // blank

        // Get batch allocation statuses
        List<BatchAllocationStatusDTO> batchStatuses;
        try {
            batchStatuses = dataExtractService.getBatchesWithPeriodAllocations(
                    resolvedOwnerId, period.start, period.end, propertyIds);
        } catch (Exception e) {
            log.warn("Could not get batch allocations: {}", e.getMessage());
            batchStatuses = Collections.emptyList();
        }

        // ===== ALLOCATION STATUS SUMMARY =====
        try {
            Row statusHeader = sheet.createRow(rowNum++);
            statusHeader.createCell(0).setCellValue("ALLOCATION STATUS");
            statusHeader.getCell(0).setCellStyle(styles.bold);

            Map<String, Object> allocationSummary = dataExtractService.getAllocationStatusSummary(
                    resolvedOwnerId, period.start, period.end);

            Map<String, Object> priorAllocated = (Map<String, Object>) allocationSummary.get("allocatedBeforePeriod");
            Map<String, Object> periodAllocated = (Map<String, Object>) allocationSummary.get("allocatedDuringPeriod");
            Map<String, Object> unallocated = (Map<String, Object>) allocationSummary.get("unallocatedAtEnd");

            Row priorRow = sheet.createRow(rowNum++);
            priorRow.createCell(0).setCellValue("  Allocated before period start:");
            Cell priorCell = priorRow.createCell(1);
            priorCell.setCellValue(((Number) priorAllocated.get("amount")).doubleValue());
            priorCell.setCellStyle(styles.currency);
            priorRow.createCell(2).setCellValue("(" + priorAllocated.get("count") + " items)");

            Row periodAllocRow = sheet.createRow(rowNum++);
            periodAllocRow.createCell(0).setCellValue("  Allocated during this period:");
            Cell periodAllocCell = periodAllocRow.createCell(1);
            periodAllocCell.setCellValue(((Number) periodAllocated.get("amount")).doubleValue());
            periodAllocCell.setCellStyle(styles.currency);
            periodAllocRow.createCell(2).setCellValue("(" + periodAllocated.get("count") + " items)");

            Row unallocRow = sheet.createRow(rowNum++);
            unallocRow.createCell(0).setCellValue("  For Future Periods (after this period):");
            unallocRow.getCell(0).setCellStyle(styles.bold);
            Cell unallocCell = unallocRow.createCell(1);
            unallocCell.setCellValue(((Number) unallocated.get("amount")).doubleValue());
            unallocCell.setCellStyle(styles.boldCurrency);
            unallocRow.createCell(2).setCellValue("(" + unallocated.get("count") + " items)");
        } catch (Exception e) {
            log.warn("Could not write allocation status: {}", e.getMessage());
        }

        rowNum++; // blank

        // ===== OWNER PAYMENTS THIS PERIOD =====
        Row paymentsHeader = sheet.createRow(rowNum++);
        paymentsHeader.createCell(0).setCellValue("OWNER PAYMENTS THIS PERIOD");
        paymentsHeader.getCell(0).setCellStyle(styles.bold);

        Row paymentsSubHeader = sheet.createRow(rowNum++);
        paymentsSubHeader.createCell(0).setCellValue("(Batches containing allocations from transactions in this period)");

        rowNum++; // blank

        // Period category headers
        Row periodCatRow = sheet.createRow(rowNum++);
        periodCatRow.createCell(6).setCellValue("Prior");
        periodCatRow.getCell(6).setCellStyle(styles.bold);
        periodCatRow.createCell(12).setCellValue("This Period");
        periodCatRow.getCell(12).setCellStyle(styles.bold);
        periodCatRow.createCell(18).setCellValue("Future");
        periodCatRow.getCell(18).setCellStyle(styles.bold);

        // Column headers
        Row colHeaderRow = sheet.createRow(rowNum++);
        String[] payHeaders = {"Batch ID", "Payment Date", "Gross Income", "Commission", "Expenses", "Net to Owner",
                               "Property", "Txn Date", "Income", "Expense", "Commission", "Net",
                               "Property", "Txn Date", "Income", "Expense", "Commission", "Net",
                               "Property", "Txn Date", "Income", "Expense", "Commission", "Net"};
        for (int i = 0; i < payHeaders.length; i++) {
            Cell cell = colHeaderRow.createCell(i);
            cell.setCellValue(payHeaders[i]);
            cell.setCellStyle(styles.header);
        }

        if (batchStatuses.isEmpty()) {
            Row noPayments = sheet.createRow(rowNum++);
            noPayments.createCell(0).setCellValue("(No payments made during this period)");
            return rowNum;
        }

        // ===== PRE-CALCULATE ALL TOTALS (so we can write the TOTALS row first) =====
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalComm = BigDecimal.ZERO;
        BigDecimal totalExp = BigDecimal.ZERO;
        BigDecimal totalPayments = BigDecimal.ZERO;
        double priorIncome = 0, priorExpense = 0, priorCommission = 0, priorNet = 0;
        double currentIncome = 0, currentExpense = 0, currentCommission = 0, currentNet = 0;
        double futureIncome = 0, futureExpense = 0, futureCommission = 0, futureNet = 0;

        // Pre-process all batches to calculate totals and classify allocations
        List<ProcessedBatch> processedBatches = new ArrayList<>();

        for (BatchAllocationStatusDTO batch : batchStatuses) {
            ProcessedBatch pb = new ProcessedBatch(batch);

            for (BatchAllocationStatusDTO.AllocationDetailDTO alloc : batch.getAllocations()) {
                String allocType = alloc.getAllocationType();
                if ("COMMISSION".equals(allocType)) continue; // already in OWNER via commission_amount

                double allocInc = 0, allocExp = 0, allocComm = 0;
                if ("OWNER".equals(allocType) && alloc.getAmount() != null) {
                    allocInc = alloc.getGrossAmount() != null ? alloc.getGrossAmount().abs().doubleValue() : alloc.getAmount().abs().doubleValue();
                    if (alloc.getCommissionAmount() != null) allocComm = alloc.getCommissionAmount().abs().doubleValue();
                } else if (("EXPENSE".equals(allocType) || "DISBURSEMENT".equals(allocType)) && alloc.getAmount() != null) {
                    allocExp = alloc.getAmount().doubleValue();
                }
                double allocNet = allocInc - allocExp - allocComm;

                String classification = alloc.getPeriodClassification();
                if ("B/F".equals(classification)) {
                    pb.priorAllocations.add(alloc);
                    pb.priorIncome += allocInc; pb.priorExpense += allocExp; pb.priorCommission += allocComm; pb.priorNet += allocNet;
                    priorIncome += allocInc; priorExpense += allocExp; priorCommission += allocComm; priorNet += allocNet;
                } else if ("FUTURE".equals(classification)) {
                    pb.futureAllocations.add(alloc);
                    pb.futureIncome += allocInc; pb.futureExpense += allocExp; pb.futureCommission += allocComm; pb.futureNet += allocNet;
                    futureIncome += allocInc; futureExpense += allocExp; futureCommission += allocComm; futureNet += allocNet;
                } else {
                    pb.currentAllocations.add(alloc);
                    pb.currentIncome += allocInc; pb.currentExpense += allocExp; pb.currentCommission += allocComm; pb.currentNet += allocNet;
                    currentIncome += allocInc; currentExpense += allocExp; currentCommission += allocComm; currentNet += allocNet;
                }
            }

            totalGross = totalGross.add(batch.getGrossIncome());
            totalComm = totalComm.add(batch.getCommission());
            totalExp = totalExp.add(batch.getExpenses());
            totalPayments = totalPayments.add(batch.getNetToOwner());
            processedBatches.add(pb);
        }

        // ===== TOTALS ROW FIRST (above batch details) =====
        Row totalsRow = sheet.createRow(rowNum++);
        Cell totalsLabel = totalsRow.createCell(0);
        totalsLabel.setCellValue("TOTALS");
        totalsLabel.setCellStyle(styles.bold);

        Cell tGrossCell = totalsRow.createCell(2);
        tGrossCell.setCellValue(totalGross.doubleValue());
        tGrossCell.setCellStyle(styles.boldCurrency);
        Cell tCommCell = totalsRow.createCell(3);
        tCommCell.setCellValue(totalComm.doubleValue());
        tCommCell.setCellStyle(styles.boldCurrency);
        Cell tExpCell = totalsRow.createCell(4);
        tExpCell.setCellValue(totalExp.doubleValue());
        tExpCell.setCellStyle(styles.boldCurrency);
        Cell tNetCell = totalsRow.createCell(5);
        tNetCell.setCellValue(totalPayments.doubleValue());
        tNetCell.setCellStyle(styles.boldCurrency);

        // Prior totals
        writeCurrencyCell(totalsRow, 8, priorIncome, styles.boldCurrency);
        writeCurrencyCell(totalsRow, 9, priorExpense, styles.boldCurrency);
        writeCurrencyCell(totalsRow, 10, priorCommission, styles.boldCurrency);
        writeCurrencyCell(totalsRow, 11, priorNet, styles.boldCurrency);
        // This Period totals
        writeCurrencyCell(totalsRow, 14, currentIncome, styles.boldCurrency);
        writeCurrencyCell(totalsRow, 15, currentExpense, styles.boldCurrency);
        writeCurrencyCell(totalsRow, 16, currentCommission, styles.boldCurrency);
        writeCurrencyCell(totalsRow, 17, currentNet, styles.boldCurrency);
        // Future totals
        writeCurrencyCell(totalsRow, 20, futureIncome, styles.boldCurrency);
        writeCurrencyCell(totalsRow, 21, futureExpense, styles.boldCurrency);
        writeCurrencyCell(totalsRow, 22, futureCommission, styles.boldCurrency);
        writeCurrencyCell(totalsRow, 23, futureNet, styles.boldCurrency);

        rowNum++; // blank row between totals and details

        // ===== BATCH DETAIL ROWS (below the totals) =====
        for (ProcessedBatch pb : processedBatches) {
            BatchAllocationStatusDTO batch = pb.batch;

            // Batch header row
            Row batchRow = sheet.createRow(rowNum++);
            batchRow.createCell(0).setCellValue(str(batch.getBatchId()));
            Cell payDateCell = batchRow.createCell(1);
            if (batch.getPaymentDate() != null) {
                payDateCell.setCellValue(batch.getPaymentDate());
                payDateCell.setCellStyle(styles.date);
            } else {
                payDateCell.setCellValue("Pending");
            }
            writeCurrencyCell(batchRow, 2, batch.getGrossIncome().doubleValue(), styles.boldCurrency);
            writeCurrencyCell(batchRow, 3, batch.getCommission().doubleValue(), styles.currency);
            writeCurrencyCell(batchRow, 4, batch.getExpenses().doubleValue(), styles.currency);
            writeCurrencyCell(batchRow, 5, batch.getNetToOwner().doubleValue(), styles.boldCurrency);

            // Period breakdown sub-rows
            Row priorSumRow = sheet.createRow(rowNum++);
            priorSumRow.createCell(1).setCellValue("Prior:");
            writeCurrencyCell(priorSumRow, 2, pb.priorIncome, styles.currency);
            writeCurrencyCell(priorSumRow, 3, pb.priorCommission, styles.currency);
            writeCurrencyCell(priorSumRow, 4, pb.priorExpense, styles.currency);
            writeCurrencyCell(priorSumRow, 5, pb.priorNet, styles.currency);

            Row currSumRow = sheet.createRow(rowNum++);
            currSumRow.createCell(1).setCellValue("This Period:");
            writeCurrencyCell(currSumRow, 2, pb.currentIncome, styles.currency);
            writeCurrencyCell(currSumRow, 3, pb.currentCommission, styles.currency);
            writeCurrencyCell(currSumRow, 4, pb.currentExpense, styles.currency);
            writeCurrencyCell(currSumRow, 5, pb.currentNet, styles.currency);

            Row futSumRow = sheet.createRow(rowNum++);
            futSumRow.createCell(1).setCellValue("Future:");
            writeCurrencyCell(futSumRow, 2, pb.futureIncome, styles.currency);
            writeCurrencyCell(futSumRow, 3, pb.futureCommission, styles.currency);
            writeCurrencyCell(futSumRow, 4, pb.futureExpense, styles.currency);
            writeCurrencyCell(futSumRow, 5, pb.futureNet, styles.currency);

            // Allocation detail rows (Prior | This Period | Future side by side)
            int maxRows = Math.max(1, Math.max(pb.priorAllocations.size(),
                    Math.max(pb.currentAllocations.size(), pb.futureAllocations.size())));

            for (int rowIdx = 0; rowIdx < maxRows; rowIdx++) {
                Row detailRow = sheet.createRow(rowNum++);

                // Prior (cols 6-11)
                if (rowIdx < pb.priorAllocations.size()) {
                    writeAllocationDetail(detailRow, 6, pb.priorAllocations.get(rowIdx), styles);
                }
                // This Period (cols 12-17)
                if (rowIdx < pb.currentAllocations.size()) {
                    writeAllocationDetail(detailRow, 12, pb.currentAllocations.get(rowIdx), styles);
                }
                // Future (cols 18-23)
                if (rowIdx < pb.futureAllocations.size()) {
                    writeAllocationDetail(detailRow, 18, pb.futureAllocations.get(rowIdx), styles);
                }
            }

            rowNum++; // blank between batches
        }

        return rowNum;
    }

    /**
     * Write a single allocation detail into 6 columns starting at startCol.
     * Columns: Property, Txn Date, Income, Expense, Commission, Net
     */
    private void writeAllocationDetail(Row row, int startCol, BatchAllocationStatusDTO.AllocationDetailDTO alloc, Styles styles) {
        row.createCell(startCol).setCellValue(str(alloc.getPropertyName()));

        Cell txnDateCell = row.createCell(startCol + 1);
        if (alloc.getTransactionDate() != null) {
            txnDateCell.setCellValue(alloc.getTransactionDate());
            txnDateCell.setCellStyle(styles.date);
        }

        double income = 0, expense = 0, commission = 0;
        String type = alloc.getAllocationType();
        if ("OWNER".equals(type) && alloc.getAmount() != null) {
            income = alloc.getGrossAmount() != null ? alloc.getGrossAmount().abs().doubleValue() : alloc.getAmount().abs().doubleValue();
            Cell incCell = row.createCell(startCol + 2);
            incCell.setCellValue(income);
            incCell.setCellStyle(styles.currency);
            if (alloc.getCommissionAmount() != null) {
                commission = alloc.getCommissionAmount().abs().doubleValue();
                Cell commCell = row.createCell(startCol + 4);
                commCell.setCellValue(commission);
                commCell.setCellStyle(styles.currency);
            }
        } else if (("EXPENSE".equals(type) || "DISBURSEMENT".equals(type)) && alloc.getAmount() != null) {
            expense = alloc.getAmount().doubleValue();
            Cell expCell = row.createCell(startCol + 3);
            expCell.setCellValue(expense);
            expCell.setCellStyle(styles.currency);
        }

        double net = income - Math.abs(expense) - commission;
        Cell netCell = row.createCell(startCol + 5);
        netCell.setCellValue(net);
        netCell.setCellStyle(styles.currency);
    }

    private void writeCurrencyCell(Row row, int col, double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    /** Pre-processed batch with allocations classified by period */
    private static class ProcessedBatch {
        final BatchAllocationStatusDTO batch;
        final List<BatchAllocationStatusDTO.AllocationDetailDTO> priorAllocations = new ArrayList<>();
        final List<BatchAllocationStatusDTO.AllocationDetailDTO> currentAllocations = new ArrayList<>();
        final List<BatchAllocationStatusDTO.AllocationDetailDTO> futureAllocations = new ArrayList<>();
        double priorIncome, priorExpense, priorCommission, priorNet;
        double currentIncome, currentExpense, currentCommission, currentNet;
        double futureIncome, futureExpense, futureCommission, futureNet;

        ProcessedBatch(BatchAllocationStatusDTO batch) { this.batch = batch; }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private String buildCrossSheetSum(List<String> sheetNames, String colLetter, int excelRow) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sheetNames.size(); i++) {
            if (i > 0) sb.append("+");
            sb.append(String.format("'%s'!%s%d", sheetNames.get(i), colLetter, excelRow));
        }
        return sb.toString();
    }

    private void writeSumFormula(Row row, int colIdx, int fromRow, int toRow, CellStyle style) {
        Cell cell = row.createCell(colIdx);
        String colLetter = colLetter(colIdx);
        cell.setCellFormula(String.format("SUM(%s%d:%s%d)", colLetter, fromRow, colLetter, toRow));
        cell.setCellStyle(style);
    }

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

    private Long resolveActualOwnerId(Long customerId) {
        try {
            Customer customer = customerRepository.findById(customerId).orElse(null);
            if (customer == null) return customerId;
            if ((customer.getCustomerType() == CustomerType.DELEGATED_USER ||
                 customer.getCustomerType() == CustomerType.MANAGER) &&
                customer.getManagesOwnerId() != null) {
                return customer.getManagesOwnerId();
            }
            return customerId;
        } catch (Exception e) {
            return customerId;
        }
    }

    /**
     * Generate rent due periods for a lease within a statement period.
     * Simplified version of the existing ExcelStatementGeneratorService logic.
     */
    private List<RentDuePeriodInfo> generateRentDuePeriodsForLease(LeaseMasterDTO lease,
                                                                     LocalDate periodStart, LocalDate periodEnd) {
        List<RentDuePeriodInfo> periods = new ArrayList<>();

        LocalDate leaseStart = lease.getStartDate();
        LocalDate leaseEnd = lease.getEndDate();
        BigDecimal monthlyRent = lease.getMonthlyRent();
        int cycleMonths = lease.getFrequencyMonths() != null ? lease.getFrequencyMonths() : 1;
        int paymentDay = lease.getPaymentDay() != null ? lease.getPaymentDay() :
                (leaseStart != null ? leaseStart.getDayOfMonth() : 1);

        if (leaseStart == null || monthlyRent == null) return periods;
        if (leaseStart.isAfter(periodEnd)) return periods;
        if (leaseEnd != null && leaseEnd.isBefore(periodStart)) return periods;

        if (cycleMonths == 1) {
            LocalDate cycleStart = leaseStart;
            if (leaseStart.isBefore(periodStart)) {
                YearMonth targetMonth = YearMonth.from(periodStart);
                int effectiveDay = Math.min(paymentDay, targetMonth.lengthOfMonth());
                cycleStart = targetMonth.atDay(effectiveDay);
                if (cycleStart.isBefore(periodStart)) {
                    targetMonth = targetMonth.plusMonths(1);
                    effectiveDay = Math.min(paymentDay, targetMonth.lengthOfMonth());
                    cycleStart = targetMonth.atDay(effectiveDay);
                }
            }

            while (!cycleStart.isAfter(periodEnd)) {
                YearMonth nextMonth = YearMonth.from(cycleStart).plusMonths(1);
                int effectiveDay = Math.min(paymentDay, nextMonth.lengthOfMonth());
                LocalDate nextCycleStart = nextMonth.atDay(effectiveDay);
                LocalDate cycleEnd = nextCycleStart.minusDays(1);

                boolean inPeriod = !cycleStart.isBefore(periodStart) && !cycleStart.isAfter(periodEnd);
                boolean overlapsLease = !cycleEnd.isBefore(leaseStart) &&
                        (leaseEnd == null || !cycleStart.isAfter(leaseEnd));

                if (inPeriod && overlapsLease) {
                    LocalDate displayStart = cycleStart.isBefore(leaseStart) ? leaseStart : cycleStart;
                    LocalDate displayEnd = (leaseEnd != null && leaseEnd.isBefore(cycleEnd)) ? leaseEnd : cycleEnd;

                    boolean leaseEndsMidCycle = leaseEnd != null && leaseEnd.isBefore(cycleEnd) && !leaseEnd.isBefore(cycleStart);
                    BigDecimal rent;
                    if (leaseEndsMidCycle) {
                        int totalDays = (int) ChronoUnit.DAYS.between(cycleStart, cycleEnd) + 1;
                        int leaseDays = (int) ChronoUnit.DAYS.between(cycleStart, leaseEnd) + 1;
                        rent = BigDecimal.valueOf(Math.round(monthlyRent.doubleValue() * leaseDays / totalDays * 100.0) / 100.0);
                    } else {
                        rent = monthlyRent;
                    }
                    periods.add(new RentDuePeriodInfo(displayStart, displayEnd, rent));
                }
                cycleStart = nextCycleStart;
            }
        } else {
            LocalDate cycleStart = leaseStart;
            while (cycleStart.isBefore(periodStart)) {
                cycleStart = cycleStart.plusMonths(cycleMonths);
            }
            while (!cycleStart.isAfter(periodEnd)) {
                LocalDate cycleEnd = cycleStart.plusMonths(cycleMonths).minusDays(1);
                BigDecimal rent;
                if (leaseEnd != null && leaseEnd.isBefore(cycleEnd) && !leaseEnd.isBefore(cycleStart)) {
                    double monthlyRate = monthlyRent.doubleValue() / cycleMonths;
                    long fullMonths = ChronoUnit.MONTHS.between(cycleStart, leaseEnd.plusDays(1));
                    LocalDate lastFullEnd = cycleStart.plusMonths(fullMonths);
                    long remDays = ChronoUnit.DAYS.between(lastFullEnd, leaseEnd) + 1;
                    int daysInFinal = leaseEnd.lengthOfMonth();
                    double prorated = (fullMonths * monthlyRate) + ((double) remDays / daysInFinal * monthlyRate);
                    rent = BigDecimal.valueOf(Math.round(prorated * 100.0) / 100.0);
                } else {
                    rent = monthlyRent;
                }
                periods.add(new RentDuePeriodInfo(cycleStart,
                        leaseEnd != null && leaseEnd.isBefore(cycleEnd) ? leaseEnd : cycleEnd, rent));
                cycleStart = cycleStart.plusMonths(cycleMonths);
            }
        }
        return periods;
    }

    // ========================================================================
    // Inner classes
    // ========================================================================

    private static class Period {
        final LocalDate start;
        final LocalDate end;
        Period(LocalDate start, LocalDate end) { this.start = start; this.end = end; }
    }

    private static class RentDuePeriodInfo {
        final LocalDate periodStart;
        final LocalDate periodEnd;
        final BigDecimal rentDue;
        RentDuePeriodInfo(LocalDate start, LocalDate end, BigDecimal rent) {
            this.periodStart = start; this.periodEnd = end; this.rentDue = rent;
        }
    }

    private List<Period> generatePeriods(LocalDate startDate, LocalDate endDate,
                                         int periodStartDay, String frequency) {
        List<Period> periods = new ArrayList<>();
        int monthsPerPeriod;
        switch (frequency.toUpperCase()) {
            case "MONTHLY":     monthsPerPeriod = 1;  break;
            case "QUARTERLY":   monthsPerPeriod = 3;  break;
            case "SEMI_ANNUAL": monthsPerPeriod = 6;  break;
            case "ANNUAL":      monthsPerPeriod = 12; break;
            default:            monthsPerPeriod = 3;  break;
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
            if (periodEnd.isAfter(endDate)) periodEnd = endDate;
            periods.add(new Period(periodStart, periodEnd));
            periodStart = nextStart;
        }
        return periods;
    }

    private String sanitizeSheetName(String name) {
        String sanitized = name.replaceAll("[\\\\/:*?\\[\\]]", "-");
        if (sanitized.length() > 31) sanitized = sanitized.substring(0, 31);
        return sanitized;
    }

    private String str(String value) { return value != null ? value : ""; }

    private String colLetter(int colIndex) {
        if (colIndex < 26) return String.valueOf((char) ('A' + colIndex));
        return String.valueOf((char) ('A' + colIndex / 26 - 1)) + (char) ('A' + colIndex % 26);
    }

    private void applyWidths(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            if (i <= 4) sheet.setColumnWidth(i, 25 * 256);
            else sheet.setColumnWidth(i, 15 * 256);
        }
    }

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

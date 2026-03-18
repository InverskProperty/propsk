package site.easy.to.build.crm.service.statement;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
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
 * Formula Audit Statement Generator (v2 — slim layout).
 *
 * Architecture:
 *   RENT_RECEIVED sheet — every rent payment as a row (lease_reference, date, amount)
 *   EXPENSES sheet — every expense as a row (lease_reference, date, amount, category)
 *   Period sheets use SUMIFS formulas pointing at those sheets for totals.
 *   Detail text cells give at-a-glance human-readable summaries.
 *
 * Key design:
 * 1. FIXED ROWS: Every lease occupies the same row across ALL period sheets.
 * 2. FORMULA TOTALS via SUMIFS: total_rent and total_expenses are Excel formulas
 *    that query the RENT_RECEIVED/EXPENSES sheets by lease_reference + date range.
 * 3. CHAINED BALANCES: Opening balance of period N+1 references closing balance
 *    of period N via cross-sheet cell reference.
 * 4. DETAIL TEXT: rent_detail and expense_detail columns provide human-readable
 *    summaries (date: amount; date: amount (category); ...) for quick review.
 * 5. TOTALS SHEET: Cross-references all period sheets with SUM formulas per lease.
 *
 * Period sheet columns (20 columns, A-T):
 *   A: lease_id          B: lease_reference    C: property_name
 *   D: customer_name     E: tenant_name
 *   F: rent_due_from     G: rent_due_to        H: rent_due
 *   I: opening_balance
 *   J: total_rent        (SUMIFS -> RENT_RECEIVED)
 *   K: rent_detail       (text summary)
 *   L: period_arrears    (formula: H-J)
 *   M: closing_balance   (formula: I+L)
 *   N: commission_rate
 *   O: total_commission  (formula: J*N)
 *   P: total_expenses    (SUMIFS -> EXPENSES)
 *   Q: expense_detail    (text summary)
 *   R: net_to_owner      (formula: J-O-P)
 *   S: batch_id          T: owner_payment_date
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

    // Period sheet headers — slim v2 layout
    private static final String[] PERIOD_HEADERS = {
        "lease_id", "lease_reference", "property_name", "customer_name", "tenant_name",
        "rent_due_from", "rent_due_to", "rent_due",
        "opening_balance",
        "total_rent", "rent_detail",
        "period_arrears", "closing_balance",
        "commission_rate", "total_commission",
        "total_expenses", "expense_detail",
        "net_to_owner",
        "batch_id", "owner_payment_date"
    };

    // Column indices for period sheets
    private static final int COL_LEASE_ID = 0;
    private static final int COL_LEASE_REF = 1;
    private static final int COL_PROPERTY = 2;
    private static final int COL_CUSTOMER = 3;
    private static final int COL_TENANT = 4;
    private static final int COL_RENT_DUE_FROM = 5;
    private static final int COL_RENT_DUE_TO = 6;
    private static final int COL_RENT_DUE = 7;
    private static final int COL_OPENING_BAL = 8;
    private static final int COL_TOTAL_RENT = 9;
    private static final int COL_RENT_DETAIL = 10;
    private static final int COL_PERIOD_ARREARS = 11;
    private static final int COL_CLOSING_BAL = 12;
    private static final int COL_COMM_RATE = 13;
    private static final int COL_TOTAL_COMM = 14;
    private static final int COL_TOTAL_EXPENSES = 15;
    private static final int COL_EXPENSE_DETAIL = 16;
    private static final int COL_NET_TO_OWNER = 17;
    private static final int COL_BATCH_ID = 18;
    private static final int COL_OWNER_PAY_DATE = 19;

    // RENT_RECEIVED sheet column indices (for SUMIFS references)
    // A: transaction_id, B: transaction_date, C: lease_reference, D: property_name, E: amount, F: category, G: description
    private static final String RENT_SHEET_NAME = "RENT_RECEIVED";
    private static final String EXPENSE_SHEET_NAME = "EXPENSES";
    // ALLOCATIONS sheet: A: lease_reference, B: allocation_type, C: amount, D: gross_amount,
    //   E: commission_amount, F: batch_id, G: paid_date, H: beneficiary, I: property
    private static final String ALLOC_SHEET_NAME = "ALLOCATIONS";

    /**
     * Generate the formula audit workbook.
     */
    public Workbook generateFormulaAuditStatement(Long customerId, LocalDate startDate, LocalDate endDate,
                                                   int periodStartDay, String statementFrequency) {
        long start = System.currentTimeMillis();
        log.info("Formula Audit v2: Customer {} from {} to {} (day={}, freq={})",
                customerId, startDate, endDate, periodStartDay, statementFrequency);

        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);
        Styles styles = new Styles(workbook);

        // 1. Extract data
        List<LeaseMasterDTO> allLeases = dataExtractService.extractLeaseMasterForCustomer(customerId);
        log.info("Formula Audit v2: {} leases for customer {}", allLeases.size(), customerId);

        List<LeaseMasterDTO> leases = allLeases.stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsBlockProperty()))
                .filter(l -> !Boolean.TRUE.equals(l.getIsParking()))
                .collect(Collectors.toList());

        List<TransactionDTO> rentTransactions = dataExtractService.extractAllRentReceivedForCustomer(customerId);
        log.info("Formula Audit v2: {} rent transactions", rentTransactions.size());

        List<TransactionDTO> expenseTransactions = dataExtractService.extractAllExpensesForCustomer(customerId);
        log.info("Formula Audit v2: {} expense transactions", expenseTransactions.size());

        // 2. Create LEASE_MASTER sheet
        createLeaseMasterSheet(workbook, allLeases, styles);

        // 3. Create RENT_RECEIVED sheet (replaces old TRANSACTIONS)
        int rentDataRows = createRentReceivedSheet(workbook, rentTransactions, styles);
        rentTransactions = null; // free memory

        // 4. Create EXPENSES sheet
        int expenseDataRows = createExpensesSheet(workbook, expenseTransactions, styles);
        expenseTransactions = null; // free memory

        // 4b. Create ALLOCATIONS sheet
        List<Long> leaseIds = leases.stream().map(LeaseMasterDTO::getLeaseId).collect(Collectors.toList());
        List<Map<String, Object>> allocations = dataExtractService.extractAllocationsForLeases(leaseIds);
        int allocDataRows = createAllocationsSheet(workbook, allocations, styles);
        allocations = null; // free memory

        // 4c. Create OWNER_PAYMENTS sheet (uses same leaseIds as TOTALS for alignment)
        List<Map<String, Object>> ownerPayments = dataExtractService.extractOwnerPayments(leaseIds);
        createOwnerPaymentsSheet(workbook, ownerPayments, styles);
        ownerPayments = null; // free memory

        // 4d. Extract block property leases for service charge sections
        List<LeaseMasterDTO> blockPropertyLeases = dataExtractService.extractBlockPropertyLeases(customerId);
        log.info("Formula Audit v2: {} block property leases", blockPropertyLeases.size());

        // 5. Generate periods
        List<Period> periods = generatePeriods(startDate, endDate, periodStartDay, statementFrequency);
        log.info("Formula Audit v2: {} periods", periods.size());

        // 6. Create period sheets — fixed row per lease, SUMIFS formulas
        List<String> periodSheetNames = new ArrayList<>();

        for (int i = 0; i < periods.size(); i++) {
            Period period = periods.get(i);
            String sheetName = sanitizeSheetName(
                    period.start.format(DateTimeFormatter.ofPattern("MMM dd")) + " - " +
                    period.end.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
            periodSheetNames.add(sheetName);

            String prevSheetName = (i > 0) ? periodSheetNames.get(i - 1) : null;
            createPeriodSheet(workbook, sheetName, leases, period, prevSheetName,
                    startDate, styles, customerId, rentDataRows, expenseDataRows,
                    blockPropertyLeases);
        }

        // 7. Create TOTALS sheet (with allocation reconciliation and service charge summary)
        createTotalsSheet(workbook, leases, periodSheetNames, startDate, styles, allocDataRows,
                blockPropertyLeases, endDate);

        log.info("Formula Audit v2: Complete in {}ms, {} sheets",
                System.currentTimeMillis() - start, workbook.getNumberOfSheets());

        return workbook;
    }

    // ========================================================================
    // LEASE_MASTER Sheet
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
    // RENT_RECEIVED Sheet — every rent payment as a row for SUMIFS
    // ========================================================================

    /**
     * Creates the RENT_RECEIVED sheet with all incoming rent transactions.
     * Columns: transaction_id | transaction_date | lease_reference | property_name | amount | category | description
     * Period sheets use SUMIFS against cols C (lease_reference), B (date), E (amount).
     *
     * @return number of data rows written (for SUMIFS range references)
     */
    private int createRentReceivedSheet(Workbook workbook, List<TransactionDTO> transactions, Styles styles) {
        Sheet sheet = workbook.createSheet(RENT_SHEET_NAME);

        String[] headers = {
            "transaction_id", "transaction_date", "lease_reference", "property_name",
            "amount", "category", "description"
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

            row.createCell(2).setCellValue(str(txn.getLeaseReference()));
            row.createCell(3).setCellValue(str(txn.getPropertyName()));

            Cell amtCell = row.createCell(4);
            if (txn.getAmount() != null) {
                // Rent received should always be positive for correct SUMIFS
                amtCell.setCellValue(Math.abs(txn.getAmount().doubleValue()));
                amtCell.setCellStyle(styles.currency);
            }

            row.createCell(5).setCellValue(str(txn.getCategory()));
            row.createCell(6).setCellValue(str(txn.getDescription()));
        }

        applyWidths(sheet, headers.length);
        log.info("RENT_RECEIVED: {} rows", transactions.size());
        return transactions.size();
    }

    // ========================================================================
    // EXPENSES Sheet — every expense as a row for SUMIFS
    // ========================================================================

    /**
     * Creates the EXPENSES sheet with all expense transactions.
     * Columns: transaction_id | transaction_date | lease_reference | property_name | amount | category | description
     * Amounts stored as POSITIVE values (absolute) for clean SUMIFS.
     *
     * @return number of data rows written (for SUMIFS range references)
     */
    private int createExpensesSheet(Workbook workbook, List<TransactionDTO> transactions, Styles styles) {
        Sheet sheet = workbook.createSheet(EXPENSE_SHEET_NAME);

        String[] headers = {
            "transaction_id", "transaction_date", "lease_reference", "property_name",
            "amount", "category", "description"
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

            row.createCell(2).setCellValue(str(txn.getLeaseReference()));
            row.createCell(3).setCellValue(str(txn.getPropertyName()));

            Cell amtCell = row.createCell(4);
            if (txn.getAmount() != null) {
                // Store as-is (preserve sign) so reversals cancel out in SUMIFS
                // Most expenses are positive; disbursement reversals are negative
                amtCell.setCellValue(txn.getAmount().doubleValue());
                amtCell.setCellStyle(styles.currency);
            }

            row.createCell(5).setCellValue(str(txn.getCategory()));
            row.createCell(6).setCellValue(str(txn.getDescription()));
        }

        applyWidths(sheet, headers.length);
        log.info("EXPENSES: {} rows", transactions.size());
        return transactions.size();
    }

    // ========================================================================
    // ALLOCATIONS Sheet — every allocation as a row for SUMIFS
    // ========================================================================

    /**
     * Creates the ALLOCATIONS sheet with all unified_allocation records.
     * Columns: lease_reference | allocation_type | amount | gross_amount | commission_amount |
     *          batch_id | paid_date | beneficiary | property
     * TOTALS sheet uses SUMIFS against cols A (lease_reference), B (type), C/D/E (amounts).
     *
     * @return number of data rows written
     */
    private int createAllocationsSheet(Workbook workbook, List<Map<String, Object>> allocations, Styles styles) {
        Sheet sheet = workbook.createSheet(ALLOC_SHEET_NAME);

        String[] headers = {
            "lease_reference", "allocation_type", "amount", "gross_amount",
            "commission_amount", "batch_id", "paid_date", "beneficiary", "property"
        };

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header);
        }

        int rowNum = 1;
        for (Map<String, Object> alloc : allocations) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(str((String) alloc.get("lease_reference")));
            row.createCell(1).setCellValue(str((String) alloc.get("allocation_type")));

            Cell amtCell = row.createCell(2);
            Number amount = (Number) alloc.get("amount");
            if (amount != null) { amtCell.setCellValue(amount.doubleValue()); amtCell.setCellStyle(styles.currency); }

            Cell grossCell = row.createCell(3);
            Number gross = (Number) alloc.get("gross_amount");
            if (gross != null) { grossCell.setCellValue(gross.doubleValue()); grossCell.setCellStyle(styles.currency); }

            Cell commCell = row.createCell(4);
            Number comm = (Number) alloc.get("commission_amount");
            if (comm != null) { commCell.setCellValue(comm.doubleValue()); commCell.setCellStyle(styles.currency); }

            row.createCell(5).setCellValue(str((String) alloc.get("payment_batch_id")));

            Cell paidCell = row.createCell(6);
            Object paidDate = alloc.get("paid_date");
            if (paidDate instanceof java.sql.Date) {
                paidCell.setCellValue(((java.sql.Date) paidDate).toLocalDate());
                paidCell.setCellStyle(styles.date);
            } else if (paidDate instanceof LocalDate) {
                paidCell.setCellValue((LocalDate) paidDate);
                paidCell.setCellStyle(styles.date);
            }

            row.createCell(7).setCellValue(str((String) alloc.get("beneficiary_name")));
            row.createCell(8).setCellValue(str((String) alloc.get("property_name")));
        }

        applyWidths(sheet, headers.length);
        log.info("ALLOCATIONS: {} rows", allocations.size());
        return allocations.size();
    }

    // ========================================================================
    // OWNER_PAYMENTS Sheet — every batch payment to the owner
    // ========================================================================

    /**
     * Creates the OWNER_PAYMENTS sheet listing every batch payment made to the property owner.
     * Each row = one batch: batch_id, payment_date, gross_income, commission, expenses,
     * disbursements, net_to_owner (the actual bank transfer amount).
     */
    private void createOwnerPaymentsSheet(Workbook workbook, List<Map<String, Object>> ownerPayments, Styles styles) {
        Sheet sheet = workbook.createSheet("OWNER_PAYMENTS");

        String[] headers = {
            "batch_id", "payment_date", "amount_paid", "beneficiary", "source"
        };

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header);
        }

        int rowNum = 1;
        for (Map<String, Object> payment : ownerPayments) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(str((String) payment.get("batch_id")));

            Cell dateCell = row.createCell(1);
            Object payDate = payment.get("payment_date");
            if (payDate instanceof java.sql.Date) {
                dateCell.setCellValue(((java.sql.Date) payDate).toLocalDate());
                dateCell.setCellStyle(styles.date);
            } else if (payDate instanceof LocalDate) {
                dateCell.setCellValue((LocalDate) payDate);
                dateCell.setCellStyle(styles.date);
            }

            setCurrencyFromNumber(row.createCell(2), payment.get("total_payment"), styles.currency);
            row.createCell(3).setCellValue(str((String) payment.get("beneficiary_name")));
            row.createCell(4).setCellValue(str((String) payment.get("source")));
        }

        // TOTAL row
        int totalExcelRow = rowNum + 1;
        Row totalsRow = sheet.createRow(rowNum);
        Cell label = totalsRow.createCell(0);
        label.setCellValue("TOTAL");
        label.setCellStyle(styles.bold);

        Cell totalCell = totalsRow.createCell(2);
        totalCell.setCellFormula(String.format("SUM(C2:C%d)", totalExcelRow - 1));
        totalCell.setCellStyle(styles.boldCurrency);

        applyWidths(sheet, headers.length);
        log.info("OWNER_PAYMENTS: {} rows", ownerPayments.size());
    }

    private void setCurrencyFromNumber(Cell cell, Object value, CellStyle style) {
        if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
            cell.setCellStyle(style);
        }
    }

    // ========================================================================
    // Period Sheet — FIXED ONE ROW PER LEASE with SUMIFS formulas
    // ========================================================================

    /**
     * Creates a period sheet with one row per lease (fixed position).
     * total_rent and total_expenses use SUMIFS formulas pointing at RENT_RECEIVED/EXPENSES sheets.
     * rent_detail and expense_detail are human-readable text summaries.
     *
     * Layout:
     *   Row 1: Headers
     *   Row 2..N+1: One row per lease
     *   Row N+2: PERIOD TOTAL (SUM formulas)
     *   Then: PAYMENT RECONCILIATION section
     */
    private void createPeriodSheet(Workbook workbook, String sheetName,
                                   List<LeaseMasterDTO> leases, Period period,
                                   String prevSheetName, LocalDate overallStartDate,
                                   Styles styles, Long customerId,
                                   int rentDataRows, int expenseDataRows,
                                   List<LeaseMasterDTO> blockPropertyLeases) {

        Sheet sheet = workbook.createSheet(sheetName);

        // Header row
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < PERIOD_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(PERIOD_HEADERS[i]);
            cell.setCellStyle(styles.header);
        }

        // SUMIFS range extent — data rows from 2 to rentDataRows+1 (1-based, +1 for header)
        int rentLastRow = rentDataRows + 1;
        int expenseLastRow = expenseDataRows + 1;

        // Period dates as Excel DATE() literals for SUMIFS (not cell references — period is the same for all leases)
        String periodStartDate = String.format("DATE(%d,%d,%d)", period.start.getYear(), period.start.getMonthValue(), period.start.getDayOfMonth());
        String periodEndDate = String.format("DATE(%d,%d,%d)", period.end.getYear(), period.end.getMonthValue(), period.end.getDayOfMonth());

        // Fixed rows: one per lease
        for (int leaseIdx = 0; leaseIdx < leases.size(); leaseIdx++) {
            LeaseMasterDTO lease = leases.get(leaseIdx);
            int excelRow = leaseIdx + 2; // 1-based, +1 for header
            Row row = sheet.createRow(leaseIdx + 1);

            boolean active = isLeaseActiveInPeriod(lease, period);

            // Get batch data for text detail and batch_id/payment_date columns
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

            // Aggregate ALL rent payments and expenses for detail text
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
            String leaseRef = str(lease.getLeaseReference());

            // A-E: Lease identification
            row.createCell(COL_LEASE_ID).setCellValue(lease.getLeaseId());
            row.createCell(COL_LEASE_REF).setCellValue(leaseRef);
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
                obCell.setCellFormula(String.format("'%s'!M%d", prevSheetName, excelRow));
            } else {
                BigDecimal openingBal = dataExtractService.calculateTenantOpeningBalance(
                        lease.getLeaseId(), lease.getStartDate(), overallStartDate,
                        lease.getMonthlyRent(), lease.getFrequencyMonths(), lease.getEndDate());
                obCell.setCellValue(openingBal != null ? openingBal.doubleValue() : 0);
            }
            obCell.setCellStyle(styles.currency);

            // J: total_rent — SUMIFS formula querying RENT_RECEIVED sheet
            // Always write SUMIFS (even for inactive leases) to capture late payments
            Cell totalRentCell = row.createCell(COL_TOTAL_RENT);
            if (!leaseRef.isEmpty()) {
                String formula = String.format(
                    "SUMIFS(%s!$E$2:$E$%d,%s!$C$2:$C$%d,B%d,%s!$B$2:$B$%d,\">=\"&%s,%s!$B$2:$B$%d,\"<=\"&%s)",
                    RENT_SHEET_NAME, rentLastRow, RENT_SHEET_NAME, rentLastRow, excelRow,
                    RENT_SHEET_NAME, rentLastRow, periodStartDate,
                    RENT_SHEET_NAME, rentLastRow, periodEndDate);
                totalRentCell.setCellFormula(formula);
            } else {
                totalRentCell.setCellValue(0);
            }
            totalRentCell.setCellStyle(styles.currency);

            // K: rent_detail — human-readable text summary
            Cell rentDetailCell = row.createCell(COL_RENT_DETAIL);
            rentDetailCell.setCellValue(buildRentDetailText(allRentPayments));

            // L: period_arrears = rent_due - total_rent (formula)
            Cell arrearsCell = row.createCell(COL_PERIOD_ARREARS);
            arrearsCell.setCellFormula(String.format("H%d-J%d", excelRow, excelRow));
            arrearsCell.setCellStyle(styles.currency);

            // M: closing_balance = opening_balance + period_arrears (formula)
            Cell cbCell = row.createCell(COL_CLOSING_BAL);
            cbCell.setCellFormula(String.format("I%d+L%d", excelRow, excelRow));
            cbCell.setCellStyle(styles.currency);

            // N: commission_rate
            Cell commRateCell = row.createCell(COL_COMM_RATE);
            commRateCell.setCellValue(commissionRate);
            commRateCell.setCellStyle(styles.percent);

            // O: total_commission = total_rent * commission_rate (formula)
            Cell totalCommCell = row.createCell(COL_TOTAL_COMM);
            totalCommCell.setCellFormula(String.format("J%d*N%d", excelRow, excelRow));
            totalCommCell.setCellStyle(styles.currency);

            // P: total_expenses — SUMIFS formula querying EXPENSES sheet
            // Always write SUMIFS (even for inactive leases) to capture late expenses
            Cell totalExpCell = row.createCell(COL_TOTAL_EXPENSES);
            if (!leaseRef.isEmpty()) {
                String formula = String.format(
                    "SUMIFS(%s!$E$2:$E$%d,%s!$C$2:$C$%d,B%d,%s!$B$2:$B$%d,\">=\"&%s,%s!$B$2:$B$%d,\"<=\"&%s)",
                    EXPENSE_SHEET_NAME, expenseLastRow, EXPENSE_SHEET_NAME, expenseLastRow, excelRow,
                    EXPENSE_SHEET_NAME, expenseLastRow, periodStartDate,
                    EXPENSE_SHEET_NAME, expenseLastRow, periodEndDate);
                totalExpCell.setCellFormula(formula);
            } else {
                totalExpCell.setCellValue(0);
            }
            totalExpCell.setCellStyle(styles.currency);

            // Q: expense_detail — human-readable text summary
            Cell expenseDetailCell = row.createCell(COL_EXPENSE_DETAIL);
            expenseDetailCell.setCellValue(buildExpenseDetailText(allExpenses));

            // R: net_to_owner = total_rent - total_commission - total_expenses (formula)
            Cell netCell = row.createCell(COL_NET_TO_OWNER);
            netCell.setCellFormula(String.format("J%d-O%d-P%d", excelRow, excelRow, excelRow));
            netCell.setCellStyle(styles.currency);

            // S: batch_id(s)
            row.createCell(COL_BATCH_ID).setCellValue(String.join(", ", batchIds));

            // T: owner_payment_date
            Cell payDateCell = row.createCell(COL_OWNER_PAY_DATE);
            if (firstPaymentDate != null) { payDateCell.setCellValue(firstPaymentDate); payDateCell.setCellStyle(styles.date); }
        }

        // PERIOD TOTAL row
        int totalsRowIdx = leases.size() + 1;
        int totalsExcelRow = totalsRowIdx + 1;
        Row totalsRow = sheet.createRow(totalsRowIdx);

        Cell labelCell = totalsRow.createCell(COL_LEASE_ID);
        labelCell.setCellValue("PERIOD TOTAL");
        labelCell.setCellStyle(styles.bold);

        writeSumFormula(totalsRow, COL_RENT_DUE, 2, totalsExcelRow - 1, styles.boldCurrency);
        writeSumFormula(totalsRow, COL_OPENING_BAL, 2, totalsExcelRow - 1, styles.boldCurrency);
        writeSumFormula(totalsRow, COL_TOTAL_RENT, 2, totalsExcelRow - 1, styles.boldCurrency);
        writeSumFormula(totalsRow, COL_PERIOD_ARREARS, 2, totalsExcelRow - 1, styles.boldCurrency);
        writeSumFormula(totalsRow, COL_CLOSING_BAL, 2, totalsExcelRow - 1, styles.boldCurrency);
        writeSumFormula(totalsRow, COL_TOTAL_COMM, 2, totalsExcelRow - 1, styles.boldCurrency);
        writeSumFormula(totalsRow, COL_TOTAL_EXPENSES, 2, totalsExcelRow - 1, styles.boldCurrency);
        writeSumFormula(totalsRow, COL_NET_TO_OWNER, 2, totalsExcelRow - 1, styles.boldCurrency);

        // SERVICE CHARGE section (between period total and payment reconciliation)
        int nextRow = totalsRowIdx + 2;
        for (LeaseMasterDTO blockLease : blockPropertyLeases) {
            ServiceChargeDataDTO scData = dataExtractService.extractServiceChargeData(
                    blockLease.getLeaseId(), period.start, period.end);
            nextRow = addServiceChargeSection(sheet, nextRow, scData, styles);
        }

        // PAYMENT RECONCILIATION section
        Long resolvedOwnerId = resolveActualOwnerId(customerId);
        Set<Long> allPropertyIds = leases.stream()
                .map(LeaseMasterDTO::getPropertyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        writeReconciliationSection(sheet, nextRow, resolvedOwnerId, period, allPropertyIds, styles, totalsExcelRow);

        applyWidths(sheet, PERIOD_HEADERS.length);
    }

    // ========================================================================
    // Detail text builders
    // ========================================================================

    /**
     * Build a human-readable summary of rent payments.
     * Format: "2026-01-05: £8,500.00; 2026-02-03: £8,500.00"
     */
    private String buildRentDetailText(List<PaymentDetailDTO> rentPayments) {
        if (rentPayments.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rentPayments.size(); i++) {
            PaymentDetailDTO p = rentPayments.get(i);
            if (i > 0) sb.append("; ");
            if (p.getPaymentDate() != null) {
                sb.append(p.getPaymentDate().toString());
            }
            sb.append(": ");
            if (p.getAmount() != null) {
                sb.append(String.format("£%.2f", p.getAmount().doubleValue()));
            }
        }
        return sb.toString();
    }

    /**
     * Build a human-readable summary of expenses.
     * Format: "2026-01-15: £450.00 (Plumbing); 2026-02-20: £1,200.00 (Electrical)"
     */
    private String buildExpenseDetailText(List<PaymentDetailDTO> expenses) {
        if (expenses.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < expenses.size(); i++) {
            PaymentDetailDTO exp = expenses.get(i);
            if (i > 0) sb.append("; ");
            if (exp.getPaymentDate() != null) {
                sb.append(exp.getPaymentDate().toString());
            }
            sb.append(": ");
            if (exp.getAmount() != null) {
                sb.append(String.format("£%.2f", Math.abs(exp.getAmount().doubleValue())));
            }
            if (exp.getCategory() != null && !exp.getCategory().isEmpty()) {
                sb.append(" (").append(exp.getCategory()).append(")");
            }
        }
        return sb.toString();
    }

    // ========================================================================
    // TOTALS Sheet
    // ========================================================================

    private void createTotalsSheet(Workbook workbook, List<LeaseMasterDTO> leases,
                                   List<String> periodSheetNames, LocalDate overallStartDate,
                                   Styles styles, int allocDataRows,
                                   List<LeaseMasterDTO> blockPropertyLeases, LocalDate overallEndDate) {

        Sheet sheet = workbook.createSheet("TOTALS");

        // Headers: formula-based columns | spacer | allocation-based columns | reconciliation
        String[] headers = {
            "lease_id", "lease_reference", "property_name", "tenant_name",
            // Formula-based (E-K)
            "total_rent_due", "total_rent_received", "total_arrears",
            "commission_rate", "total_commission", "total_expenses",
            "total_net_to_owner",
            // Balance (L-M)
            "first_opening_balance", "last_closing_balance",
            // Spacer (N)
            "",
            // Allocation-based (O-S) — SUMIFS from ALLOCATIONS sheet
            "alloc_gross_income", "alloc_commission", "alloc_expenses",
            "alloc_disbursements", "alloc_net_to_owner",
            // Reconciliation (T-U)
            "rent_vs_alloc_diff", "net_diff"
        };

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header);
        }

        int allocLastRow = allocDataRows + 1; // 1-based, +1 for header

        // Column letters in period sheet layout:
        // H = rent_due, J = total_rent, L = period_arrears,
        // I = opening_balance, M = closing_balance,
        // O = total_commission, P = total_expenses, R = net_to_owner

        for (int leaseIdx = 0; leaseIdx < leases.size(); leaseIdx++) {
            LeaseMasterDTO lease = leases.get(leaseIdx);
            int periodExcelRow = leaseIdx + 2;
            int r = leaseIdx + 2; // this row in Excel (1-based)
            Row row = sheet.createRow(leaseIdx + 1);

            int col = 0;
            // A-D: Identification
            row.createCell(col++).setCellValue(lease.getLeaseId());       // A
            row.createCell(col++).setCellValue(str(lease.getLeaseReference())); // B
            row.createCell(col++).setCellValue(str(lease.getPropertyName()));   // C
            row.createCell(col++).setCellValue(str(lease.getTenantName()));     // D

            // E: total_rent_due
            Cell trdCell = row.createCell(col++);
            trdCell.setCellFormula(buildCrossSheetSum(periodSheetNames, "H", periodExcelRow));
            trdCell.setCellStyle(styles.currency);

            // F: total_rent_received
            Cell trrCell = row.createCell(col++);
            trrCell.setCellFormula(buildCrossSheetSum(periodSheetNames, "J", periodExcelRow));
            trrCell.setCellStyle(styles.currency);

            // G: total_arrears = E - F
            Cell taCell = row.createCell(col++);
            taCell.setCellFormula(String.format("E%d-F%d", r, r));
            taCell.setCellStyle(styles.currency);

            // H: commission_rate
            Cell crCell = row.createCell(col++);
            crCell.setCellValue(getCommissionRate(lease));
            crCell.setCellStyle(styles.percent);

            // I: total_commission
            Cell tcCell = row.createCell(col++);
            tcCell.setCellFormula(buildCrossSheetSum(periodSheetNames, "O", periodExcelRow));
            tcCell.setCellStyle(styles.currency);

            // J: total_expenses
            Cell teCell = row.createCell(col++);
            teCell.setCellFormula(buildCrossSheetSum(periodSheetNames, "P", periodExcelRow));
            teCell.setCellStyle(styles.currency);

            // K: total_net_to_owner = F - I - J
            Cell tnCell = row.createCell(col++);
            tnCell.setCellFormula(String.format("F%d-I%d-J%d", r, r, r));
            tnCell.setCellStyle(styles.currency);

            // L: first_opening_balance
            Cell obCell = row.createCell(col++);
            if (!periodSheetNames.isEmpty()) {
                obCell.setCellFormula(String.format("'%s'!I%d", periodSheetNames.get(0), periodExcelRow));
            } else {
                obCell.setCellValue(0);
            }
            obCell.setCellStyle(styles.currency);

            // M: last_closing_balance
            Cell cbCell = row.createCell(col++);
            if (!periodSheetNames.isEmpty()) {
                String lastSheet = periodSheetNames.get(periodSheetNames.size() - 1);
                cbCell.setCellFormula(String.format("'%s'!M%d", lastSheet, periodExcelRow));
            } else {
                cbCell.setCellValue(0);
            }
            cbCell.setCellStyle(styles.currency);

            // N: spacer
            row.createCell(col++);

            // O: alloc_gross_income — SUMIFS(ALLOCATIONS gross_amount where lease_ref matches AND type=OWNER)
            Cell agCell = row.createCell(col++);
            agCell.setCellFormula(String.format(
                "SUMIFS(%s!$D$2:$D$%d,%s!$A$2:$A$%d,B%d,%s!$B$2:$B$%d,\"OWNER\")",
                ALLOC_SHEET_NAME, allocLastRow, ALLOC_SHEET_NAME, allocLastRow, r,
                ALLOC_SHEET_NAME, allocLastRow));
            agCell.setCellStyle(styles.currency);

            // P: alloc_commission — SUMIFS(commission_amount where OWNER)
            // Note: COMMISSION allocation rows duplicate the OWNER.commission_amount for PayProp batches,
            // so we only use OWNER.commission_amount to avoid double-counting.
            // Historical batches only have OWNER.commission_amount (no separate COMMISSION rows).
            Cell acCell = row.createCell(col++);
            acCell.setCellFormula(String.format(
                "SUMIFS(%s!$E$2:$E$%d,%s!$A$2:$A$%d,B%d,%s!$B$2:$B$%d,\"OWNER\")",
                ALLOC_SHEET_NAME, allocLastRow, ALLOC_SHEET_NAME, allocLastRow, r,
                ALLOC_SHEET_NAME, allocLastRow));
            acCell.setCellStyle(styles.currency);

            // Q: alloc_expenses — SUMIFS(amount where EXPENSE)
            Cell aeCell = row.createCell(col++);
            aeCell.setCellFormula(String.format(
                "SUMIFS(%s!$C$2:$C$%d,%s!$A$2:$A$%d,B%d,%s!$B$2:$B$%d,\"EXPENSE\")",
                ALLOC_SHEET_NAME, allocLastRow, ALLOC_SHEET_NAME, allocLastRow, r,
                ALLOC_SHEET_NAME, allocLastRow));
            aeCell.setCellStyle(styles.currency);

            // R: alloc_disbursements — SUMIFS(amount where DISBURSEMENT)
            Cell adCell = row.createCell(col++);
            adCell.setCellFormula(String.format(
                "SUMIFS(%s!$C$2:$C$%d,%s!$A$2:$A$%d,B%d,%s!$B$2:$B$%d,\"DISBURSEMENT\")",
                ALLOC_SHEET_NAME, allocLastRow, ALLOC_SHEET_NAME, allocLastRow, r,
                ALLOC_SHEET_NAME, allocLastRow));
            adCell.setCellStyle(styles.currency);

            // S: alloc_net_to_owner = alloc_gross - alloc_commission - alloc_expenses - alloc_disbursements
            Cell anCell = row.createCell(col++);
            anCell.setCellFormula(String.format("O%d-P%d-Q%d-R%d", r, r, r, r));
            anCell.setCellStyle(styles.currency);

            // T: rent_vs_alloc_diff = total_rent_received - alloc_gross_income
            Cell rdCell = row.createCell(col++);
            rdCell.setCellFormula(String.format("F%d-O%d", r, r));
            rdCell.setCellStyle(styles.currency);

            // U: net_diff = total_net_to_owner - alloc_net_to_owner
            Cell ndCell = row.createCell(col++);
            ndCell.setCellFormula(String.format("K%d-S%d", r, r));
            ndCell.setCellStyle(styles.currency);
        }

        // GRAND TOTAL row
        int totalsRowNum = leases.size() + 1;
        int totalsExcelRow = totalsRowNum + 1;
        Row totalsRow = sheet.createRow(totalsRowNum);

        Cell grandLabel = totalsRow.createCell(0);
        grandLabel.setCellValue("GRAND TOTAL");
        grandLabel.setCellStyle(styles.bold);

        // SUM all numeric columns (skip D=commission_rate, N=spacer)
        int[] sumCols = {4, 5, 6, 8, 9, 10, 11, 12, 14, 15, 16, 17, 18, 19, 20};
        for (int c : sumCols) {
            Cell cell = totalsRow.createCell(c);
            String colLetter = colLetter(c);
            cell.setCellFormula(String.format("SUM(%s2:%s%d)", colLetter, colLetter, totalsExcelRow - 1));
            cell.setCellStyle(styles.boldCurrency);
        }

        // SERVICE CHARGE ACCOUNTS summary section
        if (!blockPropertyLeases.isEmpty()) {
            int scRowNum = totalsRowNum + 3;

            Row scSeparator = sheet.createRow(scRowNum++);
            Cell scSepCell = scSeparator.createCell(0);
            scSepCell.setCellValue("═══════════════════════════════════════════════════════════════════════════════");
            scSepCell.setCellStyle(styles.bold);

            Row scHeader = sheet.createRow(scRowNum++);
            Cell scHeaderCell = scHeader.createCell(0);
            scHeaderCell.setCellValue("SERVICE CHARGE ACCOUNTS");
            scHeaderCell.setCellStyle(styles.bold);

            scRowNum++; // blank

            Row scColHeaders = sheet.createRow(scRowNum++);
            String[] scHeaders = {"Block Property Name", "Tenant", "Opening Balance",
                                  "Total Income", "Total Expenses", "Closing Balance"};
            for (int i = 0; i < scHeaders.length; i++) {
                Cell cell = scColHeaders.createCell(i);
                cell.setCellValue(scHeaders[i]);
                cell.setCellStyle(styles.header);
            }

            for (LeaseMasterDTO blockLease : blockPropertyLeases) {
                ServiceChargeDataDTO scData = dataExtractService.extractServiceChargeData(
                        blockLease.getLeaseId(), overallStartDate, overallEndDate);

                Row scRow = sheet.createRow(scRowNum++);
                scRow.createCell(0).setCellValue(str(blockLease.getPropertyName()));
                scRow.createCell(1).setCellValue(str(blockLease.getTenantName()));

                Cell obCell = scRow.createCell(2);
                obCell.setCellValue(scData.getOpeningBalance().doubleValue());
                obCell.setCellStyle(styles.currency);

                Cell incCell = scRow.createCell(3);
                incCell.setCellValue(scData.getTotalIncome().doubleValue());
                incCell.setCellStyle(styles.currency);

                Cell expCell = scRow.createCell(4);
                expCell.setCellValue(scData.getTotalExpenses().doubleValue());
                expCell.setCellStyle(styles.currency);

                Cell cbCell = scRow.createCell(5);
                cbCell.setCellValue(scData.getClosingBalance().doubleValue());
                cbCell.setCellStyle(styles.boldCurrency);
            }

            log.info("TOTALS: Added {} service charge account rows", blockPropertyLeases.size());
        }

        applyWidths(sheet, headers.length);
        log.info("TOTALS: {} leases, {} period sheets, {} allocation rows",
                leases.size(), periodSheetNames.size(), allocDataRows);
    }

    // ========================================================================
    // Payment Reconciliation section (embedded in each period sheet)
    // ========================================================================

    @SuppressWarnings("unchecked")
    private int writeReconciliationSection(Sheet sheet, int startRow, Long resolvedOwnerId,
                                            Period period, Set<Long> propertyIds, Styles styles,
                                            int periodTotalExcelRow) {
        int rowNum = startRow;

        Row sectionHeader = sheet.createRow(rowNum++);
        sectionHeader.createCell(0).setCellValue("PAYMENT RECONCILIATION");
        sectionHeader.getCell(0).setCellStyle(styles.bold);

        Row periodRow = sheet.createRow(rowNum++);
        periodRow.createCell(0).setCellValue("Period: " + period.start + " to " + period.end);

        rowNum++; // blank

        // PERIOD SUMMARY — references the PERIOD TOTAL row above for at-a-glance view
        // Uses column letters from the period table: J=total_rent, O=total_commission, P=total_expenses, R=net_to_owner
        Row psSectionRow = sheet.createRow(rowNum++);
        psSectionRow.createCell(0).setCellValue("PERIOD SUMMARY");
        psSectionRow.getCell(0).setCellStyle(styles.bold);

        Row psHeaderRow = sheet.createRow(rowNum++);
        psHeaderRow.createCell(1).setCellValue("Rent Received");  psHeaderRow.getCell(1).setCellStyle(styles.bold);
        psHeaderRow.createCell(2).setCellValue("Commission");     psHeaderRow.getCell(2).setCellStyle(styles.bold);
        psHeaderRow.createCell(3).setCellValue("Expenses");       psHeaderRow.getCell(3).setCellStyle(styles.bold);
        psHeaderRow.createCell(4).setCellValue("Net to Owner");   psHeaderRow.getCell(4).setCellStyle(styles.bold);

        Row psValRow = sheet.createRow(rowNum++);
        psValRow.createCell(0).setCellValue("Period Table:");
        // Reference PERIOD TOTAL row: J=col9=total_rent, O=col14=total_comm, P=col15=total_expenses, R=col17=net_to_owner
        Cell psRent = psValRow.createCell(1);
        psRent.setCellFormula(String.format("%s%d", colLetter(COL_TOTAL_RENT), periodTotalExcelRow));
        psRent.setCellStyle(styles.currency);
        Cell psComm = psValRow.createCell(2);
        psComm.setCellFormula(String.format("%s%d", colLetter(COL_TOTAL_COMM), periodTotalExcelRow));
        psComm.setCellStyle(styles.currency);
        Cell psExp = psValRow.createCell(3);
        psExp.setCellFormula(String.format("%s%d", colLetter(COL_TOTAL_EXPENSES), periodTotalExcelRow));
        psExp.setCellStyle(styles.currency);
        Cell psNet = psValRow.createCell(4);
        psNet.setCellFormula(String.format("%s%d", colLetter(COL_NET_TO_OWNER), periodTotalExcelRow));
        psNet.setCellStyle(styles.boldCurrency);

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

        // ALLOCATION STATUS SUMMARY
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

        // OWNER PAYMENTS THIS PERIOD
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

        // Classify allocations by period for each batch
        List<ProcessedBatch> processedBatches = new ArrayList<>();
        for (BatchAllocationStatusDTO batch : batchStatuses) {
            ProcessedBatch pb = new ProcessedBatch(batch);
            for (BatchAllocationStatusDTO.AllocationDetailDTO alloc : batch.getAllocations()) {
                String allocType = alloc.getAllocationType();
                if ("COMMISSION".equals(allocType)) continue;

                String classification = alloc.getPeriodClassification();
                if ("B/F".equals(classification)) {
                    pb.priorAllocations.add(alloc);
                } else if ("FUTURE".equals(classification)) {
                    pb.futureAllocations.add(alloc);
                } else {
                    pb.currentAllocations.add(alloc);
                }
            }
            processedBatches.add(pb);
        }

        // Reserve TOTALS row — we'll fill in SUM formulas after writing batch rows
        int totalsRowNum = rowNum++;
        rowNum++; // blank after totals

        // Track batch header row numbers for TOTALS SUM formulas
        List<Integer> batchHeaderExcelRows = new ArrayList<>();

        // BATCH DETAIL ROWS — each batch: header, Prior/This/Future sub-rows, then detail rows
        for (ProcessedBatch pb : processedBatches) {
            BatchAllocationStatusDTO batch = pb.batch;

            int batchHeaderRowNum = rowNum;
            int batchHeaderExcelRow = batchHeaderRowNum + 1; // 1-based
            batchHeaderExcelRows.add(batchHeaderExcelRow);

            Row batchRow = sheet.createRow(rowNum++);
            batchRow.createCell(0).setCellValue(str(batch.getBatchId()));
            Cell payDateCell = batchRow.createCell(1);
            if (batch.getPaymentDate() != null) {
                payDateCell.setCellValue(batch.getPaymentDate());
                payDateCell.setCellStyle(styles.date);
            } else {
                payDateCell.setCellValue("Pending");
            }
            // Batch header C-F: formulas summing the 3 sub-rows below
            int priorSubExcel = batchHeaderExcelRow + 1;
            int currSubExcel = batchHeaderExcelRow + 2;
            int futSubExcel = batchHeaderExcelRow + 3;
            for (int c = 2; c <= 5; c++) {
                String cl = colLetter(c);
                Cell cell = batchRow.createCell(c);
                cell.setCellFormula(String.format("%s%d+%s%d+%s%d", cl, priorSubExcel, cl, currSubExcel, cl, futSubExcel));
                cell.setCellStyle(c == 2 || c == 5 ? styles.boldCurrency : styles.currency);
            }

            // Prior sub-row: SUM over prior detail rows (cols I/J/K/L = 8/9/10/11)
            int detailStartExcel = batchHeaderExcelRow + 4; // detail rows start after 3 sub-rows + header
            int detailEndExcel = detailStartExcel + Math.max(pb.priorAllocations.size(), Math.max(pb.currentAllocations.size(), pb.futureAllocations.size())) - 1;
            if (detailEndExcel < detailStartExcel) detailEndExcel = detailStartExcel;

            Row priorSumRow = sheet.createRow(rowNum++);
            priorSumRow.createCell(1).setCellValue("Prior:");
            // Prior sub-row C-F: SUM of prior detail columns (I/J/K/L = cols 8,9,10,11)
            for (int c = 2; c <= 5; c++) {
                Cell cell = priorSumRow.createCell(c);
                String detailCol = colLetter(c + 6); // C->I, D->J, E->K, F->L
                cell.setCellFormula(String.format("SUM(%s%d:%s%d)", detailCol, detailStartExcel, detailCol, detailEndExcel));
                cell.setCellStyle(styles.currency);
            }

            Row currSumRow = sheet.createRow(rowNum++);
            currSumRow.createCell(1).setCellValue("This Period:");
            // This Period sub-row C-F: SUM of current detail columns (O/P/Q/R = cols 14,15,16,17)
            for (int c = 2; c <= 5; c++) {
                Cell cell = currSumRow.createCell(c);
                String detailCol = colLetter(c + 12); // C->O, D->P, E->Q, F->R
                cell.setCellFormula(String.format("SUM(%s%d:%s%d)", detailCol, detailStartExcel, detailCol, detailEndExcel));
                cell.setCellStyle(styles.currency);
            }

            Row futSumRow = sheet.createRow(rowNum++);
            futSumRow.createCell(1).setCellValue("Future:");
            // Future sub-row C-F: SUM of future detail columns (U/V/W/X = cols 20,21,22,23)
            for (int c = 2; c <= 5; c++) {
                Cell cell = futSumRow.createCell(c);
                String detailCol = colLetter(c + 18); // C->U, D->V, E->W, F->X
                cell.setCellFormula(String.format("SUM(%s%d:%s%d)", detailCol, detailStartExcel, detailCol, detailEndExcel));
                cell.setCellStyle(styles.currency);
            }

            // Allocation detail rows
            int maxRows = Math.max(1, Math.max(pb.priorAllocations.size(),
                    Math.max(pb.currentAllocations.size(), pb.futureAllocations.size())));

            for (int rowIdx = 0; rowIdx < maxRows; rowIdx++) {
                Row detailRow = sheet.createRow(rowNum++);
                if (rowIdx < pb.priorAllocations.size()) {
                    writeAllocationDetail(detailRow, 6, pb.priorAllocations.get(rowIdx), styles);
                }
                if (rowIdx < pb.currentAllocations.size()) {
                    writeAllocationDetail(detailRow, 12, pb.currentAllocations.get(rowIdx), styles);
                }
                if (rowIdx < pb.futureAllocations.size()) {
                    writeAllocationDetail(detailRow, 18, pb.futureAllocations.get(rowIdx), styles);
                }
            }

            rowNum++; // blank between batches
        }

        // NOW fill in the TOTALS row with SUM formulas over batch header rows
        Row totalsRow = sheet.getRow(totalsRowNum);
        if (totalsRow == null) totalsRow = sheet.createRow(totalsRowNum);
        Cell totalsLabel = totalsRow.createCell(0);
        totalsLabel.setCellValue("TOTALS");
        totalsLabel.setCellStyle(styles.bold);

        if (!batchHeaderExcelRows.isEmpty()) {
            // Build SUM formulas that add up specific batch header rows (not a range, since rows aren't contiguous)
            for (int c = 2; c <= 5; c++) {
                String cl = colLetter(c);
                StringBuilder formula = new StringBuilder();
                for (int i = 0; i < batchHeaderExcelRows.size(); i++) {
                    if (i > 0) formula.append("+");
                    formula.append(cl).append(batchHeaderExcelRows.get(i));
                }
                Cell cell = totalsRow.createCell(c);
                cell.setCellFormula(formula.toString());
                cell.setCellStyle(styles.boldCurrency);
            }

            // Prior totals (cols 8-11): sum prior sub-rows
            for (int c = 8; c <= 11; c++) {
                String cl = colLetter(c);
                StringBuilder formula = new StringBuilder();
                for (int i = 0; i < batchHeaderExcelRows.size(); i++) {
                    if (i > 0) formula.append("+");
                    formula.append(colLetter(c - 6)).append(batchHeaderExcelRows.get(i) + 1); // prior sub-row is +1
                }
                Cell cell = totalsRow.createCell(c);
                cell.setCellFormula(formula.toString());
                cell.setCellStyle(styles.boldCurrency);
            }

            // This Period totals (cols 14-17): sum current sub-rows
            for (int c = 14; c <= 17; c++) {
                String cl = colLetter(c);
                StringBuilder formula = new StringBuilder();
                for (int i = 0; i < batchHeaderExcelRows.size(); i++) {
                    if (i > 0) formula.append("+");
                    formula.append(colLetter(c - 12)).append(batchHeaderExcelRows.get(i) + 2); // current sub-row is +2
                }
                Cell cell = totalsRow.createCell(c);
                cell.setCellFormula(formula.toString());
                cell.setCellStyle(styles.boldCurrency);
            }

            // Future totals (cols 20-23): sum future sub-rows
            for (int c = 20; c <= 23; c++) {
                String cl = colLetter(c);
                StringBuilder formula = new StringBuilder();
                for (int i = 0; i < batchHeaderExcelRows.size(); i++) {
                    if (i > 0) formula.append("+");
                    formula.append(colLetter(c - 18)).append(batchHeaderExcelRows.get(i) + 3); // future sub-row is +3
                }
                Cell cell = totalsRow.createCell(c);
                cell.setCellFormula(formula.toString());
                cell.setCellStyle(styles.boldCurrency);
            }
        }

        // RECONCILIATION COMPARISON — add "Allocated" and "Difference" rows under the PERIOD SUMMARY
        // These reference the OWNER PAYMENTS TOTALS "This Period" columns (O/P/Q/R = 14/15/16/17)
        int totalsExcelRowRef = totalsRowNum + 1; // 1-based
        Row allocatedRow = sheet.createRow(rowNum++);
        allocatedRow.createCell(0).setCellValue("Allocated (Paid):");
        // This Period income from TOTALS = col O (14)
        Cell arRent = allocatedRow.createCell(1);
        arRent.setCellFormula(String.format("%s%d", colLetter(14), totalsExcelRowRef));
        arRent.setCellStyle(styles.currency);
        Cell arComm = allocatedRow.createCell(2);
        arComm.setCellFormula(String.format("%s%d", colLetter(15), totalsExcelRowRef));
        arComm.setCellStyle(styles.currency);
        Cell arExp = allocatedRow.createCell(3);
        arExp.setCellFormula(String.format("%s%d", colLetter(16), totalsExcelRowRef));
        arExp.setCellStyle(styles.currency);
        Cell arNet = allocatedRow.createCell(4);
        arNet.setCellFormula(String.format("%s%d", colLetter(17), totalsExcelRowRef));
        arNet.setCellStyle(styles.boldCurrency);

        // Difference row: Period Table - Allocated
        int allocatedExcelRow = rowNum; // current row (0-based) + 1 = rowNum
        Row diffRow = sheet.createRow(rowNum++);
        diffRow.createCell(0).setCellValue("Difference:");
        diffRow.getCell(0).setCellStyle(styles.bold);
        for (int c = 1; c <= 4; c++) {
            String cl = colLetter(c);
            // Find the Period Table row — it was written as psValRow earlier
            // psValRow is at a variable position. We need to find it.
            // Actually, let's reference the PERIOD TOTAL row directly and the allocated row
            Cell diffCell = diffRow.createCell(c);
            // Period Table value is the row above "Allocated" minus 2 (psValRow)
            // This is fragile. Better: reference the period table totals row directly
            String periodRef;
            if (c == 1) periodRef = colLetter(COL_TOTAL_RENT);
            else if (c == 2) periodRef = colLetter(COL_TOTAL_COMM);
            else if (c == 3) periodRef = colLetter(COL_TOTAL_EXPENSES);
            else periodRef = colLetter(COL_NET_TO_OWNER);
            diffCell.setCellFormula(String.format("%s%d-%s%d", periodRef, periodTotalExcelRow, cl, allocatedExcelRow));
            diffCell.setCellStyle(styles.boldCurrency);
        }

        rowNum++; // blank

        return rowNum;
    }

    private void writeAllocationDetail(Row row, int startCol, BatchAllocationStatusDTO.AllocationDetailDTO alloc, Styles styles) {
        row.createCell(startCol).setCellValue(str(alloc.getPropertyName()));

        Cell txnDateCell = row.createCell(startCol + 1);
        if (alloc.getTransactionDate() != null) {
            txnDateCell.setCellValue(alloc.getTransactionDate());
            txnDateCell.setCellStyle(styles.date);
        }

        String type = alloc.getAllocationType();
        int excelRow = row.getRowNum() + 1; // 1-based
        String incCol = colLetter(startCol + 2);
        String expCol = colLetter(startCol + 3);
        String commCol = colLetter(startCol + 4);
        String netCol = colLetter(startCol + 5);

        if ("OWNER".equals(type) && alloc.getAmount() != null) {
            double income = alloc.getGrossAmount() != null ? alloc.getGrossAmount().abs().doubleValue() : alloc.getAmount().abs().doubleValue();
            Cell incCell = row.createCell(startCol + 2);
            incCell.setCellValue(income);
            incCell.setCellStyle(styles.currency);
            if (alloc.getCommissionAmount() != null) {
                Cell commCell = row.createCell(startCol + 4);
                commCell.setCellValue(alloc.getCommissionAmount().abs().doubleValue());
                commCell.setCellStyle(styles.currency);
            }
        } else if (("EXPENSE".equals(type) || "DISBURSEMENT".equals(type)) && alloc.getAmount() != null) {
            Cell expCell = row.createCell(startCol + 3);
            expCell.setCellValue(alloc.getAmount().doubleValue());
            expCell.setCellStyle(styles.currency);
        }

        // Net = Income - Expense - Commission (formula)
        Cell netCell = row.createCell(startCol + 5);
        netCell.setCellFormula(String.format("%s%d-%s%d-%s%d", incCol, excelRow, expCol, excelRow, commCol, excelRow));
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
                // Stop if cycle starts after lease has ended
                if (leaseEnd != null && cycleStart.isAfter(leaseEnd)) break;
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
            if (i == COL_RENT_DETAIL || i == COL_EXPENSE_DETAIL) {
                sheet.setColumnWidth(i, 50 * 256); // wider for detail text
            } else if (i <= 4) {
                sheet.setColumnWidth(i, 25 * 256);
            } else {
                sheet.setColumnWidth(i, 15 * 256);
            }
        }
    }

    // ========================================================================
    // Service Charge section (embedded in each period sheet + TOTALS)
    // ========================================================================

    private int addServiceChargeSection(Sheet sheet, int startRow, ServiceChargeDataDTO data, Styles styles) {
        int rowNum = startRow;

        if (data.getBlockPropertyName() == null) return rowNum;

        // Section separator
        Row separatorRow = sheet.createRow(rowNum++);
        Cell separatorCell = separatorRow.createCell(0);
        separatorCell.setCellValue("═══════════════════════════════════════════════════════════════════════════════");
        separatorCell.setCellStyle(styles.bold);

        // Section header
        Row headerRow = sheet.createRow(rowNum++);
        Cell headerCell = headerRow.createCell(0);
        headerCell.setCellValue("SERVICE CHARGE ACCOUNT - " + data.getBlockPropertyName());
        headerCell.setCellStyle(styles.bold);

        if (data.getTenantName() != null) {
            Row tenantRow = sheet.createRow(rowNum++);
            tenantRow.createCell(0).setCellValue("Service Charge Payer: " + data.getTenantName());
        }

        rowNum++; // blank

        // Summary headers
        Row summaryHeaderRow = sheet.createRow(rowNum++);
        summaryHeaderRow.createCell(0).setCellValue("");
        Cell obH = summaryHeaderRow.createCell(1); obH.setCellValue("Opening Balance"); obH.setCellStyle(styles.bold);
        Cell incH = summaryHeaderRow.createCell(2); incH.setCellValue("Income"); incH.setCellStyle(styles.bold);
        Cell expH = summaryHeaderRow.createCell(3); expH.setCellValue("Expenses"); expH.setCellStyle(styles.bold);
        Cell cbH = summaryHeaderRow.createCell(4); cbH.setCellValue("Closing Balance"); cbH.setCellStyle(styles.bold);

        // Summary values
        Row summaryRow = sheet.createRow(rowNum++);
        Cell sumLabel = summaryRow.createCell(0); sumLabel.setCellValue("SUMMARY"); sumLabel.setCellStyle(styles.bold);
        Cell obVal = summaryRow.createCell(1); obVal.setCellValue(data.getOpeningBalance().doubleValue()); obVal.setCellStyle(styles.currency);
        Cell incVal = summaryRow.createCell(2); incVal.setCellValue(data.getTotalIncome().doubleValue()); incVal.setCellStyle(styles.currency);
        Cell expVal = summaryRow.createCell(3); expVal.setCellValue(data.getTotalExpenses().doubleValue()); expVal.setCellStyle(styles.currency);
        Cell cbVal = summaryRow.createCell(4); cbVal.setCellValue(data.getClosingBalance().doubleValue()); cbVal.setCellStyle(styles.boldCurrency);

        rowNum++; // blank

        // Income details
        if (!data.getIncomeTransactions().isEmpty()) {
            Row incHeader = sheet.createRow(rowNum++);
            Cell incHdr = incHeader.createCell(0); incHdr.setCellValue("INCOME (Service Charge Payments)"); incHdr.setCellStyle(styles.bold);

            Row incColHdr = sheet.createRow(rowNum++);
            incColHdr.createCell(0).setCellValue("Date");
            incColHdr.createCell(1).setCellValue("Description");
            incColHdr.createCell(2).setCellValue("Amount");
            incColHdr.createCell(3).setCellValue("Running Balance");

            BigDecimal runBal = data.getOpeningBalance();
            for (ServiceChargeDataDTO.ServiceChargeTransactionDTO txn : data.getIncomeTransactions()) {
                Row txnRow = sheet.createRow(rowNum++);
                Cell dateCell = txnRow.createCell(0);
                if (txn.getTransactionDate() != null) { dateCell.setCellValue(txn.getTransactionDate()); dateCell.setCellStyle(styles.date); }
                txnRow.createCell(1).setCellValue(txn.getDescription() != null ? txn.getDescription() : "");
                BigDecimal amt = txn.getAmount() != null ? txn.getAmount().abs() : BigDecimal.ZERO;
                Cell amtCell = txnRow.createCell(2); amtCell.setCellValue(amt.doubleValue()); amtCell.setCellStyle(styles.currency);
                runBal = runBal.add(amt);
                Cell runCell = txnRow.createCell(3); runCell.setCellValue(runBal.doubleValue()); runCell.setCellStyle(styles.currency);
            }
            rowNum++; // blank
        }

        // Expense details
        if (!data.getExpenseTransactions().isEmpty()) {
            Row expHeader = sheet.createRow(rowNum++);
            Cell expHdr = expHeader.createCell(0); expHdr.setCellValue("EXPENSES (Block Account)"); expHdr.setCellStyle(styles.bold);

            Row expColHdr = sheet.createRow(rowNum++);
            expColHdr.createCell(0).setCellValue("Date");
            expColHdr.createCell(1).setCellValue("Description");
            expColHdr.createCell(2).setCellValue("Category");
            expColHdr.createCell(3).setCellValue("Amount");
            expColHdr.createCell(4).setCellValue("Running Balance");

            BigDecimal runBal = data.getOpeningBalance().add(data.getTotalIncome());
            for (ServiceChargeDataDTO.ServiceChargeTransactionDTO txn : data.getExpenseTransactions()) {
                Row txnRow = sheet.createRow(rowNum++);
                Cell dateCell = txnRow.createCell(0);
                if (txn.getTransactionDate() != null) { dateCell.setCellValue(txn.getTransactionDate()); dateCell.setCellStyle(styles.date); }
                txnRow.createCell(1).setCellValue(txn.getDescription() != null ? txn.getDescription() : "");
                txnRow.createCell(2).setCellValue(txn.getCategory() != null ? txn.getCategory() : "");
                BigDecimal amt = txn.getAmount() != null ? txn.getAmount().abs() : BigDecimal.ZERO;
                Cell amtCell = txnRow.createCell(3); amtCell.setCellValue(-amt.doubleValue()); amtCell.setCellStyle(styles.currency);
                runBal = runBal.subtract(amt);
                Cell runCell = txnRow.createCell(4); runCell.setCellValue(runBal.doubleValue()); runCell.setCellStyle(styles.currency);
            }
        }

        rowNum++; // blank at end
        return rowNum;
    }

    private static class Styles {
        final CellStyle header;
        final CellStyle date;
        final CellStyle currency;
        final CellStyle percent;
        final CellStyle bold;
        final CellStyle boldCurrency;
        final CellStyle wrapText;

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

            this.wrapText = workbook.createCellStyle();
            wrapText.setWrapText(true);
        }
    }
}

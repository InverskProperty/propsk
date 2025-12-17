package site.easy.to.build.crm.service.statement;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.dto.statement.LeaseAllocationSummaryDTO;
import site.easy.to.build.crm.dto.statement.LeaseMasterDTO;
import site.easy.to.build.crm.dto.statement.PaymentBatchSummaryDTO;
import site.easy.to.build.crm.dto.statement.PaymentWithAllocationsDTO;
import site.easy.to.build.crm.dto.statement.RelatedPaymentDTO;
import site.easy.to.build.crm.dto.statement.TransactionDTO;
import site.easy.to.build.crm.dto.statement.UnallocatedIncomeDTO;
import site.easy.to.build.crm.dto.statement.UnallocatedPaymentDTO;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.PaymentBatch;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.repository.PaymentBatchRepository;
import site.easy.to.build.crm.repository.TransactionBatchAllocationRepository;
import site.easy.to.build.crm.repository.UnifiedAllocationRepository;
import site.easy.to.build.crm.entity.UnifiedAllocation;
import site.easy.to.build.crm.entity.UnifiedTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for generating Excel statements with formulas (Option C)
 *
 * KEY PRINCIPLE: Write FORMULAS to cells, not calculated values
 * - All calculations done in Excel
 * - Users can see, audit, and modify formulas
 * - Transparent and flexible
 */
@Service
public class ExcelStatementGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(ExcelStatementGeneratorService.class);

    @Autowired
    private StatementDataExtractService dataExtractService;

    @Autowired
    private site.easy.to.build.crm.config.CommissionConfig commissionConfig;

    @Autowired
    private TransactionBatchAllocationRepository allocationRepository;

    @Autowired
    private PaymentBatchRepository paymentBatchRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UnifiedAllocationRepository unifiedAllocationRepository;

    @Autowired
    private site.easy.to.build.crm.repository.UnifiedTransactionRepository unifiedTransactionRepository;

    /**
     * Log current memory usage for debugging statement generation issues
     * Search keyword: [STMT-DEBUG] for easy log filtering
     */
    private void logMemoryUsage(String phase) {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        int usedPercent = (int) ((usedMemory * 100) / maxMemory);

        String status = usedPercent > 90 ? "🔴 CRITICAL" : usedPercent > 70 ? "🟡 WARNING" : "🟢 OK";

        log.info("[STMT-DEBUG] {} [{}] Memory: {}MB/{}MB ({}%) - Used={}MB, Free={}MB",
            status, phase,
            usedMemory / (1024 * 1024),
            maxMemory / (1024 * 1024),
            usedPercent,
            usedMemory / (1024 * 1024),
            freeMemory / (1024 * 1024));
    }

    /**
     * Request garbage collection and log memory before/after.
     * Called between heavy operations to free memory.
     */
    private void requestGC(String phase) {
        Runtime runtime = Runtime.getRuntime();
        long usedBefore = runtime.totalMemory() - runtime.freeMemory();

        // Request GC (hint to JVM, not guaranteed)
        System.gc();

        // Small pause to allow GC to run
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}

        long usedAfter = runtime.totalMemory() - runtime.freeMemory();
        long freed = usedBefore - usedAfter;

        if (freed > 0) {
            log.info("[STMT-DEBUG] 🗑️ GC after {} freed {}MB", phase, freed / (1024 * 1024));
        }
    }

    /**
     * Generate complete statement workbook
     *
     * @param startDate Statement period start
     * @param endDate Statement period end
     * @return Excel workbook with all sheets and formulas
     */
    public Workbook generateStatement(LocalDate startDate, LocalDate endDate) {
        long startTime = System.currentTimeMillis();
        log.info("🚀 STATEMENT GENERATION START: All customers from {} to {}", startDate, endDate);
        logMemoryUsage("START");

        Workbook workbook = new XSSFWorkbook();

        // MEMORY OPTIMIZATION: Create styles ONCE and reuse across all sheets
        WorkbookStyles styles = new WorkbookStyles(workbook);
        log.info("📝 Created new XSSFWorkbook with shared styles");
        logMemoryUsage("WORKBOOK_CREATED");

        // Extract data
        log.info("📥 Extracting lease master data...");
        long extractStart = System.currentTimeMillis();
        List<LeaseMasterDTO> leaseMaster = dataExtractService.extractLeaseMaster();
        log.info("📥 Lease master extracted: {} leases in {}ms", leaseMaster.size(), System.currentTimeMillis() - extractStart);
        logMemoryUsage("LEASE_MASTER_EXTRACTED");

        // IMPORTANT: Only extract INCOMING transactions (rent received) to prevent double-counting
        // This excludes OUTGOING transactions (landlord payments, fees, expenses)
        log.info("📥 Extracting transactions (rent received)...");
        extractStart = System.currentTimeMillis();
        List<TransactionDTO> transactions = dataExtractService.extractRentReceived(startDate, endDate);
        log.info("📥 Transactions extracted: {} transactions in {}ms", transactions.size(), System.currentTimeMillis() - extractStart);
        logMemoryUsage("TRANSACTIONS_EXTRACTED");

        // Create sheets - all using shared styles
        log.info("📄 Creating LEASE_MASTER sheet...");
        long sheetStart = System.currentTimeMillis();
        createLeaseMasterSheet(workbook, leaseMaster, styles);
        log.info("✅ LEASE_MASTER sheet created in {}ms", System.currentTimeMillis() - sheetStart);
        logMemoryUsage("LEASE_MASTER_SHEET");

        log.info("📄 Creating TRANSACTIONS sheet...");
        sheetStart = System.currentTimeMillis();
        createTransactionsSheet(workbook, transactions, styles);
        log.info("✅ TRANSACTIONS sheet created in {}ms", System.currentTimeMillis() - sheetStart);
        logMemoryUsage("TRANSACTIONS_SHEET");

        // MEMORY: Clear transactions list - no longer needed, data is in Excel sheet
        transactions.clear();
        transactions = null;
        System.gc(); // Hint to GC
        logMemoryUsage("TRANSACTIONS_CLEARED");

        log.info("📄 Creating RENT_DUE sheet...");
        sheetStart = System.currentTimeMillis();
        createRentDueSheet(workbook, leaseMaster, startDate, endDate, styles);
        log.info("✅ RENT_DUE sheet created in {}ms", System.currentTimeMillis() - sheetStart);
        logMemoryUsage("RENT_DUE_SHEET");

        log.info("📄 Creating RENT_RECEIVED sheet...");
        sheetStart = System.currentTimeMillis();
        createRentReceivedSheet(workbook, leaseMaster, startDate, endDate, styles);
        log.info("✅ RENT_RECEIVED sheet created in {}ms", System.currentTimeMillis() - sheetStart);
        logMemoryUsage("RENT_RECEIVED_SHEET");

        log.info("📄 Creating EXPENSES sheet...");
        sheetStart = System.currentTimeMillis();
        createExpensesSheet(workbook, leaseMaster, startDate, endDate, styles);
        log.info("✅ EXPENSES sheet created in {}ms", System.currentTimeMillis() - sheetStart);
        logMemoryUsage("EXPENSES_SHEET");

        log.info("📄 Creating MONTHLY_STATEMENT sheet...");
        sheetStart = System.currentTimeMillis();
        createMonthlyStatementSheet(workbook, leaseMaster, startDate, endDate, styles);
        log.info("✅ MONTHLY_STATEMENT sheet created in {}ms", System.currentTimeMillis() - sheetStart);
        logMemoryUsage("MONTHLY_STATEMENT_SHEET");

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("🏁 STATEMENT GENERATION COMPLETE: {} sheets created in {}ms ({}s)",
            workbook.getNumberOfSheets(), totalTime, totalTime / 1000);
        logMemoryUsage("COMPLETE");

        return workbook;
    }

    /**
     * Generate statement for specific customer
     *
     * @param customerId Customer ID
     * @param startDate Statement period start
     * @param endDate Statement period end
     * @return Excel workbook for this customer only
     */
    public Workbook generateStatementForCustomer(Long customerId, LocalDate startDate, LocalDate endDate) {
        long startTime = System.currentTimeMillis();
        log.info("🚀 CUSTOMER STATEMENT START: Customer {} from {} to {}", customerId, startDate, endDate);
        logMemoryUsage("CUSTOMER_START");

        Workbook workbook = new XSSFWorkbook();

        // MEMORY OPTIMIZATION: Create styles ONCE and reuse across all sheets
        WorkbookStyles styles = new WorkbookStyles(workbook);
        log.info("📝 Created new XSSFWorkbook with shared styles for customer {}", customerId);
        logMemoryUsage("CUSTOMER_WORKBOOK_CREATED");

        // Extract data for this customer
        log.info("📥 Extracting lease master for customer {}...", customerId);
        long extractStart = System.currentTimeMillis();
        List<LeaseMasterDTO> leaseMaster = dataExtractService.extractLeaseMasterForCustomer(customerId);
        log.info("📥 Lease master extracted: {} leases in {}ms", leaseMaster.size(), System.currentTimeMillis() - extractStart);
        logMemoryUsage("CUSTOMER_LEASE_MASTER");

        // IMPORTANT: Only extract INCOMING transactions (rent received) to prevent double-counting
        // This excludes OUTGOING transactions (landlord payments, fees, expenses)
        log.info("📥 Extracting transactions for customer {}...", customerId);
        extractStart = System.currentTimeMillis();
        List<TransactionDTO> transactions = dataExtractService.extractRentReceivedForCustomer(
            customerId, startDate, endDate);
        log.info("📥 Transactions extracted: {} transactions in {}ms", transactions.size(), System.currentTimeMillis() - extractStart);
        logMemoryUsage("CUSTOMER_TRANSACTIONS");

        // Create data sheets - all using shared styles
        log.info("📄 Creating LEASE_MASTER sheet for customer {}...", customerId);
        long sheetStart = System.currentTimeMillis();
        createLeaseMasterSheet(workbook, leaseMaster, styles);
        log.info("✅ LEASE_MASTER sheet created in {}ms", System.currentTimeMillis() - sheetStart);

        log.info("📄 Creating TRANSACTIONS sheet...");
        sheetStart = System.currentTimeMillis();
        createTransactionsSheet(workbook, transactions, styles);
        log.info("✅ TRANSACTIONS sheet created in {}ms", System.currentTimeMillis() - sheetStart);

        // MEMORY: Clear transactions list - no longer needed
        transactions.clear();
        transactions = null;
        System.gc();
        logMemoryUsage("CUSTOMER_TRANSACTIONS_CLEARED");

        log.info("📄 Creating RENT_DUE sheet...");
        sheetStart = System.currentTimeMillis();
        createRentDueSheet(workbook, leaseMaster, startDate, endDate, styles);
        log.info("✅ RENT_DUE sheet created in {}ms", System.currentTimeMillis() - sheetStart);

        log.info("📄 Creating RENT_RECEIVED sheet...");
        sheetStart = System.currentTimeMillis();
        createRentReceivedSheet(workbook, leaseMaster, startDate, endDate, styles);
        log.info("✅ RENT_RECEIVED sheet created in {}ms", System.currentTimeMillis() - sheetStart);

        log.info("📄 Creating EXPENSES sheet...");
        sheetStart = System.currentTimeMillis();
        createExpensesSheet(workbook, leaseMaster, startDate, endDate, styles);
        log.info("✅ EXPENSES sheet created in {}ms", System.currentTimeMillis() - sheetStart);
        logMemoryUsage("CUSTOMER_DATA_SHEETS_COMPLETE");

        // Create PROPERTY_ACCOUNT sheet for block property account balance tracking
        log.info("📄 Creating PROPERTY_ACCOUNT sheet...");
        sheetStart = System.currentTimeMillis();
        createPropertyAccountSheet(workbook, leaseMaster, startDate, endDate, styles);
        log.info("✅ PROPERTY_ACCOUNT sheet created in {}ms", System.currentTimeMillis() - sheetStart);

        // Create separate monthly statement sheets for each month in the period (using shared styles)
        List<site.easy.to.build.crm.util.RentCyclePeriodCalculator.RentCyclePeriod> periods =
            site.easy.to.build.crm.util.RentCyclePeriodCalculator.calculateMonthlyPeriods(startDate, endDate);

        log.info("📄 Creating {} monthly statement sheets...", periods.size());
        sheetStart = System.currentTimeMillis();
        int sheetCount = 0;
        for (site.easy.to.build.crm.util.RentCyclePeriodCalculator.RentCyclePeriod period : periods) {
            sheetCount++;
            log.debug("📄 Creating monthly sheet {}/{}: {} to {}", sheetCount, periods.size(),
                period.getStartDate(), period.getEndDate());
            createMonthlyStatementSheetForPeriod(workbook, leaseMaster, period, styles);

            // Log memory every 3 sheets to track growth
            if (sheetCount % 3 == 0) {
                logMemoryUsage("MONTHLY_SHEET_" + sheetCount);
            }
        }
        log.info("✅ {} monthly sheets created in {}ms", periods.size(), System.currentTimeMillis() - sheetStart);
        logMemoryUsage("CUSTOMER_MONTHLY_SHEETS_COMPLETE");

        // NOTE: Reconciliation is now integrated into each monthly statement sheet
        // via createPeriodBasedReconciliationSection, not as a separate sheet

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("🏁 CUSTOMER STATEMENT COMPLETE: Customer {}, {} sheets in {}ms ({}s)",
            customerId, workbook.getNumberOfSheets(), totalTime, totalTime / 1000);
        logMemoryUsage("CUSTOMER_COMPLETE");

        return workbook;
    }

    /**
     * Generate statement with custom period start day (e.g., 22nd-21st periods)
     *
     * @param startDate Statement period start
     * @param endDate Statement period end
     * @param periodStartDay Day of month when period starts (1-31)
     * @return Excel workbook with custom periods
     */
    public Workbook generateStatementWithCustomPeriods(LocalDate startDate, LocalDate endDate, int periodStartDay) {
        long startTime = System.currentTimeMillis();
        log.info("🚀 CUSTOM PERIOD STATEMENT START: periodStartDay={} from {} to {}", periodStartDay, startDate, endDate);
        logMemoryUsage("CUSTOM_PERIOD_START");

        Workbook workbook = new XSSFWorkbook();

        // MEMORY OPTIMIZATION: Create styles ONCE and reuse across all sheets
        WorkbookStyles styles = new WorkbookStyles(workbook);
        log.info("📝 Created new XSSFWorkbook with shared styles");

        // Extract data
        log.info("📥 Extracting lease master...");
        long extractStart = System.currentTimeMillis();
        List<LeaseMasterDTO> leaseMaster = dataExtractService.extractLeaseMaster();
        log.info("📥 Lease master extracted: {} leases in {}ms", leaseMaster.size(), System.currentTimeMillis() - extractStart);

        // IMPORTANT: Only extract INCOMING transactions (rent received) to prevent double-counting
        log.info("📥 Extracting transactions...");
        extractStart = System.currentTimeMillis();
        List<TransactionDTO> transactions = dataExtractService.extractRentReceived(startDate, endDate);
        log.info("📥 Transactions extracted: {} in {}ms", transactions.size(), System.currentTimeMillis() - extractStart);
        logMemoryUsage("CUSTOM_PERIOD_DATA_EXTRACTED");

        // Create sheets with custom periods - all using shared styles
        log.info("📄 Creating sheets with custom periods...");
        long sheetStart = System.currentTimeMillis();
        createLeaseMasterSheet(workbook, leaseMaster, styles);
        log.info("✅ LEASE_MASTER created in {}ms", System.currentTimeMillis() - sheetStart);

        sheetStart = System.currentTimeMillis();
        createTransactionsSheet(workbook, transactions, styles);
        log.info("✅ TRANSACTIONS created in {}ms", System.currentTimeMillis() - sheetStart);

        // MEMORY: Clear transactions list - no longer needed
        transactions.clear();
        transactions = null;
        System.gc();
        logMemoryUsage("CUSTOM_TRANSACTIONS_CLEARED");

        sheetStart = System.currentTimeMillis();
        createRentDueSheetWithCustomPeriods(workbook, leaseMaster, startDate, endDate, periodStartDay, styles);
        log.info("✅ RENT_DUE (custom) created in {}ms", System.currentTimeMillis() - sheetStart);

        sheetStart = System.currentTimeMillis();
        createRentReceivedSheet(workbook, leaseMaster, startDate, endDate, styles);
        log.info("✅ RENT_RECEIVED (flat) created in {}ms", System.currentTimeMillis() - sheetStart);

        sheetStart = System.currentTimeMillis();
        createExpensesSheet(workbook, leaseMaster, startDate, endDate, styles);
        log.info("✅ EXPENSES created in {}ms", System.currentTimeMillis() - sheetStart);

        sheetStart = System.currentTimeMillis();
        createMonthlyStatementSheetWithCustomPeriods(workbook, leaseMaster, startDate, endDate, periodStartDay, styles);
        log.info("✅ MONTHLY_STATEMENT (custom) created in {}ms", System.currentTimeMillis() - sheetStart);

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("🏁 CUSTOM PERIOD STATEMENT COMPLETE: {} sheets in {}ms ({}s)",
            workbook.getNumberOfSheets(), totalTime, totalTime / 1000);
        logMemoryUsage("CUSTOM_PERIOD_COMPLETE");

        return workbook;
    }

    /**
     * Generate statement for customer with custom periods
     *
     * @param customerId Customer ID
     * @param startDate Statement period start
     * @param endDate Statement period end
     * @param periodStartDay Day of month when period starts
     * @return Excel workbook for this customer with custom periods
     */
    public Workbook generateStatementForCustomerWithCustomPeriods(Long customerId, LocalDate startDate,
                                                                  LocalDate endDate, int periodStartDay) {
        // Default to QUARTERLY for memory efficiency
        return generateStatementForCustomerWithCustomPeriods(customerId, startDate, endDate, periodStartDay, "QUARTERLY");
    }

    /**
     * Generate statement for customer with custom periods and configurable statement frequency.
     * Data sheets cover the full period, but monthly statement sheets are generated at the specified frequency.
     *
     * @param customerId Customer ID
     * @param startDate Statement period start
     * @param endDate Statement period end
     * @param periodStartDay Day of month when period starts
     * @param statementFrequency MONTHLY (12/year), QUARTERLY (4/year), SEMI_ANNUAL (2/year), ANNUAL (1/year)
     * @return Excel workbook for this customer with custom periods
     */
    public Workbook generateStatementForCustomerWithCustomPeriods(Long customerId, LocalDate startDate,
                                                                  LocalDate endDate, int periodStartDay,
                                                                  String statementFrequency) {
        long startTime = System.currentTimeMillis();
        log.info("🚀 SXSSF STREAMING STATEMENT START: Customer {} periodStartDay={} frequency={} from {} to {}",
            customerId, periodStartDay, statementFrequency, startDate, endDate);
        logMemoryUsage("SXSSF_START");

        // MEMORY OPTIMIZATION: Use SXSSF (streaming) to write rows to disk instead of memory
        // Keep only 20 rows in memory, flush older rows to temp file
        SXSSFWorkbook workbook = new SXSSFWorkbook(20);
        workbook.setCompressTempFiles(true); // Compress temp files to save disk space

        // MEMORY OPTIMIZATION: Create styles ONCE and reuse across all sheets
        // This prevents creating 51+ duplicate CellStyle objects (was causing OOM)
        WorkbookStyles styles = new WorkbookStyles(workbook);
        log.info("📝 Created XSSFWorkbook with shared styles for customer {}", customerId);

        // Extract data for this customer
        log.info("📥 Extracting lease master for customer {}...", customerId);
        long extractStart = System.currentTimeMillis();
        List<LeaseMasterDTO> leaseMaster = dataExtractService.extractLeaseMasterForCustomer(customerId);
        log.info("📥 Lease master extracted: {} leases in {}ms", leaseMaster.size(), System.currentTimeMillis() - extractStart);
        logMemoryUsage("CUSTOMER_CUSTOM_LEASE_MASTER");

        // SXSSF: Extract ALL transactions (no date filter) for Excel formula-based opening balance
        log.info("📥 Extracting ALL transactions for customer {} (no date filter)...", customerId);
        extractStart = System.currentTimeMillis();
        List<TransactionDTO> transactions = dataExtractService.extractAllRentReceivedForCustomer(customerId);
        log.info("📥 ALL transactions extracted: {} in {}ms", transactions.size(), System.currentTimeMillis() - extractStart);
        logMemoryUsage("SXSSF_ALL_TRANSACTIONS");

        // Create data sheets with custom periods
        // MEMORY OPTIMIZATION: All sheets now use shared WorkbookStyles (was ~48 styles, now 6)
        log.info("📄 Creating data sheets...");
        long sheetStart = System.currentTimeMillis();
        createLeaseMasterSheet(workbook, leaseMaster, styles);
        log.info("✅ LEASE_MASTER created in {}ms", System.currentTimeMillis() - sheetStart);

        sheetStart = System.currentTimeMillis();
        createTransactionsSheet(workbook, transactions, styles);
        log.info("✅ TRANSACTIONS created in {}ms", System.currentTimeMillis() - sheetStart);

        // MEMORY: Clear transactions list - no longer needed, data is in Excel sheet
        transactions.clear();
        transactions = null;
        requestGC("DATA_SHEETS");

        sheetStart = System.currentTimeMillis();
        createRentDueSheetWithCustomPeriods(workbook, leaseMaster, startDate, endDate, periodStartDay, styles);
        log.info("✅ RENT_DUE (custom) created in {}ms", System.currentTimeMillis() - sheetStart);

        sheetStart = System.currentTimeMillis();
        createRentReceivedSheet(workbook, leaseMaster, startDate, endDate, styles);
        log.info("✅ RENT_RECEIVED (flat) created in {}ms", System.currentTimeMillis() - sheetStart);

        // GC hint after rent sheets
        requestGC("RENT_SHEETS");

        sheetStart = System.currentTimeMillis();
        createExpensesSheet(workbook, leaseMaster, startDate, endDate, styles);
        log.info("✅ EXPENSES created in {}ms", System.currentTimeMillis() - sheetStart);

        // Create PROPERTY_ACCOUNT sheet for block property account balance tracking
        log.info("📄 Creating PROPERTY_ACCOUNT sheet...");
        sheetStart = System.currentTimeMillis();
        createPropertyAccountSheet(workbook, leaseMaster, startDate, endDate, styles);
        log.info("✅ PROPERTY_ACCOUNT created in {}ms", System.currentTimeMillis() - sheetStart);
        logMemoryUsage("CUSTOMER_CUSTOM_DATA_SHEETS");

        // GC hint before statement sheets (heaviest part)
        requestGC("BEFORE_STATEMENT_SHEETS");

        // Generate statement periods based on frequency (reduces memory for quarterly/semi-annual)
        // Data sheets already have full history - statement sheets are just for display/summary
        List<CustomPeriod> statementPeriods = generateStatementPeriods(startDate, endDate, periodStartDay, statementFrequency);

        // Create statement sheets for each period (using shared styles)
        sheetStart = System.currentTimeMillis();
        int sheetCount = 0;
        for (CustomPeriod period : statementPeriods) {
            sheetCount++;
            String sheetName = sanitizeSheetName(
                period.periodStart.format(DateTimeFormatter.ofPattern("MMM dd")) + " - " +
                period.periodEnd.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
            );
            log.debug("📄 Creating statement sheet {}/{}: {}", sheetCount, statementPeriods.size(), sheetName);
            createMonthlyStatementSheetForCustomPeriod(workbook, leaseMaster, period, sheetName, styles, customerId);

            // Log memory and request GC every 3 sheets
            if (sheetCount % 3 == 0) {
                logMemoryUsage("CUSTOM_MONTHLY_SHEET_" + sheetCount);
                requestGC("MONTHLY_SHEET_" + sheetCount);
            }
        }
        log.info("✅ {} statement sheets created in {}ms", statementPeriods.size(), System.currentTimeMillis() - sheetStart);
        logMemoryUsage("CUSTOMER_CUSTOM_MONTHLY_SHEETS");

        // Create summary sheet (totals across all statement periods) - using shared styles
        log.info("📄 Creating summary sheet...");
        sheetStart = System.currentTimeMillis();
        createSummarySheetForCustomPeriods(workbook, leaseMaster, statementPeriods, startDate, endDate, styles);
        log.info("✅ SUMMARY sheet created in {}ms", System.currentTimeMillis() - sheetStart);

        // Create SUMMARY_CHECK sheet (calculates from monthly period tabs for verification)
        sheetStart = System.currentTimeMillis();
        createSummaryCheckSheet(workbook, leaseMaster, statementPeriods, styles);
        log.info("✅ SUMMARY_CHECK sheet created in {}ms", System.currentTimeMillis() - sheetStart);

        // Create allocation tracking sheets for this owner - using shared styles
        // Resolve the actual owner ID (handles delegated users who manage another owner's properties)
        Long resolvedOwnerId = resolveActualOwnerId(customerId);
        log.info("📄 Creating allocation sheets for owner {} (resolved from {})...", resolvedOwnerId, customerId);

        sheetStart = System.currentTimeMillis();
        createIncomeAllocationsSheet(workbook, resolvedOwnerId, styles);
        log.info("✅ INCOME_ALLOCATIONS sheet created in {}ms", System.currentTimeMillis() - sheetStart);

        sheetStart = System.currentTimeMillis();
        createExpenseAllocationsSheet(workbook, resolvedOwnerId, styles);
        log.info("✅ EXPENSE_ALLOCATIONS sheet created in {}ms", System.currentTimeMillis() - sheetStart);

        sheetStart = System.currentTimeMillis();
        createOwnerPaymentsSummarySheet(workbook, resolvedOwnerId, styles);
        log.info("✅ OWNER_PAYMENTS_SUMMARY sheet created in {}ms", System.currentTimeMillis() - sheetStart);

        // Create PAYMENT_ALLOCATIONS sheet with flat data for all payment allocations
        sheetStart = System.currentTimeMillis();
        createPaymentAllocationsSheet(workbook, resolvedOwnerId, startDate, endDate, styles);
        log.info("✅ PAYMENT_ALLOCATIONS sheet created in {}ms", System.currentTimeMillis() - sheetStart);

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("🏁 SXSSF STREAMING STATEMENT COMPLETE: Customer {}, {} sheets in {}ms ({}s)",
            customerId, workbook.getNumberOfSheets(), totalTime, totalTime / 1000);
        log.info("📊 SXSSF: Opening balance now calculated via Excel SUMIFS formula (not Java)");
        log.info("📊 SXSSF: Full transaction/rent history included for formula-based calculations");
        logMemoryUsage("SXSSF_COMPLETE");

        return workbook;
    }

    /**
     * Helper class for custom periods (e.g., 22nd-21st)
     */
    private static class CustomPeriod {
        LocalDate periodStart;
        LocalDate periodEnd;
        int periodDays;

        public CustomPeriod(LocalDate start, LocalDate end) {
            this.periodStart = start;
            this.periodEnd = end;
            this.periodDays = (int) ChronoUnit.DAYS.between(start, end) + 1;
        }
    }

    /**
     * Find the earliest lease start date from the lease master list.
     * Used to determine how far back to include data for opening balance calculations.
     */
    private LocalDate findEarliestLeaseStart(List<LeaseMasterDTO> leaseMaster) {
        LocalDate earliest = null;
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();
            if (leaseStart != null && (earliest == null || leaseStart.isBefore(earliest))) {
                earliest = leaseStart;
            }
        }
        if (earliest != null) {
            log.info("Earliest lease start date: {}", earliest);
        }
        return earliest;
    }

    /**
     * Find the cycle start date that falls within a given period.
     * Used for proration calculations when lease ends mid-cycle.
     *
     * @param leaseStart The lease start date (first cycle)
     * @param periodStart Start of the period to search
     * @param periodEnd End of the period to search
     * @param cycleMonths Months per billing cycle
     * @return The cycle start date within the period, or null if none found
     */
    private LocalDate findCycleStartInPeriod(LocalDate leaseStart, LocalDate periodStart,
                                              LocalDate periodEnd, int cycleMonths) {
        if (leaseStart == null || periodStart == null || periodEnd == null) {
            return null;
        }

        // Start from lease start and find the cycle that falls in the period
        LocalDate cycleStart = leaseStart;

        // Skip cycles before the period
        while (cycleStart.isBefore(periodStart)) {
            cycleStart = cycleStart.plusMonths(cycleMonths);
        }

        // Check if this cycle is within the period
        if (!cycleStart.isAfter(periodEnd)) {
            return cycleStart;
        }

        return null;
    }

    /**
     * Generate rent due periods based on the LEASE START DATE (anniversary-based)
     * Each lease has its own rent cycle based on when it started.
     * E.g., a lease starting March 6th has periods: Mar 6 - Apr 5, Apr 6 - May 5, etc.
     *
     * @param leaseStart The lease start date (determines rent due day)
     * @param leaseEnd The lease end date (null for ongoing leases)
     * @param overallStart The overall statement start date
     * @param overallEnd The overall statement end date
     * @return List of rent due periods for this specific lease
     */
    private List<CustomPeriod> generateLeaseBasedPeriods(LocalDate leaseStart, LocalDate leaseEnd,
                                                          LocalDate overallStart, LocalDate overallEnd) {
        // Default: only generate periods within statement range (for monthly statement sheets)
        return generateLeaseBasedPeriods(leaseStart, leaseEnd, overallStart, overallEnd, false);
    }

    /**
     * Generate rent due periods based on the LEASE START DATE (anniversary-based).
     *
     * @param leaseStart The lease start date (determines rent due day)
     * @param leaseEnd The lease end date (null for ongoing leases)
     * @param overallStart The overall statement start date
     * @param overallEnd The overall statement end date
     * @param includeFullHistory If true, include ALL periods from lease start (for data sheets/opening balances).
     *                           If false, only include periods within statement range (for monthly sheets).
     * @return List of rent due periods
     */
    private List<CustomPeriod> generateLeaseBasedPeriods(LocalDate leaseStart, LocalDate leaseEnd,
                                                          LocalDate overallStart, LocalDate overallEnd,
                                                          boolean includeFullHistory) {
        List<CustomPeriod> periods = new ArrayList<>();

        if (leaseStart == null) {
            log.warn("Lease has no start date, cannot generate periods");
            return periods;
        }

        int rentDueDay = leaseStart.getDayOfMonth();

        // Start from lease start date
        LocalDate periodStart = leaseStart;

        // If NOT including full history, skip forward to statement range
        if (!includeFullHistory && periodStart.isBefore(overallStart)) {
            // Move forward to find the rent due date in or just before the statement range
            while (periodStart.plusMonths(1).isBefore(overallStart) ||
                   periodStart.plusMonths(1).isEqual(overallStart)) {
                periodStart = periodStart.plusMonths(1);
                // Adjust for months with fewer days
                periodStart = LocalDate.of(periodStart.getYear(), periodStart.getMonthValue(),
                                          Math.min(rentDueDay, periodStart.lengthOfMonth()));
            }
        }

        // Determine the effective end date (lease end or statement end, whichever is earlier)
        LocalDate effectiveEnd = overallEnd;
        if (leaseEnd != null && leaseEnd.isBefore(overallEnd)) {
            effectiveEnd = leaseEnd;
        }

        // Generate periods from start to effective end
        while (!periodStart.isAfter(effectiveEnd)) {
            // Calculate period end (one day before next month's rent due date)
            LocalDate nextPeriodStart = periodStart.plusMonths(1);
            nextPeriodStart = LocalDate.of(nextPeriodStart.getYear(), nextPeriodStart.getMonthValue(),
                                          Math.min(rentDueDay, nextPeriodStart.lengthOfMonth()));
            LocalDate periodEnd = nextPeriodStart.minusDays(1);

            // If lease ends before period end, use lease end
            if (leaseEnd != null && leaseEnd.isBefore(periodEnd)) {
                periodEnd = leaseEnd;
            }

            // When including full history, add ALL periods
            // When not including full history, only add periods that overlap with statement range
            if (includeFullHistory || (!periodEnd.isBefore(overallStart) && !periodStart.isAfter(overallEnd))) {
                periods.add(new CustomPeriod(periodStart, periodEnd));
            }

            // Move to next period
            periodStart = nextPeriodStart;
        }

        log.debug("Generated {} lease-based periods for lease starting {} (rent due day: {}, fullHistory: {})",
                 periods.size(), leaseStart, rentDueDay, includeFullHistory);
        return periods;
    }

    /**
     * Generate custom periods based on start day (e.g., 22nd of each month)
     */
    private List<CustomPeriod> generateCustomPeriods(LocalDate start, LocalDate end, int periodStartDay) {
        return generateCustomPeriods(start, end, periodStartDay, null);
    }

    /**
     * Generate custom periods based on start day (e.g., 22nd of each month)
     *
     * @param start Statement start date
     * @param end Statement end date
     * @param periodStartDay Day of month for period boundaries (e.g., 22)
     * @param earliestLeaseStart If provided, include periods from this date for full history (for data sheets)
     */
    private List<CustomPeriod> generateCustomPeriods(LocalDate start, LocalDate end, int periodStartDay,
                                                     LocalDate earliestLeaseStart) {
        List<CustomPeriod> periods = new ArrayList<>();

        // Determine effective start - use earliest lease start if provided (for full history)
        LocalDate effectiveStart = start;
        if (earliestLeaseStart != null && earliestLeaseStart.isBefore(start)) {
            effectiveStart = earliestLeaseStart;
            log.info("Including full history from {} for opening balance calculations", earliestLeaseStart);
        }

        // Find first period start on or after the effective start date
        LocalDate periodStart = LocalDate.of(effectiveStart.getYear(), effectiveStart.getMonthValue(),
                                            Math.min(periodStartDay, effectiveStart.lengthOfMonth()));

        // If we're past the period start day in the first month, use start of that period
        if (periodStart.isAfter(effectiveStart)) {
            // Go back one month to catch the period that contains effectiveStart
            periodStart = periodStart.minusMonths(1);
            periodStart = LocalDate.of(periodStart.getYear(), periodStart.getMonthValue(),
                                      Math.min(periodStartDay, periodStart.lengthOfMonth()));
        }

        while (!periodStart.isAfter(end)) {
            // Period ends one day before next month's start day
            LocalDate nextMonthStart = periodStart.plusMonths(1);
            // Handle month boundaries (e.g., Feb 22 -> March 22)
            nextMonthStart = LocalDate.of(nextMonthStart.getYear(), nextMonthStart.getMonthValue(),
                                         Math.min(periodStartDay, nextMonthStart.lengthOfMonth()));
            LocalDate periodEnd = nextMonthStart.minusDays(1);

            // Adjust if period end exceeds overall end date
            if (periodEnd.isAfter(end)) {
                periodEnd = end;
            }

            periods.add(new CustomPeriod(periodStart, periodEnd));

            // Move to next period
            periodStart = nextMonthStart;
        }

        log.info("Generated {} custom periods with start day {} (from {} to {})",
                periods.size(), periodStartDay, effectiveStart, end);
        return periods;
    }

    /**
     * Generate statement periods based on frequency (MONTHLY, QUARTERLY, SEMI_ANNUAL, ANNUAL).
     * Used to control how many monthly statement sheets are generated (memory optimization).
     *
     * @param startDate Statement start date
     * @param endDate Statement end date
     * @param periodStartDay Day of month for period boundaries (e.g., 22)
     * @param frequency MONTHLY (12/year), QUARTERLY (4/year), SEMI_ANNUAL (2/year), ANNUAL (1/year)
     * @return List of statement periods (fewer than monthly periods for quarterly/semi-annual/annual)
     */
    private List<CustomPeriod> generateStatementPeriods(LocalDate startDate, LocalDate endDate,
                                                        int periodStartDay, String frequency) {
        List<CustomPeriod> periods = new ArrayList<>();

        // Determine months per period based on frequency
        int monthsPerPeriod;
        switch (frequency.toUpperCase()) {
            case "MONTHLY":
                monthsPerPeriod = 1;
                break;
            case "QUARTERLY":
                monthsPerPeriod = 3;
                break;
            case "SEMI_ANNUAL":
                monthsPerPeriod = 6;
                break;
            case "ANNUAL":
                monthsPerPeriod = 12;
                break;
            default:
                log.warn("Unknown frequency '{}', defaulting to QUARTERLY", frequency);
                monthsPerPeriod = 3;
        }

        // Find first period start on or after the start date
        LocalDate periodStart = LocalDate.of(startDate.getYear(), startDate.getMonthValue(),
                                            Math.min(periodStartDay, startDate.lengthOfMonth()));

        // If we're past the period start day in the first month, use start of that period
        if (periodStart.isAfter(startDate)) {
            periodStart = periodStart.minusMonths(1);
            periodStart = LocalDate.of(periodStart.getYear(), periodStart.getMonthValue(),
                                      Math.min(periodStartDay, periodStart.lengthOfMonth()));
        }

        while (!periodStart.isAfter(endDate)) {
            // Period ends based on frequency (e.g., 3 months for quarterly)
            LocalDate nextPeriodStart = periodStart.plusMonths(monthsPerPeriod);
            nextPeriodStart = LocalDate.of(nextPeriodStart.getYear(), nextPeriodStart.getMonthValue(),
                                          Math.min(periodStartDay, nextPeriodStart.lengthOfMonth()));
            LocalDate periodEnd = nextPeriodStart.minusDays(1);

            // Adjust if period end exceeds overall end date
            if (periodEnd.isAfter(endDate)) {
                periodEnd = endDate;
            }

            periods.add(new CustomPeriod(periodStart, periodEnd));

            // Move to next period
            periodStart = nextPeriodStart;
        }

        log.info("Generated {} {} statement periods (start day: {}) from {} to {}",
                periods.size(), frequency, periodStartDay, startDate, endDate);
        return periods;
    }

    /**
     * Create LEASE_MASTER sheet with raw lease data
     * NO CALCULATIONS - Just data from database
     */
    private void createLeaseMasterSheet(Workbook workbook, List<LeaseMasterDTO> leaseMaster, WorkbookStyles styles) {
        log.info("Creating LEASE_MASTER sheet with {} leases", leaseMaster.size());

        Sheet sheet = workbook.createSheet("LEASE_MASTER");

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_id", "lease_reference", "property_id", "property_name", "property_address",
            "customer_id", "customer_name", "start_date", "end_date", "monthly_rent", "frequency",
            "frequency_months"  // Numeric billing cycle for rent_due calculations
        };

        // Use shared styles (memory optimization)
        CellStyle headerStyle = styles.headerStyle;
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Use shared styles
        CellStyle dateStyle = styles.dateStyle;
        CellStyle currencyStyle = styles.currencyStyle;

        int rowNum = 1;
        for (LeaseMasterDTO lease : leaseMaster) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(lease.getLeaseId());
            row.createCell(1).setCellValue(lease.getLeaseReference());
            row.createCell(2).setCellValue(lease.getPropertyId() != null ? lease.getPropertyId() : 0);
            row.createCell(3).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");
            row.createCell(4).setCellValue(lease.getPropertyAddress() != null ? lease.getPropertyAddress() : "");
            row.createCell(5).setCellValue(lease.getCustomerId() != null ? lease.getCustomerId() : 0);
            row.createCell(6).setCellValue(lease.getCustomerName() != null ? lease.getCustomerName() : "");

            // Start date
            Cell startDateCell = row.createCell(7);
            if (lease.getStartDate() != null) {
                startDateCell.setCellValue(lease.getStartDate());
                startDateCell.setCellStyle(dateStyle);
            }

            // End date (may be NULL for ongoing leases)
            Cell endDateCell = row.createCell(8);
            if (lease.getEndDate() != null) {
                endDateCell.setCellValue(lease.getEndDate());
                endDateCell.setCellStyle(dateStyle);
            }

            // Monthly rent
            Cell rentCell = row.createCell(9);
            if (lease.getMonthlyRent() != null) {
                rentCell.setCellValue(lease.getMonthlyRent().doubleValue());
                rentCell.setCellStyle(currencyStyle);
            }

            row.createCell(10).setCellValue(lease.getFrequency() != null ? lease.getFrequency() : "MONTHLY");
            row.createCell(11).setCellValue(lease.getFrequencyMonths());  // Numeric billing cycle
        }

        // Apply fixed column widths (autoSizeColumn causes OutOfMemoryError on large sheets)
        applyFixedColumnWidths(sheet, headers.length);

        log.info("LEASE_MASTER sheet created with {} rows", leaseMaster.size());
    }

    /**
     * Create TRANSACTIONS sheet with raw transaction data
     * NO CALCULATIONS - Just data from database
     */
    private void createTransactionsSheet(Workbook workbook, List<TransactionDTO> transactions, WorkbookStyles styles) {
        log.info("Creating TRANSACTIONS sheet with {} transactions", transactions.size());

        Sheet sheet = workbook.createSheet("TRANSACTIONS");

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "transaction_id", "transaction_date", "invoice_id", "property_id", "property_name",
            "customer_id", "category", "transaction_type", "amount", "description", "lease_reference"
        };

        // Use shared styles (memory optimization)
        CellStyle headerStyle = styles.headerStyle;
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Use shared styles
        CellStyle dateStyle = styles.dateStyle;
        CellStyle currencyStyle = styles.currencyStyle;

        int rowNum = 1;
        for (TransactionDTO txn : transactions) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(txn.getTransactionId());

            // Transaction date
            Cell dateCell = row.createCell(1);
            if (txn.getTransactionDate() != null) {
                dateCell.setCellValue(txn.getTransactionDate());
                dateCell.setCellStyle(dateStyle);
            }

            row.createCell(2).setCellValue(txn.getInvoiceId() != null ? txn.getInvoiceId() : 0);
            row.createCell(3).setCellValue(txn.getPropertyId() != null ? txn.getPropertyId() : 0);
            row.createCell(4).setCellValue(txn.getPropertyName() != null ? txn.getPropertyName() : "");
            row.createCell(5).setCellValue(txn.getCustomerId() != null ? txn.getCustomerId() : 0);
            row.createCell(6).setCellValue(txn.getCategory() != null ? txn.getCategory() : "");
            row.createCell(7).setCellValue(txn.getTransactionType() != null ? txn.getTransactionType() : "");

            // Amount
            Cell amountCell = row.createCell(8);
            if (txn.getAmount() != null) {
                amountCell.setCellValue(txn.getAmount().doubleValue());
                amountCell.setCellStyle(currencyStyle);
            }

            row.createCell(9).setCellValue(txn.getDescription() != null ? txn.getDescription() : "");
            row.createCell(10).setCellValue(txn.getLeaseReference() != null ? txn.getLeaseReference() : "");
        }

        // Apply fixed column widths (autoSizeColumn causes OutOfMemoryError on large sheets)
        applyFixedColumnWidths(sheet, headers.length);

        log.info("TRANSACTIONS sheet created with {} rows", transactions.size());
    }

    /**
     * Create RENT_DUE sheet with FORMULAS for pro-rating
     * This is where Excel formulas do the heavy lifting!
     */
    private void createRentDueSheet(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                   LocalDate startDate, LocalDate endDate, WorkbookStyles styles) {
        log.info("Creating RENT_DUE sheet with formulas");

        Sheet sheet = workbook.createSheet("RENT_DUE");

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_id", "lease_reference", "property_name", "month_start", "month_end",
            "days_in_month", "lease_days_in_month", "prorated_rent_due",
            "management_fee", "service_fee", "total_commission", "net_to_owner"
        };

        // Use shared styles (memory optimization)
        CellStyle headerStyle = styles.headerStyle;
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Use shared styles
        CellStyle dateStyle = styles.dateStyle;
        CellStyle currencyStyle = styles.currencyStyle;

        int rowNum = 1;

        // Generate one row per lease per month that has started
        // Show active leases AND ended leases (with £0 rent due), but NOT future leases that haven't started
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();
            LocalDate leaseEnd = lease.getEndDate();

            // Generate monthly rows from LEASE START to statement end (for accurate cumulative arrears)
            YearMonth currentMonth = leaseStart != null ? YearMonth.from(leaseStart) : YearMonth.from(startDate);
            YearMonth endMonth = YearMonth.from(endDate);

            while (!currentMonth.isAfter(endMonth)) {
                LocalDate monthStart = currentMonth.atDay(1);
                LocalDate monthEnd = currentMonth.atEndOfMonth();

                // Skip future leases that haven't started yet (lease start is after month end)
                if (leaseStart != null && leaseStart.isAfter(monthEnd)) {
                    currentMonth = currentMonth.plusMonths(1);
                    continue; // Don't show leases that haven't started yet
                }

                // Check if lease is active in this month (used for rent calculation)
                boolean leaseActiveInMonth = (leaseEnd == null || !monthEnd.isBefore(leaseStart))
                                   && !monthStart.isAfter(leaseEnd != null ? leaseEnd : monthEnd);

                // Create row for leases that have started (active or ended, but not future)
                Row row = sheet.createRow(rowNum);
                int col = 0;

                // Column A: lease_id (value)
                row.createCell(col++).setCellValue(lease.getLeaseId());

                // Column B: lease_reference (value)
                row.createCell(col++).setCellValue(lease.getLeaseReference());

                // Column C: property_name (value)
                row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");

                // Column D: month_start (value)
                Cell monthStartCell = row.createCell(col++);
                monthStartCell.setCellValue(monthStart);
                monthStartCell.setCellStyle(dateStyle);

                // Column E: month_end (formula: EOMONTH)
                Cell monthEndCell = row.createCell(col++);
                monthEndCell.setCellFormula("EOMONTH(D" + (rowNum + 1) + ",0)");
                monthEndCell.setCellStyle(dateStyle);

                // Column F: days_in_month (formula)
                row.createCell(col++).setCellFormula("DAY(E" + (rowNum + 1) + ")");

                // Column G: lease_days_in_month - 0 if lease not active in this month
                Cell leaseDaysCell = row.createCell(col++);
                if (leaseActiveInMonth) {
                    // Formula: MAX(0, MIN(lease_end, month_end) - MAX(lease_start, month_start) + 1)
                    String leaseDaysFormula = String.format(
                        "MAX(0, MIN(IF(ISBLANK(VLOOKUP(A%d,LEASE_MASTER!A:I,9,FALSE)), E%d, VLOOKUP(A%d,LEASE_MASTER!A:I,9,FALSE)), E%d) - MAX(VLOOKUP(A%d,LEASE_MASTER!A:I,8,FALSE), D%d) + 1)",
                        rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1
                    );
                    leaseDaysCell.setCellFormula(leaseDaysFormula);
                } else {
                    // Lease not active - 0 days in this month
                    leaseDaysCell.setCellValue(0);
                }

                // Column H: rent_due - RENT IN ADVANCE model with proration
                // Full cycle rent is due on lease start date and each cycle anniversary
                // If lease ends mid-cycle, prorate based on days used
                Cell rentDueCell = row.createCell(col++);
                if (leaseActiveInMonth) {
                    int cycleMonths = lease.getFrequencyMonths() != null ? lease.getFrequencyMonths() : 1;

                    if (cycleMonths == 1) {
                        // Monthly billing: use proration formula for partial months
                        int r = rowNum + 1;
                        String proratedFormula = String.format(
                            "IF(AND(NOT(ISBLANK(VLOOKUP(A%d,LEASE_MASTER!A:L,9,FALSE))),VLOOKUP(A%d,LEASE_MASTER!A:L,9,FALSE)>=D%d,VLOOKUP(A%d,LEASE_MASTER!A:L,9,FALSE)<=E%d)," +
                            "ROUND((VLOOKUP(A%d,LEASE_MASTER!A:L,9,FALSE)-MAX(VLOOKUP(A%d,LEASE_MASTER!A:L,8,FALSE),D%d)+1)/F%d*VLOOKUP(A%d,LEASE_MASTER!A:L,10,FALSE),2)," +
                            "VLOOKUP(A%d,LEASE_MASTER!A:L,10,FALSE))",
                            r, r, r, r, r, r, r, r, r, r, r
                        );
                        rentDueCell.setCellFormula(proratedFormula);
                    } else {
                        // Multi-month billing: check if a cycle start date falls in this month
                        long cyclesInMonth = dataExtractService.countCycleStartDatesInPeriod(
                            leaseStart, monthStart, monthEnd, cycleMonths);

                        if (cyclesInMonth > 0) {
                            // Cycle starts in this month - calculate rent with proration if lease ends mid-cycle
                            LocalDate cycleStartDate = findCycleStartInPeriod(leaseStart, monthStart, monthEnd, cycleMonths);
                            LocalDate cycleEndDate = cycleStartDate.plusMonths(cycleMonths).minusDays(1);
                            LocalDate leaseEndDate = lease.getEndDate();

                            double rentDue;
                            if (leaseEndDate != null && leaseEndDate.isBefore(cycleEndDate)) {
                                // Lease ends mid-cycle - prorate
                                // Calculate: (full months × monthly rate) + (remaining days / days in final month × monthly rate)
                                double monthlyRate = lease.getMonthlyRent().doubleValue() / cycleMonths;
                                long fullMonths = java.time.temporal.ChronoUnit.MONTHS.between(cycleStartDate, leaseEndDate.plusDays(1));
                                LocalDate lastFullMonthEnd = cycleStartDate.plusMonths(fullMonths);
                                long remainingDays = java.time.temporal.ChronoUnit.DAYS.between(lastFullMonthEnd, leaseEndDate) + 1;
                                int daysInFinalMonth = leaseEndDate.lengthOfMonth();

                                rentDue = (fullMonths * monthlyRate) + ((double) remainingDays / daysInFinalMonth * monthlyRate);
                                rentDue = Math.round(rentDue * 100.0) / 100.0; // Round to 2 decimal places

                                log.debug("Prorated rent for lease {}: {} months + {} days = £{}",
                                    lease.getLeaseReference(), fullMonths, remainingDays, rentDue);
                            } else {
                                // Full cycle - no proration needed
                                rentDue = lease.getMonthlyRent().doubleValue() * cyclesInMonth;
                            }
                            rentDueCell.setCellValue(rentDue);
                        } else {
                            // No cycle start in this month - £0 due
                            rentDueCell.setCellValue(0);
                        }
                    }
                } else {
                    // Lease not active - rent due is £0
                    rentDueCell.setCellValue(0);
                }
                rentDueCell.setCellStyle(currencyStyle);

                // Column I: management_fee (formula: 10%)
                // BLOCK PROPERTY: No commission for block properties (they're communal funds, not rent)
                boolean isBlockProperty = Boolean.TRUE.equals(lease.getIsBlockProperty());
                double mgmtFeeRate = isBlockProperty ? 0.0 : commissionConfig.getManagementFeePercent().doubleValue();
                Cell mgmtFeeCell = row.createCell(col++);
                mgmtFeeCell.setCellFormula(String.format("H%d * %.4f", rowNum + 1, mgmtFeeRate));
                mgmtFeeCell.setCellStyle(currencyStyle);

                // Column J: service_fee (formula: 5%)
                // BLOCK PROPERTY: No service fee for block properties
                double svcFeeRate = isBlockProperty ? 0.0 : commissionConfig.getServiceFeePercent().doubleValue();
                Cell svcFeeCell = row.createCell(col++);
                svcFeeCell.setCellFormula(String.format("H%d * %.4f", rowNum + 1, svcFeeRate));
                svcFeeCell.setCellStyle(currencyStyle);

                // Column K: total_commission (formula)
                Cell totalCommCell = row.createCell(col++);
                totalCommCell.setCellFormula(String.format("I%d + J%d", rowNum + 1, rowNum + 1));
                totalCommCell.setCellStyle(currencyStyle);

                // Column L: net_to_owner (formula)
                Cell netToOwnerCell = row.createCell(col++);
                netToOwnerCell.setCellFormula(String.format("H%d - K%d", rowNum + 1, rowNum + 1));
                netToOwnerCell.setCellStyle(currencyStyle);

                rowNum++;

                currentMonth = currentMonth.plusMonths(1);
            }
        }

        // Apply fixed column widths (autoSizeColumn causes OutOfMemoryError on large sheets)
        applyFixedColumnWidths(sheet, headers.length);

        log.info("RENT_DUE sheet created with {} rows (lease × month)", rowNum - 1);
    }

    /**
     * Create RENT_RECEIVED sheet with payment breakdown
     * Shows individual payment dates and amounts (up to 4 payments per period)
     */
    private void createRentReceivedSheet(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                        LocalDate startDate, LocalDate endDate, WorkbookStyles styles) {
        log.info("Creating RENT_RECEIVED sheet (flat structure - one row per payment)");

        Sheet sheet = workbook.createSheet("RENT_RECEIVED");

        // Header row - flat structure with batch tracking columns
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_id", "lease_reference", "property_name",
            "payment_date", "amount",
            "batch_id", "payment_status", "paid_date"
        };

        // Use shared styles (memory optimization)
        CellStyle headerStyle = styles.headerStyle;
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Use shared styles
        CellStyle dateStyle = styles.dateStyle;
        CellStyle currencyStyle = styles.currencyStyle;

        int rowNum = 1;

        // Generate one row per payment (flat structure)
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();

            // Determine query start date: from lease start or statement start (whichever is earlier)
            // This captures all historical payments for opening balance calculations
            LocalDate queryStart = leaseStart != null && leaseStart.isBefore(startDate) ? leaseStart : startDate;

            // Get ALL payments for this lease across the entire date range
            List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> payments =
                dataExtractService.extractRentReceivedDetails(lease.getLeaseId(), queryStart, endDate);

            // Create one row per payment
            for (site.easy.to.build.crm.dto.statement.PaymentDetailDTO payment : payments) {
                Row row = sheet.createRow(rowNum);
                int col = 0;

                // Column A: lease_id
                row.createCell(col++).setCellValue(lease.getLeaseId());

                // Column B: lease_reference
                row.createCell(col++).setCellValue(lease.getLeaseReference());

                // Column C: property_name
                row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");

                // Column D: payment_date
                Cell paymentDateCell = row.createCell(col++);
                if (payment.getPaymentDate() != null) {
                    paymentDateCell.setCellValue(payment.getPaymentDate());
                    paymentDateCell.setCellStyle(dateStyle);
                }

                // Column E: amount
                Cell amountCell = row.createCell(col++);
                if (payment.getAmount() != null) {
                    amountCell.setCellValue(payment.getAmount().doubleValue());
                    amountCell.setCellStyle(currencyStyle);
                }

                // Column F: batch_id
                row.createCell(col++).setCellValue(payment.getBatchId() != null ? payment.getBatchId() : "");

                // Column G: payment_status
                row.createCell(col++).setCellValue(payment.getPaymentStatus() != null ? payment.getPaymentStatus() : "");

                // Column H: paid_date
                Cell paidDateCell = row.createCell(col++);
                if (payment.getPaidDate() != null) {
                    paidDateCell.setCellValue(payment.getPaidDate());
                    paidDateCell.setCellStyle(dateStyle);
                }

                rowNum++;
            }
        }

        // Apply fixed column widths (autoSizeColumn causes OutOfMemoryError on large sheets)
        applyFixedColumnWidths(sheet, headers.length);

        log.info("RENT_RECEIVED sheet created with {} rows (flat structure)", rowNum - 1);
    }

    /**
     * Create EXPENSES sheet with expense breakdown by date, amount, and category
     * Shows property expenses (cleaning, maintenance, furnishings, etc.)
     * Includes batch tracking columns for payment reconciliation.
     */
    private void createExpensesSheet(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                     LocalDate startDate, LocalDate endDate, WorkbookStyles styles) {
        log.info("Creating EXPENSES sheet with flat structure (one row per expense)");

        Sheet sheet = workbook.createSheet("EXPENSES");

        // Flat format with batch tracking columns
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_id", "lease_reference", "property_name",
            "expense_date", "expense_amount", "expense_category", "expense_description",
            "batch_id", "payment_status", "paid_date"
        };

        // Use shared styles (memory optimization)
        CellStyle headerStyle = styles.headerStyle;
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Use shared styles
        CellStyle dateStyle = styles.dateStyle;
        CellStyle currencyStyle = styles.currencyStyle;

        int rowNum = 1;

        // Generate one row per expense (flat structure)
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();

            // Get ALL expenses for this lease from lease start to statement end
            LocalDate expenseStartDate = leaseStart != null ? leaseStart : startDate;
            List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> expenses =
                dataExtractService.extractExpenseDetails(lease.getLeaseId(), expenseStartDate, endDate);

            // Create one row per expense
            for (site.easy.to.build.crm.dto.statement.PaymentDetailDTO expense : expenses) {
                Row row = sheet.createRow(rowNum);
                int col = 0;

                // Column A: lease_id
                row.createCell(col++).setCellValue(lease.getLeaseId());

                // Column B: lease_reference
                row.createCell(col++).setCellValue(lease.getLeaseReference());

                // Column C: property_name
                row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");

                // Column D: expense_date
                Cell expenseDateCell = row.createCell(col++);
                if (expense.getPaymentDate() != null) {
                    expenseDateCell.setCellValue(expense.getPaymentDate());
                    expenseDateCell.setCellStyle(dateStyle);
                }

                // Column E: expense_amount
                Cell expenseAmountCell = row.createCell(col++);
                if (expense.getAmount() != null) {
                    expenseAmountCell.setCellValue(expense.getAmount().doubleValue());
                    expenseAmountCell.setCellStyle(currencyStyle);
                }

                // Column F: expense_category
                row.createCell(col++).setCellValue(expense.getCategory() != null ? expense.getCategory() : "");

                // Column G: expense_description
                row.createCell(col++).setCellValue(expense.getDescription() != null ? expense.getDescription() : "");

                // Column H: batch_id
                row.createCell(col++).setCellValue(expense.getBatchId() != null ? expense.getBatchId() : "");

                // Column I: payment_status
                row.createCell(col++).setCellValue(expense.getPaymentStatus() != null ? expense.getPaymentStatus() : "");

                // Column J: paid_date
                Cell paidDateCell = row.createCell(col++);
                if (expense.getPaidDate() != null) {
                    paidDateCell.setCellValue(expense.getPaidDate());
                    paidDateCell.setCellStyle(dateStyle);
                }

                rowNum++;
            }
        }

        // Apply fixed column widths (autoSizeColumn causes OutOfMemoryError on large sheets)
        applyFixedColumnWidths(sheet, headers.length);

        log.info("EXPENSES sheet created with {} expense rows", rowNum - 1);
    }

    /**
     * Create PROPERTY_ACCOUNT sheet for block property account balance tracking
     *
     * FLAT STRUCTURE: One row per transaction (like RENT_RECEIVED and EXPENSES sheets)
     *
     * Structure:
     * - Column A: property_id (block property ID or source property ID)
     * - Column B: property_name (block property name or flat name)
     * - Column C: transaction_date
     * - Column D: movement_type ("IN" for allocations, "OUT" for expenses)
     * - Column E: amount (positive for both - allocations flip sign)
     * - Column F: category
     * - Column G: description
     * - Column H: source_reference (payprop_transaction_id)
     *
     * Data sources:
     * 1. IN: PROPERTY_ACCOUNT_ALLOCATION transactions (negative amounts flipped to positive)
     * 2. OUT: OUTGOING transactions for block properties (expenses)
     *
     * Balance formula: SUMIF(movement_type="IN") - SUMIF(movement_type="OUT")
     */
    private void createPropertyAccountSheet(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                            LocalDate startDate, LocalDate endDate, WorkbookStyles styles) {
        log.info("Creating PROPERTY_ACCOUNT sheet (flat structure - one row per transaction)");

        Sheet sheet = workbook.createSheet("PROPERTY_ACCOUNT");

        // Header row - flat structure matching RENT_RECEIVED/EXPENSES pattern
        Row header = sheet.createRow(0);
        String[] headers = {
            "property_id", "property_name", "transaction_date", "movement_type",
            "amount", "category", "description", "source_reference"
        };

        // Use shared styles (memory optimization)
        CellStyle headerStyle = styles.headerStyle;
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Use shared styles
        CellStyle dateStyle = styles.dateStyle;
        CellStyle currencyStyle = styles.currencyStyle;

        int rowNum = 1;

        // 1. Get ALL PROPERTY_ACCOUNT_ALLOCATION transactions (IN - money allocated to property account)
        // These are stored with negative amounts, so we flip the sign for display
        List<UnifiedTransaction> allocations = unifiedTransactionRepository.findPropertyAccountAllocations();
        log.debug("Found {} PROPERTY_ACCOUNT_ALLOCATION transactions for property account inflows", allocations.size());

        for (UnifiedTransaction allocation : allocations) {
            Row row = sheet.createRow(rowNum);
            int col = 0;

            // Column A: property_id (the block property receiving the allocation)
            Long propId = allocation.getPropertyId();
            row.createCell(col++).setCellValue(propId != null ? propId : 0);

            // Column B: property_name
            row.createCell(col++).setCellValue(allocation.getPropertyName() != null ? allocation.getPropertyName() : "");

            // Column C: transaction_date
            Cell dateCell = row.createCell(col++);
            if (allocation.getTransactionDate() != null) {
                dateCell.setCellValue(allocation.getTransactionDate());
                dateCell.setCellStyle(dateStyle);
            }

            // Column D: movement_type = "IN" (money coming into property account)
            row.createCell(col++).setCellValue("IN");

            // Column E: amount (flip sign - stored as negative, display as positive)
            Cell amountCell = row.createCell(col++);
            java.math.BigDecimal amount = allocation.getAmount();
            if (amount != null) {
                // Flip sign: stored as negative, show as positive IN
                amountCell.setCellValue(amount.abs().doubleValue());
                amountCell.setCellStyle(currencyStyle);
            }

            // Column F: category
            row.createCell(col++).setCellValue(allocation.getCategory() != null ? allocation.getCategory() : "Property Account Allocation");

            // Column G: description
            row.createCell(col++).setCellValue(allocation.getDescription() != null ? allocation.getDescription() : "");

            // Column H: source_reference
            row.createCell(col++).setCellValue(allocation.getPaypropTransactionId() != null ? allocation.getPaypropTransactionId() : "");

            rowNum++;
        }

        // 2. Get ALL OUTGOING transactions for block properties (OUT - expenses paid from property account)
        List<UnifiedTransaction> blockExpenses = unifiedTransactionRepository.findAllBlockPropertyExpenses();
        log.debug("Found {} OUTGOING transactions for block property expenses", blockExpenses.size());

        for (UnifiedTransaction expense : blockExpenses) {
            Row row = sheet.createRow(rowNum);
            int col = 0;

            // Column A: property_id (the block property)
            Long propId = expense.getPropertyId();
            row.createCell(col++).setCellValue(propId != null ? propId : 0);

            // Column B: property_name
            row.createCell(col++).setCellValue(expense.getPropertyName() != null ? expense.getPropertyName() : "");

            // Column C: transaction_date
            Cell dateCell = row.createCell(col++);
            if (expense.getTransactionDate() != null) {
                dateCell.setCellValue(expense.getTransactionDate());
                dateCell.setCellStyle(dateStyle);
            }

            // Column D: movement_type = "OUT" (money going out of property account)
            row.createCell(col++).setCellValue("OUT");

            // Column E: amount (keep as positive - expenses are stored as positive OUTGOING)
            Cell amountCell = row.createCell(col++);
            java.math.BigDecimal amount = expense.getAmount();
            if (amount != null) {
                // Use absolute value for display consistency
                amountCell.setCellValue(amount.abs().doubleValue());
                amountCell.setCellStyle(currencyStyle);
            }

            // Column F: category
            row.createCell(col++).setCellValue(expense.getCategory() != null ? expense.getCategory() : expense.getTransactionType());

            // Column G: description
            row.createCell(col++).setCellValue(expense.getDescription() != null ? expense.getDescription() : "");

            // Column H: source_reference
            row.createCell(col++).setCellValue(expense.getPaypropTransactionId() != null ? expense.getPaypropTransactionId() : "");

            rowNum++;
        }

        // Apply fixed column widths (autoSizeColumn causes OutOfMemoryError on large sheets)
        applyFixedColumnWidths(sheet, headers.length);

        log.info("PROPERTY_ACCOUNT sheet created with {} rows ({} IN allocations, {} OUT expenses)",
            rowNum - 1, allocations.size(), blockExpenses.size());
    }

    /**
     * Create MONTHLY_STATEMENT sheet with FORMULAS combining DUE and RECEIVED
     * Final output sheet with arrears calculation
     */
    private void createMonthlyStatementSheet(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                            LocalDate startDate, LocalDate endDate, WorkbookStyles styles) {
        log.info("Creating MONTHLY_STATEMENT sheet with formulas");

        Sheet sheet = workbook.createSheet("MONTHLY_STATEMENT");

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_reference", "property_name", "customer_name", "lease_start_date", "rent_due_day", "month",
            "rent_due", "rent_received", "arrears", "management_fee", "service_fee",
            "total_commission", "total_expenses", "net_to_owner", "opening_balance", "cumulative_arrears",
            // NEW: Block property and property account balance columns
            "block_name", "property_account_opening", "property_account_in", "property_account_out", "property_account_closing"
        };

        // Use shared styles (memory optimization)
        CellStyle headerStyle = styles.headerStyle;
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Use shared styles
        CellStyle dateStyle = styles.dateStyle;
        CellStyle currencyStyle = styles.currencyStyle;

        int rowNum = 1;

        // Generate one row per lease per month (statement period only)
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();
            LocalDate leaseEnd = lease.getEndDate();

            // Generate monthly rows for STATEMENT PERIOD only (opening balance handles prior history)
            YearMonth currentMonth = YearMonth.from(startDate);
            YearMonth endMonth = YearMonth.from(endDate);
            boolean isFirstRowForLease = true;

            while (!currentMonth.isAfter(endMonth)) {
                LocalDate monthStart = currentMonth.atDay(1);
                LocalDate monthEnd = currentMonth.atEndOfMonth();

                boolean leaseActive = (leaseEnd == null || !monthEnd.isBefore(leaseStart))
                                   && !monthStart.isAfter(leaseEnd != null ? leaseEnd : monthEnd);

                if (leaseActive) {
                    Row row = sheet.createRow(rowNum);
                    int col = 0;

                    // Column A: lease_reference
                    row.createCell(col++).setCellValue(lease.getLeaseReference());

                    // Column B: property_name
                    row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");

                    // Column C: customer_name
                    row.createCell(col++).setCellValue(lease.getCustomerName() != null ? lease.getCustomerName() : "");

                    // Column D: lease_start_date
                    Cell startDateCell = row.createCell(col++);
                    if (leaseStart != null) {
                        startDateCell.setCellValue(leaseStart);
                        startDateCell.setCellStyle(dateStyle);
                    }

                    // Column E: rent_due_day
                    row.createCell(col++).setCellValue(lease.getPaymentDay() != null ? lease.getPaymentDay() : 0);

                    // Column F: month
                    Cell monthCell = row.createCell(col++);
                    monthCell.setCellValue(monthStart);
                    monthCell.setCellStyle(dateStyle);

                    // Column G: rent_due (SUMIFS to RENT_DUE sheet - simpler than array formula)
                    Cell rentDueCell = row.createCell(col++);
                    rentDueCell.setCellFormula(String.format(
                        "SUMIFS(RENT_DUE!H:H, RENT_DUE!A:A, %d, RENT_DUE!D:D, F%d)",
                        lease.getLeaseId(), rowNum + 1
                    ));
                    rentDueCell.setCellStyle(currencyStyle);

                    // Column H: rent_received (SUMIFS to RENT_RECEIVED sheet - simpler than array formula)
                    Cell rentReceivedCell = row.createCell(col++);
                    rentReceivedCell.setCellFormula(String.format(
                        "SUMIFS(RENT_RECEIVED!O:O, RENT_RECEIVED!A:A, %d, RENT_RECEIVED!E:E, F%d)",
                        lease.getLeaseId(), rowNum + 1
                    ));
                    rentReceivedCell.setCellStyle(currencyStyle);

                    // Column I: arrears (formula: due - received)
                    Cell arrearsCell = row.createCell(col++);
                    arrearsCell.setCellFormula(String.format("G%d - H%d", rowNum + 1, rowNum + 1));
                    arrearsCell.setCellStyle(currencyStyle);

                    // Column J: management_fee (formula: rent_received * management_fee_percent)
                    // BLOCK PROPERTY: No commission for block properties (they're communal funds, not rent)
                    // Use property-specific commission rate, fallback to global default if not set
                    boolean isBlockProperty = Boolean.TRUE.equals(lease.getIsBlockProperty());
                    double mgmtFeeRate = isBlockProperty ? 0.0
                        : (lease.getCommissionPercentage() != null
                            ? lease.getCommissionPercentage().doubleValue() / 100.0  // Property stores as percentage (e.g., 10), convert to decimal
                            : commissionConfig.getManagementFeePercent().doubleValue());
                    Cell mgmtFeeCell = row.createCell(col++);
                    mgmtFeeCell.setCellFormula(String.format("H%d * %.4f", rowNum + 1, mgmtFeeRate));
                    mgmtFeeCell.setCellStyle(currencyStyle);

                    // Column K: service_fee (formula: rent_received * service_fee_percent)
                    // BLOCK PROPERTY: No service fee for block properties
                    // Use property-specific service fee rate, fallback to global default if not set
                    double svcFeeRate = isBlockProperty ? 0.0
                        : (lease.getServiceFeePercentage() != null
                            ? lease.getServiceFeePercentage().doubleValue() / 100.0  // Property stores as percentage (e.g., 5), convert to decimal
                            : commissionConfig.getServiceFeePercent().doubleValue());
                    Cell svcFeeCell = row.createCell(col++);
                    svcFeeCell.setCellFormula(String.format("H%d * %.4f", rowNum + 1, svcFeeRate));
                    svcFeeCell.setCellStyle(currencyStyle);

                    // Column L: total_commission (formula: ABS(mgmt + svc))
                    Cell totalCommCell = row.createCell(col++);
                    totalCommCell.setCellFormula(String.format("ABS(J%d + K%d)", rowNum + 1, rowNum + 1));
                    totalCommCell.setCellStyle(currencyStyle);

                    // Column M: total_expenses (SUMIFS to EXPENSES sheet)
                    // ✅ NEW: Flat EXPENSES sheet - sum expense_amount where expense_date is in this month
                    Cell expensesCell = row.createCell(col++);
                    expensesCell.setCellFormula(String.format(
                        "SUMIFS(EXPENSES!E:E, EXPENSES!B:B, A%d, EXPENSES!D:D, \">=\"&F%d, EXPENSES!D:D, \"<\"&EOMONTH(F%d,0)+1)",
                        rowNum + 1, rowNum + 1, rowNum + 1
                    ));
                    expensesCell.setCellStyle(currencyStyle);

                    // Column N: net_to_owner (formula: received - commission - expenses)
                    Cell netToOwnerCell = row.createCell(col++);
                    netToOwnerCell.setCellFormula(String.format("H%d - L%d - M%d", rowNum + 1, rowNum + 1, rowNum + 1));
                    netToOwnerCell.setCellStyle(currencyStyle);

                    // Column O: opening_balance (pre-calculated from database)
                    // MEMORY FIX: Calculate in service layer instead of using SUMIFS on historical data
                    Cell openingBalanceCell = row.createCell(col++);
                    if (isFirstRowForLease) {
                        // Opening balance calculated from database for first row
                        // Pass frequencyMonths and leaseEndDate for proration if lease ends mid-cycle
                        java.math.BigDecimal openingBalance = dataExtractService.calculateTenantOpeningBalance(
                            lease.getLeaseId(), leaseStart, monthStart, lease.getMonthlyRent(),
                            lease.getFrequencyMonths(), lease.getEndDate());
                        openingBalanceCell.setCellValue(openingBalance != null ? openingBalance.doubleValue() : 0);
                    } else {
                        // For subsequent months, opening balance is 0 (cumulative handles the carry-forward)
                        openingBalanceCell.setCellValue(0);
                    }
                    openingBalanceCell.setCellStyle(currencyStyle);

                    // Column P: cumulative_arrears (formula: opening_balance + running sum of arrears)
                    Cell cumArrearsCell = row.createCell(col++);
                    if (isFirstRowForLease) {
                        cumArrearsCell.setCellFormula(String.format("O%d + I%d", rowNum + 1, rowNum + 1));
                    } else {
                        cumArrearsCell.setCellFormula(String.format("P%d + I%d", rowNum, rowNum + 1));
                    }
                    cumArrearsCell.setCellStyle(currencyStyle);

                    // ===== NEW: Block Property and Property Account Balance Columns =====

                    // Column Q: block_name (for grouping)
                    Cell blockNameCell = row.createCell(col++);
                    if (lease.getBlockName() != null) {
                        blockNameCell.setCellValue(lease.getBlockName());
                    } else {
                        blockNameCell.setCellValue(""); // Not part of a block
                    }

                    // Columns R-U: Property account balance tracking (only for properties in blocks)
                    if (lease.belongsToBlock() && lease.getBlockName() != null) {
                        // Column R: property_account_opening
                        Cell propAcctOpeningCell = row.createCell(col++);
                        propAcctOpeningCell.setCellFormula(String.format(
                            "IFERROR(SUMIFS(PROPERTY_ACCOUNT!D:D, PROPERTY_ACCOUNT!A:A, Q%d, PROPERTY_ACCOUNT!B:B, \"opening\"), 0)",
                            rowNum + 1
                        ));
                        propAcctOpeningCell.setCellStyle(currencyStyle);

                        // Column S: property_account_in
                        Cell propAcctInCell = row.createCell(col++);
                        propAcctInCell.setCellFormula(String.format(
                            "IFERROR(SUMIFS(PROPERTY_ACCOUNT!D:D, PROPERTY_ACCOUNT!A:A, Q%d, PROPERTY_ACCOUNT!B:B, \"in\"), 0)",
                            rowNum + 1
                        ));
                        propAcctInCell.setCellStyle(currencyStyle);

                        // Column T: property_account_out
                        Cell propAcctOutCell = row.createCell(col++);
                        propAcctOutCell.setCellFormula(String.format(
                            "IFERROR(SUMIFS(PROPERTY_ACCOUNT!D:D, PROPERTY_ACCOUNT!A:A, Q%d, PROPERTY_ACCOUNT!B:B, \"out\"), 0)",
                            rowNum + 1
                        ));
                        propAcctOutCell.setCellStyle(currencyStyle);

                        // Column U: property_account_closing (opening + in - out)
                        Cell propAcctClosingCell = row.createCell(col++);
                        propAcctClosingCell.setCellFormula(String.format("R%d + S%d - T%d", rowNum + 1, rowNum + 1, rowNum + 1));
                        propAcctClosingCell.setCellStyle(currencyStyle);
                    } else {
                        // Not a block property - leave cells empty
                        row.createCell(col++).setCellValue(""); // property_account_opening
                        row.createCell(col++).setCellValue(""); // property_account_in
                        row.createCell(col++).setCellValue(""); // property_account_out
                        row.createCell(col++).setCellValue(""); // property_account_closing
                    }

                    rowNum++;
                    isFirstRowForLease = false;
                }

                currentMonth = currentMonth.plusMonths(1);
            }
        }

        // Apply fixed column widths (autoSizeColumn causes OutOfMemoryError on large sheets)
        applyFixedColumnWidths(sheet, headers.length);

        log.info("MONTHLY_STATEMENT sheet created with {} rows", rowNum - 1);
    }

    /**
     * Create a monthly statement sheet for a specific period
     * Used when generating separate sheets for each month
     */
    private void createMonthlyStatementSheetForPeriod(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                                      site.easy.to.build.crm.util.RentCyclePeriodCalculator.RentCyclePeriod period,
                                                      WorkbookStyles styles) {
        String sheetName = sanitizeSheetName(period.getSheetName());
        log.info("Creating monthly statement sheet: {}", sheetName);

        // Call the existing method with the period's date range
        createMonthlyStatementSheetWithName(workbook, leaseMaster, period.getStartDate(), period.getEndDate(), sheetName, styles);
    }

    /**
     * Create MONTHLY_STATEMENT sheet with a custom name
     */
    private void createMonthlyStatementSheetWithName(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                                     LocalDate startDate, LocalDate endDate, String sheetName,
                                                     WorkbookStyles styles) {
        log.info("Creating {} sheet with formulas", sheetName);

        Sheet sheet = workbook.createSheet(sheetName);

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_reference", "property_name", "customer_name", "lease_start_date", "rent_due_day", "month",
            "rent_due", "rent_received", "arrears", "management_fee", "service_fee",
            "total_commission", "total_expenses", "net_to_owner", "opening_balance", "cumulative_arrears",
            // NEW: Block property and property account balance columns
            "block_name", "property_account_opening", "property_account_in", "property_account_out", "property_account_closing"
        };

        // Use shared styles (memory optimization)
        CellStyle headerStyle = styles.headerStyle;
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Use shared styles
        CellStyle dateStyle = styles.dateStyle;
        CellStyle currencyStyle = styles.currencyStyle;

        int rowNum = 1;

        // Generate one row per lease per month that has started
        // Show active leases AND ended leases (with £0 rent due), but NOT future leases that haven't started
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();
            LocalDate leaseEnd = lease.getEndDate();

            // Generate monthly rows for STATEMENT PERIOD only
            YearMonth currentMonth = YearMonth.from(startDate);
            YearMonth endMonth = YearMonth.from(endDate);

            while (!currentMonth.isAfter(endMonth)) {
                LocalDate monthStart = currentMonth.atDay(1);
                LocalDate monthEnd = currentMonth.atEndOfMonth();

                // Skip future leases that haven't started yet (lease start is after month end)
                if (leaseStart != null && leaseStart.isAfter(monthEnd)) {
                    currentMonth = currentMonth.plusMonths(1);
                    continue; // Don't show leases that haven't started yet
                }

                // Check if lease is active in this month (used for rent calculation)
                boolean leaseActiveInMonth = (leaseEnd == null || !monthEnd.isBefore(leaseStart))
                                   && !monthStart.isAfter(leaseEnd != null ? leaseEnd : monthEnd);

                // Create row for leases that have started (active or ended, but not future)
                Row row = sheet.createRow(rowNum);
                int col = 0;

                // Basic lease info
                row.createCell(col++).setCellValue(lease.getLeaseReference());
                row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");
                row.createCell(col++).setCellValue(lease.getCustomerName() != null ? lease.getCustomerName() : "");

                // Lease start date
                Cell startDateCell = row.createCell(col++);
                if (leaseStart != null) {
                    startDateCell.setCellValue(leaseStart);
                    startDateCell.setCellStyle(dateStyle);
                }

                // Rent due day
                row.createCell(col++).setCellValue(lease.getPaymentDay() != null ? lease.getPaymentDay() : 0);

                // Month
                Cell monthCell = row.createCell(col++);
                monthCell.setCellValue(monthStart);
                monthCell.setCellStyle(dateStyle);

                // Column G: rent_due - If lease not active in month, show £0; otherwise use SUMIFS formula
                Cell rentDueCell = row.createCell(col++);
                if (leaseActiveInMonth) {
                    rentDueCell.setCellFormula(String.format(
                        "SUMIFS(RENT_DUE!H:H, RENT_DUE!A:A, %d, RENT_DUE!D:D, F%d)",
                        lease.getLeaseId(), rowNum + 1
                    ));
                } else {
                    // Lease not active - rent due is £0
                    rentDueCell.setCellValue(0);
                }
                rentDueCell.setCellStyle(currencyStyle);

                // Column H: rent_received (SUMIFS to RENT_RECEIVED sheet) - payments can come in even for inactive leases
                Cell rentReceivedCell = row.createCell(col++);
                rentReceivedCell.setCellFormula(String.format(
                    "SUMIFS(RENT_RECEIVED!O:O, RENT_RECEIVED!A:A, %d, RENT_RECEIVED!E:E, F%d)",
                    lease.getLeaseId(), rowNum + 1
                ));
                rentReceivedCell.setCellStyle(currencyStyle);

                // Column I: arrears (formula: due - received)
                Cell arrearsCell = row.createCell(col++);
                arrearsCell.setCellFormula(String.format("G%d - H%d", rowNum + 1, rowNum + 1));
                arrearsCell.setCellStyle(currencyStyle);

                // Column J: management_fee (formula: rent_received * management_fee_percent)
                // BLOCK PROPERTY: No commission for block properties (they're communal funds, not rent)
                boolean isBlockProperty = Boolean.TRUE.equals(lease.getIsBlockProperty());
                double mgmtFeeRate = isBlockProperty ? 0.0 : commissionConfig.getManagementFeePercent().doubleValue();
                Cell managementFeeCell = row.createCell(col++);
                managementFeeCell.setCellFormula(String.format("H%d * %.4f", rowNum + 1, mgmtFeeRate));
                managementFeeCell.setCellStyle(currencyStyle);

                // Column K: service_fee (formula: rent_received * service_fee_percent)
                // BLOCK PROPERTY: No service fee for block properties
                double svcFeeRate = isBlockProperty ? 0.0 : commissionConfig.getServiceFeePercent().doubleValue();
                Cell serviceFeeCell = row.createCell(col++);
                serviceFeeCell.setCellFormula(String.format("H%d * %.4f", rowNum + 1, svcFeeRate));
                serviceFeeCell.setCellStyle(currencyStyle);

                // Column L: total_commission (formula: mgmt + svc)
                Cell totalCommissionCell = row.createCell(col++);
                totalCommissionCell.setCellFormula(String.format("J%d + K%d", rowNum + 1, rowNum + 1));
                totalCommissionCell.setCellStyle(currencyStyle);

                // Column M: total_expenses (SUMIFS to EXPENSES sheet)
                // ✅ NEW: Flat EXPENSES sheet - sum expenses where expense_date falls within the period
                Cell expensesCell = row.createCell(col++);
                expensesCell.setCellFormula(String.format(
                    "SUMIFS(EXPENSES!E:E, EXPENSES!B:B, A%d, EXPENSES!D:D, \">=\"&DATE(%d,%d,%d), EXPENSES!D:D, \"<=\"&DATE(%d,%d,%d))",
                    rowNum + 1,
                    startDate.getYear(), startDate.getMonthValue(), startDate.getDayOfMonth(),
                    endDate.getYear(), endDate.getMonthValue(), endDate.getDayOfMonth()
                ));
                expensesCell.setCellStyle(currencyStyle);

                // Column N: net_to_owner (formula: received - commission - expenses)
                Cell netCell = row.createCell(col++);
                netCell.setCellFormula(String.format("H%d - L%d - M%d", rowNum + 1, rowNum + 1, rowNum + 1));
                netCell.setCellStyle(currencyStyle);

                // Column O: opening_balance (pre-calculated from database)
                // MEMORY FIX: Calculate in service layer instead of using SUMIFS on historical data
                // Pass frequencyMonths and leaseEndDate for proration if lease ends mid-cycle
                Cell openingBalanceCell = row.createCell(col++);
                java.math.BigDecimal openingBalance = dataExtractService.calculateTenantOpeningBalance(
                    lease.getLeaseId(), leaseStart, monthStart, lease.getMonthlyRent(),
                    lease.getFrequencyMonths(), lease.getEndDate());
                openingBalanceCell.setCellValue(openingBalance != null ? openingBalance.doubleValue() : 0);
                openingBalanceCell.setCellStyle(currencyStyle);

                // Column P: cumulative_arrears (opening_balance + current month arrears)
                Cell cumulativeArrearsCell = row.createCell(col++);
                cumulativeArrearsCell.setCellFormula(String.format("O%d + I%d", rowNum + 1, rowNum + 1));
                cumulativeArrearsCell.setCellStyle(currencyStyle);

                // ===== NEW: Block Property and Property Account Balance Columns =====

                // Column Q: block_name (for grouping)
                Cell blockNameCell = row.createCell(col++);
                if (lease.getBlockName() != null) {
                    blockNameCell.setCellValue(lease.getBlockName());
                } else {
                    blockNameCell.setCellValue(""); // Not part of a block
                }

                // Columns R-U: Property account balance tracking (only for properties in blocks)
                // These show the flow of money through the block's property account
                if (lease.belongsToBlock() && lease.getBlockName() != null) {
                    // Get property account data from unified allocations
                    // Note: In a real implementation, you would calculate these from the database
                    // For now, we use the property's current account_balance and calculate movement

                    // Column R: property_account_opening (balance at start of period)
                    // This is calculated as: all historical inflows - all historical outflows before startDate
                    Cell propAcctOpeningCell = row.createCell(col++);
                    // Use a placeholder formula referencing a PROPERTY_ACCOUNT_SUMMARY sheet
                    propAcctOpeningCell.setCellFormula(String.format(
                        "IFERROR(SUMIFS(PROPERTY_ACCOUNT!D:D, PROPERTY_ACCOUNT!A:A, Q%d, PROPERTY_ACCOUNT!B:B, \"opening\"), 0)",
                        rowNum + 1
                    ));
                    propAcctOpeningCell.setCellStyle(currencyStyle);

                    // Column S: property_account_in (disbursements into property account this period)
                    Cell propAcctInCell = row.createCell(col++);
                    propAcctInCell.setCellFormula(String.format(
                        "IFERROR(SUMIFS(PROPERTY_ACCOUNT!D:D, PROPERTY_ACCOUNT!A:A, Q%d, PROPERTY_ACCOUNT!B:B, \"in\"), 0)",
                        rowNum + 1
                    ));
                    propAcctInCell.setCellStyle(currencyStyle);

                    // Column T: property_account_out (expenses paid from property account this period)
                    Cell propAcctOutCell = row.createCell(col++);
                    propAcctOutCell.setCellFormula(String.format(
                        "IFERROR(SUMIFS(PROPERTY_ACCOUNT!D:D, PROPERTY_ACCOUNT!A:A, Q%d, PROPERTY_ACCOUNT!B:B, \"out\"), 0)",
                        rowNum + 1
                    ));
                    propAcctOutCell.setCellStyle(currencyStyle);

                    // Column U: property_account_closing (opening + in - out)
                    Cell propAcctClosingCell = row.createCell(col++);
                    propAcctClosingCell.setCellFormula(String.format("R%d + S%d - T%d", rowNum + 1, rowNum + 1, rowNum + 1));
                    propAcctClosingCell.setCellStyle(currencyStyle);
                } else {
                    // Not a block property - leave cells empty
                    row.createCell(col++).setCellValue(""); // property_account_opening
                    row.createCell(col++).setCellValue(""); // property_account_in
                    row.createCell(col++).setCellValue(""); // property_account_out
                    row.createCell(col++).setCellValue(""); // property_account_closing
                }

                rowNum++;

                currentMonth = currentMonth.plusMonths(1);
            }
        }

        // Apply fixed column widths (autoSizeColumn causes OutOfMemoryError on large sheets)
        applyFixedColumnWidths(sheet, headers.length);

        log.info("{} sheet created with {} rows", sheetName, rowNum - 1);
    }

    /**
     * Sanitize sheet name for Excel compatibility
     */
    private String sanitizeSheetName(String name) {
        // Excel sheet names can't contain: \ / ? * [ ]
        // Max length is 31 characters
        String sanitized = name.replaceAll("[\\\\/:*?\\[\\]]", "-");
        if (sanitized.length() > 31) {
            sanitized = sanitized.substring(0, 31);
        }
        return sanitized;
    }

    // ============================================================================
    // Custom Period Sheet Creation Methods
    // ============================================================================

    /**
     * Create RENT_DUE sheet with LEASE-BASED periods (anniversary dates)
     * Each lease gets its own rent cycle based on when the lease started.
     * E.g., a lease starting March 6th has periods: Mar 6 - Apr 5, Apr 6 - May 5, etc.
     * Uses actual month days for accurate pro-rating.
     */
    private void createRentDueSheetWithCustomPeriods(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                                    LocalDate startDate, LocalDate endDate, int periodStartDay,
                                                    WorkbookStyles styles) {
        log.info("Creating RENT_DUE sheet with LEASE-BASED periods (ignoring periodStartDay: {})", periodStartDay);

        Sheet sheet = workbook.createSheet("RENT_DUE");

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_id", "lease_reference", "property_name", "period_start", "period_end",
            "period_days", "lease_start", "lease_end", "monthly_rent", "rent_due_day",
            "lease_days_in_period", "prorated_rent_due",
            "management_fee", "service_fee", "total_commission", "net_to_owner"
        };

        // Use shared styles (memory optimization)
        CellStyle headerStyle = styles.headerStyle;
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Use shared styles
        CellStyle dateStyle = styles.dateStyle;
        CellStyle currencyStyle = styles.currencyStyle;

        int rowNum = 1;

        // Generate rows for each lease using LEASE-BASED periods (not fixed billing periods)
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();
            LocalDate leaseEnd = lease.getEndDate();
            int rentDueDay = leaseStart != null ? leaseStart.getDayOfMonth() : 1;

            // Skip leases without start date
            if (leaseStart == null) {
                log.warn("Skipping lease {} - no start date", lease.getLeaseReference());
                continue;
            }

            // Generate periods based on THIS LEASE's start date (anniversary-based)
            // SXSSF: Include ALL periods from lease start for Excel formula-based opening balance calculation
            List<CustomPeriod> leasePeriods = generateLeaseBasedPeriods(leaseStart, leaseEnd, startDate, endDate, true);

            // Get frequency for cycle-based calculations
            int frequencyMonths = lease.getFrequencyMonths() != null ? lease.getFrequencyMonths() : 1;

            for (CustomPeriod period : leasePeriods) {
                Row row = sheet.createRow(rowNum);
                int col = 0;

                // Determine if this period contains a billing cycle start date
                // RENT IN ADVANCE: Full rent is due when a cycle starts (lease start + every N months)
                // Use Java calculation to properly detect cycle starts based on actual lease anniversary dates
                boolean isCycleStart;
                if (frequencyMonths <= 1) {
                    // Monthly billing - every period is a cycle start
                    isCycleStart = true;
                } else {
                    // Multi-month billing - check if a cycle start date falls within this period
                    long cyclesInPeriod = dataExtractService.countCycleStartDatesInPeriod(
                        leaseStart, period.periodStart, period.periodEnd, frequencyMonths);
                    isCycleStart = cyclesInPeriod > 0;
                }

                // A: lease_id
                row.createCell(col++).setCellValue(lease.getLeaseId());

                // B: lease_reference
                row.createCell(col++).setCellValue(lease.getLeaseReference());

                // C: property_name
                row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");

                // D: period_start (based on lease anniversary)
                Cell periodStartCell = row.createCell(col++);
                periodStartCell.setCellValue(period.periodStart);
                periodStartCell.setCellStyle(dateStyle);

                // E: period_end (day before next anniversary, or lease end if earlier)
                Cell periodEndCell = row.createCell(col++);
                periodEndCell.setCellValue(period.periodEnd);
                periodEndCell.setCellStyle(dateStyle);

                // F: period_days
                row.createCell(col++).setCellValue(period.periodDays);

                // G: lease_start
                Cell leaseStartCell = row.createCell(col++);
                leaseStartCell.setCellValue(leaseStart);
                leaseStartCell.setCellStyle(dateStyle);

                // H: lease_end
                Cell leaseEndCell = row.createCell(col++);
                if (leaseEnd != null) {
                    leaseEndCell.setCellValue(leaseEnd);
                    leaseEndCell.setCellStyle(dateStyle);
                }

                // I: monthly_rent (actually cycle_rent for multi-month frequencies)
                Cell monthlyRentCell = row.createCell(col++);
                if (lease.getMonthlyRent() != null) {
                    monthlyRentCell.setCellValue(lease.getMonthlyRent().doubleValue());
                    monthlyRentCell.setCellStyle(currencyStyle);
                }

                // J: rent_due_day (from lease start date)
                row.createCell(col++).setCellValue(rentDueDay);

                // K: lease_days_in_period
                Cell leaseDaysCell = row.createCell(col++);
                leaseDaysCell.setCellValue(period.periodDays);

                // L: prorated_rent_due - handles cycle-based billing for multi-month frequencies
                // For multi-month (freq > 1): full rent on cycle start, £0 on intermediate months
                // For monthly (freq = 1): prorate if partial period
                Cell proratedRentCell = row.createCell(col++);
                if (!isCycleStart) {
                    // Not a cycle start - no rent due this period
                    proratedRentCell.setCellValue(0);
                } else {
                    // Cycle start - check if period is prorated (less than ~28 days)
                    proratedRentCell.setCellFormula(String.format(
                        "IF(F%d < 28, ROUND(F%d / DAY(EOMONTH(D%d, 0)) * I%d, 2), I%d)",
                        rowNum + 1, // period_days
                        rowNum + 1, // period_days for proration
                        rowNum + 1, // period_start for month length
                        rowNum + 1, // monthly_rent
                        rowNum + 1  // monthly_rent (full)
                    ));
                }
                proratedRentCell.setCellStyle(currencyStyle);

                // M: management_fee (from config)
                // BLOCK PROPERTY: No commission for block properties (they're communal funds, not rent)
                boolean isBlockProperty = Boolean.TRUE.equals(lease.getIsBlockProperty());
                double mgmtFeeRate = isBlockProperty ? 0.0 : commissionConfig.getManagementFeePercent().doubleValue();
                Cell mgmtFeeCell = row.createCell(col++);
                mgmtFeeCell.setCellFormula(String.format("L%d * %.4f", rowNum + 1, mgmtFeeRate));
                mgmtFeeCell.setCellStyle(currencyStyle);

                // N: service_fee (from config)
                // BLOCK PROPERTY: No service fee for block properties
                double svcFeeRate = isBlockProperty ? 0.0 : commissionConfig.getServiceFeePercent().doubleValue();
                Cell svcFeeCell = row.createCell(col++);
                svcFeeCell.setCellFormula(String.format("L%d * %.4f", rowNum + 1, svcFeeRate));
                svcFeeCell.setCellStyle(currencyStyle);

                // O: total_commission (15%)
                Cell totalCommCell = row.createCell(col++);
                totalCommCell.setCellFormula(String.format("M%d + N%d", rowNum + 1, rowNum + 1));
                totalCommCell.setCellStyle(currencyStyle);

                // P: net_to_owner
                Cell netToOwnerCell = row.createCell(col++);
                netToOwnerCell.setCellFormula(String.format("L%d - O%d", rowNum + 1, rowNum + 1));
                netToOwnerCell.setCellStyle(currencyStyle);

                rowNum++;
            }
        }

        // Apply fixed column widths (autoSizeColumn causes OutOfMemoryError on large sheets)
        applyFixedColumnWidths(sheet, headers.length);

        log.info("RENT_DUE sheet created with {} rows (lease-based periods)", rowNum - 1);
    }

    /**
     * Create RENT_RECEIVED sheet with custom periods and payment breakdown
     * Shows individual payment dates and amounts (up to 4 payments per period)
     */
    private void createRentReceivedSheetWithCustomPeriods(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                                         LocalDate startDate, LocalDate endDate, int periodStartDay,
                                                         WorkbookStyles styles) {
        log.info("Creating RENT_RECEIVED sheet with custom periods and payment breakdown");

        Sheet sheet = workbook.createSheet("RENT_RECEIVED");

        // Header row with payment breakdown columns
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_id", "lease_reference", "property_name", "rent_due_date",
            "period_start", "period_end",
            "payment_1_date", "payment_1_amount",
            "payment_2_date", "payment_2_amount",
            "payment_3_date", "payment_3_amount",
            "payment_4_date", "payment_4_amount",
            "total_received"
        };

        // Use shared styles (memory optimization)
        CellStyle headerStyle = styles.headerStyle;
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Use shared styles
        CellStyle dateStyle = styles.dateStyle;
        CellStyle currencyStyle = styles.currencyStyle;

        int rowNum = 1;

        // Generate rows for each lease using LEASE-BASED periods (like RENT_DUE)
        // Include ALL periods from lease start for Excel formula-based opening balance calculation
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();
            LocalDate leaseEnd = lease.getEndDate();

            // Skip leases without start date
            if (leaseStart == null) {
                log.warn("Skipping lease {} - no start date", lease.getLeaseReference());
                continue;
            }

            // Generate periods based on THIS LEASE's start date (anniversary-based)
            // Include ALL periods from lease start for accurate opening balance calculation
            List<CustomPeriod> leasePeriods = generateLeaseBasedPeriods(leaseStart, leaseEnd, startDate, endDate, true);

            // Check for pre-lease payments (payments made before lease start date)
            // These need to be captured for accurate opening balance calculation
            LocalDate preLeaseStart = leaseStart.minusYears(1); // Look back 1 year for pre-lease payments
            List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> preLeasePayments =
                dataExtractService.extractRentReceivedDetails(lease.getLeaseId(), preLeaseStart, leaseStart.minusDays(1));

            if (!preLeasePayments.isEmpty()) {
                // Add a "pre-lease" row to capture payments before lease started
                Row preLeaseRow = sheet.createRow(rowNum);
                int col = 0;

                preLeaseRow.createCell(col++).setCellValue(lease.getLeaseId());
                preLeaseRow.createCell(col++).setCellValue(lease.getLeaseReference());
                preLeaseRow.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");

                Cell preDueDateCell = preLeaseRow.createCell(col++);
                preDueDateCell.setCellValue(preLeaseStart);
                preDueDateCell.setCellStyle(dateStyle);

                Cell prePeriodStartCell = preLeaseRow.createCell(col++);
                prePeriodStartCell.setCellValue(preLeaseStart);
                prePeriodStartCell.setCellStyle(dateStyle);

                Cell prePeriodEndCell = preLeaseRow.createCell(col++);
                prePeriodEndCell.setCellValue(leaseStart.minusDays(1));
                prePeriodEndCell.setCellStyle(dateStyle);

                // Payment breakdown for pre-lease payments
                BigDecimal preLeaseTotal = BigDecimal.ZERO;
                for (int i = 0; i < 4; i++) {
                    if (i < preLeasePayments.size()) {
                        site.easy.to.build.crm.dto.statement.PaymentDetailDTO payment = preLeasePayments.get(i);
                        Cell paymentDateCell = preLeaseRow.createCell(col++);
                        paymentDateCell.setCellValue(payment.getPaymentDate());
                        paymentDateCell.setCellStyle(dateStyle);
                        Cell paymentAmountCell = preLeaseRow.createCell(col++);
                        paymentAmountCell.setCellValue(payment.getAmount().doubleValue());
                        paymentAmountCell.setCellStyle(currencyStyle);
                        preLeaseTotal = preLeaseTotal.add(payment.getAmount());
                    } else {
                        preLeaseRow.createCell(col++);
                        preLeaseRow.createCell(col++);
                    }
                }

                Cell preTotalCell = preLeaseRow.createCell(col++);
                preTotalCell.setCellValue(preLeaseTotal.doubleValue());
                preTotalCell.setCellStyle(currencyStyle);

                rowNum++;
                log.info("Added pre-lease payments row for {} with {} payments totaling {}",
                    lease.getLeaseReference(), preLeasePayments.size(), preLeaseTotal);
            }

            for (CustomPeriod period : leasePeriods) {

                // Create row for leases that have started (payments can come in even for ended leases)
                Row row = sheet.createRow(rowNum);
                int col = 0;

                // Get payment details for this lease/period
                // Cap the query end date at the statement end date to exclude payments made after statement period
                LocalDate queryEndDate = period.periodEnd.isAfter(endDate) ? endDate : period.periodEnd;
                List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> payments =
                    dataExtractService.extractRentReceivedDetails(lease.getLeaseId(), period.periodStart, queryEndDate);

                // A: lease_id
                row.createCell(col++).setCellValue(lease.getLeaseId());

                // B: lease_reference
                row.createCell(col++).setCellValue(lease.getLeaseReference());

                // C: property_name
                row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");

                // D: rent_due_date (period start date)
                Cell dueDateCell = row.createCell(col++);
                dueDateCell.setCellValue(period.periodStart);
                dueDateCell.setCellStyle(dateStyle);

                // E: period_start
                Cell periodStartCell = row.createCell(col++);
                periodStartCell.setCellValue(period.periodStart);
                periodStartCell.setCellStyle(dateStyle);

                // F: period_end
                Cell periodEndCell = row.createCell(col++);
                periodEndCell.setCellValue(period.periodEnd);
                periodEndCell.setCellStyle(dateStyle);

                // Columns G-N: Payment breakdown (up to 4 payments)
                BigDecimal total = BigDecimal.ZERO;
                for (int i = 0; i < 4; i++) {
                    if (i < payments.size()) {
                        site.easy.to.build.crm.dto.statement.PaymentDetailDTO payment = payments.get(i);

                        // Payment date
                        Cell paymentDateCell = row.createCell(col++);
                        paymentDateCell.setCellValue(payment.getPaymentDate());
                        paymentDateCell.setCellStyle(dateStyle);

                        // Payment amount
                        Cell paymentAmountCell = row.createCell(col++);
                        paymentAmountCell.setCellValue(payment.getAmount().doubleValue());
                        paymentAmountCell.setCellStyle(currencyStyle);

                        total = total.add(payment.getAmount());
                    } else {
                        // Empty payment columns
                        row.createCell(col++); // date
                        row.createCell(col++); // amount
                    }
                }

                // Column O: total_received
                Cell totalReceivedCell = row.createCell(col++);
                totalReceivedCell.setCellValue(total.doubleValue());
                totalReceivedCell.setCellStyle(currencyStyle);

                rowNum++;
            }

            // Check for post-lease payments (payments made after lease end date)
            // These need to be captured for accurate balance calculation (e.g., late payments)
            // Only include payments up to the statement end date
            if (leaseEnd != null && leaseEnd.isBefore(endDate)) {
                // Cap post-lease query at statement end date
                LocalDate postLeaseQueryEnd = endDate;
                List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> postLeasePayments =
                    dataExtractService.extractRentReceivedDetails(lease.getLeaseId(), leaseEnd.plusDays(1), postLeaseQueryEnd);

                if (!postLeasePayments.isEmpty()) {
                    // Add a "post-lease" row to capture payments after lease ended
                    Row postLeaseRow = sheet.createRow(rowNum);
                    int col = 0;

                    postLeaseRow.createCell(col++).setCellValue(lease.getLeaseId());
                    postLeaseRow.createCell(col++).setCellValue(lease.getLeaseReference());
                    postLeaseRow.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");

                    Cell postDueDateCell = postLeaseRow.createCell(col++);
                    postDueDateCell.setCellValue(leaseEnd.plusDays(1));
                    postDueDateCell.setCellStyle(dateStyle);

                    Cell postPeriodStartCell = postLeaseRow.createCell(col++);
                    postPeriodStartCell.setCellValue(leaseEnd.plusDays(1));
                    postPeriodStartCell.setCellStyle(dateStyle);

                    Cell postPeriodEndCell = postLeaseRow.createCell(col++);
                    postPeriodEndCell.setCellValue(postLeaseQueryEnd);
                    postPeriodEndCell.setCellStyle(dateStyle);

                    // Payment breakdown for post-lease payments
                    BigDecimal postLeaseTotal = BigDecimal.ZERO;
                    for (int i = 0; i < 4; i++) {
                        if (i < postLeasePayments.size()) {
                            site.easy.to.build.crm.dto.statement.PaymentDetailDTO payment = postLeasePayments.get(i);
                            Cell paymentDateCell = postLeaseRow.createCell(col++);
                            paymentDateCell.setCellValue(payment.getPaymentDate());
                            paymentDateCell.setCellStyle(dateStyle);
                            Cell paymentAmountCell = postLeaseRow.createCell(col++);
                            paymentAmountCell.setCellValue(payment.getAmount().doubleValue());
                            paymentAmountCell.setCellStyle(currencyStyle);
                            postLeaseTotal = postLeaseTotal.add(payment.getAmount());
                        } else {
                            postLeaseRow.createCell(col++);
                            postLeaseRow.createCell(col++);
                        }
                    }

                    Cell postTotalCell = postLeaseRow.createCell(col++);
                    postTotalCell.setCellValue(postLeaseTotal.doubleValue());
                    postTotalCell.setCellStyle(currencyStyle);

                    rowNum++;
                    log.info("Added post-lease payments row for {} with {} payments totaling {}",
                        lease.getLeaseReference(), postLeasePayments.size(), postLeaseTotal);
                }
            }
        }

        // Apply fixed column widths (autoSizeColumn causes OutOfMemoryError on large sheets)
        applyFixedColumnWidths(sheet, headers.length);

        log.info("RENT_RECEIVED sheet created with {} rows (custom periods)", rowNum - 1);
    }

    /**
     * Create a monthly statement sheet for a specific custom period
     */
    /**
     * @deprecated Use version with WorkbookStyles and customerId for memory efficiency and reconciliation
     */
    private void createMonthlyStatementSheetForCustomPeriod(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                                           CustomPeriod period, String sheetName) {
        // Create styles on-demand for backward compatibility - no customerId for reconciliation
        createMonthlyStatementSheetForCustomPeriod(workbook, leaseMaster, period, sheetName, new WorkbookStyles(workbook), null);
    }

    /**
     * Create monthly statement sheet with shared styles (memory optimized version)
     * @param customerId Customer ID for period-based reconciliation (can be null for legacy calls)
     */
    private void createMonthlyStatementSheetForCustomPeriod(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                                           CustomPeriod period, String sheetName, WorkbookStyles styles,
                                                           Long customerId) {
        log.info("Creating monthly statement sheet: {}", sheetName);

        Sheet sheet = workbook.createSheet(sheetName);

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_reference", "property_name", "customer_name", "tenant_name", "lease_start_date", "rent_due_day",
            "rent_due", "rent_received", "rent_batch",  // Batch ref for income
            "opening_balance", "period_arrears", "closing_balance",
            "management_fee", "service_fee", "total_commission", "commission_batch",  // Batch ref for commission
            "total_expenses", "expenses_batch",  // Batch ref for expenses
            "net_to_owner",
            "block_name", "property_account_opening", "property_account_in", "property_account_out", "property_account_closing",
            // Reconciliation columns for payment tracking
            "allocated_amount", "variance", "allocation_status", "batch_id", "paid_date"
        };

        // Use shared styles instead of creating new ones
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.headerStyle);
        }

        // Use shared styles
        CellStyle dateStyle = styles.dateStyle;
        CellStyle currencyStyle = styles.currencyStyle;

        int rowNum = 1;

        // ===== PRE-FETCH ALLOCATION SUMMARIES FOR RECONCILIATION COLUMNS =====
        List<Long> propertyIds = leaseMaster.stream()
            .filter(l -> l.getPropertyId() != null)
            .map(LeaseMasterDTO::getPropertyId)
            .distinct()
            .collect(Collectors.toList());

        List<Long> invoiceIds = leaseMaster.stream()
            .filter(l -> l.getLeaseId() != null)
            .map(LeaseMasterDTO::getLeaseId)
            .distinct()
            .collect(Collectors.toList());

        // Use invoice-based allocation lookup for accurate per-lease allocation tracking
        Map<Long, LeaseAllocationSummaryDTO> allocationSummaries =
            dataExtractService.extractAllocationSummaryByInvoice(
                propertyIds, invoiceIds, period.periodStart, period.periodEnd);

        log.info("Pre-fetched allocation summaries for {} invoices", allocationSummaries.size());

        // ===== PRE-FETCH BATCH REFS BY INVOICE/LEASE AND TYPE =====
        // Uses invoice_id to ensure batch refs match the specific lease, not all leases on same property
        Map<Long, Map<String, String>> batchRefsByInvoiceAndType =
            dataExtractService.extractBatchRefsByPropertyAndType(
                propertyIds, invoiceIds, period.periodStart, period.periodEnd);

        log.info("Pre-fetched batch refs for {} invoices", batchRefsByInvoiceAndType.size());

        // Generate rows for each lease that has started by this period
        // Show active leases AND ended leases (with £0 rent due), but NOT future leases that haven't started
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();
            LocalDate leaseEnd = lease.getEndDate();

            // Skip future leases that haven't started yet (lease start is after period end)
            if (leaseStart != null && leaseStart.isAfter(period.periodEnd)) {
                continue; // Don't show leases that haven't started yet
            }

            // Check if lease is active during this period (for rent calculation)
            boolean leaseActiveInPeriod = (leaseStart == null || !leaseStart.isAfter(period.periodEnd))
                               && (leaseEnd == null || !leaseEnd.isBefore(period.periodStart));

            // Create row for leases that have started (active or ended, but not future)
            Row row = sheet.createRow(rowNum);
            int col = 0;

            // A: lease_reference
            row.createCell(col++).setCellValue(lease.getLeaseReference());

            // B: property_name
            row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");

            // C: customer_name
            row.createCell(col++).setCellValue(lease.getCustomerName() != null ? lease.getCustomerName() : "");

            // D: tenant_name
            row.createCell(col++).setCellValue(lease.getTenantName() != null ? lease.getTenantName() : "");

            // E: lease_start_date
            Cell startDateCell = row.createCell(col++);
            if (leaseStart != null) {
                startDateCell.setCellValue(leaseStart);
                startDateCell.setCellStyle(dateStyle);
            }

            // F: rent_due_day
            row.createCell(col++).setCellValue(lease.getPaymentDay() != null ? lease.getPaymentDay() : 0);

            // G: rent_due - Pull from RENT_DUE sheet's prorated_rent_due column (L)
            // Find the rent due period where period_start (D) falls within the billing period
            // This ensures we get exactly ONE rent due per billing period
            // RENT_DUE columns: B=lease_reference, D=period_start, L=prorated_rent_due
            Cell rentDueCell = row.createCell(col++);
            if (leaseActiveInPeriod) {
                // Use SUMPRODUCT to find rent due where:
                // - lease_reference matches
                // - period_start is >= billing_period_start AND <= billing_period_end
                // This captures the ONE rent due period that starts within this billing period
                rentDueCell.setCellFormula(String.format(
                    "IFERROR(SUMPRODUCT((RENT_DUE!$B$2:$B$1000=\"%s\")*(RENT_DUE!$D$2:$D$1000>=DATE(%d,%d,%d))*(RENT_DUE!$D$2:$D$1000<=DATE(%d,%d,%d))*RENT_DUE!$L$2:$L$1000), 0)",
                    lease.getLeaseReference(),
                    period.periodStart.getYear(), period.periodStart.getMonthValue(), period.periodStart.getDayOfMonth(),  // period_start >= billing_start
                    period.periodEnd.getYear(), period.periodEnd.getMonthValue(), period.periodEnd.getDayOfMonth()  // period_start <= billing_end
                ));
            } else {
                // Lease not active - rent due is £0
                rentDueCell.setCellValue(0);
            }
            rentDueCell.setCellStyle(currencyStyle);

            // H: rent_received (SUMIFS to RENT_RECEIVED sheet) - payments can come in even for inactive leases
            // Use date range to sum ALL payments within the period (flat structure: D=payment_date, E=amount)
            Cell rentReceivedCell = row.createCell(col++);
            rentReceivedCell.setCellFormula(String.format(
                "IFERROR(SUMPRODUCT((RENT_RECEIVED!$B$2:$B$10000=\"%s\")*(RENT_RECEIVED!$D$2:$D$10000>=DATE(%d,%d,%d))*(RENT_RECEIVED!$D$2:$D$10000<=DATE(%d,%d,%d))*RENT_RECEIVED!$E$2:$E$10000), 0)",
                lease.getLeaseReference(),
                period.periodStart.getYear(), period.periodStart.getMonthValue(), period.periodStart.getDayOfMonth(),
                period.periodEnd.getYear(), period.periodEnd.getMonthValue(), period.periodEnd.getDayOfMonth()
            ));
            rentReceivedCell.setCellStyle(currencyStyle);

            // I: rent_batch (batch references for income allocations - OWNER type)
            // Look up by lease ID (invoice_id), fallback to negative property_id for legacy allocations
            Cell rentBatchCell = row.createCell(col++);
            Map<String, String> leaseBatchRefs = null;
            if (lease.getLeaseId() != null) {
                leaseBatchRefs = batchRefsByInvoiceAndType.get(lease.getLeaseId());
            }
            if (leaseBatchRefs == null && lease.getPropertyId() != null) {
                // Fallback: check for legacy allocations stored under negative property_id
                leaseBatchRefs = batchRefsByInvoiceAndType.get(-lease.getPropertyId());
            }
            String rentBatchRef = leaseBatchRefs != null ? leaseBatchRefs.get("OWNER") : null;
            rentBatchCell.setCellValue(rentBatchRef != null ? rentBatchRef : "");

            // J: opening_balance (cumulative arrears BEFORE this period)
            // Excel formula: (Rent Due before period) - (Rent Received before period)
            // RENT_DUE columns: B=lease_reference, D=period_start, L=prorated_rent_due
            // RENT_RECEIVED columns (flat): B=lease_reference, D=payment_date, E=amount
            Cell openingBalanceCell = row.createCell(col++);
            openingBalanceCell.setCellFormula(String.format(
                "IFERROR(SUMIFS(RENT_DUE!$L$2:$L$10000, RENT_DUE!$B$2:$B$10000, \"%s\", RENT_DUE!$D$2:$D$10000, \"<\"&DATE(%d,%d,%d)), 0) - " +
                "IFERROR(SUMIFS(RENT_RECEIVED!$E$2:$E$10000, RENT_RECEIVED!$B$2:$B$10000, \"%s\", RENT_RECEIVED!$D$2:$D$10000, \"<\"&DATE(%d,%d,%d)), 0)",
                lease.getLeaseReference(),
                period.periodStart.getYear(), period.periodStart.getMonthValue(), period.periodStart.getDayOfMonth(),
                lease.getLeaseReference(),
                period.periodStart.getYear(), period.periodStart.getMonthValue(), period.periodStart.getDayOfMonth()
            ));
            openingBalanceCell.setCellStyle(currencyStyle);

            // J: period_arrears (this period only: rent_due - rent_received)
            Cell periodArrearsCell = row.createCell(col++);
            periodArrearsCell.setCellFormula(String.format("G%d - H%d", rowNum + 1, rowNum + 1));
            periodArrearsCell.setCellStyle(currencyStyle);

            // L: closing_balance (opening + period arrears = cumulative position at end of period)
            // Column J=opening_balance, K=period_arrears
            Cell closingBalanceCell = row.createCell(col++);
            closingBalanceCell.setCellFormula(String.format("J%d + K%d", rowNum + 1, rowNum + 1));
            closingBalanceCell.setCellStyle(currencyStyle);

            // L: management_fee (formula: rent_received * management_fee_percent) - H is rent_received
            // BLOCK PROPERTY: No commission for block properties (they're communal funds, not rent)
            // Use property-specific commission rate, fallback to global default if not set
            boolean isBlockProperty = Boolean.TRUE.equals(lease.getIsBlockProperty());
            double mgmtFeeRate = isBlockProperty ? 0.0
                : (lease.getCommissionPercentage() != null
                    ? lease.getCommissionPercentage().doubleValue() / 100.0  // Property stores as percentage (e.g., 10), convert to decimal (0.10)
                    : commissionConfig.getManagementFeePercent().doubleValue());
            Cell mgmtFeeCell = row.createCell(col++);
            mgmtFeeCell.setCellFormula(String.format("H%d * %.4f", rowNum + 1, mgmtFeeRate));
            mgmtFeeCell.setCellStyle(currencyStyle);

            // M: service_fee (formula: rent_received * service_fee_percent) - H is rent_received
            // BLOCK PROPERTY: No service fee for block properties
            // Use property-specific service fee rate, fallback to global default if not set
            double svcFeeRate = isBlockProperty ? 0.0
                : (lease.getServiceFeePercentage() != null
                    ? lease.getServiceFeePercentage().doubleValue() / 100.0  // Property stores as percentage (e.g., 5), convert to decimal (0.05)
                    : commissionConfig.getServiceFeePercent().doubleValue());
            Cell svcFeeCell = row.createCell(col++);
            svcFeeCell.setCellFormula(String.format("H%d * %.4f", rowNum + 1, svcFeeRate));
            svcFeeCell.setCellStyle(currencyStyle);

            // O: total_commission (formula: ABS(mgmt + svc)) - M is mgmt_fee, N is svc_fee
            Cell totalCommCell = row.createCell(col++);
            totalCommCell.setCellFormula(String.format("ABS(M%d + N%d)", rowNum + 1, rowNum + 1));
            totalCommCell.setCellStyle(currencyStyle);

            // P: commission_batch (batch references for commission allocations)
            Cell commissionBatchCell = row.createCell(col++);
            String commissionBatchRef = leaseBatchRefs != null ? leaseBatchRefs.get("COMMISSION") : null;
            commissionBatchCell.setCellValue(commissionBatchRef != null ? commissionBatchRef : "");

            // Q: total_expenses (SUMIFS to EXPENSES sheet)
            // ✅ NEW: Flat EXPENSES sheet - sum expenses where expense_date falls within the period
            Cell expensesCell = row.createCell(col++);
            expensesCell.setCellFormula(String.format(
                "SUMIFS(EXPENSES!E:E, EXPENSES!B:B, \"%s\", EXPENSES!D:D, \">=\"&DATE(%d,%d,%d), EXPENSES!D:D, \"<=\"&DATE(%d,%d,%d))",
                lease.getLeaseReference(),
                period.periodStart.getYear(), period.periodStart.getMonthValue(), period.periodStart.getDayOfMonth(),
                period.periodEnd.getYear(), period.periodEnd.getMonthValue(), period.periodEnd.getDayOfMonth()
            ));
            expensesCell.setCellStyle(currencyStyle);

            // R: expenses_batch (batch references for expense allocations)
            Cell expensesBatchCell = row.createCell(col++);
            String expensesBatchRef = leaseBatchRefs != null ? leaseBatchRefs.get("EXPENSE") : null;
            expensesBatchCell.setCellValue(expensesBatchRef != null ? expensesBatchRef : "");

            // S: net_to_owner (formula: rent_received - total_commission - expenses) - H is rent_received, O is commission, Q is expenses
            Cell netToOwnerCell = row.createCell(col++);
            netToOwnerCell.setCellFormula(String.format("H%d - O%d - Q%d", rowNum + 1, rowNum + 1, rowNum + 1));
            netToOwnerCell.setCellStyle(currencyStyle);

            // ===== Block Property and Property Account Balance Columns =====

            // T: block_name (for grouping)
            Cell blockNameCell = row.createCell(col++);
            if (lease.getBlockName() != null) {
                blockNameCell.setCellValue(lease.getBlockName());
            } else {
                blockNameCell.setCellValue(""); // Not part of a block
            }

            // Columns U-X: Property account balance tracking (only for properties in blocks)
            if (lease.belongsToBlock() && lease.getBlockName() != null) {
                // U: property_account_opening - SUMIFS from PROPERTY_ACCOUNT sheet
                Cell propAcctOpeningCell = row.createCell(col++);
                propAcctOpeningCell.setCellFormula(String.format(
                    "IFERROR(SUMIFS(PROPERTY_ACCOUNT!D:D, PROPERTY_ACCOUNT!A:A, T%d, PROPERTY_ACCOUNT!B:B, \"opening\"), 0)",
                    rowNum + 1
                ));
                propAcctOpeningCell.setCellStyle(currencyStyle);

                // V: property_account_in - inflows from PROPERTY_ACCOUNT sheet
                Cell propAcctInCell = row.createCell(col++);
                propAcctInCell.setCellFormula(String.format(
                    "IFERROR(SUMIFS(PROPERTY_ACCOUNT!D:D, PROPERTY_ACCOUNT!A:A, T%d, PROPERTY_ACCOUNT!B:B, \"in\"), 0)",
                    rowNum + 1
                ));
                propAcctInCell.setCellStyle(currencyStyle);

                // W: property_account_out - outflows from PROPERTY_ACCOUNT sheet
                Cell propAcctOutCell = row.createCell(col++);
                propAcctOutCell.setCellFormula(String.format(
                    "IFERROR(SUMIFS(PROPERTY_ACCOUNT!D:D, PROPERTY_ACCOUNT!A:A, T%d, PROPERTY_ACCOUNT!B:B, \"out\"), 0)",
                    rowNum + 1
                ));
                propAcctOutCell.setCellStyle(currencyStyle);

                // X: property_account_closing (opening + in - out)
                Cell propAcctClosingCell = row.createCell(col++);
                propAcctClosingCell.setCellFormula(String.format("U%d + V%d - W%d", rowNum + 1, rowNum + 1, rowNum + 1));
                propAcctClosingCell.setCellStyle(currencyStyle);
            } else {
                // Not a block property - leave cells empty
                row.createCell(col++).setCellValue(""); // property_account_opening
                row.createCell(col++).setCellValue(""); // property_account_in
                row.createCell(col++).setCellValue(""); // property_account_out
                row.createCell(col++).setCellValue(""); // property_account_closing
            }

            // ===== RECONCILIATION COLUMNS (Y-AC) =====
            // Look up allocation by invoice_id first, fallback to property-based (negative key) for legacy data
            LeaseAllocationSummaryDTO allocSummary = null;
            if (lease.getLeaseId() != null) {
                allocSummary = allocationSummaries.get(lease.getLeaseId());
            }
            if (allocSummary == null && lease.getPropertyId() != null) {
                // Fallback: check for legacy allocations stored under negative property_id
                allocSummary = allocationSummaries.get(-lease.getPropertyId());
            }

            // Y: allocated_amount (sum of OWNER allocations)
            Cell allocatedCell = row.createCell(col++);
            if (allocSummary != null && allocSummary.getTotalAllocatedAmount() != null) {
                allocatedCell.setCellValue(allocSummary.getTotalAllocatedAmount().doubleValue());
            } else {
                allocatedCell.setCellValue(0);
            }
            allocatedCell.setCellStyle(currencyStyle);

            // Z: variance (net_to_owner - allocated_amount) - S is net_to_owner, Y is allocated
            Cell varianceCell = row.createCell(col++);
            varianceCell.setCellFormula(String.format("S%d-Y%d", rowNum + 1, rowNum + 1));
            varianceCell.setCellStyle(currencyStyle);

            // AA: allocation_status (PAID, PENDING, PARTIAL, NONE)
            Cell statusCell = row.createCell(col++);
            String status = determineAllocationStatus(allocSummary);
            statusCell.setCellValue(status);

            // AB: batch_id
            Cell batchCell = row.createCell(col++);
            batchCell.setCellValue(allocSummary != null && allocSummary.getPrimaryBatchId() != null ?
                allocSummary.getPrimaryBatchId() : "-");

            // AC: paid_date
            Cell paidDateCell = row.createCell(col++);
            if (allocSummary != null && allocSummary.getLatestPaymentDate() != null) {
                paidDateCell.setCellValue(allocSummary.getLatestPaymentDate());
                paidDateCell.setCellStyle(dateStyle);
            } else {
                paidDateCell.setCellValue("");
            }

            rowNum++;
        }

        // Add totals row (use shared styles to save memory)
        if (rowNum > 1) {
            Row totalsRow = sheet.createRow(rowNum);
            int col = 0;

            // A-F: Label in first column (use shared boldStyle)
            Cell labelCell = totalsRow.createCell(col++);
            labelCell.setCellValue("TOTALS");
            labelCell.setCellStyle(styles.boldStyle);

            // Skip columns B-F (property_name, customer_name, tenant_name, lease_start_date, rent_due_day)
            col += 5;

            // G: total_rent_due (use shared boldCurrencyStyle)
            Cell totalRentDueCell = totalsRow.createCell(col++);
            totalRentDueCell.setCellFormula(String.format("SUM(G2:G%d)", rowNum));
            totalRentDueCell.setCellStyle(styles.boldCurrencyStyle);

            // H: total_rent_received
            Cell totalRentReceivedCell = totalsRow.createCell(col++);
            totalRentReceivedCell.setCellFormula(String.format("SUM(H2:H%d)", rowNum));
            totalRentReceivedCell.setCellStyle(styles.boldCurrencyStyle);

            // I: rent_batch (skip - no total)
            totalsRow.createCell(col++).setCellValue("");

            // J: total_opening_balance
            Cell totalOpeningBalanceCell = totalsRow.createCell(col++);
            totalOpeningBalanceCell.setCellFormula(String.format("SUM(J2:J%d)", rowNum));
            totalOpeningBalanceCell.setCellStyle(styles.boldCurrencyStyle);

            // K: total_period_arrears
            Cell totalPeriodArrearsCell = totalsRow.createCell(col++);
            totalPeriodArrearsCell.setCellFormula(String.format("SUM(K2:K%d)", rowNum));
            totalPeriodArrearsCell.setCellStyle(styles.boldCurrencyStyle);

            // L: total_closing_balance
            Cell totalClosingBalanceCell = totalsRow.createCell(col++);
            totalClosingBalanceCell.setCellFormula(String.format("SUM(L2:L%d)", rowNum));
            totalClosingBalanceCell.setCellStyle(styles.boldCurrencyStyle);

            // M: total_management_fee
            Cell totalMgmtFeeCell = totalsRow.createCell(col++);
            totalMgmtFeeCell.setCellFormula(String.format("SUM(M2:M%d)", rowNum));
            totalMgmtFeeCell.setCellStyle(styles.boldCurrencyStyle);

            // N: total_service_fee
            Cell totalSvcFeeCell = totalsRow.createCell(col++);
            totalSvcFeeCell.setCellFormula(String.format("SUM(N2:N%d)", rowNum));
            totalSvcFeeCell.setCellStyle(styles.boldCurrencyStyle);

            // O: total_commission
            Cell totalCommCell = totalsRow.createCell(col++);
            totalCommCell.setCellFormula(String.format("SUM(O2:O%d)", rowNum));
            totalCommCell.setCellStyle(styles.boldCurrencyStyle);

            // P: commission_batch (skip - no total)
            totalsRow.createCell(col++).setCellValue("");

            // Q: total_expenses
            Cell totalExpensesCell = totalsRow.createCell(col++);
            totalExpensesCell.setCellFormula(String.format("SUM(Q2:Q%d)", rowNum));
            totalExpensesCell.setCellStyle(styles.boldCurrencyStyle);

            // R: expenses_batch (skip - no total)
            totalsRow.createCell(col++).setCellValue("");

            // S: total_net_to_owner
            Cell totalNetCell = totalsRow.createCell(col++);
            totalNetCell.setCellFormula(String.format("SUM(S2:S%d)", rowNum));
            totalNetCell.setCellStyle(styles.boldCurrencyStyle);

            // T: block_name (no total - just skip)
            totalsRow.createCell(col++).setCellValue("");

            // U-X: Property account balance totals
            Cell totalPropAcctOpeningCell = totalsRow.createCell(col++);
            totalPropAcctOpeningCell.setCellFormula(String.format("SUM(U2:U%d)", rowNum));
            totalPropAcctOpeningCell.setCellStyle(styles.boldCurrencyStyle);

            Cell totalPropAcctInCell = totalsRow.createCell(col++);
            totalPropAcctInCell.setCellFormula(String.format("SUM(V2:V%d)", rowNum));
            totalPropAcctInCell.setCellStyle(styles.boldCurrencyStyle);

            Cell totalPropAcctOutCell = totalsRow.createCell(col++);
            totalPropAcctOutCell.setCellFormula(String.format("SUM(W2:W%d)", rowNum));
            totalPropAcctOutCell.setCellStyle(styles.boldCurrencyStyle);

            Cell totalPropAcctClosingCell = totalsRow.createCell(col++);
            totalPropAcctClosingCell.setCellFormula(String.format("SUM(X2:X%d)", rowNum));
            totalPropAcctClosingCell.setCellStyle(styles.boldCurrencyStyle);
        }

        // Apply fixed column widths (autoSizeColumn causes OutOfMemoryError on large sheets)
        applyFixedColumnWidths(sheet, headers.length);

        // ===== PAYMENT RECONCILIATION SECTION (Period-Based) =====
        // Add blank rows for separation
        rowNum += 2;

        log.info("PAYMENT RECONCILIATION: Sheet {}, Period {} to {}, CustomerId: {}",
            sheetName, period.periodStart, period.periodEnd, customerId);

        if (customerId != null) {
            // Resolve actual owner ID for delegated users
            Long resolvedOwnerId = resolveActualOwnerId(customerId);
            log.info("PAYMENT RECONCILIATION: Resolved owner {} from customer {}", resolvedOwnerId, customerId);

            // Use new period-based reconciliation model
            rowNum = createPeriodBasedReconciliationSection(sheet, rowNum, resolvedOwnerId,
                period.periodStart, period.periodEnd, styles);
        } else {
            // Fallback to old model if no customerId (legacy calls)
            if (!propertyIds.isEmpty()) {
                List<PaymentBatchSummaryDTO> paymentBatches =
                    dataExtractService.extractPaymentBatchSummariesForPeriod(
                        propertyIds, period.periodStart, period.periodEnd);

                log.info("PAYMENT RECONCILIATION (legacy): Found {} payment batches for sheet {}",
                    paymentBatches != null ? paymentBatches.size() : 0, sheetName);

                int totalsRowNum = rowNum - 2;
                rowNum = createPaymentReconciliationSection(sheet, rowNum, totalsRowNum, paymentBatches, styles);
            } else {
                log.warn("PAYMENT RECONCILIATION: No property IDs or customerId for sheet {}", sheetName);
            }
        }

        log.info("{} sheet created with {} rows (including payment reconciliation)", sheetName, rowNum - 1);
    }

    /**
     * Create Payment Reconciliation section showing owner balance calculation.
     * Shows: Net to Owner, Payments Made, Balance c/f
     */
    private int createPaymentReconciliationSection(Sheet sheet, int startRowNum, int totalsRowNum,
            List<PaymentBatchSummaryDTO> paymentBatches, WorkbookStyles styles) {

        int rowNum = startRowNum;

        // Section header
        Row sectionHeaderRow = sheet.createRow(rowNum++);
        Cell sectionHeaderCell = sectionHeaderRow.createCell(0);
        sectionHeaderCell.setCellValue("PAYMENT RECONCILIATION");
        sectionHeaderCell.setCellStyle(styles.boldStyle);

        // Blank row
        rowNum++;

        // Owner Credit b/f (placeholder for now - would need prior period data)
        Row bfRow = sheet.createRow(rowNum++);
        bfRow.createCell(0).setCellValue("Owner Credit b/f:");
        Cell bfValueCell = bfRow.createCell(1);
        bfValueCell.setCellValue(0); // TODO: Calculate from prior periods
        bfValueCell.setCellStyle(styles.currencyStyle);

        // Net to Owner this period (reference to totals row column S)
        Row netRow = sheet.createRow(rowNum++);
        netRow.createCell(0).setCellValue("Net to Owner this period:");
        Cell netValueCell = netRow.createCell(1);
        netValueCell.setCellFormula(String.format("S%d", totalsRowNum + 1)); // Reference totals row
        netValueCell.setCellStyle(styles.currencyStyle);

        // Separator
        Row separatorRow = sheet.createRow(rowNum++);
        separatorRow.createCell(0).setCellValue("─────────────────────────────");

        // Amount Due
        Row dueRow = sheet.createRow(rowNum++);
        dueRow.createCell(0).setCellValue("Amount Due:");
        Cell dueValueCell = dueRow.createCell(1);
        // Amount Due = Net to Owner - Credit b/f (credit reduces what's due)
        dueValueCell.setCellFormula(String.format("B%d - B%d", rowNum - 2, rowNum - 4)); // Net - Credit b/f
        dueValueCell.setCellStyle(styles.currencyStyle);

        // Blank row
        rowNum++;

        // Payments Made header
        Row paymentsHeaderRow = sheet.createRow(rowNum++);
        paymentsHeaderRow.createCell(0).setCellValue("Payments Made:");

        // Calculate total payments
        BigDecimal totalPayments = BigDecimal.ZERO;

        if (paymentBatches != null && !paymentBatches.isEmpty()) {
            for (PaymentBatchSummaryDTO batch : paymentBatches) {
                Row batchRow = sheet.createRow(rowNum++);
                String batchInfo = String.format("  %s (%s)",
                    batch.getBatchId(),
                    batch.getPaymentDate() != null ? batch.getPaymentDate().toString() : "pending");
                batchRow.createCell(0).setCellValue(batchInfo);

                Cell batchAmountCell = batchRow.createCell(1);
                BigDecimal netPayment = batch.getNetPayment() != null ? batch.getNetPayment() : BigDecimal.ZERO;
                batchAmountCell.setCellValue(netPayment.doubleValue());
                batchAmountCell.setCellStyle(styles.currencyStyle);

                totalPayments = totalPayments.add(netPayment);
            }
        } else {
            Row noBatchRow = sheet.createRow(rowNum++);
            noBatchRow.createCell(0).setCellValue("  (No payments found for this period)");
        }

        // Separator
        Row separator2Row = sheet.createRow(rowNum++);
        separator2Row.createCell(0).setCellValue("─────────────────────────────");

        // Total Paid
        Row totalPaidRow = sheet.createRow(rowNum++);
        totalPaidRow.createCell(0).setCellValue("Total Paid:");
        Cell totalPaidCell = totalPaidRow.createCell(1);
        totalPaidCell.setCellValue(totalPayments.doubleValue());
        totalPaidCell.setCellStyle(styles.boldCurrencyStyle);

        // Owner Credit c/f (Balance = Payments - Net to Owner + Credit b/f)
        // Positive = overpayment (credit), Negative = underpayment (owed)
        Row cfRow = sheet.createRow(rowNum++);
        cfRow.createCell(0).setCellValue("Owner Credit c/f:");
        Cell cfValueCell = cfRow.createCell(1);
        // Credit c/f = Total Paid - Amount Due + Credit b/f
        // Simplified: Credit c/f = Credit b/f + Total Paid - Net to Owner
        double creditCf = totalPayments.doubleValue(); // Start with total paid
        cfValueCell.setCellFormula(String.format("B%d + %f - B%d",
            startRowNum + 3, // Credit b/f row
            totalPayments.doubleValue(),
            startRowNum + 4  // Net to Owner row
        ));
        cfValueCell.setCellStyle(styles.boldCurrencyStyle);

        // Note about the balance
        Row noteRow = sheet.createRow(rowNum++);
        noteRow.createCell(0).setCellValue("(Positive = overpaid, Negative = still owed)");

        return rowNum;
    }

    /**
     * Create Period-Based Reconciliation section with single Amount column
     * Uses [Income]/[Expense] text labels on allocation lines for clarity
     */
    private int createPeriodBasedReconciliationSection(Sheet sheet, int startRowNum, Long customerId,
                                                        LocalDate periodStart, LocalDate periodEnd,
                                                        WorkbookStyles styles) {
        int rowNum = startRowNum;

        CellStyle currencyStyle = styles.currencyStyle;
        CellStyle boldStyle = styles.boldStyle;
        CellStyle boldCurrencyStyle = styles.boldCurrencyStyle;

        // Column positions: A=Description, B=Amount
        final int COL_DESC = 0;
        final int COL_AMT = 1;

        // ===== TITLE =====
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(COL_DESC);
        titleCell.setCellValue("PAYMENT RECONCILIATION");
        titleCell.setCellStyle(boldStyle);

        Row periodRow = sheet.createRow(rowNum++);
        periodRow.createCell(COL_DESC).setCellValue("Period: " + periodStart.toString() + " to " + periodEnd.toString());

        rowNum++; // Blank row

        // ===== GET PAYMENTS FIRST (this data is reliable) =====
        List<PaymentWithAllocationsDTO> payments = dataExtractService.extractPaymentsWithAllocationsInPeriod(
            customerId, periodStart, periodEnd, periodStart);

        // Calculate B/F and Activity from payment allocations (more reliable than direct queries)
        // Collect B/F items with full details (these need to be listed since they don't appear elsewhere)
        List<PaymentWithAllocationsDTO.AllocationLineDTO> bfIncomeItems = new ArrayList<>();
        List<PaymentWithAllocationsDTO.AllocationLineDTO> bfExpenseItems = new ArrayList<>();
        BigDecimal bfIncomeFromAllocations = BigDecimal.ZERO;
        BigDecimal bfExpenseFromAllocations = BigDecimal.ZERO;
        BigDecimal periodIncomeFromAllocations = BigDecimal.ZERO;
        BigDecimal periodExpenseFromAllocations = BigDecimal.ZERO;
        int periodIncomeCount = 0;
        int periodExpenseCount = 0;

        for (PaymentWithAllocationsDTO payment : payments) {
            for (PaymentWithAllocationsDTO.AllocationLineDTO alloc : payment.getAllocations()) {
                // Use allocation_type from unified_allocations (more accurate than category guessing)
                boolean isExpense = alloc.isExpense();
                BigDecimal allocAmt = alloc.getAllocatedAmount() != null ? alloc.getAllocatedAmount().abs() : BigDecimal.ZERO;
                LocalDate txnDate = alloc.getTransactionDate();

                if (txnDate != null && txnDate.isBefore(periodStart)) {
                    // B/F item (from prior period) - collect with details
                    if (isExpense) {
                        bfExpenseFromAllocations = bfExpenseFromAllocations.add(allocAmt);
                        bfExpenseItems.add(alloc);
                    } else {
                        bfIncomeFromAllocations = bfIncomeFromAllocations.add(allocAmt);
                        bfIncomeItems.add(alloc);
                    }
                } else {
                    // This period item - just count (details appear elsewhere on sheet)
                    if (isExpense) {
                        periodExpenseFromAllocations = periodExpenseFromAllocations.add(allocAmt);
                        periodExpenseCount++;
                    } else {
                        periodIncomeFromAllocations = periodIncomeFromAllocations.add(allocAmt);
                        periodIncomeCount++;
                    }
                }
            }
        }

        // ===== BROUGHT FORWARD (with detail - these items don't appear elsewhere) =====
        Row bfHeaderRow = sheet.createRow(rowNum++);
        Cell bfHeaderCell = bfHeaderRow.createCell(COL_DESC);
        bfHeaderCell.setCellValue("BROUGHT FORWARD (items from prior periods paid this period)");
        bfHeaderCell.setCellStyle(boldStyle);

        // B/F Income items - listed individually
        if (!bfIncomeItems.isEmpty()) {
            Row bfIncomeHeaderRow = sheet.createRow(rowNum++);
            bfIncomeHeaderRow.createCell(COL_DESC).setCellValue("  Prior Period Income:");

            for (PaymentWithAllocationsDTO.AllocationLineDTO item : bfIncomeItems) {
                Row itemRow = sheet.createRow(rowNum++);
                String itemDesc = String.format("    %s | %s | %s | Batch: %s",
                    item.getTransactionDate() != null ? item.getTransactionDate().toString() : "N/A",
                    item.getPropertyName() != null ? item.getPropertyName() : "Unknown",
                    item.getCategory() != null ? item.getCategory() : "Income",
                    item.getBatchId() != null ? item.getBatchId() : "N/A");
                itemRow.createCell(COL_DESC).setCellValue(itemDesc);
                Cell itemAmtCell = itemRow.createCell(COL_AMT);
                itemAmtCell.setCellValue(item.getAllocatedAmount() != null ? item.getAllocatedAmount().abs().doubleValue() : 0);
                itemAmtCell.setCellStyle(currencyStyle);
            }

            Row bfIncomeTotalRow = sheet.createRow(rowNum++);
            Cell bfIncomeTotalLabel = bfIncomeTotalRow.createCell(COL_DESC);
            bfIncomeTotalLabel.setCellValue("  Prior Period Income Total (" + bfIncomeItems.size() + " items):");
            bfIncomeTotalLabel.setCellStyle(boldStyle);
            Cell bfIncomeTotalCell = bfIncomeTotalRow.createCell(COL_AMT);
            bfIncomeTotalCell.setCellValue(bfIncomeFromAllocations.doubleValue());
            bfIncomeTotalCell.setCellStyle(boldCurrencyStyle);
        } else {
            Row noIncomeRow = sheet.createRow(rowNum++);
            noIncomeRow.createCell(COL_DESC).setCellValue("  Prior Period Income: None");
        }

        // B/F Expense items - listed individually
        if (!bfExpenseItems.isEmpty()) {
            Row bfExpenseHeaderRow = sheet.createRow(rowNum++);
            bfExpenseHeaderRow.createCell(COL_DESC).setCellValue("  Prior Period Expenses:");

            for (PaymentWithAllocationsDTO.AllocationLineDTO item : bfExpenseItems) {
                Row itemRow = sheet.createRow(rowNum++);
                String itemDesc = String.format("    %s | %s | %s | Batch: %s",
                    item.getTransactionDate() != null ? item.getTransactionDate().toString() : "N/A",
                    item.getPropertyName() != null ? item.getPropertyName() : "Unknown",
                    item.getCategory() != null ? item.getCategory() : "Expense",
                    item.getBatchId() != null ? item.getBatchId() : "N/A");
                itemRow.createCell(COL_DESC).setCellValue(itemDesc);
                Cell itemAmtCell = itemRow.createCell(COL_AMT);
                itemAmtCell.setCellValue(item.getAllocatedAmount() != null ? item.getAllocatedAmount().abs().doubleValue() : 0);
                itemAmtCell.setCellStyle(currencyStyle);
            }

            Row bfExpenseTotalRow = sheet.createRow(rowNum++);
            Cell bfExpenseTotalLabel = bfExpenseTotalRow.createCell(COL_DESC);
            bfExpenseTotalLabel.setCellValue("  Prior Period Expenses Total (" + bfExpenseItems.size() + " items):");
            bfExpenseTotalLabel.setCellStyle(boldStyle);
            Cell bfExpenseTotalCell = bfExpenseTotalRow.createCell(COL_AMT);
            bfExpenseTotalCell.setCellValue(bfExpenseFromAllocations.doubleValue());
            bfExpenseTotalCell.setCellStyle(boldCurrencyStyle);
        } else {
            Row noExpenseRow = sheet.createRow(rowNum++);
            noExpenseRow.createCell(COL_DESC).setCellValue("  Prior Period Expenses: None");
        }

        // Net B/F
        BigDecimal netBf = bfIncomeFromAllocations.subtract(bfExpenseFromAllocations);
        Row bfNetRow = sheet.createRow(rowNum++);
        Cell bfNetLabel = bfNetRow.createCell(COL_DESC);
        bfNetLabel.setCellValue("  NET B/F (Income - Expenses):");
        bfNetLabel.setCellStyle(boldStyle);
        Cell bfNetCell = bfNetRow.createCell(COL_AMT);
        bfNetCell.setCellValue(netBf.doubleValue());
        bfNetCell.setCellStyle(boldCurrencyStyle);

        rowNum++; // Blank row

        // ===== THIS PERIOD ACTIVITY (summary only - details appear in main sheet) =====
        Row activityHeaderRow = sheet.createRow(rowNum++);
        Cell activityHeaderCell = activityHeaderRow.createCell(COL_DESC);
        activityHeaderCell.setCellValue("THIS PERIOD ACTIVITY (see transactions above for details)");
        activityHeaderCell.setCellStyle(boldStyle);

        Row incomeRow = sheet.createRow(rowNum++);
        incomeRow.createCell(COL_DESC).setCellValue("  Period Income (" + periodIncomeCount + " transactions)");
        Cell incomeCell = incomeRow.createCell(COL_AMT);
        incomeCell.setCellValue(periodIncomeFromAllocations.doubleValue());
        incomeCell.setCellStyle(currencyStyle);

        Row expenseRow = sheet.createRow(rowNum++);
        expenseRow.createCell(COL_DESC).setCellValue("  Period Expenses (" + periodExpenseCount + " transactions)");
        Cell expenseCell = expenseRow.createCell(COL_AMT);
        expenseCell.setCellValue(periodExpenseFromAllocations.doubleValue());
        expenseCell.setCellStyle(currencyStyle);

        BigDecimal netActivity = periodIncomeFromAllocations.subtract(periodExpenseFromAllocations);
        Row activityNetRow = sheet.createRow(rowNum++);
        Cell activityNetLabel = activityNetRow.createCell(COL_DESC);
        activityNetLabel.setCellValue("  Net Activity (Income - Expenses):");
        activityNetLabel.setCellStyle(boldStyle);
        Cell activityNetCell = activityNetRow.createCell(COL_AMT);
        activityNetCell.setCellValue(netActivity.doubleValue());
        activityNetCell.setCellStyle(boldCurrencyStyle);

        rowNum++; // Blank row

        // ===== PAYMENTS MADE THIS PERIOD =====
        Row paymentsHeaderRow = sheet.createRow(rowNum++);
        Cell paymentsHeaderCell = paymentsHeaderRow.createCell(COL_DESC);
        paymentsHeaderCell.setCellValue("PAYMENTS MADE THIS PERIOD");
        paymentsHeaderCell.setCellStyle(boldStyle);

        // Note: payments already fetched at start of method
        BigDecimal totalPaymentsMade = BigDecimal.ZERO;
        BigDecimal totalPaymentsAllocated = BigDecimal.ZERO;
        BigDecimal totalPeriodUnallocated = BigDecimal.ZERO;

        if (payments.isEmpty()) {
            Row noPaymentsRow = sheet.createRow(rowNum++);
            noPaymentsRow.createCell(COL_DESC).setCellValue("  (No payments made this period)");
        } else {
            for (PaymentWithAllocationsDTO payment : payments) {
                BigDecimal paymentAmount = payment.getTotalPayment() != null ? payment.getTotalPayment() : BigDecimal.ZERO;
                totalPaymentsMade = totalPaymentsMade.add(paymentAmount);

                // Calculate income vs expense breakdown for summary using allocation_type
                BigDecimal incomeAllocated = BigDecimal.ZERO;
                BigDecimal expenseAllocated = BigDecimal.ZERO;
                int incomeCount = 0;
                int expenseCount = 0;

                for (PaymentWithAllocationsDTO.AllocationLineDTO alloc : payment.getAllocations()) {
                    BigDecimal allocAmt = alloc.getAllocatedAmount() != null ? alloc.getAllocatedAmount().abs() : BigDecimal.ZERO;

                    if (alloc.isExpense()) {
                        expenseAllocated = expenseAllocated.add(allocAmt);
                        expenseCount++;
                    } else {
                        incomeAllocated = incomeAllocated.add(allocAmt);
                        incomeCount++;
                    }
                }
                // Net allocated = income - expenses (should match payment amount)
                BigDecimal netAllocated = incomeAllocated.subtract(expenseAllocated);
                totalPaymentsAllocated = totalPaymentsAllocated.add(netAllocated);

                // Payment line with amount
                Row paymentRow = sheet.createRow(rowNum++);
                String paymentInfo = String.format("  %s (%s)",
                    payment.getBatchId() != null ? payment.getBatchId() : "Unknown",
                    payment.getPaymentDate() != null ? payment.getPaymentDate().toString() : "pending");
                paymentRow.createCell(COL_DESC).setCellValue(paymentInfo);
                Cell paymentAmountCell = paymentRow.createCell(COL_AMT);
                paymentAmountCell.setCellValue(paymentAmount.doubleValue());
                paymentAmountCell.setCellStyle(boldCurrencyStyle);

                // Detailed breakdown - list each allocation for reconciliation
                // Separate income and expense items
                List<PaymentWithAllocationsDTO.AllocationLineDTO> incomeItems = new ArrayList<>();
                List<PaymentWithAllocationsDTO.AllocationLineDTO> expenseItems = new ArrayList<>();
                for (PaymentWithAllocationsDTO.AllocationLineDTO alloc : payment.getAllocations()) {
                    if (alloc.isExpense()) {
                        expenseItems.add(alloc);
                    } else {
                        incomeItems.add(alloc);
                    }
                }

                // List income items
                if (!incomeItems.isEmpty()) {
                    Row incomeHeaderRow = sheet.createRow(rowNum++);
                    incomeHeaderRow.createCell(COL_DESC).setCellValue("    Income:");

                    for (PaymentWithAllocationsDTO.AllocationLineDTO item : incomeItems) {
                        Row itemRow = sheet.createRow(rowNum++);
                        String partialMarker = item.isPartial() ? " [PARTIAL]" : "";
                        String priorMarker = item.isFromPriorPeriod() ? " [B/F]" : "";
                        String itemDesc = String.format("      %s | %s | %s%s%s",
                            item.getTransactionDate() != null ? item.getTransactionDate().toString() : "N/A",
                            item.getPropertyName() != null ? item.getPropertyName() : "Unknown",
                            item.getCategory() != null ? item.getCategory() : "income",
                            partialMarker, priorMarker);
                        itemRow.createCell(COL_DESC).setCellValue(itemDesc);
                        Cell itemAmtCell = itemRow.createCell(COL_AMT);
                        itemAmtCell.setCellValue(item.getAllocatedAmount() != null ? item.getAllocatedAmount().abs().doubleValue() : 0);
                        itemAmtCell.setCellStyle(currencyStyle);
                    }

                    // Income subtotal
                    Row incomeSubtotalRow = sheet.createRow(rowNum++);
                    incomeSubtotalRow.createCell(COL_DESC).setCellValue("    Income Subtotal:");
                    Cell incomeSubtotalCell = incomeSubtotalRow.createCell(COL_AMT);
                    incomeSubtotalCell.setCellValue(incomeAllocated.doubleValue());
                    incomeSubtotalCell.setCellStyle(currencyStyle);
                }

                // List expense items
                if (!expenseItems.isEmpty()) {
                    Row expenseHeaderRow = sheet.createRow(rowNum++);
                    expenseHeaderRow.createCell(COL_DESC).setCellValue("    Expenses:");

                    for (PaymentWithAllocationsDTO.AllocationLineDTO item : expenseItems) {
                        Row itemRow = sheet.createRow(rowNum++);
                        String partialMarker = item.isPartial() ? " [PARTIAL]" : "";
                        String priorMarker = item.isFromPriorPeriod() ? " [B/F]" : "";
                        String itemDesc = String.format("      %s | %s | %s%s%s",
                            item.getTransactionDate() != null ? item.getTransactionDate().toString() : "N/A",
                            item.getPropertyName() != null ? item.getPropertyName() : "Unknown",
                            item.getCategory() != null ? item.getCategory() : "expense",
                            partialMarker, priorMarker);
                        itemRow.createCell(COL_DESC).setCellValue(itemDesc);
                        Cell itemAmtCell = itemRow.createCell(COL_AMT);
                        itemAmtCell.setCellValue(item.getAllocatedAmount() != null ? item.getAllocatedAmount().abs().doubleValue() : 0);
                        itemAmtCell.setCellStyle(currencyStyle);
                    }

                    // Expense subtotal
                    Row expenseSubtotalRow = sheet.createRow(rowNum++);
                    expenseSubtotalRow.createCell(COL_DESC).setCellValue("    Expenses Subtotal:");
                    Cell expenseSubtotalCell = expenseSubtotalRow.createCell(COL_AMT);
                    expenseSubtotalCell.setCellValue(expenseAllocated.doubleValue());
                    expenseSubtotalCell.setCellStyle(currencyStyle);
                }

                // Net summary line
                if (incomeCount > 0 || expenseCount > 0) {
                    Row summaryRow = sheet.createRow(rowNum++);
                    String summaryText = String.format("    Net: £%.2f (Income £%.2f - Expenses £%.2f)",
                        netAllocated.doubleValue(), incomeAllocated.doubleValue(), expenseAllocated.doubleValue());
                    Cell summaryCell = summaryRow.createCell(COL_DESC);
                    summaryCell.setCellValue(summaryText);
                    summaryCell.setCellStyle(boldStyle);
                }

                // Unallocated portion
                BigDecimal unalloc = payment.getUnallocatedAmount() != null ? payment.getUnallocatedAmount() : BigDecimal.ZERO;
                if (unalloc.compareTo(BigDecimal.valueOf(0.01)) > 0) {
                    Row unallocRow = sheet.createRow(rowNum++);
                    unallocRow.createCell(COL_DESC).setCellValue("    → Unallocated (owner credit)");
                    Cell unallocAmtCell = unallocRow.createCell(COL_AMT);
                    unallocAmtCell.setCellValue(unalloc.doubleValue());
                    unallocAmtCell.setCellStyle(currencyStyle);
                    totalPeriodUnallocated = totalPeriodUnallocated.add(unalloc);
                }
            }
        }

        // Payments total
        Row paymentsTotalRow = sheet.createRow(rowNum++);
        Cell paymentsTotalLabel = paymentsTotalRow.createCell(COL_DESC);
        paymentsTotalLabel.setCellValue("  Total Payments Made:");
        paymentsTotalLabel.setCellStyle(boldStyle);
        Cell paymentsTotalCell = paymentsTotalRow.createCell(COL_AMT);
        paymentsTotalCell.setCellValue(totalPaymentsMade.doubleValue());
        paymentsTotalCell.setCellStyle(boldCurrencyStyle);

        // Reference to detail sheet
        Row refRow = sheet.createRow(rowNum++);
        refRow.createCell(COL_DESC).setCellValue("  (See PAYMENT_ALLOCATIONS sheet for full details)");

        rowNum++; // Blank row

        // ===== SUMMARY =====
        Row summaryHeaderRow = sheet.createRow(rowNum++);
        Cell summaryHeaderCell = summaryHeaderRow.createCell(COL_DESC);
        summaryHeaderCell.setCellValue("SUMMARY");
        summaryHeaderCell.setCellStyle(boldStyle);

        // Total allocated this period = B/F items + Period items
        BigDecimal totalAllocated = bfIncomeFromAllocations.add(periodIncomeFromAllocations)
                                    .subtract(bfExpenseFromAllocations).subtract(periodExpenseFromAllocations);
        Row allocatedRow = sheet.createRow(rowNum++);
        allocatedRow.createCell(COL_DESC).setCellValue("  Total Allocated (Income - Expenses):");
        Cell allocatedCell = allocatedRow.createCell(COL_AMT);
        allocatedCell.setCellValue(totalAllocated.doubleValue());
        allocatedCell.setCellStyle(currencyStyle);

        Row paymentsRow = sheet.createRow(rowNum++);
        paymentsRow.createCell(COL_DESC).setCellValue("  Total Payments Made:");
        Cell paymentsCell = paymentsRow.createCell(COL_AMT);
        paymentsCell.setCellValue(totalPaymentsMade.doubleValue());
        paymentsCell.setCellStyle(currencyStyle);

        // Unallocated from payments this period
        Row unallocRow = sheet.createRow(rowNum++);
        unallocRow.createCell(COL_DESC).setCellValue("  Unallocated Payment Amounts:");
        Cell unallocCell = unallocRow.createCell(COL_AMT);
        unallocCell.setCellValue(totalPeriodUnallocated.doubleValue());
        unallocCell.setCellStyle(currencyStyle);

        // Variance check: Payments = Allocated + Unallocated
        BigDecimal expectedPayments = totalAllocated.add(totalPeriodUnallocated);
        BigDecimal variance = totalPaymentsMade.subtract(expectedPayments);

        rowNum++;
        Row verifyRow = sheet.createRow(rowNum++);
        String verifyText = String.format("Check: Allocated %.2f + Unallocated %.2f = %.2f vs Payments %.2f",
            totalAllocated.doubleValue(), totalPeriodUnallocated.doubleValue(),
            expectedPayments.doubleValue(), totalPaymentsMade.doubleValue());
        verifyRow.createCell(COL_DESC).setCellValue(verifyText);

        if (variance.abs().compareTo(BigDecimal.valueOf(0.01)) > 0) {
            Row varianceRow = sheet.createRow(rowNum++);
            varianceRow.createCell(COL_DESC).setCellValue("  Variance: " + String.format("%.2f", variance.doubleValue()) + " (check allocations)");
        }

        return rowNum;
    }

    /**
     * Create SUMMARY sheet that totals across all custom periods
     */
    private void createSummarySheetForCustomPeriods(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                                   List<CustomPeriod> periods, LocalDate startDate, LocalDate endDate,
                                                   WorkbookStyles styles) {
        log.info("Creating SUMMARY sheet for custom periods");

        Sheet sheet = workbook.createSheet("SUMMARY");

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_reference", "property_name", "customer_name", "tenant_name", "lease_start_date", "rent_due_day",
            "total_rent_due", "total_rent_received", "total_arrears", "total_management_fee", "total_service_fee",
            "total_commission", "total_expenses", "total_net_to_owner", "opening_balance", "closing_balance"
        };

        // Use shared styles (memory optimization)
        CellStyle headerStyle = styles.headerStyle;
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Use shared styles
        CellStyle dateStyle = styles.dateStyle;
        CellStyle currencyStyle = styles.currencyStyle;

        int rowNum = 1;

        // Generate summary rows for each lease
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();
            LocalDate leaseEnd = lease.getEndDate();

            // Check if lease was active during ANY period
            boolean wasActiveInAnyPeriod = false;
            for (CustomPeriod period : periods) {
                boolean leaseActive = (leaseStart == null || !leaseStart.isAfter(period.periodEnd))
                                   && (leaseEnd == null || !leaseEnd.isBefore(period.periodStart));
                if (leaseActive) {
                    wasActiveInAnyPeriod = true;
                    break;
                }
            }

            if (wasActiveInAnyPeriod) {
                Row row = sheet.createRow(rowNum);
                int col = 0;

                // A: lease_reference
                row.createCell(col++).setCellValue(lease.getLeaseReference());

                // B: property_name
                row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");

                // C: customer_name
                row.createCell(col++).setCellValue(lease.getCustomerName() != null ? lease.getCustomerName() : "");

                // D: tenant_name
                row.createCell(col++).setCellValue(lease.getTenantName() != null ? lease.getTenantName() : "");

                // E: lease_start_date
                Cell startDateCell = row.createCell(col++);
                if (leaseStart != null) {
                    startDateCell.setCellValue(leaseStart);
                    startDateCell.setCellStyle(dateStyle);
                }

                // F: rent_due_day
                row.createCell(col++).setCellValue(lease.getPaymentDay() != null ? lease.getPaymentDay() : 0);

                // G: total_rent_due (SUM across all periods in RENT_DUE sheet)
                Cell totalRentDueCell = row.createCell(col++);
                totalRentDueCell.setCellFormula(String.format(
                    "SUMIFS(RENT_DUE!L:L, RENT_DUE!B:B, \"%s\", RENT_DUE!D:D, \">=\"&DATE(%d,%d,%d), RENT_DUE!D:D, \"<=\"&DATE(%d,%d,%d))",
                    lease.getLeaseReference(),
                    startDate.getYear(), startDate.getMonthValue(), startDate.getDayOfMonth(),
                    endDate.getYear(), endDate.getMonthValue(), endDate.getDayOfMonth()
                ));
                totalRentDueCell.setCellStyle(currencyStyle);

                // H: total_rent_received (SUM payments where period_start <= statement end date)
                // This includes pre-lease payments (period_start is before statement end)
                // But excludes payments for periods that start AFTER the statement end date
                Cell totalRentReceivedCell = row.createCell(col++);
                totalRentReceivedCell.setCellFormula(String.format(
                    "SUMIFS(RENT_RECEIVED!O:O, RENT_RECEIVED!B:B, \"%s\", RENT_RECEIVED!E:E, \"<=\"&DATE(%d,%d,%d))",
                    lease.getLeaseReference(),
                    endDate.getYear(), endDate.getMonthValue(), endDate.getDayOfMonth()
                ));
                totalRentReceivedCell.setCellStyle(currencyStyle);

                // I: total_arrears (formula: total_due - total_received)
                Cell totalArrearsCell = row.createCell(col++);
                totalArrearsCell.setCellFormula(String.format("G%d - H%d", rowNum + 1, rowNum + 1));
                totalArrearsCell.setCellStyle(currencyStyle);

                // J: total_management_fee - Use property-specific rate with fallback to global
                // BLOCK PROPERTY: No commission for block properties (they're communal funds, not rent)
                boolean isBlockProperty = Boolean.TRUE.equals(lease.getIsBlockProperty());
                double mgmtFeeRate = isBlockProperty ? 0.0
                    : (lease.getCommissionPercentage() != null
                        ? lease.getCommissionPercentage().doubleValue() / 100.0  // Property stores as percentage (e.g., 10), convert to decimal (0.10)
                        : commissionConfig.getManagementFeePercent().doubleValue());
                Cell totalMgmtFeeCell = row.createCell(col++);
                totalMgmtFeeCell.setCellFormula(String.format("H%d * %.4f", rowNum + 1, mgmtFeeRate));
                totalMgmtFeeCell.setCellStyle(currencyStyle);

                // K: total_service_fee - Use property-specific rate with fallback to global
                // BLOCK PROPERTY: No service fee for block properties
                double svcFeeRate = isBlockProperty ? 0.0
                    : (lease.getServiceFeePercentage() != null
                        ? lease.getServiceFeePercentage().doubleValue() / 100.0  // Property stores as percentage (e.g., 5), convert to decimal (0.05)
                        : commissionConfig.getServiceFeePercent().doubleValue());
                Cell totalSvcFeeCell = row.createCell(col++);
                totalSvcFeeCell.setCellFormula(String.format("H%d * %.4f", rowNum + 1, svcFeeRate));
                totalSvcFeeCell.setCellStyle(currencyStyle);

                // L: total_commission (with ABS to match period sheets)
                Cell totalCommCell = row.createCell(col++);
                totalCommCell.setCellFormula(String.format("ABS(J%d + K%d)", rowNum + 1, rowNum + 1));
                totalCommCell.setCellStyle(currencyStyle);

                // M: total_expenses (SUM all expenses for this lease in the period)
                // ✅ NEW: Flat EXPENSES sheet - sum expenses where expense_date falls within the statement period
                Cell totalExpensesCell = row.createCell(col++);
                totalExpensesCell.setCellFormula(String.format(
                    "SUMIFS(EXPENSES!E:E, EXPENSES!B:B, \"%s\", EXPENSES!D:D, \">=\"&DATE(%d,%d,%d), EXPENSES!D:D, \"<=\"&DATE(%d,%d,%d))",
                    lease.getLeaseReference(),
                    startDate.getYear(), startDate.getMonthValue(), startDate.getDayOfMonth(),
                    endDate.getYear(), endDate.getMonthValue(), endDate.getDayOfMonth()
                ));
                totalExpensesCell.setCellStyle(currencyStyle);

                // N: total_net_to_owner
                Cell totalNetCell = row.createCell(col++);
                totalNetCell.setCellFormula(String.format("H%d - L%d - M%d", rowNum + 1, rowNum + 1, rowNum + 1));
                totalNetCell.setCellStyle(currencyStyle);

                // O: opening_balance (pre-calculated from database)
                // MEMORY FIX: Calculate in service layer instead of using SUMIFS on historical data
                // Pass frequencyMonths and leaseEndDate for proration if lease ends mid-cycle
                Cell openingBalanceCell = row.createCell(col++);
                java.math.BigDecimal openingBalance = dataExtractService.calculateTenantOpeningBalance(
                    lease.getLeaseId(), lease.getStartDate(), startDate, lease.getMonthlyRent(),
                    lease.getFrequencyMonths(), lease.getEndDate());
                openingBalanceCell.setCellValue(openingBalance != null ? openingBalance.doubleValue() : 0);
                openingBalanceCell.setCellStyle(currencyStyle);

                // P: closing_balance (opening + arrears for period)
                Cell closingBalanceCell = row.createCell(col++);
                closingBalanceCell.setCellFormula(String.format("O%d + I%d", rowNum + 1, rowNum + 1));
                closingBalanceCell.setCellStyle(currencyStyle);

                rowNum++;
            }
        }

        // Add totals row
        if (rowNum > 1) {
            Row totalsRow = sheet.createRow(rowNum);
            int col = 0;

            // A-F: Label in first column
            Cell labelCell = totalsRow.createCell(col++);
            labelCell.setCellValue("TOTALS");
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            CellStyle boldStyle = workbook.createCellStyle();
            boldStyle.setFont(boldFont);
            labelCell.setCellStyle(boldStyle);

            // Skip columns B-F (property_name, customer_name, tenant_name, lease_start_date, rent_due_day)
            col += 5;

            // G: total_rent_due
            Cell totalRentDueCell = totalsRow.createCell(col++);
            totalRentDueCell.setCellFormula(String.format("SUM(G2:G%d)", rowNum));
            CellStyle boldCurrencyStyle = workbook.createCellStyle();
            boldCurrencyStyle.cloneStyleFrom(currencyStyle);
            boldCurrencyStyle.setFont(boldFont);
            totalRentDueCell.setCellStyle(boldCurrencyStyle);

            // H: total_rent_received
            Cell totalRentReceivedCell = totalsRow.createCell(col++);
            totalRentReceivedCell.setCellFormula(String.format("SUM(H2:H%d)", rowNum));
            totalRentReceivedCell.setCellStyle(boldCurrencyStyle);

            // I: total_arrears
            Cell totalArrearsCell = totalsRow.createCell(col++);
            totalArrearsCell.setCellFormula(String.format("SUM(I2:I%d)", rowNum));
            totalArrearsCell.setCellStyle(boldCurrencyStyle);

            // J: total_management_fee
            Cell totalMgmtFeeCell = totalsRow.createCell(col++);
            totalMgmtFeeCell.setCellFormula(String.format("SUM(J2:J%d)", rowNum));
            totalMgmtFeeCell.setCellStyle(boldCurrencyStyle);

            // K: total_service_fee
            Cell totalSvcFeeCell = totalsRow.createCell(col++);
            totalSvcFeeCell.setCellFormula(String.format("SUM(K2:K%d)", rowNum));
            totalSvcFeeCell.setCellStyle(boldCurrencyStyle);

            // L: total_commission
            Cell totalCommCell = totalsRow.createCell(col++);
            totalCommCell.setCellFormula(String.format("SUM(L2:L%d)", rowNum));
            totalCommCell.setCellStyle(boldCurrencyStyle);

            // M: total_expenses
            Cell totalExpensesCell = totalsRow.createCell(col++);
            totalExpensesCell.setCellFormula(String.format("SUM(M2:M%d)", rowNum));
            totalExpensesCell.setCellStyle(boldCurrencyStyle);

            // N: total_net_to_owner
            Cell totalNetCell = totalsRow.createCell(col++);
            totalNetCell.setCellFormula(String.format("SUM(N2:N%d)", rowNum));
            totalNetCell.setCellStyle(boldCurrencyStyle);

            // O: total_opening_balance
            Cell totalOpeningBalanceCell = totalsRow.createCell(col++);
            totalOpeningBalanceCell.setCellFormula(String.format("SUM(O2:O%d)", rowNum));
            totalOpeningBalanceCell.setCellStyle(boldCurrencyStyle);

            // P: total_closing_balance
            Cell totalClosingBalanceCell = totalsRow.createCell(col++);
            totalClosingBalanceCell.setCellFormula(String.format("SUM(P2:P%d)", rowNum));
            totalClosingBalanceCell.setCellStyle(boldCurrencyStyle);
        }

        // Apply fixed column widths (autoSizeColumn causes OutOfMemoryError on large sheets)
        applyFixedColumnWidths(sheet, headers.length);

        log.info("SUMMARY sheet created with {} rows", rowNum - 1);
    }

    /**
     * TEMPORARY: Create SUMMARY_CHECK sheet that calculates totals from monthly period tabs
     * This allows verification of SUMMARY calculations by comparing against period sheet data
     */
    private void createSummaryCheckSheet(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                         List<CustomPeriod> periods, WorkbookStyles styles) {
        log.info("Creating SUMMARY_CHECK sheet (verification from period tabs)");

        Sheet sheet = workbook.createSheet("SUMMARY_CHECK");

        // Header row - match SUMMARY columns for easy comparison
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_reference", "property_name", "customer_name", "tenant_name", "lease_start_date", "rent_due_day",
            "total_rent_due", "total_rent_received", "total_arrears", "total_management_fee", "total_service_fee",
            "total_commission", "total_expenses", "total_net_to_owner", "first_opening_balance", "last_closing_balance"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.headerStyle);
        }

        CellStyle dateStyle = styles.dateStyle;
        CellStyle currencyStyle = styles.currencyStyle;

        // Build list of period sheet names for formulas
        List<String> sheetNames = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd");
        DateTimeFormatter yearFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
        for (CustomPeriod period : periods) {
            String sheetName = period.periodStart.format(formatter) + " - " + period.periodEnd.format(yearFormatter);
            sheetNames.add(sheetName);
        }

        int rowNum = 1;

        // Generate rows for each lease
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();
            LocalDate leaseEnd = lease.getEndDate();

            // Check if lease was active during ANY period
            boolean wasActiveInAnyPeriod = false;
            for (CustomPeriod period : periods) {
                boolean leaseActive = (leaseStart == null || !leaseStart.isAfter(period.periodEnd))
                                   && (leaseEnd == null || !leaseEnd.isBefore(period.periodStart));
                if (leaseActive) {
                    wasActiveInAnyPeriod = true;
                    break;
                }
            }

            if (wasActiveInAnyPeriod) {
                Row row = sheet.createRow(rowNum);
                int col = 0;
                String leaseRef = lease.getLeaseReference();

                // A: lease_reference
                row.createCell(col++).setCellValue(leaseRef);

                // B: property_name
                row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");

                // C: customer_name
                row.createCell(col++).setCellValue(lease.getCustomerName() != null ? lease.getCustomerName() : "");

                // D: tenant_name
                row.createCell(col++).setCellValue(lease.getTenantName() != null ? lease.getTenantName() : "");

                // E: lease_start_date
                Cell startDateCell = row.createCell(col++);
                if (leaseStart != null) {
                    startDateCell.setCellValue(leaseStart);
                    startDateCell.setCellStyle(dateStyle);
                }

                // F: rent_due_day
                row.createCell(col++).setCellValue(lease.getPaymentDay() != null ? lease.getPaymentDay() : 0);

                // Build SUMIF formulas across all period sheets
                // Period sheet columns: G=rent_due, H=rent_received, J=period_arrears, L=management_fee,
                // M=service_fee, N=total_commission, O=total_expenses, P=net_to_owner, I=opening_balance, K=closing_balance

                // G: total_rent_due - SUM of column G across all period sheets
                Cell totalRentDueCell = row.createCell(col++);
                StringBuilder rentDueFormula = new StringBuilder();
                for (int i = 0; i < sheetNames.size(); i++) {
                    if (i > 0) rentDueFormula.append("+");
                    rentDueFormula.append(String.format("IFERROR(SUMIF('%s'!$A:$A,\"%s\",'%s'!$G:$G),0)",
                        sheetNames.get(i), leaseRef, sheetNames.get(i)));
                }
                totalRentDueCell.setCellFormula(rentDueFormula.toString());
                totalRentDueCell.setCellStyle(currencyStyle);

                // H: total_rent_received - SUM of column H across all period sheets
                Cell totalRentReceivedCell = row.createCell(col++);
                StringBuilder rentReceivedFormula = new StringBuilder();
                for (int i = 0; i < sheetNames.size(); i++) {
                    if (i > 0) rentReceivedFormula.append("+");
                    rentReceivedFormula.append(String.format("IFERROR(SUMIF('%s'!$A:$A,\"%s\",'%s'!$H:$H),0)",
                        sheetNames.get(i), leaseRef, sheetNames.get(i)));
                }
                totalRentReceivedCell.setCellFormula(rentReceivedFormula.toString());
                totalRentReceivedCell.setCellStyle(currencyStyle);

                // I: total_arrears (formula: total_due - total_received)
                Cell totalArrearsCell = row.createCell(col++);
                totalArrearsCell.setCellFormula(String.format("G%d - H%d", rowNum + 1, rowNum + 1));
                totalArrearsCell.setCellStyle(currencyStyle);

                // J: total_management_fee - SUM of column L across all period sheets
                Cell totalMgmtFeeCell = row.createCell(col++);
                StringBuilder mgmtFeeFormula = new StringBuilder();
                for (int i = 0; i < sheetNames.size(); i++) {
                    if (i > 0) mgmtFeeFormula.append("+");
                    mgmtFeeFormula.append(String.format("IFERROR(SUMIF('%s'!$A:$A,\"%s\",'%s'!$L:$L),0)",
                        sheetNames.get(i), leaseRef, sheetNames.get(i)));
                }
                totalMgmtFeeCell.setCellFormula(mgmtFeeFormula.toString());
                totalMgmtFeeCell.setCellStyle(currencyStyle);

                // K: total_service_fee - SUM of column M across all period sheets
                Cell totalSvcFeeCell = row.createCell(col++);
                StringBuilder svcFeeFormula = new StringBuilder();
                for (int i = 0; i < sheetNames.size(); i++) {
                    if (i > 0) svcFeeFormula.append("+");
                    svcFeeFormula.append(String.format("IFERROR(SUMIF('%s'!$A:$A,\"%s\",'%s'!$M:$M),0)",
                        sheetNames.get(i), leaseRef, sheetNames.get(i)));
                }
                totalSvcFeeCell.setCellFormula(svcFeeFormula.toString());
                totalSvcFeeCell.setCellStyle(currencyStyle);

                // L: total_commission - SUM of column N across all period sheets
                Cell totalCommCell = row.createCell(col++);
                StringBuilder commFormula = new StringBuilder();
                for (int i = 0; i < sheetNames.size(); i++) {
                    if (i > 0) commFormula.append("+");
                    commFormula.append(String.format("IFERROR(SUMIF('%s'!$A:$A,\"%s\",'%s'!$N:$N),0)",
                        sheetNames.get(i), leaseRef, sheetNames.get(i)));
                }
                totalCommCell.setCellFormula(commFormula.toString());
                totalCommCell.setCellStyle(currencyStyle);

                // M: total_expenses - SUM of column O across all period sheets
                Cell totalExpensesCell = row.createCell(col++);
                StringBuilder expensesFormula = new StringBuilder();
                for (int i = 0; i < sheetNames.size(); i++) {
                    if (i > 0) expensesFormula.append("+");
                    expensesFormula.append(String.format("IFERROR(SUMIF('%s'!$A:$A,\"%s\",'%s'!$O:$O),0)",
                        sheetNames.get(i), leaseRef, sheetNames.get(i)));
                }
                totalExpensesCell.setCellFormula(expensesFormula.toString());
                totalExpensesCell.setCellStyle(currencyStyle);

                // N: total_net_to_owner - SUM of column P across all period sheets
                Cell totalNetCell = row.createCell(col++);
                StringBuilder netFormula = new StringBuilder();
                for (int i = 0; i < sheetNames.size(); i++) {
                    if (i > 0) netFormula.append("+");
                    netFormula.append(String.format("IFERROR(SUMIF('%s'!$A:$A,\"%s\",'%s'!$P:$P),0)",
                        sheetNames.get(i), leaseRef, sheetNames.get(i)));
                }
                totalNetCell.setCellFormula(netFormula.toString());
                totalNetCell.setCellStyle(currencyStyle);

                // O: first_opening_balance - Get from FIRST period sheet column I
                Cell openingBalanceCell = row.createCell(col++);
                if (!sheetNames.isEmpty()) {
                    openingBalanceCell.setCellFormula(String.format(
                        "IFERROR(SUMIF('%s'!$A:$A,\"%s\",'%s'!$I:$I),0)",
                        sheetNames.get(0), leaseRef, sheetNames.get(0)));
                } else {
                    openingBalanceCell.setCellValue(0);
                }
                openingBalanceCell.setCellStyle(currencyStyle);

                // P: last_closing_balance - Get from LAST period sheet column K
                Cell closingBalanceCell = row.createCell(col++);
                if (!sheetNames.isEmpty()) {
                    String lastSheet = sheetNames.get(sheetNames.size() - 1);
                    closingBalanceCell.setCellFormula(String.format(
                        "IFERROR(SUMIF('%s'!$A:$A,\"%s\",'%s'!$K:$K),0)",
                        lastSheet, leaseRef, lastSheet));
                } else {
                    closingBalanceCell.setCellValue(0);
                }
                closingBalanceCell.setCellStyle(currencyStyle);

                rowNum++;
            }
        }

        // Add totals row
        if (rowNum > 1) {
            Row totalsRow = sheet.createRow(rowNum);
            int col = 0;

            Cell labelCell = totalsRow.createCell(col++);
            labelCell.setCellValue("TOTALS");
            labelCell.setCellStyle(styles.boldStyle);

            // Skip columns B-F
            col += 5;

            // G-P: Sum totals
            String[] sumCols = {"G", "H", "I", "J", "K", "L", "M", "N", "O", "P"};
            for (String sumCol : sumCols) {
                Cell totalCell = totalsRow.createCell(col++);
                totalCell.setCellFormula(String.format("SUM(%s2:%s%d)", sumCol, sumCol, rowNum));
                totalCell.setCellStyle(styles.boldCurrencyStyle);
            }
        }

        applyFixedColumnWidths(sheet, headers.length);
        log.info("SUMMARY_CHECK sheet created with {} rows", rowNum - 1);
    }

    /**
     * Create the RELATED PAYMENTS section below the main statement data.
     * Shows how the period's income and expenses were allocated to payment batches.
     *
     * Layout:
     * - Section header row ("RELATED PAYMENTS")
     * - Subtitle explaining the section
     * - Batch summary rows (one per batch)
     * - Detail rows (one per allocation) for full audit
     * - Section totals
     *
     * @param sheet The worksheet
     * @param startRowNum Starting row number
     * @param payments Payment batch summaries from extractRelatedPaymentsForPeriod()
     * @param styles Shared workbook styles
     * @return Next available row number
     */
    private int createRelatedPaymentsSection(Sheet sheet, int startRowNum,
            List<PaymentBatchSummaryDTO> payments, WorkbookStyles styles) {

        int rowNum = startRowNum;

        // Section header
        Row sectionHeaderRow = sheet.createRow(rowNum++);
        Cell sectionHeaderCell = sectionHeaderRow.createCell(0);
        sectionHeaderCell.setCellValue("RELATED PAYMENTS");
        sectionHeaderCell.setCellStyle(styles.boldStyle);

        // Subtitle explaining the section
        Row subtitleRow = sheet.createRow(rowNum++);
        subtitleRow.createCell(0).setCellValue(
            "Payment batches containing allocations for this period's transactions");

        // Blank row
        rowNum++;

        // Check if no payments
        if (payments == null || payments.isEmpty()) {
            Row noPaymentsRow = sheet.createRow(rowNum++);
            noPaymentsRow.createCell(0).setCellValue("No related payments found for this period");
            return rowNum;
        }

        // ===== BATCH SUMMARY SECTION =====
        Row summaryHeaderRow = sheet.createRow(rowNum++);
        String[] summaryHeaders = {
            "Batch ID", "Payment Date", "Status",
            "Income Allocated", "Expenses Deducted", "Commission Deducted",
            "Net Payment", "Properties"
        };

        for (int i = 0; i < summaryHeaders.length; i++) {
            Cell cell = summaryHeaderRow.createCell(i);
            cell.setCellValue(summaryHeaders[i]);
            cell.setCellStyle(styles.headerStyle);
        }

        // Payment batch summary rows
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;

        for (PaymentBatchSummaryDTO batch : payments) {
            Row row = sheet.createRow(rowNum++);
            int col = 0;

            // Batch ID
            row.createCell(col++).setCellValue(batch.getBatchId() != null ? batch.getBatchId() : "");

            // Payment Date
            Cell dateCell = row.createCell(col++);
            if (batch.getPaymentDate() != null) {
                dateCell.setCellValue(batch.getPaymentDate());
                dateCell.setCellStyle(styles.dateStyle);
            } else {
                dateCell.setCellValue("Pending");
            }

            // Status
            row.createCell(col++).setCellValue(batch.getBatchStatus() != null ? batch.getBatchStatus() : "");

            // Income Allocated (OWNER type)
            Cell incomeCell = row.createCell(col++);
            incomeCell.setCellValue(batch.getTotalOwnerAllocations() != null
                ? batch.getTotalOwnerAllocations().doubleValue() : 0);
            incomeCell.setCellStyle(styles.currencyStyle);

            // Expenses Deducted
            Cell expenseCell = row.createCell(col++);
            expenseCell.setCellValue(batch.getTotalExpenseAllocations() != null
                ? batch.getTotalExpenseAllocations().doubleValue() : 0);
            expenseCell.setCellStyle(styles.currencyStyle);

            // Commission Deducted
            Cell commCell = row.createCell(col++);
            commCell.setCellValue(batch.getTotalCommissionAllocations() != null
                ? batch.getTotalCommissionAllocations().doubleValue() : 0);
            commCell.setCellStyle(styles.currencyStyle);

            // Net Payment
            Cell netCell = row.createCell(col++);
            netCell.setCellValue(batch.getNetPayment() != null
                ? batch.getNetPayment().doubleValue() : 0);
            netCell.setCellStyle(styles.currencyStyle);

            // Property Count
            row.createCell(col++).setCellValue(batch.getPropertyCount());

            // Accumulate totals
            if (batch.getTotalOwnerAllocations() != null)
                totalIncome = totalIncome.add(batch.getTotalOwnerAllocations());
            if (batch.getTotalExpenseAllocations() != null)
                totalExpenses = totalExpenses.add(batch.getTotalExpenseAllocations());
            if (batch.getTotalCommissionAllocations() != null)
                totalCommission = totalCommission.add(batch.getTotalCommissionAllocations());
            if (batch.getNetPayment() != null)
                totalNet = totalNet.add(batch.getNetPayment());
        }

        // Summary totals row
        Row summaryTotalsRow = sheet.createRow(rowNum++);
        Cell summaryTotalLabelCell = summaryTotalsRow.createCell(0);
        summaryTotalLabelCell.setCellValue("PAYMENT TOTALS");
        summaryTotalLabelCell.setCellStyle(styles.boldStyle);

        // Skip date and status columns
        Cell totalIncomeCell = summaryTotalsRow.createCell(3);
        totalIncomeCell.setCellValue(totalIncome.doubleValue());
        totalIncomeCell.setCellStyle(styles.boldCurrencyStyle);

        Cell totalExpenseCell = summaryTotalsRow.createCell(4);
        totalExpenseCell.setCellValue(totalExpenses.doubleValue());
        totalExpenseCell.setCellStyle(styles.boldCurrencyStyle);

        Cell totalCommCell = summaryTotalsRow.createCell(5);
        totalCommCell.setCellValue(totalCommission.doubleValue());
        totalCommCell.setCellStyle(styles.boldCurrencyStyle);

        Cell totalNetCell = summaryTotalsRow.createCell(6);
        totalNetCell.setCellValue(totalNet.doubleValue());
        totalNetCell.setCellStyle(styles.boldCurrencyStyle);

        // ===== ALLOCATION DETAIL SECTION (Full Audit) =====
        rowNum += 2; // Blank rows

        Row detailHeaderLabelRow = sheet.createRow(rowNum++);
        Cell detailHeaderLabelCell = detailHeaderLabelRow.createCell(0);
        detailHeaderLabelCell.setCellValue("ALLOCATION DETAILS");
        detailHeaderLabelCell.setCellStyle(styles.boldStyle);

        Row detailHeaderRow = sheet.createRow(rowNum++);
        String[] detailHeaders = {
            "Allocation ID", "Transaction ID", "Batch ID", "Type",
            "Amount", "Property", "Category", "Description"
        };

        for (int i = 0; i < detailHeaders.length; i++) {
            Cell cell = detailHeaderRow.createCell(i);
            cell.setCellValue(detailHeaders[i]);
            cell.setCellStyle(styles.headerStyle);
        }

        // Detail rows for all allocations
        for (PaymentBatchSummaryDTO batch : payments) {
            if (batch.getAllocations() != null) {
                for (RelatedPaymentDTO alloc : batch.getAllocations()) {
                    Row row = sheet.createRow(rowNum++);
                    int col = 0;

                    // Allocation ID
                    row.createCell(col++).setCellValue(
                        alloc.getAllocationId() != null ? alloc.getAllocationId() : 0);

                    // Transaction ID
                    row.createCell(col++).setCellValue(
                        alloc.getTransactionId() != null ? alloc.getTransactionId() : 0);

                    // Batch ID
                    row.createCell(col++).setCellValue(
                        alloc.getBatchId() != null ? alloc.getBatchId() : "");

                    // Type
                    row.createCell(col++).setCellValue(
                        alloc.getAllocationType() != null ? alloc.getAllocationType() : "");

                    // Amount
                    Cell amountCell = row.createCell(col++);
                    amountCell.setCellValue(alloc.getAmount() != null
                        ? alloc.getAmount().doubleValue() : 0);
                    amountCell.setCellStyle(styles.currencyStyle);

                    // Property
                    row.createCell(col++).setCellValue(
                        alloc.getPropertyName() != null ? alloc.getPropertyName() : "");

                    // Category
                    row.createCell(col++).setCellValue(
                        alloc.getCategory() != null ? alloc.getCategory() : "");

                    // Description
                    row.createCell(col++).setCellValue(
                        alloc.getDescription() != null ? alloc.getDescription() : "");
                }
            }
        }

        log.info("Related payments section created with {} batches", payments.size());
        return rowNum;
    }

    /**
     * Create MONTHLY_STATEMENT sheet with custom periods
     */
    private void createMonthlyStatementSheetWithCustomPeriods(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                                             LocalDate startDate, LocalDate endDate, int periodStartDay,
                                                             WorkbookStyles styles) {
        log.info("Creating MONTHLY_STATEMENT sheet with custom periods");

        Sheet sheet = workbook.createSheet("MONTHLY_STATEMENT");

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_reference", "property_name", "customer_name", "lease_start_date", "rent_due_day", "period",
            "rent_due", "rent_received", "arrears", "management_fee", "service_fee",
            "total_commission", "total_expenses", "net_to_owner", "opening_balance", "cumulative_arrears"
        };

        // Use shared styles (memory optimization)
        CellStyle headerStyle = styles.headerStyle;
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Use shared styles
        CellStyle dateStyle = styles.dateStyle;
        CellStyle currencyStyle = styles.currencyStyle;

        int rowNum = 1;

        // Generate custom periods for STATEMENT PERIOD only (opening balance handles prior history)
        List<CustomPeriod> periods = generateCustomPeriods(startDate, endDate, periodStartDay);

        // Generate rows for each lease × custom period
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();
            LocalDate leaseEnd = lease.getEndDate();
            boolean isFirstPeriodForLease = true;

            for (CustomPeriod period : periods) {
                boolean leaseActive = (leaseStart == null || !leaseStart.isAfter(period.periodEnd))
                                   && (leaseEnd == null || !leaseEnd.isBefore(period.periodStart));

                if (leaseActive) {
                    Row row = sheet.createRow(rowNum);
                    int col = 0;

                    // A: lease_reference
                    row.createCell(col++).setCellValue(lease.getLeaseReference());

                    // B: property_name
                    row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");

                    // C: customer_name
                    row.createCell(col++).setCellValue(lease.getCustomerName() != null ? lease.getCustomerName() : "");

                    // D: lease_start_date
                    Cell startDateCell = row.createCell(col++);
                    if (leaseStart != null) {
                        startDateCell.setCellValue(leaseStart);
                        startDateCell.setCellStyle(dateStyle);
                    }

                    // E: rent_due_day
                    row.createCell(col++).setCellValue(lease.getPaymentDay() != null ? lease.getPaymentDay() : 0);

                    // F: period (formatted as "Dec 22 - Jan 21")
                    String periodLabel = period.periodStart.format(DateTimeFormatter.ofPattern("MMM dd")) + " - " +
                                       period.periodEnd.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
                    row.createCell(col++).setCellValue(periodLabel);

                    // G: rent_due (SUMPRODUCT to RENT_DUE sheet - sum all rent due periods starting within this billing period)
                    Cell rentDueCell = row.createCell(col++);
                    rentDueCell.setCellFormula(String.format(
                        "IFERROR(SUMPRODUCT((RENT_DUE!$B$2:$B$1000=\"%s\")*(RENT_DUE!$D$2:$D$1000>=DATE(%d,%d,%d))*(RENT_DUE!$D$2:$D$1000<=DATE(%d,%d,%d))*RENT_DUE!$L$2:$L$1000), 0)",
                        lease.getLeaseReference(),
                        period.periodStart.getYear(), period.periodStart.getMonthValue(), period.periodStart.getDayOfMonth(),
                        period.periodEnd.getYear(), period.periodEnd.getMonthValue(), period.periodEnd.getDayOfMonth()
                    ));
                    rentDueCell.setCellStyle(currencyStyle);

                    // H: rent_received (SUMPRODUCT to RENT_RECEIVED sheet - flat structure: D=payment_date, E=amount)
                    Cell rentReceivedCell = row.createCell(col++);
                    rentReceivedCell.setCellFormula(String.format(
                        "IFERROR(SUMPRODUCT((RENT_RECEIVED!$B$2:$B$10000=\"%s\")*(RENT_RECEIVED!$D$2:$D$10000>=DATE(%d,%d,%d))*(RENT_RECEIVED!$D$2:$D$10000<=DATE(%d,%d,%d))*RENT_RECEIVED!$E$2:$E$10000), 0)",
                        lease.getLeaseReference(),
                        period.periodStart.getYear(), period.periodStart.getMonthValue(), period.periodStart.getDayOfMonth(),
                        period.periodEnd.getYear(), period.periodEnd.getMonthValue(), period.periodEnd.getDayOfMonth()
                    ));
                    rentReceivedCell.setCellStyle(currencyStyle);

                    // I: arrears (formula: due - received)
                    Cell arrearsCell = row.createCell(col++);
                    arrearsCell.setCellFormula(String.format("G%d - H%d", rowNum + 1, rowNum + 1));
                    arrearsCell.setCellStyle(currencyStyle);

                    // J: management_fee (formula: rent_received * management_fee_percent)
                    // BLOCK PROPERTY: No commission for block properties (they're communal funds, not rent)
                    boolean isBlockProperty = Boolean.TRUE.equals(lease.getIsBlockProperty());
                    double mgmtFeeRate = isBlockProperty ? 0.0 : commissionConfig.getManagementFeePercent().doubleValue();
                    Cell mgmtFeeCell = row.createCell(col++);
                    mgmtFeeCell.setCellFormula(String.format("H%d * %.4f", rowNum + 1, mgmtFeeRate));
                    mgmtFeeCell.setCellStyle(currencyStyle);

                    // K: service_fee (formula: rent_received * service_fee_percent)
                    // BLOCK PROPERTY: No service fee for block properties
                    double svcFeeRate = isBlockProperty ? 0.0 : commissionConfig.getServiceFeePercent().doubleValue();
                    Cell svcFeeCell = row.createCell(col++);
                    svcFeeCell.setCellFormula(String.format("H%d * %.4f", rowNum + 1, svcFeeRate));
                    svcFeeCell.setCellStyle(currencyStyle);

                    // L: total_commission (formula: mgmt + svc)
                    Cell totalCommCell = row.createCell(col++);
                    totalCommCell.setCellFormula(String.format("J%d + K%d", rowNum + 1, rowNum + 1));
                    totalCommCell.setCellStyle(currencyStyle);

                    // M: total_expenses (SUMIFS to EXPENSES sheet)
                    // ✅ NEW: Flat EXPENSES sheet - sum expenses where expense_date falls within the period
                    Cell expensesCell = row.createCell(col++);
                    expensesCell.setCellFormula(String.format(
                        "SUMIFS(EXPENSES!E:E, EXPENSES!B:B, \"%s\", EXPENSES!D:D, \">=\"&DATE(%d,%d,%d), EXPENSES!D:D, \"<=\"&DATE(%d,%d,%d))",
                        lease.getLeaseReference(),
                        period.periodStart.getYear(), period.periodStart.getMonthValue(), period.periodStart.getDayOfMonth(),
                        period.periodEnd.getYear(), period.periodEnd.getMonthValue(), period.periodEnd.getDayOfMonth()
                    ));
                    expensesCell.setCellStyle(currencyStyle);

                    // N: net_to_owner (formula: received - commission - expenses)
                    Cell netToOwnerCell = row.createCell(col++);
                    netToOwnerCell.setCellFormula(String.format("H%d - L%d - M%d", rowNum + 1, rowNum + 1, rowNum + 1));
                    netToOwnerCell.setCellStyle(currencyStyle);

                    // O: opening_balance (pre-calculated from database)
                    // MEMORY FIX: Calculate in service layer instead of using SUMIFS on historical data
                    // Pass frequencyMonths and leaseEndDate for proration if lease ends mid-cycle
                    Cell openingBalanceCell = row.createCell(col++);
                    if (isFirstPeriodForLease) {
                        // Opening balance calculated from database for first period
                        java.math.BigDecimal openingBalance = dataExtractService.calculateTenantOpeningBalance(
                            lease.getLeaseId(), leaseStart, period.periodStart, lease.getMonthlyRent(),
                            lease.getFrequencyMonths(), lease.getEndDate());
                        openingBalanceCell.setCellValue(openingBalance != null ? openingBalance.doubleValue() : 0);
                    } else {
                        // For subsequent periods, opening balance is 0 (cumulative handles the carry-forward)
                        openingBalanceCell.setCellValue(0);
                    }
                    openingBalanceCell.setCellStyle(currencyStyle);

                    // P: cumulative_arrears (formula: opening_balance + running sum of arrears)
                    Cell cumArrearsCell = row.createCell(col++);
                    if (isFirstPeriodForLease) {
                        cumArrearsCell.setCellFormula(String.format("O%d + I%d", rowNum + 1, rowNum + 1));
                    } else {
                        cumArrearsCell.setCellFormula(String.format("P%d + I%d", rowNum, rowNum + 1));
                    }
                    cumArrearsCell.setCellStyle(currencyStyle);

                    rowNum++;
                    isFirstPeriodForLease = false;
                }
            }
        }

        // Apply fixed column widths (autoSizeColumn causes OutOfMemoryError on large sheets)
        applyFixedColumnWidths(sheet, headers.length);

        log.info("MONTHLY_STATEMENT sheet created with {} rows (custom periods)", rowNum - 1);
    }

    // ============================================================================
    // Cell Style Helpers
    // ============================================================================
    // STYLE MANAGEMENT - MEMORY OPTIMIZATION
    // CellStyles are stored at workbook level. Creating styles per-sheet wastes memory.
    // Solution: Create styles ONCE per workbook and reuse across all sheets.
    // ============================================================================

    /**
     * Holds all CellStyles for a workbook - created ONCE and reused across all sheets.
     * This prevents creating 51+ duplicate styles (was causing OOM on 512MB instances).
     */
    private static class WorkbookStyles {
        final CellStyle headerStyle;
        final CellStyle dateStyle;
        final CellStyle currencyStyle;
        final CellStyle percentStyle;
        final CellStyle boldStyle;
        final CellStyle boldCurrencyStyle;

        WorkbookStyles(Workbook workbook) {
            // Bold font - reused across styles
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);

            // Header style - bold with grey background
            this.headerStyle = workbook.createCellStyle();
            headerStyle.setFont(boldFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Date style
            DataFormat format = workbook.createDataFormat();
            this.dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(format.getFormat("yyyy-mm-dd"));

            // Currency style
            this.currencyStyle = workbook.createCellStyle();
            currencyStyle.setDataFormat(format.getFormat("£#,##0.00"));

            // Percent style
            this.percentStyle = workbook.createCellStyle();
            percentStyle.setDataFormat(format.getFormat("0.00%"));

            // Bold style (for totals labels)
            this.boldStyle = workbook.createCellStyle();
            boldStyle.setFont(boldFont);

            // Bold currency style (for totals values)
            this.boldCurrencyStyle = workbook.createCellStyle();
            boldCurrencyStyle.setFont(boldFont);
            boldCurrencyStyle.setDataFormat(format.getFormat("£#,##0.00"));
        }
    }

    /**
     * @deprecated Use WorkbookStyles instead for memory efficiency
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /**
     * @deprecated Use WorkbookStyles instead for memory efficiency
     */
    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("yyyy-mm-dd"));
        return style;
    }

    /**
     * @deprecated Use WorkbookStyles instead for memory efficiency
     */
    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("£#,##0.00"));
        return style;
    }

    // ===== OWNER ID RESOLUTION =====

    /**
     * Resolve the actual owner ID for allocation lookups.
     * For DELEGATED_USER or MANAGER types, returns the manages_owner_id.
     * For PROPERTY_OWNER, returns the same customerId.
     * This ensures allocation sheets show data for the actual property owner,
     * not the delegated user who is viewing the statement.
     */
    private Long resolveActualOwnerId(Long customerId) {
        try {
            Customer customer = customerRepository.findById(customerId).orElse(null);
            if (customer == null) {
                log.debug("🔍 Customer {} not found, returning same ID", customerId);
                return customerId;
            }

            log.debug("🔍 Customer {} type={}, managesOwnerId={}",
                    customerId, customer.getCustomerType(), customer.getManagesOwnerId());

            // If delegated user or manager with an assigned owner, return the owner they manage
            if ((customer.getCustomerType() == site.easy.to.build.crm.entity.CustomerType.DELEGATED_USER ||
                 customer.getCustomerType() == site.easy.to.build.crm.entity.CustomerType.MANAGER) &&
                customer.getManagesOwnerId() != null) {

                log.debug("🔍 Delegated user/manager {} manages owner {}, using that ID",
                        customerId, customer.getManagesOwnerId());
                return customer.getManagesOwnerId();
            }

            // Otherwise return the same ID (it's already the owner)
            return customerId;

        } catch (Exception e) {
            log.error("Error resolving owner ID for customer {}: {}", customerId, e.getMessage());
            return customerId;
        }
    }

    // ===== ALLOCATION TRACKING SHEETS =====

    /**
     * Create Income Allocations sheet showing all income transactions allocated to payment batches
     */
    private void createIncomeAllocationsSheet(Workbook workbook, Long ownerId, WorkbookStyles styles) {
        log.info("Creating Income Allocations sheet for owner {}", ownerId);

        Sheet sheet = workbook.createSheet("Income Allocations");
        // Use shared styles (memory optimization)
        CellStyle headerStyle = styles.headerStyle;
        CellStyle dateStyle = styles.dateStyle;
        CellStyle currencyStyle = styles.currencyStyle;

        // Headers - Transaction ID links to the Transactions sheet
        String[] headers = {"Transaction ID", "Date", "Property", "Category", "Description", "Source",
                           "Amount", "Batch Ref", "Payment Date", "Payment Status"};

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        BigDecimal totalAllocated = BigDecimal.ZERO;

        // Income allocations come from unified_allocations (amount > 0)
        // This includes both PayProp and manual batch allocations
        List<Object[]> incomeAllocations = unifiedAllocationRepository.getIncomeAllocationsForOwner(ownerId);
        log.info("Found {} income allocations (amount > 0) for owner {}", incomeAllocations.size(), ownerId);

        for (Object[] allocation : incomeAllocations) {
            Row row = sheet.createRow(rowNum++);

            // Parse allocation data from unified_allocations
            // Columns: 0=id, 1=unified_transaction_id, 2=historical_transaction_id, 3=property_name,
            //          4=category, 5=amount, 6=payment_batch_id, 7=description, 8=created_at, 9=source
            Long allocationId = allocation[0] != null ? ((Number) allocation[0]).longValue() : null;
            Long unifiedTxId = allocation[1] != null ? ((Number) allocation[1]).longValue() : null;
            Long historicalTxId = allocation[2] != null ? ((Number) allocation[2]).longValue() : null;
            String propertyName = allocation[3] != null ? allocation[3].toString() : "";
            String category = allocation[4] != null ? allocation[4].toString() : "";
            BigDecimal allocatedAmount = allocation[5] != null ? new BigDecimal(allocation[5].toString()) : BigDecimal.ZERO;
            String batchRef = allocation[6] != null ? allocation[6].toString() : "";
            String description = allocation[7] != null ? allocation[7].toString() : "";
            java.sql.Timestamp createdAt = allocation[8] != null ? java.sql.Timestamp.valueOf(allocation[8].toString()) : null;
            String source = allocation[9] != null ? allocation[9].toString() : "";

            // Get payment batch details
            PaymentBatch batch = paymentBatchRepository.findByBatchId(batchRef).orElse(null);
            LocalDate paymentDate = batch != null ? batch.getPaymentDate() : null;
            String paymentStatus = batch != null ? batch.getStatus().toString() : "Unknown";

            // Transaction ID - prefer unified, fall back to historical
            Long transactionId = unifiedTxId != null ? unifiedTxId : historicalTxId;
            if (transactionId != null) {
                row.createCell(0).setCellValue(transactionId);
            } else {
                row.createCell(0).setCellValue("");
            }

            // Date (use created_at from allocation)
            Cell dateCell = row.createCell(1);
            if (createdAt != null) {
                dateCell.setCellValue(createdAt);
                dateCell.setCellStyle(dateStyle);
            }

            // Property
            row.createCell(2).setCellValue(propertyName);

            // Category
            row.createCell(3).setCellValue(category);

            // Description
            row.createCell(4).setCellValue(description);

            // Source (from database: PAYPROP, HISTORICAL, MANUAL)
            row.createCell(5).setCellValue(source);

            // Amount
            Cell amountCell = row.createCell(6);
            amountCell.setCellValue(allocatedAmount.doubleValue());
            amountCell.setCellStyle(currencyStyle);
            totalAllocated = totalAllocated.add(allocatedAmount);

            // Batch Ref
            row.createCell(7).setCellValue(batchRef);

            // Payment Date
            Cell payDateCell = row.createCell(8);
            if (paymentDate != null) {
                payDateCell.setCellValue(java.sql.Date.valueOf(paymentDate));
                payDateCell.setCellStyle(dateStyle);
            }

            // Payment Status
            row.createCell(9).setCellValue(paymentStatus);
        }

        // Total row
        Row totalRow = sheet.createRow(rowNum);
        Cell totalLabel = totalRow.createCell(5);
        totalLabel.setCellValue("TOTAL:");
        totalLabel.setCellStyle(headerStyle);

        Cell totalCell = totalRow.createCell(6);
        totalCell.setCellValue(totalAllocated.doubleValue());
        totalCell.setCellStyle(currencyStyle);

        // Apply fixed column widths (autoSizeColumn causes OutOfMemoryError on large sheets)
        applyFixedColumnWidths(sheet, headers.length);

        log.info("Income Allocations sheet created with {} rows, total: {}", incomeAllocations.size(), totalAllocated);
    }

    /**
     * Create Expense Allocations sheet showing all expense transactions allocated to payment batches
     */
    private void createExpenseAllocationsSheet(Workbook workbook, Long ownerId, WorkbookStyles styles) {
        log.info("Creating Expense Allocations sheet for owner {}", ownerId);

        Sheet sheet = workbook.createSheet("Expense Allocations");
        // Use shared styles (memory optimization)
        CellStyle headerStyle = styles.headerStyle;
        CellStyle dateStyle = styles.dateStyle;
        CellStyle currencyStyle = styles.currencyStyle;

        // Headers - Transaction ID links to the Transactions sheet
        String[] headers = {"Transaction ID", "Date", "Property", "Category", "Description", "Source",
                           "Amount", "Batch Ref", "Payment Date", "Payment Status"};

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        BigDecimal totalAllocated = BigDecimal.ZERO;

        // Expense allocations come from unified_allocations (EXPENSE, COMMISSION, DISBURSEMENT types)
        // This includes both PayProp and manual batch allocations
        List<Object[]> expenseAllocations = unifiedAllocationRepository.getExpenseAllocationsForOwner(ownerId);
        log.info("Found {} expense/commission/disbursement allocations for owner {}", expenseAllocations.size(), ownerId);

        for (Object[] allocation : expenseAllocations) {
            Row row = sheet.createRow(rowNum++);

            // Parse allocation data from unified_allocations
            // Columns: 0=id, 1=unified_transaction_id, 2=historical_transaction_id, 3=property_name,
            //          4=category, 5=amount, 6=payment_batch_id, 7=description, 8=created_at, 9=source
            Long allocationId = allocation[0] != null ? ((Number) allocation[0]).longValue() : null;
            Long unifiedTxId = allocation[1] != null ? ((Number) allocation[1]).longValue() : null;
            Long historicalTxId = allocation[2] != null ? ((Number) allocation[2]).longValue() : null;
            String propertyName = allocation[3] != null ? allocation[3].toString() : "";
            String category = allocation[4] != null ? allocation[4].toString() : "";
            BigDecimal allocatedAmount = allocation[5] != null ? new BigDecimal(allocation[5].toString()) : BigDecimal.ZERO;
            String batchRef = allocation[6] != null ? allocation[6].toString() : "";
            String description = allocation[7] != null ? allocation[7].toString() : "";
            java.sql.Timestamp createdAt = allocation[8] != null ? java.sql.Timestamp.valueOf(allocation[8].toString()) : null;
            String source = allocation[9] != null ? allocation[9].toString() : "";

            // Get payment batch details
            PaymentBatch batch = paymentBatchRepository.findByBatchId(batchRef).orElse(null);
            LocalDate paymentDate = batch != null ? batch.getPaymentDate() : null;
            String paymentStatus = batch != null ? batch.getStatus().toString() : "Unknown";

            // Transaction ID - prefer unified, fall back to historical
            Long transactionId = unifiedTxId != null ? unifiedTxId : historicalTxId;
            if (transactionId != null) {
                row.createCell(0).setCellValue(transactionId);
            } else {
                row.createCell(0).setCellValue("");
            }

            // Date (use created_at from allocation)
            Cell dateCell = row.createCell(1);
            if (createdAt != null) {
                dateCell.setCellValue(createdAt);
                dateCell.setCellStyle(dateStyle);
            }

            // Property
            row.createCell(2).setCellValue(propertyName);

            // Category
            row.createCell(3).setCellValue(category);

            // Description
            row.createCell(4).setCellValue(description);

            // Source (from database: PAYPROP, HISTORICAL, MANUAL)
            row.createCell(5).setCellValue(source);

            // Amount (expenses are positive in PayProp, show as-is)
            Cell amountCell = row.createCell(6);
            amountCell.setCellValue(allocatedAmount.doubleValue());
            amountCell.setCellStyle(currencyStyle);
            totalAllocated = totalAllocated.add(allocatedAmount);

            // Batch Ref
            row.createCell(7).setCellValue(batchRef);

            // Payment Date
            Cell payDateCell = row.createCell(8);
            if (paymentDate != null) {
                payDateCell.setCellValue(java.sql.Date.valueOf(paymentDate));
                payDateCell.setCellStyle(dateStyle);
            }

            // Payment Status
            row.createCell(9).setCellValue(paymentStatus);
        }

        // Total row
        Row totalRow = sheet.createRow(rowNum);
        Cell totalLabel = totalRow.createCell(5);
        totalLabel.setCellValue("TOTAL:");
        totalLabel.setCellStyle(headerStyle);

        Cell totalCell = totalRow.createCell(6);
        totalCell.setCellValue(totalAllocated.doubleValue());
        totalCell.setCellStyle(currencyStyle);

        // Apply fixed column widths (autoSizeColumn causes OutOfMemoryError on large sheets)
        applyFixedColumnWidths(sheet, headers.length);

        log.info("Expense Allocations sheet created with {} rows, total: {}", expenseAllocations.size(), totalAllocated);
    }

    /**
     * Create Owner Payments Summary sheet showing all payment batches for this owner
     */
    private void createOwnerPaymentsSummarySheet(Workbook workbook, Long ownerId, WorkbookStyles styles) {
        log.info("Creating Owner Payments Summary sheet for owner {}", ownerId);

        Sheet sheet = workbook.createSheet("Owner Payments Summary");
        // Use shared styles (memory optimization)
        CellStyle headerStyle = styles.headerStyle;
        CellStyle dateStyle = styles.dateStyle;
        CellStyle currencyStyle = styles.currencyStyle;

        // Headers
        String[] headers = {"Batch Reference", "Payment Date", "Total Allocations", "Balance Adjustment",
                           "Total Payment", "Status", "Payment Method", "Notes"};

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Get batch references from unified_allocations
        // This is the primary source for owner payment batch allocations (both PayProp and manual)
        List<String> batchRefs = unifiedAllocationRepository.findDistinctBatchReferencesByBeneficiaryId(ownerId);
        log.info("Found {} payment batches for owner {}", batchRefs.size(), ownerId);

        int rowNum = 1;
        BigDecimal totalPayments = BigDecimal.ZERO;

        for (String batchRef : batchRefs) {
            PaymentBatch batch = paymentBatchRepository.findByBatchId(batchRef).orElse(null);
            if (batch == null) {
                log.warn("Payment batch not found for reference: {}", batchRef);
                continue;
            }

            Row row = sheet.createRow(rowNum++);

            // Batch Reference
            row.createCell(0).setCellValue(batchRef);

            // Payment Date
            Cell dateCell = row.createCell(1);
            if (batch.getPaymentDate() != null) {
                dateCell.setCellValue(java.sql.Date.valueOf(batch.getPaymentDate()));
                dateCell.setCellStyle(dateStyle);
            }

            // Total Allocations
            Cell allocCell = row.createCell(2);
            if (batch.getTotalAllocations() != null) {
                allocCell.setCellValue(batch.getTotalAllocations().doubleValue());
                allocCell.setCellStyle(currencyStyle);
            }

            // Balance Adjustment
            Cell adjCell = row.createCell(3);
            if (batch.getBalanceAdjustment() != null) {
                adjCell.setCellValue(batch.getBalanceAdjustment().doubleValue());
                adjCell.setCellStyle(currencyStyle);
            }

            // Total Payment
            Cell payCell = row.createCell(4);
            if (batch.getTotalPayment() != null) {
                payCell.setCellValue(batch.getTotalPayment().doubleValue());
                payCell.setCellStyle(currencyStyle);
                totalPayments = totalPayments.add(batch.getTotalPayment());
            }

            // Status
            row.createCell(5).setCellValue(batch.getStatus() != null ? batch.getStatus().toString() : "");

            // Payment Method
            row.createCell(6).setCellValue(batch.getPaymentMethod() != null ? batch.getPaymentMethod() : "");

            // Notes
            row.createCell(7).setCellValue(batch.getNotes() != null ? batch.getNotes() : "");
        }

        // Total row
        Row totalRow = sheet.createRow(rowNum);
        Cell totalLabel = totalRow.createCell(3);
        totalLabel.setCellValue("TOTAL:");
        totalLabel.setCellStyle(headerStyle);

        Cell totalCell = totalRow.createCell(4);
        totalCell.setCellValue(totalPayments.doubleValue());
        totalCell.setCellStyle(currencyStyle);

        // Apply fixed column widths (autoSizeColumn causes OutOfMemoryError on large sheets)
        applyFixedColumnWidths(sheet, headers.length);

        log.info("Owner Payments Summary sheet created with {} batches, total: {}", batchRefs.size(), totalPayments);
    }

    /**
     * Create PAYMENT_ALLOCATIONS sheet with flat data for all payment allocations.
     * This raw data sheet can be used for filtering/lookups by other sheets.
     * Columns: payment_batch_id, payment_date, transaction_id, transaction_date,
     *          property_name, category, type (Income/Expense), allocated_amount
     */
    private void createPaymentAllocationsSheet(Workbook workbook, Long ownerId, LocalDate startDate,
                                               LocalDate endDate, WorkbookStyles styles) {
        log.info("Creating PAYMENT_ALLOCATIONS sheet for owner {} from {} to {}", ownerId, startDate, endDate);

        Sheet sheet = workbook.createSheet("PAYMENT_ALLOCATIONS");
        CellStyle headerStyle = styles.headerStyle;
        CellStyle dateStyle = styles.dateStyle;
        CellStyle currencyStyle = styles.currencyStyle;

        // Headers
        String[] headers = {"payment_batch_id", "payment_date", "transaction_id", "transaction_date",
                           "property_name", "category", "type", "allocated_amount"};

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Get all payments with allocations for this owner (using existing extraction method)
        // We need to get ALL payments, not just period-specific
        List<PaymentWithAllocationsDTO> allPayments = dataExtractService.extractPaymentsWithAllocationsInPeriod(
            ownerId, startDate.minusYears(10), endDate.plusDays(1), startDate.minusYears(10));

        int rowNum = 1;
        for (PaymentWithAllocationsDTO payment : allPayments) {
            String batchId = payment.getBatchId();
            LocalDate paymentDate = payment.getPaymentDate();

            for (PaymentWithAllocationsDTO.AllocationLineDTO alloc : payment.getAllocations()) {
                Row row = sheet.createRow(rowNum++);

                // payment_batch_id
                row.createCell(0).setCellValue(batchId != null ? batchId : "");

                // payment_date
                Cell payDateCell = row.createCell(1);
                if (paymentDate != null) {
                    payDateCell.setCellValue(java.sql.Date.valueOf(paymentDate));
                    payDateCell.setCellStyle(dateStyle);
                }

                // transaction_id
                row.createCell(2).setCellValue(alloc.getTransactionId() != null ? alloc.getTransactionId().toString() : "");

                // transaction_date
                Cell txnDateCell = row.createCell(3);
                if (alloc.getTransactionDate() != null) {
                    txnDateCell.setCellValue(java.sql.Date.valueOf(alloc.getTransactionDate()));
                    txnDateCell.setCellStyle(dateStyle);
                }

                // property_name
                row.createCell(4).setCellValue(alloc.getPropertyName() != null ? alloc.getPropertyName() : "");

                // category
                row.createCell(5).setCellValue(alloc.getCategory() != null ? alloc.getCategory() : "");

                // type (Income or Expense) - use actual allocation_type from unified_allocations
                row.createCell(6).setCellValue(alloc.isExpense() ? "Expense" : "Income");

                // allocated_amount
                Cell amtCell = row.createCell(7);
                BigDecimal allocAmt = alloc.getAllocatedAmount() != null ? alloc.getAllocatedAmount().abs() : BigDecimal.ZERO;
                amtCell.setCellValue(allocAmt.doubleValue());
                amtCell.setCellStyle(currencyStyle);
            }
        }

        applyFixedColumnWidths(sheet, headers.length);
        log.info("PAYMENT_ALLOCATIONS sheet created with {} rows", rowNum - 1);
    }

    /**
     * Determine allocation status for reconciliation column.
     *
     * @param summary The allocation summary for the property (can be null)
     * @return Status string: PAID, PENDING, BATCHED, or NONE
     */
    private String determineAllocationStatus(LeaseAllocationSummaryDTO summary) {
        if (summary == null || summary.getAllocationCount() == 0) {
            return "NONE";  // No allocations exist
        }

        String paymentStatus = summary.getPaymentStatus();
        if (paymentStatus == null) {
            return "PENDING";
        }

        // Return the payment status directly
        // Possible values: PAID, BATCHED, PENDING
        return paymentStatus;
    }

    // ===== RECONCILIATION SHEET =====

    /**
     * Create Reconciliation sheet showing period-based payment reconciliation.
     *
     * Structure:
     * 1. BROUGHT FORWARD - Unallocated income + owner credits from prior periods
     * 2. THIS PERIOD ACTIVITY - Income received, expenses incurred
     * 3. PAYMENTS MADE - Each payment with allocation breakdown
     * 4. CARRIED FORWARD - Unallocated income + owner credits at period end
     * 5. VERIFICATION - Proof formula: B/F + Activity - Payments = C/F
     *
     * @param workbook The workbook to add the sheet to
     * @param customerId The customer ID
     * @param startDate Period start date
     * @param endDate Period end date
     * @param styles Shared workbook styles
     */
    private void createReconciliationSheet(Workbook workbook, Long customerId,
                                           LocalDate startDate, LocalDate endDate, WorkbookStyles styles) {
        log.info("Creating Reconciliation sheet for customer {} from {} to {}", customerId, startDate, endDate);

        Sheet sheet = workbook.createSheet("RECONCILIATION");
        CellStyle headerStyle = styles.headerStyle;
        CellStyle dateStyle = styles.dateStyle;
        CellStyle currencyStyle = styles.currencyStyle;
        CellStyle boldStyle = styles.boldStyle;
        CellStyle boldCurrencyStyle = styles.boldCurrencyStyle;

        int rowNum = 0;

        // ===== TITLE =====
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("PAYMENT RECONCILIATION");
        titleCell.setCellStyle(boldStyle);

        Row periodRow = sheet.createRow(rowNum++);
        periodRow.createCell(0).setCellValue("Period:");
        Cell periodDateCell = periodRow.createCell(1);
        periodDateCell.setCellValue(startDate.toString() + " to " + endDate.toString());

        rowNum++; // Blank row

        // ===== SECTION 1: BROUGHT FORWARD =====
        Row bfHeaderRow = sheet.createRow(rowNum++);
        Cell bfHeaderCell = bfHeaderRow.createCell(0);
        bfHeaderCell.setCellValue("BROUGHT FORWARD (Unallocated as of " + startDate.toString() + ")");
        bfHeaderCell.setCellStyle(headerStyle);

        // Unallocated Income (owed to owner)
        List<UnallocatedIncomeDTO> bfIncome = dataExtractService.extractUnallocatedIncomeAsOf(customerId, startDate);
        BigDecimal totalBfIncome = BigDecimal.ZERO;

        if (!bfIncome.isEmpty()) {
            Row bfIncomeHeaderRow = sheet.createRow(rowNum++);
            bfIncomeHeaderRow.createCell(0).setCellValue("Unallocated Income (owed to owner):");
            bfIncomeHeaderRow.getCell(0).setCellStyle(boldStyle);

            // Sub-headers
            Row subHeaderRow = sheet.createRow(rowNum++);
            String[] incomeHeaders = {"Date", "Property", "Tenant", "Gross", "Commission", "Net Due", "Allocated", "Unallocated"};
            for (int i = 0; i < incomeHeaders.length; i++) {
                Cell cell = subHeaderRow.createCell(i);
                cell.setCellValue(incomeHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            for (UnallocatedIncomeDTO income : bfIncome) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;

                Cell dateCell = row.createCell(col++);
                if (income.getTransactionDate() != null) {
                    dateCell.setCellValue(java.sql.Date.valueOf(income.getTransactionDate()));
                    dateCell.setCellStyle(dateStyle);
                }

                row.createCell(col++).setCellValue(income.getPropertyName() != null ? income.getPropertyName() : "");
                row.createCell(col++).setCellValue(income.getTenantName() != null ? income.getTenantName() : "");

                Cell grossCell = row.createCell(col++);
                grossCell.setCellValue(income.getGrossAmount() != null ? income.getGrossAmount().doubleValue() : 0);
                grossCell.setCellStyle(currencyStyle);

                Cell commCell = row.createCell(col++);
                commCell.setCellValue(income.getCommission() != null ? income.getCommission().doubleValue() : 0);
                commCell.setCellStyle(currencyStyle);

                Cell netCell = row.createCell(col++);
                netCell.setCellValue(income.getNetDue() != null ? income.getNetDue().doubleValue() : 0);
                netCell.setCellStyle(currencyStyle);

                Cell allocCell = row.createCell(col++);
                allocCell.setCellValue(income.getAllocatedAmount() != null ? income.getAllocatedAmount().doubleValue() : 0);
                allocCell.setCellStyle(currencyStyle);

                Cell unallocCell = row.createCell(col++);
                unallocCell.setCellValue(income.getRemainingUnallocated() != null ? income.getRemainingUnallocated().doubleValue() : 0);
                unallocCell.setCellStyle(currencyStyle);

                totalBfIncome = totalBfIncome.add(income.getRemainingUnallocated() != null ? income.getRemainingUnallocated() : BigDecimal.ZERO);
            }
        }

        // Unallocated Payments (owner credit - overpayments)
        List<UnallocatedPaymentDTO> bfPayments = dataExtractService.extractUnallocatedPaymentsAsOf(customerId, startDate);
        BigDecimal totalBfCredit = BigDecimal.ZERO;

        if (!bfPayments.isEmpty()) {
            rowNum++; // Blank row
            Row bfCreditHeaderRow = sheet.createRow(rowNum++);
            bfCreditHeaderRow.createCell(0).setCellValue("Unallocated Payments (owner credit):");
            bfCreditHeaderRow.getCell(0).setCellStyle(boldStyle);

            // Sub-headers
            Row subHeaderRow = sheet.createRow(rowNum++);
            String[] paymentHeaders = {"Date", "Batch Ref", "Total Payment", "Total Allocated", "Unallocated (Credit)"};
            for (int i = 0; i < paymentHeaders.length; i++) {
                Cell cell = subHeaderRow.createCell(i);
                cell.setCellValue(paymentHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            for (UnallocatedPaymentDTO payment : bfPayments) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;

                Cell dateCell = row.createCell(col++);
                if (payment.getPaymentDate() != null) {
                    dateCell.setCellValue(java.sql.Date.valueOf(payment.getPaymentDate()));
                    dateCell.setCellStyle(dateStyle);
                }

                row.createCell(col++).setCellValue(payment.getBatchId() != null ? payment.getBatchId() : "");

                Cell totalCell = row.createCell(col++);
                totalCell.setCellValue(payment.getTotalPayment() != null ? payment.getTotalPayment().doubleValue() : 0);
                totalCell.setCellStyle(currencyStyle);

                Cell allocCell = row.createCell(col++);
                allocCell.setCellValue(payment.getTotalAllocated() != null ? payment.getTotalAllocated().doubleValue() : 0);
                allocCell.setCellStyle(currencyStyle);

                Cell unallocCell = row.createCell(col++);
                unallocCell.setCellValue(payment.getUnallocatedAmount() != null ? payment.getUnallocatedAmount().doubleValue() : 0);
                unallocCell.setCellStyle(currencyStyle);

                totalBfCredit = totalBfCredit.add(payment.getUnallocatedAmount() != null ? payment.getUnallocatedAmount() : BigDecimal.ZERO);
            }
        }

        // B/F Summary
        rowNum++;
        int bfSummaryRow = rowNum;
        Row bfTotalRow = sheet.createRow(rowNum++);
        bfTotalRow.createCell(0).setCellValue("TOTAL BROUGHT FORWARD:");
        bfTotalRow.getCell(0).setCellStyle(boldStyle);
        bfTotalRow.createCell(1).setCellValue("Income Owed:");
        Cell bfIncomeTotal = bfTotalRow.createCell(2);
        bfIncomeTotal.setCellValue(totalBfIncome.doubleValue());
        bfIncomeTotal.setCellStyle(boldCurrencyStyle);
        bfTotalRow.createCell(3).setCellValue("Less Credit:");
        Cell bfCreditTotal = bfTotalRow.createCell(4);
        bfCreditTotal.setCellValue(totalBfCredit.negate().doubleValue());
        bfCreditTotal.setCellStyle(boldCurrencyStyle);
        bfTotalRow.createCell(5).setCellValue("Net B/F:");
        Cell bfNetTotal = bfTotalRow.createCell(6);
        bfNetTotal.setCellValue(totalBfIncome.subtract(totalBfCredit).doubleValue());
        bfNetTotal.setCellStyle(boldCurrencyStyle);

        rowNum += 2; // Blank rows

        // ===== SECTION 2: THIS PERIOD ACTIVITY =====
        Row activityHeaderRow = sheet.createRow(rowNum++);
        Cell activityHeaderCell = activityHeaderRow.createCell(0);
        activityHeaderCell.setCellValue("THIS PERIOD ACTIVITY (" + startDate.toString() + " to " + endDate.toString() + ")");
        activityHeaderCell.setCellStyle(headerStyle);

        // Income Received
        List<UnallocatedIncomeDTO> periodIncome = dataExtractService.extractIncomeReceivedInPeriod(customerId, startDate, endDate);
        BigDecimal totalPeriodIncome = BigDecimal.ZERO;

        if (!periodIncome.isEmpty()) {
            Row incomeHeaderRow = sheet.createRow(rowNum++);
            incomeHeaderRow.createCell(0).setCellValue("Income Received:");
            incomeHeaderRow.getCell(0).setCellStyle(boldStyle);

            // Sub-headers
            Row subHeaderRow = sheet.createRow(rowNum++);
            String[] incomeHeaders = {"Date", "Property", "Tenant", "Gross", "Commission", "Net Due"};
            for (int i = 0; i < incomeHeaders.length; i++) {
                Cell cell = subHeaderRow.createCell(i);
                cell.setCellValue(incomeHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            for (UnallocatedIncomeDTO income : periodIncome) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;

                Cell dateCell = row.createCell(col++);
                if (income.getTransactionDate() != null) {
                    dateCell.setCellValue(java.sql.Date.valueOf(income.getTransactionDate()));
                    dateCell.setCellStyle(dateStyle);
                }

                row.createCell(col++).setCellValue(income.getPropertyName() != null ? income.getPropertyName() : "");
                row.createCell(col++).setCellValue(income.getTenantName() != null ? income.getTenantName() : "");

                Cell grossCell = row.createCell(col++);
                grossCell.setCellValue(income.getGrossAmount() != null ? income.getGrossAmount().doubleValue() : 0);
                grossCell.setCellStyle(currencyStyle);

                Cell commCell = row.createCell(col++);
                commCell.setCellValue(income.getCommission() != null ? income.getCommission().doubleValue() : 0);
                commCell.setCellStyle(currencyStyle);

                Cell netCell = row.createCell(col++);
                netCell.setCellValue(income.getNetDue() != null ? income.getNetDue().doubleValue() : 0);
                netCell.setCellStyle(currencyStyle);

                totalPeriodIncome = totalPeriodIncome.add(income.getNetDue() != null ? income.getNetDue() : BigDecimal.ZERO);
            }
        }

        // Income subtotal
        Row incomeSubtotalRow = sheet.createRow(rowNum++);
        incomeSubtotalRow.createCell(4).setCellValue("Total Income:");
        incomeSubtotalRow.getCell(4).setCellStyle(boldStyle);
        Cell incomeSubtotalCell = incomeSubtotalRow.createCell(5);
        incomeSubtotalCell.setCellValue(totalPeriodIncome.doubleValue());
        incomeSubtotalCell.setCellStyle(boldCurrencyStyle);

        rowNum++; // Blank row

        // Expenses Incurred
        List<UnallocatedIncomeDTO> periodExpenses = dataExtractService.extractExpensesInPeriod(customerId, startDate, endDate);
        BigDecimal totalPeriodExpenses = BigDecimal.ZERO;

        if (!periodExpenses.isEmpty()) {
            Row expenseHeaderRow = sheet.createRow(rowNum++);
            expenseHeaderRow.createCell(0).setCellValue("Expenses Incurred:");
            expenseHeaderRow.getCell(0).setCellStyle(boldStyle);

            // Sub-headers
            Row subHeaderRow = sheet.createRow(rowNum++);
            String[] expenseHeaders = {"Date", "Property", "Category", "Description", "Amount"};
            for (int i = 0; i < expenseHeaders.length; i++) {
                Cell cell = subHeaderRow.createCell(i);
                cell.setCellValue(expenseHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            for (UnallocatedIncomeDTO expense : periodExpenses) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;

                Cell dateCell = row.createCell(col++);
                if (expense.getTransactionDate() != null) {
                    dateCell.setCellValue(java.sql.Date.valueOf(expense.getTransactionDate()));
                    dateCell.setCellStyle(dateStyle);
                }

                row.createCell(col++).setCellValue(expense.getPropertyName() != null ? expense.getPropertyName() : "");
                row.createCell(col++).setCellValue(expense.getCategory() != null ? expense.getCategory() : "");
                row.createCell(col++).setCellValue(expense.getTenantName() != null ? expense.getTenantName() : ""); // Description in tenant field

                Cell amountCell = row.createCell(col++);
                amountCell.setCellValue(expense.getGrossAmount() != null ? expense.getGrossAmount().doubleValue() : 0);
                amountCell.setCellStyle(currencyStyle);

                totalPeriodExpenses = totalPeriodExpenses.add(expense.getGrossAmount() != null ? expense.getGrossAmount() : BigDecimal.ZERO);
            }
        }

        // Expense subtotal
        Row expenseSubtotalRow = sheet.createRow(rowNum++);
        expenseSubtotalRow.createCell(3).setCellValue("Total Expenses:");
        expenseSubtotalRow.getCell(3).setCellStyle(boldStyle);
        Cell expenseSubtotalCell = expenseSubtotalRow.createCell(4);
        expenseSubtotalCell.setCellValue(totalPeriodExpenses.doubleValue());
        expenseSubtotalCell.setCellStyle(boldCurrencyStyle);

        // Net Activity
        rowNum++;
        int activitySummaryRow = rowNum;
        Row activityTotalRow = sheet.createRow(rowNum++);
        activityTotalRow.createCell(0).setCellValue("NET PERIOD ACTIVITY:");
        activityTotalRow.getCell(0).setCellStyle(boldStyle);
        activityTotalRow.createCell(1).setCellValue("Income:");
        Cell actIncomeCell = activityTotalRow.createCell(2);
        actIncomeCell.setCellValue(totalPeriodIncome.doubleValue());
        actIncomeCell.setCellStyle(boldCurrencyStyle);
        activityTotalRow.createCell(3).setCellValue("Expenses:");
        Cell actExpenseCell = activityTotalRow.createCell(4);
        actExpenseCell.setCellValue(totalPeriodExpenses.doubleValue());
        actExpenseCell.setCellStyle(boldCurrencyStyle);
        activityTotalRow.createCell(5).setCellValue("Net:");
        Cell actNetCell = activityTotalRow.createCell(6);
        BigDecimal netActivity = totalPeriodIncome.add(totalPeriodExpenses); // Expenses already negative
        actNetCell.setCellValue(netActivity.doubleValue());
        actNetCell.setCellStyle(boldCurrencyStyle);

        rowNum += 2; // Blank rows

        // ===== SECTION 3: PAYMENTS MADE THIS PERIOD =====
        Row paymentsHeaderRow = sheet.createRow(rowNum++);
        Cell paymentsHeaderCell = paymentsHeaderRow.createCell(0);
        paymentsHeaderCell.setCellValue("PAYMENTS MADE THIS PERIOD");
        paymentsHeaderCell.setCellStyle(headerStyle);

        List<PaymentWithAllocationsDTO> payments = dataExtractService.extractPaymentsWithAllocationsInPeriod(
            customerId, startDate, endDate, startDate);
        BigDecimal totalPaymentsMade = BigDecimal.ZERO;
        BigDecimal totalPaymentsAllocated = BigDecimal.ZERO;

        for (PaymentWithAllocationsDTO payment : payments) {
            rowNum++; // Blank row before each payment

            // Payment header
            Row paymentRow = sheet.createRow(rowNum++);
            paymentRow.createCell(0).setCellValue("Payment:");
            paymentRow.getCell(0).setCellStyle(boldStyle);
            paymentRow.createCell(1).setCellValue(payment.getBatchId() != null ? payment.getBatchId() : "");

            Cell payDateCell = paymentRow.createCell(2);
            if (payment.getPaymentDate() != null) {
                payDateCell.setCellValue(java.sql.Date.valueOf(payment.getPaymentDate()));
                payDateCell.setCellStyle(dateStyle);
            }

            paymentRow.createCell(3).setCellValue("Amount:");
            Cell payAmountCell = paymentRow.createCell(4);
            payAmountCell.setCellValue(payment.getTotalPayment() != null ? payment.getTotalPayment().doubleValue() : 0);
            payAmountCell.setCellStyle(boldCurrencyStyle);

            totalPaymentsMade = totalPaymentsMade.add(payment.getTotalPayment() != null ? payment.getTotalPayment() : BigDecimal.ZERO);

            // Allocation details
            if (!payment.getAllocations().isEmpty()) {
                Row allocHeaderRow = sheet.createRow(rowNum++);
                allocHeaderRow.createCell(1).setCellValue("Covers:");
                allocHeaderRow.getCell(1).setCellStyle(boldStyle);

                // Allocation sub-headers
                Row allocSubHeaderRow = sheet.createRow(rowNum++);
                String[] allocHeaders = {"", "Date", "Property", "Category", "Description", "Amount", "Note"};
                for (int i = 0; i < allocHeaders.length; i++) {
                    Cell cell = allocSubHeaderRow.createCell(i);
                    cell.setCellValue(allocHeaders[i]);
                    if (i > 0) cell.setCellStyle(headerStyle);
                }

                for (PaymentWithAllocationsDTO.AllocationLineDTO alloc : payment.getAllocations()) {
                    Row allocRow = sheet.createRow(rowNum++);
                    int col = 1; // Start at column B

                    Cell allocDateCell = allocRow.createCell(col++);
                    if (alloc.getTransactionDate() != null) {
                        allocDateCell.setCellValue(java.sql.Date.valueOf(alloc.getTransactionDate()));
                        allocDateCell.setCellStyle(dateStyle);
                    }

                    allocRow.createCell(col++).setCellValue(alloc.getPropertyName() != null ? alloc.getPropertyName() : "");
                    allocRow.createCell(col++).setCellValue(alloc.getCategory() != null ? alloc.getCategory() : "");
                    allocRow.createCell(col++).setCellValue(alloc.getDescription() != null ?
                        (alloc.getDescription().length() > 50 ? alloc.getDescription().substring(0, 47) + "..." : alloc.getDescription()) : "");

                    Cell allocAmtCell = allocRow.createCell(col++);
                    allocAmtCell.setCellValue(alloc.getAllocatedAmount() != null ? alloc.getAllocatedAmount().doubleValue() : 0);
                    allocAmtCell.setCellStyle(currencyStyle);

                    totalPaymentsAllocated = totalPaymentsAllocated.add(
                        alloc.getAllocatedAmount() != null ? alloc.getAllocatedAmount().abs() : BigDecimal.ZERO);

                    // Note column - show if partial or from prior period
                    String note = "";
                    if (alloc.isPartial()) note += "SPLIT ";
                    if (alloc.isFromPriorPeriod()) note += "PRIOR ";
                    allocRow.createCell(col++).setCellValue(note.trim());
                }
            }

            // Unallocated portion of this payment
            if (payment.getUnallocatedAmount() != null && payment.getUnallocatedAmount().compareTo(BigDecimal.valueOf(0.01)) > 0) {
                Row unallocRow = sheet.createRow(rowNum++);
                unallocRow.createCell(1).setCellValue("Unallocated (Owner Credit):");
                Cell unallocAmtCell = unallocRow.createCell(5);
                unallocAmtCell.setCellValue(payment.getUnallocatedAmount().doubleValue());
                unallocAmtCell.setCellStyle(currencyStyle);
            }
        }

        // Payments Summary
        rowNum++;
        int paymentsSummaryRow = rowNum;
        Row paymentsTotalRow = sheet.createRow(rowNum++);
        paymentsTotalRow.createCell(0).setCellValue("TOTAL PAYMENTS:");
        paymentsTotalRow.getCell(0).setCellStyle(boldStyle);
        paymentsTotalRow.createCell(1).setCellValue("Made:");
        Cell payMadeCell = paymentsTotalRow.createCell(2);
        payMadeCell.setCellValue(totalPaymentsMade.doubleValue());
        payMadeCell.setCellStyle(boldCurrencyStyle);
        paymentsTotalRow.createCell(3).setCellValue("Allocated:");
        Cell payAllocCell = paymentsTotalRow.createCell(4);
        payAllocCell.setCellValue(totalPaymentsAllocated.doubleValue());
        payAllocCell.setCellStyle(boldCurrencyStyle);

        rowNum += 2; // Blank rows

        // ===== SECTION 4: CARRIED FORWARD =====
        Row cfHeaderRow = sheet.createRow(rowNum++);
        Cell cfHeaderCell = cfHeaderRow.createCell(0);
        cfHeaderCell.setCellValue("CARRIED FORWARD (Unallocated as of " + endDate.toString() + ")");
        cfHeaderCell.setCellStyle(headerStyle);

        // Unallocated Income at end
        List<UnallocatedIncomeDTO> cfIncome = dataExtractService.extractUnallocatedIncomeAsOfEndDate(customerId, endDate);
        BigDecimal totalCfIncome = BigDecimal.ZERO;

        if (!cfIncome.isEmpty()) {
            Row cfIncomeHeaderRow = sheet.createRow(rowNum++);
            cfIncomeHeaderRow.createCell(0).setCellValue("Unallocated Income (owed to owner):");
            cfIncomeHeaderRow.getCell(0).setCellStyle(boldStyle);

            // Sub-headers
            Row subHeaderRow = sheet.createRow(rowNum++);
            String[] incomeHeaders = {"Date", "Property", "Tenant", "Gross", "Commission", "Net Due", "Allocated", "Unallocated"};
            for (int i = 0; i < incomeHeaders.length; i++) {
                Cell cell = subHeaderRow.createCell(i);
                cell.setCellValue(incomeHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            for (UnallocatedIncomeDTO income : cfIncome) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;

                Cell dateCell = row.createCell(col++);
                if (income.getTransactionDate() != null) {
                    dateCell.setCellValue(java.sql.Date.valueOf(income.getTransactionDate()));
                    dateCell.setCellStyle(dateStyle);
                }

                row.createCell(col++).setCellValue(income.getPropertyName() != null ? income.getPropertyName() : "");
                row.createCell(col++).setCellValue(income.getTenantName() != null ? income.getTenantName() : "");

                Cell grossCell = row.createCell(col++);
                grossCell.setCellValue(income.getGrossAmount() != null ? income.getGrossAmount().doubleValue() : 0);
                grossCell.setCellStyle(currencyStyle);

                Cell commCell = row.createCell(col++);
                commCell.setCellValue(income.getCommission() != null ? income.getCommission().doubleValue() : 0);
                commCell.setCellStyle(currencyStyle);

                Cell netCell = row.createCell(col++);
                netCell.setCellValue(income.getNetDue() != null ? income.getNetDue().doubleValue() : 0);
                netCell.setCellStyle(currencyStyle);

                Cell allocCell = row.createCell(col++);
                allocCell.setCellValue(income.getAllocatedAmount() != null ? income.getAllocatedAmount().doubleValue() : 0);
                allocCell.setCellStyle(currencyStyle);

                Cell unallocCell = row.createCell(col++);
                unallocCell.setCellValue(income.getRemainingUnallocated() != null ? income.getRemainingUnallocated().doubleValue() : 0);
                unallocCell.setCellStyle(currencyStyle);

                totalCfIncome = totalCfIncome.add(income.getRemainingUnallocated() != null ? income.getRemainingUnallocated() : BigDecimal.ZERO);
            }
        }

        // Unallocated Payments at end (owner credit)
        List<UnallocatedPaymentDTO> cfPayments = dataExtractService.extractUnallocatedPaymentsAsOfEndDate(customerId, endDate);
        BigDecimal totalCfCredit = BigDecimal.ZERO;

        if (!cfPayments.isEmpty()) {
            rowNum++; // Blank row
            Row cfCreditHeaderRow = sheet.createRow(rowNum++);
            cfCreditHeaderRow.createCell(0).setCellValue("Unallocated Payments (owner credit):");
            cfCreditHeaderRow.getCell(0).setCellStyle(boldStyle);

            // Sub-headers
            Row subHeaderRow = sheet.createRow(rowNum++);
            String[] paymentHeaders = {"Date", "Batch Ref", "Total Payment", "Total Allocated", "Unallocated (Credit)"};
            for (int i = 0; i < paymentHeaders.length; i++) {
                Cell cell = subHeaderRow.createCell(i);
                cell.setCellValue(paymentHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            for (UnallocatedPaymentDTO payment : cfPayments) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;

                Cell dateCell = row.createCell(col++);
                if (payment.getPaymentDate() != null) {
                    dateCell.setCellValue(java.sql.Date.valueOf(payment.getPaymentDate()));
                    dateCell.setCellStyle(dateStyle);
                }

                row.createCell(col++).setCellValue(payment.getBatchId() != null ? payment.getBatchId() : "");

                Cell totalCell = row.createCell(col++);
                totalCell.setCellValue(payment.getTotalPayment() != null ? payment.getTotalPayment().doubleValue() : 0);
                totalCell.setCellStyle(currencyStyle);

                Cell allocCell = row.createCell(col++);
                allocCell.setCellValue(payment.getTotalAllocated() != null ? payment.getTotalAllocated().doubleValue() : 0);
                allocCell.setCellStyle(currencyStyle);

                Cell unallocCell = row.createCell(col++);
                unallocCell.setCellValue(payment.getUnallocatedAmount() != null ? payment.getUnallocatedAmount().doubleValue() : 0);
                unallocCell.setCellStyle(currencyStyle);

                totalCfCredit = totalCfCredit.add(payment.getUnallocatedAmount() != null ? payment.getUnallocatedAmount() : BigDecimal.ZERO);
            }
        }

        // C/F Summary
        rowNum++;
        int cfSummaryRow = rowNum;
        Row cfTotalRow = sheet.createRow(rowNum++);
        cfTotalRow.createCell(0).setCellValue("TOTAL CARRIED FORWARD:");
        cfTotalRow.getCell(0).setCellStyle(boldStyle);
        cfTotalRow.createCell(1).setCellValue("Income Owed:");
        Cell cfIncomeTotal = cfTotalRow.createCell(2);
        cfIncomeTotal.setCellValue(totalCfIncome.doubleValue());
        cfIncomeTotal.setCellStyle(boldCurrencyStyle);
        cfTotalRow.createCell(3).setCellValue("Less Credit:");
        Cell cfCreditTotal = cfTotalRow.createCell(4);
        cfCreditTotal.setCellValue(totalCfCredit.negate().doubleValue());
        cfCreditTotal.setCellStyle(boldCurrencyStyle);
        cfTotalRow.createCell(5).setCellValue("Net C/F:");
        Cell cfNetTotal = cfTotalRow.createCell(6);
        cfNetTotal.setCellValue(totalCfIncome.subtract(totalCfCredit).doubleValue());
        cfNetTotal.setCellStyle(boldCurrencyStyle);

        rowNum += 2; // Blank rows

        // ===== SECTION 5: VERIFICATION =====
        Row verifyHeaderRow = sheet.createRow(rowNum++);
        Cell verifyHeaderCell = verifyHeaderRow.createCell(0);
        verifyHeaderCell.setCellValue("VERIFICATION");
        verifyHeaderCell.setCellStyle(headerStyle);

        BigDecimal netBf = totalBfIncome.subtract(totalBfCredit);
        BigDecimal netCf = totalCfIncome.subtract(totalCfCredit);
        BigDecimal calculatedCf = netBf.add(netActivity).subtract(totalPaymentsAllocated);
        BigDecimal variance = netCf.subtract(calculatedCf);

        Row proofRow = sheet.createRow(rowNum++);
        proofRow.createCell(0).setCellValue("B/F + Activity - Payments = Expected C/F");

        Row calcRow = sheet.createRow(rowNum++);
        Cell bfCell = calcRow.createCell(0);
        bfCell.setCellValue(netBf.doubleValue());
        bfCell.setCellStyle(currencyStyle);
        calcRow.createCell(1).setCellValue("+");
        Cell actCell = calcRow.createCell(2);
        actCell.setCellValue(netActivity.doubleValue());
        actCell.setCellStyle(currencyStyle);
        calcRow.createCell(3).setCellValue("-");
        Cell payCell = calcRow.createCell(4);
        payCell.setCellValue(totalPaymentsAllocated.doubleValue());
        payCell.setCellStyle(currencyStyle);
        calcRow.createCell(5).setCellValue("=");
        Cell expectedCfCell = calcRow.createCell(6);
        expectedCfCell.setCellValue(calculatedCf.doubleValue());
        expectedCfCell.setCellStyle(boldCurrencyStyle);

        Row comparisonRow = sheet.createRow(rowNum++);
        comparisonRow.createCell(0).setCellValue("Actual C/F:");
        Cell actualCfCell = comparisonRow.createCell(1);
        actualCfCell.setCellValue(netCf.doubleValue());
        actualCfCell.setCellStyle(boldCurrencyStyle);
        comparisonRow.createCell(2).setCellValue("Variance:");
        Cell varianceCell = comparisonRow.createCell(3);
        varianceCell.setCellValue(variance.doubleValue());
        varianceCell.setCellStyle(boldCurrencyStyle);

        if (variance.abs().compareTo(BigDecimal.valueOf(0.01)) > 0) {
            comparisonRow.createCell(4).setCellValue("⚠️ CHECK REQUIRED");
        } else {
            comparisonRow.createCell(4).setCellValue("✓ BALANCED");
        }

        // Apply column widths
        applyFixedColumnWidths(sheet, 8);

        log.info("Reconciliation sheet created with {} rows", rowNum);
    }

    /**
     * Apply fixed column widths to avoid memory-intensive autoSizeColumn()
     * autoSizeColumn() causes OutOfMemoryError on large sheets because it:
     * 1. Iterates through ALL cells in the column
     * 2. Creates font metrics objects
     * 3. Caches rendered text widths
     *
     * Fixed widths are much faster and use minimal memory.
     *
     * @param sheet The sheet to apply widths to
     * @param columnCount Number of columns to size
     */
    private void applyFixedColumnWidths(Sheet sheet, int columnCount) {
        // Default width in characters (Excel uses 1/256th of character width)
        int defaultWidth = 15 * 256;  // 15 characters
        int narrowWidth = 10 * 256;   // 10 characters for IDs, dates
        int wideWidth = 25 * 256;     // 25 characters for names, descriptions
        int currencyWidth = 12 * 256; // 12 characters for currency values

        for (int i = 0; i < columnCount; i++) {
            // Apply width based on column position patterns
            // Most sheets have: ID, reference, name, dates, amounts...
            if (i == 0) {
                sheet.setColumnWidth(i, narrowWidth);  // First column usually ID or reference
            } else if (i == 1 || i == 2) {
                sheet.setColumnWidth(i, wideWidth);    // Names, property names
            } else {
                sheet.setColumnWidth(i, defaultWidth); // Everything else
            }
        }
    }
}

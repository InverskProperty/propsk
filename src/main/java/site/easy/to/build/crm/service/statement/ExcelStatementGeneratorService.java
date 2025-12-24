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

        log.info("📄 Creating OWNER_PAYMENTS sheet...");
        sheetStart = System.currentTimeMillis();
        createOwnerPaymentsSheet(workbook, leaseMaster, startDate, endDate, styles);
        log.info("✅ OWNER_PAYMENTS sheet created in {}ms", System.currentTimeMillis() - sheetStart);
        logMemoryUsage("OWNER_PAYMENTS_SHEET");

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

        log.info("📄 Creating OWNER_PAYMENTS sheet...");
        sheetStart = System.currentTimeMillis();
        createOwnerPaymentsSheet(workbook, leaseMaster, startDate, endDate, styles);
        log.info("✅ OWNER_PAYMENTS sheet created in {}ms", System.currentTimeMillis() - sheetStart);

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
        log.info("✅ RENT_RECEIVED created in {}ms", System.currentTimeMillis() - sheetStart);

        sheetStart = System.currentTimeMillis();
        createExpensesSheet(workbook, leaseMaster, startDate, endDate, styles);
        log.info("✅ EXPENSES created in {}ms", System.currentTimeMillis() - sheetStart);

        sheetStart = System.currentTimeMillis();
        createOwnerPaymentsSheet(workbook, leaseMaster, startDate, endDate, styles);
        log.info("✅ OWNER_PAYMENTS created in {}ms", System.currentTimeMillis() - sheetStart);

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
        log.info("✅ RENT_RECEIVED created in {}ms", System.currentTimeMillis() - sheetStart);

        // GC hint after rent sheets
        requestGC("RENT_SHEETS");

        sheetStart = System.currentTimeMillis();
        createExpensesSheet(workbook, leaseMaster, startDate, endDate, styles);
        log.info("✅ EXPENSES created in {}ms", System.currentTimeMillis() - sheetStart);

        sheetStart = System.currentTimeMillis();
        createOwnerPaymentsSheet(workbook, leaseMaster, startDate, endDate, styles);
        log.info("✅ OWNER_PAYMENTS created in {}ms", System.currentTimeMillis() - sheetStart);

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
     * Calculate the total rent due for a lease during a specific period.
     * Uses the RENT IN ADVANCE model - full cycle rent is due when a cycle STARTS.
     *
     * For monthly leases: rent is due for each month the lease is active.
     * For multi-month leases: rent is due when a cycle anniversary falls in the period.
     *
     * Handles proration when lease ends mid-cycle.
     *
     * @param lease The lease master DTO with lease details
     * @param periodStart Start of the statement period
     * @param periodEnd End of the statement period
     * @return Total rent due for this lease in the period
     */
    private java.math.BigDecimal calculateRentDueForPeriod(LeaseMasterDTO lease, LocalDate periodStart, LocalDate periodEnd) {
        LocalDate leaseStart = lease.getStartDate();
        LocalDate leaseEnd = lease.getEndDate();
        java.math.BigDecimal monthlyRent = lease.getMonthlyRent();
        int cycleMonths = lease.getFrequencyMonths() != null ? lease.getFrequencyMonths() : 1;

        // Handle null or invalid inputs
        if (leaseStart == null || monthlyRent == null || periodStart == null || periodEnd == null) {
            return java.math.BigDecimal.ZERO;
        }

        // If lease hasn't started yet, no rent due
        if (leaseStart.isAfter(periodEnd)) {
            return java.math.BigDecimal.ZERO;
        }

        // If lease ended before period started, no rent due
        if (leaseEnd != null && leaseEnd.isBefore(periodStart)) {
            return java.math.BigDecimal.ZERO;
        }

        java.math.BigDecimal totalRentDue = java.math.BigDecimal.ZERO;

        if (cycleMonths == 1) {
            // Monthly billing - iterate through each month in the period
            YearMonth currentMonth = YearMonth.from(periodStart);
            YearMonth endMonth = YearMonth.from(periodEnd);

            while (!currentMonth.isAfter(endMonth)) {
                LocalDate monthStart = currentMonth.atDay(1);
                LocalDate monthEnd = currentMonth.atEndOfMonth();

                // Check if lease is active in this month
                boolean leaseActiveInMonth = !leaseStart.isAfter(monthEnd) &&
                    (leaseEnd == null || !leaseEnd.isBefore(monthStart));

                if (leaseActiveInMonth) {
                    // Calculate effective dates for this month
                    LocalDate effectiveStart = leaseStart.isAfter(monthStart) ? leaseStart : monthStart;
                    LocalDate effectiveEnd = (leaseEnd != null && leaseEnd.isBefore(monthEnd)) ? leaseEnd : monthEnd;

                    // Check if full month or partial
                    if (effectiveStart.equals(monthStart) && effectiveEnd.equals(monthEnd)) {
                        // Full month
                        totalRentDue = totalRentDue.add(monthlyRent);
                    } else {
                        // Partial month - prorate
                        int daysInMonth = monthEnd.getDayOfMonth();
                        int leaseDays = (int) java.time.temporal.ChronoUnit.DAYS.between(effectiveStart, effectiveEnd) + 1;
                        double proratedRent = monthlyRent.doubleValue() * leaseDays / daysInMonth;
                        totalRentDue = totalRentDue.add(java.math.BigDecimal.valueOf(Math.round(proratedRent * 100.0) / 100.0));
                    }
                }

                currentMonth = currentMonth.plusMonths(1);
            }
        } else {
            // Multi-month billing - rent is due when a cycle starts in the period
            long cyclesInPeriod = dataExtractService.countCycleStartDatesInPeriod(
                leaseStart, periodStart, periodEnd, cycleMonths);

            if (cyclesInPeriod > 0) {
                // Find the cycle start date to check for proration
                LocalDate cycleStartDate = findCycleStartInPeriod(leaseStart, periodStart, periodEnd, cycleMonths);
                if (cycleStartDate != null) {
                    LocalDate cycleEndDate = cycleStartDate.plusMonths(cycleMonths).minusDays(1);

                    // Check if lease ends mid-cycle
                    if (leaseEnd != null && leaseEnd.isBefore(cycleEndDate)) {
                        // Lease ends mid-cycle - prorate
                        double monthlyRate = monthlyRent.doubleValue() / cycleMonths;
                        long fullMonths = java.time.temporal.ChronoUnit.MONTHS.between(cycleStartDate, leaseEnd.plusDays(1));
                        LocalDate lastFullMonthEnd = cycleStartDate.plusMonths(fullMonths);
                        long remainingDays = java.time.temporal.ChronoUnit.DAYS.between(lastFullMonthEnd, leaseEnd) + 1;
                        int daysInFinalMonth = leaseEnd.lengthOfMonth();

                        double proratedRent = (fullMonths * monthlyRate) + ((double) remainingDays / daysInFinalMonth * monthlyRate);
                        totalRentDue = java.math.BigDecimal.valueOf(Math.round(proratedRent * 100.0) / 100.0);
                    } else {
                        // Full cycle rent
                        totalRentDue = monthlyRent.multiply(java.math.BigDecimal.valueOf(cyclesInPeriod));
                    }
                }
            }
        }

        return totalRentDue;
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
     * Create RENT_RECEIVED sheet with batch-based structure.
     * One row per batch per lease, showing up to 4 rent payments per batch.
     * Only includes batched payments (excludes pending).
     * Filters by owner_payment_date (paidDate), not transaction date.
     */
    private void createRentReceivedSheet(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                        LocalDate startDate, LocalDate endDate, WorkbookStyles styles) {
        log.info("Creating RENT_RECEIVED sheet (batch-based structure - one row per batch per lease)");

        Sheet sheet = workbook.createSheet("RENT_RECEIVED");

        // Header row - batch-based structure with up to 4 rent payments
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_id", "lease_reference", "property_name",
            "batch_id", "owner_payment_date",
            "rent_1_date", "rent_1_amount",
            "rent_2_date", "rent_2_amount",
            "rent_3_date", "rent_3_amount",
            "rent_4_date", "rent_4_amount",
            "total_rent"
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

        // Generate one row per batch per lease
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();

            // Query from lease start to capture all historical batches for opening balance
            LocalDate queryStart = leaseStart != null && leaseStart.isBefore(startDate) ? leaseStart : startDate;

            // Get rent payments grouped by batch, filtered by paidDate
            Map<String, List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO>> rentByBatch =
                dataExtractService.extractRentReceivedByBatch(lease.getLeaseId(), queryStart, endDate);

            // Create one row per batch
            for (Map.Entry<String, List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO>> entry : rentByBatch.entrySet()) {
                String batchId = entry.getKey();
                List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> payments = entry.getValue();

                if (payments.isEmpty()) continue;

                Row row = sheet.createRow(rowNum);
                int col = 0;

                // Column A: lease_id
                row.createCell(col++).setCellValue(lease.getLeaseId());

                // Column B: lease_reference
                row.createCell(col++).setCellValue(lease.getLeaseReference());

                // Column C: property_name
                row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");

                // Column D: batch_id
                row.createCell(col++).setCellValue(batchId != null ? batchId : "");

                // Column E: owner_payment_date (paidDate from first payment)
                Cell ownerPaymentDateCell = row.createCell(col++);
                LocalDate ownerPaymentDate = payments.get(0).getPaidDate();
                if (ownerPaymentDate != null) {
                    ownerPaymentDateCell.setCellValue(ownerPaymentDate);
                    ownerPaymentDateCell.setCellStyle(dateStyle);
                }

                // Columns F-M: rent_1 through rent_4 (date and amount for each)
                BigDecimal totalRent = BigDecimal.ZERO;
                for (int i = 0; i < 4; i++) {
                    if (i < payments.size()) {
                        site.easy.to.build.crm.dto.statement.PaymentDetailDTO payment = payments.get(i);

                        // rent_N_date
                        Cell rentDateCell = row.createCell(col++);
                        if (payment.getPaymentDate() != null) {
                            rentDateCell.setCellValue(payment.getPaymentDate());
                            rentDateCell.setCellStyle(dateStyle);
                        }

                        // rent_N_amount
                        Cell rentAmountCell = row.createCell(col++);
                        if (payment.getAmount() != null) {
                            rentAmountCell.setCellValue(payment.getAmount().doubleValue());
                            rentAmountCell.setCellStyle(currencyStyle);
                            totalRent = totalRent.add(payment.getAmount());
                        }
                    } else {
                        // Empty cells for missing payments
                        row.createCell(col++); // date
                        row.createCell(col++); // amount
                    }
                }

                // Column N: total_rent (sum of all rent payments in this batch)
                Cell totalRentCell = row.createCell(col++);
                totalRentCell.setCellValue(totalRent.doubleValue());
                totalRentCell.setCellStyle(currencyStyle);

                rowNum++;
            }
        }

        // Apply fixed column widths
        applyFixedColumnWidths(sheet, headers.length);

        log.info("RENT_RECEIVED sheet created with {} rows (batch-based structure)", rowNum - 1);
    }

    /**
     * Create EXPENSES sheet with batch-based structure.
     * One row per batch per lease, showing up to 4 expenses per batch.
     * Only includes batched expenses (excludes pending).
     * Filters by owner_payment_date (paidDate), not transaction date.
     */
    private void createExpensesSheet(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                     LocalDate startDate, LocalDate endDate, WorkbookStyles styles) {
        log.info("Creating EXPENSES sheet (batch-based structure - one row per batch per lease)");

        Sheet sheet = workbook.createSheet("EXPENSES");

        // Header row - batch-based structure with up to 4 expenses
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_id", "lease_reference", "property_name",
            "batch_id", "owner_payment_date",
            "expense_1_date", "expense_1_amount", "expense_1_category",
            "expense_2_date", "expense_2_amount", "expense_2_category",
            "expense_3_date", "expense_3_amount", "expense_3_category",
            "expense_4_date", "expense_4_amount", "expense_4_category",
            "total_expenses"
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

        // Generate one row per batch per lease
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();

            // Query from lease start to capture all historical batches
            LocalDate queryStart = leaseStart != null && leaseStart.isBefore(startDate) ? leaseStart : startDate;

            // Get expenses grouped by batch, filtered by paidDate
            Map<String, List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO>> expensesByBatch =
                dataExtractService.extractExpensesByBatch(lease.getLeaseId(), queryStart, endDate);

            // Create one row per batch
            for (Map.Entry<String, List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO>> entry : expensesByBatch.entrySet()) {
                String batchId = entry.getKey();
                List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> expenses = entry.getValue();

                if (expenses.isEmpty()) continue;

                Row row = sheet.createRow(rowNum);
                int col = 0;

                // Column A: lease_id
                row.createCell(col++).setCellValue(lease.getLeaseId());

                // Column B: lease_reference
                row.createCell(col++).setCellValue(lease.getLeaseReference());

                // Column C: property_name
                row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");

                // Column D: batch_id
                row.createCell(col++).setCellValue(batchId != null ? batchId : "");

                // Column E: owner_payment_date (paidDate from first expense)
                Cell ownerPaymentDateCell = row.createCell(col++);
                LocalDate ownerPaymentDate = expenses.get(0).getPaidDate();
                if (ownerPaymentDate != null) {
                    ownerPaymentDateCell.setCellValue(ownerPaymentDate);
                    ownerPaymentDateCell.setCellStyle(dateStyle);
                }

                // Columns F-Q: expense_1 through expense_4 (date, amount, category for each)
                BigDecimal totalExpenses = BigDecimal.ZERO;
                for (int i = 0; i < 4; i++) {
                    if (i < expenses.size()) {
                        site.easy.to.build.crm.dto.statement.PaymentDetailDTO expense = expenses.get(i);

                        // expense_N_date
                        Cell expenseDateCell = row.createCell(col++);
                        if (expense.getPaymentDate() != null) {
                            expenseDateCell.setCellValue(expense.getPaymentDate());
                            expenseDateCell.setCellStyle(dateStyle);
                        }

                        // expense_N_amount
                        Cell expenseAmountCell = row.createCell(col++);
                        if (expense.getAmount() != null) {
                            expenseAmountCell.setCellValue(expense.getAmount().doubleValue());
                            expenseAmountCell.setCellStyle(currencyStyle);
                            totalExpenses = totalExpenses.add(expense.getAmount());
                        }

                        // expense_N_category
                        row.createCell(col++).setCellValue(expense.getCategory() != null ? expense.getCategory() : "");
                    } else {
                        // Empty cells for missing expenses
                        row.createCell(col++); // date
                        row.createCell(col++); // amount
                        row.createCell(col++); // category
                    }
                }

                // Column R: total_expenses (sum of all expenses in this batch)
                Cell totalExpensesCell = row.createCell(col++);
                totalExpensesCell.setCellValue(totalExpenses.doubleValue());
                totalExpensesCell.setCellStyle(currencyStyle);

                rowNum++;
            }
        }

        // Apply fixed column widths
        applyFixedColumnWidths(sheet, headers.length);

        log.info("EXPENSES sheet created with {} rows (batch-based structure)", rowNum - 1);
    }

    /**
     * Create OWNER_PAYMENTS sheet - combined view of rent, commission, and expenses per batch.
     * One row per batch per lease showing all payment components.
     * Only includes batched payments (excludes pending).
     * Filters by owner_payment_date (paidDate), not transaction date.
     */
    private void createOwnerPaymentsSheet(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                          LocalDate startDate, LocalDate endDate, WorkbookStyles styles) {
        log.info("Creating OWNER_PAYMENTS sheet (combined batch view - rent + commission + expenses)");

        Sheet sheet = workbook.createSheet("OWNER_PAYMENTS");

        // Header row - combined structure
        Row header = sheet.createRow(0);
        String[] headers = {
            // Identifiers
            "lease_id", "lease_reference", "property_name",
            "batch_id", "owner_payment_date",
            // Rent payments (up to 4)
            "rent_1_date", "rent_1_amount", "rent_1_commission",
            "rent_2_date", "rent_2_amount", "rent_2_commission",
            "rent_3_date", "rent_3_amount", "rent_3_commission",
            "rent_4_date", "rent_4_amount", "rent_4_commission",
            // Rent totals
            "total_rent", "total_commission",
            // Expenses (up to 4)
            "expense_1_date", "expense_1_amount", "expense_1_category",
            "expense_2_date", "expense_2_amount", "expense_2_category",
            "expense_3_date", "expense_3_amount", "expense_3_category",
            "expense_4_date", "expense_4_amount", "expense_4_category",
            // Expense total
            "total_expenses",
            // Net
            "net_to_owner"
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

        // Generate one row per batch per lease
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();

            // Query from lease start to capture all historical batches
            LocalDate queryStart = leaseStart != null && leaseStart.isBefore(startDate) ? leaseStart : startDate;

            // Get batch payment groups (combines rent + expenses per batch)
            List<site.easy.to.build.crm.dto.statement.BatchPaymentGroupDTO> batchGroups =
                dataExtractService.extractBatchPaymentGroups(lease, queryStart, endDate);

            for (site.easy.to.build.crm.dto.statement.BatchPaymentGroupDTO group : batchGroups) {
                Row row = sheet.createRow(rowNum);
                int col = 0;

                // Column A: lease_id
                row.createCell(col++).setCellValue(group.getLeaseId());

                // Column B: lease_reference
                row.createCell(col++).setCellValue(group.getLeaseReference());

                // Column C: property_name
                row.createCell(col++).setCellValue(group.getPropertyName() != null ? group.getPropertyName() : "");

                // Column D: batch_id
                row.createCell(col++).setCellValue(group.getBatchId() != null ? group.getBatchId() : "");

                // Column E: owner_payment_date
                Cell ownerPaymentDateCell = row.createCell(col++);
                if (group.getOwnerPaymentDate() != null) {
                    ownerPaymentDateCell.setCellValue(group.getOwnerPaymentDate());
                    ownerPaymentDateCell.setCellStyle(dateStyle);
                }

                // Get commission rate for this lease
                BigDecimal commissionRate = group.getCommissionRate();
                if (commissionRate == null) {
                    commissionRate = BigDecimal.ZERO;
                }

                // Columns F-Q: rent_1 through rent_4 (date, amount, commission for each)
                BigDecimal totalRent = BigDecimal.ZERO;
                BigDecimal totalCommission = BigDecimal.ZERO;
                for (int i = 0; i < 4; i++) {
                    site.easy.to.build.crm.dto.statement.PaymentDetailDTO rent = group.getRentPayment(i);
                    if (rent != null) {
                        // rent_N_date
                        Cell rentDateCell = row.createCell(col++);
                        if (rent.getPaymentDate() != null) {
                            rentDateCell.setCellValue(rent.getPaymentDate());
                            rentDateCell.setCellStyle(dateStyle);
                        }

                        // rent_N_amount
                        Cell rentAmountCell = row.createCell(col++);
                        BigDecimal rentAmount = rent.getAmount() != null ? rent.getAmount() : BigDecimal.ZERO;
                        rentAmountCell.setCellValue(rentAmount.doubleValue());
                        rentAmountCell.setCellStyle(currencyStyle);
                        totalRent = totalRent.add(rentAmount);

                        // rent_N_commission
                        Cell commissionCell = row.createCell(col++);
                        BigDecimal commission = rentAmount.multiply(commissionRate);
                        commissionCell.setCellValue(commission.doubleValue());
                        commissionCell.setCellStyle(currencyStyle);
                        totalCommission = totalCommission.add(commission);
                    } else {
                        // Empty cells for missing rent payments
                        row.createCell(col++); // date
                        row.createCell(col++); // amount
                        row.createCell(col++); // commission
                    }
                }

                // Column R: total_rent
                Cell totalRentCell = row.createCell(col++);
                totalRentCell.setCellValue(totalRent.doubleValue());
                totalRentCell.setCellStyle(currencyStyle);

                // Column S: total_commission
                Cell totalCommissionCell = row.createCell(col++);
                totalCommissionCell.setCellValue(totalCommission.doubleValue());
                totalCommissionCell.setCellStyle(currencyStyle);

                // Columns T-AE: expense_1 through expense_4 (date, amount, category for each)
                BigDecimal totalExpenses = BigDecimal.ZERO;
                for (int i = 0; i < 4; i++) {
                    site.easy.to.build.crm.dto.statement.PaymentDetailDTO expense = group.getExpense(i);
                    if (expense != null) {
                        // expense_N_date
                        Cell expenseDateCell = row.createCell(col++);
                        if (expense.getPaymentDate() != null) {
                            expenseDateCell.setCellValue(expense.getPaymentDate());
                            expenseDateCell.setCellStyle(dateStyle);
                        }

                        // expense_N_amount
                        Cell expenseAmountCell = row.createCell(col++);
                        BigDecimal expenseAmount = expense.getAmount() != null ? expense.getAmount() : BigDecimal.ZERO;
                        expenseAmountCell.setCellValue(expenseAmount.doubleValue());
                        expenseAmountCell.setCellStyle(currencyStyle);
                        totalExpenses = totalExpenses.add(expenseAmount);

                        // expense_N_category
                        row.createCell(col++).setCellValue(expense.getCategory() != null ? expense.getCategory() : "");
                    } else {
                        // Empty cells for missing expenses
                        row.createCell(col++); // date
                        row.createCell(col++); // amount
                        row.createCell(col++); // category
                    }
                }

                // Column AF: total_expenses
                Cell totalExpensesCell = row.createCell(col++);
                totalExpensesCell.setCellValue(totalExpenses.doubleValue());
                totalExpensesCell.setCellStyle(currencyStyle);

                // Column AG: net_to_owner = total_rent - total_commission - total_expenses
                Cell netToOwnerCell = row.createCell(col++);
                BigDecimal netToOwner = totalRent.subtract(totalCommission).subtract(totalExpenses);
                netToOwnerCell.setCellValue(netToOwner.doubleValue());
                netToOwnerCell.setCellStyle(currencyStyle);

                rowNum++;
            }
        }

        // Apply fixed column widths
        applyFixedColumnWidths(sheet, headers.length);

        log.info("OWNER_PAYMENTS sheet created with {} rows (combined batch view)", rowNum - 1);
    }

    /**
     * Create PROPERTY_ACCOUNT sheet for block property account balance tracking
     *
     * ALLOCATION-BASED: Uses unified_allocations for accurate property-level reporting
     *
     * Structure:
     * - Column A: block_property_id (the block property receiving/spending)
     * - Column B: block_property_name
     * - Column C: source_property (flat name for contributions, or block name for expenses)
     * - Column D: paid_date
     * - Column E: movement_type ("IN" for contributions, "OUT" for expenses)
     * - Column F: amount (always positive for display)
     * - Column G: category
     * - Column H: description
     * - Column I: batch_reference
     *
     * Data sources (from unified_allocations):
     * 1. IN: DISBURSEMENT allocations where beneficiary is block property (flat contributions)
     * 2. IN: Negative OWNER allocations on block property (owner contributions)
     * 3. OUT: EXPENSE/COMMISSION allocations on block property
     *
     * Balance formula: SUMIF(movement_type="IN") - SUMIF(movement_type="OUT")
     */
    private void createPropertyAccountSheet(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                            LocalDate startDate, LocalDate endDate, WorkbookStyles styles) {
        log.info("Creating PROPERTY_ACCOUNT sheet (allocation-based - from unified_allocations)");

        Sheet sheet = workbook.createSheet("PROPERTY_ACCOUNT");

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "block_property_id", "block_property_name", "source_property", "paid_date",
            "movement_type", "amount", "category", "description", "batch_reference"
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
        int disbursementCount = 0;
        int ownerContributionCount = 0;
        int expenseCount = 0;

        // 1. Get DISBURSEMENT allocations TO block properties (IN - contributions from flats)
        // These are in unified_allocations with allocation_type='DISBURSEMENT' and beneficiary like block name
        List<UnifiedAllocation> disbursements = unifiedAllocationRepository.findByAllocationType(
            UnifiedAllocation.AllocationType.DISBURSEMENT);

        for (UnifiedAllocation alloc : disbursements) {
            // Only include disbursements to block properties (beneficiary name contains "BLOCK")
            String beneficiary = alloc.getBeneficiaryName();
            if (beneficiary == null || !beneficiary.toUpperCase().contains("BLOCK")) {
                continue;
            }

            Row row = sheet.createRow(rowNum++);
            int col = 0;

            // Column A: block_property_id - look up from beneficiary name
            // For now, use 0 as we'd need to resolve the block property ID
            row.createCell(col++).setCellValue(0);

            // Column B: block_property_name (the beneficiary)
            row.createCell(col++).setCellValue(beneficiary);

            // Column C: source_property (the flat contributing)
            row.createCell(col++).setCellValue(alloc.getPropertyName() != null ? alloc.getPropertyName() : "");

            // Column D: paid_date
            Cell dateCell = row.createCell(col++);
            if (alloc.getPaidDate() != null) {
                dateCell.setCellValue(alloc.getPaidDate());
                dateCell.setCellStyle(dateStyle);
            }

            // Column E: movement_type = "IN" (contribution coming into block)
            row.createCell(col++).setCellValue("IN");

            // Column F: amount (positive)
            Cell amountCell = row.createCell(col++);
            if (alloc.getAmount() != null) {
                amountCell.setCellValue(alloc.getAmount().abs().doubleValue());
                amountCell.setCellStyle(currencyStyle);
            }

            // Column G: category
            row.createCell(col++).setCellValue("Flat Contribution");

            // Column H: description
            row.createCell(col++).setCellValue(alloc.getDescription() != null ? alloc.getDescription() : "Service charge contribution");

            // Column I: batch_reference
            row.createCell(col++).setCellValue(alloc.getPaymentBatchId() != null ? alloc.getPaymentBatchId() : "");

            disbursementCount++;
        }

        // 2. Get block property allocations (EXPENSE, COMMISSION, and negative OWNER)
        // Find all allocations where property is a block property
        List<UnifiedAllocation> blockAllocations = unifiedAllocationRepository.findAll().stream()
            .filter(a -> a.getPropertyName() != null && a.getPropertyName().toUpperCase().contains("BLOCK"))
            .toList();

        for (UnifiedAllocation alloc : blockAllocations) {
            UnifiedAllocation.AllocationType type = alloc.getAllocationType();

            if (type == UnifiedAllocation.AllocationType.EXPENSE ||
                type == UnifiedAllocation.AllocationType.COMMISSION) {
                // EXPENSE/COMMISSION = OUT (money leaving block)
                if (alloc.getAmount() == null || alloc.getAmount().compareTo(java.math.BigDecimal.ZERO) == 0) {
                    continue; // Skip zero amounts
                }

                Row row = sheet.createRow(rowNum++);
                int col = 0;

                // Column A: block_property_id
                row.createCell(col++).setCellValue(alloc.getPropertyId() != null ? alloc.getPropertyId() : 0);

                // Column B: block_property_name
                row.createCell(col++).setCellValue(alloc.getPropertyName() != null ? alloc.getPropertyName() : "");

                // Column C: source_property (same as block for expenses)
                row.createCell(col++).setCellValue(alloc.getBeneficiaryName() != null ? alloc.getBeneficiaryName() : "");

                // Column D: paid_date
                Cell dateCell = row.createCell(col++);
                if (alloc.getPaidDate() != null) {
                    dateCell.setCellValue(alloc.getPaidDate());
                    dateCell.setCellStyle(dateStyle);
                }

                // Column E: movement_type = "OUT"
                row.createCell(col++).setCellValue("OUT");

                // Column F: amount (positive)
                Cell amountCell = row.createCell(col++);
                amountCell.setCellValue(alloc.getAmount().abs().doubleValue());
                amountCell.setCellStyle(currencyStyle);

                // Column G: category
                row.createCell(col++).setCellValue(alloc.getCategory() != null ? alloc.getCategory() : type.toString());

                // Column H: description
                row.createCell(col++).setCellValue(alloc.getDescription() != null ? alloc.getDescription() : "");

                // Column I: batch_reference
                row.createCell(col++).setCellValue(alloc.getPaymentBatchId() != null ? alloc.getPaymentBatchId() : "");

                expenseCount++;

            } else if (type == UnifiedAllocation.AllocationType.OWNER &&
                       alloc.getAmount() != null &&
                       alloc.getAmount().compareTo(java.math.BigDecimal.ZERO) < 0) {
                // Negative OWNER = IN (owner contribution to cover expenses)
                Row row = sheet.createRow(rowNum++);
                int col = 0;

                // Column A: block_property_id
                row.createCell(col++).setCellValue(alloc.getPropertyId() != null ? alloc.getPropertyId() : 0);

                // Column B: block_property_name
                row.createCell(col++).setCellValue(alloc.getPropertyName() != null ? alloc.getPropertyName() : "");

                // Column C: source_property (owner)
                row.createCell(col++).setCellValue(alloc.getBeneficiaryName() != null ? alloc.getBeneficiaryName() : "Owner");

                // Column D: paid_date
                Cell dateCell = row.createCell(col++);
                if (alloc.getPaidDate() != null) {
                    dateCell.setCellValue(alloc.getPaidDate());
                    dateCell.setCellStyle(dateStyle);
                }

                // Column E: movement_type = "IN" (owner contributing)
                row.createCell(col++).setCellValue("IN");

                // Column F: amount (absolute value - stored negative, show positive)
                Cell amountCell = row.createCell(col++);
                amountCell.setCellValue(alloc.getAmount().abs().doubleValue());
                amountCell.setCellStyle(currencyStyle);

                // Column G: category
                row.createCell(col++).setCellValue("Owner Contribution");

                // Column H: description
                row.createCell(col++).setCellValue(alloc.getDescription() != null ? alloc.getDescription() : "Owner funding block expenses");

                // Column I: batch_reference
                row.createCell(col++).setCellValue(alloc.getPaymentBatchId() != null ? alloc.getPaymentBatchId() : "");

                ownerContributionCount++;
            }
        }

        // Apply fixed column widths (autoSizeColumn causes OutOfMemoryError on large sheets)
        applyFixedColumnWidths(sheet, headers.length);

        log.info("PROPERTY_ACCOUNT sheet created with {} rows ({} flat contributions, {} owner contributions, {} expenses)",
            rowNum - 1, disbursementCount, ownerContributionCount, expenseCount);
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

                    // Column H: rent_received (SUMIFS to batch-based RENT_RECEIVED sheet)
                    // RENT_RECEIVED columns: A=lease_id, B=lease_ref, C=property, D=batch_id, E=owner_payment_date, N=total_rent
                    // Sum total_rent where lease_id matches AND owner_payment_date is in this month
                    Cell rentReceivedCell = row.createCell(col++);
                    rentReceivedCell.setCellFormula(String.format(
                        "SUMIFS(RENT_RECEIVED!N:N, RENT_RECEIVED!A:A, %d, RENT_RECEIVED!E:E, \">=\"&F%d, RENT_RECEIVED!E:E, \"<\"&EOMONTH(F%d,0)+1)",
                        lease.getLeaseId(), rowNum + 1, rowNum + 1
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

                    // Column M: total_expenses (SUMIFS to batch-based EXPENSES sheet)
                    // EXPENSES columns: A=lease_id, B=lease_ref, C=property, D=batch_id, E=owner_payment_date, R=total_expenses
                    // Sum total_expenses where lease_id matches AND owner_payment_date is in this month
                    Cell expensesCell = row.createCell(col++);
                    expensesCell.setCellFormula(String.format(
                        "SUMIFS(EXPENSES!R:R, EXPENSES!A:A, A%d, EXPENSES!E:E, \">=\"&F%d, EXPENSES!E:E, \"<\"&EOMONTH(F%d,0)+1)",
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

                // Column H: rent_received (SUMIFS to batch-based RENT_RECEIVED sheet) - payments can come in even for inactive leases
                // RENT_RECEIVED columns: A=lease_id, B=lease_ref, C=property, D=batch_id, E=owner_payment_date, N=total_rent
                // Sum total_rent where lease_id matches AND owner_payment_date is in this month
                Cell rentReceivedCell = row.createCell(col++);
                rentReceivedCell.setCellFormula(String.format(
                    "SUMIFS(RENT_RECEIVED!N:N, RENT_RECEIVED!A:A, %d, RENT_RECEIVED!E:E, \">=\"&F%d, RENT_RECEIVED!E:E, \"<\"&EOMONTH(F%d,0)+1)",
                    lease.getLeaseId(), rowNum + 1, rowNum + 1
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

                // Column M: total_expenses (SUMIFS to batch-based EXPENSES sheet)
                // EXPENSES columns: A=lease_id, B=lease_ref, C=property, D=batch_id, E=owner_payment_date, R=total_expenses
                // Sum total_expenses where lease_id matches AND owner_payment_date is in date range
                Cell expensesCell = row.createCell(col++);
                expensesCell.setCellFormula(String.format(
                    "SUMIFS(EXPENSES!R:R, EXPENSES!A:A, A%d, EXPENSES!E:E, \">=\"&DATE(%d,%d,%d), EXPENSES!E:E, \"<=\"&DATE(%d,%d,%d))",
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
     * Create monthly statement sheet with batch-based structure.
     * One row per batch per lease, showing individual rent payments and expenses.
     * Filtered by owner_payment_date (paidDate) within the statement period.
     * @param customerId Customer ID for period-based reconciliation (can be null for legacy calls)
     */
    private void createMonthlyStatementSheetForCustomPeriod(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                                           CustomPeriod period, String sheetName, WorkbookStyles styles,
                                                           Long customerId) {
        log.info("Creating monthly statement sheet (batch-based): {}", sheetName);

        // Resolve the actual owner ID for reconciliation queries
        // This handles delegated users who manage another owner's properties
        Long resolvedOwnerId = resolveActualOwnerId(customerId);
        if (!resolvedOwnerId.equals(customerId)) {
            log.info("Resolved customer {} to owner {} for reconciliation", customerId, resolvedOwnerId);
        }

        Sheet sheet = workbook.createSheet(sheetName);

        // Header row - batch-based structure with individual payment columns
        // Added rent_due and arrears columns for each lease's first batch row
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_id", "lease_reference", "property_name", "customer_name", "tenant_name",
            "rent_due", "opening_balance", "period_arrears",
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

        // Use shared styles instead of creating new ones
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.headerStyle);
        }

        // Use shared styles
        CellStyle dateStyle = styles.dateStyle;
        CellStyle currencyStyle = styles.currencyStyle;
        CellStyle percentStyle = styles.percentStyle;

        int rowNum = 1;

        // Collect all batches for reconciliation section
        List<site.easy.to.build.crm.dto.statement.BatchPaymentGroupDTO> allBatchesForReconciliation = new ArrayList<>();
        java.util.Set<Long> allPropertyIds = new java.util.HashSet<>();
        java.math.BigDecimal grandTotalRent = java.math.BigDecimal.ZERO;
        java.math.BigDecimal grandTotalCommission = java.math.BigDecimal.ZERO;
        java.math.BigDecimal grandTotalExpenses = java.math.BigDecimal.ZERO;
        java.math.BigDecimal grandTotalNetToOwner = java.math.BigDecimal.ZERO;
        java.math.BigDecimal grandTotalRentDue = java.math.BigDecimal.ZERO;

        // Generate one row per batch per lease
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();

            // Skip future leases that haven't started yet
            if (leaseStart != null && leaseStart.isAfter(period.periodEnd)) {
                continue;
            }

            // Calculate rent due for this lease in this period
            // Uses the same logic as RENT_DUE sheet - cycle-based billing
            java.math.BigDecimal leaseRentDue = calculateRentDueForPeriod(
                lease, period.periodStart, period.periodEnd);

            // Calculate opening balance (arrears brought forward from before this period)
            java.math.BigDecimal openingBalance = dataExtractService.calculateTenantOpeningBalance(
                lease.getLeaseId(), leaseStart, period.periodStart, lease.getMonthlyRent(),
                lease.getFrequencyMonths(), lease.getEndDate());
            if (openingBalance == null) {
                openingBalance = java.math.BigDecimal.ZERO;
            }

            // Get batch payment groups for this lease within the statement period
            List<site.easy.to.build.crm.dto.statement.BatchPaymentGroupDTO> batchGroups =
                dataExtractService.extractBatchPaymentGroups(lease, period.periodStart, period.periodEnd);

            // Calculate total rent received for this lease in this period (for arrears calculation)
            java.math.BigDecimal leaseRentReceived = java.math.BigDecimal.ZERO;
            for (site.easy.to.build.crm.dto.statement.BatchPaymentGroupDTO batch : batchGroups) {
                for (site.easy.to.build.crm.dto.statement.PaymentDetailDTO payment : batch.getRentPayments()) {
                    if (payment.getAmount() != null) {
                        leaseRentReceived = leaseRentReceived.add(payment.getAmount());
                    }
                }
            }

            // Period arrears = rent_due - rent_received (positive = tenant owes, negative = overpaid)
            java.math.BigDecimal periodArrears = leaseRentDue.subtract(leaseRentReceived);

            // Add to collection for reconciliation
            allBatchesForReconciliation.addAll(batchGroups);

            // Collect property ID for payment batch query
            if (lease.getPropertyId() != null) {
                allPropertyIds.add(lease.getPropertyId());
            }

            // Get commission rate for this lease
            boolean isBlockProperty = Boolean.TRUE.equals(lease.getIsBlockProperty());
            double commissionRate = isBlockProperty ? 0.0
                : (lease.getCommissionPercentage() != null
                    ? lease.getCommissionPercentage().doubleValue() / 100.0
                    : commissionConfig.getManagementFeePercent().doubleValue() + commissionConfig.getServiceFeePercent().doubleValue());

            // Track if this is the first row for the lease (to show rent_due/arrears only once)
            boolean isFirstRowForLease = true;

            // Accumulate rent due for grand total
            grandTotalRentDue = grandTotalRentDue.add(leaseRentDue);

            // If no batches for this lease, still create a row to show rent_due/arrears
            if (batchGroups.isEmpty() && leaseRentDue.compareTo(java.math.BigDecimal.ZERO) > 0) {
                Row row = sheet.createRow(rowNum);
                int col = 0;

                // A: lease_id
                row.createCell(col++).setCellValue(lease.getLeaseId());
                // B: lease_reference
                row.createCell(col++).setCellValue(lease.getLeaseReference());
                // C: property_name
                row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");
                // D: customer_name
                row.createCell(col++).setCellValue(lease.getCustomerName() != null ? lease.getCustomerName() : "");
                // E: tenant_name
                row.createCell(col++).setCellValue(lease.getTenantName() != null ? lease.getTenantName() : "");

                // F: rent_due
                Cell rentDueCell = row.createCell(col++);
                rentDueCell.setCellValue(leaseRentDue.doubleValue());
                rentDueCell.setCellStyle(currencyStyle);

                // G: opening_balance
                Cell openingBalanceCell = row.createCell(col++);
                openingBalanceCell.setCellValue(openingBalance.doubleValue());
                openingBalanceCell.setCellStyle(currencyStyle);

                // H: period_arrears (= rent_due since no payments)
                Cell periodArrearsCell = row.createCell(col++);
                periodArrearsCell.setCellValue(periodArrears.doubleValue());
                periodArrearsCell.setCellStyle(currencyStyle);

                // I-AI: Empty cells for batch/payment/expense columns
                // batch_id, owner_payment_date
                row.createCell(col++);
                row.createCell(col++);
                // rent_1 through rent_4 (date and amount) = 8 cells
                for (int i = 0; i < 8; i++) row.createCell(col++);
                // total_rent
                Cell totalRentCell = row.createCell(col++);
                totalRentCell.setCellValue(0);
                totalRentCell.setCellStyle(currencyStyle);
                // commission_rate
                Cell commRateCell = row.createCell(col++);
                commRateCell.setCellValue(commissionRate);
                commRateCell.setCellStyle(percentStyle);
                // total_commission
                Cell totalCommCell = row.createCell(col++);
                totalCommCell.setCellValue(0);
                totalCommCell.setCellStyle(currencyStyle);
                // expense_1 through expense_4 (date, amount, category) = 12 cells
                for (int i = 0; i < 12; i++) row.createCell(col++);
                // total_expenses
                Cell totalExpCell = row.createCell(col++);
                totalExpCell.setCellValue(0);
                totalExpCell.setCellStyle(currencyStyle);
                // net_to_owner
                Cell netCell = row.createCell(col++);
                netCell.setCellValue(0);
                netCell.setCellStyle(currencyStyle);

                rowNum++;
                isFirstRowForLease = false;
            }

            for (site.easy.to.build.crm.dto.statement.BatchPaymentGroupDTO batch : batchGroups) {
                List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> rentPayments = batch.getRentPayments();
                List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> expenses = batch.getExpenses();

                // Calculate totals upfront (needed for first row)
                java.math.BigDecimal totalRent = java.math.BigDecimal.ZERO;
                for (site.easy.to.build.crm.dto.statement.PaymentDetailDTO payment : rentPayments) {
                    if (payment.getAmount() != null) {
                        totalRent = totalRent.add(payment.getAmount());
                    }
                }

                java.math.BigDecimal totalExpenses = java.math.BigDecimal.ZERO;
                for (site.easy.to.build.crm.dto.statement.PaymentDetailDTO expense : expenses) {
                    if (expense.getAmount() != null) {
                        totalExpenses = totalExpenses.add(java.math.BigDecimal.valueOf(Math.abs(expense.getAmount().doubleValue())));
                    }
                }

                double totalCommission = totalRent.doubleValue() * commissionRate;
                double netToOwner = totalRent.doubleValue() - totalCommission - totalExpenses.doubleValue();

                // Calculate how many rows needed (spillover for >4 items)
                int rentRows = (int) Math.ceil(rentPayments.size() / 4.0);
                int expenseRows = (int) Math.ceil(expenses.size() / 4.0);
                int totalRows = Math.max(1, Math.max(rentRows, expenseRows));

                for (int rowIdx = 0; rowIdx < totalRows; rowIdx++) {
                    Row row = sheet.createRow(rowNum);
                    int col = 0;

                    // First row shows all the main data, spillover rows only show additional rent/expense items
                    boolean isFirstRow = (rowIdx == 0);

                    // A: lease_id
                    if (isFirstRow) {
                        row.createCell(col++).setCellValue(lease.getLeaseId());
                    } else {
                        row.createCell(col++);
                    }

                    // B: lease_reference
                    if (isFirstRow) {
                        row.createCell(col++).setCellValue(lease.getLeaseReference());
                    } else {
                        row.createCell(col++);
                    }

                    // C: property_name
                    if (isFirstRow) {
                        row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");
                    } else {
                        row.createCell(col++);
                    }

                    // D: customer_name (owner)
                    if (isFirstRow) {
                        row.createCell(col++).setCellValue(lease.getCustomerName() != null ? lease.getCustomerName() : "");
                    } else {
                        row.createCell(col++);
                    }

                    // E: tenant_name
                    if (isFirstRow) {
                        row.createCell(col++).setCellValue(lease.getTenantName() != null ? lease.getTenantName() : "");
                    } else {
                        row.createCell(col++);
                    }

                    // F: rent_due (only on first row for this lease)
                    Cell rentDueCell = row.createCell(col++);
                    if (isFirstRow && isFirstRowForLease) {
                        rentDueCell.setCellValue(leaseRentDue.doubleValue());
                        rentDueCell.setCellStyle(currencyStyle);
                    }

                    // G: opening_balance (only on first row for this lease)
                    Cell openingBalanceCell = row.createCell(col++);
                    if (isFirstRow && isFirstRowForLease) {
                        openingBalanceCell.setCellValue(openingBalance.doubleValue());
                        openingBalanceCell.setCellStyle(currencyStyle);
                    }

                    // H: period_arrears (only on first row for this lease)
                    // Period arrears = rent_due - total rent received in period
                    Cell periodArrearsCell = row.createCell(col++);
                    if (isFirstRow && isFirstRowForLease) {
                        periodArrearsCell.setCellValue(periodArrears.doubleValue());
                        periodArrearsCell.setCellStyle(currencyStyle);
                    }

                    // I: batch_id
                    if (isFirstRow) {
                        row.createCell(col++).setCellValue(batch.getBatchId() != null ? batch.getBatchId() : "");
                    } else {
                        row.createCell(col++);
                    }

                    // J: owner_payment_date
                    Cell ownerPaymentDateCell = row.createCell(col++);
                    if (isFirstRow && batch.getOwnerPaymentDate() != null) {
                        ownerPaymentDateCell.setCellValue(batch.getOwnerPaymentDate());
                        ownerPaymentDateCell.setCellStyle(dateStyle);
                    }

                    // K-R: rent_1 through rent_4 (date and amount for each) - offset by rowIdx * 4
                    int rentStartIdx = rowIdx * 4;
                    for (int i = 0; i < 4; i++) {
                        int rentIdx = rentStartIdx + i;
                        if (rentIdx < rentPayments.size()) {
                            site.easy.to.build.crm.dto.statement.PaymentDetailDTO payment = rentPayments.get(rentIdx);

                            // rent_N_date
                            Cell rentDateCell = row.createCell(col++);
                            if (payment.getPaymentDate() != null) {
                                rentDateCell.setCellValue(payment.getPaymentDate());
                                rentDateCell.setCellStyle(dateStyle);
                            }

                            // rent_N_amount
                            Cell rentAmountCell = row.createCell(col++);
                            if (payment.getAmount() != null) {
                                rentAmountCell.setCellValue(payment.getAmount().doubleValue());
                                rentAmountCell.setCellStyle(currencyStyle);
                            }
                        } else {
                            // Empty cells for missing payments
                            row.createCell(col++); // date
                            row.createCell(col++); // amount
                        }
                    }

                    // S: total_rent (only on first row)
                    Cell totalRentCell = row.createCell(col++);
                    if (isFirstRow) {
                        totalRentCell.setCellValue(totalRent.doubleValue());
                        totalRentCell.setCellStyle(currencyStyle);
                    }

                    // T: commission_rate (only on first row)
                    Cell commissionRateCell = row.createCell(col++);
                    if (isFirstRow) {
                        commissionRateCell.setCellValue(commissionRate);
                        commissionRateCell.setCellStyle(percentStyle);
                    }

                    // U: total_commission (only on first row)
                    Cell totalCommCell = row.createCell(col++);
                    if (isFirstRow) {
                        totalCommCell.setCellValue(totalCommission);
                        totalCommCell.setCellStyle(currencyStyle);
                    }

                    // V-AG: expense_1 through expense_4 (date, amount, category for each) - offset by rowIdx * 4
                    int expenseStartIdx = rowIdx * 4;
                    for (int i = 0; i < 4; i++) {
                        int expenseIdx = expenseStartIdx + i;
                        if (expenseIdx < expenses.size()) {
                            site.easy.to.build.crm.dto.statement.PaymentDetailDTO expense = expenses.get(expenseIdx);

                            // expense_N_date
                            Cell expenseDateCell = row.createCell(col++);
                            if (expense.getPaymentDate() != null) {
                                expenseDateCell.setCellValue(expense.getPaymentDate());
                                expenseDateCell.setCellStyle(dateStyle);
                            }

                            // expense_N_amount
                            Cell expenseAmountCell = row.createCell(col++);
                            if (expense.getAmount() != null) {
                                // Expenses are stored as negative, display as positive
                                double expenseAmount = Math.abs(expense.getAmount().doubleValue());
                                expenseAmountCell.setCellValue(expenseAmount);
                                expenseAmountCell.setCellStyle(currencyStyle);
                            }

                            // expense_N_category
                            row.createCell(col++).setCellValue(expense.getCategory() != null ? expense.getCategory() : "");
                        } else {
                            // Empty cells for missing expenses
                            row.createCell(col++); // date
                            row.createCell(col++); // amount
                            row.createCell(col++); // category
                        }
                    }

                    // AH: total_expenses (only on first row)
                    Cell totalExpensesCell = row.createCell(col++);
                    if (isFirstRow) {
                        totalExpensesCell.setCellValue(totalExpenses.doubleValue());
                        totalExpensesCell.setCellStyle(currencyStyle);
                    }

                    // AI: net_to_owner (only on first row)
                    Cell netToOwnerCell = row.createCell(col++);
                    if (isFirstRow) {
                        netToOwnerCell.setCellValue(netToOwner);
                        netToOwnerCell.setCellStyle(currencyStyle);
                    }

                    rowNum++;

                    // Mark that we've processed the first row for this lease
                    if (isFirstRow) {
                        isFirstRowForLease = false;
                    }
                }

                // Accumulate grand totals for reconciliation (use values calculated earlier)
                grandTotalRent = grandTotalRent.add(totalRent);
                grandTotalCommission = grandTotalCommission.add(java.math.BigDecimal.valueOf(totalCommission));
                grandTotalExpenses = grandTotalExpenses.add(totalExpenses);
                grandTotalNetToOwner = grandTotalNetToOwner.add(java.math.BigDecimal.valueOf(netToOwner));

                rowNum++;
            }
        }

        // Add totals row (use shared styles to save memory)
        if (rowNum > 1) {
            Row totalsRow = sheet.createRow(rowNum);
            int col = 0;

            // A: Label in first column (use shared boldStyle)
            Cell labelCell = totalsRow.createCell(col++);
            labelCell.setCellValue("TOTALS");
            labelCell.setCellStyle(styles.boldStyle);

            // Skip columns B-E (lease_reference, property_name, customer_name, tenant_name)
            col += 4;

            // F: rent_due (sum)
            Cell totalRentDueSumCell = totalsRow.createCell(col++);
            totalRentDueSumCell.setCellFormula(String.format("SUM(F2:F%d)", rowNum));
            totalRentDueSumCell.setCellStyle(styles.boldCurrencyStyle);

            // G: opening_balance (sum)
            Cell totalOpeningBalanceSumCell = totalsRow.createCell(col++);
            totalOpeningBalanceSumCell.setCellFormula(String.format("SUM(G2:G%d)", rowNum));
            totalOpeningBalanceSumCell.setCellStyle(styles.boldCurrencyStyle);

            // H: period_arrears (sum)
            Cell totalArrearsSumCell = totalsRow.createCell(col++);
            totalArrearsSumCell.setCellFormula(String.format("SUM(H2:H%d)", rowNum));
            totalArrearsSumCell.setCellStyle(styles.boldCurrencyStyle);

            // Skip columns I-J (batch_id, owner_payment_date)
            col += 2;

            // Skip K-R (rent_1 through rent_4 date and amount columns)
            col += 8;

            // S: total_rent (sum)
            Cell totalRentSumCell = totalsRow.createCell(col++);
            totalRentSumCell.setCellFormula(String.format("SUM(S2:S%d)", rowNum));
            totalRentSumCell.setCellStyle(styles.boldCurrencyStyle);

            // T: commission_rate (skip)
            totalsRow.createCell(col++).setCellValue("");

            // U: total_commission (sum)
            Cell totalCommSumCell = totalsRow.createCell(col++);
            totalCommSumCell.setCellFormula(String.format("SUM(U2:U%d)", rowNum));
            totalCommSumCell.setCellStyle(styles.boldCurrencyStyle);

            // Skip V-AG (expense_1 through expense_4 date, amount, category columns)
            col += 12;

            // AH: total_expenses (sum)
            Cell totalExpensesSumCell = totalsRow.createCell(col++);
            totalExpensesSumCell.setCellFormula(String.format("SUM(AH2:AH%d)", rowNum));
            totalExpensesSumCell.setCellStyle(styles.boldCurrencyStyle);

            // AI: net_to_owner (sum)
            Cell totalNetSumCell = totalsRow.createCell(col++);
            totalNetSumCell.setCellFormula(String.format("SUM(AI2:AI%d)", rowNum));
            totalNetSumCell.setCellStyle(styles.boldCurrencyStyle);
        }

        // Apply fixed column widths (autoSizeColumn causes OutOfMemoryError on large sheets)
        applyFixedColumnWidths(sheet, headers.length);

        // ===== PAYMENT RECONCILIATION SECTION =====
        // Shows all batches that have ANY allocation from transactions in this period
        // Add blank rows for separation
        rowNum += 3;

        log.info("PAYMENT RECONCILIATION: Sheet {}, Period {} to {}, Customer {} (resolved owner: {})",
            sheetName, period.periodStart, period.periodEnd, customerId, resolvedOwnerId);

        // Section header
        Row reconcileHeaderRow = sheet.createRow(rowNum++);
        Cell reconcileHeaderCell = reconcileHeaderRow.createCell(0);
        reconcileHeaderCell.setCellValue("PAYMENT RECONCILIATION");
        reconcileHeaderCell.setCellStyle(styles.boldStyle);

        Row periodInfoRow = sheet.createRow(rowNum++);
        periodInfoRow.createCell(0).setCellValue("Period: " + period.periodStart.toString() + " to " + period.periodEnd.toString());

        rowNum++; // Blank row

        // Get all batches that have allocations from transactions in this period
        // Use resolvedOwnerId to handle delegated users managing another owner's properties
        List<site.easy.to.build.crm.dto.statement.BatchAllocationStatusDTO> batchStatuses =
            dataExtractService.getBatchesWithPeriodAllocations(resolvedOwnerId, period.periodStart, period.periodEnd);

        log.info("Found {} batches with period allocations", batchStatuses.size());

        // Calculate totals
        java.math.BigDecimal totalFromPriorPeriods = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalFromThisPeriod = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalPaymentsMade = java.math.BigDecimal.ZERO;
        int priorCount = 0;
        int thisPeriodCount = 0;

        for (site.easy.to.build.crm.dto.statement.BatchAllocationStatusDTO batch : batchStatuses) {
            totalFromPriorPeriods = totalFromPriorPeriods.add(batch.getAllocatedFromPriorPeriods());
            totalFromThisPeriod = totalFromThisPeriod.add(batch.getAllocatedFromThisPeriod());
            totalPaymentsMade = totalPaymentsMade.add(batch.getNetToOwner());
            priorCount += batch.getPriorPeriodAllocations();
            thisPeriodCount += batch.getThisPeriodAllocations();
        }

        // ===== ALLOCATION STATUS SUMMARY =====
        Row statusHeaderRow = sheet.createRow(rowNum++);
        Cell statusHeaderCell = statusHeaderRow.createCell(0);
        statusHeaderCell.setCellValue("ALLOCATION STATUS");
        statusHeaderCell.setCellStyle(styles.boldStyle);

        // Get full allocation status summary
        // Use resolvedOwnerId to handle delegated users managing another owner's properties
        Map<String, Object> allocationSummary = dataExtractService.getAllocationStatusSummary(
            resolvedOwnerId, period.periodStart, period.periodEnd);

        Map<String, Object> priorAllocated = (Map<String, Object>) allocationSummary.get("allocatedBeforePeriod");
        Map<String, Object> periodAllocated = (Map<String, Object>) allocationSummary.get("allocatedDuringPeriod");
        Map<String, Object> unallocated = (Map<String, Object>) allocationSummary.get("unallocatedAtEnd");

        Row allocStartRow = sheet.createRow(rowNum++);
        allocStartRow.createCell(0).setCellValue("  Allocated before period start:");
        Cell allocStartCell = allocStartRow.createCell(1);
        allocStartCell.setCellValue(((Number) priorAllocated.get("amount")).doubleValue());
        allocStartCell.setCellStyle(styles.currencyStyle);
        allocStartRow.createCell(2).setCellValue("(" + priorAllocated.get("count") + " items)");

        Row allocPeriodRow = sheet.createRow(rowNum++);
        allocPeriodRow.createCell(0).setCellValue("  Allocated during this period:");
        Cell allocPeriodCell = allocPeriodRow.createCell(1);
        allocPeriodCell.setCellValue(((Number) periodAllocated.get("amount")).doubleValue());
        allocPeriodCell.setCellStyle(styles.currencyStyle);
        allocPeriodRow.createCell(2).setCellValue("(" + periodAllocated.get("count") + " items)");

        Row unallocRow = sheet.createRow(rowNum++);
        Cell unallocLabel = unallocRow.createCell(0);
        unallocLabel.setCellValue("  For Future Periods (after this period):");
        unallocLabel.setCellStyle(styles.boldStyle);
        Cell unallocCell = unallocRow.createCell(1);
        unallocCell.setCellValue(((Number) unallocated.get("amount")).doubleValue());
        unallocCell.setCellStyle(styles.boldCurrencyStyle);
        unallocRow.createCell(2).setCellValue("(" + unallocated.get("count") + " items)");

        rowNum++; // Blank row

        // ===== OWNER PAYMENTS THIS PERIOD (detailed) =====
        // Shows all batches that include any transactions from this period
        Row paymentsHeaderRow = sheet.createRow(rowNum++);
        Cell paymentsHeaderCell = paymentsHeaderRow.createCell(0);
        paymentsHeaderCell.setCellValue("OWNER PAYMENTS THIS PERIOD");
        paymentsHeaderCell.setCellStyle(styles.boldStyle);

        Row paymentsSubHeaderRow = sheet.createRow(rowNum++);
        paymentsSubHeaderRow.createCell(0).setCellValue("(Batches containing allocations from transactions in this period)");

        rowNum++; // Blank row

        // Column layout:
        // 0: Batch ID, 1: Payment Date, 2: Gross Income, 3: Commission, 4: Expenses, 5: Net to Owner
        // PRIOR section: 6: Property, 7: Txn Date, 8: Income, 9: Expense, 10: Commission, 11: Net
        // THIS PERIOD section: 12: Property, 13: Txn Date, 14: Income, 15: Expense, 16: Commission, 17: Net
        // FUTURE section: 18: Property, 19: Txn Date, 20: Income, 21: Expense, 22: Commission, 23: Net

        // First header row - main categories with "Prior", "This Period" and "Future" spanning columns
        Row periodHeaderRow = sheet.createRow(rowNum++);
        periodHeaderRow.createCell(6).setCellValue("Prior");
        periodHeaderRow.getCell(6).setCellStyle(styles.boldStyle);
        periodHeaderRow.createCell(12).setCellValue("This Period");
        periodHeaderRow.getCell(12).setCellStyle(styles.boldStyle);
        periodHeaderRow.createCell(18).setCellValue("Future");
        periodHeaderRow.getCell(18).setCellStyle(styles.boldStyle);

        // Table headers - batch summary columns + allocation detail columns for all three periods
        Row tableHeaderRow = sheet.createRow(rowNum++);
        String[] paymentHeaders = {"Batch ID", "Payment Date", "Gross Income", "Commission", "Expenses", "Net to Owner",
                                   "Property", "Txn Date", "Income", "Expense", "Commission", "Net",
                                   "Property", "Txn Date", "Income", "Expense", "Commission", "Net",
                                   "Property", "Txn Date", "Income", "Expense", "Commission", "Net"};
        for (int i = 0; i < paymentHeaders.length; i++) {
            Cell headerCell = tableHeaderRow.createCell(i);
            headerCell.setCellValue(paymentHeaders[i]);
            headerCell.setCellStyle(styles.headerStyle);
        }

        // Running totals for detail columns (Prior/This Period/Future)
        double priorIncome = 0, priorExpense = 0, priorCommission = 0, priorNet = 0;
        double currentIncome = 0, currentExpense = 0, currentCommission = 0, currentNet = 0;
        double futureIncome = 0, futureExpense = 0, futureCommission = 0, futureNet = 0;

        if (batchStatuses.isEmpty()) {
            Row noPaymentsRow = sheet.createRow(rowNum++);
            noPaymentsRow.createCell(0).setCellValue("(No payments made during this period)");
        } else {
            for (site.easy.to.build.crm.dto.statement.BatchAllocationStatusDTO batch : batchStatuses) {
                // Separate allocations by period (three categories: Prior, This Period, Future)
                List<site.easy.to.build.crm.dto.statement.BatchAllocationStatusDTO.AllocationDetailDTO> priorAllocations = new ArrayList<>();
                List<site.easy.to.build.crm.dto.statement.BatchAllocationStatusDTO.AllocationDetailDTO> currentAllocations = new ArrayList<>();
                List<site.easy.to.build.crm.dto.statement.BatchAllocationStatusDTO.AllocationDetailDTO> futureAllocations = new ArrayList<>();

                // Pre-calculate period subtotals for this batch
                double batchPriorIncome = 0, batchPriorExpense = 0, batchPriorCommission = 0, batchPriorNet = 0;
                double batchCurrentIncome = 0, batchCurrentExpense = 0, batchCurrentCommission = 0, batchCurrentNet = 0;
                double batchFutureIncome = 0, batchFutureExpense = 0, batchFutureCommission = 0, batchFutureNet = 0;

                for (site.easy.to.build.crm.dto.statement.BatchAllocationStatusDTO.AllocationDetailDTO alloc : batch.getAllocations()) {
                    String allocPeriod = alloc.getPeriodClassification();
                    String allocType = alloc.getAllocationType();
                    double allocIncome = 0, allocExpense = 0, allocCommission = 0;

                    if ("OWNER".equals(allocType) && alloc.getAmount() != null) {
                        allocIncome = alloc.getGrossAmount() != null ? alloc.getGrossAmount().abs().doubleValue() : alloc.getAmount().abs().doubleValue();
                        if (alloc.getCommissionAmount() != null) {
                            allocCommission = alloc.getCommissionAmount().abs().doubleValue();
                        }
                    } else if (("EXPENSE".equals(allocType) || "DISBURSEMENT".equals(allocType)) && alloc.getAmount() != null) {
                        allocExpense = alloc.getAmount().abs().doubleValue();
                    } else if ("COMMISSION".equals(allocType) && alloc.getAmount() != null) {
                        allocCommission = alloc.getAmount().abs().doubleValue();
                    }

                    double allocNet = allocIncome - allocExpense - allocCommission;

                    if ("B/F".equals(allocPeriod)) {
                        priorAllocations.add(alloc);
                        batchPriorIncome += allocIncome;
                        batchPriorExpense += allocExpense;
                        batchPriorCommission += allocCommission;
                        batchPriorNet += allocNet;
                    } else if ("FUTURE".equals(allocPeriod)) {
                        futureAllocations.add(alloc);
                        batchFutureIncome += allocIncome;
                        batchFutureExpense += allocExpense;
                        batchFutureCommission += allocCommission;
                        batchFutureNet += allocNet;
                    } else {
                        currentAllocations.add(alloc);
                        batchCurrentIncome += allocIncome;
                        batchCurrentExpense += allocExpense;
                        batchCurrentCommission += allocCommission;
                        batchCurrentNet += allocNet;
                    }
                }

                // === BATCH HEADER ROW ===
                Row batchHeaderRow = sheet.createRow(rowNum++);
                batchHeaderRow.createCell(0).setCellValue(batch.getBatchId() != null ? batch.getBatchId() : "Unknown");

                Cell paymentDateCell = batchHeaderRow.createCell(1);
                if (batch.getPaymentDate() != null) {
                    paymentDateCell.setCellValue(batch.getPaymentDate());
                    paymentDateCell.setCellStyle(styles.dateStyle);
                } else {
                    paymentDateCell.setCellValue("Pending");
                }

                Cell grossCell = batchHeaderRow.createCell(2);
                grossCell.setCellValue(batch.getGrossIncome().doubleValue());
                grossCell.setCellStyle(styles.boldCurrencyStyle);

                Cell commCell = batchHeaderRow.createCell(3);
                commCell.setCellValue(batch.getCommission().doubleValue());
                commCell.setCellStyle(styles.currencyStyle);

                Cell expCell = batchHeaderRow.createCell(4);
                expCell.setCellValue(batch.getExpenses().doubleValue());
                expCell.setCellStyle(styles.currencyStyle);

                Cell netCell = batchHeaderRow.createCell(5);
                netCell.setCellValue(batch.getNetToOwner().doubleValue());
                netCell.setCellStyle(styles.boldCurrencyStyle);

                // === PERIOD BREAKDOWN ROWS (Prior, This Period, Future) ===
                // Prior row
                Row priorSummaryRow = sheet.createRow(rowNum++);
                priorSummaryRow.createCell(1).setCellValue("Prior:");
                Cell priorIncCell = priorSummaryRow.createCell(2);
                priorIncCell.setCellValue(batchPriorIncome);
                priorIncCell.setCellStyle(styles.currencyStyle);
                Cell priorCommSumCell = priorSummaryRow.createCell(3);
                priorCommSumCell.setCellValue(batchPriorCommission);
                priorCommSumCell.setCellStyle(styles.currencyStyle);
                Cell priorExpSumCell = priorSummaryRow.createCell(4);
                priorExpSumCell.setCellValue(batchPriorExpense);
                priorExpSumCell.setCellStyle(styles.currencyStyle);
                Cell priorNetSumCell = priorSummaryRow.createCell(5);
                priorNetSumCell.setCellValue(batchPriorNet);
                priorNetSumCell.setCellStyle(styles.currencyStyle);

                // This Period row
                Row currentSummaryRow = sheet.createRow(rowNum++);
                currentSummaryRow.createCell(1).setCellValue("This Period:");
                Cell currIncCell = currentSummaryRow.createCell(2);
                currIncCell.setCellValue(batchCurrentIncome);
                currIncCell.setCellStyle(styles.currencyStyle);
                Cell currCommSumCell = currentSummaryRow.createCell(3);
                currCommSumCell.setCellValue(batchCurrentCommission);
                currCommSumCell.setCellStyle(styles.currencyStyle);
                Cell currExpSumCell = currentSummaryRow.createCell(4);
                currExpSumCell.setCellValue(batchCurrentExpense);
                currExpSumCell.setCellStyle(styles.currencyStyle);
                Cell currNetSumCell = currentSummaryRow.createCell(5);
                currNetSumCell.setCellValue(batchCurrentNet);
                currNetSumCell.setCellStyle(styles.currencyStyle);

                // Future row
                Row futureSummaryRow = sheet.createRow(rowNum++);
                futureSummaryRow.createCell(1).setCellValue("Future:");
                Cell futIncCell = futureSummaryRow.createCell(2);
                futIncCell.setCellValue(batchFutureIncome);
                futIncCell.setCellStyle(styles.currencyStyle);
                Cell futCommSumCell = futureSummaryRow.createCell(3);
                futCommSumCell.setCellValue(batchFutureCommission);
                futCommSumCell.setCellStyle(styles.currencyStyle);
                Cell futExpSumCell = futureSummaryRow.createCell(4);
                futExpSumCell.setCellValue(batchFutureExpense);
                futExpSumCell.setCellStyle(styles.currencyStyle);
                Cell futNetSumCell = futureSummaryRow.createCell(5);
                futNetSumCell.setCellValue(batchFutureNet);
                futNetSumCell.setCellStyle(styles.currencyStyle);

                // === ALLOCATION DETAIL ROWS ===
                // Determine how many rows we need for this batch (max of all three allocation lists, minimum 1)
                int maxRows = Math.max(1, Math.max(priorAllocations.size(), Math.max(currentAllocations.size(), futureAllocations.size())));

                for (int rowIdx = 0; rowIdx < maxRows; rowIdx++) {
                    Row batchRow = sheet.createRow(rowNum++);

                    // Prior Period allocation (columns 6-11: Property, Txn Date, Income, Expense, Commission, Net)
                    if (rowIdx < priorAllocations.size()) {
                        site.easy.to.build.crm.dto.statement.BatchAllocationStatusDTO.AllocationDetailDTO alloc = priorAllocations.get(rowIdx);

                        // Column 6: Property name
                        batchRow.createCell(6).setCellValue(alloc.getPropertyName() != null ? alloc.getPropertyName() : "");

                        // Column 7: Transaction date
                        Cell txnDateCell = batchRow.createCell(7);
                        if (alloc.getTransactionDate() != null) {
                            txnDateCell.setCellValue(alloc.getTransactionDate());
                            txnDateCell.setCellStyle(styles.dateStyle);
                        }

                        // Columns 8, 9, 10, 11: Income (Gross), Expense, Commission, Net
                        double rowIncome = 0, rowExpense = 0, rowCommission = 0;
                        if (alloc.getAmount() != null) {
                            String type = alloc.getAllocationType();
                            if ("OWNER".equals(type)) {
                                // For OWNER allocations, use grossAmount if available (new structure)
                                rowIncome = alloc.getGrossAmount() != null ?
                                    alloc.getGrossAmount().abs().doubleValue() :
                                    alloc.getAmount().abs().doubleValue();
                                Cell incomeCell = batchRow.createCell(8);
                                incomeCell.setCellValue(rowIncome);
                                incomeCell.setCellStyle(styles.currencyStyle);
                                priorIncome += rowIncome;

                                // Show commission from the allocation breakdown if available
                                if (alloc.getCommissionAmount() != null) {
                                    rowCommission = alloc.getCommissionAmount().abs().doubleValue();
                                    Cell priorCommCell = batchRow.createCell(10);
                                    priorCommCell.setCellValue(rowCommission);
                                    priorCommCell.setCellStyle(styles.currencyStyle);
                                    priorCommission += rowCommission;
                                }
                            } else if ("EXPENSE".equals(type) || "DISBURSEMENT".equals(type)) {
                                rowExpense = alloc.getAmount().abs().doubleValue();
                                Cell expenseCell = batchRow.createCell(9);
                                expenseCell.setCellValue(rowExpense);
                                expenseCell.setCellStyle(styles.currencyStyle);
                                priorExpense += rowExpense;
                            } else if ("COMMISSION".equals(type)) {
                                // Standalone COMMISSION allocation (legacy or agency reimbursements)
                                rowCommission = alloc.getAmount().abs().doubleValue();
                                Cell priorCommCell2 = batchRow.createCell(10);
                                priorCommCell2.setCellValue(rowCommission);
                                priorCommCell2.setCellStyle(styles.currencyStyle);
                                priorCommission += rowCommission;
                            }
                        }
                        // Column 11: Net (Income - Expense - Commission)
                        double rowNet = rowIncome - rowExpense - rowCommission;
                        Cell priorNetCell = batchRow.createCell(11);
                        priorNetCell.setCellValue(rowNet);
                        priorNetCell.setCellStyle(styles.currencyStyle);
                        priorNet += rowNet;
                    }

                    // This Period allocation (columns 12-17: Property, Txn Date, Income, Expense, Commission, Net)
                    if (rowIdx < currentAllocations.size()) {
                        site.easy.to.build.crm.dto.statement.BatchAllocationStatusDTO.AllocationDetailDTO alloc = currentAllocations.get(rowIdx);

                        // Column 12: Property name
                        batchRow.createCell(12).setCellValue(alloc.getPropertyName() != null ? alloc.getPropertyName() : "");

                        // Column 13: Transaction date
                        Cell txnDateCell = batchRow.createCell(13);
                        if (alloc.getTransactionDate() != null) {
                            txnDateCell.setCellValue(alloc.getTransactionDate());
                            txnDateCell.setCellStyle(styles.dateStyle);
                        }

                        // Columns 14, 15, 16, 17: Income (Gross), Expense, Commission, Net
                        double rowIncome = 0, rowExpense = 0, rowCommission = 0;
                        if (alloc.getAmount() != null) {
                            String type = alloc.getAllocationType();
                            if ("OWNER".equals(type)) {
                                // For OWNER allocations, use grossAmount if available (new structure)
                                rowIncome = alloc.getGrossAmount() != null ?
                                    alloc.getGrossAmount().abs().doubleValue() :
                                    alloc.getAmount().abs().doubleValue();
                                Cell incomeCell = batchRow.createCell(14);
                                incomeCell.setCellValue(rowIncome);
                                incomeCell.setCellStyle(styles.currencyStyle);
                                currentIncome += rowIncome;

                                // Show commission from the allocation breakdown if available
                                if (alloc.getCommissionAmount() != null) {
                                    rowCommission = alloc.getCommissionAmount().abs().doubleValue();
                                    Cell currCommCell = batchRow.createCell(16);
                                    currCommCell.setCellValue(rowCommission);
                                    currCommCell.setCellStyle(styles.currencyStyle);
                                    currentCommission += rowCommission;
                                }
                            } else if ("EXPENSE".equals(type) || "DISBURSEMENT".equals(type)) {
                                rowExpense = alloc.getAmount().abs().doubleValue();
                                Cell expenseCell = batchRow.createCell(15);
                                expenseCell.setCellValue(rowExpense);
                                expenseCell.setCellStyle(styles.currencyStyle);
                                currentExpense += rowExpense;
                            } else if ("COMMISSION".equals(type)) {
                                // Standalone COMMISSION allocation (legacy or agency reimbursements)
                                rowCommission = alloc.getAmount().abs().doubleValue();
                                Cell currCommCell2 = batchRow.createCell(16);
                                currCommCell2.setCellValue(rowCommission);
                                currCommCell2.setCellStyle(styles.currencyStyle);
                                currentCommission += rowCommission;
                            }
                        }
                        // Column 17: Net (Income - Expense - Commission)
                        double rowNet = rowIncome - rowExpense - rowCommission;
                        Cell currNetCell = batchRow.createCell(17);
                        currNetCell.setCellValue(rowNet);
                        currNetCell.setCellStyle(styles.currencyStyle);
                        currentNet += rowNet;
                    }

                    // Future Period allocation (columns 18-23: Property, Txn Date, Income, Expense, Commission, Net)
                    if (rowIdx < futureAllocations.size()) {
                        site.easy.to.build.crm.dto.statement.BatchAllocationStatusDTO.AllocationDetailDTO alloc = futureAllocations.get(rowIdx);

                        // Column 18: Property name
                        batchRow.createCell(18).setCellValue(alloc.getPropertyName() != null ? alloc.getPropertyName() : "");

                        // Column 19: Transaction date
                        Cell txnDateCell = batchRow.createCell(19);
                        if (alloc.getTransactionDate() != null) {
                            txnDateCell.setCellValue(alloc.getTransactionDate());
                            txnDateCell.setCellStyle(styles.dateStyle);
                        }

                        // Columns 20, 21, 22, 23: Income (Gross), Expense, Commission, Net
                        double rowIncome = 0, rowExpense = 0, rowCommission = 0;
                        if (alloc.getAmount() != null) {
                            String type = alloc.getAllocationType();
                            if ("OWNER".equals(type)) {
                                // For OWNER allocations, use grossAmount if available (new structure)
                                rowIncome = alloc.getGrossAmount() != null ?
                                    alloc.getGrossAmount().abs().doubleValue() :
                                    alloc.getAmount().abs().doubleValue();
                                Cell incomeCell = batchRow.createCell(20);
                                incomeCell.setCellValue(rowIncome);
                                incomeCell.setCellStyle(styles.currencyStyle);
                                futureIncome += rowIncome;

                                // Show commission from the allocation breakdown if available
                                if (alloc.getCommissionAmount() != null) {
                                    rowCommission = alloc.getCommissionAmount().abs().doubleValue();
                                    Cell futCommCell = batchRow.createCell(22);
                                    futCommCell.setCellValue(rowCommission);
                                    futCommCell.setCellStyle(styles.currencyStyle);
                                    futureCommission += rowCommission;
                                }
                            } else if ("EXPENSE".equals(type) || "DISBURSEMENT".equals(type)) {
                                rowExpense = alloc.getAmount().abs().doubleValue();
                                Cell expenseCell = batchRow.createCell(21);
                                expenseCell.setCellValue(rowExpense);
                                expenseCell.setCellStyle(styles.currencyStyle);
                                futureExpense += rowExpense;
                            } else if ("COMMISSION".equals(type)) {
                                // Standalone COMMISSION allocation (legacy or agency reimbursements)
                                rowCommission = alloc.getAmount().abs().doubleValue();
                                Cell futCommCell2 = batchRow.createCell(22);
                                futCommCell2.setCellValue(rowCommission);
                                futCommCell2.setCellStyle(styles.currencyStyle);
                                futureCommission += rowCommission;
                            }
                        }
                        // Column 23: Net (Income - Expense - Commission)
                        double rowNet = rowIncome - rowExpense - rowCommission;
                        Cell futNetCell = batchRow.createCell(23);
                        futNetCell.setCellValue(rowNet);
                        futNetCell.setCellStyle(styles.currencyStyle);
                        futureNet += rowNet;
                    }
                }

                // Add blank row between batches for readability
                rowNum++;
            }

            // Totals row
            Row totalsRow = sheet.createRow(rowNum++);
            Cell totalsLabel = totalsRow.createCell(0);
            totalsLabel.setCellValue("TOTALS");
            totalsLabel.setCellStyle(styles.boldStyle);

            // Calculate totals
            java.math.BigDecimal totalGross = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalComm = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalExp = java.math.BigDecimal.ZERO;

            for (site.easy.to.build.crm.dto.statement.BatchAllocationStatusDTO batch : batchStatuses) {
                totalGross = totalGross.add(batch.getGrossIncome());
                totalComm = totalComm.add(batch.getCommission());
                totalExp = totalExp.add(batch.getExpenses());
            }

            // New column indices: 2=Gross, 3=Commission, 4=Expenses, 5=Net
            Cell totalGrossCell = totalsRow.createCell(2);
            totalGrossCell.setCellValue(totalGross.doubleValue());
            totalGrossCell.setCellStyle(styles.boldCurrencyStyle);

            Cell totalCommCell = totalsRow.createCell(3);
            totalCommCell.setCellValue(totalComm.doubleValue());
            totalCommCell.setCellStyle(styles.boldCurrencyStyle);

            Cell totalExpCell = totalsRow.createCell(4);
            totalExpCell.setCellValue(totalExp.doubleValue());
            totalExpCell.setCellStyle(styles.boldCurrencyStyle);

            Cell totalNetCell = totalsRow.createCell(5);
            totalNetCell.setCellValue(totalPaymentsMade.doubleValue());
            totalNetCell.setCellStyle(styles.boldCurrencyStyle);

            // Prior Period detail totals (columns 8, 9, 10, 11: Income, Expense, Commission, Net)
            Cell priorIncomeTotal = totalsRow.createCell(8);
            priorIncomeTotal.setCellValue(priorIncome);
            priorIncomeTotal.setCellStyle(styles.boldCurrencyStyle);

            Cell priorExpenseTotal = totalsRow.createCell(9);
            priorExpenseTotal.setCellValue(priorExpense);
            priorExpenseTotal.setCellStyle(styles.boldCurrencyStyle);

            Cell priorCommissionTotal = totalsRow.createCell(10);
            priorCommissionTotal.setCellValue(priorCommission);
            priorCommissionTotal.setCellStyle(styles.boldCurrencyStyle);

            Cell priorNetTotal = totalsRow.createCell(11);
            priorNetTotal.setCellValue(priorNet);
            priorNetTotal.setCellStyle(styles.boldCurrencyStyle);

            // This Period detail totals (columns 14, 15, 16, 17: Income, Expense, Commission, Net)
            Cell currentIncomeTotal = totalsRow.createCell(14);
            currentIncomeTotal.setCellValue(currentIncome);
            currentIncomeTotal.setCellStyle(styles.boldCurrencyStyle);

            Cell currentExpenseTotal = totalsRow.createCell(15);
            currentExpenseTotal.setCellValue(currentExpense);
            currentExpenseTotal.setCellStyle(styles.boldCurrencyStyle);

            Cell currentCommissionTotal = totalsRow.createCell(16);
            currentCommissionTotal.setCellValue(currentCommission);
            currentCommissionTotal.setCellStyle(styles.boldCurrencyStyle);

            Cell currentNetTotal = totalsRow.createCell(17);
            currentNetTotal.setCellValue(currentNet);
            currentNetTotal.setCellStyle(styles.boldCurrencyStyle);

            // Future Period detail totals (columns 20, 21, 22, 23: Income, Expense, Commission, Net)
            Cell futureIncomeTotal = totalsRow.createCell(20);
            futureIncomeTotal.setCellValue(futureIncome);
            futureIncomeTotal.setCellStyle(styles.boldCurrencyStyle);

            Cell futureExpenseTotal = totalsRow.createCell(21);
            futureExpenseTotal.setCellValue(futureExpense);
            futureExpenseTotal.setCellStyle(styles.boldCurrencyStyle);

            Cell futureCommissionTotal = totalsRow.createCell(22);
            futureCommissionTotal.setCellValue(futureCommission);
            futureCommissionTotal.setCellStyle(styles.boldCurrencyStyle);

            Cell futureNetTotal = totalsRow.createCell(23);
            futureNetTotal.setCellValue(futureNet);
            futureNetTotal.setCellStyle(styles.boldCurrencyStyle);
        }

        rowNum++; // Blank row

        // Grand totals summary
        Row summaryHeaderRow = sheet.createRow(rowNum++);
        Cell summaryHeaderCell = summaryHeaderRow.createCell(0);
        summaryHeaderCell.setCellValue("PERIOD SUMMARY");
        summaryHeaderCell.setCellStyle(styles.boldStyle);

        Row totalRentRow = sheet.createRow(rowNum++);
        totalRentRow.createCell(0).setCellValue("Total Rent Received:");
        Cell totalRentCell = totalRentRow.createCell(1);
        totalRentCell.setCellValue(grandTotalRent.doubleValue());
        totalRentCell.setCellStyle(styles.currencyStyle);

        Row totalCommRow = sheet.createRow(rowNum++);
        totalCommRow.createCell(0).setCellValue("Less: Commission:");
        Cell totalCommCell = totalCommRow.createCell(1);
        totalCommCell.setCellValue(grandTotalCommission.negate().doubleValue());
        totalCommCell.setCellStyle(styles.currencyStyle);

        Row totalExpRow = sheet.createRow(rowNum++);
        totalExpRow.createCell(0).setCellValue("Less: Expenses:");
        Cell totalExpCell = totalExpRow.createCell(1);
        totalExpCell.setCellValue(grandTotalExpenses.negate().doubleValue());
        totalExpCell.setCellStyle(styles.currencyStyle);

        Row separatorRow = sheet.createRow(rowNum++);
        separatorRow.createCell(0).setCellValue("─────────────────────────────");

        Row netToOwnerRow = sheet.createRow(rowNum++);
        Cell netToOwnerLabel = netToOwnerRow.createCell(0);
        netToOwnerLabel.setCellValue("Net to Owner:");
        netToOwnerLabel.setCellStyle(styles.boldStyle);
        Cell netToOwnerTotal = netToOwnerRow.createCell(1);
        netToOwnerTotal.setCellValue(grandTotalNetToOwner.doubleValue());
        netToOwnerTotal.setCellStyle(styles.boldCurrencyStyle);

        Row paidRow = sheet.createRow(rowNum++);
        Cell paidLabel = paidRow.createCell(0);
        paidLabel.setCellValue("Total Paid to Owner:");
        paidLabel.setCellStyle(styles.boldStyle);
        Cell paidTotal = paidRow.createCell(1);
        paidTotal.setCellValue(totalPaymentsMade.doubleValue());
        paidTotal.setCellStyle(styles.boldCurrencyStyle);

        Row balanceRow = sheet.createRow(rowNum++);
        Cell balanceLabel = balanceRow.createCell(0);
        balanceLabel.setCellValue("Balance (should be £0):");
        balanceLabel.setCellStyle(styles.boldStyle);
        Cell balanceCell = balanceRow.createCell(1);
        balanceCell.setCellValue(grandTotalNetToOwner.subtract(totalPaymentsMade).doubleValue());
        balanceCell.setCellStyle(styles.boldCurrencyStyle);

        log.info("{} sheet created with {} rows (batch-based, including reconciliation)", sheetName, rowNum);
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
                    unallocRow.createCell(COL_DESC).setCellValue("    → For Future Periods (owner credit)");
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

        // For future periods - amounts not yet allocated
        Row unallocRow = sheet.createRow(rowNum++);
        unallocRow.createCell(COL_DESC).setCellValue("  For Future Periods (not yet allocated):");
        Cell unallocCell = unallocRow.createCell(COL_AMT);
        unallocCell.setCellValue(totalPeriodUnallocated.doubleValue());
        unallocCell.setCellStyle(currencyStyle);

        // Variance check: Payments = Allocated + Unallocated
        BigDecimal expectedPayments = totalAllocated.add(totalPeriodUnallocated);
        BigDecimal variance = totalPaymentsMade.subtract(expectedPayments);

        rowNum++;
        Row verifyRow = sheet.createRow(rowNum++);
        String verifyText = String.format("Check: Allocated %.2f + For Future %.2f = %.2f vs Payments %.2f",
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

                // H: total_rent_received (SUM payments where owner_payment_date <= statement end date)
                // RENT_RECEIVED columns: A=lease_id, B=lease_ref, C=property, D=batch_id, E=owner_payment_date, N=total_rent
                Cell totalRentReceivedCell = row.createCell(col++);
                totalRentReceivedCell.setCellFormula(String.format(
                    "SUMIFS(RENT_RECEIVED!N:N, RENT_RECEIVED!A:A, %d, RENT_RECEIVED!E:E, \"<=\"&DATE(%d,%d,%d))",
                    lease.getLeaseId(),
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
                // EXPENSES columns: A=lease_id, B=lease_ref, C=property, D=batch_id, E=owner_payment_date, R=total_expenses
                // Sum total_expenses where lease_id matches AND owner_payment_date is in date range
                Cell totalExpensesCell = row.createCell(col++);
                totalExpensesCell.setCellFormula(String.format(
                    "SUMIFS(EXPENSES!R:R, EXPENSES!A:A, %d, EXPENSES!E:E, \">=\"&DATE(%d,%d,%d), EXPENSES!E:E, \"<=\"&DATE(%d,%d,%d))",
                    lease.getLeaseId(),
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
     * Create MONTHLY_STATEMENT sheet with batch-based structure.
     * One row per batch per lease, showing individual rent payments and expenses.
     * Filtered by owner_payment_date (paidDate) within the statement period.
     */
    private void createMonthlyStatementSheetWithCustomPeriods(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                                             LocalDate startDate, LocalDate endDate, int periodStartDay,
                                                             WorkbookStyles styles) {
        log.info("Creating MONTHLY_STATEMENT sheet (batch-based - one row per batch per lease)");

        Sheet sheet = workbook.createSheet("MONTHLY_STATEMENT");

        // Header row - batch-based structure with individual payment columns
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_id", "lease_reference", "property_name", "customer_name", "tenant_name",
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
        CellStyle percentStyle = styles.percentStyle;

        int rowNum = 1;

        // Generate one row per batch per lease
        for (LeaseMasterDTO lease : leaseMaster) {
            // Get batch payment groups for this lease within the statement period
            List<site.easy.to.build.crm.dto.statement.BatchPaymentGroupDTO> batchGroups =
                dataExtractService.extractBatchPaymentGroups(lease, startDate, endDate);

            // Get commission rate for this lease
            boolean isBlockProperty = Boolean.TRUE.equals(lease.getIsBlockProperty());
            double commissionRate = isBlockProperty ? 0.0
                : (lease.getCommissionPercentage() != null
                    ? lease.getCommissionPercentage().doubleValue() / 100.0
                    : commissionConfig.getManagementFeePercent().doubleValue() + commissionConfig.getServiceFeePercent().doubleValue());

            for (site.easy.to.build.crm.dto.statement.BatchPaymentGroupDTO batch : batchGroups) {
                List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> rentPayments = batch.getRentPayments();
                List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> expenses = batch.getExpenses();

                // Calculate totals upfront (needed for first row)
                java.math.BigDecimal totalRent = java.math.BigDecimal.ZERO;
                for (site.easy.to.build.crm.dto.statement.PaymentDetailDTO payment : rentPayments) {
                    if (payment.getAmount() != null) {
                        totalRent = totalRent.add(payment.getAmount());
                    }
                }

                java.math.BigDecimal totalExpenses = java.math.BigDecimal.ZERO;
                for (site.easy.to.build.crm.dto.statement.PaymentDetailDTO expense : expenses) {
                    if (expense.getAmount() != null) {
                        totalExpenses = totalExpenses.add(java.math.BigDecimal.valueOf(Math.abs(expense.getAmount().doubleValue())));
                    }
                }

                double totalCommission = totalRent.doubleValue() * commissionRate;
                double netToOwner = totalRent.doubleValue() - totalCommission - totalExpenses.doubleValue();

                // Calculate how many rows needed (spillover for >4 items)
                int rentRows = (int) Math.ceil(rentPayments.size() / 4.0);
                int expenseRows = (int) Math.ceil(expenses.size() / 4.0);
                int totalRows = Math.max(1, Math.max(rentRows, expenseRows));

                for (int rowIdx = 0; rowIdx < totalRows; rowIdx++) {
                    Row row = sheet.createRow(rowNum);
                    int col = 0;

                    // First row shows all the main data, spillover rows only show additional rent/expense items
                    boolean isFirstRow = (rowIdx == 0);

                    // A: lease_id
                    if (isFirstRow) {
                        row.createCell(col++).setCellValue(lease.getLeaseId());
                    } else {
                        row.createCell(col++);
                    }

                    // B: lease_reference
                    if (isFirstRow) {
                        row.createCell(col++).setCellValue(lease.getLeaseReference());
                    } else {
                        row.createCell(col++);
                    }

                    // C: property_name
                    if (isFirstRow) {
                        row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");
                    } else {
                        row.createCell(col++);
                    }

                    // D: customer_name (owner)
                    if (isFirstRow) {
                        row.createCell(col++).setCellValue(lease.getCustomerName() != null ? lease.getCustomerName() : "");
                    } else {
                        row.createCell(col++);
                    }

                    // E: tenant_name
                    if (isFirstRow) {
                        row.createCell(col++).setCellValue(lease.getTenantName() != null ? lease.getTenantName() : "");
                    } else {
                        row.createCell(col++);
                    }

                    // F: batch_id
                    if (isFirstRow) {
                        row.createCell(col++).setCellValue(batch.getBatchId() != null ? batch.getBatchId() : "");
                    } else {
                        row.createCell(col++);
                    }

                    // G: owner_payment_date
                    Cell ownerPaymentDateCell = row.createCell(col++);
                    if (isFirstRow && batch.getOwnerPaymentDate() != null) {
                        ownerPaymentDateCell.setCellValue(batch.getOwnerPaymentDate());
                        ownerPaymentDateCell.setCellStyle(dateStyle);
                    }

                    // H-O: rent_1 through rent_4 (date and amount for each) - offset by rowIdx * 4
                    int rentStartIdx = rowIdx * 4;
                    for (int i = 0; i < 4; i++) {
                        int rentIdx = rentStartIdx + i;
                        if (rentIdx < rentPayments.size()) {
                            site.easy.to.build.crm.dto.statement.PaymentDetailDTO payment = rentPayments.get(rentIdx);

                            // rent_N_date
                            Cell rentDateCell = row.createCell(col++);
                            if (payment.getPaymentDate() != null) {
                                rentDateCell.setCellValue(payment.getPaymentDate());
                                rentDateCell.setCellStyle(dateStyle);
                            }

                            // rent_N_amount
                            Cell rentAmountCell = row.createCell(col++);
                            if (payment.getAmount() != null) {
                                rentAmountCell.setCellValue(payment.getAmount().doubleValue());
                                rentAmountCell.setCellStyle(currencyStyle);
                            }
                        } else {
                            // Empty cells for missing payments
                            row.createCell(col++); // date
                            row.createCell(col++); // amount
                        }
                    }

                    // P: total_rent (only on first row)
                    Cell totalRentCell = row.createCell(col++);
                    if (isFirstRow) {
                        totalRentCell.setCellValue(totalRent.doubleValue());
                        totalRentCell.setCellStyle(currencyStyle);
                    }

                    // Q: commission_rate (only on first row)
                    Cell commissionRateCell = row.createCell(col++);
                    if (isFirstRow) {
                        commissionRateCell.setCellValue(commissionRate);
                        commissionRateCell.setCellStyle(percentStyle);
                    }

                    // R: total_commission (only on first row)
                    Cell totalCommCell = row.createCell(col++);
                    if (isFirstRow) {
                        totalCommCell.setCellValue(totalCommission);
                        totalCommCell.setCellStyle(currencyStyle);
                    }

                    // S-AD: expense_1 through expense_4 (date, amount, category for each) - offset by rowIdx * 4
                    int expenseStartIdx = rowIdx * 4;
                    for (int i = 0; i < 4; i++) {
                        int expenseIdx = expenseStartIdx + i;
                        if (expenseIdx < expenses.size()) {
                            site.easy.to.build.crm.dto.statement.PaymentDetailDTO expense = expenses.get(expenseIdx);

                            // expense_N_date
                            Cell expenseDateCell = row.createCell(col++);
                            if (expense.getPaymentDate() != null) {
                                expenseDateCell.setCellValue(expense.getPaymentDate());
                                expenseDateCell.setCellStyle(dateStyle);
                            }

                            // expense_N_amount
                            Cell expenseAmountCell = row.createCell(col++);
                            if (expense.getAmount() != null) {
                                // Expenses are stored as negative, display as positive
                                double expenseAmount = Math.abs(expense.getAmount().doubleValue());
                                expenseAmountCell.setCellValue(expenseAmount);
                                expenseAmountCell.setCellStyle(currencyStyle);
                            }

                            // expense_N_category
                            row.createCell(col++).setCellValue(expense.getCategory() != null ? expense.getCategory() : "");
                        } else {
                            // Empty cells for missing expenses
                            row.createCell(col++); // date
                            row.createCell(col++); // amount
                            row.createCell(col++); // category
                        }
                    }

                    // AE: total_expenses (only on first row)
                    Cell totalExpensesCell = row.createCell(col++);
                    if (isFirstRow) {
                        totalExpensesCell.setCellValue(totalExpenses.doubleValue());
                        totalExpensesCell.setCellStyle(currencyStyle);
                    }

                    // AF: net_to_owner (only on first row)
                    Cell netToOwnerCell = row.createCell(col++);
                    if (isFirstRow) {
                        netToOwnerCell.setCellValue(netToOwner);
                        netToOwnerCell.setCellStyle(currencyStyle);
                    }

                    rowNum++;
                }
            }
        }

        // Apply fixed column widths (autoSizeColumn causes OutOfMemoryError on large sheets)
        applyFixedColumnWidths(sheet, headers.length);

        log.info("MONTHLY_STATEMENT sheet created with {} rows (batch-based)", rowNum - 1);
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
        bfHeaderCell.setCellValue("BROUGHT FORWARD (For Future Periods as of " + startDate.toString() + ")");
        bfHeaderCell.setCellStyle(headerStyle);

        // For Future Periods Income (owed to owner)
        List<UnallocatedIncomeDTO> bfIncome = dataExtractService.extractUnallocatedIncomeAsOf(customerId, startDate);
        BigDecimal totalBfIncome = BigDecimal.ZERO;

        if (!bfIncome.isEmpty()) {
            Row bfIncomeHeaderRow = sheet.createRow(rowNum++);
            bfIncomeHeaderRow.createCell(0).setCellValue("For Future Periods - Income (owed to owner):");
            bfIncomeHeaderRow.getCell(0).setCellStyle(boldStyle);

            // Sub-headers
            Row subHeaderRow = sheet.createRow(rowNum++);
            String[] incomeHeaders = {"Date", "Property", "Tenant", "Gross", "Commission", "Net Due", "Allocated", "For Future"};
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

        // For Future Periods (owner credit - overpayments)
        List<UnallocatedPaymentDTO> bfPayments = dataExtractService.extractUnallocatedPaymentsAsOf(customerId, startDate);
        BigDecimal totalBfCredit = BigDecimal.ZERO;

        if (!bfPayments.isEmpty()) {
            rowNum++; // Blank row
            Row bfCreditHeaderRow = sheet.createRow(rowNum++);
            bfCreditHeaderRow.createCell(0).setCellValue("For Future Periods (owner credit):");
            bfCreditHeaderRow.getCell(0).setCellStyle(boldStyle);

            // Sub-headers
            Row subHeaderRow = sheet.createRow(rowNum++);
            String[] paymentHeaders = {"Date", "Batch Ref", "Total Payment", "Total Allocated", "For Future (Credit)"};
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

            // For Future Periods portion of this payment
            if (payment.getUnallocatedAmount() != null && payment.getUnallocatedAmount().compareTo(BigDecimal.valueOf(0.01)) > 0) {
                Row unallocRow = sheet.createRow(rowNum++);
                unallocRow.createCell(1).setCellValue("For Future Periods (Owner Credit):");
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
        cfHeaderCell.setCellValue("CARRIED FORWARD (For Future Periods as of " + endDate.toString() + ")");
        cfHeaderCell.setCellStyle(headerStyle);

        // For Future Periods Income at end
        List<UnallocatedIncomeDTO> cfIncome = dataExtractService.extractUnallocatedIncomeAsOfEndDate(customerId, endDate);
        BigDecimal totalCfIncome = BigDecimal.ZERO;

        if (!cfIncome.isEmpty()) {
            Row cfIncomeHeaderRow = sheet.createRow(rowNum++);
            cfIncomeHeaderRow.createCell(0).setCellValue("For Future Periods - Income (owed to owner):");
            cfIncomeHeaderRow.getCell(0).setCellStyle(boldStyle);

            // Sub-headers
            Row subHeaderRow = sheet.createRow(rowNum++);
            String[] incomeHeaders = {"Date", "Property", "Tenant", "Gross", "Commission", "Net Due", "Allocated", "For Future"};
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

        // For Future Periods at end (owner credit)
        List<UnallocatedPaymentDTO> cfPayments = dataExtractService.extractUnallocatedPaymentsAsOfEndDate(customerId, endDate);
        BigDecimal totalCfCredit = BigDecimal.ZERO;

        if (!cfPayments.isEmpty()) {
            rowNum++; // Blank row
            Row cfCreditHeaderRow = sheet.createRow(rowNum++);
            cfCreditHeaderRow.createCell(0).setCellValue("For Future Periods (owner credit):");
            cfCreditHeaderRow.getCell(0).setCellStyle(boldStyle);

            // Sub-headers
            Row subHeaderRow = sheet.createRow(rowNum++);
            String[] paymentHeaders = {"Date", "Batch Ref", "Total Payment", "Total Allocated", "For Future (Credit)"};
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

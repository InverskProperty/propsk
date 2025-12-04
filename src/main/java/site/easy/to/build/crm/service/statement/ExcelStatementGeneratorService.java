package site.easy.to.build.crm.service.statement;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.dto.statement.LeaseMasterDTO;
import site.easy.to.build.crm.dto.statement.TransactionDTO;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.PaymentBatch;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.repository.PaymentBatchRepository;
import site.easy.to.build.crm.repository.TransactionBatchAllocationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Generate complete statement workbook
     *
     * @param startDate Statement period start
     * @param endDate Statement period end
     * @return Excel workbook with all sheets and formulas
     */
    public Workbook generateStatement(LocalDate startDate, LocalDate endDate) {
        log.info("Generating statement from {} to {}", startDate, endDate);

        Workbook workbook = new XSSFWorkbook();

        // Extract data
        List<LeaseMasterDTO> leaseMaster = dataExtractService.extractLeaseMaster();
        // IMPORTANT: Only extract INCOMING transactions (rent received) to prevent double-counting
        // This excludes OUTGOING transactions (landlord payments, fees, expenses)
        List<TransactionDTO> transactions = dataExtractService.extractRentReceived(startDate, endDate);

        log.info("Extracted {} leases and {} INCOMING transactions (rent received)", leaseMaster.size(), transactions.size());

        // Create sheets
        createLeaseMasterSheet(workbook, leaseMaster);
        createTransactionsSheet(workbook, transactions);
        createRentDueSheet(workbook, leaseMaster, startDate, endDate);
        createRentReceivedSheet(workbook, leaseMaster, startDate, endDate);
        createExpensesSheet(workbook, leaseMaster, startDate, endDate);
        createMonthlyStatementSheet(workbook, leaseMaster, startDate, endDate);

        log.info("Statement workbook created successfully");
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
        log.info("Generating statement for customer {} from {} to {}", customerId, startDate, endDate);

        Workbook workbook = new XSSFWorkbook();

        // Extract data for this customer
        List<LeaseMasterDTO> leaseMaster = dataExtractService.extractLeaseMasterForCustomer(customerId);
        // IMPORTANT: Only extract INCOMING transactions (rent received) to prevent double-counting
        // This excludes OUTGOING transactions (landlord payments, fees, expenses)
        List<TransactionDTO> transactions = dataExtractService.extractRentReceivedForCustomer(
            customerId, startDate, endDate);

        log.info("Extracted {} leases and {} INCOMING transactions (rent received) for customer {}",
                leaseMaster.size(), transactions.size(), customerId);

        // Create data sheets
        createLeaseMasterSheet(workbook, leaseMaster);
        createTransactionsSheet(workbook, transactions);
        createRentDueSheet(workbook, leaseMaster, startDate, endDate);
        createRentReceivedSheet(workbook, leaseMaster, startDate, endDate);
        createExpensesSheet(workbook, leaseMaster, startDate, endDate);

        // Create separate monthly statement sheets for each month in the period
        List<site.easy.to.build.crm.util.RentCyclePeriodCalculator.RentCyclePeriod> periods =
            site.easy.to.build.crm.util.RentCyclePeriodCalculator.calculateMonthlyPeriods(startDate, endDate);

        log.info("Creating {} separate monthly statement sheets", periods.size());
        for (site.easy.to.build.crm.util.RentCyclePeriodCalculator.RentCyclePeriod period : periods) {
            createMonthlyStatementSheetForPeriod(workbook, leaseMaster, period);
        }

        log.info("Customer statement workbook created successfully with {} monthly sheets", periods.size());
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
        log.info("Generating statement with custom periods (start day: {}) from {} to {}", periodStartDay, startDate, endDate);

        Workbook workbook = new XSSFWorkbook();

        // Extract data
        List<LeaseMasterDTO> leaseMaster = dataExtractService.extractLeaseMaster();
        // IMPORTANT: Only extract INCOMING transactions (rent received) to prevent double-counting
        // This excludes OUTGOING transactions (landlord payments, fees, expenses)
        List<TransactionDTO> transactions = dataExtractService.extractRentReceived(startDate, endDate);

        log.info("Extracted {} leases and {} INCOMING transactions (rent received)", leaseMaster.size(), transactions.size());

        // Create sheets with custom periods
        createLeaseMasterSheet(workbook, leaseMaster);
        createTransactionsSheet(workbook, transactions);
        createRentDueSheetWithCustomPeriods(workbook, leaseMaster, startDate, endDate, periodStartDay);
        createRentReceivedSheetWithCustomPeriods(workbook, leaseMaster, startDate, endDate, periodStartDay);
        createExpensesSheet(workbook, leaseMaster, startDate, endDate);
        createMonthlyStatementSheetWithCustomPeriods(workbook, leaseMaster, startDate, endDate, periodStartDay);

        log.info("Statement workbook with custom periods created successfully");
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
        log.error("🔍 DEBUG: Generating statement for customer {} with custom periods (start day: {})", customerId, periodStartDay);

        Workbook workbook = new XSSFWorkbook();

        // Extract data for this customer
        log.error("🔍 DEBUG: About to extract lease master for customer {}", customerId);
        List<LeaseMasterDTO> leaseMaster = dataExtractService.extractLeaseMasterForCustomer(customerId);
        log.error("🔍 DEBUG: About to extract INCOMING transactions (rent received) for customer {} from {} to {}", customerId, startDate, endDate);
        // IMPORTANT: Only extract INCOMING transactions (rent received) to prevent double-counting
        // This excludes OUTGOING transactions (landlord payments, fees, expenses)
        List<TransactionDTO> transactions = dataExtractService.extractRentReceivedForCustomer(
            customerId, startDate, endDate);

        log.error("🔍 DEBUG: Extracted {} leases and {} INCOMING transactions (rent received) for customer {}",
                leaseMaster.size(), transactions.size(), customerId);

        // Create data sheets with custom periods
        createLeaseMasterSheet(workbook, leaseMaster);
        createTransactionsSheet(workbook, transactions);
        createRentDueSheetWithCustomPeriods(workbook, leaseMaster, startDate, endDate, periodStartDay);
        createRentReceivedSheetWithCustomPeriods(workbook, leaseMaster, startDate, endDate, periodStartDay);
        createExpensesSheet(workbook, leaseMaster, startDate, endDate);

        // Generate custom periods
        List<CustomPeriod> periods = generateCustomPeriods(startDate, endDate, periodStartDay);

        // Create separate monthly statement sheets for each period
        log.info("Creating {} separate monthly statement sheets", periods.size());
        for (CustomPeriod period : periods) {
            String sheetName = sanitizeSheetName(
                period.periodStart.format(DateTimeFormatter.ofPattern("MMM dd")) + " - " +
                period.periodEnd.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
            );
            createMonthlyStatementSheetForCustomPeriod(workbook, leaseMaster, period, sheetName);
        }

        // Create summary sheet (totals across all periods)
        createSummarySheetForCustomPeriods(workbook, leaseMaster, periods, startDate, endDate);

        // Create allocation tracking sheets for this owner
        // Resolve the actual owner ID (handles delegated users who manage another owner's properties)
        Long resolvedOwnerId = resolveActualOwnerId(customerId);
        log.error("🔍 DEBUG: Resolved owner ID {} -> {} for allocation sheets", customerId, resolvedOwnerId);

        createIncomeAllocationsSheet(workbook, resolvedOwnerId);
        createExpenseAllocationsSheet(workbook, resolvedOwnerId);
        createOwnerPaymentsSummarySheet(workbook, resolvedOwnerId);

        log.info("Customer statement workbook with custom periods created successfully with {} monthly sheets", periods.size());
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
        List<CustomPeriod> periods = new ArrayList<>();

        if (leaseStart == null) {
            log.warn("Lease has no start date, cannot generate periods");
            return periods;
        }

        int rentDueDay = leaseStart.getDayOfMonth();

        // Find the first rent due date on or after lease start that's within our statement range
        LocalDate periodStart = leaseStart;

        // If lease started before our statement range, find the first period that overlaps
        if (periodStart.isBefore(overallStart)) {
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

        // Generate periods from lease start to effective end
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

            // Only add period if it overlaps with our statement range
            if (!periodEnd.isBefore(overallStart) && !periodStart.isAfter(overallEnd)) {
                periods.add(new CustomPeriod(periodStart, periodEnd));
            }

            // Move to next period
            periodStart = nextPeriodStart;
        }

        log.debug("Generated {} lease-based periods for lease starting {} (rent due day: {})",
                 periods.size(), leaseStart, rentDueDay);
        return periods;
    }

    /**
     * Generate custom periods based on start day (e.g., 22nd of each month)
     */
    private List<CustomPeriod> generateCustomPeriods(LocalDate start, LocalDate end, int periodStartDay) {
        List<CustomPeriod> periods = new ArrayList<>();

        // Find first period start on or after the start date
        LocalDate periodStart = LocalDate.of(start.getYear(), start.getMonthValue(),
                                            Math.min(periodStartDay, start.lengthOfMonth()));

        // If we're past the period start day in the first month, start there
        if (periodStart.isBefore(start)) {
            // Move to next month's period start
            periodStart = periodStart.plusMonths(1);
            periodStart = LocalDate.of(periodStart.getYear(), periodStart.getMonthValue(),
                                      Math.min(periodStartDay, periodStart.lengthOfMonth()));
        } else if (start.getDayOfMonth() > periodStartDay) {
            // Start date is after period start day, so next period starts next month
            periodStart = periodStart.plusMonths(1);
            periodStart = LocalDate.of(periodStart.getYear(), periodStart.getMonthValue(),
                                      Math.min(periodStartDay, periodStart.lengthOfMonth()));
        }

        // Actually, let's align to the first period that contains or starts after the start date
        periodStart = LocalDate.of(start.getYear(), start.getMonthValue(),
                                  Math.min(periodStartDay, start.lengthOfMonth()));
        if (periodStart.isBefore(start)) {
            periodStart = start;
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
            periodStart = periodEnd.plusDays(1);
        }

        log.info("Generated {} custom periods with start day {}", periods.size(), periodStartDay);
        return periods;
    }

    /**
     * Create LEASE_MASTER sheet with raw lease data
     * NO CALCULATIONS - Just data from database
     */
    private void createLeaseMasterSheet(Workbook workbook, List<LeaseMasterDTO> leaseMaster) {
        log.info("Creating LEASE_MASTER sheet with {} leases", leaseMaster.size());

        Sheet sheet = workbook.createSheet("LEASE_MASTER");

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_id", "lease_reference", "property_id", "property_name", "property_address",
            "customer_id", "customer_name", "start_date", "end_date", "monthly_rent", "frequency"
        };

        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data rows
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);

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
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        log.info("LEASE_MASTER sheet created with {} rows", leaseMaster.size());
    }

    /**
     * Create TRANSACTIONS sheet with raw transaction data
     * NO CALCULATIONS - Just data from database
     */
    private void createTransactionsSheet(Workbook workbook, List<TransactionDTO> transactions) {
        log.info("Creating TRANSACTIONS sheet with {} transactions", transactions.size());

        Sheet sheet = workbook.createSheet("TRANSACTIONS");

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "transaction_id", "transaction_date", "invoice_id", "property_id", "property_name",
            "customer_id", "category", "transaction_type", "amount", "description"
        };

        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data rows
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);

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
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        log.info("TRANSACTIONS sheet created with {} rows", transactions.size());
    }

    /**
     * Create RENT_DUE sheet with FORMULAS for pro-rating
     * This is where Excel formulas do the heavy lifting!
     */
    private void createRentDueSheet(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                   LocalDate startDate, LocalDate endDate) {
        log.info("Creating RENT_DUE sheet with formulas");

        Sheet sheet = workbook.createSheet("RENT_DUE");

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_id", "lease_reference", "property_name", "month_start", "month_end",
            "days_in_month", "lease_days_in_month", "prorated_rent_due",
            "management_fee", "service_fee", "total_commission", "net_to_owner"
        };

        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Styles
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);

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

                // Column H: prorated_rent_due - £0 if lease not active in this month
                Cell proratedRentCell = row.createCell(col++);
                if (leaseActiveInMonth) {
                    proratedRentCell.setCellFormula(String.format(
                        "IF(AND(NOT(ISBLANK(VLOOKUP(A%d,LEASE_MASTER!A:I,9,FALSE))), VLOOKUP(A%d,LEASE_MASTER!A:I,9,FALSE)>=D%d, VLOOKUP(A%d,LEASE_MASTER!A:I,9,FALSE)<E%d), ROUND((VLOOKUP(A%d,LEASE_MASTER!A:I,9,FALSE)-D%d+1)/F%d*VLOOKUP(A%d,LEASE_MASTER!A:J,10,FALSE), 2), VLOOKUP(A%d,LEASE_MASTER!A:J,10,FALSE))",
                        rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1
                    ));
                } else {
                    // Lease not active - rent due is £0
                    proratedRentCell.setCellValue(0);
                }
                proratedRentCell.setCellStyle(currencyStyle);

                // Column I: management_fee (formula: 10%)
                Cell mgmtFeeCell = row.createCell(col++);
                mgmtFeeCell.setCellFormula(String.format("H%d * %.2f", rowNum + 1, commissionConfig.getManagementFeePercent().doubleValue()));
                mgmtFeeCell.setCellStyle(currencyStyle);

                // Column J: service_fee (formula: 5%)
                Cell svcFeeCell = row.createCell(col++);
                svcFeeCell.setCellFormula(String.format("H%d * %.2f", rowNum + 1, commissionConfig.getServiceFeePercent().doubleValue()));
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

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        log.info("RENT_DUE sheet created with {} rows (lease × month)", rowNum - 1);
    }

    /**
     * Create RENT_RECEIVED sheet with payment breakdown
     * Shows individual payment dates and amounts (up to 4 payments per period)
     */
    private void createRentReceivedSheet(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                        LocalDate startDate, LocalDate endDate) {
        log.info("Creating RENT_RECEIVED sheet with payment breakdown");

        Sheet sheet = workbook.createSheet("RENT_RECEIVED");

        // Header row with payment breakdown columns
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_id", "lease_reference", "property_name", "rent_due_date",
            "month_start", "month_end",
            "payment_1_date", "payment_1_amount",
            "payment_2_date", "payment_2_amount",
            "payment_3_date", "payment_3_amount",
            "payment_4_date", "payment_4_amount",
            "total_received"
        };

        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Styles
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);

        int rowNum = 1;

        // Generate one row per lease per month that has started
        // Show active leases AND ended leases, but NOT future leases that haven't started
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();
            LocalDate leaseEnd = lease.getEndDate();

            // Start from lease start to capture all historical payments for opening balance calculation
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

                // Create row for leases that have started (payments can come in even for ended leases)
                Row row = sheet.createRow(rowNum);
                int col = 0;

                // Get payment details for this lease/month (payments can exist even for inactive leases)
                List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> payments =
                    dataExtractService.extractRentReceivedDetails(lease.getLeaseId(), monthStart, monthEnd);

                // Column A: lease_id
                row.createCell(col++).setCellValue(lease.getLeaseId());

                // Column B: lease_reference
                row.createCell(col++).setCellValue(lease.getLeaseReference());

                // Column C: property_name
                row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");

                // Column D: rent_due_date (1st of the month by default)
                Cell dueDateCell = row.createCell(col++);
                dueDateCell.setCellValue(monthStart);
                dueDateCell.setCellStyle(dateStyle);

                // Column E: month_start
                Cell monthStartCell = row.createCell(col++);
                monthStartCell.setCellValue(monthStart);
                monthStartCell.setCellStyle(dateStyle);

                // Column F: month_end
                Cell monthEndCell = row.createCell(col++);
                monthEndCell.setCellValue(monthEnd);
                monthEndCell.setCellStyle(dateStyle);

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

                currentMonth = currentMonth.plusMonths(1);
            }
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        log.info("RENT_RECEIVED sheet created with {} rows", rowNum - 1);
    }

    /**
     * Create EXPENSES sheet with expense breakdown by date, amount, and category
     * Shows property expenses (cleaning, maintenance, furnishings, etc.)
     */
    private void createExpensesSheet(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                     LocalDate startDate, LocalDate endDate) {
        log.info("Creating EXPENSES sheet with flat structure (one row per expense)");

        Sheet sheet = workbook.createSheet("EXPENSES");

        // ✅ RESTRUCTURED: Flat format with one row per expense for proper date range filtering
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_id", "lease_reference", "property_name",
            "expense_date", "expense_amount", "expense_category", "expense_description"
        };

        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Styles
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);

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
                expenseDateCell.setCellValue(expense.getPaymentDate());
                expenseDateCell.setCellStyle(dateStyle);

                // Column E: expense_amount
                Cell expenseAmountCell = row.createCell(col++);
                expenseAmountCell.setCellValue(expense.getAmount().doubleValue());
                expenseAmountCell.setCellStyle(currencyStyle);

                // Column F: expense_category
                row.createCell(col++).setCellValue(expense.getCategory() != null ? expense.getCategory() : "");

                // Column G: expense_description
                row.createCell(col++).setCellValue(expense.getDescription() != null ? expense.getDescription() : "");

                rowNum++;
            }
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        log.info("EXPENSES sheet created with {} expense rows", rowNum - 1);
    }

    /**
     * Create MONTHLY_STATEMENT sheet with FORMULAS combining DUE and RECEIVED
     * Final output sheet with arrears calculation
     */
    private void createMonthlyStatementSheet(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                            LocalDate startDate, LocalDate endDate) {
        log.info("Creating MONTHLY_STATEMENT sheet with formulas");

        Sheet sheet = workbook.createSheet("MONTHLY_STATEMENT");

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_reference", "property_name", "customer_name", "lease_start_date", "rent_due_day", "month",
            "rent_due", "rent_received", "arrears", "management_fee", "service_fee",
            "total_commission", "total_expenses", "net_to_owner", "opening_balance", "cumulative_arrears"
        };

        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Styles
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);

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
                    // Use property-specific commission rate, fallback to global default if not set
                    double mgmtFeeRate = lease.getCommissionPercentage() != null
                        ? lease.getCommissionPercentage().doubleValue() / 100.0  // Property stores as percentage (e.g., 10), convert to decimal
                        : commissionConfig.getManagementFeePercent().doubleValue();
                    Cell mgmtFeeCell = row.createCell(col++);
                    mgmtFeeCell.setCellFormula(String.format("H%d * %.4f", rowNum + 1, mgmtFeeRate));
                    mgmtFeeCell.setCellStyle(currencyStyle);

                    // Column K: service_fee (formula: rent_received * service_fee_percent)
                    // Use property-specific service fee rate, fallback to global default if not set
                    double svcFeeRate = lease.getServiceFeePercentage() != null
                        ? lease.getServiceFeePercentage().doubleValue() / 100.0  // Property stores as percentage (e.g., 5), convert to decimal
                        : commissionConfig.getServiceFeePercent().doubleValue();
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

                    // Column O: opening_balance (formula: sum all arrears before statement period)
                    Cell openingBalanceCell = row.createCell(col++);
                    if (isFirstRowForLease) {
                        // Opening balance = (total rent due before period start) - (total rent received before period start)
                        openingBalanceCell.setCellFormula(String.format(
                            "SUMIFS(RENT_DUE!H:H, RENT_DUE!A:A, %d, RENT_DUE!D:D, \"<\"&F%d) - SUMIFS(RENT_RECEIVED!O:O, RENT_RECEIVED!A:A, %d, RENT_RECEIVED!E:E, \"<\"&F%d)",
                            lease.getLeaseId(), rowNum + 1, lease.getLeaseId(), rowNum + 1
                        ));
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

                    rowNum++;
                    isFirstRowForLease = false;
                }

                currentMonth = currentMonth.plusMonths(1);
            }
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        log.info("MONTHLY_STATEMENT sheet created with {} rows", rowNum - 1);
    }

    /**
     * Create a monthly statement sheet for a specific period
     * Used when generating separate sheets for each month
     */
    private void createMonthlyStatementSheetForPeriod(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                                      site.easy.to.build.crm.util.RentCyclePeriodCalculator.RentCyclePeriod period) {
        String sheetName = sanitizeSheetName(period.getSheetName());
        log.info("Creating monthly statement sheet: {}", sheetName);

        // Call the existing method with the period's date range
        createMonthlyStatementSheetWithName(workbook, leaseMaster, period.getStartDate(), period.getEndDate(), sheetName);
    }

    /**
     * Create MONTHLY_STATEMENT sheet with a custom name
     */
    private void createMonthlyStatementSheetWithName(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                                     LocalDate startDate, LocalDate endDate, String sheetName) {
        log.info("Creating {} sheet with formulas", sheetName);

        Sheet sheet = workbook.createSheet(sheetName);

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_reference", "property_name", "customer_name", "lease_start_date", "rent_due_day", "month",
            "rent_due", "rent_received", "arrears", "management_fee", "service_fee",
            "total_commission", "total_expenses", "net_to_owner", "opening_balance", "cumulative_arrears"
        };

        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Styles
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);

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
                Cell managementFeeCell = row.createCell(col++);
                managementFeeCell.setCellFormula(String.format("H%d * %.2f", rowNum + 1, commissionConfig.getManagementFeePercent().doubleValue()));
                managementFeeCell.setCellStyle(currencyStyle);

                // Column K: service_fee (formula: rent_received * service_fee_percent)
                Cell serviceFeeCell = row.createCell(col++);
                serviceFeeCell.setCellFormula(String.format("H%d * %.2f", rowNum + 1, commissionConfig.getServiceFeePercent().doubleValue()));
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

                // Column O: opening_balance (sum all rent due before this month minus all rent received before this month)
                Cell openingBalanceCell = row.createCell(col++);
                openingBalanceCell.setCellFormula(String.format(
                    "SUMIFS(RENT_DUE!H:H, RENT_DUE!A:A, %d, RENT_DUE!D:D, \"<\"&F%d) - SUMIFS(RENT_RECEIVED!O:O, RENT_RECEIVED!A:A, %d, RENT_RECEIVED!E:E, \"<\"&F%d)",
                    lease.getLeaseId(), rowNum + 1, lease.getLeaseId(), rowNum + 1
                ));
                openingBalanceCell.setCellStyle(currencyStyle);

                // Column P: cumulative_arrears (opening_balance + current month arrears)
                Cell cumulativeArrearsCell = row.createCell(col++);
                cumulativeArrearsCell.setCellFormula(String.format("O%d + I%d", rowNum + 1, rowNum + 1));
                cumulativeArrearsCell.setCellStyle(currencyStyle);

                rowNum++;

                currentMonth = currentMonth.plusMonths(1);
            }
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

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
                                                    LocalDate startDate, LocalDate endDate, int periodStartDay) {
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

        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Styles
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);

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
            List<CustomPeriod> leasePeriods = generateLeaseBasedPeriods(leaseStart, leaseEnd, startDate, endDate);

            for (CustomPeriod period : leasePeriods) {
                Row row = sheet.createRow(rowNum);
                int col = 0;

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

                // I: monthly_rent
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

                // L: prorated_rent_due
                // If this is a full period (period_days >= 28), use full monthly rent
                // If this is a partial period (e.g., lease ends mid-period), prorate
                Cell proratedRentCell = row.createCell(col++);
                // Check if period is prorated (less than ~28 days for a month)
                // Use formula: IF period_days < 28, prorate, else full rent
                proratedRentCell.setCellFormula(String.format(
                    "IF(F%d < 28, ROUND(F%d / DAY(EOMONTH(D%d, 0)) * I%d, 2), I%d)",
                    rowNum + 1, // period_days
                    rowNum + 1, // period_days for proration
                    rowNum + 1, // period_start for month length
                    rowNum + 1, // monthly_rent
                    rowNum + 1  // monthly_rent (full)
                ));
                proratedRentCell.setCellStyle(currencyStyle);

                // M: management_fee (from config)
                Cell mgmtFeeCell = row.createCell(col++);
                mgmtFeeCell.setCellFormula(String.format("L%d * %.2f", rowNum + 1, commissionConfig.getManagementFeePercent().doubleValue()));
                mgmtFeeCell.setCellStyle(currencyStyle);

                // N: service_fee (from config)
                Cell svcFeeCell = row.createCell(col++);
                svcFeeCell.setCellFormula(String.format("L%d * %.2f", rowNum + 1, commissionConfig.getServiceFeePercent().doubleValue()));
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

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        log.info("RENT_DUE sheet created with {} rows (lease-based periods)", rowNum - 1);
    }

    /**
     * Create RENT_RECEIVED sheet with custom periods and payment breakdown
     * Shows individual payment dates and amounts (up to 4 payments per period)
     */
    private void createRentReceivedSheetWithCustomPeriods(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                                         LocalDate startDate, LocalDate endDate, int periodStartDay) {
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

        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Styles
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);

        int rowNum = 1;

        // Generate custom periods
        List<CustomPeriod> periods = generateCustomPeriods(startDate, endDate, periodStartDay);

        // Generate rows for each lease × custom period that has started
        // Show active leases AND ended leases, but NOT future leases that haven't started
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();
            LocalDate leaseEnd = lease.getEndDate();

            for (CustomPeriod period : periods) {
                // Skip future leases that haven't started yet (lease start is after period end)
                if (leaseStart != null && leaseStart.isAfter(period.periodEnd)) {
                    continue; // Don't show leases that haven't started yet
                }

                // Create row for leases that have started (payments can come in even for ended leases)
                Row row = sheet.createRow(rowNum);
                int col = 0;

                // Get payment details for this lease/period (payments can exist even for inactive leases)
                List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> payments =
                    dataExtractService.extractRentReceivedDetails(lease.getLeaseId(), period.periodStart, period.periodEnd);

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
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        log.info("RENT_RECEIVED sheet created with {} rows (custom periods)", rowNum - 1);
    }

    /**
     * Create a monthly statement sheet for a specific custom period
     */
    private void createMonthlyStatementSheetForCustomPeriod(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                                           CustomPeriod period, String sheetName) {
        log.info("Creating monthly statement sheet: {}", sheetName);

        Sheet sheet = workbook.createSheet(sheetName);

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_reference", "property_name", "customer_name", "tenant_name", "lease_start_date", "rent_due_day",
            "rent_due", "rent_received", "opening_balance", "period_arrears", "closing_balance",
            "management_fee", "service_fee", "total_commission", "total_expenses", "net_to_owner"
        };

        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Styles
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);

        int rowNum = 1;

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
            Cell rentReceivedCell = row.createCell(col++);
            rentReceivedCell.setCellFormula(String.format(
                "IFERROR(SUMIFS(RENT_RECEIVED!O:O, RENT_RECEIVED!B:B, \"%s\", RENT_RECEIVED!E:E, DATE(%d,%d,%d)), 0)",
                lease.getLeaseReference(),
                period.periodStart.getYear(),
                period.periodStart.getMonthValue(),
                period.periodStart.getDayOfMonth()
            ));
            rentReceivedCell.setCellStyle(currencyStyle);

            // I: opening_balance (cumulative arrears BEFORE this period)
            // Formula: SUM(all rent due for periods starting before this billing period) - SUM(all rent received before period start)
            // IMPORTANT: Must match rent_due logic - we count rent due where period_start falls within billing period
            // So opening_balance = sum where period_start < billing_period_start
            // RENT_DUE: B=lease_reference, D=period_start, L=prorated_rent_due
            // RENT_RECEIVED: B=lease_reference, E=period_start, O=total_received
            Cell openingBalanceCell = row.createCell(col++);
            openingBalanceCell.setCellFormula(String.format(
                "SUMIFS(RENT_DUE!L:L, RENT_DUE!B:B, A%d, RENT_DUE!D:D, \"<\"&DATE(%d,%d,%d)) - SUMIFS(RENT_RECEIVED!O:O, RENT_RECEIVED!B:B, A%d, RENT_RECEIVED!E:E, \"<\"&DATE(%d,%d,%d))",
                rowNum + 1,
                period.periodStart.getYear(), period.periodStart.getMonthValue(), period.periodStart.getDayOfMonth(),
                rowNum + 1,
                period.periodStart.getYear(), period.periodStart.getMonthValue(), period.periodStart.getDayOfMonth()
            ));
            openingBalanceCell.setCellStyle(currencyStyle);

            // J: period_arrears (this period only: rent_due - rent_received)
            Cell periodArrearsCell = row.createCell(col++);
            periodArrearsCell.setCellFormula(String.format("G%d - H%d", rowNum + 1, rowNum + 1));
            periodArrearsCell.setCellStyle(currencyStyle);

            // K: closing_balance (opening + period arrears = cumulative position at end of period)
            Cell closingBalanceCell = row.createCell(col++);
            closingBalanceCell.setCellFormula(String.format("I%d + J%d", rowNum + 1, rowNum + 1));
            closingBalanceCell.setCellStyle(currencyStyle);

            // L: management_fee (formula: rent_received * management_fee_percent) - H is rent_received
            // Use property-specific commission rate, fallback to global default if not set
            double mgmtFeeRate = lease.getCommissionPercentage() != null
                ? lease.getCommissionPercentage().doubleValue() / 100.0  // Property stores as percentage (e.g., 10), convert to decimal (0.10)
                : commissionConfig.getManagementFeePercent().doubleValue();
            Cell mgmtFeeCell = row.createCell(col++);
            mgmtFeeCell.setCellFormula(String.format("H%d * %.4f", rowNum + 1, mgmtFeeRate));
            mgmtFeeCell.setCellStyle(currencyStyle);

            // M: service_fee (formula: rent_received * service_fee_percent) - H is rent_received
            // Use property-specific service fee rate, fallback to global default if not set
            double svcFeeRate = lease.getServiceFeePercentage() != null
                ? lease.getServiceFeePercentage().doubleValue() / 100.0  // Property stores as percentage (e.g., 5), convert to decimal (0.05)
                : commissionConfig.getServiceFeePercent().doubleValue();
            Cell svcFeeCell = row.createCell(col++);
            svcFeeCell.setCellFormula(String.format("H%d * %.4f", rowNum + 1, svcFeeRate));
            svcFeeCell.setCellStyle(currencyStyle);

            // N: total_commission (formula: ABS(mgmt + svc)) - L is mgmt_fee, M is svc_fee
            Cell totalCommCell = row.createCell(col++);
            totalCommCell.setCellFormula(String.format("ABS(L%d + M%d)", rowNum + 1, rowNum + 1));
            totalCommCell.setCellStyle(currencyStyle);

            // O: total_expenses (SUMIFS to EXPENSES sheet)
            // ✅ NEW: Flat EXPENSES sheet - sum expenses where expense_date falls within the period
            Cell expensesCell = row.createCell(col++);
            expensesCell.setCellFormula(String.format(
                "SUMIFS(EXPENSES!E:E, EXPENSES!B:B, \"%s\", EXPENSES!D:D, \">=\"&DATE(%d,%d,%d), EXPENSES!D:D, \"<=\"&DATE(%d,%d,%d))",
                lease.getLeaseReference(),
                period.periodStart.getYear(), period.periodStart.getMonthValue(), period.periodStart.getDayOfMonth(),
                period.periodEnd.getYear(), period.periodEnd.getMonthValue(), period.periodEnd.getDayOfMonth()
            ));
            expensesCell.setCellStyle(currencyStyle);

            // P: net_to_owner (formula: rent_received - total_commission - expenses) - H is rent_received, N is commission, O is expenses
            Cell netToOwnerCell = row.createCell(col++);
            netToOwnerCell.setCellFormula(String.format("H%d - N%d - O%d", rowNum + 1, rowNum + 1, rowNum + 1));
            netToOwnerCell.setCellStyle(currencyStyle);

            rowNum++;
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

            // I: total_opening_balance
            Cell totalOpeningBalanceCell = totalsRow.createCell(col++);
            totalOpeningBalanceCell.setCellFormula(String.format("SUM(I2:I%d)", rowNum));
            totalOpeningBalanceCell.setCellStyle(boldCurrencyStyle);

            // J: total_period_arrears
            Cell totalPeriodArrearsCell = totalsRow.createCell(col++);
            totalPeriodArrearsCell.setCellFormula(String.format("SUM(J2:J%d)", rowNum));
            totalPeriodArrearsCell.setCellStyle(boldCurrencyStyle);

            // K: total_closing_balance
            Cell totalClosingBalanceCell = totalsRow.createCell(col++);
            totalClosingBalanceCell.setCellFormula(String.format("SUM(K2:K%d)", rowNum));
            totalClosingBalanceCell.setCellStyle(boldCurrencyStyle);

            // L: total_management_fee
            Cell totalMgmtFeeCell = totalsRow.createCell(col++);
            totalMgmtFeeCell.setCellFormula(String.format("SUM(L2:L%d)", rowNum));
            totalMgmtFeeCell.setCellStyle(boldCurrencyStyle);

            // M: total_service_fee
            Cell totalSvcFeeCell = totalsRow.createCell(col++);
            totalSvcFeeCell.setCellFormula(String.format("SUM(M2:M%d)", rowNum));
            totalSvcFeeCell.setCellStyle(boldCurrencyStyle);

            // N: total_commission
            Cell totalCommCell = totalsRow.createCell(col++);
            totalCommCell.setCellFormula(String.format("SUM(N2:N%d)", rowNum));
            totalCommCell.setCellStyle(boldCurrencyStyle);

            // O: total_expenses
            Cell totalExpensesCell = totalsRow.createCell(col++);
            totalExpensesCell.setCellFormula(String.format("SUM(O2:O%d)", rowNum));
            totalExpensesCell.setCellStyle(boldCurrencyStyle);

            // P: total_net_to_owner
            Cell totalNetCell = totalsRow.createCell(col++);
            totalNetCell.setCellFormula(String.format("SUM(P2:P%d)", rowNum));
            totalNetCell.setCellStyle(boldCurrencyStyle);
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        log.info("{} sheet created with {} rows", sheetName, rowNum - 1);
    }

    /**
     * Create SUMMARY sheet that totals across all custom periods
     */
    private void createSummarySheetForCustomPeriods(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                                   List<CustomPeriod> periods, LocalDate startDate, LocalDate endDate) {
        log.info("Creating SUMMARY sheet for custom periods");

        Sheet sheet = workbook.createSheet("SUMMARY");

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_reference", "property_name", "customer_name", "tenant_name", "lease_start_date", "rent_due_day",
            "total_rent_due", "total_rent_received", "total_arrears", "total_management_fee", "total_service_fee",
            "total_commission", "total_expenses", "total_net_to_owner", "opening_balance", "closing_balance"
        };

        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Styles
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);

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

                // G: total_rent_received (SUM across all periods in RENT_RECEIVED sheet)
                Cell totalRentReceivedCell = row.createCell(col++);
                totalRentReceivedCell.setCellFormula(String.format(
                    "SUMIFS(RENT_RECEIVED!O:O, RENT_RECEIVED!B:B, \"%s\", RENT_RECEIVED!E:E, \">=\"&DATE(%d,%d,%d), RENT_RECEIVED!E:E, \"<=\"&DATE(%d,%d,%d))",
                    lease.getLeaseReference(),
                    startDate.getYear(), startDate.getMonthValue(), startDate.getDayOfMonth(),
                    endDate.getYear(), endDate.getMonthValue(), endDate.getDayOfMonth()
                ));
                totalRentReceivedCell.setCellStyle(currencyStyle);

                // I: total_arrears (formula: total_due - total_received)
                Cell totalArrearsCell = row.createCell(col++);
                totalArrearsCell.setCellFormula(String.format("G%d - H%d", rowNum + 1, rowNum + 1));
                totalArrearsCell.setCellStyle(currencyStyle);

                // J: total_management_fee
                Cell totalMgmtFeeCell = row.createCell(col++);
                totalMgmtFeeCell.setCellFormula(String.format("H%d * %.2f", rowNum + 1, commissionConfig.getManagementFeePercent().doubleValue()));
                totalMgmtFeeCell.setCellStyle(currencyStyle);

                // K: total_service_fee
                Cell totalSvcFeeCell = row.createCell(col++);
                totalSvcFeeCell.setCellFormula(String.format("H%d * %.2f", rowNum + 1, commissionConfig.getServiceFeePercent().doubleValue()));
                totalSvcFeeCell.setCellStyle(currencyStyle);

                // L: total_commission
                Cell totalCommCell = row.createCell(col++);
                totalCommCell.setCellFormula(String.format("J%d + K%d", rowNum + 1, rowNum + 1));
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

                // O: opening_balance (arrears before first period start)
                Cell openingBalanceCell = row.createCell(col++);
                openingBalanceCell.setCellFormula(String.format(
                    "SUMIFS(RENT_DUE!L:L, RENT_DUE!B:B, \"%s\", RENT_DUE!D:D, \"<\"&DATE(%d,%d,%d)) - SUMIFS(RENT_RECEIVED!O:O, RENT_RECEIVED!B:B, \"%s\", RENT_RECEIVED!E:E, \"<\"&DATE(%d,%d,%d))",
                    lease.getLeaseReference(),
                    startDate.getYear(), startDate.getMonthValue(), startDate.getDayOfMonth(),
                    lease.getLeaseReference(),
                    startDate.getYear(), startDate.getMonthValue(), startDate.getDayOfMonth()
                ));
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

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        log.info("SUMMARY sheet created with {} rows", rowNum - 1);
    }

    /**
     * Create MONTHLY_STATEMENT sheet with custom periods
     */
    private void createMonthlyStatementSheetWithCustomPeriods(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                                             LocalDate startDate, LocalDate endDate, int periodStartDay) {
        log.info("Creating MONTHLY_STATEMENT sheet with custom periods");

        Sheet sheet = workbook.createSheet("MONTHLY_STATEMENT");

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_reference", "property_name", "customer_name", "lease_start_date", "rent_due_day", "period",
            "rent_due", "rent_received", "arrears", "management_fee", "service_fee",
            "total_commission", "total_expenses", "net_to_owner", "opening_balance", "cumulative_arrears"
        };

        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Styles
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);

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

                    // G: rent_due (SUMIFS to RENT_DUE sheet - more reliable than INDEX/MATCH array formula)
                    Cell rentDueCell = row.createCell(col++);
                    rentDueCell.setCellFormula(String.format(
                        "IFERROR(SUMIFS(RENT_DUE!L:L, RENT_DUE!B:B, \"%s\", RENT_DUE!D:D, DATE(%d,%d,%d)), 0)",
                        lease.getLeaseReference(),
                        period.periodStart.getYear(),
                        period.periodStart.getMonthValue(),
                        period.periodStart.getDayOfMonth()
                    ));
                    rentDueCell.setCellStyle(currencyStyle);

                    // H: rent_received (SUMIFS to RENT_RECEIVED sheet - more reliable than INDEX/MATCH array formula)
                    Cell rentReceivedCell = row.createCell(col++);
                    rentReceivedCell.setCellFormula(String.format(
                        "IFERROR(SUMIFS(RENT_RECEIVED!O:O, RENT_RECEIVED!B:B, \"%s\", RENT_RECEIVED!E:E, DATE(%d,%d,%d)), 0)",
                        lease.getLeaseReference(),
                        period.periodStart.getYear(),
                        period.periodStart.getMonthValue(),
                        period.periodStart.getDayOfMonth()
                    ));
                    rentReceivedCell.setCellStyle(currencyStyle);

                    // I: arrears (formula: due - received)
                    Cell arrearsCell = row.createCell(col++);
                    arrearsCell.setCellFormula(String.format("G%d - H%d", rowNum + 1, rowNum + 1));
                    arrearsCell.setCellStyle(currencyStyle);

                    // J: management_fee (formula: rent_received * management_fee_percent)
                    Cell mgmtFeeCell = row.createCell(col++);
                    mgmtFeeCell.setCellFormula(String.format("H%d * %.2f", rowNum + 1, commissionConfig.getManagementFeePercent().doubleValue()));
                    mgmtFeeCell.setCellStyle(currencyStyle);

                    // K: service_fee (formula: rent_received * service_fee_percent)
                    Cell svcFeeCell = row.createCell(col++);
                    svcFeeCell.setCellFormula(String.format("H%d * %.2f", rowNum + 1, commissionConfig.getServiceFeePercent().doubleValue()));
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

                    // O: opening_balance (formula: sum all arrears before statement period)
                    Cell openingBalanceCell = row.createCell(col++);
                    if (isFirstPeriodForLease) {
                        // Opening balance = (total rent due before first period start) - (total rent received before first period start)
                        openingBalanceCell.setCellFormula(String.format(
                            "SUMIFS(RENT_DUE!H:H, RENT_DUE!A:A, %d, RENT_DUE!D:D, \"<\"&DATE(%d,%d,%d)) - SUMIFS(RENT_RECEIVED!O:O, RENT_RECEIVED!A:A, %d, RENT_RECEIVED!E:E, \"<\"&DATE(%d,%d,%d))",
                            lease.getLeaseId(),
                            period.periodStart.getYear(), period.periodStart.getMonthValue(), period.periodStart.getDayOfMonth(),
                            lease.getLeaseId(),
                            period.periodStart.getYear(), period.periodStart.getMonthValue(), period.periodStart.getDayOfMonth()
                        ));
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

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        log.info("MONTHLY_STATEMENT sheet created with {} rows (custom periods)", rowNum - 1);
    }

    // ============================================================================
    // Cell Style Helpers
    // ============================================================================

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("yyyy-mm-dd"));
        return style;
    }

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
                log.error("🔍 DEBUG: Customer {} not found, returning same ID", customerId);
                return customerId;
            }

            log.error("🔍 DEBUG: Customer {} type={}, managesOwnerId={}",
                    customerId, customer.getCustomerType(), customer.getManagesOwnerId());

            // If delegated user or manager with an assigned owner, return the owner they manage
            if ((customer.getCustomerType() == site.easy.to.build.crm.entity.CustomerType.DELEGATED_USER ||
                 customer.getCustomerType() == site.easy.to.build.crm.entity.CustomerType.MANAGER) &&
                customer.getManagesOwnerId() != null) {

                log.error("🔍 DEBUG: Delegated user/manager {} manages owner {}, using that ID",
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
    private void createIncomeAllocationsSheet(Workbook workbook, Long ownerId) {
        log.error("🔍 DEBUG: Creating Income Allocations sheet for owner {}", ownerId);

        // FORENSIC: Get ALL allocations for this owner to understand the data
        List<site.easy.to.build.crm.entity.TransactionBatchAllocation> allAllocations =
            allocationRepository.findByBeneficiaryId(ownerId);
        log.error("🔍 FORENSIC: Found {} TOTAL allocations for owner {}", allAllocations.size(), ownerId);

        for (site.easy.to.build.crm.entity.TransactionBatchAllocation alloc : allAllocations) {
            log.error("🔍 FORENSIC: Allocation id={}, txnId={}, batch={}, allocatedAmount={}, property={}",
                alloc.getId(), alloc.getTransactionId(), alloc.getBatchReference(),
                alloc.getAllocatedAmount(), alloc.getPropertyName());
        }

        Sheet sheet = workbook.createSheet("Income Allocations");
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);

        // Headers
        String[] headers = {"Date", "Property", "Category", "Description", "Tenant", "Gross Amount",
                           "Commission", "Net To Owner", "Batch Ref", "Allocated Amount", "Payment Date", "Payment Status"};

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Get income allocations from repository (allocatedAmount > 0)
        List<Object[]> allocations = allocationRepository.getIncomeAllocationsForOwner(ownerId);
        log.error("🔍 DEBUG: Found {} income allocations (allocatedAmount > 0) for owner {}", allocations.size(), ownerId);

        // Debug: Check if there are ANY allocations in the system for this owner
        List<String> allBatches = allocationRepository.findDistinctBatchReferencesByBeneficiaryId(ownerId);
        log.error("🔍 DEBUG: Found {} batch references for owner {}: {}", allBatches.size(), ownerId, allBatches);

        int rowNum = 1;
        BigDecimal totalAllocated = BigDecimal.ZERO;

        for (Object[] allocation : allocations) {
            Row row = sheet.createRow(rowNum++);

            // Parse allocation data
            // Returns: transactionId, transactionDate, propertyName, category, amount, netToOwner, commission, batchRef, allocatedAmount, description, tenantName
            java.sql.Date transactionDate = allocation[1] != null ? java.sql.Date.valueOf(allocation[1].toString()) : null;
            String propertyName = allocation[2] != null ? allocation[2].toString() : "";
            String category = allocation[3] != null ? allocation[3].toString() : "";
            BigDecimal amount = allocation[4] != null ? new BigDecimal(allocation[4].toString()) : BigDecimal.ZERO;
            BigDecimal netToOwner = allocation[5] != null ? new BigDecimal(allocation[5].toString()) : BigDecimal.ZERO;
            BigDecimal commission = allocation[6] != null ? new BigDecimal(allocation[6].toString()) : BigDecimal.ZERO;
            String batchRef = allocation[7] != null ? allocation[7].toString() : "";
            BigDecimal allocatedAmount = allocation[8] != null ? new BigDecimal(allocation[8].toString()) : BigDecimal.ZERO;
            String description = allocation[9] != null ? allocation[9].toString() : "";
            String tenantName = allocation[10] != null ? allocation[10].toString() : "";

            // Get payment batch details
            PaymentBatch batch = paymentBatchRepository.findByBatchId(batchRef).orElse(null);
            LocalDate paymentDate = batch != null ? batch.getPaymentDate() : null;
            String paymentStatus = batch != null ? batch.getStatus().toString() : "Unknown";

            // Date
            Cell dateCell = row.createCell(0);
            if (transactionDate != null) {
                dateCell.setCellValue(transactionDate);
                dateCell.setCellStyle(dateStyle);
            }

            // Property
            row.createCell(1).setCellValue(propertyName);

            // Category
            row.createCell(2).setCellValue(category);

            // Description
            row.createCell(3).setCellValue(description);

            // Tenant
            row.createCell(4).setCellValue(tenantName);

            // Gross Amount
            Cell amountCell = row.createCell(5);
            amountCell.setCellValue(amount.doubleValue());
            amountCell.setCellStyle(currencyStyle);

            // Commission
            Cell commCell = row.createCell(6);
            commCell.setCellValue(commission.doubleValue());
            commCell.setCellStyle(currencyStyle);

            // Net To Owner
            Cell netCell = row.createCell(7);
            netCell.setCellValue(netToOwner.doubleValue());
            netCell.setCellStyle(currencyStyle);

            // Batch Ref
            row.createCell(8).setCellValue(batchRef);

            // Allocated Amount
            Cell allocCell = row.createCell(9);
            allocCell.setCellValue(allocatedAmount.doubleValue());
            allocCell.setCellStyle(currencyStyle);
            totalAllocated = totalAllocated.add(allocatedAmount);

            // Payment Date
            Cell payDateCell = row.createCell(10);
            if (paymentDate != null) {
                payDateCell.setCellValue(java.sql.Date.valueOf(paymentDate));
                payDateCell.setCellStyle(dateStyle);
            }

            // Payment Status
            row.createCell(11).setCellValue(paymentStatus);
        }

        // Total row
        Row totalRow = sheet.createRow(rowNum);
        Cell totalLabel = totalRow.createCell(8);
        totalLabel.setCellValue("TOTAL:");
        totalLabel.setCellStyle(headerStyle);

        Cell totalCell = totalRow.createCell(9);
        totalCell.setCellValue(totalAllocated.doubleValue());
        totalCell.setCellStyle(currencyStyle);

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        log.info("Income Allocations sheet created with {} rows, total: {}", allocations.size(), totalAllocated);
    }

    /**
     * Create Expense Allocations sheet showing all expense transactions allocated to payment batches
     */
    private void createExpenseAllocationsSheet(Workbook workbook, Long ownerId) {
        log.error("🔍 DEBUG: Creating Expense Allocations sheet for owner {}", ownerId);

        Sheet sheet = workbook.createSheet("Expense Allocations");
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);

        // Headers
        String[] headers = {"Date", "Property", "Category", "Description", "Amount",
                           "Batch Ref", "Allocated Amount", "Payment Date", "Payment Status"};

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Get expense allocations from repository
        List<Object[]> allocations = allocationRepository.getExpenseAllocationsForOwner(ownerId);
        log.error("🔍 DEBUG: Found {} expense allocations for owner {}", allocations.size(), ownerId);

        int rowNum = 1;
        BigDecimal totalAllocated = BigDecimal.ZERO;

        for (Object[] allocation : allocations) {
            Row row = sheet.createRow(rowNum++);

            // Parse allocation data
            // Returns: transactionId, transactionDate, propertyName, category, amount, batchRef, allocatedAmount, description
            java.sql.Date transactionDate = allocation[1] != null ? java.sql.Date.valueOf(allocation[1].toString()) : null;
            String propertyName = allocation[2] != null ? allocation[2].toString() : "";
            String category = allocation[3] != null ? allocation[3].toString() : "";
            BigDecimal amount = allocation[4] != null ? new BigDecimal(allocation[4].toString()) : BigDecimal.ZERO;
            String batchRef = allocation[5] != null ? allocation[5].toString() : "";
            BigDecimal allocatedAmount = allocation[6] != null ? new BigDecimal(allocation[6].toString()) : BigDecimal.ZERO;
            String description = allocation[7] != null ? allocation[7].toString() : "";

            // Get payment batch details
            PaymentBatch batch = paymentBatchRepository.findByBatchId(batchRef).orElse(null);
            LocalDate paymentDate = batch != null ? batch.getPaymentDate() : null;
            String paymentStatus = batch != null ? batch.getStatus().toString() : "Unknown";

            // Date
            Cell dateCell = row.createCell(0);
            if (transactionDate != null) {
                dateCell.setCellValue(transactionDate);
                dateCell.setCellStyle(dateStyle);
            }

            // Property
            row.createCell(1).setCellValue(propertyName);

            // Category
            row.createCell(2).setCellValue(category);

            // Description
            row.createCell(3).setCellValue(description);

            // Amount
            Cell amountCell = row.createCell(4);
            amountCell.setCellValue(amount.doubleValue());
            amountCell.setCellStyle(currencyStyle);

            // Batch Ref
            row.createCell(5).setCellValue(batchRef);

            // Allocated Amount
            Cell allocCell = row.createCell(6);
            allocCell.setCellValue(allocatedAmount.doubleValue());
            allocCell.setCellStyle(currencyStyle);
            totalAllocated = totalAllocated.add(allocatedAmount);

            // Payment Date
            Cell payDateCell = row.createCell(7);
            if (paymentDate != null) {
                payDateCell.setCellValue(java.sql.Date.valueOf(paymentDate));
                payDateCell.setCellStyle(dateStyle);
            }

            // Payment Status
            row.createCell(8).setCellValue(paymentStatus);
        }

        // Total row
        Row totalRow = sheet.createRow(rowNum);
        Cell totalLabel = totalRow.createCell(5);
        totalLabel.setCellValue("TOTAL:");
        totalLabel.setCellStyle(headerStyle);

        Cell totalCell = totalRow.createCell(6);
        totalCell.setCellValue(totalAllocated.doubleValue());
        totalCell.setCellStyle(currencyStyle);

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        log.info("Expense Allocations sheet created with {} rows, total: {}", allocations.size(), totalAllocated);
    }

    /**
     * Create Owner Payments Summary sheet showing all payment batches for this owner
     */
    private void createOwnerPaymentsSummarySheet(Workbook workbook, Long ownerId) {
        log.error("🔍 DEBUG: Creating Owner Payments Summary sheet for owner {}", ownerId);

        Sheet sheet = workbook.createSheet("Owner Payments Summary");
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);

        // Headers
        String[] headers = {"Batch Reference", "Payment Date", "Total Allocations", "Balance Adjustment",
                           "Total Payment", "Status", "Payment Method", "Notes"};

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Get all batch references for this owner
        List<String> batchRefs = allocationRepository.findDistinctBatchReferencesByBeneficiaryId(ownerId);
        log.error("🔍 DEBUG: Found {} payment batches for owner {}: {}", batchRefs.size(), ownerId, batchRefs);

        // Debug: Check all batch references in the system
        List<String> allBatchRefs = allocationRepository.findAllDistinctBatchReferences();
        log.error("🔍 DEBUG: All batch references in system: {}", allBatchRefs);

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

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        log.info("Owner Payments Summary sheet created with {} batches, total: {}", batchRefs.size(), totalPayments);
    }
}

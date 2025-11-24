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

        // Generate one row per lease per month
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();
            LocalDate leaseEnd = lease.getEndDate();

            // Generate monthly rows from LEASE START to statement end (for accurate cumulative arrears)
            YearMonth currentMonth = leaseStart != null ? YearMonth.from(leaseStart) : YearMonth.from(startDate);
            YearMonth endMonth = YearMonth.from(endDate);

            while (!currentMonth.isAfter(endMonth)) {
                LocalDate monthStart = currentMonth.atDay(1);
                LocalDate monthEnd = currentMonth.atEndOfMonth();

                // Only include if lease overlaps with this month
                boolean leaseActive = (leaseEnd == null || !monthEnd.isBefore(leaseStart))
                                   && !monthStart.isAfter(leaseEnd != null ? leaseEnd : monthEnd);

                if (leaseActive) {
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

                    // Column G: lease_days_in_month (formula with VLOOKUP to LEASE_MASTER)
                    // Formula: MAX(0, MIN(lease_end, month_end) - MAX(lease_start, month_start) + 1)
                    String leaseDaysFormula = String.format(
                        "MAX(0, MIN(IF(ISBLANK(VLOOKUP(A%d,LEASE_MASTER!A:I,9,FALSE)), E%d, VLOOKUP(A%d,LEASE_MASTER!A:I,9,FALSE)), E%d) - MAX(VLOOKUP(A%d,LEASE_MASTER!A:I,8,FALSE), D%d) + 1)",
                        rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1
                    );
                    row.createCell(col++).setCellFormula(leaseDaysFormula);

                    // Column H: prorated_rent_due (formula - only pro-rate at lease END, not start)
                    Cell proratedRentCell = row.createCell(col++);
                    proratedRentCell.setCellFormula(String.format(
                        "IF(AND(NOT(ISBLANK(VLOOKUP(A%d,LEASE_MASTER!A:I,9,FALSE))), VLOOKUP(A%d,LEASE_MASTER!A:I,9,FALSE)>=D%d, VLOOKUP(A%d,LEASE_MASTER!A:I,9,FALSE)<E%d), ROUND((VLOOKUP(A%d,LEASE_MASTER!A:I,9,FALSE)-D%d+1)/F%d*VLOOKUP(A%d,LEASE_MASTER!A:J,10,FALSE), 2), VLOOKUP(A%d,LEASE_MASTER!A:J,10,FALSE))",
                        rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1
                    ));
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
                }

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

        // Generate one row per lease per month (from LEASE START for historical data)
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();
            LocalDate leaseEnd = lease.getEndDate();

            // Start from lease start to capture all historical payments for opening balance calculation
            YearMonth currentMonth = leaseStart != null ? YearMonth.from(leaseStart) : YearMonth.from(startDate);
            YearMonth endMonth = YearMonth.from(endDate);

            while (!currentMonth.isAfter(endMonth)) {
                LocalDate monthStart = currentMonth.atDay(1);
                LocalDate monthEnd = currentMonth.atEndOfMonth();

                boolean leaseActive = (leaseEnd == null || !monthEnd.isBefore(leaseStart))
                                   && !monthStart.isAfter(leaseEnd != null ? leaseEnd : monthEnd);

                if (leaseActive) {
                    Row row = sheet.createRow(rowNum);
                    int col = 0;

                    // Get payment details for this lease/month
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
                }

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
        log.info("Creating EXPENSES sheet with expense breakdown");

        Sheet sheet = workbook.createSheet("EXPENSES");

        // Header row with expense breakdown columns
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_id", "lease_reference", "property_name",
            "month_start", "month_end",
            "expense_1_date", "expense_1_amount", "expense_1_category",
            "expense_2_date", "expense_2_amount", "expense_2_category",
            "expense_3_date", "expense_3_amount", "expense_3_category",
            "expense_4_date", "expense_4_amount", "expense_4_category",
            "total_expenses"
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

        // Generate one row per lease per month (from LEASE START for historical data)
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();
            LocalDate leaseEnd = lease.getEndDate();

            // Start from lease start to capture all historical expenses
            YearMonth currentMonth = leaseStart != null ? YearMonth.from(leaseStart) : YearMonth.from(startDate);
            YearMonth endMonth = YearMonth.from(endDate);

            while (!currentMonth.isAfter(endMonth)) {
                LocalDate monthStart = currentMonth.atDay(1);
                LocalDate monthEnd = currentMonth.atEndOfMonth();

                boolean leaseActive = (leaseEnd == null || !monthEnd.isBefore(leaseStart))
                                   && !monthStart.isAfter(leaseEnd != null ? leaseEnd : monthEnd);

                if (leaseActive) {
                    // Get expense details for this lease/month
                    List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> expenses =
                        dataExtractService.extractExpenseDetails(lease.getLeaseId(), monthStart, monthEnd);

                    // Only create row if there are expenses for this month
                    if (!expenses.isEmpty()) {
                        Row row = sheet.createRow(rowNum);
                        int col = 0;

                        // Column A: lease_id
                        row.createCell(col++).setCellValue(lease.getLeaseId());

                        // Column B: lease_reference
                        row.createCell(col++).setCellValue(lease.getLeaseReference());

                        // Column C: property_name
                        row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");

                        // Column D: month_start
                        Cell monthStartCell = row.createCell(col++);
                        monthStartCell.setCellValue(monthStart);
                        monthStartCell.setCellStyle(dateStyle);

                        // Column E: month_end
                        Cell monthEndCell = row.createCell(col++);
                        monthEndCell.setCellValue(monthEnd);
                        monthEndCell.setCellStyle(dateStyle);

                        // Columns F-P: Expense breakdown (up to 4 expenses)
                        BigDecimal total = BigDecimal.ZERO;
                        for (int i = 0; i < 4; i++) {
                            if (i < expenses.size()) {
                                site.easy.to.build.crm.dto.statement.PaymentDetailDTO expense = expenses.get(i);

                                // Expense date
                                Cell expenseDateCell = row.createCell(col++);
                                expenseDateCell.setCellValue(expense.getPaymentDate());
                                expenseDateCell.setCellStyle(dateStyle);

                                // Expense amount
                                Cell expenseAmountCell = row.createCell(col++);
                                expenseAmountCell.setCellValue(expense.getAmount().doubleValue());
                                expenseAmountCell.setCellStyle(currencyStyle);

                                // Expense category
                                row.createCell(col++).setCellValue(expense.getCategory() != null ? expense.getCategory() : "");

                                total = total.add(expense.getAmount());
                            } else {
                                // Empty expense columns
                                row.createCell(col++); // date
                                row.createCell(col++); // amount
                                row.createCell(col++); // category
                            }
                        }

                        // Column Q: total_expenses
                        Cell totalExpensesCell = row.createCell(col++);
                        totalExpensesCell.setCellValue(total.doubleValue());
                        totalExpensesCell.setCellStyle(currencyStyle);

                        rowNum++;
                    }
                }

                currentMonth = currentMonth.plusMonths(1);
            }
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        log.info("EXPENSES sheet created with {} rows", rowNum - 1);
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
                    Cell mgmtFeeCell = row.createCell(col++);
                    mgmtFeeCell.setCellFormula(String.format("H%d * %.2f", rowNum + 1, commissionConfig.getManagementFeePercent().doubleValue()));
                    mgmtFeeCell.setCellStyle(currencyStyle);

                    // Column K: service_fee (formula: rent_received * service_fee_percent)
                    Cell svcFeeCell = row.createCell(col++);
                    svcFeeCell.setCellFormula(String.format("H%d * %.2f", rowNum + 1, commissionConfig.getServiceFeePercent().doubleValue()));
                    svcFeeCell.setCellStyle(currencyStyle);

                    // Column L: total_commission (formula: mgmt + svc)
                    Cell totalCommCell = row.createCell(col++);
                    totalCommCell.setCellFormula(String.format("J%d + K%d", rowNum + 1, rowNum + 1));
                    totalCommCell.setCellStyle(currencyStyle);

                    // Column M: total_expenses (SUMIFS to EXPENSES sheet)
                    Cell expensesCell = row.createCell(col++);
                    expensesCell.setCellFormula(String.format(
                        "SUMIFS(EXPENSES!R:R, EXPENSES!A:A, %d, EXPENSES!D:D, F%d)",
                        lease.getLeaseId(), rowNum + 1
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

        // Generate one row per lease per month (statement period only)
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();
            LocalDate leaseEnd = lease.getEndDate();

            // Generate monthly rows for STATEMENT PERIOD only
            YearMonth currentMonth = YearMonth.from(startDate);
            YearMonth endMonth = YearMonth.from(endDate);

            while (!currentMonth.isAfter(endMonth)) {
                LocalDate monthStart = currentMonth.atDay(1);
                LocalDate monthEnd = currentMonth.atEndOfMonth();

                boolean leaseActive = (leaseEnd == null || !monthEnd.isBefore(leaseStart))
                                   && !monthStart.isAfter(leaseEnd != null ? leaseEnd : monthEnd);

                if (leaseActive) {
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

                    // Column G: rent_due (SUMIFS to RENT_DUE sheet)
                    Cell rentDueCell = row.createCell(col++);
                    rentDueCell.setCellFormula(String.format(
                        "SUMIFS(RENT_DUE!H:H, RENT_DUE!A:A, %d, RENT_DUE!D:D, F%d)",
                        lease.getLeaseId(), rowNum + 1
                    ));
                    rentDueCell.setCellStyle(currencyStyle);

                    // Column H: rent_received (SUMIFS to RENT_RECEIVED sheet)
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
                    Cell expensesCell = row.createCell(col++);
                    expensesCell.setCellFormula(String.format(
                        "SUMIFS(EXPENSES!R:R, EXPENSES!A:A, %d, EXPENSES!D:D, F%d)",
                        lease.getLeaseId(), rowNum + 1
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
                }

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
     * Create RENT_DUE sheet with custom periods and accurate calendar-day pro-rating
     * Uses actual month days instead of fixed 30-day months
     */
    private void createRentDueSheetWithCustomPeriods(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                                    LocalDate startDate, LocalDate endDate, int periodStartDay) {
        log.info("Creating RENT_DUE sheet with custom periods (start day: {})", periodStartDay);

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

        // Generate custom periods
        List<CustomPeriod> periods = generateCustomPeriods(startDate, endDate, periodStartDay);

        // Generate rows for each lease × custom period
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();
            LocalDate leaseEnd = lease.getEndDate();
            int rentDueDay = leaseStart != null ? leaseStart.getDayOfMonth() : periodStartDay;

            for (CustomPeriod period : periods) {
                // Check if lease overlaps with this period
                boolean leaseActive = (leaseStart == null || !leaseStart.isAfter(period.periodEnd))
                                   && (leaseEnd == null || !leaseEnd.isBefore(period.periodStart));

                if (leaseActive) {
                    Row row = sheet.createRow(rowNum);
                    int col = 0;

                    // A: lease_id
                    row.createCell(col++).setCellValue(lease.getLeaseId());

                    // B: lease_reference
                    row.createCell(col++).setCellValue(lease.getLeaseReference());

                    // C: property_name
                    row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");

                    // D: period_start
                    Cell periodStartCell = row.createCell(col++);
                    periodStartCell.setCellValue(period.periodStart);
                    periodStartCell.setCellStyle(dateStyle);

                    // E: period_end
                    Cell periodEndCell = row.createCell(col++);
                    periodEndCell.setCellValue(period.periodEnd);
                    periodEndCell.setCellStyle(dateStyle);

                    // F: period_days
                    row.createCell(col++).setCellValue(period.periodDays);

                    // G: lease_start
                    Cell leaseStartCell = row.createCell(col++);
                    if (leaseStart != null) {
                        leaseStartCell.setCellValue(leaseStart);
                        leaseStartCell.setCellStyle(dateStyle);
                    }

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

                    // J: rent_due_day
                    row.createCell(col++).setCellValue(rentDueDay);

                    // K: lease_days_in_period (FORMULA - simple overlap calculation)
                    row.createCell(col++).setCellFormula(String.format(
                        "MAX(0, MIN(IF(ISBLANK(H%d), E%d, H%d), E%d) - MAX(G%d, D%d) + 1)",
                        rowNum + 1, rowNum + 1, rowNum + 1, // lease_end or period_end
                        rowNum + 1, // period_end
                        rowNum + 1, rowNum + 1 // lease_start, period_start
                    ));

                    // L: prorated_rent_due (FORMULA - only pro-rate at lease END, not start)
                    // Full rent unless lease ends mid-period
                    Cell proratedRentCell = row.createCell(col++);
                    proratedRentCell.setCellFormula(String.format(
                        "IF(AND(NOT(ISBLANK(H%d)), H%d>=D%d, H%d<E%d), ROUND((H%d-D%d+1)/DAY(EOMONTH(D%d, 0))*I%d, 2), I%d)",
                        rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1, // lease_end checks
                        rowNum + 1, rowNum + 1, // lease_end - period_start + 1
                        rowNum + 1, // days in anchor month
                        rowNum + 1, // monthly_rent (pro-rated)
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
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        log.info("RENT_DUE sheet created with {} rows (custom periods)", rowNum - 1);
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

        // Generate rows for each lease × custom period
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();
            LocalDate leaseEnd = lease.getEndDate();

            for (CustomPeriod period : periods) {
                boolean leaseActive = (leaseStart == null || !leaseStart.isAfter(period.periodEnd))
                                   && (leaseEnd == null || !leaseEnd.isBefore(period.periodStart));

                if (leaseActive) {
                    Row row = sheet.createRow(rowNum);
                    int col = 0;

                    // Get payment details for this lease/period
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
            "lease_reference", "property_name", "customer_name", "lease_start_date", "rent_due_day",
            "rent_due", "rent_received", "arrears", "management_fee", "service_fee",
            "total_commission", "total_expenses", "net_to_owner"
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

        // Generate rows for each lease
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();
            LocalDate leaseEnd = lease.getEndDate();

            // Check if lease overlaps with this period
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

                // F: rent_due (SUMIFS to RENT_DUE sheet)
                Cell rentDueCell = row.createCell(col++);
                rentDueCell.setCellFormula(String.format(
                    "IFERROR(SUMIFS(RENT_DUE!L:L, RENT_DUE!B:B, \"%s\", RENT_DUE!D:D, DATE(%d,%d,%d)), 0)",
                    lease.getLeaseReference(),
                    period.periodStart.getYear(),
                    period.periodStart.getMonthValue(),
                    period.periodStart.getDayOfMonth()
                ));
                rentDueCell.setCellStyle(currencyStyle);

                // G: rent_received (SUMIFS to RENT_RECEIVED sheet)
                Cell rentReceivedCell = row.createCell(col++);
                rentReceivedCell.setCellFormula(String.format(
                    "IFERROR(SUMIFS(RENT_RECEIVED!O:O, RENT_RECEIVED!B:B, \"%s\", RENT_RECEIVED!E:E, DATE(%d,%d,%d)), 0)",
                    lease.getLeaseReference(),
                    period.periodStart.getYear(),
                    period.periodStart.getMonthValue(),
                    period.periodStart.getDayOfMonth()
                ));
                rentReceivedCell.setCellStyle(currencyStyle);

                // H: arrears (formula: due - received)
                Cell arrearsCell = row.createCell(col++);
                arrearsCell.setCellFormula(String.format("F%d - G%d", rowNum + 1, rowNum + 1));
                arrearsCell.setCellStyle(currencyStyle);

                // I: management_fee (formula: rent_received * management_fee_percent)
                Cell mgmtFeeCell = row.createCell(col++);
                mgmtFeeCell.setCellFormula(String.format("G%d * %.2f", rowNum + 1, commissionConfig.getManagementFeePercent().doubleValue()));
                mgmtFeeCell.setCellStyle(currencyStyle);

                // J: service_fee (formula: rent_received * service_fee_percent)
                Cell svcFeeCell = row.createCell(col++);
                svcFeeCell.setCellFormula(String.format("G%d * %.2f", rowNum + 1, commissionConfig.getServiceFeePercent().doubleValue()));
                svcFeeCell.setCellStyle(currencyStyle);

                // K: total_commission (formula: mgmt + svc)
                Cell totalCommCell = row.createCell(col++);
                totalCommCell.setCellFormula(String.format("I%d + J%d", rowNum + 1, rowNum + 1));
                totalCommCell.setCellStyle(currencyStyle);

                // L: total_expenses (INDEX/MATCH to EXPENSES sheet)
                Cell expensesCell = row.createCell(col++);
                expensesCell.setCellFormula(String.format(
                    "IFERROR(INDEX(EXPENSES!R:R, MATCH(1, (EXPENSES!B:B=\"%s\") * (EXPENSES!D:D=DATE(%d,%d,%d)), 0)), 0)",
                    lease.getLeaseReference(),
                    period.periodStart.getYear(),
                    period.periodStart.getMonthValue(),
                    period.periodStart.getDayOfMonth()
                ));
                expensesCell.setCellStyle(currencyStyle);

                // M: net_to_owner (formula: received - commission - expenses)
                Cell netToOwnerCell = row.createCell(col++);
                netToOwnerCell.setCellFormula(String.format("G%d - K%d - L%d", rowNum + 1, rowNum + 1, rowNum + 1));
                netToOwnerCell.setCellStyle(currencyStyle);

                rowNum++;
            }
        }

        // Add totals row
        if (rowNum > 1) {
            Row totalsRow = sheet.createRow(rowNum);
            int col = 0;

            // A-E: Label in first column
            Cell labelCell = totalsRow.createCell(col++);
            labelCell.setCellValue("TOTALS");
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            CellStyle boldStyle = workbook.createCellStyle();
            boldStyle.setFont(boldFont);
            labelCell.setCellStyle(boldStyle);

            // Skip columns B-E
            col += 4;

            // F: total_rent_due
            Cell totalRentDueCell = totalsRow.createCell(col++);
            totalRentDueCell.setCellFormula(String.format("SUM(F2:F%d)", rowNum));
            CellStyle boldCurrencyStyle = workbook.createCellStyle();
            boldCurrencyStyle.cloneStyleFrom(currencyStyle);
            boldCurrencyStyle.setFont(boldFont);
            totalRentDueCell.setCellStyle(boldCurrencyStyle);

            // G: total_rent_received
            Cell totalRentReceivedCell = totalsRow.createCell(col++);
            totalRentReceivedCell.setCellFormula(String.format("SUM(G2:G%d)", rowNum));
            totalRentReceivedCell.setCellStyle(boldCurrencyStyle);

            // H: total_arrears
            Cell totalArrearsCell = totalsRow.createCell(col++);
            totalArrearsCell.setCellFormula(String.format("SUM(H2:H%d)", rowNum));
            totalArrearsCell.setCellStyle(boldCurrencyStyle);

            // I: total_management_fee
            Cell totalMgmtFeeCell = totalsRow.createCell(col++);
            totalMgmtFeeCell.setCellFormula(String.format("SUM(I2:I%d)", rowNum));
            totalMgmtFeeCell.setCellStyle(boldCurrencyStyle);

            // J: total_service_fee
            Cell totalSvcFeeCell = totalsRow.createCell(col++);
            totalSvcFeeCell.setCellFormula(String.format("SUM(J2:J%d)", rowNum));
            totalSvcFeeCell.setCellStyle(boldCurrencyStyle);

            // K: total_commission
            Cell totalCommCell = totalsRow.createCell(col++);
            totalCommCell.setCellFormula(String.format("SUM(K2:K%d)", rowNum));
            totalCommCell.setCellStyle(boldCurrencyStyle);

            // L: total_expenses
            Cell totalExpensesCell = totalsRow.createCell(col++);
            totalExpensesCell.setCellFormula(String.format("SUM(L2:L%d)", rowNum));
            totalExpensesCell.setCellStyle(boldCurrencyStyle);

            // M: total_net_to_owner
            Cell totalNetCell = totalsRow.createCell(col++);
            totalNetCell.setCellFormula(String.format("SUM(M2:M%d)", rowNum));
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
            "lease_reference", "property_name", "customer_name", "lease_start_date", "rent_due_day",
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

                // D: lease_start_date
                Cell startDateCell = row.createCell(col++);
                if (leaseStart != null) {
                    startDateCell.setCellValue(leaseStart);
                    startDateCell.setCellStyle(dateStyle);
                }

                // E: rent_due_day
                row.createCell(col++).setCellValue(lease.getPaymentDay() != null ? lease.getPaymentDay() : 0);

                // F: total_rent_due (SUM across all periods in RENT_DUE sheet)
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

                // H: total_arrears (formula: total_due - total_received)
                Cell totalArrearsCell = row.createCell(col++);
                totalArrearsCell.setCellFormula(String.format("F%d - G%d", rowNum + 1, rowNum + 1));
                totalArrearsCell.setCellStyle(currencyStyle);

                // I: total_management_fee
                Cell totalMgmtFeeCell = row.createCell(col++);
                totalMgmtFeeCell.setCellFormula(String.format("G%d * %.2f", rowNum + 1, commissionConfig.getManagementFeePercent().doubleValue()));
                totalMgmtFeeCell.setCellStyle(currencyStyle);

                // J: total_service_fee
                Cell totalSvcFeeCell = row.createCell(col++);
                totalSvcFeeCell.setCellFormula(String.format("G%d * %.2f", rowNum + 1, commissionConfig.getServiceFeePercent().doubleValue()));
                totalSvcFeeCell.setCellStyle(currencyStyle);

                // K: total_commission
                Cell totalCommCell = row.createCell(col++);
                totalCommCell.setCellFormula(String.format("I%d + J%d", rowNum + 1, rowNum + 1));
                totalCommCell.setCellStyle(currencyStyle);

                // L: total_expenses (SUM all expenses for this lease in the period)
                Cell totalExpensesCell = row.createCell(col++);
                totalExpensesCell.setCellFormula(String.format(
                    "SUMIFS(EXPENSES!R:R, EXPENSES!B:B, \"%s\", EXPENSES!D:D, \">=\"&DATE(%d,%d,%d), EXPENSES!D:D, \"<=\"&DATE(%d,%d,%d))",
                    lease.getLeaseReference(),
                    startDate.getYear(), startDate.getMonthValue(), startDate.getDayOfMonth(),
                    endDate.getYear(), endDate.getMonthValue(), endDate.getDayOfMonth()
                ));
                totalExpensesCell.setCellStyle(currencyStyle);

                // M: total_net_to_owner
                Cell totalNetCell = row.createCell(col++);
                totalNetCell.setCellFormula(String.format("G%d - K%d - L%d", rowNum + 1, rowNum + 1, rowNum + 1));
                totalNetCell.setCellStyle(currencyStyle);

                // N: opening_balance (arrears before first period start)
                Cell openingBalanceCell = row.createCell(col++);
                openingBalanceCell.setCellFormula(String.format(
                    "SUMIFS(RENT_DUE!L:L, RENT_DUE!B:B, \"%s\", RENT_DUE!D:D, \"<\"&DATE(%d,%d,%d)) - SUMIFS(RENT_RECEIVED!O:O, RENT_RECEIVED!B:B, \"%s\", RENT_RECEIVED!E:E, \"<\"&DATE(%d,%d,%d))",
                    lease.getLeaseReference(),
                    startDate.getYear(), startDate.getMonthValue(), startDate.getDayOfMonth(),
                    lease.getLeaseReference(),
                    startDate.getYear(), startDate.getMonthValue(), startDate.getDayOfMonth()
                ));
                openingBalanceCell.setCellStyle(currencyStyle);

                // O: closing_balance (opening + arrears for period)
                Cell closingBalanceCell = row.createCell(col++);
                closingBalanceCell.setCellFormula(String.format("N%d + H%d", rowNum + 1, rowNum + 1));
                closingBalanceCell.setCellStyle(currencyStyle);

                rowNum++;
            }
        }

        // Add totals row
        if (rowNum > 1) {
            Row totalsRow = sheet.createRow(rowNum);
            int col = 0;

            // A-E: Label in first column
            Cell labelCell = totalsRow.createCell(col++);
            labelCell.setCellValue("TOTALS");
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            CellStyle boldStyle = workbook.createCellStyle();
            boldStyle.setFont(boldFont);
            labelCell.setCellStyle(boldStyle);

            // Skip columns B-E
            col += 4;

            // F: total_rent_due
            Cell totalRentDueCell = totalsRow.createCell(col++);
            totalRentDueCell.setCellFormula(String.format("SUM(F2:F%d)", rowNum));
            CellStyle boldCurrencyStyle = workbook.createCellStyle();
            boldCurrencyStyle.cloneStyleFrom(currencyStyle);
            boldCurrencyStyle.setFont(boldFont);
            totalRentDueCell.setCellStyle(boldCurrencyStyle);

            // G: total_rent_received
            Cell totalRentReceivedCell = totalsRow.createCell(col++);
            totalRentReceivedCell.setCellFormula(String.format("SUM(G2:G%d)", rowNum));
            totalRentReceivedCell.setCellStyle(boldCurrencyStyle);

            // H: total_arrears
            Cell totalArrearsCell = totalsRow.createCell(col++);
            totalArrearsCell.setCellFormula(String.format("SUM(H2:H%d)", rowNum));
            totalArrearsCell.setCellStyle(boldCurrencyStyle);

            // I: total_management_fee
            Cell totalMgmtFeeCell = totalsRow.createCell(col++);
            totalMgmtFeeCell.setCellFormula(String.format("SUM(I2:I%d)", rowNum));
            totalMgmtFeeCell.setCellStyle(boldCurrencyStyle);

            // J: total_service_fee
            Cell totalSvcFeeCell = totalsRow.createCell(col++);
            totalSvcFeeCell.setCellFormula(String.format("SUM(J2:J%d)", rowNum));
            totalSvcFeeCell.setCellStyle(boldCurrencyStyle);

            // K: total_commission
            Cell totalCommCell = totalsRow.createCell(col++);
            totalCommCell.setCellFormula(String.format("SUM(K2:K%d)", rowNum));
            totalCommCell.setCellStyle(boldCurrencyStyle);

            // L: total_expenses
            Cell totalExpensesCell = totalsRow.createCell(col++);
            totalExpensesCell.setCellFormula(String.format("SUM(L2:L%d)", rowNum));
            totalExpensesCell.setCellStyle(boldCurrencyStyle);

            // M: total_net_to_owner
            Cell totalNetCell = totalsRow.createCell(col++);
            totalNetCell.setCellFormula(String.format("SUM(M2:M%d)", rowNum));
            totalNetCell.setCellStyle(boldCurrencyStyle);

            // N: total_opening_balance
            Cell totalOpeningBalanceCell = totalsRow.createCell(col++);
            totalOpeningBalanceCell.setCellFormula(String.format("SUM(N2:N%d)", rowNum));
            totalOpeningBalanceCell.setCellStyle(boldCurrencyStyle);

            // O: total_closing_balance
            Cell totalClosingBalanceCell = totalsRow.createCell(col++);
            totalClosingBalanceCell.setCellFormula(String.format("SUM(O2:O%d)", rowNum));
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

                    // M: total_expenses (INDEX/MATCH to EXPENSES sheet)
                    Cell expensesCell = row.createCell(col++);
                    expensesCell.setCellFormula(String.format(
                        "IFERROR(INDEX(EXPENSES!R:R, MATCH(1, (EXPENSES!B:B=\"%s\") * (EXPENSES!D:D=DATE(%d,%d,%d)), 0)), 0)",
                        lease.getLeaseReference(),
                        period.periodStart.getYear(),
                        period.periodStart.getMonthValue(),
                        period.periodStart.getDayOfMonth()
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
}

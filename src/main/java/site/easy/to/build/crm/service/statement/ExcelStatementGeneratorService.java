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

    // Commission rates (currently hardcoded, future: from database)
    private static final BigDecimal MANAGEMENT_FEE_PCT = new BigDecimal("0.10");  // 10%
    private static final BigDecimal SERVICE_FEE_PCT = new BigDecimal("0.05");     // 5%

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
        List<TransactionDTO> transactions = dataExtractService.extractTransactions(startDate, endDate);

        log.info("Extracted {} leases and {} transactions", leaseMaster.size(), transactions.size());

        // Create sheets
        createLeaseMasterSheet(workbook, leaseMaster);
        createTransactionsSheet(workbook, transactions);
        createRentDueSheet(workbook, leaseMaster, startDate, endDate);
        createRentReceivedSheet(workbook, leaseMaster, startDate, endDate);
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
        List<TransactionDTO> transactions = dataExtractService.extractTransactionsForCustomer(
            customerId, startDate, endDate);

        log.info("Extracted {} leases and {} transactions for customer {}",
                leaseMaster.size(), transactions.size(), customerId);

        // Create sheets
        createLeaseMasterSheet(workbook, leaseMaster);
        createTransactionsSheet(workbook, transactions);
        createRentDueSheet(workbook, leaseMaster, startDate, endDate);
        createRentReceivedSheet(workbook, leaseMaster, startDate, endDate);
        createMonthlyStatementSheet(workbook, leaseMaster, startDate, endDate);

        log.info("Customer statement workbook created successfully");
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
        List<TransactionDTO> transactions = dataExtractService.extractTransactions(startDate, endDate);

        log.info("Extracted {} leases and {} transactions", leaseMaster.size(), transactions.size());

        // Create sheets with custom periods
        createLeaseMasterSheet(workbook, leaseMaster);
        createTransactionsSheet(workbook, transactions);
        createRentDueSheetWithCustomPeriods(workbook, leaseMaster, startDate, endDate, periodStartDay);
        createRentReceivedSheetWithCustomPeriods(workbook, leaseMaster, startDate, endDate, periodStartDay);
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
        log.error("🔍 DEBUG: About to extract transactions for customer {} from {} to {}", customerId, startDate, endDate);
        List<TransactionDTO> transactions = dataExtractService.extractTransactionsForCustomer(
            customerId, startDate, endDate);

        log.error("🔍 DEBUG: Extracted {} leases and {} transactions for customer {}",
                leaseMaster.size(), transactions.size(), customerId);

        // Create sheets with custom periods
        createLeaseMasterSheet(workbook, leaseMaster);
        createTransactionsSheet(workbook, transactions);
        createRentDueSheetWithCustomPeriods(workbook, leaseMaster, startDate, endDate, periodStartDay);
        createRentReceivedSheetWithCustomPeriods(workbook, leaseMaster, startDate, endDate, periodStartDay);
        createMonthlyStatementSheetWithCustomPeriods(workbook, leaseMaster, startDate, endDate, periodStartDay);

        log.info("Customer statement workbook with custom periods created successfully");
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
            "transaction_id", "transaction_date", "invoice_id", "property_id", "customer_id",
            "category", "transaction_type", "amount", "description"
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
            row.createCell(4).setCellValue(txn.getCustomerId() != null ? txn.getCustomerId() : 0);
            row.createCell(5).setCellValue(txn.getCategory() != null ? txn.getCategory() : "");
            row.createCell(6).setCellValue(txn.getTransactionType() != null ? txn.getTransactionType() : "");

            // Amount
            Cell amountCell = row.createCell(7);
            if (txn.getAmount() != null) {
                amountCell.setCellValue(txn.getAmount().doubleValue());
                amountCell.setCellStyle(currencyStyle);
            }

            row.createCell(8).setCellValue(txn.getDescription() != null ? txn.getDescription() : "");
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

            // Generate monthly rows from statement start to end
            YearMonth currentMonth = YearMonth.from(startDate);
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

                    // Column H: prorated_rent_due (formula)
                    Cell proratedRentCell = row.createCell(col++);
                    proratedRentCell.setCellFormula(String.format(
                        "IF(G%d>0, (G%d/F%d) * VLOOKUP(A%d,LEASE_MASTER!A:J,10,FALSE), 0)",
                        rowNum + 1, rowNum + 1, rowNum + 1, rowNum + 1
                    ));
                    proratedRentCell.setCellStyle(currencyStyle);

                    // Column I: management_fee (formula: 10%)
                    Cell mgmtFeeCell = row.createCell(col++);
                    mgmtFeeCell.setCellFormula(String.format("H%d * %.2f", rowNum + 1, MANAGEMENT_FEE_PCT.doubleValue()));
                    mgmtFeeCell.setCellStyle(currencyStyle);

                    // Column J: service_fee (formula: 5%)
                    Cell svcFeeCell = row.createCell(col++);
                    svcFeeCell.setCellFormula(String.format("H%d * %.2f", rowNum + 1, SERVICE_FEE_PCT.doubleValue()));
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
     * Create RENT_RECEIVED sheet with FORMULAS for aggregation
     * Uses SUMIFS to sum transactions by lease and month
     */
    private void createRentReceivedSheet(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                        LocalDate startDate, LocalDate endDate) {
        log.info("Creating RENT_RECEIVED sheet with formulas");

        Sheet sheet = workbook.createSheet("RENT_RECEIVED");

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_id", "lease_reference", "property_name", "month_start", "month_end", "total_received"
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

        // Generate one row per lease per month (same structure as RENT_DUE)
        for (LeaseMasterDTO lease : leaseMaster) {
            LocalDate leaseStart = lease.getStartDate();
            LocalDate leaseEnd = lease.getEndDate();

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

                    // Column F: total_received (SUMIFS formula)
                    // Sum amounts from TRANSACTIONS where invoice_id matches AND date in range
                    Cell totalReceivedCell = row.createCell(col++);
                    String sumFormula = String.format(
                        "SUMIFS(TRANSACTIONS!H:H, TRANSACTIONS!C:C, A%d, TRANSACTIONS!B:B, \">=\"&D%d, TRANSACTIONS!B:B, \"<=\"&E%d)",
                        rowNum + 1, rowNum + 1, rowNum + 1
                    );
                    totalReceivedCell.setCellFormula(sumFormula);
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
            "lease_reference", "property_name", "customer_name", "month",
            "rent_due", "rent_received", "arrears", "management_fee", "service_fee",
            "total_commission", "net_to_owner", "cumulative_arrears"
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

                    // Column A: lease_reference
                    row.createCell(col++).setCellValue(lease.getLeaseReference());

                    // Column B: property_name
                    row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");

                    // Column C: customer_name
                    row.createCell(col++).setCellValue(lease.getCustomerName() != null ? lease.getCustomerName() : "");

                    // Column D: month
                    Cell monthCell = row.createCell(col++);
                    monthCell.setCellValue(monthStart);
                    monthCell.setCellStyle(dateStyle);

                    // Column E: rent_due (SUMIFS to RENT_DUE sheet - simpler than array formula)
                    Cell rentDueCell = row.createCell(col++);
                    rentDueCell.setCellFormula(String.format(
                        "SUMIFS(RENT_DUE!H:H, RENT_DUE!A:A, %d, RENT_DUE!D:D, D%d)",
                        lease.getLeaseId(), rowNum + 1
                    ));
                    rentDueCell.setCellStyle(currencyStyle);

                    // Column F: rent_received (SUMIFS to RENT_RECEIVED sheet - simpler than array formula)
                    Cell rentReceivedCell = row.createCell(col++);
                    rentReceivedCell.setCellFormula(String.format(
                        "SUMIFS(RENT_RECEIVED!F:F, RENT_RECEIVED!A:A, %d, RENT_RECEIVED!D:D, D%d)",
                        lease.getLeaseId(), rowNum + 1
                    ));
                    rentReceivedCell.setCellStyle(currencyStyle);

                    // Column G: arrears (formula: due - received)
                    Cell arrearsCell = row.createCell(col++);
                    arrearsCell.setCellFormula(String.format("E%d - F%d", rowNum + 1, rowNum + 1));
                    arrearsCell.setCellStyle(currencyStyle);

                    // Column H: management_fee (SUMIFS to RENT_DUE)
                    Cell mgmtFeeCell = row.createCell(col++);
                    mgmtFeeCell.setCellFormula(String.format(
                        "SUMIFS(RENT_DUE!I:I, RENT_DUE!A:A, %d, RENT_DUE!D:D, D%d)",
                        lease.getLeaseId(), rowNum + 1
                    ));
                    mgmtFeeCell.setCellStyle(currencyStyle);

                    // Column I: service_fee (SUMIFS to RENT_DUE)
                    Cell svcFeeCell = row.createCell(col++);
                    svcFeeCell.setCellFormula(String.format(
                        "SUMIFS(RENT_DUE!J:J, RENT_DUE!A:A, %d, RENT_DUE!D:D, D%d)",
                        lease.getLeaseId(), rowNum + 1
                    ));
                    svcFeeCell.setCellStyle(currencyStyle);

                    // Column J: total_commission (formula: mgmt + svc)
                    Cell totalCommCell = row.createCell(col++);
                    totalCommCell.setCellFormula(String.format("H%d + I%d", rowNum + 1, rowNum + 1));
                    totalCommCell.setCellStyle(currencyStyle);

                    // Column K: net_to_owner (formula: received - commission)
                    Cell netToOwnerCell = row.createCell(col++);
                    netToOwnerCell.setCellFormula(String.format("F%d - J%d", rowNum + 1, rowNum + 1));
                    netToOwnerCell.setCellStyle(currencyStyle);

                    // Column L: cumulative_arrears (formula: running sum)
                    Cell cumArrearsCell = row.createCell(col++);
                    if (rowNum == 1) {
                        cumArrearsCell.setCellFormula(String.format("G%d", rowNum + 1));
                    } else {
                        cumArrearsCell.setCellFormula(String.format("L%d + G%d", rowNum, rowNum + 1));
                    }
                    cumArrearsCell.setCellStyle(currencyStyle);

                    rowNum++;
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
                boolean leaseActive = (leaseEnd == null || !period.periodEnd.isBefore(leaseStart))
                                   && !period.periodStart.isAfter(leaseEnd != null ? leaseEnd : period.periodEnd);

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

                    // L: prorated_rent_due (FORMULA - accurate calendar day pro-rating)
                    // Uses actual days in the anchor month for pro-rating
                    Cell proratedRentCell = row.createCell(col++);
                    proratedRentCell.setCellFormula(String.format(
                        "IF(K%d>0, ROUND(I%d * (K%d / DAY(EOMONTH(D%d, 0))), 2), 0)",
                        rowNum + 1, // lease_days check
                        rowNum + 1, // monthly_rent
                        rowNum + 1, // lease_days_in_period
                        rowNum + 1  // period_start for EOMONTH
                    ));
                    proratedRentCell.setCellStyle(currencyStyle);

                    // M: management_fee (10%)
                    Cell mgmtFeeCell = row.createCell(col++);
                    mgmtFeeCell.setCellFormula(String.format("L%d * %.2f", rowNum + 1, MANAGEMENT_FEE_PCT.doubleValue()));
                    mgmtFeeCell.setCellStyle(currencyStyle);

                    // N: service_fee (5%)
                    Cell svcFeeCell = row.createCell(col++);
                    svcFeeCell.setCellFormula(String.format("L%d * %.2f", rowNum + 1, SERVICE_FEE_PCT.doubleValue()));
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
     * Create RENT_RECEIVED sheet with custom periods
     */
    private void createRentReceivedSheetWithCustomPeriods(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                                         LocalDate startDate, LocalDate endDate, int periodStartDay) {
        log.info("Creating RENT_RECEIVED sheet with custom periods");

        Sheet sheet = workbook.createSheet("RENT_RECEIVED");

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_id", "lease_reference", "property_name", "period_start", "period_end", "total_received"
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
                boolean leaseActive = (leaseEnd == null || !period.periodEnd.isBefore(leaseStart))
                                   && !period.periodStart.isAfter(leaseEnd != null ? leaseEnd : period.periodEnd);

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

                    // F: total_received (SUMIFS formula with custom period dates)
                    Cell totalReceivedCell = row.createCell(col++);
                    String sumFormula = String.format(
                        "SUMIFS(TRANSACTIONS!H:H, TRANSACTIONS!C:C, A%d, TRANSACTIONS!B:B, \">=\"&D%d, TRANSACTIONS!B:B, \"<=\"&E%d)",
                        rowNum + 1, rowNum + 1, rowNum + 1
                    );
                    totalReceivedCell.setCellFormula(sumFormula);
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
     * Create MONTHLY_STATEMENT sheet with custom periods
     */
    private void createMonthlyStatementSheetWithCustomPeriods(Workbook workbook, List<LeaseMasterDTO> leaseMaster,
                                                             LocalDate startDate, LocalDate endDate, int periodStartDay) {
        log.info("Creating MONTHLY_STATEMENT sheet with custom periods");

        Sheet sheet = workbook.createSheet("MONTHLY_STATEMENT");

        // Header row
        Row header = sheet.createRow(0);
        String[] headers = {
            "lease_reference", "property_name", "customer_name", "period",
            "rent_due", "rent_received", "arrears", "management_fee", "service_fee",
            "total_commission", "net_to_owner", "cumulative_arrears"
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
                boolean leaseActive = (leaseEnd == null || !period.periodEnd.isBefore(leaseStart))
                                   && !period.periodStart.isAfter(leaseEnd != null ? leaseEnd : period.periodEnd);

                if (leaseActive) {
                    Row row = sheet.createRow(rowNum);
                    int col = 0;

                    // A: lease_reference
                    row.createCell(col++).setCellValue(lease.getLeaseReference());

                    // B: property_name
                    row.createCell(col++).setCellValue(lease.getPropertyName() != null ? lease.getPropertyName() : "");

                    // C: customer_name
                    row.createCell(col++).setCellValue(lease.getCustomerName() != null ? lease.getCustomerName() : "");

                    // D: period (formatted as "Dec 22 - Jan 21")
                    String periodLabel = period.periodStart.format(DateTimeFormatter.ofPattern("MMM dd")) + " - " +
                                       period.periodEnd.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
                    row.createCell(col++).setCellValue(periodLabel);

                    // E: rent_due (VLOOKUP to RENT_DUE sheet)
                    Cell rentDueCell = row.createCell(col++);
                    rentDueCell.setCellFormula(String.format(
                        "IFERROR(INDEX(RENT_DUE!L:L, MATCH(1, (RENT_DUE!A:A=\"%s\") * (RENT_DUE!D:D=DATE(%d,%d,%d)), 0)), 0)",
                        lease.getLeaseReference(),
                        period.periodStart.getYear(),
                        period.periodStart.getMonthValue(),
                        period.periodStart.getDayOfMonth()
                    ));
                    rentDueCell.setCellStyle(currencyStyle);

                    // F: rent_received (VLOOKUP to RENT_RECEIVED sheet)
                    Cell rentReceivedCell = row.createCell(col++);
                    rentReceivedCell.setCellFormula(String.format(
                        "IFERROR(INDEX(RENT_RECEIVED!F:F, MATCH(1, (RENT_RECEIVED!A:A=\"%s\") * (RENT_RECEIVED!D:D=DATE(%d,%d,%d)), 0)), 0)",
                        lease.getLeaseReference(),
                        period.periodStart.getYear(),
                        period.periodStart.getMonthValue(),
                        period.periodStart.getDayOfMonth()
                    ));
                    rentReceivedCell.setCellStyle(currencyStyle);

                    // G: arrears (formula: due - received)
                    Cell arrearsCell = row.createCell(col++);
                    arrearsCell.setCellFormula(String.format("E%d - F%d", rowNum + 1, rowNum + 1));
                    arrearsCell.setCellStyle(currencyStyle);

                    // H: management_fee (VLOOKUP to RENT_DUE)
                    Cell mgmtFeeCell = row.createCell(col++);
                    mgmtFeeCell.setCellFormula(String.format(
                        "IFERROR(INDEX(RENT_DUE!M:M, MATCH(1, (RENT_DUE!A:A=\"%s\") * (RENT_DUE!D:D=DATE(%d,%d,%d)), 0)), 0)",
                        lease.getLeaseReference(),
                        period.periodStart.getYear(),
                        period.periodStart.getMonthValue(),
                        period.periodStart.getDayOfMonth()
                    ));
                    mgmtFeeCell.setCellStyle(currencyStyle);

                    // I: service_fee (VLOOKUP to RENT_DUE)
                    Cell svcFeeCell = row.createCell(col++);
                    svcFeeCell.setCellFormula(String.format(
                        "IFERROR(INDEX(RENT_DUE!N:N, MATCH(1, (RENT_DUE!A:A=\"%s\") * (RENT_DUE!D:D=DATE(%d,%d,%d)), 0)), 0)",
                        lease.getLeaseReference(),
                        period.periodStart.getYear(),
                        period.periodStart.getMonthValue(),
                        period.periodStart.getDayOfMonth()
                    ));
                    svcFeeCell.setCellStyle(currencyStyle);

                    // J: total_commission (formula: mgmt + svc)
                    Cell totalCommCell = row.createCell(col++);
                    totalCommCell.setCellFormula(String.format("H%d + I%d", rowNum + 1, rowNum + 1));
                    totalCommCell.setCellStyle(currencyStyle);

                    // K: net_to_owner (formula: received - commission)
                    Cell netToOwnerCell = row.createCell(col++);
                    netToOwnerCell.setCellFormula(String.format("F%d - J%d", rowNum + 1, rowNum + 1));
                    netToOwnerCell.setCellStyle(currencyStyle);

                    // L: cumulative_arrears (formula: running sum)
                    Cell cumArrearsCell = row.createCell(col++);
                    if (rowNum == 1) {
                        cumArrearsCell.setCellFormula(String.format("G%d", rowNum + 1));
                    } else {
                        cumArrearsCell.setCellFormula(String.format("L%d + G%d", rowNum, rowNum + 1));
                    }
                    cumArrearsCell.setCellStyle(currencyStyle);

                    rowNum++;
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

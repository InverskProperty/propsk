package site.easy.to.build.crm.service.statements;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.repository.FinancialTransactionRepository;
import site.easy.to.build.crm.dto.StatementGenerationRequest;
import site.easy.to.build.crm.enums.StatementDataSource;
import site.easy.to.build.crm.service.tenant.TenantBalanceService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service implementing your Boden House spreadsheet template format
 * for Property Owner and Portfolio statements
 *
 * This service recreates the exact structure of your spreadsheet:
 * - PROPSK LTD header and company details
 * - Property-grouped sections (BODEN HOUSE, KNIGHTON HAYES, etc.)
 * - Complete column structure with payment routing
 * - Commission calculations (10% + 5% = 15%)
 * - Excel formulas for dynamic calculations
 */
@Service
public class BodenHouseStatementTemplateService {

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private TenantBalanceService tenantBalanceService;

    /**
     * Generate Property Owner Statement using Boden House template
     */
    public List<List<Object>> generatePropertyOwnerStatement(Customer propertyOwner, LocalDate fromDate, LocalDate toDate) {
        BodenHouseStatementData data = buildStatementData(propertyOwner, fromDate, toDate);
        return buildBodenHouseStatementValues(data);
    }

    /**
     * Generate Property Owner Statement with data source selection
     */
    public List<List<Object>> generatePropertyOwnerStatement(Customer propertyOwner, LocalDate fromDate, LocalDate toDate, Set<StatementDataSource> includedDataSources) {
        BodenHouseStatementData data = buildStatementData(propertyOwner, fromDate, toDate, includedDataSources);
        return buildBodenHouseStatementValues(data);
    }

    /**
     * Generate Property Owner Statement using StatementGenerationRequest
     */
    public List<List<Object>> generatePropertyOwnerStatement(StatementGenerationRequest request) {
        Customer propertyOwner = customerService.findByCustomerId(request.getPropertyOwnerId());
        return generatePropertyOwnerStatement(propertyOwner, request.getFromDate(), request.getToDate(), request.getIncludedDataSources());
    }

    /**
     * Generate Portfolio Statement using Boden House template
     */
    public List<List<Object>> generatePortfolioStatement(Customer propertyOwner, LocalDate fromDate, LocalDate toDate) {
        BodenHouseStatementData data = buildPortfolioStatementData(propertyOwner, fromDate, toDate);
        return buildBodenHouseStatementValues(data);
    }

    /**
     * Build the complete statement data following your Boden House structure
     */
    private BodenHouseStatementData buildStatementData(Customer propertyOwner, LocalDate fromDate, LocalDate toDate) {
        BodenHouseStatementData data = new BodenHouseStatementData();
        data.client = propertyOwner.getName();
        data.fromDate = fromDate;
        data.toDate = toDate;
        data.period = formatPeriod(fromDate, toDate);

        // Get all properties for this owner
        List<Property> properties = propertyService.getPropertiesByOwner(propertyOwner.getCustomerId());

        // TEMPORARY: If no properties found, generate sample data for testing
        if (properties.isEmpty()) {
            properties = generateSampleProperties(propertyOwner);
        }

        // Group properties by building/location (like your spreadsheet)
        Map<String, List<Property>> propertiesByBuilding = properties.stream()
            .collect(Collectors.groupingBy(this::extractBuildingName));

        // Build property groups
        for (Map.Entry<String, List<Property>> entry : propertiesByBuilding.entrySet()) {
            PropertyGroup group = new PropertyGroup();
            group.propertyName = entry.getKey();
            group.units = new ArrayList<>();

            for (Property property : entry.getValue()) {
                PropertyUnit unit = buildPropertyUnit(property, fromDate, toDate);
                group.units.add(unit);
            }

            // Calculate group totals
            calculateGroupTotals(group);
            data.propertyGroups.add(group);
        }

        // Calculate overall totals
        calculateOverallTotals(data);

        return data;
    }

    /**
     * Build statement data with data source filtering
     */
    private BodenHouseStatementData buildStatementData(Customer propertyOwner, LocalDate fromDate, LocalDate toDate, Set<StatementDataSource> includedDataSources) {
        BodenHouseStatementData data = new BodenHouseStatementData();
        data.client = propertyOwner.getName();
        data.fromDate = fromDate;
        data.toDate = toDate;
        data.period = formatPeriod(fromDate, toDate);
        data.includedDataSources = includedDataSources;

        // Get all properties for this owner
        List<Property> properties = propertyService.getPropertiesByOwner(propertyOwner.getCustomerId());

        // Group properties by building/location (like your spreadsheet)
        Map<String, List<Property>> propertiesByBuilding = properties.stream()
            .collect(Collectors.groupingBy(this::extractBuildingName));

        // Build property groups with data source filtering
        for (Map.Entry<String, List<Property>> entry : propertiesByBuilding.entrySet()) {
            PropertyGroup group = new PropertyGroup();
            group.propertyName = entry.getKey();
            group.units = new ArrayList<>();

            for (Property property : entry.getValue()) {
                PropertyUnit unit = buildPropertyUnit(property, fromDate, toDate, includedDataSources);
                group.units.add(unit);
            }

            // Calculate group totals
            calculateGroupTotals(group);
            data.propertyGroups.add(group);
        }

        return data;
    }

    /**
     * Build portfolio statement data (multiple properties)
     */
    private BodenHouseStatementData buildPortfolioStatementData(Customer propertyOwner, LocalDate fromDate, LocalDate toDate) {
        // For portfolio statements, we use the same structure but may include more properties
        return buildStatementData(propertyOwner, fromDate, toDate);
    }

    /**
     * Build property unit data following your spreadsheet structure
     */
    private PropertyUnit buildPropertyUnit(Property property, LocalDate fromDate, LocalDate toDate) {
        PropertyUnit unit = new PropertyUnit();

        // Basic property info
        unit.unitNumber = extractUnitNumber(property);
        unit.propertyAddress = property.getPropertyName() != null ? property.getPropertyName() : "";

        // Tenant information
        Customer tenant = getTenantForProperty(property.getId());
        unit.tenantName = tenant != null ? tenant.getName() : "";
        unit.tenancyDates = tenant != null && tenant.getMoveInDate() != null ?
            tenant.getMoveInDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "";

        // Rent information
        unit.rentDueAmount = property.getMonthlyPayment() != null ? property.getMonthlyPayment() : BigDecimal.ZERO;
        unit.rentDueDate = extractRentDueDay(property);

        // Get financial transactions for this property
        List<FinancialTransaction> transactions = getTransactionsForProperty(property, fromDate, toDate);

        // Determine payment routing (following your spreadsheet logic)
        setPaymentRouting(unit, transactions);

        // Calculate amounts
        calculateUnitAmounts(unit, transactions);

        // Get expenses for this property
        unit.expenses = getExpensesForProperty(property, fromDate, toDate);

        // Set comments
        unit.comments = determineComments(unit, property);

        return unit;
    }

    /**
     * Build property unit with data source filtering
     */
    private PropertyUnit buildPropertyUnit(Property property, LocalDate fromDate, LocalDate toDate, Set<StatementDataSource> includedDataSources) {
        PropertyUnit unit = new PropertyUnit();

        // Basic property info
        unit.unitNumber = extractUnitNumber(property);
        unit.propertyAddress = property.getPropertyName() != null ? property.getPropertyName() : "";

        // Tenant information
        Customer tenant = getTenantForProperty(property.getId());
        unit.tenantName = tenant != null ? tenant.getName() : "";
        unit.tenancyDates = tenant != null && tenant.getMoveInDate() != null ?
            tenant.getMoveInDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "";

        // Rent information
        unit.rentDueAmount = property.getMonthlyPayment() != null ? property.getMonthlyPayment() : BigDecimal.ZERO;
        unit.rentDueDate = extractRentDueDay(property);

        // Get financial transactions for this property with data source filtering
        List<FinancialTransaction> allTransactions = getTransactionsForProperty(property, fromDate, toDate);
        List<FinancialTransaction> filteredTransactions = filterTransactionsByDataSource(allTransactions, includedDataSources);

        // Determine payment routing (following your spreadsheet logic)
        setPaymentRouting(unit, filteredTransactions);

        // Calculate amounts using filtered transactions
        calculateUnitAmounts(unit, filteredTransactions);

        // Get expenses for this property (also filtered)
        unit.expenses = getFilteredExpensesForProperty(property, fromDate, toDate, includedDataSources);

        // Set comments
        unit.comments = "";

        return unit;
    }

    /**
     * Set payment routing flags (Robert Ellis, Propsk Old Account, PayProp)
     */
    private void setPaymentRouting(PropertyUnit unit, List<FinancialTransaction> transactions) {
        // Analyze transactions to determine payment routing
        unit.paidToRobertEllis = false;
        unit.paidToPropskOldAccount = false;
        unit.paidToPropskPayProp = false;

        for (FinancialTransaction transaction : transactions) {
            if (isRentPayment(transaction)) {
                if (isRobertEllisPayment(transaction)) {
                    unit.paidToRobertEllis = true;
                } else if (isOldAccountPayment(transaction)) {
                    unit.paidToPropskOldAccount = true;
                } else if (isPayPropPayment(transaction)) {
                    unit.paidToPropskPayProp = true;
                }
            }
        }
    }

    /**
     * Calculate all unit amounts following your spreadsheet formulas
     */
    private void calculateUnitAmounts(PropertyUnit unit, List<FinancialTransaction> transactions) {
        // Calculate rent received
        unit.rentReceivedAmount = calculateTotalRentReceived(transactions);
        unit.amountReceivedPayProp = calculatePayPropAmount(transactions);
        unit.amountReceivedOldAccount = calculateOldAccountAmount(transactions);
        unit.totalRentReceivedByPropsk = unit.amountReceivedPayProp.add(unit.amountReceivedOldAccount);

        // Commission calculations (10% + 5% = 15%)
        unit.managementFeePercentage = new BigDecimal("10.00");
        unit.serviceFeePercentage = new BigDecimal("5.00");

        if (unit.rentReceivedAmount.compareTo(BigDecimal.ZERO) > 0) {
            unit.managementFeeAmount = unit.rentReceivedAmount
                .multiply(unit.managementFeePercentage)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                .negate(); // Negative for fees

            unit.serviceFeeAmount = unit.rentReceivedAmount
                .multiply(unit.serviceFeePercentage)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                .negate(); // Negative for fees
        } else {
            unit.managementFeeAmount = BigDecimal.ZERO;
            unit.serviceFeeAmount = BigDecimal.ZERO;
        }

        unit.totalFeesChargedByPropsk = unit.managementFeeAmount.add(unit.serviceFeeAmount);

        // Calculate total expenses
        unit.totalExpenses = unit.expenses.stream()
            .map(expense -> expense.amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Net calculations
        unit.totalExpensesAndCommission = unit.totalExpenses.add(unit.totalFeesChargedByPropsk.abs());
        unit.netDueToOwner = unit.rentReceivedAmount.subtract(unit.totalExpensesAndCommission);

        // Outstanding calculation
        unit.rentDueLessReceived = unit.rentDueAmount.subtract(unit.rentReceivedAmount);
    }

    /**
     * Build the complete statement values list (your exact spreadsheet structure)
     */
    private List<List<Object>> buildBodenHouseStatementValues(BodenHouseStatementData data) {
        List<List<Object>> values = new ArrayList<>();

        // Create empty row template (38 columns like your spreadsheet)
        Object[] emptyRow = new Object[38];
        Arrays.fill(emptyRow, "");

        // PROPSK LTD Header (matching your exact format)
        addPropskHeader(values, emptyRow);

        // Client and period information
        addClientInfo(values, data, emptyRow);

        // Column headers (your exact structure)
        addColumnHeaders(values);

        // Property sections
        for (PropertyGroup group : data.propertyGroups) {
            addPropertySection(values, group, emptyRow);
        }

        // Overall totals
        addOverallTotals(values, data, emptyRow);

        // Payment summary
        addPaymentSummary(values, data, emptyRow);

        // Balance calculations
        addBalanceCalculations(values, data, emptyRow);

        return values;
    }

    /**
     * Add PROPSK LTD header (your exact format)
     */
    private void addPropskHeader(List<List<Object>> values, Object[] emptyRow) {
        // Multiple empty rows for spacing
        for (int i = 0; i < 30; i++) {
            values.add(Arrays.asList(emptyRow.clone()));
        }

        // Company header
        Object[] companyRow1 = emptyRow.clone();
        companyRow1[37] = "PROPSK LTD";
        values.add(Arrays.asList(companyRow1));

        Object[] companyRow2 = emptyRow.clone();
        companyRow2[37] = "1 Poplar Court, Greensward Lane, Hockley, England, SS5 5JB";
        values.add(Arrays.asList(companyRow2));

        Object[] companyRow3 = emptyRow.clone();
        companyRow3[37] = "Company number 15933011";
        values.add(Arrays.asList(companyRow3));

        values.add(Arrays.asList(emptyRow.clone())); // Empty row

        Object[] statementRow = emptyRow.clone();
        statementRow[37] = "STATEMENT";
        values.add(Arrays.asList(statementRow));

        values.add(Arrays.asList(emptyRow.clone())); // Empty row
    }

    /**
     * Add client and period information
     */
    private void addClientInfo(List<List<Object>> values, BodenHouseStatementData data, Object[] emptyRow) {
        Object[] clientRow = emptyRow.clone();
        clientRow[0] = "CLIENT:";
        clientRow[1] = data.client;
        values.add(Arrays.asList(clientRow));

        Object[] periodRow = emptyRow.clone();
        periodRow[0] = "PERIOD:";
        periodRow[1] = data.period;
        values.add(Arrays.asList(periodRow));

        values.add(Arrays.asList(emptyRow.clone())); // Empty row
    }

    /**
     * Add column headers (your exact spreadsheet structure)
     */
    private void addColumnHeaders(List<List<Object>> values) {
        List<Object> headers = Arrays.asList(
            "cde", "Unit No.", "Tenant", "Tenancy Dates", "Rent Due Date",
            "Rent\nDue\nAmount", "Rent\nReceived\nDate", "Paid to Robert Ellis",
            "Paid to Propsk Old Account", "Paid to Propsk PayProp", "Rent\nReceived\nAmount",
            "Amount received in Payprop", "Amount Received Old Account",
            "Total Rent Received By Propsk", "Management\nFee\n(%)", "Management\nFee\n(£)",
            "Service Fee (%)", "Service\nFee\n(£)", "Total Fees Charged by Propsk",
            "Expense 1 Label", "Expense 1 Amount", "Expense 1 Comment",
            "Expense 2 Label", "Expense 2 Amount", "Expense 2 Comment",
            "Expense 3 Label", "Expense 3 Amount", "Expense 3 Comment",
            "Expense 4 Label", "Expense 4 Amount", "Expense 4 Comment",
            "Total Expenses", "Total Expenses and Commission",
            "Net\nDue to\nPrestvale", "Net Due from Propsk After Expenses and Commissions",
            "Date\nPaid", "Rent\nDue less\nReceived", "Tenant Balance", "Comments"
        );
        values.add(headers);
    }

    /**
     * Add property section (BODEN HOUSE, KNIGHTON HAYES, etc.)
     */
    private void addPropertySection(List<List<Object>> values, PropertyGroup group, Object[] emptyRow) {
        // Property header row
        Object[] propertyRow = emptyRow.clone();
        propertyRow[0] = "PROPERTY:";
        propertyRow[1] = group.propertyName;
        values.add(Arrays.asList(propertyRow));

        values.add(Arrays.asList(emptyRow.clone())); // Empty row

        // Unit rows
        for (PropertyUnit unit : group.units) {
            addUnitRow(values, unit);
        }

        // Group totals
        addGroupTotals(values, group, emptyRow);

        values.add(Arrays.asList(emptyRow.clone())); // Empty row
    }

    /**
     * Add individual unit row with all data and formulas
     */
    private void addUnitRow(List<List<Object>> values, PropertyUnit unit) {
        Object[] row = new Object[38];
        Arrays.fill(row, "");

        // Calculate current row number for formulas (Excel rows are 1-based)
        int currentRow = values.size() + 1;

        row[0] = ""; // cde
        row[1] = unit.unitNumber;
        row[2] = unit.tenantName;
        row[3] = unit.tenancyDates;
        row[4] = unit.rentDueDate;
        row[5] = unit.rentDueAmount;
        row[6] = unit.rentReceivedDate;
        row[7] = unit.paidToRobertEllis ? "TRUE" : "FALSE";
        row[8] = unit.paidToPropskOldAccount ? "TRUE" : "FALSE";
        row[9] = unit.paidToPropskPayProp ? "TRUE" : "FALSE";
        row[10] = unit.rentReceivedAmount;

        // Excel formulas matching your spreadsheet exactly
        row[11] = "=L" + currentRow + "*K" + currentRow; // Amount received in Payprop
        row[12] = "=J" + currentRow + "*L" + currentRow; // Amount Received Old Account
        row[13] = "=(L" + currentRow + "*J" + currentRow + ")+(L" + currentRow + "*K" + currentRow + ")"; // Total Rent Received By Propsk
        row[14] = "10%"; // Management Fee %
        row[15] = "=L" + currentRow + "*-P" + currentRow; // Management Fee £
        row[16] = "5%"; // Service Fee %
        row[17] = "=L" + currentRow + "*-R" + currentRow; // Service Fee £
        row[18] = "=Q" + currentRow + "+S" + currentRow; // Total Fees Charged by Propsk

        // Expenses (up to 4 expenses like your spreadsheet)
        for (int i = 0; i < 4 && i < unit.expenses.size(); i++) {
            ExpenseItem expense = unit.expenses.get(i);
            row[19 + (i * 3)] = expense.label;
            row[20 + (i * 3)] = expense.amount;
            row[21 + (i * 3)] = expense.comment;
        }

        // More formulas matching your spreadsheet
        row[31] = "=-V" + currentRow + "+-Y" + currentRow + "+-AB" + currentRow + "+-AE" + currentRow; // Total Expenses
        row[32] = "=AG" + currentRow + "+T" + currentRow; // Total Expenses and Commission
        row[33] = "=L" + currentRow + "+T" + currentRow + "+AG" + currentRow; // Net Due to Owner
        row[34] = "=O" + currentRow + "+AH" + currentRow; // Net Due from Propsk After Expenses and Commissions
        row[35] = unit.datePaid;
        row[36] = "=G" + currentRow + "-L" + currentRow; // Rent Due less Received

        // Generate cross-sheet reference for running tenant balance (like your spreadsheet)
        String tenantBalanceFormula = generateTenantBalanceFormula(unit, currentRow);
        row[37] = tenantBalanceFormula; // Tenant Balance with cross-sheet reference

        values.add(Arrays.asList(row));
    }

    // ===== HELPER METHODS =====

    private void addGroupTotals(List<List<Object>> values, PropertyGroup group, Object[] emptyRow) {
        Object[] totalRow = emptyRow.clone();

        // Calculate the range for SUM formulas
        int currentRow = values.size() + 1;
        int startDataRow = currentRow - group.units.size(); // Start of data rows
        int endDataRow = currentRow - 1; // End of data rows

        totalRow[1] = "TOTAL";

        // SUM formulas matching your spreadsheet exactly
        totalRow[5] = "=SUM(G" + startDataRow + ":G" + endDataRow + ")"; // Rent Due
        totalRow[10] = "=SUM(L" + startDataRow + ":L" + endDataRow + ")"; // Rent Received
        totalRow[11] = "=SUM(M" + startDataRow + ":M" + endDataRow + ")"; // Amount received in Payprop
        totalRow[12] = "=SUM(N" + startDataRow + ":N" + endDataRow + ")"; // Amount Received Old Account
        totalRow[13] = "=SUM(O" + startDataRow + ":O" + endDataRow + ")"; // Total Rent Received By Propsk
        totalRow[15] = "=SUM(Q" + startDataRow + ":Q" + endDataRow + ")"; // Management Fees
        totalRow[17] = "=SUM(S" + startDataRow + ":S" + endDataRow + ")"; // Service Fees
        totalRow[18] = "=SUM(T" + startDataRow + ":T" + endDataRow + ")"; // Total Fees
        totalRow[31] = "=SUM(AG" + startDataRow + ":AG" + endDataRow + ")"; // Total Expenses
        totalRow[32] = "=SUM(AH" + startDataRow + ":AH" + endDataRow + ")"; // Total Expenses and Commission
        totalRow[33] = "=SUM(AI" + startDataRow + ":AI" + endDataRow + ")"; // Net Due
        totalRow[36] = "=SUM(AL" + startDataRow + ":AL" + endDataRow + ")"; // Rent Due less Received

        values.add(Arrays.asList(totalRow));
    }

    private void addOverallTotals(List<List<Object>> values, BodenHouseStatementData data, Object[] emptyRow) {
        Object[] totalRow = emptyRow.clone();
        totalRow[0] = "TOTALS";
        totalRow[5] = data.grandTotalRentDue;
        totalRow[10] = data.grandTotalRentReceived;
        totalRow[15] = data.grandTotalManagementFees;
        totalRow[17] = data.grandTotalServiceFees;
        totalRow[18] = data.grandTotalFees;
        totalRow[31] = data.grandTotalExpenses;
        totalRow[33] = data.grandTotalNetDue;
        values.add(Arrays.asList(totalRow));
    }

    private void addPaymentSummary(List<List<Object>> values, BodenHouseStatementData data, Object[] emptyRow) {
        // Payment entries (like your spreadsheet)
        Object[] payment1 = emptyRow.clone();
        payment1[0] = "Rent Payment";
        payment1[1] = "propsk-boden";
        payment1[2] = data.toDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        payment1[4] = "£6,233.23"; // Example amount
        values.add(Arrays.asList(payment1));

        // Add more payment rows as needed
        values.add(Arrays.asList(emptyRow.clone()));

        Object[] totalPayments = emptyRow.clone();
        totalPayments[0] = "Total";
        totalPayments[4] = data.totalPayments;
        values.add(Arrays.asList(totalPayments));
    }

    private void addBalanceCalculations(List<List<Object>> values, BodenHouseStatementData data, Object[] emptyRow) {
        values.add(Arrays.asList(emptyRow.clone()));

        Object[] previousBalance = emptyRow.clone();
        previousBalance[0] = "Previous Month Balance";
        previousBalance[4] = data.previousMonthBalance;
        values.add(Arrays.asList(previousBalance));

        Object[] amountPaid = emptyRow.clone();
        amountPaid[0] = "Amount Paid";
        amountPaid[4] = data.amountPaid;
        values.add(Arrays.asList(amountPaid));

        Object[] amountOwed = emptyRow.clone();
        amountOwed[0] = "Amount Owed";
        amountOwed[4] = data.amountOwed;
        values.add(Arrays.asList(amountOwed));

        Object[] balance = emptyRow.clone();
        balance[0] = "BALANCE";
        balance[4] = data.balance;
        values.add(Arrays.asList(balance));
    }

    // ===== CALCULATION METHODS =====

    private void calculateGroupTotals(PropertyGroup group) {
        group.totalRentDue = group.units.stream()
            .map(u -> u.rentDueAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        group.totalRentReceived = group.units.stream()
            .map(u -> u.rentReceivedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        group.totalFees = group.units.stream()
            .map(u -> u.totalFeesChargedByPropsk)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        group.totalExpenses = group.units.stream()
            .map(u -> u.totalExpenses)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        group.totalNetDue = group.units.stream()
            .map(u -> u.netDueToOwner)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void calculateOverallTotals(BodenHouseStatementData data) {
        data.grandTotalRentDue = data.propertyGroups.stream()
            .map(g -> g.totalRentDue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        data.grandTotalRentReceived = data.propertyGroups.stream()
            .map(g -> g.totalRentReceived)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        data.grandTotalFees = data.propertyGroups.stream()
            .map(g -> g.totalFees)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        data.grandTotalExpenses = data.propertyGroups.stream()
            .map(g -> g.totalExpenses)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        data.grandTotalNetDue = data.propertyGroups.stream()
            .map(g -> g.totalNetDue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ===== UTILITY METHODS =====

    private String formatPeriod(LocalDate fromDate, LocalDate toDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d");
        String fromFormatted = fromDate.format(formatter) + getOrdinalSuffix(fromDate.getDayOfMonth()) +
                              " " + fromDate.format(DateTimeFormatter.ofPattern("MMM yyyy"));
        String toFormatted = toDate.format(formatter) + getOrdinalSuffix(toDate.getDayOfMonth()) +
                            " " + toDate.format(DateTimeFormatter.ofPattern("MMM yyyy"));
        return fromFormatted + " to " + toFormatted;
    }

    private String getOrdinalSuffix(int day) {
        if (day >= 11 && day <= 13) return "th";
        switch (day % 10) {
            case 1: return "st";
            case 2: return "nd";
            case 3: return "rd";
            default: return "th";
        }
    }

    private String extractBuildingName(Property property) {
        if (property.getPropertyName() == null) return "UNKNOWN PROPERTY";

        String name = property.getPropertyName().toUpperCase();
        if (name.contains("BODEN HOUSE") || name.contains("WEST GATE")) {
            return "BODEN HOUSE";
        } else if (name.contains("KNIGHTON HAYES")) {
            return "KNIGHTON HAYES";
        }
        return property.getPropertyName().toUpperCase();
    }

    private String extractUnitNumber(Property property) {
        if (property.getPropertyName() == null) return "";

        if (property.getPropertyName().contains("FLAT")) {
            return property.getPropertyName();
        } else if (property.getPropertyName().contains("PARKING")) {
            return property.getPropertyName();
        }
        return property.getPropertyName();
    }

    private Integer extractRentDueDay(Property property) {
        // Extract rent due day from property data
        // This would need to be implemented based on your property structure
        return 1; // Default
    }

    private Customer getTenantForProperty(Long propertyId) {
        // Get tenant for property - implement based on your relationships
        return null; // Placeholder
    }

    private List<FinancialTransaction> getTransactionsForProperty(Property property, LocalDate fromDate, LocalDate toDate) {
        try {
            if (property.getPayPropId() != null) {
                return financialTransactionRepository
                    .findPropertyTransactionsForStatement(property.getPayPropId(), fromDate, toDate);
            }
        } catch (Exception e) {
            System.err.println("Error getting transactions for property " + property.getId() + ": " + e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<ExpenseItem> getExpensesForProperty(Property property, LocalDate fromDate, LocalDate toDate) {
        // Get expenses for property from transactions
        List<ExpenseItem> expenses = new ArrayList<>();
        // Implementation would extract expenses from FinancialTransaction data
        return expenses;
    }

    private boolean isRentPayment(FinancialTransaction transaction) {
        return "invoice".equals(transaction.getTransactionType()) &&
               (transaction.getCategoryName() == null || transaction.getCategoryName().toLowerCase().contains("rent"));
    }

    private boolean isRobertEllisPayment(FinancialTransaction transaction) {
        return (transaction.getDescription() != null && transaction.getDescription().contains("Robert Ellis")) ||
               (transaction.getDataSource() != null && transaction.getDataSource().contains("ROBERT_ELLIS"));
    }

    private boolean isOldAccountPayment(FinancialTransaction transaction) {
        return (transaction.getDescription() != null && transaction.getDescription().contains("Old Account")) ||
               (transaction.getDataSource() != null && transaction.getDataSource().contains("PROPSK_OLD"));
    }

    private boolean isPayPropPayment(FinancialTransaction transaction) {
        return (transaction.getDescription() != null && transaction.getDescription().contains("PayProp")) ||
               (transaction.getDataSource() != null && transaction.getDataSource().contains("PAYPROP")) ||
               transaction.getPayPropBatchId() != null;
    }

    private BigDecimal calculateTotalRentReceived(List<FinancialTransaction> transactions) {
        return transactions.stream()
            .filter(this::isRentPayment)
            .map(FinancialTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculatePayPropAmount(List<FinancialTransaction> transactions) {
        return transactions.stream()
            .filter(this::isRentPayment)
            .filter(this::isPayPropPayment)
            .map(FinancialTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateOldAccountAmount(List<FinancialTransaction> transactions) {
        return transactions.stream()
            .filter(this::isRentPayment)
            .filter(this::isOldAccountPayment)
            .map(FinancialTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String determineComments(PropertyUnit unit, Property property) {
        if (unit.tenantName == null || unit.tenantName.trim().isEmpty()) {
            return "Vacant";
        }
        return "";
    }

    // ===== DATA CLASSES =====

    public static class BodenHouseStatementData {
        public String client;
        public LocalDate fromDate;
        public LocalDate toDate;
        public String period;
        public List<PropertyGroup> propertyGroups = new ArrayList<>();
        public Set<StatementDataSource> includedDataSources; // New field for tracking data sources

        // Grand totals
        public BigDecimal grandTotalRentDue = BigDecimal.ZERO;
        public BigDecimal grandTotalRentReceived = BigDecimal.ZERO;
        public BigDecimal grandTotalManagementFees = BigDecimal.ZERO;
        public BigDecimal grandTotalServiceFees = BigDecimal.ZERO;
        public BigDecimal grandTotalFees = BigDecimal.ZERO;
        public BigDecimal grandTotalExpenses = BigDecimal.ZERO;
        public BigDecimal grandTotalNetDue = BigDecimal.ZERO;

        // Helper method to get data source summary
        public String getDataSourceSummary() {
            if (includedDataSources == null || includedDataSources.isEmpty()) {
                return "All data sources";
            }
            return includedDataSources.stream()
                .map(StatementDataSource::getDisplayName)
                .collect(Collectors.joining(", "));
        }

        // Payment summary
        public BigDecimal totalPayments = BigDecimal.ZERO;
        public BigDecimal previousMonthBalance = BigDecimal.ZERO;
        public BigDecimal amountPaid = BigDecimal.ZERO;
        public BigDecimal amountOwed = BigDecimal.ZERO;
        public BigDecimal balance = BigDecimal.ZERO;
    }

    public static class PropertyGroup {
        public String propertyName;
        public List<PropertyUnit> units = new ArrayList<>();

        // Group totals
        public BigDecimal totalRentDue = BigDecimal.ZERO;
        public BigDecimal totalRentReceived = BigDecimal.ZERO;
        public BigDecimal totalReceivedPayProp = BigDecimal.ZERO;
        public BigDecimal totalReceivedOldAccount = BigDecimal.ZERO;
        public BigDecimal totalReceivedByPropsk = BigDecimal.ZERO;
        public BigDecimal totalManagementFees = BigDecimal.ZERO;
        public BigDecimal totalServiceFees = BigDecimal.ZERO;
        public BigDecimal totalFees = BigDecimal.ZERO;
        public BigDecimal totalExpenses = BigDecimal.ZERO;
        public BigDecimal totalExpensesAndCommission = BigDecimal.ZERO;
        public BigDecimal totalNetDue = BigDecimal.ZERO;
    }

    public static class PropertyUnit {
        public String unitNumber;
        public String propertyAddress;
        public String tenantName;
        public String tenantId; // For tenant balance tracking
        public String tenancyDates;
        public Integer rentDueDate;
        public BigDecimal rentDueAmount = BigDecimal.ZERO;
        public String rentReceivedDate;
        public boolean paidToRobertEllis;
        public boolean paidToPropskOldAccount;
        public boolean paidToPropskPayProp;
        public BigDecimal rentReceivedAmount = BigDecimal.ZERO;
        public BigDecimal amountReceivedPayProp = BigDecimal.ZERO;
        public BigDecimal amountReceivedOldAccount = BigDecimal.ZERO;
        public BigDecimal totalRentReceivedByPropsk = BigDecimal.ZERO;
        public BigDecimal managementFeePercentage = new BigDecimal("10.00");
        public BigDecimal managementFeeAmount = BigDecimal.ZERO;
        public BigDecimal serviceFeePercentage = new BigDecimal("5.00");
        public BigDecimal serviceFeeAmount = BigDecimal.ZERO;
        public BigDecimal totalFeesChargedByPropsk = BigDecimal.ZERO;
        public List<ExpenseItem> expenses = new ArrayList<>();
        public BigDecimal totalExpenses = BigDecimal.ZERO;
        public BigDecimal totalExpensesAndCommission = BigDecimal.ZERO;
        public BigDecimal netDueToOwner = BigDecimal.ZERO;
        public String datePaid;
        public BigDecimal rentDueLessReceived = BigDecimal.ZERO;
        public String comments;
    }

    public static class ExpenseItem {
        public String label;
        public BigDecimal amount;
        public String comment;
    }

    // =====================================================
    // DATA SOURCE FILTERING METHODS
    // =====================================================

    /**
     * Filter transactions by selected data sources
     */
    private List<FinancialTransaction> filterTransactionsByDataSource(List<FinancialTransaction> transactions, Set<StatementDataSource> includedDataSources) {
        if (includedDataSources == null || includedDataSources.isEmpty()) {
            return transactions; // Include all if no filter specified
        }

        return transactions.stream()
            .filter(transaction -> isTransactionIncluded(transaction, includedDataSources))
            .collect(Collectors.toList());
    }

    /**
     * Check if transaction should be included based on data sources
     */
    private boolean isTransactionIncluded(FinancialTransaction transaction, Set<StatementDataSource> includedDataSources) {
        return includedDataSources.stream()
            .anyMatch(dataSource -> dataSource.matchesTransaction(transaction.getDataSource()));
    }

    /**
     * Get filtered expenses for property
     */
    private List<ExpenseItem> getFilteredExpensesForProperty(Property property, LocalDate fromDate, LocalDate toDate, Set<StatementDataSource> includedDataSources) {
        // Get all expense transactions directly from financial transactions
        List<FinancialTransaction> allExpenseTransactions = getExpenseTransactionsForProperty(property, fromDate, toDate);
        List<FinancialTransaction> filteredExpenses = filterTransactionsByDataSource(allExpenseTransactions, includedDataSources);

        return filteredExpenses.stream()
            .map(this::convertToExpenseItem)
            .collect(Collectors.toList());
    }

    /**
     * Check if transaction is an expense
     */
    private boolean isExpenseTransaction(FinancialTransaction transaction) {
        String type = transaction.getTransactionType();
        return type != null && (
            type.contains("payment") ||
            type.contains("expense") ||
            type.contains("debit") ||
            type.equals("payment_to_contractor") ||
            type.equals("payment_to_beneficiary")
        );
    }

    /**
     * Get expense transactions for property (returns FinancialTransaction list)
     */
    private List<FinancialTransaction> getExpenseTransactionsForProperty(Property property, LocalDate fromDate, LocalDate toDate) {
        if (property.getPayPropId() == null) {
            return new ArrayList<>();
        }

        return financialTransactionRepository.findByPropertyAndDateRange(property.getPayPropId(), fromDate, toDate)
            .stream()
            .filter(this::isExpenseTransaction)
            .collect(Collectors.toList());
    }

    /**
     * Convert FinancialTransaction to ExpenseItem
     */
    private ExpenseItem convertToExpenseItem(FinancialTransaction transaction) {
        ExpenseItem expense = new ExpenseItem();
        expense.label = transaction.getCategoryName() != null ? transaction.getCategoryName() : transaction.getDescription();
        expense.amount = transaction.getAmount();
        expense.comment = transaction.getDescription();
        return expense;
    }

    /**
     * Generate tenant balance formula with cross-sheet reference
     * Mimics: =AL14+'May Propsk Statement'!AM14
     */
    private String generateTenantBalanceFormula(PropertyUnit unit, int currentRow) {
        // Current period calculation: Rent Due - Rent Received
        String currentBalance = "AL" + currentRow;

        // Try to generate cross-sheet reference if there's historical data
        if (unit.tenantId != null && !unit.tenantId.isEmpty()) {
            LocalDate currentPeriod = LocalDate.now(); // Or get from context
            String crossSheetRef = tenantBalanceService.generateCrossSheetFormula(
                currentBalance, unit.tenantId, currentPeriod, currentRow);

            return crossSheetRef;
        } else {
            // No tenant ID, just use current calculation
            return currentBalance;
        }
    }

    /**
     * Update all tenant balances for statement period
     */
    public void updateTenantBalancesForPeriod(Customer propertyOwner, LocalDate fromDate, LocalDate toDate) {
        List<Property> properties = propertyService.getPropertiesByOwner(propertyOwner.getCustomerId());

        for (Property property : properties) {
            // Update balances for each property
            tenantBalanceService.updatePropertyBalances(property, toDate);
        }
    }

    /**
     * TEMPORARY: Generate sample properties for testing formulas when no real data exists
     * This will be removed once real property data is imported
     */
    private List<Property> generateSampleProperties(Customer propertyOwner) {
        List<Property> sampleProperties = new ArrayList<>();

        // Create sample Boden House property
        Property bodenFlat1 = new Property();
        bodenFlat1.setId(999L);
        bodenFlat1.setPayPropId("SAMPLE_BODEN_FLAT1");
        bodenFlat1.setPropertyName("FLAT 1, BODEN HOUSE");
        bodenFlat1.setMonthlyPayment(new BigDecimal("1200.00"));

        Property bodenFlat2 = new Property();
        bodenFlat2.setId(998L);
        bodenFlat2.setPayPropId("SAMPLE_BODEN_FLAT2");
        bodenFlat2.setPropertyName("FLAT 2, BODEN HOUSE");
        bodenFlat2.setMonthlyPayment(new BigDecimal("1350.00"));

        // Create sample Knighton Hayes property
        Property knightonFlat1 = new Property();
        knightonFlat1.setId(997L);
        knightonFlat1.setPayPropId("SAMPLE_KNIGHTON_FLAT1");
        knightonFlat1.setPropertyName("FLAT 1, KNIGHTON HAYES");
        knightonFlat1.setMonthlyPayment(new BigDecimal("1100.00"));

        sampleProperties.add(bodenFlat1);
        sampleProperties.add(bodenFlat2);
        sampleProperties.add(knightonFlat1);

        return sampleProperties;
    }

}
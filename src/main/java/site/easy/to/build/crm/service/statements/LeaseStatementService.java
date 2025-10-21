package site.easy.to.build.crm.service.statements;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.dto.LeaseStatementRow;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating lease-based financial statements
 * Transforms property-centric data into lease-centric statements
 */
@Service
public class LeaseStatementService {

    private static final Logger logger = LoggerFactory.getLogger(LeaseStatementService.class);

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private LeaseBalanceCalculationService balanceCalculationService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private HistoricalTransactionRepository historicalTransactionRepository;

    // Default commission rates - can be overridden per property/owner
    private static final BigDecimal DEFAULT_MANAGEMENT_FEE_PERCENTAGE = new BigDecimal("10");
    private static final BigDecimal DEFAULT_SERVICE_FEE_PERCENTAGE = new BigDecimal("5");

    /**
     * Generate lease-based statement rows for a property owner
     *
     * @param propertyOwner The property owner
     * @param periodStart Statement period start
     * @param periodEnd Statement period end
     * @return Map of property ID to list of lease statement rows
     */
    public Map<Long, List<LeaseStatementRow>> generateLeaseStatementForOwner(
            Customer propertyOwner, LocalDate periodStart, LocalDate periodEnd) {

        logger.info("Generating lease-based statement for owner {} from {} to {}",
                   propertyOwner.getCustomerId(), periodStart, periodEnd);

        // Get all properties for this owner
        List<Property> properties = propertyRepository.findByPropertyOwnerId(propertyOwner.getCustomerId());

        logger.info("Found {} properties for owner {}", properties.size(), propertyOwner.getCustomerId());

        Map<Long, List<LeaseStatementRow>> statementByProperty = new LinkedHashMap<>();

        for (Property property : properties) {
            List<LeaseStatementRow> leaseRows = generateLeaseRowsForProperty(property, periodStart, periodEnd);
            if (!leaseRows.isEmpty()) {
                statementByProperty.put(property.getId(), leaseRows);
            }
        }

        return statementByProperty;
    }

    /**
     * Generate lease statement rows for a single property
     *
     * @param property The property
     * @param periodStart Statement period start
     * @param periodEnd Statement period end
     * @return List of lease statement rows
     */
    public List<LeaseStatementRow> generateLeaseRowsForProperty(
            Property property, LocalDate periodStart, LocalDate periodEnd) {

        logger.info("Generating lease rows for property {} from {} to {}",
                   property.getId(), periodStart, periodEnd);

        // Get all leases (invoices) for this property
        // Include leases that overlap with the statement period
        List<Invoice> leases = invoiceRepository.findByPropertyAndDateRangeOverlap(
            property, periodStart, periodEnd);

        logger.info("Found {} leases for property {}", leases.size(), property.getId());

        List<LeaseStatementRow> rows = new ArrayList<>();

        for (Invoice lease : leases) {
            LeaseStatementRow row = buildLeaseStatementRow(lease, property, periodStart, periodEnd);
            rows.add(row);
        }

        // Sort by lease reference
        rows.sort(Comparator.comparing(LeaseStatementRow::getLeaseReference,
                                      Comparator.nullsLast(Comparator.naturalOrder())));

        return rows;
    }

    /**
     * Build a single lease statement row
     */
    private LeaseStatementRow buildLeaseStatementRow(
            Invoice lease, Property property, LocalDate periodStart, LocalDate periodEnd) {

        LeaseStatementRow row = new LeaseStatementRow();

        // Lease identification
        row.setLeaseId(lease.getId());
        row.setLeaseReference(lease.getLeaseReference());
        row.setUnitNumber(property.getPropertyName()); // Property doesn't have unitNumber, using propertyName
        row.setPropertyName(property.getPropertyName());

        // Tenant information
        Customer tenant = lease.getCustomer();
        if (tenant != null) {
            row.setTenantName(tenant.getFullName());
            row.setTenantId(tenant.getCustomerId());
        } else {
            row.setTenantName("Vacant");
        }

        // Lease dates
        row.setTenancyStartDate(lease.getStartDate());
        row.setTenancyEndDate(lease.getEndDate());
        row.setRentDueDay(lease.getPaymentDay());

        // Calculate rent due for this period
        BigDecimal rentDue = balanceCalculationService.calculateRentDue(lease, periodStart, periodEnd);
        row.setRentDueAmount(rentDue);

        // Calculate payments received for this period
        BigDecimal paymentsReceived = balanceCalculationService.calculatePaymentsReceived(
            lease.getId(), periodStart, periodEnd);
        row.setRentReceivedAmount(paymentsReceived);

        // Get the most recent payment date
        LocalDate lastPaymentDate = balanceCalculationService.getLastPaymentDate(
            lease.getId(), periodStart, periodEnd);
        row.setRentReceivedDate(lastPaymentDate);

        // Commission and fees
        BigDecimal managementFeePercentage = getManagementFeePercentage(property);
        BigDecimal serviceFeePercentage = getServiceFeePercentage(property);

        row.setManagementFeePercentage(managementFeePercentage);
        row.setServiceFeePercentage(serviceFeePercentage);

        // Calculate total commission percentage
        BigDecimal totalCommissionPercentage = managementFeePercentage.add(serviceFeePercentage);

        // Calculate fees based on rent received
        BigDecimal managementFee = balanceCalculationService.calculateManagementFee(
            paymentsReceived, managementFeePercentage);
        BigDecimal serviceFee = balanceCalculationService.calculateManagementFee(
            paymentsReceived, serviceFeePercentage);

        row.setManagementFeeAmount(managementFee);
        row.setServiceFeeAmount(serviceFee);

        // Get expenses for this lease
        List<LeaseStatementRow.ExpenseItem> expenses = getExpensesForLease(lease, periodStart, periodEnd);
        row.setExpenses(expenses);

        // Calculate tenant balance (cumulative from lease start)
        BigDecimal tenantBalance = balanceCalculationService.calculateTenantBalance(lease, periodEnd);
        row.setTenantBalance(tenantBalance);

        // Calculate all totals
        row.calculateTotals();

        return row;
    }

    /**
     * Get expenses allocated to a specific lease
     */
    private List<LeaseStatementRow.ExpenseItem> getExpensesForLease(
            Invoice lease, LocalDate periodStart, LocalDate periodEnd) {

        List<LeaseStatementRow.ExpenseItem> expenses = new ArrayList<>();

        // Get expenses from FinancialTransaction
        List<FinancialTransaction> financialExpenses = financialTransactionRepository
            .findExpensesByInvoiceIdAndDateRange(lease.getId(), periodStart, periodEnd);

        for (FinancialTransaction expense : financialExpenses) {
            LeaseStatementRow.ExpenseItem item = new LeaseStatementRow.ExpenseItem();
            item.setLabel(expense.getCategoryName() != null ? expense.getCategoryName() : expense.getDescription());
            item.setAmount(expense.getAmount().abs()); // Expenses are typically negative
            item.setComment(expense.getDescription());
            expenses.add(item);
        }

        // Get expenses from HistoricalTransaction
        List<HistoricalTransaction> historicalExpenses = historicalTransactionRepository
            .findExpensesByInvoiceIdAndDateRange(lease.getId(), periodStart, periodEnd);

        for (HistoricalTransaction expense : historicalExpenses) {
            LeaseStatementRow.ExpenseItem item = new LeaseStatementRow.ExpenseItem();
            item.setLabel(expense.getCategory() != null ? expense.getCategory() : "Expense");
            item.setAmount(expense.getAmount().abs());
            item.setComment(expense.getDescription());
            expenses.add(item);
        }

        // Sort by amount descending
        expenses.sort(Comparator.comparing(LeaseStatementRow.ExpenseItem::getAmount).reversed());

        return expenses;
    }

    /**
     * Get management fee percentage for a property
     * Can be customized per property or owner
     */
    private BigDecimal getManagementFeePercentage(Property property) {
        // TODO: Look up property-specific rate if configured
        // For now, return default
        return DEFAULT_MANAGEMENT_FEE_PERCENTAGE;
    }

    /**
     * Get service fee percentage for a property
     * Can be customized per property or owner
     */
    private BigDecimal getServiceFeePercentage(Property property) {
        // TODO: Look up property-specific rate if configured
        // For now, return default
        return DEFAULT_SERVICE_FEE_PERCENTAGE;
    }

    /**
     * Generate statement for a single property
     */
    public List<LeaseStatementRow> generateLeaseStatementForProperty(
            Property property, LocalDate periodStart, LocalDate periodEnd) {

        return generateLeaseRowsForProperty(property, periodStart, periodEnd);
    }

    /**
     * Generate statement rows for multiple properties
     */
    public Map<Long, List<LeaseStatementRow>> generateLeaseStatementForProperties(
            List<Property> properties, LocalDate periodStart, LocalDate periodEnd) {

        Map<Long, List<LeaseStatementRow>> statementByProperty = new LinkedHashMap<>();

        for (Property property : properties) {
            List<LeaseStatementRow> leaseRows = generateLeaseRowsForProperty(property, periodStart, periodEnd);
            if (!leaseRows.isEmpty()) {
                statementByProperty.put(property.getId(), leaseRows);
            }
        }

        return statementByProperty;
    }

    /**
     * Calculate totals across all leases in a statement
     */
    public Map<String, BigDecimal> calculateStatementTotals(List<LeaseStatementRow> rows) {
        Map<String, BigDecimal> totals = new HashMap<>();

        BigDecimal totalRentDue = BigDecimal.ZERO;
        BigDecimal totalRentReceived = BigDecimal.ZERO;
        BigDecimal totalManagementFees = BigDecimal.ZERO;
        BigDecimal totalServiceFees = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        BigDecimal totalNetToLandlord = BigDecimal.ZERO;
        BigDecimal totalBalance = BigDecimal.ZERO;

        for (LeaseStatementRow row : rows) {
            totalRentDue = totalRentDue.add(row.getRentDueAmount() != null ? row.getRentDueAmount() : BigDecimal.ZERO);
            totalRentReceived = totalRentReceived.add(row.getRentReceivedAmount() != null ? row.getRentReceivedAmount() : BigDecimal.ZERO);
            totalManagementFees = totalManagementFees.add(row.getManagementFeeAmount() != null ? row.getManagementFeeAmount() : BigDecimal.ZERO);
            totalServiceFees = totalServiceFees.add(row.getServiceFeeAmount() != null ? row.getServiceFeeAmount() : BigDecimal.ZERO);
            totalExpenses = totalExpenses.add(row.getTotalExpenses() != null ? row.getTotalExpenses() : BigDecimal.ZERO);
            totalNetToLandlord = totalNetToLandlord.add(row.getNetDueToLandlord() != null ? row.getNetDueToLandlord() : BigDecimal.ZERO);
            totalBalance = totalBalance.add(row.getTenantBalance() != null ? row.getTenantBalance() : BigDecimal.ZERO);
        }

        totals.put("totalRentDue", totalRentDue);
        totals.put("totalRentReceived", totalRentReceived);
        totals.put("totalManagementFees", totalManagementFees);
        totals.put("totalServiceFees", totalServiceFees);
        totals.put("totalExpenses", totalExpenses);
        totals.put("totalNetToLandlord", totalNetToLandlord);
        totals.put("totalBalance", totalBalance);

        return totals;
    }
}

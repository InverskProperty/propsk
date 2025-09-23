package site.easy.to.build.crm.service.tenant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.FinancialTransaction;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.TenantBalance;
import site.easy.to.build.crm.repository.FinancialTransactionRepository;
import site.easy.to.build.crm.repository.TenantBalanceRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing tenant balances and running totals
 * Handles cross-period balance calculations for rent arrears tracking
 */
@Service
public class TenantBalanceService {

    @Autowired
    private TenantBalanceRepository tenantBalanceRepository;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    /**
     * Calculate and update tenant balance for a specific period
     */
    public TenantBalance calculateTenantBalance(String tenantId, String propertyId,
                                              LocalDate statementPeriod,
                                              BigDecimal rentDue) {

        // Get or create tenant balance record
        TenantBalance balance = tenantBalanceRepository
            .findByTenantIdAndStatementPeriod(tenantId, statementPeriod)
            .orElse(new TenantBalance(tenantId, propertyId, statementPeriod));

        // Get previous balance
        BigDecimal previousBalance = getPreviousBalance(tenantId, statementPeriod);
        balance.setPreviousBalance(previousBalance);

        // Set rent due
        balance.setRentDue(rentDue);

        // Calculate rent received from financial transactions
        BigDecimal rentReceived = calculateRentReceived(propertyId, statementPeriod);
        balance.setRentReceived(rentReceived);

        // Calculate additional charges
        BigDecimal charges = calculateCharges(propertyId, statementPeriod);
        balance.setCharges(charges);

        // Calculate payments (beyond rent)
        BigDecimal payments = calculatePayments(propertyId, statementPeriod);
        balance.setPayments(payments);

        // Calculate running balance
        balance.calculateRunningBalance();

        // Save and return
        return tenantBalanceRepository.save(balance);
    }

    /**
     * Get previous balance for cross-sheet reference equivalent
     */
    public BigDecimal getPreviousBalance(String tenantId, LocalDate currentPeriod) {
        return tenantBalanceRepository
            .findMostRecentBalanceForTenant(tenantId, currentPeriod)
            .map(TenantBalance::getRunningBalance)
            .orElse(BigDecimal.ZERO);
    }

    /**
     * Calculate rent received from financial transactions
     */
    private BigDecimal calculateRentReceived(String propertyId, LocalDate statementPeriod) {
        // Get start and end of statement period
        LocalDate fromDate = statementPeriod.withDayOfMonth(1);
        LocalDate toDate = statementPeriod.withDayOfMonth(statementPeriod.lengthOfMonth());

        // Find rent payments
        List<FinancialTransaction> rentPayments = financialTransactionRepository
            .findByPropertyAndDateRange(propertyId, fromDate, toDate)
            .stream()
            .filter(this::isRentPayment)
            .toList();

        return rentPayments.stream()
            .map(FinancialTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate additional charges (late fees, damages, etc.)
     */
    private BigDecimal calculateCharges(String propertyId, LocalDate statementPeriod) {
        LocalDate fromDate = statementPeriod.withDayOfMonth(1);
        LocalDate toDate = statementPeriod.withDayOfMonth(statementPeriod.lengthOfMonth());

        List<FinancialTransaction> charges = financialTransactionRepository
            .findByPropertyAndDateRange(propertyId, fromDate, toDate)
            .stream()
            .filter(this::isChargeTransaction)
            .toList();

        return charges.stream()
            .map(FinancialTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate payments (non-rent payments like deposits returned, etc.)
     */
    private BigDecimal calculatePayments(String propertyId, LocalDate statementPeriod) {
        LocalDate fromDate = statementPeriod.withDayOfMonth(1);
        LocalDate toDate = statementPeriod.withDayOfMonth(statementPeriod.lengthOfMonth());

        List<FinancialTransaction> payments = financialTransactionRepository
            .findByPropertyAndDateRange(propertyId, fromDate, toDate)
            .stream()
            .filter(this::isPaymentToTenant)
            .toList();

        return payments.stream()
            .map(FinancialTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Generate Excel formula for cross-sheet reference
     * This creates the equivalent of: =AL14+'May Propsk Statement'!AM14
     */
    public String generateCrossSheetFormula(String currentCellRef, String tenantId,
                                          LocalDate currentPeriod, int currentRow) {

        LocalDate previousPeriod = currentPeriod.minusMonths(1);
        String previousSheetName = formatSheetName(previousPeriod);

        // Get previous balance for validation (optional)
        BigDecimal previousBalance = getPreviousBalance(tenantId, currentPeriod);

        if (previousBalance.equals(BigDecimal.ZERO)) {
            // No previous balance, just use current calculation
            return currentCellRef;
        } else {
            // Generate cross-sheet reference
            return currentCellRef + "+'" + previousSheetName + "'!" + currentCellRef;
        }
    }

    /**
     * Format sheet name for cross-reference (like "May Propsk Statement")
     */
    private String formatSheetName(LocalDate date) {
        String monthName = date.getMonth().name().toLowerCase();
        monthName = monthName.substring(0, 1).toUpperCase() + monthName.substring(1);
        return monthName + " Propsk Statement";
    }

    /**
     * Get all tenants in arrears for a period
     */
    public List<TenantBalance> getTenantsInArrears(LocalDate period) {
        return tenantBalanceRepository.findTenantsInArrears(period);
    }

    /**
     * Get balance history for a tenant
     */
    public List<TenantBalance> getTenantBalanceHistory(String tenantId) {
        return tenantBalanceRepository.findByTenantIdOrderByStatementPeriodDesc(tenantId);
    }

    /**
     * Update balances for all tenants in a property for a given period
     */
    public void updatePropertyBalances(Property property, LocalDate statementPeriod) {
        // This would be called when generating statements to ensure all balances are current
        String propertyId = property.getPayPropId();
        BigDecimal rentDue = property.getMonthlyPayment() != null ?
            property.getMonthlyPayment() : BigDecimal.ZERO;

        // For now, assume one tenant per property
        // In reality, you'd iterate through all tenants
        String tenantId = getTenantIdForProperty(propertyId);
        if (tenantId != null) {
            calculateTenantBalance(tenantId, propertyId, statementPeriod, rentDue);
        }
    }

    // Helper methods for transaction classification
    private boolean isRentPayment(FinancialTransaction transaction) {
        String type = transaction.getTransactionType();
        String category = transaction.getCategoryName();

        return "invoice".equals(type) &&
               (category == null || category.toLowerCase().contains("rent"));
    }

    private boolean isChargeTransaction(FinancialTransaction transaction) {
        String category = transaction.getCategoryName();
        return category != null && (
            category.toLowerCase().contains("late fee") ||
            category.toLowerCase().contains("damage") ||
            category.toLowerCase().contains("charge")
        );
    }

    private boolean isPaymentToTenant(FinancialTransaction transaction) {
        String type = transaction.getTransactionType();
        return "credit_note".equals(type) ||
               "payment_to_tenant".equals(type);
    }

    private String getTenantIdForProperty(String propertyId) {
        // TODO: Implement logic to get current tenant for property
        // This would query your tenant/customer relationships
        return null;
    }
}
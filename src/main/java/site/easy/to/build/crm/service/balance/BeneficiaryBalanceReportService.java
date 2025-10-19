package site.easy.to.build.crm.service.balance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import site.easy.to.build.crm.entity.BeneficiaryBalance;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.repository.BeneficiaryBalanceRepository;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.repository.CustomerRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for generating beneficiary balance reports and dashboard data
 *
 * Provides various reports including:
 * - Balances due for payment
 * - Overdrawn accounts
 * - Balance summaries by owner
 * - Historical balance trends
 */
@Service
@Transactional(readOnly = true)
public class BeneficiaryBalanceReportService {

    private static final Logger log = LoggerFactory.getLogger(BeneficiaryBalanceReportService.class);

    @Autowired
    private BeneficiaryBalanceRepository beneficiaryBalanceRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    // ===== DASHBOARD SUMMARY =====

    /**
     * Get comprehensive dashboard summary of all beneficiary balances
     */
    public DashboardSummary getDashboardSummary() {
        DashboardSummary summary = new DashboardSummary();

        // Get all active balances
        List<BeneficiaryBalance> activeBalances = beneficiaryBalanceRepository.findActiveBalances();

        // Calculate totals
        BigDecimal totalOwed = BigDecimal.ZERO;
        BigDecimal totalOverdrawn = BigDecimal.ZERO;
        int positiveCount = 0;
        int negativeCount = 0;

        for (BeneficiaryBalance balance : activeBalances) {
            BigDecimal amount = balance.getBalanceAmount();
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                totalOwed = totalOwed.add(amount);
                positiveCount++;
            } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
                totalOverdrawn = totalOverdrawn.add(amount.negate());
                negativeCount++;
            }
        }

        summary.totalOwedToOwners = totalOwed;
        summary.totalOwedByOwners = totalOverdrawn;
        summary.netPosition = totalOwed.subtract(totalOverdrawn);
        summary.accountsWithBalance = positiveCount;
        summary.overdrawnAccounts = negativeCount;
        summary.zeroBalanceAccounts = activeBalances.size() - positiveCount - negativeCount;

        return summary;
    }

    // ===== PAYMENT DUE REPORTS =====

    /**
     * Get list of balances due for payment (above threshold)
     *
     * @param threshold Minimum balance to include (e.g., £100)
     * @return List of balances due for payment
     */
    public List<BalanceDueReport> getBalancesDueForPayment(BigDecimal threshold) {
        List<BeneficiaryBalance> balances = beneficiaryBalanceRepository
                .findBalancesDueForPayment(threshold);

        return balances.stream()
                .map(this::createBalanceDueReport)
                .collect(Collectors.toList());
    }

    /**
     * Get total amount due for payment
     */
    public BigDecimal getTotalDueForPayment(BigDecimal threshold) {
        return getBalancesDueForPayment(threshold).stream()
                .map(report -> report.currentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ===== OVERDRAWN ACCOUNT REPORTS =====

    /**
     * Get list of overdrawn accounts (owners who owe the agency)
     */
    public List<OverdrawnReport> getOverdrawnAccounts() {
        List<BeneficiaryBalance> balances = beneficiaryBalanceRepository.findOverdrawnBalances();

        return balances.stream()
                .map(this::createOverdrawnReport)
                .collect(Collectors.toList());
    }

    /**
     * Get total amount owed by owners to agency
     */
    public BigDecimal getTotalOwedByOwners() {
        return getOverdrawnAccounts().stream()
                .map(report -> report.amountOwed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ===== OWNER SUMMARY REPORTS =====

    /**
     * Get balance summary for a specific owner across all properties
     */
    public OwnerBalanceSummary getOwnerBalanceSummary(Customer owner) {
        List<BeneficiaryBalance> balances = beneficiaryBalanceRepository
                .findActiveBalancesByCustomer(owner);

        OwnerBalanceSummary summary = new OwnerBalanceSummary();
        summary.owner = owner;
        summary.propertyBalances = new ArrayList<>();

        BigDecimal total = BigDecimal.ZERO;
        for (BeneficiaryBalance balance : balances) {
            PropertyBalanceDetail detail = new PropertyBalanceDetail();
            detail.property = balance.getProperty();
            detail.currentBalance = balance.getBalanceAmount();
            detail.openingBalance = balance.getOpeningBalance();
            detail.rentAllocated = balance.getTotalRentAllocated();
            detail.expenses = balance.getTotalExpenses();
            detail.paymentsOut = balance.getTotalPaymentsOut();
            detail.balanceDate = balance.getBalanceDate();

            summary.propertyBalances.add(detail);
            total = total.add(balance.getBalanceAmount());
        }

        summary.totalBalance = total;
        summary.propertyCount = balances.size();

        return summary;
    }

    /**
     * Get balance summaries for all owners who have balances
     */
    public List<OwnerBalanceSummary> getAllOwnerBalanceSummaries() {
        List<BeneficiaryBalance> allBalances = beneficiaryBalanceRepository.findActiveBalances();

        // Group by customer
        Map<Long, Customer> customersWithBalances = allBalances.stream()
                .filter(b -> b.getCustomer() != null)
                .collect(Collectors.toMap(
                        b -> b.getCustomer().getCustomerId(),
                        BeneficiaryBalance::getCustomer,
                        (existing, replacement) -> existing
                ));

        List<OwnerBalanceSummary> summaries = new ArrayList<>();
        for (Customer owner : customersWithBalances.values()) {
            OwnerBalanceSummary summary = getOwnerBalanceSummary(owner);
            if (!summary.propertyBalances.isEmpty()) {
                summaries.add(summary);
            }
        }

        // Sort by total balance descending
        summaries.sort((a, b) -> b.totalBalance.compareTo(a.totalBalance));

        return summaries;
    }

    // ===== PROPERTY SUMMARY REPORTS =====

    /**
     * Get balance summary for a specific property
     */
    public PropertyBalanceSummary getPropertyBalanceSummary(Property property) {
        List<BeneficiaryBalance> balances = beneficiaryBalanceRepository
                .findByProperty(property);

        PropertyBalanceSummary summary = new PropertyBalanceSummary();
        summary.property = property;
        summary.ownerBalances = new ArrayList<>();

        BigDecimal total = BigDecimal.ZERO;
        for (BeneficiaryBalance balance : balances) {
            OwnerBalanceDetail detail = new OwnerBalanceDetail();
            detail.owner = balance.getCustomer();
            detail.currentBalance = balance.getBalanceAmount();
            detail.balanceDate = balance.getBalanceDate();

            summary.ownerBalances.add(detail);
            total = total.add(balance.getBalanceAmount());
        }

        summary.totalBalance = total;
        summary.ownerCount = balances.size();

        return summary;
    }

    // ===== HELPER METHODS =====

    private BalanceDueReport createBalanceDueReport(BeneficiaryBalance balance) {
        BalanceDueReport report = new BalanceDueReport();
        report.owner = balance.getCustomer();
        report.property = balance.getProperty();
        report.currentBalance = balance.getBalanceAmount();
        report.openingBalance = balance.getOpeningBalance();
        report.rentAllocated = balance.getTotalRentAllocated();
        report.expenses = balance.getTotalExpenses();
        report.paymentsOut = balance.getTotalPaymentsOut();
        report.balanceDate = balance.getBalanceDate();
        report.recommendedPayment = balance.getBalanceAmount();
        return report;
    }

    private OverdrawnReport createOverdrawnReport(BeneficiaryBalance balance) {
        OverdrawnReport report = new OverdrawnReport();
        report.owner = balance.getCustomer();
        report.property = balance.getProperty();
        report.amountOwed = balance.getBalanceAmount().negate(); // Make positive
        report.rentAllocated = balance.getTotalRentAllocated();
        report.expenses = balance.getTotalExpenses();
        report.balanceDate = balance.getBalanceDate();
        return report;
    }

    // ===== DATA CLASSES =====

    public static class DashboardSummary {
        public BigDecimal totalOwedToOwners;
        public BigDecimal totalOwedByOwners;
        public BigDecimal netPosition;
        public int accountsWithBalance;
        public int overdrawnAccounts;
        public int zeroBalanceAccounts;
    }

    public static class BalanceDueReport {
        public Customer owner;
        public Property property;
        public BigDecimal currentBalance;
        public BigDecimal openingBalance;
        public BigDecimal rentAllocated;
        public BigDecimal expenses;
        public BigDecimal paymentsOut;
        public LocalDate balanceDate;
        public BigDecimal recommendedPayment;
    }

    public static class OverdrawnReport {
        public Customer owner;
        public Property property;
        public BigDecimal amountOwed;
        public BigDecimal rentAllocated;
        public BigDecimal expenses;
        public LocalDate balanceDate;
    }

    public static class OwnerBalanceSummary {
        public Customer owner;
        public List<PropertyBalanceDetail> propertyBalances;
        public BigDecimal totalBalance;
        public int propertyCount;
    }

    public static class PropertyBalanceDetail {
        public Property property;
        public BigDecimal currentBalance;
        public BigDecimal openingBalance;
        public BigDecimal rentAllocated;
        public BigDecimal expenses;
        public BigDecimal paymentsOut;
        public LocalDate balanceDate;
    }

    public static class PropertyBalanceSummary {
        public Property property;
        public List<OwnerBalanceDetail> ownerBalances;
        public BigDecimal totalBalance;
        public int ownerCount;
    }

    public static class OwnerBalanceDetail {
        public Customer owner;
        public BigDecimal currentBalance;
        public LocalDate balanceDate;
    }
}

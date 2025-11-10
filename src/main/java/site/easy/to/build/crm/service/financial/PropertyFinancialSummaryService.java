package site.easy.to.build.crm.service.financial;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.UnifiedTransaction;
import site.easy.to.build.crm.repository.UnifiedTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Centralized service for property financial summary calculations.
 *
 * This service provides consistent transaction classification and aggregation
 * logic across the entire application (financials page, property details, statements, etc.)
 *
 * SINGLE SOURCE OF TRUTH for:
 * - What constitutes a "commission" transaction
 * - What constitutes an "expense" transaction
 * - How to calculate totals and net income
 */
@Service
public class PropertyFinancialSummaryService {

    private static final Logger log = LoggerFactory.getLogger(PropertyFinancialSummaryService.class);

    @Autowired
    private UnifiedTransactionRepository unifiedTransactionRepository;

    /**
     * Financial summary DTO for a property
     */
    public static class PropertyFinancialSummary {
        private Long propertyId;
        private String propertyName;
        private LocalDate periodStart;
        private LocalDate periodEnd;

        private BigDecimal totalRent = BigDecimal.ZERO;
        private BigDecimal totalExpenses = BigDecimal.ZERO;
        private BigDecimal totalCommission = BigDecimal.ZERO;
        private BigDecimal netToOwner = BigDecimal.ZERO;

        private int totalTransactionCount = 0;
        private int rentTransactionCount = 0;
        private int expenseTransactionCount = 0;
        private int commissionTransactionCount = 0;

        private List<UnifiedTransaction> allTransactions = new ArrayList<>();
        private List<UnifiedTransaction> rentTransactions = new ArrayList<>();
        private List<UnifiedTransaction> expenseTransactions = new ArrayList<>();
        private List<UnifiedTransaction> commissionTransactions = new ArrayList<>();

        // Getters and setters
        public Long getPropertyId() { return propertyId; }
        public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }

        public String getPropertyName() { return propertyName; }
        public void setPropertyName(String propertyName) { this.propertyName = propertyName; }

        public LocalDate getPeriodStart() { return periodStart; }
        public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

        public LocalDate getPeriodEnd() { return periodEnd; }
        public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

        public BigDecimal getTotalRent() { return totalRent; }
        public void setTotalRent(BigDecimal totalRent) { this.totalRent = totalRent; }

        public BigDecimal getTotalExpenses() { return totalExpenses; }
        public void setTotalExpenses(BigDecimal totalExpenses) { this.totalExpenses = totalExpenses; }

        public BigDecimal getTotalCommission() { return totalCommission; }
        public void setTotalCommission(BigDecimal totalCommission) { this.totalCommission = totalCommission; }

        public BigDecimal getNetToOwner() { return netToOwner; }
        public void setNetToOwner(BigDecimal netToOwner) { this.netToOwner = netToOwner; }

        public int getTotalTransactionCount() { return totalTransactionCount; }
        public void setTotalTransactionCount(int count) { this.totalTransactionCount = count; }

        public int getRentTransactionCount() { return rentTransactionCount; }
        public void setRentTransactionCount(int count) { this.rentTransactionCount = count; }

        public int getExpenseTransactionCount() { return expenseTransactionCount; }
        public void setExpenseTransactionCount(int count) { this.expenseTransactionCount = count; }

        public int getCommissionTransactionCount() { return commissionTransactionCount; }
        public void setCommissionTransactionCount(int count) { this.commissionTransactionCount = count; }

        public List<UnifiedTransaction> getAllTransactions() { return allTransactions; }
        public void setAllTransactions(List<UnifiedTransaction> transactions) { this.allTransactions = transactions; }

        public List<UnifiedTransaction> getRentTransactions() { return rentTransactions; }
        public void setRentTransactions(List<UnifiedTransaction> transactions) { this.rentTransactions = transactions; }

        public List<UnifiedTransaction> getExpenseTransactions() { return expenseTransactions; }
        public void setExpenseTransactions(List<UnifiedTransaction> transactions) { this.expenseTransactions = transactions; }

        public List<UnifiedTransaction> getCommissionTransactions() { return commissionTransactions; }
        public void setCommissionTransactions(List<UnifiedTransaction> transactions) { this.commissionTransactions = transactions; }
    }

    /**
     * CENTRALIZED CLASSIFICATION LOGIC - SINGLE SOURCE OF TRUTH
     *
     * Determines if a transaction is RENT/INCOME
     */
    public boolean isRentTransaction(UnifiedTransaction tx) {
        if (tx.getFlowDirection() != UnifiedTransaction.FlowDirection.INCOMING) {
            return false;
        }

        String type = tx.getTransactionType();
        if (type == null) return false;

        String typeLower = type.toLowerCase();
        return typeLower.contains("rent") ||
               typeLower.contains("income") ||
               typeLower.contains("payment");
    }

    /**
     * CENTRALIZED CLASSIFICATION LOGIC - SINGLE SOURCE OF TRUTH
     *
     * Determines if a transaction is COMMISSION/AGENCY FEE
     */
    public boolean isCommissionTransaction(UnifiedTransaction tx) {
        if (tx.getFlowDirection() != UnifiedTransaction.FlowDirection.OUTGOING) {
            return false;
        }

        String type = tx.getTransactionType();
        if (type == null) return false;

        String typeLower = type.toLowerCase();
        return typeLower.contains("commission") ||
               typeLower.contains("agency") ||
               typeLower.contains("fee") ||
               typeLower.equals("payment_to_agency");
    }

    /**
     * CENTRALIZED CLASSIFICATION LOGIC - SINGLE SOURCE OF TRUTH
     *
     * Determines if a transaction is an EXPENSE
     */
    public boolean isExpenseTransaction(UnifiedTransaction tx) {
        if (tx.getFlowDirection() != UnifiedTransaction.FlowDirection.OUTGOING) {
            return false;
        }

        // Don't count commission as expense
        if (isCommissionTransaction(tx)) {
            return false;
        }

        String type = tx.getTransactionType();
        if (type == null) return false;

        String typeLower = type.toLowerCase();

        // Special handling for payment_to_beneficiary - these can be either:
        // 1. Owner payments (beneficiary) - NOT an expense
        // 2. Contractor/vendor payments - IS an expense
        // We distinguish by checking the description for "(beneficiary)" which indicates owner payment
        if (typeLower.equals("payment_to_beneficiary")) {
            String description = tx.getDescription();
            if (description != null && description.toLowerCase().contains("(beneficiary)")) {
                // This is a payment to the property owner, NOT an expense
                return false;
            }
            // Otherwise, it's a payment to a contractor/vendor, which IS an expense
            return true;
        }

        return typeLower.contains("expense") ||
               typeLower.contains("repair") ||
               typeLower.contains("maintenance") ||
               typeLower.contains("utility") ||
               typeLower.contains("tax") ||
               typeLower.contains("insurance");
    }

    /**
     * Get financial summary for a single property
     *
     * @param propertyId The property ID
     * @param fromDate Start date (inclusive)
     * @param toDate End date (inclusive)
     * @return PropertyFinancialSummary with all calculations
     */
    public PropertyFinancialSummary getPropertySummary(Long propertyId, LocalDate fromDate, LocalDate toDate) {
        PropertyFinancialSummary summary = new PropertyFinancialSummary();
        summary.setPropertyId(propertyId);
        summary.setPeriodStart(fromDate);
        summary.setPeriodEnd(toDate);

        // Get all transactions for this property
        List<UnifiedTransaction> allTransactions = unifiedTransactionRepository.findByPropertyId(propertyId);

        // Filter by date range
        List<UnifiedTransaction> periodTransactions = allTransactions.stream()
            .filter(tx -> !tx.getTransactionDate().isBefore(fromDate) &&
                         !tx.getTransactionDate().isAfter(toDate))
            .sorted((a, b) -> b.getTransactionDate().compareTo(a.getTransactionDate()))
            .collect(Collectors.toList());

        summary.setAllTransactions(periodTransactions);
        summary.setTotalTransactionCount(periodTransactions.size());

        // Classify and aggregate transactions
        List<UnifiedTransaction> rentTxs = new ArrayList<>();
        List<UnifiedTransaction> expenseTxs = new ArrayList<>();
        List<UnifiedTransaction> commissionTxs = new ArrayList<>();

        BigDecimal totalRent = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;

        for (UnifiedTransaction tx : periodTransactions) {
            if (isRentTransaction(tx)) {
                rentTxs.add(tx);
                totalRent = totalRent.add(tx.getAmount());
            } else if (isCommissionTransaction(tx)) {
                commissionTxs.add(tx);
                totalCommission = totalCommission.add(tx.getAmount());
            } else if (isExpenseTransaction(tx)) {
                expenseTxs.add(tx);
                totalExpenses = totalExpenses.add(tx.getAmount());
            }
        }

        summary.setRentTransactions(rentTxs);
        summary.setExpenseTransactions(expenseTxs);
        summary.setCommissionTransactions(commissionTxs);

        summary.setRentTransactionCount(rentTxs.size());
        summary.setExpenseTransactionCount(expenseTxs.size());
        summary.setCommissionTransactionCount(commissionTxs.size());

        summary.setTotalRent(totalRent);
        summary.setTotalExpenses(totalExpenses);
        summary.setTotalCommission(totalCommission);
        summary.setNetToOwner(totalRent.subtract(totalExpenses).subtract(totalCommission));

        log.info("Property {} financial summary ({} to {}): Rent=£{}, Expenses=£{}, Commission=£{}, Net=£{}",
                propertyId, fromDate, toDate, totalRent, totalExpenses, totalCommission, summary.getNetToOwner());

        return summary;
    }

    /**
     * Get financial summary for last 12 months
     */
    public PropertyFinancialSummary getPropertySummaryLast12Months(Long propertyId) {
        LocalDate oneYearAgo = LocalDate.now().minusYears(1);
        LocalDate today = LocalDate.now();
        return getPropertySummary(propertyId, oneYearAgo, today);
    }

    /**
     * Get financial summaries for multiple properties
     */
    public Map<Long, PropertyFinancialSummary> getMultiplePropertySummaries(
            List<Long> propertyIds, LocalDate fromDate, LocalDate toDate) {

        Map<Long, PropertyFinancialSummary> summaries = new HashMap<>();

        for (Long propertyId : propertyIds) {
            summaries.put(propertyId, getPropertySummary(propertyId, fromDate, toDate));
        }

        return summaries;
    }
}

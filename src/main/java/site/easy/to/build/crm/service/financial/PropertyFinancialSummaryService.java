package site.easy.to.build.crm.service.financial;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.UnifiedTransaction;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.repository.UnifiedTransactionRepository;
import site.easy.to.build.crm.repository.PropertyRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    @Autowired
    private PropertyRepository propertyRepository;

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
     *
     * For BLOCK PROPERTIES: Income represents communal service charge funds,
     * not traditional rent. These are still classified as "income" but will
     * have 0% commission applied (see getPropertySummary).
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
               typeLower.contains("payment") ||
               typeLower.contains("incoming");
    }

    /**
     * Check if a transaction is related to a block property.
     * Block property income is communal service charge funds, not rent.
     */
    public boolean isBlockPropertyTransaction(UnifiedTransaction tx) {
        // Check if the transaction has block property indicators
        String category = tx.getCategory();
        String description = tx.getDescription();

        // Category-based detection
        if (category != null) {
            String catLower = category.toLowerCase();
            if (catLower.contains("block") ||
                catLower.contains("service_charge") ||
                catLower.contains("communal") ||
                catLower.equals("property_account_allocation")) {
                return true;
            }
        }

        // Description-based detection
        if (description != null) {
            String descLower = description.toLowerCase();
            if (descLower.contains("block property") ||
                descLower.contains("service charge") ||
                descLower.contains("communal")) {
                return true;
            }
        }

        return false;
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

        // IMPORTANT: payment_to_agency is the disbursement of the commission, not the commission itself
        // commission_payment records the commission charge
        // payment_to_agency records the payment of that same commission to the agency
        // To avoid double-counting, we only count commission_payment, NOT payment_to_agency
        // However, we still classify payment_to_agency as "commission-related" so it's not counted as expense

        // Count only commission_payment and other fee-related transactions
        if (typeLower.equals("payment_to_agency")) {
            // This is a disbursement, not a charge - don't count it in commission total
            return false;
        }

        return typeLower.contains("commission") ||
               typeLower.contains("agency_fee") ||  // agency_fee (not payment_to_agency)
               typeLower.contains("management_fee");
    }

    /**
     * CENTRALIZED CLASSIFICATION LOGIC - SINGLE SOURCE OF TRUTH
     *
     * Determines if a transaction is an EXPENSE
     *
     * Business rule: If a payment is outgoing AND not commission AND not owner category = EXPENSE
     * - payment_to_beneficiary where category != 'Owner' and != 'owner_payment' = EXPENSE
     * - payment_to_agency where category != 'Commission' = EXPENSE (e.g., Council, Contractor payments)
     * - Disbursement category = EXPENSE (block property contributions)
     * - expense/maintenance/payment_to_contractor types = EXPENSE
     */
    public boolean isExpenseTransaction(UnifiedTransaction tx) {
        if (tx.getFlowDirection() != null && tx.getFlowDirection() != UnifiedTransaction.FlowDirection.OUTGOING) {
            return false;
        }

        // Don't count commission as expense
        if (isCommissionTransaction(tx)) {
            return false;
        }

        String type = tx.getTransactionType();
        if (type == null) return false;

        String typeLower = type.toLowerCase();
        String category = tx.getCategory();

        // payment_to_agency: expense if NOT commission category
        if (typeLower.equals("payment_to_agency")) {
            // Commission payments are NOT expenses (tracked separately)
            if (category != null && category.equalsIgnoreCase("Commission")) {
                return false;
            }
            // Everything else (Council, Contractor, utilities, Other, etc.) IS an expense
            return true;
        }

        // payment_to_beneficiary: expense if NOT owner category
        if (typeLower.equals("payment_to_beneficiary")) {
            // Owner payments are NOT expenses
            if (category != null && (category.equalsIgnoreCase("Owner") || category.equalsIgnoreCase("owner_payment"))) {
                return false;
            }
            // Fallback check using description for legacy data
            String description = tx.getDescription();
            if (description != null && description.toLowerCase().contains("(beneficiary)") &&
                (category == null || category.equalsIgnoreCase("Owner"))) {
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

        // Fetch the property to get commission percentage
        Property property = propertyRepository.findById(propertyId)
            .orElseThrow(() -> new RuntimeException("Property not found: " + propertyId));

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

        for (UnifiedTransaction tx : periodTransactions) {
            if (isRentTransaction(tx)) {
                rentTxs.add(tx);
                totalRent = totalRent.add(tx.getAmount());
            } else if (isCommissionTransaction(tx)) {
                // Track commission transactions for reference, but don't sum them
                commissionTxs.add(tx);
            } else if (isExpenseTransaction(tx)) {
                expenseTxs.add(tx);
                totalExpenses = totalExpenses.add(tx.getAmount());
            }
        }

        // CALCULATE commission from percentage, not from transactions
        // BLOCK PROPERTY: No commission for block properties (they're communal funds, not rent)
        // This aligns with Option C Excel statement generator behavior
        boolean isBlockProperty = Boolean.TRUE.equals(property.getIsBlockProperty()) ||
                                  "BLOCK".equalsIgnoreCase(property.getPropertyType());

        BigDecimal commissionPercentage;
        if (isBlockProperty) {
            // Block properties ALWAYS have 0% commission regardless of stored value
            commissionPercentage = BigDecimal.ZERO;
            log.debug("Block property {} - forcing 0% commission (communal funds, not rent)", propertyId);
        } else {
            commissionPercentage = property.getCommissionPercentage() != null
                ? property.getCommissionPercentage()
                : BigDecimal.valueOf(15.0); // Default to 15% if not set
        }

        BigDecimal totalCommission = totalRent
            .multiply(commissionPercentage)
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

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

        log.info("Property {} financial summary ({} to {}): Rent=£{}, Expenses=£{}, Commission=£{} ({}% of rent), Net=£{}",
                propertyId, fromDate, toDate, totalRent, totalExpenses, totalCommission, commissionPercentage, summary.getNetToOwner());

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

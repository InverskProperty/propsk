package site.easy.to.build.crm.service.financial;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.dto.StatementTransactionDto;
import site.easy.to.build.crm.entity.HistoricalTransaction;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.UnifiedTransaction;
import site.easy.to.build.crm.repository.HistoricalTransactionRepository;
import site.easy.to.build.crm.service.statements.PayPropTransactionService;
import site.easy.to.build.crm.service.statements.StatementTransactionConverter;
import site.easy.to.build.crm.service.invoice.RentCalculationService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Unified Financial Data Service
 *
 * Combines Historical Transactions and PayProp data into a single unified view
 * for dashboard financial tables and reports.
 */
@Service
public class UnifiedFinancialDataService {

    private static final Logger log = LoggerFactory.getLogger(UnifiedFinancialDataService.class);

    @Autowired
    private HistoricalTransactionRepository historicalTransactionRepository;

    @Autowired
    private PayPropTransactionService payPropTransactionService;

    @Autowired
    private StatementTransactionConverter transactionConverter;

    @Autowired
    private RentCalculationService rentCalculationService;

    @Autowired
    private site.easy.to.build.crm.repository.UnifiedTransactionRepository unifiedTransactionRepository;

    /**
     * Get combined financial transactions for a property (last 2 years)
     *
     * @param property The property
     * @return List of unified transactions from both sources
     */
    public List<StatementTransactionDto> getPropertyTransactionsLast2Years(Property property) {
        LocalDate twoYearsAgo = LocalDate.now().minusYears(2);
        LocalDate today = LocalDate.now();
        return getPropertyTransactions(property, twoYearsAgo, today);
    }

    /**
     * Get combined financial transactions for a property (custom date range)
     *
     * ✅ NOW USES unified_transactions TABLE - includes both HISTORICAL and PAYPROP data
     *
     * @param property The property
     * @param fromDate Start date
     * @param toDate End date
     * @return List of unified transactions from unified_transactions table
     */
    public List<StatementTransactionDto> getPropertyTransactions(Property property, LocalDate fromDate, LocalDate toDate) {
        List<StatementTransactionDto> combined = new ArrayList<>();

        try {
            // Query unified_transactions table (includes both HISTORICAL and PAYPROP sources)
            List<UnifiedTransaction> unifiedTransactions =
                unifiedTransactionRepository.findByPropertyId(property.getId());

            // Filter by date range
            List<UnifiedTransaction> filteredTransactions = unifiedTransactions.stream()
                .filter(tx -> !tx.getTransactionDate().isBefore(fromDate) && !tx.getTransactionDate().isAfter(toDate))
                .collect(Collectors.toList());

            // Convert to DTOs
            combined = transactionConverter.convertUnifiedListToDto(filteredTransactions);

            // Sort by date (newest first)
            combined.sort(Comparator.comparing(StatementTransactionDto::getTransactionDate).reversed());

            log.info("✅ Retrieved {} total transactions from unified_transactions for property {} ({} to {})",
                combined.size(), property.getPropertyName(), fromDate, toDate);

            // Log breakdown by source
            long historicalCount = combined.stream().filter(StatementTransactionDto::isHistoricalTransaction).count();
            long paypropCount = combined.stream().filter(StatementTransactionDto::isPayPropTransaction).count();
            log.debug("   - {} HISTORICAL transactions", historicalCount);
            log.debug("   - {} PAYPROP transactions", paypropCount);

        } catch (Exception e) {
            log.error("Error getting unified transactions for property {}: {}",
                property.getPropertyName(), e.getMessage(), e);
        }

        return combined;
    }

    /**
     * Get financial summary for a property (last 2 years)
     *
     * Uses:
     * - Rent DUE: Calculated from local invoices
     * - Rent RECEIVED: Actual incoming transactions from historical + PayProp
     * - Expenses: Actual expenses from historical + PayProp
     * - Commissions: Calculated from commission percentages
     */
    public Map<String, Object> getPropertyFinancialSummary(Property property) {
        LocalDate twoYearsAgo = LocalDate.now().minusYears(2);
        LocalDate today = LocalDate.now();

        List<StatementTransactionDto> transactions = getPropertyTransactions(property, twoYearsAgo, today);

        Map<String, Object> summary = new HashMap<>();

        // RENT DUE (calculated from invoices/leases) - with error handling
        BigDecimal rentDue = BigDecimal.ZERO;
        try {
            rentDue = rentCalculationService.calculateTotalRentDue(property.getId(), twoYearsAgo, today);
        } catch (Exception e) {
            log.warn("Error calculating rent due for property {}: {}", property.getId(), e.getMessage());
            // Continue with rentDue = 0
        }

        // RENT RECEIVED (actual incoming payments)
        BigDecimal rentReceived = transactions.stream()
            .filter(StatementTransactionDto::isRentPayment)
            .map(StatementTransactionDto::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // RENT ARREARS (difference between due and received)
        BigDecimal rentArrears = rentDue.subtract(rentReceived);

        // EXPENSES (actual outgoing)
        BigDecimal totalExpenses = transactions.stream()
            .filter(StatementTransactionDto::isExpense)
            .map(tx -> tx.getAmount().abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // COMMISSIONS (calculated fees)
        BigDecimal totalCommissions = transactions.stream()
            .filter(StatementTransactionDto::isAgencyFee)
            .map(tx -> tx.getAmount().abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // NET TO OWNER
        BigDecimal netOwnerIncome = rentReceived.subtract(totalExpenses).subtract(totalCommissions);

        summary.put("rentDue", rentDue);  // Expected rent from invoices
        summary.put("rentReceived", rentReceived);  // Actual rent received
        summary.put("rentArrears", rentArrears);  // Outstanding rent
        summary.put("totalIncome", rentReceived);  // Actual rent received
        summary.put("totalExpenses", totalExpenses);
        summary.put("totalCommissions", totalCommissions);
        summary.put("netOwnerIncome", netOwnerIncome);
        summary.put("transactionCount", transactions.size());

        // Recent transactions (limit to 50 for dashboard display)
        // NO SOURCE TRANSPARENCY - Just show the transaction data
        List<Map<String, Object>> recentTransactions = transactions.stream()
            .limit(50)
            .map(this::convertToMapSimple)
            .collect(Collectors.toList());

        summary.put("recentTransactions", recentTransactions);
        summary.put("dateRange", Map.of(
            "from", twoYearsAgo.toString(),
            "to", today.toString()
        ));

        return summary;
    }

    /**
     * Convert StatementTransactionDto to Map for JSON response (SIMPLE VERSION - NO SOURCE TRANSPARENCY)
     */
    private Map<String, Object> convertToMapSimple(StatementTransactionDto tx) {
        Map<String, Object> map = new HashMap<>();
        map.put("date", tx.getTransactionDate());
        map.put("description", tx.getDescription());
        map.put("category", tx.getCategory());
        map.put("type", tx.getTransactionType());
        map.put("amount", tx.getAmount());

        // NO SOURCE TRANSPARENCY - removed source badges

        return map;
    }

    /**
     * Get transactions for multiple properties (for owner dashboard showing all their properties)
     */
    public List<StatementTransactionDto> getTransactionsForOwner(Long ownerId, LocalDate fromDate, LocalDate toDate) {
        // This would query all properties owned by the customer
        // and combine transactions from all of them
        // Implementation depends on your property-owner relationship
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * ✨ PHASE 2: Get expense breakdown by category for all customer properties
     *
     * @param customerId Customer ID
     * @param startDate Start date
     * @param endDate End date
     * @return Map of category -> total amount
     */
    public Map<String, BigDecimal> getExpensesByCategoryForCustomer(Long customerId, LocalDate startDate, LocalDate endDate) {
        Map<String, BigDecimal> categoryTotals = new LinkedHashMap<>();

        try {
            // Query all customer transactions at once using optimized query
            List<UnifiedTransaction> allTransactions =
                unifiedTransactionRepository.findByCustomerOwnedPropertiesAndDateRangeAndFlowDirection(
                    customerId,
                    startDate,
                    endDate,
                    UnifiedTransaction.FlowDirection.OUTGOING
                );

            log.info("🔍 Found {} OUTGOING transactions for customer {} in date range",
                allTransactions.size(), customerId);

            // Group expenses by category
            for (UnifiedTransaction tx : allTransactions) {
                // Skip if not an expense (e.g., could be agency fee)
                if (tx.getTransactionType() != null &&
                    (tx.getTransactionType().contains("EXPENSE") ||
                     tx.getTransactionType().contains("MAINTENANCE") ||
                     tx.getTransactionType().contains("REPAIR") ||
                     tx.getTransactionType().contains("UTILITY") ||
                     tx.getTransactionType().contains("CLEANING") ||
                     tx.getTransactionType().contains("COMPLIANCE"))) {

                    String category = tx.getCategory() != null && !tx.getCategory().isEmpty()
                        ? tx.getCategory()
                        : "Other Expenses";

                    BigDecimal amount = tx.getAmount().abs();
                    categoryTotals.merge(category, amount, BigDecimal::add);
                }
            }

            log.info("✅ Calculated expense breakdown: {} categories with total transactions analyzed: {}",
                categoryTotals.size(), allTransactions.size());

            if (!categoryTotals.isEmpty()) {
                log.info("📊 Expense categories found: {}", categoryTotals.keySet());
            }
        } catch (Exception e) {
            log.error("❌ Error calculating expense breakdown: {}", e.getMessage(), e);
        }

        return categoryTotals;
    }

    /**
     * DEPRECATED: Use getExpensesByCategoryForCustomer instead
     * Legacy method kept for compatibility
     */
    @Deprecated
    public Map<String, BigDecimal> getExpensesByCategory(List<Property> properties, LocalDate startDate, LocalDate endDate) {
        Map<String, BigDecimal> categoryTotals = new LinkedHashMap<>();

        try {
            for (Property property : properties) {
                List<StatementTransactionDto> transactions = getPropertyTransactions(property, startDate, endDate);

                // Group expenses by category
                transactions.stream()
                    .filter(StatementTransactionDto::isExpense)
                    .forEach(tx -> {
                        String category = tx.getCategory() != null ? tx.getCategory() : "Other";
                        BigDecimal amount = tx.getAmount().abs();
                        categoryTotals.merge(category, amount, BigDecimal::add);
                    });
            }

            log.info("✅ Calculated expense breakdown: {} categories", categoryTotals.size());
        } catch (Exception e) {
            log.error("Error calculating expense breakdown: {}", e.getMessage(), e);
        }

        return categoryTotals;
    }

    /**
     * ✨ PHASE 2: Get monthly financial trends for customer properties - OPTIMIZED VERSION
     *
     * @param customerId Customer ID
     * @param startDate Start date
     * @param endDate End date
     * @return List of monthly summaries
     */
    public List<Map<String, Object>> getMonthlyTrendsForCustomer(Long customerId, LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> monthlyData = new ArrayList<>();

        try {
            // Query all customer transactions at once
            List<UnifiedTransaction> allTransactions =
                unifiedTransactionRepository.findByCustomerOwnedPropertiesAndDateRange(
                    customerId,
                    startDate,
                    endDate
                );

            log.info("🔍 Found {} total transactions for customer {} in date range {} to {}",
                allTransactions.size(), customerId, startDate, endDate);

            if (allTransactions.isEmpty()) {
                log.warn("⚠️ No transactions found for customer {} - charts will be empty", customerId);
                return monthlyData;
            }

            // Group by month (YYYY-MM format)
            Map<String, List<UnifiedTransaction>> byMonth = allTransactions.stream()
                .collect(Collectors.groupingBy(tx ->
                    tx.getTransactionDate().getYear() + "-" +
                    String.format("%02d", tx.getTransactionDate().getMonthValue())
                ));

            log.info("📅 Grouped transactions into {} months", byMonth.size());

            // Calculate totals per month
            byMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String monthKey = entry.getKey();
                    List<UnifiedTransaction> monthTxs = entry.getValue();

                    // Income: INCOMING transactions (rent payments)
                    BigDecimal income = monthTxs.stream()
                        .filter(tx -> tx.getFlowDirection() == UnifiedTransaction.FlowDirection.INCOMING)
                        .map(UnifiedTransaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Expenses: OUTGOING transactions that are expenses
                    BigDecimal expenses = monthTxs.stream()
                        .filter(tx -> tx.getFlowDirection() == UnifiedTransaction.FlowDirection.OUTGOING)
                        .filter(tx -> tx.getTransactionType() != null &&
                            (tx.getTransactionType().contains("EXPENSE") ||
                             tx.getTransactionType().contains("MAINTENANCE") ||
                             tx.getTransactionType().contains("REPAIR") ||
                             tx.getTransactionType().contains("UTILITY") ||
                             tx.getTransactionType().contains("CLEANING") ||
                             tx.getTransactionType().contains("COMPLIANCE")))
                        .map(tx -> tx.getAmount().abs())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Commission: OUTGOING transactions that are agency fees
                    BigDecimal commission = monthTxs.stream()
                        .filter(tx -> tx.getFlowDirection() == UnifiedTransaction.FlowDirection.OUTGOING)
                        .filter(tx -> tx.getTransactionType() != null &&
                            (tx.getTransactionType().contains("AGENCY_FEE") ||
                             tx.getTransactionType().contains("COMMISSION") ||
                             tx.getTransactionType().contains("MANAGEMENT_FEE")))
                        .map(tx -> tx.getAmount().abs())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal netToOwner = income.subtract(expenses).subtract(commission);

                    Map<String, Object> monthSummary = new HashMap<>();
                    monthSummary.put("month", monthKey);
                    monthSummary.put("income", income);
                    monthSummary.put("expenses", expenses);
                    monthSummary.put("commission", commission);
                    monthSummary.put("netToOwner", netToOwner);
                    monthSummary.put("transactionCount", monthTxs.size());

                    monthlyData.add(monthSummary);

                    log.debug("📊 Month {}: Income=£{}, Expenses=£{}, Commission=£{}, Net=£{}",
                        monthKey, income, expenses, commission, netToOwner);
                });

            log.info("✅ Calculated monthly trends: {} months with data", monthlyData.size());
        } catch (Exception e) {
            log.error("❌ Error calculating monthly trends: {}", e.getMessage(), e);
            e.printStackTrace();
        }

        return monthlyData;
    }

    /**
     * DEPRECATED: Use getMonthlyTrendsForCustomer instead
     * Legacy method kept for compatibility
     */
    @Deprecated
    public List<Map<String, Object>> getMonthlyTrends(List<Property> properties, LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> monthlyData = new ArrayList<>();

        try {
            // Collect all transactions for all properties
            List<StatementTransactionDto> allTransactions = new ArrayList<>();
            for (Property property : properties) {
                allTransactions.addAll(getPropertyTransactions(property, startDate, endDate));
            }

            // Group by month
            Map<String, List<StatementTransactionDto>> byMonth = allTransactions.stream()
                .collect(Collectors.groupingBy(tx ->
                    tx.getTransactionDate().getYear() + "-" +
                    String.format("%02d", tx.getTransactionDate().getMonthValue())
                ));

            // Calculate totals per month
            byMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String monthKey = entry.getKey();
                    List<StatementTransactionDto> monthTxs = entry.getValue();

                    BigDecimal income = monthTxs.stream()
                        .filter(StatementTransactionDto::isRentPayment)
                        .map(StatementTransactionDto::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal expenses = monthTxs.stream()
                        .filter(StatementTransactionDto::isExpense)
                        .map(tx -> tx.getAmount().abs())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal commission = monthTxs.stream()
                        .filter(StatementTransactionDto::isAgencyFee)
                        .map(tx -> tx.getAmount().abs())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal netToOwner = income.subtract(expenses).subtract(commission);

                    Map<String, Object> monthSummary = new HashMap<>();
                    monthSummary.put("month", monthKey);
                    monthSummary.put("income", income);
                    monthSummary.put("expenses", expenses);
                    monthSummary.put("commission", commission);
                    monthSummary.put("netToOwner", netToOwner);
                    monthSummary.put("transactionCount", monthTxs.size());

                    monthlyData.add(monthSummary);
                });

            log.info("✅ Calculated monthly trends: {} months", monthlyData.size());
        } catch (Exception e) {
            log.error("Error calculating monthly trends: {}", e.getMessage(), e);
        }

        return monthlyData;
    }
}

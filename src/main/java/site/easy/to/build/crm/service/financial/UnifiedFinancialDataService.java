package site.easy.to.build.crm.service.financial;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.dto.StatementTransactionDto;
import site.easy.to.build.crm.entity.HistoricalTransaction;
import site.easy.to.build.crm.entity.Property;
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
     * NO DEDUPLICATION - Shows all transactions from both sources
     *
     * @param property The property
     * @param fromDate Start date
     * @param toDate End date
     * @return List of unified transactions from both sources
     */
    public List<StatementTransactionDto> getPropertyTransactions(Property property, LocalDate fromDate, LocalDate toDate) {
        List<StatementTransactionDto> combined = new ArrayList<>();

        try {
            // 1. Get Historical Transactions (actual payments/expenses that happened)
            if (property.getPayPropId() != null) {
                List<HistoricalTransaction> historical = historicalTransactionRepository
                    .findPropertyTransactionsForStatement(property.getPayPropId(), fromDate, toDate);

                List<StatementTransactionDto> historicalDtos = transactionConverter.convertHistoricalListToDto(historical);
                combined.addAll(historicalDtos);

                log.debug("Loaded {} historical transactions for property {}", historicalDtos.size(), property.getPropertyName());
            }

            // 2. Get PayProp Transactions (current actual payments from PayProp)
            if (property.getPayPropId() != null && !property.getPayPropId().trim().isEmpty()) {
                List<StatementTransactionDto> paypropTxs = payPropTransactionService
                    .getPayPropTransactionsForProperty(property.getPayPropId(), fromDate, toDate);

                // NO DEDUPLICATION - Add all PayProp transactions
                combined.addAll(paypropTxs);

                log.debug("Loaded {} PayProp transactions for property {}", paypropTxs.size(), property.getPropertyName());
            }

            // 3. Sort by date (newest first)
            combined.sort(Comparator.comparing(StatementTransactionDto::getTransactionDate).reversed());

            log.info("✅ Retrieved {} total transactions for property {} ({} to {})",
                combined.size(), property.getPropertyName(), fromDate, toDate);

        } catch (Exception e) {
            log.error("Error getting combined transactions for property {}: {}",
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
}

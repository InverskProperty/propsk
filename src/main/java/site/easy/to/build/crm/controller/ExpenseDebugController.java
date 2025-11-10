package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.UnifiedTransaction;
import site.easy.to.build.crm.repository.UnifiedTransactionRepository;
import site.easy.to.build.crm.service.financial.PropertyFinancialSummaryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/debug")
public class ExpenseDebugController {

    @Autowired
    private UnifiedTransactionRepository unifiedTransactionRepository;

    @Autowired
    private PropertyFinancialSummaryService propertyFinancialSummaryService;

    @Autowired
    private EntityManager entityManager;

    /**
     * Get detailed expense breakdown for a property
     * Example: GET /api/debug/expenses/1
     */
    @GetMapping("/expenses/{propertyId}")
    public Map<String, Object> getPropertyExpenses(@PathVariable Long propertyId) {

        LocalDate oneYearAgo = LocalDate.now().minusYears(1);
        LocalDate today = LocalDate.now();

        // Get all transactions for property
        List<UnifiedTransaction> allTransactions = unifiedTransactionRepository.findByPropertyId(propertyId);

        // Filter to last 12 months
        List<UnifiedTransaction> periodTransactions = allTransactions.stream()
            .filter(tx -> !tx.getTransactionDate().isBefore(oneYearAgo) &&
                         !tx.getTransactionDate().isAfter(today))
            .collect(Collectors.toList());

        // Filter to expenses only
        List<UnifiedTransaction> expenses = periodTransactions.stream()
            .filter(propertyFinancialSummaryService::isExpenseTransaction)
            .sorted((a, b) -> b.getTransactionDate().compareTo(a.getTransactionDate()))
            .collect(Collectors.toList());

        // Calculate totals
        BigDecimal totalExpenses = expenses.stream()
            .map(UnifiedTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Group by transaction type
        Map<String, List<UnifiedTransaction>> byType = expenses.stream()
            .collect(Collectors.groupingBy(
                tx -> tx.getTransactionType() != null ? tx.getTransactionType() : "UNKNOWN"
            ));

        Map<String, Object> typeBreakdown = new HashMap<>();
        for (Map.Entry<String, List<UnifiedTransaction>> entry : byType.entrySet()) {
            BigDecimal typeTotal = entry.getValue().stream()
                .map(UnifiedTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            typeBreakdown.put(entry.getKey(), Map.of(
                "count", entry.getValue().size(),
                "total", typeTotal,
                "transactions", entry.getValue().stream()
                    .map(tx -> Map.of(
                        "date", tx.getTransactionDate().toString(),
                        "amount", tx.getAmount(),
                        "description", tx.getDescription() != null ? tx.getDescription() : "N/A",
                        "category", tx.getCategory() != null ? tx.getCategory() : "N/A"
                    ))
                    .collect(Collectors.toList())
            ));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("propertyId", propertyId);
        result.put("periodStart", oneYearAgo.toString());
        result.put("periodEnd", today.toString());
        result.put("totalExpenses", totalExpenses);
        result.put("expenseCount", expenses.size());
        result.put("breakdownByType", typeBreakdown);
        result.put("allExpenses", expenses.stream()
            .map(tx -> Map.of(
                "date", tx.getTransactionDate().toString(),
                "type", tx.getTransactionType() != null ? tx.getTransactionType() : "UNKNOWN",
                "category", tx.getCategory() != null ? tx.getCategory() : "N/A",
                "amount", tx.getAmount(),
                "description", tx.getDescription() != null ? tx.getDescription() : "N/A",
                "source", tx.getSourceSystem() != null ? tx.getSourceSystem().toString() : "N/A"
            ))
            .collect(Collectors.toList())
        );

        return result;
    }

    /**
     * Get beneficiary type information from financial_transactions source table
     * Example: GET /api/debug/beneficiary-types/1
     */
    @GetMapping("/beneficiary-types/{propertyId}")
    public Map<String, Object> getBeneficiaryTypes(@PathVariable Long propertyId) {

        // Query financial_transactions directly with native SQL
        String sql = """
            SELECT
                id,
                transaction_date,
                transaction_type,
                amount,
                payprop_beneficiary_type,
                description,
                flow_direction
            FROM financial_transactions
            WHERE property_id = :propertyId
            AND transaction_date >= DATE_SUB(NOW(), INTERVAL 12 MONTH)
            AND flow_direction = 'OUTGOING'
            ORDER BY transaction_date DESC
        """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("propertyId", propertyId.toString());

        List<Object[]> results = query.getResultList();

        List<Map<String, Object>> transactions = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        Map<String, Integer> beneficiaryTypeCounts = new HashMap<>();
        Map<String, BigDecimal> beneficiaryTypeTotals = new HashMap<>();
        Map<String, Integer> transactionTypeCounts = new HashMap<>();
        Map<String, BigDecimal> transactionTypeTotals = new HashMap<>();

        for (Object[] row : results) {
            Long id = ((Number) row[0]).longValue();
            LocalDate date = row[1] != null ? ((java.sql.Date) row[1]).toLocalDate() : null;
            String txType = (String) row[2];
            BigDecimal amount = row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
            String beneficiaryType = (String) row[4];
            String description = (String) row[5];
            String flowDirection = (String) row[6];

            Map<String, Object> txMap = new HashMap<>();
            txMap.put("id", id);
            txMap.put("date", date != null ? date.toString() : "N/A");
            txMap.put("transactionType", txType != null ? txType : "N/A");
            txMap.put("amount", amount);
            txMap.put("beneficiaryType", beneficiaryType != null ? beneficiaryType : "NULL");
            txMap.put("description", description != null ? description : "N/A");
            txMap.put("flowDirection", flowDirection);

            transactions.add(txMap);
            total = total.add(amount);

            // Count by beneficiary type
            String benefKey = beneficiaryType != null ? beneficiaryType : "NULL";
            beneficiaryTypeCounts.put(benefKey, beneficiaryTypeCounts.getOrDefault(benefKey, 0) + 1);
            beneficiaryTypeTotals.put(benefKey, beneficiaryTypeTotals.getOrDefault(benefKey, BigDecimal.ZERO).add(amount));

            // Count by transaction type
            String txKey = txType != null ? txType : "NULL";
            transactionTypeCounts.put(txKey, transactionTypeCounts.getOrDefault(txKey, 0) + 1);
            transactionTypeTotals.put(txKey, transactionTypeTotals.getOrDefault(txKey, BigDecimal.ZERO).add(amount));
        }

        // Build summary by beneficiary type
        List<Map<String, Object>> beneficiarySummary = new ArrayList<>();
        for (String benefType : beneficiaryTypeCounts.keySet()) {
            Map<String, Object> summary = new HashMap<>();
            summary.put("beneficiaryType", benefType);
            summary.put("count", beneficiaryTypeCounts.get(benefType));
            summary.put("total", beneficiaryTypeTotals.get(benefType));
            beneficiarySummary.add(summary);
        }
        beneficiarySummary.sort((a, b) -> ((BigDecimal) b.get("total")).compareTo((BigDecimal) a.get("total")));

        // Build summary by transaction type
        List<Map<String, Object>> transactionSummary = new ArrayList<>();
        for (String txType : transactionTypeCounts.keySet()) {
            Map<String, Object> summary = new HashMap<>();
            summary.put("transactionType", txType);
            summary.put("count", transactionTypeCounts.get(txType));
            summary.put("total", transactionTypeTotals.get(txType));
            transactionSummary.add(summary);
        }
        transactionSummary.sort((a, b) -> ((BigDecimal) b.get("total")).compareTo((BigDecimal) a.get("total")));

        // Filter to payment_to_beneficiary only
        List<Map<String, Object>> paymentToBeneficiaryTxs = transactions.stream()
            .filter(tx -> "payment_to_beneficiary".equals(tx.get("transactionType")))
            .collect(Collectors.toList());

        BigDecimal paymentToBeneficiaryTotal = paymentToBeneficiaryTxs.stream()
            .map(tx -> (BigDecimal) tx.get("amount"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new HashMap<>();
        result.put("propertyId", propertyId);
        result.put("totalOutgoing", total);
        result.put("transactionCount", transactions.size());
        result.put("allTransactions", transactions);
        result.put("summaryByBeneficiaryType", beneficiarySummary);
        result.put("summaryByTransactionType", transactionSummary);
        result.put("paymentToBeneficiaryTransactions", paymentToBeneficiaryTxs);
        result.put("paymentToBeneficiaryTotal", paymentToBeneficiaryTotal);
        result.put("paymentToBeneficiaryCount", paymentToBeneficiaryTxs.size());

        return result;
    }
}

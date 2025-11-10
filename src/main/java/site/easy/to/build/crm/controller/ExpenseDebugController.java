package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.UnifiedTransaction;
import site.easy.to.build.crm.repository.UnifiedTransactionRepository;
import site.easy.to.build.crm.service.financial.PropertyFinancialSummaryService;

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
                        "category", tx.getCategoryName() != null ? tx.getCategoryName() : "N/A",
                        "beneficiary", tx.getBeneficiaryType() != null ? tx.getBeneficiaryType() : "N/A"
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
                "category", tx.getCategoryName() != null ? tx.getCategoryName() : "N/A",
                "beneficiary", tx.getBeneficiaryType() != null ? tx.getBeneficiaryType() : "N/A",
                "amount", tx.getAmount(),
                "description", tx.getDescription() != null ? tx.getDescription() : "N/A",
                "source", tx.getSourceSystem() != null ? tx.getSourceSystem() : "N/A"
            ))
            .collect(Collectors.toList())
        );

        return result;
    }
}

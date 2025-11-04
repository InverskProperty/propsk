package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import site.easy.to.build.crm.entity.UnifiedTransaction;
import site.easy.to.build.crm.repository.UnifiedTransactionRepository;
import site.easy.to.build.crm.dto.StatementTransactionDto;
import site.easy.to.build.crm.service.statements.StatementTransactionConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class TestCategorizationController {

    @Autowired
    private UnifiedTransactionRepository unifiedTransactionRepository;

    @Autowired
    private StatementTransactionConverter transactionConverter;

    @GetMapping("/api/test/categorization")
    public Map<String, Object> testCategorization() {
        LocalDate oneYearAgo = LocalDate.now().minusYears(1);
        LocalDate today = LocalDate.now();

        // Get all transactions from last 12 months
        List<UnifiedTransaction> allTransactions = unifiedTransactionRepository
            .findByTransactionDateBetween(oneYearAgo, today);

        // Convert to DTOs
        List<StatementTransactionDto> dtos = transactionConverter.convertUnifiedListToDto(allTransactions);

        // Apply categorization rules
        BigDecimal totalRentIncome = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        BigDecimal totalCommissions = BigDecimal.ZERO;
        BigDecimal totalOwnerPayments = BigDecimal.ZERO;

        Map<String, Integer> rentCount = new HashMap<>();
        Map<String, Integer> expenseCount = new HashMap<>();
        Map<String, Integer> commissionCount = new HashMap<>();
        Map<String, Integer> ownerPaymentCount = new HashMap<>();

        for (StatementTransactionDto dto : dtos) {
            String key = dto.getTransactionType() + " | " +
                        (dto.getCategory() != null ? dto.getCategory() : "NULL") + " | " +
                        (dto.getBeneficiaryType() != null ? dto.getBeneficiaryType() : "NULL");

            // Rent Income
            if (dto.isRentPayment()) {
                totalRentIncome = totalRentIncome.add(dto.getAmount());
                rentCount.put(key, rentCount.getOrDefault(key, 0) + 1);
            }

            // Expenses
            if (dto.isExpense()) {
                totalExpenses = totalExpenses.add(dto.getAmount().abs());
                expenseCount.put(key, expenseCount.getOrDefault(key, 0) + 1);
            }

            // Commissions
            if (dto.isAgencyFee()) {
                totalCommissions = totalCommissions.add(dto.getAmount().abs());
                commissionCount.put(key, commissionCount.getOrDefault(key, 0) + 1);
            }

            // Owner Payments
            if (dto.isOwnerPayment()) {
                totalOwnerPayments = totalOwnerPayments.add(dto.getAmount().abs());
                ownerPaymentCount.put(key, ownerPaymentCount.getOrDefault(key, 0) + 1);
            }
        }

        BigDecimal netToOwner = totalRentIncome.subtract(totalExpenses).subtract(totalCommissions);

        Map<String, Object> result = new HashMap<>();
        result.put("dateRange", Map.of("from", oneYearAgo, "to", today));
        result.put("totalTransactions", dtos.size());

        result.put("rentIncome", totalRentIncome);
        result.put("expenses", totalExpenses);
        result.put("commissions", totalCommissions);
        result.put("ownerPayments", totalOwnerPayments);
        result.put("netToOwner", netToOwner);

        result.put("rentTransactionTypes", rentCount);
        result.put("expenseTransactionTypes", expenseCount);
        result.put("commissionTransactionTypes", commissionCount);
        result.put("ownerPaymentTransactionTypes", ownerPaymentCount);

        result.put("formula", String.format("Net to Owner = £%,.2f - £%,.2f - £%,.2f = £%,.2f",
            totalRentIncome, totalExpenses, totalCommissions, netToOwner));

        return result;
    }
}

package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import site.easy.to.build.crm.entity.UnifiedTransaction;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.repository.UnifiedTransactionRepository;
import site.easy.to.build.crm.repository.InvoiceRepository;
import site.easy.to.build.crm.dto.StatementTransactionDto;
import site.easy.to.build.crm.service.statements.StatementTransactionConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class TestCategorizationController {

    @Autowired
    private UnifiedTransactionRepository unifiedTransactionRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

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

    @GetMapping("/api/test/arrears-analysis")
    public Map<String, Object> analyzeArrears() {
        LocalDate oneYearAgo = LocalDate.now().minusYears(1);
        LocalDate today = LocalDate.now();

        Map<String, Object> result = new HashMap<>();
        result.put("dateRange", Map.of("from", oneYearAgo, "to", today));

        // STEP 1: Get RENT RECEIVED from unified_transactions
        List<UnifiedTransaction> allTransactions = unifiedTransactionRepository
            .findByTransactionDateBetween(oneYearAgo, today);

        List<StatementTransactionDto> dtos = transactionConverter.convertUnifiedListToDto(allTransactions);

        BigDecimal rentReceived = dtos.stream()
            .filter(StatementTransactionDto::isRentPayment)
            .map(StatementTransactionDto::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        long rentTxCount = dtos.stream().filter(StatementTransactionDto::isRentPayment).count();

        // Breakdown by transaction type
        Map<String, Long> rentBreakdown = dtos.stream()
            .filter(StatementTransactionDto::isRentPayment)
            .collect(Collectors.groupingBy(
                StatementTransactionDto::getTransactionType,
                Collectors.counting()
            ));

        result.put("rentReceived", rentReceived);
        result.put("rentTransactionCount", rentTxCount);
        result.put("rentBreakdownByType", rentBreakdown);

        // STEP 2: Get RENT DUE from invoices
        List<Invoice> activeInvoices = invoiceRepository.findAll().stream()
            .filter(inv -> inv.getStartDate() != null)
            .filter(inv -> !inv.getStartDate().isAfter(today))
            .filter(inv -> inv.getEndDate() == null || !inv.getEndDate().isBefore(oneYearAgo))
            .collect(Collectors.toList());

        int invoiceCount = activeInvoices.size();

        BigDecimal totalMonthlyRent = activeInvoices.stream()
            .map(Invoice::getAmount)
            .filter(amt -> amt != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Estimate annual rent (monthly * 12)
        BigDecimal estimatedRentDue = totalMonthlyRent.multiply(BigDecimal.valueOf(12));

        result.put("activeInvoiceCount", invoiceCount);
        result.put("totalMonthlyRent", totalMonthlyRent);
        result.put("estimatedAnnualRentDue", estimatedRentDue);

        // STEP 3: Calculate ARREARS
        BigDecimal arrears = estimatedRentDue.subtract(rentReceived);
        result.put("arrears", arrears);
        result.put("arrearsStatus", arrears.compareTo(BigDecimal.ZERO) < 0 ? "OVERPAID" :
                                    arrears.compareTo(BigDecimal.ZERO) > 0 ? "OWED" : "PAID_UP");

        // STEP 4: Check for issues
        Map<String, Object> issues = new HashMap<>();

        // Check for properties with transactions but no invoices
        List<Long> propertiesWithTransactions = allTransactions.stream()
            .map(UnifiedTransaction::getPropertyId)
            .filter(id -> id != null)
            .distinct()
            .collect(Collectors.toList());

        List<Long> propertiesWithInvoices = activeInvoices.stream()
            .map(inv -> inv.getProperty() != null ? inv.getProperty().getId() : null)
            .filter(id -> id != null)
            .distinct()
            .collect(Collectors.toList());

        List<Long> orphanProperties = propertiesWithTransactions.stream()
            .filter(id -> !propertiesWithInvoices.contains(id))
            .collect(Collectors.toList());

        issues.put("propertiesWithTransactionsButNoInvoices", orphanProperties.size());
        issues.put("orphanPropertyIds", orphanProperties);

        // Check for potential deposits
        long potentialDeposits = allTransactions.stream()
            .filter(tx -> tx.getDescription() != null &&
                         tx.getDescription().toLowerCase().contains("deposit"))
            .count();
        issues.put("potentialDepositTransactions", potentialDeposits);

        result.put("issues", issues);

        result.put("summary", String.format(
            "Rent Due: £%,.2f | Rent Received: £%,.2f | Arrears: £%,.2f (%s)",
            estimatedRentDue, rentReceived, arrears,
            arrears.compareTo(BigDecimal.ZERO) < 0 ? "OVERPAID" : "OWED"
        ));

        return result;
    }
}

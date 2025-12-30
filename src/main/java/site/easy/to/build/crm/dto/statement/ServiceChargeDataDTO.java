package site.easy.to.build.crm.dto.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for Service Charge Account data in statement generation.
 *
 * Service charge accounts are for block properties and show:
 * - Income from service charge payments (e.g., from Prestvale Properties)
 * - Expenses paid from the block account
 * - A running BALANCE (not net-to-owner like regular properties)
 *
 * This bypasses the allocation system - transactions are shown directly.
 */
public class ServiceChargeDataDTO {

    private String blockPropertyName;
    private Long blockPropertyLeaseId;
    private String tenantName;  // e.g., "Prestvale Properties Limited"

    // Balance information
    private BigDecimal openingBalance = BigDecimal.ZERO;
    private BigDecimal totalIncome = BigDecimal.ZERO;
    private BigDecimal totalExpenses = BigDecimal.ZERO;
    private BigDecimal closingBalance = BigDecimal.ZERO;  // opening + income - expenses

    // Transaction details
    private List<ServiceChargeTransactionDTO> incomeTransactions = new ArrayList<>();
    private List<ServiceChargeTransactionDTO> expenseTransactions = new ArrayList<>();

    public ServiceChargeDataDTO() {
    }

    public ServiceChargeDataDTO(String blockPropertyName, Long blockPropertyLeaseId) {
        this.blockPropertyName = blockPropertyName;
        this.blockPropertyLeaseId = blockPropertyLeaseId;
    }

    /**
     * Calculate the closing balance based on opening, income, and expenses
     */
    public void calculateClosingBalance() {
        this.closingBalance = openingBalance.add(totalIncome).subtract(totalExpenses);
    }

    /**
     * Add an income transaction and update totals
     */
    public void addIncomeTransaction(ServiceChargeTransactionDTO transaction) {
        incomeTransactions.add(transaction);
        if (transaction.getAmount() != null) {
            totalIncome = totalIncome.add(transaction.getAmount().abs());
        }
    }

    /**
     * Add an expense transaction and update totals
     */
    public void addExpenseTransaction(ServiceChargeTransactionDTO transaction) {
        expenseTransactions.add(transaction);
        if (transaction.getAmount() != null) {
            totalExpenses = totalExpenses.add(transaction.getAmount().abs());
        }
    }

    // Getters and Setters
    public String getBlockPropertyName() {
        return blockPropertyName;
    }

    public void setBlockPropertyName(String blockPropertyName) {
        this.blockPropertyName = blockPropertyName;
    }

    public Long getBlockPropertyLeaseId() {
        return blockPropertyLeaseId;
    }

    public void setBlockPropertyLeaseId(Long blockPropertyLeaseId) {
        this.blockPropertyLeaseId = blockPropertyLeaseId;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public void setOpeningBalance(BigDecimal openingBalance) {
        this.openingBalance = openingBalance != null ? openingBalance : BigDecimal.ZERO;
    }

    public BigDecimal getTotalIncome() {
        return totalIncome;
    }

    public void setTotalIncome(BigDecimal totalIncome) {
        this.totalIncome = totalIncome != null ? totalIncome : BigDecimal.ZERO;
    }

    public BigDecimal getTotalExpenses() {
        return totalExpenses;
    }

    public void setTotalExpenses(BigDecimal totalExpenses) {
        this.totalExpenses = totalExpenses != null ? totalExpenses : BigDecimal.ZERO;
    }

    public BigDecimal getClosingBalance() {
        return closingBalance;
    }

    public void setClosingBalance(BigDecimal closingBalance) {
        this.closingBalance = closingBalance != null ? closingBalance : BigDecimal.ZERO;
    }

    public List<ServiceChargeTransactionDTO> getIncomeTransactions() {
        return incomeTransactions;
    }

    public void setIncomeTransactions(List<ServiceChargeTransactionDTO> incomeTransactions) {
        this.incomeTransactions = incomeTransactions != null ? incomeTransactions : new ArrayList<>();
    }

    public List<ServiceChargeTransactionDTO> getExpenseTransactions() {
        return expenseTransactions;
    }

    public void setExpenseTransactions(List<ServiceChargeTransactionDTO> expenseTransactions) {
        this.expenseTransactions = expenseTransactions != null ? expenseTransactions : new ArrayList<>();
    }

    /**
     * Check if there is any activity (income or expenses)
     */
    public boolean hasActivity() {
        return !incomeTransactions.isEmpty() || !expenseTransactions.isEmpty();
    }

    /**
     * Inner class for individual transaction details
     */
    public static class ServiceChargeTransactionDTO {
        private Long transactionId;
        private LocalDate transactionDate;
        private String description;
        private String category;
        private BigDecimal amount;

        public ServiceChargeTransactionDTO() {
        }

        public ServiceChargeTransactionDTO(Long transactionId, LocalDate transactionDate,
                                           String description, String category, BigDecimal amount) {
            this.transactionId = transactionId;
            this.transactionDate = transactionDate;
            this.description = description;
            this.category = category;
            this.amount = amount;
        }

        // Getters and Setters
        public Long getTransactionId() {
            return transactionId;
        }

        public void setTransactionId(Long transactionId) {
            this.transactionId = transactionId;
        }

        public LocalDate getTransactionDate() {
            return transactionDate;
        }

        public void setTransactionDate(LocalDate transactionDate) {
            this.transactionDate = transactionDate;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }
}

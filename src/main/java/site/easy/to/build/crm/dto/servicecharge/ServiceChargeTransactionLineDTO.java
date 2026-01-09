package site.easy.to.build.crm.dto.servicecharge;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for a single transaction line in the Service Charge Statement.
 * Includes both income and expense transactions with running balance.
 */
public class ServiceChargeTransactionLineDTO {

    private Long transactionId;
    private LocalDate transactionDate;
    private String description;
    private String category;
    private String reference;

    // Only one of these will be set per line
    private BigDecimal amountIn = BigDecimal.ZERO;
    private BigDecimal amountOut = BigDecimal.ZERO;

    // Running balance after this transaction
    private BigDecimal runningBalance = BigDecimal.ZERO;

    // Transaction type for styling
    private TransactionType type;

    public enum TransactionType {
        INCOME,
        EXPENSE
    }

    public ServiceChargeTransactionLineDTO() {
    }

    /**
     * Create an income transaction line
     */
    public static ServiceChargeTransactionLineDTO income(
            Long transactionId,
            LocalDate transactionDate,
            String description,
            String category,
            BigDecimal amount) {
        ServiceChargeTransactionLineDTO line = new ServiceChargeTransactionLineDTO();
        line.transactionId = transactionId;
        line.transactionDate = transactionDate;
        line.description = description;
        line.category = category;
        line.amountIn = amount != null ? amount.abs() : BigDecimal.ZERO;
        line.amountOut = BigDecimal.ZERO;
        line.type = TransactionType.INCOME;
        return line;
    }

    /**
     * Create an expense transaction line
     */
    public static ServiceChargeTransactionLineDTO expense(
            Long transactionId,
            LocalDate transactionDate,
            String description,
            String category,
            BigDecimal amount) {
        ServiceChargeTransactionLineDTO line = new ServiceChargeTransactionLineDTO();
        line.transactionId = transactionId;
        line.transactionDate = transactionDate;
        line.description = description;
        line.category = category;
        line.amountIn = BigDecimal.ZERO;
        line.amountOut = amount != null ? amount.abs() : BigDecimal.ZERO;
        line.type = TransactionType.EXPENSE;
        return line;
    }

    /**
     * Check if this is an income transaction
     */
    public boolean isIncome() {
        return type == TransactionType.INCOME;
    }

    /**
     * Check if this is an expense transaction
     */
    public boolean isExpense() {
        return type == TransactionType.EXPENSE;
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

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public BigDecimal getAmountIn() {
        return amountIn;
    }

    public void setAmountIn(BigDecimal amountIn) {
        this.amountIn = amountIn != null ? amountIn : BigDecimal.ZERO;
    }

    public BigDecimal getAmountOut() {
        return amountOut;
    }

    public void setAmountOut(BigDecimal amountOut) {
        this.amountOut = amountOut != null ? amountOut : BigDecimal.ZERO;
    }

    public BigDecimal getRunningBalance() {
        return runningBalance;
    }

    public void setRunningBalance(BigDecimal runningBalance) {
        this.runningBalance = runningBalance != null ? runningBalance : BigDecimal.ZERO;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }
}

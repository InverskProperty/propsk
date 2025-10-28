package site.easy.to.build.crm.dto.statement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for Transaction data in Option C statement generation
 *
 * Represents one transaction linked to a lease
 * Maps to TRANSACTIONS sheet in Excel output
 */
public class TransactionDTO {

    private Long transactionId;
    private LocalDate transactionDate;
    private Long invoiceId;  // Links to LeaseMasterDTO
    private Long propertyId;
    private Long customerId;
    private String category;  // rent, expense, owner_allocation, etc.
    private String transactionType;  // payment, charge
    private BigDecimal amount;
    private String description;
    private LocalDate leaseStartDate;  // Denormalized from invoice
    private LocalDate leaseEndDate;    // Denormalized from invoice
    private BigDecimal rentAmountAtTransaction;  // Monthly rent at time of transaction

    // Constructor
    public TransactionDTO() {
    }

    public TransactionDTO(Long transactionId, LocalDate transactionDate, Long invoiceId,
                         Long propertyId, Long customerId, String category, String transactionType,
                         BigDecimal amount, String description, LocalDate leaseStartDate,
                         LocalDate leaseEndDate, BigDecimal rentAmountAtTransaction) {
        this.transactionId = transactionId;
        this.transactionDate = transactionDate;
        this.invoiceId = invoiceId;
        this.propertyId = propertyId;
        this.customerId = customerId;
        this.category = category;
        this.transactionType = transactionType;
        this.amount = amount;
        this.description = description;
        this.leaseStartDate = leaseStartDate;
        this.leaseEndDate = leaseEndDate;
        this.rentAmountAtTransaction = rentAmountAtTransaction;
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

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(Long invoiceId) {
        this.invoiceId = invoiceId;
    }

    public Long getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(Long propertyId) {
        this.propertyId = propertyId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getLeaseStartDate() {
        return leaseStartDate;
    }

    public void setLeaseStartDate(LocalDate leaseStartDate) {
        this.leaseStartDate = leaseStartDate;
    }

    public LocalDate getLeaseEndDate() {
        return leaseEndDate;
    }

    public void setLeaseEndDate(LocalDate leaseEndDate) {
        this.leaseEndDate = leaseEndDate;
    }

    public BigDecimal getRentAmountAtTransaction() {
        return rentAmountAtTransaction;
    }

    public void setRentAmountAtTransaction(BigDecimal rentAmountAtTransaction) {
        this.rentAmountAtTransaction = rentAmountAtTransaction;
    }

    @Override
    public String toString() {
        return "TransactionDTO{" +
                "transactionId=" + transactionId +
                ", transactionDate=" + transactionDate +
                ", invoiceId=" + invoiceId +
                ", category='" + category + '\'' +
                ", transactionType='" + transactionType + '\'' +
                ", amount=" + amount +
                ", description='" + description + '\'' +
                '}';
    }
}

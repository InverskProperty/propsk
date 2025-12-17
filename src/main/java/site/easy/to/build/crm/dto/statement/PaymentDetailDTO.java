package site.easy.to.build.crm.dto.statement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for individual payment details (date + amount)
 * Used for breaking down rent received, outgoing payments, and expenses
 */
public class PaymentDetailDTO {
    private LocalDate paymentDate;
    private BigDecimal amount;
    private String description;
    private String category;
    private Long transactionId;

    // Batch/allocation tracking fields
    private String batchId;
    private String paymentStatus;
    private LocalDate paidDate;

    public PaymentDetailDTO() {
    }

    public PaymentDetailDTO(LocalDate paymentDate, BigDecimal amount) {
        this.paymentDate = paymentDate;
        this.amount = amount;
    }

    public PaymentDetailDTO(LocalDate paymentDate, BigDecimal amount, String description) {
        this.paymentDate = paymentDate;
        this.amount = amount;
        this.description = description;
    }

    // Getters and Setters
    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public LocalDate getPaidDate() {
        return paidDate;
    }

    public void setPaidDate(LocalDate paidDate) {
        this.paidDate = paidDate;
    }
}

package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "batch_payments")
public class BatchPayment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "payprop_batch_id", unique = true)
    private String paypropBatchId;
    
    @Column(name = "customer_id")
    private Long customerId;  // Changed from String to Long
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "total_amount", precision = 19, scale = 2)
    private BigDecimal totalAmount;
    
    @Column(name = "payment_count")
    private Integer paymentCount;
    
    @Column(name = "processing_date")
    private LocalDateTime processingDate;
    
    @Column(name = "completed_date")
    private LocalDateTime completedDate;
    
    @Column(name = "payment_method")
    private String paymentMethod;
    
    @Column(name = "currency", length = 3)
    private String currency = "GBP";
    
    @Column(name = "bank_reference")
    private String bankReference;
    
    @OneToMany(mappedBy = "batchPayment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Payment> payments = new ArrayList<>();
    
    @Column(name = "webhook_received_at")
    private LocalDateTime webhookReceivedAt;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "retry_count")
    private Integer retryCount = 0;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Additional fields for PayProp webhook data
    @Column(name = "total_in", precision = 19, scale = 2)
    private BigDecimal totalIn;
    
    @Column(name = "total_out", precision = 19, scale = 2)
    private BigDecimal totalOut;
    
    @Column(name = "total_commission", precision = 19, scale = 2)
    private BigDecimal totalCommission;
    
    @Column(name = "batch_date")
    private LocalDate batchDate;
    
    @Column(name = "processed_date")
    private LocalDateTime processedDate;
    
    @Column(name = "record_count")
    private Integer recordCount;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getPaypropBatchId() {
        return paypropBatchId;
    }
    
    public void setPaypropBatchId(String paypropBatchId) {
        this.paypropBatchId = paypropBatchId;
    }
    
    // Add alias methods for consistency with controller usage
    public String getPayPropBatchId() {
        return paypropBatchId;
    }
    
    public void setPayPropBatchId(String payPropBatchId) {
        this.paypropBatchId = payPropBatchId;
    }
    
    public Long getCustomerId() {  // Changed return type
        return customerId;
    }
    
    public void setCustomerId(Long customerId) {  // Changed parameter type
        this.customerId = customerId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
    
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
    
    public Integer getPaymentCount() {
        return paymentCount;
    }
    
    public void setPaymentCount(Integer paymentCount) {
        this.paymentCount = paymentCount;
    }
    
    public LocalDateTime getProcessingDate() {
        return processingDate;
    }
    
    public void setProcessingDate(LocalDateTime processingDate) {
        this.processingDate = processingDate;
    }
    
    public LocalDateTime getCompletedDate() {
        return completedDate;
    }
    
    public void setCompletedDate(LocalDateTime completedDate) {
        this.completedDate = completedDate;
    }
    
    public String getPaymentMethod() {
        return paymentMethod;
    }
    
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public String getBankReference() {
        return bankReference;
    }
    
    public void setBankReference(String bankReference) {
        this.bankReference = bankReference;
    }
    
    public List<Payment> getPayments() {
        return payments;
    }
    
    public void setPayments(List<Payment> payments) {
        this.payments = payments;
    }
    
    public LocalDateTime getWebhookReceivedAt() {
        return webhookReceivedAt;
    }
    
    public void setWebhookReceivedAt(LocalDateTime webhookReceivedAt) {
        this.webhookReceivedAt = webhookReceivedAt;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public Integer getRetryCount() {
        return retryCount;
    }
    
    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    // Additional getters and setters for PayProp webhook fields
    public BigDecimal getTotalIn() {
        return totalIn;
    }
    
    public void setTotalIn(BigDecimal totalIn) {
        this.totalIn = totalIn;
    }
    
    public BigDecimal getTotalOut() {
        return totalOut;
    }
    
    public void setTotalOut(BigDecimal totalOut) {
        this.totalOut = totalOut;
    }
    
    public BigDecimal getTotalCommission() {
        return totalCommission;
    }
    
    public void setTotalCommission(BigDecimal totalCommission) {
        this.totalCommission = totalCommission;
    }
    
    public LocalDate getBatchDate() {
        return batchDate;
    }
    
    public void setBatchDate(LocalDate batchDate) {
        this.batchDate = batchDate;
    }
    
    public LocalDateTime getProcessedDate() {
        return processedDate;
    }
    
    public void setProcessedDate(LocalDateTime processedDate) {
        this.processedDate = processedDate;
    }
    
    public Integer getRecordCount() {
        return recordCount;
    }
    
    public void setRecordCount(Integer recordCount) {
        this.recordCount = recordCount;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    // Alias method for consistency
    public LocalDateTime getPayPropWebhookReceived() {
        return webhookReceivedAt;
    }
    
    // Add setter alias for controller usage
    public void setPayPropWebhookReceived(LocalDateTime dateTime) {
        this.webhookReceivedAt = dateTime;
    }
    
    // Helper methods
    public void addPayment(Payment payment) {
        payments.add(payment);
        payment.setBatchPayment(this);
    }
    
    public void removePayment(Payment payment) {
        payments.remove(payment);
        payment.setBatchPayment(null);
    }
    
    public boolean isCompleted() {
        return "COMPLETED".equalsIgnoreCase(status);
    }
    
    public boolean isFailed() {
        return "FAILED".equalsIgnoreCase(status);
    }
    
    public boolean isPending() {
        return "PENDING".equalsIgnoreCase(status);
    }
}
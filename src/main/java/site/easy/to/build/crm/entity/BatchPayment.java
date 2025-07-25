// BatchPayment.java - Entity to store PayProp batch payment information
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
    private String payPropBatchId;
    
    @Column(name = "batch_date")
    private LocalDate batchDate;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;
    
    @Column(name = "total_in", precision = 10, scale = 2)
    private BigDecimal totalIn;
    
    @Column(name = "total_out", precision = 10, scale = 2)
    private BigDecimal totalOut;
    
    @Column(name = "total_commission", precision = 10, scale = 2)
    private BigDecimal totalCommission;
    
    @Column(name = "record_count")
    private Integer recordCount;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "processed_date")
    private LocalDateTime processedDate;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Relationship to payments in this batch
    @OneToMany(mappedBy = "batchPayment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Payment> payments = new ArrayList<>();
    
    // Relationship to financial transactions
    @OneToMany(mappedBy = "batchPayment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<FinancialTransaction> financialTransactions = new ArrayList<>();
    
    // PayProp integration fields
    @Column(name = "payprop_synced")
    private Boolean payPropSynced = false;
    
    @Column(name = "payprop_last_sync")
    private LocalDateTime payPropLastSync;
    
    @Column(name = "payprop_webhook_received")
    private LocalDateTime payPropWebhookReceived;
    
    // Constructors
    public BatchPayment() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public BatchPayment(String payPropBatchId, LocalDate batchDate) {
        this();
        this.payPropBatchId = payPropBatchId;
        this.batchDate = batchDate;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getPayPropBatchId() {
        return payPropBatchId;
    }
    
    public void setPayPropBatchId(String payPropBatchId) {
        this.payPropBatchId = payPropBatchId;
    }
    
    public LocalDate getBatchDate() {
        return batchDate;
    }
    
    public void setBatchDate(LocalDate batchDate) {
        this.batchDate = batchDate;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
    
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
    
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
        this.updatedAt = LocalDateTime.now();
    }
    
    public BigDecimal getTotalIn() {
        return totalIn;
    }
    
    public void setTotalIn(BigDecimal totalIn) {
        this.totalIn = totalIn;
        this.updatedAt = LocalDateTime.now();
    }
    
    public BigDecimal getTotalOut() {
        return totalOut;
    }
    
    public void setTotalOut(BigDecimal totalOut) {
        this.totalOut = totalOut;
        this.updatedAt = LocalDateTime.now();
    }
    
    public BigDecimal getTotalCommission() {
        return totalCommission;
    }
    
    public void setTotalCommission(BigDecimal totalCommission) {
        this.totalCommission = totalCommission;
        this.updatedAt = LocalDateTime.now();
    }
    
    public Integer getRecordCount() {
        return recordCount;
    }
    
    public void setRecordCount(Integer recordCount) {
        this.recordCount = recordCount;
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }
    
    public LocalDateTime getProcessedDate() {
        return processedDate;
    }
    
    public void setProcessedDate(LocalDateTime processedDate) {
        this.processedDate = processedDate;
        this.updatedAt = LocalDateTime.now();
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
    
    public List<Payment> getPayments() {
        return payments;
    }
    
    public void setPayments(List<Payment> payments) {
        this.payments = payments;
    }
    
    public List<FinancialTransaction> getFinancialTransactions() {
        return financialTransactions;
    }
    
    public void setFinancialTransactions(List<FinancialTransaction> financialTransactions) {
        this.financialTransactions = financialTransactions;
    }
    
    public Boolean getPayPropSynced() {
        return payPropSynced;
    }
    
    public void setPayPropSynced(Boolean payPropSynced) {
        this.payPropSynced = payPropSynced;
        this.updatedAt = LocalDateTime.now();
    }
    
    public LocalDateTime getPayPropLastSync() {
        return payPropLastSync;
    }
    
    public void setPayPropLastSync(LocalDateTime payPropLastSync) {
        this.payPropLastSync = payPropLastSync;
        this.updatedAt = LocalDateTime.now();
    }
    
    public LocalDateTime getPayPropWebhookReceived() {
        return payPropWebhookReceived;
    }
    
    public void setPayPropWebhookReceived(LocalDateTime payPropWebhookReceived) {
        this.payPropWebhookReceived = payPropWebhookReceived;
        this.updatedAt = LocalDateTime.now();
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
    
    public void addFinancialTransaction(FinancialTransaction transaction) {
        financialTransactions.add(transaction);
        transaction.setBatchPayment(this);
    }
    
    public void removeFinancialTransaction(FinancialTransaction transaction) {
        financialTransactions.remove(transaction);
        transaction.setBatchPayment(null);
    }
    
    @Override
    public String toString() {
        return "BatchPayment{" +
                "id=" + id +
                ", payPropBatchId='" + payPropBatchId + '\'' +
                ", batchDate=" + batchDate +
                ", status='" + status + '\'' +
                ", totalAmount=" + totalAmount +
                ", recordCount=" + recordCount +
                ", createdAt=" + createdAt +
                '}';
    }
}
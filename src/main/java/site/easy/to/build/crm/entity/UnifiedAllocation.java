package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * UnifiedAllocation - Represents an allocation from an incoming transaction
 * (expense, commission, owner payment, disbursement)
 *
 * Links to unified_allocations table created for the unified payment tracking system
 */
@Entity
@Table(name = "unified_allocations")
public class UnifiedAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incoming_transaction_id")
    private Long incomingTransactionId;

    // Link to unified_transactions (consolidated from transaction_batch_allocations)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unified_transaction_id")
    private UnifiedTransaction unifiedTransaction;

    @Column(name = "unified_transaction_id", insertable = false, updatable = false)
    private Long unifiedTransactionId;

    // Link to historical_transactions (for backwards compatibility)
    @Column(name = "historical_transaction_id")
    private Long historicalTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_type", nullable = false)
    private AllocationType allocationType;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "property_id")
    private Long propertyId;

    @Column(name = "property_name", length = 255)
    private String propertyName;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "beneficiary_type", length = 50)
    private String beneficiaryType;

    @Column(name = "beneficiary_id")
    private Long beneficiaryId;

    @Column(name = "beneficiary_name", length = 255)
    private String beneficiaryName;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "payment_batch_id", length = 50)
    private String paymentBatchId;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private AllocationSource source;

    @Column(name = "source_record_id")
    private Long sourceRecordId;

    @Column(name = "payprop_payment_id", length = 100)
    private String paypropPaymentId;

    @Column(name = "payprop_batch_id", length = 100)
    private String paypropBatchId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    // Enums
    public enum AllocationType {
        EXPENSE, COMMISSION, OWNER, DISBURSEMENT, OTHER
    }

    public enum PaymentStatus {
        PENDING, BATCHED, PAID
    }

    public enum AllocationSource {
        HISTORICAL, PAYPROP, MANUAL, CSV_IMPORT
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIncomingTransactionId() { return incomingTransactionId; }
    public void setIncomingTransactionId(Long incomingTransactionId) { this.incomingTransactionId = incomingTransactionId; }

    public AllocationType getAllocationType() { return allocationType; }
    public void setAllocationType(AllocationType allocationType) { this.allocationType = allocationType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }

    public String getPropertyName() { return propertyName; }
    public void setPropertyName(String propertyName) { this.propertyName = propertyName; }

    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }

    public String getBeneficiaryType() { return beneficiaryType; }
    public void setBeneficiaryType(String beneficiaryType) { this.beneficiaryType = beneficiaryType; }

    public Long getBeneficiaryId() { return beneficiaryId; }
    public void setBeneficiaryId(Long beneficiaryId) { this.beneficiaryId = beneficiaryId; }

    public String getBeneficiaryName() { return beneficiaryName; }
    public void setBeneficiaryName(String beneficiaryName) { this.beneficiaryName = beneficiaryName; }

    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getPaymentBatchId() { return paymentBatchId; }
    public void setPaymentBatchId(String paymentBatchId) { this.paymentBatchId = paymentBatchId; }

    public LocalDate getPaidDate() { return paidDate; }
    public void setPaidDate(LocalDate paidDate) { this.paidDate = paidDate; }

    public AllocationSource getSource() { return source; }
    public void setSource(AllocationSource source) { this.source = source; }

    public Long getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(Long sourceRecordId) { this.sourceRecordId = sourceRecordId; }

    public String getPaypropPaymentId() { return paypropPaymentId; }
    public void setPaypropPaymentId(String paypropPaymentId) { this.paypropPaymentId = paypropPaymentId; }

    public String getPaypropBatchId() { return paypropBatchId; }
    public void setPaypropBatchId(String paypropBatchId) { this.paypropBatchId = paypropBatchId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public UnifiedTransaction getUnifiedTransaction() { return unifiedTransaction; }
    public void setUnifiedTransaction(UnifiedTransaction unifiedTransaction) { this.unifiedTransaction = unifiedTransaction; }

    public Long getUnifiedTransactionId() { return unifiedTransactionId; }
    public void setUnifiedTransactionId(Long unifiedTransactionId) { this.unifiedTransactionId = unifiedTransactionId; }

    public Long getHistoricalTransactionId() { return historicalTransactionId; }
    public void setHistoricalTransactionId(Long historicalTransactionId) { this.historicalTransactionId = historicalTransactionId; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

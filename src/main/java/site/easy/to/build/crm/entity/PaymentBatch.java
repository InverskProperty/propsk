package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PaymentBatch - Groups allocations into a single payment
 * Supports both PayProp imported batches and manual payment batches
 *
 * Links to payment_batches table created for the unified payment tracking system
 */
@Entity
@Table(name = "payment_batches")
public class PaymentBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", unique = true, nullable = false, length = 50)
    private String batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "batch_type", nullable = false)
    private BatchType batchType;

    @Column(name = "beneficiary_id")
    private Long beneficiaryId;

    @Column(name = "beneficiary_name", length = 255)
    private String beneficiaryName;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "total_allocations", precision = 12, scale = 2, nullable = false)
    private BigDecimal totalAllocations;

    @Column(name = "balance_adjustment", precision = 12, scale = 2)
    private BigDecimal balanceAdjustment = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_source")
    private AdjustmentSource adjustmentSource = AdjustmentSource.NONE;

    @Column(name = "adjustment_notes", length = 500)
    private String adjustmentNotes;

    @Column(name = "total_payment", precision = 12, scale = 2, nullable = false)
    private BigDecimal totalPayment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private BatchStatus status = BatchStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private BatchSource source;

    @Column(name = "payprop_batch_id", length = 100)
    private String paypropBatchId;

    @Column(name = "payment_reference", length = 255)
    private String paymentReference;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Enums
    public enum BatchType {
        OWNER_PAYMENT, EXPENSE_PAYMENT, COMMISSION, DISBURSEMENT
    }

    public enum AdjustmentSource {
        NONE, BLOCK, OWNER_BALANCE
    }

    public enum BatchStatus {
        DRAFT, PENDING, PAID
    }

    public enum BatchSource {
        MANUAL, PAYPROP
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public BatchType getBatchType() { return batchType; }
    public void setBatchType(BatchType batchType) { this.batchType = batchType; }

    public Long getBeneficiaryId() { return beneficiaryId; }
    public void setBeneficiaryId(Long beneficiaryId) { this.beneficiaryId = beneficiaryId; }

    public String getBeneficiaryName() { return beneficiaryName; }
    public void setBeneficiaryName(String beneficiaryName) { this.beneficiaryName = beneficiaryName; }

    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }

    public BigDecimal getTotalAllocations() { return totalAllocations; }
    public void setTotalAllocations(BigDecimal totalAllocations) { this.totalAllocations = totalAllocations; }

    public BigDecimal getBalanceAdjustment() { return balanceAdjustment; }
    public void setBalanceAdjustment(BigDecimal balanceAdjustment) { this.balanceAdjustment = balanceAdjustment; }

    public AdjustmentSource getAdjustmentSource() { return adjustmentSource; }
    public void setAdjustmentSource(AdjustmentSource adjustmentSource) { this.adjustmentSource = adjustmentSource; }

    public String getAdjustmentNotes() { return adjustmentNotes; }
    public void setAdjustmentNotes(String adjustmentNotes) { this.adjustmentNotes = adjustmentNotes; }

    public BigDecimal getTotalPayment() { return totalPayment; }
    public void setTotalPayment(BigDecimal totalPayment) { this.totalPayment = totalPayment; }

    public BatchStatus getStatus() { return status; }
    public void setStatus(BatchStatus status) { this.status = status; }

    public BatchSource getSource() { return source; }
    public void setSource(BatchSource source) { this.source = source; }

    public String getPaypropBatchId() { return paypropBatchId; }
    public void setPaypropBatchId(String paypropBatchId) { this.paypropBatchId = paypropBatchId; }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

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

package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_corrections")
public class PaymentCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correction_type", nullable = false, length = 50)
    private String correctionType; // REASSIGN_PROPERTY, EXCLUDE, ADJUST_AMOUNT

    @Column(name = "source_system", nullable = false, length = 20)
    private String sourceSystem; // PAYPROP, HISTORICAL

    @Column(name = "source_transaction_id", length = 100)
    private String sourceTransactionId; // PayProp transaction ID (pay_prop_transaction_id)

    @Column(name = "source_incoming_transaction_id", length = 100)
    private String sourceIncomingTransactionId; // PayProp incoming transaction ID

    @Column(name = "original_property_id")
    private Long originalPropertyId;

    @Column(name = "corrected_property_id")
    private Long correctedPropertyId;

    @Column(name = "original_invoice_id")
    private Long originalInvoiceId;

    @Column(name = "corrected_invoice_id")
    private Long correctedInvoiceId;

    @Column(name = "original_lease_reference", length = 100)
    private String originalLeaseReference;

    @Column(name = "corrected_lease_reference", length = 100)
    private String correctedLeaseReference;

    @Column(name = "original_amount", precision = 12, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "corrected_amount", precision = 12, scale = 2)
    private BigDecimal correctedAmount;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCorrectionType() { return correctionType; }
    public void setCorrectionType(String correctionType) { this.correctionType = correctionType; }

    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }

    public String getSourceTransactionId() { return sourceTransactionId; }
    public void setSourceTransactionId(String sourceTransactionId) { this.sourceTransactionId = sourceTransactionId; }

    public String getSourceIncomingTransactionId() { return sourceIncomingTransactionId; }
    public void setSourceIncomingTransactionId(String sourceIncomingTransactionId) { this.sourceIncomingTransactionId = sourceIncomingTransactionId; }

    public Long getOriginalPropertyId() { return originalPropertyId; }
    public void setOriginalPropertyId(Long originalPropertyId) { this.originalPropertyId = originalPropertyId; }

    public Long getCorrectedPropertyId() { return correctedPropertyId; }
    public void setCorrectedPropertyId(Long correctedPropertyId) { this.correctedPropertyId = correctedPropertyId; }

    public Long getOriginalInvoiceId() { return originalInvoiceId; }
    public void setOriginalInvoiceId(Long originalInvoiceId) { this.originalInvoiceId = originalInvoiceId; }

    public Long getCorrectedInvoiceId() { return correctedInvoiceId; }
    public void setCorrectedInvoiceId(Long correctedInvoiceId) { this.correctedInvoiceId = correctedInvoiceId; }

    public String getOriginalLeaseReference() { return originalLeaseReference; }
    public void setOriginalLeaseReference(String originalLeaseReference) { this.originalLeaseReference = originalLeaseReference; }

    public String getCorrectedLeaseReference() { return correctedLeaseReference; }
    public void setCorrectedLeaseReference(String correctedLeaseReference) { this.correctedLeaseReference = correctedLeaseReference; }

    public BigDecimal getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }

    public BigDecimal getCorrectedAmount() { return correctedAmount; }
    public void setCorrectedAmount(BigDecimal correctedAmount) { this.correctedAmount = correctedAmount; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}

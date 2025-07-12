// Payment.java - Main payment entity for all transactions
package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== PAYPROP INTEGRATION FIELDS =====
    
    @Column(name = "pay_prop_payment_id", unique = true)
    private String payPropPaymentId;
    
    @Column(name = "parent_payment_id")
    private String parentPaymentId;
    
    @Column(name = "batch_id")
    private String batchId;

    // ===== FINANCIAL DETAILS =====
    
    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "tax_amount", precision = 10, scale = 2)
    private BigDecimal taxAmount;
    
    @Column(name = "commission_amount", precision = 10, scale = 2)
    private BigDecimal commissionAmount;

    // ===== DATES =====
    
    @Column(name = "payment_date")
    private LocalDate paymentDate;
    
    @Column(name = "reconciliation_date")
    private LocalDate reconciliationDate;
    
    @Column(name = "remittance_date")
    private LocalDate remittanceDate;

    // ===== PAYMENT DETAILS =====
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "reference", length = 255)
    private String reference;
    
    @Column(name = "status", length = 50)
    private String status;
    
    @Column(name = "category_id", length = 100)
    private String categoryId;

    // ===== RELATIONSHIPS =====
    
    @Column(name = "property_id")
    private Long propertyId;
    
    @Column(name = "tenant_id")
    private Long tenantId;
    
    @Column(name = "beneficiary_id")
    private Long beneficiaryId;

    // ===== AUDIT FIELDS =====
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by")
    private Long createdBy;
    
    @Column(name = "updated_by")
    private Long updatedBy;

    // ===== CONSTRUCTORS =====
    
    public Payment() {}

    // ===== GETTERS AND SETTERS =====
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPayPropPaymentId() { return payPropPaymentId; }
    public void setPayPropPaymentId(String payPropPaymentId) { this.payPropPaymentId = payPropPaymentId; }

    public String getParentPaymentId() { return parentPaymentId; }
    public void setParentPaymentId(String parentPaymentId) { this.parentPaymentId = parentPaymentId; }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(BigDecimal commissionAmount) { this.commissionAmount = commissionAmount; }

    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }

    public LocalDate getReconciliationDate() { return reconciliationDate; }
    public void setReconciliationDate(LocalDate reconciliationDate) { this.reconciliationDate = reconciliationDate; }

    public LocalDate getRemittanceDate() { return remittanceDate; }
    public void setRemittanceDate(LocalDate remittanceDate) { this.remittanceDate = remittanceDate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getBeneficiaryId() { return beneficiaryId; }
    public void setBeneficiaryId(Long beneficiaryId) { this.beneficiaryId = beneficiaryId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    // ===== UTILITY METHODS =====
    
    /**
     * Check if this is a rent payment (payment from tenant)
     */
    public boolean isRentPayment() {
        return tenantId != null && (categoryId == null || categoryId.toLowerCase().contains("rent"));
    }
    
    /**
     * Check if this is an outgoing payment (payment to beneficiary)
     */
    public boolean isOutgoingPayment() {
        return beneficiaryId != null;
    }

    @Override
    public String toString() {
        return "Payment{" +
                "id=" + id +
                ", payPropPaymentId='" + payPropPaymentId + '\'' +
                ", amount=" + amount +
                ", paymentDate=" + paymentDate +
                ", status='" + status + '\'' +
                ", categoryId='" + categoryId + '\'' +
                '}';
    }
}
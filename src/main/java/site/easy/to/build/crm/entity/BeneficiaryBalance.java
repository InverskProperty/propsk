// BeneficiaryBalance.java - Outstanding balances owed to beneficiaries
package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "beneficiary_balances")
public class BeneficiaryBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== RELATIONSHIPS =====
    
    @Column(name = "beneficiary_id", nullable = false)
    private Long beneficiaryId;
    
    @Column(name = "property_id")
    private Long propertyId; // Optional - can be global balance

    // ===== BALANCE DETAILS =====
    
    @Column(name = "balance_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal balanceAmount;
    
    @Column(name = "balance_date", nullable = false)
    private LocalDate balanceDate;
    
    @Column(name = "balance_type", length = 50)
    private String balanceType; // OWED, CREDIT, etc.

    // ===== DESCRIPTIONS =====
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "notes", length = 1000)
    private String notes;

    // ===== STATUS =====
    
    @Column(name = "status", length = 50, columnDefinition = "varchar(50) default 'ACTIVE'")
    private String status = "ACTIVE";
    
    @Column(name = "is_cleared", length = 1, columnDefinition = "varchar(1) default 'N'")
    private String isCleared = "N";
    
    @Column(name = "cleared_date")
    private LocalDate clearedDate;

    // ===== AUDIT FIELDS =====
    
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_by")
    private Long updatedBy;

    // ===== CONSTRUCTORS =====
    
    public BeneficiaryBalance() {}

    public BeneficiaryBalance(Long beneficiaryId, BigDecimal balanceAmount, LocalDate balanceDate) {
        this.beneficiaryId = beneficiaryId;
        this.balanceAmount = balanceAmount;
        this.balanceDate = balanceDate;
        this.createdAt = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
    }

    public BeneficiaryBalance(Long beneficiaryId, Long propertyId, BigDecimal balanceAmount, LocalDate balanceDate) {
        this.beneficiaryId = beneficiaryId;
        this.propertyId = propertyId;
        this.balanceAmount = balanceAmount;
        this.balanceDate = balanceDate;
        this.createdAt = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
    }

    // ===== GETTERS AND SETTERS =====
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBeneficiaryId() { return beneficiaryId; }
    public void setBeneficiaryId(Long beneficiaryId) { this.beneficiaryId = beneficiaryId; }

    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }

    public BigDecimal getBalanceAmount() { return balanceAmount; }
    public void setBalanceAmount(BigDecimal balanceAmount) { this.balanceAmount = balanceAmount; }

    public LocalDate getBalanceDate() { return balanceDate; }
    public void setBalanceDate(LocalDate balanceDate) { this.balanceDate = balanceDate; }

    public String getBalanceType() { return balanceType; }
    public void setBalanceType(String balanceType) { this.balanceType = balanceType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIsCleared() { return isCleared; }
    public void setIsCleared(String isCleared) { this.isCleared = isCleared; }

    public LocalDate getClearedDate() { return clearedDate; }
    public void setClearedDate(LocalDate clearedDate) { this.clearedDate = clearedDate; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    // ===== UTILITY METHODS =====
    
    /**
     * Check if balance is positive (owed to beneficiary)
     */
    public boolean isPositiveBalance() {
        return balanceAmount != null && balanceAmount.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Check if balance is negative (credit balance)
     */
    public boolean isCreditBalance() {
        return balanceAmount != null && balanceAmount.compareTo(BigDecimal.ZERO) < 0;
    }
    
    /**
     * Check if balance is cleared
     */
    public boolean isBalanceCleared() {
        return "Y".equals(isCleared);
    }
    
    /**
     * Mark balance as cleared
     */
    public void markAsCleared(LocalDate clearedDate) {
        this.isCleared = "Y";
        this.clearedDate = clearedDate;
        this.status = "CLEARED";
        this.lastUpdated = LocalDateTime.now();
    }
    
    /**
     * Check if this is a property-specific balance
     */
    public boolean isPropertySpecific() {
        return propertyId != null;
    }
    
    /**
     * Check if this is a global balance
     */
    public boolean isGlobalBalance() {
        return propertyId == null;
    }

    @Override
    public String toString() {
        return "BeneficiaryBalance{" +
                "id=" + id +
                ", beneficiaryId=" + beneficiaryId +
                ", propertyId=" + propertyId +
                ", balanceAmount=" + balanceAmount +
                ", balanceDate=" + balanceDate +
                ", status='" + status + '\'' +
                '}';
    }
}
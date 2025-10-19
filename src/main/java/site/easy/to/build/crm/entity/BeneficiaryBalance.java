/**
 * Tracks running balance for property beneficiaries (owners)
 *
 * Balance represents what the agency OWES to the beneficiary:
 * - Positive balance = Agency owes money to beneficiary
 * - Negative balance = Beneficiary owes money to agency (expenses exceeded income)
 *
 * Balance Movements:
 * - Owner's share of rent: INCREASES balance (we owe them more)
 * - Expenses paid on their behalf: DECREASES balance (we owe them less)
 * - Payments to owner: DECREASES balance (we paid them)
 */
package site.easy.to.build.crm.entity;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "beneficiary_balances",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"customer_id", "property_id", "balance_date"}
    )
)
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class BeneficiaryBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== RELATIONSHIPS =====

    /**
     * The beneficiary (owner) for this balance
     * @JsonIdentityInfo handles circular reference prevention
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /**
     * The property this balance relates to
     * @JsonIdentityInfo handles circular reference prevention
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    // Legacy fields - kept for backward compatibility and query support
    @Column(name = "beneficiary_id", insertable = false, updatable = false)
    private Long beneficiaryId;

    @Column(name = "property_id", insertable = false, updatable = false)
    private Long propertyId;

    // ===== BALANCE TRACKING =====

    /**
     * Current balance (what agency owes to beneficiary)
     * Positive = Agency owes money
     * Negative = Beneficiary owes money (expenses exceeded income)
     */
    @Column(name = "balance_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal balanceAmount = BigDecimal.ZERO;

    /**
     * Opening balance at start of period
     */
    @Column(name = "opening_balance", precision = 15, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    /**
     * Date of this balance snapshot
     */
    @Column(name = "balance_date", nullable = false)
    private LocalDate balanceDate;

    @Column(name = "balance_type", length = 50)
    private String balanceType = "CURRENT";

    // ===== PERIOD TRACKING =====

    /**
     * Period start date (for monthly/period statements)
     */
    @Column(name = "period_start")
    private LocalDate periodStart;

    /**
     * Period end date (for monthly/period statements)
     */
    @Column(name = "period_end")
    private LocalDate periodEnd;

    // ===== PERIOD TOTALS =====

    /**
     * Total rent allocated to owner in this period
     * (Owner's share after commission)
     */
    @Column(name = "total_rent_allocated", precision = 15, scale = 2)
    private BigDecimal totalRentAllocated = BigDecimal.ZERO;

    /**
     * Total expenses paid on behalf of owner in this period
     */
    @Column(name = "total_expenses", precision = 15, scale = 2)
    private BigDecimal totalExpenses = BigDecimal.ZERO;

    /**
     * Total payments made to owner in this period
     */
    @Column(name = "total_payments_out", precision = 15, scale = 2)
    private BigDecimal totalPaymentsOut = BigDecimal.ZERO;

    // ===== DESCRIPTIONS =====

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "notes", length = 1000)
    private String notes;

    // ===== STATUS =====

    @Column(name = "status", length = 50)
    private String status = "ACTIVE";

    @Column(name = "is_cleared", length = 1)
    private String isCleared = "N";

    @Column(name = "cleared_date")
    private LocalDate clearedDate;

    // ===== AUDIT FIELDS =====

    /**
     * Last transaction that updated this balance
     */
    @Column(name = "last_transaction_id")
    private Long lastTransactionId;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    // ===== PAYPROP INTEGRATION =====

    /**
     * PayProp beneficiary ID for sync
     */
    @Column(name = "payprop_beneficiary_id", length = 50)
    private String paypropBeneficiaryId;

    /**
     * Sync status with PayProp
     */
    @Column(name = "sync_status", length = 20)
    private String syncStatus = "active";

    // ===== CONSTRUCTORS =====

    public BeneficiaryBalance() {
        this.createdAt = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
        this.balanceDate = LocalDate.now();
    }

    public BeneficiaryBalance(Customer customer, Property property, LocalDate balanceDate) {
        this();
        this.customer = customer;
        this.property = property;
        this.balanceDate = balanceDate;
    }

    // Legacy constructor
    public BeneficiaryBalance(Long beneficiaryId, BigDecimal balanceAmount, LocalDate balanceDate) {
        this.beneficiaryId = beneficiaryId;
        this.balanceAmount = balanceAmount;
        this.balanceDate = balanceDate;
        this.createdAt = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
    }

    // Legacy constructor
    public BeneficiaryBalance(Long beneficiaryId, Long propertyId, BigDecimal balanceAmount, LocalDate balanceDate) {
        this.beneficiaryId = beneficiaryId;
        this.balanceAmount = balanceAmount;
        this.balanceDate = balanceDate;
        this.createdAt = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
    }

    // ===== LIFECYCLE CALLBACKS =====

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
        if (this.balanceDate == null) {
            this.balanceDate = LocalDate.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.lastUpdated = LocalDateTime.now();
    }

    // ===== BUSINESS LOGIC METHODS =====

    /**
     * Add owner's share of rent (increases balance)
     */
    public void addRentAllocation(BigDecimal amount) {
        this.balanceAmount = this.balanceAmount.add(amount);
        this.totalRentAllocated = this.totalRentAllocated.add(amount);
    }

    /**
     * Deduct expense paid on owner's behalf (decreases balance)
     */
    public void deductExpense(BigDecimal amount) {
        this.balanceAmount = this.balanceAmount.subtract(amount);
        this.totalExpenses = this.totalExpenses.add(amount);
    }

    /**
     * Record payment to owner (decreases balance)
     */
    public void deductPayment(BigDecimal amount) {
        this.balanceAmount = this.balanceAmount.subtract(amount);
        this.totalPaymentsOut = this.totalPaymentsOut.add(amount);
    }

    /**
     * Check if beneficiary owes money (negative balance)
     */
    public boolean isOverdrawn() {
        return this.balanceAmount.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Check if payment is due (positive balance above threshold)
     */
    public boolean isPaymentDue(BigDecimal threshold) {
        return this.balanceAmount.compareTo(threshold) > 0;
    }

    /**
     * Get net change for the period
     */
    public BigDecimal getNetChange() {
        if (this.openingBalance == null) {
            return this.balanceAmount;
        }
        return this.balanceAmount.subtract(this.openingBalance);
    }

    // ===== GETTERS AND SETTERS =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public Property getProperty() { return property; }
    public void setProperty(Property property) { this.property = property; }

    public Long getBeneficiaryId() { return beneficiaryId; }
    public void setBeneficiaryId(Long beneficiaryId) { this.beneficiaryId = beneficiaryId; }

    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }

    public BigDecimal getBalanceAmount() { return balanceAmount; }
    public void setBalanceAmount(BigDecimal balanceAmount) { this.balanceAmount = balanceAmount; }

    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }

    public LocalDate getBalanceDate() { return balanceDate; }
    public void setBalanceDate(LocalDate balanceDate) { this.balanceDate = balanceDate; }

    public String getBalanceType() { return balanceType; }
    public void setBalanceType(String balanceType) { this.balanceType = balanceType; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public BigDecimal getTotalRentAllocated() { return totalRentAllocated; }
    public void setTotalRentAllocated(BigDecimal totalRentAllocated) {
        this.totalRentAllocated = totalRentAllocated;
    }

    public BigDecimal getTotalExpenses() { return totalExpenses; }
    public void setTotalExpenses(BigDecimal totalExpenses) { this.totalExpenses = totalExpenses; }

    public BigDecimal getTotalPaymentsOut() { return totalPaymentsOut; }
    public void setTotalPaymentsOut(BigDecimal totalPaymentsOut) {
        this.totalPaymentsOut = totalPaymentsOut;
    }

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

    public Long getLastTransactionId() { return lastTransactionId; }
    public void setLastTransactionId(Long lastTransactionId) {
        this.lastTransactionId = lastTransactionId;
    }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    public String getPaypropBeneficiaryId() { return paypropBeneficiaryId; }
    public void setPaypropBeneficiaryId(String paypropBeneficiaryId) {
        this.paypropBeneficiaryId = paypropBeneficiaryId;
    }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    // ===== UTILITY METHODS =====

    /**
     * Check if balance is positive (owed to beneficiary)
     */
    public boolean isPositiveBalance() {
        return balanceAmount != null && balanceAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if balance is negative (beneficiary owes agency)
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
        return property != null;
    }

    @Override
    public String toString() {
        return "BeneficiaryBalance{" +
                "id=" + id +
                ", customer=" + (customer != null ? customer.getCustomerId() : null) +
                ", property=" + (property != null ? property.getId() : null) +
                ", balanceAmount=" + balanceAmount +
                ", balanceDate=" + balanceDate +
                ", status='" + status + '\'' +
                '}';
    }
}
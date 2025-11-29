package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PropertyBalanceLedger - Tracks all balance movements for a property
 *
 * This provides an audit trail for Property.accountBalance changes.
 * Each entry records a deposit, withdrawal, transfer, or adjustment.
 *
 * Balance Semantics:
 * - DEPOSIT: Money added to balance (paid less than owed to owner)
 * - WITHDRAWAL: Money taken from balance (paid more than owed, or expense paid)
 * - TRANSFER_IN: Received from another property (e.g., unit → block)
 * - TRANSFER_OUT: Sent to another property (e.g., unit → block)
 * - ADJUSTMENT: Manual correction
 * - OPENING_BALANCE: Initial balance setup
 */
@Entity
@Table(name = "property_balance_ledger",
    indexes = {
        @Index(name = "idx_pbl_property", columnList = "property_id"),
        @Index(name = "idx_pbl_owner", columnList = "owner_id"),
        @Index(name = "idx_pbl_batch", columnList = "payment_batch_id"),
        @Index(name = "idx_pbl_date", columnList = "entry_date"),
        @Index(name = "idx_pbl_related_property", columnList = "related_property_id")
    }
)
public class PropertyBalanceLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== WHICH PROPERTY =====

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "property_name", length = 255)
    private String propertyName;

    // ===== OWNER (for filtering/reporting) =====

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "owner_name", length = 255)
    private String ownerName;

    // ===== WHAT TYPE OF MOVEMENT =====

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private EntryType entryType;

    /**
     * Amount of the movement (always positive)
     * The entry_type determines if it increases or decreases balance
     */
    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    /**
     * Running balance after this entry
     * This should match Property.accountBalance after applying this entry
     */
    @Column(name = "running_balance", precision = 15, scale = 2, nullable = false)
    private BigDecimal runningBalance;

    // ===== WHY =====

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ===== LINKS =====

    /**
     * Links to PaymentBatch.batchId for audit trail
     */
    @Column(name = "payment_batch_id", length = 50)
    private String paymentBatchId;

    /**
     * External reference (bank reference, invoice number, etc.)
     */
    @Column(name = "reference", length = 100)
    private String reference;

    // ===== TRANSFER SUPPORT (for block property transfers) =====

    /**
     * For TRANSFER_IN: the source property
     * For TRANSFER_OUT: the destination property
     */
    @Column(name = "related_property_id")
    private Long relatedPropertyId;

    @Column(name = "related_property_name", length = 255)
    private String relatedPropertyName;

    // ===== SOURCE =====

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20)
    private Source source;

    // ===== AUDIT =====

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    // ===== ENUMS =====

    public enum EntryType {
        DEPOSIT,           // Money added (paid less than owed)
        WITHDRAWAL,        // Money taken (paid more than owed, or expense)
        TRANSFER_IN,       // Received from another property (block)
        TRANSFER_OUT,      // Sent to another property (to block)
        ADJUSTMENT,        // Manual correction
        OPENING_BALANCE    // Initial balance setup
    }

    public enum Source {
        PAYMENT_BATCH,       // From payment batch process
        BLOCK_TRANSFER,      // Transfer to/from block property
        MANUAL,              // Manual entry by user
        IMPORT,              // CSV/Excel import
        PAYPROP_SYNC,        // Synced from PayProp
        HISTORICAL_RECON     // Historical reconciliation
    }

    // ===== CONSTRUCTORS =====

    public PropertyBalanceLedger() {
        this.createdAt = LocalDateTime.now();
        this.entryDate = LocalDate.now();
    }

    public PropertyBalanceLedger(Long propertyId, EntryType entryType, BigDecimal amount, BigDecimal runningBalance) {
        this();
        this.propertyId = propertyId;
        this.entryType = entryType;
        this.amount = amount;
        this.runningBalance = runningBalance;
    }

    // ===== LIFECYCLE CALLBACKS =====

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (entryDate == null) {
            entryDate = LocalDate.now();
        }
    }

    // ===== BUSINESS LOGIC =====

    /**
     * Check if this entry increases the balance
     */
    public boolean isCredit() {
        return entryType == EntryType.DEPOSIT ||
               entryType == EntryType.TRANSFER_IN ||
               entryType == EntryType.OPENING_BALANCE ||
               (entryType == EntryType.ADJUSTMENT && amount.compareTo(BigDecimal.ZERO) > 0);
    }

    /**
     * Check if this entry decreases the balance
     */
    public boolean isDebit() {
        return entryType == EntryType.WITHDRAWAL ||
               entryType == EntryType.TRANSFER_OUT ||
               (entryType == EntryType.ADJUSTMENT && amount.compareTo(BigDecimal.ZERO) < 0);
    }

    /**
     * Get the signed amount (positive for credits, negative for debits)
     */
    public BigDecimal getSignedAmount() {
        if (isDebit()) {
            return amount.negate();
        }
        return amount;
    }

    /**
     * Check if this is a transfer entry
     */
    public boolean isTransfer() {
        return entryType == EntryType.TRANSFER_IN || entryType == EntryType.TRANSFER_OUT;
    }

    // ===== GETTERS AND SETTERS =====

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(Long propertyId) {
        this.propertyId = propertyId;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getRunningBalance() {
        return runningBalance;
    }

    public void setRunningBalance(BigDecimal runningBalance) {
        this.runningBalance = runningBalance;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getPaymentBatchId() {
        return paymentBatchId;
    }

    public void setPaymentBatchId(String paymentBatchId) {
        this.paymentBatchId = paymentBatchId;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public Long getRelatedPropertyId() {
        return relatedPropertyId;
    }

    public void setRelatedPropertyId(Long relatedPropertyId) {
        this.relatedPropertyId = relatedPropertyId;
    }

    public String getRelatedPropertyName() {
        return relatedPropertyName;
    }

    public void setRelatedPropertyName(String relatedPropertyName) {
        this.relatedPropertyName = relatedPropertyName;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public String toString() {
        return "PropertyBalanceLedger{" +
                "id=" + id +
                ", propertyId=" + propertyId +
                ", entryType=" + entryType +
                ", amount=" + amount +
                ", runningBalance=" + runningBalance +
                ", entryDate=" + entryDate +
                '}';
    }
}

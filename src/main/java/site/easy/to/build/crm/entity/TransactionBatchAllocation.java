package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * TransactionBatchAllocation - Links transactions to payment batches with split support
 *
 * This entity enables:
 * - Allocating a transaction fully to one batch
 * - Splitting a transaction across multiple batches
 * - Tracking which transactions contributed to which owner payments
 *
 * Example:
 *   Transaction A (£1000) → 100% to Batch LMN
 *   Transaction B (£1000) → 100% to Batch OPQ
 *   Transaction C (£1000) → £500 to Batch LMN, £500 to Batch OPQ
 *
 * Validation: SUM(allocated_amount) for a transaction should not exceed
 *             the transaction's net_to_owner_amount
 *
 * @deprecated Use {@link UnifiedAllocation} instead. The allocation system has been
 * consolidated into a single table (unified_allocations) that handles both PayProp
 * allocations and manual batch allocations.
 *
 * All data from this table has been migrated to unified_allocations.
 * This entity is kept for backwards compatibility during the transition period.
 */
@Deprecated
@Entity
@Table(name = "transaction_batch_allocations",
    indexes = {
        @Index(name = "idx_tba_transaction", columnList = "transaction_id"),
        @Index(name = "idx_tba_batch", columnList = "batch_reference"),
        @Index(name = "idx_tba_property", columnList = "property_id"),
        @Index(name = "idx_tba_beneficiary", columnList = "beneficiary_id")
    }
)
public class TransactionBatchAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== LINK TO SOURCE TRANSACTION =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private HistoricalTransaction transaction;

    @Column(name = "transaction_id", insertable = false, updatable = false)
    private Long transactionId;

    // ===== LINK TO UNIFIED TRANSACTION (NEW - for migration to unified layer) =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unified_transaction_id")
    private UnifiedTransaction unifiedTransaction;

    @Column(name = "unified_transaction_id", insertable = false, updatable = false)
    private Long unifiedTransactionId;

    // ===== BATCH REFERENCE =====

    /**
     * Reference to the payment batch (e.g., "OWNER-20250301-0001")
     * This links to PaymentBatch.batchId or can be a simple reference string
     */
    @Column(name = "batch_reference", nullable = false, length = 50)
    private String batchReference;

    // ===== ALLOCATED AMOUNT =====

    /**
     * The portion of the transaction's net_to_owner_amount allocated to this batch.
     * Can be positive (rent income) or negative (expenses).
     */
    @Column(name = "allocated_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal allocatedAmount;

    // ===== DENORMALIZED FIELDS (for quick filtering/reporting) =====

    @Column(name = "property_id")
    private Long propertyId;

    @Column(name = "property_name", length = 255)
    private String propertyName;

    @Column(name = "beneficiary_id")
    private Long beneficiaryId;

    @Column(name = "beneficiary_name", length = 255)
    private String beneficiaryName;

    // ===== ALLOCATION TYPE =====

    /**
     * Type of allocation - determines how this is displayed in payment advice.
     * OWNER = rent income (shown as receipt)
     * COMMISSION = commission deduction (usually embedded in OWNER receipts)
     * EXPENSE = expense deduction
     * DISBURSEMENT = disbursement to third party (e.g., service charges)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_type")
    private AllocationType allocationType = AllocationType.OWNER;

    public enum AllocationType {
        OWNER, COMMISSION, EXPENSE, DISBURSEMENT
    }

    // ===== AUDIT =====

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    // ===== CONSTRUCTORS =====

    public TransactionBatchAllocation() {
        this.createdAt = LocalDateTime.now();
    }

    public TransactionBatchAllocation(HistoricalTransaction transaction, String batchReference, BigDecimal allocatedAmount) {
        this();
        this.transaction = transaction;
        this.batchReference = batchReference;
        this.allocatedAmount = allocatedAmount;

        // Copy denormalized fields
        if (transaction.getProperty() != null) {
            this.propertyId = transaction.getProperty().getId();
            this.propertyName = transaction.getProperty().getPropertyName();
        }

        // Determine allocation type based on transaction category
        this.allocationType = determineAllocationType(transaction);
    }

    /**
     * Determine allocation type from transaction category.
     */
    private static AllocationType determineAllocationType(HistoricalTransaction transaction) {
        String category = transaction.getCategory();
        if (category == null) {
            return AllocationType.EXPENSE;
        }

        String lowerCategory = category.toLowerCase();

        // DISBURSEMENT: Block property or explicit disbursement items
        if (lowerCategory.contains("disbursement") || lowerCategory.contains("block property")) {
            return AllocationType.DISBURSEMENT;
        }

        // EXPENSE: Refunds and other expense categories
        if (lowerCategory.contains("refund") || lowerCategory.contains("expense") ||
            lowerCategory.contains("council") || lowerCategory.contains("utilities") ||
            lowerCategory.contains("maintenance") || lowerCategory.contains("cleaning") ||
            lowerCategory.contains("contractor") || lowerCategory.contains("compliance") ||
            lowerCategory.contains("furnishings") || lowerCategory.contains("management")) {
            return AllocationType.EXPENSE;
        }

        // COMMISSION: Explicit commission/agency fee
        if (lowerCategory.contains("commission") || lowerCategory.contains("agency_fee")) {
            return AllocationType.COMMISSION;
        }

        // OWNER: Rent income and tenant payments (default for income)
        if (lowerCategory.contains("rent") || lowerCategory.contains("income") ||
            lowerCategory.contains("tenant")) {
            return AllocationType.OWNER;
        }

        // Default to EXPENSE for unrecognized categories
        return AllocationType.EXPENSE;
    }

    // ===== LIFECYCLE CALLBACKS =====

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // ===== BUSINESS METHODS =====

    /**
     * Check if this is a positive allocation (income to owner)
     */
    public boolean isIncome() {
        return allocatedAmount != null && allocatedAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if this is a negative allocation (expense reducing owner's payment)
     */
    public boolean isExpense() {
        return allocatedAmount != null && allocatedAmount.compareTo(BigDecimal.ZERO) < 0;
    }

    // ===== GETTERS AND SETTERS =====

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public HistoricalTransaction getTransaction() {
        return transaction;
    }

    public void setTransaction(HistoricalTransaction transaction) {
        this.transaction = transaction;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public UnifiedTransaction getUnifiedTransaction() {
        return unifiedTransaction;
    }

    public void setUnifiedTransaction(UnifiedTransaction unifiedTransaction) {
        this.unifiedTransaction = unifiedTransaction;
    }

    public Long getUnifiedTransactionId() {
        return unifiedTransactionId;
    }

    public String getBatchReference() {
        return batchReference;
    }

    public void setBatchReference(String batchReference) {
        this.batchReference = batchReference;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }

    public void setAllocatedAmount(BigDecimal allocatedAmount) {
        this.allocatedAmount = allocatedAmount;
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

    public Long getBeneficiaryId() {
        return beneficiaryId;
    }

    public void setBeneficiaryId(Long beneficiaryId) {
        this.beneficiaryId = beneficiaryId;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public void setBeneficiaryName(String beneficiaryName) {
        this.beneficiaryName = beneficiaryName;
    }

    public AllocationType getAllocationType() {
        return allocationType;
    }

    public void setAllocationType(AllocationType allocationType) {
        this.allocationType = allocationType;
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
        return "TransactionBatchAllocation{" +
                "id=" + id +
                ", transactionId=" + transactionId +
                ", batchReference='" + batchReference + '\'' +
                ", allocatedAmount=" + allocatedAmount +
                ", propertyName='" + propertyName + '\'' +
                '}';
    }
}

package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Unified Transaction Entity - Materialized View Combining Multiple Sources
 *
 * This is a DERIVED table that combines transactions from:
 * - historical_transactions (CSV imports)
 * - financial_transactions (PayProp sync)
 * - Future sources (Xero, QuickBooks, etc.)
 *
 * KEY PRINCIPLE: This table is REBUILABLE - can be deleted and recreated anytime
 * from source tables without data loss.
 *
 * Used by:
 * - Statement generation
 * - Block financial reporting
 * - Portfolio analytics
 * - Summary tables
 */
@Entity
@Table(name = "unified_transactions")
public class UnifiedTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== SOURCE TRACKING =====

    @Enumerated(EnumType.STRING)
    @Column(name = "source_system", nullable = false)
    private SourceSystem sourceSystem;

    @Column(name = "source_table", length = 50, nullable = false)
    private String sourceTable;

    @Column(name = "source_record_id", nullable = false)
    private Long sourceRecordId;

    // ===== TRANSACTION CORE FIELDS =====

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 100)
    private String category;

    // ===== RELATIONSHIPS =====

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "property_id")
    private Long propertyId;

    @Column(name = "customer_id")
    private Long customerId;

    // ===== LEASE CONTEXT (Denormalized) =====

    @Column(name = "lease_reference", length = 100)
    private String leaseReference;

    @Column(name = "lease_start_date")
    private LocalDate leaseStartDate;

    @Column(name = "lease_end_date")
    private LocalDate leaseEndDate;

    @Column(name = "rent_amount_at_transaction", precision = 10, scale = 2)
    private BigDecimal rentAmountAtTransaction;

    // ===== PROPERTY CONTEXT (Denormalized) =====

    @Column(name = "property_name", length = 255)
    private String propertyName;

    // ===== PAYPROP SPECIFIC =====

    @Column(name = "payprop_transaction_id", length = 100)
    private String paypropTransactionId;

    @Column(name = "payprop_data_source", length = 50)
    private String paypropDataSource;

    // ===== REBUILD METADATA =====

    @Column(name = "rebuilt_at", nullable = false)
    private LocalDateTime rebuiltAt;

    @Column(name = "rebuild_batch_id", length = 100)
    private String rebuildBatchId;

    // ===== ENUMS =====

    public enum SourceSystem {
        HISTORICAL,
        PAYPROP,
        XERO,
        QUICKBOOKS
    }

    // ===== CONSTRUCTORS =====

    public UnifiedTransaction() {
        this.rebuiltAt = LocalDateTime.now();
    }

    // ===== GETTERS AND SETTERS =====

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SourceSystem getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(SourceSystem sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    public Long getSourceRecordId() {
        return sourceRecordId;
    }

    public void setSourceRecordId(Long sourceRecordId) {
        this.sourceRecordId = sourceRecordId;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(Long invoiceId) {
        this.invoiceId = invoiceId;
    }

    public Long getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(Long propertyId) {
        this.propertyId = propertyId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getLeaseReference() {
        return leaseReference;
    }

    public void setLeaseReference(String leaseReference) {
        this.leaseReference = leaseReference;
    }

    public LocalDate getLeaseStartDate() {
        return leaseStartDate;
    }

    public void setLeaseStartDate(LocalDate leaseStartDate) {
        this.leaseStartDate = leaseStartDate;
    }

    public LocalDate getLeaseEndDate() {
        return leaseEndDate;
    }

    public void setLeaseEndDate(LocalDate leaseEndDate) {
        this.leaseEndDate = leaseEndDate;
    }

    public BigDecimal getRentAmountAtTransaction() {
        return rentAmountAtTransaction;
    }

    public void setRentAmountAtTransaction(BigDecimal rentAmountAtTransaction) {
        this.rentAmountAtTransaction = rentAmountAtTransaction;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPaypropTransactionId() {
        return paypropTransactionId;
    }

    public void setPaypropTransactionId(String paypropTransactionId) {
        this.paypropTransactionId = paypropTransactionId;
    }

    public String getPaypropDataSource() {
        return paypropDataSource;
    }

    public void setPaypropDataSource(String paypropDataSource) {
        this.paypropDataSource = paypropDataSource;
    }

    public LocalDateTime getRebuiltAt() {
        return rebuiltAt;
    }

    public void setRebuiltAt(LocalDateTime rebuiltAt) {
        this.rebuiltAt = rebuiltAt;
    }

    public String getRebuildBatchId() {
        return rebuildBatchId;
    }

    public void setRebuildBatchId(String rebuildBatchId) {
        this.rebuildBatchId = rebuildBatchId;
    }
}

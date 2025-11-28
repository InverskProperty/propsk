package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * UnifiedIncomingTransaction - Represents an incoming rent payment
 * This is the anchor transaction from which allocations are derived
 *
 * Links to unified_incoming_transactions table
 */
@Entity
@Table(name = "unified_incoming_transactions")
public class UnifiedIncomingTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private TransactionSource source;

    @Column(name = "source_id", length = 100)
    private String sourceId;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "tenant_name", length = 255)
    private String tenantName;

    @Column(name = "lease_id")
    private Long leaseId;

    @Column(name = "lease_reference", length = 100)
    private String leaseReference;

    @Column(name = "payprop_transaction_id", length = 100)
    private String paypropTransactionId;

    @Column(name = "reconciliation_date")
    private LocalDate reconciliationDate;

    @Column(name = "bank_statement_date")
    private LocalDate bankStatementDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Enum
    public enum TransactionSource {
        HISTORICAL, PAYPROP
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public TransactionSource getSource() { return source; }
    public void setSource(TransactionSource source) { this.source = source; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }

    public Long getLeaseId() { return leaseId; }
    public void setLeaseId(Long leaseId) { this.leaseId = leaseId; }

    public String getLeaseReference() { return leaseReference; }
    public void setLeaseReference(String leaseReference) { this.leaseReference = leaseReference; }

    public String getPaypropTransactionId() { return paypropTransactionId; }
    public void setPaypropTransactionId(String paypropTransactionId) { this.paypropTransactionId = paypropTransactionId; }

    public LocalDate getReconciliationDate() { return reconciliationDate; }
    public void setReconciliationDate(LocalDate reconciliationDate) { this.reconciliationDate = reconciliationDate; }

    public LocalDate getBankStatementDate() { return bankStatementDate; }
    public void setBankStatementDate(LocalDate bankStatementDate) { this.bankStatementDate = bankStatementDate; }

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

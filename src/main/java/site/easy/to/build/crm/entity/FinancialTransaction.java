// FinancialTransaction.java - Financial transaction entity for PayProp integration
package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "financial_transactions")
public class FinancialTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // ===== PAYPROP INTEGRATION =====
    
    @Column(name = "pay_prop_transaction_id", unique = true)
    private String payPropTransactionId;
    
    // ===== FINANCIAL DETAILS =====
    
    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;
    
    @Column(name = "matched_amount", precision = 10, scale = 2)
    private BigDecimal matchedAmount;
    
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;
    
    @Column(name = "transaction_type", length = 50)
    private String transactionType; // "invoice", "credit_note", "debit_note"
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "has_tax")
    private Boolean hasTax = false;
    
    @Column(name = "tax_amount", precision = 10, scale = 2)
    private BigDecimal taxAmount;
    
    @Column(name = "deposit_id", length = 100)
    private String depositId;
    
    // ===== PROPERTY INFORMATION =====
    
    @Column(name = "property_id", length = 100)
    private String propertyId;
    
    @Column(name = "property_name", length = 255)
    private String propertyName;
    
    // ===== TENANT INFORMATION =====
    
    @Column(name = "tenant_id", length = 100)
    private String tenantId;
    
    @Column(name = "tenant_name", length = 255)
    private String tenantName;
    
    // ===== CATEGORY INFORMATION =====
    
    @Column(name = "category_id", length = 100)
    private String categoryId;
    
    @Column(name = "category_name", length = 255)
    private String categoryName;
    
    // ===== CALCULATED COMMISSION FIELDS =====
    
    @Column(name = "commission_amount", precision = 10, scale = 2)
    private BigDecimal commissionAmount;
    
    @Column(name = "commission_rate", precision = 5, scale = 2)
    private BigDecimal commissionRate;
    
    @Column(name = "service_fee_amount", precision = 10, scale = 2)
    private BigDecimal serviceFeeAmount;
    
    @Column(name = "net_to_owner_amount", precision = 10, scale = 2)
    private BigDecimal netToOwnerAmount;
    
    // ===== DATA SOURCE TRACKING =====
    
    @Column(name = "data_source", length = 50)
    private String dataSource; // "ICDN_ACTUAL", "PAYMENT_INSTRUCTION", "COMMISSION_PAYMENT"
    
    @Column(name = "instruction_id", length = 100) 
    private String instructionId; // Links actual payments to their instructions
    
    @Column(name = "reconciliation_date")
    private LocalDate reconciliationDate; // When payment was actually processed
    
    @Column(name = "instruction_date") 
    private LocalDate instructionDate; // When payment was instructed
    
    // ===== ACTUAL VS CALCULATED TRACKING =====
    
    @Column(name = "is_actual_transaction")
    private Boolean isActualTransaction = false; // true for reconciled payments
    
    @Column(name = "is_instruction")
    private Boolean isInstruction = false; // true for payment instructions
    
    @Column(name = "actual_commission_amount", precision = 10, scale = 2)
    private BigDecimal actualCommissionAmount; // Real commission taken by PayProp
    
    @Column(name = "calculated_commission_amount", precision = 10, scale = 2) 
    private BigDecimal calculatedCommissionAmount; // What should have been charged
    
    // ===== AUDIT FIELDS =====
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // ===== CONSTRUCTORS =====
    
    public FinancialTransaction() {}
    
    public FinancialTransaction(String payPropTransactionId, BigDecimal amount, LocalDate transactionDate) {
        this.payPropTransactionId = payPropTransactionId;
        this.amount = amount;
        this.transactionDate = transactionDate;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // ===== LIFECYCLE METHODS =====
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (hasTax == null) {
            hasTax = false;
        }
        if (isActualTransaction == null) {
            isActualTransaction = false;
        }
        if (isInstruction == null) {
            isInstruction = false;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // ===== GETTERS AND SETTERS =====
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getPayPropTransactionId() { return payPropTransactionId; }
    public void setPayPropTransactionId(String payPropTransactionId) { this.payPropTransactionId = payPropTransactionId; }
    
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    
    public BigDecimal getMatchedAmount() { return matchedAmount; }
    public void setMatchedAmount(BigDecimal matchedAmount) { this.matchedAmount = matchedAmount; }
    
    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }
    
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Boolean getHasTax() { return hasTax; }
    public void setHasTax(Boolean hasTax) { this.hasTax = hasTax; }
    
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    
    public String getDepositId() { return depositId; }
    public void setDepositId(String depositId) { this.depositId = depositId; }
    
    public String getPropertyId() { return propertyId; }
    public void setPropertyId(String propertyId) { this.propertyId = propertyId; }
    
    public String getPropertyName() { return propertyName; }
    public void setPropertyName(String propertyName) { this.propertyName = propertyName; }
    
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    
    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }
    
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    
    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(BigDecimal commissionAmount) { this.commissionAmount = commissionAmount; }
    
    public BigDecimal getCommissionRate() { return commissionRate; }
    public void setCommissionRate(BigDecimal commissionRate) { this.commissionRate = commissionRate; }
    
    public BigDecimal getServiceFeeAmount() { return serviceFeeAmount; }
    public void setServiceFeeAmount(BigDecimal serviceFeeAmount) { this.serviceFeeAmount = serviceFeeAmount; }
    
    public BigDecimal getNetToOwnerAmount() { return netToOwnerAmount; }
    public void setNetToOwnerAmount(BigDecimal netToOwnerAmount) { this.netToOwnerAmount = netToOwnerAmount; }
    
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    
    public String getInstructionId() { return instructionId; }
    public void setInstructionId(String instructionId) { this.instructionId = instructionId; }
    
    public LocalDate getReconciliationDate() { return reconciliationDate; }
    public void setReconciliationDate(LocalDate reconciliationDate) { this.reconciliationDate = reconciliationDate; }
    
    public LocalDate getInstructionDate() { return instructionDate; }
    public void setInstructionDate(LocalDate instructionDate) { this.instructionDate = instructionDate; }
    
    public Boolean getIsActualTransaction() { return isActualTransaction; }
    public void setIsActualTransaction(Boolean isActualTransaction) { this.isActualTransaction = isActualTransaction; }
    
    public Boolean getIsInstruction() { return isInstruction; }
    public void setIsInstruction(Boolean isInstruction) { this.isInstruction = isInstruction; }
    
    public BigDecimal getActualCommissionAmount() { return actualCommissionAmount; }
    public void setActualCommissionAmount(BigDecimal actualCommissionAmount) { this.actualCommissionAmount = actualCommissionAmount; }
    
    public BigDecimal getCalculatedCommissionAmount() { return calculatedCommissionAmount; }
    public void setCalculatedCommissionAmount(BigDecimal calculatedCommissionAmount) { this.calculatedCommissionAmount = calculatedCommissionAmount; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    // ===== UTILITY METHODS =====
    
    /**
     * Check if this is a rent payment transaction
     */
    public boolean isRentPayment() {
        return "invoice".equals(transactionType) && 
               (categoryName == null || categoryName.toLowerCase().contains("rent"));
    }
    
    /**
     * Check if commission has been calculated
     */
    public boolean hasCommissionCalculated() {
        return commissionAmount != null && commissionRate != null;
    }
    
    /**
     * Get the outstanding amount (due but not matched)
     */
    public BigDecimal getOutstandingAmount() {
        if (amount == null) return BigDecimal.ZERO;
        if (matchedAmount == null) return amount;
        return amount.subtract(matchedAmount);
    }
    
    /**
     * Check if transaction is fully paid
     */
    public boolean isFullyPaid() {
        return getOutstandingAmount().compareTo(BigDecimal.ZERO) == 0;
    }
    
    /**
     * Check if this is a deposit transaction
     */
    public boolean isDeposit() {
        return depositId != null || 
               (categoryName != null && categoryName.toLowerCase().contains("deposit"));
    }
    
    /**
     * Check if this is an actual transaction (not instruction)
     */
    public boolean isActual() {
        return Boolean.TRUE.equals(isActualTransaction);
    }
    
    /**
     * Check if this is an instruction (not actual)
     */
    public boolean isInstructionOnly() {
        return Boolean.TRUE.equals(isInstruction);
    }
    
    @Override
    public String toString() {
        return "FinancialTransaction{" +
                "id=" + id +
                ", payPropTransactionId='" + payPropTransactionId + '\'' +
                ", amount=" + amount +
                ", transactionDate=" + transactionDate +
                ", transactionType='" + transactionType + '\'' +
                ", dataSource='" + dataSource + '\'' +
                ", propertyName='" + propertyName + '\'' +
                ", tenantName='" + tenantName + '\'' +
                '}';
    }
}
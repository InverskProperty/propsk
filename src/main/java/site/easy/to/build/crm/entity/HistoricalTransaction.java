package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Historical Transaction Entity - For importing and managing historical financial data
 * 
 * This entity handles historical payment records, bank imports, and manual entries
 * that need to be integrated into financial reporting alongside current PayProp/local data.
 */
@Entity
@Table(name = "historical_transactions")
public class HistoricalTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // ===== RELATIONSHIP FIELDS =====
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id")
    private Property property;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /**
     * Invoice (Lease) Reference - Links transaction to specific lease agreement
     * Enables lease-level arrears tracking and historical lease analysis
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    /**
     * Lease Period Fields - Captures the lease dates at time of transaction
     * Used for historical reporting and lease period analysis
     */
    @Column(name = "lease_start_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate leaseStartDate;

    @Column(name = "lease_end_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate leaseEndDate;

    /**
     * Rent Amount at Transaction - Captures monthly rent at time of transaction
     * Used for historical income analysis and lease comparison
     */
    @Column(name = "rent_amount_at_transaction", precision = 10, scale = 2)
    private BigDecimal rentAmountAtTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedByUser;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconciled_by_user_id")
    private User reconciledByUser;
    
    // ===== CORE TRANSACTION FIELDS =====
    
    @Column(name = "transaction_date", nullable = false)
    @NotNull(message = "Transaction date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate transactionDate;
    
    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "-999999999.99", message = "Amount is too small")
    @DecimalMax(value = "999999999.99", message = "Amount is too large")
    private BigDecimal amount;
    
    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    @NotBlank(message = "Description is required")
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;
    
    // ===== CLASSIFICATION FIELDS =====
    
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;
    
    @Column(name = "category", length = 100)
    private String category;
    
    @Column(name = "subcategory", length = 100)
    private String subcategory;
    
    // ===== SOURCE AND PROVENANCE FIELDS =====

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private TransactionSource source = TransactionSource.historical_import;

    @Column(name = "account_source", length = 50)
    private String accountSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_source_id")
    private PaymentSource paymentSource;

    // NEW: Block-level transaction tracking
    // Enables block financial statements (service charges, block expenses, etc.)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "block_id")
    private Block block;

    @Column(name = "source_reference", length = 255)
    private String sourceReference;

    @Column(name = "import_batch_id", length = 100)
    private String importBatchId;

    @Column(name = "import_staging_id")
    private Long importStagingId;
    
    // ===== BANK/PAYMENT DETAILS =====
    
    @Column(name = "bank_reference", length = 100)
    private String bankReference;
    
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;
    
    @Column(name = "counterparty_name", length = 255)
    private String counterpartyName;
    
    @Column(name = "counterparty_account", length = 100)
    private String counterpartyAccount;
    
    // ===== BALANCE AND ACCOUNTING FIELDS =====
    
    @Column(name = "running_balance", precision = 12, scale = 2)
    private BigDecimal runningBalance;
    
    @Column(name = "reconciled", nullable = false)
    private Boolean reconciled = false;
    
    @Column(name = "reconciled_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate reconciledDate;
    
    // ===== REPORTING AND TAX FIELDS =====
    
    @Column(name = "financial_year", length = 10)
    @Pattern(regexp = "\\d{4}-\\d{4}|\\d{4}", message = "Financial year must be in format YYYY or YYYY-YYYY")
    private String financialYear;
    
    @Column(name = "tax_relevant", nullable = false)
    private Boolean taxRelevant = false;
    
    @Column(name = "vat_applicable", nullable = false)
    private Boolean vatApplicable = false;
    
    @Column(name = "vat_amount", precision = 10, scale = 2)
    @DecimalMin(value = "0.00", message = "VAT amount cannot be negative")
    private BigDecimal vatAmount;
    
    // Note: net_amount is calculated in database as generated column
    
    // ===== STATUS AND VALIDATION FIELDS =====
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private TransactionStatus status = TransactionStatus.active;
    
    @Column(name = "validated", nullable = false)
    private Boolean validated = false;
    
    @Column(name = "validation_notes", columnDefinition = "TEXT")
    private String validationNotes;
    
    // ===== AUDIT FIELDS =====
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // ===== METADATA FIELDS =====
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @Column(name = "tags", length = 500)
    private String tags;

    // ===== PAYPROP INTEGRATION FIELDS =====

    @Column(name = "payprop_transaction_id", unique = true, length = 100)
    private String paypropTransactionId;

    @Column(name = "payprop_property_id", length = 100)
    private String paypropPropertyId;

    @Column(name = "payprop_tenant_id", length = 100)
    private String paypropTenantId;

    @Column(name = "payprop_beneficiary_id", length = 100)
    private String paypropBeneficiaryId;

    @Column(name = "payprop_category_id", length = 100)
    private String paypropCategoryId;

    // ===== COMMISSION AND FEE TRACKING =====

    @Column(name = "commission_rate", precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Column(name = "commission_amount", precision = 10, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "service_fee_rate", precision = 5, scale = 2)
    private BigDecimal serviceFeeRate;

    @Column(name = "service_fee_amount", precision = 10, scale = 2)
    private BigDecimal serviceFeeAmount;

    @Column(name = "net_to_owner_amount", precision = 12, scale = 2)
    private BigDecimal netToOwnerAmount;

    // ===== INSTRUCTION TRACKING =====

    @Column(name = "is_instruction")
    private Boolean isInstruction = false;

    @Column(name = "is_actual_transaction")
    private Boolean isActualTransaction = false;

    @Column(name = "instruction_id", length = 100)
    private String instructionId;

    @Column(name = "instruction_date")
    private LocalDate instructionDate;

    // ===== BATCH PAYMENT SUPPORT =====

    @Column(name = "batch_payment_id")
    private Long batchPaymentId;

    @Column(name = "payprop_batch_id", length = 100)
    private String paypropBatchId;

    @Column(name = "batch_sequence_number")
    private Integer batchSequenceNumber;

    // ===== ADDITIONAL FIELDS =====

    @Column(name = "deposit_id", length = 100)
    private String depositId;

    @Column(name = "reference", length = 255)
    private String reference;

    // ===== CONSTRUCTORS =====
    
    public HistoricalTransaction() {
        this.createdAt = LocalDateTime.now();
    }
    
    public HistoricalTransaction(LocalDate transactionDate, BigDecimal amount, 
                               String description, TransactionType transactionType) {
        this();
        this.transactionDate = transactionDate;
        this.amount = amount;
        this.description = description;
        this.transactionType = transactionType;
    }
    
    // ===== LIFECYCLE CALLBACKS =====
    
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        
        // Auto-set financial year if not provided
        if (this.financialYear == null && this.transactionDate != null) {
            this.financialYear = calculateFinancialYear(this.transactionDate);
        }
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    // ===== BUSINESS LOGIC METHODS =====
    
    /**
     * Calculate financial year from transaction date
     * Assumes UK financial year (April to March)
     */
    public static String calculateFinancialYear(LocalDate date) {
        int year = date.getYear();
        if (date.getMonthValue() >= 4) { // April or later
            return year + "-" + (year + 1);
        } else { // January to March
            return (year - 1) + "-" + year;
        }
    }
    
    /**
     * Check if this is a credit transaction (positive amount)
     */
    public boolean isCredit() {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Check if this is a debit transaction (negative amount)
     */
    public boolean isDebit() {
        return amount != null && amount.compareTo(BigDecimal.ZERO) < 0;
    }
    
    /**
     * Get absolute amount (always positive)
     */
    public BigDecimal getAbsoluteAmount() {
        return amount != null ? amount.abs() : BigDecimal.ZERO;
    }
    
    /**
     * Mark transaction as reconciled
     */
    public void markReconciled(User reconciledBy) {
        this.reconciled = true;
        this.reconciledDate = LocalDate.now();
        this.reconciledByUser = reconciledBy;
    }
    
    /**
     * Mark transaction as unreconciled
     */
    public void markUnreconciled() {
        this.reconciled = false;
        this.reconciledDate = null;
        this.reconciledByUser = null;
    }
    
    /**
     * Validate transaction data
     */
    public void validate() {
        this.validated = true;
        // Add validation logic here
    }
    
    /**
     * Get display string for source
     */
    public String getSourceDisplay() {
        return switch (source) {
            case historical_import -> "Historical Import";
            case manual_entry -> "Manual Entry";
            case bank_import -> "Bank Import";
            case spreadsheet_import -> "Spreadsheet Import";
            case system_migration -> "System Migration";
            case api_sync -> "API Sync";
        };
    }
    
    /**
     * Get CSS class for transaction type
     */
    public String getTransactionTypeClass() {
        return switch (transactionType) {
            case payment -> "text-success";
            case invoice -> "text-primary";
            case expense -> "text-danger";
            case maintenance -> "text-warning";
            case deposit -> "text-info";
            case withdrawal -> "text-warning";
            case transfer -> "text-secondary";
            case fee -> "text-danger";
            case refund -> "text-success";
            case adjustment -> "text-muted";
        };
    }
    
    // ===== GETTERS AND SETTERS =====
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Property getProperty() { return property; }
    public void setProperty(Property property) { this.property = property; }
    
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public Invoice getInvoice() { return invoice; }
    public void setInvoice(Invoice invoice) { this.invoice = invoice; }

    public LocalDate getLeaseStartDate() { return leaseStartDate; }
    public void setLeaseStartDate(LocalDate leaseStartDate) { this.leaseStartDate = leaseStartDate; }

    public LocalDate getLeaseEndDate() { return leaseEndDate; }
    public void setLeaseEndDate(LocalDate leaseEndDate) { this.leaseEndDate = leaseEndDate; }

    public BigDecimal getRentAmountAtTransaction() { return rentAmountAtTransaction; }
    public void setRentAmountAtTransaction(BigDecimal rentAmountAtTransaction) {
        this.rentAmountAtTransaction = rentAmountAtTransaction;
    }

    public User getCreatedByUser() { return createdByUser; }
    public void setCreatedByUser(User createdByUser) { this.createdByUser = createdByUser; }
    
    public User getUpdatedByUser() { return updatedByUser; }
    public void setUpdatedByUser(User updatedByUser) { this.updatedByUser = updatedByUser; }
    
    public User getReconciledByUser() { return reconciledByUser; }
    public void setReconciledByUser(User reconciledByUser) { this.reconciledByUser = reconciledByUser; }
    
    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }
    
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public String getSubcategory() { return subcategory; }
    public void setSubcategory(String subcategory) { this.subcategory = subcategory; }
    
    public TransactionSource getSource() { return source; }
    public void setSource(TransactionSource source) { this.source = source; }

    public String getAccountSource() { return accountSource; }
    public void setAccountSource(String accountSource) { this.accountSource = accountSource; }

    public PaymentSource getPaymentSource() { return paymentSource; }
    public void setPaymentSource(PaymentSource paymentSource) { this.paymentSource = paymentSource; }

    public Block getBlock() { return block; }
    public void setBlock(Block block) { this.block = block; }

    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String sourceReference) { this.sourceReference = sourceReference; }

    public String getImportBatchId() { return importBatchId; }
    public void setImportBatchId(String importBatchId) { this.importBatchId = importBatchId; }

    public Long getImportStagingId() { return importStagingId; }
    public void setImportStagingId(Long importStagingId) { this.importStagingId = importStagingId; }
    
    public String getBankReference() { return bankReference; }
    public void setBankReference(String bankReference) { this.bankReference = bankReference; }
    
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    
    public String getCounterpartyName() { return counterpartyName; }
    public void setCounterpartyName(String counterpartyName) { this.counterpartyName = counterpartyName; }
    
    public String getCounterpartyAccount() { return counterpartyAccount; }
    public void setCounterpartyAccount(String counterpartyAccount) { this.counterpartyAccount = counterpartyAccount; }
    
    public BigDecimal getRunningBalance() { return runningBalance; }
    public void setRunningBalance(BigDecimal runningBalance) { this.runningBalance = runningBalance; }
    
    public Boolean getReconciled() { return reconciled; }
    public void setReconciled(Boolean reconciled) { this.reconciled = reconciled; }
    
    public LocalDate getReconciledDate() { return reconciledDate; }
    public void setReconciledDate(LocalDate reconciledDate) { this.reconciledDate = reconciledDate; }
    
    public String getFinancialYear() { return financialYear; }
    public void setFinancialYear(String financialYear) { this.financialYear = financialYear; }
    
    public Boolean getTaxRelevant() { return taxRelevant; }
    public void setTaxRelevant(Boolean taxRelevant) { this.taxRelevant = taxRelevant; }
    
    public Boolean getVatApplicable() { return vatApplicable; }
    public void setVatApplicable(Boolean vatApplicable) { this.vatApplicable = vatApplicable; }
    
    public BigDecimal getVatAmount() { return vatAmount; }
    public void setVatAmount(BigDecimal vatAmount) { this.vatAmount = vatAmount; }
    
    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }
    
    public Boolean getValidated() { return validated; }
    public void setValidated(Boolean validated) { this.validated = validated; }
    
    public String getValidationNotes() { return validationNotes; }
    public void setValidationNotes(String validationNotes) { this.validationNotes = validationNotes; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    // ===== PAYPROP FIELD GETTERS AND SETTERS =====

    public String getPaypropTransactionId() { return paypropTransactionId; }
    public void setPaypropTransactionId(String paypropTransactionId) { this.paypropTransactionId = paypropTransactionId; }

    public String getPaypropPropertyId() { return paypropPropertyId; }
    public void setPaypropPropertyId(String paypropPropertyId) { this.paypropPropertyId = paypropPropertyId; }

    public String getPaypropTenantId() { return paypropTenantId; }
    public void setPaypropTenantId(String paypropTenantId) { this.paypropTenantId = paypropTenantId; }

    public String getPaypropBeneficiaryId() { return paypropBeneficiaryId; }
    public void setPaypropBeneficiaryId(String paypropBeneficiaryId) { this.paypropBeneficiaryId = paypropBeneficiaryId; }

    public String getPaypropCategoryId() { return paypropCategoryId; }
    public void setPaypropCategoryId(String paypropCategoryId) { this.paypropCategoryId = paypropCategoryId; }

    public BigDecimal getCommissionRate() { return commissionRate; }
    public void setCommissionRate(BigDecimal commissionRate) { this.commissionRate = commissionRate; }

    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(BigDecimal commissionAmount) { this.commissionAmount = commissionAmount; }

    public BigDecimal getServiceFeeRate() { return serviceFeeRate; }
    public void setServiceFeeRate(BigDecimal serviceFeeRate) { this.serviceFeeRate = serviceFeeRate; }

    public BigDecimal getServiceFeeAmount() { return serviceFeeAmount; }
    public void setServiceFeeAmount(BigDecimal serviceFeeAmount) { this.serviceFeeAmount = serviceFeeAmount; }

    public BigDecimal getNetToOwnerAmount() { return netToOwnerAmount; }
    public void setNetToOwnerAmount(BigDecimal netToOwnerAmount) { this.netToOwnerAmount = netToOwnerAmount; }

    public Boolean getIsInstruction() { return isInstruction; }
    public void setIsInstruction(Boolean isInstruction) { this.isInstruction = isInstruction; }

    public Boolean getIsActualTransaction() { return isActualTransaction; }
    public void setIsActualTransaction(Boolean isActualTransaction) { this.isActualTransaction = isActualTransaction; }

    public String getInstructionId() { return instructionId; }
    public void setInstructionId(String instructionId) { this.instructionId = instructionId; }

    public LocalDate getInstructionDate() { return instructionDate; }
    public void setInstructionDate(LocalDate instructionDate) { this.instructionDate = instructionDate; }

    public Long getBatchPaymentId() { return batchPaymentId; }
    public void setBatchPaymentId(Long batchPaymentId) { this.batchPaymentId = batchPaymentId; }

    public String getPaypropBatchId() { return paypropBatchId; }
    public void setPaypropBatchId(String paypropBatchId) { this.paypropBatchId = paypropBatchId; }

    public Integer getBatchSequenceNumber() { return batchSequenceNumber; }
    public void setBatchSequenceNumber(Integer batchSequenceNumber) { this.batchSequenceNumber = batchSequenceNumber; }

    public String getDepositId() { return depositId; }
    public void setDepositId(String depositId) { this.depositId = depositId; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    // ===== ENUMS =====
    
    public enum TransactionType {
        payment("Payment"),
        invoice("Invoice"),
        expense("Expense"),
        maintenance("Maintenance"),
        deposit("Deposit"),
        withdrawal("Withdrawal"),
        transfer("Transfer"),
        fee("Fee"),
        refund("Refund"),
        adjustment("Adjustment");
        
        private final String displayName;
        
        TransactionType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
    
    public enum TransactionSource {
        historical_import("Historical Import"),
        manual_entry("Manual Entry"),
        bank_import("Bank Import"),
        spreadsheet_import("Spreadsheet Import"),
        system_migration("System Migration"),
        api_sync("API Sync");

        private final String displayName;

        TransactionSource(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }
    
    public enum TransactionStatus {
        active("Active"),
        cancelled("Cancelled"),
        disputed("Disputed"),
        pending_review("Pending Review");
        
        private final String displayName;
        
        TransactionStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
    
    // ===== toString, equals, hashCode =====
    
    @Override
    public String toString() {
        return String.format("HistoricalTransaction{id=%d, date=%s, amount=%s, type=%s, description='%s'}", 
                           id, transactionDate, amount, transactionType, description);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HistoricalTransaction)) return false;
        HistoricalTransaction that = (HistoricalTransaction) o;
        return id != null && id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
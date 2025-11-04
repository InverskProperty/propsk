package site.easy.to.build.crm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Unified Transaction DTO for Statement Generation
 *
 * Combines data from:
 * - HistoricalTransaction (pre-PayProp or non-PayProp properties)
 * - PayProp tables (payprop_report_all_payments, payprop_export_payments)
 *
 * This DTO provides a consistent interface for statement calculations
 * regardless of the underlying data source.
 */
public class StatementTransactionDto {

    // ===== CORE TRANSACTION FIELDS =====

    private LocalDate transactionDate;
    private BigDecimal amount;
    private String description;
    private String transactionType;  // payment, invoice, expense, fee, etc.
    private String category;
    private String subcategory;

    // ===== SOURCE IDENTIFICATION =====

    private TransactionSource source;  // HISTORICAL, PAYPROP
    private String sourceTransactionId;  // Original ID from source system
    private String accountSource;  // "Robert Ellis", "Propsk Old Account", "PayProp"

    // ===== PROPERTY AND CUSTOMER LINKS =====

    private Long propertyId;
    private String propertyPayPropId;
    private String propertyName;

    private Long customerId;  // Primary customer
    private String customerPayPropId;
    private String customerName;

    // ===== BENEFICIARY INFORMATION =====

    private Long beneficiaryId;
    private String beneficiaryPayPropId;
    private String beneficiaryName;
    private String beneficiaryType;  // agency, beneficiary, contractor, etc.

    // ===== TENANT INFORMATION =====

    private Long tenantId;
    private String tenantPayPropId;
    private String tenantName;

    // ===== OWNER INFORMATION =====

    private Long ownerId;
    private String ownerPayPropId;
    private String ownerName;

    // ===== COMMISSION AND FEE TRACKING =====

    private BigDecimal commissionRate;
    private BigDecimal commissionAmount;
    private BigDecimal serviceFeeRate;
    private BigDecimal serviceFeeAmount;
    private BigDecimal transactionFee;
    private BigDecimal netToOwnerAmount;

    // ===== PAYMENT BATCH TRACKING =====

    private String batchPaymentId;
    private String paypropBatchId;
    private LocalDate batchTransferDate;

    // ===== INCOMING TRANSACTION TRACKING =====

    private String incomingTransactionId;
    private BigDecimal incomingTransactionAmount;
    private LocalDate reconciliationDate;

    // ===== LEASE/INVOICE REFERENCE =====

    private Long invoiceId;
    private String leaseReference;
    private String paypropInvoiceId;

    // ===== BANK AND PAYMENT DETAILS =====

    private String bankReference;
    private String paymentMethod;
    private String reference;

    // ===== VAT AND TAX =====

    private Boolean vatApplicable;
    private BigDecimal vatAmount;
    private Boolean taxRelevant;

    // ===== STATUS FLAGS =====

    private Boolean reconciled;
    private LocalDate reconciledDate;
    private String status;

    // ===== METADATA =====

    private String notes;

    // ===== ENUMS =====

    public enum TransactionSource {
        HISTORICAL,  // From historical_transactions table
        PAYPROP      // From payprop_report_all_payments or payprop_export_* tables
    }

    // ===== CONSTRUCTORS =====

    public StatementTransactionDto() {
    }

    // ===== BUSINESS LOGIC METHODS =====

    /**
     * Check if this is a rent payment (incoming)
     */
    public boolean isRentPayment() {
        return "invoice".equalsIgnoreCase(transactionType) ||
               "payment".equalsIgnoreCase(transactionType) ||
               "incoming_payment".equalsIgnoreCase(transactionType) ||  // PayProp incoming payments
               "rent_received".equalsIgnoreCase(transactionType) ||     // Historical rent received
               "tenant_payment".equalsIgnoreCase(transactionType) ||    // Alternative tenant payment type
               "rent".equalsIgnoreCase(category);
    }

    /**
     * Check if this is an expense (outgoing)
     * payment_to_beneficiary with beneficiaryType='contractor' is an expense
     */
    public boolean isExpense() {
        return "expense".equalsIgnoreCase(transactionType) ||
               "maintenance".equalsIgnoreCase(transactionType) ||
               "payment_to_contractor".equalsIgnoreCase(transactionType) ||
               "contractor".equalsIgnoreCase(beneficiaryType);  // payment_to_beneficiary where beneficiary is contractor
    }

    /**
     * Check if this is an owner payment (money going to property owner)
     * payment_to_beneficiary with beneficiaryType='beneficiary' is an owner payment
     */
    public boolean isOwnerPayment() {
        return "beneficiary".equalsIgnoreCase(beneficiaryType);  // Only when beneficiary type is 'beneficiary' (owner)
    }

    /**
     * Check if this is an agency fee / commission
     */
    public boolean isAgencyFee() {
        return "fee".equalsIgnoreCase(transactionType) ||
               "commission_payment".equalsIgnoreCase(transactionType) ||  // PayProp/Historical commission
               "payment_to_agency".equalsIgnoreCase(transactionType) ||    // Payment to agency (commission)
               "agency".equalsIgnoreCase(beneficiaryType) ||
               "management_fee".equalsIgnoreCase(category) ||
               "commission".equalsIgnoreCase(category);
    }

    /**
     * Check if this is a PayProp transaction
     */
    public boolean isPayPropTransaction() {
        return source == TransactionSource.PAYPROP;
    }

    /**
     * Check if this is a historical transaction
     */
    public boolean isHistoricalTransaction() {
        return source == TransactionSource.HISTORICAL;
    }

    /**
     * Get absolute amount (always positive)
     */
    public BigDecimal getAbsoluteAmount() {
        return amount != null ? amount.abs() : BigDecimal.ZERO;
    }

    // ===== GETTERS AND SETTERS =====

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

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public void setSubcategory(String subcategory) {
        this.subcategory = subcategory;
    }

    public TransactionSource getSource() {
        return source;
    }

    public void setSource(TransactionSource source) {
        this.source = source;
    }

    public String getSourceTransactionId() {
        return sourceTransactionId;
    }

    public void setSourceTransactionId(String sourceTransactionId) {
        this.sourceTransactionId = sourceTransactionId;
    }

    public String getAccountSource() {
        return accountSource;
    }

    public void setAccountSource(String accountSource) {
        this.accountSource = accountSource;
    }

    public Long getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(Long propertyId) {
        this.propertyId = propertyId;
    }

    public String getPropertyPayPropId() {
        return propertyPayPropId;
    }

    public void setPropertyPayPropId(String propertyPayPropId) {
        this.propertyPayPropId = propertyPayPropId;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerPayPropId() {
        return customerPayPropId;
    }

    public void setCustomerPayPropId(String customerPayPropId) {
        this.customerPayPropId = customerPayPropId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public Long getBeneficiaryId() {
        return beneficiaryId;
    }

    public void setBeneficiaryId(Long beneficiaryId) {
        this.beneficiaryId = beneficiaryId;
    }

    public String getBeneficiaryPayPropId() {
        return beneficiaryPayPropId;
    }

    public void setBeneficiaryPayPropId(String beneficiaryPayPropId) {
        this.beneficiaryPayPropId = beneficiaryPayPropId;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public void setBeneficiaryName(String beneficiaryName) {
        this.beneficiaryName = beneficiaryName;
    }

    public String getBeneficiaryType() {
        return beneficiaryType;
    }

    public void setBeneficiaryType(String beneficiaryType) {
        this.beneficiaryType = beneficiaryType;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantPayPropId() {
        return tenantPayPropId;
    }

    public void setTenantPayPropId(String tenantPayPropId) {
        this.tenantPayPropId = tenantPayPropId;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerPayPropId() {
        return ownerPayPropId;
    }

    public void setOwnerPayPropId(String ownerPayPropId) {
        this.ownerPayPropId = ownerPayPropId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }

    public void setCommissionRate(BigDecimal commissionRate) {
        this.commissionRate = commissionRate;
    }

    public BigDecimal getCommissionAmount() {
        return commissionAmount;
    }

    public void setCommissionAmount(BigDecimal commissionAmount) {
        this.commissionAmount = commissionAmount;
    }

    public BigDecimal getServiceFeeRate() {
        return serviceFeeRate;
    }

    public void setServiceFeeRate(BigDecimal serviceFeeRate) {
        this.serviceFeeRate = serviceFeeRate;
    }

    public BigDecimal getServiceFeeAmount() {
        return serviceFeeAmount;
    }

    public void setServiceFeeAmount(BigDecimal serviceFeeAmount) {
        this.serviceFeeAmount = serviceFeeAmount;
    }

    public BigDecimal getTransactionFee() {
        return transactionFee;
    }

    public void setTransactionFee(BigDecimal transactionFee) {
        this.transactionFee = transactionFee;
    }

    public BigDecimal getNetToOwnerAmount() {
        return netToOwnerAmount;
    }

    public void setNetToOwnerAmount(BigDecimal netToOwnerAmount) {
        this.netToOwnerAmount = netToOwnerAmount;
    }

    public String getBatchPaymentId() {
        return batchPaymentId;
    }

    public void setBatchPaymentId(String batchPaymentId) {
        this.batchPaymentId = batchPaymentId;
    }

    public String getPaypropBatchId() {
        return paypropBatchId;
    }

    public void setPaypropBatchId(String paypropBatchId) {
        this.paypropBatchId = paypropBatchId;
    }

    public LocalDate getBatchTransferDate() {
        return batchTransferDate;
    }

    public void setBatchTransferDate(LocalDate batchTransferDate) {
        this.batchTransferDate = batchTransferDate;
    }

    public String getIncomingTransactionId() {
        return incomingTransactionId;
    }

    public void setIncomingTransactionId(String incomingTransactionId) {
        this.incomingTransactionId = incomingTransactionId;
    }

    public BigDecimal getIncomingTransactionAmount() {
        return incomingTransactionAmount;
    }

    public void setIncomingTransactionAmount(BigDecimal incomingTransactionAmount) {
        this.incomingTransactionAmount = incomingTransactionAmount;
    }

    public LocalDate getReconciliationDate() {
        return reconciliationDate;
    }

    public void setReconciliationDate(LocalDate reconciliationDate) {
        this.reconciliationDate = reconciliationDate;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(Long invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getLeaseReference() {
        return leaseReference;
    }

    public void setLeaseReference(String leaseReference) {
        this.leaseReference = leaseReference;
    }

    public String getPaypropInvoiceId() {
        return paypropInvoiceId;
    }

    public void setPaypropInvoiceId(String paypropInvoiceId) {
        this.paypropInvoiceId = paypropInvoiceId;
    }

    public String getBankReference() {
        return bankReference;
    }

    public void setBankReference(String bankReference) {
        this.bankReference = bankReference;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public Boolean getVatApplicable() {
        return vatApplicable;
    }

    public void setVatApplicable(Boolean vatApplicable) {
        this.vatApplicable = vatApplicable;
    }

    public BigDecimal getVatAmount() {
        return vatAmount;
    }

    public void setVatAmount(BigDecimal vatAmount) {
        this.vatAmount = vatAmount;
    }

    public Boolean getTaxRelevant() {
        return taxRelevant;
    }

    public void setTaxRelevant(Boolean taxRelevant) {
        this.taxRelevant = taxRelevant;
    }

    public Boolean getReconciled() {
        return reconciled;
    }

    public void setReconciled(Boolean reconciled) {
        this.reconciled = reconciled;
    }

    public LocalDate getReconciledDate() {
        return reconciledDate;
    }

    public void setReconciledDate(LocalDate reconciledDate) {
        this.reconciledDate = reconciledDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        return String.format("StatementTransactionDto{date=%s, amount=%s, type=%s, source=%s, property=%s, description='%s'}",
                           transactionDate, amount, transactionType, source, propertyName,
                           description != null && description.length() > 50 ? description.substring(0, 50) + "..." : description);
    }
}

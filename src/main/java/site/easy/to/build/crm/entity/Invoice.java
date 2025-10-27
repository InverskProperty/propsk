package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Invoice Entity - Local Invoice Instructions with PayProp Sync Capability
 * 
 * This entity represents local invoice instructions that can be created independently
 * and optionally synced to PayProp. Follows the same patterns as Customer and Property entities.
 */
@Entity
@Table(name = "invoices")
public class Invoice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // ===== PAYPROP SYNC FIELDS =====
    
    @Column(name = "payprop_id", length = 32, unique = true)
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "PayProp ID must be alphanumeric")
    private String paypropId;
    
    @Column(name = "payprop_customer_id", length = 32)
    @Pattern(regexp = "^[a-zA-Z0-9]+$")
    private String paypropCustomerId;
    
    @Column(name = "payprop_last_sync")
    private LocalDateTime paypropLastSync;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false)
    private SyncStatus syncStatus = SyncStatus.pending;
    
    @Column(name = "sync_error_message", columnDefinition = "TEXT")
    private String syncErrorMessage;
    
    // ===== RELATIONSHIP FIELDS =====
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedByUser;
    
    // ===== PAYPROP CATEGORY FIELDS =====
    
    @Column(name = "category_id", length = 32, nullable = false)
    @NotBlank(message = "Category is required")
    private String categoryId;
    
    @Column(name = "category_name", length = 100)
    private String categoryName;
    
    // ===== FINANCIAL FIELDS =====
    
    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "Amount cannot be negative")
    private BigDecimal amount;
    
    @Column(name = "vat_included", nullable = false)
    private Boolean vatIncluded = false;
    
    @Column(name = "vat_amount", precision = 10, scale = 2)
    @DecimalMin(value = "0.00", message = "VAT amount cannot be negative")
    private BigDecimal vatAmount;
    
    // Note: net_amount is calculated in database as generated column
    
    // ===== FREQUENCY AND SCHEDULING FIELDS =====
    
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private InvoiceFrequency frequency = InvoiceFrequency.monthly;
    
    @Column(name = "frequency_code", length = 10)
    private String frequencyCode;
    
    @Column(name = "payment_day")
    @Min(value = 1, message = "Payment day must be between 1 and 31")
    @Max(value = 31, message = "Payment day must be between 1 and 31")
    private Integer paymentDay;
    
    // ===== DATE RANGE FIELDS =====
    
    @Column(name = "start_date", nullable = false)
    @NotNull(message = "Start date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    
    @Column(name = "end_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
    
    // ===== INVOICE DETAILS =====
    
    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    @NotBlank(message = "Description is required")
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;
    
    @Column(name = "internal_reference", length = 100)
    private String internalReference;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    /**
     * Lease Reference - User-assigned unique identifier for this lease
     * Used for linking historical transactions to specific leases during import
     * Example: "LEASE-FLAT1-2024", "LEASE-APARTMENT40-2024"
     */
    @Column(name = "lease_reference", length = 100, unique = true)
    private String leaseReference;
    
    // ===== STATUS AND CONTROL FIELDS =====
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "is_debit_order", nullable = false)
    private Boolean isDebitOrder = false;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type")
    private AccountType accountType;
    
    // ===== AUDIT FIELDS =====
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    // ===== METADATA FIELDS =====
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @Column(name = "invoice_type", length = 50)
    private String invoiceType = "standard";
    
    // ===== CONSTRUCTORS =====
    
    public Invoice() {
        this.createdAt = LocalDateTime.now();
    }
    
    public Invoice(Customer customer, Property property, String categoryId, 
                   BigDecimal amount, InvoiceFrequency frequency, 
                   LocalDate startDate, String description) {
        this();
        this.customer = customer;
        this.property = property;
        this.categoryId = categoryId;
        this.amount = amount;
        this.frequency = frequency;
        this.startDate = startDate;
        this.description = description;
    }
    
    // ===== LIFECYCLE CALLBACKS =====
    
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        
        // Set frequency code for PayProp sync
        if (this.frequencyCode == null) {
            this.frequencyCode = this.frequency.name();
        }
        
        // Auto-set payment day for monthly/quarterly/yearly invoices
        if (this.paymentDay == null && this.frequency.requiresPaymentDay()) {
            this.paymentDay = 1; // Default to 1st of month
        }
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    // ===== BUSINESS LOGIC METHODS =====
    
    /**
     * Check if this invoice instruction is currently active
     */
    public boolean isCurrentlyActive() {
        if (!this.isActive || this.deletedAt != null) {
            return false;
        }
        
        LocalDate today = LocalDate.now();
        if (this.startDate.isAfter(today)) {
            return false;
        }
        
        return this.endDate == null || !this.endDate.isBefore(today);
    }
    
    /**
     * Check if this invoice needs PayProp sync
     */
    public boolean needsPayPropSync() {
        return this.paypropId == null && 
               this.syncStatus == SyncStatus.pending && 
               this.isCurrentlyActive();
    }
    
    /**
     * Mark invoice as successfully synced to PayProp
     */
    public void markSyncedToPayProp(String paypropId, String paypropCustomerId) {
        this.paypropId = paypropId;
        this.paypropCustomerId = paypropCustomerId;
        this.syncStatus = SyncStatus.synced;
        this.paypropLastSync = LocalDateTime.now();
        this.syncErrorMessage = null;
    }
    
    /**
     * Mark invoice sync as failed
     */
    public void markSyncFailed(String errorMessage) {
        this.syncStatus = SyncStatus.error;
        this.syncErrorMessage = errorMessage;
    }
    
    /**
     * Calculate next generation date based on frequency
     */
    public LocalDate calculateNextGenerationDate() {
        LocalDate today = LocalDate.now();
        
        return switch (this.frequency) {
            case one_time -> this.startDate; // One-time
            case daily -> today.plusDays(1); // Daily
            case weekly -> today.plusWeeks(1); // Weekly
            case monthly -> { // Monthly
                LocalDate nextMonth = today.plusMonths(1);
                yield nextMonth.withDayOfMonth(
                    Math.min(this.paymentDay != null ? this.paymentDay : 1,
                             nextMonth.lengthOfMonth())
                );
            }
            case quarterly -> today.plusMonths(3); // Quarterly
            case yearly -> today.plusYears(1); // Yearly
        };
    }
    
    // ===== GETTERS AND SETTERS =====
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getPaypropId() { return paypropId; }
    public void setPaypropId(String paypropId) { this.paypropId = paypropId; }
    
    public String getPaypropCustomerId() { return paypropCustomerId; }
    public void setPaypropCustomerId(String paypropCustomerId) { this.paypropCustomerId = paypropCustomerId; }
    
    public LocalDateTime getPaypropLastSync() { return paypropLastSync; }
    public void setPaypropLastSync(LocalDateTime paypropLastSync) { this.paypropLastSync = paypropLastSync; }
    
    public SyncStatus getSyncStatus() { return syncStatus; }
    public void setSyncStatus(SyncStatus syncStatus) { this.syncStatus = syncStatus; }
    
    public String getSyncErrorMessage() { return syncErrorMessage; }
    public void setSyncErrorMessage(String syncErrorMessage) { this.syncErrorMessage = syncErrorMessage; }
    
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    
    public Property getProperty() { return property; }
    public void setProperty(Property property) { this.property = property; }
    
    public User getCreatedByUser() { return createdByUser; }
    public void setCreatedByUser(User createdByUser) { this.createdByUser = createdByUser; }
    
    public User getUpdatedByUser() { return updatedByUser; }
    public void setUpdatedByUser(User updatedByUser) { this.updatedByUser = updatedByUser; }
    
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    
    public Boolean getVatIncluded() { return vatIncluded; }
    public void setVatIncluded(Boolean vatIncluded) { this.vatIncluded = vatIncluded; }
    
    public BigDecimal getVatAmount() { return vatAmount; }
    public void setVatAmount(BigDecimal vatAmount) { this.vatAmount = vatAmount; }
    
    public InvoiceFrequency getFrequency() { return frequency; }
    public void setFrequency(InvoiceFrequency frequency) { this.frequency = frequency; }
    
    public String getFrequencyCode() { return frequencyCode; }
    public void setFrequencyCode(String frequencyCode) { this.frequencyCode = frequencyCode; }
    
    public Integer getPaymentDay() { return paymentDay; }
    public void setPaymentDay(Integer paymentDay) { this.paymentDay = paymentDay; }
    
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getInternalReference() { return internalReference; }
    public void setInternalReference(String internalReference) { this.internalReference = internalReference; }
    
    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String externalReference) { this.externalReference = externalReference; }

    public String getLeaseReference() { return leaseReference; }
    public void setLeaseReference(String leaseReference) { this.leaseReference = leaseReference; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    public Boolean getIsDebitOrder() { return isDebitOrder; }
    public void setIsDebitOrder(Boolean isDebitOrder) { this.isDebitOrder = isDebitOrder; }
    
    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public String getInvoiceType() { return invoiceType; }
    public void setInvoiceType(String invoiceType) { this.invoiceType = invoiceType; }
    
    // ===== ENUMS =====
    
    public enum SyncStatus {
        pending,    // Not yet synced to PayProp
        synced,     // Successfully synced to PayProp
        error,      // Sync failed with error
        manual      // Manually managed, no auto sync
    }
    
    public enum InvoiceFrequency {
        one_time("One-time", false),
        weekly("Weekly", false),
        monthly("Monthly", true),
        quarterly("Quarterly", true),
        yearly("Yearly", true),
        daily("Daily", false);

        private final String displayName;
        private final boolean requiresPaymentDay;

        InvoiceFrequency(String displayName, boolean requiresPaymentDay) {
            this.displayName = displayName;
            this.requiresPaymentDay = requiresPaymentDay;
        }

        public String getDisplayName() { return displayName; }
        public boolean requiresPaymentDay() { return requiresPaymentDay; }
    }
    
    // ===== toString, equals, hashCode =====
    
    @Override
    public String toString() {
        return String.format("Invoice{id=%d, customer='%s', property='%s', amount=%s, frequency=%s, description='%s'}", 
                           id, 
                           customer != null ? customer.getName() : null,
                           property != null ? property.getPropertyName() : null,
                           amount, frequency, description);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Invoice)) return false;
        Invoice invoice = (Invoice) o;
        return id != null && id.equals(invoice.id);
    }
    
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
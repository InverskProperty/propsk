package site.easy.to.build.crm.service.invoice;

import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.Invoice.SyncStatus;
import site.easy.to.build.crm.entity.Invoice.InvoiceFrequency;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Invoice Service Interface - Business logic layer for invoice management
 * Follows patterns from CustomerService and PropertyService
 */
public interface InvoiceService {
    
    // ===== BASIC CRUD OPERATIONS =====
    
    /**
     * Save an invoice (create or update)
     */
    Invoice save(Invoice invoice);
    
    /**
     * Find invoice by ID
     */
    Optional<Invoice> findById(Long id);
    
    /**
     * Find all invoices (active only by default)
     */
    List<Invoice> findAll();
    
    /**
     * Find all invoices with pagination
     */
    Page<Invoice> findAll(Pageable pageable);
    
    /**
     * Delete invoice (soft delete)
     */
    void delete(Long id);
    
    /**
     * Delete invoice (soft delete)
     */
    void delete(Invoice invoice);
    
    /**
     * Hard delete invoice (use with caution)
     */
    void hardDelete(Long id);
    
    /**
     * Check if invoice exists
     */
    boolean existsById(Long id);
    
    // ===== CREATION METHODS =====
    
    /**
     * Create a new invoice instruction
     */
    Invoice createInvoice(InvoiceCreationRequest request);
    
    /**
     * Create a rent invoice for a tenant
     */
    Invoice createRentInvoice(Customer tenant, Property property, BigDecimal amount, 
                             LocalDate startDate, Integer paymentDay);
    
    /**
     * Create a deposit invoice for a tenant
     */
    Invoice createDepositInvoice(Customer tenant, Property property, BigDecimal amount, 
                               LocalDate startDate);
    
    /**
     * Create a utilities invoice
     */
    Invoice createUtilitiesInvoice(Customer customer, Property property, BigDecimal amount, 
                                  InvoiceFrequency frequency, LocalDate startDate, Integer paymentDay);
    
    /**
     * Duplicate an existing invoice with modifications
     */
    Invoice duplicateInvoice(Long invoiceId, InvoiceModificationRequest modifications);
    
    // ===== FINDERS BY RELATIONSHIP =====
    
    /**
     * Find all invoices for a customer
     */
    List<Invoice> findByCustomer(Customer customer);
    
    /**
     * Find all invoices for a property
     */
    List<Invoice> findByProperty(Property property);
    
    /**
     * Find invoices for a customer-property combination
     */
    List<Invoice> findByCustomerAndProperty(Customer customer, Property property);
    
    /**
     * Find invoices created by a specific user
     */
    Page<Invoice> findByCreatedByUser(Long userId, Pageable pageable);
    
    // ===== ACTIVE AND STATUS FINDERS =====
    
    /**
     * Find currently active invoices
     */
    List<Invoice> findActiveInvoices();
    
    /**
     * Find active invoices for a customer
     */
    List<Invoice> findActiveInvoicesForCustomer(Customer customer);
    
    /**
     * Find active invoices for a property
     */
    List<Invoice> findActiveInvoicesForProperty(Property property);
    
    /**
     * Find invoices by sync status
     */
    List<Invoice> findBySyncStatus(SyncStatus syncStatus);
    
    /**
     * Find invoices needing PayProp sync
     */
    List<Invoice> findInvoicesNeedingSync();
    
    /**
     * Find invoices with sync errors
     */
    List<Invoice> findSyncErrorInvoices();
    
    // ===== FREQUENCY AND SCHEDULING =====
    
    /**
     * Find monthly invoices due on a specific day
     */
    List<Invoice> findMonthlyInvoicesByPaymentDay(Integer paymentDay);
    
    /**
     * Find recurring invoices (not one-time)
     */
    List<Invoice> findRecurringInvoices();
    
    /**
     * Find invoices by frequency
     */
    List<Invoice> findByFrequency(InvoiceFrequency frequency);
    
    /**
     * Calculate next generation date for an invoice
     */
    LocalDate calculateNextGenerationDate(Invoice invoice);
    
    /**
     * Find invoices ready for generation today
     */
    List<Invoice> findInvoicesReadyForGeneration(LocalDate date);
    
    // ===== CATEGORY AND AMOUNT OPERATIONS =====
    
    /**
     * Find invoices by category
     */
    List<Invoice> findByCategoryId(String categoryId);
    
    /**
     * Find rent invoices
     */
    List<Invoice> findRentInvoices();
    
    /**
     * Get total invoice amount for a customer
     */
    BigDecimal getTotalAmountByCustomer(Customer customer);
    
    /**
     * Get total invoice amount for a property
     */
    BigDecimal getTotalAmountByProperty(Property property);
    
    // ===== SEARCH AND FILTERING =====
    
    /**
     * Search invoices by keyword
     */
    Page<Invoice> searchInvoices(String keyword, Pageable pageable);
    
    /**
     * Find invoices by date range
     */
    List<Invoice> findByDateRange(LocalDate startDate, LocalDate endDate);
    
    /**
     * Find invoices ending soon (within specified days)
     */
    List<Invoice> findInvoicesEndingSoon(int daysAhead);
    
    /**
     * Find potential duplicate invoices
     */
    List<Invoice> findPotentialDuplicates();
    
    // ===== BUSINESS LOGIC OPERATIONS =====
    
    /**
     * Activate an invoice
     */
    void activateInvoice(Long invoiceId);
    
    /**
     * Deactivate an invoice
     */
    void deactivateInvoice(Long invoiceId);
    
    /**
     * Update invoice end date
     */
    void updateEndDate(Long invoiceId, LocalDate endDate);
    
    /**
     * Update invoice amount
     */
    void updateAmount(Long invoiceId, BigDecimal newAmount);
    
    /**
     * Mark invoice as synced to PayProp
     */
    void markSyncedToPayProp(Long invoiceId, String paypropId, String paypropCustomerId);
    
    /**
     * Mark invoice sync as failed
     */
    void markSyncFailed(Long invoiceId, String errorMessage);
    
    /**
     * Reset sync status to pending
     */
    void resetSyncStatus(Long invoiceId);
    
    /**
     * Set invoice to manual sync mode
     */
    void setManualSync(Long invoiceId);
    
    // ===== VALIDATION METHODS =====
    
    /**
     * Validate invoice creation request
     */
    void validateInvoiceCreation(InvoiceCreationRequest request);
    
    /**
     * Check if invoice can be created for customer-property combination
     */
    boolean canCreateInvoiceFor(Customer customer, Property property);
    
    /**
     * Validate PayProp sync requirements
     */
    boolean isPayPropSyncReady(Invoice invoice);
    
    /**
     * Check for duplicate invoices
     */
    boolean hasDuplicateInvoice(Customer customer, Property property, 
                              String categoryId, BigDecimal amount, InvoiceFrequency frequency);
    
    // ===== STATISTICS AND REPORTING =====
    
    /**
     * Count active invoices
     */
    long countActiveInvoices();
    
    /**
     * Count invoices by sync status
     */
    long countBySyncStatus(SyncStatus syncStatus);
    
    /**
     * Count invoices for a customer
     */
    long countByCustomer(Customer customer);
    
    /**
     * Count invoices for a property
     */
    long countByProperty(Property property);
    
    /**
     * Get invoice statistics
     */
    InvoiceStatistics getInvoiceStatistics();
    
    /**
     * Get sync status summary
     */
    SyncStatusSummary getSyncStatusSummary();
    
    // ===== BULK OPERATIONS =====
    
    /**
     * Bulk activate invoices
     */
    int bulkActivateInvoices(List<Long> invoiceIds);
    
    /**
     * Bulk deactivate invoices
     */
    int bulkDeactivateInvoices(List<Long> invoiceIds);
    
    /**
     * Bulk delete invoices
     */
    int bulkDeleteInvoices(List<Long> invoiceIds);
    
    /**
     * Bulk update end dates
     */
    int bulkUpdateEndDates(List<Long> invoiceIds, LocalDate endDate);
    
    // ===== PAYPROP INTEGRATION SUPPORT =====
    
    /**
     * Prepare invoice for PayProp sync
     */
    void prepareForPayPropSync(Invoice invoice);
    
    /**
     * Convert invoice to PayProp format
     */
    Object convertToPayPropFormat(Invoice invoice);
    
    /**
     * Find invoice by PayProp ID
     */
    Optional<Invoice> findByPayPropId(String paypropId);
    
    /**
     * Get invoices requiring sync retry
     */
    List<Invoice> getInvoicesRequiringSyncRetry();
    
    // ===== INNER CLASSES AND DTOs =====
    
    /**
     * Invoice creation request DTO
     */
    class InvoiceCreationRequest {
        private Long customerId;
        private Long propertyId;
        private String categoryId;
        private BigDecimal amount;
        private InvoiceFrequency frequency;
        private Integer paymentDay;
        private LocalDate startDate;
        private LocalDate endDate;
        private String description;
        private Boolean vatIncluded;
        private BigDecimal vatAmount;
        private String notes;
        private Boolean syncToPayProp;
        
        // Getters and setters
        public Long getCustomerId() { return customerId; }
        public void setCustomerId(Long customerId) { this.customerId = customerId; }
        
        public Long getPropertyId() { return propertyId; }
        public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }
        
        public String getCategoryId() { return categoryId; }
        public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
        
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        
        public InvoiceFrequency getFrequency() { return frequency; }
        public void setFrequency(InvoiceFrequency frequency) { this.frequency = frequency; }
        
        public Integer getPaymentDay() { return paymentDay; }
        public void setPaymentDay(Integer paymentDay) { this.paymentDay = paymentDay; }
        
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public Boolean getVatIncluded() { return vatIncluded; }
        public void setVatIncluded(Boolean vatIncluded) { this.vatIncluded = vatIncluded; }
        
        public BigDecimal getVatAmount() { return vatAmount; }
        public void setVatAmount(BigDecimal vatAmount) { this.vatAmount = vatAmount; }
        
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        
        public Boolean getSyncToPayProp() { return syncToPayProp; }
        public void setSyncToPayProp(Boolean syncToPayProp) { this.syncToPayProp = syncToPayProp; }
    }
    
    /**
     * Invoice modification request for duplication
     */
    class InvoiceModificationRequest {
        private BigDecimal newAmount;
        private InvoiceFrequency newFrequency;
        private LocalDate newStartDate;
        private LocalDate newEndDate;
        private String newDescription;
        private Integer newPaymentDay;
        
        // Getters and setters
        public BigDecimal getNewAmount() { return newAmount; }
        public void setNewAmount(BigDecimal newAmount) { this.newAmount = newAmount; }
        
        public InvoiceFrequency getNewFrequency() { return newFrequency; }
        public void setNewFrequency(InvoiceFrequency newFrequency) { this.newFrequency = newFrequency; }
        
        public LocalDate getNewStartDate() { return newStartDate; }
        public void setNewStartDate(LocalDate newStartDate) { this.newStartDate = newStartDate; }
        
        public LocalDate getNewEndDate() { return newEndDate; }
        public void setNewEndDate(LocalDate newEndDate) { this.newEndDate = newEndDate; }
        
        public String getNewDescription() { return newDescription; }
        public void setNewDescription(String newDescription) { this.newDescription = newDescription; }
        
        public Integer getNewPaymentDay() { return newPaymentDay; }
        public void setNewPaymentDay(Integer newPaymentDay) { this.newPaymentDay = newPaymentDay; }
    }
    
    /**
     * Invoice statistics summary
     */
    class InvoiceStatistics {
        private long totalInvoices;
        private long activeInvoices;
        private long inactiveInvoices;
        private BigDecimal totalAmount;
        private BigDecimal activeAmount;
        private long recurringInvoices;
        private long oneTimeInvoices;
        
        // Getters and setters
        public long getTotalInvoices() { return totalInvoices; }
        public void setTotalInvoices(long totalInvoices) { this.totalInvoices = totalInvoices; }
        
        public long getActiveInvoices() { return activeInvoices; }
        public void setActiveInvoices(long activeInvoices) { this.activeInvoices = activeInvoices; }
        
        public long getInactiveInvoices() { return inactiveInvoices; }
        public void setInactiveInvoices(long inactiveInvoices) { this.inactiveInvoices = inactiveInvoices; }
        
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        
        public BigDecimal getActiveAmount() { return activeAmount; }
        public void setActiveAmount(BigDecimal activeAmount) { this.activeAmount = activeAmount; }
        
        public long getRecurringInvoices() { return recurringInvoices; }
        public void setRecurringInvoices(long recurringInvoices) { this.recurringInvoices = recurringInvoices; }
        
        public long getOneTimeInvoices() { return oneTimeInvoices; }
        public void setOneTimeInvoices(long oneTimeInvoices) { this.oneTimeInvoices = oneTimeInvoices; }
    }
    
    /**
     * Sync status summary
     */
    class SyncStatusSummary {
        private long pendingSync;
        private long synced;
        private long syncErrors;
        private long manualSync;
        
        // Getters and setters
        public long getPendingSync() { return pendingSync; }
        public void setPendingSync(long pendingSync) { this.pendingSync = pendingSync; }
        
        public long getSynced() { return synced; }
        public void setSynced(long synced) { this.synced = synced; }
        
        public long getSyncErrors() { return syncErrors; }
        public void setSyncErrors(long syncErrors) { this.syncErrors = syncErrors; }
        
        public long getManualSync() { return manualSync; }
        public void setManualSync(long manualSync) { this.manualSync = manualSync; }
    }
}
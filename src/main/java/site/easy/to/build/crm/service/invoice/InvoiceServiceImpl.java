package site.easy.to.build.crm.service.invoice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.entity.Invoice.SyncStatus;
import site.easy.to.build.crm.entity.Invoice.InvoiceFrequency;
import site.easy.to.build.crm.entity.AccountType;
import site.easy.to.build.crm.repository.InvoiceRepository;
import site.easy.to.build.crm.repository.UserRepository;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Invoice Service Implementation - Complete business logic for invoice management
 * Follows patterns from CustomerServiceImpl and PropertyServiceImpl
 */
@Service
@Transactional
public class InvoiceServiceImpl implements InvoiceService {
    
    private static final Logger log = LoggerFactory.getLogger(InvoiceServiceImpl.class);
    
    @Autowired
    private InvoiceRepository invoiceRepository;
    
    @Autowired
    private CustomerService customerService;
    
    @Autowired
    private PropertyService propertyService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private AuthenticationUtils authenticationUtils;
    
    // ===== BASIC CRUD OPERATIONS =====
    
    @Override
    public Invoice save(Invoice invoice) {
        log.debug("Saving invoice: {}", invoice);
        return invoiceRepository.save(invoice);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<Invoice> findById(Long id) {
        return invoiceRepository.findById(id);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findAll() {
        return invoiceRepository.findCurrentlyActiveInvoices(LocalDate.now());
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<Invoice> findAll(Pageable pageable) {
        return invoiceRepository.findActiveInvoices(pageable);
    }
    
    @Override
    public void delete(Long id) {
        Optional<Invoice> invoiceOpt = invoiceRepository.findById(id);
        if (invoiceOpt.isPresent()) {
            delete(invoiceOpt.get());
        } else {
            throw new IllegalArgumentException("Invoice not found with ID: " + id);
        }
    }
    
    @Override
    public void delete(Invoice invoice) {
        log.info("Soft deleting invoice: ID {}, Description: '{}'", 
                invoice.getId(), invoice.getDescription());
        invoice.setDeletedAt(LocalDateTime.now());
        invoice.setIsActive(false);
        invoiceRepository.save(invoice);
    }
    
    @Override
    public void hardDelete(Long id) {
        log.warn("Hard deleting invoice: ID {}", id);
        invoiceRepository.deleteById(id);
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return invoiceRepository.existsById(id);
    }
    
    // ===== CREATION METHODS =====
    
    @Override
    public Invoice createInvoice(InvoiceCreationRequest request) {
        log.info("Creating invoice for customer ID {} and property ID {}", 
                request.getCustomerId(), request.getPropertyId());
        
        // Validate the request
        validateInvoiceCreation(request);
        
        // Get entities
        Customer customer = customerService.findByCustomerId(request.getCustomerId());
        if (customer == null) {
            throw new IllegalArgumentException("Customer not found: " + request.getCustomerId());
        }
        
        Property property = propertyService.findById(request.getPropertyId());
        if (property == null) {
            throw new IllegalArgumentException("Property not found: " + request.getPropertyId());
        }
        
        // Get current user
        // TODO: Fix auth handling - using default user for now
        User currentUser = null; // TODO: Fix auth - temporarily disabled
        
        // Check for duplicates
        if (hasDuplicateInvoice(customer, property, request.getCategoryId(), 
                              request.getAmount(), request.getFrequency())) {
            throw new IllegalArgumentException(
                "Duplicate invoice already exists for this customer, property, and category");
        }
        
        // Create invoice
        Invoice invoice = new Invoice(customer, property, request.getCategoryId(),
                                    request.getAmount(), request.getFrequency(),
                                    request.getStartDate(), request.getDescription());
        
        // Set additional fields
        invoice.setCreatedByUser(currentUser);
        invoice.setEndDate(request.getEndDate());
        invoice.setPaymentDay(request.getPaymentDay());
        invoice.setVatIncluded(request.getVatIncluded() != null ? request.getVatIncluded() : false);
        invoice.setVatAmount(request.getVatAmount());
        invoice.setNotes(request.getNotes());
        
        // Set account type from customer
        invoice.setAccountType(customer.getAccountType());
        
        // Set sync status based on request
        if (request.getSyncToPayProp() != null && !request.getSyncToPayProp()) {
            invoice.setSyncStatus(SyncStatus.manual);
        } else {
            invoice.setSyncStatus(SyncStatus.pending);
        }
        
        // Save invoice
        Invoice savedInvoice = invoiceRepository.save(invoice);

        // Auto-generate uniform lease reference if not already set
        if (savedInvoice.getLeaseReference() == null || savedInvoice.getLeaseReference().isEmpty()) {
            savedInvoice.setLeaseReference("LEASE-" + savedInvoice.getId());
            savedInvoice = invoiceRepository.save(savedInvoice);
            log.info("Generated lease reference: {}", savedInvoice.getLeaseReference());
        }

        log.info("Created invoice: ID {}, Lease Ref: {}, Amount: {}, Description: '{}'",
                savedInvoice.getId(), savedInvoice.getLeaseReference(),
                savedInvoice.getAmount(), savedInvoice.getDescription());

        return savedInvoice;
    }
    
    @Override
    public Invoice createRentInvoice(Customer tenant, Property property, BigDecimal amount, 
                                   LocalDate startDate, Integer paymentDay) {
        InvoiceCreationRequest request = new InvoiceCreationRequest();
        request.setCustomerId(tenant.getCustomerId().longValue());
        request.setPropertyId(property.getId());
        request.setCategoryId("rent");
        request.setAmount(amount);
        request.setFrequency(InvoiceFrequency.monthly);
        request.setPaymentDay(paymentDay != null ? paymentDay : 1);
        request.setStartDate(startDate);
        request.setDescription("Monthly rent for " + property.getPropertyName());
        request.setSyncToPayProp(true);
        
        return createInvoice(request);
    }
    
    @Override
    public Invoice createDepositInvoice(Customer tenant, Property property, BigDecimal amount, 
                                      LocalDate startDate) {
        InvoiceCreationRequest request = new InvoiceCreationRequest();
        request.setCustomerId(tenant.getCustomerId().longValue());
        request.setPropertyId(property.getId());
        request.setCategoryId("deposit");
        request.setAmount(amount);
        request.setFrequency(InvoiceFrequency.one_time); // One-time
        request.setStartDate(startDate);
        request.setDescription("Security deposit for " + property.getPropertyName());
        request.setSyncToPayProp(true);
        
        return createInvoice(request);
    }
    
    @Override
    public Invoice createUtilitiesInvoice(Customer customer, Property property, BigDecimal amount, 
                                        InvoiceFrequency frequency, LocalDate startDate, Integer paymentDay) {
        InvoiceCreationRequest request = new InvoiceCreationRequest();
        request.setCustomerId(customer.getCustomerId().longValue());
        request.setPropertyId(property.getId());
        request.setCategoryId("utilities");
        request.setAmount(amount);
        request.setFrequency(frequency);
        request.setPaymentDay(paymentDay);
        request.setStartDate(startDate);
        request.setDescription("Utilities for " + property.getPropertyName());
        request.setSyncToPayProp(true);
        
        return createInvoice(request);
    }
    
    @Override
    public Invoice duplicateInvoice(Long invoiceId, InvoiceModificationRequest modifications) {
        Optional<Invoice> originalOpt = invoiceRepository.findById(invoiceId);
        if (originalOpt.isEmpty()) {
            throw new IllegalArgumentException("Invoice not found: " + invoiceId);
        }
        
        Invoice original = originalOpt.get();
        
        InvoiceCreationRequest request = new InvoiceCreationRequest();
        request.setCustomerId(original.getCustomer().getCustomerId().longValue());
        request.setPropertyId(original.getProperty().getId());
        request.setCategoryId(original.getCategoryId());
        
        // Apply modifications or use original values
        request.setAmount(modifications.getNewAmount() != null ? 
                         modifications.getNewAmount() : original.getAmount());
        request.setFrequency(modifications.getNewFrequency() != null ? 
                           modifications.getNewFrequency() : original.getFrequency());
        request.setStartDate(modifications.getNewStartDate() != null ? 
                           modifications.getNewStartDate() : original.getStartDate());
        request.setEndDate(modifications.getNewEndDate());
        request.setDescription(modifications.getNewDescription() != null ? 
                             modifications.getNewDescription() : original.getDescription() + " (Copy)");
        request.setPaymentDay(modifications.getNewPaymentDay() != null ? 
                            modifications.getNewPaymentDay() : original.getPaymentDay());
        
        request.setVatIncluded(original.getVatIncluded());
        request.setVatAmount(original.getVatAmount());
        request.setSyncToPayProp(true);
        
        return createInvoice(request);
    }
    
    // ===== FINDERS BY RELATIONSHIP =====
    
    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findByCustomer(Customer customer) {
        return invoiceRepository.findByCustomer(customer);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findByProperty(Property property) {
        return invoiceRepository.findByProperty(property);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findByCustomerAndProperty(Customer customer, Property property) {
        return invoiceRepository.findByCustomerAndProperty(customer, property);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<Invoice> findByCreatedByUser(Long userId, Pageable pageable) {
        return invoiceRepository.findByCreatedByUserId(userId, pageable);
    }
    
    // ===== ACTIVE AND STATUS FINDERS =====
    
    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findActiveInvoices() {
        return invoiceRepository.findCurrentlyActiveInvoices(LocalDate.now());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findActiveInvoicesForCustomer(Customer customer) {
        return invoiceRepository.findActiveInvoicesForCustomer(customer, LocalDate.now());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findActiveInvoicesForProperty(Property property) {
        return invoiceRepository.findActiveInvoicesForProperty(property, LocalDate.now());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findBySyncStatus(SyncStatus syncStatus) {
        return invoiceRepository.findBySyncStatus(syncStatus);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findInvoicesNeedingSync() {
        return invoiceRepository.findInvoicesNeedingSync(LocalDate.now());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findSyncErrorInvoices() {
        return invoiceRepository.findSyncErrorInvoices();
    }
    
    // ===== FREQUENCY AND SCHEDULING =====
    
    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findMonthlyInvoicesByPaymentDay(Integer paymentDay) {
        return invoiceRepository.findMonthlyInvoicesByPaymentDay(paymentDay, LocalDate.now());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findRecurringInvoices() {
        return invoiceRepository.findRecurringInvoices();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findByFrequency(InvoiceFrequency frequency) {
        return invoiceRepository.findByFrequency(frequency);
    }
    
    @Override
    @Transactional(readOnly = true)
    public LocalDate calculateNextGenerationDate(Invoice invoice) {
        return invoice.calculateNextGenerationDate();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findInvoicesReadyForGeneration(LocalDate date) {
        // This would need custom logic to determine which invoices should generate on the given date
        // For now, return active invoices that started before or on the date
        return invoiceRepository.findCurrentlyActiveInvoices(date)
                .stream()
                .filter(invoice -> !invoice.getStartDate().isAfter(date))
                .toList();
    }
    
    // ===== CATEGORY AND AMOUNT OPERATIONS =====
    
    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findByCategoryId(String categoryId) {
        return invoiceRepository.findActiveByCategoryId(categoryId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findRentInvoices() {
        return invoiceRepository.findRentInvoices();
    }
    
    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalAmountByCustomer(Customer customer) {
        BigDecimal total = invoiceRepository.getTotalAmountByCustomer(customer);
        return total != null ? total : BigDecimal.ZERO;
    }
    
    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalAmountByProperty(Property property) {
        BigDecimal total = invoiceRepository.getTotalAmountByProperty(property);
        return total != null ? total : BigDecimal.ZERO;
    }
    
    // ===== SEARCH AND FILTERING =====
    
    @Override
    @Transactional(readOnly = true)
    public Page<Invoice> searchInvoices(String keyword, Pageable pageable) {
        return invoiceRepository.searchInvoices(keyword, pageable);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return invoiceRepository.findByStartDateBetween(startDate, endDate);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findInvoicesEndingSoon(int daysAhead) {
        LocalDate today = LocalDate.now();
        LocalDate futureDate = today.plusDays(daysAhead);
        return invoiceRepository.findInvoicesEndingSoon(today, futureDate);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findPotentialDuplicates() {
        return invoiceRepository.findPotentialDuplicates();
    }
    
    // ===== BUSINESS LOGIC OPERATIONS =====
    
    @Override
    public void activateInvoice(Long invoiceId) {
        Optional<Invoice> invoiceOpt = invoiceRepository.findById(invoiceId);
        if (invoiceOpt.isPresent()) {
            Invoice invoice = invoiceOpt.get();
            invoice.setIsActive(true);
            invoiceRepository.save(invoice);
            log.info("Activated invoice: ID {}", invoiceId);
        } else {
            throw new IllegalArgumentException("Invoice not found: " + invoiceId);
        }
    }
    
    @Override
    public void deactivateInvoice(Long invoiceId) {
        Optional<Invoice> invoiceOpt = invoiceRepository.findById(invoiceId);
        if (invoiceOpt.isPresent()) {
            Invoice invoice = invoiceOpt.get();
            invoice.setIsActive(false);
            invoiceRepository.save(invoice);
            log.info("Deactivated invoice: ID {}", invoiceId);
        } else {
            throw new IllegalArgumentException("Invoice not found: " + invoiceId);
        }
    }
    
    @Override
    public void updateEndDate(Long invoiceId, LocalDate endDate) {
        Optional<Invoice> invoiceOpt = invoiceRepository.findById(invoiceId);
        if (invoiceOpt.isPresent()) {
            Invoice invoice = invoiceOpt.get();
            invoice.setEndDate(endDate);
            invoiceRepository.save(invoice);
            log.info("Updated end date for invoice ID {}: {}", invoiceId, endDate);
        } else {
            throw new IllegalArgumentException("Invoice not found: " + invoiceId);
        }
    }
    
    @Override
    public void updateAmount(Long invoiceId, BigDecimal newAmount) {
        Optional<Invoice> invoiceOpt = invoiceRepository.findById(invoiceId);
        if (invoiceOpt.isPresent()) {
            Invoice invoice = invoiceOpt.get();
            BigDecimal oldAmount = invoice.getAmount();
            invoice.setAmount(newAmount);
            invoiceRepository.save(invoice);
            log.info("Updated amount for invoice ID {}: {} -> {}", invoiceId, oldAmount, newAmount);
        } else {
            throw new IllegalArgumentException("Invoice not found: " + invoiceId);
        }
    }
    
    @Override
    public void markSyncedToPayProp(Long invoiceId, String paypropId, String paypropCustomerId) {
        Optional<Invoice> invoiceOpt = invoiceRepository.findById(invoiceId);
        if (invoiceOpt.isPresent()) {
            Invoice invoice = invoiceOpt.get();
            invoice.markSyncedToPayProp(paypropId, paypropCustomerId);
            invoiceRepository.save(invoice);
            log.info("Marked invoice ID {} as synced to PayProp: {}", invoiceId, paypropId);
        } else {
            throw new IllegalArgumentException("Invoice not found: " + invoiceId);
        }
    }
    
    @Override
    public void markSyncFailed(Long invoiceId, String errorMessage) {
        Optional<Invoice> invoiceOpt = invoiceRepository.findById(invoiceId);
        if (invoiceOpt.isPresent()) {
            Invoice invoice = invoiceOpt.get();
            invoice.markSyncFailed(errorMessage);
            invoiceRepository.save(invoice);
            log.warn("Marked invoice ID {} as sync failed: {}", invoiceId, errorMessage);
        } else {
            throw new IllegalArgumentException("Invoice not found: " + invoiceId);
        }
    }
    
    @Override
    public void resetSyncStatus(Long invoiceId) {
        Optional<Invoice> invoiceOpt = invoiceRepository.findById(invoiceId);
        if (invoiceOpt.isPresent()) {
            Invoice invoice = invoiceOpt.get();
            invoice.setSyncStatus(SyncStatus.pending);
            invoice.setSyncErrorMessage(null);
            invoiceRepository.save(invoice);
            log.info("Reset sync status for invoice ID {}", invoiceId);
        } else {
            throw new IllegalArgumentException("Invoice not found: " + invoiceId);
        }
    }
    
    @Override
    public void setManualSync(Long invoiceId) {
        Optional<Invoice> invoiceOpt = invoiceRepository.findById(invoiceId);
        if (invoiceOpt.isPresent()) {
            Invoice invoice = invoiceOpt.get();
            invoice.setSyncStatus(SyncStatus.manual);
            invoice.setSyncErrorMessage(null);
            invoiceRepository.save(invoice);
            log.info("Set manual sync mode for invoice ID {}", invoiceId);
        } else {
            throw new IllegalArgumentException("Invoice not found: " + invoiceId);
        }
    }
    
    // ===== VALIDATION METHODS =====
    
    @Override
    public void validateInvoiceCreation(InvoiceCreationRequest request) {
        if (request.getCustomerId() == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        if (request.getPropertyId() == null) {
            throw new IllegalArgumentException("Property ID is required");
        }
        if (request.getCategoryId() == null || request.getCategoryId().trim().isEmpty()) {
            throw new IllegalArgumentException("Category ID is required");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (request.getFrequency() == null) {
            throw new IllegalArgumentException("Frequency is required");
        }
        if (request.getStartDate() == null) {
            throw new IllegalArgumentException("Start date is required");
        }
        if (request.getDescription() == null || request.getDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("Description is required");
        }
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        
        // Frequency-specific validations
        if ((request.getFrequency() == InvoiceFrequency.monthly ||
             request.getFrequency() == InvoiceFrequency.quarterly ||
             request.getFrequency() == InvoiceFrequency.yearly) &&
            request.getPaymentDay() == null) {
            throw new IllegalArgumentException("Payment day is required for monthly, quarterly, and yearly invoices");
        }
        
        if (request.getPaymentDay() != null && (request.getPaymentDay() < 1 || request.getPaymentDay() > 31)) {
            throw new IllegalArgumentException("Payment day must be between 1 and 31");
        }
        
        if (request.getVatAmount() != null && request.getVatAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("VAT amount cannot be negative");
        }
        
        log.debug("Invoice creation request validation passed");
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean canCreateInvoiceFor(Customer customer, Property property) {
        // Check if customer and property exist and are valid
        if (customer == null || property == null) {
            return false;
        }
        
        // Add any business rules here
        // For example, check if customer is active, property is available, etc.
        return true;
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean isPayPropSyncReady(Invoice invoice) {
        return invoice != null && 
               invoice.getCustomer() != null && 
               invoice.getProperty() != null && 
               invoice.getCategoryId() != null && 
               invoice.getAmount() != null && 
               invoice.getFrequency() != null &&
               invoice.getStartDate() != null &&
               invoice.getDescription() != null &&
               invoice.isCurrentlyActive();
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean hasDuplicateInvoice(Customer customer, Property property, String categoryId, 
                                     BigDecimal amount, InvoiceFrequency frequency) {
        List<Invoice> existingInvoices = invoiceRepository.findByCustomerAndProperty(customer, property);
        
        return existingInvoices.stream()
                .anyMatch(invoice -> 
                    invoice.getIsActive() && 
                    invoice.getDeletedAt() == null &&
                    categoryId.equals(invoice.getCategoryId()) &&
                    amount.compareTo(invoice.getAmount()) == 0 &&
                    frequency == invoice.getFrequency());
    }
    
    // ===== STATISTICS AND REPORTING =====
    
    @Override
    @Transactional(readOnly = true)
    public long countActiveInvoices() {
        return invoiceRepository.countActiveInvoices();
    }
    
    @Override
    @Transactional(readOnly = true)
    public long countBySyncStatus(SyncStatus syncStatus) {
        return invoiceRepository.countBySyncStatus(syncStatus);
    }
    
    @Override
    @Transactional(readOnly = true)
    public long countByCustomer(Customer customer) {
        return invoiceRepository.countByCustomer(customer);
    }
    
    @Override
    @Transactional(readOnly = true)
    public long countByProperty(Property property) {
        return invoiceRepository.countByProperty(property);
    }
    
    @Override
    @Transactional(readOnly = true)
    public InvoiceStatistics getInvoiceStatistics() {
        InvoiceStatistics stats = new InvoiceStatistics();
        
        List<Invoice> allInvoices = invoiceRepository.findAllIncludingDeleted();
        List<Invoice> activeInvoices = findActiveInvoices();
        
        stats.setTotalInvoices(allInvoices.size());
        stats.setActiveInvoices(activeInvoices.size());
        stats.setInactiveInvoices(allInvoices.size() - activeInvoices.size());
        
        BigDecimal totalAmount = allInvoices.stream()
                .filter(i -> i.getDeletedAt() == null)
                .map(Invoice::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.setTotalAmount(totalAmount);
        
        BigDecimal activeAmount = activeInvoices.stream()
                .map(Invoice::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.setActiveAmount(activeAmount);
        
        long recurringCount = allInvoices.stream()
                .filter(i -> i.getDeletedAt() == null && i.getFrequency() != InvoiceFrequency.one_time)
                .count();
        stats.setRecurringInvoices(recurringCount);

        long oneTimeCount = allInvoices.stream()
                .filter(i -> i.getDeletedAt() == null && i.getFrequency() == InvoiceFrequency.one_time)
                .count();
        stats.setOneTimeInvoices(oneTimeCount);
        
        return stats;
    }
    
    @Override
    @Transactional(readOnly = true)
    public SyncStatusSummary getSyncStatusSummary() {
        SyncStatusSummary summary = new SyncStatusSummary();
        
        summary.setPendingSync(countBySyncStatus(SyncStatus.pending));
        summary.setSynced(countBySyncStatus(SyncStatus.synced));
        summary.setSyncErrors(countBySyncStatus(SyncStatus.error));
        summary.setManualSync(countBySyncStatus(SyncStatus.manual));
        
        return summary;
    }
    
    // ===== BULK OPERATIONS =====
    
    @Override
    public int bulkActivateInvoices(List<Long> invoiceIds) {
        int count = 0;
        for (Long invoiceId : invoiceIds) {
            try {
                activateInvoice(invoiceId);
                count++;
            } catch (Exception e) {
                log.error("Failed to activate invoice {}: {}", invoiceId, e.getMessage());
            }
        }
        log.info("Bulk activated {} out of {} invoices", count, invoiceIds.size());
        return count;
    }
    
    @Override
    public int bulkDeactivateInvoices(List<Long> invoiceIds) {
        int count = 0;
        for (Long invoiceId : invoiceIds) {
            try {
                deactivateInvoice(invoiceId);
                count++;
            } catch (Exception e) {
                log.error("Failed to deactivate invoice {}: {}", invoiceId, e.getMessage());
            }
        }
        log.info("Bulk deactivated {} out of {} invoices", count, invoiceIds.size());
        return count;
    }
    
    @Override
    public int bulkDeleteInvoices(List<Long> invoiceIds) {
        int count = 0;
        for (Long invoiceId : invoiceIds) {
            try {
                delete(invoiceId);
                count++;
            } catch (Exception e) {
                log.error("Failed to delete invoice {}: {}", invoiceId, e.getMessage());
            }
        }
        log.info("Bulk deleted {} out of {} invoices", count, invoiceIds.size());
        return count;
    }
    
    @Override
    public int bulkUpdateEndDates(List<Long> invoiceIds, LocalDate endDate) {
        int count = 0;
        for (Long invoiceId : invoiceIds) {
            try {
                updateEndDate(invoiceId, endDate);
                count++;
            } catch (Exception e) {
                log.error("Failed to update end date for invoice {}: {}", invoiceId, e.getMessage());
            }
        }
        log.info("Bulk updated end dates for {} out of {} invoices", count, invoiceIds.size());
        return count;
    }
    
    // ===== PAYPROP INTEGRATION SUPPORT =====
    
    @Override
    public void prepareForPayPropSync(Invoice invoice) {
        // Ensure account type is set from customer
        if (invoice.getAccountType() == null && invoice.getCustomer() != null) {
            invoice.setAccountType(invoice.getCustomer().getAccountType());
        }
        
        // Set frequency code
        if (invoice.getFrequencyCode() == null) {
            invoice.setFrequencyCode(invoice.getFrequency().name());
        }
        
        // Ensure payment day is set for applicable frequencies
        if (invoice.getPaymentDay() == null && invoice.getFrequency().requiresPaymentDay()) {
            invoice.setPaymentDay(1);
        }
        
        invoiceRepository.save(invoice);
    }
    
    @Override
    public Object convertToPayPropFormat(Invoice invoice) {
        // This would convert the invoice to PayProp API format
        // Implementation depends on PayProp API structure
        // For now, return a placeholder
        return Map.of(
            "id", invoice.getId(),
            "description", invoice.getDescription(),
            "amount", invoice.getAmount(),
            "frequency", invoice.getFrequency().name(),
            "start_date", invoice.getStartDate().toString()
        );
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<Invoice> findByPayPropId(String paypropId) {
        return invoiceRepository.findByPaypropId(paypropId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Invoice> getInvoicesRequiringSyncRetry() {
        // Find invoices that failed sync but might be retryable
        return findSyncErrorInvoices().stream()
                .filter(invoice -> invoice.isCurrentlyActive())
                .toList();
    }
}
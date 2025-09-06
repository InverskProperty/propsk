package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.entity.Invoice.SyncStatus;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.invoice.InvoiceService;
import site.easy.to.build.crm.service.assignment.CustomerPropertyAssignmentService;
import site.easy.to.build.crm.service.payprop.PayPropApiClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Local to PayProp Sync Service
 * 
 * Syncs local entities to PayProp using tested API patterns from our staging tests.
 * Based on successful entity creation patterns discovered through API testing.
 */
@Service
@Transactional
public class LocalToPayPropSyncService {
    
    private static final Logger log = LoggerFactory.getLogger(LocalToPayPropSyncService.class);
    
    @Autowired
    private PayPropApiClient payPropApiClient;
    
    @Autowired
    private PropertyService propertyService;
    
    @Autowired
    private CustomerService customerService;
    
    @Autowired
    private InvoiceService invoiceService;
    
    @Autowired
    private CustomerPropertyAssignmentService assignmentService;
    
    // PayProp Category IDs from our API testing
    public static class PayPropCategories {
        // Invoice Categories (tested)
        public static final String RENT = "Vv2XlY1ema";           // System category
        public static final String MAINTENANCE = "vagXVvX3RP";
        public static final String OTHER = "W5AJ5Oa1Mk";
        public static final String DEPOSIT = "woRZQl1mA4";
        public static final String HOLDING_DEPOSIT = "6EyJ6RJjbv"; // System category
        
        // Payment Categories (tested)
        public static final String OWNER = "Vv2XlY1ema";          // System category
        public static final String AGENT = "woRZQl1mA4";
        public static final String CONTRACTOR = "DWzJBaZQBp";
        public static final String COMMISSION = "Kd71e915Ma";     // System category
        public static final String PAYMENT_DEPOSIT = "zKd1b21vGg"; // System category
    }
    
    // ===== PROPERTY SYNC =====
    
    /**
     * Sync local property to PayProp using tested API pattern
     * Based on successful property creation: POST /entity/property
     */
    public String syncPropertyToPayProp(Property localProperty) {
        log.info("Syncing property to PayProp: {}", localProperty.getPropertyName());
        
        // Validate prerequisites
        if (localProperty.getPayPropId() != null) {
            log.warn("Property already has PayProp ID: {}", localProperty.getPayPropId());
            return localProperty.getPayPropId();
        }
        
        if (localProperty.getMonthlyPayment() == null) {
            throw new IllegalStateException("Monthly payment is required for PayProp sync");
        }
        
        try {
            Map<String, Object> payPropData = new HashMap<>();
            
            // Required fields (based on successful test)
            payPropData.put("name", localProperty.getPropertyName());
            payPropData.put("customer_id", localProperty.getCustomerId() != null ? 
                           localProperty.getCustomerId() : "LOCAL-PROP-" + localProperty.getId());
            
            // Address object (required)
            Map<String, Object> address = new HashMap<>();
            address.put("address_line_1", localProperty.getAddressLine1());
            if (localProperty.getAddressLine2() != null && !localProperty.getAddressLine2().trim().isEmpty()) {
                address.put("address_line_2", localProperty.getAddressLine2());
            }
            address.put("city", localProperty.getCity() != null ? localProperty.getCity() : "London");
            address.put("postal_code", localProperty.getPostcode() != null ? localProperty.getPostcode() : "SW1A 1AA");
            address.put("country_code", localProperty.getCountryCode() != null ? localProperty.getCountryCode() : "UK");
            if (localProperty.getState() != null) {
                address.put("state", localProperty.getState());
            }
            payPropData.put("address", address);
            
            // Settings object (required when monthly_payment provided)
            Map<String, Object> settings = new HashMap<>();
            settings.put("monthly_payment", localProperty.getMonthlyPayment().doubleValue());
            settings.put("enable_payments", "Y".equals(localProperty.getEnablePayments()));
            settings.put("hold_owner_funds", "Y".equals(localProperty.getHoldOwnerFunds()));
            settings.put("verify_payments", "Y".equals(localProperty.getVerifyPayments()));
            if (localProperty.getPropertyAccountMinimumBalance() != null) {
                settings.put("minimum_balance", localProperty.getPropertyAccountMinimumBalance().doubleValue());
            }
            payPropData.put("settings", settings);
            
            // Optional customer reference
            if (localProperty.getCustomerReference() != null) {
                payPropData.put("customer_reference", localProperty.getCustomerReference());
            }
            
            // Call PayProp API
            Object response = payPropApiClient.post("/entity/property", payPropData);
            String payPropId = extractIdFromResponse(response);
            
            // Update local property
            localProperty.setPayPropId(payPropId);
            propertyService.save(localProperty);
            
            log.info("Successfully synced property to PayProp: {} -> {}", 
                    localProperty.getPropertyName(), payPropId);
            
            return payPropId;
            
        } catch (Exception e) {
            log.error("Failed to sync property to PayProp: {}", e.getMessage(), e);
            throw new RuntimeException("Property sync failed: " + e.getMessage(), e);
        }
    }
    
    // ===== CUSTOMER SYNC =====
    
    /**
     * Sync local customer to PayProp as beneficiary (property owner)
     * Based on successful beneficiary creation: POST /entity/beneficiary
     */
    public String syncCustomerAsBeneficiaryToPayProp(Customer localCustomer) {
        log.info("Syncing customer as beneficiary to PayProp: {}", localCustomer.getName());
        
        // Validate prerequisites
        if (localCustomer.getPayPropEntityId() != null) {
            log.warn("Customer already has PayProp entity ID: {}", localCustomer.getPayPropEntityId());
            return localCustomer.getPayPropEntityId();
        }
        
        try {
            Map<String, Object> payPropData = buildCustomerPayPropData(localCustomer, "beneficiary");
            
            // Beneficiary-specific fields
            if (localCustomer.getBankAccountNumber() != null) {
                Map<String, Object> bankAccount = new HashMap<>();
                bankAccount.put("account_name", localCustomer.getBankAccountName() != null ? 
                               localCustomer.getBankAccountName() : localCustomer.getName());
                bankAccount.put("account_number", localCustomer.getBankAccountNumber());
                bankAccount.put("branch_code", localCustomer.getBankSortCode());
                payPropData.put("bank_account", bankAccount);
            }
            
            // Payment method (default to local as per our test)
            payPropData.put("payment_method", "local");
            
            // Call PayProp API
            Object response = payPropApiClient.post("/entity/beneficiary", payPropData);
            String payPropId = extractIdFromResponse(response);
            
            // Update local customer
            updateCustomerWithPayPropId(localCustomer, payPropId, "beneficiary");
            
            log.info("Successfully synced customer as beneficiary to PayProp: {} -> {}", 
                    localCustomer.getName(), payPropId);
            
            return payPropId;
            
        } catch (Exception e) {
            log.error("Failed to sync customer as beneficiary to PayProp: {}", e.getMessage(), e);
            throw new RuntimeException("Beneficiary sync failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Sync local customer to PayProp as tenant
     * Based on successful tenant creation: POST /entity/tenant
     */
    public String syncCustomerAsTenantToPayProp(Customer localCustomer) {
        log.info("Syncing customer as tenant to PayProp: {}", localCustomer.getName());
        
        // Validate prerequisites
        if (localCustomer.getPayPropEntityId() != null) {
            log.warn("Customer already has PayProp entity ID: {}", localCustomer.getPayPropEntityId());
            return localCustomer.getPayPropEntityId();
        }
        
        try {
            Map<String, Object> payPropData = buildCustomerPayPropData(localCustomer, "tenant");
            
            // Tenant-specific fields
            if (localCustomer.getDateOfBirth() != null && 
                localCustomer.getAccountType() == AccountType.individual) {
                payPropData.put("date_of_birth", localCustomer.getDateOfBirth().toString());
            }
            
            if (localCustomer.getVatNumber() != null && 
                localCustomer.getAccountType() == AccountType.business) {
                payPropData.put("vat_number", localCustomer.getVatNumber());
            }
            
            // Bank account for tenants
            if (localCustomer.getBankAccountNumber() != null) {
                payPropData.put("has_bank_account", true);
                Map<String, Object> bankAccount = new HashMap<>();
                bankAccount.put("account_name", localCustomer.getBankAccountName() != null ? 
                               localCustomer.getBankAccountName() : localCustomer.getName());
                bankAccount.put("account_number", localCustomer.getBankAccountNumber());
                bankAccount.put("branch_code", localCustomer.getBankSortCode());
                payPropData.put("bank_account", bankAccount);
            } else {
                payPropData.put("has_bank_account", false);
            }
            
            // Use mobile_number field for tenants (not mobile)
            if (localCustomer.getMobileNumber() != null) {
                payPropData.put("mobile_number", localCustomer.getMobileNumber());
                payPropData.remove("mobile"); // Remove mobile field used for beneficiaries
            }
            
            // Call PayProp API
            Object response = payPropApiClient.post("/entity/tenant", payPropData);
            String payPropId = extractIdFromResponse(response);
            
            // Update local customer
            updateCustomerWithPayPropId(localCustomer, payPropId, "tenant");
            
            log.info("Successfully synced customer as tenant to PayProp: {} -> {}", 
                    localCustomer.getName(), payPropId);
            
            return payPropId;
            
        } catch (Exception e) {
            log.error("Failed to sync customer as tenant to PayProp: {}", e.getMessage(), e);
            throw new RuntimeException("Tenant sync failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Build common PayProp data for customer entities
     */
    private Map<String, Object> buildCustomerPayPropData(Customer localCustomer, String entityType) {
        Map<String, Object> payPropData = new HashMap<>();
        
        // Account type and basic info
        payPropData.put("account_type", localCustomer.getAccountType().name());
        payPropData.put("customer_id", "LOCAL-CUST-" + localCustomer.getCustomerId());
        payPropData.put("email_address", localCustomer.getEmail());
        
        // Customer reference if available
        if (localCustomer.getCustomerReference() != null) {
            payPropData.put("customer_reference", localCustomer.getCustomerReference());
        }
        
        // Name fields based on account type
        if (localCustomer.getAccountType() == AccountType.individual) {
            payPropData.put("first_name", localCustomer.getFirstName());
            payPropData.put("last_name", localCustomer.getLastName());
        } else {
            payPropData.put("business_name", localCustomer.getBusinessName());
            // Contact person details for business
            if (localCustomer.getFirstName() != null) {
                payPropData.put("first_name", localCustomer.getFirstName());
            }
            if (localCustomer.getLastName() != null) {
                payPropData.put("last_name", localCustomer.getLastName());
            }
        }
        
        // Contact info (mobile field name differs between beneficiaries and tenants)
        if (localCustomer.getMobileNumber() != null) {
            if ("tenant".equals(entityType)) {
                payPropData.put("mobile_number", localCustomer.getMobileNumber());
            } else {
                payPropData.put("mobile", localCustomer.getMobileNumber());
            }
        }
        if (localCustomer.getPhone() != null) {
            payPropData.put("phone", localCustomer.getPhone());
        }
        
        // Address
        Map<String, Object> address = new HashMap<>();
        address.put("address_line_1", localCustomer.getAddressLine1() != null ? 
                   localCustomer.getAddressLine1() : "Address Line 1");
        if (localCustomer.getAddressLine2() != null && !localCustomer.getAddressLine2().trim().isEmpty()) {
            address.put("address_line_2", localCustomer.getAddressLine2());
        }
        address.put("city", localCustomer.getCity() != null ? localCustomer.getCity() : "London");
        address.put("postal_code", localCustomer.getPostcode() != null ? 
                   localCustomer.getPostcode() : "SW1A 1AA");
        address.put("country_code", localCustomer.getCountryCode() != null ? 
                   localCustomer.getCountryCode() : "UK");
        if (localCustomer.getState() != null) {
            address.put("state", localCustomer.getState());
        }
        payPropData.put("address", address);
        
        return payPropData;
    }
    
    /**
     * Update local customer with PayProp sync information
     */
    private void updateCustomerWithPayPropId(Customer customer, String payPropId, String entityType) {
        customer.setPayPropEntityId(payPropId);
        customer.setPayPropEntityType(entityType);
        customer.setPayPropSynced(true);
        customer.setPayPropLastSync(LocalDateTime.now());
        customerService.save(customer);
    }
    
    // ===== INVOICE SYNC =====
    
    /**
     * Sync local invoice to PayProp using tested pattern
     * Based on successful invoice creation: POST /entity/invoice
     */
    public String syncInvoiceToPayProp(Invoice localInvoice) {
        log.info("Syncing invoice to PayProp: {}", localInvoice.getDescription());
        
        // Validate prerequisites
        if (localInvoice.getPaypropId() != null) {
            log.warn("Invoice already has PayProp ID: {}", localInvoice.getPaypropId());
            return localInvoice.getPaypropId();
        }
        
        if (localInvoice.getCustomer().getPayPropEntityId() == null) {
            throw new IllegalStateException("Customer must be synced to PayProp before invoice sync");
        }
        
        if (localInvoice.getProperty().getPayPropId() == null) {
            throw new IllegalStateException("Property must be synced to PayProp before invoice sync");
        }
        
        try {
            Map<String, Object> payPropData = new HashMap<>();
            
            // Required fields based on our successful tests
            payPropData.put("gross_amount", localInvoice.getAmount().doubleValue());
            payPropData.put("description", localInvoice.getDescription());
            payPropData.put("frequency_code", localInvoice.getFrequency().name());
            payPropData.put("start_date", localInvoice.getStartDate().toString());
            
            // Category ID - map local categories to PayProp
            String payPropCategoryId = mapLocalCategoryToPayProp(localInvoice.getCategoryId());
            payPropData.put("category_id", payPropCategoryId);
            
            // Entity references
            payPropData.put("tenant_id", localInvoice.getCustomer().getPayPropEntityId());
            payPropData.put("property_id", localInvoice.getProperty().getPayPropId());
            payPropData.put("account_type", localInvoice.getAccountType() != null ? 
                           localInvoice.getAccountType().name() : "individual");
            
            // Payment day for recurring invoices
            if (localInvoice.getFrequency().requiresPaymentDay() && localInvoice.getPaymentDay() != null) {
                payPropData.put("payment_day", localInvoice.getPaymentDay());
            }
            
            // Optional fields
            if (localInvoice.getEndDate() != null) {
                payPropData.put("end_date", localInvoice.getEndDate().toString());
            }
            if (localInvoice.getIsDebitOrder() != null) {
                payPropData.put("debit_order", localInvoice.getIsDebitOrder());
            }
            if (localInvoice.getVatIncluded() != null) {
                payPropData.put("vat", localInvoice.getVatIncluded());
            }
            if (localInvoice.getVatAmount() != null) {
                payPropData.put("vat_amount", localInvoice.getVatAmount().doubleValue());
            }
            
            // Call PayProp API
            Object response = payPropApiClient.post("/entity/invoice", payPropData);
            String payPropId = extractIdFromResponse(response);
            
            // Update local invoice
            localInvoice.markSyncedToPayProp(payPropId, localInvoice.getCustomer().getPayPropEntityId());
            invoiceService.save(localInvoice);
            
            log.info("Successfully synced invoice to PayProp: {} -> {}", 
                    localInvoice.getDescription(), payPropId);
            
            return payPropId;
            
        } catch (Exception e) {
            log.error("Failed to sync invoice to PayProp: {}", e.getMessage(), e);
            localInvoice.markSyncFailed(e.getMessage());
            invoiceService.save(localInvoice);
            throw new RuntimeException("Invoice sync failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Map local category IDs to PayProp category IDs
     */
    private String mapLocalCategoryToPayProp(String localCategoryId) {
        return switch (localCategoryId.toLowerCase()) {
            case "rent" -> PayPropCategories.RENT;
            case "maintenance" -> PayPropCategories.MAINTENANCE;
            case "deposit" -> PayPropCategories.DEPOSIT;
            case "holding_deposit" -> PayPropCategories.HOLDING_DEPOSIT;
            case "other" -> PayPropCategories.OTHER;
            default -> {
                log.warn("Unknown local category '{}', defaulting to 'other'", localCategoryId);
                yield PayPropCategories.OTHER;
            }
        };
    }
    
    // ===== COMPLETE ECOSYSTEM SYNC =====
    
    /**
     * Sync a complete property ecosystem to PayProp
     * Property -> Owners (as beneficiaries) -> Tenants -> Invoices -> Payment Instructions
     */
    @Transactional
    public PropertyEcosystemSyncResult syncCompletePropertyEcosystem(Long propertyId) {
        log.info("Starting complete property ecosystem sync for property ID: {}", propertyId);
        
        PropertyEcosystemSyncResult result = new PropertyEcosystemSyncResult();
        result.setPropertyId(propertyId);
        result.setStartTime(LocalDateTime.now());
        
        try {
            // Get property
            Optional<Property> propertyOpt = propertyService.findById(propertyId);
            if (propertyOpt.isEmpty()) {
                throw new IllegalArgumentException("Property not found: " + propertyId);
            }
            Property property = propertyOpt.get();
            
            // Step 1: Sync property
            if (property.getPayPropId() == null) {
                String propertyPayPropId = syncPropertyToPayProp(property);
                result.setPropertyPayPropId(propertyPayPropId);
                result.setPropertySynced(true);
            } else {
                result.setPropertyPayPropId(property.getPayPropId());
                result.setPropertySynced(false); // Already synced
            }
            
            // Step 2: Sync all property owners as beneficiaries
            List<Customer> owners = assignmentService.getCustomersForProperty(propertyId, AssignmentType.OWNER);
            for (Customer owner : owners) {
                try {
                    if (owner.getPayPropEntityId() == null) {
                        String beneficiaryId = syncCustomerAsBeneficiaryToPayProp(owner);
                        result.addSyncedBeneficiary(beneficiaryId);
                    } else {
                        result.addExistingBeneficiary(owner.getPayPropEntityId());
                    }
                } catch (Exception e) {
                    result.addError("Failed to sync owner " + owner.getName() + ": " + e.getMessage());
                }
            }
            
            // Step 3: Sync all tenants
            List<Customer> tenants = assignmentService.getCustomersForProperty(propertyId, AssignmentType.TENANT);
            for (Customer tenant : tenants) {
                try {
                    if (tenant.getPayPropEntityId() == null) {
                        String tenantId = syncCustomerAsTenantToPayProp(tenant);
                        result.addSyncedTenant(tenantId);
                    } else {
                        result.addExistingTenant(tenant.getPayPropEntityId());
                    }
                } catch (Exception e) {
                    result.addError("Failed to sync tenant " + tenant.getName() + ": " + e.getMessage());
                }
            }
            
            // Step 4: Sync local invoices
            List<Invoice> localInvoices = invoiceService.findByProperty(property);
            for (Invoice invoice : localInvoices) {
                try {
                    if (invoice.needsPayPropSync()) {
                        String invoiceId = syncInvoiceToPayProp(invoice);
                        result.addSyncedInvoice(invoiceId);
                    } else {
                        if (invoice.getPaypropId() != null) {
                            result.addExistingInvoice(invoice.getPaypropId());
                        }
                    }
                } catch (Exception e) {
                    result.addError("Failed to sync invoice " + invoice.getDescription() + ": " + e.getMessage());
                }
            }
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            
            log.info("Successfully completed property ecosystem sync for property ID: {}. " +
                    "Synced: {} beneficiaries, {} tenants, {} invoices", 
                    propertyId, result.getSyncedBeneficiaries().size(), 
                    result.getSyncedTenants().size(), result.getSyncedInvoices().size());
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
            log.error("Failed to sync property ecosystem for property ID {}: {}", propertyId, e.getMessage(), e);
        }
        
        return result;
    }
    
    // ===== UTILITY METHODS =====
    
    /**
     * Extract ID from PayProp API response
     */
    private String extractIdFromResponse(Object response) {
        if (response instanceof Map) {
            Map<?, ?> responseMap = (Map<?, ?>) response;
            Object id = responseMap.get("id");
            if (id != null) {
                return id.toString();
            }
        }
        throw new RuntimeException("Could not extract ID from PayProp response: " + response);
    }
    
    // ===== RESULT CLASSES =====
    
    /**
     * Result object for complete property ecosystem sync
     */
    public static class PropertyEcosystemSyncResult {
        private Long propertyId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private boolean success;
        private String errorMessage;
        
        // Property sync
        private boolean propertySynced;
        private String propertyPayPropId;
        
        // Entity sync results
        private final List<String> syncedBeneficiaries = new ArrayList<>();
        private final List<String> existingBeneficiaries = new ArrayList<>();
        private final List<String> syncedTenants = new ArrayList<>();
        private final List<String> existingTenants = new ArrayList<>();
        private final List<String> syncedInvoices = new ArrayList<>();
        private final List<String> existingInvoices = new ArrayList<>();
        private final List<String> syncedPayments = new ArrayList<>();
        
        // Errors
        private final List<String> errors = new ArrayList<>();
        
        // Getters and setters
        public Long getPropertyId() { return propertyId; }
        public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }
        
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        public boolean isPropertySynced() { return propertySynced; }
        public void setPropertySynced(boolean propertySynced) { this.propertySynced = propertySynced; }
        
        public String getPropertyPayPropId() { return propertyPayPropId; }
        public void setPropertyPayPropId(String propertyPayPropId) { this.propertyPayPropId = propertyPayPropId; }
        
        public List<String> getSyncedBeneficiaries() { return syncedBeneficiaries; }
        public void addSyncedBeneficiary(String id) { this.syncedBeneficiaries.add(id); }
        
        public List<String> getExistingBeneficiaries() { return existingBeneficiaries; }
        public void addExistingBeneficiary(String id) { this.existingBeneficiaries.add(id); }
        
        public List<String> getSyncedTenants() { return syncedTenants; }
        public void addSyncedTenant(String id) { this.syncedTenants.add(id); }
        
        public List<String> getExistingTenants() { return existingTenants; }
        public void addExistingTenant(String id) { this.existingTenants.add(id); }
        
        public List<String> getSyncedInvoices() { return syncedInvoices; }
        public void addSyncedInvoice(String id) { this.syncedInvoices.add(id); }
        
        public List<String> getExistingInvoices() { return existingInvoices; }
        public void addExistingInvoice(String id) { this.existingInvoices.add(id); }
        
        public List<String> getSyncedPayments() { return syncedPayments; }
        public void addSyncedPayment(String id) { this.syncedPayments.add(id); }
        
        public List<String> getErrors() { return errors; }
        public void addError(String error) { this.errors.add(error); }
        
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("Property Ecosystem Sync Result for Property ID ").append(propertyId).append(":\n");
            sb.append("Success: ").append(success).append("\n");
            sb.append("Property Synced: ").append(propertySynced).append(" (ID: ").append(propertyPayPropId).append(")\n");
            sb.append("Synced Beneficiaries: ").append(syncedBeneficiaries.size()).append("\n");
            sb.append("Synced Tenants: ").append(syncedTenants.size()).append("\n");
            sb.append("Synced Invoices: ").append(syncedInvoices.size()).append("\n");
            if (!errors.isEmpty()) {
                sb.append("Errors: ").append(errors.size()).append("\n");
                for (String error : errors) {
                    sb.append("  - ").append(error).append("\n");
                }
            }
            return sb.toString();
        }
    }
}
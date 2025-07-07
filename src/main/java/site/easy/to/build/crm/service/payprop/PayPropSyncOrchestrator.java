// PayPropSyncOrchestrator.java - SIMPLIFIED: Unified Customer Approach
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.time.LocalDateTime;
import java.util.*;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropSyncOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PayPropSyncOrchestrator.class);

    private final PayPropSyncService payPropSyncService;
    private final PayPropSyncLogger syncLogger;
    private final CustomerService customerService;
    private final PropertyService propertyService;
    private final AuthenticationUtils authenticationUtils;

    @Value("${payprop.sync.batch-size:25}")
    private int batchSize;

    @Autowired
    public PayPropSyncOrchestrator(PayPropSyncService payPropSyncService,
                                  PayPropSyncLogger syncLogger,
                                  CustomerService customerService,
                                  PropertyService propertyService,
                                  AuthenticationUtils authenticationUtils) {
        this.payPropSyncService = payPropSyncService;
        this.syncLogger = syncLogger;
        this.customerService = customerService;
        this.propertyService = propertyService;
        this.authenticationUtils = authenticationUtils;
    }

    // ===== MAIN SYNC ORCHESTRATION =====

    /**
     * Complete two-way synchronization using unified Customer entity
     */
    public UnifiedSyncResult performUnifiedSync(Long initiatedBy) {
        UnifiedSyncResult result = new UnifiedSyncResult();
        syncLogger.logSyncStart("UNIFIED_SYNC", initiatedBy);
        
        try {
            // Step 1: Sync Properties (foundation)
            result.setPropertiesResult(syncPropertiesFromPayProp(initiatedBy));
            
            // Step 2: Get payment relationships 
            Map<String, PropertyRelationship> relationships = extractRelationshipsFromPayments();
            
            // Step 3: Sync Property Owners as Customers
            result.setPropertyOwnersResult(syncPropertyOwnersAsCustomers(initiatedBy, relationships));
            
            // Step 4: Sync Tenants as Customers  
            result.setTenantsResult(syncTenantsAsCustomers(initiatedBy));
            
            // Step 5: Establish property assignments
            result.setRelationshipsResult(establishPropertyAssignments(relationships));
            
            syncLogger.logSyncComplete("UNIFIED_SYNC", result.isOverallSuccess(), result.getSummary());
            
        } catch (Exception e) {
            syncLogger.logSyncError("UNIFIED_SYNC", e);
            result.setOverallError(e.getMessage());
        }
        
        return result;
    }

    // ===== STEP 1: SYNC PROPERTIES =====
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult syncPropertiesFromPayProp(Long initiatedBy) {
        try {
            int page = 1;
            int totalProcessed = 0;
            int totalCreated = 0;
            int totalUpdated = 0;
            int totalErrors = 0;
            
            while (true) {
                PayPropSyncService.PayPropExportResult exportResult = 
                    payPropSyncService.exportPropertiesFromPayProp(page, batchSize);
                
                if (exportResult.getItems().isEmpty()) {
                    break;
                }

                for (Map<String, Object> propertyData : exportResult.getItems()) {
                    try {
                        boolean isNew = createOrUpdateProperty(propertyData, initiatedBy);
                        if (isNew) totalCreated++; else totalUpdated++;
                        totalProcessed++;
                    } catch (Exception e) {
                        totalErrors++;
                        log.error("Failed to sync property {}: {}", propertyData.get("id"), e.getMessage());
                    }
                }
                page++;
            }

            Map<String, Object> details = Map.of(
                "processed", totalProcessed,
                "created", totalCreated, 
                "updated", totalUpdated,
                "errors", totalErrors
            );

            return totalErrors == 0 ? 
                SyncResult.success("Properties synced successfully", details) : 
                SyncResult.partial("Properties synced with some errors", details);
                
        } catch (Exception e) {
            return SyncResult.failure("Properties sync failed: " + e.getMessage());
        }
    }

    // ===== STEP 2: EXTRACT RELATIONSHIPS =====
    
    private Map<String, PropertyRelationship> extractRelationshipsFromPayments() {
        Map<String, PropertyRelationship> relationships = new HashMap<>();
        
        try {
            int page = 1;
            while (true) {
                PayPropSyncService.PayPropExportResult exportResult = 
                    payPropSyncService.exportPaymentsFromPayProp(page, batchSize);
                
                if (exportResult.getItems().isEmpty()) {
                    break;
                }
                
                for (Map<String, Object> payment : exportResult.getItems()) {
                    Map<String, Object> beneficiaryInfo = (Map<String, Object>) payment.get("beneficiary_info");
                    if (beneficiaryInfo != null && "beneficiary".equals(beneficiaryInfo.get("beneficiary_type"))) {
                        String category = (String) payment.get("category");
                        if ("Owner".equals(category)) {
                            String ownerId = (String) beneficiaryInfo.get("id");
                            Map<String, Object> property = (Map<String, Object>) payment.get("property");
                            if (property != null) {
                                String propertyId = (String) property.get("id");
                                
                                PropertyRelationship rel = new PropertyRelationship();
                                rel.setOwnerPayPropId(ownerId);
                                rel.setPropertyPayPropId(propertyId);
                                rel.setOwnershipType("OWNER");
                                
                                Object percentage = payment.get("gross_percentage");
                                if (percentage instanceof Number) {
                                    rel.setOwnershipPercentage(((Number) percentage).doubleValue());
                                }
                                
                                relationships.put(ownerId, rel);
                            }
                        }
                    }
                }
                page++;
            }
            
            log.info("Extracted {} property relationships from payments", relationships.size());
            return relationships;
            
        } catch (Exception e) {
            log.error("Failed to extract relationships: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    // ===== STEP 3: SYNC PROPERTY OWNERS AS CUSTOMERS =====
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult syncPropertyOwnersAsCustomers(Long initiatedBy, Map<String, PropertyRelationship> relationships) {
        try {
            int page = 1;
            int totalProcessed = 0;
            int totalCreated = 0;
            int totalUpdated = 0;
            int totalErrors = 0;
            
            while (true) {
                PayPropSyncService.PayPropExportResult exportResult = 
                    payPropSyncService.exportBeneficiariesFromPayProp(page, batchSize);
                
                if (exportResult.getItems().isEmpty()) {
                    break;
                }

                for (Map<String, Object> beneficiaryData : exportResult.getItems()) {
                    try {
                        String payPropId = (String) beneficiaryData.get("id");
                        PropertyRelationship relationship = relationships.get(payPropId);
                        
                        boolean isNew = createOrUpdatePropertyOwnerCustomer(beneficiaryData, relationship, initiatedBy);
                        if (isNew) totalCreated++; else totalUpdated++;
                        totalProcessed++;
                    } catch (Exception e) {
                        totalErrors++;
                        log.error("Failed to sync property owner {}: {}", beneficiaryData.get("id"), e.getMessage());
                    }
                }
                page++;
            }

            Map<String, Object> details = Map.of(
                "processed", totalProcessed,
                "created", totalCreated,
                "updated", totalUpdated, 
                "errors", totalErrors
            );

            return totalErrors == 0 ? 
                SyncResult.success("Property owners synced successfully", details) : 
                SyncResult.partial("Property owners synced with some errors", details);
                
        } catch (Exception e) {
            return SyncResult.failure("Property owners sync failed: " + e.getMessage());
        }
    }

    // ===== STEP 4: SYNC TENANTS AS CUSTOMERS =====
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult syncTenantsAsCustomers(Long initiatedBy) {
        try {
            int page = 1;
            int totalProcessed = 0;
            int totalCreated = 0;
            int totalUpdated = 0;
            int totalErrors = 0;
            
            while (true) {
                PayPropSyncService.PayPropExportResult exportResult = 
                    payPropSyncService.exportTenantsFromPayProp(page, batchSize);
                
                if (exportResult.getItems().isEmpty()) {
                    break;
                }

                for (Map<String, Object> tenantData : exportResult.getItems()) {
                    try {
                        boolean isNew = createOrUpdateTenantCustomer(tenantData, initiatedBy);
                        if (isNew) totalCreated++; else totalUpdated++;
                        totalProcessed++;
                    } catch (Exception e) {
                        totalErrors++;
                        log.error("Failed to sync tenant {}: {}", tenantData.get("id"), e.getMessage());
                    }
                }
                page++;
            }

            Map<String, Object> details = Map.of(
                "processed", totalProcessed,
                "created", totalCreated,
                "updated", totalUpdated,
                "errors", totalErrors
            );

            return totalErrors == 0 ? 
                SyncResult.success("Tenants synced successfully", details) : 
                SyncResult.partial("Tenants synced with some errors", details);
                
        } catch (Exception e) {
            return SyncResult.failure("Tenants sync failed: " + e.getMessage());
        }
    }

    // ===== STEP 5: ESTABLISH PROPERTY ASSIGNMENTS =====
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult establishPropertyAssignments(Map<String, PropertyRelationship> relationships) {
        try {
            int assignmentsCreated = 0;
            int assignmentErrors = 0;
            
            for (PropertyRelationship rel : relationships.values()) {
                try {
                    // Find the property owner customer
                    Customer ownerCustomer = customerService.findByPayPropEntityId(rel.getOwnerPayPropId());
                    if (ownerCustomer == null) {
                        log.warn("Property owner customer not found for PayProp ID: {}", rel.getOwnerPayPropId());
                        assignmentErrors++;
                        continue;
                    }
                    
                    // Find the property
                    Optional<Property> propertyOpt = propertyService.findByPayPropId(rel.getPropertyPayPropId());
                    if (propertyOpt.isEmpty()) {
                        log.warn("Property not found for PayProp ID: {}", rel.getPropertyPayPropId());
                        assignmentErrors++;
                        continue;
                    }
                    Property property = propertyOpt.get();
                    
                    // Establish assignment
                    ownerCustomer.setAssignedPropertyId(property.getId());
                    ownerCustomer.setAssignmentDate(LocalDateTime.now());
                    ownerCustomer.setEntityType("Property");
                    ownerCustomer.setEntityId(property.getId());
                    ownerCustomer.setPrimaryEntity(rel.getOwnershipPercentage() >= 50.0);
                    
                    customerService.save(ownerCustomer);
                    assignmentsCreated++;
                    
                } catch (Exception e) {
                    assignmentErrors++;
                    log.error("Failed to establish property assignment: {}", e.getMessage());
                }
            }
            
            Map<String, Object> details = Map.of(
                "assignmentsCreated", assignmentsCreated,
                "assignmentErrors", assignmentErrors
            );
            
            return assignmentErrors == 0 ? 
                SyncResult.success("Property assignments established", details) :
                SyncResult.partial("Property assignments completed with some errors", details);
                
        } catch (Exception e) {
            return SyncResult.failure("Property assignments failed: " + e.getMessage());
        }
    }

    // ===== ENTITY CREATION/UPDATE METHODS =====

    private boolean createOrUpdateProperty(Map<String, Object> propertyData, Long initiatedBy) {
        String payPropId = (String) propertyData.get("id");
        Optional<Property> existingOpt = propertyService.findByPayPropId(payPropId);
        
        if (existingOpt.isPresent()) {
            Property existing = existingOpt.get();
            updatePropertyFromPayPropData(existing, propertyData);
            existing.setUpdatedBy(initiatedBy);
            propertyService.save(existing);
            return false;
        } else {
            Property property = createPropertyFromPayPropData(propertyData);
            property.setCreatedBy(initiatedBy);
            propertyService.save(property);
            return true;
        }
    }

    private boolean createOrUpdatePropertyOwnerCustomer(Map<String, Object> beneficiaryData, 
                                                       PropertyRelationship relationship, 
                                                       Long initiatedBy) {
        String payPropId = (String) beneficiaryData.get("id");
        Customer existing = customerService.findByPayPropEntityId(payPropId);
        
        if (existing != null) {
            updateCustomerFromBeneficiaryData(existing, beneficiaryData, relationship);
            customerService.save(existing);
            return false;
        } else {
            Customer customer = createCustomerFromBeneficiaryData(beneficiaryData, relationship);
            customer.setCreatedAt(LocalDateTime.now());
            customerService.save(customer);
            return true;
        }
    }

    private boolean createOrUpdateTenantCustomer(Map<String, Object> tenantData, Long initiatedBy) {
        String payPropId = (String) tenantData.get("id");
        Customer existing = customerService.findByPayPropEntityId(payPropId);
        
        if (existing != null) {
            updateCustomerFromTenantData(existing, tenantData);
            customerService.save(existing);
            return false;
        } else {
            Customer customer = createCustomerFromTenantData(tenantData);
            customer.setCreatedAt(LocalDateTime.now());
            customerService.save(customer);
            return true;
        }
    }

    // ===== CUSTOMER MAPPING METHODS =====

    private Customer createCustomerFromBeneficiaryData(Map<String, Object> data, PropertyRelationship relationship) {
        Customer customer = new Customer();
        
        // PayProp Integration Fields
        customer.setPayPropEntityId((String) data.get("id"));
        customer.setPayPropCustomerId((String) data.get("customer_id"));
        customer.setPayPropEntityType("beneficiary");
        customer.setCustomerType(CustomerType.PROPERTY_OWNER);
        customer.setIsPropertyOwner(true);
        customer.setPayPropSynced(true);
        customer.setPayPropLastSync(LocalDateTime.now());
        
        // Account Type
        String accountTypeStr = (String) data.get("account_type");
        if ("business".equals(accountTypeStr)) {
            customer.setAccountType(AccountType.business);
            customer.setBusinessName((String) data.get("business_name"));
            customer.setName(customer.getBusinessName());
        } else {
            customer.setAccountType(AccountType.individual);
            customer.setFirstName((String) data.get("first_name"));
            customer.setLastName((String) data.get("last_name"));
            customer.setName(customer.getFirstName() + " " + customer.getLastName());
        }
        
        // Contact Details
        customer.setEmail((String) data.get("email_address"));
        customer.setMobileNumber((String) data.get("mobile"));
        customer.setPhone((String) data.get("phone"));
        
        // FIXED: Set required country field
        customer.setCountry("UK"); // Default required value
        
        // Address
        Map<String, Object> address = (Map<String, Object>) data.get("billing_address");
        if (address != null) {
            customer.setAddressLine1((String) address.get("first_line"));
            customer.setAddressLine2((String) address.get("second_line"));
            customer.setAddressLine3((String) address.get("third_line"));
            customer.setCity((String) address.get("city"));
            customer.setState((String) address.get("state"));
            customer.setPostcode((String) address.get("postal_code"));
            customer.setCountryCode((String) address.get("country_code"));
        }
        
        // Payment Method
        String paymentMethodStr = (String) data.get("payment_method");
        if (paymentMethodStr != null) {
            try {
                customer.setPaymentMethod(PaymentMethod.valueOf(paymentMethodStr.toLowerCase()));
            } catch (IllegalArgumentException e) {
                customer.setPaymentMethod(PaymentMethod.local);
            }
        }
        
        // Bank Details
        Map<String, Object> bankAccount = (Map<String, Object>) data.get("bank_account");
        if (bankAccount != null) {
            customer.setBankAccountName((String) bankAccount.get("account_name"));
            customer.setBankAccountNumber((String) bankAccount.get("account_number"));
            customer.setBankSortCode((String) bankAccount.get("branch_code"));
            customer.setBankName((String) bankAccount.get("bank_name"));
            customer.setBankBranchName((String) bankAccount.get("branch_name"));
            customer.setBankIban((String) bankAccount.get("iban"));
            customer.setBankSwiftCode((String) bankAccount.get("swift_code"));
        }
        
        // Property Relationship (if available)
        if (relationship != null) {
            Optional<Property> propertyOpt = propertyService.findByPayPropId(relationship.getPropertyPayPropId());
            if (propertyOpt.isPresent()) {
                Property property = propertyOpt.get();
                customer.setAssignedPropertyId(property.getId());
                customer.setEntityType("Property");
                customer.setEntityId(property.getId());
                customer.setPrimaryEntity(relationship.getOwnershipPercentage() >= 50.0);
            }
        }
        
        return customer;
    }

    private Customer createCustomerFromTenantData(Map<String, Object> data) {
        Customer customer = new Customer();
        
        // PayProp Integration Fields
        customer.setPayPropEntityId((String) data.get("id"));
        customer.setPayPropCustomerId((String) data.get("customer_id"));
        customer.setPayPropEntityType("tenant");
        customer.setCustomerType(CustomerType.TENANT);
        customer.setIsTenant(true);
        customer.setPayPropSynced(true);
        customer.setPayPropLastSync(LocalDateTime.now());
        
        // Account Type
        String accountTypeStr = (String) data.get("account_type");
        if ("business".equals(accountTypeStr)) {
            customer.setAccountType(AccountType.business);
            customer.setBusinessName((String) data.get("business_name"));
            customer.setName(customer.getBusinessName());
        } else {
            customer.setAccountType(AccountType.individual);
            customer.setFirstName((String) data.get("first_name"));
            customer.setLastName((String) data.get("last_name"));
            customer.setName(customer.getFirstName() + " " + customer.getLastName());
        }
        
        // Contact Details
        customer.setEmail((String) data.get("email_address"));
        customer.setMobileNumber((String) data.get("mobile_number"));
        customer.setPhone((String) data.get("phone"));
        
        // Address
        Map<String, Object> address = (Map<String, Object>) data.get("address");
        if (address != null) {
            customer.setAddressLine1((String) address.get("first_line"));
            customer.setAddressLine2((String) address.get("second_line"));
            customer.setAddressLine3((String) address.get("third_line"));
            customer.setCity((String) address.get("city"));
            customer.setPostcode((String) address.get("postal_code"));
            customer.setCountryCode((String) address.get("country_code"));
        }
        
        // Tenant-specific fields
        Object invoiceLeadDays = data.get("invoice_lead_days");
        if (invoiceLeadDays instanceof Number) {
            customer.setInvoiceLeadDays(((Number) invoiceLeadDays).intValue());
        }
        
        // Bank Details (optional for tenants)
        Boolean hasBankAccount = (Boolean) data.get("has_bank_account");
        if (Boolean.TRUE.equals(hasBankAccount)) {
            Map<String, Object> bankAccount = (Map<String, Object>) data.get("bank_account");
            if (bankAccount != null) {
                customer.setBankAccountName((String) bankAccount.get("account_name"));
                customer.setBankAccountNumber((String) bankAccount.get("account_number"));
                customer.setBankSortCode((String) bankAccount.get("branch_code"));
                customer.setBankName((String) bankAccount.get("bank_name"));
                customer.setHasBankAccount(true);
            }
        }
        
        return customer;
    }

    private void updateCustomerFromBeneficiaryData(Customer customer, Map<String, Object> data, PropertyRelationship relationship) {
        // Update PayProp sync fields
        customer.setPayPropLastSync(LocalDateTime.now());
        customer.setPayPropSynced(true);
        
        // Update contact details
        customer.setEmail((String) data.get("email_address"));
        customer.setMobileNumber((String) data.get("mobile"));
        customer.setPhone((String) data.get("phone"));
        
        // Update names if needed
        if (customer.getAccountType() == AccountType.business) {
            String businessName = (String) data.get("business_name");
            if (businessName != null) {
                customer.setBusinessName(businessName);
                customer.setName(businessName);
            }
        } else {
            String firstName = (String) data.get("first_name");
            String lastName = (String) data.get("last_name");
            if (firstName != null && lastName != null) {
                customer.setFirstName(firstName);
                customer.setLastName(lastName);
                customer.setName(firstName + " " + lastName);
            }
        }
        
        // Update property relationship if changed
        if (relationship != null) {
            Optional<Property> propertyOpt = propertyService.findByPayPropId(relationship.getPropertyPayPropId());
            if (propertyOpt.isPresent()) {
                Property property = propertyOpt.get();
                if (!property.getId().equals(customer.getAssignedPropertyId())) {
                    customer.setAssignedPropertyId(property.getId());
                    customer.setEntityId(property.getId());
                    customer.setPrimaryEntity(relationship.getOwnershipPercentage() >= 50.0);
                }
            }
        }
    }

    private void updateCustomerFromTenantData(Customer customer, Map<String, Object> data) {
        // Update PayProp sync fields
        customer.setPayPropLastSync(LocalDateTime.now());
        customer.setPayPropSynced(true);
        
        // Update contact details
        customer.setEmail((String) data.get("email_address"));
        customer.setMobileNumber((String) data.get("mobile_number"));
        customer.setPhone((String) data.get("phone"));
        
        // Update names if needed
        if (customer.getAccountType() == AccountType.business) {
            String businessName = (String) data.get("business_name");
            if (businessName != null) {
                customer.setBusinessName(businessName);
                customer.setName(businessName);
            }
        } else {
            String firstName = (String) data.get("first_name");
            String lastName = (String) data.get("last_name");
            if (firstName != null && lastName != null) {
                customer.setFirstName(firstName);
                customer.setLastName(lastName);
                customer.setName(firstName + " " + lastName);
            }
        }
    }

    // ===== UTILITY METHODS =====

    private Property createPropertyFromPayPropData(Map<String, Object> data) {
        Property property = new Property();
        property.setPayPropId((String) data.get("id"));
        property.setCustomerId((String) data.get("customer_id"));
        property.setCustomerReference((String) data.get("customer_reference"));
        property.setPropertyName((String) data.get("property_name"));
        
        // Address mapping
        Map<String, Object> address = (Map<String, Object>) data.get("address");
        if (address != null) {
            property.setAddressLine1((String) address.get("first_line"));
            property.setAddressLine2((String) address.get("second_line"));
            property.setAddressLine3((String) address.get("third_line"));
            property.setCity((String) address.get("city"));
            property.setState((String) address.get("state"));
            property.setPostcode((String) address.get("postal_code"));
            property.setCountryCode((String) address.get("country_code"));
        }
        
        return property;
    }

    private void updatePropertyFromPayPropData(Property property, Map<String, Object> data) {
        property.setPropertyName((String) data.get("property_name"));
        property.setCustomerReference((String) data.get("customer_reference"));
        
        // Update address if present
        Map<String, Object> address = (Map<String, Object>) data.get("address");
        if (address != null) {
            property.setAddressLine1((String) address.get("first_line"));
            property.setAddressLine2((String) address.get("second_line"));
            property.setAddressLine3((String) address.get("third_line"));
            property.setCity((String) address.get("city"));
            property.setState((String) address.get("state"));
            property.setPostcode((String) address.get("postal_code"));
            property.setCountryCode((String) address.get("country_code"));
        }
    }

    // ===== RESULT CLASSES =====

    public static class UnifiedSyncResult {
        private SyncResult propertiesResult;
        private SyncResult propertyOwnersResult;
        private SyncResult tenantsResult;
        private SyncResult relationshipsResult;
        private String overallError;

        public boolean isOverallSuccess() {
            return overallError == null && 
                   (propertiesResult == null || propertiesResult.isSuccess()) &&
                   (propertyOwnersResult == null || propertyOwnersResult.isSuccess()) &&
                   (tenantsResult == null || tenantsResult.isSuccess()) &&
                   (relationshipsResult == null || relationshipsResult.isSuccess());
        }

        public String getSummary() {
            if (overallError != null) return overallError;
            return "Unified sync completed successfully";
        }

        // Getters and setters
        public SyncResult getPropertiesResult() { return propertiesResult; }
        public void setPropertiesResult(SyncResult propertiesResult) { this.propertiesResult = propertiesResult; }
        
        public SyncResult getPropertyOwnersResult() { return propertyOwnersResult; }
        public void setPropertyOwnersResult(SyncResult propertyOwnersResult) { this.propertyOwnersResult = propertyOwnersResult; }
        
        public SyncResult getTenantsResult() { return tenantsResult; }
        public void setTenantsResult(SyncResult tenantsResult) { this.tenantsResult = tenantsResult; }
        
        public SyncResult getRelationshipsResult() { return relationshipsResult; }
        public void setRelationshipsResult(SyncResult relationshipsResult) { this.relationshipsResult = relationshipsResult; }
        
        public String getOverallError() { return overallError; }
        public void setOverallError(String overallError) { this.overallError = overallError; }
    }

    public static class PropertyRelationship {
        private String ownerPayPropId;
        private String propertyPayPropId;
        private String ownershipType;
        private Double ownershipPercentage;

        // Getters and setters
        public String getOwnerPayPropId() { return ownerPayPropId; }
        public void setOwnerPayPropId(String ownerPayPropId) { this.ownerPayPropId = ownerPayPropId; }
        
        public String getPropertyPayPropId() { return propertyPayPropId; }
        public void setPropertyPayPropId(String propertyPayPropId) { this.propertyPayPropId = propertyPayPropId; }
        
        public String getOwnershipType() { return ownershipType; }
        public void setOwnershipType(String ownershipType) { this.ownershipType = ownershipType; }
        
        public Double getOwnershipPercentage() { return ownershipPercentage; }
        public void setOwnershipPercentage(Double ownershipPercentage) { this.ownershipPercentage = ownershipPercentage; }
    }
}
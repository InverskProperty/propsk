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
import site.easy.to.build.crm.service.payprop.PayPropSyncService.PayPropExportResult;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.assignment.CustomerPropertyAssignmentService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropSyncOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PayPropSyncOrchestrator.class);

    private final PayPropSyncService payPropSyncService;
    private final PayPropSyncLogger syncLogger;
    private final CustomerService customerService;
    private final PropertyService propertyService;
    private final AuthenticationUtils authenticationUtils;
    private final CustomerPropertyAssignmentService assignmentService;

    @Value("${payprop.sync.batch-size:25}")
    private int batchSize;

    @Autowired
    public PayPropSyncOrchestrator(PayPropSyncService payPropSyncService,
                                  PayPropSyncLogger syncLogger,
                                  CustomerService customerService,
                                  PropertyService propertyService,
                                  AuthenticationUtils authenticationUtils,
                                  CustomerPropertyAssignmentService assignmentService) {
        this.payPropSyncService = payPropSyncService;
        this.syncLogger = syncLogger;
        this.customerService = customerService;
        this.propertyService = propertyService;
        this.authenticationUtils = authenticationUtils;
        this.assignmentService = assignmentService;
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

            // Step 5: Sync Contractors as Customers (standalone entities)
            result.setContractorsResult(syncContractorsAsCustomers(initiatedBy));

            // Step 6: Establish property assignments via junction table
            result.setRelationshipsResult(establishPropertyAssignments(relationships));

            // Step 7: Establish tenant relationships via junction table
            result.setTenantRelationshipsResult(establishTenantPropertyRelationships());
            
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
            log.info("🔗 Starting payment relationship extraction...");
            int page = 1;
            int totalPayments = 0;
            int beneficiaryPayments = 0;
            int ownerPayments = 0;
            
            while (true) {
                PayPropSyncService.PayPropExportResult exportResult = 
                    payPropSyncService.exportPaymentsFromPayProp(page, batchSize);
                
                if (exportResult.getItems().isEmpty()) {
                    log.info("📄 No more payments on page {}", page);
                    break;
                }
                
                log.info("📄 Processing {} payments on page {}", exportResult.getItems().size(), page);
                totalPayments += exportResult.getItems().size();
                
                for (Map<String, Object> payment : exportResult.getItems()) {
                    Map<String, Object> beneficiaryInfo = (Map<String, Object>) payment.get("beneficiary_info");
                    if (beneficiaryInfo != null) {
                        beneficiaryPayments++;
                        String beneficiaryType = (String) beneficiaryInfo.get("beneficiary_type");
                        log.debug("Found beneficiary_info: type={}", beneficiaryType);
                        
                        if ("beneficiary".equals(beneficiaryType)) {
                            String category = (String) payment.get("category");
                            log.debug("Beneficiary payment category: {}", category);
                            
                            if ("Owner".equals(category)) {
                                ownerPayments++;
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
                                    log.info("✅ Found relationship: Owner {} owns Property {} ({}%)", 
                                        ownerId, propertyId, rel.getOwnershipPercentage());
                                } else {
                                    log.warn("⚠️ Owner payment missing property info: {}", ownerId);
                                }
                            }
                        }
                    }
                }
                page++;
            }
            
            log.info("🔗 Payment extraction completed: {} total payments, {} with beneficiary_info, {} owner payments, {} relationships found", 
                totalPayments, beneficiaryPayments, ownerPayments, relationships.size());
            return relationships;
            
        } catch (Exception e) {
            log.error("❌ Failed to extract relationships: {}", e.getMessage(), e);
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

    // ===== STEP 5: SYNC CONTRACTORS AS CUSTOMERS =====

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult syncContractorsAsCustomers(Long initiatedBy) {
        try {
            log.info("🔧 Starting contractor discovery via maintenance payments...");
            
            Set<String> contractorBeneficiaryIds = discoverContractorsFromPayments();
            int totalProcessed = 0;
            int totalCreated = 0;
            int totalUpdated = 0;
            int totalErrors = 0;
            
            log.info("🔧 Found {} unique contractor beneficiary IDs", contractorBeneficiaryIds.size());
            
            if (!contractorBeneficiaryIds.isEmpty()) {
                int page = 1;
                
                while (true) {
                    PayPropSyncService.PayPropExportResult exportResult = 
                        payPropSyncService.exportBeneficiariesFromPayProp(page, batchSize);
                    
                    if (exportResult.getItems().isEmpty()) {
                        break;
                    }

                    for (Map<String, Object> beneficiaryData : exportResult.getItems()) {
                        String beneficiaryId = (String) beneficiaryData.get("id");
                        
                        // Only process if this beneficiary is a contractor
                        if (contractorBeneficiaryIds.contains(beneficiaryId)) {
                            try {
                                boolean isNew = createOrUpdateContractorCustomer(beneficiaryData, initiatedBy);
                                if (isNew) totalCreated++; else totalUpdated++;
                                totalProcessed++;
                                
                                String name = getNameFromBeneficiaryData(beneficiaryData);
                                log.info("✅ Processed contractor: {} ({})", name, isNew ? "created" : "updated");
                            } catch (Exception e) {
                                totalErrors++;
                                log.error("Failed to sync contractor {}: {}", beneficiaryId, e.getMessage());
                            }
                        }
                    }
                    page++;
                }
            }

            Map<String, Object> details = Map.of(
                "contractorsDiscovered", contractorBeneficiaryIds.size(),
                "processed", totalProcessed,
                "created", totalCreated,
                "updated", totalUpdated,
                "errors", totalErrors
            );

            log.info("🔧 Contractor sync completed: {} discovered, {} processed, {} created, {} updated, {} errors", 
                contractorBeneficiaryIds.size(), totalProcessed, totalCreated, totalUpdated, totalErrors);

            return totalErrors == 0 ? 
                SyncResult.success("Contractors synced successfully", details) : 
                SyncResult.partial("Contractors synced with some errors", details);
                
        } catch (Exception e) {
            log.error("❌ Contractor sync failed: {}", e.getMessage(), e);
            return SyncResult.failure("Contractor sync failed: " + e.getMessage());
        }
    }

    // ===== CONTRACTOR DISCOVERY METHOD =====

    private Set<String> discoverContractorsFromPayments() {
        Set<String> contractorIds = new HashSet<>();
        
        try {
            log.info("🔍 Discovering contractors from payment instructions...");
            int page = 1;
            int totalPayments = 0;
            int contractorPayments = 0;
            
            while (true) {
                PayPropSyncService.PayPropExportResult exportResult = 
                    payPropSyncService.exportPaymentsFromPayProp(page, batchSize);
                
                if (exportResult.getItems().isEmpty()) {
                    break;
                }
                
                totalPayments += exportResult.getItems().size();
                
                for (Map<String, Object> payment : exportResult.getItems()) {
                    Map<String, Object> beneficiaryInfo = (Map<String, Object>) payment.get("beneficiary_info");
                    if (beneficiaryInfo != null && "beneficiary".equals(beneficiaryInfo.get("beneficiary_type"))) {
                        String category = (String) payment.get("category");
                        
                        // Look for contractor-related payment categories or maintenance tickets
                        if (isContractorPayment(payment, category)) {
                            String contractorId = (String) beneficiaryInfo.get("id");
                            contractorIds.add(contractorId);
                            contractorPayments++;
                            
                            log.debug("🔧 Found contractor payment: {} (category: {}, maintenance_ticket: {})", 
                                contractorId, category, payment.get("maintenance_ticket_id"));
                        }
                    }
                }
                page++;
            }
            
            log.info("🔧 Contractor discovery completed: {} contractors found from {} payments ({} contractor payments)", 
                contractorIds.size(), totalPayments, contractorPayments);
            return contractorIds;
            
        } catch (Exception e) {
            log.error("❌ Failed to discover contractors from payments: {}", e.getMessage());
            return new HashSet<>();
        }
    }

    private boolean isContractorPayment(Map<String, Object> payment, String category) {
        // Method 1: Payment linked to maintenance ticket
        if (payment.get("maintenance_ticket_id") != null) {
            return true;
        }
        
        // Method 2: Contractor-related payment categories
        if (category != null) {
            String lowerCategory = category.toLowerCase();
            return lowerCategory.contains("maintenance") ||
                lowerCategory.contains("contractor") ||
                lowerCategory.contains("repair") ||
                lowerCategory.contains("plumber") ||
                lowerCategory.contains("electrician") ||
                lowerCategory.contains("gardening") ||
                lowerCategory.contains("cleaning") ||
                lowerCategory.contains("handyman") ||
                lowerCategory.contains("painting") ||
                lowerCategory.contains("roofing") ||
                lowerCategory.contains("heating") ||
                lowerCategory.contains("building");
        }
        
        return false;
    }

    // ===== CONTRACTOR CUSTOMER CREATION/UPDATE =====

    private boolean createOrUpdateContractorCustomer(Map<String, Object> beneficiaryData, Long initiatedBy) {
        String payPropId = (String) beneficiaryData.get("id");
        Customer existing = customerService.findByPayPropEntityId(payPropId);
        
        if (existing != null) {
            // Update existing customer to be a contractor if not already marked
            if (!existing.getIsContractor()) {
                existing.setIsContractor(true);
                existing.setCustomerType(CustomerType.CONTRACTOR);
                log.info("🔧 Updated existing customer {} to contractor", existing.getName());
            }
            updateCustomerFromContractorData(existing, beneficiaryData);
            customerService.save(existing);
            return false;
        } else {
            Customer customer = createCustomerFromContractorData(beneficiaryData);
            customer.setCreatedAt(LocalDateTime.now());
            customerService.save(customer);
            return true;
        }
    }

    private Customer createCustomerFromContractorData(Map<String, Object> data) {
        Customer customer = new Customer();
        
        // PayProp Integration Fields
        customer.setPayPropEntityId((String) data.get("id"));
        customer.setPayPropCustomerId((String) data.get("customer_id"));
        customer.setPayPropEntityType("beneficiary");
        customer.setCustomerType(CustomerType.CONTRACTOR);
        customer.setIsContractor(true);
        customer.setPayPropSynced(true);
        customer.setPayPropLastSync(LocalDateTime.now());
        
        // Account Type and Name
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
        customer.setCountry("UK");
        
        // Address (same pattern as existing beneficiary mapping)
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
        
        // Bank Details (same pattern as property owners)
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
        
        return customer;
    }

    private void updateCustomerFromContractorData(Customer customer, Map<String, Object> data) {
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
    }

    // ===== UTILITY METHOD =====

    private String getNameFromBeneficiaryData(Map<String, Object> data) {
        String businessName = (String) data.get("business_name");
        if (businessName != null) {
            return businessName;
        }
        
        String firstName = (String) data.get("first_name");
        String lastName = (String) data.get("last_name");
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        
        return "Unknown Contractor";
    }

    // ===== STEP 5: ESTABLISH PROPERTY ASSIGNMENTS =====
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult establishPropertyAssignments(Map<String, PropertyRelationship> relationships) {
        try {
            log.info("🏠 Starting enhanced property assignment establishment...");
            int globalAssignments = 0;
            int propertySpecificAssignments = 0;
            int assignmentErrors = 0;
            
            // Step 1: Establish relationships from global payments (existing logic)
            log.info("📋 Step 1: Processing global payment relationships ({} found)", relationships.size());
            for (PropertyRelationship rel : relationships.values()) {
                try {
                    Customer ownerCustomer = customerService.findByPayPropEntityId(rel.getOwnerPayPropId());
                    if (ownerCustomer == null) {
                        log.warn("❌ Property owner customer not found for PayProp ID: {}", rel.getOwnerPayPropId());
                        assignmentErrors++;
                        continue;
                    }
                    
                    Optional<Property> propertyOpt = propertyService.findByPayPropId(rel.getPropertyPayPropId());
                    if (propertyOpt.isEmpty()) {
                        log.warn("❌ Property not found for PayProp ID: {}", rel.getPropertyPayPropId());
                        assignmentErrors++;
                        continue;
                    }
                    Property property = propertyOpt.get();
                    
                    BigDecimal percentage = rel.getOwnershipPercentage() != null ? 
                        new BigDecimal(rel.getOwnershipPercentage().toString()) : new BigDecimal("100.00");
                    
                    try {
                        assignmentService.createAssignment(ownerCustomer, property, AssignmentType.OWNER, percentage, true);
                        globalAssignments++;
                        log.info("✅ Global payment assignment: {} owns {} ({}%)", 
                            ownerCustomer.getName(), property.getPropertyName(), percentage);
                    } catch (IllegalStateException e) {
                        log.info("ℹ️ Assignment already exists: {} owns {}", ownerCustomer.getName(), property.getPropertyName());
                    }
                    
                } catch (Exception e) {
                    assignmentErrors++;
                    log.error("❌ Failed to establish global assignment: {}", e.getMessage(), e);
                }
            }
            
            // Step 2: Find missing owners via property-specific payments (NEW ENHANCED LOGIC)
            log.info("🔍 Step 2: Finding missing owners via property-specific payments...");
            List<Property> propertiesWithoutOwners = propertyService.findAll().stream()
                .filter(p -> {
                    List<Customer> owners = assignmentService.getCustomersForProperty(p.getId(), AssignmentType.OWNER);
                    return owners.isEmpty();
                })
                .collect(Collectors.toList());
            
            log.info("Found {} properties without owners, checking each individually...", propertiesWithoutOwners.size());
            
            for (Property property : propertiesWithoutOwners) {
                if (property.getPayPropId() != null) {
                    try {
                        PayPropSyncService.PayPropExportResult propertyPayments = 
                            payPropSyncService.exportPaymentsByProperty(property.getPayPropId());
                        
                        for (Map<String, Object> payment : propertyPayments.getItems()) {
                            if ("Owner".equals(payment.get("category")) && payment.get("beneficiary_info") != null) {
                                Map<String, Object> beneficiaryInfo = (Map<String, Object>) payment.get("beneficiary_info");
                                String ownerId = (String) beneficiaryInfo.get("id");
                                
                                Customer owner = customerService.findByPayPropEntityId(ownerId);
                                if (owner != null) {
                                    try {
                                        assignmentService.createAssignment(owner, property, AssignmentType.OWNER, 
                                            new BigDecimal("100.00"), true);
                                        propertySpecificAssignments++;
                                        log.info("🎯 Found missing owner via property-specific API: {} owns {}", 
                                            owner.getName(), property.getPropertyName());
                                    } catch (IllegalStateException e) {
                                        log.info("ℹ️ Property-specific assignment already exists: {} owns {}", 
                                            owner.getName(), property.getPropertyName());
                                    }
                                } else {
                                    log.warn("❌ Owner customer not found for beneficiary ID: {}", ownerId);
                                    assignmentErrors++;
                                }
                            }
                        }
                    } catch (Exception e) {
                        assignmentErrors++;
                        log.error("Failed to get property-specific payments for {}: {}", 
                            property.getPayPropId(), e.getMessage());
                    }
                }
            }
            
            Map<String, Object> details = Map.of(
                "globalAssignments", globalAssignments,
                "propertySpecificAssignments", propertySpecificAssignments,
                "totalAssignments", globalAssignments + propertySpecificAssignments,
                "assignmentErrors", assignmentErrors,
                "totalRelationships", relationships.size()
            );
            
            log.info("🏠 Enhanced assignment completed: {} global + {} property-specific = {} total assignments, {} errors", 
                globalAssignments, propertySpecificAssignments, globalAssignments + propertySpecificAssignments, assignmentErrors);
            
            return assignmentErrors == 0 ? 
                SyncResult.success("Enhanced property assignments established", details) :
                SyncResult.partial("Enhanced property assignments completed with some errors", details);
                
        } catch (Exception e) {
            log.error("❌ Enhanced property assignment process failed: {}", e.getMessage(), e);
            return SyncResult.failure("Enhanced property assignments failed: " + e.getMessage());
        }
    }
    // ===== STEP 6: ESTABLISH TENANT RELATIONSHIPS =====
    
    private SyncResult establishTenantPropertyRelationships() {
        int relationships = 0;
        int errors = 0;
        
        List<Property> allProperties = propertyService.findAll();
        
        for (Property property : allProperties) {
            if (property.getPayPropId() != null) {
                try {
                    PayPropSyncService.PayPropExportResult tenants = payPropSyncService.exportTenantsByProperty(property.getPayPropId());
                    
                    for (Map<String, Object> tenantData : tenants.getItems()) {
                        String tenantPayPropId = (String) tenantData.get("id");
                        Customer tenant = customerService.findByPayPropEntityId(tenantPayPropId);
                        
                        if (tenant != null) {
                            try {
                                assignmentService.createAssignment(tenant, property, AssignmentType.TENANT);
                                relationships++;
                                log.info("✅ Established tenant relationship: {} rents {}", 
                                    tenant.getName(), property.getPropertyName());
                            } catch (IllegalStateException e) {
                                log.info("ℹ️ Tenant assignment already exists: {} rents {}", 
                                    tenant.getName(), property.getPropertyName());
                            }
                        }
                    }
                } catch (Exception e) {
                    errors++;
                    log.error("Failed to get tenants for property {}: {}", property.getPayPropId(), e.getMessage());
                }
            }
        }
        
        Map<String, Object> details = Map.of(
            "tenantRelationships", relationships,
            "errors", errors
        );
        
        return errors == 0 ? 
            SyncResult.success("Tenant relationships established", details) :
            SyncResult.partial("Tenant relationships completed with some errors", details);
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
        
        // Property Relationship will be handled separately via junction table
        // No direct assignment to customer entity anymore
        
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
        
        // FIXED: Set required country field
        customer.setCountry("UK"); // Default required value
        
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
        
        // Property relationships now handled via junction table
        // Customer updates don't modify property assignments directly
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
        
        // ✅ NEW: Settings mapping including monthly_payment
        Map<String, Object> settings = (Map<String, Object>) data.get("settings");
        if (settings != null) {
            // Monthly payment - this is the critical field you're missing!
            Object monthlyPayment = settings.get("monthly_payment");
            if (monthlyPayment instanceof Number) {
                property.setMonthlyPayment(new BigDecimal(monthlyPayment.toString()));
                log.info("✅ Mapped monthly_payment: {} for property {}", monthlyPayment, property.getPayPropId());
            }
            
            // Other settings fields
            Object enablePayments = settings.get("enable_payments");
            if (enablePayments instanceof Boolean) {
                property.setEnablePaymentsFromBoolean((Boolean) enablePayments);
            }
            
            Object holdOwnerFunds = settings.get("hold_owner_funds");
            if (holdOwnerFunds instanceof Boolean) {
                property.setHoldOwnerFundsFromBoolean((Boolean) holdOwnerFunds);
            }
            
            Object verifyPayments = settings.get("verify_payments");
            if (verifyPayments instanceof Boolean) {
                property.setVerifyPaymentsFromBoolean((Boolean) verifyPayments);
            }
            
            Object minimumBalance = settings.get("minimum_balance");
            if (minimumBalance instanceof Number) {
                property.setPropertyAccountMinimumBalance(new BigDecimal(minimumBalance.toString()));
            }
            
            // Date fields
            String listingFrom = (String) settings.get("listing_from");
            if (listingFrom != null) {
                try {
                    property.setListedFrom(LocalDate.parse(listingFrom));
                } catch (Exception e) {
                    log.warn("Could not parse listing_from date: {}", listingFrom);
                }
            }
            
            String listingTo = (String) settings.get("listing_to");
            if (listingTo != null) {
                try {
                    property.setListedUntil(LocalDate.parse(listingTo));
                } catch (Exception e) {
                    log.warn("Could not parse listing_to date: {}", listingTo);
                }
            }
        } else {
            log.warn("⚠️ No settings object found in PayProp data for property {}", data.get("id"));
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
        
        // ✅ NEW: Update settings including monthly_payment
        Map<String, Object> settings = (Map<String, Object>) data.get("settings");
        if (settings != null) {
            // Monthly payment - this is the critical field you're missing!
            Object monthlyPayment = settings.get("monthly_payment");
            if (monthlyPayment instanceof Number) {
                property.setMonthlyPayment(new BigDecimal(monthlyPayment.toString()));
                log.info("✅ Updated monthly_payment: {} for property {}", monthlyPayment, property.getPayPropId());
            }
            
            // Other settings updates
            Object enablePayments = settings.get("enable_payments");
            if (enablePayments instanceof Boolean) {
                property.setEnablePaymentsFromBoolean((Boolean) enablePayments);
            }
            
            Object holdOwnerFunds = settings.get("hold_owner_funds");
            if (holdOwnerFunds instanceof Boolean) {
                property.setHoldOwnerFundsFromBoolean((Boolean) holdOwnerFunds);
            }
            
            Object verifyPayments = settings.get("verify_payments");
            if (verifyPayments instanceof Boolean) {
                property.setVerifyPaymentsFromBoolean((Boolean) verifyPayments);
            }
            
            Object minimumBalance = settings.get("minimum_balance");
            if (minimumBalance instanceof Number) {
                property.setPropertyAccountMinimumBalance(new BigDecimal(minimumBalance.toString()));
            }
            
            // Date fields
            String listingFrom = (String) settings.get("listing_from");
            if (listingFrom != null) {
                try {
                    property.setListedFrom(LocalDate.parse(listingFrom));
                } catch (Exception e) {
                    log.warn("Could not parse listing_from date: {}", listingFrom);
                }
            }
            
            String listingTo = (String) settings.get("listing_to");
            if (listingTo != null) {
                try {
                    property.setListedUntil(LocalDate.parse(listingTo));
                } catch (Exception e) {
                    log.warn("Could not parse listing_to date: {}", listingTo);
                }
            }
        } else {
            log.warn("⚠️ No settings object found in PayProp data during update for property {}", property.getPayPropId());
        }
    }

    // ===== RESULT CLASSES =====

    public static class UnifiedSyncResult {
        private SyncResult propertiesResult;
        private SyncResult propertyOwnersResult;
        private SyncResult tenantsResult;
        private SyncResult contractorsResult;
        private SyncResult relationshipsResult;
        private SyncResult tenantRelationshipsResult;
        private String overallError;

        public boolean isOverallSuccess() {
            return overallError == null && 
                (propertiesResult == null || propertiesResult.isSuccess()) &&
                (propertyOwnersResult == null || propertyOwnersResult.isSuccess()) &&
                (tenantsResult == null || tenantsResult.isSuccess()) &&
                (contractorsResult == null || contractorsResult.isSuccess()) &&
                (relationshipsResult == null || relationshipsResult.isSuccess()) &&
                (tenantRelationshipsResult == null || tenantRelationshipsResult.isSuccess());
        }

        public String getSummary() {
            if (overallError != null) return overallError;
            
            StringBuilder summary = new StringBuilder();
            summary.append("Properties: ").append(propertiesResult != null ? propertiesResult.getMessage() : "skipped").append("; ");
            summary.append("Owners: ").append(propertyOwnersResult != null ? propertyOwnersResult.getMessage() : "skipped").append("; ");
            summary.append("Tenants: ").append(tenantsResult != null ? tenantsResult.getMessage() : "skipped").append("; ");
            summary.append("Contractors: ").append(contractorsResult != null ? contractorsResult.getMessage() : "skipped").append("; ");
            summary.append("Owner Relationships: ").append(relationshipsResult != null ? relationshipsResult.getMessage() : "skipped").append("; ");
            summary.append("Tenant Relationships: ").append(tenantRelationshipsResult != null ? tenantRelationshipsResult.getMessage() : "skipped");
            return summary.toString();
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
        
        public SyncResult getTenantRelationshipsResult() { return tenantRelationshipsResult; }
        public void setTenantRelationshipsResult(SyncResult tenantRelationshipsResult) { 
            this.tenantRelationshipsResult = tenantRelationshipsResult; 
        }
        
        public String getOverallError() { return overallError; }
        public void setOverallError(String overallError) { this.overallError = overallError; }

        public SyncResult getContractorsResult() { return contractorsResult; }
        public void setContractorsResult(SyncResult contractorsResult) { this.contractorsResult = contractorsResult; }
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
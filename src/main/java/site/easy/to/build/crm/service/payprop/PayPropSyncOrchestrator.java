// PayPropSyncOrchestrator.java - Database Compatible Version
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.property.TenantService;
import site.easy.to.build.crm.service.property.PropertyOwnerService;
import site.easy.to.build.crm.service.user.UserService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropSyncOrchestrator {

    private final PayPropSyncService payPropSyncService;
    private final PayPropPortfolioSyncService portfolioSyncService;
    private final PayPropConflictResolver conflictResolver;
    private final PayPropChangeDetection payPropChangeDetection;
    private final PayPropSyncLogger syncLogger;
    private final CustomerService customerService;
    private final PropertyService propertyService;
    private final TenantService tenantService;
    private final PropertyOwnerService propertyOwnerService;
    private final UserService userService;
    private final ExecutorService executorService;

    @Value("${payprop.sync.batch-size:10}")
    private int batchSize;

    @Value("${payprop.sync.parallel-enabled:false}")
    private boolean parallelSyncEnabled;

    @Value("${payprop.sync.debug-mode:false}")
    private boolean debugMode;

    @Value("${payprop.sync.debug-sample-size:2}")
    private int debugSampleSize;

    @Autowired
    public PayPropSyncOrchestrator(PayPropSyncService payPropSyncService,
                                  PayPropPortfolioSyncService portfolioSyncService,
                                  PayPropConflictResolver conflictResolver,
                                  PayPropChangeDetection payPropChangeDetection,
                                  PayPropSyncLogger syncLogger,
                                  CustomerService customerService,
                                  PropertyService propertyService,
                                  TenantService tenantService,
                                  PropertyOwnerService propertyOwnerService,
                                  UserService userService) {
        this.payPropSyncService = payPropSyncService;
        this.portfolioSyncService = portfolioSyncService;
        this.conflictResolver = conflictResolver;
        this.payPropChangeDetection = payPropChangeDetection;
        this.syncLogger = syncLogger;
        this.customerService = customerService;
        this.propertyService = propertyService;
        this.tenantService = tenantService;
        this.propertyOwnerService = propertyOwnerService;
        this.userService = userService;
        this.executorService = Executors.newFixedThreadPool(5);
    }

    // ===== MAIN SYNC ORCHESTRATION =====

    /**
     * Complete two-way synchronization between CRM and PayProp
     */
    @Transactional
    public ComprehensiveSyncResult performFullSync(Long initiatedBy) {
        return performFullSync(initiatedBy, false);
    }

    /**
     * Complete two-way synchronization with debug mode control
     */
    @Transactional
    public ComprehensiveSyncResult performFullSync(Long initiatedBy, boolean enableDebugMode) {
        ComprehensiveSyncResult result = new ComprehensiveSyncResult();
        
        if (enableDebugMode) {
            syncLogger.setDebugMode(true);
        }
        
        syncLogger.logSyncStart("FULL_SYNC", initiatedBy);
        
        boolean originalDebugMode = this.debugMode;
        this.debugMode = enableDebugMode;
        
        try {
            // Phase 1: Push CRM changes to PayProp
            result.setCrmToPayPropResult(syncCrmToPayProp(initiatedBy));
            
            // Phase 2: Pull PayProp changes to CRM
            result.setPayPropToCrmResult(syncPayPropToCrm(initiatedBy));
            
            // Phase 3: Resolve conflicts
            result.setConflictResolutionResult(resolveConflicts(initiatedBy));
            
            // Phase 4: Sync portfolios/tags
            result.setPortfolioSyncResult(syncPortfolios(initiatedBy));
            
            syncLogger.logSyncComplete("FULL_SYNC", result.isOverallSuccess(), result.getSummary());
            
        } catch (Exception e) {
            syncLogger.logSyncError("FULL_SYNC", e);
            result.setOverallError(e.getMessage());
        } finally {
            this.debugMode = originalDebugMode;
            
            if (enableDebugMode) {
                syncLogger.clearDebugMode();
            }
        }
        
        return result;
    }

    /**
     * NEW: Complete synchronization with relationship import
     */
    @Transactional
    public ComprehensiveSyncResult performFullSyncWithRelationships(Long initiatedBy) {
        ComprehensiveSyncResult result = new ComprehensiveSyncResult();
        
        syncLogger.logSyncStart("FULL_SYNC_WITH_RELATIONSHIPS", initiatedBy);
        
        try {
            // Phase 1: Basic entity import
            result.setCrmToPayPropResult(syncCrmToPayProp(initiatedBy));
            
            // Phase 2: Entity import from PayProp
            result.setPayPropToCrmResult(syncPayPropToCrm(initiatedBy));
            
            // Phase 3: NEW - Relationship import
            SyncResult tenantPropertyResult = pullTenantsWithPropertiesFromPayProp(initiatedBy);
            SyncResult beneficiaryPropertyResult = pullBeneficiariesWithPropertiesFromPayProp(initiatedBy);
            SyncResult relationshipValidation = validateRelationshipsFromInvoices(initiatedBy);
            
            Map<String, Object> relationshipResults = Map.of(
                "tenantProperties", tenantPropertyResult,
                "beneficiaryProperties", beneficiaryPropertyResult,
                "invoiceValidation", relationshipValidation
            );
            
            result.setRelationshipImportResult(SyncResult.success("All relationships imported", relationshipResults));
            
            // Phase 4: Conflict resolution
            result.setConflictResolutionResult(resolveConflicts(initiatedBy));
            
            // Phase 5: Portfolio sync
            result.setPortfolioSyncResult(syncPortfolios(initiatedBy));
            
            syncLogger.logSyncComplete("FULL_SYNC_WITH_RELATIONSHIPS", result.isOverallSuccess(), result.getSummary());
            
        } catch (Exception e) {
            syncLogger.logSyncError("FULL_SYNC_WITH_RELATIONSHIPS", e);
            result.setOverallError(e.getMessage());
        }
        
        return result;
    }

    // ===== USER HELPER METHOD =====
    
    private User getCurrentUser(Long userId) {
        if (userId != null) {
            try {
                // FIXED: Handle your actual user ID type conversion
                User user = userService.findById(userId.intValue());
                if (user != null) return user;
            } catch (Exception e) {
                System.err.println("Could not find user by ID " + userId + ": " + e.getMessage());
            }
        }
        
        // FIXED: Use your actual method to find users
        try {
            List<User> allUsers = userService.findAll();
            if (!allUsers.isEmpty()) {
                return allUsers.get(0); // Return first available user
            }
        } catch (Exception e) {
            System.err.println("Could not find any users: " + e.getMessage());
        }
        
        throw new RuntimeException("No users found in system - cannot create Customer without User");
    }

    // ===== CRM TO PAYPROP SYNC =====

    private SyncResult syncCrmToPayProp(Long initiatedBy) {
        Map<String, Object> results = new HashMap<>();
        int totalSuccess = 0;
        int totalErrors = 0;

        try {
            // Sync Properties
            SyncResult propertyResult = syncPropertiesToPayProp(initiatedBy);
            results.put("properties", propertyResult);
            if (propertyResult.isSuccess()) totalSuccess++; else totalErrors++;

            // Sync Tenants 
            SyncResult tenantResult = syncTenantsToPayProp(initiatedBy);
            results.put("tenants", tenantResult);
            if (tenantResult.isSuccess()) totalSuccess++; else totalErrors++;

            // Sync Beneficiaries
            SyncResult beneficiaryResult = syncBeneficiariesToPayProp(initiatedBy);
            results.put("beneficiaries", beneficiaryResult);
            if (beneficiaryResult.isSuccess()) totalSuccess++; else totalErrors++;

            String message = String.format("CRM to PayProp sync completed. Success: %d, Errors: %d", 
                                          totalSuccess, totalErrors);
            
            return totalErrors == 0 ? 
                SyncResult.success(message, results) : 
                SyncResult.partial(message, results);
                
        } catch (Exception e) {
            results.put("errors", List.of("CRM to PayProp sync failed: " + e.getMessage()));
            return SyncResult.failure("CRM to PayProp sync failed", results);
        }
    }

    private SyncResult syncPropertiesToPayProp(Long initiatedBy) {
        // FIXED: Use your actual repository methods
        List<Property> properties = propertyService.findAll().stream()
            .filter(p -> p.getPayPropId() == null) // Not yet synced
            .toList();
            
        return processBatchSync(properties, "PROPERTY", initiatedBy, 
            property -> payPropSyncService.syncPropertyToPayProp(property.getId()));
    }

    private SyncResult syncTenantsToPayProp(Long initiatedBy) {
        // FIXED: Use actual repository methods - your database has plural table names
        List<Tenant> tenants = tenantService.findAll().stream()
            .filter(t -> t.getPayPropId() == null) // Not yet synced
            .toList();
            
        return processBatchSync(tenants, "TENANT", initiatedBy, 
            tenant -> payPropSyncService.syncTenantToPayProp(tenant.getId()));
    }

    private SyncResult syncBeneficiariesToPayProp(Long initiatedBy) {
        // FIXED: Use actual repository methods
        List<PropertyOwner> owners = propertyOwnerService.findAll().stream()
            .filter(o -> o.getPayPropId() == null) // Not yet synced
            .toList();
            
        return processBatchSync(owners, "BENEFICIARY", initiatedBy, 
            owner -> payPropSyncService.syncBeneficiaryToPayProp(owner.getId()));
    }

    // ===== PAYPROP TO CRM SYNC =====

    private SyncResult syncPayPropToCrm(Long initiatedBy) {
        Map<String, Object> results = new HashMap<>();
        int totalSuccess = 0;
        int totalErrors = 0;

        try {
            // Pull properties from PayProp
            SyncResult propertyResult = pullPropertiesFromPayProp(initiatedBy);
            results.put("properties", propertyResult);
            if (propertyResult.isSuccess()) totalSuccess++; else totalErrors++;

            // Pull tenants from PayProp
            SyncResult tenantResult = pullTenantsFromPayProp(initiatedBy);
            results.put("tenants", tenantResult);
            if (tenantResult.isSuccess()) totalSuccess++; else totalErrors++;

            // Pull beneficiaries from PayProp
            SyncResult beneficiaryResult = pullBeneficiariesFromPayProp(initiatedBy);
            results.put("beneficiaries", beneficiaryResult);
            if (beneficiaryResult.isSuccess()) totalSuccess++; else totalErrors++;

            String message = String.format("PayProp to CRM sync completed. Success: %d, Errors: %d", 
                                          totalSuccess, totalErrors);
            
            return totalErrors == 0 ? 
                SyncResult.success(message, results) : 
                SyncResult.partial(message, results);
                
        } catch (Exception e) {
            return SyncResult.failure("PayProp to CRM sync failed: " + e.getMessage(), results);
        }
    }

    private SyncResult pullPropertiesFromPayProp(Long initiatedBy) {
        try {
            int page = 1;
            int processedCount = 0;
            int updatedCount = 0;
            int createdCount = 0;
            List<Map<String, Object>> sampleData = new ArrayList<>();

            while (true) {
                PayPropSyncService.PayPropExportResult exportResult = 
                    payPropSyncService.exportPropertiesFromPayProp(page, batchSize);
                
                if (exportResult.getItems().isEmpty()) {
                    break;
                }

                for (Map<String, Object> propertyData : exportResult.getItems()) {
                    try {
                        boolean isNew = updateOrCreatePropertyFromPayProp(propertyData, initiatedBy);
                        if (isNew) createdCount++; else updatedCount++;
                        processedCount++;
                        
                        if (debugMode && sampleData.size() < debugSampleSize) {
                            sampleData.add(createSamplePropertyData(propertyData, isNew));
                        }
                    } catch (Exception e) {
                        syncLogger.logEntityError("PROPERTY_PULL", propertyData.get("id"), e);
                    }
                }

                page++;
            }

            Map<String, Object> details = new HashMap<>();
            details.put("processed", processedCount);
            details.put("created", createdCount);
            details.put("updated", updatedCount);
            
            if (debugMode && !sampleData.isEmpty()) {
                details.put("sampleData", sampleData);
                details.put("debugMode", true);
                details.put("note", "Showing " + sampleData.size() + " sample records for debugging");
            }

            return SyncResult.success("Properties pulled from PayProp", details);
            
        } catch (Exception e) {
            return SyncResult.failure("Failed to pull properties from PayProp: " + e.getMessage());
        }
    }

    private SyncResult pullTenantsFromPayProp(Long initiatedBy) {
        try {
            int page = 1;
            int processedCount = 0;
            int updatedCount = 0;
            int createdCount = 0;
            List<Map<String, Object>> sampleData = new ArrayList<>();

            while (true) {
                PayPropSyncService.PayPropExportResult exportResult = 
                    payPropSyncService.exportTenantsFromPayProp(page, batchSize);
                
                if (exportResult.getItems().isEmpty()) {
                    break;
                }

                for (Map<String, Object> tenantData : exportResult.getItems()) {
                    try {
                        boolean isNew = updateOrCreateTenantFromPayProp(tenantData, initiatedBy);
                        if (isNew) createdCount++; else updatedCount++;
                        processedCount++;
                        
                        if (debugMode && sampleData.size() < debugSampleSize) {
                            sampleData.add(createSampleTenantData(tenantData, isNew));
                        }
                    } catch (Exception e) {
                        syncLogger.logEntityError("TENANT_PULL", tenantData.get("id"), e);
                    }
                }

                page++;
            }

            Map<String, Object> details = new HashMap<>();
            details.put("processed", processedCount);
            details.put("created", createdCount);
            details.put("updated", updatedCount);
            
            if (debugMode && !sampleData.isEmpty()) {
                details.put("sampleData", sampleData);
                details.put("debugMode", true);
                details.put("note", "Showing " + sampleData.size() + " sample records for debugging");
            }

            return SyncResult.success("Tenants pulled from PayProp", details);
            
        } catch (Exception e) {
            return SyncResult.failure("Failed to pull tenants from PayProp: " + e.getMessage());
        }
    }

    private SyncResult pullBeneficiariesFromPayProp(Long initiatedBy) {
        try {
            int page = 1;
            int processedCount = 0;
            int updatedCount = 0;
            int createdCount = 0;
            List<Map<String, Object>> sampleData = new ArrayList<>();

            while (true) {
                PayPropSyncService.PayPropExportResult exportResult = 
                    payPropSyncService.exportBeneficiariesFromPayProp(page, batchSize);
                
                if (exportResult.getItems().isEmpty()) {
                    break;
                }

                for (Map<String, Object> beneficiaryData : exportResult.getItems()) {
                    try {
                        boolean isNew = updateOrCreateBeneficiaryFromPayProp(beneficiaryData, initiatedBy);
                        if (isNew) createdCount++; else updatedCount++;
                        processedCount++;
                        
                        if (debugMode && sampleData.size() < debugSampleSize) {
                            sampleData.add(createSampleBeneficiaryData(beneficiaryData, isNew));
                        }
                    } catch (Exception e) {
                        syncLogger.logEntityError("BENEFICIARY_PULL", beneficiaryData.get("id"), e);
                    }
                }

                page++;
            }

            Map<String, Object> details = new HashMap<>();
            details.put("processed", processedCount);
            details.put("created", createdCount);
            details.put("updated", updatedCount);
            
            if (debugMode && !sampleData.isEmpty()) {
                details.put("sampleData", sampleData);
                details.put("debugMode", true);
                details.put("note", "Showing " + sampleData.size() + " sample records for debugging");
            }

            return SyncResult.success("Beneficiaries pulled from PayProp", details);
            
        } catch (Exception e) {
            return SyncResult.failure("Failed to pull beneficiaries from PayProp: " + e.getMessage());
        }
    }

    // ===== NEW: RELATIONSHIP IMPORT METHODS =====

    /**
     * Import tenants with property relationships from PayProp
     */
    private SyncResult pullTenantsWithPropertiesFromPayProp(Long initiatedBy) {
        try {
            int page = 1;
            int processedCount = 0;
            int relationshipsCreated = 0;
            List<String> errors = new ArrayList<>();

            while (true) {
                PayPropSyncService.PayPropExportResult exportResult = 
                    payPropSyncService.exportTenantsFromPayProp(page, batchSize);
                
                if (exportResult.getItems().isEmpty()) {
                    break;
                }

                for (Map<String, Object> tenantData : exportResult.getItems()) {
                    try {
                        // Create/update tenant first
                        boolean isNew = updateOrCreateTenantFromPayProp(tenantData, initiatedBy);
                        processedCount++;
                        
                        // RELATIONSHIP IMPORT: Process tenant property assignments
                        Object propertiesObj = tenantData.get("properties");
                        if (propertiesObj instanceof List) {
                            List<Map<String, Object>> properties = (List<Map<String, Object>>) propertiesObj;
                            
                            for (Map<String, Object> propertyData : properties) {
                                String propertyPayPropId = (String) propertyData.get("id");
                                String tenantPayPropId = (String) tenantData.get("id");
                                
                                // FIXED: Find entities using PayProp IDs with your database structure
                                Property property = findPropertyByPayPropId(propertyPayPropId);
                                Tenant tenant = findTenantByPayPropId(tenantPayPropId);
                                
                                if (property != null && tenant != null) {
                                    // FIXED: Create relationship using your database structure
                                    // Update tenant to link to property via property ID
                                    tenant.setPropertyId(property.getId());
                                    
                                    // Set rental information from property data
                                    Object monthlyPayment = propertyData.get("monthly_payment_required");
                                    if (monthlyPayment == null) {
                                        monthlyPayment = propertyData.get("monthly_payment");
                                    }
                                    if (monthlyPayment instanceof Number) {
                                        tenant.setMonthlyRent(BigDecimal.valueOf(((Number) monthlyPayment).doubleValue()));
                                    }
                                    
                                    tenantService.save(tenant);
                                    relationshipsCreated++;
                                    
                                    syncLogger.logRelationshipCreated("TENANT_PROPERTY", 
                                        tenantPayPropId, propertyPayPropId, "Monthly rent assignment");
                                }
                            }
                        }
                        
                    } catch (Exception e) {
                        errors.add("Tenant " + tenantData.get("id") + ": " + e.getMessage());
                        syncLogger.logEntityError("TENANT_RELATIONSHIP_PULL", tenantData.get("id"), e);
                    }
                }

                page++;
            }

            Map<String, Object> details = Map.of(
                "processed", processedCount,
                "relationshipsCreated", relationshipsCreated,
                "errors", errors.size(),
                "errorDetails", errors
            );

            return SyncResult.success("Tenants with property relationships imported", details);
            
        } catch (Exception e) {
            return SyncResult.failure("Failed to import tenant relationships: " + e.getMessage());
        }
    }

    /**
     * Import property owners with property relationships from PayProp
     */
    private SyncResult pullBeneficiariesWithPropertiesFromPayProp(Long initiatedBy) {
        try {
            int page = 1;
            int processedCount = 0;
            int relationshipsCreated = 0;
            List<String> errors = new ArrayList<>();

            while (true) {
                PayPropSyncService.PayPropExportResult exportResult = 
                    payPropSyncService.exportBeneficiariesFromPayProp(page, batchSize);
                
                if (exportResult.getItems().isEmpty()) {
                    break;
                }

                for (Map<String, Object> beneficiaryData : exportResult.getItems()) {
                    try {
                        // Create/update beneficiary first
                        boolean isNew = updateOrCreateBeneficiaryFromPayProp(beneficiaryData, initiatedBy);
                        processedCount++;
                        
                        // RELATIONSHIP IMPORT: Process beneficiary property ownership
                        Object propertiesObj = beneficiaryData.get("properties");
                        if (propertiesObj instanceof List) {
                            List<Map<String, Object>> properties = (List<Map<String, Object>>) propertiesObj;
                            
                            for (Map<String, Object> propertyData : properties) {
                                String propertyPayPropId = (String) propertyData.get("id");
                                String beneficiaryPayPropId = (String) beneficiaryData.get("id");
                                
                                // FIXED: Find entities using PayProp IDs
                                Property property = findPropertyByPayPropId(propertyPayPropId);
                                PropertyOwner beneficiary = findPropertyOwnerByPayPropId(beneficiaryPayPropId);
                                
                                if (property != null && beneficiary != null) {
                                    // FIXED: Create PropertyOwner relationship using your database structure
                                    beneficiary.setPropertyId(property.getId());
                                    
                                    // Set ownership details from beneficiary data
                                    Boolean isActiveOwner = (Boolean) beneficiaryData.get("is_active_owner");
                                    if (Boolean.TRUE.equals(isActiveOwner)) {
                                        beneficiary.setIsPrimaryOwner("Y");
                                        beneficiary.setStatus("Active");
                                    }
                                    
                                    // Set financial details from property data
                                    Object monthlyPayment = propertyData.get("monthly_payment_required");
                                    if (monthlyPayment == null) {
                                        monthlyPayment = propertyData.get("monthly_payment");
                                    }
                                    if (monthlyPayment instanceof Number) {
                                        beneficiary.setReceiveRentPayments("Y");
                                    }
                                    
                                    Object accountBalance = propertyData.get("account_balance");
                                    if (accountBalance instanceof Number) {
                                        beneficiary.setComment("Property account balance: £" + accountBalance);
                                    }
                                    
                                    beneficiary.setCreatedAt(LocalDateTime.now());
                                    beneficiary.setCreatedBy(initiatedBy);
                                    
                                    propertyOwnerService.save(beneficiary);
                                    relationshipsCreated++;
                                    
                                    syncLogger.logRelationshipCreated("BENEFICIARY_PROPERTY", 
                                        beneficiaryPayPropId, propertyPayPropId, "Property ownership assignment");
                                }
                            }
                        }
                        
                    } catch (Exception e) {
                        errors.add("Beneficiary " + beneficiaryData.get("id") + ": " + e.getMessage());
                        syncLogger.logEntityError("BENEFICIARY_RELATIONSHIP_PULL", beneficiaryData.get("id"), e);
                    }
                }

                page++;
            }

            Map<String, Object> details = Map.of(
                "processed", processedCount,
                "relationshipsCreated", relationshipsCreated,
                "errors", errors.size(),
                "errorDetails", errors
            );

            return SyncResult.success("Beneficiaries with property relationships imported", details);
            
        } catch (Exception e) {
            return SyncResult.failure("Failed to import beneficiary relationships: " + e.getMessage());
        }
    }

    /**
     * Import and validate relationships using invoice data
     */
    private SyncResult validateRelationshipsFromInvoices(Long initiatedBy) {
        try {
            int page = 1;
            int validatedRelationships = 0;
            int missingRelationships = 0;
            List<String> issues = new ArrayList<>();

            while (true) {
                PayPropSyncService.PayPropExportResult exportResult = 
                    payPropSyncService.exportInvoicesFromPayProp(page, batchSize);
                
                if (exportResult.getItems().isEmpty()) {
                    break;
                }

                for (Map<String, Object> invoiceData : exportResult.getItems()) {
                    try {
                        // Extract relationship data from invoice
                        Map<String, Object> propertyInfo = (Map<String, Object>) invoiceData.get("property");
                        Map<String, Object> tenantInfo = (Map<String, Object>) invoiceData.get("tenant");
                        
                        if (propertyInfo != null && tenantInfo != null) {
                            String propertyPayPropId = (String) propertyInfo.get("id");
                            String tenantPayPropId = (String) tenantInfo.get("id");
                            
                            // Handle both 'email' and 'email_address' fields from invoice
                            String tenantEmail = (String) tenantInfo.get("email");
                            if (tenantEmail == null) {
                                tenantEmail = (String) tenantInfo.get("email_address");
                            }
                            
                            // FIXED: Find entities using your database structure
                            Property property = findPropertyByPayPropId(propertyPayPropId);
                            Tenant tenant = findTenantByPayPropId(tenantPayPropId);
                            
                            // If not found by PayProp ID, try by email
                            if (tenant == null && tenantEmail != null) {
                                tenant = findTenantByEmail(tenantEmail);
                            }
                            
                            if (property != null && tenant != null) {
                                // Check if relationship is correctly established
                                if (tenant.getPropertyId() != null && 
                                    tenant.getPropertyId().equals(property.getId())) {
                                    validatedRelationships++;
                                } else {
                                    // Relationship missing - create it
                                    tenant.setPropertyId(property.getId());
                                    
                                    // Set rent amount from invoice
                                    Object grossAmount = invoiceData.get("gross_amount");
                                    if (grossAmount instanceof Number) {
                                        tenant.setMonthlyRent(BigDecimal.valueOf(((Number) grossAmount).doubleValue()));
                                    }
                                    
                                    tenantService.save(tenant);
                                    missingRelationships++;
                                    
                                    syncLogger.logRelationshipFixed("INVOICE_VALIDATION", 
                                        tenantPayPropId, propertyPayPropId, "Created missing tenant-property relationship from invoice data");
                                }
                            } else {
                                issues.add("Invoice " + invoiceData.get("id") + ": Missing property or tenant in system");
                            }
                        }
                        
                    } catch (Exception e) {
                        issues.add("Invoice " + invoiceData.get("id") + ": " + e.getMessage());
                    }
                }

                page++;
            }

            Map<String, Object> details = Map.of(
                "validatedRelationships", validatedRelationships,
                "fixedRelationships", missingRelationships,
                "issues", issues.size(),
                "issueDetails", issues
            );

            return SyncResult.success("Relationship validation completed", details);
            
        } catch (Exception e) {
            return SyncResult.failure("Failed to validate relationships: " + e.getMessage());
        }
    }

    // ===== ENTITY LOOKUP METHODS (FIXED FOR YOUR DATABASE) =====

    private Property findPropertyByPayPropId(String payPropId) {
        if (payPropId == null) return null;
        try {
            // FIXED: Use your actual repository method
            return propertyService.findAll().stream()
                .filter(p -> payPropId.equals(p.getPayPropId()))
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            System.err.println("Error finding property by PayProp ID " + payPropId + ": " + e.getMessage());
            return null;
        }
    }

    private Tenant findTenantByPayPropId(String payPropId) {
        if (payPropId == null) return null;
        try {
            // FIXED: Use your actual repository method
            return tenantService.findAll().stream()
                .filter(t -> payPropId.equals(t.getPayPropId()))
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            System.err.println("Error finding tenant by PayProp ID " + payPropId + ": " + e.getMessage());
            return null;
        }
    }

    private Tenant findTenantByEmail(String email) {
        if (email == null) return null;
        try {
            // FIXED: Use your actual repository method
            return tenantService.findAll().stream()
                .filter(t -> email.equals(t.getEmailAddress()))
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            System.err.println("Error finding tenant by email " + email + ": " + e.getMessage());
            return null;
        }
    }

    private PropertyOwner findPropertyOwnerByPayPropId(String payPropId) {
        if (payPropId == null) return null;
        try {
            // FIXED: Use your actual repository method
            return propertyOwnerService.findAll().stream()
                .filter(o -> payPropId.equals(o.getPayPropId()))
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            System.err.println("Error finding property owner by PayProp ID " + payPropId + ": " + e.getMessage());
            return null;
        }
    }

    // ===== SAMPLE DATA CREATION FOR DEBUG MODE =====

    private Map<String, Object> createSamplePropertyData(Map<String, Object> fullData, boolean isNew) {
        Map<String, Object> sample = new HashMap<>();
        sample.put("id", fullData.get("id"));
        
        // Handle both 'property_name' and 'name' fields
        String propertyName = (String) fullData.get("property_name");
        if (propertyName == null) {
            propertyName = (String) fullData.get("name");
        }
        sample.put("property_name", propertyName);
        sample.put("customer_reference", fullData.get("customer_reference"));
        sample.put("action", isNew ? "created" : "updated");
        
        // Include simplified address
        Map<String, Object> address = (Map<String, Object>) fullData.get("address");
        if (address != null) {
            Map<String, Object> sampleAddress = new HashMap<>();
            
            String addressLine1 = (String) address.get("first_line");
            if (addressLine1 == null) {
                addressLine1 = (String) address.get("address_line_1");
            }
            sampleAddress.put("address_line_1", addressLine1);
            sampleAddress.put("city", address.get("city"));
            
            String postalCode = (String) address.get("postal_code");
            if (postalCode == null) {
                postalCode = (String) address.get("zip_code");
            }
            sampleAddress.put("postal_code", postalCode);
            sample.put("address", sampleAddress);
        }
        
        return sample;
    }

    private Map<String, Object> createSampleTenantData(Map<String, Object> fullData, boolean isNew) {
        Map<String, Object> sample = new HashMap<>();
        sample.put("id", fullData.get("id"));
        sample.put("first_name", fullData.get("first_name"));
        sample.put("last_name", fullData.get("last_name"));
        sample.put("email_address", fullData.get("email_address"));
        sample.put("account_type", fullData.get("account_type"));
        sample.put("action", isNew ? "created" : "updated");
        return sample;
    }

    private Map<String, Object> createSampleBeneficiaryData(Map<String, Object> fullData, boolean isNew) {
        Map<String, Object> sample = new HashMap<>();
        sample.put("id", fullData.get("id"));
        sample.put("first_name", fullData.get("first_name"));
        sample.put("last_name", fullData.get("last_name"));
        sample.put("email_address", fullData.get("email_address"));
        sample.put("payment_method", fullData.get("payment_method"));
        sample.put("action", isNew ? "created" : "updated");
        return sample;
    }

    // ===== CONFLICT RESOLUTION =====

    private SyncResult resolveConflicts(Long initiatedBy) {
        try {
            List<PayPropConflictResolver.SyncConflict> conflicts = conflictResolver.detectConflicts();
            
            if (conflicts.isEmpty()) {
                return SyncResult.success("No conflicts detected");
            }

            Map<String, Object> results = new HashMap<>();
            int resolvedCount = 0;
            int unresolvedCount = 0;

            for (PayPropConflictResolver.SyncConflict conflict : conflicts) {
                try {
                    PayPropConflictResolver.ConflictResolution resolution = conflictResolver.resolveConflict(conflict);
                    if (resolution.isResolved()) {
                        resolvedCount++;
                    } else {
                        unresolvedCount++;
                    }
                } catch (Exception e) {
                    unresolvedCount++;
                    syncLogger.logConflictError(conflict, e);
                }
            }

            results.put("totalConflicts", conflicts.size());
            results.put("resolved", resolvedCount);
            results.put("unresolved", unresolvedCount);

            return unresolvedCount == 0 ? 
                SyncResult.success("All conflicts resolved", results) :
                SyncResult.partial("Some conflicts remain unresolved", results);
                
        } catch (Exception e) {
            return SyncResult.failure("Conflict resolution failed: " + e.getMessage());
        }
    }

    // ===== PORTFOLIO SYNC =====

    private SyncResult syncPortfolios(Long initiatedBy) {
        try {
            SyncResult portfolioToTagResult = portfolioSyncService.syncAllPortfolios(initiatedBy);
            SyncResult tagToPortfolioResult = portfolioSyncService.pullAllTagsFromPayProp(initiatedBy);
            
            Map<String, Object> details = Map.of(
                "portfolioToTag", portfolioToTagResult,
                "tagToPortfolio", tagToPortfolioResult
            );

            boolean overallSuccess = portfolioToTagResult.isSuccess() && tagToPortfolioResult.isSuccess();
            
            return overallSuccess ? 
                SyncResult.success("Portfolio sync completed successfully", details) :
                SyncResult.partial("Portfolio sync completed with issues", details);
                
        } catch (Exception e) {
            return SyncResult.failure("Portfolio sync failed: " + e.getMessage());
        }
    }

    // ===== HELPER METHODS =====

    private <T> SyncResult processBatchSync(List<T> entities, String entityType, Long initiatedBy, 
                                          EntitySyncFunction<T> syncFunction) {
        if (entities.isEmpty()) {
            return SyncResult.success("No " + entityType.toLowerCase() + " entities to sync");
        }

        int totalEntities = entities.size();
        int successCount = 0;
        int errorCount = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < totalEntities; i += batchSize) {
            int endIndex = Math.min(i + batchSize, totalEntities);
            List<T> batch = entities.subList(i, endIndex);
            
            if (parallelSyncEnabled) {
                // Parallel processing
                List<CompletableFuture<SyncResult>> futures = batch.stream()
                    .map(entity -> CompletableFuture.supplyAsync(() -> {
                        try {
                            syncFunction.sync(entity);
                            return SyncResult.success("Synced");
                        } catch (Exception e) {
                            return SyncResult.failure(e.getMessage());
                        }
                    }, executorService))
                    .toList();
                
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                
                for (CompletableFuture<SyncResult> future : futures) {
                    SyncResult result = future.join();
                    if (result.isSuccess()) {
                        successCount++;
                    } else {
                        errorCount++;
                        errors.add(result.getMessage());
                    }
                }
            } else {
                // Sequential processing
                for (T entity : batch) {
                    try {
                        syncFunction.sync(entity);
                        successCount++;
                    } catch (Exception e) {
                        errorCount++;
                        errors.add(e.getMessage());
                    }
                }
            }
        }

        Map<String, Object> details = Map.of(
            "total", totalEntities,
            "success", successCount,
            "errors", errorCount,
            "errorMessages", errors
        );

        String message = String.format("%s sync completed. Success: %d, Errors: %d", 
                                      entityType, successCount, errorCount);
        
        return errorCount == 0 ? 
            SyncResult.success(message, details) : 
            SyncResult.partial(message, details);
    }

    // ===== ENTITY UPDATE METHODS (FIXED FOR YOUR DATABASE) =====

    private boolean updateOrCreatePropertyFromPayProp(Map<String, Object> propertyData, Long initiatedBy) {
        String payPropId = (String) propertyData.get("id");
        Property existingProperty = findPropertyByPayPropId(payPropId);
        
        if (existingProperty != null) {
            updatePropertyFromPayPropData(existingProperty, propertyData);
            existingProperty.setUpdatedBy(initiatedBy);
            propertyService.save(existingProperty);
            return false; // Not new
        } else {
            Property property = createPropertyFromPayPropData(propertyData);
            property.setCreatedBy(initiatedBy);
            propertyService.save(property);
            return true; // New
        }
    }

    private boolean updateOrCreateTenantFromPayProp(Map<String, Object> tenantData, Long initiatedBy) {
        String payPropId = (String) tenantData.get("id");
        Tenant existingTenant = findTenantByPayPropId(payPropId);
        
        if (existingTenant != null) {
            updateTenantFromPayPropData(existingTenant, tenantData);
            existingTenant.setUpdatedAt(LocalDateTime.now());
            tenantService.save(existingTenant);
            return false; // Not new
        } else {
            Tenant tenant = createTenantFromPayPropData(tenantData);
            tenant.setCreatedAt(LocalDateTime.now());
            tenant.setCreatedBy(initiatedBy);
            tenantService.save(tenant);
            return true; // New
        }
    }

    private boolean updateOrCreateBeneficiaryFromPayProp(Map<String, Object> beneficiaryData, Long initiatedBy) {
        String payPropId = (String) beneficiaryData.get("id");
        PropertyOwner existingOwner = findPropertyOwnerByPayPropId(payPropId);
        
        if (existingOwner != null) {
            updatePropertyOwnerFromPayPropData(existingOwner, beneficiaryData);
            existingOwner.setUpdatedAt(LocalDateTime.now());
            propertyOwnerService.save(existingOwner);
            return false; // Not new
        } else {
            PropertyOwner owner = createPropertyOwnerFromPayPropData(beneficiaryData);
            owner.setCreatedAt(LocalDateTime.now());
            owner.setCreatedBy(initiatedBy);
            propertyOwnerService.save(owner);
            return true; // New
        }
    }

    // ===== UTILITY METHODS (FIXED FOR YOUR DATABASE) =====

    private void updatePropertyFromPayPropData(Property property, Map<String, Object> data) {
        // Handle both 'property_name' and 'name' fields
        String propertyName = (String) data.get("property_name");
        if (propertyName == null) {
            propertyName = (String) data.get("name");
        }
        property.setPropertyName(propertyName);
        property.setCustomerReference((String) data.get("customer_reference"));
        property.setAgentName((String) data.get("agent_name"));
        property.setComment((String) data.get("notes"));
        
        // Update address with PayProp structure handling
        Map<String, Object> address = (Map<String, Object>) data.get("address");
        if (address != null) {
            String addressLine1 = (String) address.get("first_line");
            if (addressLine1 == null) {
                addressLine1 = (String) address.get("address_line_1");
            }
            property.setAddressLine1(addressLine1);
            
            String addressLine2 = (String) address.get("second_line");
            if (addressLine2 == null) {
                addressLine2 = (String) address.get("address_line_2");
            }
            property.setAddressLine2(addressLine2);
            
            String addressLine3 = (String) address.get("third_line");
            if (addressLine3 == null) {
                addressLine3 = (String) address.get("address_line_3");
            }
            property.setAddressLine3(addressLine3);
            
            property.setCity((String) address.get("city"));
            
            String postalCode = (String) address.get("postal_code");
            if (postalCode == null) {
                postalCode = (String) address.get("zip_code");
            }
            property.setPostcode(postalCode);
            property.setCountryCode((String) address.get("country_code"));
        }
        
        // Update settings if present
        Map<String, Object> settings = (Map<String, Object>) data.get("settings");
        if (settings != null) {
            // FIXED: Convert boolean to Y/N for your database
            Boolean enablePayments = (Boolean) settings.get("enable_payments");
            if (enablePayments != null) {
                property.setEnablePayments(enablePayments ? "Y" : "N");
            }
            
            Boolean holdOwnerFunds = (Boolean) settings.get("hold_owner_funds");
            if (holdOwnerFunds != null) {
                property.setHoldOwnerFunds(holdOwnerFunds ? "Y" : "N");
            }
            
            property.setMonthlyPayment((BigDecimal) settings.get("monthly_payment"));
            property.setPropertyAccountMinimumBalance((BigDecimal) settings.get("minimum_balance"));
            property.setListedFrom((LocalDate) settings.get("listing_from"));
            property.setListedUntil((LocalDate) settings.get("listing_to"));
        }
    }

    private Property createPropertyFromPayPropData(Map<String, Object> data) {
        Property property = new Property();
        property.setPayPropId((String) data.get("id"));
        updatePropertyFromPayPropData(property, data);
        return property;
    }

    private void updateTenantFromPayPropData(Tenant tenant, Map<String, Object> data) {
        tenant.setPayPropId((String) data.get("id"));
        tenant.setPayPropCustomerId((String) data.get("customer_id"));
        tenant.setEmailAddress((String) data.get("email_address"));
        tenant.setMobileNumber((String) data.get("mobile_number"));
        tenant.setPhoneNumber((String) data.get("phone"));
        tenant.setComment((String) data.get("comment"));
        
        String firstName = (String) data.get("first_name");
        String lastName = (String) data.get("last_name");
        String businessName = (String) data.get("business_name");
        
        tenant.setFirstName(firstName);
        tenant.setLastName(lastName);
        tenant.setBusinessName(businessName);
        
        // FIXED: Set account type based on available data with your enum handling
        if (firstName != null && !firstName.trim().isEmpty() && 
            lastName != null && !lastName.trim().isEmpty()) {
            tenant.setAccountType(AccountType.individual);
        } else if (businessName != null && !businessName.trim().isEmpty()) {
            tenant.setAccountType(AccountType.business);
        } else {
            tenant.setAccountType(AccountType.individual);
        }
        
        // Update address with PayProp structure handling
        Map<String, Object> address = (Map<String, Object>) data.get("address");
        if (address != null) {
            String addressLine1 = (String) address.get("first_line");
            if (addressLine1 == null) {
                addressLine1 = (String) address.get("address_line_1");
            }
            tenant.setAddressLine1(addressLine1);
            
            String addressLine2 = (String) address.get("second_line");
            if (addressLine2 == null) {
                addressLine2 = (String) address.get("address_line_2");
            }
            tenant.setAddressLine2(addressLine2);
            
            String addressLine3 = (String) address.get("third_line");
            if (addressLine3 == null) {
                addressLine3 = (String) address.get("address_line_3");
            }
            tenant.setAddressLine3(addressLine3);
            
            tenant.setCity((String) address.get("city"));
            
            String postalCode = (String) address.get("postal_code");
            if (postalCode == null) {
                postalCode = (String) address.get("zip_code");
            }
            tenant.setPostcode(postalCode);
            tenant.setCountry((String) address.get("country_code"));
        }
    }

    private Tenant createTenantFromPayPropData(Map<String, Object> data) {
        Tenant tenant = new Tenant();
        updateTenantFromPayPropData(tenant, data);
        return tenant;
    }

    private void updatePropertyOwnerFromPayPropData(PropertyOwner owner, Map<String, Object> data) {
        owner.setPayPropId((String) data.get("id"));
        owner.setPayPropCustomerId((String) data.get("customer_id"));
        owner.setEmailAddress((String) data.get("email_address"));
        owner.setMobile((String) data.get("mobile_number"));
        owner.setComment((String) data.get("comment"));
        
        String firstName = (String) data.get("first_name");
        String lastName = (String) data.get("last_name");
        String businessName = (String) data.get("business_name");
        
        owner.setFirstName(firstName);
        owner.setLastName(lastName);
        owner.setBusinessName(businessName);
        
        // FIXED: Handle missing business name issue from your API inspection
        if (firstName != null && !firstName.trim().isEmpty() && 
            lastName != null && !lastName.trim().isEmpty()) {
            owner.setAccountType(AccountType.individual);
        } else if (businessName != null && !businessName.trim().isEmpty()) {
            owner.setAccountType(AccountType.business);
        } else {
            // Fallback for missing names
            owner.setAccountType(AccountType.individual);
            String email = (String) data.get("email_address");
            if (email != null && !email.trim().isEmpty()) {
                String emailPrefix = email.contains("@") ? email.substring(0, email.indexOf("@")) : email;
                owner.setFirstName("Property");
                owner.setLastName("Owner - " + emailPrefix);
            } else {
                owner.setFirstName("Property");
                owner.setLastName("Owner - " + data.get("id"));
            }
        }
        
        // Update address from billing_address
        Map<String, Object> billingAddress = (Map<String, Object>) data.get("billing_address");
        if (billingAddress != null) {
            owner.setAddressLine1((String) billingAddress.get("first_line"));
            owner.setAddressLine2((String) billingAddress.get("second_line"));
            owner.setAddressLine3((String) billingAddress.get("third_line"));
            owner.setCity((String) billingAddress.get("city"));
            owner.setPostalCode((String) billingAddress.get("postal_code"));
            owner.setCountry((String) billingAddress.get("country_code"));
            owner.setState((String) billingAddress.get("state"));
        }
        
        // Set other PayProp fields
        owner.setVatNumber((String) data.get("vat_number"));
        owner.setIdNumber((String) data.get("id_reg_number"));
        
        // FIXED: Handle Y/N enum conversion
        Boolean notifyEmail = (Boolean) data.get("notify_email");
        if (notifyEmail != null) {
            owner.setEmailEnabled(notifyEmail ? "Y" : "N");
        }
        Boolean notifySms = (Boolean) data.get("notify_sms");
        if (notifySms != null) {
            owner.setNotifySms(notifySms ? "Y" : "N");
        }
    }

    private PropertyOwner createPropertyOwnerFromPayPropData(Map<String, Object> data) {
        PropertyOwner owner = new PropertyOwner();
        updatePropertyOwnerFromPayPropData(owner, data);
        return owner;
    }

    // ===== INTERFACE DEFINITIONS =====

    @FunctionalInterface
    private interface EntitySyncFunction<T> {
        void sync(T entity) throws Exception;
    }

    // ===== RESULT CLASSES =====

    public static class ComprehensiveSyncResult {
        private SyncResult crmToPayPropResult;
        private SyncResult payPropToCrmResult;
        private SyncResult conflictResolutionResult;
        private SyncResult portfolioSyncResult;
        private SyncResult relationshipImportResult;
        private String overallError;
        private String message;

        public boolean isOverallSuccess() {
            return overallError == null && 
                   (crmToPayPropResult == null || crmToPayPropResult.isSuccess()) &&
                   (payPropToCrmResult == null || payPropToCrmResult.isSuccess()) &&
                   (conflictResolutionResult == null || conflictResolutionResult.isSuccess()) &&
                   (portfolioSyncResult == null || portfolioSyncResult.isSuccess()) &&
                   (relationshipImportResult == null || relationshipImportResult.isSuccess());
        }

        public String getSummary() {
            if (overallError != null) return overallError;
            if (message != null) return message;
            return "Comprehensive sync completed";
        }

        // Getters and setters
        public SyncResult getCrmToPayPropResult() { return crmToPayPropResult; }
        public void setCrmToPayPropResult(SyncResult crmToPayPropResult) { this.crmToPayPropResult = crmToPayPropResult; }
        
        public SyncResult getPayPropToCrmResult() { return payPropToCrmResult; }
        public void setPayPropToCrmResult(SyncResult payPropToCrmResult) { this.payPropToCrmResult = payPropToCrmResult; }
        
        public SyncResult getConflictResolutionResult() { return conflictResolutionResult; }
        public void setConflictResolutionResult(SyncResult conflictResolutionResult) { this.conflictResolutionResult = conflictResolutionResult; }
        
        public SyncResult getPortfolioSyncResult() { return portfolioSyncResult; }
        public void setPortfolioSyncResult(SyncResult portfolioSyncResult) { this.portfolioSyncResult = portfolioSyncResult; }
        
        public SyncResult getRelationshipImportResult() { return relationshipImportResult; }
        public void setRelationshipImportResult(SyncResult relationshipImportResult) { this.relationshipImportResult = relationshipImportResult; }
        
        public String getOverallError() { return overallError; }
        public void setOverallError(String overallError) { this.overallError = overallError; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
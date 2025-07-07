// PayPropSyncOrchestrator.java - API-Compliant Phone Number Handling
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
import site.easy.to.build.crm.service.property.TenantService;
import site.easy.to.build.crm.service.property.PropertyOwnerService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.entity.PaymentMethod; 
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import site.easy.to.build.crm.util.AuthenticationUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropSyncOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PayPropSyncOrchestrator.class);

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
    public ComprehensiveSyncResult performFullSync(Long initiatedBy) {
        return performFullSync(initiatedBy, false);
    }

    /**
     * Complete two-way synchronization with debug mode control
     */
    public ComprehensiveSyncResult performFullSync(Long initiatedBy, boolean enableDebugMode) {
        ComprehensiveSyncResult result = new ComprehensiveSyncResult();
        
        if (enableDebugMode) {
            syncLogger.setDebugMode(true);
        }
        
        syncLogger.logSyncStart("FULL_SYNC", initiatedBy);
        
        boolean originalDebugMode = this.debugMode;
        this.debugMode = enableDebugMode;
        
        try {
            // Phase 1: Push CRM changes to PayProp (separate transaction)
            result.setCrmToPayPropResult(syncCrmToPayPropInSeparateTransaction(initiatedBy));
            
            // Phase 2: Pull PayProp changes to CRM (separate transaction)
            result.setPayPropToCrmResult(syncPayPropToCrmInSeparateTransaction(initiatedBy));
            
            // Phase 3: Resolve conflicts (separate transaction)
            result.setConflictResolutionResult(resolveConflictsInSeparateTransaction(initiatedBy));
            
            // Phase 4: Sync portfolios/tags (separate transaction)
            result.setPortfolioSyncResult(syncPortfoliosInSeparateTransaction(initiatedBy));
            
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
     * Intelligent sync based on change detection
     */
    public ComprehensiveSyncResult performIntelligentSync(Long initiatedBy) {
        ComprehensiveSyncResult result = new ComprehensiveSyncResult();
        
        syncLogger.logSyncStart("INTELLIGENT_SYNC", initiatedBy);
        
        try {
            // For now, just perform a basic sync since change detection logic is complex
            // In the future, this would detect changes since last sync and only sync what's changed
            
            // Phase 1: Quick property sync (only unsynced) - separate transaction
            result.setCrmToPayPropResult(syncPropertiesToPayPropInSeparateTransaction(initiatedBy));
            
            // Phase 2: Quick tenant sync (only unsynced) - separate transaction
            SyncResult tenantResult = syncTenantsToPayPropInSeparateTransaction(initiatedBy);
            
            // Phase 3: Quick beneficiary sync (only unsynced) - separate transaction
            SyncResult beneficiaryResult = syncBeneficiariesToPayPropInSeparateTransaction(initiatedBy);
            
            // Combine results
            Map<String, Object> combinedResults = new HashMap<>();
            combinedResults.put("properties", result.getCrmToPayPropResult());
            combinedResults.put("tenants", tenantResult);
            combinedResults.put("beneficiaries", beneficiaryResult);
            
            result.setPayPropToCrmResult(SyncResult.success("Intelligent sync completed", combinedResults));
            
            syncLogger.logSyncComplete("INTELLIGENT_SYNC", result.isOverallSuccess(), result.getSummary());
            
        } catch (Exception e) {
            syncLogger.logSyncError("INTELLIGENT_SYNC", e);
            result.setOverallError(e.getMessage());
        }
        
        return result;
    }

    /**
     * Complete synchronization with relationship import
     */
    public ComprehensiveSyncResult performFullSyncWithRelationships(Long initiatedBy) {
        ComprehensiveSyncResult result = new ComprehensiveSyncResult();
        
        syncLogger.logSyncStart("FULL_SYNC_WITH_RELATIONSHIPS", initiatedBy);
        
        try {
            // Phase 1: Basic entity import (separate transaction)
            result.setCrmToPayPropResult(syncCrmToPayPropInSeparateTransaction(initiatedBy));
            
            // Phase 2: Entity import from PayProp (separate transaction)
            result.setPayPropToCrmResult(syncPayPropToCrmInSeparateTransaction(initiatedBy));
            
            // Phase 3: Relationship import (separate transaction)
            result.setRelationshipImportResult(syncRelationshipsInSeparateTransaction(initiatedBy));
            
            // Phase 4: Conflict resolution (separate transaction)
            result.setConflictResolutionResult(resolveConflictsInSeparateTransaction(initiatedBy));
            
            // Phase 5: Portfolio sync (separate transaction)
            result.setPortfolioSyncResult(syncPortfoliosInSeparateTransaction(initiatedBy));
            
            syncLogger.logSyncComplete("FULL_SYNC_WITH_RELATIONSHIPS", result.isOverallSuccess(), result.getSummary());
            
        } catch (Exception e) {
            syncLogger.logSyncError("FULL_SYNC_WITH_RELATIONSHIPS", e);
            result.setOverallError(e.getMessage());
        }
        
        return result;
    }

    // ===== USER HELPER METHOD =====
    
    @Autowired
    private AuthenticationUtils authenticationUtils;

    private User getCurrentUser(Long userId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                
                // For OAuth2 authentication, get email and find user by email
                if (auth instanceof OAuth2AuthenticationToken) {
                    OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) auth;
                    String email = oauth2Token.getPrincipal().getAttribute("email");
                    
                    if (email != null) {
                        User currentUser = userService.findByEmail(email);
                        if (currentUser != null) {
                            log.info("✅ Found user by email {}: ID={}, username={}", email, currentUser.getId(), currentUser.getUsername());
                            return currentUser;
                        }
                    }
                }
                
                // Fallback: try AuthenticationUtils method
                int currentUserId = authenticationUtils.getLoggedInUserId(auth);
                if (currentUserId > 0) {
                    User currentUser = userService.findById(currentUserId);
                    if (currentUser != null) {
                        log.info("✅ Found user by AuthenticationUtils: ID={}", currentUser.getId());
                        return currentUser;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not get authenticated user: {}", e.getMessage());
        }
        
        throw new RuntimeException("No valid user found for portfolio creation");
    }

    // ===== SEPARATE TRANSACTION METHODS =====
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult syncCrmToPayPropInSeparateTransaction(Long initiatedBy) {
        return syncCrmToPayProp(initiatedBy);
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult syncPayPropToCrmInSeparateTransaction(Long initiatedBy) {
        return syncPayPropToCrm(initiatedBy);
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult syncPropertiesToPayPropInSeparateTransaction(Long initiatedBy) {
        return syncPropertiesToPayProp(initiatedBy);
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult syncTenantsToPayPropInSeparateTransaction(Long initiatedBy) {
        log.info("Tenant sync to PayProp is temporarily disabled (read-only mode due to insufficient permissions)");
        return SyncResult.success("Tenant sync to PayProp skipped (read-only mode)", 
            Map.of("reason", "Insufficient PayProp permissions", "mode", "read-only"));
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult syncBeneficiariesToPayPropInSeparateTransaction(Long initiatedBy) {
        return syncBeneficiariesToPayProp(initiatedBy);
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult syncRelationshipsInSeparateTransaction(Long initiatedBy) {
        try {
            SyncResult tenantPropertyResult = pullTenantsWithPropertiesFromPayProp(initiatedBy);
            SyncResult beneficiaryPropertyResult = pullBeneficiariesWithPropertiesFromPayProp(initiatedBy);
            SyncResult relationshipValidation = validateRelationshipsFromInvoices(initiatedBy);
            
            Map<String, Object> relationshipResults = Map.of(
                "tenantProperties", tenantPropertyResult,
                "beneficiaryProperties", beneficiaryPropertyResult,
                "invoiceValidation", relationshipValidation
            );
            
            return SyncResult.success("All relationships imported", relationshipResults);
        } catch (Exception e) {
            return SyncResult.failure("Relationship import failed: " + e.getMessage());
        }
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult resolveConflictsInSeparateTransaction(Long initiatedBy) {
        return resolveConflicts(initiatedBy);
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult syncPortfoliosInSeparateTransaction(Long initiatedBy) {
        return syncPortfolios(initiatedBy);
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

            // TEMPORARILY DISABLED: Skip tenant sync to PayProp due to permission restrictions
            log.info("Skipping tenant sync to PayProp (read-only mode - insufficient permissions)");
            results.put("tenants", SyncResult.success("Tenant sync to PayProp skipped (read-only mode)"));
            totalSuccess++;

            // Sync Beneficiaries
            SyncResult beneficiaryResult = syncBeneficiariesToPayProp(initiatedBy);
            results.put("beneficiaries", beneficiaryResult);
            if (beneficiaryResult.isSuccess()) totalSuccess++; else totalErrors++;

            String message = String.format("CRM to PayProp sync completed. Success: %d, Errors: %d (Tenants: read-only mode)", 
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
        List<Property> properties = propertyService.findAll().stream()
            .filter(p -> p.getPayPropId() == null) // Not yet synced
            .toList();
            
        return processBatchSync(properties, "PROPERTY", initiatedBy, 
            property -> payPropSyncService.syncPropertyToPayProp(property.getId()));
    }

    private SyncResult syncTenantsToPayProp(Long initiatedBy) {
        // TEMPORARILY DISABLED: Due to PayProp permission restrictions
        log.info("Tenant sync to PayProp is temporarily disabled (read-only mode)");
        
        List<Tenant> tenantsNeedingSync = tenantService.findAll().stream()
            .filter(t -> t.getPayPropId() == null) // Not yet synced
            .toList();
            
        return SyncResult.success("Tenant sync to PayProp skipped (read-only mode)", 
            Map.of(
                "skippedCount", tenantsNeedingSync.size(),
                "reason", "Insufficient PayProp permissions - tenant creation denied",
                "mode", "read-only",
                "note", "Tenants will only be imported from PayProp, not exported to PayProp"
            ));
    }

    private SyncResult syncBeneficiariesToPayProp(Long initiatedBy) {
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
            int errorCount = 0;
            List<Map<String, Object>> sampleData = new ArrayList<>();
            List<String> errors = new ArrayList<>();

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
                        errorCount++;
                        String propertyId = String.valueOf(propertyData.get("id"));
                        log.error("Failed to sync property {}: {}", propertyId, e.getMessage());
                        errors.add("Property " + propertyId + ": " + e.getMessage());
                        syncLogger.logEntityError("PROPERTY_PULL", propertyData.get("id"), e);
                    }
                }

                page++;
            }

            Map<String, Object> details = new HashMap<>();
            details.put("processed", processedCount);
            details.put("created", createdCount);
            details.put("updated", updatedCount);
            details.put("errors", errorCount);
            
            if (errorCount > 0) {
                details.put("errorDetails", errors);
            }
            
            if (debugMode && !sampleData.isEmpty()) {
                details.put("sampleData", sampleData);
                details.put("debugMode", true);
                details.put("note", "Showing " + sampleData.size() + " sample records for debugging");
            }

            String message = String.format("Properties pulled from PayProp. Success: %d, Errors: %d", 
                (createdCount + updatedCount), errorCount);
            
            return errorCount == 0 ? 
                SyncResult.success(message, details) : 
                SyncResult.partial(message, details);
            
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
            int errorCount = 0;
            List<Map<String, Object>> sampleData = new ArrayList<>();
            List<String> errors = new ArrayList<>();

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
                        errorCount++;
                        String tenantId = String.valueOf(tenantData.get("id"));
                        log.error("Failed to sync tenant {}: {}", tenantId, e.getMessage());
                        errors.add("Tenant " + tenantId + ": " + e.getMessage());
                        syncLogger.logEntityError("TENANT_PULL", tenantData.get("id"), e);
                    }
                }

                page++;
            }

            Map<String, Object> details = new HashMap<>();
            details.put("processed", processedCount);
            details.put("created", createdCount);
            details.put("updated", updatedCount);
            details.put("errors", errorCount);
            
            if (errorCount > 0) {
                details.put("errorDetails", errors);
            }
            
            if (debugMode && !sampleData.isEmpty()) {
                details.put("sampleData", sampleData);
                details.put("debugMode", true);
                details.put("note", "Showing " + sampleData.size() + " sample records for debugging");
            }

            String message = String.format("Tenants pulled from PayProp. Success: %d, Errors: %d", 
                (createdCount + updatedCount), errorCount);
            
            return errorCount == 0 ? 
                SyncResult.success(message, details) : 
                SyncResult.partial(message, details);
            
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
            int errorCount = 0;
            List<Map<String, Object>> sampleData = new ArrayList<>();
            List<String> errors = new ArrayList<>();

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
                        errorCount++;
                        String beneficiaryId = String.valueOf(beneficiaryData.get("id"));
                        log.error("Failed to sync beneficiary {}: {}", beneficiaryId, e.getMessage());
                        errors.add("Beneficiary " + beneficiaryId + ": " + e.getMessage());
                        syncLogger.logEntityError("BENEFICIARY_PULL", beneficiaryData.get("id"), e);
                    }
                }

                page++;
            }

            Map<String, Object> details = new HashMap<>();
            details.put("processed", processedCount);
            details.put("created", createdCount);
            details.put("updated", updatedCount);
            details.put("errors", errorCount);
            
            if (errorCount > 0) {
                details.put("errorDetails", errors);
            }
            
            if (debugMode && !sampleData.isEmpty()) {
                details.put("sampleData", sampleData);
                details.put("debugMode", true);
                details.put("note", "Showing " + sampleData.size() + " sample records for debugging");
            }

            String message = String.format("Beneficiaries pulled from PayProp. Success: %d, Errors: %d", 
                (createdCount + updatedCount), errorCount);
            
            return errorCount == 0 ? 
                SyncResult.success(message, details) : 
                SyncResult.partial(message, details);
            
        } catch (Exception e) {
            return SyncResult.failure("Failed to pull beneficiaries from PayProp: " + e.getMessage());
        }
    }

    // ===== RELATIONSHIP IMPORT METHODS =====

    private SyncResult pullTenantsWithPropertiesFromPayProp(Long initiatedBy) {
        try {
            int page = 1;
            int processedCount = 0;
            int relationshipsCreated = 0;
            int errorCount = 0;
            List<String> errors = new ArrayList<>();

            while (true) {
                PayPropSyncService.PayPropExportResult exportResult = 
                    payPropSyncService.exportTenantsFromPayProp(page, batchSize);
                
                if (exportResult.getItems().isEmpty()) {
                    break;
                }

                for (Map<String, Object> tenantData : exportResult.getItems()) {
                    try {
                        boolean isNew = updateOrCreateTenantFromPayProp(tenantData, initiatedBy);
                        processedCount++;
                        
                        Object propertiesObj = tenantData.get("properties");
                        if (propertiesObj instanceof List) {
                            List<Map<String, Object>> properties = (List<Map<String, Object>>) propertiesObj;
                            
                            for (Map<String, Object> propertyData : properties) {
                                try {
                                    String propertyPayPropId = (String) propertyData.get("id");
                                    String tenantPayPropId = (String) tenantData.get("id");
                                    
                                    Property property = findPropertyByPayPropId(propertyPayPropId);
                                    Tenant tenant = findTenantByPayPropId(tenantPayPropId);
                                    
                                    if (property != null && tenant != null) {
                                        String relationshipInfo = "Linked to property: " + propertyPayPropId;
                                        if (tenant.getComment() != null && !tenant.getComment().isEmpty()) {
                                            tenant.setComment(tenant.getComment() + "; " + relationshipInfo);
                                        } else {
                                            tenant.setComment(relationshipInfo);
                                        }
                                        
                                        Object monthlyPayment = propertyData.get("monthly_payment_required");
                                        if (monthlyPayment == null) {
                                            monthlyPayment = propertyData.get("monthly_payment");
                                        }
                                        if (monthlyPayment instanceof Number) {
                                            String rentInfo = "Monthly rent: £" + ((Number) monthlyPayment).doubleValue();
                                            tenant.setComment(tenant.getComment() + "; " + rentInfo);
                                        }
                                        
                                        try {
                                            tenantService.save(tenant);
                                            relationshipsCreated++;
                                            syncLogger.logRelationshipCreated("TENANT_PROPERTY", 
                                                tenantPayPropId, propertyPayPropId, "Property link via comment");
                                        } catch (DataIntegrityViolationException e) {
                                            log.warn("Duplicate key error when saving tenant {} relationship to property {}, skipping", 
                                                tenantPayPropId, propertyPayPropId);
                                            errors.add("Duplicate tenant relationship: " + tenantPayPropId + " -> " + propertyPayPropId);
                                        }
                                    }
                                } catch (Exception e) {
                                    String propertyId = String.valueOf(propertyData.get("id"));
                                    log.error("Failed to create relationship for tenant {} to property {}: {}", 
                                        tenantData.get("id"), propertyId, e.getMessage());
                                    errors.add("Relationship error - tenant " + tenantData.get("id") + " to property " + propertyId + ": " + e.getMessage());
                                }
                            }
                        }
                        
                    } catch (Exception e) {
                        errorCount++;
                        String tenantId = String.valueOf(tenantData.get("id"));
                        log.error("Failed to process tenant {} with relationships: {}", tenantId, e.getMessage());
                        errors.add("Tenant " + tenantId + ": " + e.getMessage());
                        syncLogger.logEntityError("TENANT_RELATIONSHIP_PULL", tenantData.get("id"), e);
                    }
                }

                page++;
            }

            Map<String, Object> details = Map.of(
                "processed", processedCount,
                "relationshipsCreated", relationshipsCreated,
                "errors", errorCount,
                "errorDetails", errors
            );

            String message = String.format("Tenants with relationships imported. Success: %d, Relationships: %d, Errors: %d", 
                (processedCount - errorCount), relationshipsCreated, errorCount);
            
            return errorCount == 0 ? 
                SyncResult.success(message, details) : 
                SyncResult.partial(message, details);
            
        } catch (Exception e) {
            return SyncResult.failure("Failed to import tenant relationships: " + e.getMessage());
        }
    }

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
                        boolean isNew = updateOrCreateBeneficiaryFromPayProp(beneficiaryData, initiatedBy);
                        processedCount++;
                        
                        Object propertiesObj = beneficiaryData.get("properties");
                        if (propertiesObj instanceof List) {
                            List<Map<String, Object>> properties = (List<Map<String, Object>>) propertiesObj;
                            
                            for (Map<String, Object> propertyData : properties) {
                                String propertyPayPropId = (String) propertyData.get("id");
                                String beneficiaryPayPropId = (String) beneficiaryData.get("id");
                                
                                Property property = findPropertyByPayPropId(propertyPayPropId);
                                PropertyOwner beneficiary = findPropertyOwnerByPayPropId(beneficiaryPayPropId);
                                
                                if (property != null && beneficiary != null) {
                                    String relationshipInfo = "Owns property: " + propertyPayPropId;
                                    if (beneficiary.getComment() != null && !beneficiary.getComment().isEmpty()) {
                                        beneficiary.setComment(beneficiary.getComment() + "; " + relationshipInfo);
                                    } else {
                                        beneficiary.setComment(relationshipInfo);
                                    }
                                    
                                    Boolean isActiveOwner = (Boolean) beneficiaryData.get("is_active_owner");
                                    if (Boolean.TRUE.equals(isActiveOwner)) {
                                        beneficiary.setIsPrimaryOwner("Y");
                                        beneficiary.setStatus("Active");
                                    }
                                    
                                    Object monthlyPayment = propertyData.get("monthly_payment_required");
                                    if (monthlyPayment == null) {
                                        monthlyPayment = propertyData.get("monthly_payment");
                                    }
                                    if (monthlyPayment instanceof Number) {
                                        beneficiary.setReceiveRentPayments("Y");
                                        String rentInfo = "Property rent: £" + ((Number) monthlyPayment).doubleValue();
                                        beneficiary.setComment(beneficiary.getComment() + "; " + rentInfo);
                                    }
                                    
                                    Object accountBalance = propertyData.get("account_balance");
                                    if (accountBalance instanceof Number) {
                                        String balanceInfo = "Property account balance: £" + accountBalance;
                                        beneficiary.setComment(beneficiary.getComment() + "; " + balanceInfo);
                                    }
                                    
                                    beneficiary.setCreatedAt(LocalDateTime.now());
                                    beneficiary.setCreatedBy(initiatedBy);
                                    
                                    try {
                                        propertyOwnerService.save(beneficiary);
                                        relationshipsCreated++;
                                        syncLogger.logRelationshipCreated("BENEFICIARY_PROPERTY", 
                                            beneficiaryPayPropId, propertyPayPropId, "Property ownership via comment");
                                    } catch (DataIntegrityViolationException e) {
                                        log.warn("Duplicate key error when saving beneficiary {} relationship to property {}, skipping", 
                                            beneficiaryPayPropId, propertyPayPropId);
                                        errors.add("Duplicate beneficiary relationship: " + beneficiaryPayPropId + " -> " + propertyPayPropId);
                                    }
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
                        Map<String, Object> propertyInfo = (Map<String, Object>) invoiceData.get("property");
                        Map<String, Object> tenantInfo = (Map<String, Object>) invoiceData.get("tenant");
                        
                        if (propertyInfo != null && tenantInfo != null) {
                            String propertyPayPropId = (String) propertyInfo.get("id");
                            String tenantPayPropId = (String) tenantInfo.get("id");
                            
                            String tenantEmail = (String) tenantInfo.get("email");
                            if (tenantEmail == null) {
                                tenantEmail = (String) tenantInfo.get("email_address");
                            }
                            
                            Property property = findPropertyByPayPropId(propertyPayPropId);
                            Tenant tenant = findTenantByPayPropId(tenantPayPropId);
                            
                            if (tenant == null && tenantEmail != null) {
                                tenant = findTenantByEmail(tenantEmail);
                            }
                            
                            if (property != null && tenant != null) {
                                String currentComment = tenant.getComment();
                                boolean hasRelationship = currentComment != null && 
                                    currentComment.contains("Linked to property: " + propertyPayPropId);
                                
                                if (hasRelationship) {
                                    validatedRelationships++;
                                } else {
                                    String relationshipInfo = "Linked to property: " + propertyPayPropId;
                                    if (currentComment != null && !currentComment.isEmpty()) {
                                        tenant.setComment(currentComment + "; " + relationshipInfo);
                                    } else {
                                        tenant.setComment(relationshipInfo);
                                    }
                                    
                                    Object grossAmount = invoiceData.get("gross_amount");
                                    if (grossAmount instanceof Number) {
                                        String rentInfo = "Monthly rent: £" + ((Number) grossAmount).doubleValue();
                                        tenant.setComment(tenant.getComment() + "; " + rentInfo);
                                    }
                                    
                                    try {
                                        tenantService.save(tenant);
                                        missingRelationships++;
                                        syncLogger.logRelationshipFixed("INVOICE_VALIDATION", 
                                            tenantPayPropId, propertyPayPropId, "Created missing tenant-property relationship from invoice data");
                                    } catch (DataIntegrityViolationException e) {
                                        log.warn("Duplicate key error when fixing tenant {} relationship from invoice, skipping", tenantPayPropId);
                                        issues.add("Duplicate tenant fix: " + tenantPayPropId + " from invoice " + invoiceData.get("id"));
                                    }
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

    // ===== ENTITY LOOKUP METHODS =====

    private Property findPropertyByPayPropId(String payPropId) {
        if (payPropId == null) return null;
        try {
            return propertyService.findAll().stream()
                .filter(p -> payPropId.equals(p.getPayPropId()))
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            log.error("Error finding property by PayProp ID {}: {}", payPropId, e.getMessage());
            return null;
        }
    }

    private Tenant findTenantByPayPropId(String payPropId) {
        if (payPropId == null) return null;
        try {
            return tenantService.findAll().stream()
                .filter(t -> payPropId.equals(t.getPayPropId()))
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            log.error("Error finding tenant by PayProp ID {}: {}", payPropId, e.getMessage());
            return null;
        }
    }

    private Tenant findTenantByEmail(String email) {
        if (email == null) return null;
        try {
            return tenantService.findAll().stream()
                .filter(t -> email.equals(t.getEmailAddress()))
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            log.error("Error finding tenant by email {}: {}", email, e.getMessage());
            return null;
        }
    }

    private PropertyOwner findPropertyOwnerByPayPropId(String payPropId) {
        if (payPropId == null) return null;
        try {
            return propertyOwnerService.findAll().stream()
                .filter(o -> payPropId.equals(o.getPayPropId()))
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            log.error("Error finding property owner by PayProp ID {}: {}", payPropId, e.getMessage());
            return null;
        }
    }

    // ===== SAMPLE DATA CREATION FOR DEBUG MODE =====

    private Map<String, Object> createSamplePropertyData(Map<String, Object> fullData, boolean isNew) {
        Map<String, Object> sample = new HashMap<>();
        sample.put("id", fullData.get("id"));
        
        String propertyName = (String) fullData.get("property_name");
        if (propertyName == null) {
            propertyName = (String) fullData.get("name");
        }
        sample.put("property_name", propertyName);
        sample.put("customer_reference", fullData.get("customer_reference"));
        sample.put("action", isNew ? "created" : "updated");
        
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
                List<CompletableFuture<SyncResult>> futures = batch.stream()
                    .map(entity -> CompletableFuture.supplyAsync(() -> {
                        try {
                            syncFunction.sync(entity);
                            return SyncResult.success("Synced");
                        } catch (Exception e) {
                            log.error("Failed to sync {} entity: {}", entityType.toLowerCase(), e.getMessage());
                            return SyncResult.failure(e.getMessage());
                        }
                    }, executorService))
                    .toList();
                
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                
                for (CompletableFuture<SyncResult> future : futures) {
                    try {
                        SyncResult result = future.join();
                        if (result.isSuccess()) {
                            successCount++;
                        } else {
                            errorCount++;
                            errors.add(result.getMessage());
                        }
                    } catch (Exception e) {
                        errorCount++;
                        log.error("Future join failed for {} entity: {}", entityType.toLowerCase(), e.getMessage());
                        errors.add("Future execution failed: " + e.getMessage());
                    }
                }
            } else {
                for (T entity : batch) {
                    try {
                        syncFunction.sync(entity);
                        successCount++;
                    } catch (Exception e) {
                        errorCount++;
                        log.error("Failed to sync {} entity: {}", entityType.toLowerCase(), e.getMessage());
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

    // ===== ENTITY UPDATE METHODS =====

    private boolean updateOrCreatePropertyFromPayProp(Map<String, Object> propertyData, Long initiatedBy) {
        String payPropId = (String) propertyData.get("id");
        Property existingProperty = findPropertyByPayPropId(payPropId);
        
        if (existingProperty != null) {
            updatePropertyFromPayPropData(existingProperty, propertyData);
            existingProperty.setUpdatedBy(initiatedBy);
            
            try {
                propertyService.save(existingProperty);
                return false; // Not new
            } catch (DataIntegrityViolationException e) {
                log.warn("Property with PayProp ID {} already exists during update, skipping", payPropId);
                return false;
            }
        } else {
            Property property = createPropertyFromPayPropData(propertyData);
            property.setCreatedBy(initiatedBy);
            
            try {
                propertyService.save(property);
                return true; // New
            } catch (DataIntegrityViolationException e) {
                log.warn("Property with PayProp ID {} already exists during creation, attempting to find and update existing", payPropId);
                Property existing = findPropertyByPayPropId(payPropId);
                if (existing != null) {
                    updatePropertyFromPayPropData(existing, propertyData);
                    existing.setUpdatedBy(initiatedBy);
                    try {
                        propertyService.save(existing);
                        return false; // Updated existing
                    } catch (DataIntegrityViolationException e2) {
                        log.error("Failed to update existing property with PayProp ID {} after duplicate key error", payPropId, e2);
                        throw new RuntimeException("Unable to save property with PayProp ID: " + payPropId, e2);
                    }
                } else {
                    log.error("Duplicate key error but could not find existing property with PayProp ID {}", payPropId);
                    throw new RuntimeException("Duplicate key error for unknown property with PayProp ID: " + payPropId, e);
                }
            }
        }
    }

    private boolean updateOrCreateTenantFromPayProp(Map<String, Object> tenantData, Long initiatedBy) {
        String payPropId = (String) tenantData.get("id");
        Tenant existingTenant = findTenantByPayPropId(payPropId);
        
        if (existingTenant != null) {
            updateTenantFromPayPropData(existingTenant, tenantData);
            existingTenant.setUpdatedAt(LocalDateTime.now());
            
            try {
                tenantService.save(existingTenant);
                return false; // Not new
            } catch (DataIntegrityViolationException e) {
                log.warn("Tenant with PayProp ID {} already exists during update, skipping", payPropId);
                return false;
            }
        } else {
            Tenant tenant = createTenantFromPayPropData(tenantData);
            tenant.setCreatedAt(LocalDateTime.now());
            tenant.setCreatedBy(initiatedBy);
            
            try {
                tenantService.save(tenant);
                return true; // New
            } catch (DataIntegrityViolationException e) {
                log.warn("Tenant with PayProp ID {} already exists during creation, attempting to find and update existing", payPropId);
                Tenant existing = findTenantByPayPropId(payPropId);
                if (existing != null) {
                    updateTenantFromPayPropData(existing, tenantData);
                    existing.setUpdatedAt(LocalDateTime.now());
                    try {
                        tenantService.save(existing);
                        return false; // Updated existing
                    } catch (DataIntegrityViolationException e2) {
                        log.error("Failed to update existing tenant with PayProp ID {} after duplicate key error", payPropId, e2);
                        throw new RuntimeException("Unable to save tenant with PayProp ID: " + payPropId, e2);
                    }
                } else {
                    log.error("Duplicate key error but could not find existing tenant with PayProp ID {}", payPropId);
                    throw new RuntimeException("Duplicate key error for unknown tenant with PayProp ID: " + payPropId, e);
                }
            }
        }
    }

    private boolean updateOrCreateBeneficiaryFromPayProp(Map<String, Object> beneficiaryData, Long initiatedBy) {
        String payPropId = (String) beneficiaryData.get("id");
        PropertyOwner existingOwner = findPropertyOwnerByPayPropId(payPropId);
        
        if (existingOwner != null) {
            updatePropertyOwnerFromPayPropData(existingOwner, beneficiaryData);
            existingOwner.setUpdatedAt(LocalDateTime.now());
            
            try {
                propertyOwnerService.save(existingOwner);
                return false; // Not new
            } catch (DataIntegrityViolationException e) {
                log.warn("PropertyOwner with PayProp ID {} already exists during update, skipping", payPropId);
                return false;
            }
        } else {
            PropertyOwner owner = createPropertyOwnerFromPayPropData(beneficiaryData);
            owner.setCreatedAt(LocalDateTime.now());
            owner.setCreatedBy(initiatedBy);
            
            try {
                propertyOwnerService.save(owner);
                return true; // New
            } catch (DataIntegrityViolationException e) {
                log.warn("PropertyOwner with PayProp ID {} already exists during creation, attempting to find and update existing", payPropId);
                PropertyOwner existing = findPropertyOwnerByPayPropId(payPropId);
                if (existing != null) {
                    updatePropertyOwnerFromPayPropData(existing, beneficiaryData);
                    existing.setUpdatedAt(LocalDateTime.now());
                    try {
                        propertyOwnerService.save(existing);
                        return false; // Updated existing
                    } catch (DataIntegrityViolationException e2) {
                        log.error("Failed to update existing PropertyOwner with PayProp ID {} after duplicate key error", payPropId, e2);
                        throw new RuntimeException("Unable to save PropertyOwner with PayProp ID: " + payPropId, e2);
                    }
                } else {
                    log.error("Duplicate key error but could not find existing PropertyOwner with PayProp ID {}", payPropId);
                    throw new RuntimeException("Duplicate key error for unknown PropertyOwner with PayProp ID: " + payPropId, e);
                }
            }
        }
    }

    // ===== UTILITY METHODS =====

    private void updatePropertyFromPayPropData(Property property, Map<String, Object> data) {
        String propertyName = (String) data.get("property_name");
        if (propertyName == null) {
            propertyName = (String) data.get("name");
        }
        property.setPropertyName(propertyName);
        property.setCustomerReference((String) data.get("customer_reference"));
        property.setAgentName((String) data.get("agent_name"));
        property.setComment((String) data.get("notes"));
        
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
            
            String state = (String) address.get("state");
            if (state == null || state.trim().isEmpty()) {
                state = "N/A"; // Default value to avoid PayProp validation error
            }
            property.setState(state);
        }
        
        Map<String, Object> settings = (Map<String, Object>) data.get("settings");
        if (settings != null) {
            Boolean enablePayments = (Boolean) settings.get("enable_payments");
            if (enablePayments != null) {
                property.setEnablePayments(enablePayments ? "Y" : "N");
            }
            
            Boolean holdOwnerFunds = (Boolean) settings.get("hold_owner_funds");
            if (holdOwnerFunds != null) {
                property.setHoldOwnerFunds(holdOwnerFunds ? "Y" : "N");
            }
            
            Boolean verifyPayments = (Boolean) settings.get("verify_payments");
            if (verifyPayments != null) {
                property.setVerifyPayments(verifyPayments ? "Y" : "N");
            }
            
            Object monthlyPaymentObj = settings.get("monthly_payment");
            if (monthlyPaymentObj instanceof Number) {
                property.setMonthlyPayment(BigDecimal.valueOf(((Number) monthlyPaymentObj).doubleValue()));
            }
            
            Object minBalanceObj = settings.get("minimum_balance");
            if (minBalanceObj instanceof Number) {
                property.setPropertyAccountMinimumBalance(BigDecimal.valueOf(((Number) minBalanceObj).doubleValue()));
            }
            
            Object listingFromObj = settings.get("listing_from");
            if (listingFromObj instanceof LocalDate) {
                property.setListedFrom((LocalDate) listingFromObj);
            } else if (listingFromObj instanceof String) {
                try {
                    property.setListedFrom(LocalDate.parse((String) listingFromObj));
                } catch (Exception e) {
                    log.warn("Could not parse listing_from date: {}", listingFromObj);
                    property.setListedFrom(null);
                }
            } else if (listingFromObj instanceof List) {
                log.warn("listing_from received as array, skipping: {}", listingFromObj);
                property.setListedFrom(null);
            }
            
            Object listingToObj = settings.get("listing_to");
            if (listingToObj instanceof LocalDate) {
                property.setListedUntil((LocalDate) listingToObj);
            } else if (listingToObj instanceof String) {
                try {
                    property.setListedUntil(LocalDate.parse((String) listingToObj));
                } catch (Exception e) {
                    log.warn("Could not parse listing_to date: {}", listingToObj);
                    property.setListedUntil(null);
                }
            } else if (listingToObj instanceof List) {
                log.warn("listing_to received as array, skipping: {}", listingToObj);
                property.setListedUntil(null);
            }
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
        
        // FIXED: Mobile number with tenant-specific validation (15 char limit)
        String rawMobile = (String) data.get("mobile_number");
        if (rawMobile != null && !rawMobile.trim().isEmpty()) {
            String formattedMobile = formatMobileForPayProp(rawMobile, "tenant");
            if (formattedMobile != null) {
                tenant.setMobileNumber(formattedMobile);
                log.debug("Tenant mobile formatted: '{}' -> '{}'", rawMobile, formattedMobile);
            } else {
                log.warn("Invalid mobile number for tenant {}: '{}' - setting to null", 
                    data.get("id"), rawMobile);
                tenant.setMobileNumber(null);
            }
        } else {
            tenant.setMobileNumber(null);
        }
        
        // FIXED: Phone number with 15-char limit validation
        String rawPhone = (String) data.get("phone");
        tenant.setPhoneNumber(formatPhoneForPayProp(rawPhone));
        
        // FIXED: Fax number handling (if your Tenant entity supports it)
        String rawFax = (String) data.get("fax");
        if (rawFax != null) {
            try {
                java.lang.reflect.Method setFaxMethod = tenant.getClass().getMethod("setFax", String.class);
                setFaxMethod.invoke(tenant, formatPhoneForPayProp(rawFax));
            } catch (NoSuchMethodException e) {
                // Fax field doesn't exist, add to comment
                String faxInfo = "Fax: " + formatPhoneForPayProp(rawFax);
                String currentComment = tenant.getComment();
                if (currentComment != null && !currentComment.isEmpty()) {
                    tenant.setComment(currentComment + "; " + faxInfo);
                } else {
                    tenant.setComment(faxInfo);
                }
            } catch (Exception e) {
                log.warn("Could not set fax for tenant: {}", e.getMessage());
            }
        }
        
        tenant.setComment((String) data.get("comment"));
        
        String firstName = (String) data.get("first_name");
        String lastName = (String) data.get("last_name");
        String businessName = (String) data.get("business_name");
        
        tenant.setFirstName(firstName);
        tenant.setLastName(lastName);
        tenant.setBusinessName(businessName);
        
        if (firstName != null && !firstName.trim().isEmpty() && 
            lastName != null && !lastName.trim().isEmpty()) {
            tenant.setAccountType(AccountType.individual);
        } else if (businessName != null && !businessName.trim().isEmpty()) {
            tenant.setAccountType(AccountType.business);
        } else {
            tenant.setAccountType(AccountType.individual);
        }
        
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
        
        // FIXED: Mobile number with beneficiary-specific validation (25 char limit)
        String rawMobile = (String) data.get("mobile_number");
        if (rawMobile != null && !rawMobile.trim().isEmpty()) {
            String formattedMobile = formatMobileForPayProp(rawMobile, "beneficiary");
            if (formattedMobile != null) {
                owner.setMobile(formattedMobile);
                log.debug("PropertyOwner mobile formatted: '{}' -> '{}'", rawMobile, formattedMobile);
            } else {
                log.warn("Invalid mobile number for property owner {}: '{}' - setting to null", 
                    data.get("id"), rawMobile);
                owner.setMobile(null);
            }
        } else {
            owner.setMobile(null);
        }
        
        // FIXED: Phone number with 15-char limit validation  
        String rawPhone = (String) data.get("phone");
        if (rawPhone != null) {
            try {
                java.lang.reflect.Method setPhoneMethod = owner.getClass().getMethod("setPhone", String.class);
                setPhoneMethod.invoke(owner, formatPhoneForPayProp(rawPhone));
            } catch (NoSuchMethodException e) {
                // Phone field doesn't exist, add to comment
                String phoneInfo = "Phone: " + formatPhoneForPayProp(rawPhone);
                String currentComment = owner.getComment();
                if (currentComment != null && !currentComment.isEmpty()) {
                    owner.setComment(currentComment + "; " + phoneInfo);
                } else {
                    owner.setComment(phoneInfo);
                }
            } catch (Exception e) {
                log.warn("Could not set phone for property owner: {}", e.getMessage());
            }
        }
        
        // FIXED: Fax number handling
        String rawFax = (String) data.get("fax");
        if (rawFax != null) {
            try {
                java.lang.reflect.Method setFaxMethod = owner.getClass().getMethod("setFax", String.class);
                setFaxMethod.invoke(owner, formatPhoneForPayProp(rawFax));
            } catch (NoSuchMethodException e) {
                // Fax field doesn't exist, add to comment
                String faxInfo = "Fax: " + formatPhoneForPayProp(rawFax);
                String currentComment = owner.getComment();
                if (currentComment != null && !currentComment.isEmpty()) {
                    owner.setComment(currentComment + "; " + faxInfo);
                } else {
                    owner.setComment(faxInfo);
                }
            } catch (Exception e) {
                log.warn("Could not set fax for property owner: {}", e.getMessage());
            }
        }
        
        owner.setComment((String) data.get("comment"));

        String paymentMethodStr = (String) data.get("payment_method");
        if (paymentMethodStr != null && !paymentMethodStr.trim().isEmpty()) {
            try {
                owner.setPaymentMethod(PaymentMethod.valueOf(paymentMethodStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                owner.setPaymentMethod(PaymentMethod.local);
            }
        } else {
            owner.setPaymentMethod(PaymentMethod.local);
        }
        
        String firstName = (String) data.get("first_name");
        String lastName = (String) data.get("last_name");
        String businessName = (String) data.get("business_name");
        
        owner.setFirstName(firstName);
        owner.setLastName(lastName);
        owner.setBusinessName(businessName);
        
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
        
        owner.setVatNumber((String) data.get("vat_number"));
        owner.setIdNumber((String) data.get("id_reg_number"));
        
        Boolean notifyEmail = (Boolean) data.get("notify_email");
        if (notifyEmail != null) {
            owner.setEmailEnabled(notifyEmail);
        }
        
        Boolean notifySms = (Boolean) data.get("notify_sms");
        if (notifySms != null) {
            try {
                java.lang.reflect.Method setNotifySmsMethod = owner.getClass().getMethod("setNotifySms", String.class);
                setNotifySmsMethod.invoke(owner, notifySms ? "Y" : "N");
            } catch (NoSuchMethodException e) {
                String smsInfo = "SMS notifications: " + (notifySms ? "Y" : "N");
                String currentComment = owner.getComment();
                if (currentComment != null && !currentComment.isEmpty()) {
                    owner.setComment(currentComment + "; " + smsInfo);
                } else {
                    owner.setComment(smsInfo);
                }
            } catch (Exception e) {
                log.warn("Could not set notify_sms: {}", e.getMessage());
            }
        }
    }

    private PropertyOwner createPropertyOwnerFromPayPropData(Map<String, Object> data) {
        PropertyOwner owner = new PropertyOwner();
        updatePropertyOwnerFromPayPropData(owner, data);
        return owner;
    }

    // ===== PHONE NUMBER FORMATTING METHODS (API COMPLIANT) =====

    /**
     * Format mobile number for PayProp API with entity-specific validation
     * @param mobile Raw mobile number
     * @param entityType "tenant" or "beneficiary" 
     * @return Formatted mobile number or null if invalid
     */
    private String formatMobileForPayProp(String mobile, String entityType) {
        if (mobile == null || mobile.trim().isEmpty()) {
            return null; // Allow null for optional field
        }
        
        log.debug("Formatting mobile number for {}: '{}'", entityType, mobile);
        
        // Clean: remove everything except digits and + sign
        String cleaned = mobile.replaceAll("[^\\d+]", "");
        
        // Skip if empty after cleaning
        if (cleaned.isEmpty()) {
            log.warn("Mobile number is empty after cleaning: {}", mobile);
            return null;
        }
        
        // Handle different input formats
        String formatted = cleaned;
        
        // Case 1: Already has country code (starts with +)
        if (formatted.startsWith("+")) {
            formatted = formatted.substring(1); // Remove + sign
            log.debug("Removed + sign: '{}'", formatted);
        }
        
        // Case 2: UK mobile starting with 0
        if (formatted.startsWith("0") && formatted.length() >= 10) {
            // Convert 07xxxxxxxxx to 447xxxxxxxxx
            formatted = "44" + formatted.substring(1);
            log.debug("Converted UK domestic format: '{}' -> '{}'", mobile, formatted);
        }
        
        // Case 3: UK mobile without leading 0 or country code
        else if (formatted.startsWith("7") && formatted.length() >= 9 && formatted.length() <= 10) {
            // Convert 7xxxxxxxxx to 447xxxxxxxxx
            formatted = "44" + formatted;
            log.debug("Added UK country code: '{}' -> '{}'", mobile, formatted);
        }
        
        // Case 4: Already has 44 prefix - validate
        else if (formatted.startsWith("44") && formatted.length() >= 12) {
            log.debug("Already in UK international format: '{}'", formatted);
        }
        
        // Case 5: Other international numbers - validate length
        else if (!formatted.startsWith("44")) {
            log.debug("International number: '{}'", formatted);
        }
        
        // FIXED: Entity-specific length validation per API spec
        int maxLength;
        if ("tenant".equals(entityType)) {
            maxLength = 15; // Tenant mobile_number max: 15
        } else if ("beneficiary".equals(entityType)) {
            maxLength = 25; // Beneficiary mobile max: 25
        } else {
            maxLength = 15; // Default fallback
        }
        
        // Length validation with entity-specific limits
        if (formatted.length() < 1 || formatted.length() > maxLength) {
            log.warn("{} mobile number length invalid: {} (length: {}, max: {})", 
                entityType, formatted, formatted.length(), maxLength);
            return null;
        }
        
        // Pattern validation: ^[1-9]\d*$ (must start with 1-9, followed by digits)
        if (!formatted.matches("^[1-9]\\d*$")) {
            log.warn("{} mobile number doesn't match PayProp pattern ^[1-9]\\d*$: {}", 
                entityType, formatted);
            return null;
        }
        
        log.debug("{} mobile number successfully formatted: '{}' -> '{}'", 
            entityType, mobile, formatted);
        return formatted;
    }

    /**
     * Format phone number for record-keeping fields (phone, fax)
     * Max 15 characters, no pattern restrictions
     */
    private String formatPhoneForPayProp(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = phone.trim();
        
        // API limit: 15 characters for phone/fax fields
        if (trimmed.length() > 15) {
            log.warn("Phone number too long ({}), truncating to 15 chars: {}", 
                trimmed.length(), trimmed);
            return trimmed.substring(0, 15);
        }
        
        return trimmed;
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

    // ===== PHONE NUMBER TESTING AND ANALYSIS METHODS =====

    /**
     * Test mobile number formatting with API-compliant test cases
     */
    public void testMobileNumberFormattingWithApiSpec() {
        log.info("=== TESTING MOBILE NUMBER FORMATTING (API SPEC COMPLIANCE) ===");
        
        String[] testNumbers = {
            "07712345678",        // UK format with 0 -> 447712345678 (12 chars) ✓
            "447712345678",       // UK format with country code (12 chars) ✓
            "+447712345678",      // UK format with + (12 chars) ✓
            "4477123456789012345", // 20 chars - OK for beneficiary, too long for tenant
            "44771234567890123456789012345", // 29 chars - too long for both
            "07",                 // Too short ✗
            "447",                // Too short ✗ 
            "0771234567",         // 11 chars -> 4477123456 (10 chars) ✓
            "+33123456789",       // French number (12 chars) ✓
        };
        
        for (String testNumber : testNumbers) {
            String tenantResult = formatMobileForPayProp(testNumber, "tenant");
            String beneficiaryResult = formatMobileForPayProp(testNumber, "beneficiary");
            
            log.info("Input: '{}' -> Tenant: {} | Beneficiary: {}", 
                testNumber != null ? testNumber : "null", 
                tenantResult != null ? tenantResult : "INVALID",
                beneficiaryResult != null ? beneficiaryResult : "INVALID");
        }
    }

    /**
     * Pre-sync cleanup method to fix mobile numbers before validation
     */
    public void cleanupTenantMobileNumbers() {
        log.info("Starting tenant mobile number cleanup for PayProp sync...");
        
        List<Tenant> tenants = tenantService.findAll();
        int updatedCount = 0;
        int errorCount = 0;
        
        for (Tenant tenant : tenants) {
            try {
                if (tenant.getMobileNumber() != null && !tenant.getMobileNumber().trim().isEmpty()) {
                    String originalMobile = tenant.getMobileNumber();
                    String formattedMobile = formatMobileForPayProp(originalMobile, "tenant");
                    
                    if (formattedMobile == null) {
                        log.warn("Tenant {} has invalid mobile number that cannot be formatted: {}", 
                            tenant.getId(), originalMobile);
                        
                        tenant.setMobileNumber(null);
                        
                        String note = "Invalid mobile cleared: " + originalMobile;
                        try {
                            java.lang.reflect.Method getNotesMethod = tenant.getClass().getMethod("getNotes");
                            java.lang.reflect.Method setNotesMethod = tenant.getClass().getMethod("setNotes", String.class);
                            String currentNotes = (String) getNotesMethod.invoke(tenant);
                            
                            if (currentNotes != null && !currentNotes.isEmpty()) {
                                setNotesMethod.invoke(tenant, currentNotes + "; " + note);
                            } else {
                                setNotesMethod.invoke(tenant, note);
                            }
                        } catch (NoSuchMethodException e) {
                            // Notes field doesn't exist, add to comment instead
                            String currentComment = tenant.getComment();
                            if (currentComment != null && !currentComment.isEmpty()) {
                                tenant.setComment(currentComment + "; " + note);
                            } else {
                                tenant.setComment(note);
                            }
                        } catch (Exception e) {
                            log.warn("Could not set notes for tenant: {}", e.getMessage());
                        }
                        
                        tenantService.save(tenant);
                        errorCount++;
                        
                    } else if (!formattedMobile.equals(originalMobile)) {
                        tenant.setMobileNumber(formattedMobile);
                        tenantService.save(tenant);
                        updatedCount++;
                        log.info("Updated tenant {} mobile: {} -> {}", 
                            tenant.getId(), originalMobile, formattedMobile);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing tenant {} mobile number: {}", tenant.getId(), e.getMessage());
                errorCount++;
            }
        }
        
        log.info("Mobile number cleanup completed. Updated: {}, Errors: {}", updatedCount, errorCount);
    }

    /**
     * Analyze current tenant mobile numbers for debugging
     */
    public void analyzeTenantMobileNumbers() {
        log.info("=== ANALYZING TENANT MOBILE NUMBERS ===");
        
        List<Tenant> tenants = tenantService.findAll();
        
        Map<String, Integer> formatCounts = new HashMap<>();
        List<String> problematicNumbers = new ArrayList<>();
        List<String> validNumbers = new ArrayList<>();
        
        for (Tenant tenant : tenants) {
            String mobile = tenant.getMobileNumber();
            if (mobile != null && !mobile.trim().isEmpty()) {
                String category = categorizeMobileNumber(mobile);
                formatCounts.merge(category, 1, Integer::sum);
                
                String formatted = formatMobileForPayProp(mobile, "tenant");
                if (formatted == null) {
                    problematicNumbers.add(String.format("Tenant ID %d: '%s' -> INVALID", 
                        tenant.getId(), mobile));
                } else {
                    validNumbers.add(String.format("Tenant ID %d: '%s' -> '%s'", 
                        tenant.getId(), mobile, formatted));
                }
            }
        }
        
        log.info("Mobile Number Format Analysis:");
        formatCounts.forEach((format, count) -> 
            log.info("  {}: {} numbers", format, count));
        
        log.info("\nValid Mobile Numbers (showing first 10):");
        validNumbers.stream().limit(10).forEach(log::info);
        
        log.info("\nProblematic Mobile Numbers:");
        problematicNumbers.forEach(log::warn);
        
        log.info("\nSummary: {} valid, {} problematic out of {} total", 
            validNumbers.size(), problematicNumbers.size(), validNumbers.size() + problematicNumbers.size());
    }

    /**
     * Categorize mobile number formats for analysis
     */
    private String categorizeMobileNumber(String mobile) {
        if (mobile == null || mobile.trim().isEmpty()) {
            return "EMPTY";
        }
        
        String cleaned = mobile.replaceAll("[^\\d+]", "");
        
        if (cleaned.startsWith("+44")) {
            return "UK_PLUS_FORMAT";
        } else if (cleaned.startsWith("44")) {
            return "UK_NO_PLUS";
        } else if (cleaned.startsWith("07")) {
            return "UK_DOMESTIC";
        } else if (cleaned.startsWith("7") && cleaned.length() >= 9) {
            return "UK_NO_ZERO";
        } else if (cleaned.startsWith("+")) {
            return "INTERNATIONAL_PLUS";
        } else if (cleaned.startsWith("0") && !cleaned.startsWith("07")) {
            return "UK_LANDLINE";
        } else if (cleaned.length() < 7) {
            return "TOO_SHORT";
        } else if (cleaned.length() > 15) {
            return "TOO_LONG";
        } else {
            return "OTHER_FORMAT";
        }
    }

    /**
     * Validation method for mobile numbers before PayProp sync
     */
    public boolean isValidMobileNumber(String mobile, String entityType) {
        return formatMobileForPayProp(mobile, entityType) != null;
    }
}
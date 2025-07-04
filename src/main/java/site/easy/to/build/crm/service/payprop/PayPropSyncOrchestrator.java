// PayPropSyncOrchestrator.java - Central Two-Way Sync Coordinator with Debug Mode
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
    private final ExecutorService executorService;

    @Value("${payprop.sync.batch-size:10}")
    private int batchSize;

    @Value("${payprop.sync.parallel-enabled:false}")
    private boolean parallelSyncEnabled;

    // NEW: Debug mode settings
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
                                  PropertyOwnerService propertyOwnerService) {
        this.payPropSyncService = payPropSyncService;
        this.portfolioSyncService = portfolioSyncService;
        this.conflictResolver = conflictResolver;
        this.payPropChangeDetection = payPropChangeDetection;
        this.syncLogger = syncLogger;
        this.customerService = customerService;
        this.propertyService = propertyService;
        this.tenantService = tenantService;
        this.propertyOwnerService = propertyOwnerService;
        this.executorService = Executors.newFixedThreadPool(5);
    }

    // ===== FULL SYNC ORCHESTRATION =====

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
        
        // Enable debug mode in logger
        if (enableDebugMode) {
            syncLogger.setDebugMode(true);
        }
        
        syncLogger.logSyncStart("FULL_SYNC", initiatedBy);
        
        // Temporarily set debug mode for this operation
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
            // Restore original debug mode
            this.debugMode = originalDebugMode;
            
            // Clear debug mode from logger
            if (enableDebugMode) {
                syncLogger.clearDebugMode();
            }
        }
        
        return result;
    }

    /**
     * Intelligent sync based on change detection
     */
    @Transactional
    public ComprehensiveSyncResult performIntelligentSync(Long initiatedBy) {
        ComprehensiveSyncResult result = new ComprehensiveSyncResult();
        
        // Detect changes since last sync
        PayPropChangeDetection.SyncChangeDetection changes = payPropChangeDetection.detectChanges();
        
        if (changes.hasNoCrmChanges() && changes.hasNoPayPropChanges()) {
            result.setMessage("No changes detected - sync skipped");
            return result;
        }
        
        // Sync only what's changed
        if (changes.hasCrmChanges()) {
            result.setCrmToPayPropResult(syncSpecificEntities(changes.getCrmChanges(), initiatedBy));
        }
        
        if (changes.hasPayPropChanges()) {
            result.setPayPropToCrmResult(pullSpecificChanges(changes.getPayPropChanges(), initiatedBy));
        }
        
        return result;
    }

    // ===== CRM TO PAYPROP SYNC =====

    private SyncResult syncCrmToPayProp(Long initiatedBy) {
        Map<String, Object> results = new HashMap<>();
        int totalSuccess = 0;
        int totalErrors = 0;
        List<String> errors = new ArrayList<>();

        try {
            // Sync Properties
            SyncResult propertyResult = syncPropertiesToPayProp(initiatedBy);
            results.put("properties", propertyResult);
            if (propertyResult.isSuccess()) totalSuccess++; else totalErrors++;

            // Sync Tenants (prioritize Customer entities)
            SyncResult tenantResult = syncTenantsToPayProp(initiatedBy);
            results.put("tenants", tenantResult);
            if (tenantResult.isSuccess()) totalSuccess++; else totalErrors++;

            // Sync Beneficiaries (Property Owners)
            SyncResult beneficiaryResult = syncBeneficiariesToPayProp(initiatedBy);
            results.put("beneficiaries", beneficiaryResult);
            if (beneficiaryResult.isSuccess()) totalSuccess++; else totalErrors++;

            String message = String.format("CRM to PayProp sync completed. Success: %d, Errors: %d", 
                                          totalSuccess, totalErrors);
            
            return totalErrors == 0 ? 
                SyncResult.success(message, results) : 
                SyncResult.partial(message, results);
                
        } catch (Exception e) {
            errors.add("CRM to PayProp sync failed: " + e.getMessage());
            results.put("errors", errors);
            return SyncResult.failure("CRM to PayProp sync failed", results);
        }
    }

    private SyncResult syncPropertiesToPayProp(Long initiatedBy) {
        List<Property> properties = propertyService.findPropertiesReadyForSync();
        return processBatchSync(properties, "PROPERTY", initiatedBy, 
            property -> payPropSyncService.syncPropertyToPayProp(property.getId()));
    }

    private SyncResult syncTenantsToPayProp(Long initiatedBy) {
        // Priority 1: Customer entities marked as tenants
        List<Customer> tenantCustomers = customerService.findByCustomerType(CustomerType.TENANT);
        List<SyncResult> customerResults = new ArrayList<>();
        
        for (Customer customer : tenantCustomers) {
            if (customer.isReadyForPayPropSync()) {
                try {
                    String payPropId = syncCustomerAsTenant(customer);
                    customerResults.add(SyncResult.success("Customer " + customer.getCustomerId() + " synced"));
                } catch (Exception e) {
                    customerResults.add(SyncResult.failure("Customer " + customer.getCustomerId() + " failed: " + e.getMessage()));
                }
            }
        }

        // Priority 2: Legacy Tenant entities (if still needed)
        List<Tenant> tenants = tenantService.findTenantsReadyForPayPropSync();
        List<SyncResult> tenantResults = new ArrayList<>();
        
        for (Tenant tenant : tenants) {
            try {
                String payPropId = payPropSyncService.syncTenantToPayProp(tenant.getId());
                tenantResults.add(SyncResult.success("Tenant " + tenant.getId() + " synced"));
            } catch (Exception e) {
                tenantResults.add(SyncResult.failure("Tenant " + tenant.getId() + " failed: " + e.getMessage()));
            }
        }

        int totalSuccess = (int) customerResults.stream().filter(SyncResult::isSuccess).count() +
                          (int) tenantResults.stream().filter(SyncResult::isSuccess).count();
        int totalErrors = customerResults.size() + tenantResults.size() - totalSuccess;

        Map<String, Object> details = Map.of(
            "customerTenants", customerResults.size(),
            "legacyTenants", tenantResults.size(),
            "totalSuccess", totalSuccess,
            "totalErrors", totalErrors
        );

        return totalErrors == 0 ? 
            SyncResult.success("All tenants synced successfully", details) :
            SyncResult.partial("Tenant sync completed with errors", details);
    }

    private SyncResult syncBeneficiariesToPayProp(Long initiatedBy) {
        // Priority 1: Customer entities marked as property owners
        List<Customer> ownerCustomers = customerService.findByCustomerType(CustomerType.PROPERTY_OWNER);
        List<SyncResult> customerResults = new ArrayList<>();
        
        for (Customer customer : ownerCustomers) {
            if (customer.isReadyForPayPropSync()) {
                try {
                    String payPropId = syncCustomerAsBeneficiary(customer);
                    customerResults.add(SyncResult.success("Customer " + customer.getCustomerId() + " synced"));
                } catch (Exception e) {
                    customerResults.add(SyncResult.failure("Customer " + customer.getCustomerId() + " failed: " + e.getMessage()));
                }
            }
        }

        // Priority 2: Legacy PropertyOwner entities (if still needed)
        List<PropertyOwner> owners = propertyOwnerService.findPropertyOwnersReadyForSync();
        List<SyncResult> ownerResults = new ArrayList<>();
        
        for (PropertyOwner owner : owners) {
            try {
                String payPropId = payPropSyncService.syncBeneficiaryToPayProp(owner.getId());
                ownerResults.add(SyncResult.success("PropertyOwner " + owner.getId() + " synced"));
            } catch (Exception e) {
                ownerResults.add(SyncResult.failure("PropertyOwner " + owner.getId() + " failed: " + e.getMessage()));
            }
        }

        int totalSuccess = (int) customerResults.stream().filter(SyncResult::isSuccess).count() +
                          (int) ownerResults.stream().filter(SyncResult::isSuccess).count();
        int totalErrors = customerResults.size() + ownerResults.size() - totalSuccess;

        Map<String, Object> details = Map.of(
            "customerOwners", customerResults.size(),
            "legacyOwners", ownerResults.size(),
            "totalSuccess", totalSuccess,
            "totalErrors", totalErrors
        );

        return totalErrors == 0 ? 
            SyncResult.success("All beneficiaries synced successfully", details) :
            SyncResult.partial("Beneficiary sync completed with errors", details);
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
                        
                        // Collect sample data for debug mode
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
            
            // Add sample data in debug mode
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
                        
                        // Collect sample data for debug mode
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
            
            // Add sample data in debug mode
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
                        
                        // Collect sample data for debug mode
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
            
            // Add sample data in debug mode
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

    // ===== SAMPLE DATA CREATION FOR DEBUG MODE =====

    private Map<String, Object> createSamplePropertyData(Map<String, Object> fullData, boolean isNew) {
        Map<String, Object> sample = new HashMap<>();
        sample.put("id", fullData.get("id"));
        sample.put("name", fullData.get("name"));
        sample.put("customer_reference", fullData.get("customer_reference"));
        sample.put("action", isNew ? "created" : "updated");
        
        // Include address if present but simplified
        Map<String, Object> address = (Map<String, Object>) fullData.get("address");
        if (address != null) {
            Map<String, Object> sampleAddress = new HashMap<>();
            sampleAddress.put("address_line_1", address.get("address_line_1"));
            sampleAddress.put("city", address.get("city"));
            sampleAddress.put("postal_code", address.get("postal_code"));
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
            // Sync local portfolios to PayProp tags
            SyncResult portfolioToTagResult = portfolioSyncService.syncAllPortfolios(initiatedBy);
            
            // Pull PayProp tags to local portfolios
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

        // Process in batches
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

    // ===== CUSTOMER SYNC METHODS =====

    private String syncCustomerAsTenant(Customer customer) throws Exception {
        // Convert Customer to PayProp Tenant format
        PayPropTenantDTO dto = new PayPropTenantDTO();
        
        // Map customer fields to PayProp tenant fields
        dto.setAccount_type(customer.getAccountType().getValue());
        dto.setEmail_address(customer.getEmail());
        dto.setCustomer_id(customer.getPayPropCustomerId());
        dto.setCustomer_reference(customer.getCustomerReference());
        dto.setComment(customer.getNotes());
        
        if (customer.getAccountType() == AccountType.individual) {
            dto.setFirst_name(customer.getFirstName());
            dto.setLast_name(customer.getLastName());
        } else {
            dto.setBusiness_name(customer.getBusinessName());
        }
        
        // Map address
        if (customer.getAddressLine1() != null) {
            PayPropAddressDTO address = new PayPropAddressDTO();
            address.setAddress_line_1(customer.getAddressLine1());
            address.setAddress_line_2(customer.getAddressLine2());
            address.setAddress_line_3(customer.getAddressLine3());
            address.setCity(customer.getCity());
            address.setPostal_code(customer.getPostcode());
            address.setCountry_code(customer.getCountryCode());
            dto.setAddress(address);
        }
        
        // Use PayProp sync service to create tenant
        return payPropSyncService.createTenantFromCustomer(dto);
    }

    private String syncCustomerAsBeneficiary(Customer customer) throws Exception {
        // Convert Customer to PayProp Beneficiary format
        PayPropBeneficiaryDTO dto = new PayPropBeneficiaryDTO();
        
        // Map customer fields to PayProp beneficiary fields
        dto.setAccount_type(customer.getAccountType().getValue());
        dto.setPayment_method(customer.getPaymentMethod().getPayPropCode());
        dto.setEmail_address(customer.getEmail());
        dto.setCustomer_id(customer.getPayPropCustomerId());
        dto.setCustomer_reference(customer.getCustomerReference());
        dto.setComment(customer.getNotes());
        
        if (customer.getAccountType() == AccountType.individual) {
            dto.setFirst_name(customer.getFirstName());
            dto.setLast_name(customer.getLastName());
        } else {
            dto.setBusiness_name(customer.getBusinessName());
        }
        
        // Map address (required for international payments)
        if (customer.getAddressLine1() != null) {
            PayPropAddressDTO address = new PayPropAddressDTO();
            address.setAddress_line_1(customer.getAddressLine1());
            address.setAddress_line_2(customer.getAddressLine2());
            address.setAddress_line_3(customer.getAddressLine3());
            address.setCity(customer.getCity());
            address.setPostal_code(customer.getPostcode());
            address.setCountry_code(customer.getCountryCode());
            dto.setAddress(address);
        }
        
        // Use PayProp sync service to create beneficiary
        return payPropSyncService.createBeneficiaryFromCustomer(dto);
    }

    // ===== ENTITY UPDATE METHODS =====

    private boolean updateOrCreatePropertyFromPayProp(Map<String, Object> propertyData, Long initiatedBy) {
        String payPropId = (String) propertyData.get("id");
        Optional<Property> existingProperty = propertyService.findByPayPropId(payPropId);
        
        if (existingProperty.isPresent()) {
            // Update existing property
            Property property = existingProperty.get();
            updatePropertyFromPayPropData(property, propertyData);
            property.setUpdatedBy(initiatedBy);
            propertyService.save(property);
            return false; // Not new
        } else {
            // Create new property
            Property property = createPropertyFromPayPropData(propertyData);
            property.setCreatedBy(initiatedBy);
            propertyService.save(property);
            return true; // New
        }
    }

    private boolean updateOrCreateTenantFromPayProp(Map<String, Object> tenantData, Long initiatedBy) {
        String payPropId = (String) tenantData.get("id");
        String email = (String) tenantData.get("email_address");
        
        // Look for existing Customer first (preferred approach)
        Customer existingCustomer = customerService.findByEmail(email);
        
        if (existingCustomer != null && existingCustomer.getCustomerType() == CustomerType.TENANT) {
            // Update existing customer
            updateCustomerFromPayPropTenantData(existingCustomer, tenantData);
            existingCustomer.setPayPropUpdatedAt(LocalDateTime.now());
            customerService.save(existingCustomer);
            return false; // Not new
        } else {
            // Create new customer from PayProp tenant
            Customer customer = createCustomerFromPayPropTenantData(tenantData);
            customer.setCreatedAt(LocalDateTime.now());
            customerService.save(customer);
            return true; // New
        }
    }

    private boolean updateOrCreateBeneficiaryFromPayProp(Map<String, Object> beneficiaryData, Long initiatedBy) {
        String payPropId = (String) beneficiaryData.get("id");
        String email = (String) beneficiaryData.get("email_address");
        
        // Look for existing Customer first (preferred approach)
        Customer existingCustomer = customerService.findByEmail(email);
        
        if (existingCustomer != null && existingCustomer.getCustomerType() == CustomerType.PROPERTY_OWNER) {
            // Update existing customer
            updateCustomerFromPayPropBeneficiaryData(existingCustomer, beneficiaryData);
            existingCustomer.setPayPropUpdatedAt(LocalDateTime.now());
            customerService.save(existingCustomer);
            return false; // Not new
        } else {
            // Create new customer from PayProp beneficiary
            Customer customer = createCustomerFromPayPropBeneficiaryData(beneficiaryData);
            customer.setCreatedAt(LocalDateTime.now());
            customerService.save(customer);
            return true; // New
        }
    }

    // ===== UTILITY METHODS =====

    private void updatePropertyFromPayPropData(Property property, Map<String, Object> data) {
        // Update property fields from PayProp data
        property.setPropertyName((String) data.get("name"));
        property.setCustomerReference((String) data.get("customer_reference"));
        property.setAgentName((String) data.get("agent_name"));
        property.setComment((String) data.get("notes"));
        
        // Update address if present
        Map<String, Object> address = (Map<String, Object>) data.get("address");
        if (address != null) {
            property.setAddressLine1((String) address.get("address_line_1"));
            property.setAddressLine2((String) address.get("address_line_2"));
            property.setAddressLine3((String) address.get("address_line_3"));
            property.setCity((String) address.get("city"));
            property.setPostcode((String) address.get("postal_code"));
            property.setCountryCode((String) address.get("country_code"));
        }
        
        // Update settings if present
        Map<String, Object> settings = (Map<String, Object>) data.get("settings");
        if (settings != null) {
            property.setEnablePaymentsFromBoolean((Boolean) settings.get("enable_payments"));
            property.setHoldOwnerFundsFromBoolean((Boolean) settings.get("hold_owner_funds"));
            // Handle monthly_payment and minimum_balance as needed
        }
    }

    private Property createPropertyFromPayPropData(Map<String, Object> data) {
        Property property = new Property();
        property.setPayPropId((String) data.get("id"));
        updatePropertyFromPayPropData(property, data);
        return property;
    }

    private void updateCustomerFromPayPropTenantData(Customer customer, Map<String, Object> data) {
        // Update customer fields from PayProp tenant data
        customer.setPayPropEntityId((String) data.get("id"));
        customer.setPayPropCustomerId((String) data.get("customer_id"));
        customer.setEmail((String) data.get("email_address"));
        customer.setMobileNumber((String) data.get("mobile_number"));
        customer.setPhone((String) data.get("phone"));
        customer.setNotes((String) data.get("comment"));
        
        // FIXED: PayProp tenants are missing account_type but have both individual and business names
        String firstName = (String) data.get("first_name");
        String lastName = (String) data.get("last_name");
        String businessName = (String) data.get("business_name");
        
        // PayProp tenants have both - prefer individual names if available
        if (firstName != null && !firstName.trim().isEmpty() && 
            lastName != null && !lastName.trim().isEmpty()) {
            // Use individual data (preferred for tenants)
            customer.setFirstName(firstName);
            customer.setLastName(lastName);
            customer.setName(firstName + " " + lastName);
            customer.setAccountType(AccountType.individual);
        } else if (businessName != null && !businessName.trim().isEmpty()) {
            // Fallback to business name
            customer.setBusinessName(businessName);
            customer.setName(businessName);
            customer.setAccountType(AccountType.business);
        } else {
            // Last resort: create placeholder
            customer.setFirstName("Unknown");
            customer.setLastName("Tenant");
            customer.setName("Unknown Tenant");
            customer.setAccountType(AccountType.individual);
        }
        
        // Update address if present
        Map<String, Object> address = (Map<String, Object>) data.get("address");
        if (address != null) {
            customer.setAddressLine1((String) address.get("address_line_1"));
            customer.setAddressLine2((String) address.get("address_line_2"));
            customer.setAddressLine3((String) address.get("address_line_3"));
            customer.setCity((String) address.get("city"));
            customer.setPostcode((String) address.get("postal_code"));
            customer.setCountryCode((String) address.get("country_code"));
        }
        
        customer.setPayPropSynced(true);
        customer.setPayPropLastSync(LocalDateTime.now());
    }

    private Customer createCustomerFromPayPropTenantData(Map<String, Object> data) {
        Customer customer = new Customer();
        customer.setCustomerType(CustomerType.TENANT);
        customer.setIsTenant(true);
        customer.setAccountType(AccountType.valueOf((String) data.get("account_type")));
        updateCustomerFromPayPropTenantData(customer, data);
        return customer;
    }

    private void updateCustomerFromPayPropBeneficiaryData(Customer customer, Map<String, Object> data) {
        // Update customer fields from PayProp beneficiary data
        customer.setPayPropEntityId((String) data.get("id"));
        customer.setPayPropCustomerId((String) data.get("customer_id"));
        customer.setEmail((String) data.get("email_address"));
        customer.setMobileNumber((String) data.get("mobile"));
        customer.setPhone((String) data.get("phone"));
        customer.setNotes((String) data.get("comment"));
        
        // FIXED: PayProp beneficiaries are missing account_type but have first/last names
        String firstName = (String) data.get("first_name");
        String lastName = (String) data.get("last_name");
        String businessName = (String) data.get("business_name");
        
        // PayProp beneficiaries are individuals with first_name/last_name
        if (firstName != null && !firstName.trim().isEmpty() && 
            lastName != null && !lastName.trim().isEmpty()) {
            // Use individual data
            customer.setFirstName(firstName);
            customer.setLastName(lastName);
            customer.setName(firstName + " " + lastName);
            customer.setAccountType(AccountType.individual);
        } else if (businessName != null && !businessName.trim().isEmpty()) {
            // Fallback to business name if available
            customer.setBusinessName(businessName);
            customer.setName(businessName);
            customer.setAccountType(AccountType.business);
        } else {
            // Last resort: create placeholder
            customer.setFirstName("Unknown");
            customer.setLastName("Beneficiary");
            customer.setName("Unknown Beneficiary");
            customer.setAccountType(AccountType.individual);
        }
        
        // Update payment method safely
        String paymentMethod = (String) data.get("payment_method");
        if (paymentMethod != null) {
            customer.setPaymentMethod(PaymentMethod.fromPayPropCode(paymentMethod));
        }
        
        // Update address if present
        Map<String, Object> address = (Map<String, Object>) data.get("address");
        if (address != null) {
            customer.setAddressLine1((String) address.get("address_line_1"));
            customer.setAddressLine2((String) address.get("address_line_2"));
            customer.setAddressLine3((String) address.get("address_line_3"));
            customer.setCity((String) address.get("city"));
            customer.setPostcode((String) address.get("postal_code"));
            customer.setCountryCode((String) address.get("country_code"));
        }
        
        customer.setPayPropSynced(true);
        customer.setPayPropLastSync(LocalDateTime.now());
    }

    private Customer createCustomerFromPayPropBeneficiaryData(Map<String, Object> data) {
        Customer customer = new Customer();
        customer.setCustomerType(CustomerType.PROPERTY_OWNER);
        customer.setIsPropertyOwner(true);
        customer.setAccountType(AccountType.valueOf((String) data.get("account_type")));
        updateCustomerFromPayPropBeneficiaryData(customer, data);
        return customer;
    }

    // ===== SPECIFIC ENTITY SYNC METHODS =====
    
    private SyncResult syncSpecificEntities(PayPropChangeDetection.CrmChanges changes, Long initiatedBy) {
        Map<String, Object> results = new HashMap<>();
        int totalSuccess = 0;
        int totalErrors = 0;

        try {
            // Sync modified and new properties
            if (!changes.getModifiedProperties().isEmpty() || !changes.getNewProperties().isEmpty()) {
                List<Property> allProperties = new ArrayList<>();
                allProperties.addAll(changes.getModifiedProperties());
                allProperties.addAll(changes.getNewProperties());
                
                SyncResult propertyResult = processBatchSync(allProperties, "PROPERTY", initiatedBy, 
                    property -> payPropSyncService.syncPropertyToPayProp(property.getId()));
                results.put("properties", propertyResult);
                if (propertyResult.isSuccess()) totalSuccess++; else totalErrors++;
            }

            // Sync modified and new customers
            if (!changes.getModifiedCustomers().isEmpty() || !changes.getNewCustomers().isEmpty()) {
                List<Customer> allCustomers = new ArrayList<>();
                allCustomers.addAll(changes.getModifiedCustomers());
                allCustomers.addAll(changes.getNewCustomers());
                
                SyncResult customerResult = syncCustomerList(allCustomers, initiatedBy);
                results.put("customers", customerResult);
                if (customerResult.isSuccess()) totalSuccess++; else totalErrors++;
            }

            String message = String.format("Specific entity sync completed. Success: %d, Errors: %d", 
                                          totalSuccess, totalErrors);
            
            return totalErrors == 0 ? 
                SyncResult.success(message, results) : 
                SyncResult.partial(message, results);
                
        } catch (Exception e) {
            return SyncResult.failure("Specific entity sync failed: " + e.getMessage(), results);
        }
    }

    private SyncResult pullSpecificChanges(PayPropChangeDetection.PayPropChanges changes, Long initiatedBy) {
        Map<String, Object> results = new HashMap<>();
        int totalSuccess = 0;
        int totalErrors = 0;

        try {
            // Pull modified properties
            if (!changes.getModifiedProperties().isEmpty()) {
                int processedCount = 0;
                for (Map<String, Object> propertyData : changes.getModifiedProperties()) {
                    try {
                        updateOrCreatePropertyFromPayProp(propertyData, initiatedBy);
                        processedCount++;
                    } catch (Exception e) {
                        syncLogger.logEntityError("PROPERTY_PULL", propertyData.get("id"), e);
                    }
                }
                results.put("modifiedProperties", processedCount);
                totalSuccess++;
            }

            // Pull modified tenants
            if (!changes.getModifiedTenants().isEmpty()) {
                int processedCount = 0;
                for (Map<String, Object> tenantData : changes.getModifiedTenants()) {
                    try {
                        updateOrCreateTenantFromPayProp(tenantData, initiatedBy);
                        processedCount++;
                    } catch (Exception e) {
                        syncLogger.logEntityError("TENANT_PULL", tenantData.get("id"), e);
                    }
                }
                results.put("modifiedTenants", processedCount);
                totalSuccess++;
            }

            String message = String.format("Specific changes pull completed. Success: %d, Errors: %d", 
                                          totalSuccess, totalErrors);
            
            return SyncResult.success(message, results);
                
        } catch (Exception e) {
            return SyncResult.failure("Specific changes pull failed: " + e.getMessage(), results);
        }
    }

    private SyncResult syncCustomerList(List<Customer> customers, Long initiatedBy) {
        int successCount = 0;
        int errorCount = 0;
        List<String> errors = new ArrayList<>();

        for (Customer customer : customers) {
            try {
                if (customer.getCustomerType() == CustomerType.TENANT) {
                    syncCustomerAsTenant(customer);
                } else if (customer.getCustomerType() == CustomerType.PROPERTY_OWNER) {
                    syncCustomerAsBeneficiary(customer);
                }
                successCount++;
            } catch (Exception e) {
                errorCount++;
                errors.add("Customer " + customer.getCustomerId() + ": " + e.getMessage());
            }
        }

        Map<String, Object> details = Map.of(
            "total", customers.size(),
            "success", successCount,
            "errors", errorCount,
            "errorMessages", errors
        );

        String message = String.format("Customer sync completed. Success: %d, Errors: %d", 
                                      successCount, errorCount);
        
        return errorCount == 0 ? 
            SyncResult.success(message, details) : 
            SyncResult.partial(message, details);
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
        private String overallError;
        private String message;

        public boolean isOverallSuccess() {
            return overallError == null && 
                   (crmToPayPropResult == null || crmToPayPropResult.isSuccess()) &&
                   (payPropToCrmResult == null || payPropToCrmResult.isSuccess()) &&
                   (conflictResolutionResult == null || conflictResolutionResult.isSuccess()) &&
                   (portfolioSyncResult == null || portfolioSyncResult.isSuccess());
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
        
        public String getOverallError() { return overallError; }
        public void setOverallError(String overallError) { this.overallError = overallError; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
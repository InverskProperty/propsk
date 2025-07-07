// PayPropSyncLogger.java - ENHANCED DEBUG VERSION with Comprehensive Entity Tracking
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.PortfolioSyncLog;
import site.easy.to.build.crm.repository.PortfolioSyncLogRepository;
import site.easy.to.build.crm.service.payprop.PayPropConflictResolver.SyncConflict;
import site.easy.to.build.crm.service.payprop.PayPropConflictResolver.ConflictResolution;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PayPropSyncLogger {

    private final PortfolioSyncLogRepository syncLogRepository;
    
    // Debug mode configuration
    @Value("${payprop.sync.debug-mode:false}")
    private boolean globalDebugMode;
    
    @Value("${payprop.sync.debug-sample-size:5}")
    private int debugSampleSize;
    
    // Thread-local debug mode (for per-operation control)
    private final ThreadLocal<Boolean> debugModeOverride = new ThreadLocal<>();
    
    // Counters and samples for debug mode
    private final ThreadLocal<Map<String, AtomicInteger>> operationCounters = ThreadLocal.withInitial(ConcurrentHashMap::new);
    private final ThreadLocal<Map<String, List<String>>> operationSamples = ThreadLocal.withInitial(ConcurrentHashMap::new);
    private final ThreadLocal<Map<String, Long>> operationTimers = ThreadLocal.withInitial(ConcurrentHashMap::new);

    @Autowired
    public PayPropSyncLogger(PortfolioSyncLogRepository syncLogRepository) {
        this.syncLogRepository = syncLogRepository;
    }

    // ===== DEBUG MODE CONTROL =====
    
    public void setDebugMode(boolean enabled) {
        debugModeOverride.set(enabled);
        if (enabled) {
            operationCounters.get().clear();
            operationSamples.get().clear();
            operationTimers.get().clear();
            System.out.println("🔍 DEBUG MODE ENABLED - Detailed tracking active");
        }
    }
    
    public void clearDebugMode() {
        debugModeOverride.remove();
        operationCounters.remove();
        operationSamples.remove();
        operationTimers.remove();
        System.out.println("🔍 DEBUG MODE DISABLED");
    }
    
    private boolean isDebugMode() {
        Boolean override = debugModeOverride.get();
        return override != null ? override : globalDebugMode;
    }

    // ===== ENHANCED BASIC SYNC LOGGING =====

    public void logSyncStart(String syncType, Long initiatedBy) {
        if (isDebugMode()) {
            operationTimers.get().put("SYNC_" + syncType, System.currentTimeMillis());
            System.out.println("🚀 SYNC STARTED: " + syncType + " (DEBUG MODE) at " + LocalDateTime.now());
            System.out.println("   Initiated by user ID: " + initiatedBy);
        } else {
            System.out.println("🚀 SYNC STARTED: " + syncType + " at " + LocalDateTime.now());
        }
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType(syncType);
        log.setOperation("START");
        log.setStatus("PENDING");
        log.setSyncStartedAt(LocalDateTime.now());
        log.setInitiatedBy(initiatedBy);
        syncLogRepository.save(log);
    }

    public void logSyncComplete(String syncType, boolean success, String summary) {
        long duration = 0;
        if (isDebugMode()) {
            Long startTime = operationTimers.get().get("SYNC_" + syncType);
            if (startTime != null) {
                duration = System.currentTimeMillis() - startTime;
            }
            
            System.out.println((success ? "✅ SYNC COMPLETED: " : "❌ SYNC FAILED: ") + syncType + " (DEBUG MODE)");
            System.out.println("   Duration: " + duration + "ms");
            System.out.println("   Summary: " + summary);
            printComprehensiveDebugSummary();
        } else {
            System.out.println(success ? "✅ SYNC COMPLETED: " : "❌ SYNC FAILED: " + syncType);
            System.out.println("Summary: " + summary);
            System.out.println("Completed at: " + LocalDateTime.now());
        }
    }

    public void logSyncError(String syncType, Exception error) {
        if (isDebugMode()) {
            incrementCounter("SYNC_ERRORS");
            addSample("SYNC_ERRORS", syncType + ": " + error.getMessage());
        }
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType(syncType);
        log.setOperation("ERROR");
        log.setStatus("FAILED");
        log.setErrorMessage(error.getMessage());
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        syncLogRepository.save(log);
        
        System.err.println("❌ SYNC ERROR: " + syncType + " - " + error.getMessage());
        if (isDebugMode()) {
            error.printStackTrace();
        }
    }

    // ===== ENHANCED PROPERTY LOGGING =====

    public void logPropertyProcessingStart(String payPropId, int currentPage, int totalOnPage) {
        if (isDebugMode()) {
            System.out.println("🏠 PROCESSING PROPERTY: " + payPropId + " (Page " + currentPage + ", Item on page)");
        }
    }

    public void logPropertySaveSuccess(String payPropId, Long propertyId, boolean isNew) {
        if (isDebugMode()) {
            String key = isNew ? "PROPERTY_CREATED" : "PROPERTY_UPDATED";
            incrementCounter(key);
            addSample(key, "PayProp ID " + payPropId + " -> DB ID " + propertyId);
            
            String action = isNew ? "CREATED" : "UPDATED";
            System.out.println("✅ PROPERTY " + action + ": PayProp ID " + payPropId + " -> DB ID " + propertyId);
        }
    }

    public void logPropertySaveError(String payPropId, Exception error) {
        if (isDebugMode()) {
            incrementCounter("PROPERTY_SAVE_ERRORS");
            addSample("PROPERTY_SAVE_ERRORS", "PayProp ID " + payPropId + ": " + error.getMessage());
            System.err.println("❌ PROPERTY SAVE FAILED: PayProp ID " + payPropId + " - " + error.getMessage());
        }
        logEntityError("PROPERTY_SAVE", payPropId, error);
    }

    public void logPropertyValidationError(String payPropId, String validationError) {
        if (isDebugMode()) {
            incrementCounter("PROPERTY_VALIDATION_ERRORS");
            addSample("PROPERTY_VALIDATION_ERRORS", "PayProp ID " + payPropId + ": " + validationError);
            System.err.println("⚠️ PROPERTY VALIDATION FAILED: PayProp ID " + payPropId + " - " + validationError);
        }
    }

    public void logPropertySkipped(String payPropId, String reason) {
        if (isDebugMode()) {
            incrementCounter("PROPERTY_SKIPPED");
            addSample("PROPERTY_SKIPPED", "PayProp ID " + payPropId + ": " + reason);
            System.out.println("⏭️ PROPERTY SKIPPED: PayProp ID " + payPropId + " - " + reason);
        }
    }

    public void logPropertyDuplicateHandling(String payPropId, String action) {
        if (isDebugMode()) {
            incrementCounter("PROPERTY_DUPLICATE_HANDLING");
            addSample("PROPERTY_DUPLICATE_HANDLING", "PayProp ID " + payPropId + ": " + action);
            System.out.println("🔄 PROPERTY DUPLICATE: PayProp ID " + payPropId + " - " + action);
        }
    }

    // ===== ENHANCED TENANT LOGGING =====

    public void logTenantProcessingStart(String payPropId, int currentPage, int totalOnPage) {
        if (isDebugMode()) {
            System.out.println("👤 PROCESSING TENANT: " + payPropId + " (Page " + currentPage + ", Item on page)");
        }
    }

    public void logTenantSaveSuccess(String payPropId, Long tenantId, boolean isNew) {
        if (isDebugMode()) {
            String key = isNew ? "TENANT_CREATED" : "TENANT_UPDATED";
            incrementCounter(key);
            addSample(key, "PayProp ID " + payPropId + " -> DB ID " + tenantId);
            
            String action = isNew ? "CREATED" : "UPDATED";
            System.out.println("✅ TENANT " + action + ": PayProp ID " + payPropId + " -> DB ID " + tenantId);
        }
    }

    public void logTenantSaveError(String payPropId, Exception error) {
        if (isDebugMode()) {
            incrementCounter("TENANT_SAVE_ERRORS");
            addSample("TENANT_SAVE_ERRORS", "PayProp ID " + payPropId + ": " + error.getMessage());
            System.err.println("❌ TENANT SAVE FAILED: PayProp ID " + payPropId + " - " + error.getMessage());
        }
        logEntityError("TENANT_SAVE", payPropId, error);
    }

    public void logTenantValidationError(String payPropId, String validationError) {
        if (isDebugMode()) {
            incrementCounter("TENANT_VALIDATION_ERRORS");
            addSample("TENANT_VALIDATION_ERRORS", "PayProp ID " + payPropId + ": " + validationError);
            System.err.println("⚠️ TENANT VALIDATION FAILED: PayProp ID " + payPropId + " - " + validationError);
        }
    }

    public void logTenantSkipped(String payPropId, String reason) {
        if (isDebugMode()) {
            incrementCounter("TENANT_SKIPPED");
            addSample("TENANT_SKIPPED", "PayProp ID " + payPropId + ": " + reason);
            System.out.println("⏭️ TENANT SKIPPED: PayProp ID " + payPropId + " - " + reason);
        }
    }

    public void logTenantDuplicateHandling(String payPropId, String action) {
        if (isDebugMode()) {
            incrementCounter("TENANT_DUPLICATE_HANDLING");
            addSample("TENANT_DUPLICATE_HANDLING", "PayProp ID " + payPropId + ": " + action);
            System.out.println("🔄 TENANT DUPLICATE: PayProp ID " + payPropId + " - " + action);
        }
    }

    public void logTenantMobileFormatting(String payPropId, String originalMobile, String formattedMobile, boolean success) {
        if (isDebugMode()) {
            if (success) {
                incrementCounter("TENANT_MOBILE_FORMATTED");
                if (!originalMobile.equals(formattedMobile)) {
                    addSample("TENANT_MOBILE_FORMATTED", "PayProp ID " + payPropId + ": '" + originalMobile + "' -> '" + formattedMobile + "'");
                }
            } else {
                incrementCounter("TENANT_MOBILE_FORMAT_ERRORS");
                addSample("TENANT_MOBILE_FORMAT_ERRORS", "PayProp ID " + payPropId + ": Invalid mobile '" + originalMobile + "'");
            }
        }
    }

    // ===== ENHANCED BENEFICIARY/PROPERTY OWNER LOGGING =====

    public void logBeneficiaryProcessingStart(String payPropId, int currentPage, int totalOnPage) {
        if (isDebugMode()) {
            System.out.println("🏦 PROCESSING BENEFICIARY: " + payPropId + " (Page " + currentPage + ", Item on page)");
        }
    }

    public void logBeneficiarySaveSuccess(String payPropId, Long propertyOwnerId, boolean isNew) {
        if (isDebugMode()) {
            String key = isNew ? "BENEFICIARY_CREATED" : "BENEFICIARY_UPDATED";
            incrementCounter(key);
            addSample(key, "PayProp ID " + payPropId + " -> DB ID " + propertyOwnerId);
            
            String action = isNew ? "CREATED" : "UPDATED";
            System.out.println("✅ BENEFICIARY " + action + ": PayProp ID " + payPropId + " -> DB ID " + propertyOwnerId);
        }
    }

    public void logBeneficiarySaveError(String payPropId, Exception error) {
        if (isDebugMode()) {
            incrementCounter("BENEFICIARY_SAVE_ERRORS");
            addSample("BENEFICIARY_SAVE_ERRORS", "PayProp ID " + payPropId + ": " + error.getMessage());
            System.err.println("❌ BENEFICIARY SAVE FAILED: PayProp ID " + payPropId + " - " + error.getMessage());
        }
        logEntityError("BENEFICIARY_SAVE", payPropId, error);
    }

    public void logBeneficiaryValidationError(String payPropId, String validationError) {
        if (isDebugMode()) {
            incrementCounter("BENEFICIARY_VALIDATION_ERRORS");
            addSample("BENEFICIARY_VALIDATION_ERRORS", "PayProp ID " + payPropId + ": " + validationError);
            System.err.println("⚠️ BENEFICIARY VALIDATION FAILED: PayProp ID " + payPropId + " - " + validationError);
        }
    }

    public void logBeneficiarySkipped(String payPropId, String reason) {
        if (isDebugMode()) {
            incrementCounter("BENEFICIARY_SKIPPED");
            addSample("BENEFICIARY_SKIPPED", "PayProp ID " + payPropId + ": " + reason);
            System.out.println("⏭️ BENEFICIARY SKIPPED: PayProp ID " + payPropId + " - " + reason);
        }
    }

    public void logBeneficiaryPropertyIdMissing(String payPropId, String details) {
        if (isDebugMode()) {
            incrementCounter("BENEFICIARY_PROPERTY_ID_MISSING");
            addSample("BENEFICIARY_PROPERTY_ID_MISSING", "PayProp ID " + payPropId + ": " + details);
            System.err.println("💥 BENEFICIARY PROPERTY_ID MISSING: PayProp ID " + payPropId + " - " + details);
        }
    }

    public void logBeneficiaryDuplicateHandling(String payPropId, String action) {
        if (isDebugMode()) {
            incrementCounter("BENEFICIARY_DUPLICATE_HANDLING");
            addSample("BENEFICIARY_DUPLICATE_HANDLING", "PayProp ID " + payPropId + ": " + action);
            System.out.println("🔄 BENEFICIARY DUPLICATE: PayProp ID " + payPropId + " - " + action);
        }
    }

    public void logBeneficiaryMobileFormatting(String payPropId, String originalMobile, String formattedMobile, boolean success) {
        if (isDebugMode()) {
            if (success) {
                incrementCounter("BENEFICIARY_MOBILE_FORMATTED");
                if (!originalMobile.equals(formattedMobile)) {
                    addSample("BENEFICIARY_MOBILE_FORMATTED", "PayProp ID " + payPropId + ": '" + originalMobile + "' -> '" + formattedMobile + "'");
                }
            } else {
                incrementCounter("BENEFICIARY_MOBILE_FORMAT_ERRORS");
                addSample("BENEFICIARY_MOBILE_FORMAT_ERRORS", "PayProp ID " + payPropId + ": Invalid mobile '" + originalMobile + "'");
            }
        }
    }

    // ===== API CALL LOGGING =====

    public void logApiCallStart(String endpoint, String method, int page) {
        if (isDebugMode()) {
            String key = "API_CALLS_" + endpoint.toUpperCase().replace("/", "_");
            incrementCounter(key);
            System.out.println("📡 API CALL START: " + method + " " + endpoint + " (Page " + page + ")");
        }
    }

    public void logApiCallSuccess(String endpoint, String method, int itemsReturned, long durationMs) {
        if (isDebugMode()) {
            String key = "API_CALL_SUCCESS";
            incrementCounter(key);
            addSample(key, method + " " + endpoint + ": " + itemsReturned + " items in " + durationMs + "ms");
            System.out.println("✅ API CALL SUCCESS: " + method + " " + endpoint + " - " + itemsReturned + " items in " + durationMs + "ms");
        }
    }

    public void logApiCallError(String endpoint, String method, Exception error, long durationMs) {
        if (isDebugMode()) {
            incrementCounter("API_CALL_ERRORS");
            addSample("API_CALL_ERRORS", method + " " + endpoint + ": " + error.getMessage() + " (after " + durationMs + "ms)");
            System.err.println("❌ API CALL ERROR: " + method + " " + endpoint + " - " + error.getMessage() + " (after " + durationMs + "ms)");
        }
    }

    // ===== BATCH PROCESSING LOGGING =====

    public void logBatchStart(String entityType, int page, int pageSize) {
        if (isDebugMode()) {
            System.out.println("📦 BATCH START: " + entityType + " Page " + page + " (Size: " + pageSize + ")");
        }
    }

    public void logBatchComplete(String entityType, int page, int processed, int created, int updated, int errors, int skipped) {
        if (isDebugMode()) {
            String key = entityType.toUpperCase() + "_BATCH_SUMMARY";
            incrementCounter(key);
            addSample(key, "Page " + page + ": " + processed + " processed, " + created + " created, " + updated + " updated, " + errors + " errors, " + skipped + " skipped");
            
            System.out.println("📦 BATCH COMPLETE: " + entityType + " Page " + page);
            System.out.println("   Processed: " + processed + ", Created: " + created + ", Updated: " + updated + ", Errors: " + errors + ", Skipped: " + skipped);
        }
    }

    // ===== DATABASE CONSTRAINT LOGGING =====

    public void logConstraintViolation(String entityType, String payPropId, String constraintName, String details) {
        if (isDebugMode()) {
            String key = entityType.toUpperCase() + "_CONSTRAINT_VIOLATIONS";
            incrementCounter(key);
            addSample(key, "PayProp ID " + payPropId + " - " + constraintName + ": " + details);
            System.err.println("💥 CONSTRAINT VIOLATION: " + entityType + " PayProp ID " + payPropId + " - " + constraintName + ": " + details);
        }
    }

    public void logForeignKeyError(String entityType, String payPropId, String foreignKeyField, String missingValue) {
        if (isDebugMode()) {
            String key = entityType.toUpperCase() + "_FOREIGN_KEY_ERRORS";
            incrementCounter(key);
            addSample(key, "PayProp ID " + payPropId + " - Missing " + foreignKeyField + ": " + missingValue);
            System.err.println("🔗 FOREIGN KEY ERROR: " + entityType + " PayProp ID " + payPropId + " - Missing " + foreignKeyField + ": " + missingValue);
        }
    }

    // ===== TRANSACTION LOGGING =====

    public void logTransactionStart(String operation) {
        if (isDebugMode()) {
            operationTimers.get().put("TRANSACTION_" + operation, System.currentTimeMillis());
            System.out.println("🔄 TRANSACTION START: " + operation);
        }
    }

    public void logTransactionComplete(String operation, boolean success) {
        if (isDebugMode()) {
            Long startTime = operationTimers.get().get("TRANSACTION_" + operation);
            long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;
            
            String key = success ? "TRANSACTION_SUCCESS" : "TRANSACTION_ROLLBACK";
            incrementCounter(key);
            addSample(key, operation + " (" + duration + "ms)");
            
            String status = success ? "✅ COMMITTED" : "🔄 ROLLBACK";
            System.out.println(status + " TRANSACTION: " + operation + " (" + duration + "ms)");
        }
    }

    // ===== UTILITY METHODS =====

    private void incrementCounter(String key) {
        if (isDebugMode()) {
            operationCounters.get().computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        }
    }

    private void addSample(String key, String sample) {
        if (isDebugMode()) {
            List<String> samples = operationSamples.get().computeIfAbsent(key, k -> new ArrayList<>());
            if (samples.size() < debugSampleSize) {
                samples.add(sample);
            }
        }
    }

    // ===== ENHANCED DEBUG SUMMARY =====

    private void printComprehensiveDebugSummary() {
        if (!isDebugMode()) return;
        
        System.out.println("\n📊 COMPREHENSIVE DEBUG SUMMARY:");
        System.out.println("============================================================");
        
        Map<String, AtomicInteger> counters = operationCounters.get();
        Map<String, List<String>> samples = operationSamples.get();
        
        // Group by entity type
        Map<String, Map<String, Object>> groupedResults = new HashMap<>();
        
        for (Map.Entry<String, AtomicInteger> entry : counters.entrySet()) {
            String operation = entry.getKey();
            int count = entry.getValue().get();
            
            String entityType = extractEntityType(operation);
            groupedResults.computeIfAbsent(entityType, k -> new HashMap<>()).put(operation, count);
        }
        
        // Print grouped results
        for (Map.Entry<String, Map<String, Object>> group : groupedResults.entrySet()) {
            String entityType = group.getKey();
            Map<String, Object> operations = group.getValue();
            
            System.out.println("\n🔍 " + entityType + ":");
            for (Map.Entry<String, Object> op : operations.entrySet()) {
                String operation = op.getKey();
                Object count = op.getValue();
                
                System.out.println("  " + operation + ": " + count + " total");
                
                List<String> operationSamples = samples.get(operation);
                if (operationSamples != null && !operationSamples.isEmpty()) {
                    System.out.println("    Sample entries:");
                    for (String sample : operationSamples) {
                        System.out.println("      - " + sample);
                    }
                }
            }
        }
        
        // Summary statistics
        printSummaryStatistics(counters);
        
        System.out.println("============================================================");
        System.out.println();
    }

    private String extractEntityType(String operation) {
        if (operation.startsWith("PROPERTY_")) return "PROPERTIES";
        if (operation.startsWith("TENANT_")) return "TENANTS";
        if (operation.startsWith("BENEFICIARY_")) return "BENEFICIARIES";
        if (operation.startsWith("API_")) return "API_CALLS";
        if (operation.startsWith("TRANSACTION_")) return "TRANSACTIONS";
        if (operation.startsWith("RELATIONSHIP_")) return "RELATIONSHIPS";
        if (operation.startsWith("CONFLICT_")) return "CONFLICTS";
        return "OTHER";
    }

    private void printSummaryStatistics(Map<String, AtomicInteger> counters) {
        System.out.println("\n📈 SUMMARY STATISTICS:");
        
        // Calculate totals
        int totalCreated = getCounterValue(counters, "PROPERTY_CREATED") + 
                          getCounterValue(counters, "TENANT_CREATED") + 
                          getCounterValue(counters, "BENEFICIARY_CREATED");
        
        int totalUpdated = getCounterValue(counters, "PROPERTY_UPDATED") + 
                          getCounterValue(counters, "TENANT_UPDATED") + 
                          getCounterValue(counters, "BENEFICIARY_UPDATED");
        
        int totalErrors = getCounterValue(counters, "PROPERTY_SAVE_ERRORS") + 
                         getCounterValue(counters, "TENANT_SAVE_ERRORS") + 
                         getCounterValue(counters, "BENEFICIARY_SAVE_ERRORS");
        
        int totalSkipped = getCounterValue(counters, "PROPERTY_SKIPPED") + 
                          getCounterValue(counters, "TENANT_SKIPPED") + 
                          getCounterValue(counters, "BENEFICIARY_SKIPPED");
        
        System.out.println("  Total Created: " + totalCreated);
        System.out.println("  Total Updated: " + totalUpdated);
        System.out.println("  Total Errors: " + totalErrors);
        System.out.println("  Total Skipped: " + totalSkipped);
        System.out.println("  Success Rate: " + calculateSuccessRate(totalCreated + totalUpdated, totalErrors) + "%");
    }

    private int getCounterValue(Map<String, AtomicInteger> counters, String key) {
        AtomicInteger counter = counters.get(key);
        return counter != null ? counter.get() : 0;
    }

    private double calculateSuccessRate(int successful, int errors) {
        int total = successful + errors;
        return total > 0 ? (successful * 100.0 / total) : 0.0;
    }

    // ===== EXISTING METHODS (keeping compatibility) =====

    public void logEntityError(String operation, Object entityId, Exception error) {
        if (isDebugMode()) {
            String key = operation + "_ERRORS";
            incrementCounter(key);
            addSample(key, "Entity " + entityId + ": " + error.getMessage());
        }
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType(operation);
        log.setOperation("ENTITY_ERROR");
        log.setStatus("FAILED");
        log.setErrorMessage("Entity " + entityId + ": " + error.getMessage());
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        syncLogRepository.save(log);
    }

    // ===== RELATIONSHIP LOGGING METHODS (existing) =====

    public void logRelationshipCreated(String relationshipType, String sourceEntityId, String targetEntityId, String details) {
        if (isDebugMode()) {
            incrementCounter("RELATIONSHIPS_CREATED");
            addSample("RELATIONSHIPS_CREATED", relationshipType + ": " + sourceEntityId + " -> " + targetEntityId + " (" + details + ")");
            System.out.println("🔗 RELATIONSHIP CREATED: " + relationshipType + " " + sourceEntityId + " -> " + targetEntityId);
        }
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType("RELATIONSHIP_CREATION");
        log.setOperation("CREATE_RELATIONSHIP");
        log.setStatus("SUCCESS");
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        syncLogRepository.save(log);
    }

    public void logRelationshipFixed(String validationType, String sourceEntityId, String targetEntityId, String fixDescription) {
        if (isDebugMode()) {
            incrementCounter("RELATIONSHIPS_FIXED");
            addSample("RELATIONSHIPS_FIXED", validationType + ": " + sourceEntityId + " -> " + targetEntityId + " (Fixed: " + fixDescription + ")");
            System.out.println("🔧 RELATIONSHIP FIXED: " + validationType + " " + sourceEntityId + " -> " + targetEntityId);
        }
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType("RELATIONSHIP_FIX");
        log.setOperation("FIX_RELATIONSHIP");
        log.setStatus("SUCCESS");
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        syncLogRepository.save(log);
    }

    // ===== CONFLICT LOGGING (existing methods) =====

    public void logConflictDetection(int conflictCount) {
        if (isDebugMode()) {
            incrementCounter("CONFLICTS_DETECTED");
            addSample("CONFLICTS_DETECTED", "Found " + conflictCount + " conflicts");
            System.out.println("🔍 CONFLICT DETECTION: Found " + conflictCount + " conflicts");
        }
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType("CONFLICT_DETECTION");
        log.setOperation("DETECT");
        log.setStatus("COMPLETED");
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        syncLogRepository.save(log);
    }

    public void logConflictResolution(SyncConflict conflict, ConflictResolution resolution) {
        if (isDebugMode()) {
            String key = resolution.isResolved() ? "CONFLICTS_RESOLVED" : "CONFLICTS_UNRESOLVED";
            incrementCounter(key);
            addSample(key, conflict.getEntityType() + " " + conflict.getEntityId() + ": " + resolution.getStrategy());
        }
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType("CONFLICT_RESOLUTION");
        log.setOperation("RESOLVE");
        log.setStatus(resolution.isResolved() ? "COMPLETED" : "FAILED");
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        syncLogRepository.save(log);
    }

    public void logConflictError(SyncConflict conflict, Exception error) {
        if (isDebugMode()) {
            incrementCounter("CONFLICT_ERRORS");
            addSample("CONFLICT_ERRORS", conflict.getEntityType() + " " + conflict.getEntityId() + ": " + error.getMessage());
        }
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType("CONFLICT_RESOLUTION");
        log.setOperation("ERROR");
        log.setStatus("FAILED");
        log.setErrorMessage("Conflict " + conflict.getEntityId() + ": " + error.getMessage());
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        syncLogRepository.save(log);
    }

    // ===== OTHER UTILITY METHODS (existing) =====

    public void logBatchOperation(String operation, int totalCount, int successCount, int errorCount) {
        if (isDebugMode()) {
            incrementCounter("BATCH_OPERATIONS");
            addSample("BATCH_OPERATIONS", operation + ": " + totalCount + " total, " + successCount + " success, " + errorCount + " errors");
            System.out.println("📊 BATCH " + operation + ": " + totalCount + " total, " + successCount + " success, " + errorCount + " errors");
        }
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType("BATCH_" + operation);
        log.setOperation("BATCH");
        log.setStatus(errorCount == 0 ? "COMPLETED" : "PARTIAL");
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        syncLogRepository.save(log);
    }

    public void logApiCall(String endpoint, String method, boolean success, long duration) {
        if (isDebugMode()) {
            String key = success ? "API_CALL_SUCCESS" : "API_CALL_ERRORS";
            incrementCounter(key);
            addSample(key, method + " " + endpoint + " (" + duration + "ms)");
        }
    }

    public void logWebhookReceived(String webhookType, String payload) {
        if (isDebugMode()) {
            incrementCounter("WEBHOOKS_RECEIVED");
            addSample("WEBHOOKS_RECEIVED", webhookType + " (Size: " + (payload != null ? payload.length() : 0) + " chars)");
            System.out.println("🔔 WEBHOOK RECEIVED: " + webhookType);
        }
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType("WEBHOOK");
        log.setOperation("RECEIVE");
        log.setStatus("COMPLETED");
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        syncLogRepository.save(log);
    }

    public void logValidationError(String entityType, Object entityId, String validationError) {
        if (isDebugMode()) {
            String key = entityType.toUpperCase() + "_VALIDATION_ERRORS";
            incrementCounter(key);
            addSample(key, "ID " + entityId + ": " + validationError);
        }
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType("VALIDATION");
        log.setOperation("VALIDATE");
        log.setStatus("FAILED");
        log.setErrorMessage(validationError);
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        syncLogRepository.save(log);
    }

    // ===== SYNC STATISTICS (existing) =====

    public SyncStatistics getSyncStatistics(LocalDateTime since) {
        SyncStatistics stats = new SyncStatistics();
        stats.setSince(since);
        stats.setGeneratedAt(LocalDateTime.now());
        
        try {
            List<PortfolioSyncLog> allLogs = syncLogRepository.findAll();
            List<PortfolioSyncLog> logsInPeriod = allLogs.stream()
                .filter(log -> log.getSyncStartedAt() != null && log.getSyncStartedAt().isAfter(since))
                .toList();
            
            stats.setTotalSyncs(logsInPeriod.size());
            
            long successfulSyncs = logsInPeriod.stream()
                .filter(log -> "COMPLETED".equals(log.getStatus()) || "SUCCESS".equals(log.getStatus()))
                .count();
            stats.setSuccessfulSyncs((int) successfulSyncs);
            
            long failedSyncs = logsInPeriod.stream()
                .filter(log -> "FAILED".equals(log.getStatus()) || "ERROR".equals(log.getStatus()))
                .count();
            stats.setFailedSyncs((int) failedSyncs);
            
            long conflictsDetected = logsInPeriod.stream()
                .filter(log -> "CONFLICT_DETECTION".equals(log.getSyncType()) || "CONFLICT_RESOLUTION".equals(log.getSyncType()))
                .count();
            stats.setConflictsDetected((int) conflictsDetected);
            
            long conflictsResolved = logsInPeriod.stream()
                .filter(log -> "CONFLICT_RESOLUTION".equals(log.getSyncType()) && "COMPLETED".equals(log.getStatus()))
                .count();
            stats.setConflictsResolved((int) conflictsResolved);
            
            double avgDuration = logsInPeriod.stream()
                .filter(log -> log.getSyncStartedAt() != null && log.getSyncCompletedAt() != null)
                .mapToLong(log -> java.time.Duration.between(log.getSyncStartedAt(), log.getSyncCompletedAt()).toMillis())
                .average()
                .orElse(0.0);
            stats.setAverageSyncDuration(avgDuration);
            
            Map<String, Integer> breakdown = new HashMap<>();
            logsInPeriod.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    log -> log.getSyncType() != null ? log.getSyncType() : "UNKNOWN",
                    java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.counting(),
                        Math::toIntExact
                    )
                ))
                .forEach(breakdown::put);
            stats.setSyncTypeBreakdown(breakdown);
            
        } catch (Exception e) {
            System.err.println("Error calculating sync statistics: " + e.getMessage());
            stats.setTotalSyncs(0);
            stats.setSuccessfulSyncs(0);
            stats.setFailedSyncs(0);
            stats.setConflictsDetected(0);
            stats.setConflictsResolved(0);
            stats.setAverageSyncDuration(0.0);
            stats.setSyncTypeBreakdown(new HashMap<>());
        }
        
        return stats;
    }

    // ===== SYNC STATISTICS CLASS (existing) =====
    public static class SyncStatistics {
        private LocalDateTime since;
        private LocalDateTime generatedAt;
        private int totalSyncs;
        private int successfulSyncs;
        private int failedSyncs;
        private int conflictsDetected;
        private int conflictsResolved;
        private double averageSyncDuration;
        private Map<String, Integer> syncTypeBreakdown;

        public SyncStatistics() {
            this.syncTypeBreakdown = new HashMap<>();
        }

        // Getters and setters
        public LocalDateTime getSince() { return since; }
        public void setSince(LocalDateTime since) { this.since = since; }
        
        public LocalDateTime getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
        
        public int getTotalSyncs() { return totalSyncs; }
        public void setTotalSyncs(int totalSyncs) { this.totalSyncs = totalSyncs; }
        
        public int getSuccessfulSyncs() { return successfulSyncs; }
        public void setSuccessfulSyncs(int successfulSyncs) { this.successfulSyncs = successfulSyncs; }
        
        public int getFailedSyncs() { return failedSyncs; }
        public void setFailedSyncs(int failedSyncs) { this.failedSyncs = failedSyncs; }
        
        public int getConflictsDetected() { return conflictsDetected; }
        public void setConflictsDetected(int conflictsDetected) { this.conflictsDetected = conflictsDetected; }
        
        public int getConflictsResolved() { return conflictsResolved; }
        public void setConflictsResolved(int conflictsResolved) { this.conflictsResolved = conflictsResolved; }
        
        public double getAverageSyncDuration() { return averageSyncDuration; }
        public void setAverageSyncDuration(double averageSyncDuration) { this.averageSyncDuration = averageSyncDuration; }
        
        public Map<String, Integer> getSyncTypeBreakdown() { return syncTypeBreakdown; }
        public void setSyncTypeBreakdown(Map<String, Integer> syncTypeBreakdown) { this.syncTypeBreakdown = syncTypeBreakdown; }
        
        public double getSuccessRate() {
            return totalSyncs > 0 ? (successfulSyncs * 100.0 / totalSyncs) : 0;
        }
        
        public double getConflictResolutionRate() {
            return conflictsDetected > 0 ? (conflictsResolved * 100.0 / conflictsDetected) : 0;
        }
    }
}
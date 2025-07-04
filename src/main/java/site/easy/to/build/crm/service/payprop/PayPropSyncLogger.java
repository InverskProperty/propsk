// PayPropSyncLogger.java - Comprehensive Sync Logging with Debug Mode
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
    
    // NEW: Debug mode configuration
    @Value("${payprop.sync.debug-mode:false}")
    private boolean globalDebugMode;
    
    @Value("${payprop.sync.debug-sample-size:2}")
    private int debugSampleSize;
    
    // Thread-local debug mode (for per-operation control)
    private final ThreadLocal<Boolean> debugModeOverride = new ThreadLocal<>();
    
    // Counters and samples for debug mode
    private final ThreadLocal<Map<String, AtomicInteger>> operationCounters = ThreadLocal.withInitial(ConcurrentHashMap::new);
    private final ThreadLocal<Map<String, List<String>>> operationSamples = ThreadLocal.withInitial(ConcurrentHashMap::new);

    @Autowired
    public PayPropSyncLogger(PortfolioSyncLogRepository syncLogRepository) {
        this.syncLogRepository = syncLogRepository;
    }

    // ===== DEBUG MODE CONTROL =====
    
    public void setDebugMode(boolean enabled) {
        debugModeOverride.set(enabled);
        if (enabled) {
            // Clear counters and samples for new debug session
            operationCounters.get().clear();
            operationSamples.get().clear();
        }
    }
    
    public void clearDebugMode() {
        debugModeOverride.remove();
        operationCounters.remove();
        operationSamples.remove();
    }
    
    private boolean isDebugMode() {
        Boolean override = debugModeOverride.get();
        return override != null ? override : globalDebugMode;
    }

    /**
     * Log sync operation start
     */
    public void logSyncStart(String syncType, Long initiatedBy) {
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType(syncType);
        log.setOperation("START");
        log.setStatus("PENDING");
        log.setSyncStartedAt(LocalDateTime.now());
        log.setInitiatedBy(initiatedBy);
        
        syncLogRepository.save(log);
        
        if (isDebugMode()) {
            System.out.println("🚀 SYNC STARTED: " + syncType + " (DEBUG MODE)");
        } else {
            System.out.println("🚀 SYNC STARTED: " + syncType + " at " + LocalDateTime.now());
        }
    }

    /**
     * Log sync operation completion
     */
    public void logSyncComplete(String syncType, boolean success, String summary) {
        if (isDebugMode()) {
            System.out.println((success ? "✅ SYNC COMPLETED: " : "❌ SYNC FAILED: ") + syncType + " (DEBUG MODE)");
            System.out.println("Summary: " + summary);
            
            // Print debug summary
            printDebugSummary();
        } else {
            System.out.println(success ? "✅ SYNC COMPLETED: " : "❌ SYNC FAILED: " + syncType);
            System.out.println("Summary: " + summary);
            System.out.println("Completed at: " + LocalDateTime.now());
        }
    }

    /**
     * Log sync error
     */
    public void logSyncError(String syncType, Exception error) {
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType(syncType);
        log.setOperation("ERROR");
        log.setStatus("FAILED");
        log.setErrorMessage(error.getMessage());
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        
        syncLogRepository.save(log);
        
        if (isDebugMode()) {
            System.err.println("❌ SYNC ERROR: " + syncType + " - " + error.getMessage());
        } else {
            System.err.println("❌ SYNC ERROR: " + syncType);
            System.err.println("Error: " + error.getMessage());
            error.printStackTrace();
        }
    }

    /**
     * Log entity-specific error with debug mode support
     */
    public void logEntityError(String operation, Object entityId, Exception error) {
        if (isDebugMode()) {
            // Count errors and collect samples
            String key = operation + "_ERRORS";
            AtomicInteger counter = operationCounters.get().computeIfAbsent(key, k -> new AtomicInteger(0));
            counter.incrementAndGet();
            
            List<String> samples = operationSamples.get().computeIfAbsent(key, k -> new ArrayList<>());
            if (samples.size() < debugSampleSize) {
                samples.add("Entity " + entityId + ": " + error.getMessage());
            }
        } else {
            System.err.println("❌ ENTITY ERROR: " + operation + " - Entity ID: " + entityId);
            System.err.println("Error: " + error.getMessage());
        }
        
        // Always save to database
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType(operation);
        log.setOperation("ENTITY_ERROR");
        log.setStatus("FAILED");
        log.setErrorMessage("Entity " + entityId + ": " + error.getMessage());
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        
        syncLogRepository.save(log);
    }

    /**
     * Log conflict detection
     */
    public void logConflictDetection(int conflictCount) {
        if (isDebugMode()) {
            System.out.println("🔍 CONFLICT DETECTION: Found " + conflictCount + " conflicts (DEBUG MODE)");
        } else {
            System.out.println("🔍 CONFLICT DETECTION: Found " + conflictCount + " conflicts");
        }
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType("CONFLICT_DETECTION");
        log.setOperation("DETECT");
        log.setStatus("COMPLETED");
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("conflictCount", conflictCount);
        payload.put("detectedAt", LocalDateTime.now());
        log.setPayloadReceived(payload);
        
        syncLogRepository.save(log);
    }

    /**
     * Log conflict resolution with debug mode support
     */
    public void logConflictResolution(SyncConflict conflict, ConflictResolution resolution) {
        if (isDebugMode()) {
            // Count resolutions and collect samples
            String key = "CONFLICT_RESOLUTIONS";
            AtomicInteger counter = operationCounters.get().computeIfAbsent(key, k -> new AtomicInteger(0));
            counter.incrementAndGet();
            
            List<String> samples = operationSamples.get().computeIfAbsent(key, k -> new ArrayList<>());
            if (samples.size() < debugSampleSize) {
                String status = resolution.isResolved() ? "RESOLVED" : "UNRESOLVED";
                samples.add(conflict.getEntityType() + " " + conflict.getEntityId() + ": " + status + " (" + resolution.getStrategy() + ")");
            }
        } else {
            String status = resolution.isResolved() ? "✅ RESOLVED" : "⚠️ UNRESOLVED";
            System.out.println(status + " CONFLICT: " + conflict.getEntityType() + " " + conflict.getEntityId());
            System.out.println("Strategy: " + resolution.getStrategy());
            System.out.println("Reason: " + resolution.getReason());
        }
        
        // Always save to database
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType("CONFLICT_RESOLUTION");
        log.setOperation("RESOLVE");
        log.setStatus(resolution.isResolved() ? "COMPLETED" : "FAILED");
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("entityType", conflict.getEntityType());
        payload.put("entityId", conflict.getEntityId());
        payload.put("conflictType", conflict.getConflictType());
        payload.put("strategy", resolution.getStrategy());
        payload.put("resolved", resolution.isResolved());
        payload.put("reason", resolution.getReason());
        log.setPayloadReceived(payload);
        
        syncLogRepository.save(log);
    }

    /**
     * Log conflict error with debug mode support
     */
    public void logConflictError(SyncConflict conflict, Exception error) {
        if (isDebugMode()) {
            // Count conflict errors and collect samples
            String key = "CONFLICT_ERRORS";
            AtomicInteger counter = operationCounters.get().computeIfAbsent(key, k -> new AtomicInteger(0));
            counter.incrementAndGet();
            
            List<String> samples = operationSamples.get().computeIfAbsent(key, k -> new ArrayList<>());
            if (samples.size() < debugSampleSize) {
                samples.add(conflict.getEntityType() + " " + conflict.getEntityId() + ": " + error.getMessage());
            }
        } else {
            System.err.println("❌ CONFLICT RESOLUTION ERROR: " + conflict.getEntityType() + " " + conflict.getEntityId());
            System.err.println("Error: " + error.getMessage());
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

    /**
     * Print debug summary at the end of operations
     */
    private void printDebugSummary() {
        if (!isDebugMode()) return;
        
        System.out.println("\n📊 DEBUG SUMMARY:");
        
        Map<String, AtomicInteger> counters = operationCounters.get();
        Map<String, List<String>> samples = operationSamples.get();
        
        for (Map.Entry<String, AtomicInteger> entry : counters.entrySet()) {
            String operation = entry.getKey();
            int count = entry.getValue().get();
            List<String> operationSamples = samples.get(operation);
            
            System.out.println("  " + operation + ": " + count + " total");
            
            if (operationSamples != null && !operationSamples.isEmpty()) {
                System.out.println("    Sample entries:");
                for (String sample : operationSamples) {
                    System.out.println("      - " + sample);
                }
            }
        }
        System.out.println();
    }

    // ===== EXISTING METHODS (simplified for debug mode) =====

    public void logBatchOperation(String operation, int totalCount, int successCount, int errorCount) {
        if (isDebugMode()) {
            System.out.println("📊 BATCH " + operation + ": " + totalCount + " total, " + successCount + " success, " + errorCount + " errors");
        } else {
            System.out.println("📊 BATCH " + operation + ":");
            System.out.println("  Total: " + totalCount);
            System.out.println("  Success: " + successCount);
            System.out.println("  Errors: " + errorCount);
            System.out.println("  Success Rate: " + (totalCount > 0 ? (successCount * 100 / totalCount) : 0) + "%");
        }
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType("BATCH_" + operation);
        log.setOperation("BATCH");
        log.setStatus(errorCount == 0 ? "COMPLETED" : "PARTIAL");
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("totalCount", totalCount);
        payload.put("successCount", successCount);
        payload.put("errorCount", errorCount);
        payload.put("successRate", totalCount > 0 ? (successCount * 100.0 / totalCount) : 0);
        log.setPayloadReceived(payload);
        
        syncLogRepository.save(log);
    }

    public void logApiCall(String endpoint, String method, boolean success, long duration) {
        if (isDebugMode()) {
            String status = success ? "✅" : "❌";
            System.out.println(status + " API: " + method + " " + endpoint + " (" + duration + "ms)");
        } else {
            String status = success ? "✅" : "❌";
            System.out.println(status + " API CALL: " + method + " " + endpoint + " (" + duration + "ms)");
        }
    }

    public void logWebhookReceived(String webhookType, String payload) {
        System.out.println("🔔 WEBHOOK RECEIVED: " + webhookType);
        if (!isDebugMode()) {
            System.out.println("Payload size: " + (payload != null ? payload.length() : 0) + " characters");
        }
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType("WEBHOOK");
        log.setOperation("RECEIVE");
        log.setStatus("COMPLETED");
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        
        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("webhookType", webhookType);
        payloadMap.put("payloadSize", payload != null ? payload.length() : 0);
        payloadMap.put("receivedAt", LocalDateTime.now());
        log.setPayloadReceived(payloadMap);
        
        syncLogRepository.save(log);
    }

    public void logDataTransformation(String fromFormat, String toFormat, int recordCount, boolean success) {
        String status = success ? "✅" : "❌";
        System.out.println(status + " DATA TRANSFORM: " + fromFormat + " → " + toFormat + " (" + recordCount + " records)");
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType("DATA_TRANSFORM");
        log.setOperation("TRANSFORM");
        log.setStatus(success ? "COMPLETED" : "FAILED");
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("fromFormat", fromFormat);
        payload.put("toFormat", toFormat);
        payload.put("recordCount", recordCount);
        payload.put("success", success);
        log.setPayloadReceived(payload);
        
        syncLogRepository.save(log);
    }

    public void logValidationError(String entityType, Object entityId, String validationError) {
        if (isDebugMode()) {
            // Count validation errors
            String key = "VALIDATION_ERRORS";
            AtomicInteger counter = operationCounters.get().computeIfAbsent(key, k -> new AtomicInteger(0));
            counter.incrementAndGet();
            
            List<String> samples = operationSamples.get().computeIfAbsent(key, k -> new ArrayList<>());
            if (samples.size() < debugSampleSize) {
                samples.add(entityType + " " + entityId + ": " + validationError);
            }
        } else {
            System.err.println("⚠️ VALIDATION ERROR: " + entityType + " " + entityId);
            System.err.println("Error: " + validationError);
        }
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType("VALIDATION");
        log.setOperation("VALIDATE");
        log.setStatus("FAILED");
        log.setErrorMessage(validationError);
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("entityType", entityType);
        payload.put("entityId", entityId);
        payload.put("validationError", validationError);
        log.setPayloadReceived(payload);
        
        syncLogRepository.save(log);
    }

    public void logPerformanceMetrics(String operation, long duration, int recordsProcessed) {
        double recordsPerSecond = recordsProcessed / (duration / 1000.0);
        
        if (isDebugMode()) {
            System.out.println("📈 PERFORMANCE: " + operation + " - " + duration + "ms, " + recordsProcessed + " records, " + String.format("%.2f", recordsPerSecond) + " rec/sec");
        } else {
            System.out.println("📈 PERFORMANCE: " + operation);
            System.out.println("  Duration: " + duration + "ms");
            System.out.println("  Records: " + recordsProcessed);
            System.out.println("  Rate: " + String.format("%.2f", recordsPerSecond) + " records/sec");
        }
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType("PERFORMANCE");
        log.setOperation(operation);
        log.setStatus("COMPLETED");
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("duration", duration);
        payload.put("recordsProcessed", recordsProcessed);
        payload.put("recordsPerSecond", recordsPerSecond);
        log.setPayloadReceived(payload);
        
        syncLogRepository.save(log);
    }

    public SyncStatistics getSyncStatistics(LocalDateTime since) {
        SyncStatistics stats = new SyncStatistics();
        stats.setSince(since);
        stats.setGeneratedAt(LocalDateTime.now());
        
        // You would implement actual database queries here
        stats.setTotalSyncs(0);
        stats.setSuccessfulSyncs(0);
        stats.setFailedSyncs(0);
        stats.setConflictsDetected(0);
        stats.setConflictsResolved(0);
        
        return stats;
    }

    // ===== SYNC STATISTICS CLASS =====
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
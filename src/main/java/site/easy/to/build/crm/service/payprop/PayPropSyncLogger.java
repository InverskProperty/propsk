// PayPropSyncLogger.java - Database Compatible Version
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

    // ===== BASIC SYNC LOGGING =====

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

    public void logSyncComplete(String syncType, boolean success, String summary) {
        if (isDebugMode()) {
            System.out.println((success ? "✅ SYNC COMPLETED: " : "❌ SYNC FAILED: ") + syncType + " (DEBUG MODE)");
            System.out.println("Summary: " + summary);
            printDebugSummary();
        } else {
            System.out.println(success ? "✅ SYNC COMPLETED: " : "❌ SYNC FAILED: " + syncType);
            System.out.println("Summary: " + summary);
            System.out.println("Completed at: " + LocalDateTime.now());
        }
    }

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

    public void logEntityError(String operation, Object entityId, Exception error) {
        if (isDebugMode()) {
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
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType(operation);
        log.setOperation("ENTITY_ERROR");
        log.setStatus("FAILED");
        log.setErrorMessage("Entity " + entityId + ": " + error.getMessage());
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        
        syncLogRepository.save(log);
    }

    // ===== RELATIONSHIP LOGGING METHODS =====

    public void logRelationshipCreated(String relationshipType, String sourceEntityId, String targetEntityId, String details) {
        if (isDebugMode()) {
            String key = "RELATIONSHIPS_CREATED";
            AtomicInteger counter = operationCounters.get().computeIfAbsent(key, k -> new AtomicInteger(0));
            counter.incrementAndGet();
            
            List<String> samples = operationSamples.get().computeIfAbsent(key, k -> new ArrayList<>());
            if (samples.size() < debugSampleSize) {
                samples.add(relationshipType + ": " + sourceEntityId + " -> " + targetEntityId + " (" + details + ")");
            }
        } else {
            System.out.println("🔗 RELATIONSHIP CREATED: " + relationshipType);
            System.out.println("  Source: " + sourceEntityId);
            System.out.println("  Target: " + targetEntityId);
            System.out.println("  Details: " + details);
        }
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType("RELATIONSHIP_CREATION");
        log.setOperation("CREATE_RELATIONSHIP");
        log.setStatus("SUCCESS");
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        
        // Create payload map for relationship data
        Map<String, Object> payload = new HashMap<>();
        payload.put("relationshipType", relationshipType);
        payload.put("sourceEntityId", sourceEntityId);
        payload.put("targetEntityId", targetEntityId);
        payload.put("details", details);
        payload.put("createdAt", LocalDateTime.now());
        
        // FIXED: Use correct method name from your PortfolioSyncLog entity
        try {
            // Try the correct method name from your entity
            log.setPayloadReceived(payload);
        } catch (Exception e) {
            System.err.println("Warning: Could not set relationship payload: " + e.getMessage());
        }
        
        syncLogRepository.save(log);
    }

    public void logRelationshipFixed(String validationType, String sourceEntityId, String targetEntityId, String fixDescription) {
        if (isDebugMode()) {
            String key = "RELATIONSHIPS_FIXED";
            AtomicInteger counter = operationCounters.get().computeIfAbsent(key, k -> new AtomicInteger(0));
            counter.incrementAndGet();
            
            List<String> samples = operationSamples.get().computeIfAbsent(key, k -> new ArrayList<>());
            if (samples.size() < debugSampleSize) {
                samples.add(validationType + ": " + sourceEntityId + " -> " + targetEntityId + " (Fixed: " + fixDescription + ")");
            }
        } else {
            System.out.println("🔧 RELATIONSHIP FIXED: " + validationType);
            System.out.println("  Source: " + sourceEntityId);
            System.out.println("  Target: " + targetEntityId);
            System.out.println("  Fix: " + fixDescription);
        }
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType("RELATIONSHIP_FIX");
        log.setOperation("FIX_RELATIONSHIP");
        log.setStatus("SUCCESS");
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("validationType", validationType);
        payload.put("sourceEntityId", sourceEntityId);
        payload.put("targetEntityId", targetEntityId);
        payload.put("fixDescription", fixDescription);
        payload.put("fixedAt", LocalDateTime.now());
        
        try {
            log.setPayloadReceived(payload);
        } catch (Exception e) {
            System.err.println("Warning: Could not serialize relationship fix payload: " + e.getMessage());
        }
        
        syncLogRepository.save(log);
    }

    public void logRelationshipIssue(String validationType, String sourceEntityId, String targetEntityId, String issueDescription) {
        if (isDebugMode()) {
            String key = "RELATIONSHIP_ISSUES";
            AtomicInteger counter = operationCounters.get().computeIfAbsent(key, k -> new AtomicInteger(0));
            counter.incrementAndGet();
            
            List<String> samples = operationSamples.get().computeIfAbsent(key, k -> new ArrayList<>());
            if (samples.size() < debugSampleSize) {
                samples.add(validationType + ": " + sourceEntityId + " -> " + targetEntityId + " (Issue: " + issueDescription + ")");
            }
        } else {
            System.err.println("⚠️ RELATIONSHIP ISSUE: " + validationType);
            System.err.println("  Source: " + sourceEntityId);
            System.err.println("  Target: " + targetEntityId);
            System.err.println("  Issue: " + issueDescription);
        }
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType("RELATIONSHIP_VALIDATION");
        log.setOperation("VALIDATION_ISSUE");
        log.setStatus("WARNING");
        log.setErrorMessage(issueDescription);
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("validationType", validationType);
        payload.put("sourceEntityId", sourceEntityId);
        payload.put("targetEntityId", targetEntityId);
        payload.put("issueDescription", issueDescription);
        payload.put("detectedAt", LocalDateTime.now());
        
        try {
            log.setPayloadReceived(payload);
        } catch (Exception e) {
            System.err.println("Warning: Could not serialize relationship issue payload: " + e.getMessage());
        }
        
        syncLogRepository.save(log);
    }

    public void logRelationshipStatistics(String operationType, int totalProcessed, int relationshipsCreated, int relationshipsFixed, int issues) {
        if (isDebugMode()) {
            System.out.println("📊 RELATIONSHIP STATS: " + operationType + " - Processed: " + totalProcessed + 
                             ", Created: " + relationshipsCreated + ", Fixed: " + relationshipsFixed + ", Issues: " + issues);
        } else {
            System.out.println("📊 RELATIONSHIP STATISTICS: " + operationType);
            System.out.println("  Total Processed: " + totalProcessed);
            System.out.println("  Relationships Created: " + relationshipsCreated);
            System.out.println("  Relationships Fixed: " + relationshipsFixed);
            System.out.println("  Issues Found: " + issues);
            System.out.println("  Success Rate: " + (totalProcessed > 0 ? ((relationshipsCreated + relationshipsFixed) * 100.0 / totalProcessed) : 0) + "%");
        }
        
        PortfolioSyncLog log = new PortfolioSyncLog();
        log.setSyncType("RELATIONSHIP_STATISTICS");
        log.setOperation("STATISTICS");
        log.setStatus("COMPLETED");
        log.setSyncStartedAt(LocalDateTime.now());
        log.setSyncCompletedAt(LocalDateTime.now());
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("operationType", operationType);
        payload.put("totalProcessed", totalProcessed);
        payload.put("relationshipsCreated", relationshipsCreated);
        payload.put("relationshipsFixed", relationshipsFixed);
        payload.put("issues", issues);
        payload.put("successRate", totalProcessed > 0 ? ((relationshipsCreated + relationshipsFixed) * 100.0 / totalProcessed) : 0);
        payload.put("generatedAt", LocalDateTime.now());
        
        try {
            log.setPayloadReceived(payload);
        } catch (Exception e) {
            System.err.println("Warning: Could not serialize relationship statistics payload: " + e.getMessage());
        }
        
        syncLogRepository.save(log);
    }

    // ===== CONFLICT LOGGING =====

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
        
        try {
            log.setPayloadReceived(payload);
        } catch (Exception e) {
            System.err.println("Warning: Could not serialize conflict detection payload: " + e.getMessage());
        }
        
        syncLogRepository.save(log);
    }

    public void logConflictResolution(SyncConflict conflict, ConflictResolution resolution) {
        if (isDebugMode()) {
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
        
        try {
            log.setPayloadReceived(payload);
        } catch (Exception e) {
            System.err.println("Warning: Could not serialize conflict resolution payload: " + e.getMessage());
        }
        
        syncLogRepository.save(log);
    }

    public void logConflictError(SyncConflict conflict, Exception error) {
        if (isDebugMode()) {
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

    // ===== OTHER EXISTING METHODS =====

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
        
        try {
            log.setPayloadReceived(payload);
        } catch (Exception e) {
            System.err.println("Warning: Could not serialize batch operation payload: " + e.getMessage());
        }
        
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
        
        try {
            log.setPayloadReceived(payloadMap);
        } catch (Exception e) {
            System.err.println("Warning: Could not serialize webhook payload: " + e.getMessage());
        }
        
        syncLogRepository.save(log);
    }

    public void logValidationError(String entityType, Object entityId, String validationError) {
        if (isDebugMode()) {
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
        
        try {
            log.setPayloadReceived(payload);
        } catch (Exception e) {
            System.err.println("Warning: Could not serialize validation error payload: " + e.getMessage());
        }
        
        syncLogRepository.save(log);
    }

    // ===== UTILITY METHODS =====

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

    /**
     * Get sync statistics from the database
     */
    public SyncStatistics getSyncStatistics(LocalDateTime since) {
        SyncStatistics stats = new SyncStatistics();
        stats.setSince(since);
        stats.setGeneratedAt(LocalDateTime.now());
        
        try {
            // Get basic counts from sync log repository
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
            
            // Count conflicts
            long conflictsDetected = logsInPeriod.stream()
                .filter(log -> "CONFLICT_DETECTION".equals(log.getSyncType()) || "CONFLICT_RESOLUTION".equals(log.getSyncType()))
                .count();
            stats.setConflictsDetected((int) conflictsDetected);
            
            long conflictsResolved = logsInPeriod.stream()
                .filter(log -> "CONFLICT_RESOLUTION".equals(log.getSyncType()) && "COMPLETED".equals(log.getStatus()))
                .count();
            stats.setConflictsResolved((int) conflictsResolved);
            
            // Calculate average duration
            double avgDuration = logsInPeriod.stream()
                .filter(log -> log.getSyncStartedAt() != null && log.getSyncCompletedAt() != null)
                .mapToLong(log -> java.time.Duration.between(log.getSyncStartedAt(), log.getSyncCompletedAt()).toMillis())
                .average()
                .orElse(0.0);
            stats.setAverageSyncDuration(avgDuration);
            
            // Create sync type breakdown
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
            // Return empty stats if calculation fails
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

    /**
     * Simple JSON conversion for payload storage
     * Note: Your database uses TEXT fields, not native JSON
     */
    private String convertMapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value == null) {
                json.append("null");
            } else if (value instanceof String) {
                json.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
            } else if (value instanceof LocalDateTime) {
                json.append("\"").append(value.toString()).append("\"");
            } else {
                json.append(value.toString());
            }
            
            first = false;
        }
        json.append("}");
        
        return json.toString();
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
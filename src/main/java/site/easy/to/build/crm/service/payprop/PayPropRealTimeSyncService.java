package site.easy.to.build.crm.service.payprop;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.config.TicketSyncConfig;
import site.easy.to.build.crm.entity.Ticket;
import site.easy.to.build.crm.service.ticket.TicketService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * PayProp Real-Time Sync Service
 * Handles immediate push of critical ticket updates to PayProp
 */
@Service
@ConditionalOnProperty(name = "payprop.sync.realtime.enabled", havingValue = "true")
public class PayPropRealTimeSyncService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRealTimeSyncService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private TicketService ticketService;
    
    @Autowired
    @Qualifier("payPropTicketRateLimiter")
    private RateLimiter rateLimiter;
    
    @Autowired
    @Qualifier("payPropCircuitBreaker")
    private TicketSyncConfig.PayPropCircuitBreaker circuitBreaker;
    
    @Autowired
    @Qualifier("ticketSyncStatusTracker")
    private TicketSyncConfig.TicketSyncStatusTracker statusTracker;
    
    @Value("${payprop.sync.realtime.critical-statuses:resolved,in-progress,emergency}")
    private String[] criticalStatuses;
    
    /**
     * Push ticket update to PayProp immediately (async)
     */
    @Async("ticketSyncExecutor")
    public CompletableFuture<Boolean> pushUpdateAsync(Ticket ticket) {
        String ticketId = String.valueOf(ticket.getTicketId());
        
        // Check if sync is already in progress
        if (statusTracker.isSyncInProgress(ticketId)) {
            log.debug("⏭️ Sync already in progress for ticket {}", ticketId);
            return CompletableFuture.completedFuture(false);
        }
        
        statusTracker.markSyncInProgress(ticketId, "REALTIME");
        
        try {
            // Circuit breaker check
            if (circuitBreaker.isCircuitOpen()) {
                log.warn("🚨 Circuit breaker is OPEN, queuing ticket {} for batch sync", ticketId);
                fallbackToBatchSync(ticket);
                statusTracker.markSyncFailed(ticketId, "REALTIME", "Circuit breaker open");
                return CompletableFuture.completedFuture(false);
            }
            
            // Rate limiting
            rateLimiter.acquire();
            
            // Validate ticket can be synced
            if (!canSyncTicket(ticket)) {
                log.debug("⏭️ Ticket {} cannot be synced to PayProp", ticketId);
                statusTracker.markSyncSuccess(ticketId, "REALTIME");
                return CompletableFuture.completedFuture(false);
            }
            
            // Build minimal payload for speed
            Map<String, Object> payload = buildMinimalPayload(ticket);
            
            // Make API call
            String endpoint = "/maintenance/tickets/" + ticket.getPayPropTicketId();
            apiClient.put(endpoint, payload);
            
            // Update ticket sync status
            ticket.setPayPropSynced(true);
            ticket.setPayPropLastSync(LocalDateTime.now());
            ticketService.save(ticket);
            
            // Record success
            circuitBreaker.recordSuccess();
            statusTracker.markSyncSuccess(ticketId, "REALTIME");
            
            log.info("⚡ REAL-TIME: Updated PayProp ticket {} with status: {}", 
                ticket.getPayPropTicketId(), ticket.getStatus());
            
            return CompletableFuture.completedFuture(true);
            
        } catch (Exception e) {
            // Record failure
            circuitBreaker.recordFailure();
            statusTracker.markSyncFailed(ticketId, "REALTIME", e.getMessage());
            
            log.warn("⚡ REAL-TIME FAILED: Ticket {} - {}", ticketId, e.getMessage());
            
            // Fallback to batch sync
            fallbackToBatchSync(ticket);
            
            return CompletableFuture.completedFuture(false);
        }
    }
    
    /**
     * Check if ticket should be pushed immediately
     */
    public boolean shouldPushImmediately(Ticket ticket) {
        // Must have PayProp ticket ID
        if (ticket.getPayPropTicketId() == null) {
            return false;
        }
        
        // Must be a critical status change
        if (!isCriticalUpdate(ticket)) {
            return false;
        }
        
        // Circuit breaker check
        if (circuitBreaker.isCircuitOpen()) {
            return false;
        }
        
        // Don't sync if already in progress
        if (statusTracker.isSyncInProgress(String.valueOf(ticket.getTicketId()))) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if this is a critical update that needs immediate sync
     */
    private boolean isCriticalUpdate(Ticket ticket) {
        String status = ticket.getStatus();
        String urgency = ticket.getUrgencyLevel();
        
        // Critical status changes
        if (status != null && Arrays.asList(criticalStatuses).contains(status.toLowerCase())) {
            return true;
        }
        
        // Emergency urgency always critical
        if ("emergency".equals(urgency)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if ticket can be synced to PayProp
     */
    private boolean canSyncTicket(Ticket ticket) {
        return ticket.getPayPropTicketId() != null && 
               !ticket.getPayPropTicketId().trim().isEmpty();
    }
    
    /**
     * Build minimal payload for fast updates
     */
    private Map<String, Object> buildMinimalPayload(Ticket ticket) {
        Map<String, Object> payload = new HashMap<>();
        
        // Only include essential fields for speed
        payload.put("status", mapCrmStatusToPayPropStatus(ticket.getStatus()));
        payload.put("is_emergency", "emergency".equals(ticket.getUrgencyLevel()));
        
        // Add last modified timestamp
        payload.put("last_modified", LocalDateTime.now().toString());
        
        return payload;
    }
    
    /**
     * Map CRM status to PayProp status
     */
    private String mapCrmStatusToPayPropStatus(String crmStatus) {
        if (crmStatus == null) return "new";
        
        switch (crmStatus.toLowerCase()) {
            case "open": return "new";
            case "in-progress": return "in_progress";
            case "on-hold": return "on_hold";
            case "resolved": return "resolved";
            case "closed": return "rejected";
            default: return "new";
        }
    }
    
    /**
     * Fallback to batch sync when real-time fails
     */
    private void fallbackToBatchSync(Ticket ticket) {
        try {
            ticket.setPayPropSynced(false);
            ticketService.save(ticket);
            log.info("🔄 Ticket {} queued for batch sync fallback", ticket.getTicketId());
        } catch (Exception e) {
            log.error("❌ Failed to queue ticket {} for batch sync: {}", ticket.getTicketId(), e.getMessage());
        }
    }
    
    /**
     * Get sync statistics
     */
    public Map<String, Object> getSyncStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("circuit_breaker_open", circuitBreaker.isCircuitOpen());
        stats.put("consecutive_failures", circuitBreaker.getConsecutiveFailures());
        stats.put("active_sync_operations", statusTracker.getActiveStatusCount());
        stats.put("rate_limit_permits_per_second", rateLimiter.getRate());
        
        return stats;
    }
    
    /**
     * Health check for real-time sync
     */
    public boolean isHealthy() {
        return !circuitBreaker.isCircuitOpen() && 
               statusTracker.getActiveStatusCount() < 100; // Reasonable limit
    }
    
    /**
     * Clear old sync statuses (maintenance)
     */
    public void performMaintenance() {
        statusTracker.clearOldStatuses();
        log.debug("🧹 Cleared old sync statuses, active count: {}", statusTracker.getActiveStatusCount());
    }
}
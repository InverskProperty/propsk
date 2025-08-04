package site.easy.to.build.crm.config;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ticket Sync Configuration
 * Provides async infrastructure and rate limiting for PayProp ticket synchronization
 */
@Configuration
@EnableAsync
@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
public class TicketSyncConfig {
    
    private static final Logger log = LoggerFactory.getLogger(TicketSyncConfig.class);
    
    @Value("${payprop.sync.realtime.enabled:false}")
    private boolean realtimeEnabled;
    
    @Value("${payprop.sync.realtime.rate-limit:2.0}")
    private double rateLimitPerSecond;
    
    @Value("${payprop.sync.realtime.thread-pool.core:2}")
    private int corePoolSize;
    
    @Value("${payprop.sync.realtime.thread-pool.max:5}")
    private int maxPoolSize;
    
    @Value("${payprop.sync.realtime.thread-pool.queue:50}")
    private int queueCapacity;
    
    /**
     * Dedicated thread pool for ticket sync operations
     */
    @Bean("ticketSyncExecutor")
    public TaskExecutor ticketSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("TicketSync-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        
        log.info("🔧 Ticket sync executor configured: core={}, max={}, queue={}", 
            corePoolSize, maxPoolSize, queueCapacity);
        
        return executor;
    }
    
    /**
     * Rate limiter for PayProp API calls
     */
    @Bean("payPropTicketRateLimiter")
    public RateLimiter payPropTicketRateLimiter() {
        RateLimiter limiter = RateLimiter.create(rateLimitPerSecond);
        log.info("🚦 PayProp ticket rate limiter configured: {} requests/second", rateLimitPerSecond);
        return limiter;
    }
    
    /**
     * Circuit breaker for PayProp health monitoring
     */
    @Bean("payPropCircuitBreaker")
    public PayPropCircuitBreaker payPropCircuitBreaker() {
        return new PayPropCircuitBreaker();
    }
    
    /**
     * Simple circuit breaker implementation
     */
    public static class PayPropCircuitBreaker {
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private final AtomicBoolean circuitOpen = new AtomicBoolean(false);
        private volatile long lastFailureTime = 0;
        
        private static final int FAILURE_THRESHOLD = 5;
        private static final long RECOVERY_TIMEOUT_MS = 60000; // 1 minute
        
        public boolean isCircuitOpen() {
            if (circuitOpen.get()) {
                // Check if recovery timeout has passed
                if (System.currentTimeMillis() - lastFailureTime > RECOVERY_TIMEOUT_MS) {
                    log.info("🔄 Circuit breaker recovery timeout reached, attempting to close circuit");
                    circuitOpen.set(false);
                    consecutiveFailures.set(0);
                    return false;
                }
                return true;
            }
            return false;
        }
        
        public void recordSuccess() {
            consecutiveFailures.set(0);
            circuitOpen.set(false);
        }
        
        public void recordFailure() {
            int failures = consecutiveFailures.incrementAndGet();
            lastFailureTime = System.currentTimeMillis();
            
            if (failures >= FAILURE_THRESHOLD && !circuitOpen.get()) {
                log.warn("🚨 Circuit breaker OPENED after {} consecutive failures", failures);
                circuitOpen.set(true);
            }
        }
        
        public int getConsecutiveFailures() {
            return consecutiveFailures.get();
        }
    }
    
    /**
     * Sync status tracking for tickets
     */
    @Bean("ticketSyncStatusTracker")
    public TicketSyncStatusTracker ticketSyncStatusTracker() {
        return new TicketSyncStatusTracker();
    }
    
    /**
     * In-memory sync status tracking
     */
    public static class TicketSyncStatusTracker {
        private final ConcurrentHashMap<String, SyncStatus> syncStatuses = new ConcurrentHashMap<>();
        
        public void markSyncInProgress(String ticketId, String method) {
            syncStatuses.put(ticketId, new SyncStatus(SyncState.IN_PROGRESS, method, System.currentTimeMillis()));
        }
        
        public void markSyncSuccess(String ticketId, String method) {
            syncStatuses.put(ticketId, new SyncStatus(SyncState.SUCCESS, method, System.currentTimeMillis()));
        }
        
        public void markSyncFailed(String ticketId, String method, String error) {
            syncStatuses.put(ticketId, new SyncStatus(SyncState.FAILED, method, System.currentTimeMillis(), error));
        }
        
        public SyncStatus getSyncStatus(String ticketId) {
            return syncStatuses.get(ticketId);
        }
        
        public boolean isSyncInProgress(String ticketId) {
            SyncStatus status = syncStatuses.get(ticketId);
            return status != null && status.state == SyncState.IN_PROGRESS;
        }
        
        public void clearOldStatuses() {
            long cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000); // 24 hours
            syncStatuses.entrySet().removeIf(entry -> entry.getValue().timestamp < cutoff);
        }
        
        public int getActiveStatusCount() {
            return syncStatuses.size();
        }
        
        public static class SyncStatus {
            public final SyncState state;
            public final String method;
            public final long timestamp;
            public final String error;
            
            public SyncStatus(SyncState state, String method, long timestamp) {
                this(state, method, timestamp, null);
            }
            
            public SyncStatus(SyncState state, String method, long timestamp, String error) {
                this.state = state;
                this.method = method;
                this.timestamp = timestamp;
                this.error = error;
            }
        }
        
        public enum SyncState {
            IN_PROGRESS, SUCCESS, FAILED
        }
    }
}
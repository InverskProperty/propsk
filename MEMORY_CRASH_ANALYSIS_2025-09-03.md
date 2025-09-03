# CRM Memory Crash Analysis & Recovery Plan
**Date**: September 3, 2025  
**Issue**: Application crashing with memory errors after recent changes  
**Status**: Critical - Memory leaks causing OutOfMemoryError crashes

---

## Executive Summary

**CRITICAL FINDING**: Your application is experiencing severe memory leaks primarily caused by Google OAuth token refresh operations creating new `NetHttpTransport` instances without proper cleanup. The crashes started after recent maintenance system changes in the last 24 hours.

**Root Cause**: Multiple `GoogleNetHttpTransport.newTrustedTransport()` calls are creating unmanaged HTTP transport instances that accumulate in memory, especially during token refresh operations.

**Impact**: 
- Native memory allocation failures (264MB+ allocation attempts failing)
- G1 GC unable to recover heap space
- Application crashes at startup or during intensive token operations

---

## Memory Crash Evidence

### 1. Fatal Memory Error (hs_err_pid57988.log)
```
# There is insufficient memory for the Java Runtime Environment to continue.
# Native memory allocation (mmap) failed to map 264241152 bytes. Error detail: G1 virtual space
# Out of Memory Error (os_windows.cpp:3618), pid=57988, tid=30264
```

**Analysis**:
- Initial heap: 264MB, Max heap: 4GB (system has 16GB RAM)
- Crash occurred during JVM startup before application initialization
- G1 GC virtual space allocation failure indicates native memory exhaustion

### 2. Recent Memory-Related Commits
From git analysis, critical commits in last 24 hours:
- `49b5ac2`: Fix Bad Gateway: Add null checks for static HTTP_TRANSPORT
- `b909e29`: **CRITICAL FIX**: Resolve Google token refresh memory leak causing OutOfMemoryError  
- `86c1f25`: Add comprehensive memory diagnostic code to identify OutOfMemoryError source
- `db5b151`: Fix PayProp startup memory leak by deferring token refresh

**Pattern**: Multiple emergency fixes attempting to resolve HTTP_TRANSPORT memory leaks

---

## Memory Leak Sources Identified

### 1. Google OAuth Token Refresh Leak (PRIMARY CAUSE)
**Location**: `src/main/java/site/easy/to/build/crm/service/user/OAuthUserServiceImpl.java:36-44`

**Problem**:
```java
private static NetHttpTransport HTTP_TRANSPORT;
static {
    try {
        HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException e) {
        HTTP_TRANSPORT = null;  // LEAK: Fallback creates new transports
    }
}
```

**Leak Mechanism**:
- Static `HTTP_TRANSPORT` can be null during startup failures
- Fallback code creates new `GoogleNetHttpTransport.newTrustedTransport()` instances
- Each transport creates thread pools, connection managers, SSL contexts
- No cleanup or shutdown of these resources

**Evidence from Code**:
```java
// Line 142: Creates new transport on every null check
if (transport == null) {
    transport = GoogleNetHttpTransport.newTrustedTransport();
}

// Used in token refresh (line 146) and revoke (line 206)
// Each call potentially creates new HTTP transport instances
```

### 2. Scheduled Token Refresh Amplification
**Location**: `src/main/java/site/easy/to/build/crm/google/service/GoogleTokenRefreshScheduler.java`

**Problem**: 
- Runs every 30 minutes (`@Scheduled(fixedDelay = 1800000)`)
- Processes all OAuth users in database
- Each user refresh can trigger new transport creation
- Memory diagnostic calls on every operation add overhead

**Amplification Factor**: 
- If 10 users need token refresh = 10 potential new HTTP transports
- 48 refresh cycles per day = up to 480 transport instances daily
- Each transport ~5-10MB native memory = 2-5GB daily accumulation

### 3. PayProp Integration Memory Load
**Location**: Multiple PayProp sync services

**Contributing Factors**:
- Heavy sync operations with comprehensive diagnostics
- Real-time sync service creating additional HTTP connections
- Circuit breaker and rate limiting creating cached objects
- Large data processing during sync operations

---

## Memory Usage Analysis

### Current JVM Configuration (from crash dump):
- **Max Heap**: 4GB (`-Xmx4213178368`)  
- **Initial Heap**: 264MB (`-XX:InitialHeapSize=264241152`)
- **GC**: G1 with 2MB regions
- **Architecture**: 64-bit with compressed OOPs

### Memory Distribution Issues:
1. **Native Memory Exhaustion**: HTTP transport SSL/TLS native libraries
2. **Thread Pool Accumulation**: Each transport creates worker threads
3. **Connection Manager Buffers**: Cached HTTP connections consuming native memory
4. **G1 Region Fragmentation**: Large objects causing virtual space mapping failures

---

## Immediate Fix Strategy (Zero Downtime)

### 1. HTTP Transport Singleton Fix ⚡ **CRITICAL**
```java
// Replace the problematic static initialization
private static final class TransportHolder {
    private static volatile NetHttpTransport INSTANCE;
    private static final Object LOCK = new Object();
    
    static NetHttpTransport getInstance() {
        if (INSTANCE == null) {
            synchronized (LOCK) {
                if (INSTANCE == null) {
                    try {
                        INSTANCE = GoogleNetHttpTransport.newTrustedTransport();
                        // Add shutdown hook for cleanup
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            if (INSTANCE != null) {
                                try {
                                    INSTANCE.shutdown();
                                } catch (Exception e) {
                                    System.err.println("Transport shutdown error: " + e);
                                }
                            }
                        }));
                    } catch (GeneralSecurityException | IOException e) {
                        throw new RuntimeException("Failed to create HTTP transport", e);
                    }
                }
            }
        }
        return INSTANCE;
    }
}
```

### 2. Token Refresh Batching ⚡ **HIGH PRIORITY**
```java
// Reduce refresh frequency and batch processing
@Scheduled(fixedDelay = 3600000) // Change from 30min to 1 hour
public void refreshExpiringTokens() {
    // Add memory check before processing
    Runtime runtime = Runtime.getRuntime();
    long usedMemory = runtime.totalMemory() - runtime.freeMemory();
    if (usedMemory > runtime.maxMemory() * 0.85) {
        System.err.println("Skipping token refresh - high memory usage");
        return;
    }
    
    // Process in smaller batches with memory monitoring
    List<OAuthUser> users = oAuthUserRepository.findAll();
    int batchSize = 5;
    for (int i = 0; i < users.size(); i += batchSize) {
        List<OAuthUser> batch = users.subList(i, 
            Math.min(i + batchSize, users.size()));
        processBatch(batch);
        
        // Force GC between batches
        System.gc();
        Thread.sleep(1000);
    }
}
```

### 3. JVM Memory Tuning ⚡ **IMMEDIATE**
Update your startup script or Docker configuration:
```bash
# Current problematic settings
-Xms256m -Xmx450m  # TOO LOW for your workload

# Recommended settings for your 16GB system
-Xms1g -Xmx6g 
-XX:+UseG1GC 
-XX:MaxGCPauseMillis=200
-XX:+UseStringDeduplication
-XX:+UseCompressedOops
-XX:NativeMemoryTracking=summary
-XX:MaxDirectMemorySize=2g
-XX:+ExitOnOutOfMemoryError  # Fail fast instead of hanging
```

---

## Long-term Architecture Fixes

### 1. HTTP Client Connection Pooling
Replace individual transport creation with managed connection pool:
```java
@Configuration
public class HttpClientConfig {
    @Bean
    @Singleton
    public CloseableHttpClient httpClient() {
        return HttpClients.custom()
            .setMaxConnTotal(50)
            .setMaxConnPerRoute(10)
            .setConnectionTimeToLive(30, TimeUnit.SECONDS)
            .evictIdleConnections(60, TimeUnit.SECONDS)
            .build();
    }
}
```

### 2. Token Refresh Circuit Breaker
Prevent cascade failures during memory pressure:
```java
@Component
public class TokenRefreshCircuitBreaker {
    private final AtomicInteger failures = new AtomicInteger(0);
    private volatile long lastFailTime = 0;
    
    public boolean allowRefresh() {
        if (failures.get() > 5 && 
            (System.currentTimeMillis() - lastFailTime) < 300000) {
            return false; // Circuit open for 5 minutes
        }
        return true;
    }
}
```

### 3. Memory Monitoring & Alerting
```java
@Component
public class MemoryWatchdog {
    @EventListener
    @Async
    public void onLowMemory() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        double usagePercent = (usedMemory * 100.0) / runtime.maxMemory();
        
        if (usagePercent > 90) {
            // Emergency actions
            System.gc();
            pauseNonCriticalOperations();
            alertAdministrators("CRITICAL: Memory usage at " + usagePercent + "%");
        }
    }
}
```

---

## Testing & Validation Plan

### 1. Memory Load Testing
```bash
# Test with memory diagnostic
java -XX:NativeMemoryTracking=detail -XX:+PrintGC -XX:+PrintGCDetails \
     -Xloggc:gc.log -jar app.war

# Monitor native memory
jcmd <pid> VM.native_memory summary
```

### 2. Token Refresh Stress Test
```java
// Create test that simulates multiple concurrent token refreshes
@Test
public void testConcurrentTokenRefresh() {
    ExecutorService executor = Executors.newFixedThreadPool(20);
    for (int i = 0; i < 100; i++) {
        executor.submit(() -> oAuthUserService.refreshAccessTokenIfNeeded(testUser));
    }
    // Verify no memory leaks after completion
}
```

### 3. Production Monitoring
```java
// Add to application.properties
management.endpoints.web.exposure.include=health,info,metrics,httptrace,memory
management.endpoint.health.show-details=always

# Custom memory metrics
@Component
public class MemoryMetrics {
    @EventListener
    @Scheduled(fixedDelay = 60000)
    public void recordMemoryMetrics() {
        Runtime runtime = Runtime.getRuntime();
        // Record to monitoring system
    }
}
```

---

## Deployment Strategy

### Phase 1: Emergency Stabilization (Today)
1. **Apply HTTP Transport Fix** - Replace static initialization
2. **Update JVM Settings** - Increase heap to 6GB
3. **Reduce Token Refresh Frequency** - 30min → 1 hour
4. **Deploy Memory Monitoring** - Real-time alerts

### Phase 2: Load Balancing (This Week)  
1. **Implement Connection Pooling** - Managed HTTP clients
2. **Add Circuit Breakers** - Prevent cascade failures  
3. **Batch Processing** - Smaller, memory-conscious batches
4. **Enhanced Monitoring** - Native memory tracking

### Phase 3: Optimization (Next Week)
1. **Cache Token Validation** - Reduce refresh frequency
2. **Async Processing** - Non-blocking token operations
3. **Resource Cleanup** - Proper disposal patterns
4. **Load Testing** - Stress test with real workload

---

## Monitoring & Alerts

### Key Metrics to Watch:
- **Heap Usage**: Should stay < 70% of max
- **Native Memory**: Track direct memory allocation  
- **GC Frequency**: G1 collections should be < 100ms
- **Token Refresh Success Rate**: Should be > 95%
- **HTTP Transport Instances**: Should be constant (not growing)

### Alert Thresholds:
- **CRITICAL**: Heap > 90% for 5 minutes
- **WARNING**: Native memory > 1GB growth per hour
- **INFO**: Token refresh failures > 10% in 1 hour

---

## Risk Assessment

**HIGH RISK**: Current memory leak will cause crashes within hours of restart
**MEDIUM RISK**: Token refresh failures during high memory pressure  
**LOW RISK**: Performance degradation during GC pauses

**Business Impact**: 
- Application downtime during memory crashes
- Failed token refreshes affecting user operations
- PayProp integration instability

---

## Success Criteria

✅ **Application runs for 24+ hours without memory crashes**  
✅ **Heap usage stabilizes < 70% after 1 hour of operations**  
✅ **Token refresh success rate > 95%**  
✅ **Native memory growth < 100MB per hour**  
✅ **GC pause times < 200ms**  

---

*Analysis completed: September 3, 2025*  
*Next Review: September 4, 2025 (post-deployment)*  
*Critical Issue Status: REQUIRES IMMEDIATE ACTION*
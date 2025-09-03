package site.easy.to.build.crm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import site.easy.to.build.crm.util.MemoryDiagnostics;

@Configuration
public class MemoryMonitoringConfig {

    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        MemoryDiagnostics.logMemoryUsage("Spring Context Refreshed");
    }

    @EventListener
    public void handleApplicationStarted(ApplicationStartedEvent event) {
        MemoryDiagnostics.logMemoryUsage("Spring Application Started");
    }

    @EventListener
    public void handleApplicationReady(ApplicationReadyEvent event) {
        MemoryDiagnostics.forceGCAndLog("Spring Application Ready - Final Memory State");
        
        // Log critical memory thresholds
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        double usagePercent = (usedMemory * 100.0) / maxMemory;
        
        System.out.println("🎯 MEMORY USAGE SUMMARY:");
        System.out.printf("   Max Memory Available: %.2f MB%n", maxMemory / 1024.0 / 1024.0);
        System.out.printf("   Current Usage: %.2f MB (%.1f%%)%n", 
                         usedMemory / 1024.0 / 1024.0, usagePercent);
        System.out.printf("   Memory Remaining: %.2f MB%n", 
                         (maxMemory - usedMemory) / 1024.0 / 1024.0);
        
        if (usagePercent > 85) {
            System.err.println("⚠️  MEMORY WARNING: Usage over 85% - risk of OutOfMemoryError!");
        } else if (usagePercent > 75) {
            System.out.println("⚡ Memory usage is high (>75%) but within acceptable range");
        } else {
            System.out.println("✅ Memory usage is healthy (<75%)");
        }
    }
}
package site.easy.to.build.crm.util;

import org.springframework.stereotype.Component;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.GarbageCollectorMXBean;

@Component
public class MemoryDiagnostics {
    
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private static final Runtime runtime = Runtime.getRuntime();
    
    public static void logMemoryUsage(String operation) {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        System.out.println("🔍 MEMORY DIAGNOSTIC - " + operation);
        System.out.println("   📊 Runtime Memory:");
        System.out.printf("      Used: %.2f MB / Max: %.2f MB (%.1f%%)%n", 
                         usedMemory / 1024.0 / 1024.0, 
                         maxMemory / 1024.0 / 1024.0,
                         (usedMemory * 100.0) / maxMemory);
        
        System.out.println("   🏗️  Heap Memory:");
        System.out.printf("      Used: %.2f MB / Committed: %.2f MB / Max: %.2f MB%n",
                         heapUsage.getUsed() / 1024.0 / 1024.0,
                         heapUsage.getCommitted() / 1024.0 / 1024.0,
                         heapUsage.getMax() / 1024.0 / 1024.0);
        
        System.out.println("   📚 Non-Heap Memory:");
        System.out.printf("      Used: %.2f MB / Committed: %.2f MB%n",
                         nonHeapUsage.getUsed() / 1024.0 / 1024.0,
                         nonHeapUsage.getCommitted() / 1024.0 / 1024.0);
        
        System.out.printf("   ⚠️  Memory Alert: %s%n", 
                         (usedMemory * 100.0) / maxMemory > 90 ? "CRITICAL - Over 90%" : "OK");
        System.out.println();
    }
    
    public static void logGCActivity() {
        System.out.println("🗑️  GARBAGE COLLECTION ACTIVITY:");
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.printf("   %s: %d collections, %d ms total time%n", 
                             gc.getName(), gc.getCollectionCount(), gc.getCollectionTime());
        }
        System.out.println();
    }
    
    public static void forceGCAndLog(String beforeOperation) {
        System.out.println("🔄 FORCING GARBAGE COLLECTION BEFORE: " + beforeOperation);
        logMemoryUsage("Before GC - " + beforeOperation);
        System.gc();
        try {
            Thread.sleep(100); // Give GC time to run
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logMemoryUsage("After GC - " + beforeOperation);
        logGCActivity();
    }
}
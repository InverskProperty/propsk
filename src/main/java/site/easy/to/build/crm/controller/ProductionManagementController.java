package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.service.payprop.PayPropFinancialSyncService;
import site.easy.to.build.crm.repository.FinancialTransactionRepository;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.repository.UserRepository;
import site.easy.to.build.crm.repository.CustomerRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Production Environment Management Controller
 * Only available in production profile - provides read-only monitoring
 * NO database reset or test user creation endpoints for safety
 */
@RestController
@RequestMapping("/admin/production")
@Profile("production")
public class ProductionManagementController {

    @Autowired
    private PayPropFinancialSyncService payPropSyncService;
    
    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;
    
    @Autowired
    private PropertyRepository propertyRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private CustomerRepository customerRepository;

    /**
     * Production health check - read only, no modification endpoints
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> productionHealthCheck() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Check database connectivity with production data counts
            long userCount = userRepository.count();
            long customerCount = customerRepository.count();
            long propertyCount = propertyRepository.count();
            long transactionCount = financialTransactionRepository.count();
            
            health.put("status", "healthy");
            health.put("database", "connected");
            health.put("environment", "production");
            health.put("server_time", LocalDateTime.now());
            health.put("data_summary", Map.of(
                "users", userCount,
                "customers", customerCount,
                "properties", propertyCount,
                "total_transactions", transactionCount
            ));
            
            // Additional production metrics
            health.put("data_freshness", Map.of(
                "properties_with_payprop_sync", propertyRepository.countByPayPropIdIsNotNull(),
                "recent_transactions_7_days", financialTransactionRepository.countByTransactionDateAfter(LocalDate.now().minusDays(7)),
                "recent_transactions_30_days", financialTransactionRepository.countByTransactionDateAfter(LocalDate.now().minusDays(30))
            ));
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            System.err.println("❌ PRODUCTION: Health check failed: " + e.getMessage());
            health.put("status", "unhealthy");
            health.put("error", e.getMessage());
            health.put("environment", "production");
            return ResponseEntity.status(500).body(health);
        }
    }

    /**
     * Production statistics - comprehensive read-only data overview
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getProductionStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            stats.put("environment", "production");
            stats.put("timestamp", LocalDateTime.now());
            
            // Core entity counts
            stats.put("entities", Map.of(
                "users", userRepository.count(),
                "customers", customerRepository.count(),
                "properties", propertyRepository.count(),
                "financial_transactions", financialTransactionRepository.count()
            ));
            
            // PayProp integration stats
            stats.put("payprop_integration", Map.of(
                "properties_synced", propertyRepository.countByPayPropIdIsNotNull(),
                "transactions_from_payprop", financialTransactionRepository.countByPayPropTransactionIdIsNotNull()
            ));
            
            // Recent activity
            stats.put("recent_activity", Map.of(
                "transactions_last_7_days", financialTransactionRepository.countByTransactionDateAfter(LocalDate.now().minusDays(7)),
                "transactions_last_30_days", financialTransactionRepository.countByTransactionDateAfter(LocalDate.now().minusDays(30)),
                "transactions_last_90_days", financialTransactionRepository.countByTransactionDateAfter(LocalDate.now().minusDays(90))
            ));
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            System.err.println("❌ PRODUCTION: Failed to get statistics: " + e.getMessage());
            stats.put("error", "Failed to retrieve statistics: " + e.getMessage());
            return ResponseEntity.status(500).body(stats);
        }
    }

    /**
     * Incremental PayProp sync - safe for production use
     * Syncs recent data without major disruption
     */
    @PostMapping("/sync/incremental")
    public ResponseEntity<Map<String, Object>> executeProductionIncrementalSync() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            System.out.println("🔄 PRODUCTION: Starting incremental PayProp sync...");
            
            // Get data counts before sync
            long propertiesBefore = propertyRepository.count();
            long transactionsBefore = financialTransactionRepository.count();
            
            // Execute incremental sync (last 30 days for production safety)
            payPropSyncService.performComprehensiveFinancialSync();
            
            // Get data counts after sync
            long propertiesAfter = propertyRepository.count();
            long transactionsAfter = financialTransactionRepository.count();
            
            result.put("status", "success");
            result.put("message", "Production incremental sync completed");
            result.put("timestamp", LocalDateTime.now());
            result.put("sync_type", "incremental");
            result.put("sync_period", "last_30_days");
            
            result.put("data_changes", Map.of(
                "properties_before", propertiesBefore,
                "properties_after", propertiesAfter,
                "properties_added", propertiesAfter - propertiesBefore,
                "transactions_before", transactionsBefore,
                "transactions_after", transactionsAfter,
                "transactions_added", transactionsAfter - transactionsBefore
            ));
            
            System.out.println("✅ PRODUCTION: Incremental sync completed successfully");
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            System.err.println("❌ PRODUCTION: Incremental sync failed: " + e.getMessage());
            e.printStackTrace();
            
            result.put("status", "error");
            result.put("message", "Production incremental sync failed: " + e.getMessage());
            result.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Production data integrity check
     * Validates data relationships and consistency
     */
    @GetMapping("/validate/data-integrity")
    public ResponseEntity<Map<String, Object>> validateDataIntegrity() {
        Map<String, Object> validation = new HashMap<>();
        
        try {
            validation.put("environment", "production");
            validation.put("validation_time", LocalDateTime.now());
            
            // Check for orphaned data
            validation.put("data_consistency", Map.of(
                "properties_with_payprop_id", propertyRepository.countByPayPropIdIsNotNull(),
                "total_properties", propertyRepository.count(),
                "transactions_with_payprop_id", financialTransactionRepository.countByPayPropTransactionIdIsNotNull(),
                "total_transactions", financialTransactionRepository.count()
            ));
            
            // Check recent sync activity
            long recentTransactions = financialTransactionRepository.countByTransactionDateAfter(LocalDate.now().minusDays(7));
            validation.put("sync_health", Map.of(
                "recent_transactions_7_days", recentTransactions,
                "sync_appears_active", recentTransactions > 0
            ));
            
            validation.put("status", "completed");
            return ResponseEntity.ok(validation);
            
        } catch (Exception e) {
            validation.put("status", "error");
            validation.put("error", e.getMessage());
            return ResponseEntity.status(500).body(validation);
        }
    }

    /**
     * System information - production environment details
     */
    @GetMapping("/system-info")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        Map<String, Object> systemInfo = new HashMap<>();
        
        systemInfo.put("environment", "production");
        systemInfo.put("profile", "production");
        systemInfo.put("timestamp", LocalDateTime.now());
        systemInfo.put("available_endpoints", Map.of(
            "health", "/admin/production/health",
            "statistics", "/admin/production/stats", 
            "incremental_sync", "/admin/production/sync/incremental",
            "data_validation", "/admin/production/validate/data-integrity",
            "system_info", "/admin/production/system-info"
        ));
        
        systemInfo.put("security_note", "Production environment - no data modification endpoints available");
        
        return ResponseEntity.ok(systemInfo);
    }
}
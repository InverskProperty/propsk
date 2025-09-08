package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.service.payprop.PayPropFinancialSyncService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.repository.FinancialTransactionRepository;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.repository.UserRepository;
import site.easy.to.build.crm.repository.CustomerRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * Staging Environment Data Management Controller
 * Only available in staging profile for development purposes
 */
@RestController
@RequestMapping("/admin/staging")
@Profile("staging")
public class StagingDataController {

    @Autowired
    private PayPropFinancialSyncService payPropSyncService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private CustomerService customerService;
    
    @Autowired
    private PropertyService propertyService;
    
    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;
    
    @Autowired
    private PropertyRepository propertyRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private CustomerRepository customerRepository;

    /**
     * Execute full PayProp data sync for staging environment
     */
    @PostMapping("/sync/full")
    public ResponseEntity<Map<String, Object>> executeFullSync() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            System.out.println("🚀 STAGING: Starting full PayProp data sync...");
            
            // Execute comprehensive financial sync
            payPropSyncService.performComprehensiveFinancialSync();
            
            result.put("status", "success");
            result.put("message", "Full PayProp sync completed successfully");
            result.put("timestamp", LocalDateTime.now());
            
            // Add sync statistics
            result.put("statistics", getSyncStatistics());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            System.err.println("❌ STAGING: Full sync failed: " + e.getMessage());
            e.printStackTrace();
            
            result.put("status", "error");
            result.put("message", "Full sync failed: " + e.getMessage());
            result.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Get current data statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDataStatistics() {
        Map<String, Object> stats = getSyncStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Create test users for development
     */
    @PostMapping("/users/create-test-users")
    public ResponseEntity<Map<String, Object>> createTestUsers() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Create test admin user
            User adminUser = new User();
            adminUser.setEmail("admin@staging.local");
            adminUser.setUsername("admin");
            adminUser.setStatus("ACTIVE");
            adminUser.setCreatedAt(LocalDateTime.now());
            
            // Create test property owner customer
            Customer propertyOwner = new Customer();
            propertyOwner.setEmail("owner@staging.local");
            propertyOwner.setName("Test Property Owner");
            propertyOwner.setCustomerType(CustomerType.PROPERTY_OWNER);
            propertyOwner.setIsPropertyOwner(true);
            propertyOwner.setCreatedDate(LocalDate.now());
            
            // Create test tenant customer  
            Customer tenant = new Customer();
            tenant.setEmail("tenant@staging.local");
            tenant.setName("Test Tenant");
            tenant.setCustomerType(CustomerType.TENANT);
            tenant.setIsTenant(true);
            tenant.setCreatedDate(LocalDate.now());
            
            // Save test users
            userService.save(adminUser);
            customerService.save(propertyOwner);
            customerService.save(tenant);
            
            result.put("status", "success");
            result.put("message", "Test users created successfully");
            result.put("users", Map.of(
                "admin", adminUser.getId(),
                "property_owner", propertyOwner.getCustomerId(),
                "tenant", tenant.getCustomerId()
            ));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Failed to create test users: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Reset database (STAGING ONLY)
     */
    @PostMapping("/database/reset")
    public ResponseEntity<Map<String, Object>> resetDatabase() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            System.out.println("⚠️ STAGING: Resetting database...");
            
            // Clear data in dependency order
            financialTransactionRepository.deleteAll();
            propertyRepository.deleteAll();
            customerRepository.deleteAll();
            userRepository.deleteAll();
            
            System.out.println("✅ STAGING: Database reset complete");
            
            result.put("status", "success");
            result.put("message", "Database reset successfully");
            result.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Database reset failed: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Check database connectivity
            long userCount = userRepository.count();
            long customerCount = customerRepository.count();
            long propertyCount = propertyRepository.count();
            long transactionCount = financialTransactionRepository.count();
            
            health.put("status", "healthy");
            health.put("database", "connected");
            health.put("environment", "staging");
            health.put("counts", Map.of(
                "users", userCount,
                "customers", customerCount,
                "properties", propertyCount,
                "transactions", transactionCount
            ));
            health.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            health.put("status", "unhealthy");
            health.put("error", e.getMessage());
            return ResponseEntity.status(500).body(health);
        }
    }

    /**
     * Incremental sync for ongoing development
     */
    @PostMapping("/sync/incremental")  
    public ResponseEntity<Map<String, Object>> executeIncrementalSync() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            System.out.println("🔄 STAGING: Starting incremental PayProp sync...");
            
            // Sync last 30 days of data
            LocalDate fromDate = LocalDate.now().minusDays(30);
            LocalDate toDate = LocalDate.now();
            
            // Execute targeted sync
            payPropSyncService.performComprehensiveFinancialSync();
            
            result.put("status", "success");
            result.put("message", "Incremental sync completed");
            result.put("period", fromDate + " to " + toDate);
            result.put("statistics", getSyncStatistics());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Incremental sync failed: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Helper method to get sync statistics
     */
    private Map<String, Object> getSyncStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            stats.put("users", userRepository.count());
            stats.put("customers", customerRepository.count());  
            stats.put("properties", propertyRepository.count());
            stats.put("financial_transactions", financialTransactionRepository.count());
            stats.put("properties_with_payprop_id", 
                propertyRepository.countByPayPropIdIsNotNull());
            stats.put("recent_transactions_30_days", 
                financialTransactionRepository.countByTransactionDateAfter(
                    LocalDate.now().minusDays(30)));
            
        } catch (Exception e) {
            stats.put("error", "Failed to get statistics: " + e.getMessage());
        }
        
        return stats;
    }
}
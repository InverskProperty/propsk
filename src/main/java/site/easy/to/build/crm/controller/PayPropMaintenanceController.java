package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.repository.OAuthUserRepository;
import site.easy.to.build.crm.repository.UserRepository;
import site.easy.to.build.crm.service.payprop.PayPropEntityResolutionService;
import site.easy.to.build.crm.service.payprop.PayPropSyncOrchestrator;
import site.easy.to.build.crm.service.payprop.PayPropSyncMonitoringService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * PayProp Maintenance Controller
 * Provides endpoints for manual sync operations, orphan resolution, and system health checks
 */
@RestController
@RequestMapping("/api/payprop/maintenance")
@PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
public class PayPropMaintenanceController {
    
    @Autowired
    private PayPropEntityResolutionService resolutionService;
    
    @Autowired
    private PayPropSyncOrchestrator syncOrchestrator;
    
    @Autowired
    private PayPropSyncMonitoringService monitoringService;
    
    @Autowired
    private OAuthUserRepository oAuthUserRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private AuthenticationUtils authenticationUtils;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    /**
     * Manually trigger orphaned entity resolution
     */
    @PostMapping("/resolve-orphans")
    public ResponseEntity<?> resolveOrphans() {
        try {
            resolutionService.resolveAllOrphanedEntities();
            
            // Get updated status after resolution
            Map<String, Object> status = checkOrphanStatusInternal();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Orphan resolution completed",
                "timestamp", LocalDateTime.now(),
                "status", status
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }
    

    /**
     * Run a full sync manually
     */
    @PostMapping("/full-sync")
    public ResponseEntity<?> runFullSync(@RequestParam(required = false) Long userId) {
        try {
            // Use current user if userId not provided
            if (userId == null) {
                // Get current authenticated user ID directly
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                int currentUserId = authenticationUtils.getLoggedInUserId(authentication);
                
                if (currentUserId == -1) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "No authenticated user found",
                        "timestamp", LocalDateTime.now()
                    ));
                }
                
                userId = Long.valueOf(currentUserId);
            }
            
            // Get OAuth user for the current session
            // Since we don't have findByUserIdAndProvider, we'll use a different approach
            OAuthUser oAuthUser = null;
            
            // First try to get from authentication if it's OAuth
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof OAuth2User) {
                oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(auth);
            }
            
            // If not OAuth authentication, try to find by user
            if (oAuthUser == null) {
                // Get the user first
                User user = userRepository.findById(userId.intValue());
                
                if (user != null) {
                    // Now get the OAuth user for this user
                    oAuthUser = oAuthUserRepository.getOAuthUserByUser(user);
                }
            }
            
            if (oAuthUser == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "No Google OAuth connection found. Please connect your Google account first.",
                    "timestamp", LocalDateTime.now()
                ));
            }
            
            // Run the sync
            PayPropSyncOrchestrator.UnifiedSyncResult result = 
                syncOrchestrator.performEnhancedUnifiedSyncWithWorkingFinancials(oAuthUser, userId);
            
            return ResponseEntity.ok(Map.of(
                "success", result.isOverallSuccess(),
                "summary", result.getSummary(),
                "timestamp", LocalDateTime.now(),
                "details", extractSyncDetails(result)
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }
    
    /**
     * Check current orphan status
     */
    @GetMapping("/orphan-status")
    public ResponseEntity<?> checkOrphanStatus() {
        try {
            Map<String, Object> status = checkOrphanStatusInternal();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }
    
    /**
     * Get comprehensive sync health report
     */
    @GetMapping("/health")
    public ResponseEntity<?> getSyncHealth() {
        try {
            PayPropSyncMonitoringService.SyncHealthReport report = 
                monitoringService.generateHealthReport();
            
            return ResponseEntity.ok(Map.of(
                "healthScore", report.getHealthScore(),
                "healthStatus", report.getHealthStatus(),
                "timestamp", LocalDateTime.now(),
                "details", report
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }
    
    /**
     * Get sync statistics
     */
    @GetMapping("/sync-stats")
    public ResponseEntity<?> getSyncStatistics() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // Entity sync rates
            String entityStatsSql = """
                SELECT 
                    'Properties' as entity_type,
                    COUNT(*) as total,
                    SUM(CASE WHEN payprop_id IS NOT NULL THEN 1 ELSE 0 END) as synced,
                    ROUND(100.0 * SUM(CASE WHEN payprop_id IS NOT NULL THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0), 2) as sync_rate
                FROM properties
                
                UNION ALL
                
                SELECT 
                    'Customers' as entity_type,
                    COUNT(*) as total,
                    SUM(CASE WHEN payprop_entity_id IS NOT NULL THEN 1 ELSE 0 END) as synced,
                    ROUND(100.0 * SUM(CASE WHEN payprop_entity_id IS NOT NULL THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0), 2) as sync_rate
                FROM customers
                
                UNION ALL
                
                SELECT 
                    'Payments' as entity_type,
                    COUNT(*) as total,
                    SUM(CASE WHEN pay_prop_payment_id IS NOT NULL THEN 1 ELSE 0 END) as synced,
                    ROUND(100.0 * SUM(CASE WHEN pay_prop_payment_id IS NOT NULL THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0), 2) as sync_rate
                FROM payments
            """;
            
            stats.put("entityStats", jdbcTemplate.queryForList(entityStatsSql));
            
            // Financial sync stats
            String financialStatsSql = """
                SELECT 
                    COUNT(*) as total_transactions,
                    SUM(CASE WHEN is_actual_transaction = 1 THEN 1 ELSE 0 END) as actual_transactions,
                    SUM(CASE WHEN is_instruction = 1 THEN 1 ELSE 0 END) as instructions,
                    COUNT(DISTINCT property_id) as properties_with_transactions,
                    COUNT(DISTINCT pay_prop_batch_id) as total_batches,
                    MIN(transaction_date) as earliest_transaction,
                    MAX(transaction_date) as latest_transaction
                FROM financial_transactions
            """;
            
            stats.put("financialStats", jdbcTemplate.queryForMap(financialStatsSql));
            
            // Recent sync activity
            String recentActivitySql = """
                SELECT 
                    DATE(created_at) as sync_date,
                    COUNT(*) as transactions_synced,
                    SUM(amount) as total_amount
                FROM financial_transactions
                WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
                GROUP BY DATE(created_at)
                ORDER BY sync_date DESC
            """;
            
            stats.put("recentActivity", jdbcTemplate.queryForList(recentActivitySql));
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }
    
    /**
     * Internal method to check orphan status
     */
    private Map<String, Object> checkOrphanStatusInternal() {
        Map<String, Object> status = new HashMap<>();
        
        // Check orphaned properties
        String orphanedPropertiesSql = """
            SELECT COUNT(DISTINCT ft.property_id) 
            FROM financial_transactions ft 
            LEFT JOIN properties p ON p.payprop_id = ft.property_id 
            WHERE ft.property_id IS NOT NULL AND p.id IS NULL
        """;
        
        // Check orphaned tenants
        String orphanedTenantsSql = """
            SELECT COUNT(DISTINCT ft.tenant_id) 
            FROM financial_transactions ft 
            LEFT JOIN customers c ON c.payprop_entity_id = ft.tenant_id 
            WHERE ft.tenant_id IS NOT NULL AND c.customer_id IS NULL
        """;
        
        // Check orphaned beneficiaries
        String orphanedBeneficiariesSql = """
            SELECT COUNT(DISTINCT p.beneficiary_id) 
            FROM payments p 
            LEFT JOIN customers c ON c.customer_id = p.beneficiary_id 
            WHERE p.beneficiary_id IS NOT NULL AND c.customer_id IS NULL
        """;
        
        Integer orphanedProperties = jdbcTemplate.queryForObject(orphanedPropertiesSql, Integer.class);
        Integer orphanedTenants = jdbcTemplate.queryForObject(orphanedTenantsSql, Integer.class);
        Integer orphanedBeneficiaries = jdbcTemplate.queryForObject(orphanedBeneficiariesSql, Integer.class);
        
        status.put("orphanedProperties", orphanedProperties != null ? orphanedProperties : 0);
        status.put("orphanedTenants", orphanedTenants != null ? orphanedTenants : 0);
        status.put("orphanedBeneficiaries", orphanedBeneficiaries != null ? orphanedBeneficiaries : 0);
        status.put("totalOrphans", 
            (orphanedProperties != null ? orphanedProperties : 0) + 
            (orphanedTenants != null ? orphanedTenants : 0) + 
            (orphanedBeneficiaries != null ? orphanedBeneficiaries : 0));
        status.put("needsResolution", 
            (orphanedProperties != null && orphanedProperties > 0) || 
            (orphanedTenants != null && orphanedTenants > 0) || 
            (orphanedBeneficiaries != null && orphanedBeneficiaries > 0));
        status.put("timestamp", LocalDateTime.now());
        
        return status;
    }
    
    /**
     * Extract detailed sync results
     */
    private Map<String, Object> extractSyncDetails(PayPropSyncOrchestrator.UnifiedSyncResult result) {
        Map<String, Object> details = new HashMap<>();
        
        if (result.getPropertiesResult() != null) {
            details.put("properties", result.getPropertiesResult().getDetails());
        }
        if (result.getPropertyOwnersResult() != null) {
            details.put("propertyOwners", result.getPropertyOwnersResult().getDetails());
        }
        if (result.getTenantsResult() != null) {
            details.put("tenants", result.getTenantsResult().getDetails());
        }
        if (result.getContractorsResult() != null) {
            details.put("contractors", result.getContractorsResult().getDetails());
        }
        if (result.getFinancialSyncResult() != null) {
            details.put("financial", result.getFinancialSyncResult().getDetails());
        }
        if (result.getOrphanResolutionResult() != null) {
            details.put("orphanResolution", result.getOrphanResolutionResult().getDetails());
        }
        
        return details;
    }
}
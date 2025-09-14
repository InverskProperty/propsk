package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
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
import site.easy.to.build.crm.service.payprop.PayPropMaintenanceSyncService;
import site.easy.to.build.crm.service.payprop.PayPropSyncOrchestrator;
import site.easy.to.build.crm.service.payprop.SyncResult;
import site.easy.to.build.crm.service.payprop.PayPropSyncMonitoringService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.util.AuthorizationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PayProp Maintenance Controller
 * Provides endpoints for manual sync operations, orphan resolution, and system health checks
 */
@RestController
@RequestMapping("/api/payprop/maintenance")
@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
public class PayPropMaintenanceController {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropMaintenanceController.class);
    
    @Autowired
    private PayPropEntityResolutionService resolutionService;
    
    @Autowired
    private PayPropSyncOrchestrator syncOrchestrator;
    
    @Autowired
    private PayPropSyncMonitoringService monitoringService;
    
    @Autowired
    private PayPropMaintenanceSyncService payPropMaintenanceSyncService;
    
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
     * Run a scope-aware sync that works with current permissions (no all-payments report)
     * Works with your available scopes: read:export:* permissions
     */
    @PostMapping("/scope-aware-sync")
    public ResponseEntity<?> runScopeAwareSync(@RequestParam(required = false) Long userId,
                                              @RequestParam(required = false, defaultValue = "false") Boolean syncToPayProp) {
        try {
            // Use current user if userId not provided
            if (userId == null) {
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
            
            Map<String, Object> result = new HashMap<>();
            result.put("syncType", "scope-aware");
            result.put("syncToPayProp", syncToPayProp);
            result.put("message", "Running unified sync with available scopes (uses /export/* endpoints only)");
            result.put("timestamp", LocalDateTime.now());
            result.put("userId", userId);
            
            log.info("🔄 Starting scope-aware sync for user {}, syncToPayProp={}", userId, syncToPayProp);
            
            // Step 1: Process properties from raw PayProp data
            log.info("🏠 Step 1: Processing raw properties into main properties table...");
            try {
                if (syncOrchestrator != null) {
                    SyncResult propertiesResult = syncOrchestrator.syncPropertiesFromPayPropEnhanced(userId);
                    result.put("propertiesResult", Map.of(
                        "success", propertiesResult.isSuccess(),
                        "message", propertiesResult.getMessage(),
                        "details", propertiesResult.getDetails()
                    ));
                    log.info("✅ Properties processing: {}", propertiesResult.getMessage());
                } else {
                    log.warn("⚠️ SyncOrchestrator not available, skipping properties processing");
                    result.put("propertiesResult", Map.of("skipped", true, "reason", "SyncOrchestrator not available"));
                }
            } catch (Exception e) {
                log.error("❌ Properties processing failed: {}", e.getMessage());
                result.put("propertiesResult", Map.of("success", false, "error", e.getMessage()));
            }
            
            // Step 2: Process property owners (beneficiaries) as customers
            log.info("👥 Step 2: Processing property owners from raw beneficiaries...");
            try {
                if (syncOrchestrator != null) {
                    // Extract relationships from raw export data instead of payments
                    Map<String, PayPropSyncOrchestrator.PropertyRelationship> relationships = syncOrchestrator.extractRelationshipsFromRawData();
                    SyncResult ownersResult = syncOrchestrator.syncPropertyOwnersAsCustomers(userId, relationships);
                    result.put("propertyOwnersResult", Map.of(
                        "success", ownersResult.isSuccess(),
                        "message", ownersResult.getMessage(),
                        "details", ownersResult.getDetails()
                    ));
                    log.info("✅ Property owners processing: {}", ownersResult.getMessage());
                } else {
                    result.put("propertyOwnersResult", Map.of("skipped", true, "reason", "SyncOrchestrator not available"));
                }
            } catch (Exception e) {
                log.error("❌ Property owners processing failed: {}", e.getMessage());
                result.put("propertyOwnersResult", Map.of("success", false, "error", e.getMessage()));
            }
            
            // Step 3: Process tenants as customers
            log.info("🏠 Step 3: Processing tenants from raw tenant data...");
            try {
                if (syncOrchestrator != null) {
                    SyncResult tenantsResult = syncOrchestrator.syncTenantsAsCustomers(userId);
                    result.put("tenantsResult", Map.of(
                        "success", tenantsResult.isSuccess(),
                        "message", tenantsResult.getMessage(),
                        "details", tenantsResult.getDetails()
                    ));
                    log.info("✅ Tenants processing: {}", tenantsResult.getMessage());
                } else {
                    result.put("tenantsResult", Map.of("skipped", true, "reason", "SyncOrchestrator not available"));
                }
            } catch (Exception e) {
                log.error("❌ Tenants processing failed: {}", e.getMessage());
                result.put("tenantsResult", Map.of("success", false, "error", e.getMessage()));
            }
            
            // Step 4: Process financial data (invoices and payments) without all-payments report
            log.info("💰 Step 4: Processing financial data using export endpoints only...");
            try {
                if (syncOrchestrator != null) {
                    SyncResult financialResult = syncOrchestrator.syncFinancialDataFromRawExports(userId);
                    result.put("financialResult", Map.of(
                        "success", financialResult.isSuccess(),
                        "message", financialResult.getMessage(),
                        "details", financialResult.getDetails()
                    ));
                    log.info("✅ Financial data processing: {}", financialResult.getMessage());
                } else {
                    result.put("financialResult", Map.of("skipped", true, "reason", "SyncOrchestrator not available"));
                }
            } catch (Exception e) {
                log.error("❌ Financial data processing failed: {}", e.getMessage());
                result.put("financialResult", Map.of("success", false, "error", e.getMessage()));
            }
            
            // Step 5: Establish property-owner relationships
            log.info("🔗 Step 5: Establishing property-owner relationships...");
            try {
                if (syncOrchestrator != null) {
                    SyncResult relationshipsResult = syncOrchestrator.establishPropertyOwnerRelationships();
                    result.put("relationshipsResult", Map.of(
                        "success", relationshipsResult.isSuccess(),
                        "message", relationshipsResult.getMessage(),
                        "details", relationshipsResult.getDetails()
                    ));
                    log.info("✅ Property relationships: {}", relationshipsResult.getMessage());
                } else {
                    result.put("relationshipsResult", Map.of("skipped", true, "reason", "SyncOrchestrator not available"));
                }
            } catch (Exception e) {
                log.error("❌ Property relationships failed: {}", e.getMessage());
                result.put("relationshipsResult", Map.of("success", false, "error", e.getMessage()));
            }
            
            // Summary of what was processed
            log.info("📊 Summary: Scope-aware sync using available export permissions");
            Map<String, Object> scopeSummary = Map.of(
                "scopesUsed", List.of("read:export:properties", "read:export:beneficiaries", "read:export:tenants", "read:export:invoices", "read:export:payments"),
                "excludedScopes", List.of("read:report:all-payments"),
                "processedEntities", List.of("properties", "property-owners", "tenants", "invoices", "payments", "relationships")
            );
            result.put("scopeSummary", scopeSummary);
            
            result.put("success", true);
            result.put("completedAt", LocalDateTime.now());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("❌ Scope-aware sync failed", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            errorResult.put("timestamp", LocalDateTime.now());
            return ResponseEntity.ok(errorResult);
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
            
            // Run the sync (FULL SYNC WITH ALL-PAYMENTS)
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
     * Sync maintenance categories from PayProp
     */
    @PostMapping("/sync/categories")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncMaintenanceCategories(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }
        
        try {
            SyncResult result = payPropMaintenanceSyncService.syncMaintenanceCategories();
            
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("details", result.getDetails());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error syncing maintenance categories: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Category sync failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Import maintenance tickets from PayProp
     */
    @PostMapping("/sync/import-tickets")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> importMaintenanceTickets(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }
        
        try {
            SyncResult result = payPropMaintenanceSyncService.importMaintenanceTickets();
            
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("details", result.getDetails());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error importing maintenance tickets: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Import failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Export CRM tickets to PayProp
     */
    @PostMapping("/sync/export-tickets")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> exportMaintenanceTickets(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }
        
        try {
            SyncResult result = payPropMaintenanceSyncService.exportMaintenanceTicketsToPayProp();
            
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("details", result.getDetails());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error exporting maintenance tickets: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Export failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Full bidirectional sync
     */
    @PostMapping("/sync/bidirectional")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> bidirectionalMaintenanceSync(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }
        
        try {
            // Import from PayProp first
            SyncResult importResult = payPropMaintenanceSyncService.importMaintenanceTickets();
            
            // Then export pending CRM tickets
            SyncResult exportResult = payPropMaintenanceSyncService.exportMaintenanceTicketsToPayProp();
            
            response.put("success", importResult.isSuccess() && exportResult.isSuccess());
            response.put("import", Map.of(
                "success", importResult.isSuccess(),
                "message", importResult.getMessage(),
                "details", importResult.getDetails()
            ));
            response.put("export", Map.of(
                "success", exportResult.isSuccess(),
                "message", exportResult.getMessage(),
                "details", exportResult.getDetails()
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error in bidirectional sync: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Bidirectional sync failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Enhanced maintenance ticket created webhook
     */
    @PostMapping("/maintenance-ticket-created")
    public ResponseEntity<Map<String, Object>> handleMaintenanceTicketCreated(@RequestBody Map<String, Object> webhookData) {
        try {
            log.info("🎫 Received PayProp maintenance-ticket-created webhook");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> events = (List<Map<String, Object>>) webhookData.get("events");
            if (events == null || events.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "No events in webhook data"));
            }
            
            int processed = 0;
            int errors = 0;
            
            for (Map<String, Object> event : events) {
                if ("maintenance_ticket".equals(event.get("type")) && "create".equals(event.get("action"))) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> ticketData = (Map<String, Object>) event.get("data");
                    
                    if (ticketData != null) {
                        try {
                            // Use the new sync service with proper enum reference
                            PayPropMaintenanceSyncService.MaintenanceTicketSyncResult result = 
                                payPropMaintenanceSyncService.syncMaintenanceTicketFromPayProp(ticketData);
                            
                            if (result != PayPropMaintenanceSyncService.MaintenanceTicketSyncResult.ERROR) {
                                processed++;
                            } else {
                                errors++;
                            }
                            
                        } catch (Exception e) {
                            errors++;
                            log.error("❌ Failed to process maintenance ticket webhook: {}", e.getMessage());
                        }
                    }
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "success", errors == 0,
                "message", String.format("Processed %d tickets, %d errors", processed, errors),
                "processed", processed,
                "errors", errors
            ));
            
        } catch (Exception e) {
            log.error("❌ Error processing maintenance-ticket-created webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Enhanced maintenance ticket updated webhook
     */
    @PostMapping("/maintenance-ticket-updated")
    public ResponseEntity<Map<String, Object>> handleMaintenanceTicketUpdated(@RequestBody Map<String, Object> webhookData) {
        try {
            log.info("🎫 Received PayProp maintenance-ticket-updated webhook");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> events = (List<Map<String, Object>>) webhookData.get("events");
            if (events == null || events.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "No events in webhook data"));
            }
            
            int processed = 0;
            int errors = 0;
            
            for (Map<String, Object> event : events) {
                if ("maintenance_ticket".equals(event.get("type")) && "update".equals(event.get("action"))) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> ticketData = (Map<String, Object>) event.get("data");
                    
                    if (ticketData != null) {
                        try {
                            // Use the new sync service with proper enum reference
                            PayPropMaintenanceSyncService.MaintenanceTicketSyncResult result = 
                                payPropMaintenanceSyncService.syncMaintenanceTicketFromPayProp(ticketData);
                            
                            if (result != PayPropMaintenanceSyncService.MaintenanceTicketSyncResult.ERROR) {
                                processed++;
                            } else {
                                errors++;
                            }
                            
                        } catch (Exception e) {
                            errors++;
                            log.error("❌ Failed to process maintenance ticket update webhook: {}", e.getMessage());
                        }
                    }
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "success", errors == 0,
                "message", String.format("Updated %d tickets, %d errors", processed, errors),
                "processed", processed,
                "errors", errors
            ));
            
        } catch (Exception e) {
            log.error("❌ Error processing maintenance-ticket-updated webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
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
        if (result.getMaintenanceResult() != null) { // ADD THIS BLOCK
            details.put("maintenance", result.getMaintenanceResult().getDetails());
        }
        
        return details;
    }
}
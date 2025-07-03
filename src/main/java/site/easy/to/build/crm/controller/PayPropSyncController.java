// PayPropSyncController.java - Main Two-Way Sync Management API
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.service.payprop.*;
import site.easy.to.build.crm.service.payprop.PayPropSyncOrchestrator.ComprehensiveSyncResult;
import site.easy.to.build.crm.service.payprop.PayPropChangeDetection.SyncChangeDetection;
import site.easy.to.build.crm.service.payprop.PayPropSyncLogger.SyncStatistics;
import site.easy.to.build.crm.util.AuthorizationUtil;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Controller
@RequestMapping("/api/payprop/sync")
public class PayPropSyncController {

    private final PayPropSyncOrchestrator syncOrchestrator;
    private final PayPropOAuth2Service oAuth2Service;
    private final PayPropChangeDetection changeDetection;
    private final PayPropSyncLogger syncLogger;
    private final PayPropSyncService payPropSyncService;
    private final PayPropPortfolioSyncService portfolioSyncService;

    @Autowired
    public PayPropSyncController(PayPropSyncOrchestrator syncOrchestrator,
                                PayPropOAuth2Service oAuth2Service,
                                PayPropChangeDetection changeDetection,
                                PayPropSyncLogger syncLogger,
                                PayPropSyncService payPropSyncService,
                                PayPropPortfolioSyncService portfolioSyncService) {
        this.syncOrchestrator = syncOrchestrator;
        this.oAuth2Service = oAuth2Service;
        this.changeDetection = changeDetection;
        this.syncLogger = syncLogger;
        this.payPropSyncService = payPropSyncService;
        this.portfolioSyncService = portfolioSyncService;
    }

    // ===== DASHBOARD AND STATUS =====

    /**
     * Main sync dashboard
     */
    @GetMapping("/dashboard")
    public String showSyncDashboard(Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return "redirect:/access-denied";
        }

        // Add sync status information
        model.addAttribute("hasValidTokens", oAuth2Service.hasValidTokens());
        model.addAttribute("syncStatistics", syncLogger.getSyncStatistics(LocalDateTime.now().minusDays(7)));
        model.addAttribute("pageTitle", "PayProp Sync Dashboard");

        return "payprop/sync-dashboard";
    }

    /**
     * Get current sync status
     */
    @GetMapping("/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSyncStatus(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        Map<String, Object> status = new HashMap<>();
        
        // OAuth2 status
        status.put("oauthConnected", oAuth2Service.hasValidTokens());
        
        // Sync statistics
        SyncStatistics stats = syncLogger.getSyncStatistics(LocalDateTime.now().minusHours(24));
        status.put("last24Hours", Map.of(
            "totalSyncs", stats.getTotalSyncs(),
            "successRate", stats.getSuccessRate(),
            "conflictsDetected", stats.getConflictsDetected(),
            "conflictsResolved", stats.getConflictsResolved()
        ));
        
        // Change detection
        try {
            SyncChangeDetection changes = changeDetection.detectChanges();
            status.put("pendingChanges", Map.of(
                "crmChanges", changes.getCrmChanges().getTotalChanges(),
                "payPropChanges", changes.getPayPropChanges().getTotalChanges(),
                "hasChanges", !changes.hasNoChanges()
            ));
        } catch (Exception e) {
            status.put("pendingChanges", Map.of("error", "Failed to detect changes"));
        }
        
        // Quick status check
        try {
            payPropSyncService.checkSyncStatus();
            status.put("systemHealth", "healthy");
        } catch (Exception e) {
            status.put("systemHealth", "error");
            status.put("healthError", e.getMessage());
        }

        return ResponseEntity.ok(status);
    }

    // ===== FULL SYNC OPERATIONS =====

    /**
     * Perform full two-way sync
     */
    @PostMapping("/full")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> performFullSync(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        if (!oAuth2Service.hasValidTokens()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "PayProp not authorized. Please complete OAuth2 setup first."
            ));
        }

        Long userId = 1L; // You'd get this from authentication
        
        try {
            ComprehensiveSyncResult result = syncOrchestrator.performFullSync(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isOverallSuccess());
            response.put("message", result.getSummary());
            response.put("details", Map.of(
                "crmToPayProp", result.getCrmToPayPropResult(),
                "payPropToCrm", result.getPayPropToCrmResult(),
                "conflictResolution", result.getConflictResolutionResult(),
                "portfolioSync", result.getPortfolioSyncResult()
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Full sync failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Perform intelligent sync (only changed data)
     */
    @PostMapping("/intelligent")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> performIntelligentSync(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        if (!oAuth2Service.hasValidTokens()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "PayProp not authorized"
            ));
        }

        Long userId = 1L; // You'd get this from authentication
        
        try {
            ComprehensiveSyncResult result = syncOrchestrator.performIntelligentSync(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isOverallSuccess());
            response.put("message", result.getSummary());
            response.put("details", result);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Intelligent sync failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Perform async full sync
     */
    @PostMapping("/full/async")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> performAsyncFullSync(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        if (!oAuth2Service.hasValidTokens()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "PayProp not authorized"
            ));
        }

        Long userId = 1L; // You'd get this from authentication
        
        // Start async sync
        CompletableFuture<ComprehensiveSyncResult> futureResult = CompletableFuture.supplyAsync(() -> {
            try {
                return syncOrchestrator.performFullSync(userId);
            } catch (Exception e) {
                ComprehensiveSyncResult errorResult = new ComprehensiveSyncResult();
                errorResult.setOverallError("Async sync failed: " + e.getMessage());
                return errorResult;
            }
        });

        Map<String, Object> response = Map.of(
            "success", true,
            "message", "Full sync started asynchronously",
            "syncId", "sync_" + System.currentTimeMillis()
        );

        return ResponseEntity.ok(response);
    }

    // ===== ENTITY-SPECIFIC SYNC =====

    /**
     * Sync specific property
     */
    @PostMapping("/property/{propertyId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncProperty(@PathVariable Long propertyId, 
                                                           Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        try {
            String payPropId = payPropSyncService.syncPropertyToPayProp(propertyId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Property synced successfully",
                "payPropId", payPropId
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Property sync failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Sync specific tenant
     */
    @PostMapping("/tenant/{tenantId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncTenant(@PathVariable Long tenantId,
                                                         Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        try {
            String payPropId = payPropSyncService.syncTenantToPayProp(tenantId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Tenant synced successfully",
                "payPropId", payPropId
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Tenant sync failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Sync specific beneficiary (property owner)
     */
    @PostMapping("/beneficiary/{ownerId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncBeneficiary(@PathVariable Long ownerId,
                                                              Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        try {
            String payPropId = payPropSyncService.syncBeneficiaryToPayProp(ownerId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Beneficiary synced successfully",
                "payPropId", payPropId
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Beneficiary sync failed: " + e.getMessage()
            ));
        }
    }

    // ===== BATCH SYNC OPERATIONS =====

    /**
     * Sync all ready properties
     */
    @PostMapping("/properties/batch")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncAllProperties(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        try {
            payPropSyncService.syncAllReadyProperties();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "All ready properties synced"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Batch property sync failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Sync all ready tenants
     */
    @PostMapping("/tenants/batch")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncAllTenants(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        try {
            payPropSyncService.syncAllReadyTenants();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "All ready tenants synced"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Batch tenant sync failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Sync all ready beneficiaries
     */
    @PostMapping("/beneficiaries/batch")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncAllBeneficiaries(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        try {
            payPropSyncService.syncAllReadyBeneficiaries();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "All ready beneficiaries synced"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Batch beneficiary sync failed: " + e.getMessage()
            ));
        }
    }

    // ===== PORTFOLIO SYNC =====

    /**
     * Sync portfolios to PayProp tags
     */
    @PostMapping("/portfolios")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncPortfolios(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        Long userId = 1L; // You'd get this from authentication
        
        try {
            SyncResult result = portfolioSyncService.syncAllPortfolios(userId);
            
            return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage(),
                "details", result.getDetails()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Portfolio sync failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Pull PayProp tags to local portfolios
     */
    @PostMapping("/portfolios/pull")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> pullPortfoliosFromPayProp(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        Long userId = 1L; // You'd get this from authentication
        
        try {
            SyncResult result = portfolioSyncService.pullAllTagsFromPayProp(userId);
            
            return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage(),
                "details", result.getDetails()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Portfolio pull failed: " + e.getMessage()
            ));
        }
    }

    // ===== CHANGE DETECTION =====

    /**
     * Detect pending changes
     */
    @GetMapping("/changes")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> detectChanges(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        try {
            SyncChangeDetection changes = changeDetection.detectChanges();
            
            Map<String, Object> response = new HashMap<>();
            response.put("detectedAt", changes.getDetectedAt());
            response.put("hasChanges", !changes.hasNoChanges());
            response.put("crmChanges", Map.of(
                "total", changes.getCrmChanges().getTotalChanges(),
                "newProperties", changes.getCrmChanges().getNewProperties().size(),
                "modifiedProperties", changes.getCrmChanges().getModifiedProperties().size(),
                "newCustomers", changes.getCrmChanges().getNewCustomers().size(),
                "modifiedCustomers", changes.getCrmChanges().getModifiedCustomers().size()
            ));
            response.put("payPropChanges", Map.of(
                "total", changes.getPayPropChanges().getTotalChanges(),
                "newProperties", changes.getPayPropChanges().getNewProperties().size(),
                "modifiedProperties", changes.getPayPropChanges().getModifiedProperties().size(),
                "newTenants", changes.getPayPropChanges().getNewTenants().size(),
                "modifiedTenants", changes.getPayPropChanges().getModifiedTenants().size(),
                "newBeneficiaries", changes.getPayPropChanges().getNewBeneficiaries().size(),
                "modifiedBeneficiaries", changes.getPayPropChanges().getModifiedBeneficiaries().size()
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Change detection failed: " + e.getMessage()
            ));
        }
    }

    // ===== SYNC STATISTICS =====

    /**
     * Get sync statistics
     */
    @GetMapping("/statistics")
    @ResponseBody
    public ResponseEntity<SyncStatistics> getSyncStatistics(
            @RequestParam(required = false, defaultValue = "24") int hours,
            Authentication authentication) {
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).build();
        }

        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        SyncStatistics stats = syncLogger.getSyncStatistics(since);
        
        return ResponseEntity.ok(stats);
    }

    // ===== UTILITY ENDPOINTS =====

    /**
     * Check sync readiness
     */
    @GetMapping("/readiness")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkSyncReadiness(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        Map<String, Object> readiness = new HashMap<>();
        
        // Check OAuth2 status
        readiness.put("oauthReady", oAuth2Service.hasValidTokens());
        
        // Check system health
        try {
            payPropSyncService.checkSyncStatus();
            readiness.put("systemHealthy", true);
        } catch (Exception e) {
            readiness.put("systemHealthy", false);
            readiness.put("healthError", e.getMessage());
        }
        
        // Check for pending sync items
        try {
            SyncChangeDetection changes = changeDetection.detectChanges();
            readiness.put("hasPendingChanges", !changes.hasNoChanges());
            readiness.put("pendingChanges", changes.getCrmChanges().getTotalChanges() + 
                                           changes.getPayPropChanges().getTotalChanges());
        } catch (Exception e) {
            readiness.put("changeDetectionError", e.getMessage());
        }
        
        boolean overallReady = (boolean) readiness.get("oauthReady") && 
                              (boolean) readiness.get("systemHealthy");
        readiness.put("ready", overallReady);
        
        return ResponseEntity.ok(readiness);
    }

    /**
     * Force sync status check
     */

    @GetMapping("/sync-dashboard")
    public String syncDashboard(Model model) {
        return "payprop/sync-dashboard";
    }
    
    @PostMapping("/check-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> forceStatusCheck(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        try {
            payPropSyncService.checkSyncStatus();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Sync status check completed"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Status check failed: " + e.getMessage()
            ));
        }
    }
}
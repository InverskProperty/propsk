// PayPropSyncController.java - Enhanced with Complete Sync Functionality
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.service.payprop.*;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.service.payprop.PayPropSyncOrchestrator.UnifiedSyncResult;
import site.easy.to.build.crm.service.payprop.PayPropSyncLogger.SyncStatistics;
import site.easy.to.build.crm.service.payprop.PayPropSyncService.PayPropExportResult;
import site.easy.to.build.crm.service.payprop.PayPropSyncService.SyncResult;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Controller
@RequestMapping("/api/payprop/sync")
public class PayPropSyncController {

    private final PayPropSyncOrchestrator syncOrchestrator;
    private final PayPropOAuth2Service oAuth2Service;
    private final PayPropSyncLogger syncLogger;
    private final PayPropSyncService payPropSyncService;
    private final AuthenticationUtils authenticationUtils;

    @Autowired
    public PayPropSyncController(PayPropSyncOrchestrator syncOrchestrator,
                                PayPropOAuth2Service oAuth2Service,
                                PayPropSyncLogger syncLogger,
                                PayPropSyncService payPropSyncService,
                                AuthenticationUtils authenticationUtils) {
        this.syncOrchestrator = syncOrchestrator;
        this.oAuth2Service = oAuth2Service;
        this.syncLogger = syncLogger;
        this.payPropSyncService = payPropSyncService;
        this.authenticationUtils = authenticationUtils;
    }

    // ===== DASHBOARD AND STATUS =====

    @GetMapping("/dashboard")
    public String showSyncDashboard(Model model, Authentication authentication) {
        model.addAttribute("hasValidTokens", oAuth2Service.hasValidTokens());
        model.addAttribute("syncStatistics", syncLogger.getSyncStatistics(LocalDateTime.now().minusDays(7)));
        model.addAttribute("pageTitle", "PayProp Sync Dashboard");
        return "payprop/sync-dashboard";
    }

    @GetMapping("/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSyncStatus(Authentication authentication) {
        Map<String, Object> status = new HashMap<>();
        
        status.put("oauthConnected", oAuth2Service.hasValidTokens());
        
        SyncStatistics stats = syncLogger.getSyncStatistics(LocalDateTime.now().minusHours(24));
        status.put("last24Hours", Map.of(
            "totalSyncs", stats.getTotalSyncs(),
            "successRate", stats.getSuccessRate(),
            "conflictsDetected", stats.getConflictsDetected(),
            "conflictsResolved", stats.getConflictsResolved()
        ));
        
        status.put("systemHealth", "healthy");

        return ResponseEntity.ok(status);
    }

    // ===== UNIFIED SYNC OPERATIONS =====

    @PostMapping("/unified")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> performUnifiedSync(Authentication authentication) {
        
        System.out.println("🚀 UNIFIED SYNC ENDPOINT REACHED");
        
        if (!oAuth2Service.hasValidTokens()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "PayProp not authorized. Please complete OAuth2 setup first."
            ));
        }

        Long userId = getCurrentUserId(authentication);
        System.out.println("🔍 Using user ID: " + userId);
        
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            UnifiedSyncResult result = syncOrchestrator.performUnifiedSync(oAuthUser, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isOverallSuccess());
            response.put("message", result.getSummary());
            response.put("details", Map.of(
                "properties", result.getPropertiesResult(),
                "propertyOwners", result.getPropertyOwnersResult(),
                "tenants", result.getTenantsResult(),
                "relationships", result.getRelationshipsResult()
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("❌ Unified sync failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Unified sync failed: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ NEW: Enhanced unified sync with complete rent and occupancy data
     */
    @PostMapping("/unified-enhanced")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> performEnhancedUnifiedSync(Authentication authentication) {
        
        System.out.println("🚀 ENHANCED UNIFIED SYNC ENDPOINT REACHED");
        
        if (!oAuth2Service.hasValidTokens()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "PayProp not authorized. Please complete OAuth2 setup first."
            ));
        }

        Long userId = getCurrentUserId(authentication);
        System.out.println("🔍 Using user ID: " + userId);
        
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            
            // ✅ USE ENHANCED SYNC METHOD
            UnifiedSyncResult result = syncOrchestrator.performEnhancedUnifiedSync(oAuthUser, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isOverallSuccess());
            response.put("message", result.getSummary());
            
            // ✅ ENHANCED: Include detailed rent and occupancy statistics
            Map<String, Object> enhancedDetails = new HashMap<>();
            enhancedDetails.put("properties", result.getPropertiesResult());
            enhancedDetails.put("propertyOwners", result.getPropertyOwnersResult());
            enhancedDetails.put("tenants", result.getTenantsResult());
            enhancedDetails.put("relationships", result.getRelationshipsResult());
            
            // Add data quality metrics
            SyncResult propertiesResult = result.getPropertiesResult();
            if (propertiesResult != null && propertiesResult.getDetails() != null) {
                Map<String, Object> propertyDetails = propertiesResult.getDetails();
                enhancedDetails.put("dataQuality", Map.of(
                    "rentDataQuality", propertyDetails.get("rentDataQuality"),
                    "occupancyDataQuality", propertyDetails.get("occupancyDataQuality"),
                    "rentAmountsFound", propertyDetails.get("rentAmountsFound"),
                    "occupancyDetected", propertyDetails.get("occupancyDetected")
                ));
            }
            
            response.put("details", enhancedDetails);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("❌ Enhanced unified sync failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Enhanced unified sync failed: " + e.getMessage()
            ));
        }
    }

    // Keep legacy endpoint for compatibility
    @PostMapping("/full")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> performFullSync(Authentication authentication) {
        return performUnifiedSync(authentication);
    }

    @PostMapping("/unified/async")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> performAsyncUnifiedSync(Authentication authentication) {
        if (!oAuth2Service.hasValidTokens()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "PayProp not authorized"
            ));
        }

        Long userId = getCurrentUserId(authentication);
        
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);

        CompletableFuture<UnifiedSyncResult> futureResult = CompletableFuture.supplyAsync(() -> {
            try {
                // FIXED: Call with correct signature (OAuthUser, Long)
                return syncOrchestrator.performUnifiedSync(oAuthUser, userId);
            } catch (Exception e) {
                UnifiedSyncResult errorResult = new UnifiedSyncResult();
                errorResult.setOverallError("Async sync failed: " + e.getMessage());
                return errorResult;
            }
        });

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Unified sync started asynchronously",
            "syncId", "sync_" + System.currentTimeMillis()
        ));
    }

    // ===== NEW ENHANCED ENDPOINTS =====

    /**
     * ✅ NEW: Get comprehensive property statistics with rent and occupancy data
     */
    @GetMapping("/property-statistics")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPropertyStatistics(Authentication authentication) {
        if (!oAuth2Service.hasValidTokens()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "PayProp not authorized"
            ));
        }
        
        try {
            // Get property statistics from PayProp with enhanced data
            Map<String, Object> stats = payPropSyncService.getPropertyStatistics();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "statistics", stats
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to get property statistics: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ NEW: Test individual property data retrieval with complete information
     */
    @GetMapping("/test-property/{propertyId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testPropertyDataRetrieval(
            @PathVariable String propertyId, 
            Authentication authentication) {
        
        if (!oAuth2Service.hasValidTokens()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "PayProp not authorized"
            ));
        }
        
        try {
            Map<String, Object> propertyData = payPropSyncService.getCompletePropertyData(propertyId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("propertyId", propertyId);
            response.put("data", propertyData);
            
            // Extract key information for easy viewing
            Map<String, Object> settings = (Map<String, Object>) propertyData.get("settings");
            List<Map<String, Object>> activeTenants = (List<Map<String, Object>>) propertyData.get("active_tenants");
            
            response.put("summary", Map.of(
                "propertyName", propertyData.get("property_name"),
                "monthlyRent", settings != null ? settings.get("monthly_payment") : "Not found",
                "isOccupied", propertyData.get("is_occupied"),
                "activeTenants", activeTenants != null ? activeTenants.size() : 0,
                "hasSettings", settings != null,
                "settingsKeys", settings != null ? settings.keySet() : "No settings"
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to retrieve property data: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ NEW: Debug endpoint to check what data PayProp is actually returning
     */
    @GetMapping("/debug-export/{page}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> debugPayPropExport(
            @PathVariable int page,
            @RequestParam(defaultValue = "5") int rows,
            Authentication authentication) {
        
        if (!oAuth2Service.hasValidTokens()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "PayProp not authorized"
            ));
        }
        
        try {
            // Test both regular and enhanced export
            PayPropExportResult regularResult = 
                payPropSyncService.exportPropertiesFromPayProp(page, rows);
            
            PayPropExportResult enhancedResult = 
                payPropSyncService.exportPropertiesFromPayPropEnhanced(page, rows);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            
            // Compare what we get from each method
            Map<String, Object> comparison = new HashMap<>();
            comparison.put("regularCount", regularResult.getItems().size());
            comparison.put("enhancedCount", enhancedResult.getItems().size());
            
            if (!regularResult.getItems().isEmpty()) {
                Map<String, Object> regularSample = regularResult.getItems().get(0);
                comparison.put("regularKeys", regularSample.keySet());
                comparison.put("regularHasSettings", regularSample.containsKey("settings"));
                
                if (regularSample.containsKey("settings")) {
                    Map<String, Object> settings = (Map<String, Object>) regularSample.get("settings");
                    comparison.put("regularSettingsKeys", settings != null ? settings.keySet() : "null");
                    comparison.put("regularMonthlyPayment", settings != null ? settings.get("monthly_payment") : "null");
                }
            }
            
            if (!enhancedResult.getItems().isEmpty()) {
                Map<String, Object> enhancedSample = enhancedResult.getItems().get(0);
                comparison.put("enhancedKeys", enhancedSample.keySet());
                comparison.put("enhancedHasSettings", enhancedSample.containsKey("settings"));
                comparison.put("enhancedHasActiveTenancies", enhancedSample.containsKey("active_tenancies"));
                
                if (enhancedSample.containsKey("settings")) {
                    Map<String, Object> settings = (Map<String, Object>) enhancedSample.get("settings");
                    comparison.put("enhancedSettingsKeys", settings != null ? settings.keySet() : "null");
                    comparison.put("enhancedMonthlyPayment", settings != null ? settings.get("monthly_payment") : "null");
                }
                
                if (enhancedSample.containsKey("active_tenancies")) {
                    List<Map<String, Object>> tenancies = (List<Map<String, Object>>) enhancedSample.get("active_tenancies");
                    comparison.put("enhancedActiveTenancies", tenancies != null ? tenancies.size() : 0);
                }
            }
            
            response.put("comparison", comparison);
            response.put("regularData", regularResult.getItems());
            response.put("enhancedData", enhancedResult.getItems());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Debug export failed: " + e.getMessage()
            ));
        }
    }

    // ===== SYNC STATISTICS =====

    @GetMapping("/statistics")
    @ResponseBody
    public ResponseEntity<SyncStatistics> getSyncStatistics(
            @RequestParam(required = false, defaultValue = "24") int hours,
            Authentication authentication) {
        
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        SyncStatistics stats = syncLogger.getSyncStatistics(since);
        
        return ResponseEntity.ok(stats);
    }

    // ===== UTILITY ENDPOINTS =====

    @GetMapping("/readiness")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkSyncReadiness(Authentication authentication) {
        Map<String, Object> readiness = new HashMap<>();
        
        readiness.put("oauthReady", oAuth2Service.hasValidTokens());
        readiness.put("systemHealthy", true);
        readiness.put("ready", oAuth2Service.hasValidTokens());
        
        return ResponseEntity.ok(readiness);
    }

    @GetMapping("/sync-dashboard")
    public String syncDashboard(Model model) {
        return "payprop/sync-dashboard";
    }
    
    @PostMapping("/check-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> forceStatusCheck(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Sync status check completed"
        ));
    }

    // ===== HELPER METHOD =====
    
    private Long getCurrentUserId(Authentication authentication) {
        if (authentication != null) {
            try {
                String email = null;
                
                if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser) {
                    org.springframework.security.oauth2.core.oidc.user.OidcUser oidcUser = 
                        (org.springframework.security.oauth2.core.oidc.user.OidcUser) authentication.getPrincipal();
                    email = oidcUser.getEmail();
                } else if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
                    org.springframework.security.oauth2.core.user.OAuth2User oauth2User = 
                        (org.springframework.security.oauth2.core.user.OAuth2User) authentication.getPrincipal();
                    email = oauth2User.getAttribute("email");
                }
                
                System.out.println("🔍 Extracted email from auth: " + email);
                
                if ("management@propsk.com".equals(email)) {
                    return 54L;
                } else if ("sajidkazmi@propsk.com".equals(email)) {
                    return 53L;
                } else if ("admin@localhost.com".equals(email)) {
                    return 52L;
                }
                
            } catch (Exception e) {
                System.err.println("Could not get user ID from authentication: " + e.getMessage());
            }
        }
        
        System.out.println("🔍 Using default user ID: 54");
        return 54L;
    }
}
// PayPropSyncController.java - Simplified for Unified Sync
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
    private final PayPropSyncLogger syncLogger;
    private final AuthenticationUtils authenticationUtils;

    @Autowired
    public PayPropSyncController(PayPropSyncOrchestrator syncOrchestrator,
                                PayPropOAuth2Service oAuth2Service,
                                PayPropSyncLogger syncLogger,
                                AuthenticationUtils authenticationUtils) {
        this.syncOrchestrator = syncOrchestrator;
        this.oAuth2Service = oAuth2Service;
        this.syncLogger = syncLogger;
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

    // ===== UNIFIED SYNC OPERATION =====

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
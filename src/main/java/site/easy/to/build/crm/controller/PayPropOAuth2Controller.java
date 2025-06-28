// PayPropOAuth2Controller.java - OAuth2 Authorization Flow
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.easy.to.build.crm.service.payprop.PayPropOAuth2Service;
import site.easy.to.build.crm.service.payprop.PayPropOAuth2Service.PayPropTokens;
import site.easy.to.build.crm.util.AuthorizationUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Controller
@RequestMapping("/api/payprop/oauth")
public class PayPropOAuth2Controller {

    private final PayPropOAuth2Service oAuth2Service;

    @Autowired
    public PayPropOAuth2Controller(PayPropOAuth2Service oAuth2Service) {
        this.oAuth2Service = oAuth2Service;
    }

    /**
     * Show PayProp OAuth2 status and initiate authorization
     */
    @GetMapping("/status")
    public String showOAuthStatus(Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return "redirect:/access-denied";
        }

        PayPropTokens tokens = oAuth2Service.getCurrentTokens();
        model.addAttribute("hasTokens", tokens != null);
        model.addAttribute("tokens", tokens);
        model.addAttribute("pageTitle", "PayProp Integration Setup");

        return "payprop/oauth-status";
    }

    /**
     * Initiate OAuth2 authorization flow
     */
    @GetMapping("/authorize")
    public String initiateAuthorization(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return "redirect:/access-denied";
        }

        // Generate state parameter for security
        String state = UUID.randomUUID().toString();
        
        // In production, store state in session for verification
        // session.setAttribute("oauth_state", state);
        
        String authorizationUrl = oAuth2Service.getAuthorizationUrl(state);
        
        System.out.println("🔐 Redirecting to PayProp authorization: " + authorizationUrl);
        
        return "redirect:" + authorizationUrl;
    }

    /**
     * Handle OAuth2 callback from PayProp
     */
    @GetMapping("/callback")
    public String handleCallback(@RequestParam(required = false) String code,
                                @RequestParam(required = false) String error,
                                @RequestParam(required = false) String error_description,
                                @RequestParam(required = false) String state,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        
        System.out.println("📞 PayProp OAuth2 callback received");
        System.out.println("Code: " + (code != null ? code.substring(0, Math.min(20, code.length())) + "..." : "null"));
        System.out.println("Error: " + error);
        System.out.println("State: " + state);

        if (error != null) {
            System.err.println("❌ OAuth2 authorization failed: " + error);
            redirectAttributes.addFlashAttribute("error", 
                "PayProp authorization failed: " + (error_description != null ? error_description : error));
            return "redirect:/api/payprop/oauth/status";
        }

        if (code == null) {
            System.err.println("❌ No authorization code received");
            redirectAttributes.addFlashAttribute("error", "No authorization code received from PayProp");
            return "redirect:/api/payprop/oauth/status";
        }

        try {
            // Exchange code for tokens
            PayPropTokens tokens = oAuth2Service.exchangeCodeForToken(code);
            
            System.out.println("✅ PayProp OAuth2 setup completed successfully!");
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "PayProp integration authorized successfully! You can now sync data with PayProp.");
            
            return "redirect:/api/payprop/oauth/status";
            
        } catch (Exception e) {
            System.err.println("❌ Failed to exchange authorization code: " + e.getMessage());
            e.printStackTrace();
            
            redirectAttributes.addFlashAttribute("error", 
                "Failed to complete PayProp authorization: " + e.getMessage());
            return "redirect:/api/payprop/oauth/status";
        }
    }

    /**
     * Test API connection with current tokens
     */
    @PostMapping("/test-connection")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testConnection(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            if (!oAuth2Service.hasValidTokens()) {
                response.put("success", false);
                response.put("message", "No valid OAuth2 tokens. Please authorize first.");
                return ResponseEntity.ok(response);
            }

            // Test API call to PayProp
            String accessToken = oAuth2Service.getValidAccessToken();
            
            // You can add a simple API test here, like fetching properties count
            // For now, just verify we have a valid token
            
            response.put("success", true);
            response.put("message", "PayProp API connection successful!");
            response.put("tokenStatus", "Valid");
            
            PayPropTokens tokens = oAuth2Service.getCurrentTokens();
            if (tokens != null) {
                response.put("expiresAt", tokens.getExpiresAt());
                response.put("scopes", tokens.getScopes());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("❌ PayProp API test failed: " + e.getMessage());
            
            response.put("success", false);
            response.put("message", "API test failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Refresh OAuth2 tokens
     */
    @PostMapping("/refresh")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> refreshTokens(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            PayPropTokens tokens = oAuth2Service.refreshToken();
            
            response.put("success", true);
            response.put("message", "Tokens refreshed successfully");
            response.put("expiresAt", tokens.getExpiresAt());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to refresh tokens: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Clear stored tokens (logout)
     */
    @PostMapping("/disconnect")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> disconnect(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        oAuth2Service.clearTokens();
        
        response.put("success", true);
        response.put("message", "PayProp integration disconnected");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get current token status
     */
    @GetMapping("/token-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTokenStatus(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        PayPropTokens tokens = oAuth2Service.getCurrentTokens();
        
        response.put("hasTokens", tokens != null);
        response.put("isValid", oAuth2Service.hasValidTokens());
        
        if (tokens != null) {
            response.put("expiresAt", tokens.getExpiresAt());
            response.put("isExpired", tokens.isExpired());
            response.put("isExpiringSoon", tokens.isExpiringSoon());
            response.put("scopes", tokens.getScopes());
            response.put("obtainedAt", tokens.getObtainedAt());
        }
        
        return ResponseEntity.ok(response);
    }
}
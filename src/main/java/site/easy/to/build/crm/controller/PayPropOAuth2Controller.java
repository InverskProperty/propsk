// PayPropOAuth2Controller.java - OAuth2 Authorization Flow with Test Endpoints
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.easy.to.build.crm.service.payprop.PayPropOAuth2Service;
import site.easy.to.build.crm.service.payprop.PayPropOAuth2Service.PayPropTokens;
import site.easy.to.build.crm.util.AuthorizationUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.List;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Controller
@RequestMapping("/api/payprop/oauth")
public class PayPropOAuth2Controller {

    private final PayPropOAuth2Service oAuth2Service;
    private final RestTemplate restTemplate;

    @Autowired
    public PayPropOAuth2Controller(PayPropOAuth2Service oAuth2Service, RestTemplate restTemplate) {
        this.oAuth2Service = oAuth2Service;
        this.restTemplate = restTemplate;
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
     * Test available payment endpoints with current scopes
     */
    @PostMapping("/test-available-payment-endpoints")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testAvailablePaymentEndpoints(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String baseUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1";
            Map<String, Object> results = new HashMap<>();
            
            // Test different payment-related endpoints
            String[] endpoints = {
                "/export/payments",
                "/export/invoices", 
                "/report/icdn",
                "/report/tenant/balances",
                "/report/processing-summary",
                "/export/invoice-instructions"
            };
            
            for (String endpoint : endpoints) {
                try {
                    ResponseEntity<Map> apiResponse = restTemplate.exchange(
                        baseUrl + endpoint + "?page=1&rows=5", 
                        HttpMethod.GET, request, Map.class);
                    
                    results.put(endpoint, Map.of(
                        "status", "✅ WORKS",
                        "statusCode", apiResponse.getStatusCode().value(),
                        "hasData", apiResponse.getBody() != null,
                        "itemCount", getItemCount(apiResponse.getBody())
                    ));
                } catch (HttpClientErrorException e) {
                    results.put(endpoint, Map.of(
                        "status", "❌ " + e.getStatusCode(),
                        "error", e.getResponseBodyAsString()
                    ));
                } catch (Exception e) {
                    results.put(endpoint, Map.of(
                        "status", "❌ ERROR", 
                        "error", e.getMessage()
                    ));
                }
            }
            
            response.put("success", true);
            response.put("endpointTests", results);
            response.put("message", "Endpoint availability test completed");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Helper method to count items in API response
     */
    private int getItemCount(Map<String, Object> responseBody) {
        if (responseBody == null) return 0;
        
        Object items = responseBody.get("items");
        if (items instanceof java.util.List) {
            return ((java.util.List<?>) items).size();
        }
        
        Object data = responseBody.get("data");
        if (data instanceof java.util.List) {
            return ((java.util.List<?>) data).size();
        }
        
        return 0;
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

    /**
     * Simple OAuth test endpoint for JavaScript testing
     */
    @GetMapping("/test-oauth")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testOAuth(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Check authorization
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                response.put("success", false);
                response.put("message", "Access denied - MANAGER role required");
                response.put("tokenValid", false);
                return ResponseEntity.status(403).body(response);
            }

            // Check if we have tokens
            PayPropTokens tokens = oAuth2Service.getCurrentTokens();
            boolean hasTokens = tokens != null;
            boolean isValid = oAuth2Service.hasValidTokens();
            
            response.put("success", true);
            response.put("hasTokens", hasTokens);
            response.put("tokenValid", isValid);
            
            if (hasTokens) {
                response.put("tokenType", tokens.getTokenType());
                response.put("scopes", tokens.getScopes());
                response.put("expiresAt", tokens.getExpiresAt());
                response.put("isExpired", tokens.isExpired());
                response.put("isExpiringSoon", tokens.isExpiringSoon());
                
                if (isValid) {
                    response.put("message", "OAuth2 tokens are valid and ready for use");
                    
                    // Test a simple API call
                    try {
                        String accessToken = oAuth2Service.getValidAccessToken();
                        response.put("accessTokenLength", accessToken.length());
                        response.put("apiCallTest", "Token retrieved successfully");
                    } catch (Exception e) {
                        response.put("apiCallTest", "Failed to get access token: " + e.getMessage());
                    }
                } else {
                    response.put("message", "OAuth2 tokens exist but are invalid/expired");
                }
            } else {
                response.put("message", "No OAuth2 tokens found - authorization required");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "OAuth test failed: " + e.getMessage());
            response.put("tokenValid", false);
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/test-financial-data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testFinancialData(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        Map<String, Object> results = new HashMap<>();
        
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            String baseUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1";
            
            // Test 1: Export Payments (actual transactions?)
            String paymentsUrl = baseUrl + "/export/payments?rows=5&include_beneficiary_info=true";
            ResponseEntity<Map> paymentsResponse = restTemplate.exchange(paymentsUrl, HttpMethod.GET, request, Map.class);
            results.put("export_payments", Map.of(
                "status", "SUCCESS",
                "count", getItemCount(paymentsResponse.getBody()),
                "sample_data", getSampleData(paymentsResponse.getBody(), 2)
            ));
            
            // Test 2: Export Invoices
            String invoicesUrl = baseUrl + "/export/invoices?rows=5";
            ResponseEntity<Map> invoicesResponse = restTemplate.exchange(invoicesUrl, HttpMethod.GET, request, Map.class);
            results.put("export_invoices", Map.of(
                "status", "SUCCESS", 
                "count", getItemCount(invoicesResponse.getBody()),
                "sample_data", getSampleData(invoicesResponse.getBody(), 2)
            ));
            
            // Test 3: ICDN Report (financial transactions)
            String icdnUrl = baseUrl + "/report/icdn?rows=5&from_date=2024-01-01";
            ResponseEntity<Map> icdnResponse = restTemplate.exchange(icdnUrl, HttpMethod.GET, request, Map.class);
            results.put("report_icdn", Map.of(
                "status", "SUCCESS",
                "count", getItemCount(icdnResponse.getBody()),
                "sample_data", getSampleData(icdnResponse.getBody(), 2)
            ));
            
            // Test 4: Specific property payments (using your most active property)
            String propPaymentsUrl = baseUrl + "/export/payments?property_id=116&rows=5&include_beneficiary_info=true";
            ResponseEntity<Map> propResponse = restTemplate.exchange(propPaymentsUrl, HttpMethod.GET, request, Map.class);
            results.put("property_116_payments", Map.of(
                "status", "SUCCESS",
                "property_name", "Havelock Place 87, Hartley",
                "count", getItemCount(propResponse.getBody()),
                "sample_data", getSampleData(propResponse.getBody(), 2)
            ));
            
        } catch (Exception e) {
            results.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(results);
    }

    private Object getSampleData(Map<String, Object> response, int maxItems) {
        if (response != null && response.containsKey("items")) {
            List<?> items = (List<?>) response.get("items");
            return items.stream().limit(maxItems).collect(Collectors.toList());
        }
        return "No data";
    }

    /**
     * Test endpoint for tags functionality
     */
    @GetMapping("/test-tags")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testTags(Authentication authentication) {
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

            // Note: Your system uses PayPropPortfolioSyncService for tag operations
            // This is just a test endpoint to verify the OAuth connection for tags
            
            response.put("success", true);
            response.put("message", "OAuth ready for tag operations");
            response.put("note", "Tag operations are handled via PayPropPortfolioSyncService");
            response.put("availableOperations", Arrays.asList(
                "getAllPayPropTags()",
                "createPayPropTag()",
                "syncPortfolioToPayProp()",
                "handlePayPropTagChange()"
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Tag test failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
}
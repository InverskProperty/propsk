// PayPropOAuth2Service.java - COMPLETE REPLACEMENT with Database Token Persistence and API Validation
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;
import site.easy.to.build.crm.entity.PayPropToken;
import site.easy.to.build.crm.repository.PayPropTokenRepository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.List;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropOAuth2Service {

    private final RestTemplate restTemplate;
    private final PayPropTokenRepository tokenRepository;
    private final Environment environment;
    
    @Value("${payprop.oauth2.client-id}")
    private String clientId;
    
    @Value("${payprop.oauth2.client-secret}")
    private String clientSecret;
    
    @Value("${payprop.oauth2.authorization-url}")
    private String authorizationUrl;
    
    @Value("${payprop.oauth2.token-url}")
    private String tokenUrl;
    
    @Value("${payprop.oauth2.redirect-uri}")
    private String redirectUri;
    
    @Value("${payprop.oauth2.scopes}")
    private String scopes;
    
    // Store tokens in memory for quick access (cache)
    private volatile PayPropTokens currentTokens;

    @Autowired
    public PayPropOAuth2Service(RestTemplate restTemplate, PayPropTokenRepository tokenRepository, Environment environment) {
        this.restTemplate = restTemplate;
        this.tokenRepository = tokenRepository;
        this.environment = environment;
        
        // Load existing tokens from database on startup
        loadTokensFromDatabase();
    }
    
    /**
     * Load tokens from database on service initialization
     */
    private void loadTokensFromDatabase() {
        System.out.println("🔄 Loading PayProp tokens from database...");
        
        Optional<PayPropToken> savedToken = tokenRepository.findMostRecentActiveToken();
        if (savedToken.isPresent()) {
            PayPropToken token = savedToken.get();
            
            // Convert database entity to in-memory tokens
            currentTokens = new PayPropTokens();
            currentTokens.setAccessToken(token.getAccessToken());
            currentTokens.setRefreshToken(token.getRefreshToken());
            currentTokens.setTokenType(token.getTokenType());
            currentTokens.setExpiresAt(token.getExpiresAt());
            currentTokens.setScopes(token.getScopes());
            currentTokens.setObtainedAt(token.getObtainedAt());
            
            System.out.println("✅ Loaded existing PayProp tokens from database");
            System.out.println("   Token expires at: " + token.getExpiresAt());
            System.out.println("   Has refresh token: " + (token.getRefreshToken() != null));
            
            // CRITICAL ADDITION: Validate tokens on startup
            try {
                if (currentTokens.isExpired()) {
                    System.out.println("🔄 Tokens expired, attempting refresh on startup...");
                    refreshToken();
                } else if (currentTokens.isExpiringSoon()) {
                    System.out.println("🔄 Tokens expiring soon, refreshing proactively...");
                    refreshToken();
                }
            } catch (Exception e) {
                System.err.println("⚠️ Token validation failed on startup: " + e.getMessage());
                System.err.println("   Tokens may need reauthorization");
            }
            
        } else {
            System.out.println("📝 No existing PayProp tokens found in database");
        }
    }
    
    /**
     * Save tokens to database
     */
    private void saveTokensToDatabase(PayPropTokens tokens, Long userId) {
        System.out.println("💾 Saving PayProp tokens to database...");
        
        // Deactivate any existing tokens
        tokenRepository.deactivateAllTokens();
        
        // Create new token entity
        PayPropToken tokenEntity = new PayPropToken();
        tokenEntity.setAccessToken(tokens.getAccessToken());
        tokenEntity.setRefreshToken(tokens.getRefreshToken());
        tokenEntity.setTokenType(tokens.getTokenType());
        tokenEntity.setExpiresAt(tokens.getExpiresAt());
        tokenEntity.setScopes(tokens.getScopes());
        tokenEntity.setObtainedAt(tokens.getObtainedAt());
        tokenEntity.setUserId(userId);
        tokenEntity.setIsActive(true);
        
        if (tokens.getObtainedAt().isBefore(LocalDateTime.now().minusMinutes(1))) {
            tokenEntity.setLastRefreshedAt(LocalDateTime.now());
        }
        
        tokenRepository.save(tokenEntity);
        System.out.println("✅ PayProp tokens saved to database with ID: " + tokenEntity.getId());
    }

    /**
     * Get the authorization URL for OAuth2 flow
     */
    public String getAuthorizationUrl(String state) {
        System.out.println("🔧 BUILDING AUTHORIZATION URL:");
        System.out.println("   Base URL: " + authorizationUrl);
        System.out.println("   Client ID: " + clientId);
        System.out.println("   Redirect URI: " + redirectUri);
        System.out.println("   Scopes: " + scopes);
        System.out.println("   State: " + state);
        
        String fullUrl = UriComponentsBuilder.fromHttpUrl(authorizationUrl)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", scopes)
                .queryParam("state", state)
                .build()
                .toUriString();
        
        System.out.println("🔗 FULL AUTHORIZATION URL: " + fullUrl);
        return fullUrl;
    }

    /**
     * Exchange authorization code for access token
     */
    public PayPropTokens exchangeCodeForToken(String authorizationCode, Long userId) throws Exception {
        System.out.println("🔄 STARTING TOKEN EXCHANGE:");
        System.out.println("   Token URL: " + tokenUrl);
        System.out.println("   Authorization Code: " + authorizationCode.substring(0, Math.min(20, authorizationCode.length())) + "...");
        System.out.println("   Client ID: " + clientId);
        System.out.println("   Redirect URI: " + redirectUri);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("grant_type", "authorization_code");
        requestBody.add("code", authorizationCode);
        requestBody.add("client_id", clientId);
        requestBody.add("client_secret", clientSecret);
        requestBody.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);

        try {
            System.out.println("🌐 MAKING TOKEN EXCHANGE REQUEST...");
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            
            System.out.println("📥 TOKEN RESPONSE:");
            System.out.println("   Status Code: " + response.getStatusCode());
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                System.out.println("   Response Body: " + responseBody);
                
                PayPropTokens tokens = new PayPropTokens();
                tokens.setAccessToken((String) responseBody.get("access_token"));
                tokens.setRefreshToken((String) responseBody.get("refresh_token"));
                tokens.setTokenType((String) responseBody.get("token_type"));
                
                Integer expiresIn = (Integer) responseBody.get("expires_in");
                if (expiresIn != null) {
                    tokens.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
                }
                
                tokens.setScopes(scopes);
                tokens.setObtainedAt(LocalDateTime.now());
                
                // Store tokens in memory
                this.currentTokens = tokens;
                
                // CRITICAL FIX: Persist tokens to database
                saveTokensToDatabase(tokens, userId);
                
                System.out.println("✅ TOKENS OBTAINED AND SAVED SUCCESSFULLY!");
                System.out.println("   Access Token: " + tokens.getAccessToken().substring(0, 20) + "...");
                System.out.println("   Token Type: " + tokens.getTokenType());
                System.out.println("   Expires At: " + tokens.getExpiresAt());
                System.out.println("   Has Refresh Token: " + (tokens.getRefreshToken() != null));
                
                return tokens;
            } else {
                System.err.println("❌ UNEXPECTED RESPONSE:");
                System.err.println("   Status: " + response.getStatusCode());
                System.err.println("   Body: " + response.getBody());
                throw new RuntimeException("Unexpected response from token endpoint");
            }
            
        } catch (HttpClientErrorException e) {
            System.err.println("❌ HTTP CLIENT ERROR:");
            System.err.println("   Status Code: " + e.getStatusCode());
            System.err.println("   Response Body: " + e.getResponseBodyAsString());
            throw new RuntimeException("PayProp token exchange failed: " + e.getResponseBodyAsString(), e);
            
        } catch (Exception e) {
            System.err.println("❌ UNEXPECTED ERROR:");
            System.err.println("   Type: " + e.getClass().getSimpleName());
            System.err.println("   Message: " + e.getMessage());
            throw new RuntimeException("Unexpected error during token exchange", e);
        }
    }

    /**
     * Refresh access token using refresh token
     */
    public PayPropTokens refreshToken() throws Exception {
        // First try memory, then database
        if (currentTokens == null || currentTokens.getRefreshToken() == null) {
            System.out.println("🔄 No tokens in memory, checking database...");
            loadTokensFromDatabase();
        }
        
        if (currentTokens == null || currentTokens.getRefreshToken() == null) {
            throw new IllegalStateException("No refresh token available");
        }

        System.out.println("🔄 REFRESHING ACCESS TOKEN:");
        System.out.println("   Token URL: " + tokenUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("grant_type", "refresh_token");
        requestBody.add("refresh_token", currentTokens.getRefreshToken());
        requestBody.add("client_id", clientId);
        requestBody.add("client_secret", clientSecret);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            
            System.out.println("📥 REFRESH RESPONSE:");
            System.out.println("   Status Code: " + response.getStatusCode());
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                System.out.println("   Response Body: " + responseBody);
                
                currentTokens.setAccessToken((String) responseBody.get("access_token"));
                
                String newRefreshToken = (String) responseBody.get("refresh_token");
                if (newRefreshToken != null) {
                    currentTokens.setRefreshToken(newRefreshToken);
                }
                
                Integer expiresIn = (Integer) responseBody.get("expires_in");
                if (expiresIn != null) {
                    currentTokens.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
                }
                
                // CRITICAL FIX: Update database with refreshed tokens
                Optional<PayPropToken> existingToken = tokenRepository.findMostRecentActiveToken();
                if (existingToken.isPresent()) {
                    PayPropToken token = existingToken.get();
                    token.setAccessToken(currentTokens.getAccessToken());
                    if (newRefreshToken != null) {
                        token.setRefreshToken(newRefreshToken);
                    }
                    token.setExpiresAt(currentTokens.getExpiresAt());
                    token.setLastRefreshedAt(LocalDateTime.now());
                    tokenRepository.save(token);
                    System.out.println("✅ Updated database with refreshed tokens");
                } else {
                    // Save as new token if none exists
                    saveTokensToDatabase(currentTokens, null);
                }
                
                System.out.println("✅ TOKENS REFRESHED SUCCESSFULLY!");
                return currentTokens;
            }
            
            throw new RuntimeException("Failed to refresh token");
            
        } catch (HttpClientErrorException e) {
            System.err.println("❌ REFRESH TOKEN ERROR:");
            System.err.println("   Status: " + e.getStatusCode());
            System.err.println("   Body: " + e.getResponseBodyAsString());
            
            // If refresh token is invalid, clear from database
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                tokenRepository.deactivateAllTokens();
                currentTokens = null;
                System.err.println("🔄 Cleared invalid tokens from database");
            }
            
            throw new RuntimeException("Failed to refresh token: " + e.getResponseBodyAsString(), e);
        }
    }

    /**
     * Get valid access token (refresh if needed)
     */
    public String getValidAccessToken() throws Exception {
        // First check memory, then database
        if (currentTokens == null) {
            System.out.println("🔄 No tokens in memory, loading from database...");
            loadTokensFromDatabase();
        }
        
        if (currentTokens == null) {
            throw new IllegalStateException("No OAuth2 tokens available. Please complete authorization first.");
        }

        // Check if token is expired or expiring soon (5 minutes buffer)
        if (currentTokens.getExpiresAt() != null && 
            currentTokens.getExpiresAt().isBefore(LocalDateTime.now().plusMinutes(5))) {
            
            System.out.println("🔄 Access token expiring soon, refreshing...");
            refreshToken();
        }

        return currentTokens.getAccessToken();
    }

    /**
     * Create authorized HTTP headers for PayProp API calls
     */
    public HttpHeaders createAuthorizedHeaders() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Use Bearer token as per PayProp API specification
        String accessToken = getValidAccessToken();
        headers.set("Authorization", "Bearer " + accessToken);
        
        // Add User-Agent header (good practice for API calls)
        headers.set("User-Agent", "CRM-System/1.0");
        
        return headers;
    }

    /**
     * Check if we have valid tokens that actually work with PayProp API
     */
    public boolean hasValidTokens() {
        // First check if tokens exist and are not expired
        boolean hasTokens = hasStoredTokens();
        if (!hasTokens) {
            System.out.println("❌ No valid tokens found");
            return false;
        }
        
        // CRITICAL ADDITION: Test if tokens actually work with PayProp API
        try {
            boolean apiConnected = testConnection();
            System.out.println("🔍 PayProp API connection test: " + (apiConnected ? "SUCCESS" : "FAILED"));
            return apiConnected;
        } catch (Exception e) {
            System.err.println("❌ PayProp API connection test failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if tokens exist and are not expired (internal method)
     */
    private boolean hasStoredTokens() {
        // Check memory first
        if (currentTokens != null && currentTokens.getAccessToken() != null) {
            boolean notExpired = currentTokens.getExpiresAt() == null || 
                               currentTokens.getExpiresAt().isAfter(LocalDateTime.now());
            if (notExpired) {
                return true;
            }
        }
        
        // Check database
        Optional<PayPropToken> savedToken = tokenRepository.findMostRecentActiveToken();
        if (savedToken.isPresent()) {
            PayPropToken token = savedToken.get();
            boolean hasTokens = token.getAccessToken() != null;
            boolean notExpired = token.getExpiresAt() == null || 
                               token.getExpiresAt().isAfter(LocalDateTime.now());
            
            if (hasTokens && notExpired) {
                // Load to memory for faster access
                loadTokensFromDatabase();
                return true;
            }
        }
        
        return false;
    }

    /**
     * CRITICAL ADDITION: Test actual API connection using PayProp's /meta/me endpoint
     */
    public boolean testConnection() throws Exception {
        try {
            // Use YOUR staging environment for connection testing
            String baseUrl = environment.getProperty("payprop.api.base-url", "https://ukapi.staging.payprop.com/api/agency/v1.1");
            String apiUrl = baseUrl + "/meta/me";
            
            HttpHeaders headers = createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                apiUrl, 
                HttpMethod.GET, 
                request, 
                Map.class
            );
            
            boolean success = response.getStatusCode() == HttpStatus.OK;
            
            if (success && response.getBody() != null) {
                Map<String, Object> metaInfo = response.getBody();
                System.out.println("✅ PayProp API connection verified!");
                
                // Log useful connection info
                if (metaInfo.containsKey("user")) {
                    Map<String, Object> user = (Map<String, Object>) metaInfo.get("user");
                    System.out.println("   Connected as: " + user.get("full_name") + " (" + user.get("email") + ")");
                    System.out.println("   User type: " + user.get("type"));
                    System.out.println("   Is admin: " + user.get("is_admin"));
                }
                
                if (metaInfo.containsKey("agency")) {
                    Map<String, Object> agency = (Map<String, Object>) metaInfo.get("agency");
                    System.out.println("   Agency: " + agency.get("name"));
                }
                
                if (metaInfo.containsKey("scopes")) {
                    List<String> scopes = (List<String>) metaInfo.get("scopes");
                    System.out.println("   Available scopes: " + String.join(", ", scopes));
                }
            }
            
            return success;
            
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                System.err.println("🔄 PayProp tokens expired (401), attempting refresh...");
                
                try {
                    refreshToken();
                    // Retry the connection test after refresh
                    return testConnection();
                } catch (Exception refreshError) {
                    System.err.println("❌ Token refresh failed: " + refreshError.getMessage());
                    // Clear invalid tokens
                    clearTokens();
                    return false;
                }
            } else {
                System.err.println("❌ PayProp API error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
                return false;
            }
        } catch (Exception e) {
            System.err.println("❌ PayProp connection test failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get connection status for UI display
     */
    public String getConnectionStatus() {
        try {
            if (!hasStoredTokens()) {
                return "NOT_AUTHORIZED";
            }
            
            if (testConnection()) {
                return "CONNECTED";
            } else {
                return "CONNECTION_FAILED";
            }
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Get detailed connection info for debugging and UI display
     */
    public Map<String, Object> getConnectionInfo() {
        Map<String, Object> info = new HashMap<>();
        
        try {
            info.put("payPropEnabled", true); // Since this service only exists when enabled
            info.put("hasMemoryTokens", currentTokens != null);
            
            Optional<PayPropToken> dbToken = tokenRepository.findMostRecentActiveToken();
            info.put("hasDatabaseTokens", dbToken.isPresent());
            
            if (dbToken.isPresent()) {
                PayPropToken token = dbToken.get();
                info.put("tokenExpiresAt", token.getExpiresAt());
                info.put("tokenObtainedAt", token.getObtainedAt());
                info.put("hasRefreshToken", token.getRefreshToken() != null);
                info.put("tokenExpired", token.getExpiresAt() != null && 
                        token.getExpiresAt().isBefore(LocalDateTime.now()));
            }
            
            // Test actual API connection
            String status = getConnectionStatus();
            info.put("connectionStatus", status);
            
            // If connected, get user info
            if ("CONNECTED".equals(status)) {
                try {
                    Map<String, Object> metaInfo = getUserMetaInfo();
                    info.put("userInfo", metaInfo);
                } catch (Exception e) {
                    info.put("userInfoError", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            info.put("error", e.getMessage());
        }
        
        return info;
    }

    public String getAccessToken() {
        try {
            return getValidAccessToken();
        } catch (Exception e) {
            System.err.println("❌ Failed to get access token: " + e.getMessage());
            return null;
        }
    }
    
    // Also add this helper method that was referenced but missing:
    private PayPropToken getValidToken() {
        Optional<PayPropToken> savedToken = tokenRepository.findMostRecentActiveToken();
        if (savedToken.isPresent()) {
            PayPropToken token = savedToken.get();
            boolean notExpired = token.getExpiresAt() == null || 
                            token.getExpiresAt().isAfter(LocalDateTime.now());
            if (notExpired) {
                return token;
            }
        }
        return null;
    }

    /**
     * Get PayProp user meta information (useful for debugging and display)
     */
    public Map<String, Object> getUserMetaInfo() throws Exception {
        String baseUrl = environment.getProperty("payprop.api.base-url", "https://ukapi.staging.payprop.com/api/agency/v1.1");
        String apiUrl = baseUrl + "/meta/me";
        
        HttpHeaders headers = createAuthorizedHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(
            apiUrl, 
            HttpMethod.GET, 
            request, 
            Map.class
        );
        
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            return response.getBody();
        }
        
        throw new RuntimeException("Failed to get user meta info from PayProp");
    }

    /**
     * Get current token info for debugging
     */
    public PayPropTokens getCurrentTokens() {
        if (currentTokens == null) {
            loadTokensFromDatabase();
        }
        return currentTokens;
    }

    /**
     * Clear stored tokens (for logout)
     */
    public void clearTokens() {
        this.currentTokens = null;
        tokenRepository.deactivateAllTokens();
        System.out.println("🔓 PayProp OAuth2 tokens cleared from memory and database");
    }

    // Token data class (unchanged)
    public static class PayPropTokens {
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private LocalDateTime expiresAt;
        private String scopes;
        private LocalDateTime obtainedAt;

        // Getters and setters
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

        public String getTokenType() { return tokenType; }
        public void setTokenType(String tokenType) { this.tokenType = tokenType; }

        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

        public String getScopes() { return scopes; }
        public void setScopes(String scopes) { this.scopes = scopes; }

        public LocalDateTime getObtainedAt() { return obtainedAt; }
        public void setObtainedAt(LocalDateTime obtainedAt) { this.obtainedAt = obtainedAt; }

        public boolean isExpired() {
            return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
        }

        public boolean isExpiringSoon() {
            return expiresAt != null && expiresAt.isBefore(LocalDateTime.now().plusMinutes(5));
        }
    }
}
// PayPropOAuth2Service.java - OAuth2 Authentication for PayProp API
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.Map;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropOAuth2Service {

    private final RestTemplate restTemplate;
    
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
    
    // Store tokens in memory (in production, use a database)
    private volatile PayPropTokens currentTokens;

    public PayPropOAuth2Service(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Get the authorization URL for OAuth2 flow
     */
    public String getAuthorizationUrl(String state) {
        return UriComponentsBuilder.fromHttpUrl(authorizationUrl)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", scopes)
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    /**
     * Exchange authorization code for access token
     */
    public PayPropTokens exchangeCodeForToken(String authorizationCode) throws Exception {
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
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
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
                
                // Store tokens
                this.currentTokens = tokens;
                
                System.out.println("✅ PayProp OAuth2 tokens obtained successfully!");
                System.out.println("Access Token: " + tokens.getAccessToken().substring(0, 20) + "...");
                System.out.println("Expires At: " + tokens.getExpiresAt());
                
                return tokens;
            }
            
            throw new RuntimeException("Failed to exchange authorization code for token");
            
        } catch (Exception e) {
            System.err.println("❌ Failed to exchange authorization code: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Refresh access token using refresh token
     */
    public PayPropTokens refreshToken() throws Exception {
        if (currentTokens == null || currentTokens.getRefreshToken() == null) {
            throw new IllegalStateException("No refresh token available");
        }

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
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                currentTokens.setAccessToken((String) responseBody.get("access_token"));
                
                String newRefreshToken = (String) responseBody.get("refresh_token");
                if (newRefreshToken != null) {
                    currentTokens.setRefreshToken(newRefreshToken);
                }
                
                Integer expiresIn = (Integer) responseBody.get("expires_in");
                if (expiresIn != null) {
                    currentTokens.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
                }
                
                System.out.println("✅ PayProp tokens refreshed successfully!");
                
                return currentTokens;
            }
            
            throw new RuntimeException("Failed to refresh token");
            
        } catch (Exception e) {
            System.err.println("❌ Failed to refresh token: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Get valid access token (refresh if needed)
     */
    public String getValidAccessToken() throws Exception {
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
        headers.setBearerAuth(getValidAccessToken());
        return headers;
    }

    /**
     * Check if we have valid tokens
     */
    public boolean hasValidTokens() {
        return currentTokens != null && currentTokens.getAccessToken() != null &&
               (currentTokens.getExpiresAt() == null || 
                currentTokens.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    /**
     * Get current token info for debugging
     */
    public PayPropTokens getCurrentTokens() {
        return currentTokens;
    }

    /**
     * Clear stored tokens (for logout)
     */
    public void clearTokens() {
        this.currentTokens = null;
        System.out.println("🔓 PayProp OAuth2 tokens cleared");
    }

    // Token data class
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
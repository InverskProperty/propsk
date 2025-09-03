package site.easy.to.build.crm.service.user;

import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.repository.OAuthUserRepository;
import site.easy.to.build.crm.repository.UserRepository;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.util.MemoryDiagnostics;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;

@Service
public class OAuthUserServiceImpl implements OAuthUserService{

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    private static final String APPLICATION_NAME = "Your-Application-Name";
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static NetHttpTransport HTTP_TRANSPORT;
    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            HTTP_TRANSPORT = null;
        }
    }

    @Autowired
    OAuthUserRepository oAuthUserRepository;

    @Autowired
    UserRepository userRepository;

    @Override
    public OAuthUser findById(Long id) {
        return oAuthUserRepository.findById(id.intValue());
    }

    @Override
    public OAuthUser findBtEmail(String email) {
        return oAuthUserRepository.findByEmail(email);
    }

    @Override
    public OAuthUser getOAuthUserByUser(User user) {
        return oAuthUserRepository.getOAuthUserByUser(user);
    }

    /**
     * Check if the OAuth user has valid tokens for API access
     */
    public boolean hasValidTokens(OAuthUser oauthUser) {
        System.out.println("🔍 DEBUG: Checking OAuth token validity...");
        
        if (oauthUser == null) {
            System.out.println("❌ OAuth user is null");
            return false;
        }
        
        System.out.println("   OAuth user email: " + oauthUser.getEmail());
        System.out.println("   Access token present: " + (oauthUser.getAccessToken() != null));
        System.out.println("   Access token expiration: " + oauthUser.getAccessTokenExpiration());
        System.out.println("   Refresh token present: " + (oauthUser.getRefreshToken() != null));
        
        // Check if we have an access token that's still valid
        if (oauthUser.getAccessToken() != null && oauthUser.getAccessTokenExpiration() != null) {
            boolean isValid = Instant.now().isBefore(oauthUser.getAccessTokenExpiration());
            System.out.println("   Access token is valid: " + isValid);
            if (isValid) {
                System.out.println("✅ Access token is still valid, no refresh needed");
                return true;
            } else {
                System.out.println("⚠️ Access token has expired");
            }
        }
        
        // Check if we have a refresh token to get a new access token
        boolean hasRefreshToken = oauthUser.getRefreshToken() != null && 
               !oauthUser.getRefreshToken().isEmpty() && 
               !"N/A".equals(oauthUser.getRefreshToken());
        
        System.out.println("   Has valid refresh token: " + hasRefreshToken);
        if (!hasRefreshToken) {
            System.out.println("❌ No valid refresh token available - user needs to re-authenticate");
        }
        
        return hasRefreshToken;
    }
    
    @Override
    @ConditionalOnExpression("!T(site.easy.to.build.crm.util.StringUtils).isEmpty('${spring.security.oauth2.client.registration.google.client-id:}')")
    public String refreshAccessTokenIfNeeded(OAuthUser oauthUser) {
        MemoryDiagnostics.logMemoryUsage("refreshAccessTokenIfNeeded START for " + oauthUser.getEmail());
        System.out.println("🔄 Token refresh attempt for user: " + oauthUser.getEmail());
        System.out.println("   Current time: " + Instant.now());
        System.out.println("   Token expires at: " + oauthUser.getAccessTokenExpiration());
        
        Instant now = Instant.now();
        
        // Check if token is still valid
        if (oauthUser.getAccessTokenExpiration() != null && 
            now.isBefore(oauthUser.getAccessTokenExpiration())) {
            System.out.println("✅ Token still valid, no refresh needed");
            return oauthUser.getAccessToken();
        }
        
        System.out.println("⚠️ Token expired, attempting refresh...");
        
        // Check refresh token
        if (oauthUser.getRefreshToken() == null || 
            oauthUser.getRefreshToken().isEmpty() || 
            "N/A".equals(oauthUser.getRefreshToken())) {
            System.err.println("❌ NO REFRESH TOKEN AVAILABLE!");
            throw new RuntimeException("No refresh token available. User must re-authenticate.");
        }
        
        try {
            System.out.println("🔄 Making refresh request to Google...");
            MemoryDiagnostics.logMemoryUsage("Before GoogleRefreshTokenRequest Creation");
            
            GoogleTokenResponse tokenResponse = new GoogleRefreshTokenRequest(
                HTTP_TRANSPORT, // CRITICAL: Use static transport instead of new NetHttpTransport()
                GsonFactory.getDefaultInstance(),
                oauthUser.getRefreshToken(),
                clientId,
                clientSecret
            ).execute();
            
            MemoryDiagnostics.logMemoryUsage("After GoogleRefreshTokenRequest Execute");
            
            String newAccessToken = tokenResponse.getAccessToken();
            long expiresIn = tokenResponse.getExpiresInSeconds();
            Instant expiresAt = Instant.now().plusSeconds(expiresIn);
            
            System.out.println("✅ TOKEN REFRESH SUCCESSFUL!");
            System.out.println("   New token expires at: " + expiresAt);
            
            // Update in database
            oauthUser.setAccessToken(newAccessToken);
            oauthUser.setAccessTokenExpiration(expiresAt);
            oauthUser.setAccessTokenIssuedAt(Instant.now());
            
            if (tokenResponse.getRefreshToken() != null) {
                System.out.println("✅ New refresh token received");
                oauthUser.setRefreshToken(tokenResponse.getRefreshToken());
            }
            
            MemoryDiagnostics.logMemoryUsage("Before Database Save");
            oAuthUserRepository.save(oauthUser);
            System.out.println("✅ Token saved to database");
            
            MemoryDiagnostics.logMemoryUsage("refreshAccessTokenIfNeeded SUCCESS COMPLETE");
            return newAccessToken;
            
        } catch (IOException e) {
            System.err.println("❌ TOKEN REFRESH FAILED: " + e.getMessage());
            e.printStackTrace();
            
            MemoryDiagnostics.logMemoryUsage("After Token Refresh FAILURE");
            
            if (e.getMessage().contains("invalid_grant")) {
                System.err.println("❌ REFRESH TOKEN INVALID - User must re-authenticate!");
                // Don't delete the user, just clear tokens
                oauthUser.setAccessToken(null);
                oauthUser.setRefreshToken(null);
                oAuthUserRepository.save(oauthUser);
            }
            
            throw new RuntimeException("Failed to refresh access token: " + e.getMessage(), e);
        }
    }

    @Override
    @ConditionalOnExpression("!T(site.easy.to.build.crm.util.StringUtils).isEmpty('${spring.security.oauth2.client.registration.google.client-id:}')")
    public void revokeAccess(OAuthUser oAuthUser) {
        try {
            // CRITICAL: Use static transport to prevent memory leak
            HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory();

            GenericUrl url = new GenericUrl("https://accounts.google.com/o/oauth2/revoke");
            url.set("token", oAuthUser.getAccessToken());

            HttpRequest request = requestFactory.buildGetRequest(url);
            request.execute();
        } catch (HttpResponseException e) {
            // Handle the error response if needed
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void save(OAuthUser oAuthUser, User user) {
        oAuthUser.setUser(user);
        user.setOauthUser(oAuthUser);
        oAuthUserRepository.save(oAuthUser);
    }

    @Override
    public void save(OAuthUser oAuthUser) {
        oAuthUserRepository.save(oAuthUser);
    }

    @Override
    public void deleteById(Long id) {
        // Implementation if needed
    }

    /**
     * FIXED: Handle null refresh tokens properly
     * Google doesn't always provide refresh tokens, especially for returning users
     */
    public void updateOAuthUserTokens(OAuthUser oAuthUser, OAuth2AccessToken oAuth2AccessToken, OAuth2RefreshToken oAuth2RefreshToken) {
        System.out.println("🔄 Updating OAuth user tokens...");
        
        // Always set access token fields
        oAuthUser.setAccessToken(oAuth2AccessToken.getTokenValue());
        oAuthUser.setAccessTokenIssuedAt(oAuth2AccessToken.getIssuedAt());
        oAuthUser.setAccessTokenExpiration(oAuth2AccessToken.getExpiresAt());
        
        System.out.println("✅ Access token updated - expires at: " + oAuth2AccessToken.getExpiresAt());

        // Handle refresh token - Google doesn't always provide one
        if(oAuth2RefreshToken != null) {
            System.out.println("✅ Refresh token provided by Google");
            oAuthUser.setRefreshToken(oAuth2RefreshToken.getTokenValue());
            oAuthUser.setRefreshTokenIssuedAt(oAuth2RefreshToken.getIssuedAt());
            oAuthUser.setRefreshTokenExpiration(oAuth2RefreshToken.getExpiresAt());
            System.out.println("✅ Refresh token updated - expires at: " + oAuth2RefreshToken.getExpiresAt());
        } else {
            System.out.println("⚠️ No refresh token provided by Google");
            System.out.println("   This is normal for returning users - Google only provides refresh tokens on first authorization");
            System.out.println("   Existing refresh token will be preserved if available");
            
            // Don't overwrite existing refresh token with null
            // Only set to null if there was no existing refresh token
            if (oAuthUser.getRefreshToken() == null) {
                System.out.println("   No existing refresh token found - setting fields to null");
                oAuthUser.setRefreshToken(null);
                oAuthUser.setRefreshTokenIssuedAt(null);
                oAuthUser.setRefreshTokenExpiration(null);
            } else {
                System.out.println("   Preserving existing refresh token");
            }
        }
        
        System.out.println("🔄 OAuth token update complete");
    }
}
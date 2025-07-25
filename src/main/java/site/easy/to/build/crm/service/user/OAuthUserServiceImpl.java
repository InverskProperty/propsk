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

    @Override
    @ConditionalOnExpression("!T(site.easy.to.build.crm.util.StringUtils).isEmpty('${spring.security.oauth2.client.registration.google.client-id:}')")
    public String refreshAccessTokenIfNeeded(OAuthUser oauthUser) {
        Instant now = Instant.now();
        if (now.isBefore(oauthUser.getAccessTokenExpiration())) {
            return oauthUser.getAccessToken();
        }

        // FIXED: Check if refresh token is available before attempting refresh
        if (oauthUser.getRefreshToken() == null || oauthUser.getRefreshToken().isEmpty() || "N/A".equals(oauthUser.getRefreshToken())) {
            System.err.println("❌ Cannot refresh access token - no valid refresh token available");
            throw new RuntimeException("Cannot refresh access token - no valid refresh token available. User needs to re-authenticate.");
        }

        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        // Create a new GoogleTokenResponse
        GoogleTokenResponse tokenResponse;
        try {
            tokenResponse = new GoogleRefreshTokenRequest(
                    new NetHttpTransport(),
                    jsonFactory,
                    oauthUser.getRefreshToken(),
                    clientId,
                    clientSecret)
                    .execute();
            String newAccessToken = tokenResponse.getAccessToken();
            long expiresIn = tokenResponse.getExpiresInSeconds();
            Instant expiresAt = Instant.now().plusSeconds(expiresIn);

            oauthUser.setAccessToken(newAccessToken);
            oauthUser.setAccessTokenExpiration(expiresAt);

            // Update refresh token if a new one is provided
            if (tokenResponse.getRefreshToken() != null) {
                oauthUser.setRefreshToken(tokenResponse.getRefreshToken());
                System.out.println("✅ New refresh token received and stored");
            }

            oAuthUserRepository.save(oauthUser);
            System.out.println("✅ Access token refreshed successfully");
            
        } catch (IOException e) {
            System.err.println("❌ Failed to refresh access token: " + e.getMessage());
            throw new RuntimeException("Failed to refresh access token: " + e.getMessage(), e);
        }

        return oauthUser.getAccessToken();
    }

    @Override
    @ConditionalOnExpression("!T(site.easy.to.build.crm.util.StringUtils).isEmpty('${spring.security.oauth2.client.registration.google.client-id:}')")
    public void revokeAccess(OAuthUser oAuthUser) {
        try {
            final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            HttpRequestFactory requestFactory = httpTransport.createRequestFactory();

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
package site.easy.to.build.crm.google.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.google.api.client.http.HttpResponseException;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.service.user.OAuthUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.function.Function;

@Service
public class TokenAwareApiExecutor {
    private static final Logger log = LoggerFactory.getLogger(TokenAwareApiExecutor.class);
    private static final int MAX_RETRIES = 3;
    private static final long TOKEN_REFRESH_THRESHOLD_SECONDS = 300; // 5 minutes
    
    @Autowired
    private OAuthUserService oAuthUserService;
    
    /**
     * Execute API operation with automatic token refresh on expiry
     */
    public <T> T executeWithTokenRefresh(OAuthUser oAuthUser, 
                                         Function<String, T> operation) 
                                         throws Exception {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                // ALWAYS refresh if expired or expiring soon
                if (isTokenExpiringSoon(oAuthUser)) {
                    log.info("Token expired/expiring for user {}, refreshing now", 
                            oAuthUser.getEmail());
                    forceTokenRefresh(oAuthUser);
                }
                
                String accessToken = oAuthUser.getAccessToken();
                return operation.apply(accessToken);
                
            } catch (HttpResponseException e) {
                if (e.getStatusCode() == 401 && attempt < MAX_RETRIES - 1) {
                    log.warn("Token expired during operation for user {}, attempt {}/{}", 
                            oAuthUser.getEmail(), attempt + 1, MAX_RETRIES);
                    forceTokenRefresh(oAuthUser);
                    continue;
                }
                throw e;
            }
        }
        throw new RuntimeException("Failed after " + MAX_RETRIES + " attempts");
    }
    
    private boolean isTokenExpiringSoon(OAuthUser oAuthUser) {
        if (oAuthUser.getAccessTokenExpiration() == null) {
            return true;
        }
        Instant expiryThreshold = Instant.now().plusSeconds(TOKEN_REFRESH_THRESHOLD_SECONDS);
        return oAuthUser.getAccessTokenExpiration().isBefore(expiryThreshold);
    }
    
    private void forceTokenRefresh(OAuthUser oAuthUser) throws Exception {
        // Force refresh by setting expiration to past
        oAuthUser.setAccessTokenExpiration(Instant.now().minusSeconds(1));
        String newToken = oAuthUserService.refreshAccessTokenIfNeeded(oAuthUser);
        oAuthUser.setAccessToken(newToken);
        log.info("Token refreshed successfully for user {}", oAuthUser.getEmail());
    }
}
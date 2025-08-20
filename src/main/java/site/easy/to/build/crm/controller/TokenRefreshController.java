package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.repository.OAuthUserRepository;
import site.easy.to.build.crm.service.user.OAuthUserService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/token")
public class TokenRefreshController {
    
    @Autowired
    private OAuthUserRepository oAuthUserRepository;
    
    @Autowired
    private OAuthUserService oAuthUserService;
    
    @Autowired
    private AuthenticationUtils authenticationUtils;
    
    /**
     * Emergency endpoint to manually refresh tokens
     */
    @GetMapping("/refresh-all")
    public ResponseEntity<Map<String, Object>> refreshAllTokens() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get the OAuth user (there's only one)
            OAuthUser oAuthUser = oAuthUserRepository.findByEmail("management@propsk.com");
            
            if (oAuthUser == null) {
                result.put("error", "OAuth user not found");
                return ResponseEntity.badRequest().body(result);
            }
            
            // Check current status
            result.put("email", oAuthUser.getEmail());
            result.put("currentExpiration", oAuthUser.getAccessTokenExpiration());
            result.put("isExpired", oAuthUser.getAccessTokenExpiration().isBefore(Instant.now()));
            
            // Force refresh
            oAuthUser.setAccessTokenExpiration(Instant.now().minusSeconds(1));
            String newToken = oAuthUserService.refreshAccessTokenIfNeeded(oAuthUser);
            
            // Reload to get updated values
            oAuthUser = oAuthUserRepository.findByEmail("management@propsk.com");
            
            result.put("success", true);
            result.put("newExpiration", oAuthUser.getAccessTokenExpiration());
            result.put("tokenRefreshed", newToken != null);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("success", false);
            return ResponseEntity.status(500).body(result);
        }
    }
    
    /**
     * Check token status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> checkTokenStatus(Authentication authentication) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            
            if (oAuthUser == null) {
                // Try to get the only user we have
                oAuthUser = oAuthUserRepository.findByEmail("management@propsk.com");
            }
            
            if (oAuthUser == null) {
                result.put("error", "No OAuth user found");
                return ResponseEntity.badRequest().body(result);
            }
            
            Instant now = Instant.now();
            Instant expiration = oAuthUser.getAccessTokenExpiration();
            
            result.put("email", oAuthUser.getEmail());
            result.put("hasAccessToken", oAuthUser.getAccessToken() != null);
            result.put("hasRefreshToken", oAuthUser.getRefreshToken() != null && !oAuthUser.getRefreshToken().equals("N/A"));
            result.put("currentTime", now);
            result.put("tokenExpiration", expiration);
            
            if (expiration != null) {
                if (expiration.isBefore(now)) {
                    result.put("status", "EXPIRED");
                    result.put("expiredMinutesAgo", java.time.Duration.between(expiration, now).toMinutes());
                } else if (expiration.isBefore(now.plusSeconds(300))) {
                    result.put("status", "EXPIRING_SOON");
                    result.put("minutesUntilExpiry", java.time.Duration.between(now, expiration).toMinutes());
                } else {
                    result.put("status", "VALID");
                    result.put("minutesUntilExpiry", java.time.Duration.between(now, expiration).toMinutes());
                }
            } else {
                result.put("status", "NO_EXPIRATION_SET");
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }
}
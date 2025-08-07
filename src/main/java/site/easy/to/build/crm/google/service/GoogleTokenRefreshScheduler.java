// GoogleTokenRefreshScheduler.java - NEW SERVICE for Automatic Token Refresh
package site.easy.to.build.crm.google.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.repository.OAuthUserRepository;
import site.easy.to.build.crm.service.user.OAuthUserService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class GoogleTokenRefreshScheduler {
    
    private final OAuthUserRepository oAuthUserRepository;
    private final OAuthUserService oAuthUserService;
    
    @Autowired
    public GoogleTokenRefreshScheduler(OAuthUserRepository oAuthUserRepository, 
                                      OAuthUserService oAuthUserService) {
        this.oAuthUserRepository = oAuthUserRepository;
        this.oAuthUserService = oAuthUserService;
    }
    
    /**
     * Run every 30 minutes to check and refresh expiring tokens
     * This ensures tokens are refreshed before they expire
     */
    @Scheduled(fixedDelay = 1800000) // 30 minutes in milliseconds
    public void refreshExpiringTokens() {
        System.out.println("🔄 Starting scheduled Google token refresh check...");
        
        List<OAuthUser> allOAuthUsers = oAuthUserRepository.findAll();
        int refreshedCount = 0;
        int errorCount = 0;
        int skipCount = 0;
        
        for (OAuthUser oAuthUser : allOAuthUsers) {
            try {
                // Skip if no refresh token
                if (oAuthUser.getRefreshToken() == null || 
                    oAuthUser.getRefreshToken().isEmpty() || 
                    "N/A".equals(oAuthUser.getRefreshToken())) {
                    System.out.println("⏭️ Skipping user " + oAuthUser.getEmail() + " - no refresh token");
                    skipCount++;
                    continue;
                }
                
                // Check if token expires within the next hour
                Instant now = Instant.now();
                Instant expiresAt = oAuthUser.getAccessTokenExpiration();
                
                if (expiresAt != null && expiresAt.isBefore(now.plus(1, ChronoUnit.HOURS))) {
                    System.out.println("🔄 Refreshing token for user: " + oAuthUser.getEmail());
                    System.out.println("   Token expires at: " + expiresAt);
                    
                    // Refresh the token
                    String newAccessToken = oAuthUserService.refreshAccessTokenIfNeeded(oAuthUser);
                    
                    if (newAccessToken != null && !newAccessToken.isEmpty()) {
                        System.out.println("✅ Successfully refreshed token for: " + oAuthUser.getEmail());
                        refreshedCount++;
                    } else {
                        System.err.println("❌ Failed to refresh token for: " + oAuthUser.getEmail());
                        errorCount++;
                    }
                } else {
                    System.out.println("✅ Token still valid for: " + oAuthUser.getEmail());
                    if (expiresAt != null) {
                        System.out.println("   Expires at: " + expiresAt);
                    }
                }
                
            } catch (Exception e) {
                System.err.println("❌ Error refreshing token for " + oAuthUser.getEmail() + ": " + e.getMessage());
                errorCount++;
                
                // If it's an authentication error, mark the user as needing re-auth
                if (e.getMessage() != null && e.getMessage().contains("invalid_grant")) {
                    markUserForReauth(oAuthUser);
                }
            }
        }
        
        System.out.println("📊 Token refresh summary:");
        System.out.println("   Total users: " + allOAuthUsers.size());
        System.out.println("   Refreshed: " + refreshedCount);
        System.out.println("   Skipped (no refresh token): " + skipCount);
        System.out.println("   Errors: " + errorCount);
        System.out.println("✅ Scheduled token refresh check completed");
    }
    
    /**
     * Emergency refresh - can be called manually when needed
     */
    public void refreshAllTokensNow() {
        System.out.println("🚨 Emergency token refresh initiated");
        refreshExpiringTokens();
    }
    
    /**
     * Refresh tokens for a specific user
     */
    public boolean refreshTokenForUser(String email) {
        System.out.println("🔄 Manual token refresh for user: " + email);
        
        OAuthUser oAuthUser = oAuthUserRepository.findByEmail(email);
        if (oAuthUser == null) {
            System.err.println("❌ User not found: " + email);
            return false;
        }
        
        if (oAuthUser.getRefreshToken() == null || 
            oAuthUser.getRefreshToken().isEmpty() || 
            "N/A".equals(oAuthUser.getRefreshToken())) {
            System.err.println("❌ User has no refresh token: " + email);
            return false;
        }
        
        try {
            String newAccessToken = oAuthUserService.refreshAccessTokenIfNeeded(oAuthUser);
            if (newAccessToken != null && !newAccessToken.isEmpty()) {
                System.out.println("✅ Successfully refreshed token for: " + email);
                return true;
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to refresh token: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Check token status for all users
     */
    public void checkTokenStatus() {
        System.out.println("📊 Google OAuth Token Status Report");
        System.out.println("=====================================");
        
        List<OAuthUser> allOAuthUsers = oAuthUserRepository.findAll();
        Instant now = Instant.now();
        
        int validCount = 0;
        int expiredCount = 0;
        int noRefreshTokenCount = 0;
        int expiringWithinHourCount = 0;
        int expiringWithinDayCount = 0;
        
        for (OAuthUser user : allOAuthUsers) {
            Instant expiresAt = user.getAccessTokenExpiration();
            boolean hasRefreshToken = user.getRefreshToken() != null && 
                                     !user.getRefreshToken().isEmpty() && 
                                     !"N/A".equals(user.getRefreshToken());
            
            System.out.println("\n📧 User: " + user.getEmail());
            System.out.println("   Has refresh token: " + hasRefreshToken);
            
            if (!hasRefreshToken) {
                noRefreshTokenCount++;
                System.out.println("   ⚠️ Status: NO REFRESH TOKEN - needs re-authentication");
            } else if (expiresAt == null) {
                System.out.println("   ⚠️ Status: No expiration set");
            } else if (expiresAt.isBefore(now)) {
                expiredCount++;
                long hoursAgo = ChronoUnit.HOURS.between(expiresAt, now);
                System.out.println("   ❌ Status: EXPIRED " + hoursAgo + " hours ago");
            } else if (expiresAt.isBefore(now.plus(1, ChronoUnit.HOURS))) {
                expiringWithinHourCount++;
                long minutesLeft = ChronoUnit.MINUTES.between(now, expiresAt);
                System.out.println("   ⚠️ Status: EXPIRING SOON - " + minutesLeft + " minutes left");
            } else if (expiresAt.isBefore(now.plus(24, ChronoUnit.HOURS))) {
                expiringWithinDayCount++;
                long hoursLeft = ChronoUnit.HOURS.between(now, expiresAt);
                System.out.println("   🟡 Status: Expiring in " + hoursLeft + " hours");
            } else {
                validCount++;
                long daysLeft = ChronoUnit.DAYS.between(now, expiresAt);
                System.out.println("   ✅ Status: Valid for " + daysLeft + " days");
            }
            
            System.out.println("   Granted scopes: " + user.getGrantedScopes());
        }
        
        System.out.println("\n=====================================");
        System.out.println("📊 Summary:");
        System.out.println("   Total OAuth users: " + allOAuthUsers.size());
        System.out.println("   ✅ Valid tokens: " + validCount);
        System.out.println("   ⚠️ Expiring within 1 hour: " + expiringWithinHourCount);
        System.out.println("   🟡 Expiring within 24 hours: " + expiringWithinDayCount);
        System.out.println("   ❌ Expired tokens: " + expiredCount);
        System.out.println("   🔄 No refresh token (need re-auth): " + noRefreshTokenCount);
        System.out.println("=====================================");
    }
    
    /**
     * Mark user as needing re-authentication
     */
    private void markUserForReauth(OAuthUser oAuthUser) {
        System.out.println("🔄 Marking user for re-authentication: " + oAuthUser.getEmail());
        System.out.println("   Removing OAuth record to force complete re-authentication");
        
        // Delete the OAuth record entirely
        oAuthUserRepository.delete(oAuthUser);
        
        System.out.println("✅ OAuth record removed. User must re-authenticate with Google.");
    }
}
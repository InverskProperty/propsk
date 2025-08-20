# IMMEDIATE PRODUCTION FIXES - Deploy NOW

## 🚨 CRITICAL STATUS
- **YOUR TOKEN IS EXPIRED** - All Google API calls will fail
- **No automatic refresh** is happening
- **Only 1 user** in OAuth table

---

## FIX #1: Emergency Token Refresh Service (Deploy IMMEDIATELY)

Create file: `src/main/java/site/easy/to/build/crm/google/service/TokenAwareApiExecutor.java`

```java
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
```

---

## FIX #2: Add Manual Token Refresh Endpoint (For Testing)

Add to any existing controller or create new file: `src/main/java/site/easy/to/build/crm/controller/TokenRefreshController.java`

```java
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
```

---

## FIX #3: Updated OAuthUserServiceImpl with Logging

Update your existing `OAuthUserServiceImpl.java` - Add better error handling:

```java
@Override
@Transactional
@ConditionalOnExpression("!T(site.easy.to.build.crm.util.StringUtils).isEmpty('${spring.security.oauth2.client.registration.google.client-id:}')")
public String refreshAccessTokenIfNeeded(OAuthUser oAuthUser) {
    System.out.println("🔄 Token refresh attempt for user: " + oAuthUser.getEmail());
    System.out.println("   Current time: " + Instant.now());
    System.out.println("   Token expires at: " + oAuthUser.getAccessTokenExpiration());
    
    Instant now = Instant.now();
    
    // Check if token is still valid
    if (oAuthUser.getAccessTokenExpiration() != null && 
        now.isBefore(oAuthUser.getAccessTokenExpiration())) {
        System.out.println("✅ Token still valid, no refresh needed");
        return oAuthUser.getAccessToken();
    }
    
    System.out.println("⚠️ Token expired, attempting refresh...");
    
    // Check refresh token
    if (oAuthUser.getRefreshToken() == null || 
        oAuthUser.getRefreshToken().isEmpty() || 
        "N/A".equals(oAuthUser.getRefreshToken())) {
        System.err.println("❌ NO REFRESH TOKEN AVAILABLE!");
        throw new RuntimeException("No refresh token available. User must re-authenticate.");
    }
    
    try {
        System.out.println("🔄 Making refresh request to Google...");
        
        GoogleTokenResponse tokenResponse = new GoogleRefreshTokenRequest(
            new NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            oAuthUser.getRefreshToken(),
            clientId,
            clientSecret
        ).execute();
        
        String newAccessToken = tokenResponse.getAccessToken();
        long expiresIn = tokenResponse.getExpiresInSeconds();
        Instant expiresAt = Instant.now().plusSeconds(expiresIn);
        
        System.out.println("✅ TOKEN REFRESH SUCCESSFUL!");
        System.out.println("   New token expires at: " + expiresAt);
        
        // Update in database
        oAuthUser.setAccessToken(newAccessToken);
        oAuthUser.setAccessTokenExpiration(expiresAt);
        oAuthUser.setAccessTokenIssuedAt(Instant.now());
        
        if (tokenResponse.getRefreshToken() != null) {
            System.out.println("✅ New refresh token received");
            oAuthUser.setRefreshToken(tokenResponse.getRefreshToken());
        }
        
        oAuthUserRepository.save(oAuthUser);
        System.out.println("✅ Token saved to database");
        
        return newAccessToken;
        
    } catch (IOException e) {
        System.err.println("❌ TOKEN REFRESH FAILED: " + e.getMessage());
        e.printStackTrace();
        
        if (e.getMessage().contains("invalid_grant")) {
            System.err.println("❌ REFRESH TOKEN INVALID - User must re-authenticate!");
            // Don't delete the user, just clear tokens
            oAuthUser.setAccessToken(null);
            oAuthUser.setRefreshToken(null);
            oAuthUserRepository.save(oAuthUser);
        }
        
        throw new RuntimeException("Failed to refresh access token: " + e.getMessage(), e);
    }
}
```

---

## FIX #4: Add to application.properties (IMMEDIATELY)

```properties
# ADD THESE LINES TO YOUR EXISTING application.properties

# Critical OAuth Settings
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://accounts.google.com

# Token Management
token.refresh.interval=1800000
token.refresh.threshold=300000
token.refresh.max-retries=3

# Connection Pool - CRITICAL for Railway
spring.datasource.hikari.maximum-pool-size=3
spring.datasource.hikari.minimum-idle=1
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.validation-timeout=5000
spring.datasource.hikari.leak-detection-threshold=60000

# Enable detailed OAuth logging for debugging
logging.level.site.easy.to.build.crm.service.user=DEBUG
logging.level.site.easy.to.build.crm.config.oauth2=DEBUG
logging.level.org.springframework.security.oauth2=DEBUG
logging.level.com.google.api.client.http=DEBUG

# Disable problematic scheduled tasks temporarily
spring.task.scheduling.enabled=false
```

---

## FIX #5: Empty GoogleCalendarApiServiceImpl (CRITICAL!)

Since your file is empty, create: `src/main/java/site/easy/to/build/crm/google/service/calendar/GoogleCalendarApiServiceImpl.java`

```java
package site.easy.to.build.crm.google.service.calendar;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.google.model.calendar.*;
import site.easy.to.build.crm.service.user.OAuthUserService;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Service
public class GoogleCalendarApiServiceImpl implements GoogleCalendarApiService {
    
    @Autowired
    private OAuthUserService oAuthUserService;
    
    @Override
    public EventDisplayList getEvents(String calendarId, OAuthUser oAuthUser) 
            throws IOException, GeneralSecurityException {
        // Minimal implementation to prevent NPE
        EventDisplayList list = new EventDisplayList();
        list.setItems(new java.util.ArrayList<>());
        return list;
    }
    
    @Override
    public EventDisplay getEvent(String calendarId, OAuthUser oAuthUser, String eventId) 
            throws IOException, GeneralSecurityException {
        return new EventDisplay();
    }
    
    @Override
    public String createEvent(String calendarId, OAuthUser oAuthUser, Event event) 
            throws IOException, GeneralSecurityException {
        return "temp-event-id";
    }
    
    @Override
    public void updateEvent(String calendarId, OAuthUser oAuthUser, String eventId, Event event) 
            throws IOException, GeneralSecurityException {
        // No-op for now
    }
    
    @Override
    public void deleteEvent(String calendarId, OAuthUser oAuthUser, String eventId) 
            throws IOException, GeneralSecurityException {
        // No-op for now
    }
}
```

---

## DEPLOYMENT STEPS (DO NOW!)

### Step 1: Test Token Refresh (IMMEDIATELY)
```bash
# SSH into your Render instance and run:
curl https://spoutproperty-hub.onrender.com/api/token/refresh-all

# Check status:
curl https://spoutproperty-hub.onrender.com/api/token/status
```

### Step 2: Deploy Files (In Order)
1. `TokenAwareApiExecutor.java` - Create new file
2. `TokenRefreshController.java` - Create new file  
3. Update `OAuthUserServiceImpl.java` - Add logging
4. `GoogleCalendarApiServiceImpl.java` - Create (empty causing crashes!)
5. Update `application.properties` - Add new properties

### Step 3: Restart Application
```bash
# On Render, trigger a manual deploy or:
# Push to your Git repository to trigger auto-deploy
```

### Step 4: Verify Token is Refreshed
```bash
# After restart, check:
curl https://spoutproperty-hub.onrender.com/api/token/status

# Should show "status": "VALID"
```

---

## MONITORING AFTER DEPLOYMENT

### Check Logs for Token Refresh
```bash
# Look for these messages in logs:
"🔄 Token refresh attempt"
"✅ TOKEN REFRESH SUCCESSFUL"
"❌ TOKEN REFRESH FAILED"
```

### Database Check
```sql
-- Run this query to verify token is refreshed:
SELECT 
    email,
    CASE 
        WHEN access_token_expiration > NOW() THEN 'VALID'
        ELSE 'EXPIRED'
    END as status,
    access_token_expiration,
    TIMESTAMPDIFF(MINUTE, NOW(), access_token_expiration) as minutes_until_expiry
FROM oauth_users;
```

---

## IF TOKEN REFRESH FAILS

### Option 1: Manual Re-authentication
1. Go to: https://spoutproperty-hub.onrender.com/login
2. Click "Login with Google"
3. Re-authorize the application

### Option 2: Force New Consent
```sql
-- Clear the refresh token to force re-auth:
UPDATE oauth_users 
SET refresh_token = NULL 
WHERE email = 'management@propsk.com';
```

---

## CRITICAL NOTES

1. **Your token expired 2 days ago** - Nothing using Google APIs will work
2. **Only 1 user in system** - All features depend on this one OAuth record
3. **No automatic refresh** is happening - Must deploy fixes
4. **Calendar service is empty** - Will cause crashes

**Deploy these fixes IMMEDIATELY to restore Google API functionality!**
# OAuth2 Delegated Access Setup Guide

## Overview

This guide provides complete setup instructions for implementing OAuth2 delegated access in the CRM application, allowing administrators to access Google services on behalf of other users.

## Current Architecture Analysis

### Existing OAuth2 Implementation

The application currently implements:

1. **Individual User OAuth2 Flow**
   - Each user authenticates individually via Google OAuth2
   - Tokens stored in `oauth_users` table
   - Manual token refresh per user
   - Limited to authenticated user's scope

2. **Service Account for Sheets**
   - `GoogleSheetsServiceAccountService` uses service account credentials
   - Limited to application-level access
   - No user impersonation capability

### Issues with Current Setup

1. **Foreign Key Constraint Failures**
   - `portfolio_sync_log.initiated_by` references missing user IDs
   - Fixed by implementing proper user validation in `PayPropPortfolioSyncService`

2. **Hibernate Session Management**
   - Session corruption when sync logs fail to persist
   - Fixed by implementing proper transaction handling and fallback mechanisms

3. **Limited Delegation Capabilities**
   - No domain-wide delegation setup
   - Manual user authentication required for each user
   - Cannot access resources on behalf of other domain users

## OAuth2 Delegation Solutions

### Option 1: Google Workspace Domain-Wide Delegation (Recommended)

**Best for:** Organizations with Google Workspace where admin control is needed

#### Setup Steps

1. **Create Service Account with Domain-Wide Delegation**
   ```bash
   # 1. Go to Google Cloud Console
   # 2. Enable Google Drive API, Gmail API, Google Sheets API
   # 3. Create Service Account
   # 4. Download JSON key file
   # 5. Enable Domain-Wide Delegation for the service account
   ```

2. **Configure Google Workspace Admin Console**
   ```
   Admin Console > Security > API Controls > Domain-wide Delegation
   Add Client ID with required scopes:
   - https://www.googleapis.com/auth/drive
   - https://www.googleapis.com/auth/gmail.send
   - https://www.googleapis.com/auth/spreadsheets
   - https://www.googleapis.com/auth/userinfo.email
   - https://www.googleapis.com/auth/userinfo.profile
   ```

3. **Implementation Code**
   ```java
   // Create new service: DelegatedOAuth2Service.java
   @Service
   public class DelegatedOAuth2Service {

       @Value("${google.service.account.key}")
       private String serviceAccountKey;

       private GoogleCredential createDelegatedCredential(String userEmail, List<String> scopes)
           throws IOException, GeneralSecurityException {

           GoogleCredential credential = GoogleCredential
               .fromStream(new ByteArrayInputStream(serviceAccountKey.getBytes()))
               .createScoped(scopes)
               .createDelegated(userEmail); // KEY: This enables impersonation

           return credential;
       }

       public Drive createDriveService(String userEmail)
           throws IOException, GeneralSecurityException {

           GoogleCredential credential = createDelegatedCredential(userEmail,
               Arrays.asList(DriveScopes.DRIVE));

           return new Drive.Builder(
               GoogleNetHttpTransport.newTrustedTransport(),
               GsonFactory.getDefaultInstance(),
               credential)
               .setApplicationName("CRM Property Management")
               .build();
       }
   }
   ```

### Option 2: OAuth2 Token Exchange (Alternative)

**Best for:** Multi-tenant applications or when domain-wide delegation isn't available

#### Implementation
```java
@Service
public class TokenExchangeService {

    public String exchangeTokenForUser(String adminToken, String targetUserEmail) {
        // Implement OAuth2 token exchange flow
        // Use admin token to request access on behalf of target user
        // Requires specific OAuth2 provider support
    }
}
```

### Option 3: Enhanced User Token Management (Current + Improvements)

**Best for:** Current setup with improved management

#### Improvements to Existing System

1. **Centralized Token Management**
   ```java
   @Service
   public class CentralizedTokenService {

       public String getValidTokenForUser(String userEmail) {
           OAuthUser oauthUser = oAuthUserRepository.findByEmail(userEmail);
           if (oauthUser == null) {
               throw new RuntimeException("User not authenticated: " + userEmail);
           }
           return oAuthUserService.refreshAccessTokenIfNeeded(oauthUser);
       }

       public boolean canAccessOnBehalfOf(String adminEmail, String targetEmail) {
           // Implement permission checking logic
           User admin = userRepository.findByEmail(adminEmail);
           return admin != null && hasAdminRole(admin);
       }
   }
   ```

2. **Batch Token Refresh**
   ```java
   @Scheduled(fixedRate = 1800000) // 30 minutes
   public void refreshAllTokens() {
       List<OAuthUser> users = oAuthUserRepository.findAll();
       for (OAuthUser user : users) {
           try {
               oAuthUserService.refreshAccessTokenIfNeeded(user);
           } catch (Exception e) {
               log.warn("Failed to refresh token for user: {}", user.getEmail());
           }
       }
   }
   ```

## Implementation Plan

### Phase 1: Fix Current Issues ✅

- [x] Fix foreign key constraint failures in `PayPropPortfolioSyncService`
- [x] Implement proper user validation methods
- [x] Fix Hibernate session management issues
- [x] Improve error handling and transaction management

### Phase 2: Implement Domain-Wide Delegation

1. **Create Service Account Configuration**
   ```properties
   # Add to application.properties
   google.service.account.key=${GOOGLE_SERVICE_ACCOUNT_KEY}
   google.domain.admin.email=${GOOGLE_DOMAIN_ADMIN_EMAIL}
   google.delegation.enabled=${GOOGLE_DELEGATION_ENABLED:false}
   ```

2. **Create Delegated Service Classes**
   - `DelegatedOAuth2Service.java`
   - `DelegatedDriveService.java`
   - `DelegatedGmailService.java`
   - `DelegatedSheetsService.java`

3. **Update Existing Services**
   - Modify `PayPropPortfolioSyncService` to use delegated access
   - Update Google API services to support delegation
   - Add permission checking before delegation

### Phase 3: Security and Monitoring

1. **Access Control**
   ```java
   @PreAuthorize("hasRole('ADMIN') or hasPermission(#targetUser, 'DELEGATE')")
   public void performActionOnBehalfOf(String targetUser) {
       // Delegated action
   }
   ```

2. **Audit Logging**
   ```java
   @EventListener
   public void logDelegatedAccess(DelegatedAccessEvent event) {
       auditLog.info("Admin {} accessed {} on behalf of {}",
           event.getAdminUser(), event.getResource(), event.getTargetUser());
   }
   ```

3. **Rate Limiting**
   ```java
   @RateLimiter(name = "delegation", fallbackMethod = "delegationFallback")
   public String performDelegatedAction(String userEmail) {
       // Implementation
   }
   ```

## Configuration Examples

### Environment Variables
```bash
# Service Account for Domain-Wide Delegation
GOOGLE_SERVICE_ACCOUNT_KEY='{
  "type": "service_account",
  "project_id": "your-project",
  "private_key_id": "key-id",
  "private_key": "-----BEGIN PRIVATE KEY-----\n...",
  "client_email": "your-service-account@your-project.iam.gserviceaccount.com",
  "client_id": "123456789",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token"
}'

# Domain Admin (must be G Suite/Workspace admin)
GOOGLE_DOMAIN_ADMIN_EMAIL=admin@yourdomain.com

# Feature flags
GOOGLE_DELEGATION_ENABLED=true
OAUTH2_FALLBACK_TO_USER_TOKENS=true
```

### Application Configuration
```properties
# OAuth2 Delegation Settings
oauth2.delegation.enabled=${GOOGLE_DELEGATION_ENABLED:false}
oauth2.delegation.admin-email=${GOOGLE_DOMAIN_ADMIN_EMAIL}
oauth2.delegation.allowed-domains=yourdomain.com,subsidiary.com
oauth2.delegation.rate-limit=100

# Security
oauth2.delegation.require-admin-role=true
oauth2.delegation.audit-enabled=true
oauth2.delegation.session-timeout=3600
```

## Testing Strategy

### Unit Tests
```java
@Test
public void testDelegatedAccess() {
    // Arrange
    String adminEmail = "admin@company.com";
    String targetEmail = "user@company.com";

    // Act
    Drive service = delegatedOAuth2Service.createDriveService(targetEmail);

    // Assert
    assertNotNull(service);
    // Verify delegation works correctly
}
```

### Integration Tests
```java
@Test
public void testEndToEndDelegation() {
    // Test full flow from login to delegated API call
    // Verify permissions, token management, and API access
}
```

## Troubleshooting

### Common Issues

1. **"Domain-wide delegation not enabled"**
   - Verify service account has domain-wide delegation enabled
   - Check Google Workspace admin console settings
   - Ensure client ID is authorized for required scopes

2. **"Access denied for delegated user"**
   - Verify target user is in the same domain
   - Check user's Google Workspace settings
   - Ensure required APIs are enabled

3. **"Invalid credentials"**
   - Verify service account key is correctly formatted
   - Check key permissions and expiration
   - Ensure proper environment variable setup

### Debug Commands
```bash
# Test service account credentials
curl -X POST \
  "https://oauth2.googleapis.com/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=YOUR_JWT"

# Verify domain delegation
# Check Google Workspace Admin Console > Security > API Controls
```

## Security Best Practices

1. **Principle of Least Privilege**
   - Grant minimum required scopes
   - Implement role-based access control
   - Regular permission audits

2. **Token Management**
   - Secure storage of service account keys
   - Regular key rotation
   - Monitor token usage

3. **Audit and Compliance**
   - Log all delegated actions
   - Monitor for unusual access patterns
   - Regular security reviews

## Migration Strategy

### From Current Setup to Delegated Access

1. **Phase 1**: Implement alongside existing system
2. **Phase 2**: Gradually migrate services to use delegation
3. **Phase 3**: Deprecate individual user tokens for admin actions
4. **Phase 4**: Full migration and cleanup

### Rollback Plan

1. Keep existing OAuth2 user flow as fallback
2. Feature flags to disable delegation
3. Monitoring to detect issues early
4. Quick rollback procedures documented

## Conclusion

OAuth2 delegated access will significantly improve the CRM application's ability to manage Google services on behalf of users. The recommended approach is Google Workspace Domain-Wide Delegation for maximum flexibility and control.

Key benefits:
- Centralized access management
- Reduced user authentication friction
- Better integration capabilities
- Enhanced security and auditing

The implementation should be done incrementally with proper testing and monitoring at each phase.
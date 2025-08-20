# Google OAuth Authentication & Token Storage - Complete System Mapping

## Table of Contents
1. [System Overview](#system-overview)
2. [Authentication Flow](#authentication-flow)
3. [Token Storage Architecture](#token-storage-architecture)
4. [Key Components](#key-components)
5. [Security Configurations](#security-configurations)
6. [Token Refresh Mechanism](#token-refresh-mechanism)
7. [Error Handling & Restrictions](#error-handling--restrictions)
8. [Critical Issues & Fixes](#critical-issues--fixes)

---

## System Overview

### Primary Authentication Method
- **OAuth2 Provider**: Google OAuth2
- **Framework**: Spring Security OAuth2
- **Token Storage**: Database-backed (JPA/Hibernate)
- **Refresh Strategy**: Automatic refresh with fallback to re-authentication

### Key Database Tables
- `oauth_users` - Stores OAuth tokens and user associations
- `users` - Main user table
- `user_profiles` - User profile information

---

## Authentication Flow

### 1. Initial OAuth2 Configuration
**File**: `OAuth2ClientIdInitializer.java`
```
Location: site.easy.to.build.crm.config.init
Purpose: Initialize OAuth2 settings with enhanced refresh token support
```

**Key Configurations**:
- Forces `access_type=offline` to request refresh tokens
- Sets `prompt=consent` to ensure refresh token is provided
- Configures authorization URI with refresh token parameters
- Default scopes: `openid, email, profile`

### 2. OAuth Login Flow

#### Step 1: User Initiates Google Login
- User clicks Google login button
- Spring Security redirects to Google OAuth2 endpoint

#### Step 2: Google Authorization
**Authorization URL Template**:
```
https://accounts.google.com/o/oauth2/auth?
  access_type=offline&
  prompt=consent&
  response_type=code&
  client_id={clientId}&
  scope={scopes}&
  state={state}&
  redirect_uri={redirectUri}
```

#### Step 3: OAuth Success Handler
**File**: `OAuthLoginSuccessHandler.java`
```
Location: site.easy.to.build.crm.config.oauth2
```

**Process Flow**:
1. Receives OAuth2 tokens from Google
2. Checks for existing user by email
3. Creates new user if first-time login
4. Stores/updates OAuth tokens in database
5. Assigns roles based on email domain
6. Updates Spring Security context
7. Redirects based on user status

**Role Assignment Logic**:
- First user → `ROLE_MANAGER`
- Management emails → `ROLE_MANAGER`
- Company domain (@propsk.com) → `ROLE_EMPLOYEE`
- External users → `ROLE_EMPLOYEE` (default)

---

## Token Storage Architecture

### OAuth User Entity
**File**: `OAuthUser.java`
```
Location: site.easy.to.build.crm.entity
Table: oauth_users
```

**Fields**:
| Field | Type | Purpose |
|-------|------|---------|
| id | Integer | Primary key |
| granted_scopes | Set<String> | Authorized Google scopes |
| access_token | TEXT | Current access token |
| access_token_issued_at | Instant | Token issue timestamp |
| access_token_expiration | Instant | Token expiry timestamp |
| refresh_token | TEXT | Refresh token for renewal |
| refresh_token_issued_at | Instant | Refresh token issue time |
| refresh_token_expiration | Instant | Refresh token expiry |
| email | String | User's Google email |
| user_id | Foreign Key | Link to users table |

### Repository Layer
**File**: `OAuthUserRepository.java`
```
Location: site.easy.to.build.crm.repository
```

**Key Methods**:
- `findById(int id)`
- `getOAuthUserByUser(User user)`
- `findByEmail(String email)`

---

## Key Components

### 1. Google API Configuration
**Files**:
- `GoogleApiConfig.java` - Bean configuration
- `GoogleApiProperties.java` - Property binding
- `GoogleAuthorizationCodeFlowWrapper.java` - Flow wrapper

**Configuration Properties Required**:
```properties
spring.security.oauth2.client.registration.google.client-id=YOUR_CLIENT_ID
spring.security.oauth2.client.registration.google.client-secret=YOUR_SECRET
spring.security.oauth2.client.registration.google.scope=openid,email,profile
```

### 2. Custom OAuth2 User Service
**File**: `CustomOAuth2UserService.java`
```
Purpose: Load user from OAuth2 request
Integration: Links OAuth2 user with application User entity
```

### 3. Custom OAuth2 User
**File**: `CustomOAuth2User.java`
```
Purpose: Bridge between OAuth2User and application User
Provides: Authority mapping from database roles
```

---

## Security Configurations

### Account Status Validation
**Valid Statuses**: 
- `ACTIVE` - Full access
- `INACTIVE` - Redirects to `/account-inactive`
- `SUSPENDED` - Redirects to `/account-suspended`

### Security Checks in Login Handler:
1. Validates Google OAuth credentials exist
2. Checks user account status
3. Verifies role assignments
4. Logs security events

---

## Token Refresh Mechanism

### Automatic Token Refresh
**File**: `OAuthUserServiceImpl.java`
**Method**: `refreshAccessTokenIfNeeded()`

**Process**:
1. Check if access token is expired
2. If valid → return existing token
3. If expired → use refresh token to get new access token
4. Update database with new tokens
5. Handle refresh failures

**Refresh Token Request**:
```java
GoogleTokenResponse tokenResponse = new GoogleRefreshTokenRequest(
    new NetHttpTransport(),
    jsonFactory,
    oauthUser.getRefreshToken(),
    clientId,
    clientSecret
).execute();
```

### Scheduled Token Refresh Service
**File**: `GoogleTokenRefreshScheduler.java`
```
Location: site.easy.to.build.crm.google.service
Schedule: Every 30 minutes (@Scheduled(fixedDelay = 1800000))
```

**Features**:
- **Automatic Refresh**: Runs every 30 minutes to check expiring tokens
- **Proactive Refresh**: Refreshes tokens expiring within 1 hour
- **Batch Processing**: Processes all OAuth users in database
- **Error Handling**: Tracks and reports refresh failures
- **Manual Methods**:
  - `refreshAllTokensNow()` - Emergency refresh all tokens
  - `refreshTokenForUser(String email)` - Refresh specific user
  - `checkTokenStatus()` - Generate status report

**Token Status Categories**:
| Status | Condition | Action |
|--------|-----------|--------|
| Valid | Expires > 24 hours | No action |
| Expiring Soon | Expires < 1 hour | Auto-refresh |
| Expiring | Expires < 24 hours | Monitor |
| Expired | Past expiration | Immediate refresh |
| No Refresh Token | Missing/invalid | Requires re-auth |

### Token Validation
**Method**: `hasValidTokens()`
**Checks**:
- Access token exists and not expired
- Refresh token exists and valid
- Returns false if re-authentication needed

---

## Error Handling & Restrictions

### Common Error Scenarios

#### 1. Missing Refresh Token
**Issue**: Google doesn't always provide refresh tokens
**Handling**: 
- First-time users always get refresh token (due to `prompt=consent`)
- Returning users may not get new refresh token
- System preserves existing refresh token if available

#### 2. Expired Refresh Token
**Error**: `invalid_grant`
**Handling**:
- Delete OAuth record
- Force re-authentication
- User must re-authorize Google access

#### 3. Invalid Account Status
**Handling**:
- INACTIVE → `/account-inactive`
- SUSPENDED → `/account-suspended`
- Other → `/access-denied`

### Token Timeout Configurations
- **Access Token**: ~1 hour (3600 seconds)
- **Refresh Token**: Long-lived (unless revoked)
- **Session Timeout**: Configured in Spring Security

### Re-authorization Triggers
1. Refresh token expired/revoked
2. User manually revokes access in Google account
3. Application explicitly calls `revokeAccess()`
4. OAuth record deleted from database

---

## Critical Issues & Fixes

### 1. Refresh Token Not Always Provided
**Issue**: Google only provides refresh token on first authorization
**Fix**: 
- Added `prompt=consent` to force consent screen
- Added `access_type=offline` to request refresh tokens
- Preserve existing refresh token if new one not provided

### 2. User Role Assignment Security
**Issue**: Originally assigned MANAGER role to all OAuth users
**Fix**: 
- Implemented email-based role determination
- Default to EMPLOYEE role for external users
- Manual role upgrade required for elevated permissions

### 3. Token Refresh Failures
**Issue**: Access token expires, refresh fails
**Fix**:
- Comprehensive error handling in refresh logic
- Delete invalid OAuth records
- Clear error messages for re-authentication

### 4. Missing User Association
**Issue**: OAuth user created without proper User link
**Fix**:
- Enhanced user lookup by email
- Proper association in `OAuthLoginSuccessHandler`
- Fallback creation for new users

---

## Service Layer Methods

### OAuthUserService Interface Methods
| Method | Purpose |
|--------|---------|
| `findById(Long id)` | Get OAuth user by ID |
| `findByEmail(String email)` | Get OAuth user by email |
| `getOAuthUserByUser(User user)` | Get OAuth user for User entity |
| `hasValidTokens(OAuthUser oauthUser)` | Check token validity |
| `refreshAccessTokenIfNeeded(OAuthUser oauthUser)` | Refresh expired tokens |
| `revokeAccess(OAuthUser oAuthUser)` | Revoke Google access |
| `save(OAuthUser oAuthUser, User user)` | Save with user association |
| `updateOAuthUserTokens(...)` | Update token values |

### UserService Interface Methods
| Method | Purpose |
|--------|---------|
| `countAllUsers()` | Get total user count |
| `findById(Long id)` | Get user by ID |
| `findByEmail(String email)` | Get user by email |
| `save(User user)` | Save/update user |

---

## Debug & Monitoring

### Key Log Points
1. **OAuth Login Success Handler**
   - Token receipt confirmation
   - User creation/update
   - Role assignment
   - Security context update

2. **Token Refresh Service**
   - Token expiration check
   - Refresh attempt
   - Success/failure status
   - Database update confirmation

3. **Scheduled Refresh Service**
   - Batch refresh summary
   - Per-user refresh status
   - Error tracking
   - Re-authentication requirements

4. **Security Events**
   - Invalid account access attempts
   - Role assignment decisions
   - Authentication failures

### Token Status Monitoring
**Method**: `GoogleTokenRefreshScheduler.checkTokenStatus()`

**Provides Real-time Report**:
- Total OAuth users
- Valid tokens count
- Tokens expiring within 1 hour
- Tokens expiring within 24 hours
- Expired tokens
- Users needing re-authentication

**Sample Output**:
```
📊 Google OAuth Token Status Report
=====================================
🔧 User: user@example.com
   Has refresh token: true
   ✅ Status: Valid for 5 days
   Granted scopes: [openid, email, profile, drive.file]

📊 Summary:
   Total OAuth users: 25
   ✅ Valid tokens: 20
   ⚠️ Expiring within 1 hour: 2
   🟡 Expiring within 24 hours: 1
   ❌ Expired tokens: 1
   🔄 No refresh token (need re-auth): 1
=====================================
```

### Monitoring Recommendations
1. Track refresh token usage/failures
2. Monitor re-authentication frequency
3. Log security events for audit
4. Alert on high failure rates
5. Schedule regular token status reports
6. Monitor scheduled refresh job success rate

---

## Configuration Checklist

### Required Google Console Setup
- [ ] OAuth 2.0 Client ID created
- [ ] Authorized redirect URIs configured
- [ ] Required scopes enabled
- [ ] Consent screen configured

### Application Properties Required
- [ ] `spring.security.oauth2.client.registration.google.client-id`
- [ ] `spring.security.oauth2.client.registration.google.client-secret`
- [ ] `spring.security.oauth2.client.registration.google.scope`
- [ ] `spring.security.oauth2.client.registration.google.redirect-uri`

### Database Requirements
- [ ] `oauth_users` table created
- [ ] Foreign key to `users` table
- [ ] String set converter configured
- [ ] Proper column sizes for tokens (TEXT)

---

## API Integration Points

### Google OAuth2 Endpoints Used
1. **Authorization**: `https://accounts.google.com/o/oauth2/v2/auth`
2. **Token Exchange**: `https://oauth2.googleapis.com/token`
3. **Token Refresh**: `https://oauth2.googleapis.com/token`
4. **Token Revocation**: `https://accounts.google.com/o/oauth2/revoke`
5. **Token Info**: `https://www.googleapis.com/oauth2/v3/tokeninfo`

### Google API Services

#### Gmail API Service
**Files**: 
- `GoogleGmailApiService.java` (interface)
- `GoogleGmailApiServiceImpl.java` (implementation)
- `GmailEmailService.java` (wrapper service)

**Base URL**: `https://www.googleapis.com/gmail/v1/users/me`

**Key Methods**:
| Method | Purpose | Token Usage |
|--------|---------|-------------|
| `sendEmail()` | Send email via Gmail API | Refreshes before send |
| `listAndReadEmails()` | Fetch email list | Refreshes before fetch |
| `getEmailsCount()` | Count emails matching query | Refreshes before count |
| `deleteEmail()` | Delete email by ID | Refreshes before delete |
| `replyToEmail()` | Reply to existing email | Refreshes before reply |
| `forwardEmail()` | Forward email | Refreshes before forward |
| `createDraft()` | Create email draft | Refreshes before create |
| `updateDraft()` | Update existing draft | Refreshes before update |

**Token Refresh Pattern**:
```java
String accessToken = oAuthUserService.refreshAccessTokenIfNeeded(oAuthUser);
HttpRequestFactory httpRequestFactory = GoogleApiHelper.createRequestFactory(accessToken);
```

#### Google Access Service
**Files**:
- `GoogleAccessService.java` (interface)
- `GoogleAccessServiceImpl.java` (implementation)

**Scope Management**:
```
SCOPE_CALENDAR: https://www.googleapis.com/auth/calendar
SCOPE_GMAIL: https://www.googleapis.com/auth/gmail.modify
SCOPE_DRIVE: https://www.googleapis.com/auth/drive.file
```

**Key Methods**:
| Method | Purpose |
|--------|---------|
| `grantGoogleAccess()` | Initiate OAuth flow for additional scopes |
| `handleGrantedAccess()` | Process OAuth callback with new permissions |
| `verifyAccessAndHandleRevokedToken()` | Check token validity and handle revocation |

**Scope Change Process**:
1. User requests additional permissions
2. Build authorization URL with required scopes
3. Redirect to Google consent screen
4. Handle callback with authorization code
5. Exchange code for tokens
6. Update granted scopes in database
7. Revoke old access if scope changes detected

### Spring Security Integration
- Uses Spring Security OAuth2 Client
- Custom success handler for token storage
- Custom user service for role mapping
- Session management via Spring Security

---

## Service Flow Diagrams

### Token Refresh Flow
```
Every 30 minutes (Scheduled Job)
    ↓
GoogleTokenRefreshScheduler.refreshExpiringTokens()
    ↓
For each OAuth User:
    ├─→ Check refresh token exists
    │   └─→ No: Skip (mark for re-auth)
    ├─→ Check if expires < 1 hour
    │   └─→ No: Skip (still valid)
    └─→ Yes: Refresh token
        ├─→ OAuthUserService.refreshAccessTokenIfNeeded()
        ├─→ Google OAuth2 Token Endpoint
        ├─→ Update database with new tokens
        └─→ Handle errors (invalid_grant → delete OAuth record)
```

### API Call Flow with Token Management
```
User Action (e.g., Send Email)
    ↓
Controller Layer
    ↓
Service Layer (e.g., GmailEmailService)
    ↓
GoogleGmailApiServiceImpl
    ├─→ refreshAccessTokenIfNeeded()
    │   ├─→ Check token expiration
    │   ├─→ Refresh if needed
    │   └─→ Return valid token
    ├─→ Create HTTP Request with token
    ├─→ Execute API call
    └─→ Handle response/errors
```

### Scope Change Flow
```
User requests new permissions
    ↓
GoogleAccessService.grantGoogleAccess()
    ├─→ Build scope list
    ├─→ Store in session
    └─→ Redirect to Google consent
        ↓
Google Authorization
    ↓
Callback to handleGrantedAccess()
    ├─→ Exchange code for tokens
    ├─→ Extract granted scopes
    ├─→ Compare with requested scopes
    ├─→ Update OAuth record
    └─→ Revoke old tokens if needed
```
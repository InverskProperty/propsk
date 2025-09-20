# PayProp + Google Service Account Integration Pattern

## Architecture Overview

```
CRM User Action
    ↓
PayProp Operations (OAuth2 to PayProp)
    ↓
Google Operations (Service Account - No User Auth Required)
    ↓
Complete
```

## Implementation Strategy

### 1. PayProp Operations (Keep Current Implementation)
```java
@Service
public class PayPropTagService {

    @Autowired
    private PayPropOAuth2Service payPropOAuth2;

    @Autowired
    private PayPropApiClient payPropApiClient;

    public String createPortfolioTag(String portfolioName, String userEmail) {
        // Use existing PayProp OAuth2 - this works perfectly
        String accessToken = payPropOAuth2.getValidAccessToken();
        return payPropApiClient.createTag(accessToken, portfolioName);
    }

    public void deletePortfolioTag(String tagId, String userEmail) {
        String accessToken = payPropOAuth2.getValidAccessToken();
        payPropApiClient.deleteTag(accessToken, tagId);
    }
}
```

### 2. Google Operations (New Service Account)
```java
@Service
public class GoogleDocumentationService {

    private final GoogleCredential serviceAccountCredential;

    public void documentTagCreation(String crmUserEmail, String tagName, String tagId) {
        // Use service account - no user auth needed
        Drive drive = createDriveService();
        Sheets sheets = createSheetsService();

        // Create documentation in Google without requiring user Google account
        String folderId = getOrCreateUserFolder(crmUserEmail);
        createTagDocumentation(folderId, tagName, tagId);
        updateTrackingSheet(crmUserEmail, tagName, tagId);
    }

    private Drive createDriveService() throws Exception {
        GoogleCredential credential = GoogleCredential
            .fromStream(new ByteArrayInputStream(serviceAccountKey.getBytes()))
            .createScoped(Arrays.asList(DriveScopes.DRIVE));

        return new Drive.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName("CRM Property Management")
            .build();
    }
}
```

### 3. Combined Workflow Service
```java
@Service
public class PortfolioWorkflowService {

    @Autowired
    private PayPropTagService payPropTagService;

    @Autowired
    private GoogleDocumentationService googleDocService;

    @Transactional
    public PortfolioSyncResult syncPortfolioToPayProp(Long portfolioId, String userEmail) {
        try {
            // 1. PayProp operations (existing OAuth2)
            String tagId = payPropTagService.createPortfolioTag(portfolioName, userEmail);

            // 2. Google documentation (service account - background)
            googleDocService.documentTagCreation(userEmail, portfolioName, tagId);

            // 3. Update local database
            savePortfolioSyncResult(portfolioId, tagId, "SUCCESS");

            return PortfolioSyncResult.success(tagId);

        } catch (PayPropException e) {
            // PayProp failed - no Google operations needed
            savePortfolioSyncResult(portfolioId, null, "PAYPROP_FAILED");
            throw e;
        } catch (Exception e) {
            // Google failed - PayProp succeeded, log but don't fail
            log.warn("Google documentation failed for portfolio {}: {}", portfolioId, e.getMessage());
            savePortfolioSyncResult(portfolioId, tagId, "PAYPROP_SUCCESS_GOOGLE_FAILED");
            return PortfolioSyncResult.partialSuccess(tagId, "Documentation failed");
        }
    }
}
```

## Benefits for Your PayProp Integration

### ✅ Easier PayProp Operations
1. **No Google Auth Dependencies**: PayProp operations don't require users to be authenticated with Google
2. **Simplified Error Handling**: PayProp failures don't cascade to Google operations
3. **Independent Scheduling**: Can run PayProp syncs on schedule without user interaction

### ✅ Better User Experience
1. **No Multiple Logins**: Users only need to log into CRM, not Google
2. **Background Documentation**: Google sheets/docs created automatically
3. **Consistent Access**: Service account never expires or needs re-auth

### ✅ Improved Reliability
1. **No Token Refresh Issues**: Service account tokens don't expire like user tokens
2. **No Permission Problems**: Service account has consistent permissions
3. **No User Dependency**: Operations work even if user is offline

## Migration Path

### Phase 1: Keep Current PayProp (No Changes)
- Your existing PayProp OAuth2 continues working
- Tag creation/deletion unchanged
- No disruption to current operations

### Phase 2: Add Google Service Account
- Implement service account for Google operations
- Run in parallel with existing Google user auth
- Gradually migrate Google operations to service account

### Phase 3: Simplify Architecture
- Remove Google user authentication requirements
- Use service account for all Google operations
- Clean up OAuth2 complexity

## Example: Tag Creation Flow

```java
// Current complex flow with potential failures
public void createPortfolioTag(Portfolio portfolio, User user) {
    // ❌ Check if user has Google auth - can fail
    if (!googleAuthService.hasValidToken(user)) {
        throw new RuntimeException("Please authenticate with Google first");
    }

    // ❌ PayProp operation - can fail
    String tagId = payPropApiClient.createTag(portfolio.getName());

    // ❌ Google operation using user token - can fail if token expired
    googleSheetsService.updateTrackingSheet(user.getGoogleToken(), tagId);
}

// New clean flow with service account
public void createPortfolioTag(Portfolio portfolio, String userEmail) {
    // ✅ PayProp operation - isolated failure
    String tagId = payPropApiClient.createTag(portfolio.getName());

    // ✅ Google operation using service account - always works
    googleDocumentationService.documentTagCreation(userEmail, portfolio.getName(), tagId);
}
```

## Configuration Update

```properties
# PayProp stays the same
payprop.oauth2.client-id=${PAYPROP_CLIENT_ID}
payprop.oauth2.client-secret=${PAYPROP_CLIENT_SECRET}

# Add Google service account
google.service.account.key=${GOOGLE_SERVICE_ACCOUNT_KEY}
google.service.account.enabled=true

# Disable Google user OAuth for backend operations
google.user.oauth.required=false
```

## Summary

Google Service Account makes PayProp integration **significantly easier** because:

1. **Separation of Concerns**: PayProp handles property operations, Google handles documentation
2. **Reduced Complexity**: No need to coordinate multiple OAuth flows
3. **Better Reliability**: Service account doesn't have user-dependent failures
4. **Easier Testing**: Can test PayProp operations without Google auth setup

Your PayProp tag creation/deletion will be **unchanged** and **more reliable** with this setup.
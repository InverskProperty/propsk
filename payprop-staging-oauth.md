# PayProp OAuth Staging Configuration

## Step 1: PayProp Developer Console Setup

### 1.1 Access PayProp Developer Portal
```bash
# Login to PayProp Developer Portal
https://developer.payprop.com
# Or contact PayProp support if you don't have developer access
```

### 1.2 Create Staging Application
1. Navigate to "Applications" or "OAuth Applications"
2. Click "Create New Application"
3. Application Details:
   - **Name**: `CRM Staging Environment`
   - **Description**: `Development and testing environment for CRM integration`
   - **Environment**: `Staging` or `Development`
   - **Application Type**: `Web Application`

### 1.3 Configure OAuth Settings
```bash
# Redirect URLs for Staging
Redirect URI: https://crm-staging.onrender.com/payprop/oauth/callback
Webhook URL: https://crm-staging.onrender.com/payprop/webhooks/callback

# Scopes Required
- financials:read
- properties:read  
- tenants:read
- owners:read
- transactions:read
- payments:read
```

### 1.4 Get Staging Credentials
```bash
# PayProp will provide staging credentials:
Client ID:     staging-client-id-xxxxx
Client Secret: staging-client-secret-xxxxx
API Base URL:  https://api.payprop.com (or staging URL if different)
```

## Step 2: Environment Configuration

### 2.1 Update .env.staging
```properties
# PayProp OAuth Staging Configuration
PAYPROP_OAUTH_CLIENT_ID=staging-client-id-xxxxx
PAYPROP_OAUTH_CLIENT_SECRET=staging-client-secret-xxxxx
PAYPROP_OAUTH_REDIRECT_URI=https://crm-staging.onrender.com/payprop/oauth/callback

# PayProp API Configuration
PAYPROP_API_BASE_URL=https://api.payprop.com
PAYPROP_API_VERSION=v1
PAYPROP_API_TIMEOUT=30000

# Rate Limiting (relaxed for staging)
API_RATE_LIMIT_ENABLED=false
PAYPROP_API_RATE_LIMIT_REQUESTS_PER_MINUTE=100
```

### 2.2 Update Render Environment Variables
```bash
# In Render Dashboard → Environment Variables
PAYPROP_OAUTH_CLIENT_ID=staging-client-id-xxxxx
PAYPROP_OAUTH_CLIENT_SECRET=staging-client-secret-xxxxx
PAYPROP_OAUTH_REDIRECT_URI=https://crm-staging.onrender.com/payprop/oauth/callback
PAYPROP_API_BASE_URL=https://api.payprop.com
```

## Step 3: OAuth Flow Testing

### 3.1 Initialize OAuth Flow
```bash
# Start OAuth authorization
curl https://crm-staging.onrender.com/payprop/oauth/authorize
# This redirects to PayProp authorization page
```

### 3.2 Complete OAuth Authorization
1. Visit staging app: `https://crm-staging.onrender.com`
2. Click "Connect PayProp Account" or navigate to PayProp integration
3. Authorize with PayProp staging credentials
4. Verify successful callback: `https://crm-staging.onrender.com/payprop/oauth/callback`

### 3.3 Verify OAuth Token Storage
```bash
# Check stored tokens
curl https://crm-staging.onrender.com/admin/staging/oauth/status
# Should show active OAuth tokens for PayProp
```

## Step 4: Data Sync Configuration

### 4.1 Configure Sync Service for Staging
```java
// In PayPropFinancialSyncService.java - staging profile
@Profile("staging")
@Service
public class PayPropStagingFinancialSyncService extends PayPropFinancialSyncService {
    
    @Override
    public void performComprehensiveFinancialSync() {
        System.out.println("🚀 STAGING: Starting comprehensive PayProp sync...");
        
        // Enhanced logging for staging
        try {
            super.performComprehensiveFinancialSync();
            System.out.println("✅ STAGING: PayProp sync completed successfully");
        } catch (Exception e) {
            System.err.println("❌ STAGING: PayProp sync failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
```

### 4.2 Staging-Specific Sync Settings
```properties
# In application-staging.properties
# PayProp sync configuration for staging
crm.payprop.sync.batch-size=50
crm.payprop.sync.timeout-minutes=30
crm.payprop.sync.retry-attempts=3
crm.payprop.sync.date-range-days=365

# Test mode settings
crm.financial.sync.test-mode=true
crm.payprop.sync.dry-run=false
crm.payprop.sync.validate-data=true
```

## Step 5: API Endpoints for Staging Data Management

### 5.1 Full PayProp Sync
```bash
# Execute comprehensive sync from PayProp
curl -X POST https://crm-staging.onrender.com/admin/staging/sync/full \
  -H "Content-Type: application/json" \
  -d '{"scope": "all", "dateRange": "last12months"}'

# Expected Response:
{
  "status": "success",
  "message": "Full PayProp sync completed",
  "statistics": {
    "properties": 25,
    "financial_transactions": 1240,
    "properties_with_payprop_id": 25,
    "recent_transactions_30_days": 89
  }
}
```

### 5.2 Incremental Sync  
```bash
# Sync recent changes only (last 30 days)
curl -X POST https://crm-staging.onrender.com/admin/staging/sync/incremental

# Custom date range
curl -X POST https://crm-staging.onrender.com/admin/staging/sync/incremental \
  -H "Content-Type: application/json" \
  -d '{"fromDate": "2024-01-01", "toDate": "2024-03-31"}'
```

### 5.3 OAuth Status Check
```bash
# Check PayProp OAuth connection status
curl https://crm-staging.onrender.com/admin/staging/oauth/payprop/status

# Refresh OAuth tokens if needed
curl -X POST https://crm-staging.onrender.com/admin/staging/oauth/payprop/refresh
```

## Step 6: Data Validation and Testing

### 6.1 Validate Sync Data
```bash
# Check sync statistics
curl https://crm-staging.onrender.com/admin/staging/stats

# Validate specific property data
curl https://crm-staging.onrender.com/admin/staging/validate/properties

# Validate financial transactions
curl https://crm-staging.onrender.com/admin/staging/validate/transactions
```

### 6.2 Test Data Queries
```sql
-- Connect to Railway staging database
-- Verify PayProp data import

-- Check properties with PayProp IDs
SELECT id, payprop_property_id, address, rental_amount 
FROM properties 
WHERE payprop_property_id IS NOT NULL;

-- Check financial transactions from PayProp
SELECT id, payprop_transaction_id, transaction_type, amount, transaction_date
FROM financial_transactions 
WHERE payprop_transaction_id IS NOT NULL
ORDER BY transaction_date DESC
LIMIT 20;

-- Check OAuth users
SELECT id, email, access_token IS NOT NULL as has_token, 
       refresh_token IS NOT NULL as has_refresh_token,
       created_at
FROM oauth_users
WHERE provider = 'payprop';
```

## Step 7: Troubleshooting Common Issues

### 7.1 OAuth Authorization Failures
```bash
# Check logs for OAuth errors
curl https://crm-staging.onrender.com/admin/staging/logs/oauth

# Common Issues:
# - Incorrect redirect URI in PayProp console
# - Invalid client credentials  
# - Staging vs production endpoint mismatch
```

### 7.2 Data Sync Issues
```bash
# Check sync error logs
curl https://crm-staging.onrender.com/admin/staging/logs/sync

# Retry failed sync operations
curl -X POST https://crm-staging.onrender.com/admin/staging/sync/retry-failed
```

### 7.3 Rate Limiting Issues
```bash
# Check API rate limit status
curl https://crm-staging.onrender.com/admin/staging/ratelimit/status

# Adjust rate limiting for staging
# Set API_RATE_LIMIT_ENABLED=false in environment variables
```

## Step 8: Security Considerations

### 8.1 Staging Data Isolation
- Staging OAuth tokens are separate from production
- Staging database is isolated on Railway
- PayProp staging environment has limited/test data
- No production customer data in staging

### 8.2 Access Control
```bash
# Staging endpoints are protected by @Profile("staging")
# Only available when SPRING_PROFILES_ACTIVE=staging

# Admin endpoints require authorization header
curl -H "Authorization: Bearer staging-admin-token" \
  https://crm-staging.onrender.com/admin/staging/stats
```

## Quick Setup Commands

```bash
# 1. Set up PayProp staging OAuth credentials in Render
# 2. Deploy staging app with updated environment variables
# 3. Initialize OAuth connection
curl https://crm-staging.onrender.com/payprop/oauth/authorize

# 4. Execute initial data sync
curl -X POST https://crm-staging.onrender.com/admin/staging/sync/full

# 5. Verify data import
curl https://crm-staging.onrender.com/admin/staging/stats
```

This configuration provides secure, isolated PayProp integration for your staging environment with comprehensive data sync capabilities.
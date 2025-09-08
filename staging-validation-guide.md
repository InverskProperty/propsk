# Staging Environment Validation Guide

## Overview

This guide provides comprehensive testing procedures to validate your CRM staging environment after deployment. It covers database connectivity, PayProp integration, data synchronization, and performance validation.

## Prerequisites

### Environment Setup
- ✅ Railway MySQL database configured
- ✅ Render deployment active
- ✅ PayProp OAuth credentials configured
- ✅ Environment variables set correctly

### Required Tools
```bash
# Install required tools for testing
curl --version    # HTTP client for API testing
jq --version      # JSON processor for response validation
bc --version      # Basic calculator for performance metrics

# If not installed:
# macOS: brew install curl jq bc
# Ubuntu: apt-get install curl jq bc
# Windows: Use WSL or Git Bash
```

## Quick Validation (5 minutes)

### Step 1: Basic Health Check
```bash
# Test staging environment is responding
curl https://crm-staging.onrender.com/admin/staging/health

# Expected response:
{
  "status": "healthy",
  "database": "connected", 
  "environment": "staging",
  "counts": {...}
}
```

### Step 2: Environment Validation
```bash
# Verify correct Spring profile
curl https://crm-staging.onrender.com/actuator/env | jq '.propertySources[] | select(.name == "systemEnvironment") | .properties.SPRING_PROFILES_ACTIVE'

# Expected: "staging"
```

### Step 3: Database Connection Test
```bash
# Check database statistics
curl https://crm-staging.onrender.com/admin/staging/stats

# Should return counts for all entities (may be 0 initially)
{
  "users": 0,
  "customers": 0,
  "properties": 0,
  "financial_transactions": 0
}
```

## Comprehensive Testing (15 minutes)

### Run Automated Test Suite
```bash
# Make test script executable
chmod +x staging-test-suite.sh

# Run comprehensive tests
./staging-test-suite.sh

# Or with custom staging URL
STAGING_URL="https://your-staging-app.onrender.com" ./staging-test-suite.sh
```

### Manual Validation Steps

#### 1. Test User Creation
```bash
# Create test users
curl -X POST https://crm-staging.onrender.com/admin/staging/users/create-test-users

# Verify creation
curl https://crm-staging.onrender.com/admin/staging/stats
# Should show users > 0, customers > 0
```

#### 2. Test Database Reset (Safe in Staging)
```bash
# Reset database to clean state
curl -X POST https://crm-staging.onrender.com/admin/staging/database/reset

# Verify reset worked
curl https://crm-staging.onrender.com/admin/staging/stats
# Should show all counts = 0
```

#### 3. Test PayProp Integration Endpoints
```bash
# Check if PayProp sync endpoints are available
curl -I https://crm-staging.onrender.com/admin/staging/sync/full
# Should return HTTP 200 or 405 (method not allowed)

curl -I https://crm-staging.onrender.com/admin/staging/sync/incremental  
# Should return HTTP 200 or 405 (method not allowed)
```

## PayProp Data Synchronization Testing

### Phase 1: OAuth Setup Validation
```bash
# 1. Start OAuth flow
curl https://crm-staging.onrender.com/payprop/oauth/authorize
# Should redirect to PayProp authorization page

# 2. After OAuth completion, verify token storage
curl https://crm-staging.onrender.com/admin/staging/oauth/status
# Should show active OAuth tokens

# 3. Check OAuth user creation
curl https://crm-staging.onrender.com/admin/staging/stats
# Should show users with OAuth records
```

### Phase 2: Initial Data Sync
```bash
# Execute full PayProp sync
curl -X POST https://crm-staging.onrender.com/admin/staging/sync/full

# Monitor sync progress (may take 2-5 minutes)
# Check logs: In Render Dashboard → Logs

# Verify sync results
curl https://crm-staging.onrender.com/admin/staging/stats
# Should show:
# - properties > 0
# - financial_transactions > 0  
# - properties_with_payprop_id > 0
```

### Phase 3: Data Validation Queries
```sql
-- Connect to Railway staging database
-- Verify PayProp integration data

-- 1. Check properties imported from PayProp
SELECT 
    id, 
    payprop_property_id, 
    address, 
    rental_amount,
    created_at
FROM properties 
WHERE payprop_property_id IS NOT NULL
LIMIT 10;

-- 2. Check financial transactions from PayProp
SELECT 
    id,
    payprop_transaction_id,
    transaction_type,
    amount,
    transaction_date,
    description
FROM financial_transactions 
WHERE payprop_transaction_id IS NOT NULL
ORDER BY transaction_date DESC
LIMIT 20;

-- 3. Check OAuth users
SELECT 
    id,
    email,
    provider,
    access_token IS NOT NULL as has_access_token,
    refresh_token IS NOT NULL as has_refresh_token,
    created_at
FROM oauth_users
WHERE provider = 'payprop';

-- 4. Validate data relationships
SELECT 
    p.id as property_id,
    p.address,
    COUNT(ft.id) as transaction_count,
    SUM(ft.amount) as total_amount
FROM properties p
LEFT JOIN financial_transactions ft ON ft.property_id = p.id
WHERE p.payprop_property_id IS NOT NULL
GROUP BY p.id, p.address
ORDER BY transaction_count DESC
LIMIT 10;
```

## Performance & Load Testing

### Response Time Testing
```bash
# Test API response times
echo "Testing response times..."

for endpoint in "/admin/staging/health" "/admin/staging/stats"; do
    echo "Testing $endpoint"
    curl -w "Response Time: %{time_total}s\nHTTP Code: %{http_code}\n" \
         -s -o /dev/null \
         "https://crm-staging.onrender.com$endpoint"
    echo "---"
done
```

### Concurrent Request Testing  
```bash
# Test concurrent requests (basic load test)
echo "Testing concurrent requests..."

for i in {1..5}; do
    curl -s https://crm-staging.onrender.com/admin/staging/health > /dev/null &
done
wait

echo "Concurrent test completed"
```

## Troubleshooting Common Issues

### Database Connection Issues
```bash
# Check database connectivity
curl https://crm-staging.onrender.com/admin/staging/health

# If database shows "disconnected":
# 1. Verify Railway database is running
# 2. Check SPRING_DATASOURCE_URL in Render environment variables
# 3. Verify Railway database allows external connections
# 4. Check Render application logs for JDBC errors
```

### PayProp Integration Issues
```bash
# Check OAuth setup
curl https://crm-staging.onrender.com/admin/staging/oauth/status

# If OAuth fails:
# 1. Verify PAYPROP_OAUTH_CLIENT_ID and CLIENT_SECRET
# 2. Check redirect URI matches PayProp console: 
#    https://crm-staging.onrender.com/payprop/oauth/callback
# 3. Verify PayProp staging environment is accessible
```

### Environment Configuration Issues
```bash
# Check active Spring profile
curl https://crm-staging.onrender.com/actuator/env | grep -i spring.profiles.active

# If not "staging":
# 1. Check SPRING_PROFILES_ACTIVE=staging in Render environment variables
# 2. Restart Render service after changing environment variables
# 3. Check application startup logs
```

### Data Sync Issues
```bash
# Check sync logs
# In Render Dashboard → Logs, look for:
# - "STAGING: Starting comprehensive PayProp sync"
# - "PayProp API" related messages
# - Error messages related to sync operations

# Common issues:
# - PayProp API rate limiting
# - Invalid OAuth tokens (expired/revoked)
# - Network connectivity issues
# - Data validation errors
```

## Success Criteria Checklist

### ✅ Basic Environment
- [ ] Staging app responds at https://crm-staging.onrender.com
- [ ] Health endpoint returns "healthy" status
- [ ] Database shows "connected" status  
- [ ] Environment shows "staging"
- [ ] Management endpoints accessible

### ✅ Data Layer
- [ ] Test users can be created successfully
- [ ] Database reset works (all counts return to 0)
- [ ] Statistics endpoint returns all entity counts
- [ ] Data operations reflected in statistics

### ✅ PayProp Integration  
- [ ] OAuth authorization flow accessible
- [ ] OAuth tokens stored successfully
- [ ] Full sync endpoint available
- [ ] Incremental sync endpoint available
- [ ] Sync operations complete without errors

### ✅ Data Validation
- [ ] Properties imported with PayProp IDs
- [ ] Financial transactions imported with PayProp IDs  
- [ ] OAuth users created with valid tokens
- [ ] Data relationships maintained correctly

### ✅ Performance
- [ ] API endpoints respond within 5 seconds
- [ ] Concurrent requests handled properly
- [ ] Sync operations complete within reasonable time (< 10 minutes)
- [ ] No memory/resource issues in logs

## Staging Environment Ready!

Once all validation steps pass, your staging environment is ready for development use:

```bash
# Final validation command
curl https://crm-staging.onrender.com/admin/staging/health | jq

# Should return:
{
  "status": "healthy",
  "database": "connected",
  "environment": "staging", 
  "counts": {
    "users": 3,           # Test users created
    "customers": 2,       # Test customers created  
    "properties": 25,     # Imported from PayProp
    "transactions": 1240  # Imported from PayProp
  }
}
```

### Next Development Steps
1. ✅ Begin feature development on staging
2. ✅ Test new features with real PayProp data  
3. ✅ Use database reset for clean testing states
4. ✅ Deploy feature branches to staging for testing
5. ✅ Merge to main when staging tests pass

Your staging environment is now a complete, isolated replica of production with real PayProp data for development and testing!
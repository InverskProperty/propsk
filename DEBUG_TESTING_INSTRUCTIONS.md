# 🚨 PayProp Tag Creation Debug Testing

## Overview
This document provides step-by-step instructions to test the enhanced PayProp tag creation fix.

## Prerequisites
1. ✅ Application is running on `localhost:8080`
2. ✅ PayProp integration is enabled (`PAYPROP_ENABLED=true`)
3. ✅ Valid PayProp OAuth2 credentials configured
4. ✅ User is authenticated (has valid session)

## Debug Endpoints Added

### 1. Test Tag Creation Only
```bash
POST /portfolio/internal/payprop/debug/test-tag-creation
Parameter: tagName (string)
```

### 2. Test Full Portfolio Creation
```bash
POST /portfolio/internal/payprop/debug/test-portfolio-creation  
Parameter: portfolioName (string)
```

## Running the Tests

### Method 1: Using the Test Script
```bash
# Make script executable
chmod +x test-tag-creation.sh

# Run the test
./test-tag-creation.sh
```

### Method 2: Manual cURL Commands

#### Test 1: Basic Tag Creation
```bash
curl -X POST "http://localhost:8080/portfolio/internal/payprop/debug/test-tag-creation" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "tagName=DEBUG-TEST-TAG-$(date +%s)" \
  -w "\nHTTP Status: %{http_code}\n"
```

#### Test 2: Portfolio Creation  
```bash
curl -X POST "http://localhost:8080/portfolio/internal/payprop/debug/test-portfolio-creation" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "portfolioName=DEBUG-PORTFOLIO-$(date +%s)" \
  -w "\nHTTP Status: %{http_code}\n"
```

### Method 3: Using Postman/Browser
1. Open Postman or use browser dev tools
2. Create POST request to debug endpoints
3. Add form parameter with test name
4. Execute and check response

## Expected Results

### Success Case
```json
{
  "success": true,
  "message": "Tag creation test completed successfully",
  "tagId": "90JYeBeJor",  // ← This should be populated!
  "tagName": "DEBUG-TEST-TAG-1692123456",
  "tagDescription": "Created by CRM for portfolio organization"
}
```

### Failure Cases
Look for specific error messages:
- `"PayProp authentication failed"` → OAuth2 token issue
- `"PayProp API error"` → API connectivity/format issue  
- `"PayProp response missing tag ID"` → Response parsing issue

## Log Analysis

### Key Log Messages to Look For

#### ✅ Success Indicators
```
🏷️ Starting tag creation/verification for: 'DEBUG-TEST-TAG-1692123456'
✅ PayProp authentication validated
📝 Creating new PayProp tag: 'DEBUG-TEST-TAG-1692123456'
API Request: POST /tags with payload: {name=DEBUG-TEST-TAG-1692123456, description=Created by CRM for portfolio organization}
API Response: {id=90JYeBeJor, name=DEBUG-TEST-TAG-1692123456, ...}
Found tag ID in field 'id': '90JYeBeJor'
✅ Successfully created PayProp tag: 'DEBUG-TEST-TAG-1692123456' with external ID: '90JYeBeJor'
```

#### ❌ Failure Indicators
```
❌ CRITICAL: Authentication validation failed: OAuth2 service not available
❌ PayProp API error: HTTP 401 - {"error": "unauthorized"}
❌ Could not find tag ID in response: {error=some_error_message}
❌ CRITICAL: Tag creation failed for 'DEBUG-TEST-TAG-1692123456': ...
```

## Troubleshooting

### Issue 1: Authentication Errors
**Symptoms**: `PayProp authentication failed`
**Solutions**:
1. Check OAuth2 tokens are valid: `GET /payprop/oauth/status`
2. Re-authorize if needed: `GET /payprop/oauth/authorize`
3. Verify environment variables: `PAYPROP_CLIENT_ID`, `PAYPROP_CLIENT_SECRET`

### Issue 2: API Connectivity Errors  
**Symptoms**: `PayProp API error`, network timeouts
**Solutions**:
1. Check PayProp API base URL: `https://ukapi.staging.payprop.com/api/agency/v1.1`
2. Verify network connectivity to PayProp
3. Check if PayProp staging environment is operational

### Issue 3: Response Parsing Errors
**Symptoms**: `PayProp response missing tag ID`
**Solutions**:
1. Check PayProp API response format in logs
2. Verify the `extractTagId()` method handles all response field variations
3. Add additional field names to the `possibleFields` array if needed

### Issue 4: Database/Transaction Errors
**Symptoms**: Portfolio creation fails, duplicate key errors
**Solutions**:
1. Check database connection
2. Verify portfolio name uniqueness
3. Check transaction rollback issues

## Next Steps After Testing

### If Tests Pass ✅
1. **Fix existing broken portfolios**: Run migration service
2. **Test property assignments**: Verify sync works end-to-end
3. **Deploy to production**: After thorough validation

### If Tests Fail ❌  
1. **Analyze specific error**: Use log indicators above
2. **Fix identified issue**: Authentication, API, or parsing
3. **Re-test**: Repeat until successful
4. **Document resolution**: Update this guide with findings

## Database Verification

After successful test, verify database state:

```sql
-- Check test portfolio was created with PayProp data
SELECT id, name, payprop_tags, payprop_tag_names, sync_status 
FROM portfolios 
WHERE name LIKE 'DEBUG-PORTFOLIO-%' 
ORDER BY created_at DESC 
LIMIT 5;

-- Should show:
-- payprop_tags: NOT NULL (has external ID like "90JYeBeJor")
-- payprop_tag_names: NOT NULL (has readable name)
-- sync_status: 'synced'
```

## Success Criteria

The fix is working correctly when:
- [ ] Tag creation endpoint returns `success: true`
- [ ] Response includes valid `tagId` (not null/empty)
- [ ] Portfolio creation succeeds with PayProp sync
- [ ] Database shows `payprop_tags` populated (not NULL)
- [ ] Database shows `sync_status = 'synced'`
- [ ] Logs show successful API calls and responses
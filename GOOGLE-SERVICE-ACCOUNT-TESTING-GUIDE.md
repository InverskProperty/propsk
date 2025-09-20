# Google Service Account Testing Guide

## Quick Test URLs (after starting your application)

### 1. Health Check
```
GET http://localhost:8080/api/test/google-service-account/health
```
**Expected**: `{"status":"UP","service":"Google Service Account Test Controller"}`

### 2. Service Account Info
```
GET http://localhost:8080/api/test/google-service-account/info
```
**Expected**: Shows status of Drive, Sheets, and Gmail services

### 3. Full Connectivity Test
```
GET http://localhost:8080/api/test/google-service-account/connectivity
```
**Expected**: Tests all Google APIs and returns detailed results

### 4. Create Test Folder
```
POST http://localhost:8080/api/test/google-service-account/create-folder
Content-Type: application/x-www-form-urlencoded

customerEmail=test@example.com&customerName=Test Customer
```
**Expected**: Creates folder in Google Drive and returns folder ID

### 5. Create Test Sheet
```
POST http://localhost:8080/api/test/google-service-account/create-sheet
Content-Type: application/x-www-form-urlencoded

title=My Test Sheet
```
**Expected**: Creates Google Sheet with test data and returns sheet ID

### 6. Test Integrated Workflow (requires existing portfolio)
```
POST http://localhost:8080/api/test/google-service-account/integrated-workflow
Content-Type: application/x-www-form-urlencoded

portfolioId=1&initiatedByEmail=admin@example.com
```
**Expected**: Runs PayProp + Google integration for portfolio

## Step-by-Step Testing Instructions

### Step 1: Start Your Application
```bash
# In your project directory
mvn spring-boot:run
# OR
java -jar target/your-app.jar
```

### Step 2: Test Basic Connectivity
Open a web browser or use curl:
```bash
curl http://localhost:8080/api/test/google-service-account/health
```

### Step 3: Test Google Service Account
```bash
curl http://localhost:8080/api/test/google-service-account/connectivity
```

**Success Response:**
```json
{
  "driveTest": {"status": "SUCCESS", "filesCount": "5"},
  "sheetsTest": {"status": "SUCCESS", "testSheetId": "1AbC..."},
  "overallStatus": "SUCCESS",
  "timestamp": "2025-09-20T..."
}
```

### Step 4: Create Test Google Drive Folder
```bash
curl -X POST "http://localhost:8080/api/test/google-service-account/create-folder" \
  -d "customerEmail=john.doe@example.com&customerName=John Doe"
```

### Step 5: Create Test Google Sheet
```bash
curl -X POST "http://localhost:8080/api/test/google-service-account/create-sheet" \
  -d "title=CRM Integration Test Sheet"
```

### Step 6: Test PayProp Integration (if you have portfolios)
```bash
curl -X POST "http://localhost:8080/api/test/google-service-account/integrated-workflow" \
  -d "portfolioId=1&initiatedByEmail=admin@yourcompany.com"
```

## Troubleshooting

### Common Issues and Solutions

#### 1. "Service account key not found"
**Problem**: `GOOGLE_SERVICE_ACCOUNT_KEY` environment variable not set correctly
**Solution**:
- Verify the environment variable is set in your production environment
- Check that the JSON is properly escaped
- Restart your application after setting the variable

#### 2. "Access denied" or "Authentication failed"
**Problem**: Service account doesn't have proper permissions
**Solutions**:
- Verify the service account has required API access enabled
- Check Google Cloud Console > IAM & Admin > Service Accounts
- Ensure APIs are enabled: Drive API, Sheets API, Gmail API

#### 3. "Quota exceeded"
**Problem**: Too many API calls
**Solutions**:
- Check Google Cloud Console > APIs & Services > Quotas
- Wait a few minutes and try again
- Implement rate limiting in production

#### 4. "Integration service not available"
**Problem**: PayPropGoogleIntegrationService isn't loading
**Solutions**:
- Check application logs for startup errors
- Verify all dependencies are properly imported
- Ensure `@Service` annotations are present

## What Each Test Does

### `/connectivity`
- Tests Drive API by listing files
- Tests Sheets API by creating a temporary spreadsheet
- Returns comprehensive status report

### `/create-folder`
- Creates customer folder structure in Google Drive
- Tests folder creation and organization
- Returns folder ID for verification

### `/create-sheet`
- Creates a Google Sheets spreadsheet with test data
- Tests spreadsheet creation and data writing
- Returns sheet ID and viewable URL

### `/integrated-workflow`
- Tests the complete PayProp + Google workflow
- Uses existing portfolio data
- Creates PayProp tags AND Google documentation
- Shows end-to-end integration

## Expected Google Drive Structure

After successful tests, you should see this structure in Google Drive:

```
📁 CRM Property Management/
  📁 Customer-test@example.com/
    📊 Portfolio Tracking - [Customer] - [Portfolio]
    📊 Tag Operations Log - [Portfolio]
    📊 Property Summary - [Portfolio]
  📊 CRM Test Sheet - [timestamp]
  📊 Various test sheets from API calls
```

## Production Deployment Checklist

- [ ] `GOOGLE_SERVICE_ACCOUNT_KEY` environment variable set
- [ ] Google Cloud APIs enabled (Drive, Sheets)
- [ ] Service account has proper permissions
- [ ] Application starts without errors
- [ ] `/health` endpoint returns UP
- [ ] `/connectivity` test passes
- [ ] PayProp integration works
- [ ] Google documentation creates successfully

## Monitoring in Production

1. **Check logs** for Google API errors
2. **Monitor quota usage** in Google Cloud Console
3. **Test integration** periodically
4. **Verify folder structure** in Google Drive
5. **Check sheet creation** for new portfolios

## Next Steps After Testing

1. **Update existing controllers** to use new integration service
2. **Add proper error handling** for production scenarios
3. **Implement rate limiting** for Google API calls
4. **Set up monitoring** and alerting
5. **Create documentation** for users
6. **Schedule regular tests** to ensure continued functionality
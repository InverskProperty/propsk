# Render Staging Deployment Setup Guide

## Step 1: Render Dashboard Configuration

### 1.1 Create New Web Service
1. Login to [Render Dashboard](https://dashboard.render.com)
2. Click "New +" → "Web Service"
3. Connect your GitHub repository
4. Service Configuration:
   - **Name**: `crm-staging`
   - **Environment**: `Java`
   - **Plan**: `Starter` ($7/month)
   - **Region**: `Ohio` (US East)

### 1.2 Build & Deploy Settings
```yaml
# Build Configuration
Build Command: mvn clean package -DskipTests -Pstaging
Start Command: java -jar target/*.jar --spring.profiles.active=staging

# Advanced Settings
Health Check Path: /admin/staging/health
Auto-Deploy: Yes (on git push)
Branch: main
```

## Step 2: Environment Variables Setup

### 2.1 Core Application Settings
```bash
# In Render Dashboard → Environment Variables
SPRING_PROFILES_ACTIVE=staging
CRM_DATA_SOURCE=PAYPROP_STAGING  
CRM_DEBUG_MODE=true
CRM_FINANCIAL_SYNC_TEST_MODE=true
SERVER_PORT=10000
```

### 2.2 Database Configuration (Railway MySQL)
```bash
# Database Connection - Replace with your Railway details
SPRING_DATASOURCE_URL=jdbc:mysql://containers-us-west-xxx.railway.app:6543/railway?createDatabaseIfNotExist=true&useSSL=true&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=[YOUR_RAILWAY_PASSWORD]

# JPA Settings for Staging
SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop
SPRING_SQL_INIT_MODE=always
SPRING_JPA_SHOW_SQL=true
SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.MySQL8Dialect
```

### 2.3 PayProp API Configuration (Staging)
```bash
# PayProp OAuth - Staging Credentials
PAYPROP_OAUTH_CLIENT_ID=[YOUR_STAGING_PAYPROP_CLIENT_ID]
PAYPROP_OAUTH_CLIENT_SECRET=[YOUR_STAGING_PAYPROP_CLIENT_SECRET]  
PAYPROP_OAUTH_REDIRECT_URI=https://crm-staging.onrender.com/payprop/oauth/callback

# PayProp API Settings
PAYPROP_API_BASE_URL=https://api.payprop.com
PAYPROP_API_VERSION=v1
PAYPROP_API_TIMEOUT=30000
```

### 2.4 Google OAuth Configuration (Staging)
```bash
# Google OAuth - Staging Credentials
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=[STAGING_GOOGLE_CLIENT_ID]
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=[STAGING_GOOGLE_CLIENT_SECRET]
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI=https://crm-staging.onrender.com/login/oauth2/code/google

# Google OAuth Scopes
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_SCOPE=openid,email,profile,https://www.googleapis.com/auth/gmail.send,https://www.googleapis.com/auth/drive,https://www.googleapis.com/auth/spreadsheets
```

### 2.5 Logging & Monitoring
```bash
# Enhanced Logging for Staging
LOGGING_LEVEL_SITE_EASY_TO_BUILD_CRM=DEBUG
LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=DEBUG
LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG

# Management Endpoints
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics,env
MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always
MANAGEMENT_SECURITY_ENABLED=false
```

## Step 3: Deploy from Repository

### 3.1 Prepare Repository
```bash
# Ensure these files are in your repo
cp render-staging-deploy.yml render.yaml  # Copy staging config to render.yaml
git add render.yaml
git commit -m "Add staging deployment configuration"
git push origin main
```

### 3.2 Manual Deploy (if needed)
```bash
# In Render Dashboard
Manual Deploy → Deploy Latest Commit
# Or use Render CLI
render deploy --service=crm-staging
```

## Step 4: Post-Deployment Setup

### 4.1 Verify Deployment
```bash
# Check health endpoint
curl https://crm-staging.onrender.com/admin/staging/health

# Expected response:
{
  "status": "healthy",
  "database": "connected",
  "environment": "staging",
  "counts": {
    "users": 0,
    "customers": 0,
    "properties": 0,
    "transactions": 0
  }
}
```

### 4.2 Initialize Staging Data
```bash
# Create test users
curl -X POST https://crm-staging.onrender.com/admin/staging/users/create-test-users

# Execute full PayProp sync
curl -X POST https://crm-staging.onrender.com/admin/staging/sync/full
```

## Step 5: Custom Domain Setup (Optional)

### 5.1 Add Custom Domain
```bash
# In Render Dashboard → Settings → Custom Domains
staging.yourcrm.com → crm-staging.onrender.com

# SSL Certificate will be auto-generated
```

### 5.2 Update OAuth Redirect URLs
```bash
# Update in Google OAuth Console
https://staging.yourcrm.com/login/oauth2/code/google

# Update in PayProp Developer Console  
https://staging.yourcrm.com/payprop/oauth/callback
```

## Step 6: Staging Workflow

### 6.1 Development Process
```bash
# 1. Create feature branch
git checkout -b feature/new-feature

# 2. Deploy feature branch to staging
# In Render: Change Branch from 'main' to 'feature/new-feature' 
# Auto-deploys on push

# 3. Test in staging environment
https://crm-staging.onrender.com

# 4. Merge to main when ready
git checkout main
git merge feature/new-feature
git push origin main
```

### 6.2 Data Management Commands
```bash
# Reset staging database
curl -X POST https://crm-staging.onrender.com/admin/staging/database/reset

# Refresh data from PayProp
curl -X POST https://crm-staging.onrender.com/admin/staging/sync/incremental

# Check data statistics
curl https://crm-staging.onrender.com/admin/staging/stats
```

## Step 7: Monitoring & Logs

### 7.1 View Application Logs
```bash
# In Render Dashboard → Logs
# Or use Render CLI
render logs --service=crm-staging --tail
```

### 7.2 Performance Monitoring
```bash
# Access metrics endpoint
curl https://crm-staging.onrender.com/actuator/metrics

# Health checks
curl https://crm-staging.onrender.com/actuator/health
```

## Step 8: Cost Optimization

### 8.1 Sleep Mode
```bash
# Render automatically sleeps free services after 15 minutes
# Paid services ($7/month) don't sleep
# First request after sleep takes ~30 seconds to wake up
```

### 8.2 Resource Usage
```bash
# Monitor in Render Dashboard
# CPU Usage, Memory Usage, Bandwidth
# Upgrade plan if needed (Standard: $25/month, Pro: $85/month)
```

## Quick Commands Reference

```bash
# Staging URLs
App:           https://crm-staging.onrender.com
Health Check:  https://crm-staging.onrender.com/admin/staging/health
Admin Panel:   https://crm-staging.onrender.com/admin/staging/stats

# Data Management
Reset DB:      curl -X POST https://crm-staging.onrender.com/admin/staging/database/reset
Full Sync:     curl -X POST https://crm-staging.onrender.com/admin/staging/sync/full
Test Users:    curl -X POST https://crm-staging.onrender.com/admin/staging/users/create-test-users

# Test Accounts (after setup)
Admin:         admin@staging.local
Property Owner: owner@staging.local
Tenant:        tenant@staging.local
```

Your staging environment will be live at `https://crm-staging.onrender.com` with automatic SSL, monitoring, and isolated data from production.
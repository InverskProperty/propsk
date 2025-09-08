# Staging Environment Setup Guide

## Phase 1: Environment Configuration

### 1. Railway Database Setup
```sql
-- Connect to Railway MySQL
-- Create staging database
CREATE DATABASE crm_staging CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create staging user (if needed)
CREATE USER 'crm_staging'@'%' IDENTIFIED BY 'staging_password';
GRANT ALL PRIVILEGES ON crm_staging.* TO 'crm_staging'@'%';
FLUSH PRIVILEGES;
```

### 2. Render Deployment Configuration
```yaml
# render.yaml for staging
services:
  - type: web
    name: crm-staging
    env: java
    plan: starter
    buildCommand: mvn clean package -DskipTests
    startCommand: java -jar target/app.war
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: staging
      - key: SPRING_DATASOURCE_URL
        fromDatabase:
          name: crm-staging-db
          property: connectionString
      - key: CRMS_DATA_SOURCE
        value: PAYPROP_STAGING
```

### 3. Application Properties - Staging
```properties
# application-staging.properties

# Database Configuration
spring.datasource.url=jdbc:mysql://[railway-host]/crm_staging
spring.datasource.username=[railway-username]
spring.datasource.password=[railway-password]
spring.jpa.hibernate.ddl-auto=create-drop
spring.sql.init.mode=always

# PayProp Integration - Staging
payprop.api.base-url=https://api.payprop.com
payprop.oauth.client-id=[your-staging-client-id]
payprop.oauth.client-secret=[your-staging-client-secret]
payprop.oauth.redirect-uri=https://crm-staging.onrender.com/payprop/oauth/callback

# Google OAuth - Staging
spring.security.oauth2.client.registration.google.client-id=[staging-google-client-id]
spring.security.oauth2.client.registration.google.client-secret=[staging-google-client-secret]
spring.security.oauth2.client.registration.google.redirect-uri=https://crm-staging.onrender.com/login/oauth2/code/google

# Development Features
logging.level.site.easy.to.build.crm=DEBUG
crm.debug.mode=true
crm.financial.sync.test-mode=true
```

## Phase 2: Data Population Strategy

### Option A: PayProp API Full Sync (Recommended)

1. **Deploy Staging App**
   - Empty database, fresh schema
   - All tables created via JPA

2. **Setup Staging PayProp OAuth**
   ```bash
   # Access staging app
   curl https://crm-staging.onrender.com/payprop/oauth/authorize
   # Complete OAuth flow
   ```

3. **Execute Full Data Sync**
   ```bash
   # Trigger comprehensive sync
   curl -X POST https://crm-staging.onrender.com/api/payprop/sync/full \
        -H "Authorization: Bearer [your-token]"
   ```

### Option B: Production Data Clone (Alternative)

1. **Export Production Data** (selective)
   ```sql
   -- Export core tables only (no sensitive data)
   mysqldump production_db \
     properties property_assignments customers \
     financial_transactions batch_payments \
     > staging_data.sql
   ```

2. **Import to Staging**
   ```sql
   mysql crm_staging < staging_data.sql
   ```

## Phase 3: Development Workflow

### 1. Feature Development Process
```bash
# 1. Create feature branch
git checkout -b feature/new-functionality

# 2. Deploy to staging
git push origin feature/new-functionality
# Render auto-deploys staging from this branch

# 3. Test with real PayProp data
# 4. When ready, merge to main for production
```

### 2. Data Refresh Commands
```bash
# Reset staging data anytime
curl -X POST https://crm-staging.onrender.com/admin/reset-database
curl -X POST https://crm-staging.onrender.com/api/payprop/sync/full
```

### 3. Testing Utilities
```bash
# Generate test users
curl -X POST https://crm-staging.onrender.com/admin/create-test-users

# Verify data integrity
curl https://crm-staging.onrender.com/admin/data-health-check

# Financial sync status
curl https://crm-staging.onrender.com/admin/sync-status
```

## Phase 4: Monitoring & Maintenance

### 1. Staging-Specific Features
- Debug endpoints for data inspection
- Test user creation utilities  
- PayProp API rate limit monitoring
- Data refresh automation

### 2. Development Tools
```sql
-- Quick data queries for development
SELECT COUNT(*) FROM properties; 
SELECT COUNT(*) FROM financial_transactions;
SELECT * FROM oauth_users WHERE email LIKE '%@propsk.com';
```

### 3. Automated Sync Schedule
```yaml
# Optional: Scheduled data refresh
# Via GitHub Actions or Render cron
schedule: "0 2 * * *" # Daily at 2 AM
command: curl -X POST https://crm-staging.onrender.com/api/payprop/sync/incremental
```

## Benefits of This Approach

✅ **Real Data**: Current PayProp data via API
✅ **Safe Development**: Isolated from production
✅ **Reproducible**: Can reset/refresh anytime  
✅ **API Testing**: Tests actual PayProp integration
✅ **Cost Effective**: Uses existing Railway/Render setup
✅ **Fast Setup**: 30-60 minutes to full staging environment

## Next Steps

1. Set up staging database on Railway
2. Configure staging app deployment on Render
3. Set up PayProp staging OAuth credentials
4. Execute initial data sync
5. Begin development on staging environment

This gives you a robust staging environment that mirrors production functionality while being completely isolated and safe for development.
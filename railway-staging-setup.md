# Railway MySQL Staging Database Setup

## Step 1: Create Staging Database on Railway

### 1.1 Access Railway Dashboard
```bash
# Login to Railway dashboard
https://railway.app/dashboard
```

### 1.2 Create New MySQL Database
1. Click "New Project" → "Provision MySQL"
2. Name: `crm-staging-db`
3. Environment: `staging`

### 1.3 Get Database Connection Details
```bash
# Railway will provide these connection details:
RAILWAY_HOST=containers-us-west-xxx.railway.app
RAILWAY_PORT=6543
RAILWAY_DATABASE=railway
RAILWAY_USERNAME=root  
RAILWAY_PASSWORD=[generated-password]
RAILWAY_PUBLIC_URL=mysql://root:[password]@containers-us-west-xxx.railway.app:6543/railway
```

## Step 2: Configure Database Connection

### 2.1 Create .env.staging file
```bash
# Copy template and fill values
cp .env.staging.template .env.staging
```

### 2.2 Update Database Configuration
```properties
# In .env.staging - Replace with actual Railway values
SPRING_DATASOURCE_URL=jdbc:mysql://containers-us-west-xxx.railway.app:6543/railway?createDatabaseIfNotExist=true&useSSL=true&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=[your-railway-password]

# Database settings for staging
SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop
SPRING_SQL_INIT_MODE=always
SPRING_JPA_SHOW_SQL=true
```

## Step 3: Initialize Database Schema

### 3.1 Create Initialization SQL
```sql
-- Create staging-specific database (if needed)
CREATE DATABASE IF NOT EXISTS crm_staging 
CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Use the database
USE crm_staging;

-- Railway MySQL is ready - Spring JPA will create tables
-- No manual table creation needed
```

### 3.2 Test Database Connection
```bash
# Test connection from local environment
mysql -h containers-us-west-xxx.railway.app -P 6543 -u root -p railway

# Or using Railway CLI
railway login
railway connect mysql
```

## Step 4: Configure Spring Boot Application

### 4.1 Application Properties for Staging
```properties
# application-staging.properties
spring.profiles.active=staging

# Database Configuration
spring.datasource.url=${SPRING_DATASOURCE_URL}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA Configuration for staging
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect
spring.jpa.properties.hibernate.format_sql=true

# Connection Pool Settings
spring.datasource.hikari.maximum-pool-size=5
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.connection-timeout=20000
```

## Step 5: Data Management Commands

### 5.1 Database Reset (Staging Only)
```bash
# Clear all data - staging environment
curl -X POST https://crm-staging.onrender.com/admin/staging/database/reset \
  -H "Authorization: Bearer staging-token"
```

### 5.2 Health Check
```bash
# Verify database connectivity
curl https://crm-staging.onrender.com/admin/staging/health
```

### 5.3 Data Statistics  
```bash
# Check current data counts
curl https://crm-staging.onrender.com/admin/staging/stats
```

## Step 6: PayProp Data Import

### 6.1 Full Sync from PayProp
```bash
# Execute comprehensive PayProp sync
curl -X POST https://crm-staging.onrender.com/admin/staging/sync/full \
  -H "Authorization: Bearer staging-token"
```

### 6.2 Test Users Creation
```bash
# Create development test users
curl -X POST https://crm-staging.onrender.com/admin/staging/users/create-test-users \
  -H "Authorization: Bearer staging-token"
```

## Step 7: Environment Variables for Railway

### 7.1 Railway Environment Variables
```bash
# Set in Railway dashboard for your service
SPRING_PROFILES_ACTIVE=staging
SPRING_DATASOURCE_URL=${{MySQL.DATABASE_URL}}
SPRING_DATASOURCE_USERNAME=${{MySQL.MYSQL_USER}}
SPRING_DATASOURCE_PASSWORD=${{MySQL.MYSQL_PASSWORD}}

# App specific
CRM_DATA_SOURCE=PAYPROP_STAGING
CRM_DEBUG_MODE=true
```

## Step 8: Monitoring and Maintenance

### 8.1 Daily Data Refresh (Optional)
```bash
# Setup GitHub Action or Railway cron job
# Daily at 2 AM UTC
curl -X POST https://crm-staging.onrender.com/admin/staging/sync/incremental
```

### 8.2 Database Backup (Optional)
```bash
# Export staging data for backup
mysqldump -h containers-us-west-xxx.railway.app -P 6543 -u root -p railway > staging-backup.sql
```

## Next Steps

1. ✅ Create Railway MySQL database 
2. ✅ Configure connection in .env.staging
3. ⏳ Set up Render deployment (next step)
4. ⏳ Configure PayProp OAuth credentials  
5. ⏳ Run initial data sync

This gives you a clean, isolated staging database that can be reset anytime while keeping production data safe.
# Go Live Checklist
## Deploy Production While Keeping Staging for Development

## Current Status ✅
- **Staging Environment**: Currently running with PayProp data
- **Development**: Active in staging environment  
- **Data Source**: PayProp via data import + API integration
- **Ready to Deploy**: Production environment alongside staging

## Pre-Go-Live Setup (1-2 hours)

### 1. Create Production Database
```bash
# In Railway Dashboard
- Create new project: "CRM Production Database"
- Select MySQL database  
- Environment: production
- Note connection details for step 3
```

### 2. Set Up Production PayProp OAuth
```bash
# Contact PayProp or access developer portal
- Create new OAuth application for production
- Name: "CRM Production Environment"  
- Redirect URI: https://yourapp.com/payprop/oauth/callback
- Note Client ID and Secret for step 3
```

### 3. Create Production Render Service
```bash
# In Render Dashboard  
- New Web Service: "crm-production"
- Repository: Same as staging
- Branch: main
- Plan: Professional ($25/month for production)
- Environment Variables:
  - SPRING_PROFILES_ACTIVE=production
  - SPRING_DATASOURCE_URL=[Production Railway URL]
  - PAYPROP_OAUTH_CLIENT_ID=[Production Client ID]
  - PAYPROP_OAUTH_CLIENT_SECRET=[Production Secret]
  - (Copy all production environment variables from application-production.properties)
```

### 4. Configure Production Domain
```bash  
# In Render Dashboard → Custom Domains
- Add custom domain: yourapp.com
- SSL certificate auto-configured
- Update DNS: yourapp.com → crm-production.onrender.com
```

## Go Live Deployment (30 minutes)

### 5. Deploy Production Environment
```bash
# Manual deploy in Render Dashboard
- Production Service → Deploy Latest Commit
- Monitor deployment logs
- Verify health: https://yourapp.com/admin/production/health
```

### 6. Initialize Production Data
```bash
# Set up production PayProp OAuth
# Visit: https://yourapp.com and complete PayProp authorization

# Execute production data sync
curl -X POST https://yourapp.com/admin/production/sync/incremental

# Verify data populated
curl https://yourapp.com/admin/production/stats
```

### 7. Production Validation
```bash
# Test production health
curl https://yourapp.com/admin/production/health
# Should show: "status": "healthy", "environment": "production"

# Test user login flows
# Test PayProp integration
# Test core functionality
```

## Post-Go-Live (Ongoing)

### 8. Verify Both Environments Running
```bash
# Production (live users)
curl https://yourapp.com/admin/production/health

# Staging (development) - continues as before
curl https://staging-yourapp.onrender.com/admin/staging/health
```

### 9. Development Workflow
```bash
# Continue development in staging
git push origin main  # Auto-deploys to staging

# Test new features in staging with PayProp data
https://staging-yourapp.onrender.com

# When ready to release to production
# Manual deploy in Render Dashboard: Production Service → Deploy
```

## Environment URLs After Go-Live

### Production Environment (Live Users)
```bash
URL: https://yourapp.com
Purpose: Live users, property owners, tenants
Database: Production Railway database  
PayProp: Production OAuth credentials
Admin: /admin/production/health (read-only)
Features: Stable, monitored, no database reset
```

### Staging Environment (Development)
```bash  
URL: https://staging-yourapp.onrender.com
Purpose: Development team, feature testing
Database: Staging Railway database (current)
PayProp: Staging OAuth credentials (current)  
Admin: /admin/staging/* (full control, database reset)
Features: All current capabilities, development tools
```

## Cost Summary

### Before Go-Live (Current)
```
Railway Staging DB: $5/month
Render Staging App: $7/month  
Total: $12/month
```

### After Go-Live (Both Environments)
```
Railway Staging DB: $5/month
Railway Production DB: $5/month
Render Staging App: $7/month (Starter)
Render Production App: $25/month (Professional)
Custom Domain: $0 (included)
Total: $42/month
```

## Quick Commands Reference

### Production Management
```bash
# Production health check
curl https://yourapp.com/admin/production/health

# Production data statistics  
curl https://yourapp.com/admin/production/stats

# Production PayProp sync
curl -X POST https://yourapp.com/admin/production/sync/incremental
```

### Staging Management (Unchanged)
```bash
# Staging health check
curl https://staging-yourapp.onrender.com/admin/staging/health

# Reset staging database (development only)
curl -X POST https://staging-yourapp.onrender.com/admin/staging/database/reset

# Full PayProp sync in staging
curl -X POST https://staging-yourapp.onrender.com/admin/staging/sync/full
```

## Safety Features

### Production Protections
- ✅ No database reset endpoints
- ✅ No test user creation
- ✅ Read-only admin endpoints  
- ✅ Conservative sync settings
- ✅ Enhanced error handling
- ✅ Separate database from staging

### Staging Preserved
- ✅ All current functionality maintained
- ✅ Database reset still available
- ✅ Test user creation  
- ✅ Development-friendly logging
- ✅ Independent of production

## Timeline

### Day 1: Setup (1-2 hours)
- Create production database
- Set up production PayProp OAuth
- Configure production Render service
- Test deployment

### Day 2: Go Live (30 minutes)
- Deploy production
- Initialize production data
- Update DNS  
- Validate functionality

### Ongoing: Dual Environment
- **Production**: Live users, stable releases
- **Staging**: Development, testing, feature work

## Emergency Rollback

If issues arise with production deployment:

```bash
# Rollback DNS
# Point yourapp.com back to staging environment temporarily

# Or rollback Render deployment  
# Production Service → Deployments → Redeploy previous version
```

## Success Criteria

### ✅ Production Ready
- [ ] Production app responds at yourapp.com
- [ ] Production health check returns "healthy"  
- [ ] Production PayProp OAuth working
- [ ] Production data synced from PayProp
- [ ] Users can log in and use the app
- [ ] Core functionality working

### ✅ Staging Preserved  
- [ ] Staging continues at staging-yourapp.onrender.com
- [ ] All staging admin endpoints still work
- [ ] Database reset still works in staging
- [ ] PayProp sync still works in staging
- [ ] Can continue development in staging

Once both checkboxes are complete, you're live with production while maintaining your development environment!

**Your staging environment remains exactly as it is now - your development workflow doesn't change. Production runs independently for live users.**
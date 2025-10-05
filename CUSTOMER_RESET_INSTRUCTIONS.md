# Customer Data Reset & Reimport Instructions

## ✅ **Completed Steps**

1. ✅ **SQL Cleanup Script Created**: `database_cleanup_customer_reset.sql`
2. ✅ **PayProp Import Fixed**: Customer creation now properly extracts names from PayProp data
3. ✅ **Code Compiled Successfully**: All changes compiled without errors

---

## 🔄 **Next Steps - Execute in Order**

### **Step 1: Run Database Cleanup Script**

Connect to your MySQL database and run the cleanup script:

```bash
"C:\Program Files\MySQL\MySQL Server 9.3\bin\mysql.exe" -h switchyard.proxy.rlwy.net -P 55090 -u root -p'iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW' railway < C:\Users\sajid\crecrm\database_cleanup_customer_reset.sql
```

**This will:**
- ✅ Backup customers, customer_property_assignments, historical_transactions
- ✅ Clear (truncate) customers table
- ✅ Clear customer_property_assignments table
- ✅ Clear historical_transactions table
- ✅ Keep properties table intact (44/46 already synced with PayProp)
- ✅ Verify PayProp source data is still available

---

### **Step 2: Restart Application**

Restart your Spring Boot application to pick up the code changes:

```bash
# Stop current instance (Ctrl+C if running in terminal)
# Then restart
mvn spring-boot:run
```

---

### **Step 3: Run Fresh PayProp Import**

**Option A: Using Web Interface**
1. Navigate to: `https://spoutproperty-hub.onrender.com/payprop/import`
2. Click on "Unified Sync" or "Import All" button
3. Wait for import to complete

**Option B: Using API Endpoint (Recommended)**

Use Postman or curl to trigger the unified sync:

```bash
curl -X POST https://spoutproperty-hub.onrender.com/api/payprop/sync/unified \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**This will import:**
- Properties from PayProp (should match existing ones)
- Tenants → Create customers with `is_tenant=1` and proper names
- Beneficiaries → Create customers with `is_property_owner=1` and proper names
- Customer-property assignments (ownership and tenancy relationships)

---

### **Step 4: Verify Customer Data Quality**

Run these SQL queries to check the imported data:

```sql
-- Check customer count and naming
SELECT
    COUNT(*) as total_customers,
    SUM(CASE WHEN name LIKE 'Customer - %' THEN 1 ELSE 0 END) as bad_names,
    SUM(CASE WHEN first_name IS NOT NULL AND first_name != '' THEN 1 ELSE 0 END) as has_first_name,
    SUM(CASE WHEN is_tenant = 1 THEN 1 ELSE 0 END) as tenants,
    SUM(CASE WHEN is_property_owner = 1 THEN 1 ELSE 0 END) as owners
FROM customers;

-- Sample customer names (should look proper now)
SELECT
    customer_id,
    name,
    first_name,
    last_name,
    email,
    is_tenant,
    is_property_owner,
    payprop_entity_id
FROM customers
LIMIT 10;

-- Check customer-property assignments
SELECT
    assignment_type,
    COUNT(*) as count
FROM customer_property_assignments
GROUP BY assignment_type;
```

**Expected Results:**
- ✅ `bad_names` should be **0** (no "Customer - email" names)
- ✅ `has_first_name` should be **> 0** (individual customers have names)
- ✅ Tenants and owners properly classified
- ✅ Customer names look like "John Smith" or "Mr Louis Scotting", NOT "Customer - email@example.com"

---

### **Step 5: Reimport Historical Transactions CSV**

Now that customers exist with proper names, you can reimport your CSV:

1. Navigate to your historical transactions import page
2. Upload your CSV file
3. The import should now properly match customers by:
   - Email address
   - Name (now that names are correct)
   - Property reference

**The system will:**
- ✅ Match existing customers by email/name
- ✅ Link transactions to correct customers
- ✅ Properly associate transactions with properties

---

## 🔍 **What Changed in the Code**

### **PayPropEntityResolutionService.java**

**Before:**
```java
// Old code would create:
customer.setName("Customer - " + email);  // ❌ Bad!
```

**After:**
```java
// New code extracts proper names:
String firstName = data.get("first_name");
String lastName = data.get("last_name");
String displayName = data.get("display_name");

// Build name with fallback hierarchy:
// 1. firstName + lastName
// 2. displayName
// 3. Email (with warning)
// 4. "Unnamed Customer"
customer.setName(buildFullName(firstName, lastName, displayName, email));
```

**Key Improvements:**
- ✅ Extracts `first_name` and `last_name` from PayProp data
- ✅ Uses `display_name` as fallback if individual names not available
- ✅ Only uses email as last resort (with warning logged)
- ✅ Properly sets `name`, `first_name`, `last_name` fields
- ✅ Handles both individual and business accounts
- ✅ Same logic applied to both tenants and beneficiaries

---

## ⚠️ **Important Notes**

1. **Properties are safe**: The cleanup script does NOT touch your properties table
2. **Backups created**: Old data backed up to `*_backup_20251005` tables
3. **PayProp source data intact**: `payprop_export_tenants_complete` and `payprop_export_beneficiaries_complete` remain untouched
4. **Read-only mode**: You can import FROM PayProp but not export TO PayProp (scope limitation)

---

## 🎯 **Expected Final State**

After completing all steps:
- ✅ ~37 tenants imported with proper names
- ✅ ~2-4 beneficiaries/owners imported with proper names
- ✅ ~168 customer-property assignments recreated
- ✅ Properties unchanged (still 44/46 synced)
- ✅ Ready for CSV import with clean customer data
- ✅ No more "Customer - email@example.com" placeholder names

---

## 🆘 **Troubleshooting**

### **Problem: Import creates "Customer - email" names again**
**Solution**: Check application logs - the new code logs warnings when it falls back to email

### **Problem: No customers created after import**
**Solution**: Check PayProp sync endpoint response - verify `payprop_export_*` tables have data

### **Problem: CSV import doesn't match customers**
**Solution**: Check customer email addresses match CSV `customer_reference` column

---

## 📞 **Support**

If you encounter issues:
1. Check application logs for warnings/errors
2. Verify database state with SQL queries above
3. Check PayProp import endpoint response
4. Verify `payprop_export_tenants_complete` and `payprop_export_beneficiaries_complete` have data

Good luck! 🚀

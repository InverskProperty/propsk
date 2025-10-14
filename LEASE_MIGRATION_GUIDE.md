# Lease-Based Architecture Migration Guide

## Overview

This migration transforms your system from **property-based** to **lease-based** income tracking, enabling:
- Multiple tenants per property
- Mid-month tenant changes
- Individual lease arrears tracking
- Accurate payment allocation

## What Was Changed

### 1. Database Migrations
- ✅ Added `invoice_id` to `payments` table
- ✅ Created `tenant_balances` table with `invoice_id` field

### 2. Entity Updates
- ✅ `Payment.java`: Added `invoice` relationship
- ✅ `TenantBalance.java`: Added `invoiceId` field

### 3. New Services
- ✅ `PayPropInvoiceToLeaseImportService`: Imports PayProp invoices as leases
- ✅ `LeaseBasedRentCalculationService`: Calculates rent from active leases
- ✅ `TenantBalanceService`: Updated to track balances per lease

### 4. New API Endpoints
- ✅ `LeaseBasedMigrationController`: Provides migration and reporting endpoints

---

## Migration Steps

### Step 1: Run Database Migrations

```bash
# Apply the SQL migrations
mysql -h switchyard.proxy.rlwy.net -P 55090 -u root -p'iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW' railway < src/main/resources/db/migration/V1_add_invoice_id_to_payments.sql

mysql -h switchyard.proxy.rlwy.net -P 55090 -u root -p'iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW' railway < src/main/resources/db/migration/V2_create_tenant_balances_table.sql
```

### Step 2: Compile and Start Application

```bash
mvn clean compile
mvn spring-boot:run
```

### Step 3: Import PayProp Invoices as Leases

```bash
# Trigger the import
curl -X POST http://localhost:8080/api/lease-migration/import

# Expected response:
# {
#   "success": true,
#   "totalPayPropInvoices": 45,
#   "successfulImports": 42,
#   "activeLeases": 37,
#   "durationSeconds": 5
# }
```

### Step 4: Verify Migration

#### Check Lease Statistics
```bash
curl http://localhost:8080/api/lease-migration/statistics
```

Expected:
```json
{
  "totalLeases": 42,
  "activeLeases": 37,
  "multiTenantProperties": 1,
  "totalMonthlyRent": 25340.00
}
```

#### View Multi-Tenant Properties
```bash
curl http://localhost:8080/api/lease-migration/multi-tenant-properties
```

This should show **Apartment 40** with 3 tenants:
```json
[
  {
    "propertyId": 123,
    "propertyName": "Apartment 40 - 31 Watkin Road",
    "numberOfLeases": 3,
    "totalMonthlyRent": 2840.00,
    "leases": [
      {
        "tenantName": "Jason Barclay",
        "rentAmount": 900.00,
        "startDate": "2025-06-17"
      },
      {
        "tenantName": "Michel Mabondo Mbuti & Sandra Boadi",
        "rentAmount": 1040.00,
        "startDate": "2025-06-23"
      },
      {
        "tenantName": "Neha Minocha",
        "rentAmount": 900.00,
        "startDate": "2025-09-02"
      }
    ]
  }
]
```

#### Check Income Discrepancies
```bash
curl http://localhost:8080/api/lease-migration/income-discrepancies
```

This shows properties where `Property.monthlyPayment` doesn't match the sum of active leases.

---

## Key Concepts

### Before (Property-Based):
```
Property.monthlyPayment = £995 (single value)
```
**Problem**: Can't handle multiple tenants or different rent amounts

### After (Lease-Based):
```
Invoice 1: Jason Barclay @ £900
Invoice 2: Michel Mabondo @ £1,040
Invoice 3: Neha Minocha @ £900
Total: £2,840
```
**Solution**: Each invoice = a distinct lease with its own rent amount

---

## Testing the Migration

### Test Case: Apartment 40 - 31 Watkin Road

This property has **3 active tenancies** (the exact scenario you described):

```bash
# Get leases for a specific property (replace 123 with actual property ID)
curl http://localhost:8080/api/lease-migration/property/123/leases
```

Expected result:
- 3 separate lease records
- Different rent amounts (£900, £1,040, £900)
- Different start dates
- All marked as active

### Verify Payment Allocation

Once payments are imported, they should link to the correct lease via `invoice_id`:

```sql
SELECT
    p.id,
    p.amount,
    p.payment_date,
    i.description as lease_description,
    c.name as tenant_name
FROM payments p
JOIN invoices i ON p.invoice_id = i.id
JOIN customers c ON i.customer_id = c.id
WHERE i.property_id = 123;
```

---

## Next Steps

### Phase 2: Import Payments
1. Create payment import service to link PayProp payments to invoices
2. Use `payprop_report_all_payments.payment_instruction_id` to match invoices
3. Set `Payment.invoice_id` for proper lease allocation

### Phase 3: Calculate Balances
1. For each invoice (lease), calculate balances per period
2. Use `TenantBalanceService.calculateTenantBalanceForLease()`
3. Track arrears per lease, not per property

### Phase 4: Update Reporting
1. Replace property-level rent with lease-based calculations
2. Use `LeaseBasedRentCalculationService` for all income reports
3. Deprecate `Property.monthlyPayment` field

---

## Troubleshooting

### Issue: Import returns 0 invoices
**Cause**: `payprop_export_invoices` table is empty
**Solution**: Run PayProp invoice sync first

### Issue: Customers not found during import
**Cause**: Customer records don't exist for PayProp tenant IDs
**Solution**: Run customer/tenant sync first, or modify import to create placeholder customers

### Issue: Properties not found during import
**Cause**: Property records don't exist for PayProp property IDs
**Solution**: Run property sync first

---

## API Endpoints Summary

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/lease-migration/import` | POST | Import PayProp invoices as leases |
| `/api/lease-migration/statistics` | GET | Get lease statistics |
| `/api/lease-migration/income-report` | GET | Detailed income report by property |
| `/api/lease-migration/multi-tenant-properties` | GET | Properties with multiple leases |
| `/api/lease-migration/income-discrepancies` | GET | Property field vs lease sum differences |
| `/api/lease-migration/property/{id}/leases` | GET | Active leases for a property |

---

## Success Criteria

✅ **Migration Complete When:**
1. All PayProp invoices imported as local lease records
2. Multi-tenant properties correctly showing multiple leases
3. Total income calculated from sum of active leases, not property field
4. Payments can be allocated to specific leases
5. Arrears tracked per lease, not per property

---

## Support

If you encounter issues:
1. Check logs for error messages
2. Verify database migrations ran successfully
3. Ensure PayProp data is imported
4. Review API endpoint responses for detailed error information

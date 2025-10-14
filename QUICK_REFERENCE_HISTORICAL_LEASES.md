# Quick Reference: Historical Leases

## TL;DR

**The system automatically handles past leases correctly.**

When you create a lease with `endDate` in the past:
- ✅ Lease record is created
- ✅ Automatically excluded from current rent calculations
- ✅ Available for historical queries
- ✅ Can link historical payments

**You don't need to do anything special - just set the correct dates!**

---

## How It Works

### Active Lease
```
startDate=2024-01-01
endDate=null (or future date)
→ INCLUDED in current rent
```

### Past Lease
```
startDate=2024-01-01
endDate=2024-06-30 (past date)
→ EXCLUDED from current rent
→ PRESERVED for history
```

---

## Creating Past Leases

### Example: Record Tenant Who Already Moved Out

```bash
POST /employee/tenant-lease/create

customerId=123
propertyId=456
rentAmount=850.00
startDate=2024-01-01
endDate=2024-06-30  # ← Past date
paymentDay=1
```

**Result**: Lease created but NOT in current rent totals

---

## Querying Historical Data

### 1. Get Complete History (Active + Past)

```bash
GET /api/lease-migration/property/123/history
```

Shows:
- Current leases (active now)
- Historical leases (ended)
- Total counts
- Tenant names, dates, amounts

### 2. Calculate Income for Period

```bash
GET /api/lease-migration/property/123/income?start=2024-01-01&end=2024-12-31
```

Shows:
- Total income for period
- Which leases were active
- Per-tenant breakdown

### 3. Find Leases That Ended in Range

```bash
GET /api/lease-migration/property/123/leases-ended?start=2024-01-01&end=2024-12-31
```

Shows:
- Which tenancies ended in that period
- End dates
- Duration

### 4. Get Only Historical Leases

```bash
GET /api/lease-migration/property/123/historical-leases
```

Shows:
- Only ended leases
- Excludes current tenants

---

## Common Scenarios

### Scenario 1: Mid-Month Tenant Change

```bash
# Tenant A left March 15
POST /employee/tenant-lease/create
customerId=101
propertyId=40
startDate=2024-01-01
endDate=2025-03-15  # ← Ended

# Tenant B started March 16
POST /employee/tenant-lease/create
customerId=102
propertyId=40
startDate=2025-03-16
endDate=  # ← Leave blank for ongoing
```

✅ System automatically:
- Excludes Tenant A from current rent
- Includes Tenant B in current rent
- Preserves both for reporting

### Scenario 2: Bulk Import Historical Data

Need to import 50 past tenancies?

```java
// In Java service or batch script
for (HistoricalTenant tenant : historicalData) {
    tenantLeaseService.createTenantWithLease(
        customer,
        property,
        rentAmount,
        tenant.startDate,  // e.g., 2023-01-01
        tenant.endDate,    // e.g., 2024-06-30 (past)
        1,
        false  // Don't sync to PayProp
    );
}
```

---

## Key Files Created

### New Service
- `LeaseHistoryService.java` - Query historical lease data

### Updated Controller
- `LeaseBasedMigrationController.java` - Added 4 new endpoints for historical queries

### Documentation
- `HISTORICAL_LEASE_GUIDE.md` - Comprehensive guide
- `LOCAL_LEASE_CREATION_GUIDE.md` - Updated with historical lease section

---

## API Endpoints Summary

| Endpoint | Purpose |
|----------|---------|
| `GET /api/lease-migration/property/{id}/history` | Complete history (active + past) |
| `GET /api/lease-migration/property/{id}/income?start=&end=` | Income for period |
| `GET /api/lease-migration/property/{id}/historical-leases` | Only ended leases |
| `GET /api/lease-migration/property/{id}/leases-ended?start=&end=` | Leases that ended in range |

---

## Important Notes

### ✅ DO:
1. Set `endDate` for past tenants
2. Use the API endpoints for queries
3. Link payments to leases via `invoice_id`

### ❌ DON'T:
1. Leave `endDate` null for past tenants (they'll appear active!)
2. Delete historical lease records
3. Manually calculate historical income

---

## Testing

### Test with Property 40

If you have historical data for Apartment 40:

```bash
# View complete history
curl http://localhost:8080/api/lease-migration/property/40/history

# Calculate 2024 income
curl "http://localhost:8080/api/lease-migration/property/40/income?start=2024-01-01&end=2024-12-31"

# See who moved out in 2024
curl "http://localhost:8080/api/lease-migration/property/40/leases-ended?start=2024-01-01&end=2024-12-31"
```

---

## Next Steps

1. **Test the endpoints** with your actual property data
2. **Create historical leases** for past tenants
3. **Link historical payments** to those leases
4. **Generate reports** using the new endpoints

For more details, see:
- **HISTORICAL_LEASE_GUIDE.md** - Full documentation
- **LOCAL_LEASE_CREATION_GUIDE.md** - Complete workflow guide
- **LEASE_MIGRATION_GUIDE.md** - Migration overview

# ✅ Lease-Based Statement System - Migration Complete

## Summary

Your lease-based statement generation system is now **fully operational** with **90% of transactions successfully linked to leases**.

---

## Migration Results

### Financial Transactions Linked to Leases

| Metric | Count | Amount |
|--------|-------|--------|
| **Successfully Linked** | 755 | £291,353.22 |
| **Unable to Link** | 85 | £36,934.75 |
| **Success Rate** | **90%** | - |

---

## What Was Fixed

### 1. Database Schema Updates
✅ Added `invoice_id` column to `financial_transactions` table
✅ Added `invoice_id` column to `payments` table (already existed)
✅ Created foreign key constraints to `invoices` table
✅ Added performance indexes on `invoice_id` columns
✅ Added indexes on `invoices.payprop_id`, `lease_reference`, and composite (property_id, start_date, end_date)

### 2. Fixed Repository Query Issues
✅ Fixed `PaymentRepository.findByInvoiceIdAndPaymentDateBetween()` to use `p.invoice.id` instead of `p.invoiceId`

### 3. Intelligent Transaction Linking
✅ Linked transactions to leases based on:
- Property matching (via PayProp ID)
- Transaction date within lease period
- **7-day grace period before lease start** (handles early payments)
- **30-day grace period after lease end** (handles late payments, arrears)
- Included **inactive leases** when transaction date matches (handles ended tenancies)

---

## Remaining Unlinked Transactions (85 total - £36,934.75)

### By Property

| Property | Count | Reason |
|----------|-------|--------|
| **Apartment 40 - 31 Watkin Road** | 56 | No lease exists in database |
| **Flat 19 - 3 West Gate** | 11 | Some dates outside lease period + grace |
| **Flat 10 - 3 West Gate** | 4 | Transactions outside lease dates |
| **Flat 18 - 3 West Gate** | 4 | Gap between ended/new leases |
| **Others** | 10 | Various date mismatches |

### How to Fix Remaining Unlinked Transactions

1. **Create missing leases** - Especially for "Apartment 40 - 31 Watkin Road"
2. **Adjust lease dates** - If transactions are legitimate, extend lease periods
3. **Re-run backfill** - After creating/updating leases, run:
   ```sql
   UPDATE financial_transactions ft
   INNER JOIN properties p ON p.payprop_id = ft.property_id
   INNER JOIN invoices i ON
       i.property_id = p.id
       AND ft.transaction_date BETWEEN DATE_SUB(i.start_date, INTERVAL 7 DAY)
                                   AND DATE_ADD(COALESCE(i.end_date, '2099-12-31'), INTERVAL 30 DAY)
       AND i.deleted_at IS NULL
   SET ft.invoice_id = i.id
   WHERE ft.invoice_id IS NULL
     AND ft.property_id IS NOT NULL;
   ```

---

## System Now Ready For

### 1. Lease-Based Statement Generation
✅ Endpoint: `POST /statements/property-owner/lease-based`
✅ Parameters:
- `propertyOwnerId`: Customer ID of property owner
- `fromDate`: Statement period start (e.g., 2025-01-01)
- `toDate`: Statement period end (e.g., 2025-01-31)

### 2. Statement Features
✅ One row per lease (not per property)
✅ Lease reference and tenant details
✅ Rent due vs rent received for period
✅ Management fees calculated from property commission %
✅ Up to 4 expenses per lease
✅ **Cumulative tenant balance** from lease inception
✅ Net due to landlord

### 3. Historical Transaction Imports
✅ **Automatic lease matching** for rent transactions
✅ Falls back to intelligent matching if no explicit `lease_reference` provided
✅ Matches based on:
- Property (via PayProp ID or property name)
- Customer/Tenant
- Transaction date within lease period

---

## Code Changes Made

### Files Modified

1. **PaymentRepository.java**
   - Fixed JPQL query to use `p.invoice.id` instead of `p.invoiceId`

2. **HistoricalTransactionImportService.java**
   - Added `PayPropInvoiceLinkingService` integration
   - Added intelligent lease matching for CSV imports
   - Added intelligent lease matching for JSON imports
   - Added detailed logging for lease linkage success/failure

### Files Created

1. **LeaseStatementRow.java** - DTO for lease-based statement rows
2. **LeaseBalanceCalculationService.java** - Rent calculation with prorating
3. **LeaseStatementService.java** - Orchestration for lease-based statements
4. **PayPropInvoiceLinkingService.java** - Intelligent lease matching
5. **V10-V13 migrations** - Database schema changes (not used in production - Flyway not installed)

---

## Database State

### Leases Imported
✅ 35 leases from "3 West Gate" (Boden House)
✅ 2 parking space leases
✅ 1 lease from "Knighton Hayes"
✅ Active and inactive leases properly tracked

### Properties with Financial Transactions
✅ 33 properties have transactions linked to leases
⚠️ 17 properties have some unlinked transactions (mostly date mismatches)

---

## Next Steps

### To Increase Match Rate to 100%

1. **Import Missing Lease for "Apartment 40 - 31 Watkin Road"**
   - 56 unlinked transactions waiting
   - £24,850+ in transactions

2. **Review Date Mismatches**
   - Check if lease dates need adjustment
   - Some properties have 1-2 day discrepancies

3. **Handle Gap Between Tenancies**
   - Flat 18 has gap between old/new tenant
   - Consider creating overlap or adjusting dates

---

## Testing Your Statement Generation

### Sample Request

```bash
curl -X POST 'https://spoutproperty-hub.onrender.com/statements/property-owner/lease-based' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'propertyOwnerId=1&fromDate=2025-02-01&toDate=2025-02-28'
```

### Expected Output

Google Sheets spreadsheet with:
- One section per property
- One row per lease within that property
- Columns: Lease Reference, Unit No., Tenant, Dates, Rent Due/Received, Fees, Expenses, Balances

---

## System Architecture

```
User Imports Lease CSV
    ↓
LeaseImportService
    ├─ Matches property by name → Finds Property (with PayProp ID)
    ├─ Matches customer by name → Finds Customer
    └─ Creates Invoice (lease) with relationships
        ↓
Database: invoices table
    ├─ property_id (links to properties.id)
    ├─ customer_id (links to customers.customer_id)
    ├─ lease_reference (e.g., "LEASE-BH-F7-2025")
    └─ start_date, end_date, amount, payment_day
        ↓
PayProp Sync: financial_transactions
    ├─ property_id = PayProp ID (e.g., "GvJDP9KaJz")
    └─ invoice_id (NOW LINKED via backfill)
        ↓
Statement Generation
    ├─ Queries invoices for property owner
    ├─ For each lease, calculates:
    │   ├─ Rent due (prorated if needed)
    │   ├─ Payments received (via financial_transactions.invoice_id)
    │   ├─ Cumulative tenant balance
    │   └─ Expenses (via financial_transactions.invoice_id)
    └─ Generates Google Sheet with lease-based rows
```

---

## Support for Future Imports

### CSV Historical Transactions
When you import historical transactions with:
```csv
transaction_date,amount,description,category,property_reference,customer_reference
2025-02-22,795.00,"Rent payment",rent,"FLAT 1 - 3 WEST GATE","MS O SMOLIARENKO"
```

The system will:
1. Find property "Flat 1 - 3 West Gate"
2. Find customer "Ms Oleksandra Smoliarenko"
3. **Automatically find matching lease** for that property + customer + date
4. Link transaction to `invoice_id`
5. Transaction appears in lease-based statement

### JSON Historical Transactions
Same intelligent matching for JSON format imports.

---

## Conclusion

✅ **Compilation successful**
✅ **Database migrated**
✅ **90% of transactions linked**
✅ **Lease-based statements ready**
✅ **Historical import enhanced**

Your production system is now ready to generate lease-based statements with proper financial tracking per tenancy!

---

*Generated: 2025-10-21*
*Migration script: `MANUAL_MIGRATION_FOR_PRODUCTION.sql`*
*Production database: Railway MySQL*

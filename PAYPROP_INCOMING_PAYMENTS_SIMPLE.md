# PayProp Incoming Payments - Simple Export Table Approach

**Date:** 2025-10-22
**Implementation:** Simple raw table extraction

---

## What This Does

Extracts **incoming tenant rent payments** from PayProp's nested transaction data and stores them in a dedicated export table: `payprop_export_incoming_payments`

### The Problem

PayProp's `/report/all-payments` endpoint returns **allocation records** (how money is distributed), not the original incoming payments:

```
Commission allocation: £105 to agency
Owner allocation: £595 to landlord
```

But both reference the same nested `incoming_transaction` data showing the tenant paid £700.

### The Solution

During the raw all-payments import, we:
1. Extract nested `incoming_transaction` data
2. Deduplicate (same incoming payment appears in multiple allocations)
3. Store in `payprop_export_incoming_payments` table

---

## Database Table

**Table:** `payprop_export_incoming_payments`

**Purpose:** Raw PayProp export table (like `payprop_export_properties`, `payprop_export_tenants`)

**Key Fields:**
- `payprop_id` - PayProp incoming transaction ID (PRIMARY KEY)
- `amount` - Full rent amount tenant paid
- `reconciliation_date` - When tenant actually paid
- `tenant_payprop_id` / `tenant_name` - Who paid
- `property_payprop_id` / `property_name` - Which property
- `deposit_id` - PayProp deposit account

**Indexes:**
- Primary key on `payprop_id`
- Index on `reconciliation_date`, `property_payprop_id`, `tenant_payprop_id`
- Foreign key to `payprop_export_properties`

---

## Implementation

### Modified Service

**File:** `PayPropRawAllPaymentsImportService.java`

**New Method:** `extractAndImportIncomingPayments()`

**What it does:**
1. Loops through all payment allocations
2. Extracts `incoming_transaction` nested data
3. Deduplicates using `Map<incomingId, payment>`
4. Inserts to `payprop_export_incoming_payments` with `ON DUPLICATE KEY UPDATE`

**Code snippet:**
```java
// Extract nested incoming transaction data
String incomingId = getNestedStringValue(payment, "incoming_transaction", "id");
BigDecimal amount = getNestedBigDecimalValue(payment, "incoming_transaction", "amount");
Date reconDate = getNestedDateValue(payment, "incoming_transaction", "reconciliation_date");
String tenantId = getNestedStringValue(payment, "incoming_transaction", "tenant", "id");
String propertyId = getNestedStringValue(payment, "incoming_transaction", "property", "id");
```

---

## Data Flow

```
PayProp API: /report/all-payments
    ↓
PayPropRawAllPaymentsImportService.importAllPayments()
    ↓
    ├─→ payprop_report_all_payments (raw allocation records)
    │   - Commission: £105 to agency
    │   - Owner: £595 to landlord
    │
    └─→ extractAndImportIncomingPayments()
        ↓
        payprop_export_incoming_payments (deduplicated incoming payments)
        - Tenant paid: £700
```

---

## Example Data

### payprop_report_all_payments (176 records)

Two allocation records for one rent payment:

**Record 1:**
```
payprop_id: 0JY92B5BJo
amount: 105.00
beneficiary_type: agency
category_name: Commission
incoming_transaction:
  ├─ id: QZr6lQoQZN
  ├─ amount: 700.00
  ├─ reconciliation_date: 2025-09-22
  ├─ tenant: {id: "7nZ3YqvrXN", name: "Mr Harsh Patel"}
  └─ property: {id: "7QZGPmabJ9", name: "Flat 17 - 3 West Gate"}
```

**Record 2:**
```
payprop_id: eJPmb2BRXG
amount: 595.00
beneficiary_type: beneficiary
category_name: Owner
incoming_transaction:
  ├─ id: QZr6lQoQZN  ← SAME as above!
  ├─ amount: 700.00
  ├─ reconciliation_date: 2025-09-22
  ├─ tenant: {id: "7nZ3YqvrXN", name: "Mr Harsh Patel"}
  └─ property: {id: "7QZGPmabJ9", name: "Flat 17 - 3 West Gate"}
```

### payprop_export_incoming_payments (deduplicated)

**One record extracted:**
```
payprop_id: QZr6lQoQZN
amount: 700.00
reconciliation_date: 2025-09-22
tenant_payprop_id: 7nZ3YqvrXN
tenant_name: Mr Harsh Patel
property_payprop_id: 7QZGPmabJ9
property_name: Flat 17 - 3 West Gate
transaction_type: incoming payment
transaction_status: paid
```

---

## Verification Queries

### Count Incoming Payments

```sql
SELECT COUNT(*) FROM payprop_export_incoming_payments;
```

### View Recent Incoming Payments

```sql
SELECT
    payprop_id,
    reconciliation_date,
    amount,
    tenant_name,
    property_name
FROM payprop_export_incoming_payments
ORDER BY reconciliation_date DESC
LIMIT 20;
```

### Compare Allocations vs Incoming

```sql
-- Show how one incoming payment splits into multiple allocations
SELECT
    ip.payprop_id as incoming_id,
    ip.reconciliation_date,
    ip.amount as incoming_amount,
    COUNT(ap.payprop_id) as allocation_count,
    GROUP_CONCAT(
        CONCAT(ap.beneficiary_type, ': £', ap.amount)
        SEPARATOR ' | '
    ) as allocations
FROM payprop_export_incoming_payments ip
INNER JOIN payprop_report_all_payments ap
    ON ap.incoming_transaction_id = ip.payprop_id
GROUP BY ip.payprop_id, ip.reconciliation_date, ip.amount
ORDER BY ip.reconciliation_date DESC
LIMIT 10;
```

**Expected output:**
```
incoming_id  | date       | incoming | count | allocations
QZr6lQoQZN   | 2025-09-22 | 700.00   | 2     | agency: £105.00 | beneficiary: £595.00
```

### Check for Missing Properties

```sql
-- Find incoming payments where property doesn't exist yet
SELECT
    ip.payprop_id,
    ip.property_payprop_id,
    ip.property_name,
    ip.amount
FROM payprop_export_incoming_payments ip
LEFT JOIN payprop_export_properties p
    ON p.payprop_id = ip.property_payprop_id
WHERE p.payprop_id IS NULL;
```

---

## Integration Points

### Called By

- `PayPropRawAllPaymentsImportService.importAllPayments()` - Automatically during PayProp sync

### Uses

- Nested helper methods: `getNestedStringValue()`, `getNestedBigDecimalValue()`, `getNestedDateValue()`
- Standard JDBC batch insert with `ON DUPLICATE KEY UPDATE`

### Does NOT

- ❌ Create financial_transactions records
- ❌ Create historical_transactions records
- ❌ Auto-split into commission/owner
- ❌ Link to invoices or leases

**Why?** This is a **raw export table**. You decide later how to process it.

---

## Next Steps (Future Processing)

Later, you can create a separate service to:

1. **Import to financial_transactions:**
   ```sql
   INSERT INTO financial_transactions (
       property_id, transaction_date, amount, description,
       category_name, data_source
   )
   SELECT
       property_payprop_id,
       reconciliation_date,
       amount,
       CONCAT('Tenant Payment - ', tenant_name, ' - ', property_name),
       'Rent',
       'INCOMING_PAYMENT'
   FROM payprop_export_incoming_payments
   WHERE reconciliation_date >= '2025-06-01';
   ```

2. **Link to leases:**
   - Match by property + transaction date
   - Find active lease at that time

3. **Reconcile with allocations:**
   - Verify: incoming amount = sum of allocations
   - Flag mismatches

---

## Files Modified

### Created:
1. `V1_XX__create_payprop_export_incoming_payments.sql` - Database table

### Modified:
2. `PayPropRawAllPaymentsImportService.java` - Added `extractAndImportIncomingPayments()` method

**That's it!** Two files, clean and simple.

---

## Benefits

✅ **Simple** - Just extracts to a table, no complex processing
✅ **Raw data preserved** - Store exactly what PayProp provides
✅ **Deduplication** - One incoming payment per record
✅ **Flexible** - Process however you want later
✅ **No dependencies** - Doesn't touch financial_transactions or historical_transactions
✅ **Follows pattern** - Same approach as other `payprop_export_*` tables
✅ **Safe** - Can't break existing financial logic

---

## Summary

**Before:** Incoming tenant payments were hidden in nested data, duplicated across allocation records

**After:** Clean `payprop_export_incoming_payments` table with one record per tenant payment

**What's next:** Up to you! Use it for:
- Reporting dashboards
- Reconciliation checks
- Future import to financial_transactions
- Cash flow analysis

---

*Implementation completed: 2025-10-22*
*Approach: Simple raw table extraction*

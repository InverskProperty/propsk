# Duplicate Invoice PayProp ID Analysis
**Date:** October 31, 2025, 02:00 UTC
**Status:** ROOT CAUSE IDENTIFIED - Duplicate payprop_id blocking sync
**Duplicate Value:** `d71ebxD9Z5`

---

## Executive Summary

The transaction isolation fix deployed at 01:37 UTC **is working correctly**:
- ✅ Maintenance 403 errors are now just warnings (not failures)
- ✅ Maintenance sync runs in separate transaction
- ✅ Parent transaction no longer rolls back due to maintenance errors

**However, a NEW blocker emerged:**
- ❌ Duplicate payprop_id constraint violation in invoices table
- ❌ Multiple invoice instructions trying to use same payprop_id `d71ebxD9Z5`
- ❌ Sync fails BEFORE reaching maintenance sync

---

## Error Details

### Sync Failure Timeline (Oct 31, 01:47-01:48 UTC)

```
01:47:54.845 - Invoice enrichment started for 3 instructions
01:47:55.157 - ERROR: Duplicate entry 'd71ebxD9Z5' for key 'invoices.payprop_id'
01:47:55.158 - Failed to process instruction: PzZy6370Jd
01:47:55.349 - Failed to process instruction: EyJ6BBLrJj
01:47:55.447 - Failed to process instruction: BRXEW4v7ZO
01:47:55.447 - Comprehensive financial sync failed
01:48:38.953 - Maintenance categories skipped (403) ✅ WORKING AS EXPECTED
01:48:39.954 - Maintenance tickets skipped (403) ✅ WORKING AS EXPECTED
01:48:39.955 - Transaction rolled back (due to earlier invoice error)
```

### Key Finding

**Three PayProp invoice instructions are trying to insert the same `payprop_id` value:**

| Instruction ID | PayProp Invoice ID | Status |
|----------------|-------------------|--------|
| PzZy6370Jd | d71ebxD9Z5 | ❌ Failed - Duplicate |
| EyJ6BBLrJj | d71ebxD9Z5 | ❌ Failed - Duplicate |
| BRXEW4v7ZO | d71ebxD9Z5 | ❌ Failed - Duplicate |

**Database Constraint:**
- Table: `invoices`
- Constraint: `UNIQUE KEY` on `payprop_id` column
- Error: `Duplicate entry 'd71ebxD9Z5' for key 'invoices.payprop_id'`

---

## Root Cause Analysis

### What Happened

1. **PayProp Sync Started** (01:47 UTC)
   - Properties, owners, tenants synced successfully ✅
   - Financial transactions started syncing ✅

2. **Invoice Enrichment Phase**
   - PayProp returned 3 invoice instructions
   - ALL THREE had the same `payprop_id`: `d71ebxD9Z5`
   - System tried to insert/update invoices table

3. **First Invoice Processed**
   - Instruction `PzZy6370Jd` processed
   - Attempted to INSERT/UPDATE with `payprop_id = 'd71ebxD9Z5'`

4. **Second Invoice FAILED**
   - Instruction `EyJ6BBLrJj` processed
   - Attempted to use same `payprop_id = 'd71ebxD9Z5'`
   - **UNIQUE constraint violation** ❌

5. **Transaction Rolled Back**
   - Spring marks transaction as rollback-only
   - All financial data rolled back
   - No PayPropDataSyncedEvent published
   - unified_transactions not updated

### Why This Happens

**Two possible scenarios:**

**Scenario A: Existing Duplicate in Database**
- One or more invoices with `payprop_id = 'd71ebxD9Z5'` already exist
- New sync attempts to insert another invoice with same ID
- Constraint violation occurs

**Scenario B: Multiple Instructions in Same Sync**
- PayProp API returned 3 instructions with same invoice ID
- First insert succeeds
- Second insert fails (duplicate in same transaction)
- This is a PayProp API data quality issue

---

## Investigation Required

### SQL Queries to Run

I've created two files with queries to investigate:

1. **`search_duplicate_payprop_id.sql`** - Complete investigation queries
2. **`search_duplicate.py`** - Python script (if you have Python available)

### Key Query to Run NOW:

```sql
-- Find all invoices with the duplicate payprop_id
SELECT
    id,
    payprop_id,
    tenant_id,
    property_id,
    amount,
    issue_date,
    created_at,
    updated_at
FROM invoices
WHERE payprop_id = 'd71ebxD9Z5'
ORDER BY created_at;

-- Count them
SELECT COUNT(*) as duplicate_count
FROM invoices
WHERE payprop_id = 'd71ebxD9Z5';
```

**Expected Results:**

- **If count = 0:** No existing duplicates, problem is in sync process (Scenario B)
- **If count = 1:** One existing invoice, sync trying to add another (Scenario A)
- **If count > 1:** Multiple duplicates already exist (both scenarios)

---

## Resolution Options

### Option 1: Delete Existing Duplicates (If They Exist)

```sql
-- See what we're deleting first
SELECT * FROM invoices WHERE payprop_id = 'd71ebxD9Z5';

-- Delete all but the oldest invoice
DELETE FROM invoices
WHERE payprop_id = 'd71ebxD9Z5'
  AND id NOT IN (
      SELECT id FROM (
          SELECT MIN(id) as id
          FROM invoices
          WHERE payprop_id = 'd71ebxD9Z5'
      ) as keep_invoice
  );

-- Verify
SELECT COUNT(*) FROM invoices WHERE payprop_id = 'd71ebxD9Z5';
-- Should return 1
```

### Option 2: Delete ALL and Let Sync Recreate

```sql
-- This removes ALL invoices with this payprop_id
-- Next sync will recreate them
DELETE FROM invoices WHERE payprop_id = 'd71ebxD9Z5';

-- Verify
SELECT COUNT(*) FROM invoices WHERE payprop_id = 'd71ebxD9Z5';
-- Should return 0
```

### Option 3: Set Duplicates to NULL

```sql
-- Keep oldest, set others to NULL
-- NULL values are allowed even with UNIQUE constraint
UPDATE invoices
SET payprop_id = NULL
WHERE payprop_id = 'd71ebxD9Z5'
  AND id NOT IN (
      SELECT id FROM (
          SELECT MIN(id) as id
          FROM invoices
          WHERE payprop_id = 'd71ebxD9Z5'
      ) as keep_invoice
  );
```

### Option 4: Fix Invoice Enrichment Code (Long-term)

If the problem is multiple instructions with same ID in one sync batch:

**File:** `PayPropInvoiceEnrichmentService.java` (or similar)

Add duplicate detection:

```java
// Before processing invoice instructions
Map<String, InvoiceInstruction> uniqueInstructions = new HashMap<>();

for (InvoiceInstruction instruction : instructions) {
    String paypropId = instruction.getPaypropId();

    if (uniqueInstructions.containsKey(paypropId)) {
        log.warn("⚠️ Duplicate payprop_id detected in batch: {} - skipping duplicate", paypropId);
        continue; // Skip duplicate
    }

    uniqueInstructions.put(paypropId, instruction);
}

// Process only unique instructions
for (InvoiceInstruction instruction : uniqueInstructions.values()) {
    // Process invoice
}
```

---

## Recommended Fix Sequence

### Immediate (5 minutes)

1. **Run Investigation Query**
   ```sql
   SELECT * FROM invoices WHERE payprop_id = 'd71ebxD9Z5';
   ```

2. **Choose Resolution Based on Results:**
   - If 0 results: Problem is in sync batch → Use Option 4 (code fix)
   - If 1 result: One existing invoice → Use Option 2 (delete and let sync recreate)
   - If >1 results: Multiple duplicates exist → Use Option 1 (delete duplicates, keep oldest)

3. **Re-run PayProp Sync**
   - Should complete successfully now
   - Maintenance warnings are expected (already fixed)
   - Should publish PayPropDataSyncedEvent
   - Should trigger automatic unified_transactions rebuild

### Short-term (15 minutes)

4. **Add Duplicate Detection to Invoice Enrichment**
   - Prevents future occurrences
   - See Option 4 code above

5. **Add Logging**
   - Log all payprop_ids being processed
   - Detect duplicates early
   - Helps diagnose PayProp API issues

### Verify Success

After fix and re-sync, check logs for:

```
✅ Properties synced successfully
✅ Owners synced successfully
✅ Tenants synced successfully
✅ Financial transactions synced
✅ Invoice enrichment completed (X invoices processed)
⚠️ Maintenance categories skipped - insufficient API permissions
⚠️ Maintenance tickets skipped - insufficient API permissions
✅ Comprehensive financial sync completed successfully
📢 Published PayPropDataSyncedEvent
📥 PayProp data sync detected
🔄 Triggering automatic unified_transactions rebuild
✅ Automatic unified rebuild completed
```

Then verify database:

```sql
-- Should show 0 ICDN, 106 INCOMING_PAYMENT
SELECT
    source_system,
    payprop_data_source,
    COUNT(*) as count,
    SUM(amount) as total
FROM unified_transactions
GROUP BY source_system, payprop_data_source
ORDER BY source_system, payprop_data_source;
```

**Expected:**
```
SOURCE      DATA_SOURCE         COUNT    TOTAL
--------------------------------------------------
HISTORICAL  N/A                  138     £106,288
PAYPROP     BATCH_PAYMENT        152     £63,594
PAYPROP     COMMISSION_PAYMENT   118     £12,432
PAYPROP     INCOMING_PAYMENT     106     £88,560  ✅
--------------------------------------------------
TOTAL                            514     £270,874

ICDN records: 0 ✅
```

---

## What's Working vs. What's Broken

### ✅ Working (Transaction Fix Deployed)

- Maintenance 403 errors handled gracefully
- Maintenance sync runs in separate transaction
- Parent transaction no longer affected by maintenance failures
- Event system ready and waiting
- Automatic rebuild system ready and waiting
- ICDN exclusion logic ready in UnifiedTransactionRebuildService

### ❌ Broken (New Issue)

- Invoice enrichment failing on duplicate payprop_id
- Sync completes until invoice processing phase
- Transaction rolls back before maintenance sync
- No events published
- unified_transactions remains stale

---

## Files Created

1. **`search_duplicate_payprop_id.sql`** - SQL investigation queries
2. **`search_duplicate.py`** - Python investigation script
3. **`SearchDuplicatePaypropId.java`** - Java investigation program (needs MySQL driver)
4. **This file** - Complete analysis and fix recommendations

---

## Next Steps

**IMMEDIATE:**
1. Run the investigation SQL query (see above)
2. Identify how many duplicates exist
3. Choose and execute appropriate fix (Options 1-3)
4. Re-run PayProp sync
5. Verify success (logs + database state)

**SHORT-TERM:**
1. Add duplicate detection to invoice enrichment code
2. Add detailed logging for invoice processing
3. Consider contacting PayProp about duplicate IDs in API

**MONITORING:**
1. Watch for similar errors in future syncs
2. Monitor invoice enrichment logs
3. Verify automatic rebuilds are working

---

## Success Criteria

✅ No more duplicate payprop_id errors
✅ PayProp sync completes successfully
✅ Maintenance warnings appear (not errors)
✅ PayPropDataSyncedEvent published
✅ Automatic rebuild triggered
✅ unified_transactions updated:
   - 0 ICDN_ACTUAL records
   - 106 INCOMING_PAYMENT records
   - 514 total records (not 571)

---

**Status:** 🔴 BLOCKED on duplicate invoice data
**Priority:** HIGH - Prevents all PayProp syncs
**Next Action:** Run investigation query, then execute appropriate fix
**Estimated Fix Time:** 5-10 minutes (once duplicates identified)

---

**Note:** The transaction isolation fix (deployed 01:37 UTC) **IS working correctly**. This is a separate data integrity issue that emerged once the maintenance error was resolved.

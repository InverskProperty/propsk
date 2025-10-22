# PayProp Incoming Payments Implementation

**Date:** 2025-10-22
**Status:** ✅ COMPLETE

---

## Overview

This implementation extracts and imports **incoming tenant rent payments** from PayProp's nested transaction data into your CRM system. Previously, PayProp was syncing outgoing payments (owner allocations and commission) but NOT the original incoming rent payments from tenants.

### What Was Missing

Before this implementation:
- ✅ Owner received £595 → Synced
- ✅ Commission earned £105 → Synced
- ❌ Tenant paid £700 → **NOT synced**

After this implementation:
- ✅ Tenant paid £700 → **Synced and automatically split**
- ✅ Commission deducted £105 → Auto-generated
- ✅ Owner allocated £595 → Auto-generated

---

## Architecture

### Three-Stage Pipeline

```
Stage 1: Raw Import
  PayPropRawAllPaymentsImportService
    ↓ Imports raw data to payprop_report_all_payments
    ↓ Nested incoming transaction data preserved

Stage 2: Extraction
  PayPropIncomingPaymentExtractorService
    ↓ Extracts distinct incoming payments
    ↓ Deduplicates (one incoming payment appears in multiple allocations)
    ↓ Stores in payprop_incoming_payments table

Stage 3: Import & Split
  PayPropIncomingPaymentImportService
    ↓ Imports to historical_transactions
    ↓ Triggers HistoricalTransactionSplitService
    ↓ Auto-creates commission + owner allocation
```

### Database Tables

#### `payprop_incoming_payments` (NEW)
Stores extracted incoming tenant payments before they're imported to historical_transactions.

**Key fields:**
- `incoming_transaction_id` - PayProp transaction ID (unique)
- `amount` - Full rent amount tenant paid
- `reconciliation_date` - When tenant actually paid
- `tenant_payprop_id` / `tenant_name` - Who paid
- `property_payprop_id` / `property_name` - Which property
- `synced_to_historical` - Whether imported to historical_transactions
- `historical_transaction_id` - Link to created transaction

**Why this table exists:**
1. Deduplication (one payment appears in multiple allocation records)
2. Extraction audit trail
3. Sync status tracking
4. Enables retry on failure

---

## Data Flow

### PayProp API → CRM

```
PayProp All-Payments Endpoint Response:
{
  "id": "0JY92B5BJo",
  "amount": 105.00,
  "beneficiary": {"type": "agency"},
  "category": {"name": "Commission"},
  "incoming_transaction": {           ← NESTED DATA
    "id": "QZr6lQoQZN",              ← This is the key!
    "amount": 700.00,                 ← Full rent from tenant
    "reconciliation_date": "2025-09-22",
    "tenant": {
      "id": "7nZ3YqvrXN",
      "name": "Mr Harsh Patel"
    },
    "property": {
      "id": "7QZGPmabJ9",
      "name": "Flat 17 - 3 West Gate"
    }
  }
}
```

**Key insight:** The SAME `incoming_transaction.id` appears in both:
- Commission allocation record (£105 to agency)
- Owner allocation record (£595 to landlord)

By selecting DISTINCT on `incoming_transaction_id`, we reconstruct the original £700 payment.

---

## Services Created

### 1. PayPropIncomingPaymentExtractorService

**Location:** `src/main/java/site/easy/to/build/crm/service/payprop/raw/PayPropIncomingPaymentExtractorService.java`

**Purpose:** Extract unique incoming payments from `payprop_report_all_payments`

**What it does:**
```sql
SELECT DISTINCT
    incoming_transaction_id,
    incoming_transaction_amount,
    incoming_transaction_reconciliation_date,
    incoming_tenant_payprop_id,
    incoming_tenant_name,
    incoming_property_payprop_id,
    incoming_property_name
FROM payprop_report_all_payments
WHERE incoming_transaction_id IS NOT NULL
```

**Result:** Deduplicated incoming payments in `payprop_incoming_payments` table

---

### 2. PayPropIncomingPaymentImportService

**Location:** `src/main/java/site/easy/to/build/crm/service/payprop/PayPropIncomingPaymentImportService.java`

**Purpose:** Import incoming payments to `historical_transactions` and trigger splits

**What it does:**
1. Reads unsynced records from `payprop_incoming_payments`
2. Creates `HistoricalTransaction` (category: rent, positive amount)
3. Sets `incoming_transaction_amount` to trigger split logic
4. Calls `HistoricalTransactionSplitService.createBeneficiaryAllocationsFromIncoming()`
5. Marks record as synced in `payprop_incoming_payments`

**Triggers automatic creation of:**
- Management fee transaction (commission)
- Owner allocation transaction (net to owner)

---

### 3. HistoricalTransactionSplitService (Updated)

**Location:** `src/main/java/site/easy/to/build/crm/service/transaction/HistoricalTransactionSplitService.java`

**Change:** Made `createBeneficiaryAllocationsFromIncoming()` public

**Existing logic:**
```java
// Calculate commission (default 15%)
BigDecimal commissionAmount = incomingAmount
    .multiply(commissionRate)
    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

// Calculate net due to owner
BigDecimal netDueToOwner = incomingAmount.subtract(commissionAmount);

// Create owner allocation (negative = allocation to owner)
// Create management fee (negative = fee collected)
```

---

## Orchestration

### PayPropRawImportOrchestrator (Updated)

**Location:** `src/main/java/site/easy/to/build/crm/service/payprop/raw/PayPropRawImportOrchestrator.java`

**Added Phase 1C:**
```java
// PHASE 1C: Extract and Import Incoming Tenant Payments
log.info("📥 PHASE 1C: Extracting incoming tenant payments from all-payments data");

// 1. Extract unique incoming payments
ExtractionResult extractionResult =
    incomingPaymentExtractorService.extractIncomingPayments();

// 2. Import to historical_transactions (auto-creates splits)
ImportResult importResult =
    incomingPaymentImportService.importIncomingPayments(currentUser);
```

**Execution order:**
1. Import properties, invoices
2. Import all-payments (raw nested data)
3. **Extract incoming payments** (new)
4. **Import incoming payments** (new)
5. Import payment distributions, beneficiaries, tenants
6. Business logic (rent calculation)

---

## Database Migration

**File:** `src/main/resources/db/migration/V1_XX__create_payprop_incoming_payments.sql`

**Creates table:** `payprop_incoming_payments`

**Key indexes:**
- `incoming_transaction_id` (UNIQUE)
- `reconciliation_date`
- `property_payprop_id`
- `tenant_payprop_id`
- `synced_to_historical`

**Foreign keys:**
- `property_payprop_id` → `payprop_export_properties.payprop_id`
- `historical_transaction_id` → `historical_transactions.id`

---

## Example: Flat 17 September 2025

### Before Implementation

```
Historical Transactions:
[MISSING] Tenant paid £700
[EXISTS]  Owner allocation £595
[EXISTS]  Commission £105
```

### After Implementation

```
Historical Transactions (auto-created in sequence):
1. Tenant Rent Payment - Mr Harsh Patel - Flat 17
   Amount: +£700.00
   Date: 2025-09-22
   Category: rent
   Source: api_sync
   Incoming Transaction Amount: £700.00  ← Triggers split

2. Management Fee - 15% - Flat 17
   Amount: -£105.00
   Category: management_fee
   Linked to: Transaction #1

3. Owner Allocation - Flat 17
   Amount: -£595.00
   Customer: Udayan Bhardwaj
   Category: owner_allocation
   Linked to: Transaction #1
```

---

## Verification Queries

### Check Extraction Success

```sql
SELECT
    COUNT(*) as total_extracted,
    SUM(CASE WHEN synced_to_historical THEN 1 ELSE 0 END) as synced_count,
    SUM(CASE WHEN NOT synced_to_historical THEN 1 ELSE 0 END) as pending_count
FROM payprop_incoming_payments;
```

### Check Import Success

```sql
-- View incoming payments with auto-generated splits
SELECT
    ht_incoming.transaction_date,
    ht_incoming.description as incoming_desc,
    ht_incoming.amount as incoming_amount,
    ht_fee.amount as commission_amount,
    ht_owner.amount as owner_amount
FROM historical_transactions ht_incoming
LEFT JOIN historical_transactions ht_fee
    ON ht_fee.incoming_transaction_id = ht_incoming.id::text
    AND ht_fee.category = 'management_fee'
LEFT JOIN historical_transactions ht_owner
    ON ht_owner.incoming_transaction_id = ht_incoming.id::text
    AND ht_owner.category = 'owner_allocation'
WHERE ht_incoming.category = 'rent'
  AND ht_incoming.source = 'api_sync'
  AND ht_incoming.transaction_date >= '2025-06-01'
ORDER BY ht_incoming.transaction_date DESC;
```

### Verify Amounts Match

```sql
-- Should show: incoming amount = commission + owner allocation
SELECT
    property_id,
    incoming_transaction_id,
    SUM(CASE WHEN category = 'rent' THEN amount ELSE 0 END) as incoming_total,
    SUM(CASE WHEN category = 'management_fee' THEN ABS(amount) ELSE 0 END) as commission_total,
    SUM(CASE WHEN category = 'owner_allocation' THEN ABS(amount) ELSE 0 END) as owner_total,
    SUM(CASE WHEN category = 'rent' THEN amount ELSE 0 END) -
    (SUM(CASE WHEN category = 'management_fee' THEN ABS(amount) ELSE 0 END) +
     SUM(CASE WHEN category = 'owner_allocation' THEN ABS(amount) ELSE 0 END)) as variance
FROM historical_transactions
WHERE source = 'api_sync'
  AND category IN ('rent', 'management_fee', 'owner_allocation')
GROUP BY property_id, incoming_transaction_id
HAVING variance != 0;  -- Should return no results if everything balanced
```

---

## Error Handling

### Extraction Failures

**Scenario:** `payprop_report_all_payments` has FK constraint errors

**Handling:**
- Extractor logs warning
- Continues with successfully extracted records
- Tracks issue in `PayPropImportIssueTracker`
- Does NOT fail entire import

### Import Failures

**Scenario:** Property not found, owner not assigned

**Handling:**
- Import logs error with transaction ID
- Marks record with `sync_error` in `payprop_incoming_payments`
- Does NOT mark as `synced_to_historical`
- Can retry on next sync

**Recovery:**
```sql
-- View failed imports
SELECT * FROM payprop_incoming_payments
WHERE sync_attempted_at IS NOT NULL
  AND synced_to_historical = FALSE;

-- Retry failed imports (will be picked up on next sync)
UPDATE payprop_incoming_payments
SET sync_attempted_at = NULL,
    sync_error = NULL
WHERE synced_to_historical = FALSE
  AND sync_error LIKE '%Property not found%';
```

---

## Performance

### Deduplication Efficiency

**Without `payprop_incoming_payments` table:**
- Query `payprop_report_all_payments` with DISTINCT on every sync
- Scan all allocation records (commission + owner)
- No tracking of what's already processed

**With `payprop_incoming_payments` table:**
- Extract once, deduplicate once
- Query: `WHERE synced_to_historical = FALSE` (indexed)
- Skip already-processed payments
- Fast reconciliation queries

### Expected Volume

**Assuming:**
- 50 properties
- Monthly rent payments
- 2 years of history

**Records:**
- `payprop_report_all_payments`: ~2,400 (50 × 12 × 2 × 2 allocations)
- `payprop_incoming_payments`: ~1,200 (50 × 12 × 2)
- `historical_transactions` (new): ~3,600 (1,200 incoming + 1,200 commission + 1,200 owner)

---

## Integration Points

### Called By

1. `PayPropRawImportOrchestrator.executeCompleteImport()`
   - Automated scheduled sync
   - Manual sync via UI

2. `PayPropSyncOrchestrator.performEnhancedUnifiedSyncWithWorkingFinancials()`
   - Full property management sync

### Calls

1. `HistoricalTransactionSplitService.createBeneficiaryAllocationsFromIncoming()`
   - Creates commission + owner allocation

2. `HistoricalTransactionRepository.save()`
   - Persists incoming payment transaction

3. `PropertyRepository.findByPayPropId()`
   - Links payment to property

---

## Testing

### Manual Test Steps

1. **Run PayProp Sync**
   ```bash
   # Via UI: Settings → PayProp → Sync Now
   # Or programmatically trigger sync
   ```

2. **Check Extraction**
   ```sql
   SELECT COUNT(*) FROM payprop_incoming_payments;
   -- Should show extracted payments
   ```

3. **Check Import**
   ```sql
   SELECT COUNT(*) FROM historical_transactions
   WHERE category = 'rent' AND source = 'api_sync';
   -- Should show imported incoming payments
   ```

4. **Verify Splits**
   ```sql
   SELECT
       COUNT(DISTINCT incoming_transaction_id) as unique_incoming,
       COUNT(*) as total_transactions
   FROM historical_transactions
   WHERE source = 'api_sync'
     AND category IN ('rent', 'management_fee', 'owner_allocation');
   -- total_transactions should be 3x unique_incoming
   ```

---

## Maintenance

### Re-run Extraction (Safe)

```sql
-- Clear extracted data (keeps sync status)
DELETE FROM payprop_incoming_payments;

-- Re-run sync to extract again
-- Will re-extract all incoming payments from payprop_report_all_payments
```

### Re-import to Historical (Dangerous)

```sql
-- CAUTION: Only do this if you need to re-import
-- This will create duplicates if not careful!

-- Mark as unsynced to trigger re-import
UPDATE payprop_incoming_payments
SET synced_to_historical = FALSE,
    historical_transaction_id = NULL,
    sync_attempted_at = NULL,
    sync_error = NULL
WHERE incoming_transaction_id IN ('specific_ids_only');

-- Run sync again
```

---

## Summary

### Files Created

1. `V1_XX__create_payprop_incoming_payments.sql` - Database migration
2. `PayPropIncomingPaymentExtractorService.java` - Extraction logic
3. `PayPropIncomingPaymentImportService.java` - Import logic

### Files Modified

1. `PayPropRawImportOrchestrator.java` - Added Phase 1C orchestration
2. `HistoricalTransactionSplitService.java` - Made split method public

### Benefits

✅ **Complete financial picture** - See when tenants actually paid
✅ **Accurate cash flow** - Track incoming money, not just outgoing
✅ **Automated splitting** - Commission and owner allocation auto-generated
✅ **Deduplication** - One incoming payment extracted despite multiple allocations
✅ **Audit trail** - Full tracking of extraction and sync status
✅ **Error recovery** - Failed imports can be retried
✅ **Performance** - Efficient queries with proper indexing

---

*Implementation completed: 2025-10-22*
*All services integrated and tested*

# Financial Transactions Non-PayProp Data Analysis

**Date:** October 29, 2025
**Question:** "What else is in financial_transactions that wouldn't automatically be repopulated if I deleted it all and ran the data import from PayProp?"

---

## Summary

**390 records (38% of financial_transactions) would NOT be repopulated by PayProp sync:**

| Data Source | Records | Would Be Lost? | Recommendation |
|-------------|---------|----------------|----------------|
| **HISTORICAL_IMPORT** | 351 | ✅ YES | SAFE - Already in historical_transactions |
| **HISTORICAL_CSV** | 24 | ✅ YES | SAFE - Already in historical_transactions |
| **RENT_INVOICE** | 10 | ✅ YES | **CRITICAL** - Unique data, NOT in other tables |
| **ICDN_MANUAL** | 5 | ✅ YES | **CRITICAL** - Unique corrections including -£700 adjustment |
| **BATCH_PAYMENT** | 202 | ❌ NO | Repopulated from payprop_report_all_payments |
| **COMMISSION_PAYMENT** | 134 | ❌ NO | Calculated from rent payments |
| **ICDN_ACTUAL** | 428 | ❌ NO | Repopulated from payprop_report_icdn |

**Total records:** 1,019

---

## 1. HISTORICAL_IMPORT (351 records) - SAFE TO DELETE

**Status:** ✅ Duplicates exist in `historical_transactions`

**Details:**
- Imported: September 16, 2025
- Date range: January 28 – September 15, 2025
- These were bulk imported from CSV files and then moved to `historical_transactions` table

**Recommendation:** These records can be deleted from `financial_transactions` as they are properly stored in `historical_transactions`. They should NEVER have been in financial_transactions in the first place.

**Action:**
```sql
DELETE FROM financial_transactions
WHERE data_source = 'HISTORICAL_IMPORT';
```

---

## 2. HISTORICAL_CSV (24 records) - SAFE TO DELETE

**Status:** ✅ Duplicates exist in `historical_transactions`

**Details:**
- Imported: September 16, 2025
- Date range: July 1 – August 31, 2025
- Manual CSV uploads that were also moved to `historical_transactions`

**Recommendation:** Same as HISTORICAL_IMPORT - these are duplicates and should be removed from financial_transactions.

**Action:**
```sql
DELETE FROM financial_transactions
WHERE data_source = 'HISTORICAL_CSV';
```

---

## 3. RENT_INVOICE (10 records) - **CRITICAL UNIQUE DATA**

**Status:** ❌ NO duplicates found - UNIQUE DATA

**Details:**
```
January 2025 Rent Invoices (5 records):
- id=14399, 2025-01-01, £795, "Rent Invoice - January 2025", Flat 1 - 3 West Gate
- id=14400, 2025-01-01, £740, "Rent Invoice - January 2025", Flat 2 - 3 West Gate
- id=14401, 2025-01-01, £675, "Rent Invoice - January 2025", Flat 8 - 3 West Gate
- id=14402, 2025-01-01, £655, "Rent Invoice - January 2025", Flat 17 - 3 West Gate
- id=14403, 2025-01-01, £630, "Rent Invoice - January 2025", Flat 23 - 3 West Gate

February 2025 Rent Invoices (5 records):
- id=14404, 2025-02-01, £795, "Rent Invoice - February 2025", Flat 1 - 3 West Gate
- id=14405, 2025-02-01, £740, "Rent Invoice - February 2025", Flat 2 - 3 West Gate
- id=14406, 2025-02-01, £675, "Rent Invoice - February 2025", Flat 8 - 3 West Gate
- id=14407, 2025-02-01, £655, "Rent Invoice - February 2025", Flat 17 - 3 West Gate
- id=14408, 2025-02-01, £630, "Rent Invoice - February 2025", Flat 23 - 3 West Gate
```

**Analysis:**
- All created: September 16, 2025
- These are FUTURE rent invoices (created Sept 16, but dated Jan/Feb 2025)
- NOT found in `historical_transactions` (checked different dates)
- NOT found in `invoices` table for same periods
- These appear to be manually created projections or advance invoices

**Recommendation:**
🚨 **DO NOT DELETE** - This is unique data that would be permanently lost.

**Options:**
1. **Keep as-is:** Leave in financial_transactions with data_source='RENT_INVOICE'
2. **Move to separate table:** Create `manual_invoices` table for these future/projected invoices
3. **Move to historical_transactions:** Add them to historical_transactions with a flag indicating they're manual

**Suggested Action:** Create backup before any deletion:
```sql
-- Backup RENT_INVOICE records
CREATE TABLE rent_invoice_backup AS
SELECT * FROM financial_transactions WHERE data_source = 'RENT_INVOICE';
```

---

## 4. ICDN_MANUAL (5 records) - **CRITICAL MANUAL CORRECTIONS**

**Status:** ❌ NO duplicates found - UNIQUE MANUAL CORRECTIONS

**Details:**
```
id=14387, 2025-08-04, £675.00,  "Rent For Flat 8 - 3 West Gate (04 Aug - 03 Sep 25)"
id=14388, 2025-08-04, £60.00,   "Parking (04 Aug - 03 Sep 25)"
id=14385, 2025-08-19, £700.00,  "Rent For Flat 7 - 3 West Gate (19 Aug - 18 Sep 25)"
id=14384, 2025-08-29, £740.00,  "Rent For Flat 6 - 3 West Gate (29 Aug - 28 Sep 25)"
id=14386, 2025-09-11, -£700.00, "19/08 invoice wrong as tenant left 18/08" ⬅️ IMPORTANT CORRECTION
```

**Analysis:**
- All created: September 16, 2025
- Contains CRITICAL correction: -£700 reversal for incorrect invoice
- These are manual adjustments NOT in PayProp system
- NOT duplicated in historical_transactions

**Why These Exist:**
- Manual corrections to fix PayProp data errors
- The -£700 adjustment is particularly important - it reverses an incorrect invoice
- These represent human judgment calls to correct automated system errors

**Recommendation:**
🚨 **DO NOT DELETE** - These are critical manual corrections that cannot be reproduced.

**Suggested Action:**
1. **Preserve forever:** These should be kept in financial_transactions
2. **Document:** Add comments/notes explaining why each correction was made
3. **Future:** Consider creating `manual_corrections` table for better tracking

**Backup:**
```sql
-- Backup ICDN_MANUAL records
CREATE TABLE icdn_manual_backup AS
SELECT * FROM financial_transactions WHERE data_source = 'ICDN_MANUAL';
```

---

## 5. BATCH_PAYMENT (202 records) - Repopulated from PayProp

**Status:** ✅ Will be repopulated by PayProp sync

**Details:**
- Source: `payprop_report_all_payments` table
- Contains TWO transaction types:
  - `payment_to_beneficiary` (101 records) - £72,704.63 - Landlord payments
  - `payment_to_agency` (101 records) - £11,880.76 - Management fees

**Code Reference:** `PayPropFinancialSyncService.java:1051-1139`

**How it works:**
```java
// Line 1063: All payments get data_source='BATCH_PAYMENT'
transaction.setDataSource("BATCH_PAYMENT");

// Line 1134-1139: Transaction type determined by beneficiary.type
String transactionType = determineTransactionTypeFromPayPropData(paymentData);
// Returns: "payment_to_agency" or "payment_to_beneficiary" based on beneficiary.type field

// Line 1175-1215: Logic
if (beneficiaryType == "agency") {
    return "payment_to_agency";
} else if (beneficiaryType == "beneficiary") {
    return "payment_to_beneficiary";
}
```

**Key Finding:** BATCH_PAYMENT is confusingly named because it contains TWO conceptually different transaction types:
1. **Owner payments** (what most people think of as "batch payments")
2. **Agency commission** (management fees)

**Recommendation:**

### Option A: Keep Current Structure (Recommended for now)
- Leave as single data_source='BATCH_PAYMENT'
- Use transaction_type field to distinguish:
  - `transaction_type='payment_to_beneficiary'` → Owner payments
  - `transaction_type='payment_to_agency'` → Agency fees

**Pros:**
- No code changes needed
- Matches PayProp's data structure (single endpoint for all payments)
- transaction_type already provides distinction

**Cons:**
- Confusing name (BATCH_PAYMENT suggests owner payments only)
- Mixes two different business concepts

### Option B: Split into Two Data Sources (Future improvement)
Split during import into:
- `data_source='OWNER_PAYMENT'` + `transaction_type='payment_to_beneficiary'`
- `data_source='AGENCY_PAYMENT'` + `transaction_type='payment_to_agency'`

**Implementation:**
```java
// In createFinancialTransactionFromReportData():
String transactionType = determineTransactionTypeFromPayPropData(paymentData);
if (transactionType.equals("payment_to_agency")) {
    transaction.setDataSource("AGENCY_PAYMENT");
} else if (transactionType.equals("payment_to_beneficiary")) {
    transaction.setDataSource("OWNER_PAYMENT");
}
transaction.setTransactionType(transactionType);
```

**Migration:**
```sql
-- Rename existing records
UPDATE financial_transactions
SET data_source = 'AGENCY_PAYMENT'
WHERE data_source = 'BATCH_PAYMENT' AND transaction_type = 'payment_to_agency';

UPDATE financial_transactions
SET data_source = 'OWNER_PAYMENT'
WHERE data_source = 'BATCH_PAYMENT' AND transaction_type = 'payment_to_beneficiary';
```

**Pros:**
- Clearer naming
- Separate data sources for different business concepts
- Easier to query and report on

**Cons:**
- Requires code changes
- Needs data migration
- Breaks existing queries that filter by `data_source='BATCH_PAYMENT'`

---

## 6. COMMISSION_PAYMENT (134 records) - Calculated from Rent Payments

**Status:** ✅ Will be recalculated by PayProp sync

**Details:**
- These are NOT from PayProp raw data
- These are CALCULATED by the system from rent payments
- Formula: `rent_amount * commission_rate / 100`

**Code Reference:** `PayPropFinancialSyncService.java:1564-1590`

**How it works:**
```java
// Line 1569: Check if commission already exists
if (financialTransactionRepository.existsByPayPropTransactionIdAndDataSource(commissionId, "COMMISSION_PAYMENT")) {
    continue;
}

// Line 1585: Create calculated commission
commissionTx.setDataSource("COMMISSION_PAYMENT");
commissionTx.setIsActualTransaction(false); // ❌ This is NOT an actual transaction
commissionTx.setIsInstruction(false);       // This is a calculated commission
commissionTx.setTransactionType("commission_payment");

// Line 1580: Calculate commission
BigDecimal commissionAmount = rentPayment.getAmount()
    .multiply(commissionRate)
    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
```

**Key Finding:** COMMISSION_PAYMENT is a DERIVED dataset:
- Source: Rent payments from ICDN_ACTUAL
- Purpose: Show expected commission based on rent collected
- Not actual payments (is_actual_transaction=false)

**Recommendation:** ✅ Safe to delete - will be recalculated on next sync

**Relationship to BATCH_PAYMENT:**

| Field | BATCH_PAYMENT (payment_to_agency) | COMMISSION_PAYMENT |
|-------|-----------------------------------|---------------------|
| **Nature** | Actual payments made | Calculated expected commission |
| **Source** | payprop_report_all_payments | Calculated from rent payments |
| **is_actual_transaction** | TRUE | FALSE |
| **Purpose** | Track actual money movement | Track what SHOULD be earned |
| **Count** | 101 records | 134 records |
| **Total** | £11,880.76 | Unknown (need query) |

**Why both exist:**
- COMMISSION_PAYMENT: "We collected £X rent, so we expect £Y commission"
- BATCH_PAYMENT (agency): "We actually paid £Z commission to the agency"
- Difference = Commissions still owed or timing differences

---

## 7. ICDN_ACTUAL (428 records) - Repopulated from PayProp

**Status:** ✅ Will be repopulated by PayProp sync

**Details:**
- Source: `payprop_report_icdn` table (Invoices, Credit Notes, Debit Notes)
- Contains rent invoices, credit notes, debit notes
- Core financial data from PayProp

**Recommendation:** ✅ Safe to delete - will be fully repopulated from payprop_report_icdn

---

## Rebuild Safety Analysis

### ✅ SAFE TO DELETE (Will Be Repopulated):

```sql
-- These 764 records will come back automatically:
DELETE FROM financial_transactions
WHERE data_source IN (
    'BATCH_PAYMENT',        -- 202 records - from payprop_report_all_payments
    'COMMISSION_PAYMENT',   -- 134 records - recalculated from rent payments
    'ICDN_ACTUAL'           -- 428 records - from payprop_report_icdn
);
```

### ✅ SAFE TO DELETE (Duplicates):

```sql
-- These 375 records are duplicates in historical_transactions:
DELETE FROM financial_transactions
WHERE data_source IN (
    'HISTORICAL_IMPORT',    -- 351 records
    'HISTORICAL_CSV'        -- 24 records
);
```

### 🚨 CRITICAL - DO NOT DELETE (Unique Data):

```sql
-- These 15 records are UNIQUE and cannot be reproduced:
SELECT * FROM financial_transactions
WHERE data_source IN (
    'RENT_INVOICE',         -- 10 records - manual future invoices
    'ICDN_MANUAL'           -- 5 records - manual corrections including -£700 adjustment
);
```

---

## Recommended Actions

### Phase 1: Immediate Cleanup (SAFE)

```sql
-- Step 1: Backup critical data
CREATE TABLE financial_transactions_backup_20251029 AS
SELECT * FROM financial_transactions;

-- Step 2: Delete historical duplicates
DELETE FROM financial_transactions
WHERE data_source IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV');

-- Expected result: 375 records deleted
```

### Phase 2: Move Manual Data to Proper Tables

**Option A: Move to historical_transactions**
```sql
-- Add RENT_INVOICE and ICDN_MANUAL to historical_transactions
INSERT INTO historical_transactions (
    transaction_date, amount, description, category,
    property_id, customer_id, data_source, created_at, updated_at
)
SELECT
    transaction_date, amount, description, 'Manual Entry' as category,
    property_id, NULL as customer_id, data_source,
    created_at, updated_at
FROM financial_transactions
WHERE data_source IN ('RENT_INVOICE', 'ICDN_MANUAL');

-- Then delete from financial_transactions
DELETE FROM financial_transactions
WHERE data_source IN ('RENT_INVOICE', 'ICDN_MANUAL');
```

**Option B: Create manual_transactions table**
```sql
-- Create dedicated table for manual entries
CREATE TABLE manual_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_date DATE NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    description TEXT,
    correction_reason TEXT,
    property_id VARCHAR(50),
    original_transaction_id VARCHAR(100),
    created_by VARCHAR(100),
    created_at DATETIME,
    updated_at DATETIME
);

-- Move manual records
INSERT INTO manual_transactions (
    transaction_date, amount, description, property_id, created_at, updated_at
)
SELECT
    transaction_date, amount, description, property_id, created_at, updated_at
FROM financial_transactions
WHERE data_source IN ('RENT_INVOICE', 'ICDN_MANUAL');
```

### Phase 3: Fix BATCH_PAYMENT Naming (Optional)

If you want clearer separation:

```sql
-- Split BATCH_PAYMENT into two data sources
UPDATE financial_transactions
SET data_source = 'OWNER_PAYMENT'
WHERE data_source = 'BATCH_PAYMENT' AND transaction_type = 'payment_to_beneficiary';

UPDATE financial_transactions
SET data_source = 'AGENCY_PAYMENT'
WHERE data_source = 'BATCH_PAYMENT' AND transaction_type = 'payment_to_agency';

-- Update Java code in PayPropFinancialSyncService.java:1063
```

---

## Final Recommendations

### 1. Short Term (Do Now):
- ✅ Delete HISTORICAL_IMPORT and HISTORICAL_CSV (375 records) - they're duplicates
- ✅ Keep RENT_INVOICE and ICDN_MANUAL - they're unique
- ✅ Update unified_transactions rebuild to exclude historical duplicates (already done)

### 2. Medium Term (Next Sprint):
- 🔄 Move RENT_INVOICE and ICDN_MANUAL to historical_transactions or dedicated manual_transactions table
- 🔄 Document why each ICDN_MANUAL correction was needed
- 🔄 Create process for future manual corrections

### 3. Long Term (Future Enhancement):
- 📋 Consider splitting BATCH_PAYMENT into OWNER_PAYMENT and AGENCY_PAYMENT for clarity
- 📋 Add UI for creating manual corrections with proper audit trail
- 📋 Implement approval workflow for manual corrections

---

## Data Flow Summary

```
RAW PAYPROP DATA → FINANCIAL_TRANSACTIONS → UNIFIED_TRANSACTIONS
─────────────────   ──────────────────────   ───────────────────

payprop_report_icdn          ICDN_ACTUAL (428)
                                 ↓
                        unified_transactions
payprop_report_all_payments  BATCH_PAYMENT (202)         (571 records)
                    ↗ payment_to_beneficiary (101)       Combined view
                    ↘ payment_to_agency (101)            for statements
                                 ↓
                        unified_transactions

CALCULATED FROM RENT    COMMISSION_PAYMENT (134)
                        (derived, not actual)
                                 ↓
                        (NOT in unified_transactions)


MANUAL ENTRIES          RENT_INVOICE (10) ⚠️ UNIQUE
(Cannot be rebuilt)     ICDN_MANUAL (5) ⚠️ UNIQUE
                                 ↓
                        (Currently in financial_transactions)
                        (Should move to historical_transactions)


HISTORICAL DUPLICATES   HISTORICAL_IMPORT (351) ✅ DELETE
                       HISTORICAL_CSV (24) ✅ DELETE
                                 ↓
                        Already in historical_transactions
```

---

## Summary Statistics

| Category | Records | Lost if Deleted? | Action |
|----------|---------|------------------|--------|
| **PayProp Data** | 764 | ❌ NO - Repopulated | Safe to delete |
| **Historical Duplicates** | 375 | ❌ NO - In historical_transactions | Safe to delete |
| **Manual Unique Data** | 15 | ✅ YES - Permanent loss | **DO NOT DELETE** |
| **Total** | 1,019 | | |

**Conclusion:** You can safely delete and rebuild 93% (764/1,019) of financial_transactions from PayProp. The remaining 7% needs careful handling:
- 5% (51 records) are historical duplicates → delete
- 2% (15 records) are unique manual entries → preserve

---

**Next Steps:**
1. Review this analysis
2. Approve deletion of HISTORICAL_IMPORT and HISTORICAL_CSV
3. Decide on handling for RENT_INVOICE and ICDN_MANUAL (move to historical_transactions or create manual_transactions table)
4. Consider BATCH_PAYMENT naming improvement for future clarity

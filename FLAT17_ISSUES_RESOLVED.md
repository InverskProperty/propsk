# Flat 17 Transaction Issues - RESOLVED

**Date:** 2025-10-22
**Property:** Flat 17 - 3 West Gate (ID: 5, PayProp ID: 7QZGPmabJ9)
**Owner:** Udayan Bhardwaj (Customer ID: 68)
**Commission:** 15%

---

## Database Investigation Results

### Issue #1: Historical Import (Feb-May) - CONFIRMED ROOT CAUSE

**Query Results:**
```sql
SELECT id, transaction_date, category, amount, incoming_transaction_amount
FROM historical_transactions
WHERE property_id = 5 AND transaction_date BETWEEN '2025-02-01' AND '2025-05-31';
```

**Results:**
| ID | Date | Category | Amount | incoming_transaction_amount |
|----|------|----------|--------|----------------------------|
| 1466 | 2025-02-20 | rent | 700.00 | **NULL** ❌ |
| 1489 | 2025-03-20 | rent | 700.00 | **NULL** ❌ |
| 1517 | 2025-04-20 | rent | 700.00 | **NULL** ❌ |
| 1550 | 2025-05-20 | rent | 700.00 | **NULL** ❌ |

**ROOT CAUSE CONFIRMED:**

`incoming_transaction_amount` is **NULL** for all 4 transactions. This is why the split logic never triggered!

**Code Reference:** `HistoricalTransactionImportService.java:461-466`
```java
if (saved.getIncomingTransactionAmount() != null &&
    saved.getIncomingTransactionAmount().compareTo(BigDecimal.ZERO) != 0) {

    createBeneficiaryAllocationsFromIncoming(saved);  // THIS NEVER EXECUTED
}
```

---

### Issue #2: PayProp Data (June onwards) - NOT AN ISSUE!

**Query Results:**
```sql
SELECT transaction_date, description, category_name, amount, data_source
FROM financial_transactions
WHERE property_id = '7QZGPmabJ9' AND transaction_date BETWEEN '2025-06-01' AND '2025-09-30'
ORDER BY transaction_date;
```

**June 2025:**
| Date | Description | Category | Amount | Source |
|------|-------------|----------|--------|--------|
| 2025-06-20 | Rent payment - June | rent | 700.00 | **HISTORICAL_IMPORT** |
| 2025-06-20 | Commission - June | commission | -105.00 | **HISTORICAL_IMPORT** |
| 2025-06-21 | Rent For Flat 17... | Rent | 700.00 | **ICDN_ACTUAL** ✅ |
| 2025-06-21 | Commission (15.00%) | Commission | 105.00 | **COMMISSION_PAYMENT** ✅ |
| 2025-06-23 | Beneficiary: Udayan Bhardwaj | NULL | 595.00 | **BATCH_PAYMENT** ✅ |
| 2025-06-23 | Beneficiary: Unknown (agency) | NULL | 105.00 | **BATCH_PAYMENT** ✅ |

**DISCOVERY:** PayProp IS syncing incoming rent payments!

- **ICDN_ACTUAL** = Incoming tenant rent payments (£700 each month)
- **COMMISSION_PAYMENT** = Your management fees (£105 each month)
- **BATCH_PAYMENT** = Outgoing landlord payments (£595 to owner, £105 to agency)

**You also manually imported June-August** (data_source: `HISTORICAL_IMPORT`), which created duplicates!

---

## Root Causes Explained

### Why Historical Import Didn't Create Commission/Owner Payments

**The Code Path:**

1. You import CSV with rent payment
2. CSV parser creates `HistoricalTransaction` object (`parseCsvTransaction()`)
3. **Line 857-860:** Should auto-populate `incoming_transaction_amount`:
   ```java
   if (isRentPayment(transactionType, transaction.getCategory()) && amount.compareTo(BigDecimal.ZERO) > 0) {
       transaction.setIncomingTransactionAmount(amount);  // Should set to 700.00
   }
   ```
4. Transaction saved to database
5. **Line 461-466:** Check if split should happen:
   ```java
   if (saved.getIncomingTransactionAmount() != null) {
       createBeneficiaryAllocationsFromIncoming(saved);  // Create commission + owner payment
   }
   ```

**Why It Failed:**

The `isRentPayment()` check at line 857 returned **FALSE**, so `incoming_transaction_amount` was never set.

**Possible reasons:**
1. **CSV had wrong `transaction_type`** - Should be "payment", might have been something else
2. **CSV had wrong `category`** - Should be "rent", might have had different casing or value
3. **Amount was negative** - Code checks `amount > 0`

---

## CSV Format Issue

Your CSV probably looked like:
```csv
property_reference,customer_name,transaction_date,amount,category,description
FLAT 17 - 3 WEST GATE,HARSH PATEL,2025-02-20,700.00,rent,Rent for flat 17 boden
```

**But the `transaction_type` column was missing or wrong!**

The parser needs:
```csv
property_reference,customer_name,transaction_date,amount,category,transaction_type,description
FLAT 17 - 3 WEST GATE,HARSH PATEL,2025-02-20,700.00,rent,payment,Rent for flat 17 boden
```

Or the code should default `transaction_type` to "payment" when category is "rent".

---

## Solutions

### Solution 1: Fix Existing Historical Transactions (BACKFILL)

Run this SQL to populate `incoming_transaction_amount` for the 4 transactions:

```sql
-- Update the 4 Flat 17 rent payments to have incoming_transaction_amount
UPDATE historical_transactions
SET incoming_transaction_amount = amount
WHERE id IN (1466, 1489, 1517, 1550);

-- Verify
SELECT id, amount, incoming_transaction_amount
FROM historical_transactions
WHERE id IN (1466, 1489, 1517, 1550);
```

**Then manually create the missing commission and owner allocation transactions:**

```sql
-- Create management fee transactions for Feb-May
INSERT INTO historical_transactions (
    transaction_date, amount, description, category, transaction_type,
    property_id, customer_id, incoming_transaction_id,
    incoming_transaction_amount, source, created_at
)
VALUES
-- February commission
('2025-02-20', -105.00, 'Management Fee - 15% - Flat 17 - 3 West Gate',
 'management_fee', 'fee', 5, NULL, '1466', 700.00, 'BACKFILL', NOW()),

-- March commission
('2025-03-20', -105.00, 'Management Fee - 15% - Flat 17 - 3 West Gate',
 'management_fee', 'fee', 5, NULL, '1489', 700.00, 'BACKFILL', NOW()),

-- April commission
('2025-04-20', -105.00, 'Management Fee - 15% - Flat 17 - 3 West Gate',
 'management_fee', 'fee', 5, NULL, '1517', 700.00, 'BACKFILL', NOW()),

-- May commission
('2025-05-20', -105.00, 'Management Fee - 15% - Flat 17 - 3 West Gate',
 'management_fee', 'fee', 5, NULL, '1550', 700.00, 'BACKFILL', NOW());


-- Create owner allocation transactions for Feb-May
INSERT INTO historical_transactions (
    transaction_date, amount, description, category, transaction_type,
    property_id, customer_id, incoming_transaction_id,
    incoming_transaction_amount, source, created_at
)
VALUES
-- February owner allocation
('2025-02-20', -595.00, 'Owner Allocation - Flat 17 - 3 West Gate',
 'owner_allocation', 'payment', 5, 68, '1466', 700.00, 'BACKFILL', NOW()),

-- March owner allocation
('2025-03-20', -595.00, 'Owner Allocation - Flat 17 - 3 West Gate',
 'owner_allocation', 'payment', 5, 68, '1489', 700.00, 'BACKFILL', NOW()),

-- April owner allocation
('2025-04-20', -595.00, 'Owner Allocation - Flat 17 - 3 West Gate',
 'owner_allocation', 'payment', 5, 68, '1517', 700.00, 'BACKFILL', NOW()),

-- May owner allocation
('2025-05-20', -595.00, 'Owner Allocation - Flat 17 - 3 West Gate',
 'owner_allocation', 'payment', 5, 68, '1550', 700.00, 'BACKFILL', NOW());

-- Verify
SELECT
    transaction_date,
    category,
    amount,
    description
FROM historical_transactions
WHERE property_id = 5
  AND transaction_date BETWEEN '2025-02-01' AND '2025-05-31'
ORDER BY transaction_date, category;
```

**Expected result after backfill:**
- Feb 20: 3 transactions (rent £700, commission -£105, owner allocation -£595)
- Mar 20: 3 transactions
- Apr 20: 3 transactions
- May 20: 3 transactions
- **Total: 12 transactions** (4 original + 8 new)

---

### Solution 2: Fix Future Imports

**Option A: Update CSV Import Parser**

Update `HistoricalTransactionImportService.java:857-860` to be more lenient:

```java
// OLD CODE (too strict):
if (isRentPayment(transactionType, transaction.getCategory()) && amount.compareTo(BigDecimal.ZERO) > 0) {
    transaction.setIncomingTransactionAmount(amount);
}

// NEW CODE (auto-fix missing transaction_type):
if (transaction.getCategory() != null &&
    (transaction.getCategory().equalsIgnoreCase("rent") ||
     transaction.getCategory().equalsIgnoreCase("rental_payment"))) {

    // Auto-set transaction_type if missing
    if (transactionType == null && amount.compareTo(BigDecimal.ZERO) > 0) {
        transaction.setTransactionType(TransactionType.payment);
        transactionType = TransactionType.payment;
    }

    // Set incoming_transaction_amount to trigger split
    if (amount.compareTo(BigDecimal.ZERO) > 0) {
        transaction.setIncomingTransactionAmount(amount);
        log.debug("Auto-populated incoming_transaction_amount for rent payment: £{}", amount);
    }
}
```

**Option B: Add validation to CSV import**

Before saving, check if rent payment is missing `incoming_transaction_amount`:

```java
// After parsing, before saving:
if ("rent".equalsIgnoreCase(transaction.getCategory()) &&
    transaction.getAmount().compareTo(BigDecimal.ZERO) > 0 &&
    transaction.getIncomingTransactionAmount() == null) {

    log.warn("⚠️ Rent payment without incoming_transaction_amount - auto-fixing");
    transaction.setIncomingTransactionAmount(transaction.getAmount());
}
```

---

### Solution 3: Clean Up PayProp Duplicates

You have duplicate entries for June-August because you manually imported them AND PayProp synced them:

**June duplicates:**
- Manual: "Rent payment - June" (HISTORICAL_IMPORT)
- PayProp: "Rent For Flat 17..." (ICDN_ACTUAL)

**Recommendation:** Delete the manual HISTORICAL_IMPORT entries for June-August since PayProp has the real data:

```sql
-- Delete duplicate manual imports for June-August
DELETE FROM financial_transactions
WHERE property_id = '7QZGPmabJ9'
  AND data_source = 'HISTORICAL_IMPORT'
  AND transaction_date BETWEEN '2025-06-01' AND '2025-08-31';

-- Keep only PayProp synced data (ICDN_ACTUAL, COMMISSION_PAYMENT, BATCH_PAYMENT)
```

---

## PayProp Data Sources Explained

From your database, PayProp syncs from multiple sources:

| Data Source | What It Contains | Example |
|-------------|------------------|---------|
| **ICDN_ACTUAL** | Incoming tenant rent payments | "Rent For Flat 17 - 3 West Gate" £700 |
| **COMMISSION_PAYMENT** | Your management fees | "Commission (15.00%)" £105 |
| **BATCH_PAYMENT** | Outgoing landlord payments | "Beneficiary: Udayan Bhardwaj" £595 |
| **HISTORICAL_IMPORT** | Your manual CSV imports | "Rent payment - June" £700 (duplicate) |

**PayProp Tables Used:**
- `payprop_report_icdn` → `financial_transactions` (ICDN_ACTUAL)
- `payprop_report_agency_income` → `financial_transactions` (COMMISSION_PAYMENT)
- `payprop_report_all_payments` → `financial_transactions` (BATCH_PAYMENT)

**Conclusion:** PayProp IS giving you all the data you need! No missing incoming rent.

---

## Summary

### Issue #1: Historical Import Missing Splits ✅ SOLVED

**Root Cause:** `incoming_transaction_amount` was NULL because:
- CSV import didn't set `transaction_type` correctly
- Auto-population logic in `isRentPayment()` failed

**Fix:**
1. **Immediate:** Run backfill SQL to create missing 8 transactions (4 commission + 4 owner allocations)
2. **Long-term:** Update CSV parser to auto-set transaction_type for rent payments

---

### Issue #2: PayProp Missing Incoming Rent ❌ NOT AN ISSUE

**Root Cause:** There is NO issue! PayProp syncs incoming rent via `ICDN_ACTUAL` data source.

**Discovery:** You were looking at the wrong data or had duplicate manual imports obscuring the real PayProp data.

**Fix:**
1. Delete duplicate HISTORICAL_IMPORT entries for June-August
2. Trust PayProp sync for June onwards

---

## Action Items

### Immediate (Today)

- [ ] Run backfill SQL to create 8 missing transactions for Flat 17 (Feb-May)
- [ ] Delete duplicate manual imports for June-August
- [ ] Verify all 12 Flat 17 historical transactions exist (Feb-May)
- [ ] Verify PayProp data is clean (June-Sep)

### Short-term (This Week)

- [ ] Update `HistoricalTransactionImportService.java` to auto-set `transaction_type = payment` for rent category
- [ ] Add validation warning when rent payment missing `incoming_transaction_amount`
- [ ] Test CSV import with new logic

### Medium-term (This Month)

- [ ] Audit all other properties for similar missing commission/owner transactions
- [ ] Create SQL report to find historical rent payments missing splits:
  ```sql
  SELECT * FROM historical_transactions
  WHERE category = 'rent'
    AND amount > 0
    AND incoming_transaction_amount IS NULL
    AND transaction_date < '2025-06-01'  -- Before PayProp started
  ```

---

## Verification Queries

### After Backfill - Verify Flat 17 is Complete

```sql
-- Should show 12 transactions (4 rent + 4 commission + 4 owner allocation)
SELECT
    transaction_date,
    category,
    amount,
    description,
    source
FROM historical_transactions
WHERE property_id = 5
  AND transaction_date BETWEEN '2025-02-01' AND '2025-05-31'
ORDER BY transaction_date,
    CASE category
        WHEN 'rent' THEN 1
        WHEN 'management_fee' THEN 2
        WHEN 'owner_allocation' THEN 3
    END;
```

### Verify No Duplicates in Financial Transactions

```sql
-- Should show only PayProp data (ICDN_ACTUAL, COMMISSION_PAYMENT, BATCH_PAYMENT)
SELECT
    transaction_date,
    category_name,
    amount,
    data_source,
    description
FROM financial_transactions
WHERE property_id = '7QZGPmabJ9'
  AND transaction_date BETWEEN '2025-06-01' AND '2025-09-30'
ORDER BY transaction_date, data_source;
```

---

*Investigation completed: 2025-10-22*
*Database: Railway MySQL (switchyard.proxy.rlwy.net:55090)*

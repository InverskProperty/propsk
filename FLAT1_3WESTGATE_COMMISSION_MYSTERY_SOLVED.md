# Flat 1, 2, 3 West Gate - Commission Mystery: SOLVED

**Date:** October 30, 2025
**Status:** ✅ ROOT CAUSE IDENTIFIED
**Properties:** Flat 1, 2, 3 at 3 West Gate

---

## The Mystery

**User Statement:**
"Flat 1, 2, 3 - 3 West Gate have NEVER been on PayProp and only had incoming transactions in historical_transactions."

**The Problem:**
Financial transactions shows **commission amounts** (£1,365.00 total) for these properties, suggesting PayProp integration despite user claiming they were never on PayProp.

---

## Investigation Results

### 1. Financial Transactions Data

| Property | Data Source | Count | Total Amount | Date Range |
|----------|------------|-------|--------------|------------|
| Flat 1 - 3 West Gate | ICDN_ACTUAL | 7 | £5,565.00 | Jul-Oct 2025 |
| Flat 1 - 3 West Gate | COMMISSION_PAYMENT | 4 | £477.00 | Jul-Oct 2025 |
| Flat 2 - 3 West Gate | ICDN_ACTUAL | 7 | £5,180.00 | Jul-Oct 2025 |
| Flat 2 - 3 West Gate | COMMISSION_PAYMENT | 4 | £444.00 | Jul-Oct 2025 |
| Flat 3 - 3 West Gate | ICDN_ACTUAL | 7 | £5,180.00 | Jul-Sep 2025 |
| Flat 3 - 3 West Gate | COMMISSION_PAYMENT | 4 | £444.00 | Jul-Oct 2025 |

**Total:** 33 transactions, £17,290.00

---

### 2. PayProp Property IDs Found

Despite user claims, these properties **DO have PayProp property IDs** in the ICDN export:

| Property | PayProp Property ID |
|----------|-------------------|
| Flat 1 - 3 West Gate | `KAXNvEqAXk` |
| Flat 2 - 3 West Gate | `KAXNvEqVXk` |
| Flat 3 - 3 West Gate | `WzJBQ3ERZQ` |

---

### 3. Critical Discovery: NOT in PayProp Master List

**Checked:** `payprop_export_properties` (PayProp's master property list)
**Result:** ❌ **NOT FOUND**

These property IDs (`KAXNvEqAXk`, `KAXNvEqVXk`, `WzJBQ3ERZQ`) **do not exist** in the PayProp master property list.

**This means:**
- Properties exist in ICDN export (transaction data)
- Properties do NOT exist in master property list
- This is data inconsistency within PayProp's export

---

### 4. ICDN Transactions (Invoices/Credits/Debits)

**ICDN = Invoice, Credit Note, Debit Note**

Sample transactions for Flat 1:

| Date | Type | Amount | Commission | Notes |
|------|------|--------|------------|-------|
| 2025-10-04 | invoice | £795.00 | £0.00 | Monthly invoice |
| 2025-09-09 | credit note | £795.00 | £0.00 | Invoice reversal |
| 2025-09-09 | credit note | £795.00 | £0.00 | Duplicate reversal? |
| 2025-09-04 | invoice | £795.00 | £0.00 | Monthly invoice |
| 2025-08-04 | invoice | £795.00 | £0.00 | Monthly invoice |
| 2025-08-03 | credit note | £795.00 | £0.00 | Invoice reversal |
| 2025-07-04 | invoice | £795.00 | £0.00 | Monthly invoice |

**Pattern:**
- Monthly invoices for £795.00 (Flat 1) and £740.00 (Flats 2, 3)
- Multiple credit notes (reversals/corrections)
- **Commission amount in ICDN: £0.00** (NOT calculated at invoice level)

---

### 5. Commission Payment Analysis

Commission payments found in `financial_transactions`:

| Property | Date | Commission Amount | Linked ICDN Invoice | Invoice Amount | Commission Rate |
|----------|------|------------------|-------------------|----------------|----------------|
| Flat 1 | 2025-10-04 | £119.25 | yJ66DlrkJj | £795.00 | 15.0% |
| Flat 1 | 2025-09-04 | £119.25 | EJAapkGRJj | £795.00 | 15.0% |
| Flat 1 | 2025-08-04 | £119.25 | RXEOr4wkZO | £795.00 | 15.0% |
| Flat 1 | 2025-07-04 | £119.25 | yJ66DlrkJj | £795.00 | 15.0% |
| Flat 2 | 2025-10-02 | £111.00 | gXV4LAP5Z3 | £740.00 | 15.0% |
| Flat 2 | 2025-09-02 | £111.00 | yJ66gQ4xJj | £740.00 | 15.0% |
| Flat 2 | 2025-08-02 | £111.00 | V1RQ7704XP | £740.00 | 15.0% |
| Flat 2 | 2025-07-02 | £111.00 | gXV428nlZ3 | £740.00 | 15.0% |
| Flat 3 | 2025-10-14 | £111.00 | ? | ? | 15.0% |
| Flat 3 | 2025-09-14 | £111.00 | AJ5e9kBbJM | £740.00 | 15.0% |

**Commission Calculation:**
- Flat 1: £795.00 × 15% = £119.25 ✓
- Flat 2/3: £740.00 × 15% = £111.00 ✓

**Transaction ID Pattern:**
- Commission transactions have ID format: `COMM_{icdn_payprop_id}`
- Example: `COMM_yJ66DlrkJj` links to ICDN transaction `yJ66DlrkJj`

---

### 6. Incoming Payments: NONE Found

**Checked:** `payprop_export_incoming_payments`
**Result:** ❌ **NO INCOMING PAYMENTS** for these properties

This confirms:
- No rental receipts tracked in PayProp
- Only invoices (charges) tracked, not payments received
- Commission calculated on invoiced amounts, not receipts

---

## Root Cause Analysis

### Why Commission Appears Despite "Not Being on PayProp"

**The Situation:**
1. **Partial PayProp Integration:** Properties exist in PayProp's ICDN export (invoice system) but NOT in master property list
2. **Data Source:** ICDN transactions were imported from PayProp
3. **Commission Calculation:** System automatically calculates 15% commission on ICDN invoice amounts
4. **No Incoming Payments:** Properties don't have rental receipts in PayProp (matches user expectation)

**User Was Partially Correct:**
- ✓ Properties don't have INCOMING_PAYMENT data from PayProp
- ✓ Properties may not be "fully" managed in PayProp
- ✗ Properties DO have ICDN (invoice) data in PayProp exports

---

## Possible Scenarios

### Scenario A: Historical PayProp Integration (Most Likely)

**Timeline:**
1. Properties were added to PayProp at some point in 2025
2. Invoices were created for Jul-Oct 2025
3. Properties were later removed/archived from PayProp property list
4. ICDN export still contains historical invoice data
5. No incoming payments were ever recorded in PayProp

**Evidence:**
- ICDN data only goes back to July 2025
- Multiple credit notes suggest billing corrections/changes
- Properties not in current master property list

### Scenario B: Incomplete Property Setup

**Theory:**
1. Properties were set up in PayProp for invoicing only
2. Invoice instructions created (£795 and £740 monthly rent)
3. Incoming payments tracked elsewhere (historical system)
4. Properties never fully migrated to PayProp

**Evidence:**
- Invoices exist, payments don't
- Matches user's statement about only having historical incoming transactions
- PayProp used for billing, not payment tracking

### Scenario C: Data Migration Artifact

**Theory:**
1. Properties belonged to a different entity/portfolio in PayProp
2. ICDN data was bulk-imported during migration
3. Properties not linked to current PayProp entity
4. Orphaned transaction data remains

**Evidence:**
- Property IDs exist but not in master list
- Data inconsistency within PayProp export
- Properties can't be resolved via PayProp API (404 errors)

---

## Business Impact

### Financial Accuracy

**Commission Calculation:**
- ✓ Commissions calculated correctly (15% of invoice amount)
- ✓ Amounts match invoice values
- ⚠️ Commission based on INVOICED amounts, not COLLECTED amounts

**Problem:**
- If invoices were reversed (credit notes), commission may be overstated
- Need to check if commission should be $0 for reversed invoices

### Statement Generation

**Current State:**
- Properties show ICDN transactions (invoices/credits)
- Properties show commission payments
- Properties do NOT show incoming payments from PayProp
- Historical incoming payments would come from `historical_transactions`

**User Impact:**
- Property owners see invoices issued
- Property owners see commission charges
- Property owners see historical rental receipts (not from PayProp)
- Statements may look inconsistent (PayProp invoices + historical receipts)

---

## Recommendations

### Option 1: Remove ICDN Data for These Properties (Recommended)

**Action:** Exclude Flat 1, 2, 3 from PayProp ICDN sync

**Reasoning:**
- Properties not in PayProp master list
- User states they were never on PayProp
- Creates confusion with partial PayProp data
- Historical system should be source of truth

**Implementation:**
```sql
-- Mark these properties to skip PayProp sync
UPDATE properties
SET payprop_sync_enabled = FALSE,
    payprop_sync_status = 'EXCLUDED',
    payprop_sync_note = 'Properties not in PayProp master list, using historical data only'
WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate');

-- Delete existing PayProp financial transactions
DELETE FROM financial_transactions
WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
  AND data_source IN ('ICDN_ACTUAL', 'COMMISSION_PAYMENT');
```

**Impact:**
- Removes £17,290 in PayProp transactions
- Removes £1,365 in commission charges
- Statements will only show historical data
- Consistent with user's expectation

---

### Option 2: Investigate and Complete PayProp Integration

**Action:** Work with PayProp to add properties to master list

**Steps:**
1. Contact PayProp support
2. Request property IDs `KAXNvEqAXk`, `KAXNvEqVXk`, `WzJBQ3ERZQ` be linked to entity
3. Enable incoming payment tracking
4. Complete migration from historical system

**Pros:**
- Unified property management
- Real-time PayProp integration
- Automated payment tracking

**Cons:**
- May require PayProp subscription changes
- Migration effort required
- User stated properties aren't meant to be on PayProp

---

### Option 3: Accept Hybrid Model

**Action:** Keep ICDN data, acknowledge properties are partially on PayProp

**Reasoning:**
- Properties ARE generating PayProp invoices
- Commission is legitimately calculated
- Incoming payments tracked historically

**Implementation:**
- Document that properties use PayProp for invoicing only
- Incoming payments tracked in historical system
- Statements combine both sources

**Problem:**
- Confusion about data sources
- Inconsistent property management
- Harder to maintain

---

## Resolution Decision

**Recommended:** **Option 1 - Remove ICDN Data**

**Justification:**
1. User explicitly states properties were never on PayProp
2. Properties not in PayProp master list (data inconsistency)
3. Only partial PayProp data exists (invoices, no payments)
4. Historical system is source of truth for these properties
5. Removes confusion and ensures data consistency

**Next Steps:**
1. Confirm with user/business owner
2. Backup affected data
3. Execute deletion queries
4. Rebuild unified_transactions
5. Verify statements show only historical data
6. Document exclusion for future reference

---

## Supporting Data

### Database Evidence

**Properties in ICDN but not in master list:**
```sql
-- Properties with ICDN data
SELECT DISTINCT property_payprop_id, property_name
FROM payprop_report_icdn
WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate');

-- Result: 3 properties found (KAXNvEqAXk, KAXNvEqVXk, WzJBQ3ERZQ)

-- Check master property list
SELECT payprop_id, name
FROM payprop_export_properties
WHERE payprop_id IN ('KAXNvEqAXk', 'KAXNvEqVXk', 'WzJBQ3ERZQ');

-- Result: 0 rows (NOT FOUND)
```

**Financial transactions breakdown:**
```sql
SELECT
    property_name,
    data_source,
    COUNT(*) as count,
    SUM(amount) as total
FROM financial_transactions
WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
GROUP BY property_name, data_source;
```

**Results:**
- ICDN_ACTUAL: 21 transactions, £15,925.00
- COMMISSION_PAYMENT: 12 transactions, £1,365.00
- **Total: 33 transactions, £17,290.00**

---

## Conclusion

### The Answer

**Q: Why do financial transactions show commission for Flat 1-3 when they were never on PayProp?**

**A: The properties ARE partially in PayProp's export data (ICDN invoices) but NOT in the master property list.**

### What Happened

1. Properties have PayProp property IDs
2. PayProp ICDN export contains invoice/credit note transactions for these properties
3. Properties are NOT in PayProp's master property list (orphaned data)
4. System imported ICDN transactions and calculated commission (15%)
5. No incoming payments exist in PayProp (matches user's expectation)

### Why It's Confusing

- User states "never on PayProp" → True for incoming payments, False for invoices
- Properties partially integrated → Some PayProp data exists, but incomplete
- Data inconsistency → Properties in transaction export but not property list
- Commission appears → Automatically calculated from invoice amounts

### The Fix

**Remove PayProp data for these properties** since they're not in the master property list and user expects historical-only data.

---

**Investigation Complete**
**Status:** ✅ MYSTERY SOLVED
**Action Required:** Business decision on Option 1, 2, or 3

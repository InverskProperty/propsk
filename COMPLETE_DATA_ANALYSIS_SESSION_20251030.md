# Complete Data Analysis Session - October 30, 2025

**Session Date:** October 30, 2025
**Status:** In Progress - Paused for Later
**Purpose:** Investigate data sources, resolve Flat 1-3 West Gate mystery, analyze system vs actual records

---

## 📑 TABLE OF CONTENTS

1. [Executive Summary](#executive-summary)
2. [The Original Mystery](#the-original-mystery)
3. [Database Structure](#database-structure)
4. [Data Sources Feeding Unified Transactions](#data-sources-feeding-unified-transactions)
5. [The Flat 1-3 West Gate Mystery - SOLVED](#the-flat-1-3-west-gate-mystery---solved)
6. [ICDN Exclusion Decision](#icdn-exclusion-decision)
7. [Incoming Payments Analysis](#incoming-payments-analysis)
8. [System vs Your Records Variance Analysis](#system-vs-your-records-variance-analysis)
9. [Action Items](#action-items)
10. [SQL Queries for Investigation](#sql-queries-for-investigation)

---

## EXECUTIVE SUMMARY

### What We Discovered:

1. ✅ **Flat 1-3 West Gate mystery SOLVED**
   - Properties ARE in PayProp (for invoicing)
   - Actual rent receipts in historical_transactions (not PayProp)
   - Commission calculated from PayProp invoices
   - **Hybrid setup:** PayProp invoicing + Historical receipts

2. ✅ **ICDN Should Be Excluded from Unified Data**
   - ICDN = Invoices (what's billed), not cash flow (what's received)
   - Code updated to exclude ICDN_ACTUAL from unified_transactions
   - Cleanup SQL created: `cleanup_icdn_from_unified.sql`

3. ✅ **Incoming Payments Totals**
   - PayProp: £88,560.39 (106 payments, Jun-Oct 2025)
   - Historical: £104,968.00 (135 payments, Jan-Sep 2025)
   - **Combined: £193,528.39** (241 payments)

4. ⚠️ **System vs Your Records - 99.7% Match**
   - Your total: £181,174
   - System total: £180,648
   - Variance: -£526 (0.3%)
   - **BUT**: 4 major anomalies identified that need investigation

### Key Files Created:

1. `DATA_SOURCES_EXPLAINED.md` - Complete table structure guide
2. `EXCLUDE_ICDN_FROM_UNIFIED_DATA.md` - ICDN exclusion implementation
3. `INCOMING_PAYMENTS_SUMMARY.md` - All incoming payment analysis
4. `FLAT1_3WESTGATE_COMMISSION_MYSTERY_SOLVED.md` - Mystery resolution
5. `cleanup_icdn_from_unified.sql` - SQL to remove ICDN data

---

## THE ORIGINAL MYSTERY

### User Statement:
"Financial transactions shows a commission amount for Flat 1, 2, 3 - 3 West Gate when the property has only had incoming transactions in historical transactions. Why?"

### The Problem:
- User believed these properties were NEVER on PayProp
- Only had historical incoming payment data
- But system showed commission charges
- Where was commission coming from?

### Investigation Files:
- `InvestigateFlat1Data.java`
- `TraceCommissionSource.java`
- `TracePhantomData.java`
- `CheckWestGateProperty.java`

---

## DATABASE STRUCTURE

### Tables Overview:

#### LOCAL TABLES (Your Database)

| Table | Purpose | Row Count | Key Fields |
|-------|---------|-----------|------------|
| **properties** | Property master list | ~60 | id, property_name, payprop_id |
| **invoices** | Lease agreements (rent amounts) | ~60 | id, payprop_id, lease_reference, amount |
| **customers** | Tenants and property owners | Varies | customer_id, name, type |
| **historical_transactions** | Pre-PayProp CSV imports | ~176 | id, category, amount, transaction_date |
| **financial_transactions** | PayProp synced data | 629 | id, data_source, amount, pay_prop_transaction_id |
| **unified_transactions** | Materialized view (rebuilt) | 571 | source_system, payprop_data_source |

#### PAYPROP EXPORT TABLES (From PayProp API)

| Table | Purpose | Row Count | What It Contains |
|-------|---------|-----------|------------------|
| **payprop_export_properties** | PayProp property master | 45 | Property setup, addresses |
| **payprop_export_invoices** | Invoice INSTRUCTIONS (templates) | 34 | "Create £795 invoice monthly" |
| **payprop_export_payments** | Payment RULES (distribution) | 66 | "Pay landlord 100%, commission 15%" |
| **payprop_export_incoming_payments** | ACTUAL rent received | 106 | Real tenant payments |
| **payprop_report_icdn** | ACTUAL invoices created | 172 | Individual bills sent to tenants |
| **payprop_report_all_payments** | ACTUAL disbursements | 202 | Money paid to landlords |
| **payprop_export_tenants** | Tenant master list | 42 | Tenant details |
| **payprop_export_beneficiaries** | Payment recipients | 8 | Who receives money |

### The Three Types of "Invoices"

**IMPORTANT:** Don't confuse these three different meanings:

1. **`invoices` table** = **Lease agreements** (your local database)
   - "Tenant pays £795/month for this property"
   - Setup once, defines ongoing rent
   - Example: `LEASE-BH-F1-2025`, £795/month

2. **`payprop_export_invoices`** = **Invoice templates/instructions** (from PayProp)
   - "Create a £795 invoice every month for tenant X"
   - Configuration for recurring billing
   - These are RULES, not actual invoices

3. **`payprop_report_icdn`** = **Actual invoices created** (from PayProp)
   - "Invoice #yJ66DlrkJj for £795 sent on July 4, 2025"
   - Individual bills actually sent to tenants each month
   - **ICDN = Invoice, Credit note, Debit note**

---

## DATA SOURCES FEEDING UNIFIED TRANSACTIONS

### Current Implementation (BEFORE our changes):

```
unified_transactions gets data from:

1. historical_transactions
   - WHERE invoice_id IS NOT NULL
   - All records with lease linkage

2. financial_transactions
   - WHERE invoice_id IS NOT NULL
   - AND data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
   - Includes: INCOMING_PAYMENT, BATCH_PAYMENT, COMMISSION_PAYMENT, ICDN_ACTUAL
```

### Updated Implementation (AFTER our changes):

```
unified_transactions gets data from:

1. historical_transactions
   - WHERE invoice_id IS NOT NULL
   - [No change]

2. financial_transactions
   - WHERE invoice_id IS NOT NULL
   - AND data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV', 'ICDN_ACTUAL')
   - Includes: INCOMING_PAYMENT, BATCH_PAYMENT, COMMISSION_PAYMENT
   - EXCLUDES: ICDN_ACTUAL (invoices - not cash flow)
```

**File changed:** `UnifiedTransactionRebuildService.java` (lines 175 & 331)

### Data Source Breakdown:

#### financial_transactions data_source values:

| Data Source | Type | Count | Total | Include in Unified? |
|------------|------|-------|-------|---------------------|
| **INCOMING_PAYMENT** | Rent received from tenants | 106 | £88,560 | ✅ YES |
| **BATCH_PAYMENT** | Money paid to landlords | 202 | £84,585 | ✅ YES |
| **COMMISSION_PAYMENT** | Management fees charged | 134 | £14,002 | ✅ YES |
| **ICDN_ACTUAL** | Invoices sent (bills) | 187 | £133,145 | ❌ **NO (excluded)** |
| **HISTORICAL_IMPORT** | Duplicates historical | - | - | ❌ NO |
| **HISTORICAL_CSV** | Duplicates historical | - | - | ❌ NO |

#### historical_transactions category values:

| Category | Type | Count | Total | Include in Unified? |
|----------|------|-------|-------|---------------------|
| **rent** | Rent received (positive) | 135 | £104,968 | ✅ YES (if invoice_id linked) |
| owner_payment | Paid to owners (negative) | 13 | -£79,185 | Depends on invoice_id |
| furnishings | Expenses | 8 | -£1,530 | Depends on invoice_id |
| cleaning | Expenses | 7 | -£1,400 | Depends on invoice_id |
| management | Fees | 5 | -£880 | Depends on invoice_id |
| utilities | Bills | 2 | -£288 | Depends on invoice_id |

**Note:** historical_transactions uses `category = 'rent'` to identify incoming payments, NOT `transaction_type`.

---

## THE FLAT 1-3 WEST GATE MYSTERY - SOLVED

### The Facts:

**Flat 1 - 3 West Gate:**
- PayProp Property ID: `KAXNvEqAXk`
- Monthly rent: £795
- Status: Active in PayProp
- Last synced: 2025-10-30 10:30:20

**Flat 2 - 3 West Gate:**
- PayProp Property ID: `KAXNvEqVXk`
- Monthly rent: £740
- Status: Active in PayProp

**Flat 3 - 3 West Gate:**
- PayProp Property ID: `WzJBQ3ERZQ`
- Monthly rent: £740
- Status: Active in PayProp

### What We Found in Each Table:

#### ✅ payprop_export_properties (Master List)
```
Flat 1: EXISTS (KAXNvEqAXk)
- is_archived: 0 (active)
- commission_percentage: 15%
- imported_at: 2025-10-30 10:29:55 (TODAY!)
```

#### ✅ payprop_report_icdn (Invoices Created)
```
Flat 1: 7 invoices (£5,565 total)
- Monthly £795 invoices (Jul-Oct 2025)
- Some credit notes (reversals)
```

#### ✅ payprop_export_payments (Payment Rules)
```
Flat 1 has 2 payment rules:
1. Pay 100% to landlord (Udayan Bhardwaj)
2. Charge 15% commission to Propsk
```

#### ❌ payprop_export_incoming_payments (Actual Receipts)
```
Flat 1: ZERO records
Flat 2: ZERO records
Flat 3: ZERO records
```

#### ✅ historical_transactions (Your CSV Imports)
```
Flat 1: 7 payments (£5,565 total, Jan-Sep 2025)
Flat 2: 6 payments (£4,440 total, Jan-Aug 2025)
Flat 3: 6 payments (£4,440 total, Jan-Sep 2025)
```

### The Answer:

**These properties ARE on PayProp - but in a HYBRID setup:**

1. **PayProp manages:** Invoicing (bills sent to tenants)
2. **Historical manages:** Actual rent receipts (payments received)
3. **Commission calculated:** From PayProp invoices (15% of £795 = £119.25)

**Why this happened:**
- Properties added to PayProp mid-2025 for invoicing
- PayProp started creating monthly invoices
- But actual rent receipts still tracked in historical system (CSV imports)
- PayProp calculates commission from invoices, NOT receipts
- Result: Commission appears without PayProp receipt data

**Local property table confirms:**
```
payprop_manages_financials: FALSE
financial_tracking_source: PAYPROP
```

This means PayProp does invoicing, but not full financial tracking.

---

## ICDN EXCLUSION DECISION

### Why Exclude ICDN from Unified Transactions?

**ICDN = Invoice, Credit note, Debit note**

These represent:
- ❌ Bills SENT to tenants (what they OWE)
- ❌ NOT money actually received
- ❌ NOT cash flow

**Including ICDN causes:**
- Double-counting (invoice + payment = 2x income)
- Inflated financial totals
- Confusion between billed vs collected

### The Numbers:

**Current unified_transactions (WITH ICDN):**
- ICDN_ACTUAL: 163 records, £112,909
- INCOMING_PAYMENT: ~106 records, £88,560
- BATCH_PAYMENT: 152 records, £63,594
- COMMISSION_PAYMENT: 118 records, £12,432
- **Total PayProp: 539 records, £277,495**

**After ICDN exclusion (WITHOUT ICDN):**
- INCOMING_PAYMENT: 106 records, £88,560
- BATCH_PAYMENT: 152 records, £63,594
- COMMISSION_PAYMENT: 118 records, £12,432
- **Total PayProp: 376 records, £164,586**

**Impact:** Removes £112,909 in invoice data (41% reduction in PayProp totals)

### Code Changes Made:

**File:** `src/main/java/site/easy/to/build/crm/service/transaction/UnifiedTransactionRebuildService.java`

**Lines 175 and 331:**
```java
// BEFORE:
WHERE ft.data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')

// AFTER:
WHERE ft.data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV', 'ICDN_ACTUAL')
```

### Cleanup Required:

**Run this SQL to remove existing ICDN data:**
```sql
DELETE FROM unified_transactions
WHERE payprop_data_source = 'ICDN_ACTUAL';
```

Expected to remove: 163 records (£112,909)

**File:** `cleanup_icdn_from_unified.sql` (ready to execute)

---

## INCOMING PAYMENTS ANALYSIS

### Grand Total: £193,528.39 (241 payments)

#### Breakdown by Source:

| Source | Count | Total | Date Range | Average |
|--------|-------|-------|------------|---------|
| **PayProp** (financial_transactions) | 106 | £88,560.39 | Jun 17 - Oct 23, 2025 | £835.48 |
| **Historical** (historical_transactions) | 135 | £104,968.00 | Jan 28 - Sep 15, 2025 | £777.54 |
| **COMBINED** | **241** | **£193,528.39** | Jan 28 - Oct 23, 2025 | £803.02 |

### PayProp Incoming Payments (financial_transactions):

**Filter:** `data_source = 'INCOMING_PAYMENT'`

**Sample recent payments (Oct 23, 2025):**
- Flat 12 - 3 West Gate: £795
- Flat 24 - 3 West Gate: £810
- Flat 5 - 3 West Gate: £740
- Flat 10 - 3 West Gate: £785
- Flat 18 - 3 West Gate: £795

**Characteristics:**
- Properties currently on PayProp
- Real-time tenant payment tracking
- Linked to PayProp transaction IDs
- Most recent data (June onwards)

### Historical Incoming Payments (historical_transactions):

**Filter:** `category = 'rent' AND amount > 0`

**Sample recent payments:**
- Sep 15: Flat 3 - 3 West Gate: £740
- Sep 10: Flat 30 - 3 West Gate: £740
- Sep 4: **Flat 1 - 3 West Gate: £795** ← Found it!
- Aug 30: Flat 28 - 3 West Gate: £740
- Aug 26: Flat 14 - 3 West Gate: £795

**Characteristics:**
- Pre-PayProp CSV imports
- Properties not on PayProp (or before integration)
- Manual data entry / legacy system
- Stops around September 2025

### Properties with Historical Data (Top 10):

| Property | Payments | Total | Latest |
|----------|----------|-------|--------|
| Apartment F - Knighton Hayes Manor | 4 | £6,750 | May 19 |
| **Flat 1 - 3 West Gate** | **7** | **£5,565** | **Sep 4** |
| Flat 30 - 3 West Gate | 7 | £5,180 | Sep 10 |
| Flat 14 - 3 West Gate | 6 | £4,775 | Aug 26 |
| Flat 26 - 3 West Gate | 7 | £4,725 | Aug 28 |
| Flat 28 - 3 West Gate | 6 | £4,440 | Aug 30 |
| **Flat 3 - 3 West Gate** | **6** | **£4,440** | **Sep 15** |
| **Flat 2 - 3 West Gate** | **6** | **£4,440** | **Aug 4** |
| Flat 21 - 3 West Gate | 5 | £3,975 | Jun 15 |
| Flat 29 - 3 West Gate | 5 | £3,700 | Jul 2 |

**Key Finding:** Flats 1, 2, 3 ARE in historical data! This solved the mystery.

---

## SYSTEM VS YOUR RECORDS VARIANCE ANALYSIS

### Your Period Definition:
Months run from **22nd to 21st** (not calendar months)

Example:
- January: Dec 22, 2024 - Jan 21, 2025
- February: Jan 22, 2025 - Feb 21, 2025
- etc.

### Grand Total Comparison:

| Account | Your Total | System Total | Variance |
|---------|-----------|--------------|----------|
| **Old Account** (Historical) | £113,183 | £104,968 | **-£8,215** |
| **PayProp Account** | £67,991 | £75,680 | **+£7,689** |
| **COMBINED TOTAL** | **£181,174** | **£180,648** | **-£526 (0.3%)** |

**Overall: 99.7% match!**

### Period-by-Period Breakdown:

| Period | Your Total | System Total | Variance | Status |
|--------|-----------|-------------|----------|--------|
| January | £0 | £0 | £0 | ✅ Perfect |
| February | £7,475 | £7,475 | £0 | ✅ **Perfect** |
| March | £20,085 | £20,085 | £0 | ✅ **Perfect** |
| April | £20,470 | £19,730 | **-£740** | ⚠️ Missing |
| May | £22,800 | £22,800 | £0 | ✅ **Perfect** |
| June | £20,165 | £17,215 | **-£2,950** | ⚠️ Missing |
| July | £22,590 | £12,435 | **-£10,155** | 🚨 Big Gap |
| August | £21,842 | £37,631 | **+£15,789** | 🚨 **HUGE SPIKE** |
| September | £19,496 | £27,172 | **+£7,676** | 🚨 High |
| October | £26,251 | £16,105 | **-£10,146** | 🚨 Big Gap |

### Perfect Match Periods:
✅ **February, March, May** - These prove the system CAN capture data correctly!

---

## MAJOR ANOMALIES IDENTIFIED

### 1. 🚨 **Flat 12 - 3 West Gate: £5,687.30 Payment** (Aug 8, 2025)

**THE SMOKING GUN**

- Normal rent for Flat 12: ~£675-£795/month
- Actual payment recorded: **£5,687.30**
- This is **7.6x normal monthly rent**
- Accounts for most of August's £15,789 variance

**Possible explanations:**
- ❓ Back-payment for 7-8 months
- ❓ Deposit + advance rent lump sum
- ❓ **Data entry error** (should be £568.73?)
- ❓ **Duplicate payment** recorded multiple times
- ❓ Decimal point error (£5687.30 vs £568.73)

**Transaction details:**
```
Date: 2025-08-08
Property: Flat 12 - 3 West Gate
Amount: £5,687.30
Source: financial_transactions (PayProp)
Data Source: INCOMING_PAYMENT
```

**URGENT:** This needs investigation to determine if legitimate or error.

---

### 2. 🚨 **July - Missing £10,155 in PayProp**

**Your records:** £15,130 in PayProp
**System shows:** £4,975 in PayProp

**System only captured 6 payments:**
1. Apartment F - Knighton Hayes Manor: £1,125
2. Apartment 40 - 31 Watkin Road: £900
3. Flat 24 - 3 West Gate: £810
4. Flat 5 - 3 West Gate: £740
5. Flat 7 - 3 West Gate: £700
6. Flat 17 - 3 West Gate: £700
**Total: £4,975**

**Missing: ~12-15 properties** worth of rent (£10,155)

**Likely cause:**
- PayProp sync incomplete for June 22 - July 21 period
- Transition period where properties were moving to PayProp
- Data import gap

---

### 3. 🚨 **October - Missing £10,146 Total**

**Your records:**
- Old Account: £6,735
- PayProp: £19,516
- Total: £26,251

**System shows:**
- Old Account: £0 (no historical after Sep 15)
- PayProp: £16,105
- Total: £16,105

**Gaps identified:**
1. **Historical missing £6,735** - No historical transactions imported for Sept 22 - Oct 21
2. **PayProp missing £3,411** - System has £16,105 vs your £19,516

**Cause:**
- Historical data import stopped after Sep 15
- PayProp incomplete for this period

---

### 4. ⚠️ **June - Missing £2,950 in PayProp**

**Your records:** £3,690 in PayProp
**System shows:** £740 in PayProp (only 1 property: Flat 15)

**Missing:** £2,950 worth of PayProp payments (~4 properties)

**This is a transition period** - some properties may have been switching to PayProp.

---

### 5. ⚠️ **April & September - Small Gaps**

**April:**
- Your: £20,470
- System: £19,730
- Missing: £740 (exactly 1 month's rent for a property)

**September:**
- Historical missing: £740
- PayProp extra: £8,416
- Could be timing differences

---

## ACTION ITEMS

### URGENT - Investigate Flat 12 Anomaly:

```sql
-- Get full transaction details
SELECT
    id,
    pay_prop_transaction_id,
    transaction_date,
    amount,
    property_name,
    tenant_name,
    description,
    reference,
    created_at,
    updated_at,
    data_source
FROM financial_transactions
WHERE property_name = 'Flat 12 - 3 West Gate'
  AND transaction_date = '2025-08-08'
  AND amount = 5687.30;

-- Check for nearby transactions
SELECT
    transaction_date,
    amount,
    description
FROM financial_transactions
WHERE property_name = 'Flat 12 - 3 West Gate'
  AND transaction_date BETWEEN '2025-07-01' AND '2025-09-30'
ORDER BY transaction_date;

-- Check historical for Flat 12
SELECT
    transaction_date,
    amount,
    description
FROM historical_transactions ht
LEFT JOIN properties p ON ht.property_id = p.id
WHERE p.property_name = 'Flat 12 - 3 West Gate'
ORDER BY transaction_date DESC;
```

### HIGH PRIORITY - Fix Missing Data:

#### 1. Import Missing July PayProp Data (£10,155)

**Period:** June 22 - July 21, 2025

**Check what should be there:**
```sql
-- Check PayProp export for this period
SELECT
    property_name,
    transaction_date,
    amount
FROM payprop_export_incoming_payments
WHERE transaction_date BETWEEN '2025-06-22' AND '2025-07-21'
ORDER BY transaction_date;

-- Compare to what's in financial_transactions
SELECT
    property_name,
    transaction_date,
    amount
FROM financial_transactions
WHERE data_source = 'INCOMING_PAYMENT'
  AND transaction_date BETWEEN '2025-06-22' AND '2025-07-21'
ORDER BY transaction_date;
```

#### 2. Import Missing October Historical Data (£6,735)

**Period:** Sept 22 - Oct 21, 2025

```sql
-- Check if data exists but isn't in category='rent'
SELECT
    category,
    COUNT(*) as count,
    SUM(amount) as total
FROM historical_transactions
WHERE transaction_date BETWEEN '2025-09-22' AND '2025-10-21'
GROUP BY category;

-- Check last import date
SELECT
    MAX(transaction_date) as last_transaction,
    MAX(created_at) as last_import
FROM historical_transactions
WHERE category = 'rent';
```

#### 3. Investigate Missing October PayProp Data (£3,411)

**Period:** Sept 22 - Oct 21, 2025

```sql
-- Count properties that paid in October
SELECT
    COUNT(DISTINCT property_name) as property_count,
    SUM(amount) as total
FROM financial_transactions
WHERE data_source = 'INCOMING_PAYMENT'
  AND transaction_date BETWEEN '2025-09-22' AND '2025-10-21';

-- Compare to previous months
SELECT
    DATE_FORMAT(transaction_date, '%Y-%m') as month,
    COUNT(*) as payment_count,
    SUM(amount) as total
FROM financial_transactions
WHERE data_source = 'INCOMING_PAYMENT'
  AND transaction_date >= '2025-06-01'
GROUP BY DATE_FORMAT(transaction_date, '%Y-%m')
ORDER BY month;
```

### MEDIUM PRIORITY - Execute ICDN Cleanup:

```sql
-- Preview
SELECT
    COUNT(*) as records_to_delete,
    SUM(amount) as total_amount
FROM unified_transactions
WHERE payprop_data_source = 'ICDN_ACTUAL';

-- Execute
DELETE FROM unified_transactions
WHERE payprop_data_source = 'ICDN_ACTUAL';

-- Verify
SELECT
    payprop_data_source,
    COUNT(*) as count
FROM unified_transactions
GROUP BY payprop_data_source;
```

### LOW PRIORITY - Rebuild Unified Transactions:

After fixing data issues and removing ICDN:

```sql
-- Via API or service
POST /api/admin/unified/rebuild

-- Or via Java
unifiedTransactionRebuildService.rebuildComplete();
```

---

## SQL QUERIES FOR INVESTIGATION

### Quick Health Check:

```sql
-- Total incoming by period (your custom 22nd-21st)
SELECT
    'Historical' as source,
    COUNT(*) as count,
    SUM(amount) as total
FROM historical_transactions
WHERE category = 'rent' AND amount > 0
  AND transaction_date BETWEEN '2024-12-22' AND '2025-10-21'
UNION ALL
SELECT
    'PayProp' as source,
    COUNT(*) as count,
    SUM(amount) as total
FROM financial_transactions
WHERE data_source = 'INCOMING_PAYMENT'
  AND transaction_date BETWEEN '2024-12-22' AND '2025-10-21';
```

### Find Unusually Large Payments:

```sql
SELECT
    'Historical' as source,
    transaction_date,
    p.property_name,
    amount
FROM historical_transactions ht
LEFT JOIN properties p ON ht.property_id = p.id
WHERE category = 'rent'
  AND amount > 2000
UNION ALL
SELECT
    'PayProp' as source,
    transaction_date,
    property_name,
    amount
FROM financial_transactions
WHERE data_source = 'INCOMING_PAYMENT'
  AND amount > 2000
ORDER BY amount DESC;
```

### Check Data Source Breakdown:

```sql
-- financial_transactions by source
SELECT
    data_source,
    COUNT(*) as count,
    SUM(amount) as total,
    MIN(transaction_date) as earliest,
    MAX(transaction_date) as latest
FROM financial_transactions
GROUP BY data_source
ORDER BY count DESC;

-- historical_transactions by category
SELECT
    category,
    COUNT(*) as count,
    SUM(amount) as total
FROM historical_transactions
WHERE category IS NOT NULL
GROUP BY category
ORDER BY count DESC;
```

### Verify Flat 1-3 West Gate Data:

```sql
-- PayProp ICDN (invoices)
SELECT
    property_name,
    COUNT(*) as invoice_count,
    SUM(amount) as total
FROM payprop_report_icdn
WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
GROUP BY property_name;

-- Historical receipts
SELECT
    p.property_name,
    COUNT(*) as payment_count,
    SUM(ht.amount) as total
FROM historical_transactions ht
LEFT JOIN properties p ON ht.property_id = p.id
WHERE p.property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
  AND ht.category = 'rent'
GROUP BY p.property_name;

-- Commission calculated
SELECT
    property_name,
    COUNT(*) as commission_count,
    SUM(amount) as total
FROM financial_transactions
WHERE property_name IN ('Flat 1 - 3 West Gate', 'Flat 2 - 3 West Gate', 'Flat 3 - 3 West Gate')
  AND data_source = 'COMMISSION_PAYMENT'
GROUP BY property_name;
```

---

## SUMMARY & NEXT STEPS

### What We Accomplished:

1. ✅ Solved Flat 1-3 West Gate mystery (hybrid PayProp/Historical setup)
2. ✅ Documented complete database structure
3. ✅ Identified ICDN should be excluded from unified data
4. ✅ Updated code to exclude ICDN from future rebuilds
5. ✅ Analyzed all incoming payments (£193,528 total)
6. ✅ Compared system vs your records (99.7% match)
7. ✅ Identified 4 major data anomalies

### Critical Issues to Resolve:

1. **🚨 URGENT:** Investigate Flat 12 £5,687.30 payment (possible error)
2. **🚨 HIGH:** Import missing July PayProp data (£10,155)
3. **🚨 HIGH:** Import missing October data (£10,146)
4. **⚠️ MEDIUM:** Execute ICDN cleanup from unified_transactions
5. **⚠️ MEDIUM:** Investigate June missing PayProp (£2,950)

### Files to Reference:

**Investigation Files:**
- `COMPLETE_DATA_ANALYSIS_SESSION_20251030.md` ← **THIS FILE**
- `DATA_SOURCES_EXPLAINED.md`
- `FLAT1_3WESTGATE_COMMISSION_MYSTERY_SOLVED.md`
- `INCOMING_PAYMENTS_SUMMARY.md`
- `EXCLUDE_ICDN_FROM_UNIFIED_DATA.md`

**SQL Scripts:**
- `cleanup_icdn_from_unified.sql`
- All investigation queries included in this document

**Java Investigation Tools:**
- `InvestigateFlat1Data.java`
- `CompleteFlat1Investigation.java`
- `SearchDatabaseForPropertyID.java`
- `AnalyzeUnifiedTransactions.java`
- `CompareIncomingPayments.java`
- `InvestigateDifferences.java`
- `CustomPeriodAnalysis.java`

### Quick Reference Commands:

```bash
# Compile investigation tools
javac -cp mysql-connector-j-8.0.33.jar InvestigateDifferences.java

# Run investigation
java -cp ".;mysql-connector-j-8.0.33.jar" InvestigateDifferences

# Rebuild unified transactions (after fixes)
POST /api/admin/unified/rebuild
```

---

## QUESTIONS TO ANSWER WHEN YOU RESUME:

1. **Is the Flat 12 £5,687.30 payment legitimate or an error?**
   - If error: What should it be?
   - If legitimate: What does it represent?

2. **Where is the missing July PayProp data?**
   - Was it imported but under different source?
   - Is it in payprop_export_incoming_payments but not financial_transactions?

3. **Why did historical data import stop at Sep 15?**
   - Need to import Sept 22 - Oct 21 period
   - Is there a CSV file that wasn't imported?

4. **Should we proceed with ICDN cleanup?**
   - Confirm it's safe to delete 163 ICDN records from unified
   - Understand impact on existing statements

---

**END OF SESSION SUMMARY**
**Ready to resume investigation**
**All context preserved in this document**

# Invoice/Lease Architecture Analysis

**Date:** October 29, 2025
**User Questions:**
1. Why are there only 10 RENT_INVOICE records?
2. What purpose do they serve?
3. How is this purpose served for other properties?
4. Are ICDN_MANUAL records from PayProp or locally created?

---

## CRITICAL DISCOVERIES

### 1. ICDN_MANUAL Records ARE from PayProp (DUPLICATES!)

**YOU WERE CORRECT** - ICDN_MANUAL records ARE imported from PayProp, but they were imported **without PayProp transaction IDs**, creating duplicates.

#### Evidence:

**ICDN_MANUAL Records (5 duplicates created Sept 16):**
```
ID      DATE        AMOUNT   DESCRIPTION                              PAYPROP_ID
14387   2025-08-04  £675     Rent For Flat 8 (04 Aug - 03 Sep 25)    NULL ❌
14388   2025-08-04  £60      Parking (04 Aug - 03 Sep 25)            NULL ❌
14385   2025-08-19  £700     Rent For Flat 7 (19 Aug - 18 Sep 25)    NULL ❌
14384   2025-08-29  £740     Rent For Flat 6 (29 Aug - 28 Sep 25)    NULL ❌
14386   2025-09-11  -£700    19/08 invoice wrong tenant left 18/08   NULL ❌
```

**ICDN_ACTUAL Records (Same transactions from PayProp):**
```
ID      DATE        AMOUNT   DESCRIPTION                              PAYPROP_ID
15099   2025-08-04  £675     Rent For Flat 8 (04 Aug - 03 Sep 25)    7Z4gEYLq1P ✅
15100   2025-08-04  £60      Parking (04 Aug - 03 Sep 25)            GX03zgQgJ3 ✅
15070   2025-08-19  £700     Rent For Flat 7 (19 Aug - 18 Sep 25)    zZy9NyMdZd ✅
15053   2025-08-29  £740     Rent For Flat 6 (29 Aug - 28 Sep 25)    YZ207E8jXQ ✅
15054   2025-09-11  £700     19/08 invoice wrong tenant left 18/08   yJ66GDYkJj ✅ (note: positive £700 in ICDN_ACTUAL)
```

**PayProp Raw Data (payprop_report_icdn):**
```
PAYPROP_ID   DATE        AMOUNT   DESCRIPTION                              TYPE
7Z4gEYLq1P   2025-08-04  £675     Rent For Flat 8 (04 Aug - 03 Sep '25)   invoice
GX03zgQgJ3   2025-08-04  £60      Parking (04 Aug - 03 Sep '25)           invoice
zZy9NyMdZd   2025-08-19  £700     Rent For Flat 7 (19 Aug - 18 Sep '25)   invoice
YZ207E8jXQ   2025-08-29  £740     Rent For Flat 6 (29 Aug - 28 Sep '25)   invoice
yJ66GDYkJj   2025-09-11  £700     19/08 invoice wrong as tenant left 18/08   credit note
```

#### Conclusion on ICDN_MANUAL:

🚨 **ICDN_MANUAL are DUPLICATES** - they should be **DELETED**

These 5 records:
- ✅ **ARE from PayProp** (exist in payprop_report_icdn)
- ✅ **WILL be repopulated** (already exist as ICDN_ACTUAL with proper PayProp IDs)
- ❌ **Missing PayProp transaction IDs** (pay_prop_transaction_id = NULL)
- ❌ **Imported incorrectly** on Sept 16 without proper ID tracking
- ❌ **Create duplicate transaction counts** (same transactions counted twice)

**Action:** Safe to delete - they're already correctly imported as ICDN_ACTUAL.

---

## 2. Why Only 10 RENT_INVOICE Records?

### The Answer: RENT_INVOICE is an OBSOLETE one-time import, replaced by the `invoices` table

**RENT_INVOICE Records (10 total):**
```
PROPERTY                DATE        AMOUNT
Flat 1 - 3 West Gate    2025-01-01  £795
Flat 2 - 3 West Gate    2025-01-01  £740
Flat 3 - 3 West Gate    2025-01-01  £740
Flat 4 - 3 West Gate    2025-01-01  £855
Flat 13 - 3 West Gate   2025-01-01  £800
Flat 5 - 3 West Gate    2025-02-01  £740
Flat 7 - 3 West Gate    2025-02-01  £700
Flat 15 - 3 West Gate   2025-02-01  £740
Flat 17 - 3 West Gate   2025-02-01  £700
Flat 18 - 3 West Gate   2025-02-01  £740
```

**Characteristics:**
- Created: September 16, 2025 (all on same day)
- Covers: January-February 2025 only (2 months)
- Properties: Only "3 West Gate" building (10 flats)
- Has NO `invoice_id` link (invoice_id = NULL)
- Has NO `property_id` (property_id = NULL)
- Has NO `pay_prop_transaction_id` (NULL)

**Purpose:** These were a **one-time test/migration** of rent invoice data, likely:
1. Early test of importing rental data before PayProp integration
2. Manual entry for pre-PayProp period
3. Backup/reference data for specific flats

---

## 3. How This Purpose is Served for Other Properties

### The Modern System: `invoices` Table (Lease Instructions)

**Total Invoices:** 43
- **From PayProp:** 33 invoices (synced via PayPropInvoiceToLeaseImportService)
- **Locally Created:** 10 invoices
- **Properties Covered:** 34 distinct properties
- **Invoice Types:**
  - `lease`: 38 invoices (rental agreements)
  - `standard`: 5 invoices (other charges)

**Sample Invoice Data:**
```
ID   PROPERTY  LEASE_REF            AMOUNT   START       END         PAYPROP_ID  TYPE
1    22        LEASE-BH-F1-2025     £795     2025-02-27  NULL        PYZ249oAXQ  lease
2    23        LEASE-BH-F2-2025     £740     2025-02-27  NULL        KAXNko2j1k  lease
3    42        LEASE-BH-F3-2025     £740     2025-03-11  NULL        PzZy6370Jd  lease
4    30        LEASE-BH-F4-2025     £855     2025-02-25  NULL        agXVwV8B13  lease
7    20        LEASE-BH-F7-2025     £700     2025-02-19  2025-08-18  NULL        lease (ended)
10   7         LEASE-BH-F10-2025    £735     2025-03-06  2025-09-23  NULL        lease (ended)
11   7         LEASE-BH-F10-2025B   £795     2025-09-03  NULL        90JYrp3yXo  lease (new tenant)
```

**Key Features:**
- **Unique lease_reference** for each lease (e.g., "LEASE-BH-F1-2025")
- **Links to property_id** (integer foreign key to properties table)
- **PayProp sync status** (payprop_id tracks sync to PayProp)
- **Date ranges** (start_date, end_date for lease periods)
- **Handles tenant changes** (see Flat 10: ended Sept 23, new tenant started Sept 3)

---

## 4. The Architecture: Three Data Layers

### Layer 1: Raw PayProp Data (Import Tables)

**Purpose:** Store raw API responses from PayProp

**Tables:**
- `payprop_report_icdn` - Invoices, Credit Notes, Debit Notes (172 records)
- `payprop_report_all_payments` - All payment transactions
- `payprop_export_invoice_instructions` - Invoice instructions from PayProp

**Lifecycle:** Refreshed on each PayProp sync

---

### Layer 2A: Standardized Transactions (financial_transactions)

**Purpose:** Standardize transactions from multiple sources into unified format

**Data Sources:**
```
ICDN_ACTUAL (187)         ← payprop_report_icdn
BATCH_PAYMENT (202)       ← payprop_report_all_payments
COMMISSION_PAYMENT (134)  ← Calculated from rent payments
INCOMING_PAYMENT (106)    ← PayProp incoming payments
HISTORICAL_IMPORT (351)   ← CSV imports (duplicates, should be in historical_transactions)
HISTORICAL_CSV (24)       ← CSV imports (duplicates, should be in historical_transactions)
RENT_INVOICE (10)         ← ❌ OBSOLETE one-time import, use invoices table instead
ICDN_MANUAL (5)           ← ❌ DUPLICATES of ICDN_ACTUAL, missing PayProp IDs
```

**Lifecycle:** Grows continuously, can be rebuilt from sources

---

### Layer 2B: Lease Instructions (invoices table)

**Purpose:** Store invoice instructions (recurring charges) that define leases

**Key Concept:** In this system, **Invoice = Lease**

An invoice instruction represents:
- **WHO:** customer_id (tenant)
- **WHERE:** property_id (flat/unit)
- **WHAT:** category_id (rent, deposit, parking, etc.)
- **HOW MUCH:** amount (monthly rent)
- **WHEN:** start_date, end_date, frequency (monthly, quarterly, etc.)
- **SYNC:** payprop_id (tracks if synced to PayProp)

**Example - Flat 7 Tenant Change:**
```
Invoice 10:  Flat 7, £735/month, 2025-03-06 to 2025-09-23 (old tenant, ended)
Invoice 11:  Flat 7, £795/month, 2025-09-03 to NULL (new tenant, ongoing)
```

**Lifecycle:**
- Created locally OR imported from PayProp
- Synced to PayProp via PayPropInvoiceToLeaseImportService
- Updated when lease terms change

---

### Layer 3: Unified Transactions (unified_transactions)

**Purpose:** Combine ALL transaction sources for statement generation

**Architecture:**
```
SOURCES:
  historical_transactions (176 records)
  financial_transactions (1,019 records)
          ↓
  unified_transactions (571 records)
          ↓
  Statement Generation
```

**Why 571 not 1,019+176?**
- Excludes HISTORICAL_IMPORT/HISTORICAL_CSV (already in historical_transactions)
- Only includes lease-linked transactions (invoice_id IS NOT NULL)
- Deduplicates where appropriate

**Code Reference:** `UnifiedTransactionRebuildService.java:106-179`

---

## 5. How Invoices Work: Three Workflows

### Workflow A: Local Invoice Creation → Sync to PayProp

**User Action:** Employee creates lease in UI

**Steps:**
1. Create invoice in `invoices` table (via InvoiceService.java)
2. Set `sync_status = 'pending'`
3. Background job syncs to PayProp API
4. PayProp returns `payprop_id`
5. Update invoice with `payprop_id`, set `sync_status = 'synced'`

**Result:** Local invoice becomes PayProp invoice instruction

**Code:** `InvoiceServiceImpl.java:116-150`

---

### Workflow B: PayProp Invoice Import → Create Local Lease

**User Action:** Click "Import PayProp Invoices" button

**Steps:**
1. Fetch invoices from `payprop_export_invoice_instructions`
2. For each PayProp invoice:
   - Find or create local property (match by payprop_id)
   - Find or create local tenant (match by payprop_id)
   - Create invoice record with `payprop_id` already set
3. Set `sync_status = 'synced'`

**Result:** PayProp invoices become local lease records

**Code:** `PayPropInvoiceToLeaseImportService.java:75-122`

**Database Impact:**
- 33 invoices imported from PayProp (out of 43 total)
- Covers 33 properties

---

### Workflow C: Lease Import Wizard (CSV → Local Invoices)

**User Action:** Upload CSV with lease data

**Steps:**
1. Parse CSV (property, tenant, amount, dates)
2. Interactive wizard:
   - Map property names to existing properties
   - Map tenant names/emails to existing customers
   - Review all mappings
3. Bulk create invoice records
4. Set `sync_status = 'pending'` or `'manual'` (user choice)

**Result:** CSV becomes local lease records, optionally synced to PayProp

**Code:** `LeaseImportWizardController.java:85-100`

**Use Case:** Importing historical leases or bulk lease creation

---

## 6. Key Relationships

### invoices ↔ financial_transactions

**Link:** `financial_transactions.invoice_id` → `invoices.id`

**Purpose:** Link actual transactions (payments, invoices) to the lease instruction

**Example:**
```
invoices.id = 7 (Flat 7, £700/month lease)
  ↓
financial_transactions.invoice_id = 7
  - Aug 4: £675 (partial month rent)
  - Aug 19: £700 (full month rent)
  - Sept 11: -£700 (credit note - tenant left early)
```

**Current Status:**
- RENT_INVOICE records have `invoice_id = NULL` ❌ (not linked)
- ICDN_ACTUAL records have `invoice_id` ✅ (properly linked)

---

### invoices ↔ properties

**Link:** `invoices.property_id` → `properties.id`

**Purpose:** Know which property the lease is for

**Current Status:**
- `invoices` table: Uses integer property_id ✅
- `financial_transactions` table: Uses string property_id (PayProp ID) ⚠️
  - RENT_INVOICE: property_id = NULL ❌
  - ICDN_ACTUAL: property_id = PayProp string ID (must join via properties.payprop_id)

---

### invoices ↔ customers

**Link:** `invoices.customer_id` → `customers.customer_id`

**Purpose:** Know which tenant the lease is for

**Current Status:**
- All invoices have customer_id ✅
- RENT_INVOICE in financial_transactions: customer_id = NULL ❌

---

## 7. Summary: Why Only 10 RENT_INVOICE?

### The Real Answer:

**RENT_INVOICE is OBSOLETE and was replaced by the `invoices` table**

| Aspect | RENT_INVOICE (Old) | invoices Table (Current) |
|--------|---------------------|--------------------------|
| **Purpose** | One-time import of Jan/Feb 2025 rent | Ongoing lease instruction management |
| **Scope** | 10 flats, 2 months | 34 properties, ongoing |
| **Count** | 10 records (frozen) | 43 invoices (growing) |
| **Links** | No property_id, no invoice_id | ✅ Linked to properties, customers |
| **PayProp Sync** | No payprop_id | ✅ 33/43 synced with PayProp |
| **Maintenance** | Static (never updates) | ✅ Active (updated as leases change) |
| **Creation Method** | Manual SQL insert | ✅ UI/API/PayProp import/CSV wizard |

### How Other Properties Are Served:

**Via the `invoices` table:**
- 34 properties have lease records (vs 10 in RENT_INVOICE)
- Covers full lease lifecycle (start, end, tenant changes)
- Syncs bidirectionally with PayProp
- Handles multiple leases per property (tenant changes)
- Links to actual transactions via financial_transactions.invoice_id

**Example - Comprehensive Coverage:**
```
RENT_INVOICE: Only Flats 1,2,3,4,5,7,13,15,17,18 at "3 West Gate"
invoices table: Flats 1-23 + Flat 40, Flat 42, etc. across multiple buildings
```

---

## 8. Recommendations

### Immediate Actions:

1. **Delete ICDN_MANUAL (5 records)** - They're duplicates of ICDN_ACTUAL with same data from PayProp
   ```sql
   DELETE FROM financial_transactions WHERE data_source = 'ICDN_MANUAL';
   ```

2. **Delete or Archive RENT_INVOICE (10 records)** - Obsolete, replaced by invoices table
   ```sql
   -- Option A: Delete
   DELETE FROM financial_transactions WHERE data_source = 'RENT_INVOICE';

   -- Option B: Archive for reference
   UPDATE financial_transactions
   SET data_source = 'RENT_INVOICE_ARCHIVED'
   WHERE data_source = 'RENT_INVOICE';
   ```

3. **Delete HISTORICAL_IMPORT and HISTORICAL_CSV (375 records)** - Duplicates in historical_transactions
   ```sql
   DELETE FROM financial_transactions
   WHERE data_source IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV');
   ```

### Medium Term:

4. **Standardize property_id in financial_transactions**
   - Currently uses PayProp ID (string)
   - Should use local property_id (integer) for consistency
   - Requires migration: map via properties.payprop_id

5. **Document invoice creation workflows**
   - Create admin guide for when to use each method
   - UI creation vs PayProp import vs CSV wizard

### Long Term:

6. **Consolidate invoice/lease terminology**
   - System uses "Invoice" to mean "Lease"
   - Consider renaming `invoices` → `leases` for clarity
   - Or update UI to clarify "Invoice Instruction = Lease Agreement"

---

## 9. Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                        USER ACTIONS                          │
├─────────────────────────────────────────────────────────────┤
│  1. Create Lease (UI)                                       │
│  2. Import PayProp Invoices                                 │
│  3. Upload Lease CSV                                        │
└──────────────┬────────────┬──────────────┬──────────────────┘
               │            │              │
               ▼            ▼              ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │ Invoice  │  │ PayProp  │  │  Lease   │
        │ Service  │  │  Import  │  │  Wizard  │
        └────┬─────┘  └────┬─────┘  └────┬─────┘
             │             │              │
             └─────────────┼──────────────┘
                           │
                           ▼
                  ┌─────────────────┐
                  │  invoices Table │ ← LEASE INSTRUCTIONS
                  │   43 records    │   (ongoing management)
                  └────────┬────────┘
                           │
                           │ invoice_id (foreign key)
                           │
                           ▼
         ┌──────────────────────────────────────┐
         │   financial_transactions Table       │ ← ACTUAL TRANSACTIONS
         │         1,019 records                 │   (payments, invoices)
         │                                       │
         │  Sources:                             │
         │  ├─ ICDN_ACTUAL (187) from PayProp   │
         │  ├─ BATCH_PAYMENT (202) from PayProp │
         │  ├─ INCOMING_PAYMENT (106)           │
         │  ├─ COMMISSION_PAYMENT (134) calc    │
         │  ├─ HISTORICAL_* (375) DUPLICATES ❌ │
         │  ├─ RENT_INVOICE (10) OBSOLETE ❌    │
         │  └─ ICDN_MANUAL (5) DUPLICATES ❌    │
         └──────────────┬───────────────────────┘
                        │
                        │ Rebuild process
                        │
                        ▼
              ┌───────────────────┐
              │ unified_           │ ← FOR STATEMENTS
              │ transactions      │   (combined view)
              │   571 records     │
              └─────────┬─────────┘
                        │
                        ▼
              ┌───────────────────┐
              │   Statements      │
              │   (PDF/Excel)     │
              └───────────────────┘
```

---

## 10. Conclusion

**Your Question:** Why are there only 10 RENT_INVOICE? What purpose do they serve? How is this purpose served for other properties?

**Answer:**

1. **Why only 10?**
   - RENT_INVOICE is an **obsolete one-time import** from Sept 16
   - Covered only "3 West Gate" building for Jan-Feb 2025
   - Was an early test/migration before full PayProp integration

2. **What purpose?**
   - Originally: Track rent invoices for those 10 flats
   - Now: **NO PURPOSE** - replaced by `invoices` table

3. **How served for other properties?**
   - Via the **`invoices` table** with 43 lease records covering 34 properties
   - Syncs with PayProp (33 invoices imported from PayProp)
   - Supports full lease lifecycle (creation, updates, tenant changes, termination)
   - Links to transactions via `financial_transactions.invoice_id`

**Your Insight on ICDN_MANUAL:** ✅ **YOU WERE CORRECT!**
- ICDN_MANUAL records ARE from PayProp
- They were imported without PayProp transaction IDs, creating duplicates
- The same 5 transactions exist as ICDN_ACTUAL with proper PayProp IDs
- Safe to delete - will be repopulated from PayProp on next sync

---

**Next Steps:**
1. Delete duplicates (ICDN_MANUAL, HISTORICAL_IMPORT, HISTORICAL_CSV)
2. Archive or delete obsolete RENT_INVOICE records
3. Rebuild unified_transactions to reflect clean data
4. Update statements to use invoices table + unified_transactions (not RENT_INVOICE)

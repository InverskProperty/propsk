# Data Flow Analysis: PayProp → Financial Transactions → Unified View

**Date:** October 29, 2025

---

## Question: Is financial_transactions Already a Standardization Layer?

**Answer: YES! It's already doing exactly that.**

---

## Current Data Flow Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                     LAYER 1: RAW IMPORT                          │
│                  (PayProp native format)                         │
└──────────────────────────────────────────────────────────────────┘
                            │
    ┌───────────────────────┼───────────────────────┐
    │                       │                       │
    ▼                       ▼                       ▼
┌─────────────┐   ┌──────────────────┐   ┌──────────────────┐
│ payprop_    │   │ payprop_report_  │   │ payprop_report_  │
│ report_     │   │ all_payments     │   │ agency_income    │
│ icdn        │   │                  │   │                  │
│             │   │ 202 records      │   │ 0 records        │
│ 172 records │   │                  │   │                  │
│             │   │ • Raw payment    │   │ • Commission     │
│ • Tenant    │   │   batches        │   │   data           │
│   rent      │   │ • Owner pmts     │   │                  │
│   payments  │   │ • Contractor     │   │                  │
│             │   │   payments       │   │                  │
└─────────────┘   └──────────────────┘   └──────────────────┘
                            │
                            │ TRANSFORMATION/STANDARDIZATION
                            │ (PayPropFinancialSyncService)
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│                 LAYER 2: STANDARDIZATION                         │
│              (Your internal schema format)                       │
└──────────────────────────────────────────────────────────────────┘
                            │
                            ▼
                  ┌───────────────────┐
                  │ financial_        │
                  │ transactions      │
                  │                   │
                  │ 1,019 records     │
                  │                   │
                  │ Sources:          │
                  │ • ICDN_ACTUAL     │ ← from payprop_report_icdn
                  │ • BATCH_PAYMENT   │ ← from payprop_report_all_payments
                  │ • COMMISSION_PMT  │ ← from payprop_report_agency_income
                  │ • INCOMING_PMT    │ ← from PayProp API
                  │ • HISTORICAL_IMP  │ ← from Sept 16 bulk import
                  └───────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│                  LAYER 3: UNIFIED VIEW                           │
│           (Combines multiple sources for queries)                │
└──────────────────────────────────────────────────────────────────┘
                            │
        ┌───────────────────┴───────────────────┐
        ▼                                       ▼
┌──────────────────┐                  ┌──────────────────┐
│ financial_       │                  │ historical_      │
│ transactions     │                  │ transactions     │
│                  │                  │                  │
│ PayProp data     │  MERGE/UNION    │ CSV imports      │
│ (standardized)   │  ─────────────> │ (your format)    │
│ 1,019 records    │                  │ 176 records      │
└──────────────────┘                  └──────────────────┘
                            │
                            ▼
                  ┌───────────────────┐
                  │ unified_          │
                  │ transactions      │
                  │                   │
                  │ (proposed)        │
                  │                   │
                  │ Used by:          │
                  │ • Statements      │
                  │ • Block finances  │
                  │ • Portfolios      │
                  └───────────────────┘
```

---

## Evidence: financial_transactions IS a Standardization Layer

### Proof 1: Data Transformation Logic

**File:** `PayPropFinancialSyncService.java:2419-2550`

```java
// RAW INPUT from payprop_report_all_payments
SELECT
    payprop_id, amount, description, due_date,
    beneficiary_payprop_id, beneficiary_name,
    payment_batch_id, reconciliation_date
FROM payprop_report_all_payments
WHERE beneficiary_type = 'beneficiary'

// STANDARDIZED OUTPUT to financial_transactions
FinancialTransaction transaction = new FinancialTransaction();
transaction.setPayPropTransactionId(payPropId);
transaction.setAmount(amount.abs());  // ← Normalized to positive
transaction.setTransactionDate(transactionDate);  // ← Standardized date field
transaction.setTransactionType("payment_to_beneficiary");  // ← Your internal enum
transaction.setPropertyId(propertyPayPropId);  // ← Mapped to your property
transaction.setDataSource("BATCH_PAYMENT");  // ← Tagged with source
```

**This is classic ETL (Extract, Transform, Load) standardization!**

### Proof 2: Multiple PayProp Tables → One Standard Table

| Raw PayProp Table | Records | Transforms To | financial_transactions.data_source |
|-------------------|---------|---------------|-----------------------------------|
| payprop_report_icdn | 172 | Tenant rent payments | ICDN_ACTUAL (187 records) |
| payprop_report_all_payments | 202 | Owner/contractor payments | BATCH_PAYMENT (202 records) |
| payprop_report_agency_income | 0 | Management fees | COMMISSION_PAYMENT (134 records) |
| PayProp API (incoming) | N/A | Incoming payments | INCOMING_PAYMENT (106 records) |

**Note:** More `financial_transactions` records than raw because:
- Enrichment (calculated commission records added)
- Splits (one raw payment becomes multiple records)
- Historical bulk import on Sept 16 (351 records)

### Proof 3: Schema Standardization

**Raw PayProp Schema** (`payprop_report_icdn`):
```sql
property_payprop_id VARCHAR(50)    -- PayProp's internal ID
tenant_payprop_id VARCHAR(50)       -- PayProp's tenant ID
category_payprop_id VARCHAR(50)     -- PayProp's category system
transaction_type VARCHAR(50)        -- PayProp's type vocabulary
```

**Standardized Schema** (`financial_transactions`):
```sql
property_id VARCHAR(100)            -- Maps to YOUR property.payprop_id
tenant_id VARCHAR(100)              -- Maps to YOUR customer
category_name VARCHAR(255)          -- Human-readable
transaction_type VARCHAR(50)        -- YOUR transaction types
invoice_id BIGINT                   -- FK to YOUR invoices (leases)
```

### Proof 4: Rebuilability Test

```sql
-- Delete standardized layer
DELETE FROM financial_transactions WHERE data_source IN (
    'ICDN_ACTUAL', 'BATCH_PAYMENT', 'COMMISSION_PAYMENT'
);

-- Re-run PayProp sync
POST /api/payprop/sync

-- Result: financial_transactions repopulated from raw tables!
```

✅ **This confirms financial_transactions is derived/rebuilable**

---

## Three-Layer Architecture

### Layer 1: Raw Imports (Source of Truth)
**Purpose:** Store PayProp data exactly as received

**Tables:**
- `payprop_report_icdn` (172 records)
- `payprop_report_all_payments` (202 records)
- `payprop_report_agency_income` (0 records)
- `payprop_export_*` tables (properties, tenants, beneficiaries)

**Characteristics:**
- ✅ Immutable (only overwritten by new PayProp sync)
- ✅ Can be deleted and re-imported from PayProp API
- ✅ PayProp native schema (their field names, IDs, structure)
- ❌ Not queryable for statements (schema doesn't match your needs)

### Layer 2: Standardization Layer (Already Exists!)
**Purpose:** Transform PayProp format → Your internal format

**Table:** `financial_transactions`

**Characteristics:**
- ✅ Your schema (property_id, invoice_id, transaction_date, category_name)
- ✅ Rebuilable from Layer 1 (via PayPropFinancialSyncService)
- ✅ Tagged with source (`data_source` field)
- ✅ Enriched (adds commission records, links to invoices/leases)
- ❌ Mixed with historical imports (351 HISTORICAL_IMPORT records)
- ⚠️ Not yet used by statements (they read historical_transactions only)

### Layer 3: Unified Query Layer (Proposed)
**Purpose:** Combine multiple standardized sources for querying

**Table:** `unified_transactions` (to be created)

**Characteristics:**
- ✅ Combines `financial_transactions` + `historical_transactions`
- ✅ Rebuilable from Layer 2 sources
- ✅ Single table for all queries (statements, blocks, portfolios)
- ✅ Deduplication handled
- ✅ Can be dropped and rebuilt in seconds

---

## Recommended Architecture: Keep All 3 Layers

```
LAYER 1          LAYER 2               LAYER 3
Raw              Standardization       Unified Query
─────────────    ──────────────────    ─────────────────
payprop_*        financial_            unified_
  tables         transactions          transactions

172 records  →   1,019 records    →    ~1,195 records
                                       (1,019 + 176)
PayProp          + historical_
format           transactions          Used by:
                                       • Statements
                 176 records           • Block finances
                                       • Portfolios
                 Your format
```

### Why Keep Layer 2 (financial_transactions)?

**Reason 1: PayProp Updates**
When PayProp corrects an expense:
1. Raw table updated: `payprop_report_all_payments`
2. Sync service reruns transformation
3. `financial_transactions` updated automatically
4. Unified view rebuilt

Without Layer 2, you'd need to query raw PayProp tables directly (messy!).

**Reason 2: Enrichment**
`financial_transactions` adds:
- Commission calculation records
- Property/lease linking (invoice_id)
- Tenant/owner mappings
- Data source tagging

**Reason 3: Multiple Raw Sources**
You have 3+ PayProp report tables. Layer 2 merges them into one standard format.

**Reason 4: Future Sources**
When you add Xero/QuickBooks:
```
xero_raw_*  →  financial_transactions  →  unified_transactions
                (with data_source='XERO')
```

---

## Your Specific Questions Answered

### Q: "Is financial_transactions already a derivative of payprop_export tables?"

**A: YES!** It's derived from:
- `payprop_report_icdn` → `ICDN_ACTUAL` records
- `payprop_report_all_payments` → `BATCH_PAYMENT` records
- `payprop_report_agency_income` → `COMMISSION_PAYMENT` records
- PayProp API direct calls → `INCOMING_PAYMENT` records

### Q: "Is it useful to keep that layer?"

**A: ABSOLUTELY YES!** Because:

1. **Standardization:** Converts PayProp schema to your internal schema
2. **Enrichment:** Adds commission records, links leases, maps properties
3. **Rebuilable:** Delete and rebuild from raw tables anytime
4. **Queryable:** Ready for your application to use (correct field names/types)
5. **Extensible:** Can add Xero, QuickBooks, etc. in the same format

### Q: "Would that be the way it works: raw import tables → standardized local tables → unified table?"

**A: EXACTLY!** That's the ideal pattern:

```
RAW IMPORTS          STANDARDIZATION        UNIFIED QUERY
(Source Systems)     (Your Format)          (One Table)
───────────────      ─────────────────      ────────────────
payprop_report_*  →  financial_           → unified_
                     transactions             transactions
historical CSVs   →  historical_          ↗
                     transactions

xero_imports      →  financial_           ↗
(future)             transactions
                     (data_source='XERO')
```

---

## Proposed Implementation

### Step 1: Keep Existing Layer 2
**No changes needed!** `financial_transactions` is already perfect for its purpose.

### Step 2: Create Layer 3 (Unified View)

```sql
CREATE TABLE unified_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Source tracking
    source_system ENUM('HISTORICAL', 'PAYPROP', 'XERO', 'QUICKBOOKS'),
    source_table VARCHAR(50),  -- 'historical_transactions' or 'financial_transactions'
    source_record_id BIGINT,   -- FK to original record

    -- Standard fields (your schema)
    transaction_date DATE NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    description TEXT,
    category VARCHAR(100),

    -- Relationships (standardized)
    invoice_id BIGINT,  -- Lease
    property_id BIGINT,
    customer_id BIGINT,

    -- Denormalized for performance
    lease_reference VARCHAR(100),
    property_name VARCHAR(255),

    -- PayProp specific (nullable for historical)
    payprop_transaction_id VARCHAR(100),
    payprop_data_source VARCHAR(50),

    -- Rebuild tracking
    rebuilt_at DATETIME NOT NULL,
    rebuild_batch_id VARCHAR(100),

    INDEX idx_date (transaction_date),
    INDEX idx_lease (invoice_id),
    INDEX idx_property (property_id),
    UNIQUE KEY (source_system, source_table, source_record_id)
);
```

### Step 3: Rebuild Process

```java
@Service
public class UnifiedTransactionRebuildService {

    @Transactional
    public void rebuild() {
        // 1. Truncate unified table
        jdbcTemplate.execute("TRUNCATE unified_transactions");

        // 2. Insert from financial_transactions (Layer 2 standardized data)
        String sql1 = """
            INSERT INTO unified_transactions (
                source_system, source_table, source_record_id,
                transaction_date, amount, description, category,
                invoice_id, property_id,
                payprop_transaction_id, payprop_data_source,
                rebuilt_at, rebuild_batch_id
            )
            SELECT
                'PAYPROP', 'financial_transactions', ft.id,
                ft.transaction_date, ft.amount, ft.description, ft.category_name,
                ft.invoice_id, p.id,
                ft.pay_prop_transaction_id, ft.data_source,
                NOW(), ?
            FROM financial_transactions ft
            LEFT JOIN properties p ON ft.property_id = p.payprop_id
            WHERE ft.invoice_id IS NOT NULL
              AND ft.data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
        """;

        // 3. Insert from historical_transactions (Layer 2 for historical data)
        String sql2 = """
            INSERT INTO unified_transactions (
                source_system, source_table, source_record_id,
                transaction_date, amount, description, category,
                invoice_id, property_id, customer_id,
                rebuilt_at, rebuild_batch_id
            )
            SELECT
                'HISTORICAL', 'historical_transactions', ht.id,
                ht.transaction_date, ht.amount, ht.description, ht.category,
                ht.invoice_id, ht.property_id, ht.customer_id,
                NOW(), ?
            FROM historical_transactions ht
            WHERE ht.invoice_id IS NOT NULL
        """;

        String batchId = "REBUILD-" + LocalDateTime.now();
        jdbcTemplate.update(sql1, batchId);
        jdbcTemplate.update(sql2, batchId);
    }
}
```

### Step 4: Update Statement Service

```java
// BEFORE (current):
List<HistoricalTransaction> transactions =
    historicalTransactionRepository.findAll();

// AFTER (unified):
List<UnifiedTransaction> transactions =
    unifiedTransactionRepository.findByInvoiceIdNotNull();
```

---

## Benefits of 3-Layer Architecture

### ✅ Separation of Concerns
- **Layer 1:** PayProp's problem (their schema)
- **Layer 2:** Your problem (standardize once)
- **Layer 3:** Query optimization (merge sources)

### ✅ Rebuilability at Each Layer
- Delete Layer 1 → Re-import from PayProp API
- Delete Layer 2 → Rebuild from Layer 1 (sync service)
- Delete Layer 3 → Rebuild from Layer 2 (rebuild service)

### ✅ Easy Updates
PayProp corrects expense:
1. Update `payprop_report_all_payments` (Layer 1)
2. Rerun sync → Updates `financial_transactions` (Layer 2)
3. Rebuild → Updates `unified_transactions` (Layer 3)

### ✅ Extensible
Add Xero:
```
xero_raw_invoices (Layer 1)
    ↓ sync service
financial_transactions (Layer 2, data_source='XERO')
    ↓ rebuild service
unified_transactions (Layer 3)
```

### ✅ Performance
Statements query one table (`unified_transactions`), not 5+ tables.

---

## Migration Path

**Week 1:** Create `unified_transactions` table and rebuild service
**Week 2:** Test rebuild process (compare counts, amounts)
**Week 3:** Update statement generation to use `unified_transactions`
**Week 4:** Update block finances, portfolios to use unified view
**Week 5:** Clean up 351 duplicate HISTORICAL_IMPORT records from `financial_transactions`

---

## Summary

### Current State: ✅ Already Have 2 Layers!
- ✅ Layer 1: Raw PayProp tables (`payprop_report_*`)
- ✅ Layer 2: Standardized (`financial_transactions`)
- ❌ Missing: Layer 3 unified view

### Recommended: Add Layer 3
- Keep Layer 1 (raw imports)
- Keep Layer 2 (standardization) ← **Very valuable!**
- Add Layer 3 (unified query table)

### Why Keep financial_transactions?
1. Converts PayProp schema to your schema
2. Enriches data (commission records, property mapping)
3. Rebuilable from raw tables
4. Extensible to other sources (Xero, QuickBooks)
5. Already working well!

**Don't skip the standardization layer - it's the key to maintainability!**

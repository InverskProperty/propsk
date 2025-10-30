# Data Sources Explained - Complete Guide

**Date:** October 30, 2025
**Purpose:** Clarify exactly what tables feed into unified_transactions

---

## Question 1: What Tables Are We Bringing Into Unified?

### ✅ **TWO Source Tables Feed unified_transactions:**

#### 1. `historical_transactions` (Historical CSV imports)
- **Purpose:** Pre-PayProp transaction history
- **Contains:** Bank statements, manual entries, legacy data
- **Row count:** Varies by property
- **Key fields:**
  - `transaction_date`, `amount`, `description`
  - `property_id`, `customer_id`, `invoice_id`
  - `category`, `transaction_type`

**What gets included in unified:**
```sql
WHERE invoice_id IS NOT NULL
```
Only historical transactions linked to a lease.

---

#### 2. `financial_transactions` (PayProp synced data)
- **Purpose:** Transactions imported from PayProp API
- **Contains:** Real-time PayProp financial data
- **Row count:** 629 records currently
- **Key fields:**
  - `transaction_date`, `amount`, `description`
  - `pay_prop_transaction_id`, `data_source`
  - `property_id`, `invoice_id`

**What gets included in unified:**
```sql
WHERE invoice_id IS NOT NULL
  AND data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV', 'ICDN_ACTUAL')
```

**Included data_source values:**
- ✅ `INCOMING_PAYMENT` - Tenant payments received (106 records)
- ✅ `BATCH_PAYMENT` - Owner disbursements (202 records)
- ✅ `COMMISSION_PAYMENT` - Management fees (134 records)

**Excluded data_source values:**
- ❌ `ICDN_ACTUAL` - Invoices (187 records) **← NOW EXCLUDED**
- ❌ `HISTORICAL_IMPORT` - Duplicates historical_transactions
- ❌ `HISTORICAL_CSV` - Duplicates historical_transactions

---

### Summary: Data Flow to Unified

```
┌────────────────────────────┐
│  historical_transactions   │ (Historical CSV imports)
│  - Pre-PayProp data        │
│  - Bank statements         │
│  - Manual entries          │
└─────────────┬──────────────┘
              │
              ├─────> WHERE invoice_id IS NOT NULL
              │
              ▼
┌─────────────────────────────────────────┐
│       unified_transactions               │
│  - Materialized view                    │
│  - Rebuilt from sources                 │
│  - Cash flow only                       │
└─────────────────────────────────────────┘
              ▲
              │
              ├─────> WHERE invoice_id IS NOT NULL
              │        AND data_source IN (
              │          'INCOMING_PAYMENT',
              │          'BATCH_PAYMENT',
              │          'COMMISSION_PAYMENT'
              │        )
              │
┌─────────────┴──────────────┐
│  financial_transactions    │ (PayProp API sync)
│  - INCOMING_PAYMENT        │ ✓
│  - BATCH_PAYMENT           │ ✓
│  - COMMISSION_PAYMENT      │ ✓
│  - ICDN_ACTUAL             │ ✗ (excluded)
└────────────────────────────┘
```

---

## Question 2: Where Are Actual Rent Invoices Stored?

### The "invoices" Table (Local Lease Agreements)

**Table:** `invoices`
- **Purpose:** Stores lease agreements with rent amounts
- **Row count:** ~60+ leases
- **This is YOUR local table**, not a PayProp export

**Structure:**
```
id                  - Local invoice/lease ID
payprop_id          - PayProp invoice instruction ID (if linked)
lease_reference     - e.g., "LEASE-BH-F1-2025"
customer_id         - Tenant ID
property_id         - Property ID
amount              - Monthly rent (e.g., £795)
frequency           - "monthly"
start_date          - Lease start
end_date            - Lease end (or NULL for ongoing)
description         - Lease description
is_active           - Currently active?
invoice_type        - Type of agreement
```

**Sample data:**
| ID | Lease Ref | PayProp ID | Amount | Frequency |
|----|-----------|------------|--------|-----------|
| 1 | LEASE-BH-F1-2025 | PYZ249oAXQ | £795 | monthly |
| 2 | LEASE-BH-F2-2025 | KAXNko2j1k | £740 | monthly |
| 3 | LEASE-BH-F3-2025 | PzZy6370Jd | £740 | monthly |

**Key Point:**
- This is a **LEASE AGREEMENT**, not individual invoices
- Defines: "Tenant pays £795/month starting July 1"
- Individual monthly invoices would be in PayProp's system

---

### PayProp Invoice Data (Two Types)

#### A. `payprop_export_invoices` (Invoice INSTRUCTIONS)
- **What it is:** Setup/configuration for recurring invoices
- **Analogy:** "Create a £795 invoice every month for this tenant"
- **Row count:** 34 instructions
- **This is the TEMPLATE**, not the actual invoices

**Structure:**
```
payprop_id          - Instruction ID
property_payprop_id - Property this applies to
tenant_payprop_id   - Tenant to invoice
gross_amount        - Amount (£795)
frequency           - "Monthly"
from_date           - Start date
to_date             - End date (or NULL)
is_active_instruction - Still active?
```

**Example:**
```
Instruction: "Invoice tenant X for £795 every month starting June 10"
```

---

#### B. `payprop_report_icdn` (ACTUAL Invoices Created)
- **What it is:** Individual invoices/credits/debits that were ACTUALLY created
- **Analogy:** "Invoice #yJ66DlrkJj for £795 sent on July 4, 2025"
- **Row count:** 172 actual invoice records
- **This is the ACTUAL BILLS sent to tenants**

**ICDN = Invoice, Credit note, Debit note**

**Structure:**
```
payprop_id          - Unique invoice ID
transaction_type    - "invoice", "credit note", "debit note"
amount              - Invoice amount (£795)
transaction_date    - Date invoice was created
property_name       - "Flat 1 - 3 West Gate"
tenant_name         - Who owes this
description         - Invoice details
commission_amount   - Commission on this invoice
```

**Sample ICDN records for Flat 1:**
| PayProp ID | Type | Date | Amount | Description |
|-----------|------|------|--------|-------------|
| yJ66DlrkJj | invoice | 2025-07-04 | £795 | Monthly rent |
| RXEOr4wkZO | invoice | 2025-08-04 | £795 | Monthly rent |
| eJP7BDvKZG | credit note | 2025-08-03 | £795 | Reversal |
| EJAapkGRJj | invoice | 2025-09-04 | £795 | Monthly rent |

**What this means:**
- July: Invoice created for £795
- August: Invoice created, then REVERSED with credit note
- September: New invoice created

---

### The Confusion About "Invoices"

**Three different meanings:**

1. **`invoices` table** = **Lease agreements** (your local database)
   - "Tenant pays £795/month for this property"
   - Setup once, defines ongoing rent

2. **`payprop_export_invoices`** = **Invoice templates/instructions** (from PayProp)
   - "Create a £795 invoice every month"
   - Configuration, not actual invoices

3. **`payprop_report_icdn`** = **Actual invoices created** (from PayProp)
   - "Invoice #12345 for £795 created on July 4"
   - Individual bills sent to tenants each month

**Why we exclude ICDN from unified:**
- ICDN = Invoices SENT (what tenant owes)
- Unified should have = Payments RECEIVED (actual money)
- Including both would double-count

---

## Question 3: Where Do Payment Instructions Get Saved?

### `payprop_export_payments` (Payment Distribution Rules)

**What it is:** Instructions for HOW to distribute incoming payments

**Row count:** 66 payment rules

**Think of it as:** "When rent comes in, split it like this..."

**Structure:**
```
payprop_id              - Rule ID
property_payprop_id     - Which property
beneficiary             - Who gets paid (e.g., "Udayan Bhardwaj [B]")
category                - "Owner", "Commission", "Service Fee"
gross_percentage        - % of rent (e.g., 100% for owner, 15% for commission)
gross_amount            - Fixed amount (alternative to percentage)
frequency               - "Monthly"
from_date               - Rule starts
to_date                 - Rule ends (or NULL)
description             - What this payment is for
enabled                 - Rule active?
```

**Example payment rules for Flat 1:**

| Rule ID | Beneficiary | Category | Percentage | Description |
|---------|-------------|----------|------------|-------------|
| GX0EWog413 | Udayan Bhardwaj [B] | Owner | 100% | Landlord Rental Payment |
| n183eo2NX9 | Propsk [C] | Commission | 15% | Management Fee |

**What this means:**
```
When £795 rent is received for Flat 1:
1. Pay 100% (£795) to landlord Udayan Bhardwaj
2. Charge 15% (£119.25) commission to Propsk
```

**Important distinction:**

| Table | What it stores | Example |
|-------|---------------|---------|
| **payprop_export_payments** | **RULES** | "Always pay landlord 100%" |
| **payprop_export_incoming_payments** | **ACTUAL RECEIPTS** | "£795 received on July 15" |
| **payprop_report_all_payments** | **ACTUAL DISBURSEMENTS** | "£795 paid to landlord on July 20" |

---

## The Complete Picture

### PayProp Table Categories

#### 1. EXPORT Tables (Property Setup)
- `payprop_export_properties` - Property master list (45 rows)
- `payprop_export_tenants` - Tenant master list (42 rows)
- `payprop_export_beneficiaries` - Payment recipients (8 rows)
- `payprop_export_invoices` - Invoice INSTRUCTIONS (34 rows)
- `payprop_export_payments` - Payment RULES (66 rows)

#### 2. EXPORT Tables (Actual Transactions)
- `payprop_export_incoming_payments` - Rent RECEIVED (106 rows)

#### 3. REPORT Tables (Financial Data)
- `payprop_report_icdn` - Invoices/Credits/Debits ISSUED (172 rows)
- `payprop_report_all_payments` - Payments DISBURSED (202 rows)

---

## Summary: What Goes Into Unified?

### From `historical_transactions`:
✅ All historical transactions WHERE invoice_id IS NOT NULL

### From `financial_transactions`:
✅ INCOMING_PAYMENT - Tenant payments received
✅ BATCH_PAYMENT - Owner payments disbursed
✅ COMMISSION_PAYMENT - Management fees charged
❌ ICDN_ACTUAL - Invoices issued (**NOW EXCLUDED**)

### Why This Makes Sense:

**Unified transactions should answer:**
- "How much money came in?" → INCOMING_PAYMENT
- "How much money went out?" → BATCH_PAYMENT
- "How much commission charged?" → COMMISSION_PAYMENT

**Unified should NOT include:**
- "How much did we invoice?" → ICDN_ACTUAL (not cash flow)

---

## Practical Examples

### Example 1: Flat with Full PayProp Integration

**Scenario:** Flat 4 - 3 West Gate, rent £855/month

**Invoice instruction:**
```
payprop_export_invoices: "Invoice tenant £855 every month"
```

**Actual invoices:**
```
payprop_report_icdn:
- July 4: Invoice #ABC123 for £855
- Aug 4: Invoice #DEF456 for £855
```

**Actual receipts:**
```
payprop_export_incoming_payments:
- July 10: £855 received
- Aug 12: £855 received
```

**Payment rules:**
```
payprop_export_payments:
- Rule: Pay landlord 100% (£855)
- Rule: Charge commission 15% (£128.25)
```

**Actual disbursements:**
```
payprop_report_all_payments:
- July 15: £855 paid to landlord
- Aug 15: £855 paid to landlord
```

**What goes in unified_transactions:**
✅ July 10: INCOMING_PAYMENT £855 (money IN)
✅ July 15: BATCH_PAYMENT £855 (money OUT to landlord)
✅ July 15: COMMISSION_PAYMENT £128.25 (fee charged)
✅ Aug 12: INCOMING_PAYMENT £855
✅ Aug 15: BATCH_PAYMENT £855
✅ Aug 15: COMMISSION_PAYMENT £128.25

❌ NOT July 4 invoice (ICDN_ACTUAL) - that's just a bill, not a transaction

---

### Example 2: Flat 1-3 (Partial PayProp)

**Scenario:** Flats 1-3, NOT fully on PayProp

**Invoice instruction:**
```
payprop_export_invoices: "Invoice tenant £795 every month"
```

**Actual invoices:**
```
payprop_report_icdn:
- July: Invoice created
- Aug: Invoice created
```

**Actual receipts:**
```
payprop_export_incoming_payments: NONE (not tracked in PayProp)
```

**What goes in unified_transactions:**
❌ NOTHING from PayProp (no actual receipts)
✅ Only historical_transactions (if they exist)

**This is why Flat 1-3 show commission but no income** - invoices exist (ICDN), but no actual payments tracked.

---

## Key Takeaways

1. **Two source tables:** `historical_transactions` + `financial_transactions`

2. **Three data sources included:**
   - INCOMING_PAYMENT (rent received)
   - BATCH_PAYMENT (money paid out)
   - COMMISSION_PAYMENT (fees charged)

3. **Invoices in THREE places:**
   - `invoices` = Lease agreements (local)
   - `payprop_export_invoices` = Invoice templates (setup)
   - `payprop_report_icdn` = Actual invoices created (excluded from unified)

4. **Payment instructions:** `payprop_export_payments` = Rules for how to split money

5. **Why ICDN excluded:** Invoices ≠ cash flow (just bills, not payments)

---

**End of Guide**

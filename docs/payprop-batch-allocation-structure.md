# PayProp Batch and Allocation Structure

## Overview

This document explains how PayProp batches work and how rent payments are split into allocations for owners, commission, and expenses.

## Key Concept: Batches Contain NET Values, Not GROSS

PayProp batches represent the **net payment amount** to each beneficiary, not the gross rent. When a tenant pays rent, PayProp splits it into multiple allocations, each potentially going to a different batch.

---

## Data Flow: From Tenant Payment to Owner

```
TENANT PAYS RENT (Gross Amount)
           │
           ▼
┌─────────────────────────────────────────────────────────┐
│  payprop_report_all_payments                            │
│  ─────────────────────────────────────────────────────  │
│  incoming_transaction_id: unique ID for this payment    │
│  incoming_transaction_amount: GROSS RENT (e.g., £1,400) │
│  incoming_property_name: property details               │
│  incoming_tenant_name: tenant who paid                  │
└─────────────────────────────────────────────────────────┘
           │
           │  PayProp automatically splits into allocations
           ▼
┌─────────────────────────────────────────────────────────┐
│  MULTIPLE ALLOCATION RECORDS                            │
│  (all share same incoming_transaction_id)               │
│  ─────────────────────────────────────────────────────  │
│  1. OWNER allocation      → goes to owner's batch       │
│  2. COMMISSION allocation → goes to agency batch        │
│  3. EXPENSE allocations   → go to contractor batches    │
└─────────────────────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────┐
│  PAYMENT BATCHES                                        │
│  ─────────────────────────────────────────────────────  │
│  Each beneficiary gets their own batch                  │
│  Batch amount = only that beneficiary's allocation      │
└─────────────────────────────────────────────────────────┘
```

---

## Production Database Structure

### Current Tables

| Table | Records | Purpose |
|-------|---------|---------|
| `payprop_report_all_payments` | ~6,900+ | Raw PayProp data with ALL allocations |
| `unified_incoming_transactions` | ~400+ | Gross rent payments (tenant → property) |
| `unified_allocations` | ~421 | **INCOMPLETE** - missing most commissions |
| `unified_transactions` | ~800+ | Consolidated transaction view |
| `historical_transactions` | ~192 | Legacy/manual transactions |

### Current Problem: Missing Commission Allocations

```
payprop_report_all_payments: 131 commission records
unified_allocations:           6 commission records  ← 95% MISSING!
```

The system is NOT syncing commission allocations to `unified_allocations`, making reconciliation impossible.

---

## Concrete Example: Flat 10 - 3 West Gate (Production)

### The Incoming Payment

| Field | Value |
|-------|-------|
| Gross Rent | £785.00 |
| Transaction Date | 2025-11-21 |
| Property | Flat 10 - 3 West Gate |

### PayProp Allocations (from payprop_report_all_payments)

| PayProp ID | Beneficiary | Category | Amount | Batch ID |
|------------|-------------|----------|--------|----------|
| YZ2YlaYj1Q | Udayan Bhardwaj | Owner | £546.83 | YZ2WWPAKJQ |
| RXEMRmMkXO | Agency | Commission | £117.75 | zJBBB46WJQ |
| zZyqKm5KZd | EREWASH COUNCIL | Council | £20.42 | wXxzzbDYZA |
| 7Z4PVrnqZP | BODEN HOUSE BLOCK | Disbursement | £100.00 | GX0yyp2413 |

### What's in unified_allocations (CURRENT - INCOMPLETE)

| ID | Type | Amount | Category | Beneficiary |
|----|------|--------|----------|-------------|
| 468 | OWNER | £546.83 | Owner | Udayan Bhardwaj |
| 481 | OWNER | £20.42 | Council | EREWASH COUNCIL |
| 308 | DISBURSEMENT | £100.00 | Disbursement | BODEN HOUSE BLOCK |
| ??? | COMMISSION | £117.75 | Commission | **MISSING!** |

### Verification
```
Owner:        £546.83
Commission:   £117.75  ← NOT IN unified_allocations
Council:       £20.42
Disbursement: £100.00
─────────────────────
Total:        £785.00 ✓ (matches gross rent)
```

---

## Proposed Change: Complete Allocation Sync

### Goal
Import ALL allocations from PayProp so `unified_allocations` contains:
- OWNER allocations (net to owner)
- COMMISSION allocations (agency fee)
- EXPENSE allocations (contractors, council, etc.)
- DISBURSEMENT allocations (block property payments)

### What unified_allocations SHOULD look like:

| ID | Type | Amount | Category | Beneficiary | Batch ID |
|----|------|--------|----------|-------------|----------|
| 468 | OWNER | £546.83 | Owner | Udayan Bhardwaj | YZ2WWPAKJQ |
| NEW | COMMISSION | £117.75 | Commission | Agency | zJBBB46WJQ |
| 481 | EXPENSE | £20.42 | Council | EREWASH COUNCIL | wXxzzbDYZA |
| 308 | DISBURSEMENT | £100.00 | Disbursement | BODEN HOUSE BLOCK | GX0yyp2413 |

### Benefits
1. **Full reconciliation** - Sum of allocations = gross rent
2. **Commission tracking** - See agency fees per property/transaction
3. **Expense visibility** - All deductions visible in one place
4. **Audit trail** - Complete breakdown of where rent money goes

---

## Database Tables

### payprop_report_all_payments

This is the main table containing PayProp allocation data.

**Key Fields:**

| Field | Description |
|-------|-------------|
| `payprop_id` | Unique allocation ID |
| `amount` | Allocation amount (net to this beneficiary) |
| `beneficiary_name` | Who receives this payment |
| `beneficiary_type` | `agency`, `beneficiary`, or `global_beneficiary` |
| `category_name` | `Owner`, `Commission`, `Contractor`, etc. |
| `incoming_transaction_id` | Links all allocations from same rent payment |
| `incoming_transaction_amount` | **GROSS RENT** - the original tenant payment |
| `incoming_property_name` | Property name |
| `incoming_tenant_name` | Tenant who paid |
| `payment_batch_id` | Which batch this allocation belongs to |
| `payment_batch_amount` | Total amount in that batch |
| `payment_batch_status` | `paid`, `in progress`, `failed`, etc. |
| `payment_batch_transfer_date` | When batch was paid to beneficiary |

### unified_incoming_transactions

Stores the GROSS rent payment from tenant.

**Key Fields:**

| Field | Description |
|-------|-------------|
| `id` | Primary key (referenced by unified_allocations.incoming_transaction_id) |
| `amount` | **GROSS RENT** amount |
| `transaction_date` | When tenant paid |
| `property_id` | Link to property |
| `tenant_name` | Tenant who paid |
| `payprop_transaction_id` | PayProp's incoming_transaction_id |

### unified_allocations

Should contain ALL allocations for reconciliation.

**Key Fields:**

| Field | Description |
|-------|-------------|
| `id` | Primary key |
| `incoming_transaction_id` | FK to unified_incoming_transactions |
| `unified_transaction_id` | FK to unified_transactions |
| `allocation_type` | `OWNER`, `COMMISSION`, `EXPENSE`, `DISBURSEMENT`, `OTHER` |
| `amount` | Allocation amount |
| `category` | Category name from PayProp |
| `beneficiary_id` | Who receives payment |
| `beneficiary_name` | Beneficiary name |
| `payment_batch_id` | PayProp batch ID |
| `payment_status` | `PENDING`, `BATCHED`, `PAID` |
| `payprop_payment_id` | PayProp's payprop_id for this allocation |

### unified_transactions

Consolidated transaction view (stores gross amount).

**Key Fields:**

| Field | Description |
|-------|-------------|
| `id` | Primary key |
| `amount` | Transaction amount (gross) |
| `net_to_owner_amount` | Calculated net after deductions |
| `commission_rate` | Commission percentage |
| `commission_amount` | Calculated commission |
| `transaction_date` | Transaction date |

### historical_transactions

Contains transaction records with calculated commission fields.

**Key Fields:**

| Field | Description |
|-------|-------------|
| `amount` | Transaction amount |
| `commission_rate` | Commission percentage |
| `commission_amount` | Calculated commission |
| `net_to_owner_amount` | Amount after commission |
| `payprop_transaction_id` | Link to PayProp |
| `payprop_batch_id` | Link to PayProp batch |

---

## Beneficiary Types

| Type | Description |
|------|-------------|
| `beneficiary` | Property owner or contractor receiving payment |
| `agency` | The property management agency (receives commission) |
| `global_beneficiary` | Shared/global expense recipients |

## Category Types

| Category | Description | Allocation Type |
|----------|-------------|-----------------|
| `Owner` | Net rent to property owner | OWNER |
| `Commission` | Agency management fee | COMMISSION |
| `Contractor` | Maintenance, repairs, services | EXPENSE |
| `Council` | Council tax payments | EXPENSE |
| `Disbursement` | Block property payments | DISBURSEMENT |
| Other categories | Various expenses | EXPENSE/OTHER |

---

## Important Relationships

### Current Data Chain (Production)

```
unified_incoming_transactions (GROSS RENT: £785.00)
         │
         │ incoming_transaction_id
         ▼
unified_allocations
    ├── OWNER: £546.83        ✓ synced
    ├── COMMISSION: £117.75   ✗ MISSING
    ├── EXPENSE: £20.42       ✓ synced (as OWNER - wrong type?)
    └── DISBURSEMENT: £100.00 ✓ synced
         │
         │ unified_transaction_id
         ▼
unified_transactions (amount: £785.00 gross)
```

### Desired Data Chain

```
unified_incoming_transactions (GROSS RENT: £785.00)
         │
         │ incoming_transaction_id
         ▼
unified_allocations (SUM = £785.00)
    ├── OWNER: £546.83
    ├── COMMISSION: £117.75   ← NOW INCLUDED
    ├── EXPENSE: £20.42       ← CORRECT TYPE
    └── DISBURSEMENT: £100.00
         │
         │ unified_transaction_id
         ▼
unified_transactions (amount: £785.00 gross)
```

---

## SQL Examples

### Find all allocations for a property owner's batch
```sql
SELECT
    p1.payprop_id,
    p1.amount,
    p1.beneficiary_name,
    p1.category_name,
    p1.incoming_transaction_amount as gross_rent
FROM payprop_report_all_payments p1
WHERE p1.payment_batch_id = 'V1znKzOo1O';
```

### Find complete breakdown of a rent payment
```sql
SELECT
    payprop_id,
    amount,
    beneficiary_name,
    beneficiary_type,
    category_name,
    payment_batch_id,
    incoming_transaction_amount as gross_rent
FROM payprop_report_all_payments
WHERE incoming_transaction_id = '0JYPWBKeZo'
ORDER BY category_name;
```

### Sum allocations to verify against gross
```sql
SELECT
    incoming_transaction_id,
    incoming_transaction_amount as gross_rent,
    SUM(amount) as total_allocated,
    (incoming_transaction_amount - SUM(amount)) as difference
FROM payprop_report_all_payments
WHERE incoming_transaction_id = '0JYPWBKeZo'
GROUP BY incoming_transaction_id, incoming_transaction_amount;
```

### Find missing commission allocations (not in unified_allocations)
```sql
SELECT
    p.payprop_id,
    p.amount,
    p.category_name,
    p.incoming_transaction_id,
    p.incoming_property_name
FROM payprop_report_all_payments p
LEFT JOIN unified_allocations ua ON ua.payprop_payment_id = p.payprop_id
WHERE p.category_name = 'Commission'
  AND ua.id IS NULL;
```

### Compare allocation counts between tables
```sql
SELECT 'payprop_report_all_payments' as source, category_name, COUNT(*) as count
FROM payprop_report_all_payments
GROUP BY category_name
UNION ALL
SELECT 'unified_allocations', allocation_type, COUNT(*)
FROM unified_allocations
GROUP BY allocation_type
ORDER BY source, category_name;
```

---

## Implementation Notes

### Files to Modify

1. **Sync Service** - The service that imports from `payprop_report_all_payments` to `unified_allocations` needs to include Commission allocations

2. **Allocation Type Mapping** - Map PayProp categories to correct allocation_type:
   - `Owner` → `OWNER`
   - `Commission` → `COMMISSION`
   - `Contractor` → `EXPENSE`
   - `Council` → `EXPENSE`
   - `Disbursement` → `DISBURSEMENT`
   - Others → `EXPENSE` or `OTHER`

3. **Statement Generator** - Update to use complete allocation data for reconciliation

### Migration Required

Need to backfill missing commission allocations from `payprop_report_all_payments` to `unified_allocations`.

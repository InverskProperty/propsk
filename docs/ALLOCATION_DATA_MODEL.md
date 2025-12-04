# Allocation Data Model - Verified Facts

## 100% VERIFIED: Database Structure

### Key Tables

| Table | Purpose | Primary Key |
|-------|---------|-------------|
| `payment_batches` | Owner payment batches | `id`, unique `batch_id` |
| `transaction_batch_allocations` | Individual transaction allocations | `id` |
| `historical_transactions` | Source transactions (rent, expenses) | `id` |
| `unified_allocations` | PayProp payment summaries | `id` |

---

## CRITICAL: How Owner is Determined

**The `beneficiary_id` in `transaction_batch_allocations` is UNRELIABLE (mostly NULL).**

### Correct Relationship:
```
payment_batches.beneficiary_id = 68 (owner's customer_id)
         ↓
payment_batches.batch_id = transaction_batch_allocations.batch_reference
         ↓
transaction_batch_allocations (individual rent/expense allocations)
         ↓
historical_transactions (source transaction details)
```

### Wrong Approach (DO NOT USE):
```sql
-- This returns very few results because beneficiary_id is mostly NULL
SELECT * FROM transaction_batch_allocations WHERE beneficiary_id = 68;
```

### Correct Approach:
```sql
-- Join through payment_batches to get all allocations for an owner
SELECT tba.*
FROM transaction_batch_allocations tba
JOIN payment_batches pb ON tba.batch_reference = pb.batch_id
WHERE pb.beneficiary_id = 68;
```

---

## Example: Payment Batch HIST-2025-04-1532

### payment_batches record:
| Field | Value |
|-------|-------|
| batch_id | HIST-2025-04-1532 |
| beneficiary_id | 68 |
| beneficiary_name | Udayan Bhardwaj |
| total_payment | -18,296.25 |
| status | PAID |

### transaction_batch_allocations (31 rows):
| Type | Count | Total | beneficiary_id |
|------|-------|-------|----------------|
| Income (positive) | 30 | +18,496.25 | **NULL** |
| Expense (negative) | 1 | -200.00 | 91 (vendor) |

**Note:** The 30 income allocations have `beneficiary_id = NULL`, NOT 68!

---

## unified_allocations Table

This table stores **PAYMENT SUMMARIES**, not individual allocations.

### allocation_type meanings:
| Type | Purpose | beneficiary_id |
|------|---------|----------------|
| OWNER | Total payment summary to owner | Owner's customer_id |
| EXPENSE | PayProp vendor disbursement | Vendor's ID or NULL |
| COMMISSION | Commission to property manager | Company ID |
| DISBURSEMENT | Other disbursements | Various |

### Example for batch HIST-2025-04-1532:
```
unified_allocations: 1 row
- allocation_type: OWNER
- amount: -18,296.25 (the TOTAL payment, not individual transactions)
- category: "owner_payment"
- description: "Payment to owner"
```

**This is NOT the same as individual rent transactions!**

---

## XLSX Statement Allocation Sheets

### Data Source: `transaction_batch_allocations` ONLY

All three sheets query the same table with different filters:

| Sheet | Filter | Query Join |
|-------|--------|------------|
| Income Allocations | `allocated_amount > 0` | Via payment_batches |
| Expense Allocations | `allocated_amount < 0` | Via payment_batches |
| Owner Payments Summary | Distinct batch references | Via payment_batches |

### Repository Queries (Fixed):
```java
// Income
JOIN PaymentBatch pb ON a.batchReference = pb.batchId
WHERE pb.beneficiaryId = :ownerId AND a.allocatedAmount > 0

// Expenses
JOIN PaymentBatch pb ON a.batchReference = pb.batchId
WHERE pb.beneficiaryId = :ownerId AND a.allocatedAmount < 0
```

---

## Owner 68 (Udayan Bhardwaj) - Verified Data

### Via payment_batches join:
- 15 payment batches
- 186 total allocations
- Income: 157 allocations
- Expenses: 29 allocations

### Direct beneficiary_id query (WRONG):
- Only 3 allocations found (all expenses)

---

## Summary

1. **transaction_batch_allocations** = Individual allocations (rent, expenses)
2. **unified_allocations (OWNER)** = Payment summaries (NOT individual transactions)
3. **Owner lookup** = MUST join through payment_batches, NOT direct beneficiary_id
4. **allocated_amount sign** = Positive for income, negative for expenses

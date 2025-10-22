# PayProp All Payments - Complete Data Structure

**Date:** 2025-10-22
**Table:** `payprop_report_all_payments`
**Records:** 176
**Discovery:** Incoming transaction data IS available nested in owner/commission payments!

---

## CRITICAL FINDING: Incoming Transaction Data IS Available!

The `payprop_report_all_payments` table contains **nested incoming transaction details** that show:
- ✅ When tenant paid
- ✅ How much tenant paid
- ✅ Which tenant paid
- ✅ PayProp transaction ID for the incoming payment

**This data is currently being imported but NOT extracted into separate transactions!**

---

## Complete Field Structure

### Payment Record Fields (Top Level)

| Field | Type | Example | Description |
|-------|------|---------|-------------|
| `payprop_id` | varchar(50) | `0JY92B5BJo` | PayProp payment allocation ID |
| `amount` | decimal(10,2) | `105.00` | Amount allocated (commission or owner share) |
| `description` | text | `Management Fee For Flat 17 - 3 West Gate` | Payment description |
| `due_date` | date | `2025-09-22` | When payment is due |
| `has_tax` | tinyint(1) | `0` | Whether payment includes VAT |
| `reference` | varchar(100) | `` | Payment reference |
| `service_fee` | decimal(10,2) | `6.05` | PayProp service fee charged |
| `transaction_fee` | decimal(10,2) | `0.18` | PayProp transaction fee |
| `tax_amount` | decimal(10,2) | `NULL` | VAT amount if applicable |
| `part_of_amount` | decimal(10,2) | `0.00` | Partial payment amount |

### Beneficiary Fields

| Field | Type | Example (Agency) | Example (Owner) | Description |
|-------|------|------------------|-----------------|-------------|
| `beneficiary_payprop_id` | varchar(50) | `v2XlYPY1em` | `beneficiary_id` | Who receives the payment |
| `beneficiary_name` | varchar(100) | `NULL` | `Udayan Bhardwaj` | Beneficiary name |
| `beneficiary_type` | varchar(50) | `agency` | `beneficiary` | Type: agency or beneficiary (owner) |

### Category Fields

| Field | Type | Example (Commission) | Example (Owner) | Description |
|-------|------|---------------------|-----------------|-------------|
| `category_payprop_id` | varchar(50) | `Kd71e915Ma` | `category_id` | PayProp category ID |
| `category_name` | varchar(100) | `Commission` | `Owner` | Payment category |

---

## Incoming Transaction Fields (NESTED DATA - THE KEY!)

**These fields contain the original tenant→PayProp payment details:**

| Field | Type | Example | Description |
|-------|------|---------|-------------|
| `incoming_transaction_id` | varchar(50) | `QZr6lQoQZN` | **PayProp ID for tenant's payment** |
| `incoming_transaction_amount` | decimal(10,2) | `700.00` | **Full rent amount tenant paid** |
| `incoming_transaction_type` | varchar(100) | `incoming payment` | **Type of incoming payment** |
| `incoming_transaction_status` | varchar(50) | `paid` | **Payment status** |
| `incoming_transaction_reconciliation_date` | date | `2025-09-22` | **When tenant actually paid** ✅ |
| `incoming_transaction_deposit_id` | varchar(50) | `YTP10` | PayProp deposit account ID |

### Incoming Transaction - Property/Tenant Fields

| Field | Type | Example | Description |
|-------|------|---------|-------------|
| `incoming_property_payprop_id` | varchar(50) | `7QZGPmabJ9` | Property where rent came from |
| `incoming_property_name` | text | `Flat 17 - 3 West Gate` | Property name |
| `incoming_tenant_payprop_id` | varchar(50) | `7nZ3YqvrXN` | **Tenant who paid** |
| `incoming_tenant_name` | varchar(100) | `Mr Harsh Patel` | **Tenant name** |

### Batch Payment Fields

| Field | Type | Example | Description |
|-------|------|---------|-------------|
| `payment_batch_id` | varchar(50) | `nZ3Njj3o1N` | Batch payment ID |
| `payment_batch_amount` | decimal(10,2) | `1898.89` | Total batch amount (multiple properties) |
| `payment_batch_status` | varchar(50) | `paid` | Batch status |
| `payment_batch_transfer_date` | date | `2025-10-06` | When landlord received money |

### Payment Instruction Fields

| Field | Type | Example | Description |
|-------|------|---------|-------------|
| `payment_instruction_id` | varchar(50) | `b1gNGabyXG` | Link to payment instruction/rule |

### Secondary Payment Fields

| Field | Type | Example | Description |
|-------|------|---------|-------------|
| `secondary_payment_is_child` | tinyint(1) | `0` | Is this a split payment child? |
| `secondary_payment_is_parent` | tinyint(1) | `0` | Is this a split payment parent? |
| `secondary_payment_parent_id` | varchar(50) | `NULL` | Parent payment ID if child |

### Bank Statement Fields

| Field | Type | Example | Description |
|-------|------|---------|-------------|
| `bank_statement_date` | date | `NULL` | Bank statement date |
| `bank_statement_id` | varchar(50) | `NULL` | Bank statement ID |

### Sync Status Fields

| Field | Type | Example | Description |
|-------|------|---------|-------------|
| `reconciliation_date` | date | `NULL` | Internal reconciliation date |
| `imported_at` | timestamp | `2025-10-20 09:53:33` | When record was imported |
| `sync_status` | enum | `active` | Sync status: active/processed/error |

---

## Real Data Examples

### Example 1: Commission Payment (Agency)

```
Payment Allocation Record:
┌─────────────────────────────────────────────────────────────┐
│ Payment ID: 0JY92B5BJo                                      │
│ Amount: £105.00                                             │
│ Description: Management Fee For Flat 17 - 3 West Gate      │
│ Beneficiary: agency (You)                                   │
│ Category: Commission                                        │
│ Service Fee: £6.05                                          │
│ Transaction Fee: £0.18                                      │
├─────────────────────────────────────────────────────────────┤
│ NESTED: Original Incoming Transaction                       │
│ ├─ ID: QZr6lQoQZN                                          │
│ ├─ Amount: £700.00  ← FULL RENT TENANT PAID               │
│ ├─ Type: incoming payment                                   │
│ ├─ Status: paid                                             │
│ ├─ Reconciliation Date: 2025-09-22  ← WHEN TENANT PAID    │
│ ├─ Property: Flat 17 - 3 West Gate (7QZGPmabJ9)           │
│ └─ Tenant: Mr Harsh Patel (7nZ3YqvrXN)                    │
├─────────────────────────────────────────────────────────────┤
│ Batch Payment Info:                                         │
│ ├─ Batch ID: nZ3Njj3o1N                                    │
│ ├─ Batch Total: £1,898.89 (multiple properties)           │
│ └─ Transfer Date: 2025-10-06  ← WHEN YOU PAID OUT         │
└─────────────────────────────────────────────────────────────┘
```

**Timeline:**
1. **Sep 22, 2025:** Tenant paid £700 (`incoming_transaction_reconciliation_date`)
2. **Sep 22, 2025:** PayProp allocated £105 commission to you
3. **Oct 6, 2025:** PayProp transferred batch payment including this commission

---

### Example 2: Owner Payment (Beneficiary)

```
Payment Allocation Record:
┌─────────────────────────────────────────────────────────────┐
│ Payment ID: eJPmb2BRXG                                      │
│ Amount: £595.00                                             │
│ Description: Landlord Rental Payment                        │
│ Beneficiary: Udayan Bhardwaj (beneficiary)                  │
│ Category: Owner                                             │
│ Transaction Fee: £0.89                                      │
├─────────────────────────────────────────────────────────────┤
│ NESTED: Original Incoming Transaction (SAME AS ABOVE!)      │
│ ├─ ID: QZr6lQoQZN  ← Same incoming payment as commission! │
│ ├─ Amount: £700.00  ← FULL RENT TENANT PAID               │
│ ├─ Type: incoming payment                                   │
│ ├─ Status: paid                                             │
│ ├─ Reconciliation Date: 2025-09-22  ← WHEN TENANT PAID    │
│ ├─ Property: Flat 17 - 3 West Gate (7QZGPmabJ9)           │
│ └─ Tenant: Mr Harsh Patel (7nZ3YqvrXN)                    │
├─────────────────────────────────────────────────────────────┤
│ Batch Payment Info:                                         │
│ ├─ Batch ID: QZGNwwqb19                                    │
│ ├─ Transfer Date: 2025-10-06  ← WHEN OWNER RECEIVED $     │
└─────────────────────────────────────────────────────────────┘
```

**Key Insight:** Both commission and owner payment records reference the SAME `incoming_transaction_id` (QZr6lQoQZN)!

---

## The Critical Relationship

### One Incoming Payment → Multiple Allocations

```
Tenant Payment (Sep 22):
  incoming_transaction_id: QZr6lQoQZN
  incoming_transaction_amount: £700.00
  incoming_transaction_reconciliation_date: 2025-09-22

    ↓ Splits into ↓

Allocation 1 (Commission):
  payprop_id: 0JY92B5BJo
  amount: £105.00
  beneficiary_type: agency
  incoming_transaction_id: QZr6lQoQZN  ← Links back

Allocation 2 (Owner):
  payprop_id: eJPmb2BRXG
  amount: £595.00
  beneficiary_type: beneficiary
  incoming_transaction_id: QZr6lQoQZN  ← Links back
```

**This means:**
- You can GROUP BY `incoming_transaction_id` to reconstruct the original £700 payment
- You can get the EXACT date tenant paid from `incoming_transaction_reconciliation_date`
- You can identify the tenant from `incoming_tenant_payprop_id` and `incoming_tenant_name`

---

## How to Extract Incoming Tenant Payments

### SQL to Create Tenant Payment Records

```sql
-- Extract unique incoming tenant payments from all_payments table
INSERT INTO financial_transactions (
    pay_prop_transaction_id,
    transaction_date,
    amount,
    description,
    category_name,
    transaction_type,
    property_id,
    tenant_id,
    tenant_name,
    data_source,
    created_at
)
SELECT DISTINCT
    ap.incoming_transaction_id as pay_prop_transaction_id,
    ap.incoming_transaction_reconciliation_date as transaction_date,
    ap.incoming_transaction_amount as amount,
    CONCAT('Tenant Rent Payment - ', ap.incoming_property_name, ' - ', ap.incoming_tenant_name) as description,
    'Rent' as category_name,
    ap.incoming_transaction_type as transaction_type,
    ap.incoming_property_payprop_id as property_id,
    ap.incoming_tenant_payprop_id as tenant_id,
    ap.incoming_tenant_name as tenant_name,
    'PAYPROP_INCOMING_EXTRACTED' as data_source,
    NOW() as created_at
FROM payprop_report_all_payments ap
WHERE ap.incoming_transaction_id IS NOT NULL
  AND ap.incoming_transaction_amount IS NOT NULL
  AND ap.incoming_transaction_reconciliation_date IS NOT NULL
  AND NOT EXISTS (
      -- Don't create duplicates
      SELECT 1 FROM financial_transactions ft
      WHERE ft.pay_prop_transaction_id = ap.incoming_transaction_id
  )
ORDER BY ap.incoming_transaction_reconciliation_date;
```

**This will create one transaction per incoming payment, with:**
- Transaction ID: PayProp's incoming transaction ID
- Date: Actual date tenant paid
- Amount: Full rent amount (£700)
- Property: Linked via PayProp property ID
- Tenant: Linked via PayProp tenant ID

---

## Verification Query

### Check if incoming payment links to allocations correctly:

```sql
-- For Flat 17, show incoming payment → allocations relationship
SELECT
    ap.incoming_transaction_id,
    ap.incoming_transaction_reconciliation_date as tenant_paid_date,
    ap.incoming_transaction_amount as full_rent,
    ap.incoming_tenant_name,
    GROUP_CONCAT(
        CONCAT(ap.beneficiary_type, ': £', ap.amount)
        SEPARATOR ' | '
    ) as allocations,
    SUM(ap.amount) as total_allocated
FROM payprop_report_all_payments ap
WHERE ap.incoming_property_payprop_id = '7QZGPmabJ9'
  AND ap.incoming_transaction_reconciliation_date >= '2025-07-01'
GROUP BY
    ap.incoming_transaction_id,
    ap.incoming_transaction_reconciliation_date,
    ap.incoming_transaction_amount,
    ap.incoming_tenant_name
ORDER BY ap.incoming_transaction_reconciliation_date DESC;
```

**Expected output:**
```
incoming_transaction_id | tenant_paid_date | full_rent | allocations                              | total_allocated
─────────────────────────┼──────────────────┼───────────┼──────────────────────────────────────────┼────────────────
QZr6lQoQZN              | 2025-09-22       | 700.00    | agency: £105.00 | beneficiary: £595.00  | 700.00
zZy9NrjkZd              | 2025-08-22       | 700.00    | agency: £105.00 | beneficiary: £595.00  | 700.00
AJ5eYnjaJM              | 2025-07-23       | 700.00    | agency: £105.00 | beneficiary: £595.00  | 700.00
```

**Validates:** Full rent (£700) = Commission (£105) + Owner (£595) ✅

---

## Next Steps

### Immediate Action:

1. **Run the extraction SQL** to create incoming payment transactions
2. **Verify the data** matches expected amounts
3. **Link to leases** using property_id and transaction_date

### Update Sync Service:

4. **Modify `PayPropRawAllPaymentsImportService.java`** to extract incoming transactions
5. **Create separate transaction records** for:
   - Incoming tenant payments (from nested data)
   - Commission allocations (existing)
   - Owner allocations (existing)

### Code Location:

The sync service that imports this data is likely in:
- `PayPropRawAllPaymentsImportService.java`
- `PayPropFinancialSyncService.java`

**Current behavior:** Imports allocation records but ignores nested incoming transaction data

**Needed behavior:** Extract incoming transaction data and create separate rent payment records

---

## Summary

### What We Discovered:

✅ **Incoming tenant payment data IS available** in `payprop_report_all_payments`
✅ **Full details captured:** Amount, date, tenant, property, transaction ID
✅ **Already imported:** 176 records in the table with nested incoming data
❌ **Not extracted:** Sync service doesn't create separate incoming payment transactions

### The Fix:

**Simple:** Extract the nested `incoming_transaction_*` fields and create dedicated rent payment records.

**Impact:** Will show actual tenant payment dates, enabling accurate cash flow tracking and tenant balance calculations.

---

*Analysis completed: 2025-10-22*
*Table: payprop_report_all_payments (176 records)*
*Key finding: Incoming transaction data is nested in allocation records*

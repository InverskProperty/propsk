# PayProp Missing Incoming Payments - Confirmed Analysis

**Date:** 2025-10-22
**Finding:** PayProp is NOT syncing incoming tenant rent payments
**Impact:** Cannot see actual cash flow, only invoicing and disbursements

---

## Confirmation: You Were Right!

After thorough investigation, I can confirm:

**PayProp IS NOT syncing incoming tenant→PayProp rent payments to your database.**

---

## What PayProp Currently Syncs

### ✅ ICDN (Invoices/Bills) - `payprop_report_icdn`
- **172 records**
- **What it is:** Invoices GENERATED, not payments RECEIVED
- **Example:** "Invoice for £700 rent" = tenant OWES £700
- **NOT:** "Tenant PAID £700"

**Synced to:** `financial_transactions` with `data_source = 'ICDN_ACTUAL'`

### ✅ All Payments (Owner Disbursements) - `payprop_report_all_payments`
- **176 records**
- **What it is:** Money PayProp PAYS OUT to landlords
- **Example:** "Paid Udayan Bhardwaj £595"

**Synced to:** `financial_transactions` with `data_source = 'BATCH_PAYMENT'`

### ✅ Export Payments (Payment Instructions) - `payprop_export_payments`
- **66 records**
- **What it is:** RULES for recurring payments
- **Example:** "Pay landlord rental payment on day 10 of each month"
- **NOT:** Actual payment transactions

### ❌ Agency Income - `payprop_report_agency_income`
- **0 records** - Not synced or empty

### ❌ Tenant Statements - `payprop_report_tenant_statement`
- **0 records** - Not synced
- **This is where incoming tenant payments would be!**

### ❌ Tenant Balances - `payprop_report_tenant_balances`
- **0 records** - Not synced

---

## The Missing Data: Incoming Tenant Payments

**What's NOT in your database:**

When Harsh Patel pays £700 rent to PayProp (June 20), this transaction does NOT appear in:
- ❌ `payments` table (empty for all properties)
- ❌ `financial_transactions` table (only has invoices and owner payments)
- ❌ `historical_transactions` table (only has your manual imports)

**The Gap:**
```
Tenant pays £700 → PayProp bank account
    ↓
[MISSING IN DATABASE]
    ↓
PayProp pays out:
  - £595 to landlord ✅ SYNCED
  - £105 to agency  ✅ SYNCED (but from wrong table)
```

---

## Why This Matters

### Current State - What You Can See:

**For Flat 17 in June:**
- ✅ Invoice generated: £700 (ICDN)
- ✅ Landlord received: £595 (BATCH_PAYMENT)
- ✅ Commission earned: £105 (COMMISSION_PAYMENT)

**What you CAN'T see:**
- ❌ When tenant actually paid
- ❌ If tenant paid at all
- ❌ If payment was late
- ❌ Actual cash flow timeline

### Impact on Statements:

Your lease-based statements will show:
- ✅ Rent DUE: £700 (from lease)
- ❌ Rent RECEIVED: Unknown (no payment data)
- ✅ Owner PAID: £595 (from batch payment)
- ✅ Commission EARNED: £105

**The problem:** You can't calculate actual tenant balances/arrears without knowing when they paid.

---

## Root Cause: PayProp Sync Services Not Complete

### Services That ARE Running:

1. **ICDN Import** (`PayPropRawIcdnImportService.java`)
   - Syncs: Invoices, credit notes, debit notes
   - To table: `payprop_report_icdn`
   - Status: ✅ Working (172 records)

2. **All Payments Import** (`PayPropRawAllPaymentsImportService.java`)
   - Syncs: Owner disbursements, batch payments
   - To table: `payprop_report_all_payments`
   - Status: ✅ Working (176 records)

3. **Export Payments Import** (likely `PayPropRawPaymentsImportService.java`)
   - Syncs: Payment instruction rules
   - To table: `payprop_export_payments`
   - Status: ✅ Working (66 records)

### Services That Are NOT Running (or table is empty):

4. **Tenant Statement Import** (`PayPropRawTenantStatementImportService.java`?)
   - Should sync: Individual tenant payment transactions
   - To table: `payprop_report_tenant_statement`
   - Status: ❌ NOT SYNCED (0 records)

5. **Agency Income Import** (`PayPropRawAgencyIncomeImportService.java`)
   - Should sync: Commission details
   - To table: `payprop_report_agency_income`
   - Status: ❌ NOT SYNCED (0 records)

6. **Tenant Balances Import**
   - Should sync: Current tenant arrears/credit
   - To table: `payprop_report_tenant_balances`
   - Status: ❌ NOT SYNCED (0 records)

---

## Solution Options

### Option 1: Enable Tenant Statement Sync (RECOMMENDED)

**Find and enable the tenant statement import service:**

1. Check if `PayPropRawTenantStatementImportService.java` exists
2. If it exists, check why it's not running
3. If it doesn't exist, create it

**Expected API endpoint:** `/report/tenant-statement` or similar

**What it should import:**
```json
{
  "id": "ts_12345",
  "tenant_id": "tenant_abc",
  "property_id": "7QZGPmabJ9",
  "entry_date": "2025-06-20",
  "entry_type": "payment",
  "amount": 700.00,
  "description": "Rent payment - June",
  "paid_date": "2025-06-20",
  "payment_id": "pay_xyz"
}
```

**Then sync to:** `payments` table or create new `tenant_payments` table

---

### Option 2: Derive Incoming Payments from Owner Payments

**Calculate backwards from what PayProp paid out:**

```sql
-- For each owner payment + commission, create derived incoming payment
INSERT INTO financial_transactions (
    transaction_date,
    amount,
    description,
    category_name,
    property_id,
    data_source
)
SELECT
    -- Use the date 2-3 days before owner payment (typical PayProp processing time)
    DATE_SUB(ft_owner.transaction_date, INTERVAL 3 DAY) as transaction_date,

    -- Incoming rent = owner payment + commission
    (ft_owner.amount + ft_commission.amount) as amount,

    CONCAT('Tenant Rent Payment (Derived from disbursement) - ', p.property_name) as description,
    'rent' as category_name,
    ft_owner.property_id,
    'DERIVED_FROM_PAYPROP_DISBURSEMENT' as data_source

FROM financial_transactions ft_owner
INNER JOIN financial_transactions ft_commission ON
    ft_commission.property_id = ft_owner.property_id
    AND ft_commission.transaction_date = ft_owner.transaction_date
    AND ft_commission.data_source = 'COMMISSION_PAYMENT'
INNER JOIN properties p ON p.payprop_id = ft_owner.property_id
WHERE ft_owner.data_source = 'BATCH_PAYMENT'
  AND ft_owner.description LIKE '%Beneficiary:%'
  AND ft_owner.transaction_date >= '2025-06-01'
  AND NOT EXISTS (
      SELECT 1 FROM financial_transactions ft3
      WHERE ft3.property_id = ft_owner.property_id
        AND ft3.transaction_date BETWEEN DATE_SUB(ft_owner.transaction_date, INTERVAL 7 DAY)
                                    AND ft_owner.transaction_date
        AND ft3.category_name = 'rent'
        AND ft3.data_source LIKE '%DERIVED%'
  );
```

**Pros:**
- Can calculate immediately from existing data
- No need to wait for PayProp API

**Cons:**
- Transaction date is estimated (not exact)
- Might miss edge cases (partial payments, arrears)

---

### Option 3: Use ICDN Invoice Date as Proxy

**Treat invoice generation date as payment date:**

```sql
-- Assume invoice date = payment date (optimistic)
INSERT INTO financial_transactions (
    transaction_date,
    amount,
    description,
    category_name,
    property_id,
    tenant_id,
    data_source
)
SELECT
    transaction_date,
    amount,
    description,
    category_name,
    property_payprop_id as property_id,
    tenant_payprop_id as tenant_id,
    'DERIVED_FROM_ICDN' as data_source
FROM payprop_report_icdn
WHERE transaction_type = 'invoice'
  AND category_name = 'Rent'
  AND NOT EXISTS (
      SELECT 1 FROM financial_transactions ft
      WHERE ft.property_id = payprop_report_icdn.property_payprop_id
        AND ft.transaction_date = payprop_report_icdn.transaction_date
        AND ft.category_name = 'rent'
        AND ft.data_source LIKE '%DERIVED%'
  );
```

**Pros:**
- Simple
- Uses PayProp's official billing dates

**Cons:**
- Assumes tenant paid on time (might not be true)
- Doesn't account for late payments or arrears

---

## Recommended Approach

### Immediate (Today):

1. **Check if tenant statement import service exists:**
   ```bash
   ls src/main/java/site/easy/to/build/crm/service/payprop/raw/ | grep -i tenant
   ```

2. **If it exists but not running:**
   - Check scheduler configuration
   - Check if API endpoint is enabled
   - Enable the service

3. **If it doesn't exist:**
   - Use **Option 2** (derive from owner payments) as temporary solution
   - Run the SQL to backfill June-September incoming payments

### Short-term (This Week):

4. **Create tenant statement import service** if missing
5. **Test PayProp API** `/report/tenant-statement` endpoint
6. **Sync historical tenant payments** (last 6 months)

### Long-term (This Month):

7. **Regular sync** of tenant statements (daily)
8. **Reconciliation report** comparing:
   - Rent invoiced (ICDN)
   - Rent received (tenant statements)
   - Rent paid out (owner payments)
9. **Alert on mismatches** (e.g., paid out more than received)

---

## Next Steps - Action Items

### Investigation:

- [ ] Check if `PayPropRawTenantStatementImportService.java` exists
- [ ] Test PayProp API manually: `GET /report/tenant-statement`
- [ ] Check PayProp documentation for tenant payment endpoints

### Quick Fix (Historical Import Feb-May):

- [ ] Run backfill SQL for historical transactions `incoming_transaction_amount`
- [ ] Create missing commission/owner allocation transactions
- [ ] Test Flat 17 statement generation

### PayProp Sync Fix (June onwards):

- [ ] Run Option 2 SQL to derive incoming payments from disbursements
- [ ] Verify derived payments match expected amounts
- [ ] Create service to auto-derive on each PayProp sync

### Future-Proof:

- [ ] Enable tenant statement sync (or create if missing)
- [ ] Create reconciliation dashboard
- [ ] Add alerts for payment discrepancies

---

## Verification Query

After implementing any solution, verify with:

```sql
-- Flat 17 complete transaction history
SELECT
    transaction_date,
    data_source,
    category_name,
    amount,
    description
FROM financial_transactions
WHERE property_id = '7QZGPmabJ9'
  AND transaction_date BETWEEN '2025-06-01' AND '2025-09-30'
ORDER BY transaction_date,
    CASE data_source
        WHEN 'DERIVED_FROM_PAYPROP_DISBURSEMENT' THEN 1
        WHEN 'ICDN_ACTUAL' THEN 2
        WHEN 'COMMISSION_PAYMENT' THEN 3
        WHEN 'BATCH_PAYMENT' THEN 4
    END;
```

**Expected result (per month):**
```
2025-06-20  DERIVED     rent         700.00  Tenant Rent Payment (Derived)
2025-06-21  ICDN_ACTUAL Rent         700.00  Rent For Flat 17... (invoice)
2025-06-21  COMMISSION  Commission   105.00  Commission (15.00%)
2025-06-23  BATCH_PAYMENT NULL       595.00  Beneficiary: Udayan...
2025-06-23  BATCH_PAYMENT NULL       105.00  Beneficiary: Unknown (agency)
```

---

*Investigation completed: 2025-10-22*
*Conclusion: PayProp tenant payment sync is NOT enabled - incoming payments are missing*

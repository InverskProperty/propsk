# Flat 17 Transaction Issues - Root Cause Analysis

**Date:** 2025-10-22
**Property:** Flat 17 - 3 West Gate
**Lease:** LEASE-BH-F17-2025
**Tenant:** HARSH PATEL
**Rent:** £700/month
**Commission:** 15%

---

## The Problem

### Observed Data

**Feb-May 2025 (Historical Import):**
```
20/02/2025  Rent for flat 17 boden    rent  payment  +£700.00  ✅ Present
20/03/2025  rent for flat 17 boden    rent  payment  +£700.00  ✅ Present
20/04/2025  rent for flat 17 boden    rent  payment  +£700.00  ✅ Present
20/05/2025  rent for flat 17 boden    rent  payment  +£700.00  ✅ Present
[MISSING]   Management Fee            commission     -£105.00  ❌ Missing
[MISSING]   Owner Allocation          owner          -£595.00  ❌ Missing
```

**June-Sep 2025 (PayProp Sync):**
```
23/06/2025  Landlord Rental Payment   Owner          +£595.00  ✅ Present
23/06/2025  Management Fee...         Commission     +£105.00  ✅ Present
[MISSING]   Incoming Rent             rent           +£700.00  ❌ Missing
```

---

## Issue #1: Historical Import Not Creating Commission/Owner Payments

### Expected Behavior

When you import a rent payment via CSV:
```csv
property_reference,amount,category,transaction_date,description
FLAT 17 - 3 WEST GATE,700.00,rent,2025-02-20,Rent for flat 17 boden
```

**System should automatically create 3 transactions:**

1. **Incoming Rent Payment** (what you imported)
   - Amount: +£700.00
   - Category: rent
   - Description: "Rent for flat 17 boden"

2. **Management Fee** (auto-generated)
   - Amount: -£105.00 (15% of £700)
   - Category: management_fee
   - Description: "Management Fee - 15% - Flat 17 - 3 West Gate"
   - Linked to incoming transaction

3. **Owner Allocation** (auto-generated)
   - Amount: -£595.00 (£700 - £105)
   - Category: owner_allocation
   - Description: "Owner Allocation - Flat 17 - 3 West Gate"
   - Linked to incoming transaction

### Code That Should Handle This

**File:** `HistoricalTransactionImportService.java`

**Lines 857-860:** Auto-populate `incoming_transaction_amount` for rent payments
```java
if (isRentPayment(transactionType, transaction.getCategory()) && amount.compareTo(BigDecimal.ZERO) > 0) {
    transaction.setIncomingTransactionAmount(amount);  // Triggers split logic
    log.debug("Auto-populated incoming_transaction_amount for rent payment: £{}", amount);
}
```

**Lines 461-466:** Check if split should be created
```java
if (saved.getIncomingTransactionAmount() != null &&
    saved.getIncomingTransactionAmount().compareTo(BigDecimal.ZERO) != 0) {

    log.debug("Creating split transactions for incoming amount: {}", saved.getIncomingTransactionAmount());
    createBeneficiaryAllocationsFromIncoming(saved);  // Creates commission + owner payment
}
```

**Lines 491-557:** Calculate and create commission + owner payment
```java
// Calculate commission (default 15% = 10% management + 5% service)
BigDecimal commissionRate = property.getCommissionPercentage() != null ?
                            property.getCommissionPercentage() :
                            new BigDecimal("15.00");

BigDecimal commissionAmount = incomingAmount
    .multiply(commissionRate)
    .divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);

BigDecimal netDueToOwner = incomingAmount.subtract(commissionAmount);

// Create owner allocation transaction...
// Create management fee transaction...
```

### Why It Might Not Be Working

**Possible Causes:**

1. **`incoming_transaction_amount` not set**
   - CSV might not have this column
   - Auto-population logic not triggered (wrong category or transaction_type)

2. **Property has no commission percentage**
   - `property.commission_percentage` is NULL
   - Code defaults to 15%, so this shouldn't break it

3. **Property has no owner**
   - `getPropertyOwner(property)` returns NULL (line 484)
   - Method exits early with warning (line 486-488)

4. **Transaction category doesn't match**
   - `isRentPayment()` checks for category = "rent", "rental_payment", or "rental"
   - Your imports use "rent" ✅ Should match

5. **Transaction type is wrong**
   - `isRentPayment()` requires `TransactionType.payment` or `TransactionType.invoice`
   - If CSV parser sets different type → Split won't trigger

### Diagnostic Steps

Run the diagnostic query: `FLAT17_DIAGNOSTIC_QUERY.sql`

**Check 1:** Does `historical_transactions` have `incoming_transaction_amount` populated?
```sql
SELECT incoming_transaction_amount FROM historical_transactions
WHERE description LIKE '%flat 17%' AND transaction_date = '2025-02-20';
```
- **If NULL:** Auto-population failed → Bug in CSV parser
- **If £700.00:** Auto-population worked → Bug in split logic

**Check 2:** Does Flat 17 property have an owner assigned?
```sql
SELECT p.property_name, p.commission_percentage, cpa.customer_id, c.name as owner_name
FROM properties p
LEFT JOIN customer_property_assignments cpa ON cpa.property_id = p.id AND cpa.assignment_type = 'OWNER'
LEFT JOIN customers c ON c.customer_id = cpa.customer_id
WHERE p.property_name LIKE '%Flat 17%';
```
- **If no owner:** Split can't happen (no one to allocate payment to)
- **If owner exists:** Should work

**Check 3:** What transaction_type was set during import?
```sql
SELECT transaction_type, category FROM historical_transactions
WHERE description LIKE '%flat 17%' AND transaction_date = '2025-02-20';
```
- **Should be:** `transaction_type = 'payment'`, `category = 'rent'`

---

## Issue #2: PayProp Not Syncing Incoming Rent Payments

### Expected Behavior

When PayProp processes rent for June:

**Tenant pays £700 to PayProp (June 23)**

PayProp should sync 3 transactions:
1. **Incoming rent from tenant:** +£700 (category: rent)
2. **Management fee withheld:** -£105 (category: commission)
3. **Payment to landlord:** -£595 (category: owner_payment)

### Actual Behavior

PayProp only syncs:
1. ❌ Incoming rent: **MISSING**
2. ✅ Management fee: +£105 (Present)
3. ✅ Payment to landlord: +£595 (Present)

### Why This Happens

**PayProp's Data Model:**

PayProp tracks:
- ✅ **Invoices** (what tenants owe)
- ✅ **Payment Instructions** (what to pay landlords)
- ✅ **Reconciled Payments** (actual bank transactions to landlords)
- ❌ **Individual tenant payments?** (might not expose this via API)

**Possible Explanations:**

#### Theory 1: PayProp Doesn't Expose Tenant Payments Via API

PayProp's API might only provide:
- **Outgoing transactions** (what PayProp pays OUT to landlords/suppliers)
- **Commission records** (what PayProp earns)
- **NOT incoming tenant payments** (tenant → PayProp bank account)

**Reasoning:**
- From PayProp's perspective, they're a payment processor
- They care about: "What do I owe landlord?" and "What's my commission?"
- They may not expose: "Which tenant paid £700 on June 23"

#### Theory 2: Incoming Rent is in Different PayProp Table

Your system might be syncing from:
- ✅ `payprop_payment_instructions` → landlord payments
- ✅ `payprop_agency_income` → commission
- ❌ Missing: `payprop_tenant_payments` or `payprop_invoices_paid`

#### Theory 3: Data Source Filter

Your sync might filter by `data_source`:
```java
WHERE data_source IN ('COMMISSION_PAYMENT', 'PAYMENT_INSTRUCTION')
// Missing: 'TENANT_PAYMENT' or 'INVOICE_PAYMENT'
```

### How to Investigate

**Step 1: Check what PayProp tables you're syncing**

```bash
grep -r "payprop_raw" src/main/java/site/easy/to/build/crm/service/payprop/raw/
```

Look for services like:
- `PayPropRawPaymentsImportService` - What does this import?
- `PayPropRawInvoicesImportService` - Does this include paid invoices?
- `PayPropRawTenantsImportService` - Does this include tenant payments?

**Step 2: Check financial_transactions data_source values**

```sql
SELECT DISTINCT data_source, COUNT(*) as count
FROM financial_transactions
GROUP BY data_source;
```

Expected values might be:
- `PAYMENT_INSTRUCTION` - Landlord payments
- `COMMISSION_PAYMENT` - Management fees
- `ICDN_ACTUAL` - Actual bank transactions
- `TENANT_PAYMENT` - Incoming rent? (might be missing)

**Step 3: Check if incoming rent is in a different table**

```sql
-- Check if payments table has tenant payments
SELECT * FROM payments
WHERE property_id = (SELECT id FROM properties WHERE property_name LIKE '%Flat 17%' LIMIT 1)
  AND payment_date BETWEEN '2025-06-01' AND '2025-09-30'
ORDER BY payment_date;
```

**Step 4: Check PayProp raw import tables**

Your system might have raw PayProp data tables:
```sql
SHOW TABLES LIKE 'payprop%';
```

Check if tenant payments are there but not being synced:
```sql
SELECT * FROM payprop_raw_tenant_payments  -- or similar table
WHERE property_id LIKE '%Flat 17%'
  AND payment_date BETWEEN '2025-06-01' AND '2025-09-30';
```

---

## Solutions

### Solution for Issue #1: Historical Import

**Option A: Fix the Import (Recommended)**

Update your CSV to include explicit flag for splitting:
```csv
property_reference,amount,category,transaction_date,description,should_split
FLAT 17 - 3 WEST GATE,700.00,rent,2025-02-20,Rent for flat 17 boden,true
```

Or ensure `incoming_transaction_amount` column is set:
```csv
property_reference,amount,category,incoming_transaction_amount,transaction_date
FLAT 17 - 3 WEST GATE,700.00,rent,700.00,2025-02-20
```

**Option B: Manually Create Missing Transactions**

Run a backfill script:
```sql
-- For each historical rent payment without splits, create commission + owner allocation
INSERT INTO historical_transactions (
    transaction_date, amount, description, category, property_id, customer_id,
    incoming_transaction_id, incoming_transaction_amount, transaction_type, source
)
SELECT
    ht.transaction_date,
    (ht.amount * -0.15) as amount,  -- 15% commission as negative
    CONCAT('Management Fee - 15% - ', p.property_name) as description,
    'management_fee' as category,
    ht.property_id,
    NULL as customer_id,  -- Commission doesn't belong to customer
    ht.id as incoming_transaction_id,
    ht.amount as incoming_transaction_amount,
    'fee' as transaction_type,
    ht.source
FROM historical_transactions ht
INNER JOIN properties p ON p.id = ht.property_id
WHERE ht.category = 'rent'
  AND ht.amount > 0
  AND ht.transaction_date BETWEEN '2025-02-01' AND '2025-05-31'
  AND p.property_name LIKE '%Flat 17%'
  AND NOT EXISTS (
      SELECT 1 FROM historical_transactions ht2
      WHERE ht2.incoming_transaction_id = ht.id::text
        AND ht2.category = 'management_fee'
  );

-- Similar INSERT for owner_allocation with amount = ht.amount * -0.85
```

**Option C: Re-import with Fixed Logic**

1. Delete existing Flat 17 historical transactions (Feb-May)
2. Fix the import service (ensure `incoming_transaction_amount` is set)
3. Re-import the 4 rent payments
4. System should auto-create commission + owner payments

---

### Solution for Issue #2: PayProp Missing Incoming Rent

**Option A: Enable Tenant Payment Sync**

If PayProp has tenant payment data:

1. Find the PayProp table/API endpoint that has tenant payments
2. Create or update sync service to import them:
   ```java
   @Service
   public class PayPropTenantPaymentSyncService {
       public void syncTenantPayments(LocalDate from, LocalDate to) {
           // Fetch from PayProp API: /payments/tenant or similar
           // Import into financial_transactions with category='rent'
       }
   }
   ```

**Option B: Derive Incoming Rent from Invoices**

If PayProp doesn't expose payments:

Calculate incoming rent from landlord payments + commission:
```sql
INSERT INTO financial_transactions (
    transaction_date, amount, description, category_name, property_id, data_source
)
SELECT
    ft_owner.transaction_date,
    (ft_owner.amount + ft_commission.amount) as amount,  -- Reconstruct full rent
    CONCAT('Tenant Rent Payment (Derived) - ', ft_owner.description) as description,
    'rent' as category_name,
    ft_owner.property_id,
    'DERIVED_FROM_PAYPROP' as data_source
FROM financial_transactions ft_owner
INNER JOIN financial_transactions ft_commission ON
    ft_commission.property_id = ft_owner.property_id
    AND ft_commission.transaction_date = ft_owner.transaction_date
    AND ft_commission.category_name = 'commission'
WHERE ft_owner.category_name = 'owner_payment'
  AND ft_owner.transaction_date >= '2025-06-01'
  AND NOT EXISTS (
      SELECT 1 FROM financial_transactions ft3
      WHERE ft3.property_id = ft_owner.property_id
        AND ft3.transaction_date = ft_owner.transaction_date
        AND ft3.category_name = 'rent'
  );
```

**Option C: Accept Current State**

If incoming rent payments aren't critical for your statements:
- Keep current setup (only track outgoing landlord payments + commission)
- Calculate rent due from lease records (Invoice table)
- Compare rent due vs landlord payments received

---

## Next Steps

1. **Run diagnostic query** (`FLAT17_DIAGNOSTIC_QUERY.sql`) to confirm root causes
2. **Check property owner assignment** for Flat 17
3. **Review PayProp sync services** to see what data is being imported
4. **Decide on solution approach** (fix import vs backfill vs accept current state)

---

*Generated: 2025-10-22*
*Related Files:*
- `HistoricalTransactionImportService.java:455-557`
- `FLAT17_DIAGNOSTIC_QUERY.sql`

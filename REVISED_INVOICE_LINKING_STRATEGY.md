# Revised Invoice Linking Strategy
## Based on: Invoices are GENERATED, Transactions are MATCHED

**Date**: 2025-10-28
**Key Insight**: Rent due amounts should be generated from lease terms, not imported. Actual payments should match to leases using stronger identifiers than date.

---

## Current vs New Approach

### OLD APPROACH (What we just implemented)
```
Match transaction to lease by:
1. Property + Date within lease period
2. Optionally: Tenant PayProp ID
```

**Problem**: Strict date checking prevents matching deposits, early payments, late payments

### NEW APPROACH (What you're proposing)

```
Match transaction to lease by PRIORITY:
1. Invoice ID (if transaction has it) → EXACT match
2. Property + Tenant → Find correct lease
   - If only one lease: Use it ✅
   - If multiple leases: Use most recent (closest end date) ✅
3. Property only (fallback) → Use most recent lease
4. Date is INFORMATIONAL only, not a hard constraint
```

**Benefit**: More flexible, handles early/late payments, deposits, multiple leases per tenant

---

## Matching Logic Pseudocode

```java
Invoice findInvoiceForTransaction(Property property,
                                  String tenantPayPropId,
                                  String invoiceId,
                                  LocalDate transactionDate) {

    // PRIORITY 1: Exact invoice ID match (strongest identifier)
    if (invoiceId != null && !invoiceId.isEmpty()) {
        Invoice invoice = findByInvoiceId(invoiceId);
        if (invoice != null) {
            return invoice; // ✅ EXACT MATCH
        }
    }

    // PRIORITY 2: Property + Tenant match (strong identifiers)
    if (property != null && tenantPayPropId != null) {
        List<Invoice> tenantLeases = findByPropertyAndTenant(property, tenantPayPropId);

        if (tenantLeases.size() == 1) {
            return tenantLeases.get(0); // ✅ Only one lease for this tenant
        }

        if (tenantLeases.size() > 1) {
            // Multiple leases: Pick most recent (closest end date to transaction)
            return tenantLeases.stream()
                .sorted(Comparator.comparing(Invoice::getEndDate,
                                           Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst()
                .orElse(null); // ✅ Most recent lease
        }
    }

    // PRIORITY 3: Property only (weakest fallback)
    if (property != null) {
        List<Invoice> propertyLeases = findByProperty(property);

        if (propertyLeases.size() == 1) {
            return propertyLeases.get(0); // ✅ Only one lease for property
        }

        if (propertyLeases.size() > 1) {
            // Multiple leases: Pick most recent
            return propertyLeases.stream()
                .sorted(Comparator.comparing(Invoice::getEndDate,
                                           Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst()
                .orElse(null); // ✅ Most recent lease
        }
    }

    return null; // ❌ No match found
}
```

---

## Effect on Current Unlinked Transactions

Let's see what happens with the 10 remaining transactions:

| Transaction | Property | Tenant Info | Current Matches | New Approach Result |
|-------------|----------|-------------|-----------------|---------------------|
| 1551 | 33 (Flat 18) | Unknown tenant | 2 leases | ✅ Links to most recent lease |
| 1526 | 15 (Flat 28) | Unknown tenant | 1 lease | ✅ Links to only lease |
| 1515 | 24 (Flat 14) | Unknown tenant | 1 lease | ✅ Links to only lease |
| 1484 | 7 (Flat 10) | Unknown tenant | 2 leases | ✅ Links to most recent lease |
| 1492 | 39 (Flat 20) | Unknown tenant | 1 lease | ✅ Links to only lease |
| 1483 | 6 (Flat 9) | Unknown tenant | 1 lease | ✅ Links to only lease |
| 1491 | 43 (Flat 19) | Unknown tenant | 2 leases | ✅ Links to most recent lease |
| 1485 | 17 (Flat 11) | Unknown tenant | 1 lease | ✅ Links to only lease |
| 1494 | 14 (Flat 22) | Unknown tenant | 1 lease | ✅ Links to only lease |
| 1496 | 32 (Flat 26) | Unknown tenant | 1 lease | ✅ Links to only lease |

**Result**: ALL 10 would be linked ✅

**Safe?**
- 7 transactions: Only one lease for property → **100% safe**
- 3 transactions: Multiple leases → Links to most recent → **Probably correct** (deposits for new lease or late payment for old lease)

---

## Spreadsheet Generation Strategy

### For Rent DUE (Expected Rent)

**Generate from lease data, not transactions**

```sql
-- Generate expected rent schedule
SELECT
    i.id as invoice_id,
    i.property_id,
    p.property_name,
    c.name as tenant_name,
    i.start_date,
    i.end_date,
    i.amount as monthly_rent,

    -- Generate month series using calendar table or date functions
    DATE('2025-01-01') + INTERVAL (m.n) MONTH as rent_due_date,
    i.amount as rent_due_amount

FROM invoices i
JOIN properties p ON p.id = i.property_id
JOIN customers c ON c.id = i.customer_id
CROSS JOIN (
    SELECT 0 as n UNION SELECT 1 UNION SELECT 2 UNION ... UNION SELECT 11
) m
WHERE
    -- Only generate for months within lease period
    DATE('2025-01-01') + INTERVAL (m.n) MONTH >= i.start_date
    AND (i.end_date IS NULL OR DATE('2025-01-01') + INTERVAL (m.n) MONTH <= i.end_date)
```

**Excel Formula Approach**:
```
Sheet: Lease Master
- Columns: invoice_id, property, tenant, start_date, end_date, monthly_rent, frequency

Sheet: Rent Due (Generated)
- Formula in "Rent Due Date": =EDATE(LeaseStart, ROW()-1)
- Formula in "Rent Due Amount": =VLOOKUP(invoice_id, LeaseMaster, monthly_rent_col, FALSE)
- Formula in "Pro-rated Amount": =IF(month=start_month, [your pro-rating formula], monthly_rent)
```

### For Rent RECEIVED (Actual Payments)

**Extract from transactions**

```sql
-- Extract actual payments received
SELECT
    ht.id,
    ht.transaction_date as payment_date,
    ht.amount as payment_amount,
    ht.invoice_id,
    i.lease_reference,
    ht.property_id,
    p.property_name,
    ht.customer_id,
    c.name as tenant_name
FROM historical_transactions ht
LEFT JOIN invoices i ON i.id = ht.invoice_id
LEFT JOIN properties p ON p.id = ht.property_id
LEFT JOIN customers c ON c.id = ht.customer_id
WHERE ht.category = 'rent'
    AND ht.transaction_type = 'payment'
    AND ht.transaction_date BETWEEN '2025-01-01' AND '2025-01-31'
ORDER BY ht.transaction_date
```

**Excel Formula to Match Payments to Expected Rent**:
```
Sheet: Rent Received (from transactions)
- Columns: payment_date, invoice_id, property, tenant, payment_amount

Sheet: Arrears Calculation
- Formula: =SUMIFS(RentDue[amount], RentDue[invoice_id], invoice_id, RentDue[date], "<=31/01/2025")
           - SUMIFS(RentReceived[amount], RentReceived[invoice_id], invoice_id, RentReceived[date], "<=31/01/2025")
```

---

## Implementation: Updated PayPropInvoiceLinkingService

### Changes Needed

**File**: `src/main/java/site/easy/to/build/crm/service/payprop/PayPropInvoiceLinkingService.java`

**Current method** (lines 40-90):
```java
public Invoice findInvoiceForTransaction(Property property,
                                        String tenantPayPropId,
                                        LocalDate transactionDate)
```

**New method signature**:
```java
public Invoice findInvoiceForTransaction(Property property,
                                        String tenantPayPropId,
                                        String invoicePayPropId,  // NEW parameter
                                        LocalDate transactionDate)
```

**New logic**:
1. Try invoice ID match first
2. Try property + tenant match (ignore dates)
3. If multiple matches, pick most recent lease by end_date
4. Fall back to property-only match

---

## Migration Strategy

### Phase 1: Update Linking Service (Code)
1. Modify `PayPropInvoiceLinkingService.findInvoiceForTransaction()` to:
   - Accept optional `invoicePayPropId` parameter
   - Remove strict date checking
   - Add "most recent lease" logic for multiple matches
2. Update import services to pass invoice ID (if available)

### Phase 2: Re-run Backfill (Data)
1. Run new backfill with updated logic
2. Should link all 10 remaining transactions
3. Verify matches are correct

### Phase 3: Spreadsheet Generation (Reporting)
1. Create "Lease Master" data extract (lease terms only)
2. Create "Rent Due" generator (Excel formulas from lease terms)
3. Create "Rent Received" data extract (actual transactions)
4. Create "Arrears" calculator (Due - Received)

---

## Benefits of This Approach

### 1. Separation of Concerns ✅
- **Rent DUE** = Calculated from lease agreement
- **Rent RECEIVED** = Imported from actual transactions
- **Arrears** = Comparison of the two

### 2. Handles Real-World Scenarios ✅
- Early payments (deposits)
- Late payments
- Partial payments
- Multiple leases per tenant/property

### 3. More Accurate Matching ✅
- Uses stronger identifiers (invoice ID, tenant, property)
- Date is informational, not restrictive
- Handles sequential leases correctly

### 4. Simpler Spreadsheets ✅
- Generate expected rent in Excel (transparent formulas)
- Import actual payments from database
- Compare the two with SUMIFS

---

## Questions for You

1. **Do PayProp transactions include an invoice reference ID?**
   - If yes, we can use this for exact matching
   - If no, property + tenant matching is sufficient

2. **How do you want to handle deposits?**
   - Link to the lease they're for?
   - Separate category entirely?

3. **Multiple leases for same tenant/property**:
   - Most recent lease = correct approach?
   - Or prefer lease that's active on transaction date?

4. **Should we implement this now?**
   - Update the code
   - Re-run backfill
   - Test with your data

Let me know and I can implement this revised strategy!

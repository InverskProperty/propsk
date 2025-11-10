# Unified Transaction Layer - Architecture Proposal

## Executive Summary

This proposal outlines a redesign of the `unified_transactions` table to create a clean, predictable data layer that separates **base transactions** (actual money movements) from **calculated values** (commissions, net amounts). This will eliminate confusion, prevent double-counting, and create a single source of truth for financial reporting.

---

## Current State Analysis

### Transaction Sources

Currently, `unified_transactions` combines data from:
1. **historical_transactions** (Manual CSV imports)
2. **financial_transactions** (PayProp API sync)

### Current Transaction Types in unified_transactions

#### HISTORICAL Source:
- `rent_received` (INCOMING) - 135 records, £104,968
- `expense` (OUTGOING) - 23 records, £7,223
- `payment_to_beneficiary` (OUTGOING) - 5 records, -£39,760
- `other` (OUTGOING) - 2 records, £1,480

#### PAYPROP Source:
- `incoming_payment` (INCOMING) - 106 records, £88,560
- `commission_payment` (OUTGOING) - 118 records, £12,432 ⚠️
- `payment_to_beneficiary` (OUTGOING) - 76 records, £54,280
- `payment_to_agency` (OUTGOING) - 76 records, £9,314 ⚠️

### Problems Identified

1. **Mixed Base and Calculated Data**
   - `commission_payment` is a **calculated value** (rent × 15%), not a real transaction
   - `payment_to_agency` is an **internal disbursement record**, not a unique transaction
   - These calculated values are being summed alongside real transactions, causing double-counting

2. **Inconsistent Naming**
   - Historical: `rent_received`
   - PayProp: `incoming_payment`
   - Same concept, different names

3. **Unclear Semantics**
   - `payment_to_beneficiary` means different things:
     - Owner payments (net distributions)
     - Sometimes misclassified as expenses (contractor payments)

4. **Commission Double Counting**
   - Property 1 showed £888 commission (£444 commission_payment + £444 payment_to_agency)
   - Both represent the SAME commission, counted twice

5. **Expense Misclassification**
   - Property 1 showed £2,516 in "expenses" that were actually owner payments
   - No way to distinguish owner payments from contractor expenses in unified layer

---

## Proposed Architecture

### Core Principle: Base Transactions Only

The `unified_transactions` table should contain **ONLY base transactions** - actual money movements that happened in the real world.

All derived values (commission, net owed) should be **calculated at the application layer** using consistent business logic.

### Proposed Transaction Types

#### INCOMING Transactions (Money Received)

| Transaction Type | Description | Example |
|-----------------|-------------|---------|
| `rent_received` | Rent payments from tenants | £740 from tenant for monthly rent |
| `owner_contribution` | Capital injection from owner | £5,000 from owner for repairs |
| `deposit_received` | Security deposits | £1,500 deposit for new lease |
| `other_income` | Miscellaneous income | Late fees, pet fees, etc. |

#### OUTGOING Transactions (Money Paid Out)

| Transaction Type | Description | Example |
|-----------------|-------------|---------|
| `expense` | Operational expenses with categories | £200 for plumber repair |
| `owner_payout` | Actual payments made to property owner | £629 paid to owner on 2025-10-06 |

#### Expense Categories (stored in `category` field)

- `maintenance` - Repairs, servicing
- `utilities` - Water, electricity, gas
- `insurance` - Property insurance
- `property_tax` - Council tax, property taxes
- `compliance` - Safety certificates, inspections
- `cleaning` - Cleaning services
- `furnishings` - Furniture, appliances
- `management` - Third-party management fees
- `legal` - Legal costs, eviction proceedings
- `other` - Miscellaneous expenses

#### EXCLUDED Transaction Types (Calculated at App Layer)

These should **NOT** be stored in unified_transactions:
- ❌ `commission_payment` - Calculate from rent × property.commission_percentage
- ❌ `payment_to_agency` - Internal disbursement record
- ❌ `net_owed` - Calculate from formula
- ❌ Any calculated/derived values

---

## Financial Calculation Logic

### Formulas (Application Layer Only)

```
┌─────────────────────────────────────────────────────────────┐
│ Financial Calculation Chain                                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. Total Rent Received (INCOMING: rent_received)          │
│     - SUM of all rent_received transactions                │
│                                                             │
│  2. Total Expenses (OUTGOING: expense)                     │
│     - SUM of all expense transactions                      │
│                                                             │
│  3. Commission (CALCULATED)                                │
│     = Total Rent × (property.commission_percentage / 100)  │
│                                                             │
│  4. Net Owed to Owner (CALCULATED)                         │
│     = Total Rent - Total Expenses - Commission             │
│                                                             │
│  5. Total Owner Payouts Made (OUTGOING: owner_payout)     │
│     - SUM of all owner_payout transactions                 │
│                                                             │
│  6. Amount Still Owed to Owner (CALCULATED)                │
│     = Net Owed - Total Owner Payouts Made                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Example Calculation (Property 1)

```
Rent Received:        £5,180.00  (from transactions)
Expenses:             £0.00      (from transactions)
Commission:           £777.00    (15% of £5,180 - CALCULATED)
────────────────────────────────
Net Owed to Owner:    £4,403.00  (CALCULATED)
Owner Payouts Made:   £2,516.00  (from transactions)
────────────────────────────────
Amount Still Owed:    £1,887.00  (CALCULATED)
```

---

## Implementation Plan

### Phase 1: Update Rebuild Service Mapping

**File**: `UnifiedTransactionRebuildService.java`

#### 1.1 Update PayProp Mapping

**Current (Line 203 in insertFromFinancialTransactions):**
```java
ft.transaction_type as transaction_type
```

**Proposed:**
```sql
CASE
    -- Map PayProp types to unified types
    WHEN ft.data_source = 'INCOMING_PAYMENT'
        THEN 'rent_received'

    WHEN ft.data_source = 'BATCH_PAYMENT'
         AND ft.transaction_type = 'payment_to_beneficiary'
         AND ft.payprop_beneficiary_type = 'beneficiary'
        THEN 'owner_payout'

    WHEN ft.data_source = 'BATCH_PAYMENT'
         AND ft.transaction_type = 'payment_to_beneficiary'
         AND ft.payprop_beneficiary_type != 'beneficiary'
        THEN 'expense'

    -- Exclude calculated/derived transactions
    WHEN ft.transaction_type = 'commission_payment'
        THEN NULL  -- Exclude from unified layer

    WHEN ft.transaction_type = 'payment_to_agency'
        THEN NULL  -- Exclude from unified layer

    ELSE 'other'
END as transaction_type
```

**Add filter:**
```sql
WHERE (ft.invoice_id IS NOT NULL OR ft.data_source = 'INCOMING_PAYMENT')
  AND ft.data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV', 'ICDN_ACTUAL')
  AND ft.transaction_type NOT IN ('commission_payment', 'payment_to_agency')  -- Exclude
```

#### 1.2 Standardize Historical Mapping

**Update historical mapping to use same transaction types:**
```sql
CASE
    WHEN ht.category LIKE '%rent%' OR ht.category LIKE '%Rent%'
        THEN 'rent_received'  -- Standardized

    WHEN ht.category = 'owner_payment'
        THEN 'owner_payout'  -- Standardized

    WHEN ht.category IN ('cleaning', 'furnishings', 'maintenance', 'utilities',
                         'compliance', 'management', 'agency_fee')
        OR ht.category LIKE '%expense%' OR ht.category LIKE '%Expense%'
        THEN 'expense'  -- Keep category in 'category' field

    ELSE 'other'
END as transaction_type
```

### Phase 2: Update Classification Services

**File**: `PropertyFinancialSummaryService.java`

#### Remove Transaction-Based Commission Logic

**Current:**
```java
} else if (isCommissionTransaction(tx)) {
    commissionTxs.add(tx);
    totalCommission = totalCommission.add(tx.getAmount());
}
```

**Keep (Already Fixed):**
```java
} else if (isCommissionTransaction(tx)) {
    // Track for reference only, don't sum
    commissionTxs.add(tx);
}

// Calculate commission from percentage
BigDecimal totalCommission = totalRent
    .multiply(commissionPercentage)
    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
```

#### Update Classification Methods

```java
public boolean isRentTransaction(UnifiedTransaction tx) {
    return tx.getFlowDirection() == FlowDirection.INCOMING
        && "rent_received".equals(tx.getTransactionType());
}

public boolean isExpenseTransaction(UnifiedTransaction tx) {
    return tx.getFlowDirection() == FlowDirection.OUTGOING
        && "expense".equals(tx.getTransactionType());
}

public boolean isOwnerPayoutTransaction(UnifiedTransaction tx) {
    return tx.getFlowDirection() == FlowDirection.OUTGOING
        && "owner_payout".equals(tx.getTransactionType());
}

// Remove isCommissionTransaction() - no longer needed
// Commission is calculated, not summed from transactions
```

### Phase 3: Update Financial Summary DTO

**File**: `PropertyFinancialSummaryService.PropertyFinancialSummary`

Add new fields:
```java
private BigDecimal totalOwnerPayouts = BigDecimal.ZERO;
private BigDecimal amountStillOwed = BigDecimal.ZERO;

// Calculation:
// netToOwner = totalRent - totalExpenses - totalCommission
// amountStillOwed = netToOwner - totalOwnerPayouts
```

### Phase 4: Rebuild Unified Transactions

**Endpoint**: `POST /api/admin/unified-transactions/rebuild`

1. Run complete rebuild with new mapping logic
2. Verify transaction counts before/after
3. Verify financial calculations are correct
4. Test all statement generation and reports

---

## Migration Steps

### Step 1: Backup Current State
```sql
CREATE TABLE unified_transactions_backup_20250110 AS
SELECT * FROM unified_transactions;
```

### Step 2: Deploy Code Changes
1. Update `UnifiedTransactionRebuildService.java`
2. Update `PropertyFinancialSummaryService.java`
3. Update `UnifiedFinancialDataService.java` (already done)
4. Deploy to staging environment

### Step 3: Test Rebuild
1. Call rebuild endpoint: `POST /api/admin/unified-transactions/rebuild`
2. Verify transaction counts:
   ```sql
   -- Should have NO commission_payment or payment_to_agency
   SELECT transaction_type, COUNT(*)
   FROM unified_transactions
   GROUP BY transaction_type;
   ```

### Step 4: Validate Calculations
1. Compare Property 1 before/after
2. Verify commission = rent × 15%
3. Verify expenses exclude owner payouts
4. Verify net calculations are correct

### Step 5: Deploy to Production
1. Deploy code to production
2. Run rebuild during low-traffic period
3. Monitor financial reports for accuracy

---

## Expected Changes for Property 1

### Before (Current State)
```
unified_transactions for Property 1:
- incoming_payment (INCOMING): 7 records, £5,180
- commission_payment (OUTGOING): 4 records, £444  ⚠️ Remove
- payment_to_agency (OUTGOING): 4 records, £444   ⚠️ Remove
- payment_to_beneficiary (OUTGOING): 4 records, £2,516

Financial Summary:
- Rent: £5,180
- Expenses: £2,516 (wrong - these are owner payouts)
- Commission: £888 (wrong - double counting)
- Net: £1,776 (wrong)
```

### After (Proposed State)
```
unified_transactions for Property 1:
- rent_received (INCOMING): 7 records, £5,180
- owner_payout (OUTGOING): 4 records, £2,516
(commission_payment and payment_to_agency excluded)

Financial Summary (Calculated):
- Rent: £5,180
- Expenses: £0
- Commission: £777 (15% of £5,180 - calculated)
- Net Owed: £4,403
- Owner Payouts Made: £2,516
- Amount Still Owed: £1,887
```

---

## Benefits

### 1. **Single Source of Truth**
- One clear place to look for actual money movements
- No confusion between base and calculated values

### 2. **Prevents Double Counting**
- Commission calculated once from percentage
- No more summing commission_payment + payment_to_agency

### 3. **Predictable and Fixed**
- Transaction types are standardized
- Mapping logic is explicit and documented
- Easy to understand what each transaction represents

### 4. **Easy to Extend**
- Adding new sources (Xero, QuickBooks) becomes straightforward
- Just map their transaction types to our unified types

### 5. **Accurate Financial Reporting**
- Expenses are actual expenses, not owner payouts
- Commission reflects actual business rules (percentage-based)
- Net calculations are transparent and auditable

### 6. **Simpler Application Logic**
- Classification methods become simple type checks
- No complex string matching or description parsing
- Calculation formulas are clear and in one place

---

## Risks and Mitigation

### Risk 1: Data Loss During Migration
**Mitigation**:
- Backup unified_transactions before rebuild
- Test on staging first
- Keep source tables (financial_transactions, historical_transactions) intact

### Risk 2: Breaking Existing Reports
**Mitigation**:
- Inventory all places that use unified_transactions
- Test all statement generation endpoints
- Run parallel calculations (old vs new) during transition

### Risk 3: Historical Data Incompatibility
**Mitigation**:
- Historical transactions may not have clear beneficiary_type
- Use description-based heuristics as fallback
- Document any edge cases

---

## Open Questions

1. **Should we keep commission_payment records for audit?**
   - Option A: Exclude completely from unified layer ✓ (Recommended)
   - Option B: Keep but add `is_calculated` flag to distinguish

2. **How to handle ICDN_ACTUAL transactions?**
   - Currently excluded (line 220: `ft.data_source NOT IN ('ICDN_ACTUAL')`)
   - Should we include as calculated reference data?

3. **Should we add `is_reconciled` flag?**
   - Track whether transactions have been matched/verified
   - Useful for future reconciliation features

4. **Need beneficiary_type in unified_transactions?**
   - Currently not copied from financial_transactions
   - Would help distinguish owner vs contractor payments
   - Recommend adding to UnifiedTransaction entity

---

## Success Criteria

✅ **Rebuild completes without errors**

✅ **No commission_payment or payment_to_agency in unified_transactions**

✅ **All rent transactions use `rent_received` type**

✅ **Owner payouts use `owner_payout` type and don't count as expenses**

✅ **Property 1 calculations match expected values:**
   - Rent: £5,180
   - Commission: £777 (15%)
   - Expenses: £0
   - Net Owed: £4,403
   - Still Owed: £1,887

✅ **All financial reports and statements generate correctly**

✅ **Statement generation still works for all properties**

---

## Timeline Estimate

- **Phase 1** (Rebuild Service): 4 hours
- **Phase 2** (Classification Logic): 2 hours
- **Phase 3** (DTO Updates): 2 hours
- **Phase 4** (Testing): 4 hours
- **Migration & Deployment**: 2 hours

**Total**: ~14 hours (2 working days)

---

## Approval Required

- [ ] Business logic confirmed (commission calculation, net owed formula)
- [ ] Expected financial values validated for test properties
- [ ] Migration plan approved
- [ ] Deployment window scheduled

---

## Document Version

- **Version**: 1.0
- **Date**: 2025-01-10
- **Author**: Claude Code
- **Status**: Proposal - Awaiting Approval

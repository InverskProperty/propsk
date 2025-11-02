# Expense Handling Analysis & Implementation Plan

## Date: 2025-11-02

## Executive Summary

**CRITICAL FINDING:** Expenses are NOT properly handled across historical and PayProp data sources in the unified_transactions table.

- **Historical expenses:** 24 out of 25 expense records NOT imported (96% missing!)
- **PayProp expenses:** 52 credit_note/debit_note records NOT imported (100% missing!)
- **Current state:** Only 1 expense in unified_transactions (from 77 total across both sources)

---

## Current State Analysis

### 1. Historical Expenses (historical_transactions table)

**Total expense records found:** 25

**Categories:**
| Category | Count | Total Amount | Status in unified_transactions |
|----------|-------|--------------|-------------------------------|
| cleaning | 7 | £1,400.00 | ❌ NOT imported |
| furnishings | 8 | £1,530.41 | ❌ NOT imported |
| maintenance | 2 | £380.01 | ❌ NOT imported |
| utilities | 2 | £288.38 | ❌ NOT imported |
| compliance | 1 | £273.84 | ❌ NOT imported |
| management | 5 | £880.00 | ⚠️ Only 1 imported |

**Total historical expenses:** £4,752.64

**Sample records:**
```
CLEANING:
  2025-07-22 | £-200.00 | End of Tenancy Clean
  2025-08-22 | £-200.00 | End of Tenancy Clean

FURNISHINGS:
  2025-07-22 | £-229.94 | Ceramic Hob
  2025-07-22 | £-250.00 | White Goods
  2025-07-22 | £-58.99  | Bed

MAINTENANCE:
  2025-05-22 | £-115.01 | Repairs
  2025-06-22 | £-265.00 | Maintenance

UTILITIES:
  2025-03-22 | £-144.19 | Council Tax

COMPLIANCE:
  2025-05-22 | £-273.84 | Fire Safety Equipment
```

**Problem:** The import logic only looks for category LIKE '%expense%':
```sql
CASE
    WHEN ht.category LIKE '%rent%' THEN 'rent_received'
    WHEN ht.category LIKE '%expense%' THEN 'expense'  -- ❌ Never matches!
    ELSE 'other'
END
```

None of the historical expense categories contain the word "expense", so they all fall into the "other" bucket and are classified as OUTGOING with transaction_type='other' instead of 'expense'.

### 2. PayProp Expenses (financial_transactions table)

**Total credit_note/debit_note records:** 52
- credit_note: 38 records (£24,614.02)
- debit_note: 14 records (£8,649.11)

**Status:** ❌ **ZERO imported into unified_transactions**

**Problem:** All credit_note/debit_note records have data_source='ICDN_ACTUAL' which is explicitly excluded:
```sql
WHERE (ft.invoice_id IS NOT NULL OR ft.data_source = 'INCOMING_PAYMENT')
  AND ft.data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV', 'ICDN_ACTUAL')  -- ❌ Excludes all notes!
```

**Sample records:**
```
credit_note | 2025-10-06 | £20.00  | rent adjustment September | Category: Other
debit_note  | 2025-10-05 | £382.00 | Rent 21st Sept to 5th Oct | Category: Rent
credit_note | 2025-09-19 | £740.00 | paid via old system 10/09 | Category: Rent
```

**Note:** These aren't all expenses! Many are rent adjustments or corrections. Need to filter by category.

### 3. unified_transactions Current State

**Transaction types present:**
| transaction_type | flow_direction | Count | Total Amount |
|-----------------|----------------|-------|--------------|
| incoming_payment | INCOMING | 106 | £88,560.39 |
| rent_received | INCOMING | 135 | £104,968.00 |
| commission_payment | OUTGOING | 118 | £12,432.00 |
| payment_to_agency | OUTGOING | 76 | £9,314.16 |
| payment_to_beneficiary | OUTGOING | 76 | £54,280.23 |
| **other** | OUTGOING | **3** | **£1,320.00** |

**No 'expense' transaction_type exists!**

The 3 "other" transactions include:
1. Historical rent payment (not an expense)
2. Management fee (£-160) - this is the ONE expense that made it through
3. Another rent payment (not an expense)

---

## Problem Root Causes

### Issue 1: Historical Expense Category Mismatch
**File:** `UnifiedTransactionRebuildService.java:133-136`

**Current logic:**
```java
CASE
    WHEN ht.category LIKE '%rent%' OR ht.category LIKE '%Rent%' THEN 'rent_received'
    WHEN ht.category LIKE '%expense%' OR ht.category LIKE '%Expense%' THEN 'expense'
    ELSE 'other'
END
```

**Actual expense categories:**
- cleaning
- furnishings
- maintenance
- utilities
- compliance
- management
- agency_fee

**None contain "expense"!**

### Issue 2: PayProp Expenses Excluded by data_source Filter
**File:** `UnifiedTransactionRebuildService.java:198-199`

**Current logic:**
```sql
WHERE (ft.invoice_id IS NOT NULL OR ft.data_source = 'INCOMING_PAYMENT')
  AND ft.data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV', 'ICDN_ACTUAL')
```

**Problem:** All credit_note/debit_note have data_source='ICDN_ACTUAL'

### Issue 3: No Expense Classification in PayProp Import
Even if we include ICDN_ACTUAL, the logic doesn't classify expenses:
```java
ft.transaction_type as transaction_type,  // Just passes through "credit_note" or "debit_note"
CASE
    WHEN ft.data_source = 'INCOMING_PAYMENT' THEN 'INCOMING'
    WHEN ft.data_source = 'BATCH_PAYMENT' OR ft.data_source = 'COMMISSION_PAYMENT' THEN 'OUTGOING'
    ELSE 'OUTGOING'
END
```

No logic to map credit_note/debit_note to 'expense' based on category.

---

## Proposed Solution

### Phase 1: Fix Historical Expense Import (Quick Win)

**Update:** `UnifiedTransactionRebuildService.java:132-140`

**From:**
```sql
CASE
    WHEN ht.category LIKE '%rent%' OR ht.category LIKE '%Rent%' THEN 'rent_received'
    WHEN ht.category LIKE '%expense%' OR ht.category LIKE '%Expense%' THEN 'expense'
    ELSE 'other'
END as transaction_type,
CASE
    WHEN ht.category LIKE '%rent%' OR ht.category LIKE '%Rent%' THEN 'INCOMING'
    ELSE 'OUTGOING'
END as flow_direction
```

**To:**
```sql
CASE
    WHEN ht.category LIKE '%rent%' OR ht.category LIKE '%Rent%' THEN 'rent_received'
    WHEN ht.category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee')
        OR ht.category LIKE '%expense%' OR ht.category LIKE '%Expense%' THEN 'expense'
    WHEN ht.category = 'owner_payment' THEN 'payment_to_beneficiary'
    ELSE 'other'
END as transaction_type,
CASE
    WHEN ht.category LIKE '%rent%' OR ht.category LIKE '%Rent%' THEN 'INCOMING'
    WHEN ht.category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee')
        OR ht.category LIKE '%expense%' OR ht.category LIKE '%Expense%' THEN 'OUTGOING'
    WHEN ht.category = 'owner_payment' THEN 'OUTGOING'
    ELSE 'OUTGOING'
END as flow_direction
```

**Impact:** Will correctly import 24 additional historical expense records (£3,872)

### Phase 2: Add PayProp Expense Import (Medium Complexity)

**Option A: Include ICDN_ACTUAL with category filtering**

Update line 198-199:
```sql
WHERE (ft.invoice_id IS NOT NULL
    OR ft.data_source = 'INCOMING_PAYMENT'
    OR (ft.data_source = 'ICDN_ACTUAL' AND ft.category_name NOT IN ('Rent', 'Other')))
  AND ft.data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
```

Add expense classification logic (line 187):
```sql
CASE
    WHEN ft.transaction_type IN ('credit_note', 'debit_note')
        AND ft.category_name NOT IN ('Rent', 'Other') THEN 'expense'
    ELSE ft.transaction_type
END as transaction_type
```

**Option B: Create separate expense import method**

Add new method:
```java
private int insertExpensesFromFinancialTransactions(String batchId) {
    String sql = """
        INSERT INTO unified_transactions (...)
        SELECT ...
        FROM financial_transactions ft
        WHERE ft.data_source = 'ICDN_ACTUAL'
          AND ft.transaction_type IN ('credit_note', 'debit_note')
          AND ft.category_name NOT IN ('Rent', 'Other')
        """;
    return jdbcTemplate.update(sql, batchId);
}
```

Call in `rebuildAll()`:
```java
int expenseCount = insertExpensesFromFinancialTransactions(batchId);
result.put("expenseRecords", expenseCount);
```

**Recommendation:** Option B (separate method) is cleaner and easier to debug.

### Phase 3: Add Expense Extraction to Statement Services

**Add to:** `StatementDataExtractService.java`

```java
/**
 * Extract expense details for a lease in a date range
 * Returns all OUTGOING transactions with transaction_type = 'expense'
 */
public List<PaymentDetailDTO> extractExpenseDetails(
        Long invoiceId, LocalDate startDate, LocalDate endDate) {
    return extractPaymentDetails(invoiceId, startDate, endDate,
        UnifiedTransaction.FlowDirection.OUTGOING,
        "expense");  // Filter by transaction_type
}

/**
 * Generic payment detail extraction with transaction_type filter
 */
public List<PaymentDetailDTO> extractPaymentDetails(
        Long invoiceId, LocalDate startDate, LocalDate endDate,
        UnifiedTransaction.FlowDirection flowDirection,
        String transactionType) {

    List<UnifiedTransaction> transactions = unifiedTransactionRepository
        .findByInvoiceIdAndTransactionDateBetweenAndFlowDirectionAndTransactionType(
            invoiceId, startDate, endDate, flowDirection, transactionType);

    // Map to PaymentDetailDTO and sort
    List<PaymentDetailDTO> details = new ArrayList<>();
    for (UnifiedTransaction txn : transactions) {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentDate(txn.getTransactionDate());
        detail.setAmount(txn.getAmount());
        detail.setDescription(txn.getDescription());
        detail.setCategory(txn.getCategory());
        detail.setTransactionId(txn.getId());
        details.add(detail);
    }

    details.sort((a, b) -> a.getPaymentDate().compareTo(b.getPaymentDate()));
    return details;
}
```

**Add repository method:**
```java
// UnifiedTransactionRepository.java
List<UnifiedTransaction> findByInvoiceIdAndTransactionDateBetweenAndFlowDirectionAndTransactionType(
    Long invoiceId,
    LocalDate startDate,
    LocalDate endDate,
    UnifiedTransaction.FlowDirection flowDirection,
    String transactionType
);
```

### Phase 4: Add Expense Sheet to Excel Statements

**Add to:** `ExcelStatementGeneratorService.java`

```java
private void createExpensesSheet(XSSFWorkbook workbook, ...) {
    XSSFSheet sheet = workbook.createSheet("EXPENSES");

    // Header row
    String[] headers = {
        "lease_id", "lease_reference", "property_name",
        "month_start", "month_end",
        "expense_1_date", "expense_1_amount", "expense_1_category",
        "expense_2_date", "expense_2_amount", "expense_2_category",
        "expense_3_date", "expense_3_amount", "expense_3_category",
        "expense_4_date", "expense_4_amount", "expense_4_category",
        "total_expenses"
    };

    // ... populate with expense data from extractExpenseDetails()
}
```

Update MONTHLY_STATEMENT to include expense column with formula:
```java
Cell expenseCell = row.createCell(columnIndex);
expenseCell.setCellFormula(String.format(
    "SUMIFS(EXPENSES!P:P, EXPENSES!A:A, %d, EXPENSES!D:D, D%d)",
    lease.getLeaseId(), rowNum + 1
));
```

---

## Implementation Checklist

### ✅ Completed
- [x] Analyze historical expense handling
- [x] Analyze PayProp expense handling
- [x] Identify root causes
- [x] Document current state

### 🔲 To Do

**Phase 1: Historical Expenses (Priority 1)**
- [ ] Update UnifiedTransactionRebuildService.java historical expense logic
- [ ] Test with rebuild to verify 25 expense records imported
- [ ] Verify transaction_type='expense' and flow_direction='OUTGOING'

**Phase 2: PayProp Expenses (Priority 2)**
- [ ] Add insertExpensesFromFinancialTransactions() method
- [ ] Update rebuildAll() to call new method
- [ ] Test import of credit_note/debit_note as expenses
- [ ] Verify category filtering (exclude Rent/Other)

**Phase 3: Service Layer (Priority 3)**
- [ ] Add extractExpenseDetails() to StatementDataExtractService
- [ ] Add repository method for expense queries
- [ ] Unit test expense extraction

**Phase 4: Excel Generation (Priority 4)**
- [ ] Create EXPENSES sheet in ExcelStatementGeneratorService
- [ ] Add expense breakdown columns (4 expenses max)
- [ ] Update MONTHLY_STATEMENT to include expense totals
- [ ] Test full statement generation with expenses

---

## Expected Results After Fix

### Unified Transactions Breakdown:
| transaction_type | flow_direction | Current Count | After Fix |
|-----------------|----------------|---------------|-----------|
| incoming_payment | INCOMING | 106 | 106 (no change) |
| rent_received | INCOMING | 135 | 135 (no change) |
| commission_payment | OUTGOING | 118 | 118 (no change) |
| payment_to_agency | OUTGOING | 76 | 76 (no change) |
| payment_to_beneficiary | OUTGOING | 76 | 89 (+13 owner_payment) |
| **expense** | **OUTGOING** | **0** | **~50** (+25 historical, +25 PayProp) |
| other | OUTGOING | 3 | 2 (-1 moved to expense) |

### Statement Impact:
- RENT_RECEIVED sheet: No change (still INCOMING only) ✅
- NEW EXPENSES sheet: Will show expense breakdown by date/category
- MONTHLY_STATEMENT: New "Total Expenses" column with formula
- Net to Owner calculation: rent_received - commission - expenses - fees

---

## Questions for User

1. **Expense categories:** Should we include all these categories as expenses, or exclude any?
   - cleaning
   - furnishings
   - maintenance
   - utilities
   - compliance
   - management

2. **PayProp credit_note/debit_note:** Should we:
   - Import ALL and filter by category (exclude Rent/Other)?
   - Only import specific categories?
   - Manual review before import?

3. **Negative amounts:** Historical expenses have negative amounts (e.g., £-200.00). Should we:
   - Keep negative in database?
   - Display as positive in reports?
   - Use ABS() in expense totals?

4. **Priority:** Should we implement all 4 phases or just Phase 1 (historical expenses) first?

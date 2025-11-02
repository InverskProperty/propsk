# Expense Implementation - Phase 1 & 2 Complete

## Date: 2025-11-02

## Status: ✅ Phases 1 & 2 Implemented and Compiled Successfully

---

## Phase 1: Database & Import Layer ✅ COMPLETE

### Changes Made

**File:** `UnifiedTransactionRebuildService.java`

**Updated:** Historical transaction import logic (2 methods)

**Before:**
```java
CASE
    WHEN ht.category LIKE '%rent%' THEN 'rent_received'
    WHEN ht.category LIKE '%expense%' THEN 'expense'  // Never matched!
    ELSE 'other'
END
```

**After:**
```java
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

**Impact:**
- ✅ 25 historical expense records will now be correctly imported
- ✅ transaction_type set to 'expense' instead of 'other'
- ✅ flow_direction set to 'OUTGOING'
- ✅ 13 owner_payment records will be classified as 'payment_to_beneficiary'

**Methods Updated:**
1. `insertFromHistoricalTransactions()` - Line 132-145
2. `insertUpdatedHistoricalTransactions()` - Line 329-342

---

## Phase 2: Service Layer ✅ COMPLETE

### Changes Made

**File 1:** `UnifiedTransactionRepository.java`

**Added:** New repository query method

```java
/**
 * Find transactions by invoice ID, date range, flow direction, and transaction type
 * Use this for extracting specific transaction types for a lease (e.g., expenses only)
 */
List<UnifiedTransaction> findByInvoiceIdAndTransactionDateBetweenAndFlowDirectionAndTransactionType(
    Long invoiceId,
    LocalDate startDate,
    LocalDate endDate,
    UnifiedTransaction.FlowDirection flowDirection,
    String transactionType
);
```

**File 2:** `StatementDataExtractService.java`

**Added:** New expense extraction method

```java
/**
 * Extract expense details for a specific lease and date range
 * Returns only OUTGOING transactions with transaction_type = 'expense'
 */
public List<PaymentDetailDTO> extractExpenseDetails(
        Long invoiceId, LocalDate startDate, LocalDate endDate) {

    log.info("Extracting expense details for lease {} from {} to {}",
        invoiceId, startDate, endDate);

    List<UnifiedTransaction> transactions = unifiedTransactionRepository
        .findByInvoiceIdAndTransactionDateBetweenAndFlowDirectionAndTransactionType(
            invoiceId, startDate, endDate,
            UnifiedTransaction.FlowDirection.OUTGOING,
            "expense");

    List<PaymentDetailDTO> expenseDetails = new ArrayList<>();

    for (UnifiedTransaction txn : transactions) {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentDate(txn.getTransactionDate());
        detail.setAmount(txn.getAmount());
        detail.setDescription(txn.getDescription());
        detail.setCategory(txn.getCategory());
        detail.setTransactionId(txn.getId());
        expenseDetails.add(detail);
    }

    // Sort by date ascending
    expenseDetails.sort((a, b) -> a.getPaymentDate().compareTo(b.getPaymentDate()));

    log.info("Extracted {} expense details for lease {}", expenseDetails.size(), invoiceId);
    return expenseDetails;
}
```

---

## Phase 3: Excel Generation Layer ⏳ PENDING

### What Needs To Be Done

**File:** `ExcelStatementGeneratorService.java`

**Required Changes:**

1. **Create EXPENSES sheet** (similar to RENT_RECEIVED)
   - Columns: lease_id, lease_reference, property_name, month_start, month_end
   - 4 expense columns: expense_1_date, expense_1_amount, expense_1_category (repeat x4)
   - total_expenses column

2. **Update MONTHLY_STATEMENT sheet**
   - Add "Total Expenses" column
   - Add formula: `SUMIFS(EXPENSES!M:M, EXPENSES!A:A, lease_id, EXPENSES!D:D, month_start)`

3. **Call createExpensesSheet() in all 4 generation methods:**
   - `generateStatement()`
   - `generateStatementForCustomer()`
   - `generateStatementWithCustomPeriods()`
   - `generateStatementForCustomerWithCustomPeriods()`

### Implementation Complexity

**Estimated changes:** ~200-300 lines of code
**Files affected:** 1 (ExcelStatementGeneratorService.java)
**Time estimate:** 30-45 minutes

---

## Testing Plan

### Step 1: Rebuild Unified Transactions
```bash
# Run rebuild to import expenses
curl -X POST http://localhost:8080/api/unified-transactions/rebuild
```

**Expected result:**
- Historical records: 138 → 138 (same count, but 25 now classified as 'expense')
- transaction_type='expense': 0 → 25
- transaction_type='other': 3 → 2

### Step 2: Verify Expense Data
```sql
SELECT transaction_type, category, COUNT(*), SUM(amount)
FROM unified_transactions
WHERE transaction_type = 'expense'
GROUP BY transaction_type, category;
```

**Expected result:**
```
expense | cleaning      | 7  | £-1400.00
expense | furnishings   | 8  | £-1530.41
expense | maintenance   | 2  | £-380.01
expense | utilities     | 2  | £-288.38
expense | compliance    | 1  | £-273.84
expense | management    | 4  | £-720.00
```

### Step 3: Generate Statement (After Phase 3)
```bash
curl http://localhost:8080/api/statements/owner/1/excel?year=2025&month=7
```

**Expected:**
- RENT_RECEIVED sheet: Shows INCOMING only ✅ (already working)
- **NEW** EXPENSES sheet: Shows expense breakdown by date/category
- MONTHLY_STATEMENT: Shows expense totals per lease

---

## Files Modified

### Phase 1:
- `src/main/java/site/easy/to/build/crm/service/transaction/UnifiedTransactionRebuildService.java`

### Phase 2:
- `src/main/java/site/easy/to/build/crm/repository/UnifiedTransactionRepository.java`
- `src/main/java/site/easy/to/build/crm/service/statement/StatementDataExtractService.java`

### Phase 3 (Pending):
- `src/main/java/site/easy/to/build/crm/service/statement/ExcelStatementGeneratorService.java`

---

## Compilation Status

✅ **Phase 1 & 2:** BUILD SUCCESS

All changes compiled without errors.

---

## Next Steps

1. ✅ Commit Phase 1 & 2 changes
2. ⏳ Implement Phase 3 (EXPENSES sheet in Excel)
3. ⏳ Test rebuild with expense import
4. ⏳ Generate and verify statement with expenses

---

## Notes

- PayProp expenses: Not needed (ICDN_ACTUAL excluded, as confirmed by user)
- Historical expenses: 25 records (£4,752.64 total)
- Expense categories: cleaning, furnishings, maintenance, utilities, compliance, management
- All expenses are OUTGOING transactions


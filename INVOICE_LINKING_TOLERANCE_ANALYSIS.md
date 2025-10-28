# Invoice Linking Date Tolerance Analysis

## Problem
10 rent payments (7.4%) cannot be linked because they fall outside lease date ranges.

## Current Constraint
```java
// Transaction must be on or after lease start
if (startDate != null && transactionDate.isBefore(startDate)) {
    return false;
}

// Transaction must be before lease end (if end date exists)
if (endDate != null && transactionDate.isAfter(endDate)) {
    return false;
}
```

## Options

### Option 1: Remove Date Constraint Entirely ❌ NOT RECOMMENDED

**Effect**: Would link all 10 transactions
**Risk**: 4 transactions have multiple matching leases - would link to WRONG lease

**Critical Issues**:
- Transaction 1551 (2025-05-15) falls in GAP between two leases for Property 33
- Would link to most recent lease, which hasn't started yet
- Different tenants could get mixed up

### Option 2: Add Before-Lease Tolerance Window ✅ RECOMMENDED

**Allow transactions up to 14-30 days BEFORE lease start**

```java
// Allow transactions within tolerance window before lease start
int BEFORE_LEASE_TOLERANCE_DAYS = 14; // or 30

if (startDate != null) {
    LocalDate earliestAllowed = startDate.minusDays(BEFORE_LEASE_TOLERANCE_DAYS);
    if (transactionDate.isBefore(earliestAllowed)) {
        return false;
    }
}

// Keep strict constraint for after lease end
if (endDate != null && transactionDate.isAfter(endDate)) {
    return false;
}
```

**Effect with 14-day tolerance**:
- Would link: 6 transactions (within 14 days before lease)
- Would NOT link: 4 transactions (more than 14 days before)

**Effect with 30-day tolerance**:
- Would link: 9 transactions
- Would NOT link: 1 transaction (26 days before)

**Breakdown**:

| Transaction | Date | Days Before Lease | 14-day? | 30-day? | Safe? |
|-------------|------|-------------------|---------|---------|-------|
| 1494 | 2025-02-28 | 1 day | ✅ | ✅ | ✅ Safe |
| 1515 | 2025-03-26 | 2 days | ✅ | ✅ | ✅ Safe |
| 1485 | 2025-03-02 | 4 days | ✅ | ✅ | ✅ Safe |
| 1484 | 2025-03-05 | 1 day | ✅ | ✅ | ✅ Safe |
| 1492 | 2025-03-05 | 11 days | ✅ | ✅ | ✅ Safe |
| 1491 | 2025-03-03 | 11 days | ✅ | ✅ | ⚠️ Has 2 matches |
| 1496 | 2025-02-28 | 14 days | ✅ | ✅ | ✅ Safe |
| 1483 | 2025-03-03 | 17 days | ❌ | ✅ | ✅ Safe |
| 1526 | 2025-03-31 | 18 days | ❌ | ✅ | ✅ Safe |
| 1551 | 2025-05-15 | 26 days | ❌ | ❌ | 🔴 Gap between leases |

### Option 3: Manual Review + Tolerance ✅ BEST APPROACH

1. **Add 14-day tolerance window** - Links 7 safe transactions
2. **Manually review remaining 3**:
   - Transaction 1551: In gap between leases - needs manual decision
   - Transaction 1483: 17 days before - probably deposit, safe to link
   - Transaction 1526: 18 days before - probably deposit, safe to link

## Recommendation: 14-Day Tolerance Window

**Rationale**:
- Industry standard: Deposits typically paid 1-2 weeks before lease starts
- Avoids ambiguous matches
- Keeps data integrity high
- Balances automation vs. accuracy

**Implementation**:
1. Modify `PayPropInvoiceLinkingService.isDateWithinLease()` to add tolerance
2. Re-run backfill with new logic
3. Manually review/link the 3 remaining transactions

**Code Change Needed**:
```java
// In PayPropInvoiceLinkingService.java, line 95-110

private static final int BEFORE_LEASE_TOLERANCE_DAYS = 14;

private boolean isDateWithinLease(Invoice invoice, LocalDate transactionDate) {
    LocalDate startDate = invoice.getStartDate();
    LocalDate endDate = invoice.getEndDate();

    // Allow transactions within tolerance window before lease start
    if (startDate != null) {
        LocalDate earliestAllowed = startDate.minusDays(BEFORE_LEASE_TOLERANCE_DAYS);
        if (transactionDate.isBefore(earliestAllowed)) {
            return false;
        }
    }

    // Transaction must be on or before lease end (strict)
    if (endDate != null && transactionDate.isAfter(endDate)) {
        return false;
    }

    return true;
}
```

## Impact Summary

| Approach | Transactions Linked | Risk | Data Quality |
|----------|-------------------|------|--------------|
| No date constraint | 10 (100%) | 🔴 HIGH - Wrong matches | Low |
| 14-day tolerance | 7 (70%) | 🟢 LOW - All safe | High |
| 30-day tolerance | 9 (90%) | 🟡 MEDIUM - 1 risky | Medium |
| Manual only | 0 (requires work) | 🟢 NONE | Highest |

**Recommended**: 14-day tolerance + manual review of 3 remaining

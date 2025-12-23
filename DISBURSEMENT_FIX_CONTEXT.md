# DISBURSEMENT Allocations on Monthly Statement - Fix Context

## FIXES COMPLETED (2025-12-23)

### Fix 1: DISBURSEMENT as Expenses on Flat Rows ✅
Added code to `extractExpensesByBatch()` to fetch DISBURSEMENT allocations by invoice_id and show them as expenses on individual flat rows.

### Fix 2: DISBURSEMENT as Income on Block Property Row ✅
Added code to `extractBatchPaymentGroups()` to detect block properties and fetch incoming DISBURSEMENT allocations where the beneficiary_name matches the block property name. These are added as "income" to the block property row.

**New Repository Methods Added:**
- `findByInvoiceIdAndAllocationType()` - Find disbursements by lease/invoice ID
- `findByInvoiceIdAndAllocationTypeInPeriod()` - Find disbursements by lease ID in date range
- `findByPropertyIdAndAllocationTypeInPeriod()` - Find disbursements by property ID in date range
- `findDisbursementsReceivedByBlockInPeriod()` - Find disbursements where block property is beneficiary
- `findDisbursementsReceivedByBlock()` - Find all disbursements for a block property

---

## Original Problem Summary
The £120/£150 block service charge contributions (DISBURSEMENT allocations) are not appearing on the monthly owner statements. These payments exist in the `unified_allocations` table but the statement generation code doesn't include them.

## Root Cause
The `extractExpensesByBatch()` method in `StatementDataExtractService.java` only queries `unified_transactions` for OUTGOING transactions. DISBURSEMENT allocations are stored in `unified_allocations` table and are not being fetched.

## Data Flow
1. Individual flat owner pays rent
2. Part of rent (e.g., £120/£150) is allocated as DISBURSEMENT to block property (e.g., "BODEN HOUSE BLOCK PROPERTY")
3. This DISBURSEMENT should appear as an expense/deduction on the flat owner's monthly statement
4. Currently it does NOT appear because `extractExpensesByBatch()` doesn't query `unified_allocations`

## Database Evidence
- 36 DISBURSEMENT records exist in `unified_allocations` table
- Total value: £4,930
- These represent service charges going from flats to block property account

## Files to Modify

### 1. UnifiedAllocationRepository.java
**Location:** `src/main/java/site/easy/to/build/crm/repository/UnifiedAllocationRepository.java`

**Add these methods at the end (before closing brace):**

```java
    /**
     * Find DISBURSEMENT allocations by invoice ID (lease ID).
     * Used for monthly statement to show block service charge deductions.
     */
    List<UnifiedAllocation> findByInvoiceIdAndAllocationType(Long invoiceId, AllocationType allocationType);

    /**
     * Find DISBURSEMENT allocations by invoice ID within a date range.
     * Used for monthly statement to show block service charge deductions for a specific period.
     */
    @Query("SELECT ua FROM UnifiedAllocation ua WHERE ua.invoiceId = :invoiceId " +
           "AND ua.allocationType = :allocationType " +
           "AND ua.paidDate BETWEEN :startDate AND :endDate")
    List<UnifiedAllocation> findByInvoiceIdAndAllocationTypeInPeriod(
        @Param("invoiceId") Long invoiceId,
        @Param("allocationType") AllocationType allocationType,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
```

### 2. StatementDataExtractService.java
**Location:** `src/main/java/site/easy/to/build/crm/service/statement/StatementDataExtractService.java`

**Method to modify:** `extractExpensesByBatch()` (around line 3039)

**Current code at lines 3071-3075:**
```java
            allExpenses.add(detail);
        }

        // Populate batch info from unified_allocations using EXPENSE/DISBURSEMENT/COMMISSION allocation types
        populateBatchInfoForExpenses(allExpenses);
```

**Add this code between `allExpenses.add(detail);` closing brace and `populateBatchInfoForExpenses`:**

```java
        // Include DISBURSEMENT allocations (block service charge contributions like £120/£150)
        // These are stored in unified_allocations, not unified_transactions
        try {
            List<UnifiedAllocation> disbursements = unifiedAllocationRepository
                .findByInvoiceIdAndAllocationTypeInPeriod(
                    leaseId,
                    UnifiedAllocation.AllocationType.DISBURSEMENT,
                    periodStart,
                    periodEnd
                );

            for (UnifiedAllocation alloc : disbursements) {
                site.easy.to.build.crm.dto.statement.PaymentDetailDTO detail =
                    new site.easy.to.build.crm.dto.statement.PaymentDetailDTO();

                detail.setPaymentDate(alloc.getPaidDate());
                detail.setAmount(alloc.getAmount());
                detail.setDescription("Block contribution: " +
                    (alloc.getBeneficiaryName() != null ? alloc.getBeneficiaryName() : "Service Charge"));
                detail.setCategory("Disbursement");
                detail.setBatchId(alloc.getPaymentBatchId());
                detail.setPaidDate(alloc.getPaidDate());

                allExpenses.add(detail);
            }

            if (!disbursements.isEmpty()) {
                log.info("  Added {} DISBURSEMENT allocations for lease {}", disbursements.size(), leaseId);
            }
        } catch (Exception e) {
            log.warn("Could not fetch DISBURSEMENT allocations for lease {}: {}", leaseId, e.getMessage());
        }
```

## Key Entities/Fields

### UnifiedAllocation Entity
- `invoiceId` - Links to Invoice (lease) ID
- `propertyId` - Links to Property
- `allocationType` - Enum: OWNER, EXPENSE, COMMISSION, DISBURSEMENT, OTHER
- `amount` - The allocation amount
- `paidDate` - When the allocation was paid
- `paymentBatchId` - Batch reference
- `beneficiaryName` - For DISBURSEMENT, this is the block property name

### Invoice Entity
- `id` - Used as leaseId in statements
- `property` - ManyToOne to Property
- `customer` - ManyToOne to Customer (tenant)

## Already Injected Dependencies
- `UnifiedAllocationRepository` is already injected in `StatementDataExtractService` at line 99
- `InvoiceRepository` is already injected at line 75

## Related Previous Fixes (Already Done)
1. **PayProp API Rate Limiting (429 errors):** Added retry with exponential backoff to `PayPropApiClient.java`
2. **SQL Column Name Error:** Fixed `UnifiedTransactionRebuildService.java` Step 5d to use `properties.property_owner_id` instead of `property_owners.customer_id`

## Testing After Fix
1. Rebuild the application: `mvn clean install` or restart Spring Boot
2. Generate a monthly statement for a property that has DISBURSEMENT allocations
3. Look for "Block contribution:" entries in the expenses section
4. Verify the £120/£150 amounts appear on the statement

## Alternative Approach (If invoice_id is NULL)
If DISBURSEMENT allocations don't have `invoice_id` populated, use property_id instead:

```java
// Get property ID from invoice
Invoice invoice = invoiceRepository.findById(leaseId).orElse(null);
if (invoice != null && invoice.getProperty() != null) {
    Long propertyId = invoice.getProperty().getId();
    List<UnifiedAllocation> disbursements = unifiedAllocationRepository
        .findByPropertyIdAndAllocationType(propertyId, UnifiedAllocation.AllocationType.DISBURSEMENT);
    // ... rest of code
}
```

## Notes
- The file modification conflict errors were likely due to IDE auto-save/auto-format
- Close IntelliJ/VS Code before making edits, or disable auto-save temporarily

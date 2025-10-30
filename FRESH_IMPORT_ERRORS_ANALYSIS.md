# Fresh Data Import Errors - Root Cause Analysis

**Date:** October 30, 2025
**Status:** IDENTIFIED - Fixes Required

---

## Summary

Fresh PayProp data import encountered 3 critical errors blocking statement generation and financial sync:

1. **SQL ORDER BY/DISTINCT Error** - Properties query for customer 79 fails
2. **Duplicate Invoice PayProp ID** - 13 invoice instructions attempting duplicate payprop_id
3. **Entity Resolution Failures** - 25 properties + 29 tenants not linked (EXPECTED)

---

## Error 1: ORDER BY with DISTINCT Incompatibility ❌

### Error Message:
```
Expression #1 of ORDER BY clause is not in SELECT list, references column
'railway.p1_0.display_order' which is not in SELECT list;
this is incompatible with DISTINCT
```

### Frequency:
- Occurs every time customer 79 (or any property owner) logs in
- Blocks property listing in UI
- Last occurred: 2025-10-30 00:11:48 UTC

### Root Cause:

**File:** `CustomerPropertyAssignmentRepository.java:60-66`

```java
@Query("SELECT DISTINCT cpa.property FROM CustomerPropertyAssignment cpa " +
       "LEFT JOIN PropertyBlockAssignment pba ON cpa.property.id = pba.property.id AND pba.isActive = true " +
       "WHERE cpa.customer.customerId = :customerId AND cpa.assignmentType = :assignmentType " +
       "ORDER BY CASE WHEN pba.displayOrder IS NULL THEN 1 ELSE 0 END, pba.displayOrder ASC, cpa.property.propertyName ASC")
List<Property> findPropertiesByCustomerIdAndAssignmentTypeOrdered(
        @Param("customerId") Long customerId,
        @Param("assignmentType") AssignmentType assignmentType);
```

**Problem:**
- Query uses `SELECT DISTINCT cpa.property` (only property fields)
- Tries to `ORDER BY pba.displayOrder` from LEFT JOIN
- **pba.displayOrder is NOT in SELECT list**
- MySQL ONLY_FULL_GROUP_BY mode rejects this as ambiguous

**Why It's a Problem:**
- With DISTINCT, MySQL must group by SELECT columns
- Ordering by columns not in SELECT is ambiguous when rows are grouped
- Different properties might join to different pba.displayOrder values

### Impact:
- **PropertyService.getPropertiesByOwner()** fails (line 333 of PropertyServiceImpl.java)
- Property owners cannot see their properties in UI
- Blocks statements, financials, and dashboard for owners
- All owner functionality broken

---

## Error 2: Duplicate Invoice PayProp ID ❌

### Error Message:
```
Duplicate entry 'd71ebxD9Z5' for key 'invoices.payprop_id'
```

### Frequency:
- 13 different invoice instructions attempted to set same payprop_id
- Occurred: 2025-10-30 00:18:13 UTC
- All 13 attempts failed

### Affected Instructions:
```
z2JkeE7b1b, agXVwV8B13, 5AJ5qPo0JM, oRZQbY2dJm, PzZyra2DJd,
0G1OWDA21M, EyJ6OGD3Xj, WzJBMzm3ZQ, K3JwbKBwZE, z2JkgOEYJb,
z2Jkyy6x1b, PzZy6370Jd, EyJ6BBLrJj, BRXEW4v7ZO
```

### Root Cause:

**File:** `PayPropInvoiceInstructionEnrichmentService.java:112-130`

```java
if (existingLease != null) {
    // ENRICH existing lease with PayProp IDs
    boolean updated = false;

    if (existingLease.getPaypropId() == null || !existingLease.getPaypropId().equals(instruction.paypropId)) {
        existingLease.setPaypropId(instruction.paypropId);
        updated = true;
    }

    if (updated) {
        existingLease.setPaypropLastSync(LocalDateTime.now());
        invoiceRepository.save(existingLease);  // ❌ Can cause duplicate constraint violation
        enriched++;
    }
}
```

**Problem:**
- Database has UNIQUE constraint on `invoices.payprop_id`
- Multiple PayProp invoice instructions reference the SAME payprop_id 'd71ebxD9Z5'
- Service tries to update 13 different local leases with the same payprop_id
- First update succeeds, next 12 fail with duplicate key error

**Why This Happens:**
1. One PayProp invoice instruction can have multiple references in `payprop_export_invoices`
2. Service matches each instruction to a different local lease (by property + customer)
3. All 13 instructions have the same `payprop_id` value
4. Each tries to set its matched lease's `payprop_id` to 'd71ebxD9Z5'
5. Database rejects duplicates due to UNIQUE constraint

**Possible Scenarios:**
- PayProp instruction is cloned across multiple properties (shared lease template?)
- Multiple tenants share the same payment instruction
- Data quality issue in PayProp export (duplicate instruction IDs)
- Historical data migration issue (old IDs reused)

### Impact:
- **PayPropInvoiceInstructionEnrichmentService.enrichLocalLeasesWithPayPropIds()** fails
- 13 leases cannot be linked to PayProp data
- Financial transactions for those leases cannot be matched
- Statement generation incomplete for affected properties

---

## Error 3: Entity Resolution Failures ⚠️ (EXPECTED)

### Error Message:
```
Failed to resolve property X: PayProp API error:
{"errors":[{"message":"Property not linked to entity"}],"status":404}
```

### Frequency:
- 25 properties failed entity resolution
- 29 tenants failed entity resolution
- Occurred throughout import process

### Root Cause:

**Expected Behavior:**
- Not all properties in local database are linked to PayProp entities
- Some properties may be:
  - Locally created (not synced to PayProp yet)
  - Archived/inactive in PayProp
  - PayProp integration disabled for that property
- This is NOT an error, just expected data skipping

**Current Logging:**
- Logged as ERROR level, causing alarm
- Should be INFO or DEBUG level

### Impact:
- **No impact on core functionality**
- Properties without PayProp entities are simply skipped
- They continue to work with historical data only
- Misleading error logs suggest problem when there isn't one

---

## Error 4: Financial Sync Rollback ❌

### Error Message:
```
Transaction silently rolled back because it has been marked as rollback-only
```

### Frequency:
- Occurred once: during comprehensive financial sync
- Followed the duplicate payprop_id errors

### Root Cause:

**Cascading Failure:**
- PayPropInvoiceInstructionEnrichmentService throws exception (duplicate payprop_id)
- Exception marks transaction for rollback
- Comprehensive financial sync includes multiple operations in one transaction
- When one fails, entire transaction rolls back
- "Rollback-only" message appears when trying to commit

**Transaction Scope:**
- Service annotated with `@Transactional`
- All database operations wrapped in single transaction
- One error = entire batch rolled back

### Impact:
- **Entire financial sync fails**
- No financial data updated (even successful parts)
- PayProp data not integrated
- System state remains unchanged

---

## Fix Priority

### Priority 1: ORDER BY/DISTINCT Error (CRITICAL)
**Reason:** Blocks all property owner functionality
**Users Affected:** All property owners (customer 79 specifically logged)
**Urgency:** IMMEDIATE - System unusable for owners

### Priority 2: Duplicate PayProp ID Error (HIGH)
**Reason:** Blocks financial sync for 13 leases
**Users Affected:** Owners of affected properties
**Urgency:** HIGH - Statements incomplete, financials wrong

### Priority 3: Financial Sync Rollback (MEDIUM)
**Reason:** Caused by Priority 2 error
**Users Affected:** System-wide data freshness
**Urgency:** MEDIUM - Will resolve after fixing Priority 2

### Priority 4: Entity Resolution Logging (LOW)
**Reason:** Misleading logs, not actual errors
**Users Affected:** None (cosmetic)
**Urgency:** LOW - Fix logging level only

---

## Proposed Fixes

### Fix 1: ORDER BY/DISTINCT Error

**Option A: Remove DISTINCT (RECOMMENDED)**

Remove DISTINCT and rely on inherent uniqueness of customer-property assignments:

```java
@Query("SELECT cpa.property FROM CustomerPropertyAssignment cpa " +
       "LEFT JOIN PropertyBlockAssignment pba ON cpa.property.id = pba.property.id AND pba.isActive = true " +
       "WHERE cpa.customer.customerId = :customerId AND cpa.assignmentType = :assignmentType " +
       "GROUP BY cpa.property.id " +
       "ORDER BY MIN(CASE WHEN pba.displayOrder IS NULL THEN 1 ELSE 0 END), " +
                "MIN(pba.displayOrder) ASC, " +
                "MIN(cpa.property.propertyName) ASC")
List<Property> findPropertiesByCustomerIdAndAssignmentTypeOrdered(...);
```

**Why This Works:**
- GROUP BY ensures uniqueness (same as DISTINCT)
- MIN() aggregates allow ORDER BY columns not in SELECT
- Handles multiple block assignments per property
- MySQL ONLY_FULL_GROUP_BY compatible

**Option B: Include display_order in SELECT (Alternative)**

Add display_order to SELECT, but this changes return type:

```java
// Would need to return List<Object[]> or create a DTO
// More complex, not recommended
```

**Recommendation:** Use Option A (GROUP BY with MIN aggregates)

---

### Fix 2: Duplicate PayProp ID Error

**Option A: Check Before Update (SAFEST)**

Before updating payprop_id, check if it's already used:

```java
if (existingLease.getPaypropId() == null || !existingLease.getPaypropId().equals(instruction.paypropId)) {
    // Check if this payprop_id is already used by another lease
    Optional<Invoice> conflictingLease = invoiceRepository.findByPaypropId(instruction.paypropId);

    if (conflictingLease.isPresent() && !conflictingLease.get().getId().equals(existingLease.getId())) {
        log.warn("⚠️ PayProp ID {} already assigned to lease {} ({}). Skipping lease {} ({})",
            instruction.paypropId,
            conflictingLease.get().getId(),
            conflictingLease.get().getLeaseReference(),
            existingLease.getId(),
            existingLease.getLeaseReference());
        skipped++;
        result.addSkipped(instruction.paypropId,
            "Already assigned to lease " + conflictingLease.get().getLeaseReference());
        continue; // Skip to next instruction
    }

    existingLease.setPaypropId(instruction.paypropId);
    updated = true;
}
```

**Option B: Deduplication Strategy**

Group instructions by payprop_id before processing:

```java
// In enrichLocalLeasesWithPayPropIds():
Map<String, List<PayPropInvoiceInstruction>> groupedByPaypropId = instructions.stream()
    .collect(Collectors.groupingBy(i -> i.paypropId));

// Warn about duplicates
groupedByPaypropId.forEach((paypropId, instructionList) -> {
    if (instructionList.size() > 1) {
        log.warn("⚠️ Duplicate PayProp ID {} found in {} instructions",
            paypropId, instructionList.size());
        // Keep only the first/most recent instruction
    }
});
```

**Option C: Remove UNIQUE Constraint (NOT RECOMMENDED)**

Remove unique constraint on `invoices.payprop_id`, but this:
- Loses data integrity
- Allows multiple leases to claim same PayProp data
- Creates ambiguity in transaction matching
- **DON'T DO THIS**

**Recommendation:** Use Option A (Check before update) + investigate why PayProp has duplicate instruction IDs

---

### Fix 3: Transaction Rollback

**Option A: Catch and Continue**

Wrap each instruction processing in try-catch to prevent transaction-wide rollback:

```java
for (PayPropInvoiceInstruction instruction : instructions) {
    try {
        // ... processing logic ...
        invoiceRepository.save(existingLease);
        enriched++;
    } catch (DataIntegrityViolationException e) {
        log.error("❌ Failed to process instruction {}: {}", instruction.paypropId, e.getMessage());
        errors++;
        result.addError(instruction.paypropId, e.getMessage());
        // Continue to next instruction instead of rolling back
    }
}
```

**Already Implemented:** Code already has this pattern (lines 160-164)

**Problem:** Transaction is still rolled back even with catch

**Solution:** Add `@Transactional(propagation = Propagation.REQUIRED)` and flush after each save:

```java
@Transactional(propagation = Propagation.REQUIRED)
public EnrichmentResult enrichLocalLeasesWithPayPropIds() {
    ...
    for (PayPropInvoiceInstruction instruction : instructions) {
        try {
            ...
            invoiceRepository.save(existingLease);
            invoiceRepository.flush(); // Force immediate save
            enriched++;
        } catch (DataIntegrityViolationException e) {
            // Log and continue
            errors++;
        }
    }
}
```

---

### Fix 4: Entity Resolution Logging

**Simple Fix:**

Change log level from ERROR to DEBUG/INFO:

```java
// BEFORE:
log.error("Failed to resolve property {}: {}", propertyId, error);

// AFTER:
log.debug("Property {} not linked to PayProp entity (expected for local-only properties): {}",
    propertyId, error);
```

---

## Testing Plan

### Test 1: ORDER BY/DISTINCT Fix
```bash
# 1. Apply fix to CustomerPropertyAssignmentRepository.java
# 2. Compile
mvn clean compile

# 3. Test property owner login
# Login as customer 79 and verify properties display correctly

# 4. Check logs
# Should see no "Expression #1 of ORDER BY clause" errors
```

### Test 2: Duplicate PayProp ID Fix
```bash
# 1. Query database for duplicate payprop_id
mysql -h switchyard.proxy.rlwy.net -P 55090 -u root -p'iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW' railway -e "
SELECT payprop_id, COUNT(*) as count
FROM payprop_export_invoices
WHERE is_active_instruction = 1
GROUP BY payprop_id
HAVING count > 1;
"

# 2. Apply fix to PayPropInvoiceInstructionEnrichmentService.java

# 3. Run fresh import
# Should see warnings about duplicate IDs
# Should see "Skipped" instead of "Failed"
# Should see some leases successfully enriched

# 4. Verify no duplicate errors in logs
```

### Test 3: Full Import After Fixes
```bash
# 1. Apply all fixes
# 2. Run comprehensive financial sync
# 3. Verify:
#    - No ORDER BY errors
#    - Duplicate payprop_id handled gracefully
#    - Financial sync completes successfully
#    - Statement generation works
```

---

## Database Queries for Investigation

### Query 1: Check Duplicate PayProp IDs
```sql
-- Find PayProp instruction IDs that appear multiple times
SELECT
    payprop_id,
    COUNT(*) as instruction_count,
    GROUP_CONCAT(property_payprop_id) as properties,
    GROUP_CONCAT(tenant_payprop_id) as tenants
FROM payprop_export_invoices
WHERE is_active_instruction = 1
GROUP BY payprop_id
HAVING instruction_count > 1;
```

### Query 2: Check Current Invoice PayProp ID Usage
```sql
-- Find invoices that already have payprop_id 'd71ebxD9Z5'
SELECT
    id,
    lease_reference,
    payprop_id,
    property_id,
    customer_id,
    amount,
    start_date,
    end_date
FROM invoices
WHERE payprop_id = 'd71ebxD9Z5';
```

### Query 3: Check Property Assignments for Customer 79
```sql
-- Verify customer 79 property assignments work
SELECT
    cpa.id,
    cpa.customer_id,
    cpa.property_id,
    cpa.assignment_type,
    p.property_name,
    pba.display_order,
    pba.block_id
FROM customer_property_assignments cpa
JOIN properties p ON cpa.property_id = p.id
LEFT JOIN property_block_assignments pba ON p.id = pba.property_id AND pba.is_active = 1
WHERE cpa.customer_id = 79 AND cpa.assignment_type = 'OWNER'
ORDER BY
    CASE WHEN pba.display_order IS NULL THEN 1 ELSE 0 END,
    pba.display_order ASC,
    p.property_name ASC;
```

---

## Next Steps

1. ✅ **Documented root causes** (this file)
2. 🔜 **Apply Fix 1**: ORDER BY/DISTINCT error
3. 🔜 **Apply Fix 2**: Duplicate payprop_id handling
4. 🔜 **Test fixes**: Run fresh import and verify
5. 🔜 **Monitor logs**: Ensure no regression
6. 🔜 **Update statement generation**: Test with fixed data

---

**Analysis Complete**
**Ready to Apply Fixes**

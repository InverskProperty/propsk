# Lease Linkage Gap Analysis
**Date**: October 27, 2025
**Database**: Production (railway)
**Focus**: Missing linkages in existing lease data

---

## Executive Summary

**Finding**: Your existing leases have **INCOMPLETE linkages** that could impact transaction matching and reporting.

| Linkage Type | Coverage | Gap | Status |
|--------------|----------|-----|--------|
| **Property Link** | 100% (41/41) | 0 | ✅ Complete |
| **Customer Link** | 100% (41/41) | 0 | ✅ Complete |
| **Lease Reference** | 100% (41/41) | 0 | ✅ Complete |
| **PayProp ID** | 80% (33/41) | **8 missing** | ⚠️ **Gap** |
| **PayProp Customer ID** | 80% (33/41) | **8 missing** | ⚠️ **Gap** |
| **Customer-Property Assignment** | 90% (37/41) | **4 missing** | ⚠️ **Gap** |

---

## Gap 1: Missing Customer-Property Assignments (4 leases)

### What's Missing

4 leases lack corresponding `customer_property_assignments` records:

| Lease Reference | Property | Customer | Start Date | End Date | Issue |
|----------------|----------|----------|------------|----------|-------|
| LEASE-BH-F12-2025 | Flat 12 | MR J SOOPIYADAKATH | 2025-04-07 | 2025-06-07 | No assignment |
| LEASE-BH-F18-2025 | Flat 18 | Marie Dinko | 2025-01-30 | 2025-05-06 | No assignment |
| LEASE-BH-PS1-2025 | Parking Space 1 | Mr Shermal Wijesinghage | 2025-07-04 | NULL | No assignment |
| LEASE-BH-PS2-2025 | Parking Space 2 | Nirmali Gedara | 2025-07-14 | NULL | No assignment |

### Why This Matters

**customer_property_assignments** table is used for:

1. **Owner Lookup in Commission Calculation** (`HistoricalTransactionImportService.java:593-607`)
   ```java
   // Commission split logic looks up owner via assignment table
   List<CustomerPropertyAssignment> ownerAssignments =
       assignmentRepository.findByPropertyAndType(property, AssignmentType.OWNER);
   ```

2. **Property Management Dashboard** - Showing current tenants per property

3. **Historical Analysis** - Tracking all tenant relationships over time

4. **Lease Overlap Detection** - Preventing double-booking

**Impact of Missing Assignments**:
- ❌ Commission splits may fail to find owner for these properties
- ❌ Properties appear "vacant" in tenant dashboards
- ❌ Historical tenant reports incomplete
- ⚠️ Could impact transaction linking for these leases

---

### Root Cause: Two Import Paths

#### Path 1: LeaseImportWizardController ✅ (Creates Assignments)

**File**: `LeaseImportWizardController.java` (lines 836-846)

```java
// Save lease to database
Invoice savedLease = invoiceRepository.save(lease);

// Create corresponding tenant assignment
CustomerPropertyAssignment assignment = new CustomerPropertyAssignment();
assignment.setCustomer(customer);
assignment.setProperty(property);
assignment.setAssignmentType(AssignmentType.TENANT);
assignment.setStartDate(startDate);
assignment.setEndDate(endDate);
assignmentRepository.save(assignment);
```

**Result**: ✅ Lease + Assignment both created

---

#### Path 2: LeaseImportService ❌ (DOES NOT Create Assignments)

**File**: `LeaseImportService.java` (lines 407-443)

```java
private Invoice createLease(LeaseRow row, Property property, Customer customer, User createdByUser) {
    Invoice lease = new Invoice();
    lease.setProperty(property);
    lease.setCustomer(customer);
    // ... set other fields ...
    return lease; // ❌ NO assignment created!
}
```

**Result**: ⚠️ Lease created, Assignment NOT created

---

### Which Leases Used Which Path?

**37 leases** → Used Wizard (have assignments) ✅
**4 leases** → Used Service or Manual (missing assignments) ❌

Likely scenario:
- Most leases imported via Wizard (interactive UI)
- 4 leases created manually or via CSV service import
- These 4 bypassed assignment creation

---

## Gap 2: Missing PayProp IDs (8 leases)

### What's Missing

8 leases lack PayProp enrichment (`payprop_id` and `payprop_customer_id` are NULL):

**Confirmed Missing**:
- LEASE-BH-F7-2025
- LEASE-BH-F10-2025
- LEASE-BH-F12-2025
- LEASE-BH-F18-2025
- LEASE-BH-PS1-2025
- LEASE-BH-PS2-2025
- (+ 2 more)

### Why This Matters

PayProp IDs enable:

1. **Intelligent Lease Matching** in transaction imports
   ```java
   // PayPropInvoiceLinkingService uses payprop_customer_id for matching
   lease = findInvoiceForTransaction(property, tenantPayPropId, transactionDate);
   ```

2. **Cross-Referencing with PayProp API** - Link manual leases to PayProp data

3. **Better Auto-Matching** - More accurate than name-based matching

**Impact of Missing PayProp IDs**:
- ⚠️ Intelligent lease matching falls back to name matching (less accurate)
- ❌ Can't cross-reference these leases with PayProp API imports
- ⚠️ Transaction imports may fail to link automatically

---

### Root Cause: Properties Not in PayProp

**Possible Reasons**:
1. **Parking Spaces** (PS1, PS2) - May not be managed via PayProp
2. **Older Leases** (F7, F10, F12, F18) - Created before PayProp integration?
3. **Manual Entry** - Created directly in system without PayProp sync

**Note**: This may be **intentional** if these properties aren't managed via PayProp.

---

## Impact Analysis

### On Transaction Imports

**Scenario**: Importing rent payment for LEASE-BH-F12-2025

**Without Customer-Property Assignment**:
```csv
transaction_date,amount,category,property_reference,customer_reference,lease_reference
2025-05-06,795.00,rent,Flat 12 - 3 West Gate,MR J SOOPIYADAKATH,LEASE-BH-F12-2025
```

**What Happens**:
1. ✅ Property matched (Flat 12)
2. ✅ Customer matched (MR J SOOPIYADAKATH)
3. ✅ Lease matched by lease_reference
4. ⚠️ Commission split tries to find owner via assignments → **May fail**
5. ❌ Transaction might not create owner allocation/management fee

**Result**: Transaction imports but commission splits may not work correctly.

---

### On Reporting

**Dashboard Queries** that use `customer_property_assignments`:

```sql
-- Current Tenants Report
SELECT p.property_name, c.name as tenant, cpa.start_date
FROM customer_property_assignments cpa
JOIN properties p ON cpa.property_id = p.id
JOIN customers c ON cpa.customer_id = c.customer_id
WHERE cpa.assignment_type = 'TENANT'
AND cpa.end_date IS NULL;
```

**Result**: 4 properties won't show as "occupied" even though they have active leases.

---

## Recommended Fixes

### Fix 1: Create Missing Customer-Property Assignments

**SQL Script**:

```sql
-- Create missing tenant assignments from leases
INSERT INTO customer_property_assignments
    (customer_id, property_id, assignment_type, start_date, end_date, created_at, sync_status)
SELECT
    i.customer_id,
    i.property_id,
    'TENANT' as assignment_type,
    i.start_date,
    i.end_date,
    NOW() as created_at,
    'LOCAL_ONLY' as sync_status
FROM invoices i
LEFT JOIN customer_property_assignments cpa
    ON i.property_id = cpa.property_id
    AND i.customer_id = cpa.customer_id
WHERE cpa.id IS NULL
AND i.invoice_type = 'lease';

-- Expected: 4 rows inserted
```

**Verification Query**:
```sql
-- Check all leases now have assignments
SELECT
    COUNT(DISTINCT i.id) as total_leases,
    COUNT(DISTINCT cpa.id) as leases_with_assignments
FROM invoices i
LEFT JOIN customer_property_assignments cpa
    ON i.property_id = cpa.property_id
    AND i.customer_id = cpa.customer_id
WHERE i.invoice_type = 'lease';

-- Expected: both columns should be 41
```

---

### Fix 2: Update LeaseImportService to Create Assignments

**File**: `src/main/java/site/easy/to/build/crm/service/lease/LeaseImportService.java`

**Add to class** (around line 63):
```java
@Autowired
private CustomerPropertyAssignmentRepository assignmentRepository;
```

**Modify** `importFromCsvString` method (after line 138):
```java
// Create lease (Invoice entity)
Invoice lease = createLease(row, property, customer, createdByUser);
Invoice savedLease = invoiceRepository.save(lease);

// ✅ NEW: Create corresponding tenant assignment
CustomerPropertyAssignment assignment = new CustomerPropertyAssignment();
assignment.setCustomer(customer);
assignment.setProperty(property);
assignment.setAssignmentType(AssignmentType.TENANT);
assignment.setStartDate(lease.getStartDate());
assignment.setEndDate(lease.getEndDate());
assignment.setCreatedAt(LocalDateTime.now());
assignment.setSyncStatus("LOCAL_ONLY");
assignmentRepository.save(assignment);

result.incrementSuccessful();
```

**Impact**: Future CSV imports will create assignments automatically.

---

### Fix 3: PayProp ID Enrichment (If Needed)

**Decision Required**: Are these 8 leases managed via PayProp?

**If YES** (should have PayProp IDs):
1. Check if properties exist in PayProp
2. Run PayProp lease sync to populate `payprop_id` and `payprop_customer_id`
3. Manually link if necessary

**If NO** (intentionally not in PayProp):
1. Mark as acceptable gap (e.g., parking spaces, manual properties)
2. Document that these use name-based matching only
3. No action needed

**Query to Check**:
```sql
-- Check if properties have PayProp IDs
SELECT
    p.property_name,
    p.payprop_id,
    i.lease_reference,
    i.payprop_id as lease_payprop_id
FROM invoices i
JOIN properties p ON i.property_id = p.id
WHERE i.payprop_id IS NULL;
```

---

## Idempotency Answer: Lease Import IS Safe to Re-Run

**Original Question**: Is the lease import wizard idempotent?

**Answer**: ✅ **YES** - Safe to re-run, BUT with important caveats:

### What's Idempotent (Safe) ✅

1. **Duplicate Detection**: Uses `lease_reference` UNIQUE constraint
   ```java
   if (invoiceRepository.existsByLeaseReference(row.leaseReference)) {
       result.addSkipped("Lease reference already exists");
       continue; // ✅ Skips, doesn't create duplicate
   }
   ```

2. **No Data Loss**: Existing leases remain unchanged
3. **Clear Feedback**: Reports skipped vs created

### What's NOT Idempotent (Gaps) ⚠️

1. **Won't Update Existing Leases**: If data changes (e.g., rent amount), import skips it
2. **Won't Create Missing Assignments**: Re-importing existing leases won't fix missing assignments
3. **Won't Add PayProp IDs**: Re-import won't enrich existing leases

---

## Immediate Action Plan

### Priority 1: Fix Missing Assignments (CRITICAL)

**Risk**: Impacts commission calculations and reporting

**Steps**:
1. ✅ Run SQL script to create 4 missing assignments
2. ✅ Verify with query
3. ✅ Update `LeaseImportService` to auto-create assignments
4. ✅ Test with new CSV import

**Time**: 30 minutes

---

### Priority 2: Investigate PayProp ID Gap (MEDIUM)

**Risk**: Impacts intelligent matching, may be intentional

**Steps**:
1. ❓ Determine if 8 leases should have PayProp IDs
2. ❓ If yes, sync from PayProp or manually link
3. ❓ If no, document as acceptable gap

**Time**: 1-2 hours (investigation + fix)

---

### Priority 3: Prevent Future Gaps (LOW)

**Risk**: Preventative, improves future data quality

**Steps**:
1. ✅ Update `LeaseImportService` code (already described above)
2. ✅ Add validation warning if assignment missing
3. ✅ Document both import paths clearly

**Time**: 1 hour

---

## SQL Scripts for Immediate Use

### Script 1: Create Missing Assignments

```sql
-- Backup existing assignments first
CREATE TABLE customer_property_assignments_backup_20251027 AS
SELECT * FROM customer_property_assignments;

-- Create missing assignments
INSERT INTO customer_property_assignments
    (customer_id, property_id, assignment_type, start_date, end_date, created_at, sync_status)
SELECT
    i.customer_id,
    i.property_id,
    'TENANT' as assignment_type,
    i.start_date,
    i.end_date,
    NOW() as created_at,
    'LOCAL_ONLY' as sync_status
FROM invoices i
LEFT JOIN customer_property_assignments cpa
    ON i.property_id = cpa.property_id
    AND i.customer_id = cpa.customer_id
WHERE cpa.id IS NULL
AND i.invoice_type = 'lease';

-- Verify
SELECT
    'Before' as status,
    37 as with_assignments,
    4 as missing
UNION ALL
SELECT
    'After' as status,
    COUNT(DISTINCT cpa.id) as with_assignments,
    (SELECT COUNT(*) FROM invoices WHERE invoice_type = 'lease') - COUNT(DISTINCT cpa.id) as missing
FROM invoices i
LEFT JOIN customer_property_assignments cpa
    ON i.property_id = cpa.property_id
    AND i.customer_id = cpa.customer_id
WHERE i.invoice_type = 'lease';
```

---

### Script 2: Identify Leases Needing PayProp IDs

```sql
-- Find leases without PayProp IDs and check if properties have PayProp integration
SELECT
    i.lease_reference,
    p.property_name,
    c.name as customer_name,
    i.start_date,
    CASE
        WHEN p.payprop_id IS NOT NULL THEN 'Property has PayProp - INVESTIGATE'
        ELSE 'Property not in PayProp - EXPECTED'
    END as recommendation
FROM invoices i
JOIN properties p ON i.property_id = p.id
JOIN customers c ON i.customer_id = c.customer_id
WHERE i.payprop_id IS NULL
ORDER BY p.payprop_id IS NOT NULL DESC, i.lease_reference;
```

---

### Script 3: Health Check After Fixes

```sql
-- Comprehensive lease linkage health check
SELECT
    'Total Leases' as metric,
    COUNT(*) as count,
    '100%' as target,
    CONCAT(ROUND(100.0 * COUNT(*) / COUNT(*), 1), '%') as actual
FROM invoices WHERE invoice_type = 'lease'

UNION ALL

SELECT
    'Has Property Link',
    COUNT(*),
    '100%',
    CONCAT(ROUND(100.0 * COUNT(*) / (SELECT COUNT(*) FROM invoices WHERE invoice_type = 'lease'), 1), '%')
FROM invoices WHERE invoice_type = 'lease' AND property_id IS NOT NULL

UNION ALL

SELECT
    'Has Customer Link',
    COUNT(*),
    '100%',
    CONCAT(ROUND(100.0 * COUNT(*) / (SELECT COUNT(*) FROM invoices WHERE invoice_type = 'lease'), 1), '%')
FROM invoices WHERE invoice_type = 'lease' AND customer_id IS NOT NULL

UNION ALL

SELECT
    'Has Lease Reference',
    COUNT(*),
    '100%',
    CONCAT(ROUND(100.0 * COUNT(*) / (SELECT COUNT(*) FROM invoices WHERE invoice_type = 'lease'), 1), '%')
FROM invoices WHERE invoice_type = 'lease' AND lease_reference IS NOT NULL

UNION ALL

SELECT
    'Has Customer-Property Assignment',
    COUNT(DISTINCT i.id),
    '100%',
    CONCAT(ROUND(100.0 * COUNT(DISTINCT i.id) / (SELECT COUNT(*) FROM invoices WHERE invoice_type = 'lease'), 1), '%')
FROM invoices i
INNER JOIN customer_property_assignments cpa
    ON i.property_id = cpa.property_id
    AND i.customer_id = cpa.customer_id
WHERE i.invoice_type = 'lease'

UNION ALL

SELECT
    'Has PayProp ID',
    COUNT(*),
    '80-100%',
    CONCAT(ROUND(100.0 * COUNT(*) / (SELECT COUNT(*) FROM invoices WHERE invoice_type = 'lease'), 1), '%')
FROM invoices WHERE invoice_type = 'lease' AND payprop_id IS NOT NULL;
```

**Expected After Fixes**:
```
Total Leases: 41 (100%)
Has Property Link: 41 (100%)
Has Customer Link: 41 (100%)
Has Lease Reference: 41 (100%)
Has Customer-Property Assignment: 41 (100%) ← Fixed!
Has PayProp ID: 33-41 (80-100%) ← Depends on decision
```

---

## Conclusion

**Your existing leases have two gaps**:

1. **4 leases missing customer-property assignments** (90% coverage)
   - ❌ **Must fix** - Impacts commission calculations
   - ✅ Easy SQL fix (30 minutes)

2. **8 leases missing PayProp IDs** (80% coverage)
   - ❓ **Investigate** - May be intentional (parking spaces, non-PayProp properties)
   - ⚠️ Impacts intelligent matching accuracy

**Next Steps**:
1. Run SQL Script 1 to create missing assignments
2. Run SQL Script 2 to investigate PayProp ID gap
3. Update LeaseImportService code to prevent future gaps
4. Document acceptable gaps (if any)

**Idempotency Answer**: ✅ Lease import is safe to re-run (won't create duplicates), but won't fix existing gaps.

---

**Document Version**: 1.0
**Date**: October 27, 2025
**Status**: Ready for Action

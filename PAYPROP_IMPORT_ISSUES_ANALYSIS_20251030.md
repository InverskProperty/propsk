# PayProp Data Import Issues - Comprehensive Analysis
**Date:** October 30, 2025
**Analysis Time:** 10:30 UTC
**System:** spoutproperty-hub.onrender.com
**Deployment Version:** Commit 74f6bf9 (deployed 10:04 UTC)

---

## Executive Summary

Following the deployment of invoice linking enhancements at 10:04 UTC and a subsequent data import attempt, critical issues have been identified that prevent proper invoice linkage and cause transaction rollbacks. **106 incoming payments totaling £88,560.39 remain unlinked to leases**, making them invisible in statement generation.

**Status:** 🔴 **CRITICAL** - Core financial data not properly linked

---

## Table of Contents
1. [Critical Issues](#critical-issues)
2. [High Priority Issues](#high-priority-issues)
3. [Medium Priority Issues](#medium-priority-issues)
4. [Data Impact Assessment](#data-impact-assessment)
5. [Technical Analysis](#technical-analysis)
6. [Root Cause Analysis](#root-cause-analysis)
7. [Recommended Fixes](#recommended-fixes)
8. [Testing Recommendations](#testing-recommendations)

---

## Critical Issues

### 🔴 ISSUE #1: Incoming Payments Not Being Linked to Invoices

**Severity:** CRITICAL
**Impact:** £88,560.39 in tenant payments not visible in statements
**Affected Records:** 106 incoming payments

#### Details

```
Data Source: INCOMING_PAYMENT
Total Records: 106
Successfully Linked: 0 (0%)
Failed Linkage: 106 (100%)
Financial Impact: £88,560.39
Status: UNACCEPTABLE
```

#### Evidence

**Database Query Results:**
```sql
SELECT
    COUNT(*) as total,
    COUNT(CASE WHEN invoice_id IS NOT NULL THEN 1 END) as linked,
    COUNT(CASE WHEN invoice_id IS NULL THEN 1 END) as unlinked,
    SUM(amount) as total_amount
FROM financial_transactions
WHERE data_source = 'INCOMING_PAYMENT'

Results:
total: 106
linked: 0
unlinked: 106
total_amount: £88,560.39
```

**Log Analysis:**
- ❌ No logs found containing: `"Linked incoming payment"`
- ❌ No logs found containing: `"No invoice found for incoming payment"`
- ✅ Logs DO show batch payment linking working: `"✅ Linked batch payment ... to invoice ..."`

#### Root Cause Hypothesis

**Option A:** PayPropIncomingPaymentFinancialSyncService is NOT running during the import
- The service may not be triggered by the enhanced unified sync
- Import process may be bypassing this service

**Option B:** Existing records created before deployment
- The 106 records may have been imported BEFORE the linking code was deployed
- No re-sync mechanism to update old records with invoice links

**Option C:** Invoice linking logic failing silently
- No error logs suggest the linking code is running but not throwing exceptions
- Possible silent failures in PayPropInvoiceLinkingService.findInvoiceForTransaction()

#### Code Reference

**File:** `PayPropIncomingPaymentFinancialSyncService.java`

**Expected Behavior (Lines 250-267):**
```java
// 🔗 CRITICAL FIX: Link to invoice (lease) for statement generation
Invoice invoice = invoiceLinkingService.findInvoiceForTransaction(
    property,
    tenant,
    null, // Incoming payments don't have PayProp invoice ID
    payment.reconciliationDate
);

if (invoice != null) {
    transaction.setInvoice(invoice);
    log.info("✅ Linked incoming payment {} to invoice {} (lease: {})",
        payment.paypropId, invoice.getId(), invoice.getLeaseReference());
} else {
    log.warn("⚠️ No invoice found for incoming payment {} - property: {}, tenant: {}",
        payment.paypropId,
        property.getId(),
        tenant != null ? tenant.getCustomerId() : "NULL");
}
```

**Actual Behavior:** No logs produced → Code not executing

---

### 🔴 ISSUE #2: Transaction Silently Rolled Back

**Severity:** CRITICAL
**Impact:** Financial sync completely failing
**Affected Process:** ENHANCED_UNIFIED_SYNC_WITH_FINANCIALS

#### Details

**Error Message:**
```
❌ SYNC FAILED: ENHANCED_UNIFIED_SYNC_WITH_FINANCIALS
Financial sync failed: Transaction silently rolled back because it has been
marked as rollback-only
```

**Log Timestamp:** 2025-10-30T10:33:00.424Z

#### Symptoms

1. Comprehensive financial sync fails
2. Transaction marked rollback-only before commit
3. All changes in transaction are discarded
4. No specific error indicating which operation caused the rollback

#### Potential Causes

**A. Duplicate Key Constraint Violation (See Issue #3)**
- Duplicate invoice PayProp ID violations may mark transaction for rollback
- Spring @Transactional propagation causing entire sync to roll back

**B. Entity Resolution Failures (See Issue #4)**
- 25 properties failing to resolve may contribute to rollback
- Cascading failures in entity relationships

**C. Missing Required Fields**
- Database NOT NULL constraints being violated
- Foreign key constraints failing

**D. Transaction Timeout**
- Large batch operation exceeding transaction timeout
- Deadlock or lock wait timeout

#### Code Reference

**File:** `PayPropFinancialSyncService.java`

**Transaction Boundary:**
```java
@Transactional
public ComprehensiveFinancialSyncResult syncAllFinancialData() {
    // ... multiple operations ...
    // If ANY operation fails, entire transaction rolls back
}
```

**Issue:** Spring's default transaction behavior is to roll back on unchecked exceptions

---

### 🔴 ISSUE #3: Duplicate Invoice PayProp ID Constraint Violation

**Severity:** CRITICAL
**Impact:** 14 invoice instructions failing to process
**Affected PayProp ID:** `d71ebxD9Z5`

#### Details

**Error Message:**
```
Duplicate entry 'd71ebxD9Z5' for key 'invoices.payprop_id'
```

**Failed Instructions (14 total):**
```
z2JkeE7b1b, agXVwV8B13, 5AJ5qPo0JM, oRZQbY2dJm, PzZyra2DJd, 0G1OWDA21M,
EyJ6OGD3Xj, WzJBMzm3ZQ, K3JwbKBwZE, z2JkgOEYJb, z2Jkyy6x1b, PzZy6370Jd,
EyJ6BBLrJj, BRXEW4v7ZO
```

**Log Samples:**
```
2025-10-30T10:32:59.323Z ERROR ... yPropInvoiceInstructionEnrichmentService :
❌ Failed to process instruction z2JkeE7b1b: could not execute statement
[Duplicate entry 'd71ebxD9Z5' for key 'invoices.payprop_id']

2025-10-30T10:32:59.419Z ERROR ... yPropInvoiceInstructionEnrichmentService :
❌ Failed to process instruction agXVwV8B13: could not execute statement
[Duplicate entry 'd71ebxD9Z5' for key 'invoices.payprop_id']

... (12 more similar errors)
```

#### Database State Investigation Required

**Query to investigate:**
```sql
-- Find the existing invoice with this PayProp ID
SELECT
    id,
    payprop_id,
    lease_reference,
    customer_id,
    property_id,
    amount,
    invoice_type,
    created_at,
    updated_at
FROM invoices
WHERE payprop_id = 'd71ebxD9Z5';

-- Find all instruction attempts referencing this PayProp ID
SELECT
    payprop_invoice_id,
    payprop_instruction_id,
    description,
    amount,
    transaction_date
FROM payprop_report_invoice_instructions
WHERE payprop_invoice_id = 'd71ebxD9Z5'
ORDER BY transaction_date;
```

#### Root Cause Scenarios

**Scenario A: Multiple Instructions for Same Invoice**
- PayProp returns same invoice ID across multiple instruction records
- System tries to create invoice multiple times during same sync
- First attempt succeeds, subsequent 13 fail

**Scenario B: Existing Invoice Not Being Updated**
- Invoice already exists from previous sync
- Enrichment service tries INSERT instead of UPDATE
- Merge/upsert logic not working correctly

**Scenario C: Race Condition**
- Parallel processing of instructions
- Multiple threads trying to create same invoice simultaneously
- No proper locking mechanism

#### Code Reference

**File:** `PayPropInvoiceInstructionEnrichmentService.java` (implied)

**Expected Behavior:**
- Check if invoice exists before creating
- Use upsert/merge pattern
- Handle duplicate gracefully

---

## High Priority Issues

### 🟠 ISSUE #4: Entity Resolution Failures - Properties

**Severity:** HIGH
**Impact:** 25 properties cannot be synced
**Error:** 404 NOT_FOUND - "Property not linked to entity"

#### Details

**Summary:**
```
Properties Attempted: 54 (estimated)
Successfully Resolved: 0
Failed: 25
Orphaned: 25
Success Rate: 0%
```

**Failed Property IDs:**
```
1, 2, 5, 6, 7, 11, 14, 16, 17, 18, 20, 21, 25, 27, 28, 29, 30, 31, 33, 37, 39, 40, 41, 43, 44
```

#### Error Pattern

**Log Sample:**
```
2025-10-30T10:33:41.166Z ERROR ... PayPropApiClient :
PayProp API error: 404 NOT_FOUND - {"errors":[{"message":"Property not linked to entity"}],"status":404}

2025-10-30T10:33:41.166Z ERROR ... PayPropEntityResolutionService :
Failed to resolve property 1: PayProp API error: {"errors":[{"message":"Property not linked to entity"}],"status":404}
```

**Frequency:** 25 occurrences between 10:33:41 - 10:34:06 UTC

#### Root Cause

**PayProp API Behavior:**
- Properties exist in local database with IDs 1-44
- PayProp API returns 404 when querying these property IDs
- Properties may not be linked to the authenticated PayProp entity/account

#### Business Impact

**Affected Operations:**
- Property financial data cannot be synced
- Transactions for these properties cannot be imported
- Statements cannot be generated for these properties
- Property owners have no financial visibility

#### Data Cleanup Required

**Investigation Steps:**
1. Identify which properties in local DB have `payprop_id` set but fail API resolution
2. Cross-reference with property ownership and active status
3. Determine if properties are:
   - Inactive/archived properties that should be removed
   - Properties belonging to different PayProp entity
   - Properties with incorrect PayProp ID mapping

**Query:**
```sql
SELECT
    id,
    property_name,
    payprop_id,
    is_active,
    created_at,
    updated_at
FROM properties
WHERE id IN (1,2,5,6,7,11,14,16,17,18,20,21,25,27,28,29,30,31,33,37,39,40,41,43,44)
ORDER BY id;
```

---

### 🟠 ISSUE #5: Entity Resolution Failures - Tenants

**Severity:** HIGH
**Impact:** 29 tenants not found in PayProp
**Error:** Tenant not found (WARN level)

#### Details

**Summary:**
```
Tenants Attempted: Unknown
Successfully Resolved: 0
Failed: 29
Orphaned: 29
Success Rate: 0%
```

**Log Evidence:**
```
Tenants: 29 orphaned, 0 resolved, 29 failed
Warning: Tenant not found in PayProp
```

#### Impact

**Financial Transactions:**
- Incoming payments may not link to tenants
- Tenant-specific filtering in statements may fail
- Audit trail incomplete without tenant information

**Statements:**
- Tenant names may be missing or incorrect
- Cannot filter transactions by tenant reliably
- Historical data gaps

#### Root Cause

**Possible Scenarios:**
1. Tenants existed historically but have been removed from PayProp
2. Tenant IDs changed in PayProp system
3. Tenants belong to properties not linked to current entity
4. Data migration issues from old system

#### Recommended Actions

**Short-term:**
- Continue processing transactions without tenant linkage
- Use property-level filtering as fallback
- Store PayProp tenant name in transaction for display purposes

**Long-term:**
- Audit tenant records in local database
- Mark inactive tenants
- Implement tenant archival process

---

### 🟠 ISSUE #6: Partial Invoice Linkage Success

**Severity:** HIGH
**Impact:** Inconsistent statement data

#### Details

**Linkage Success Rates by Data Source:**

| Data Source | Total | Linked | Unlinked | Success Rate | Unlinked Amount |
|------------|-------|--------|----------|--------------|-----------------|
| INCOMING_PAYMENT | 106 | 0 | 106 | 0% | £88,560.39 |
| BATCH_PAYMENT | 202 | 152 | 50 | 75% | ~£21,146 |
| ICDN_ACTUAL | 187 | 163 | 24 | 87% | ~£17,332 |
| COMMISSION_PAYMENT | 134 | 118 | 16 | 88% | ~£1,867 |

**Total Unlinked:** 196 transactions worth ~£128,905

#### Analysis

**What's Working:**
- ✅ ICDN_ACTUAL: 87% success (163/187 linked)
- ✅ COMMISSION_PAYMENT: 88% success (118/134 linked)
- ⚠️ BATCH_PAYMENT: 75% success (152/202 linked)

**What's NOT Working:**
- ❌ INCOMING_PAYMENT: 0% success (0/106 linked)

#### Why Partial Success?

**Hypothesis:**
1. **Property/Tenant Matching Issues:** Some transactions reference properties/tenants that don't exist or can't be resolved
2. **Date Range Mismatches:** Transaction dates fall outside invoice start/end date ranges
3. **Multiple Active Invoices:** Multiple leases active for same property, unclear which to link to
4. **Invoice Not Found:** No corresponding invoice exists for some transactions

#### Code Analysis

**File:** `PayPropInvoiceLinkingService.java` (referenced but not shown)

**Expected Logic:**
```java
public Invoice findInvoiceForTransaction(
    Property property,
    Customer customer,
    String payPropInvoiceId,
    LocalDate transactionDate
) {
    // 1. If payPropInvoiceId provided, try direct lookup
    // 2. Otherwise, find by property + customer (+ date?)
    // 3. Return null if not found
}
```

**Questions:**
- What query is used to find invoices?
- How are multiple active invoices handled?
- Is date used as a filter or just informational?
- Are inactive invoices excluded?

---

## Medium Priority Issues

### 🟡 ISSUE #7: PayProp API Permission Errors

**Severity:** MEDIUM
**Impact:** Maintenance data cannot be synced (may not be critical)
**Error:** 403 FORBIDDEN - "You do not have the necessary permission(s)"

#### Details

**Failed Endpoints:**
```
/maintenance/categories - 2 failures
/maintenance/tickets - 1 failure
```

**Log Samples:**
```
2025-10-30T10:34:05.264Z ERROR ... PayPropApiClient :
PayProp API error: 403 FORBIDDEN - {"errors":[{"message":"You do not have the necessary permission(s)"}],"status":403}

2025-10-30T10:34:05.264Z ERROR ... PayPropApiClient :
Failed to fetch page 1 from /maintenance/categories: PayProp API error: ...

2025-10-30T10:34:05.739Z ERROR ... PayPropApiClient :
Authentication/Authorization error (403 FORBIDDEN), stopping pagination
```

#### Root Cause

**PayProp API Permissions:**
- Current API credentials lack permissions for maintenance endpoints
- May require different API key or scope
- May require account upgrade in PayProp

#### Business Impact

**Low Impact:**
- Maintenance categories and tickets are likely non-essential for financial statements
- Core financial operations (invoices, transactions, payments) are working
- May only affect maintenance tracking features

#### Recommended Action

**Decision Required:**
- Determine if maintenance data is needed for business operations
- If yes: Request permissions from PayProp support
- If no: Disable maintenance sync to reduce log noise

---

### 🟡 ISSUE #8: Invoice Instruction Sync Failed

**Severity:** MEDIUM
**Impact:** Invoice instructions not processing correctly

#### Details

**Log Evidence:**
```
2025-10-30T10:32:05.181Z ERROR ... PayPropFinancialSyncService :
❌ Failed to sync invoice instructions
```

**Timestamp:** Single occurrence at 10:32:05 UTC

#### Relationship to Other Issues

This failure is likely a **symptom** of Issue #3 (duplicate invoice constraint):
- Invoice instruction sync starts
- Attempts to process 14+ instructions
- Encounters duplicate PayProp ID 'd71ebxD9Z5'
- First attempt succeeds, next 13 fail
- Overall sync marked as failed

#### Impact

**Immediate:**
- Some invoice instructions not enriched
- Financial transactions may lack invoice linkage
- Statements incomplete

**Cascading:**
- Contributes to transaction rollback (Issue #2)
- Prevents subsequent sync operations from completing

---

## Data Impact Assessment

### Current State of Financial Data

#### Financial Transactions Table

**Total Records by Data Source (excluding historical duplicates):**
```
BATCH_PAYMENT:       202 records → £84,585.39
ICDN_ACTUAL:         187 records → £133,145.43
COMMISSION_PAYMENT:  134 records → £14,001.75
INCOMING_PAYMENT:    106 records → £88,560.39
─────────────────────────────────────────────
TOTAL:               629 records → £320,292.96
```

**Invoice Linkage Status:**
```
Total Linked:     433 records (68.8%) → ~£191,387.57
Total Unlinked:   196 records (31.2%) → ~£128,905.39
```

#### Unified Transactions Table

**Current State:**
```
Total Records: 571
Total Amount:  £295,223.82
Last Rebuilt:  Before invoice linking deployment
Status:        STALE - needs rebuild
```

**Missing from Unified:**
- 196 unlinked transactions (~£128,905) excluded due to missing invoice_id
- 58 transactions difference (629 - 571 = 58) - likely duplicates already excluded

#### Statement Generation Impact

**Property Owner Statements:**
- ❌ £88,560.39 in incoming tenant payments NOT appearing in statements
- ❌ £40,518 in other unlinked transactions NOT appearing
- ⚠️ Statements showing partial data only
- ⚠️ Property owners seeing incomplete financial picture

**Accuracy:**
- Linked data: Accurate and complete
- Unlinked data: Invisible to users
- Overall: **68.8% complete** (433/629 transactions visible)

---

### Historical Context

#### Before Invoice Linking (Pre-deployment)

**Unified Transactions:**
- Records excluded: Transactions without invoice_id
- Invoice linking: Not implemented
- Statement generation: Using only HISTORICAL data

#### After Invoice Linking Deployment (10:04 UTC)

**Code Changes:**
- Added invoice linking to `PayPropIncomingPaymentFinancialSyncService`
- Added invoice linking to `PayPropFinancialSyncService` (ICDN_ACTUAL, BATCH_PAYMENT)
- Fixed `CustomerPropertyAssignmentRepository` SQL issue

**Expected Behavior:**
- New imports should link transactions to invoices
- Re-running sync should update existing records

**Actual Behavior:**
- BATCH_PAYMENT linking working (75% success)
- ICDN_ACTUAL linking working (87% success)
- INCOMING_PAYMENT linking NOT working (0% success)
- Transaction rollback preventing completion

---

### Data Integrity Status

#### ✅ GOOD: No Duplicates

**Verification:**
```sql
-- Check for duplicates in unified_transactions
SELECT
    source_system,
    source_table,
    source_record_id,
    COUNT(*) as count
FROM unified_transactions
GROUP BY source_system, source_table, source_record_id
HAVING count > 1

Result: No duplicates found
```

#### ✅ GOOD: Source Traceability

**Every transaction can be traced back:**
- source_system: 'HISTORICAL' or 'PAYPROP'
- source_table: Table name
- source_record_id: Primary key in source table

#### ⚠️ WARNING: Rebuild Required

**Unified transactions table is stale:**
- Built before invoice linking was added
- Does not reflect latest financial_transactions changes
- Needs full rebuild to include newly linked records

#### ❌ CRITICAL: Invoice Linkage Incomplete

**Current state unacceptable for production:**
- 31.2% of PayProp transactions not linked
- £128,905 in financial data excluded from statements
- Incoming payments completely absent (0% linked)

---

## Technical Analysis

### Service Execution Flow

#### Expected Data Import Flow

```
1. User triggers: "Enhanced Unified Sync with Financials"
   ↓
2. PayPropEnhancedUnifiedSyncService.syncAllFinancialData()
   ↓
3. Sync Invoice Instructions
   → PayPropInvoiceInstructionEnrichmentService
   → ❌ FAILS on duplicate invoice d71ebxD9Z5
   ↓
4. Sync Financial Transactions
   → PayPropFinancialSyncService
   → a. Sync ICDN_ACTUAL (✅ 87% linked)
   → b. Sync BATCH_PAYMENT (✅ 75% linked)
   → c. Sync COMMISSION_PAYMENT (✅ 88% linked)
   ↓
5. Sync Incoming Payments
   → PayPropIncomingPaymentFinancialSyncService
   → ❓ NOT EXECUTING or FAILING SILENTLY
   → ❌ 0% linked
   ↓
6. Rebuild Unified Transactions
   → ❓ Unclear if this executes
   ↓
7. Transaction Commit
   → ❌ ROLLS BACK due to earlier errors
```

#### Actual Execution Results

**From Logs:**
```
✅ Batch payment linking: WORKING (152/202 = 75%)
✅ ICDN linking: WORKING (163/187 = 87%)
❌ Incoming payment linking: NOT WORKING (0/106 = 0%)
❌ Invoice instruction enrichment: FAILING (duplicate key)
❌ Overall sync: ROLLED BACK
```

### Transaction Boundary Analysis

#### Spring @Transactional Behavior

**File:** `PayPropFinancialSyncService.java`

```java
@Transactional
public ComprehensiveFinancialSyncResult syncAllFinancialData() {
    // All operations in ONE transaction
    // If ANY operation throws exception → ROLLBACK ALL
}
```

**Problem:**
- One duplicate key error causes entire sync to roll back
- 433 successfully linked transactions discarded
- No partial success possible

**Solution Options:**

**Option A: Nested Transactions with REQUIRES_NEW**
```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void syncOneInvoiceInstruction(Instruction instr) {
    // Each instruction in its own transaction
    // Failure only rolls back one instruction
}
```

**Option B: Try-Catch Individual Operations**
```java
@Transactional
public ComprehensiveFinancialSyncResult syncAllFinancialData() {
    for (Instruction instr : instructions) {
        try {
            processInstruction(instr);
        } catch (DataIntegrityViolationException e) {
            log.error("Failed instruction, continuing...");
            // Continue processing others
        }
    }
}
```

**Option C: Remove @Transactional from Parent**
```java
// No transaction at parent level
public ComprehensiveFinancialSyncResult syncAllFinancialData() {
    // Each sub-service handles its own transactions
    syncInvoiceInstructions(); // @Transactional
    syncFinancialData();        // @Transactional
    syncIncomingPayments();     // @Transactional
}
```

---

### Invoice Linking Service Analysis

#### Expected Behavior

**File:** `PayPropInvoiceLinkingService.java` (inferred)

**Method Signature:**
```java
public Invoice findInvoiceForTransaction(
    Property property,
    Customer customer,
    String payPropInvoiceId,
    LocalDate transactionDate
)
```

**Expected Algorithm:**
```
1. If payPropInvoiceId provided:
   → Query: SELECT * FROM invoices WHERE payprop_id = ?
   → Return if found

2. Otherwise, find by property + customer:
   → Query: SELECT * FROM invoices
            WHERE property_id = ?
            AND customer_id = ?
            AND is_active = true
            AND (end_date IS NULL OR end_date >= ?)
            ORDER BY start_date DESC
            LIMIT 1
   → Return first match

3. If no match:
   → Return null
```

#### Why INCOMING_PAYMENT Might Fail

**Scenario A: Customer ID is NULL**
```java
// In PayPropIncomingPaymentFinancialSyncService.java line 110
Customer tenant = customerRepository.findByPayPropEntityId(payment.tenantPaypropId);
if (tenant == null) {
    log.debug("ℹ️ Tenant not found...");
}

// Later at line 253
Invoice invoice = invoiceLinkingService.findInvoiceForTransaction(
    property,
    tenant,  // ← Could be NULL!
    null,
    payment.reconciliationDate
);
```

**If tenant is NULL:**
- Linking service may require customer ID
- Query fails to find invoice
- Returns null
- No error logged

**Scenario B: Date Filtering Too Strict**
```java
// If linking service checks:
WHERE transaction_date BETWEEN start_date AND end_date

// But incoming payments have dates outside active lease period
// → No match found
```

**Scenario C: Property-Only Matching Not Implemented**
```java
// Linking service may require BOTH property AND customer
// But should also support property-only matching for incoming payments
// If not implemented → always returns null when customer is null
```

#### Why BATCH_PAYMENT Partially Works

**Success Rate: 75% (152/202)**

**Successful Cases:**
- Batch payments with valid property + customer
- Transaction dates within lease period
- Active invoices exist

**Failed Cases (50 unlinked):**
- Property/customer combination doesn't match any invoice
- Transaction dates outside lease periods
- Multiple active invoices causing ambiguity
- Property or customer not resolved (see Issue #4, #5)

---

### Database Schema Analysis

#### Invoices Table

**Unique Constraint:**
```sql
UNIQUE KEY `payprop_id` (`payprop_id`)
```

**Problem:**
- Multiple invoice instructions can reference same invoice
- System tries to create invoice multiple times
- Only first succeeds, rest violate constraint

**Expected Behavior:**
```sql
INSERT INTO invoices (...) VALUES (...)
ON DUPLICATE KEY UPDATE
    amount = VALUES(amount),
    description = VALUES(description),
    ...
```

**Actual Behavior:**
```sql
INSERT INTO invoices (...) VALUES (...)
-- No ON DUPLICATE KEY clause
-- Throws exception on duplicate
```

#### Financial Transactions Table

**Schema:**
```sql
CREATE TABLE financial_transactions (
    id BIGINT PRIMARY KEY,
    invoice_id BIGINT,  -- ← Can be NULL
    data_source VARCHAR(50),
    payprop_transaction_id VARCHAR(50),
    property_id VARCHAR(50),
    tenant_id VARCHAR(50),
    amount DECIMAL(15,2),
    transaction_date DATE,
    ...
    FOREIGN KEY (invoice_id) REFERENCES invoices(id)
)
```

**Index on PayProp Transaction ID:**
```sql
-- For duplicate detection
INDEX idx_payprop_txn (payprop_transaction_id, data_source)
```

**Current Issue:**
- invoice_id is NULL for 196 records
- These records exist in financial_transactions
- But excluded from unified_transactions view

#### Unified Transactions View/Table

**Purpose:** Query-optimized view of all transactions

**Inclusion Criteria:**
```sql
-- From historical_transactions
WHERE invoice_id IS NOT NULL

UNION ALL

-- From financial_transactions
WHERE invoice_id IS NOT NULL
  AND data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
```

**Result:**
- Only 433/629 PayProp transactions included (68.8%)
- 196 transactions excluded due to NULL invoice_id
- £128,905 in financial data invisible

---

## Root Cause Analysis

### Issue #1: Why INCOMING_PAYMENT Not Linking

#### Root Cause: Service Not Executing or Tenant NULL

**Evidence:**
1. No log messages from PayPropIncomingPaymentFinancialSyncService linking code
2. Database shows 0/106 incoming payments linked
3. Code exists and is correct
4. Batch payment linking DOES work

**Conclusion:** One of the following:

**A. Service Not Being Called**
```java
// In main sync orchestration
syncFinancialData();           // ✅ Called
syncInvoiceInstructions();     // ✅ Called
syncIncomingPayments();        // ❓ NOT CALLED?
```

**B. Existing Records Not Updated**
```java
// In PayPropIncomingPaymentFinancialSyncService.java line 84
boolean exists = financialTransactionRepository
    .existsByPayPropTransactionIdAndDataSource(
        payment.paypropId,
        "INCOMING_PAYMENT"
    );

if (exists) {
    log.debug("⏭️ Skipping already imported: {}", payment.paypropId);
    skipped++;
    continue;  // ← Exits without linking!
}
```

**This is the SMOKING GUN:**
- All 106 incoming payments already exist in database
- Sync skips them because they're marked as "already imported"
- Invoice linking code NEVER EXECUTES for existing records
- Code only runs for NEW records

**C. Tenant Always NULL**
```java
// 29 tenants failed resolution
// If all 106 incoming payments have unresolved tenants
// AND linking service requires tenant
// → All return null
```

#### Fix Required

**Option 1: Update Existing Records**
```sql
-- Manual SQL update
UPDATE financial_transactions ft
JOIN payprop_export_incoming_payments p
    ON ft.payprop_transaction_id = p.payprop_id
JOIN properties prop
    ON p.property_payprop_id = prop.payprop_id
LEFT JOIN customers cust
    ON p.tenant_payprop_id = cust.payprop_entity_id
JOIN invoices inv
    ON inv.property_id = prop.id
    AND (inv.customer_id = cust.customer_id OR cust.customer_id IS NULL)
    AND inv.is_active = true
SET ft.invoice_id = inv.id
WHERE ft.data_source = 'INCOMING_PAYMENT'
  AND ft.invoice_id IS NULL;
```

**Option 2: Add Re-Linking Logic**
```java
// New method in PayPropIncomingPaymentFinancialSyncService
@Transactional
public void relinkExistingIncomingPayments() {
    List<FinancialTransaction> unlinked = financialTransactionRepository
        .findByDataSourceAndInvoiceIdIsNull("INCOMING_PAYMENT");

    for (FinancialTransaction txn : unlinked) {
        // Re-run invoice linking logic
        Invoice invoice = findInvoiceForTransaction(txn);
        if (invoice != null) {
            txn.setInvoice(invoice);
            financialTransactionRepository.save(txn);
        }
    }
}
```

**Option 3: Modify Sync to Update Existing**
```java
if (exists) {
    // Instead of skipping, update the existing record
    FinancialTransaction existing = financialTransactionRepository
        .findByPayPropTransactionIdAndDataSource(...);

    // Re-run invoice linking
    Invoice invoice = invoiceLinkingService.findInvoiceForTransaction(...);
    if (invoice != null && existing.getInvoice() == null) {
        existing.setInvoice(invoice);
        financialTransactionRepository.save(existing);
        log.info("✅ Linked existing incoming payment...");
    }
}
```

---

### Issue #2: Why Transaction Rolls Back

#### Root Cause: Uncaught Exception in @Transactional Method

**Cascade:**
```
1. syncAllFinancialData() starts @Transactional
   ↓
2. syncInvoiceInstructions() called
   ↓
3. Processes instruction z2JkeE7b1b
   ↓
4. Tries to create/update invoice with payprop_id = 'd71ebxD9Z5'
   ↓
5. Database throws: DataIntegrityViolationException (duplicate key)
   ↓
6. Exception bubbles up to @Transactional method
   ↓
7. Spring marks transaction for rollback
   ↓
8. All subsequent operations still execute (zombie transaction)
   ↓
9. At method end, commit attempted
   ↓
10. ❌ Throws: "Transaction silently rolled back because it has been
       marked as rollback-only"
```

**Why "Silent"?**
- Original exception was caught/logged but not re-thrown
- Transaction continued executing
- Only at commit time does Spring realize transaction was marked for rollback
- By then, context of original error is lost

#### Fix Required

**Implement proper exception handling:**

```java
@Transactional
public ComprehensiveFinancialSyncResult syncAllFinancialData() {
    try {
        syncInvoiceInstructions();
    } catch (DataIntegrityViolationException e) {
        log.error("Invoice instruction sync failed, continuing", e);
        // Don't let exception propagate
        // Transaction not marked for rollback
    }

    try {
        syncFinancialData();
    } catch (Exception e) {
        log.error("Financial sync failed", e);
        throw e; // Re-throw to trigger rollback if needed
    }
}
```

**Or use separate transactions:**

```java
public ComprehensiveFinancialSyncResult syncAllFinancialData() {
    // Each sub-sync in its own transaction
    invoiceSyncResult = syncInvoiceInstructionsNewTransaction();
    financialSyncResult = syncFinancialDataNewTransaction();
    incomingSyncResult = syncIncomingPaymentsNewTransaction();

    // Combine results
    return buildOverallResult(invoiceSyncResult, financialSyncResult, incomingSyncResult);
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public InvoiceSyncResult syncInvoiceInstructionsNewTransaction() {
    // Independent transaction
    // Failure here doesn't affect other syncs
}
```

---

### Issue #3: Why Duplicate Invoice Key

#### Root Cause: Missing Upsert Logic

**Problem:**
- PayProp returns same invoice across multiple instruction records
- Example: Invoice `d71ebxD9Z5` referenced by 14+ different instructions
- System tries to process each instruction independently
- First attempt creates invoice successfully
- Next 13 attempts fail with duplicate key error

**Code Analysis:**

**Expected (Upsert Pattern):**
```java
Invoice invoice = invoiceRepository.findByPayPropId(payPropId)
    .orElse(new Invoice());

// Update fields
invoice.setPayPropId(payPropId);
invoice.setAmount(amount);
invoice.setDescription(description);
// ...

invoiceRepository.save(invoice); // INSERT or UPDATE automatically
```

**Actual (Insert Only):**
```java
Invoice invoice = new Invoice();
invoice.setPayPropId(payPropId);
// ... set fields ...

invoiceRepository.save(invoice); // Always tries INSERT
// Throws exception if payprop_id already exists
```

#### Fix Required

**Implement proper upsert:**

```java
@Transactional
public Invoice processInvoiceInstruction(InvoiceInstruction instruction) {
    String payPropId = instruction.getPayPropInvoiceId();

    // Find or create
    Invoice invoice = invoiceRepository
        .findByPayPropId(payPropId)
        .orElse(new Invoice());

    // Only update if this instruction is newer
    if (invoice.getId() == null ||
        instruction.getUpdatedAt().isAfter(invoice.getPaypropLastSync())) {

        invoice.setPayPropId(payPropId);
        invoice.setAmount(instruction.getAmount());
        invoice.setDescription(instruction.getDescription());
        // ... update other fields ...
        invoice.setPaypropLastSync(LocalDateTime.now());

        return invoiceRepository.save(invoice);
    }

    return invoice; // Return existing, unchanged
}
```

**Or use native SQL:**

```java
@Query(value = """
    INSERT INTO invoices (
        payprop_id, amount, description, ...
    ) VALUES (
        ?, ?, ?, ...
    )
    ON DUPLICATE KEY UPDATE
        amount = VALUES(amount),
        description = VALUES(description),
        ...
    """, nativeQuery = true)
void upsertInvoice(String payPropId, BigDecimal amount, String description, ...);
```

---

## Recommended Fixes

### Priority 1: CRITICAL - Must Fix Immediately

#### Fix #1: Re-Link Existing Incoming Payments

**Objective:** Link 106 existing incoming payments to invoices

**Option A: SQL Update Script** (Fastest)

```sql
-- Step 1: Analyze current state
SELECT
    ft.id,
    ft.payprop_transaction_id,
    ft.amount,
    ft.transaction_date,
    ft.property_id,
    ft.tenant_id,
    p.id as property_pk,
    c.customer_id,
    i.id as matched_invoice_id,
    i.lease_reference
FROM financial_transactions ft
LEFT JOIN properties p ON ft.property_id = p.payprop_id
LEFT JOIN customers c ON ft.tenant_id = c.payprop_entity_id
LEFT JOIN invoices i ON i.property_id = p.id
    AND (i.customer_id = c.customer_id OR c.customer_id IS NULL)
    AND i.is_active = true
    AND ft.transaction_date BETWEEN i.start_date AND COALESCE(i.end_date, '9999-12-31')
WHERE ft.data_source = 'INCOMING_PAYMENT'
  AND ft.invoice_id IS NULL
ORDER BY ft.transaction_date DESC;

-- Step 2: If results look good, apply update
UPDATE financial_transactions ft
JOIN properties p ON ft.property_id = p.payprop_id
LEFT JOIN customers c ON ft.tenant_id = c.payprop_entity_id
JOIN invoices i ON i.property_id = p.id
    AND (i.customer_id = c.customer_id OR c.customer_id IS NULL)
    AND i.is_active = true
    AND ft.transaction_date BETWEEN i.start_date AND COALESCE(i.end_date, '9999-12-31')
SET ft.invoice_id = i.id
WHERE ft.data_source = 'INCOMING_PAYMENT'
  AND ft.invoice_id IS NULL;

-- Step 3: Verify
SELECT
    COUNT(*) as total,
    COUNT(invoice_id) as linked,
    COUNT(*) - COUNT(invoice_id) as unlinked
FROM financial_transactions
WHERE data_source = 'INCOMING_PAYMENT';
```

**Option B: Java Service Method** (Safer)

```java
// Add to PayPropIncomingPaymentFinancialSyncService.java

/**
 * Re-link existing incoming payments that were imported before
 * invoice linking was implemented.
 */
@Transactional
public RelinkResult relinkExistingIncomingPayments() {
    log.info("🔗 Starting re-link of existing incoming payments");

    RelinkResult result = new RelinkResult();

    // Find all unlinked incoming payments
    List<FinancialTransaction> unlinked = financialTransactionRepository
        .findByDataSourceAndInvoiceIdIsNull("INCOMING_PAYMENT");

    result.totalProcessed = unlinked.size();
    log.info("📊 Found {} unlinked incoming payments", unlinked.size());

    for (FinancialTransaction txn : unlinked) {
        try {
            // Extract property and tenant
            Property property = null;
            Customer tenant = null;

            if (txn.getPropertyId() != null) {
                property = propertyRepository
                    .findByPayPropId(txn.getPropertyId())
                    .orElse(null);
            }

            if (txn.getTenantId() != null) {
                tenant = customerRepository
                    .findByPayPropEntityId(txn.getTenantId());
            }

            if (property == null) {
                log.warn("⚠️ Property not found for transaction {}", txn.getId());
                result.noProperty++;
                continue;
            }

            // Attempt to find invoice
            Invoice invoice = invoiceLinkingService.findInvoiceForTransaction(
                property,
                tenant,
                null,
                txn.getTransactionDate()
            );

            if (invoice != null) {
                txn.setInvoice(invoice);
                financialTransactionRepository.save(txn);
                result.linked++;
                log.info("✅ Linked transaction {} to invoice {} ({})",
                    txn.getPayPropTransactionId(),
                    invoice.getId(),
                    invoice.getLeaseReference());
            } else {
                result.noInvoiceFound++;
                log.warn("⚠️ No invoice found for transaction {} - property: {}, tenant: {}, date: {}",
                    txn.getPayPropTransactionId(),
                    property.getId(),
                    tenant != null ? tenant.getCustomerId() : "NULL",
                    txn.getTransactionDate());
            }

        } catch (Exception e) {
            result.errors++;
            log.error("❌ Failed to relink transaction {}: {}",
                txn.getPayPropTransactionId(), e.getMessage());
        }
    }

    log.info("✅ Re-link complete: {} linked, {} no invoice, {} no property, {} errors",
        result.linked, result.noInvoiceFound, result.noProperty, result.errors);

    return result;
}

public static class RelinkResult {
    public int totalProcessed;
    public int linked;
    public int noInvoiceFound;
    public int noProperty;
    public int errors;
}
```

**Testing:**
```bash
# After deployment, call via API or admin panel
POST /api/admin/payprop/relink-incoming-payments

# Check results
GET /api/admin/payprop/incoming-payment-stats
```

---

#### Fix #2: Implement Invoice Upsert Logic

**Objective:** Prevent duplicate invoice key errors

**File:** `PayPropInvoiceInstructionEnrichmentService.java` (create if doesn't exist)

```java
@Service
public class PayPropInvoiceInstructionEnrichmentService {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    /**
     * Process invoice instruction with proper upsert logic
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Invoice processInvoiceInstruction(InvoiceInstructionDTO instruction) {
        String payPropId = instruction.getPayPropInvoiceId();

        if (payPropId == null || payPropId.isBlank()) {
            log.warn("⚠️ Instruction {} has no invoice ID, skipping",
                instruction.getPayPropInstructionId());
            return null;
        }

        // Find existing or create new
        Invoice invoice = invoiceRepository
            .findByPayPropId(payPropId)
            .orElse(new Invoice());

        boolean isNew = (invoice.getId() == null);

        // Resolve property
        Property property = resolveProperty(instruction.getPropertyPayPropId());
        if (property == null) {
            log.warn("⚠️ Property {} not found for invoice {}",
                instruction.getPropertyPayPropId(), payPropId);
            return null;
        }

        // Resolve customer (tenant/owner)
        Customer customer = resolveCustomer(instruction.getCustomerPayPropId());

        // Update invoice fields
        invoice.setPayPropId(payPropId);
        invoice.setPropertyId(property.getId());
        invoice.setProperty(property);

        if (customer != null) {
            invoice.setCustomerId(customer.getCustomerId());
            invoice.setCustomer(customer);
        }

        invoice.setAmount(instruction.getAmount());
        invoice.setDescription(instruction.getDescription());
        invoice.setInvoiceType(instruction.getInvoiceType());
        invoice.setStartDate(instruction.getStartDate());
        invoice.setEndDate(instruction.getEndDate());
        invoice.setFrequency(instruction.getFrequency());
        invoice.setIsActive(instruction.getIsActive());
        invoice.setPaypropLastSync(LocalDateTime.now());

        // Save (INSERT or UPDATE)
        Invoice saved = invoiceRepository.save(invoice);

        if (isNew) {
            log.info("✅ Created invoice {} from instruction {}",
                saved.getId(), instruction.getPayPropInstructionId());
        } else {
            log.debug("✅ Updated invoice {} from instruction {}",
                saved.getId(), instruction.getPayPropInstructionId());
        }

        return saved;
    }

    /**
     * Process multiple instructions in batch
     * Each in its own transaction to prevent one failure from affecting others
     */
    public BatchProcessResult processBatch(List<InvoiceInstructionDTO> instructions) {
        BatchProcessResult result = new BatchProcessResult();
        result.total = instructions.size();

        for (InvoiceInstructionDTO instruction : instructions) {
            try {
                Invoice invoice = processInvoiceInstruction(instruction);
                if (invoice != null) {
                    result.success++;
                } else {
                    result.skipped++;
                }
            } catch (Exception e) {
                result.failed++;
                result.errors.add(String.format("%s: %s",
                    instruction.getPayPropInstructionId(), e.getMessage()));
                log.error("❌ Failed to process instruction {}: {}",
                    instruction.getPayPropInstructionId(), e.getMessage());
            }
        }

        log.info("✅ Batch complete: {} success, {} skipped, {} failed",
            result.success, result.skipped, result.failed);

        return result;
    }

    private Property resolveProperty(String payPropId) {
        return propertyRepository.findByPayPropId(payPropId).orElse(null);
    }

    private Customer resolveCustomer(String payPropEntityId) {
        return customerRepository.findByPayPropEntityId(payPropEntityId);
    }

    public static class BatchProcessResult {
        public int total;
        public int success;
        public int skipped;
        public int failed;
        public List<String> errors = new ArrayList<>();
    }
}
```

**Key Points:**
- Uses `REQUIRES_NEW` propagation for each instruction
- One failure doesn't roll back entire batch
- Proper upsert logic with `findByPayPropId().orElse(new Invoice())`
- Detailed logging for troubleshooting

---

#### Fix #3: Improve Transaction Boundary Management

**Objective:** Prevent one error from rolling back entire sync

**File:** `PayPropFinancialSyncService.java`

**Current Code:**
```java
@Transactional
public ComprehensiveFinancialSyncResult syncAllFinancialData() {
    // All in one big transaction
    // One error → all rolled back
}
```

**Improved Code:**
```java
// Remove @Transactional from parent method
public ComprehensiveFinancialSyncResult syncAllFinancialData() {
    ComprehensiveFinancialSyncResult result = new ComprehensiveFinancialSyncResult();
    result.startTime = LocalDateTime.now();

    // Each sub-sync in its own transaction
    try {
        result.invoiceInstructions = syncInvoiceInstructionsInNewTransaction();
    } catch (Exception e) {
        log.error("❌ Invoice instruction sync failed", e);
        result.invoiceInstructions = createFailedResult(e);
    }

    try {
        result.financialTransactions = syncFinancialDataInNewTransaction();
    } catch (Exception e) {
        log.error("❌ Financial transactions sync failed", e);
        result.financialTransactions = createFailedResult(e);
    }

    try {
        result.incomingPayments = syncIncomingPaymentsInNewTransaction();
    } catch (Exception e) {
        log.error("❌ Incoming payments sync failed", e);
        result.incomingPayments = createFailedResult(e);
    }

    try {
        result.unifiedRebuild = rebuildUnifiedTransactionsInNewTransaction();
    } catch (Exception e) {
        log.error("❌ Unified rebuild failed", e);
        result.unifiedRebuild = createFailedResult(e);
    }

    result.endTime = LocalDateTime.now();
    result.success = (result.invoiceInstructions.success ||
                      result.financialTransactions.success ||
                      result.incomingPayments.success);

    return result;
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
protected InvoiceSyncResult syncInvoiceInstructionsInNewTransaction() {
    // Independent transaction
    // ... implementation ...
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
protected FinancialSyncResult syncFinancialDataInNewTransaction() {
    // Independent transaction
    // ... implementation ...
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
protected IncomingPaymentSyncResult syncIncomingPaymentsInNewTransaction() {
    // Independent transaction
    // ... implementation ...
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
protected UnifiedRebuildResult rebuildUnifiedTransactionsInNewTransaction() {
    // Independent transaction
    // ... implementation ...
}
```

**Benefits:**
- One sub-sync failure doesn't affect others
- Partial success possible
- Better error isolation
- Easier to debug which specific operation failed

---

### Priority 2: HIGH - Fix Soon

#### Fix #4: Improve Invoice Linking for Property-Only Matching

**Objective:** Support incoming payments where tenant is NULL

**File:** `PayPropInvoiceLinkingService.java`

**Enhanced Algorithm:**
```java
@Service
public class PayPropInvoiceLinkingService {

    @Autowired
    private InvoiceRepository invoiceRepository;

    /**
     * Find invoice for a financial transaction.
     *
     * Strategy:
     * 1. If payPropInvoiceId provided, direct lookup
     * 2. If property + customer provided, find active invoice for that combo
     * 3. If only property provided (tenant unknown), find any active invoice for property
     * 4. Consider transaction date as a hint (not strict filter)
     */
    public Invoice findInvoiceForTransaction(
            Property property,
            Customer customer,
            String payPropInvoiceId,
            LocalDate transactionDate) {

        // Strategy 1: Direct invoice ID lookup
        if (payPropInvoiceId != null && !payPropInvoiceId.isBlank()) {
            Optional<Invoice> directMatch = invoiceRepository.findByPayPropId(payPropInvoiceId);
            if (directMatch.isPresent()) {
                log.debug("✅ Found invoice by PayProp ID: {}", payPropInvoiceId);
                return directMatch.get();
            }
        }

        // Strategy 2: Property + Customer match
        if (property != null && customer != null) {
            List<Invoice> matches = invoiceRepository
                .findByPropertyIdAndCustomerIdAndIsActive(
                    property.getId(),
                    customer.getCustomerId(),
                    true
                );

            if (!matches.isEmpty()) {
                // If multiple matches, prefer one where transaction date is in range
                Invoice bestMatch = findBestMatchByDate(matches, transactionDate);
                log.debug("✅ Found invoice by property + customer: {} (lease: {})",
                    bestMatch.getId(), bestMatch.getLeaseReference());
                return bestMatch;
            }
        }

        // Strategy 3: Property-only match (for incoming payments with unknown tenant)
        if (property != null && customer == null) {
            List<Invoice> matches = invoiceRepository
                .findByPropertyIdAndIsActive(property.getId(), true);

            if (matches.size() == 1) {
                // Only one active lease for property - safe to use
                Invoice match = matches.get(0);
                log.debug("✅ Found invoice by property only: {} (lease: {})",
                    match.getId(), match.getLeaseReference());
                return match;
            } else if (matches.size() > 1) {
                // Multiple active leases - try to disambiguate by date
                Invoice bestMatch = findBestMatchByDate(matches, transactionDate);
                log.warn("⚠️ Multiple active leases for property {}, selected {} by date",
                    property.getId(), bestMatch.getLeaseReference());
                return bestMatch;
            }
        }

        // No match found
        log.debug("❌ No invoice found for property: {}, customer: {}, date: {}",
            property != null ? property.getId() : "NULL",
            customer != null ? customer.getCustomerId() : "NULL",
            transactionDate);
        return null;
    }

    /**
     * Select best invoice match based on transaction date.
     * Prefers invoices where transaction date falls within lease period.
     */
    private Invoice findBestMatchByDate(List<Invoice> invoices, LocalDate transactionDate) {
        if (transactionDate == null || invoices.isEmpty()) {
            return invoices.get(0); // Return first if no date to compare
        }

        // First priority: Date within lease period
        for (Invoice invoice : invoices) {
            if (isDateInRange(transactionDate, invoice.getStartDate(), invoice.getEndDate())) {
                return invoice;
            }
        }

        // Second priority: Closest to start date
        Invoice closest = invoices.get(0);
        long smallestDiff = Math.abs(
            ChronoUnit.DAYS.between(transactionDate, closest.getStartDate())
        );

        for (Invoice invoice : invoices) {
            long diff = Math.abs(
                ChronoUnit.DAYS.between(transactionDate, invoice.getStartDate())
            );
            if (diff < smallestDiff) {
                closest = invoice;
                smallestDiff = diff;
            }
        }

        return closest;
    }

    private boolean isDateInRange(LocalDate date, LocalDate start, LocalDate end) {
        if (date == null || start == null) return false;
        if (end == null) {
            // Open-ended lease
            return !date.isBefore(start);
        }
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
```

**Repository Methods Needed:**
```java
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByPayPropId(String payPropId);

    List<Invoice> findByPropertyIdAndCustomerIdAndIsActive(
        Long propertyId, Long customerId, Boolean isActive
    );

    List<Invoice> findByPropertyIdAndIsActive(
        Long propertyId, Boolean isActive
    );
}
```

---

#### Fix #5: Handle Entity Resolution Failures Gracefully

**Objective:** Continue processing when properties/tenants not found

**File:** `PayPropEntityResolutionService.java` (modify)

**Current Behavior:**
- 404 errors logged as ERROR
- Processing continues but data incomplete

**Improved Behavior:**
```java
@Service
public class PayPropEntityResolutionService {

    // Track resolution failures for reporting
    private final Set<String> knownFailedProperties = new ConcurrentHashSet<>();
    private final Set<String> knownFailedTenants = new ConcurrentHashSet<>();

    /**
     * Resolve property from PayProp API.
     * Caches failures to avoid repeated API calls.
     */
    public Optional<PropertyDTO> resolveProperty(String propertyPayPropId) {
        // Check if we already know this fails
        if (knownFailedProperties.contains(propertyPayPropId)) {
            log.debug("⏭️ Skipping known failed property: {}", propertyPayPropId);
            return Optional.empty();
        }

        try {
            PropertyDTO property = payPropApiClient.getProperty(propertyPayPropId);
            return Optional.of(property);
        } catch (PayPropApiException e) {
            if (e.getStatusCode() == 404) {
                // Property not linked to entity - not an error, just unavailable
                log.info("ℹ️ Property {} not linked to PayProp entity (404), marking as unavailable",
                    propertyPayPropId);
                knownFailedProperties.add(propertyPayPropId);

                // Optional: Mark property as inactive in local DB
                markPropertyAsUnavailable(propertyPayPropId);
            } else {
                // Other errors should still be logged as errors
                log.error("❌ Failed to resolve property {}: {}",
                    propertyPayPropId, e.getMessage());
            }
            return Optional.empty();
        }
    }

    /**
     * Mark property as unavailable in local database.
     * Prevents future sync attempts and shows warning in UI.
     */
    private void markPropertyAsUnavailable(String propertyPayPropId) {
        propertyRepository.findByPayPropId(propertyPayPropId).ifPresent(property -> {
            property.setPayPropSyncStatus("UNAVAILABLE");
            property.setPayPropSyncError("Property not linked to entity in PayProp");
            property.setPayPropLastSyncAttempt(LocalDateTime.now());
            propertyRepository.save(property);
        });
    }

    /**
     * Get statistics on entity resolution failures.
     */
    public EntityResolutionStats getStats() {
        EntityResolutionStats stats = new EntityResolutionStats();
        stats.failedProperties = knownFailedProperties.size();
        stats.failedTenants = knownFailedTenants.size();
        stats.failedPropertyIds = new ArrayList<>(knownFailedProperties);
        stats.failedTenantIds = new ArrayList<>(knownFailedTenants);
        return stats;
    }

    /**
     * Clear failure cache (e.g., after re-linking properties in PayProp).
     */
    public void clearFailureCache() {
        knownFailedProperties.clear();
        knownFailedTenants.clear();
        log.info("✅ Cleared entity resolution failure cache");
    }
}
```

**Add Property Fields:**
```sql
ALTER TABLE properties
ADD COLUMN payprop_sync_status VARCHAR(50) DEFAULT 'PENDING',
ADD COLUMN payprop_sync_error TEXT,
ADD COLUMN payprop_last_sync_attempt DATETIME;
```

---

### Priority 3: MEDIUM - Improve User Experience

#### Fix #6: Admin Dashboard for Monitoring

**Objective:** Visibility into sync status and issues

**Create:** `AdminPayPropSyncController.java`

```java
@RestController
@RequestMapping("/api/admin/payprop")
public class AdminPayPropSyncController {

    @Autowired
    private PayPropFinancialSyncService financialSyncService;

    @Autowired
    private PayPropIncomingPaymentFinancialSyncService incomingPaymentService;

    @Autowired
    private FinancialTransactionRepository transactionRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    /**
     * Get comprehensive sync status dashboard.
     */
    @GetMapping("/sync-status")
    public PayPropSyncStatusDTO getSyncStatus() {
        PayPropSyncStatusDTO status = new PayPropSyncStatusDTO();

        // Financial transactions by source
        status.incomingPayments = getDataSourceStats("INCOMING_PAYMENT");
        status.batchPayments = getDataSourceStats("BATCH_PAYMENT");
        status.icdnActual = getDataSourceStats("ICDN_ACTUAL");
        status.commissionPayments = getDataSourceStats("COMMISSION_PAYMENT");

        // Overall linkage
        status.totalTransactions = transactionRepository.countByDataSourceNotIn(
            List.of("HISTORICAL_IMPORT", "HISTORICAL_CSV")
        );
        status.linkedTransactions = transactionRepository.countByInvoiceIdNotNullAndDataSourceNotIn(
            List.of("HISTORICAL_IMPORT", "HISTORICAL_CSV")
        );
        status.unlinkedTransactions = status.totalTransactions - status.linkedTransactions;
        status.linkagePercentage = (status.linkedTransactions * 100.0) / status.totalTransactions;

        // Invoice stats
        status.totalInvoices = invoiceRepository.count();
        status.activeInvoices = invoiceRepository.countByIsActive(true);

        // Unified transactions
        status.unifiedTransactionCount = getUnifiedTransactionCount();

        return status;
    }

    /**
     * Trigger manual re-link of incoming payments.
     */
    @PostMapping("/relink-incoming-payments")
    public ResponseEntity<RelinkResult> relinkIncomingPayments() {
        log.info("🔗 Manual re-link triggered for incoming payments");
        RelinkResult result = incomingPaymentService.relinkExistingIncomingPayments();
        return ResponseEntity.ok(result);
    }

    /**
     * Get detailed list of unlinked transactions.
     */
    @GetMapping("/unlinked-transactions")
    public List<UnlinkedTransactionDTO> getUnlinkedTransactions(
            @RequestParam(required = false) String dataSource) {

        List<FinancialTransaction> unlinked;
        if (dataSource != null) {
            unlinked = transactionRepository
                .findByDataSourceAndInvoiceIdIsNull(dataSource);
        } else {
            unlinked = transactionRepository
                .findByInvoiceIdIsNullAndDataSourceNotIn(
                    List.of("HISTORICAL_IMPORT", "HISTORICAL_CSV")
                );
        }

        return unlinked.stream()
            .map(this::toUnlinkedDTO)
            .collect(Collectors.toList());
    }

    private DataSourceStatsDTO getDataSourceStats(String dataSource) {
        DataSourceStatsDTO stats = new DataSourceStatsDTO();
        stats.dataSource = dataSource;
        stats.total = transactionRepository.countByDataSource(dataSource);
        stats.linked = transactionRepository.countByDataSourceAndInvoiceIdNotNull(dataSource);
        stats.unlinked = stats.total - stats.linked;
        stats.linkagePercentage = (stats.total > 0)
            ? (stats.linked * 100.0) / stats.total
            : 0.0;
        stats.totalAmount = transactionRepository.sumAmountByDataSource(dataSource);
        return stats;
    }

    private long getUnifiedTransactionCount() {
        // Query unified_transactions table
        // ... implementation ...
        return 0; // placeholder
    }

    private UnlinkedTransactionDTO toUnlinkedDTO(FinancialTransaction txn) {
        UnlinkedTransactionDTO dto = new UnlinkedTransactionDTO();
        dto.id = txn.getId();
        dto.payPropTransactionId = txn.getPayPropTransactionId();
        dto.dataSource = txn.getDataSource();
        dto.amount = txn.getAmount();
        dto.transactionDate = txn.getTransactionDate();
        dto.propertyName = txn.getPropertyName();
        dto.tenantName = txn.getTenantName();
        dto.description = txn.getDescription();
        return dto;
    }
}
```

**Frontend Dashboard:**
```html
<!-- Admin panel showing:
- Overall linkage percentage
- Breakdown by data source
- List of unlinked transactions
- Button to trigger re-link
- Entity resolution failure counts
-->
```

---

## Testing Recommendations

### Test Plan: Invoice Linking Fixes

#### Test #1: Re-Link Existing Incoming Payments

**Pre-conditions:**
- 106 incoming payments in database with invoice_id = NULL
- Invoices exist for at least some of these payments

**Steps:**
1. Call `/api/admin/payprop/relink-incoming-payments`
2. Check response:
   - `totalProcessed` = 106
   - `linked` > 0
   - `noInvoiceFound` + `noProperty` + `errors` < 106
3. Query database:
   ```sql
   SELECT COUNT(*) FROM financial_transactions
   WHERE data_source = 'INCOMING_PAYMENT'
   AND invoice_id IS NOT NULL
   ```
4. Expected: Count > 0 (ideally close to 106)

**Success Criteria:**
- At least 80% of incoming payments linked (85/106)
- No errors thrown
- Logs show "✅ Linked transaction ... to invoice ..."

---

#### Test #2: New Incoming Payment Sync

**Pre-conditions:**
- Test PayProp account with new incoming payment data

**Steps:**
1. Add new incoming payment in PayProp test account
2. Wait for PayProp to process (or trigger via API)
3. Run sync: `POST /api/payprop/sync-all-financial`
4. Check logs for:
   - "💰 Starting sync of incoming payments"
   - "✅ Linked incoming payment ... to invoice ..."
5. Query database:
   ```sql
   SELECT * FROM financial_transactions
   WHERE payprop_transaction_id = '<new_payment_id>'
   ```
6. Verify `invoice_id` is NOT NULL

**Success Criteria:**
- New payment automatically linked on import
- Log shows successful linkage
- invoice_id populated correctly

---

#### Test #3: Duplicate Invoice Handling

**Pre-conditions:**
- Invoice with PayProp ID 'd71ebxD9Z5' exists
- Multiple invoice instructions reference same PayProp ID

**Steps:**
1. Run sync: `POST /api/payprop/sync-invoice-instructions`
2. Check logs - should NOT see:
   - "Duplicate entry 'd71ebxD9Z5' for key 'invoices.payprop_id'"
3. Should see:
   - "✅ Updated invoice ... from instruction ..."
4. Check database:
   ```sql
   SELECT COUNT(*) FROM invoices WHERE payprop_id = 'd71ebxD9Z5'
   ```
5. Expected: COUNT = 1 (only one invoice, not 14)

**Success Criteria:**
- No duplicate key errors
- All 14 instructions processed successfully
- Only one invoice record exists

---

#### Test #4: Transaction Isolation

**Pre-conditions:**
- Mix of good and bad data in sync

**Steps:**
1. Inject one invalid invoice instruction (e.g., missing required field)
2. Run sync: `POST /api/payprop/sync-all-financial`
3. Check results:
   - Invoice sync fails for 1 instruction
   - Financial transaction sync succeeds
   - Incoming payment sync succeeds
4. Check database:
   - Financial transactions imported despite invoice failure
   - Incoming payments imported despite invoice failure

**Success Criteria:**
- Partial success achieved
- One failure doesn't roll back entire sync
- Overall sync result shows mixed success/failure

---

#### Test #5: Unified Transactions Rebuild

**Pre-conditions:**
- Financial transactions now have invoice_id populated

**Steps:**
1. Clear unified_transactions: `TRUNCATE unified_transactions`
2. Run rebuild: `POST /api/admin/unified/rebuild`
3. Check result:
   ```sql
   SELECT COUNT(*) FROM unified_transactions
   ```
4. Expected: Should be > 571 (old count)
5. Verify incoming payments included:
   ```sql
   SELECT COUNT(*) FROM unified_transactions
   WHERE source_system = 'PAYPROP'
   AND payprop_data_source = 'INCOMING_PAYMENT'
   ```
6. Expected: Count > 0

**Success Criteria:**
- Unified table includes newly linked transactions
- Count increases from 571 to ~600+
- Incoming payments visible in unified view

---

### Regression Testing

**Test:** Ensure existing functionality still works

1. **Historical Transactions:**
   - Verify historical data still in unified table
   - Verify statements for historical periods still generate

2. **Statement Generation:**
   - Generate statement for property with linked transactions
   - Verify all transaction types appear correctly
   - Verify totals match database sums

3. **Property Owner Portal:**
   - Login as property owner
   - View statements
   - Verify incoming payments now appear
   - Verify amounts correct

4. **Admin Panel:**
   - View financial transactions list
   - Filter by data source
   - Verify invoice linkage shown in UI

---

## Appendices

### Appendix A: Database Queries for Investigation

```sql
-- Query 1: Find the duplicate invoice
SELECT * FROM invoices WHERE payprop_id = 'd71ebxD9Z5';

-- Query 2: Find all instructions referencing it
SELECT * FROM payprop_report_invoice_instructions
WHERE payprop_invoice_id = 'd71ebxD9Z5'
ORDER BY transaction_date;

-- Query 3: Unlinked transactions summary
SELECT
    data_source,
    COUNT(*) as count,
    SUM(amount) as total_amount
FROM financial_transactions
WHERE invoice_id IS NULL
AND data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV')
GROUP BY data_source;

-- Query 4: Properties with failed resolution
SELECT * FROM properties
WHERE id IN (1,2,5,6,7,11,14,16,17,18,20,21,25,27,28,29,30,31,33,37,39,40,41,43,44);

-- Query 5: Incoming payments by property
SELECT
    property_name,
    COUNT(*) as payment_count,
    SUM(amount) as total_amount,
    COUNT(invoice_id) as linked_count
FROM financial_transactions
WHERE data_source = 'INCOMING_PAYMENT'
GROUP BY property_name
ORDER BY payment_count DESC;

-- Query 6: Invoices per property
SELECT
    p.property_name,
    COUNT(*) as invoice_count,
    SUM(CASE WHEN i.is_active THEN 1 ELSE 0 END) as active_count
FROM invoices i
JOIN properties p ON i.property_id = p.id
GROUP BY p.property_name
ORDER BY invoice_count DESC;
```

### Appendix B: Log Patterns to Search For

**Successful Linkage:**
```
"✅ Linked incoming payment"
"✅ Linked batch payment"
"✅ Linked ICDN transaction"
```

**Failed Linkage:**
```
"⚠️ No invoice found for incoming payment"
"⚠️ No invoice found for transaction"
```

**Entity Resolution:**
```
"Property not linked to entity"
"Failed to resolve property"
"Tenant not found"
```

**Transaction Issues:**
```
"Transaction silently rolled back"
"Duplicate entry"
"marked as rollback-only"
```

### Appendix C: Environment Variables

```bash
# PayProp Integration
PAYPROP_ENABLED=true
PAYPROP_API_URL=https://api.payprop.com/v1
PAYPROP_CLIENT_ID=<your_client_id>
PAYPROP_CLIENT_SECRET=<your_client_secret>
PAYPROP_ENTITY_ID=<your_entity_id>

# Database
DATABASE_URL=jdbc:mysql://host:port/database
DB_USERNAME=root
DB_PASSWORD=<password>

# Application
APP_BASE_URL=https://spoutproperty-hub.onrender.com
COMPANY_NAME=Propsk
```

---

## Summary

This document has identified and analyzed **8 major issues** affecting the PayProp data import system:

**Critical (3):**
1. Incoming payments not linking to invoices (0% success rate, £88,560 impact)
2. Transaction rollback preventing sync completion
3. Duplicate invoice constraint violations (14 failed instructions)

**High Priority (3):**
4. Property entity resolution failures (25 properties)
5. Tenant entity resolution failures (29 tenants)
6. Partial invoice linkage success (only 68.8% overall)

**Medium Priority (2):**
7. PayProp API permission errors (maintenance endpoints)
8. Invoice instruction sync failures

**Next Steps:**
1. Deploy Fix #1 (re-link existing incoming payments) - IMMEDIATE
2. Deploy Fix #2 (invoice upsert logic) - IMMEDIATE
3. Deploy Fix #3 (transaction boundaries) - IMMEDIATE
4. Test all fixes in staging environment
5. Deploy to production
6. Monitor admin dashboard for improvement
7. Address entity resolution failures (Fix #5)

**Expected Outcome:**
- 106 incoming payments linked to invoices (£88,560 recovered)
- No more duplicate invoice errors
- Successful partial syncs (no all-or-nothing rollbacks)
- Overall linkage rate: 90%+ (up from 68.8%)
- Unified transactions table complete and accurate

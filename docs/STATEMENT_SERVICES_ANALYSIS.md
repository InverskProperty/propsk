# Statement Services Analysis

## CRITICAL: Underlying Data Architecture Issues

Before implementing statement enhancements, we must address fundamental data flow problems that affect data accuracy and consistency.

### The Problem: Dual PayProp Import Paths

PayProp data currently flows through **two competing paths**, creating duplication and confusion:

```
PayProp API
    │
    ├──► PayPropFinancialSyncService ──────────────► financial_transactions ✓
    │    PayPropIncomingPaymentFinancialSyncService
    │
    └──► PayPropIncomingPaymentImportService ──────► historical_transactions ✗
         PayPropPaymentImportService
```

### Table Purpose Matrix

| Table | Intended Purpose | Actual Usage | Problem |
|-------|------------------|--------------|---------|
| `historical_transactions` | CSV/manual imports only | Also receives PayProp data | Dual purpose = confusion |
| `financial_transactions` | PayProp API data | Correct usage | ✓ OK |
| `unified_transactions` | Materialized view from both | Rebuilds correctly | Potential duplicates if both sources have same data |
| `unified_incoming_transactions` | Single source for incoming payments | Underutilized | Allocation pages bypass it |
| `payprop_incoming_payments` | Extracted tenant payments | Raw storage | ✓ OK |
| `payprop_report_all_payments` | Raw PayProp allocations | Raw storage | ✓ OK |

### Incoming Payment Extraction (Working Correctly)

One tenant payment (e.g., £700) appears in MULTIPLE allocation records in PayProp's API:
- Commission allocation record
- Owner allocation record
- Property account allocation record

**Solution implemented:** `PayPropIncomingPaymentExtractorService`

```
PayProp API: /all-payments
         │
         ▼
┌─────────────────────────────────────────────────────┐
│     payprop_report_all_payments (raw table)         │
│  Each row has embedded incoming payment fields:     │
│  - incoming_transaction_id                          │
│  - incoming_transaction_amount                      │
│  - incoming_tenant_payprop_id, etc.                 │
└─────────────────────────┬───────────────────────────┘
                          │
                          ▼ PayPropIncomingPaymentExtractorService
                          │ (SELECT DISTINCT on incoming_transaction_id)
                          │
┌─────────────────────────▼───────────────────────────┐
│       payprop_incoming_payments (deduplicated)      │
│  One row per actual tenant payment                  │
└─────────────────────────────────────────────────────┘
```

This extraction is **correct**. The problem is downstream.

### Allocation Pages Bypass Unified Layer

| UI Page | Controller | Current Data Source | Should Be |
|---------|------------|---------------------|-----------|
| `/owner-payments` | `TransactionAllocationController` | `historical_transactions` | `unified_incoming_transactions` |
| Owner payment creation | `TransactionBatchAllocationService` | `historical_transactions` | `unified_incoming_transactions` |
| `/property-owner/financials` | `PropertyOwnerController` | `historical_transactions` | Unified layer |

**Evidence:**
- `TransactionBatchAllocationService.java:252` - `transactionRepository.findByPropertyIdWithNetToOwner(propertyId)`
- `TransactionBatchAllocationService.java:280` - `transactionRepository.findByOwnerIdWithNetToOwner(ownerId)`
- `OwnerPaymentController.java:388-400` - Raw SQL: `FROM historical_transactions ht`

### Redundant Services to Remove

| Service | What It Does | Why Redundant |
|---------|--------------|---------------|
| `PayPropIncomingPaymentImportService` | Copies `payprop_incoming_payments` → `historical_transactions` | `PayPropIncomingPaymentFinancialSyncService` already writes to `financial_transactions` |
| `PayPropPaymentImportService` | Copies `payprop_report_all_payments` → `historical_transactions` | `PayPropFinancialSyncService` already writes to `financial_transactions` |

### Target Architecture

```
┌───────────────────────────────────────────────────────────────────┐
│                       DATA SOURCES                                 │
├─────────────────────┬─────────────────────────────────────────────┤
│   CSV Imports       │          PayProp API                        │
└─────────┬───────────┴──────────────┬──────────────────────────────┘
          │                          │
          ▼                          ▼
┌─────────────────────┐    ┌─────────────────────────────────────┐
│historical_transactions│   │ payprop_incoming_payments          │
│ (ONLY CSV/manual)    │   │ payprop_report_all_payments         │
└─────────┬───────────┘    │ (RAW PayProp - keep as audit trail) │
          │                 └──────────────┬──────────────────────┘
          │                                │
          │                                ▼
          │                 ┌─────────────────────────────────────┐
          │                 │     financial_transactions          │
          │                 │     (PayProp processed data)        │
          │                 └──────────────┬──────────────────────┘
          │                                │
          └────────────────┬───────────────┘
                           │ REBUILD
                           ▼
          ┌─────────────────────────────────────────────┐
          │     unified_transactions                     │
          │   - Single source of truth for reporting    │
          └─────────────────────────────────────────────┘
                           │
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
    ┌──────────┐   ┌────────────┐   ┌──────────────┐
    │Allocation│   │ Statements │   │   Reports    │
    │  Pages   │   │   (Excel)  │   │              │
    └──────────┘   └────────────┘   └──────────────┘
```

### Required Fixes Before Statement Enhancements

1. **Phase 0A**: Remove redundant import services
   - Disable `PayPropIncomingPaymentImportService`
   - Disable `PayPropPaymentImportService`

2. **Phase 0B**: Fix allocation pages to use unified layer
   - Modify `TransactionBatchAllocationService` to query `unified_transactions`
   - Modify `OwnerPaymentController` to use unified layer

3. **Phase 0C**: Consolidate allocation systems
   - Merge `UnifiedAllocation` + `TransactionBatchAllocation` into one system

---

## Current State: Two Competing Statement Services

### 1. ExcelStatementGeneratorService (Option C) - CURRENTLY IN USE
**Location:** `service.statement.ExcelStatementGeneratorService`
**Endpoint:** `/api/statements/option-c/owner/{id}/excel`
**Called from:** `statements.html` via JavaScript

#### Sheets Generated
| Sheet | Purpose | Columns |
|-------|---------|---------|
| LEASE_MASTER | All leases with property/customer info | 11 |
| TRANSACTIONS | Raw incoming transaction data | 8 |
| RENT_DUE | What's due per lease per period | Variable |
| RENT_RECEIVED | What's been received per lease | Variable |
| EXPENSES | Expenses by property | Variable |
| MONTHLY_STATEMENT | Summary per lease | 16 |
| Income Allocations | Links transactions to payment batches | 10 |
| Expense Allocations | Expense transactions in batches | 10 |
| Owner Payments Summary | Payment batch summary | 8 |

#### MONTHLY_STATEMENT Columns (16)
```
lease_reference, property_name, customer_name, lease_start_date, rent_due_day, month,
rent_due, rent_received, arrears, management_fee, service_fee,
total_commission, total_expenses, net_to_owner, opening_balance, cumulative_arrears
```

#### Tenant Balance - ALREADY EXISTS
The current columns already provide tenant balance tracking:
| Column | Meaning |
|--------|---------|
| `opening_balance` | What tenant owed at START of period |
| `rent_due` | What was charged THIS period |
| `rent_received` | What was paid THIS period |
| `arrears` | rent_due - rent_received (THIS period only) |
| `cumulative_arrears` | opening_balance + arrears = **closing balance / tenant balance** |

**No new columns needed for tenant balance.**

---

### 2. BodenHouseStatementTemplateService - NOT USED FROM STATEMENTS PAGE
**Location:** `service.statements.BodenHouseStatementTemplateService`
**Endpoint:** `/property-owner/generate-statement-xlsx` (JS bypasses this!)
**Actually used by:** Google Sheets services only

- 41 columns (excessive)
- Property grouping with subtotals
- Legacy payment source columns (Robert Ellis, Old Account, PayProp)
- Block property support with Property Account Balance

---

## Approved Enhancement Plan

### Enhancement 1: Property Account Balance (Block Properties)

Mirror the tenant balance structure for property accounts:

#### New Columns for MONTHLY_STATEMENT (block properties only)
| Column | Meaning |
|--------|---------|
| `property_account_opening` | Balance in property account at START of period |
| `property_account_in` | Money allocated TO property account this period |
| `property_account_out` | Money spent FROM property account this period |
| `property_account_closing` | What's left in property account at END |

#### New Sheet: PROPERTY_ACCOUNT_SUMMARY
| Column | Purpose |
|--------|---------|
| block_name | Name of block property |
| opening_balance | Balance at start of statement period |
| allocations_in | Total allocated to account this period |
| expenses_out | Total expenses paid from account this period |
| net_movement | allocations_in - expenses_out |
| closing_balance | Balance at end of statement period |

---

### Enhancement 2: Full Payment Traceability (PayProp + Historical)

Enable complete audit trail from tenant payment to owner bank account.

#### Enhanced TRANSACTIONS Sheet Columns
| Column | Purpose |
|--------|---------|
| transaction_id | Our internal ID |
| payprop_transaction_id | PayProp's ID (if from PayProp, NULL if manual/historical) |
| date | Transaction date |
| property | Property name |
| tenant | Tenant name |
| amount | Transaction amount |
| category | Rent/Deposit/etc |
| allocated_to_batch | Batch reference this was allocated to |

#### Enhanced Income Allocations Sheet
| Column | Purpose |
|--------|---------|
| transaction_id | Our internal ID (links to TRANSACTIONS) |
| payprop_transaction_id | PayProp's transaction ID |
| date | Transaction date |
| property | Property name |
| tenant | Tenant name |
| amount | Allocated amount |
| batch_id | Our internal batch reference |
| payprop_batch_id | PayProp's batch ID (if applicable) |
| payment_date | When owner was paid |
| payment_status | DRAFT/PENDING/PAID |
| payment_reference | Bank reference/confirmation |

#### Enhanced Owner Payments Summary Sheet
| Column | Purpose |
|--------|---------|
| batch_id | Our internal batch reference |
| payprop_batch_id | PayProp's batch ID (if originated from PayProp) |
| payment_date | Date payment was made |
| total_allocations | Sum of all allocations in batch |
| total_income | Positive allocations (rent received) |
| total_expenses | Negative allocations (expenses deducted) |
| balance_adjustment | Any manual adjustments |
| total_payment | Final amount paid to owner |
| payment_status | DRAFT/PENDING/PAID |
| payment_method | Bank transfer/etc |
| payment_reference | Bank reference/confirmation |

---

### Enhancement 3: Property/Block Grouping

Transform MONTHLY_STATEMENT from flat list to hierarchical:

```
OWNER: John Smith
  BLOCK: Boden House
    ├── Flat 1 - Tenant A - £700 due - £700 received - Paid
    ├── Flat 2 - Tenant B - £650 due - £650 received - Pending
    └── BLOCK SUBTOTAL: £1,350 | Property Account: £450

  PROPERTY: 123 High Street
    ├── Lease - Tenant C - £900 due - £900 received - Paid
    └── PROPERTY SUBTOTAL: £900

OWNER GRAND TOTAL: £2,250
```

---

## Audit Trail Chain

The enhanced system enables full traceability:

```
┌─────────────────────────────────────────────────────────────────┐
│ TRANSACTIONS Sheet                                               │
│ transaction_id: 12345 | payprop_id: PP-INC-98765 | £700         │
│ → allocated_to_batch: BATCH-001                                  │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Income Allocations Sheet                                         │
│ transaction_id: 12345 | batch_id: BATCH-001                     │
│ payprop_batch_id: PP-PAY-55555 | status: PAID | date: 2024-12-01│
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Owner Payments Summary Sheet                                     │
│ batch_id: BATCH-001 | payprop_batch_id: PP-PAY-55555            │
│ total: £1,200 | status: PAID | ref: BACS-123456                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Files to Modify

### Primary
- `ExcelStatementGeneratorService.java`
  - Add property account balance columns
  - Add PayProp ID columns to all relevant sheets
  - Add property/block grouping logic
  - Create PROPERTY_ACCOUNT_SUMMARY sheet

### Secondary
- `StatementDataExtractService.java`
  - Enhance data extraction to include PayProp IDs
  - Add property account balance queries

- `TransactionBatchAllocationRepository.java`
  - Add queries for PayProp batch IDs
  - Add property account balance queries

### Data Sources
- `historical_transactions` - has payprop_transaction_id
- `payment_batches` - has payprop_batch_id
- `transaction_batch_allocations` - links transactions to batches
- `financial_transactions` - property account allocations

---

## Implementation Order

1. **Phase 1**: Add PayProp ID columns to existing sheets (minimal risk)
2. **Phase 2**: Add property account balance columns + summary sheet
3. **Phase 3**: Add property/block grouping to MONTHLY_STATEMENT
4. **Phase 4**: Test and validate audit trail completeness

---

## Appendix A: Complete File Dependency Map

### `historical_transactions` Table Usage

| File | Type | Usage | Impact |
|------|------|-------|--------|
| `TransactionAllocationController.java` | Controller | Lines 17-19, 49 - imports entity, uses repository | **HIGH** - Powers allocation UI |
| `OwnerPaymentController.java` | Controller | Raw JDBC queries (lines 388-400) | **HIGH** - Bypasses all layers |
| `PropertyController.java` | Controller | Direct queries | Medium |
| `PropertyOwnerController.java` | Controller | Direct queries | Medium |
| `TransactionBatchAllocationService.java` | Service | Lines 252, 278-285 - core allocation queries | **HIGH** - Core allocation logic |
| `ExcelStatementGeneratorService.java` | Service | Statement generation queries | **HIGH** |
| `PayPropIncomingPaymentImportService.java` | Service | **WRITES** PayProp data here | **REDUNDANT** |
| `PayPropPaymentImportService.java` | Service | **WRITES** PayProp data here | **REDUNDANT** |
| `HistoricalTransactionImportService.java` | Service | CSV imports (correct) | OK |
| `UnifiedTransactionRebuildService.java` | Service | Rebuilds unified FROM this | OK |
| `StatementDataExtractService.java` | Service | Data extraction for statements | Medium |
| `LeaseStatementService.java` | Service | Statement generation | Medium |
| `UnifiedFinancialDataService.java` | Service | Financial data aggregation | Medium |

### `financial_transactions` Table Usage

| File | Type | Usage | Impact |
|------|------|-------|--------|
| `PayPropFinancialSyncService.java` | Service | **WRITES** all PayProp data | **PRIMARY** |
| `PayPropIncomingPaymentFinancialSyncService.java` | Service | **WRITES** incoming payments | **PRIMARY** |
| `UnifiedTransactionRebuildService.java` | Service | Rebuilds unified FROM this | OK |
| `LeaseStatementService.java` | Service | Statement queries | Medium |
| `BodenHouseStatementTemplateService.java` | Service | Statement queries | Medium |
| `XLSXStatementService.java` | Service | Statement queries | Medium |
| `FinancialController.java` | Controller | Direct queries | Medium |
| `LeaseTransactionReportController.java` | Controller | Report queries | Medium |

### `unified_transactions` Table Usage

| File | Type | Usage | Impact |
|------|------|-------|--------|
| `UnifiedTransactionRebuildService.java` | Service | REBUILDS this table | Core |
| `UnifiedFinancialDataService.java` | Service | Queries for unified view | OK |
| `PropertyFinancialSummaryService.java` | Service | Summary queries | OK |
| `XLSXStatementService.java` | Service | Statement generation | OK |
| `UnifiedTransactionController.java` | Controller | API for unified data | OK |

### `unified_incoming_transactions` Table Usage (**UNDERUTILIZED**)

| File | Type | Usage | Impact |
|------|------|-------|--------|
| `HistoricalTransactionImportService.java` | Service | Creates records | Limited |
| `PayPropPaymentImportService.java` | Service | Creates records (but also writes to historical!) | Confusing |
| `BodenHouseStatementTemplateService.java` | Service | Reads for statements | OK |

### `unified_allocations` Table Usage

| File | Type | Usage | Impact |
|------|------|-------|--------|
| `UnifiedAllocationService.java` | Service | CRUD operations | OK |
| `PayPropPaymentImportService.java` | Service | Creates records | OK |
| `ExcelStatementGeneratorService.java` | Service | Reads for statements | OK |
| **NOT USED BY:** `TransactionBatchAllocationService.java` | Service | Uses `TransactionBatchAllocation` instead! | **PARALLEL SYSTEM** |

### PayProp Raw Tables Usage

| File | Type | Tables Used |
|------|------|-------------|
| `PayPropRawAllPaymentsImportService.java` | Service | `payprop_report_all_payments` (write) |
| `PayPropRawPropertiesImportService.java` | Service | `payprop_raw_properties` (write) |
| `PayPropIncomingPaymentExtractorService.java` | Service | `payprop_report_all_payments` → `payprop_incoming_payments` |
| `PayPropIncomingPaymentImportService.java` | Service | `payprop_incoming_payments` (read) |
| `PayPropPaymentImportService.java` | Service | `payprop_report_all_payments` (read) |
| `PayPropTransactionService.java` | Service | Both raw tables (read) |

---

## Appendix B: UI Template → Data Source Map

| Template | Controller | API Endpoints | Ultimate Data Source |
|----------|------------|---------------|---------------------|
| `owner-payments/index.html` | `TransactionAllocationController` | `/api/transaction-allocations/*` | `historical_transactions` via `TransactionBatchAllocationService` |
| `owner-payments/transactions.html` | `OwnerPaymentController` | `/owner-payments/api/*` | `historical_transactions` via raw JDBC |
| `property-owner/statements.html` | `PropertyOwnerController` | `/api/statements/*` | Mixed (unified for statements) |
| `property-owner/financials.html` | `PropertyOwnerController` | Various | `historical_transactions` |

---

## Appendix C: Parallel Allocation Systems

The codebase has **two competing allocation systems**:

### System 1: UnifiedAllocation
- Entity: `UnifiedAllocation.java`
- Repository: `UnifiedAllocationRepository.java`
- Service: `UnifiedAllocationService.java`
- Links to: `unified_incoming_transactions`

### System 2: TransactionBatchAllocation
- Entity: `TransactionBatchAllocation.java`
- Repository: `TransactionBatchAllocationRepository.java`
- Service: `TransactionBatchAllocationService.java`
- Links to: `historical_transactions`

**These should be consolidated into ONE system.**

---

## Appendix D: PayProp Data Flow (data_source values)

The `financial_transactions.data_source` column tracks origin:

| data_source | Service | Description |
|-------------|---------|-------------|
| `INCOMING_PAYMENT` | `PayPropIncomingPaymentFinancialSyncService` | Tenant rent payments |
| `ICDN_ACTUAL` | `PayPropFinancialSyncService` | Actual reconciled payments |
| `BATCH_PAYMENT` | `PayPropFinancialSyncService` | Batch payments to owners |
| `COMMISSION_PAYMENT` | `PayPropFinancialSyncService` | Commission taken |
| `EXPENSE_PAYMENT` | `PayPropFinancialSyncService` | Expenses paid |
| `PAYMENT_INSTRUCTION` | `PayPropFinancialSyncService` | Pending instructions |
| `ACTUAL_PAYMENT` | `PayPropFinancialSyncService` | Actual payments |
| `LIVE_PAYPROP_OWNER_PAYMENTS` | `PayPropFinancialSyncService` | Live owner payments |
| `HISTORICAL_IMPORT` | `PayPropHistoricalDataImportService` | Old data import |

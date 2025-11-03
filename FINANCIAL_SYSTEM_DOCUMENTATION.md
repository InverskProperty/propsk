# Financial System Documentation

## Overview

This document provides a comprehensive overview of the CreCRM financial system, including the unified transactions architecture, data sources, lease management, and statement generation.

---

## Architecture Overview

### Unified Transactions Layer

The system uses a **unified transactions layer** (`unified_transactions` table) that consolidates financial data from multiple sources into a single, consistent view.

```
┌─────────────────────┐     ┌──────────────────────┐
│  Historical CSV     │     │   PayProp API        │
│  Imports            │     │   (Live Sync)        │
└──────────┬──────────┘     └──────────┬───────────┘
           │                           │
           v                           v
┌──────────────────────┐     ┌──────────────────────┐
│ historical_          │     │ financial_           │
│ transactions         │     │ transactions         │
└──────────┬───────────┘     └──────────┬───────────┘
           │                           │
           └────────────┬──────────────┘
                        v
              ┌──────────────────────┐
              │ unified_             │
              │ transactions         │
              │ (Materialized View)  │
              └──────────┬───────────┘
                         │
                         v
              ┌──────────────────────┐
              │ Statement            │
              │ Generation           │
              │ (Excel/PDF)          │
              └──────────────────────┘
```

---

## Data Sources

### 1. Historical Transactions (`historical_transactions`)

**Purpose**: Stores manually imported transaction data from CSV files for periods before PayProp integration.

**Source**: CSV imports uploaded by administrators

**Key Columns**:
- `id` - Primary key
- `transaction_date` - When the transaction occurred
- `amount` - Transaction amount (always positive)
- `description` - Transaction description
- `category` - Transaction category (e.g., "rent", "cleaning", "maintenance")
- `source` - Import source identifier
- `invoice_id` - Link to property lease

**Category Mapping**:
```
Category        → Flow Direction
─────────────────────────────────
rent            → INCOMING
Rent            → INCOMING
cleaning        → OUTGOING
furnishings     → OUTGOING
maintenance     → OUTGOING
utilities       → OUTGOING
compliance      → OUTGOING
management      → OUTGOING
agency_fee      → OUTGOING
owner_payment   → OUTGOING
(default)       → OUTGOING
```

**Import Process**: Located in `HistoricalTransactionImportService.java`

---

### 2. PayProp Transactions (`financial_transactions`)

**Purpose**: Stores real-time transaction data synced from PayProp API.

**Source**: PayProp API automatic sync

**Key Columns**:
- `id` - Primary key
- `transaction_date` - When the transaction occurred
- `amount` - Transaction amount
- `description` - Transaction description
- `category_name` - PayProp's category classification
- `data_source` - PayProp transaction type identifier
- `transaction_type` - Specific transaction type from PayProp
- `pay_prop_transaction_id` - PayProp's unique transaction ID
- `invoice_id` - Link to property lease

**Data Source Types**:
```
Data Source         Transaction Type      Flow Direction    Import to Unified?
────────────────────────────────────────────────────────────────────────────────
INCOMING_PAYMENT    incoming_payment      INCOMING          ✅ YES (actual payments)
BATCH_PAYMENT       payment_to_agency     OUTGOING          ✅ YES
BATCH_PAYMENT       payment_to_beneficiary OUTGOING         ✅ YES
COMMISSION_PAYMENT  commission_payment    OUTGOING          ✅ YES
ICDN_ACTUAL         invoice               N/A               ❌ NO (rent invoices, not payments)
HISTORICAL_IMPORT   N/A                   N/A               ❌ NO (duplicates historical data)
HISTORICAL_CSV      N/A                   N/A               ❌ NO (duplicates historical data)
```

**Critical Rule**: ICDN_ACTUAL transactions are **rent invoices** (what tenant owes), not actual payments. They must be excluded to prevent double-counting with INCOMING_PAYMENT records (what tenant actually paid).

**Sync Process**: Automatic via PayProp integration service

---

## Unified Transactions Layer

### Purpose

The `unified_transactions` table serves as a **single source of truth** for all financial data, regardless of origin.

### Structure

**Core Fields**:
```sql
unified_transactions
├── id                      -- Primary key
├── transaction_date        -- When transaction occurred
├── amount                  -- Transaction amount (always positive)
├── description             -- Transaction description
├── flow_direction          -- INCOMING or OUTGOING
├── transaction_type        -- Type classification
├── category                -- Normalized category
├── source_system           -- HISTORICAL or PAYPROP
├── source_table            -- Original table name
├── source_record_id        -- Original record ID
├── payprop_data_source     -- PayProp data_source (if applicable)
├── payprop_transaction_id  -- PayProp TX ID (if applicable)
├── invoice_id              -- Link to lease/property
├── property_id             -- Link to property
├── customer_id             -- Link to customer/owner
└── rebuilt_at              -- Last rebuild timestamp
```

### Flow Direction Logic

The system determines `flow_direction` based on transaction source and category:

**For Historical Transactions**:
```java
CASE
    WHEN category LIKE '%rent%' OR category LIKE '%Rent%'
        THEN 'INCOMING'
    WHEN category IN ('cleaning', 'furnishings', 'maintenance',
                      'utilities', 'compliance', 'management',
                      'agency_fee', 'owner_payment')
        THEN 'OUTGOING'
    ELSE 'OUTGOING'
END
```

**For PayProp Transactions**:
```java
CASE
    -- First check PayProp-specific data_source
    WHEN data_source = 'INCOMING_PAYMENT'
        THEN 'INCOMING'
    WHEN data_source IN ('BATCH_PAYMENT', 'COMMISSION_PAYMENT')
        THEN 'OUTGOING'

    -- Then check category_name (fallback for edge cases)
    WHEN category_name LIKE '%rent%' OR category_name LIKE '%Rent%'
        THEN 'INCOMING'
    WHEN category_name IN ('cleaning', 'furnishings', 'maintenance',
                           'utilities', 'compliance', 'management',
                           'agency_fee')
        THEN 'OUTGOING'

    ELSE 'OUTGOING'
END
```

**Location**: `UnifiedTransactionRebuildService.java` lines 150-211, 202-211

### Rebuild Process

The unified transactions table is rebuilt on-demand via the `UnifiedTransactionRebuildService`.

**Trigger**:
- Manual API call: `POST /api/unified-transactions/rebuild`
- Automatic after PayProp sync (optional)

**Process**:
1. Truncate `unified_transactions` table
2. Insert from `historical_transactions` with transformations
3. Insert from `financial_transactions` with transformations
4. Apply flow_direction logic
5. Link to invoices/properties/customers
6. Verify and return statistics

**Location**: `UnifiedTransactionRebuildService.java`

---

## Lease & Invoice Management

### Invoice Table Structure

The `invoices` table serves as the **lease management** system.

**Key Fields**:
```sql
invoices
├── id                    -- Primary key
├── lease_reference       -- Unique lease identifier (e.g., LEASE-BH-F5-2025)
├── lease_start_date      -- When lease begins
├── lease_end_date        -- When lease ends
├── property_id           -- Link to property
├── customer_id           -- Link to property owner
└── payprop_id            -- PayProp property ID
```

**Naming Convention**:
```
LEASE-{BUILDING}-{UNIT}-{YEAR}

Examples:
LEASE-BH-F5-2025    → Boden House, Flat 5, 2025
LEASE-BH-F22-2025   → Boden House, Flat 22, 2025
LEASE-KH-F-2024     → Kingston House, Flat, 2024
```

### Property Hierarchy

```
Customer (Property Owner)
    └── Property (Building + Unit)
            └── Invoice (Lease Period)
                    └── Unified Transactions
```

---

## Statement Generation

### Types of Statements

1. **Property Owner Statements** (Excel)
   - Monthly rent due vs. received
   - Expenses breakdown
   - Net payment calculations
   - Period-based reporting

2. **Portfolio Statements** (Future)
   - Multi-property rollups
   - Owner-level aggregations
   - Block-level summaries

### Statement Calculation Logic

**File**: `StatementServiceOptionC.java`

#### Rent Due Calculation

```java
// For a specific period (e.g., June 2025)
Rent Due = Daily Rent × Number of Days in Period within Lease

Where:
Daily Rent = Monthly Rent ÷ Days in Month (actual calendar days)

Example (June 2025, Flat 5, £740/month, lease starts June 17):
Daily Rent = £740 ÷ 30 = £24.67
Days in Period = 14 days (June 17-30)
Rent Due = £24.67 × 14 = £345.33
```

**Pro-rating**: Uses **actual calendar days**, not fixed 30-day months.

#### Rent Received Calculation

```sql
SELECT SUM(amount)
FROM unified_transactions
WHERE invoice_id = ?
  AND flow_direction = 'INCOMING'
  AND transaction_date >= period_start
  AND transaction_date <= period_end
  AND transaction_type = 'rent_received'
```

#### Expenses Calculation

```sql
SELECT category, SUM(amount)
FROM unified_transactions
WHERE invoice_id = ?
  AND flow_direction = 'OUTGOING'
  AND transaction_date >= period_start
  AND transaction_date <= period_end
GROUP BY category
```

#### Net Payment to Owner

```
Net Payment = Rent Received - Total Expenses - Management Fee
```

### Excel Statement Structure

**Sheets**:
1. **Summary** - Overview with key metrics
2. **Rent Due** - Month-by-month rent due calculations with formulas
3. **Rent Received** - Actual payments received with dates
4. **Expenses** - Categorized expenses breakdown
5. **Calculations** - Supporting calculations and arrears tracking

**Formula Examples**:
```excel
// Rent Due (pro-rated)
=IF(LeaseStartDay <= 1, MonthlyRent,
   MonthlyRent * (DaysInMonth - LeaseStartDay + 1) / DaysInMonth)

// Arrears Calculation
=PreviousArrears + RentDue - RentReceived

// Net Payment
=RentReceived - Expenses - ManagementFee
```

---

## Period Calculations

### Standard Monthly Periods

```
Period: Month 1st - Last Day
Example: June 2025 = June 1 - June 30
```

### Custom Period Support

The system supports custom billing periods:

**Common Patterns**:
- 1st of month to last day
- 22nd to 21st (following month)
- 25th to 24th (following month)
- 28th to 27th (following month)

**Example** (22nd to 21st):
```
June Period = May 22 - June 21
July Period = June 22 - July 21
```

**Pro-rating Logic**:
```java
if (leaseStart > periodStart) {
    // Lease started mid-period
    daysInPeriod = daysBetween(leaseStart, periodEnd)
    rentDue = (monthlyRent / daysInMonth) * daysInPeriod
}

if (leaseEnd < periodEnd) {
    // Lease ended mid-period
    daysInPeriod = daysBetween(periodStart, leaseEnd)
    rentDue = (monthlyRent / daysInMonth) * daysInPeriod
}
```

---

## Key Business Rules

### 1. Transaction Categorization

- All amounts stored as **positive numbers**
- Direction determined by `flow_direction` field
- Categories must match predefined lists for proper classification

### 2. Duplicate Prevention

- Historical and PayProp data must not overlap
- ICDN_ACTUAL invoices excluded from import (excluded via line 218 in UnifiedTransactionRebuildService.java)
- One actual payment per rent period

### 3. Data Source Priority

When conflicts occur:
1. **PayProp data** takes precedence for current/recent periods
2. **Historical data** used for pre-integration periods
3. **No overlap** - clear cutoff date between sources

### 4. Lease Period Integrity

- Transactions only counted within active lease dates
- Pro-rating applied for partial months
- Arrears carry forward between periods

### 5. Statement Generation

- All formulas preserved in Excel output
- Transparent calculations for auditing
- Period-based filtering at statement generation time

---

## Common Operations

### Rebuild Unified Transactions

**When to Rebuild**:
- After importing historical CSV data
- After PayProp sync completes
- When fixing data issues
- After schema changes

**How**:
```bash
POST /api/unified-transactions/rebuild
```

**What Happens**:
1. Clears existing unified_transactions
2. Re-imports all historical data
3. Re-imports all PayProp data (excluding ICDN_ACTUAL)
4. Applies flow_direction logic
5. Links to leases/properties
6. Returns verification statistics

### Generate Statement

**Endpoint**:
```bash
GET /api/statements/option-c/owner/{ownerId}/excel
  ?startMonth=2025-01
  &endMonth=2025-07
  &periodStartDay=1
```

**Parameters**:
- `ownerId` - Customer/owner ID
- `startMonth` - First month to include
- `endMonth` - Last month to include
- `periodStartDay` - Day of month periods start (1, 22, 25, 28)

**Output**: Excel file with multiple sheets and formulas

---

## Database Schema Reference

### Core Tables

#### invoices
```sql
CREATE TABLE invoices (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lease_reference VARCHAR(255) UNIQUE,
    lease_start_date DATE,
    lease_end_date DATE,
    property_id BIGINT,
    customer_id BIGINT,
    payprop_id VARCHAR(255)
);
```

#### unified_transactions
```sql
CREATE TABLE unified_transactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transaction_date DATE NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    description TEXT,
    flow_direction ENUM('INCOMING', 'OUTGOING'),
    transaction_type VARCHAR(50),
    category VARCHAR(100),
    source_system ENUM('HISTORICAL', 'PAYPROP'),
    source_table VARCHAR(50),
    source_record_id BIGINT,
    payprop_data_source VARCHAR(50),
    payprop_transaction_id VARCHAR(100),
    invoice_id BIGINT,
    property_id BIGINT,
    customer_id BIGINT,
    rebuilt_at TIMESTAMP,

    INDEX idx_invoice_date (invoice_id, transaction_date),
    INDEX idx_flow_direction (flow_direction),
    INDEX idx_transaction_date (transaction_date)
);
```

#### historical_transactions
```sql
CREATE TABLE historical_transactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transaction_date DATE NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    source VARCHAR(255),
    invoice_id BIGINT,

    INDEX idx_invoice_id (invoice_id)
);
```

#### financial_transactions
```sql
CREATE TABLE financial_transactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transaction_date DATE NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    description TEXT,
    category_name VARCHAR(100),
    data_source VARCHAR(50),
    transaction_type VARCHAR(50),
    pay_prop_transaction_id VARCHAR(100) UNIQUE,
    invoice_id BIGINT,

    INDEX idx_invoice_id (invoice_id),
    INDEX idx_data_source (data_source),
    INDEX idx_payprop_id (pay_prop_transaction_id)
);
```

---

## File Locations

### Core Services

**Unified Transaction Management**:
- `src/main/java/site/easy/to/build/crm/service/transaction/UnifiedTransactionRebuildService.java`

**Historical Import**:
- `src/main/java/site/easy/to/build/crm/service/transaction/HistoricalTransactionImportService.java`

**Statement Generation**:
- `src/main/java/site/easy/to/build/crm/service/statement/StatementServiceOptionC.java`

**PayProp Integration**:
- `src/main/java/site/easy/to/build/crm/service/payprop/PayPropSyncService.java` (if exists)

### Controllers

**API Endpoints**:
- `src/main/java/site/easy/to/build/crm/controller/StatementController.java`
- `src/main/java/site/easy/to/build/crm/controller/UnifiedTransactionController.java`

### Frontend

**UI Pages**:
- `src/main/resources/static/enhanced-statements.html` - Admin statement UI
- `src/main/resources/static/statements.html` - Property owner statement UI

---

## Known Issues & Fixes Applied

### Issue 1: Duplicate ICDN_ACTUAL Invoices
**Problem**: Rent invoices imported as payments, causing double-counting

**Fix**: Excluded ICDN_ACTUAL from import in `UnifiedTransactionRebuildService.java:218`

**Status**: ✅ Fixed (163 duplicate invoices removed)

### Issue 2: Miscategorized Rent Transactions
**Problem**: 124 rent payments marked as OUTGOING instead of INCOMING

**Fix**:
- Added category_name checking for PayProp transactions
- Bulk updated existing miscategorized records

**Status**: ✅ Fixed (124 transactions corrected)

### Issue 3: Missing Flat 3 April Payment
**Problem**: Transaction existed but had flow_direction='OUTGOING'

**Root Cause**: Historical import with NULL category defaulted to OUTGOING

**Fix**: Manually updated Transaction ID 2590 to INCOMING

**Status**: ✅ Fixed

---

## Future Enhancements

### Portfolio Management
- Group properties into blocks/portfolios
- Generate block-level financial summaries
- Multi-property owner dashboards

### Advanced Reporting
- Year-end tax summaries
- Cash flow projections
- Arrears tracking and alerts

### Data Source Filtering
- UI controls for selecting data sources
- Historical vs. PayProp comparison views
- Data source audit trails

---

## Glossary

**Flow Direction**: Whether money is coming in (INCOMING) or going out (OUTGOING)

**ICDN_ACTUAL**: PayProp's rent invoice/charge records (not actual payments)

**INCOMING_PAYMENT**: PayProp's actual tenant payment records

**Unified Transaction**: Normalized transaction record combining all sources

**Pro-rating**: Calculating partial rent for partial months based on actual days

**Lease Reference**: Unique identifier for a property lease period

**Period Start Day**: Day of month when billing periods begin (1st, 22nd, 25th, or 28th)

---

## Contact & Support

For questions about this financial system, refer to:
- This documentation
- Code comments in service files
- Java test files for examples

---

**Document Version**: 1.0
**Last Updated**: 2025-11-03
**Maintained By**: CreCRM Development Team

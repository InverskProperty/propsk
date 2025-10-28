# Monthly Statement Spreadsheet Generation - Database Mapping

**Purpose:** Generate monthly property owner statements automatically from database

---

## Overview of Spreadsheet Structure

### Page 1: Lease Master List
Simple listing of all active leases

### Page 2: Monthly Owner Statement
Complex statement showing:
- Rent due (pro-rated for partial periods)
- Rent received (from transactions)
- Management fees calculated
- Expenses allocated
- Net amount due to owner
- Tenant arrears tracking

---

## Database Mapping

### LEASE MASTER LIST - Source Tables

**Primary Source:** `invoices` table (43 records) or `invoice_instructions` table

#### Column Mapping

| Spreadsheet Column | Database Field | Table | Notes |
|-------------------|----------------|-------|-------|
| Lease Reference | lease_reference | invoices | LEASE-BH-F1-2025 |
| Unit Number | property.name or property.unit_number | properties | FLAT 1 - 3 WEST GATE |
| Tenant Name(s) | customer.name | customers | Via customer_id FK |
| Lease Start Date | start_date (invoices) or from_date (invoice_instructions) | invoices/invoice_instructions | Excel serial date |
| Lease End Date | end_date (invoices) or to_date (invoice_instructions) | invoices/invoice_instructions | Blank if ongoing |
| Payment Day | payment_day | invoices/invoice_instructions | Day of month (1-31) |
| Monthly Rent | amount | invoices/invoice_instructions | Base rent amount |
| Management Fee % | commission_rate or category-based | invoices or properties | 0.2 = 20%, 0.1 = 10% |

#### SQL Query for Lease Master List

```sql
SELECT
    i.lease_reference,
    p.name AS unit_number,
    c.name AS tenant_name,
    i.start_date,
    i.end_date,
    i.payment_day,
    i.amount AS monthly_rent,
    COALESCE(i.commission_rate, 0.15) AS management_fee_pct
FROM invoices i
LEFT JOIN properties p ON i.property_id = p.id
LEFT JOIN customers c ON i.customer_id = c.id
WHERE i.is_active = TRUE
  AND i.deleted_at IS NULL
ORDER BY p.name, i.lease_reference;
```

---

## MONTHLY OWNER STATEMENT - Complex Calculations

### Statement Header

| Field | Source | Notes |
|-------|--------|-------|
| CLIENT | Customer name | From property owner |
| Date BOP | Input parameter | Beginning of period |
| Date EOP | Input parameter | End of period |
| PROPERTY | Property/Block name | Single property or all properties |

### Row-Level Data Mapping

#### Base Lease Information (Columns A-F)

| Column | Field | Source | Notes |
|--------|-------|--------|-------|
| A. Lease Reference | lease_reference | invoices | |
| B. Unit No. | property.name | properties | |
| C. Tenant | customer.name | customers | |
| D. Tenancy Start Date | start_date | invoices | |
| E. Tenancy End Date | end_date | invoices | NULL if ongoing |
| F. Rent Due Date | payment_day | invoices | Only show if active in period |

#### Rent Due Amount (Column G) - COMPLEX

**Business Logic:**
1. Only calculate if lease is active during the period
2. If lease runs full month → full rent amount
3. If lease starts mid-period → pro-rate from start date
4. If lease ends mid-period → pro-rate until end date
5. Use 30-day month for calculations

**Calculation Formula:**
```
IF lease NOT active in period:
    0
ELSE IF lease_end_date exists AND lease_end_date < period_end:
    # Pro-rate for partial period
    days_occupied = DATEDIFF(lease_end_date, period_start) + 1
    pro_rated_amount = (monthly_rent / 30) * days_occupied
ELSE IF lease_start_date > period_start:
    # Started mid-period
    days_occupied = DATEDIFF(period_end, lease_start_date) + 1
    pro_rated_amount = (monthly_rent / 30) * days_occupied
ELSE:
    # Full month
    monthly_rent
```

**SQL Implementation:**
```sql
CASE
    -- Lease not active in period
    WHEN i.start_date > @period_end OR (i.end_date IS NOT NULL AND i.end_date < @period_start) THEN 0

    -- Lease ends during period (pro-rate)
    WHEN i.end_date IS NOT NULL AND i.end_date BETWEEN @period_start AND @period_end THEN
        ROUND(i.amount * (DATEDIFF(i.end_date, @period_start) + 1) / 30.0, 2)

    -- Lease starts during period (pro-rate)
    WHEN i.start_date BETWEEN @period_start AND @period_end THEN
        ROUND(i.amount * (DATEDIFF(@period_end, i.start_date) + 1) / 30.0, 2)

    -- Full month
    ELSE i.amount
END AS rent_due_amount
```

#### Rent Received (Columns H-M)

**Data Source:** `historical_transactions` or `financial_transactions`

**Query Logic:**
```sql
SELECT
    transaction_date AS rent_received_date,
    amount AS rent_received_amount
FROM historical_transactions
WHERE invoice_id = @invoice_id
  AND transaction_date BETWEEN @period_start AND @period_end
  AND transaction_type = 'payment'
  AND amount > 0
ORDER BY transaction_date
LIMIT 2;
```

**Or from financial_transactions:**
```sql
SELECT
    reconciliation_date AS rent_received_date,
    amount AS rent_received_amount
FROM financial_transactions
WHERE invoice_id = @invoice_id
  AND reconciliation_date BETWEEN @period_start AND @period_end
  AND transaction_type IN ('invoice', 'payment')
  AND amount > 0
ORDER BY reconciliation_date
LIMIT 2;
```

**Or from payprop_incoming_payments:**
```sql
SELECT
    reconciliation_date AS rent_received_date,
    amount AS rent_received_amount
FROM payprop_incoming_payments pip
JOIN properties p ON pip.property_payprop_id = p.payprop_id
WHERE p.id = @property_id
  AND reconciliation_date BETWEEN @period_start AND @period_end
  AND pip.synced_to_historical = TRUE
ORDER BY reconciliation_date
LIMIT 2;
```

| Column | Calculation | Notes |
|--------|-------------|-------|
| H. Rent Received 1 Date | transaction_date | First payment in period |
| I. Rent Received 1 Amount | amount | First payment amount |
| J. Rent Received 2 Date | transaction_date | Second payment in period |
| K. Rent Received 2 Amount | amount | Second payment amount |
| L. Rent Received Total | SUM(amounts) | Total payments received |

#### Management Fee (Columns M-N)

| Column | Calculation | Source |
|--------|-------------|--------|
| M. Management Fee (%) | commission_rate | invoices.commission_rate or fixed % per property |
| N. Management Fee Amount | Rent Received Total × Management Fee % | Calculated |

**SQL:**
```sql
SELECT
    COALESCE(i.commission_rate, 0.15) AS management_fee_pct,
    ROUND(@rent_received_total * COALESCE(i.commission_rate, 0.15), 2) AS management_fee_amount
FROM invoices i
WHERE i.id = @invoice_id;
```

#### Expenses (Columns O-W)

**Data Source:** `historical_transactions` with transaction_type IN ('expense', 'maintenance', 'fee')

**Query:**
```sql
SELECT
    category AS expense_label,
    ABS(amount) AS expense_amount
FROM historical_transactions
WHERE invoice_id = @invoice_id
  AND transaction_date BETWEEN @period_start AND @period_end
  AND transaction_type IN ('expense', 'maintenance', 'fee')
  AND amount < 0  -- Expenses are negative
ORDER BY transaction_date
LIMIT 4;
```

| Column | Data | Notes |
|--------|------|-------|
| O. Expense 1 Label | category | e.g., "Maintenance", "Repairs" |
| P. Expense 1 Amount | ABS(amount) | Positive amount |
| Q. Expense 2 Label | category | |
| R. Expense 2 Amount | ABS(amount) | |
| S. Expense 3 Label | category | |
| T. Expense 3 Amount | ABS(amount) | |
| U. Expense 4 Label | category | |
| V. Expense 4 Amount | ABS(amount) | |
| W. Total Expenses | SUM(expense amounts) | |

#### Calculated Totals (Columns X-Z)

| Column | Formula | Business Logic |
|--------|---------|----------------|
| X. Net Due to Owner | Rent Received Total - Management Fee Amount - Total Expenses | What owner receives |
| Y. Rent Due Less Received Total | Rent Due Amount - Rent Received Total | Shortfall/Overpayment |
| Z. Tenant Balance | Running balance of arrears | May need separate calculation |

**Tenant Balance Calculation:**
This is typically a running total across all periods:
```sql
SELECT
    SUM(
        CASE
            WHEN transaction_type IN ('payment') THEN amount
            WHEN transaction_type IN ('invoice') THEN -amount
            ELSE 0
        END
    ) AS tenant_balance
FROM historical_transactions
WHERE invoice_id = @invoice_id
  AND transaction_date <= @period_end;
```

Or calculated as:
```sql
-- Opening balance + this month's rent due - this month's payments
@opening_balance + @rent_due_amount - @rent_received_total
```

---

## Complete Statement Generation SQL

### Main Query Structure

```sql
WITH period_params AS (
    SELECT
        @period_start AS period_start,
        @period_end AS period_end,
        @property_id AS property_id
),

active_leases AS (
    SELECT
        i.id AS invoice_id,
        i.lease_reference,
        p.name AS unit_no,
        c.name AS tenant,
        i.start_date AS tenancy_start_date,
        i.end_date AS tenancy_end_date,
        i.payment_day AS rent_due_date,
        i.amount AS base_rent,
        COALESCE(i.commission_rate, 0.15) AS management_fee_pct,

        -- Calculate rent due for period (pro-rated)
        CASE
            WHEN i.start_date > @period_end OR (i.end_date IS NOT NULL AND i.end_date < @period_start) THEN 0
            WHEN i.end_date IS NOT NULL AND i.end_date BETWEEN @period_start AND @period_end THEN
                ROUND(i.amount * (DATEDIFF(i.end_date, @period_start) + 1) / 30.0, 2)
            WHEN i.start_date BETWEEN @period_start AND @period_end THEN
                ROUND(i.amount * (DATEDIFF(@period_end, i.start_date) + 1) / 30.0, 2)
            ELSE i.amount
        END AS rent_due_amount

    FROM invoices i
    JOIN properties p ON i.property_id = p.id
    JOIN customers c ON i.customer_id = c.id
    WHERE i.is_active = TRUE
      AND i.deleted_at IS NULL
      AND (i.property_id = @property_id OR @property_id IS NULL)
      AND i.start_date <= @period_end
      AND (i.end_date IS NULL OR i.end_date >= @period_start)
),

payments_received AS (
    SELECT
        ht.invoice_id,
        MAX(CASE WHEN rn = 1 THEN ht.transaction_date END) AS rent_received_1_date,
        MAX(CASE WHEN rn = 1 THEN ht.amount END) AS rent_received_1_amount,
        MAX(CASE WHEN rn = 2 THEN ht.transaction_date END) AS rent_received_2_date,
        MAX(CASE WHEN rn = 2 THEN ht.amount END) AS rent_received_2_amount,
        SUM(ht.amount) AS rent_received_total
    FROM (
        SELECT
            invoice_id,
            transaction_date,
            amount,
            ROW_NUMBER() OVER (PARTITION BY invoice_id ORDER BY transaction_date) AS rn
        FROM historical_transactions
        WHERE transaction_date BETWEEN @period_start AND @period_end
          AND transaction_type = 'payment'
          AND amount > 0
    ) ht
    WHERE rn <= 2
    GROUP BY ht.invoice_id
),

expenses_aggregated AS (
    SELECT
        invoice_id,
        MAX(CASE WHEN rn = 1 THEN category END) AS expense_1_label,
        MAX(CASE WHEN rn = 1 THEN ABS(amount) END) AS expense_1_amount,
        MAX(CASE WHEN rn = 2 THEN category END) AS expense_2_label,
        MAX(CASE WHEN rn = 2 THEN ABS(amount) END) AS expense_2_amount,
        MAX(CASE WHEN rn = 3 THEN category END) AS expense_3_label,
        MAX(CASE WHEN rn = 3 THEN ABS(amount) END) AS expense_3_amount,
        MAX(CASE WHEN rn = 4 THEN category END) AS expense_4_label,
        MAX(CASE WHEN rn = 4 THEN ABS(amount) END) AS expense_4_amount,
        SUM(ABS(amount)) AS total_expenses
    FROM (
        SELECT
            invoice_id,
            category,
            amount,
            ROW_NUMBER() OVER (PARTITION BY invoice_id ORDER BY transaction_date) AS rn
        FROM historical_transactions
        WHERE transaction_date BETWEEN @period_start AND @period_end
          AND transaction_type IN ('expense', 'maintenance', 'fee')
          AND amount < 0
    ) ht
    WHERE rn <= 4
    GROUP BY invoice_id
)

SELECT
    al.lease_reference,
    al.unit_no,
    al.tenant,
    al.tenancy_start_date,
    al.tenancy_end_date,
    al.rent_due_date,
    al.rent_due_amount,

    pr.rent_received_1_date,
    pr.rent_received_1_amount,
    pr.rent_received_2_date,
    pr.rent_received_2_amount,
    COALESCE(pr.rent_received_total, 0) AS rent_received_total,

    al.management_fee_pct,
    ROUND(COALESCE(pr.rent_received_total, 0) * al.management_fee_pct, 2) AS management_fee_amount,

    ea.expense_1_label,
    ea.expense_1_amount,
    ea.expense_2_label,
    ea.expense_2_amount,
    ea.expense_3_label,
    ea.expense_3_amount,
    ea.expense_4_label,
    ea.expense_4_amount,
    COALESCE(ea.total_expenses, 0) AS total_expenses,

    -- Net due to owner
    COALESCE(pr.rent_received_total, 0)
        - ROUND(COALESCE(pr.rent_received_total, 0) * al.management_fee_pct, 2)
        - COALESCE(ea.total_expenses, 0) AS net_due_to_owner,

    -- Rent shortfall
    al.rent_due_amount - COALESCE(pr.rent_received_total, 0) AS rent_due_less_received,

    -- Tenant balance (would need additional calculation)
    NULL AS tenant_balance

FROM active_leases al
LEFT JOIN payments_received pr ON al.invoice_id = pr.invoice_id
LEFT JOIN expenses_aggregated ea ON al.invoice_id = ea.invoice_id
ORDER BY al.unit_no;
```

---

## Implementation Recommendations

### 1. **Use invoice_instructions or invoices?**

**Current State:**
- `invoices` table: 43 records, legacy, has FK links to transactions
- `invoice_instructions` table: Current PayProp-synced system

**Recommendation:**
- Start with `invoices` table since it has `invoice_id` links in both `historical_transactions` and `financial_transactions`
- If using `invoice_instructions`, you'll need to:
  - Add invoice_id FK to transactions
  - OR match by property_id + tenant_id + date range

### 2. **Transaction Source Priority**

For rent received, use this priority:
1. **historical_transactions** with `invoice_id` link (most reliable)
2. **payprop_incoming_payments** synced records
3. **financial_transactions** with `invoice_id` link

### 3. **Missing Data Handling**

**Current Issues:**
- Not all transactions have `invoice_id` populated
- PayProp transactions use VARCHAR IDs, not BIGINT FKs
- Some properties/tenants may not have lease records

**Solutions:**
- Populate `invoice_id` in transactions via backfill script matching:
  - property_id + tenant_id + date within lease period
- Create view that unions both transaction types
- Flag orphaned transactions for manual review

### 4. **Tenant Balance Calculation**

**Option A: Running Total in Database**
Add `tenant_balance` column to tenants or tenant_balances table, updated on each transaction

**Option B: Calculate on Demand**
```sql
SELECT
    invoice_id,
    SUM(
        CASE
            WHEN transaction_type IN ('payment') THEN amount
            WHEN transaction_type IN ('invoice', 'expense', 'fee') THEN -ABS(amount)
            ELSE 0
        END
    ) AS tenant_balance
FROM historical_transactions
WHERE invoice_id = @invoice_id
  AND transaction_date <= @period_end
GROUP BY invoice_id;
```

**Recommendation:** Option B for accuracy, Option A for performance

### 5. **Pro-Rating Logic**

Your current spreadsheet uses complex Excel formula with:
- 30-day months
- Inclusive date ranges (DATEDIF + 1)
- Handles lease end during period

**Database Implementation:**
```sql
-- Days in period
DATEDIFF(
    LEAST(COALESCE(i.end_date, @period_end), @period_end),
    GREATEST(i.start_date, @period_start)
) + 1

-- Pro-rated amount
ROUND(i.amount * days_in_period / 30.0, 2)
```

---

## Export Format

### Option 1: Generate Excel via Java/Spring

Use Apache POI or similar library to generate `.xlsx` files with:
- Multiple sheets (Lease Master, Monthly Statement)
- Formulas for totals
- Formatting (borders, bold headers, etc.)

### Option 2: CSV Export

Simple CSV files that can be imported into Excel template with formulas preserved

### Option 3: Google Sheets API

Push data directly to Google Sheets with formatting and formulas

---

## Key Fields Needed But Possibly Missing

### In invoices/invoice_instructions:
- ✅ lease_reference - **EXISTS** in invoices
- ✅ start_date/end_date - **EXISTS**
- ✅ payment_day - **EXISTS**
- ✅ amount - **EXISTS**
- ⚠️ commission_rate - **EXISTS** in invoices as commission field?
- ⚠️ property_id linkage

### In transactions:
- ⚠️ invoice_id - **EXISTS** but not always populated
- ✅ transaction_date - **EXISTS**
- ✅ amount - **EXISTS**
- ✅ transaction_type - **EXISTS**
- ✅ category - **EXISTS**

### Gaps to Address:
1. **invoice_id not always populated in transactions** - Need backfill
2. **Commission rates may be property-level, not lease-level** - May need to join via properties table
3. **Tenant balance requires calculation** - Not stored, must calculate
4. **PayProp ID mismatches** - Need mapping between PayProp varchar IDs and local BIGINT IDs

---

## Next Steps

1. **Validate invoice_id coverage** - How many transactions have invoice_id populated?
2. **Test pro-rating logic** - Verify calculations match your Excel formulas
3. **Build backfill script** - Populate missing invoice_id in transactions
4. **Create statement generation endpoint** - REST API to generate statements
5. **Add export functionality** - Excel/CSV export of statements
6. **Build UI** - Interface to select client, property, date range

---

## SQL Queries for Validation

### Check invoice_id coverage:
```sql
SELECT
    'historical_transactions' AS table_name,
    COUNT(*) AS total_records,
    SUM(CASE WHEN invoice_id IS NOT NULL THEN 1 ELSE 0 END) AS with_invoice_id,
    ROUND(100.0 * SUM(CASE WHEN invoice_id IS NOT NULL THEN 1 ELSE 0 END) / COUNT(*), 2) AS pct_coverage
FROM historical_transactions
WHERE transaction_type IN ('payment', 'expense', 'maintenance', 'fee')

UNION ALL

SELECT
    'financial_transactions' AS table_name,
    COUNT(*) AS total_records,
    SUM(CASE WHEN invoice_id IS NOT NULL THEN 1 ELSE 0 END) AS with_invoice_id,
    ROUND(100.0 * SUM(CASE WHEN invoice_id IS NOT NULL THEN 1 ELSE 0 END) / COUNT(*), 2) AS pct_coverage
FROM financial_transactions;
```

### Find transactions that should have invoice_id but don't:
```sql
SELECT
    ht.id,
    ht.transaction_date,
    ht.amount,
    ht.description,
    ht.property_id,
    ht.customer_id,
    i.id AS matching_invoice_id,
    i.lease_reference
FROM historical_transactions ht
LEFT JOIN invoices i ON
    ht.property_id = i.property_id
    AND ht.customer_id = i.customer_id
    AND ht.transaction_date BETWEEN i.start_date AND COALESCE(i.end_date, '2099-12-31')
WHERE ht.invoice_id IS NULL
  AND ht.transaction_type IN ('payment', 'expense', 'maintenance', 'fee')
  AND i.id IS NOT NULL;
```

---

**End of Analysis**

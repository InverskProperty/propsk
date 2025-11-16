# Quick Implementation Guide

## Problem Summary
Your financial reports are missing PayProp data because views query `historical_transactions` (OLD_ACCOUNT only) instead of `unified_transactions` (both sources).

## Solution
Use the new views that query `unified_transactions` for complete data.

---

## Step-by-Step Implementation

### 1. Backup Current State (5 minutes)
```sql
-- Connect to database
mysql -h switchyard.proxy.rlwy.net -P 55090 -u root -p railway

-- Create backup
CREATE TABLE backup_v_monthly_summary_20250116 AS
SELECT * FROM v_monthly_summary_by_account;

CREATE TABLE backup_financial_summary_month_20250116 AS
SELECT * FROM financial_summary_by_month;

CREATE TABLE backup_financial_summary_property_20250116 AS
SELECT * FROM financial_summary_by_property;
```

### 2. Deploy New Views (2 minutes)
```bash
# From C:\Users\sajid\crecrm directory
"C:\Program Files\MySQL\MySQL Server 9.3\bin\mysql.exe" -h switchyard.proxy.rlwy.net -P 55090 -u root -p railway < sql\corrected_financial_views.sql
```

### 3. Verify Data (5 minutes)
```sql
-- Test June 2025 totals
SELECT
    year_month,
    old_account_incoming,
    payprop_incoming,
    total_incoming,
    old_account_incoming_count,
    payprop_incoming_count
FROM v_unified_monthly_summary
WHERE year_month = '2025-06';

-- Expected result:
-- old_account_incoming: 16475.00
-- payprop_incoming: 740.00 (or 3690.00 after date fix)
-- total_incoming: 17215.00 (or 20165.00 after date fix)
```

### 4. Test Block Summary (2 minutes)
```sql
SELECT
    block_name,
    property_count,
    total_incoming,
    old_account_incoming,
    payprop_incoming
FROM v_unified_block_summary
WHERE block_name = 'Boden House NG10';
```

### 5. Update Application Code

#### Replace These Queries:

**OLD:**
```sql
SELECT * FROM v_monthly_summary_by_account WHERE statement_month = 'June 2025';
```

**NEW:**
```sql
SELECT * FROM v_unified_monthly_summary WHERE year_month = '2025-06';
```

---

**OLD:**
```sql
SELECT * FROM financial_summary_by_property WHERE property_id = 123;
```

**NEW:**
```sql
SELECT * FROM v_unified_property_summary WHERE property_id = 123;
```

---

## View Mapping Reference

| Old View | New View | Purpose |
|----------|----------|---------|
| `v_monthly_summary_by_account` | `v_unified_monthly_summary` | Monthly totals by account |
| `financial_summary_by_month` | `v_unified_monthly_summary` | Monthly financial summary |
| `financial_summary_by_property` | `v_unified_property_summary` | Property-level summary |
| *(none)* | `v_unified_block_summary` | **NEW** - Block-level summary |
| *(none)* | `v_unified_portfolio_summary` | **NEW** - Portfolio-level summary |
| *(none)* | `v_unified_transaction_detail` | **NEW** - Enriched transaction detail |

---

## Common Queries

### Get All Transactions for a Property
```sql
SELECT
    transaction_date,
    amount,
    description,
    payment_source_name,
    flow_direction
FROM v_unified_transaction_detail
WHERE property_name = 'Flat 5 - 3 West Gate'
ORDER BY transaction_date DESC;
```

### Get Monthly Summary for Specific Period
```sql
SELECT * FROM v_unified_monthly_summary
WHERE transaction_date BETWEEN '2025-01-01' AND '2025-06-30'
ORDER BY transaction_year DESC, transaction_month DESC;
```

### Get All Properties in Boden House Block
```sql
SELECT
    property_name,
    total_incoming,
    old_account_incoming,
    payprop_incoming
FROM v_unified_property_summary
WHERE block_name = 'Boden House NG10'
ORDER BY property_name;
```

### Get Portfolio Performance
```sql
SELECT
    portfolio_name,
    property_count,
    block_count,
    total_incoming,
    total_outgoing,
    net_position
FROM v_unified_portfolio_summary;
```

---

## Troubleshooting

### Issue: Views Don't Exist
**Solution:** Run the deployment script again
```bash
"C:\Program Files\MySQL\MySQL Server 9.3\bin\mysql.exe" -h switchyard.proxy.rlwy.net -P 55090 -u root -p railway < sql\corrected_financial_views.sql
```

### Issue: PayProp Totals Still Wrong
**Cause:** PayProp reconciliation dates are June 23 (outside June period)
**Solution:** See "PayProp Date Fix" section below

### Issue: Block Summary Shows NULL
**Cause:** Properties not assigned to blocks
**Solution:** Assign properties to blocks:
```sql
-- Check current assignments
SELECT p.id, p.property_name, pba.block_id
FROM properties p
LEFT JOIN property_block_assignments pba ON p.id = pba.property_id
WHERE p.property_name LIKE '%West Gate%';

-- If missing, they should already be in property_block_assignments table
-- Verify: SELECT * FROM property_block_assignments;
```

---

## PayProp Date Fix (Optional - Resolves June Discrepancy)

The issue: PayProp payments dated June 23 fall outside the June period (2025-05-22 to 2025-06-21).

### Option 1: Adjust Your Period Dates
Use calendar months instead:
```sql
WHERE transaction_date BETWEEN '2025-06-01' AND '2025-06-30'
```

### Option 2: Add Payment Date Field to PayProp Import
Update the PayProp sync to capture actual payment date (not just reconciliation date).

---

## Rollback Plan (If Needed)

If something goes wrong, restore old views:
```sql
-- Drop new views
DROP VIEW IF EXISTS v_unified_monthly_summary;
DROP VIEW IF EXISTS v_unified_property_summary;
DROP VIEW IF EXISTS v_unified_block_summary;
DROP VIEW IF EXISTS v_unified_portfolio_summary;
DROP VIEW IF EXISTS v_unified_transaction_detail;

-- Old views are still there (we didn't drop them)
-- Just use the old view names again
```

---

## Timeline

- **Immediate:** Deploy views (7 minutes)
- **Day 1:** Update application queries
- **Week 1:** Monitor data accuracy
- **Week 2:** Fix PayProp date handling
- **Month 1:** Assign all properties to blocks/portfolios

---

## Questions?

Check these files:
- `FINDINGS_AND_RECOMMENDATIONS.md` - Detailed analysis
- `sql/corrected_financial_views.sql` - View definitions with examples

---

**Last Updated:** 2025-01-16

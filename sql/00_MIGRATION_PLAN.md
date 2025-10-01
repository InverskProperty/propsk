# Transaction Table Unification - Migration Plan

## Overview

This migration consolidates all transaction data into a single canonical `historical_transactions` table (which will eventually be renamed to `transactions`).

## Architecture

### Before:
```
PayProp API → financial_transactions → Statement Generation
CSV Imports → historical_transactions → (Not used by statements)
payprop_report_* → (Raw staging, not normalized)
```

### After:
```
PayProp API → payprop_report_* (raw) → historical_transactions (canonical)
CSV Imports → raw_csv_imports (raw) → historical_transactions (canonical)
Bank Import → raw_bank_statements (raw) → historical_transactions (canonical)
Spreadsheets → raw_spreadsheet_imports (raw) → historical_transactions (canonical)
Manual Entry → raw_manual_entries (raw) → historical_transactions (canonical)

All sources → historical_transactions → Statement Generation & All Reports
```

## Migration Steps

### Step 1: Enhance Schema
**File:** `01_enhance_historical_transactions.sql`

**What it does:**
- Adds PayProp integration fields (payprop_transaction_id, payprop_property_id, etc.)
- Adds commission tracking fields (commission_rate, commission_amount, service_fee_amount)
- Adds instruction tracking (is_instruction, is_actual_transaction)
- Adds batch payment support
- Creates necessary indexes

**Run:**
```bash
mysql -h switchyard.proxy.rlwy.net -P 55090 -u root -p'iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW' railway < sql/01_enhance_historical_transactions.sql
```

**Verify:**
```sql
DESCRIBE historical_transactions;
-- Should show all new PayProp fields
```

### Step 2: Create Raw Import Tables
**File:** `02_create_raw_import_tables.sql`

**What it does:**
- Creates `raw_csv_imports` for CSV staging
- Creates `raw_bank_statements` for bank import staging
- Creates `raw_spreadsheet_imports` for Excel/Google Sheets staging
- Creates `raw_manual_entries` for UI form submissions
- Creates `import_batches` for tracking all imports
- Creates `normalization_log` for audit trail

**Run:**
```bash
mysql -h switchyard.proxy.rlwy.net -P 55090 -u root -p'iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW' railway < sql/02_create_raw_import_tables.sql
```

**Verify:**
```sql
SHOW TABLES LIKE 'raw_%';
SHOW TABLES LIKE 'import_%';
SHOW TABLES LIKE 'normalization_%';
```

### Step 3: Migrate Existing Data
**File:** `03_migrate_financial_transactions.sql`

**What it does:**
- Migrates all data from `financial_transactions` into `historical_transactions`
- Maps field names appropriately
- Converts transaction types to match enum
- Links to properties via pay_prop_id
- Sets source='api_sync' and account_source='propsk_payprop'
- Avoids duplicates

**Before running:**
1. **BACKUP FIRST:**
```sql
CREATE TABLE financial_transactions_backup AS SELECT * FROM financial_transactions;
CREATE TABLE historical_transactions_backup AS SELECT * FROM historical_transactions;
```

2. **Count existing records:**
```sql
SELECT COUNT(*) FROM financial_transactions;
SELECT COUNT(*) FROM historical_transactions;
```

**Run:**
```bash
mysql -h switchyard.proxy.rlwy.net -P 55090 -u root -p'iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW' railway < sql/03_migrate_financial_transactions.sql
```

**Verify:**
```sql
-- Count migrated records
SELECT
    'Original financial_transactions' as table_name,
    COUNT(*) as count
FROM financial_transactions
UNION ALL
SELECT
    'Migrated to historical_transactions',
    COUNT(*)
FROM historical_transactions
WHERE payprop_transaction_id IS NOT NULL;

-- Check property linking success
SELECT
    COUNT(*) as total_payprop_records,
    COUNT(property_id) as linked_to_properties,
    COUNT(*) - COUNT(property_id) as missing_property_link
FROM historical_transactions
WHERE payprop_transaction_id IS NOT NULL;

-- Sample migrated data
SELECT * FROM historical_transactions
WHERE payprop_transaction_id IS NOT NULL
LIMIT 5;
```

### Step 4: Update Application Code
**Files to modify:**
1. `PayPropFinancialSyncService.java` - Change to save into `historical_transactions`
2. `BodenHouseStatementTemplateService.java` - Change to query `historical_transactions`
3. Create normalization services for each raw table

**Changes needed:**
- Repository: Use `HistoricalTransactionRepository` instead of `FinancialTransactionRepository`
- Entity: Use `HistoricalTransaction` instead of `FinancialTransaction`
- Queries: Update all queries to use new table/entity

### Step 5: Final Cleanup (AFTER FULL VERIFICATION)

**Option A - Keep as backup:**
```sql
RENAME TABLE financial_transactions TO financial_transactions_deprecated;
```

**Option B - Remove completely (only after 100% confidence):**
```sql
DROP TABLE financial_transactions;
```

**Optional - Rename for clarity:**
```sql
RENAME TABLE historical_transactions TO transactions;
-- Then update all Java entities and repositories to match
```

## Current Data State

Based on validation report:
- `historical_transactions`: 844 records (manual CSV import from Dec 2024 - Aug 2025)
  - 574 transactions from old account
  - 270 transactions from PayProp account
  - Has `account_source`, `statement_month`, `related_transaction_group`

- `financial_transactions`: Unknown count (PayProp API synced data)
  - Real-time PayProp data
  - Used by current statement generation

- `payprop_report_*`: Multiple tables with raw PayProp report data
  - `payprop_report_icdn`
  - `payprop_report_all_payments`
  - `payprop_report_tenant_balances`
  - etc.

## Rollback Plan

If something goes wrong:

1. **Restore from backup:**
```sql
DROP TABLE historical_transactions;
CREATE TABLE historical_transactions AS SELECT * FROM historical_transactions_backup;

DROP TABLE financial_transactions;
CREATE TABLE financial_transactions AS SELECT * FROM financial_transactions_backup;
```

2. **Revert code changes** in git

3. **Redeploy** previous version

## Testing Checklist

After migration, test:
- [ ] Statement generation works correctly
- [ ] Can query transactions by property
- [ ] Can query transactions by date range
- [ ] Commission calculations are correct
- [ ] Owner payment amounts match
- [ ] Account source tracking preserved
- [ ] CSV import still works
- [ ] PayProp sync continues to work
- [ ] No duplicate transactions
- [ ] Property links are correct

## Benefits After Migration

1. ✅ **Single source of truth** - All transactions in one table
2. ✅ **Unified queries** - No more UNION between tables
3. ✅ **Complete audit trail** - Raw tables preserve original data
4. ✅ **Flexible imports** - Easy to add new data sources
5. ✅ **Better data integrity** - Proper foreign keys and validation
6. ✅ **Simpler code** - One repository, one entity, one table
7. ✅ **Future-proof** - Extensible schema for new requirements

## Timeline

- **Preparation:** 30 mins (review scripts, create backups)
- **Schema changes:** 5 mins (run SQL scripts)
- **Data migration:** 5-10 mins (depends on volume)
- **Verification:** 30 mins (thorough testing)
- **Code updates:** 2-3 hours (update services and tests)
- **Final testing:** 1 hour (end-to-end verification)

**Total:** ~4-5 hours

## Questions to Answer Before Migration

1. How many records are in `financial_transactions`?
2. What date range does `financial_transactions` cover?
3. Are there any active processes writing to `financial_transactions`?
4. Is the production database or development database?
5. When is a good maintenance window to run this?

## Next Steps

1. **Review this plan** - Make sure it makes sense
2. **Run verification queries** - Understand current data state
3. **Execute Step 1** - Enhance schema (low risk)
4. **Execute Step 2** - Create raw tables (low risk)
5. **Execute Step 3** - Migrate data (BACKUP FIRST!)
6. **Update code** - Modify Java services
7. **Test thoroughly** - Ensure everything works
8. **Deploy** - Roll out to production

---

**Created:** 2025-10-01
**Status:** Ready for review and execution

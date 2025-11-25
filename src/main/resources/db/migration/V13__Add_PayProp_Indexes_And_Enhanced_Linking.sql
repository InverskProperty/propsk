-- Add indexes on PayProp ID fields for faster lookups and ensure uniqueness
-- Improves performance of PayProp data integration

-- Index on invoices.payprop_id for reverse lookup (PayProp → Local)
CREATE INDEX IF NOT EXISTS idx_invoices_payprop_id ON invoices(payprop_id);

-- Index on invoices.lease_reference for statement generation
CREATE INDEX IF NOT EXISTS idx_invoices_lease_reference ON invoices(lease_reference);

-- Composite index for lease date range queries (property + dates)
CREATE INDEX IF NOT EXISTS idx_invoices_property_dates ON invoices(property_id, start_date, end_date);

-- Index on customer.payprop_entity_id for tenant lookups
CREATE INDEX IF NOT EXISTS idx_customer_payprop_entity_id ON customer(payprop_entity_id);

-- Index on property.payprop_id for property lookups
CREATE INDEX IF NOT EXISTS idx_property_payprop_id ON property(payprop_id);

-- Enhanced backfill with conflict resolution
-- Finds BEST matching invoice for transactions, prioritizing tenant match

-- Report BEFORE enhanced backfill
SELECT
    'BEFORE Enhanced Backfill' as phase,
    COUNT(*) as unlinked_transactions,
    COALESCE(SUM(amount), 0) as unlinked_amount,
    COUNT(DISTINCT property_id) as affected_properties
FROM financial_transactions
WHERE pay_prop_transaction_id IS NOT NULL
  AND invoice_id IS NULL;

-- Enhanced backfill for financial_transactions
-- Uses subquery to find BEST matching invoice with tenant priority
UPDATE financial_transactions ft
SET ft.invoice_id = (
    SELECT i.id
    FROM invoices i
    WHERE CAST(i.property_id AS CHAR) = ft.property_id
      AND i.is_active = true
      AND i.deleted_at IS NULL
      AND ft.transaction_date BETWEEN i.start_date AND COALESCE(i.end_date, '2099-12-31')
    -- Prioritize: 1) Matching tenant, 2) More recent lease
    ORDER BY
        CASE WHEN CAST(i.customer_id AS CHAR) = ft.tenant_id THEN 0 ELSE 1 END,
        i.start_date DESC
    LIMIT 1
)
WHERE ft.pay_prop_transaction_id IS NOT NULL
  AND ft.invoice_id IS NULL
  AND ft.property_id IS NOT NULL;

-- Report AFTER enhanced backfill
SELECT
    'AFTER Enhanced Backfill' as phase,
    COUNT(*) as still_unlinked,
    COALESCE(SUM(amount), 0) as still_unlinked_amount,
    COUNT(DISTINCT property_id) as still_affected_properties
FROM financial_transactions
WHERE pay_prop_transaction_id IS NOT NULL
  AND invoice_id IS NULL;

-- Report successfully linked transactions
SELECT
    'Successfully Linked' as status,
    COUNT(*) as linked_count,
    COALESCE(SUM(amount), 0) as linked_amount,
    COUNT(DISTINCT property_id) as properties_covered,
    COUNT(DISTINCT invoice_id) as invoices_linked
FROM financial_transactions
WHERE invoice_id IS NOT NULL
  AND pay_prop_transaction_id IS NOT NULL;

-- Create view for orphaned transaction detection
CREATE OR REPLACE VIEW v_orphaned_payprop_transactions AS
SELECT
    'FinancialTransaction' as source_table,
    ft.id,
    ft.pay_prop_transaction_id as payprop_id,
    ft.property_id,
    ft.tenant_id,
    ft.transaction_date,
    ft.amount,
    ft.description
FROM financial_transactions ft
WHERE ft.pay_prop_transaction_id IS NOT NULL
  AND ft.invoice_id IS NULL

UNION ALL

SELECT
    'HistoricalTransaction' as source_table,
    ht.id,
    ht.payprop_transaction_id as payprop_id,
    CAST(ht.property_id AS CHAR) as property_id,
    CAST(ht.tenant_id AS CHAR) as tenant_id,
    ht.transaction_date,
    ht.amount,
    ht.description
FROM historical_transactions ht
WHERE ht.payprop_transaction_id IS NOT NULL
  AND ht.invoice_id IS NULL;

COMMENT ON VIEW v_orphaned_payprop_transactions IS 'Identifies transactions with PayProp IDs but no local lease link';

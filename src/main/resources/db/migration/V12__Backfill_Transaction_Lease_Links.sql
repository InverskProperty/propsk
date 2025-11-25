-- Backfill invoice_id for existing financial transactions and historical transactions
-- This links expenses and other transactions to their corresponding leases

-- Update financial_transactions where we can match property + tenant + transaction date to an active lease
UPDATE financial_transactions ft
INNER JOIN invoices i ON
    ft.property_id = CAST(i.property_id AS CHAR)
    AND (ft.tenant_id = CAST(i.customer_id AS CHAR) OR ft.tenant_id IS NULL)
    AND ft.transaction_date BETWEEN i.start_date AND COALESCE(i.end_date, '2099-12-31')
    AND i.is_active = true
    AND i.deleted_at IS NULL
SET ft.invoice_id = i.id
WHERE ft.invoice_id IS NULL
  AND ft.property_id IS NOT NULL
  -- Prioritize matching transactions with tenant_id
  AND (
    ft.tenant_id IS NOT NULL
    OR NOT EXISTS (
      SELECT 1 FROM invoices i2
      WHERE CAST(i2.property_id AS CHAR) = ft.property_id
        AND ft.transaction_date BETWEEN i2.start_date AND COALESCE(i2.end_date, '2099-12-31')
        AND CAST(i2.customer_id AS CHAR) = ft.tenant_id
        AND i2.is_active = true
        AND i2.deleted_at IS NULL
    )
  );

-- Update historical_transactions (these already have invoice_id field from V8 migration)
-- Match by property + tenant/customer + transaction date
UPDATE historical_transactions ht
INNER JOIN invoices i ON
    ht.property_id = i.property_id
    AND (ht.tenant_id = i.customer_id OR ht.customer_id = i.customer_id OR ht.beneficiary_id = i.customer_id)
    AND ht.transaction_date BETWEEN i.start_date AND COALESCE(i.end_date, '2099-12-31')
    AND i.is_active = true
    AND i.deleted_at IS NULL
SET ht.invoice_id = i.id
WHERE ht.invoice_id IS NULL
  AND ht.property_id IS NOT NULL
  AND ht.status = 'active';

-- Log statistics
SELECT
    'FinancialTransactions' as table_name,
    COUNT(*) as transactions_linked,
    COUNT(DISTINCT ft.property_id) as properties_affected,
    COUNT(DISTINCT i.id) as leases_linked
FROM financial_transactions ft
INNER JOIN invoices i ON ft.invoice_id = i.id
WHERE ft.invoice_id IS NOT NULL

UNION ALL

SELECT
    'HistoricalTransactions' as table_name,
    COUNT(*) as transactions_linked,
    COUNT(DISTINCT ht.property_id) as properties_affected,
    COUNT(DISTINCT i.id) as leases_linked
FROM historical_transactions ht
INNER JOIN invoices i ON ht.invoice_id = i.id
WHERE ht.invoice_id IS NOT NULL;

-- Add comments
COMMENT ON COLUMN financial_transactions.invoice_id IS 'Links transaction to specific lease (Invoice) for lease-based expense tracking';

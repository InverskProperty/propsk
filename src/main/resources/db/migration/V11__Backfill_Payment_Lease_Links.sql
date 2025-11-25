-- Backfill invoice_id for existing payments based on property + customer + date matching
-- This links historical payments to their corresponding leases

-- Update payments where we can match property + tenant + payment date to an active lease
UPDATE payments p
INNER JOIN invoices i ON
    p.property_id = i.property_id
    AND p.tenant_id = i.customer_id
    AND p.payment_date BETWEEN i.start_date AND COALESCE(i.end_date, '2099-12-31')
    AND i.is_active = true
    AND i.deleted_at IS NULL
SET p.invoice_id = i.id
WHERE p.invoice_id IS NULL
  AND p.tenant_id IS NOT NULL  -- Only rent payments (payments from tenants)
  AND p.property_id IS NOT NULL;

-- Log the number of payments that were linked
SELECT
    COUNT(*) as payments_linked,
    COUNT(DISTINCT p.property_id) as properties_affected,
    COUNT(DISTINCT i.id) as leases_linked
FROM payments p
INNER JOIN invoices i ON p.invoice_id = i.id
WHERE p.invoice_id IS NOT NULL;

-- Add index to improve query performance on invoice_id if not already present
CREATE INDEX IF NOT EXISTS idx_payments_invoice_id ON payments(invoice_id);

COMMENT ON COLUMN payments.invoice_id IS 'Links payment to specific lease (Invoice) for lease-based financial tracking';

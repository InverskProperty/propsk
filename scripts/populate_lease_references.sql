-- Standalone script to populate lease_reference for existing invoices
-- This can be run directly in Railway dashboard or any MySQL client
-- Safe to run multiple times (idempotent)

-- Check current state
SELECT
    COUNT(*) as total_invoices,
    SUM(CASE WHEN lease_reference IS NOT NULL AND lease_reference != '' THEN 1 ELSE 0 END) as with_lease_ref,
    SUM(CASE WHEN lease_reference IS NULL OR lease_reference = '' THEN 1 ELSE 0 END) as without_lease_ref
FROM invoices
WHERE deleted_at IS NULL;

-- Update invoices that don't have a lease_reference
-- Generate uniform format: LEASE-{id}
UPDATE invoices
SET lease_reference = CONCAT('LEASE-', id)
WHERE (lease_reference IS NULL OR lease_reference = '')
  AND deleted_at IS NULL;

-- Verify the update
SELECT
    COUNT(*) as total_invoices,
    SUM(CASE WHEN lease_reference LIKE 'LEASE-%' THEN 1 ELSE 0 END) as with_uniform_ref,
    SUM(CASE WHEN lease_reference IS NOT NULL AND lease_reference NOT LIKE 'LEASE-%' THEN 1 ELSE 0 END) as with_external_ref,
    SUM(CASE WHEN lease_reference IS NULL OR lease_reference = '' THEN 1 ELSE 0 END) as still_null
FROM invoices
WHERE deleted_at IS NULL;

-- Show sample results
SELECT
    id,
    lease_reference,
    external_reference,
    customer_id,
    property_id,
    category_id,
    amount,
    start_date
FROM invoices
WHERE deleted_at IS NULL
ORDER BY id
LIMIT 20;

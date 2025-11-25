-- V22: Fix Invoice PayProp Tenant IDs
--
-- Problem: PayPropLeaseCreationService was incorrectly storing tenant PayProp IDs
-- in the payprop_id column (which has a UNIQUE constraint) instead of payprop_customer_id.
-- This caused duplicate key violations when multiple leases had the same tenant.
--
-- Solution: Move tenant IDs from payprop_id to payprop_customer_id for affected leases.
-- We identify tenant IDs by matching against payprop_export_tenants_complete.payprop_id

-- Step 1: Update invoices where payprop_id matches a tenant ID (not an invoice instruction ID)
-- Move the value to payprop_customer_id and clear payprop_id
UPDATE invoices i
SET
    payprop_customer_id = CASE
        WHEN i.payprop_customer_id IS NULL THEN i.payprop_id
        ELSE i.payprop_customer_id
    END,
    payprop_id = NULL
WHERE i.payprop_id IS NOT NULL
  AND i.payprop_id IN (
      SELECT t.payprop_id FROM payprop_export_tenants_complete t
  );

-- Step 2: Log how many records were affected (for audit purposes)
-- This creates a marker we can check in application logs
SELECT CONCAT('V22 Migration: Updated ', ROW_COUNT(), ' invoices - moved tenant IDs from payprop_id to payprop_customer_id') AS migration_result;

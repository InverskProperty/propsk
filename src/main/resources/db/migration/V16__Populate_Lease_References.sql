-- V16: Populate lease_reference for existing invoices
-- This migration ensures all invoices have a uniform lease reference (LEASE-{id})
-- Safe to run multiple times (idempotent)

-- Update invoices that don't have a lease_reference
-- Generate uniform format: LEASE-{id}
UPDATE invoices
SET lease_reference = CONCAT('LEASE-', id)
WHERE (lease_reference IS NULL OR lease_reference = '')
  AND deleted_at IS NULL;

-- Migration: Add lease_reference column to invoices table
-- Purpose: Enable user-assigned unique identifiers for leases
-- Used for linking historical transactions to specific leases during import
-- Example: "LEASE-FLAT1-2024", "LEASE-APARTMENT40-2024"

ALTER TABLE invoices
ADD COLUMN lease_reference VARCHAR(100) AFTER external_reference,
ADD UNIQUE INDEX uk_invoices_lease_reference (lease_reference);

-- Add index for faster lookup during historical transaction import
CREATE INDEX idx_invoices_lease_reference_lookup ON invoices(lease_reference)
WHERE lease_reference IS NOT NULL;

-- Add comment to column for documentation
ALTER TABLE invoices
MODIFY COLUMN lease_reference VARCHAR(100)
COMMENT 'User-assigned unique identifier for this lease (e.g., LEASE-FLAT1-2024)';

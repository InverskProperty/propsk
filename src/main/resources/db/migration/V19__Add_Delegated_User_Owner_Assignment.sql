-- Migration: Add property owner assignment for delegated users and managers
-- This allows delegated users and managers to be assigned to specific property owners
-- so they can only access that owner's properties in the customer-login portal

-- Add manages_owner_id column to customers table
ALTER TABLE customers
ADD COLUMN manages_owner_id BIGINT;

-- Add foreign key constraint to link to property owner
ALTER TABLE customers
ADD CONSTRAINT fk_customers_manages_owner
    FOREIGN KEY (manages_owner_id)
    REFERENCES customers(customer_id)
    ON DELETE SET NULL;

-- Add index for performance when filtering by owner
CREATE INDEX idx_customers_manages_owner
    ON customers(manages_owner_id);

-- Add comments for documentation
COMMENT ON COLUMN customers.manages_owner_id IS 'For DELEGATED_USER and MANAGER types: the customer_id of the property owner they manage for';

-- Note: This field should only be populated for customer_type = 'DELEGATED_USER' or 'MANAGER'
-- Property owners, tenants, and contractors should have NULL for this field

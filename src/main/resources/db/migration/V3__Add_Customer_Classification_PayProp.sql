-- Migration to add customer classification and PayProp integration fields
-- File: src/main/resources/db/migration/V3__Add_Customer_Classification_PayProp.sql

-- Add new columns to customer table with safe defaults
ALTER TABLE customer 
ADD COLUMN customer_type VARCHAR(20) NOT NULL DEFAULT 'REGULAR_CUSTOMER',
ADD COLUMN payprop_entity_id VARCHAR(32) NULL,
ADD COLUMN payprop_customer_id VARCHAR(50) NULL,
ADD COLUMN payprop_synced BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN payprop_last_sync DATETIME NULL,
ADD COLUMN is_property_owner BOOLEAN DEFAULT FALSE,
ADD COLUMN is_tenant BOOLEAN DEFAULT FALSE,
ADD COLUMN is_contractor BOOLEAN DEFAULT FALSE;

-- Create indexes for performance
CREATE INDEX idx_customer_type ON customer(customer_type);
CREATE INDEX idx_customer_payprop_entity ON customer(payprop_entity_id);
CREATE INDEX idx_customer_payprop_customer ON customer(payprop_customer_id);
CREATE INDEX idx_customer_payprop_synced ON customer(payprop_synced);

-- Add unique constraints for PayProp fields (allow nulls)
ALTER TABLE customer 
ADD CONSTRAINT uk_customer_payprop_entity UNIQUE (payprop_entity_id),
ADD CONSTRAINT uk_customer_payprop_customer UNIQUE (payprop_customer_id);

-- Classify existing records based on position and description fields
-- This helps with the "All tenants shows owners" issue you mentioned

UPDATE customer SET 
    customer_type = 'TENANT',
    is_tenant = TRUE 
WHERE (LOWER(position) LIKE '%tenant%' OR LOWER(description) LIKE '%tenant%')
AND customer_type = 'REGULAR_CUSTOMER';

UPDATE customer SET 
    customer_type = 'PROPERTY_OWNER',
    is_property_owner = TRUE 
WHERE (LOWER(position) LIKE '%owner%' OR LOWER(description) LIKE '%owner%' OR LOWER(position) LIKE '%landlord%')
AND customer_type = 'REGULAR_CUSTOMER';

UPDATE customer SET 
    customer_type = 'CONTRACTOR',
    is_contractor = TRUE 
WHERE (LOWER(position) LIKE '%contractor%' OR LOWER(description) LIKE '%contractor%' 
       OR LOWER(position) LIKE '%maintenance%' OR LOWER(position) LIKE '%repair%')
AND customer_type = 'REGULAR_CUSTOMER';

-- Add a comment to track the migration
ALTER TABLE customer COMMENT = 'Customer table with classification and PayProp integration - Updated V3';

-- Optional: Create a view for easier PayProp entity querying
CREATE OR REPLACE VIEW payprop_entities AS
SELECT 
    customer_id,
    name,
    email,
    customer_type,
    payprop_entity_id,
    payprop_customer_id,
    payprop_synced,
    payprop_last_sync,
    created_at
FROM customer 
WHERE customer_type IN ('TENANT', 'PROPERTY_OWNER')
ORDER BY customer_type, name;
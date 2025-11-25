-- Create table for incoming tenant payments extracted from PayProp
-- This is a PayProp EXPORT table (like payprop_export_properties, payprop_export_tenants)
-- Stores unique incoming tenant→PayProp payments extracted during raw all-payments import

CREATE TABLE payprop_export_incoming_payments (
    -- Primary key
    payprop_id VARCHAR(50) PRIMARY KEY COMMENT 'PayProp incoming transaction ID',

    -- Core transaction data
    amount DECIMAL(10,2) NOT NULL COMMENT 'Full amount tenant paid',
    reconciliation_date DATE NOT NULL COMMENT 'When tenant payment was reconciled in PayProp',
    transaction_type VARCHAR(100) COMMENT 'PayProp transaction type (e.g., "incoming payment")',
    transaction_status VARCHAR(50) COMMENT 'PayProp status (e.g., "paid")',

    -- Tenant information
    tenant_payprop_id VARCHAR(50) COMMENT 'PayProp tenant ID who made payment',
    tenant_name VARCHAR(100) COMMENT 'Tenant name from PayProp',

    -- Property information
    property_payprop_id VARCHAR(50) NOT NULL COMMENT 'PayProp property ID',
    property_name TEXT COMMENT 'Property name from PayProp',

    -- PayProp metadata
    deposit_id VARCHAR(50) COMMENT 'PayProp deposit account ID',

    -- Import tracking
    imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'When this record was imported',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Indexes for performance
    INDEX idx_reconciliation_date (reconciliation_date),
    INDEX idx_property (property_payprop_id),
    INDEX idx_tenant (tenant_payprop_id),
    INDEX idx_imported_at (imported_at),

    -- Foreign keys
    CONSTRAINT fk_incoming_payment_property
        FOREIGN KEY (property_payprop_id)
        REFERENCES payprop_export_properties(payprop_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='PayProp incoming tenant payments - extracted from nested data in all-payments endpoint';

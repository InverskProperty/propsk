-- Create table for extracted incoming tenant payments from PayProp
-- This table stores unique incoming tenant→PayProp payments extracted from payprop_report_all_payments
-- One incoming payment (e.g., £700 rent) appears in multiple allocation records, this deduplicates them

CREATE TABLE payprop_incoming_payments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    -- PayProp identifiers (unique per incoming payment)
    incoming_transaction_id VARCHAR(50) UNIQUE NOT NULL COMMENT 'PayProp transaction ID for tenant payment',

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

    -- Extraction tracking
    extracted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'When this record was extracted from raw all-payments data',
    synced_to_historical BOOLEAN DEFAULT FALSE COMMENT 'Whether this has been imported to historical_transactions',
    historical_transaction_id BIGINT COMMENT 'FK to created historical_transaction record',
    sync_attempted_at TIMESTAMP NULL COMMENT 'Last time sync to historical was attempted',
    sync_error TEXT COMMENT 'Error message if sync failed',

    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Indexes for performance
    INDEX idx_reconciliation_date (reconciliation_date),
    INDEX idx_property (property_payprop_id),
    INDEX idx_tenant (tenant_payprop_id),
    INDEX idx_sync_status (synced_to_historical),
    INDEX idx_extracted_at (extracted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Extracted incoming tenant payments from PayProp - deduplicated from payprop_report_all_payments';

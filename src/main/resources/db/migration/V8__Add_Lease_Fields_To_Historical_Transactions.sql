-- Migration: Add lease-related fields to historical_transactions table
-- Purpose: Enable lease-centric tracking of historical transactions
-- Links transactions to specific lease agreements for arrears tracking and historical analysis

ALTER TABLE historical_transactions
ADD COLUMN invoice_id BIGINT AFTER customer_id,
ADD COLUMN lease_start_date DATE AFTER invoice_id,
ADD COLUMN lease_end_date DATE AFTER lease_start_date,
ADD COLUMN rent_amount_at_transaction DECIMAL(10,2) AFTER lease_end_date;

-- Add foreign key constraint to link transactions to invoices (leases)
ALTER TABLE historical_transactions
ADD CONSTRAINT fk_historical_transactions_invoice
    FOREIGN KEY (invoice_id)
    REFERENCES invoices(id)
    ON DELETE SET NULL;

-- Add indexes for efficient querying
CREATE INDEX idx_historical_transactions_invoice_id ON historical_transactions(invoice_id);
CREATE INDEX idx_historical_transactions_lease_period ON historical_transactions(property_id, lease_start_date, lease_end_date);
CREATE INDEX idx_historical_transactions_date_invoice ON historical_transactions(transaction_date, invoice_id);

-- Add column comments for documentation
ALTER TABLE historical_transactions
MODIFY COLUMN invoice_id BIGINT
COMMENT 'Links to Invoice (lease) record - enables lease-level arrears tracking';

ALTER TABLE historical_transactions
MODIFY COLUMN lease_start_date DATE
COMMENT 'Start date of lease at time of transaction';

ALTER TABLE historical_transactions
MODIFY COLUMN lease_end_date DATE
COMMENT 'End date of lease at time of transaction (NULL = ongoing)';

ALTER TABLE historical_transactions
MODIFY COLUMN rent_amount_at_transaction DECIMAL(10,2)
COMMENT 'Monthly rent amount at time of transaction';

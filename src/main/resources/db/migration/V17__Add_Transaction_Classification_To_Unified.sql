-- V17: Add transaction classification fields to unified_transactions
-- This enables proper categorization of transaction flows (INCOMING vs OUTGOING)
-- and transaction types (incoming_payment, payment_to_beneficiary, etc.)

-- Add transaction_type column
ALTER TABLE unified_transactions 
ADD COLUMN transaction_type VARCHAR(50) NULL
COMMENT 'Transaction type from source (incoming_payment, payment_to_beneficiary, payment_to_agency, expense, commission_payment)';

-- Add flow_direction column
ALTER TABLE unified_transactions 
ADD COLUMN flow_direction ENUM('INCOMING', 'OUTGOING') NULL
COMMENT 'Flow direction: INCOMING = money received (rent), OUTGOING = money paid out (landlord payments, fees, expenses)';

-- Add index for common queries filtering by flow direction
CREATE INDEX idx_flow_direction ON unified_transactions(flow_direction);

-- Add composite index for date + flow queries (common in statements)
CREATE INDEX idx_date_flow ON unified_transactions(transaction_date, flow_direction);

-- Add composite index for invoice + flow queries (lease-specific statements)
CREATE INDEX idx_invoice_flow ON unified_transactions(invoice_id, flow_direction);

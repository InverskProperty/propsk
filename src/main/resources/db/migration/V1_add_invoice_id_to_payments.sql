-- Add invoice_id to payments table to link payments to specific leases
-- This enables proper payment allocation to individual tenancies

ALTER TABLE payments
ADD COLUMN invoice_id BIGINT AFTER property_id,
ADD INDEX idx_payments_invoice_id (invoice_id),
ADD CONSTRAINT fk_payments_invoice
    FOREIGN KEY (invoice_id)
    REFERENCES invoices(id)
    ON DELETE SET NULL;

-- Add comment for clarity
ALTER TABLE payments
MODIFY COLUMN invoice_id BIGINT COMMENT 'Links payment to specific lease (invoice record)';

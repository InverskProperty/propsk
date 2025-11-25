-- Add invoice_id to financial_transactions table to enable lease-based tracking
-- This links PayProp financial transactions to specific lease agreements

ALTER TABLE financial_transactions
ADD COLUMN invoice_id BIGINT;

-- Add foreign key constraint to invoices table
ALTER TABLE financial_transactions
ADD CONSTRAINT fk_financial_transactions_invoice
FOREIGN KEY (invoice_id) REFERENCES invoices(id)
ON DELETE SET NULL;

-- Create index for better query performance
CREATE INDEX idx_financial_transactions_invoice_id ON financial_transactions(invoice_id);

-- Add comment to document the column purpose
COMMENT ON COLUMN financial_transactions.invoice_id IS 'Links transaction to the specific lease (Invoice) for lease-based financial tracking and statement generation';

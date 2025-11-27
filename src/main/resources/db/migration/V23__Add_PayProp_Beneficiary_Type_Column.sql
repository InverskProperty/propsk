-- Add payprop_beneficiary_type column to financial_transactions
-- This column stores the PayProp beneficiary type (agency, beneficiary, global_beneficiary, contractor, etc.)
-- Used to classify payments for expense reporting

ALTER TABLE financial_transactions
ADD COLUMN IF NOT EXISTS payprop_beneficiary_type VARCHAR(50) NULL;

-- Add index for efficient filtering
CREATE INDEX IF NOT EXISTS idx_ft_payprop_beneficiary_type
ON financial_transactions(payprop_beneficiary_type);

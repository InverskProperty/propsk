-- Migration: Add PayProp Financial Tracking Fields
-- Purpose: Track whether properties have financials managed by PayProp based on incoming payments
-- Date: 2025-10-13

-- Step 1: Add boolean and date range columns to properties table
ALTER TABLE properties
ADD COLUMN payprop_manages_financials BOOLEAN DEFAULT FALSE
    COMMENT 'TRUE if PayProp currently or previously managed financials for this property',
ADD COLUMN payprop_financial_from DATE NULL
    COMMENT 'Date when PayProp financial management started (based on first incoming payment)',
ADD COLUMN payprop_financial_to DATE NULL
    COMMENT 'Date when PayProp financial management ended (NULL = ongoing/active)',
ADD COLUMN financial_tracking_manual_override BOOLEAN DEFAULT FALSE
    COMMENT 'TRUE if user manually set the PayProp tracking dates (prevents auto-sync overwrites)';

-- Step 2: Create index for performance on financial tracking queries
CREATE INDEX idx_properties_financial_tracking
ON properties(payprop_manages_financials, financial_tracking_manual_override);

-- Step 3: Populate initial values based on incoming payments with date ranges
-- Properties with ANY incoming payment records are marked as PayProp-managed
-- 'FROM' date set to earliest payment date, 'TO' date left NULL (ongoing)
UPDATE properties p
SET
    p.payprop_manages_financials = TRUE,
    p.payprop_financial_from = (
        SELECT MIN(DATE(prap.payment_date))
        FROM payprop_report_all_payments prap
        WHERE prap.incoming_property_payprop_id = p.payprop_id
    ),
    p.payprop_financial_to = NULL  -- NULL = ongoing/active PayProp management
WHERE p.payprop_id IS NOT NULL
  AND EXISTS (
    SELECT 1
    FROM payprop_report_all_payments prap
    WHERE prap.incoming_property_payprop_id = p.payprop_id
    LIMIT 1
  );

-- Step 4: Verification queries (commented out - run manually to verify)
-- Check distribution of financial tracking status with date ranges
-- SELECT
--     COUNT(*) as total_properties,
--     SUM(CASE WHEN payprop_manages_financials = TRUE THEN 1 ELSE 0 END) as payprop_managed,
--     SUM(CASE WHEN payprop_manages_financials = FALSE THEN 1 ELSE 0 END) as not_managed,
--     SUM(CASE WHEN payprop_financial_to IS NULL AND payprop_manages_financials = TRUE THEN 1 ELSE 0 END) as currently_active,
--     SUM(CASE WHEN payprop_financial_to IS NOT NULL THEN 1 ELSE 0 END) as ended,
--     SUM(CASE WHEN financial_tracking_manual_override = TRUE THEN 1 ELSE 0 END) as manual_overrides
-- FROM properties
-- WHERE payprop_id IS NOT NULL;

-- View properties with their PayProp date ranges and payment counts
-- SELECT
--     p.id,
--     p.property_name,
--     p.payprop_id,
--     p.payprop_manages_financials,
--     p.payprop_financial_from,
--     p.payprop_financial_to,
--     p.financial_tracking_manual_override,
--     COUNT(prap.id) as payment_count,
--     MIN(prap.payment_date) as first_payment,
--     MAX(prap.payment_date) as last_payment
-- FROM properties p
-- LEFT JOIN payprop_report_all_payments prap ON prap.incoming_property_payprop_id = p.payprop_id
-- WHERE p.payprop_id IS NOT NULL
-- GROUP BY p.id, p.property_name, p.payprop_id, p.payprop_manages_financials,
--          p.payprop_financial_from, p.payprop_financial_to, p.financial_tracking_manual_override
-- ORDER BY p.payprop_manages_financials DESC, payment_count DESC;

-- View date range logic example
-- SELECT
--     p.id,
--     p.property_name,
--     p.payprop_financial_from as payprop_from,
--     p.payprop_financial_to as payprop_to,
--     CASE
--         WHEN p.payprop_financial_to IS NULL THEN 'Ongoing'
--         WHEN p.payprop_financial_to < CURDATE() THEN 'Ended'
--         ELSE 'Active'
--     END as status,
--     CASE
--         WHEN p.payprop_manages_financials = FALSE THEN 'Use historical_transactions'
--         WHEN '2023-06-01' >= p.payprop_financial_from AND
--              (p.payprop_financial_to IS NULL OR '2023-06-01' <= p.payprop_financial_to)
--         THEN 'Use PayProp data'
--         ELSE 'Use historical_transactions'
--     END as data_source_for_june_2023
-- FROM properties p
-- WHERE p.payprop_id IS NOT NULL AND p.payprop_manages_financials = TRUE
-- LIMIT 10;

-- Expected results:
-- - ~25 properties with payprop_manages_financials = TRUE (those with incoming payments)
-- - ~25 properties with payprop_financial_to = NULL (ongoing)
-- - ~20 properties with payprop_manages_financials = FALSE (parking, admin, vacant, or new properties)
-- - 0 properties with financial_tracking_manual_override = TRUE (no manual changes yet)

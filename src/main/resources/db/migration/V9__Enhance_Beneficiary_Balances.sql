-- Migration: Enhance beneficiary_balances table for comprehensive balance tracking
-- Purpose: Add entity relationships, period tracking, and detailed balance breakdown
-- Enables automatic balance tracking when rent is split and expenses are recorded

-- ===== ADD NEW COLUMNS =====

-- Entity relationships
ALTER TABLE beneficiary_balances
ADD COLUMN customer_id INT UNSIGNED AFTER id;

-- Balance tracking enhancements
ALTER TABLE beneficiary_balances
MODIFY COLUMN balance_amount DECIMAL(15,2) DEFAULT 0.00;

ALTER TABLE beneficiary_balances
ADD COLUMN opening_balance DECIMAL(15,2) DEFAULT 0.00 AFTER balance_amount;

-- Period tracking
ALTER TABLE beneficiary_balances
ADD COLUMN balance_type VARCHAR(50) DEFAULT 'CURRENT' AFTER balance_date;

ALTER TABLE beneficiary_balances
ADD COLUMN period_start DATE AFTER balance_type;

ALTER TABLE beneficiary_balances
ADD COLUMN period_end DATE AFTER period_start;

-- Period totals
ALTER TABLE beneficiary_balances
ADD COLUMN total_rent_allocated DECIMAL(15,2) DEFAULT 0.00 AFTER period_end;

ALTER TABLE beneficiary_balances
ADD COLUMN total_expenses DECIMAL(15,2) DEFAULT 0.00 AFTER total_rent_allocated;

ALTER TABLE beneficiary_balances
ADD COLUMN total_payments_out DECIMAL(15,2) DEFAULT 0.00 AFTER total_expenses;

-- Additional tracking fields
ALTER TABLE beneficiary_balances
ADD COLUMN description VARCHAR(500) AFTER total_payments_out;

ALTER TABLE beneficiary_balances
ADD COLUMN notes VARCHAR(1000) AFTER description;

-- Status fields
ALTER TABLE beneficiary_balances
ADD COLUMN status VARCHAR(50) DEFAULT 'ACTIVE' AFTER notes;

ALTER TABLE beneficiary_balances
ADD COLUMN is_cleared VARCHAR(1) DEFAULT 'N' AFTER status;

ALTER TABLE beneficiary_balances
ADD COLUMN cleared_date DATE AFTER is_cleared;

-- Audit fields
ALTER TABLE beneficiary_balances
ADD COLUMN last_transaction_id BIGINT AFTER cleared_date;

ALTER TABLE beneficiary_balances
ADD COLUMN created_at DATETIME DEFAULT CURRENT_TIMESTAMP AFTER last_transaction_id;

ALTER TABLE beneficiary_balances
ADD COLUMN updated_by BIGINT AFTER last_updated;

-- PayProp integration
ALTER TABLE beneficiary_balances
ADD COLUMN payprop_beneficiary_id VARCHAR(50) AFTER updated_by;

ALTER TABLE beneficiary_balances
ADD COLUMN sync_status VARCHAR(20) DEFAULT 'active' AFTER payprop_beneficiary_id;

-- ===== ADD FOREIGN KEY CONSTRAINTS =====

-- Link to customers table (ignore if already exists)
ALTER TABLE beneficiary_balances
ADD CONSTRAINT fk_beneficiary_balances_customer
    FOREIGN KEY (customer_id)
    REFERENCES customers(customer_id)
    ON DELETE CASCADE;

-- Note: Foreign key constraints might fail if they already exist
-- This is expected and can be ignored

-- ===== ADD INDEXES FOR PERFORMANCE =====

CREATE INDEX idx_beneficiary_balances_customer ON beneficiary_balances(customer_id);
CREATE INDEX idx_beneficiary_balances_property ON beneficiary_balances(property_id);
CREATE INDEX idx_beneficiary_balances_date ON beneficiary_balances(balance_date);
CREATE INDEX idx_beneficiary_balances_status ON beneficiary_balances(status, is_cleared);
CREATE INDEX idx_beneficiary_balances_period ON beneficiary_balances(period_start, period_end);
CREATE INDEX idx_beneficiary_balances_amount ON beneficiary_balances(balance_amount);
CREATE INDEX idx_beneficiary_balances_payprop ON beneficiary_balances(payprop_beneficiary_id);

-- Composite indexes for common queries
CREATE INDEX idx_beneficiary_balances_customer_property ON beneficiary_balances(customer_id, property_id);
CREATE INDEX idx_beneficiary_balances_customer_date ON beneficiary_balances(customer_id, balance_date);
CREATE INDEX idx_beneficiary_balances_property_date ON beneficiary_balances(property_id, balance_date);

-- ===== DATA MIGRATION =====
-- Migrate existing beneficiary_id to customer_id (if beneficiary_id links to customer_id)

UPDATE beneficiary_balances bb
LEFT JOIN customers c ON bb.beneficiary_id = c.customer_id
SET bb.customer_id = c.customer_id
WHERE bb.beneficiary_id IS NOT NULL AND bb.customer_id IS NULL;

-- Set default values for existing records
UPDATE beneficiary_balances
SET
    opening_balance = 0.00,
    balance_type = 'CURRENT',
    status = 'ACTIVE',
    is_cleared = 'N',
    created_at = COALESCE(last_updated, NOW()),
    sync_status = 'active'
WHERE opening_balance IS NULL OR status IS NULL;

-- Migration complete
-- Table now tracks comprehensive balance information including:
-- - Entity relationships (customer, property)
-- - Period tracking (start/end dates)
-- - Detailed totals (rent, expenses, payments)
-- - Status tracking (active, cleared)
-- - PayProp integration fields

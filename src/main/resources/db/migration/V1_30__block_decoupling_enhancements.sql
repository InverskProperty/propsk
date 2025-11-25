-- ====================================================================
-- V1_30: Block System Decoupling & Financial Enhancements
-- ====================================================================
-- Purpose:
--   1. Decouple blocks from portfolios (many-to-many relationship)
--   2. Separate property-block assignments from portfolio assignments
--   3. Add block property support (blocks as financial entities)
--   4. Add block_id to transactions for block-level financials
-- ====================================================================

-- ====================================================================
-- PART 1: Block-Portfolio Many-to-Many Relationship
-- ====================================================================

-- Create junction table for block-portfolio assignments
CREATE TABLE IF NOT EXISTS block_portfolio_assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    block_id BIGINT NOT NULL,
    portfolio_id BIGINT NOT NULL,
    assignment_type ENUM('PRIMARY', 'SHARED') DEFAULT 'PRIMARY' NOT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by BIGINT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    notes VARCHAR(500),
    display_order INT DEFAULT 0,

    FOREIGN KEY (block_id) REFERENCES blocks(id) ON DELETE CASCADE,
    FOREIGN KEY (portfolio_id) REFERENCES portfolios(id) ON DELETE CASCADE,

    UNIQUE KEY unique_block_portfolio_assignment (block_id, portfolio_id, assignment_type),
    INDEX idx_block_id (block_id),
    INDEX idx_portfolio_id (portfolio_id),
    INDEX idx_is_active (is_active),
    INDEX idx_assignment_type (assignment_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Migrate existing block-portfolio relationships from blocks.portfolio_id
INSERT INTO block_portfolio_assignments (block_id, portfolio_id, assignment_type, assigned_at, is_active)
SELECT
    id,
    portfolio_id,
    'PRIMARY',
    COALESCE(created_at, NOW()),
    IF(is_active = 'Y', TRUE, FALSE)
FROM blocks
WHERE portfolio_id IS NOT NULL
ON DUPLICATE KEY UPDATE assigned_at = VALUES(assigned_at);

-- NOTE: Keep blocks.portfolio_id column for now (backward compatibility)
-- Will deprecate after validation period


-- ====================================================================
-- PART 2: Property-Block Independent Assignments
-- ====================================================================

-- Create junction table for property-block assignments (independent of portfolio)
CREATE TABLE IF NOT EXISTS property_block_assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    property_id BIGINT NOT NULL,
    block_id BIGINT NOT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by BIGINT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    notes VARCHAR(500),

    FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE CASCADE,
    FOREIGN KEY (block_id) REFERENCES blocks(id) ON DELETE CASCADE,

    UNIQUE KEY unique_property_block (property_id, block_id),
    INDEX idx_property_id (property_id),
    INDEX idx_block_id (block_id),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Migrate existing property-block relationships from property_portfolio_assignments
INSERT INTO property_block_assignments (property_id, block_id, assigned_at, assigned_by, is_active)
SELECT
    property_id,
    block_id,
    assigned_at,
    assigned_by,
    is_active
FROM property_portfolio_assignments
WHERE block_id IS NOT NULL
ON DUPLICATE KEY UPDATE assigned_at = VALUES(assigned_at);

-- NOTE: Keep property_portfolio_assignments.block_id column for now (backward compatibility)
-- Will deprecate after validation period


-- ====================================================================
-- PART 3: Block Property (Block as Financial Entity)
-- ====================================================================

-- Add block_property_id to blocks table
ALTER TABLE blocks
ADD COLUMN block_property_id BIGINT DEFAULT NULL;

ALTER TABLE blocks
ADD INDEX idx_block_property_id (block_property_id);

ALTER TABLE blocks
ADD CONSTRAINT fk_blocks_block_property
    FOREIGN KEY (block_property_id) REFERENCES properties(id) ON DELETE SET NULL;

-- Add is_block_property flag to properties table
ALTER TABLE properties
ADD COLUMN is_block_property BOOLEAN DEFAULT FALSE;

ALTER TABLE properties
ADD INDEX idx_is_block_property (is_block_property);

-- Add block-level financial fields to blocks table
ALTER TABLE blocks
ADD COLUMN annual_service_charge DECIMAL(15, 2) DEFAULT NULL COMMENT 'Total annual service charge for the block',
ADD COLUMN service_charge_frequency ENUM('MONTHLY', 'QUARTERLY', 'ANNUAL') DEFAULT 'ANNUAL' COMMENT 'Service charge billing frequency',
ADD COLUMN ground_rent_annual DECIMAL(15, 2) DEFAULT NULL COMMENT 'Annual ground rent for the block',
ADD COLUMN insurance_annual DECIMAL(15, 2) DEFAULT NULL COMMENT 'Annual building insurance cost',
ADD COLUMN reserve_fund_contribution DECIMAL(15, 2) DEFAULT NULL COMMENT 'Annual reserve fund contribution',
ADD COLUMN allocation_method ENUM('EQUAL', 'BY_SQFT', 'BY_BEDROOMS', 'CUSTOM') DEFAULT 'EQUAL' COMMENT 'How service charges are distributed',
ADD COLUMN service_charge_account VARCHAR(255) DEFAULT NULL COMMENT 'Dedicated service charge bank account';


-- ====================================================================
-- PART 4: Block-Level Transaction Tracking
-- ====================================================================

-- Add block_id to historical_transactions for block-level financials
ALTER TABLE historical_transactions
ADD COLUMN block_id BIGINT DEFAULT NULL;

ALTER TABLE historical_transactions
ADD INDEX idx_block_id (block_id);

ALTER TABLE historical_transactions
ADD CONSTRAINT fk_historical_transactions_block
    FOREIGN KEY (block_id) REFERENCES blocks(id) ON DELETE SET NULL;


-- ====================================================================
-- PART 5: Block Service Charge Distribution Table
-- ====================================================================

-- Table to track how service charges are distributed across apartments
CREATE TABLE IF NOT EXISTS block_service_charge_distributions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    block_id BIGINT NOT NULL,
    property_id BIGINT NOT NULL,
    annual_charge DECIMAL(15, 2) NOT NULL COMMENT 'Annual service charge for this property',
    percentage DECIMAL(5, 2) DEFAULT NULL COMMENT 'Percentage of total block charges',
    allocation_method ENUM('EQUAL', 'BY_SQFT', 'BY_BEDROOMS', 'CUSTOM') NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    notes VARCHAR(500),

    FOREIGN KEY (block_id) REFERENCES blocks(id) ON DELETE CASCADE,
    FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE CASCADE,

    INDEX idx_block_id (block_id),
    INDEX idx_property_id (property_id),
    INDEX idx_effective_dates (effective_from, effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ====================================================================
-- PART 6: Block Expenses Table
-- ====================================================================

-- Table to track block-level expenses
CREATE TABLE IF NOT EXISTS block_expenses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    block_id BIGINT NOT NULL,
    description VARCHAR(255) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    expense_category ENUM('MAINTENANCE', 'CLEANING', 'INSURANCE', 'MANAGEMENT', 'UTILITIES', 'REPAIRS', 'SECURITY', 'LANDSCAPING', 'OTHER') NOT NULL,
    expense_date DATE NOT NULL,
    payment_source_id BIGINT DEFAULT NULL,
    is_recoverable BOOLEAN DEFAULT TRUE COMMENT 'Can this be recharged to tenants via service charges',
    invoice_reference VARCHAR(100) DEFAULT NULL,
    paid BOOLEAN DEFAULT FALSE,
    paid_date DATE DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    notes TEXT,

    FOREIGN KEY (block_id) REFERENCES blocks(id) ON DELETE CASCADE,
    FOREIGN KEY (payment_source_id) REFERENCES payment_sources(id) ON DELETE SET NULL,

    INDEX idx_block_id (block_id),
    INDEX idx_expense_date (expense_date),
    INDEX idx_expense_category (expense_category),
    INDEX idx_paid (paid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ====================================================================
-- PART 7: Validation & Audit
-- ====================================================================

-- Check data integrity
SELECT
    'Blocks migrated to junction table' AS check_name,
    COUNT(*) AS count
FROM block_portfolio_assignments;

SELECT
    'Property-block assignments migrated' AS check_name,
    COUNT(*) AS count
FROM property_block_assignments;

-- Migration completed successfully
-- V1_30: Block decoupling migration completed - blocks now support many-to-many portfolios and financial tracking

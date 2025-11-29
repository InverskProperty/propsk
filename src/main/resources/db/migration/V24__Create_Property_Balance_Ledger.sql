-- V24: Create Property Balance Ledger
-- Tracks all balance movements for properties (deposits, withdrawals, transfers, adjustments)
-- Provides audit trail for Property.account_balance changes

CREATE TABLE IF NOT EXISTS property_balance_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Which property
    property_id BIGINT NOT NULL,
    property_name VARCHAR(255),

    -- Owner (for filtering/reporting)
    owner_id BIGINT,
    owner_name VARCHAR(255),

    -- What type of movement
    entry_type ENUM('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT', 'ADJUSTMENT', 'OPENING_BALANCE') NOT NULL,
    amount DECIMAL(15,2) NOT NULL,              -- Always positive
    running_balance DECIMAL(15,2) NOT NULL,     -- Balance after this entry

    -- Why
    description VARCHAR(500),
    notes TEXT,

    -- Links
    payment_batch_id VARCHAR(50),               -- Links to payment_batches.batch_id
    reference VARCHAR(100),                     -- External reference (bank ref)

    -- Transfer support (for block property transfers)
    related_property_id BIGINT,                 -- Source/destination property
    related_property_name VARCHAR(255),

    -- Source
    source ENUM('PAYMENT_BATCH', 'BLOCK_TRANSFER', 'MANUAL', 'IMPORT', 'PAYPROP_SYNC', 'HISTORICAL_RECON'),

    -- Audit
    entry_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,

    -- Indexes
    INDEX idx_pbl_property (property_id),
    INDEX idx_pbl_owner (owner_id),
    INDEX idx_pbl_batch (payment_batch_id),
    INDEX idx_pbl_date (entry_date),
    INDEX idx_pbl_related_property (related_property_id),
    INDEX idx_pbl_entry_type (entry_type),
    INDEX idx_pbl_source (source),

    -- Foreign keys (commented out for flexibility, uncomment if strict referential integrity needed)
    -- FOREIGN KEY (property_id) REFERENCES properties(property_id),
    -- FOREIGN KEY (owner_id) REFERENCES customers(customer_id),
    -- FOREIGN KEY (related_property_id) REFERENCES properties(property_id),
    CONSTRAINT fk_pbl_property FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE,
    CONSTRAINT fk_pbl_related_property FOREIGN KEY (related_property_id) REFERENCES properties(property_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Audit trail for property balance movements';

-- Add composite index for common queries
CREATE INDEX idx_pbl_property_date ON property_balance_ledger (property_id, entry_date DESC);
CREATE INDEX idx_pbl_owner_date ON property_balance_ledger (owner_id, entry_date DESC);

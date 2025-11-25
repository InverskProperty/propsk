-- V5: Create Payment Source Management Tables

-- Create payment_sources table for managing isolated transaction data sources
CREATE TABLE payment_sources (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    source_type VARCHAR(50) COMMENT 'e.g., BANK_STATEMENT, OLD_ACCOUNT, PAYPROP, etc.',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id INT,
    last_import_date TIMESTAMP NULL,
    total_transactions INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,

    CONSTRAINT fk_payment_source_user FOREIGN KEY (created_by_user_id) REFERENCES users(id)
);

-- Create transaction_import_staging table for multi-paste batch accumulation
CREATE TABLE transaction_import_staging (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id VARCHAR(100) NOT NULL,
    payment_source_id BIGINT,
    line_number INT,
    csv_line TEXT,

    -- Parsed transaction fields
    transaction_date DATE,
    amount DECIMAL(12,2),
    description TEXT,
    transaction_type VARCHAR(50),
    category VARCHAR(100),
    bank_reference VARCHAR(255),
    payment_method VARCHAR(100),
    notes TEXT,

    -- Matched entities
    property_id BIGINT,
    customer_id INT UNSIGNED,

    -- Review status
    status VARCHAR(50) COMMENT 'PENDING_REVIEW, APPROVED, REJECTED, AMBIGUOUS_PROPERTY, AMBIGUOUS_CUSTOMER, DUPLICATE',
    is_duplicate BOOLEAN DEFAULT FALSE,
    duplicate_of_transaction_id BIGINT COMMENT 'ID of existing historical_transaction this duplicates',
    user_note TEXT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_staging_payment_source FOREIGN KEY (payment_source_id) REFERENCES payment_sources(id),
    CONSTRAINT fk_staging_property FOREIGN KEY (property_id) REFERENCES properties(id),
    CONSTRAINT fk_staging_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    CONSTRAINT fk_staging_duplicate_of FOREIGN KEY (duplicate_of_transaction_id) REFERENCES historical_transactions(id),

    INDEX idx_batch_id (batch_id),
    INDEX idx_payment_source (payment_source_id),
    INDEX idx_status (status),
    INDEX idx_transaction_date (transaction_date)
);

-- Add payment_source_id to historical_transactions for source tracking
ALTER TABLE historical_transactions
ADD COLUMN payment_source_id BIGINT AFTER account_source,
ADD COLUMN import_staging_id BIGINT AFTER payment_source_id,
ADD CONSTRAINT fk_historical_payment_source FOREIGN KEY (payment_source_id) REFERENCES payment_sources(id),
ADD CONSTRAINT fk_historical_staging FOREIGN KEY (import_staging_id) REFERENCES transaction_import_staging(id),
ADD INDEX idx_payment_source_id (payment_source_id);

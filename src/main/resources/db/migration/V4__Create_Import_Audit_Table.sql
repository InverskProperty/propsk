-- Create import_audit table for tracking transaction imports
CREATE TABLE IF NOT EXISTS import_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id VARCHAR(100) NOT NULL,
    import_type VARCHAR(50) COMMENT 'CSV, JSON, MANUAL',
    total_rows INT,
    imported_rows INT,
    skipped_rows INT,
    error_rows INT,
    user_id BIGINT,
    user_name VARCHAR(255),
    imported_at DATETIME,
    review_notes TEXT COMMENT 'User notes from review process',
    verification_status VARCHAR(50) DEFAULT 'PENDING' COMMENT 'PENDING, REVIEWED, AUTO_IMPORTED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_batch_id (batch_id),
    INDEX idx_user_id (user_id),
    INDEX idx_imported_at (imported_at),
    INDEX idx_verification_status (verification_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Audit trail for transaction imports with human verification tracking';

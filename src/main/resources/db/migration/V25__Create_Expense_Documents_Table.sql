-- V25: Create expense_documents table for managing expense invoices and receipts
-- This table links expense transactions to their supporting documents

CREATE TABLE IF NOT EXISTS expense_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Transaction links (at least one should be set)
    unified_transaction_id BIGINT,
    financial_transaction_id BIGINT,
    historical_transaction_id BIGINT,

    -- Document references
    google_drive_file_id INT,                    -- For uploaded receipts (links to google_drive_file table)
    generated_document_path VARCHAR(500),         -- For generated invoices (local path reference)
    generated_drive_file_id VARCHAR(100),         -- For generated invoices stored in Drive

    -- Document metadata
    document_type ENUM('INVOICE', 'RECEIPT', 'QUOTE', 'CREDIT_NOTE', 'VENDOR_INVOICE', 'OTHER') NOT NULL DEFAULT 'INVOICE',
    status ENUM('PENDING', 'AVAILABLE', 'ARCHIVED', 'ERROR') DEFAULT 'AVAILABLE',
    document_number VARCHAR(100),
    document_description VARCHAR(500),
    vendor_name VARCHAR(255),

    -- Property context
    property_id BIGINT,
    customer_id INT,

    -- Audit fields
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by INT,

    -- Indexes
    INDEX idx_expense_docs_unified_txn (unified_transaction_id),
    INDEX idx_expense_docs_financial_txn (financial_transaction_id),
    INDEX idx_expense_docs_historical_txn (historical_transaction_id),
    INDEX idx_expense_docs_property (property_id),
    INDEX idx_expense_docs_customer (customer_id),
    INDEX idx_expense_docs_type (document_type),
    INDEX idx_expense_docs_status (status),
    INDEX idx_expense_docs_drive_file (google_drive_file_id),

    -- Foreign keys
    CONSTRAINT fk_expense_docs_unified_txn
        FOREIGN KEY (unified_transaction_id) REFERENCES unified_transactions(id) ON DELETE SET NULL,
    CONSTRAINT fk_expense_docs_property
        FOREIGN KEY (property_id) REFERENCES property(id) ON DELETE SET NULL,
    CONSTRAINT fk_expense_docs_drive_file
        FOREIGN KEY (google_drive_file_id) REFERENCES google_drive_file(id) ON DELETE SET NULL
);

-- Add comment to table
ALTER TABLE expense_documents COMMENT = 'Links expense transactions to invoices and receipts for property owner portal';

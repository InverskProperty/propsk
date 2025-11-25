-- Create tenant_balances table to track lease-level arrears
-- Each record represents the financial state of a specific lease for a specific period

CREATE TABLE tenant_balances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Lease identification (links to invoice which represents the lease)
    invoice_id BIGINT NOT NULL COMMENT 'Links to invoice (lease) record',
    tenant_id VARCHAR(100) NOT NULL COMMENT 'Tenant PayProp ID',
    property_id VARCHAR(100) NOT NULL COMMENT 'Property PayProp ID',

    -- Period tracking
    statement_period DATE NOT NULL COMMENT 'Period for this balance calculation (YYYY-MM-01)',

    -- Financial tracking
    previous_balance DECIMAL(10,2) DEFAULT 0.00 COMMENT 'Balance brought forward from previous period',
    rent_due DECIMAL(10,2) DEFAULT 0.00 COMMENT 'Rent due for this period',
    rent_received DECIMAL(10,2) DEFAULT 0.00 COMMENT 'Rent payments received this period',
    charges DECIMAL(10,2) DEFAULT 0.00 COMMENT 'Additional charges (late fees, damages)',
    payments DECIMAL(10,2) DEFAULT 0.00 COMMENT 'Other payments/credits',
    current_balance DECIMAL(10,2) DEFAULT 0.00 COMMENT 'Current balance (positive = arrears)',
    running_balance DECIMAL(10,2) DEFAULT 0.00 COMMENT 'Running balance including history',

    -- Audit tracking
    created_date DATE NOT NULL DEFAULT (CURRENT_DATE),
    updated_date DATE NULL,

    -- Indexes for performance
    INDEX idx_tb_invoice_id (invoice_id),
    INDEX idx_tb_tenant_id (tenant_id),
    INDEX idx_tb_property_id (property_id),
    INDEX idx_tb_statement_period (statement_period),
    INDEX idx_tb_tenant_period (tenant_id, statement_period),
    INDEX idx_tb_invoice_period (invoice_id, statement_period),

    -- Foreign key to invoices (leases)
    CONSTRAINT fk_tb_invoice
        FOREIGN KEY (invoice_id)
        REFERENCES invoices(id)
        ON DELETE CASCADE,

    -- Unique constraint: one balance record per lease per period
    UNIQUE KEY uk_invoice_period (invoice_id, statement_period)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Tracks tenant balances per lease per period for arrears management';

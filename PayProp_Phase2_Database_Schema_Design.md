# PayProp Phase 2 - Database Schema Design
*Raw Mirror Tables + Business Logic Layer Architecture*

## 🎯 CURRENT DATABASE ANALYSIS

### **Existing PayProp Tables (Current System)**
Your database already has some PayProp raw storage:

✅ **Good Foundation:**
- `payprop_categories` - Invoice/payment categories
- `payprop_invoice_rules` - Some invoice rule storage
- `payprop_payment_rules` - Payment distribution rules  
- `payprop_oauth_tokens` - Authentication
- `payprop_webhook_log` - Webhook handling

⚠️ **Current Issues:**
- **No raw endpoint mirrors** - tables don't match API responses exactly
- **Mixed business logic** - rules processing mixed with raw data
- **Missing critical endpoints** - no `/export/invoices`, `/report/all-payments` raw storage

### **Current Business Tables**
✅ **Working Business Layer:**
- `properties` - Has both `monthly_payment` (£995) and `monthly_payment_required` (empty)
- `financial_transactions` - Mixed PayProp data with business processing
- `beneficiaries` - Business entities for payment recipients
- `batch_payments` - PayProp batch processing tracking

### **The £995 vs £1,075 Problem in Current Schema**
```sql
-- Current Property table (working but incomplete):
properties.monthly_payment           = £995  -- From settings.monthly_payment
properties.monthly_payment_required  = NULL -- Broken sync attempt

-- Missing: Raw PayProp invoice instructions table
-- Missing: Raw PayProp properties settings table
```

---

## 🏗️ PHASE 2 SCHEMA DESIGN

### **Design Principles**
1. **Raw Mirror Tables** - Exact PayProp API response structure
2. **Business Logic Separation** - Clean decision layer on top of raw data
3. **Zero Data Loss** - Store every field PayProp returns
4. **Future-Proof** - Easy to add new PayProp fields

---

## 📋 RAW PAYPROP MIRROR TABLES

### **1. payprop_export_properties**
*Mirrors `/export/properties` exactly*

```sql
CREATE TABLE payprop_export_properties (
    -- PayProp Primary Key
    payprop_id VARCHAR(50) PRIMARY KEY,
    
    -- Root Properties
    name TEXT,
    description TEXT,
    create_date TIMESTAMP,
    modify_date TIMESTAMP,
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    property_image TEXT,
    
    -- Address Object (Flattened)
    address_id VARCHAR(50),
    address_first_line VARCHAR(100),
    address_second_line VARCHAR(100),
    address_third_line VARCHAR(100),
    address_city VARCHAR(50),
    address_state VARCHAR(50),
    address_country_code VARCHAR(2),
    address_postal_code VARCHAR(10),
    address_zip_code VARCHAR(10),
    address_latitude DECIMAL(10,8),
    address_longitude DECIMAL(11,8),
    address_phone VARCHAR(20),
    address_fax VARCHAR(20),
    address_email VARCHAR(100),
    address_created TIMESTAMP,
    address_modified TIMESTAMP,
    
    -- Settings Object (CRITICAL - Source of £995!)
    settings_monthly_payment DECIMAL(10,2),           -- £995 - Current database source!
    settings_enable_payments BOOLEAN,
    settings_hold_owner_funds BOOLEAN,
    settings_verify_payments BOOLEAN,
    settings_minimum_balance DECIMAL(10,2),
    settings_listing_from DATE,
    settings_approval_required BOOLEAN,
    
    -- Commission Object (include_commission=true)
    commission_percentage DECIMAL(5,2),
    commission_amount DECIMAL(10,2),
    commission_id VARCHAR(50),
    
    -- Metadata
    imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP,
    sync_status ENUM('active', 'archived', 'error') DEFAULT 'active',
    
    -- Indexes
    INDEX idx_imported_at (imported_at),
    INDEX idx_settings_monthly_payment (settings_monthly_payment)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### **2. payprop_export_invoices**
*Mirrors `/export/invoices` exactly - THE MISSING £1,075 DATA!*

```sql
CREATE TABLE payprop_export_invoices (
    -- PayProp Primary Key
    payprop_id VARCHAR(50) PRIMARY KEY,
    
    -- Invoice Instruction Details
    account_type VARCHAR(50),
    debit_order BOOLEAN,
    description TEXT,
    frequency VARCHAR(20),
    frequency_code VARCHAR(1),
    from_date DATE,
    to_date DATE,
    gross_amount DECIMAL(10,2) NOT NULL,           -- £1,075 - THE AUTHORITATIVE RENT AMOUNT!
    payment_day INTEGER,
    invoice_type VARCHAR(50),
    reference TEXT,
    vat BOOLEAN,
    vat_amount DECIMAL(10,2),
    
    -- Foreign Key Relationships (DO NOT DUPLICATE DATA)
    property_payprop_id VARCHAR(50) NOT NULL,      -- Links to payprop_export_properties
    tenant_payprop_id VARCHAR(50),                 -- Links to payprop_export_tenants  
    category_payprop_id VARCHAR(50),               -- Links to payprop_invoice_categories
    
    -- Display Fields Only (from nested objects)
    property_name TEXT,                            -- For display - don't use for business logic
    tenant_display_name VARCHAR(100),             -- For display - don't use for business logic
    tenant_email VARCHAR(100),                    -- For display - don't use for business logic
    tenant_business_name VARCHAR(100),            -- For business tenants
    tenant_first_name VARCHAR(50),
    tenant_last_name VARCHAR(50),
    category_name VARCHAR(100),                   -- For display - don't use for business logic
    
    -- Metadata
    imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP,
    sync_status ENUM('active', 'archived', 'error') DEFAULT 'active',
    is_active_instruction BOOLEAN,                -- Derived field
    
    -- Foreign Key Constraints
    FOREIGN KEY (property_payprop_id) REFERENCES payprop_export_properties(payprop_id),
    
    -- Indexes
    INDEX idx_property_id (property_payprop_id),
    INDEX idx_tenant_id (tenant_payprop_id),
    INDEX idx_gross_amount (gross_amount),
    INDEX idx_payment_day (payment_day),
    INDEX idx_frequency_code (frequency_code),
    INDEX idx_active_instruction (is_active_instruction)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### **3. payprop_report_all_payments**
*Mirrors `/report/all-payments` exactly*

```sql
CREATE TABLE payprop_report_all_payments (
    -- PayProp Primary Key
    payprop_id VARCHAR(50) PRIMARY KEY,
    
    -- Payment Transaction Details
    amount DECIMAL(10,2),
    description TEXT,
    due_date DATE,
    has_tax BOOLEAN,
    reference VARCHAR(100),
    service_fee DECIMAL(10,2),                    -- PayProp service charges
    transaction_fee DECIMAL(10,2),               -- Processing fees
    tax_amount DECIMAL(10,2),                    -- VAT/tax
    part_of_amount DECIMAL(10,2),
    
    -- Beneficiary Details (Flattened)
    beneficiary_payprop_id VARCHAR(50),
    beneficiary_name VARCHAR(100),
    beneficiary_type VARCHAR(50),                -- 'agency', 'property_owner', etc.
    
    -- Category Details (Flattened)
    category_payprop_id VARCHAR(50),
    category_name VARCHAR(100),                  -- 'Commission', 'Rent', etc.
    
    -- Incoming Transaction (Original Payment)
    incoming_transaction_id VARCHAR(50),
    incoming_transaction_amount DECIMAL(10,2),   -- Original rent payment amount
    incoming_transaction_deposit_id VARCHAR(50),
    incoming_transaction_reconciliation_date DATE,
    incoming_transaction_status VARCHAR(50),
    incoming_transaction_type VARCHAR(100),      -- 'instant bank transfer', etc.
    
    -- Bank Statement Details
    bank_statement_date DATE,
    bank_statement_id VARCHAR(50),
    
    -- Property/Tenant References (from incoming transaction)
    incoming_property_payprop_id VARCHAR(50),
    incoming_property_name TEXT,
    incoming_tenant_payprop_id VARCHAR(50),
    incoming_tenant_name VARCHAR(100),
    
    -- Payment Batch Details (Flattened)
    payment_batch_id VARCHAR(50),
    payment_batch_amount DECIMAL(10,2),
    payment_batch_status VARCHAR(50),            -- 'not approved', 'approved', 'paid'
    payment_batch_transfer_date DATE,
    
    -- CRITICAL RELATIONSHIP - Links to Stage 1
    payment_instruction_id VARCHAR(50),          -- Links to payprop_export_invoices!
    
    -- Secondary Payment Structure
    secondary_payment_is_child BOOLEAN,
    secondary_payment_is_parent BOOLEAN,
    secondary_payment_parent_id VARCHAR(50),
    
    -- Metadata
    imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reconciliation_date DATE,
    sync_status ENUM('active', 'processed', 'error') DEFAULT 'active',
    
    -- Foreign Key Constraints
    FOREIGN KEY (payment_instruction_id) REFERENCES payprop_export_invoices(payprop_id),
    FOREIGN KEY (incoming_property_payprop_id) REFERENCES payprop_export_properties(payprop_id),
    
    -- Indexes
    INDEX idx_payment_instruction_id (payment_instruction_id),
    INDEX idx_incoming_property_id (incoming_property_payprop_id),
    INDEX idx_incoming_tenant_id (incoming_tenant_payprop_id),
    INDEX idx_reconciliation_date (reconciliation_date),
    INDEX idx_amount (amount),
    INDEX idx_incoming_amount (incoming_transaction_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### **4. payprop_export_payments**
*Mirrors `/export/payments` exactly*

```sql
CREATE TABLE payprop_export_payments (
    -- PayProp Primary Key
    payprop_id VARCHAR(50) PRIMARY KEY,
    
    -- Payment Distribution Rule Details
    beneficiary VARCHAR(200),                    -- "Natalie Turner [B]"
    beneficiary_reference VARCHAR(100),         -- Bank reference
    category VARCHAR(100),                       -- "Owner", "Agent", "Contractor"
    category_payprop_id VARCHAR(50),
    description TEXT,
    enabled BOOLEAN,
    frequency VARCHAR(20),
    frequency_code VARCHAR(1),
    from_date DATE,
    to_date DATE,
    gross_amount DECIMAL(10,2),                 -- Fixed amount (usually 0)
    gross_percentage DECIMAL(5,2),             -- Percentage of payment
    group_id VARCHAR(50),
    maintenance_ticket_id VARCHAR(50),
    no_commission BOOLEAN,                      -- Exempt from commission
    no_commission_amount DECIMAL(10,2),
    payment_day INTEGER,
    reference TEXT,
    vat BOOLEAN,
    vat_amount DECIMAL(10,2),
    
    -- Foreign Key Relationships
    property_payprop_id VARCHAR(50) NOT NULL,   -- Links to payprop_export_properties
    tenant_payprop_id VARCHAR(50),              -- Links to payprop_export_tenants (optional)
    
    -- Display Fields Only
    property_name TEXT,                         -- For display only
    tenant_name VARCHAR(100),                   -- For display only
    
    -- Metadata
    imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP,
    sync_status ENUM('active', 'archived', 'error') DEFAULT 'active',
    rule_priority INTEGER,                      -- Processing order
    
    -- Foreign Key Constraints
    FOREIGN KEY (property_payprop_id) REFERENCES payprop_export_properties(payprop_id),
    
    -- Indexes
    INDEX idx_property_id (property_payprop_id),
    INDEX idx_tenant_id (tenant_payprop_id),
    INDEX idx_enabled (enabled),
    INDEX idx_gross_percentage (gross_percentage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### **5. payprop_export_beneficiaries**
*Mirrors `/export/beneficiaries` exactly - STRUCTURE NEEDED*

```sql
-- PLACEHOLDER - STRUCTURE TO BE DOCUMENTED
CREATE TABLE payprop_export_beneficiaries (
    payprop_id VARCHAR(50) PRIMARY KEY,
    -- TODO: Complete structure from API call
    imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sync_status ENUM('active', 'archived', 'error') DEFAULT 'active'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### **6. payprop_export_tenants**
*Mirrors `/export/tenants` exactly - STRUCTURE NEEDED*

```sql  
-- PLACEHOLDER - STRUCTURE TO BE DOCUMENTED
CREATE TABLE payprop_export_tenants (
    payprop_id VARCHAR(50) PRIMARY KEY,
    -- TODO: Complete structure from API call
    imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sync_status ENUM('active', 'archived', 'error') DEFAULT 'active'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### **7. payprop_invoice_categories**
*Mirrors `/invoices/categories` exactly*

```sql
-- UPGRADE EXISTING TABLE
ALTER TABLE payprop_categories 
ADD COLUMN is_system BOOLEAN DEFAULT FALSE,
ADD COLUMN imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
```

---

## 🧠 BUSINESS LOGIC LAYER TABLES

### **1. property_rent_sources**
*Business decisions about rent amounts*

```sql
CREATE TABLE property_rent_sources (
    property_id BIGINT PRIMARY KEY,
    
    -- Multiple Rent Amount Sources
    settings_rent_amount DECIMAL(10,2),          -- £995 from payprop_export_properties
    invoice_instruction_amount DECIMAL(10,2),    -- £1,075 from payprop_export_invoices
    average_actual_payment DECIMAL(10,2),        -- Calculated from payments
    
    -- Business Decision
    authoritative_rent_source ENUM('settings', 'invoice', 'actual', 'manual') DEFAULT 'invoice',
    current_rent_amount DECIMAL(10,2),           -- THE chosen authoritative amount
    
    -- Variance Tracking
    settings_vs_invoice_variance DECIMAL(10,2),  -- £1,075 - £995 = £80
    variance_explanation TEXT,                   -- "Settings outdated - using invoice"
    variance_threshold DECIMAL(10,2) DEFAULT 50.00, -- Flag variances > £50
    
    -- Metadata
    last_calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    calculation_method VARCHAR(100),             -- How current_rent_amount was determined
    data_quality_score DECIMAL(3,2),            -- 0-1 confidence score
    
    -- Foreign Key
    FOREIGN KEY (property_id) REFERENCES properties(id),
    
    -- Indexes
    INDEX idx_variance (settings_vs_invoice_variance),
    INDEX idx_authoritative_source (authoritative_rent_source),
    INDEX idx_current_rent_amount (current_rent_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### **2. payment_lifecycle_links**  
*Links all 3 stages of PayProp payment processing*

```sql
CREATE TABLE payment_lifecycle_links (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    
    -- Stage Linking
    invoice_instruction_id VARCHAR(50),          -- Stage 1: What should happen
    payment_transaction_id VARCHAR(50),          -- Stage 2: What actually happened  
    distribution_rule_id VARCHAR(50),            -- Stage 3: How it's distributed
    
    -- Property Context
    property_payprop_id VARCHAR(50) NOT NULL,
    property_id BIGINT,                          -- Link to business properties table
    
    -- Lifecycle Tracking
    instruction_amount DECIMAL(10,2),            -- Expected amount (£1,075)
    actual_amount DECIMAL(10,2),                 -- Received amount (£1,075)
    distributed_amount DECIMAL(10,2),            -- Amount distributed (£1,075)
    variance_amount DECIMAL(10,2),               -- Difference tracking
    
    -- Status
    lifecycle_status ENUM('instruction_only', 'payment_received', 'distributed', 'completed') DEFAULT 'instruction_only',
    reconciliation_status ENUM('matched', 'variance', 'missing', 'error') DEFAULT 'matched',
    
    -- Timing
    instruction_date DATE,
    payment_date DATE,
    distribution_date DATE,
    
    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Foreign Key Constraints
    FOREIGN KEY (invoice_instruction_id) REFERENCES payprop_export_invoices(payprop_id),
    FOREIGN KEY (payment_transaction_id) REFERENCES payprop_report_all_payments(payprop_id),
    FOREIGN KEY (distribution_rule_id) REFERENCES payprop_export_payments(payprop_id),
    FOREIGN KEY (property_id) REFERENCES properties(id),
    
    -- Indexes
    INDEX idx_property_id (property_id),
    INDEX idx_lifecycle_status (lifecycle_status),
    INDEX idx_payment_date (payment_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 🔧 INTEGRATION WITH EXISTING SYSTEM

### **Update Existing Properties Table**
```sql
-- Add new fields to existing properties table
ALTER TABLE properties 
ADD COLUMN rent_source_calculation_id BIGINT,
ADD COLUMN payprop_sync_status ENUM('synced', 'partial', 'error') DEFAULT 'partial',
ADD COLUMN last_payprop_sync TIMESTAMP,

-- Foreign key to business logic layer
ADD CONSTRAINT fk_rent_source 
    FOREIGN KEY (rent_source_calculation_id) 
    REFERENCES property_rent_sources(property_id);

-- Update monthly_payment to use calculated authoritative amount
-- (This will be done via business logic service, not trigger)
```

### **Update Financial Transactions Table**
```sql
-- Link to raw PayProp data
ALTER TABLE financial_transactions
ADD COLUMN payprop_raw_payment_id VARCHAR(50),
ADD COLUMN payprop_lifecycle_link_id BIGINT,

ADD CONSTRAINT fk_payprop_raw_payment 
    FOREIGN KEY (payprop_raw_payment_id) 
    REFERENCES payprop_report_all_payments(payprop_id),
    
ADD CONSTRAINT fk_lifecycle_link
    FOREIGN KEY (payprop_lifecycle_link_id)
    REFERENCES payment_lifecycle_links(id);
```

---

## 🎯 SOLVING THE £995 vs £1,075 PROBLEM

### **Data Flow Solution**
```sql
-- 1. Raw Data Import (no business logic)
INSERT INTO payprop_export_properties (payprop_id, settings_monthly_payment, ...)
VALUES ('K3Jwqg8W1E', 995.00, ...);

INSERT INTO payprop_export_invoices (payprop_id, property_payprop_id, gross_amount, ...)  
VALUES ('BRXEzNG51O', 'K3Jwqg8W1E', 1075.00, ...);

-- 2. Business Logic Calculation
INSERT INTO property_rent_sources (property_id, settings_rent_amount, invoice_instruction_amount, ...)
SELECT p.id, props.settings_monthly_payment, inv.gross_amount, ...
FROM properties p
JOIN payprop_export_properties props ON p.payprop_id = props.payprop_id
JOIN payprop_export_invoices inv ON props.payprop_id = inv.property_payprop_id;

-- 3. Business Decision (authoritative amount selection)
UPDATE property_rent_sources 
SET authoritative_rent_source = 'invoice',
    current_rent_amount = invoice_instruction_amount,
    settings_vs_invoice_variance = invoice_instruction_amount - settings_rent_amount,
    variance_explanation = CASE 
        WHEN ABS(invoice_instruction_amount - settings_rent_amount) > variance_threshold 
        THEN 'Significant variance - using invoice as authoritative'
        ELSE 'Invoice amount selected as authoritative'
    END;

-- 4. Update Business Properties Table
UPDATE properties p
JOIN property_rent_sources prs ON p.id = prs.property_id
SET p.monthly_payment = prs.current_rent_amount;
```

---

## ✅ PHASE 2 BENEFITS

### **Problem Resolution**
- ✅ **£995 vs £1,075 Solved** - Both values stored, business logic decides
- ✅ **Zero Data Loss** - Complete PayProp response preservation
- ✅ **Clear Data Lineage** - Trace every value back to source
- ✅ **Future-Proof** - PayProp changes don't break business logic

### **Architecture Benefits**
- ✅ **Separation of Concerns** - Raw import vs business decisions
- ✅ **Easy Debugging** - See exactly what PayProp sent
- ✅ **Flexible Business Rules** - Change logic without re-importing
- ✅ **Complete Audit Trail** - Every payment from instruction to distribution

### **Development Benefits**
- ✅ **Right-First-Time** - No field mapping guesswork
- ✅ **Maintainable** - Clear table purposes and relationships
- ✅ **Testable** - Raw data provides consistent test foundation
- ✅ **Scalable** - Ready for additional PayProp endpoints

**Next Steps:** 
1. Complete `/export/beneficiaries` and `/export/tenants` API structure documentation
2. Create migration scripts from current schema
3. Implement raw import services (zero business logic)
4. Implement business logic services (rent calculation, payment linking)
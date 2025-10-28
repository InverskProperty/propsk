# CRM Database Structure Documentation

**Generated:** 2025-10-27
**Purpose:** Comprehensive reference for import leases, invoices, historical transactions, financial transactions, and PayProp integration

---

## Table of Contents
1. [Overview](#overview)
2. [Invoices/Leases Structure](#invoicesleases-structure)
3. [Historical Transactions](#historical-transactions)
4. [Financial Transactions](#financial-transactions)
5. [PayProp Integration Tables](#payprop-integration-tables)
6. [Import and Staging Tables](#import-and-staging-tables)
7. [Database Views](#database-views)
8. [Entity Relationships](#entity-relationships)
9. [File Reference](#file-reference)

---

## Overview

This CRM system manages:
- **Leases (Invoices):** Recurring payment instructions for tenants
- **Historical Transactions:** Complete historical financial records with multi-level support
- **Financial Transactions:** PayProp-synced actual transactions with commission tracking
- **PayProp Integration:** Two-way sync with PayProp property management system
- **Import System:** Batch import with staging, validation, and audit trails

---

## Invoices/Leases Structure

### Entity File
`src/main/java/site/easy/to/build/crm/entity/Invoice.java` (424 lines)

### Main Table: `invoices`

**Purpose:** Stores recurring payment instructions (leases) that generate actual invoices periodically

#### Core Fields
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT | Primary key |
| `lease_reference` | VARCHAR(100) UNIQUE | User-assigned lease identifier |
| `customer_id` | BIGINT FK | Tenant/customer |
| `property_id` | BIGINT FK | Associated property |
| `amount` | DECIMAL(10,2) | Invoice amount |
| `frequency` | ENUM | one_time, daily, weekly, monthly, quarterly, yearly |
| `payment_day` | INT | Day of month/week for payment |
| `start_date` | DATE | Lease start date |
| `end_date` | DATE | Lease end date (NULL for ongoing) |
| `is_active` | BOOLEAN | Active status |
| `invoice_type` | VARCHAR(50) | Type classification |

#### PayProp Sync Fields
| Field | Type | Description |
|-------|------|-------------|
| `payprop_id` | VARCHAR(32) UNIQUE | PayProp invoice ID |
| `payprop_customer_id` | VARCHAR(32) | PayProp customer reference |
| `payprop_last_sync` | DATETIME | Last sync timestamp |
| `sync_status` | ENUM | pending, synced, error, manual |
| `category_id` | VARCHAR(32) | PayProp category |

#### Additional Fields
| Field | Type | Description |
|-------|------|-------------|
| `vat_included` | BOOLEAN | VAT status |
| `vat_amount` | DECIMAL(10,2) | VAT amount if applicable |
| `is_debit_order` | BOOLEAN | Debit order flag |
| `account_type` | ENUM | individual, business |
| `description` | TEXT | Lease description |
| `internal_reference` | VARCHAR(100) | Internal tracking reference |
| `external_reference` | VARCHAR(100) | External system reference |
| `notes` | TEXT | Additional notes |
| `created_by_user_id` | BIGINT FK | User who created |
| `updated_by_user_id` | BIGINT FK | User who last updated |
| `created_at` | DATETIME | Creation timestamp |
| `updated_at` | DATETIME | Last update timestamp |
| `deleted_at` | DATETIME | Soft delete timestamp |

### Related Tables

#### `invoice_line_items`
**Purpose:** Detailed line items for invoices with multiple charges

| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT | Primary key |
| `invoice_id` | BIGINT FK | Parent invoice |
| `line_number` | INT | Line order |
| `description` | VARCHAR(255) | Line item description |
| `quantity` | DECIMAL(10,2) | Quantity |
| `unit_price` | DECIMAL(10,2) | Price per unit |
| `line_total` | DECIMAL(10,2) GENERATED | Auto-calculated total |

#### `invoice_generations`
**Purpose:** Tracks each time an invoice instruction generates an actual invoice

| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT | Primary key |
| `invoice_id` | BIGINT FK | Parent invoice instruction |
| `generation_date` | DATE | When generated |
| `payment_due_date` | DATE | Due date |
| `payment_received_date` | DATE | Payment received (if paid) |
| `payprop_generated_invoice_id` | VARCHAR(32) | PayProp reference for generated invoice |
| `status` | VARCHAR(50) | Generation status |

### Indexes
- `idx_customer_property`: (customer_id, property_id)
- `idx_payprop_id`: (payprop_id)
- `idx_sync_status`: (sync_status)
- `idx_active_invoices`: (is_active, start_date, end_date)
- `idx_frequency_payment_day`: (frequency, payment_day)
- `idx_invoices_lease_reference`: (lease_reference)
- `idx_invoices_property_dates`: (property_id, start_date, end_date)

### Schema Files
- `create_invoices_table.sql` (305 lines)
- `V7__Add_Lease_Reference_To_Invoices.sql`
- `V10__Add_Invoice_Id_To_Financial_Transactions.sql`

---

## Historical Transactions

### Entity File
`src/main/java/site/easy/to/build/crm/entity/HistoricalTransaction.java` (745 lines)

### Main Table: `historical_transactions`

**Purpose:** Complete historical financial records with multi-level transaction support and comprehensive tracking

#### Core Transaction Fields
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT | Primary key |
| `transaction_date` | DATE | Transaction date |
| `amount` | DECIMAL(12,2) | Amount (positive or negative) |
| `transaction_type` | ENUM | payment, invoice, expense, maintenance, deposit, withdrawal, transfer, fee, refund, adjustment |
| `description` | TEXT | Transaction description |
| `category` | VARCHAR(100) | Primary category |
| `subcategory` | VARCHAR(100) | Sub-category |

#### Multi-Level Entity References
| Field | Type | Description |
|-------|------|-------------|
| `property_id` | BIGINT FK | Associated property |
| `customer_id` | BIGINT FK | Primary customer |
| `beneficiary_id` | BIGINT FK | Payment recipient |
| `tenant_id` | BIGINT FK | Tenant context |
| `owner_id` | BIGINT FK | Property owner context |
| `block_id` | BIGINT FK | Block/complex reference |
| `transaction_level` | ENUM | property, block, owner, portfolio |

#### Lease Linking Fields (Added V8)
| Field | Type | Description |
|-------|------|-------------|
| `invoice_id` | BIGINT FK | Links to lease agreement (invoices table) |
| `lease_start_date` | DATE | Lease period start |
| `lease_end_date` | DATE | Lease period end |
| `rent_amount_at_transaction` | DECIMAL(10,2) | Rent amount at time of transaction |

#### Source and Import Tracking
| Field | Type | Description |
|-------|------|-------------|
| `source` | ENUM | historical_import, manual_entry, bank_import, spreadsheet_import, system_migration, api_sync |
| `source_reference` | VARCHAR(255) | Original source reference |
| `import_batch_id` | VARCHAR(100) | Batch import identifier |
| `import_staging_id` | BIGINT FK | Links to staging record |
| `payment_source_id` | BIGINT FK | Payment source reference |

#### Banking and Payment Details
| Field | Type | Description |
|-------|------|-------------|
| `bank_reference` | VARCHAR(255) | Bank transaction reference |
| `payment_method` | VARCHAR(100) | Payment method used |
| `counterparty_name` | VARCHAR(255) | Other party name |
| `counterparty_account` | VARCHAR(100) | Other party account |

#### Financial Tracking
| Field | Type | Description |
|-------|------|-------------|
| `running_balance` | DECIMAL(12,2) | Running balance at transaction |
| `financial_year` | INT | Financial year |
| `tax_relevant` | BOOLEAN | Tax relevance flag |
| `vat_applicable` | BOOLEAN | VAT applies |
| `vat_amount` | DECIMAL(10,2) | VAT amount |

#### Commission and Fees
| Field | Type | Description |
|-------|------|-------------|
| `commission_rate` | DECIMAL(5,2) | Commission percentage |
| `commission_amount` | DECIMAL(10,2) | Commission amount |
| `service_fee_rate` | DECIMAL(5,2) | Service fee percentage |
| `service_fee_amount` | DECIMAL(10,2) | Service fee amount |
| `net_to_owner_amount` | DECIMAL(10,2) | Net amount to owner |

#### PayProp Integration
| Field | Type | Description |
|-------|------|-------------|
| `payprop_transaction_id` | VARCHAR(100) | PayProp transaction ID |
| `payprop_property_id` | VARCHAR(100) | PayProp property ID |
| `payprop_tenant_id` | VARCHAR(100) | PayProp tenant ID |
| `payprop_beneficiary_id` | VARCHAR(100) | PayProp beneficiary ID |
| `payprop_category_id` | VARCHAR(100) | PayProp category ID |

#### Instruction vs Actual
| Field | Type | Description |
|-------|------|-------------|
| `is_instruction` | BOOLEAN | Payment instruction flag |
| `is_actual_transaction` | BOOLEAN | Actual transaction flag |
| `instruction_id` | VARCHAR(100) | Instruction identifier |
| `instruction_date` | DATE | Instruction date |

#### Batch Payment Support
| Field | Type | Description |
|-------|------|-------------|
| `batch_payment_id` | BIGINT FK | Batch payment reference |
| `payprop_batch_id` | VARCHAR(100) | PayProp batch ID |
| `batch_sequence_number` | INT | Sequence in batch |

#### Reconciliation
| Field | Type | Description |
|-------|------|-------------|
| `reconciled` | BOOLEAN | Reconciliation status |
| `reconciled_date` | DATE | When reconciled |
| `reconciled_by_user_id` | BIGINT FK | User who reconciled |

#### Status and Validation
| Field | Type | Description |
|-------|------|-------------|
| `status` | ENUM | active, cancelled, disputed, pending_review |
| `validated` | BOOLEAN | Validation status |
| `validation_notes` | TEXT | Validation notes |

#### Metadata
| Field | Type | Description |
|-------|------|-------------|
| `created_by_user_id` | BIGINT FK | Creating user |
| `updated_by_user_id` | BIGINT FK | Last updating user |
| `created_at` | DATETIME | Creation timestamp |
| `updated_at` | DATETIME | Last update timestamp |
| `notes` | TEXT | Additional notes |
| `tags` | TEXT | Tags (JSON array) |

### Indexes
- `idx_transaction_date`: (transaction_date)
- `idx_property_date`: (property_id, transaction_date)
- `idx_customer_date`: (customer_id, transaction_date)
- `idx_amount`: (amount)
- `idx_transaction_type`: (transaction_type)
- `idx_source`: (source)
- `idx_import_batch`: (import_batch_id)
- `idx_financial_year`: (financial_year)
- `idx_status`: (status)
- `idx_bank_reference`: (bank_reference)
- `idx_category`: (category)
- `idx_reconciled`: (reconciled)
- `idx_historical_transactions_invoice_id`: (invoice_id)
- `idx_historical_transactions_lease_period`: (property_id, lease_start_date, lease_end_date)
- `idx_historical_transactions_date_invoice`: (transaction_date, invoice_id)

### Schema Files
- `create_historical_transactions_table.sql`
- `V8__Add_Lease_Fields_To_Historical_Transactions.sql`
- `sql/01_enhance_historical_transactions.sql`

---

## Financial Transactions

### Entity File
`src/main/java/site/easy/to/build/crm/entity/FinancialTransaction.java` (379 lines)

### Main Table: `financial_transactions`

**Purpose:** PayProp-synced actual transactions with commission calculation and batch payment support

#### Core Fields
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT | Primary key |
| `pay_prop_transaction_id` | VARCHAR(100) UNIQUE | PayProp transaction identifier |
| `amount` | DECIMAL(10,2) | Transaction amount |
| `matched_amount` | DECIMAL(10,2) | Matched/allocated amount |
| `transaction_date` | DATE | Transaction date |
| `transaction_type` | VARCHAR(50) | invoice, credit_note, debit_note |
| `description` | VARCHAR(500) | Transaction description |

#### Tax and Financial Details
| Field | Type | Description |
|-------|------|-------------|
| `has_tax` | BOOLEAN | Tax applicable flag |
| `tax_amount` | DECIMAL(10,2) | Tax amount |
| `deposit_id` | VARCHAR(100) | Deposit reference |

#### PayProp Entity References
| Field | Type | Description |
|-------|------|-------------|
| `property_id` | VARCHAR(100) | PayProp property ID |
| `property_name` | VARCHAR(255) | Property name |
| `tenant_id` | VARCHAR(100) | PayProp tenant ID |
| `tenant_name` | VARCHAR(255) | Tenant name |
| `category_id` | VARCHAR(100) | PayProp category ID |
| `category_name` | VARCHAR(255) | Category name |

#### Commission and Fees
| Field | Type | Description |
|-------|------|-------------|
| `commission_amount` | DECIMAL(10,2) | Commission amount |
| `commission_rate` | DECIMAL(5,2) | Commission rate percentage |
| `service_fee_amount` | DECIMAL(10,2) | Service fee amount |
| `net_to_owner_amount` | DECIMAL(10,2) | Net amount to owner after fees |

#### Data Source Classification
| Field | Type | Description |
|-------|------|-------------|
| `data_source` | VARCHAR(50) | ICDN_ACTUAL, PAYMENT_INSTRUCTION, COMMISSION_PAYMENT |
| `is_actual_transaction` | BOOLEAN | Actual transaction flag |
| `is_instruction` | BOOLEAN | Instruction flag |

#### Instruction Tracking
| Field | Type | Description |
|-------|------|-------------|
| `instruction_id` | VARCHAR(100) | Instruction identifier |
| `instruction_date` | DATE | Instruction date |
| `reconciliation_date` | DATE | Reconciliation date |

#### Commission Calculation
| Field | Type | Description |
|-------|------|-------------|
| `actual_commission_amount` | DECIMAL(10,2) | Actual commission from PayProp |
| `calculated_commission_amount` | DECIMAL(10,2) | Locally calculated commission |

#### Batch Payment Support
| Field | Type | Description |
|-------|------|-------------|
| `batch_payment_id` | BIGINT FK | Links to batch_payment table |
| `pay_prop_batch_id` | VARCHAR(100) | PayProp batch identifier |
| `batch_sequence_number` | INT | Sequence number in batch |

#### Lease Linking (Added V10)
| Field | Type | Description |
|-------|------|-------------|
| `invoice_id` | BIGINT FK | Links to lease agreement (invoices table) |

#### Timestamps
| Field | Type | Description |
|-------|------|-------------|
| `created_at` | DATETIME | Creation timestamp |
| `updated_at` | DATETIME | Last update timestamp |

### Indexes
- `idx_financial_transactions_invoice_id`: (invoice_id)
- Additional indexes on payprop_transaction_id (UNIQUE)

### Schema Files
- `V10__Add_Invoice_Id_To_Financial_Transactions.sql`
- `sql/03_migrate_financial_transactions.sql`

---

## PayProp Integration Tables

### PayProp Tenant Complete

#### Entity File
`src/main/java/site/easy/to/build/crm/entity/PayPropTenantComplete.java` (583 lines)

#### Table: `payprop_export_tenants_complete`

**Purpose:** Complete tenant export data from PayProp `/export/tenants` endpoint

| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT | Primary key |
| `pay_prop_id` | VARCHAR(100) UNIQUE | PayProp tenant ID |
| `first_name` | VARCHAR(100) | First name |
| `last_name` | VARCHAR(100) | Last name |
| `email` | VARCHAR(255) | Email address |
| `phone` | VARCHAR(50) | Phone number |
| `mobile` | VARCHAR(50) | Mobile number |
| `address_line_1` | VARCHAR(255) | Address line 1 |
| `address_line_2` | VARCHAR(255) | Address line 2 |
| `city` | VARCHAR(100) | City |
| `postal_code` | VARCHAR(20) | Postal code |
| `country` | VARCHAR(100) | Country |
| `tenancy_start_date` | DATE | Tenancy start |
| `tenancy_end_date` | DATE | Tenancy end |
| `monthly_rent` | DECIMAL(10,2) | Monthly rent amount |
| `deposit_amount` | DECIMAL(10,2) | Deposit amount |
| `credit_score` | INT | Credit score |
| `reference` | VARCHAR(255) | Reference |
| `comment` | TEXT | Comments |
| `created_at` | DATETIME | Creation timestamp |
| `updated_at` | DATETIME | Last update timestamp |

### PayProp Incoming Payments

#### Table: `payprop_incoming_payments`

**Purpose:** Extracted tenant payment records from PayProp, deduplicated and ready for sync to historical_transactions

| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT | Primary key |
| `incoming_transaction_id` | VARCHAR(100) UNIQUE | PayProp incoming transaction ID |
| `amount` | DECIMAL(10,2) | Payment amount |
| `reconciliation_date` | DATE | Payment date |
| `tenant_id` | VARCHAR(100) | PayProp tenant ID |
| `tenant_name` | VARCHAR(255) | Tenant name |
| `property_id` | VARCHAR(100) | PayProp property ID |
| `property_name` | VARCHAR(255) | Property name |
| `category_id` | VARCHAR(100) | PayProp category ID |
| `category_name` | VARCHAR(255) | Category name |
| `source_report_date` | DATE | Source report date |
| `extracted_at` | DATETIME | When extracted |
| `synced_to_historical` | BOOLEAN | Sync status |
| `synced_at` | DATETIME | When synced |
| `historical_transaction_id` | BIGINT FK | Link to created historical transaction |

#### Indexes
- `idx_payprop_incoming_reconciliation_date`: (reconciliation_date)
- `idx_payprop_incoming_property`: (property_id)
- `idx_payprop_incoming_tenant`: (tenant_id)
- `idx_payprop_incoming_sync_status`: (synced_to_historical)
- `idx_payprop_incoming_extracted`: (extracted_at)

#### Schema Files
- `V14__create_payprop_export_incoming_payments.sql`
- `V15__create_payprop_incoming_payments.sql`

### PayProp Tag Links

#### Entity File
`src/main/java/site/easy/to/build/crm/entity/PayPropTagLink.java` (176 lines)

#### Table: `payprop_tag_links`

**Purpose:** Links internal portfolios to PayProp tags for filtering synced data

| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT | Primary key |
| `portfolio_id` | BIGINT FK | Local portfolio reference |
| `tag_id` | VARCHAR(100) | PayProp tag ID |
| `tag_name` | VARCHAR(255) | Tag name |
| `sync_status` | VARCHAR(50) | Sync status |
| `synced_at` | DATETIME | Last sync timestamp |

### Customer PayProp Fields

#### Migration: `V3__Add_Customer_Classification_PayProp.sql`

**Added to `customers` table:**

| Field | Type | Description |
|-------|------|-------------|
| `customer_type` | ENUM | REGULAR_CUSTOMER, TENANT, PROPERTY_OWNER, CONTRACTOR |
| `payprop_entity_id` | VARCHAR(100) | PayProp entity ID |
| `payprop_customer_id` | VARCHAR(100) | PayProp customer ID |
| `payprop_synced` | BOOLEAN | Sync status |
| `payprop_last_sync` | DATETIME | Last sync timestamp |
| `is_property_owner` | BOOLEAN | Property owner flag |
| `is_tenant` | BOOLEAN | Tenant flag |
| `is_contractor` | BOOLEAN | Contractor flag |

#### Indexes
- `idx_customer_type`: (customer_type)
- `idx_customer_payprop_entity`: (payprop_entity_id)
- `idx_customer_payprop_customer`: (payprop_customer_id)

---

## Import and Staging Tables

### Transaction Import Staging

#### Entity File
`src/main/java/site/easy/to/build/crm/entity/TransactionImportStaging.java` (270 lines)

#### Table: `transaction_import_staging`

**Purpose:** Multi-paste batch accumulation for reviewing transactions before committing to historical_transactions

| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT | Primary key |
| `batch_id` | VARCHAR(100) | Batch identifier for grouping |
| `payment_source_id` | BIGINT FK | Source of payment |
| `line_number` | INT | Line number in batch |
| `csv_line` | TEXT | Original CSV line |
| `transaction_date` | DATE | Transaction date |
| `amount` | DECIMAL(12,2) | Amount |
| `description` | TEXT | Description |
| `transaction_type` | VARCHAR(50) | Type |
| `category` | VARCHAR(100) | Category |
| `bank_reference` | VARCHAR(255) | Bank reference |
| `payment_method` | VARCHAR(100) | Payment method |
| `notes` | TEXT | Notes |
| `property_id` | BIGINT FK | Matched property |
| `customer_id` | BIGINT FK | Matched customer |
| `status` | VARCHAR(50) | PENDING_REVIEW, APPROVED, REJECTED, AMBIGUOUS_PROPERTY, AMBIGUOUS_CUSTOMER, DUPLICATE |
| `is_duplicate` | BOOLEAN | Duplicate flag |
| `duplicate_of_transaction_id` | BIGINT | Original transaction ID if duplicate |
| `user_note` | TEXT | User notes |
| `created_at` | DATETIME | Creation timestamp |

#### Workflow
1. User pastes multiple CSV lines
2. System parses into staging records with same batch_id
3. Duplicate detection runs
4. User reviews and approves/rejects
5. Approved records convert to historical_transactions

### Import Audit

#### Entity File
`src/main/java/site/easy/to/build/crm/entity/ImportAudit.java` (190 lines)

#### Table: `import_audit`

**Purpose:** Audit trail for all import operations

| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT | Primary key |
| `batch_id` | VARCHAR(100) | Batch identifier |
| `import_type` | VARCHAR(50) | CSV, JSON, MANUAL, PAYPROP_SYNC |
| `total_rows` | INT | Total rows in import |
| `imported_rows` | INT | Successfully imported |
| `skipped_rows` | INT | Skipped rows |
| `error_rows` | INT | Error rows |
| `user_id` | BIGINT FK | User who performed import |
| `user_name` | VARCHAR(100) | User name snapshot |
| `imported_at` | DATETIME | Import timestamp |
| `review_notes` | TEXT | Review notes |
| `verification_status` | VARCHAR(50) | PENDING, REVIEWED, AUTO_IMPORTED |
| `created_at` | DATETIME | Creation timestamp |
| `updated_at` | DATETIME | Last update timestamp |

#### Indexes
- `idx_batch_id`: (batch_id)
- `idx_user_id`: (user_id)
- `idx_imported_at`: (imported_at)
- `idx_verification_status`: (verification_status)

#### Schema File
- `V4__Create_Import_Audit_Table.sql`

### Payment Sources

#### Table: `payment_sources`

**Purpose:** Tracks different sources of transaction imports

| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT | Primary key |
| `name` | VARCHAR(255) UNIQUE | Source name |
| `description` | TEXT | Description |
| `source_type` | VARCHAR(50) | BANK_STATEMENT, OLD_ACCOUNT, PAYPROP, MANUAL, etc. |
| `created_at` | DATETIME | Creation timestamp |
| `created_by_user_id` | BIGINT FK | Creating user |
| `last_import_date` | DATE | Last import date |
| `total_transactions` | INT | Total transactions imported |
| `is_active` | BOOLEAN | Active status |

#### Schema File
- `V5__Create_Payment_Source_Tables.sql`

---

## Database Views

### 1. active_invoices_view

**Source:** `create_invoices_table.sql`

**Purpose:** Complete invoice details with customer and property information, calculates next generation date

```sql
SELECT
    i.*,
    c.name as customer_name,
    p.name as property_name,
    -- Next generation date calculation based on frequency
    CASE
        WHEN i.frequency = 'monthly' THEN ...
        WHEN i.frequency = 'quarterly' THEN ...
        -- etc
    END as next_generation_date
FROM invoices i
LEFT JOIN customers c ON i.customer_id = c.id
LEFT JOIN properties p ON i.property_id = p.id
WHERE i.is_active = TRUE
```

### 2. invoice_sync_summary

**Source:** `create_invoices_table.sql`

**Purpose:** PayProp sync status overview

```sql
SELECT
    sync_status,
    COUNT(*) as invoice_count,
    SUM(amount) as total_amount
FROM invoices
WHERE deleted_at IS NULL
GROUP BY sync_status
```

### 3. payprop_entities

**Source:** `V3__Add_Customer_Classification_PayProp.sql`

**Purpose:** Easier querying of PayProp-linked entities

```sql
SELECT
    customer_id,
    customer_type,
    payprop_entity_id,
    payprop_customer_id,
    name,
    email,
    is_tenant,
    is_property_owner,
    payprop_synced,
    payprop_last_sync
FROM customers
WHERE customer_type IN ('TENANT', 'PROPERTY_OWNER')
    AND payprop_entity_id IS NOT NULL
```

### 4. v_orphaned_payprop_transactions

**Source:** `V13__Add_PayProp_Indexes_And_Enhanced_Linking.sql`

**Purpose:** Identifies transactions with PayProp IDs but no local lease link

```sql
SELECT
    'financial_transaction' as source_table,
    id,
    pay_prop_transaction_id as payprop_id,
    amount,
    transaction_date,
    property_id,
    tenant_id
FROM financial_transactions
WHERE invoice_id IS NULL
    AND pay_prop_transaction_id IS NOT NULL

UNION ALL

SELECT
    'historical_transaction' as source_table,
    id,
    payprop_transaction_id as payprop_id,
    amount,
    transaction_date,
    payprop_property_id as property_id,
    payprop_tenant_id as tenant_id
FROM historical_transactions
WHERE invoice_id IS NULL
    AND payprop_transaction_id IS NOT NULL
```

---

## Entity Relationships

### Core Relationship Diagram

```
customers (customer_id)
    ├── customer_type: REGULAR_CUSTOMER | TENANT | PROPERTY_OWNER | CONTRACTOR
    ├── payprop_entity_id, payprop_customer_id
    ├── is_tenant, is_property_owner, is_contractor flags
    │
    ├─[customer_id]─> invoices
    │   ├── lease_reference (unique)
    │   ├── payprop_id (unique, syncs to PayProp)
    │   ├── frequency: one_time | monthly | quarterly | yearly
    │   ├── start_date, end_date (lease period)
    │   │
    │   ├─[invoice_id]─> historical_transactions
    │   │   ├── Links historical transactions to specific lease
    │   │   ├── Captures lease_start_date, lease_end_date, rent_amount_at_transaction
    │   │   └── Enables lease-based arrears tracking
    │   │
    │   ├─[invoice_id]─> financial_transactions
    │   │   ├── Links PayProp transactions to leases
    │   │   └── Enables lease-based financial reporting
    │   │
    │   └─[invoice_id]─> invoice_line_items
    │       └── Multiple charges per invoice
    │
    ├─[customer_id]─> historical_transactions (primary customer)
    │   ├── Also: beneficiary_id, tenant_id, owner_id
    │   ├── transaction_level: property | block | owner | portfolio
    │   ├── source: historical_import | manual_entry | bank_import | spreadsheet_import | api_sync
    │   ├── Commission tracking: commission_rate, commission_amount
    │   ├── PayProp fields: payprop_transaction_id, payprop_property_id, etc.
    │   └── Batch support: batch_payment_id, batch_sequence_number
    │
    └─[customer_id]─> transaction_import_staging
        └── Staging for review before importing to historical_transactions

properties (id)
    ├── payprop_id (PayProp sync)
    ├─[property_id]─> invoices
    ├─[property_id]─> historical_transactions
    └─[property_id]─> financial_transactions (via property_id string)

users (id)
    ├─[created_by_user_id]─> invoices
    ├─[created_by_user_id]─> historical_transactions
    ├─[reconciled_by_user_id]─> historical_transactions
    └─[user_id]─> import_audit

payment_sources (id)
    ├─[payment_source_id]─> transaction_import_staging
    └─[payment_source_id]─> historical_transactions

batch_payment (id)
    ├─[batch_payment_id]─> financial_transactions
    └─[batch_payment_id]─> historical_transactions

portfolios (id)
    └─[portfolio_id]─> payprop_tag_links
        └── Links portfolio to PayProp tags for filtered sync

blocks (id)
    └─[block_id]─> historical_transactions

payprop_export_tenants_complete
    └── Complete tenant data from PayProp /export/tenants

payprop_incoming_payments
    ├── Extracted tenant payments from PayProp
    └─[historical_transaction_id]─> historical_transactions (after sync)
```

### Key Relationship Notes

#### Invoice → Transaction Linking
- **Purpose:** Track which transactions relate to which lease agreements
- **historical_transactions.invoice_id** → invoices.id
- **financial_transactions.invoice_id** → invoices.id
- Enables:
  - Lease-based arrears calculations
  - Tenant payment history per lease
  - Lease-specific financial statements

#### Multi-Customer Support in Historical Transactions
- **customer_id:** Primary customer (usually tenant for rent payments)
- **beneficiary_id:** Who receives the payment (owner, contractor)
- **tenant_id:** Explicit tenant reference
- **owner_id:** Property owner context
- Enables complex multi-party transaction tracking

#### Transaction Levels
- **property:** Transaction at individual property level
- **block:** Block/complex level transaction
- **owner:** Owner-level transaction (multiple properties)
- **portfolio:** Portfolio-wide transaction

#### PayProp Synchronization Flow
1. **customers** table: payprop_entity_id, payprop_customer_id fields
2. **properties** table: payprop_id field
3. **invoices** table: payprop_id, sync_status fields
4. **financial_transactions**: Synced from PayProp with pay_prop_transaction_id
5. **payprop_incoming_payments**: Extracted payment records
6. **historical_transactions**: Can store payprop_transaction_id for tracking

#### Import Workflow
1. **transaction_import_staging**: Multi-paste batch import
2. **import_audit**: Records import metadata
3. **payment_sources**: Tracks source of imports
4. **historical_transactions**: Final destination after approval

---

## File Reference

### Entity/Model Files (Java)
| File | Lines | Description |
|------|-------|-------------|
| `src/main/java/site/easy/to/build/crm/entity/Invoice.java` | 424 | Lease/invoice model |
| `src/main/java/site/easy/to/build/crm/entity/HistoricalTransaction.java` | 745 | Historical transaction model |
| `src/main/java/site/easy/to/build/crm/entity/FinancialTransaction.java` | 379 | Financial transaction model |
| `src/main/java/site/easy/to/build/crm/entity/TransactionImportStaging.java` | 270 | Import staging model |
| `src/main/java/site/easy/to/build/crm/entity/PayPropTenantComplete.java` | 583 | PayProp tenant export model |
| `src/main/java/site/easy/to/build/crm/entity/ImportAudit.java` | 190 | Import audit model |
| `src/main/java/site/easy/to/build/crm/entity/PayPropTagLink.java` | 176 | PayProp tag link model |

### Migration Files (Flyway)
Located in `src/main/resources/db/migration/`

| File | Description |
|------|-------------|
| `V3__Add_Customer_Classification_PayProp.sql` | Customer classification and PayProp fields |
| `V4__Create_Import_Audit_Table.sql` | Import audit table creation |
| `V5__Create_Payment_Source_Tables.sql` | Payment sources and transaction staging |
| `V7__Add_Lease_Reference_To_Invoices.sql` | Lease reference field for invoices |
| `V8__Add_Lease_Fields_To_Historical_Transactions.sql` | Invoice linking in historical transactions |
| `V10__Add_Invoice_Id_To_Financial_Transactions.sql` | Invoice linking in financial transactions |
| `V13__Add_PayProp_Indexes_And_Enhanced_Linking.sql` | PayProp indexes and orphan detection view |
| `V14__create_payprop_export_incoming_payments.sql` | PayProp incoming payment export |
| `V15__create_payprop_incoming_payments.sql` | PayProp incoming payments table |

### Schema Creation Scripts
Located in project root

| File | Lines | Description |
|------|-------|-------------|
| `create_invoices_table.sql` | 305 | Complete invoices schema with views |
| `create_historical_transactions_table.sql` | 100+ | Historical transactions schema |
| `create_historical_simple.sql` | - | Simplified historical schema |
| `sql/create_payprop_import_issues_table.sql` | - | PayProp import issue tracking |

### Enhancement/Migration SQL
Located in `sql/` directory

| File | Description |
|------|-------------|
| `sql/01_enhance_historical_transactions.sql` | Historical transaction enhancements |
| `sql/03_migrate_financial_transactions.sql` | Financial transaction migration |

---

## Summary of Key Capabilities

### 1. Lease Management
- Create recurring payment instructions (invoices/leases)
- Track lease periods with start/end dates
- Link transactions to specific leases
- Calculate arrears per lease
- Sync leases with PayProp

### 2. Transaction Tracking
- **Historical Transactions:** Complete historical record with multi-level support
- **Financial Transactions:** PayProp-synced transactions with commission tracking
- Multi-party support (customer, beneficiary, tenant, owner)
- Transaction levels (property, block, owner, portfolio)

### 3. PayProp Integration
- Two-way sync with PayProp
- Customer classification (tenant, owner, contractor)
- Property linking
- Incoming payment extraction
- Tag-based portfolio filtering
- Orphan transaction detection

### 4. Import System
- Multi-paste batch imports
- Staging area for review
- Duplicate detection
- Multiple payment sources
- Complete audit trail

### 5. Commission and Fees
- Commission rate and amount tracking
- Service fee tracking
- Net-to-owner calculations
- Actual vs. calculated commission comparison

### 6. Reconciliation
- Manual reconciliation support
- Reconciliation date tracking
- User tracking for reconciliation
- Bank reference matching

### 7. Reporting Capabilities
- Active invoices with next generation dates
- Invoice sync status summaries
- PayProp entity views
- Orphaned transaction identification
- Lease-based financial statements
- Multi-level transaction aggregation

---

**End of Documentation**

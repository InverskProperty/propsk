# CRM Database Structure - VERIFIED FROM ACTUAL DATABASE

**Generated:** 2025-10-27
**Source:** Direct database schema queries from production database
**Database:** switchyard.proxy.rlwy.net:55090/railway

---

## Table of Contents
1. [Overview](#overview)
2. [Invoices and Lease Instructions](#invoices-and-lease-instructions)
3. [Historical Transactions](#historical-transactions)
4. [Financial Transactions](#financial-transactions)
5. [PayProp Integration Tables](#payprop-integration-tables)
6. [Import and Staging Tables](#import-and-staging-tables)
7. [Batch Payment System](#batch-payment-system)
8. [Database Indexes](#database-indexes)
9. [Key Relationships](#key-relationships)
10. [All Tables in Database](#all-tables-in-database)

---

## Overview

This CRM system manages property rentals with:
- **Two separate invoice/lease systems:**
  - `invoices` - Legacy table
  - `invoice_instructions` - Current PayProp-synced table
- **Historical Transactions:** Complete historical financial records (176 records)
- **Financial Transactions:** PayProp-synced actual transactions (1002 records)
- **PayProp Integration:** Multiple report tables and sync mechanisms
- **Import System:** Batch import with staging and normalization

---

## Invoices and Lease Instructions

### Table 1: `invoices` (Legacy System)

**Purpose:** Legacy invoice/lease table - appears to be older structure

#### Schema
| Field | Type | Null | Key | Default |
|-------|------|------|-----|---------|
| id | bigint | NO | PRI | auto_increment |
| payprop_id | varchar(50) | YES | UNI | NULL |
| amount | decimal(10,2) | NO | | NULL |
| account_type | enum('individual','business') | YES | | individual |
| payprop_customer_id | varchar(32) | YES | MUL | NULL |
| payprop_last_sync | datetime | YES | | NULL |
| sync_status | enum('pending','synced','error') | NO | MUL | pending |
| sync_error_message | text | YES | | NULL |
| customer_id | int unsigned | YES | MUL | NULL |
| property_id | bigint | YES | MUL | NULL |
| category_id | varchar(32) | NO | MUL | rent |
| category_name | varchar(100) | YES | | NULL |
| vat_included | tinyint(1) | NO | | 0 |
| vat_amount | decimal(10,2) | YES | | NULL |
| frequency | enum('monthly','weekly','daily','quarterly','yearly','one_time') | NO | MUL | monthly |
| frequency_code | varchar(10) | YES | | NULL |
| payment_day | int | YES | | NULL |
| start_date | date | NO | MUL | curdate() |
| end_date | date | YES | MUL | NULL |
| description | text | NO | | NULL |
| internal_reference | varchar(100) | YES | | NULL |
| external_reference | varchar(100) | YES | | NULL |
| is_active | tinyint(1) | NO | MUL | 1 |
| is_debit_order | tinyint(1) | NO | | 0 |
| created_at | datetime | NO | MUL | CURRENT_TIMESTAMP |
| updated_at | datetime | YES | | CURRENT_TIMESTAMP on update |
| deleted_at | datetime | YES | MUL | NULL |
| created_by_user_id | int unsigned | YES | | NULL |
| updated_by_user_id | int unsigned | YES | | NULL |
| notes | text | YES | | NULL |
| invoice_type | varchar(50) | YES | | NULL |
| lease_reference | varchar(100) | YES | UNI | NULL |

**43 records in database**

#### Indexes on invoices
- **PRIMARY:** id
- **UNIQUE:** payprop_id, idx_lease_reference (lease_reference)
- **MUL:**
  - idx_invoices_customer_id (customer_id)
  - idx_invoices_property_id (property_id)
  - idx_invoices_category_id (category_id)
  - idx_invoices_sync_status (sync_status)
  - idx_invoices_is_active (is_active)
  - idx_invoices_start_date (start_date)
  - idx_invoices_end_date (end_date)
  - idx_invoices_frequency (frequency)
  - idx_invoices_payprop_customer_id (payprop_customer_id)
  - idx_invoices_created_at (created_at)
  - idx_invoices_deleted_at (deleted_at)
  - **idx_invoices_property_dates** (property_id, start_date, end_date) - Composite index

---

### Table 2: `invoice_instructions` (Current PayProp System)

**Purpose:** Current system for payment instructions synced with PayProp

#### Schema
| Field | Type | Null | Key | Default |
|-------|------|------|-----|---------|
| id | bigint | NO | PRI | auto_increment |
| payprop_id | varchar(50) | YES | UNI | NULL |
| property_id | bigint | NO | MUL | NULL |
| tenant_id | bigint | YES | MUL | NULL |
| category_id | varchar(50) | YES | MUL | NULL |
| amount | decimal(15,2) | YES | | NULL |
| description | text | YES | | NULL |
| frequency | varchar(50) | YES | MUL | NULL |
| frequency_code | varchar(20) | YES | | NULL |
| from_date | date | YES | MUL | NULL |
| to_date | date | YES | MUL | NULL |
| payment_day | int | YES | | NULL |
| is_active | tinyint(1) | YES | MUL | 1 |
| created_date | timestamp | YES | | CURRENT_TIMESTAMP |
| modified_date | timestamp | YES | | CURRENT_TIMESTAMP on update |
| property_name | varchar(255) | YES | | NULL |
| tenant_name | varchar(255) | YES | | NULL |
| category_name | varchar(255) | YES | | NULL |
| sync_status | varchar(20) | YES | | active |
| gross_amount | decimal(10,2) | YES | | NULL |
| invoice_type | varchar(50) | YES | | NULL |

**Key Differences from `invoices` table:**
- Uses `tenant_id` instead of `customer_id`
- Has `gross_amount` field
- Uses `from_date/to_date` instead of `start_date/end_date`
- Different sync_status values (varchar vs enum)
- No soft delete (deleted_at)
- No user tracking fields

---

## Historical Transactions

### Table: `historical_transactions`

**Purpose:** Complete historical financial records with multi-level transaction support

**Current Data:** 176 records

#### Complete Schema
| Field | Type | Null | Key | Default |
|-------|------|------|-----|---------|
| id | bigint | NO | PRI | auto_increment |
| transaction_date | date | NO | MUL | NULL |
| amount | decimal(12,2) | NO | | NULL |
| description | text | NO | | NULL |
| transaction_type | enum | NO | | NULL |
| created_at | datetime | YES | | CURRENT_TIMESTAMP |
| category | varchar(100) | YES | | NULL |
| subcategory | varchar(100) | YES | | NULL |
| source | enum | NO | | historical_import |
| source_reference | varchar(255) | YES | | NULL |
| account_source | varchar(50) | YES | | NULL |
| payment_source_id | bigint | YES | MUL | NULL |
| import_staging_id | bigint | YES | MUL | NULL |
| related_transaction_group | varchar(100) | YES | | NULL |
| statement_month | varchar(20) | YES | | NULL |
| original_row_data | json | YES | | NULL |
| import_batch_id | varchar(100) | YES | MUL | NULL |
| bank_reference | varchar(100) | YES | | NULL |
| payment_method | varchar(50) | YES | | NULL |
| counterparty_name | varchar(255) | YES | | NULL |
| counterparty_account | varchar(100) | YES | | NULL |
| notes | text | YES | | NULL |
| property_id | bigint | YES | MUL | NULL |
| transaction_level | enum | YES | | property |
| customer_id | bigint | YES | MUL | NULL |
| **invoice_id** | bigint | YES | MUL | NULL |
| **lease_start_date** | date | YES | | NULL |
| **lease_end_date** | date | YES | | NULL |
| **rent_amount_at_transaction** | decimal(10,2) | YES | | NULL |
| created_by_user_id | bigint | YES | MUL | NULL |
| updated_at | datetime | YES | | CURRENT_TIMESTAMP on update |
| updated_by_user_id | bigint | YES | MUL | NULL |
| running_balance | decimal(12,2) | YES | | NULL |
| reconciled | tinyint(1) | NO | | 0 |
| reconciled_date | date | YES | | NULL |
| financial_year | varchar(10) | YES | | NULL |
| tax_relevant | tinyint(1) | NO | | 0 |
| vat_applicable | tinyint(1) | NO | | 0 |
| vat_amount | decimal(10,2) | YES | | NULL |
| status | enum | YES | | active |
| validated | tinyint(1) | NO | | 0 |
| validation_notes | text | YES | | NULL |
| tags | varchar(500) | YES | | NULL |
| reconciled_by_user_id | bigint | YES | MUL | NULL |
| payprop_transaction_id | varchar(100) | YES | UNI | NULL |
| payprop_property_id | varchar(100) | YES | | NULL |
| payprop_tenant_id | varchar(100) | YES | | NULL |
| payprop_beneficiary_id | varchar(100) | YES | | NULL |
| beneficiary_type | varchar(50) | YES | | NULL |
| beneficiary_name | varchar(255) | YES | | NULL |
| payprop_category_id | varchar(100) | YES | | NULL |
| commission_rate | decimal(5,2) | YES | | NULL |
| commission_amount | decimal(10,2) | YES | | NULL |
| service_fee_rate | decimal(5,2) | YES | | NULL |
| service_fee_amount | decimal(10,2) | YES | | NULL |
| transaction_fee | decimal(10,2) | YES | | 0.00 |
| incoming_transaction_amount | decimal(12,2) | YES | | NULL |
| incoming_transaction_id | varchar(100) | YES | | NULL |
| net_to_owner_amount | decimal(12,2) | YES | | NULL |
| is_instruction | tinyint(1) | YES | | 0 |
| is_actual_transaction | tinyint(1) | YES | | 0 |
| instruction_id | varchar(100) | YES | | NULL |
| instruction_date | date | YES | | NULL |
| batch_payment_id | bigint | YES | | NULL |
| payprop_batch_id | varchar(100) | YES | | NULL |
| batch_sequence_number | int | YES | | NULL |
| deposit_id | varchar(100) | YES | | NULL |
| reference | varchar(255) | YES | | NULL |
| block_id | bigint | YES | MUL | NULL |
| **beneficiary_id** | int unsigned | YES | MUL | NULL |
| **tenant_id** | int unsigned | YES | MUL | NULL |
| **owner_id** | int unsigned | YES | MUL | NULL |

#### Enums
- **transaction_type:** payment, invoice, expense, deposit, withdrawal, transfer, fee, refund, adjustment
- **source:** historical_import, manual_entry, bank_import, spreadsheet_import, system_migration, api_sync
- **transaction_level:** property, block, owner, portfolio
- **status:** active, cancelled, disputed, pending_review

#### Key Features
- **Multi-party support:** customer_id, beneficiary_id, tenant_id, owner_id
- **Lease linking:** invoice_id, lease_start_date, lease_end_date, rent_amount_at_transaction
- **Commission tracking:** commission_rate, commission_amount, service_fee_rate, service_fee_amount
- **PayProp integration:** payprop_transaction_id, payprop_property_id, etc.
- **Batch payments:** batch_payment_id, payprop_batch_id, batch_sequence_number
- **JSON original data:** original_row_data field

#### Indexes on historical_transactions
- **PRIMARY:** id
- **UNIQUE:** payprop_transaction_id
- **MUL:**
  - idx_historical_transactions_property (property_id)
  - idx_historical_transactions_customer (customer_id)
  - idx_historical_transactions_batch (import_batch_id)
  - idx_historical_transactions_date (transaction_date)
  - idx_historical_transactions_created_by (created_by_user_id)
  - idx_historical_transactions_updated_by (updated_by_user_id)
  - idx_historical_transactions_reconciled_by (reconciled_by_user_id)
  - idx_payment_source_id (payment_source_id)
  - idx_block_id (block_id)
  - **idx_historical_transactions_invoice_id** (invoice_id)
  - **idx_historical_transactions_lease_period** (property_id, lease_start_date, lease_end_date)
  - **idx_historical_transactions_date_invoice** (transaction_date, invoice_id)
  - fk_historical_beneficiary (beneficiary_id)
  - fk_historical_tenant (tenant_id)
  - fk_historical_owner (owner_id)

---

## Financial Transactions

### Table: `financial_transactions`

**Purpose:** PayProp-synced actual transactions with commission calculation

**Current Data:** 1002 records

#### Complete Schema
| Field | Type | Null | Key | Default |
|-------|------|------|-----|---------|
| id | bigint | NO | PRI | auto_increment |
| pay_prop_transaction_id | varchar(100) | YES | UNI | NULL |
| amount | decimal(10,2) | NO | | NULL |
| matched_amount | decimal(10,2) | YES | | NULL |
| transaction_date | date | NO | MUL | NULL |
| transaction_type | varchar(50) | YES | MUL | NULL |
| description | varchar(500) | YES | | NULL |
| reference | varchar(255) | YES | MUL | NULL |
| has_tax | tinyint(1) | YES | | 0 |
| tax_amount | decimal(10,2) | YES | | NULL |
| deposit_id | varchar(100) | YES | | NULL |
| property_id | varchar(100) | YES | MUL | NULL |
| property_name | varchar(255) | YES | | NULL |
| tenant_id | varchar(100) | YES | MUL | NULL |
| tenant_name | varchar(255) | YES | | NULL |
| category_id | varchar(100) | YES | | NULL |
| category_name | varchar(255) | YES | | NULL |
| commission_amount | decimal(10,2) | YES | MUL | NULL |
| commission_rate | decimal(5,2) | YES | | NULL |
| service_fee_amount | decimal(10,2) | YES | | NULL |
| net_to_owner_amount | decimal(10,2) | YES | | NULL |
| created_at | timestamp | YES | | CURRENT_TIMESTAMP |
| updated_at | timestamp | YES | | CURRENT_TIMESTAMP on update |
| data_source | varchar(50) | YES | MUL | NULL |
| instruction_id | varchar(100) | YES | MUL | NULL |
| reconciliation_date | date | YES | MUL | NULL |
| instruction_date | date | YES | | NULL |
| is_actual_transaction | tinyint(1) | YES | MUL | 0 |
| is_instruction | tinyint(1) | YES | MUL | 0 |
| actual_commission_amount | decimal(10,2) | YES | | NULL |
| calculated_commission_amount | decimal(10,2) | YES | | NULL |
| payprop_batch_id | varchar(255) | YES | MUL | NULL |
| batch_payment_id | bigint | YES | MUL | NULL |
| batch_sequence_number | int | YES | | NULL |
| payprop_beneficiary_type | enum | YES | | NULL |
| payprop_global_beneficiary | varchar(100) | YES | | NULL |
| payprop_use_money_from | enum | YES | | NULL |
| payprop_maintenance_ticket_id | varchar(32) | YES | | NULL |
| payprop_incoming_transaction_id | varchar(32) | YES | | NULL |
| payprop_parent_payment_id | varchar(32) | YES | | NULL |
| payprop_frequency | varchar(5) | YES | | NULL |
| payprop_percentage | decimal(5,2) | YES | | NULL |
| remittance_date | date | YES | | NULL |
| enabled | tinyint(1) | YES | | 1 |
| payprop_raw_payment_id | varchar(50) | YES | | NULL |
| payprop_lifecycle_link_id | bigint | YES | | NULL |
| **invoice_id** | bigint | YES | MUL | NULL |

#### Enums
- **payprop_beneficiary_type:** agency, beneficiary, global_beneficiary, property_account, deposit_account
- **payprop_use_money_from:** any_tenant, tenant, property_account

#### Key Features
- **PayProp native IDs:** property_id, tenant_id are varchar (PayProp IDs), not foreign keys
- **Commission tracking:** actual_commission_amount vs calculated_commission_amount
- **Data source tracking:** ICDN_ACTUAL, PAYMENT_INSTRUCTION, COMMISSION_PAYMENT
- **Batch payment support:** batch_payment_id, payprop_batch_id
- **Lease linking:** invoice_id (added to link to invoices table)
- **Maintenance integration:** payprop_maintenance_ticket_id
- **Lifecycle tracking:** payprop_lifecycle_link_id

#### Indexes on financial_transactions
- **PRIMARY:** id
- **UNIQUE:** pay_prop_transaction_id
- **MUL:**
  - idx_transaction_date (transaction_date)
  - idx_property_id (property_id)
  - idx_tenant_id (tenant_id)
  - idx_transaction_type (transaction_type)
  - idx_date_type (transaction_date, transaction_type)
  - idx_ft_property_date (property_id, transaction_date)
  - idx_ft_tenant_date (tenant_id, transaction_date)
  - idx_ft_type_date (transaction_type, transaction_date)
  - idx_ft_commission_calc (commission_amount)
  - idx_financial_transactions_data_source (data_source)
  - idx_financial_transactions_instruction_id (instruction_id)
  - idx_financial_transactions_reconciliation_date (reconciliation_date)
  - idx_financial_transactions_is_actual (is_actual_transaction)
  - idx_financial_transactions_is_instruction (is_instruction)
  - idx_payprop_batch_id (payprop_batch_id)
  - idx_batch_payment_id (batch_payment_id)
  - idx_reference (reference)
  - **idx_financial_transactions_invoice_id** (invoice_id)

---

## PayProp Integration Tables

### 1. `payprop_export_tenants_complete`

**Purpose:** Complete tenant data from PayProp /export/tenants endpoint

#### Schema (44 fields)
| Field | Type | Key | Description |
|-------|------|-----|-------------|
| payprop_id | varchar(50) | PRI | PayProp tenant ID |
| first_name | varchar(100) | | First name |
| last_name | varchar(100) | | Last name |
| business_name | varchar(255) | | Business name |
| display_name | varchar(255) | MUL | Display name |
| email | varchar(255) | MUL | Email address |
| email_cc | varchar(255) | | CC email |
| phone | varchar(50) | | Phone number |
| mobile | varchar(50) | | Mobile number |
| date_of_birth | date | | Date of birth |
| id_number | varchar(100) | | ID number |
| id_type | varchar(50) | | ID type |
| nationality | varchar(50) | | Nationality |
| occupation | varchar(100) | | Occupation |
| employer | varchar(255) | | Employer |
| address_id | varchar(50) | | Address ID |
| address_first_line | varchar(255) | | Address line 1 |
| address_second_line | varchar(255) | | Address line 2 |
| address_third_line | varchar(255) | | Address line 3 |
| address_city | varchar(100) | | City |
| address_state | varchar(100) | | State |
| address_country_code | varchar(5) | | Country code |
| address_postal_code | varchar(20) | | Postal code |
| address_zip_code | varchar(20) | | Zip code |
| address_phone | varchar(50) | | Address phone |
| address_email | varchar(255) | | Address email |
| emergency_contact_name | varchar(255) | | Emergency contact name |
| emergency_contact_phone | varchar(50) | | Emergency contact phone |
| emergency_contact_relationship | varchar(100) | | Emergency contact relationship |
| current_property_id | varchar(50) | MUL | Current property PayProp ID |
| current_deposit_id | varchar(50) | | Current deposit ID |
| tenancy_start_date | date | MUL | Tenancy start |
| tenancy_end_date | date | | Tenancy end |
| monthly_rent_amount | decimal(10,2) | MUL | Monthly rent |
| deposit_amount | decimal(10,2) | | Deposit amount |
| notify_email | tinyint(1) | | Email notification flag |
| notify_sms | tinyint(1) | | SMS notification flag |
| preferred_contact_method | varchar(20) | | Preferred contact |
| tenant_status | varchar(50) | | Tenant status |
| is_active | tinyint(1) | | Active flag |
| credit_score | decimal(5,2) | | Credit score |
| reference | varchar(255) | | Reference |
| comment | text | | Comments |
| properties_json | json | | Properties JSON array |
| imported_at | timestamp | | Import timestamp |
| sync_status | enum | | active, archived, error |

---

### 2. `payprop_incoming_payments`

**Purpose:** Extracted tenant payment records from PayProp, deduplicated and ready for sync to historical_transactions

#### Schema
| Field | Type | Null | Key | Default |
|-------|------|------|-----|---------|
| id | bigint | NO | PRI | auto_increment |
| incoming_transaction_id | varchar(50) | NO | UNI | NULL |
| amount | decimal(10,2) | NO | | NULL |
| reconciliation_date | date | NO | MUL | NULL |
| transaction_type | varchar(100) | YES | | NULL |
| transaction_status | varchar(50) | YES | | NULL |
| tenant_payprop_id | varchar(50) | YES | MUL | NULL |
| tenant_name | varchar(100) | YES | | NULL |
| property_payprop_id | varchar(50) | NO | MUL | NULL |
| property_name | text | YES | | NULL |
| deposit_id | varchar(50) | YES | | NULL |
| extracted_at | timestamp | YES | MUL | CURRENT_TIMESTAMP |
| synced_to_historical | tinyint(1) | YES | MUL | 0 |
| historical_transaction_id | bigint | YES | | NULL |
| sync_attempted_at | timestamp | YES | | NULL |
| sync_error | text | YES | | NULL |
| created_at | timestamp | YES | | CURRENT_TIMESTAMP |
| updated_at | timestamp | YES | | CURRENT_TIMESTAMP on update |

#### Key Features
- Deduplicates incoming payments from payprop_report_all_payments
- Tracks sync status to historical_transactions
- Links to created historical transaction via historical_transaction_id

---

### 3. `payprop_report_icdn`

**Purpose:** PayProp ICDN (Invoice/Credit/Debit Note) report data

#### Schema (29 fields)
| Field | Type | Key | Description |
|-------|------|-----|-------------|
| payprop_id | varchar(50) | PRI | PayProp transaction ID |
| transaction_type | varchar(50) | | Transaction type |
| amount | decimal(15,2) | MUL | Amount |
| gross_amount | decimal(15,2) | | Gross amount |
| net_amount | decimal(15,2) | | Net amount |
| transaction_date | date | MUL | Transaction date |
| description | text | | Description |
| reference | varchar(255) | | Reference |
| property_payprop_id | varchar(50) | MUL | Property PayProp ID |
| property_name | varchar(255) | | Property name |
| property_reference | varchar(100) | | Property reference |
| tenant_payprop_id | varchar(50) | MUL | Tenant PayProp ID |
| tenant_name | varchar(255) | | Tenant name |
| category_payprop_id | varchar(50) | | Category PayProp ID |
| category_name | varchar(100) | | Category name |
| commission_amount | decimal(10,2) | | Commission amount |
| commission_percentage | decimal(5,2) | | Commission percentage |
| service_fee | decimal(10,2) | | Service fee |
| transaction_fee | decimal(10,2) | | Transaction fee |
| tax_amount | decimal(10,2) | | Tax amount |
| transaction_status | varchar(50) | | Transaction status |
| processing_date | date | | Processing date |
| reconciliation_date | date | | Reconciliation date |
| settlement_date | date | | Settlement date |
| imported_at | timestamp | | Import timestamp |
| sync_status | enum | | active, processed, error |
| deposit_id | varchar(50) | MUL | Deposit ID |
| has_tax | tinyint(1) | | Has tax flag |
| invoice_group_id | varchar(50) | MUL | Invoice group ID |
| matched_amount | decimal(15,2) | | Matched amount |

---

### 4. `payprop_report_all_payments`

**Purpose:** Complete PayProp payment report with incoming transactions and payment instructions

#### Schema (38 fields)
| Field | Type | Key | Description |
|-------|------|-----|-------------|
| payprop_id | varchar(50) | PRI | PayProp payment ID |
| amount | decimal(10,2) | MUL | Payment amount |
| description | text | | Description |
| due_date | date | | Due date |
| has_tax | tinyint(1) | | Has tax |
| reference | varchar(100) | | Reference |
| service_fee | decimal(10,2) | | Service fee |
| transaction_fee | decimal(10,2) | | Transaction fee |
| tax_amount | decimal(10,2) | | Tax amount |
| part_of_amount | decimal(10,2) | | Part of amount |
| beneficiary_payprop_id | varchar(50) | | Beneficiary PayProp ID |
| beneficiary_name | varchar(100) | | Beneficiary name |
| beneficiary_type | varchar(50) | | Beneficiary type |
| category_payprop_id | varchar(50) | | Category PayProp ID |
| category_name | varchar(100) | | Category name |
| incoming_transaction_id | varchar(50) | | Incoming transaction ID |
| incoming_transaction_amount | decimal(10,2) | MUL | Incoming amount |
| incoming_transaction_deposit_id | varchar(50) | | Deposit ID |
| incoming_transaction_reconciliation_date | date | | Reconciliation date |
| incoming_transaction_status | varchar(50) | | Transaction status |
| incoming_transaction_type | varchar(100) | | Transaction type |
| bank_statement_date | date | | Bank statement date |
| bank_statement_id | varchar(50) | | Bank statement ID |
| incoming_property_payprop_id | varchar(50) | MUL | Property PayProp ID |
| incoming_property_name | text | | Property name |
| incoming_tenant_payprop_id | varchar(50) | MUL | Tenant PayProp ID |
| incoming_tenant_name | varchar(100) | | Tenant name |
| payment_batch_id | varchar(50) | | Batch ID |
| payment_batch_amount | decimal(10,2) | | Batch amount |
| payment_batch_status | varchar(50) | | Batch status |
| payment_batch_transfer_date | date | | Batch transfer date |
| payment_instruction_id | varchar(50) | MUL | Payment instruction ID |
| secondary_payment_is_child | tinyint(1) | | Is child payment |
| secondary_payment_is_parent | tinyint(1) | | Is parent payment |
| secondary_payment_parent_id | varchar(50) | | Parent payment ID |
| imported_at | timestamp | | Import timestamp |
| reconciliation_date | date | MUL | Reconciliation date |
| sync_status | enum | | active, processed, error |

**Key Features:**
- Contains both incoming tenant payments AND payment instructions
- Source for extracting payprop_incoming_payments
- Links payments to batches
- Supports hierarchical payments (parent/child)

---

### Other PayProp Tables in Database

| Table | Purpose |
|-------|---------|
| payprop_categories | PayProp category master data |
| payprop_entities | PayProp entity reference |
| payprop_export_beneficiaries | Beneficiary export data |
| payprop_export_beneficiaries_complete | Complete beneficiary data |
| payprop_export_incoming_payments | Raw incoming payment export |
| payprop_export_invoice_instructions | Invoice instruction export |
| payprop_export_invoices | Invoice export data |
| payprop_export_payments | Payment export data |
| payprop_export_properties | Property export data |
| payprop_export_tenants | Tenant export data (simplified) |
| payprop_import_issues | Import issue tracking |
| payprop_import_issues_summary | Import issues summary |
| payprop_invoice_categories | Invoice category mapping |
| payprop_invoice_rules | Invoice rule configuration |
| payprop_maintenance_categories | Maintenance category mapping |
| payprop_oauth_tokens | OAuth token storage |
| payprop_payment_rules | Payment rule configuration |
| payprop_payments_categories | Payment category mapping |
| payprop_report_agency_income | Agency income report |
| payprop_report_beneficiary_balances | Beneficiary balance report |
| payprop_report_processing_summary | Processing summary report |
| payprop_report_tenant_balances | Tenant balance report |
| payprop_report_tenant_statement | Tenant statement report |
| payprop_tag_links | Portfolio to PayProp tag links |
| payprop_tags | PayProp tag master data |
| payprop_webhook_log | Webhook event log |

---

## Import and Staging Tables

### 1. `transaction_import_staging`

**Purpose:** Multi-paste batch accumulation for reviewing transactions before committing to historical_transactions

#### Schema
| Field | Type | Null | Key | Default |
|-------|------|------|-----|---------|
| id | bigint | NO | PRI | auto_increment |
| batch_id | varchar(100) | NO | MUL | NULL |
| payment_source_id | bigint | YES | MUL | NULL |
| line_number | int | YES | | NULL |
| csv_line | text | YES | | NULL |
| transaction_date | date | YES | MUL | NULL |
| amount | decimal(12,2) | YES | | NULL |
| description | text | YES | | NULL |
| transaction_type | varchar(50) | YES | | NULL |
| category | varchar(100) | YES | | NULL |
| bank_reference | varchar(255) | YES | | NULL |
| payment_method | varchar(100) | YES | | NULL |
| notes | text | YES | | NULL |
| property_id | bigint | YES | MUL | NULL |
| customer_id | int unsigned | YES | MUL | NULL |
| status | varchar(50) | YES | MUL | NULL |
| is_duplicate | tinyint(1) | YES | | 0 |
| duplicate_of_transaction_id | bigint | YES | MUL | NULL |
| user_note | text | YES | | NULL |
| created_at | timestamp | YES | | CURRENT_TIMESTAMP |

**Status values:**
- PENDING_REVIEW
- APPROVED
- REJECTED
- AMBIGUOUS_PROPERTY
- AMBIGUOUS_CUSTOMER
- DUPLICATE

---

### 2. `import_batches`

**Purpose:** Tracks import batch operations with normalization status

#### Schema
| Field | Type | Null | Key | Default |
|-------|------|------|-----|---------|
| id | bigint | NO | PRI | auto_increment |
| batch_id | varchar(100) | NO | UNI | NULL |
| import_source | enum | NO | MUL | NULL |
| started_at | timestamp | YES | MUL | CURRENT_TIMESTAMP |
| completed_at | timestamp | YES | | NULL |
| imported_by_user_id | bigint | YES | | NULL |
| total_rows | int | YES | | 0 |
| successful_normalizations | int | YES | | 0 |
| failed_normalizations | int | YES | | 0 |
| skipped_rows | int | YES | | 0 |
| status | enum | YES | MUL | importing |
| source_filename | varchar(255) | YES | | NULL |
| source_description | text | YES | | NULL |
| import_config | json | YES | | NULL |

**import_source enum:**
- payprop_api
- csv_upload
- bank_statement
- spreadsheet
- manual_entry

**status enum:**
- importing
- normalizing
- completed
- error
- cancelled

---

### 3. `payment_sources`

**Purpose:** Tracks different sources of transaction imports

#### Schema
| Field | Type | Null | Key | Default |
|-------|------|------|-----|---------|
| id | bigint | NO | PRI | auto_increment |
| name | varchar(100) | NO | UNI | NULL |
| description | text | YES | | NULL |
| source_type | varchar(50) | YES | | NULL |
| created_at | timestamp | YES | | CURRENT_TIMESTAMP |
| created_by_user_id | int | YES | MUL | NULL |
| last_import_date | timestamp | YES | | NULL |
| total_transactions | int | YES | | 0 |
| is_active | tinyint(1) | YES | | 1 |

---

## Batch Payment System

### Table: `batch_payments`

**Purpose:** Groups financial transactions into batches for processing and reconciliation

#### Schema
| Field | Type | Null | Key | Default |
|-------|------|------|-----|---------|
| id | bigint | NO | PRI | auto_increment |
| payprop_batch_id | varchar(255) | YES | UNI | NULL |
| batch_date | date | YES | | NULL |
| status | varchar(50) | YES | | NULL |
| total_amount | decimal(10,2) | YES | | NULL |
| total_in | decimal(10,2) | YES | | NULL |
| total_out | decimal(10,2) | YES | | NULL |
| total_commission | decimal(10,2) | YES | | NULL |
| record_count | int | YES | | NULL |
| description | varchar(255) | YES | | NULL |
| processed_date | datetime | YES | | NULL |
| created_at | datetime | YES | | CURRENT_TIMESTAMP |
| updated_at | datetime | YES | | CURRENT_TIMESTAMP on update |
| payprop_synced | tinyint(1) | YES | | 0 |
| payprop_last_sync | datetime | YES | | NULL |
| payprop_webhook_received | datetime | YES | | NULL |
| bank_reference | varchar(255) | YES | | NULL |
| completed_date | datetime | YES | | NULL |
| currency | varchar(10) | YES | | GBP |
| customer_id | bigint | YES | | NULL |
| error_message | text | YES | | NULL |
| payment_count | int | YES | | 0 |
| payment_method | varchar(100) | YES | | NULL |
| processing_date | datetime | YES | | NULL |
| retry_count | int | YES | | 0 |
| webhook_received_at | datetime | YES | | NULL |

**Key Features:**
- Links to PayProp via payprop_batch_id
- Tracks totals: in, out, commission
- Webhook tracking
- Retry mechanism
- Status tracking

**Related via:**
- financial_transactions.batch_payment_id → batch_payments.id
- historical_transactions.batch_payment_id → batch_payments.id

---

## Database Indexes

### Summary of Key Indexes

#### Invoices Table
- **17 indexes total**
- Composite: idx_invoices_property_dates (property_id, start_date, end_date)
- Unique: payprop_id, lease_reference

#### Historical Transactions Table
- **20 indexes total**
- Composite indexes:
  - idx_historical_transactions_lease_period (property_id, lease_start_date, lease_end_date)
  - idx_historical_transactions_date_invoice (transaction_date, invoice_id)
- Unique: payprop_transaction_id
- Foreign key indexes: beneficiary_id, tenant_id, owner_id

#### Financial Transactions Table
- **23 indexes total**
- Multiple composite indexes for date+type, property+date, tenant+date combinations
- Unique: pay_prop_transaction_id
- Commission calculation index

---

## Key Relationships

### Invoice/Lease → Transaction Linking

```
TWO INVOICE SYSTEMS:
1. invoices (legacy, 43 records)
   └─[invoice_id]─> historical_transactions
   └─[invoice_id]─> financial_transactions

2. invoice_instructions (current PayProp-synced)
   └─ No direct FK links yet to transactions
```

### Historical Transactions Multi-Party Structure

```
historical_transactions
├─ customer_id → customers (primary customer, usually tenant)
├─ beneficiary_id → customers (payment recipient)
├─ tenant_id → customers (explicit tenant reference)
├─ owner_id → customers (property owner)
├─ property_id → properties
├─ block_id → blocks
├─ invoice_id → invoices (lease agreement link)
├─ payment_source_id → payment_sources
├─ import_staging_id → transaction_import_staging
└─ batch_payment_id → batch_payments
```

### Financial Transactions Structure

```
financial_transactions
├─ property_id: varchar(100) - PayProp ID, NOT FK
├─ tenant_id: varchar(100) - PayProp ID, NOT FK
├─ invoice_id → invoices (lease link, BIGINT FK)
├─ batch_payment_id → batch_payments
└─ payprop_lifecycle_link_id → payment_lifecycle_links
```

### PayProp Sync Flow

```
PayProp API
├─> payprop_report_icdn (ICDN transactions)
├─> payprop_report_all_payments (all payments + incoming)
│   └─> EXTRACT → payprop_incoming_payments
│       └─> SYNC → historical_transactions
│
├─> payprop_export_tenants_complete (tenant master)
├─> payprop_export_properties (property master)
├─> invoice_instructions (payment instructions)
└─> batch_payments (batch processing)
```

### Import Workflow

```
User Import
├─> transaction_import_staging (staging with batch_id)
│   ├─ Duplicate detection
│   ├─ Property/customer matching
│   └─ Status: PENDING_REVIEW → APPROVED
│
├─> import_batches (batch metadata)
│   └─ Status: importing → normalizing → completed
│
├─> payment_sources (source tracking)
│
└─> APPROVED records → historical_transactions
    └─ Links back via import_staging_id
```

---

## All Tables in Database

**Total: 135 tables**

### Core Transaction Tables
- batch_payments
- financial_transactions
- historical_transactions
- invoices
- invoice_instructions
- transaction_import_staging

### Import and Audit
- import_batches
- payment_sources
- normalization_log

### PayProp Integration (27 tables)
- payprop_categories
- payprop_entities
- payprop_export_beneficiaries
- payprop_export_beneficiaries_complete
- payprop_export_incoming_payments
- payprop_export_invoice_instructions
- payprop_export_invoices
- payprop_export_payments
- payprop_export_properties
- payprop_export_tenants
- payprop_export_tenants_complete
- payprop_import_issues
- payprop_import_issues_summary
- payprop_incoming_payments
- payprop_invoice_categories
- payprop_invoice_rules
- payprop_maintenance_categories
- payprop_oauth_tokens
- payprop_payment_rules
- payprop_payments_categories
- payprop_report_agency_income
- payprop_report_all_payments
- payprop_report_beneficiary_balances
- payprop_report_icdn
- payprop_report_processing_summary
- payprop_report_tenant_balances
- payprop_report_tenant_statement
- payprop_tag_links
- payprop_tags
- payprop_webhook_log

### Customer/Entity Tables
- customers
- customers_backup_20250809
- customers_backup_20251005
- customer_backup
- customer_folder_structures
- customer_login_info
- customer_property_assignments
- customer_property_assignments_backup_20251005
- customer_property_assignments_backup_20251027

### Property Tables
- properties
- properties_backup_20251005
- property_block_assignments
- property_mappings
- property_owner_email_cc
- property_owners
- property_owners_backup
- property_portfolio_assignments
- property_rent_sources

### Tenant/Beneficiary
- tenants
- tenants_backup
- tenant_balances
- tenant_email_cc
- beneficiaries
- beneficiary_balances

### Block/Portfolio
- blocks
- block_expenses
- block_portfolio_assignments
- block_service_charge_distributions
- portfolios
- portfolio_analytics
- portfolio_sync_log

### Maintenance/Tickets
- tickets
- ticket_messages
- ticket_settings
- ticket_sync_audit
- maintenance_tickets_sync_view
- maintenance_ticket_workflow
- maintenance_workflow_audit
- maintenance_payment_links
- maintenance_payments
- maintenance_sync_statistics

### Contractor Management
- contractors
- contractor_bids
- contractor_bids_payprop_sync
- contractor_services
- contracts
- contract_settings

### User Management
- users
- users_backup_20250809
- user_profile
- user_roles
- user_customer_security_fix
- roles
- oauth_users

### Payment Management
- payments
- payment_categories
- payment_lifecycle_links

### File Management
- file
- files
- file_access_log
- google_drive_file

### Reporting/Summaries
- financial_summary_by_month
- financial_summary_by_property
- google_sheets_statements

### Raw Data Imports
- raw_bank_statements
- raw_csv_imports
- raw_manual_entries
- raw_spreadsheet_imports

### Views (4 views)
- v_historical_totals
- v_monthly_summary_by_account
- v_property_monthly_detail
- v_transaction_audit_trail

### Triggers/Automation
- trigger_contract
- trigger_lead
- trigger_ticket

### Lead Management
- lead_action
- lead_settings

### Email
- email_template

### Other
- employee

---

## Key Insights and Observations

### 1. Dual Invoice Systems
- **Legacy:** `invoices` table (43 records) with rich feature set
- **Current:** `invoice_instructions` table (PayProp-synced)
- Both tables coexist - migration not complete
- Only `invoices` table has FK links to transactions via invoice_id

### 2. Transaction Volume
- **financial_transactions:** 1002 records (PayProp-synced)
- **historical_transactions:** 176 records (historical + manual)
- Financial transactions are the primary transaction source

### 3. PayProp Integration Depth
- 27 PayProp-specific tables
- Multiple report tables: ICDN, all_payments, agency_income, balances, tenant_statement
- Export tables for entities: tenants, properties, beneficiaries, invoices, payments
- Incoming payment extraction and deduplication system
- Webhook logging and OAuth token management

### 4. ID Type Mismatch
- **historical_transactions:** Uses BIGINT foreign keys for customer_id, property_id
- **financial_transactions:** Uses VARCHAR(100) for property_id, tenant_id (PayProp IDs)
- This suggests financial_transactions contains PayProp-native data
- Reconciliation between systems requires mapping PayProp IDs to local IDs

### 5. Commission Tracking
- Both tables support commission tracking
- financial_transactions has actual vs calculated commission
- historical_transactions has commission + service fees + net to owner

### 6. Comprehensive Indexing
- 60+ indexes across key tables
- Composite indexes for date+entity queries
- Proper foreign key indexes
- Performance-optimized for reporting queries

### 7. Batch Payment System
- Sophisticated batch processing
- Links financial_transactions and historical_transactions
- PayProp batch sync capability
- Webhook integration for real-time updates

---

**End of Verified Documentation**

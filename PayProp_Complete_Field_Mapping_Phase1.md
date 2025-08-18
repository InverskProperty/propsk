# PayProp Complete Field Mapping - Phase 1 Research
*Raw Database Schema Design - Mirror PayProp Structure Exactly*

## 🎯 RESEARCH OBJECTIVES

**Goal:** Document every field from every PayProp endpoint for exact database schema design  
**Approach:** Zero data loss - store exactly as PayProp returns  
**Status:** Phase 1 Research - Based on real API testing results

---

## 📋 ENDPOINT 1: `/export/properties`

### **API Details**
- **URL:** `GET /api/agency/v1.1/export/properties`
- **Parameters:** `?rows=25&page=1&include_commission=true`
- **Permissions:** `read:export:properties` ✅
- **Pagination:** Yes (standard PayProp pagination)

### **Complete Field Structure**
```yaml
# Root Level Properties
id: VARCHAR(50) PRIMARY KEY        # PayProp property ID
name: TEXT                         # Property display name
description: TEXT                  # Property description
create_date: TIMESTAMP             # When property was created in PayProp
modify_date: TIMESTAMP             # Last modified in PayProp  
start_date: TIMESTAMP              # Property start date
end_date: TIMESTAMP                # Property end date (usually 9999-12-31)
property_image: TEXT               # Image URL (often null)

# Address Object (Flattened)
address_id: VARCHAR(50)            # Address ID in PayProp
address_first_line: VARCHAR(100)   # Street address line 1
address_second_line: VARCHAR(100)  # Street address line 2 (can be null)
address_third_line: VARCHAR(100)   # Street address line 3 (can be null) 
address_city: VARCHAR(50)          # City
address_state: VARCHAR(50)         # State/region
address_country_code: VARCHAR(2)   # Country code (UK)
address_postal_code: VARCHAR(10)   # Postcode/ZIP
address_zip_code: VARCHAR(10)      # Alternative ZIP field
address_latitude: DECIMAL(10,8)    # GPS latitude (can be null)
address_longitude: DECIMAL(11,8)   # GPS longitude (can be null)
address_phone: VARCHAR(20)         # Address phone (can be null)
address_fax: VARCHAR(20)           # Address fax (can be null)  
address_email: VARCHAR(100)        # Address email (can be null)
address_created: TIMESTAMP         # Address creation date
address_modified: TIMESTAMP        # Address modification date

# Settings Object (Flattened) - CRITICAL FOR CURRENT SYSTEM
settings_monthly_payment: DECIMAL(10,2)     # £995 - Current source of rent amounts!
settings_enable_payments: BOOLEAN           # Payment processing enabled
settings_hold_owner_funds: BOOLEAN          # Hold funds for owner
settings_verify_payments: BOOLEAN           # Payment verification required
settings_minimum_balance: DECIMAL(10,2)     # Minimum account balance
settings_listing_from: DATE                 # Property listing start date
settings_approval_required: BOOLEAN         # Approval required for payments

# Commission Object (include_commission=true)
commission_percentage: DECIMAL(5,2)         # Commission rate (e.g., 15.00)
commission_amount: DECIMAL(10,2)           # Fixed commission amount
commission_id: VARCHAR(50)                 # Commission rule ID

# Metadata Fields
imported_at: TIMESTAMP DEFAULT CURRENT_TIMESTAMP
payprop_last_modified: TIMESTAMP
sync_status: ENUM('active', 'archived', 'error')
```

### **Current Usage Analysis**
- ✅ **PayPropEntityResolutionService**: Uses `settings.monthly_payment` → Property.monthlyPayment
- ✅ **PayPropFinancialSyncService**: Uses `commission` data
- ❌ **Missing**: Complete address structure storage
- ❌ **Missing**: Settings object complete preservation

### **Research Gaps**
- [ ] **Confirm all settings fields** (need live API call)
- [ ] **Verify commission object structure** with `include_commission=true`
- [ ] **Test pagination behavior** under high data volume
- [ ] **Document optional vs required fields**

---

## 📋 ENDPOINT 2: `/export/invoices`

### **API Details**  
- **URL:** `GET /api/agency/v1.1/export/invoices`
- **Parameters:** `?include_categories=true&rows=25&page=1`
- **Permissions:** `read:export:invoices` ✅
- **Pagination:** Yes (standard PayProp pagination)

### **Complete Field Structure**
```yaml
# Root Level Invoice Instruction
id: VARCHAR(50) PRIMARY KEY        # PayProp invoice instruction ID
account_type: VARCHAR(50)          # "direct deposit", "cash", etc.
debit_order: BOOLEAN               # Direct debit enabled
description: TEXT                  # Invoice description
frequency: VARCHAR(20)            # "Monthly", "Quarterly", "Annual"
frequency_code: VARCHAR(1)        # "M", "Q", "A"
from_date: DATE                   # Schedule start date
to_date: DATE                     # Schedule end date (can be null)
gross_amount: DECIMAL(10,2)       # £1,075 - THE AUTHORITATIVE RENT AMOUNT!
payment_day: INTEGER              # Day of month payment due (1-31)
invoice_type: VARCHAR(50)         # "Rent", "Service Charge", etc.
reference: TEXT                   # Payment reference
vat: BOOLEAN                      # VAT applicable
vat_amount: DECIMAL(10,2)         # VAT amount

# Category Object (Flattened)
category_id: VARCHAR(50)          # Links to /invoices/categories
category_name: VARCHAR(100)       # "Rent", "Deposit", etc.

# Property Object Reference (DO NOT DUPLICATE - REFERENCE ONLY)
property_payprop_id: VARCHAR(50)  # Links to payprop_export_properties.id
property_name: TEXT               # For display only - don't store separately

# Tenant Object Reference (DO NOT DUPLICATE - REFERENCE ONLY) 
tenant_payprop_id: VARCHAR(50)    # Links to payprop_export_tenants.id
tenant_display_name: VARCHAR(100) # For display only - don't store separately
tenant_email: VARCHAR(100)        # For display only - don't store separately
tenant_business_name: VARCHAR(100) # For business tenants
tenant_first_name: VARCHAR(50)    # Individual tenant first name
tenant_last_name: VARCHAR(50)     # Individual tenant last name

# Metadata Fields
imported_at: TIMESTAMP DEFAULT CURRENT_TIMESTAMP
payprop_last_modified: TIMESTAMP
sync_status: ENUM('active', 'archived', 'error')
is_active_instruction: BOOLEAN     # Derived - is this currently active?
```

### **Critical Relationships**
- ✅ **property_payprop_id** → `payprop_export_properties.id`
- ✅ **category_id** → `payprop_invoice_categories.id`  
- ✅ **tenant_payprop_id** → `payprop_export_tenants.id`

### **Current Usage Analysis**
- ❌ **PayPropFinancialSyncService**: Discovery only - no processing!
- ❌ **Missing**: Complete invoice instruction storage
- ❌ **Critical Gap**: No link between Property.monthlyPayment and invoice instructions

### **Research Gaps**  
- [ ] **Document complete property object** (may contain additional fields)
- [ ] **Document complete tenant object** (may contain additional fields)
- [ ] **Test with different invoice types** (Rent, Deposit, Maintenance, etc.)
- [ ] **Verify category object completeness**

---

## 📋 ENDPOINT 3: `/report/all-payments`

### **API Details**
- **URL:** `GET /api/agency/v1.1/report/all-payments`  
- **Parameters:** `?from_date=2025-05-19&to_date=2025-07-18&filter_by=reconciliation_date&rows=25`
- **Permissions:** `read:report:all-payments` ✅
- **Rate Limit:** 93-day maximum date range
- **Pagination:** Yes (standard PayProp pagination)

### **Complete Field Structure**
```yaml
# Root Level Payment Transaction
id: VARCHAR(50) PRIMARY KEY        # PayProp payment transaction ID
amount: DECIMAL(10,2)              # Payment amount (commission/fee portion)
description: TEXT                 # Payment description
due_date: DATE                    # Payment due date
has_tax: BOOLEAN                  # Tax applicable to this payment
reference: VARCHAR(100)           # Payment reference number
service_fee: DECIMAL(10,2)        # PayProp service fee (£0.54)
transaction_fee: DECIMAL(10,2)    # Payment processing fee (£0.32)
tax_amount: DECIMAL(10,2)         # Tax amount (£2.58)
part_of_amount: DECIMAL(10,2)     # Partial payment amount

# Beneficiary Object (Flattened)
beneficiary_id: VARCHAR(50)       # Beneficiary PayProp ID
beneficiary_name: VARCHAR(100)    # Beneficiary name
beneficiary_type: VARCHAR(50)     # "agency", "property_owner", "tenant", etc.

# Category Object (Flattened)
category_id: VARCHAR(50)          # Payment category ID
category_name: VARCHAR(100)       # "Commission", "Rent", "Deposit", etc.

# Incoming Transaction Object (The Original Payment)
incoming_transaction_id: VARCHAR(50)           # Source transaction ID
incoming_transaction_amount: DECIMAL(10,2)     # Original payment amount (£126.00)
incoming_transaction_deposit_id: VARCHAR(50)   # Deposit batch ID
incoming_transaction_reconciliation_date: DATE # When processed
incoming_transaction_status: VARCHAR(50)       # "paid", "pending", "failed"
incoming_transaction_type: VARCHAR(100)        # "instant bank transfer", "direct debit"

# Incoming Transaction - Bank Statement
bank_statement_date: DATE         # Bank statement date
bank_statement_id: VARCHAR(50)    # Bank statement reference

# Incoming Transaction - Property Reference
incoming_property_id: VARCHAR(50)     # Property PayProp ID
incoming_property_name: TEXT          # Property name for display

# Incoming Transaction - Tenant Reference  
incoming_tenant_id: VARCHAR(50)       # Tenant PayProp ID
incoming_tenant_name: VARCHAR(100)    # Tenant name for display

# Payment Batch Object (Flattened)
payment_batch_id: VARCHAR(50)     # Batch ID for grouped payments
payment_batch_amount: DECIMAL(10,2) # Batch total amount
payment_batch_status: VARCHAR(50)   # "not approved", "approved", "paid"
payment_batch_transfer_date: DATE   # When batch was processed

# Payment Instruction Link (CRITICAL RELATIONSHIP)
payment_instruction_id: VARCHAR(50) # Links to payprop_export_invoices.id

# Secondary Payment Structure
secondary_payment_is_child: BOOLEAN      # Is this a child payment?
secondary_payment_is_parent: BOOLEAN     # Is this a parent payment?
secondary_payment_parent_id: VARCHAR(50) # Parent payment ID

# Metadata Fields
imported_at: TIMESTAMP DEFAULT CURRENT_TIMESTAMP
reconciliation_date: DATE         # When payment was reconciled
sync_status: ENUM('active', 'processed', 'error')
```

### **Critical Relationships**
- ✅ **payment_instruction_id** → `payprop_export_invoices.id` (Links Stage 2 to Stage 1)
- ✅ **incoming_property_id** → `payprop_export_properties.id`
- ✅ **incoming_tenant_id** → `payprop_export_tenants.id`
- ✅ **beneficiary_id** → `payprop_export_beneficiaries.id`

### **Current Usage Analysis**
- ✅ **PayPropFinancialSyncService**: Processes into FinancialTransaction table
- ❌ **Missing**: Complete fee structure preservation
- ❌ **Missing**: Payment instruction linking
- ❌ **Missing**: Batch information storage

### **Research Gaps**
- [ ] **Document all possible beneficiary_type values**
- [ ] **Document all possible payment status values**
- [ ] **Test with different payment types** (DD, bank transfer, etc.)
- [ ] **Verify secondary payment relationships**

---

## 📋 ENDPOINT 4: `/export/payments`

### **API Details**
- **URL:** `GET /api/agency/v1.1/export/payments`
- **Parameters:** `?include_beneficiary_info=true&rows=25&page=1`
- **Permissions:** `read:export:payments` ✅
- **Pagination:** Yes (standard PayProp pagination)

### **Complete Field Structure**
```yaml
# Root Level Payment Distribution Rule
id: VARCHAR(50) PRIMARY KEY        # PayProp distribution rule ID
beneficiary: VARCHAR(200)          # "Natalie Turner [B]" - display name
beneficiary_reference: VARCHAR(100) # Bank reference "53997590"
category: VARCHAR(100)             # "Owner", "Agent", "Contractor" 
category_id: VARCHAR(50)           # Category ID
description: TEXT                  # Rule description
enabled: BOOLEAN                   # Rule is active
frequency: VARCHAR(20)             # "Monthly", "Quarterly"
frequency_code: VARCHAR(1)         # "M", "Q", "A"
from_date: DATE                    # Rule effective start date
to_date: DATE                      # Rule end date (can be null)
gross_amount: DECIMAL(10,2)        # Fixed amount (usually 0 for percentages)
gross_percentage: DECIMAL(5,2)     # Percentage of payment (100.00 = 100%)
group_id: VARCHAR(50)              # Payment group ID (can be null)
maintenance_ticket_id: VARCHAR(50) # Related maintenance ticket (can be null)
no_commission: BOOLEAN             # Exempt from commission calculation
no_commission_amount: DECIMAL(10,2) # Commission-free amount
payment_day: INTEGER               # Day of month for distribution (0 = immediate)
reference: TEXT                    # Payment reference
vat: BOOLEAN                       # VAT applicable
vat_amount: DECIMAL(10,2)          # VAT amount

# Property Object Reference (DO NOT DUPLICATE)
property_payprop_id: VARCHAR(50)   # Links to payprop_export_properties.id
property_name: TEXT                # For display only

# Tenant Object Reference (can be null for owner payments)
tenant_payprop_id: VARCHAR(50)     # Links to payprop_export_tenants.id (can be null)
tenant_name: VARCHAR(100)          # For display only (can be null)

# Metadata Fields
imported_at: TIMESTAMP DEFAULT CURRENT_TIMESTAMP
payprop_last_modified: TIMESTAMP
sync_status: ENUM('active', 'archived', 'error')
rule_priority: INTEGER             # Processing order for multiple rules
```

### **Critical Relationships**
- ✅ **property_payprop_id** → `payprop_export_properties.id`
- ✅ **tenant_payprop_id** → `payprop_export_tenants.id` (optional)
- ✅ **category_id** → Links to payment categories

### **Current Usage Analysis**
- ✅ **PayPropFinancialSyncService**: Processes payment distribution rules
- ❌ **Missing**: Complete rule storage with property linking
- ❌ **Missing**: Commission calculation integration

### **Research Gaps**
- [ ] **Document all possible category values**
- [ ] **Test with different rule types** (percentage vs fixed amount)
- [ ] **Document beneficiary_reference format patterns**
- [ ] **Verify group_id usage patterns**

---

## 📋 ENDPOINT 5: `/export/beneficiaries`

### **API Details**
- **URL:** `GET /api/agency/v1.1/export/beneficiaries`
- **Parameters:** `?owners=true&rows=25&page=1`
- **Permissions:** `read:export:beneficiaries` ✅
- **Pagination:** Yes (standard PayProp pagination)

### **Complete Field Structure**
```yaml
# Root Level Beneficiary
id: VARCHAR(50) PRIMARY KEY        # PayProp beneficiary ID
account_type: VARCHAR(50)          # "individual", "business", "company"
payment_method: VARCHAR(50)        # "bank_transfer", "check", "cash"
first_name: VARCHAR(100)           # Individual first name
last_name: VARCHAR(100)            # Individual last name  
business_name: VARCHAR(255)        # Business name (for companies)
email_address: VARCHAR(255)        # Primary email
mobile: VARCHAR(25)                # Mobile phone number
phone: VARCHAR(25)                 # Landline phone number
fax: VARCHAR(25)                   # Fax number
customer_id: VARCHAR(50)           # PayProp customer ID
customer_reference: VARCHAR(100)   # Customer reference number
comment: TEXT                      # Notes/comments
id_number: VARCHAR(50)             # National ID/SSN
vat_number: VARCHAR(50)            # VAT registration number

# Address Object (Flattened)
address_line_1: VARCHAR(255)       # Street address line 1
address_line_2: VARCHAR(255)       # Street address line 2
address_line_3: VARCHAR(255)       # Street address line 3
city: VARCHAR(100)                 # City
state: VARCHAR(100)                # State/Province
postal_code: VARCHAR(20)           # Postal/ZIP code
country_code: VARCHAR(10)          # Country code (GB, US, etc.)

# Bank Account Object (Flattened)
bank_account_name: VARCHAR(255)    # Account holder name
bank_account_number: VARCHAR(50)   # Account number
bank_branch_code: VARCHAR(20)      # Sort code/routing number
bank_name: VARCHAR(100)            # Bank name
bank_branch_name: VARCHAR(100)     # Branch name
bank_iban: VARCHAR(50)             # IBAN
bank_swift_code: VARCHAR(20)       # SWIFT/BIC code
bank_country_code: VARCHAR(10)     # Bank country code

# Communication Preferences Object (Flattened)
email_enabled: BOOLEAN              # Email notifications enabled
email_payment_advice: BOOLEAN       # Payment advice emails enabled

# Metadata Fields
imported_at: TIMESTAMP DEFAULT CURRENT_TIMESTAMP
last_modified_at: TIMESTAMP
sync_status: ENUM('active', 'archived', 'error')
```

### **Current Usage Analysis**
- ✅ **Current Beneficiary Entity**: Has comprehensive business logic structure
- ✅ **PayPropBeneficiaryDTO**: Complete raw API structure documented
- ✅ **Field Mapping**: All major fields covered in existing system
- ❌ **Missing**: Raw mirror table for complete PayProp data preservation

### **Research Complete** ✅
- ✅ **Complete field structure documented** from existing DTO
- ✅ **Bank account information fully mapped**
- ✅ **Communication preferences documented**
- ✅ **Address structure complete**

---

## 📋 ENDPOINT 6: `/export/tenants`

### **API Details**
- **URL:** `GET /api/agency/v1.1/export/tenants`
- **Parameters:** `?rows=25&page=1`
- **Permissions:** `read:export:tenants` ✅
- **Pagination:** Yes (standard PayProp pagination)

### **Complete Field Structure**
```yaml
# Root Level Tenant
id: VARCHAR(50) PRIMARY KEY        # PayProp tenant ID
account_type: VARCHAR(50)          # "individual", "business", "company"
first_name: VARCHAR(100)           # Individual first name
last_name: VARCHAR(100)            # Individual last name
business_name: VARCHAR(255)        # Business name (for companies)
email_address: VARCHAR(255)        # Primary email
mobile_number: VARCHAR(25)         # Mobile phone number
phone: VARCHAR(25)                 # Landline phone number
fax: VARCHAR(25)                   # Fax number
customer_id: VARCHAR(50)           # PayProp customer ID
customer_reference: VARCHAR(100)   # Customer reference number
comment: TEXT                      # Notes/comments
date_of_birth: DATE               # Date of birth (individuals only)
id_number: VARCHAR(50)             # National ID/SSN
vat_number: VARCHAR(50)            # VAT registration number
notify_email: BOOLEAN              # Email notifications enabled
notify_sms: BOOLEAN                # SMS notifications enabled
has_bank_account: BOOLEAN          # Whether tenant has bank account details

# Address Object (Flattened)
address_line_1: VARCHAR(255)       # Street address line 1
address_line_2: VARCHAR(255)       # Street address line 2
address_line_3: VARCHAR(255)       # Street address line 3
city: VARCHAR(100)                 # City
state: VARCHAR(100)                # State/Province
postal_code: VARCHAR(20)           # Postal/ZIP code
country_code: VARCHAR(10)          # Country code (GB, US, etc.)

# Bank Account Object (Flattened - Optional)
bank_account_name: VARCHAR(255)    # Account holder name
bank_account_number: VARCHAR(50)   # Account number
bank_branch_code: VARCHAR(20)      # Sort code/routing number
bank_name: VARCHAR(100)            # Bank name
bank_branch_name: VARCHAR(100)     # Branch name
bank_iban: VARCHAR(50)             # IBAN
bank_swift_code: VARCHAR(20)       # SWIFT/BIC code
bank_country_code: VARCHAR(10)     # Bank country code

# Metadata Fields
imported_at: TIMESTAMP DEFAULT CURRENT_TIMESTAMP
last_modified_at: TIMESTAMP
sync_status: ENUM('active', 'archived', 'error')
```

### **Current Usage Analysis**
- ✅ **PayPropTenantDTO**: Complete raw API structure documented
- ✅ **Field Mapping**: All major tenant fields covered
- ✅ **Bank Account Integration**: Optional bank details fully mapped
- ❌ **Missing**: Raw mirror table for complete PayProp data preservation

### **Research Complete** ✅
- ✅ **Complete field structure documented** from existing DTO
- ✅ **Personal vs business tenant fields mapped**
- ✅ **Contact information fields complete**
- ✅ **Bank account details optional structure**

---

## 📋 ENDPOINT 7: `/invoices/categories`

### **API Details**
- **URL:** `GET /api/agency/v1.1/invoices/categories`
- **Parameters:** None required
- **Permissions:** `read:invoices:categories` ✅
- **Pagination:** Not required (reference data)

### **Complete Field Structure**
```yaml
# Reference Table - Categories
id: VARCHAR(50) PRIMARY KEY        # "Vv2XlY1ema"
name: VARCHAR(100) NOT NULL        # "Rent", "Deposit", "Maintenance"
is_system: BOOLEAN                 # System vs custom categories

# Metadata Fields
imported_at: TIMESTAMP DEFAULT CURRENT_TIMESTAMP
sync_status: ENUM('active', 'archived')
```

### **Current Usage Analysis**
- ✅ **PayPropFinancialSyncService**: Syncs categories
- ✅ **Reference data**: Used across invoices and payments
- ✅ **Complete**: Structure fully documented

---

## 🔗 CRITICAL RELATIONSHIP MAPPING

### **Primary Relationships**
```yaml
# Property-Centric Relationships
payprop_export_properties.id (PRIMARY)
  ← payprop_export_invoices.property_payprop_id
  ← payprop_report_all_payments.incoming_property_id  
  ← payprop_export_payments.property_payprop_id

# Invoice → Payment Relationships  
payprop_export_invoices.id (PRIMARY)
  ← payprop_report_all_payments.payment_instruction_id

# Tenant Relationships
payprop_export_tenants.id (PRIMARY)
  ← payprop_export_invoices.tenant_payprop_id
  ← payprop_report_all_payments.incoming_tenant_id
  ← payprop_export_payments.tenant_payprop_id

# Category Relationships
payprop_invoice_categories.id (PRIMARY)
  ← payprop_export_invoices.category_id
  ← payprop_report_all_payments.category_id
```

### **Referential Integrity Rules**
- ✅ **Properties must exist** before invoices/payments
- ✅ **Categories must exist** before invoices/payments  
- ⚠️ **Tenants may be optional** for some payment types
- ⚠️ **Payment instructions may not exist** for historical payments

---

## 🚨 RESEARCH PRIORITIES

### **HIGH PRIORITY (Complete This Week)**
1. **Document complete `/export/beneficiaries` structure**
2. **Document complete `/export/tenants` structure**  
3. **Verify all optional vs required fields**
4. **Test pagination behavior with large datasets**

### **MEDIUM PRIORITY (Next Week)**
1. **Document all enum/choice field values**
2. **Verify relationship consistency across endpoints**
3. **Test rate limiting and performance characteristics**
4. **Document error response structures**

### **LOW PRIORITY (Future)**
1. **Document webhook payload structures**
2. **Test with different PayProp configurations**
3. **Document API versioning implications**

---

## ✅ PHASE 1 COMPLETION CRITERIA

- [ ] **100% field coverage** for all 7 endpoints
- [ ] **Complete relationship mapping** between all entities
- [ ] **Data type validation** for all fields
- [ ] **Constraint documentation** (required/optional, lengths, patterns)
- [ ] **Sample data collection** for testing database schemas

**Next Phase:** Database schema design using this complete field mapping.
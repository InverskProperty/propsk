# PayProp Data Structure Analysis
## Complete Financial Transaction Ecosystem

*Generated from analysis of staging database (ballast.proxy.rlwy.net) and CSV import to production database (switchyard.proxy.rlwy.net)*

---

## 🚀 **QUICK START: CSV IMPORT REQUIREMENTS**

### **MANDATORY CSV FORMAT SUMMARY**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes
2025-01-27,795.00,"Rent payment - January",deposit,rent,"Flat 1 - 3 West Gate","Ms O Smoliarenko","RE-JAN-01","Robert Ellis","Monthly collection"
2025-01-27,-119.25,"Commission - January",fee,commission,"Flat 1 - 3 West Gate","Ms O Smoliarenko","COMM-JAN-01","Robert Ellis","15% management fee"
```

### **CRITICAL REQUIREMENTS**
- ✅ **Date Format**: YYYY-MM-DD (e.g., 2025-01-27)
- ✅ **Amount Format**: Decimal without currency symbols (e.g., 795.00)
- ✅ **Property Names**: Must match exact database entries (see complete list below)
- ✅ **Transaction Types**: Only `deposit`, `fee`, `payment`, `expense`
- ✅ **Commission Pairs**: Every rent payment MUST have corresponding negative commission entry
- ✅ **Encoding**: UTF-8 with comma separation

### **VALIDATION CHECKLIST**
Before import, ensure:
- [ ] All property references exist in the valid list (31 properties available: 29 flats + 2 parking)
- [ ] Rent/parking payments are positive, commissions/expenses are negative
- [ ] **CRITICAL**: Each rent/parking payment has a matching commission deduction on same date
- [ ] No currency symbols (£, $) in amounts
- [ ] Dates are YYYY-MM-DD format only
- [ ] Categories match allowed values (rent, parking, commission, expense, maintenance, fire_safety, white_goods, clearance, furnishing)
- [ ] Payment methods use valid values (Robert Ellis, PayProp, Direct Payment, etc.)
- [ ] Bank references follow standardized patterns
- [ ] Commission calculations are accurate (15%, 9%, or 3%)
- [ ] End-of-tenancy and partial payments clearly described

---

## Executive Summary

PayProp operates a **dual-layer financial system** that separates billing/invoicing from actual payment processing. Understanding this distinction is crucial for implementing property management financial reports and ensuring data consistency across systems.

**Key Finding:** PayProp maintains separate records for:
1. **Invoices** - What tenants owe (billing records)
2. **Actual Payments** - What tenants actually paid (cash flow records)

---

## Database Architecture Overview

### Primary Tables Analyzed

#### 1. `financial_transactions` - Core Transaction Log
- **Purpose**: Records all financial movements (both invoices and payments)
- **Record Count**: 13,249 transactions
- **Key Fields**: `transaction_type`, `amount`, `commission_amount`, `net_to_owner_amount`

#### 2. `payprop_report_all_payments` - Actual Payment Processing
- **Purpose**: Records real money movements from tenants through PayProp system
- **Record Count**: 7,325 payment records
- **Key Fields**: `incoming_transaction_amount`, `payment_batch_transfer_date`, `beneficiary_type`

---

## Transaction Type Taxonomy

### Complete Transaction Type Analysis (from `financial_transactions`)

| Transaction Type | Count | Min Amount | Max Amount | Avg Amount | Purpose |
|-----------------|-------|------------|------------|------------|---------|
| `payment_to_agency` | 3,369 | £0.00 | £1,200.00 | £94.60 | Commission payments to property management agency |
| `invoice` | 2,971 | £0.02 | £16,225.00 | £1,050.97 | **Billing records** - charges to tenants |
| `commission_payment` | 2,828 | £0.00 | £3,080.00 | £132.46 | Commission calculations and payments |
| `payment_to_beneficiary` | 2,825 | -£1,175.18 | £16,870.16 | £816.90 | **Net payments to property owners** |
| `payment_to_contractor` | 660 | -£234.76 | £5,400.00 | £205.28 | Maintenance and repair payments |
| `deposit` | 391 | -£1,615.38 | £4,038.46 | £745.28 | Security deposit handling |
| `credit_note` | 137 | £0.01 | £6,150.00 | £999.05 | Refunds and adjustments (positive) |
| `debit_note` | 68 | £0.08 | £2,250.00 | £431.10 | Charges and adjustments (negative) |

---

## Invoice vs Payment Distinction

### 1. Invoice Records (`transaction_type = 'invoice'`)
**Purpose**: Billing/charging system - what tenants owe

```sql
-- Example invoice record
transaction_type: 'invoice'
amount: 1075.00
commission_amount: 161.25
commission_rate: 15.00
net_to_owner_amount: 860.00
category_name: 'Rent'
description: '' (typically empty)
```

**Category Breakdown:**
- `Rent`: 2,583 records (main rental charges)
- `Deposit`: 242 records (security deposits)
- `Other`: 135 records (miscellaneous charges)
- `Professional Services fee`: 7 records
- `Maintenance`: 1 record
- `Rates & Taxes`: 1 record

### 2. Actual Payment Records (`payprop_report_all_payments`)
**Purpose**: Real money movement tracking

```sql
-- Example payment flow for £705 tenant payment
incoming_transaction_amount: 705.00
incoming_tenant_name: 'Maxwell Chantelle'
incoming_property_name: 'Harben Road, Osterley Park, Croydon'
incoming_transaction_type: 'incoming payment'
payment_batch_transfer_date: '2024-11-15'

-- This creates multiple outgoing payments:
1. beneficiary_type: 'agency'      amount: 76.14    (10.8% commission)
2. beneficiary_type: 'beneficiary' amount: 637.32   (90.4% to owner)
3. beneficiary_type: 'global_beneficiary' (property account handling)
```

---

## Payment Flow Architecture

### Complete Payment Lifecycle

```
┌─────────────────────┐
│   Tenant Pays      │
│   £795 Rent        │
└──────────┬──────────┘
           │
           v
┌─────────────────────┐
│ PayProp Receives    │
│ incoming_transaction│
│ amount: £795        │
└──────────┬──────────┘
           │
           v
┌─────────────────────┐
│ PayProp Splits      │
│ Into 3 Payments:    │
├─────────────────────┤
│ 1. Agency: £119.25  │
│    (15% commission) │
│ 2. Owner: £675.75   │
│    (85% net)        │
│ 3. Property Account │
│    (handling/fees)  │
└─────────────────────┘
```

### Beneficiary Type Analysis

From `payprop_report_all_payments`:

| Beneficiary Type | Count | Avg Amount | Purpose |
|-----------------|-------|------------|---------|
| `agency` | 3,534 | £92.45 | Commission payments to property management |
| `beneficiary` | 3,371 | £714.73 | **Net payments to property owners** |
| `global_beneficiary` | 420 | £857.05 | PayProp system accounts (deposits, etc.) |

---

## Commission Structure Analysis

### Commission Rate Distribution (from invoices)

```sql
-- Actual commission rates found in data:
15.00% - Most common rate
9.00%  - Alternative rate
3.00%  - Lower rate (specific properties)
```

### Commission Calculation Examples

| Rent Amount | Commission Rate | Commission Amount | Net to Owner |
|-------------|-----------------|-------------------|--------------|
| £1,075.00 | 15.00% | £161.25 | £860.00 |
| £1,050.00 | 9.00% | £94.50 | £903.00 |
| £1,115.00 | 9.00% | £100.35 | £958.90 |
| £1,150.00 | 3.00% | £34.50 | £1,058.00 |

---

## **REQUIRED CSV FORMAT FOR HISTORICAL IMPORT**

### **MANDATORY CSV STRUCTURE**

**File Requirements:**
- **Filename**: `historical_transactions.csv`
- **Encoding**: UTF-8
- **Date Format**: YYYY-MM-DD
- **Amount Format**: Decimal (e.g., 795.00, not £795)
- **Header Row**: REQUIRED (exactly as shown below)

```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes
2025-01-27,795.00,"Rent payment - January",deposit,rent,"Flat 1 - 3 West Gate","Ms O Smoliarenko & Mr I Halii","RE-JAN-01","Robert Ellis","Monthly collection"
2025-01-27,-119.25,"Commission - January","fee","commission","Flat 1 - 3 West Gate","Ms O Smoliarenko & Mr I Halii","RE-JAN-01","Robert Ellis","15% commission"
2025-02-15,-450.00,"Plumber repair","payment","maintenance","Flat 2 - 3 West Gate","","MAINT-001","Direct Payment","Emergency repair"
```

### **REQUIRED COLUMN SPECIFICATIONS**

| Column | Type | Required | Format | Example | Notes |
|--------|------|----------|--------|---------|-------|
| `transaction_date` | Date | YES | YYYY-MM-DD | `2025-01-27` | Must be valid date |
| `amount` | Decimal | YES | 0.00 format | `795.00` | Positive for income, negative for expenses |
| `description` | Text | YES | Max 500 chars | `"Rent payment - January"` | Quoted if contains commas |
| `transaction_type` | Enum | YES | See allowed values | `deposit` | Must match allowed types |
| `category` | Text | YES | Max 100 chars | `rent` | Used for classification |
| `property_reference` | Text | YES | Max 255 chars | `"Flat 1 - 3 West Gate"` | Must match existing properties |
| `customer_reference` | Text | NO | Max 255 chars | `"Ms O Smoliarenko"` | Tenant/customer name |
| `bank_reference` | Text | NO | Max 100 chars | `"RE-JAN-01"` | Bank transaction reference |
| `payment_method` | Text | NO | Max 100 chars | `"Robert Ellis"` | Payment collection method |
| `notes` | Text | NO | Max 500 chars | `"Monthly collection"` | Additional notes |

### **ALLOWED TRANSACTION TYPES**

| CSV Type | Maps to PayProp | Purpose | Amount Sign | Allowed Categories |
|----------|-----------------|---------|-------------|-------------------|
| `deposit` | `invoice` | Rent payments FROM tenants | **Positive** | `rent`, `parking` |
| `fee` | `commission_payment` | Agency commission deductions | **Negative** | `commission` |
| `payment` | `payment_to_beneficiary` | General payments TO others | **Negative** | `expense`, `maintenance`, `fire_safety`, `white_goods`, `clearance`, `furnishing` |
| `expense` | `payment_to_contractor` | Maintenance/contractor payments | **Negative** | `maintenance`, `fire_safety`, `white_goods`, `clearance`, `furnishing` |

### **COMPREHENSIVE CATEGORY SPECIFICATIONS**

**✅ INCOME CATEGORIES (Positive Amounts):**
- `rent` - Regular rental payments from tenants
- `parking` - Parking space rental fees

**✅ COMMISSION CATEGORIES (Negative Amounts):**
- `commission` - Management fee deductions (typically 15%, 9%, or 3%)

**✅ EXPENSE CATEGORIES (Negative Amounts):**
- `expense` - General property-related expenses
- `maintenance` - Routine maintenance and repairs
- `fire_safety` - Fire safety equipment and inspections
- `white_goods` - Appliances (washing machines, refrigerators, etc.)
- `clearance` - Property clearance and cleaning
- `furnishing` - Furniture and fixtures

### **PAYMENT METHOD VALUES**

**Valid payment_method field values:**
- `"Robert Ellis"` - Robert Ellis collection service
- `"Propsk Old Account"` - Historical Propsk system payments
- `"PayProp"` - PayProp platform payments
- `"Direct Payment"` - Direct payments by owner/agent
- `"Bank Transfer"` - Direct bank transfers
- `"Property Expense"` - Property-related expense payments

### **BANK REFERENCE PATTERNS**

**Standardized reference patterns for consistency:**

**Rent Payments:**
- `"RE-JAN-01"`, `"RE-FEB-02"`, etc. (Robert Ellis monthly sequence)
- `"PROPSK-MAR-15"` (Propsk historical payments)
- `"PP-AUG-12"` (PayProp platform payments)

**Commission Payments:**
- `"COMM-JAN-01"`, `"COMM-FEB-02"`, etc. (Commission sequence)
- `"PROPSK-COM-MAR"` (Propsk commission references)

**Expense Payments:**
- `"EXP-JUN-01"`, `"EXP-AUG-03"` (General expenses)
- `"MAINT-001"`, `"MAINT-002"` (Maintenance work)

**Parking Payments:**
- `"PP-PARK-JUL-01"` (Parking space payments)
- `"COMM-PARK-JUL"` (Parking commission)

### **PROPERTY REFERENCE MAPPING**

**CRITICAL**: Property references in CSV must match existing property names EXACTLY (case-sensitive).

**COMPLETE LIST OF VALID PROPERTY REFERENCES:**

**🏢 RESIDENTIAL FLATS:**
```
"Flat 1 - 3 West Gate"
"Flat 2 - 3 West Gate"
"Flat 3 - 3 West Gate"
"Flat 4 - 3 West Gate"
"Flat 5 - 3 West Gate"
"Flat 6 - 3 West Gate"
"Flat 7 - 3 West Gate"
"Flat 8 - 3 West Gate"
"Flat 9 - 3 West Gate"
"Flat 10 - 3 West Gate"
"Flat 11 - 3 West Gate"
"Flat 12 - 3 West Gate"
"Flat 13 - 3 West Gate"
"Flat 14 - 3 West Gate"
"Flat 15 - 3 West Gate"
"Flat 16 - 3 West Gate"
"Flat 17 - 3 West Gate"
"Flat 18 - 3 West Gate"
"Flat 19 - 3 West Gate"
"Flat 20 - 3 West Gate"
"Flat 21 - 3 West Gate"
"Flat 22 - 3 West Gate"
"Flat 23 - 3 West Gate"
"Flat 24 - 3 West Gate"
"Flat 26 - 3 West Gate"
"Flat 27 - 3 West Gate"
"Flat 28 - 3 West Gate"
"Flat 29 - 3 West Gate"
"Flat 30 - 3 West Gate"
```

**🅿️ PARKING SPACES:**
```
"Parking Space 1"
"Parking Space 2"
```

**⚠️ IMPORTANT NOTES:**
- **Flat 25** is NOT in the system - do not use this reference
- Residential flats use format: `"Flat [NUMBER] - 3 West Gate"`
- Parking spaces use format: `"Parking Space [NUMBER]"`
- Property references must be enclosed in double quotes in CSV
- Any transactions with invalid property references will be imported but remain unlinked
- **Total valid properties: 31** (29 flats + 2 parking spaces)

**To verify current valid properties:**
```sql
SELECT DISTINCT property_name FROM financial_transactions WHERE property_id IS NOT NULL ORDER BY property_name;
```

### **DATA VALIDATION RULES**

**BEFORE IMPORT - CSV MUST PASS ALL CHECKS:**

1. **File Format**
   - ✅ UTF-8 encoding
   - ✅ Comma-separated values
   - ✅ Header row present and correct
   - ✅ No empty rows

2. **Date Validation**
   - ✅ All dates in YYYY-MM-DD format
   - ✅ No future dates beyond current date
   - ✅ No dates before 2020-01-01

3. **Amount Validation**
   - ✅ Valid decimal format (e.g., 795.00)
   - ✅ No currency symbols (£, $, etc.)
   - ✅ Rent payments are positive amounts
   - ✅ Commission/fees are negative amounts

4. **Transaction Type Validation**
   - ✅ Only allowed values: `deposit`, `fee`, `payment`, `expense`
   - ✅ Consistent with amount sign (positive for deposit, negative for others)

5. **Property Reference Validation**
   - ✅ All property references exist in the system
   - ✅ Exact string match (case-sensitive)
   - ✅ No orphaned property references

### **EXAMPLE COMPLETE ROWS**

```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes
2025-01-27,795.00,"Rent payment - January",deposit,rent,"Flat 1 - 3 West Gate","Ms O Smoliarenko & Mr I Halii","RE-JAN-01","Robert Ellis","Monthly rent collection"
2025-01-27,-119.25,"Commission - January",fee,commission,"Flat 1 - 3 West Gate","Ms O Smoliarenko & Mr I Halii","COMM-JAN-01","Robert Ellis","15% management fee"
2025-02-15,-450.00,"Emergency plumber",payment,maintenance,"Flat 2 - 3 West Gate","","MAINT-001","Direct Payment","Burst pipe repair"
2025-03-01,1200.00,"March rent payment",deposit,rent,"Flat 2 - 3 West Gate","Mr John Smith","RE-MAR-02","Bank Transfer","Direct debit collection"
```

### **🚨 COMMISSION PAIRING RULES - MANDATORY**

**CRITICAL RULE: Every rent payment MUST have a corresponding commission entry on the SAME DATE.**

**This is a HARD REQUIREMENT - no exceptions allowed.**

**Standard Commission Rates:**
- **Default**: 15% (most common)
- **Alternative**: 9% (some properties)
- **Reduced**: 3% (special arrangements)

**Commission Calculation Examples:**

**1. Standard Rent Payment:**
```
Rent Payment: £795.00
Commission Rate: 15%
Commission Amount: £795.00 × 0.15 = £119.25 (negative in CSV)
Net to Owner: £795.00 - £119.25 = £675.75
```

**2. Partial Payment:**
```
Partial Rent: £185.00 (Marie Dinko example)
Commission Rate: 15%
Commission Amount: £185.00 × 0.15 = £27.75 (negative in CSV)
Net to Owner: £185.00 - £27.75 = £157.25
```

**3. End-of-Tenancy/Arrears Payment:**
```
Full Amount Due: £5,687.30 (Adam Kirby example)
Commission Rate: 15%
Commission Amount: £5,687.30 × 0.15 = £853.10 (negative in CSV)
Net to Owner: £5,687.30 - £853.10 = £4,834.20
```

**Required CSV Entry Patterns:**

**A. Standard Monthly Rent:**
```csv
2025-01-27,795.00,"Rent payment - January",deposit,rent,"Flat 1 - 3 West Gate","Ms O Smoliarenko","RE-JAN-01","Robert Ellis","Monthly collection"
2025-01-27,-119.25,"Commission - January",fee,commission,"Flat 1 - 3 West Gate","Ms O Smoliarenko","COMM-JAN-01","Robert Ellis","15% management fee"
```

**B. Partial Payment:**
```csv
2025-05-15,185.00,"Rent payment - May (partial)",deposit,rent,"Flat 5 - 3 West Gate","Marie Dinko","RE-MAY-PART","Robert Ellis","Partial payment"
2025-05-15,-27.75,"Commission - May (partial)",fee,commission,"Flat 5 - 3 West Gate","Marie Dinko","COMM-MAY-PART","Robert Ellis","15% on partial payment"
```

**C. End-of-Tenancy/Arrears:**
```csv
2025-08-08,5687.30,"Rent payment - August + arrears",deposit,rent,"Flat 12 - 3 West Gate","Adam Kirby","RE-AUG-FULL","Robert Ellis","Final payment + arrears"
2025-08-08,-853.10,"Commission - August + arrears",fee,commission,"Flat 12 - 3 West Gate","Adam Kirby","COMM-AUG-FULL","Robert Ellis","15% on full amount"
```

**D. Parking Space Rent:**
```csv
2025-07-01,60.00,"Parking rent - July",deposit,parking,"Parking Space 1","John Smith","PP-PARK-JUL-01","PayProp","Monthly parking fee"
2025-07-01,-9.00,"Parking commission - July",fee,commission,"Parking Space 1","John Smith","COMM-PARK-JUL","PayProp","15% parking commission"
```

**⚠️ VALIDATION CHECKS:**
- Every `deposit` entry with `rent` or `parking` category MUST have a matching `fee` entry
- Both entries MUST have identical `transaction_date`
- Both entries MUST reference the same `property_reference`
- Commission amount MUST be negative
- Commission percentage should be consistent per property

### **POST-IMPORT VERIFICATION**

**After CSV import, run these queries to verify data integrity:**

```sql
-- 1. Check total imported records
SELECT COUNT(*) as total_records
FROM financial_transactions
WHERE data_source = 'HISTORICAL_IMPORT';

-- 2. Verify transaction type distribution
SELECT transaction_type, category_name, COUNT(*), SUM(amount) as total_amount
FROM financial_transactions
WHERE data_source = 'HISTORICAL_IMPORT'
GROUP BY transaction_type, category_name
ORDER BY COUNT(*) DESC;

-- 3. Check property linkage success
SELECT
  COUNT(*) as total_records,
  COUNT(property_id) as linked_records,
  COUNT(*) - COUNT(property_id) as unlinked_records,
  (COUNT(property_id) / COUNT(*)) * 100 as linkage_percentage
FROM financial_transactions
WHERE data_source = 'HISTORICAL_IMPORT';

-- 4. Validate commission calculations
SELECT
  property_name,
  SUM(CASE WHEN transaction_type = 'invoice' AND category_name = 'Rent' THEN amount ELSE 0 END) as rent_total,
  SUM(CASE WHEN transaction_type = 'commission_payment' THEN ABS(amount) ELSE 0 END) as commission_total,
  (SUM(CASE WHEN transaction_type = 'commission_payment' THEN ABS(amount) ELSE 0 END) /
   SUM(CASE WHEN transaction_type = 'invoice' AND category_name = 'Rent' THEN amount ELSE 0 END)) * 100 as commission_rate
FROM financial_transactions
WHERE data_source = 'HISTORICAL_IMPORT'
GROUP BY property_name
HAVING rent_total > 0;

-- 5. Check for orphaned records (no property match)
SELECT property_name, transaction_type, category_name, COUNT(*), SUM(amount)
FROM financial_transactions
WHERE data_source = 'HISTORICAL_IMPORT' AND property_id IS NULL
GROUP BY property_name, transaction_type, category_name;
```

## **📋 CSV PREPARATION FAQ**

### **Q1: Payment Sources - Which payments to include?**

**A: Include ALL rent payments regardless of collection method.**

Your spreadsheet tracks three destinations:
- "Paid to Robert Ellis" - Include these
- "Paid to Propsk Old Account" - Include these
- "Paid to Propsk PayProp" - Include these

**Reasoning**: The PayProp system needs complete historical rent data for accurate financial reporting. Use the `payment_method` field to distinguish sources:

```csv
# Robert Ellis collection
2025-01-27,795.00,"Rent payment - January",deposit,rent,"Flat 1 - 3 West Gate","Ms O","RE-JAN-01","Robert Ellis","Direct collection"

# Propsk Old Account
2025-02-15,850.00,"Rent payment - February",deposit,rent,"Flat 2 - 3 West Gate","Mr J","PROPSK-FEB-15","Propsk Old Account","Historical system"

# PayProp platform
2025-03-12,920.00,"Rent payment - March",deposit,rent,"Flat 3 - 3 West Gate","Ms K","PP-MAR-12","PayProp","Current platform"
```

### **Q2: Commission Structure - 10% + 5% = 15%**

**A: Combine into single 15% commission entry.**

Your breakdown:
- Management Fee: 10%
- Service Fee: 5%
- **Total: 15%**

**Use single commission entry** for simplicity and consistency with PayProp's structure:

```csv
# Rent received
2025-01-27,795.00,"Rent payment - January",deposit,rent,"Flat 1 - 3 West Gate","Ms O","RE-JAN-01","Robert Ellis","Monthly collection"
# Combined commission (not separate management + service)
2025-01-27,-119.25,"Commission - January (10% mgmt + 5% service)",fee,commission,"Flat 1 - 3 West Gate","Ms O","COMM-JAN-01","Robert Ellis","15% total commission"
```

### **Q3: Expense Categories - Standardization Required**

**A: Map your descriptions to allowed categories:**

| Your Spreadsheet | CSV Category | Example |
|------------------|--------------|---------|
| "Fire Safety Equipment" | `fire_safety` | `fire_safety` |
| "White Goods (fridges, washing machines)" | `white_goods` | `white_goods` |
| "Clearance services" | `clearance` | `clearance` |
| "Repairs (plumbing, painting)" | `maintenance` | `maintenance` |
| "Beds/Mattresses" | `furnishing` | `furnishing` |

**Example mapping:**
```csv
2025-04-10,-450.00,"Plumber - burst pipe repair",payment,maintenance,"Flat 5 - 3 West Gate","","MAINT-001","Direct Payment","Emergency repair"
2025-05-15,-280.00,"New washing machine",payment,white_goods,"Flat 8 - 3 West Gate","","WG-001","Property Expense","Appliance replacement"
2025-06-01,-120.00,"Fire extinguisher service",payment,fire_safety,"Flat 12 - 3 West Gate","","FS-001","Direct Payment","Annual inspection"
```

### **Q4: Date Handling - Use Rent Received Date**

**A: Priority order for transaction_date:**

1. **First Choice**: "Rent Received Date" (when available)
2. **Second Choice**: Actual payment date from bank
3. **Last Resort**: Rent due date for the month

**Reasoning**: The system needs actual cash flow dates, not theoretical due dates.

```csv
# Use actual received date when known
2025-01-27,795.00,"Rent payment - January",deposit,rent,"Flat 1 - 3 West Gate","Ms O","RE-JAN-01","Robert Ellis","Received 27th Jan"

# If received date unknown, use payment processing date
2025-02-15,850.00,"Rent payment - February",deposit,rent,"Flat 2 - 3 West Gate","Mr J","PROPSK-FEB-15","Propsk Old Account","Processed 15th Feb"
```

### **Q5: Partial/Overpayments - Use Actual Amounts**

**A: Enter exactly as received, with clear descriptions:**

**Partial Payment Example:**
```csv
2025-05-15,185.00,"Rent payment - May (partial)",deposit,rent,"Flat 18 - 3 West Gate","Marie Dinko","RE-MAY-PART","Robert Ellis","Partial payment only"
2025-05-15,-27.75,"Commission - May (partial)",fee,commission,"Flat 18 - 3 West Gate","Marie Dinko","COMM-MAY-PART","Robert Ellis","15% on partial amount"
```

**Arrears/Full Settlement Example:**
```csv
2025-08-08,5687.30,"Rent payment - August + arrears settlement",deposit,rent,"Flat 12 - 3 West Gate","Adam Kirby","RE-AUG-FULL","Robert Ellis","Final payment including arrears"
2025-08-08,-853.10,"Commission - August + arrears",fee,commission,"Flat 12 - 3 West Gate","Adam Kirby","COMM-AUG-FULL","Robert Ellis","15% on full settlement"
```

**Key Point**: The CSV captures actual cash movements. PayProp's invoicing system will handle the reconciliation between what was owed vs what was received.

---

### **COMMON IMPORT ERRORS TO AVOID**

❌ **DON'T DO:**
```csv
# Wrong date format
27/01/2025,795.00,"Rent payment",deposit,rent,"Flat 1"

# Currency symbols in amount
2025-01-27,£795.00,"Rent payment",deposit,rent,"Flat 1"

# Positive commission (should be negative)
2025-01-27,119.25,"Commission",fee,commission,"Flat 1"

# Property name doesn't match database
2025-01-27,795.00,"Rent payment",deposit,rent,"Flat 1 West Gate"
```

✅ **CORRECT FORMAT:**
```csv
# Correct format
2025-01-27,795.00,"Rent payment - January",deposit,rent,"Flat 1 - 3 West Gate","Ms O Smoliarenko","RE-JAN-01","Robert Ellis","Monthly collection"
2025-01-27,-119.25,"Commission - January",fee,commission,"Flat 1 - 3 West Gate","Ms O Smoliarenko","COMM-JAN-01","Robert Ellis","15% management fee"
```

---

## Financial Dashboard Query Logic

### Expected Query Patterns

Based on staging database analysis, financial dashboards expect:

```sql
-- Total Rent Received (from actual payments, not invoices)
SELECT SUM(incoming_transaction_amount)
FROM payprop_report_all_payments
WHERE incoming_transaction_type = 'incoming payment'

-- Commission Paid
SELECT SUM(amount)
FROM payprop_report_all_payments
WHERE beneficiary_type = 'agency'

-- Net to Property Owner
SELECT SUM(amount)
FROM payprop_report_all_payments
WHERE beneficiary_type = 'beneficiary'
```

---

## Property Linkage Requirements

### Property ID Mapping

Our imported data required property_id linking:

| Property Name | PayProp ID | Transaction Count |
|---------------|------------|-------------------|
| Flat 1 - 3 West Gate | KAXNvEqAXk | 7 |
| Flat 2 - 3 West Gate | KAXNvEqVXk | 7 |
| Flat 3 - 3 West Gate | WzJBQ3ERZQ | 7 |
| ... | ... | ... |
| **Total Properties** | **30** | **203 linked** |

**Unlinked Records**: 15 (commission payments, parking spaces, expenses)

---

## Database Schema Constraints

### Transaction Type Constraints

The `financial_transactions` table enforces specific transaction types:

```sql
CONSTRAINT `chk_transaction_type` CHECK (
  `transaction_type` IN (
    'invoice',
    'credit_note',
    'debit_note',
    'deposit',
    'commission_payment',
    'payment_to_beneficiary',
    'payment_to_agency',
    'payment_to_contractor',
    'payment_property_account',
    'payment_deposit_account',
    'refund',
    'adjustment',
    'transfer'
  )
)
```

---

## CSV to PayProp Mapping Strategy

### Current Implementation

Our imported CSV data was mapped as follows:

| CSV Type | Mapped to PayProp Type | Rationale |
|----------|------------------------|-----------|
| `deposit` (rent) | `invoice` + `category_name: 'Rent'` | Rent charges to tenants |
| `fee` | `commission_payment` + `category_name: 'Commission'` | Agency commission |
| `payment` | `payment_to_beneficiary` | Miscellaneous payments |
| `expense` | `payment_to_contractor` | Maintenance expenses |

### Issues Identified

1. **Missing Payment Flow**: Our CSV has rent amounts but doesn't split into commission/net components
2. **Single-sided Entries**: PayProp expects both invoice AND payment records for each transaction
3. **Commission Calculations**: Need to reverse-engineer commission rates from our aggregate data

---

## Recommended Implementation

### For Complete PayProp Compatibility

Each rent payment in our CSV should generate:

```sql
-- 1. Invoice Record (what was charged)
INSERT INTO financial_transactions (
  transaction_type: 'invoice',
  amount: 795.00,
  category_name: 'Rent',
  commission_rate: 15.00,
  commission_amount: 119.25,
  net_to_owner_amount: 675.75
)

-- 2. Actual Payment Record (in payprop_report_all_payments)
INSERT INTO payprop_report_all_payments (
  incoming_transaction_amount: 795.00,
  incoming_tenant_name: 'Ms O Smoliarenko & Mr I Halii',
  incoming_property_name: 'Flat 1 - 3 West Gate',
  payment_batch_transfer_date: '2025-01-27'
)

-- 3. Commission Payment
INSERT INTO financial_transactions (
  transaction_type: 'commission_payment',
  amount: 119.25,
  category_name: 'Commission'
)

-- 4. Net Payment to Owner
INSERT INTO financial_transactions (
  transaction_type: 'payment_to_beneficiary',
  amount: 675.75
)
```

---

## Data Quality Observations

### Staging Database Statistics

- **Total Transactions**: 13,249
- **Total Payments**: 7,325
- **Average Commission Rate**: ~10-15%
- **Primary Property Count**: 30+ properties
- **Date Range**: 2023-2024 data available

### Production Database Current State

- **Imported Records**: 218
- **Property Linked**: 203 (93%)
- **Date Range**: January-August 2025
- **Total Value**: £149,579.19 rent + commissions

---

## Technical Implementation Notes

### Database Connections

- **Staging Database**: `ballast.proxy.rlwy.net:45419` (railway)
- **Production Database**: `switchyard.proxy.rlwy.net:55090` (railway)

### Key Files Generated

- `complete_bulk_import.sql` - Full transaction import
- `update_property_ids.sql` - Property linkage updates
- `generate_sql.ps1` - PowerShell CSV processor

### Verification Queries

```sql
-- Check import success
SELECT COUNT(*) FROM financial_transactions WHERE data_source = 'HISTORICAL_IMPORT';

-- Verify property linkage
SELECT property_name, property_id, COUNT(*)
FROM financial_transactions
WHERE data_source = 'HISTORICAL_IMPORT'
GROUP BY property_name, property_id;

-- Commission calculation verification
SELECT transaction_type, category_name, SUM(amount)
FROM financial_transactions
WHERE data_source = 'HISTORICAL_IMPORT'
GROUP BY transaction_type, category_name;
```

---

## Conclusions and Recommendations

### Current Status ✅
- CSV data successfully imported (218 records)
- Property linkage established (203/218 records)
- Transaction types mapped to PayProp constraints
- Basic financial data available for reporting

### Missing Components ⚠️
1. **Dual-entry system**: Need both invoices and payments for each transaction
2. **Commission splitting**: Individual transactions should split into commission + net components
3. **PayProp payment table**: Should populate `payprop_report_all_payments` for dashboard compatibility
4. **Beneficiary relationships**: Missing property owner payment flow records

### Next Steps 🎯
1. Implement complete payment flow generation from CSV data
2. Create corresponding `payprop_report_all_payments` entries
3. Verify financial dashboard functionality with dual-entry data
4. Establish automated commission calculation based on property-specific rates

---

*Analysis completed: 2025-01-16*
*Database schemas: PayProp v2024 + Custom Extensions*
*CSV source: Historical transaction data (Jan-Aug 2025)*
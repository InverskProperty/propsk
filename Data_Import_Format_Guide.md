# CRM Data Import Format Guide ✨ ENHANCED

This guide documents all supported data import formats for the CRM system, including expenses, incoming payments, landlord payments, and other transaction types.

## 🆕 **LATEST ENHANCEMENTS (October 2025)**

Your system has been **significantly enhanced** with simplified imports and automatic split transaction creation:

### ✅ **Simplified CSV Import - One Template, Any Data Richness**
- **One Standard Template**: Always use the same comprehensive CSV header with all 15 fields
- **Fill What You Have**: Leave fields blank where you don't have data - system handles both sparse and rich data equally
- **Only 2 Fields Required**: `transaction_date` and `amount` are the only mandatory fields
- **Smart Defaults**: Description, transaction_type, and other fields automatically inferred from context when blank
- **Human Review**: Interactive review interface lets you edit any field, map properties/customers, and override inferred values

### ✅ **Automatic Split Transaction Creation**
- **Incoming Payments**: Automatically creates owner allocations and agency fees
- **Beneficiary Balance Tracking**: Updates owner balances in real-time
- **Commission Calculation**: Auto-calculates from property settings (default 15%)
- **PayProp Compatible**: Works identically to automated PayProp imports

### ✅ **Enhanced Statement Generation**
- **PayProp Properties**: Uses rich PayProp data (`payprop_export_invoices`, `payprop_report_tenant_balances`, `payprop_report_all_payments`)
- **Old Account Properties**: Enhanced spreadsheet extraction with arrears tracking, actual payment dates, and expense comments
- **Balance Tracking**: Shows running balances for all owners across both accounts
- **Unified Output**: Same detailed format for both property types

## Import Routes Available

### 1. Historical Transaction Import (Simple Format)
**Route:** `/employee/transaction/import/csv` or `/employee/transaction/import/json`
**Purpose:** Direct import of organized transaction data
**Access:** Employee level access required

### 2. PayProp Historical Import (Spreadsheet Conversion)
**Route:** `/admin/payprop-import`
**Purpose:** Import property management spreadsheets and convert to standardized format
**Access:** Manager level access required

---

## 1. Historical Transaction Import Formats

### CSV Format (Simple Import)

**📋 Standard CSV Template (Always Use This Header):**

Include ALL columns in your CSV header - leave fields blank where you don't have data. The system will infer or auto-generate missing values.

```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
```

**Example - Rich Data (All Fields Filled):**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
2025-01-22,1125.00,"Rent - January 2025",payment,rent,"Apartment F - Knighton Hayes",john@email.com,"LEASE-APT-F-2025",,1125.00,REF-12345,"Bank Transfer","John Smith","Rent on time",OLD_ACCOUNT
```

**Example - Sparse Data (Only Essentials):**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
2025-02-01,500.00,,,,,,,,500.00,,,,,OLD_ACCOUNT
```
**Result:** System infers transaction_type=payment, auto-generates description, processes the payment.

### Field Specifications

#### ✅ REQUIRED Fields (Only 2!):

- **`transaction_date`** - Date of transaction
  - Formats: `yyyy-MM-dd`, `dd/MM/yyyy`, `MM/dd/yyyy`, `dd-MM-yyyy`, `yyyy/MM/dd`
  - Example: `2025-01-15` or `15/01/2025`

- **`amount`** - Transaction amount
  - Format: Decimal number
  - Positive = Money in, Negative = Money out
  - Examples: `1125.00` (rent received), `-150.00` (expense paid)

#### 🤖 OPTIONAL Fields (Auto-Generated with Smart Defaults):

- **`description`** - Transaction description
  - **AUTO-GENERATED** if empty from:
    1. `bank_reference` (if provided)
    2. `counterparty_name` (if provided)
    3. Beneficiary type context (e.g., "Owner Allocation - Apartment F")
    4. Transaction type + property + amount (e.g., "Payment - Apartment F - £1125.00")
  - Format: Text (can be quoted if contains commas)
  - Example: `"Rent payment - 123 Main St"`

- **`transaction_type`** - Type of transaction
  - **AUTO-INFERRED** if empty from:
    1. `incoming_transaction_amount` presence → `payment`
    2. `beneficiary_type` value → `payment` or `expense`
    3. Amount direction → Positive = `invoice`, Negative = `payment`
  - Valid values: `payment`, `invoice`, `fee`, `expense`, `maintenance`, `adjustment`, `deposit`, `withdrawal`

#### 📊 Entity Linking & Mapping Fields:

- **`property_reference`** - Property identifier
  - **Can be set during import review** if empty
  - Examples: `"123 Main St"`, `"Apartment F"`, `"PROP001"`
  - System attempts fuzzy matching with existing properties

- **`customer_reference`** - Customer identifier
  - **Can be set during import review** if empty
  - Examples: `"john@email.com"`, `"CUST001"`, `"John Smith"`
  - System attempts matching by email or name

- **`lease_reference`** - Links transaction to specific lease
  - Example: `"LEASE-BH-F1-2025"`
  - If provided, transaction automatically linked to invoice/lease

#### 💰 Balance Tracking & Split Transactions:

- **`beneficiary_type`** - **NEW!** Controls balance tracking
  - **`beneficiary`** - Owner allocation (INCREASES owner balance)
    - Used for: Net due to owner after fees/expenses
    - Automatically created when `incoming_transaction_amount` is present
  - **`beneficiary_payment`** - Payment to owner (DECREASES owner balance)
    - Used for: Actual bank transfers to owners
  - **`contractor`** - Payment to contractor
    - Used for: Maintenance, repairs, etc.
  - Leave empty for regular transactions (no balance impact)

- **`incoming_transaction_amount`** - **NEW!** Triggers automatic split
  - When provided, system automatically creates 3 transactions:
    1. Main transaction (rent received)
    2. Owner allocation (net after commission)
    3. Agency fee (commission amount)
  - Example: `1125.00` on a rent payment
  - Commission calculated from property settings (default 15%)

- **`payment_source`** - Account tracking for statements
  - **`OLD_ACCOUNT`** - Historical "Propsk Old Account" transactions
  - **`PAYPROP`** - PayProp account transactions
  - **`BOTH`** - Duplicates transaction across both accounts
  - Used to separate columns on owner statements

#### 📝 Additional Classification Fields:

- **`category`** - Transaction category
  - Examples: `rent`, `maintenance`, `utilities`, `insurance`, `owner_allocation`, `owner_payment`

- **`subcategory`** - Sub-category for detailed classification
  - Examples: `electricity`, `gas`, `water`, `repairs`, `cleaning`

- **`bank_reference`** - Bank transaction reference
  - **Used for auto-generating description** if description is empty
  - Examples: `"FPS JOHN SMITH RENT JAN"`, `"FASTER PMT ABC PLUMBING"`

- **`payment_method`** - Method of payment
  - Examples: `"Bank Transfer"`, `"cash"`, `"cheque"`

- **`counterparty_name`** - Name of other party in transaction
  - **Used for auto-generating description** if description is empty
  - Examples: `"ABC Maintenance Ltd"`, `"John Smith"`

- **`source_reference`** - Reference to source document
  - Examples: `"INV001"`, `"STMT202301"`

- **`notes`** - Additional notes
  - Format: Free text

### CSV Examples

**All examples use the SAME comprehensive header** - only difference is which fields are filled vs left blank:

```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
```

#### 🎯 Example 1: Absolute Minimum (Just Date + Amount)
**Most fields blank - system auto-generates everything:**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
2025-01-22,1125.00,,,,,,,,,,,,
2025-01-25,-150.00,,,,,,,,,,,,
```
**Result:** System infers types (payment), generates descriptions like "Payment - £1125.00"

#### 📋 Example 2: Bank Statement Data (Some Context)
**Using bank_reference for better descriptions:**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
2025-01-22,1125.00,,,,,,,,,"FPS JOHN SMITH RENT JAN",,,
2025-01-25,-150.00,,,,,,,,,"FASTER PMT ABC PLUMBING",,,
```
**Result:** Uses bank_reference as description, infers types automatically

#### 💰 Example 3: Rent with Auto-Split (Creates 3 Transactions!)
**Using incoming_transaction_amount triggers auto-split logic:**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
2025-01-22,1125.00,,,,"Apartment F - Knighton Hayes",,,1125.00,,,,,OLD_ACCOUNT
```
**Result:** System creates:
1. Main: £1125 rent received
2. Owner allocation: -£956.25 (85% to owner) → beneficiary_type="beneficiary"
3. Agency fee: -£168.75 (15% commission) → category="management_fee"

#### 🏠 Example 4: Payment to Owner (Decreases Balance)
**Using beneficiary_type to track owner payments:**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
2025-01-28,-956.25,,,,"John Smith",,beneficiary_payment,,,,,,OLD_ACCOUNT
```
**Result:** Records payment, decreases owner balance by £956.25

#### 🔧 Example 5: Contractor Payment with Auto-Description
**Using counterparty_name for auto-generated description:**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
2025-01-25,-150.00,,,,"Apartment F - Knighton Hayes",,contractor,,,,"ABC Plumbing",
```
**Result:** Description auto-generated: "Contractor Payment - ABC Plumbing - £150.00"

#### 📊 Example 6: Full Control (All Fields Filled)
**When you have complete information from your accounting system:**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
2025-01-22,1125.00,"Rent Received - January",payment,rent,"Apartment F - Knighton Hayes","john@email.com","LEASE-APT-F-2025",,1125.00,"REF-001","Bank Transfer","John Smith","Rent on time",OLD_ACCOUNT
2025-01-25,-150.00,"Plumbing Repair",expense,maintenance,"Apartment F - Knighton Hayes","ABC Plumbing",,"contractor",,,"Bank Transfer","ABC Plumbing","Emergency repair",OLD_ACCOUNT
2025-01-28,-956.25,"Payment to Owner",payment,owner_payment,,"John Smith",,beneficiary_payment,,,"Bank Transfer","John Smith","Monthly payment",OLD_ACCOUNT
```
**Result:** System uses all provided data exactly as given, no inference needed

#### 🔄 Example 7: Mixed Data Richness (Real-World Scenario)
**Your actual CSV showing what came from your spreadsheet - sparse data mixed with rich data:**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
17/02/2025,740.00,,,,FLAT 5 - 3 WEST GATE,,,,,740.00,,,,,OLD_ACCOUNT
19/02/2025,700.00,,,,FLAT 7 - 3 WEST GATE,,,,,700.00,,,,,OLD_ACCOUNT
06/03/2025,-3080.00,,,,"Boden House NG10 - Block Property",,,,,,,,,,OLD_ACCOUNT
06/03/2025,-2111.00,,,,,,,,,,,,,,OLD_ACCOUNT
```
**Result:**
- Row 1 & 2: Rent payments with property + incoming_transaction_amount → auto-split, infers types
- Row 3: Expense with property → infers type=payment, auto-generates description
- Row 4: Just date+amount → infers type=payment, generates basic description

---

## 2. JSON Format (Alternative)

### JSON Structure:
```json
{
  "source_description": "Historical bank data 2023",
  "transactions": [
    {
      "transaction_date": "2023-01-15",
      "amount": -1200.00,
      "description": "Rent payment - Main St",
      "transaction_type": "payment",
      "category": "rent",
      "property_reference": "123 Main St",
      "customer_reference": "john@email.com",
      "bank_reference": "TXN123456"
    },
    {
      "transaction_date": "2023-02-01",
      "amount": 50.00,
      "description": "Interest payment",
      "transaction_type": "deposit",
      "category": "interest",
      "bank_reference": "INT789"
    }
  ]
}
```

---

## 3. PayProp Spreadsheet Import

### Spreadsheet Format (38-Column Layout)
**Route:** `/admin/payprop-import`
**Purpose:** Import complex property management spreadsheets with multiple transaction types

### Key Columns for Different Transaction Types:

#### Rent Income (Columns 1-12):
- Column 1: Property reference
- Column 2: Period date
- Column 3: Rent amount
- Column 4: Service charges
- Column 5: Other income

#### Expenses (Columns 13-37):
- Column 13: Management fees
- Column 14: Maintenance costs
- Column 15: Utility bills
- Column 16: Insurance
- Column 17: Legal fees
- Column 18: Advertising costs
- Columns 19-37: Various other expense categories

#### Owner Payments (Columns 38-40):
- Column 38: Net Due to Prestvale Propsk Old Account
- Column 39: Net Due to Prestvale Propsk PayProp Account
- Column 40: Total owner payment

### Spreadsheet Requirements:
- Must have 38+ columns
- First row contains headers
- Data rows contain property transactions
- Supports both .xlsx and .csv formats
- Period information required during upload

---

## 💡 Understanding Split Transactions & Balance Tracking

### How Split Transactions Work

When you import a rent payment with `incoming_transaction_amount`, the system automatically creates a **complete financial picture**:

#### Example: £1125 Rent Payment
```csv
transaction_date,amount,property_reference,incoming_transaction_amount,payment_source
2025-01-22,1125.00,Apartment F,1125.00,OLD_ACCOUNT
```

**System automatically creates 3 transactions:**

1. **Rent Payment (Main Transaction)**
   - Amount: £1125.00
   - Description: "Payment - Apartment F - £1125.00"
   - Type: payment
   - This is what you see in your bank

2. **Owner Allocation (Increases Owner Balance)**
   - Amount: -£956.25 (85% of £1125)
   - Description: "Owner Allocation - Apartment F"
   - beneficiary_type: "beneficiary"
   - Category: "owner_allocation"
   - **Effect:** Owner balance INCREASES by £956.25

3. **Agency Fee (Your Income)**
   - Amount: -£168.75 (15% of £1125)
   - Description: "Management Fee - 15% - Apartment F"
   - Type: fee
   - Category: "management_fee"
   - **Effect:** Tracks your agency income

### Commission Calculation

- **Source:** Property-specific `commission_percentage` field
- **Default:** 15% (if not set on property)
- **Formula:**
  - Commission = Rent × (Commission % ÷ 100)
  - Net to Owner = Rent - Commission

### Balance Tracking Flow

```
Rent Received (£1125)
        ↓
   [Auto-Split]
        ↓
    ┌───────────────┬──────────────┐
    ↓               ↓              ↓
Owner Alloc     Agency Fee    Main Txn
£956.25         £168.75       £1125
(to owner)      (to agency)   (in bank)
    ↓
Owner Balance
+£956.25 ✅
```

```
Payment to Owner (-£956.25)
    beneficiary_type = "beneficiary_payment"
        ↓
Owner Balance
-£956.25 ✅
```

### Beneficiary Types Explained

| Type | Effect on Balance | Use Case | Example |
|------|------------------|----------|---------|
| `beneficiary` | **INCREASES** | Owner allocation from rent | Net due to owner after fees |
| `beneficiary_payment` | **DECREASES** | Actual payment to owner | Bank transfer to owner |
| `contractor` | **DECREASES** | Payment to contractor | Maintenance, repairs |
| *(empty)* | No effect | Regular transactions | Agency expenses, fees |

### Statement Integration

Split transactions automatically appear on owner statements:

**Rent Section:**
- Shows "Rent Received" (£1125)
- Shows "Management Fee" (-£168.75)
- Shows "Net Due to Owner" (£956.25)

**Payment Section:**
- Shows "Payment to Owner" (-£956.25)

**Balance Section:**
- Opening balance
- + Allocations (£956.25)
- - Payments (-£956.25)
- = Closing balance (£0 if paid)

---

## Transaction Types and Categories

### Available Transaction Types:
1. **`payment`** - Outgoing payments (expenses, owner payments)
2. **`invoice`** - Incoming payments (rent, deposits)
3. **`fee`** - Fees charged/received
4. **`expense`** - Expenses and maintenance
5. **`maintenance`** - Maintenance expenses (legacy, use `expense`)
6. **`adjustment`** - Balance adjustments
7. **`deposit`** - Deposits (legacy, use `invoice`)
8. **`withdrawal`** - Withdrawals (legacy, use `payment`)

### Common Categories:
- **Income:** `rent`, `deposits`, `fees`, `interest`, `other_income`
- **Allocations:** `owner_allocation`, `contractor_allocation`
- **Expenses:** `maintenance`, `utilities`, `insurance`, `legal`, `advertising`, `management_fee`
- **Payments:** `owner_payment`, `contractor_payment`, `beneficiary_payment`

---

## Data Validation Rules

### ✅ Required Validations (Only 2!):
1. **`transaction_date`** - Must be parseable in supported date formats
2. **`amount`** - Must be valid decimal number

### 🤖 Auto-Generated if Missing:
1. **`description`** - Generated from bank_reference, counterparty_name, or context
2. **`transaction_type`** - Inferred from incoming_transaction_amount, beneficiary_type, or amount direction

### 🔍 Optional Validations (Attempted Matching):
1. **`property_reference`** - Fuzzy matching with existing properties
2. **`customer_reference`** - Matching by email or name
3. **`beneficiary_type`** - Must be: beneficiary, beneficiary_payment, contractor, or empty
4. **`payment_source`** - Must be: OLD_ACCOUNT, PAYPROP, BOTH, or empty
5. **`incoming_transaction_amount`** - Must be valid decimal if provided

### Import Review Process:
- **Validation:** System validates CSV structure and required fields
- **Property Mapping:** Review interface shows property matches for manual confirmation
- **Customer Mapping:** Review interface shows customer matches or allows creating new
- **Final Confirmation:** Review all transactions before importing

---

## Import Process

### 1. File Upload:
- Maximum file size: Check system settings
- Supported formats: .csv, .json, .xlsx (for spreadsheets)
- Encoding: UTF-8 recommended

### 2. Processing:
- Headers mapped to database fields
- Data validation performed
- Property/customer matching attempted
- Batch ID assigned for tracking

### 3. Results:
- Success/failure count
- Error messages for failed rows
- Batch ID for future reference
- Warnings for partial matches

---

## Common Use Cases

### 1. Monthly Rent Collection with Arrears Tracking:
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,notes
2023-01-01,2500.00,January rent,deposit,rent,123 Main St,tenant@email.com,Monthly rent collection
2023-01-01,2300.00,January rent,deposit,rent,456 Oak Ave,tenant2@email.com,Monthly rent collection | Shortfall: £200
2023-01-01,200.00,January rent arrears,arrears,rent_arrears,456 Oak Ave,tenant2@email.com,Outstanding rent amount
```

### 2. Enhanced Maintenance Expenses with Comments:
```csv
transaction_date,amount,description,transaction_type,category,subcategory,property_reference,counterparty_name,notes
2023-01-15,-150.00,Boiler repair - Emergency call out required,maintenance,maintenance,heating,123 Main St,ABC Heating Ltd,Emergency call out required
2023-01-20,-89.50,Garden maintenance - Monthly service,maintenance,maintenance,gardening,123 Main St,Green Thumb Services,Monthly service
2023-02-01,-75.00,Electrical inspection - Safety check,maintenance,maintenance,electrical,123 Main St,Safe Electric Ltd,Safety check
2023-02-05,-200.00,Plumbing repair - Leak in bathroom,maintenance,maintenance,plumbing,123 Main St,Drain Master,Leak in bathroom
```

### 3. Owner Distributions:
```csv
transaction_date,amount,description,transaction_type,category,property_reference,counterparty_name
2023-01-31,-2000.00,Monthly owner distribution,payment,owner_payment,123 Main St,John Smith (Owner)
2023-01-31,-2300.00,Monthly owner distribution,payment,owner_payment,456 Oak Ave,Jane Doe Properties
```

### 4. Utility Bills:
```csv
transaction_date,amount,description,transaction_type,category,subcategory,property_reference
2023-01-05,-125.50,Electricity bill,utility,utilities,electricity,123 Main St
2023-01-10,-89.00,Gas bill,utility,utilities,gas,123 Main St
2023-01-15,-45.00,Water bill,utility,utilities,water,123 Main St
```

### 5. Enhanced Mixed Transactions with All Data:
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,notes
2023-01-12,2500.00,Monthly rent collection,deposit,rent,123 Main St,tenant@email.com,Monthly rent collection
2023-01-12,-375.00,Commission (15% total),fee,commission,123 Main St,,15% total commission
2023-01-15,-150.00,Boiler repair - Emergency repair required,expense,maintenance,123 Main St,,Emergency repair required
2023-01-31,-2000.00,Owner payment,payment,owner_payment,123 Main St,,Monthly distribution
```

---

## Error Handling

### Common Errors:
1. **Invalid date format** - Check date format matches supported patterns
2. **Invalid amount** - Ensure amount is numeric
3. **Missing required fields** - Verify all required columns present
4. **Property not found** - Check property reference matches existing data
5. **Customer not found** - Check customer reference matches existing data

### Best Practices:
1. **Start minimal** - Use just `transaction_date` and `amount`, let system auto-generate the rest
2. **Add context gradually** - Include `property_reference` or `bank_reference` for better descriptions
3. **Use review interface** - Map properties and customers during import review
4. **Test with samples** - Try small batches first to verify formatting
5. **Use incoming_transaction_amount** - For rent payments to auto-create allocations and fees
6. **Leverage beneficiary_type** - Track balances automatically for owners and contractors

---

## 🚀 Quick Reference Guide

### Simplest Possible Import
```csv
transaction_date,amount
2025-01-22,1125.00
```

### Rent with Auto-Split
```csv
transaction_date,amount,property_reference,incoming_transaction_amount
2025-01-22,1125.00,Apartment F,1125.00
```
→ Creates: Rent + Owner Allocation + Agency Fee

### Payment to Owner
```csv
transaction_date,amount,customer_reference,beneficiary_type
2025-01-28,-956.25,John Smith,beneficiary_payment
```
→ Decreases owner balance

### Bank Statement Paste
```csv
transaction_date,amount,bank_reference
2025-01-22,1125.00,FPS JOHN SMITH RENT
2025-01-25,-150.00,PMT ABC PLUMBING
```
→ Uses bank references as descriptions

### Field Cheat Sheet

| Want to... | Use this field | Example |
|------------|---------------|---------|
| Just import bare minimum | `transaction_date`, `amount` | `2025-01-22,1125.00` |
| Auto-split rent payments | `incoming_transaction_amount` | `1125.00` |
| Track owner balance | `beneficiary_type` | `beneficiary` or `beneficiary_payment` |
| Better descriptions | `bank_reference` or `counterparty_name` | `"FPS JOHN SMITH"` |
| Link to property | `property_reference` | `"Apartment F"` |
| Link to customer | `customer_reference` | `"john@email.com"` |
| Link to lease | `lease_reference` | `"LEASE-BH-F1-2025"` |
| Separate OLD/PAYPROP | `payment_source` | `OLD_ACCOUNT` or `PAYPROP` |

### Beneficiary Type Quick Reference

| Type | What it does | When to use |
|------|-------------|-------------|
| *(empty)* | Normal transaction | Regular income/expenses |
| `beneficiary` | +£ to owner balance | Auto-created from rent split |
| `beneficiary_payment` | -£ from owner balance | When paying owners |
| `contractor` | Tracks contractor | Maintenance/repairs |

---

## Need Help?

- **Can't find a property?** - Use review interface to map manually
- **Wrong descriptions?** - Add `description` column with exact text
- **Wrong types?** - Add `transaction_type` column to override inference
- **Balance not tracking?** - Check `beneficiary_type` is set correctly
- **Split not working?** - Ensure `incoming_transaction_amount` matches `amount` and `property_reference` is set

For more help, see: `/employee/transaction/import` → "Examples & Help"

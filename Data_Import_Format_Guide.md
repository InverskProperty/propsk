# CRM Data Import Format Guide ✨ ENHANCED

This guide documents all supported data import formats for the CRM system, including expenses, incoming payments, landlord payments, and other transaction types.

## 🆕 **LATEST ENHANCEMENTS (September 2025)**

Your system has been **significantly enhanced** to use all available PayProp data and capture missing information from spreadsheets:

### ✅ **Enhanced Statement Generation**
- **PayProp Properties**: Now uses rich PayProp data (`payprop_export_invoices`, `payprop_report_tenant_balances`, `payprop_report_all_payments`)
- **Old Account Properties**: Enhanced spreadsheet extraction with arrears tracking, actual payment dates, and expense comments
- **Unified Output**: Same detailed format for both property types

### ✅ **Enhanced Spreadsheet Import**
- **Rent Arrears Tracking**: Captures both "Rent Due" (Column 6) and "Rent Received" (Column 13) to detect shortfalls
- **Actual Payment Dates**: Uses "Rent Received Date" (Column 12) instead of period end date
- **All 4 Expense Categories**: Extracts expenses with comments from columns 24-26, 30-32, 36-38, 42-44
- **General Comments**: Captures comments from Column 57 for context
- **Owner Payments**: Enhanced extraction from columns 38-40

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

**Required Headers:**
```csv
transaction_date,amount,description,transaction_type
```

**All Supported Headers:**
```csv
transaction_date,amount,description,transaction_type,category,subcategory,property_reference,customer_reference,bank_reference,payment_method,counterparty_name,source_reference,notes
```

### Field Specifications

#### Required Fields:
- **`transaction_date`** - Date of transaction
  - Formats: `yyyy-MM-dd`, `dd/MM/yyyy`, `MM/dd/yyyy`, `dd-MM-yyyy`, `yyyy/MM/dd`
  - Example: `2023-01-15` or `15/01/2023`

- **`amount`** - Transaction amount (positive for income, negative for expenses)
  - Format: Decimal number
  - Examples: `-1200.00` (expense), `2500.00` (income)

- **`description`** - Transaction description
  - Format: Text (can be quoted if contains commas)
  - Example: `"Rent payment - 123 Main St"`

- **`transaction_type`** - Type of transaction
  - Valid values: `payment`, `deposit`, `fee`, `commission`, `maintenance`, `utility`, `insurance`, `tax`

#### Optional Fields:
- **`category`** - Transaction category
  - Examples: `rent`, `maintenance`, `utilities`, `insurance`, `interest`

- **`subcategory`** - Sub-category for detailed classification
  - Examples: `electricity`, `gas`, `water`, `repairs`, `cleaning`

- **`property_reference`** - Property identifier
  - Examples: `"123 Main St"`, `"Property A"`, `"PROP001"`

- **`customer_reference`** - Customer identifier
  - Examples: `"john@email.com"`, `"CUST001"`, `"John Smith"`

- **`bank_reference`** - Bank transaction reference
  - Examples: `"TXN123456"`, `"REF789"`

- **`payment_method`** - Method of payment
  - Examples: `"bank_transfer"`, `"cash"`, `"cheque"`

- **`counterparty_name`** - Name of other party in transaction
  - Examples: `"ABC Maintenance Ltd"`, `"John Smith"`

- **`source_reference`** - Reference to source document
  - Examples: `"INV001"`, `"STMT202301"`

- **`notes`** - Additional notes
  - Format: Free text

### CSV Examples

#### Basic Expenses Example:
```csv
transaction_date,amount,description,transaction_type,category,property_reference
2023-01-15,-150.00,Plumbing repair,maintenance,maintenance,123 Main St
2023-01-20,-89.50,Electricity bill,utility,utilities,123 Main St
2023-02-01,-1200.00,Insurance premium,insurance,insurance,123 Main St
```

#### Incoming Payments Example:
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference
2023-01-01,2500.00,Monthly rent payment,deposit,rent,123 Main St,john@email.com
2023-02-01,2500.00,Monthly rent payment,deposit,rent,123 Main St,john@email.com
2023-01-15,50.00,Late payment fee,deposit,fees,123 Main St,john@email.com
```

#### Landlord Payments Example:
```csv
transaction_date,amount,description,transaction_type,category,property_reference,counterparty_name
2023-01-31,-2000.00,Net rental payment to owner,payment,owner_payment,123 Main St,Property Owner Ltd
2023-02-28,-1950.00,Net rental payment to owner,payment,owner_payment,123 Main St,Property Owner Ltd
```

#### Mixed Transactions Example:
```csv
transaction_date,amount,description,transaction_type,category,subcategory,property_reference,customer_reference,bank_reference
2023-01-01,2500.00,Monthly rent,deposit,rent,,123 Main St,john@email.com,TXN001
2023-01-05,-150.00,Plumbing repair,maintenance,maintenance,repairs,123 Main St,,REP123
2023-01-31,-2000.00,Owner payment,payment,owner_payment,,123 Main St,,PAY456
2023-02-01,2500.00,Monthly rent,deposit,rent,,123 Main St,john@email.com,TXN002
```

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

## Transaction Types and Categories

### Available Transaction Types:
1. **`payment`** - Outgoing payments (expenses, owner payments)
2. **`deposit`** - Incoming payments (rent, deposits)
3. **`fee`** - Fees charged/received
4. **`commission`** - Commission payments
5. **`maintenance`** - Maintenance expenses
6. **`utility`** - Utility bills
7. **`insurance`** - Insurance payments
8. **`tax`** - Tax payments

### Common Categories:
- **Income:** `rent`, `deposits`, `fees`, `interest`, `other_income`
- **Expenses:** `maintenance`, `utilities`, `insurance`, `legal`, `advertising`, `management_fees`
- **Payments:** `owner_payment`, `contractor_payment`, `tax_payment`

---

## Data Validation Rules

### Required Validations:
1. **Date Format:** Must be parseable in supported formats
2. **Amount:** Must be valid decimal number
3. **Description:** Cannot be empty
4. **Transaction Type:** Must match available types

### Optional Validations:
1. **Property Reference:** Attempts to match existing properties
2. **Customer Reference:** Attempts to match existing customers
3. **Amounts:** Negative for expenses, positive for income (by convention)

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
1. Test with small sample first
2. Use consistent date formats
3. Include property/customer references for better matching
4. Use descriptive transaction descriptions
5. Validate data before upload when possible

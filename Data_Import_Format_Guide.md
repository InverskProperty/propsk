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

## ⚡ **IMPORT PHILOSOPHY - READ THIS FIRST**

### 🎯 Only Import ACTUAL TRANSACTIONS (Money Movements)

**DO Import:**
- ✅ Rent payments **RECEIVED** (money came in)
- ✅ Expenses **PAID** (money went out)
- ✅ Payments to owners **MADE** (money went out)
- ✅ Bank transfers **COMPLETED** (money moved)
- ✅ Fees **CHARGED** (money was deducted)

**DO NOT Import:**
- ❌ **Unpaid rent** - Invoices for rent that's due but unpaid (your lease system tracks this)
- ❌ **Unpaid contractor bills** - Invoice received but not yet paid (wait until you pay it)
- ❌ **Unpaid utility bills** - Bill arrived but payment not made yet (import when paid)
- ❌ **Unpaid supplier invoices** - Any bill/invoice you received but haven't paid
- ❌ **Future due dates** - Rent due next month, upcoming payments (not actual transactions)
- ❌ **Balance calculations** - Arrears totals, what's owed, running balances (these are calculations)
- ❌ **Projected payments** - Expected payments, scheduled transfers that haven't happened yet

**Key Rule:** If money hasn't actually moved (in or out of your bank account), don't import it.

### 📊 The CSV Header is a TEMPLATE Showing ALL Possibilities

**The comprehensive header shows every field the system CAN accept - not what you MUST fill in.**

**Fill what you have from your source data:**
- Source has banking reference? → Fill `bank_reference` column
- Source has lease reference? → Fill `lease_reference` column
- Source has property address? → Fill `property_reference` column
- Source doesn't have these? → **Leave those columns blank**

**Example from bank statement:**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
17/02/2025,740.00,,,,FLAT 5 - 3 WEST GATE,,,,,740.00,"FPS-REF-12345","Bank Transfer",,Rent received,OLD_ACCOUNT
```
✅ **Includes**: bank_reference (from bank statement), payment_method (from source)
✅ **Blank**: description, transaction_type, lease_reference (not in source - system will infer/generate)

**Example from simple spreadsheet:**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
17/02/2025,740.00,,,,FLAT 5 - 3 WEST GATE,,,,,740.00,,,,,OLD_ACCOUNT
```
✅ **Includes**: Only date, amount, property, incoming_transaction_amount (what the spreadsheet showed)
✅ **Blank**: Everything else (spreadsheet didn't have that info)

### 🔍 Real-World Example: Unpaid Rent Period

**Spreadsheet shows:**
- Rent DUE: £1,125 (on 22nd)
- Rent RECEIVED: "-" (nothing)
- Arrears: £1,125

**What to import:**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
```
**Nothing.** No payment received = no transaction to import. Leave CSV empty or with just headers.

**When the tenant pays later:**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
15/03/2025,1125.00,,,,Apartment F - Knighton Hayes,,,,,1125.00,"FPS-REF-67890","Bank Transfer",,Late payment received,OLD_ACCOUNT
```
**Now import it** - money actually moved.

### 📝 Common Transaction Scenarios - What to Import & When

#### 💰 **Payments to Property Owners**

**Scenario 1: Owner is OWED money (from rent received)**
```csv
# DON'T manually import this - system auto-creates when you import rent with incoming_transaction_amount
# The auto-split creates beneficiary_type="beneficiary" transaction automatically
```

**Scenario 2: You ACTUALLY PAID the owner (bank transfer made)**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
28/01/2025,-1500.00,"Payment to owner - January",,owner_payment,,"John Smith",,beneficiary_payment,,"TXN-123456","Bank Transfer","John Smith","Monthly distribution",OLD_ACCOUNT
```
**What's filled:** Date, negative amount, customer_reference (owner name), beneficiary_type=beneficiary_payment, bank_reference (if from bank statement)
**What's blank:** transaction_type (system infers "payment" from beneficiary_payment)
**Result:** Type inferred as "payment", decreases owner balance by £1,500, records actual payment made

#### 🔧 **Contractor & Supplier Payments**

**Scenario 1: Contractor invoice RECEIVED (unpaid)**
```
❌ DON'T IMPORT - No money has moved yet
Wait until you actually pay the invoice
```

**Scenario 2: You PAID the contractor invoice**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
15/02/2025,-150.00,"Boiler repair",,maintenance,"Apartment F - Knighton Hayes",,,contractor,,"BACS-ABC-PLU","Bank Transfer","ABC Plumbing","Emergency repair - invoice #1234",OLD_ACCOUNT
```
**What's filled:** Date, negative amount, description, category=maintenance, property, beneficiary_type=contractor, counterparty_name, bank_reference
**What's blank:** transaction_type (system infers "expense" from contractor)
**Result:** Type inferred as "expense", records contractor payment

**Scenario 3: Paid contractor from bank statement**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
15/02/2025,-150.00,,,,"Apartment F - Knighton Hayes",,,contractor,,"BACS PAYMENT TO ABC PLUMBING REF INV1234","BACS","ABC Plumbing",,OLD_ACCOUNT
```
**What's filled:** Date, amount, property, beneficiary_type=contractor, bank_reference (full bank description), payment_method, counterparty_name
**What's blank:** transaction_type (system infers "expense" from contractor), description (auto-generated from bank_reference)
**Result:** Type inferred as "expense", description auto-generated, records contractor payment

#### 💡 **Utility Bills & Regular Expenses**

**Scenario 1: Utility bill ARRIVED (unpaid)**
```
❌ DON'T IMPORT - Bill received but not paid yet
Example: "Thames Water bill for £89 arrived today"
Wait until the payment leaves your account
```

**Scenario 2: Utility bill PAID**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
10/02/2025,-89.00,"Thames Water - January",,utilities,"Boden House NG10",,,,,,"Direct Debit","Thames Water","Account #12345",OLD_ACCOUNT
```
**What's filled:** Date, negative amount, description, category=utilities, property, payment_method, counterparty_name
**What's blank:** transaction_type (system infers "payment" from negative amount)
**Result:** Type inferred as "payment", records utility expense

**Scenario 3: Multiple utility bills paid (from bank statement)**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
05/02/2025,-125.50,,,utilities,"123 Main St",,,,,,"DD BRITISH GAS 123456789","Direct Debit","British Gas",,OLD_ACCOUNT
10/02/2025,-89.00,,,utilities,"123 Main St",,,,,,"DD THAMES WATER ACC12345","Direct Debit","Thames Water",,OLD_ACCOUNT
15/02/2025,-45.00,,,utilities,"123 Main St",,,,,,"DD EDF ENERGY REF9876","Direct Debit","EDF Energy",,OLD_ACCOUNT
```
**What's filled:** Date, amount, category=utilities, property, bank_reference (DD = Direct Debit reference), payment_method, counterparty_name
**What's blank:** transaction_type (system infers "payment" from negative amount), description (auto-generated from bank_reference)
**Result:** Type inferred as "payment", descriptions auto-generated from bank_reference, categorized as utilities

#### 🧾 **General Expenses (Management/Agency Costs)**

**Scenario: You paid for property management expense**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
20/02/2025,-350.00,"Safety certificates - annual inspection",,compliance,"Block Property - Boden House",,,,,,"Bank Transfer","Gas Safe Inspector","Annual gas safety checks",OLD_ACCOUNT
```
**What's filled:** Date, negative amount, description, category=compliance, property, payment_method, counterparty_name, notes
**What's blank:** transaction_type (system infers "payment" from negative amount)
**Result:** Type inferred as "payment", records compliance expense

### 🎯 Quick Decision Guide

**"Should I import this?"**
1. ✅ **Has money left/entered your bank?** → Yes, import it
2. ❌ **Is it a bill you received but haven't paid?** → No, don't import (wait until paid)
3. ❌ **Is it money someone owes you but hasn't paid?** → No, don't import (wait until received)
4. ❌ **Is it a future scheduled payment?** → No, don't import (wait until it executes)

---

## 1. Historical Transaction Import Formats

### CSV Format (Simple Import)

**📋 Standard CSV Template (Always Use This Header):**

This header shows ALL available fields. Leave blank any fields your source data doesn't provide.

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
  - **RECOMMENDED:** Leave blank and let system infer (it's smart about context)
  - **AUTO-INFERRED** if empty using this logic:
    1. `incoming_transaction_amount` present → `payment` (rent received with auto-split)
    2. `beneficiary_type=contractor` → `expense` (contractor payment)
    3. `beneficiary_type=beneficiary/beneficiary_payment` → `payment` (owner allocation/payment)
    4. Positive amount → `invoice` (money coming in)
    5. Negative amount → `payment` (money going out)
    6. Default → `payment`

  **Understanding the Types:**
  - **`payment`** - Generic type used for MOST transactions (rent received, expenses paid, owner payments)
  - **`invoice`** - Money coming IN (rent receivable) - use when amount is positive and no incoming_transaction_amount
  - **`expense`** - Specific outgoing payment for goods/services - auto-set for contractors
  - **`fee`** - Fees charged/received (management fees, commission)
  - **`maintenance`** - Maintenance expenses (legacy - system converts to `expense`)
  - **`adjustment`** - Balance corrections, manual adjustments
  - **`deposit`** - Deposits held (legacy - system treats as `invoice`)
  - **`withdrawal`** - Withdrawals (legacy - system treats as `payment`)

  **Key Insight:** "payment" is the catch-all generic type - used for rent received, expenses paid, and owner payments. The system distinguishes these using `beneficiary_type`, `incoming_transaction_amount`, and `category` instead.

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

- **`bank_reference`** - Bank transaction reference (from bank statement)
  - **Fill if present in source data** (bank statement, banking system export)
  - **Leave blank if not present** (spreadsheet without banking details)
  - **Used for auto-generating description** if description is empty
  - Examples: `"FPS JOHN SMITH RENT JAN"`, `"FASTER PMT ABC PLUMBING"`, `"REF-12345"`
  - Typical sources: Bank statement downloads, Xero/QuickBooks exports, banking APIs

- **`payment_method`** - Method of payment
  - **Fill if known from source data**
  - **Leave blank if not specified in source**
  - Examples: `"Bank Transfer"`, `"Cash"`, `"Cheque"`, `"Direct Debit"`
  - Typical sources: Bank statements (shows "FPS", "BACS"), accounting exports

- **`counterparty_name`** - Name of other party in transaction
  - **Fill if present in source data** (who sent/received the money)
  - **Leave blank if not specified**
  - **Used for auto-generating description** if description is empty
  - Examples: `"ABC Maintenance Ltd"`, `"John Smith"`, `"Thames Water"`
  - Typical sources: Bank statements, invoice systems, payment records

- **`source_reference`** - Reference to source document
  - **Optional** - use if tracking import batches or source files
  - Examples: `"INV001"`, `"STMT202301"`, `"IMPORT-2025-01"`

- **`notes`** - Additional notes
  - Format: Free text

### CSV Examples

**All examples use the SAME comprehensive header** - only difference is which fields are filled vs left blank:

```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
```

#### 🎯 Example 1: From Bank Statement Export (Banking References Present)
**Source: Downloaded CSV from online banking - includes banking references and payment methods:**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
17/02/2025,740.00,,,,FLAT 5 - 3 WEST GATE,,,,,740.00,"FPS-12345","Bank Transfer","John Smith",,OLD_ACCOUNT
19/02/2025,700.00,,,,FLAT 7 - 3 WEST GATE,,,,,700.00,"FPS-67890","Bank Transfer","Sarah Jones",,OLD_ACCOUNT
20/02/2025,-150.00,,,,,,,,,,"BACS-ABC-PLU","BACS","ABC Plumbing",,OLD_ACCOUNT
```
**What's filled:** Date, amount, property, incoming_transaction_amount, bank_reference, payment_method, counterparty_name (all from bank export)
**What's blank:** description, transaction_type, lease_reference (not in bank export)
**Result:** System uses bank_reference for description, infers transaction types, processes payments correctly

#### 📋 Example 2: From Simple Spreadsheet (No Banking References)
**Source: Property management spreadsheet - only has property, date, amount:**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,beneficiary_type,incoming_transaction_amount,bank_reference,payment_method,counterparty_name,notes,payment_source
17/02/2025,740.00,,,,FLAT 5 - 3 WEST GATE,,,,,740.00,,,,,OLD_ACCOUNT
19/02/2025,700.00,,,,FLAT 7 - 3 WEST GATE,,,,,700.00,,,,,OLD_ACCOUNT
06/03/2025,-3080.00,,,,"Boden House NG10",,,,,,,,,,OLD_ACCOUNT
```
**What's filled:** Date, amount, property, incoming_transaction_amount (what the spreadsheet had)
**What's blank:** bank_reference, payment_method, counterparty_name (spreadsheet doesn't track this)
**Result:** System auto-generates descriptions from property+amount, infers types, processes correctly

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

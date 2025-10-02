# 🔍 CSV Import Validation & Matching Guide

## 📋 Overview

This guide explains **exactly what happens** when you upload a CSV file to the CRM import system, including how properties and tenants are matched, what validation occurs, and what happens when things don't match perfectly.

---

## 🚀 The Import Process (Step-by-Step)

### Step 1: File Upload & Parsing
```
You upload → System reads CSV → Parses header row → Creates column mapping
```

**What happens:**
1. System reads the header row
2. Converts all headers to lowercase: `"Property_Reference"` → `"property_reference"`
3. Creates a map: `{" transaction_date": 0, "amount": 1, "description": 2, ...}`
4. Generates unique batch ID: `HIST_CSV_20251002_143052`

### Step 2: Line-by-Line Processing
```
For each data row → Parse values → Validate → Match property/customer → Save or Error
```

---

## ✅ Validation Rules

### 1️⃣ **Required Fields** (Will FAIL if missing):

| Field | Validation | Error Message |
|-------|------------|---------------|
| `transaction_date` | Must be parseable | "Transaction date is required" |
| `amount` | Must be numeric | "For input string: 'abc'" (Java error) |
| `description` | Must not be empty | "Transaction description required" |
| `transaction_type` | Must match enum | "Invalid transaction type: xyz" |

### 2️⃣ **Date Parsing** (Multiple formats supported):

The system tries these formats **in order**:

```java
✅ "yyyy-MM-dd"    → 2025-05-06
✅ "dd/MM/yyyy"    → 06/05/2025
✅ "MM/dd/yyyy"    → 05/06/2025
✅ "dd-MM-yyyy"    → 06-05-2025
✅ "yyyy/MM/dd"    → 2025/05/06
```

**If NONE match:**
```
ERROR: "Invalid date format: 06-May-2025. Supported formats: yyyy-MM-dd, dd/MM/yyyy, MM/dd/yyyy, dd-MM-yyyy, yyyy/MM/dd"
```

### 3️⃣ **Transaction Type Validation** (Smart mapping):

The system is **very smart** about transaction types:

**Direct Match (case-insensitive):**
```csv
payment, invoice, expense, maintenance, deposit, withdrawal, transfer, fee, refund, adjustment
```

**Smart Mapping (these get converted automatically):**

| What You Write | System Converts To | Why |
|----------------|-------------------|-----|
| `maintenance`, `repair`, `contractor`, `upkeep` | `maintenance` | Contains maintenance keywords |
| `payment_to_contractor` | `maintenance` | Contractor payment |
| `service_payment` | `maintenance` | Service-related |
| `rent`, `rental`, `rental_payment`, `parking` | `payment` | Rent-related |
| `management_fee`, `commission`, `service_fee` | `fee` | Fee-related |
| `owner_payment`, `payment_to_beneficiary` | `payment` | Beneficiary payment |

**Example conversions:**
```csv
Input: "Boiler repair"          → ERROR (not a valid type)
Input: "maintenance"             → ✅ TransactionType.maintenance
Input: "payment_to_contractor"   → ✅ TransactionType.maintenance (smart mapped)
Input: "rent"                    → ✅ TransactionType.payment (smart mapped)
```

⚠️ **IMPORTANT:** For your conversions, use the exact values from the guide:
- Rent: `payment`
- Fees: `fee`
- Expenses: `expense` or `maintenance`
- Owner liability: `adjustment`

### 4️⃣ **Payment Source Validation**:

**Valid values (case-insensitive):**
```csv
OLD_ACCOUNT
PAYPROP
BOTH
```

**Invalid values cause error:**
```
ERROR: "Invalid payment source: old_account. Valid sources: OLD_ACCOUNT, PAYPROP, BOTH"
```

---

## 🏠 Property Matching Algorithm

### How It Works:

When you provide `property_reference` in your CSV, the system tries to find a matching property using this logic:

```java
findPropertyByReference("FLAT 1 - 3 WEST GATE") {
    // Step 1: Try EXACT name match (case-insensitive)
    for each property in database:
        if "FLAT 1 - 3 WEST GATE".equalsIgnoreCase(property.propertyName):
            ✅ MATCH FOUND - return property

    // Step 2: Try address line match (contains, case-insensitive)
    for each property in database:
        if property.addressLine1 exists:
            if "FLAT 1 - 3 WEST GATE".contains(property.addressLine1):
                ✅ MATCH FOUND - return property

    // Step 3: Try postcode match (contains, case-insensitive)
    for each property in database:
        if property.postcode exists:
            if "FLAT 1 - 3 WEST GATE".contains(property.postcode):
                ✅ MATCH FOUND - return property

    // Step 4: No match found
    ⚠️ log warning: "Could not find property for reference: FLAT 1 - 3 WEST GATE"
    return NULL (transaction saved without property link)
}
```

### Matching Examples:

#### ✅ **Exact Match**
```csv
CSV: FLAT 1 - 3 WEST GATE
DB:  propertyName = "FLAT 1 - 3 WEST GATE"
Result: ✅ PERFECT MATCH
```

#### ✅ **Case Insensitive Match**
```csv
CSV: flat 1 - 3 west gate
DB:  propertyName = "FLAT 1 - 3 WEST GATE"
Result: ✅ MATCHED (equalsIgnoreCase)
```

#### ✅ **Address Contains Match**
```csv
CSV: Unit at 3 West Gate, Boden House
DB:  propertyName = "FLAT 1"
     addressLine1 = "3 West Gate"
Result: ✅ MATCHED (address contains "3 West Gate")
```

#### ⚠️ **No Match - Transaction Still Saved**
```csv
CSV: FLAT 1 - WEST GATE (missing "3")
DB:  propertyName = "FLAT 1 - 3 WEST GATE"
Result: ⚠️ NO MATCH
        Transaction saved with property = NULL
        Warning logged: "Could not find property for reference: FLAT 1 - WEST GATE"
```

### What Happens if Property Doesn't Match?

**The transaction is STILL saved!** It just has `property = NULL`.

```
Transaction saved:
- ✅ Date, amount, description all saved
- ✅ Transaction stored in database
- ⚠️ property field = NULL
- ⚠️ Warning in import log
- ✅ Import shows as "successful" (not failed)
```

**Later, you can:**
1. View unmatched transactions
2. Manually link them to properties
3. Or re-import with corrected property names

---

## 👤 Customer/Tenant Matching Algorithm

### How It Works:

```java
findCustomerByReference("MS O SMOLIARENKO & MR I HALII") {
    // Step 1: Try EMAIL match (exact)
    customer = findByEmail("MS O SMOLIARENKO & MR I HALII")
    if customer exists:
        ✅ MATCH FOUND - return customer

    // Step 2: Try NAME match (contains, case-insensitive)
    customers = findByNameContaining("MS O SMOLIARENKO & MR I HALII")
    if customers found:
        ✅ MATCH FOUND - return first customer in list

    // Step 3: No match found
    ⚠️ log warning: "Could not find customer for reference: MS O SMOLIARENKO & MR I HALII"
    return NULL (transaction saved without customer link)
}
```

### Matching Examples:

#### ✅ **Email Match (Best)**
```csv
CSV: sajidkazmi@propsk.com
DB:  customer.email = "sajidkazmi@propsk.com"
Result: ✅ PERFECT EMAIL MATCH
```

#### ✅ **Name Contains Match**
```csv
CSV: MS O SMOLIARENKO & MR I HALII
DB:  customer.name = "O SMOLIARENKO"
Result: ✅ MATCHED (name contains "SMOLIARENKO")
```

#### ✅ **Partial Name Match**
```csv
CSV: John Smith
DB:  customer.name = "Mr John Smith"
Result: ✅ MATCHED (contains "John Smith")
```

#### ⚠️ **No Match - Transaction Still Saved**
```csv
CSV: J Smith
DB:  customer.name = "John Smith"
Result: ⚠️ NO MATCH (doesn't contain "J Smith")
        Transaction saved with customer = NULL
```

### What Happens if Customer Doesn't Match?

**Same as property - transaction is STILL saved!**

```
Transaction saved:
- ✅ All data saved
- ⚠️ customer field = NULL
- ⚠️ Warning logged
```

---

## 📊 Import Result Summary

After upload completes, you'll see:

```
Import Summary:
Batch ID: HIST_CSV_20251002_143052
Source File: may_2025_statements.csv
Total Processed: 150
Successful: 145
Failed: 5

Errors:
Line 12: Invalid date format: 06-May-2025
Line 34: Invalid transaction type: rental
Line 56: For input string: "£795.00" (remove £ symbol)
Line 89: Transaction date is required
Line 120: Invalid payment source: old
```

### Success vs. Failure:

| Result | Meaning | Transaction Saved? |
|--------|---------|-------------------|
| **Successful** | All required fields valid | ✅ YES |
| **Successful** (with warnings) | Required fields valid, property/customer not matched | ✅ YES (but property/customer = NULL) |
| **Failed** | Required field missing or invalid | ❌ NO - not saved |

---

## 🎯 Best Practices for Clean Imports

### 1️⃣ **Property Names - Be Exact!**

**❌ Bad (won't match):**
```csv
property_reference
Flat 1
FLAT 1
Flat 1 West Gate
Flat 1, Boden House
```

**✅ Good (exact match):**
```csv
property_reference
FLAT 1 - 3 WEST GATE
FLAT 2 - 3 WEST GATE
Apartment F - Knighton Hayes
```

**💡 Tip:** Copy property names EXACTLY from your CRM property list.

### 2️⃣ **Customer Names - Use Emails When Possible**

**✅ Best (email match):**
```csv
customer_reference
tenant@email.com
landlord@propsk.com
```

**⚠️ Okay (name match):**
```csv
customer_reference
MS O SMOLIARENKO
John Smith
```

**❌ Risky (might not match):**
```csv
customer_reference
O SMOL
J Smith
```

### 3️⃣ **Test Import First**

Create a **small test CSV** with 5-10 transactions:
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source
2025-05-06,795.00,Rent Received - May 2025,payment,rent,FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO & MR I HALII,,,Monthly rent collection,OLD_ACCOUNT
2025-05-06,-119.00,Management Fee - 15%,fee,commission,FLAT 1 - 3 WEST GATE,,,,15% management fee on £795,OLD_ACCOUNT
```

Upload and verify:
- ✅ Properties match correctly
- ✅ No errors
- ✅ Dates parse correctly

Then import the full dataset.

### 4️⃣ **Check Your Property List Before Importing**

Query your database to get exact property names:

```sql
SELECT id, property_name, address_line1, postcode
FROM properties
ORDER BY property_name;
```

Use these EXACT names in your CSV.

---

## 🔍 Common Import Scenarios

### Scenario 1: Perfect Match
```csv
CSV Row:
FLAT 1 - 3 WEST GATE,795.00,Rent Received,...

Database:
property_name = "FLAT 1 - 3 WEST GATE"

Result:
✅ Transaction saved
✅ property linked
✅ All data perfect
Import Status: Successful
```

### Scenario 2: Property Not Found
```csv
CSV Row:
Flat 1,795.00,Rent Received,...

Database:
property_name = "FLAT 1 - 3 WEST GATE"

Result:
✅ Transaction saved
⚠️ property = NULL
⚠️ Warning logged
Import Status: Successful (but with warning)

Later Action Required:
- Find unmatched transactions
- Manually link to correct property
- OR re-import with correct name
```

### Scenario 3: Invalid Data
```csv
CSV Row:
FLAT 1 - 3 WEST GATE,£795.00,Rent Received,rental,rent,OLD_ACCOUNT

Result:
❌ Transaction NOT saved
❌ Error: "For input string: '£795.00'"
Import Status: Failed

Fix Required:
- Remove £ symbol: 795.00
- OR fix transaction_type: "rental" → "payment"
- Re-import fixed row
```

### Scenario 4: Portfolio Payment (No Property)
```csv
CSV Row:
PORTFOLIO PAYMENT,-17111.56,Actual Owner Payment,...

Database:
No property named "PORTFOLIO PAYMENT"

Result:
✅ Transaction saved
⚠️ property = NULL (THIS IS EXPECTED!)
⚠️ Warning logged (safe to ignore)
Import Status: Successful

This is CORRECT - portfolio payments don't have a specific property!
```

---

## 📝 Pre-Import Checklist

Before uploading your CSV, verify:

### File Format:
- [ ] File saved as .csv (UTF-8 encoding)
- [ ] Exactly 11 columns
- [ ] Header row matches: `transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source`
- [ ] No extra columns
- [ ] No empty rows between data

### Data Quality:
- [ ] All dates in YYYY-MM-DD format (e.g., 2025-05-06)
- [ ] No currency symbols (£, $, etc.) in amounts
- [ ] Income = positive, expenses = negative
- [ ] Property names match EXACTLY (copy from database)
- [ ] Transaction types are valid (payment, fee, expense, maintenance, adjustment)
- [ ] Payment sources are valid (OLD_ACCOUNT, PAYPROP, BOTH)

### Test Sample:
- [ ] Created 5-10 row test file
- [ ] Uploaded test file first
- [ ] Verified matches work
- [ ] Reviewed import summary
- [ ] Fixed any errors

---

## 🆘 Troubleshooting Common Errors

### Error: "Invalid date format"
**Problem:** Date not in supported format
**Solution:** Convert to YYYY-MM-DD
```
Wrong: 06/05/25, 06-May-2025, May 6 2025
Right: 2025-05-06
```

### Error: "Invalid transaction type"
**Problem:** Type not recognized
**Solution:** Use valid types
```
Wrong: rental, rent_payment, landlord_payment
Right: payment, fee, expense, maintenance, adjustment
```

### Error: "For input string: '£795.00'"
**Problem:** Currency symbol in amount
**Solution:** Remove all symbols
```
Wrong: £795.00, $100, 1,200.00
Right: 795.00, 100, 1200.00
```

### Error: "Invalid payment source"
**Problem:** Incorrect case or value
**Solution:** Use exact values
```
Wrong: old_account, propsk_old, payprop
Right: OLD_ACCOUNT, PAYPROP, BOTH
```

### Warning: "Could not find property"
**Problem:** Property name doesn't match database
**Solution:** Copy exact name from database
```
Your CSV: Flat 1
Database:  FLAT 1 - 3 WEST GATE
Fix:      Use "FLAT 1 - 3 WEST GATE"
```

---

## ✅ Summary

### What Gets Validated:
1. **Required fields** - Must be present and valid
2. **Date format** - Must match supported formats
3. **Amount** - Must be numeric (no symbols)
4. **Transaction type** - Must be valid enum value
5. **Payment source** - Must be OLD_ACCOUNT, PAYPROP, or BOTH

### What's Flexible (Warnings Only):
1. **Property matching** - Transaction saved even if no match
2. **Customer matching** - Transaction saved even if no match
3. **Optional fields** - Can be empty

### The Import Is Forgiving:
- ✅ Transactions save even if property/customer don't match
- ✅ Warnings don't fail the import
- ✅ You can fix matches later
- ✅ Smart type mapping helps with variations

### The Import Is Strict On:
- ❌ Date format must be parseable
- ❌ Amount must be numeric
- ❌ Required fields must exist
- ❌ Transaction type must be valid

---

**Your CSV will import successfully if you follow the format guide and use exact property names!** 🎯

# 🔄 Boden House Spreadsheet to CSV Import Conversion Guide

## 📋 Overview

This guide shows you **exactly how to convert** your existing Boden House property statement spreadsheet format into the CSV format required by the CRM import system.

## 🎯 The Challenge

Your **Boden House spreadsheet** stores data in an **aggregated format** (one row per property per month with all transactions summarized), but the CRM import system expects **individual transaction rows** (one row per transaction event).

### Your Current Format (Aggregated):
```
FLAT 1 - 3 WEST GATE | Tenant: MS O SMOLIARENKO | Rent Due: £795 | Rent Received: £795 on 06/05/2025 | Management Fee: (£119) | Net to Owner: £676
```

### Required Format (Individual Transactions):
```csv
2025-05-06,795.00,Monthly rent - May 2025,payment,rent,FLAT 1 - 3 WEST GATE,...
2025-05-06,-119.00,Management fee - 15%,fee,commission,FLAT 1 - 3 WEST GATE,...
2025-05-27,676.00,Owner payment,payment,owner_payment,FLAT 1 - 3 WEST GATE,...
```

---

## 📊 Required CSV Structure

Your CSV must have **exactly 11 columns** in this order:

```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source
```

### Column Specifications:

| Column | Required | Format | Your Source Column |
|--------|----------|--------|-------------------|
| `transaction_date` | ✅ Yes | YYYY-MM-DD | "Rent Received Date" (convert from DD/MM/YYYY) |
| `amount` | ✅ Yes | Decimal | Various amount columns (see mapping below) |
| `description` | ✅ Yes | Text | Construct from transaction type + property |
| `transaction_type` | ✅ Yes | payment/fee/expense | Determine by transaction (see mapping) |
| `category` | ✅ Yes | rent/commission/etc | Determine by transaction type |
| `property_reference` | ✅ Yes | Property name | "Unit No." column |
| `customer_reference` | ❌ Optional | Tenant name | "Tenant" column |
| `bank_reference` | ❌ Optional | Reference | Leave empty |
| `payment_method` | ❌ Optional | Text | "Old Account" / "PayProp" |
| `notes` | ❌ Optional | Text | "Comments" column + constructed notes |
| `payment_source` | ✅ Yes | OLD_ACCOUNT/PAYPROP | Based on "Payment Source" column |

---

## 🔄 Conversion Process: Row-by-Row Breakdown

### Example: Converting FLAT 1 - 3 WEST GATE (May 2025)

#### Your Spreadsheet Row (Partial):
```
Unit No.: FLAT 1 - 3 WEST GATE
Tenant: MS O SMOLIARENKO & MR I HALII
Tenancy Dates: 27/04/2024
Rent Due Date: 27
Rent Due Amount: 795
Payment Source: 2 (Old Account)
Paid to Propsk Old Account: TRUE
Paid to Propsk PayProp: FALSE
Rent Received Date: 06/05/2025
Rent Received Amount: 795
Management Fee (%): 15%
Management Fee (£): (119)
Total Fees Charged by Propsk: (119)
Expense 1 Label: [empty]
Expense 1 Amount: -
Total Expenses: -
Net Due to Prestvale: 676
Comments: [empty]
```

#### Converts To → Multiple CSV Rows:

```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source
2025-05-27,795.00,Rent Due - May 2025,invoice,rent,FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO & MR I HALII,,,Rent due on 27th,OLD_ACCOUNT
2025-05-06,795.00,Rent Received - May 2025,payment,rent,FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO & MR I HALII,,,Monthly rent collection,OLD_ACCOUNT
2025-05-06,-119.00,Management Fee - 15%,fee,commission,FLAT 1 - 3 WEST GATE,,,,15% management fee on £795,OLD_ACCOUNT
2025-05-27,-676.00,Owner Payment - May 2025,payment,owner_payment,FLAT 1 - 3 WEST GATE,,,,Net rental payment to landlord,OLD_ACCOUNT
```

---

## 📐 Transaction Type Mapping Rules

For **each property row** in your spreadsheet, you create **multiple CSV transaction rows**:

### 1️⃣ **Rent Due (Invoice)**
**When to create:** If "Rent Due Amount" > 0

| Field | Value | Source |
|-------|-------|--------|
| `transaction_date` | First day of month or rent due date | Use: "YYYY-MM-{Rent Due Date}" (e.g., 2025-05-27) |
| `amount` | Positive amount | "Rent Due Amount" column |
| `description` | "Rent Due - {Month} {Year}" | Construct: e.g., "Rent Due - May 2025" |
| `transaction_type` | `invoice` | Fixed value |
| `category` | `rent` | Fixed value |
| `property_reference` | Property name | "Unit No." column |
| `customer_reference` | Tenant name | "Tenant" column |
| `payment_source` | OLD_ACCOUNT or PAYPROP | "Payment Source" (2=OLD_ACCOUNT, 3=PAYPROP) |

### 2️⃣ **Rent Received (Payment)**
**When to create:** If "Rent Received Amount" > 0

| Field | Value | Source |
|-------|-------|--------|
| `transaction_date` | Actual receipt date | "Rent Received Date" (convert DD/MM/YYYY → YYYY-MM-DD) |
| `amount` | Positive amount | "Rent Received Amount" column |
| `description` | "Rent Received - {Month} {Year}" | Construct from period |
| `transaction_type` | `payment` | Fixed value |
| `category` | `rent` | Fixed value |
| `property_reference` | Property name | "Unit No." column |
| `customer_reference` | Tenant name | "Tenant" column |
| `notes` | "Monthly rent collection" | Add context |
| `payment_source` | OLD_ACCOUNT or PAYPROP | Based on "Paid to Propsk Old Account" / "Paid to Propsk PayProp" |

### 3️⃣ **Management Fee**
**When to create:** If "Management Fee Amount Total" ≠ 0

| Field | Value | Source |
|-------|-------|--------|
| `transaction_date` | Same as rent received date | "Rent Received Date" |
| `amount` | **Negative** amount | "Management Fee Amount Total" (already negative in sheet) |
| `description` | "Management Fee - {percentage}%" | Use "Management Fee (%)" column |
| `transaction_type` | `fee` | Fixed value |
| `category` | `commission` | Fixed value |
| `property_reference` | Property name | "Unit No." column |
| `notes` | "{percentage}% management fee on £{rent}" | Construct from data |
| `payment_source` | OLD_ACCOUNT or PAYPROP | Match the rent payment source |

### 4️⃣ **Expenses** (Up to 4 per property)
**When to create:** If "Expense 1 Amount" (or 2, 3, 4) ≠ 0

**Example for Expense 1:**

| Field | Value | Source |
|-------|-------|--------|
| `transaction_date` | Same as rent received date | "Rent Received Date" (or best estimate) |
| `amount` | **Negative** amount | "Expense 1 Amount" |
| `description` | Expense label + comment | "Expense 1 Label" + " - " + "Expense 1 Comment" |
| `transaction_type` | `expense` or `maintenance` | Determine from label (see [Expense Type Logic](#expense-type-logic)) |
| `category` | Expense category | Determine from label |
| `property_reference` | Property name | "Unit No." column |
| `notes` | Full comment | "Expense 1 Comment" |
| `payment_source` | OLD_ACCOUNT or PAYPROP | "Expense 1 Source" (2=OLD_ACCOUNT, 3=PAYPROP) |

**Repeat for Expense 2, Expense 3, Expense 4** using their respective columns.

### 5️⃣ **Owner Payment (Net to Landlord)**
**When to create:** If "Net Due to Prestvale" ≠ 0

| Field | Value | Source |
|-------|-------|--------|
| `transaction_date` | End of period or payment date | Use last day of month (e.g., 2025-05-31) |
| `amount` | **Negative** amount (outgoing) | "Net Due to Prestvale" (negate if positive) |
| `description` | "Owner Payment - {Month} {Year}" | Construct: e.g., "Owner Payment - May 2025" |
| `transaction_type` | `payment` | Fixed value |
| `category` | `owner_payment` | Fixed value |
| `property_reference` | Property name | "Unit No." column |
| `notes` | "Net rental payment to landlord" | Standard note |
| `payment_source` | OLD_ACCOUNT or PAYPROP | Based on which account column has value |

---

## 🧠 Expense Type Logic

Determine `transaction_type` and `category` from "Expense X Label":

| If Label Contains | transaction_type | category |
|-------------------|------------------|----------|
| "Clean", "Cleaning" | `maintenance` | `cleaning` |
| "Repair", "Fix" | `maintenance` | `maintenance` |
| "Plumb", "Drain" | `maintenance` | `plumbing` |
| "Electric", "Electrical" | `maintenance` | `electrical` |
| "Heat", "Boiler", "Gas" | `maintenance` | `heating` |
| "White Goods", "Appliance" | `expense` | `white_goods` |
| "Bed", "Furnish", "Furniture" | `expense` | `furnishing` |
| "End of Tenancy" | `maintenance` | `clearance` |
| Anything else | `expense` | `general` |

---

## 📅 Date Conversion Rules

### Converting "Rent Received Date" (DD/MM/YYYY → YYYY-MM-DD):

**Your format:** `06/05/2025`
**Required format:** `2025-05-06`

**Excel Formula:**
```excel
=TEXT(DATE(RIGHT(A2,4), MID(A2,4,2), LEFT(A2,2)), "yyyy-mm-dd")
```

**Manual conversion:**
- `06/05/2025` → `2025-05-06`
- `19/05/2025` → `2025-05-19`
- `02/05/2025` → `2025-05-02`

### Date for Each Transaction Type:

| Transaction | Use This Date |
|-------------|---------------|
| Rent Due | `YYYY-MM-{Rent Due Date}` (e.g., 2025-05-27 for 27th) |
| Rent Received | "Rent Received Date" (converted to YYYY-MM-DD) |
| Management Fee | Same as Rent Received |
| Expenses | Same as Rent Received (or actual expense date if known) |
| Owner Payment | Last day of month (e.g., 2025-05-31) |

---

## 💡 Payment Source Mapping

Your spreadsheet uses codes in "Payment Source" column:

| Your Code | CSV Value | When to Use |
|-----------|-----------|-------------|
| `2` | `OLD_ACCOUNT` | "Paid to Propsk Old Account" = TRUE |
| `3` | `PAYPROP` | "Paid to Propsk PayProp" = TRUE |
| Both checked | `OLD_ACCOUNT` | Use OLD_ACCOUNT for old bank data |

---

## 📋 Complete Example: FLAT 18 with Expense

### Your Spreadsheet Row:
```
Unit No.: FLAT 18 - 3 WEST GATE
Tenant: MARIE DINKO
Rent Due Amount: 185
Rent Received Date: 15/05/2025
Rent Received Amount: 185
Management Fee (%): 15%
Management Fee Amount: (28)
Expense 1 Source: 2 (Old Account)
Expense 1 Label: End of Tenancy Clean
Expense 1 Amount: 200
Expense 1 Comment: End of Tenancy Clean
Total Expenses: (200)
Net Due to Prestvale: (43)
Payment Source: 2
```

### Converts To CSV:
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source
2025-05-28,185.00,Rent Due - May 2025,invoice,rent,FLAT 18 - 3 WEST GATE,MARIE DINKO,,,Rent due on 28th,OLD_ACCOUNT
2025-05-15,185.00,Rent Received - May 2025,payment,rent,FLAT 18 - 3 WEST GATE,MARIE DINKO,,,Monthly rent collection,OLD_ACCOUNT
2025-05-15,-28.00,Management Fee - 15%,fee,commission,FLAT 18 - 3 WEST GATE,,,,15% management fee on £185,OLD_ACCOUNT
2025-05-15,-200.00,End of Tenancy Clean,maintenance,clearance,FLAT 18 - 3 WEST GATE,,,,End of Tenancy Clean,OLD_ACCOUNT
2025-05-31,-43.00,Owner Payment - May 2025,payment,owner_payment,FLAT 18 - 3 WEST GATE,,,,Net rental payment to landlord (after expenses),OLD_ACCOUNT
```

**Note:** Net due is **negative** because expenses (£200) exceeded net rent after fees (£185 - £28 = £157).

---

## 📋 Complete Example: FLAT 7 with Arrears

### Your Spreadsheet Row:
```
Unit No.: FLAT 7 - 3 WEST GATE
Tenant: C TANG
Rent Due Amount: 700
Rent Received Date: 19/05/2025
Rent Received Amount: 1,400  ← DOUBLE PAYMENT (paying arrears)
Management Fee (%): 15%
Management Fee Amount: (210)  ← Fee on £1,400
Total Expenses: -
Net Due to Prestvale: 1,190
Rent Due Less Received: (700)  ← £700 in CREDIT (overpaid)
Payment Source: 2
```

### Converts To CSV:
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source
2025-05-19,700.00,Rent Due - May 2025,invoice,rent,FLAT 7 - 3 WEST GATE,C TANG,,,Rent due on 19th,OLD_ACCOUNT
2025-05-19,1400.00,Rent Received - May 2025,payment,rent,FLAT 7 - 3 WEST GATE,C TANG,,,Double payment - includes £700 arrears from previous month,OLD_ACCOUNT
2025-05-19,-210.00,Management Fee - 15%,fee,commission,FLAT 7 - 3 WEST GATE,,,,15% management fee on £1400,OLD_ACCOUNT
2025-05-31,-1190.00,Owner Payment - May 2025,payment,owner_payment,FLAT 7 - 3 WEST GATE,,,,Net rental payment to landlord,OLD_ACCOUNT
```

**Note:** The system will track the £700 credit automatically when statements are generated.

---

## 🏢 Complete Example: KNIGHTON HAYES with Different Fee

### Your Spreadsheet Row:
```
Unit No.: Apartment F - Knighton Hayes
Tenant: Riaz
Rent Due Amount: 1,125
Rent Received Date: 19/05/2025
Rent Received Amount: 1,125
Management Fee (%): 10%  ← DIFFERENT RATE (not 15%)
Management Fee Amount: (113)
Total Expenses: -
Net Due to Prestvale: 1,013
Tenant Balance: (1,125)  ← £1,125 in arrears
Payment Source: 2
```

### Converts To CSV:
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source
2025-05-22,1125.00,Rent Due - May 2025,invoice,rent,Apartment F - Knighton Hayes,Riaz,,,Rent due on 22nd,OLD_ACCOUNT
2025-05-19,1125.00,Rent Received - May 2025,payment,rent,Apartment F - Knighton Hayes,Riaz,,,Monthly rent collection,OLD_ACCOUNT
2025-05-19,-113.00,Management Fee - 10%,fee,commission,Apartment F - Knighton Hayes,,,,10% management fee on £1125,OLD_ACCOUNT
2025-05-31,-1013.00,Owner Payment - May 2025,payment,owner_payment,Apartment F - Knighton Hayes,,,,Net rental payment to landlord,OLD_ACCOUNT
```

**Note:** The negative tenant balance indicates tenant owes £1,125 from previous periods.

---

## 🚗 Handling Parking Spaces & Additional Income

### Your Spreadsheet Format:
```
PARKING SPACE 1: No rent shown in main columns
BUILDING ADDITIONAL INCOME: No rent shown in main columns
OFFICE: No rent shown in main columns
```

### How to Handle:

**If parking is included with a flat:**
- Include in the main flat's rent amount
- Add note: "Includes parking space 1 - £60"

**If parking is separate income:**
- Create a separate property in your system: "PARKING SPACE 1 - 3 WEST GATE"
- Create separate transactions for parking income

---

## 🔧 Excel/Google Sheets Conversion Template

### Step-by-Step Conversion Formula:

**Create a new sheet with these columns:**

1. **transaction_date** - Formula for rent received:
```excel
=TEXT(DATE(RIGHT(L2,4), MID(L2,4,2), LEFT(L2,2)), "yyyy-mm-dd")
```
Where L2 = "Rent Received Date" column

2. **amount** - For rent received:
```excel
=M2
```
Where M2 = "Rent Received Amount" column

3. **description** - For rent received:
```excel
="Rent Received - " & TEXT(L2, "MMMM YYYY")
```

4. **transaction_type** - For rent received:
```excel
="payment"
```

5. **category** - For rent received:
```excel
="rent"
```

6. **property_reference** - Unit number:
```excel
=B2
```
Where B2 = "Unit No." column

7. **customer_reference** - Tenant:
```excel
=C2
```
Where C2 = "Tenant" column

8. **payment_source** - Convert 2/3 to OLD_ACCOUNT/PAYPROP:
```excel
=IF(H2=TRUE, "OLD_ACCOUNT", IF(I2=TRUE, "PAYPROP", ""))
```
Where H2 = "Paid to Propsk Old Account", I2 = "Paid to Propsk PayProp"

---

## ⚠️ Important Validation Rules

### Before Importing:

✅ **Check All Dates**
- Must be YYYY-MM-DD format
- No future dates beyond reasonable limit
- No dates before property existed

✅ **Check All Amounts**
- Income = positive numbers
- Expenses = negative numbers
- Management fees = negative numbers
- Owner payments = negative numbers (outgoing)
- No currency symbols (£, $, etc.)
- Use decimal point, not comma (795.00, not 795,00)

✅ **Check Payment Sources**
- Only use: OLD_ACCOUNT, PAYPROP, BOTH
- Match actual payment routing

✅ **Check Property Names**
- Must exactly match properties in your CRM system
- Case sensitive: "FLAT 1 - 3 WEST GATE" ≠ "flat 1 - 3 west gate"

---

## 📊 Validation Checklist

Before importing your CSV, verify:

- [ ] File has exactly 11 columns
- [ ] Header row matches: `transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source`
- [ ] All dates in YYYY-MM-DD format
- [ ] All amounts are numeric (no £ symbols)
- [ ] Income amounts are positive
- [ ] Expense/fee amounts are negative
- [ ] Property references match your CRM exactly
- [ ] Payment sources are valid: OLD_ACCOUNT, PAYPROP, or BOTH
- [ ] File is saved as CSV (UTF-8 encoding)

---

## 🚀 Import Process

### Step 1: Prepare CSV File
1. Convert your spreadsheet using the formulas above
2. Export to CSV format
3. Verify format matches requirements

### Step 2: Upload to CRM
1. Navigate to: `/employee/transaction/import/csv`
2. Select your CSV file
3. Click "Import CSV File"

### Step 3: Review Results
- Check import summary
- Verify transaction counts
- Review any error messages
- Test with one property first before importing all

---

## 💡 Pro Tips

### 🎯 Start Small
Test with **1-2 properties first** to verify the conversion is correct before converting the entire spreadsheet.

### 🎯 Use a Template
Create a **reusable Excel template** with formulas that can convert each property row into multiple transaction rows automatically.

### 🎯 Batch by Period
Import **one month at a time** to make it easier to track and verify data.

### 🎯 Keep Originals
**Never delete your original spreadsheet** - keep it as a backup and reference.

### 🎯 Document Your Sources
Add meaningful notes to each transaction to track what period/source it came from.

---

## 🆘 Troubleshooting

### Issue: "Invalid date format"
**Solution:** Dates must be YYYY-MM-DD. Check for DD/MM/YYYY or MM/DD/YYYY formats.

### Issue: "Property not found"
**Solution:** Property names must exactly match your CRM. Check for:
- Extra spaces
- Different capitalization
- Spelling differences

### Issue: "Duplicate transactions"
**Solution:** The system detects duplicates. This is normal if you're re-importing data.

### Issue: "Negative rent received"
**Solution:** Check your amount columns - rent received should be positive, fees should be negative.

---

## 📞 Need Help?

If you encounter issues:
1. Check this guide's examples
2. Verify your CSV format matches exactly
3. Test with a small sample file first
4. Review error messages carefully

---

**You now have everything you need to convert your Boden House spreadsheet into the CRM import format!** 🎯

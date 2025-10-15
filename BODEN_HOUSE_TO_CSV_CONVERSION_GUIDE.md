# 🔄 Boden House Spreadsheet to CSV Import Conversion Guide

## 📋 Overview

This guide shows you **exactly how to convert** your existing Boden House property statement spreadsheet format into the CSV format required by the CRM import system.

## 🆕 Two-Phase Import Workflow (NEW!)

### Why Two Phases?

The CRM now supports **lease-based tracking**, which allows:
- Multiple tenants at the same property with different rent amounts
- Tracking arrears per-lease instead of per-property
- Historical lease analysis and reporting
- Better handling of mid-month tenant changes

### Phase 1: Import Leases (Do This FIRST!)

Before importing transactions, you must import your lease agreements. This creates a reference for each tenancy.

**Lease CSV Format (Flexible Customer Identification):**
```csv
property_reference,customer_name,customer_email,lease_start_date,lease_end_date,rent_amount,payment_day,lease_reference
FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO & MR I HALII,osana.smoliarenko@email.com,2025-02-27,,795.00,27,LEASE-BH-F1-2025
FLAT 2 - 3 WEST GATE,MR M K J AL BAYAT & MRS G B A AL ZANGANA,,2025-02-27,,740.00,27,LEASE-BH-F2-2025
FLAT 3 - 3 WEST GATE,,tom.whyte@email.com,2025-03-11,,740.00,11,LEASE-BH-F3-2025
```

**Customer Identification Options:**

The system supports **three ways** to identify customers:

1. **✅ RECOMMENDED: Both name AND email**
   ```csv
   FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO & MR I HALII,osana.smoliarenko@email.com,2025-02-27,,795.00,27,LEASE-BH-F1-2025
   ```
   - System matches by email first (most accurate)
   - Name used as fallback if email doesn't match
   - Best for auto-matching existing customers

2. **Name only (email empty)**
   ```csv
   FLAT 2 - 3 WEST GATE,MR M K J AL BAYAT & MRS G B A AL ZANGANA,,2025-02-27,,740.00,27,LEASE-BH-F2-2025
   ```
   - System suggests similar customers during import
   - You can select from dropdown or create new customer
   - Good for new tenants where you'll add email later

3. **Email only (name empty)**
   ```csv
   FLAT 3 - 3 WEST GATE,,tom.whyte@email.com,2025-03-11,,740.00,11,LEASE-BH-F3-2025
   ```
   - System matches by email
   - Name pulled from existing customer record
   - Useful when you only have email addresses

**Important Notes:**
- At least ONE of `customer_name` or `customer_email` must be provided
- If both are present, email takes priority for matching
- `lease_end_date` can be empty for ongoing leases
- `lease_reference` must be unique (e.g., LEASE-BH-F1-2025, LEASE-BH-F18-2025B)
- `payment_day` is the day of month rent is due (1-31)

**Import Location:** Navigate to `/employee/lease/import` to import leases.

---

### 🤖 Quick Conversion Using Claude UI

If you have a natural format CSV (like your Boden House spreadsheet), paste this prompt + your CSV into Claude UI to convert it:

**Prompt for Claude UI:**
```
Convert this CSV to the lease import format. Use these exact column names:
property_reference,customer_name,customer_email,lease_start_date,lease_end_date,rent_amount,payment_day,lease_reference

Rules:
1. Map "Unit Number" → property_reference
2. Map "Tenant Name(s)" → customer_name
3. Leave customer_email empty (I'll add it later if needed)
4. Convert "Lease Start Date" to YYYY-MM-DD format
5. Map "Lease End Date" to lease_end_date (empty if blank/ongoing)
6. Map "Monthly Rent" → rent_amount
7. Map "Payment Day" → payment_day
8. Map "Lease Reference" → lease_reference
9. Skip rows where Tenant Name is empty (vacant units)
10. Remove any extra columns (Management Fee %, Payment Account, Status, Notes)

Output ONLY the CSV with no explanation.

[PASTE YOUR CSV HERE]
```

**Example Input (Natural Format):**
```
Lease Reference,Unit Number,Tenant Name(s),Lease Start Date,Lease End Date,Payment Day,Monthly Rent,Management Fee %,Payment Account,Status,Notes
LEASE-BH-F1-2025,FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO & MR I HALII,2025-02-27,,27,795,15%,PayProp,Active,
LEASE-BH-F2-2025,FLAT 2 - 3 WEST GATE,MR M K J AL BAYAT & MRS G B A AL ZANGANA,2025-02-27,,27,740,15%,PayProp,Active,
LEASE-BH-PS3-2025,PARKING SPACE 3 - 3 WEST GATE,,,,,0,15%,PayProp,,
```

**Example Output (Import-Ready):**
```csv
property_reference,customer_name,customer_email,lease_start_date,lease_end_date,rent_amount,payment_day,lease_reference
FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO & MR I HALII,,2025-02-27,,795.00,27,LEASE-BH-F1-2025
FLAT 2 - 3 WEST GATE,MR M K J AL BAYAT & MRS G B A AL ZANGANA,,2025-02-27,,740.00,27,LEASE-BH-F2-2025
```

Notice: Empty parking space row was skipped automatically.

---

### Phase 2: Import Transactions (With Lease References)

After leases are imported, add a `lease_reference` column to your transaction CSV. The CRM will automatically link transactions to the correct lease.

**Updated CSV Structure (12 columns):**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source,lease_reference
2025-05-06,795.00,Rent Received - May 2025,payment,rent,FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO,,,,OLD_ACCOUNT,LEASE-FLAT1-2024
```

**What if lease_reference is missing?**
- The import will still work
- You'll be prompted to select the correct lease during the review process
- The system will suggest matching leases based on property + customer + date

**Benefits of Lease References:**
- Automatic linking to correct tenancy
- No manual matching required
- Supports multiple tenancies at same property
- Tracks payments that occur after lease ends (arrears)

### Creating Lease References

**Recommended Format:** `LEASE-{BLOCK_CODE}-{UNIT}-{YEAR}[SUFFIX]`

**Examples from Boden House:**
- `LEASE-BH-F1-2025` - Boden House, Flat 1, started 2025
- `LEASE-BH-F10-2025B` - Boden House, Flat 10, second tenant in 2025 (suffix B)
- `LEASE-BH-PS1-2025` - Boden House, Parking Space 1, started 2025
- `LEASE-KH-F-2024` - Knighton Hayes, Apartment F, started 2024

**For Mid-Year Tenant Changes (Use Letter Suffix):**
```csv
property_reference,customer_name,customer_email,lease_start_date,lease_end_date,rent_amount,payment_day,lease_reference
FLAT 10 - 3 WEST GATE,MR F PETER,,2025-03-06,2025-09-23,735.00,6,LEASE-BH-F10-2025
FLAT 10 - 3 WEST GATE,Anna Stoliarchuk,,2025-09-03,,795.00,9,LEASE-BH-F10-2025B
```

Both tenants at FLAT 10 in same year, but different leases (A vs B) track their individual arrears and payments.

**Format Components:**
- `LEASE` - Fixed prefix for all leases
- `BH` - Block code (BH = Boden House, KH = Knighton Hayes, etc.)
- `F1` - Unit identifier (F = Flat, PS = Parking Space, OF = Office)
- `2025` - Year lease started
- `B` - Optional suffix for multiple leases in same year (A, B, C, etc.)

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

Your CSV must have **either 11 columns** (without lease reference) or **12 columns** (with lease reference):

**Without Lease Reference (11 columns):**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source
```

**With Lease Reference (12 columns - RECOMMENDED):**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source,lease_reference
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
| `lease_reference` | ❌ Optional | Unique ID | Lease identifier (e.g., LEASE-FLAT1-2024) |

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
2025-05-31,-676.00,Owner Liability Accrual - May 2025,adjustment,owner_liability,FLAT 1 - 3 WEST GATE,,,,Net amount owed to landlord (not yet paid),OLD_ACCOUNT
```

**PLUS - Actual Payment Transactions (from bottom of spreadsheet):**
```csv
2025-05-27,-17111.56,Actual Owner Payment - 27 May 2025,payment,owner_payment,,Prestvale,,,Bank transfer to landlord - Payment 1,OLD_ACCOUNT
2025-05-28,-923.21,Actual Owner Payment - 28 May 2025,payment,owner_payment,,Prestvale,,,Bank transfer to landlord - Payment 2,OLD_ACCOUNT
```

**Note:** The empty property_reference (,,) is intentional - these payments cover the entire portfolio.

**Total for May 2025:** £18,034.77 actual payments reconcile against total accrued liabilities across all properties.

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

### 5️⃣ **Owner Liability (Net Due to Landlord)** ⚠️ ACCRUAL ONLY
**When to create:** If "Net Due to Prestvale" ≠ 0
**⚠️ IMPORTANT:** This is what you OWE, not what you PAID. See [Actual Payments](#actual-owner-payments) below.

| Field | Value | Source |
|-------|-------|--------|
| `transaction_date` | End of period | Use last day of month (e.g., 2025-05-31) |
| `amount` | **Negative** amount (outgoing liability) | "Net Due to Prestvale Propsk Old Account" or "Net Due to Prestvale Propsk PayProp Account" |
| `description` | "Owner Liability Accrual - {Month} {Year}" | Construct: e.g., "Owner Liability Accrual - May 2025" |
| `transaction_type` | `adjustment` | Use adjustment to track accrual |
| `category` | `owner_liability` | Track what's owed |
| `property_reference` | Property name | "Unit No." column |
| `notes` | "Net amount owed to landlord (not yet paid)" | Clarify this is accrual |
| `payment_source` | OLD_ACCOUNT or PAYPROP | Based on which account column has value |

### 6️⃣ **Actual Owner Payments** 💰 CASH TRANSACTION
**When to create:** For each payment in "Payments From Propsk Old Account" or "Payments From Propsk PayProp Account" section
**⚠️ IMPORTANT:** These are ACTUAL bank transfers made to the landlord.

**Location in spreadsheet:** Bottom section with payment dates and amounts

| Field | Value | Source |
|-------|-------|--------|
| `transaction_date` | Actual payment date | "Payment 1" date column (e.g., 27/05/2025 → 2025-05-27) |
| `amount` | **Negative** amount (outgoing) | "Payment 1" amount (e.g., -17111.56) |
| `description` | "Actual Owner Payment - {Date}" | Construct: e.g., "Actual Owner Payment - 27 May 2025" |
| `transaction_type` | `payment` | Actual cash payment |
| `category` | `owner_payment` | Cash disbursement |
| `property_reference` | **Leave EMPTY** | These are portfolio-level payments, not property-specific |
| `customer_reference` | Owner/Landlord name | Use landlord name (e.g., "Prestvale") |
| `notes` | "Bank transfer to landlord - reconciles against accrued liabilities" | Track reconciliation |
| `payment_source` | OLD_ACCOUNT or PAYPROP | Based on which section payment appears in |

**Example for May 2025 payments:**
```csv
2025-05-27,-17111.56,Actual Owner Payment - 27 May 2025,payment,owner_payment,,Prestvale,,,Bank transfer to landlord - Payment 1 reconciling May liabilities,OLD_ACCOUNT
2025-05-28,-923.21,Actual Owner Payment - 28 May 2025,payment,owner_payment,,Prestvale,,,Bank transfer to landlord - Payment 2 reconciling May liabilities,OLD_ACCOUNT
```

**Note:** Empty property_reference is CORRECT for these - they're portfolio-level payments covering multiple properties.

---

## 💡 Understanding Accrual vs. Cash Transactions

### The Two-Part System:

Your spreadsheet tracks BOTH:

1. **Per-Property Accruals** (What you owe per property)
   - FLAT 1: £676 owed
   - FLAT 2: £629 owed
   - FLAT 3: £629 owed
   - ... (all 30 flats)
   - **Total Accrued:** £19,236

2. **Actual Portfolio Payments** (What you actually paid)
   - Payment 1 on 27/05/2025: £17,111.56
   - Payment 2 on 28/05/2025: £923.21
   - **Total Paid:** £18,034.77

### Why the Difference?

The difference (£19,236 - £18,034.77 = **£1,201.23**) represents:
- Timing differences
- Held amounts
- Adjustments
- Working capital

### How to Import Both:

✅ **Create liability accruals** for each property (transaction_type = `adjustment`, category = `owner_liability`)
✅ **Create actual payments** from the payment section (transaction_type = `payment`, category = `owner_payment`)

The CRM will then show:
- **Liabilities owed** per property
- **Actual cash paid** to landlord
- **Outstanding balance** to reconcile

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

## 🚗 Handling Parking Spaces, Office, & Building Items

### Properties Already in Your Database:
```sql
✅ Parking Space 1 - 3 West Gate
✅ Parking Space 2 - 3 West Gate
... (up to Parking Space 10)
✅ Office - 3 West Gate
```

### Your Spreadsheet Rows (Currently Empty):
```
PARKING SPACE 1: No rent/data shown
BUILDING ADDITIONAL INCOME: No rent/data shown
OFFICE: No rent/data shown
```

### How to Handle:

#### **Parking Income:**
**If parking is included with a flat:**
- Include in the main flat's rent amount
- Add note: "Includes parking space 1 - £60"

**If parking is separate income:**
You already have properties! Use them:
```csv
2025-05-06,60.00,Parking income - May 2025,payment,parking,Parking Space 1 - 3 West Gate,Tenant Name,,,Monthly parking fee,OLD_ACCOUNT
```

#### **Office Income:**
If office generates income:
```csv
2025-05-06,250.00,Office rental - May 2025,payment,rent,Office - 3 West Gate,Business Tenant,,,Monthly office rent,OLD_ACCOUNT
```

#### **Building Additional Income:**
Examples: Laundry income, antenna rental, communal space fees

**Option 1 - Create a building property:**
```sql
INSERT INTO properties (property_name, property_type)
VALUES ('BODEN HOUSE - Building Common Income', 'Building');
```
Then:
```csv
2025-05-15,45.00,Laundry machine income,payment,building_income,BODEN HOUSE - Building Common Income,,,,Communal laundry,OLD_ACCOUNT
```

**Option 2 - Skip if no data:**
If these rows are always empty in your spreadsheet, **don't create transactions for them**.

#### **Building Expenses:**
Examples: Building insurance, communal repairs, fire safety equipment

**Create a building expense property:**
```sql
INSERT INTO properties (property_name, property_type)
VALUES ('BODEN HOUSE - Building Common Expenses', 'Building');
```
Then:
```csv
2025-05-20,-500.00,Building insurance premium,expense,insurance,BODEN HOUSE - Building Common Expenses,,,,Annual building insurance,OLD_ACCOUNT
2025-05-22,-150.00,Fire extinguisher service,maintenance,fire_safety,BODEN HOUSE - Building Common Expenses,,,,Annual fire safety check,OLD_ACCOUNT
```

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

### NEW: Two-Phase Import (Recommended)

#### Phase 1: Import Leases

1. **Prepare Lease CSV**
   - Create a spreadsheet with columns: property_reference, customer_reference, lease_start_date, lease_end_date, rent_amount, payment_day, lease_reference
   - Fill in lease details for each tenancy
   - Export to CSV

2. **Import Leases**
   - Navigate to: `/employee/lease/import`
   - Upload your lease CSV file
   - Review import results
   - Verify all leases imported successfully

#### Phase 2: Import Transactions

1. **Prepare Transaction CSV with Lease References**
   - Convert your spreadsheet using the formulas above
   - Add `lease_reference` column (column 12)
   - Match each transaction to its lease reference
   - Export to CSV format

2. **Upload to CRM**
   - Navigate to: `/employee/transaction/import`
   - Select your transaction CSV file
   - **Review Workflow**: If lease_reference is missing, you'll see suggested lease matches
   - Click "Import" to confirm

3. **Review Results**
   - Check import summary
   - Verify transaction counts
   - Verify lease linkages
   - Review any error messages

### Alternative: Single-Phase Import (Without Leases)

If you haven't created leases yet, you can still import transactions:

1. **Prepare CSV File (11 columns)**
   - Convert your spreadsheet using the formulas above
   - Omit the lease_reference column
   - Export to CSV format

2. **Upload to CRM**
   - Navigate to: `/employee/transaction/import`
   - Select your CSV file
   - Click "Import CSV File"

3. **Review Results**
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

---

## 📊 Processing Statement Generator Output (Advanced)

### Understanding the Statement Generator

Your statement generator spreadsheet uses **two sheets**:

1. **Sheet5 (Input Data)** - Master lease data with static information
2. **Statement Sheet (Calculated)** - Monthly statement with formulas that calculate rent due, prorations, fees, expenses, and net amounts

The statement sheet contains **Excel formulas** that reference Sheet5 and calculate everything dynamically based on the statement period (Date BOP = Beginning of Period, Date EOP = End of Period).

### Statement Structure

Each statement has:
- **Header**: CLIENT, Date BOP, Date EOP
- **Property Section**: PROPERTY: BODEN HOUSE
- **Lease Rows**: One row per lease with calculated values
- **Totals Row**: Aggregated totals
- **Payments Section**: Actual bank transfers made to landlord

### Key Statement Columns

| Column | Description | Example Value |
|--------|-------------|---------------|
| Unit No. | Property reference | FLAT 1 - 3 WEST GATE |
| Tenant | Customer name | MS O SMOLIARENKO & MR I HALII |
| Tenancy Start Date | Lease start | 2025-02-27 |
| Tenancy End Date | Lease end (empty if ongoing) | 2025-08-18 |
| Rent Due Date | Day of month | 27 |
| Rent Due Amount | Calculated rent (may be prorated) | 795.00 |
| Payment Source | 2=Old Account, 3=PayProp | 2 or 3 |
| Rent Received Date | Actual payment date | 04/08/2025 |
| Rent Received Amount | Actual amount paid | 795 |
| Management Fee (%) | Fee percentage | 15% |
| Management Fee Amount | Calculated fee (negative) | (119) |
| Expense 1-4 Source | Which account pays | 2 or 3 |
| Expense 1-4 Chargeable to [Account] | TRUE/FALSE per account | TRUE or FALSE |
| Expense 1-4 Label | Expense description | White Goods |
| Expense 1-4 Amount | Expense cost (negative) | 250 |
| Expense 1-4 Comment | Additional notes | John Oleyed fridge freezer |
| Net Due to Prestvale [Account] | Amount owed to landlord per account | 676 |
| Tenant Balance | Cumulative balance (arrears if negative) | (5) |

### Transaction Types from Statement Generator

#### 1️⃣ **Rent Due (Invoice)** - When Rent Due Amount > 0

The statement calculates prorated rent for mid-month moves:

**Formula Logic:**
```
IF tenancy ends before end of period:
    Prorate = (Days in tenancy / 30) * Monthly rent
ELSE:
    Full monthly rent
```

**CSV Mapping:**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source,lease_reference
{Start of period or rent due day},{Rent Due Amount},Rent Due - {Month} {Year},invoice,rent,{Unit No.},{Tenant},,,Rent due on {Rent Due Date},{Payment Source},{Lease Reference}
```

**Example - Full Month:**
```csv
2025-08-01,795.00,Rent Due - August 2025,invoice,rent,FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO & MR I HALII,,,Rent due on 27th,OLD_ACCOUNT,LEASE-BH-F1-2025
```

**Example - Prorated (Tenant Leaving):**
```csv
2025-08-01,438.00,Rent Due - August 2025 (Prorated),invoice,rent,FLAT 19 - 3 WEST GATE,MR J BANKS & MISS L MIDDLEMASS,,,Prorated rent - tenancy ends 31/07/2025,OLD_ACCOUNT,LEASE-BH-F19-2025
```

**Note:** Rent Due Amount of 0 means:
- Tenancy hasn't started yet (future tenant)
- Tenancy ended before this period
- Skip creating invoice transaction

#### 2️⃣ **Rent Received (Payment)** - When Rent Received Amount > 0

**CSV Mapping:**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source,lease_reference
{Rent Received Date},{Rent Received Amount},Rent Received - {Month} {Year},payment,rent,{Unit No.},{Tenant},,,Monthly rent payment,{Which account received},{Lease Reference}
```

**Determining Payment Account:**
Look at columns:
- "Amount Received Old Account" > 0 → OLD_ACCOUNT
- "Amount received in Payprop" > 0 → PAYPROP

**Example:**
```csv
2025-08-04,795.00,Rent Received - August 2025,payment,rent,FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO & MR I HALII,,,Monthly rent payment,OLD_ACCOUNT,LEASE-BH-F1-2025
```

**Special Case - Partial Payment:**
```csv
2025-08-12,584.00,Rent Received - August 2025 (Partial),payment,rent,FLAT 16 - 3 WEST GATE,MR R P BERESFORD & MS G L JUDGE,,,Partial payment - £8 outstanding,PAYPROP,LEASE-BH-F16-2025
```

**Special Case - Overpayment (Arrears Catch-up):**
```csv
2025-08-15,1400.00,Rent Received - August 2025 (Including Arrears),payment,rent,FLAT 7 - 3 WEST GATE,C TANG,,,Payment includes £700 arrears from previous months,PAYPROP,LEASE-BH-F7-2025
```

#### 3️⃣ **Management Fees** - When Management Fee Amount ≠ 0

**Split by Account:**
The statement calculates fees separately for each account:
- "Management Fee Propsk Old Account Amount"
- "Management Fee Propsk Payprop Account Amount"

**Create separate transactions for each account with fees:**

**CSV Mapping (Old Account):**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source,lease_reference
{Rent Received Date},{Management Fee Old Account Amount},Management Fee - {%},fee,commission,{Unit No.},,,{%} management fee on £{Rent Received Old Account},OLD_ACCOUNT,{Lease Reference}
```

**CSV Mapping (PayProp Account):**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source,lease_reference
{Rent Received Date},{Management Fee PayProp Amount},Management Fee - {%},fee,commission,{Unit No.},,,{%} management fee on £{Rent Received PayProp},PAYPROP,{Lease Reference}
```

**Example (15% fee on OLD_ACCOUNT rent):**
```csv
2025-08-04,-119.00,Management Fee - 15%,fee,commission,FLAT 1 - 3 WEST GATE,,,,15% management fee on £795,OLD_ACCOUNT,LEASE-BH-F1-2025
```

**Example (10% fee for different property):**
```csv
2025-08-20,-113.00,Management Fee - 10%,fee,commission,Apartment F - Knighton Hayes,,,,10% management fee on £1125,PAYPROP,LEASE-KH-F-2024
```

**Note:** Amount is already negative in statement. Don't make it more negative!

#### 4️⃣ **Expenses** (Up to 4 per Lease)

Each lease can have **4 separate expenses**, each with its own:
- Source (2=OLD_ACCOUNT, 3=PAYPROP)
- Chargeable Account (TRUE/FALSE for Old vs PayProp)
- Label
- Amount
- Comment

**CSV Mapping for Each Expense:**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source,lease_reference
{Rent Received Date or best estimate},{-Expense Amount},{Expense Label},expense,{category},{Unit No.},,,,{Expense Comment},{Expense Source},{Lease Reference}
```

**Determining Expense Category:**
| Expense Label Contains | transaction_type | category |
|------------------------|------------------|----------|
| "White Goods", "Fridge", "Freezer", "Appliance" | `expense` | `white_goods` |
| "Bed", "Mattress" | `expense` | `furnishing` |
| "End of Tenancy Clean", "Cleaning" | `maintenance` | `cleaning` |
| "Clearance" | `maintenance` | `clearance` |
| "Inventory" | `expense` | `inventory` |
| "Ceramic Hob", "Cooker" | `maintenance` | `appliances` |
| Anything else | `expense` | `general` |

**Example - Multiple Expenses on One Lease:**

Statement Row for FLAT 10:
```
Expense 1: White Goods, 250, John Oleyed fridge freezer
Expense 2: End of Tenancy Clean, 200, End of Tenancy Clean
```

Converts to:
```csv
2025-09-11,-250.00,White Goods,expense,white_goods,FLAT 10 - 3 WEST GATE,,,,John Oleyed fridge freezer,PAYPROP,LEASE-BH-F10-2025
2025-09-11,-200.00,End of Tenancy Clean,maintenance,cleaning,FLAT 10 - 3 WEST GATE,,,,End of Tenancy Clean,PAYPROP,LEASE-BH-F10-2025
```

**Example - Tenancy Ended, Only Expenses (No Rent):**

Statement Row for FLAT 7 (Tenant left, but expenses charged):
```
Rent Due Amount: 0.00
Expense 1: Ceramic Hob, 229.94
Expense 2: End of Tenancy Clean, 200
Expense 3: Inventory, 150
```

Converts to:
```csv
2025-08-01,-229.94,Ceramic Hob,maintenance,appliances,FLAT 7 - 3 WEST GATE,,,,Replacement ceramic hob,PAYPROP,LEASE-BH-F7-2025
2025-08-01,-200.00,End of Tenancy Clean,maintenance,cleaning,FLAT 7 - 3 WEST GATE,,,,End of tenancy cleaning,PAYPROP,LEASE-BH-F7-2025
2025-08-01,-150.00,Inventory,expense,inventory,FLAT 7 - 3 WEST GATE,,,,Inventory check,PAYPROP,LEASE-BH-F7-2025
```

**Note:** These expenses are charged to the TENANT (deducted from their deposit or owed), not the landlord.

#### 5️⃣ **Net Due to Landlord (Accrual)** - Split by Account

The statement calculates net amounts **separately for each account**:
- "Net Due to Prestvale Propsk Old Account"
- "Net Due to Prestvale Propsk PayProp Account"

This represents **what you OWE the landlord** (not what you've paid yet).

**Create separate accrual transactions for each account:**

**CSV Mapping (Old Account):**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source,lease_reference
{Last day of period},{-Net Due Old Account},Owner Liability Accrual - {Month} {Year},adjustment,owner_liability,{Unit No.},,,,Net amount owed to landlord - OLD ACCOUNT,OLD_ACCOUNT,{Lease Reference}
```

**CSV Mapping (PayProp Account):**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source,lease_reference
{Last day of period},{-Net Due PayProp},Owner Liability Accrual - {Month} {Year},adjustment,owner_liability,{Unit No.},,,,Net amount owed to landlord - PAYPROP,PAYPROP,{Lease Reference}
```

**Example (Positive Net - Owed to Landlord):**
```csv
2025-08-31,-676.00,Owner Liability Accrual - August 2025,adjustment,owner_liability,FLAT 1 - 3 WEST GATE,,,,Net amount owed to landlord - OLD ACCOUNT (£795 rent - £119 fee),OLD_ACCOUNT,LEASE-BH-F1-2025
```

**Example (Negative Net - Landlord Owes You):**

When expenses exceed rent collected:
```csv
2025-08-31,580.00,Owner Liability Adjustment - August 2025,adjustment,owner_liability,FLAT 7 - 3 WEST GATE,,,,Expenses exceeded rent - landlord owes balance,PAYPROP,LEASE-BH-F7-2025
```

**Note:**
- Positive net → Negative amount (outgoing to landlord)
- Negative net → Positive amount (landlord owes you)

#### 6️⃣ **Actual Portfolio Payments to Landlord** 💰

At the **bottom of the statement**, there's a "Payments" section:

```
Payments From Propsk Old Account
Payment 1           £0.00
Payment 2           0
Payment 3           0.00
Payment 4           0.00
Total Rent Paid to Prestvale From Propsk Old Account: £0.00

Payments From Propsk PayProp Account
Payment 1    22/08/2025    £16,666.26
Payment 2           £0.00
Payment 3           £0.00
Payment 4           0.00
Total Rent Paid to Prestvale From Propsk PayProp Account: £16,666.26
```

**These are ACTUAL bank transfers made to the landlord** (not per-property, but portfolio-wide).

**CSV Mapping:**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source,lease_reference
{Payment Date},{-Payment Amount},Actual Owner Payment - {Date},payment,owner_payment,,{Landlord Name},,,Bank transfer to landlord - Payment {number} reconciling {Month} liabilities,{OLD_ACCOUNT or PAYPROP},
```

**Example:**
```csv
2025-08-22,-16666.26,Actual Owner Payment - 22 August 2025,payment,owner_payment,,PRESTVALE PROPERTIES LIMITED,,,Bank transfer to landlord - Payment 1 reconciling August 2025 liabilities,PAYPROP,
```

**Key Points:**
- `property_reference` is **EMPTY** (portfolio-level payment)
- `customer_reference` is the **landlord name**
- `lease_reference` is **EMPTY** (not linked to specific lease)
- Amount is **negative** (outgoing payment)
- Create one transaction per payment line with a value > 0

### Reconciliation: Accruals vs. Actual Payments

The statement tracks:

1. **Per-Property Accruals** (what you theoretically owe):
   - FLAT 1: £676
   - FLAT 2: £629
   - ... (all properties)
   - **Total Accrued:** £15,583

2. **Actual Portfolio Payments** (what you actually paid):
   - Payment 1 on 22/08/2025: £16,666.26

**Difference:** £16,666.26 - £15,583 = **£1,083.26 overpayment**

This means:
- You paid more than this month's net
- Likely catching up on previous months
- Or paying in advance

The CRM will track:
- ✅ Individual property accruals
- ✅ Actual cash payments
- ✅ Outstanding balance per account

### Special Cases from Statement Generator

#### Case 1: Mid-Month Tenant Change

**Statement Shows:**
```
LEASE-BH-F10-2025    MR F PETER    2025-03-06    2025-09-23    6    735.00
LEASE-BH-F10-2025B   Anna Stoliarchuk    2025-09-03        735.00
```

**Processing:**
- First tenant: Prorated rent for Mar-Aug, then partial September
- Second tenant: Starts 09/03, gets partial September + full months going forward
- **Each gets their own lease reference** (suffix B for second tenant)

#### Case 2: No Rent, Only Expenses (Ended Tenancy)

**Statement Shows:**
```
LEASE-BH-F7-2025    C TANG    Rent: 0.00    Expenses: (580)    Net: (580)
```

**Processing:**
- ❌ Skip rent due/received transactions (amount = 0)
- ✅ Create expense transactions
- ✅ Create net liability (negative = landlord owes you)

#### Case 3: Vacant Unit (Future Tenant)

**Statement Shows:**
```
LEASE-BH-F10-2025B    Anna Stoliarchuk    2025-09-03        0.00
```

**Processing:**
- ❌ Skip entirely for this month (tenancy starts later)
- ✅ Import the lease itself (Phase 1)
- ✅ Will generate transactions starting September 2025

#### Case 4: Arrears Tracking

**Statement Shows:**
```
Tenant Balance: (1,125)    ← Negative means tenant owes this amount
```

**Processing:**
- This is **cumulative balance** from previous months
- Don't create a separate transaction for this
- The CRM will calculate balance automatically from transaction history
- Use this value to **validate** your import is correct

### Processing Workflow for Statement Generator

**Step 1: Export Lease Data (Phase 1)**

From **Sheet5** (the master data), export:
```csv
property_reference,customer_name,customer_email,lease_start_date,lease_end_date,rent_amount,payment_day,lease_reference
FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO & MR I HALII,,2025-02-27,,795.00,27,LEASE-BH-F1-2025
```

**Step 2: Calculate Statement Values**

The statement sheet formulas calculate:
- Prorated rent for mid-month moves
- Management fees split by account
- Net amounts split by account

**Step 3: Extract Calculated Values**

Copy the **calculated statement output** (not the formulas) to a new sheet.

**Step 4: Convert to Transaction Rows**

For each lease row, create:
1. Rent Due transaction (if Rent Due Amount > 0)
2. Rent Received transaction (if Rent Received Amount > 0)
3. Management Fee transaction(s) (split by account if needed)
4. Expense transactions (up to 4)
5. Net liability accrual transaction(s) (split by account)

**Step 5: Add Portfolio Payments**

From the Payments section at bottom:
- Create portfolio-level payment transactions
- Use empty property_reference
- Use landlord name as customer_reference

**Step 6: Validate Totals**

Check the TOTALS row matches your transaction sums:
- Total Rent Received = Sum of all rent payment transactions
- Total Management Fees = Sum of all fee transactions
- Total Expenses = Sum of all expense transactions
- Total Net = Sum of all accrual transactions

**Step 7: Import to CRM**

Navigate to `/employee/transaction/import` and upload your CSV.

---

**You now have everything you need to convert your Boden House spreadsheet into the CRM import format!** 🎯

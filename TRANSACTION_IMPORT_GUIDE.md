# Transaction Import Guide
**Complete guide to importing historical transaction data into the CRM**

---

## Quick Start

**New to importing?** Start here:

1. Download the [simple template](#download-template) (7 columns)
2. Fill in your transaction data
3. Upload via the [import page](/employee/transaction/import)
4. Review and approve

**Estimated time**: 10-15 minutes for first import

---

## Table of Contents

1. [CSV Structure](#csv-structure)
2. [Field Reference](#field-reference)
3. [Tiered Templates](#tiered-templates)
4. [Lease Linking](#lease-linking)
5. [PayProp Data Enrichment](#payprop-data-enrichment)
6. [Common Patterns](#common-patterns)
7. [Troubleshooting](#troubleshooting)

---

## CSV Structure

### Minimal CSV (Beginner)

**Only 2 fields required!** The system will infer the rest from context.

```csv
transaction_date,amount
2025-05-06,795.00
2025-05-15,740.00
2025-05-20,-150.00
```

**What the system auto-generates:**
- `description` - "Transaction on [date] for £[amount]"
- `transaction_type` - Inferred from amount (positive = payment, negative = expense)
- Links to properties/customers/leases - None (orphaned transaction)

**Good for**: Quick testing, bank statement imports where details will be added later

---

### Recommended CSV (Intermediate)

**7 fields for best results** - Good balance of simplicity and enrichment.

```csv
transaction_date,amount,description,category,property_reference,customer_reference,lease_reference
2025-05-06,795.00,Rent - May 2025,rent,FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO,LEASE-BH-F1-2025
2025-05-06,-119.25,Management fee,commission,FLAT 1 - 3 WEST GATE,,
2025-05-15,740.00,Rent - May 2025,rent,FLAT 2 - 3 WEST GATE,MR M K J AL BAYAT,LEASE-BH-F2-2025
```

**What you get:**
- ✅ Clear descriptions
- ✅ Categorized transactions
- ✅ Linked to properties
- ✅ Linked to customers
- ✅ **Linked to specific leases** (most important!)

**Good for**: Most import scenarios, ongoing monthly updates

---

### Full CSV (Advanced)

**All available fields** - Maximum enrichment and PayProp integration.

```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference,incoming_transaction_amount,payment_method,notes,payprop_property_id,payprop_tenant_id
2025-01-22,1125.00,"Rent - January 2025",payment,rent,"Apartment F - Knighton Hayes",Riaz,"LEASE-APT-F-2025",1125.00,"Bank Transfer","Rent due on 22nd",PPR_12345,PPT_67890
2025-01-25,-956.25,"Payment to Owner",payment,owner_payment,,"John Smith",,,"Bank Transfer","Monthly payment",,
2025-01-20,-150.00,"Plumbing Repair",expense,maintenance,"Apartment F - Knighton Hayes","ABC Plumbing","LEASE-APT-F-2025",,,,,
```

**Advanced features:**
- ✅ `incoming_transaction_amount` - Triggers automatic commission/owner split calculation
- ✅ `payprop_property_id`, `payprop_tenant_id` - Full PayProp enrichment
- ✅ `payment_method`, `notes` - Additional tracking

**Good for**: Historical PayProp data imports, complete financial records migration

---

## Field Reference

### Required Fields

| Field | Format | Example | Notes |
|-------|--------|---------|-------|
| `transaction_date` | YYYY-MM-DD | 2025-05-06 | Date of transaction |
| `amount` | Decimal | 795.00 or -150.00 | Positive = income, Negative = expense |

**That's it!** Only 2 fields are absolutely required.

---

### Recommended Fields (Highly Important)

| Field | Example | Why It Matters |
|-------|---------|----------------|
| `lease_reference` | LEASE-BH-F1-2025 | **Most Important!** Links transaction to specific lease period. Enables tenant statements, arrears tracking, lease-level reporting. |
| `category` | rent, maintenance, commission | Helps with auto-matching and reporting. Required for intelligent lease matching to work. |
| `property_reference` | FLAT 1 - 3 WEST GATE | Links to property. Fuzzy matched by name/address. |
| `customer_reference` | MS O SMOLIARENKO or email@example.com | Links to tenant/customer. Matched by name or email (email more accurate). |

---

### Optional Fields

| Field | Example | Purpose |
|-------|---------|---------|
| `description` | Rent - May 2025 | Transaction description (auto-generated if blank) |
| `transaction_type` | payment, expense, fee | Type of transaction (inferred if blank) |
| `subcategory` | rent_arrears | Additional categorization |
| `bank_reference` | TXN123456 | Bank transaction reference |
| `payment_method` | Bank Transfer, Cash, Cheque | How payment was made |
| `notes` | Additional details | Free-form notes |

---

### Advanced Fields

#### Auto-Split Fields

| Field | Example | Purpose |
|-------|---------|---------|
| `incoming_transaction_amount` | 795.00 | For rent payments: triggers automatic creation of management fee and owner allocation transactions. Calculates commission based on property commission rate. |
| `beneficiary_type` | beneficiary, beneficiary_payment, contractor | Controls balance tracking system. `beneficiary` increases owner balance, `beneficiary_payment` decreases it. |

**Example**: Importing rent payment with auto-split
```csv
transaction_date,amount,category,property_reference,customer_reference,lease_reference,incoming_transaction_amount
2025-05-06,795.00,rent,FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO,LEASE-BH-F1-2025,795.00
```

**Result**: System creates 3 transactions automatically:
1. Rent payment: £795.00 (income)
2. Management fee: -£119.25 (15% commission)
3. Owner allocation: -£675.75 (net to owner)

---

#### PayProp Enrichment Fields

| Field | Example | Purpose |
|-------|---------|---------|
| `payprop_property_id` | PPR_12345 | Links to PayProp property record. Enables cross-referencing with PayProp API imports. |
| `payprop_tenant_id` | PPT_67890 | Links to PayProp tenant record. Improves lease auto-matching. |
| `payprop_beneficiary_id` | PPB_54321 | Links to PayProp beneficiary (owner) record. |
| `payprop_transaction_id` | PPTX_ABC123 | Links to PayProp transaction. Prevents duplicate imports. |

**When to use**: Importing historical PayProp data from CSV exports.

**Example**:
```csv
transaction_date,amount,description,property_reference,payprop_property_id,payprop_tenant_id,payprop_transaction_id
2025-05-06,795.00,Rent Payment,FLAT 1 - 3 WEST GATE,PPR_12345,PPT_67890,PPTX_ABC123
```

---

## Tiered Templates

### How to Download

Visit [/employee/transaction/import](/employee/transaction/import) and click:
- **"Download Simple Template"** - 7-column recommended template
- **"View Example"** - See full example with all fields

### Which Template Should I Use?

```
┌─────────────────────────────────────────────────────────────────┐
│ Decision Tree: Which Template?                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│ Just testing the system?                                        │
│   → Use MINIMAL (2 columns)                                     │
│                                                                  │
│ Importing monthly rent/payments?                                │
│   → Use RECOMMENDED (7 columns)                                 │
│                                                                  │
│ Importing historical PayProp data?                              │
│   → Use FULL (13+ columns with PayProp IDs)                     │
│                                                                  │
│ Want automatic commission splits?                               │
│   → Use FULL (include incoming_transaction_amount)              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Lease Linking

### Why Link to Leases?

**Without lease linking**:
- ❌ Can't generate tenant statements for specific lease periods
- ❌ Can't track arrears per lease
- ❌ Can't calculate "rent paid vs rent owed" for tenancy
- ❌ Mid-year tenant changes cause confusion

**With lease linking**:
- ✅ Automatic lease period detection
- ✅ Accurate arrears tracking
- ✅ Historical lease analysis
- ✅ Clean tenant financial statements

---

### Method 1: Explicit Lease Reference (Recommended)

**Best for**: Reliable, guaranteed linking

**Format**: `LEASE-{CODE}-{UNIT}-{YEAR}[SUFFIX]`

**Examples**:
- `LEASE-BH-F1-2025` - Boden House, Flat 1, started 2025
- `LEASE-KH-F-2024` - Knighton Hayes, Apartment F, started 2024
- `LEASE-BH-F10-2025B` - Boden House, Flat 10, second tenant in 2025 (suffix B)

**CSV Example**:
```csv
transaction_date,amount,description,category,property_reference,customer_reference,lease_reference
2025-05-06,795.00,Rent - May 2025,rent,FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO,LEASE-BH-F1-2025
```

**Success Rate**: ~100% (if lease reference exists in system)

---

### Method 2: Intelligent Auto-Matching (Fallback)

**How it works**: If you don't provide `lease_reference`, the system automatically tries to match rent transactions.

**Matching Logic**:
1. Category must be "rent"
2. Property must be provided and match
3. Transaction date used to find active lease
4. Customer PayProp ID used if available (improves accuracy)

**CSV Example** (no lease_reference):
```csv
transaction_date,amount,description,category,property_reference,customer_reference
2025-05-06,795.00,Rent - May 2025,rent,FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO
```

**System tries to match**:
- Find property "FLAT 1 - 3 WEST GATE"
- Find customer "MS O SMOLIARENKO"
- Find active lease at that property on 2025-05-06
- Link if match found

**Success Rate**: ~60-80% (depends on data quality and customer PayProp IDs)

**Tip**: If customer has `payprop_entity_id` set, matching is much more accurate!

---

### Troubleshooting Lease Matching

**Problem**: "Transaction imported but not linked to lease"

**Possible Causes**:
1. **Lease doesn't exist** - Import leases first via `/employee/lease/import`
2. **Wrong category** - Must be "rent" for auto-matching to work
3. **Property name mismatch** - Use exact property name from system
4. **Customer name mismatch** - Use email for better matching
5. **Date outside lease period** - Check lease start/end dates

**Solution**:
```csv
# ❌ Won't auto-match (missing category)
transaction_date,amount,property_reference,customer_reference
2025-05-06,795.00,FLAT 1,MS O SMOLIARENKO

# ✅ Will auto-match (has category="rent")
transaction_date,amount,category,property_reference,customer_reference
2025-05-06,795.00,rent,FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO

# ✅✅ Guaranteed match (explicit lease_reference)
transaction_date,amount,category,property_reference,customer_reference,lease_reference
2025-05-06,795.00,rent,FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO,LEASE-BH-F1-2025
```

---

## PayProp Data Enrichment

### Scenario: Importing Historical PayProp CSV Export

**What you have** (PayProp export):
```csv
Date,Amount,Description,Property_Payprop_ID,Tenant_Payprop_ID,Transaction_ID
06/05/2025,795.00,Rent Payment,PPR_12345,PPT_67890,PPTX_ABC123
```

**What you need** (CRM import format):
```csv
transaction_date,amount,description,property_reference,customer_reference,payprop_property_id,payprop_tenant_id,payprop_transaction_id
2025-05-06,795.00,Rent Payment,FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO,PPR_12345,PPT_67890,PPTX_ABC123
```

**Key transformations**:
1. Date format: DD/MM/YYYY → YYYY-MM-DD
2. Add property_reference (manual lookup: PPR_12345 → "FLAT 1 - 3 WEST GATE")
3. Add customer_reference (manual lookup: PPT_67890 → "MS O SMOLIARENKO")
4. Keep PayProp IDs for enrichment

---

### Benefits of PayProp ID Enrichment

| Without PayProp IDs | With PayProp IDs |
|---------------------|------------------|
| Property matched by name (fuzzy, error-prone) | Property matched by PayProp ID (exact) |
| Customer matched by name (fuzzy, error-prone) | Customer matched by PayProp ID (exact) |
| Can't link to PayProp API imports | Can cross-reference with PayProp API data |
| Can't deduplicate against PayProp | Can detect duplicate imports |
| Manual linking burden | Automatic intelligent matching |

---

### Full PayProp Import Example

```csv
transaction_date,amount,description,category,property_reference,customer_reference,lease_reference,payprop_property_id,payprop_tenant_id,payprop_transaction_id
2025-05-06,795.00,Rent Payment,rent,FLAT 1 - 3 WEST GATE,osana.smoliarenko@email.com,LEASE-BH-F1-2025,PPR_12345,PPT_67890,PPTX_ABC123
2025-05-15,740.00,Rent Payment,rent,FLAT 2 - 3 WEST GATE,muhsin@email.com,LEASE-BH-F2-2025,PPR_12346,PPT_67891,PPTX_ABC124
```

**Result**: Fully enriched historical data matching quality of PayProp API imports!

---

## Common Patterns

### Pattern 1: Monthly Rent Payments

```csv
transaction_date,amount,description,category,property_reference,customer_reference,lease_reference
2025-05-06,795.00,Rent - May 2025,rent,FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO,LEASE-BH-F1-2025
2025-05-15,740.00,Rent - May 2025,rent,FLAT 2 - 3 WEST GATE,MR M K J AL BAYAT,LEASE-BH-F2-2025
2025-05-22,685.00,Rent - May 2025,rent,FLAT 3 - 3 WEST GATE,ANNA STOLIARCHUK,LEASE-BH-F3-2025
```

---

### Pattern 2: Rent Payment with Auto-Split

```csv
transaction_date,amount,description,category,property_reference,customer_reference,lease_reference,incoming_transaction_amount
2025-05-06,795.00,Rent - May 2025,rent,FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO,LEASE-BH-F1-2025,795.00
```

**System automatically creates**:
- Rent payment: £795.00
- Management fee: -£119.25 (15%)
- Owner allocation: -£675.75

---

### Pattern 3: Maintenance/Expense

```csv
transaction_date,amount,description,category,property_reference,customer_reference,lease_reference
2025-05-10,-150.00,Plumbing repair,maintenance,FLAT 1 - 3 WEST GATE,ABC Plumbing Ltd,LEASE-BH-F1-2025
2025-05-15,-75.00,Cleaning service,maintenance,FLAT 2 - 3 WEST GATE,CleanCo,LEASE-BH-F2-2025
```

---

### Pattern 4: Owner Payments

```csv
transaction_date,amount,description,category,customer_reference
2025-05-27,-675.75,Monthly payment to owner,owner_payment,JOHN SMITH
2025-05-27,-629.00,Monthly payment to owner,owner_payment,JANE DOE
```

**Note**: Owner payments typically don't link to specific properties or leases.

---

### Pattern 5: Commission Fees

```csv
transaction_date,amount,description,category,property_reference
2025-05-06,-119.25,Management fee - 15%,commission,FLAT 1 - 3 WEST GATE
2025-05-15,-111.00,Management fee - 15%,commission,FLAT 2 - 3 WEST GATE
```

---

## Troubleshooting

### Issue: "CSV file is invalid"

**Causes**:
- Missing required columns (transaction_date, amount)
- Invalid date format (must be YYYY-MM-DD)
- Invalid amount format (use decimal, not currency symbols)

**Fix**:
```csv
# ❌ Wrong
Date,Amount
06/05/2025,£795.00

# ✅ Correct
transaction_date,amount
2025-05-06,795.00
```

---

### Issue: "Property not found"

**Causes**:
- Property name doesn't match system records
- Typo in property name
- Property not yet imported

**Fix**:
1. Check exact property name in Properties list
2. Use copy/paste to avoid typos
3. Import properties first via `/employee/property/import`

**Example**:
```csv
# ❌ Won't match
property_reference
Flat 1

# ✅ Will match (exact name)
property_reference
FLAT 1 - 3 WEST GATE
```

---

### Issue: "Customer not found"

**Causes**:
- Customer name doesn't match
- Email doesn't match
- Customer not yet imported

**Fix**: Use email for more accurate matching
```csv
# ⚠️ Might not match (name fuzzy matching)
customer_reference
O SMOLIARENKO

# ✅ Better (exact email match)
customer_reference
osana.smoliarenko@email.com
```

---

### Issue: "Transaction imported but not linked to lease"

**See**: [Troubleshooting Lease Matching](#troubleshooting-lease-matching)

---

### Issue: "Duplicate transactions detected"

**Causes**:
- Importing same data twice
- No deduplication ID provided

**Fix**: Include `payprop_transaction_id` for deduplication
```csv
transaction_date,amount,payprop_transaction_id
2025-05-06,795.00,PPTX_ABC123
```

System will skip if `PPTX_ABC123` already exists.

---

## Best Practices

### ✅ Do This

1. **Always include lease_reference** for rent payments
2. **Use email addresses** for customer matching (more accurate than names)
3. **Download template first** instead of creating from scratch
4. **Import leases before transactions** (required for linking)
5. **Test with 2-3 rows first** before bulk import
6. **Use consistent date format** (YYYY-MM-DD)
7. **Include PayProp IDs** when importing historical PayProp data

### ❌ Don't Do This

1. ❌ Don't use currency symbols (£, $) in amounts
2. ❌ Don't use DD/MM/YYYY date format (use YYYY-MM-DD)
3. ❌ Don't import rent payments without lease_reference
4. ❌ Don't mix different property naming conventions
5. ❌ Don't skip the template download (too easy to make mistakes)

---

## Advanced Topics

### Commission Calculation

When you include `incoming_transaction_amount`, the system:

1. Reads property commission rate (default 15%)
2. Calculates: `commission = incoming_amount × (rate ÷ 100)`
3. Calculates: `net_to_owner = incoming_amount - commission`
4. Creates 3 transactions:
   - Original rent payment (+)
   - Management fee (-)
   - Owner allocation (-)

**Example**:
- Incoming: £795.00
- Commission rate: 15%
- Commission: £119.25
- Net to owner: £675.75

---

### Balance Tracking with Beneficiary Types

Use `beneficiary_type` for owner balance tracking:

| Type | Effect | Example |
|------|--------|---------|
| `beneficiary` | Increases owner balance | Owner allocation from rent |
| `beneficiary_payment` | Decreases owner balance | Payment to owner |
| `contractor` | No effect on owner balance | Payment to maintenance contractor |

---

### Multiple Tenants, Same Property

Use letter suffixes in lease references:

```csv
transaction_date,amount,property_reference,customer_reference,lease_reference
2025-03-06,735.00,FLAT 10 - 3 WEST GATE,MR F PETER,LEASE-BH-F10-2025
2025-09-03,795.00,FLAT 10 - 3 WEST GATE,ANNA STOLIARCHUK,LEASE-BH-F10-2025B
```

Both tenants at FLAT 10, but different leases (A vs B) for different periods.

---

## Getting Help

### Import Page Features

- **Download Simple Template** - Get started quickly
- **View Example** - See full field reference
- **Validation Preview** - Check your data before importing
- **Review Queue** - Verify matches and approve before final import

### Support Resources

- Import documentation: This guide
- Lease import guide: `BODEN_HOUSE_TO_CSV_CONVERSION_GUIDE.md`
- Data audit report: `PAYPROP_DATA_AUDIT_REPORT.md`
- Gap analysis: `IMPORT_UI_DOCUMENTATION_GAP_ANALYSIS.md`

### Common Questions

**Q: Can I import without lease references?**
A: Yes, but automatic matching only works ~60-80% of the time. Manual `lease_reference` is recommended.

**Q: Do I need to import leases first?**
A: Yes, if you want transactions to link to leases. Import via `/employee/lease/import`.

**Q: Can I import PayProp data?**
A: Yes! Use the full template with PayProp ID fields for best results.

**Q: What if I make a mistake?**
A: Imports are batched and can be reviewed before approval. You can also delete batches after import.

**Q: How do I create lease references?**
A: Use format `LEASE-{CODE}-{UNIT}-{YEAR}`. Example: `LEASE-BH-F1-2025`

---

## Quick Reference Card

```
┌──────────────────────────────────────────────────────────────┐
│ TRANSACTION IMPORT QUICK REFERENCE                           │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│ Required Fields (2):                                         │
│   • transaction_date (YYYY-MM-DD)                            │
│   • amount (decimal, negative for expenses)                  │
│                                                               │
│ Highly Recommended (4):                                      │
│   • lease_reference (e.g., LEASE-BH-F1-2025)                 │
│   • category (rent, maintenance, commission)                 │
│   • property_reference (property name)                       │
│   • customer_reference (name or email)                       │
│                                                               │
│ PayProp Enrichment (4):                                      │
│   • payprop_property_id (e.g., PPR_12345)                    │
│   • payprop_tenant_id (e.g., PPT_67890)                      │
│   • payprop_beneficiary_id                                   │
│   • payprop_transaction_id (for deduplication)               │
│                                                               │
│ Auto-Split Fields:                                           │
│   • incoming_transaction_amount (triggers commission calc)   │
│   • beneficiary_type (balance tracking)                      │
│                                                               │
│ Templates:                                                   │
│   • Minimal: 2 columns (testing)                             │
│   • Recommended: 7 columns (most users)                      │
│   • Full: 13+ columns (PayProp imports, advanced)            │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

---

**Document Version**: 1.0
**Last Updated**: October 27, 2025
**Related Guides**:
- Lease Import: `BODEN_HOUSE_TO_CSV_CONVERSION_GUIDE.md`
- Data Audit: `PAYPROP_DATA_AUDIT_REPORT.md`
- Gap Analysis: `IMPORT_UI_DOCUMENTATION_GAP_ANALYSIS.md`

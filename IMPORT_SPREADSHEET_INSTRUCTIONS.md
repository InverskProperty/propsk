# 📊 **CSV Import Spreadsheet Format - ENHANCED GUIDE**

## 🎯 **Quick Summary**

Your CSV import system supports **dual-source reconciliation** with Old Account + PayProp integration, **parking space income**, **maintenance transactions**, and **period-based source filtering**.

## 📋 **ENHANCED CSV COLUMNS (11 columns total)**

Your CSV **must have exactly 11 columns** in this order:

```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source
```

### **Column Details:**

| Column | Required | Format | Examples |
|--------|----------|--------|----------|
| `transaction_date` | ✅ Yes | `YYYY-MM-DD` | `2025-01-15`, `2024-12-21` |
| `amount` | ✅ Yes | Decimal (no £ symbol) | `795.00`, `-119.25`, `1200.50` |
| `description` | ✅ Yes | Text description | `Monthly rent - Flat 8`, `Parking space income` |
| `transaction_type` | ✅ Yes | See [Transaction Types](#transaction-types) | `payment`, `maintenance`, `expense` |
| `category` | ❌ Optional | Text category | `rent`, `parking`, `maintenance`, `commission` |
| `property_reference` | ✅ Yes | Property identifier | `FLAT 8 - 3 WEST GATE`, `PARKING_SPACE_1` |
| `customer_reference` | ❌ Optional | Customer/tenant name | `Mr Shermal Wijesinghage` |
| `bank_reference` | ❌ Optional | Bank transaction ref | `FPS-12345`, `DD-67890` |
| `payment_method` | ❌ Optional | Payment method | `Old Account`, `PayProp`, `Bank Transfer` |
| `notes` | ❌ Optional | Additional notes | `Emergency repair`, `Monthly payment` |
| `payment_source` | ✅ **NEW** | Source system | `OLD_ACCOUNT`, `PAYPROP`, `BOTH` |

## 🔄 **Transaction Types** {#transaction-types}

### **✅ Valid Transaction Types**

Your system now supports these transaction types:

| Type | Use For | Example |
|------|---------|---------|
| `payment` | Rent, parking, owner payments, general payments | Monthly rent + parking received |
| `invoice` | Bills, invoices sent | Rent invoice generated |
| `expense` | General business expenses | Office supplies |
| `maintenance` | Property maintenance, repairs | Plumbing, electrical work |
| `deposit` | Security deposits, initial payments | Tenant deposit |
| `withdrawal` | Money withdrawn from accounts | Cash withdrawal |
| `transfer` | Money transfers between accounts | Account transfers |
| `fee` | Management fees, commission | 15% management fee |
| `refund` | Money refunded to customers | Deposit refund |
| `adjustment` | Account corrections | Error corrections |

## 🏗️ **Payment Source Handling**

### **✅ Payment Source Types**

| Source | When to Use | Description |
|--------|-------------|-------------|
| `OLD_ACCOUNT` | Pre-PayProp or ongoing old bank | Payments via your traditional bank account |
| `PAYPROP` | PayProp-collected payments | Payments processed through PayProp system |
| `BOTH` | Duplicate or split payments | When same payment appears in both systems |

### **🚗 Parking Space Integration**

**Parking spaces are now treated as separate properties:**
- **Create parking properties**: `PARKING SPACE 1 - 3 WEST GATE`, `PARKING SPACE 2 - 3 WEST GATE`
- **Separate transactions**: Each parking space has its own property_reference
- **Property Type**: Use "Parking Space" when creating parking properties in the system

**Example:**
```csv
2025-07-04,675.00,Monthly rent,payment,rent,FLAT 8 - 3 WEST GATE,Mr Shermal Wijesinghage,,PayProp,,PAYPROP
2025-07-04,60.00,Monthly parking income,payment,parking,PARKING SPACE 1 - 3 WEST GATE,Mr Shermal Wijesinghage,,PayProp,,PAYPROP
```

### **🧠 Smart Type Mapping**

The system automatically maps these variations:

| Your CSV Value | Maps To | Why |
|----------------|---------|-----|
| `maintenance` | `maintenance` | Direct match |
| `payment_to_contractor` | `maintenance` | Contractor payments |
| `contractor_payment` | `maintenance` | Contractor payments |
| `service_payment` | `maintenance` | Service payments |
| `rent` | `payment` | Rent payments |
| `rental_payment` | `payment` | Rental payments |
| `parking` | `payment` | Parking income |
| `management_fee` | `fee` | Management fees |
| `commission` | `fee` | Commission fees |
| `owner_payment` | `payment` | Owner payments |
| `payment_to_beneficiary` | `payment` | Beneficiary payments |

## 📁 **Sample CSV Files**

### **Example 1: Dual-Source Rent + Parking**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source
2025-07-04,675.00,Monthly rent - Flat 8,payment,rent,FLAT 8 - 3 WEST GATE,Mr Shermal Wijesinghage,PP-001,PayProp,Monthly payment,PAYPROP
2025-07-04,60.00,Parking space income,payment,parking,PARKING SPACE 1 - 3 WEST GATE,Mr Shermal Wijesinghage,PP-001,PayProp,Parking payment,PAYPROP
2025-07-04,-110.25,Management fee - Flat 8,fee,commission,FLAT 8 - 3 WEST GATE,Mr Shermal Wijesinghage,,,15% management fee,PAYPROP
2025-07-15,750.00,Monthly rent - Flat 1,payment,rent,FLAT 1 - 3 WEST GATE,John Smith,OLD-001,Old Account,Old bank payment,OLD_ACCOUNT
```

### **Example 2: Maintenance and Mixed Sources**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source
2025-01-20,-150.00,Boiler service,maintenance,heating,FLAT 2 - 3 WEST GATE,,SVC-001,Bank Transfer,Annual service,OLD_ACCOUNT
2025-01-21,-75.00,Electrical safety check,maintenance,electrical,FLAT 2 - 3 WEST GATE,,ELEC-002,PayProp,PAT testing,PAYPROP
2025-01-22,795.00,Monthly rent received,payment,rent,FLAT 2 - 3 WEST GATE,Jane Doe,OLD-002,Old Account,Historical payment,OLD_ACCOUNT
```

### **Example 3: Credit/Debit Note Scenario**
```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source
2025-06-15,795.00,Rent paid in old account,payment,rent,FLAT 5 - 3 WEST GATE,Mike Jones,OLD-003,Old Account,Pre-PayProp payment,OLD_ACCOUNT
2025-06-15,-795.00,Credit note - rent paid old account,adjustment,credit_note,FLAT 5 - 3 WEST GATE,Mike Jones,CN-001,PayProp,Balancing PayProp,PAYPROP
```

## 🚀 **Import Process**

### **Step 1: Prepare Your CSV**
1. **Open your Excel/spreadsheet**
2. **Ensure exactly 11 columns** in the correct order
3. **Add payment_source column**
4. **Create separate properties** for parking spaces in your system
5. **Save as CSV** (UTF-8 recommended)
6. **Double-check date format**: YYYY-MM-DD

### **Step 2: Period-Based Source Strategy**
Choose your approach based on time period:

**For Historical Periods (Pre-PayProp):**
- Use `OLD_ACCOUNT` for all transactions
- Focus on cash flow accuracy
- Don't worry about PayProp invoice matching

**For Transition Periods (PayProp setup):**
- Use `BOTH` for duplicate transactions
- Use `PAYPROP` for credit/debit notes
- Use `OLD_ACCOUNT` for genuine old bank payments

**For Current Periods (Live PayProp):**
- Minimize imports (PayProp handles most data)
- Use for corrections or additional expenses only

### **Step 3: Upload to System**
1. **Log in** with your Google account (sajidkazmi@propsk.com)
2. **Navigate to**:
   - For PayProp imports: `/admin/payprop-import/upload-csv`
   - For general imports: `/employee/transaction/import/csv`
3. **Select your CSV file**
4. **Click "Import CSV File"**

### **Step 4: Review Results**
- ✅ **Success**: "Import successful! X transactions imported"
- ⚠️ **Warnings**: Some transactions imported with issues
- ❌ **Errors**: Review error messages and fix CSV

## 🔧 **Common Issues & Solutions**

### **Issue 1: "Insufficient fields in CSV line. Expected 11, got 10"**
**Solution**: Your CSV has too few columns. Add the new column:
```csv
# OLD FORMAT (10 columns):
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes

# NEW FORMAT (11 columns):
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes,payment_source
```

### **Issue 2: "Invalid payment source"**
**Solution**: Use valid payment source values:
```csv
# VALID:
OLD_ACCOUNT, PAYPROP, BOTH

# INVALID:
old_account, payrop, dual
```

### **Issue 3: Parking Space Handling**
**Solution**: For properties with parking:
- **Create separate properties** in your system for parking spaces
- **Use separate property references** for each parking space
- **Property Type**: Set to "Parking Space" when creating parking properties
```csv
# Example:
2025-07-04,675.00,Monthly rent,payment,rent,FLAT 8 - 3 WEST GATE,Tenant Name,,,,,PAYPROP
2025-07-04,60.00,Parking income,payment,parking,PARKING SPACE 1 - 3 WEST GATE,Tenant Name,,,,,PAYPROP
```

### **Issue 4: "Invalid date format"**
**Solution**: Use YYYY-MM-DD format:
```csv
# WRONG:
15/01/2025, 01-15-2025, Jan 15 2025

# CORRECT:
2025-01-15
```

### **Issue 5: "CSV file is empty"**
**Solution**: Ensure your CSV has:
1. Header row with column names
2. At least one data row
3. Proper CSV encoding (UTF-8)

## 📊 **Data Validation Rules**

### **Amounts**
- ✅ Positive for income: `795.00`
- ✅ Negative for expenses: `-119.25`
- ✅ No currency symbols: `1000.50` (not `£1000.50`)
- ✅ Decimal format: `100.25` (not `100,25`)

### **Dates**
- ✅ ISO format: `2025-01-15`
- ✅ Valid dates only
- ❌ Future dates beyond reasonable limit
- ❌ Dates before 1900

### **Text Fields**
- ✅ Description: Max 1000 characters
- ✅ Notes: No limit
- ✅ Property reference: Should match existing properties
- ✅ Customer reference: Should match existing customers

## 🎯 **Best Practices**

### **1. Property References**
Match exactly with your system:
```csv
# Use exact property names from your system:
FLAT 1 - 3 WEST GATE
APARTMENT F - KNIGHTON HAYES
PARKING SPACE 1
```

### **2. Transaction Descriptions**
Be descriptive and consistent:
```csv
# Good descriptions:
Monthly rent - January 2025
Plumbing repair - kitchen tap leak
Management fee - 15% commission
Owner payment - net rental income

# Poor descriptions:
Payment
Maintenance
Fee
```

### **3. Categories**
Use consistent categorization:
```csv
# Recommended categories:
rent, commission, maintenance, heating, electrical, plumbing,
fire_safety, white_goods, clearance, furnishing
```

## 🎯 **Period Filtering Strategy**

### **Avoiding Duplicate Data**
**Problem**: PayProp credit/debit notes can create confusing duplicates
**Solution**: **Filter by period and payment source**

### **Recommended Approach by Period:**

#### **December 2024 - May 2025 (Historical)**
- ✅ **Include**: `OLD_ACCOUNT` transactions only
- ❌ **Exclude**: All `PAYPROP` data for this period
- 🎯 **Focus**: Cash flow accuracy from your spreadsheets

#### **June 2025 - Current (Transition/Live)**
- ⚠️ **Careful**: Mix of both sources
- ✅ **Include**: `OLD_ACCOUNT` for genuine old bank payments
- ✅ **Include**: `PAYPROP` only for corrections or missed items
- ❌ **Exclude**: Credit notes marked as "rent paid in old account"

## 🚨 **IMPORTANT NOTES**

### **✅ What's Enhanced**
- ✅ **Dual-source reconciliation** with payment source tracking
- ✅ **Parking space integration** as separate properties
- ✅ **Period-based filtering** to avoid PayProp duplicates
- ✅ **Maintenance transactions** with smart type mapping
- ✅ **New property types**: Parking Space, Office, Storage
- ✅ **CSRF token** issues resolved

### **🔄 Import Endpoints**
- **PayProp Admin**: `/admin/payprop-import/upload-csv` (MANAGER role)
- **Employee Import**: `/employee/transaction/import/csv` (EMPLOYEE role)

### **📈 Enhanced Transaction Mapping**
- **Rent + Parking**: Parking spaces as separate properties
- **Payment Sources**: OLD_ACCOUNT, PAYPROP, BOTH tracking
- **Maintenance**: Full categorization support
- **Property Types**: Parking Space, Office, Storage added
- **Block Expenses**: Ready for shared cost allocation

## ⚡ **Implementation Status**

### **✅ Ready to Use:**
- Enhanced CSV format (11 columns)
- Payment source tracking
- Parking spaces as separate properties
- New property types: Parking Space, Office, Storage
- Period-based strategy

### **🔄 Coming Soon:**
- Enhanced import validation for new columns
- Duplicate detection across payment sources
- Block expense allocation features

## 📞 **Support**

If you encounter issues:

1. **Check the error message** - most are self-explanatory
2. **Verify CSV format** - exactly 12 columns now
3. **Check payment source values** - use OLD_ACCOUNT, PAYPROP, BOTH
4. **Review date format** - must be YYYY-MM-DD
5. **Try with a small test file** first

Your import system now supports **sophisticated dual-source reconciliation** with parking integration! 🚀
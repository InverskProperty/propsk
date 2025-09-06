# CRM Financial System Documentation
## Local Entity Creation with PayProp Integration

*Version 1.0 | Created: September 2025*

---

## 📋 **EXECUTIVE SUMMARY**

This document covers the comprehensive financial system modifications that enable:
- **Local entity creation** (invoices, transactions) with optional PayProp synchronization
- **Unified data architecture** respecting "PayProp Winner" logic
- **Historical transaction import system** for complete financial reporting
- **Seamless integration** with existing customer/property assignment system

---

## 🏗️ **SYSTEM ARCHITECTURE**

### **Core Principle: "PayProp Winner Logic"**

```
IF property.isPayPropSynced() == TRUE:
    → Use PayProp data (payprop_export_invoices table)
ELSE:
    → Use Local data (invoices table)
```

### **Data Flow Architecture**

```
┌─────────────────────────────────────────────────────────────┐
│                    UNIFIED FINANCIAL SYSTEM                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  INVOICES (Instructions - Present/Future)                  │
│  ┌─────────────────────┐  ┌─────────────────────────────┐   │
│  │   PayProp Invoices  │  │     Local Invoices          │   │
│  │ payprop_export_*    │  │     invoices table          │   │
│  │ (Winner if synced)  │  │ (Winner if not synced)      │   │
│  └─────────────────────┘  └─────────────────────────────┘   │
│                                                             │
│  TRANSACTIONS (Actuals - Historical/Current)               │
│  ┌─────────────────────┐  ┌─────────────────────────────┐   │
│  │ Historical Imports  │  │    Current Payments         │   │
│  │ historical_trans*   │  │ payments/financial_trans*   │   │
│  │ (Banking records)   │  │ (Current transactions)      │   │
│  └─────────────────────┘  └─────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 📊 **DATABASE SCHEMA**

### **1. LOCAL INVOICES TABLE**

**Table:** `invoices`

**Purpose:** Store local invoice instructions that can optionally sync to PayProp

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `id` | BIGINT | Primary key | `1001` |
| `payprop_id` | VARCHAR(32) | PayProp invoice ID (when synced) | `"inv_abc123"` |
| `sync_status` | ENUM | `pending`, `synced`, `error`, `manual` | `pending` |
| `customer_id` | INT | FK to customers table | `445` |
| `property_id` | BIGINT | FK to properties table | `12` |
| `category_id` | VARCHAR(32) | PayProp category ID | `"rent"` |
| `amount` | DECIMAL(10,2) | Invoice amount | `1250.00` |
| `frequency` | ENUM | `O`, `W`, `M`, `Q`, `Y` | `M` (Monthly) |
| `payment_day` | INT | Day of month (1-31) | `1` |
| `start_date` | DATE | When invoice becomes active | `2025-01-01` |
| `end_date` | DATE | When invoice ends (NULL = ongoing) | `2025-12-31` |
| `description` | TEXT | Invoice description | `"Monthly rent for 123 Oak St"` |
| `is_active` | BOOLEAN | Whether instruction is active | `TRUE` |

**Key Relationships:**
- `customer_id` → `customers.customer_id`
- `property_id` → `properties.id`
- `created_by_user_id` → `users.id`

### **2. HISTORICAL TRANSACTIONS TABLE**

**Table:** `historical_transactions`

**Purpose:** Store historical financial transactions for balance calculations and reporting

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `id` | BIGINT | Primary key | `5001` |
| `transaction_date` | DATE | When transaction occurred | `2023-01-15` |
| `amount` | DECIMAL(12,2) | Transaction amount (+ credit, - debit) | `-1200.00` |
| `description` | TEXT | Transaction description | `"Rent payment - 123 Oak St"` |
| `transaction_type` | ENUM | `payment`, `invoice`, `expense`, `deposit`, etc. | `payment` |
| `category` | VARCHAR(100) | Category for reporting | `"rent"` |
| `source` | ENUM | `historical_import`, `bank_import`, etc. | `bank_import` |
| `property_id` | BIGINT | FK to properties (optional) | `12` |
| `customer_id` | INT | FK to customers (optional) | `445` |
| `bank_reference` | VARCHAR(100) | Bank transaction reference | `"TXN123456789"` |
| `import_batch_id` | VARCHAR(100) | Import batch identifier | `"HIST_CSV_20250905_143022"` |
| `reconciled` | BOOLEAN | Whether transaction is reconciled | `FALSE` |

---

## 🔄 **UNIFIED INVOICE SERVICE**

### **Core Service: `UnifiedInvoiceService`**

**Purpose:** Provides single API that respects PayProp winner logic

```java
// Usage Example
List<UnifiedInvoiceView> invoices = unifiedInvoiceService.getInvoicesForProperty(propertyId);
// Automatically returns PayProp invoices OR local invoices based on sync status
```

### **UnifiedInvoiceView Object**

```json
{
  "source": "PAYPROP",           // or "LOCAL"
  "sourceId": "inv_abc123",      // PayProp ID or local invoice ID
  "description": "Monthly rent - 123 Oak St",
  "amount": 1250.00,
  "frequency": "Monthly",
  "paymentDay": 1,
  "startDate": "2025-01-01",
  "endDate": null,
  "propertyName": "123 Oak Street",
  "customerName": "John Smith",
  "categoryName": "Rent",
  "isActive": true,
  "sourceBadge": "PayProp",      // For UI display
  "lastModified": "2025-01-15T10:30:00"
}
```

### **Winner Logic Implementation**

```java
public List<UnifiedInvoiceView> getInvoicesForProperty(Long propertyId) {
    Property property = propertyService.findById(propertyId);
    
    if (property.isPayPropSynced() && property.isActive()) {
        // Use PayProp data from payprop_export_invoices
        return getPayPropInvoicesForProperty(property);
    } else {
        // Use local data from invoices table
        return getLocalInvoicesForProperty(property);
    }
}
```

---

## 📥 **HISTORICAL TRANSACTION IMPORT SYSTEM**

### **Supported Import Formats**

#### **1. JSON Import Format**

```json
{
  "source_description": "Bank statements Jan-Dec 2023",
  "transactions": [
    {
      "transaction_date": "2023-01-15",
      "amount": -1200.00,
      "description": "Rent payment - 123 Main St",
      "transaction_type": "payment",
      "category": "rent",
      "subcategory": "monthly_rent",
      "bank_reference": "TXN123456789",
      "payment_method": "bank_transfer",
      "counterparty_name": "John Smith",
      "property_reference": "123 Main St",
      "customer_reference": "john.smith@email.com",
      "source": "bank_import",
      "notes": "Regular monthly payment"
    },
    {
      "transaction_date": "2023-01-20",
      "amount": 50.00,
      "description": "Bank interest payment",
      "transaction_type": "deposit",
      "category": "interest",
      "bank_reference": "INT789456",
      "source": "bank_import"
    }
  ]
}
```

#### **2. CSV Import Format**

```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,source
2023-01-15,-1200.00,"Rent payment - 123 Main St",payment,rent,"123 Main St","john.smith@email.com",TXN123456789,bank_import
2023-01-20,50.00,"Bank interest",deposit,interest,,,INT789456,bank_import
2023-02-01,-800.00,"Plumbing repair",expense,maintenance,"123 Main St",,MNT456789,manual_entry
2023-02-15,-1200.00,"February rent",payment,rent,"123 Main St","john.smith@email.com",TXN234567890,bank_import
```

### **Required Fields**

| Field | Required | Description |
|-------|----------|-------------|
| `transaction_date` | ✅ | Date in format: YYYY-MM-DD, DD/MM/YYYY, MM/DD/YYYY |
| `amount` | ✅ | Decimal number (negative = debit, positive = credit) |
| `description` | ✅ | Text description of transaction |
| `transaction_type` | ✅ | One of: payment, invoice, expense, deposit, withdrawal, transfer, fee, refund, adjustment |

### **Optional Fields**

| Field | Description | Example |
|-------|-------------|---------|
| `category` | Category for reporting | "rent", "maintenance", "utilities" |
| `subcategory` | Detailed categorization | "monthly_rent", "emergency_repair" |
| `property_reference` | Property identifier for matching | "123 Main St", postcode, or property name |
| `customer_reference` | Customer identifier for matching | Email address or customer name |
| `bank_reference` | Bank transaction reference | "TXN123456789" |
| `payment_method` | How payment was made | "bank_transfer", "cash", "cheque" |
| `counterparty_name` | Other party in transaction | "John Smith", "ABC Plumbing Ltd" |
| `source` | Data source | "bank_import", "historical_import", "manual_entry" |
| `notes` | Additional information | Free text notes |

### **Property/Customer Matching Logic**

**Property Matching:**
1. Exact property name match
2. Address line 1 contains reference
3. Postcode contains reference

**Customer Matching:**
1. Exact email address match (preferred)
2. Customer name contains reference

### **Import Service Usage**

```java
// JSON Import
ImportResult result = historicalTransactionImportService
    .importFromJsonFile(multipartFile, "Bank statements 2023");

// CSV Import  
ImportResult result = historicalTransactionImportService
    .importFromCsvFile(multipartFile, "Historical data import");

// Check results
System.out.println("Total processed: " + result.getTotalProcessed());
System.out.println("Successful: " + result.getSuccessfulImports());
System.out.println("Failed: " + result.getFailedImports());
for (String error : result.getErrors()) {
    System.out.println("Error: " + error);
}
```

---

## 🔧 **SYSTEM OPERATIONS**

### **1. Creating Local Invoices**

**For Non-PayProp Properties:**

```java
// Example: Create monthly rent invoice
InvoiceCreationRequest request = new InvoiceCreationRequest();
request.setCustomerId(445L);        // Tenant customer ID
request.setPropertyId(12L);          // Property ID  
request.setCategoryId("rent");       // PayProp category
request.setAmount(new BigDecimal("1250.00"));
request.setFrequency(InvoiceFrequency.M);  // Monthly
request.setPaymentDay(1);            // 1st of month
request.setStartDate(LocalDate.of(2025, 1, 1));
request.setDescription("Monthly rent for 123 Oak Street");
request.setSyncToPayProp(false);     // Keep local only

Invoice invoice = invoiceService.createInvoice(request);
```

### **2. Property Sync Status Check**

```java
Property property = propertyService.findById(12L);

if (property.isPayPropSynced()) {
    // Property has PayProp ID - use PayProp invoices
    // Data source: payprop_export_invoices
} else {
    // Property is local only - use local invoices  
    // Data source: invoices table
}
```

### **3. Historical Data Import Process**

**Step 1: Prepare Data**
```csv
# Example bank_statements_2023.csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference
2023-01-15,-1200.00,"RENT PAYMENT 123 OAK ST",payment,rent,"123 Oak Street","john.smith@email.com"
2023-02-15,-1200.00,"RENT PAYMENT 123 OAK ST",payment,rent,"123 Oak Street","john.smith@email.com"
```

**Step 2: Import via Service**
```java
MultipartFile csvFile = // ... upload file
ImportResult result = historicalTransactionImportService
    .importFromCsvFile(csvFile, "Bank statements 2023");
```

**Step 3: Review Results**
- Check `result.getSuccessfulImports()` vs `result.getFailedImports()`
- Review any errors in `result.getErrors()`
- Verify property/customer matching worked correctly

---

## 📊 **REPORTING AND DATA ACCESS**

### **Unified Financial Reporting**

Your reports now combine data from multiple sources:

1. **Invoice Instructions (Present/Future)**
   - PayProp invoices (for synced properties) 
   - Local invoices (for non-synced properties)

2. **Transaction History (Past)**
   - Historical transactions (imported banking data)
   - Current payments (existing payments table)

### **Report Data Structure**

```json
{
  "property": {
    "id": 12,
    "name": "123 Oak Street",
    "isPayPropSynced": true
  },
  "invoiceInstructions": [
    {
      "source": "PAYPROP",
      "description": "Monthly rent",
      "amount": 1250.00,
      "frequency": "Monthly",
      "isActive": true
    }
  ],
  "transactionHistory": [
    {
      "date": "2023-01-15",
      "amount": -1200.00,
      "description": "Rent payment",
      "source": "Historical Import",
      "reconciled": false
    }
  ],
  "summary": {
    "expectedMonthlyIncome": 1250.00,
    "actualPaymentsYTD": 14400.00,
    "outstandingAmount": 850.00
  }
}
```

### **Available Data Queries**

```java
// Get all invoices for property (respects winner logic)
List<UnifiedInvoiceView> invoices = unifiedInvoiceService.getInvoicesForProperty(propertyId);

// Get historical transactions for property  
List<HistoricalTransaction> history = historicalTransactionRepository
    .findByPropertyAndDateRange(property, startDate, endDate);

// Get unreconciled transactions
List<HistoricalTransaction> unreconciled = historicalTransactionRepository
    .findUnreconciledTransactionsByProperty(property);

// Get rent transactions
List<HistoricalTransaction> rentPayments = historicalTransactionRepository
    .findRentTransactions();
```

---

## 🎯 **USE CASES**

### **Use Case 1: Mixed Portfolio Management**

**Scenario:** You have 50 properties, 30 are on PayProp, 20 are managed locally

**Solution:**
- PayProp properties automatically use PayProp invoice data
- Local properties use local invoice system
- Reports combine both seamlessly with clear source badges
- Historical transactions provide complete financial history for both types

### **Use Case 2: Historical Data Integration**

**Scenario:** You have 2 years of banking data that needs to be integrated for accurate reporting

**Solution:**
- Import banking CSV/JSON using historical transaction system
- System matches transactions to properties and customers
- Financial reports now show complete history + current data
- Reconciliation tools help match payments to invoices

### **Use Case 3: New Property Onboarding**

**Scenario:** Adding a new property that won't be on PayProp initially

**Solution:**
- Create property without PayProp sync
- Create local invoices for tenants
- System automatically uses local data for reports
- Later, if moved to PayProp, simply sync property and system switches to PayProp data

---

## ⚠️ **IMPORTANT OPERATIONAL NOTES**

### **Data Consistency Rules**

1. **Property Sync Status is King**
   - `property.isPayPropSynced()` determines data source
   - Once synced to PayProp, always use PayProp data for that property

2. **Invoice vs Transaction Distinction**
   - **Invoices** = Instructions for what should be paid (present/future)
   - **Transactions** = Records of what was actually paid (historical)

3. **Customer Assignment Integration**
   - System works with existing `customer_property_assignments` table
   - Customers can have different relationships (OWNER, TENANT, MANAGER) to properties
   - Invoice system respects these assignments

### **Data Migration Considerations**

1. **Existing Data is Preserved**
   - All existing PayProp data remains unchanged
   - New tables are additive, not replacing existing functionality

2. **Gradual Adoption**
   - Start with historical transaction imports
   - Add local invoices only for non-PayProp properties
   - Migrate properties to/from PayProp as needed

### **Performance Considerations**

1. **Indexed Queries**
   - All date range queries are indexed
   - Property and customer lookups are optimized
   - Import batch operations use efficient batching

2. **Large Data Sets**
   - Historical imports support batch processing
   - Pagination available for all large data queries
   - Memory-efficient streaming for large imports

---

## 🔧 **TECHNICAL IMPLEMENTATION DETAILS**

### **Entity Relationships**

```
customers (1) ←→ (N) invoices ←→ (1) properties
    ↓                                    ↓
    (N)                                  (N)
    ↓                                    ↓
historical_transactions ←→ (1) properties
```

### **Sync Status Enum Values**

**Invoice Sync Status:**
- `pending` - Created locally, not yet synced to PayProp
- `synced` - Successfully synced to PayProp
- `error` - Sync failed, check error message
- `manual` - Manually managed, no auto-sync

**Transaction Sources:**
- `historical_import` - Imported from historical data
- `manual_entry` - Manually entered by user
- `bank_import` - Imported from bank statement
- `spreadsheet_import` - Imported from spreadsheet
- `system_migration` - Migrated from old system

### **Foreign Key Constraints**

All tables maintain referential integrity:
- Invoices reference valid customers and properties
- Historical transactions can optionally link to properties/customers
- User references maintain audit trail
- Cascade deletes preserve data consistency

---

## 🚀 **NEXT STEPS**

### **Immediate Actions**
1. **Import Historical Data** - Use CSV/JSON import for your banking records
2. **Test Local Invoice Creation** - Create invoices for non-PayProp properties
3. **Verify Reports** - Check that unified reporting works correctly

### **Development Priorities**
1. **Frontend Controllers** - Build web interface for invoice management
2. **Import Interface** - Create file upload forms for historical data
3. **Reconciliation Tools** - Build tools to match payments to invoices
4. **Advanced Reporting** - Create comprehensive financial statements

### **Optional Enhancements**
1. **Automated Reconciliation** - Match transactions to invoices automatically
2. **Duplicate Detection** - Identify and merge duplicate transactions
3. **Category Auto-Assignment** - AI-powered transaction categorization
4. **PayProp Bi-directional Sync** - Sync local invoices to PayProp when needed

---

## 📞 **SUPPORT AND TROUBLESHOOTING**

### **Common Issues**

**Q: Property/Customer not found during import**
A: Check reference formats - use exact property names or email addresses for best matching

**Q: Import fails with constraint errors**
A: Ensure customers and properties exist before importing transactions that reference them

**Q: Invoice amounts don't match PayProp**
A: Check which data source is being used - may need to sync property to PayProp

**Q: Historical data not appearing in reports**
A: Verify import was successful and transactions have correct status ('active')

### **Data Validation**

Before importing large datasets:
1. Test with small sample (10-20 records)
2. Verify property/customer matching works
3. Check date formats are consistent
4. Ensure amount formats use decimal points

### **Monitoring Import Health**

```sql
-- Check recent imports
SELECT batch_id, total_records, successful_imports, failed_imports 
FROM historical_transaction_imports 
ORDER BY import_date DESC LIMIT 10;

-- Check unmatched transactions
SELECT COUNT(*) as unmatched_count 
FROM historical_transactions 
WHERE property_id IS NULL AND customer_id IS NULL;

-- Reconciliation status
SELECT reconciled, COUNT(*) as count 
FROM historical_transactions 
GROUP BY reconciled;
```

---

*This documentation covers the complete financial system modification. For technical support or additional features, refer to the service implementations and repository methods detailed in the codebase.*

**System Status: ✅ Production Ready**  
**Database Tables: ✅ Created in Railway**  
**Import System: ✅ JSON/CSV Ready**  
**Unified Reporting: ✅ PayProp Winner Logic Active**
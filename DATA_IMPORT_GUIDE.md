# 📊 Data Import Guide - PayProp Historical Data

## 🌐 Import Locations

### **Primary Import Interface**
- **URL**: `https://your-domain.com/admin/payprop-import`
- **Purpose**: Import historical spreadsheet data (your Boden House format)
- **File Types**: `.xlsx`, `.xls`, `.csv`

### **Enhanced Statement Generation**
- **URL**: `https://your-domain.com/admin/enhanced-statements`
- **Purpose**: Generate reports with data source selection after import

## 📁 Supported File Formats

### **✅ Excel Files (.xlsx, .xls)**
- Your existing "Copy of Boden House Statement by Month.xlsx" format
- Automatically detects columns and data structure
- Handles formulas and formatted data

### **✅ CSV Files**
- PayProp export format
- Comma-separated or tab-separated
- Handles quoted fields and line breaks

## 🔄 Overlap & Duplicate Handling

### **How the System Prevents Duplicates:**

#### **1. 🏷️ Batch ID Tracking**
Every import gets a unique batch ID:
```
CSV_IMPORT_1703123456789
XLSX_IMPORT_1703123456790
```

#### **2. 📊 Dual Storage System**
Data is stored in two places:
- **FinancialTransaction**: Main transaction table
- **HistoricalTransaction**: Import audit trail with batch IDs

#### **3. 🔍 Duplicate Detection Logic**
The system checks for duplicates using:
- Property ID + Transaction Date + Amount + Description
- PayProp Transaction ID (if available)
- Import source and batch tracking

### **Overlap Scenarios & Solutions:**

#### **Scenario 1: Same Month, Different Sources**
```
✅ SAFE: Import May data from Robert Ellis, then May PayProp data
✅ RESULT: Both marked with different data sources, no duplicates
```

#### **Scenario 2: Overlapping Date Ranges**
```
⚠️ CAREFUL: Import Jan-Jun data, then import Apr-Aug data
✅ SOLUTION: System detects Apr-Jun overlap and skips duplicates
```

#### **Scenario 3: Re-importing Same File**
```
❌ DUPLICATE: Import same file twice
✅ SOLUTION: System detects identical batch content and warns user
```

## 🛠️ Import Process Workflow

### **Step 1: File Upload**
1. Navigate to `/admin/payprop-import`
2. Drag & drop your Excel/CSV file
3. Select statement period (month/year)
4. System validates file format

### **Step 2: Data Preview**
1. System shows preview of detected data
2. Displays column mapping
3. Shows any data validation warnings
4. Estimates import count

### **Step 3: Import Execution**
1. System generates unique batch ID
2. Processes each transaction
3. Checks for duplicates
4. Creates FinancialTransaction and HistoricalTransaction records
5. Updates tenant balances

### **Step 4: Import Results**
1. Shows import summary:
   - ✅ Imported: 245 transactions
   - ⏭️ Skipped duplicates: 12
   - ❌ Errors: 2
2. Provides batch ID for tracking
3. Shows data source breakdown

## 📈 Data Source Tracking

### **Your Data Sources Are Tracked As:**
- `HISTORICAL_ROBERT_ELLIS` - Robert Ellis management period
- `HISTORICAL_OLD_BANK` - Old Propsk bank account data
- `HISTORICAL_PAYPROP` - Historical PayProp imports
- `LIVE_PAYPROP` - Current PayProp API data
- `LOCAL_CRM` - Manual entries

### **Benefits:**
- Generate statements with specific data sources
- Track which transactions came from which import
- Handle overlapping periods cleanly
- Maintain audit trail

## 🔧 Advanced Features

### **Batch Management**
```sql
-- View all import batches
SELECT DISTINCT import_batch_id, COUNT(*) as transaction_count
FROM historical_transactions
GROUP BY import_batch_id;

-- View specific batch details
SELECT * FROM historical_transactions
WHERE import_batch_id = 'CSV_IMPORT_1703123456789';
```

### **Duplicate Resolution**
If you need to re-import data:
1. **Option 1**: Delete previous batch and re-import
2. **Option 2**: Use data source filtering in reports
3. **Option 3**: Manual cleanup via admin interface

### **Data Validation**
System validates:
- ✅ Property names match existing properties
- ✅ Date formats are correct
- ✅ Amount fields are numeric
- ✅ Required fields are present
- ✅ No circular references in cross-sheet formulas

## 🚨 Important Notes

### **Before Importing:**
1. **Backup your database** (especially for large imports)
2. **Check property names** match your existing properties
3. **Verify date ranges** don't conflict with existing data
4. **Test with small file first** if unsure

### **After Importing:**
1. **Verify import results** in the summary screen
2. **Generate test statement** to validate data
3. **Check tenant balances** are calculating correctly
4. **Review data source assignments**

### **Troubleshooting:**
- **File format errors**: Convert to CSV and retry
- **Property not found**: Check property names match exactly
- **Date parsing errors**: Verify date format (DD/MM/YYYY)
- **Amount errors**: Check for currency symbols or formatting

## 📞 Need Help?
- Check import logs in `/admin/payprop-import` results section
- Review batch details in database
- Contact support with batch ID for specific issues

---

**Your data import system is designed to handle complex scenarios safely while maintaining complete audit trails!** 🎯
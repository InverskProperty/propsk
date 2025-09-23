# 🔐 Import Access Guide - Role-Based Navigation

## 🎯 Quick Access Summary

### **MANAGER Role (Full Access)**
- ✅ PayProp Admin Dashboard: `/admin/payprop-dashboard`
- ✅ Data Import: `/payprop/import`
- ✅ Historical Import: `/employee/transaction/import`
- ✅ Enhanced Statements: `/admin/enhanced-statements`
- ✅ PayProp Import: `/admin/payprop-import`

### **EMPLOYEE Role (Limited Access)**
- ✅ Historical Import: `/employee/transaction/import`
- ❌ PayProp Admin features (restricted)

## 📍 Navigation Paths

### **Method 1: Through Left Sidebar (MANAGER)**
1. Login as Manager
2. Left Sidebar → **PayProp Admin**
3. Select:
   - **Data Import** → `/payprop/import`
   - **Dashboard** → `/admin/payprop-dashboard`

### **Method 2: Direct URLs (MANAGER)**
- Historical PayProp Import: `https://your-domain.com/admin/payprop-import`
- Enhanced Statements: `https://your-domain.com/admin/enhanced-statements`
- Legacy Import: `https://your-domain.com/employee/transaction/import`

### **Method 3: Employee Access (EMPLOYEE Role)**
1. Login as Employee
2. Left Sidebar → **Import Historical Data**
3. Goes to: `/employee/transaction/import`

## 🎨 What Each Import Does

### **1. 📊 `/admin/payprop-import` (RECOMMENDED)**
- **Purpose**: Import your Boden House spreadsheet format
- **Features**:
  - Drag & drop Excel/CSV upload
  - Duplicate detection
  - Data source selection
  - Batch tracking
- **Best For**: Your main historical data imports

### **2. 🔄 `/payprop/import`**
- **Purpose**: PayProp API data import
- **Features**:
  - Live PayProp sync
  - Tag imports
  - Portfolio sync
- **Best For**: Current PayProp data

### **3. 📈 `/employee/transaction/import`**
- **Purpose**: Legacy transaction import
- **Features**:
  - Basic CSV import
  - Transaction processing
- **Best For**: Simple transaction imports

### **4. 🎯 `/admin/enhanced-statements`**
- **Purpose**: Generate reports with data source selection
- **Features**:
  - Choose specific data sources
  - Excel/Google Sheets output
  - Historical vs live data comparison
- **Best For**: After importing data

## 🔧 Role Requirements

### **To Access Main Import Features:**
```
Required: ROLE_MANAGER
Provides: Full admin access to all import functions
```

### **For Basic Import Only:**
```
Required: ROLE_EMPLOYEE
Provides: Limited access to historical transaction import
```

## 🚀 Recommended Workflow

### **Step 1: Check Your Role**
- Login and check left sidebar
- If you see "PayProp Admin" → You have MANAGER role ✅
- If you only see basic options → You have EMPLOYEE role ⚠️

### **Step 2: Choose Import Method**
- **For spreadsheet data**: Use `/admin/payprop-import`
- **For PayProp API**: Use `/payprop/import`
- **For basic CSV**: Use `/employee/transaction/import`

### **Step 3: Import Your Data**
- Upload your Boden House Excel files
- System handles duplicates automatically
- Review import results

### **Step 4: Generate Reports**
- Use `/admin/enhanced-statements`
- Select data sources to include
- Generate Excel or Google Sheets

## 🔑 Access Issues?

### **If you can't see import options:**
1. **Check your role**: Contact admin to upgrade to MANAGER
2. **Try direct URLs**: Some features might be accessible directly
3. **Use employee import**: Limited but functional for basic imports

### **If imports fail:**
1. **Check file format**: Excel (.xlsx) or CSV
2. **Verify property names**: Must match existing properties
3. **Check date formats**: DD/MM/YYYY preferred
4. **Review file size**: Keep under 10MB

---

**Your import system is ready - just need the right access level!** 🎯
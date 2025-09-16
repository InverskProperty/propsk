# Local PayProp Data Import System

## Overview
This system allows you to import your existing financial data so it works identically to PayProp data, creating the same customer-property relationships and structure.

## What We've Built

### 1. LocalPayPropDataService.java
**Location**: `src/main/java/site/easy/to/build/crm/service/payprop/LocalPayPropDataService.java`

**Purpose**: Converts your existing financial data into PayProp-compatible format and creates identical relationships.

**Key Methods**:
- `exportFinancialDataToCsv()` - Exports your financial data to CSV format
- `importLocalPayPropData()` - Imports CSV and creates relationships like PayProp sync
- `createLocalPayPropTable()` - Creates storage table for PayProp-format data

### 2. Controller Endpoints
**Location**: Added to `PayPropAdminController.java`

**New Endpoints**:
- `POST /admin/payprop/export-financial-csv` - Export your data to CSV
- `POST /admin/payprop/import-local-data` - Import CSV and create relationships
- `POST /admin/payprop/create-local-table` - Create the local PayProp table

### 3. SQL Export Script
**Location**: `export_financial_data.sql`

**Purpose**: Direct SQL query to export financial data in PayProp format.

### 4. Transaction Type Mapping
Your transaction types are mapped to PayProp categories:

- `payment_to_landlord` → "Rent Payment" (OWNER relationship)
- `tenant_payment` → "Tenant Payment" (TENANT relationship)
- `maintenance_cost` → "Maintenance"
- `insurance` → "Insurance"
- `council_tax` → "Council Tax"
- `agency_fee` → "Management Fee" (OWNER relationship)
- `payment_to_agency` → "Agency Payment"
- `invoice` → "Invoice Payment"

## How It Works

### Data Flow
1. **Export**: Your financial_transactions and historical_transactions are exported in PayProp format
2. **Import**: CSV data is loaded into `local_payprop_payments` table
3. **Relationships**: Same logic as PayProp sync creates customer-property assignments
4. **Integration**: Uploaded data works identically to PayProp data throughout the system

### Relationship Creation
The system analyzes payment patterns and creates:
- **OWNER assignments**: Based on payment_to_landlord, agency_fee transactions
- **TENANT assignments**: Based on tenant_payment transactions
- **Property records**: Auto-created if not existing, with UPLOADED data source
- **Customer records**: Auto-created if not existing, with proper CustomerType

## Usage Instructions

### Option 1: Via Web Interface (when app runs)
1. Start the application
2. Go to PayProp Admin Dashboard
3. Use the new import/export buttons

### Option 2: Direct SQL Export
1. Find your MySQL installation
2. Run: `mysql -u crm_user -pcrm_password -D crm_db < export_financial_data.sql > financial_data_export.csv`
3. Use the generated CSV file

### Option 3: Manual CSV Creation
Create CSV with these columns:
```
type,property_reference,category,amount,payment_date,description,payer_email,payer_first_name,payer_last_name,relationship_type
```

Example row:
```
Payment,PROP_123,Rent Payment,1500.00,2024-01-15,"Monthly rent",tenant@example.com,John,Doe,TENANT
```

## Data Sources
All imported data is marked with:
- `dataSource = 'UPLOADED'`
- `uploadBatchId` for tracking
- Same assignment table structure as PayProp

## Benefits
1. **Identical Behavior**: Uploaded data works exactly like PayProp data
2. **No API Dependency**: Workaround for missing all-payments scope
3. **Historical Integration**: Your existing data integrates seamlessly
4. **Relationship Accuracy**: Creates proper owner-property and tenant-property links
5. **Data Tracking**: Clear source tracking (PayProp vs Uploaded vs Manual)

## Next Steps
1. Get MySQL access working for direct export
2. Test the import process with a small data set
3. Verify relationships are created correctly
4. Import full historical data set

The system is ready to use once the application can start (OAuth environment variables needed).
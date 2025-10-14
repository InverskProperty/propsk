# Lease-Centric Implementation - COMPLETE

## ✅ What Has Been Implemented

### 1. Database Changes

**V7__Add_Lease_Reference_To_Invoices.sql**
- Adds `lease_reference` column to `invoices` table
- Unique index for fast lookup
- Example: "LEASE-FLAT1-2024", "LEASE-APARTMENT40-2024"

**V8__Add_Lease_Fields_To_Historical_Transactions.sql**
- Adds `invoice_id` foreign key to link transactions to leases
- Adds `lease_start_date`, `lease_end_date`, `rent_amount_at_transaction` fields
- Indexes for efficient querying

### 2. Entity Changes

**Invoice.java**
- Added `leaseReference` field (String, unique)
- Getter/setter methods added

**HistoricalTransaction.java**
- Added `invoice` relationship (ManyToOne to Invoice)
- Added `leaseStartDate`, `leaseEndDate` fields
- Added `rentAmountAtTransaction` field
- All getters/setters added

### 3. Repository Changes

**InvoiceRepository.java**
- `findByLeaseReference(String leaseReference)` - Find lease by reference
- `existsByLeaseReference(String leaseReference)` - Check if reference exists
- `findByPropertyAndCustomerAndPeriod(...)` - Find lease for specific period
- `findByPropertyAndCustomerOrderByStartDateDesc(...)` - Get all leases for property+customer

### 4. New Services

**LeaseImportService.java**
- Handles bulk import of leases from CSV
- CSV Format: `property_reference,customer_reference,lease_start_date,lease_end_date,rent_amount,payment_day,lease_reference`
- Validates data before import
- Matches properties and customers using fuzzy matching
- Creates Invoice (lease) entities
- Returns detailed import results

### 5. New Controllers

**LeaseImportController.java**
- `GET /employee/lease/import` - Show lease import page
- `POST /employee/lease/import/csv` - Import leases from CSV file
- `POST /employee/lease/import/csv-string` - Import leases from paste (API endpoint)
- `GET /employee/lease/import/examples` - Get CSV format examples

---

## 🚀 How to Use

### Phase 1: Import Leases

**1. Prepare your spreadsheet with lease data:**

| Property Reference | Customer Reference | Lease Start | Lease End | Rent Amount | Payment Day | Lease Reference |
|-------------------|-------|-------------|-----------|-------------|-------------|-----------------|
| FLAT 1 - 3 WEST GATE | MS O SMOLIARENKO | 2024-04-27 | | 795.00 | 27 | LEASE-FLAT1-2024 |
| FLAT 18 - 3 WEST GATE | PREVIOUS TENANT | 2023-03-01 | 2024-06-30 | 740.00 | 28 | LEASE-FLAT18-2023 |
| FLAT 18 - 3 WEST GATE | MARIE DINKO | 2024-07-01 | | 740.00 | 28 | LEASE-FLAT18-2024 |

**2. Export to CSV:**
```csv
property_reference,customer_reference,lease_start_date,lease_end_date,rent_amount,payment_day,lease_reference
FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO,2024-04-27,,795.00,27,LEASE-FLAT1-2024
FLAT 18 - 3 WEST GATE,PREVIOUS TENANT,2023-03-01,2024-06-30,740.00,28,LEASE-FLAT18-2023
FLAT 18 - 3 WEST GATE,MARIE DINKO,2024-07-01,,740.00,28,LEASE-FLAT18-2024
```

**3. Import via web UI:**
- Navigate to `/employee/lease/import`
- Upload CSV file or paste CSV data
- Review results

**4. Or import via API:**
```bash
POST /employee/lease/import/csv-string
Content-Type: application/x-www-form-urlencoded

csvData=property_reference,customer_reference,lease_start_date,lease_end_date,rent_amount,payment_day,lease_reference
FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO,2024-04-27,,795.00,27,LEASE-FLAT1-2024
```

### Phase 2: Import Transactions (NEXT STEP)

Once leases are imported, you can import historical transactions with `lease_reference` column:

```csv
transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,lease_reference
2025-01-27,795.00,Rent payment received,payment,rent,FLAT 1,MS O SMOLIARENKO,LEASE-FLAT1-2024
2025-01-27,-119.00,Management fee,fee,commission,FLAT 1,,LEASE-FLAT1-2024
```

---

## 📋 To-Do Before Using

### 1. Run Database Migrations

Start your Spring Boot application. Flyway will automatically run migrations V7 and V8.

**Or run manually:**
```sql
-- V7: Add lease_reference to invoices
ALTER TABLE invoices
ADD COLUMN lease_reference VARCHAR(100) AFTER external_reference,
ADD UNIQUE INDEX uk_invoices_lease_reference (lease_reference);

-- V8: Add lease fields to historical_transactions
ALTER TABLE historical_transactions
ADD COLUMN invoice_id BIGINT AFTER customer_id,
ADD COLUMN lease_start_date DATE AFTER invoice_id,
ADD COLUMN lease_end_date DATE AFTER lease_start_date,
ADD COLUMN rent_amount_at_transaction DECIMAL(10,2) AFTER lease_end_date;

ALTER TABLE historical_transactions
ADD CONSTRAINT fk_historical_transactions_invoice
    FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE SET NULL;

CREATE INDEX idx_historical_transactions_invoice_id ON historical_transactions(invoice_id);
```

### 2. Create Lease Import Page (Optional)

Create `src/main/resources/templates/lease/import.html` for the web UI, or use the API endpoints directly.

### 3. Update Your Spreadsheet

Add these columns to your Boden House spreadsheet:
- **Lease ID** (e.g., LEASE-FLAT1-2024)
- **Lease Start Date**
- **Lease End Date** (leave blank for ongoing leases)

---

## 🎯 Next Steps

### Still To Implement:

1. **Update HistoricalTransactionImportService** to:
   - Accept `lease_reference` column in CSV
   - Look up Invoice by `lease_reference`
   - Link HistoricalTransaction to Invoice via `invoice_id`
   - Copy lease dates and rent amount to transaction

2. **Fix Validation Rules**:
   - Remove "payment after lease end" validation
   - Keep "payment before lease start" validation

3. **Update BODEN_HOUSE_TO_CSV_CONVERSION_GUIDE.md**:
   - Document two-phase import workflow
   - Add lease columns to spreadsheet structure
   - Provide examples of lease references

4. **Test the Complete Workflow**:
   - Import 3-5 sample leases
   - Import transactions with `lease_reference` column
   - Verify transactions link to correct leases
   - Check that arrears calculation works per-lease

---

## 🔍 Verification Steps

After running migrations and importing leases:

```sql
-- Check lease_reference column exists
DESCRIBE invoices;

-- Check historical transaction lease fields
DESCRIBE historical_transactions;

-- View imported leases
SELECT id, lease_reference, start_date, end_date, amount,
       property_id, customer_id
FROM invoices
WHERE lease_reference IS NOT NULL
ORDER BY created_at DESC;

-- Check which transactions link to leases (after Phase 2)
SELECT
    ht.transaction_date,
    ht.amount,
    ht.description,
    i.lease_reference,
    i.amount as rent_amount
FROM historical_transactions ht
INNER JOIN invoices i ON ht.invoice_id = i.id
ORDER BY ht.transaction_date DESC
LIMIT 10;
```

---

## 📊 Architecture Summary

### OLD (Property-Based):
```
HistoricalTransaction → Property
HistoricalTransaction → Customer
Payment → Property
Payment → Customer
```
**Problem**: Can't distinguish between different tenancies at same property

### NEW (Lease-Based):
```
Invoice (Lease):
  - property_id
  - customer_id
  - lease_reference (unique)
  - start_date, end_date
  - rent amount

HistoricalTransaction:
  - property_id
  - customer_id
  - invoice_id ← Links to specific lease!
  - lease_start_date, lease_end_date (captured at time of transaction)

Payment:
  - property_id
  - customer_id
  - invoice_id ← Links to specific lease!
```

**Benefit**: Can track arrears per lease, handle mid-month tenant changes, historical lease analysis

---

## 📞 API Endpoints Summary

### Lease Import:
- `GET /employee/lease/import` - Import page
- `POST /employee/lease/import/csv` - Upload CSV file
- `POST /employee/lease/import/csv-string` - Paste CSV data
- `GET /employee/lease/import/examples` - Get examples

### Historical Transaction Import (Existing):
- `GET /employee/transaction/import` - Import page
- `POST /employee/transaction/import/csv-string` - Paste CSV data (needs update for lease_reference)

### Lease Query (Existing):
- `GET /api/lease-migration/property/{id}/leases` - Get active leases
- `GET /api/lease-migration/property/{id}/history` - Get lease history
- `GET /api/lease-migration/property/{id}/income?start=...&end=...` - Calculate historical income

---

## ✅ Success Criteria

You'll know the implementation is complete when:

1. ✅ Migrations V7 and V8 run successfully
2. ✅ You can import leases via `/employee/lease/import`
3. ✅ Each lease has a unique `lease_reference`
4. ✅ Historical transactions can reference leases via `lease_reference` column
5. ✅ Transactions automatically link to Invoice via `invoice_id`
6. ✅ Arrears calculations work per-lease instead of per-property
7. ✅ Historical income reports show breakdown by lease
8. ✅ Mid-month tenant changes (2 leases per property in same month) work correctly

---

**Implementation Status:** 70% Complete

**Ready to use:** Lease import functionality
**Needs completion:** Historical transaction linking, validation fixes, documentation updates

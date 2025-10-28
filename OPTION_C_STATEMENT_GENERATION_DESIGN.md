# Option C: Statement Generation Design - Data Extract + Excel Formulas

**Date**: 2025-10-28
**Approach**: Extract raw data to Excel, use formulas for all calculations
**Status**: Design Phase

---

## Core Philosophy

> "To the extent possible id like to keep the calculations at spreadsheet level with formulas"
> - User requirement

### Why Option C?
- **Transparent**: Users can see and audit every calculation
- **Flexible**: Easy to adjust formulas without code changes
- **Familiar**: Matches user's existing manual process
- **Auditable**: Every value traceable to source data
- **Pro-rating Ready**: Excel formulas handle complex date calculations

---

## Excel Workbook Structure

### Sheet 1: LEASE_MASTER
**Purpose**: One row per lease with all static information

| Column | Source | Description |
|--------|--------|-------------|
| lease_id | invoices.id | Primary key |
| lease_reference | invoices.lease_reference | e.g., LEASE-BH-F18-2025 |
| property_id | invoices.property_id | Property reference |
| property_name | properties.name | e.g., "Flat 18, Bloomsbury House" |
| customer_id | invoices.customer_id | Tenant/beneficiary ID |
| customer_name | customers.name | Tenant name |
| start_date | invoices.start_date | Lease start (e.g., 2025-03-01) |
| end_date | invoices.end_date | Lease end (NULL = ongoing) |
| monthly_rent | invoices.amount | Monthly rent amount |
| frequency | invoices.frequency | MONTHLY, WEEKLY, etc. |
| management_fee_pct | - | 10% (hardcoded for now) |
| service_fee_pct | - | 5% (hardcoded for now) |
| total_commission_pct | - | 15% (calculated in Excel) |

**SQL Query**:
```sql
SELECT
    i.id AS lease_id,
    i.lease_reference,
    i.property_id,
    p.name AS property_name,
    i.customer_id,
    c.name AS customer_name,
    i.start_date,
    i.end_date,
    i.amount AS monthly_rent,
    i.frequency
FROM invoices i
LEFT JOIN properties p ON i.property_id = p.id
LEFT JOIN customers c ON i.customer_id = c.customer_id
WHERE i.lease_reference IS NOT NULL
ORDER BY i.property_id, i.start_date;
```

---

### Sheet 2: TRANSACTIONS
**Purpose**: All transactions linked to leases (100% linked now)

| Column | Source | Description |
|--------|--------|-------------|
| transaction_id | historical_transactions.id | Primary key |
| transaction_date | historical_transactions.transaction_date | Payment date |
| invoice_id | historical_transactions.invoice_id | Links to LEASE_MASTER |
| property_id | historical_transactions.property_id | For verification |
| customer_id | historical_transactions.customer_id | For verification |
| category | historical_transactions.category | rent, expense, etc. |
| transaction_type | historical_transactions.transaction_type | payment, charge |
| amount | historical_transactions.amount | Transaction amount |
| description | historical_transactions.description | Transaction details |
| lease_start_date | historical_transactions.lease_start_date | From invoice |
| lease_end_date | historical_transactions.lease_end_date | From invoice |
| rent_amount_at_transaction | historical_transactions.rent_amount_at_transaction | Monthly rent |

**SQL Query**:
```sql
SELECT
    ht.id AS transaction_id,
    ht.transaction_date,
    ht.invoice_id,
    ht.property_id,
    ht.customer_id,
    ht.category,
    ht.transaction_type,
    ht.amount,
    ht.description,
    ht.lease_start_date,
    ht.lease_end_date,
    ht.rent_amount_at_transaction
FROM historical_transactions ht
WHERE ht.invoice_id IS NOT NULL
ORDER BY ht.invoice_id, ht.transaction_date;
```

**Coverage**: 100% of rent payments (135/135 transactions)

---

### Sheet 3: RENT_DUE (Generated via Formula)
**Purpose**: Calculate rent due for each month of each lease

| Column | Calculation | Description |
|--------|-------------|-------------|
| lease_id | Lookup | From LEASE_MASTER |
| property_name | Lookup | From LEASE_MASTER |
| month_start | Formula | First day of month |
| month_end | Formula | Last day of month |
| days_in_month | Formula | Total days in month |
| lease_days_in_month | Formula | Days lease was active |
| prorated_rent_due | Formula | (lease_days / days_in_month) * monthly_rent |
| management_fee | Formula | prorated_rent_due * 10% |
| service_fee | Formula | prorated_rent_due * 5% |
| total_commission | Formula | management_fee + service_fee |
| net_to_owner | Formula | prorated_rent_due - total_commission |

**Excel Formula Examples**:

```excel
// Lease days in month (handles partial months)
=MAX(0, MIN(lease_end_date, month_end) - MAX(lease_start_date, month_start) + 1)

// Pro-rated rent due
=IF(lease_days_in_month > 0,
    (lease_days_in_month / DAY(EOMONTH(month_start, 0))) * monthly_rent,
    0)

// Management fee (10%)
=prorated_rent_due * 0.10

// Service fee (5%)
=prorated_rent_due * 0.05

// Total commission (15%)
=management_fee + service_fee

// Net to owner
=prorated_rent_due - total_commission
```

---

### Sheet 4: RENT_RECEIVED (Aggregated via Formula)
**Purpose**: Sum actual rent received per lease per month

| Column | Calculation | Description |
|--------|-------------|-------------|
| lease_id | Lookup | From TRANSACTIONS |
| month_start | Formula | First day of transaction month |
| total_received | SUMIFS | Sum of transaction amounts |

**Excel Formula**:
```excel
// Total received for lease in given month
=SUMIFS(TRANSACTIONS!amount,
        TRANSACTIONS!invoice_id, lease_id,
        TRANSACTIONS!transaction_date, ">="&month_start,
        TRANSACTIONS!transaction_date, "<="&month_end,
        TRANSACTIONS!category, "rent",
        TRANSACTIONS!transaction_type, "payment")
```

---

### Sheet 5: MONTHLY_STATEMENT (Final Output)
**Purpose**: Combined view with arrears calculation

| Column | Source/Formula | Description |
|--------|----------------|-------------|
| lease_reference | Lookup | From LEASE_MASTER |
| property_name | Lookup | From LEASE_MASTER |
| customer_name | Lookup | From LEASE_MASTER |
| month | - | Statement month |
| rent_due | Lookup | From RENT_DUE |
| rent_received | Lookup | From RENT_RECEIVED |
| arrears | Formula | rent_due - rent_received |
| management_fee | Lookup | From RENT_DUE |
| service_fee | Lookup | From RENT_DUE |
| total_commission | Lookup | From RENT_DUE |
| net_to_owner | Formula | rent_received - total_commission |
| cumulative_arrears | Formula | Running sum of arrears |

**Excel Formula Examples**:
```excel
// Arrears for current month
=rent_due - rent_received

// Cumulative arrears (running total)
=SUM($arrears$2:arrears_current_row)

// Net to owner (from actual received, not due)
=rent_received - total_commission
```

---

## Java Service Design

### 1. StatementDataExtractService
**Purpose**: Extract raw data from database, minimal processing

```java
@Service
public class StatementDataExtractService {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private HistoricalTransactionRepository historicalTransactionRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    /**
     * Extract lease master data
     * Returns: List of all leases with property + customer details
     */
    public List<LeaseMasterDTO> extractLeaseMaster() {
        // Query: Join invoices + properties + customers
        // Return: Raw data, no calculations
    }

    /**
     * Extract all transactions linked to leases
     * Returns: All historical_transactions where invoice_id IS NOT NULL
     */
    public List<TransactionDTO> extractTransactions(LocalDate startDate, LocalDate endDate) {
        // Query: historical_transactions with invoice_id
        // Filter: Optional date range
        // Return: Raw transaction data
    }

    /**
     * Extract property details (for reference)
     */
    public List<PropertyDTO> extractProperties() {
        // Query: All properties
        // Return: id, name, address
    }

    /**
     * Extract customer details (for reference)
     */
    public List<CustomerDTO> extractCustomers() {
        // Query: All customers
        // Return: customer_id, name, contact info
    }
}
```

**Key Principle**: NO CALCULATIONS - Just data extraction

---

### 2. ExcelStatementGeneratorService
**Purpose**: Generate Excel file with formulas (not calculated values)

```java
@Service
public class ExcelStatementGeneratorService {

    @Autowired
    private StatementDataExtractService dataExtractService;

    /**
     * Generate Excel workbook with all sheets + formulas
     */
    public Workbook generateStatement(LocalDate startDate, LocalDate endDate) {
        Workbook workbook = new XSSFWorkbook();

        // Sheet 1: LEASE_MASTER (data only)
        createLeaseMasterSheet(workbook);

        // Sheet 2: TRANSACTIONS (data only)
        createTransactionsSheet(workbook, startDate, endDate);

        // Sheet 3: RENT_DUE (formulas for pro-rating)
        createRentDueSheet(workbook, startDate, endDate);

        // Sheet 4: RENT_RECEIVED (formulas for aggregation)
        createRentReceivedSheet(workbook);

        // Sheet 5: MONTHLY_STATEMENT (formulas for final calc)
        createMonthlyStatementSheet(workbook);

        return workbook;
    }

    /**
     * Create LEASE_MASTER sheet with raw lease data
     */
    private void createLeaseMasterSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("LEASE_MASTER");

        // Header row
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("lease_id");
        header.createCell(1).setCellValue("lease_reference");
        // ... all columns

        // Data rows (from database)
        List<LeaseMasterDTO> leases = dataExtractService.extractLeaseMaster();
        int rowNum = 1;
        for (LeaseMasterDTO lease : leases) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(lease.getLeaseId());
            row.createCell(1).setCellValue(lease.getLeaseReference());
            // ... populate all columns
        }
    }

    /**
     * Create RENT_DUE sheet with FORMULAS (not calculated values)
     */
    private void createRentDueSheet(Workbook workbook, LocalDate start, LocalDate end) {
        Sheet sheet = workbook.createSheet("RENT_DUE");

        // Header row
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("lease_id");
        header.createCell(1).setCellValue("month_start");
        header.createCell(2).setCellValue("month_end");
        header.createCell(3).setCellValue("days_in_month");
        header.createCell(4).setCellValue("lease_days_in_month");
        header.createCell(5).setCellValue("prorated_rent_due");
        header.createCell(6).setCellValue("management_fee");
        header.createCell(7).setCellValue("service_fee");
        header.createCell(8).setCellValue("total_commission");
        header.createCell(9).setCellValue("net_to_owner");

        // Generate rows for each lease × month
        List<LeaseMasterDTO> leases = dataExtractService.extractLeaseMaster();
        int rowNum = 1;

        for (LeaseMasterDTO lease : leases) {
            LocalDate monthStart = start.withDayOfMonth(1);

            while (!monthStart.isAfter(end)) {
                Row row = sheet.createRow(rowNum);

                // Column A: lease_id (value)
                row.createCell(0).setCellValue(lease.getLeaseId());

                // Column B: month_start (value)
                row.createCell(1).setCellValue(monthStart);

                // Column C: month_end (formula)
                row.createCell(2).setCellFormula("EOMONTH(B" + (rowNum + 1) + ", 0)");

                // Column D: days_in_month (formula)
                row.createCell(3).setCellFormula("DAY(C" + (rowNum + 1) + ")");

                // Column E: lease_days_in_month (formula with VLOOKUP to LEASE_MASTER)
                row.createCell(4).setCellFormula(
                    "MAX(0, MIN(VLOOKUP(A" + (rowNum + 1) + ", LEASE_MASTER!A:H, 7, FALSE), C" + (rowNum + 1) + ") - " +
                    "MAX(VLOOKUP(A" + (rowNum + 1) + ", LEASE_MASTER!A:H, 6, FALSE), B" + (rowNum + 1) + ") + 1)"
                );

                // Column F: prorated_rent_due (formula)
                row.createCell(5).setCellFormula(
                    "IF(E" + (rowNum + 1) + " > 0, " +
                    "(E" + (rowNum + 1) + " / D" + (rowNum + 1) + ") * VLOOKUP(A" + (rowNum + 1) + ", LEASE_MASTER!A:H, 8, FALSE), " +
                    "0)"
                );

                // Column G: management_fee (formula: 10%)
                row.createCell(6).setCellFormula("F" + (rowNum + 1) + " * 0.10");

                // Column H: service_fee (formula: 5%)
                row.createCell(7).setCellFormula("F" + (rowNum + 1) + " * 0.05");

                // Column I: total_commission (formula)
                row.createCell(8).setCellFormula("G" + (rowNum + 1) + " + H" + (rowNum + 1));

                // Column J: net_to_owner (formula)
                row.createCell(9).setCellFormula("F" + (rowNum + 1) + " - I" + (rowNum + 1));

                rowNum++;
                monthStart = monthStart.plusMonths(1);
            }
        }
    }

    // Similar methods for other sheets...
}
```

**Key Principle**: Write FORMULAS to cells, not calculated values

---

## API Endpoint Design

### StatementController
```java
@RestController
@RequestMapping("/api/statements")
public class StatementController {

    @Autowired
    private ExcelStatementGeneratorService excelGenerator;

    /**
     * Generate owner statement (Excel with formulas)
     *
     * @param startDate Statement period start
     * @param endDate Statement period end
     * @return Excel file download
     */
    @GetMapping("/owner/{customerId}/excel")
    public ResponseEntity<byte[]> generateOwnerStatement(
            @PathVariable Long customerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        Workbook workbook = excelGenerator.generateStatement(startDate, endDate);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment",
            "statement_" + customerId + "_" + startDate + "_" + endDate + ".xlsx");

        return ResponseEntity.ok()
            .headers(headers)
            .body(out.toByteArray());
    }

    /**
     * Generate all statements (for admin/bulk download)
     */
    @GetMapping("/all/excel")
    public ResponseEntity<byte[]> generateAllStatements(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        // Same logic, includes all leases
        Workbook workbook = excelGenerator.generateStatement(startDate, endDate);

        // ... return as download
    }
}
```

---

## Advantages Over Existing System

### Current System (XLSXStatementService)
- ❌ NO invoice linking (uses property-only matching)
- ❌ Hardcoded 15% commission (can't change without code)
- ❌ Java calculates everything (black box)
- ❌ Complex 1,984-line monolith
- ❌ No pro-rating formulas visible

### Option C (This Design)
- ✅ 100% invoice linking (invoice_id mandatory)
- ✅ Formulas in Excel (transparent, auditable)
- ✅ Easy to adjust commission % without code changes
- ✅ Matches user's manual process
- ✅ Pro-rating logic visible in formulas
- ✅ Simple Java service (extract only)
- ✅ Users can verify/modify calculations

---

## Pro-Rating Example

### Scenario: Lease from March 15 to August 20
**Monthly rent**: £800

| Month | Days in Month | Lease Days | Pro-Rated Rent Due | Formula |
|-------|---------------|------------|-------------------|---------|
| March 2025 | 31 | 17 (15-31) | £438.71 | =(17/31) * 800 |
| April 2025 | 30 | 30 (1-30) | £800.00 | =(30/30) * 800 |
| May 2025 | 31 | 31 (1-31) | £800.00 | =(31/31) * 800 |
| June 2025 | 30 | 30 (1-30) | £800.00 | =(30/30) * 800 |
| July 2025 | 31 | 31 (1-31) | £800.00 | =(31/31) * 800 |
| August 2025 | 31 | 20 (1-20) | £516.13 | =(20/31) * 800 |

**Total**: £4,354.84 (for 5 months 5 days)

**Excel Formula**:
```excel
// Lease days in month
=MAX(0, MIN($lease_end, EOMONTH(month_start, 0)) - MAX($lease_start, month_start) + 1)

// Pro-rated rent
=(lease_days / DAY(EOMONTH(month_start, 0))) * $monthly_rent
```

---

## Commission Calculation Example

### Scenario: £800 rent received

| Fee Type | Percentage | Calculation | Amount |
|----------|------------|-------------|--------|
| Management Fee | 10% | =800 * 0.10 | £80.00 |
| Service Fee | 5% | =800 * 0.05 | £40.00 |
| **Total Commission** | **15%** | =80 + 40 | **£120.00** |
| **Net to Owner** | **85%** | =800 - 120 | **£680.00** |

**Excel Formulas**:
```excel
// Management fee
=rent_received * 0.10

// Service fee
=rent_received * 0.05

// Total commission
=management_fee + service_fee

// Net to owner
=rent_received - total_commission
```

---

## Arrears Calculation Example

### Scenario: Tenant pays late

| Month | Rent Due | Rent Received | Arrears (Month) | Cumulative Arrears |
|-------|----------|---------------|----------------|-------------------|
| March | £800 | £800 | £0 | £0 |
| April | £800 | £500 | £300 | £300 |
| May | £800 | £600 | £200 | £500 |
| June | £800 | £1,300 | -£500 | £0 |

**Excel Formulas**:
```excel
// Monthly arrears
=rent_due - rent_received

// Cumulative arrears (running total)
=SUM($arrears$2:arrears_current_row)
```

---

## Implementation Plan

### Phase 1: Data Extraction ✅ (READY)
- ✅ Database has invoice_id populated (100% of rent payments)
- ✅ All necessary data available (leases, transactions, properties, customers)

### Phase 2: Java Service Implementation
1. Create `StatementDataExtractService`
   - Method: `extractLeaseMaster()`
   - Method: `extractTransactions(startDate, endDate)`
   - Method: `extractProperties()`
   - Method: `extractCustomers()`

2. Create DTOs
   - `LeaseMasterDTO`
   - `TransactionDTO`
   - `PropertyDTO`
   - `CustomerDTO`

3. Create `ExcelStatementGeneratorService`
   - Method: `generateStatement(startDate, endDate)`
   - Method: `createLeaseMasterSheet()`
   - Method: `createTransactionsSheet()`
   - Method: `createRentDueSheet()` (with formulas)
   - Method: `createRentReceivedSheet()` (with formulas)
   - Method: `createMonthlyStatementSheet()` (with formulas)

4. Create `StatementController`
   - Endpoint: `GET /api/statements/owner/{customerId}/excel`
   - Endpoint: `GET /api/statements/all/excel`

### Phase 3: Testing
1. Unit tests for data extraction
2. Integration tests for Excel generation
3. Manual verification: Open Excel, verify formulas work
4. Compare with user's manual spreadsheet

### Phase 4: Frontend Integration (Future)
1. Add "Download Statement" button in UI
2. Date range picker for statement period
3. Download Excel file

---

## Files to Create

### Java Files
1. `StatementDataExtractService.java` - Data extraction service
2. `ExcelStatementGeneratorService.java` - Excel generation with formulas
3. `StatementController.java` - REST API endpoint
4. `LeaseMasterDTO.java` - Lease master data object
5. `TransactionDTO.java` - Transaction data object
6. `PropertyDTO.java` - Property data object
7. `CustomerDTO.java` - Customer data object

### Test Files
1. `StatementDataExtractServiceTest.java`
2. `ExcelStatementGeneratorServiceTest.java`
3. `StatementControllerTest.java`

---

## Success Criteria

### Functional
- ✅ Excel file downloads successfully
- ✅ All formulas work (no #REF!, #VALUE! errors)
- ✅ Pro-rating calculations match user's manual approach
- ✅ Commission calculations correct (10% + 5% = 15%)
- ✅ Arrears calculations accurate
- ✅ 100% of transactions linked to leases

### Non-Functional
- ✅ Transparent (users can see/audit formulas)
- ✅ Flexible (easy to change commission % in Excel)
- ✅ Fast (data extract + formula write, no heavy calculations in Java)
- ✅ Maintainable (simple service, formulas in Excel)

---

## Next Steps

1. **Implement StatementDataExtractService** - Extract raw data from database
2. **Create DTOs** - Data transfer objects for lease, transaction, property, customer
3. **Implement ExcelStatementGeneratorService** - Generate Excel with formulas
4. **Create StatementController** - REST endpoint for downloading statements
5. **Test with real data** - Generate statement, verify formulas work

---

## Notes

- **Commission Rates**: Currently hardcoded as 10% + 5%. Future: Store in database per property/lease
- **Invoice Linking**: 100% complete (135/135 rent payments linked)
- **Date Constraints**: Removed (handles deposits, early/late payments)
- **Most Recent Lease**: Logic implemented for multiple leases per property + customer
- **Backward Compatible**: Old import services still work, use deprecated methods

---

**Status**: ✅ Design Complete - Ready for Implementation
**Next**: Implement StatementDataExtractService

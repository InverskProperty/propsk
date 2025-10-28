# Monthly Statement Generation - Data Extract + Formula Approach

**Purpose:** Extract raw data from database, populate spreadsheet, use Excel formulas for all calculations

---

## Approach Overview

### What We Extract (SQL)
1. Lease master data
2. Transaction list (payments)
3. Expense list
4. Property/customer reference data

### What Excel Does (Formulas)
1. Pro-rate rent for partial periods
2. Match payments to leases
3. Calculate management fees
4. Sum expenses
5. Calculate net to owner and arrears

---

## Required Data Sheets in Workbook

### Sheet 1: "Lease Master" (Source Data)
Raw lease data from database

### Sheet 2: "Transactions" (Source Data)
All transactions for the period

### Sheet 3: "Expenses" (Source Data)
All expenses for the period

### Sheet 4: "Properties" (Lookup Data)
Property reference info

### Sheet 5: "Leases" (Current manual sheet)
Your current lease listing - populated from Sheet 1

### Sheet 6+: "Monthly Statement - [Property]"
Owner statements with formulas referencing data sheets

---

## SHEET 1: Lease Master (Data Extract)

### SQL Query
```sql
SELECT
    i.id AS invoice_id,
    i.lease_reference,
    i.customer_id,
    c.name AS tenant_name,
    i.property_id,
    p.name AS property_name,
    p.block_id,
    b.name AS block_name,
    i.start_date,
    i.end_date,
    i.payment_day,
    i.amount AS monthly_rent,
    COALESCE(i.category_id, 'rent') AS category_id,
    COALESCE(i.category_name, 'Rent') AS category_name,
    i.is_active,
    i.frequency,
    -- Get commission rate from invoice or property settings
    COALESCE(
        i.commission_rate,
        ps.default_commission_rate,
        0.15
    ) AS management_fee_pct
FROM invoices i
LEFT JOIN customers c ON i.customer_id = c.id
LEFT JOIN properties p ON i.property_id = p.id
LEFT JOIN blocks b ON p.block_id = b.id
LEFT JOIN property_settings ps ON p.id = ps.property_id
WHERE i.deleted_at IS NULL
ORDER BY p.name, i.lease_reference;
```

### Spreadsheet Columns (Sheet1: "Lease Master")
| Col | Field | Type | Notes |
|-----|-------|------|-------|
| A | invoice_id | Number | Primary key for lookups |
| B | lease_reference | Text | LEASE-BH-F1-2025 |
| C | customer_id | Number | For lookups |
| D | tenant_name | Text | Display name |
| E | property_id | Number | For filtering |
| F | property_name | Text | Unit name |
| G | block_id | Number | For grouping |
| H | block_name | Text | BODEN HOUSE |
| I | start_date | Date | Lease start |
| J | end_date | Date | Lease end (blank if ongoing) |
| K | payment_day | Number | 1-31 |
| L | monthly_rent | Currency | Base monthly rent |
| M | category_id | Text | Rent category |
| N | category_name | Text | Category display |
| O | is_active | Boolean | TRUE/FALSE |
| P | frequency | Text | monthly, quarterly, etc |
| Q | management_fee_pct | Decimal | 0.15 = 15% |

---

## SHEET 2: Transactions (Data Extract)

### SQL Query
```sql
SELECT
    ht.id AS transaction_id,
    ht.invoice_id,
    ht.property_id,
    ht.customer_id,
    ht.transaction_date,
    ht.amount,
    ht.transaction_type,
    ht.category,
    ht.description,
    ht.payment_method,
    ht.bank_reference,
    -- Add property/tenant info for matching if invoice_id is null
    p.name AS property_name,
    c.name AS customer_name
FROM historical_transactions ht
LEFT JOIN properties p ON ht.property_id = p.id
LEFT JOIN customers c ON ht.customer_id = c.id
WHERE ht.transaction_date BETWEEN @period_start AND @period_end
  AND ht.transaction_type IN ('payment', 'invoice')
  AND ht.amount > 0
ORDER BY ht.transaction_date, ht.id;
```

### Alternative: Include PayProp Incoming Payments
```sql
SELECT
    pip.id AS transaction_id,
    NULL AS invoice_id,  -- Will match via property
    NULL AS property_id,
    NULL AS customer_id,
    pip.reconciliation_date AS transaction_date,
    pip.amount,
    'payment' AS transaction_type,
    'Rent Payment' AS category,
    CONCAT('Payment from ', pip.tenant_name) AS description,
    'Bank Transfer' AS payment_method,
    pip.incoming_transaction_id AS bank_reference,
    pip.property_name,
    pip.tenant_name AS customer_name
FROM payprop_incoming_payments pip
WHERE pip.reconciliation_date BETWEEN @period_start AND @period_end
  AND pip.synced_to_historical = FALSE
ORDER BY pip.reconciliation_date;
```

### Spreadsheet Columns (Sheet2: "Transactions")
| Col | Field | Type | Notes |
|-----|-------|------|-------|
| A | transaction_id | Number | Unique ID |
| B | invoice_id | Number | Link to lease (may be null) |
| C | property_id | Number | For matching |
| D | customer_id | Number | For matching |
| E | transaction_date | Date | Payment date |
| F | amount | Currency | Payment amount |
| G | transaction_type | Text | payment, invoice |
| H | category | Text | Category |
| I | description | Text | Details |
| J | payment_method | Text | How paid |
| K | bank_reference | Text | Bank ref |
| L | property_name | Text | For matching if invoice_id null |
| M | customer_name | Text | For matching if invoice_id null |

---

## SHEET 3: Expenses (Data Extract)

### SQL Query
```sql
SELECT
    ht.id AS expense_id,
    ht.invoice_id,
    ht.property_id,
    ht.customer_id,
    ht.transaction_date,
    ABS(ht.amount) AS amount,  -- Make positive
    ht.transaction_type,
    ht.category,
    ht.subcategory,
    ht.description,
    ht.beneficiary_name,
    p.name AS property_name,
    c.name AS customer_name
FROM historical_transactions ht
LEFT JOIN properties p ON ht.property_id = p.id
LEFT JOIN customers c ON ht.customer_id = c.id
WHERE ht.transaction_date BETWEEN @period_start AND @period_end
  AND ht.transaction_type IN ('expense', 'maintenance', 'fee', 'withdrawal')
  AND ht.amount < 0  -- Expenses are negative
ORDER BY ht.transaction_date, ht.id;
```

### Spreadsheet Columns (Sheet3: "Expenses")
| Col | Field | Type | Notes |
|-----|-------|------|-------|
| A | expense_id | Number | Unique ID |
| B | invoice_id | Number | Link to lease (may be null) |
| C | property_id | Number | For matching |
| D | customer_id | Number | For matching |
| E | transaction_date | Date | Expense date |
| F | amount | Currency | Positive amount |
| G | transaction_type | Text | expense, maintenance, etc |
| H | category | Text | Expense category |
| I | subcategory | Text | Sub-category |
| J | description | Text | Details |
| K | beneficiary_name | Text | Who was paid |
| L | property_name | Text | For matching |
| M | customer_name | Text | For matching |

---

## SHEET 4: Properties (Lookup Data)

### SQL Query
```sql
SELECT
    p.id AS property_id,
    p.name AS property_name,
    p.block_id,
    b.name AS block_name,
    po.id AS owner_id,
    po.name AS owner_name,
    po.email AS owner_email
FROM properties p
LEFT JOIN blocks b ON p.block_id = b.id
LEFT JOIN property_owners po ON p.owner_id = po.id
WHERE p.is_active = TRUE
ORDER BY b.name, p.name;
```

### Spreadsheet Columns (Sheet4: "Properties")
| Col | Field | Type |
|-----|-------|------|
| A | property_id | Number |
| B | property_name | Text |
| C | block_id | Number |
| D | block_name | Text |
| E | owner_id | Number |
| F | owner_name | Text |
| G | owner_email | Text |

---

## SHEET 5: "Leases" (Your Current Lease Page)

This is populated using formulas from Sheet1 data.

### Formula-Based Columns

**Assuming your current sheet structure:**

| Col | Header | Formula | Notes |
|-----|--------|---------|-------|
| A | Lease Reference | `='Lease Master'!B2` | Direct reference |
| B | Unit Number | `='Lease Master'!F2` | property_name |
| C | Tenant Name(s) | `='Lease Master'!D2` | tenant_name |
| D | Lease Start Date | `='Lease Master'!I2` | start_date |
| E | Lease End Date | `=IF('Lease Master'!J2="","",'Lease Master'!J2)` | end_date |
| F | Payment Day | `='Lease Master'!K2` | payment_day |
| G | Monthly Rent | `='Lease Master'!L2` | monthly_rent |
| H | Management Fee % | `='Lease Master'!Q2` | management_fee_pct |

**Filter for active leases:**
```
Use Excel Table with filter or array formula:
=FILTER('Lease Master'!A:Q, 'Lease Master'!O:O=TRUE)
```

---

## SHEET 6+: Monthly Statement (Formula-Based)

### Statement Header (Manual Entry or Parameters)
```
Cell B3: CLIENT:     [Owner Name - manual or from dropdown]
Cell B8: Date BOP:   [Period Start - manual entry]
Cell B9: Date EOP:   [Period End - manual entry]
Cell B12: PROPERTY:  [Property Name - manual or from dropdown]
```

### Data Row Formulas (Starting Row 14)

#### Columns A-F: Lease Info (VLOOKUP from Lease Master)

| Col | Header | Formula | Notes |
|-----|--------|---------|-------|
| A | Lease Reference | `='Lease Master'!B14` | Or FILTER by property |
| B | Unit No. | `='Lease Master'!F14` | |
| C | Tenant | `='Lease Master'!D14` | |
| D | Tenancy Start Date | `='Lease Master'!I14` | |
| E | Tenancy End Date | `=IF('Lease Master'!J14="","",'Lease Master'!J14)` | |
| F | Rent Due Date | `=IF(AND(E14<=$B$9, OR(F14="", F14>=$B$8)), DAY(E14), "")` | Only if active |

#### Column G: Rent Due Amount (PRO-RATED) - Your Current Complex Formula

```excel
=IF(OR(E14="", E14=""), 0,
   IF(AND(E14<=$B$9, OR(F14="", F14>=$B$8)),
      IF(AND(F14<>"", F14<=IF(DAY(E14)>=DAY($B$8), DATE(YEAR($B$8), MONTH($B$8), DAY(E14)), DATE(YEAR($B$9), MONTH($B$9), DAY(E14)))),
         0,
         IF(F14="",
            'Lease Master'!L14,
            MIN('Lease Master'!L14,
               ROUND('Lease Master'!L14 *
                  IF(F14<EDATE(IF(DAY(E14)>=DAY($B$8), DATE(YEAR($B$8), MONTH($B$8), DAY(E14)), DATE(YEAR($B$9), MONTH($B$9), DAY(E14))), 1),
                     (DATEDIF(IF(DAY(E14)>=DAY($B$8), DATE(YEAR($B$8), MONTH($B$8), DAY(E14)), DATE(YEAR($B$9), MONTH($B$9), DAY(E14))), F14, "D") + 1),
                     30
                  ) / 30, 2)
            )
         )
      ),
      0)
)
```

**Or simpler version:**
```excel
=IF(
  AND('Lease Master'!I14<=$B$9, OR('Lease Master'!J14="", 'Lease Master'!J14>=$B$8)),
  IF(
    'Lease Master'!J14<>"",
    'Lease Master'!L14 * (DATEDIF($B$8, 'Lease Master'!J14, "D")+1) / 30,
    'Lease Master'!L14
  ),
  0
)
```

#### Columns H-K: Rent Received (LOOKUP from Transactions)

**First Payment:**
```excel
# Column H: Rent Received 1 Date
=IFERROR(
  INDEX(Transactions!$E:$E,
    SMALL(
      IF(Transactions!$B:$B='Lease Master'!$A14, ROW(Transactions!$E:$E)),
      1
    )
  ),
  ""
)

# Column I: Rent Received 1 Amount
=IFERROR(INDEX(Transactions!$F:$F, MATCH(H14, Transactions!$E:$E, 0)), 0)
```

**Second Payment:**
```excel
# Column J: Rent Received 2 Date
=IFERROR(
  INDEX(Transactions!$E:$E,
    SMALL(
      IF(Transactions!$B:$B='Lease Master'!$A14, ROW(Transactions!$E:$E)),
      2
    )
  ),
  ""
)

# Column K: Rent Received 2 Amount
=IFERROR(INDEX(Transactions!$F:$F, MATCH(J14, Transactions!$E:$E, 0)), 0)
```

**Or using SUMIFS (if you just want total):**
```excel
# Column L: Rent Received Total
=SUMIFS(Transactions!$F:$F,
        Transactions!$B:$B, 'Lease Master'!$A14,
        Transactions!$E:$E, ">="&$B$8,
        Transactions!$E:$E, "<="&$B$9)
```

#### Columns M-N: Management Fee

```excel
# Column M: Management Fee (%) - Display
='Lease Master'!Q14

# Column N: Management Fee Amount
=IFERROR(L14 * M14, 0)
```

#### Columns O-W: Expenses (LOOKUP from Expenses)

**Expense 1:**
```excel
# Column O: Expense 1 Label
=IFERROR(
  INDEX(Expenses!$H:$H,
    SMALL(
      IF(Expenses!$B:$B='Lease Master'!$A14, ROW(Expenses!$H:$H)),
      1
    )
  ),
  ""
)

# Column P: Expense 1 Amount
=IFERROR(
  INDEX(Expenses!$F:$F,
    SMALL(
      IF(Expenses!$B:$B='Lease Master'!$A14, ROW(Expenses!$F:$F)),
      1
    )
  ),
  0
)
```

**Repeat pattern for Expenses 2, 3, 4...**

**Or Total Expenses:**
```excel
# Column W: Total Expenses
=SUMIFS(Expenses!$F:$F,
        Expenses!$B:$B, 'Lease Master'!$A14,
        Expenses!$E:$E, ">="&$B$8,
        Expenses!$E:$E, "<="&$B$9)
```

#### Columns X-Z: Calculated Totals

```excel
# Column X: Net Due to Owner
=L14 - N14 - W14

# Column Y: Rent Due Less Received Total
=G14 - L14

# Column Z: Tenant Balance (would need additional sheet with running balances)
=SUMIFS(Transactions!$F:$F, Transactions!$B:$B, 'Lease Master'!$A14, Transactions!$E:$E, "<="&$B$9) -
 SUMIFS(Expenses!$F:$F, Expenses!$B:$B, 'Lease Master'!$A14, Expenses!$E:$E, "<="&$B$9)
```

### TOTAL Row (Row 62 or after last lease)
```excel
# Column G: Total Rent Due
=SUM(G14:G61)

# Column L: Total Rent Received
=SUM(L14:L61)

# Column N: Total Management Fees
=SUM(N14:N61)

# Column W: Total Expenses
=SUM(W14:W61)

# Column X: Total Net to Owner
=SUM(X14:X61)

# Column Y: Total Shortfall
=SUM(Y14:Y61)
```

---

## Implementation Steps

### 1. Database Query Service (Java/Spring)

Create a service that generates the data extracts:

```java
@Service
public class StatementDataExtractService {

    public StatementDataExtract generateExtract(
        Long propertyId,
        LocalDate periodStart,
        LocalDate periodEnd
    ) {
        StatementDataExtract extract = new StatementDataExtract();

        extract.setLeases(getLeaseMasterData(propertyId));
        extract.setTransactions(getTransactions(propertyId, periodStart, periodEnd));
        extract.setExpenses(getExpenses(propertyId, periodStart, periodEnd));
        extract.setProperties(getPropertyLookupData());

        return extract;
    }

    private List<LeaseMasterRow> getLeaseMasterData(Long propertyId) {
        // Execute SQL query for Lease Master sheet
        // Return list of DTOs
    }

    private List<TransactionRow> getTransactions(
        Long propertyId,
        LocalDate start,
        LocalDate end
    ) {
        // Execute SQL query for Transactions sheet
    }

    private List<ExpenseRow> getExpenses(
        Long propertyId,
        LocalDate start,
        LocalDate end
    ) {
        // Execute SQL query for Expenses sheet
    }
}
```

### 2. Excel Generation Service (Apache POI)

```java
@Service
public class StatementExcelGeneratorService {

    public Workbook generateStatementWorkbook(StatementDataExtract data) {
        Workbook workbook = new XSSFWorkbook();

        // Create data sheets
        createLeaseMasterSheet(workbook, data.getLeases());
        createTransactionsSheet(workbook, data.getTransactions());
        createExpensesSheet(workbook, data.getExpenses());
        createPropertiesSheet(workbook, data.getProperties());

        // Create formula-based sheets
        createLeaseListingSheet(workbook);
        createMonthlyStatementSheet(workbook);

        return workbook;
    }

    private void createMonthlyStatementSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Monthly Statement");

        // Create header rows
        createStatementHeader(sheet);

        // Create data rows with formulas
        int rowNum = 13;  // Start at row 14 (0-indexed = 13)

        for (int i = 0; i < MAX_LEASES; i++) {
            Row row = sheet.createRow(rowNum++);

            // Column A: Lease Reference (from Lease Master)
            Cell cellA = row.createCell(0);
            cellA.setCellFormula("'Lease Master'!B" + (i + 2));

            // Column G: Rent Due Amount (complex pro-rating formula)
            Cell cellG = row.createCell(6);
            cellG.setCellFormula(buildRentDueFormula(i + 2));

            // Column L: Rent Received Total
            Cell cellL = row.createCell(11);
            cellL.setCellFormula(
                "SUMIFS(Transactions!$F:$F, " +
                "Transactions!$B:$B, 'Lease Master'!$A" + (i+2) + ", " +
                "Transactions!$E:$E, \">=\"&$B$8, " +
                "Transactions!$E:$E, \"<=\"&$B$9)"
            );

            // ... etc for all columns
        }

        // Create TOTAL row
        createTotalRow(sheet, rowNum);
    }

    private String buildRentDueFormula(int leaseRow) {
        // Build the complex pro-rating formula
        return "IF(AND('Lease Master'!I" + leaseRow + "<=$B$9, " +
               "OR('Lease Master'!J" + leaseRow + "=\"\", " +
               "'Lease Master'!J" + leaseRow + ">=$B$8)), " +
               "'Lease Master'!L" + leaseRow + ", 0)";
    }
}
```

### 3. REST API Endpoint

```java
@RestController
@RequestMapping("/api/statements")
public class StatementController {

    @Autowired
    private StatementDataExtractService extractService;

    @Autowired
    private StatementExcelGeneratorService excelService;

    @GetMapping("/generate")
    public ResponseEntity<byte[]> generateStatement(
        @RequestParam(required = false) Long propertyId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd
    ) throws IOException {

        // Extract data
        StatementDataExtract data = extractService.generateExtract(
            propertyId, periodStart, periodEnd
        );

        // Generate Excel
        Workbook workbook = excelService.generateStatementWorkbook(data);

        // Convert to byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData(
            "attachment",
            "statement_" + periodStart + "_" + periodEnd + ".xlsx"
        );

        return new ResponseEntity<>(
            outputStream.toByteArray(),
            headers,
            HttpStatus.OK
        );
    }
}
```

---

## Key Advantages of This Approach

### 1. **Transparency**
- All calculations visible in Excel formulas
- Clients can verify calculations
- Easy to audit

### 2. **Flexibility**
- Formula tweaks don't require code changes
- Users can add custom calculations
- Can copy/paste formulas to new sheets

### 3. **Familiarity**
- Matches current manual process
- No learning curve for users
- Can still manually override if needed

### 4. **Performance**
- SQL only extracts raw data (fast)
- Excel handles calculations (client-side)
- No complex SQL joins for calculations

### 5. **Debugging**
- Easy to trace formula errors
- Can step through calculations
- Can validate intermediate results

---

## Data Quality Requirements

For this to work properly, you need:

### 1. **invoice_id populated in transactions**
```sql
UPDATE historical_transactions ht
SET invoice_id = (
    SELECT i.id
    FROM invoices i
    WHERE i.property_id = ht.property_id
      AND i.customer_id = ht.customer_id
      AND ht.transaction_date BETWEEN i.start_date AND COALESCE(i.end_date, '2099-12-31')
    LIMIT 1
)
WHERE ht.invoice_id IS NULL
  AND ht.transaction_type IN ('payment', 'expense', 'maintenance', 'fee');
```

### 2. **Commission rates defined**
Either in invoices table or property settings

### 3. **Clean property/customer data**
No orphaned transactions without property or customer links

---

## Next Steps

1. **Validate data coverage** - Check how many transactions have invoice_id
2. **Build data extract queries** - Test SQL queries for each sheet
3. **Create Excel template** - Set up formulas in template
4. **Build Java service** - Implement data extract and Excel generation
5. **Test with real data** - Generate statement for one property
6. **Refine formulas** - Adjust calculations as needed

Would you like me to:
- **Check your data coverage first?** (Run queries to see completeness)
- **Build the backfill script?** (Populate missing invoice_id)
- **Create the Excel template?** (With all the formulas)
- **Start on the Java service?** (Data extract implementation)

Which makes most sense to tackle first?

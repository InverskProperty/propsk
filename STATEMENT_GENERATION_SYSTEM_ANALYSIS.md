# Statement Generation System - Comprehensive Analysis

**Date**: 2025-10-28
**Purpose**: Document existing statement generation services for future improvements

---

## 📋 Table of Contents

1. [System Overview](#system-overview)
2. [Architecture](#architecture)
3. [Data Sources](#data-sources)
4. [Statement Types](#statement-types)
5. [Data Extraction](#data-extraction)
6. [Calculation Logic](#calculation-logic)
7. [Output Formats](#output-formats)
8. [Current Issues](#current-issues)
9. [Connection to Invoice Linking](#connection-to-invoice-linking)
10. [Improvement Opportunities](#improvement-opportunities)

---

## System Overview

### Two Main Statement Generation Paths

#### 1. **XLSX Generation** (Downloadable Excel)
- **Service**: `XLSXStatementService.java` (1,984 lines)
- **Location**: `src/main/java/site/easy/to/build/crm/service/statements/XLSXStatementService.java`
- **Technology**: Apache POI
- **Output**: Binary `.xlsx` file download
- **Features**:
  - Property owner statements
  - Portfolio statements (multiple properties)
  - Tenant statements
  - Monthly breakdown with multiple sheets
  - Boden House template formatting

#### 2. **Google Sheets Generation** (Cloud-based)
- **Service**: `GoogleSheetsStatementService.java` (2,021 lines)
- **Location**: `src/main/java/site/easy/to/build/crm/service/sheets/GoogleSheetsStatementService.java`
- **Technology**: Google Sheets API
- **Authentication**: OAuth2 or Service Account
- **Output**: Shareable Google Sheets URL
- **Features**:
  - Same data as XLSX
  - Cloud-hosted on Google Shared Drive
  - Real-time collaboration
  - Service account fallback

---

## Architecture

### Controllers

#### **StatementController.java** (1,219 lines)
**Location**: `src/main/java/site/easy/to/build/crm/controller/StatementController.java`

**Key Endpoints**:
```
GET  /statements                                 - Show statement generation page
POST /statements/property-owner                  - Generate Google Sheets (property owner)
POST /statements/property-owner/xlsx             - Generate XLSX download (property owner)
POST /statements/tenant                          - Generate tenant statement
POST /statements/tenant/xlsx                     - Tenant XLSX download
POST /statements/portfolio                       - Generate portfolio statement
POST /statements/portfolio/xlsx                  - Portfolio XLSX download
POST /statements/property-owner/lease-based      - Lease-centric statements
POST /statements/property-owner/service-account  - Service account fallback
GET  /statements/api/account-sources             - List available data sources
GET  /statements/debug-auth                      - Debug authentication
```

#### **EnhancedStatementController.java** (253 lines)
**Location**: `src/main/java/site/easy/to/build/crm/controller/EnhancedStatementController.java`

**Purpose**: Admin-level statement generation with data source selection

**Key Endpoints**:
```
GET  /admin/enhanced-statements                      - Show enhanced form
POST /admin/enhanced-statements/property-owner/xlsx  - XLSX with source selection
POST /admin/enhanced-statements/property-owner/google-sheets - Google Sheets with sources
POST /admin/enhanced-statements/portfolio/xlsx       - Portfolio with sources
```

### Services

| Service | Lines | Purpose | Output |
|---------|-------|---------|--------|
| XLSXStatementService | 1,984 | Excel generation | `.xlsx` file |
| GoogleSheetsStatementService | 2,021 | Google Sheets generation | Shareable URL |
| BodenHouseStatementTemplateService | 200+ | Template formatting | Boden House format |
| LeaseStatementService | 150+ | Lease-centric statements | Lease-based rows |

### DTOs

#### **StatementGenerationRequest.java**
```java
Fields:
- propertyOwnerId: Long
- fromDate: LocalDate
- toDate: LocalDate
- includedDataSources: Set<StatementDataSource>
- statementType: String ("PROPERTY_OWNER", "PORTFOLIO", "TENANT")
- outputFormat: String ("XLSX", "GOOGLE_SHEETS", "PDF")
- includeExpenses: boolean
- includeFormulas: boolean
- notes: String

Methods:
- includesDataSource(StatementDataSource)
- includesHistoricalData()
- includesLiveData()
```

#### **LeaseStatementRow.java**
```java
Fields:
- leaseId, leaseReference
- unitNumber, propertyName
- tenantName, tenantId
- tenancyStartDate, tenancyEndDate, rentDueDay
- rentDueAmount, rentReceivedAmount
- paymentDates
- commissionCalculations
```

---

## Data Sources

### Enum: StatementDataSource.java
**Location**: `src/main/java/site/easy/to/build/crm/enums/StatementDataSource.java`

**Three Available Sources**:

```java
UNIFIED("unified", "Unified (Historical + PayProp)"),
    // Combines historical transactions + PayProp data (last 2 years)
    // Recommended for complete view
    // Account Source Value: "unified"

HISTORICAL("historical", "Historical Transactions Only"),
    // Pre-PayProp era transactions only
    // Used for legacy data
    // Account Source Value: "historical"

PAYPROP("payprop", "PayProp Transactions Only")
    // Current PayProp-synced transactions only
    // Live data from PayProp API
    // Account Source Value: "payprop"
```

### Data Source Selection Flow

```
User selects data sources in UI
    ↓
Controller receives Set<StatementDataSource>
    ↓
Service filters queries based on sources:
    - UNIFIED    → Query both repositories
    - HISTORICAL → Query HistoricalTransactionRepository only
    - PAYPROP    → Query FinancialTransactionRepository only
```

---

## Statement Types

### 1. Property Owner Statement
**Most Common**

**Shows**:
- All properties owned by a customer
- Rent due vs rent received per property
- Expenses per property
- Management & service fees
- Net amount due to owner
- Outstanding balances

**Grouping**: By property
**Period**: User-specified date range

### 2. Portfolio Statement
**Multiple Owners**

**Shows**:
- All properties in a portfolio
- Aggregated financials across properties
- Same calculations as property owner statement
- Summary totals

**Grouping**: By portfolio, then property
**Period**: User-specified date range

### 3. Tenant Statement
**For Tenants**

**Shows**:
- Rent due
- Payments made
- Outstanding balance
- Payment history

**Grouping**: By lease/tenancy
**Period**: User-specified date range

### 4. Lease-Based Statement
**Lease-Centric View**

**Shows**:
- One row per lease/invoice (not per property)
- Useful when property has multiple sequential tenants
- Lease reference numbers
- Tenant-specific financials

**Grouping**: By lease/invoice
**Period**: User-specified date range

---

## Data Extraction

### Primary Repositories

#### **FinancialTransactionRepository.java** (648 lines)
**Purpose**: Query PayProp and unified transaction data

**Key Methods**:
```java
// Statement-specific queries
List<FinancialTransaction> findPropertyTransactionsForStatement(
    String propertyId, LocalDate fromDate, LocalDate toDate)

// Aggregation queries
BigDecimal sumExpensesForProperty(
    String propertyId, LocalDate fromDate, LocalDate toDate)

BigDecimal sumPaymentsByTenant(
    String tenantId, LocalDate fromDate, LocalDate toDate)

BigDecimal sumOutstandingForProperty(
    String propertyId, LocalDate fromDate, LocalDate toDate)

// Payment date tracking
LocalDate findLatestPaymentDateForProperty(
    String propertyId, LocalDate fromDate, LocalDate toDate)

// Owner financial summary
Object[] getPropertyOwnerFinancialSummary(Long customerId)
List<Object[]> getPropertyOwnerPropertyBreakdown(Long customerId)

// Portfolio queries
Object[] getPortfolioFinancialSummary(Long portfolioId)
List<Object[]> getPortfolioPropertyBreakdown(Long portfolioId)
```

#### **HistoricalTransactionRepository.java**
**Purpose**: Query pre-PayProp historical data

**Key Methods**:
```java
List<HistoricalTransaction> findByPropertyAndDateRange(
    Property property, LocalDate fromDate, LocalDate toDate)

List<HistoricalTransaction> findByCustomerAndDateRange(
    Customer customer, LocalDate fromDate, LocalDate toDate)
```

#### **InvoiceRepository.java**
**Purpose**: Query lease/rental agreement data

**Key Methods**:
```java
List<Invoice> findByPropertyAndDateRangeOverlap(
    Property property, LocalDate periodStart, LocalDate periodEnd)

List<Invoice> findActiveInvoicesForProperty(
    Property property, LocalDate today)
```

### Data Extraction Pattern

**Example from XLSXStatementService (lines 1139-1150)**:
```java
// 1. Get all transactions for property in date range
List<FinancialTransaction> transactions =
    financialTransactionRepository.findPropertyTransactionsForStatement(
        property.getPayPropId(), fromDate, toDate);

// 2. Extract rent received
BigDecimal rentReceived = transactions.stream()
    .filter(t -> "invoice".equals(t.getTransactionType()) ||
                 "rent".equalsIgnoreCase(t.getCategoryName()))
    .map(FinancialTransaction::getAmount)
    .reduce(BigDecimal.ZERO, BigDecimal::add);

// 3. Extract expenses
BigDecimal totalExpenses = transactions.stream()
    .filter(t -> isExpenseTransaction(t))
    .map(FinancialTransaction::getAmount)
    .reduce(BigDecimal.ZERO, BigDecimal::add);

// 4. Calculate net to owner
BigDecimal netToOwner = rentReceived
    .add(managementFee)
    .add(serviceFee)
    .add(totalExpenses);
```

---

## Calculation Logic

### Commission Calculations

**HARDCODED RATES** (XLSXStatementService lines 1144-1149):
```java
// Management Fee: 10% of rent received
BigDecimal managementFee = rentReceived.multiply(new BigDecimal("-0.10"));

// Service Fee: 5% of rent received
BigDecimal serviceFee = rentReceived.multiply(new BigDecimal("-0.05"));

// Total commission: 15%
BigDecimal totalFees = managementFee.add(serviceFee);
```

**Excel Formula Approach**:
```
Management Fee (£) = Rent Received × -10%
  Excel: =J{row}*-0.1

Service Fee (£)    = Rent Received × -5%
  Excel: =J{row}*-0.05

Total Fees         = Management + Service
  Excel: =O{row}+Q{row}

Net Due to Owner   = Rent Received + Total Fees + Total Expenses
  Excel: =J{row}+R{row}+AE{row}
```

### Rent Calculations

```java
// Rent Due (from property master data)
BigDecimal rentDue = property.getMonthlyPayment();

// Rent Received (from transactions)
BigDecimal rentReceived = transactions.stream()
    .filter(t -> "invoice".equals(t.getTransactionType()) ||
                 "rent".equalsIgnoreCase(t.getCategoryName()))
    .map(FinancialTransaction::getAmount)
    .reduce(BigDecimal.ZERO, BigDecimal::add);

// Outstanding Balance
BigDecimal outstanding = rentDue.subtract(rentReceived);
```

**Excel Formula**:
```
Outstanding = Rent Due - Rent Received
Excel: =E{row}-J{row}
```

### Expense Handling

**Category-Based Detection** (XLSXStatementService lines 1236-1261):
```java
private boolean isExpenseTransaction(FinancialTransaction t) {
    String category = t.getCategoryName();
    if (category == null) return false;

    String categoryLower = category.toLowerCase();
    return categoryLower.contains("maintenance") ||
           categoryLower.contains("repair") ||
           categoryLower.contains("clean") ||
           categoryLower.contains("service") ||
           categoryLower.contains("utilities");
}
```

**Transaction Type Based**:
```java
String transactionType = t.getTransactionType();
return "payment_to_contractor".equals(transactionType) ||
       "debit_note".equals(transactionType) ||
       "adjustment".equals(transactionType);
```

**Limitation**: Only first 4 expenses per property shown in statement

### Payment Routing Detection

**Three Routing Channels** (XLSXStatementService lines 1196-1214):

```java
// 1. Paid to Robert Ellis (ALWAYS FALSE)
private boolean isPaidToRobertEllis(FinancialTransaction t) {
    return false; // Placeholder - not implemented
}

// 2. Paid to Old Account (String matching in comments)
private boolean isPaidToOldAccount(FinancialTransaction t) {
    // Check data_source field
    if ("OLD_ACCOUNT".equals(t.getDataSource())) {
        return true;
    }

    // Fallback: Check description/comment for "old account"
    String description = t.getDescription();
    if (description != null &&
        description.toLowerCase().contains("old account")) {
        return true;
    }

    return false;
}

// 3. Paid to PayProp (Property has PayProp ID)
private boolean isPaidToPayProp(Property property) {
    return property.getPayPropId() != null &&
           !property.getPayPropId().trim().isEmpty();
}
```

---

## Output Formats

### XLSX Format

#### File Structure (38 Columns)

**Header Section (Rows 0-11)**:
```
Row 0-1:  PROPSK LTD
Row 2:    Company Address
Row 3:    Email/Phone
Row 5:    STATEMENT OF ACCOUNT
Row 6:    Property Holding Statement
Row 8:    Client: [Owner Name]
Row 9:    For the month: [Period]
Row 11:   Generated on: [Date]
```

**Column Headers (Row 12)**:
```
A.  Unit No.
B.  Tenant
C.  Tenancy Dates
D.  Rent Due Date
E.  Rent Due Amount
F.  Rent Received Date
G.  Paid to Robert Ellis (Y/N)
H.  Paid to Old Account (Y/N)
I.  Paid to PayProp (Y/N)
J.  Rent Received Amount
K.  Amount via PayProp
L.  Amount via Old Account
M.  Total Rent Received
N.  Management Fee %
O.  Management Fee (£)
P.  Service Fee %
Q.  Service Fee (£)
R.  Total Fees
S.  Expense 1 Label
T.  Expense 1 Amount
U.  Expense 1 Comment
V.  Expense 2 Label
W.  Expense 2 Amount
X.  Expense 2 Comment
Y.  Expense 3 Label
Z.  Expense 3 Amount
AA. Expense 3 Comment
AB. Expense 4 Label
AC. Expense 4 Amount
AD. Expense 4 Comment
AE. Total Expenses
AF. Net Due to Owner
AG. Net from Old Account
AH. Net from PayProp
AI. Net Total
AJ. Date Paid
AK. Outstanding
AL. Comments
```

**Data Rows (Row 13+)**:
- One row per property
- Excel formulas for calculations
- Formatted as currency/percentage

**Total Row (Bottom)**:
- Yellow background
- Sum formulas across all properties

#### Formatting Details

**Cell Styles**:
```java
// Currency format
CellStyle currencyStyle = workbook.createCellStyle();
currencyStyle.setDataFormat(
    workbook.createDataFormat().getFormat("£#,##0.00")
);

// Percentage format
CellStyle percentageStyle = workbook.createCellStyle();
percentageStyle.setDataFormat(
    workbook.createDataFormat().getFormat("0.00%")
);

// Header style
CellStyle headerStyle = workbook.createCellStyle();
headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
headerStyle.setFont(headerFont); // White, bold
```

**Column Widths**:
```java
sheet.setColumnWidth(0, 1500);  // Unit No.
sheet.setColumnWidth(1, 4000);  // Tenant
sheet.setColumnWidth(2, 3000);  // Tenancy Dates
sheet.setColumnWidth(3, 2500);  // Rent Due Date
// ... (38 columns total)
```

#### Multi-Sheet Support

**Monthly Breakdown**:
```
Sheet 1: January 2025
Sheet 2: February 2025
Sheet 3: March 2025
...
Sheet N: Summary (cross-sheet references)
```

**Summary Sheet Formulas**:
```
Total Rent Received = SUM(January!AF100, February!AF100, March!AF100, ...)
Excel: =SUM('January 2025'!AF100,'February 2025'!AF100,...)
```

### Google Sheets Format

**Same Structure as XLSX**, but:
- Cloud-hosted on Google Drive
- Shareable URL returned
- Real-time collaboration
- Apps Script integration capability
- Automatic formula evaluation

**Google Sheets API Integration**:
```java
// Create spreadsheet
Spreadsheet spreadsheet = sheetsService.spreadsheets().create(request).execute();

// Get shareable URL
String spreadsheetUrl = spreadsheet.getSpreadsheetUrl();

// Share with owner
Drive driveService = getDriveService();
Permission permission = new Permission()
    .setType("user")
    .setRole("writer")
    .setEmailAddress(owner.getEmail());
driveService.permissions()
    .create(spreadsheet.getSpreadsheetId(), permission)
    .execute();
```

**Shared Drive Location**:
- Drive ID: `0ADaFlidiFrFDUk9PVA`
- Folder: Automatically organized by owner/period
- Permissions: Owner has editor access, service account has manager access

---

## Current Issues

### 1. ❌ **No Invoice Linking** (CRITICAL - NOW FIXED)

**Issue**:
```java
// Current approach (lines 1139-1150 in XLSXStatementService)
List<FinancialTransaction> transactions =
    financialTransactionRepository.findPropertyTransactionsForStatement(
        property.getPayPropId(), fromDate, toDate);
```

**Problem**:
- Queries by property + date range only
- No explicit `invoice_id` matching
- Could mismatch payments to wrong leases
- Multiple sequential tenants in same property = wrong attribution

**Example Issue**:
```
Property: Flat 10
Lease 1: Jan-Jun 2025 (Tenant A)
Lease 2: Jul-Dec 2025 (Tenant B)

Payment on May 15: £735
  Current logic: Matches to property + date (could be either lease)
  Correct logic: Match by invoice_id → Lease 1 (Tenant A)
```

**Status**: ✅ **FIXED** - All 135 rent payments now have `invoice_id` populated

### 2. ⚠️ **Hardcoded Commission Rates**

**Issue** (XLSXStatementService lines 1144-1149):
```java
// Management Fee: 10% hardcoded
BigDecimal managementFee = rentReceived.multiply(new BigDecimal("-0.10"));

// Service Fee: 5% hardcoded
BigDecimal serviceFee = rentReceived.multiply(new BigDecimal("-0.05"));
```

**Problem**:
- All properties/owners get same 15% commission
- No per-property or per-owner rates
- Cannot handle variable commission agreements

**Should Be**:
```java
// Get commission rate from property or owner configuration
BigDecimal managementRate = property.getManagementFeePercentage();
BigDecimal serviceRate = property.getServiceFeePercentage();
```

### 3. ⚠️ **Payment Routing Detection Issues**

**Issue** (XLSXStatementService lines 1196-1214):

```java
// Robert Ellis routing always returns FALSE
private boolean isPaidToRobertEllis(FinancialTransaction t) {
    return false; // TODO: Implement
}

// Old Account detection relies on string matching
private boolean isPaidToOldAccount(FinancialTransaction t) {
    String description = t.getDescription();
    return description != null &&
           description.toLowerCase().contains("old account");
}
```

**Problems**:
- Fragile string matching in description field
- "Robert Ellis" routing not implemented
- No structured `payment_channel` or `account_type` field
- Relies on comments/descriptions which users can modify

**Should Have**:
```sql
-- Add to financial_transactions table
ALTER TABLE financial_transactions
ADD COLUMN payment_channel ENUM('payprop', 'old_account', 'robert_ellis', 'direct');
```

### 4. ⚠️ **Limited Expense Categorization**

**Issue** (XLSXStatementService lines 1236-1261):

```java
// Only 4 expenses per property (hardcoded)
List<FinancialTransaction> expenses = transactions.stream()
    .filter(t -> isExpenseTransaction(t))
    .limit(4) // ← HARDCODED LIMIT
    .collect(Collectors.toList());

// String-based category detection
private boolean isExpenseTransaction(FinancialTransaction t) {
    String category = t.getCategoryName();
    if (category == null) return false;

    String categoryLower = category.toLowerCase();
    return categoryLower.contains("maintenance") ||
           categoryLower.contains("repair") ||
           categoryLower.contains("clean"); // ← STRING MATCHING
}
```

**Problems**:
- Limit of 4 expenses per property (arbitrary)
- String matching on category names (fragile)
- No proper expense type enum
- Cannot group/aggregate expenses by type

**Should Have**:
```java
enum ExpenseType {
    MAINTENANCE,
    REPAIR,
    CLEANING,
    UTILITIES,
    SERVICE_CHARGE,
    INSURANCE,
    COMPLIANCE,
    OTHER
}
```

### 5. ⚠️ **Sample Data in Production Code**

**Issue** (BodenHouseStatementTemplateService lines 115-118):

```java
if (properties.isEmpty()) {
    // TEMPORARY: Generate sample data for testing
    log.warn("No properties found for owner {}, generating sample data", ownerId);
    return generateSampleStatement(owner, fromDate, toDate);
}
```

**Problem**:
- Production code generates fake data
- Could hide missing data issues
- User sees statement but data is not real
- No clear indication that data is sample

**Should**:
- Remove sample data generation from production
- Return error or empty statement
- Log warning but don't create fake statement

### 6. ⚠️ **Data Source Filtering Incomplete**

**Issue**:
```java
// StatementDataSource enum exists
enum StatementDataSource {
    UNIFIED,
    HISTORICAL,
    PAYPROP
}

// But filtering logic not fully implemented in all services
// Some services ignore the data source parameter
```

**Problem**:
- User selects data source in UI
- But some services query all data anyway
- Inconsistent behavior across statement types
- No clear separation of historical vs PayProp queries

**Should**:
```java
if (request.includesDataSource(HISTORICAL)) {
    transactions.addAll(
        historicalTransactionRepository.findByPropertyAndDateRange(...)
    );
}

if (request.includesDataSource(PAYPROP)) {
    transactions.addAll(
        financialTransactionRepository.findPropertyTransactionsForStatement(...)
    );
}
```

### 7. ⚠️ **No Pro-Rating Logic**

**Issue**:
- Statements assume full month rent
- Cannot handle partial months (move-in/move-out)
- User's manual spreadsheet has pro-rating formulas
- System doesn't replicate this

**Example**:
```
Lease: March 15 - June 15
Statement period: March 2025

Rent due: £740/month
Days in March: 31
Days occupied: 17 (March 15-31)

Pro-rated rent: £740 × (17/31) = £405.81
Current system: £740 (full month)
```

**Should Have**:
```java
BigDecimal calculateProRatedRent(Invoice lease, LocalDate periodStart, LocalDate periodEnd) {
    LocalDate leaseStart = lease.getStartDate();
    LocalDate leaseEnd = lease.getEndDate();

    // Calculate overlap days
    LocalDate effectiveStart = laterDate(leaseStart, periodStart);
    LocalDate effectiveEnd = earlierDate(leaseEnd, periodEnd);

    int daysOccupied = daysBetween(effectiveStart, effectiveEnd);
    int daysInMonth = lengthOfMonth(periodStart);

    return lease.getAmount()
        .multiply(new BigDecimal(daysOccupied))
        .divide(new BigDecimal(daysInMonth), 2, RoundingMode.HALF_UP);
}
```

### 8. ⚠️ **Authentication Complexity**

**Issue** (StatementController lines 683-695):

```java
// Complex fallback logic for customer lookup
Customer owner;
if (isAdmin || isEmployee) {
    owner = customerRepository.findById(request.getPropertyOwnerId())
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Owner not found"));
} else {
    // User ID 54 assumed to have multiple customer records
    if (currentUser.getId() == 54L) {
        owner = customerRepository.findById(request.getPropertyOwnerId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Owner not found"));
    } else {
        owner = customerRepository.findByUserId(currentUser.getId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Not authorized"));
    }
}
```

**Problems**:
- Hardcoded user ID 54 edge case
- Complex admin/employee detection
- OAuth vs regular authentication handling
- No clear role-based access control

**Should Have**:
```java
@PreAuthorize("hasRole('ADMIN') or @securityService.ownsCustomer(#customerId)")
public Statement generateStatement(Long customerId, ...) {
    // Authorization handled by Spring Security
}
```

### 9. ⚠️ **Missing Reconciliation Data**

**Issue**:
```java
// Field exists but not consistently populated
transaction.setReconciliationDate(date); // Sometimes used, sometimes null
```

**Problem**:
- No batch-level reconciliation tracking
- Cannot show "cleared" vs "pending" payments
- PayProp batch ID exists but not always linked
- Cannot generate bank reconciliation reports

**Should Have**:
```sql
CREATE TABLE reconciliation_batches (
    id BIGINT PRIMARY KEY,
    batch_date DATE,
    bank_statement_date DATE,
    total_amount DECIMAL(10,2),
    status ENUM('pending', 'reconciled', 'discrepancy'),
    created_at DATETIME
);

ALTER TABLE financial_transactions
ADD COLUMN reconciliation_batch_id BIGINT REFERENCES reconciliation_batches(id);
```

### 10. ⚠️ **Excel Formula Limitations**

**Issue** (XLSXStatementService):

```java
// Formulas as strings with substitution
cell.setCellFormula("=J" + rowNum + "*-0.1"); // Management fee

// String substitution approach
String formula = "=J{row}*-0.1".replace("{row}", String.valueOf(rowNum));
cell.setCellFormula(formula);
```

**Problems**:
- Formulas as strings (no type safety)
- String substitution with row numbers (error-prone)
- No Excel formula API usage
- Formula evaluation unreliable across Excel versions
- Cannot validate formulas at generation time

**Should Use**:
```java
// Apache POI formula builder
FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

CellReference rentReceivedRef = new CellReference(rowNum, 9); // Column J
String formula = rentReceivedRef.formatAsString() + "*-0.1";
cell.setCellFormula(formula);

// Validate formula
evaluator.evaluateFormulaCell(cell);
```

---

## Connection to Invoice Linking

### Before Invoice Linking Fix

**Problem**:
```java
// Statement service queries transactions by property + date
List<FinancialTransaction> transactions =
    findPropertyTransactionsForStatement(propertyId, fromDate, toDate);

// No way to match transactions to specific leases
// If property has multiple sequential leases, payments could mismatch
```

**Scenario**:
```
Property: Flat 10
Lease 1: Jan-Jun (Tenant A, £700/month)
Lease 2: Jul-Dec (Tenant B, £750/month)

Payment on May 15: £700
  Statement logic: Shows under property (but which lease?)
  Problem: Could attribute to wrong tenant
```

### After Invoice Linking Fix (100% Populated)

**Now Available**:
```sql
SELECT
    ht.transaction_date,
    ht.amount,
    ht.description,
    ht.invoice_id,        -- ✅ NOW POPULATED
    i.lease_reference,    -- ✅ CAN JOIN
    i.customer_id,        -- ✅ CORRECT TENANT
    i.start_date,         -- ✅ LEASE PERIOD
    i.end_date,
    c.name as tenant_name
FROM historical_transactions ht
JOIN invoices i ON ht.invoice_id = i.id  -- ✅ EXPLICIT LINK
JOIN customers c ON i.customer_id = c.customer_id
WHERE ht.property_id = :propertyId
  AND ht.transaction_date BETWEEN :fromDate AND :toDate
ORDER BY i.lease_reference, ht.transaction_date
```

**Benefits for Statement Generation**:

1. **✅ Accurate Lease Attribution**
   - Each payment explicitly linked to a lease
   - No ambiguity with multiple sequential tenants
   - Correct tenant shown on statement

2. **✅ Lease-Based Grouping**
   - Can generate statements grouped by lease
   - One row per lease (not per property)
   - Show lease reference numbers

3. **✅ Lease-Specific Arrears**
   - Calculate arrears per lease
   - Handle partial months correctly
   - Track payment history per tenancy

4. **✅ Pro-Rating Enabled**
   - Lease dates available on transaction
   - Can calculate partial month rent
   - Match user's manual spreadsheet approach

5. **✅ Tenant History**
   - Show all payments for a specific lease
   - Track tenant payment behavior
   - Generate tenant statements accurately

### How to Update Statement Services

**Option 1: Modify Existing Query**
```java
// OLD (property-based)
List<FinancialTransaction> transactions =
    financialTransactionRepository.findPropertyTransactionsForStatement(
        property.getPayPropId(), fromDate, toDate);

// NEW (lease-based)
List<FinancialTransaction> transactions =
    financialTransactionRepository.findByInvoiceIdAndDateRange(
        invoice.getId(), fromDate, toDate);
```

**Option 2: Add New Repository Method**
```java
@Query("SELECT ht FROM HistoricalTransaction ht " +
       "JOIN FETCH ht.invoice i " +
       "WHERE ht.property = :property " +
       "AND ht.transaction_date BETWEEN :fromDate AND :toDate " +
       "ORDER BY i.leaseReference, ht.transactionDate")
List<HistoricalTransaction> findTransactionsGroupedByLease(
    @Param("property") Property property,
    @Param("fromDate") LocalDate fromDate,
    @Param("toDate") LocalDate toDate
);
```

**Option 3: New Lease-Based Statement Service**
```java
public class InvoiceBasedStatementService {

    public Workbook generateStatement(Customer owner, LocalDate from, LocalDate to) {
        // Get all leases for owner's properties
        List<Invoice> leases = invoiceRepository.findByOwnerAndDateRange(owner, from, to);

        for (Invoice lease : leases) {
            // Get transactions for THIS SPECIFIC LEASE
            List<HistoricalTransaction> payments =
                historicalTransactionRepository.findByInvoiceId(lease.getId());

            // Calculate rent due for THIS LEASE
            BigDecimal rentDue = calculateProRatedRent(lease, from, to);

            // Calculate rent received for THIS LEASE
            BigDecimal rentReceived = payments.stream()
                .map(HistoricalTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Add row to statement
            addRowToStatement(sheet, lease, rentDue, rentReceived, payments);
        }
    }
}
```

---

## Improvement Opportunities

### High Priority

#### 1. **Use invoice_id for Lease-Specific Statements** ✅
- Modify queries to filter by `invoice_id`
- Group transactions by lease
- Show lease reference on statements

#### 2. **Add Pro-Rating Logic** 📊
- Calculate partial month rent based on lease dates
- Handle move-in/move-out scenarios
- Match user's manual spreadsheet formulas

#### 3. **Make Commission Rates Configurable** ⚙️
```sql
ALTER TABLE properties
ADD COLUMN management_fee_percentage DECIMAL(5,2) DEFAULT 10.00,
ADD COLUMN service_fee_percentage DECIMAL(5,2) DEFAULT 5.00;

-- OR per-owner rates
ALTER TABLE customers
ADD COLUMN default_management_fee_percentage DECIMAL(5,2) DEFAULT 10.00,
ADD COLUMN default_service_fee_percentage DECIMAL(5,2) DEFAULT 5.00;
```

### Medium Priority

#### 4. **Structured Payment Routing** 🔄
```sql
ALTER TABLE financial_transactions
ADD COLUMN payment_channel ENUM(
    'payprop',
    'old_account',
    'robert_ellis',
    'direct_deposit',
    'cash',
    'cheque'
) DEFAULT 'payprop';

-- Populate from existing data
UPDATE financial_transactions
SET payment_channel = 'old_account'
WHERE data_source = 'OLD_ACCOUNT'
   OR description LIKE '%old account%';
```

#### 5. **Expense Type Enum** 📝
```sql
ALTER TABLE financial_transactions
ADD COLUMN expense_type ENUM(
    'maintenance',
    'repair',
    'cleaning',
    'utilities',
    'service_charge',
    'insurance',
    'compliance',
    'furnishings',
    'other'
);

-- Populate from category names
UPDATE financial_transactions
SET expense_type = CASE
    WHEN category_name LIKE '%maintenance%' THEN 'maintenance'
    WHEN category_name LIKE '%repair%' THEN 'repair'
    WHEN category_name LIKE '%clean%' THEN 'cleaning'
    WHEN category_name LIKE '%utilit%' THEN 'utilities'
    ELSE 'other'
END
WHERE transaction_type IN ('payment_to_contractor', 'debit_note');
```

#### 6. **Remove Expense Limit** 🔓
```java
// OLD: Limited to 4 expenses
List<FinancialTransaction> expenses = transactions.stream()
    .filter(t -> isExpenseTransaction(t))
    .limit(4) // ← REMOVE THIS
    .collect(Collectors.toList());

// NEW: Show all expenses, group by type
Map<String, List<FinancialTransaction>> expensesByType =
    transactions.stream()
        .filter(t -> isExpenseTransaction(t))
        .collect(Collectors.groupingBy(t -> t.getExpenseType().name()));

// Add summary row per expense type
for (Map.Entry<String, List<FinancialTransaction>> entry : expensesByType.entrySet()) {
    String expenseType = entry.getKey();
    BigDecimal total = entry.getValue().stream()
        .map(FinancialTransaction::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    addExpenseRow(sheet, expenseType, total, entry.getValue().size());
}
```

### Low Priority

#### 7. **Reconciliation Tracking** 💰
- Add reconciliation batch tracking
- Show cleared vs pending payments
- Generate bank reconciliation reports

#### 8. **Remove Sample Data Generation** 🚫
- Remove `generateSampleStatement()` from production
- Return clear error if no data found
- Don't create fake statements

#### 9. **Consistent Data Source Filtering** 🔍
- Implement data source filtering in all services
- Clear separation of Historical vs PayProp queries
- Honor user's data source selection

#### 10. **Role-Based Access Control** 🔐
- Replace hardcoded user ID checks
- Use Spring Security `@PreAuthorize`
- Proper authorization for customer access

---

## Next Steps

### Immediate Actions

1. **✅ COMPLETE** - Invoice linking (100% of transactions linked)

2. **Review Statement Requirements** ❓
   - What calculations does the manual spreadsheet do?
   - What pro-rating formulas are used?
   - What format is preferred?

3. **Decide on Approach** 🤔
   - **Option A**: Update existing XLSX service to use invoice_id
   - **Option B**: Create new invoice-based statement service
   - **Option C**: Extract data + generate with Excel formulas (your manual approach)

4. **Test with Real Data** 🧪
   - Generate statement for a property with multiple leases
   - Verify payments attributed to correct tenants
   - Check arrears calculations

### Questions for User

1. **Do you want to keep the existing service format** (38 columns, current layout)?
2. **Or replicate your manual spreadsheet** (with pro-rating formulas)?
3. **What are the biggest pain points** with the current statement service?
4. **Are commission rates always 15%** (10% + 5%), or do they vary?
5. **How important is pro-rating** for partial months?

---

## Summary

**Existing System**:
- ✅ Well-structured with two output formats (XLSX + Google Sheets)
- ✅ Multiple statement types (owner, portfolio, tenant, lease-based)
- ✅ Configurable data sources (Unified/Historical/PayProp)
- ✅ Professional formatting and Excel formulas

**Main Issues**:
- ❌ No invoice linking (NOW FIXED - 100% populated)
- ⚠️ Hardcoded commission rates
- ⚠️ Payment routing detection fragile
- ⚠️ Limited expense handling
- ⚠️ No pro-rating logic

**Opportunities**:
- Use invoice_id for accurate lease attribution
- Add pro-rating for partial months
- Make commission rates configurable
- Structured payment routing and expense types

---

**Status**: Ready for next phase - statement service improvements

**Decision Needed**: How to proceed with statement generation?

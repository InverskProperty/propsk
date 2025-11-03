# Unified Data Layer Specification

**Version:** 1.0
**Date:** November 3, 2025
**Status:** Active Standard

---

## Purpose

This document defines the **standard structure and expectations** for all financial data in the CRM system. All dashboards, reports, and APIs MUST use this unified layer to ensure consistency.

---

## Core Principle

**ONE DATA MODEL, MULTIPLE SOURCES**

All financial data flows through a unified layer that combines:
- Historical transactions (CSV imports, legacy data)
- PayProp live data (current property management)
- Future sources (Xero, QuickBooks, etc.)

---

## Data Flow Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     DATA SOURCES                             │
├─────────────────────────────────────────────────────────────┤
│  Historical CSVs  │  PayProp API  │  Xero  │  QuickBooks   │
└──────────┬──────────────┬──────────────┬──────────┬─────────┘
           │              │              │          │
           ▼              ▼              ▼          ▼
┌─────────────────────────────────────────────────────────────┐
│              SOURCE-SPECIFIC TABLES                          │
├─────────────────────────────────────────────────────────────┤
│ historical_transactions  │  payprop_report_*  │  xero_*     │
└──────────┬──────────────────┬─────────────────────┬─────────┘
           │                  │                     │
           └──────────────────┼─────────────────────┘
                              │
                              ▼
           ┌──────────────────────────────────────┐
           │   UNIFIED TRANSACTION LAYER          │
           │   (StatementTransactionDto)          │
           └──────────────────┬───────────────────┘
                              │
                              ▼
           ┌──────────────────────────────────────┐
           │   DASHBOARDS & REPORTS               │
           │   - Financial Summary                │
           │   - Statements                       │
           │   - Analytics                        │
           └──────────────────────────────────────┘
```

---

## Standard DTO: StatementTransactionDto

**File:** `site.easy.to.build.crm.dto.StatementTransactionDto`

This is the **ONLY** DTO that should be used for displaying financial transactions across the system.

### Core Fields (ALWAYS PRESENT)

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `transactionDate` | LocalDate | When transaction occurred | 2025-10-23 |
| `amount` | BigDecimal | Transaction amount | 1200.00 |
| `description` | String | Human-readable description | "Management Fee For Flat 17" |
| `transactionType` | String | Transaction type identifier | "invoice", "payment", "expense" |
| `source` | TransactionSource | Data source enum | HISTORICAL, PAYPROP |

### Property Context (SHOULD BE PRESENT)

| Field | Type | Description |
|-------|------|-------------|
| `propertyId` | Long | Internal property ID |
| `propertyPayPropId` | String | PayProp property reference |
| `propertyName` | String | Property display name |

### Financial Breakdown (MAY BE PRESENT)

| Field | Type | Description | When Present |
|-------|------|-------------|--------------|
| `commissionRate` | BigDecimal | Commission % (e.g. 16.29) | On invoices/rent |
| `commissionAmount` | BigDecimal | Calculated commission £ | On invoices/rent |
| `netToOwnerAmount` | BigDecimal | Net amount to landlord | On beneficiary payments |
| `serviceFeeAmount` | BigDecimal | Service/transaction fees | On payments |

### Extended Context (OPTIONAL)

| Field | Type | Description |
|-------|------|-------------|
| `category` | String | Transaction category (e.g., "rent", "maintenance") |
| `subcategory` | String | Sub-classification |
| `beneficiaryName` | String | Who received payment |
| `beneficiaryType` | String | "agency", "beneficiary", "contractor" |
| `tenantName` | String | Tenant name if applicable |
| `ownerName` | String | Property owner name |

---

## Standard Transaction Types

### Primary Types (transactionType field)

| Type | Description | Flow | Amount Sign |
|------|-------------|------|-------------|
| `invoice` | Rent invoiced to tenant | INCOMING | Positive |
| `payment` | Rent received from tenant | INCOMING | Positive |
| `tenant_payment` | Tenant payment received | INCOMING | Positive |
| `payment_to_beneficiary` | Payment made to landlord | OUTGOING | Negative |
| `payment_to_agency` | Payment to agency | OUTGOING | Negative |
| `commission_payment` | Agency commission | OUTGOING | Negative |
| `expense` | Property expense | OUTGOING | Negative |
| `maintenance` | Maintenance work | OUTGOING | Negative |
| `fee` | Service fee | OUTGOING | Negative |

### Business Logic Helpers (in StatementTransactionDto)

```java
// Check transaction intent
boolean isRentPayment()      // Incoming rent
boolean isExpense()          // Outgoing expense
boolean isOwnerPayment()     // Money to owner
boolean isAgencyFee()        // Agency fees

// Check data source
boolean isPayPropTransaction()
boolean isHistoricalTransaction()
```

---

## Service Layer Standard

### UnifiedFinancialDataService

**File:** `site.easy.to.build.crm.service.financial.UnifiedFinancialDataService`

This service MUST be used for all financial data queries.

#### Method: getPropertyTransactions()

```java
List<StatementTransactionDto> getPropertyTransactions(
    Property property,
    LocalDate fromDate,
    LocalDate toDate
)
```

**Returns:** Combined list of transactions from ALL sources
- Automatically merges historical + PayProp data
- Sorted by date (newest first)
- NO deduplication (shows all records)
- Returns empty list on error (never null)

#### Method: getPropertyFinancialSummary()

```java
Map<String, Object> getPropertyFinancialSummary(Property property)
```

**Returns Map containing:**

| Key | Type | Description |
|-----|------|-------------|
| `rentReceived` | BigDecimal | Total rent actually received |
| `totalExpenses` | BigDecimal | Total expenses paid out |
| `totalCommissions` | BigDecimal | Total agency commissions |
| `netOwnerIncome` | BigDecimal | Net to owner after everything |
| `rentArrears` | BigDecimal | Outstanding rent arrears |
| `transactionCount` | Integer | Number of transactions |

#### Method: getExpensesByCategory()

```java
Map<String, BigDecimal> getExpensesByCategory(
    List<Property> properties,
    LocalDate startDate,
    LocalDate endDate
)
```

**Returns:** Map of category name → total amount spent

Example:
```java
{
    "agency_fee": 3080.00,
    "maintenance": 1250.00,
    "insurance": 450.00
}
```

#### Method: getMonthlyTrends()

```java
List<Map<String, Object>> getMonthlyTrends(
    List<Property> properties,
    LocalDate startDate,
    LocalDate endDate
)
```

**Returns:** List of month objects, each containing:

```java
{
    "month": "2025-10",           // YYYY-MM format
    "income": 15000.00,           // Total income
    "expenses": 2500.00,          // Total expenses
    "commission": 2445.00,        // Commission paid
    "netToOwner": 10055.00        // Net to owner
}
```

---

## UI Display Standards

### Transaction Table

When displaying transactions in ANY UI, use this pattern:

```html
<tr th:each="transaction : ${transactions}">
    <!-- Date -->
    <td th:text="${#temporals.format(transaction.transactionDate, 'dd/MM/yyyy')}">
        Date
    </td>

    <!-- Property -->
    <td th:text="${transaction.propertyName}">
        Property Name
    </td>

    <!-- Type Badge -->
    <td>
        <span class="badge" th:text="${transaction.transactionType}">
            Type
        </span>
    </td>

    <!-- Amount - ALWAYS show if present -->
    <td class="text-right">
        <span th:if="${transaction.amount != null}">
            £<span th:text="${#numbers.formatDecimal(transaction.amount, 1, 2)}">0.00</span>
        </span>
        <span th:if="${transaction.amount == null}">-</span>
    </td>

    <!-- Commission - show if present -->
    <td class="text-right">
        <span th:if="${transaction.commissionAmount != null}">
            £<span th:text="${#numbers.formatDecimal(transaction.commissionAmount, 1, 2)}">0.00</span>
        </span>
        <span th:if="${transaction.commissionAmount == null}">-</span>
    </td>

    <!-- Net to Owner - show if present -->
    <td class="text-right">
        <span th:if="${transaction.netToOwnerAmount != null}">
            £<span th:text="${#numbers.formatDecimal(transaction.netToOwnerAmount, 1, 2)}">0.00</span>
        </span>
        <span th:if="${transaction.netToOwnerAmount == null}">-</span>
    </td>

    <!-- Description -->
    <td th:text="${transaction.description}">
        Description
    </td>
</tr>
```

### Summary Cards

```html
<!-- Rent Received Card -->
<div class="card">
    <h5>£<span th:text="${#numbers.formatDecimal(totalRent, 1, 2)}">0.00</span></h5>
    <p>Total Rent Received</p>
    <small class="text-muted">PayProp actual data</small>
</div>

<!-- Commission Card -->
<div class="card">
    <h5>£<span th:text="${#numbers.formatDecimal(totalCommission, 1, 2)}">0.00</span></h5>
    <p>Commission Paid</p>
    <small class="text-muted" th:text="${commissionRate} + '% average rate'">
        16.29% average rate
    </small>
</div>

<!-- Net to Owner Card -->
<div class="card">
    <h5>£<span th:text="${#numbers.formatDecimal(totalNetToOwner, 1, 2)}">0.00</span></h5>
    <p>Net to You</p>
    <small class="text-muted">Your actual receipts</small>
</div>

<!-- Arrears Card (conditional styling) -->
<div class="card" th:classappend="${totalArrears > 0 ? 'border-danger' : 'border-success'}">
    <h5>£<span th:text="${#numbers.formatDecimal(totalArrears, 1, 2)}">0.00</span></h5>
    <p>Rent Arrears</p>
    <small th:if="${totalArrears > 0}" class="text-danger">Action required</small>
    <small th:if="${totalArrears == 0}" class="text-success">All up to date</small>
</div>
```

---

## Chart.js Integration

### Monthly Trends Chart

```javascript
// Controller must provide:
model.addAttribute("monthlyTrends", monthlyTrends);

// JavaScript in HTML:
const monthlyTrendsData = /*[[${monthlyTrends}]]*/ [];

const labels = monthlyTrendsData.map(m => m.month);
const incomeData = monthlyTrendsData.map(m => m.income);
const expensesData = monthlyTrendsData.map(m => m.expenses);
const netData = monthlyTrendsData.map(m => m.netToOwner);

new Chart(ctx, {
    type: 'line',
    data: {
        labels: labels,
        datasets: [
            {
                label: 'Rent Received',
                data: incomeData,
                borderColor: '#28a745',
                backgroundColor: 'rgba(40, 167, 69, 0.1)'
            },
            {
                label: 'Expenses',
                data: expensesData,
                borderColor: '#dc3545',
                backgroundColor: 'rgba(220, 53, 69, 0.1)'
            },
            {
                label: 'Net to Owner',
                data: netData,
                borderColor: '#007bff',
                backgroundColor: 'rgba(0, 123, 255, 0.1)'
            }
        ]
    }
});
```

### Expense Breakdown Chart

```javascript
// Controller must provide:
model.addAttribute("expensesByCategory", expensesByCategory);

// JavaScript:
const expensesData = /*[[${expensesByCategory}]]*/ {};

const labels = Object.keys(expensesData);
const amounts = Object.values(expensesData);

new Chart(ctx, {
    type: 'doughnut',
    data: {
        labels: labels,
        datasets: [{
            data: amounts,
            backgroundColor: [
                '#007bff', '#28a745', '#ffc107',
                '#dc3545', '#6c757d', '#17a2b8'
            ]
        }]
    }
});
```

---

## Controller Requirements

### Financial Dashboard Endpoint

**Pattern:** All financial dashboards MUST follow this structure:

```java
@GetMapping("/financial-dashboard")
public String viewFinancials(
    Model model,
    Authentication authentication,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
) {
    // 1. Get authenticated customer
    Customer customer = getAuthenticatedPropertyOwner(authentication);

    // 2. Get customer's properties
    List<Property> properties = propertyService.findPropertiesByCustomerAssignments(customer.getCustomerId());

    // 3. Default date range: last 12 months
    if (startDate == null) startDate = LocalDate.now().minusYears(1);
    if (endDate == null) endDate = LocalDate.now();

    // 4. Calculate aggregates using UnifiedFinancialDataService
    BigDecimal totalRent = BigDecimal.ZERO;
    BigDecimal totalCommission = BigDecimal.ZERO;
    BigDecimal totalNetToOwner = BigDecimal.ZERO;
    BigDecimal totalArrears = BigDecimal.ZERO;

    for (Property property : properties) {
        Map<String, Object> summary = unifiedFinancialDataService.getPropertyFinancialSummary(property);
        totalRent = totalRent.add((BigDecimal) summary.get("rentReceived"));
        totalCommission = totalCommission.add((BigDecimal) summary.get("totalCommissions"));
        totalNetToOwner = totalNetToOwner.add((BigDecimal) summary.get("netOwnerIncome"));
        totalArrears = totalArrears.add((BigDecimal) summary.get("rentArrears"));
    }

    // 5. Get chart data
    Map<String, BigDecimal> expensesByCategory =
        unifiedFinancialDataService.getExpensesByCategory(properties, startDate, endDate);

    List<Map<String, Object>> monthlyTrends =
        unifiedFinancialDataService.getMonthlyTrends(properties, startDate, endDate);

    // 6. Get recent transactions
    List<StatementTransactionDto> recentTransactions = properties.stream()
        .flatMap(p -> unifiedFinancialDataService.getPropertyTransactions(p, startDate, endDate).stream())
        .sorted(Comparator.comparing(StatementTransactionDto::getTransactionDate).reversed())
        .limit(50)
        .collect(Collectors.toList());

    // 7. Add to model
    model.addAttribute("totalRent", totalRent);
    model.addAttribute("totalCommission", totalCommission);
    model.addAttribute("totalNetToOwner", totalNetToOwner);
    model.addAttribute("totalArrears", totalArrears);
    model.addAttribute("expensesByCategory", expensesByCategory);
    model.addAttribute("monthlyTrends", monthlyTrends);
    model.addAttribute("recentTransactions", recentTransactions);
    model.addAttribute("startDate", startDate);
    model.addAttribute("endDate", endDate);

    return "financial-dashboard";
}
```

---

## Testing Requirements

### Unit Tests

Every new financial feature MUST have tests verifying:

1. **Data Source Integration**
   ```java
   @Test
   void shouldCombineHistoricalAndPayPropData()
   ```

2. **Calculation Accuracy**
   ```java
   @Test
   void shouldCalculateCorrectTotals()
   ```

3. **Date Range Filtering**
   ```java
   @Test
   void shouldFilterByDateRange()
   ```

4. **Null Safety**
   ```java
   @Test
   void shouldHandleNullValuesGracefully()
   ```

### Integration Tests

1. **End-to-End Flow**
   ```java
   @Test
   void shouldDisplayCompleteFinancialDashboard()
   ```

2. **Multiple Properties**
   ```java
   @Test
   void shouldAggregateAcrossMultipleProperties()
   ```

---

## Common Pitfalls

### ❌ DON'T: Query source tables directly

```java
// WRONG
List<PayPropTransaction> txs = payPropRepo.findAll();
```

### ✅ DO: Use UnifiedFinancialDataService

```java
// CORRECT
List<StatementTransactionDto> txs =
    unifiedFinancialDataService.getPropertyTransactions(property, start, end);
```

### ❌ DON'T: Type-specific display logic

```html
<!-- WRONG - breaks when new types added -->
<td th:if="${transaction.transactionType == 'invoice'}">
    Show amount
</td>
<td th:if="${transaction.transactionType != 'invoice'}">
    -
</td>
```

### ✅ DO: Null-safe conditional display

```html
<!-- CORRECT - works for all types -->
<td>
    <span th:if="${transaction.amount != null}">
        £[[${#numbers.formatDecimal(transaction.amount, 1, 2)}]]
    </span>
    <span th:if="${transaction.amount == null}">-</span>
</td>
```

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-11-03 | Initial specification based on current implementation |

---

## References

- `StatementTransactionDto.java` - Standard DTO definition
- `UnifiedFinancialDataService.java` - Service layer implementation
- `UnifiedTransaction.java` - Database entity (future migration target)
- `UNIFIED_TRANSACTIONS_IMPLEMENTATION_COMPLETE.md` - Implementation details

---

**END OF SPECIFICATION**

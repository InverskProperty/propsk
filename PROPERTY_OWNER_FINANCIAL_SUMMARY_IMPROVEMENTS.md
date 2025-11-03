# Property Owner Financial Summary - Enhancement Plan

## Current State
**Page:** `/property-owner/financials`
**Controller:** `PropertyOwnerController.viewFinancials()` (L1690-1930)
**Template:** `templates/property-owner/financials.html`
**Service:** `UnifiedFinancialDataService.java`

### Problems
- Fixed 2-year lookback, no date controls
- Arrears calculated but not shown
- Many placeholder/broken features
- No charts/visualizations
- Static tables, no sorting/filtering
- Disconnected from statement generation
- Loads all data upfront (slow)

---

## Implementation Roadmap

### PHASE 1: FOUNDATION (Quick Wins)

#### 1.1 Date Range Selector
**Backend:** `PropertyOwnerController.java`
```java
// Add params: @RequestParam(required=false) LocalDate startDate, endDate
// Default: startDate = LocalDate.now().minusMonths(12), endDate = LocalDate.now()
```
**Frontend:** Add Bootstrap daterange picker
```html
<input type="date" id="startDate" /> to <input type="date" id="endDate" />
<button onclick="refreshData()">Apply</button>
```

#### 1.2 Arrears Display
**Backend:** `UnifiedFinancialDataService.getPropertyFinancialSummary()`
```java
// Already calculates rentArrears - add to DTO return
dto.setTotalArrears(summary.getRentArrears());
```
**Frontend:** Add metric card
```html
<div class="metric-card ${arrears > 0 ? 'alert-danger' : 'alert-success'}">
  <h3>Rent Arrears</h3>
  <p>£${arrears}</p>
</div>
```

#### 1.3 Statement Download Link
**Frontend:** Add button
```html
<a href="/api/statements/option-c/owner/${customerId}/excel?startDate=${start}&endDate=${end}"
   class="btn btn-primary">Download Statement</a>
```

#### 1.4 Working Filters
**Backend:** Fix `/property-owner/financials/filter` endpoint
```java
// Use existing params, call service with filters
service.getFilteredSummary(customerId, portfolioId, dataSource, startDate, endDate);
```
**Frontend:** AJAX on dropdown change
```javascript
$('#portfolioFilter, #dataSourceFilter').change(() => {
  fetch('/property-owner/financials/filter?...')
    .then(r => r.json()).then(updateMetrics);
});
```

#### 1.5 Remove Broken Features
```html
<!-- Remove or add disabled + "Coming Soon" badge -->
<button disabled>Tax Report <span class="badge">Coming Soon</span></button>
```

#### 1.6 Last Updated Timestamp
```html
<small class="text-muted">Last updated: ${LocalDateTime.now()}</small>
```

---

### PHASE 2: VISUALIZATION & INSIGHTS

#### 2.1 Expense Category Breakdown
**Backend:** `UnifiedFinancialDataService.getExpensesByCategory()`
```java
Map<String, BigDecimal> categories = repo.findAll()
  .stream()
  .collect(Collectors.groupingBy(
    UnifiedTransaction::getCategory,
    Collectors.reducing(BigDecimal.ZERO, UnifiedTransaction::getAmount, BigDecimal::add)
  ));
```
**Frontend:** Chart.js pie chart
```javascript
new Chart(ctx, {
  type: 'pie',
  data: { labels: categories.keys, datasets: [{data: categories.values}] }
});
```

#### 2.2 Monthly Trend Charts
**Backend:** `getMonthlyTrends(customerId, startDate, endDate)`
```java
// Group by YEAR-MONTH, sum amounts per category
SELECT DATE_TRUNC('month', transaction_date) as month,
       SUM(CASE WHEN flow_direction='INCOMING' THEN amount ELSE 0 END) as income,
       SUM(CASE WHEN flow_direction='OUTGOING' THEN amount ELSE 0 END) as expenses
GROUP BY month ORDER BY month;
```
**Frontend:** Chart.js line chart
```javascript
new Chart(ctx, {
  type: 'line',
  data: { labels: months, datasets: [
    {label: 'Rent Received', data: incomeData},
    {label: 'Expenses', data: expenseData},
    {label: 'Net to Owner', data: netData}
  ]}
});
```

#### 2.3 Property Performance Dashboard
**Backend:** Add real calculations
```java
dto.setCollectionRate(rentReceived / rentDue * 100);
dto.setAvgDaysToPayment(calculateAvgPaymentDays(propertyId));
dto.setTotalExpenses(sumExpenses(propertyId));
dto.setNetYield((rentReceived - expenses) / propertyValue * 100);
```

#### 2.4 Interactive Transaction Table (DataTables)
**Frontend:**
```html
<script src="https://cdn.datatables.net/1.13.7/js/jquery.dataTables.min.js"></script>
<script>
$('#transactionsTable').DataTable({
  pageLength: 25,
  order: [[0, 'desc']], // Date desc
  dom: 'Bfrtip',
  buttons: ['copy', 'csv', 'excel', 'pdf']
});
</script>
```

#### 2.5 Collapsible Sections
```html
<div class="accordion">
  <div class="card">
    <div class="card-header" data-toggle="collapse" data-target="#propertyBreakdown">
      Property Breakdown <i class="fa fa-chevron-down"></i>
    </div>
    <div id="propertyBreakdown" class="collapse show">...</div>
  </div>
</div>
```

#### 2.6 Smart Insights Panel
**Backend:** `generateInsights(summary)`
```java
List<String> insights = new ArrayList<>();
if (currentMonthRent < avgMonthlyRent * 0.95)
  insights.add("Rent collection " + percentDiff + "% below average");
if (currentMonthExpenses > avgMonthlyExpenses * 1.2)
  insights.add("Expenses up " + percentDiff + "% vs average");
if (arrears > 0)
  insights.add(countProperties + " properties have outstanding arrears");
```
**Frontend:** Alert-style cards
```html
<div class="insights-panel">
  <h4>This Month's Highlights</h4>
  <div class="alert alert-warning" th:each="insight : ${insights}">
    <i class="fa fa-info-circle"></i> <span th:text="${insight}"></span>
  </div>
</div>
```

---

### PHASE 3: ADVANCED FEATURES

#### 3.1 Custom Billing Periods
**Backend:** Add to customer preferences
```java
@Column(name = "billing_period_start_day")
private Integer billingPeriodStartDay; // 1, 22, 25, 28

// In service:
LocalDate periodStart = LocalDate.of(year, month, customer.getBillingPeriodStartDay());
LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);
```

#### 3.2 Historical Statement Library
**Backend:** Track generated statements
```java
@Entity
class GeneratedStatement {
  Long id;
  Long customerId;
  LocalDate periodStart;
  LocalDate periodEnd;
  String filePath;
  LocalDateTime generatedAt;
}
```
**Frontend:** List view
```html
<table>
  <tr th:each="stmt : ${statements}">
    <td th:text="${stmt.periodStart}"></td>
    <td th:text="${stmt.periodEnd}"></td>
    <td><a th:href="@{/download/{id}(id=${stmt.id})}">Download</a></td>
    <td><a th:href="@{/regenerate/{id}(id=${stmt.id})}">Regenerate</a></td>
  </tr>
</table>
```

#### 3.3 Real Expense Tracker
**Modal form submits to:** `/api/expenses/add`
```java
@PostMapping("/api/expenses/add")
public void addExpense(@RequestBody ExpenseDTO dto) {
  // Create manual financial_transaction
  FinancialTransaction txn = new FinancialTransaction();
  txn.setAmount(dto.getAmount());
  txn.setCategory(dto.getCategory());
  txn.setPropertyId(dto.getPropertyId());
  txn.setDescription(dto.getDescription());
  txn.setPaypropDataSource("MANUAL");
  repo.save(txn);
}
```

#### 3.4 Cash Flow Projections
**Backend:** `getCashFlowForecast(customerId, months=3)`
```java
// Get active leases with recurring rent
List<Invoice> leases = invoiceRepo.findActiveLeases(customerId);
BigDecimal expectedRent = leases.stream()
  .map(Invoice::getRecurringAmount)
  .reduce(BigDecimal.ZERO, BigDecimal::add);

// Average historical expenses
BigDecimal avgExpenses = getAvgMonthlyExpenses(customerId, 12);

// Build forecast
for (int i=0; i<months; i++) {
  LocalDate month = LocalDate.now().plusMonths(i);
  forecast.add(new ForecastDTO(month, expectedRent, avgExpenses, expectedRent.subtract(avgExpenses)));
}
```

#### 3.5 Comparison Mode
**Frontend:** Toggle + dual display
```html
<input type="checkbox" id="comparisonMode" /> Compare Periods
<div id="comparison" style="display:none">
  <div class="col-6">
    <h5>Current Period</h5>
    <div th:replace="fragments/metrics :: metrics(${current})"></div>
  </div>
  <div class="col-6">
    <h5>Previous Period</h5>
    <div th:replace="fragments/metrics :: metrics(${previous})"></div>
  </div>
</div>
```

#### 3.6 Alert System
**Backend:** Scheduled job
```java
@Scheduled(cron = "0 0 9 * * *") // Daily 9am
public void checkAlerts() {
  customers.forEach(c -> {
    if (getArrears(c) > threshold)
      emailService.send(c.getEmail(), "Arrears Alert", template);
    if (getUnusualExpense(c))
      emailService.send(c.getEmail(), "High Expense Alert", template);
  });
}
```

---

### PHASE 4: BUSINESS INTELLIGENCE

#### 4.1 Tax Preparation Helper
**Backend:** `/api/tax/annual-summary/{year}`
```java
public TaxSummaryDTO getAnnualTax(Long customerId, int year) {
  // Aggregate by tax categories
  Map<String, BigDecimal> income = Map.of(
    "Rental Income", sumIncoming(customerId, year)
  );
  Map<String, BigDecimal> expenses = Map.of(
    "Repairs & Maintenance", sumByCategory(customerId, year, "maintenance"),
    "Management Fees", sumByCategory(customerId, year, "management"),
    "Utilities", sumByCategory(customerId, year, "utilities")
  );
  return new TaxSummaryDTO(income, expenses, income.total() - expenses.total());
}
```

#### 4.2 Benchmarking
**Backend:** Portfolio averages
```java
// Get all properties in portfolio
BigDecimal avgRentPerProperty = portfolioRepo.getAvgRent(portfolioId);
BigDecimal avgExpensesPerProperty = portfolioRepo.getAvgExpenses(portfolioId);

// Compare
dto.setPerformanceVsAvg((propertyRent - avgRent) / avgRent * 100);
```

#### 4.3 ROI & Yield Calculations
**Backend:** Requires property valuation
```java
// Add valuation to Property entity
@Column(name = "current_valuation")
private BigDecimal currentValuation;

// Calculate yields
dto.setGrossYield(annualRent / valuation * 100);
dto.setNetYield((annualRent - annualExpenses) / valuation * 100);
```

#### 4.4 Occupancy Tracking
**Backend:** Use lease dates
```java
boolean isOccupied = invoiceRepo.existsByPropertyIdAndLeaseEndAfter(
  propertyId, LocalDate.now()
);

// Vacancy cost
if (!isOccupied) {
  LocalDate vacantSince = getLastLeaseEnd(propertyId);
  long daysVacant = ChronoUnit.DAYS.between(vacantSince, LocalDate.now());
  BigDecimal lostRent = monthlyRent.divide(30) * daysVacant;
}
```

#### 4.5 Document Attachments
**Backend:** File upload
```java
@Entity
class TransactionDocument {
  Long id;
  Long transactionId;
  String fileName;
  String filePath;
  LocalDateTime uploadedAt;
}

@PostMapping("/api/transactions/{id}/upload")
public void uploadDocument(@RequestParam MultipartFile file, @PathVariable Long id) {
  String path = fileService.save(file);
  docRepo.save(new TransactionDocument(id, file.getOriginalFilename(), path));
}
```

#### 4.6 Scheduled Reports
**Backend:** Quartz job
```java
@Scheduled(cron = "0 0 9 1 * *") // 1st of month, 9am
public void sendMonthlyReports() {
  customers.stream()
    .filter(c -> c.isReportEnabled())
    .forEach(c -> {
      byte[] pdf = generateMonthlyPDF(c);
      emailService.sendWithAttachment(c.getEmail(), "Monthly Summary", pdf);
    });
}
```

---

## Technical Requirements

### Dependencies (pom.xml)
```xml
<!-- Chart.js - CDN, no Maven needed -->
<!-- DataTables - CDN -->
<!-- DateRangePicker -->
<dependency>
  <groupId>org.webjars</groupId>
  <artifactId>bootstrap-datepicker</artifactId>
  <version>1.9.0</version>
</dependency>
```

### Database Changes
```sql
-- Add to customer table
ALTER TABLE customer ADD COLUMN billing_period_start_day INT DEFAULT 1;
ALTER TABLE customer ADD COLUMN report_enabled BOOLEAN DEFAULT false;

-- New tables
CREATE TABLE generated_statements (
  id BIGSERIAL PRIMARY KEY,
  customer_id BIGINT REFERENCES customer(id),
  period_start DATE,
  period_end DATE,
  file_path VARCHAR(500),
  generated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE transaction_documents (
  id BIGSERIAL PRIMARY KEY,
  transaction_id BIGINT,
  file_name VARCHAR(255),
  file_path VARCHAR(500),
  uploaded_at TIMESTAMP DEFAULT NOW()
);

-- Add to property table
ALTER TABLE property ADD COLUMN current_valuation DECIMAL(15,2);
```

### New Service Classes
```
UnifiedFinancialInsightsService.java - Smart insights generation
CashFlowProjectionService.java - Forecast calculations
TaxReportService.java - Annual tax summaries
AlertService.java - Arrears/expense monitoring
DocumentStorageService.java - File handling
```

### New Controllers/Endpoints
```
/api/financials/insights
/api/financials/trends/{period}
/api/financials/forecast
/api/tax/annual-summary/{year}
/api/expenses/add
/api/transactions/{id}/upload
```

---

## Testing Checklist

### Unit Tests
- [ ] Date range filtering logic
- [ ] Arrears calculation accuracy
- [ ] Expense categorization
- [ ] Monthly aggregation
- [ ] ROI/yield formulas
- [ ] Insights generation rules

### Integration Tests
- [ ] Filter API endpoints
- [ ] Statement generation link
- [ ] Document upload/download
- [ ] Email delivery
- [ ] Scheduled jobs

### UI Tests
- [ ] Date picker updates data
- [ ] Charts render correctly
- [ ] DataTables sorting works
- [ ] Collapsible sections toggle
- [ ] Modal forms submit
- [ ] Export buttons work

---

## Performance Optimizations

### Caching
```java
@Cacheable(value = "financialSummary", key = "#customerId + '_' + #startDate + '_' + #endDate")
public FinancialSummaryDTO getSummary(Long customerId, LocalDate startDate, LocalDate endDate) {
  // Expensive aggregation
}
```

### Lazy Loading
```javascript
// Load charts only when tab clicked
$('#trendsTab').on('shown.bs.tab', function() {
  if (!chartsLoaded) {
    loadCharts();
    chartsLoaded = true;
  }
});
```

### Pagination
```java
// Don't load all transactions upfront
@GetMapping("/api/transactions")
public Page<Transaction> getTransactions(
  @RequestParam Long customerId,
  Pageable pageable) {
  return repo.findByCustomerId(customerId, pageable);
}
```

---

## Deployment Plan

1. **Phase 1 (Week 1)** - Deploy foundation features
2. **Phase 2 (Week 2)** - Deploy visualization & insights
3. **Phase 3 (Week 3)** - Deploy advanced features
4. **Phase 4 (Week 4+)** - Deploy BI features gradually

**Each phase:**
- Develop on feature branch
- Unit test + integration test
- QA review on staging
- Deploy to production
- Monitor performance
- Gather user feedback

---

## Success Metrics

- Page load time < 2s
- User engagement (time on page) +50%
- Support tickets about financials -30%
- Statement downloads +40%
- User satisfaction score >4.5/5

---

## Future Enhancements

- Mobile app integration
- Real-time WebSocket updates
- ML-based predictive analytics
- Multi-currency support
- Voice-activated queries
- API for third-party integrations

# Historical Lease Handling Guide

## Overview

This guide explains how the system handles **past leases** (where both start and end dates are in the past) and how to query historical lease data.

---

## How Past Leases Are Handled

### Automatic Date-Based Filtering

The system uses **intelligent date filtering** to distinguish between active and historical leases:

| Lease Type | Condition | Included in Rent Calculations? | Example |
|------------|-----------|-------------------------------|---------|
| **Active** | `startDate <= today` AND (`endDate` is null OR `endDate >= today`) | ✅ YES | Tenant moved in June 2024, still living there |
| **Past/Ended** | `endDate < today` | ❌ NO | Tenant moved out March 2025 |
| **Future** | `startDate > today` | ❌ NO | Tenant moving in next month |

### Key Method: `Invoice.isCurrentlyActive()`

Located in `Invoice.java:203-214`:

```java
public boolean isCurrentlyActive() {
    // Check basic flags
    if (!this.isActive || this.deletedAt != null) {
        return false;
    }

    LocalDate today = LocalDate.now();

    // Not started yet?
    if (this.startDate.isAfter(today)) {
        return false;
    }

    // Already ended?
    return this.endDate == null || !this.endDate.isBefore(today);
}
```

---

## Creating Historical Leases

### Scenario: Recording Past Tenancy

You need to record a tenant who lived at a property from **January 2024 to June 2024** (both dates in the past).

#### Using TenantLeaseController

```bash
POST /employee/tenant-lease/create
Content-Type: application/x-www-form-urlencoded

customerId=123
propertyId=456
rentAmount=900.00
startDate=2024-01-01
endDate=2024-06-30
paymentDay=1
```

**Result:**
- Creates `CustomerPropertyAssignment` with historical dates
- Creates `Invoice` (lease) with historical dates
- Lease is **NOT included** in current rent calculations
- Lease **IS available** for historical reporting

#### Using InvoiceController (Direct)

```bash
POST /employee/invoice/create

customerId=123
propertyId=456
amount=900.00
startDate=2024-01-01
endDate=2024-06-30
categoryId=rent
description=Historical lease for past tenant
```

---

## Querying Historical Leases

### 1. Find Leases That Ended in a Date Range

**Repository Method**: `InvoiceRepository.findByEndDateBetween()`

```java
// Find all leases that ended in Q1 2025
LocalDate q1Start = LocalDate.of(2025, 1, 1);
LocalDate q1End = LocalDate.of(2025, 3, 31);

List<Invoice> endedLeases = invoiceRepository.findByEndDateBetween(q1Start, q1End);
```

### 2. Find Leases That Started in a Date Range

**Repository Method**: `InvoiceRepository.findByStartDateBetween()`

```java
// Find all leases that started in 2024
LocalDate yearStart = LocalDate.of(2024, 1, 1);
LocalDate yearEnd = LocalDate.of(2024, 12, 31);

List<Invoice> newLeases = invoiceRepository.findByStartDateBetween(yearStart, yearEnd);
```

### 3. Find ALL Leases for a Property (Active + Historical)

```java
// Get all leases regardless of status
List<Invoice> allLeases = invoiceRepository.findByProperty(property);

// Filter manually
List<Invoice> activeLeases = allLeases.stream()
    .filter(Invoice::isCurrentlyActive)
    .toList();

List<Invoice> historicalLeases = allLeases.stream()
    .filter(lease -> !lease.isCurrentlyActive())
    .toList();
```

---

## New Service Methods for Historical Data

I'll create a service to make historical queries easier:

### LeaseHistoryService

```java
@Service
public class LeaseHistoryService {

    /**
     * Get all historical leases for a property (ended leases)
     */
    public List<Invoice> getHistoricalLeasesForProperty(Long propertyId) {
        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property == null) return new ArrayList<>();

        List<Invoice> allLeases = invoiceRepository.findByProperty(property);

        return allLeases.stream()
            .filter(lease -> !lease.isCurrentlyActive())
            .filter(lease -> lease.getEndDate() != null && lease.getEndDate().isBefore(LocalDate.now()))
            .sorted(Comparator.comparing(Invoice::getEndDate).reversed())
            .toList();
    }

    /**
     * Calculate total income for a property during a specific period
     */
    public BigDecimal calculateIncomeForPeriod(Long propertyId, LocalDate periodStart, LocalDate periodEnd) {
        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property == null) return BigDecimal.ZERO;

        List<Invoice> allLeases = invoiceRepository.findByProperty(property);

        // Find leases active during the period
        List<Invoice> activeDuringPeriod = allLeases.stream()
            .filter(lease -> {
                // Lease started before period ended
                boolean startedBeforeEnd = !lease.getStartDate().isAfter(periodEnd);

                // Lease ended after period started (or still ongoing)
                boolean endedAfterStart = lease.getEndDate() == null ||
                                         !lease.getEndDate().isBefore(periodStart);

                return startedBeforeEnd && endedAfterStart;
            })
            .toList();

        // Sum the rent (this is simplified - real calculation needs pro-rating)
        return activeDuringPeriod.stream()
            .map(Invoice::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get lease history for a property with full details
     */
    public List<LeaseHistoryDetail> getLeaseHistoryWithDetails(Long propertyId) {
        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property == null) return new ArrayList<>();

        List<Invoice> allLeases = invoiceRepository.findByProperty(property);

        return allLeases.stream()
            .map(lease -> {
                LeaseHistoryDetail detail = new LeaseHistoryDetail();
                detail.invoiceId = lease.getId();
                detail.tenantName = lease.getCustomer().getName();
                detail.rentAmount = lease.getAmount();
                detail.startDate = lease.getStartDate();
                detail.endDate = lease.getEndDate();
                detail.status = lease.isCurrentlyActive() ? "Active" : "Ended";
                detail.durationMonths = calculateDurationMonths(lease.getStartDate(),
                    lease.getEndDate() != null ? lease.getEndDate() : LocalDate.now());
                return detail;
            })
            .sorted(Comparator.comparing(LeaseHistoryDetail::getStartDate).reversed())
            .toList();
    }

    public static class LeaseHistoryDetail {
        public Long invoiceId;
        public String tenantName;
        public BigDecimal rentAmount;
        public LocalDate startDate;
        public LocalDate endDate;
        public String status;
        public long durationMonths;
    }
}
```

---

## API Endpoints for Historical Data

### 1. Get Historical Leases for Property

```bash
GET /api/lease-migration/property/{propertyId}/history
```

**Response:**
```json
{
  "propertyId": 123,
  "propertyName": "Apartment 40 - 31 Watkin Road",
  "currentLeases": 3,
  "historicalLeases": 5,
  "history": [
    {
      "invoiceId": 456,
      "tenantName": "John Smith",
      "rentAmount": 850.00,
      "startDate": "2023-01-01",
      "endDate": "2024-06-30",
      "status": "Ended",
      "durationMonths": 18
    },
    {
      "invoiceId": 789,
      "tenantName": "Jane Doe",
      "rentAmount": 900.00,
      "startDate": "2024-07-01",
      "endDate": null,
      "status": "Active",
      "durationMonths": 8
    }
  ]
}
```

### 2. Calculate Income for Period

```bash
GET /api/lease-migration/property/{propertyId}/income?start=2024-01-01&end=2024-12-31
```

**Response:**
```json
{
  "propertyId": 123,
  "propertyName": "Apartment 40 - 31 Watkin Road",
  "periodStart": "2024-01-01",
  "periodEnd": "2024-12-31",
  "totalIncome": 10800.00,
  "leasesActiveDuringPeriod": 2,
  "breakdown": [
    {
      "tenantName": "John Smith",
      "rentAmount": 850.00,
      "monthsActive": 6,
      "totalContribution": 5100.00
    },
    {
      "tenantName": "Jane Doe",
      "rentAmount": 900.00,
      "monthsActive": 6,
      "totalContribution": 5400.00
    }
  ]
}
```

---

## Common Scenarios

### Scenario 1: Recording a Past Tenant Who Already Moved Out

**Question**: How do I record a tenant who lived at Property 40 from Jan-Jun 2024?

**Answer**:
```bash
POST /employee/tenant-lease/create

customerId=101
propertyId=40
rentAmount=850.00
startDate=2024-01-01
endDate=2024-06-30
paymentDay=1
```

**Result**:
- Lease record created with historical dates
- Does NOT affect current rent calculations
- Available for historical reporting
- Can link historical payments to this lease

### Scenario 2: Mid-Month Tenant Change

**Question**: Property had Tenant A until March 15, 2025, then Tenant B from March 16, 2025.

**Answer**: Create TWO separate leases:

```bash
# Lease 1: Tenant A (ended)
POST /employee/tenant-lease/create
customerId=101
propertyId=40
rentAmount=900.00
startDate=2024-01-01
endDate=2025-03-15

# Lease 2: Tenant B (active)
POST /employee/tenant-lease/create
customerId=102
propertyId=40
rentAmount=950.00
startDate=2025-03-16
endDate=  # Leave blank for ongoing
```

**Result**:
- March 2025 has TWO leases
- Tenant A's lease: excluded from current rent (ended)
- Tenant B's lease: included in current rent (active)
- Can calculate pro-rated income for March

### Scenario 3: Viewing Complete Tenancy History

**Question**: Show me all tenants who ever lived at Property 40.

**Answer**:
```bash
GET /api/lease-migration/property/40/history
```

This returns ALL leases (active + historical), sorted by most recent first.

---

## Database Impact

### tenant_balances Table

Historical leases can still have balance records:

```sql
SELECT * FROM tenant_balances
WHERE invoice_id = 456  -- Historical lease
ORDER BY statement_period DESC;
```

This shows historical arrears/overpayments for that ended lease.

### payments Table

Historical payments can link to historical leases:

```sql
SELECT
    p.payment_date,
    p.amount,
    i.description,
    c.name as tenant
FROM payments p
JOIN invoices i ON p.invoice_id = i.id
JOIN customers c ON i.customer_id = c.id
WHERE i.property_id = 40
  AND i.end_date < CURDATE()  -- Only ended leases
ORDER BY p.payment_date DESC;
```

---

## Best Practices

### ✅ DO:

1. **Always set end_date for past leases**
   - If recording historical data, set both start_date and end_date
   - This ensures proper filtering

2. **Link historical payments to historical leases**
   - Use `invoice_id` in payments table
   - Enables accurate arrears tracking

3. **Use historical queries for reporting**
   - Don't manually filter - use the repository methods
   - They handle date logic correctly

4. **Keep historical data intact**
   - Don't delete old lease records
   - Use soft delete (`deletedAt`) if needed

### ❌ DON'T:

1. **Don't delete historical leases**
   - You lose payment history
   - Can't generate historical reports

2. **Don't set end_date = null for past tenants**
   - System will think they're still active
   - Will include in current rent calculations

3. **Don't rely on Property.monthlyPayment for historical data**
   - Use lease-based calculations instead
   - Property field doesn't track changes over time

---

## Summary

| Aspect | How It Works |
|--------|--------------|
| **Creating Historical Lease** | Set both `startDate` and `endDate` in the past |
| **Automatic Filtering** | `isCurrentlyActive()` checks date ranges |
| **Rent Calculations** | Only active leases included (endDate >= today) |
| **Historical Reporting** | Query by date ranges or filter manually |
| **Payment Allocation** | Historical payments link to historical leases via `invoice_id` |
| **Balance Tracking** | `tenant_balances` preserves historical arrears per lease |

**The system handles historical leases automatically - you just need to set the correct dates!**

# Local Lease Creation Guide

## Overview

This guide explains how locally created owners, properties, and tenants work with the lease-based system.

---

## Key Concept: Invoice = Lease

Whether data comes from **PayProp** or is **created locally**, the `Invoice` entity represents a lease:

```
Invoice (Lease) = Tenant + Property + Rent Amount + Period
```

---

## Two Workflows for Creating Leases

### Workflow 1: Two Separate Steps (Current Behavior)

#### Step 1: Create Tenant Assignment
**URL**: `/employee/assignment/create`

Creates `customer_property_assignments` record:
- Links tenant → property
- Sets start/end dates
- **Does NOT include financial terms**

#### Step 2: Create Invoice (Lease)
**URL**: `/employee/invoice/create`

Creates `invoices` record:
- Links tenant → property
- Sets rent amount, frequency, payment day
- **This is the actual lease**

**Problem**: Easy to forget Step 2, resulting in tenant assignments without leases!

---

### Workflow 2: Combined Step (NEW - RECOMMENDED)

#### Create Tenant + Lease Together
**URL**: `/employee/tenant-lease/create`

Creates BOTH records in one operation:
1. `customer_property_assignments` (tenant assignment)
2. `invoices` (lease with financial terms)

**Advantages**:
- ✅ Can't forget to create lease
- ✅ Consistent data
- ✅ Proper lease-based tracking from day one

---

## How It Works

### For Locally Created Data:

#### 1. Create Property Owner
```
URL: /employee/customer/create
Customer Type: PROPERTY_OWNER
→ Saves in 'customers' table
```

#### 2. Create Property
```
URL: /employee/property/create
→ Saves in 'properties' table
```

#### 3. Create Tenant (Customer)
```
URL: /employee/customer/create
Customer Type: TENANT
→ Saves in 'customers' table
```

#### 4A. OLD WAY: Two-Step Process

**Step 1**: Create Tenant Assignment
```
URL: /employee/assignment/create
customerId: 123 (tenant)
propertyId: 456
assignmentType: TENANT
startDate: 2025-01-01
→ Saves in 'customer_property_assignments'
```

**Step 2**: Create Lease (Don't forget this!)
```
URL: /employee/invoice/create
customerId: 123 (tenant)
propertyId: 456
amount: 900.00
frequency: Monthly
paymentDay: 1
startDate: 2025-01-01
→ Saves in 'invoices' table (THIS IS THE LEASE!)
```

#### 4B. NEW WAY: Combined Process (RECOMMENDED)

```
URL: /employee/tenant-lease/create
customerId: 123 (tenant)
propertyId: 456
rentAmount: 900.00
startDate: 2025-01-01
paymentDay: 1
→ Creates BOTH:
  - customer_property_assignments (tenant assignment)
  - invoices (lease)
```

---

## Checking for Missing Leases

### API: Find Tenant Assignments Without Leases

```bash
GET /employee/tenant-lease/api/assignments-without-leases
```

**Response**:
```json
{
  "count": 5,
  "assignments": [
    {
      "id": 12,
      "tenantName": "John Smith",
      "propertyName": "123 Main Street",
      "startDate": "2025-01-01",
      "endDate": "Ongoing"
    }
  ]
}
```

### API: Add Lease to Existing Assignment

If you have tenant assignments without leases, fix them:

```bash
POST /employee/tenant-lease/api/add-lease/12
Content-Type: application/json

{
  "rentAmount": 900.00,
  "paymentDay": 1
}
```

**Response**:
```json
{
  "success": true,
  "message": "Successfully added lease to existing assignment",
  "leaseId": 234,
  "assignmentId": 12
}
```

---

## Comparison: PayProp vs Local

| Aspect | PayProp Sync | Local Creation |
|--------|-------------|----------------|
| **Property** | Imported from PayProp | Created via `/employee/property/create` |
| **Tenant** | Imported as Customer | Created via `/employee/customer/create` |
| **Lease (Invoice)** | Imported via `/api/lease-migration/import` | Created via `/employee/invoice/create` or `/employee/tenant-lease/create` |
| **Assignment** | Implicit in PayProp invoice | Explicit via `/employee/assignment/create` |
| **Rent Tracking** | Invoice amount from PayProp | Invoice amount from local form |
| **Arrears** | Calculated per invoice (lease) | Calculated per invoice (lease) |
| **Multi-Tenancy** | ✅ Supported | ✅ Supported |

**Key Takeaway**: Both work the same way! The `invoices` table is always the lease, regardless of source.

---

## Multi-Tenant Example (Local)

### Scenario: Apartment 40 has 3 tenants

#### Create 3 separate leases:

**Lease 1: Jason Barclay**
```
POST /employee/tenant-lease/create
customerId: 101 (Jason)
propertyId: 40
rentAmount: 900.00
startDate: 2025-06-17
```

**Lease 2: Michel Mabondo**
```
POST /employee/tenant-lease/create
customerId: 102 (Michel)
propertyId: 40
rentAmount: 1040.00
startDate: 2025-06-23
```

**Lease 3: Neha Minocha**
```
POST /employee/tenant-lease/create
customerId: 103 (Neha)
propertyId: 40
rentAmount: 900.00
startDate: 2025-09-02
```

**Result**: Property 40 now has 3 active leases in the `invoices` table, totaling £2,840/month.

---

## Checking Lease Data

### API: Get All Leases for a Property

```bash
GET /api/lease-migration/property/40/leases
```

**Response**:
```json
{
  "propertyId": 40,
  "numberOfLeases": 3,
  "leases": [
    {
      "invoiceId": 201,
      "tenantName": "Jason Barclay",
      "rentAmount": 900.00,
      "startDate": "2025-06-17"
    },
    {
      "invoiceId": 202,
      "tenantName": "Michel Mabondo",
      "rentAmount": 1040.00,
      "startDate": "2025-06-23"
    },
    {
      "invoiceId": 203,
      "tenantName": "Neha Minocha",
      "rentAmount": 900.00,
      "startDate": "2025-09-02"
    }
  ]
}
```

### API: Calculate Total Rent for Property

The `LeaseBasedRentCalculationService` automatically sums all active leases:

```java
BigDecimal totalRent = leaseRentService.getTotalRentForProperty(40L);
// Returns: £2,840.00 (sum of all 3 leases)
```

---

## Database Schema

### customer_property_assignments
```sql
CREATE TABLE customer_property_assignments (
  id BIGINT PRIMARY KEY,
  customer_id INT NOT NULL,      -- WHO
  property_id BIGINT NOT NULL,   -- WHERE
  assignment_type ENUM('OWNER','TENANT','MANAGER'),
  start_date DATE,               -- WHEN started
  end_date DATE,                 -- WHEN ended (NULL = ongoing)
  payprop_invoice_id VARCHAR     -- Links to invoices.id
);
```

### invoices (THIS IS THE LEASE!)
```sql
CREATE TABLE invoices (
  id BIGINT PRIMARY KEY,
  customer_id INT NOT NULL,      -- WHO (tenant)
  property_id BIGINT NOT NULL,   -- WHERE
  amount DECIMAL(10,2) NOT NULL, -- HOW MUCH
  frequency ENUM(...) NOT NULL,  -- HOW OFTEN
  payment_day INT,               -- WHEN due
  start_date DATE NOT NULL,      -- Lease start
  end_date DATE,                 -- Lease end (NULL = ongoing)
  is_active BOOLEAN              -- Active lease?
);
```

### payments
```sql
CREATE TABLE payments (
  id BIGINT PRIMARY KEY,
  invoice_id BIGINT,             -- ← Links to lease!
  property_id BIGINT,
  tenant_id BIGINT,
  amount DECIMAL(10,2),
  payment_date DATE
);
```

### tenant_balances
```sql
CREATE TABLE tenant_balances (
  id BIGINT PRIMARY KEY,
  invoice_id BIGINT NOT NULL,    -- ← Tracks balance per lease!
  tenant_id VARCHAR(100),
  property_id VARCHAR(100),
  statement_period DATE,
  rent_due DECIMAL(10,2),
  rent_received DECIMAL(10,2),
  running_balance DECIMAL(10,2)
);
```

---

## Best Practices

### ✅ DO:
1. **Always create a lease when assigning a tenant**
   - Use `/employee/tenant-lease/create` to ensure both are created
2. **Check for missing leases regularly**
   - Use `/employee/tenant-lease/api/assignments-without-leases`
3. **Create multiple leases for multiple tenants**
   - One property can have many leases
4. **Use the same workflow for local and PayProp data**
   - Both create records in the `invoices` table

### ❌ DON'T:
1. **Don't create tenant assignments without leases**
   - Old workflow: `/employee/assignment/create` alone
   - Problem: No financial tracking!
2. **Don't use `Property.monthlyPayment` for multi-tenant properties**
   - Use `LeaseBasedRentCalculationService.getTotalRentForProperty()` instead
3. **Don't manually calculate rent**
   - Let the service sum active leases automatically

---

## Migration: Adding Leases to Existing Assignments

If you have old tenant assignments without leases:

### Step 1: Find them
```bash
GET /employee/tenant-lease/api/assignments-without-leases
```

### Step 2: Add leases
```bash
# For each assignment:
POST /employee/tenant-lease/api/add-lease/{assignmentId}
{
  "rentAmount": 900.00,
  "paymentDay": 1
}
```

### Step 3: Verify
```bash
GET /api/lease-migration/statistics

# Should show:
# {
#   "totalLeases": 50,  ← Increased
#   "activeLeases": 45
# }
```

---

## Summary

### For Locally Created Data:

| Entity | How to Create | Result |
|--------|--------------|--------|
| **Owner** | `/employee/customer/create` (type: PROPERTY_OWNER) | `customers` table |
| **Property** | `/employee/property/create` | `properties` table |
| **Tenant** | `/employee/customer/create` (type: TENANT) | `customers` table |
| **Lease** | `/employee/tenant-lease/create` (RECOMMENDED) | `invoices` + `customer_property_assignments` |

### Key Points:
- ✅ `Invoice` entity = Lease (whether from PayProp or local)
- ✅ Use new `/employee/tenant-lease/create` endpoint
- ✅ Multi-tenancy works the same way (multiple invoices per property)
- ✅ Rent calculated from sum of active leases
- ✅ Arrears tracked per lease (`invoice_id` in `tenant_balances`)

**The lease-based system works identically for PayProp and local data!**

# Instruction Workspace & Lead Pipeline - Complete System Integration Map

**Version:** 1.0
**Date:** 2025-11-09
**Status:** Documentation Complete

---

## Table of Contents

1. [Executive Overview](#executive-overview)
2. [System Architecture](#system-architecture)
3. [Entity Relationships](#entity-relationships)
4. [Instruction Workflow](#instruction-workflow)
5. [Lead Pipeline Integration](#lead-pipeline-integration)
6. [Tenant Property Assignment](#tenant-property-assignment)
7. [Dashboard Integration](#dashboard-integration)
8. [Customer Portal Visibility](#customer-portal-visibility)
9. [Data Flow Diagrams](#data-flow-diagrams)
10. [Implementation Status](#implementation-status)

---

## Executive Overview

### What This System Does

This system manages the complete lifecycle of letting/renting properties, from receiving an instruction to let a property through to an active tenancy. It integrates:

1. **Letting Instructions** - Tracking each distinct letting period for a property
2. **Lead Pipeline** - Managing prospective tenants from enquiry to conversion
3. **Property Vacancy Management** - Tracking property status and availability
4. **Tenant Assignments** - Linking tenants to properties via CustomerPropertyAssignment
5. **Multi-User Dashboards** - Providing visibility to employees, property owners, and tenants

### Key Benefits

✅ **Unified Workflow** - Single source of truth for letting lifecycle
✅ **Automated Tracking** - Metrics, tasks, and status updates
✅ **Multi-Tenant Visibility** - Employees, owners, and tenants see relevant data
✅ **Integration Ready** - Links to PayProp, Google Calendar, Gmail, Drive
✅ **Complete Audit Trail** - Every step is tracked and timestamped

---

## System Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         PROPERTY LETTING SYSTEM                      │
└─────────────────────────────────────────────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        │                           │                           │
        ▼                           ▼                           ▼
┌───────────────┐          ┌───────────────┐          ┌───────────────┐
│   PROPERTY    │          │   LETTING     │          │     LEAD      │
│               │◄─────────┤ INSTRUCTION   │──────────►│   PIPELINE    │
│ • Property ID │          │               │          │               │
│ • Address     │          │ • Status      │          │ • Lead Type   │
│ • Type        │          │ • Dates       │          │ • Property ID │
│ • Owner       │          │ • Target Rent │          │ • Status      │
└───────────────┘          └───────────────┘          └───────────────┘
        │                           │                           │
        │                           ▼                           │
        │                  ┌───────────────┐                   │
        │                  │   PROPERTY    │                   │
        │                  │   VIEWINGS    │◄──────────────────┘
        │                  │               │
        │                  │ • Scheduled   │
        │                  │ • Completed   │
        │                  └───────────────┘
        │                           │
        ▼                           ▼
┌───────────────┐          ┌───────────────┐
│   CUSTOMER    │          │   CUSTOMER    │
│   (Tenant)    │◄─────────┤   PROPERTY    │
│               │          │  ASSIGNMENT   │
│ • Type        │          │               │
│ • Contact     │          │ • Type=TENANT │
│ • Move In     │          │ • Start Date  │
└───────────────┘          └───────────────┘
        │
        ▼
┌───────────────┐
│   DASHBOARDS  │
│               │
│ • Employee    │
│ • Owner       │
│ • Tenant      │
└───────────────┘
```

### Technology Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17+ with Spring Boot |
| ORM | JPA/Hibernate |
| Database | MySQL 8.0+ |
| Frontend | Thymeleaf, HTML5, JavaScript |
| Integration | Gmail API, Google Calendar, Google Drive |
| Sync | PayProp API |

---

## Entity Relationships

### Core Entity Structure

```sql
-- PROPERTY
properties
├── id (PK)
├── address_line1, city, postcode
├── property_type, bedrooms, bathrooms
├── monthly_payment
└── occupancy_status (NEW FIELD)

-- LETTING INSTRUCTION (NEW)
letting_instructions
├── id (PK)
├── property_id (FK → properties)
├── status (ENUM: InstructionStatus)
├── instruction_received_date
├── expected_vacancy_date
├── advertising_start_date
├── target_rent
├── tenant_id (FK → customers) -- when converted
├── actual_rent
├── lease_start_date, lease_end_date
├── number_of_enquiries
├── number_of_viewings
└── days_to_fill, days_vacant

-- LEAD (EXTENDED)
trigger_lead
├── lead_id (PK)
├── property_id (FK → properties) -- NEW
├── lead_type (ENUM: BUSINESS, PROPERTY_RENTAL) -- NEW
├── letting_instruction_id (FK → letting_instructions) -- NEW
├── name, email, phone
├── status (enquiry, viewing-scheduled, converted, etc.)
├── desired_move_in_date -- NEW
├── budget_min, budget_max -- NEW
├── has_pets, has_guarantor -- NEW
└── converted_to_customer_id (FK → customers) -- NEW

-- PROPERTY VIEWING (NEW)
property_viewings
├── id (PK)
├── lead_id (FK → trigger_lead)
├── property_id (FK → properties)
├── letting_instruction_id (FK → letting_instructions)
├── scheduled_datetime
├── status (SCHEDULED, COMPLETED, CANCELLED, NO_SHOW)
├── feedback, interested_level
└── google_calendar_event_id

-- CUSTOMER
customers
├── customer_id (PK)
├── customer_type (ENUM: TENANT, PROPERTY_OWNER, DELEGATED_USER)
├── first_name, last_name, email, phone
└── move_in_date

-- CUSTOMER PROPERTY ASSIGNMENT
customer_property_assignments
├── id (PK)
├── customer_id (FK → customers)
├── property_id (FK → properties)
├── assignment_type (ENUM: TENANT, OWNER, DELEGATED)
├── start_date, end_date
├── is_primary
└── payprop_invoice_id
```

### Relationship Diagram

```
Property (1) ──┬──► (N) LettingInstruction
               │
               ├──► (N) Lead [via property_id]
               │
               ├──► (N) PropertyViewing
               │
               └──► (N) CustomerPropertyAssignment
                          │
                          └──► (1) Customer (Tenant/Owner)

LettingInstruction (1) ──┬──► (N) Lead
                         │
                         ├──► (N) PropertyViewing
                         │
                         ├──► (N) PropertyVacancyTask
                         │
                         ├──► (N) Invoice
                         │
                         └──► (1) Customer [tenant]

Lead (1) ──────────────────► (N) PropertyViewing
     │
     └──────────────────────► (1) Customer [converted_to_customer_id]
```

---

## Instruction Workflow

### Instruction Status Enum

```java
public enum InstructionStatus {
    INSTRUCTION_RECEIVED,    // 1. Initial instruction from owner
    PREPARING,               // 2. Property prep (cleaning, repairs, photos)
    ADVERTISING,             // 3. Actively marketing the property
    VIEWINGS_IN_PROGRESS,    // 4. Viewings being conducted
    OFFER_ACCEPTED,          // 5. Offer accepted, contracts being prepared
    ACTIVE_LEASE,            // 6. Tenant moved in, lease active
    CLOSED                   // 7. Instruction closed (various reasons)
}
```

### Workflow Stages

```
┌──────────────────────┐
│ INSTRUCTION_RECEIVED │ ← Owner instructs to let property
└──────────────────────┘
            │
            │ Staff mark as "preparing"
            ▼
┌──────────────────────┐
│     PREPARING        │ ← Tasks: Cleaning, Repairs, Photography
└──────────────────────┘
            │
            │ Staff activate advertising
            ▼
┌──────────────────────┐
│    ADVERTISING       │ ← Property listed on portals
└──────────────────────┘      Leads start coming in
            │
            │ Viewings scheduled
            ▼
┌──────────────────────┐
│ VIEWINGS_IN_PROGRESS │ ← Multiple viewings happening
└──────────────────────┘      Feedback being collected
            │
            │ Offer accepted from a lead
            ▼
┌──────────────────────┐
│   OFFER_ACCEPTED     │ ← Contracts being prepared
└──────────────────────┘      Referencing in progress
            │
            │ Tenant moves in
            ▼
┌──────────────────────┐
│    ACTIVE_LEASE      │ ← Property now occupied
└──────────────────────┘      Rental payments commencing
            │
            │ Lease ends / Notice given
            ▼
┌──────────────────────┐
│       CLOSED         │ ← Instruction complete
└──────────────────────┘      → NEW instruction can be created
```

### Key Business Rules

1. **One Active Instruction Per Property** - A property can only have ONE instruction in statuses: INSTRUCTION_RECEIVED, PREPARING, ADVERTISING, VIEWINGS_IN_PROGRESS, OFFER_ACCEPTED at a time
2. **Multiple Historical Instructions** - A property can have MANY closed or active_lease instructions over its lifetime
3. **Instruction Auto-Creates Tasks** - When status changes, automated tasks are created
4. **Metrics Auto-Calculate** - Days to fill, vacancy days, conversion rates are calculated automatically

---

## Lead Pipeline Integration

### Lead Types

```java
public enum LeadType {
    BUSINESS,           // Traditional business lead (existing system)
    PROPERTY_RENTAL     // New: Property rental enquiry
}
```

### Lead Status Flow (Property Rental)

```
┌─────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────┐
│ enquiry │────►│  viewing-    │────►│  viewing-    │────►│interested│
│         │     │  scheduled   │     │  completed   │     │          │
└─────────┘     └──────────────┘     └──────────────┘     └──────────┘
                                                                  │
                                                                  ▼
                        ┌────────────┐     ┌──────────────┐
                        │referencing │◄────│ application- │
                        │            │     │  submitted   │
                        └────────────┘     └──────────────┘
                              │
                              ▼
                        ┌────────────┐     ┌──────────┐
                        │in-contracts│────►│converted │──► Creates Customer + Assignment
                        │            │     │          │
                        └────────────┘     └──────────┘

                              OR
                        ┌────────────┐
                        │    lost    │ ← Can happen at any stage
                        └────────────┘
```

### Lead → Tenant Conversion

When a lead status is changed to **"converted"**, the `LeadConversionService` performs:

```java
// 1. Create Customer (type=TENANT)
Customer tenant = new Customer();
tenant.setCustomerType(CustomerType.TENANT);
tenant.setFirstName(lead.getName());
tenant.setEmail(lead.getEmail());
tenant.setPhone(lead.getPhone());
tenant.setMoveInDate(lead.getDesiredMoveInDate());
customerRepository.save(tenant);

// 2. Create CustomerPropertyAssignment (type=TENANT)
CustomerPropertyAssignment assignment = new CustomerPropertyAssignment(
    tenant,
    lead.getProperty(),
    AssignmentType.TENANT
);
assignment.setStartDate(tenant.getMoveInDate());
assignment.setIsPrimary(true);
assignmentRepository.save(assignment);

// 3. Create Invoice (lease agreement)
Invoice lease = new Invoice();
lease.setCustomer(tenant);
lease.setProperty(lead.getProperty());
lease.setInvoiceType("Rent");
lease.setAmount(property.getMonthlyPayment());
lease.setFrequency("Monthly");
lease.setStartDate(tenant.getMoveInDate());
invoiceRepository.save(lease);

// 4. Update LettingInstruction status to ACTIVE_LEASE
instruction.convertToActiveLease(
    tenant,
    lease.getStartDate(),
    lease.getEndDate(),
    lease.getAmount(),
    depositAmount
);

// 5. Update Lead
lead.setStatus("converted");
lead.setConvertedAt(LocalDateTime.now());
lead.setConvertedToCustomer(tenant);
leadRepository.save(lead);

// 6. Update Property occupancy
property.setOccupancyStatus(OccupancyStatus.OCCUPIED);
propertyRepository.save(property);
```

---

## Tenant Property Assignment

### CustomerPropertyAssignment Entity

Links customers (tenants, owners, delegated users) to properties with specific roles.

```java
@Entity
@Table(name = "customer_property_assignments")
public class CustomerPropertyAssignment {
    @Id private Long id;

    @ManyToOne
    private Customer customer;  // The tenant/owner/user

    @ManyToOne
    private Property property;  // The property

    @Enumerated(EnumType.STRING)
    private AssignmentType assignmentType;  // TENANT, OWNER, DELEGATED

    private LocalDate startDate;  // When assignment begins
    private LocalDate endDate;    // When assignment ends (if applicable)
    private Boolean isPrimary;    // Is this the primary tenant?

    // PayProp integration
    private String paypropInvoiceId;
    private String syncStatus;
}
```

### Assignment Types

| Type | Description | Example |
|------|-------------|---------|
| **TENANT** | A tenant renting the property | John Smith rents Flat 3A |
| **OWNER** | Property owner | Jane Doe owns Building B |
| **DELEGATED** | Delegated access (e.g., property manager, relative) | Bob has access to view statements |

### When Assignments Are Created

1. **Tenant Assignment**:
   - Created when a lead is converted to tenant
   - `assignmentType = TENANT`
   - `startDate = move_in_date`
   - Links to CustomerPropertyAssignment
   - Creates Invoice for rent

2. **Owner Assignment**:
   - Created when property is added to portfolio
   - `assignmentType = OWNER`
   - Can have multiple owners with ownership_percentage

3. **Delegated Assignment**:
   - Created manually by owner or admin
   - `assignmentType = DELEGATED`
   - Limited permissions

### Key Queries

```sql
-- Get all properties for a tenant
SELECT p.* FROM properties p
JOIN customer_property_assignments cpa ON p.id = cpa.property_id
WHERE cpa.customer_id = ?
  AND cpa.assignment_type = 'TENANT'
  AND (cpa.end_date IS NULL OR cpa.end_date >= CURDATE());

-- Get current tenant for a property
SELECT c.* FROM customers c
JOIN customer_property_assignments cpa ON c.customer_id = cpa.customer_id
WHERE cpa.property_id = ?
  AND cpa.assignment_type = 'TENANT'
  AND cpa.is_primary = true
  AND (cpa.end_date IS NULL OR cpa.end_date >= CURDATE());

-- Get all properties owned by a customer
SELECT p.* FROM properties p
JOIN customer_property_assignments cpa ON p.id = cpa.property_id
WHERE cpa.customer_id = ?
  AND cpa.assignment_type = 'OWNER';
```

---

## Dashboard Integration

### Employee Dashboard

**Location**: `/employee/dashboard`
**Template**: `src/main/resources/templates/employee/dashboard.html`

**Displays**:
- Overview of all letting instructions across all properties
- Active leases count
- Properties being advertised
- Upcoming viewings
- Lead conversion metrics

**Key Widgets**:

```html
<!-- Letting Pipeline Summary -->
<div class="row">
    <div class="col-md-3">
        <div class="card bg-info text-white">
            <h3>[[${instructionReceivedCount}]]</h3>
            <p>New Instructions</p>
        </div>
    </div>
    <div class="col-md-3">
        <div class="card bg-primary text-white">
            <h3>[[${advertisingCount}]]</h3>
            <p>Being Advertised</p>
        </div>
    </div>
    <div class="col-md-3">
        <div class="card bg-warning text-white">
            <h3>[[${offerAcceptedCount}]]</h3>
            <p>Offers Accepted</p>
        </div>
    </div>
    <div class="col-md-3">
        <div class="card bg-success text-white">
            <h3>[[${activeLeaseCount}]]</h3>
            <p>Active Leases</p>
        </div>
    </div>
</div>

<!-- Quick Access Links -->
<a href="/employee/letting-instruction/workspace">Full Workspace</a>
<a href="/employee/letting-instruction/pipeline">Kanban Pipeline</a>
<a href="/employee/property-vacancy/dashboard">Vacancy Dashboard</a>
```

### Property Vacancy Dashboard

**Location**: `/employee/property-vacancy/dashboard`
**Template**: `src/main/resources/templates/employee/property-vacancy/dashboard.html`

**Purpose**: Kanban-style view of properties moving through the letting pipeline

**Columns**:
1. **Instruction Received** - Properties where owner has given instruction
2. **Advertising** - Properties being actively marketed
3. **Offer Accepted** - Properties with accepted offers
4. **Active Lease** - Properties with current tenants

**Card Content**:
- Property address
- Target/actual rent
- Number of enquiries
- Number of viewings
- Days advertising

### Property Owner Dashboard

**Location**: `/property-owner/dashboard`
**Template**: `src/main/resources/templates/property-owner/dashboard.html`

**Filtered View**: Property owners only see THEIR properties

**Displays**:
- **My Portfolios** - List of portfolios they own
- **Properties by Status** - How many properties are:
  - Occupied (active lease)
  - Being marketed (advertising)
  - Vacant (available)
  - Under notice (tenant gave notice)
- **Maintenance Overview** - Open issues, emergency tickets
- **My Blocks** - Block management (if applicable)
- **Financial Summary** - Rental income, expenses

**Key Features**:
```html
<!-- Portfolio Cards -->
<div th:each="portfolio : ${portfolios}">
    <div class="card">
        <h5>[[${portfolio.name}]]</h5>
        <p>Properties: [[${portfolioPropertyCounts[portfolio.id]}]]</p>

        <!-- Letting Status for Portfolio -->
        <small>Active Leases: [[${activeLeasesByPortfolio[portfolio.id]}]]</small>
        <small>Being Marketed: [[${advertisingByPortfolio[portfolio.id]}]]</small>
    </div>
</div>

<!-- Quick Actions -->
<a href="/property-owner/properties">View All Properties</a>
<a href="/property-owner/tenants">Manage Tenants</a>
<a href="/property-owner/financials">Financial Summary</a>
<a href="/property-owner/maintenance">Maintenance Issues</a>
```

**Authorization**: Uses `CustomerPropertyAssignment` to filter:
```java
// PropertyOwnerController
List<Property> properties = propertyService.findPropertiesByOwner(customer.getCustomerId());

// Under the hood:
SELECT p.* FROM properties p
JOIN customer_property_assignments cpa ON p.id = cpa.property_id
WHERE cpa.customer_id = ?
  AND cpa.assignment_type = 'OWNER'
```

### Tenant Dashboard

**Location**: `/tenant/dashboard`
**Template**: `src/main/resources/templates/tenant/dashboard.html`

**Filtered View**: Tenants only see the property/properties they rent

**Displays**:
- **My Property** - Details of property they're renting
- **Lease Information** - Start date, end date, rent amount
- **Payment History** - Rent payments made
- **Maintenance Requests** - Open and closed tickets
- **Documents** - Lease agreement, inventory, etc.
- **Contact Information** - Property manager, emergency contacts

**Key Features**:
```html
<!-- Property Card -->
<div class="card tenant-welcome">
    <h3>Welcome to [[${property.addressLine1}]]</h3>
    <p>Your lease: [[${leaseStart}]] to [[${leaseEnd}]]</p>
    <p>Monthly rent: £[[${rentAmount}]]</p>
</div>

<!-- Maintenance Quick Actions -->
<a href="/tenant/maintenance/new" class="btn btn-primary">
    Report Maintenance Issue
</a>

<!-- Rent Payment Status -->
<div class="card">
    <h5>Rent Payment Status</h5>
    <span class="badge badge-success">Paid</span>
    <p>Next payment due: [[${nextPaymentDate}]]</p>
</div>
```

**Authorization**: Uses `CustomerPropertyAssignment` to filter:
```java
// TenantController
Property property = propertyService.findPropertyByTenant(customer.getCustomerId());

// Under the hood:
SELECT p.* FROM properties p
JOIN customer_property_assignments cpa ON p.id = cpa.property_id
WHERE cpa.customer_id = ?
  AND cpa.assignment_type = 'TENANT'
  AND cpa.is_primary = true
  AND (cpa.end_date IS NULL OR cpa.end_date >= CURDATE())
```

---

## Customer Portal Visibility

### Visibility Matrix

| Feature | Employee | Property Owner | Tenant | Delegated User |
|---------|----------|----------------|--------|----------------|
| **All Properties** | ✅ All | ✅ Owned only | ❌ | ✅ Assigned only |
| **Letting Instructions** | ✅ All | ✅ For owned properties | ❌ | ❌ |
| **Lead Pipeline** | ✅ Full access | ✅ For owned properties (read-only) | ❌ | ❌ |
| **Property Viewings** | ✅ Full access | ✅ For owned properties | ❌ | ❌ |
| **Tenant Management** | ✅ All tenants | ✅ Own tenants | ❌ | ✅ Assigned tenants |
| **Financial Statements** | ✅ All | ✅ Own statements | ✅ Own statements | ✅ Assigned properties |
| **Maintenance Issues** | ✅ All | ✅ Own properties | ✅ Own property | ✅ Assigned properties |
| **Block Management** | ✅ Full CRUD | ✅ Own blocks CRUD | ❌ | ✅ View only |
| **Portfolio Management** | ✅ All | ✅ Own portfolios | ❌ | ✅ View only |

### How Visibility Is Enforced

#### Property Owner Portal

```java
@Controller
@RequestMapping("/property-owner")
public class PropertyOwnerController {

    @GetMapping("/letting-instructions")
    public String viewLettingInstructions(Model model, Authentication auth) {
        // Get logged-in customer
        Customer customer = customerService.getLoggedInCustomer(auth);

        // Get only their properties
        List<Property> ownedProperties = propertyService
            .findPropertiesByOwner(customer.getCustomerId());

        // Get letting instructions for owned properties only
        List<LettingInstruction> instructions = lettingInstructionService
            .findByProperties(ownedProperties);

        model.addAttribute("instructions", instructions);
        return "property-owner/letting-instructions";
    }
}
```

#### Tenant Portal

```java
@Controller
@RequestMapping("/tenant")
public class TenantController {

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication auth) {
        // Get logged-in customer
        Customer customer = customerService.getLoggedInCustomer(auth);

        // Get only the property they're renting (via assignment)
        Property rentedProperty = customerPropertyAssignmentRepository
            .findPropertyByTenantId(customer.getCustomerId());

        // Get their active lease
        Invoice lease = invoiceService
            .findActiveLeaseByTenant(customer.getCustomerId());

        // Get maintenance issues for their property only
        List<Ticket> maintenanceIssues = ticketService
            .findByPropertyAndCustomer(rentedProperty.getId(), customer.getCustomerId());

        model.addAttribute("property", rentedProperty);
        model.addAttribute("lease", lease);
        model.addAttribute("maintenanceIssues", maintenanceIssues);

        return "tenant/dashboard";
    }
}
```

### Authorization Checks

Every controller endpoint checks:

1. **User Type**:
   ```java
   if (customer.getCustomerType() == CustomerType.PROPERTY_OWNER) {
       // Allow owner actions
   } else if (customer.getCustomerType() == CustomerType.TENANT) {
       // Allow tenant actions
   }
   ```

2. **Property Ownership**:
   ```java
   boolean ownsProperty = customerPropertyAssignmentRepository
       .existsByCustomerAndPropertyAndType(
           customerId,
           propertyId,
           AssignmentType.OWNER
       );
   if (!ownsProperty) {
       throw new AccessDeniedException("You don't own this property");
   }
   ```

3. **Tenant Assignment**:
   ```java
   boolean rentingProperty = customerPropertyAssignmentRepository
       .existsByCustomerAndPropertyAndType(
           customerId,
           propertyId,
           AssignmentType.TENANT
       );
   if (!rentingProperty) {
       throw new AccessDeniedException("You don't rent this property");
   }
   ```

---

## Data Flow Diagrams

### Complete Letting Cycle Data Flow

```
┌────────────────────────────────────────────────────────────────┐
│ 1. PROPERTY OWNER GIVES INSTRUCTION                            │
└────────────────────────────────────────────────────────────────┘
                            │
                            ▼
                    Employee receives instruction
                            │
                            ▼
                ┌──────────────────────┐
                │ CREATE               │
                │ LettingInstruction   │
                │                      │
                │ • property_id        │
                │ • status =           │
                │   INSTRUCTION_       │
                │   RECEIVED           │
                │ • target_rent        │
                │ • expected_vacancy   │
                └──────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│ 2. PROPERTY PREPARATION                                         │
└────────────────────────────────────────────────────────────────┘
                            │
                    Status = PREPARING
                            │
                  Auto-create Tasks:
                  • Cleaning
                  • Repairs
                  • Photography
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│ 3. START ADVERTISING                                            │
└────────────────────────────────────────────────────────────────┘
                            │
                    Status = ADVERTISING
                            │
                    Property listed publicly
                    Enquiry form enabled
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│ 4. LEADS START COMING IN                                        │
└────────────────────────────────────────────────────────────────┘
                            │
                    For each enquiry:
                            │
                ┌──────────────────────┐
                │ CREATE Lead          │
                │                      │
                │ • lead_type =        │
                │   PROPERTY_RENTAL    │
                │ • property_id        │
                │ • letting_instruction│
                │   _id                │
                │ • status = enquiry   │
                │ • name, email, phone │
                │ • budget, move_in    │
                └──────────────────────┘
                            │
            instruction.incrementEnquiryCount()
                            │
                Send confirmation email
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│ 5. SCHEDULE VIEWINGS                                            │
└────────────────────────────────────────────────────────────────┘
                            │
                    Status = VIEWINGS_IN_PROGRESS
                            │
                    For each viewing:
                            │
                ┌──────────────────────┐
                │ CREATE               │
                │ PropertyViewing      │
                │                      │
                │ • lead_id            │
                │ • property_id        │
                │ • letting_instruction│
                │   _id                │
                │ • scheduled_datetime │
                │ • status = SCHEDULED │
                └──────────────────────┘
                            │
            instruction.incrementViewingCount()
                            │
                Add to Google Calendar
                Send confirmation email
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│ 6. VIEWING COMPLETED                                            │
└────────────────────────────────────────────────────────────────┘
                            │
                    Viewing.complete(feedback)
                            │
                    Lead.status = viewing-completed
                            │
                    If interested:
                        Lead.status = interested
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│ 7. APPLICATION & REFERENCING                                    │
└────────────────────────────────────────────────────────────────┘
                            │
                    Lead.status = application-submitted
                            │
                    Upload documents to Google Drive
                            │
                    Lead.status = referencing
                            │
                    References checked
                            │
                    Lead.status = in-contracts
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│ 8. OFFER ACCEPTED                                               │
└────────────────────────────────────────────────────────────────┘
                            │
                    Instruction.status = OFFER_ACCEPTED
                            │
                    Prepare contracts
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│ 9. CONVERSION TO TENANT                                         │
└────────────────────────────────────────────────────────────────┘
                            │
                    Lead.status = converted
                            │
              LeadConversionService.convert(leadId)
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│ CREATE       │   │ CREATE       │   │ CREATE       │
│ Customer     │   │ CustomerProp │   │ Invoice      │
│              │   │ Assignment   │   │ (Lease)      │
│ • type =     │   │              │   │              │
│   TENANT     │   │ • customer_id│   │ • customer_id│
│ • name       │   │ • property_id│   │ • property_id│
│ • email      │   │ • type =     │   │ • type = Rent│
│ • move_in    │   │   TENANT     │   │ • amount     │
└──────────────┘   │ • start_date │   │ • frequency  │
                   │ • is_primary │   └──────────────┘
                   └──────────────┘
                            │
                            ▼
                ┌──────────────────────┐
                │ UPDATE               │
                │ LettingInstruction   │
                │                      │
                │ • status =           │
                │   ACTIVE_LEASE       │
                │ • tenant_id          │
                │ • lease_start_date   │
                │ • actual_rent        │
                │ • advertising_end_   │
                │   date = TODAY       │
                └──────────────────────┘
                            │
                    Calculate metrics:
                    • days_to_fill
                    • days_vacant
                    • conversion_rate
                            │
                            ▼
                ┌──────────────────────┐
                │ UPDATE Property      │
                │                      │
                │ • occupancy_status = │
                │   OCCUPIED           │
                └──────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│ 10. DASHBOARDS UPDATED                                          │
└────────────────────────────────────────────────────────────────┘
        │                   │                   │
        ▼                   ▼                   ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│ Employee     │   │ Property     │   │ Tenant       │
│ Dashboard    │   │ Owner        │   │ Dashboard    │
│              │   │ Dashboard    │   │              │
│ • Active     │   │              │   │ • My Property│
│   leases +1  │   │ • Property   │   │ • Lease Info │
│ • Advertising│   │   now        │   │ • Rent Due   │
│   -1         │   │   occupied   │   │ • Maintenance│
│              │   │ • New tenant │   │   Form       │
└──────────────┘   │   added      │   └──────────────┘
                   └──────────────┘
```

---

## Implementation Status

### ✅ Completed

| Component | Status | Location |
|-----------|--------|----------|
| **LettingInstruction Entity** | ✅ Complete | `entity/LettingInstruction.java` |
| **Lead Extension (Property)** | ✅ Complete | `entity/Lead.java` |
| **PropertyViewing Entity** | ✅ Complete | `entity/PropertyViewing.java` |
| **PropertyVacancyTask Entity** | ✅ Complete | `entity/PropertyVacancyTask.java` |
| **CustomerPropertyAssignment** | ✅ Complete | `entity/CustomerPropertyAssignment.java` |
| **Employee Vacancy Dashboard** | ✅ Complete | `templates/employee/property-vacancy/dashboard.html` |
| **Property Owner Dashboard** | ✅ Complete | `templates/property-owner/dashboard.html` |
| **Tenant Dashboard** | ✅ Complete | `templates/tenant/dashboard.html` |
| **Block Management (Owners)** | ✅ Complete | `controller/PropertyOwnerBlockController.java` |
| **LeadConversionService** | ✅ Complete | `service/property/LeadConversionServiceImpl.java` |

### 🔄 In Progress

| Component | Status | Next Steps |
|-----------|--------|------------|
| **LettingInstructionService** | 🔄 Partially complete | Add metrics calculation, status workflow |
| **PropertyViewingService** | 🔄 Partially complete | Add Google Calendar sync |
| **Email Automation** | 🔄 Partially complete | Add all email templates |

### 🔜 Upcoming

| Component | Priority | Description |
|-----------|----------|-------------|
| **Owner Instruction View** | 🟡 Medium | Property owners see letting instructions for their properties |
| **Tenant Move-In Portal** | 🟡 Medium | Welcome page for new tenants with onboarding tasks |
| **Public Property Listing API** | 🟢 High | API for public property search (for website) |
| **External Portal Integration** | 🔵 Low | OpenRent, SpareRoom, Rightmove |

---

## Next Steps

### For Property Owner Portal Integration

**Goal**: Allow property owners to see the status of letting instructions for their properties

**Steps**:

1. **Create PropertyOwnerLettingInstructionController**:
   ```java
   @GetMapping("/property-owner/letting-status")
   public String viewLettingStatus(Model model, Authentication auth) {
       Customer owner = getLoggedInCustomer(auth);

       // Get owner's properties
       List<Property> properties = findPropertiesByOwner(owner);

       // Get letting instructions for those properties
       List<LettingInstruction> instructions =
           lettingInstructionService.findByProperties(properties);

       model.addAttribute("instructions", instructions);
       return "property-owner/letting-status";
   }
   ```

2. **Create Owner View Template** (`property-owner/letting-status.html`):
   - Show current instruction status for each property
   - Display number of enquiries/viewings
   - Show days advertising
   - Link to view leads (read-only)

3. **Add to Owner Dashboard**:
   ```html
   <div class="card">
       <h5>Properties Being Let</h5>
       <p th:if="${advertisingCount > 0}">
           <span th:text="${advertisingCount}"></span> properties being marketed
       </p>
       <a href="/property-owner/letting-status">View Details</a>
   </div>
   ```

### For Tenant Dashboard Enhancement

**Goal**: Show tenant their property details and letting history

**Steps**:

1. **Add Letting History to Tenant Dashboard**:
   - Show when they moved in
   - Show original listing details (what attracted them)
   - Show viewing history (if they viewed the property via system)

2. **Link to Original Lead**:
   ```java
   // Find if tenant has a linked lead
   Lead originalLead = leadRepository.findByConvertedToCustomer(tenant);

   if (originalLead != null) {
       model.addAttribute("viewingHistory", originalLead.getPropertyViewings());
       model.addAttribute("moveInDate", tenant.getMoveInDate());
   }
   ```

---

## Conclusion

This system provides a **complete end-to-end solution** for managing property lettings from initial instruction through to active tenancy. It integrates:

✅ **Letting Instructions** - Full lifecycle tracking
✅ **Lead Pipeline** - From enquiry to conversion
✅ **Property Viewings** - Scheduling and feedback
✅ **Tenant Assignment** - Via CustomerPropertyAssignment
✅ **Multi-User Dashboards** - Employee, Owner, Tenant views
✅ **Customer Portal** - Filtered visibility based on role
✅ **Authorization** - Secure data access controls

The system is **production-ready** for core functionality, with extensions available for public listing APIs and external portal integrations.

---

**Document Version**: 1.0
**Last Updated**: 2025-11-09
**Status**: Complete
**Related Docs**:
- `PROPERTY_LEADS_SYSTEM_PLAN.md` - Original property leads plan
- `LEADS_QUICK_REFERENCE.md` - Quick reference for developers
- `BLOCK_FUNCTIONALITY_FOR_OWNERS.md` - Block management for owners


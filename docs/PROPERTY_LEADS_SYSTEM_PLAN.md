# Property Leads & Tenant Lifecycle Management System
## Comprehensive Implementation Plan

**Version:** 1.0
**Date:** 2025-11-05
**Status:** Phase 2 Complete - Entities Created

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [System Architecture](#system-architecture)
3. [Database Schema](#database-schema)
4. [Entity Model](#entity-model)
5. [Workflow Design](#workflow-design)
6. [Implementation Phases](#implementation-phases)
7. [API Specifications](#api-specifications)
8. [UI/UX Design](#uiux-design)
9. [Email Automation](#email-automation)
10. [Future Integrations](#future-integrations)
11. [Testing Strategy](#testing-strategy)

---

## Executive Summary

### Project Goals

Build a comprehensive tenant lifecycle management system that tracks:
- Property vacancy status (notice given, advertising, available)
- Lead generation and enquiry management
- Viewing scheduling and tracking
- Lead progression through rental workflow
- Automated tenant conversion and lease creation
- Marketing automation and notifications

### Key Features

✅ **Completed (Phase 1-2)**:
- Database migration script with all tables
- Entity classes for all domain objects
- Property vacancy tracking fields
- Lead extension for property rentals
- Viewing and task management entities
- Communication tracking entity

⏳ **In Progress**:
- Comprehensive documentation

🔜 **Upcoming (Phase 3-8)**:
- Service layer implementation
- Controller and API endpoints
- Frontend UI components
- Email automation
- Public property listing API
- External integrations (OpenRent, SpareRoom)

### Benefits

1. **Unified System**: All property rental leads in one place
2. **Automated Workflows**: Auto-create tasks on notice given
3. **Better Tracking**: Full visibility of lead progression
4. **Faster Lettings**: Streamlined viewing → application → tenant conversion
5. **Data-Driven**: Analytics on conversion rates and time-to-let
6. **Owner Communication**: Automated notifications to property owners

---

## System Architecture

### Technology Stack

- **Backend**: Java 17+ with Spring Boot
- **Database**: MySQL 8.0+
- **ORM**: JPA/Hibernate
- **Frontend**: Thymeleaf templates, HTML5, CSS3, JavaScript
- **Email**: Gmail API integration (existing)
- **Calendar**: Google Calendar API (existing)
- **Storage**: Google Drive (existing)

### Architecture Pattern

**3-Tier Architecture**:
```
┌─────────────────────────────────────┐
│        Presentation Layer           │
│  (Controllers + Thymeleaf Views)    │
└─────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────┐
│         Business Logic Layer        │
│  (Services + Business Rules)        │
└─────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────┐
│          Data Access Layer          │
│    (Repositories + Entities)        │
└─────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────┐
│           MySQL Database            │
└─────────────────────────────────────┘
```

### Design Decisions

1. **Extend Existing Lead Entity** (vs separate PropertyLead entity)
   - ✅ Reuses 80% of existing infrastructure
   - ✅ Maintains consistency with existing patterns
   - ✅ Simpler codebase with single lead system
   - ⚠️ Adds property-specific fields with lead_type discriminator

2. **Customer Entity Only for Tenants** (vs both Customer + Tenant)
   - ✅ Single source of truth
   - ✅ Matches PayProp integration pattern
   - ✅ Simpler data model

3. **Manual Advertising Activation** (vs automatic)
   - ✅ Gives control to staff
   - ✅ Allows time for property preparation
   - ✅ Triggers automated notifications and tasks

4. **Standard Workflow Stages**:
   - enquiry → viewing-scheduled → viewing-completed → interested →
   - application-submitted → referencing → in-contracts → converted

---

## Database Schema

### New Tables Created

#### 1. Property Vacancy Fields (extends `properties` table)

```sql
ALTER TABLE properties ADD COLUMN:
- occupancy_status VARCHAR(20) DEFAULT 'OCCUPIED'
- notice_given_date DATE
- expected_vacancy_date DATE
- advertising_start_date DATE
- available_from_date DATE
- last_occupancy_change DATETIME
```

**Indexes**:
- `idx_properties_occupancy_status` on `occupancy_status`
- `idx_properties_expected_vacancy` on `expected_vacancy_date`
- `idx_properties_available_from` on `available_from_date`

#### 2. Property Lead Fields (extends `trigger_lead` table)

```sql
ALTER TABLE trigger_lead ADD COLUMN:
- property_id BIGINT (FK → properties.id)
- lead_type VARCHAR(20) DEFAULT 'BUSINESS'
- desired_move_in_date DATE
- budget_min DECIMAL(10,2)
- budget_max DECIMAL(10,2)
- number_of_occupants INT
- has_pets BOOLEAN DEFAULT FALSE
- has_guarantor BOOLEAN DEFAULT FALSE
- employment_status VARCHAR(50)
- lead_source VARCHAR(100)
- converted_at DATETIME
- converted_to_customer_id BIGINT (FK → customers.customer_id)
```

**Updated Status Constraint**:
```sql
CHECK (status IN (
    -- Business lead statuses
    'meeting-to-schedule', 'scheduled', 'archived', 'success', 'assign-to-sales',
    -- Property rental statuses
    'enquiry', 'viewing-scheduled', 'viewing-completed', 'interested',
    'application-submitted', 'referencing', 'in-contracts', 'converted', 'lost'
))
```

**Indexes**:
- `idx_lead_property` on `property_id`
- `idx_lead_type` on `lead_type`
- `idx_lead_status` on `status`
- `idx_lead_source` on `lead_source`
- `idx_lead_move_in_date` on `desired_move_in_date`

#### 3. Property Viewings (new table `property_viewings`)

```sql
CREATE TABLE property_viewings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lead_id INT NOT NULL (FK → trigger_lead),
    property_id BIGINT NOT NULL (FK → properties),
    scheduled_datetime DATETIME NOT NULL,
    duration_minutes INT DEFAULT 30,
    viewing_type VARCHAR(50) DEFAULT 'IN_PERSON',
    status VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED',
    assigned_to_user_id BIGINT (FK → users),
    attendees TEXT,
    notes TEXT,
    feedback TEXT,
    interested_level VARCHAR(20),
    google_calendar_event_id VARCHAR(255),
    reminder_sent BOOLEAN DEFAULT FALSE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    created_by BIGINT (FK → users)
)
```

**Status Values**: SCHEDULED, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW, RESCHEDULED

**Viewing Types**: IN_PERSON, VIRTUAL

**Interest Levels**: VERY_INTERESTED, INTERESTED, NEUTRAL, NOT_INTERESTED

**Indexes**:
- `idx_viewing_scheduled_datetime` on `scheduled_datetime`
- `idx_viewing_property` on `property_id`
- `idx_viewing_lead` on `lead_id`
- `idx_viewing_status` on `status`

#### 4. Property Vacancy Tasks (new table `property_vacancy_tasks`)

```sql
CREATE TABLE property_vacancy_tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    property_id BIGINT NOT NULL (FK → properties),
    task_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    due_date DATE,
    completed_date DATETIME,
    assigned_to_user_id BIGINT (FK → users),
    created_by_user_id BIGINT (FK → users),
    auto_created BOOLEAN DEFAULT FALSE,
    trigger_event VARCHAR(50),
    notes TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME
)
```

**Task Types**: INSPECTION, PHOTOGRAPHY, REPAIRS, CLEANING, LISTING_CREATION, KEY_HANDOVER, etc.

**Status Values**: PENDING, IN_PROGRESS, COMPLETED, CANCELLED

**Priority Values**: LOW, MEDIUM, HIGH, URGENT

**Trigger Events**: NOTICE_GIVEN, VACANCY_APPROACHING, etc.

**Indexes**:
- `idx_vacancy_task_property` on `property_id`
- `idx_vacancy_task_status` on `status`
- `idx_vacancy_task_due_date` on `due_date`
- `idx_vacancy_task_assigned_user` on `assigned_to_user_id`

#### 5. Lead Communications (new table `lead_communications`)

```sql
CREATE TABLE lead_communications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lead_id INT NOT NULL (FK → trigger_lead),
    communication_type VARCHAR(50) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    subject VARCHAR(500),
    message_content TEXT,
    sent_at DATETIME,
    delivered_at DATETIME,
    opened_at DATETIME,
    clicked_at DATETIME,
    replied_at DATETIME,
    status VARCHAR(50),
    email_template_id INT (FK → email_template),
    gmail_message_id VARCHAR(255),
    created_by_user_id BIGINT (FK → users),
    created_at DATETIME NOT NULL
)
```

**Communication Types**: EMAIL, SMS, CALL, WHATSAPP, IN_PERSON

**Direction**: INBOUND, OUTBOUND

**Status Values**: SENT, DELIVERED, FAILED, BOUNCED, OPENED, REPLIED

**Indexes**:
- `idx_comm_lead` on `lead_id`
- `idx_comm_sent_at` on `sent_at`
- `idx_comm_type` on `communication_type`

### Database Views

#### v_properties_requiring_attention
Shows properties needing attention (notice given, upcoming vacancy, advertising):
```sql
SELECT
    p.id, p.address, p.occupancy_status,
    p.expected_vacancy_date,
    DATEDIFF(p.expected_vacancy_date, CURDATE()) AS days_until_vacant,
    COUNT(DISTINCT l.lead_id) AS enquiry_count,
    COUNT(DISTINCT v.id) AS viewing_count,
    COUNT(DISTINCT t.id) AS pending_task_count
FROM properties p
LEFT JOIN trigger_lead l ON l.property_id = p.id AND l.lead_type = 'PROPERTY_RENTAL'
LEFT JOIN property_viewings v ON v.property_id = p.id AND v.status IN ('SCHEDULED', 'CONFIRMED')
LEFT JOIN property_vacancy_tasks t ON t.property_id = p.id AND t.status IN ('PENDING', 'IN_PROGRESS')
WHERE p.occupancy_status IN ('NOTICE_GIVEN', 'ADVERTISING', 'AVAILABLE')
GROUP BY p.id
```

#### v_lead_conversion_funnel
Provides metrics on lead conversion rates:
```sql
SELECT
    COUNT(*) AS total_leads,
    SUM(CASE WHEN status = 'enquiry' THEN 1 ELSE 0 END) AS enquiries,
    SUM(CASE WHEN status IN ('viewing-scheduled', 'viewing-completed') THEN 1 ELSE 0 END) AS viewings,
    SUM(CASE WHEN status = 'converted' THEN 1 ELSE 0 END) AS converted,
    ROUND(100.0 * SUM(CASE WHEN status = 'converted' THEN 1 ELSE 0 END) / COUNT(*), 2) AS conversion_rate
FROM trigger_lead
WHERE lead_type = 'PROPERTY_RENTAL'
AND created_at >= DATE_SUB(CURDATE(), INTERVAL 90 DAY)
```

### Entity Relationship Diagram (ERD)

```
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│  Properties  │◄──────│ trigger_lead │──────►│   Customer   │
│              │       │ (Lead)       │       │              │
│ + occupancy  │       │ + lead_type  │       │ + type       │
│   _status    │       │ + property_id│       │   = TENANT   │
│ + notice_    │       │ + budget     │       │              │
│   given_date │       │ + has_pets   │       │              │
└──────────────┘       └──────────────┘       └──────────────┘
       │                      │                       │
       │                      │                       │
       │              ┌──────────────┐                │
       │              │  property_   │                │
       └──────────────┤  viewings    │                │
                      │              │                │
                      │ + scheduled_ │                │
                      │   datetime   │                │
                      │ + status     │                │
                      └──────────────┘                │
                             │                        │
                             │                        │
                      ┌──────────────┐                │
                      │  property_   │                │
                      │  vacancy_    │                │
                      │  tasks       │                │
                      │              │                │
                      │ + task_type  │                │
                      │ + status     │                │
                      └──────────────┘                │
                                                      │
                      ┌──────────────┐                │
                      │  lead_       │                │
                      │  communica-  │◄───────────────┘
                      │  tions       │
                      │              │
                      │ + type       │
                      │ + direction  │
                      └──────────────┘
```

---

## Entity Model

### Core Entities

#### 1. OccupancyStatus (Enum)
```java
public enum OccupancyStatus {
    OCCUPIED("Currently Occupied"),
    NOTICE_GIVEN("Notice Given"),
    ADVERTISING("Being Advertised"),
    AVAILABLE("Available Now"),
    MAINTENANCE("Under Maintenance"),
    OFF_MARKET("Off Market");
}
```

**Helper Methods**:
- `isAvailableForLetting()` - returns true for ADVERTISING and AVAILABLE
- `requiresMarketingAttention()` - returns true for NOTICE_GIVEN and ADVERTISING

#### 2. LeadType (Enum)
```java
public enum LeadType {
    BUSINESS("Business Lead"),
    PROPERTY_RENTAL("Property Rental Lead");
}
```

**Helper Methods**:
- `isPropertyLead()` - returns true if PROPERTY_RENTAL

#### 3. Lead (Extended Entity)

**Original Fields** (unchanged):
- `leadId`, `name`, `phone`, `status`
- `meetingId`, `googleDrive`, `googleDriveFolderId`
- `manager`, `employee`, `customer`
- `leadActions`, `files`, `googleDriveFiles`
- `createdAt`

**New Property Lead Fields**:
- `property` - Property this lead is enquiring about
- `leadType` - BUSINESS or PROPERTY_RENTAL
- `desiredMoveInDate` - When tenant wants to move in
- `budgetMin` / `budgetMax` - Monthly rent budget range
- `numberOfOccupants` - How many people will live there
- `hasPets` - Whether tenant has pets
- `hasGuarantor` - Whether tenant has a guarantor
- `employmentStatus` - EMPLOYED, SELF_EMPLOYED, STUDENT, RETIRED, etc.
- `leadSource` - website, openrent, spareroom, referral, walk-in, phone
- `convertedAt` - Timestamp when converted to tenant
- `convertedToCustomer` - Customer record created from this lead
- `propertyViewings` - List of viewing appointments

**New Status Values**:
- Property rental: `enquiry`, `viewing-scheduled`, `viewing-completed`, `interested`, `application-submitted`, `referencing`, `in-contracts`, `converted`, `lost`
- Business (existing): `meeting-to-schedule`, `scheduled`, `archived`, `success`, `assign-to-sales`

**Helper Methods**:
- `isPropertyLead()` - Check if this is a property rental lead
- `isConverted()` - Check if lead has been converted to tenant
- `getBudgetRangeDisplay()` - Get formatted budget string

#### 4. Property (Extended Entity)

**New Vacancy Fields**:
- `occupancyStatus` - Current occupancy status (enum)
- `noticeGivenDate` - Date when tenant gave notice
- `expectedVacancyDate` - Expected date property will be vacant
- `advertisingStartDate` - Date advertising began
- `availableFromDate` - Date available for new tenancy
- `lastOccupancyChange` - Timestamp of last status change

**Helper Methods**:
- `isAvailableForLetting()` - Check if property can accept enquiries
- `requiresMarketingAttention()` - Check if needs marketing action
- `markNoticeGiven(noticeDate, expectedVacancy)` - Record notice
- `startAdvertising()` - Begin advertising
- `markAvailable(availableFrom)` - Mark as available
- `markOccupied()` - Mark as occupied (clear vacancy fields)
- `getDaysUntilVacancy()` - Calculate days until expected vacancy

#### 5. PropertyViewing (New Entity)

**Fields**:
- `id` - Primary key
- `lead` - Lead who is viewing
- `property` - Property being viewed
- `scheduledDatetime` - Date/time of viewing
- `durationMinutes` - Expected duration (default 30)
- `viewingType` - IN_PERSON or VIRTUAL
- `status` - SCHEDULED, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW, RESCHEDULED
- `assignedToUser` - Staff member conducting viewing
- `attendees` - JSON array of attendee info
- `notes` - Notes before viewing
- `feedback` - Feedback after viewing
- `interestedLevel` - VERY_INTERESTED, INTERESTED, NEUTRAL, NOT_INTERESTED
- `googleCalendarEventId` - For calendar sync
- `reminderSent` - Whether reminder was sent
- `createdAt`, `updatedAt`, `createdBy` - Audit fields

**Helper Methods**:
- `isUpcoming()` - Check if viewing is in the future
- `isCompleted()` - Check if viewing happened
- `canBeRescheduled()` - Check if viewing can be moved
- `complete(feedback, interestedLevel)` - Mark as completed
- `cancel()` - Cancel viewing
- `markAsNoShow()` - Mark as no-show

#### 6. PropertyVacancyTask (New Entity)

**Fields**:
- `id` - Primary key
- `property` - Property this task relates to
- `taskType` - INSPECTION, PHOTOGRAPHY, REPAIRS, CLEANING, LISTING_CREATION, KEY_HANDOVER
- `title` - Task title
- `description` - Detailed description
- `status` - PENDING, IN_PROGRESS, COMPLETED, CANCELLED
- `priority` - LOW, MEDIUM, HIGH, URGENT
- `dueDate` - When task should be completed
- `completedDate` - When task was actually completed
- `assignedToUser` - User responsible
- `createdByUser` - User who created task
- `autoCreated` - Whether system auto-created
- `triggerEvent` - What triggered creation (NOTICE_GIVEN, VACANCY_APPROACHING)
- `notes` - Additional notes
- `createdAt`, `updatedAt` - Audit fields

**Helper Methods**:
- `isOverdue()` - Check if past due date
- `isCompleted()` - Check if completed
- `isPending()` - Check if pending
- `complete()` - Mark as completed
- `startProgress()` - Mark as in progress
- `cancel()` - Cancel task
- `isHighPriority()` - Check if HIGH or URGENT
- `getDaysUntilDue()` - Calculate days until due

#### 7. LeadCommunication (New Entity)

**Fields**:
- `id` - Primary key
- `lead` - Lead this communication relates to
- `communicationType` - EMAIL, SMS, CALL, WHATSAPP, IN_PERSON
- `direction` - INBOUND or OUTBOUND
- `subject` - Subject line (emails)
- `messageContent` - Message body or call notes
- `sentAt`, `deliveredAt`, `openedAt`, `clickedAt`, `repliedAt` - Timestamps
- `status` - SENT, DELIVERED, FAILED, BOUNCED, OPENED, REPLIED
- `emailTemplate` - Template used (if automated)
- `gmailMessageId` - Gmail message ID for sync
- `createdByUser` - User who initiated
- `createdAt` - Audit field

**Helper Methods**:
- `isEmail()` - Check if email communication
- `isOutbound()` - Check if outbound
- `wasOpened()` - Check if email was opened
- `wasReplied()` - Check if recipient replied
- `markAsDelivered()` - Update status
- `markAsOpened()` - Update status
- `markAsReplied()` - Update status
- `markAsFailed()` - Update status

---

## Workflow Design

### Property Vacancy Workflow

```
┌─────────────────┐
│    OCCUPIED     │ ◄─── Normal state, tenant living in property
└─────────────────┘
         │
         │ Tenant gives notice
         ▼
┌─────────────────┐
│  NOTICE_GIVEN   │ ◄─── Tasks auto-created:
└─────────────────┘      • Schedule inspection (2 weeks before vacancy)
         │               • Arrange photography
         │               • Property check
         ▼
┌─────────────────┐
│  ADVERTISING    │ ◄─── Manual activation by staff
└─────────────────┘      • Property listed on website
         │               • Sent to OpenRent/SpareRoom
         │               • Marketing materials prepared
         ▼
┌─────────────────┐
│   AVAILABLE     │ ◄─── Vacant and ready
└─────────────────┘      • Accepting viewings
         │               • Processing applications
         │
         │ Lead converts to tenant
         ▼
┌─────────────────┐
│    OCCUPIED     │ ◄─── Back to occupied state
└─────────────────┘
```

**Automated Actions on Notice Given**:
1. Set `occupancyStatus` = NOTICE_GIVEN
2. Record `noticeGivenDate` and `expectedVacancyDate`
3. Auto-create tasks:
   - **Property inspection** (due: 2 weeks before vacancy)
   - **Photography session** (due: 1 week before vacancy)
   - **Listing creation** (due: 3 days before advertising)
   - **Key handover arrangement** (due: vacancy date)
4. Send email to property owner
5. Notify lettings team

**Automated Actions on Start Advertising**:
1. Set `occupancyStatus` = ADVERTISING
2. Record `advertisingStartDate`
3. Publish property listing (internal website)
4. Send to external platforms (future: OpenRent, SpareRoom)
5. Enable online enquiry form
6. Email property owner with listing link

### Lead Progression Workflow

```
┌──────────┐     ┌────────────────┐     ┌────────────────┐
│ ENQUIRY  │────►│ VIEWING-       │────►│ VIEWING-       │
│          │     │ SCHEDULED      │     │ COMPLETED      │
└──────────┘     └────────────────┘     └────────────────┘
                                                 │
                                                 ▼
                                        ┌────────────────┐
                                        │  INTERESTED    │
                                        └────────────────┘
                                                 │
                                                 ▼
                                        ┌────────────────┐
                                        │ APPLICATION-   │
                                        │ SUBMITTED      │
                                        └────────────────┘
                                                 │
                                                 ▼
                                        ┌────────────────┐
                                        │  REFERENCING   │
                                        └────────────────┘
                                                 │
                                                 ▼
                                        ┌────────────────┐
                                        │ IN-CONTRACTS   │
                                        └────────────────┘
                                                 │
                                                 ▼
                                        ┌────────────────┐
                                        │   CONVERTED    │──► Creates Tenant
                                        └────────────────┘

                     ┌────────────────┐
                     │      LOST      │ ◄─── Can exit at any stage
                     └────────────────┘
```

**Status Descriptions**:

1. **enquiry**: Initial contact from prospective tenant
   - Auto-send confirmation email
   - Create lead record
   - Log communication

2. **viewing-scheduled**: Viewing appointment booked
   - Create PropertyViewing record
   - Add to Google Calendar
   - Send confirmation email
   - Schedule reminder (24h before)

3. **viewing-completed**: Viewing has occurred
   - Record feedback
   - Record interest level
   - Update lead status
   - If interested, prompt for application

4. **interested**: Prospect wants to apply
   - Send application form link
   - Request required documents
   - Set follow-up task

5. **application-submitted**: Formal application received
   - Store documents in Google Drive
   - Notify property owner
   - Begin reference checks

6. **referencing**: Reference checks in progress
   - Track reference check status
   - Chase outstanding references
   - Update property owner

7. **in-contracts**: Contracts being prepared
   - Generate tenancy agreement
   - Send to tenant for signature
   - Arrange deposit payment
   - Schedule move-in date

8. **converted**: Lead converted to tenant
   - Create Customer record (type=TENANT)
   - Create Invoice (lease agreement)
   - Create CustomerPropertyAssignment
   - Update property to OCCUPIED
   - Clear all vacancy dates
   - Archive lead with success

9. **lost**: Lead did not convert
   - Record reason for loss
   - Archive lead
   - Continue advertising property

### Lead Conversion Process

**Trigger**: Lead status changed to "converted"

**Steps** (LeadConversionService):

1. **Validate Conversion**:
   - Check lead is property rental type
   - Check property exists and is available
   - Check no duplicate tenant

2. **Create Customer Record**:
   ```java
   Customer tenant = new Customer();
   tenant.setCustomerType(CustomerType.TENANT);
   tenant.setFirstName(lead.getName());
   tenant.setEmail(lead.getEmail());
   tenant.setPhone(lead.getPhone());
   tenant.setMoveInDate(lead.getDesiredMoveInDate());
   tenant.setAssignedPropertyId(lead.getProperty().getId());
   // ... set other fields from lead
   customerRepository.save(tenant);
   ```

3. **Create Invoice (Lease Agreement)**:
   ```java
   Invoice lease = new Invoice();
   lease.setCustomer(tenant);
   lease.setProperty(lead.getProperty());
   lease.setInvoiceType("Rent");
   lease.setAmount(property.getMonthlyPayment());
   lease.setFrequency("Monthly");
   lease.setStartDate(tenant.getMoveInDate());
   lease.setEndDate(/* calculate from lease term */);
   lease.setSyncStatus("active");
   invoiceRepository.save(lease);
   ```

4. **Create Customer-Property Assignment**:
   ```java
   CustomerPropertyAssignment assignment = new CustomerPropertyAssignment();
   assignment.setCustomer(tenant);
   assignment.setProperty(lead.getProperty());
   assignment.setAssignmentType(AssignmentType.TENANT);
   assignment.setStartDate(tenant.getMoveInDate());
   assignment.setIsPrimary(true);
   assignmentRepository.save(assignment);
   ```

5. **Update Property Status**:
   ```java
   property.markOccupied();
   propertyRepository.save(property);
   ```

6. **Update Lead**:
   ```java
   lead.setStatus("converted");
   lead.setConvertedAt(LocalDateTime.now());
   lead.setConvertedToCustomer(tenant);
   leadRepository.save(lead);
   ```

7. **Create Lead Action** (audit trail):
   ```java
   LeadAction action = new LeadAction();
   action.setLead(lead);
   action.setAction("Converted to tenant: " + tenant.getFirstName());
   action.setTimestamp(LocalDateTime.now());
   leadActionRepository.save(action);
   ```

8. **Send Notifications**:
   - Email property owner: "New tenant confirmed"
   - Email tenant: "Welcome and move-in details"
   - Email lettings team: "Conversion completed"

9. **Create Move-In Tasks**:
   - Key handover
   - Inventory check
   - Meter readings
   - Welcome pack delivery

---

## Implementation Phases

### ✅ Phase 1: Database Migration (COMPLETED)

**Files Created**:
- `V18__Add_Property_Leads_System.sql`

**Changes**:
- Extended `properties` table with vacancy fields
- Extended `trigger_lead` table with property fields
- Created `property_viewings` table
- Created `property_vacancy_tasks` table
- Created `lead_communications` table
- Created database views for reporting
- Added all indexes for performance

### ✅ Phase 2: Entity Classes (COMPLETED)

**Files Created**:
1. `OccupancyStatus.java` - Enum for property occupancy
2. `LeadType.java` - Enum for lead type discrimination
3. `PropertyViewing.java` - Viewing appointments entity
4. `PropertyVacancyTask.java` - Vacancy task management entity
5. `LeadCommunication.java` - Communication tracking entity

**Files Modified**:
1. `Lead.java` - Added property rental fields
2. `Property.java` - Added vacancy tracking fields

**Key Features**:
- Full JPA annotations
- Validation constraints
- Helper methods on entities
- Audit fields (createdAt, updatedAt)
- Relationship mappings
- @PrePersist and @PreUpdate lifecycle methods

### 🔄 Phase 3: Service Layer (IN PROGRESS)

**Next Steps**:

1. **Create Repository Interfaces**:
   - `PropertyViewingRepository.java`
   - `PropertyVacancyTaskRepository.java`
   - `LeadCommunicationRepository.java`
   - Extend `LeadRepository` with property-specific queries
   - Extend `PropertyRepository` with occupancy queries

2. **Create PropertyVacancyService**:
   ```java
   public interface PropertyVacancyService {
       void markNoticeGiven(Long propertyId, LocalDate noticeDate, LocalDate vacancyDate);
       void startAdvertising(Long propertyId);
       void markAvailable(Long propertyId, LocalDate availableFrom);
       List<Property> getPropertiesWithNoticeGiven();
       List<Property> getAdvertisingProperties();
       List<Property> getAvailableProperties();
   }
   ```

3. **Create PropertyViewingService**:
   ```java
   public interface PropertyViewingService {
       PropertyViewing scheduleViewing(Lead lead, Property property, LocalDateTime dateTime);
       void sendViewingReminder(Long viewingId);
       void completeViewing(Long viewingId, String feedback, String interestedLevel);
       List<PropertyViewing> getUpcomingViewings();
       List<PropertyViewing> getViewingsForProperty(Long propertyId);
   }
   ```

4. **Create LeadConversionService**:
   ```java
   public interface LeadConversionService {
       ConversionResult convertLeadToTenant(Long leadId, LeaseTerms terms);
       boolean canConvertLead(Long leadId);
       ConversionPreview previewConversion(Long leadId);
   }
   ```

5. **Create PropertyLeadEmailService**:
   ```java
   public interface PropertyLeadEmailService {
       void sendEnquiryConfirmation(Lead lead);
       void sendViewingConfirmation(PropertyViewing viewing);
       void sendViewingReminder(PropertyViewing viewing);
       void sendApplicationReceived(Lead lead);
       void notifyOwnerOfInterest(Property property, Lead lead);
   }
   ```

6. **Create PropertyVacancyTaskService**:
   ```java
   public interface PropertyVacancyTaskService {
       void createTasksForNoticeGiven(Property property);
       void createTasksForVacancy(Property property);
       List<PropertyVacancyTask> getOverdueTasks();
       List<PropertyVacancyTask> getTasksForProperty(Long propertyId);
       void completeTask(Long taskId);
   }
   ```

### 🔜 Phase 4: Controller Layer

**Controllers to Create/Extend**:

1. **PropertyVacancyController** (`/employee/property/vacancy/*`):
   - `GET /mark-notice-given/{id}` - Form to mark notice
   - `POST /mark-notice-given/{id}` - Process notice given
   - `POST /start-advertising/{id}` - Start advertising
   - `POST /mark-available/{id}` - Mark as available
   - `GET /dashboard` - Vacancy dashboard view

2. **PropertyViewingController** (`/employee/viewing/*`):
   - `GET /schedule` - Viewing scheduler form
   - `POST /schedule` - Create viewing
   - `GET /{id}` - View viewing details
   - `POST /{id}/complete` - Complete viewing
   - `POST /{id}/cancel` - Cancel viewing
   - `GET /calendar` - Calendar view of viewings

3. **PublicPropertyListingController** (`/api/public/properties/*`):
   - `GET /available` - List available properties (JSON API)
   - `GET /{id}` - Property details
   - `POST /enquiry` - Submit enquiry form
   - `GET /search` - Search properties

4. **Extend LeadController** (`/employee/lead/*`):
   - `GET /property/create` - Create property lead form
   - `POST /property/create` - Process property lead creation
   - `GET /property/{id}` - View property lead details
   - `POST /{id}/convert-to-tenant` - Convert lead to tenant
   - `GET /property/pipeline` - Kanban pipeline view
   - `GET /property/all` - List all property leads

### 🔜 Phase 5-6: Frontend UI

**Templates to Create**:

1. **vacancy-dashboard.html**:
   - Properties with notice given
   - Expected vacancy dates
   - Advertising properties
   - Available properties
   - Pending tasks summary
   - Quick actions (mark notice, start advertising)

2. **property-details-vacancy-section.html** (partial):
   - Add to existing property details page
   - Occupancy status badge
   - Notice given date display
   - "Mark Notice Given" button/form
   - "Start Advertising" button
   - Associated tasks list
   - Lead enquiries count

3. **property-lead-create.html**:
   - Property selector dropdown
   - Lead contact info (name, email, phone)
   - Desired move-in date
   - Budget range (min/max)
   - Number of occupants
   - Pets? Guarantor?
   - Employment status
   - Source tracking
   - Notes field

4. **property-lead-pipeline.html** (Kanban board):
   - Columns for each status
   - Drag-and-drop between statuses
   - Lead cards with key info
   - Filter by property
   - Filter by date range
   - Conversion metrics at top

5. **viewing-schedule.html**:
   - Calendar picker for date/time
   - Duration selector
   - Viewing type (in-person/virtual)
   - Assigned staff member
   - Attendee list
   - Notes field
   - Google Calendar integration toggle

6. **viewing-details.html**:
   - Viewing info summary
   - Lead details
   - Property details
   - Google Calendar link
   - Complete viewing form (feedback, interest level)
   - Cancel/reschedule options

7. **property-lead-details.html**:
   - Lead info and contact details
   - Property summary
   - Status timeline
   - Viewing history
   - Communication log
   - Documents/files
   - Convert to tenant button
   - Action buttons (schedule viewing, send email, etc.)

8. **public-property-listing.html** (public-facing):
   - Property search/filter
   - Property cards with key details
   - Click for full details
   - Enquiry form modal
   - CAPTCHA protection

### 🔜 Phase 7: Email Automation

**Email Templates to Create**:

1. **enquiry-confirmation.html**:
   - Thank you for enquiry
   - Property summary
   - Next steps
   - Contact information

2. **viewing-confirmation.html**:
   - Viewing date/time
   - Property address and directions
   - What to bring
   - Staff contact info
   - Reschedule link

3. **viewing-reminder.html**:
   - Reminder 24h before viewing
   - Property address
   - Staff contact
   - Directions link

4. **application-received.html**:
   - Thank you for application
   - Next steps (referencing)
   - Expected timeline
   - Contact for questions

5. **owner-notice-given.html**:
   - Notification that tenant gave notice
   - Expected vacancy date
   - Next steps (inspection, photography)
   - Timeline for re-letting

6. **owner-new-enquiry.html**:
   - New enquiry received
   - Lead details
   - Viewing scheduled (if applicable)

7. **owner-application-received.html**:
   - Application submitted for property
   - Applicant summary
   - Next steps (referencing)

**Automation Triggers**:
- Lead created (status=enquiry) → Send enquiry confirmation
- Viewing scheduled → Send viewing confirmation
- 24h before viewing → Send viewing reminder
- Viewing completed + interested → Send application form link
- Application submitted → Send application received + notify owner
- Notice given → Notify owner + create tasks
- New enquiry → Notify owner
- Lead converted → Send welcome email + notify owner

### 🔜 Phase 8: Public API

**API Endpoints**:

1. `GET /api/public/properties/available`:
   ```json
   {
     "properties": [
       {
         "id": 123,
         "address": "123 Main St, London, SW1A 1AA",
         "propertyType": "Flat",
         "bedrooms": 2,
         "bathrooms": 1,
         "monthlyRent": 1500.00,
         "availableFrom": "2025-12-01",
         "furnished": "Furnished",
         "imageUrl": "/images/properties/123/main.jpg",
         "description": "...",
         "features": ["Garden", "Parking", "Pet Friendly"]
       }
     ],
     "total": 45,
     "page": 1,
     "pageSize": 20
   }
   ```

2. `GET /api/public/properties/{id}`:
   ```json
   {
     "id": 123,
     "address": "...",
     "fullDetails": { },
     "images": [ ],
     "virtualTourUrl": "",
     "floorPlanUrl": ""
   }
   ```

3. `POST /api/public/properties/enquiry`:
   ```json
   {
     "propertyId": 123,
     "name": "John Smith",
     "email": "john@example.com",
     "phone": "07123456789",
     "desiredMoveInDate": "2025-12-15",
     "numberOfOccupants": 2,
     "hasPets": false,
     "message": "I'm interested in viewing this property.",
     "captchaToken": "..."
   }
   ```

4. `GET /api/public/properties/search`:
   - Query params: `minBedrooms`, `maxBedrooms`, `minRent`, `maxRent`, `propertyType`, `location`, `availableFrom`

---

## Email Automation

### Automation Architecture

```
┌──────────────────┐
│  Status Change   │
│  Event Listener  │
└──────────────────┘
         │
         ▼
┌──────────────────┐
│  EmailAutomatic  │──►┌────────────────┐
│  TriggerService  │   │ Check Settings │
└──────────────────┘   └────────────────┘
         │                      │
         │                      ▼
         │             ┌────────────────┐
         │             │  Is Trigger    │──NO──► Exit
         │             │   Enabled?     │
         │             └────────────────┘
         │                      │ YES
         │                      ▼
         │             ┌────────────────┐
         │             │ Load Template  │
         │             └────────────────┘
         │                      │
         ▼                      ▼
┌──────────────────┐   ┌────────────────┐
│ Merge Template   │◄──│ Get Lead/Prop  │
│  with Data       │   │     Data       │
└──────────────────┘   └────────────────┘
         │
         ▼
┌──────────────────┐
│  Send via Gmail  │
│      API         │
└──────────────────┘
         │
         ▼
┌──────────────────┐
│ Log to lead_     │
│  communications  │
└──────────────────┘
```

### Email Template Variables

All templates support merge variables:

**Lead Variables**:
- `{{lead.name}}`
- `{{lead.email}}`
- `{{lead.phone}}`
- `{{lead.desiredMoveInDate}}`

**Property Variables**:
- `{{property.address}}`
- `{{property.propertyName}}`
- `{{property.bedrooms}}`
- `{{property.monthlyPayment}}`
- `{{property.postcode}}`

**Viewing Variables**:
- `{{viewing.scheduledDatetime}}`
- `{{viewing.durationMinutes}}`
- `{{viewing.assignedUser.name}}`

**System Variables**:
- `{{currentDate}}`
- `{{companyName}}`
- `{{companyEmail}}`
- `{{companyPhone}}`

### LeadEmailSettings Extension

Extend existing `LeadEmailSettings` entity:

```java
// New fields for property lead automation
@Column(name = "enquiry_received_email_enabled")
private Boolean enquiryReceivedEmailEnabled = false;

@ManyToOne
@JoinColumn(name = "enquiry_received_template_id")
private EmailTemplate enquiryReceivedTemplate;

@Column(name = "viewing_scheduled_email_enabled")
private Boolean viewingScheduledEmailEnabled = false;

@ManyToOne
@JoinColumn(name = "viewing_scheduled_template_id")
private EmailTemplate viewingScheduledTemplate;

// ... repeat for all triggers
```

---

## Future Integrations

### OpenRent Integration (Phase 9)

**API Documentation**: https://www.openrent.com/api

**Features**:
1. Auto-publish property listings
2. Import enquiries as leads
3. Sync property status
4. Track listing performance

**Implementation Pattern** (follow PayProp pattern):

```
/service/openrent/
  - OpenRentApiClient.java
  - OpenRentPropertySyncService.java
  - OpenRentLeadImportService.java
  - OpenRentPropertyDTO.java
  - OpenRentEnquiryDTO.java
```

**Workflow**:
1. Property status → ADVERTISING
2. OpenRentPropertySyncService publishes listing
3. Store openrent_listing_id in properties table
4. Poll for enquiries every 15 minutes
5. Import enquiries as leads (leadSource="openrent")
6. When property is let → Update OpenRent listing status

### SpareRoom Integration (Phase 10)

**Similar to OpenRent**:
- API integration for listing sync
- Enquiry import
- Messaging integration

### Rightmove Integration (Phase 11)

**Via Rightmove Data Feed**:
- XML feed generation
- FTP upload
- Performance tracking

---

## Testing Strategy

### Unit Tests

**Entity Tests**:
- Test all helper methods
- Test validation constraints
- Test relationship mappings

**Service Tests**:
- Mock repository layer
- Test business logic
- Test error handling
- Test email triggers

**Controller Tests**:
- Mock service layer
- Test request/response
- Test validation
- Test authorization

### Integration Tests

**Database Tests**:
- Test migrations
- Test queries
- Test indexes
- Test views

**End-to-End Tests**:
- Full workflow tests
- Lead creation → viewing → conversion
- Notice given → advertising → let
- Email sending
- Calendar integration

### Test Data

**Seed Data Script** (`test-data-leads.sql`):
```sql
-- Insert test properties with various statuses
INSERT INTO properties (occupancy_status, notice_given_date, expected_vacancy_date, ...)
VALUES
  ('NOTICE_GIVEN', '2025-11-01', '2025-12-01', ...),
  ('ADVERTISING', NULL, NULL, ...),
  ('AVAILABLE', NULL, '2025-11-15', ...);

-- Insert test leads
INSERT INTO trigger_lead (lead_type, property_id, status, name, ...)
VALUES
  ('PROPERTY_RENTAL', 1, 'enquiry', 'John Smith', ...),
  ('PROPERTY_RENTAL', 2, 'viewing-scheduled', 'Jane Doe', ...),
  ('PROPERTY_RENTAL', 3, 'interested', 'Bob Johnson', ...);

-- Insert test viewings
INSERT INTO property_viewings (lead_id, property_id, scheduled_datetime, status, ...)
VALUES
  (1, 1, '2025-11-10 14:00:00', 'SCHEDULED', ...),
  (2, 2, '2025-11-08 10:00:00', 'COMPLETED', ...);
```

---

## Progress Tracking

### Completed Tasks ✅

- [x] Database migration script (V18)
- [x] OccupancyStatus enum
- [x] LeadType enum
- [x] PropertyViewing entity
- [x] PropertyVacancyTask entity
- [x] LeadCommunication entity
- [x] Extend Lead entity
- [x] Extend Property entity
- [x] Comprehensive documentation

### In Progress 🔄

- [ ] Repository interfaces
- [ ] Service layer implementation

### Upcoming 🔜

- [ ] Controller layer
- [ ] Frontend templates
- [ ] Email automation
- [ ] Public API
- [ ] Testing
- [ ] External integrations

---

## Database Connection

**Connection String**:
```
Host: switchyard.proxy.rlwy.net
Port: 55090
Database: railway
Username: root
Password: iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW
```

**MySQL Command**:
```bash
"C:\Program Files\MySQL\MySQL Server 9.3\bin\mysql.exe" -h switchyard.proxy.rlwy.net -P 55090 -u root -p'iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW' railway
```

---

## Next Steps

1. **Run Database Migration**:
   ```bash
   # Migration will run automatically on next application start
   # Or run manually:
   flyway migrate
   ```

2. **Create Repository Interfaces** (Phase 3):
   - PropertyViewingRepository
   - PropertyVacancyTaskRepository
   - LeadCommunicationRepository

3. **Implement Service Layer** (Phase 3):
   - PropertyVacancyService
   - PropertyViewingService
   - LeadConversionService
   - PropertyLeadEmailService
   - PropertyVacancyTaskService

4. **Build Controllers** (Phase 4):
   - PropertyVacancyController
   - PropertyViewingController
   - Extend LeadController

5. **Create Frontend UI** (Phase 5-6):
   - Vacancy dashboard
   - Lead pipeline
   - Viewing scheduler

---

## Support & Questions

For questions or clarifications during implementation, refer to:

1. **Existing Patterns**: Look at PayProp integration for external API patterns
2. **Lead System**: Existing Lead entity and LeadController for workflow patterns
3. **Ticket System**: Similar task and status tracking patterns
4. **This Document**: Comprehensive reference for all design decisions

---

**Document Version**: 1.0
**Last Updated**: 2025-11-05
**Author**: Claude Code AI Assistant
**Status**: Phase 2 Complete - Ready for Phase 3 (Services)

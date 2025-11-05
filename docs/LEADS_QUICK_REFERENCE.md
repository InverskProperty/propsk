# Property Leads System - Quick Reference Guide

**Quick access to key information for developers working on the leads system**

---

## 🗂️ File Locations

### Entities
- `src/main/java/site/easy/to/build/crm/entity/`
  - `OccupancyStatus.java` - Property occupancy enum ✅
  - `LeadType.java` - Lead type enum ✅
  - `Lead.java` - Extended with property fields ✅
  - `Property.java` - Extended with vacancy tracking ✅
  - `PropertyViewing.java` - Viewing appointments ✅
  - `PropertyVacancyTask.java` - Vacancy tasks ✅
  - `LeadCommunication.java` - Communication tracking ✅

### Database
- `src/main/resources/db/migration/V18__Add_Property_Leads_System.sql` ✅

### Documentation
- `docs/PROPERTY_LEADS_SYSTEM_PLAN.md` - Full implementation plan ✅
- `docs/LEADS_QUICK_REFERENCE.md` - This file ✅

---

## 📊 Database Tables

| Table | Purpose | Key Fields |
|-------|---------|------------|
| `properties` | Extended with vacancy fields | occupancy_status, notice_given_date, expected_vacancy_date |
| `trigger_lead` | Extended for property leads | property_id, lead_type, budget_min/max, has_pets |
| `property_viewings` | Viewing appointments | lead_id, property_id, scheduled_datetime, status |
| `property_vacancy_tasks` | Automated vacancy tasks | property_id, task_type, status, due_date |
| `lead_communications` | Communication log | lead_id, communication_type, direction, status |

---

## 🔄 Lead Status Workflow

```
enquiry → viewing-scheduled → viewing-completed → interested →
application-submitted → referencing → in-contracts → converted
```

**Or at any stage**: → `lost`

---

## 🏠 Property Occupancy Workflow

```
OCCUPIED → NOTICE_GIVEN → ADVERTISING → AVAILABLE → OCCUPIED
```

---

## 🔑 Key Classes & Methods

### Property
```java
// Mark notice given
property.markNoticeGiven(LocalDate noticeDate, LocalDate expectedVacancy);

// Start advertising
property.startAdvertising();

// Mark available
property.markAvailable(LocalDate availableFrom);

// Mark occupied (after tenant moves in)
property.markOccupied();

// Check status
boolean available = property.isAvailableForLetting();
boolean needsMarketing = property.requiresMarketingAttention();
Long daysLeft = property.getDaysUntilVacancy();
```

### Lead
```java
// Check lead type
boolean isPropertyLead = lead.isPropertyLead();

// Check conversion status
boolean converted = lead.isConverted();

// Get formatted budget
String budgetDisplay = lead.getBudgetRangeDisplay(); // "£1,000 - £1,500"
```

### PropertyViewing
```java
// Create viewing
PropertyViewing viewing = new PropertyViewing(lead, property, dateTime);

// Complete viewing
viewing.complete(feedback, interestedLevel);

// Cancel viewing
viewing.cancel();

// Check status
boolean upcoming = viewing.isUpcoming();
boolean canReschedule = viewing.canBeRescheduled();
```

### PropertyVacancyTask
```java
// Create task
PropertyVacancyTask task = new PropertyVacancyTask(property, "INSPECTION", "Property Inspection");
task.setDueDate(vacancyDate.minusDays(14));
task.setAutoCreated(true);

// Manage task
task.startProgress();
task.complete();

// Check status
boolean overdue = task.isOverdue();
Long daysLeft = task.getDaysUntilDue();
```

---

## 📝 Lead Conversion Process

**LeadConversionService.convertLeadToTenant(leadId)**:

1. ✅ Validate lead can be converted
2. ✅ Create Customer record (type=TENANT)
3. ✅ Create Invoice (lease agreement)
4. ✅ Create CustomerPropertyAssignment (type=TENANT)
5. ✅ Update property status to OCCUPIED
6. ✅ Update lead status to "converted"
7. ✅ Create audit trail (LeadAction)
8. ✅ Send notifications (owner, tenant, team)
9. ✅ Create move-in tasks

---

## 🎯 Next Implementation Steps

### Phase 3: Service Layer (NEXT)

**Create Repositories**:
1. `PropertyViewingRepository extends JpaRepository<PropertyViewing, Long>`
2. `PropertyVacancyTaskRepository extends JpaRepository<PropertyVacancyTask, Long>`
3. `LeadCommunicationRepository extends JpaRepository<LeadCommunication, Long>`

**Custom Query Methods Needed**:
```java
// PropertyRepository additions
List<Property> findByOccupancyStatus(OccupancyStatus status);
List<Property> findByOccupancyStatusIn(List<OccupancyStatus> statuses);
List<Property> findByExpectedVacancyDateBetween(LocalDate start, LocalDate end);

// LeadRepository additions
List<Lead> findByLeadTypeAndProperty(LeadType type, Property property);
List<Lead> findByLeadTypeAndStatus(LeadType type, String status);
List<Lead> findByPropertyAndStatusNot(Property property, String status);

// PropertyViewingRepository
List<PropertyViewing> findByPropertyAndStatusIn(Property property, List<String> statuses);
List<PropertyViewing> findByScheduledDatetimeBetween(LocalDateTime start, LocalDateTime end);
List<PropertyViewing> findByLeadOrderByScheduledDatetimeDesc(Lead lead);

// PropertyVacancyTaskRepository
List<PropertyVacancyTask> findByPropertyAndStatusIn(Property property, List<String> statuses);
List<PropertyVacancyTask> findByDueDateBeforeAndStatusNot(LocalDate date, String status);
List<PropertyVacancyTask> findByAssignedToUser(User user);

// LeadCommunicationRepository
List<LeadCommunication> findByLeadOrderByCreatedAtDesc(Lead lead);
List<LeadCommunication> findByLeadAndCommunicationType(Lead lead, String type);
```

**Create Services**:
1. `PropertyVacancyService` + `PropertyVacancyServiceImpl`
2. `PropertyViewingService` + `PropertyViewingServiceImpl`
3. `LeadConversionService` + `LeadConversionServiceImpl`
4. `PropertyLeadEmailService` + `PropertyLeadEmailServiceImpl`
5. `PropertyVacancyTaskService` + `PropertyVacancyTaskServiceImpl`

---

## 📧 Email Automation Triggers

| Trigger Event | Email Template | Recipient |
|---------------|----------------|-----------|
| Lead created (status=enquiry) | enquiry-confirmation | Lead |
| Viewing scheduled | viewing-confirmation | Lead |
| 24h before viewing | viewing-reminder | Lead |
| Application submitted | application-received | Lead + Owner |
| Notice given | owner-notice-given | Owner |
| New enquiry received | owner-new-enquiry | Owner |
| Lead converted | tenant-welcome | Tenant |

---

## 🎨 Frontend Routes (To Create)

### Employee/Admin Routes
- `/employee/property/vacancy/dashboard` - Vacancy overview
- `/employee/property/vacancy/mark-notice-given/{id}` - Mark notice form
- `/employee/property/vacancy/start-advertising/{id}` - Start advertising
- `/employee/lead/property/create` - Create property lead
- `/employee/lead/property/pipeline` - Kanban pipeline view
- `/employee/lead/property/{id}` - Property lead details
- `/employee/lead/{id}/convert-to-tenant` - Conversion page
- `/employee/viewing/schedule` - Schedule viewing form
- `/employee/viewing/{id}` - Viewing details
- `/employee/viewing/calendar` - Calendar view

### Public Routes
- `/properties` - Public property listings
- `/properties/{id}` - Property details
- `/properties/enquiry` - Enquiry form (modal or page)

### API Routes
- `GET /api/public/properties/available` - List available properties (JSON)
- `GET /api/public/properties/{id}` - Property details (JSON)
- `POST /api/public/properties/enquiry` - Submit enquiry
- `GET /api/public/properties/search` - Search properties

---

## 💾 Example Data

### Create Test Property Lead
```sql
INSERT INTO trigger_lead (
    name, phone, status, lead_type, property_id,
    desired_move_in_date, budget_min, budget_max,
    number_of_occupants, has_pets, has_guarantor,
    employment_status, lead_source, created_at
) VALUES (
    'John Smith',
    '07123456789',
    'enquiry',
    'PROPERTY_RENTAL',
    1,  -- property ID
    '2025-12-01',
    1000.00,
    1500.00,
    2,
    FALSE,
    FALSE,
    'EMPLOYED',
    'website',
    NOW()
);
```

### Mark Property Notice Given
```sql
UPDATE properties
SET
    occupancy_status = 'NOTICE_GIVEN',
    notice_given_date = '2025-11-01',
    expected_vacancy_date = '2025-12-01',
    last_occupancy_change = NOW()
WHERE id = 1;
```

### Schedule Viewing
```sql
INSERT INTO property_viewings (
    lead_id, property_id, scheduled_datetime,
    duration_minutes, viewing_type, status,
    created_at, updated_at
) VALUES (
    1,  -- lead ID
    1,  -- property ID
    '2025-11-15 14:00:00',
    30,
    'IN_PERSON',
    'SCHEDULED',
    NOW(),
    NOW()
);
```

---

## 🔍 Useful Queries

### Find Properties Requiring Attention
```sql
SELECT * FROM v_properties_requiring_attention
ORDER BY expected_vacancy_date;
```

### Get Lead Conversion Metrics
```sql
SELECT * FROM v_lead_conversion_funnel;
```

### Find Upcoming Viewings
```sql
SELECT
    pv.*,
    l.name AS lead_name,
    l.phone AS lead_phone,
    p.address AS property_address
FROM property_viewings pv
JOIN trigger_lead l ON pv.lead_id = l.lead_id
JOIN properties p ON pv.property_id = p.id
WHERE pv.scheduled_datetime >= NOW()
AND pv.status IN ('SCHEDULED', 'CONFIRMED')
ORDER BY pv.scheduled_datetime;
```

### Find Overdue Vacancy Tasks
```sql
SELECT
    pvt.*,
    p.address AS property_address
FROM property_vacancy_tasks pvt
JOIN properties p ON pvt.property_id = p.id
WHERE pvt.due_date < CURDATE()
AND pvt.status NOT IN ('COMPLETED', 'CANCELLED')
ORDER BY pvt.due_date, pvt.priority;
```

### Find Active Property Leads by Property
```sql
SELECT
    l.*,
    p.address AS property_address,
    COUNT(pv.id) AS viewing_count
FROM trigger_lead l
JOIN properties p ON l.property_id = p.id
LEFT JOIN property_viewings pv ON l.lead_id = pv.lead_id
WHERE l.lead_type = 'PROPERTY_RENTAL'
AND l.status NOT IN ('converted', 'lost', 'archived')
GROUP BY l.lead_id
ORDER BY l.created_at DESC;
```

---

## 🧪 Testing Checklist

### Unit Tests
- [ ] OccupancyStatus enum methods
- [ ] LeadType enum methods
- [ ] Property.markNoticeGiven()
- [ ] Property.startAdvertising()
- [ ] Property.markOccupied()
- [ ] Lead.isPropertyLead()
- [ ] Lead.getBudgetRangeDisplay()
- [ ] PropertyViewing helper methods
- [ ] PropertyVacancyTask helper methods

### Integration Tests
- [ ] Database migration runs successfully
- [ ] All indexes are created
- [ ] Views return correct data
- [ ] Foreign key constraints work
- [ ] Lead creation with property
- [ ] Viewing scheduling
- [ ] Task auto-creation on notice given

### End-to-End Tests
- [ ] Full workflow: Notice → Advertising → Lead → Viewing → Conversion
- [ ] Lead pipeline progression
- [ ] Email automation triggers
- [ ] Google Calendar integration
- [ ] Public enquiry form submission

---

## 🐛 Common Pitfalls

1. **Enum Mapping**: Ensure `@Enumerated(EnumType.STRING)` is used (not ORDINAL)
2. **Null Checks**: Always check occupancyStatus != null before calling enum methods
3. **LeadType Discrimination**: Always set `leadType` when creating property leads
4. **Status Validation**: Use the Pattern constraint to enforce valid status values
5. **Bidirectional Relationships**: Remember to set both sides when linking entities
6. **Timestamps**: Use `@PrePersist` and `@PreUpdate` for audit fields
7. **Google Calendar**: Check OAuthUser exists before calendar operations
8. **Email Automation**: Check LeadEmailSettings before sending automated emails

---

## 📞 Integration Points

### Existing Systems to Leverage

1. **Gmail Integration**:
   - `GmailEmailService` - Send emails
   - Store message ID in `LeadCommunication.gmailMessageId`

2. **Google Calendar**:
   - `GoogleCalendarApiService` - Create/update events
   - Store event ID in `PropertyViewing.googleCalendarEventId`

3. **Google Drive**:
   - `GoogleDriveService` - Store lead documents
   - Use existing `Lead.googleDriveFolderId`

4. **Email Templates**:
   - `EmailTemplateService` - Load templates
   - `EmailTemplate` entity - Template content

5. **PayProp Pattern**:
   - Reference `PayPropLeaseCreationService` for conversion logic
   - Similar patterns for external API integration

---

## 🎯 Success Criteria

### Phase 3 (Services) Complete When:
- [ ] All repository interfaces created
- [ ] All service interfaces defined
- [ ] All service implementations completed
- [ ] Unit tests pass (>80% coverage)
- [ ] Can programmatically:
  - Mark property notice given
  - Create property lead
  - Schedule viewing
  - Convert lead to tenant
  - Auto-create tasks

### Phase 4 (Controllers) Complete When:
- [ ] All controllers created
- [ ] All endpoints responding
- [ ] Request validation working
- [ ] Authorization checks in place
- [ ] Integration tests pass

### Phase 5-6 (Frontend) Complete When:
- [ ] Vacancy dashboard displays data
- [ ] Can mark notice given via UI
- [ ] Can create property leads via form
- [ ] Pipeline view shows leads
- [ ] Can schedule viewings
- [ ] Can convert leads to tenants
- [ ] All forms validate correctly

### Phase 7 (Email) Complete When:
- [ ] All email templates created
- [ ] Automation triggers working
- [ ] Emails send successfully
- [ ] Email tracking working (opens, clicks)
- [ ] Communications logged

### Phase 8 (Public API) Complete When:
- [ ] Public property list working
- [ ] Property search working
- [ ] Enquiry form working
- [ ] CAPTCHA protection working
- [ ] Rate limiting in place

---

## 📚 Related Documentation

- **Full Plan**: `docs/PROPERTY_LEADS_SYSTEM_PLAN.md`
- **Database Schema**: `src/main/resources/db/migration/V18__Add_Property_Leads_System.sql`
- **Existing Patterns**:
  - Lead system: `src/main/java/site/easy/to/build/crm/entity/Lead.java`
  - Ticket system: `src/main/java/site/easy/to/build/crm/entity/Ticket.java`
  - PayProp integration: `src/main/java/site/easy/to/build/crm/service/payprop/`

---

**Last Updated**: 2025-11-05
**Current Phase**: Phase 2 Complete (Entities) → Starting Phase 3 (Services)

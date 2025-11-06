# Letting Instruction System

## Overview

A comprehensive property letting lifecycle management system that tracks the complete journey of renting out a property, from initial instruction through to active tenancy.

## Workflow

```
INSTRUCTION_RECEIVED → ADVERTISING → OFFER_ACCEPTED → ACTIVE_LEASE
                            ↓
                       CANCELLED (at any point)
```

### Status Definitions

1. **INSTRUCTION_RECEIVED** - Owner instructs to let the property
2. **ADVERTISING** - Property actively marketed (viewings, enquiries, offers all happening concurrently)
3. **OFFER_ACCEPTED** - Holding deposit paid, proceeding to referencing/contracts
4. **ACTIVE_LEASE** - Tenant moved in, lease is active
5. **CANCELLED** - Instruction withdrawn or cancelled

### Legacy Statuses (Deprecated)
- `PREPARING` - Being prepared for marketing
- `VIEWINGS_IN_PROGRESS` - Conducting viewings
- `OFFER_MADE` - Offer accepted
- `CLOSED` - Instruction closed

## Architecture

### Entity Layer
**`LettingInstruction.java`**
- Core entity representing one complete rental cycle
- Tracks instruction dates, target/actual rent, lease details
- Links to Property, Tenant/Customer, Leads, Viewings
- Built-in metrics: enquiry count, viewing count, days to fill

**`InstructionStatus.java`**
- Enum defining lifecycle stages
- Validation rules for status transitions
- Helper methods for Kanban ordering and badge styling

### Service Layer
**`LettingInstructionService.java`**

**Lifecycle Methods:**
- `createInstruction()` - Create new instruction
- `startAdvertising()` - Begin marketing
- `markOfferAccepted()` - Holding deposit paid ✨ NEW
- `convertToActiveLease()` - Tenant moved in
- `cancelInstruction()` - Withdraw instruction ✨ NEW

**Query Methods:**
- `getAllActiveInstructions()` - All non-closed/non-leased
- `getActiveMarketingInstructions()` - Currently being marketed
- `getInstructionsByStatus()` - Filter by status
- `getInstructionSummary()` - Dashboard statistics

**Lead Management:**
- `addLeadToInstruction()` - Link enquiry to instruction
- `removeLeadFromInstruction()` - Unlink lead

### Controller Layer
**`LettingInstructionController.java`**

**Views:**
```
GET  /employee/letting-instruction/workspace      - Kanban dashboard
GET  /employee/letting-instruction/{id}/detail    - Single instruction view
GET  /employee/letting-instruction/active-leases  - Tenanted properties
GET  /employee/letting-instruction/history        - Closed instructions
```

**Status Transitions (REST API):**
```
POST /employee/letting-instruction/create                    - Create new instruction
POST /employee/letting-instruction/{id}/start-advertising    - Start marketing
POST /employee/letting-instruction/{id}/mark-offer-accepted  - Holding deposit paid ✨ NEW
POST /employee/letting-instruction/{id}/convert-to-lease     - Tenant moves in
POST /employee/letting-instruction/{id}/cancel               - Cancel instruction ✨ NEW
POST /employee/letting-instruction/{id}/close                - Close (legacy)
```

**Lead Management:**
```
POST /employee/letting-instruction/{id}/add-lead     - Add enquiry
POST /employee/letting-instruction/{id}/remove-lead  - Remove enquiry
```

### Repository Layer
**`LettingInstructionRepository.java`**

**Key Queries:**
- `findAllActiveInstructions()` - All active (non-closed/non-leased)
- `findAllActiveMarketingInstructions()` - Currently advertising
- `findAllActiveLeases()` - Tenanted properties
- `findByProperty()` - Instruction history for a property
- `findByStatusOrderByCreatedAtDesc()` - Filter by status
- `searchByPropertyOrReference()` - Search functionality

**Metrics:**
- `calculateAverageDaysToFill()` - Average time to let
- `calculateConversionRate()` - Enquiries to lease conversion

## UI Components

### Kanban Workspace
**`instruction-workspace.html`**

**4 Columns:**
1. **New** - Just received instructions
2. **Advertising** - Being marketed (viewings/enquiries/offers happening)
3. **Offer Accepted** - Holding deposit paid, referencing in progress
4. **Active Leases** - Tenanted properties

**Summary Cards:**
- Being Marketed
- Offers Accepted
- Active Leases
- New Instructions

**Metrics:**
- Average Days to Fill
- Average Conversion Rate

### Instruction Detail View
**`instruction-detail.html`**

**Action Buttons (context-aware):**
- Start Advertising (when INSTRUCTION_RECEIVED)
- Mark Offer Accepted (when ADVERTISING) ✨ NEW
- Convert to Active Lease (when OFFER_ACCEPTED)
- Cancel Instruction (available at any stage) ✨ NEW

## API Request DTOs

### CreateInstructionRequest
```json
{
  "propertyId": 123,
  "targetRent": 1000.00,
  "targetLeaseLengthMonths": 12,
  "expectedVacancyDate": "2025-01-01",
  "propertyDescription": "Beautiful 2-bed apartment..."
}
```

### MarkOfferAcceptedRequest ✨ NEW
```json
{
  "tenantId": 456,
  "agreedRent": 950.00,
  "notes": "Holding deposit £500 received, references requested"
}
```

### CancelInstructionRequest ✨ NEW
```json
{
  "reason": "Owner decided not to let property"
}
```

### ConvertToLeaseRequest
```json
{
  "tenantId": 456,
  "actualRent": 950.00,
  "leaseStartDate": "2025-02-01",
  "leaseEndDate": "2026-02-01",
  "leaseLengthMonths": 12,
  "depositAmount": 1425.00
}
```

## Real-World Usage

### Scenario: Letting a Property

1. **Owner calls**: "I need to let 3 West Gate Flat 16"
   - Create instruction → Status: `INSTRUCTION_RECEIVED`
   - System generates reference: `INST-WG16-001`

2. **Start marketing**: Property cleaned, photos taken, listing ready
   - Click "Start Advertising" → Status: `ADVERTISING`
   - Add to Rightmove, Zoopla, etc.

3. **Activity happens concurrently**:
   - 15 enquiries come in (leads added to instruction)
   - 8 viewings conducted (tracked on instruction)
   - 3 offers received (notes added to instruction)

4. **Offer accepted**: Best candidate selected
   - Holding deposit £500 paid
   - Click "Mark Offer Accepted" → Status: `OFFER_ACCEPTED`
   - Request references
   - Prepare tenancy agreement

5. **References clear**: Ready to move in
   - Click "Convert to Active Lease" → Status: `ACTIVE_LEASE`
   - Tenant moves in 01 Feb 2025
   - Property now shows in "Active Leases" dashboard

## Key Features

✅ **Realistic Workflow** - Matches real-world letting process
✅ **Concurrent Activities** - Viewings/offers happen during advertising
✅ **Full History** - Track every instruction for each property
✅ **Performance Metrics** - Days to fill, conversion rates
✅ **Lead Management** - Link enquiries to instructions
✅ **Backward Compatible** - Legacy statuses still work
✅ **Status Validation** - Enforces valid transitions
✅ **Audit Trail** - Internal notes for each stage

## Database Schema

**Key Fields:**
- `instruction_reference` - Unique identifier (e.g., INST-WG16-001)
- `status` - Current lifecycle stage
- `property_id` - FK to Property
- `tenant_id` - FK to Customer (when leased)
- `instruction_received_date` - When instruction created
- `advertising_start_date` - When marketing began
- `expected_vacancy_date` - When property becomes available
- `target_rent` / `actual_rent` - Asking vs agreed rent
- `lease_start_date` / `lease_end_date` - Tenancy period
- `number_of_enquiries` / `number_of_viewings` - Performance metrics
- `internal_notes` - Activity log

## Future Enhancements

- **Lead Status Tracking**: Enquiry → Viewing Booked → Viewing Attended → Holding Deposit → Referencing → Contracting → Tenant
- **Automated Notifications**: Email/SMS on status changes
- **Document Management**: Attach contracts, references, certificates
- **Calendar Integration**: Sync viewings to Google Calendar
- **Analytics Dashboard**: Trends, performance by property type/area
- **Bulk Operations**: Mass status updates, batch advertising

## Notes

- Instructions created after refactor automatically start in `INSTRUCTION_RECEIVED` status
- System supports multiple instructions per property over time (full letting history)
- Each instruction represents one complete rental cycle
- Metrics auto-calculate on status transitions
- Frontend uses Bootstrap 4 + jQuery for interactive UI

# Lead Kanban Query Analysis - Finding Missing Lead with Count 1

## Summary
A lead with count=1 is not showing in any kanban columns on the instruction detail/pipeline page.

## 1. HTML TEMPLATES FOR LEAD DISPLAY

### Lead Pipeline (Kanban Board) Template
File: src/main/resources/templates/property-lifecycle/lead-pipeline.html

Kanban Columns (10 total):
1. Enquiry
2. Viewing Scheduled
3. Viewing Completed
4. Interested
5. Application Submitted
6. Referencing
7. In Contracts
8. Contracts Complete
9. Converted
10. Lost

Data Model Attributes:
- enquiryLeads
- viewingScheduledLeads
- viewingCompletedLeads
- interestedLeads
- applicationSubmittedLeads
- referencingLeads
- inContractsLeads
- contractsCompleteLeads
- convertedLeads
- lostLeads

### Instruction Detail Template
File: src/main/resources/templates/property-lifecycle/instruction-detail.html

Shows ALL leads in instruction.leads without status filtering

## 2. CONTROLLER HANDLING LEAD QUERIES

File: src/main/java/.../controller/LettingInstructionController.java
Method: leadPipeline() (lines 69-109)

The lead filtering happens in Java streams at lines 87-105:

```
allLeads.stream().filter(l -> "enquiry".equals(l.getStatus())).toList()
allLeads.stream().filter(l -> "viewing-scheduled".equals(l.getStatus())).toList()
allLeads.stream().filter(l -> "viewing-completed".equals(l.getStatus())).toList()
allLeads.stream().filter(l -> "interested".equals(l.getStatus())).toList()
allLeads.stream().filter(l -> "application-submitted".equals(l.getStatus())).toList()
allLeads.stream().filter(l -> "referencing".equals(l.getStatus())).toList()
allLeads.stream().filter(l -> "in-contracts".equals(l.getStatus())).toList()
allLeads.stream().filter(l -> "contracts-complete".equals(l.getStatus())).toList()
allLeads.stream().filter(l -> "converted".equals(l.getStatus())).toList()
allLeads.stream().filter(l -> "lost".equals(l.getStatus())).toList()
```

## 3. VALID LEAD STATUSES (10 values)

From LeadStatus.java enum:

Enum Value             Database Value            Display Name
ENQUIRY               "enquiry"                  Enquiry
VIEWING_SCHEDULED     "viewing-scheduled"        Viewing Scheduled
VIEWING_COMPLETED     "viewing-completed"        Viewing Completed
INTERESTED            "interested"               Interested
APPLICATION_SUBMITTED "application-submitted"    Application Submitted
REFERENCING           "referencing"              Referencing
IN_CONTRACTS          "in-contracts"             In Contracts
CONTRACTS_COMPLETE    "contracts-complete"       Contracts Complete
CONVERTED             "converted"                Converted
LOST                  "lost"                     Lost

## 4. WHERE LEADS COME FROM

Lead Entity Relationship:
- Lead has ManyToOne relationship with LettingInstruction
- Column: letting_instruction_id
- LettingInstruction has OneToMany leads collection

How linked:
```
lead.setLettingInstruction(instruction);
```

## 5. WHY A LEAD WITH COUNT=1 MIGHT NOT SHOW

POSSIBLE ROOT CAUSES:

1. INVALID STATUS VALUE
   - Status is something other than the 10 valid values
   - Could be NULL, empty string, misspelled, or old value
   - Check database: SELECT status FROM trigger_lead WHERE lead_id = ?

2. LEAD NOT LINKED TO INSTRUCTION
   - lead.letting_instruction_id is NULL in database
   - Check: SELECT * FROM trigger_lead WHERE lead_id = ? AND letting_instruction_id IS NULL

3. STATUS CONVERSION ISSUE
   - LeadStatusConverter not working properly
   - Status stored as enum but filter expects string

4. LAZY INITIALIZATION FAILURE
   - instruction.getLeads() not initialized
   - Should use getInstructionWithDetails() which has FETCH JOIN
   - Or leads list is null/empty after lazy loading fails

## 6. STATUS FILTER COMPARISON

The controller uses string matching:
```
"enquiry".equals(l.getStatus())
```

This assumes l.getStatus() returns a STRING that matches exactly.

But Lead.status is a LeadStatus ENUM that gets converted to string.

## 7. DEBUGGING STEPS

1. Query database for the lead:
   SELECT lead_id, name, status, letting_instruction_id 
   FROM trigger_lead 
   WHERE letting_instruction_id = ?

2. Check if status value matches one of 10 valid values

3. If instruction detail page shows the lead but kanban doesn't:
   - Status is invalid/unmatched
   - Add logging to see actual status value

4. If neither page shows the lead:
   - letting_instruction_id might be NULL
   - Lead was removed but count still shows 1

5. Check for NULL status:
   - Any NULL status won't match any filter

## 8. KEY FILES

instruction-detail.html       - Simple table view (no filtering)
lead-pipeline.html            - Kanban board with status filtering
LettingInstructionController  - Lines 69-109 contain filtering logic
Lead.java                     - Lead entity
LeadStatus.java               - 10 valid status values
LettingInstruction.java       - OneToMany leads relationship


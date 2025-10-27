# Lease Import Commission Enhancement Proposal
**Date**: October 27, 2025
**Status**: PROPOSAL - Not Yet Implemented

---

## Executive Summary

Your lease import data includes commission rates (15%, 10%, 0%) per lease, but the current import wizard **does not capture or use this information**. This proposal outlines how to enhance the lease import wizard to:

1. **Check current property commission settings** during import
2. **Warn about mismatches** between CSV and existing property settings
3. **Offer to update property commission rates** from the CSV data

---

## Current State Analysis

### What Your Data Contains

From `boden_house_leases_import.csv`, your leases have:

**Commission Rates in Original Data**:
- **15%** - 35 flats + 2 parking spaces (37 leases)
- **10%** - Knighton Hayes apartment (1 lease)
- **0%** - Boden House Block Property (1 lease)

**Properties Involved**:
- 30 unique flats (FLAT 1-30 at 3 West Gate)
- 2 parking spaces (PS1, PS2 at 3 West Gate)
- 1 block property (Boden House NG10)
- 1 Knighton Hayes apartment

**Total**: 34 unique properties across 39 lease records

---

### Current Database Schema

**Property Entity** has commission fields:
```java
@Column(name = "commission_percentage", precision = 5, scale = 2)
private BigDecimal commissionPercentage;  // e.g., 15.00 for 15%

@Column(name = "commission_amount", precision = 10, scale = 2)
private BigDecimal commissionAmount;  // Flat fee (if applicable)

// Helper method to check if commission is configured
public boolean hasCommissionRate() {
    return commissionPercentage != null && commissionPercentage.compareTo(BigDecimal.ZERO) > 0;
}
```

---

### Current Import Behavior

**What the wizard DOES capture**:
- ✅ property_reference
- ✅ customer_name / customer_email
- ✅ lease_start_date / lease_end_date
- ✅ rent_amount
- ✅ payment_day
- ✅ lease_reference

**What the wizard IGNORES**:
- ❌ commission_percentage (not even in CSV format!)
- ❌ management_fee_percentage (your data has this but wizard doesn't read it)

**Result**: Your properties may have **no commission rate set** or **incorrect rates**, causing:
- ❌ Commission calculations fail or use wrong rates
- ❌ Owner statements show incorrect management fees
- ❌ Financial reporting inaccurate

---

## The Problem: Checking Current Property Commission Status

### Cannot Query Database Directly

I attempted to check your current property commission settings but cannot access the database from this environment:
```
Error: psql: command not found
Error: no workspace set (for Render MCP)
```

**Action Required**: You need to run this query manually to check current state:

```sql
-- Check commission rates for all properties in your import
SELECT
    property_name,
    commission_percentage,
    commission_amount,
    CASE
        WHEN commission_percentage IS NULL THEN '❌ NOT SET'
        WHEN commission_percentage = 0 THEN '⚠️ SET TO 0%'
        ELSE '✅ SET'
    END as status
FROM properties
WHERE
    property_name LIKE 'FLAT % - 3 WEST GATE'
    OR property_name LIKE 'PARKING SPACE % - 3 WEST GATE'
    OR property_name LIKE '%Knighton Hayes%'
    OR property_name LIKE '%Boden House%'
ORDER BY property_name;
```

**Expected Results**:
- Some properties will show `commission_percentage = NULL` (not set)
- Some may show `commission_percentage = 15.00` (already set)
- Some may differ from your CSV data (e.g., property shows 10% but CSV shows 15%)

---

## Proposed Enhancement

### Phase 1: Add Commission Field to CSV Format

**Update CSV Header** to include `commission_percentage`:

**Current Format** (8 columns):
```csv
property_reference,customer_name,customer_email,lease_start_date,lease_end_date,rent_amount,payment_day,lease_reference
```

**Proposed Format** (9 columns):
```csv
property_reference,customer_name,customer_email,lease_start_date,lease_end_date,rent_amount,payment_day,lease_reference,commission_percentage
```

**Example Data**:
```csv
property_reference,customer_name,customer_email,lease_start_date,lease_end_date,rent_amount,payment_day,lease_reference,commission_percentage
FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO & MR I HALII,,2025-02-27,,795.00,27,LEASE-BH-F1-2025,15.00
FLAT 2 - 3 WEST GATE,MR M K J AL BAYAT & MRS G B A AL ZANGANA,,2025-02-27,,740.00,27,LEASE-BH-F2-2025,15.00
Apartment F - Knighton Hayes,Riaz,,2024-12-22,,1125.00,22,LEASE-KH-F-2024,10.00
Boden House NG10 - Block Property,Uday Bhardwaj,,2025-12-22,,0.00,21,LEASE-OF-F30-2025,0.00
```

**Note**: `commission_percentage` is **optional** (can be empty) - if not provided, no warnings shown.

---

### Phase 2: Parse and Validate Commission Rates

**Code Changes to `LeaseImportWizardController.java`**:

#### 2.1. Update `ParsedLeaseRow` Class (line 874)

```java
public static class ParsedLeaseRow {
    public int rowNumber;
    public String rawPropertyReference;
    public String rawCustomerName;
    public String rawCustomerEmail;
    public String leaseStartDate;
    public String leaseEndDate;
    public String rentAmount;
    public String paymentDay;
    public String leaseReference;
    public String commissionPercentage;  // ✅ NEW

    // Getter for JSON serialization
    public String getCommissionPercentage() { return commissionPercentage; }
}
```

#### 2.2. Update CSV Parser (line 632)

```java
row.leaseReference = getValueOrEmpty(values, headerMap, "lease_reference");
row.commissionPercentage = getValueOrEmpty(values, headerMap, "commission_percentage");  // ✅ NEW
```

#### 2.3. Add Commission Validation Logic (new method around line 750)

```java
/**
 * Validate property commission rates against CSV data
 * Returns warnings if:
 * - Property has no commission set but CSV provides one
 * - Property commission differs from CSV value
 */
private List<CommissionWarning> validateCommissionRates(
        List<ParsedLeaseRow> rows,
        Map<String, Property> matchedProperties) {

    List<CommissionWarning> warnings = new ArrayList<>();
    Map<Long, String> processedProperties = new HashMap<>();  // Track properties we've checked

    for (ParsedLeaseRow row : rows) {
        // Skip if no commission provided in CSV
        if (row.commissionPercentage == null || row.commissionPercentage.isEmpty()) {
            continue;
        }

        // Get matched property
        Property property = matchedProperties.get(row.rawPropertyReference);
        if (property == null) continue;

        // Skip if we already checked this property
        if (processedProperties.containsKey(property.getId())) {
            continue;
        }
        processedProperties.put(property.getId(), row.rawPropertyReference);

        // Parse CSV commission value
        BigDecimal csvCommission;
        try {
            csvCommission = new BigDecimal(row.commissionPercentage);
        } catch (NumberFormatException e) {
            log.warn("Invalid commission percentage for {}: {}",
                    row.rawPropertyReference, row.commissionPercentage);
            continue;
        }

        // Check if property has commission set
        if (property.getCommissionPercentage() == null) {
            // Property has NO commission set
            warnings.add(new CommissionWarning(
                CommissionWarning.Type.NOT_SET,
                property.getId(),
                property.getPropertyName(),
                null,
                csvCommission
            ));
        } else {
            // Property HAS commission set - check if it differs
            BigDecimal propertyCommission = property.getCommissionPercentage();

            if (propertyCommission.compareTo(csvCommission) != 0) {
                warnings.add(new CommissionWarning(
                    CommissionWarning.Type.MISMATCH,
                    property.getId(),
                    property.getPropertyName(),
                    propertyCommission,
                    csvCommission
                ));
            }
        }
    }

    return warnings;
}
```

#### 2.4. Add CommissionWarning DTO (new class at end of file)

```java
public static class CommissionWarning {
    public enum Type {
        NOT_SET,     // Property has no commission rate
        MISMATCH     // Property commission differs from CSV
    }

    public Type type;
    public Long propertyId;
    public String propertyName;
    public BigDecimal currentRate;   // null if not set
    public BigDecimal csvRate;

    public CommissionWarning(Type type, Long propertyId, String propertyName,
                            BigDecimal currentRate, BigDecimal csvRate) {
        this.type = type;
        this.propertyId = propertyId;
        this.propertyName = propertyName;
        this.currentRate = currentRate;
        this.csvRate = csvRate;
    }

    // Getters for JSON
    public String getType() { return type.name(); }
    public Long getPropertyId() { return propertyId; }
    public String getPropertyName() { return propertyName; }
    public BigDecimal getCurrentRate() { return currentRate; }
    public BigDecimal getCsvRate() { return csvRate; }

    public String getMessage() {
        if (type == Type.NOT_SET) {
            return String.format("%s: No commission rate set (CSV suggests %.2f%%)",
                                propertyName, csvRate);
        } else {
            return String.format("%s: Commission rate is %.2f%% but CSV shows %.2f%%",
                                propertyName, currentRate, csvRate);
        }
    }
}
```

---

### Phase 3: Show Warnings to User

**Update `/validate` Endpoint Response** (line ~700):

```java
@PostMapping("/validate")
public ResponseEntity<?> validateLeases(@RequestBody Map<String, String> request) {
    // ... existing parsing code ...

    List<ParsedLeaseRow> rows = parseCsvData(csvData);

    // ... existing property/customer matching ...

    // ✅ NEW: Validate commission rates
    List<CommissionWarning> commissionWarnings = validateCommissionRates(rows, matchedProperties);

    // Build response
    Map<String, Object> response = new HashMap<>();
    response.put("success", true);
    response.put("rows", rows);
    response.put("propertyMatches", propertyMatches);
    response.put("customerMatches", customerMatches);
    response.put("commissionWarnings", commissionWarnings);  // ✅ NEW

    return ResponseEntity.ok(response);
}
```

---

### Phase 4: UI Updates

**Update `/import` Page Template** to show commission warnings:

**After validation results** (add new section):

```html
<!-- Commission Warnings Section -->
<div th:if="${commissionWarnings != null and !commissionWarnings.empty}"
     class="alert alert-warning mt-3">
    <h5><i class="fas fa-exclamation-triangle"></i> Commission Rate Issues Detected</h5>

    <p>The following properties have commission rate issues:</p>

    <!-- Properties with no commission set -->
    <div th:if="${commissionWarnings.stream().filter(w -> w.type == 'NOT_SET').count() > 0}">
        <h6><strong>Properties Without Commission Rates:</strong></h6>
        <ul>
            <li th:each="warning : ${commissionWarnings}"
                th:if="${warning.type == 'NOT_SET'}">
                <strong th:text="${warning.propertyName}">FLAT 1</strong>:
                No commission set
                (CSV suggests <span th:text="${warning.csvRate}">15.00</span>%)
            </li>
        </ul>
    </div>

    <!-- Properties with mismatched commission -->
    <div th:if="${commissionWarnings.stream().filter(w -> w.type == 'MISMATCH').count() > 0}">
        <h6><strong>Properties With Different Commission Rates:</strong></h6>
        <ul>
            <li th:each="warning : ${commissionWarnings}"
                th:if="${warning.type == 'MISMATCH'}">
                <strong th:text="${warning.propertyName}">FLAT 2</strong>:
                Currently <span th:text="${warning.currentRate}">10.00</span>%
                but CSV shows <span th:text="${warning.csvRate}">15.00</span>%
            </li>
        </ul>
    </div>

    <!-- Action buttons -->
    <div class="mt-3">
        <p><strong>Would you like to update these property commission rates from the CSV data?</strong></p>
        <button type="button" class="btn btn-primary" onclick="updateCommissionRates()">
            <i class="fas fa-sync-alt"></i> Yes, Update Commission Rates
        </button>
        <button type="button" class="btn btn-secondary" onclick="continueWithoutUpdate()">
            <i class="fas fa-times"></i> No, Continue Without Updating
        </button>
    </div>
</div>
```

---

### Phase 5: Commission Update Endpoint

**Add New Endpoint** to update property commission rates:

```java
/**
 * Update property commission rates from CSV data
 * Called when user clicks "Yes, Update Commission Rates"
 */
@PostMapping("/update-commission-rates")
public ResponseEntity<?> updateCommissionRates(@RequestBody CommissionUpdateRequest request) {
    log.info("📊 Updating commission rates for {} properties", request.updates.size());

    int updated = 0;
    List<String> errors = new ArrayList<>();

    for (CommissionUpdate update : request.updates) {
        try {
            Property property = propertyRepository.findById(update.propertyId)
                .orElseThrow(() -> new RuntimeException("Property not found: " + update.propertyId));

            property.setCommissionPercentage(update.newRate);
            propertyRepository.save(property);

            log.info("✅ Updated commission for {}: {} -> {}",
                    property.getPropertyName(),
                    update.oldRate != null ? update.oldRate + "%" : "not set",
                    update.newRate + "%");
            updated++;

        } catch (Exception e) {
            log.error("❌ Failed to update property {}: {}", update.propertyId, e.getMessage());
            errors.add("Failed to update property ID " + update.propertyId + ": " + e.getMessage());
        }
    }

    Map<String, Object> response = new HashMap<>();
    response.put("success", errors.isEmpty());
    response.put("updated", updated);
    response.put("errors", errors);

    return ResponseEntity.ok(response);
}

// Request DTO
public static class CommissionUpdateRequest {
    public List<CommissionUpdate> updates;
}

public static class CommissionUpdate {
    public Long propertyId;
    public BigDecimal oldRate;  // null if not set
    public BigDecimal newRate;
}
```

---

## Updated CSV Format with Commission

To use this enhancement, regenerate your CSV with the commission column:

```csv
property_reference,customer_name,customer_email,lease_start_date,lease_end_date,rent_amount,payment_day,lease_reference,commission_percentage
FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO & MR I HALII,,2025-02-27,,795.00,27,LEASE-BH-F1-2025,15.00
FLAT 2 - 3 WEST GATE,MR M K J AL BAYAT & MRS G B A AL ZANGANA,,2025-02-27,,740.00,27,LEASE-BH-F2-2025,15.00
FLAT 3 - 3 WEST GATE,MR T WHYTE,,2025-03-11,,740.00,11,LEASE-BH-F3-2025,15.00
Apartment F - Knighton Hayes,Riaz,,2024-12-22,,1125.00,22,LEASE-KH-F-2024,10.00
Boden House NG10 - Block Property,Uday Bhardwaj,,2025-12-22,,0.00,21,LEASE-OF-F30-2025,0.00
```

I can regenerate your entire CSV with this new column if you'd like.

---

## Benefits

### For Initial Setup
- ✅ **One-time bulk update** of property commission rates
- ✅ **Catches configuration gaps** before they cause calculation errors
- ✅ **Audit trail** of commission rate changes

### For Ongoing Operations
- ✅ **Data quality checks** - ensures properties have commission configured
- ✅ **Prevents silent failures** - commission calculations won't silently use 0% or fail
- ✅ **Visibility** - user sees what will be updated before proceeding

### For Reporting
- ✅ **Accurate financial calculations** from day one
- ✅ **Correct owner statements** with proper management fees
- ✅ **No retroactive corrections needed**

---

## Implementation Effort

### Estimated Time
- **Backend Changes**: 3-4 hours
  - Add commission field parsing
  - Implement validation logic
  - Create update endpoint
  - Write tests

- **Frontend Changes**: 2-3 hours
  - Update UI template
  - Add JavaScript for update buttons
  - Style warning section

- **Testing**: 2-3 hours
  - Test with various commission scenarios
  - Test update workflow
  - Verify property settings changed correctly

**Total**: 7-10 hours (1-1.5 days)

---

## Risks and Considerations

### 1. **Bulk Updates Can Be Destructive**
**Risk**: User accidentally updates all properties with wrong rates

**Mitigation**:
- Show clear summary of what will change
- Require explicit confirmation
- Log all changes for audit
- Support "undo" by showing previous values

### 2. **Commission Rates May Vary By Lease**
**Risk**: Same property might have different commission rates for different lease periods

**Current Assumption**: Commission is **property-level** not **lease-level**

**If commission varies by lease**: Would need different approach:
- Store commission on `invoices` (lease) table instead
- Property commission becomes "default" for new leases
- Existing leases keep their own rate

### 3. **Commission Amount vs Percentage**
**Your Data**: Only has percentage (15%, 10%, 0%)

**Schema Supports**: Both `commission_percentage` AND `commission_amount` (flat fee)

**Decision Needed**:
- Do you ever charge flat fee commission? (e.g., £50/month regardless of rent)
- Or always percentage-based?

If always percentage, we can ignore `commission_amount` field.

---

## Alternative: Manual Update Script

If you prefer **one-time bulk update** without UI changes:

```sql
-- Update all 3 West Gate flats to 15%
UPDATE properties
SET commission_percentage = 15.00
WHERE property_name LIKE 'FLAT % - 3 WEST GATE';

-- Update parking spaces to 15%
UPDATE properties
SET commission_percentage = 15.00
WHERE property_name LIKE 'PARKING SPACE % - 3 WEST GATE';

-- Update Knighton Hayes to 10%
UPDATE properties
SET commission_percentage = 10.00
WHERE property_name LIKE '%Knighton Hayes%';

-- Update Boden House Block to 0%
UPDATE properties
SET commission_percentage = 0.00
WHERE property_name LIKE '%Boden House NG10%';

-- Verify
SELECT property_name, commission_percentage
FROM properties
WHERE property_name LIKE '%3 WEST GATE%'
   OR property_name LIKE '%Knighton Hayes%'
   OR property_name LIKE '%Boden House%'
ORDER BY property_name;
```

**Pros**: Quick, no code changes needed
**Cons**: Manual process, no validation, user doesn't see warnings during import

---

## Recommendation

### For Your Immediate Need

**Option A**: Run manual SQL update script now (5 minutes)
- Gets commission rates configured immediately
- Unblocks current lease import
- Can enhance wizard later

**Option B**: Implement full enhancement (1-2 days)
- Better user experience
- Catches future mismatches
- Self-service data quality checks

### Long-Term

**Implement the enhancement** for:
- Future lease imports (new properties)
- Ongoing data quality assurance
- Better user experience

---

## Next Steps

1. **Run database query** to check current commission status:
   ```sql
   SELECT property_name, commission_percentage, commission_amount
   FROM properties
   ORDER BY property_name;
   ```

2. **Decide on approach**:
   - Quick fix: Manual SQL update
   - Long-term: Implement enhancement

3. **If implementing enhancement**:
   - Review and approve this proposal
   - Prioritize in development backlog
   - I can implement all code changes

4. **If doing manual fix**:
   - Run SQL update script above
   - Verify with query
   - Proceed with current lease import

---

## Questions for You

1. **Do all properties currently have commission rates set?** (Need to run query)

2. **Should commission be property-level or lease-level?**
   - Property-level: All leases at FLAT 1 always use same rate
   - Lease-level: Different leases at FLAT 1 could have different rates

3. **Do you use flat-fee commission or always percentage?**
   - Percentage only: Ignore `commission_amount` field
   - Sometimes flat fee: Need to capture both

4. **Preferred approach for immediate need?**
   - Manual SQL update now, enhance later?
   - Implement enhancement first, then import?

---

**Document Version**: 1.0
**Date**: October 27, 2025
**Status**: Awaiting Decision


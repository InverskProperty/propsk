# Data Migration Strategy: Converting Fictional Property Owners

## Overview
This document outlines the strategy for converting existing "fictional" property owners (employees/delegated users) to the new `DELEGATED_USER` customer type before PayProp imports real legal owners.

## Current Problem
- Users like **Piyush** are classified as `PROPERTY_OWNER` but are actually employees
- When PayProp imports real legal owners, there will be conflicts
- Need to identify and reclassify fictional owners before PayProp sync

## Identification Criteria

### Manual Property Owners (Potential Fictional Owners)
```sql
SELECT customer_id, name, email, customer_type, data_source, created_at
FROM customers
WHERE customer_type = 'PROPERTY_OWNER'
AND data_source = 'MANUAL'
ORDER BY created_at DESC;
```

### Red Flags for Fictional Owners
1. **Recent Creation**: Created manually in the last few months
2. **Employee Email Patterns**: Email domains matching company emails
3. **No PayProp Integration**: `payprop_customer_id` is null or empty
4. **No Real Property Ownership**: Not in actual property ownership records

## Migration Process

### Phase 1: Audit Current Property Owners
```sql
-- Get all manual property owners for review
SELECT
    c.customer_id,
    c.name,
    c.email,
    c.customer_type,
    c.data_source,
    c.created_at,
    c.payprop_customer_id,
    c.description,
    COUNT(cpa.property_id) as property_count
FROM customers c
LEFT JOIN customer_property_assignment cpa ON c.customer_id = cpa.customer_id
WHERE c.customer_type = 'PROPERTY_OWNER'
AND c.data_source = 'MANUAL'
GROUP BY c.customer_id
ORDER BY c.created_at DESC;
```

### Phase 2: Identify Specific Cases

#### Known Fictional Owners (Based on Investigation)
- **Piyush**: Employee who works for owner, not actual owner
- **Others**: TBD based on audit results

#### Identification Questions for Each Record:
1. Is this person an employee of the property management company?
2. Do they work for an actual property owner rather than owning properties themselves?
3. Were they created to give portal access rather than reflect legal ownership?
4. Do they need property owner permissions but aren't legal owners?

### Phase 3: Safe Migration

#### Step 1: Backup Current Data
```sql
-- Create backup table
CREATE TABLE customers_backup_pre_migration AS
SELECT * FROM customers
WHERE customer_type = 'PROPERTY_OWNER'
AND data_source = 'MANUAL';
```

#### Step 2: Convert Identified Fictional Owners
```sql
-- Convert specific fictional owners to DELEGATED_USER
UPDATE customers
SET customer_type = 'DELEGATED_USER',
    entity_type = 'delegated_user',
    is_property_owner = false,
    description = CONCAT(COALESCE(description, ''), ' [Converted from fictional property owner to delegated user]')
WHERE customer_id IN (
    -- List of identified fictional owner IDs
    -- Example: Customer ID for Piyush and others
);
```

#### Step 3: Verify Migration
```sql
-- Verify conversion
SELECT customer_id, name, email, customer_type, entity_type, description
FROM customers
WHERE customer_type = 'DELEGATED_USER'
ORDER BY customer_id;
```

## Testing Strategy

### Pre-Migration Testing
1. **Login Test**: Verify current property owners can log in
2. **Permission Test**: Verify they have correct dashboard access
3. **Functionality Test**: Verify they can access portfolios, properties, etc.

### Post-Migration Testing
1. **Login Test**: Verify converted users can still log in
2. **Permission Continuity**: Verify they still have same access rights
3. **Dashboard Routing**: Verify they still go to property owner dashboard
4. **Feature Access**: Verify all previously accessible features still work

### Expected Results
- **No functional changes** for converted users
- **Same login experience**
- **Same dashboard and features**
- **Clean separation** for PayProp integration

## Rollback Plan

If issues arise, rollback is simple:
```sql
-- Rollback by restoring from backup
UPDATE customers c
JOIN customers_backup_pre_migration b ON c.customer_id = b.customer_id
SET c.customer_type = b.customer_type,
    c.entity_type = b.entity_type,
    c.is_property_owner = b.is_property_owner,
    c.description = b.description
WHERE c.customer_type = 'DELEGATED_USER';
```

## Implementation Timeline

### Immediate (Before PayProp Import)
1. **Audit existing property owners** (1-2 hours)
2. **Identify fictional owners** (manually review audit results)
3. **Test with one user** (convert one fictional owner and test)
4. **Full migration** (convert all identified fictional owners)
5. **Verification testing** (ensure no functionality lost)

### Post-Migration
1. **PayProp import safe** (real owners will be separate)
2. **Monitor system** (ensure no unexpected issues)
3. **Clean up backup tables** (after confirmation migration worked)

## Success Criteria

1. ✅ All fictional property owners converted to `DELEGATED_USER`
2. ✅ No loss of functionality for converted users
3. ✅ PayProp can import real owners without conflicts
4. ✅ Clear separation between legal ownership and portal access
5. ✅ System ready for production PayProp integration

## Risk Mitigation

- **Low Risk**: DELEGATED_USER has same permissions as PROPERTY_OWNER
- **Backup Strategy**: Full backup before migration
- **Gradual Approach**: Test with one user first
- **Quick Rollback**: Simple SQL to reverse changes if needed
- **Verification Steps**: Multiple test points to ensure functionality preserved
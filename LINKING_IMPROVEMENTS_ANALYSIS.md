# Transaction Linking System - What We Fixed & Lessons Learned

**Date:** 2025-10-22
**Achievement:** 0% → 91% transaction linkage (762/840 transactions)
**Focus:** Understanding what was broken and how we fixed it

---

## The Journey: 0% → 91% Linkage

### Error 1: Property ID Type Mismatch (0% → 79% linkage)

**Initial Problem:**
```sql
-- This query linked ZERO transactions
UPDATE financial_transactions ft
INNER JOIN invoices i ON CAST(i.property_id AS CHAR) = ft.property_id
WHERE ft.invoice_id IS NULL;
```

**Root Cause:**
- `financial_transactions.property_id` stores PayProp ID (string: "GvJDP9KaJz")
- `invoices.property_id` stores numeric local Property ID (integer: 43)
- Casting `43` to `"43"` never matches `"GvJDP9KaJz"`

**The Fix:**
```sql
-- Join through properties table using payprop_id
UPDATE financial_transactions ft
INNER JOIN properties p ON p.payprop_id = ft.property_id  -- ✅ String-to-string match
INNER JOIN invoices i ON i.property_id = p.id              -- ✅ Then join to lease
WHERE ft.invoice_id IS NULL;
```

**Result:** 662 transactions linked (£262,121.31)

**Lesson Learned:**
- **PayProp uses STRING IDs**, local system uses NUMERIC IDs
- **Properties table is the BRIDGE** between PayProp and local data
- `properties.payprop_id` is the critical linking field
- **Never assume ID types match** - always check the schema first

---

### Error 2: Active-Only Lease Filter (79% → 88% linkage)

**Problem:**
```sql
-- This excluded valid matches
WHERE i.is_active = true  -- ❌ Only matches ACTIVE leases
```

**Scenario:**
- Tenant lease ended August 31st (lease marked `is_active = false`)
- Final rent payment arrives September 5th
- Transaction date (Sept 5) falls within lease period (if we use end_date)
- But query skipped it because lease is inactive

**The Fix:**
```sql
-- Remove is_active filter, trust the dates
WHERE ft.transaction_date BETWEEN i.start_date AND COALESCE(i.end_date, '2099-12-31')
  -- Don't filter by is_active
```

**Result:** +76 transactions linked

**Lesson Learned:**
- **is_active is a STATUS flag, not a validity flag**
- Ended leases are still VALID for their date range
- **Trust the dates, not the status** when matching historical transactions
- Active/inactive should only affect FUTURE operations, not historical linking

---

### Error 3: No Grace Periods (88% → 90% linkage)

**Problem:**
```sql
-- Strict date matching missed edge cases
WHERE ft.transaction_date BETWEEN i.start_date AND COALESCE(i.end_date, '2099-12-31')
```

**Real-World Scenarios Missed:**
1. **Early Payments:** Tenant pays 3 days before lease starts (eager tenant)
2. **Late Payments:** Final payment arrives 15 days after lease ends (arrears)
3. **Processing Delays:** Rent due on 1st, clears bank on 4th

**The Fix:**
```sql
WHERE ft.transaction_date BETWEEN
    DATE_SUB(i.start_date, INTERVAL 7 DAY)       -- ✅ 7 days early grace
    AND DATE_ADD(COALESCE(i.end_date, '2099-12-31'), INTERVAL 30 DAY)  -- ✅ 30 days late grace
```

**Result:** +17 transactions linked (90% total)

**Lesson Learned:**
- **Real-world payments don't respect exact dates**
- Grace periods must account for:
  - Early payments (tenant convenience)
  - Late payments (arrears, processing delays)
  - Data entry timing differences
- **7 days before / 30 days after** worked well for your data

---

### Error 4: Data Quality - Wrong Property Assignment (90% → 91% linkage)

**Problem:**
```sql
-- LEASE-BH-F19-2025B was assigned to wrong property
SELECT property_id, lease_reference FROM invoices WHERE lease_reference = 'LEASE-BH-F19-2025B';
-- Result: property_id = 44 (Flat 20)  ❌ WRONG

-- But transactions had correct property
SELECT DISTINCT property_id FROM financial_transactions WHERE description LIKE '%Flat 19%';
-- Result: property_id = "GvJDP9KaJz" (Flat 19)  ✅ CORRECT
```

**Root Cause:**
- Lease import CSV had property name slightly wrong
- Fuzzy matching picked Flat 20 instead of Flat 19
- 11 transactions for Flat 19 couldn't match because lease was on Flat 20

**The Fix:**
```sql
-- Corrected the property assignment
UPDATE invoices
SET property_id = 43  -- Flat 19
WHERE lease_reference = 'LEASE-BH-F19-2025B';
```

**Result:** +21 transactions linked (now at 91%)

**Lesson Learned:**
- **Fuzzy matching can be WRONG**
- Need to **verify matches** before finalizing import
- **Show user the matched property** and ask for confirmation
- Consider **match confidence score** (90%+ = auto, 50-89% = confirm, <50% = reject)

---

## Summary: What Made Linkage Work

### Technical Fixes

| Fix | Impact | Key Insight |
|-----|--------|-------------|
| Join through properties.payprop_id | 0% → 79% | PayProp IDs ≠ Local IDs, need bridge table |
| Include inactive leases | +9% | Status ≠ Validity, trust dates not flags |
| Add grace periods (7d/30d) | +2% | Real world is messy, dates aren't exact |
| Fix data quality issues | +1% | Fuzzy matching needs human verification |

### The Real Problem: Impedance Mismatch

**PayProp World:**
- String IDs ("GvJDP9KaJz")
- Property-centric (property owns transactions)
- No concept of "leases" (just invoices)
- Transactions reference property + tenant separately

**Local CRM World:**
- Numeric IDs (43, 44, 45)
- Lease-centric (Invoice entity = lease contract)
- Leases link property + tenant together
- Transactions should reference lease directly

**The Bridge:**
```
PayProp Transaction                  Properties Table                Invoice (Lease)
┌─────────────────────┐             ┌──────────────────┐           ┌─────────────────┐
│ property_id (string)│────────────>│ payprop_id (str) │           │                 │
│ "GvJDP9KaJz"        │             │ id (numeric)     │<──────────│ property_id (int)│
│                     │             │ 43               │           │ 43              │
│ tenant_id (string)  │             └──────────────────┘           │                 │
│ transaction_date    │                                            │ customer_id (int)│
│ 2025-08-15          │                                            │ start_date      │
└─────────────────────┘                                            │ end_date        │
                                                                   └─────────────────┘
```

**Linking Logic:**
1. Match PayProp property_id → properties.payprop_id → get local property ID
2. Find invoices where property_id = local ID
3. Filter by transaction_date within (start_date - 7d) to (end_date + 30d)
4. Prefer matches where tenant IDs also match
5. Set financial_transactions.invoice_id = matched invoice ID

---

## What You Need: Persistent Mapping Cache

### The Problem You're Solving

**Current State:**
Every time you import historical transactions, the system:
1. Fuzzy matches property names → might match wrong property
2. Fuzzy matches customer names → might match wrong customer
3. Can't remember your manual corrections from last import
4. Forces you to re-verify the same mappings repeatedly

**Desired State:**
1. User imports transactions, system attempts auto-match
2. For ambiguous matches, user **clarifies once** (e.g., "Flat 1" → Property ID 43)
3. System **stores this mapping** as "canonical"
4. Next import with "Flat 1" **auto-uses Property ID 43** without asking again
5. Mappings are **revisable** if data structure changes

---

## Proposed Solution: Import Mapping Cache

### Database Schema

```sql
CREATE TABLE import_mapping_cache (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    -- What was in the import file
    source_system VARCHAR(50) NOT NULL,  -- 'CSV', 'PAYPROP', 'JSON'
    source_reference VARCHAR(255) NOT NULL,  -- The string from import file
    mapping_type VARCHAR(50) NOT NULL,  -- 'PROPERTY', 'CUSTOMER', 'CATEGORY'

    -- What we mapped it to
    target_entity_type VARCHAR(50) NOT NULL,  -- 'Property', 'Customer', 'Category'
    target_entity_id BIGINT NOT NULL,  -- The resolved ID

    -- Confidence and validation
    match_confidence_score INT,  -- 0-100, how sure the fuzzy match was
    manual_override BOOLEAN DEFAULT false,  -- Did user manually select this?

    -- Context for revisability
    source_context TEXT,  -- JSON: {"property_name": "Flat 1", "customer_email": "john@example.com"}
    target_context TEXT,  -- JSON: {"property_name": "Flat 1 - 3 West Gate", "id": 43}

    -- Audit
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id BIGINT,
    last_used_at TIMESTAMP,
    usage_count INT DEFAULT 1,

    -- Revision tracking
    superseded_by_mapping_id BIGINT,  -- If this mapping was revised
    is_active BOOLEAN DEFAULT true,

    UNIQUE KEY uk_mapping (source_system, source_reference, mapping_type),
    KEY idx_target (target_entity_type, target_entity_id),
    KEY idx_active (is_active, source_system, mapping_type)
);

-- Store revision history
CREATE TABLE import_mapping_revisions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mapping_id BIGINT NOT NULL,

    old_target_entity_id BIGINT,
    new_target_entity_id BIGINT,

    reason VARCHAR(255),  -- "Data error", "Property renamed", "User correction"
    revised_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    revised_by_user_id BIGINT,

    FOREIGN KEY (mapping_id) REFERENCES import_mapping_cache(id)
);
```

---

### Service Layer: Import Mapping Cache Service

```java
package site.easy.to.build.crm.service.cache;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

/**
 * Persistent mapping cache for import operations
 * Remembers user's clarifications and property/customer matches
 */
@Service
public class ImportMappingCacheService {

    @Autowired
    private ImportMappingCacheRepository mappingRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    /**
     * Lookup cached mapping for a property reference
     *
     * @param sourceReference The property name/reference from import file
     * @param sourceSystem "CSV", "PAYPROP", "JSON"
     * @return Cached property ID if exists, empty if not cached or invalidated
     */
    public Optional<CachedMapping> lookupPropertyMapping(String sourceReference, String sourceSystem) {
        return mappingRepository
            .findBySourceReferenceAndMappingTypeAndSourceSystemAndIsActive(
                sourceReference, "PROPERTY", sourceSystem, true)
            .map(this::toCachedMapping);
    }

    /**
     * Lookup cached mapping for a customer reference
     */
    public Optional<CachedMapping> lookupCustomerMapping(String sourceReference, String sourceSystem) {
        return mappingRepository
            .findBySourceReferenceAndMappingTypeAndSourceSystemAndIsActive(
                sourceReference, "CUSTOMER", sourceSystem, true)
            .map(this::toCachedMapping);
    }

    /**
     * Store a new mapping (from fuzzy match or manual selection)
     *
     * @param sourceReference What was in the import file (e.g., "Flat 1")
     * @param targetEntityId What we mapped it to (e.g., Property ID 43)
     * @param matchConfidence 0-100 score from fuzzy matching
     * @param manualOverride true if user manually selected, false if auto-matched
     */
    @Transactional
    public void storeMapping(String sourceReference,
                            String sourceSystem,
                            String mappingType,
                            String targetEntityType,
                            Long targetEntityId,
                            Integer matchConfidence,
                            boolean manualOverride,
                            Long userId) {

        // Check if mapping already exists
        Optional<ImportMappingCache> existing = mappingRepository
            .findBySourceReferenceAndMappingTypeAndSourceSystemAndIsActive(
                sourceReference, mappingType, sourceSystem, true);

        if (existing.isPresent()) {
            // Update existing mapping
            ImportMappingCache cache = existing.get();

            // If target changed, create revision
            if (!cache.getTargetEntityId().equals(targetEntityId)) {
                createRevision(cache, targetEntityId, "Updated during import", userId);
                cache.setTargetEntityId(targetEntityId);
            }

            cache.setLastUsedAt(LocalDateTime.now());
            cache.setUsageCount(cache.getUsageCount() + 1);
            cache.setMatchConfidenceScore(Math.max(cache.getMatchConfidenceScore(), matchConfidence));
            cache.setManualOverride(manualOverride || cache.getManualOverride());

            mappingRepository.save(cache);

        } else {
            // Create new mapping
            ImportMappingCache cache = new ImportMappingCache();
            cache.setSourceSystem(sourceSystem);
            cache.setSourceReference(sourceReference);
            cache.setMappingType(mappingType);
            cache.setTargetEntityType(targetEntityType);
            cache.setTargetEntityId(targetEntityId);
            cache.setMatchConfidenceScore(matchConfidence);
            cache.setManualOverride(manualOverride);
            cache.setCreatedByUserId(userId);

            // Store context for revisability
            cache.setSourceContext(buildSourceContext(sourceReference, mappingType));
            cache.setTargetContext(buildTargetContext(targetEntityType, targetEntityId));

            mappingRepository.save(cache);

            log.info("✅ Cached mapping: '{}' ({}) → {} ID {}",
                    sourceReference, sourceSystem, targetEntityType, targetEntityId);
        }
    }

    /**
     * Revise an existing mapping (e.g., user corrects a wrong match)
     */
    @Transactional
    public void reviseMapping(String sourceReference,
                             String sourceSystem,
                             String mappingType,
                             Long newTargetEntityId,
                             String reason,
                             Long userId) {

        Optional<ImportMappingCache> existing = mappingRepository
            .findBySourceReferenceAndMappingTypeAndSourceSystemAndIsActive(
                sourceReference, mappingType, sourceSystem, true);

        if (existing.isEmpty()) {
            throw new IllegalArgumentException("No active mapping found for: " + sourceReference);
        }

        ImportMappingCache cache = existing.get();
        Long oldTargetId = cache.getTargetEntityId();

        // Create revision record
        createRevision(cache, newTargetEntityId, reason, userId);

        // Update mapping
        cache.setTargetEntityId(newTargetEntityId);
        cache.setTargetContext(buildTargetContext(cache.getTargetEntityType(), newTargetEntityId));
        mappingRepository.save(cache);

        log.info("🔄 Revised mapping: '{}' → {} ID {} (was {}). Reason: {}",
                sourceReference, cache.getTargetEntityType(), newTargetEntityId, oldTargetId, reason);
    }

    /**
     * Invalidate a mapping (e.g., property was deleted)
     */
    @Transactional
    public void invalidateMapping(Long mappingId, String reason, Long userId) {
        ImportMappingCache cache = mappingRepository.findById(mappingId)
            .orElseThrow(() -> new IllegalArgumentException("Mapping not found: " + mappingId));

        cache.setIsActive(false);
        mappingRepository.save(cache);

        createRevision(cache, null, "Invalidated: " + reason, userId);

        log.warn("⚠️ Invalidated mapping: '{}' → {} ID {}. Reason: {}",
                cache.getSourceReference(), cache.getTargetEntityType(), cache.getTargetEntityId(), reason);
    }

    /**
     * Get all mappings for a specific target entity
     * Useful for: "Show me all import references that map to Property ID 43"
     */
    public List<ImportMappingCache> getMappingsForEntity(String entityType, Long entityId) {
        return mappingRepository.findByTargetEntityTypeAndTargetEntityIdAndIsActive(
            entityType, entityId, true);
    }

    /**
     * Validate all active mappings (check if target entities still exist)
     * Run this during system health checks
     */
    @Transactional
    public MappingValidationReport validateAllMappings() {
        MappingValidationReport report = new MappingValidationReport();

        List<ImportMappingCache> allMappings = mappingRepository.findByIsActive(true);

        for (ImportMappingCache mapping : allMappings) {
            boolean targetExists = false;

            switch (mapping.getTargetEntityType()) {
                case "Property":
                    targetExists = propertyRepository.existsById(mapping.getTargetEntityId());
                    break;
                case "Customer":
                    targetExists = customerRepository.existsById(mapping.getTargetEntityId());
                    break;
            }

            if (!targetExists) {
                report.addInvalidMapping(mapping, "Target entity no longer exists");
                invalidateMapping(mapping.getId(), "Target entity deleted", null);
            }
        }

        return report;
    }

    // ===== Helper Methods =====

    private void createRevision(ImportMappingCache mapping, Long newTargetId, String reason, Long userId) {
        ImportMappingRevision revision = new ImportMappingRevision();
        revision.setMappingId(mapping.getId());
        revision.setOldTargetEntityId(mapping.getTargetEntityId());
        revision.setNewTargetEntityId(newTargetId);
        revision.setReason(reason);
        revision.setRevisedByUserId(userId);

        mappingRevisionRepository.save(revision);
    }

    private String buildSourceContext(String reference, String mappingType) {
        // Store the raw reference and any additional context from import
        Map<String, Object> context = new HashMap<>();
        context.put("reference", reference);
        context.put("mapping_type", mappingType);
        return new ObjectMapper().writeValueAsString(context);
    }

    private String buildTargetContext(String entityType, Long entityId) {
        // Store the current entity details for human-readable revision history
        Map<String, Object> context = new HashMap<>();
        context.put("entity_type", entityType);
        context.put("entity_id", entityId);

        if ("Property".equals(entityType)) {
            Property property = propertyRepository.findById(entityId).orElse(null);
            if (property != null) {
                context.put("property_name", property.getPropertyName());
                context.put("payprop_id", property.getPaypropId());
            }
        } else if ("Customer".equals(entityType)) {
            Customer customer = customerRepository.findById(entityId).orElse(null);
            if (customer != null) {
                context.put("customer_name", customer.getName());
                context.put("email", customer.getEmail());
            }
        }

        return new ObjectMapper().writeValueAsString(context);
    }

    private CachedMapping toCachedMapping(ImportMappingCache cache) {
        CachedMapping mapping = new CachedMapping();
        mapping.targetEntityId = cache.getTargetEntityId();
        mapping.matchConfidence = cache.getMatchConfidenceScore();
        mapping.manualOverride = cache.getManualOverride();
        mapping.usageCount = cache.getUsageCount();
        mapping.lastUsed = cache.getLastUsedAt();
        return mapping;
    }

    // ===== DTOs =====

    public static class CachedMapping {
        public Long targetEntityId;
        public Integer matchConfidence;
        public Boolean manualOverride;
        public Integer usageCount;
        public LocalDateTime lastUsed;
    }

    public static class MappingValidationReport {
        private List<InvalidMapping> invalidMappings = new ArrayList<>();

        public void addInvalidMapping(ImportMappingCache mapping, String reason) {
            invalidMappings.add(new InvalidMapping(mapping, reason));
        }

        public List<InvalidMapping> getInvalidMappings() {
            return invalidMappings;
        }

        public int getInvalidCount() {
            return invalidMappings.size();
        }
    }

    public static class InvalidMapping {
        public final ImportMappingCache mapping;
        public final String reason;

        public InvalidMapping(ImportMappingCache mapping, String reason) {
            this.mapping = mapping;
            this.reason = reason;
        }
    }
}
```

---

### Integration: Historical Transaction Import with Cache

**Update:** `HistoricalTransactionImportService.java`

```java
@Autowired
private ImportMappingCacheService mappingCache;

private Property matchProperty(String propertyRef, String sourceSystem, Long userId) {
    // Step 1: Check cache first
    Optional<ImportMappingCacheService.CachedMapping> cached =
        mappingCache.lookupPropertyMapping(propertyRef, sourceSystem);

    if (cached.isPresent()) {
        Property property = propertyRepository.findById(cached.get().targetEntityId).orElse(null);
        if (property != null) {
            log.info("✅ CACHE HIT: '{}' → Property ID {} (used {} times)",
                    propertyRef, property.getId(), cached.get().usageCount);

            // Update usage stats
            mappingCache.storeMapping(propertyRef, sourceSystem, "PROPERTY", "Property",
                property.getId(), cached.get().matchConfidence, cached.get().manualOverride, userId);

            return property;
        } else {
            // Cached property no longer exists
            log.warn("⚠️ CACHE STALE: Property ID {} no longer exists", cached.get().targetEntityId);
            mappingCache.invalidateMapping(propertyRef, sourceSystem, "PROPERTY",
                "Property deleted", userId);
        }
    }

    // Step 2: Cache miss - perform fuzzy matching
    log.info("🔍 CACHE MISS: Performing fuzzy match for '{}'", propertyRef);

    List<PropertyMatch> matches = fuzzyMatchProperties(propertyRef);

    if (matches.isEmpty()) {
        log.error("❌ No property matches for: {}", propertyRef);
        return null;
    }

    PropertyMatch bestMatch = matches.get(0);

    // Step 3: Auto-accept high-confidence matches, ask user for low-confidence
    if (bestMatch.score >= 90) {
        // High confidence - auto-accept and cache
        Property property = propertyRepository.findById(bestMatch.propertyId).orElse(null);

        mappingCache.storeMapping(propertyRef, sourceSystem, "PROPERTY", "Property",
            property.getId(), bestMatch.score, false, userId);

        log.info("✅ AUTO-MATCHED ({}%): '{}' → {} (ID {})",
                bestMatch.score, propertyRef, property.getPropertyName(), property.getId());

        return property;

    } else {
        // Low confidence - need user confirmation
        log.warn("⚠️ AMBIGUOUS MATCH: '{}' has {} potential matches",
                propertyRef, matches.size());

        // Return match but DON'T cache yet (wait for user confirmation)
        // In interactive import, this would show confirmation dialog
        return propertyRepository.findById(bestMatch.propertyId).orElse(null);
    }
}

/**
 * User confirms a fuzzy match - cache it for future imports
 */
public void confirmPropertyMatch(String propertyRef, Long propertyId, String sourceSystem, Long userId) {
    mappingCache.storeMapping(propertyRef, sourceSystem, "PROPERTY", "Property",
        propertyId, 100, true, userId);  // manualOverride = true

    log.info("✅ USER CONFIRMED: '{}' → Property ID {}", propertyRef, propertyId);
}

/**
 * User corrects a wrong match
 */
public void correctPropertyMatch(String propertyRef, Long oldPropertyId, Long newPropertyId,
                                String sourceSystem, Long userId) {
    mappingCache.reviseMapping(propertyRef, sourceSystem, "PROPERTY",
        newPropertyId, "User correction during import", userId);

    log.info("🔄 USER CORRECTED: '{}' → Property ID {} (was {})",
            propertyRef, newPropertyId, oldPropertyId);
}
```

---

### UI: Import with Mapping Review

**Flow:**

```
┌─────────────────────────────────────────────────────────────────┐
│ Step 1: Upload CSV                                              │
│ [Choose File] historical_transactions.csv                       │
│ [Parse]                                                         │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ Step 2: Review Mappings (100 rows parsed)                      │
├─────────────────────────────────────────────────────────────────┤
│ ✅ 85 rows: Auto-matched (cached or high confidence)           │
│ ⚠️  12 rows: Need confirmation (low confidence matches)        │
│ ❌ 3 rows: No match found                                      │
│                                                                  │
│ [Review Ambiguous Mappings]                                     │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ Ambiguous Mappings Review                                       │
├─────────────────────────────────────────────────────────────────┤
│ Row 12: Property "Flat 1"                                       │
│ ┌─────────────────────────────────────────────────────────────┐│
│ │ Best Match (78%): Flat 1 - 3 West Gate (ID: 43)            ││
│ │ [✓ Use This]  [Choose Different]                           ││
│ │                                                             ││
│ │ Other Matches:                                              ││
│ │ • Flat 10 - 3 West Gate (ID: 52) - 65%                     ││
│ │ • Flat 1 - Knighton Hayes (ID: 18) - 60%                   ││
│ └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│ Row 24: Customer "John Smith"                                   │
│ ┌─────────────────────────────────────────────────────────────┐│
│ │ Best Match (72%): Mr John Smith (ID: 156)                  ││
│ │ [✓ Use This]  [Choose Different]  [Create New]            ││
│ └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│ [Save Mappings & Continue Import]                              │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ Step 3: Import Complete                                        │
│ ✅ 97 transactions imported                                    │
│ ⚠️  12 new mappings saved to cache (will auto-match next time) │
│ ❌ 3 rows skipped (no property match)                          │
└─────────────────────────────────────────────────────────────────┘
```

---

### Admin UI: Mapping Management

**Endpoint:** `/admin/import-mappings`

```
┌─────────────────────────────────────────────────────────────────┐
│ Import Mapping Cache Management                                │
├─────────────────────────────────────────────────────────────────┤
│ Total Mappings: 248                                             │
│ • 186 Auto-matched (high confidence)                            │
│ • 62 User-confirmed (manual)                                    │
│                                                                  │
│ Recent Activity:                                                │
│ • "Flat 1" → Property #43 (used 15 times, last: 2 days ago)    │
│ • "MS O SMOLIARENKO" → Customer #89 (used 8 times)             │
│                                                                  │
│ [View All] [Validate Mappings] [Export]                        │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ Property Mappings (125 total)                                  │
├──────────────┬──────────────────────┬─────────┬────────────────┤
│ Import Ref   │ Mapped To            │ Used    │ Actions        │
├──────────────┼──────────────────────┼─────────┼────────────────┤
│ "Flat 1"     │ Flat 1 - 3 West Gate │ 15x     │ [Edit][Delete] │
│              │ (ID: 43)             │ 100%    │                │
├──────────────┼──────────────────────┼─────────┼────────────────┤
│ "FLAT 1"     │ Flat 1 - 3 West Gate │ 8x      │ [Merge↑][Edit] │
│ (duplicate)  │ (ID: 43)             │ 100%    │                │
├──────────────┼──────────────────────┼─────────┼────────────────┤
│ "Apartment40"│ Apartment 40 - 31... │ 56x     │ [Edit][Delete] │
│              │ (ID: 78)             │ 95%     │                │
└──────────────┴──────────────────────┴─────────┴────────────────┘
```

**Features:**
1. **View all mappings** - see what's cached
2. **Edit mappings** - correct mistakes
3. **Merge duplicates** - "Flat 1" and "FLAT 1" should map to same property
4. **Validate** - check if target entities still exist
5. **Export/Import** - backup mappings, share between environments

---

## Benefits of Persistent Mapping Cache

### 1. Speed
- First import: Manual review needed
- Second import: 90%+ auto-matched from cache
- Tenth import: Nearly 100% auto-matched

### 2. Consistency
- "Flat 1" ALWAYS maps to Property #43
- No risk of fuzzy match picking different property on different days

### 3. Auditability
- Full history of mappings and revisions
- Can answer: "When did 'Flat 1' start mapping to Property #43?"
- Can answer: "Who changed this mapping?"

### 4. Data Quality
- Manual confirmations improve over time
- Cache acts as "ground truth" for import references
- Easier to spot when PayProp reference changes

### 5. Revisability
- If property renamed: Update mapping, all future imports use new mapping
- If mapping was wrong: Revise it, keep revision history
- If property deleted: Invalidate mapping, prevent stale references

---

## Implementation Roadmap

### Phase 1: Core Cache (1 week)

1. Create tables: `import_mapping_cache`, `import_mapping_revisions`
2. Create `ImportMappingCacheService`
3. Add repository interfaces
4. Unit tests for cache operations

### Phase 2: Integration (1 week)

5. Update `HistoricalTransactionImportService` to use cache
6. Update `LeaseImportService` to use cache
7. Add confidence scoring to fuzzy match logic
8. Return cache hits vs misses in import results

### Phase 3: UI (1 week)

9. Add mapping review step to import wizard
10. Show confidence scores and alternative matches
11. Allow user to confirm/reject/correct mappings
12. Persist user decisions to cache

### Phase 4: Admin Tools (1 week)

13. Create mapping management admin page
14. Add validation endpoint (check for stale mappings)
15. Add merge duplicates feature
16. Add export/import for cache

---

## Example Usage

### First Import (No Cache)

```
User uploads: historical_transactions.csv with "Flat 1" reference
System: No cached mapping found
System: Fuzzy matches → 78% confidence → Flat 1 - 3 West Gate (ID 43)
System: Shows user for confirmation (low confidence)
User: ✓ Confirms
System: Stores mapping ("Flat 1" → Property #43, manual=true, confidence=100)
```

### Second Import (Cache Hit)

```
User uploads: new_transactions.csv with "Flat 1" reference
System: Cache hit! "Flat 1" → Property #43 (100% confidence, manual)
System: Auto-matches without asking user
System: Updates usage count (15 → 16)
Import: Instant match, no user intervention needed
```

### Third Import (Data Changed)

```
User uploads: corrections.csv
User: "Wait, 'Flat 1' should be Property #52, not #43!"
User: Opens mapping management, edits "Flat 1" mapping
System: Creates revision record (old=#43, new=#52, reason="User correction")
System: Updates mapping
Future imports: "Flat 1" → Property #52 automatically
```

---

*Generated: 2025-10-22*
*Focus: What we fixed (0% → 91%) + Persistent mapping cache design*

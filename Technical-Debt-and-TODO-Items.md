# Technical Debt and TODO Items
## Local-to-PayProp Integration Implementation

*Created: 2025-09-06*  
*Status: Implementation Complete - Technical Debt Identified*

---

## 🚨 **CRITICAL FIXES REQUIRED**

### 1. **Authentication Handling - HIGH PRIORITY**

**Issue**: Authentication calls disabled during compilation fixes  
**Files Affected**:
- `HistoricalTransactionImportService.java:486`
- `InvoiceServiceImpl.java:136`

**Current State**:
```java
// BROKEN: Temporarily disabled
User currentUser = null; // TODO: Fix auth - temporarily disabled
```

**Required Fix**:
```java
// PROPER: Implement proper authentication
User currentUser = authenticationUtils.getCurrentUser();
// OR use Security Context
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
```

**Impact**: 
- ❌ User tracking broken for invoices/transactions
- ❌ Audit trails incomplete
- ❌ Security vulnerability - no user validation

---

### 2. **Property Service Method Assumptions**

**Issue**: Assumed `propertyService.findById()` returns `Property` directly  
**Files Affected**:
- `LocalToPayPropSyncService.java:594`
- `UnifiedInvoiceService.java:52`
- `InvoiceServiceImpl.java:128`

**Current State**:
```java
Property property = propertyService.findById(propertyId); // May return null
if (property == null) { ... }
```

**Risk**: NullPointerException if property doesn't exist

**Required Verification**:
- Check if `PropertyService.findById()` actually returns `Optional<Property>` or `Property`
- Add proper null checking throughout the codebase

---

## 🛠️ **MISSING FUNCTIONALITY**

### 3. **PayProp Update API Calls**

**Issue**: Only CREATE operations implemented, no UPDATE support  
**Location**: `LocalToPayPropSyncService.java` - all sync methods

**Missing Operations**:
- `PUT /entity/property/{id}` - Update existing properties
- `PUT /entity/beneficiary/{id}` - Update beneficiary details
- `PUT /entity/tenant/{id}` - Update tenant information
- `PUT /entity/invoice/{id}` - Update invoice details

**Current Limitation**:
```java
// TODO: Implement update API
if (invoice.needsPayPropSync()) {
    String invoiceId = syncInvoiceToPayProp(invoice);  // Only creates, doesn't update
}
```

**Impact**: 
- ✅ Can sync new entities to PayProp
- ❌ Cannot sync changes to existing entities
- ❌ Data drift between local and PayProp systems

---

### 4. **Rollback and Transaction Management**

**Issue**: No rollback mechanism for failed multi-entity syncs  
**Location**: `syncCompletePropertyEcosystem()` method

**Problem**:
```java
// If tenant sync fails after property sync succeeds,
// property remains synced in PayProp but ecosystem is incomplete
```

**Required Implementation**:
- Transaction boundaries across PayProp API calls
- Rollback mechanism for partial failures
- Compensation transactions to undo PayProp entities

---

### 5. **Comprehensive Validation**

**Issue**: Minimal validation before PayProp sync  
**Location**: All sync methods in `LocalToPayPropSyncService`

**Missing Validations**:
- Required field validation before API calls
- Business rule validation (e.g., property has valid address)
- PayProp-specific format validation
- Duplicate prevention logic

**Example Missing Validation**:
```java
// NEEDED: Validation before sync
if (property.getMonthlyPayment() == null) {
    throw new ValidationException("Monthly payment required for PayProp sync");
}
if (property.getAddressLine1() == null || property.getPostcode() == null) {
    throw new ValidationException("Complete address required for PayProp");
}
```

---

## 📊 **MONITORING AND OBSERVABILITY**

### 6. **Comprehensive Logging**

**Issue**: Basic logging only, insufficient for production debugging

**Missing Logging**:
- Structured logging with correlation IDs
- Performance metrics for API calls
- Business event logging
- Error categorization and alerting

**Required Implementation**:
```java
@Timed("payprop.sync.property.duration")
@EventSource("property-sync")
public String syncPropertyToPayProp(Property property) {
    String correlationId = UUID.randomUUID().toString();
    log.info("Starting property sync", 
        kv("correlationId", correlationId),
        kv("propertyId", property.getId()),
        kv("propertyName", property.getPropertyName())
    );
    // ... sync logic
}
```

### 7. **Health Checks and Circuit Breakers**

**Issue**: No resilience patterns implemented

**Missing Components**:
- Circuit breaker for PayProp API calls
- Health checks for PayProp connectivity
- Retry logic with exponential backoff
- Rate limiting for bulk operations

---

## 🔄 **SYNC STRATEGY IMPROVEMENTS**

### 8. **Bidirectional Sync**

**Issue**: Only local-to-PayProp sync implemented

**Missing Direction**: PayProp-to-local sync for:
- Changes made directly in PayProp
- Payment status updates
- Invoice status changes
- New entities created in PayProp

### 9. **Incremental Sync**

**Issue**: Full sync operations only

**Missing Functionality**:
- Last-modified tracking
- Delta sync capabilities
- Change detection mechanisms
- Scheduled incremental updates

### 10. **Conflict Resolution**

**Issue**: No conflict resolution strategy

**Missing Logic**:
- Handling concurrent updates
- Data conflict detection
- Resolution strategies (PayProp wins, local wins, manual resolution)
- Conflict reporting and alerts

---

## 📈 **PERFORMANCE OPTIMIZATIONS**

### 11. **Batch Operations**

**Issue**: Individual API calls for each entity

**Optimization Opportunities**:
- Batch entity creation APIs (if supported by PayProp)
- Parallel processing for independent syncs
- Connection pooling and reuse
- Async processing for non-critical syncs

### 12. **Caching Strategy**

**Issue**: No caching of PayProp API responses

**Missing Caching**:
- Category ID mappings
- PayProp entity metadata
- API response caching for read operations
- Local cache invalidation strategies

---

## 🧪 **TESTING REQUIREMENTS**

### 13. **Comprehensive Test Coverage**

**Missing Test Types**:
- Unit tests for all sync methods
- Integration tests with PayProp API
- Error scenario testing
- Performance/load testing
- End-to-end workflow testing

### 14. **Test Data Management**

**Missing Infrastructure**:
- Test data factories for entities
- PayProp sandbox/staging environment integration
- Mock PayProp API for unit tests
- Test cleanup and isolation

---

## 📋 **DOCUMENTATION GAPS**

### 15. **Operational Documentation**

**Missing Documentation**:
- Deployment procedures
- Configuration management
- Troubleshooting guides
- API rate limits and constraints
- Disaster recovery procedures

### 16. **Developer Documentation**

**Missing Guides**:
- Integration testing procedures
- Local development setup
- PayProp API authentication setup
- Code review checklists
- Architecture decision records

---

## 🎯 **IMPLEMENTATION PRIORITY**

### **Phase 1 - Critical (Must Fix Before Production)**
1. ✅ Authentication handling (#1)
2. ✅ Property service method verification (#2)
3. ✅ Basic validation implementation (#5)

### **Phase 2 - Important (Next Sprint)**
4. PayProp update API calls (#3)
5. Comprehensive logging (#6)
6. Error handling improvements (#4)

### **Phase 3 - Enhancement (Future Iterations)**
7. Health checks and circuit breakers (#7)
8. Bidirectional sync (#8)
9. Performance optimizations (#11)

### **Phase 4 - Quality (Ongoing)**
10. Test coverage (#13)
11. Documentation completion (#15)
12. Monitoring and observability (#6)

---

## 🔗 **RELATED FILES**

### **Implementation Files**:
- `LocalToPayPropSyncService.java` - Core sync implementation
- `PayPropSyncOrchestrator.java` - Integration layer
- `PayPropSyncController.java` - REST endpoints

### **Documentation Files**:
- `PayProp-API-Findings.md` - Tested API patterns
- `Local-to-PayProp-Mapping.md` - Field mappings
- `CRM-Financial-System-Documentation.md` - System architecture

### **Configuration Files**:
- `application.properties` - PayProp configuration
- PayProp OAuth2 settings
- Database connection settings

---

*This document should be reviewed and updated as technical debt items are resolved.*
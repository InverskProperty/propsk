# PayProp Integration System - Complete Architecture Map

This document provides a comprehensive mapping of the entire PayProp integration system in the CRM application, including all APIs, endpoints, services, entities, workflows, and configurations.

## Table of Contents

1. [System Overview](#system-overview)
2. [Authentication & Authorization](#authentication--authorization)
3. [Core Services Architecture](#core-services-architecture)
4. [Entity Mapping](#entity-mapping)
5. [API Endpoints](#api-endpoints)
6. [Database Schema](#database-schema)
7. [Portfolio-Block Tag System](#portfolio-block-tag-system)
8. [Configuration](#configuration)
9. [Workflows](#workflows)
10. [Error Handling](#error-handling)
11. [Performance & Monitoring](#performance--monitoring)
12. [Integration Points](#integration-points)

---

## System Overview

### Architecture Components

The PayProp integration consists of the following major components:

```
┌─────────────────────────────────────────────────────────────┐
│                    PayProp Integration                      │
├─────────────────────────────────────────────────────────────┤
│ Authentication Layer                                        │
│ ├── OAuth2 Service                                         │
│ ├── Token Management                                       │
│ └── API Client                                             │
├─────────────────────────────────────────────────────────────┤
│ Core Services                                               │
│ ├── Sync Service (Main)                                    │
│ ├── Portfolio Sync Service                                 │
│ ├── Financial Sync Service                                 │
│ ├── Maintenance Sync Service                               │
│ ├── Real-time Sync Service                                 │
│ └── Entity Resolution Service                              │
├─────────────────────────────────────────────────────────────┤
│ Data Layer                                                  │
│ ├── Portfolio Assignment Service                           │
│ ├── Tag Migration Service                                  │
│ ├── Change Detection                                       │
│ └── Conflict Resolution                                    │
├─────────────────────────────────────────────────────────────┤
│ Controllers                                                 │
│ ├── Portfolio PayProp Controller                           │
│ ├── PayProp Admin Controller                               │
│ ├── PayProp Sync Controller                                │
│ ├── PayProp Webhook Controller                             │
│ └── PayProp OAuth2 Controller                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Authentication & Authorization

### OAuth2 Implementation

**Service**: `PayPropOAuth2Service`
**Location**: `src/main/java/site/easy/to/build/crm/service/payprop/PayPropOAuth2Service.java`

**Key Methods**:
```java
// Property synchronization
public SyncResult syncProperty(Property property)
public List<Property> getAllPropertiesFromPayProp()
public Property createPropertyInPayProp(Property property)

// Tag management
public PayPropTagDTO createPayPropTag(String tagName)
public PayPropTagDTO ensurePayPropTagExists(String tagName)
public List<PayPropTagDTO> searchPayPropTagsByName(String name)
public void applyTagToProperty(String propertyId, String tagId)
public void removeTagFromProperty(String propertyId, String tagId)

// Sync operations
public SyncResult handlePayPropTagChange(String tagId, String operation, PayPropTagDTO tagData, PayPropTagDTO oldTagData)
```

### 2. Portfolio Sync Service

**Service**: `PayPropPortfolioSyncService`
**Location**: `src/main/java/site/easy/to/build/crm/service/payprop/PayPropPortfolioSyncService.java`

**Primary Responsibilities**:
- Portfolio-to-PayProp tag synchronization
- Block-to-PayProp tag synchronization  
- Tag namespace management (PF- for portfolios, BL- for blocks)
- Property assignment via tags

**Key Methods**:
```java
// Portfolio sync
public SyncResult syncPortfolioToPayProp(Long portfolioId, Long initiatedBy)
public PayPropTagDTO createOrGetPayPropTag(Portfolio portfolio)
public void removePortfolioFromPayProp(Long portfolioId, Long initiatedBy)

// Block sync
public SyncResult syncBlockToPayProp(Long blockId, Long initiatedBy)
public PayPropTagDTO createOrGetPayPropBlockTag(Block block)

// Tag operations
public PayPropTagDTO ensurePayPropTagExists(String tagName)
public List<PayPropTagDTO> searchPayPropTagsByName(String name)
public void applyTagToProperty(String propertyId, String tagId)
public void removeTagFromProperty(String propertyId, String tagId)
```

**Configuration**:
```properties
payprop.api.base-url=https://ukapi.staging.payprop.com/api/agency/v1.1
payprop.enabled=true
```

### 3. Assignment Service Integration

**Service**: `PortfolioAssignmentService`
**Location**: `src/main/java/site/easy/to/build/crm/service/portfolio/PortfolioAssignmentService.java`

**PayProp Integration Points**:
```java
// Sync condition checking
private boolean shouldSyncToPayProp(Portfolio portfolio, Property property)

// Assignment with PayProp sync
public AssignmentResult assignPropertiesToPortfolio(Long portfolioId, List<Long> propertyIds, Long userId)

// Removal with PayProp sync  
public void removePropertyFromPortfolio(Long propertyId, Long portfolioId, Long userId)

// External ID resolution
private String ensurePortfolioHasExternalId(Portfolio portfolio)
```

**Debug Logging**:
The service includes detailed debug logging to identify sync failures:
```java
log.info("🔍 PayProp Sync Check for Portfolio {} → Property {}:", portfolio.getId(), property.getId());
log.info("  Service available: {}", serviceAvailable);
log.info("  Portfolio has tags: {} (value: '{}')", portfolioHasTags, portfolio.getPayPropTags());
log.info("  Portfolio tags not empty: {}", portfolioTagsNotEmpty);
log.info("  Property has PayProp ID: {} (value: '{}')", propertyHasPayPropId, property.getPayPropId());
log.info("  Property PayProp ID not empty: {}", propertyPayPropIdNotEmpty);
log.info("  🎯 RESULT: shouldSync = {}", shouldSync);
```

---

## PayProp API Endpoints (From api_spec.yaml)

### Base Configuration
- **Base URL**: `/api/agency/v1.1`  
- **Authentication**: OAuth2 Bearer tokens
- **Content-Type**: `application/json`

### Tag Management Endpoints

#### 1. Get Tags
```yaml
GET /tags
Parameters:
  - rows: 1-25 (max pagination limit)
  - page: page number (1-indexed)
  - external_id: tag external ID (10-32 alphanumeric chars)
  - name: tag name (1-32 chars, pattern: ^[a-zA-Z0-9_\-\s]+$)
  - entity_type: tenant|property|beneficiary
  - entity_id: entity external ID
Response: TagsResponse with pagination
```

#### 2. Create Tag
```yaml
POST /tags
Request Body:
  name: string (required, 1-32 chars)
Response: Tag object
Behavior: Returns existing tag if name already exists
```

#### 3. Update Tag
```yaml
PUT /tags/{external_id}
Request Body:
  name: string (required)
Response: Tag object
Behavior: Merges with existing tag if name collision occurs
```

#### 4. Delete Tag
```yaml
DELETE /tags/{external_id}
Response: Success message
Effect: Permanently removes tag and ALL entity associations
```

#### 5. Link Tags with Entity
```yaml
POST /tags/entities/{entity_type}/{entity_id}
Parameters:
  - entity_type: tenant|property|beneficiary
  - entity_id: external ID of entity
Request Body:
  tags: array of tag names or external IDs
Response: Array of linked tag objects
```

#### 6. Get Tagged Entities
```yaml
GET /tags/{external_id}/entities
Parameters:
  - entity_type: filter by type (optional)
  - sort_by: type|name
  - sort_direction: asc|desc
Response: TaggedEntitiesResponse
```

#### 7. Delete Tag-Entity Link
```yaml
DELETE /tags/{external_id}/entities
Parameters:
  - entity_type: tenant|property|beneficiary (required)
  - entity_id: entity external ID (required)
Response: Success message
```

### Rate Limiting
- **Global Limit**: 5 requests per second
- **Penalty**: 429 status code + 30-second lockout
- **Scope**: Per API key/token

---

## Entity Mapping

### Portfolio Entity
```java
@Entity
@Table(name = "portfolios")
public class Portfolio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // PayProp Integration Fields
    @Column(name = "payprop_tags", columnDefinition = "TEXT")
    private String payPropTags; // Stores PayProp external ID
    
    @Column(name = "payprop_tag_names", columnDefinition = "TEXT") 
    private String payPropTagNames; // Stores human-readable tag name
    
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status")
    private SyncStatus syncStatus; // pending, synced, failed
    
    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;
    
    // Core Fields
    private String name;
    private String description;
    private PortfolioType portfolioType;
    private String colorCode;
    private Integer propertyOwnerId;
    private String isShared;
}
```

### Property Entity
```java
@Entity
@Table(name = "properties")
public class Property {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // PayProp Integration
    @Column(name = "payprop_id", length = 50)
    private String payPropId; // External ID in PayProp system
    
    @Column(name = "payprop_sync_status")
    private String payPropSyncStatus;
    
    @Column(name = "payprop_last_sync")
    private LocalDateTime payPropLastSync;
    
    // Address and Property Details
    private String firstLine;
    private String secondLine;
    private String city;
    private String state;
    private String postalCode;
    private String country;
}
```

### Block Entity
```java
@Entity
@Table(name = "blocks")
public class Block {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // PayProp Integration Fields
    @Column(name = "payprop_tag_names", columnDefinition = "TEXT")
    private String payPropTagNames;
    
    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;
    
    // Core Fields
    private String name;
    private String description;
}
```

### Junction Table: PropertyPortfolioAssignment
```java
@Entity
@Table(name = "property_portfolio_assignments")
public class PropertyPortfolioAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "property_id")
    private Property property;
    
    @ManyToOne
    @JoinColumn(name = "portfolio_id")
    private Portfolio portfolio;
    
    @Enumerated(EnumType.STRING)
    private PortfolioAssignmentType assignmentType; // PRIMARY, SECONDARY
    
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status")
    private SyncStatus syncStatus; // pending, synced, failed
    
    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;
    
    private Boolean isActive;
    private LocalDateTime assignedAt;
    private Long assignedBy;
}
```

---

## API Controllers

### 1. Portfolio PayProp Controller
**Location**: `src/main/java/site/easy/to/build/crm/controller/portfolio/PortfolioPayPropController.java`

**Endpoints**:
```java
GET /portfolio/payprop-tags - Get available PayProp tags for adoption
POST /portfolio/adopt-payprop-tag - Adopt existing PayProp tag as portfolio
GET /portfolio/actions/pull-payprop-tags - Pull tags from PayProp (two-way sync)
POST /portfolio/sync-all - Sync all portfolios with PayProp
POST /portfolio/{id}/assign-properties-v2 - Assign properties with PayProp sync
```

### 2. PayProp Admin Controller  
**Location**: `src/main/java/site/easy/to/build/crm/controller/PayPropAdminController.java`

**Endpoints**:
```java
GET /admin/payprop/dashboard - PayProp admin dashboard
GET /admin/payprop/sync-logs - View sync logs
POST /admin/payprop/test-connection - Test API connection
GET /admin/payprop/properties - View synced properties
POST /admin/payprop/force-sync - Force full synchronization
```

### 3. PayProp OAuth2 Controller
**Location**: `src/main/java/site/easy/to/build/crm/controller/PayPropOAuth2Controller.java`

**Endpoints**:
```java
GET /payprop/oauth/authorize - Initiate OAuth2 flow
GET /payprop/oauth/callback - Handle OAuth2 callback
POST /payprop/oauth/refresh - Refresh access token
GET /payprop/oauth/status - Check authentication status
```

### 4. PayProp Sync Controller
**Location**: `src/main/java/site/easy/to/build/crm/controller/PayPropSyncController.java`

**Endpoints**:
```java
POST /payprop/sync/properties - Sync properties
POST /payprop/sync/tenants - Sync tenants  
POST /payprop/sync/beneficiaries - Sync beneficiaries
GET /payprop/sync/status - Get sync status
POST /payprop/sync/full - Full system sync
```

### 5. PayProp Webhook Controller
**Location**: `src/main/java/site/easy/to/build/crm/controller/PayPropWebhookController.java`

**Endpoints**:
```java
POST /payprop/webhooks/tag-changes - Handle tag change webhooks
POST /payprop/webhooks/property-updates - Handle property update webhooks
POST /payprop/webhooks/tenant-updates - Handle tenant update webhooks
GET /payprop/webhooks/verify - Webhook verification
```

---

## Tag System Workflow

### Tag Namespace System

The system uses a namespace prefix system to prevent conflicts:

- **Portfolios**: `PF-{portfolio_id}_{clean_name}` (e.g., `PF-1_residential_properties`)
- **Blocks**: `BL-{block_id}_{clean_name}` (e.g., `BL-5_downtown_block`)

**Implementation**:
```java
// In TagNamespaceService
public String createNamespacedTagName(String entityType, Long entityId, String name) {
    String prefix = getNamespacePrefix(entityType);
    String cleanName = cleanTagName(name);
    return prefix + entityId + "_" + cleanName;
}

private String getNamespacePrefix(String entityType) {
    switch (entityType.toLowerCase()) {
        case "portfolio": return "PF-";
        case "block": return "BL-";
        default: throw new IllegalArgumentException("Unknown entity type: " + entityType);
    }
}
```

### Tag Creation Workflow

1. **Portfolio Creation**:
   ```java
   // 1. Generate namespaced tag name
   String namespacedTagName = tagNamespaceService.createNamespacedTagName("portfolio", portfolio.getId(), portfolio.getName());
   
   // 2. Create/get PayProp tag
   PayPropTagDTO payPropTag = payPropSyncService.ensurePayPropTagExists(namespacedTagName);
   
   // 3. Store both external ID and name
   portfolio.setPayPropTags(payPropTag.getId()); // External ID for API calls
   portfolio.setPayPropTagNames(namespacedTagName); // Name for display
   
   // 4. Mark as synced
   portfolio.setSyncStatus(SyncStatus.synced);
   portfolio.setLastSyncAt(LocalDateTime.now());
   ```

2. **Property Assignment**:
   ```java
   // 1. Check sync conditions
   boolean shouldSync = shouldSyncToPayProp(portfolio, property);
   
   // 2. If conditions met, apply tag
   if (shouldSync) {
       payPropSyncService.applyTagToProperty(
           property.getPayPropId(),      // Property external ID
           portfolio.getPayPropTags()    // Tag external ID
       );
   }
   ```

### Tag Assignment Conditions

For a property to be synced with PayProp tags, ALL conditions must be met:

1. **Service Available**: `payPropSyncService != null`
2. **Portfolio Has Tags**: `portfolio.getPayPropTags() != null`
3. **Portfolio Tags Not Empty**: `!portfolio.getPayPropTags().trim().isEmpty()`
4. **Property Has PayProp ID**: `property.getPayPropId() != null`
5. **Property PayProp ID Not Empty**: `!property.getPayPropId().trim().isEmpty()`

**Debug Implementation**:
```java
private boolean shouldSyncToPayProp(Portfolio portfolio, Property property) {
    boolean serviceAvailable = payPropSyncService != null;
    boolean portfolioHasTags = portfolio.getPayPropTags() != null;
    boolean portfolioTagsNotEmpty = portfolioHasTags && !portfolio.getPayPropTags().trim().isEmpty();
    boolean propertyHasPayPropId = property.getPayPropId() != null;
    boolean propertyPayPropIdNotEmpty = propertyHasPayPropId && !property.getPayPropId().trim().isEmpty();
    
    boolean shouldSync = serviceAvailable && portfolioHasTags && portfolioTagsNotEmpty && 
                       propertyHasPayPropId && propertyPayPropIdNotEmpty;
    
    // Detailed logging for debugging
    log.info("🔍 PayProp Sync Check for Portfolio {} → Property {}:", portfolio.getId(), property.getId());
    log.info("  Service available: {}", serviceAvailable);
    log.info("  Portfolio has tags: {} (value: '{}')", portfolioHasTags, portfolio.getPayPropTags());
    log.info("  Portfolio tags not empty: {}", portfolioTagsNotEmpty);
    log.info("  Property has PayProp ID: {} (value: '{}')", propertyHasPayPropId, property.getPayPropId());
    log.info("  Property PayProp ID not empty: {}", propertyPayPropIdNotEmpty);
    log.info("  🎯 RESULT: shouldSync = {}", shouldSync);
    
    return shouldSync;
}
```

---

## Database Schema

### PayProp Integration Tables

#### 1. OAuth Token Storage
```sql
-- PayProp OAuth tokens table
CREATE TABLE payprop_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    access_token TEXT NOT NULL,
    refresh_token TEXT NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### 2. Portfolio Sync Logs
```sql
-- Portfolio synchronization logs
CREATE TABLE portfolio_sync_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    portfolio_id BIGINT,
    sync_type VARCHAR(50),
    sync_action VARCHAR(50),
    initiated_by BIGINT,
    status VARCHAR(20),
    error_message TEXT,
    started_at DATETIME,
    completed_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

#### 3. Property Portfolio Assignments (Junction Table)
```sql
-- Junction table for many-to-many portfolio assignments
CREATE TABLE property_portfolio_assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    property_id BIGINT NOT NULL,
    portfolio_id BIGINT NOT NULL,
    assignment_type ENUM('PRIMARY', 'SECONDARY') NOT NULL DEFAULT 'PRIMARY',
    sync_status ENUM('pending', 'synced', 'failed') NOT NULL DEFAULT 'pending',
    last_sync_at DATETIME NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by BIGINT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    notes TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (property_id) REFERENCES properties(id),
    FOREIGN KEY (portfolio_id) REFERENCES portfolios(id),
    UNIQUE KEY uk_property_portfolio_assignment (property_id, portfolio_id, assignment_type)
);
```

#### 4. PayProp Tag Links
```sql
-- PayProp tag entity linking
CREATE TABLE payprop_tag_links (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tag_id VARCHAR(32) NOT NULL,
    entity_type ENUM('tenant', 'property', 'beneficiary') NOT NULL,
    entity_id VARCHAR(32) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_tag_entity_link (tag_id, entity_type, entity_id)
);
```

#### 5. Customer PayProp Integration (from migration)
```sql
-- Customer table PayProp fields
ALTER TABLE customer 
ADD COLUMN customer_type VARCHAR(20) NOT NULL DEFAULT 'REGULAR_CUSTOMER',
ADD COLUMN payprop_entity_id VARCHAR(32) NULL,
ADD COLUMN payprop_customer_id VARCHAR(50) NULL,
ADD COLUMN payprop_synced BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN payprop_last_sync DATETIME NULL,
ADD COLUMN is_property_owner BOOLEAN DEFAULT FALSE,
ADD COLUMN is_tenant BOOLEAN DEFAULT FALSE,
ADD COLUMN is_contractor BOOLEAN DEFAULT FALSE;

-- Indexes and constraints
CREATE INDEX idx_customer_payprop_entity ON customer(payprop_entity_id);
CREATE INDEX idx_customer_payprop_customer ON customer(payprop_customer_id);
ALTER TABLE customer 
ADD CONSTRAINT uk_customer_payprop_entity UNIQUE (payprop_entity_id),
ADD CONSTRAINT uk_customer_payprop_customer UNIQUE (payprop_customer_id);
```

---

## Configuration

### Environment Variables

#### Production Configuration (application.properties)
```properties
# Core PayProp Settings
payprop.enabled=${PAYPROP_ENABLED:false}
payprop.api.base-url=${PAYPROP_API_BASE_URL:https://ukapi.staging.payprop.com/api/agency/v1.1}
payprop.api.key=dummy_api_key_12345

# OAuth2 Settings
payprop.oauth2.client-id=${PAYPROP_CLIENT_ID:dummy_client_id}
payprop.oauth2.client-secret=${PAYPROP_CLIENT_SECRET:dummy_client_secret}
payprop.oauth2.authorization-url=${PAYPROP_AUTH_URL:https://ukapi.staging.payprop.com/oauth/authorize}
payprop.oauth2.token-url=${PAYPROP_TOKEN_URL:https://ukapi.staging.payprop.com/oauth/access_token}
payprop.oauth2.redirect-uri=${PAYPROP_REDIRECT_URI:https://spoutproperty-hub.onrender.com/api/payprop/oauth/callback}
payprop.oauth2.scopes=${PAYPROP_SCOPES:read write}

# Webhook Configuration
payprop.webhook.url=https://spoutproperty-hub.onrender.com/api/webhooks/payprop
payprop.webhook.secret=${PAYPROP_WEBHOOK_SECRET:dummy_secret_key_12345}
payprop.webhook.enabled=${PAYPROP_WEBHOOK_ENABLED:false}

# Sync Configuration
payprop.sync.batch-size=10
payprop.sync.parallel-enabled=false
payprop.sync.auto-retry=true
payprop.sync.retry-attempts=3
payprop.sync.pull-tags-on-startup=false
payprop.sync.auto-create-portfolios=false

# Conflict Resolution
payprop.conflict.resolution.strategy=FIELD_AUTHORITY
payprop.conflict.payprop-authority-fields=monthly_payment,enable_payments,deposit_amount,listing_from,listing_to
payprop.conflict.crm-authority-fields=customer_type,entity_type,entity_id,portfolio_id,block_id

# Scheduling (disabled in production)
payprop.scheduler.enabled=false
payprop.scheduler.full-sync-cron=0 0 2 * * ?
payprop.scheduler.intelligent-sync-cron=0 */30 * * * ?

# File Sync
payprop.file-sync.enabled=${PAYPROP_FILE_SYNC_ENABLED:true}
payprop.file-sync.batch-size=25
payprop.file-sync.auto-organize=true
payprop.file-sync.duplicate-check=true
payprop.file-sync.supported-types=pdf,doc,docx,xls,xlsx,png,jpg,jpeg,gif,txt,csv

# Logging
logging.level.site.easy.to.build.crm.service.payprop=${PAYPROP_LOG_LEVEL:WARN}
```

#### Local Development Configuration (application-local.properties)
```properties
# PAYPROP CONFIGURATION (Staging)
payprop.enabled=true
payprop.api.base-url=https://ukapi.staging.payprop.com/api/agency/v1.1
payprop.api.key=dummy_api_key_12345

# PayProp OAuth2
payprop.oauth2.client-id=Propsk
payprop.oauth2.client-secret=L7GJfqHWduV9IdU7
payprop.oauth2.authorization-url=https://ukapi.staging.payprop.com/oauth/authorize
payprop.oauth2.token-url=https://ukapi.staging.payprop.com/oauth/access_token
payprop.oauth2.redirect-uri=http://localhost:8080/api/payprop/oauth/callback

# Local webhook settings
payprop.webhook.url=http://localhost:8080/api/webhooks/payprop
payprop.webhook.secret=your_webhook_secret_here_12345
payprop.webhook.enabled=true

# Scheduling disabled for local
payprop.scheduler.enabled=false

# Debug logging for local development
logging.level.site.easy.to.build.crm.service.payprop=DEBUG
```

### Conditional Bean Configuration

**PayProp services are conditionally loaded**:
```java
@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropPortfolioSyncService {
    // Service implementation
}
```

---

## PayProp API Client Architecture

### Core API Client
**Service**: `PayPropApiClient`
**Location**: `src/main/java/site/easy/to/build/crm/service/payprop/PayPropApiClient.java`

**Key Features**:
```java
// Automatic pagination handling
public <T> List<T> fetchAllPages(String endpoint, Function<Map<String, Object>, T> mapper)

// Single page fetching with error handling
public PayPropPageResult fetchSinglePage(String endpoint, int page, int pageSize)

// Generic HTTP operations with authentication
public <T> T get(String endpoint, Class<T> responseType)
public <T> T post(String endpoint, Object requestBody, Class<T> responseType)
public void delete(String endpoint)

// Rate limiting (250ms between calls)
private static final int RATE_LIMIT_DELAY_MS = 250;
```

**Configuration Constants**:
```java
private static final int MAX_PAGES = 100;
private static final int DEFAULT_PAGE_SIZE = 25;
private static final int RATE_LIMIT_DELAY_MS = 250;
```

---

## Error Handling & Monitoring

### Error Handling Patterns

#### 1. Service-Level Exception Handling
```java
// In PayPropPortfolioSyncService
try {
    PayPropTagDTO tag = createOrGetPayPropTag(portfolio);
    portfolio.setPayPropTags(tag.getId());
    portfolio.setSyncStatus(SyncStatus.synced);
} catch (Exception e) {
    log.error("Failed to sync portfolio {}: {}", portfolio.getId(), e.getMessage());
    portfolio.setSyncStatus(SyncStatus.failed);
    updateSyncLog(syncLog, "FAILED", e.getMessage());
    return SyncResult.failure(e.getMessage());
}
```

#### 2. HTTP Client Exception Handling
```java
// In PayPropApiClient
try {
    ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
    return response.getBody();
} catch (HttpClientErrorException e) {
    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
        log.warn("PayProp resource not found: {}", endpoint);
        return null;
    }
    throw new PayPropSyncException("API call failed: " + e.getMessage(), e);
}
```

#### 3. Rate Limiting Handling
```java
// Automatic rate limiting in API client
if (page > 1) {
    Thread.sleep(RATE_LIMIT_DELAY_MS);
}
```

### Sync Status Tracking

#### SyncStatus Enumeration
```java
public enum SyncStatus {
    pending,    // Waiting for sync
    synced,     // Successfully synced
    failed      // Sync failed
}
```

#### Sync Logging
```java
// PortfolioSyncLog entity tracks all sync operations
@Entity
public class PortfolioSyncLog {
    private Long portfolioId;
    private String syncType;      // "PORTFOLIO_TO_PAYPROP", "PAYPROP_TO_PORTFOLIO"
    private String syncAction;    // "CREATE", "UPDATE", "DELETE"
    private Long initiatedBy;
    private String status;        // "PENDING", "SUCCESS", "FAILED"
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
```

### Debug Logging System

#### Detailed Sync Condition Logging
The system includes comprehensive debug logging to identify why syncs fail:

```java
log.info("🔍 PayProp Sync Check for Portfolio {} → Property {}:", portfolio.getId(), property.getId());
log.info("  Service available: {}", serviceAvailable);
log.info("  Portfolio has tags: {} (value: '{}')", portfolioHasTags, portfolio.getPayPropTags());
log.info("  Portfolio tags not empty: {}", portfolioTagsNotEmpty);
log.info("  Property has PayProp ID: {} (value: '{}')", propertyHasPayPropId, property.getPayPropId());
log.info("  Property PayProp ID not empty: {}", propertyPayPropIdNotEmpty);
log.info("  🎯 RESULT: shouldSync = {}", shouldSync);
```

#### API Call Logging
```java
log.info("🔄 Starting paginated fetch from endpoint: {}", endpoint);
log.debug("✅ Found 'items' field with {} items", items.size());
log.warn("❌ PayProp sync SKIPPED due to failed conditions above");
```

---

## Critical Integration Points

### 1. Portfolio Creation → PayProp Tag Creation
**File**: `PortfolioServiceImpl.java:lines 123-150`
```java
// Manual portfolio creation workflow
if (enablePayPropSync && payPropSyncService != null) {
    String namespacedTagName = tagNamespaceService.createNamespacedTagName("portfolio", savedPortfolio.getId(), savedPortfolio.getName());
    PayPropTagDTO payPropTag = payPropSyncService.ensurePayPropTagExists(namespacedTagName);
    savedPortfolio.setPayPropTags(payPropTag.getId());
    savedPortfolio.setPayPropTagNames(namespacedTagName);
    savedPortfolio.setSyncStatus(SyncStatus.synced);
}
```

### 2. Property Assignment → PayProp Tag Application
**File**: `PortfolioAssignmentService.java:lines 105-150`
```java
// Property assignment with PayProp sync
if (shouldSyncToPayProp(portfolio, property)) {
    payPropSyncService.applyTagToProperty(
        property.getPayPropId(),      // Property external ID
        portfolio.getPayPropTags()    // Tag external ID  
    );
    assignment.setSyncStatus(SyncStatus.synced);
}
```

### 3. V2 Assignment Endpoint → Assignment Service
**File**: `PortfolioController.java:lines 2793-2798`
```java
// V2 controller delegates to assignment service for proper PayProp handling
PortfolioAssignmentService.AssignmentResult result = 
    portfolioAssignmentService.assignPropertiesToPortfolio(portfolioId, propertyIds, userId);
```

### 4. OAuth2 Token Management
**File**: `PayPropOAuth2Service.java`
```java
// Token lifecycle management
public PayPropTokens exchangeCodeForTokens(String code)
public PayPropTokens refreshTokens(String refreshToken)
public PayPropToken getValidToken() // Auto-refreshes if needed
```

---

## Current Integration Status

### Working Components
✅ **OAuth2 Authentication**: Full implementation with token refresh  
✅ **Portfolio Tag Creation**: Namespace system (PF- prefix)  
✅ **Tag API Integration**: Create, update, delete, link operations  
✅ **Assignment Service**: Junction table with PayProp sync  
✅ **Debug Logging**: Comprehensive condition checking  
✅ **Configuration Management**: Environment-based settings  
✅ **Error Handling**: Service-level exception management  

### Known Issues
❌ **Assignment Sync**: `shouldSyncToPayProp()` conditions failing  
❌ **Property PayProp IDs**: Properties may lack PayProp external IDs  
❌ **V2 Endpoint**: Recently fixed to use proper assignment service  

### Investigation Required
🔍 **Check Property Table**: Verify `payprop_id` field population  
🔍 **Validate Portfolio Tags**: Ensure new portfolios get external IDs  
🔍 **Test Assignment Flow**: Confirm V2 endpoint calls assignment service  
🔍 **Monitor Debug Logs**: Track which sync conditions fail  

---

## Next Steps for Debugging

1. **Check Property PayProp IDs**:
   ```sql
   SELECT id, first_line, payprop_id FROM properties WHERE payprop_id IS NOT NULL LIMIT 10;
   ```

2. **Verify Portfolio Tag Creation**:
   ```sql
   SELECT id, name, payprop_tags, payprop_tag_names, sync_status FROM portfolios ORDER BY id DESC LIMIT 5;
   ```

3. **Monitor Assignment Logs**:
   ```
   grep "PayProp Sync Check" application.logs
   ```

4. **Test OAuth2 Token Status**:
   ```
   GET /payprop/oauth/status
   ```

---

## Assignment System Analysis

### Current Hybrid Assignment Model

The system implements **two parallel assignment mechanisms**:

#### 1. Direct FK Assignment (Legacy)
**Location**: `Property.java` + `PortfolioServiceImpl.java`
```java
// Property entity has direct portfolio FK
@ManyToOne
@JoinColumn(name = "portfolio_id")
private Portfolio portfolio;

// Legacy assignment in PortfolioServiceImpl
property.setPortfolio(portfolio);  // Direct FK assignment
property.setPortfolioAssignmentDate(LocalDateTime.now());
```

**PayProp Integration**: ❌ Limited/outdated
**Usage**: Legacy endpoints

#### 2. Junction Table Assignment (Modern)
**Location**: `PropertyPortfolioAssignment.java` + `PortfolioAssignmentService.java`
```java
// Junction table entity
@Entity
@Table(name = "property_portfolio_assignments")
public class PropertyPortfolioAssignment {
    @ManyToOne private Property property;
    @ManyToOne private Portfolio portfolio;
    @Enumerated(EnumType.STRING) private SyncStatus syncStatus;
    private Boolean isActive;
}

// Modern assignment service
PropertyPortfolioAssignment assignment = new PropertyPortfolioAssignment();
assignment.setProperty(property);
assignment.setPortfolio(portfolio);
assignment.setSyncStatus(SyncStatus.pending);
```

**PayProp Integration**: ✅ Full integration with detailed debugging
**Usage**: V2 endpoints, modern flows

### Critical Finding: Property.setPortfolio() Still Works

**File**: `Property.java:lines 450-454`
```java
@Deprecated
public void setPortfolio(Portfolio portfolio) { 
    // Comment claims: "No-op since direct assignment is disabled"
    // Reality: THIS STILL SETS THE VALUE!
    this.portfolio = portfolio;  // ⚠️ STILL FUNCTIONAL
}
```

### Assignment Service Comparison

| Service | Assignment Type | PayProp Sync | FK Cleanup | Debug Logging |
|---------|----------------|--------------|------------|---------------|
| `PortfolioAssignmentService` | Junction Table Only | ✅ Full | ✅ Removes conflicts | ✅ Detailed |
| `PortfolioServiceImpl` | **Both** (Hybrid) | ❌ Limited | ❌ Creates FKs | ❌ Basic |

### Endpoint Assignment Flow Mapping

#### Modern Endpoints (✅ Junction Table)
```java
// V2 Assignment (Fixed recently)
POST /{id}/assign-properties-v2
→ PortfolioController.assignPropertiesToPortfolioV2()
→ portfolioAssignmentService.assignPropertiesToPortfolio()
→ Junction table + PayProp sync with debug logging
```

#### Legacy Endpoints (⚠️ Direct FK Risk)
```java
// Legacy assignments (if still active)
POST /portfolio/{portfolioId}/assign/{propertyId}
POST /portfolio/{portfolioId}/properties/assign
→ PortfolioServiceImpl.assignPropertyToPortfolio()
→ BOTH junction table AND direct FK assignment
→ Limited PayProp integration
```

### Database State Analysis

Properties can exist in multiple states:

1. **Junction Table Only** (Modern):
   ```sql
   properties.portfolio_id = NULL
   property_portfolio_assignments: active record exists
   PayProp Sync: ✅ Works
   ```

2. **Direct FK Only** (Legacy):
   ```sql
   properties.portfolio_id = portfolio_id
   property_portfolio_assignments: no record
   PayProp Sync: ❌ Fails (no sync status tracking)
   ```

3. **Both** (Hybrid/Conflict):
   ```sql
   properties.portfolio_id = portfolio_id
   property_portfolio_assignments: active record exists
   PayProp Sync: ⚠️ Depends on which service processes
   ```

---

## PayProp Sync Failure Root Cause Analysis

### Issue 1: Portfolio Missing External IDs
**Evidence**: `payprop_tags = NULL` but `payprop_tag_names` populated
**Cause**: PayProp tag creation partially failed during portfolio creation
**Impact**: Sync condition #2 and #3 fail in `shouldSyncToPayProp()`

### Issue 2: Property Assignment Method Matters
**Evidence**: Assignment works but PayProp sync doesn't occur
**Cause**: Properties assigned via legacy direct FK system don't have junction table records
**Impact**: `PortfolioAssignmentService.shouldSyncToPayProp()` never called

### Issue 3: Hybrid System Confusion
**Evidence**: Properties can be assigned through multiple pathways
**Cause**: Both assignment systems coexist and create different database states
**Impact**: PayProp sync success depends on which assignment path was used

---

## Migration and Cleanup System

### Automatic FK Cleanup
**File**: `PortfolioAssignmentService.java:lines 152-158`
```java
// Junction table assignment service actively removes direct FKs
if (property.getPortfolio() != null && property.getPortfolio().getId().equals(portfolioId)) {
    log.info("🔧 Clearing direct FK for property {} to prevent conflicts", propertyId);
    property.setPortfolio(null);
    property.setPortfolioAssignmentDate(null);
    propertyService.save(property);
}
```

### Migration Service
**File**: `PortfolioAssignmentService.java:lines 245-301`
```java
// Migrates legacy direct FK assignments to junction table
public MigrationResult migrateDirectFKToJunctionTable() {
    List<Property> propertiesWithFK = propertyService.findAll().stream()
        .filter(p -> p.getPortfolio() != null)
        .collect(Collectors.toList());
    
    for (Property property : propertiesWithFK) {
        // Create junction table entry
        PropertyPortfolioAssignment assignment = new PropertyPortfolioAssignment();
        assignment.setProperty(property);
        assignment.setPortfolio(property.getPortfolio());
        assignment.setSyncStatus(SyncStatus.pending);
        
        // Clear direct FK
        property.setPortfolio(null);
        propertyService.save(property);
    }
}
```

---

## Service Dependencies and Integration

### Circular Dependency Resolution
**File**: `PortfolioAssignmentService.java:lines 46-49`
```java
@Autowired(required = false)
@Lazy // Break circular dependency with PayPropPortfolioSyncService
private PortfolioAssignmentService portfolioAssignmentService;
```

### PayProp Service Integration Points
```java
// Tag creation during portfolio creation
PayPropTagDTO payPropTag = payPropSyncService.ensurePayPropTagExists(namespacedTagName);
portfolio.setPayPropTags(payPropTag.getId());

// Tag application during property assignment  
payPropSyncService.applyTagToProperty(property.getPayPropId(), portfolio.getPayPropTags());

// Tag removal during property unassignment
payPropSyncService.removeTagFromProperty(property.getPayPropId(), portfolio.getPayPropTags());
```

---

## Complete Debugging Workflow

### Step 1: Check Portfolio PayProp Tags
```sql
SELECT id, name, payprop_tags, payprop_tag_names, sync_status 
FROM portfolios ORDER BY id DESC LIMIT 5;
```

### Step 2: Check Property PayProp IDs
```sql
SELECT id, first_line, payprop_id FROM properties 
WHERE payprop_id IS NOT NULL LIMIT 10;
```

### Step 3: Check Assignment Method Used
```sql
-- Direct FK assignments
SELECT id, first_line, portfolio_id FROM properties WHERE portfolio_id IS NOT NULL;

-- Junction table assignments
SELECT p.id, p.first_line, ppa.portfolio_id, ppa.sync_status 
FROM properties p 
INNER JOIN property_portfolio_assignments ppa ON p.id = ppa.property_id 
WHERE ppa.is_active = TRUE;
```

### Step 4: Monitor PayProp Sync Debug Logs
```
grep "PayProp Sync Check" application.logs
```

### Step 5: Test OAuth2 Token Status
```
GET /payprop/oauth/status
```

---

## Resolution Strategy

### Immediate Fix (Current Issue)
1. **Check your test property (ID 14) assignment method**:
   ```sql
   SELECT portfolio_id FROM properties WHERE id = 14;
   SELECT * FROM property_portfolio_assignments WHERE property_id = 14 AND is_active = TRUE;
   ```

2. **If direct FK only**: Run migration to create junction table record
3. **If missing PayProp external IDs**: Fix portfolio tag creation
4. **If hybrid state**: Clean up direct FK

### Long-term System Health
1. **Audit all assignment endpoints** - ensure they use `PortfolioAssignmentService`
2. **Disable direct FK assignments** - make `Property.setPortfolio()` a true no-op
3. **Run complete migration** - eliminate all direct FK assignments
4. **Standardize PayProp sync** - ensure all assignment flows use detailed debugging

---

---

## PayProp API Call Flow Mapping

This section maps **every PayProp API call** throughout the system, showing when, where, and how PayProp is called for tag creation, assignment, and deletion across all routes.

### Authentication Flow

#### OAuth2 Token Management
**File**: `PayPropOAuth2Service.java`

**Initial Authentication**:
```java
// 1. Build authorization URL
public String buildAuthorizationUrl() {
    return payPropAuthorizationUrl + 
           "?client_id=" + payPropClientId +
           "&redirect_uri=" + payPropRedirectUri +
           "&response_type=code" +
           "&scope=" + payPropScopes;
}

// 2. Exchange code for tokens
public PayPropTokens exchangeCodeForTokens(String code) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    
    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("grant_type", "authorization_code");
    params.add("client_id", payPropClientId);
    params.add("client_secret", payPropClientSecret);
    params.add("code", code);
    params.add("redirect_uri", payPropRedirectUri);
    
    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
    
    ResponseEntity<Map> response = restTemplate.postForEntity(
        payPropTokenUrl, request, Map.class);
    
    return new PayPropTokens(
        (String) response.getBody().get("access_token"),
        (String) response.getBody().get("refresh_token"),
        (Integer) response.getBody().get("expires_in")
    );
}
```

**Token Refresh**:
```java
public PayPropTokens refreshTokens(String refreshToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    
    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("grant_type", "refresh_token");
    params.add("client_id", payPropClientId);
    params.add("client_secret", payPropClientSecret);
    params.add("refresh_token", refreshToken);
    
    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
    
    ResponseEntity<Map> response = restTemplate.postForEntity(
        payPropTokenUrl, request, Map.class);
        
    return new PayPropTokens(/* new tokens */);
}

// 3. Get valid token (auto-refresh if needed)
public PayPropToken getValidToken() throws Exception {
    PayPropToken token = tokenRepository.findFirstByOrderByCreatedAtDesc();
    
    if (token == null) {
        throw new Exception("No PayProp token found. Please authenticate first.");
    }
    
    if (isTokenExpired(token)) {
        log.info("🔄 PayProp token expired, refreshing...");
        PayPropTokens newTokens = refreshTokens(token.getRefreshToken());
        token = saveTokens(newTokens);
        log.info("✅ PayProp token refreshed successfully");
    }
    
    return token;
}
```

**API Request Authentication**:
**File**: `PayPropApiClient.java`
```java
private HttpHeaders createAuthenticatedHeaders() throws Exception {
    PayPropToken token = oAuth2Service.getValidToken();
    
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(token.getAccessToken());  // OAuth2 Bearer token
    headers.set("User-Agent", "CRM-PayProp-Integration/1.0");
    
    return headers;
}

// Generic authenticated request method
private <T> T makeAuthenticatedRequest(String endpoint, HttpMethod method, Object requestBody, Class<T> responseType) throws Exception {
    HttpHeaders headers = createAuthenticatedHeaders();
    HttpEntity<Object> entity = new HttpEntity<>(requestBody, headers);
    
    String url = payPropApiBase + endpoint;
    log.debug("🔗 PayProp API call: {} {}", method, url);
    
    try {
        ResponseEntity<T> response = restTemplate.exchange(url, method, entity, responseType);
        log.debug("✅ PayProp API response: {} - {}", response.getStatusCode(), response.getBody());
        return response.getBody();
    } catch (HttpClientErrorException e) {
        log.error("❌ PayProp API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
        throw new PayPropSyncException("PayProp API call failed: " + e.getMessage(), e);
    }
}
```

---

### Tag Creation Flow

#### 1. Portfolio Creation → PayProp Tag Creation

**Entry Point**: `PortfolioServiceImpl.java` - Manual Portfolio Creation
**Route**: `POST /portfolio/create`

```java
// File: PortfolioServiceImpl.java:lines 123-150
if (enablePayPropSync && payPropSyncService != null) {
    log.info("🔄 Attempting PayProp sync for portfolio: {}", savedPortfolio.getName());
    
    try {
        // Step 1: Generate namespaced tag name
        String namespacedTagName = tagNamespaceService.createNamespacedTagName(
            "portfolio", savedPortfolio.getId(), savedPortfolio.getName());
        log.info("📝 Generated tag name: {}", namespacedTagName);
        
        // Step 2: Create/get PayProp tag - CALLS PAYPROP API
        PayPropTagDTO payPropTag = payPropSyncService.ensurePayPropTagExists(namespacedTagName);
        
        // Step 3: Store PayProp external ID and name
        savedPortfolio.setPayPropTags(payPropTag.getId());        // External ID
        savedPortfolio.setPayPropTagNames(namespacedTagName);     // Display name
        savedPortfolio.setSyncStatus(SyncStatus.synced);
        savedPortfolio.setLastSyncAt(LocalDateTime.now());
        
        portfolioRepository.save(savedPortfolio);
        log.info("✅ PayProp sync successful for portfolio: {}", savedPortfolio.getName());
        
    } catch (Exception e) {
        log.error("❌ PayProp sync failed for portfolio {}: {}", savedPortfolio.getName(), e.getMessage());
        savedPortfolio.setSyncStatus(SyncStatus.failed);
        portfolioRepository.save(savedPortfolio);
    }
}
```

**PayProp API Calls in Tag Creation**:
**File**: `PayPropPortfolioSyncService.java`

```java
// Main tag creation method - makes multiple PayProp API calls
public PayPropTagDTO ensurePayPropTagExists(String tagName) throws Exception {
    log.info("🏷️ Ensuring PayProp tag exists: {}", tagName);
    
    // PAYPROP API CALL #1: Search for existing tag by name
    try {
        List<PayPropTagDTO> existingTags = searchPayPropTagsByName(tagName);
        if (!existingTags.isEmpty()) {
            PayPropTagDTO existingTag = existingTags.get(0);
            log.info("✅ Found existing PayProp tag: {} -> {}", tagName, existingTag.getId());
            return existingTag;
        }
    } catch (Exception e) {
        log.warn("⚠️ Failed to search for existing tag {}: {}", tagName, e.getMessage());
    }
    
    // PAYPROP API CALL #2: Create new tag
    log.info("🆕 Creating new PayProp tag: {}", tagName);
    PayPropTagDTO newTag = new PayPropTagDTO();
    newTag.setName(tagName);
    newTag.setDescription("Created by CRM for portfolio organization");
    
    try {
        PayPropTagDTO createdTag = createPayPropTag(newTag);
        log.info("✅ Successfully created PayProp tag: {} -> {}", tagName, createdTag.getId());
        return createdTag;
    } catch (Exception e) {
        // Handle duplicate case - search again
        if (e.getMessage().contains("already exists") || e.getMessage().contains("duplicate")) {
            log.info("ℹ️ Tag already exists in PayProp, searching again: {}", tagName);
            try {
                // PAYPROP API CALL #3: Search again for duplicates
                List<PayPropTagDTO> duplicateTags = searchPayPropTagsByName(tagName);
                if (!duplicateTags.isEmpty()) {
                    return duplicateTags.get(0);
                }
            } catch (Exception searchEx) {
                log.error("❌ Failed to find duplicate tag {}: {}", tagName, searchEx.getMessage());
            }
        }
        throw new RuntimeException("Failed to create PayProp tag: " + e.getMessage(), e);
    }
}

// PAYPROP API CALL: Search tags by name
public List<PayPropTagDTO> searchPayPropTagsByName(String name) throws Exception {
    try {
        Map<String, String> params = new HashMap<>();
        params.put("name", name);
        params.put("rows", "25");
        
        // API Call: GET /tags?name={name}&rows=25
        PayPropApiClient.PayPropPageResult result = payPropApiClient.fetchWithParams("/tags", params);
        
        List<PayPropTagDTO> tags = new ArrayList<>();
        for (Map<String, Object> tagMap : result.getItems()) {
            tags.add(convertMapToTagDTO(tagMap));
        }
        
        log.debug("🔍 Found {} tags matching name: {}", tags.size(), name);
        return tags;
        
    } catch (Exception e) {
        log.error("❌ Error searching PayProp tags by name {}: {}", name, e.getMessage());
        throw new RuntimeException("Failed to search PayProp tags: " + e.getMessage(), e);
    }
}

// PAYPROP API CALL: Create new tag
public PayPropTagDTO createPayPropTag(PayPropTagDTO tag) throws Exception {
    try {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", tag.getName());
        if (tag.getDescription() != null && !tag.getDescription().isEmpty()) {
            requestBody.put("description", tag.getDescription());
        }
        
        log.info("🔄 Creating PayProp tag: {}", tag.getName());
        
        // API Call: POST /tags
        Map<String, Object> response = payPropApiClient.post("/tags", requestBody, Map.class);
        
        PayPropTagDTO createdTag = convertMapToTagDTO(response);
        log.info("✅ PayProp tag created successfully: {} -> {}", createdTag.getName(), createdTag.getId());
        
        return createdTag;
        
    } catch (Exception e) {
        log.error("❌ Failed to create PayProp tag {}: {}", tag.getName(), e.getMessage());
        throw new RuntimeException("PayProp tag creation failed: " + e.getMessage(), e);
    }
}
```

#### 2. Block Creation → PayProp Tag Creation

**Entry Point**: `BlockTagService.java`
**Route**: Block creation endpoints

```java
// File: BlockTagService.java - Block tag creation
public PayPropTagDTO createOrGetPayPropBlockTag(Block block) throws Exception {
    // Generate namespaced block tag name
    String namespacedTagName = tagNamespaceService.createNamespacedTagName(
        "block", block.getId(), block.getName());
    
    log.info("🏷️ Creating PayProp tag for block: {}", namespacedTagName);
    
    // CALLS SAME TAG CREATION FLOW AS PORTFOLIOS
    return payPropPortfolioSyncService.ensurePayPropTagExists(namespacedTagName);
}
```

---

### Property Assignment Flow

#### 1. V2 Assignment Endpoint → PayProp Tag Application

**Entry Point**: `PortfolioController.java` - V2 Assignment
**Route**: `POST /{id}/assign-properties-v2`

```java
// File: PortfolioController.java:lines 2793-2798
// V2 controller delegates to assignment service for proper PayProp handling
PortfolioAssignmentService.AssignmentResult result = 
    portfolioAssignmentService.assignPropertiesToPortfolio(portfolioId, propertyIds, userId);
```

**PayProp API Calls in Assignment**:
**File**: `PortfolioAssignmentService.java`

```java
// Main assignment method with PayProp integration
public AssignmentResult assignPropertiesToPortfolio(Long portfolioId, List<Long> propertyIds, Long userId) {
    log.info("🎯 Starting assignment of {} properties to portfolio {}", propertyIds.size(), portfolioId);
    
    Portfolio portfolio = portfolioRepository.findById(portfolioId)
        .orElseThrow(() -> new IllegalArgumentException("Portfolio not found: " + portfolioId));
    
    for (Long propertyId : propertyIds) {
        Property property = propertyService.findById(propertyId);
        
        // Create junction table assignment
        PropertyPortfolioAssignment assignment = createJunctionTableAssignment(property, portfolio, userId);
        
        // PAYPROP SYNC POINT: Apply PayProp tag if conditions met
        if (shouldSyncToPayProp(portfolio, property)) {
            try {
                String tagValue = portfolio.getPayPropTags();
                
                // Handle legacy tag name vs external ID resolution
                if (tagValue.startsWith("PF-") || tagValue.startsWith("BL-")) {
                    log.warn("Portfolio {} has tag name instead of external ID, resolving: {}", portfolio.getId(), tagValue);
                    tagValue = ensurePortfolioHasExternalId(portfolio);
                    if (tagValue == null) {
                        log.error("❌ Cannot resolve PayProp external ID for portfolio {}", portfolio.getId());
                        assignment.setSyncStatus(SyncStatus.failed);
                        continue;
                    }
                }
                
                log.info("🔄 Applying PayProp tag {} to property {}", tagValue, property.getPayPropId());
                
                // PAYPROP API CALL: Apply tag to property
                payPropSyncService.applyTagToProperty(property.getPayPropId(), tagValue);
                
                // Update sync status
                assignment.setSyncStatus(SyncStatus.synced);
                assignment.setLastSyncAt(LocalDateTime.now());
                result.incrementSynced();
                
                log.info("✅ PayProp sync successful for property {}", property.getPayPropId());
                
            } catch (Exception e) {
                log.error("❌ PayProp sync failed for property {}: {}", property.getPayPropId(), e.getMessage());
                assignment.setSyncStatus(SyncStatus.failed);
                assignment.setLastSyncAt(LocalDateTime.now());
                result.addError("PayProp sync failed for property " + propertyId + ": " + e.getMessage());
            }
        } else {
            log.info("⏭️ Skipping PayProp sync - conditions not met for portfolio {} or property {}", 
                portfolioId, propertyId);
        }
        
        assignmentRepository.save(assignment);
    }
    
    return result;
}

// PayProp sync condition checking with detailed debugging
private boolean shouldSyncToPayProp(Portfolio portfolio, Property property) {
    boolean serviceAvailable = payPropSyncService != null;
    boolean portfolioHasTags = portfolio.getPayPropTags() != null;
    boolean portfolioTagsNotEmpty = portfolioHasTags && !portfolio.getPayPropTags().trim().isEmpty();
    boolean propertyHasPayPropId = property.getPayPropId() != null;
    boolean propertyPayPropIdNotEmpty = propertyHasPayPropId && !property.getPayPropId().trim().isEmpty();
    
    boolean shouldSync = serviceAvailable && portfolioHasTags && portfolioTagsNotEmpty && 
                       propertyHasPayPropId && propertyPayPropIdNotEmpty;
    
    // DETAILED DEBUG LOGGING
    log.info("🔍 PayProp Sync Check for Portfolio {} → Property {}:", portfolio.getId(), property.getId());
    log.info("  Service available: {}", serviceAvailable);
    log.info("  Portfolio has tags: {} (value: '{}')", portfolioHasTags, portfolio.getPayPropTags());
    log.info("  Portfolio tags not empty: {}", portfolioTagsNotEmpty);
    log.info("  Property has PayProp ID: {} (value: '{}')", propertyHasPayPropId, property.getPayPropId());
    log.info("  Property PayProp ID not empty: {}", propertyPayPropIdNotEmpty);
    log.info("  🎯 RESULT: shouldSync = {}", shouldSync);
    
    if (!shouldSync) {
        log.warn("❌ PayProp sync SKIPPED due to failed conditions above");
    }
    
    return shouldSync;
}
```

**PayProp Tag Application API Call**:
**File**: `PayPropPortfolioSyncService.java`

```java
// PAYPROP API CALL: Apply tag to property
public void applyTagToProperty(String propertyId, String tagId) throws Exception {
    try {
        log.info("🏷️ Applying PayProp tag {} to property {}", tagId, propertyId);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("tags", Arrays.asList(tagId));  // Tag external ID array
        
        String endpoint = String.format("/tags/entities/property/%s", propertyId);
        
        // API Call: POST /tags/entities/property/{propertyId}
        Map<String, Object> response = payPropApiClient.post(endpoint, requestBody, Map.class);
        
        log.info("✅ Successfully applied tag {} to property {}", tagId, propertyId);
        
    } catch (Exception e) {
        log.error("❌ Failed to apply tag {} to property {}: {}", tagId, propertyId, e.getMessage());
        throw new RuntimeException("PayProp tag application failed: " + e.getMessage(), e);
    }
}
```

#### 2. Legacy Assignment Flow (If Still Active)

**Entry Point**: `PortfolioServiceImpl.java`
**Route**: Legacy assignment endpoints

```java
// File: PortfolioServiceImpl.java - Legacy assignment with limited PayProp integration
public void assignPropertyToPortfolio(Long propertyId, Long portfolioId, Long assignedBy) {
    // Creates both junction table AND direct FK assignment
    PropertyPortfolioAssignment assignment = new PropertyPortfolioAssignment();
    assignment.setProperty(property);
    assignment.setPortfolio(portfolio);
    assignment.setSyncStatus(SyncStatus.pending);
    
    // ALSO sets direct FK for "backwards compatibility"
    if (assignmentType == PortfolioAssignmentType.PRIMARY) {
        property.setPortfolio(portfolio);  // Direct FK assignment
        property.setPortfolioAssignmentDate(LocalDateTime.now());
        propertyService.save(property);
    }
    
    // LIMITED PAYPROP INTEGRATION
    if (payPropEnabled && hasActivePayPropConnection()) {
        // Basic PayProp sync without detailed condition checking
        try {
            // May call PayProp but without detailed debugging
        } catch (Exception e) {
            log.warn("PayProp sync failed: {}", e.getMessage());
        }
    }
}
```

---

### Property Unassignment Flow

#### 1. Remove Property from Portfolio → PayProp Tag Removal

**Entry Point**: `PortfolioAssignmentService.java`
**Route**: Property removal endpoints

```java
// Property removal with PayProp tag removal
public void removePropertyFromPortfolio(Long propertyId, Long portfolioId, Long userId) {
    log.info("🗑️ Removing property {} from portfolio {}", propertyId, portfolioId);
    
    Property property = propertyService.findById(propertyId);
    Portfolio portfolio = portfolioRepository.findById(portfolioId)
        .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));
    
    // Deactivate junction table assignment
    Optional<PropertyPortfolioAssignment> assignmentOpt = 
        assignmentRepository.findByPropertyIdAndPortfolioIdAndAssignmentTypeAndIsActive(
            propertyId, portfolioId, PortfolioAssignmentType.PRIMARY, Boolean.TRUE);
    
    if (assignmentOpt.isPresent()) {
        PropertyPortfolioAssignment assignment = assignmentOpt.get();
        assignment.setIsActive(Boolean.FALSE);
        assignment.setUpdatedAt(LocalDateTime.now());
        assignment.setUpdatedBy(userId);
        assignmentRepository.save(assignment);
    }
    
    // PAYPROP TAG REMOVAL
    if (shouldSyncToPayProp(portfolio, property)) {
        try {
            String tagValue = portfolio.getPayPropTags();
            
            // Handle tag name vs external ID resolution
            if (tagValue.startsWith("PF-") || tagValue.startsWith("BL-")) {
                log.warn("Portfolio {} has tag name instead of external ID for removal, resolving: {}", portfolio.getId(), tagValue);
                tagValue = ensurePortfolioHasExternalId(portfolio);
                if (tagValue == null) {
                    log.error("❌ Cannot resolve PayProp external ID for portfolio {} removal", portfolio.getId());
                    throw new RuntimeException("Failed to resolve PayProp external ID for tag removal");
                }
            }
            
            log.info("🔄 Removing PayProp tag {} from property {}", tagValue, property.getPayPropId());
            
            // PAYPROP API CALL: Remove tag from property
            payPropSyncService.removeTagFromProperty(property.getPayPropId(), tagValue);
            
            log.info("✅ PayProp tag removed successfully");
        } catch (Exception e) {
            log.error("❌ Failed to remove PayProp tag: {}", e.getMessage());
            throw new RuntimeException("Local removal succeeded, but PayProp tag removal failed: " + e.getMessage(), e);
        }
    }
    
    // Clear direct FK if it matches
    if (property.getPortfolio() != null && property.getPortfolio().getId().equals(portfolioId)) {
        property.setPortfolio(null);
        property.setPortfolioAssignmentDate(null);
        propertyService.save(property);
    }
}
```

**PayProp Tag Removal API Call**:
**File**: `PayPropPortfolioSyncService.java`

```java
// PAYPROP API CALL: Remove tag from property
public void removeTagFromProperty(String propertyId, String tagId) throws Exception {
    try {
        log.info("🗑️ Removing PayProp tag {} from property {}", tagId, propertyId);
        
        String endpoint = String.format("/tags/%s/entities", tagId);
        Map<String, String> params = new HashMap<>();
        params.put("entity_type", "property");
        params.put("entity_id", propertyId);
        
        // API Call: DELETE /tags/{tagId}/entities?entity_type=property&entity_id={propertyId}
        payPropApiClient.deleteWithParams(endpoint, params);
        
        log.info("✅ Successfully removed tag {} from property {}", tagId, propertyId);
        
    } catch (Exception e) {
        log.error("❌ Failed to remove tag {} from property {}: {}", tagId, propertyId, e.getMessage());
        throw new RuntimeException("PayProp tag removal failed: " + e.getMessage(), e);
    }
}
```

---

### Tag Deletion Flow

#### 1. Portfolio Deletion → PayProp Tag Deletion

**Entry Point**: `PayPropPortfolioSyncService.java`
**Route**: Portfolio deletion endpoints

```java
// Remove portfolio from PayProp (delete tag)
public void removePortfolioFromPayProp(Long portfolioId, Long initiatedBy) {
    Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
    if (portfolio == null || portfolio.getPayPropTags() == null) {
        log.warn("Portfolio {} not found or has no PayProp tags to remove", portfolioId);
        return;
    }
    
    PortfolioSyncLog syncLog = createSyncLog(portfolioId, null, null, 
        "PORTFOLIO_FROM_PAYPROP", "DELETE", initiatedBy);
    
    try {
        String tagId = portfolio.getPayPropTags();
        
        // Handle tag name vs external ID
        if (tagId.startsWith("PF-") || tagId.startsWith("BL-")) {
            tagId = ensurePortfolioHasExternalId(portfolio);
            if (tagId == null) {
                throw new RuntimeException("Cannot resolve PayProp external ID for deletion");
            }
        }
        
        log.info("🗑️ Removing PayProp tag for portfolio {}: {}", portfolioId, tagId);
        
        // PAYPROP API CALL: Delete tag
        deletePayPropTag(tagId);
        
        // Clear PayProp fields from portfolio
        portfolio.setPayPropTags(null);
        portfolio.setPayPropTagNames(null);
        portfolio.setSyncStatus(null);
        portfolio.setLastSyncAt(null);
        portfolioRepository.save(portfolio);
        
        updateSyncLog(syncLog, "SUCCESS", "Portfolio successfully removed from PayProp");
        log.info("✅ Portfolio {} successfully removed from PayProp", portfolioId);
        
    } catch (Exception e) {
        log.error("❌ Failed to remove portfolio {} from PayProp: {}", portfolioId, e.getMessage());
        updateSyncLog(syncLog, "FAILED", e.getMessage());
        throw new RuntimeException("PayProp portfolio removal failed: " + e.getMessage(), e);
    }
}

// PAYPROP API CALL: Delete tag
public void deletePayPropTag(String tagId) throws Exception {
    try {
        log.info("🗑️ Deleting PayProp tag: {}", tagId);
        
        String endpoint = String.format("/tags/%s", tagId);
        
        // API Call: DELETE /tags/{tagId}
        payPropApiClient.delete(endpoint);
        
        log.info("✅ PayProp tag deleted successfully: {}", tagId);
        
    } catch (Exception e) {
        log.error("❌ Failed to delete PayProp tag {}: {}", tagId, e.getMessage());
        throw new RuntimeException("PayProp tag deletion failed: " + e.getMessage(), e);
    }
}
```

---

### Bulk Sync Operations

#### 1. Sync All Portfolios

**Entry Point**: `PortfolioPayPropController.java`
**Route**: `POST /portfolio/sync-all`

```java
// Bulk sync all portfolios to PayProp
@PostMapping("/sync-all")
public ResponseEntity<Map<String, Object>> syncAllPortfolios(Authentication authentication) {
    int userId = authenticationUtils.getLoggedInUserId(authentication);
    
    try {
        // Get all portfolios that need syncing
        List<Portfolio> portfolios = portfolioService.findAll().stream()
            .filter(p -> p.getPayPropTags() == null || p.getSyncStatus() != SyncStatus.synced)
            .collect(Collectors.toList());
        
        int synced = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        
        for (Portfolio portfolio : portfolios) {
            try {
                // CALLS FULL TAG CREATION FLOW FOR EACH PORTFOLIO
                PayPropPortfolioSyncService.SyncResult result = 
                    payPropPortfolioSyncService.syncPortfolioToPayProp(portfolio.getId(), (long) userId);
                
                if (result.isSuccess()) {
                    synced++;
                } else {
                    failed++;
                    errors.add("Portfolio " + portfolio.getId() + ": " + result.getMessage());
                }
            } catch (Exception e) {
                failed++;
                errors.add("Portfolio " + portfolio.getId() + ": " + e.getMessage());
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("synced", synced);
        response.put("failed", failed);
        response.put("errors", errors);
        
        return ResponseEntity.ok(response);
        
    } catch (Exception e) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", e.getMessage());
        return ResponseEntity.status(500).body(errorResponse);
    }
}
```

#### 2. Sync Pending PayProp Tags

**Entry Point**: `PortfolioAssignmentService.java`
**Route**: Admin sync operations

```java
// Sync all pending PayProp tags for portfolio assignments
public SyncResult syncPendingPayPropTags() {
    log.info("🔄 Starting sync of pending PayProp tags");
    
    if (payPropSyncService == null) {
        return new SyncResult(false, "PayProp integration not available");
    }
    
    // Find all assignments with pending sync status
    List<PropertyPortfolioAssignment> pendingAssignments = 
        assignmentRepository.findAll().stream()
            .filter(a -> Boolean.TRUE.equals(a.getIsActive()) && a.getSyncStatus() == SyncStatus.pending)
            .collect(Collectors.toList());
    
    log.info("Found {} assignments pending PayProp sync", pendingAssignments.size());
    
    int syncedCount = 0;
    int failedCount = 0;
    List<String> errors = new ArrayList<>();
    
    for (PropertyPortfolioAssignment assignment : pendingAssignments) {
        try {
            Property property = assignment.getProperty();
            Portfolio portfolio = assignment.getPortfolio();
            
            if (shouldSyncToPayProp(portfolio, property)) {
                String tagValue = portfolio.getPayPropTags();
                
                // Handle tag name vs external ID resolution
                if (tagValue.startsWith("PF-") || tagValue.startsWith("BL-")) {
                    log.warn("Portfolio {} has tag name instead of external ID for sync, resolving: {}", portfolio.getId(), tagValue);
                    tagValue = ensurePortfolioHasExternalId(portfolio);
                    if (tagValue == null) {
                        throw new RuntimeException("Cannot resolve PayProp external ID for portfolio " + portfolio.getId());
                    }
                }
                
                // PAYPROP API CALL: Apply tag to property
                payPropSyncService.applyTagToProperty(property.getPayPropId(), tagValue);
                
                assignment.setSyncStatus(SyncStatus.synced);
                assignment.setLastSyncAt(LocalDateTime.now());
                assignmentRepository.save(assignment);
                syncedCount++;
                
                log.info("✅ Synced property {} to PayProp", property.getId());
            }
        } catch (Exception e) {
            assignment.setSyncStatus(SyncStatus.failed);
            assignment.setLastSyncAt(LocalDateTime.now());
            assignmentRepository.save(assignment);
            failedCount++;
            errors.add("Assignment " + assignment.getId() + ": " + e.getMessage());
            log.error("❌ Failed to sync assignment {}: {}", assignment.getId(), e.getMessage());
        }
    }
    
    String message = String.format("Synced %d assignments, %d failed", syncedCount, failedCount);
    return new SyncResult(failedCount == 0, message, errors);
}
```

---

### PayProp API Client Rate Limiting and Error Handling

**File**: `PayPropApiClient.java`

```java
// Rate limiting implementation
private static final int RATE_LIMIT_DELAY_MS = 250;  // 4 requests per second (under 5 limit)

public <T> List<T> fetchAllPages(String endpoint, Function<Map<String, Object>, T> mapper) {
    List<T> allResults = new ArrayList<>();
    int page = 1;
    
    while (page <= MAX_PAGES) {
        try {
            // Rate limiting - wait between API calls
            if (page > 1) {
                Thread.sleep(RATE_LIMIT_DELAY_MS);
            }
            
            // Fetch single page with authentication
            PayPropPageResult result = fetchSinglePage(endpoint, page, DEFAULT_PAGE_SIZE);
            
            // Process results
            for (Map<String, Object> item : result.getItems()) {
                allResults.add(mapper.apply(item));
            }
            
            // Check if more pages available
            if (result.getItems().size() < DEFAULT_PAGE_SIZE) {
                break; // Last page
            }
            
            page++;
            
        } catch (Exception e) {
            log.error("❌ Failed to fetch page {} from {}: {}", page, endpoint, e.getMessage());
            throw new PayPropSyncException("Pagination failed: " + e.getMessage(), e);
        }
    }
    
    return allResults;
}

// Generic HTTP operations with authentication and error handling
public <T> T post(String endpoint, Object requestBody, Class<T> responseType) throws Exception {
    return makeAuthenticatedRequest(endpoint, HttpMethod.POST, requestBody, responseType);
}

public <T> T get(String endpoint, Class<T> responseType) throws Exception {
    return makeAuthenticatedRequest(endpoint, HttpMethod.GET, null, responseType);
}

public void delete(String endpoint) throws Exception {
    makeAuthenticatedRequest(endpoint, HttpMethod.DELETE, null, Void.class);
}

public void deleteWithParams(String endpoint, Map<String, String> params) throws Exception {
    StringBuilder url = new StringBuilder(endpoint);
    if (!params.isEmpty()) {
        url.append("?");
        params.forEach((key, value) -> url.append(key).append("=").append(value).append("&"));
    }
    delete(url.toString());
}
```

---

## PayProp API Call Summary

### Authentication Calls
1. **OAuth2 Authorization**: `GET /oauth/authorize` (user redirect)
2. **Token Exchange**: `POST /oauth/access_token` (code → tokens)
3. **Token Refresh**: `POST /oauth/access_token` (refresh → new tokens)

### Tag Management Calls
1. **Search Tags**: `GET /tags?name={name}&rows=25`
2. **Create Tag**: `POST /tags` with `{name: "tag_name"}`
3. **Delete Tag**: `DELETE /tags/{external_id}`

### Tag-Entity Linking Calls
1. **Apply Tag to Property**: `POST /tags/entities/property/{property_id}` with `{tags: [tag_id]}`
2. **Remove Tag from Property**: `DELETE /tags/{tag_id}/entities?entity_type=property&entity_id={property_id}`

### All API calls include:
- **OAuth2 Bearer token authentication** in headers
- **Rate limiting** (250ms delays between calls)
- **Comprehensive error handling** with retry logic
- **Detailed logging** for debugging and monitoring

This comprehensive map documents the entire PayProp integration system, from API specifications to database schema, configuration, assignment flow conflicts, and critical debugging information.

---

## Database State Analysis - Root Cause Identification

### Current Database Schema Analysis

Based on the actual database schema and data analysis:

#### Portfolio Table Structure
```sql
CREATE TABLE `portfolios` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `description` text,
  `portfolio_type` enum('GEOGRAPHIC','PROPERTY_TYPE','INVESTMENT_CLASS','TENANT_TYPE','CUSTOM') DEFAULT 'CUSTOM',
  `payprop_tags` text COMMENT 'Comma-separated PayProp tag IDs',           -- ⚠️ CRITICAL FIELD
  `payprop_tag_names` text COMMENT 'Human-readable tag names for UI',       -- ⚠️ CRITICAL FIELD  
  `last_sync_at` timestamp NULL DEFAULT NULL,
  `sync_status` enum('pending','syncing','synced','failed','conflict') DEFAULT 'pending',
  `created_by` int NOT NULL COMMENT 'User who created this portfolio',
  -- ... other fields
)
```

#### Property Portfolio Assignment Table Structure
```sql
CREATE TABLE `property_portfolio_assignments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `property_id` bigint NOT NULL,
  `portfolio_id` bigint NOT NULL,
  `assignment_type` varchar(50) NOT NULL DEFAULT 'PRIMARY',
  `assigned_by` bigint NOT NULL,
  `assigned_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `notes` varchar(500) DEFAULT NULL,
  `is_active` tinyint(1) DEFAULT '1',
  `display_order` int DEFAULT '0',
  `sync_status` varchar(50) DEFAULT 'pending',                              -- ⚠️ CRITICAL FIELD
  -- ... other fields
)
```

### 🔥 ROOT CAUSE IDENTIFIED: Current Database State Issues

#### Problem 1: Portfolios Missing PayProp Tag IDs
```sql
-- Current portfolio data from database:
INSERT INTO `portfolios` VALUES (
  1,'Bitch RTesr','...','CUSTOM',
  NULL,                           -- ⚠️ payprop_tags = NULL (PROBLEM!)
  'PF-BITCH-RTESR',              -- ✅ payprop_tag_names populated
  '2025-08-17 07:15:04','synced',54,1105,...
);

INSERT INTO `portfolios` VALUES (
  2,'Tester Newtest','...','CUSTOM',
  NULL,                           -- ⚠️ payprop_tags = NULL (PROBLEM!)
  'PF-TESTER-NEWTEST',           -- ✅ payprop_tag_names populated  
  '2025-08-17 07:58:27','synced',54,1105,...
);
```

**Analysis**: Portfolios are being created with:
- ✅ `payprop_tag_names` = human-readable names (e.g., "PF-BITCH-RTESR")
- ❌ `payprop_tags` = NULL (should contain PayProp external IDs)
- ✅ `sync_status` = 'synced' (misleading - not actually synced to PayProp)

#### Problem 2: Properties Have PayProp IDs But Assignments Fail
```sql
-- Properties involved in failed assignments:
-- Property 14: '8eJPKR7VZG' (Bennett Grove 58, Dover)
-- Property 36: 'ge1alGapXE' (Canon Murnane Road 89, Lambeth)

INSERT INTO `property_portfolio_assignments` VALUES (
  1,14,1,'PRIMARY',54,'2025-08-17 07:16:21','Assigned via portfolio UI v2',
  1,0,'pending',                  -- ⚠️ sync_status = 'pending' (never synced)
  NULL,'2025-08-17 07:16:21','2025-08-17 07:16:21',NULL
);

INSERT INTO `property_portfolio_assignments` VALUES (
  2,36,2,'PRIMARY',54,'2025-08-17 07:59:06','Assigned via PortfolioAssignmentService',
  1,0,'pending',                  -- ⚠️ sync_status = 'pending' (never synced)
  NULL,'2025-08-17 07:59:06','2025-08-17 07:59:06',NULL
);
```

**Analysis**: Property assignments are created with:
- ✅ Properties have valid PayProp IDs (`8eJPKR7VZG`, `ge1alGapXE`)
- ❌ Assignments remain in 'pending' status 
- ❌ PayProp API never called due to portfolio missing `payprop_tags`

### 🎯 Why PayProp Sync Conditions Fail

From `PortfolioAssignmentService.shouldSyncToPayProp()` debug analysis:

```java
// The 5 conditions that must ALL pass:
boolean serviceAvailable = payPropPortfolioService.isAvailable();                    // ✅ TRUE
boolean portfolioHasTags = portfolio.getPayPropTags() != null;                      // ❌ FALSE - NULL
boolean portfolioTagsNotEmpty = !portfolio.getPayPropTags().trim().isEmpty();       // ❌ FALSE - NPE risk
boolean propertyHasPayPropId = property.getPayPropId() != null;                     // ✅ TRUE  
boolean propertyPayPropIdNotEmpty = !property.getPayPropId().trim().isEmpty();      // ✅ TRUE

boolean shouldSync = serviceAvailable && portfolioHasTags && 
                    portfolioTagsNotEmpty && propertyHasPayPropId && 
                    propertyPayPropIdNotEmpty;                                       // ❌ FALSE (overall)
```

**Root Cause**: `portfolio.getPayPropTags()` returns `NULL`, causing the sync condition to fail immediately.

### 🔧 Tag Creation Process Analysis

From the database evidence, the tag creation process is **partially working**:

1. ✅ **Tag Name Generation**: `PortfolioService.generatePortfolioTagName()` works correctly
   - Generates: "PF-BITCH-RTESR", "PF-TESTER-NEWTEST"
   - Stores in `payprop_tag_names` field

2. ❌ **PayProp Tag Creation**: `PayPropPortfolioService.ensurePayPropTagExists()` fails
   - Should call PayProp API to create tag and get external ID
   - Should store external ID in `payprop_tags` field
   - Currently leaving `payprop_tags` as NULL

### 🔍 Tag Creation Investigation

The tag creation failure means either:

**A) PayProp API Call Failing**:
```java
// In PayPropPortfolioService.ensurePayPropTagExists()
Map<String, Object> tagResponse = payPropApiClient.post("/tags", tagRequest);
String externalId = (String) tagResponse.get("id");  // ← This fails?
```

**B) Tag Storage Failing**:
```java
// After successful API call, storage fails:
portfolio.setPayPropTags(externalId);  // ← This isn't persisting?
portfolioRepository.save(portfolio);
```

**C) Exception Handling Masking Errors**:
```java
// Errors caught and logged but not thrown:
} catch (Exception e) {
    log.error("Failed to ensure PayProp tag exists: {}", e.getMessage());
    return null;  // ← Silent failure
}
```

### 📋 Summary: Database Investigation Complete

**Investigation Status**: ✅ COMPLETE - Root cause identified

**Root Cause**: Portfolio creation successfully generates tag names but fails to create PayProp tags and store external IDs, causing all subsequent assignment sync operations to fail the `shouldSyncToPayProp()` condition check.

**Next Steps**: Fix the tag creation process in `PayPropPortfolioService.ensurePayPropTagExists()` to ensure PayProp API calls succeed and external IDs are properly stored in the `payprop_tags` field.

---

## 🚨 Emergency Fix Implementation Plan

### Phase 0: Root Cause Resolution (Priority: CRITICAL)

#### 🔥 Step 1: Debug Tag Creation Failure (Day 1)

**Problem**: `PayPropPortfolioService.ensurePayPropTagExists()` fails silently, leaving `payprop_tags = NULL`

**Action Items**:

1. **Add Comprehensive Logging to Tag Creation**:
```java
// In PayPropPortfolioService.ensurePayPropTagExists()
public String ensurePayPropTagExists(String tagName) {
    log.info("🏷️ Starting tag creation/verification for: '{}'", tagName);
    
    try {
        // Step 1: Check if tag already exists
        log.debug("Step 1: Searching for existing tag with name: '{}'", tagName);
        List<PayPropTagDTO> existingTags = searchPayPropTagsByName(tagName);
        
        if (!existingTags.isEmpty()) {
            PayPropTagDTO existingTag = existingTags.get(0);
            log.info("✅ Found existing PayProp tag: '{}' with ID: '{}'", tagName, existingTag.getId());
            return existingTag.getId();
        }
        
        // Step 2: Create new tag
        log.info("📝 Creating new PayProp tag: '{}'", tagName);
        Map<String, Object> tagRequest = new HashMap<>();
        tagRequest.put("name", tagName);
        
        log.debug("API Request: POST /tags with payload: {}", tagRequest);
        Map<String, Object> tagResponse = payPropApiClient.post("/tags", tagRequest);
        log.debug("API Response: {}", tagResponse);
        
        // Step 3: Extract external ID
        String externalId = (String) tagResponse.get("id");
        if (externalId == null || externalId.trim().isEmpty()) {
            log.error("❌ PayProp API returned null/empty external ID. Full response: {}", tagResponse);
            throw new RuntimeException("PayProp API returned invalid tag ID");
        }
        
        log.info("✅ Successfully created PayProp tag: '{}' with external ID: '{}'", tagName, externalId);
        return externalId;
        
    } catch (Exception e) {
        log.error("❌ CRITICAL: Tag creation failed for '{}': {}", tagName, e.getMessage(), e);
        // DON'T return null - throw the exception to see what's breaking
        throw new RuntimeException("Tag creation failed: " + e.getMessage(), e);
    }
}
```

2. **Add OAuth2 Token Validation**:
```java
// Before tag operations, verify authentication
private void validateAuthentication() {
    try {
        if (oAuth2Service == null) {
            throw new RuntimeException("OAuth2 service not available");
        }
        
        if (!oAuth2Service.hasValidToken()) {
            log.warn("⚠️ PayProp token expired, attempting refresh...");
            oAuth2Service.refreshToken();
        }
        
        log.debug("✅ PayProp authentication validated");
    } catch (Exception e) {
        log.error("❌ PayProp authentication failed: {}", e.getMessage());
        throw new RuntimeException("PayProp authentication failed", e);
    }
}
```

3. **Test Single Portfolio Creation**:
```bash
# Create test portfolio with detailed logging
curl -X POST "http://localhost:8080/api/portfolio/create" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "DEBUG-TEST-PORTFOLIO",
    "description": "Test portfolio for debugging tag creation",
    "portfolioType": "CUSTOM"
  }'
```

#### 🔧 Step 2: Fix Identified Issues (Day 2)

**Based on debug output, implement fixes**:

**Scenario A: API Authentication Failing**:
```java
// Fix OAuth2 token issues
@PostConstruct
public void validatePayPropConnection() {
    try {
        log.info("🔌 Testing PayProp API connection...");
        Map<String, Object> testResponse = payPropApiClient.get("/properties?rows=1");
        log.info("✅ PayProp API connection successful");
    } catch (Exception e) {
        log.error("❌ PayProp API connection failed: {}", e.getMessage());
        // Don't fail startup, but log the issue
    }
}
```

**Scenario B: API Response Format Issues**:
```java
// Handle different PayProp response formats
private String extractTagId(Map<String, Object> response) {
    // Try different possible ID field names
    String[] possibleFields = {"id", "external_id", "tag_id", "tagId"};
    
    for (String field : possibleFields) {
        Object value = response.get(field);
        if (value != null && !value.toString().trim().isEmpty()) {
            log.debug("Found tag ID in field '{}': '{}'", field, value);
            return value.toString();
        }
    }
    
    log.error("❌ Could not find tag ID in response: {}", response);
    throw new RuntimeException("PayProp response missing tag ID: " + response);
}
```

**Scenario C: Database Transaction Issues**:
```java
// Ensure tag storage is transactional
@Transactional
public Portfolio createPortfolioWithPayPropSync(Portfolio portfolio) {
    try {
        // 1. Save portfolio first
        Portfolio savedPortfolio = portfolioRepository.save(portfolio);
        
        // 2. Create PayProp tag
        String tagName = generatePortfolioTagName(savedPortfolio);
        String externalId = ensurePayPropTagExists(tagName);
        
        // 3. Update portfolio with PayProp data
        savedPortfolio.setPayPropTags(externalId);
        savedPortfolio.setPayPropTagNames(tagName);
        savedPortfolio.setSyncStatus(SyncStatus.synced);
        savedPortfolio.setLastSyncAt(LocalDateTime.now());
        
        // 4. Save again with PayProp data
        Portfolio finalPortfolio = portfolioRepository.save(savedPortfolio);
        
        log.info("✅ Portfolio {} created with PayProp tag: {} (ID: {})", 
            finalPortfolio.getId(), tagName, externalId);
        
        return finalPortfolio;
        
    } catch (Exception e) {
        log.error("❌ Portfolio creation with PayProp sync failed: {}", e.getMessage());
        throw new RuntimeException("Portfolio creation failed", e);
    }
}
```

#### 🔄 Step 3: Fix Existing Broken Portfolios (Day 3)

**Migration Service**:
```java
@Service
public class PayPropPortfolioMigrationService {
    
    @Transactional
    public void fixBrokenPortfolios() {
        log.info("🔄 Starting migration of portfolios with missing PayProp tags...");
        
        // Find portfolios with missing external IDs
        List<Portfolio> brokenPortfolios = portfolioRepository.findAll().stream()
            .filter(p -> p.getPayPropTagNames() != null && p.getPayPropTags() == null)
            .collect(Collectors.toList());
        
        log.info("Found {} portfolios needing PayProp tag fix", brokenPortfolios.size());
        
        int fixed = 0;
        int failed = 0;
        
        for (Portfolio portfolio : brokenPortfolios) {
            try {
                String tagName = portfolio.getPayPropTagNames();
                log.info("🔧 Fixing portfolio {}: '{}'", portfolio.getId(), tagName);
                
                // Create/find PayProp tag
                String externalId = ensurePayPropTagExists(tagName);
                
                // Update portfolio
                portfolio.setPayPropTags(externalId);
                portfolio.setSyncStatus(SyncStatus.synced);
                portfolio.setLastSyncAt(LocalDateTime.now());
                portfolioRepository.save(portfolio);
                
                // Sync pending property assignments
                syncPendingAssignments(portfolio);
                
                fixed++;
                log.info("✅ Fixed portfolio {}", portfolio.getId());
                
            } catch (Exception e) {
                failed++;
                log.error("❌ Failed to fix portfolio {}: {}", portfolio.getId(), e.getMessage());
            }
        }
        
        log.info("✅ Migration complete: {} fixed, {} failed", fixed, failed);
    }
    
    private void syncPendingAssignments(Portfolio portfolio) {
        List<PropertyPortfolioAssignment> pendingAssignments = 
            assignmentRepository.findByPortfolioAndSyncStatus(portfolio, SyncStatus.pending);
        
        for (PropertyPortfolioAssignment assignment : pendingAssignments) {
            try {
                portfolioAssignmentService.syncAssignmentToPayProp(assignment);
                log.debug("✅ Synced assignment {} to PayProp", assignment.getId());
            } catch (Exception e) {
                log.error("❌ Failed to sync assignment {}: {}", assignment.getId(), e.getMessage());
            }
        }
    }
}
```

**Migration Endpoint**:
```java
@PostMapping("/admin/payprop/fix-broken-portfolios")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<Map<String, Object>> fixBrokenPortfolios() {
    try {
        payPropPortfolioMigrationService.fixBrokenPortfolios();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Portfolio migration completed");
        
        return ResponseEntity.ok(response);
    } catch (Exception e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", e.getMessage());
        
        return ResponseEntity.status(500).body(response);
    }
}
```

### Phase 1: Stabilization & Testing (Days 4-5)

#### 🧪 Step 4: Comprehensive Testing

**Test Cases**:

1. **New Portfolio Creation**:
```bash
# Test 1: Basic portfolio creation
POST /api/portfolio/create
{
  "name": "Test Portfolio New",
  "description": "Testing fixed tag creation",
  "portfolioType": "CUSTOM"
}

# Verify: payprop_tags populated, sync_status = 'synced'
```

2. **Property Assignment**:
```bash
# Test 2: Property assignment to fixed portfolio
POST /api/portfolio/{portfolioId}/assign-properties-v2
{
  "propertyIds": [14],
  "userId": 54
}

# Verify: sync_status = 'synced', PayProp API called
```

3. **End-to-End Workflow**:
```bash
# Test 3: Complete workflow
1. Create portfolio → Verify PayProp tag creation
2. Assign property → Verify PayProp tag application  
3. Remove property → Verify PayProp tag removal
4. Delete portfolio → Verify PayProp tag deletion
```

#### 📊 Step 5: Monitoring & Validation

**Health Check Endpoint**:
```java
@GetMapping("/admin/payprop/health")
public ResponseEntity<Map<String, Object>> checkPayPropHealth() {
    Map<String, Object> health = new HashMap<>();
    
    try {
        // Test API connectivity
        payPropApiClient.get("/properties?rows=1");
        health.put("api_connectivity", "OK");
        
        // Check broken portfolios
        long brokenCount = portfolioRepository.countByPayPropTagNamesIsNotNullAndPayPropTagsIsNull();
        health.put("broken_portfolios", brokenCount);
        
        // Check pending assignments
        long pendingCount = assignmentRepository.countBySyncStatus(SyncStatus.pending);
        health.put("pending_assignments", pendingCount);
        
        health.put("status", brokenCount == 0 && pendingCount == 0 ? "HEALTHY" : "NEEDS_ATTENTION");
        
        return ResponseEntity.ok(health);
        
    } catch (Exception e) {
        health.put("status", "ERROR");
        health.put("error", e.getMessage());
        return ResponseEntity.status(500).body(health);
    }
}
```

### Phase 2: Portfolio-Block System Implementation

**Only proceed after Phase 0-1 complete and validated**

#### 🏗️ Step 6: Block Architecture (Week 2)

**Foundation Requirements**:
- ✅ Portfolio creation works (payprop_tags populated)
- ✅ Property assignments sync to PayProp  
- ✅ Zero broken portfolios remain
- ✅ All pending assignments processed

**Implementation Order**:
1. Database schema updates for blocks
2. Block creation with hierarchical tags (`PF-{portfolio}-BL-{block}`)
3. Block assignment logic
4. UI enhancements for drag-and-drop
5. Reporting and analytics

### Success Criteria

**Phase 0 Success Metrics**:
- [ ] `ensurePayPropTagExists()` returns valid external IDs (never null)
- [ ] New portfolios have `payprop_tags` populated immediately  
- [ ] Property assignments sync successfully (sync_status = 'synced')
- [ ] Zero portfolios with `payprop_tags = NULL` and `payprop_tag_names != NULL`
- [ ] Debug logs show successful PayProp API calls

**Validation Queries**:
```sql
-- Should return 0 after fix
SELECT COUNT(*) FROM portfolios 
WHERE payprop_tag_names IS NOT NULL 
AND payprop_tags IS NULL;

-- Should return 0 after fix  
SELECT COUNT(*) FROM property_portfolio_assignments 
WHERE sync_status = 'pending' 
AND is_active = 1;
```

This emergency fix plan addresses the root cause first, then enables the comprehensive portfolio-block architecture to be built on a solid, working foundation.
```

**Configuration Properties**:
```properties
# OAuth2 Settings
payprop.oauth2.client-id=${PAYPROP_CLIENT_ID}
payprop.oauth2.client-secret=${PAYPROP_CLIENT_SECRET}
payprop.oauth2.redirect-uri=${PAYPROP_REDIRECT_URI}
payprop.oauth2.authorization-uri=https://app.payprop.com/oauth/authorize
payprop.oauth2.token-uri=https://app.payprop.com/oauth/token
payprop.oauth2.scope=read:agency write:agency
```

### API Client

**Service**: `PayPropApiClient`
**Location**: `src/main/java/site/easy/to/build/crm/service/payprop/PayPropApiClient.java`

**Core Functionality**:
```java
// HTTP operations with authentication
public <T> T get(String endpoint, Class<T> responseType)
public <T> T post(String endpoint, Object requestBody, Class<T> responseType)
public <T> T put(String endpoint, Object requestBody, Class<T> responseType)
public void delete(String endpoint)

// Rate limiting and retry logic
private void handleRateLimit()
private <T> T executeWithRetry(HttpRequest request, Class<T> responseType)
```

**Base URL**: `https://app.payprop.com/api/agency/v1.1`

---

## Core Services Architecture

### 1. Main Sync Service

**Service**: `PayPropSyncService`
**Location**: `src/main/java/site/easy/to/build/crm/service/payprop/PayPropSyncService.java`

**Primary Responsibilities**:
- Property synchronization
- Tenant synchronization 
- Beneficiary synchronization
- Tag management
- File attachment handling

---

## 🚨 EMERGENCY FIX COMPLETED - 2025-08-17 ✅

**ROOT CAUSE FIXED & VALIDATED**: Portfolio creation was storing `payprop_tags = NULL` but `payprop_tag_names` populated, causing all property assignments to fail sync conditions.

### ✅ Emergency Fix Implementation
- Enhanced `PayPropPortfolioSyncService.ensurePayPropTagExists()` with robust error handling
- Created `PayPropPortfolioMigrationService` for fixing broken portfolios  
- Added health monitoring (`/admin/payprop/health`) and debug endpoints
- Fixed compilation errors and deployed successfully

### ✅ Validation Confirmed
- **UI Test**: Property deletion removes PayProp tags correctly
- **PayProp Dashboard**: Tags `PF-BITCH-RTESR` and `PF-TESTER-NEWTEST` working
- **Database**: Portfolios now have proper external IDs
- **Sync Status**: "PayProp synced: 0" issue RESOLVED

### 📋 Next Steps
- Run SQL cleanup for manually deleted tags (see `PAYPROP_PROGRESS_LOG.md`)
- Portfolio-Block System implementation is now SAFE TO PROCEED
- Foundation is stable for further development

**For detailed progress log and debugging guide: See `PAYPROP_PROGRESS_LOG.md`**

---

**Key Methods**:

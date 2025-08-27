# PayProp Raw Data Storage Implementation Specification

## Problem Analysis

**Current State:**
- PayProp API responses are being processed and flattened into database columns
- Original nested JSON structure from API is lost
- Cannot access raw API data structure for analysis or debugging
- Financial reporting works but lacks access to original data format

**Sample Raw API Structure (from /reports/all-payments):**
```json
{
  "amount": "15.50",
  "beneficiary": {"id": "DWzJBkWXQB", "name": null, "type": "agency"},
  "category": {"id": "Kd71e915Ma", "name": "Commission"},
  "incoming_transaction": {
    "amount": "126.00",
    "property": {"id": "8EJAAwgeJj", "name": "Chesterfield Street 57..."},
    "tenant": {"id": "D6JmWjbk1v", "name": "Andrews Holly"},
    "type": "instant bank transfer",
    "status": "paid",
    "reconciliation_date": "2025-06-12"
  }
}
```

**Current Flattened Storage:**
- `incoming_transaction_amount` column
- `incoming_property_name` column  
- `incoming_tenant_name` column
- Lost: nested object relationships, full API structure

## Solution: Dual Storage Approach

Store both raw JSON and processed/flattened data to maintain API fidelity while preserving current functionality.

---

## Database Schema Changes

### 1. Raw Storage Tables

```sql
-- Raw storage for all endpoints
CREATE TABLE `payprop_raw_storage` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `endpoint` VARCHAR(100) NOT NULL,
  `payprop_id` VARCHAR(50) NOT NULL,
  `raw_json` JSON NOT NULL,
  `imported_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `sync_status` ENUM('success', 'error', 'pending') DEFAULT 'success',
  `api_response_date` TIMESTAMP NULL,
  
  UNIQUE KEY `uk_endpoint_payprop_id` (`endpoint`, `payprop_id`),
  INDEX `idx_endpoint` (`endpoint`),
  INDEX `idx_imported_at` (`imported_at`),
  INDEX `idx_sync_status` (`sync_status`)
);

-- Endpoint-specific raw tables for performance
CREATE TABLE `payprop_raw_all_payments` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `payprop_id` VARCHAR(50) UNIQUE NOT NULL,
  `raw_json` JSON NOT NULL,
  `amount` DECIMAL(10,2) GENERATED ALWAYS AS (CAST(JSON_UNQUOTE(JSON_EXTRACT(raw_json, '$.amount')) AS DECIMAL(10,2))) STORED,
  `category_name` VARCHAR(100) GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(raw_json, '$.category.name'))) STORED,
  `incoming_transaction_amount` DECIMAL(10,2) GENERATED ALWAYS AS (CAST(JSON_UNQUOTE(JSON_EXTRACT(raw_json, '$.incoming_transaction.amount')) AS DECIMAL(10,2))) STORED,
  `imported_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  INDEX `idx_amount` (`amount`),
  INDEX `idx_category_name` (`category_name`),
  INDEX `idx_incoming_amount` (`incoming_transaction_amount`)
);

CREATE TABLE `payprop_raw_properties` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `payprop_id` VARCHAR(50) UNIQUE NOT NULL,
  `raw_json` JSON NOT NULL,
  `name` VARCHAR(255) GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(raw_json, '$.name'))) STORED,
  `postal_code` VARCHAR(20) GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(raw_json, '$.postal_code'))) STORED,
  `imported_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  INDEX `idx_name` (`name`),
  INDEX `idx_postal_code` (`postal_code`)
);

CREATE TABLE `payprop_raw_tenants` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `payprop_id` VARCHAR(50) UNIQUE NOT NULL,
  `raw_json` JSON NOT NULL,
  `display_name` VARCHAR(255) GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(raw_json, '$.display_name'))) STORED,
  `email` VARCHAR(255) GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(raw_json, '$.email'))) STORED,
  `imported_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  INDEX `idx_display_name` (`display_name`),
  INDEX `idx_email` (`email`)
);

CREATE TABLE `payprop_raw_beneficiaries` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `payprop_id` VARCHAR(50) UNIQUE NOT NULL,
  `raw_json` JSON NOT NULL,
  `name` VARCHAR(255) GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(raw_json, '$.name'))) STORED,
  `type` VARCHAR(50) GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(raw_json, '$.type'))) STORED,
  `imported_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  INDEX `idx_name` (`name`),
  INDEX `idx_type` (`type`)
);

CREATE TABLE `payprop_raw_payment_instructions` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `payprop_id` VARCHAR(50) UNIQUE NOT NULL,
  `raw_json` JSON NOT NULL,
  `category` VARCHAR(100) GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(raw_json, '$.category'))) STORED,
  `gross_amount` DECIMAL(10,2) GENERATED ALWAYS AS (CAST(JSON_UNQUOTE(JSON_EXTRACT(raw_json, '$.gross_amount')) AS DECIMAL(10,2))) STORED,
  `imported_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  INDEX `idx_category` (`category`)
);

CREATE TABLE `payprop_raw_categories` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `payprop_id` VARCHAR(50) UNIQUE NOT NULL,
  `endpoint` VARCHAR(50) NOT NULL, -- 'payments/categories', 'invoice/categories'
  `raw_json` JSON NOT NULL,
  `name` VARCHAR(255) GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(raw_json, '$.name'))) STORED,
  `imported_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  INDEX `idx_endpoint_name` (`endpoint`, `name`)
);
```

---

## Service Layer Implementation

### 1. Base Raw Storage Service

```java
package site.easy.to.build.crm.service.payprop.raw;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PayPropRawStorageService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawStorageService.class);
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Store raw API response data with dual storage approach
     */
    @Transactional
    public int storeRawData(String endpoint, List<Map<String, Object>> rawData) {
        log.info("Storing raw data for endpoint: {} ({} records)", endpoint, rawData.size());
        
        int storedCount = 0;
        
        try {
            // Store in generic raw storage table
            storedCount = storeInGenericTable(endpoint, rawData);
            
            // Store in endpoint-specific table with generated columns
            storeInSpecificTable(endpoint, rawData);
            
            log.info("Successfully stored {} raw records for {}", storedCount, endpoint);
            
        } catch (Exception e) {
            log.error("Failed to store raw data for endpoint: {}", endpoint, e);
            throw new RuntimeException("Raw storage failed", e);
        }
        
        return storedCount;
    }
    
    private int storeInGenericTable(String endpoint, List<Map<String, Object>> rawData) throws Exception {
        String sql = """
            INSERT INTO payprop_raw_storage (endpoint, payprop_id, raw_json, api_response_date)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE 
                raw_json = VALUES(raw_json),
                api_response_date = VALUES(api_response_date),
                imported_at = CURRENT_TIMESTAMP
        """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            for (Map<String, Object> item : rawData) {
                String paypropId = getPayPropId(item);
                if (paypropId == null) continue;
                
                stmt.setString(1, endpoint);
                stmt.setString(2, paypropId);
                stmt.setString(3, objectMapper.writeValueAsString(item));
                stmt.setTimestamp(4, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                
                stmt.addBatch();
            }
            
            int[] results = stmt.executeBatch();
            return results.length;
        }
    }
    
    private void storeInSpecificTable(String endpoint, List<Map<String, Object>> rawData) throws Exception {
        String tableName = getSpecificTableName(endpoint);
        if (tableName == null) {
            log.debug("No specific table for endpoint: {}", endpoint);
            return;
        }
        
        String sql = String.format("""
            INSERT INTO %s (payprop_id, raw_json)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE 
                raw_json = VALUES(raw_json),
                imported_at = CURRENT_TIMESTAMP
        """, tableName);
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            for (Map<String, Object> item : rawData) {
                String paypropId = getPayPropId(item);
                if (paypropId == null) continue;
                
                stmt.setString(1, paypropId);
                stmt.setString(2, objectMapper.writeValueAsString(item));
                stmt.addBatch();
            }
            
            stmt.executeBatch();
        }
    }
    
    private String getPayPropId(Map<String, Object> item) {
        return item.get("id") != null ? item.get("id").toString() : null;
    }
    
    private String getSpecificTableName(String endpoint) {
        return switch (endpoint) {
            case "/reports/all-payments" -> "payprop_raw_all_payments";
            case "/export/properties" -> "payprop_raw_properties";
            case "/export/tenants" -> "payprop_raw_tenants";
            case "/export/beneficiaries" -> "payprop_raw_beneficiaries";
            case "/export/payments" -> "payprop_raw_payment_instructions";
            case "/payments/categories", "/invoice/categories" -> "payprop_raw_categories";
            default -> null;
        };
    }
}
```

### 2. Enhanced Import Services

```java
package site.easy.to.build.crm.service.payprop.raw;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayPropEnhancedAllPaymentsImportService {
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private PayPropRawStorageService rawStorageService;
    
    @Autowired
    private PayPropRawAllPaymentsImportService existingImportService; // Keep existing functionality
    
    @Transactional
    public PayPropRawImportResult importAllPaymentsWithRawStorage() {
        log.info("Starting enhanced all-payments import with raw storage");
        
        try {
            // Fetch raw data from API
            List<Map<String, Object>> rawPayments = apiClient.fetchAllPages(
                "/reports/all-payments",
                payment -> payment // Return unprocessed
            );
            
            // Store raw data first
            rawStorageService.storeRawData("/reports/all-payments", rawPayments);
            
            // Then run existing import logic for processed tables
            PayPropRawImportResult result = existingImportService.importAllPayments();
            
            result.setMessage("Enhanced import: raw + processed data stored");
            return result;
            
        } catch (Exception e) {
            log.error("Enhanced import failed", e);
            PayPropRawImportResult result = new PayPropRawImportResult();
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }
}
```

---

## Controller Endpoints

### New Raw Storage Endpoints

```java
@RestController
@RequestMapping("/api/payprop/raw")
public class PayPropRawStorageController {
    
    @Autowired
    private PayPropEnhancedAllPaymentsImportService enhancedService;
    
    @PostMapping("/import-all-payments-enhanced")
    public ResponseEntity<?> importAllPaymentsEnhanced() {
        PayPropRawImportResult result = enhancedService.importAllPaymentsWithRawStorage();
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/raw-data/{endpoint}")
    public ResponseEntity<?> getRawData(@PathVariable String endpoint, 
                                       @RequestParam(defaultValue = "10") int limit) {
        // Query raw storage for specific endpoint
        List<Map<String, Object>> rawData = rawStorageService.getRawData(endpoint, limit);
        return ResponseEntity.ok(rawData);
    }
    
    @PostMapping("/compare-raw-processed/{paypropId}")
    public ResponseEntity<?> compareRawVsProcessed(@PathVariable String paypropId) {
        // Compare raw JSON vs processed database record
        Map<String, Object> comparison = rawStorageService.compareData(paypropId);
        return ResponseEntity.ok(comparison);
    }
}
```

---

## Data Analysis Queries

### Raw Data Access Examples

```sql
-- Get original nested structure for a payment
SELECT 
    payprop_id,
    JSON_PRETTY(raw_json) as original_structure
FROM payprop_raw_all_payments 
WHERE payprop_id = 'AJ5wQ4pv1M';

-- Extract rent amounts from raw incoming_transaction data
SELECT 
    payprop_id,
    JSON_UNQUOTE(JSON_EXTRACT(raw_json, '$.amount')) as payment_amount,
    JSON_UNQUOTE(JSON_EXTRACT(raw_json, '$.incoming_transaction.amount')) as original_rent_amount,
    JSON_UNQUOTE(JSON_EXTRACT(raw_json, '$.category.name')) as category,
    JSON_UNQUOTE(JSON_EXTRACT(raw_json, '$.incoming_transaction.property.name')) as property_name,
    JSON_UNQUOTE(JSON_EXTRACT(raw_json, '$.incoming_transaction.tenant.name')) as tenant_name
FROM payprop_raw_all_payments
WHERE JSON_EXTRACT(raw_json, '$.incoming_transaction.amount') IS NOT NULL
ORDER BY imported_at DESC
LIMIT 10;

-- Compare raw vs processed data
SELECT 
    r.payprop_id,
    JSON_UNQUOTE(JSON_EXTRACT(r.raw_json, '$.amount')) as raw_amount,
    p.amount as processed_amount,
    JSON_UNQUOTE(JSON_EXTRACT(r.raw_json, '$.category.name')) as raw_category,
    p.category_name as processed_category
FROM payprop_raw_all_payments r
LEFT JOIN payprop_report_all_payments p ON r.payprop_id = p.payprop_id
WHERE r.payprop_id = 'AJ5wQ4pv1M';
```

---

## Implementation Plan

### Phase 1: Database Setup
1. Create raw storage tables with generated columns
2. Add indexes for performance
3. Test JSON extraction functions

### Phase 2: Service Layer
1. Implement `PayPropRawStorageService`
2. Enhance existing import services to use dual storage
3. Add validation between raw and processed data

### Phase 3: Controller Layer
1. Add raw storage endpoints
2. Create comparison and analysis endpoints
3. Add raw data retrieval methods

### Phase 4: Validation
1. Verify raw JSON storage is working
2. Compare raw vs processed data accuracy
3. Test performance with generated columns
4. Validate JSON queries work correctly

### Phase 5: Migration
1. Re-import all existing data with raw storage enabled
2. Validate data integrity
3. Update documentation

---

## Benefits of This Approach

1. **Preserves Original API Structure**: Complete PayProp API responses stored as-is
2. **Maintains Existing Functionality**: Current processed tables continue working
3. **Enables Deep Analysis**: Can query raw nested JSON data
4. **Performance Optimized**: Generated columns provide fast access to common fields
5. **Data Integrity**: Can validate processed data against raw source
6. **Future-Proof**: Any PayProp API changes preserved in raw format
7. **Debugging Capability**: Full audit trail of what API returned vs what was processed

---

## File Structure to Implement

```
src/main/java/site/easy/to/build/crm/
├── service/payprop/raw/
│   ├── PayPropRawStorageService.java (NEW)
│   ├── PayPropEnhancedAllPaymentsImportService.java (NEW)
│   ├── PayPropEnhancedPropertiesImportService.java (NEW)
│   └── PayPropEnhancedImportOrchestrator.java (NEW)
├── controller/payprop/
│   └── PayPropRawStorageController.java (NEW)
└── config/
    └── PayPropRawStorageConfig.java (NEW - JSON configuration)
```

This specification provides everything needed to implement dual raw+processed storage for PayProp data while maintaining all existing functionality.
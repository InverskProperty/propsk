package site.easy.to.build.crm.service.payprop.raw;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.service.payprop.PayPropApiClient;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * PayProp Raw ICDN Import Service
 * 
 * Imports ICDN (Invoice, Credit, Debit Notes) data from /report/icdn endpoint.
 * This contains the missing payment instruction references that payments need.
 */
@Service
public class PayPropRawIcdnImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawIcdnImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private PayPropImportIssueTracker issueTracker;
    
    /**
     * Import all ICDN records from PayProp /report/icdn endpoint
     * Uses historical chunking to get complete dataset
     */
    @Transactional
    public PayPropRawImportResult importIcdnComplete() {
        log.info("🔄 Starting raw ICDN import from PayProp");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/report/icdn");
        
        try {
            // Fetch ALL ICDN records using historical chunking
            // Add initial query parameter to match pattern used by working all-payments service
            String baseEndpoint = "/report/icdn?rows=25";
                
            log.info("🔄 Starting COMPLETE ICDN import using historical chunking");
            List<Map<String, Object>> icdnRecords = apiClient.fetchHistoricalPages(
                baseEndpoint, 
                2, // 2 years back 
                this::processIcdnItem
            );
            
            result.setTotalFetched(icdnRecords.size());
            log.info("📦 PayProp API returned: {} ICDN records", icdnRecords.size());
            
            // Import to database
            int importedCount = importIcdnToDatabase(icdnRecords);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("ICDN records imported: %d fetched, %d imported", 
                icdnRecords.size(), importedCount));
            
            log.info("✅ Raw ICDN import completed: {} fetched, {} imported", 
                icdnRecords.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Raw ICDN import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    /**
     * Process individual ICDN item from API
     */
    private Map<String, Object> processIcdnItem(Map<String, Object> icdnRecord) {
        log.debug("ICDN ID: {} Amount: {} Type: {} Date: {}", 
            icdnRecord.get("id"), 
            icdnRecord.get("amount"), 
            icdnRecord.get("type"),
            icdnRecord.get("date"));
        return icdnRecord;
    }
    
    /**
     * Import ICDN records to database
     */
    private int importIcdnToDatabase(List<Map<String, Object>> icdnRecords) throws SQLException {
        
        // Clear existing data for fresh import
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_report_icdn")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing ICDN records for fresh import", deletedCount);
            }
        }
        
        if (icdnRecords.isEmpty()) {
            log.warn("No ICDN records to import");
            return 0;
        }
        
        String insertSql = """
            INSERT IGNORE INTO payprop_report_icdn (
                payprop_id, transaction_type, amount, transaction_date, description,
                deposit_id, has_tax, invoice_group_id, matched_amount,
                property_payprop_id, property_name,
                tenant_payprop_id, tenant_name,
                category_payprop_id, category_name,
                imported_at, sync_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        int attemptedCount = 0;
        int actuallyInserted = 0;
        int emptyIdSkipped = 0;
        int mappingErrors = 0;
        
        for (Map<String, Object> record : icdnRecords) {
            try {
                String recordId = getStringValue(record, "id");
                
                // Handle empty/null IDs
                if (recordId == null || recordId.trim().isEmpty()) {
                    emptyIdSkipped++;
                    issueTracker.recordIssue(
                        PayPropImportIssueTracker.EMPTY_ID,
                        "/report/icdn",
                        recordId,
                        record,
                        "PayProp sent ICDN record without ID",
                        PayPropImportIssueTracker.FINANCIAL_DATA_MISSING
                    );
                    continue;
                }
                
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    
                    setIcdnParameters(stmt, record);
                    int insertResult = stmt.executeUpdate();
                    attemptedCount++;
                    
                    if (insertResult > 0) {
                        actuallyInserted++;
                    }
                }
                
            } catch (Exception e) {
                mappingErrors++;
                String recordId = getStringValue(record, "id");
                issueTracker.recordIssue(
                    PayPropImportIssueTracker.MAPPING_ERROR,
                    "/report/icdn",
                    recordId,
                    record,
                    e.getMessage(),
                    PayPropImportIssueTracker.FINANCIAL_DATA_MISSING
                );
                log.error("❌ ICDN MAPPING ERROR for record {}: {}", recordId, e.getMessage());
                log.error("   Record data: type={}, amount={}, date={}, property={}, tenant={}", 
                    getStringValue(record, "type"),
                    getStringValue(record, "amount"), 
                    getStringValue(record, "date"),
                    getNestedObjectField(record, "property", "name"),
                    getNestedObjectField(record, "tenant", "name"));
                log.error("   Full exception:", e);
            }
        }
        
        // Provide accurate summary
        log.info("📊 ACCURATE ICDN IMPORT SUMMARY:");
        log.info("   Total fetched from API: {}", icdnRecords.size());
        log.info("   Skipped (empty ID): {}", emptyIdSkipped);
        log.info("   Mapping errors: {}", mappingErrors);
        log.info("   Attempted to insert: {}", attemptedCount);
        log.info("   Actually inserted: {}", actuallyInserted);
        
        return actuallyInserted;
    }
    
    /**
     * Set parameters for ICDN insert statement
     */
    private void setIcdnParameters(PreparedStatement stmt, Map<String, Object> record) 
            throws SQLException {
        
        int paramIndex = 1;
        
        // Basic ICDN fields matching the actual table structure
        stmt.setString(paramIndex++, getStringValue(record, "id")); // payprop_id
        stmt.setString(paramIndex++, getStringValue(record, "type")); // transaction_type 
        setBigDecimalOrNull(stmt, paramIndex++, getBigDecimalValue(record, "amount")); // amount
        stmt.setDate(paramIndex++, getDateValue(record, "date")); // transaction_date
        stmt.setString(paramIndex++, getStringValue(record, "description")); // description
        stmt.setString(paramIndex++, getStringValue(record, "deposit_id")); // deposit_id
        setBooleanParameter(stmt, paramIndex++, getBooleanValue(record, "has_tax")); // has_tax
        stmt.setString(paramIndex++, getStringValue(record, "invoice_group_id")); // invoice_group_id
        setBigDecimalOrNull(stmt, paramIndex++, getBigDecimalValue(record, "matched_amount")); // matched_amount
        
        // Extract and flatten nested property object
        Map<String, Object> property = getNestedObject(record, "property");
        if (property != null) {
            stmt.setString(paramIndex++, getStringValue(property, "id")); // property_payprop_id
            stmt.setString(paramIndex++, getStringValue(property, "name")); // property_name
        } else {
            stmt.setNull(paramIndex++, java.sql.Types.VARCHAR);
            stmt.setNull(paramIndex++, java.sql.Types.VARCHAR);
        }
        
        // Extract and flatten nested tenant object
        Map<String, Object> tenant = getNestedObject(record, "tenant");
        if (tenant != null) {
            stmt.setString(paramIndex++, getStringValue(tenant, "id")); // tenant_payprop_id
            stmt.setString(paramIndex++, getStringValue(tenant, "name")); // tenant_name
        } else {
            stmt.setNull(paramIndex++, java.sql.Types.VARCHAR);
            stmt.setNull(paramIndex++, java.sql.Types.VARCHAR);
        }
        
        // Extract and flatten nested category object
        Map<String, Object> category = getNestedObject(record, "category");
        if (category != null) {
            stmt.setString(paramIndex++, getStringValue(category, "id")); // category_payprop_id
            stmt.setString(paramIndex++, getStringValue(category, "name")); // category_name
        } else {
            stmt.setNull(paramIndex++, java.sql.Types.VARCHAR);
            stmt.setNull(paramIndex++, java.sql.Types.VARCHAR);
        }
        
        // Meta fields
        stmt.setTimestamp(paramIndex++, Timestamp.valueOf(LocalDateTime.now())); // imported_at
        stmt.setString(paramIndex++, "active"); // sync_status
    }
    
    
    // Helper methods (same as other import services)
    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        return map.get(key).toString();
    }
    
    private BigDecimal getBigDecimalValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        try {
            return new BigDecimal(map.get(key).toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to convert {} to BigDecimal: {}", key, map.get(key));
            return null;
        }
    }
    
    private Boolean getBooleanValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }
    
    private java.sql.Date getDateValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        try {
            String dateStr = map.get(key).toString();
            return java.sql.Date.valueOf(dateStr);
        } catch (Exception e) {
            log.warn("Failed to convert {} to Date: {}", key, map.get(key));
            return null;
        }
    }
    
    private void setBigDecimalOrNull(PreparedStatement stmt, int paramIndex, BigDecimal value) 
            throws SQLException {
        if (value == null) {
            stmt.setNull(paramIndex, java.sql.Types.DECIMAL);
        } else {
            stmt.setBigDecimal(paramIndex, value);
        }
    }
    
    private void setBooleanParameter(PreparedStatement stmt, int paramIndex, Boolean value) 
            throws SQLException {
        if (value == null) {
            stmt.setNull(paramIndex, java.sql.Types.BOOLEAN);
        } else {
            stmt.setBoolean(paramIndex, value);
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getNestedObject(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }
    
    private String getNestedObjectField(Map<String, Object> map, String objectKey, String fieldKey) {
        Map<String, Object> nestedObject = getNestedObject(map, objectKey);
        if (nestedObject == null) {
            return null;
        }
        return getStringValue(nestedObject, fieldKey);
    }
}
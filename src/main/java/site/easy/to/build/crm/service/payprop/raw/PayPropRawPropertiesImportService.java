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
 * PayProp Raw Properties Import Service
 * 
 * Imports raw property data from /export/properties directly into payprop_export_properties table.
 * ZERO BUSINESS LOGIC - stores exactly as PayProp returns.
 * 
 * This service handles the £995 (settings.monthly_payment) import.
 */
@Service
public class PayPropRawPropertiesImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawPropertiesImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    /**
     * Import all properties from PayProp /export/properties endpoint
     * Stores raw data with zero transformation
     */
    @Transactional
    public PayPropRawImportResult importAllProperties() {
        log.info("🔄 Starting raw properties import from PayProp");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/export/properties");
        
        try {
            // Fetch all properties using existing API client with contract amount (monthly rent)
            List<Map<String, Object>> properties = apiClient.fetchAllPages(
                "/export/properties?include_commission=true&include_contract_amount=true&include_balance=true", 
                this::processPropertyItem
            );
            
            // Import to database
            int importedCount = importPropertiesToDatabase(properties);
            
            result.setTotalFetched(properties.size());
            result.setTotalImported(importedCount);
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            
            log.info("✅ Raw properties import completed: {} fetched, {} imported", 
                properties.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Raw properties import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    /**
     * Process a single property item from PayProp API response
     * No transformation - just wrap the raw data
     */
    private Map<String, Object> processPropertyItem(Map<String, Object> rawProperty) {
        // No transformation - return raw data as-is
        // API client will collect all items for batch processing
        return rawProperty;
    }
    
    /**
     * Import properties to payprop_export_properties table
     * Direct SQL insert to preserve exact PayProp structure
     */
    private int importPropertiesToDatabase(List<Map<String, Object>> properties) throws SQLException {
        if (properties.isEmpty()) {
            log.info("No properties to import");
            return 0;
        }
        
        // Clear existing data (full refresh approach)
        clearExistingProperties();
        
        String insertSql = """
            INSERT INTO payprop_export_properties (
                payprop_id, name, description, create_date, modify_date,
                start_date, end_date, property_image,
                address_id, address_first_line, address_second_line, address_third_line,
                address_city, address_state, address_country_code, address_postal_code,
                address_zip_code, address_latitude, address_longitude, address_phone,
                address_fax, address_email, address_created, address_modified,
                settings_monthly_payment, settings_enable_payments, settings_hold_owner_funds,
                settings_verify_payments, settings_minimum_balance, settings_listing_from,
                settings_approval_required,
                commission_percentage, commission_amount, commission_id,
                contract_amount, balance_amount,
                imported_at, last_modified_at, sync_status
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, 'active'
            )
            ON DUPLICATE KEY UPDATE
                name = VALUES(name),
                description = VALUES(description),
                modify_date = VALUES(modify_date),
                settings_monthly_payment = VALUES(settings_monthly_payment),
                contract_amount = VALUES(contract_amount),
                balance_amount = VALUES(balance_amount),
                last_modified_at = VALUES(last_modified_at)
            """;
        
        int importedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (Map<String, Object> property : properties) {
                try {
                    setPropertyParameters(stmt, property);
                    stmt.addBatch();
                    importedCount++;
                    
                    // Execute batch every 25 items (PayProp page size)
                    if (importedCount % 25 == 0) {
                        stmt.executeBatch();
                        log.debug("Imported batch: {} properties processed", importedCount);
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to prepare property for import: {}", 
                        property.get("id"), e);
                }
            }
            
            // Execute remaining batch
            if (importedCount % 25 != 0) {
                stmt.executeBatch();
            }
            
            log.info("✅ Database import completed: {} properties", importedCount);
            
        }
        
        return importedCount;
    }
    
    /**
     * Set parameters for property insert statement
     * Maps PayProp fields to database columns with null safety
     */
    private void setPropertyParameters(PreparedStatement stmt, Map<String, Object> property) 
            throws SQLException {
        
        // Extract nested objects safely
        Map<String, Object> address = getMapValue(property, "address");
        Map<String, Object> settings = getMapValue(property, "settings");
        Map<String, Object> commission = getMapValue(property, "commission");
        
        int paramIndex = 1;
        
        // Root properties
        stmt.setString(paramIndex++, getStringValue(property, "id"));
        stmt.setString(paramIndex++, getStringValue(property, "name"));
        stmt.setString(paramIndex++, getStringValue(property, "description"));
        stmt.setTimestamp(paramIndex++, getTimestampValue(property, "create_date"));
        stmt.setTimestamp(paramIndex++, getTimestampValue(property, "modify_date"));
        stmt.setTimestamp(paramIndex++, getTimestampValue(property, "start_date"));
        stmt.setTimestamp(paramIndex++, getTimestampValue(property, "end_date"));
        stmt.setString(paramIndex++, getStringValue(property, "property_image"));
        
        // Address object (flattened)
        stmt.setString(paramIndex++, getStringValue(address, "id"));
        stmt.setString(paramIndex++, getStringValue(address, "first_line"));
        stmt.setString(paramIndex++, getStringValue(address, "second_line"));
        stmt.setString(paramIndex++, getStringValue(address, "third_line"));
        stmt.setString(paramIndex++, getStringValue(address, "city"));
        stmt.setString(paramIndex++, getStringValue(address, "state"));
        stmt.setString(paramIndex++, getStringValue(address, "country_code"));
        stmt.setString(paramIndex++, getStringValue(address, "postal_code"));
        stmt.setString(paramIndex++, getStringValue(address, "zip_code"));
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(address, "latitude"));
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(address, "longitude"));
        stmt.setString(paramIndex++, getStringValue(address, "phone"));
        stmt.setString(paramIndex++, getStringValue(address, "fax"));
        stmt.setString(paramIndex++, getStringValue(address, "email"));
        stmt.setTimestamp(paramIndex++, getTimestampValue(address, "created"));
        stmt.setTimestamp(paramIndex++, getTimestampValue(address, "modified"));
        
        // Settings object
        // NOTE: PayProp returns monthly_payment_required at root level, NOT in settings
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(property, "monthly_payment_required"));
        setBooleanParameter(stmt, paramIndex++, getBooleanValue(settings, "enable_payments"));
        setBooleanParameter(stmt, paramIndex++, getBooleanValue(settings, "hold_owner_funds"));
        setBooleanParameter(stmt, paramIndex++, getBooleanValue(settings, "verify_payments"));
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(settings, "minimum_balance"));
        stmt.setDate(paramIndex++, getDateValue(settings, "listing_from"));
        setBooleanParameter(stmt, paramIndex++, getBooleanValue(settings, "approval_required"));
        
        // Commission object
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(commission, "percentage"));
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(commission, "amount"));
        stmt.setString(paramIndex++, getStringValue(commission, "id"));

        // Contract and Balance amounts (root level fields)
        // NOTE: contract_amount is the monthly rent returned when include_contract_amount=true
        // NOTE: balance_amount is account balance returned when include_balance=true
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(property, "contract_amount"));
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(property, "balance"));

        // Metadata
        stmt.setTimestamp(paramIndex++, getTimestampValue(property, "modify_date"));
    }
    
    /**
     * Clear existing properties for full refresh
     */
    private void clearExistingProperties() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            // Clear child tables first to avoid foreign key constraint violations
            log.info("Clearing raw import tables for fresh import...");
            
            // Clear all dependent tables first
            String[] childTables = {
                "payprop_export_invoices",
                "payprop_report_all_payments", 
                "payprop_export_payments",
                "payprop_export_beneficiaries",
                "payprop_export_tenants"
            };
            
            for (String table : childTables) {
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM " + table)) {
                    int deletedCount = stmt.executeUpdate();
                    log.debug("Cleared {} records from {}", deletedCount, table);
                }
            }
            
            // Finally clear the parent properties table
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_export_properties")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing properties for fresh import", deletedCount);
            }
            
            log.info("✅ All raw import tables cleared successfully");
        }
    }
    
    // ===== UTILITY METHODS FOR SAFE DATA EXTRACTION =====
    
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
    
    private Timestamp getTimestampValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        try {
            // PayProp typically returns timestamps as strings
            String timestampStr = map.get(key).toString();
            return Timestamp.valueOf(timestampStr.replace("T", " ").replace("Z", ""));
        } catch (Exception e) {
            log.warn("Failed to convert {} to Timestamp: {}", key, map.get(key));
            return null;
        }
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
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getMapValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return Map.of(); // Empty map for null safety
        }
        try {
            return (Map<String, Object>) map.get(key);
        } catch (ClassCastException e) {
            log.warn("Value for key {} is not a Map: {}", key, map.get(key));
            return Map.of();
        }
    }
    
    /**
     * Helper method to safely set boolean parameters, handling null values
     */
    private void setBooleanParameter(PreparedStatement stmt, int paramIndex, Boolean value) 
            throws SQLException {
        if (value == null) {
            stmt.setNull(paramIndex, java.sql.Types.BOOLEAN);
        } else {
            stmt.setBoolean(paramIndex, value);
        }
    }
}
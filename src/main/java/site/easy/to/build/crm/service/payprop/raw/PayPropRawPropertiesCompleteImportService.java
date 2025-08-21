package site.easy.to.build.crm.service.payprop.raw;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.service.payprop.PayPropApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;

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
 * PayProp Raw Properties Complete Import Service
 * 
 * Imports complete properties data from /export/properties into payprop_export_properties table.
 * ZERO BUSINESS LOGIC - stores exactly as PayProp returns with proper nested structure flattening.
 * 
 * Based on proven beneficiaries import service template.
 */
@Service
public class PayPropRawPropertiesCompleteImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawPropertiesCompleteImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Import complete properties data from PayProp /export/properties endpoint
     * Uses proven API patterns from working test controller
     */
    @Transactional
    public PayPropRawImportResult importPropertiesComplete() {
        log.info("🔄 Starting COMPLETE properties import from PayProp");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/export/properties");
        
        try {
            // Use the proven working endpoint configuration from your controller
            String endpoint = "/export/properties?rows=25";
            
            log.info("🔄 Starting properties import using proven working pattern");
            
            // Use fetchAllPages (not fetchHistoricalPages) - this is an export endpoint, not report
            List<Map<String, Object>> properties = apiClient.fetchAllPages(endpoint, 
                (Map<String, Object> item) -> item // Return raw item - no processing
            );
            
            result.setTotalFetched(properties.size());
            log.info("📦 PayProp API returned: {} properties", properties.size());
            
            // Import to new complete database table
            int importedCount = importPropertiesToCompleteTable(properties);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("Complete properties imported: %d fetched, %d imported", 
                properties.size(), importedCount));
            
            log.info("✅ COMPLETE properties import completed: {} fetched, {} imported", 
                properties.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Complete properties import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    /**
     * Import properties to the complete table with proper nested structure flattening
     */
    private int importPropertiesToCompleteTable(List<Map<String, Object>> properties) throws SQLException {
        
        // Clear existing data for fresh import (foreign key constraints will reveal dependency issues)
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_export_properties")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing properties for fresh import", deletedCount);
            } catch (SQLException e) {
                // Log foreign key constraint details to understand dependencies
                log.warn("⚠️ Foreign key constraint during properties delete: {}", e.getMessage());
                log.info("🔍 This reveals dependency: another table references properties");
                throw e; // Re-throw to show the actual constraint issue
            }
        }
        
        if (properties.isEmpty()) {
            log.warn("No properties to import");
            return 0;
        }
        
        String insertSql = """
            INSERT INTO payprop_export_properties (
                payprop_id, name, description, create_date, modify_date, start_date, end_date,
                property_image,
                address_id, address_first_line, address_second_line, address_third_line,
                address_city, address_state, address_country_code, address_postal_code,
                address_zip_code, address_latitude, address_longitude, address_phone,
                address_fax, address_email, address_created, address_modified,
                settings_monthly_payment, settings_enable_payments, settings_hold_owner_funds,
                settings_verify_payments, settings_minimum_balance, settings_listing_from,
                settings_approval_required,
                commission_percentage, commission_amount, commission_id,
                imported_at, sync_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        int importedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (Map<String, Object> property : properties) {
                try {
                    // Extract basic property fields
                    stmt.setString(1, getString(property, "id"));
                    stmt.setString(2, getString(property, "name"));
                    stmt.setString(3, getString(property, "description"));
                    stmt.setTimestamp(4, getTimestamp(property, "create_date"));
                    stmt.setTimestamp(5, getTimestamp(property, "modify_date"));
                    stmt.setTimestamp(6, getTimestamp(property, "start_date"));
                    stmt.setTimestamp(7, getTimestamp(property, "end_date"));
                    stmt.setString(8, getString(property, "property_image"));
                    
                    // Extract and flatten nested address object
                    Map<String, Object> address = getNestedObject(property, "address");
                    if (address != null) {
                        stmt.setString(9, getString(address, "id"));
                        stmt.setString(10, getString(address, "first_line"));
                        stmt.setString(11, getString(address, "second_line"));
                        stmt.setString(12, getString(address, "third_line"));
                        stmt.setString(13, getString(address, "city"));
                        stmt.setString(14, getString(address, "state"));
                        stmt.setString(15, getString(address, "country_code"));
                        stmt.setString(16, getString(address, "postal_code"));
                        stmt.setString(17, getString(address, "zip_code"));
                        setBigDecimalOrNull(stmt, 18, getBigDecimal(address, "latitude"));
                        setBigDecimalOrNull(stmt, 19, getBigDecimal(address, "longitude"));
                        stmt.setString(20, getString(address, "phone"));
                        stmt.setString(21, getString(address, "fax"));
                        stmt.setString(22, getString(address, "email"));
                        stmt.setTimestamp(23, getTimestamp(address, "created"));
                        stmt.setTimestamp(24, getTimestamp(address, "modified"));
                    } else {
                        // Set address fields to null if no address object
                        for (int i = 9; i <= 24; i++) {
                            if (i >= 18 && i <= 19) {
                                stmt.setNull(i, java.sql.Types.DECIMAL); // latitude, longitude
                            } else if (i == 23 || i == 24) {
                                stmt.setNull(i, java.sql.Types.TIMESTAMP); // created, modified
                            } else {
                                stmt.setNull(i, java.sql.Types.VARCHAR);
                            }
                        }
                    }
                    
                    // Extract and flatten nested settings object (CRITICAL - Source of monthly payment!)
                    Map<String, Object> settings = getNestedObject(property, "settings");
                    if (settings != null) {
                        setBigDecimalOrNull(stmt, 25, getBigDecimal(settings, "monthly_payment"));
                        setBooleanOrNull(stmt, 26, getBoolean(settings, "enable_payments"));
                        setBooleanOrNull(stmt, 27, getBoolean(settings, "hold_owner_funds"));
                        setBooleanOrNull(stmt, 28, getBoolean(settings, "verify_payments"));
                        setBigDecimalOrNull(stmt, 29, getBigDecimal(settings, "minimum_balance"));
                        stmt.setDate(30, getDate(settings, "listing_from"));
                        setBooleanOrNull(stmt, 31, getBoolean(settings, "approval_required"));
                    } else {
                        // Set settings fields to null if no settings object
                        stmt.setNull(25, java.sql.Types.DECIMAL); // monthly_payment
                        stmt.setNull(26, java.sql.Types.BOOLEAN); // enable_payments
                        stmt.setNull(27, java.sql.Types.BOOLEAN); // hold_owner_funds
                        stmt.setNull(28, java.sql.Types.BOOLEAN); // verify_payments
                        stmt.setNull(29, java.sql.Types.DECIMAL); // minimum_balance
                        stmt.setNull(30, java.sql.Types.DATE); // listing_from
                        stmt.setNull(31, java.sql.Types.BOOLEAN); // approval_required
                    }
                    
                    // Extract commission information (may not be in API)
                    Map<String, Object> commission = getNestedObject(property, "commission");
                    if (commission != null) {
                        setBigDecimalOrNull(stmt, 32, getBigDecimal(commission, "percentage"));
                        setBigDecimalOrNull(stmt, 33, getBigDecimal(commission, "amount"));
                        stmt.setString(34, getString(commission, "id"));
                    } else {
                        stmt.setNull(32, java.sql.Types.DECIMAL); // percentage
                        stmt.setNull(33, java.sql.Types.DECIMAL); // amount
                        stmt.setNull(34, java.sql.Types.VARCHAR); // id
                    }
                    
                    // Meta fields
                    stmt.setTimestamp(35, Timestamp.valueOf(LocalDateTime.now()));
                    stmt.setString(36, "active");
                    
                    stmt.executeUpdate();
                    importedCount++;
                    
                    // Get the property name for logging
                    String name = getString(property, "name");
                    if (name == null || name.trim().isEmpty()) name = getString(property, "description");
                    if (name == null || name.trim().isEmpty()) name = "Unknown Property";
                    
                    log.debug("✅ Imported property: {} ({})", name, getString(property, "id"));
                        
                } catch (Exception e) {
                    log.error("❌ Failed to import property {}: {}", 
                        getString(property, "id"), e.getMessage());
                    // Continue with next property
                }
            }
        }
        
        log.info("📊 Properties import summary: {} total, {} imported", 
            properties.size(), importedCount);
        
        return importedCount;
    }
    
    // Helper methods for safe data extraction (same as beneficiaries)
    
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
    
    private Boolean getBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }
    
    private BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private java.sql.Date getDate(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        try {
            return java.sql.Date.valueOf(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    private Timestamp getTimestamp(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        try {
            // Handle PayProp timestamp format
            String timeStr = value.toString();
            if (timeStr.contains("T")) {
                LocalDateTime ldt = LocalDateTime.parse(timeStr.substring(0, 19));
                return Timestamp.valueOf(ldt);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getNestedObject(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }
    
    // Safe setter methods for null handling
    
    private void setBooleanOrNull(PreparedStatement stmt, int parameterIndex, Boolean value) throws SQLException {
        if (value != null) {
            stmt.setBoolean(parameterIndex, value);
        } else {
            stmt.setNull(parameterIndex, java.sql.Types.BOOLEAN);
        }
    }
    
    private void setBigDecimalOrNull(PreparedStatement stmt, int parameterIndex, BigDecimal value) throws SQLException {
        if (value != null) {
            stmt.setBigDecimal(parameterIndex, value);
        } else {
            stmt.setNull(parameterIndex, java.sql.Types.DECIMAL);
        }
    }
}
package site.easy.to.build.crm.service.payprop;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PayProp Properties Import to Main Properties Table Service
 * 
 * Imports PayProp property data DIRECTLY into the main 'properties' table.
 * This clears the existing properties and reimports all 352+ properties from PayProp.
 * 
 * Maps PayProp export/properties data to the CRM properties table structure.
 */
@Service
public class PayPropPropertiesImportToMainTableService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropPropertiesImportToMainTableService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    /**
     * Clear and reimport all properties from PayProp to the main properties table
     */
    @Transactional
    public Map<String, Object> clearAndReimportProperties() {
        log.info("🔄 Starting CLEAR AND REIMPORT of properties table from PayProp");
        
        try {
            // Step 1: Clear existing data
            clearExistingProperties();
            
            // Step 2: Import fresh PayProp data
            int importedCount = importPropertiesFromPayProp();
            
            Map<String, Object> result = Map.of(
                "success", true,
                "message", "Properties table cleared and reimported successfully",
                "totalImported", importedCount,
                "timestamp", LocalDateTime.now()
            );
            
            log.info("✅ Properties clear and reimport completed: {} properties", importedCount);
            return result;
            
        } catch (Exception e) {
            log.error("❌ Properties clear and reimport failed", e);
            return Map.of(
                "success", false,
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
        }
    }
    
    /**
     * Clear existing properties (and dependent data) from main properties table
     */
    private void clearExistingProperties() throws SQLException {
        log.info("🧹 Clearing existing properties and dependencies");
        
        try (Connection conn = dataSource.getConnection()) {
            // Clear dependent tables first to avoid foreign key constraints
            
            // Clear property_owners (junction table)
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM property_owners")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} property owner relationships", deletedCount);
            } catch (SQLException e) {
                log.warn("⚠️ Could not clear property_owners: {}", e.getMessage());
            }
            
            // Clear tenants (references properties)
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM tenants")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} tenants", deletedCount);
            } catch (SQLException e) {
                log.warn("⚠️ Could not clear tenants: {}", e.getMessage());
            }
            
            // Clear trigger_ticket references
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE trigger_ticket SET property_id = NULL, tenant_id = NULL")) {
                int updatedCount = stmt.executeUpdate();
                log.info("Cleared property/tenant references from {} trigger tickets", updatedCount);
            } catch (SQLException e) {
                log.warn("⚠️ Could not clear trigger_ticket references: {}", e.getMessage());
            }
            
            // Clear trigger_contract references
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE trigger_contract SET property_id = NULL, tenant_id = NULL")) {
                int updatedCount = stmt.executeUpdate();
                log.info("Cleared property/tenant references from {} trigger contracts", updatedCount);
            } catch (SQLException e) {
                log.warn("⚠️ Could not clear trigger_contract references: {}", e.getMessage());
            }
            
            // Clear file references
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE file SET property_id = NULL, tenant_id = NULL")) {
                int updatedCount = stmt.executeUpdate();
                log.info("Cleared property/tenant references from {} files", updatedCount);
            } catch (SQLException e) {
                log.warn("⚠️ Could not clear file references: {}", e.getMessage());
            }
            
            // Now clear the main properties table
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM properties")) {
                int deletedCount = stmt.executeUpdate();
                log.info("🧹 Cleared {} existing properties from main properties table", deletedCount);
            }
        }
    }
    
    /**
     * Import all properties from PayProp (active + archived)
     */
    private int importPropertiesFromPayProp() throws Exception {
        log.info("📦 Importing properties from PayProp API");
        
        // Step 1: Import active properties
        String activeEndpoint = "/export/properties?rows=25&is_archived=false";
        log.info("📦 Fetching ACTIVE properties from {}", activeEndpoint);
        
        List<Map<String, Object>> activeProperties = apiClient.fetchAllPages(activeEndpoint, 
            (Map<String, Object> item) -> item // Return raw item - no processing
        );
        log.info("📦 PayProp API returned: {} ACTIVE properties", activeProperties.size());
        
        // Step 2: Import archived properties
        String archivedEndpoint = "/export/properties?rows=25&is_archived=true";
        log.info("📦 Fetching ARCHIVED properties from {}", archivedEndpoint);
        
        List<Map<String, Object>> archivedProperties = apiClient.fetchAllPages(archivedEndpoint, 
            (Map<String, Object> item) -> item // Return raw item - no processing
        );
        log.info("📦 PayProp API returned: {} ARCHIVED properties", archivedProperties.size());
        
        // Step 3: Mark archive status and combine lists
        List<Map<String, Object>> allProperties = new ArrayList<>();
        
        // Mark active properties
        for (Map<String, Object> property : activeProperties) {
            property.put("is_archived", "N"); // Use 'N' for main table
        }
        allProperties.addAll(activeProperties);
        
        // Mark archived properties  
        for (Map<String, Object> property : archivedProperties) {
            property.put("is_archived", "Y"); // Use 'Y' for main table
        }
        allProperties.addAll(archivedProperties);
        
        log.info("📦 TOTAL properties to import: {}", allProperties.size());
        
        // Step 4: Import to main properties table
        return importToMainPropertiesTable(allProperties);
    }
    
    /**
     * Import properties to the main properties table
     * Maps PayProp data structure to CRM properties table fields
     */
    private int importToMainPropertiesTable(List<Map<String, Object>> properties) throws SQLException {
        if (properties.isEmpty()) {
            log.warn("No properties to import");
            return 0;
        }
        
        String insertSql = """
            INSERT INTO properties (
                payprop_id, property_name, 
                address_line_1, address_line_2, address_line_3, city, county, postcode, country,
                monthly_payment_required, status, 
                listing_from, listing_to, notes,
                created_at, updated_at, is_archived
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        int importedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (Map<String, Object> property : properties) {
                try {
                    // Map PayProp fields to CRM properties table fields
                    
                    // Basic property info
                    stmt.setString(1, getString(property, "id")); // payprop_id
                    stmt.setString(2, getString(property, "name")); // property_name
                    
                    // Extract and map address from nested address object
                    Map<String, Object> address = getNestedObject(property, "address");
                    if (address != null) {
                        stmt.setString(3, getString(address, "first_line")); // address_line_1
                        stmt.setString(4, getString(address, "second_line")); // address_line_2
                        stmt.setString(5, getString(address, "third_line")); // address_line_3
                        stmt.setString(6, getString(address, "city")); // city
                        stmt.setString(7, getString(address, "state")); // county
                        stmt.setString(8, getString(address, "postal_code")); // postcode
                        stmt.setString(9, getString(address, "country_code")); // country
                    } else {
                        // No address data
                        for (int i = 3; i <= 9; i++) {
                            stmt.setNull(i, java.sql.Types.VARCHAR);
                        }
                    }
                    
                    // Extract monthly payment from nested settings object
                    Map<String, Object> settings = getNestedObject(property, "settings");
                    if (settings != null) {
                        setBigDecimalOrNull(stmt, 10, getBigDecimal(settings, "monthly_payment"));
                    } else {
                        stmt.setNull(10, java.sql.Types.DECIMAL);
                    }
                    
                    // Map archive status to Active/Archived
                    String isArchived = getString(property, "is_archived");
                    String status = "N".equals(isArchived) ? "Active" : "Archived";
                    stmt.setString(11, status);
                    
                    // Extract listing dates from settings
                    if (settings != null) {
                        stmt.setDate(12, getDate(settings, "listing_from"));
                        stmt.setNull(13, java.sql.Types.DATE); // listing_to not in PayProp
                    } else {
                        stmt.setNull(12, java.sql.Types.DATE);
                        stmt.setNull(13, java.sql.Types.DATE);
                    }
                    
                    // Description as notes
                    stmt.setString(14, getString(property, "description"));
                    
                    // Timestamps
                    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                    stmt.setTimestamp(15, now); // created_at
                    stmt.setTimestamp(16, now); // updated_at
                    
                    // Archive status
                    stmt.setString(17, getString(property, "is_archived"));
                    
                    stmt.executeUpdate();
                    importedCount++;
                    
                    // Log progress every 50 properties
                    if (importedCount % 50 == 0) {
                        log.info("📊 Imported {} properties so far...", importedCount);
                    }
                        
                } catch (Exception e) {
                    log.error("❌ Failed to import property {}: {}", 
                        getString(property, "id"), e.getMessage());
                    // Continue with next property
                }
            }
        }
        
        log.info("📊 Properties import to main table complete: {} total, {} imported", 
            properties.size(), importedCount);
        
        return importedCount;
    }
    
    // Helper methods for safe data extraction
    
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
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
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getNestedObject(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }
    
    private void setBigDecimalOrNull(PreparedStatement stmt, int parameterIndex, BigDecimal value) throws SQLException {
        if (value != null) {
            stmt.setBigDecimal(parameterIndex, value);
        } else {
            stmt.setNull(parameterIndex, java.sql.Types.DECIMAL);
        }
    }
}
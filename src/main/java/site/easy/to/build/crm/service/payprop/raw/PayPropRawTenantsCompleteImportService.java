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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PayProp Raw Tenants Complete Import Service
 * 
 * Imports complete tenants data from /export/tenants into new payprop_export_tenants_complete table.
 * ZERO BUSINESS LOGIC - stores exactly as PayProp returns with proper nested structure flattening.
 * 
 * Based on proven beneficiaries import service template.
 */
@Service
public class PayPropRawTenantsCompleteImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawTenantsCompleteImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Import complete tenants data from PayProp /export/tenants endpoint
     * Uses proven API patterns from working test controller
     */
    @Transactional
    public PayPropRawImportResult importTenantsComplete() {
        log.info("🔄 Starting COMPLETE tenants import from PayProp");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/export/tenants");
        
        try {
            // Use the proven working endpoint configuration from your controller
            String endpoint = "/export/tenants?rows=25";
            
            log.info("🔄 Starting tenants import using proven working pattern");
            
            // Use fetchAllPages (not fetchHistoricalPages) - this is an export endpoint, not report
            List<Map<String, Object>> tenants = apiClient.fetchAllPages(endpoint, 
                (Map<String, Object> item) -> item // Return raw item - no processing
            );
            
            result.setTotalFetched(tenants.size());
            log.info("📦 PayProp API returned: {} tenants", tenants.size());
            
            // Import to new complete database table
            int importedCount = importTenantsToCompleteTable(tenants);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("Complete tenants imported: %d fetched, %d imported", 
                tenants.size(), importedCount));
            
            log.info("✅ COMPLETE tenants import completed: {} fetched, {} imported", 
                tenants.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Complete tenants import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    /**
     * Import tenants to the new complete table with proper nested structure flattening
     */
    private int importTenantsToCompleteTable(List<Map<String, Object>> tenants) throws SQLException {
        
        // Track missing property IDs for analysis
        Set<String> missingPropertyIds = new HashSet<>();
        
        // Clear existing data for fresh import (foreign key constraints will reveal dependency issues)
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_export_tenants_complete")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing tenants for fresh import", deletedCount);
            } catch (SQLException e) {
                // Log foreign key constraint details to understand dependencies
                log.warn("⚠️ Foreign key constraint during tenants delete: {}", e.getMessage());
                log.info("🔍 This reveals dependency: another table references tenants");
                throw e; // Re-throw to show the actual constraint issue
            }
        }
        
        if (tenants.isEmpty()) {
            log.warn("No tenants to import");
            return 0;
        }
        
        String insertSql = """
            INSERT INTO payprop_export_tenants_complete (
                payprop_id, first_name, last_name, business_name, display_name,
                email, email_cc, phone, mobile, date_of_birth, id_number, id_type,
                nationality, occupation, employer,
                address_id, address_first_line, address_second_line, address_third_line,
                address_city, address_state, address_country_code, address_postal_code,
                address_zip_code, address_phone, address_email,
                emergency_contact_name, emergency_contact_phone, emergency_contact_relationship,
                current_property_id, current_deposit_id, tenancy_start_date, tenancy_end_date,
                monthly_rent_amount, deposit_amount, notify_email, notify_sms,
                preferred_contact_method, tenant_status, is_active, credit_score,
                reference, comment, properties_json, imported_at, sync_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        int importedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (Map<String, Object> tenant : tenants) {
                try {
                    // Extract basic tenant fields
                    stmt.setString(1, getString(tenant, "id"));
                    stmt.setString(2, getString(tenant, "first_name"));
                    stmt.setString(3, getString(tenant, "last_name"));
                    stmt.setString(4, getString(tenant, "business_name"));
                    stmt.setString(5, getString(tenant, "display_name"));
                    stmt.setString(6, getString(tenant, "email_address"));
                    stmt.setString(7, getString(tenant, "email_cc_address"));
                    stmt.setString(8, getString(tenant, "phone"));
                    stmt.setString(9, getString(tenant, "mobile_number"));
                    stmt.setDate(10, getDate(tenant, "date_of_birth"));
                    stmt.setString(11, getString(tenant, "id_reg_no"));
                    stmt.setString(12, getString(tenant, "id_type"));
                    stmt.setString(13, getString(tenant, "nationality"));
                    stmt.setString(14, getString(tenant, "occupation"));
                    stmt.setString(15, getString(tenant, "employer"));
                    
                    // Extract and flatten nested address object
                    Map<String, Object> address = getNestedObject(tenant, "address");
                    if (address != null) {
                        stmt.setString(16, getString(address, "id"));
                        stmt.setString(17, getString(address, "first_line"));
                        stmt.setString(18, getString(address, "second_line"));
                        stmt.setString(19, getString(address, "third_line"));
                        stmt.setString(20, getString(address, "city"));
                        stmt.setString(21, getString(address, "state"));
                        stmt.setString(22, getString(address, "country_code"));
                        stmt.setString(23, getString(address, "postal_code"));
                        stmt.setString(24, getString(address, "zip_code"));
                        stmt.setString(25, getString(address, "phone"));
                        stmt.setString(26, getString(address, "email"));
                    } else {
                        // Set address fields to null if no address object
                        for (int i = 16; i <= 26; i++) {
                            stmt.setNull(i, java.sql.Types.VARCHAR);
                        }
                    }
                    
                    // Emergency contact (may not be in API - set to null)
                    stmt.setNull(27, java.sql.Types.VARCHAR); // emergency_contact_name
                    stmt.setNull(28, java.sql.Types.VARCHAR); // emergency_contact_phone
                    stmt.setNull(29, java.sql.Types.VARCHAR); // emergency_contact_relationship
                    
                    // Extract tenancy details from properties array
                    List<Map<String, Object>> properties = getPropertiesArray(tenant, "properties");
                    if (properties != null && !properties.isEmpty()) {
                        Map<String, Object> currentProperty = properties.get(0); // Get first/current property
                        
                        stmt.setString(30, getString(currentProperty, "id"));
                        
                        // Extract tenant details from nested tenant object within property
                        Map<String, Object> tenantInfo = getNestedObject(currentProperty, "tenant");
                        if (tenantInfo != null) {
                            stmt.setString(31, getString(tenantInfo, "deposit_id"));
                            stmt.setDate(32, getDate(tenantInfo, "start_date"));
                            stmt.setDate(33, getDate(tenantInfo, "end_date"));
                        } else {
                            stmt.setNull(31, java.sql.Types.VARCHAR);
                            stmt.setNull(32, java.sql.Types.DATE);
                            stmt.setNull(33, java.sql.Types.DATE);
                        }
                        
                        setBigDecimalOrNull(stmt, 34, getBigDecimal(currentProperty, "monthly_payment_required"));
                        setBigDecimalOrNull(stmt, 35, getBigDecimal(currentProperty, "deposit_amount"));
                        
                        // Store full properties array as JSON for complex analysis
                        String propertiesJson = objectMapper.writeValueAsString(properties);
                        stmt.setString(44, propertiesJson); // properties_json is parameter 44
                    } else {
                        // No properties - set tenancy fields to null
                        for (int i = 30; i <= 35; i++) {
                            stmt.setNull(i, i >= 34 ? java.sql.Types.DECIMAL : java.sql.Types.VARCHAR);
                        }
                        stmt.setString(44, null); // properties_json as null
                    }
                    
                    // Notification preferences
                    setBooleanOrNull(stmt, 36, getBoolean(tenant, "notify_email"));
                    setBooleanOrNull(stmt, 37, getBoolean(tenant, "notify_sms"));
                    stmt.setString(38, "email"); // default preferred_contact_method
                    
                    // Status and meta fields  
                    stmt.setString(39, getString(tenant, "status"));
                    stmt.setBoolean(40, true); // is_active - default to true
                    stmt.setNull(41, java.sql.Types.DECIMAL); // credit_score - not in API
                    stmt.setString(42, getString(tenant, "reference"));
                    stmt.setString(43, getString(tenant, "comment")); // comment field
                    // properties_json is set above at parameter 44
                    
                    // Meta fields (imported_at=45, sync_status=46)
                    stmt.setTimestamp(45, Timestamp.valueOf(LocalDateTime.now()));
                    stmt.setString(46, "active");
                    
                    stmt.executeUpdate();
                    importedCount++;
                    
                    // Get the best available name for logging
                    String name = (getString(tenant, "first_name") + " " + getString(tenant, "last_name")).trim();
                    if (name.isEmpty()) name = getString(tenant, "business_name");
                    if (name == null || name.trim().isEmpty()) name = getString(tenant, "display_name");
                    if (name == null || name.trim().isEmpty()) name = getString(tenant, "email_address");
                    if (name == null || name.trim().isEmpty()) name = "Unknown";
                    
                    log.debug("✅ Imported tenant: {} ({})", name, getString(tenant, "id"));
                        
                } catch (Exception e) {
                    String tenantId = getString(tenant, "id");
                    String tenantName = (getString(tenant, "first_name") + " " + getString(tenant, "last_name")).trim();
                    if (tenantName.isEmpty()) tenantName = getString(tenant, "business_name");
                    if (tenantName == null || tenantName.trim().isEmpty()) tenantName = getString(tenant, "display_name");
                    if (tenantName == null || tenantName.trim().isEmpty()) tenantName = getString(tenant, "email_address");
                    if (tenantName == null || tenantName.trim().isEmpty()) tenantName = "Unknown";
                    
                    // If this is a foreign key constraint error, log detailed information
                    if (e.getMessage().contains("fk_tenant_current_property")) {
                        List<Map<String, Object>> properties = getPropertiesArray(tenant, "properties");
                        if (properties != null && !properties.isEmpty()) {
                            Map<String, Object> currentProperty = properties.get(0);
                            String missingPropertyId = getString(currentProperty, "id");
                            
                            log.error("❌ FOREIGN KEY FAILURE - Tenant {} ({}) references missing property: {}", 
                                tenantId, tenantName, missingPropertyId);
                            
                            // Track this missing property ID
                            missingPropertyIds.add(missingPropertyId);
                            
                            // Log full tenant record for analysis
                            try {
                                String tenantJson = objectMapper.writeValueAsString(tenant);
                                log.error("🔍 FULL TENANT RECORD: {}", tenantJson);
                            } catch (Exception jsonError) {
                                log.error("🔍 TENANT BASIC INFO: id={}, name={}, email={}, properties_count={}", 
                                    tenantId, tenantName, getString(tenant, "email_address"), 
                                    properties != null ? properties.size() : 0);
                            }
                            
                            // Log property details from tenant's properties array
                            log.error("🏠 MISSING PROPERTY DETAILS: id={}, name={}, address={}", 
                                missingPropertyId,
                                getString(currentProperty, "name"),
                                getString(currentProperty, "address"));
                        } else {
                            log.error("❌ FOREIGN KEY FAILURE - Tenant {} ({}) has no properties array", 
                                tenantId, tenantName);
                        }
                    } else {
                        log.error("❌ Failed to import tenant {} ({}): {}", 
                            tenantId, tenantName, e.getMessage());
                    }
                    // Continue with next tenant
                }
            }
        }
        
        log.info("📊 Tenants import summary: {} total, {} imported", 
            tenants.size(), importedCount);
            
        // Log missing property analysis
        if (!missingPropertyIds.isEmpty()) {
            log.error("🔍 MISSING PROPERTY ANALYSIS:");
            log.error("   - Total unique missing properties: {}", missingPropertyIds.size());
            log.error("   - Missing property IDs: {}", missingPropertyIds);
            log.error("   - This indicates data inconsistency between /export/properties and /export/tenants endpoints");
        }
        
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
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getNestedObject(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getPropertiesArray(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<Map<String, Object>>) value;
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
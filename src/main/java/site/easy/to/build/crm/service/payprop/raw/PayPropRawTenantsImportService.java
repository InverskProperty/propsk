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
 * PayProp Raw Tenants Import Service
 * 
 * Imports raw tenant data from /export/tenants directly into payprop_export_tenants table.
 * ZERO BUSINESS LOGIC - stores exactly as PayProp returns.
 */
@Service
public class PayPropRawTenantsImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawTenantsImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    @Transactional
    public PayPropRawImportResult importAllTenants() {
        log.info("🔄 Starting raw tenants import from PayProp");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/export/tenants");
        
        try {
            String endpoint = "/export/tenants?include_details=true";
            List<Map<String, Object>> tenants = apiClient.fetchAllPages(endpoint, this::processTenantItem);
            
            result.setTotalFetched(tenants.size());
            log.info("📦 PayProp API returned: {} tenants", tenants.size());
            
            int importedCount = importTenantsToDatabase(tenants);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("Tenants imported: %d fetched, %d imported", 
                tenants.size(), importedCount));
            
            log.info("✅ Raw tenants import completed: {} fetched, {} imported", 
                tenants.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Raw tenants import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    private Map<String, Object> processTenantItem(Map<String, Object> tenant) {
        log.debug("Tenant ID: {} Name: {} Status: {}", 
            tenant.get("id"), 
            tenant.get("name"),
            tenant.get("status"));
        return tenant;
    }
    
    private int importTenantsToDatabase(List<Map<String, Object>> tenants) throws SQLException {
        
        // Clear existing data
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_export_tenants")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing tenants for fresh import", deletedCount);
            }
        }
        
        if (tenants.isEmpty()) {
            return 0;
        }
        
        String insertSql = """
            INSERT INTO payprop_export_tenants (
                payprop_id, name, first_name, last_name, email, phone, mobile,
                date_of_birth, national_id, status, property_payprop_id,
                lease_start_date, lease_end_date, deposit_amount, monthly_rent,
                address_line1, address_line2, address_city, address_state,
                address_country, address_postal_code, emergency_contact_name,
                emergency_contact_phone, created_date, modified_date, import_timestamp
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        int importedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (Map<String, Object> tenant : tenants) {
                try {
                    setTenantParameters(stmt, tenant);
                    stmt.addBatch();
                    importedCount++;
                    
                    if (importedCount % 25 == 0) {
                        stmt.executeBatch();
                    }
                } catch (Exception e) {
                    log.error("Failed to prepare tenant for import: {}", 
                        tenant.get("id"), e);
                }
            }
            
            if (importedCount % 25 != 0) {
                stmt.executeBatch();
            }
            
            log.info("✅ Database import completed: {} tenants", importedCount);
        }
        
        return importedCount;
    }
    
    private void setTenantParameters(PreparedStatement stmt, Map<String, Object> tenant) 
            throws SQLException {
        
        Map<String, Object> address = getMapValue(tenant, "address");
        Map<String, Object> lease = getMapValue(tenant, "lease");
        Map<String, Object> emergencyContact = getMapValue(tenant, "emergency_contact");
        
        int paramIndex = 1;
        
        // Basic tenant info
        stmt.setString(paramIndex++, getStringValue(tenant, "id"));
        stmt.setString(paramIndex++, getStringValue(tenant, "name"));
        stmt.setString(paramIndex++, getStringValue(tenant, "first_name"));
        stmt.setString(paramIndex++, getStringValue(tenant, "last_name"));
        stmt.setString(paramIndex++, getStringValue(tenant, "email"));
        stmt.setString(paramIndex++, getStringValue(tenant, "phone"));
        stmt.setString(paramIndex++, getStringValue(tenant, "mobile"));
        
        // Personal details
        stmt.setDate(paramIndex++, getDateValue(tenant, "date_of_birth"));
        stmt.setString(paramIndex++, getStringValue(tenant, "national_id"));
        stmt.setString(paramIndex++, getStringValue(tenant, "status"));
        stmt.setString(paramIndex++, getStringValue(tenant, "property_id"));
        
        // Lease details
        stmt.setDate(paramIndex++, getDateValue(lease, "start_date"));
        stmt.setDate(paramIndex++, getDateValue(lease, "end_date"));
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(lease, "deposit_amount"));
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(lease, "monthly_rent"));
        
        // Address
        stmt.setString(paramIndex++, getStringValue(address, "line1"));
        stmt.setString(paramIndex++, getStringValue(address, "line2"));
        stmt.setString(paramIndex++, getStringValue(address, "city"));
        stmt.setString(paramIndex++, getStringValue(address, "state"));
        stmt.setString(paramIndex++, getStringValue(address, "country"));
        stmt.setString(paramIndex++, getStringValue(address, "postal_code"));
        
        // Emergency contact
        stmt.setString(paramIndex++, getStringValue(emergencyContact, "name"));
        stmt.setString(paramIndex++, getStringValue(emergencyContact, "phone"));
        
        // Timestamps
        stmt.setTimestamp(paramIndex++, getTimestampValue(tenant, "created"));
        stmt.setTimestamp(paramIndex++, getTimestampValue(tenant, "modified"));
        stmt.setTimestamp(paramIndex++, Timestamp.valueOf(LocalDateTime.now()));
    }
    
    // Helper methods (reused pattern)
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
    
    private java.sql.Date getDateValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        try {
            return java.sql.Date.valueOf(map.get(key).toString());
        } catch (Exception e) {
            log.warn("Failed to convert {} to Date: {}", key, map.get(key));
            return null;
        }
    }
    
    private Timestamp getTimestampValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        try {
            return Timestamp.valueOf(map.get(key).toString());
        } catch (Exception e) {
            log.warn("Failed to convert {} to Timestamp: {}", key, map.get(key));
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getMapValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return Map.of();
        }
        try {
            return (Map<String, Object>) map.get(key);
        } catch (ClassCastException e) {
            return Map.of();
        }
    }
}
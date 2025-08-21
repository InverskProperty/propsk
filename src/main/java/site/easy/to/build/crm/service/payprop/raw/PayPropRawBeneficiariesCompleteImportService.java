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
 * PayProp Raw Beneficiaries Complete Import Service - PROOF OF CONCEPT
 * 
 * Imports complete beneficiaries data from /export/beneficiaries into new payprop_export_beneficiaries_complete table.
 * ZERO BUSINESS LOGIC - stores exactly as PayProp returns with proper nested structure flattening.
 * 
 * This serves as the template for all other complete import services.
 */
@Service
public class PayPropRawBeneficiariesCompleteImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawBeneficiariesCompleteImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Import complete beneficiaries data from PayProp /export/beneficiaries endpoint
     * Uses proven API patterns from working test controller
     */
    @Transactional
    public PayPropRawImportResult importBeneficiariesComplete() {
        log.info("🔄 Starting COMPLETE beneficiaries import from PayProp");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/export/beneficiaries");
        
        try {
            // Use the proven working endpoint configuration from your controller
            String endpoint = "/export/beneficiaries?owners=true&rows=25";
            
            log.info("🔄 Starting beneficiaries import using proven working pattern");
            
            // Use fetchAllPages (not fetchHistoricalPages) - this is an export endpoint, not report
            List<Map<String, Object>> beneficiaries = apiClient.fetchAllPages(endpoint, 
                (Map<String, Object> item) -> item // Return raw item - no processing
            );
            
            result.setTotalFetched(beneficiaries.size());
            log.info("📦 PayProp API returned: {} beneficiaries", beneficiaries.size());
            
            // Import to new complete database table
            int importedCount = importBeneficiariesToCompleteTable(beneficiaries);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("Complete beneficiaries imported: %d fetched, %d imported", 
                beneficiaries.size(), importedCount));
            
            log.info("✅ COMPLETE beneficiaries import completed: {} fetched, {} imported", 
                beneficiaries.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Complete beneficiaries import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    /**
     * Import beneficiaries to the new complete table with proper nested structure flattening
     */
    private int importBeneficiariesToCompleteTable(List<Map<String, Object>> beneficiaries) throws SQLException {
        
        // Clear existing data for fresh import
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_export_beneficiaries_complete")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing beneficiaries for fresh import", deletedCount);
            }
        }
        
        if (beneficiaries.isEmpty()) {
            log.warn("No beneficiaries to import");
            return 0;
        }
        
        String insertSql = """
            INSERT INTO payprop_export_beneficiaries_complete (
                payprop_id, business_name, display_name, first_name, last_name,
                email_address, email_cc_address, mobile_number, reference, comment,
                customer_id, status, date_of_birth, id_reg_no, id_type_id, vat_number,
                notify_email, notify_sms, invoice_lead_days,
                address_id, address_first_line, address_second_line, address_third_line,
                address_city, address_state, address_country_code, address_postal_code,
                address_zip_code, address_latitude, address_longitude, address_phone,
                address_fax, address_email, address_created, address_modified,
                total_properties, total_account_balance, properties_json,
                imported_at, sync_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        int importedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (Map<String, Object> beneficiary : beneficiaries) {
                try {
                    // Extract basic fields
                    stmt.setString(1, getString(beneficiary, "id"));
                    stmt.setString(2, getString(beneficiary, "business_name"));
                    stmt.setString(3, getString(beneficiary, "display_name"));
                    stmt.setString(4, getString(beneficiary, "first_name"));
                    stmt.setString(5, getString(beneficiary, "last_name"));
                    stmt.setString(6, getString(beneficiary, "email_address"));
                    stmt.setString(7, getString(beneficiary, "email_cc_address"));
                    stmt.setString(8, getString(beneficiary, "mobile_number"));
                    stmt.setString(9, getString(beneficiary, "reference"));
                    stmt.setString(10, getString(beneficiary, "comment"));
                    stmt.setString(11, getString(beneficiary, "customer_id"));
                    stmt.setString(12, getString(beneficiary, "status"));
                    stmt.setDate(13, getDate(beneficiary, "date_of_birth"));
                    stmt.setString(14, getString(beneficiary, "id_reg_no"));
                    stmt.setString(15, getString(beneficiary, "id_type_id"));
                    stmt.setString(16, getString(beneficiary, "vat_number"));
                    stmt.setBoolean(17, getBoolean(beneficiary, "notify_email"));
                    stmt.setBoolean(18, getBoolean(beneficiary, "notify_sms"));
                    stmt.setInt(19, getInteger(beneficiary, "invoice_lead_days"));
                    
                    // Extract and flatten nested address object
                    Map<String, Object> address = getNestedObject(beneficiary, "address");
                    if (address != null) {
                        stmt.setString(20, getString(address, "id"));
                        stmt.setString(21, getString(address, "first_line"));
                        stmt.setString(22, getString(address, "second_line"));
                        stmt.setString(23, getString(address, "third_line"));
                        stmt.setString(24, getString(address, "city"));
                        stmt.setString(25, getString(address, "state"));
                        stmt.setString(26, getString(address, "country_code"));
                        stmt.setString(27, getString(address, "postal_code"));
                        stmt.setString(28, getString(address, "zip_code"));
                        stmt.setBigDecimal(29, getBigDecimal(address, "latitude"));
                        stmt.setBigDecimal(30, getBigDecimal(address, "longitude"));
                        stmt.setString(31, getString(address, "phone"));
                        stmt.setString(32, getString(address, "fax"));
                        stmt.setString(33, getString(address, "email"));
                        stmt.setTimestamp(34, getTimestamp(address, "created"));
                        stmt.setTimestamp(35, getTimestamp(address, "modified"));
                    } else {
                        // Set address fields to null if no address object
                        for (int i = 20; i <= 35; i++) {
                            stmt.setNull(i, java.sql.Types.VARCHAR);
                        }
                    }
                    
                    // Extract properties array summary
                    List<Map<String, Object>> properties = getPropertiesArray(beneficiary, "properties");
                    if (properties != null && !properties.isEmpty()) {
                        stmt.setInt(36, properties.size());
                        
                        // Calculate total account balance from all properties
                        BigDecimal totalBalance = properties.stream()
                            .map(prop -> getBigDecimal(prop, "account_balance"))
                            .filter(balance -> balance != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                        stmt.setBigDecimal(37, totalBalance);
                        
                        // Store full properties array as JSON for complex analysis
                        String propertiesJson = objectMapper.writeValueAsString(properties);
                        stmt.setString(38, propertiesJson);
                    } else {
                        stmt.setNull(36, java.sql.Types.INTEGER);
                        stmt.setNull(37, java.sql.Types.DECIMAL);
                        stmt.setNull(38, java.sql.Types.JSON);
                    }
                    
                    // Meta fields
                    stmt.setTimestamp(39, Timestamp.valueOf(LocalDateTime.now()));
                    stmt.setString(40, "active");
                    
                    stmt.executeUpdate();
                    importedCount++;
                    
                    log.debug("✅ Imported beneficiary: {} ({})", 
                        getString(beneficiary, "display_name"), 
                        getString(beneficiary, "id"));
                        
                } catch (Exception e) {
                    log.error("❌ Failed to import beneficiary {}: {}", 
                        getString(beneficiary, "id"), e.getMessage());
                    // Continue with next beneficiary
                }
            }
        }
        
        log.info("📊 Beneficiaries import summary: {} total, {} imported", 
            beneficiaries.size(), importedCount);
        
        return importedCount;
    }
    
    // Helper methods for safe data extraction
    
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
    
    private Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
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
    
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getPropertiesArray(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<Map<String, Object>>) value;
        }
        return null;
    }
}
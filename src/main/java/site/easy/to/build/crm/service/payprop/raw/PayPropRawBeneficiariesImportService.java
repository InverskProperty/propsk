package site.easy.to.build.crm.service.payprop.raw;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.service.payprop.PayPropApiClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * PayProp Raw Beneficiaries Import Service
 * 
 * Imports raw beneficiary data from /export/beneficiaries directly into payprop_export_beneficiaries table.
 * ZERO BUSINESS LOGIC - stores exactly as PayProp returns.
 */
@Service
public class PayPropRawBeneficiariesImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawBeneficiariesImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    @Transactional
    public PayPropRawImportResult importAllBeneficiaries() {
        log.info("🔄 Starting raw beneficiaries import from PayProp");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/export/beneficiaries");
        
        try {
            String endpoint = "/export/beneficiaries?owners=true";
            List<Map<String, Object>> beneficiaries = apiClient.fetchAllPages(endpoint, this::processBeneficiaryItem);
            
            result.setTotalFetched(beneficiaries.size());
            log.info("📦 PayProp API returned: {} beneficiaries", beneficiaries.size());
            
            int importedCount = importBeneficiariesToDatabase(beneficiaries);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("Beneficiaries imported: %d fetched, %d imported", 
                beneficiaries.size(), importedCount));
            
            log.info("✅ Raw beneficiaries import completed: {} fetched, {} imported", 
                beneficiaries.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Raw beneficiaries import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    private Map<String, Object> processBeneficiaryItem(Map<String, Object> beneficiary) {
        log.debug("Beneficiary ID: {} Name: {} Type: {}", 
            beneficiary.get("id"), 
            beneficiary.get("name"),
            beneficiary.get("type"));
        return beneficiary;
    }
    
    private int importBeneficiariesToDatabase(List<Map<String, Object>> beneficiaries) throws SQLException {
        
        // Clear existing data
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_export_beneficiaries")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing beneficiaries for fresh import", deletedCount);
            }
        }
        
        if (beneficiaries.isEmpty()) {
            return 0;
        }
        
        String insertSql = """
            INSERT INTO payprop_export_beneficiaries (
                payprop_id, beneficiary_type, name, email, phone,
                bank_account_name, bank_account_number, bank_sort_code, sync_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        int importedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (Map<String, Object> beneficiary : beneficiaries) {
                try {
                    setBeneficiaryParameters(stmt, beneficiary);
                    stmt.addBatch();
                    importedCount++;
                    
                    if (importedCount % 25 == 0) {
                        stmt.executeBatch();
                    }
                } catch (Exception e) {
                    log.error("Failed to prepare beneficiary for import: {}", 
                        beneficiary.get("id"), e);
                }
            }
            
            if (importedCount % 25 != 0) {
                stmt.executeBatch();
            }
            
            log.info("✅ Database import completed: {} beneficiaries", importedCount);
        }
        
        return importedCount;
    }
    
    private void setBeneficiaryParameters(PreparedStatement stmt, Map<String, Object> beneficiary) 
            throws SQLException {
        
        int paramIndex = 1;
        
        // Map PayProp API fields to actual database columns (9 total parameters)
        stmt.setString(paramIndex++, getStringValue(beneficiary, "id")); // payprop_id
        stmt.setString(paramIndex++, getStringValue(beneficiary, "type")); // beneficiary_type
        stmt.setString(paramIndex++, getStringValue(beneficiary, "name")); // name
        stmt.setString(paramIndex++, getStringValue(beneficiary, "email")); // email
        stmt.setString(paramIndex++, getStringValue(beneficiary, "phone")); // phone
        stmt.setString(paramIndex++, getStringValue(beneficiary, "bank_account_name")); // bank_account_name
        stmt.setString(paramIndex++, getStringValue(beneficiary, "bank_account_number")); // bank_account_number
        stmt.setString(paramIndex++, getStringValue(beneficiary, "bank_sort_code")); // bank_sort_code
        stmt.setString(paramIndex++, "active"); // sync_status (default)
    }
    
    // Helper methods
    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        return map.get(key).toString();
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
    
    private void setBooleanParameter(PreparedStatement stmt, int paramIndex, Boolean value) 
            throws SQLException {
        if (value == null) {
            stmt.setNull(paramIndex, java.sql.Types.BOOLEAN);
        } else {
            stmt.setBoolean(paramIndex, value);
        }
    }
}
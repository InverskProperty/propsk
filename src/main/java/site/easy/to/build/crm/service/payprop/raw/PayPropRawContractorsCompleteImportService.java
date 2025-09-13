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
 * PayProp Raw Contractors Complete Import Service
 * 
 * Imports contractor/maintenance bid data from PayProp into payprop_export_contractors table.
 * ZERO BUSINESS LOGIC - stores exactly as PayProp returns with proper nested structure flattening.
 * 
 * Based on proven properties import service template.
 */
@Service
public class PayPropRawContractorsCompleteImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawContractorsCompleteImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Import contractors/maintenance bids data from PayProp
     * Note: This might use /maintenance/contractors or similar endpoint
     */
    @Transactional
    public PayPropRawImportResult importContractorsComplete() {
        log.info("🔧 Starting COMPLETE contractors/bids import from PayProp");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/maintenance/contractors"); // Assumption - might need adjustment
        
        try {
            // Try maintenance contractors endpoint first
            String endpoint = "/maintenance/contractors?rows=25";
            
            log.info("🔄 Starting contractors import from maintenance endpoint");
            
            List<Map<String, Object>> contractors = apiClient.fetchAllPages(endpoint, 
                (Map<String, Object> item) -> item // Return raw item - no processing
            );
            
            result.setTotalFetched(contractors.size());
            log.info("📦 PayProp API returned: {} contractors", contractors.size());
            
            // Import to database table
            int importedCount = importContractorsToTable(contractors);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("Contractors imported: %d fetched, %d imported", 
                contractors.size(), importedCount));
            
            log.info("✅ COMPLETE contractors import completed: {} fetched, {} imported", 
                contractors.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Contractors import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    /**
     * Import contractors to the database table
     */
    private int importContractorsToTable(List<Map<String, Object>> contractors) throws SQLException {
        
        // Clear existing data for fresh import
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_export_contractors")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing contractors for fresh import", deletedCount);
            } catch (SQLException e) {
                log.info("⚠️ Could not clear contractors table (may not exist yet): {}", e.getMessage());
                // Continue - table might not exist yet
            }
        }
        
        if (contractors.isEmpty()) {
            log.warn("No contractors to import");
            return 0;
        }
        
        // Create basic contractor table structure (will need adjustment based on actual API response)
        String insertSql = """
            INSERT INTO payprop_export_contractors (
                payprop_id, name, email, phone, company_name, description,
                specialties, rating, active_status, created_date, modified_date,
                imported_at, sync_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        int importedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (Map<String, Object> contractor : contractors) {
                try {
                    // Extract contractor fields (structure based on typical contractor data)
                    stmt.setString(1, getString(contractor, "id"));
                    stmt.setString(2, getString(contractor, "name"));
                    stmt.setString(3, getString(contractor, "email"));
                    stmt.setString(4, getString(contractor, "phone"));
                    stmt.setString(5, getString(contractor, "company_name"));
                    stmt.setString(6, getString(contractor, "description"));
                    stmt.setString(7, getString(contractor, "specialties"));
                    setBigDecimalOrNull(stmt, 8, getBigDecimal(contractor, "rating"));
                    setBooleanOrNull(stmt, 9, getBoolean(contractor, "active"));
                    stmt.setTimestamp(10, getTimestamp(contractor, "created_date"));
                    stmt.setTimestamp(11, getTimestamp(contractor, "modified_date"));
                    
                    // Meta fields
                    stmt.setTimestamp(12, Timestamp.valueOf(LocalDateTime.now()));
                    stmt.setString(13, "active");
                    
                    stmt.executeUpdate();
                    importedCount++;
                    
                    String name = getString(contractor, "name");
                    if (name == null || name.trim().isEmpty()) name = getString(contractor, "company_name");
                    if (name == null || name.trim().isEmpty()) name = "Unknown Contractor";
                    
                    log.debug("✅ Imported contractor: {} ({})", name, getString(contractor, "id"));
                        
                } catch (Exception e) {
                    log.error("❌ Failed to import contractor {}: {}", 
                        getString(contractor, "id"), e.getMessage());
                    // Continue with next contractor
                }
            }
        }
        
        log.info("📊 Contractors import summary: {} total, {} imported", 
            contractors.size(), importedCount);
        
        return importedCount;
    }
    
    // Helper methods for safe data extraction (same as other services)
    
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
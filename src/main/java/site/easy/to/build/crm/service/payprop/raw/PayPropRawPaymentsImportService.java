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
 * PayProp Raw Payments Import Service
 * 
 * Imports raw payment distribution data from /export/payments directly into payprop_export_payments table.
 * ZERO BUSINESS LOGIC - stores exactly as PayProp returns.
 * 
 * This contains the payment distribution rules and configurations.
 */
@Service
public class PayPropRawPaymentsImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawPaymentsImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    /**
     * Import all payment distributions from PayProp /export/payments endpoint
     */
    @Transactional
    public PayPropRawImportResult importAllPayments() {
        log.info("🔄 Starting raw payments (distribution) import from PayProp");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/export/payments");
        
        try {
            // Fetch all payment distributions
            String endpoint = "/export/payments?include_details=true";
            List<Map<String, Object>> payments = apiClient.fetchAllPages(endpoint, this::processPaymentItem);
            
            result.setTotalFetched(payments.size());
            log.info("📦 PayProp API returned: {} payment distributions", payments.size());
            
            // Import to database
            int importedCount = importPaymentsToDatabase(payments);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("Payment distributions imported: %d fetched, %d imported", 
                payments.size(), importedCount));
            
            log.info("✅ Raw payments (distribution) import completed: {} fetched, {} imported", 
                payments.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Raw payments (distribution) import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    /**
     * Process individual payment distribution item from API
     */
    private Map<String, Object> processPaymentItem(Map<String, Object> payment) {
        log.debug("Payment Distribution ID: {} Type: {} Amount: {}", 
            payment.get("id"), 
            payment.get("type"),
            payment.get("amount"));
        return payment;
    }
    
    /**
     * Import payment distributions to database with exact PayProp structure
     */
    private int importPaymentsToDatabase(List<Map<String, Object>> payments) throws SQLException {
        
        // Clear existing data for fresh import
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_export_payments")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing payment distributions for fresh import", deletedCount);
            }
        }
        
        if (payments.isEmpty()) {
            log.warn("No payment distributions to import");
            return 0;
        }
        
        String insertSql = """
            INSERT INTO payprop_export_payments (
                payprop_id, payment_type, description, amount, percentage, 
                minimum_amount, maximum_amount, priority, active,
                property_payprop_id, beneficiary_payprop_id, category,
                frequency, payment_day, payment_method, currency,
                created_date, modified_date, import_timestamp
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        int importedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (Map<String, Object> payment : payments) {
                try {
                    setPaymentParameters(stmt, payment);
                    stmt.addBatch();
                    importedCount++;
                    
                    // Execute batch every 25 items
                    if (importedCount % 25 == 0) {
                        stmt.executeBatch();
                        log.debug("Imported batch: {} payment distributions processed", importedCount);
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to prepare payment distribution for import: {}", 
                        payment.get("id"), e);
                }
            }
            
            // Execute remaining batch
            if (importedCount % 25 != 0) {
                stmt.executeBatch();
            }
            
            log.info("✅ Database import completed: {} payment distributions", importedCount);
        }
        
        return importedCount;
    }
    
    /**
     * Set parameters for payment distribution insert statement
     */
    private void setPaymentParameters(PreparedStatement stmt, Map<String, Object> payment) 
            throws SQLException {
        
        int paramIndex = 1;
        
        // Core payment distribution fields
        stmt.setString(paramIndex++, getStringValue(payment, "id"));
        stmt.setString(paramIndex++, getStringValue(payment, "type"));
        stmt.setString(paramIndex++, getStringValue(payment, "description"));
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(payment, "amount"));
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(payment, "percentage"));
        
        // Amount constraints
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(payment, "minimum_amount"));
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(payment, "maximum_amount"));
        stmt.setInt(paramIndex++, getIntValue(payment, "priority"));
        setBooleanParameter(stmt, paramIndex++, getBooleanValue(payment, "active"));
        
        // Foreign key relationships
        stmt.setString(paramIndex++, getStringValue(payment, "property_id"));
        stmt.setString(paramIndex++, getStringValue(payment, "beneficiary_id"));
        stmt.setString(paramIndex++, getStringValue(payment, "category"));
        
        // Payment scheduling
        stmt.setString(paramIndex++, getStringValue(payment, "frequency"));
        stmt.setInt(paramIndex++, getIntValue(payment, "payment_day"));
        stmt.setString(paramIndex++, getStringValue(payment, "payment_method"));
        stmt.setString(paramIndex++, getStringValue(payment, "currency"));
        
        // Timestamps
        stmt.setTimestamp(paramIndex++, getTimestampValue(payment, "created"));
        stmt.setTimestamp(paramIndex++, getTimestampValue(payment, "modified"));
        stmt.setTimestamp(paramIndex++, Timestamp.valueOf(LocalDateTime.now()));
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
    
    private Integer getIntValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        try {
            return Integer.parseInt(map.get(key).toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to convert {} to Integer: {}", key, map.get(key));
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
            String timestampStr = map.get(key).toString();
            return Timestamp.valueOf(timestampStr);
        } catch (Exception e) {
            log.warn("Failed to convert {} to Timestamp: {}", key, map.get(key));
            return null;
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
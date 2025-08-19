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
            // Fetch all payment distributions using same parameters as working system
            String endpoint = "/export/payments?include_beneficiary_info=true";
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
                payprop_id, beneficiary, beneficiary_reference, category, category_payprop_id,
                description, enabled, frequency, frequency_code, from_date, to_date,
                gross_amount, gross_percentage, group_id, maintenance_ticket_id,
                no_commission, no_commission_amount, payment_day, reference,
                vat, vat_amount, property_payprop_id, tenant_payprop_id,
                property_name, tenant_name, sync_status, rule_priority
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
        
        // Extract nested objects safely (like invoices service does)
        Map<String, Object> property = getMapValue(payment, "property");
        Map<String, Object> tenant = getMapValue(payment, "tenant");
        Map<String, Object> category = getMapValue(payment, "category");
        
        int paramIndex = 1;
        
        // Map PayProp API fields to actual database columns (27 total parameters)
        stmt.setString(paramIndex++, getStringValue(payment, "id")); // payprop_id
        stmt.setString(paramIndex++, getStringValue(payment, "beneficiary")); // beneficiary
        stmt.setString(paramIndex++, getStringValue(payment, "beneficiary_reference")); // beneficiary_reference
        stmt.setString(paramIndex++, getStringValue(payment, "category")); // category
        stmt.setString(paramIndex++, getStringValue(category, "id")); // category_payprop_id (from nested object)
        stmt.setString(paramIndex++, getStringValue(payment, "description")); // description
        setBooleanParameter(stmt, paramIndex++, getBooleanValue(payment, "enabled")); // enabled
        stmt.setString(paramIndex++, getStringValue(payment, "frequency")); // frequency
        stmt.setString(paramIndex++, getStringValue(payment, "frequency_code")); // frequency_code
        stmt.setDate(paramIndex++, getDateValue(payment, "from_date")); // from_date
        stmt.setDate(paramIndex++, getDateValue(payment, "to_date")); // to_date
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(payment, "gross_amount")); // gross_amount
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(payment, "gross_percentage")); // gross_percentage
        stmt.setString(paramIndex++, getStringValue(payment, "group_id")); // group_id
        stmt.setString(paramIndex++, getStringValue(payment, "maintenance_ticket_id")); // maintenance_ticket_id
        setBooleanParameter(stmt, paramIndex++, getBooleanValue(payment, "no_commission")); // no_commission
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(payment, "no_commission_amount")); // no_commission_amount
        setIntegerParameter(stmt, paramIndex++, getIntegerValue(payment, "payment_day")); // payment_day
        stmt.setString(paramIndex++, getStringValue(payment, "reference")); // reference
        setBooleanParameter(stmt, paramIndex++, getBooleanValue(payment, "vat")); // vat
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(payment, "vat_amount")); // vat_amount
        stmt.setString(paramIndex++, getStringValue(property, "id")); // property_payprop_id (from nested object)
        stmt.setString(paramIndex++, getStringValue(tenant, "id")); // tenant_payprop_id (from nested object)
        stmt.setString(paramIndex++, getStringValue(property, "name")); // property_name (from nested object)
        stmt.setString(paramIndex++, getStringValue(tenant, "display_name")); // tenant_name (from nested object)
        stmt.setString(paramIndex++, "active"); // sync_status (default)
        setIntegerParameter(stmt, paramIndex++, getIntegerValue(payment, "rule_priority")); // rule_priority
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
    
    private Integer getIntegerValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        try {
            // Handle decimal values that should be integers (e.g., 0.0 -> 0)
            String valueStr = map.get(key).toString();
            if (valueStr.contains(".")) {
                return Integer.valueOf(Double.valueOf(valueStr).intValue());
            }
            return Integer.valueOf(valueStr);
        } catch (NumberFormatException e) {
            log.warn("Failed to convert {} to Integer: {}", key, map.get(key));
            return null;
        }
    }
    
    private void setIntegerParameter(PreparedStatement stmt, int paramIndex, Integer value) 
            throws SQLException {
        if (value == null) {
            stmt.setNull(paramIndex, java.sql.Types.INTEGER);
        } else {
            stmt.setInt(paramIndex, value);
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
}
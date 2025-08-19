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
 * PayProp Raw All Payments Import Service
 * 
 * Imports raw payment transaction data from /report/all-payments directly into payprop_report_all_payments table.
 * ZERO BUSINESS LOGIC - stores exactly as PayProp returns.
 * 
 * This contains the actual payment flows - incoming payments, commissions, distributions.
 * CRITICAL: This is where the missing £1,100 payment should be found!
 */
@Service
public class PayPropRawAllPaymentsImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawAllPaymentsImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    /**
     * Import all payment transactions from PayProp /report/all-payments endpoint
     * Uses 93-day filter to get recent payments
     */
    @Transactional
    public PayPropRawImportResult importAllPayments() {
        log.info("🔄 Starting raw all-payments import from PayProp");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/report/all-payments");
        
        try {
            // Fetch all payment transactions using same parameters as working system
            // Use last 93 days with proper date formatting
            LocalDateTime endDateTime = LocalDateTime.now();
            LocalDateTime startDateTime = endDateTime.minusDays(93);
            
            String endpoint = "/report/all-payments" +
                "?from_date=" + startDateTime.toLocalDate().toString() +
                "&to_date=" + endDateTime.toLocalDate().toString() +
                "&filter_by=reconciliation_date" +
                "&include_beneficiary_info=true";
                
            log.info("📡 Using endpoint: {}", endpoint);
            List<Map<String, Object>> payments = apiClient.fetchAllPages(endpoint, this::processPaymentItem);
            
            result.setTotalFetched(payments.size());
            log.info("📦 PayProp API returned: {} payment transactions", payments.size());
            
            // Import to database
            int importedCount = importPaymentsToDatabase(payments);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("All payments imported: %d fetched, %d imported", 
                payments.size(), importedCount));
            
            log.info("✅ Raw all-payments import completed: {} fetched, {} imported", 
                payments.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Raw all-payments import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    /**
     * Process individual payment item from API
     */
    private Map<String, Object> processPaymentItem(Map<String, Object> payment) {
        log.debug("Payment ID: {} Amount: {} Type: {} Status: {}", 
            payment.get("id"), 
            payment.get("amount"), 
            payment.get("type"),
            payment.get("status"));
        return payment;
    }
    
    /**
     * Import payments to database with exact PayProp structure
     */
    private int importPaymentsToDatabase(List<Map<String, Object>> payments) throws SQLException {
        
        // Clear existing data for fresh import
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_report_all_payments")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing payment transactions for fresh import", deletedCount);
            }
        }
        
        if (payments.isEmpty()) {
            log.warn("No payment transactions to import");
            return 0;
        }
        
        String insertSql = """
            INSERT INTO payprop_report_all_payments (
                payprop_id, payment_date, payment_type, tenant_name, beneficiary_name,
                amount, transaction_fee, service_fee, commission_amount, net_amount,
                status, category, source, property_payprop_id, tenant_payprop_id,
                beneficiary_payprop_id, reference, description, currency, reconciled,
                bank_reference, payment_method, created_date, modified_date, import_timestamp
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                        log.debug("Imported batch: {} payments processed", importedCount);
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to prepare payment for import: {}", 
                        payment.get("id"), e);
                }
            }
            
            // Execute remaining batch
            if (importedCount % 25 != 0) {
                stmt.executeBatch();
            }
            
            log.info("✅ Database import completed: {} payment transactions", importedCount);
        }
        
        return importedCount;
    }
    
    /**
     * Set parameters for payment insert statement
     */
    private void setPaymentParameters(PreparedStatement stmt, Map<String, Object> payment) 
            throws SQLException {
        
        int paramIndex = 1;
        
        // Core payment fields
        stmt.setString(paramIndex++, getStringValue(payment, "id"));
        stmt.setDate(paramIndex++, getDateValue(payment, "payment_date"));
        stmt.setString(paramIndex++, getStringValue(payment, "type"));
        stmt.setString(paramIndex++, getStringValue(payment, "tenant_name"));
        stmt.setString(paramIndex++, getStringValue(payment, "beneficiary_name"));
        
        // Financial amounts
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(payment, "amount"));
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(payment, "transaction_fee"));
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(payment, "service_fee"));
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(payment, "commission_amount"));
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(payment, "net_amount"));
        
        // Status and categorization
        stmt.setString(paramIndex++, getStringValue(payment, "status"));
        stmt.setString(paramIndex++, getStringValue(payment, "category"));
        stmt.setString(paramIndex++, getStringValue(payment, "source"));
        
        // Foreign key relationships
        stmt.setString(paramIndex++, getStringValue(payment, "property_id"));
        stmt.setString(paramIndex++, getStringValue(payment, "tenant_id"));
        stmt.setString(paramIndex++, getStringValue(payment, "beneficiary_id"));
        
        // Additional details
        stmt.setString(paramIndex++, getStringValue(payment, "reference"));
        stmt.setString(paramIndex++, getStringValue(payment, "description"));
        stmt.setString(paramIndex++, getStringValue(payment, "currency"));
        setBooleanParameter(stmt, paramIndex++, getBooleanValue(payment, "reconciled"));
        stmt.setString(paramIndex++, getStringValue(payment, "bank_reference"));
        stmt.setString(paramIndex++, getStringValue(payment, "payment_method"));
        
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
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
 * PayProp Raw Payment Rules Complete Import Service
 * 
 * Imports payment distribution rules from /export/payments into payprop_export_payments table.
 * This is NOT transaction data - it's payment distribution rules/configuration.
 * For actual transaction data, use /report/all-payments endpoint.
 * 
 * ZERO BUSINESS LOGIC - stores exactly as PayProp returns with proper nested structure flattening.
 */
@Service
public class PayPropRawPaymentsCompleteImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawPaymentsCompleteImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Import complete payments data from PayProp /export/payments endpoint
     * Source of actual payment transactions and amounts
     */
    @Transactional
    public PayPropRawImportResult importPaymentsComplete() {
        log.info("🔄 Starting COMPLETE payments import from PayProp - Transaction data");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/export/payments");
        
        try {
            // Use the proven working endpoint configuration
            String endpoint = "/export/payments?rows=25";
            
            log.info("🔄 Starting payments import using proven working pattern");
            
            // Use fetchAllPages (not fetchHistoricalPages) - this is an export endpoint, not report
            List<Map<String, Object>> payments = apiClient.fetchAllPages(endpoint, 
                (Map<String, Object> item) -> item // Return raw item - no processing
            );
            
            result.setTotalFetched(payments.size());
            log.info("📦 PayProp API returned: {} payments", payments.size());
            
            // Import to complete database table
            int importedCount = importPaymentsToCompleteTable(payments);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("Complete payments imported: %d fetched, %d imported", 
                payments.size(), importedCount));
            
            log.info("✅ COMPLETE payments import completed: {} fetched, {} imported", 
                payments.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Complete payments import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    /**
     * Import payments to the complete table with proper nested structure flattening
     */
    private int importPaymentsToCompleteTable(List<Map<String, Object>> payments) throws SQLException {
        
        // Clear existing data for fresh import (foreign key constraints will reveal dependency issues)
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_export_payments")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing payments for fresh import", deletedCount);
            } catch (SQLException e) {
                // Log foreign key constraint details to understand dependencies
                log.warn("⚠️ Foreign key constraint during payments delete: {}", e.getMessage());
                log.info("🔍 This reveals dependency: another table references payments");
                throw e; // Re-throw to discover more dependencies
            }
        }
        
        if (payments.isEmpty()) {
            log.warn("No payments to import");
            return 0;
        }
        
        String insertSql = """
            INSERT INTO payprop_export_payments (
                payprop_id, beneficiary, beneficiary_reference, category, description, 
                enabled, frequency, frequency_code, from_date, to_date, 
                gross_amount, gross_percentage, payment_day, reference, vat, 
                vat_amount, vat_percentage, property_payprop_id, property_name, 
                category_payprop_id, category_name, 
                imported_at, sync_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        int importedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (Map<String, Object> payment : payments) {
                try {
                    // Extract basic payment distribution rule fields
                    stmt.setString(1, getString(payment, "id")); // payprop_id
                    stmt.setString(2, getString(payment, "beneficiary")); // beneficiary
                    stmt.setString(3, getString(payment, "beneficiary_reference")); // beneficiary_reference
                    stmt.setString(4, getString(payment, "category")); // category
                    stmt.setString(5, getString(payment, "description")); // description
                    
                    // Boolean fields with safe conversion
                    Object enabledObj = payment.get("enabled");
                    stmt.setBoolean(6, enabledObj != null && Boolean.parseBoolean(enabledObj.toString())); // enabled
                    
                    stmt.setString(7, getString(payment, "frequency")); // frequency
                    stmt.setString(8, getString(payment, "frequency_code")); // frequency_code
                    stmt.setDate(9, getDate(payment, "from_date")); // from_date
                    stmt.setDate(10, getDate(payment, "to_date")); // to_date
                    
                    // Financial fields
                    setBigDecimalOrNull(stmt, 11, getBigDecimal(payment, "gross_amount")); // gross_amount
                    setBigDecimalOrNull(stmt, 12, getBigDecimal(payment, "gross_percentage")); // gross_percentage
                    
                    // Payment day as integer
                    Object paymentDayObj = payment.get("payment_day");
                    if (paymentDayObj != null) {
                        try {
                            stmt.setInt(13, Integer.parseInt(paymentDayObj.toString())); // payment_day
                        } catch (NumberFormatException e) {
                            stmt.setNull(13, java.sql.Types.INTEGER);
                        }
                    } else {
                        stmt.setNull(13, java.sql.Types.INTEGER);
                    }
                    
                    stmt.setString(14, getString(payment, "reference")); // reference
                    
                    // VAT fields
                    Object vatObj = payment.get("vat");
                    stmt.setBoolean(15, vatObj != null && Boolean.parseBoolean(vatObj.toString())); // vat
                    setBigDecimalOrNull(stmt, 16, getBigDecimal(payment, "vat_amount")); // vat_amount
                    setBigDecimalOrNull(stmt, 17, getBigDecimal(payment, "vat_percentage")); // vat_percentage
                    
                    // Extract and flatten nested property object
                    Map<String, Object> property = getNestedObject(payment, "property");
                    if (property != null) {
                        stmt.setString(18, getString(property, "id")); // property_payprop_id
                        stmt.setString(19, getString(property, "name")); // property_name
                    } else {
                        stmt.setNull(18, java.sql.Types.VARCHAR);
                        stmt.setNull(19, java.sql.Types.VARCHAR);
                    }
                    
                    // Extract and flatten nested category object
                    Map<String, Object> categoryObj = getNestedObject(payment, "category");
                    if (categoryObj != null) {
                        stmt.setString(20, getString(categoryObj, "id")); // category_payprop_id
                        stmt.setString(21, getString(categoryObj, "name")); // category_name
                    } else {
                        stmt.setNull(20, java.sql.Types.VARCHAR);
                        stmt.setNull(21, java.sql.Types.VARCHAR);
                    }
                    
                    // Meta fields
                    stmt.setTimestamp(22, Timestamp.valueOf(LocalDateTime.now())); // imported_at
                    stmt.setString(23, "active"); // sync_status
                    
                    stmt.executeUpdate();
                    importedCount++;
                    
                    // Get the payment distribution rule details for logging
                    String description = getString(payment, "description");
                    if (description == null || description.trim().isEmpty()) description = getString(payment, "reference");
                    if (description == null || description.trim().isEmpty()) description = getString(payment, "beneficiary");
                    if (description == null || description.trim().isEmpty()) description = "Unknown Payment Rule";
                    
                    // Log the payment rule with enhanced details
                    BigDecimal grossAmount = getBigDecimal(payment, "gross_amount");
                    BigDecimal grossPercentage = getBigDecimal(payment, "gross_percentage");
                    String beneficiary = getString(payment, "beneficiary");
                    String category = getString(payment, "category");
                    String propertyName = property != null ? getString(property, "name") : "No Property";
                    
                    log.debug("✅ Imported payment rule: {} | Beneficiary: {} | Category: {} | Amount: £{} | Percentage: {}% | Property: {} | ID: {}", 
                        description, beneficiary, category, grossAmount, grossPercentage, propertyName, getString(payment, "id"));
                        
                } catch (Exception e) {
                    log.error("❌ Failed to import payment {}: {}", 
                        getString(payment, "id"), e.getMessage());
                    // Continue with next payment
                }
            }
        }
        
        log.info("📊 Payment distribution rules import summary: {} total, {} imported", 
            payments.size(), importedCount);
        
        return importedCount;
    }
    
    // Helper methods for safe data extraction (same as other services)
    
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
    
    // Safe setter methods for null handling
    
    private void setBigDecimalOrNull(PreparedStatement stmt, int parameterIndex, BigDecimal value) throws SQLException {
        if (value != null) {
            stmt.setBigDecimal(parameterIndex, value);
        } else {
            stmt.setNull(parameterIndex, java.sql.Types.DECIMAL);
        }
    }
}
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
 * PayProp Raw Payments Complete Import Service
 * 
 * Imports complete payments data from /export/payments into payprop_export_payments table.
 * ZERO BUSINESS LOGIC - stores exactly as PayProp returns with proper nested structure flattening.
 * 
 * Based on proven invoices import service template.
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
                payprop_id, payment_date, reconciliation_date, remittance_date, amount, 
                balance, reference, description, payment_type, reconciliation_category,
                property_payprop_id, tenant_payprop_id, category_payprop_id,
                property_name, tenant_display_name, tenant_email, tenant_business_name,
                tenant_first_name, tenant_last_name, category_name,
                imported_at, sync_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        int importedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (Map<String, Object> payment : payments) {
                try {
                    // Extract basic payment fields
                    stmt.setString(1, getString(payment, "id"));
                    stmt.setDate(2, getDate(payment, "payment_date"));
                    stmt.setDate(3, getDate(payment, "reconciliation_date"));
                    stmt.setDate(4, getDate(payment, "remittance_date"));
                    setBigDecimalOrNull(stmt, 5, getBigDecimal(payment, "amount"));
                    setBigDecimalOrNull(stmt, 6, getBigDecimal(payment, "balance"));
                    stmt.setString(7, getString(payment, "reference"));
                    stmt.setString(8, getString(payment, "description"));
                    stmt.setString(9, getString(payment, "payment_type"));
                    stmt.setString(10, getString(payment, "reconciliation_category"));
                    
                    // Extract and flatten nested property object
                    Map<String, Object> property = getNestedObject(payment, "property");
                    if (property != null) {
                        stmt.setString(11, getString(property, "id")); // property_payprop_id
                        stmt.setString(14, getString(property, "name")); // property_name
                    } else {
                        stmt.setNull(11, java.sql.Types.VARCHAR);
                        stmt.setNull(14, java.sql.Types.VARCHAR);
                    }
                    
                    // Extract and flatten nested tenant object  
                    Map<String, Object> tenant = getNestedObject(payment, "tenant");
                    if (tenant != null) {
                        stmt.setString(12, getString(tenant, "id")); // tenant_payprop_id
                        stmt.setString(15, getString(tenant, "display_name")); // tenant_display_name
                        stmt.setString(16, getString(tenant, "email")); // tenant_email
                        stmt.setString(17, getString(tenant, "business_name")); // tenant_business_name
                        stmt.setString(18, getString(tenant, "first_name")); // tenant_first_name
                        stmt.setString(19, getString(tenant, "last_name")); // tenant_last_name
                    } else {
                        stmt.setNull(12, java.sql.Types.VARCHAR);
                        stmt.setNull(15, java.sql.Types.VARCHAR);
                        stmt.setNull(16, java.sql.Types.VARCHAR);
                        stmt.setNull(17, java.sql.Types.VARCHAR);
                        stmt.setNull(18, java.sql.Types.VARCHAR);
                        stmt.setNull(19, java.sql.Types.VARCHAR);
                    }
                    
                    // Extract and flatten nested category object
                    Map<String, Object> category = getNestedObject(payment, "category");
                    if (category != null) {
                        stmt.setString(13, getString(category, "id")); // category_payprop_id
                        stmt.setString(20, getString(category, "name")); // category_name
                    } else {
                        stmt.setNull(13, java.sql.Types.VARCHAR);
                        stmt.setNull(20, java.sql.Types.VARCHAR);
                    }
                    
                    // Meta fields
                    stmt.setTimestamp(21, Timestamp.valueOf(LocalDateTime.now()));
                    stmt.setString(22, "active");
                    
                    stmt.executeUpdate();
                    importedCount++;
                    
                    // Get the payment description for logging
                    String description = getString(payment, "description");
                    if (description == null || description.trim().isEmpty()) description = getString(payment, "reference");
                    if (description == null || description.trim().isEmpty()) description = getString(payment, "payment_type");
                    if (description == null || description.trim().isEmpty()) description = "Unknown Payment";
                    
                    // Log the payment amount with enhanced details
                    BigDecimal amount = getBigDecimal(payment, "amount");
                    String paymentType = getString(payment, "payment_type");
                    String propertyName = property != null ? getString(property, "name") : "No Property";
                    String tenantName = tenant != null ? getString(tenant, "display_name") : "No Tenant";
                    
                    log.debug("✅ Imported payment: {} | Type: {} | £{} | Property: {} | Tenant: {} | ID: {}", 
                        description, paymentType, amount, propertyName, tenantName, getString(payment, "id"));
                        
                } catch (Exception e) {
                    log.error("❌ Failed to import payment {}: {}", 
                        getString(payment, "id"), e.getMessage());
                    // Continue with next payment
                }
            }
        }
        
        log.info("📊 Payments import summary: {} total, {} imported", 
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
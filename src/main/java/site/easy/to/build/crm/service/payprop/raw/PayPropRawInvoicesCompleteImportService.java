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
 * PayProp Raw Invoices Complete Import Service
 * 
 * Imports complete invoices data from /export/invoices into payprop_export_invoices table.
 * ZERO BUSINESS LOGIC - stores exactly as PayProp returns with proper nested structure flattening.
 * 
 * This is the CRITICAL MISSING DATA - source of £1,075 rent amount discrepancy.
 * Based on proven beneficiaries/properties import service template.
 */
@Service
public class PayPropRawInvoicesCompleteImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawInvoicesCompleteImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Import complete invoices data from PayProp /export/invoices endpoint
     * This is the missing £1,075 data that resolves rent amount discrepancies
     */
    @Transactional
    public PayPropRawImportResult importInvoicesComplete() {
        log.info("🔄 Starting COMPLETE invoices import from PayProp - CRITICAL MISSING DATA");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/export/invoices");
        
        try {
            // Use the proven working endpoint configuration
            String endpoint = "/export/invoices?rows=25";
            
            log.info("🔄 Starting invoices import using proven working pattern");
            
            // Use fetchAllPages (not fetchHistoricalPages) - this is an export endpoint, not report
            List<Map<String, Object>> invoices = apiClient.fetchAllPages(endpoint, 
                (Map<String, Object> item) -> item // Return raw item - no processing
            );
            
            result.setTotalFetched(invoices.size());
            log.info("📦 PayProp API returned: {} invoices", invoices.size());
            
            // Import to complete database table
            int importedCount = importInvoicesToCompleteTable(invoices);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("Complete invoices imported: %d fetched, %d imported", 
                invoices.size(), importedCount));
            
            log.info("✅ COMPLETE invoices import completed: {} fetched, {} imported", 
                invoices.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Complete invoices import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    /**
     * Import invoices to the complete table with proper nested structure flattening
     */
    private int importInvoicesToCompleteTable(List<Map<String, Object>> invoices) throws SQLException {
        
        // Clear existing data for fresh import (foreign key constraints will reveal dependency issues)
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_export_invoices")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing invoices for fresh import", deletedCount);
            } catch (SQLException e) {
                // Log foreign key constraint details to understand dependencies
                log.warn("⚠️ Foreign key constraint during invoices delete: {}", e.getMessage());
                log.info("🔍 This reveals dependency: another table references invoices");
                throw e; // Re-throw to discover more dependencies
            }
        }
        
        if (invoices.isEmpty()) {
            log.warn("No invoices to import");
            return 0;
        }
        
        String insertSql = """
            INSERT INTO payprop_export_invoices (
                payprop_id, account_type, debit_order, description, frequency, frequency_code,
                from_date, to_date, gross_amount, payment_day, invoice_type, reference,
                vat, vat_amount,
                property_payprop_id, tenant_payprop_id, category_payprop_id,
                property_name, tenant_display_name, tenant_email, tenant_business_name,
                tenant_first_name, tenant_last_name, category_name,
                imported_at, sync_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        int importedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (Map<String, Object> invoice : invoices) {
                try {
                    // Extract basic invoice fields
                    stmt.setString(1, getString(invoice, "id"));
                    stmt.setString(2, getString(invoice, "account_type"));
                    setBooleanOrNull(stmt, 3, getBoolean(invoice, "debit_order"));
                    stmt.setString(4, getString(invoice, "description"));
                    stmt.setString(5, getString(invoice, "frequency"));
                    stmt.setString(6, getString(invoice, "frequency_code"));
                    stmt.setDate(7, getDate(invoice, "from_date"));
                    stmt.setDate(8, getDate(invoice, "to_date"));
                    setBigDecimalOrNull(stmt, 9, getBigDecimal(invoice, "gross_amount")); // THE £1,075 AMOUNT!
                    setIntOrNull(stmt, 10, getInteger(invoice, "payment_day"));
                    stmt.setString(11, getString(invoice, "invoice_type"));
                    stmt.setString(12, getString(invoice, "reference"));
                    setBooleanOrNull(stmt, 13, getBoolean(invoice, "vat"));
                    setBigDecimalOrNull(stmt, 14, getBigDecimal(invoice, "vat_amount"));
                    
                    // Extract and flatten nested property object
                    Map<String, Object> property = getNestedObject(invoice, "property");
                    if (property != null) {
                        stmt.setString(15, getString(property, "id")); // property_payprop_id
                        stmt.setString(18, getString(property, "name")); // property_name
                    } else {
                        stmt.setNull(15, java.sql.Types.VARCHAR);
                        stmt.setNull(18, java.sql.Types.VARCHAR);
                    }
                    
                    // Extract and flatten nested tenant object  
                    Map<String, Object> tenant = getNestedObject(invoice, "tenant");
                    if (tenant != null) {
                        stmt.setString(16, getString(tenant, "id")); // tenant_payprop_id
                        stmt.setString(19, getString(tenant, "display_name")); // tenant_display_name
                        stmt.setString(20, getString(tenant, "email")); // tenant_email
                        stmt.setString(21, getString(tenant, "business_name")); // tenant_business_name
                        stmt.setString(22, getString(tenant, "first_name")); // tenant_first_name
                        stmt.setString(23, getString(tenant, "last_name")); // tenant_last_name
                    } else {
                        stmt.setNull(16, java.sql.Types.VARCHAR);
                        stmt.setNull(19, java.sql.Types.VARCHAR);
                        stmt.setNull(20, java.sql.Types.VARCHAR);
                        stmt.setNull(21, java.sql.Types.VARCHAR);
                        stmt.setNull(22, java.sql.Types.VARCHAR);
                        stmt.setNull(23, java.sql.Types.VARCHAR);
                    }
                    
                    // Extract and flatten nested category object
                    Map<String, Object> category = getNestedObject(invoice, "category");
                    if (category != null) {
                        stmt.setString(17, getString(category, "id")); // category_payprop_id
                        stmt.setString(24, getString(category, "name")); // category_name
                    } else {
                        stmt.setNull(17, java.sql.Types.VARCHAR);
                        stmt.setNull(24, java.sql.Types.VARCHAR);
                    }
                    
                    // Meta fields
                    stmt.setTimestamp(25, Timestamp.valueOf(LocalDateTime.now()));
                    stmt.setString(26, "active");
                    
                    stmt.executeUpdate();
                    importedCount++;
                    
                    // Get the invoice description for logging
                    String description = getString(invoice, "description");
                    if (description == null || description.trim().isEmpty()) description = getString(invoice, "reference");
                    if (description == null || description.trim().isEmpty()) description = "Unknown Invoice";
                    
                    // Log the CRITICAL rent amount
                    BigDecimal grossAmount = getBigDecimal(invoice, "gross_amount");
                    log.debug("✅ Imported invoice: {} - £{} ({})", 
                        description, grossAmount, getString(invoice, "id"));
                        
                } catch (Exception e) {
                    log.error("❌ Failed to import invoice {}: {}", 
                        getString(invoice, "id"), e.getMessage());
                    // Continue with next invoice
                }
            }
        }
        
        log.info("📊 Invoices import summary: {} total, {} imported", 
            invoices.size(), importedCount);
        
        return importedCount;
    }
    
    // Helper methods for safe data extraction (same as beneficiaries/properties)
    
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
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getNestedObject(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }
    
    // Safe setter methods for null handling
    
    private void setIntOrNull(PreparedStatement stmt, int parameterIndex, Integer value) throws SQLException {
        if (value != null) {
            stmt.setInt(parameterIndex, value);
        } else {
            stmt.setNull(parameterIndex, java.sql.Types.INTEGER);
        }
    }
    
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
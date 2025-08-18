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
 * PayProp Raw Invoices Import Service
 * 
 * Imports raw invoice instruction data from /export/invoices directly into payprop_export_invoices table.
 * ZERO BUSINESS LOGIC - stores exactly as PayProp returns.
 * 
 * This service handles the £1,075 (gross_amount) import - THE MISSING DATA that solves our mystery!
 */
@Service
public class PayPropRawInvoicesImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawInvoicesImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    /**
     * Import all invoice instructions from PayProp /export/invoices endpoint
     * Stores raw data with zero transformation
     */
    @Transactional
    public PayPropRawImportResult importAllInvoices() {
        log.info("🔄 Starting raw invoice instructions import from PayProp");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/export/invoices");
        
        try {
            // Fetch all invoice instructions using existing API client
            List<Map<String, Object>> invoices = apiClient.fetchAllPages(
                "/export/invoices?include_categories=true", 
                this::processInvoiceItem
            );
            
            // Import to database
            int importedCount = importInvoicesToDatabase(invoices);
            
            result.setTotalFetched(invoices.size());
            result.setTotalImported(importedCount);
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            
            log.info("✅ Raw invoice instructions import completed: {} fetched, {} imported", 
                invoices.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Raw invoice instructions import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    /**
     * Process a single invoice instruction item from PayProp API response
     * No transformation - just wrap the raw data
     */
    private Map<String, Object> processInvoiceItem(Map<String, Object> rawInvoice) {
        // No transformation - return raw data as-is
        // API client will collect all items for batch processing
        return rawInvoice;
    }
    
    /**
     * Import invoice instructions to payprop_export_invoices table
     * Direct SQL insert to preserve exact PayProp structure
     */
    private int importInvoicesToDatabase(List<Map<String, Object>> invoices) throws SQLException {
        if (invoices.isEmpty()) {
            log.info("No invoice instructions to import");
            return 0;
        }
        
        // Clear existing data (full refresh approach)
        clearExistingInvoices();
        
        String insertSql = """
            INSERT INTO payprop_export_invoices (
                payprop_id, account_type, debit_order, description, frequency,
                frequency_code, from_date, to_date, gross_amount, payment_day,
                invoice_type, reference, vat, vat_amount,
                property_payprop_id, tenant_payprop_id, category_payprop_id,
                property_name, tenant_display_name, tenant_email, tenant_business_name,
                tenant_first_name, tenant_last_name, category_name,
                imported_at, last_modified_at, sync_status, is_active_instruction
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                NOW(), ?, 'active', ?
            )
            ON DUPLICATE KEY UPDATE
                description = VALUES(description),
                gross_amount = VALUES(gross_amount),
                from_date = VALUES(from_date),
                to_date = VALUES(to_date),
                is_active_instruction = VALUES(is_active_instruction),
                last_modified_at = VALUES(last_modified_at)
            """;
        
        int importedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (Map<String, Object> invoice : invoices) {
                try {
                    setInvoiceParameters(stmt, invoice);
                    stmt.addBatch();
                    importedCount++;
                    
                    // Execute batch every 25 items (PayProp page size)
                    if (importedCount % 25 == 0) {
                        stmt.executeBatch();
                        log.debug("Imported batch: {} invoice instructions processed", importedCount);
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to prepare invoice instruction for import: {}", 
                        invoice.get("id"), e);
                }
            }
            
            // Execute remaining batch
            if (importedCount % 25 != 0) {
                stmt.executeBatch();
            }
            
            log.info("✅ Database import completed: {} invoice instructions", importedCount);
            
        }
        
        return importedCount;
    }
    
    /**
     * Set parameters for invoice instruction insert statement
     * Maps PayProp fields to database columns with null safety
     */
    private void setInvoiceParameters(PreparedStatement stmt, Map<String, Object> invoice) 
            throws SQLException {
        
        // Extract nested objects safely
        Map<String, Object> property = getMapValue(invoice, "property");
        Map<String, Object> tenant = getMapValue(invoice, "tenant");
        Map<String, Object> category = getMapValue(invoice, "category");
        
        int paramIndex = 1;
        
        // Root invoice instruction fields
        stmt.setString(paramIndex++, getStringValue(invoice, "id"));
        stmt.setString(paramIndex++, getStringValue(invoice, "account_type"));
        stmt.setBoolean(paramIndex++, getBooleanValue(invoice, "debit_order"));
        stmt.setString(paramIndex++, getStringValue(invoice, "description"));
        stmt.setString(paramIndex++, getStringValue(invoice, "frequency"));
        stmt.setString(paramIndex++, getStringValue(invoice, "frequency_code"));
        stmt.setDate(paramIndex++, getDateValue(invoice, "from_date"));
        stmt.setDate(paramIndex++, getDateValue(invoice, "to_date"));
        
        // CRITICAL: The £1,075 authoritative rent amount!
        BigDecimal grossAmount = getBigDecimalValue(invoice, "gross_amount");
        stmt.setBigDecimal(paramIndex++, grossAmount);
        if (grossAmount != null) {
            log.debug("Invoice {} gross_amount: £{} (THE AUTHORITATIVE AMOUNT)", 
                invoice.get("id"), grossAmount);
        }
        
        stmt.setInt(paramIndex++, getIntValue(invoice, "payment_day"));
        stmt.setString(paramIndex++, getStringValue(invoice, "invoice_type"));
        stmt.setString(paramIndex++, getStringValue(invoice, "reference"));
        stmt.setBoolean(paramIndex++, getBooleanValue(invoice, "vat"));
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(invoice, "vat_amount"));
        
        // Foreign key relationships (DO NOT DUPLICATE DATA)
        stmt.setString(paramIndex++, getStringValue(property, "id")); // property_payprop_id
        stmt.setString(paramIndex++, getStringValue(tenant, "id"));   // tenant_payprop_id
        stmt.setString(paramIndex++, getStringValue(category, "id")); // category_payprop_id
        
        // Display fields only (from nested objects - for convenience only)
        stmt.setString(paramIndex++, getStringValue(property, "name"));
        stmt.setString(paramIndex++, getStringValue(tenant, "display_name"));
        stmt.setString(paramIndex++, getStringValue(tenant, "email_address"));
        stmt.setString(paramIndex++, getStringValue(tenant, "business_name"));
        stmt.setString(paramIndex++, getStringValue(tenant, "first_name"));
        stmt.setString(paramIndex++, getStringValue(tenant, "last_name"));
        stmt.setString(paramIndex++, getStringValue(category, "name"));
        
        // Metadata
        stmt.setTimestamp(paramIndex++, new Timestamp(System.currentTimeMillis()));
        
        // Business logic - determine if this is currently active
        boolean isActive = isActiveInvoiceInstruction(invoice);
        stmt.setBoolean(paramIndex++, isActive);
    }
    
    /**
     * Determine if an invoice instruction is currently active
     * Simple logic: active if to_date is null or in the future
     */
    private boolean isActiveInvoiceInstruction(Map<String, Object> invoice) {
        Object toDate = invoice.get("to_date");
        if (toDate == null) {
            return true; // No end date = active
        }
        
        try {
            java.sql.Date endDate = getDateValue(invoice, "to_date");
            if (endDate == null) {
                return true;
            }
            return endDate.after(new java.sql.Date(System.currentTimeMillis()));
        } catch (Exception e) {
            log.warn("Failed to parse to_date for active check: {}", toDate);
            return true; // Default to active if can't parse
        }
    }
    
    /**
     * Clear existing invoice instructions for full refresh
     */
    private void clearExistingInvoices() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "DELETE FROM payprop_export_invoices")) {
            int deletedCount = stmt.executeUpdate();
            log.info("Cleared {} existing invoice instructions for fresh import", deletedCount);
        }
    }
    
    // ===== UTILITY METHODS FOR SAFE DATA EXTRACTION =====
    
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
            return false; // Default to false for invoice booleans
        }
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
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
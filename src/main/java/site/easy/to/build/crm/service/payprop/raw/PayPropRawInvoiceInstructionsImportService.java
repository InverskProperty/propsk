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
 * PayProp Raw Invoice Instructions Import Service
 * 
 * Imports raw invoice instruction data from /export/invoice-instructions directly into payprop_export_invoice_instructions table.
 * ZERO BUSINESS LOGIC - stores exactly as PayProp returns.
 * 
 * This may be different from /export/invoices and could contain additional invoice instruction data.
 */
@Service
public class PayPropRawInvoiceInstructionsImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawInvoiceInstructionsImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private PayPropImportIssueTracker issueTracker;
    
    @Transactional
    public PayPropRawImportResult importAllInvoiceInstructions() {
        log.info("🔄 Starting raw invoice instructions import from PayProp");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/export/invoice-instructions");
        
        try {
            String endpoint = "/export/invoice-instructions?include_categories=true";
            List<Map<String, Object>> invoiceInstructions = apiClient.fetchAllPages(endpoint, this::processInvoiceInstructionItem);
            
            result.setTotalFetched(invoiceInstructions.size());
            log.info("📦 PayProp API returned: {} invoice instruction records", invoiceInstructions.size());
            
            int importedCount = importInvoiceInstructionsToDatabase(invoiceInstructions);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("Invoice instructions imported: %d fetched, %d imported", 
                invoiceInstructions.size(), importedCount));
            
            log.info("✅ Raw invoice instructions import completed: {} fetched, {} imported", 
                invoiceInstructions.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Raw invoice instructions import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    private Map<String, Object> processInvoiceInstructionItem(Map<String, Object> invoiceInstruction) {
        log.debug("Invoice Instruction - ID: {} Property: {} Amount: {} Description: {}", 
            invoiceInstruction.get("id"),
            invoiceInstruction.get("property_id"),
            invoiceInstruction.get("amount"),
            invoiceInstruction.get("description"));
        return invoiceInstruction;
    }
    
    private int importInvoiceInstructionsToDatabase(List<Map<String, Object>> invoiceInstructions) throws SQLException {
        
        // Clear existing data
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_export_invoice_instructions")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing invoice instruction records for fresh import", deletedCount);
            }
        }
        
        if (invoiceInstructions.isEmpty()) {
            return 0;
        }
        
        String insertSql = """
            INSERT INTO payprop_export_invoice_instructions (
                payprop_id, property_payprop_id, tenant_payprop_id, category_payprop_id,
                amount, description, reference, frequency, frequency_code,
                from_date, to_date, payment_day, is_active, created_date,
                modified_date, property_name, tenant_name, category_name, sync_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        int importedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (Map<String, Object> invoiceInstruction : invoiceInstructions) {
                try {
                    String instructionId = getStringValue(invoiceInstruction, "id");
                    if (instructionId == null || instructionId.trim().isEmpty()) {
                        issueTracker.recordIssue(
                            PayPropImportIssueTracker.IssueType.EMPTY_ID,
                            "/export/invoice-instructions",
                            instructionId,
                            invoiceInstruction,
                            "PayProp sent invoice instruction record without ID",
                            PayPropImportIssueTracker.BusinessImpact.FINANCIAL_DATA_MISSING
                        );
                        continue;
                    }
                    
                    setInvoiceInstructionParameters(stmt, invoiceInstruction);
                    stmt.addBatch();
                    importedCount++;
                    
                    if (importedCount % 25 == 0) {
                        stmt.executeBatch();
                    }
                } catch (Exception e) {
                    issueTracker.recordIssue(
                        PayPropImportIssueTracker.IssueType.MAPPING_ERROR,
                        "/export/invoice-instructions",
                        getStringValue(invoiceInstruction, "id"),
                        invoiceInstruction,
                        e.getMessage(),
                        PayPropImportIssueTracker.BusinessImpact.FINANCIAL_DATA_MISSING
                    );
                    log.error("Failed to prepare invoice instruction for import: {}", 
                        invoiceInstruction.get("id"), e);
                }
            }
            
            if (importedCount % 25 != 0) {
                stmt.executeBatch();
            }
            
            log.info("✅ Database import completed: {} invoice instruction records", importedCount);
        }
        
        return importedCount;
    }
    
    private void setInvoiceInstructionParameters(PreparedStatement stmt, Map<String, Object> invoiceInstruction) 
            throws SQLException {
        
        // Extract nested objects safely
        Map<String, Object> property = getMapValue(invoiceInstruction, "property");
        Map<String, Object> tenant = getMapValue(invoiceInstruction, "tenant");
        Map<String, Object> category = getMapValue(invoiceInstruction, "category");
        
        int paramIndex = 1;
        
        // Map PayProp API fields to database columns (19 total parameters)
        stmt.setString(paramIndex++, getStringValue(invoiceInstruction, "id")); // payprop_id
        stmt.setString(paramIndex++, getStringValue(property, "id")); // property_payprop_id
        stmt.setString(paramIndex++, getStringValue(tenant, "id")); // tenant_payprop_id
        stmt.setString(paramIndex++, getStringValue(category, "id")); // category_payprop_id
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(invoiceInstruction, "amount")); // amount
        stmt.setString(paramIndex++, getStringValue(invoiceInstruction, "description")); // description
        stmt.setString(paramIndex++, getStringValue(invoiceInstruction, "reference")); // reference
        stmt.setString(paramIndex++, getStringValue(invoiceInstruction, "frequency")); // frequency
        stmt.setString(paramIndex++, getStringValue(invoiceInstruction, "frequency_code")); // frequency_code
        stmt.setDate(paramIndex++, getDateValue(invoiceInstruction, "from_date")); // from_date
        stmt.setDate(paramIndex++, getDateValue(invoiceInstruction, "to_date")); // to_date
        setIntegerParameter(stmt, paramIndex++, getIntegerValue(invoiceInstruction, "payment_day")); // payment_day
        setBooleanParameter(stmt, paramIndex++, getBooleanValue(invoiceInstruction, "is_active")); // is_active
        stmt.setTimestamp(paramIndex++, getTimestampValue(invoiceInstruction, "created_date")); // created_date
        stmt.setTimestamp(paramIndex++, getTimestampValue(invoiceInstruction, "modified_date")); // modified_date
        stmt.setString(paramIndex++, getStringValue(property, "name")); // property_name
        stmt.setString(paramIndex++, getStringValue(tenant, "name")); // tenant_name
        stmt.setString(paramIndex++, getStringValue(category, "name")); // category_name
        stmt.setString(paramIndex++, "active"); // sync_status (default)
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
    
    private Integer getIntegerValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        try {
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
    
    private void setIntegerParameter(PreparedStatement stmt, int paramIndex, Integer value) 
            throws SQLException {
        if (value == null) {
            stmt.setNull(paramIndex, java.sql.Types.INTEGER);
        } else {
            stmt.setInt(paramIndex, value);
        }
    }
}
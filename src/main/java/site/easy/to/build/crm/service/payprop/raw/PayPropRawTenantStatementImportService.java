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
 * PayProp Raw Tenant Statement Import Service
 * 
 * Imports raw tenant statement data from /report/tenant/statement directly into payprop_report_tenant_statement table.
 * ZERO BUSINESS LOGIC - stores exactly as PayProp returns.
 * 
 * This contains detailed tenant statement entries showing rent charges, payments, and balances.
 */
@Service
public class PayPropRawTenantStatementImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawTenantStatementImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private PayPropImportIssueTracker issueTracker;
    
    @Transactional
    public PayPropRawImportResult importAllTenantStatements() {
        log.info("🔄 Starting raw tenant statements import from PayProp");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/report/tenant/statement");
        
        try {
            // Fetch tenant statements using 2-year historical chunking
            // PayProp has a 93-day limit for report endpoints, so we chunk the requests
            String baseEndpoint = "/report/tenant/statement";
                
            log.info("🕐 Starting 2-year historical import for tenant statements");
            List<Map<String, Object>> tenantStatements = apiClient.fetchHistoricalPages(baseEndpoint, 2, this::processTenantStatementItem);
            
            result.setTotalFetched(tenantStatements.size());
            log.info("📦 PayProp API returned: {} tenant statement entries", tenantStatements.size());
            
            int importedCount = importTenantStatementsToDatabase(tenantStatements);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("Tenant statements imported: %d fetched, %d imported", 
                tenantStatements.size(), importedCount));
            
            log.info("✅ Raw tenant statements import completed: {} fetched, {} imported", 
                tenantStatements.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Raw tenant statements import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    private Map<String, Object> processTenantStatementItem(Map<String, Object> statementEntry) {
        log.debug("Statement Entry - ID: {} Tenant: {} Type: {} Amount: {} Description: {}", 
            statementEntry.get("id"),
            statementEntry.get("tenant_id"),
            statementEntry.get("type"),
            statementEntry.get("amount"),
            statementEntry.get("description"));
        return statementEntry;
    }
    
    private int importTenantStatementsToDatabase(List<Map<String, Object>> tenantStatements) throws SQLException {
        
        // Clear existing data
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_report_tenant_statement")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing tenant statement entries for fresh import", deletedCount);
            }
        }
        
        if (tenantStatements.isEmpty()) {
            return 0;
        }
        
        String insertSql = """
            INSERT INTO payprop_report_tenant_statement (
                payprop_id, tenant_payprop_id, property_payprop_id, entry_date,
                entry_type, amount, description, reference, balance_after,
                transaction_id, category_name, invoice_id, payment_id,
                tenant_name, property_name, due_date, paid_date, sync_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        int importedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (Map<String, Object> statementEntry : tenantStatements) {
                try {
                    String entryId = getStringValue(statementEntry, "id");
                    if (entryId == null || entryId.trim().isEmpty()) {
                        issueTracker.recordIssue(
                            PayPropImportIssueTracker.IssueType.EMPTY_ID,
                            "/report/tenant/statement",
                            entryId,
                            statementEntry,
                            "PayProp sent tenant statement entry without ID",
                            PayPropImportIssueTracker.BusinessImpact.FINANCIAL_DATA_MISSING
                        );
                        continue;
                    }
                    
                    setTenantStatementParameters(stmt, statementEntry);
                    stmt.addBatch();
                    importedCount++;
                    
                    if (importedCount % 25 == 0) {
                        stmt.executeBatch();
                    }
                } catch (Exception e) {
                    issueTracker.recordIssue(
                        PayPropImportIssueTracker.IssueType.MAPPING_ERROR,
                        "/report/tenant/statement",
                        getStringValue(statementEntry, "id"),
                        statementEntry,
                        e.getMessage(),
                        PayPropImportIssueTracker.BusinessImpact.FINANCIAL_DATA_MISSING
                    );
                    log.error("Failed to prepare tenant statement entry for import: {}", 
                        statementEntry.get("id"), e);
                }
            }
            
            if (importedCount % 25 != 0) {
                stmt.executeBatch();
            }
            
            log.info("✅ Database import completed: {} tenant statement entries", importedCount);
        }
        
        return importedCount;
    }
    
    private void setTenantStatementParameters(PreparedStatement stmt, Map<String, Object> statementEntry) 
            throws SQLException {
        
        int paramIndex = 1;
        
        // Map PayProp API fields to database columns (18 total parameters)
        stmt.setString(paramIndex++, getStringValue(statementEntry, "id")); // payprop_id
        stmt.setString(paramIndex++, getStringValue(statementEntry, "tenant_id")); // tenant_payprop_id
        stmt.setString(paramIndex++, getStringValue(statementEntry, "property_id")); // property_payprop_id
        stmt.setDate(paramIndex++, getDateValue(statementEntry, "date")); // entry_date
        stmt.setString(paramIndex++, getStringValue(statementEntry, "type")); // entry_type
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(statementEntry, "amount")); // amount
        stmt.setString(paramIndex++, getStringValue(statementEntry, "description")); // description
        stmt.setString(paramIndex++, getStringValue(statementEntry, "reference")); // reference
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(statementEntry, "balance_after")); // balance_after
        stmt.setString(paramIndex++, getStringValue(statementEntry, "transaction_id")); // transaction_id
        stmt.setString(paramIndex++, getStringValue(statementEntry, "category_name")); // category_name
        stmt.setString(paramIndex++, getStringValue(statementEntry, "invoice_id")); // invoice_id
        stmt.setString(paramIndex++, getStringValue(statementEntry, "payment_id")); // payment_id
        stmt.setString(paramIndex++, getStringValue(statementEntry, "tenant_name")); // tenant_name
        stmt.setString(paramIndex++, getStringValue(statementEntry, "property_name")); // property_name
        stmt.setDate(paramIndex++, getDateValue(statementEntry, "due_date")); // due_date
        stmt.setDate(paramIndex++, getDateValue(statementEntry, "paid_date")); // paid_date
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
}
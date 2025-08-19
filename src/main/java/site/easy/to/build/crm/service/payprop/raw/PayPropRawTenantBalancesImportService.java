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
 * PayProp Raw Tenant Balances Import Service
 * 
 * Imports raw tenant balance data from /report/tenant/balances directly into payprop_report_tenant_balances table.
 * ZERO BUSINESS LOGIC - stores exactly as PayProp returns.
 * 
 * CRITICAL: This endpoint likely contains the £995 monthly rent amounts shown in PayProp UI!
 */
@Service
public class PayPropRawTenantBalancesImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawTenantBalancesImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private PayPropImportIssueTracker issueTracker;
    
    @Transactional
    public PayPropRawImportResult importAllTenantBalances() {
        log.info("🔄 Starting raw tenant balances import from PayProp - SEARCHING FOR £995!");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/report/tenant/balances");
        
        try {
            // Fetch all tenant balances - this should contain current rent amounts
            String endpoint = "/report/tenant/balances";
            List<Map<String, Object>> tenantBalances = apiClient.fetchAllPages(endpoint, this::processTenantBalanceItem);
            
            result.setTotalFetched(tenantBalances.size());
            log.info("📦 PayProp API returned: {} tenant balance records", tenantBalances.size());
            
            int importedCount = importTenantBalancesToDatabase(tenantBalances);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("Tenant balances imported: %d fetched, %d imported", 
                tenantBalances.size(), importedCount));
            
            log.info("✅ Raw tenant balances import completed: {} fetched, {} imported", 
                tenantBalances.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Raw tenant balances import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    private Map<String, Object> processTenantBalanceItem(Map<String, Object> tenantBalance) {
        // Log key balance fields that might contain rent amounts
        log.debug("Tenant Balance - ID: {} Property: {} Balance: {} Monthly Amount: {} Rent: {}", 
            tenantBalance.get("tenant_id"), 
            tenantBalance.get("property_id"),
            tenantBalance.get("balance"),
            tenantBalance.get("monthly_amount"),
            tenantBalance.get("rent_amount"));
        return tenantBalance;
    }
    
    private int importTenantBalancesToDatabase(List<Map<String, Object>> tenantBalances) throws SQLException {
        
        // Clear existing data
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_report_tenant_balances")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing tenant balance records for fresh import", deletedCount);
            }
        }
        
        if (tenantBalances.isEmpty()) {
            return 0;
        }
        
        String insertSql = """
            INSERT INTO payprop_report_tenant_balances (
                tenant_payprop_id, property_payprop_id, balance_amount, 
                monthly_rent_amount, current_balance, outstanding_amount,
                last_payment_amount, last_payment_date, next_due_amount,
                next_due_date, overdue_amount, rent_amount, tenant_name,
                property_name, property_address, balance_date, sync_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        int importedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (Map<String, Object> tenantBalance : tenantBalances) {
                try {
                    String tenantId = getStringValue(tenantBalance, "tenant_id");
                    if (tenantId == null || tenantId.trim().isEmpty()) {
                        issueTracker.recordIssue(
                            PayPropImportIssueTracker.IssueType.EMPTY_ID,
                            "/report/tenant/balances",
                            tenantId,
                            tenantBalance,
                            "PayProp sent tenant balance record without tenant ID",
                            PayPropImportIssueTracker.BusinessImpact.FINANCIAL_DATA_MISSING
                        );
                        continue;
                    }
                    
                    setTenantBalanceParameters(stmt, tenantBalance);
                    stmt.addBatch();
                    importedCount++;
                    
                    if (importedCount % 25 == 0) {
                        stmt.executeBatch();
                    }
                } catch (Exception e) {
                    issueTracker.recordIssue(
                        PayPropImportIssueTracker.IssueType.MAPPING_ERROR,
                        "/report/tenant/balances",
                        getStringValue(tenantBalance, "tenant_id"),
                        tenantBalance,
                        e.getMessage(),
                        PayPropImportIssueTracker.BusinessImpact.FINANCIAL_DATA_MISSING
                    );
                    log.error("Failed to prepare tenant balance for import: {}", 
                        tenantBalance.get("tenant_id"), e);
                }
            }
            
            if (importedCount % 25 != 0) {
                stmt.executeBatch();
            }
            
            log.info("✅ Database import completed: {} tenant balance records", importedCount);
        }
        
        return importedCount;
    }
    
    private void setTenantBalanceParameters(PreparedStatement stmt, Map<String, Object> tenantBalance) 
            throws SQLException {
        
        int paramIndex = 1;
        
        // Map PayProp API fields to database columns (17 total parameters)
        stmt.setString(paramIndex++, getStringValue(tenantBalance, "tenant_id")); // tenant_payprop_id
        stmt.setString(paramIndex++, getStringValue(tenantBalance, "property_id")); // property_payprop_id
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(tenantBalance, "balance")); // balance_amount
        
        // CRITICAL: These fields might contain the £995 monthly rent amount!
        BigDecimal monthlyRent = getBigDecimalValue(tenantBalance, "monthly_rent");
        if (monthlyRent == null) {
            monthlyRent = getBigDecimalValue(tenantBalance, "monthly_amount");
        }
        if (monthlyRent == null) {
            monthlyRent = getBigDecimalValue(tenantBalance, "rent_amount");
        }
        stmt.setBigDecimal(paramIndex++, monthlyRent); // monthly_rent_amount
        
        log.debug("Tenant {} Monthly Rent: £{} (POTENTIAL £995 MATCH!)", 
            tenantBalance.get("tenant_id"), monthlyRent);
        
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(tenantBalance, "current_balance")); // current_balance
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(tenantBalance, "outstanding")); // outstanding_amount
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(tenantBalance, "last_payment_amount")); // last_payment_amount
        stmt.setDate(paramIndex++, getDateValue(tenantBalance, "last_payment_date")); // last_payment_date
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(tenantBalance, "next_due_amount")); // next_due_amount
        stmt.setDate(paramIndex++, getDateValue(tenantBalance, "next_due_date")); // next_due_date
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(tenantBalance, "overdue")); // overdue_amount
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(tenantBalance, "rent_amount")); // rent_amount
        stmt.setString(paramIndex++, getStringValue(tenantBalance, "tenant_name")); // tenant_name
        stmt.setString(paramIndex++, getStringValue(tenantBalance, "property_name")); // property_name
        stmt.setString(paramIndex++, getStringValue(tenantBalance, "property_address")); // property_address
        stmt.setDate(paramIndex++, getDateValue(tenantBalance, "balance_date")); // balance_date
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
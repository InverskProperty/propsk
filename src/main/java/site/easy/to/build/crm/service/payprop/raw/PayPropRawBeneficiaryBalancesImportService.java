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
 * PayProp Raw Beneficiary Balances Import Service
 * 
 * Imports raw beneficiary balance data from /report/beneficiary/balances directly into payprop_report_beneficiary_balances table.
 * ZERO BUSINESS LOGIC - stores exactly as PayProp returns.
 * 
 * This contains property owner balance information that may include rent collection details.
 */
@Service
public class PayPropRawBeneficiaryBalancesImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawBeneficiaryBalancesImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private PayPropImportIssueTracker issueTracker;
    
    @Transactional
    public PayPropRawImportResult importAllBeneficiaryBalances() {
        log.info("🔄 Starting raw beneficiary balances import from PayProp");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/report/beneficiary/balances");
        
        try {
            String endpoint = "/report/beneficiary/balances";
            List<Map<String, Object>> beneficiaryBalances = apiClient.fetchAllPages(endpoint, this::processBeneficiaryBalanceItem);
            
            result.setTotalFetched(beneficiaryBalances.size());
            log.info("📦 PayProp API returned: {} beneficiary balance records", beneficiaryBalances.size());
            
            int importedCount = importBeneficiaryBalancesToDatabase(beneficiaryBalances);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("Beneficiary balances imported: %d fetched, %d imported", 
                beneficiaryBalances.size(), importedCount));
            
            log.info("✅ Raw beneficiary balances import completed: {} fetched, {} imported", 
                beneficiaryBalances.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Raw beneficiary balances import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    private Map<String, Object> processBeneficiaryBalanceItem(Map<String, Object> beneficiaryBalance) {
        log.debug("Beneficiary Balance - ID: {} Name: {} Balance: {} Available: {} Property Count: {}", 
            beneficiaryBalance.get("beneficiary_id"),
            beneficiaryBalance.get("beneficiary_name"),
            beneficiaryBalance.get("balance"),
            beneficiaryBalance.get("available_balance"),
            beneficiaryBalance.get("property_count"));
        return beneficiaryBalance;
    }
    
    private int importBeneficiaryBalancesToDatabase(List<Map<String, Object>> beneficiaryBalances) throws SQLException {
        
        // Clear existing data
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_report_beneficiary_balances")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing beneficiary balance records for fresh import", deletedCount);
            }
        }
        
        if (beneficiaryBalances.isEmpty()) {
            return 0;
        }
        
        String insertSql = """
            INSERT INTO payprop_report_beneficiary_balances (
                beneficiary_payprop_id, beneficiary_name, beneficiary_type,
                current_balance, available_balance, held_balance, pending_balance,
                property_count, total_rent_collected, commission_earned,
                last_payment_amount, last_payment_date, balance_date, sync_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        int importedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (Map<String, Object> beneficiaryBalance : beneficiaryBalances) {
                try {
                    String beneficiaryId = getStringValue(beneficiaryBalance, "beneficiary_id");
                    if (beneficiaryId == null || beneficiaryId.trim().isEmpty()) {
                        issueTracker.recordIssue(
                            PayPropImportIssueTracker.IssueType.EMPTY_ID,
                            "/report/beneficiary/balances",
                            beneficiaryId,
                            beneficiaryBalance,
                            "PayProp sent beneficiary balance record without beneficiary ID",
                            PayPropImportIssueTracker.BusinessImpact.BENEFICIARY_MISSING
                        );
                        continue;
                    }
                    
                    setBeneficiaryBalanceParameters(stmt, beneficiaryBalance);
                    stmt.addBatch();
                    importedCount++;
                    
                    if (importedCount % 25 == 0) {
                        stmt.executeBatch();
                    }
                } catch (Exception e) {
                    issueTracker.recordIssue(
                        PayPropImportIssueTracker.IssueType.MAPPING_ERROR,
                        "/report/beneficiary/balances",
                        getStringValue(beneficiaryBalance, "beneficiary_id"),
                        beneficiaryBalance,
                        e.getMessage(),
                        PayPropImportIssueTracker.BusinessImpact.BENEFICIARY_MISSING
                    );
                    log.error("Failed to prepare beneficiary balance for import: {}", 
                        beneficiaryBalance.get("beneficiary_id"), e);
                }
            }
            
            if (importedCount % 25 != 0) {
                stmt.executeBatch();
            }
            
            log.info("✅ Database import completed: {} beneficiary balance records", importedCount);
        }
        
        return importedCount;
    }
    
    private void setBeneficiaryBalanceParameters(PreparedStatement stmt, Map<String, Object> beneficiaryBalance) 
            throws SQLException {
        
        int paramIndex = 1;
        
        // Map PayProp API fields to database columns (14 total parameters)
        stmt.setString(paramIndex++, getStringValue(beneficiaryBalance, "beneficiary_id")); // beneficiary_payprop_id
        stmt.setString(paramIndex++, getStringValue(beneficiaryBalance, "beneficiary_name")); // beneficiary_name
        stmt.setString(paramIndex++, getStringValue(beneficiaryBalance, "beneficiary_type")); // beneficiary_type
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(beneficiaryBalance, "balance")); // current_balance
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(beneficiaryBalance, "available_balance")); // available_balance
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(beneficiaryBalance, "held_balance")); // held_balance
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(beneficiaryBalance, "pending_balance")); // pending_balance
        setIntegerParameter(stmt, paramIndex++, getIntegerValue(beneficiaryBalance, "property_count")); // property_count
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(beneficiaryBalance, "total_rent_collected")); // total_rent_collected
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(beneficiaryBalance, "commission_earned")); // commission_earned
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(beneficiaryBalance, "last_payment_amount")); // last_payment_amount
        stmt.setDate(paramIndex++, getDateValue(beneficiaryBalance, "last_payment_date")); // last_payment_date
        stmt.setDate(paramIndex++, getDateValue(beneficiaryBalance, "balance_date")); // balance_date
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
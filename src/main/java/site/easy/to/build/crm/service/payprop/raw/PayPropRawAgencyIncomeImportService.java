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
 * PayProp Raw Agency Income Import Service
 * 
 * Imports raw agency income data from /report/agency/income directly into payprop_report_agency_income table.
 * ZERO BUSINESS LOGIC - stores exactly as PayProp returns.
 * 
 * This contains agency commission and income data that may include rent-based calculations.
 */
@Service
public class PayPropRawAgencyIncomeImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawAgencyIncomeImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private PayPropImportIssueTracker issueTracker;
    
    @Transactional
    public PayPropRawImportResult importAllAgencyIncome() {
        log.info("🔄 Starting raw agency income import from PayProp");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/report/agency/income");
        
        try {
            // Use last 93 days to match other reports
            LocalDateTime endDateTime = LocalDateTime.now();
            LocalDateTime startDateTime = endDateTime.minusDays(93);
            
            String endpoint = "/report/agency/income" +
                "?from_date=" + startDateTime.toLocalDate().toString() +
                "&to_date=" + endDateTime.toLocalDate().toString();
                
            List<Map<String, Object>> agencyIncomeRecords = apiClient.fetchAllPages(endpoint, this::processAgencyIncomeItem);
            
            result.setTotalFetched(agencyIncomeRecords.size());
            log.info("📦 PayProp API returned: {} agency income records", agencyIncomeRecords.size());
            
            int importedCount = importAgencyIncomeToDatabase(agencyIncomeRecords);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("Agency income imported: %d fetched, %d imported", 
                agencyIncomeRecords.size(), importedCount));
            
            log.info("✅ Raw agency income import completed: {} fetched, {} imported", 
                agencyIncomeRecords.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Raw agency income import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    private Map<String, Object> processAgencyIncomeItem(Map<String, Object> agencyIncome) {
        log.debug("Agency Income - ID: {} Type: {} Amount: {} Property: {} Commission: {}", 
            agencyIncome.get("id"),
            agencyIncome.get("income_type"),
            agencyIncome.get("amount"),
            agencyIncome.get("property_id"),
            agencyIncome.get("commission_amount"));
        return agencyIncome;
    }
    
    private int importAgencyIncomeToDatabase(List<Map<String, Object>> agencyIncomeRecords) throws SQLException {
        
        // Clear existing data
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_report_agency_income")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing agency income records for fresh import", deletedCount);
            }
        }
        
        if (agencyIncomeRecords.isEmpty()) {
            return 0;
        }
        
        String insertSql = """
            INSERT INTO payprop_report_agency_income (
                payprop_id, income_type, income_date, amount, commission_amount,
                commission_percentage, property_payprop_id, tenant_payprop_id,
                payment_id, invoice_id, description, category_name,
                property_name, tenant_name, rent_amount, sync_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        int importedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (Map<String, Object> agencyIncome : agencyIncomeRecords) {
                try {
                    String incomeId = getStringValue(agencyIncome, "id");
                    if (incomeId == null || incomeId.trim().isEmpty()) {
                        issueTracker.recordIssue(
                            PayPropImportIssueTracker.IssueType.EMPTY_ID,
                            "/report/agency/income",
                            incomeId,
                            agencyIncome,
                            "PayProp sent agency income record without ID",
                            PayPropImportIssueTracker.BusinessImpact.FINANCIAL_DATA_MISSING
                        );
                        continue;
                    }
                    
                    setAgencyIncomeParameters(stmt, agencyIncome);
                    stmt.addBatch();
                    importedCount++;
                    
                    if (importedCount % 25 == 0) {
                        stmt.executeBatch();
                    }
                } catch (Exception e) {
                    issueTracker.recordIssue(
                        PayPropImportIssueTracker.IssueType.MAPPING_ERROR,
                        "/report/agency/income",
                        getStringValue(agencyIncome, "id"),
                        agencyIncome,
                        e.getMessage(),
                        PayPropImportIssueTracker.BusinessImpact.FINANCIAL_DATA_MISSING
                    );
                    log.error("Failed to prepare agency income for import: {}", 
                        agencyIncome.get("id"), e);
                }
            }
            
            if (importedCount % 25 != 0) {
                stmt.executeBatch();
            }
            
            log.info("✅ Database import completed: {} agency income records", importedCount);
        }
        
        return importedCount;
    }
    
    private void setAgencyIncomeParameters(PreparedStatement stmt, Map<String, Object> agencyIncome) 
            throws SQLException {
        
        int paramIndex = 1;
        
        // Map PayProp API fields to database columns (16 total parameters)
        stmt.setString(paramIndex++, getStringValue(agencyIncome, "id")); // payprop_id
        stmt.setString(paramIndex++, getStringValue(agencyIncome, "income_type")); // income_type
        stmt.setDate(paramIndex++, getDateValue(agencyIncome, "income_date")); // income_date
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(agencyIncome, "amount")); // amount
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(agencyIncome, "commission_amount")); // commission_amount
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(agencyIncome, "commission_percentage")); // commission_percentage
        stmt.setString(paramIndex++, getStringValue(agencyIncome, "property_id")); // property_payprop_id
        stmt.setString(paramIndex++, getStringValue(agencyIncome, "tenant_id")); // tenant_payprop_id
        stmt.setString(paramIndex++, getStringValue(agencyIncome, "payment_id")); // payment_id
        stmt.setString(paramIndex++, getStringValue(agencyIncome, "invoice_id")); // invoice_id
        stmt.setString(paramIndex++, getStringValue(agencyIncome, "description")); // description
        stmt.setString(paramIndex++, getStringValue(agencyIncome, "category_name")); // category_name
        stmt.setString(paramIndex++, getStringValue(agencyIncome, "property_name")); // property_name
        stmt.setString(paramIndex++, getStringValue(agencyIncome, "tenant_name")); // tenant_name
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(agencyIncome, "rent_amount")); // rent_amount
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
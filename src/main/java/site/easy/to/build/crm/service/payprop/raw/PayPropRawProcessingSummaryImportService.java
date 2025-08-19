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
 * PayProp Raw Processing Summary Import Service
 * 
 * Imports raw processing summary data from /report/processing-summary directly into payprop_report_processing_summary table.
 * ZERO BUSINESS LOGIC - stores exactly as PayProp returns.
 * 
 * This contains payment processing summaries that may include rent processing information.
 */
@Service
public class PayPropRawProcessingSummaryImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawProcessingSummaryImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private PayPropImportIssueTracker issueTracker;
    
    @Transactional
    public PayPropRawImportResult importAllProcessingSummaries() {
        log.info("🔄 Starting raw processing summary import from PayProp");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/report/processing-summary");
        
        try {
            // Use last 93 days to match other reports
            LocalDateTime endDateTime = LocalDateTime.now();
            LocalDateTime startDateTime = endDateTime.minusDays(93);
            
            String endpoint = "/report/processing-summary" +
                "?from_date=" + startDateTime.toLocalDate().toString() +
                "&to_date=" + endDateTime.toLocalDate().toString();
                
            List<Map<String, Object>> processingSummaries = apiClient.fetchAllPages(endpoint, this::processProcessingSummaryItem);
            
            result.setTotalFetched(processingSummaries.size());
            log.info("📦 PayProp API returned: {} processing summary records", processingSummaries.size());
            
            int importedCount = importProcessingSummariesToDatabase(processingSummaries);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("Processing summaries imported: %d fetched, %d imported", 
                processingSummaries.size(), importedCount));
            
            log.info("✅ Raw processing summary import completed: {} fetched, {} imported", 
                processingSummaries.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Raw processing summary import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    private Map<String, Object> processProcessingSummaryItem(Map<String, Object> processingSummary) {
        log.debug("Processing Summary - ID: {} Date: {} Total Amount: {} Payment Count: {} Properties: {}", 
            processingSummary.get("id"),
            processingSummary.get("processing_date"),
            processingSummary.get("total_amount"),
            processingSummary.get("payment_count"),
            processingSummary.get("property_count"));
        return processingSummary;
    }
    
    private int importProcessingSummariesToDatabase(List<Map<String, Object>> processingSummaries) throws SQLException {
        
        // Clear existing data
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_report_processing_summary")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing processing summary records for fresh import", deletedCount);
            }
        }
        
        if (processingSummaries.isEmpty()) {
            return 0;
        }
        
        String insertSql = """
            INSERT INTO payprop_report_processing_summary (
                payprop_id, processing_date, total_amount, payment_count,
                property_count, tenant_count, commission_amount, fee_amount,
                rent_collected, owner_payments, tenant_payments,
                successful_payments, failed_payments, processing_status, sync_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        int importedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (Map<String, Object> processingSummary : processingSummaries) {
                try {
                    String summaryId = getStringValue(processingSummary, "id");
                    if (summaryId == null || summaryId.trim().isEmpty()) {
                        issueTracker.recordIssue(
                            PayPropImportIssueTracker.IssueType.EMPTY_ID,
                            "/report/processing-summary",
                            summaryId,
                            processingSummary,
                            "PayProp sent processing summary record without ID",
                            PayPropImportIssueTracker.BusinessImpact.FINANCIAL_DATA_MISSING
                        );
                        continue;
                    }
                    
                    setProcessingSummaryParameters(stmt, processingSummary);
                    stmt.addBatch();
                    importedCount++;
                    
                    if (importedCount % 25 == 0) {
                        stmt.executeBatch();
                    }
                } catch (Exception e) {
                    issueTracker.recordIssue(
                        PayPropImportIssueTracker.IssueType.MAPPING_ERROR,
                        "/report/processing-summary",
                        getStringValue(processingSummary, "id"),
                        processingSummary,
                        e.getMessage(),
                        PayPropImportIssueTracker.BusinessImpact.FINANCIAL_DATA_MISSING
                    );
                    log.error("Failed to prepare processing summary for import: {}", 
                        processingSummary.get("id"), e);
                }
            }
            
            if (importedCount % 25 != 0) {
                stmt.executeBatch();
            }
            
            log.info("✅ Database import completed: {} processing summary records", importedCount);
        }
        
        return importedCount;
    }
    
    private void setProcessingSummaryParameters(PreparedStatement stmt, Map<String, Object> processingSummary) 
            throws SQLException {
        
        int paramIndex = 1;
        
        // Map PayProp API fields to database columns (15 total parameters)
        stmt.setString(paramIndex++, getStringValue(processingSummary, "id")); // payprop_id
        stmt.setDate(paramIndex++, getDateValue(processingSummary, "processing_date")); // processing_date
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(processingSummary, "total_amount")); // total_amount
        setIntegerParameter(stmt, paramIndex++, getIntegerValue(processingSummary, "payment_count")); // payment_count
        setIntegerParameter(stmt, paramIndex++, getIntegerValue(processingSummary, "property_count")); // property_count
        setIntegerParameter(stmt, paramIndex++, getIntegerValue(processingSummary, "tenant_count")); // tenant_count
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(processingSummary, "commission_amount")); // commission_amount
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(processingSummary, "fee_amount")); // fee_amount
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(processingSummary, "rent_collected")); // rent_collected
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(processingSummary, "owner_payments")); // owner_payments
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(processingSummary, "tenant_payments")); // tenant_payments
        setIntegerParameter(stmt, paramIndex++, getIntegerValue(processingSummary, "successful_payments")); // successful_payments
        setIntegerParameter(stmt, paramIndex++, getIntegerValue(processingSummary, "failed_payments")); // failed_payments
        stmt.setString(paramIndex++, getStringValue(processingSummary, "processing_status")); // processing_status
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
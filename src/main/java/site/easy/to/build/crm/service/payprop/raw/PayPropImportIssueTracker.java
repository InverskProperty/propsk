package site.easy.to.build.crm.service.payprop.raw;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.HashMap;

/**
 * PayProp Import Issue Tracker
 * 
 * Captures all problematic records during PayProp imports for analysis and resolution.
 * Ensures zero data loss by storing issues in separate tracking table.
 */
@Service
public class PayPropImportIssueTracker {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropImportIssueTracker.class);
    
    @Autowired
    private DataSource dataSource;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String currentImportRunId;
    
    /**
     * Start tracking for a new import run
     */
    public void startImportRun() {
        currentImportRunId = generateImportRunId();
        log.info("🔍 Started import issue tracking for run: {}", currentImportRunId);
    }
    
    /**
     * Record an import issue for later analysis
     */
    public void recordIssue(String issueType, String endpoint, String problematicId, 
                           Map<String, Object> originalData, String errorMessage, 
                           String businessImpact) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                INSERT INTO payprop_import_issues 
                (import_run_id, endpoint, issue_type, problematic_id, original_data, 
                 error_message, business_impact) 
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, currentImportRunId);
                stmt.setString(2, endpoint);
                stmt.setString(3, issueType);
                stmt.setString(4, problematicId);
                stmt.setString(5, objectMapper.writeValueAsString(originalData));
                stmt.setString(6, errorMessage);
                stmt.setString(7, businessImpact);
                
                stmt.executeUpdate();
                
                log.warn("📝 Recorded import issue: {} for {} (ID: {}) - {}", 
                    issueType, endpoint, problematicId, businessImpact);
                
            }
        } catch (Exception e) {
            log.error("❌ Failed to record import issue: {}", e.getMessage());
            // Don't rethrow - recording issues shouldn't break imports
        }
    }
    
    /**
     * Generate import summary report
     */
    public PayPropImportSummary generateImportSummary() {
        if (currentImportRunId == null) {
            log.warn("No active import run to summarize");
            return new PayPropImportSummary();
        }
        
        PayPropImportSummary summary = new PayPropImportSummary();
        summary.setImportRunId(currentImportRunId);
        
        try (Connection conn = dataSource.getConnection()) {
            // Get issue counts by type and endpoint
            String sql = """
                SELECT endpoint, issue_type, COUNT(*) as issue_count,
                       SUM(CASE WHEN business_impact LIKE '%financial%' THEN 1 ELSE 0 END) as financial_impact_count
                FROM payprop_import_issues 
                WHERE import_run_id = ? 
                GROUP BY endpoint, issue_type 
                ORDER BY endpoint, issue_count DESC
            """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, currentImportRunId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        PayPropImportSummary.IssueCount issueCount = new PayPropImportSummary.IssueCount();
                        issueCount.setEndpoint(rs.getString("endpoint"));
                        issueCount.setIssueType(rs.getString("issue_type"));
                        issueCount.setCount(rs.getInt("issue_count"));
                        issueCount.setFinancialImpactCount(rs.getInt("financial_impact_count"));
                        
                        summary.addIssueCount(issueCount);
                    }
                }
            }
            
            // Get total issue count
            String totalSql = "SELECT COUNT(*) FROM payprop_import_issues WHERE import_run_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(totalSql)) {
                stmt.setString(1, currentImportRunId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        summary.setTotalIssues(rs.getInt(1));
                    }
                }
            }
            
        } catch (SQLException e) {
            log.error("Failed to generate import summary: {}", e.getMessage());
        }
        
        return summary;
    }
    
    /**
     * Get current import run ID
     */
    public String getCurrentImportRunId() {
        return currentImportRunId;
    }
    
    /**
     * Generate unique import run ID based on timestamp
     */
    private String generateImportRunId() {
        return "IMPORT_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }
    
    /**
     * Common issue types as constants
     */
    public static class IssueType {
        public static final String EMPTY_ID = "EMPTY_ID";
        public static final String DUPLICATE_ID = "DUPLICATE_ID";
        public static final String INVALID_DATA = "INVALID_DATA";
        public static final String CONSTRAINT_VIOLATION = "CONSTRAINT_VIOLATION";
        public static final String MAPPING_ERROR = "MAPPING_ERROR";
        public static final String DATABASE_ERROR = "DATABASE_ERROR";
    }
    
    /**
     * Common business impact descriptions
     */
    public static class BusinessImpact {
        public static final String FINANCIAL_DATA_MISSING = "Financial data may be missing from reports";
        public static final String DUPLICATE_DATA = "Duplicate data - may indicate PayProp inconsistency";
        public static final String BENEFICIARY_MISSING = "Beneficiary information missing - payments may not be properly attributed";
        public static final String PROPERTY_MISSING = "Property information missing - portfolio reports may be incomplete";
        public static final String TENANT_MISSING = "Tenant information missing - rental tracking may be affected";
    }
}
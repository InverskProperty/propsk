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
    
    // Issue Type Constants
    public static final String EMPTY_ID = "EMPTY_ID";
    public static final String MAPPING_ERROR = "MAPPING_ERROR";
    public static final String CONSTRAINT_VIOLATION = "CONSTRAINT_VIOLATION";
    public static final String API_ERROR = "API_ERROR";
    
    // Business Impact Constants  
    public static final String FINANCIAL_DATA_MISSING = "FINANCIAL_DATA_MISSING";
    public static final String DATA_INTEGRITY_ISSUE = "DATA_INTEGRITY_ISSUE";
    public static final String REFERENCE_MISSING = "REFERENCE_MISSING";
    
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
     * Start tracking for a new import run with custom ID
     */
    public void startImportRun(String customRunId) {
        currentImportRunId = customRunId;
        log.info("🔍 Started import issue tracking for custom run: {}", currentImportRunId);
    }
    
    /**
     * Get current import run ID (auto-generates if none exists)
     */
    public String getCurrentImportRunId() {
        if (currentImportRunId == null) {
            currentImportRunId = generateImportRunId();
            log.info("🔍 Auto-generated import run ID: {}", currentImportRunId);
        }
        return currentImportRunId;
    }
    
    /**
     * Record an import issue with optional parent run ID for linking
     */
    public void recordIssue(String issueType, String endpoint, String problematicId, 
                           Map<String, Object> originalData, String errorMessage, 
                           String businessImpact, String parentImportRunId) {
        
        // Smart Auto-Generation: Create run ID if none exists
        if (currentImportRunId == null) {
            currentImportRunId = generateImportRunId();
            log.info("🔍 Auto-generated import run ID: {} for endpoint: {}", currentImportRunId, endpoint);
        }
        
        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                INSERT INTO payprop_import_issues 
                (import_run_id, parent_import_run_id, endpoint, issue_type, problematic_id, original_data, 
                 error_message, business_impact) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, currentImportRunId);
                stmt.setString(2, parentImportRunId); // Can be null for individual imports
                stmt.setString(3, endpoint);
                stmt.setString(4, issueType);
                stmt.setString(5, problematicId);
                stmt.setString(6, objectMapper.writeValueAsString(originalData));
                stmt.setString(7, errorMessage);
                stmt.setString(8, businessImpact);
                
                stmt.executeUpdate();
                
                log.warn("📝 Recorded import issue: {} for {} (ID: {}, Parent: {}) - {}", 
                    issueType, endpoint, problematicId, parentImportRunId, errorMessage);
                
            } catch (Exception e) {
                // Fallback: try without parent_import_run_id column if it doesn't exist
                log.warn("⚠️ Failed to record issue with parent ID, trying without: {}", e.getMessage());
                recordIssueFallback(currentImportRunId, endpoint, issueType, problematicId, originalData, errorMessage, businessImpact);
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to record import issue: {}", e.getMessage());
        }
    }
    
    /**
     * Record an import issue for later analysis
     * Auto-generates import_run_id if none exists (Smart Auto-Generation)
     */
    public void recordIssue(String issueType, String endpoint, String problematicId, 
                           Map<String, Object> originalData, String errorMessage, 
                           String businessImpact) {
        
        // Smart Auto-Generation: Create run ID if none exists
        if (currentImportRunId == null) {
            currentImportRunId = generateImportRunId();
            log.info("🔍 Auto-generated import run ID: {} for endpoint: {}", currentImportRunId, endpoint);
        }
        
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
     * Fallback method to record issues without parent_import_run_id column
     */
    private void recordIssueFallback(String runId, String endpoint, String issueType, 
                                   String problematicId, Map<String, Object> originalData,
                                   String errorMessage, String businessImpact) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                INSERT INTO payprop_import_issues 
                (import_run_id, endpoint, issue_type, problematic_id, original_data, 
                 error_message, business_impact) 
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, runId);
                stmt.setString(2, endpoint);
                stmt.setString(3, issueType);
                stmt.setString(4, problematicId);
                stmt.setString(5, objectMapper.writeValueAsString(originalData));
                stmt.setString(6, errorMessage);
                stmt.setString(7, businessImpact);
                
                stmt.executeUpdate();
                
                log.warn("📝 Recorded import issue (fallback): {} for {} (ID: {}) - {}", 
                    issueType, endpoint, problematicId, businessImpact);
                
            }
        } catch (Exception e) {
            log.error("❌ Failed to record import issue even with fallback: {}", e.getMessage());
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
        public static final String EMPTY_NAME = "EMPTY_NAME";
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
        public static final String REFERENCE_DATA_MISSING = "Reference data missing - categorization may be incomplete";
    }
}
package site.easy.to.build.crm.service.payprop.raw;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.service.payprop.PayPropApiClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * PayProp Raw Maintenance Categories Import Service
 * 
 * Imports maintenance categories from /maintenance-categories endpoint.
 * These are reference data for categorizing maintenance requests.
 */
@Service
public class PayPropRawMaintenanceCategoriesImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawMaintenanceCategoriesImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private PayPropImportIssueTracker issueTracker;
    
    @Transactional
    public PayPropRawImportResult importAllMaintenanceCategories() {
        log.info("🔄 Starting maintenance categories import from PayProp");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/maintenance/categories");
        
        try {
            String endpoint = "/maintenance/categories";
            List<Map<String, Object>> categories = apiClient.fetchAllPages(endpoint, this::processCategoryItem);
            
            result.setTotalFetched(categories.size());
            log.info("📦 PayProp API returned: {} maintenance categories", categories.size());
            
            int importedCount = importCategoriesToDatabase(categories);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("Maintenance categories imported: %d fetched, %d imported", 
                categories.size(), importedCount));
            
            log.info("✅ Maintenance categories import completed: {} fetched, {} imported", 
                categories.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Maintenance categories import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    private Map<String, Object> processCategoryItem(Map<String, Object> category) {
        log.debug("Maintenance Category - ID: {} Name: {} Description: {}", 
            category.get("id"),
            category.get("name"),
            category.get("description"));
        return category;
    }
    
    private int importCategoriesToDatabase(List<Map<String, Object>> categories) throws SQLException {
        
        // Clear existing data
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_maintenance_categories")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing maintenance categories for fresh import", deletedCount);
            }
        }
        
        if (categories.isEmpty()) {
            return 0;
        }
        
        String insertSql = """
            INSERT IGNORE INTO payprop_maintenance_categories (
                payprop_external_id, name, description, category_type, is_active
            ) VALUES (?, ?, ?, ?, ?)
        """;
        
        int importedCount = 0;
        
        for (Map<String, Object> category : categories) {
            try {
                String categoryId = getStringValue(category, "id");
                if (categoryId == null || categoryId.trim().isEmpty()) {
                    issueTracker.recordIssue(
                        PayPropImportIssueTracker.IssueType.EMPTY_ID,
                        "/payments/categories",
                        categoryId,
                        category,
                        "PayProp sent category without ID",
                        PayPropImportIssueTracker.BusinessImpact.REFERENCE_DATA_MISSING
                    );
                    continue;
                }
                
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    
                    setCategoryParameters(stmt, category);
                    int result = stmt.executeUpdate();
                    
                    if (result > 0) {
                        importedCount++;
                    }
                }
                
            } catch (Exception e) {
                issueTracker.recordIssue(
                    PayPropImportIssueTracker.IssueType.MAPPING_ERROR,
                    "/payments/categories",
                    getStringValue(category, "id"),
                    category,
                    e.getMessage(),
                    PayPropImportIssueTracker.BusinessImpact.REFERENCE_DATA_MISSING
                );
                log.error("Failed to import maintenance category: {}", category.get("id"), e);
            }
        }
        
        log.info("✅ Database import completed: {} maintenance categories", importedCount);
        return importedCount;
    }
    
    private void setCategoryParameters(PreparedStatement stmt, Map<String, Object> category) 
            throws SQLException {
        
        int paramIndex = 1;
        
        stmt.setString(paramIndex++, getStringValue(category, "id")); // payprop_external_id
        stmt.setString(paramIndex++, getStringValue(category, "name")); // name
        stmt.setString(paramIndex++, getStringValue(category, "description")); // description
        stmt.setString(paramIndex++, "maintenance"); // category_type
        stmt.setBoolean(paramIndex++, true); // is_active
    }
    
    // Helper methods
    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        return map.get(key).toString();
    }
}
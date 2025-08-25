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
 * PayProp Raw Invoice Categories Import Service
 * 
 * Imports invoice categories from /invoice-categories endpoint.
 * These are reference data for categorizing invoices.
 */
@Service
public class PayPropRawInvoiceCategoriesImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawInvoiceCategoriesImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private PayPropImportIssueTracker issueTracker;
    
    @Transactional
    public PayPropRawImportResult importAllInvoiceCategories() {
        log.info("🔄 Starting invoice categories import from PayProp");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/invoice-categories");
        
        try {
            String endpoint = "/invoice-categories";
            List<Map<String, Object>> categories = apiClient.fetchAllPages(endpoint, this::processCategoryItem);
            
            result.setTotalFetched(categories.size());
            log.info("📦 PayProp API returned: {} invoice categories", categories.size());
            
            int importedCount = importCategoriesToDatabase(categories);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("Invoice categories imported: %d fetched, %d imported", 
                categories.size(), importedCount));
            
            log.info("✅ Invoice categories import completed: {} fetched, {} imported", 
                categories.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Invoice categories import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    private Map<String, Object> processCategoryItem(Map<String, Object> category) {
        log.debug("Invoice Category - ID: {} Name: {} Active: {}", 
            category.get("id"),
            category.get("name"),
            category.get("is_active"));
        return category;
    }
    
    private int importCategoriesToDatabase(List<Map<String, Object>> categories) throws SQLException {
        
        // Clear existing data
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_invoice_categories")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing invoice categories for fresh import", deletedCount);
            }
        }
        
        if (categories.isEmpty()) {
            return 0;
        }
        
        String insertSql = """
            INSERT IGNORE INTO payprop_invoice_categories (
                payprop_external_id, name, description, is_active, 
                is_system, parent_category_id, color_code, default_frequency,
                category_group, imported_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        int importedCount = 0;
        
        for (Map<String, Object> category : categories) {
            try {
                String categoryId = getStringValue(category, "id");
                if (categoryId == null || categoryId.trim().isEmpty()) {
                    issueTracker.recordIssue(
                        PayPropImportIssueTracker.IssueType.EMPTY_ID,
                        "/invoice-categories",
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
                    "/invoice-categories",
                    getStringValue(category, "id"),
                    category,
                    e.getMessage(),
                    PayPropImportIssueTracker.BusinessImpact.REFERENCE_DATA_MISSING
                );
                log.error("Failed to import category: {}", category.get("id"), e);
            }
        }
        
        log.info("✅ Database import completed: {} invoice categories", importedCount);
        return importedCount;
    }
    
    private void setCategoryParameters(PreparedStatement stmt, Map<String, Object> category) 
            throws SQLException {
        
        int paramIndex = 1;
        
        stmt.setString(paramIndex++, getStringValue(category, "id")); // payprop_external_id
        stmt.setString(paramIndex++, getStringValue(category, "name")); // name
        stmt.setString(paramIndex++, getStringValue(category, "description")); // description
        setBooleanParameter(stmt, paramIndex++, getBooleanValue(category, "is_active")); // is_active
        setBooleanParameter(stmt, paramIndex++, getBooleanValue(category, "is_system")); // is_system
        stmt.setString(paramIndex++, getStringValue(category, "parent_category_id")); // parent_category_id
        stmt.setString(paramIndex++, getStringValue(category, "color_code")); // color_code
        stmt.setString(paramIndex++, getStringValue(category, "default_frequency")); // default_frequency
        stmt.setString(paramIndex++, getStringValue(category, "category_group")); // category_group
        stmt.setTimestamp(paramIndex++, Timestamp.valueOf(LocalDateTime.now())); // imported_at
    }
    
    // Helper methods
    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        return map.get(key).toString();
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
    
    private void setBooleanParameter(PreparedStatement stmt, int paramIndex, Boolean value) 
            throws SQLException {
        if (value == null) {
            stmt.setNull(paramIndex, java.sql.Types.BOOLEAN);
        } else {
            stmt.setBoolean(paramIndex, value);
        }
    }
}
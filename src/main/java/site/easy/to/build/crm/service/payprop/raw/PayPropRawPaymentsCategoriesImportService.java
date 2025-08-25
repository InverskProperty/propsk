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
 * PayProp Raw Payments Categories Import Service
 * 
 * Imports payment categories from /payments/categories endpoint.
 * These are reference data for categorizing payments and distributions.
 */
@Service
public class PayPropRawPaymentsCategoriesImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawPaymentsCategoriesImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private PayPropImportIssueTracker issueTracker;
    
    @Transactional
    public PayPropRawImportResult importAllPaymentsCategories() {
        log.info("🔄 Starting payments categories import from PayProp");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/payments/categories");
        
        try {
            String endpoint = "/payments/categories";
            List<Map<String, Object>> categories = apiClient.fetchAllPages(endpoint, this::processCategoryItem);
            
            result.setTotalFetched(categories.size());
            log.info("📦 PayProp API returned: {} payment categories", categories.size());
            
            int importedCount = importCategoriesToDatabase(categories);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("Payment categories imported: %d fetched, %d imported", 
                categories.size(), importedCount));
            
            log.info("✅ Payment categories import completed: {} fetched, {} imported", 
                categories.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Payment categories import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    private Map<String, Object> processCategoryItem(Map<String, Object> category) {
        log.debug("Payment Category - ID: {} Name: {} Description: {}", 
            category.get("id"),
            category.get("name"),
            category.get("description"));
        return category;
    }
    
    private int importCategoriesToDatabase(List<Map<String, Object>> categories) throws SQLException {
        
        // For now, use the existing payprop_maintenance_categories table
        // TODO: Create dedicated payprop_payment_categories table once we see the data structure
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_maintenance_categories")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing payment categories for fresh import", deletedCount);
            }
        }
        
        if (categories.isEmpty()) {
            return 0;
        }
        
        String insertSql = """
            INSERT IGNORE INTO payprop_maintenance_categories (
                payprop_external_id, name, description, imported_at
            ) VALUES (?, ?, ?, ?)
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
                log.error("Failed to import payment category: {}", category.get("id"), e);
            }
        }
        
        log.info("✅ Database import completed: {} payment categories", importedCount);
        return importedCount;
    }
    
    private void setCategoryParameters(PreparedStatement stmt, Map<String, Object> category) 
            throws SQLException {
        
        int paramIndex = 1;
        
        stmt.setString(paramIndex++, getStringValue(category, "id")); // payprop_external_id
        stmt.setString(paramIndex++, getStringValue(category, "name")); // name
        stmt.setString(paramIndex++, getStringValue(category, "description")); // description
        stmt.setTimestamp(paramIndex++, Timestamp.valueOf(LocalDateTime.now())); // imported_at
    }
    
    // Helper methods
    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        return map.get(key).toString();
    }
}
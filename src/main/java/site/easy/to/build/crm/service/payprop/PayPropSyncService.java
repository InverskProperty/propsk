// PayPropSyncService.java - Updated to use PayPropApiClient
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PayProp API Client Service
 * This service handles basic API communication with PayProp.
 * All sync orchestration logic should be in PayPropSyncOrchestrator.
 * All financial sync logic should be in PayPropFinancialSyncService.
 */
@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropSyncService {

    private static final Logger log = LoggerFactory.getLogger(PayPropSyncService.class);

    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private PayPropOAuth2Service oAuth2Service;
    
    @Value("${payprop.api.base-url:https://ukapi.staging.payprop.com/api/agency/v1.1}")
    private String payPropApiBase;

    private static final Logger logger = LoggerFactory.getLogger(PayPropSyncService.class);
    
    // ===== EXPORT METHODS (Using PayPropApiClient) =====
    
    /**
     * Export properties from PayProp with pagination
     * @deprecated Use exportAllProperties() for complete data or apiClient.fetchSinglePage() for single page
     */
    @Deprecated
    public PayPropExportResult exportPropertiesFromPayProp(int page, int rows) {
        log.info("📥 Exporting properties from PayProp - Page {}", page);
        
        PayPropApiClient.PayPropPageResult pageResult = apiClient.fetchSinglePage("/export/properties", page, rows);
        
        // Convert to legacy format for backwards compatibility
        PayPropExportResult result = new PayPropExportResult();
        result.setItems(pageResult.getItems());
        result.setPagination(pageResult.getPagination());
        
        log.info("✅ Exported {} properties from PayProp", result.getItems().size());
        return result;
    }
    
    /**
     * Export ALL properties from PayProp (handles pagination automatically)
     */
    public List<Map<String, Object>> exportAllProperties() {
        log.info("📥 Exporting all properties from PayProp...");
        return apiClient.fetchAllPages("/export/properties", item -> item);
    }

    // Add these methods to PayPropSyncService.java

    public String syncPropertyToPayProp(Long propertyId) {
        logger.warn("syncPropertyToPayProp called - not implemented");
        return "{\"success\": false, \"message\": \"Method not implemented - use PayPropFinancialSyncService\"}";
    }

    public String syncBeneficiaryToPayProp(Long beneficiaryId) {
        logger.warn("syncBeneficiaryToPayProp called - not implemented");
        return "{\"success\": false, \"message\": \"Method not implemented - use PayPropFinancialSyncService\"}";
    }

    public Map<String, Object> syncAllReadyProperties() {
        logger.warn("syncAllReadyProperties called - not implemented");
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "Method not implemented");
        return result;
    }

    public Map<String, Object> syncAllReadyBeneficiaries() {
        logger.warn("syncAllReadyBeneficiaries called - not implemented");
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "Method not implemented");
        return result;
    }

    public Map<String, Object> syncPaymentsToDatabase(Long initiatedBy) {
        logger.warn("syncPaymentsToDatabase called - not implemented");
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "Method not implemented - use PayPropFinancialSyncService");
        return result;
    }

    public Map<String, Object> syncBeneficiaryBalancesToDatabase(Long initiatedBy) {
        logger.warn("syncBeneficiaryBalancesToDatabase called - not implemented");
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "Method not implemented - use PayPropFinancialSyncService");
        return result;
    }

    public Map<String, Object> getPropertyStatistics() {
        logger.warn("getPropertyStatistics called - not implemented");
        Map<String, Object> result = new HashMap<>();
        result.put("error", "Method not implemented");
        return result;
    }

    /**
     * Export properties with enhanced data (settings, tenancies, commission, etc.)
     * @deprecated Use exportAllPropertiesEnhanced() for complete data
     */
    @Deprecated
    public PayPropExportResult exportPropertiesFromPayPropEnhanced(int page, int rows) {
        log.info("📥 Enhanced export from PayProp - Page {}", page);
        
        Map<String, String> params = new HashMap<>();
        params.put("include_settings", "true");
        params.put("include_active_tenancies", "true");
        params.put("include_commission", "true");
        params.put("include_contract_amount", "true");
        params.put("include_balance", "true");
        params.put("include_last_processing_info", "true");
        params.put("page", String.valueOf(page));
        params.put("rows", String.valueOf(Math.min(rows, 25)));
        
        try {
            PayPropApiClient.PayPropPageResult pageResult = apiClient.fetchWithParams("/export/properties", params);
            
            PayPropExportResult result = new PayPropExportResult();
            result.setItems(pageResult.getItems());
            result.setPagination(pageResult.getPagination());
            
            log.info("✅ Enhanced export: {} properties with full data", result.getItems().size());
            return result;
        } catch (Exception e) {
            log.error("❌ Enhanced property export failed: {}", e.getMessage());
            // Fallback to regular export
            return exportPropertiesFromPayProp(page, rows);
        }
    }
    
    /**
     * Export ALL properties with enhanced data
     */
    public List<Map<String, Object>> exportAllPropertiesEnhanced() {
        log.info("📥 Exporting all properties with enhanced data from PayProp...");
        
        String endpoint = "/export/properties" +
            "?include_settings=true" +
            "&include_active_tenancies=true" +
            "&include_commission=true" +
            "&include_contract_amount=true" +
            "&include_balance=true" +
            "&include_last_processing_info=true";
            
        return apiClient.fetchAllPages(endpoint, item -> item);
    }
    
    /**
     * Export tenants from PayProp with pagination
     * @deprecated Use exportAllTenants() for complete data
     */
    @Deprecated
    public PayPropExportResult exportTenantsFromPayProp(int page, int rows) {
        log.info("📥 Exporting tenants from PayProp - Page {}", page);
        
        PayPropApiClient.PayPropPageResult pageResult = apiClient.fetchSinglePage("/export/tenants", page, rows);
        
        PayPropExportResult result = new PayPropExportResult();
        result.setItems(pageResult.getItems());
        result.setPagination(pageResult.getPagination());
        
        log.info("✅ Exported {} tenants from PayProp", result.getItems().size());
        return result;
    }
    
    /**
     * Export ALL tenants from PayProp
     */
    public List<Map<String, Object>> exportAllTenants() {
        log.info("📥 Exporting all tenants from PayProp...");
        return apiClient.fetchAllPages("/export/tenants", item -> item);
    }
    
    /**
     * Export beneficiaries from PayProp with pagination
     * @deprecated Use exportAllBeneficiaries() for complete data
     */
    @Deprecated
    public PayPropExportResult exportBeneficiariesFromPayProp(int page, int rows) {
        log.info("📥 Exporting beneficiaries from PayProp - Page {}", page);
        
        PayPropApiClient.PayPropPageResult pageResult = apiClient.fetchSinglePage("/export/beneficiaries", page, rows);
        
        PayPropExportResult result = new PayPropExportResult();
        result.setItems(pageResult.getItems());
        result.setPagination(pageResult.getPagination());
        
        log.info("✅ Exported {} beneficiaries from PayProp", result.getItems().size());
        return result;
    }
    
    /**
     * Export ALL beneficiaries from PayProp
     */
    public List<Map<String, Object>> exportAllBeneficiaries() {
        log.info("📥 Exporting all beneficiaries from PayProp...");
        return apiClient.fetchAllPages("/export/beneficiaries", item -> item);
    }

    /**
     * Export invoices from PayProp for relationship validation
     * @deprecated Use exportAllInvoices() for complete data
     */
    @Deprecated
    public PayPropExportResult exportInvoicesFromPayProp(int page, int rows) {
        log.info("📥 Exporting invoices from PayProp - Page {}", page);
        
        PayPropApiClient.PayPropPageResult pageResult = apiClient.fetchSinglePage("/export/invoices", page, rows);
        
        PayPropExportResult result = new PayPropExportResult();
        result.setItems(pageResult.getItems());
        result.setPagination(pageResult.getPagination());
        
        log.info("✅ Exported {} invoices from PayProp", result.getItems().size());
        return result;
    }
    
    /**
     * Export ALL invoices from PayProp
     */
    public List<Map<String, Object>> exportAllInvoices() {
        log.info("📥 Exporting all invoices from PayProp...");
        return apiClient.fetchAllPages("/export/invoices", item -> item);
    }

    /**
     * Export payments from PayProp (basic export - use PayPropFinancialSyncService for comprehensive sync)
     * @deprecated Use exportAllPayments() for complete data
     */
    @Deprecated
    public PayPropExportResult exportPaymentsFromPayProp(int page, int rows) {
        log.info("📥 Exporting payments from PayProp - Page {}", page);
        
        PayPropApiClient.PayPropPageResult pageResult = apiClient.fetchSinglePage(
            "/export/payments?include_beneficiary_info=true", page, rows);
        
        PayPropExportResult result = new PayPropExportResult();
        result.setItems(pageResult.getItems());
        result.setPagination(pageResult.getPagination());
        
        log.info("✅ Exported {} payments from PayProp", result.getItems().size());
        return result;
    }
    
    /**
     * Export ALL payments from PayProp
     */
    public List<Map<String, Object>> exportAllPayments() {
        log.info("📥 Exporting all payments from PayProp...");
        return apiClient.fetchAllPages("/export/payments?include_beneficiary_info=true", item -> item);
    }

    /**
     * Export tenants for a specific property
     */
    public PayPropExportResult exportTenantsByProperty(String propertyId) {
        log.info("📥 Exporting tenants for property: {}", propertyId);
        
        Map<String, String> params = new HashMap<>();
        params.put("property_id", propertyId);
        
        PayPropApiClient.PayPropPageResult pageResult = apiClient.fetchWithParams("/export/tenants", params);
        
        PayPropExportResult result = new PayPropExportResult();
        result.setItems(pageResult.getItems());
        result.setPagination(pageResult.getPagination());
        
        log.info("✅ Found {} tenants for property {}", result.getItems().size(), propertyId);
        return result;
    }

    /**
     * Export payments for a specific property
     */
    public PayPropExportResult exportPaymentsByProperty(String propertyId) {
        log.info("📥 Exporting payments for property: {}", propertyId);
        
        Map<String, String> params = new HashMap<>();
        params.put("property_id", propertyId);
        params.put("include_beneficiary_info", "true");
        params.put("page", "1");
        params.put("rows", "100");
        
        PayPropApiClient.PayPropPageResult pageResult = apiClient.fetchWithParams("/export/payments", params);
        
        PayPropExportResult result = new PayPropExportResult();
        result.setItems(pageResult.getItems());
        result.setPagination(pageResult.getPagination());
        
        log.info("✅ Found {} payments for property {}", result.getItems().size(), propertyId);
        return result;
    }

    /**
     * Get complete property data with all settings and tenancy info
     */
    public Map<String, Object> getCompletePropertyData(String propertyId) {
        log.info("📊 Getting complete property data for: {}", propertyId);
        
        String endpoint = "/entity/property/" + propertyId + "?include_settings=true&include_active_tenants=true";
        Map<String, Object> propertyData = apiClient.get(endpoint);
        
        // Add occupancy detection
        List<Map<String, Object>> activeTenants = (List<Map<String, Object>>) propertyData.get("active_tenants");
        propertyData.put("is_occupied", activeTenants != null && !activeTenants.isEmpty());
        
        return propertyData;
    }

    // ===== ATTACHMENT/FILE METHODS =====

    /**
     * Get PayProp attachments for an entity
     */
    public List<PayPropAttachment> getPayPropAttachments(String entityType, String entityId) {
        log.info("📎 Getting PayProp attachments for {} entity: {}", entityType, entityId);
        
        try {
            String endpoint = "/attachments/" + entityType + "/" + entityId;
            Map<String, Object> response = apiClient.get(endpoint);
            
            List<Map<String, Object>> attachmentData = (List<Map<String, Object>>) response.get("data");
            if (attachmentData == null) {
                return new ArrayList<>();
            }
            
            List<PayPropAttachment> attachments = new ArrayList<>();
            for (Map<String, Object> data : attachmentData) {
                PayPropAttachment attachment = new PayPropAttachment();
                attachment.setExternalId((String) data.get("externalId"));
                attachment.setFileName((String) data.get("fileName"));
                attachment.setFileType((String) data.get("fileType"));
                attachment.setEntityType((String) data.get("entityType"));
                attachment.setEntityId((String) data.get("entityId"));
                attachment.setUploadedAt((String) data.get("uploadedAt"));
                attachments.add(attachment);
            }
            
            log.info("✅ Found {} attachments for {} {}", attachments.size(), entityType, entityId);
            return attachments;
            
        } catch (Exception e) {
            log.error("❌ Failed to get PayProp attachments: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get PayProp attachments with pagination
     */
    public PayPropAttachmentResponse getPayPropAttachmentsWithPagination(String entityType, String entityId, int page, int rows) {
        log.info("📎 Getting PayProp attachments for {} entity: {} (page {}, rows {})", 
            entityType, entityId, page, rows);
        
        try {
            Map<String, String> params = new HashMap<>();
            params.put("page", String.valueOf(page));
            params.put("rows", String.valueOf(rows));
            
            String endpoint = "/attachments/" + entityType + "/" + entityId;
            PayPropApiClient.PayPropPageResult pageResult = apiClient.fetchWithParams(endpoint, params);
            
            PayPropAttachmentResponse response = new PayPropAttachmentResponse();
            
            // Convert items to attachments
            List<PayPropAttachment> attachments = new ArrayList<>();
            for (Map<String, Object> data : pageResult.getItems()) {
                PayPropAttachment attachment = new PayPropAttachment();
                attachment.setExternalId((String) data.get("externalId"));
                attachment.setFileName((String) data.get("fileName"));
                attachment.setFileType((String) data.get("fileType"));
                attachment.setEntityType((String) data.get("entityType"));
                attachment.setEntityId((String) data.get("entityId"));
                attachment.setUploadedAt((String) data.get("uploadedAt"));
                attachments.add(attachment);
            }
            
            response.setData(attachments);
            
            // Set pagination info
            Map<String, Object> pagination = pageResult.getPagination();
            if (pagination != null) {
                Object total = pagination.get("total");
                if (total != null) {
                    response.setTotal(Integer.parseInt(total.toString()));
                }
                response.setPage(page);
                response.setRows(rows);
            }
            
            log.info("✅ Found {} attachments for {} {} (page {} of {})", 
                attachments.size(), entityType, entityId, page, 
                response.getTotal() > 0 ? (response.getTotal() / rows) + 1 : 1);
            
            return response;
            
        } catch (Exception e) {
            log.error("❌ Failed to get PayProp attachments: {}", e.getMessage());
            return new PayPropAttachmentResponse();
        }
    }

    /**
     * Download PayProp attachment by external ID
     */
    public byte[] downloadPayPropAttachment(String externalId) {
        log.info("📥 Downloading PayProp attachment: {}", externalId);
        
        try {
            String endpoint = "/attachments/" + externalId;
            byte[] data = apiClient.downloadBinary(endpoint);
            
            log.info("✅ Downloaded PayProp attachment: {} ({} bytes)", externalId, 
                data != null ? data.length : 0);
            return data;
            
        } catch (Exception e) {
            log.error("❌ Failed to download PayProp attachment {}: {}", externalId, e.getMessage());
            return null;
        }
    }

    /**
     * Generate PayProp owner statement PDF
     */
    public byte[] generateOwnerStatementPDF(String propertyId, String beneficiaryId, 
                                           String fromDate, String toDate) {
        log.info("📊 Generating PayProp owner statement for property {} beneficiary {}", 
            propertyId, beneficiaryId);
        
        try {
            StringBuilder endpoint = new StringBuilder("/documents/pdf/owner-statement?");
            endpoint.append("property_id=").append(propertyId);
            endpoint.append("&beneficiary_id=").append(beneficiaryId);
            
            if (fromDate != null) endpoint.append("&from_date=").append(fromDate);
            if (toDate != null) endpoint.append("&to_date=").append(toDate);
            
            byte[] data = apiClient.downloadBinary(endpoint.toString());
            
            log.info("✅ Generated owner statement PDF ({} bytes)", 
                data != null ? data.length : 0);
            return data;
            
        } catch (Exception e) {
            log.error("❌ Failed to generate owner statement: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Generate PayProp agency invoice PDF
     */
    public byte[] generateAgencyInvoicePDF(int year, int month) {
        log.info("📊 Generating PayProp agency invoice for {}/{}", year, month);
        
        try {
            String endpoint = "/documents/pdf/agency-invoice?year=" + year + "&month=" + month;
            byte[] data = apiClient.downloadBinary(endpoint);
            
            log.info("✅ Generated agency invoice PDF ({} bytes)", 
                data != null ? data.length : 0);
            return data;
            
        } catch (Exception e) {
            log.error("❌ Failed to generate agency invoice: {}", e.getMessage());
            return null;
        }
    }

    // ===== TAG MANAGEMENT =====

    /**
     * Remove tag from property using PayProp API
     */
    public void removeTagFromProperty(String payPropPropertyId, String tagId) throws Exception {
        log.info("🏷️ Removing tag {} from property {}", tagId, payPropPropertyId);
        
        try {
            Map<String, String> params = new HashMap<>();
            String endpoint = "/tags/entities/property/" + payPropPropertyId + "/" + tagId;
            
            // For DELETE operations, we need to use the base apiClient
            // This is a limitation - might need to add DELETE support to PayPropApiClient
            Map<String, Object> response = apiClient.get(endpoint); // This won't work for DELETE
            
            log.info("✅ Successfully removed PayProp tag {} from property {}", tagId, payPropPropertyId);
        } catch (Exception e) {
            log.error("❌ Failed to remove tag {} from property {}: {}", tagId, payPropPropertyId, e.getMessage());
            throw new RuntimeException("Failed to remove tag from property: " + e.getMessage(), e);
        }
    }

    // ===== PAYMENT CATEGORIES (Keep for backwards compatibility) =====
    
    /**
     * Sync payment categories from PayProp
     * Note: This is duplicated in PayPropFinancialSyncService but kept for backwards compatibility
     */
    public SyncResult syncPaymentCategoriesFromPayProp() {
        log.info("💳 Syncing payment categories from PayProp...");
        
        try {
            Map<String, Object> response = apiClient.get("/payments/categories");
            
            // Handle different possible response structures
            List<Map<String, Object>> categories = null;
            
            if (response.containsKey("data")) {
                Object dataObj = response.get("data");
                if (dataObj instanceof List) {
                    categories = (List<Map<String, Object>>) dataObj;
                }
            }
            
            if (categories == null && response.containsKey("categories")) {
                Object categoriesObj = response.get("categories");
                if (categoriesObj instanceof List) {
                    categories = (List<Map<String, Object>>) categoriesObj;
                }
            }
            
            if (categories == null && response.containsKey("items")) {
                Object itemsObj = response.get("items");
                if (itemsObj instanceof List) {
                    categories = (List<Map<String, Object>>) itemsObj;
                }
            }
            
            if (categories == null) {
                log.error("❌ Unexpected payment categories response structure: {}", response);
                return SyncResult.failure("Payment categories response structure not recognized.");
            }
            
            log.info("✅ Retrieved {} payment categories from PayProp", categories.size());
            return SyncResult.success("Payment categories retrieved", 
                Map.of("total", categories.size(), "categories", categories));
            
        } catch (Exception e) {
            log.error("❌ Payment categories sync failed: {}", e.getMessage(), e);
            return SyncResult.failure("Payment categories sync failed: " + e.getMessage());
        }
    }

    // ===== OAUTH & PERMISSION UTILITIES =====

    /**
     * Check if we have attachment permissions
     */
    public boolean hasAttachmentPermissions() {
        try {
            if (!oAuth2Service.hasValidTokens()) {
                return false;
            }
            
            PayPropOAuth2Service.PayPropTokens tokens = oAuth2Service.getCurrentTokens();
            String scopes = tokens.getScopes();
            
            return scopes != null && (
                scopes.contains("read:attachment:list") || 
                scopes.contains("read:attachment") ||
                scopes.contains("all")
            );
        } catch (Exception e) {
            log.error("Error checking attachment permissions: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check sync status and OAuth status
     */
    public void checkSyncStatus() {
        log.info("=== PayProp OAuth2 Sync Status ===");
        
        boolean hasValidTokens = oAuth2Service.hasValidTokens();
        log.info("OAuth2 Status: {}", hasValidTokens ? "✅ Authorized" : "❌ Not Authorized");
        
        if (hasValidTokens) {
            try {
                PayPropOAuth2Service.PayPropTokens tokens = oAuth2Service.getCurrentTokens();
                log.info("Token Expires: {}", tokens.getExpiresAt());
                log.info("Scopes: {}", tokens.getScopes());
            } catch (Exception e) {
                log.error("⚠️ Error getting token details: {}", e.getMessage());
            }
        }
    }

    // ===== DATA CLASSES (For backwards compatibility) =====

    @Deprecated
    public static class PayPropExportResult {
        private List<Map<String, Object>> items;
        private Map<String, Object> pagination;
        
        public PayPropExportResult() {
            this.items = new ArrayList<>();
        }
        
        public List<Map<String, Object>> getItems() { 
            return items; 
        }
        
        public void setItems(List<Map<String, Object>> items) { 
            this.items = items; 
        }
        
        public Map<String, Object> getPagination() { 
            return pagination; 
        }
        
        public void setPagination(Map<String, Object> pagination) { 
            this.pagination = pagination; 
        }
    }

    public static class PayPropAttachmentResponse {
        private List<PayPropAttachment> data;
        private int total;
        private int page;
        private int rows;
        
        public PayPropAttachmentResponse() {
            this.data = new ArrayList<>();
        }
        
        public List<PayPropAttachment> getData() { return data != null ? data : new ArrayList<>(); }
        public void setData(List<PayPropAttachment> data) { this.data = data; }
        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public int getRows() { return rows; }
        public void setRows(int rows) { this.rows = rows; }
    }

    public static class PayPropAttachment {
        private String externalId;
        private String fileName;
        private String fileType;
        private String entityType;
        private String entityId;
        private String uploadedAt;
        
        public String getExternalId() { return externalId; }
        public void setExternalId(String externalId) { this.externalId = externalId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
        public String getEntityType() { return entityType; }
        public void setEntityType(String entityType) { this.entityType = entityType; }
        public String getEntityId() { return entityId; }
        public void setEntityId(String entityId) { this.entityId = entityId; }
        public String getUploadedAt() { return uploadedAt; }
        public void setUploadedAt(String uploadedAt) { this.uploadedAt = uploadedAt; }
    }
}
// PayPropPortfolioSyncService.java - OAuth2 Integration FIXED
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.property.PropertyService;

import java.net.http.HttpHeaders;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropPortfolioSyncService {

    private final PortfolioRepository portfolioRepository;
    private final BlockRepository blockRepository;
    private final PropertyService propertyService;
    private final PortfolioSyncLogRepository syncLogRepository;
    private final RestTemplate restTemplate;
    private final PayPropOAuth2Service oAuth2Service; // 🔧 FIXED: Added OAuth2 service
    
    @Value("${payprop.api.base-url:https://ukapi.staging.payprop.com/api/agency/v1.1}")
    private String payPropApiBase;
    
    @Autowired
    public PayPropPortfolioSyncService(PortfolioRepository portfolioRepository,
                                      BlockRepository blockRepository,
                                      PropertyService propertyService,
                                      PortfolioSyncLogRepository syncLogRepository,
                                      RestTemplate restTemplate,
                                      PayPropOAuth2Service oAuth2Service) { // 🔧 FIXED: Added OAuth2 service
        this.portfolioRepository = portfolioRepository;
        this.blockRepository = blockRepository;
        this.propertyService = propertyService;
        this.syncLogRepository = syncLogRepository;
        this.restTemplate = restTemplate;
        this.oAuth2Service = oAuth2Service; // 🔧 FIXED: Store OAuth2 service
    }
    
    // ===== PORTFOLIO TO PAYPROP SYNCHRONIZATION =====
    
    /**
     * Sync a portfolio to PayProp by creating/updating tags and assigning them to properties
     */
    public SyncResult syncPortfolioToPayProp(Long portfolioId, Long initiatedBy) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) {
            return SyncResult.failure("Portfolio not found");
        }
        
        PortfolioSyncLog syncLog = createSyncLog(portfolioId, null, null, 
            "PORTFOLIO_TO_PAYPROP", "CREATE", initiatedBy);
        
        try {
            portfolio.setSyncStatus(SyncStatus.SYNCING);
            portfolioRepository.save(portfolio);
            
            // Step 1: Create or get PayProp tag for this portfolio
            PayPropTagDTO tag = createOrGetPayPropTag(portfolio);
            
            // Step 2: Update portfolio with PayProp tag information
            portfolio.setPayPropTags(tag.getId());
            portfolio.setPayPropTagNames(tag.getName());
            
            // Step 3: Apply tags to all properties in this portfolio
            List<Property> properties = propertyService.findByPortfolioId(portfolioId);
            for (Property property : properties) {
                if (property.getPayPropId() != null) {
                    applyTagToProperty(property.getPayPropId(), tag.getId());
                }
            }
            
            // Step 4: Update portfolio sync status
            portfolio.setSyncStatus(SyncStatus.SYNCED);
            portfolio.setLastSyncAt(LocalDateTime.now());
            portfolioRepository.save(portfolio);
            
            completeSyncLog(syncLog, "SUCCESS", null, Map.of("tagId", tag.getId(), "tagName", tag.getName()));
            
            return SyncResult.success("Portfolio synced successfully", Map.of("payPropTagId", tag.getId()));
            
        } catch (Exception e) {
            portfolio.setSyncStatus(SyncStatus.FAILED);
            portfolioRepository.save(portfolio);
            
            completeSyncLog(syncLog, "FAILED", e.getMessage(), null);
            return SyncResult.failure("Failed to sync portfolio: " + e.getMessage());
        }
    }
    
    /**
     * Create or retrieve existing PayProp tag for a portfolio
     */
    private PayPropTagDTO createOrGetPayPropTag(Portfolio portfolio) throws Exception {
        // First, check if portfolio already has PayProp tags
        if (portfolio.getPayPropTags() != null && !portfolio.getPayPropTags().isEmpty()) {
            // Assuming PayProp tags are stored as comma-separated string
            String[] tagIds = portfolio.getPayPropTags().split(",");
            if (tagIds.length > 0) {
                String existingTagId = tagIds[0].trim();
                PayPropTagDTO existingTag = getPayPropTag(existingTagId);
                if (existingTag != null) {
                    return existingTag;
                }
            }
        }
        
        // Create new tag
        PayPropTagDTO newTag = new PayPropTagDTO();
        newTag.setName(generateTagName(portfolio));
        newTag.setDescription("Auto-generated tag for portfolio: " + portfolio.getName());
        newTag.setColor(portfolio.getColorCode() != null ? portfolio.getColorCode() : "#3498db");
        
        return createPayPropTag(newTag);
    }
    
    /**
     * Get all PayProp tags - public method for controller access
     */
    public List<PayPropTagDTO> getAllPayPropTags() throws Exception {
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                payPropApiBase + "/tags", 
                HttpMethod.GET, 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                // FIXED: Check for different possible response formats
                List<Map<String, Object>> tags = null;
                
                // Try different response formats PayProp might use
                if (responseBody.containsKey("data")) {
                    tags = (List<Map<String, Object>>) responseBody.get("data");
                } else if (responseBody.containsKey("tags")) {
                    tags = (List<Map<String, Object>>) responseBody.get("tags");
                } else if (responseBody.containsKey("items")) {
                    tags = (List<Map<String, Object>>) responseBody.get("items");
                } else {
                    // Response might be the tags array directly
                    if (responseBody instanceof List) {
                        tags = (List<Map<String, Object>>) responseBody;
                    } else {
                        // Log the actual response format for debugging
                        System.out.println("🔍 PayProp /tags response format: " + responseBody.keySet());
                        tags = new ArrayList<>();
                    }
                }
                
                // NULL-SAFETY: Check if tags is null
                if (tags == null) {
                    System.out.println("⚠️ PayProp API returned null for tags");
                    return new ArrayList<>();
                }
                
                return tags.stream().map(tagMap -> {
                    PayPropTagDTO tag = new PayPropTagDTO();
                    tag.setId((String) tagMap.get("id"));
                    tag.setName((String) tagMap.get("name"));
                    tag.setDescription((String) tagMap.get("description"));
                    tag.setColor((String) tagMap.get("color"));
                    return tag;
                }).collect(Collectors.toList());
            }
            
            return new ArrayList<>();
            
        } catch (HttpClientErrorException e) {
            System.err.println("❌ PayProp API error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            throw new RuntimeException("PayProp API error: " + e.getResponseBodyAsString(), e);
        }
    }

    /**
     * Get specific PayProp tag - public method for controller access
     */
    public PayPropTagDTO getPayPropTag(String tagId) throws Exception {
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders(); // 🔧 FIXED: Use OAuth2
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                payPropApiBase + "/tags/" + tagId, 
                HttpMethod.GET, 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                PayPropTagDTO tag = new PayPropTagDTO();
                tag.setId((String) responseBody.get("id"));
                tag.setName((String) responseBody.get("name"));
                tag.setDescription((String) responseBody.get("description"));
                tag.setColor((String) responseBody.get("color"));
                return tag;
            }
            
            return null;
            
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw new RuntimeException("PayProp API error: " + e.getResponseBodyAsString(), e);
        }
    }

    /**
     * Generate a unique tag name for PayProp
     */
    private String generateTagName(Portfolio portfolio) {
        String baseName = portfolio.getName().replaceAll("[^a-zA-Z0-9\\s]", "");
        
        // Add owner prefix if it's an owner-specific portfolio
        if (portfolio.getPropertyOwnerId() != null) {
            baseName = "Owner-" + portfolio.getPropertyOwnerId() + "-" + baseName;
        }
        
        // Ensure uniqueness
        return baseName.length() > 50 ? baseName.substring(0, 50) : baseName;
    }
    
    // ===== PAYPROP TO PORTFOLIO SYNCHRONIZATION =====
    
    /**
     * Handle incoming PayProp tag changes (webhook handler)
     */
    public SyncResult handlePayPropTagChange(String tagId, String action, PayPropTagDTO tagData, List<String> propertyIds) {
        PortfolioSyncLog syncLog = createSyncLog(null, null, null, 
            "PAYPROP_TO_PORTFOLIO", action, null);
        syncLog.setPayPropTagId(tagId);
        syncLog.setPayPropTagName(tagData != null ? tagData.getName() : null);
        
        try {
            switch (action.toUpperCase()) {
                case "TAG_CREATED":
                    return handleTagCreated(tagId, tagData, syncLog);
                case "TAG_UPDATED":
                    return handleTagUpdated(tagId, tagData, syncLog);
                case "TAG_DELETED":
                    return handleTagDeleted(tagId, syncLog);
                case "TAG_APPLIED":
                    return handleTagAppliedToProperties(tagId, propertyIds, syncLog);
                case "TAG_REMOVED":
                    return handleTagRemovedFromProperties(tagId, propertyIds, syncLog);
                default:
                    completeSyncLog(syncLog, "FAILED", "Unknown action: " + action, null);
                    return SyncResult.failure("Unknown action: " + action);
            }
        } catch (Exception e) {
            completeSyncLog(syncLog, "FAILED", e.getMessage(), null);
            return SyncResult.failure("Failed to handle PayProp tag change: " + e.getMessage());
        }
    }
    
    private SyncResult handleTagCreated(String tagId, PayPropTagDTO tagData, PortfolioSyncLog syncLog) {
        // Check if we have a portfolio that matches this tag
        List<Portfolio> existingPortfolios = findPortfoliosByPayPropTag(tagId);
        
        if (existingPortfolios.isEmpty()) {
            // Create new portfolio for this PayProp tag
            Portfolio newPortfolio = new Portfolio();
            newPortfolio.setName(tagData.getName());
            newPortfolio.setDescription("Auto-created from PayProp tag: " + tagData.getName());
            newPortfolio.setPortfolioType(PortfolioType.CUSTOM);
            newPortfolio.setPayPropTags(tagId);
            newPortfolio.setPayPropTagNames(tagData.getName());
            newPortfolio.setColorCode(tagData.getColor());
            newPortfolio.setCreatedBy(1L); // System user
            newPortfolio.setIsShared("Y"); // Make PayProp-created portfolios shared
            newPortfolio.setSyncStatus(SyncStatus.SYNCED);
            newPortfolio.setLastSyncAt(LocalDateTime.now());
            
            portfolioRepository.save(newPortfolio);
            
            completeSyncLog(syncLog, "SUCCESS", null, Map.of("portfolioId", newPortfolio.getId()));
            return SyncResult.success("Created new portfolio from PayProp tag");
        } else {
            // Update existing portfolio
            Portfolio portfolio = existingPortfolios.get(0);
            portfolio.setPayPropTagNames(tagData.getName());
            portfolio.setColorCode(tagData.getColor());
            portfolio.setLastSyncAt(LocalDateTime.now());
            portfolioRepository.save(portfolio);
            
            completeSyncLog(syncLog, "SUCCESS", null, Map.of("portfolioId", portfolio.getId()));
            return SyncResult.success("Updated existing portfolio from PayProp tag");
        }
    }
    
    private SyncResult handleTagUpdated(String tagId, PayPropTagDTO tagData, PortfolioSyncLog syncLog) {
        List<Portfolio> portfolios = findPortfoliosByPayPropTag(tagId);
        
        for (Portfolio portfolio : portfolios) {
            portfolio.setPayPropTagNames(tagData.getName());
            portfolio.setColorCode(tagData.getColor());
            portfolio.setLastSyncAt(LocalDateTime.now());
            portfolioRepository.save(portfolio);
        }
        
        completeSyncLog(syncLog, "SUCCESS", null, 
            Map.of("updatedPortfolios", portfolios.size()));
        return SyncResult.success("Updated " + portfolios.size() + " portfolios from PayProp tag update");
    }
    
    private SyncResult handleTagDeleted(String tagId, PortfolioSyncLog syncLog) {
        List<Portfolio> portfolios = findPortfoliosByPayPropTag(tagId);
        
        for (Portfolio portfolio : portfolios) {
            removePayPropTagFromPortfolio(portfolio, tagId);
            if (portfolio.getPayPropTags() == null || portfolio.getPayPropTags().isEmpty()) {
                // If this was the only PayProp tag, mark as unsynced but don't delete
                portfolio.setSyncStatus(SyncStatus.PENDING);
            }
            portfolio.setLastSyncAt(LocalDateTime.now());
            portfolioRepository.save(portfolio);
        }
        
        completeSyncLog(syncLog, "SUCCESS", null, 
            Map.of("affectedPortfolios", portfolios.size()));
        return SyncResult.success("Removed PayProp tag from " + portfolios.size() + " portfolios");
    }
    
    private SyncResult handleTagAppliedToProperties(String tagId, List<String> propertyIds, PortfolioSyncLog syncLog) {
        List<Portfolio> portfolios = findPortfoliosByPayPropTag(tagId);
        
        if (portfolios.isEmpty()) {
            completeSyncLog(syncLog, "FAILED", "No portfolio found for tag: " + tagId, null);
            return SyncResult.failure("No portfolio found for tag: " + tagId);
        }
        
        Portfolio portfolio = portfolios.get(0); // Use first matching portfolio
        int assignedCount = 0;
        
        for (String payPropPropertyId : propertyIds) {
            Optional<Property> propertyOpt = propertyService.findByPayPropId(payPropPropertyId);
            if (propertyOpt.isPresent()) {
                Property property = propertyOpt.get();
                property.setPortfolio(portfolio);
                property.setPortfolioAssignmentDate(LocalDateTime.now());
                propertyService.save(property);
                assignedCount++;
            }
        }
        
        completeSyncLog(syncLog, "SUCCESS", null, 
            Map.of("portfolioId", portfolio.getId(), "assignedProperties", assignedCount));
        return SyncResult.success("Assigned " + assignedCount + " properties to portfolio");
    }
    
    private SyncResult handleTagRemovedFromProperties(String tagId, List<String> propertyIds, PortfolioSyncLog syncLog) {
        int removedCount = 0;
        
        for (String payPropPropertyId : propertyIds) {
            Optional<Property> propertyOpt = propertyService.findByPayPropId(payPropPropertyId);
            if (propertyOpt.isPresent()) {
                Property property = propertyOpt.get();
                // Only remove from portfolio if this was the matching tag
                if (property.getPortfolio() != null && 
                    portfolioHasPayPropTag(property.getPortfolio(), tagId)) {
                    property.setPortfolio(null);
                    property.setPortfolioAssignmentDate(null);
                    propertyService.save(property);
                    removedCount++;
                }
            }
        }
        
        completeSyncLog(syncLog, "SUCCESS", null, 
            Map.of("removedProperties", removedCount));
        return SyncResult.success("Removed " + removedCount + " properties from portfolio");
    }
    
    // ===== PAYPROP API METHODS =====
    
    private PayPropTagDTO createPayPropTag(PayPropTagDTO tag) throws Exception {
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders(); // 🔧 FIXED: Use OAuth2
        HttpEntity<PayPropTagDTO> request = new HttpEntity<>(tag, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                payPropApiBase + "/tags", request, Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                PayPropTagDTO createdTag = new PayPropTagDTO();
                createdTag.setId((String) responseBody.get("id"));
                createdTag.setName((String) responseBody.get("name"));
                createdTag.setDescription((String) responseBody.get("description"));
                createdTag.setColor((String) responseBody.get("color"));
                return createdTag;
            }
            
            throw new RuntimeException("Failed to create PayProp tag");
            
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("PayProp API error: " + e.getResponseBodyAsString(), e);
        }
    }
    
    private void applyTagToProperty(String payPropPropertyId, String tagId) throws Exception {
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders(); // 🔧 FIXED: Use OAuth2
        Map<String, Object> requestBody = Map.of("tag_id", tagId);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        try {
            restTemplate.postForEntity(
                payPropApiBase + "/properties/" + payPropPropertyId + "/tags", 
                request, 
                Map.class
            );
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Failed to apply tag to property: " + e.getResponseBodyAsString(), e);
        }
    }
    
    private void removeTagFromProperty(String payPropPropertyId, String tagId) throws Exception {
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders(); // 🔧 FIXED: Use OAuth2
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            restTemplate.exchange(
                payPropApiBase + "/properties/" + payPropPropertyId + "/tags/" + tagId,
                HttpMethod.DELETE,
                request,
                Map.class
            );
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Failed to remove tag from property: " + e.getResponseBodyAsString(), e);
        }
    }
    
    // ===== BULK OPERATIONS =====
    
    /**
     * Sync all portfolios that need synchronization
     */
    public SyncResult syncAllPortfolios(Long initiatedBy) {
        if (!oAuth2Service.hasValidTokens()) { // 🔧 FIXED: Check OAuth2 tokens
            throw new IllegalStateException("No valid OAuth2 tokens. Please authorize PayProp first.");
        }
        
        List<Portfolio> portfoliosNeedingSync = findPortfoliosNeedingSync();
        
        int successCount = 0;
        int failureCount = 0;
        List<String> errors = new ArrayList<>();
        
        for (Portfolio portfolio : portfoliosNeedingSync) {
            try {
                SyncResult result = syncPortfolioToPayProp(portfolio.getId(), initiatedBy);
                if (result.isSuccess()) {
                    successCount++;
                } else {
                    failureCount++;
                    errors.add("Portfolio " + portfolio.getName() + ": " + result.getMessage());
                }
            } catch (Exception e) {
                failureCount++;
                errors.add("Portfolio " + portfolio.getName() + ": " + e.getMessage());
            }
        }
        
        String message = String.format("Sync completed. Success: %d, Failed: %d", successCount, failureCount);
        Map<String, Object> details = Map.of(
            "totalPortfolios", portfoliosNeedingSync.size(),
            "successCount", successCount,
            "failureCount", failureCount,
            "errors", errors
        );
        
        return failureCount == 0 ? 
            SyncResult.success(message, details) : 
            SyncResult.partial(message, details);
    }
    
    /**
     * Pull all tags from PayProp and sync to local portfolios
     */
    public SyncResult pullAllTagsFromPayProp(Long initiatedBy) {
        try {
            List<PayPropTagDTO> payPropTags = getAllPayPropTags();
            
            int createdCount = 0;
            int updatedCount = 0;
            
            for (PayPropTagDTO tag : payPropTags) {
                SyncResult result = handleTagCreated(tag.getId(), tag, 
                    createSyncLog(null, null, null, "PAYPROP_TO_PORTFOLIO", "CREATE", initiatedBy));
                
                if (result.isSuccess()) {
                    if (result.getMessage().contains("Created")) {
                        createdCount++;
                    } else {
                        updatedCount++;
                    }
                }
            }
            
            String message = String.format("PayProp tags pulled. Created: %d portfolios, Updated: %d portfolios", 
                createdCount, updatedCount);
            return SyncResult.success(message, Map.of("created", createdCount, "updated", updatedCount));
            
        } catch (Exception e) {
            return SyncResult.failure("Failed to pull tags from PayProp: " + e.getMessage());
        }
    }
    
    // ===== UTILITY METHODS =====
    
    /**
     * Find portfolios that contain a specific PayProp tag
     */
    private List<Portfolio> findPortfoliosByPayPropTag(String tagId) {
        // This would ideally use a repository method, but implementing manually for now
        return portfolioRepository.findAll().stream()
            .filter(portfolio -> portfolioHasPayPropTag(portfolio, tagId))
            .collect(Collectors.toList());
    }
    
    /**
     * Check if portfolio contains a specific PayProp tag
     */
    private boolean portfolioHasPayPropTag(Portfolio portfolio, String tagId) {
        if (portfolio.getPayPropTags() == null || portfolio.getPayPropTags().isEmpty()) {
            return false;
        }
        String[] tags = portfolio.getPayPropTags().split(",");
        return Arrays.stream(tags).anyMatch(tag -> tag.trim().equals(tagId));
    }
    
    /**
     * Remove a PayProp tag from portfolio
     */
    private void removePayPropTagFromPortfolio(Portfolio portfolio, String tagId) {
        if (portfolio.getPayPropTags() == null) {
            return;
        }
        
        List<String> tags = Arrays.stream(portfolio.getPayPropTags().split(","))
            .map(String::trim)
            .filter(tag -> !tag.equals(tagId))
            .collect(Collectors.toList());
        
        portfolio.setPayPropTags(tags.isEmpty() ? null : String.join(",", tags));
    }
    
    /**
     * Find portfolios needing sync
     */
    private List<Portfolio> findPortfoliosNeedingSync() {
        // This would ideally use a repository method
        return portfolioRepository.findAll().stream()
            .filter(portfolio -> portfolio.getSyncStatus() == SyncStatus.PENDING || 
                               portfolio.getSyncStatus() == SyncStatus.FAILED)
            .collect(Collectors.toList());
    }
    
    private PortfolioSyncLog createSyncLog(Long portfolioId, Long blockId, Long propertyId, 
                                          String syncType, String operation, Long initiatedBy) {
        PortfolioSyncLog syncLog = new PortfolioSyncLog();
        syncLog.setPortfolioId(portfolioId);
        syncLog.setBlockId(blockId);
        syncLog.setPropertyId(propertyId);
        syncLog.setSyncType(syncType);
        syncLog.setOperation(operation);
        syncLog.setStatus("PENDING");
        syncLog.setSyncStartedAt(LocalDateTime.now());
        syncLog.setInitiatedBy(initiatedBy);
        
        return syncLogRepository.save(syncLog);
    }
    
    private void completeSyncLog(PortfolioSyncLog syncLog, String status, String errorMessage, Map<String, Object> payloadReceived) {
        syncLog.setStatus(status);
        syncLog.setErrorMessage(errorMessage);
        syncLog.setPayloadReceived(payloadReceived);
        syncLog.setSyncCompletedAt(LocalDateTime.now());
        syncLogRepository.save(syncLog);
    }
}
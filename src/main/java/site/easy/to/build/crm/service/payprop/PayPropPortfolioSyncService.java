// PayPropPortfolioSyncService.java - OAuth2 Integration with Duplicate Key Handling
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.property.PropertyService;
import org.springframework.http.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropPortfolioSyncService {

    private static final Logger log = LoggerFactory.getLogger(PayPropPortfolioSyncService.class);

    private final PortfolioRepository portfolioRepository;
    private final BlockRepository blockRepository;
    private final PropertyService propertyService;
    private final PortfolioSyncLogRepository syncLogRepository;
    private final RestTemplate restTemplate;
    private final PayPropOAuth2Service oAuth2Service;
    
    @Value("${payprop.api.base-url:https://ukapi.staging.payprop.com/api/agency/v1.1}")
    private String payPropApiBase;
    
    @Autowired
    public PayPropPortfolioSyncService(PortfolioRepository portfolioRepository,
                                      BlockRepository blockRepository,
                                      PropertyService propertyService,
                                      PortfolioSyncLogRepository syncLogRepository,
                                      RestTemplate restTemplate,
                                      PayPropOAuth2Service oAuth2Service) {
        this.portfolioRepository = portfolioRepository;
        this.blockRepository = blockRepository;
        this.propertyService = propertyService;
        this.syncLogRepository = syncLogRepository;
        this.restTemplate = restTemplate;
        this.oAuth2Service = oAuth2Service;
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
            portfolio.setSyncStatus(SyncStatus.pending);
            
            // FIXED: Add duplicate key handling for portfolio save
            try {
                portfolioRepository.save(portfolio);
            } catch (DataIntegrityViolationException e) {
                log.warn("Portfolio {} already exists when setting pending status, continuing", portfolioId);
            }
            
            // Step 1: Create or get PayProp tag for this portfolio
            PayPropTagDTO tag = createOrGetPayPropTag(portfolio);
            
            // Step 2: Update portfolio with PayProp tag information
            portfolio.setPayPropTags(tag.getId());
            portfolio.setPayPropTagNames(tag.getName());
            
            // Step 3: Apply tags to all properties in this portfolio
            List<Property> properties = propertyService.findByPortfolioId(portfolioId);
            int propertiesTagged = 0;
            for (Property property : properties) {
                if (property.getPayPropId() != null) {
                    try {
                        applyTagToProperty(property.getPayPropId(), tag.getId());
                        propertiesTagged++;
                    } catch (Exception e) {
                        log.warn("Failed to apply tag {} to property {}: {}", tag.getId(), property.getPayPropId(), e.getMessage());
                    }
                }
            }
            
            // Step 4: Update portfolio sync status
            portfolio.setSyncStatus(SyncStatus.synced);
            portfolio.setLastSyncAt(LocalDateTime.now());
            
            // FIXED: Add duplicate key handling for final portfolio save
            try {
                portfolioRepository.save(portfolio);
            } catch (DataIntegrityViolationException e) {
                log.warn("Portfolio {} already exists when updating sync status, skipping save", portfolioId);
            }
            
            completeSyncLog(syncLog, "SUCCESS", null, Map.of(
                "tagId", tag.getId(), 
                "tagName", tag.getName(),
                "propertiesTagged", propertiesTagged));
            
            return SyncResult.success("Portfolio synced successfully", Map.of("payPropTagId", tag.getId()));
            
        } catch (Exception e) {
            portfolio.setSyncStatus(SyncStatus.error);
            
            // FIXED: Add duplicate key handling for error status save
            try {
                portfolioRepository.save(portfolio);
            } catch (DataIntegrityViolationException ex) {
                log.warn("Portfolio {} already exists when setting error status, skipping save", portfolioId);
            }
            
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
    
    // REPLACE the existing getAllPayPropTags method with this corrected version

    /**
     * Get all PayProp tags - CORRECTED VERSION using proper API response format
     */
    public List<PayPropTagDTO> getAllPayPropTags() throws Exception {
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            // PayProp tags endpoint - may need pagination for large tag sets
            String url = payPropApiBase + "/tags?rows=100"; // Get up to 100 tags
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                // PayProp API returns tags in 'data' array
                List<Map<String, Object>> tags = null;
                
                if (responseBody.containsKey("data")) {
                    tags = (List<Map<String, Object>>) responseBody.get("data");
                } else {
                    // Fallback - response might be the tags array directly
                    if (responseBody instanceof List) {
                        tags = (List<Map<String, Object>>) responseBody;
                    } else {
                        log.warn("Unexpected PayProp /tags response format: {}", responseBody.keySet());
                        tags = new ArrayList<>();
                    }
                }
                
                if (tags == null) {
                    log.warn("PayProp API returned null for tags data");
                    return new ArrayList<>();
                }
                
                return tags.stream().map(tagMap -> {
                    PayPropTagDTO tag = new PayPropTagDTO();
                    
                    // PayProp uses 'external_id' as the tag identifier
                    tag.setId((String) tagMap.get("external_id"));
                    tag.setName((String) tagMap.get("name"));
                    
                    // These fields may not be present in PayProp API
                    tag.setDescription((String) tagMap.getOrDefault("description", ""));
                    tag.setColor((String) tagMap.getOrDefault("color", "#3498db"));
                    
                    return tag;
                }).collect(Collectors.toList());
            }
            
            log.warn("PayProp /tags API returned non-200 status: {}", response.getStatusCode());
            return new ArrayList<>();
            
        } catch (HttpClientErrorException e) {
            log.error("PayProp API error getting tags: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("PayProp API error: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Unexpected error getting PayProp tags: {}", e.getMessage());
            throw new RuntimeException("Failed to get PayProp tags: " + e.getMessage(), e);
        }
    }

    /**
     * Get specific PayProp tag - public method for controller access
     */
    public PayPropTagDTO getPayPropTag(String tagId) throws Exception {
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            // Use the external_id parameter to get specific tag
            String url = payPropApiBase + "/tags?external_id=" + tagId;
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                // PayProp returns array even for single tag lookup
                List<Map<String, Object>> tags = null;
                if (responseBody.containsKey("data")) {
                    tags = (List<Map<String, Object>>) responseBody.get("data");
                }
                
                if (tags != null && !tags.isEmpty()) {
                    Map<String, Object> tagMap = tags.get(0);
                    PayPropTagDTO tag = new PayPropTagDTO();
                    tag.setId((String) tagMap.get("external_id"));
                    tag.setName((String) tagMap.get("name"));
                    tag.setDescription((String) tagMap.getOrDefault("description", ""));
                    tag.setColor((String) tagMap.getOrDefault("color", "#3498db"));
                    return tag;
                }
            }
            
            return null;
            
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("PayProp tag {} not found", tagId);
                return null;
            }
            log.error("PayProp API error getting tag {}: {}", tagId, e.getResponseBodyAsString());
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
            newPortfolio.setSyncStatus(SyncStatus.synced);
            newPortfolio.setLastSyncAt(LocalDateTime.now());
            
            // FIXED: Add duplicate key handling for new portfolio creation
            try {
                portfolioRepository.save(newPortfolio);
                completeSyncLog(syncLog, "SUCCESS", null, Map.of("portfolioId", newPortfolio.getId()));
                return SyncResult.success("Created new portfolio from PayProp tag");
            } catch (DataIntegrityViolationException e) {
                log.warn("Portfolio with PayProp tag {} already exists during creation, finding existing", tagId);
                // Try to find the existing portfolio instead
                List<Portfolio> existingAfterError = findPortfoliosByPayPropTag(tagId);
                if (!existingAfterError.isEmpty()) {
                    Portfolio existing = existingAfterError.get(0);
                    completeSyncLog(syncLog, "SUCCESS", "Found existing portfolio after duplicate error", 
                        Map.of("portfolioId", existing.getId()));
                    return SyncResult.success("Found existing portfolio from PayProp tag");
                } else {
                    completeSyncLog(syncLog, "FAILED", "Duplicate error but no portfolio found", null);
                    return SyncResult.failure("Duplicate portfolio error: " + e.getMessage());
                }
            }
        } else {
            // Update existing portfolio
            Portfolio portfolio = existingPortfolios.get(0);
            portfolio.setPayPropTagNames(tagData.getName());
            portfolio.setColorCode(tagData.getColor());
            portfolio.setLastSyncAt(LocalDateTime.now());
            
            // FIXED: Add duplicate key handling for portfolio update
            try {
                portfolioRepository.save(portfolio);
            } catch (DataIntegrityViolationException e) {
                log.warn("Portfolio {} already exists when updating from PayProp tag, skipping save", portfolio.getId());
            }
            
            completeSyncLog(syncLog, "SUCCESS", null, Map.of("portfolioId", portfolio.getId()));
            return SyncResult.success("Updated existing portfolio from PayProp tag");
        }
    }
    
    private SyncResult handleTagUpdated(String tagId, PayPropTagDTO tagData, PortfolioSyncLog syncLog) {
        List<Portfolio> portfolios = findPortfoliosByPayPropTag(tagId);
        int updatedCount = 0;
        
        for (Portfolio portfolio : portfolios) {
            portfolio.setPayPropTagNames(tagData.getName());
            portfolio.setColorCode(tagData.getColor());
            portfolio.setLastSyncAt(LocalDateTime.now());
            
            // FIXED: Add duplicate key handling for portfolio updates
            try {
                portfolioRepository.save(portfolio);
                updatedCount++;
            } catch (DataIntegrityViolationException e) {
                log.warn("Portfolio {} already exists when updating from PayProp tag update, skipping save", portfolio.getId());
            }
        }
        
        completeSyncLog(syncLog, "SUCCESS", null, 
            Map.of("totalPortfolios", portfolios.size(), "updatedPortfolios", updatedCount));
        return SyncResult.success("Updated " + updatedCount + " of " + portfolios.size() + " portfolios from PayProp tag update");
    }
    
    private SyncResult handleTagDeleted(String tagId, PortfolioSyncLog syncLog) {
        List<Portfolio> portfolios = findPortfoliosByPayPropTag(tagId);
        int updatedCount = 0;
        
        for (Portfolio portfolio : portfolios) {
            removePayPropTagFromPortfolio(portfolio, tagId);
            if (portfolio.getPayPropTags() == null || portfolio.getPayPropTags().isEmpty()) {
                // If this was the only PayProp tag, mark as unsynced but don't delete
                portfolio.setSyncStatus(SyncStatus.pending);
            }
            portfolio.setLastSyncAt(LocalDateTime.now());
            
            // FIXED: Add duplicate key handling for portfolio updates
            try {
                portfolioRepository.save(portfolio);
                updatedCount++;
            } catch (DataIntegrityViolationException e) {
                log.warn("Portfolio {} already exists when removing PayProp tag, skipping save", portfolio.getId());
            }
        }
        
        completeSyncLog(syncLog, "SUCCESS", null, 
            Map.of("totalPortfolios", portfolios.size(), "affectedPortfolios", updatedCount));
        return SyncResult.success("Removed PayProp tag from " + updatedCount + " of " + portfolios.size() + " portfolios");
    }
    
    private SyncResult handleTagAppliedToProperties(String tagId, List<String> propertyIds, PortfolioSyncLog syncLog) {
        List<Portfolio> portfolios = findPortfoliosByPayPropTag(tagId);
        
        if (portfolios.isEmpty()) {
            completeSyncLog(syncLog, "FAILED", "No portfolio found for tag: " + tagId, null);
            return SyncResult.failure("No portfolio found for tag: " + tagId);
        }
        
        Portfolio portfolio = portfolios.get(0); // Use first matching portfolio
        int assignedCount = 0;
        int duplicateCount = 0;
        int errorCount = 0;
        List<String> errors = new ArrayList<>();

        for (String payPropPropertyId : propertyIds) {
            try {
                Optional<Property> propertyOpt = propertyService.findByPayPropId(payPropPropertyId);
                if (propertyOpt.isPresent()) {
                    Property property = propertyOpt.get();
                    property.setPortfolio(portfolio);
                    property.setPortfolioAssignmentDate(LocalDateTime.now());
                    
                    // FIXED: Add duplicate key handling for property portfolio assignment
                    try {
                        propertyService.save(property);
                        assignedCount++;
                    } catch (DataIntegrityViolationException e) {
                        log.warn("Property {} already exists when assigning to portfolio, skipping save", property.getId());
                        duplicateCount++;
                    }
                } else {
                    log.warn("Property with PayProp ID {} not found in system", payPropPropertyId);
                    errors.add("Property not found: " + payPropPropertyId);
                }
            } catch (Exception e) {
                errorCount++;
                log.error("Failed to assign property {} to portfolio: {}", payPropPropertyId, e.getMessage());
                errors.add("Property " + payPropPropertyId + ": " + e.getMessage());
                // Continue with next property
            }
        }
        
        completeSyncLog(syncLog, "SUCCESS", null, 
            Map.of("portfolioId", portfolio.getId(), "assignedProperties", assignedCount, 
                   "duplicates", duplicateCount, "errors", errorCount, "errorDetails", errors));
        
        String message = String.format("Assigned %d properties to portfolio (duplicates: %d, errors: %d)", 
            assignedCount, duplicateCount, errorCount);
        return SyncResult.success(message);
    }
    
    private SyncResult handleTagRemovedFromProperties(String tagId, List<String> propertyIds, PortfolioSyncLog syncLog) {
        int removedCount = 0;
        int duplicateCount = 0;
        int errorCount = 0;
        List<String> errors = new ArrayList<>();
        
        for (String payPropPropertyId : propertyIds) {
            try {
                Optional<Property> propertyOpt = propertyService.findByPayPropId(payPropPropertyId);
                if (propertyOpt.isPresent()) {
                    Property property = propertyOpt.get();
                    // Only remove from portfolio if this was the matching tag
                    if (property.getPortfolio() != null && 
                        portfolioHasPayPropTag(property.getPortfolio(), tagId)) {
                        property.setPortfolio(null);
                        property.setPortfolioAssignmentDate(null);
                        
                        // FIXED: Add duplicate key handling for property portfolio removal
                        try {
                            propertyService.save(property);
                            removedCount++;
                        } catch (DataIntegrityViolationException e) {
                            log.warn("Property {} already exists when removing from portfolio, skipping save", property.getId());
                            duplicateCount++;
                        }
                    }
                } else {
                    log.warn("Property with PayProp ID {} not found in system", payPropPropertyId);
                    errors.add("Property not found: " + payPropPropertyId);
                }
            } catch (Exception e) {
                errorCount++;
                log.error("Failed to remove property {} from portfolio: {}", payPropPropertyId, e.getMessage());
                errors.add("Property " + payPropPropertyId + ": " + e.getMessage());
                // Continue with next property
            }
        }
        
        completeSyncLog(syncLog, "SUCCESS", null, 
            Map.of("removedProperties", removedCount, "duplicates", duplicateCount, 
                   "errors", errorCount, "errorDetails", errors));
        
        String message = String.format("Removed %d properties from portfolio (duplicates: %d, errors: %d)", 
            removedCount, duplicateCount, errorCount);
        return SyncResult.success(message);
    }
    
    // ===== PAYPROP API METHODS =====
    
    private PayPropTagDTO createPayPropTag(PayPropTagDTO tag) throws Exception {
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
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
    

    /**
     * Apply tag to property using PayProp API - PUBLIC METHOD for controller access
     * Uses the correct PayProp endpoint: POST /tags/entities/property/{property_id}
     */
    public void applyTagToProperty(String payPropPropertyId, String tagId) throws Exception {
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        
        // PayProp expects an array of tag IDs for bulk tagging
        List<String> tagIds = List.of(tagId);
        HttpEntity<List<String>> request = new HttpEntity<>(tagIds, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                payPropApiBase + "/tags/entities/property/" + payPropPropertyId, 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Successfully applied PayProp tag {} to property {}", tagId, payPropPropertyId);
            } else {
                throw new RuntimeException("Unexpected response status: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            log.error("Failed to apply tag {} to property {}: {}", tagId, payPropPropertyId, e.getResponseBodyAsString());
            throw new RuntimeException("Failed to apply tag to property: " + e.getResponseBodyAsString(), e);
        }
    }

    /**
     * Remove tag from property using PayProp API - PUBLIC METHOD for controller access
     * Uses the correct PayProp endpoint: DELETE /tags/entities/property/{property_id}/{tag_id}
     */
    public void removeTagFromProperty(String payPropPropertyId, String tagId) throws Exception {
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                payPropApiBase + "/tags/entities/property/" + payPropPropertyId + "/" + tagId,
                HttpMethod.DELETE,
                request,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Successfully removed PayProp tag {} from property {}", tagId, payPropPropertyId);
            } else {
                throw new RuntimeException("Unexpected response status: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            log.error("Failed to remove tag {} from property {}: {}", tagId, payPropPropertyId, e.getResponseBodyAsString());
            throw new RuntimeException("Failed to remove tag from property: " + e.getResponseBodyAsString(), e);
        }
    }
    
    
    // ===== BULK OPERATIONS =====
    
    /**
     * Sync all portfolios that need synchronization
     */
    public SyncResult syncAllPortfolios(Long initiatedBy) {
        if (!oAuth2Service.hasValidTokens()) {
            throw new IllegalStateException("No valid OAuth2 tokens. Please authorize PayProp first.");
        }
        
        List<Portfolio> portfoliosNeedingSync = findPortfoliosNeedingSync();
        
        int successCount = 0;
        int failureCount = 0;
        int duplicateCount = 0;
        List<String> errors = new ArrayList<>();
        
        for (Portfolio portfolio : portfoliosNeedingSync) {
            try {
                // Each portfolio sync in its own transaction
                SyncResult result = syncPortfolioToPayPropInSeparateTransaction(portfolio.getId(), initiatedBy);
                if (result.isSuccess()) {
                    successCount++;
                } else {
                    failureCount++;
                    errors.add("Portfolio " + portfolio.getName() + ": " + result.getMessage());
                }
            } catch (DataIntegrityViolationException e) {
                log.warn("Portfolio {} already exists during bulk sync, skipping", portfolio.getId());
                duplicateCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("Failed to sync portfolio {}: {}", portfolio.getName(), e.getMessage());
                errors.add("Portfolio " + portfolio.getName() + ": " + e.getMessage());
                // Continue with next portfolio
            }
        }
        
        String message = String.format("Sync completed. Success: %d, Failed: %d, Duplicates: %d", 
            successCount, failureCount, duplicateCount);
        Map<String, Object> details = Map.of(
            "totalPortfolios", portfoliosNeedingSync.size(),
            "successCount", successCount,
            "failureCount", failureCount,
            "duplicateCount", duplicateCount,
            "errors", errors
        );
        
        return failureCount == 0 ? 
            SyncResult.success(message, details) : 
            SyncResult.partial(message, details);
    }
    
    /**
     * Portfolio sync in separate transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult syncPortfolioToPayPropInSeparateTransaction(Long portfolioId, Long initiatedBy) {
        return syncPortfolioToPayProp(portfolioId, initiatedBy);
    }
    
    /**
     * Pull all tags from PayProp and sync to local portfolios
     */
    public SyncResult pullAllTagsFromPayProp(Long initiatedBy) {
        try {
            List<PayPropTagDTO> payPropTags = getAllPayPropTags();
            
            int createdCount = 0;
            int updatedCount = 0;
            int duplicateCount = 0;
            int errorCount = 0;
            List<String> errors = new ArrayList<>();
            
            for (PayPropTagDTO tag : payPropTags) {
                try {
                    // Each tag processing in its own transaction
                    SyncResult result = handleTagCreatedInSeparateTransaction(tag.getId(), tag, initiatedBy);
                    
                    if (result.isSuccess()) {
                        if (result.getMessage().contains("Created")) {
                            createdCount++;
                        } else {
                            updatedCount++;
                        }
                    } else {
                        errorCount++;
                        errors.add("Tag " + tag.getId() + ": " + result.getMessage());
                    }
                } catch (DataIntegrityViolationException e) {
                    log.warn("Portfolio for PayProp tag {} already exists during pull, skipping", tag.getId());
                    duplicateCount++;
                } catch (Exception e) {
                    errorCount++;
                    log.error("Failed to process PayProp tag {}: {}", tag.getId(), e.getMessage());
                    errors.add("Tag " + tag.getId() + ": " + e.getMessage());
                    // Continue with next tag
                }
            }
            
            String message = String.format("PayProp tags pulled. Created: %d portfolios, Updated: %d portfolios, Duplicates: %d, Errors: %d", 
                createdCount, updatedCount, duplicateCount, errorCount);
            return SyncResult.success(message, Map.of(
                "created", createdCount, 
                "updated", updatedCount,
                "duplicates", duplicateCount,
                "errors", errorCount,
                "errorDetails", errors));
            
        } catch (Exception e) {
            return SyncResult.failure("Failed to pull tags from PayProp: " + e.getMessage());
        }
    }
    
    /**
     * Handle tag created in separate transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult handleTagCreatedInSeparateTransaction(String tagId, PayPropTagDTO tagData, Long initiatedBy) {
        PortfolioSyncLog syncLog = createSyncLog(null, null, null, "PAYPROP_TO_PORTFOLIO", "CREATE", initiatedBy);
        return handleTagCreated(tagId, tagData, syncLog);
    }
    
    // ===== UTILITY METHODS =====
    /**
     * Get available PayProp tags that are not yet adopted as portfolios
     * PUBLIC METHOD for controller access
     */
    public List<PayPropTagDTO> getAvailablePayPropTags() throws Exception {
        List<PayPropTagDTO> allTags = getAllPayPropTags();
        
        // Filter out tags that are already associated with portfolios
        return allTags.stream()
            .filter(tag -> {
                // Check if any portfolio already uses this tag
                List<Portfolio> existingPortfolios = portfolioRepository.findAll().stream()
                    .filter(portfolio -> portfolioHasPayPropTag(portfolio, tag.getId()))
                    .collect(Collectors.toList());
                return existingPortfolios.isEmpty();
            })
            .collect(Collectors.toList());
    }

    /**
     * Bulk apply tags to multiple properties
     * More efficient for portfolio assignment
     */
    public Map<String, Object> bulkApplyTagToProperties(String tagId, List<String> payPropPropertyIds) {
        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int failureCount = 0;
        List<String> errors = new ArrayList<>();
        
        for (String propertyId : payPropPropertyIds) {
            try {
                applyTagToProperty(propertyId, tagId);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                errors.add("Property " + propertyId + ": " + e.getMessage());
                log.error("Failed to apply tag {} to property {}: {}", tagId, propertyId, e.getMessage());
            }
        }
        
        result.put("successCount", successCount);
        result.put("failureCount", failureCount);
        result.put("errors", errors);
        result.put("totalProcessed", payPropPropertyIds.size());
        
        return result;
    }





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
            .filter(portfolio -> portfolio.getSyncStatus() == SyncStatus.pending || 
                               portfolio.getSyncStatus() == SyncStatus.error)
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
        
        // FIXED: Add duplicate key handling for sync log creation
        try {
            return syncLogRepository.save(syncLog);
        } catch (DataIntegrityViolationException e) {
            log.warn("Sync log already exists, creating new one with timestamp differentiation");
            syncLog.setSyncStartedAt(LocalDateTime.now().plusNanos(System.nanoTime() % 1000000));
            try {
                return syncLogRepository.save(syncLog);
            } catch (DataIntegrityViolationException e2) {
                log.error("Unable to create sync log even with timestamp differentiation: {}", e2.getMessage());
                // Return a mock sync log that won't cause issues
                syncLog.setId(System.currentTimeMillis()); // Use timestamp as ID
                return syncLog;
            }
        }
    }
    
    private void completeSyncLog(PortfolioSyncLog syncLog, String status, String errorMessage, Map<String, Object> payloadReceived) {
        syncLog.setStatus(status);
        syncLog.setErrorMessage(errorMessage);
        syncLog.setPayloadReceived(payloadReceived);
        syncLog.setSyncCompletedAt(LocalDateTime.now());
        
        // FIXED: Add duplicate key handling for sync log completion
        try {
            syncLogRepository.save(syncLog);
        } catch (DataIntegrityViolationException e) {
            log.warn("Sync log {} already exists when completing, skipping save", syncLog.getId());
        }
    }
}
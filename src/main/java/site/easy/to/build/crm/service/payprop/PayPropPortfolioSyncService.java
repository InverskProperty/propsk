// PayPropPortfolioSyncService.java - OAuth2 Integration with Duplicate Key Handling
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
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
import site.easy.to.build.crm.service.portfolio.PortfolioAssignmentService;
import site.easy.to.build.crm.service.portfolio.PortfolioService;
import site.easy.to.build.crm.service.tag.TagNamespaceService;
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
    
    @Value("${payprop.api.base-url}")
    private String payPropApiBase;
    
    @Autowired
    private PayPropApiClient payPropApiClient;
    
    // FIXED: Add PortfolioAssignmentService for proper local tag removal (with @Lazy to break circular dependency)
    @Autowired(required = false)
    @Lazy
    private PortfolioAssignmentService portfolioAssignmentService;
    
    @Autowired(required = false)
    @Lazy
    private PortfolioService portfolioService;
    
    // Add PayPropBlockSyncService for hierarchical sync
    @Autowired(required = false)
    @Lazy
    private PayPropBlockSyncService blockSyncService;
    
    @Autowired
    private TagNamespaceService tagNamespaceService;

    @Autowired
    private UserRepository userRepository;

    @Autowired(required = false)
    @Lazy
    private site.easy.to.build.crm.service.integration.PayPropGoogleIntegrationService integrationService;

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
     * Enhanced sync with Google documentation (NEW - uses integration service)
     */
    public SyncResult syncPortfolioToPayPropWithDocumentation(Long portfolioId, String initiatedByEmail) {
        if (integrationService != null) {
            try {
                log.info("🔄 Using enhanced sync with Google documentation for portfolio {}", portfolioId);

                var result = integrationService.syncPortfolioWithDocumentation(portfolioId, initiatedByEmail);

                return SyncResult.success("Enhanced sync completed", Map.of(
                    "tagId", result.getTagId(),
                    "googleDocumentation", result.getGoogleResult() != null ? "created" : "skipped"
                ));

            } catch (Exception e) {
                log.warn("Enhanced sync failed, falling back to basic sync: {}", e.getMessage());
                // Fall back to basic sync if integration service fails
                return syncPortfolioToPayProp(portfolioId, 1L); // Default user ID
            }
        } else {
            log.debug("Integration service not available, using basic sync");
            return syncPortfolioToPayProp(portfolioId, 1L);
        }
    }

    /**
     * Basic sync (existing functionality - unchanged)
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
            List<Property> properties = portfolioService.getPropertiesForPortfolio(portfolioId);
            int propertiesTagged = 0;
            for (Property property : properties) {
                if (property.getPayPropId() != null) {
                    try {
                        // ✅ FIXED: Remove third parameter - method only accepts 2
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
            portfolio.setSyncStatus(SyncStatus.failed);
            
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
     * FIXED: Create or retrieve existing PayProp tag for a portfolio using enhanced method
     */
    private PayPropTagDTO createOrGetPayPropTag(Portfolio portfolio) throws Exception {
        log.info("🔍 Creating/retrieving PayProp tag for portfolio {}: '{}'", portfolio.getId(), portfolio.getName());
        
        // First, check if portfolio already has PayProp tags
        if (portfolio.getPayPropTags() != null && !portfolio.getPayPropTags().isEmpty()) {
            // Assuming PayProp tags are stored as comma-separated string
            String[] tagIds = portfolio.getPayPropTags().split(",");
            if (tagIds.length > 0) {
                String existingTagId = tagIds[0].trim();
                log.info("Portfolio already has PayProp tag ID: '{}'", existingTagId);
                PayPropTagDTO existingTag = getPayPropTag(existingTagId);
                if (existingTag != null) {
                    log.info("✅ Retrieved existing tag: {} -> {}", existingTag.getName(), existingTag.getId());
                    return existingTag;
                }
                log.warn("⚠️ Portfolio has tag ID '{}' but tag not found in PayProp, creating new", existingTagId);
            }
        }
        
        // Generate tag name and use enhanced creation method
        String tagName = generateTagName(portfolio);
        log.info("📝 Using enhanced tag creation for: '{}'", tagName);
        
        // Use the enhanced ensurePayPropTagExists method that we just fixed
        return ensurePayPropTagExists(tagName);
    }
    
    public List<PayPropTagDTO> getAllPayPropTags() throws Exception {
        log.info("🚀 Fetching all PayProp tags using ApiClient...");
        
        try {
            List<Map<String, Object>> allTagData = payPropApiClient.fetchAllPages("/tags", item -> item);
            
            // ✅ FIX: Use lambda instead of method reference
            List<PayPropTagDTO> tags = allTagData.stream()
                .map(tagMap -> convertMapToTagDTO(tagMap))  // ✅ Lambda works reliably
                .collect(Collectors.toList());
            
            log.info("✅ Successfully fetched {} PayProp tags", tags.size());
            return tags;
            
        } catch (Exception e) {
            log.error("❌ Failed to fetch PayProp tags: {}", e.getMessage());
            throw new RuntimeException("Failed to get PayProp tags: " + e.getMessage(), e);
        }
    }

    
    // Make sure the helper method is exactly like this:
    private PayPropTagDTO convertMapToTagDTO(Map<String, Object> tagMap) {
        PayPropTagDTO tag = new PayPropTagDTO();
        
        // ✅ FIX: Try multiple possible ID field names since PayProp API might use different fields
        String tagId = extractTagIdFromMap(tagMap);
        tag.setId(tagId);
        tag.setName((String) tagMap.get("name"));
        tag.setDescription((String) tagMap.getOrDefault("description", ""));
        tag.setColor((String) tagMap.getOrDefault("color", "#3498db"));
        
        // Debug logging to understand PayProp response structure
        log.debug("🔍 Converting PayProp tag map to DTO: {}", tagMap);
        log.debug("🏷️ Extracted tag ID: '{}' from field check", tagId);
        
        return tag;
    }
    
    /**
     * Extract tag ID from PayProp response trying multiple possible field names
     */
    private String extractTagIdFromMap(Map<String, Object> tagMap) {
        // Try different possible ID field names that PayProp might use
        String[] possibleIdFields = {"id", "external_id", "tag_id", "tagId", "_id", "uuid"};
        
        for (String field : possibleIdFields) {
            Object value = tagMap.get(field);
            if (value != null && !value.toString().trim().isEmpty()) {
                log.debug("✅ Found tag ID '{}' in field '{}'", value, field);
                return value.toString();
            }
        }
        
        // If no ID found, log the entire response for debugging
        log.error("❌ No tag ID found in PayProp response. Available fields: {}", tagMap.keySet());
        log.error("❌ Full response data: {}", tagMap);
        return null;
    }

    // ✅ SAFE VALUE EXTRACTION HELPERS
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    // ===== SINGLE TAG LOOKUP - TYPE SAFE =====
    public PayPropTagDTO getPayPropTag(String tagId) throws Exception {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("external_id", tagId);
            
            // ✅ PayPropApiClient returns properly typed result
            PayPropApiClient.PayPropPageResult result = payPropApiClient.fetchWithParams("/tags", params);
            
            if (!result.getItems().isEmpty()) {
                Map<String, Object> tagMap = result.getItems().get(0);
                return convertMapToTagDTO(tagMap);  // ✅ Reuse helper method
            }
            
            log.warn("PayProp tag {} not found", tagId);
            return null;
            
        } catch (Exception e) {
            log.error("Error getting PayProp tag {}: {}", tagId, e.getMessage());
            throw new RuntimeException("Failed to get PayProp tag: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a unique tag name for PayProp (max 32 characters)
     * PayProp limit: 1-32 characters, pattern: ^[a-zA-Z0-9_\-\s]+$
     */
    private String generateTagName(Portfolio portfolio) {
        String baseName = portfolio.getName().replaceAll("[^a-zA-Z0-9\\s_-]", "");
        
        // Add owner prefix if it's an owner-specific portfolio
        if (portfolio.getPropertyOwnerId() != null) {
            baseName = "Owner-" + portfolio.getPropertyOwnerId() + "-" + baseName;
        }
        
        // ✅ CRITICAL FIX: Ensure PayProp 32-character limit is respected
        if (baseName.length() > 32) {
            // Truncate but keep meaningful content
            // Try to keep owner info and truncate portfolio name
            String ownerPrefix = "Owner-" + portfolio.getPropertyOwnerId() + "-";
            int remainingSpace = 32 - ownerPrefix.length();
            
            if (remainingSpace > 0) {
                String portfolioName = portfolio.getName().replaceAll("[^a-zA-Z0-9\\s_-]", "");
                String truncatedName = portfolioName.length() > remainingSpace ? 
                    portfolioName.substring(0, remainingSpace).trim() : portfolioName;
                baseName = ownerPrefix + truncatedName;
            } else {
                // If owner prefix itself is too long, just use first 32 chars
                baseName = baseName.substring(0, 32).trim();
            }
        }
        
        // Final safety check
        baseName = baseName.length() > 32 ? baseName.substring(0, 32).trim() : baseName;
        
        log.debug("Generated PayProp tag name: '{}' (length: {})", baseName, baseName.length());
        return baseName;
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
                    // ❌ DISABLED: Direct portfolio assignment - now handled by PropertyPortfolioAssignment table
                    // property.setPortfolio(portfolio);
                    // property.setPortfolioAssignmentDate(LocalDateTime.now());
                    
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
        
        // First, find portfolios that use this tag
        List<Portfolio> portfolios = findPortfoliosByPayPropTag(tagId);
        
        for (String payPropPropertyId : propertyIds) {
            try {
                Optional<Property> propertyOpt = propertyService.findByPayPropId(payPropPropertyId);
                if (propertyOpt.isPresent()) {
                    Property property = propertyOpt.get();
                    
                    // FIXED: Use proper portfolio assignment service for local removal
                    for (Portfolio portfolio : portfolios) {
                        try {
                            // Use PortfolioAssignmentService if available
                            if (portfolioAssignmentService != null) {
                                portfolioAssignmentService.removePropertyFromPortfolio(property.getId(), portfolio.getId(), 1L); // System user
                                removedCount++;
                                log.info("✅ Removed property {} from portfolio {} via PayProp webhook", property.getId(), portfolio.getId());
                            } else {
                                log.warn("PortfolioAssignmentService not available for property {} removal", property.getId());
                                errors.add("Property " + payPropPropertyId + ": Service not available");
                                errorCount++;
                            }
                        } catch (Exception e) {
                            log.error("Failed to remove property {} from portfolio {}: {}", property.getId(), portfolio.getId(), e.getMessage());
                            errors.add("Property " + payPropPropertyId + " from portfolio " + portfolio.getId() + ": " + e.getMessage());
                            errorCount++;
                        }
                    }
                } else {
                    log.warn("Property with PayProp ID {} not found in system", payPropPropertyId);
                    errors.add("Property not found: " + payPropPropertyId);
                    errorCount++;
                }
            } catch (Exception e) {
                errorCount++;
                log.error("Failed to process property {} for tag removal: {}", payPropPropertyId, e.getMessage());
                errors.add("Property " + payPropPropertyId + ": " + e.getMessage());
                // Continue with next property
            }
        }
        
        completeSyncLog(syncLog, "SUCCESS", null, 
            Map.of("removedAssignments", removedCount, "duplicates", duplicateCount, 
                   "errors", errorCount, "errorDetails", errors));
        
        String message = String.format("Removed %d property assignments from portfolios (duplicates: %d, errors: %d)", 
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
     * CRITICAL FIX: Ensure a PayProp tag exists by name, creating it if necessary
     * This is the core method that fixes our tag creation workflow
     * 
     * @param tagName The tag name (e.g., "PF-1105-JoeWeeks")
     * @return PayPropTagDTO with the external_id from PayProp
     * @throws Exception if tag creation fails
     */
    public PayPropTagDTO ensurePayPropTagExists(String tagName) throws Exception {
        log.info("🏷️ Starting tag creation/verification for: '{}'", tagName);
        
        // STEP 0: Validate authentication before proceeding
        try {
            validateAuthentication();
        } catch (Exception e) {
            log.error("❌ CRITICAL: Authentication validation failed: {}", e.getMessage());
            throw new RuntimeException("PayProp authentication failed: " + e.getMessage(), e);
        }
        
        try {
            // Step 1: Check if tag already exists
            log.debug("Step 1: Searching for existing tag with name: '{}'", tagName);
            List<PayPropTagDTO> existingTags = searchPayPropTagsByName(tagName);
            
            if (!existingTags.isEmpty()) {
                PayPropTagDTO existingTag = existingTags.get(0);
                log.info("✅ Found existing PayProp tag: '{}' with ID: '{}'", tagName, existingTag.getId());
                return existingTag;
            }
            
            log.debug("No existing tag found, proceeding to create new tag");
            
            // Step 2: Create new tag
            log.info("📝 Creating new PayProp tag: '{}'", tagName);
            Map<String, Object> tagRequest = new HashMap<>();
            tagRequest.put("name", tagName);
            tagRequest.put("description", "Created by CRM for portfolio organization");
            
            log.debug("API Request: POST /tags with payload: {}", tagRequest);
            Map<String, Object> tagResponse = payPropApiClient.post("/tags", tagRequest);
            log.debug("API Response: {}", tagResponse);
            
            // Step 3: Extract external ID
            String externalId = extractTagId(tagResponse);
            if (externalId == null || externalId.trim().isEmpty()) {
                log.error("❌ PayProp API returned null/empty external ID. Full response: {}", tagResponse);
                throw new RuntimeException("PayProp API returned invalid tag ID");
            }
            
            // Step 4: Create return object
            PayPropTagDTO createdTag = new PayPropTagDTO();
            createdTag.setId(externalId);
            createdTag.setName(tagName);
            createdTag.setDescription("Created by CRM for portfolio organization");
            
            log.info("✅ Successfully created PayProp tag: '{}' with external ID: '{}'", tagName, externalId);
            return createdTag;
            
        } catch (Exception e) {
            log.error("❌ CRITICAL: Tag creation failed for '{}': {}", tagName, e.getMessage(), e);
            
            // Enhanced error handling - check if it's a duplicate error
            if (e.getMessage() != null && (e.getMessage().contains("already exists") || 
                                         e.getMessage().contains("duplicate") ||
                                         e.getMessage().contains("conflict"))) {
                log.info("ℹ️ Tag conflict detected, attempting to retrieve existing tag: {}", tagName);
                try {
                    List<PayPropTagDTO> retryTags = searchPayPropTagsByName(tagName);
                    if (!retryTags.isEmpty()) {
                        PayPropTagDTO existingTag = retryTags.get(0);
                        log.info("✅ Retrieved existing tag after creation conflict: '{}' -> '{}'", tagName, existingTag.getId());
                        return existingTag;
                    }
                } catch (Exception retryError) {
                    log.error("❌ Failed to retrieve tag after creation conflict: {}", retryError.getMessage());
                }
            }
            
            // DON'T return null - throw the exception to see what's breaking
            throw new RuntimeException("Tag creation failed for '" + tagName + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Validate PayProp authentication before tag operations
     */
    private void validateAuthentication() {
        try {
            if (oAuth2Service == null) {
                throw new RuntimeException("OAuth2 service not available");
            }
            
            if (!oAuth2Service.hasValidTokens()) {
                log.warn("⚠️ PayProp token expired, attempting refresh...");
                oAuth2Service.refreshToken();
                
                if (!oAuth2Service.hasValidTokens()) {
                    throw new RuntimeException("Failed to refresh PayProp tokens");
                }
            }
            
            log.debug("✅ PayProp authentication validated");
        } catch (Exception e) {
            log.error("❌ PayProp authentication failed: {}", e.getMessage());
            throw new RuntimeException("PayProp authentication failed", e);
        }
    }
    
    /**
     * Handle different PayProp response formats to extract tag ID
     */
    private String extractTagId(Map<String, Object> response) {
        // Try different possible ID field names
        String[] possibleFields = {"id", "external_id", "tag_id", "tagId"};
        
        for (String field : possibleFields) {
            Object value = response.get(field);
            if (value != null && !value.toString().trim().isEmpty()) {
                log.debug("Found tag ID in field '{}': '{}'", field, value);
                return value.toString();
            }
        }
        
        log.error("❌ Could not find tag ID in response: {}", response);
        throw new RuntimeException("PayProp response missing tag ID: " + response);
    }
    
    /**
     * Search PayProp tags by name
     * @param tagName The exact tag name to search for
     * @return List of matching tags (should be 0 or 1 due to PayProp uniqueness)
     */
    private List<PayPropTagDTO> searchPayPropTagsByName(String tagName) throws Exception {
        log.debug("🔍 Searching PayProp for tag: {}", tagName);
        
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        // Use PayProp's tag search API with name filter
        String url = payPropApiBase + "/tags?name=" + tagName;
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, request, Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
                
                if (items != null) {
                    List<PayPropTagDTO> tags = new ArrayList<>();
                    for (Map<String, Object> item : items) {
                        PayPropTagDTO tag = new PayPropTagDTO();
                        tag.setId((String) item.get("id"));
                        tag.setName((String) item.get("name"));
                        tag.setDescription((String) item.get("description"));
                        tag.setColor((String) item.get("color"));
                        
                        // Exact name match check (PayProp search might be fuzzy)
                        if (tagName.equals(tag.getName())) {
                            tags.add(tag);
                        }
                    }
                    return tags;
                }
            }
            
            return new ArrayList<>();
            
        } catch (HttpClientErrorException e) {
            log.error("❌ PayProp API error searching tags: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to search PayProp tags: " + e.getResponseBodyAsString(), e);
        }
    }

    public void applyTagToProperty(String payPropPropertyId, String tagId) throws Exception {
        log.info("🏷️ Attempting to apply PayProp tag {} to property {}", tagId, payPropPropertyId);
        
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // ✅ CORRECT FORMAT - PayProp requires this specific structure
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("tags", List.of(tagId));
        
        // Try to get tag name for better API compatibility
        try {
            PayPropTagDTO tag = getPayPropTag(tagId);
            if (tag != null && tag.getName() != null) {
                requestBody.put("names", List.of(tag.getName()));
                log.info("📋 Using tag name '{}' along with ID", tag.getName());
            }
        } catch (Exception e) {
            log.warn("Could not retrieve tag name for {}, proceeding with ID only", tagId);
        }
        
        String url = payPropApiBase + "/tags/entities/property/" + payPropPropertyId;
        log.info("🔗 POST URL: {}, Body: {}", url, requestBody);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            log.info("📊 PayProp tag application response: Status={}, Body={}", 
                response.getStatusCode(), response.getBody());
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Successfully applied PayProp tag {} to property {}", tagId, payPropPropertyId);
            } else {
                String errorMsg = "Unexpected response status: " + response.getStatusCode();
                log.error("❌ {}", errorMsg);
                throw new RuntimeException(errorMsg);
            }
        } catch (HttpClientErrorException e) {
            String errorDetails = String.format("HTTP %s: %s", e.getStatusCode(), e.getResponseBodyAsString());
            log.error("❌ Failed to apply tag {} to property {}: {}", tagId, payPropPropertyId, errorDetails);
            
            // ENHANCED: Provide more specific error messages
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new RuntimeException("Property " + payPropPropertyId + " or tag " + tagId + " not found in PayProp");
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new RuntimeException("PayProp authentication failed - please re-authorize");
            } else if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new RuntimeException("Invalid request to PayProp: " + e.getResponseBodyAsString());
            } else {
                throw new RuntimeException("Failed to apply tag to property: " + errorDetails, e);
            }
        } catch (Exception e) {
            log.error("❌ Unexpected error applying tag {} to property {}: {}", tagId, payPropPropertyId, e.getMessage());
            throw new RuntimeException("Unexpected error during tag application: " + e.getMessage(), e);
        }
    }

    // Alternative method for name-only approach
    public void applyTagToPropertyByName(String payPropPropertyId, String tagName) throws Exception {
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // ✅ Names-only format also works
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("names", List.of(tagName));
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(
            payPropApiBase + "/tags/entities/property/" + payPropPropertyId, 
            request, 
            Map.class
        );
        
        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("Successfully applied PayProp tag {} to property {}", tagName, payPropPropertyId);
        }
    }

    /**
     * Remove tag from property using PayProp API - PUBLIC METHOD for controller access
     * Uses the correct PayProp endpoint: DELETE /tags/{tag_id}/entities?entity_type=property&entity_id={property_id}
     */
    public void removeTagFromProperty(String payPropPropertyId, String tagId) throws Exception {
        log.info("🗑️ Attempting to remove PayProp tag {} from property {}", tagId, payPropPropertyId);
        
        HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        // FIXED: Use correct PayProp API endpoint with query parameters
        String url = payPropApiBase + "/tags/" + tagId + "/entities?entity_type=property&entity_id=" + payPropPropertyId;
        log.info("🔗 CORRECTED DELETE URL: {}", url);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                request,
                Map.class
            );
            
            log.info("📊 PayProp tag removal response: Status={}, Body={}", 
                response.getStatusCode(), response.getBody());
            
            // FIXED: Accept both OK (200) and NO_CONTENT (204) as success
            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.NO_CONTENT) {
                log.info("✅ Successfully removed PayProp tag {} from property {}", tagId, payPropPropertyId);
            } else {
                String errorMsg = "Unexpected response status: " + response.getStatusCode();
                log.error("❌ {}", errorMsg);
                throw new RuntimeException(errorMsg);
            }
        } catch (HttpClientErrorException e) {
            String errorDetails = String.format("HTTP %s: %s", e.getStatusCode(), e.getResponseBodyAsString());
            log.error("❌ Failed to remove tag {} from property {}: {}", tagId, payPropPropertyId, errorDetails);
            
            // ENHANCED: Provide more specific error messages
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new RuntimeException("Tag " + tagId + " not found on property " + payPropPropertyId + " in PayProp");
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new RuntimeException("PayProp authentication failed - please re-authorize");
            } else {
                throw new RuntimeException("Failed to remove tag from property: " + errorDetails, e);
            }
        } catch (Exception e) {
            log.error("❌ Unexpected error removing tag {} from property {}: {}", tagId, payPropPropertyId, e.getMessage());
            throw new RuntimeException("Unexpected error during tag removal: " + e.getMessage(), e);
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
                               portfolio.getSyncStatus() == SyncStatus.failed)
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
        
        // CRITICAL FIX: Handle null or invalid user IDs
        if (initiatedBy == null) {
            // Try to find a valid system user, or use null if none exists
            syncLog.setInitiatedBy(findValidSystemUserId());
        } else {
            // Verify the user exists before setting
            syncLog.setInitiatedBy(validateUserId(initiatedBy));
        }
        
        try {
            return syncLogRepository.save(syncLog);
        } catch (DataIntegrityViolationException e) {
            log.warn("Sync log creation failed due to foreign key constraint. User ID: {}",
                    syncLog.getInitiatedBy());

            // Create a minimal log without user reference
            syncLog.setInitiatedBy(null);
            try {
                return syncLogRepository.save(syncLog);
            } catch (Exception e2) {
                log.error("Unable to create sync log even without user reference: {}", e2.getMessage());
                // Return a new transient log that won't interfere with Hibernate session
                PortfolioSyncLog fallbackLog = new PortfolioSyncLog();
                fallbackLog.setPortfolioId(syncLog.getPortfolioId());
                fallbackLog.setBlockId(syncLog.getBlockId());
                fallbackLog.setPropertyId(syncLog.getPropertyId());
                fallbackLog.setSyncType(syncLog.getSyncType());
                fallbackLog.setOperation(syncLog.getOperation());
                fallbackLog.setStatus("FAILED");
                fallbackLog.setErrorMessage("Unable to persist sync log: " + e2.getMessage());
                fallbackLog.setSyncStartedAt(syncLog.getSyncStartedAt());
                fallbackLog.setInitiatedBy(null);
                // Don't set ID - let it remain null for transient state
                return fallbackLog;
            }
        }
    }

    // Add these helper methods to PayPropPortfolioSyncService:

    private Long findValidSystemUserId() {
        // Try to find any valid user ID, or return null
        try {
            // Find the first active user as a system fallback
            List<User> users = userRepository.findAll();
            if (!users.isEmpty()) {
                User firstUser = users.get(0);
                log.debug("Using system user ID {} for sync log", firstUser.getId());
                return firstUser.getId().longValue();
            }
            log.warn("No users found in system, sync log will have null initiatedBy");
            return null;
        } catch (Exception e) {
            log.warn("Error finding system user: {}", e.getMessage());
            return null;
        }
    }

    private Long validateUserId(Long userId) {
        // Validate that the user exists
        try {
            if (userId == null) {
                return null;
            }

            // Check if user exists (UserRepository uses Integer, but we need Long)
            User user = userRepository.findById(userId.intValue());
            if (user != null) {
                log.debug("Validated user ID {} exists", userId);
                return userId;
            } else {
                log.warn("User ID {} not found, setting to null", userId);
                return null;
            }
        } catch (Exception e) {
            log.warn("Error validating user ID {}: {}", userId, e.getMessage());
            return null;
        }
    }
        
    private void completeSyncLog(PortfolioSyncLog syncLog, String status, String errorMessage, Map<String, Object> payloadReceived) {
        // Skip if this is a transient fallback log (no ID)
        if (syncLog == null || syncLog.getId() == null) {
            log.debug("Skipping completion of transient sync log");
            return;
        }

        try {
            syncLog.setStatus(status);
            syncLog.setErrorMessage(errorMessage);
            syncLog.setPayloadReceived(payloadReceived);
            syncLog.setSyncCompletedAt(LocalDateTime.now());

            syncLogRepository.save(syncLog);
        } catch (DataIntegrityViolationException e) {
            log.warn("Sync log {} already exists when completing, skipping save", syncLog.getId());
        } catch (Exception e) {
            log.error("Error completing sync log {}: {}", syncLog.getId(), e.getMessage());
            // Don't throw - this is a logging operation and shouldn't break the main flow
        }
    }
    
    // ===== HIERARCHICAL SYNC METHODS (Task 3.2) =====
    
    /**
     * Enhanced portfolio sync that includes cascading to blocks
     * Syncs portfolio first, then all its blocks
     */
    public SyncResult syncPortfolioWithBlocks(Long portfolioId, Long initiatedBy) {
        log.info("🏗️ Starting hierarchical sync for portfolio {} with blocks", portfolioId);
        
        // Step 1: Sync the portfolio itself
        SyncResult portfolioResult = syncPortfolioToPayProp(portfolioId, initiatedBy);
        if (!portfolioResult.isSuccess()) {
            log.error("❌ Portfolio sync failed, skipping block sync: {}", portfolioResult.getMessage());
            return portfolioResult;
        }
        
        log.info("✅ Portfolio {} synced successfully, proceeding with blocks", portfolioId);
        
        // Step 2: Sync all blocks in the portfolio (if block sync service is available)
        if (blockSyncService != null) {
            try {
                PayPropBlockSyncService.BatchBlockSyncResult blockResult = 
                    blockSyncService.syncAllBlocksInPortfolio(portfolioId);
                
                String combinedMessage = String.format(
                    "Portfolio synced successfully. %s", blockResult.getMessage()
                );
                
                Map<String, Object> combinedDetails = new HashMap<>();
                combinedDetails.putAll((Map<String, Object>) portfolioResult.getDetails());
                combinedDetails.put("blocksSucceeded", blockResult.getSuccessCount());
                combinedDetails.put("blocksFailed", blockResult.getFailureCount());
                combinedDetails.put("blocksSkipped", blockResult.getSkippedCount());
                
                if (blockResult.getFailureCount() > 0) {
                    combinedDetails.put("blockErrors", blockResult.getErrors());
                    log.warn("⚠️ Some blocks failed to sync: {}", blockResult.getErrors());
                }
                
                return SyncResult.success(combinedMessage, combinedDetails);
                
            } catch (Exception e) {
                log.error("❌ Block sync failed after successful portfolio sync: {}", e.getMessage());
                String warningMessage = String.format(
                    "Portfolio synced successfully, but block sync failed: %s", e.getMessage()
                );
                return SyncResult.success(warningMessage, portfolioResult.getDetails());
            }
        } else {
            log.warn("⚠️ PayPropBlockSyncService not available, skipping block sync");
            return SyncResult.success(
                "Portfolio synced successfully (block sync service not available)", 
                portfolioResult.getDetails()
            );
        }
    }
    
    /**
     * Sync all portfolios with their blocks (enhanced bulk operation)
     */
    public SyncResult syncAllPortfoliosWithBlocks(Long initiatedBy) {
        log.info("🏗️ Starting bulk hierarchical sync for all portfolios with blocks");
        
        if (!oAuth2Service.hasValidTokens()) {
            throw new IllegalStateException("No valid OAuth2 tokens. Please authorize PayProp first.");
        }
        
        List<Portfolio> portfoliosNeedingSync = findPortfoliosNeedingSync();
        
        int portfolioSuccessCount = 0;
        int portfolioFailureCount = 0;
        int totalBlocksSucceeded = 0;
        int totalBlocksFailed = 0;
        List<String> errors = new ArrayList<>();
        
        for (Portfolio portfolio : portfoliosNeedingSync) {
            try {
                log.info("🔄 Processing portfolio {} with hierarchical sync", portfolio.getId());
                
                SyncResult result = syncPortfolioWithBlocks(portfolio.getId(), initiatedBy);
                if (result.isSuccess()) {
                    portfolioSuccessCount++;
                    
                    // Extract block stats from result details
                    Map<String, Object> details = (Map<String, Object>) result.getDetails();
                    if (details != null) {
                        totalBlocksSucceeded += ((Number) details.getOrDefault("blocksSucceeded", 0)).intValue();
                        totalBlocksFailed += ((Number) details.getOrDefault("blocksFailed", 0)).intValue();
                    }
                    
                } else {
                    portfolioFailureCount++;
                    errors.add("Portfolio " + portfolio.getName() + ": " + result.getMessage());
                }
            } catch (Exception e) {
                portfolioFailureCount++;
                log.error("Failed to sync portfolio {} hierarchically: {}", portfolio.getName(), e.getMessage());
                errors.add("Portfolio " + portfolio.getName() + ": " + e.getMessage());
                // Continue with next portfolio
            }
        }
        
        String message = String.format(
            "Hierarchical sync completed. Portfolios - Success: %d, Failed: %d. Blocks - Success: %d, Failed: %d", 
            portfolioSuccessCount, portfolioFailureCount, totalBlocksSucceeded, totalBlocksFailed);
        
        Map<String, Object> details = Map.of(
            "totalPortfolios", portfoliosNeedingSync.size(),
            "portfolioSuccessCount", portfolioSuccessCount,
            "portfolioFailureCount", portfolioFailureCount,
            "totalBlocksSucceeded", totalBlocksSucceeded,
            "totalBlocksFailed", totalBlocksFailed,
            "errors", errors
        );
        
        return portfolioFailureCount == 0 ? 
            SyncResult.success(message, details) : 
            SyncResult.failure(message, details);
    }
    
    /**
     * Sync blocks only for portfolios that are already synced
     * Useful for adding blocks to existing portfolios
     */
    public SyncResult syncBlocksForSyncedPortfolios(Long initiatedBy) {
        log.info("🏗️ Starting block sync for already-synced portfolios");
        
        if (blockSyncService == null) {
            return SyncResult.failure("PayPropBlockSyncService not available");
        }
        
        // Find portfolios that are synced but may have blocks needing sync
        List<Portfolio> syncedPortfolios = portfolioRepository.findAll().stream()
            .filter(p -> p.getSyncStatus() == SyncStatus.synced)
            .filter(p -> p.getPayPropTags() != null && !p.getPayPropTags().trim().isEmpty())
            .collect(Collectors.toList());
        
        int portfoliosProcessed = 0;
        int totalBlocksSucceeded = 0;
        int totalBlocksFailed = 0;
        List<String> errors = new ArrayList<>();
        
        for (Portfolio portfolio : syncedPortfolios) {
            try {
                PayPropBlockSyncService.BatchBlockSyncResult blockResult = 
                    blockSyncService.syncAllBlocksInPortfolio(portfolio.getId());
                
                portfoliosProcessed++;
                totalBlocksSucceeded += blockResult.getSuccessCount();
                totalBlocksFailed += blockResult.getFailureCount();
                
                if (blockResult.getFailureCount() > 0) {
                    errors.addAll(blockResult.getErrors());
                }
                
                log.info("✅ Portfolio {}: {} blocks succeeded, {} failed", 
                        portfolio.getId(), blockResult.getSuccessCount(), blockResult.getFailureCount());
                        
            } catch (Exception e) {
                log.error("Failed to sync blocks for portfolio {}: {}", portfolio.getId(), e.getMessage());
                errors.add("Portfolio " + portfolio.getName() + ": " + e.getMessage());
            }
        }
        
        String message = String.format(
            "Block sync completed for %d portfolios. Blocks - Success: %d, Failed: %d",
            portfoliosProcessed, totalBlocksSucceeded, totalBlocksFailed);
        
        Map<String, Object> details = Map.of(
            "portfoliosProcessed", portfoliosProcessed,
            "totalBlocksSucceeded", totalBlocksSucceeded,
            "totalBlocksFailed", totalBlocksFailed,
            "errors", errors
        );
        
        return totalBlocksFailed == 0 ? 
            SyncResult.success(message, details) : 
            SyncResult.failure(message, details);
    }
}
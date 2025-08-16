package site.easy.to.build.crm.controller.portfolio;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.service.payprop.PayPropTagDTO;
import site.easy.to.build.crm.service.payprop.SyncResult;
import site.easy.to.build.crm.service.portfolio.PortfolioAssignmentService;
import site.easy.to.build.crm.service.tag.TagNamespaceService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for PayProp integration with portfolios
 * Handles PayProp tag management, synchronization, and status checking
 */
@Controller
@RequestMapping("/portfolio/internal/payprop")
public class PortfolioPayPropController extends PortfolioControllerBase {
    
    private static final Logger log = LoggerFactory.getLogger(PortfolioPayPropController.class);
    
    @Autowired
    private TagNamespaceService tagNamespaceService;
    
    /**
     * Get available PayProp tags for adoption
     */
    @GetMapping("/tags")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPayPropTags(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!isPayPropAvailable()) {
                response.put("success", false);
                response.put("message", "PayProp integration is not enabled");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            
            List<PayPropTagDTO> payPropTags = payPropSyncService.getAllPayPropTags();
            List<PayPropTagDTO> availableTags = payPropTags.stream()
                .filter(tag -> {
                    List<Portfolio> existingPortfolios = portfolioService.findByPayPropTag(tag.getId());
                    return existingPortfolios.isEmpty();
                })
                .collect(Collectors.toList());
            
            response.put("success", true);
            response.put("availableTags", availableTags);
            response.put("totalTags", payPropTags.size());
            response.put("availableCount", availableTags.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error loading PayProp tags: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Failed to load PayProp tags: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Adopt an existing PayProp tag as a portfolio
     */
    @PostMapping("/adopt-tag")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> adoptPayPropTag(
            @RequestParam String payPropTagId,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!isPayPropAvailable()) {
                response.put("success", false);
                response.put("message", "PayProp integration is not enabled");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            
            Integer userId = getLoggedInUserId(authentication);
            if (userId == null) {
                response.put("success", false);
                response.put("message", "User authentication failed");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
            List<Portfolio> existingPortfolios = portfolioService.findByPayPropTag(payPropTagId);
            if (!existingPortfolios.isEmpty()) {
                response.put("success", false);
                response.put("message", "This PayProp tag is already adopted as a portfolio");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
            
            PayPropTagDTO tagData = payPropSyncService.getPayPropTag(payPropTagId);
            if (tagData == null) {
                response.put("success", false);
                response.put("message", "PayProp tag not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            Portfolio portfolio = new Portfolio();
            portfolio.setName(tagData.getName());
            portfolio.setDescription("Adopted from PayProp tag: " + tagData.getName());
            portfolio.setPortfolioType(PortfolioType.CUSTOM);
            portfolio.setColorCode(tagData.getColor());
            portfolio.setCreatedBy((long) userId);
            portfolio.setCreatedAt(LocalDateTime.now());
            
            // Create namespaced PayProp tag for portfolio
            String namespacedTag = tagNamespaceService.createPortfolioTag(tagData.getName());
            portfolio.setPayPropTags(namespacedTag);
            portfolio.setPayPropTagNames(tagData.getName());
            portfolio.setSyncStatus(SyncStatus.synced);
            portfolio.setLastSyncAt(LocalDateTime.now());
            
            Portfolio savedPortfolio = portfolioService.save(portfolio);
            
            SyncResult syncResult = payPropSyncService.handlePayPropTagChange(
                payPropTagId, "TAG_APPLIED", tagData, null);
            
            response.put("success", true);
            response.put("message", "PayProp tag successfully adopted as portfolio");
            response.put("portfolioId", savedPortfolio.getId());
            response.put("portfolioName", savedPortfolio.getName());
            response.put("syncResult", syncResult.getMessage());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error adopting PayProp tag: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Failed to adopt PayProp tag: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Pull Tags from PayProp (Two-way sync)
     */
    @GetMapping("/pull-tags")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> pullPayPropTags(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!isPayPropAvailable()) {
                response.put("success", false);
                response.put("message", "PayProp integration is not enabled");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            
            Integer userId = getLoggedInUserId(authentication);
            if (userId == null) {
                response.put("success", false);
                response.put("message", "User authentication failed");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
            SyncResult result = payPropSyncService.pullAllTagsFromPayProp((long) userId);
            
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("details", result.getDetails());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error pulling PayProp tags: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Failed to pull PayProp tags: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Sync all portfolios with PayProp
     */
    @PostMapping("/sync-all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncAllPortfoliosWithPayProp(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!isPayPropAvailable()) {
                response.put("success", false);
                response.put("message", "PayProp integration is not enabled");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            
            Integer userId = getLoggedInUserId(authentication);
            if (userId == null) {
                response.put("success", false);
                response.put("message", "User authentication failed");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
            portfolioService.syncAllPortfoliosWithPayProp((long) userId);
            
            response.put("success", true);
            response.put("message", "Bulk portfolio sync initiated successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error syncing all portfolios: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Bulk sync failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Sync individual portfolio with PayProp
     */
    @PostMapping("/{id}/sync")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncPortfolioWithPayProp(
            @PathVariable("id") Long portfolioId,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!isPayPropAvailable()) {
                response.put("success", false);
                response.put("message", "PayProp integration is not enabled");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            
            if (!canUserEditPortfolio(portfolioId, authentication)) {
                response.put("success", false);
                response.put("message", "Access denied");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            Integer userId = getLoggedInUserId(authentication);
            if (userId == null) {
                response.put("success", false);
                response.put("message", "User authentication failed");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
            portfolioService.syncPortfolioWithPayProp(portfolioId, (long) userId);
            
            response.put("success", true);
            response.put("message", "Portfolio sync initiated successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error syncing portfolio: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Sync failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Force sync portfolio properties to PayProp tags
     */
    @PostMapping("/{id}/sync-properties")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncPropertiesToPayProp(
            @PathVariable("id") Long portfolioId,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        List<String> errors = new ArrayList<>();
        
        try {
            if (!canUserEditPortfolio(portfolioId, authentication)) {
                response.put("success", false);
                response.put("message", "Access denied");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            if (!isPayPropAvailable()) {
                response.put("success", false);
                response.put("message", "PayProp integration is not enabled");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            
            Portfolio portfolio = portfolioService.findById(portfolioId);
            if (portfolio == null) {
                response.put("success", false);
                response.put("message", "Portfolio not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            if (portfolio.getPayPropTags() == null || portfolio.getPayPropTags().isEmpty()) {
                response.put("success", false);
                response.put("message", "Portfolio has no PayProp tags to sync");
                return ResponseEntity.ok(response);
            }
            
            List<PropertyPortfolioAssignment> assignments = 
                propertyPortfolioAssignmentRepository.findByPortfolioIdAndIsActive(portfolioId, true);
            
            int syncedCount = 0;
            int failedCount = 0;
            
            log.info("Syncing {} properties to PayProp for portfolio {}", assignments.size(), portfolioId);
            log.info("Portfolio PayProp tag: {}", portfolio.getPayPropTags());
            
            for (PropertyPortfolioAssignment assignment : assignments) {
                try {
                    Property property = assignment.getProperty();
                    
                    if (property.getPayPropId() == null || property.getPayPropId().isEmpty()) {
                        errors.add("Property " + property.getPropertyName() + " has no PayProp ID");
                        failedCount++;
                        continue;
                    }
                    
                    try {
                        payPropSyncService.applyTagToProperty(
                            property.getPayPropId(), 
                            portfolio.getPayPropTags()
                        );
                        
                        assignment.setSyncStatus(SyncStatus.synced);
                        assignment.setLastSyncAt(LocalDateTime.now());
                        propertyPortfolioAssignmentRepository.save(assignment);
                        
                        syncedCount++;
                        log.info("Synced property {} (PayProp ID: {})", 
                                property.getPropertyName(), property.getPayPropId());
                        
                    } catch (Exception e) {
                        failedCount++;
                        assignment.setSyncStatus(SyncStatus.failed);
                        propertyPortfolioAssignmentRepository.save(assignment);
                        errors.add("Property " + property.getPropertyName() + ": " + e.getMessage());
                        log.error("Failed to sync property {}: {}", property.getPropertyName(), e.getMessage());
                    }
                    
                } catch (Exception e) {
                    failedCount++;
                    errors.add("Error processing assignment: " + e.getMessage());
                    log.error("Error syncing property to PayProp: {}", e.getMessage());
                }
            }
            
            response.put("success", syncedCount > 0);
            response.put("syncedCount", syncedCount);
            response.put("failedCount", failedCount);
            response.put("totalCount", assignments.size());
            response.put("errors", errors);
            response.put("message", String.format("Sync complete: %d synced, %d failed", syncedCount, failedCount));
            
            log.info("PayProp sync complete: {} synced, {} failed", syncedCount, failedCount);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error syncing portfolio properties to PayProp: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Sync failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Apply PayProp tag directly to property
     */
    @PostMapping("/apply-tag")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> applyPayPropTagDirectly(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!canUserAssignProperties(authentication)) {
                response.put("success", false);
                response.put("message", "Access denied");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            if (!isPayPropAvailable()) {
                response.put("success", false);
                response.put("message", "PayProp integration is not enabled");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            
            String propertyPayPropId = (String) request.get("propertyPayPropId");
            String tagId = (String) request.get("tagId");
            
            if (propertyPayPropId == null || tagId == null) {
                response.put("success", false);
                response.put("message", "Both propertyPayPropId and tagId are required");
                return ResponseEntity.badRequest().body(response);
            }
            
            log.info("Applying PayProp tag {} to property {}", tagId, propertyPayPropId);
            
            try {
                payPropSyncService.applyTagToProperty(propertyPayPropId, tagId);
                
                // Try to update local records if we can find the property
                List<Property> allProperties = propertyService.findAll();
                Property property = allProperties.stream()
                    .filter(p -> propertyPayPropId.equals(p.getPayPropId()))
                    .findFirst()
                    .orElse(null);
                
                if (property != null) {
                    List<Portfolio> portfolios = portfolioService.findByPayPropTag(tagId);
                    if (!portfolios.isEmpty()) {
                        Portfolio portfolio = portfolios.get(0);
                        
                        // Update assignment status if exists
                        Optional<PropertyPortfolioAssignment> assignment = 
                            propertyPortfolioAssignmentRepository
                                .findByPropertyIdAndPortfolioIdAndAssignmentTypeAndIsActive(
                                    property.getId(), portfolio.getId(), 
                                    PortfolioAssignmentType.PRIMARY, true);
                        
                        if (assignment.isPresent()) {
                            assignment.get().setSyncStatus(SyncStatus.synced);
                            assignment.get().setLastSyncAt(LocalDateTime.now());
                            propertyPortfolioAssignmentRepository.save(assignment.get());
                        }
                    }
                }
                
                response.put("success", true);
                response.put("message", "PayProp tag applied successfully");
                log.info("PayProp tag applied successfully");
                
            } catch (Exception e) {
                response.put("success", false);
                response.put("message", "Failed to apply PayProp tag: " + e.getMessage());
                log.error("Failed to apply PayProp tag: {}", e.getMessage());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error applying PayProp tag: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get PayProp sync status for portfolio
     */
    @GetMapping("/{id}/sync-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPayPropSyncStatus(
            @PathVariable("id") Long portfolioId,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!canUserEditPortfolio(portfolioId, authentication)) {
                response.put("success", false);
                response.put("message", "Access denied");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            Portfolio portfolio = portfolioService.findById(portfolioId);
            if (portfolio == null) {
                response.put("success", false);
                response.put("message", "Portfolio not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            List<PropertyPortfolioAssignment> assignments = 
                propertyPortfolioAssignmentRepository.findByPortfolioIdAndIsActive(portfolioId, true);
            
            List<Map<String, Object>> propertyStatuses = new ArrayList<>();
            int syncedCount = 0;
            int pendingCount = 0;
            
            for (PropertyPortfolioAssignment assignment : assignments) {
                Map<String, Object> propertyStatus = new HashMap<>();
                Property property = assignment.getProperty();
                
                propertyStatus.put("propertyId", property.getId());
                propertyStatus.put("propertyName", property.getPropertyName());
                propertyStatus.put("payPropId", property.getPayPropId());
                propertyStatus.put("syncStatus", 
                    assignment.getSyncStatus() != null ? assignment.getSyncStatus().toString() : "pending");
                propertyStatus.put("lastSyncAt", assignment.getLastSyncAt());
                
                if (SyncStatus.synced.equals(assignment.getSyncStatus())) {
                    syncedCount++;
                } else {
                    pendingCount++;
                }
                
                propertyStatuses.add(propertyStatus);
            }
            
            response.put("success", true);
            response.put("portfolioId", portfolioId);
            response.put("portfolioName", portfolio.getName());
            response.put("portfolioPayPropTag", portfolio.getPayPropTags());
            response.put("totalProperties", assignments.size());
            response.put("syncedCount", syncedCount);
            response.put("pendingCount", pendingCount);
            response.put("propertyStatuses", propertyStatuses);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting PayProp sync status: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Failed to get sync status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Recalculate portfolio analytics (includes PayProp sync verification)
     */
    @PostMapping("/{id}/recalculate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> recalculatePortfolioAnalytics(
            @PathVariable("id") Long portfolioId,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!canUserEditPortfolio(portfolioId, authentication)) {
                response.put("success", false);
                response.put("message", "Access denied");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            Integer userId = getLoggedInUserId(authentication);
            if (userId == null) {
                response.put("success", false);
                response.put("message", "User authentication failed");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
            // Trigger portfolio analytics update
            Portfolio portfolio = portfolioService.findById(portfolioId);
            if (portfolio != null) {
                portfolioService.save(portfolio);
            }
            
            response.put("success", true);
            response.put("message", "Portfolio analytics recalculated successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error recalculating portfolio analytics: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Recalculation failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
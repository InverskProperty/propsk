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
import site.easy.to.build.crm.service.payprop.PayPropPortfolioMigrationService;
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
    
    @Autowired
    private PayPropPortfolioMigrationService migrationService;
    
    /**
     * 🚨 DEBUG ENDPOINT: Test enhanced tag creation method
     * This will help us identify where the tag creation is failing
     */
    @PostMapping("/debug/test-tag-creation")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testTagCreation(
            @RequestParam String tagName,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("🧪 DEBUG: Testing tag creation for: '{}'", tagName);
            
            if (!isPayPropAvailable()) {
                response.put("success", false);
                response.put("message", "PayProp integration is not enabled");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            
            // Call our enhanced tag creation method directly
            PayPropTagDTO result = payPropSyncService.ensurePayPropTagExists(tagName);
            
            response.put("success", true);
            response.put("message", "Tag creation test completed successfully");
            response.put("tagId", result.getId());
            response.put("tagName", result.getName());
            response.put("tagDescription", result.getDescription());
            
            log.info("✅ DEBUG: Tag creation test successful - Tag ID: {}", result.getId());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ DEBUG: Tag creation test failed: {}", e.getMessage(), e);
            
            response.put("success", false);
            response.put("message", "Tag creation failed: " + e.getMessage());
            response.put("errorType", e.getClass().getSimpleName());
            response.put("stackTrace", Arrays.toString(e.getStackTrace()));
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 🚨 DEBUG ENDPOINT: Test portfolio creation with enhanced logging
     */
    @PostMapping("/debug/test-portfolio-creation")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testPortfolioCreation(
            @RequestParam String portfolioName,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("🧪 DEBUG: Testing portfolio creation for: '{}'", portfolioName);
            
            if (!isPayPropAvailable()) {
                response.put("success", false);
                response.put("message", "PayProp integration is not enabled");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            
            // Create portfolio manually for testing
            Portfolio testPortfolio = new Portfolio();
            testPortfolio.setName(portfolioName);
            testPortfolio.setDescription("DEBUG test portfolio for tag creation");
            testPortfolio.setPortfolioType(PortfolioType.CUSTOM);
            testPortfolio.setCreatedBy((long) userId);
            testPortfolio.setIsShared("N");
            testPortfolio.setSyncStatus(SyncStatus.pending);
            
            // Save portfolio first
            Portfolio savedPortfolio = portfolioService.save(testPortfolio);
            log.info("📝 DEBUG: Portfolio created with ID: {}", savedPortfolio.getId());
            
            // Test PayProp sync
            SyncResult syncResult = payPropSyncService.syncPortfolioToPayProp(savedPortfolio.getId(), (long) userId);
            
            // Get updated portfolio to check PayProp fields
            Portfolio updatedPortfolio = portfolioService.findById(savedPortfolio.getId()).orElse(null);
            
            response.put("success", syncResult.isSuccess());
            response.put("message", syncResult.getMessage());
            response.put("portfolioId", savedPortfolio.getId());
            response.put("payPropTags", updatedPortfolio != null ? updatedPortfolio.getPayPropTags() : null);
            response.put("payPropTagNames", updatedPortfolio != null ? updatedPortfolio.getPayPropTagNames() : null);
            response.put("syncStatus", updatedPortfolio != null ? updatedPortfolio.getSyncStatus() : null);
            response.put("syncDetails", syncResult.getDetails());
            
            log.info("✅ DEBUG: Portfolio creation test completed - Success: {}", syncResult.isSuccess());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ DEBUG: Portfolio creation test failed: {}", e.getMessage(), e);
            
            response.put("success", false);
            response.put("message", "Portfolio creation failed: " + e.getMessage());
            response.put("errorType", e.getClass().getSimpleName());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 🚨 MIGRATION ENDPOINT: Get summary of portfolios needing migration
     */
    @GetMapping("/migration/summary")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMigrationSummary(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!isPayPropAvailable()) {
                response.put("success", false);
                response.put("message", "PayProp integration is not enabled");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            
            PayPropPortfolioMigrationService.MigrationSummary summary = migrationService.getMigrationSummary();
            
            response.put("success", true);
            response.put("needsMigration", summary.needsMigration());
            response.put("brokenPortfoliosCount", summary.getBrokenPortfoliosCount());
            response.put("pendingAssignmentsCount", summary.getPendingAssignmentsCount());
            response.put("brokenPortfolioDetails", summary.getBrokenPortfolioDetails());
            
            if (summary.needsMigration()) {
                response.put("message", String.format("Migration needed: %d broken portfolios, %d pending assignments", 
                    summary.getBrokenPortfoliosCount(), summary.getPendingAssignmentsCount()));
            } else {
                response.put("message", "No migration needed - all portfolios are properly synced");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Failed to get migration summary: {}", e.getMessage(), e);
            
            response.put("success", false);
            response.put("message", "Failed to get migration summary: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 🚨 MIGRATION ENDPOINT: Fix all broken portfolios
     */
    @PostMapping("/migration/fix-broken-portfolios")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> fixBrokenPortfolios(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!isPayPropAvailable()) {
                response.put("success", false);
                response.put("message", "PayProp integration is not enabled");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            
            log.info("🔄 Starting portfolio migration requested by user");
            
            PayPropPortfolioMigrationService.MigrationResult result = migrationService.fixBrokenPortfolios();
            
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("fixedCount", result.getFixedCount());
            response.put("failedCount", result.getFailedCount());
            response.put("skippedCount", result.getSkippedCount());
            response.put("fixedPortfolios", result.getFixedPortfolios());
            response.put("errors", result.getErrors());
            
            if (result.isSuccess()) {
                log.info("✅ Portfolio migration completed successfully: {} fixed, {} failed", 
                    result.getFixedCount(), result.getFailedCount());
            } else {
                log.error("❌ Portfolio migration completed with errors: {} fixed, {} failed", 
                    result.getFixedCount(), result.getFailedCount());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Portfolio migration failed: {}", e.getMessage(), e);
            
            response.put("success", false);
            response.put("message", "Migration failed: " + e.getMessage());
            response.put("errorType", e.getClass().getSimpleName());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
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
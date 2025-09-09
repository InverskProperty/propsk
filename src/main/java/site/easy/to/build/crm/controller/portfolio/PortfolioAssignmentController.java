package site.easy.to.build.crm.controller.portfolio;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;
import site.easy.to.build.crm.service.portfolio.PortfolioAssignmentService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for property-portfolio assignment operations
 * Handles property assignment, bulk operations, and assignment status management
 */
@Controller
@RequestMapping("/portfolio/internal/assignment")
public class PortfolioAssignmentController extends PortfolioControllerBase {
    
    private static final Logger log = LoggerFactory.getLogger(PortfolioAssignmentController.class);
    
    @Autowired
    private CustomerPropertyAssignmentRepository customerPropertyAssignmentRepository;
    
    /**
     * Show properties assignment page
     */
    @GetMapping("/assign-properties")
    public String showAssignPropertiesPage(Model model, Authentication authentication) {
        if (!canUserAssignProperties(authentication)) {
            model.addAttribute("error", "Access denied");
            return "error/403";
        }
        
        try {
            List<Portfolio> portfolios = portfolioService.findPortfoliosForUser(authentication);
            List<Property> allProperties = propertyService.findAll();
            
            // Filter properties not assigned to any portfolio (truly unassigned)
            List<Property> unassignedProperties = allProperties.stream()
                .filter(property -> !hasAnyPortfolioAssignment(property.getId()))
                .filter(property -> !"Y".equals(property.getIsArchived()))
                .collect(Collectors.toList());
            
            log.info("Found {} truly unassigned properties (not in any portfolio)", unassignedProperties.size());
            
            model.addAttribute("portfolios", portfolios);
            model.addAttribute("unassignedProperties", unassignedProperties);
            model.addAttribute("allProperties", allProperties);
            model.addAttribute("pageTitle", "Assign Properties to Portfolios");
            
            return "portfolio/assign-properties";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading assignment page: " + e.getMessage());
            return "error/500";
        }
    }
    
    /**
     * Show portfolio-specific assignment page
     */
    @GetMapping("/{id}/assign")
    public String showPortfolioSpecificAssignmentPage(@PathVariable("id") Long portfolioId, 
                                                    Model model, 
                                                    Authentication authentication) {
        try {
            Portfolio targetPortfolio = portfolioService.findById(portfolioId);
            if (targetPortfolio == null) {
                model.addAttribute("error", "Portfolio not found");
                return "error/404";
            }
            
            if (!canUserEditPortfolio(portfolioId, authentication)) {
                model.addAttribute("error", "Access denied");
                return "error/403";
            }
            
            List<Portfolio> allPortfolios = portfolioService.findPortfoliosForUser(authentication);
            
            // Filter properties based on portfolio ownership using junction table logic
            List<Property> allProperties;
            List<Property> unassignedProperties;
            
            if (targetPortfolio.getPropertyOwnerId() != null) {
                // Owner-specific portfolio - only show properties for this owner
                Integer ownerId = targetPortfolio.getPropertyOwnerId();
                
                // Get properties for this owner
                allProperties = propertyService.findByPropertyOwnerId(ownerId.longValue());
                
                // Get properties already in this specific portfolio
                List<Property> propertiesInPortfolio = portfolioService.getPropertiesForPortfolio(portfolioId);
                Set<Long> assignedPropertyIds = propertiesInPortfolio.stream()
                    .map(Property::getId)
                    .collect(Collectors.toSet());
                
                // Filter to get properties NOT in this specific portfolio (available for assignment)
                unassignedProperties = allProperties.stream()
                    .filter(property -> !assignedPropertyIds.contains(property.getId()))
                    .filter(property -> !"Y".equals(property.getIsArchived()))
                    .collect(Collectors.toList());
                    
                log.info("Owner-specific portfolio {} for owner {} - showing {} total properties, {} already in this portfolio, {} available for assignment", 
                    portfolioId, ownerId, allProperties.size(), propertiesInPortfolio.size(), unassignedProperties.size());
            } else {
                // Shared portfolio - show properties not in this specific portfolio
                allProperties = propertyService.findAll();
                
                // Get properties already in this specific portfolio
                List<Property> propertiesInPortfolio = portfolioService.getPropertiesForPortfolio(portfolioId);
                Set<Long> assignedPropertyIds = propertiesInPortfolio.stream()
                    .map(Property::getId)
                    .collect(Collectors.toSet());
                
                unassignedProperties = allProperties.stream()
                    .filter(property -> !assignedPropertyIds.contains(property.getId()))
                    .filter(property -> !"Y".equals(property.getIsArchived()))
                    .collect(Collectors.toList());
                    
                log.info("Shared portfolio {} - showing {} total properties, {} already in this portfolio, {} available for assignment", 
                    portfolioId, allProperties.size(), propertiesInPortfolio.size(), unassignedProperties.size());
            }
            
            model.addAttribute("targetPortfolio", targetPortfolio);
            model.addAttribute("portfolios", allPortfolios);
            model.addAttribute("unassignedProperties", unassignedProperties);
            model.addAttribute("allProperties", allProperties);
            model.addAttribute("pageTitle", "Assign Properties to " + targetPortfolio.getName());
            model.addAttribute("isPortfolioSpecific", true);
            
            return "portfolio/assign-properties";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading assignment page: " + e.getMessage());
            return "error/500";
        }
    }
    
    /**
     * Get available properties for assignment to a portfolio
     */
    @GetMapping("/{id}/available-properties")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAvailableProperties(@PathVariable("id") Long portfolioId,
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
            
            List<Property> availableProperties;
            
            if (portfolio.getPropertyOwnerId() != null) {
                // Owner-specific portfolio - only show properties for this owner
                Integer ownerId = portfolio.getPropertyOwnerId();
                log.info("Portfolio {} is owner-specific for owner ID: {}", portfolioId, ownerId);
                
                List<Property> ownerProperties = propertyService.findByPropertyOwnerId(ownerId.longValue());
                log.info("Owner has {} total properties from junction table", ownerProperties.size());
                
                // Get properties already in this portfolio using junction table
                List<Property> propertiesInPortfolio = portfolioService.getPropertiesForPortfolio(portfolioId);
                log.info("Portfolio already has {} properties", propertiesInPortfolio.size());
                
                // Create set of IDs already in portfolio
                Set<Long> assignedPropertyIds = propertiesInPortfolio.stream()
                    .map(Property::getId)
                    .collect(Collectors.toSet());
                
                // Filter to get properties NOT already in this portfolio
                availableProperties = ownerProperties.stream()
                    .filter(property -> !assignedPropertyIds.contains(property.getId()))
                    .filter(property -> !"Y".equals(property.getIsArchived()))
                    .collect(Collectors.toList());
                    
                log.info("Owner {} - found {} total properties, {} already in portfolio, {} available for assignment", 
                    ownerId, ownerProperties.size(), propertiesInPortfolio.size(), availableProperties.size());
                
            } else {
                // Shared portfolio - show all properties not in this portfolio
                log.info("Portfolio {} is a shared portfolio", portfolioId);
                
                List<Property> allProperties = propertyService.findAll();
                log.info("Total properties in system: {}", allProperties.size());
                
                // Get properties already in this portfolio
                List<Property> propertiesInPortfolio = portfolioService.getPropertiesForPortfolio(portfolioId);
                log.info("Portfolio already has {} properties", propertiesInPortfolio.size());
                
                Set<Long> assignedPropertyIds = propertiesInPortfolio.stream()
                    .map(Property::getId)
                    .collect(Collectors.toSet());
                
                availableProperties = allProperties.stream()
                    .filter(property -> !assignedPropertyIds.contains(property.getId()))
                    .filter(property -> !"Y".equals(property.getIsArchived()))
                    .collect(Collectors.toList());
                    
                log.info("Shared portfolio - {} total properties, {} already in portfolio, {} available for assignment", 
                    allProperties.size(), propertiesInPortfolio.size(), availableProperties.size());
            }
            
            // Convert to simple format for frontend
            List<Map<String, Object>> propertyData = availableProperties.stream()
                .map(property -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", property.getId());
                    map.put("name", property.getPropertyName());
                    map.put("address", property.getFullAddress());
                    map.put("payPropId", property.getPayPropId());
                    return map;
                })
                .collect(Collectors.toList());
            
            response.put("success", true);
            response.put("availableProperties", propertyData);
            response.put("totalCount", availableProperties.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting available properties for portfolio {}: {}", portfolioId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Failed to get available properties: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get owner properties for assignment
     */
    @GetMapping("/owner/{ownerId}/properties")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getOwnerProperties(@PathVariable Integer ownerId) {
        try {
            List<CustomerPropertyAssignment> assignments = 
                customerPropertyAssignmentRepository.findByCustomerCustomerIdAndAssignmentType(
                    ownerId.longValue(), AssignmentType.OWNER);
            
            List<Map<String, Object>> propertyList = assignments.stream()
                .map(assignment -> {
                    Property property = assignment.getProperty();
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", property.getId());
                    map.put("name", property.getPropertyName());
                    map.put("address", property.getFullAddress());
                    map.put("payPropId", property.getPayPropId());
                    
                    // Check if property is already assigned to any portfolio
                    boolean hasPortfolioAssignment = hasAnyPortfolioAssignment(property.getId());
                    map.put("hasPortfolioAssignment", hasPortfolioAssignment);
                    
                    return map;
                })
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(propertyList);
            
        } catch (Exception e) {
            log.error("Error getting properties for owner {}: {}", ownerId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ArrayList<>());
        }
    }
    
    /**
     * Bulk assign properties to portfolio
     */
    @PostMapping("/bulk-assign")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> bulkAssignProperties(
            @RequestParam("portfolioId") Long portfolioId,
            @RequestParam("propertyIds") List<Long> propertyIds,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!canUserAssignProperties(authentication)) {
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
            
            portfolioService.assignPropertiesToPortfolio(portfolioId, propertyIds, (long) userId);
            
            response.put("success", true);
            response.put("message", propertyIds.size() + " properties assigned successfully");
            response.put("assignedCount", propertyIds.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error in bulk assignment: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Assignment failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Assign properties to portfolio using enhanced service
     */
    @PostMapping("/{id}/assign-properties")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> assignPropertiesToPortfolio(
            @PathVariable("id") Long portfolioId,
            @RequestParam("propertyIds") List<Long> propertyIds,
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
            
            // Use the enhanced service that handles junction table + PayProp
            PortfolioAssignmentService.AssignmentResult result = 
                portfolioAssignmentService.assignPropertiesToPortfolio(
                    portfolioId, propertyIds, (long) userId);
            
            response.put("success", result.isSuccess());
            response.put("message", result.getSummary());
            response.put("assignedCount", result.getAssignedCount());
            response.put("syncedCount", result.getSyncedCount());
            response.put("skippedCount", result.getSkippedCount());
            response.put("errors", result.getErrors());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to assign properties to portfolio {}: {}", portfolioId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Assignment failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Remove property from portfolio
     */
    @PostMapping("/{portfolioId}/remove-property/{propertyId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> removePropertyFromPortfolio(
            @PathVariable("portfolioId") Long portfolioId,
            @PathVariable("propertyId") Long propertyId,
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
            
            // Remove the property assignment
            portfolioAssignmentService.removePropertyFromPortfolio(portfolioId, propertyId, (long) userId);
            
            response.put("success", true);
            response.put("message", "Property removed from portfolio successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to remove property {} from portfolio {}: {}", 
                propertyId, portfolioId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Removal failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get assignment status for a portfolio
     */
    @GetMapping("/{id}/assignment-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAssignmentStatus(
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
            
            // Get all assignments
            List<PropertyPortfolioAssignment> assignments = 
                propertyPortfolioAssignmentRepository.findByPortfolioIdAndIsActive(portfolioId, true);
            
            List<Map<String, Object>> assignmentData = assignments.stream()
                .map(assignment -> {
                    Map<String, Object> data = new HashMap<>();
                    Property property = assignment.getProperty();
                    
                    data.put("propertyId", property.getId());
                    data.put("propertyName", property.getPropertyName());
                    data.put("address", property.getFullAddress());
                    data.put("assignmentType", assignment.getAssignmentType());
                    data.put("isActive", assignment.getIsActive());
                    data.put("assignedAt", assignment.getAssignedAt());
                    data.put("syncStatus", assignment.getSyncStatus());
                    data.put("lastSyncAt", assignment.getLastSyncAt());
                    
                    return data;
                })
                .collect(Collectors.toList());
            
            response.put("success", true);
            response.put("portfolioId", portfolioId);
            response.put("portfolioName", portfolio.getName());
            response.put("totalAssignments", assignments.size());
            response.put("assignments", assignmentData);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting assignment status for portfolio {}: {}", portfolioId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Failed to get assignment status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    // ===== BLOCK ASSIGNMENT ENDPOINTS (Task 4.2) =====
    
    /**
     * Assign properties to a specific block within a portfolio
     * POST /portfolio/internal/assignment/blocks/{blockId}/assign-properties
     */
    @PostMapping("/blocks/{blockId}/assign-properties")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> assignPropertiesToBlock(
            @PathVariable("blockId") Long blockId,
            @RequestParam("propertyIds") List<Long> propertyIds,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        log.info("🏗️ Assigning {} properties to block {}", propertyIds.size(), blockId);
        
        try {
            // Validate block exists and get portfolio
            Optional<Block> blockOpt = portfolioBlockService.findById(blockId);
            if (!blockOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "Block not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            Block block = blockOpt.get();
            Long portfolioId = block.getPortfolio().getId(); // Valid - Block has portfolio FK
            
            // Check permissions for the portfolio
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
            
            // Use the enhanced service for block assignment
            PortfolioAssignmentService.AssignmentResult result = 
                portfolioAssignmentService.assignPropertiesToBlock(
                    portfolioId, blockId, propertyIds, (long) userId);
            
            response.put("success", result.isSuccess());
            response.put("message", result.isSuccess() ? "Properties assigned successfully" : "Some properties failed to assign");
            response.put("assignedCount", result.getAssignedCount());
            response.put("skippedCount", result.getSkippedCount());
            response.put("errorCount", result.getErrors().size());
            
            if (!result.getErrors().isEmpty()) {
                response.put("errors", result.getErrors());
            }
            
            log.info("✅ Block assignment completed: {} assigned, {} skipped, {} errors", 
                    result.getAssignedCount(), result.getSkippedCount(), result.getErrors().size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Failed to assign properties to block {}: {}", blockId, e.getMessage());
            response.put("success", false);
            response.put("message", "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Move properties between blocks
     * POST /portfolio/internal/assignment/blocks/move-properties
     */
    @PostMapping("/blocks/move-properties")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> movePropertiesBetweenBlocks(
            @RequestParam("sourceBlockId") Long sourceBlockId,
            @RequestParam("targetBlockId") Long targetBlockId,
            @RequestParam("propertyIds") List<Long> propertyIds,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        log.info("🔄 Moving {} properties from block {} to block {}", 
                propertyIds.size(), sourceBlockId, targetBlockId);
        
        try {
            // Validate source block
            Optional<Block> sourceBlockOpt = portfolioBlockService.findById(sourceBlockId);
            if (!sourceBlockOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "Source block not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            // Validate target block
            Optional<Block> targetBlockOpt = portfolioBlockService.findById(targetBlockId);
            if (!targetBlockOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "Target block not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            Block sourceBlock = sourceBlockOpt.get();
            Block targetBlock = targetBlockOpt.get();
            
            // Ensure both blocks are in the same portfolio
            if (!sourceBlock.getPortfolio().getId().equals(targetBlock.getPortfolio().getId())) {
                response.put("success", false);
                response.put("message", "Blocks must be in the same portfolio");
                return ResponseEntity.badRequest().body(response);
            }
            
            Long portfolioId = sourceBlock.getPortfolio().getId();
            
            // Check permissions
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
            
            // Check target block capacity if set
            if (portfolioBlockService.isBlockAtCapacity(targetBlockId)) {
                Integer availableCapacity = portfolioBlockService.getAvailableCapacity(targetBlockId);
                if (availableCapacity != null && propertyIds.size() > availableCapacity) {
                    response.put("success", false);
                    response.put("message", String.format("Target block has capacity for only %d more properties, but %d were requested", 
                                                        availableCapacity, propertyIds.size()));
                    return ResponseEntity.badRequest().body(response);
                }
            }
            
            // Use the enhanced service for moving properties
            PortfolioAssignmentService.AssignmentResult result = 
                portfolioAssignmentService.movePropertiesBetweenBlocks(
                    portfolioId, sourceBlockId, targetBlockId, propertyIds, (long) userId);
            
            response.put("success", result.isSuccess());
            response.put("message", result.isSuccess() ? "Properties moved successfully" : "Some properties failed to move");
            response.put("movedCount", result.getAssignedCount());
            response.put("skippedCount", result.getSkippedCount());
            response.put("errorCount", result.getErrors().size());
            
            if (!result.getErrors().isEmpty()) {
                response.put("errors", result.getErrors());
            }
            
            log.info("✅ Property move completed: {} moved, {} skipped, {} errors", 
                    result.getAssignedCount(), result.getSkippedCount(), result.getErrors().size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Failed to move properties between blocks: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Remove properties from a block (move to portfolio-only assignment)
     * POST /portfolio/internal/assignment/blocks/{blockId}/remove-properties
     */
    @PostMapping("/blocks/{blockId}/remove-properties")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> removePropertiesFromBlock(
            @PathVariable("blockId") Long blockId,
            @RequestParam("propertyIds") List<Long> propertyIds,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        log.info("🗑️ Removing {} properties from block {}", propertyIds.size(), blockId);
        
        try {
            // Validate block exists
            Optional<Block> blockOpt = portfolioBlockService.findById(blockId);
            if (!blockOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "Block not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            Block block = blockOpt.get();
            Long portfolioId = block.getPortfolio().getId();
            
            // Check permissions
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
            
            // Use the enhanced service for removing properties from block
            PortfolioAssignmentService.AssignmentResult result = 
                portfolioAssignmentService.removePropertiesFromBlock(
                    portfolioId, blockId, propertyIds, (long) userId);
            
            response.put("success", result.isSuccess());
            response.put("message", result.isSuccess() ? "Properties removed successfully" : "Some properties failed to remove");
            response.put("removedCount", result.getAssignedCount());
            response.put("skippedCount", result.getSkippedCount());
            response.put("errorCount", result.getErrors().size());
            
            if (!result.getErrors().isEmpty()) {
                response.put("errors", result.getErrors());
            }
            
            log.info("✅ Property removal completed: {} removed, {} skipped, {} errors", 
                    result.getAssignedCount(), result.getSkippedCount(), result.getErrors().size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Failed to remove properties from block {}: {}", blockId, e.getMessage());
            response.put("success", false);
            response.put("message", "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get properties organized by blocks within a portfolio
     * GET /portfolio/internal/assignment/{portfolioId}/blocks-view
     */
    @GetMapping("/{portfolioId}/blocks-view")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPropertiesByBlocksInPortfolio(
            @PathVariable("portfolioId") Long portfolioId,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        log.info("📊 Getting properties organized by blocks for portfolio {}", portfolioId);
        
        try {
            // Check permissions
            if (!canUserEditPortfolio(portfolioId, authentication)) {
                response.put("success", false);
                response.put("message", "Access denied");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            // Get portfolio
            Portfolio portfolio = portfolioService.findById(portfolioId);
            if (portfolio == null) {
                response.put("success", false);
                response.put("message", "Portfolio not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            // Use the enhanced service to get organized properties
            Map<Block, List<Property>> organizedProperties = 
                portfolioAssignmentService.getPropertiesByBlocksInPortfolio(portfolioId);
            
            // Convert to response format
            Map<String, Object> blocksData = new HashMap<>();
            
            for (Map.Entry<Block, List<Property>> entry : organizedProperties.entrySet()) {
                Block block = entry.getKey();
                List<Property> properties = entry.getValue();
                
                String blockKey = block != null ? 
                    String.format("Block: %s (ID: %d)", block.getName(), block.getId()) :
                    "portfolio-only";
                
                List<Map<String, Object>> propertyData = properties.stream()
                    .map(property -> {
                        Map<String, Object> data = new HashMap<>();
                        data.put("id", property.getId());
                        data.put("propertyName", property.getPropertyName());
                        data.put("address", property.getFullAddress());
                        data.put("payPropId", property.getPayPropId());
                        return data;
                    })
                    .collect(Collectors.toList());
                
                Map<String, Object> blockInfo = new HashMap<>();
                blockInfo.put("properties", propertyData);
                blockInfo.put("propertyCount", properties.size());
                
                // If it's a block (not "portfolio-only"), add block details
                if (block != null) {
                    blockInfo.put("blockId", block.getId());
                    blockInfo.put("blockName", block.getName());
                    blockInfo.put("maxProperties", block.getMaxProperties());
                    blockInfo.put("availableCapacity", portfolioBlockService.getAvailableCapacity(block.getId()));
                }
                
                blocksData.put(blockKey, blockInfo);
            }
            
            response.put("success", true);
            response.put("portfolioId", portfolioId);
            response.put("portfolioName", portfolio.getName());
            response.put("blocks", blocksData);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Failed to get block view for portfolio {}: {}", portfolioId, e.getMessage());
            response.put("success", false);
            response.put("message", "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    // ===== UTILITY METHODS =====
    
    /**
     * Check if property has any portfolio assignments
     */
    private boolean hasAnyPortfolioAssignment(Long propertyId) {
        try {
            List<PropertyPortfolioAssignment> assignments = 
                propertyPortfolioAssignmentRepository.findByPropertyIdAndIsActive(propertyId, true);
            return !assignments.isEmpty();
        } catch (Exception e) {
            log.error("Error checking portfolio assignments for property {}: {}", propertyId, e.getMessage());
            return false;
        }
    }
}
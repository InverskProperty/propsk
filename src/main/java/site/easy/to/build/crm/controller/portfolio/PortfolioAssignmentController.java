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
            
            // Filter unassigned properties using junction table
            List<Property> unassignedProperties = allProperties.stream()
                .filter(property -> !hasAnyPortfolioAssignment(property.getId()))
                .filter(property -> !"Y".equals(property.getIsArchived()))
                .collect(Collectors.toList());
            
            log.info("Found {} unassigned properties using junction table logic", unassignedProperties.size());
            
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
                
                // Filter to get unassigned properties using junction table logic
                unassignedProperties = allProperties.stream()
                    .filter(property -> !hasAnyPortfolioAssignment(property.getId()))
                    .filter(property -> !"Y".equals(property.getIsArchived()))
                    .collect(Collectors.toList());
                    
                log.info("Owner-specific portfolio {} for owner {} - showing {} total properties, {} unassigned", 
                    portfolioId, ownerId, allProperties.size(), unassignedProperties.size());
            } else {
                // Shared portfolio - show all unassigned properties
                allProperties = propertyService.findAll();
                unassignedProperties = allProperties.stream()
                    .filter(property -> !hasAnyPortfolioAssignment(property.getId()))
                    .filter(property -> !"Y".equals(property.getIsArchived()))
                    .collect(Collectors.toList());
                    
                log.info("Shared portfolio {} - showing {} total properties, {} unassigned", 
                    portfolioId, allProperties.size(), unassignedProperties.size());
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
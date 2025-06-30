// PortfolioController.java - Unified portfolio management for employees and property owners
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import site.easy.to.build.crm.controller.PortfolioController.PortfolioWithAnalytics;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.service.portfolio.PortfolioService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.payprop.PayPropPortfolioSyncService;
import site.easy.to.build.crm.service.payprop.PayPropTagDTO;
import site.easy.to.build.crm.service.payprop.SyncResult;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.util.AuthorizationUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PortfolioController - Unified portfolio dashboard for both employees and property owners
 * Handles portfolio organization, analytics, and PayProp synchronization
 */
@Controller
@RequestMapping("/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PropertyService propertyService;
    private final AuthenticationUtils authenticationUtils;
    
    // Add customer service for manager functionality
    @Autowired(required = false)
    private CustomerService customerService;
    
    // Make PayProp service optional
    @Autowired(required = false)
    private PayPropPortfolioSyncService payPropSyncService;
    
    @Value("${payprop.enabled:false}")
    private boolean payPropEnabled;

    @Autowired
    public PortfolioController(PortfolioService portfolioService,
                              PropertyService propertyService,
                              AuthenticationUtils authenticationUtils) {
        this.portfolioService = portfolioService;
        this.propertyService = propertyService;
        this.authenticationUtils = authenticationUtils;
    }

    // ===== UNIFIED DASHBOARD =====

    /**
     * Portfolio Dashboard - Main entry point for both employees and property owners
     */
    @GetMapping("/dashboard")
    public String portfolioDashboard(Model model, Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            
            // Get portfolios based on user role
            List<Portfolio> userPortfolios = portfolioService.findPortfoliosForUser(authentication);
            
            // Calculate aggregate statistics across all user's portfolios
            PortfolioAggregateStats aggregateStats = calculateAggregateStats(userPortfolios);
            
            // Get recent analytics for each portfolio
            List<PortfolioWithAnalytics> portfoliosWithAnalytics = userPortfolios.stream()
                .map(portfolio -> {
                    PortfolioAnalytics analytics = portfolioService.getLatestPortfolioAnalytics(portfolio.getId());
                    return new PortfolioWithAnalytics(portfolio, analytics);
                })
                .collect(Collectors.toList());
            
            // Role-based permissions
            boolean canCreatePortfolio = AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") ||
                                       AuthorizationUtil.hasRole(authentication, "ROLE_CUSTOMER");
            boolean canSyncPayProp = (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") ||
                                   AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")) && 
                                   payPropEnabled && payPropSyncService != null;
            boolean isPropertyOwner = AuthorizationUtil.hasRole(authentication, "ROLE_CUSTOMER");
            
            // Add model attributes
            model.addAttribute("portfolios", portfoliosWithAnalytics);
            model.addAttribute("aggregateStats", aggregateStats);
            model.addAttribute("canCreatePortfolio", canCreatePortfolio);
            model.addAttribute("canSyncPayProp", canSyncPayProp);
            model.addAttribute("isPropertyOwner", isPropertyOwner);
            model.addAttribute("payPropEnabled", payPropEnabled);
            model.addAttribute("pageTitle", "Portfolio Dashboard");
            
            // Determine view based on role
            return isPropertyOwner ? "portfolio/property-owner-dashboard" : "portfolio/employee-dashboard";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading portfolio dashboard: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Portfolio Details - View specific portfolio with full analytics
     */
    @GetMapping("/{id}")
    public String viewPortfolio(@PathVariable("id") Long portfolioId, Model model, Authentication authentication) {
        try {
            // Check access permissions
            if (!portfolioService.canUserAccessPortfolio(portfolioId, authentication)) {
                return "redirect:/access-denied";
            }
            
            Portfolio portfolio = portfolioService.findById(portfolioId);
            if (portfolio == null) {
                return "error/not-found";
            }
            
            // Get latest analytics
            PortfolioAnalytics analytics = portfolioService.getLatestPortfolioAnalytics(portfolioId);
            if (analytics == null) {
                // Calculate if not exists
                analytics = portfolioService.calculatePortfolioAnalytics(portfolioId, LocalDate.now());
            }
            
            // Get blocks in this portfolio
            List<Block> blocks = portfolioService.findBlocksByPortfolio(portfolioId);
            
            // Get properties in this portfolio
            List<Property> properties = propertyService.findByPortfolioId(portfolioId);
            
            // Get analytics history for charts (last 6 months)
            LocalDate sixMonthsAgo = LocalDate.now().minusMonths(6);
            List<PortfolioAnalytics> analyticsHistory = portfolioService
                .getPortfolioAnalyticsHistory(portfolioId, sixMonthsAgo, LocalDate.now());
            
            // Role-based permissions
            boolean canEdit = canUserEditPortfolio(portfolioId, authentication);
            boolean canManageProperties = AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") ||
                                        AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE");
            
            model.addAttribute("portfolio", portfolio);
            model.addAttribute("analytics", analytics);
            model.addAttribute("blocks", blocks);
            model.addAttribute("properties", properties);
            model.addAttribute("analyticsHistory", analyticsHistory);
            model.addAttribute("canEdit", canEdit);
            model.addAttribute("canManageProperties", canManageProperties);
            model.addAttribute("payPropEnabled", payPropEnabled);
            model.addAttribute("pageTitle", portfolio.getName());
            
            return "portfolio/portfolio-details";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading portfolio: " + e.getMessage());
            return "error/500";
        }
    }

    // ===== PORTFOLIO MANAGEMENT =====

    /**
     * Enhanced Create Portfolio Form for Managers
     */
    @GetMapping("/create")
    public String showCreatePortfolioForm(Model model, Authentication authentication) {
        if (!canUserCreatePortfolio(authentication)) {
            return "redirect:/access-denied";
        }
        
        Portfolio portfolio = new Portfolio();
        
        // For managers, provide list of property owners to choose from
        if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            // Get all property owners - use existing methods that work
            if (customerService != null) {
                try {
                    // Use existing customer service methods
                    List<Customer> allCustomers = customerService.findAll();
                    List<Customer> propertyOwners = allCustomers.stream()
                        .filter(customer -> customer.getIsPropertyOwner() != null && customer.getIsPropertyOwner())
                        .collect(Collectors.toList());
                    model.addAttribute("propertyOwners", propertyOwners);
                } catch (Exception e) {
                    System.out.println("Property owners filtering failed: " + e.getMessage());
                    model.addAttribute("propertyOwners", new ArrayList<>());
                }
            }
            model.addAttribute("isManager", true);
        } else {
            // For property owners, auto-set themselves
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            portfolio.setPropertyOwnerId(userId);
            model.addAttribute("isManager", false);
        }
        
        model.addAttribute("portfolio", portfolio);
        model.addAttribute("portfolioTypes", PortfolioType.values());
        model.addAttribute("payPropEnabled", payPropEnabled);
        model.addAttribute("pageTitle", "Create Portfolio");
        
        return "portfolio/create-portfolio";
    }

    /**
     * Get available PayProp tags for adoption
     */
    @GetMapping("/payprop-tags")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPayPropTags(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Check if PayProp is enabled and service is available
            if (!payPropEnabled || payPropSyncService == null) {
                response.put("success", false);
                response.put("message", "PayProp integration is not enabled");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && 
                !AuthorizationUtil.hasRole(authentication, "ROLE_PROPERTY_OWNER")) {
                response.put("success", false);
                response.put("message", "Access denied");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            
            // Get all PayProp tags via the sync service
            List<PayPropTagDTO> payPropTags = payPropSyncService.getAllPayPropTags();
            
            // Filter out tags that are already adopted as portfolios
            List<PayPropTagDTO> availableTags = payPropTags.stream()
                .filter(tag -> {
                    List<Portfolio> existingPortfolios = portfolioService.findByPayPropTag(tag.getId());
                    return existingPortfolios.isEmpty();
                })
                .collect(Collectors.toList());
            
            response.put("success", true);
            response.put("tags", availableTags);
            response.put("totalTags", payPropTags.size());
            response.put("availableTags", availableTags.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to load PayProp tags: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Adopt an existing PayProp tag as a portfolio
     */
    @PostMapping("/adopt-payprop-tag")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> adoptPayPropTag(
            @RequestParam String payPropTagId,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Check if PayProp is enabled and service is available
            if (!payPropEnabled || payPropSyncService == null) {
                response.put("success", false);
                response.put("message", "PayProp integration is not enabled");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            
            if (!canUserCreatePortfolio(authentication)) {
                response.put("success", false);
                response.put("message", "Access denied");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            
            // Check if tag is already adopted
            List<Portfolio> existingPortfolios = portfolioService.findByPayPropTag(payPropTagId);
            if (!existingPortfolios.isEmpty()) {
                response.put("success", false);
                response.put("message", "This PayProp tag is already adopted as a portfolio");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
            
            // Get tag details from PayProp
            PayPropTagDTO tagData = payPropSyncService.getPayPropTag(payPropTagId);
            if (tagData == null) {
                response.put("success", false);
                response.put("message", "PayProp tag not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            // Create portfolio from PayProp tag
            Portfolio portfolio = new Portfolio();
            portfolio.setName(tagData.getName());
            portfolio.setDescription("Adopted from PayProp tag: " + tagData.getName());
            portfolio.setPortfolioType(PortfolioType.CUSTOM);
            portfolio.setColorCode(tagData.getColor());
            portfolio.setCreatedBy((long) userId);
            
            // Set owner for property owner users
            if (AuthorizationUtil.hasRole(authentication, "ROLE_PROPERTY_OWNER")) {
                portfolio.setPropertyOwnerId(userId);
                portfolio.setIsShared("N");
            } else {
                portfolio.setIsShared("Y");
            }
            
            // Set PayProp sync information
            portfolio.setPayPropTags(payPropTagId);
            portfolio.setPayPropTagNames(tagData.getName());
            portfolio.setSyncStatus(SyncStatus.SYNCED);
            portfolio.setLastSyncAt(LocalDateTime.now());
            
            Portfolio savedPortfolio = portfolioService.save(portfolio);
            
            // Sync properties that already have this tag in PayProp
            SyncResult syncResult = payPropSyncService.handlePayPropTagChange(
                payPropTagId, "TAG_APPLIED", tagData, null);
            
            response.put("success", true);
            response.put("message", "PayProp tag successfully adopted as portfolio");
            response.put("portfolioId", savedPortfolio.getId());
            response.put("portfolioName", savedPortfolio.getName());
            response.put("syncResult", syncResult.getMessage());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to adopt PayProp tag: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Enhanced Create Portfolio Processing
     */
    @PostMapping("/create")
    public String createPortfolio(@ModelAttribute("portfolio") @Validated Portfolio portfolio,
                                 BindingResult bindingResult,
                                 @RequestParam(value = "selectedOwnerId", required = false) Integer selectedOwnerId,
                                 @RequestParam(value = "enablePayPropSync", defaultValue = "false") boolean enablePayPropSync,
                                 Authentication authentication,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        
        if (!canUserCreatePortfolio(authentication)) {
            return "redirect:/access-denied";
        }
        
        if (bindingResult.hasErrors()) {
            // Re-populate model for form redisplay
            if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                if (customerService != null) {
                    try {
                        List<Customer> allCustomers = customerService.findAll();
                        List<Customer> propertyOwners = allCustomers.stream()
                            .filter(customer -> customer.getIsPropertyOwner() != null && customer.getIsPropertyOwner())
                            .collect(Collectors.toList());
                        model.addAttribute("propertyOwners", propertyOwners);
                    } catch (Exception e) {
                        model.addAttribute("propertyOwners", new ArrayList<>());
                    }
                }
                model.addAttribute("isManager", true);
            }
            model.addAttribute("portfolioTypes", PortfolioType.values());
            model.addAttribute("payPropEnabled", payPropEnabled);
            return "portfolio/create-portfolio";
        }
        
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            
            // Handle property owner assignment
            if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                // Manager can create for specific owner or as shared
                if (selectedOwnerId != null && selectedOwnerId > 0) {
                    portfolio.setPropertyOwnerId(selectedOwnerId);
                    portfolio.setIsShared("N"); // Owner-specific
                } else {
                    portfolio.setPropertyOwnerId(null);
                    portfolio.setIsShared("Y"); // Shared portfolio
                }
            } else {
                // Property owners create for themselves
                portfolio.setPropertyOwnerId(userId);
                portfolio.setIsShared("N");
            }
            
            // Create the portfolio
            Portfolio savedPortfolio = portfolioService.createPortfolio(
                portfolio.getName(),
                portfolio.getDescription(),
                portfolio.getPortfolioType(),
                portfolio.getPropertyOwnerId(),
                (long) userId
            );
            
            // Set additional properties
            savedPortfolio.setTargetMonthlyIncome(portfolio.getTargetMonthlyIncome());
            savedPortfolio.setTargetOccupancyRate(portfolio.getTargetOccupancyRate());
            savedPortfolio.setColorCode(portfolio.getColorCode());
            savedPortfolio.setIsShared(portfolio.getIsShared());
            
            // Handle PayProp sync if enabled and user has permission
            if (enablePayPropSync && payPropEnabled && payPropSyncService != null && 
                AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                
                try {
                    portfolioService.syncPortfolioWithPayProp(savedPortfolio.getId(), (long) userId);
                    redirectAttributes.addFlashAttribute("successMessage", 
                        "Portfolio '" + savedPortfolio.getName() + "' created and synced to PayProp successfully!");
                } catch (Exception e) {
                    // Portfolio created but sync failed
                    redirectAttributes.addFlashAttribute("warningMessage", 
                        "Portfolio '" + savedPortfolio.getName() + "' created successfully, but PayProp sync failed: " + e.getMessage());
                }
            } else {
                redirectAttributes.addFlashAttribute("successMessage", 
                    "Portfolio '" + savedPortfolio.getName() + "' created successfully!");
            }
            
            portfolioService.save(savedPortfolio);
            
            return "redirect:/portfolio/" + savedPortfolio.getId();
            
        } catch (Exception e) {
            model.addAttribute("error", "Failed to create portfolio: " + e.getMessage());
            
            // Re-populate model for form redisplay
            if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                if (customerService != null) {
                    try {
                        List<Customer> allCustomers = customerService.findAll();
                        List<Customer> propertyOwners = allCustomers.stream()
                            .filter(customer -> customer.getIsPropertyOwner() != null && customer.getIsPropertyOwner())
                            .collect(Collectors.toList());
                        model.addAttribute("propertyOwners", propertyOwners);
                    } catch (Exception e2) {
                        model.addAttribute("propertyOwners", new ArrayList<>());
                    }
                }
                model.addAttribute("isManager", true);
            }
            model.addAttribute("portfolioTypes", PortfolioType.values());
            model.addAttribute("payPropEnabled", payPropEnabled);
            return "portfolio/create-portfolio";
        }
    }

    /**
     * Get All Portfolios (Manager View) - SIMPLIFIED VERSION
     */
    @GetMapping("/all")
    public String showAllPortfolios(Model model, Authentication authentication,
                                   @RequestParam(value = "owner", required = false) Integer ownerId,
                                   @RequestParam(value = "type", required = false) String type,
                                   @RequestParam(value = "search", required = false) String search) {
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && 
            !AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")) {
            return "redirect:/access-denied";
        }
        
        try {
            // Use existing service methods - get all portfolios for now
            List<Portfolio> allPortfolios = portfolioService.findAll(); // Use existing method
            
            // Apply simple filtering if needed
            if (ownerId != null) {
                allPortfolios = allPortfolios.stream()
                    .filter(p -> p.getPropertyOwnerId() != null && p.getPropertyOwnerId().equals(ownerId))
                    .collect(Collectors.toList());
            }
            
            if (type != null && !type.isEmpty()) {
                try {
                    PortfolioType portfolioType = PortfolioType.valueOf(type.toUpperCase());
                    allPortfolios = allPortfolios.stream()
                        .filter(p -> p.getPortfolioType() == portfolioType)
                        .collect(Collectors.toList());
                } catch (IllegalArgumentException e) {
                    // Invalid type, ignore filter
                }
            }
            
            if (search != null && !search.trim().isEmpty()) {
                String searchLower = search.toLowerCase().trim();
                allPortfolios = allPortfolios.stream()
                    .filter(p -> (p.getName() != null && p.getName().toLowerCase().contains(searchLower)) ||
                               (p.getDescription() != null && p.getDescription().toLowerCase().contains(searchLower)))
                    .collect(Collectors.toList());
            }
            
            // Get analytics for each portfolio
            List<PortfolioWithAnalytics> portfoliosWithAnalytics = allPortfolios.stream()
                .map(portfolio -> {
                    PortfolioAnalytics analytics = portfolioService.getLatestPortfolioAnalytics(portfolio.getId());
                    return new PortfolioWithAnalytics(portfolio, analytics);
                })
                .collect(Collectors.toList());
            
            // Calculate aggregate statistics
            PortfolioAggregateStats aggregateStats = calculateAggregateStats(allPortfolios);
            
            // Get property owners for filter dropdown - use existing methods
            List<Customer> propertyOwners = new ArrayList<>();
            if (customerService != null) {
                try {
                    List<Customer> allCustomers = customerService.findAll();
                    propertyOwners = allCustomers.stream()
                        .filter(customer -> customer.getIsPropertyOwner() != null && customer.getIsPropertyOwner())
                        .collect(Collectors.toList());
                } catch (Exception e) {
                    System.out.println("Property owners filtering failed: " + e.getMessage());
                }
            }
            
            model.addAttribute("portfolios", portfoliosWithAnalytics);
            model.addAttribute("aggregateStats", aggregateStats);
            model.addAttribute("propertyOwners", propertyOwners);
            model.addAttribute("portfolioTypes", PortfolioType.values());
            model.addAttribute("selectedOwner", ownerId);
            model.addAttribute("selectedType", type);
            model.addAttribute("searchTerm", search);
            model.addAttribute("payPropEnabled", payPropEnabled);
            model.addAttribute("pageTitle", "All Portfolios");
            
            return "portfolio/all-portfolios";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading portfolios: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Assign Properties Interface - SIMPLIFIED VERSION
     */
    @GetMapping("/assign-properties")
    public String showAssignPropertiesPage(Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && 
            !AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")) {
            return "redirect:/access-denied";
        }
        
        try {
            // Get all portfolios - use existing method
            List<Portfolio> portfolios = portfolioService.findAll();
            
            // Get all properties - use existing method
            List<Property> allProperties = propertyService.findAll();
            
            // Filter unassigned properties manually
            List<Property> unassignedProperties = allProperties.stream()
                .filter(property -> property.getPortfolio() == null)
                .collect(Collectors.toList());
            
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
     * Bulk Property Assignment - FIXED VERSION
     */
    @PostMapping("/bulk-assign")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> bulkAssignProperties(
            @RequestParam("portfolioId") Long portfolioId,
            @RequestParam("propertyIds") List<Long> propertyIds,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && 
                !AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")) {
                response.put("success", false);
                response.put("message", "Access denied");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            
            // Use the method that exists - this returns void, not int
            portfolioService.assignPropertiesToPortfolio(portfolioId, propertyIds, (long) userId);
            
            response.put("success", true);
            response.put("message", propertyIds.size() + " properties assigned successfully");
            response.put("assignedCount", propertyIds.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Edit Portfolio Form
     */
    @GetMapping("/{id}/edit")
    public String showEditPortfolioForm(@PathVariable("id") Long portfolioId, Model model, Authentication authentication) {
        if (!canUserEditPortfolio(portfolioId, authentication)) {
            return "redirect:/access-denied";
        }
        
        Portfolio portfolio = portfolioService.findById(portfolioId);
        if (portfolio == null) {
            return "error/not-found";
        }
        
        model.addAttribute("portfolio", portfolio);
        model.addAttribute("portfolioTypes", PortfolioType.values());
        model.addAttribute("payPropEnabled", payPropEnabled);
        model.addAttribute("pageTitle", "Edit Portfolio");
        
        return "portfolio/edit-portfolio";
    }

    /**
     * Update Portfolio
     */
    @PostMapping("/{id}/edit")
    public String updatePortfolio(@PathVariable("id") Long portfolioId,
                                 @ModelAttribute("portfolio") @Validated Portfolio portfolio,
                                 BindingResult bindingResult,
                                 Authentication authentication,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        
        if (!canUserEditPortfolio(portfolioId, authentication)) {
            return "redirect:/access-denied";
        }
        
        if (bindingResult.hasErrors()) {
            model.addAttribute("portfolioTypes", PortfolioType.values());
            model.addAttribute("payPropEnabled", payPropEnabled);
            return "portfolio/edit-portfolio";
        }
        
        try {
            Portfolio existingPortfolio = portfolioService.findById(portfolioId);
            if (existingPortfolio == null) {
                return "error/not-found";
            }
            
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            
            // Update fields
            existingPortfolio.setName(portfolio.getName());
            existingPortfolio.setDescription(portfolio.getDescription());
            existingPortfolio.setPortfolioType(portfolio.getPortfolioType());
            existingPortfolio.setTargetMonthlyIncome(portfolio.getTargetMonthlyIncome());
            existingPortfolio.setTargetOccupancyRate(portfolio.getTargetOccupancyRate());
            existingPortfolio.setColorCode(portfolio.getColorCode());
            existingPortfolio.setUpdatedBy((long) userId);
            
            // Only managers can change sharing settings
            if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                existingPortfolio.setIsShared(portfolio.getIsShared());
            }
            
            portfolioService.save(existingPortfolio);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Portfolio '" + existingPortfolio.getName() + "' updated successfully!");
            
            return "redirect:/portfolio/" + portfolioId;
            
        } catch (Exception e) {
            model.addAttribute("error", "Failed to update portfolio: " + e.getMessage());
            model.addAttribute("portfolioTypes", PortfolioType.values());
            model.addAttribute("payPropEnabled", payPropEnabled);
            return "portfolio/edit-portfolio";
        }
    }

    // ===== PROPERTY ASSIGNMENT =====

    /**
     * Assign Properties to Portfolio
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
            
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            portfolioService.assignPropertiesToPortfolio(portfolioId, propertyIds, (long) userId);
            
            response.put("success", true);
            response.put("message", propertyIds.size() + " properties assigned successfully");
            response.put("assignedCount", propertyIds.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Remove Properties from Portfolio
     */
    @PostMapping("/{id}/remove-properties")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> removePropertiesFromPortfolio(
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
            
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            portfolioService.removePropertiesFromPortfolio(portfolioId, propertyIds, (long) userId);
            
            response.put("success", true);
            response.put("message", propertyIds.size() + " properties removed successfully");
            response.put("removedCount", propertyIds.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ===== PAYPROP SYNCHRONIZATION =====

    /**
     * Sync Portfolio with PayProp
     */
    @PostMapping("/{id}/sync")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncPortfolioWithPayProp(
            @PathVariable("id") Long portfolioId,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Check if PayProp is enabled and service is available
            if (!payPropEnabled || payPropSyncService == null) {
                response.put("success", false);
                response.put("message", "PayProp integration is not enabled");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && 
                !AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")) {
                response.put("success", false);
                response.put("message", "Access denied");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            portfolioService.syncPortfolioWithPayProp(portfolioId, (long) userId);
            
            response.put("success", true);
            response.put("message", "Portfolio sync initiated successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Sync All Portfolios with PayProp
     */
    @PostMapping("/sync-all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncAllPortfoliosWithPayProp(Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Check if PayProp is enabled and service is available
            if (!payPropEnabled || payPropSyncService == null) {
                response.put("success", false);
                response.put("message", "PayProp integration is not enabled");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                response.put("success", false);
                response.put("message", "Access denied - Manager role required");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            portfolioService.syncAllPortfoliosWithPayProp((long) userId);
            
            response.put("success", true);
            response.put("message", "Bulk portfolio sync initiated successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Pull Tags from PayProp (Two-way sync)
     */
    @PostMapping("/pull-payprop-tags")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> pullPayPropTags(Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Check if PayProp is enabled and service is available
            if (!payPropEnabled || payPropSyncService == null) {
                response.put("success", false);
                response.put("message", "PayProp integration is not enabled");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                response.put("success", false);
                response.put("message", "Access denied - Manager role required");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            SyncResult result = payPropSyncService.pullAllTagsFromPayProp((long) userId);
            
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("details", result.getDetails());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ===== ANALYTICS AND REPORTING =====

    /**
     * Recalculate Portfolio Analytics
     */
    @PostMapping("/{id}/recalculate-analytics")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> recalculateAnalytics(
            @PathVariable("id") Long portfolioId,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!portfolioService.canUserAccessPortfolio(portfolioId, authentication)) {
                response.put("success", false);
                response.put("message", "Access denied");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            PortfolioAnalytics analytics = portfolioService.calculatePortfolioAnalytics(portfolioId, LocalDate.now());
            
            response.put("success", true);
            response.put("message", "Analytics recalculated successfully");
            response.put("analytics", Map.of(
                "totalProperties", analytics.getTotalProperties(),
                "occupiedProperties", analytics.getOccupiedProperties(),
                "vacantProperties", analytics.getVacantProperties(),
                "occupancyRate", analytics.getOccupancyRate(),
                "totalMonthlyRent", analytics.getTotalMonthlyRent(),
                "actualMonthlyIncome", analytics.getActualMonthlyIncome()
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ===== UTILITY METHODS =====

    private PortfolioAggregateStats calculateAggregateStats(List<Portfolio> portfolios) {
        PortfolioAggregateStats stats = new PortfolioAggregateStats();
        
        for (Portfolio portfolio : portfolios) {
            PortfolioAnalytics analytics = portfolioService.getLatestPortfolioAnalytics(portfolio.getId());
            if (analytics != null) {
                stats.totalProperties += analytics.getTotalProperties();
                stats.totalOccupied += analytics.getOccupiedProperties();
                stats.totalVacant += analytics.getVacantProperties();
                stats.totalMonthlyRent = stats.totalMonthlyRent.add(analytics.getTotalMonthlyRent());
                stats.totalActualIncome = stats.totalActualIncome.add(analytics.getActualMonthlyIncome());
                
                // Only include sync stats if PayProp is enabled
                if (payPropEnabled) {
                    stats.totalSynced += analytics.getPropertiesSynced();
                    stats.totalPendingSync += analytics.getPropertiesPendingSync();
                }
            }
        }
        
        // Calculate overall occupancy rate
        if (stats.totalProperties > 0) {
            stats.overallOccupancyRate = (stats.totalOccupied * 100.0) / stats.totalProperties;
        }
        
        return stats;
    }

    private boolean canUserCreatePortfolio(Authentication authentication) {
        return AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") ||
               AuthorizationUtil.hasRole(authentication, "ROLE_PROPERTY_OWNER") ||
               AuthorizationUtil.hasRole(authentication, "ROLE_CUSTOMER");
    }

    private boolean canUserEditPortfolio(Long portfolioId, Authentication authentication) {
        if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return true;
        }
        
        Portfolio portfolio = portfolioService.findById(portfolioId);
        if (portfolio == null) {
            return false;
        }
        
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        
        // Property owners can edit their own portfolios
        if (AuthorizationUtil.hasRole(authentication, "ROLE_CUSTOMER")) {
            return portfolio.getPropertyOwnerId() != null && 
                   portfolio.getPropertyOwnerId().equals(userId);
        }
        
        // Employees can edit shared portfolios they created
        if (AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")) {
            return portfolio.getCreatedBy().equals((long) userId);
        }
        
        return false;
    }

    // ===== HELPER CLASSES =====

    public static class PortfolioWithAnalytics {
        private Portfolio portfolio;
        private PortfolioAnalytics analytics;
        
        public PortfolioWithAnalytics(Portfolio portfolio, PortfolioAnalytics analytics) {
            this.portfolio = portfolio;
            this.analytics = analytics;
        }
        
        public Portfolio getPortfolio() { return portfolio; }
        public PortfolioAnalytics getAnalytics() { return analytics; }
    }

    public static class PortfolioAggregateStats {
        public int totalProperties = 0;
        public int totalOccupied = 0;
        public int totalVacant = 0;
        public java.math.BigDecimal totalMonthlyRent = java.math.BigDecimal.ZERO;
        public java.math.BigDecimal totalActualIncome = java.math.BigDecimal.ZERO;
        public int totalSynced = 0;
        public int totalPendingSync = 0;
        public double overallOccupancyRate = 0.0;
        
        public int getTotalProperties() { return totalProperties; }
        public int getTotalOccupied() { return totalOccupied; }
        public int getTotalVacant() { return totalVacant; }
        public java.math.BigDecimal getTotalMonthlyRent() { return totalMonthlyRent; }
        public java.math.BigDecimal getTotalActualIncome() { return totalActualIncome; }
        public int getTotalSynced() { return totalSynced; }
        public int getTotalPendingSync() { return totalPendingSync; }
        public double getOverallOccupancyRate() { return overallOccupancyRate; }
    }
}
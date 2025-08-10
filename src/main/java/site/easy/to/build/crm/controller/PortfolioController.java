// FORCE REBUILD - Route fix deployment v4 - Remove after successful deployment
package site.easy.to.build.crm.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;
import site.easy.to.build.crm.service.portfolio.PortfolioService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.service.ticket.TicketService;
import site.easy.to.build.crm.service.payprop.PayPropPortfolioSyncService;
import site.easy.to.build.crm.service.payprop.PayPropTagDTO;
import site.easy.to.build.crm.service.payprop.SyncResult;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.util.AuthorizationUtil;
import site.easy.to.build.crm.entity.CustomerPropertyAssignment;
import site.easy.to.build.crm.entity.AssignmentType;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import site.easy.to.build.crm.entity.Customer;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PortfolioController - FIXED: Route ordering to prevent conflicts
 * Specific routes come BEFORE parameterized routes like /{id}
 */
@Controller
@RequestMapping("/portfolio")
public class PortfolioController {

    private static final Logger log = LoggerFactory.getLogger(PortfolioController.class);

    private final PortfolioService portfolioService;
    private final PropertyService propertyService;
    private final AuthenticationUtils authenticationUtils;
    
    // ✅ NEW: Add TicketService for maintenance statistics
    private final TicketService ticketService;
    
    @Autowired
    private UserService userService;
    
    @Autowired(required = false)
    private CustomerService customerService;

    @Autowired
    private CustomerPropertyAssignmentRepository customerPropertyAssignmentRepository;
    
    @Autowired(required = false)
    private PayPropPortfolioSyncService payPropSyncService;
    
    @Value("${payprop.enabled:false}")
    private boolean payPropEnabled;

    @Autowired
    public PortfolioController(PortfolioService portfolioService,
                              PropertyService propertyService,
                              AuthenticationUtils authenticationUtils,
                              TicketService ticketService) {
        this.portfolioService = portfolioService;
        this.propertyService = propertyService;
        this.authenticationUtils = authenticationUtils;
        this.ticketService = ticketService;
    }

    // ===== SPECIFIC ROUTES FIRST (BEFORE /{id}) =====

    /**
     * Portfolio Dashboard - Main entry point
     */
    /**
     * Portfolio Dashboard - Main entry point
     * FIXED: Added property owners and filtering support
     */
    @GetMapping("/dashboard")
    public String portfolioDashboard(Model model, 
                                Authentication authentication,
                                @RequestParam(value = "ownerId", required = false) Integer selectedOwnerId,
                                @RequestParam(value = "showUnassigned", defaultValue = "false") boolean showUnassigned) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            List<Portfolio> userPortfolios = portfolioService.findPortfoliosForUser(authentication);
            
            // FIX 1: Add property owners to model using Customer entity
            List<Customer> propertyOwners = new ArrayList<>();
            if (customerService != null) {
                try {
                    propertyOwners = customerService.findPropertyOwners();
                    System.out.println("✅ Found " + propertyOwners.size() + " property owners for dropdown");
                } catch (Exception e) {
                    System.out.println("❌ Error loading property owners: " + e.getMessage());
                }
            }
            model.addAttribute("propertyOwners", propertyOwners);
            
            // FIX 2: Apply owner filtering if specified
            if (selectedOwnerId != null && selectedOwnerId > 0) {
                userPortfolios = userPortfolios.stream()
                    .filter(p -> p.getPropertyOwnerId() != null && p.getPropertyOwnerId().equals(selectedOwnerId))
                    .collect(Collectors.toList());
                System.out.println("✅ Filtered to " + userPortfolios.size() + " portfolios for owner " + selectedOwnerId);
            }
            
            // FIX 3: Add unassigned properties if requested
            List<Property> unassignedProperties = new ArrayList<>();
            int unassignedCount = 0;
            
            if (showUnassigned) {
                try {
                    unassignedProperties = propertyService.findUnassignedProperties();
                    
                    // Filter unassigned properties by owner if specified
                    if (selectedOwnerId != null && selectedOwnerId > 0) {
                        // This filters based on properties owned by the customer
                        // You may need to adjust this based on your Customer-Property relationship
                        unassignedProperties = unassignedProperties.stream()
                            .filter(property -> {
                                // Check if property is owned by the selected customer
                                List<Customer> propertyCustomers = customerService.findByEntityTypeAndEntityId("Property", property.getId());
                                return propertyCustomers.stream()
                                    .anyMatch(customer -> customer.getCustomerId().equals(selectedOwnerId) && 
                                            customer.getIsPropertyOwner() != null && customer.getIsPropertyOwner());
                            })
                            .collect(Collectors.toList());
                    }
                    unassignedCount = unassignedProperties.size();
                    System.out.println("✅ Found " + unassignedCount + " unassigned properties");
                } catch (Exception e) {
                    System.out.println("❌ Error loading unassigned properties: " + e.getMessage());
                }
            }
            
            model.addAttribute("unassignedProperties", unassignedProperties);
            model.addAttribute("unassignedPropertiesCount", unassignedCount);
            model.addAttribute("showUnassigned", showUnassigned);
            model.addAttribute("selectedOwnerId", selectedOwnerId);
            
            // ✅ NEW: Add maintenance statistics for portfolio dashboard
            try {
                Map<String, Object> maintenanceStats = calculatePortfolioMaintenanceStats(userPortfolios, userId, authentication);
                model.addAttribute("maintenanceStats", maintenanceStats);
            } catch (Exception e) {
                System.err.println("Error calculating portfolio maintenance statistics: " + e.getMessage());
                model.addAttribute("maintenanceStats", getDefaultMaintenanceStats());
            }
            
            // Existing code continues...
            PortfolioAggregateStats aggregateStats = calculateAggregateStats(userPortfolios);
            
            List<PortfolioWithAnalytics> portfoliosWithAnalytics = userPortfolios.stream()
                .map(portfolio -> {
                    PortfolioAnalytics analytics = portfolioService.getLatestPortfolioAnalytics(portfolio.getId());
                    return new PortfolioWithAnalytics(portfolio, analytics);
                })
                .collect(Collectors.toList());
            
            boolean canCreatePortfolio = AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") ||
                                    AuthorizationUtil.hasRole(authentication, "ROLE_CUSTOMER");
            boolean canSyncPayProp = (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") ||
                                AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")) && 
                                payPropEnabled && payPropSyncService != null;
            boolean isPropertyOwner = AuthorizationUtil.hasRole(authentication, "ROLE_CUSTOMER");
            
            model.addAttribute("portfolios", portfoliosWithAnalytics);
            model.addAttribute("aggregateStats", aggregateStats);
            model.addAttribute("canCreatePortfolio", canCreatePortfolio);
            model.addAttribute("canSyncPayProp", canSyncPayProp);
            model.addAttribute("isPropertyOwner", isPropertyOwner);
            model.addAttribute("payPropEnabled", payPropEnabled);
            model.addAttribute("pageTitle", "Portfolio Dashboard");
            
            return isPropertyOwner ? "portfolio/property-owner-dashboard" : "portfolio/employee-dashboard";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading portfolio dashboard: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Create Portfolio Form
     */
    @GetMapping("/create")
    public String showCreatePortfolioForm(Model model, Authentication authentication) {
        if (!canUserCreatePortfolio(authentication)) {
            return "redirect:/access-denied";
        }
        
        prepareCreateFormModel(model, authentication);
        
        return "portfolio/create-portfolio";
    }


    @PostMapping("/{id}/debug-sync")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> debugSync(
            @PathVariable("id") Long portfolioId,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            response.put("portfolioId", portfolioId);
            response.put("payPropEnabled", payPropEnabled);
            response.put("payPropSyncService", payPropSyncService != null ? "AVAILABLE" : "NULL");
            
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            response.put("userId", userId);
            
            Portfolio portfolio = portfolioService.findById(portfolioId);
            response.put("portfolioExists", portfolio != null);
            
            response.put("success", true);
            response.put("message", "Debug successful");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Debug failed: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Create Portfolio Processing
     */
    @PostMapping("/create")
    public String createPortfolio(@ModelAttribute("portfolio") @Validated Portfolio portfolio,
                                BindingResult bindingResult,
                                @RequestParam(value = "selectedOwnerId", required = false) Integer selectedOwnerId,
                                @RequestParam(value = "enablePayPropSync", defaultValue = "false") boolean enablePayPropSync,
                                @RequestParam(value = "isShared", required = false) String isShared,
                                @RequestParam(value = "portfolioType", required = false) String portfolioTypeParam,
                                Authentication authentication,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        
        System.out.println("=== PORTFOLIO CREATION DEBUG ===");
        System.out.println("Portfolio name: '" + portfolio.getName() + "'");
        System.out.println("Portfolio type param: '" + portfolioTypeParam + "'");
        System.out.println("Is shared: '" + isShared + "'");
        System.out.println("Selected owner: " + selectedOwnerId);
        System.out.println("Enable PayProp: " + enablePayPropSync);
        
        if (!canUserCreatePortfolio(authentication)) {
            System.out.println("❌ Access denied");
            return "redirect:/access-denied";
        }
        
        // FIX 1: Get and validate user ID FIRST
        int userId;
        try {
            userId = authenticationUtils.getLoggedInUserId(authentication);
            System.out.println("✅ User ID: " + userId);
            
            // CRITICAL: Verify user exists in database
            // Note: UserService.existsById check removed - relying on authentication validation
            // If needed, add existsById method to UserService interface
            
        } catch (Exception e) {
            System.out.println("❌ Failed to get user ID: " + e.getMessage());
            model.addAttribute("error", "Authentication error. Please log in again.");
            return "redirect:/login";
        }
        
        // FIX 2: Handle portfolio type parameter
        if (portfolioTypeParam != null && !portfolioTypeParam.isEmpty()) {
            try {
                portfolio.setPortfolioType(PortfolioType.valueOf(portfolioTypeParam.toUpperCase()));
                System.out.println("✅ Set portfolio type: " + portfolio.getPortfolioType());
            } catch (IllegalArgumentException e) {
                portfolio.setPortfolioType(PortfolioType.CUSTOM);
                System.out.println("⚠️ Invalid portfolio type, defaulting to CUSTOM");
            }
        } else if (portfolio.getPortfolioType() == null) {
            portfolio.setPortfolioType(PortfolioType.CUSTOM);
            System.out.println("⚠️ No portfolio type provided, defaulting to CUSTOM");
        }
        
        // FIX 3: Handle isShared parameter
        if (isShared != null) {
            portfolio.setIsShared(isShared);
            System.out.println("✅ Set isShared: " + isShared);
        }
        
        // FIX 4: Validate BEFORE Spring validation
        if (portfolio.getName() == null || portfolio.getName().trim().isEmpty()) {
            System.out.println("❌ Portfolio name is empty");
            model.addAttribute("error", "Portfolio name is required");
            prepareCreateFormModel(model, authentication);
            return "portfolio/create-portfolio";
        }
        
        // Check Spring validation
        if (bindingResult.hasErrors()) {
            System.out.println("❌ Validation errors:");
            bindingResult.getAllErrors().forEach(error -> 
                System.out.println("  - " + error.getDefaultMessage()));
            
            model.addAttribute("error", "Please fix the validation errors and try again");
            prepareCreateFormModel(model, authentication);
            return "portfolio/create-portfolio";
        }
        
        try {
            // FIX 5: Handle ownership logic safely
            if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                if (selectedOwnerId != null && selectedOwnerId > 0) {
                    // Verify owner exists
                    if (customerService != null && customerService.existsById(selectedOwnerId.longValue())) {
                        portfolio.setPropertyOwnerId(selectedOwnerId);
                        portfolio.setIsShared("N");
                        System.out.println("✅ Manager creating owner-specific portfolio for: " + selectedOwnerId);
                    } else {
                        System.out.println("❌ Selected owner does not exist: " + selectedOwnerId);
                        model.addAttribute("error", "Selected property owner does not exist");
                        prepareCreateFormModel(model, authentication);
                        return "portfolio/create-portfolio";
                    }
                } else {
                    portfolio.setPropertyOwnerId(null);
                    portfolio.setIsShared("Y");
                    System.out.println("✅ Manager creating shared portfolio");
                }
            } else {
                portfolio.setPropertyOwnerId(userId);
                portfolio.setIsShared("N");
                System.out.println("✅ Customer creating personal portfolio");
            }
            
            // FIX 6: Create portfolio with proper error handling
            System.out.println("Creating portfolio in database...");
            Portfolio savedPortfolio;
            
            try {
                savedPortfolio = portfolioService.createPortfolio(
                    portfolio.getName().trim(),
                    portfolio.getDescription() != null ? portfolio.getDescription().trim() : "",
                    portfolio.getPortfolioType(),
                    portfolio.getPropertyOwnerId(),
                    (long) userId
                );
                System.out.println("✅ Portfolio created with ID: " + savedPortfolio.getId());
                
            } catch (Exception e) {
                System.out.println("❌ Portfolio creation failed: " + e.getMessage());
                e.printStackTrace();
                
                // Handle specific database errors
                if (e.getMessage().contains("foreign key constraint")) {
                    model.addAttribute("error", "Database error: Invalid user or owner reference. Please contact support.");
                } else if (e.getMessage().contains("already exists")) {
                    model.addAttribute("error", "A portfolio with this name already exists. Please choose a different name.");
                } else if (e.getMessage().contains("Duplicate entry")) {
                    model.addAttribute("error", "Portfolio name must be unique. Please choose a different name.");
                } else {
                    model.addAttribute("error", "Failed to create portfolio: " + e.getMessage());
                }
                
                prepareCreateFormModel(model, authentication);
                return "portfolio/create-portfolio";
            }
            
            // FIX 7: Update additional fields safely
            try {
                if (portfolio.getTargetMonthlyIncome() != null) {
                    savedPortfolio.setTargetMonthlyIncome(portfolio.getTargetMonthlyIncome());
                }
                if (portfolio.getTargetOccupancyRate() != null) {
                    savedPortfolio.setTargetOccupancyRate(portfolio.getTargetOccupancyRate());
                }
                if (portfolio.getColorCode() != null && !portfolio.getColorCode().trim().isEmpty()) {
                    savedPortfolio.setColorCode(portfolio.getColorCode().trim());
                } else {
                    savedPortfolio.setColorCode("#3498db"); // Default blue
                }
                
                savedPortfolio.setIsShared(portfolio.getIsShared());
                portfolioService.save(savedPortfolio);
                System.out.println("✅ Additional fields updated");
                
            } catch (Exception e) {
                System.out.println("⚠️ Failed to update additional fields: " + e.getMessage());
                // Continue anyway since core portfolio is created
            }
            
            // FIX 8: Handle PayProp sync safely
            String successMessage = "Portfolio '" + savedPortfolio.getName() + "' created successfully!";
            
            if (enablePayPropSync && payPropEnabled && payPropSyncService != null) {
                System.out.println("Attempting PayProp sync...");
                try {
                    portfolioService.syncPortfolioWithPayProp(savedPortfolio.getId(), (long) userId);
                    successMessage += " PayProp synchronization completed.";
                    System.out.println("✅ PayProp sync successful");
                } catch (Exception e) {
                    System.out.println("⚠️ PayProp sync failed: " + e.getMessage());
                    successMessage += " (PayProp sync failed: " + e.getMessage() + ")";
                }
            }
            
            redirectAttributes.addFlashAttribute("successMessage", successMessage);
            redirectAttributes.addFlashAttribute("newPortfolioId", savedPortfolio.getId());
            redirectAttributes.addFlashAttribute("newPortfolioName", savedPortfolio.getName());
            System.out.println("✅ PORTFOLIO CREATION COMPLETED - Redirecting to success page");

            return "redirect:/portfolio/" + savedPortfolio.getId() + "?justCreated=true";
            
        } catch (Exception e) {
            System.out.println("❌ UNEXPECTED ERROR: " + e.getMessage());
            e.printStackTrace();
            
            model.addAttribute("error", "An unexpected error occurred: " + e.getMessage());
            prepareCreateFormModel(model, authentication);
            return "portfolio/create-portfolio";
        }
    }

    /**
     * Get All Portfolios (Manager View)
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
            List<Portfolio> allPortfolios = portfolioService.findAll();
            
            // Apply filtering
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
            
            List<PortfolioWithAnalytics> portfoliosWithAnalytics = allPortfolios.stream()
                .map(portfolio -> {
                    PortfolioAnalytics analytics = portfolioService.getLatestPortfolioAnalytics(portfolio.getId());
                    return new PortfolioWithAnalytics(portfolio, analytics);
                })
                .collect(Collectors.toList());
            
            PortfolioAggregateStats aggregateStats = calculateAggregateStats(allPortfolios);
            
            // ✅ NEW: Add maintenance statistics for all portfolios view
            try {
                int userId = authenticationUtils.getLoggedInUserId(authentication);
                Map<String, Object> maintenanceStats = calculatePortfolioMaintenanceStats(allPortfolios, userId, authentication);
                model.addAttribute("maintenanceStats", maintenanceStats);
            } catch (Exception e) {
                System.err.println("Error calculating maintenance stats for all portfolios: " + e.getMessage());
                model.addAttribute("maintenanceStats", getDefaultMaintenanceStats());
            }
            
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
     * Assign Properties Interface
     */
    @GetMapping("/assign-properties")
    public String showAssignPropertiesPage(Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && 
            !AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")) {
            return "redirect:/access-denied";
        }
        
        try {
            List<Portfolio> portfolios = portfolioService.findAll();
            List<Property> allProperties = propertyService.findAll();
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

    // ✅ NEW: Portfolio Maintenance Dashboard
    @GetMapping("/maintenance-dashboard")
    public String portfolioMaintenanceDashboard(Model model, Authentication authentication,
                                               @RequestParam(value = "portfolioId", required = false) Long portfolioId) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            List<Portfolio> userPortfolios = portfolioService.findPortfoliosForUser(authentication);
            
            // If specific portfolio selected, filter to that one
            if (portfolioId != null) {
                userPortfolios = userPortfolios.stream()
                    .filter(p -> p.getId().equals(portfolioId))
                    .collect(Collectors.toList());
            }
            
            // Calculate detailed maintenance statistics
            Map<String, Object> detailedMaintenanceStats = calculateDetailedPortfolioMaintenanceStats(userPortfolios);
            
            // Get maintenance tickets for portfolios
            List<Ticket> portfolioMaintenanceTickets = getMaintenanceTicketsForPortfolios(userPortfolios);
            List<Ticket> recentTickets = portfolioMaintenanceTickets.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(10)
                .collect(Collectors.toList());
            
            model.addAttribute("portfolios", userPortfolios);
            model.addAttribute("selectedPortfolioId", portfolioId);
            model.addAttribute("maintenanceStats", detailedMaintenanceStats);
            model.addAttribute("recentMaintenanceTickets", recentTickets);
            model.addAttribute("totalMaintenanceTickets", portfolioMaintenanceTickets.size());
            model.addAttribute("pageTitle", "Portfolio Maintenance Dashboard");
            
            return "portfolio/maintenance-dashboard";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading maintenance dashboard: " + e.getMessage());
            return "error/500";
        }
    }

    @GetMapping("/portfolio/owner/{ownerId}/properties")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getOwnerProperties(@PathVariable Integer ownerId) {
        try {
            // Use the existing repository method
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
                    map.put("monthlyRent", property.getMonthlyPayment());
                    return map;
                })
                .collect(Collectors.toList());
                
            return ResponseEntity.ok(propertyList);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ArrayList<>());
        }
    }

    @GetMapping("/debug/test-payprop-direct")
    @ResponseBody
    public ResponseEntity<String> testPayPropDirect(Authentication authentication) {
        StringBuilder result = new StringBuilder();
        
        try {
            result.append("🔍 PayProp Direct Test\n");
            result.append("payPropEnabled: ").append(payPropEnabled).append("\n");
            result.append("payPropSyncService: ").append(payPropSyncService != null ? "AVAILABLE" : "NULL").append("\n");
            
            if (payPropSyncService != null) {
                result.append("Calling pullAllTagsFromPayProp...\n");
                SyncResult syncResult = payPropSyncService.pullAllTagsFromPayProp(54L);
                result.append("Result: ").append(syncResult.getMessage()).append("\n");
            }
            
            return ResponseEntity.ok(result.toString());
            
        } catch (Exception e) {
            result.append("ERROR: ").append(e.getMessage()).append("\n");
            return ResponseEntity.ok(result.toString());
        }
    }

    // ===== PAYPROP SPECIFIC ROUTES =====

    /**
     * Get available PayProp tags for adoption
     */
    @GetMapping("/payprop-tags")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPayPropTags(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        try {
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
            
            List<PayPropTagDTO> payPropTags = payPropSyncService.getAllPayPropTags();
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
            
            if (AuthorizationUtil.hasRole(authentication, "ROLE_PROPERTY_OWNER")) {
                portfolio.setPropertyOwnerId(userId);
                portfolio.setIsShared("N");
            } else {
                portfolio.setIsShared("Y");
            }
            
            portfolio.setPayPropTags(payPropTagId);
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
            response.put("success", false);
            response.put("message", "Failed to adopt PayProp tag: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Pull Tags from PayProp (Two-way sync) - FIXED: Now comes BEFORE /{id}
     */
    @GetMapping("/actions/pull-payprop-tags")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> pullPayPropTags(Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
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

    @GetMapping("/debug/raw-payprop-response")
    @ResponseBody
    public ResponseEntity<String> debugRawPayPropResponse(Authentication authentication) {
        try {
            if (payPropSyncService == null) {
                return ResponseEntity.ok("PayProp sync service is null");
            }
            
            List<PayPropTagDTO> tags = payPropSyncService.getAllPayPropTags();
            
            return ResponseEntity.ok("Found " + tags.size() + " tags:\n" + 
                                tags.toString());
            
        } catch (Exception e) {
            return ResponseEntity.ok("ERROR: " + e.getMessage() + 
                                "\nCause: " + (e.getCause() != null ? e.getCause().getMessage() : "None"));
        }
    }

    @PostMapping("/sync-all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncAllPortfoliosWithPayProp(Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
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
     * Bulk Property Assignment
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

    @GetMapping("/{id}/assign")
    public String showPortfolioSpecificAssignmentPage(@PathVariable("id") Long portfolioId, 
                                                    Model model, 
                                                    Authentication authentication) {
        try {
            // Check access permissions
            if (!portfolioService.canUserAccessPortfolio(portfolioId, authentication)) {
                return "redirect:/access-denied";
            }
            
            // Get the specific portfolio
            Portfolio targetPortfolio = portfolioService.findById(portfolioId);
            if (targetPortfolio == null) {
                return "error/not-found";
            }
            
            // Get all portfolios for the general assignment interface
            List<Portfolio> allPortfolios = portfolioService.findPortfoliosForUser(authentication);
            
            // FIXED: Filter properties based on portfolio ownership
            List<Property> allProperties;
            List<Property> unassignedProperties;
            
            if (targetPortfolio.getPropertyOwnerId() != null) {
                // Owner-specific portfolio - only show properties for this owner
                Integer ownerId = targetPortfolio.getPropertyOwnerId();
                
                // Use the existing method that works with junction table
                allProperties = propertyService.findByPropertyOwnerId(ownerId.longValue());
                
                // Filter to get unassigned properties for this owner
                unassignedProperties = allProperties.stream()
                    .filter(property -> property.getPortfolio() == null)
                    .collect(Collectors.toList());
                    
                System.out.println("✅ Owner-specific portfolio " + portfolioId + 
                                " for owner " + ownerId + 
                                " - showing " + allProperties.size() + " total properties, " +
                                unassignedProperties.size() + " unassigned");
            } else {
                // Shared portfolio - show all unassigned properties
                allProperties = propertyService.findAll();
                unassignedProperties = allProperties.stream()
                    .filter(property -> property.getPortfolio() == null)
                    .collect(Collectors.toList());
                    
                System.out.println("✅ Shared portfolio " + portfolioId + 
                                " - showing " + allProperties.size() + " total properties, " +
                                unassignedProperties.size() + " unassigned");
            }
            
            // Add the attributes
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

    @GetMapping("/{id}/available-properties")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAvailablePropertiesForPortfolio(
            @PathVariable("id") Long portfolioId, 
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!portfolioService.canUserAccessPortfolio(portfolioId, authentication)) {
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
            
            // FIXED: Filter properties based on portfolio ownership
            List<Property> availableProperties;
            
            if (portfolio.getPropertyOwnerId() != null) {
                // Owner-specific portfolio - only show properties for this owner
                Integer ownerId = portfolio.getPropertyOwnerId();
                
                // Get all properties for this owner using the existing method
                List<Property> ownerProperties = propertyService.findByPropertyOwnerId(ownerId.longValue());
                
                // Filter to get properties not already in this portfolio
                availableProperties = ownerProperties.stream()
                    .filter(property -> property.getPortfolio() == null || 
                                    !property.getPortfolio().getId().equals(portfolioId))
                    .collect(Collectors.toList());
                    
                System.out.println("✅ Owner " + ownerId + " - found " + 
                                ownerProperties.size() + " total properties, " +
                                availableProperties.size() + " available for portfolio " + portfolioId);
            } else {
                // Shared portfolio - show all available properties
                List<Property> allProperties = propertyService.findAll();
                availableProperties = allProperties.stream()
                    .filter(property -> property.getPortfolio() == null || 
                                    !property.getPortfolio().getId().equals(portfolioId))
                    .collect(Collectors.toList());
                    
                System.out.println("✅ Shared portfolio - found " + 
                                availableProperties.size() + " available properties");
            }
            
            // Convert to simple DTOs for JSON response
            List<Map<String, Object>> propertyDTOs = availableProperties.stream()
                .map(property -> {
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("id", property.getId());
                    dto.put("propertyName", property.getPropertyName());
                    dto.put("fullAddress", property.getFullAddress());
                    dto.put("propertyType", property.getPropertyType());
                    dto.put("monthlyPayment", property.getMonthlyPayment());
                    dto.put("payPropId", property.getPayPropId());
                    dto.put("isOccupied", property.isOccupied());
                    return dto;
                })
                .collect(Collectors.toList());
            
            response.put("success", true);
            response.put("properties", propertyDTOs);
            response.put("totalAvailable", propertyDTOs.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to load available properties: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Remove a single property from portfolio
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
            
            Property property = propertyService.findById(propertyId);
            if (property == null) {
                response.put("success", false);
                response.put("message", "Property not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            Portfolio portfolio = portfolioService.findById(portfolioId);
            if (portfolio == null) {
                response.put("success", false);
                response.put("message", "Portfolio not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            // Remove PayProp tags if applicable
            if (payPropEnabled && payPropSyncService != null && 
                portfolio.isSyncedWithPayProp() && property.getPayPropId() != null) {
                
                List<String> payPropTags = portfolio.getPayPropTagList();
                for (String tagId : payPropTags) {
                    try {
                        payPropSyncService.removeTagFromProperty(property.getPayPropId(), tagId);
                    } catch (Exception e) {
                        log.warn("Failed to remove PayProp tag {} from property {}: {}", 
                                tagId, property.getPayPropId(), e.getMessage());
                    }
                }
            }
            
            // Remove from local portfolio
            property.setPortfolio(null);
            property.setPortfolioAssignmentDate(null);
            propertyService.save(property);
            
            response.put("success", true);
            response.put("message", "Property removed from portfolio successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to remove property: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Enhanced assignment with PayProp sync
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
            
            Portfolio portfolio = portfolioService.findById(portfolioId);
            if (portfolio == null) {
                response.put("success", false);
                response.put("message", "Portfolio not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            int assignedCount = 0;
            int syncedCount = 0;
            int errorCount = 0;
            List<String> errors = new ArrayList<>();
            
            for (Long propertyId : propertyIds) {
                try {
                    Property property = propertyService.findById(propertyId);
                    if (property != null) {
                        // Assign locally
                        property.setPortfolio(portfolio);
                        property.setPortfolioAssignmentDate(LocalDateTime.now());
                        propertyService.save(property);
                        assignedCount++;
                        
                        // Sync to PayProp if enabled and property has PayProp ID
                        if (payPropEnabled && payPropSyncService != null && 
                            portfolio.isSyncedWithPayProp() && property.getPayPropId() != null) {
                            
                            List<String> payPropTags = portfolio.getPayPropTagList();
                            for (String tagId : payPropTags) {
                                try {
                                    payPropSyncService.applyTagToProperty(property.getPayPropId(), tagId);
                                    syncedCount++;
                                } catch (Exception e) {
                                    log.warn("Failed to apply PayProp tag {} to property {}: {}", 
                                        tagId, property.getPayPropId(), e.getMessage());
                                    errors.add("PayProp sync failed for property " + property.getPropertyName());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    errorCount++;
                    errors.add("Failed to assign property ID " + propertyId + ": " + e.getMessage());
                    log.error("Failed to assign property {} to portfolio {}: {}", propertyId, portfolioId, e.getMessage());
                }
            }
            
            response.put("success", true);
            response.put("message", assignedCount + " properties assigned successfully");
            response.put("assignedCount", assignedCount);
            response.put("syncedToPayProp", syncedCount);
            response.put("errorCount", errorCount);
            response.put("errors", errors);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Assignment failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Recalculate Portfolio Analytics - Fixed method name
     */
    @PostMapping("/{id}/recalculate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> recalculatePortfolioAnalytics(
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
            response.put("message", "Failed to recalculate analytics: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Portfolio Details - MOVED TO END to prevent route conflicts
     */
    @GetMapping("/{id}")
    public String showPortfolioDetails(@PathVariable Long id, Model model, Authentication authentication) {
        try {
            // Check access permissions
            if (!portfolioService.canUserAccessPortfolio(id, authentication)) {
                return "redirect:/access-denied";
            }
            
            Portfolio portfolio = portfolioService.findById(id);
            if (portfolio == null) {
                return "error/not-found";
            }
            
            // Get portfolio statistics
            Map<String, Object> stats = portfolioService.getPortfolioStatistics(id);
            
            // Get properties with tenant information
            List<Property> properties = propertyService.findByPortfolioId(id);
            
            // Create property data with tenant info (HANDLES NULL TENANTS)
            List<Map<String, Object>> propertiesWithTenants = properties.stream()
                .map(property -> {
                    Map<String, Object> propertyData = new HashMap<>();
                    propertyData.put("id", property.getId());
                    propertyData.put("propertyName", property.getPropertyName());
                    propertyData.put("fullAddress", property.getFullAddress());
                    propertyData.put("propertyType", property.getPropertyType());
                    propertyData.put("bedrooms", property.getBedrooms());
                    propertyData.put("bathrooms", property.getBathrooms());
                    propertyData.put("monthlyPayment", property.getMonthlyPayment());
                    propertyData.put("payPropId", property.getPayPropId());
                    propertyData.put("isArchived", property.getIsArchived());
                    
                    // Get tenant - will be null if no tenant
                    Customer tenant = propertyService.getCurrentTenant(property.getId());
                    if (tenant != null) {
                        Map<String, Object> tenantData = new HashMap<>();
                        tenantData.put("id", tenant.getCustomerId());
                        tenantData.put("firstName", tenant.getFirstName());
                        tenantData.put("lastName", tenant.getLastName());
                        tenantData.put("fullName", tenant.getFirstName() + " " + tenant.getLastName());
                        tenantData.put("email", tenant.getEmail());
                        tenantData.put("phone", tenant.getPhone());
                        propertyData.put("currentTenant", tenantData);
                    } else {
                        propertyData.put("currentTenant", null);
                    }
                    
                    return propertyData;
                })
                .collect(Collectors.toList());
            
            // Add all attributes
            model.addAttribute("portfolio", portfolio);
            model.addAttribute("propertiesWithTenants", propertiesWithTenants);
            model.addAttribute("stats", stats);
            model.addAttribute("pageTitle", "Portfolio: " + portfolio.getName());
            
            return "portfolio/portfolio-details";
            
        } catch (Exception e) {
            System.err.println("Error loading portfolio details: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("error", "Error loading portfolio: " + e.getMessage());
            return "error/500";
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
            
            existingPortfolio.setName(portfolio.getName());
            existingPortfolio.setDescription(portfolio.getDescription());
            existingPortfolio.setPortfolioType(portfolio.getPortfolioType());
            existingPortfolio.setTargetMonthlyIncome(portfolio.getTargetMonthlyIncome());
            existingPortfolio.setTargetOccupancyRate(portfolio.getTargetOccupancyRate());
            existingPortfolio.setColorCode(portfolio.getColorCode());
            existingPortfolio.setUpdatedBy((long) userId);
            
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
     * Recalculate Portfolio Analytics (alternative endpoint)
     */
    @PostMapping("/{id}/recalculate-analytics")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> recalculateAnalytics(
            @PathVariable("id") Long portfolioId,
            Authentication authentication) {
        
        return recalculatePortfolioAnalytics(portfolioId, authentication);
    }

    // ===== ✅ NEW: MAINTENANCE STATISTICS HELPER METHODS =====

    /**
     * Calculate maintenance statistics for multiple portfolios
     */
    private Map<String, Object> calculatePortfolioMaintenanceStats(List<Portfolio> portfolios, int userId, Authentication authentication) {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // Get all properties from all portfolios
            List<Long> allPropertyIds = portfolios.stream()
                .flatMap(portfolio -> {
                    try {
                        return propertyService.findByPortfolioId(portfolio.getId()).stream();
                    } catch (Exception e) {
                        return java.util.stream.Stream.empty();
                    }
                })
                .map(Property::getId)
                .collect(Collectors.toList());
            
            // Get maintenance tickets for all portfolio properties
            List<Ticket> allMaintenanceTickets = new ArrayList<>();
            List<Ticket> allEmergencyTickets = new ArrayList<>();
            
            for (Long propertyId : allPropertyIds) {
                try {
                    List<Ticket> propertyMaintenanceTickets = ticketService.getTicketsByPropertyIdAndType(propertyId, "maintenance");
                    List<Ticket> propertyEmergencyTickets = ticketService.getTicketsByPropertyIdAndType(propertyId, "emergency");
                    
                    allMaintenanceTickets.addAll(propertyMaintenanceTickets);
                    allEmergencyTickets.addAll(propertyEmergencyTickets);
                } catch (Exception e) {
                    System.err.println("Error getting tickets for property " + propertyId + ": " + e.getMessage());
                }
            }
            
            // Calculate statistics
            long openTickets = allMaintenanceTickets.stream()
                .filter(t -> "open".equals(t.getStatus()))
                .count();
            
            long inProgressTickets = allMaintenanceTickets.stream()
                .filter(t -> "in-progress".equals(t.getStatus()) || "work-in-progress".equals(t.getStatus()))
                .count();
            
            long emergencyCount = allEmergencyTickets.stream()
                .filter(t -> !"closed".equals(t.getStatus()) && !"resolved".equals(t.getStatus()))
                .count();
            
            long awaitingBids = allMaintenanceTickets.stream()
                .filter(t -> "bidding".equals(t.getStatus()) || "awaiting-bids".equals(t.getStatus()))
                .count();
            
            long completedTickets = allMaintenanceTickets.stream()
                .filter(t -> "completed".equals(t.getStatus()) || "closed".equals(t.getStatus()))
                .count();
            
            stats.put("openTickets", openTickets);
            stats.put("inProgressTickets", inProgressTickets);
            stats.put("emergencyTickets", emergencyCount);
            stats.put("awaitingBids", awaitingBids);
            stats.put("totalMaintenance", allMaintenanceTickets.size());
            stats.put("completedTickets", completedTickets);
            stats.put("totalPortfolios", portfolios.size());
            stats.put("totalProperties", allPropertyIds.size());
            
            // Debug logging
            System.out.println("=== PORTFOLIO MAINTENANCE STATS ===");
            System.out.println("Portfolios: " + portfolios.size());
            System.out.println("Properties: " + allPropertyIds.size());
            System.out.println("Open: " + openTickets);
            System.out.println("In Progress: " + inProgressTickets);
            System.out.println("Emergency: " + emergencyCount);
            System.out.println("=== END PORTFOLIO STATS ===");
            
        } catch (Exception e) {
            System.err.println("Error in portfolio maintenance stats calculation: " + e.getMessage());
            return getDefaultMaintenanceStats();
        }
        
        return stats;
    }
    
    /**
     * Calculate detailed maintenance statistics for portfolios
     */
    private Map<String, Object> calculateDetailedPortfolioMaintenanceStats(List<Portfolio> portfolios) {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // Initialize counters
            Map<String, Integer> categoryBreakdown = new HashMap<>();
            Map<String, Integer> priorityBreakdown = new HashMap<>();
            Map<String, Integer> portfolioBreakdown = new HashMap<>();
            
            int totalTickets = 0;
            int totalCost = 0;
            
            // Process each portfolio
            for (Portfolio portfolio : portfolios) {
                try {
                    List<Property> portfolioProperties = propertyService.findByPortfolioId(portfolio.getId());
                    int portfolioTicketCount = 0;
                    
                    for (Property property : portfolioProperties) {
                        try {
                            List<Ticket> propertyTickets = ticketService.getTicketsByPropertyId(property.getId());
                            
                            for (Ticket ticket : propertyTickets) {
                                if ("maintenance".equals(ticket.getType()) || "emergency".equals(ticket.getType())) {
                                    totalTickets++;
                                    portfolioTicketCount++;
                                    
                                    // Category breakdown
                                    String category = ticket.getMaintenanceCategory() != null ? 
                                        ticket.getMaintenanceCategory() : "General";
                                    categoryBreakdown.put(category, 
                                        categoryBreakdown.getOrDefault(category, 0) + 1);
                                    
                                    // Priority breakdown
                                    String priority = ticket.getPriority() != null ? 
                                        ticket.getPriority() : "Medium";
                                    priorityBreakdown.put(priority, 
                                        priorityBreakdown.getOrDefault(priority, 0) + 1);
                                    
                                    // Add to cost if available
                                    if (ticket.getApprovedAmount() != null) {
                                        totalCost += ticket.getApprovedAmount().intValue();
                                    }
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Error processing property " + property.getId() + ": " + e.getMessage());
                        }
                    }
                    
                    // Portfolio breakdown
                    portfolioBreakdown.put(portfolio.getName(), portfolioTicketCount);
                    
                } catch (Exception e) {
                    System.err.println("Error processing portfolio " + portfolio.getId() + ": " + e.getMessage());
                }
            }
            
            stats.put("totalTickets", totalTickets);
            stats.put("totalCost", totalCost);
            stats.put("categoryBreakdown", categoryBreakdown);
            stats.put("priorityBreakdown", priorityBreakdown);
            stats.put("portfolioBreakdown", portfolioBreakdown);
            stats.put("averageCostPerTicket", totalTickets > 0 ? totalCost / totalTickets : 0);
            
        } catch (Exception e) {
            System.err.println("Error in detailed portfolio maintenance stats: " + e.getMessage());
            return getDefaultMaintenanceStats();
        }
        
        return stats;
    }
    
    /**
     * Calculate maintenance statistics for a specific portfolio
     */
    private Map<String, Object> calculatePortfolioSpecificMaintenanceStats(Long portfolioId) {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            List<Property> portfolioProperties = propertyService.findByPortfolioId(portfolioId);
            List<Long> propertyIds = portfolioProperties.stream().map(Property::getId).collect(Collectors.toList());
            
            // Get all maintenance tickets for this portfolio's properties
            List<Ticket> allMaintenanceTickets = new ArrayList<>();
            List<Ticket> allEmergencyTickets = new ArrayList<>();
            
            for (Long propertyId : propertyIds) {
                try {
                    List<Ticket> propertyMaintenanceTickets = ticketService.getTicketsByPropertyIdAndType(propertyId, "maintenance");
                    List<Ticket> propertyEmergencyTickets = ticketService.getTicketsByPropertyIdAndType(propertyId, "emergency");
                    
                    allMaintenanceTickets.addAll(propertyMaintenanceTickets);
                    allEmergencyTickets.addAll(propertyEmergencyTickets);
                } catch (Exception e) {
                    System.err.println("Error getting tickets for property " + propertyId + ": " + e.getMessage());
                }
            }
            
            // Calculate basic statistics
            long openTickets = allMaintenanceTickets.stream()
                .filter(t -> "open".equals(t.getStatus()))
                .count();
            
            long inProgressTickets = allMaintenanceTickets.stream()
                .filter(t -> "in-progress".equals(t.getStatus()) || "work-in-progress".equals(t.getStatus()))
                .count();
            
            long emergencyCount = allEmergencyTickets.stream()
                .filter(t -> !"closed".equals(t.getStatus()) && !"resolved".equals(t.getStatus()))
                .count();
            
            long completedTickets = allMaintenanceTickets.stream()
                .filter(t -> "completed".equals(t.getStatus()) || "closed".equals(t.getStatus()))
                .count();
            
            // Calculate cost metrics
            double totalCost = allMaintenanceTickets.stream()
                .filter(t -> t.getApprovedAmount() != null)
                .mapToDouble(t -> t.getApprovedAmount().doubleValue())
                .sum();
            
            double averageCostPerProperty = portfolioProperties.size() > 0 ? totalCost / portfolioProperties.size() : 0;
            
            stats.put("openTickets", openTickets);
            stats.put("inProgressTickets", inProgressTickets);
            stats.put("emergencyTickets", emergencyCount);
            stats.put("completedTickets", completedTickets);
            stats.put("totalTickets", allMaintenanceTickets.size());
            stats.put("totalCost", totalCost);
            stats.put("averageCostPerProperty", averageCostPerProperty);
            stats.put("propertiesInPortfolio", portfolioProperties.size());
            
        } catch (Exception e) {
            System.err.println("Error in portfolio-specific maintenance stats: " + e.getMessage());
            return getDefaultMaintenanceStats();
        }
        
        return stats;
    }
    
    /**
     * Get maintenance tickets for a list of portfolios
     */
    private List<Ticket> getMaintenanceTicketsForPortfolios(List<Portfolio> portfolios) {
        List<Ticket> allTickets = new ArrayList<>();
        
        try {
            for (Portfolio portfolio : portfolios) {
                List<Property> portfolioProperties = propertyService.findByPortfolioId(portfolio.getId());
                
                for (Property property : portfolioProperties) {
                    try {
                        List<Ticket> propertyTickets = ticketService.getTicketsByPropertyId(property.getId());
                        List<Ticket> maintenanceTickets = propertyTickets.stream()
                            .filter(t -> "maintenance".equals(t.getType()) || "emergency".equals(t.getType()))
                            .collect(Collectors.toList());
                        
                        allTickets.addAll(maintenanceTickets);
                    } catch (Exception e) {
                        System.err.println("Error getting tickets for property " + property.getId() + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting maintenance tickets for portfolios: " + e.getMessage());
        }
        
        return allTickets;
    }
    
    /**
     * Default maintenance stats in case of errors
     */
    private Map<String, Object> getDefaultMaintenanceStats() {
        Map<String, Object> defaultStats = new HashMap<>();
        defaultStats.put("openTickets", 0L);
        defaultStats.put("inProgressTickets", 0L);
        defaultStats.put("emergencyTickets", 0L);
        defaultStats.put("awaitingBids", 0L);
        defaultStats.put("totalMaintenance", 0L);
        defaultStats.put("completedTickets", 0L);
        defaultStats.put("totalPortfolios", 0);
        defaultStats.put("totalProperties", 0);
        defaultStats.put("totalTickets", 0);
        defaultStats.put("totalCost", 0);
        defaultStats.put("averageCostPerTicket", 0);
        defaultStats.put("averageCostPerProperty", 0.0);
        defaultStats.put("propertiesInPortfolio", 0);
        defaultStats.put("categoryBreakdown", new HashMap<>());
        defaultStats.put("priorityBreakdown", new HashMap<>());
        defaultStats.put("portfolioBreakdown", new HashMap<>());
        return defaultStats;
    }

    // ===== UTILITY METHODS =====

    /**
     * Prepare form model for create portfolio form
     */
    private void prepareCreateFormModel(Model model, Authentication authentication) {
        System.out.println("Preparing create form model...");
        
        try {
            if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                if (customerService != null) {
                    try {
                        List<Customer> allCustomers = customerService.findAll();
                        List<Customer> propertyOwners = allCustomers.stream()
                            .filter(customer -> customer.getIsPropertyOwner() != null && customer.getIsPropertyOwner())
                            .collect(Collectors.toList());
                        model.addAttribute("propertyOwners", propertyOwners);
                        System.out.println("✅ Added " + propertyOwners.size() + " property owners to model");
                    } catch (Exception e) {
                        System.out.println("❌ Error loading property owners: " + e.getMessage());
                        model.addAttribute("propertyOwners", new ArrayList<>());
                    }
                } else {
                    System.out.println("❌ CustomerService is null");
                    model.addAttribute("propertyOwners", new ArrayList<>());
                }
                model.addAttribute("isManager", true);
            } else {
                model.addAttribute("isManager", false);
            }
            
            model.addAttribute("portfolioTypes", PortfolioType.values());
            model.addAttribute("payPropEnabled", payPropEnabled);
            
            // Ensure portfolio object exists
            if (!model.containsAttribute("portfolio")) {
                Portfolio newPortfolio = new Portfolio();
                newPortfolio.setPortfolioType(PortfolioType.CUSTOM); // Set default
                newPortfolio.setColorCode("#3498db"); // Set default color
                model.addAttribute("portfolio", newPortfolio);
            }
            
            System.out.println("✅ Form model preparation completed");
            
        } catch (Exception e) {
            System.out.println("❌ Error preparing form model: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if user can create portfolio
     */
    private boolean canUserCreatePortfolio(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        // Managers and property owners can create portfolios
        return AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") || 
               AuthorizationUtil.hasRole(authentication, "ROLE_PROPERTY_OWNER") ||
               AuthorizationUtil.hasRole(authentication, "ROLE_USER");
    }

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
                
                if (payPropEnabled) {
                    stats.totalSynced += analytics.getPropertiesSynced();
                    stats.totalPendingSync += analytics.getPropertiesPendingSync();
                }
            }
        }
        
        if (stats.totalProperties > 0) {
            stats.overallOccupancyRate = (stats.totalOccupied * 100.0) / stats.totalProperties;
        }
        
        return stats;
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
        
        if (AuthorizationUtil.hasRole(authentication, "ROLE_CUSTOMER")) {
            return portfolio.getPropertyOwnerId() != null && 
                   portfolio.getPropertyOwnerId().equals(userId);
        }
        
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
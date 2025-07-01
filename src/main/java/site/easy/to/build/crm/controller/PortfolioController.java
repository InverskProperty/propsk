// FORCE REBUILD - Route fix deployment v4 - Remove after successful deployment
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
 * PortfolioController - FIXED: Route ordering to prevent conflicts
 * Specific routes come BEFORE parameterized routes like /{id}
 */
@Controller
@RequestMapping("/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PropertyService propertyService;
    private final AuthenticationUtils authenticationUtils;
    
    @Autowired(required = false)
    private CustomerService customerService;
    
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

    // ===== SPECIFIC ROUTES FIRST (BEFORE /{id}) =====

    /**
     * Portfolio Dashboard - Main entry point
     */
    @GetMapping("/dashboard")
    public String portfolioDashboard(Model model, Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            List<Portfolio> userPortfolios = portfolioService.findPortfoliosForUser(authentication);
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
        
        Portfolio portfolio = new Portfolio();
        
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
        } else {
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
     * Create Portfolio Processing
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
                if (selectedOwnerId != null && selectedOwnerId > 0) {
                    portfolio.setPropertyOwnerId(selectedOwnerId);
                    portfolio.setIsShared("N");
                } else {
                    portfolio.setPropertyOwnerId(null);
                    portfolio.setIsShared("Y");
                }
            } else {
                portfolio.setPropertyOwnerId(userId);
                portfolio.setIsShared("N");
            }
            
            Portfolio savedPortfolio = portfolioService.createPortfolio(
                portfolio.getName(),
                portfolio.getDescription(),
                portfolio.getPortfolioType(),
                portfolio.getPropertyOwnerId(),
                (long) userId
            );
            
            savedPortfolio.setTargetMonthlyIncome(portfolio.getTargetMonthlyIncome());
            savedPortfolio.setTargetOccupancyRate(portfolio.getTargetOccupancyRate());
            savedPortfolio.setColorCode(portfolio.getColorCode());
            savedPortfolio.setIsShared(portfolio.getIsShared());
            
            // Handle PayProp sync if enabled
            if (enablePayPropSync && payPropEnabled && payPropSyncService != null && 
                AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                
                try {
                    portfolioService.syncPortfolioWithPayProp(savedPortfolio.getId(), (long) userId);
                    redirectAttributes.addFlashAttribute("successMessage", 
                        "Portfolio '" + savedPortfolio.getName() + "' created and synced to PayProp successfully!");
                } catch (Exception e) {
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
            portfolio.setSyncStatus(SyncStatus.SYNCED);
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

    @GetMapping("/debug/raw-api-call")
    @ResponseBody
    public ResponseEntity<String> debugRawApiCall(Authentication authentication) {
        try {
            // Let's call the PayProp sync service directly and catch the raw response
            return ResponseEntity.ok("About to call PayProp tags API...\n" +
                                "Base URL: " + payPropApiBase + "\n" +
                                "Full URL: " + payPropApiBase + "/tags");
            
        } catch (Exception e) {
            return ResponseEntity.ok("ERROR: " + e.getMessage());
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

    // ===== PARAMETERIZED ROUTES LAST =====

    /**
     * Portfolio Details - MOVED TO END to prevent route conflicts
     */
    @GetMapping("/{id}")
    public String viewPortfolio(@PathVariable("id") Long portfolioId, Model model, Authentication authentication) {
        try {
            if (!portfolioService.canUserAccessPortfolio(portfolioId, authentication)) {
                return "redirect:/access-denied";
            }
            
            Portfolio portfolio = portfolioService.findById(portfolioId);
            if (portfolio == null) {
                return "error/not-found";
            }
            
            PortfolioAnalytics analytics = portfolioService.getLatestPortfolioAnalytics(portfolioId);
            if (analytics == null) {
                analytics = portfolioService.calculatePortfolioAnalytics(portfolioId, LocalDate.now());
            }
            
            List<Block> blocks = portfolioService.findBlocksByPortfolio(portfolioId);
            List<Property> properties = propertyService.findByPortfolioId(portfolioId);
            
            LocalDate sixMonthsAgo = LocalDate.now().minusMonths(6);
            List<PortfolioAnalytics> analyticsHistory = portfolioService
                .getPortfolioAnalyticsHistory(portfolioId, sixMonthsAgo, LocalDate.now());
            
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
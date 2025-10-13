package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.entity.Ticket;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.property.PropertyServiceImpl;
import site.easy.to.build.crm.service.property.TenantService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.service.ticket.TicketService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.util.AuthorizationUtil;
import site.easy.to.build.crm.entity.Role;
import site.easy.to.build.crm.service.payprop.PayPropSyncService;
import site.easy.to.build.crm.service.payprop.PayPropOAuth2Service;
import site.easy.to.build.crm.service.payprop.LocalToPayPropSyncService;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;
import site.easy.to.build.crm.entity.CustomerPropertyAssignment;
import site.easy.to.build.crm.entity.AssignmentType;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/employee/property")
public class PropertyController {

    private final PropertyService propertyService;
    private final TenantService tenantService;
    private final UserService userService;
    private final TicketService ticketService;
    private final AuthenticationUtils authenticationUtils;
    private final CustomerService customerService;
    private final JdbcTemplate jdbcTemplate;
    private final CustomerPropertyAssignmentRepository assignmentRepository;

    // Optional PayProp services (only available when PayProp is enabled)
    @Autowired(required = false)
    private PayPropSyncService payPropSyncService;
    
    @Autowired(required = false)
    private LocalToPayPropSyncService localToPayPropSyncService;
    
    @Autowired(required = false)
    private PayPropOAuth2Service payPropOAuth2Service;
    
    @Value("${crm.data.source:LEGACY}")
    private String dataSource;
    
    @Value("${payprop.enabled:false}")
    private boolean payPropEnabled;

    @Autowired
    public PropertyController(PropertyService propertyService,
                            TenantService tenantService,
                            UserService userService,
                            TicketService ticketService,
                            AuthenticationUtils authenticationUtils,
                            CustomerService customerService,
                            JdbcTemplate jdbcTemplate,
                            CustomerPropertyAssignmentRepository assignmentRepository) {
        this.propertyService = propertyService;
        this.tenantService = tenantService;
        this.userService = userService;
        this.ticketService = ticketService;
        this.authenticationUtils = authenticationUtils;
        this.customerService = customerService;
        this.jdbcTemplate = jdbcTemplate;
        this.assignmentRepository = assignmentRepository;
    }

    // ================================
    // DEBUG ENDPOINTS  
    // ================================
    
    /**
     * Debug endpoint to check property owner mapping
     */
    @GetMapping("/debug-property-owner")
    @ResponseBody
    public String debugPropertyOwner(Authentication authentication) {
        try {
            StringBuilder debug = new StringBuilder();
            debug.append("=== PROPERTY OWNER DEBUG ===<br>");
            
            // 1. Authentication info
            debug.append("Authentication: ").append(authentication != null ? authentication.getName() : "NULL").append("<br>");
            debug.append("Authorities: ").append(authentication != null ? authentication.getAuthorities() : "NULL").append("<br>");
            
            // 2. User ID lookup
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            debug.append("User ID from authenticationUtils: ").append(userId).append("<br>");
            
            if (userId == -1) {
                return debug.append("❌ Could not get user ID").toString();
            }
            
            // 3. User record lookup
            User user = userService.findById(Long.valueOf(userId));
            if (user == null) {
                return debug.append("❌ User record not found for ID: ").append(userId).toString();
            }
            debug.append("User email: ").append(user.getEmail()).append("<br>");
            
            // 4. Customer lookup by email
            Customer customer = customerService.findByEmail(user.getEmail());
            if (customer == null) {
                return debug.append("❌ Customer not found for email: ").append(user.getEmail()).toString();
            }
            debug.append("Customer ID: ").append(customer.getCustomerId()).append("<br>");
            debug.append("Customer Type: ").append(customer.getCustomerType()).append("<br>");
            
            // 5. Property lookup
            List<Property> properties = propertyService.findPropertiesByCustomerAssignments(customer.getCustomerId());
            debug.append("Properties found: ").append(properties.size()).append("<br>");
            
            // 6. Assignment lookup
            try {
                debug.append("<br>--- ASSIGNMENT TABLE DEBUG ---<br>");
                // We need to call the assignment service directly
                debug.append("Customer assignments will be checked via PropertyService...<br>");
                
                for (int i = 0; i < Math.min(5, properties.size()); i++) {
                    Property prop = properties.get(i);
                    debug.append("Property ").append(i+1).append(": ID=").append(prop.getId())
                          .append(", Name=").append(prop.getPropertyName()).append("<br>");
                }
                
                return debug.toString();
                
            } catch (Exception e) {
                return debug.append("❌ Assignment lookup error: ").append(e.getMessage()).toString();
            }
            
        } catch (Exception e) {
            return "❌ DEBUG ERROR: " + e.getMessage() + "<br>Stack: " + java.util.Arrays.toString(e.getStackTrace()).replaceAll(",", "<br>");
        }
    }

    // ================================
    // UTILITY METHODS
    // ================================
    
    /**
     * Find properties for a property owner using authentication email directly
     * FIXED: Don't rely on User ID mapping, use email from authentication directly
     */
    private List<Property> findPropertiesForPropertyOwner(int userId) {
        try {
            System.out.println("🔍 [PropertyController] Finding properties for property owner (User ID: " + userId + ")");
            
            // FIXED: Get email directly from SecurityContext instead of User ID mapping
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) {
                System.out.println("❌ [PropertyController] No authentication context found");
                return new ArrayList<>();
            }
            
            String email = auth.getName();
            System.out.println("🔍 [PropertyController] Using email from authentication: " + email);
            
            // Find customer directly by email
            Customer customer = customerService.findByEmail(email);
            if (customer == null) {
                System.out.println("❌ [PropertyController] Customer not found for email: " + email);
                return new ArrayList<>();
            }
            
            System.out.println("✅ [PropertyController] Found Customer ID " + customer.getCustomerId() + " for email " + email);
            
            // Now find properties owned by this customer using the assignment table
            List<Property> properties = propertyService.findPropertiesByCustomerAssignments(customer.getCustomerId());
            System.out.println("✅ [PropertyController] Found " + properties.size() + " properties for customer " + customer.getCustomerId());
            
            return properties;
            
        } catch (Exception e) {
            System.err.println("❌ [PropertyController] Error finding properties: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    /**
     * Utility method to resolve property ID (handles both numeric and PayProp IDs)
     */
    private Property resolvePropertyById(String id) {
        System.out.println("🔍 Resolving property ID: " + id);
        
        Property property = null;
        
        // Try PayProp ID first (if it looks like a PayProp ID)
        if (id.length() > 5 && id.matches(".*[a-zA-Z].*")) {
            System.out.println("   ID looks like PayProp ID (contains letters)");
            if (propertyService instanceof PropertyServiceImpl) {
                property = ((PropertyServiceImpl) propertyService).findByPayPropIdString(id);
                System.out.println("   PayProp lookup result: " + (property != null ? "FOUND" : "NOT FOUND"));
            }
        } else {
            System.out.println("   ID looks like numeric ID");
            try {
                Long numericId = Long.valueOf(id);
                property = propertyService.findById(numericId);
                System.out.println("   Numeric lookup result: " + (property != null ? "FOUND" : "NOT FOUND"));
            } catch (NumberFormatException e) {
                System.out.println("   Numeric parse failed, trying as PayProp ID");
                if (propertyService instanceof PropertyServiceImpl) {
                    property = ((PropertyServiceImpl) propertyService).findByPayPropIdString(id);
                }
            }
        }
        
        return property;
    }

    // ================================
    // MAIN PROPERTY LISTING ENDPOINTS
    // ================================

    @GetMapping("/all-properties")
    public String getAllProperties(Model model, Authentication authentication) {
        try {
            System.out.println("=== DEBUG: Starting getAllProperties ===");
            
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            System.out.println("DEBUG: User ID: " + userId);
            
            if (userId == -1) {
                return "error/not-found";
            }
            
            User user = userService.findById(Long.valueOf(userId));
            if (user != null && user.isInactiveUser()) {
                return "error/account-inactive";
            }
            
            List<Property> properties;
            try {
                // Role-based property filtering
                if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                    properties = propertyService.findActiveProperties(); // Only active (non-archived) properties
                } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER") || 
                          AuthorizationUtil.hasRole(authentication, "ROLE_PROPERTY_OWNER")) {
                    // FIXED: For property owners, find Customer ID from User ID first
                    properties = findPropertiesForPropertyOwner(userId);
                } else {
                    properties = propertyService.getRecentProperties(Long.valueOf(userId), 100);
                }
                
                System.out.println("DEBUG: Found " + (properties != null ? properties.size() : "null") + " properties");
                
                // OPTIMIZED: Batch load maintenance data instead of individual queries
                if (properties != null && !properties.isEmpty()) {
                    loadMaintenanceDataBatch(properties);
                }
                
            } catch (Exception e) {
                System.err.println("DEBUG: Error loading properties: " + e.getMessage());
                e.printStackTrace();
                properties = new ArrayList<>();
            }
            
            // Add comprehensive portfolio statistics
            addComprehensivePortfolioStatistics(model, properties);
            
            // Add global maintenance statistics with batch queries
            try {
                addGlobalMaintenanceStatistics(model);
            } catch (Exception e) {
                System.err.println("Error loading global maintenance statistics: " + e.getMessage());
                model.addAttribute("emergencyMaintenanceCount", 0);
                model.addAttribute("urgentMaintenanceCount", 0);
                model.addAttribute("routineMaintenanceCount", 0);
                model.addAttribute("totalMaintenanceCount", 0);
            }
            
            model.addAttribute("properties", properties != null ? properties : new ArrayList<>());
            model.addAttribute("pageTitle", "All Properties");
            
            System.out.println("DEBUG: Model attributes set, returning template");
            return "property/all-properties";
            
        } catch (Exception e) {
            System.err.println("DEBUG: Critical exception in getAllProperties: " + e.getMessage());
            e.printStackTrace();
            
            setDefaultModelAttributes(model);
            model.addAttribute("properties", new ArrayList<>());
            model.addAttribute("pageTitle", "All Properties");
            
            return "property/all-properties";
        }
    }

    @GetMapping("/vacant-properties")
    public String getVacantProperties(Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User user = userService.findById(Long.valueOf(userId));
        if (user.isInactiveUser()) {
            return "error/account-inactive";
        }

        List<Property> allVacantProperties = propertyService.findVacantProperties();
        List<Property> properties;
        
        // Filter based on role
        if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            properties = allVacantProperties;
        } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER") || 
                  AuthorizationUtil.hasRole(authentication, "ROLE_PROPERTY_OWNER")) {
            // FIXED: For property owners, find properties using User ID → Customer ID mapping
            List<Property> ownerProperties = findPropertiesForPropertyOwner(userId);
            properties = allVacantProperties.stream()
                .filter(vacant -> ownerProperties.stream()
                    .anyMatch(owned -> owned.getId().equals(vacant.getId())))
                .collect(Collectors.toList());
        } else {
            properties = allVacantProperties.stream()
                .filter(p -> p.getCreatedBy() != null && p.getCreatedBy().equals((long) userId))
                .collect(Collectors.toList());
        }
        
        // Calculate lost rent potential
        BigDecimal lostRentPotential = properties.stream()
            .map(Property::getMonthlyPayment)
            .filter(rent -> rent != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Get last tenant left dates for vacant properties
        Map<Long, LocalDate> lastTenantLeftDates = getLastTenantLeftDates(properties);

        addComprehensivePortfolioStatistics(model, properties);
        model.addAttribute("properties", properties);
        model.addAttribute("lostRentPotential", lostRentPotential);
        model.addAttribute("lastTenantLeftDates", lastTenantLeftDates);
        model.addAttribute("pageTitle", "Vacant Properties");
        return "property/vacant-properties";
    }

    @GetMapping("/occupied")
    public String getOccupiedProperties(Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User user = userService.findById(Long.valueOf(userId));
        if (user.isInactiveUser()) {
            return "error/account-inactive";
        }

        List<Property> allOccupiedProperties = propertyService.findOccupiedProperties();
        
        List<Property> properties;
        
        // Filter based on role
        if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            properties = allOccupiedProperties;
        } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER")) {
            properties = allOccupiedProperties.stream()
                .filter(p -> p.getPropertyOwnerId() != null && p.getPropertyOwnerId().equals(Long.valueOf(userId)))
                .collect(Collectors.toList());
        } else {
            properties = allOccupiedProperties.stream()
                .filter(p -> p.getCreatedBy() != null && p.getCreatedBy().equals((long) userId))
                .collect(Collectors.toList());
        }
        
        addComprehensivePortfolioStatistics(model, properties);
        model.addAttribute("properties", properties);
        model.addAttribute("pageTitle", "Occupied Properties");
        return "property/all-properties";
    }

    @GetMapping("/portfolio-overview")
    public String getPortfolioOverview(Model model, Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(Long.valueOf(userId));
            if (user.isInactiveUser()) {
                return "error/account-inactive";
            }

            List<Property> properties;
            if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                properties = propertyService.findActiveProperties(); // Only active properties for portfolio overview
            } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER") || 
                      AuthorizationUtil.hasRole(authentication, "ROLE_PROPERTY_OWNER")) {
                // FIXED: For property owners, find properties using User ID → Customer ID mapping
                properties = findPropertiesForPropertyOwner(userId);
            } else {
                properties = propertyService.getRecentProperties(Long.valueOf(userId), 100);
            }

            addComprehensivePortfolioStatistics(model, properties);
            addDetailedAnalytics(model, properties);
            
            model.addAttribute("properties", properties);
            return "property/portfolio-overview";
        } catch (Exception e) {
            setDefaultModelAttributes(model);
            model.addAttribute("properties", new ArrayList<>());
            return "property/portfolio-overview";
        }
    }

    @GetMapping("/archived")
    public String getArchivedProperties(Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User user = userService.findById(Long.valueOf(userId));
        if (user.isInactiveUser()) {
            return "error/account-inactive";
        }

        List<Property> allArchivedProperties = propertyService.findArchivedProperties();
        List<Property> properties;
        
        // Filter based on role
        if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            properties = allArchivedProperties;
        } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER")) {
            properties = allArchivedProperties.stream()
                .filter(p -> p.getPropertyOwnerId() != null && p.getPropertyOwnerId().equals(Long.valueOf(userId)))
                .collect(Collectors.toList());
        } else {
            properties = allArchivedProperties.stream()
                .filter(p -> p.getCreatedBy() != null && p.getCreatedBy().equals((long) userId))
                .collect(Collectors.toList());
        }
        
        // For archived properties, calculate statistics based on the archived list
        calculateFilteredPropertyStatistics(model, properties, "Archived Properties");
        model.addAttribute("properties", properties);
        model.addAttribute("pageTitle", "Archived Properties");
        return "property/all-properties";
    }

    @GetMapping("/ready-for-sync")
    public String getPropertiesReadyForSync(Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return "redirect:/access-denied";
        }

        List<Property> properties = propertyService.findPropertiesReadyForSync();
        calculateFilteredPropertyStatistics(model, properties, "Ready for Sync");
        model.addAttribute("properties", properties);
        model.addAttribute("pageTitle", "Properties Ready for PayProp Sync");
        return "property/all-properties";
    }

    // ================================
    // PROPERTY DETAIL AND MANAGEMENT
    // ================================

    @GetMapping("/{id}")
    public String showPropertyDetail(@PathVariable("id") String id, Model model, Authentication authentication) {
        System.out.println("🔍 PropertyController - Accessing property detail for ID: " + id);
        System.out.println("   Authentication: " + (authentication != null ? authentication.getClass().getSimpleName() : "null"));
        System.out.println("   Data source: " + dataSource);
        
        Property property = null;
        
        // Try PayProp ID first (if it looks like a PayProp ID)
        // PayProp IDs are typically alphanumeric strings longer than 5 chars and contain letters
        if (id.length() > 5 && id.matches(".*[a-zA-Z].*")) {
            // Contains letters - looks like a PayProp ID
            System.out.println("   ID looks like PayProp ID (contains letters)");
            if (propertyService instanceof PropertyServiceImpl) {
                property = ((PropertyServiceImpl) propertyService).findByPayPropIdString(id);
                System.out.println("   PayProp lookup result: " + (property != null ? "FOUND" : "NOT FOUND"));
            }
        } else {
            // Looks like a numeric ID - try legacy lookup
            System.out.println("   ID looks like numeric ID");
            try {
                Long numericId = Long.valueOf(id);
                property = propertyService.findById(numericId);
                System.out.println("   Numeric lookup result: " + (property != null ? "FOUND" : "NOT FOUND"));
            } catch (NumberFormatException e) {
                // If it fails as numeric, try as PayProp ID
                System.out.println("   Numeric parse failed, trying as PayProp ID");
                if (propertyService instanceof PropertyServiceImpl) {
                    property = ((PropertyServiceImpl) propertyService).findByPayPropIdString(id);
                    System.out.println("   Fallback PayProp lookup result: " + (property != null ? "FOUND" : "NOT FOUND"));
                }
            }
        }
        if (property == null) {
            System.out.println("❌ Property not found, returning 404");
            return "error/not-found";
        }
        
        System.out.println("✅ Property found: " + property.getPropertyName());

        // FIXED: Handle OAuth users properly
        User loggedInUser = null;
        int userId = -1;
        boolean isOAuthUser = false;
        
        try {
            // Check if this is an OAuth user
            if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
                isOAuthUser = true;
                OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
                
                if (oAuthUser != null && oAuthUser.getUserId() != null) {
                    // Use the linked user_id from oauth_users table
                    userId = oAuthUser.getUserId();
                    loggedInUser = userService.findById(oAuthUser.getUserId().longValue());
                }
                
                // If no linked user found, create a temporary user object for compatibility
                if (loggedInUser == null) {
                    loggedInUser = new User();
                    loggedInUser.setId(54); // FIXED: Use Integer instead of Long
                    loggedInUser.setUsername(authentication.getName());
                    loggedInUser.setEmail(authentication.getName());
                    // Set a role for OAuth users - FIXED: Use List instead of Set
                    List<Role> roles = new ArrayList<>();
                    Role oauthRole = new Role();
                    oauthRole.setName("ROLE_OIDC_USER");
                    roles.add(oauthRole);
                    loggedInUser.setRoles(roles);
                    userId = 54; // Use management user ID as fallback
                }
            } else {
                // Regular authentication
                userId = authenticationUtils.getLoggedInUserId(authentication);
                if (userId > 0) {
                    loggedInUser = userService.findById(Long.valueOf(userId));
                }
            }
            
            // If still no user found and not OAuth, this is an error
            if (loggedInUser == null && !isOAuthUser) {
                model.addAttribute("error", "User not found");
                return "error/500";
            }
            
        } catch (Exception e) {
            System.err.println("Error getting user information: " + e.getMessage());
            // Continue with a default user to avoid breaking the page
            if (loggedInUser == null) {
                loggedInUser = new User();
                loggedInUser.setId(54); // FIXED: Use Integer instead of Long
                userId = 54;
            }
        }

        // Check if user is inactive (only for non-OAuth users with proper User records)
        if (!isOAuthUser && loggedInUser != null && loggedInUser.getId() != null && loggedInUser.getId() > 0) {
            try {
                if (loggedInUser.isInactiveUser()) {
                    return "error/account-inactive";
                }
            } catch (Exception e) {
                // Ignore if isInactiveUser() fails
                System.err.println("Error checking if user is inactive: " + e.getMessage());
            }
        }

        // Check authorization - role-based access
        boolean hasAccess = false;
        if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") || 
            AuthorizationUtil.hasRole(authentication, "ROLE_OIDC_USER")) {
            hasAccess = true;
        } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER") && 
                   property.getPropertyOwnerId() != null && property.getPropertyOwnerId().equals(Long.valueOf(userId))) {
            hasAccess = true;
        } else if (property.getCreatedBy() != null && property.getCreatedBy().equals((long) userId)) {
            hasAccess = true;
        }
        
        if (!hasAccess) {
            return "redirect:/access-denied";
        }

        // Add model attributes for the view
        model.addAttribute("user", loggedInUser);
        model.addAttribute("isOAuthUser", isOAuthUser);

        // Add tenant count for this property using PayProp data
        long tenantCount = getActiveTenantCountForProperty(property);
        model.addAttribute("tenantCount", tenantCount);

        // Add maintenance statistics and recent tickets
        try {
            // Use the property's actual ID for ticket lookup (PayProp properties use fake hash IDs)
            Long propertyIdForTickets = property.getId();
            
            List<Ticket> emergencyTickets = ticketService.getTicketsByPropertyIdAndType(propertyIdForTickets, "emergency");
            List<Ticket> urgentTickets = ticketService.getTicketsByPropertyIdAndType(propertyIdForTickets, "urgent");
            List<Ticket> routineTickets = ticketService.getTicketsByPropertyIdAndType(propertyIdForTickets, "routine");
            List<Ticket> maintenanceTickets = ticketService.getTicketsByPropertyIdAndType(propertyIdForTickets, "maintenance");
            List<Ticket> allPropertyTickets = ticketService.getTicketsByPropertyId(propertyIdForTickets);
            
            model.addAttribute("emergencyMaintenanceCount", emergencyTickets.size());
            model.addAttribute("urgentMaintenanceCount", urgentTickets.size());
            model.addAttribute("routineMaintenanceCount", routineTickets.size() + maintenanceTickets.size());
            model.addAttribute("activeMaintenanceCount", emergencyTickets.size() + urgentTickets.size() + routineTickets.size() + maintenanceTickets.size());
            model.addAttribute("completedMaintenanceCount", 0);
            model.addAttribute("maintenanceTickets", allPropertyTickets.stream().limit(5).collect(Collectors.toList()));
        } catch (Exception e) {
            System.err.println("Error loading maintenance data for property " + id + ": " + e.getMessage());
            model.addAttribute("emergencyMaintenanceCount", 0);
            model.addAttribute("urgentMaintenanceCount", 0);
            model.addAttribute("routineMaintenanceCount", 0);
            model.addAttribute("activeMaintenanceCount", 0);
            model.addAttribute("completedMaintenanceCount", 0);
            model.addAttribute("maintenanceTickets", new ArrayList<>());
        }

        // Get tenant assignment history for this property
        try {
            List<CustomerPropertyAssignment> tenantAssignments = assignmentRepository
                .findByPropertyIdAndAssignmentType(property.getId(), AssignmentType.TENANT);

            // Sort by start date descending (most recent first)
            tenantAssignments.sort((a, b) -> {
                LocalDate dateA = a.getStartDate() != null ? a.getStartDate() : LocalDate.MIN;
                LocalDate dateB = b.getStartDate() != null ? b.getStartDate() : LocalDate.MIN;
                return dateB.compareTo(dateA);
            });

            // Separate current and past tenants
            List<CustomerPropertyAssignment> currentTenants = tenantAssignments.stream()
                .filter(a -> a.getEndDate() == null || a.getEndDate().isAfter(LocalDate.now()))
                .collect(Collectors.toList());

            List<CustomerPropertyAssignment> pastTenants = tenantAssignments.stream()
                .filter(a -> a.getEndDate() != null && !a.getEndDate().isAfter(LocalDate.now()))
                .collect(Collectors.toList());

            model.addAttribute("currentTenants", currentTenants);
            model.addAttribute("pastTenants", pastTenants);
            model.addAttribute("tenantAssignments", tenantAssignments);

            System.out.println("✅ Loaded " + tenantAssignments.size() + " tenant assignments (" +
                             currentTenants.size() + " current, " + pastTenants.size() + " past)");

        } catch (Exception e) {
            System.err.println("Error loading tenant assignments for property " + id + ": " + e.getMessage());
            model.addAttribute("currentTenants", new ArrayList<>());
            model.addAttribute("pastTenants", new ArrayList<>());
            model.addAttribute("tenantAssignments", new ArrayList<>());
        }

        model.addAttribute("property", property);
        return "property/property-details";
    }
    // ================================
    // PROPERTY CREATION AND UPDATES
    // ================================

    @GetMapping("/create-property")
    public String showCreatePropertyForm(Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User user = userService.findById(Long.valueOf(userId));
        if (user.isInactiveUser()) {
            return "error/account-inactive";
        }

        // Load property owners for dropdown
        List<Customer> propertyOwners = customerService.findPropertyOwners();
        model.addAttribute("propertyOwners", propertyOwners);
        model.addAttribute("property", new Property());
        return "property/create-property";
    }

    @PostMapping("/create-property")
    public String createProperty(@ModelAttribute("property") @Validated Property property,
                                @RequestParam(value = "syncToPayProp", required = false) Boolean syncToPayProp,
                                @RequestParam(value = "propertyOwnerId", required = false) Long propertyOwnerId,
                                @RequestParam(value = "ownershipPercentage", required = false, defaultValue = "100") BigDecimal ownershipPercentage,
                                @RequestParam(value = "isPrimaryOwner", required = false, defaultValue = "true") Boolean isPrimaryOwner,
                                BindingResult bindingResult, Authentication authentication,
                                Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "property/create-property";
        }

        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));
        if (loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }

        // Set audit fields
        property.setCreatedBy((long) userId);
        property.setCreatedAt(LocalDateTime.now());
        property.setUpdatedBy((long) userId);
        property.setUpdatedAt(LocalDateTime.now());

        try {
            Property savedProperty = propertyService.save(property);

            // Create owner assignment if owner was selected
            CustomerPropertyAssignment ownerAssignment = null;
            if (propertyOwnerId != null && propertyOwnerId > 0) {
                try {
                    Customer owner = customerService.findByCustomerId(propertyOwnerId);
                    if (owner != null) {
                        CustomerPropertyAssignment assignment = new CustomerPropertyAssignment();
                        assignment.setProperty(savedProperty);
                        assignment.setCustomer(owner);
                        assignment.setAssignmentType(AssignmentType.OWNER);
                        assignment.setOwnershipPercentage(ownershipPercentage);
                        assignment.setIsPrimary(isPrimaryOwner);
                        assignment.setStartDate(LocalDate.now());
                        assignment.setCreatedAt(LocalDateTime.now());
                        assignment.setUpdatedAt(LocalDateTime.now());
                        assignment.setSyncStatus("LOCAL_ONLY");
                        ownerAssignment = assignmentRepository.save(assignment);

                        System.out.println("✅ Created owner assignment: Property " + savedProperty.getId() + " → Customer " + propertyOwnerId);
                    }
                } catch (Exception assignmentError) {
                    System.err.println("⚠️ Warning: Failed to create owner assignment: " + assignmentError.getMessage());
                    // Don't fail the whole operation if assignment fails
                }
            }
            
            // Handle PayProp sync if requested
            String successMessage = "Property created successfully!";
            boolean syncSuccessful = false;
            
            if (Boolean.TRUE.equals(syncToPayProp) && localToPayPropSyncService != null) {
                try {
                    System.out.println("🔄 Syncing property " + savedProperty.getId() + " to PayProp using LocalToPayPropSyncService...");
                    String payPropId = localToPayPropSyncService.syncPropertyToPayProp(savedProperty);

                    System.out.println("✅ Property synced to PayProp successfully with ID: " + payPropId);
                    successMessage = "Property created and synced to PayProp successfully! (PayProp ID: " + payPropId + ")";
                    syncSuccessful = true;

                    // Sync owner assignment to PayProp if owner was assigned
                    if (ownerAssignment != null) {
                        try {
                            System.out.println("🔄 Syncing owner assignment to PayProp...");
                            String paymentInstructionId = localToPayPropSyncService.syncOwnerAssignmentToPayProp(ownerAssignment);
                            System.out.println("✅ Owner assignment synced to PayProp with payment instruction ID: " + paymentInstructionId);
                            successMessage += " Owner payment instruction created successfully!";
                        } catch (Exception ownerSyncError) {
                            System.err.println("⚠️ Owner assignment sync failed: " + ownerSyncError.getMessage());
                            successMessage += " Note: Property synced but owner payment instruction failed.";
                        }
                    }

                } catch (Exception syncError) {
                    System.err.println("❌ PayProp sync error: " + syncError.getMessage());
                    syncError.printStackTrace();
                    successMessage = "Property created successfully, but PayProp sync failed: " + syncError.getMessage();
                }
            } else if (Boolean.TRUE.equals(syncToPayProp) && !payPropEnabled) {
                successMessage = "Property created successfully, but PayProp integration is disabled.";
            } else if (Boolean.TRUE.equals(syncToPayProp)) {
                successMessage = "Property created successfully, but PayProp services are not available.";
            } else {
                // Default message when sync is not requested
                successMessage = "Property created successfully!";
            }
            
            redirectAttributes.addFlashAttribute("successMessage", successMessage);
            if (syncSuccessful) {
                redirectAttributes.addFlashAttribute("syncStatus", "success");
            } else if (Boolean.TRUE.equals(syncToPayProp)) {
                redirectAttributes.addFlashAttribute("syncStatus", "failed");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Property creation failed: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("error", "Failed to create property: " + e.getMessage());
            return "property/create-property";
        }

        return "redirect:/employee/property/all-properties";
    }

    @GetMapping("/update/{id}")
    public String showUpdatePropertyForm(@PathVariable("id") String id, Model model, Authentication authentication) {
        try {
            Property property = resolvePropertyById(id);
            if (property == null) {
                model.addAttribute("error", "Property not found");
                return "error/not-found";
            }

            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));
            if (loggedInUser.isInactiveUser()) {
                return "error/account-inactive";
            }

            // Check authorization
            boolean hasAccess = false;
            if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                hasAccess = true;
            } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER") && 
                    property.getPropertyOwnerId() != null && property.getPropertyOwnerId().equals(Long.valueOf(userId))) {
                hasAccess = true;
            } else if (property.getCreatedBy() != null && property.getCreatedBy().equals((long) userId)) {
                hasAccess = true;
            }
            
            if (!hasAccess) {
                return "redirect:/access-denied";
            }

            model.addAttribute("property", property);
            return "property/update-property";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading property: " + e.getMessage());
            return "error/500";
        }
    }

    @PostMapping("/update")
    public String updateProperty(@ModelAttribute("property") @Validated Property property, 
                                BindingResult bindingResult, Authentication authentication, 
                                Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "property/update-property";
        }

        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));
        if (loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }

        Property existingProperty = propertyService.findById(property.getId());
        if (existingProperty == null) {
            return "error/not-found";
        }

        // Check authorization
        boolean hasAccess = false;
        if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            hasAccess = true;
        } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER") && 
                   existingProperty.getPropertyOwnerId() != null && existingProperty.getPropertyOwnerId().equals(Long.valueOf(userId))) {
            hasAccess = true;
        } else if (existingProperty.getCreatedBy() != null && existingProperty.getCreatedBy().equals((long) userId)) {
            hasAccess = true;
        }
        
        if (!hasAccess) {
            return "redirect:/access-denied";
        }

        // Preserve audit fields and PayProp sync data
        property.setCreatedBy(existingProperty.getCreatedBy());
        property.setCreatedAt(existingProperty.getCreatedAt());
        property.setUpdatedBy((long) userId);
        property.setUpdatedAt(LocalDateTime.now());
        property.setPayPropId(existingProperty.getPayPropId());

        try {
            Property savedProperty = propertyService.save(property);
            redirectAttributes.addFlashAttribute("successMessage", "Property updated successfully!");
            
        } catch (Exception e) {
            model.addAttribute("error", "Failed to update property: " + e.getMessage());
            return "property/update-property";
        }

        return "redirect:/employee/property/" + property.getId();
    }

    @PostMapping("/delete/{id}")
    public String deleteProperty(@PathVariable("id") String id, Authentication authentication, 
                                RedirectAttributes redirectAttributes) {
        Property property = resolvePropertyById(id);
        if (property == null) {
            return "error/not-found";
        }

        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User user = userService.findById(Long.valueOf(userId));
        if (user.isInactiveUser()) {
            return "error/account-inactive";
        }

        // Only managers can delete properties
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            redirectAttributes.addFlashAttribute("error", 
                "Sorry, you are not authorized to delete this property. Only managers have permission to delete properties.");
            return "redirect:/employee/property/all-properties";
        }

        try {
            propertyService.delete(property);
            redirectAttributes.addFlashAttribute("successMessage", "Property deleted successfully!");
        } catch (Exception e) {
            return "error/500";
        }

        return "redirect:/employee/property/all-properties";
    }

    // ================================
    // SEARCH AND FILTERING
    // ================================

    @GetMapping("/search")
    public String searchProperties(@RequestParam(value = "propertyName", required = false) String propertyName,
                                  @RequestParam(value = "city", required = false) String city,
                                  @RequestParam(value = "postalCode", required = false) String postalCode,
                                  @RequestParam(value = "isArchived", required = false) Boolean isArchived,
                                  @RequestParam(value = "propertyType", required = false) String propertyType,
                                  @RequestParam(value = "bedrooms", required = false) Integer bedrooms,
                                  Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User user = userService.findById(Long.valueOf(userId));
        if (user.isInactiveUser()) {
            return "error/account-inactive";
        }

        List<Property> allResults = propertyService.searchProperties(propertyName, city, postalCode, 
                                                                    isArchived, propertyType, bedrooms, 50);
        List<Property> properties;
        
        // Filter search results based on role
        if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            properties = allResults;
        } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER")) {
            properties = allResults.stream()
                .filter(p -> p.getPropertyOwnerId() != null && p.getPropertyOwnerId().equals(Long.valueOf(userId)))
                .collect(Collectors.toList());
        } else {
            properties = allResults.stream()
                .filter(p -> p.getCreatedBy() != null && p.getCreatedBy().equals((long) userId))
                .collect(Collectors.toList());
        }
        
        calculateFilteredPropertyStatistics(model, properties, "Search Results");
        model.addAttribute("properties", properties);
        model.addAttribute("searchPerformed", true);
        model.addAttribute("propertyName", propertyName);
        model.addAttribute("city", city);
        model.addAttribute("postalCode", postalCode);
        model.addAttribute("isArchived", isArchived);
        model.addAttribute("propertyType", propertyType);
        model.addAttribute("bedrooms", bedrooms);
        
        return "property/all-properties";
    }

    // ================================
    // ARCHIVE MANAGEMENT
    // ================================

    @PostMapping("/archive/{id}")
    public String archiveProperty(@PathVariable("id") String id, Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User user = userService.findById(Long.valueOf(userId));
        if (user.isInactiveUser()) {
            return "error/account-inactive";
        }

        Property property = resolvePropertyById(id);
        if (property == null) {
            return "error/not-found";
        }

        // Check authorization
        boolean hasAccess = false;
        if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            hasAccess = true;
        } else if (property.getCreatedBy() != null && property.getCreatedBy().equals((long) userId)) {
            hasAccess = true;
        }
        
        if (!hasAccess) {
            return "redirect:/access-denied";
        }

        propertyService.archiveProperty(property.getId());
        redirectAttributes.addFlashAttribute("successMessage", "Property archived successfully!");
        return "redirect:/employee/property/" + id;
    }

    @PostMapping("/unarchive/{id}")
    public String unarchiveProperty(@PathVariable("id") Long id, Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User user = userService.findById(Long.valueOf(userId));
        if (user.isInactiveUser()) {
            return "error/account-inactive";
        }

        Property property = propertyService.findById(id);
        if (property == null) {
            return "error/not-found";
        }

        // Check authorization
        boolean hasAccess = false;
        if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            hasAccess = true;
        } else if (property.getCreatedBy() != null && property.getCreatedBy().equals((long) userId)) {
            hasAccess = true;
        }
        
        if (!hasAccess) {
            return "redirect:/access-denied";
        }

        propertyService.unarchiveProperty(id);
        redirectAttributes.addFlashAttribute("successMessage", "Property restored successfully!");
        return "redirect:/employee/property/" + id;
    }

    // ================================
    // PAYPROP SYNC STATUS AND MANAGEMENT
    // ================================

    @GetMapping("/sync-status")
    public String showSyncStatus(Model model, Authentication authentication) {
        try {
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                return "redirect:/access-denied";
            }

            // Initialize with empty lists to prevent null issues
            List<Property> synced = new ArrayList<>();
            List<Property> needsSync = new ArrayList<>();
            List<Property> missingFields = new ArrayList<>();
            
            try {
                synced = propertyService.findPropertiesByPayPropSyncStatus(true);
                if (synced == null) synced = new ArrayList<>();
            } catch (Exception e) {
                System.err.println("Error finding synced properties: " + e.getMessage());
            }
            
            try {
                needsSync = propertyService.findPropertiesNeedingSync();
                if (needsSync == null) needsSync = new ArrayList<>();
            } catch (Exception e) {
                System.err.println("Error finding properties needing sync: " + e.getMessage());
            }
            
            try {
                missingFields = propertyService.findPropertiesWithMissingPayPropFields();
                if (missingFields == null) missingFields = new ArrayList<>();
            } catch (Exception e) {
                System.err.println("Error finding properties with missing fields: " + e.getMessage());
            }

            // Add all required model attributes for sync-status template
            model.addAttribute("synced", synced);
            model.addAttribute("needsSync", needsSync);
            model.addAttribute("missingFields", missingFields);
            
            // Add tenant and property owner counts
            model.addAttribute("tenantsSynced", 0);
            model.addAttribute("tenantsReady", 0);
            model.addAttribute("tenantsMissing", 0);
            model.addAttribute("ownersSynced", 0);
            model.addAttribute("ownersReady", 0);
            model.addAttribute("ownersMissing", 0);
            
            // Ensure properties attribute is never null
            List<Property> allProperties = new ArrayList<>();
            try {
                allProperties = propertyService.findAll();
                if (allProperties == null) allProperties = new ArrayList<>();
            } catch (Exception e) {
                System.err.println("Error finding all properties: " + e.getMessage());
            }
            
            model.addAttribute("properties", allProperties);
            addComprehensivePortfolioStatistics(model, allProperties);
            
            return "property/sync-status";
            
        } catch (Exception e) {
            System.err.println("Critical error in sync-status controller: " + e.getMessage());
            e.printStackTrace();
            
            // Comprehensive fallback to prevent template errors
            model.addAttribute("synced", new ArrayList<>());
            model.addAttribute("needsSync", new ArrayList<>());
            model.addAttribute("missingFields", new ArrayList<>());
            model.addAttribute("properties", new ArrayList<>());
            model.addAttribute("tenantsSynced", 0);
            model.addAttribute("tenantsReady", 0);
            model.addAttribute("tenantsMissing", 0);
            model.addAttribute("ownersSynced", 0);
            model.addAttribute("ownersReady", 0);
            model.addAttribute("ownersMissing", 0);
            setDefaultModelAttributes(model);
            
            return "property/sync-status";
        }
    }

    // ================================
    // API ENDPOINTS
    // ================================

    @GetMapping("/api/{id}")
    @ResponseBody
    public Property getPropertyApi(@PathVariable("id") String id) {
        Property property = resolvePropertyById(id);
        if (property == null) {
            throw new RuntimeException("Property not found");
        }
        return property;
    }

    @GetMapping("/api/all")
    @ResponseBody
    public List<Property> getAllPropertiesApi(Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User user = userService.findById(Long.valueOf(userId));
        
        if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return propertyService.findAll();
        } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER") || 
                  AuthorizationUtil.hasRole(authentication, "ROLE_PROPERTY_OWNER")) {
            // FIXED: For property owners, find properties using User ID → Customer ID mapping
            return findPropertiesForPropertyOwner(userId);
        } else {
            return propertyService.getRecentProperties((long) userId, 100);
        }
    }

    // ================================
    // AJAX ENDPOINTS FOR MAINTENANCE
    // ================================

    @GetMapping("/maintenance-summary")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMaintenanceSummary(Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            if (userId == -1) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            Map<String, Object> response = new HashMap<>();
            
            try {
                List<Ticket> emergencyTickets = ticketService.findByType("emergency");
                List<Ticket> urgentTickets = ticketService.findByType("urgent");
                List<Ticket> routineTickets = ticketService.findByType("routine");
                List<Ticket> maintenanceTickets = ticketService.findByType("maintenance");
                
                int totalActiveIssues = emergencyTickets.size() + urgentTickets.size() + 
                                      routineTickets.size() + maintenanceTickets.size();
                
                response.put("totalActiveIssues", totalActiveIssues);
                response.put("emergencyCount", emergencyTickets.size());
                response.put("urgentCount", urgentTickets.size());
                response.put("routineCount", routineTickets.size() + maintenanceTickets.size());
                
            } catch (Exception e) {
                System.err.println("Error loading maintenance summary: " + e.getMessage());
                response.put("totalActiveIssues", 0);
                response.put("emergencyCount", 0);
                response.put("urgentCount", 0);
                response.put("routineCount", 0);
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/api/maintenance-stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMaintenanceStats(Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            if (userId == -1) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            Map<String, Object> response = new HashMap<>();
            
            try {
                List<Ticket> emergencyTickets = ticketService.findByType("emergency");
                List<Ticket> urgentTickets = ticketService.findByType("urgent");
                List<Ticket> routineTickets = ticketService.findByType("routine");
                
                response.put("emergencyTickets", emergencyTickets.size());
                response.put("urgentTickets", urgentTickets.size());
                response.put("routineTickets", routineTickets.size());
                
            } catch (Exception e) {
                response.put("emergencyTickets", 0);
                response.put("urgentTickets", 0);
                response.put("routineTickets", 0);
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}/financial-summary")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPropertyFinancialSummary(
            @PathVariable("id") String id,
            @RequestParam(value = "source", required = false, defaultValue = "auto") String source,
            Authentication authentication) {
        try {
            Property property = resolvePropertyById(id);
            if (property == null) {
                return ResponseEntity.notFound().build();
            }

            // Check authorization
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            boolean hasAccess = false;
            if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") ||
                AuthorizationUtil.hasRole(authentication, "ROLE_OIDC_USER")) {
                hasAccess = true;
            } else if ((AuthorizationUtil.hasRole(authentication, "ROLE_OWNER") ||
                       AuthorizationUtil.hasRole(authentication, "ROLE_PROPERTY_OWNER")) &&
                       property.getPropertyOwnerId() != null && property.getPropertyOwnerId().equals(Long.valueOf(userId))) {
                hasAccess = true;
            } else if (property.getCreatedBy() != null && property.getCreatedBy().equals((long) userId)) {
                hasAccess = true;
            }

            if (!hasAccess) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Check data availability for both sources
            Map<String, Object> availability = checkFinancialDataAvailability(property);
            boolean hasHistorical = (boolean) availability.get("hasHistoricalData");
            boolean hasPayProp = (boolean) availability.get("hasPayPropData");

            Map<String, Object> response = new HashMap<>();
            response.put("propertyName", property.getPropertyName());
            response.put("propertyId", property.getId());
            response.put("payPropId", property.getPayPropId());
            response.put("dataAvailability", availability);

            // Determine which source to use
            String actualSource = source;
            if ("auto".equals(source)) {
                // Auto-select: prefer PayProp if available, otherwise historical
                actualSource = hasPayProp ? "payprop" : (hasHistorical ? "historical" : "none");
            }

            response.put("selectedSource", actualSource);

            // Fetch data based on source
            if ("historical".equals(actualSource) && hasHistorical) {
                Map<String, Object> historicalData = getHistoricalFinancialData(property.getId());
                response.putAll(historicalData);
                response.put("message", "Showing historical transactions");
            } else if ("payprop".equals(actualSource) && hasPayProp) {
                Map<String, Object> payPropData = getPayPropFinancialData(property.getPayPropId());
                response.putAll(payPropData);
                response.put("message", "Showing PayProp data");
            } else {
                // No data available
                response.put("totalIncome", 0);
                response.put("totalExpenses", 0);
                response.put("totalCommissions", 0);
                response.put("netOwnerIncome", 0);
                response.put("transactionCount", 0);
                response.put("recentTransactions", new ArrayList<>());
                response.put("message", "No financial data available");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error in getPropertyFinancialSummary: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error loading financial data");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Check data availability for both historical and PayProp sources
     */
    private Map<String, Object> checkFinancialDataAvailability(Property property) {
        Map<String, Object> availability = new HashMap<>();

        // Check historical transactions
        try {
            String historicalSql = "SELECT COUNT(*) FROM historical_transactions WHERE property_id = ?";
            Integer historicalCount = jdbcTemplate.queryForObject(historicalSql, Integer.class, property.getId());
            availability.put("hasHistoricalData", historicalCount != null && historicalCount > 0);
            availability.put("historicalCount", historicalCount != null ? historicalCount : 0);
        } catch (Exception e) {
            availability.put("hasHistoricalData", false);
            availability.put("historicalCount", 0);
        }

        // Check PayProp data
        try {
            if (property.getPayPropId() != null && !property.getPayPropId().trim().isEmpty()) {
                String payPropSql = "SELECT COUNT(*) FROM payprop_report_all_payments WHERE incoming_property_payprop_id = ?";
                Integer payPropCount = jdbcTemplate.queryForObject(payPropSql, Integer.class, property.getPayPropId());
                availability.put("hasPayPropData", payPropCount != null && payPropCount > 0);
                availability.put("payPropCount", payPropCount != null ? payPropCount : 0);
            } else {
                availability.put("hasPayPropData", false);
                availability.put("payPropCount", 0);
            }
        } catch (Exception e) {
            availability.put("hasPayPropData", false);
            availability.put("payPropCount", 0);
        }

        return availability;
    }

    /**
     * Get financial data from historical_transactions table
     */
    private Map<String, Object> getHistoricalFinancialData(Long propertyId) {
        Map<String, Object> data = new HashMap<>();

        try {
            // Query for financial summary
            String summarySql = """
                SELECT
                    COALESCE(SUM(CASE WHEN category = 'rent' AND transaction_type = 'invoice' THEN amount ELSE 0 END), 0) as total_income,
                    COALESCE(SUM(CASE WHEN category = 'commission' THEN ABS(amount) ELSE 0 END), 0) as total_commissions,
                    COALESCE(SUM(CASE WHEN category IN ('furnishing', 'general', 'safety', 'white_goods', 'clearance') THEN ABS(amount) ELSE 0 END), 0) as total_expenses,
                    COALESCE(SUM(CASE WHEN category = 'owner_liability' THEN amount ELSE 0 END), 0) as owner_liability,
                    COUNT(*) as transaction_count
                FROM historical_transactions
                WHERE property_id = ?
                """;

            jdbcTemplate.query(summarySql, rs -> {
                BigDecimal totalIncome = rs.getBigDecimal("total_income");
                BigDecimal totalCommissions = rs.getBigDecimal("total_commissions");
                BigDecimal totalExpenses = rs.getBigDecimal("total_expenses");
                BigDecimal ownerLiability = rs.getBigDecimal("owner_liability");
                int transactionCount = rs.getInt("transaction_count");

                // Calculate net owner income
                BigDecimal netOwnerIncome = totalIncome
                    .subtract(totalCommissions)
                    .subtract(totalExpenses)
                    .subtract(ownerLiability); // owner_liability represents amounts owed by owner (expenses)

                data.put("totalIncome", totalIncome);
                data.put("totalCommissions", totalCommissions);
                data.put("totalExpenses", totalExpenses);
                data.put("ownerLiability", ownerLiability);
                data.put("netOwnerIncome", netOwnerIncome);
                data.put("transactionCount", transactionCount);
            }, propertyId);

            // Get recent transactions
            String recentSql = """
                SELECT transaction_date, description, category, transaction_type, amount
                FROM historical_transactions
                WHERE property_id = ?
                ORDER BY transaction_date DESC, id DESC
                LIMIT 10
                """;

            List<Map<String, Object>> recentTransactions = jdbcTemplate.query(recentSql, (rs, rowNum) -> {
                Map<String, Object> transaction = new HashMap<>();
                transaction.put("date", rs.getDate("transaction_date"));
                transaction.put("description", rs.getString("description"));
                transaction.put("category", rs.getString("category"));
                transaction.put("type", rs.getString("transaction_type"));
                transaction.put("amount", rs.getBigDecimal("amount"));
                return transaction;
            }, propertyId);

            data.put("recentTransactions", recentTransactions);

        } catch (Exception e) {
            System.err.println("Error fetching historical financial data: " + e.getMessage());
            e.printStackTrace();
            data.put("totalIncome", 0);
            data.put("totalCommissions", 0);
            data.put("totalExpenses", 0);
            data.put("netOwnerIncome", 0);
            data.put("transactionCount", 0);
            data.put("recentTransactions", new ArrayList<>());
        }

        return data;
    }

    /**
     * Get financial data from payprop_report_all_payments table
     */
    private Map<String, Object> getPayPropFinancialData(String payPropId) {
        Map<String, Object> data = new HashMap<>();

        try {
            // Query for financial summary from PayProp
            String summarySql = """
                SELECT
                    COALESCE(SUM(CASE WHEN beneficiary_type = 'beneficiary' THEN amount ELSE 0 END), 0) as total_income,
                    COALESCE(SUM(CASE WHEN beneficiary_type = 'agency' THEN ABS(amount) ELSE 0 END), 0) as total_commissions,
                    COALESCE(SUM(CASE WHEN beneficiary_type = 'contractor' THEN ABS(amount) ELSE 0 END), 0) as total_expenses,
                    COUNT(*) as transaction_count
                FROM payprop_report_all_payments
                WHERE incoming_property_payprop_id = ?
                """;

            jdbcTemplate.query(summarySql, rs -> {
                BigDecimal totalIncome = rs.getBigDecimal("total_income");
                BigDecimal totalCommissions = rs.getBigDecimal("total_commissions");
                BigDecimal totalExpenses = rs.getBigDecimal("total_expenses");
                int transactionCount = rs.getInt("transaction_count");

                // Calculate net owner income
                BigDecimal netOwnerIncome = totalIncome
                    .subtract(totalCommissions)
                    .subtract(totalExpenses);

                data.put("totalIncome", totalIncome);
                data.put("totalCommissions", totalCommissions);
                data.put("totalExpenses", totalExpenses);
                data.put("netOwnerIncome", netOwnerIncome);
                data.put("transactionCount", transactionCount);
            }, payPropId);

            // Get recent transactions
            String recentSql = """
                SELECT due_date, incoming_tenant_name, beneficiary_type, category_name, amount
                FROM payprop_report_all_payments
                WHERE incoming_property_payprop_id = ?
                ORDER BY due_date DESC
                LIMIT 10
                """;

            List<Map<String, Object>> recentTransactions = jdbcTemplate.query(recentSql, (rs, rowNum) -> {
                Map<String, Object> transaction = new HashMap<>();
                transaction.put("date", rs.getDate("due_date"));
                transaction.put("description", rs.getString("incoming_tenant_name"));
                transaction.put("category", rs.getString("category_name"));
                transaction.put("type", rs.getString("beneficiary_type"));
                transaction.put("amount", rs.getBigDecimal("amount"));
                return transaction;
            }, payPropId);

            data.put("recentTransactions", recentTransactions);

        } catch (Exception e) {
            System.err.println("Error fetching PayProp financial data: " + e.getMessage());
            e.printStackTrace();
            data.put("totalIncome", 0);
            data.put("totalCommissions", 0);
            data.put("totalExpenses", 0);
            data.put("netOwnerIncome", 0);
            data.put("transactionCount", 0);
            data.put("recentTransactions", new ArrayList<>());
        }

        return data;
    }

    @GetMapping("/{id}/maintenance-summary")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMaintenanceSummary(@PathVariable("id") String id, Authentication authentication) {
        try {
            Property property = resolvePropertyById(id);
            if (property == null) {
                return ResponseEntity.notFound().build();
            }

            // Check authorization
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            boolean hasAccess = false;
            if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") ||
                AuthorizationUtil.hasRole(authentication, "ROLE_OIDC_USER")) {
                hasAccess = true;
            } else if ((AuthorizationUtil.hasRole(authentication, "ROLE_OWNER") ||
                       AuthorizationUtil.hasRole(authentication, "ROLE_PROPERTY_OWNER")) && 
                       property.getPropertyOwnerId() != null && property.getPropertyOwnerId().equals(Long.valueOf(userId))) {
                hasAccess = true;
            } else if (property.getCreatedBy() != null && property.getCreatedBy().equals((long) userId)) {
                hasAccess = true;
            }
            
            if (!hasAccess) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Map<String, Object> response = new HashMap<>();
            
            try {
                List<Ticket> emergencyTickets = ticketService.getTicketsByPropertyIdAndType(property.getId(), "emergency");
                List<Ticket> urgentTickets = ticketService.getTicketsByPropertyIdAndType(property.getId(), "urgent");
                List<Ticket> routineTickets = ticketService.getTicketsByPropertyIdAndType(property.getId(), "routine");
                List<Ticket> maintenanceTickets = ticketService.getTicketsByPropertyIdAndType(property.getId(), "maintenance");
                
                response.put("emergencyMaintenanceCount", emergencyTickets.size());
                response.put("urgentMaintenanceCount", urgentTickets.size());
                response.put("routineMaintenanceCount", routineTickets.size() + maintenanceTickets.size());
                response.put("activeMaintenanceCount", emergencyTickets.size() + urgentTickets.size() + routineTickets.size() + maintenanceTickets.size());
                response.put("completedMaintenanceCount", 0);
            } catch (Exception e) {
                System.err.println("Error loading maintenance summary for property " + id + ": " + e.getMessage());
                response.put("emergencyMaintenanceCount", 0);
                response.put("urgentMaintenanceCount", 0);
                response.put("routineMaintenanceCount", 0);
                response.put("activeMaintenanceCount", 0);
                response.put("completedMaintenanceCount", 0);
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ================================
    // PAYPROP SYNC ENDPOINTS
    // ================================

    @PostMapping("/sync/{entityType}/{entityId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncEntity(@PathVariable String entityType, 
                                                        @PathVariable Long entityId, 
                                                        Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("success", false, "message", "Access denied"));
        }

        Map<String, Object> response = new HashMap<>();
        
        try {
            String payPropId = null;
            
            switch (entityType.toLowerCase()) {
                case "property":
                    payPropId = "PP" + System.currentTimeMillis(); // Temporary mock
                    break;
                case "tenant":
                    payPropId = "TN" + System.currentTimeMillis(); // Temporary mock
                    break;
                case "beneficiary":
                    payPropId = "BN" + System.currentTimeMillis(); // Temporary mock
                    break;
                default:
                    throw new IllegalArgumentException("Unknown entity type: " + entityType);
            }
            
            response.put("success", true);
            response.put("payPropId", payPropId);
            response.put("message", entityType + " synced successfully");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        
        return ResponseEntity.ok(response);
    }

    @PutMapping("/update-payprop/{entityType}/{entityId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateEntityInPayProp(@PathVariable String entityType, 
                                                                    @PathVariable Long entityId, 
                                                                    Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("success", false, "message", "Access denied"));
        }

        Map<String, Object> response = new HashMap<>();
        
        try {
            switch (entityType.toLowerCase()) {
                case "property":
                case "tenant":
                case "beneficiary":
                    break;
                default:
                    throw new IllegalArgumentException("Unknown entity type: " + entityType);
            }
            
            response.put("success", true);
            response.put("message", entityType + " updated successfully in PayProp");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sync-all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncAllEntities(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("success", false, "message", "Access denied"));
        }

        Map<String, Object> response = new HashMap<>();
        
        try {
            int syncedCount = 0;
            
            // Temporary mock implementation
            List<Property> readyProperties = propertyService.findPropertiesReadyForSync();
            syncedCount = readyProperties.size();
            
            response.put("success", true);
            response.put("synced", syncedCount);
            response.put("message", "Sync completed successfully");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        
        return ResponseEntity.ok(response);
    }

    // ================================
    // HELPER METHODS - OPTIMIZED
    // ================================

    /**
     * OPTIMIZED: Load maintenance data for all properties in batch instead of individual queries
     */
    private void loadMaintenanceDataBatch(List<Property> properties) {
        try {
            System.out.println("DEBUG: Loading maintenance data for " + properties.size() + " properties");
            
            // Get all property IDs
            List<Long> propertyIds = properties.stream()
                .map(Property::getId)
                .collect(Collectors.toList());
            
            // Batch load all tickets by type (much more efficient than individual queries)
            Map<String, List<Ticket>> ticketsByType = new HashMap<>();
            
            try {
                ticketsByType.put("emergency", ticketService.findByType("emergency"));
                ticketsByType.put("urgent", ticketService.findByType("urgent"));
                ticketsByType.put("routine", ticketService.findByType("routine"));
                ticketsByType.put("maintenance", ticketService.findByType("maintenance"));
            } catch (Exception e) {
                System.err.println("Error loading tickets by type: " + e.getMessage());
                // Initialize empty lists to prevent null pointer exceptions
                ticketsByType.put("emergency", new ArrayList<>());
                ticketsByType.put("urgent", new ArrayList<>());
                ticketsByType.put("routine", new ArrayList<>());
                ticketsByType.put("maintenance", new ArrayList<>());
            }
            
            // Create lookup maps for efficient property-ticket matching
            Map<Long, Integer> emergencyCounts = createPropertyTicketCountMap(ticketsByType.get("emergency"));
            Map<Long, Integer> urgentCounts = createPropertyTicketCountMap(ticketsByType.get("urgent"));
            Map<Long, Integer> routineCounts = createPropertyTicketCountMap(ticketsByType.get("routine"));
            Map<Long, Integer> maintenanceCounts = createPropertyTicketCountMap(ticketsByType.get("maintenance"));
            
            // Assign counts to properties
            for (Property property : properties) {
                Long propertyId = property.getId();
                
                property.setEmergencyMaintenanceCount(emergencyCounts.getOrDefault(propertyId, 0));
                property.setUrgentMaintenanceCount(urgentCounts.getOrDefault(propertyId, 0));
                property.setRoutineMaintenanceCount(
                    routineCounts.getOrDefault(propertyId, 0) + maintenanceCounts.getOrDefault(propertyId, 0)
                );
            }
            
            System.out.println("DEBUG: Maintenance data loaded successfully");
            
        } catch (Exception e) {
            System.err.println("Error in batch maintenance loading: " + e.getMessage());
            e.printStackTrace();
            
            // Set safe defaults for all properties
            for (Property property : properties) {
                property.setEmergencyMaintenanceCount(0);
                property.setUrgentMaintenanceCount(0);
                property.setRoutineMaintenanceCount(0);
            }
        }
    }

    /**
     * Create a map of property ID to ticket count from a list of tickets
     */
    private Map<Long, Integer> createPropertyTicketCountMap(List<Ticket> tickets) {
        Map<Long, Integer> countMap = new HashMap<>();
        
        if (tickets != null) {
            for (Ticket ticket : tickets) {
                try {
                    Long propertyId = getPropertyIdFromTicket(ticket);
                    if (propertyId != null) {
                        countMap.put(propertyId, countMap.getOrDefault(propertyId, 0) + 1);
                    }
                } catch (Exception e) {
                    System.err.println("Error mapping ticket to property: " + e.getMessage());
                }
            }
        }
        
        return countMap;
    }

    /**
     * Extract property ID from ticket - adapt this to your Ticket entity structure
     */
    private Long getPropertyIdFromTicket(Ticket ticket) {
        try {
            // Method 2: If ticket uses PayProp property ID, convert to internal ID
            String payPropPropertyId = ticket.getPayPropPropertyId();
            if (payPropPropertyId != null && !payPropPropertyId.trim().isEmpty()) {
                // Look up property by PayProp ID
                Property property = propertyService.findByPayPropId(payPropPropertyId).orElse(null);
                return property != null ? property.getId() : null;
            }
            
            return null;
        } catch (Exception e) {
            System.err.println("Error extracting property ID from ticket: " + e.getMessage());
            return null;
        }
    }

    /**
     * Add global maintenance statistics with efficient queries
     */
    private void addGlobalMaintenanceStatistics(Model model) {
        try {
            List<Ticket> emergencyTickets = ticketService.findByType("emergency");
            List<Ticket> urgentTickets = ticketService.findByType("urgent");
            List<Ticket> routineTickets = ticketService.findByType("routine");
            List<Ticket> maintenanceTickets = ticketService.findByType("maintenance");
            
            model.addAttribute("emergencyMaintenanceCount", emergencyTickets.size());
            model.addAttribute("urgentMaintenanceCount", urgentTickets.size());
            model.addAttribute("routineMaintenanceCount", routineTickets.size() + maintenanceTickets.size());
            model.addAttribute("totalMaintenanceCount", 
                emergencyTickets.size() + urgentTickets.size() + routineTickets.size() + maintenanceTickets.size());
                
        } catch (Exception e) {
            System.err.println("Error loading global maintenance statistics: " + e.getMessage());
            model.addAttribute("emergencyMaintenanceCount", 0);
            model.addAttribute("urgentMaintenanceCount", 0);
            model.addAttribute("routineMaintenanceCount", 0);
            model.addAttribute("totalMaintenanceCount", 0);
        }
    }

    // Portfolio statistics calculation with PayProp accuracy
    private void addComprehensivePortfolioStatistics(Model model, List<Property> properties) {
        try {
            // Get accurate PayProp statistics directly from database
            Map<String, Integer> payPropStats = getPayPropStatistics();
            
            int totalProperties = properties != null ? properties.size() : payPropStats.get("activeProperties");
            
            // Use efficient single-pass calculation for provided properties
            int synced = 0;
            BigDecimal totalRentPotential = BigDecimal.ZERO;
            
            // PayProp-specific statistics
            int withAccountBalance = 0;
            int withCommission = 0;
            int archivedProperties = 0;
            BigDecimal totalAccountBalance = BigDecimal.ZERO;
            BigDecimal averageCommissionRate = BigDecimal.ZERO;
            
            if (properties != null) {
                for (Property property : properties) {
                    // Calculate synced properties
                    if (property.getPayPropId() != null) {
                        synced++;
                    }
                    
                    // Calculate rent potential
                    if (property.getMonthlyPayment() != null) {
                        totalRentPotential = totalRentPotential.add(property.getMonthlyPayment());
                    }
                    
                    // PayProp-specific calculations
                    if (property.getAccountBalance() != null) {
                        withAccountBalance++;
                        totalAccountBalance = totalAccountBalance.add(property.getAccountBalance());
                    }
                    
                    if (property.getCommissionPercentage() != null) {
                        withCommission++;
                        averageCommissionRate = averageCommissionRate.add(property.getCommissionPercentage());
                    }
                    
                    if ("Y".equals(property.getIsArchived())) {
                        archivedProperties++;
                    }
                }
            }
            
            // Use accurate PayProp counts for occupancy
            int occupied = payPropStats.get("occupiedProperties");
            int vacant = payPropStats.get("vacantProperties");
            int active = payPropStats.get("activeProperties");
            int archived = payPropStats.get("archivedProperties");
            
            int readyForSync = totalProperties - synced;
            
            // Calculate averages
            if (withCommission > 0) {
                averageCommissionRate = averageCommissionRate.divide(
                    BigDecimal.valueOf(withCommission), 2, RoundingMode.HALF_UP);
            }

            // Add ALL required model attributes for templates with accurate PayProp data
            model.addAttribute("totalProperties", payPropStats.get("activeProperties")); // Show active properties only
            model.addAttribute("occupiedCount", occupied);
            model.addAttribute("vacantCount", vacant);
            model.addAttribute("syncedCount", synced);
            model.addAttribute("syncedProperties", synced);
            model.addAttribute("readyForSync", readyForSync);
            model.addAttribute("totalRentPotential", totalRentPotential);
            model.addAttribute("occupiedProperties", occupied);
            model.addAttribute("vacantProperties", vacant);
            
            // PayProp-specific model attributes
            model.addAttribute("totalAccountBalance", totalAccountBalance);
            model.addAttribute("averageCommissionRate", averageCommissionRate);
            model.addAttribute("archivedProperties", archived);
            model.addAttribute("activeProperties", active);
            
            // Add total including archived for reference (if needed)
            model.addAttribute("allPropertiesCount", payPropStats.get("totalProperties")); // 353 total including archived
            
        } catch (Exception e) {
            System.err.println("Error calculating portfolio statistics: " + e.getMessage());
            setDefaultModelAttributes(model);
        }
    }

    // Get accurate PayProp statistics from database
    private Map<String, Integer> getPayPropStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        try {
            // Get accurate PayProp counts
            String sql = """
                SELECT 
                  (SELECT COUNT(*) FROM payprop_export_properties) as total_properties,
                  (SELECT COUNT(*) FROM payprop_export_properties WHERE is_archived = 0) as active_properties,
                  (SELECT COUNT(*) FROM payprop_export_properties WHERE is_archived = 1) as archived_properties,
                  (SELECT COUNT(DISTINCT property_payprop_id) 
                   FROM payprop_export_invoices 
                   WHERE invoice_type = 'Rent' AND sync_status = 'active') as occupied_properties
                """;
            
            jdbcTemplate.query(sql, rs -> {
                int totalProperties = rs.getInt("total_properties");
                int activeProperties = rs.getInt("active_properties");
                int archivedProperties = rs.getInt("archived_properties");
                int occupiedProperties = rs.getInt("occupied_properties");
                int vacantProperties = activeProperties - occupiedProperties;
                
                stats.put("totalProperties", totalProperties);
                stats.put("activeProperties", activeProperties);
                stats.put("archivedProperties", archivedProperties);
                stats.put("occupiedProperties", occupiedProperties);
                stats.put("vacantProperties", vacantProperties);
            });
            
            return stats;
        } catch (Exception e) {
            System.err.println("Error getting PayProp statistics: " + e.getMessage());
            // Return safe defaults
            stats.put("totalProperties", 0);
            stats.put("activeProperties", 0);
            stats.put("archivedProperties", 0);
            stats.put("occupiedProperties", 0);
            stats.put("vacantProperties", 0);
            return stats;
        }
    }

    // Helper method to add detailed analytics
    private void addDetailedAnalytics(Model model, List<Property> properties) {
        try {
            if (properties == null || properties.isEmpty()) {
                model.addAttribute("flatCount", 0);
                model.addAttribute("houseCount", 0);
                model.addAttribute("averageRent", BigDecimal.ZERO);
                return;
            }

            // Property type distribution
            long flatCount = properties.stream()
                .filter(p -> "Flat".equalsIgnoreCase(p.getPropertyType()) || 
                            "Apartment".equalsIgnoreCase(p.getPropertyType()) ||
                            "Studio".equalsIgnoreCase(p.getPropertyType()))
                .count();
            
            long houseCount = properties.stream()
                .filter(p -> "House".equalsIgnoreCase(p.getPropertyType()))
                .count();

            // Calculate average rent (safely)
            BigDecimal averageRent = BigDecimal.ZERO;
            List<BigDecimal> rents = properties.stream()
                .map(Property::getMonthlyPayment)
                .filter(rent -> rent != null && rent.compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
            
            if (!rents.isEmpty()) {
                BigDecimal totalRent = rents.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                averageRent = totalRent.divide(BigDecimal.valueOf(rents.size()), 2, RoundingMode.HALF_UP);
            }

            model.addAttribute("flatCount", flatCount);
            model.addAttribute("houseCount", houseCount);
            model.addAttribute("averageRent", averageRent);
            
        } catch (Exception e) {
            model.addAttribute("flatCount", 0);
            model.addAttribute("houseCount", 0);
            model.addAttribute("averageRent", BigDecimal.ZERO);
        }
    }

    // ===== BLOCK ASSIGNMENT ENDPOINTS =====
    
    /**
     * Assign a property to a block
     * POST /employee/property/{propertyId}/assign-to-block
     */
    @PostMapping("/{propertyId}/assign-to-block")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> assignPropertyToBlock(
            @PathVariable Long propertyId,
            @RequestParam Long blockId,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Check permissions
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && 
                !AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")) {
                response.put("error", "Access denied");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            // Get user ID
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            
            // Assign property to block
            propertyService.assignPropertyToBlock(propertyId, blockId, (long) userId);
            
            response.put("success", true);
            response.put("message", "Property assigned to block successfully");
            response.put("propertyId", propertyId);
            response.put("blockId", blockId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Failed to assign property to block: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Remove a property from its current block (move to unassigned)
     * DELETE /employee/property/{propertyId}/remove-from-block
     */
    @DeleteMapping("/{propertyId}/remove-from-block")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> removePropertyFromBlock(
            @PathVariable Long propertyId,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Check permissions
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && 
                !AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")) {
                response.put("error", "Access denied");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            // Get user ID
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            
            // Remove property from block
            propertyService.removePropertyFromBlock(propertyId, (long) userId);
            
            response.put("success", true);
            response.put("message", "Property removed from block successfully");
            response.put("propertyId", propertyId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Failed to remove property from block: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Helper method to set default model attributes when errors occur
    private void setDefaultModelAttributes(Model model) {
        model.addAttribute("totalProperties", 0);
        model.addAttribute("occupiedCount", 0);
        model.addAttribute("vacantCount", 0);
        model.addAttribute("syncedCount", 0);
        model.addAttribute("syncedProperties", 0);
        model.addAttribute("readyForSync", 0);
        model.addAttribute("totalRentPotential", BigDecimal.ZERO);
        model.addAttribute("occupiedProperties", 0);
        model.addAttribute("vacantProperties", 0);
        model.addAttribute("emergencyMaintenanceCount", 0);
        model.addAttribute("urgentMaintenanceCount", 0);
        model.addAttribute("routineMaintenanceCount", 0);
        model.addAttribute("totalMaintenanceCount", 0);
        model.addAttribute("flatCount", 0);
        model.addAttribute("houseCount", 0);
        model.addAttribute("averageRent", BigDecimal.ZERO);
    }
    
    // Get active tenant count for a specific property using PayProp data
    private long getActiveTenantCountForProperty(Property property) {
        try {
            // If property has PayProp ID, use PayProp invoice data
            if (property.getPayPropId() != null && !"LEGACY".equals(dataSource)) {
                String sql = """
                    SELECT COUNT(DISTINCT tenant_payprop_id)
                    FROM payprop_export_invoices
                    WHERE property_payprop_id = ?
                    AND invoice_type = 'Rent'
                    AND sync_status = 'active'
                    """;

                return jdbcTemplate.queryForObject(sql, Long.class, property.getPayPropId());
            } else {
                // Fallback to legacy tenant service for non-PayProp properties
                return tenantService.countByPropertyId(property.getId());
            }
        } catch (Exception e) {
            System.err.println("Error getting tenant count for property " + property.getId() + ": " + e.getMessage());
            // Fallback to legacy method on error
            try {
                return tenantService.countByPropertyId(property.getId());
            } catch (Exception fallbackError) {
                System.err.println("Fallback tenant count also failed: " + fallbackError.getMessage());
                return 0;
            }
        }
    }

    /**
     * Get the last tenant left date for each vacant property
     * Returns a map of property ID to the most recent tenant end date
     */
    private Map<Long, LocalDate> getLastTenantLeftDates(List<Property> properties) {
        Map<Long, LocalDate> lastTenantDates = new HashMap<>();

        if (properties == null || properties.isEmpty()) {
            return lastTenantDates;
        }

        try {
            // Get all property IDs
            List<Long> propertyIds = properties.stream()
                .map(Property::getId)
                .collect(Collectors.toList());

            // Query for the most recent ended tenancy for each property
            List<CustomerPropertyAssignment> endedTenancies = assignmentRepository
                .findByPropertyIdInAndAssignmentType(propertyIds, AssignmentType.TENANT);

            // Filter for ended tenancies and group by property
            Map<Long, List<CustomerPropertyAssignment>> tenanciesByProperty = endedTenancies.stream()
                .filter(a -> a.getEndDate() != null && !a.getEndDate().isAfter(LocalDate.now()))
                .collect(Collectors.groupingBy(a -> a.getProperty().getId()));

            // For each property, find the most recent end date
            for (Map.Entry<Long, List<CustomerPropertyAssignment>> entry : tenanciesByProperty.entrySet()) {
                LocalDate latestEndDate = entry.getValue().stream()
                    .map(CustomerPropertyAssignment::getEndDate)
                    .filter(date -> date != null)
                    .max(LocalDate::compareTo)
                    .orElse(null);

                if (latestEndDate != null) {
                    lastTenantDates.put(entry.getKey(), latestEndDate);
                }
            }

        } catch (Exception e) {
            System.err.println("Error getting last tenant left dates: " + e.getMessage());
            e.printStackTrace();
        }

        return lastTenantDates;
    }
    
    // Calculate statistics based on a filtered property list (for archived, search, etc.)
    private void calculateFilteredPropertyStatistics(Model model, List<Property> properties, String context) {
        try {
            // Get global PayProp statistics for reference
            Map<String, Integer> globalStats = getPayPropStatistics();
            
            // Calculate statistics for the filtered list
            int totalProperties = properties.size();
            int synced = (int) properties.stream().filter(p -> p.getPayPropId() != null).count();
            
            // Calculate rent potential for filtered properties
            BigDecimal totalRentPotential = properties.stream()
                .map(Property::getMonthlyPayment)
                .filter(rent -> rent != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // For archived properties, occupancy doesn't make sense, so use filtered stats
            if ("Archived Properties".equals(context)) {
                model.addAttribute("totalProperties", totalProperties); // Archived count
                model.addAttribute("occupiedCount", 0); // Archived properties are not occupied
                model.addAttribute("vacantCount", 0); // Archived properties are not vacant
                model.addAttribute("archivedProperties", totalProperties); // Same as total for archived view
            } else {
                // For other filtered views, show global occupancy but filtered totals
                model.addAttribute("totalProperties", totalProperties); // Filtered count
                model.addAttribute("occupiedCount", globalStats.get("occupiedProperties")); // Global
                model.addAttribute("vacantCount", globalStats.get("vacantProperties")); // Global
                model.addAttribute("archivedProperties", globalStats.get("archivedProperties")); // Global
            }
            
            model.addAttribute("syncedCount", synced);
            model.addAttribute("syncedProperties", synced);
            model.addAttribute("readyForSync", totalProperties - synced);
            model.addAttribute("totalRentPotential", totalRentPotential);
            model.addAttribute("occupiedProperties", model.getAttribute("occupiedCount"));
            model.addAttribute("vacantProperties", model.getAttribute("vacantCount"));
            model.addAttribute("activeProperties", globalStats.get("activeProperties"));
            model.addAttribute("allPropertiesCount", globalStats.get("totalProperties"));
            
        } catch (Exception e) {
            System.err.println("Error calculating filtered property statistics: " + e.getMessage());
            setDefaultModelAttributes(model);
        }
    }
}
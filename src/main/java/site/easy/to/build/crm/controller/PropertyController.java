package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.entity.Ticket;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.property.TenantService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.service.ticket.TicketService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.util.AuthorizationUtil;
import site.easy.to.build.crm.entity.Role;


import java.math.BigDecimal;
import java.math.RoundingMode;
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

    @Autowired
    public PropertyController(PropertyService propertyService,
                            TenantService tenantService,
                            UserService userService, 
                            TicketService ticketService,
                            AuthenticationUtils authenticationUtils) {
        this.propertyService = propertyService;
        this.tenantService = tenantService;
        this.userService = userService;
        this.ticketService = ticketService;
        this.authenticationUtils = authenticationUtils;
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
                    properties = propertyService.findAll();
                } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER")) {
                    properties = propertyService.findByPropertyOwnerId(Long.valueOf(userId));
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
        } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER")) {
            properties = allVacantProperties.stream()
                .filter(p -> p.getPropertyOwnerId() != null && p.getPropertyOwnerId().equals(Long.valueOf(userId)))
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
        
        addComprehensivePortfolioStatistics(model, properties);
        model.addAttribute("properties", properties);
        model.addAttribute("lostRentPotential", lostRentPotential);
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
                properties = propertyService.findAll();
            } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER")) {
                properties = propertyService.findByPropertyOwnerId(Long.valueOf(userId));
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
        
        addComprehensivePortfolioStatistics(model, properties);
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
        addComprehensivePortfolioStatistics(model, properties);
        model.addAttribute("properties", properties);
        model.addAttribute("pageTitle", "Properties Ready for PayProp Sync");
        return "property/all-properties";
    }

    // ================================
    // PROPERTY DETAIL AND MANAGEMENT
    // ================================

    @GetMapping("/{id}")
    public String showPropertyDetail(@PathVariable("id") Long id, Model model, Authentication authentication) {
        Property property = propertyService.findById(id);
        if (property == null) {
            return "error/not-found";
        }

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

        // Add tenant count for this property
        long tenantCount = tenantService.countByPropertyId(property.getId());
        model.addAttribute("tenantCount", tenantCount);

        // Add maintenance statistics and recent tickets
        try {
            List<Ticket> emergencyTickets = ticketService.getTicketsByPropertyIdAndType(id, "emergency");
            List<Ticket> urgentTickets = ticketService.getTicketsByPropertyIdAndType(id, "urgent");
            List<Ticket> routineTickets = ticketService.getTicketsByPropertyIdAndType(id, "routine");
            List<Ticket> maintenanceTickets = ticketService.getTicketsByPropertyIdAndType(id, "maintenance");
            List<Ticket> allPropertyTickets = ticketService.getTicketsByPropertyId(id);
            
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

        model.addAttribute("property", new Property());
        return "property/create-property";
    }

    @PostMapping("/create-property")
    public String createProperty(@ModelAttribute("property") @Validated Property property, 
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
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Property created successfully! It will be synced to PayProp on June 17, 2025.");
            
        } catch (Exception e) {
            model.addAttribute("error", "Failed to create property: " + e.getMessage());
            return "property/create-property";
        }

        return "redirect:/employee/property/all-properties";
    }

    @GetMapping("/update/{id}")
    public String showUpdatePropertyForm(@PathVariable("id") Long id, Model model, Authentication authentication) {
        try {
            Property property = propertyService.findById(id);
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
    public String deleteProperty(@PathVariable("id") Long id, Authentication authentication, 
                                RedirectAttributes redirectAttributes) {
        Property property = propertyService.findById(id);
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
        
        addComprehensivePortfolioStatistics(model, properties);
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
    public String archiveProperty(@PathVariable("id") Long id, Authentication authentication,
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

        propertyService.archiveProperty(id);
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
    public Property getPropertyApi(@PathVariable("id") Long id) {
        Property property = propertyService.findById(id);
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
        } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER")) {
            return propertyService.findByPropertyOwnerId(Long.valueOf(userId));
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

    @GetMapping("/{id}/maintenance-summary")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMaintenanceSummary(@PathVariable("id") Long id, Authentication authentication) {
        try {
            Property property = propertyService.findById(id);
            if (property == null) {
                return ResponseEntity.notFound().build();
            }

            // Check authorization
            int userId = authenticationUtils.getLoggedInUserId(authentication);
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
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Map<String, Object> response = new HashMap<>();
            
            try {
                List<Ticket> emergencyTickets = ticketService.getTicketsByPropertyIdAndType(id, "emergency");
                List<Ticket> urgentTickets = ticketService.getTicketsByPropertyIdAndType(id, "urgent");
                List<Ticket> routineTickets = ticketService.getTicketsByPropertyIdAndType(id, "routine");
                List<Ticket> maintenanceTickets = ticketService.getTicketsByPropertyIdAndType(id, "maintenance");
                
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

    // Portfolio statistics calculation
    private void addComprehensivePortfolioStatistics(Model model, List<Property> properties) {
        try {
            int totalProperties = properties != null ? properties.size() : 0;
            
            // Use efficient single-pass calculation
            int occupied = 0;
            int vacant = 0;
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
                    // Calculate occupancy
                    if (property.isOccupied() != null && property.isOccupied()) {
                        occupied++;
                    } else {
                        vacant++;
                    }
                    
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
            
            int readyForSync = totalProperties - synced;
            
            // Calculate averages
            if (withCommission > 0) {
                averageCommissionRate = averageCommissionRate.divide(
                    BigDecimal.valueOf(withCommission), 2, RoundingMode.HALF_UP);
            }

            // Add ALL required model attributes for templates
            model.addAttribute("totalProperties", totalProperties);
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
            model.addAttribute("archivedProperties", archivedProperties);
            model.addAttribute("activeProperties", totalProperties - archivedProperties);
            
        } catch (Exception e) {
            System.err.println("Error calculating portfolio statistics: " + e.getMessage());
            setDefaultModelAttributes(model);
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
}
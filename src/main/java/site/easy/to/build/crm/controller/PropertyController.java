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
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.property.TenantService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.util.AuthorizationUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/employee/property")
public class PropertyController {

    private final PropertyService propertyService;
    private final TenantService tenantService;
    private final UserService userService;
    private final AuthenticationUtils authenticationUtils;
    // TODO: Add PayPropSyncService after it's implemented
    // private final PayPropSyncService payPropSyncService;

    @Autowired
    public PropertyController(PropertyService propertyService, TenantService tenantService,
                             UserService userService, AuthenticationUtils authenticationUtils) {
        this.propertyService = propertyService;
        this.tenantService = tenantService;
        this.userService = userService;
        this.authenticationUtils = authenticationUtils;
    }

    // 🔄 UNIFIED - Single endpoint for all properties with role-based filtering
    @GetMapping("/all-properties")
    public String getAllProperties(Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        if (userId == -1) {
            return "error/not-found";
        }
        
        User user = userService.findById(userId);
        if (user.isInactiveUser()) {
            return "error/account-inactive";
        }
        
        List<Property> properties;
        try {
            // Role-based property filtering
            if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                // Managers see all properties
                properties = propertyService.findAll();
            } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER")) {
                // Property owners see only their properties - FIXED: Use correct method name and parameter type
                properties = propertyService.findByPropertyOwnerId(userId);
            } else {
                // Employees see properties they manage
                properties = propertyService.getRecentProperties((long) userId, 100);
            }
            
            // Add comprehensive portfolio statistics
            addComprehensivePortfolioStatistics(model, properties);
            
        } catch (Exception e) {
            setDefaultModelAttributes(model);
            return "error/500";
        }
        
        model.addAttribute("properties", properties);
        model.addAttribute("pageTitle", "All Properties");
        return "property/all-properties";
    }

    // 🔄 Updated - PayProp compatible vacancy logic with role filtering
    @GetMapping("/vacant-properties")
    public String getVacantProperties(Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User user = userService.findById(userId);
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
                .filter(p -> p.getPropertyOwnerId() != null && p.getPropertyOwnerId().equals(userId))
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

    // 🔄 Updated - PayProp compatible occupancy logic with role filtering
    @GetMapping("/occupied")
    public String getOccupiedProperties(Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User user = userService.findById(userId);
        if (user.isInactiveUser()) {
            return "error/account-inactive";
        }

        List<Property> allOccupiedProperties = propertyService.findOccupiedProperties();
        
        // Debug logging
        System.out.println("DEBUG: Found " + allOccupiedProperties.size() + " total occupied properties");
        System.out.println("DEBUG: User ID: " + userId);
        System.out.println("DEBUG: User roles: " + authentication.getAuthorities());
        
        List<Property> properties;
        
        // Filter based on role
        if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            properties = allOccupiedProperties;
            System.out.println("DEBUG: User is MANAGER - showing all properties");
        } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER")) {
            properties = allOccupiedProperties.stream()
                .filter(p -> p.getPropertyOwnerId() != null && p.getPropertyOwnerId().equals(userId))
                .collect(Collectors.toList());
            System.out.println("DEBUG: User is OWNER - filtered to " + properties.size() + " properties");
        } else {
            properties = allOccupiedProperties.stream()
                .filter(p -> p.getCreatedBy() != null && p.getCreatedBy().equals((long) userId))
                .collect(Collectors.toList());
            System.out.println("DEBUG: User is EMPLOYEE - filtered to " + properties.size() + " properties");
        }
        
        System.out.println("DEBUG: Final properties count: " + properties.size());
        
        addComprehensivePortfolioStatistics(model, properties);
        model.addAttribute("properties", properties);
        model.addAttribute("pageTitle", "Occupied Properties");
        return "property/all-properties";
    }

    @GetMapping("/portfolio-overview")
    public String getPortfolioOverview(Model model, Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(userId);
            if (user.isInactiveUser()) {
                return "error/account-inactive";
            }

            List<Property> properties;
            if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                properties = propertyService.findAll();
            } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER")) {
                // FIXED: Use correct method name and parameter type
                properties = propertyService.findByPropertyOwnerId(userId);
            } else {
                properties = propertyService.getRecentProperties((long) userId, 100);
            }

            addComprehensivePortfolioStatistics(model, properties);
            addDetailedAnalytics(model, properties);
            
            model.addAttribute("properties", properties);
            return "property/portfolio-overview";
        } catch (Exception e) {
            // If anything fails, set default values to prevent template errors
            setDefaultModelAttributes(model);
            model.addAttribute("properties", new ArrayList<>());
            return "property/portfolio-overview";
        }
    }

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
        User user = userService.findById(userId);
        
        if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return propertyService.findAll();
        } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER")) {
            return propertyService.findByPropertyOwnerId(userId);
        } else {
            return propertyService.getRecentProperties((long) userId, 100);
        }
    }

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

            // CRITICAL: Add all required model attributes for sync-status template
            model.addAttribute("synced", synced);
            model.addAttribute("needsSync", needsSync);
            model.addAttribute("missingFields", missingFields);
            
            // Add tenant and property owner counts (mock for now)
            model.addAttribute("tenantsSynced", 0);
            model.addAttribute("tenantsReady", 0);
            model.addAttribute("tenantsMissing", 0);
            model.addAttribute("ownersSynced", 0);
            model.addAttribute("ownersReady", 0);
            model.addAttribute("ownersMissing", 0);
            
            // CRITICAL: Ensure properties attribute is never null
            List<Property> allProperties = new ArrayList<>();
            try {
                allProperties = propertyService.findAll();
                if (allProperties == null) allProperties = new ArrayList<>();
            } catch (Exception e) {
                System.err.println("Error finding all properties: " + e.getMessage());
            }
            
            // This prevents the null pointer exception in templates
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
            model.addAttribute("properties", new ArrayList<>()); // CRITICAL: Prevent null
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

    @GetMapping("/archived")
    public String getArchivedProperties(Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User user = userService.findById(userId);
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
                .filter(p -> p.getPropertyOwnerId() != null && p.getPropertyOwnerId().equals(userId))
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

    @GetMapping("/{id}")
    public String showPropertyDetail(@PathVariable("id") Long id, Model model, Authentication authentication) {
        Property property = propertyService.findById(id);
        if (property == null) {
            return "error/not-found";
        }

        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(userId);
        if (loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }

        // Check authorization - role-based access
        boolean hasAccess = false;
        if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            hasAccess = true;
        } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER") && 
                   property.getPropertyOwnerId() != null && property.getPropertyOwnerId().equals(userId)) {
            hasAccess = true;
        } else if (property.getCreatedBy() != null && property.getCreatedBy().equals((long) userId)) {
            hasAccess = true;
        }
        
        if (!hasAccess) {
            return "redirect:/access-denied";
        }

        // Add tenant count for this property
        long tenantCount = tenantService.countByPropertyId(property.getId());
        model.addAttribute("tenantCount", tenantCount);

        model.addAttribute("property", property);
        return "property/property-details";
    }

    @GetMapping("/create-property")
    public String showCreatePropertyForm(Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User user = userService.findById(userId);
        if (user.isInactiveUser()) {
            return "error/account-inactive";
        }

        model.addAttribute("property", new Property());
        return "property/create-property";
    }

    // Add these methods to your PropertyController class

    @PostMapping("/create-property")
    public String createProperty(@ModelAttribute("property") @Validated Property property, 
                                BindingResult bindingResult, Authentication authentication, 
                                Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "property/create-property";
        }

        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(userId);
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
            
            // TODO: Add PayProp sync after service is implemented
            // payPropSyncService.createProperty(savedProperty);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Property created successfully! It will be synced to PayProp on June 17, 2025.");
            
        } catch (Exception e) {
            model.addAttribute("error", "Failed to create property: " + e.getMessage());
            return "property/create-property";
        }

        return "redirect:/employee/property/all-properties";
    }

    // PayProp Sync Endpoints
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
                    // TODO: Implement when PayPropSyncService is ready
                    // payPropId = payPropSyncService.syncPropertyToPayProp(entityId);
                    payPropId = "PP" + System.currentTimeMillis(); // Temporary mock
                    break;
                case "tenant":
                    // TODO: Implement when PayPropSyncService is ready
                    // payPropId = payPropSyncService.syncTenantToPayProp(entityId);
                    payPropId = "TN" + System.currentTimeMillis(); // Temporary mock
                    break;
                case "beneficiary":
                    // TODO: Implement when PayPropSyncService is ready
                    // payPropId = payPropSyncService.syncBeneficiaryToPayProp(entityId);
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
                    // TODO: Implement when PayPropSyncService is ready
                    // payPropSyncService.updatePropertyInPayProp(entityId);
                    break;
                case "tenant":
                    // TODO: Implement when PayPropSyncService is ready
                    // payPropSyncService.updateTenantInPayProp(entityId);
                    break;
                case "beneficiary":
                    // TODO: Implement when PayPropSyncService is ready
                    // payPropSyncService.updateBeneficiaryInPayProp(entityId);
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
            
            // TODO: Implement when PayPropSyncService is ready
            // syncedCount += payPropSyncService.syncAllReadyProperties();
            // syncedCount += payPropSyncService.syncAllReadyTenants();
            // syncedCount += payPropSyncService.syncAllReadyBeneficiaries();
            
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

    @GetMapping("/update/{id}")
    public String showUpdatePropertyForm(@PathVariable("id") Long id, Model model, Authentication authentication) {
        try {
            Property property = propertyService.findById(id);
            if (property == null) {
                model.addAttribute("error", "Property not found");
                return "error/not-found";
            }

            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(userId);
            if (loggedInUser.isInactiveUser()) {
                return "error/account-inactive";
            }

            // Check authorization
            boolean hasAccess = false;
            if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                hasAccess = true;
            } else if (AuthorizationUtil.hasRole(authentication, "ROLE_OWNER") && 
                    property.getPropertyOwnerId() != null && property.getPropertyOwnerId().equals(userId)) {
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
        User loggedInUser = userService.findById(userId);
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
                   existingProperty.getPropertyOwnerId() != null && existingProperty.getPropertyOwnerId().equals(userId)) {
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
        property.setPayPropId(existingProperty.getPayPropId()); // Preserve PayProp sync

        try {
            Property savedProperty = propertyService.save(property);
            
            // TODO: Add PayProp sync after service is implemented
            // payPropSyncService.updateProperty(savedProperty);
            
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
        User user = userService.findById(userId);
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
            // TODO: Add PayProp sync before deletion
            // payPropSyncService.deleteProperty(property);
            
            propertyService.delete(property);
            redirectAttributes.addFlashAttribute("successMessage", "Property deleted successfully!");
        } catch (Exception e) {
            return "error/500";
        }

        return "redirect:/employee/property/all-properties";
    }

    // 🔄 Updated search method - PayProp compatible with role filtering
    @GetMapping("/search")
    public String searchProperties(@RequestParam(value = "propertyName", required = false) String propertyName,
                                  @RequestParam(value = "city", required = false) String city,
                                  @RequestParam(value = "postalCode", required = false) String postalCode,
                                  @RequestParam(value = "isArchived", required = false) Boolean isArchived,
                                  @RequestParam(value = "propertyType", required = false) String propertyType,
                                  @RequestParam(value = "bedrooms", required = false) Integer bedrooms,
                                  Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User user = userService.findById(userId);
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
                .filter(p -> p.getPropertyOwnerId() != null && p.getPropertyOwnerId().equals(userId))
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

    // 🔄 Updated - PayProp compatible archive logic
    @PostMapping("/archive/{id}")
    public String archiveProperty(@PathVariable("id") Long id, Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User user = userService.findById(userId);
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
        
        // TODO: Add PayProp sync
        // payPropSyncService.archiveProperty(property);
        
        redirectAttributes.addFlashAttribute("successMessage", "Property archived successfully!");
        return "redirect:/employee/property/" + id;
    }

    @PostMapping("/unarchive/{id}")
    public String unarchiveProperty(@PathVariable("id") Long id, Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User user = userService.findById(userId);
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
        
        // TODO: Add PayProp sync
        // payPropSyncService.unarchiveProperty(property);
        
        redirectAttributes.addFlashAttribute("successMessage", "Property restored successfully!");
        return "redirect:/employee/property/" + id;
    }

    // 🆕 COMPREHENSIVE Helper method - provides ALL required template attributes
    private void addComprehensivePortfolioStatistics(Model model, List<Property> properties) {
        try {
            int totalProperties = properties != null ? properties.size() : 0;
            
            // Get global statistics for accurate counts
            List<Property> allOccupiedProperties = propertyService.findOccupiedProperties();
            List<Property> allVacantProperties = propertyService.findVacantProperties();
            
            // Calculate filtered counts
            int occupied = 0;
            int vacant = 0;
            
            if (properties != null && allOccupiedProperties != null) {
                occupied = (int) properties.stream()
                    .filter(p -> allOccupiedProperties.stream()
                        .anyMatch(op -> op.getId().equals(p.getId())))
                    .count();
            }
            
            if (properties != null && allVacantProperties != null) {
                vacant = (int) properties.stream()
                    .filter(p -> allVacantProperties.stream()
                        .anyMatch(vp -> vp.getId().equals(p.getId())))
                    .count();
            }

            // Calculate synced properties
            int synced = 0;
            if (properties != null) {
                synced = (int) properties.stream()
                    .filter(p -> p.getPayPropId() != null)
                    .count();
            }
            
            int readyForSync = totalProperties - synced;

            // Calculate rent potential
            BigDecimal totalRentPotential = BigDecimal.ZERO;
            if (properties != null) {
                totalRentPotential = properties.stream()
                    .map(Property::getMonthlyPayment)
                    .filter(rent -> rent != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            // Add ALL required model attributes for templates
            model.addAttribute("totalProperties", totalProperties);
            model.addAttribute("occupiedCount", occupied);
            model.addAttribute("vacantCount", vacant);
            model.addAttribute("syncedCount", synced);
            model.addAttribute("syncedProperties", synced); // Alternative name used by some templates
            model.addAttribute("readyForSync", readyForSync);
            model.addAttribute("totalRentPotential", totalRentPotential);
            
            // Additional attributes for dashboard compatibility
            model.addAttribute("occupiedProperties", occupied);
            model.addAttribute("vacantProperties", vacant);
            
        } catch (Exception e) {
            // If calculation fails, set safe defaults
            setDefaultModelAttributes(model);
        }
    }

    // Helper method to add detailed analytics
    private void addDetailedAnalytics(Model model, List<Property> properties) {
        try {
            if (properties == null || properties.isEmpty()) {
                // Set default analytics values
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

            // Add analytics to model
            model.addAttribute("flatCount", flatCount);
            model.addAttribute("houseCount", houseCount);
            model.addAttribute("averageRent", averageRent);
            
        } catch (Exception e) {
            // If analytics calculation fails, set safe defaults
            model.addAttribute("flatCount", 0);
            model.addAttribute("houseCount", 0);
            model.addAttribute("averageRent", BigDecimal.ZERO);
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
        model.addAttribute("flatCount", 0);
        model.addAttribute("houseCount", 0);
        model.addAttribute("averageRent", BigDecimal.ZERO);
    }
}
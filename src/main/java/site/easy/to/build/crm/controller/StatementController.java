// StatementController.java - Complete fix for statement generation with proper authentication

package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.sheets.GoogleSheetsStatementService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.service.user.OAuthUserService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

@Controller
@RequestMapping("/statements")
public class StatementController {

    private final GoogleSheetsStatementService statementService;
    private final CustomerService customerService;
    private final PropertyService propertyService;
    private final OAuthUserService oAuthUserService;
    private final AuthenticationUtils authenticationUtils;

    @Autowired
    public StatementController(GoogleSheetsStatementService statementService,
                             CustomerService customerService,
                             PropertyService propertyService,
                             OAuthUserService oAuthUserService,
                             AuthenticationUtils authenticationUtils) {
        this.statementService = statementService;
        this.customerService = customerService;
        this.propertyService = propertyService;
        this.oAuthUserService = oAuthUserService;
        this.authenticationUtils = authenticationUtils;
    }

    /**
     * Show statement generation page - FIXED to handle authentication properly
     */
    @GetMapping
    public String showStatements(Model model, Authentication authentication) {
        System.out.println("=== DEBUG: StatementController.showStatements ===");
        
        // Debug: Check total customers in database
        try {
            List<Customer> allCustomers = customerService.findAll();
            System.out.println("🔍 Total customers in database: " + allCustomers.size());
            
            // Count by type
            long propertyOwnersCount = allCustomers.stream()
                .filter(c -> "PROPERTY_OWNER".equals(c.getCustomerType()) || Boolean.TRUE.equals(c.getIsPropertyOwner()))
                .count();
            long tenantsCount = allCustomers.stream()
                .filter(c -> "TENANT".equals(c.getCustomerType()) || Boolean.TRUE.equals(c.getIsTenant()))
                .count();
                
            System.out.println("   Property owners by query criteria: " + propertyOwnersCount);
            System.out.println("   Tenants by query criteria: " + tenantsCount);
            
            // Show first few customers for debugging
            System.out.println("   First 5 customers:");
            allCustomers.stream().limit(5).forEach(c -> 
                System.out.println("   - ID: " + c.getCustomerId() + ", Name: " + c.getName() + 
                                 ", Type: " + c.getCustomerType() + ", IsOwner: " + c.getIsPropertyOwner() + 
                                 ", IsTenant: " + c.getIsTenant()));
        } catch (Exception e) {
            System.out.println("Error checking customers: " + e.getMessage());
        }
        
        // Get OAuth user for Google Sheets access
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        
        if (oAuthUser == null || oAuthUser.getAccessToken() == null) {
            model.addAttribute("error", "Google account not connected. Please connect your Google account first.");
            model.addAttribute("googleAuthRequired", true);
            return "statements/generate-statement";
        }
        
        // Determine what statements the user can generate
        Customer currentCustomer = getCurrentCustomerFromAuth(authentication);
        
        if (currentCustomer != null) {
            System.out.println("DEBUG: Found current customer: " + currentCustomer.getCustomerId() + 
                             " - " + currentCustomer.getName() + " (" + currentCustomer.getCustomerType() + ")");
            
            if (currentCustomer.getIsPropertyOwner() != null && currentCustomer.getIsPropertyOwner()) {
                // Property owner can only see their own statements
                model.addAttribute("propertyOwners", Arrays.asList(currentCustomer));
                model.addAttribute("isOwnStatements", true);
                model.addAttribute("currentCustomer", currentCustomer);
                model.addAttribute("viewMode", "owner");
            } else if (currentCustomer.getIsTenant() != null && currentCustomer.getIsTenant()) {
                // Tenant can only see their own statements
                model.addAttribute("tenants", Arrays.asList(currentCustomer));
                model.addAttribute("isOwnStatements", true);
                model.addAttribute("currentCustomer", currentCustomer);
                model.addAttribute("viewMode", "tenant");
            } else {
                // Regular customer - shouldn't see statements
                model.addAttribute("error", "You don't have permission to view statements.");
                return "error/403";
            }
        } else {
            // Admin/Employee viewing all customers
            System.out.println("DEBUG: No customer found for current user - showing admin view");
            
            // Check if this is an admin/employee
            if (isAdminOrEmployee(authentication)) {
                List<Customer> propertyOwners = customerService.findPropertyOwners();
                List<Customer> tenants = customerService.findTenants();
                
                System.out.println("🔍 StatementController - Property Owners Query Result:");
                System.out.println("   Found " + propertyOwners.size() + " property owners");
                for (Customer owner : propertyOwners) {
                    System.out.println("   - ID: " + owner.getCustomerId() + ", Name: " + owner.getName() + 
                                     ", Type: " + owner.getCustomerType() + ", IsPropertyOwner: " + owner.getIsPropertyOwner());
                }
                
                System.out.println("🔍 StatementController - Tenants Query Result:");
                System.out.println("   Found " + tenants.size() + " tenants");
                for (Customer tenant : tenants) {
                    System.out.println("   - ID: " + tenant.getCustomerId() + ", Name: " + tenant.getName() + 
                                     ", Type: " + tenant.getCustomerType() + ", IsTenant: " + tenant.getIsTenant());
                }
                
                model.addAttribute("propertyOwners", propertyOwners);
                model.addAttribute("tenants", tenants);
                model.addAttribute("isOwnStatements", false);
                model.addAttribute("viewMode", "admin");
            } else {
                model.addAttribute("error", "No customer account found for your login. Please contact support.");
                return "error/403";
            }
        }
        
        // Set default date range (current month)
        LocalDate now = LocalDate.now();
        LocalDate startOfMonth = now.withDayOfMonth(1);
        LocalDate endOfMonth = now.withDayOfMonth(now.lengthOfMonth());
        
        model.addAttribute("defaultFromDate", startOfMonth);
        model.addAttribute("defaultToDate", endOfMonth);
        model.addAttribute("hasGoogleAuth", true);
        
        return "statements/generate-statement";
    }

    /**
     * Generate property owner statement - FIXED with proper authorization
     */
    @PostMapping("/property-owner")
    public String generatePropertyOwnerStatement(
            @RequestParam("propertyOwnerId") Integer propertyOwnerId,
            @RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam("toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Check authorization
            Customer currentCustomer = getCurrentCustomerFromAuth(authentication);
            
            // If current user is a property owner, they can only generate their own statements
            if (currentCustomer != null && currentCustomer.getIsPropertyOwner() != null && currentCustomer.getIsPropertyOwner()) {
                if (!currentCustomer.getCustomerId().equals(propertyOwnerId.longValue())) {
                    redirectAttributes.addFlashAttribute("error", "You can only generate statements for your own account.");
                    return "redirect:/statements";
                }
            } else if (!isAdminOrEmployee(authentication)) {
                // Not a property owner and not admin/employee
                redirectAttributes.addFlashAttribute("error", "Access denied.");
                return "redirect:/statements";
            }
            
            // Get OAuth user for Google Sheets
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            
            if (oAuthUser == null || oAuthUser.getAccessToken() == null) {
                redirectAttributes.addFlashAttribute("error", 
                    "Google account not connected. Please connect your Google account first.");
                return "redirect:/statements";
            }
            
            // Get property owner
            Customer propertyOwner = customerService.findByCustomerId(propertyOwnerId.longValue());
            if (propertyOwner == null) {
                redirectAttributes.addFlashAttribute("error", "Property owner not found.");
                return "redirect:/statements";
            }
            
            // Generate statement
            String spreadsheetId = statementService.createPropertyOwnerStatement(
                oAuthUser, propertyOwner, fromDate, toDate);
            
            // Success message with link to Google Sheets
            String sheetsUrl = "https://docs.google.com/spreadsheets/d/" + spreadsheetId;
            redirectAttributes.addFlashAttribute("success", 
                "Statement generated successfully! <a href='" + sheetsUrl + "' target='_blank'>View in Google Sheets</a>");
            
            return "redirect:/statements";
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", 
                "Error generating statement: " + e.getMessage());
            return "redirect:/statements";
        }
    }

    /**
     * Generate tenant statement - FIXED with proper authorization
     */
    @PostMapping("/tenant")
    public String generateTenantStatement(
            @RequestParam("tenantId") Integer tenantId,
            @RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam("toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Check authorization
            Customer currentCustomer = getCurrentCustomerFromAuth(authentication);
            
            // If current user is a tenant, they can only generate their own statements
            if (currentCustomer != null && currentCustomer.getIsTenant() != null && currentCustomer.getIsTenant()) {
                if (!currentCustomer.getCustomerId().equals(tenantId.longValue())) {
                    redirectAttributes.addFlashAttribute("error", "You can only generate statements for your own tenancy.");
                    return "redirect:/statements";
                }
            } else if (!isAdminOrEmployee(authentication)) {
                // Not a tenant and not admin/employee
                redirectAttributes.addFlashAttribute("error", "Access denied.");
                return "redirect:/statements";
            }
            
            // Get OAuth user for Google Sheets
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            
            if (oAuthUser == null || oAuthUser.getAccessToken() == null) {
                redirectAttributes.addFlashAttribute("error", 
                    "Google account not connected. Please connect your Google account first.");
                return "redirect:/statements";
            }
            
            // Get tenant
            Customer tenant = customerService.findByCustomerId(tenantId.longValue());
            if (tenant == null) {
                redirectAttributes.addFlashAttribute("error", "Tenant not found.");
                return "redirect:/statements";
            }
            
            // Generate statement
            String spreadsheetId = statementService.createTenantStatement(
                oAuthUser, tenant, fromDate, toDate);
            
            // Success message with link to Google Sheets
            String sheetsUrl = "https://docs.google.com/spreadsheets/d/" + spreadsheetId;
            redirectAttributes.addFlashAttribute("success", 
                "Tenant statement generated successfully! <a href='" + sheetsUrl + "' target='_blank'>View in Google Sheets</a>");
            
            return "redirect:/statements";
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", 
                "Error generating tenant statement: " + e.getMessage());
            return "redirect:/statements";
        }
    }

    /**
     * Generate portfolio statement
     */
    @PostMapping("/portfolio")
    public String generatePortfolioStatement(
            @RequestParam("propertyOwnerId") Integer propertyOwnerId,
            @RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam("toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Check authorization (same as property owner statement)
            Customer currentCustomer = getCurrentCustomerFromAuth(authentication);
            
            if (currentCustomer != null && currentCustomer.getIsPropertyOwner() != null && currentCustomer.getIsPropertyOwner()) {
                if (!currentCustomer.getCustomerId().equals(propertyOwnerId.longValue())) {
                    redirectAttributes.addFlashAttribute("error", "You can only generate portfolio statements for your own properties.");
                    return "redirect:/statements";
                }
            } else if (!isAdminOrEmployee(authentication)) {
                redirectAttributes.addFlashAttribute("error", "Access denied.");
                return "redirect:/statements";
            }
            
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            
            if (oAuthUser == null || oAuthUser.getAccessToken() == null) {
                redirectAttributes.addFlashAttribute("error", 
                    "Google account not connected. Please connect your Google account first.");
                return "redirect:/statements";
            }
            
            Customer propertyOwner = customerService.findByCustomerId(propertyOwnerId.longValue());
            if (propertyOwner == null) {
                redirectAttributes.addFlashAttribute("error", "Property owner not found.");
                return "redirect:/statements";
            }
            
            String spreadsheetId = statementService.createPortfolioStatement(
                oAuthUser, propertyOwner, fromDate, toDate);
            
            String sheetsUrl = "https://docs.google.com/spreadsheets/d/" + spreadsheetId;
            redirectAttributes.addFlashAttribute("success", 
                "Portfolio statement generated successfully! <a href='" + sheetsUrl + "' target='_blank'>View in Google Sheets</a>");
            
            return "redirect:/statements";
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", 
                "Error generating portfolio statement: " + e.getMessage());
            return "redirect:/statements";
        }
    }

    // ===== HELPER METHODS =====

    /**
     * Get the current customer based on authentication
     * This handles the case where all customers are linked to user_id 54
     */
    private Customer getCurrentCustomerFromAuth(Authentication authentication) {
        try {
            // For OAuth users, check by email first
            if (authentication.getPrincipal() instanceof OAuth2User) {
                OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
                String email = oauth2User.getAttribute("email");
                
                System.out.println("DEBUG: OAuth user email: " + email);
                
                if (email != null) {
                    // Try to find customer by email
                    Customer customer = customerService.findByEmail(email);
                    if (customer != null) {
                        System.out.println("DEBUG: Found customer by email: " + customer.getCustomerId());
                        return customer;
                    }
                    
                    // If email is management@propsk.com, this is likely an admin
                    if ("management@propsk.com".equals(email)) {
                        System.out.println("DEBUG: Management user detected, no customer record needed");
                        return null; // Admin doesn't need a customer record
                    }
                }
                
                // Check if we have an OAuth user ID stored
                OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
                if (oAuthUser != null) {
                    // Try to find customer by oauth_user_id column
                    try {
                        Customer customer = customerService.findByOAuthUserId(oAuthUser.getId());
                        if (customer != null) {
                            System.out.println("DEBUG: Found customer by OAuth user ID: " + customer.getCustomerId());
                            return customer;
                        }
                    } catch (Exception e) {
                        System.out.println("DEBUG: findByOAuthUserId not implemented or failed: " + e.getMessage());
                    }
                }
            }
            
            // For regular authentication, use user ID
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            if (userId > 0) {
                // Find customer by user_id (but be careful with user_id 54)
                List<Customer> customers = customerService.findByUserId((long) userId);
                
                // If user_id is 54 and there are many customers, we can't determine which one
                if (userId == 54 && customers.size() > 1) {
                    System.out.println("DEBUG: Multiple customers for user_id 54, cannot determine specific customer");
                    return null;
                }
                
                if (!customers.isEmpty()) {
                    return customers.get(0);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error getting current customer: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }

    /**
     * Check if the current user is an admin or employee
     */
    private boolean isAdminOrEmployee(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        
        // Check for specific roles
        return authentication.getAuthorities().stream()
            .anyMatch(auth -> 
                auth.getAuthority().contains("ROLE_MANAGER") || 
                auth.getAuthority().contains("ROLE_EMPLOYEE") ||
                auth.getAuthority().contains("ROLE_ADMIN") ||
                auth.getAuthority().contains("ROLE_OIDC_USER"));
    }

    /**
     * API endpoint to get properties for a property owner (AJAX)
     */
    @GetMapping("/api/properties/{ownerId}")
    @ResponseBody
    public ResponseEntity<List<Property>> getPropertiesForOwner(@PathVariable Integer ownerId) {
        try {
            List<Property> properties = propertyService.getPropertiesByOwner(ownerId.longValue());
            return ResponseEntity.ok(properties);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * API endpoint to get tenants (AJAX)
     */
    @GetMapping("/api/tenants")
    @ResponseBody
    public ResponseEntity<List<Customer>> getTenants() {
        try {
            List<Customer> tenants = customerService.findTenants();
            return ResponseEntity.ok(tenants);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Debug endpoint to check customer data
     */
    @GetMapping("/debug/customers")
    @ResponseBody
    public ResponseEntity<Object> debugCustomers() {
        try {
            List<Customer> allCustomers = customerService.findAll();
            List<Customer> propertyOwners = customerService.findPropertyOwners();
            List<Customer> tenants = customerService.findTenants();
            
            Map<String, Object> debug = new HashMap<>();
            debug.put("totalCustomers", allCustomers.size());
            debug.put("propertyOwnersFound", propertyOwners.size());
            debug.put("tenantsFound", tenants.size());
            
            // Sample data
            debug.put("firstFiveCustomers", allCustomers.stream().limit(5).map(c -> 
                Map.of(
                    "customerId", c.getCustomerId(),
                    "name", c.getName() != null ? c.getName() : "null",
                    "customerType", c.getCustomerType() != null ? c.getCustomerType().toString() : "null",
                    "isPropertyOwner", c.getIsPropertyOwner() != null ? c.getIsPropertyOwner() : "null",
                    "isTenant", c.getIsTenant() != null ? c.getIsTenant() : "null"
                )
            ).collect(java.util.stream.Collectors.toList()));
            
            debug.put("propertyOwners", propertyOwners.stream().limit(10).map(c -> 
                Map.of(
                    "customerId", c.getCustomerId(),
                    "name", c.getName() != null ? c.getName() : "null",
                    "customerType", c.getCustomerType() != null ? c.getCustomerType().toString() : "null",
                    "isPropertyOwner", c.getIsPropertyOwner() != null ? c.getIsPropertyOwner() : "null"
                )
            ).collect(java.util.stream.Collectors.toList()));
            
            return ResponseEntity.ok(debug);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Preview statement data (before generating)
     */
    @GetMapping("/preview")
    @ResponseBody
    public ResponseEntity<Object> previewStatement(
            @RequestParam("type") String type,
            @RequestParam("customerId") Integer customerId,
            @RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam("toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        
        try {
            if ("property-owner".equals(type)) {
                Customer propertyOwner = customerService.findByCustomerId(customerId.longValue());
                List<Property> properties = propertyService.getPropertiesByOwner(customerId.longValue());
                
                return ResponseEntity.ok(Map.of(
                    "propertyOwner", propertyOwner,
                    "properties", properties,
                    "propertyCount", properties.size(),
                    "period", fromDate + " to " + toDate
                ));
            } else if ("tenant".equals(type)) {
                Customer tenant = customerService.findByCustomerId(customerId.longValue());
                Property property = propertyService.getPropertyByTenant(customerId.longValue());
                
                return ResponseEntity.ok(Map.of(
                    "tenant", tenant,
                    "property", property,
                    "period", fromDate + " to " + toDate
                ));
            }
            
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error generating preview: " + e.getMessage());
        }
    }
}
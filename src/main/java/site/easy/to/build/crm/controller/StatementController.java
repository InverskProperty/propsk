// StatementController.java - Complete fix for statement generation with proper authentication

package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import site.easy.to.build.crm.service.sheets.GoogleSheetsServiceAccountService;
import site.easy.to.build.crm.service.statements.XLSXStatementService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.service.user.OAuthUserService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import site.easy.to.build.crm.enums.StatementDataSource;
import site.easy.to.build.crm.dto.StatementGenerationRequest;

@Controller
@RequestMapping("/statements")
public class StatementController {

    private final GoogleSheetsStatementService statementService;
    private final GoogleSheetsServiceAccountService serviceAccountSheetsService;
    private final XLSXStatementService xlsxStatementService;
    private final CustomerService customerService;
    private final PropertyService propertyService;
    private final OAuthUserService oAuthUserService;
    private final AuthenticationUtils authenticationUtils;

    @Autowired
    public StatementController(GoogleSheetsStatementService statementService,
                             GoogleSheetsServiceAccountService serviceAccountSheetsService,
                             XLSXStatementService xlsxStatementService,
                             CustomerService customerService,
                             PropertyService propertyService,
                             OAuthUserService oAuthUserService,
                             AuthenticationUtils authenticationUtils) {
        this.statementService = statementService;
        this.serviceAccountSheetsService = serviceAccountSheetsService;
        this.xlsxStatementService = xlsxStatementService;
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
        
        // Get OAuth user for Google Sheets access (but allow service account fallback)
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        boolean useServiceAccount = (oAuthUser == null || oAuthUser.getAccessToken() == null);

        if (useServiceAccount) {
            System.out.println("🔧 Using service account for statement generation (no OAuth)");
            model.addAttribute("useServiceAccount", true);
        } else {
            System.out.println("✅ Using OAuth user for statement generation");
            model.addAttribute("useServiceAccount", false);
        }
        
        // Determine what statements the user can generate
        Customer currentCustomer = getCurrentCustomerFromAuth(authentication);
        System.out.println("🔍 DEBUG: currentCustomer = " + (currentCustomer != null ? currentCustomer.getCustomerId() + " - " + currentCustomer.getName() : "null"));
        
        if (currentCustomer != null) {
            System.out.println("DEBUG: Found current customer: " + currentCustomer.getCustomerId() + 
                             " - " + currentCustomer.getName() + " (" + currentCustomer.getCustomerType() + ")");
            
            if (currentCustomer.getIsPropertyOwner() != null && currentCustomer.getIsPropertyOwner()) {
                // Property owner can only see their own statements
                System.out.println("DEBUG: Showing property owner view for customer: " + currentCustomer.getCustomerId());
                model.addAttribute("propertyOwners", Arrays.asList(currentCustomer));
                model.addAttribute("isOwnStatements", true);
                model.addAttribute("currentCustomer", currentCustomer);
                model.addAttribute("viewMode", "owner");
                return "statements/generate-statement";
            } else if (currentCustomer.getIsTenant() != null && currentCustomer.getIsTenant()) {
                // Tenant can only see their own statements
                System.out.println("DEBUG: Showing tenant view for customer: " + currentCustomer.getCustomerId());
                model.addAttribute("tenants", Arrays.asList(currentCustomer));
                model.addAttribute("isOwnStatements", true);
                model.addAttribute("currentCustomer", currentCustomer);
                model.addAttribute("viewMode", "tenant");
                return "statements/generate-statement";
            }
        }
        
        // Show admin view only for admin/employee users or when customer is not found
        if (isAdminOrEmployee(authentication)) {
            System.out.println("DEBUG: Showing admin view - user is admin/employee");
            List<Customer> propertyOwners = customerService.findPropertyOwners();
            List<Customer> tenants = customerService.findTenants();
            
            System.out.println("🔍 StatementController - Property Owners Query Result:");
            System.out.println("   Found " + propertyOwners.size() + " property owners");
            
            model.addAttribute("propertyOwners", propertyOwners);
            model.addAttribute("tenants", tenants);
            model.addAttribute("isOwnStatements", false);
            model.addAttribute("viewMode", "admin");
        } else {
            System.out.println("DEBUG: Access denied - not a customer and not admin/employee");
            model.addAttribute("error", "Access denied. Please contact support if you believe this is an error.");
            model.addAttribute("viewMode", "denied");
        }
        
        // Set default date range (current month)
        LocalDate now = LocalDate.now();
        LocalDate startOfMonth = now.withDayOfMonth(1);
        LocalDate endOfMonth = now.withDayOfMonth(now.lengthOfMonth());
        
        model.addAttribute("defaultFromDate", startOfMonth);
        model.addAttribute("defaultToDate", endOfMonth);
        model.addAttribute("hasGoogleAuth", true);

        // Add available account sources for selection
        model.addAttribute("availableAccountSources", Arrays.asList(StatementDataSource.values()));

        return "statements/generate-statement";
    }

    /**
     * Handle GET redirects to property owner statement (direct processing)
     */
    @GetMapping("/property-owner")
    public String handlePropertyOwnerStatementRedirect(
            @RequestParam("propertyOwnerId") Integer propertyOwnerId,
            @RequestParam("fromDate") String fromDate,
            @RequestParam("toDate") String toDate,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        System.out.println("🔄 GET redirect received for property owner statement - processing directly");
        
        try {
            // Parse dates - handle both DD/MM/YYYY and YYYY-MM-DD formats
            LocalDate parsedFromDate, parsedToDate;
            
            if (fromDate.contains("/")) {
                // Handle DD/MM/YYYY format (URL encoded as DD%2FMM%2FYYYY)
                String decodedFromDate = fromDate.replace("%2F", "/");
                String decodedToDate = toDate.replace("%2F", "/");
                
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                parsedFromDate = LocalDate.parse(decodedFromDate, formatter);
                parsedToDate = LocalDate.parse(decodedToDate, formatter);
            } else {
                // Handle YYYY-MM-DD format
                parsedFromDate = LocalDate.parse(fromDate);
                parsedToDate = LocalDate.parse(toDate);
            }
            
            // Call the existing POST method logic
            return generatePropertyOwnerStatementInternal(propertyOwnerId, parsedFromDate, parsedToDate, authentication, redirectAttributes);
            
        } catch (Exception e) {
            System.err.println("❌ Error parsing dates or generating statement: " + e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error generating statement: " + e.getMessage());
            return "redirect:/statements";
        }
    }

    /**
     * Generate property owner statement - FIXED with proper authorization (POST endpoint)
     */
    @PostMapping("/property-owner")
    public String generatePropertyOwnerStatement(
            @RequestParam("propertyOwnerId") Integer propertyOwnerId,
            @RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam("toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        return generatePropertyOwnerStatementInternal(propertyOwnerId, fromDate, toDate, authentication, redirectAttributes);
    }

    /**
     * Internal method for generating property owner statements (shared by GET and POST)
     */
    private String generatePropertyOwnerStatementInternal(
            Integer propertyOwnerId,
            LocalDate fromDate,
            LocalDate toDate,
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

            // Get property owner first
            Customer propertyOwner = customerService.findByCustomerId(propertyOwnerId.longValue());
            if (propertyOwner == null) {
                redirectAttributes.addFlashAttribute("error", "Property owner not found.");
                return "redirect:/statements";
            }

            // Try OAuth first, fall back to service account
            OAuthUser oAuthUser = null;
            boolean useServiceAccount = false;
            String spreadsheetId = null;

            try {
                oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
                if (oAuthUser != null && oAuthUser.getAccessToken() != null) {
                    // Use OAuth method
                    spreadsheetId = statementService.createPropertyOwnerStatement(oAuthUser, propertyOwner, fromDate, toDate);
                } else {
                    useServiceAccount = true;
                }
            } catch (Exception e) {
                useServiceAccount = true;
            }

            if (useServiceAccount) {
                System.out.println("📝 Statement generation: Using service account fallback for " + propertyOwner.getEmail());
                // Use service account method
                spreadsheetId = serviceAccountSheetsService.createPropertyOwnerStatement(propertyOwner, fromDate, toDate);
            }
            
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

    // ===== XLSX DOWNLOAD ENDPOINTS =====

    /**
     * Generate and download property owner statement as XLSX (NO Google authentication required)
     */
    @PostMapping("/property-owner/xlsx")
    public ResponseEntity<byte[]> generatePropertyOwnerStatementXLSX(
            @RequestParam("propertyOwnerId") Integer propertyOwnerId,
            @RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam("toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "periodBreakdown", defaultValue = "SINGLE") String periodBreakdown,
            @RequestParam(value = "accountSources", required = false) List<String> accountSourceNames,
            Authentication authentication) {

        try {
            System.out.println("🏢 XLSX: Generating property owner statement for ID: " + propertyOwnerId);
            System.out.println("🏢 XLSX: Period breakdown: " + periodBreakdown);

            // Reuse existing authorization logic
            Customer currentCustomer = getCurrentCustomerFromAuth(authentication);

            if (currentCustomer != null && currentCustomer.getIsPropertyOwner() != null && currentCustomer.getIsPropertyOwner()) {
                if (!currentCustomer.getCustomerId().equals(propertyOwnerId.longValue())) {
                    System.out.println("❌ XLSX: Authorization failed - customer can only access own statements");
                    return ResponseEntity.status(403).build();
                }
            } else if (!isAdminOrEmployee(authentication)) {
                System.out.println("❌ XLSX: Authorization failed - not admin/employee and not property owner");
                return ResponseEntity.status(403).build();
            }

            Customer propertyOwner = customerService.findByCustomerId(propertyOwnerId.longValue());
            if (propertyOwner == null) {
                System.out.println("❌ XLSX: Property owner not found: " + propertyOwnerId);
                return ResponseEntity.notFound().build();
            }

            // Parse account sources
            Set<StatementDataSource> includedSources = parseAccountSources(accountSourceNames);

            // Generate XLSX using appropriate service method
            byte[] excelData;
            boolean isMonthlyBreakdown = "MONTHLY".equals(periodBreakdown);

            if (isMonthlyBreakdown) {
                System.out.println("🏢 XLSX: Generating MONTHLY breakdown statement");
                excelData = xlsxStatementService.generateMonthlyPropertyOwnerStatementXLSX(
                    propertyOwner, fromDate, toDate, includedSources);
            } else {
                System.out.println("🏢 XLSX: Generating SINGLE statement");
                excelData = xlsxStatementService.generatePropertyOwnerStatementXLSX(
                    propertyOwner, fromDate, toDate);
            }

            String customerName = propertyOwner.getName() != null ? propertyOwner.getName() : "Customer" + propertyOwner.getCustomerId();
            String periodInfo = isMonthlyBreakdown ? "_Monthly" : "";
            String filename = String.format("Statement_%s%s_%s.xlsx",
                customerName.replaceAll("[^a-zA-Z0-9]", "_"),
                periodInfo,
                fromDate.format(DateTimeFormatter.ofPattern("yyyy-MM")));

            System.out.println("✅ XLSX: Generated statement - " + excelData.length + " bytes");

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelData);

        } catch (Exception e) {
            System.err.println("❌ XLSX: Error generating property owner statement: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Handle GET redirects to property owner XLSX statement
     */
    @GetMapping("/property-owner/xlsx")
    public ResponseEntity<byte[]> handlePropertyOwnerXLSXRedirect(
            @RequestParam("propertyOwnerId") Integer propertyOwnerId,
            @RequestParam("fromDate") String fromDate,
            @RequestParam("toDate") String toDate,
            @RequestParam(value = "periodBreakdown", defaultValue = "SINGLE") String periodBreakdown,
            @RequestParam(value = "sources", required = false) String sources,
            Authentication authentication) {

        try {
            // Parse dates - handle both DD/MM/YYYY and YYYY-MM-DD formats
            LocalDate parsedFromDate, parsedToDate;

            if (fromDate.contains("/")) {
                String decodedFromDate = fromDate.replace("%2F", "/");
                String decodedToDate = toDate.replace("%2F", "/");

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                parsedFromDate = LocalDate.parse(decodedFromDate, formatter);
                parsedToDate = LocalDate.parse(decodedToDate, formatter);
            } else {
                parsedFromDate = LocalDate.parse(fromDate);
                parsedToDate = LocalDate.parse(toDate);
            }

            // Parse sources
            List<String> accountSourceNames = sources != null && !sources.isEmpty() ?
                Arrays.asList(sources.split(",")) : List.of();

            // Call POST method with parsed dates
            return generatePropertyOwnerStatementXLSX(propertyOwnerId, parsedFromDate, parsedToDate,
                periodBreakdown, accountSourceNames, authentication);

        } catch (Exception e) {
            System.err.println("❌ XLSX GET: Error parsing dates or generating statement: " + e.getMessage());
            return ResponseEntity.status(400).build();
        }
    }

    /**
     * Generate and download tenant statement as XLSX
     */
    @PostMapping("/tenant/xlsx")
    public ResponseEntity<byte[]> generateTenantStatementXLSX(
            @RequestParam("tenantId") Integer tenantId,
            @RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam("toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Authentication authentication) {

        try {
            System.out.println("🏠 XLSX: Generating tenant statement for ID: " + tenantId);

            // Check authorization
            Customer currentCustomer = getCurrentCustomerFromAuth(authentication);

            if (currentCustomer != null && currentCustomer.getIsTenant() != null && currentCustomer.getIsTenant()) {
                if (!currentCustomer.getCustomerId().equals(tenantId.longValue())) {
                    return ResponseEntity.status(403).build();
                }
            } else if (!isAdminOrEmployee(authentication)) {
                return ResponseEntity.status(403).build();
            }

            Customer tenant = customerService.findByCustomerId(tenantId.longValue());
            if (tenant == null) {
                return ResponseEntity.notFound().build();
            }

            // Generate XLSX
            byte[] excelData = xlsxStatementService.generateTenantStatementXLSX(
                tenant, fromDate, toDate);

            String tenantName = tenant.getName() != null ? tenant.getName() : "Customer" + tenant.getCustomerId();
            String filename = String.format("Tenant_Statement_%s_%s.xlsx",
                tenantName.replaceAll("[^a-zA-Z0-9]", "_"),
                fromDate.format(DateTimeFormatter.ofPattern("yyyy-MM")));

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelData);

        } catch (Exception e) {
            System.err.println("❌ XLSX: Error generating tenant statement: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Generate and download portfolio statement as XLSX
     */
    @PostMapping("/portfolio/xlsx")
    public ResponseEntity<byte[]> generatePortfolioStatementXLSX(
            @RequestParam("propertyOwnerId") Integer propertyOwnerId,
            @RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam("toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Authentication authentication) {

        try {
            System.out.println("📊 XLSX: Generating portfolio statement for ID: " + propertyOwnerId);

            // Check authorization (same as property owner statement)
            Customer currentCustomer = getCurrentCustomerFromAuth(authentication);

            if (currentCustomer != null && currentCustomer.getIsPropertyOwner() != null && currentCustomer.getIsPropertyOwner()) {
                if (!currentCustomer.getCustomerId().equals(propertyOwnerId.longValue())) {
                    return ResponseEntity.status(403).build();
                }
            } else if (!isAdminOrEmployee(authentication)) {
                return ResponseEntity.status(403).build();
            }

            Customer propertyOwner = customerService.findByCustomerId(propertyOwnerId.longValue());
            if (propertyOwner == null) {
                return ResponseEntity.notFound().build();
            }

            // Generate XLSX
            byte[] excelData = xlsxStatementService.generatePortfolioStatementXLSX(
                propertyOwner, fromDate, toDate);

            String ownerName = propertyOwner.getName() != null ? propertyOwner.getName() : "Customer" + propertyOwner.getCustomerId();
            String filename = String.format("Portfolio_Statement_%s_%s.xlsx",
                ownerName.replaceAll("[^a-zA-Z0-9]", "_"),
                fromDate.format(DateTimeFormatter.ofPattern("yyyy-MM")));

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelData);

        } catch (Exception e) {
            System.err.println("❌ XLSX: Error generating portfolio statement: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
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
                // IMPORTANT: Check if this is an admin user first
                // Admin users should NOT return customer records even if linked
                if (isAdminOrEmployee(authentication)) {
                    System.out.println("DEBUG: Admin/Employee user detected (ID: " + userId + ") - not returning customer record");
                    return null; // Admin gets admin view, not customer view
                }
                
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
     * FIXED: Allow OIDC_USER (Google OAuth) users to access statements as admin
     */
    private boolean isAdminOrEmployee(Authentication authentication) {
        if (authentication == null) {
            return false;
        }

        // Debug authentication details
        System.out.println("🔍 DEBUG: Authentication check for statements access:");
        System.out.println("   Principal: " + authentication.getPrincipal().getClass().getSimpleName());
        System.out.println("   Name: " + authentication.getName());
        System.out.println("   Authorities: ");
        authentication.getAuthorities().forEach(auth ->
            System.out.println("     - " + auth.getAuthority()));

        // Check for specific roles - RELAXED for Google OAuth users
        boolean hasAccess = authentication.getAuthorities().stream()
            .anyMatch(auth ->
                auth.getAuthority().contains("ROLE_MANAGER") ||
                auth.getAuthority().contains("ROLE_EMPLOYEE") ||
                auth.getAuthority().contains("ROLE_ADMIN") ||
                auth.getAuthority().contains("ROLE_OIDC_USER") || // Google OAuth users
                auth.getAuthority().contains("ROLE_USER")); // Standard users

        System.out.println("   🔑 Access granted: " + hasAccess);
        return hasAccess;
    }

    // ===== ACCOUNT SOURCE SELECTION ENDPOINTS =====

    /**
     * Generate property owner statement with account source selection
     */
    @PostMapping("/property-owner/with-sources")
    public String generatePropertyOwnerStatementWithSources(
            @RequestParam("propertyOwnerId") Integer propertyOwnerId,
            @RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam("toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "accountSources", required = false) List<String> accountSourceNames,
            @RequestParam(value = "periodBreakdown", defaultValue = "SINGLE") String periodBreakdown,
            @RequestParam(value = "outputFormat", defaultValue = "GOOGLE_SHEETS") String outputFormat,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {
            // Check authorization
            Customer currentCustomer = getCurrentCustomerFromAuth(authentication);

            if (currentCustomer != null && currentCustomer.getIsPropertyOwner() != null && currentCustomer.getIsPropertyOwner()) {
                if (!currentCustomer.getCustomerId().equals(propertyOwnerId.longValue())) {
                    redirectAttributes.addFlashAttribute("error", "You can only generate statements for your own account.");
                    return "redirect:/statements";
                }
            } else if (!isAdminOrEmployee(authentication)) {
                redirectAttributes.addFlashAttribute("error", "Access denied.");
                return "redirect:/statements";
            }

            // Parse account sources
            Set<StatementDataSource> includedSources = parseAccountSources(accountSourceNames);

            // Get property owner
            Customer propertyOwner = customerService.findByCustomerId(propertyOwnerId.longValue());
            if (propertyOwner == null) {
                redirectAttributes.addFlashAttribute("error", "Property owner not found.");
                return "redirect:/statements";
            }

            // Create request DTO
            StatementGenerationRequest request = new StatementGenerationRequest();
            request.setPropertyOwnerId(propertyOwnerId.longValue());
            request.setFromDate(fromDate);
            request.setToDate(toDate);
            request.setIncludedDataSources(includedSources);
            request.setOutputFormat(outputFormat);

            // Generate based on output format
            if ("XLSX".equals(outputFormat)) {
                // Redirect to XLSX download endpoint
                redirectAttributes.addFlashAttribute("info", "Downloading XLSX statement with selected sources...");
                return "redirect:/statements/property-owner/xlsx?propertyOwnerId=" + propertyOwnerId +
                       "&fromDate=" + fromDate + "&toDate=" + toDate +
                       "&periodBreakdown=" + periodBreakdown +
                       "&sources=" + String.join(",", accountSourceNames != null ? accountSourceNames : List.of());
            } else {
                // Google Sheets generation
                OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
                String spreadsheetId;

                // Check if monthly breakdown is requested
                boolean isMonthlyBreakdown = "MONTHLY".equals(periodBreakdown);

                if (isMonthlyBreakdown) {
                    // Generate multi-sheet monthly breakdown
                    if (oAuthUser != null && oAuthUser.getAccessToken() != null) {
                        spreadsheetId = statementService.createMonthlyPropertyOwnerStatement(
                            oAuthUser, propertyOwner, fromDate, toDate, includedSources);
                    } else {
                        spreadsheetId = serviceAccountSheetsService.createMonthlyPropertyOwnerStatement(
                            propertyOwner, fromDate, toDate, includedSources);
                    }
                } else {
                    // Generate single sheet statement
                    if (oAuthUser != null && oAuthUser.getAccessToken() != null) {
                        spreadsheetId = statementService.createPropertyOwnerStatement(oAuthUser, propertyOwner, fromDate, toDate);
                    } else {
                        spreadsheetId = serviceAccountSheetsService.createPropertyOwnerStatement(propertyOwner, fromDate, toDate);
                    }
                }

                String sheetsUrl = "https://docs.google.com/spreadsheets/d/" + spreadsheetId;
                String sourceInfo = includedSources.isEmpty() ? "all sources" :
                    includedSources.stream().map(StatementDataSource::getDisplayName).collect(Collectors.joining(", "));
                String periodInfo = isMonthlyBreakdown ? " (monthly breakdown)" : "";

                redirectAttributes.addFlashAttribute("success",
                    "Statement generated successfully" + periodInfo + " with sources: " + sourceInfo + "! " +
                    "<a href='" + sheetsUrl + "' target='_blank'>View in Google Sheets</a>");

                return "redirect:/statements";
            }

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "Error generating statement: " + e.getMessage());
            return "redirect:/statements";
        }
    }

    /**
     * Get available account sources as JSON (AJAX endpoint)
     */
    @GetMapping("/api/account-sources")
    @ResponseBody
    public ResponseEntity<List<Map<String, String>>> getAvailableAccountSources() {
        try {
            List<Map<String, String>> sources = Arrays.stream(StatementDataSource.values())
                .map(source -> {
                    Map<String, String> sourceMap = new HashMap<>();
                    sourceMap.put("key", source.name());
                    sourceMap.put("displayName", source.getDisplayName());
                    sourceMap.put("description", source.getDescription());
                    sourceMap.put("accountSourceValue", source.getAccountSourceValue());
                    sourceMap.put("isHistorical", String.valueOf(source.isHistorical()));
                    sourceMap.put("isUnified", String.valueOf(source.isUnified()));
                    sourceMap.put("isPayProp", String.valueOf(source.isPayProp()));
                    return sourceMap;
                })
                .collect(Collectors.toList());

            return ResponseEntity.ok(sources);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Parse account source names into StatementDataSource set
     */
    private Set<StatementDataSource> parseAccountSources(List<String> accountSourceNames) {
        if (accountSourceNames == null || accountSourceNames.isEmpty()) {
            return new HashSet<>(); // Return empty set = include all
        }

        return accountSourceNames.stream()
            .map(name -> {
                try {
                    return StatementDataSource.valueOf(name);
                } catch (IllegalArgumentException e) {
                    System.err.println("Invalid account source name: " + name);
                    return null;
                }
            })
            .filter(source -> source != null)
            .collect(Collectors.toSet());
    }

    /**
     * Debug endpoint to check authentication status
     */
    @GetMapping("/debug-auth")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> debugAuthentication(Authentication authentication) {
        Map<String, Object> debug = new HashMap<>();

        try {
            debug.put("authenticated", authentication != null);

            if (authentication != null) {
                debug.put("name", authentication.getName());
                debug.put("principalType", authentication.getPrincipal().getClass().getSimpleName());

                List<String> authorities = authentication.getAuthorities().stream()
                    .map(auth -> auth.getAuthority())
                    .collect(java.util.stream.Collectors.toList());
                debug.put("authorities", authorities);

                debug.put("isAdminOrEmployee", isAdminOrEmployee(authentication));

                Customer currentCustomer = getCurrentCustomerFromAuth(authentication);
                if (currentCustomer != null) {
                    debug.put("customerId", currentCustomer.getCustomerId());
                    debug.put("customerName", currentCustomer.getName());
                    debug.put("customerType", currentCustomer.getCustomerType());
                    debug.put("isPropertyOwner", currentCustomer.getIsPropertyOwner());
                } else {
                    debug.put("customer", "Not found");
                }
            }

            return ResponseEntity.ok(debug);

        } catch (Exception e) {
            debug.put("error", e.getMessage());
            return ResponseEntity.status(500).body(debug);
        }
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

    // ===== SERVICE ACCOUNT GOOGLE SHEETS ENDPOINTS (NO USER AUTHENTICATION REQUIRED) =====

    /**
     * Generate property owner statement using service account (no Google OAuth required)
     */
    @PostMapping("/property-owner/service-account")
    public String generatePropertyOwnerStatementServiceAccount(
            @RequestParam("propertyOwnerId") Integer propertyOwnerId,
            @RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam("toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {
            // Check authorization (same as regular endpoints)
            Customer currentCustomer = getCurrentCustomerFromAuth(authentication);

            if (currentCustomer != null && currentCustomer.getIsPropertyOwner() != null && currentCustomer.getIsPropertyOwner()) {
                if (!currentCustomer.getCustomerId().equals(propertyOwnerId.longValue())) {
                    redirectAttributes.addFlashAttribute("error", "You can only generate statements for your own account.");
                    return "redirect:/statements";
                }
            } else if (!isAdminOrEmployee(authentication)) {
                redirectAttributes.addFlashAttribute("error", "Access denied.");
                return "redirect:/statements";
            }

            // Get property owner
            Customer propertyOwner = customerService.findByCustomerId(propertyOwnerId.longValue());
            if (propertyOwner == null) {
                redirectAttributes.addFlashAttribute("error", "Property owner not found.");
                return "redirect:/statements";
            }

            // Generate statement using service account
            String spreadsheetId = serviceAccountSheetsService.createPropertyOwnerStatement(
                propertyOwner, fromDate, toDate);

            // Success message with link to Google Sheets
            String sheetsUrl = "https://docs.google.com/spreadsheets/d/" + spreadsheetId;
            redirectAttributes.addFlashAttribute("success",
                "Statement generated successfully! <a href='" + sheetsUrl + "' target='_blank'>View in Google Sheets</a>");

            return "redirect:/statements";

        } catch (Exception e) {
            System.err.println("❌ Error generating service account statement: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error",
                "Error generating statement: " + e.getMessage());
            return "redirect:/statements";
        }
    }

    /**
     * Generate tenant statement using service account (no Google OAuth required)
     */
    @PostMapping("/tenant/service-account")
    public String generateTenantStatementServiceAccount(
            @RequestParam("tenantId") Integer tenantId,
            @RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam("toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {
            // Check authorization
            Customer currentCustomer = getCurrentCustomerFromAuth(authentication);

            if (currentCustomer != null && currentCustomer.getIsTenant() != null && currentCustomer.getIsTenant()) {
                if (!currentCustomer.getCustomerId().equals(tenantId.longValue())) {
                    redirectAttributes.addFlashAttribute("error", "You can only generate statements for your own tenancy.");
                    return "redirect:/statements";
                }
            } else if (!isAdminOrEmployee(authentication)) {
                redirectAttributes.addFlashAttribute("error", "Access denied.");
                return "redirect:/statements";
            }

            // Get tenant
            Customer tenant = customerService.findByCustomerId(tenantId.longValue());
            if (tenant == null) {
                redirectAttributes.addFlashAttribute("error", "Tenant not found.");
                return "redirect:/statements";
            }

            // Generate statement using service account
            String spreadsheetId = serviceAccountSheetsService.createTenantStatement(
                tenant, fromDate, toDate);

            // Success message with link to Google Sheets
            String sheetsUrl = "https://docs.google.com/spreadsheets/d/" + spreadsheetId;
            redirectAttributes.addFlashAttribute("success",
                "Tenant statement generated successfully! <a href='" + sheetsUrl + "' target='_blank'>View in Google Sheets</a>");

            return "redirect:/statements";

        } catch (Exception e) {
            System.err.println("❌ Error generating service account tenant statement: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error",
                "Error generating tenant statement: " + e.getMessage());
            return "redirect:/statements";
        }
    }
}
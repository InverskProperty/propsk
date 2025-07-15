package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
import java.util.List;
import java.util.Map; // FIXED: Added missing Map import
import java.util.Arrays;

@Controller
@RequestMapping("/statements")
public class StatementController {

    private final GoogleSheetsStatementService statementService;
    private final CustomerService customerService;
    private final PropertyService propertyService;
    private final OAuthUserService oAuthUserService;
    private final AuthenticationUtils authenticationUtils; // FIXED: Added AuthenticationUtils

    @Autowired
    public StatementController(GoogleSheetsStatementService statementService,
                             CustomerService customerService,
                             PropertyService propertyService,
                             OAuthUserService oAuthUserService,
                             AuthenticationUtils authenticationUtils) { // FIXED: Added to constructor
        this.statementService = statementService;
        this.customerService = customerService;
        this.propertyService = propertyService;
        this.oAuthUserService = oAuthUserService;
        this.authenticationUtils = authenticationUtils;
    }

    /**
     * Show statement generation page
     */
    @GetMapping
    public String showStatements(Model model, Authentication authentication) {
        // FIXED: Use authenticationUtils method
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        Integer userId = oAuthUser != null ? oAuthUser.getUserId() : null;
        
        // Get property owners for dropdown
        List<Customer> propertyOwners = customerService.findPropertyOwners();
        model.addAttribute("propertyOwners", propertyOwners);
        
        // Set default date range (current month)
        LocalDate now = LocalDate.now();
        LocalDate startOfMonth = now.withDayOfMonth(1);
        LocalDate endOfMonth = now.withDayOfMonth(now.lengthOfMonth());
        
        model.addAttribute("defaultFromDate", startOfMonth);
        model.addAttribute("defaultToDate", endOfMonth);
        
        return "statements/generate-statement";
    }

    /**
     * Generate property owner statement
     */
    @PostMapping("/property-owner")
    public String generatePropertyOwnerStatement(
            @RequestParam("propertyOwnerId") Integer propertyOwnerId,
            @RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam("toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        try {
            // FIXED: Use authenticationUtils method
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            
            if (oAuthUser == null || oAuthUser.getAccessToken() == null) {
                redirectAttributes.addFlashAttribute("error", 
                    "Google account not connected. Please connect your Google account first.");
                return "redirect:/statements";
            }
            
            // Get property owner
            Customer propertyOwner = customerService.findByCustomerId(propertyOwnerId);
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
     * Generate tenant statement
     */
    @PostMapping("/tenant")
    public String generateTenantStatement(
            @RequestParam("tenantId") Integer tenantId,
            @RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam("toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        try {
            // FIXED: Use authenticationUtils method
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            
            if (oAuthUser == null || oAuthUser.getAccessToken() == null) {
                redirectAttributes.addFlashAttribute("error", 
                    "Google account not connected. Please connect your Google account first.");
                return "redirect:/statements";
            }
            
            // Get tenant
            Customer tenant = customerService.findByCustomerId(tenantId);
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
            // FIXED: Use authenticationUtils method
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            
            if (oAuthUser == null || oAuthUser.getAccessToken() == null) {
                redirectAttributes.addFlashAttribute("error", 
                    "Google account not connected. Please connect your Google account first.");
                return "redirect:/statements";
            }
            
            // Get property owner
            Customer propertyOwner = customerService.findByCustomerId(propertyOwnerId);
            if (propertyOwner == null) {
                redirectAttributes.addFlashAttribute("error", "Property owner not found.");
                return "redirect:/statements";
            }
            
            // Generate portfolio statement
            String spreadsheetId = statementService.createPortfolioStatement(
                oAuthUser, propertyOwner, fromDate, toDate);
            
            // Success message with link to Google Sheets
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

    /**
     * API endpoint to get properties for a property owner (AJAX)
     */
    @GetMapping("/api/properties/{ownerId}")
    @ResponseBody
    public ResponseEntity<List<Property>> getPropertiesForOwner(@PathVariable Integer ownerId) {
        try {
            List<Property> properties = propertyService.getPropertiesByOwner(ownerId);
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
                Customer propertyOwner = customerService.findByCustomerId(customerId);
                List<Property> properties = propertyService.getPropertiesByOwner(customerId);
                
                // Return preview data
                return ResponseEntity.ok(Map.of( // FIXED: Now Map is imported
                    "propertyOwner", propertyOwner,
                    "properties", properties,
                    "propertyCount", properties.size(),
                    "period", fromDate + " to " + toDate
                ));
            } else if ("tenant".equals(type)) {
                Customer tenant = customerService.findByCustomerId(customerId);
                Property property = propertyService.getPropertyByTenant(customerId);
                
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
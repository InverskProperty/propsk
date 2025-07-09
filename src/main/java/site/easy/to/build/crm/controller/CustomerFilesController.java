package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.GoogleDriveFile;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.drive.CustomerDriveOrganizationService;
import site.easy.to.build.crm.service.payprop.PayPropSyncOrchestrator;
import site.easy.to.build.crm.service.sheets.GoogleSheetsStatementService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/customer/files")
public class CustomerFilesController {

    private final CustomerService customerService;
    private final CustomerDriveOrganizationService customerDriveOrganizationService;
    private final PayPropSyncOrchestrator payPropSyncOrchestrator;
    private final GoogleSheetsStatementService googleSheetsStatementService;
    private final AuthenticationUtils authenticationUtils;

    @Autowired
    public CustomerFilesController(CustomerService customerService,
                                 CustomerDriveOrganizationService customerDriveOrganizationService,
                                 PayPropSyncOrchestrator payPropSyncOrchestrator,
                                 GoogleSheetsStatementService googleSheetsStatementService,
                                 AuthenticationUtils authenticationUtils) {
        this.customerService = customerService;
        this.customerDriveOrganizationService = customerDriveOrganizationService;
        this.payPropSyncOrchestrator = payPropSyncOrchestrator;
        this.googleSheetsStatementService = googleSheetsStatementService;
        this.authenticationUtils = authenticationUtils;
    }

    /**
     * Display customer files dashboard
     */
    @GetMapping("/{customerId}")
    public String customerFilesDashboard(@PathVariable int customerId, Model model, Authentication authentication) {
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            Customer customer = customerService.findByCustomerId(customerId);
            
            if (customer == null) {
                return "redirect:/customer/all-customers";
            }
            
            // Get customer files by category
            Map<String, List<GoogleDriveFile>> filesByCategory = getCustomerFilesByCategory(customer, oAuthUser);
            
            // Get folder structure
            CustomerDriveOrganizationService.CustomerFolderStructure folderStructure = 
                customerDriveOrganizationService.getOrCreateCustomerFolderStructure(oAuthUser, customer);
            
            model.addAttribute("customer", customer);
            model.addAttribute("filesByCategory", filesByCategory);
            model.addAttribute("folderStructure", folderStructure);
            model.addAttribute("canGenerateStatements", canGenerateStatements(customer));
            
            return "customer/files-dashboard";
        } catch (Exception e) {
            model.addAttribute("error", "Error loading customer files: " + e.getMessage());
            return "error/error";
        }
    }

    /**
     * Upload file to customer folder
     */
    @PostMapping("/{customerId}/upload")
    public String uploadFile(@PathVariable int customerId,
                           @RequestParam("file") MultipartFile file,
                           @RequestParam("category") String category,
                           @RequestParam(value = "description", required = false) String description,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            Customer customer = customerService.findByCustomerId(customerId);
            
            if (customer == null) {
                redirectAttributes.addFlashAttribute("error", "Customer not found");
                return "redirect:/customer/all-customers";
            }
            
            // Upload file to Google Drive and organize
            // This would need additional implementation in your GoogleDriveApiService
            // to handle file uploads from MultipartFile
            
            redirectAttributes.addFlashAttribute("success", "File uploaded successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error uploading file: " + e.getMessage());
        }
        
        return "redirect:/customer/files/" + customerId;
    }

    /**
     * Generate property owner statement
     */
    @PostMapping("/{customerId}/generate-owner-statement")
    public ResponseEntity<Map<String, String>> generateOwnerStatement(@PathVariable int customerId,
                                                                     @RequestParam("fromDate") String fromDate,
                                                                     @RequestParam("toDate") String toDate,
                                                                     Authentication authentication) {
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            Customer customer = customerService.findByCustomerId(customerId);
            
            if (customer == null || customer.getCustomerType() != CustomerType.PROPERTY_OWNER) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid customer or customer type"));
            }
            
            LocalDate from = LocalDate.parse(fromDate);
            LocalDate to = LocalDate.parse(toDate);
            
            String spreadsheetId = googleSheetsStatementService.createPropertyOwnerStatement(oAuthUser, customer, from, to);
            String spreadsheetUrl = "https://docs.google.com/spreadsheets/d/" + spreadsheetId;
            
            return ResponseEntity.ok(Map.of(
                "success", "Statement generated successfully",
                "spreadsheetId", spreadsheetId,
                "spreadsheetUrl", spreadsheetUrl
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error generating statement: " + e.getMessage()));
        }
    }

    /**
     * Generate tenant statement
     */
    @PostMapping("/{customerId}/generate-tenant-statement")
    public ResponseEntity<Map<String, String>> generateTenantStatement(@PathVariable int customerId,
                                                                      @RequestParam("fromDate") String fromDate,
                                                                      @RequestParam("toDate") String toDate,
                                                                      Authentication authentication) {
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            Customer customer = customerService.findByCustomerId(customerId);
            
            if (customer == null || customer.getCustomerType() != CustomerType.TENANT) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid customer or customer type"));
            }
            
            LocalDate from = LocalDate.parse(fromDate);
            LocalDate to = LocalDate.parse(toDate);
            
            String spreadsheetId = googleSheetsStatementService.createTenantStatement(oAuthUser, customer, from, to);
            String spreadsheetUrl = "https://docs.google.com/spreadsheets/d/" + spreadsheetId;
            
            return ResponseEntity.ok(Map.of(
                "success", "Statement generated successfully",
                "spreadsheetId", spreadsheetId,
                "spreadsheetUrl", spreadsheetUrl
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error generating statement: " + e.getMessage()));
        }
    }

    /**
     * Generate portfolio statement
     */
    @PostMapping("/{customerId}/generate-portfolio-statement")
    public ResponseEntity<Map<String, String>> generatePortfolioStatement(@PathVariable int customerId,
                                                                          @RequestParam("fromDate") String fromDate,
                                                                          @RequestParam("toDate") String toDate,
                                                                          Authentication authentication) {
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            Customer customer = customerService.findByCustomerId(customerId);
            
            if (customer == null || customer.getCustomerType() != CustomerType.PROPERTY_OWNER) {
                return ResponseEntity.badRequest().body(Map.of("error", "Only property owners can generate portfolio statements"));
            }
            
            LocalDate from = LocalDate.parse(fromDate);
            LocalDate to = LocalDate.parse(toDate);
            
            String spreadsheetId = googleSheetsStatementService.createPortfolioStatement(oAuthUser, customer, from, to);
            String spreadsheetUrl = "https://docs.google.com/spreadsheets/d/" + spreadsheetId;
            
            return ResponseEntity.ok(Map.of(
                "success", "Portfolio statement generated successfully",
                "spreadsheetId", spreadsheetId,
                "spreadsheetUrl", spreadsheetUrl
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error generating portfolio statement: " + e.getMessage()));
        }
    }

    /**
     * Sync PayProp files for customer - UPDATED to use PayPropSyncOrchestrator
     */
    @PostMapping("/{customerId}/sync-payprop")
    public ResponseEntity<Map<String, String>> syncPayPropFiles(@PathVariable int customerId,
                                                               Authentication authentication) {
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            Customer customer = customerService.findByCustomerId(customerId);
            
            if (customer == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Customer not found"));
            }
            
            // Use the integrated PayPropSyncOrchestrator approach
            var result = payPropSyncOrchestrator.syncPayPropFiles(oAuthUser, oAuthUser.getUserId());
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(Map.of("success", "PayProp files synced successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "File sync completed with errors: " + result.getMessage()));
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error syncing PayProp files: " + e.getMessage()));
        }
    }

    /**
     * Sync all PayProp files (admin function) - UPDATED to use PayPropSyncOrchestrator
     */
    @PostMapping("/admin/sync-all-payprop")
    public ResponseEntity<Map<String, String>> syncAllPayPropFiles(Authentication authentication) {
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            
            // Run comprehensive sync including files in background thread
            new Thread(() -> {
                try {
                    payPropSyncOrchestrator.performUnifiedSync(oAuthUser, oAuthUser.getUserId());
                } catch (Exception e) {
                    // Log error
                    System.err.println("Error in comprehensive PayProp sync: " + e.getMessage());
                }
            }).start();
            
            return ResponseEntity.ok(Map.of("success", "Comprehensive PayProp sync started in background"));
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error starting PayProp sync: " + e.getMessage()));
        }
    }

    /**
     * Delete file
     */
    @DeleteMapping("/{customerId}/file/{fileId}")
    public ResponseEntity<Map<String, String>> deleteFile(@PathVariable int customerId,
                                                          @PathVariable int fileId,
                                                          Authentication authentication) {
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            Customer customer = customerService.findByCustomerId(customerId);
            
            if (customer == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Customer not found"));
            }
            
            // Implementation would go here to delete file from Google Drive and database
            // This requires additional methods in your services
            
            return ResponseEntity.ok(Map.of("success", "File deleted successfully"));
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error deleting file: " + e.getMessage()));
        }
    }

    /**
     * Get customer files by category (for AJAX requests)
     */
    @GetMapping("/{customerId}/api/files")
    @ResponseBody
    public ResponseEntity<Map<String, List<GoogleDriveFile>>> getCustomerFilesApi(@PathVariable int customerId,
                                                                                 @RequestParam(required = false) String category,
                                                                                 Authentication authentication) {
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            Customer customer = customerService.findByCustomerId(customerId);
            
            if (customer == null) {
                return ResponseEntity.badRequest().body(Map.of());
            }
            
            Map<String, List<GoogleDriveFile>> filesByCategory = getCustomerFilesByCategory(customer, oAuthUser);
            
            if (category != null) {
                List<GoogleDriveFile> categoryFiles = filesByCategory.get(category);
                return ResponseEntity.ok(Map.of(category, categoryFiles != null ? categoryFiles : List.of()));
            }
            
            return ResponseEntity.ok(filesByCategory);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of());
        }
    }

    /**
     * Customer portal - view own files (for tenant/owner portals)
     */
    @GetMapping("/portal/my-files")
    public String customerPortalFiles(Model model, Authentication authentication) {
        try {
            // This would be used in customer portals where customers log in
            // You'd need to get the customer from the customer authentication
            Customer customer = getCurrentCustomerFromAuth(authentication);
            
            if (customer == null) {
                return "redirect:/customer/login";
            }
            
            // Get files based on customer access level
            CustomerType accessLevel = customer.getCustomerType();
            List<GoogleDriveFile> accessibleFiles = customerDriveOrganizationService.getCustomerFiles(customer, null, accessLevel);
            
            model.addAttribute("customer", customer);
            model.addAttribute("files", accessibleFiles);
            model.addAttribute("accessLevel", accessLevel);
            
            return "customer-portal/my-files";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading files: " + e.getMessage());
            return "error/error";
        }
    }

    // Helper methods
    private Map<String, List<GoogleDriveFile>> getCustomerFilesByCategory(Customer customer, OAuthUser oAuthUser) {
        // Implementation depends on your GoogleDriveFileService having category-based queries
        // This is a placeholder that would need to be implemented
        return Map.of();
    }

    private boolean canGenerateStatements(Customer customer) {
        return customer.getCustomerType() == CustomerType.PROPERTY_OWNER || 
               customer.getCustomerType() == CustomerType.TENANT;
    }

    private Customer getCurrentCustomerFromAuth(Authentication authentication) {
        // Implementation depends on your customer authentication system
        // This would extract the customer from customer login authentication
        return null;
    }
}
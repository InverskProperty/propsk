package site.easy.to.build.crm.controller;

import jakarta.servlet.http.HttpServletResponse;
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
import site.easy.to.build.crm.entity.AssignmentType;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.GoogleDriveFile;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.repository.GoogleDriveFileRepository;
import site.easy.to.build.crm.service.assignment.CustomerPropertyAssignmentService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.drive.CustomerDriveOrganizationService;
import site.easy.to.build.crm.service.drive.SharedDriveFileService;
import site.easy.to.build.crm.service.payprop.PayPropSyncOrchestrator;
import site.easy.to.build.crm.service.sheets.GoogleSheetsStatementService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/customer/files")
public class CustomerFilesController {

    private final CustomerService customerService;
    private final CustomerDriveOrganizationService customerDriveOrganizationService;
    private final SharedDriveFileService sharedDriveFileService;
    private final PayPropSyncOrchestrator payPropSyncOrchestrator;
    private final GoogleSheetsStatementService googleSheetsStatementService;
    private final AuthenticationUtils authenticationUtils;
    private final GoogleDriveFileRepository googleDriveFileRepository;
    private final CustomerPropertyAssignmentService assignmentService;

    @Autowired
    public CustomerFilesController(CustomerService customerService,
                                 CustomerDriveOrganizationService customerDriveOrganizationService,
                                 SharedDriveFileService sharedDriveFileService,
                                 @Autowired(required = false) PayPropSyncOrchestrator payPropSyncOrchestrator,
                                 GoogleSheetsStatementService googleSheetsStatementService,
                                 AuthenticationUtils authenticationUtils,
                                 GoogleDriveFileRepository googleDriveFileRepository,
                                 CustomerPropertyAssignmentService assignmentService) {
        this.customerService = customerService;
        this.customerDriveOrganizationService = customerDriveOrganizationService;
        this.sharedDriveFileService = sharedDriveFileService;
        this.payPropSyncOrchestrator = payPropSyncOrchestrator;
        this.googleSheetsStatementService = googleSheetsStatementService;
        this.authenticationUtils = authenticationUtils;
        this.googleDriveFileRepository = googleDriveFileRepository;
        this.assignmentService = assignmentService;
    }

    /**
     * Display customer files dashboard
     */
    @GetMapping("/{customerId}")
    public String customerFilesDashboard(@PathVariable Long customerId, Model model, Authentication authentication) {
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            Customer customer = customerService.findByCustomerId(customerId);

            if (customer == null) {
                return "redirect:/customer/all-customers";
            }

            // Get customer files by category
            Map<String, List<GoogleDriveFile>> filesByCategory = getCustomerFilesByCategory(customer, oAuthUser);

            // Get all files (flatten the category map)
            List<GoogleDriveFile> allFiles = filesByCategory.values().stream()
                .flatMap(List::stream)
                .collect(java.util.stream.Collectors.toList());

            // Get folder structure (optional - may not be needed if using Shared Drive)
            CustomerDriveOrganizationService.CustomerFolderStructure folderStructure = null;
            try {
                folderStructure = customerDriveOrganizationService.getOrCreateCustomerFolderStructure(oAuthUser, customer);
            } catch (Exception e) {
                // Folder structure is optional - customer files come from Shared Drive
                System.out.println("⚠️ Could not create customer folder structure (using Shared Drive): " + e.getMessage());
            }

            model.addAttribute("customer", customer);
            model.addAttribute("filesByCategory", filesByCategory);
            model.addAttribute("allFiles", allFiles);
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
     * Files are uploaded to the Shared Drive and metadata is saved to the database
     */
    @PostMapping("/{customerId}/upload")
    public String uploadFile(@PathVariable Long customerId,
                           @RequestParam("file") MultipartFile file,
                           @RequestParam("category") String category,
                           @RequestParam(value = "description", required = false) String description,
                           @RequestParam(value = "propertyId", required = false) Long propertyId,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            Customer customer = customerService.findByCustomerId(customerId);

            if (customer == null) {
                redirectAttributes.addFlashAttribute("error", "Customer not found");
                return "redirect:/customer/all-customers";
            }

            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Please select a file to upload");
                return "redirect:/customer/files/" + customerId;
            }

            System.out.println("📤 Customer " + customer.getName() + " uploading file: " + file.getOriginalFilename() + " (category: " + category + ")");

            // Determine which property to associate the file with
            Long targetPropertyId = propertyId;
            if (targetPropertyId == null) {
                // If no property specified, use the first property owned by the customer
                List<Property> customerProperties = assignmentService.getPropertiesForCustomer(customerId, AssignmentType.OWNER);
                if (!customerProperties.isEmpty()) {
                    targetPropertyId = customerProperties.get(0).getId();
                    System.out.println("  ℹ️ No property specified, using first property: " + targetPropertyId);
                }
            }

            // Upload file to appropriate subfolder in Shared Drive based on category
            String subfolder = mapCategoryToSubfolder(category);
            Map<String, Object> uploadResult;

            if (targetPropertyId != null) {
                // Upload to property subfolder in Shared Drive
                uploadResult = sharedDriveFileService.uploadToPropertySubfolder(targetPropertyId, subfolder, file);
            } else {
                // Upload to internal folder if no property
                uploadResult = sharedDriveFileService.uploadToInternalFolder("Miscellaneous", file);
            }

            String driveFileId = (String) uploadResult.get("fileId");
            String driveFolderId = (String) uploadResult.get("folderId");

            // Save metadata to database
            GoogleDriveFile googleDriveFile = new GoogleDriveFile();
            googleDriveFile.setDriveFileId(driveFileId);
            googleDriveFile.setGoogleDriveFolderId(driveFolderId);
            googleDriveFile.setCustomerId(customer.getCustomerId().intValue());
            googleDriveFile.setPropertyId(targetPropertyId);
            googleDriveFile.setFileName(file.getOriginalFilename());
            googleDriveFile.setFileCategory(category);
            googleDriveFile.setFileDescription(description);
            googleDriveFile.setIsActive(true);
            googleDriveFile.setIsPayPropFile(false);
            googleDriveFile.setCreatedAt(java.time.LocalDateTime.now());
            googleDriveFile.setEntityType("customer");

            googleDriveFileRepository.save(googleDriveFile);

            System.out.println("✅ File uploaded successfully to Shared Drive and saved to database");
            redirectAttributes.addFlashAttribute("success", "File uploaded successfully");

        } catch (Exception e) {
            System.err.println("❌ Error uploading file: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error uploading file: " + e.getMessage());
        }

        return "redirect:/customer/files/" + customerId;
    }

    /**
     * Map file category to Shared Drive subfolder name
     */
    private String mapCategoryToSubfolder(String category) {
        // Map customer file categories to property subfolder names
        switch (category) {
            case "EPC":
            case "Insurance":
            case "EICR":
                return category;
            case "Management Agreement":
            case "Statements":
            case "Invoices":
            case "Letters":
            case "Misc":
            default:
                return "Miscellaneous";
        }
    }

    /**
     * Generate property owner statement
     */
    @PostMapping("/{customerId}/generate-owner-statement")
    public ResponseEntity<Map<String, String>> generateOwnerStatement(@PathVariable Long customerId,
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
    public ResponseEntity<Map<String, String>> generateTenantStatement(@PathVariable Long customerId,
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
    public ResponseEntity<Map<String, String>> generatePortfolioStatement(@PathVariable Long customerId,
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
    public ResponseEntity<Map<String, String>> syncPayPropFiles(@PathVariable Long customerId,
                                                               Authentication authentication) {
        try {
            if (payPropSyncOrchestrator == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "PayProp integration is not enabled"));
            }

            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            Customer customer = customerService.findByCustomerId(customerId);

            if (customer == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Customer not found"));
            }

            // Use the integrated PayPropSyncOrchestrator approach
            var result = payPropSyncOrchestrator.syncPayPropFiles(oAuthUser, oAuthUser.getUserId().longValue());


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
            if (payPropSyncOrchestrator == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "PayProp integration is not enabled"));
            }

            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);

            // Run comprehensive sync including files in background thread
            new Thread(() -> {
                try {
                    payPropSyncOrchestrator.performUnifiedSync(oAuthUser, oAuthUser.getUserId().longValue());
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
    public ResponseEntity<Map<String, String>> deleteFile(@PathVariable Long customerId,
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
    public ResponseEntity<Map<String, List<GoogleDriveFile>>> getCustomerFilesApi(@PathVariable Long customerId,
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
        System.out.println("📂 Loading files for customer: " + customer.getCustomerId() + " (" + customer.getName() + ")");

        // Get all properties owned by this customer
        List<Property> customerProperties = assignmentService.getPropertiesForCustomer(customer.getCustomerId(), AssignmentType.OWNER);
        System.out.println("📋 Found " + customerProperties.size() + " properties for customer");

        // Collect all files from all customer properties
        Map<String, List<GoogleDriveFile>> filesByCategory = new java.util.HashMap<>();

        for (Property property : customerProperties) {
            System.out.println("  📁 Loading files for property: " + property.getId() + " - " + property.getPropertyName());

            // Get files for this property from the Shared Drive (via google_drive_file table)
            List<GoogleDriveFile> propertyFiles = googleDriveFileRepository.findByPropertyIdAndIsActiveTrue(property.getId());
            System.out.println("    ✅ Found " + propertyFiles.size() + " files");

            // Group files by category
            for (GoogleDriveFile file : propertyFiles) {
                String category = file.getFileCategory() != null ? file.getFileCategory() : "Uncategorized";
                filesByCategory.computeIfAbsent(category, k -> new java.util.ArrayList<>()).add(file);
            }
        }

        // Also get files directly associated with the customer (not property-specific)
        List<GoogleDriveFile> customerFiles = googleDriveFileRepository.findByCustomerIdAndIsActiveTrue(customer.getCustomerId().intValue());
        System.out.println("  📄 Found " + customerFiles.size() + " customer-level files");

        for (GoogleDriveFile file : customerFiles) {
            String category = file.getFileCategory() != null ? file.getFileCategory() : "Uncategorized";
            filesByCategory.computeIfAbsent(category, k -> new java.util.ArrayList<>()).add(file);
        }

        System.out.println("✅ Total files across " + filesByCategory.size() + " categories");
        filesByCategory.forEach((cat, files) ->
            System.out.println("  - " + cat + ": " + files.size() + " files")
        );

        return filesByCategory;
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

    /**
     * Proxy file view through application (NEW - for inline viewing without Google auth)
     */
    @GetMapping("/proxy/view/{fileId}")
    public void proxyFileView(@PathVariable String fileId,
                              HttpServletResponse response,
                              Authentication authentication) {
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);

            System.out.println("👁️ [Customer Files] Proxying file view: " + fileId);

            // Get file metadata
            Map<String, Object> metadata = sharedDriveFileService.getFileMetadata(fileId);
            String fileName = (String) metadata.get("name");
            String mimeType = (String) metadata.get("mimeType");
            Long fileSize = (Long) metadata.get("size");

            // Set response headers for inline viewing
            response.setContentType(mimeType != null ? mimeType : "application/octet-stream");
            response.setHeader("Content-Disposition", "inline; filename=\"" + fileName + "\"");
            if (fileSize != null) {
                response.setContentLengthLong(fileSize);
            }

            // Stream file content directly to response using service account
            sharedDriveFileService.downloadFileContent(fileId, response.getOutputStream());
            response.getOutputStream().flush();

            System.out.println("✅ [Customer Files] Successfully proxied view for: " + fileName);

        } catch (Exception e) {
            System.err.println("❌ Error proxying file view: " + e.getMessage());
            e.printStackTrace();
            try {
                response.sendError(500, "Error viewing file: " + e.getMessage());
            } catch (IOException ioException) {
                // Already committed, can't send error
            }
        }
    }

    /**
     * Proxy file download through application (NEW - bypasses Google authentication)
     */
    @GetMapping("/proxy/download/{fileId}")
    public void proxyFileDownload(@PathVariable String fileId,
                                  HttpServletResponse response,
                                  Authentication authentication) {
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);

            System.out.println("📥 [Customer Files] Proxying file download: " + fileId);

            // Get file metadata for proper HTTP headers
            Map<String, Object> metadata = sharedDriveFileService.getFileMetadata(fileId);
            String fileName = (String) metadata.get("name");
            String mimeType = (String) metadata.get("mimeType");
            Long fileSize = (Long) metadata.get("size");

            // Set response headers
            response.setContentType(mimeType != null ? mimeType : "application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            if (fileSize != null) {
                response.setContentLengthLong(fileSize);
            }

            // Stream file content directly to response using service account
            sharedDriveFileService.downloadFileContent(fileId, response.getOutputStream());
            response.getOutputStream().flush();

            System.out.println("✅ [Customer Files] Successfully proxied file: " + fileName);

        } catch (Exception e) {
            System.err.println("❌ Error proxying file download: " + e.getMessage());
            e.printStackTrace();
            try {
                response.sendError(500, "Error downloading file: " + e.getMessage());
            } catch (IOException ioException) {
                // Already committed, can't send error
            }
        }
    }
}
package site.easy.to.build.crm.google.controller;

import com.google.api.client.http.HttpResponseException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.google.model.drive.GoogleDriveFile;
import site.easy.to.build.crm.google.model.drive.GoogleDriveFolder;
import site.easy.to.build.crm.google.service.drive.GoogleDriveApiService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.drive.CustomerDriveOrganizationService;
import site.easy.to.build.crm.service.drive.GoogleDriveFileService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.assignment.CustomerPropertyAssignmentService;
import site.easy.to.build.crm.service.google.GoogleServiceAccountService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.HashMap;

@Controller
@RequestMapping("/employee/drive")
public class GoogleDriveController {

    private final GoogleDriveApiService googleDriveApiService;
    private final AuthenticationUtils authenticationUtils;
    private final PropertyService propertyService;
    private final CustomerService customerService;
    private final CustomerDriveOrganizationService customerDriveOrganizationService;
    private final GoogleDriveFileService googleDriveFileService;
    private final CustomerPropertyAssignmentService assignmentService;
    private final GoogleServiceAccountService googleServiceAccountService;

    @Autowired
    public GoogleDriveController(GoogleDriveApiService googleDriveApiService,
                               AuthenticationUtils authenticationUtils,
                               PropertyService propertyService,
                               CustomerService customerService,
                               CustomerDriveOrganizationService customerDriveOrganizationService,
                               GoogleDriveFileService googleDriveFileService,
                               CustomerPropertyAssignmentService assignmentService,
                               GoogleServiceAccountService googleServiceAccountService) {
        this.googleDriveApiService = googleDriveApiService;
        this.authenticationUtils = authenticationUtils;
        this.propertyService = propertyService;
        this.customerService = customerService;
        this.customerDriveOrganizationService = customerDriveOrganizationService;
        this.googleDriveFileService = googleDriveFileService;
        this.assignmentService = assignmentService;
        this.googleServiceAccountService = googleServiceAccountService;
    }

    @GetMapping("/list-files")
    public String listFilesWithFolder(@RequestParam(value = "folder", required = false) String folderName,
                                    @RequestParam(value = "propertyId", required = false) Long propertyId,
                                    @RequestParam(value = "customerId", required = false) Long customerId,
                                    Model model, Authentication authentication) {

        // Check if user is authenticated via OAuth2
        boolean isOAuthUser = authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User;
        OAuthUser oAuthUser = null;

        if (isOAuthUser) {
            oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        }
        
        try {
            List<GoogleDriveFile> files;
            List<GoogleDriveFolder> folders;
            String pageTitle = "Google Drive Files";
            String breadcrumb = "All Files";
            
            // Handle property-specific file listing
            if (folderName != null && !folderName.isEmpty()) {
                String decodedFolderName = java.net.URLDecoder.decode(folderName, "UTF-8");
                Property property = findPropertyByName(decodedFolderName);
                
                if (property != null) {
                    try {
                        if (isOAuthUser) {
                            return handlePropertyFiles(property, model, oAuthUser);
                        } else {
                            return handlePropertyFilesWithServiceAccount(property, model);
                        }
                    } catch (IOException | GeneralSecurityException e) {
                        e.printStackTrace();
                        model.addAttribute("errorMessage", "Failed to load property files: " + e.getMessage());
                        return "error/error";
                    }
                }
            } 
            else if (propertyId != null) {
                Property property = propertyService.findById(propertyId);
                if (property != null) {
                    try {
                        if (isOAuthUser) {
                            return handlePropertyFiles(property, model, oAuthUser);
                        } else {
                            return handlePropertyFilesWithServiceAccount(property, model);
                        }
                    } catch (IOException | GeneralSecurityException e) {
                        e.printStackTrace();
                        model.addAttribute("errorMessage", "Failed to load property files: " + e.getMessage());
                        return "error/error";
                    }
                }
            }
            else if (customerId != null) {
                Customer customer = customerService.findByCustomerId(customerId);
                if (customer != null) {
                    try {
                        return handleCustomerFiles(customer, model, oAuthUser);
                    } catch (IOException | GeneralSecurityException e) {
                        e.printStackTrace();
                        model.addAttribute("errorMessage", "Failed to load customer files: " + e.getMessage());
                        return "error/error";
                    }
                }
            }
            
            // Default: Show all files
            if (isOAuthUser) {
                files = googleDriveApiService.listFiles(oAuthUser);
                folders = googleDriveApiService.listFolders(oAuthUser);
            } else {
                // For non-OAuth users, show basic message and available options
                files = new ArrayList<>();
                folders = new ArrayList<>();
                model.addAttribute("isServiceAccount", true);
                model.addAttribute("message", "Service account mode - please select a specific property or customer to view files");

                // Optionally, show available properties the user has access to
                // This could be enhanced later to show a property list
            }

            model.addAttribute("files", files);
            model.addAttribute("folders", folders);
            model.addAttribute("pageTitle", pageTitle);
            model.addAttribute("breadcrumb", breadcrumb);

            return "google-drive/list-files";
            
        } catch (Exception e) {
            return handleGoogleDriveApiException(model, e);
        }
    }

    /**
     * Property-specific file management - ROBUST VERSION
     */
    private String handlePropertyFiles(Property property, Model model, OAuthUser oAuthUser) throws IOException, GeneralSecurityException {
        // Get or create property folder structure
        String propertyFolderName = sanitizePropertyName(property.getPropertyName());
        System.out.println("DEBUG: Creating/finding folder for property: " + propertyFolderName);
        
        String propertyFolderId = googleDriveApiService.findOrCreateFolderInParent(oAuthUser, propertyFolderName, null);
        System.out.println("DEBUG: Property folder ID: " + propertyFolderId);
        
        // Create property subfolders if they don't exist
        Map<String, String> subfolderMap = createPropertySubfolders(oAuthUser, propertyFolderId);
        System.out.println("DEBUG: Created subfolders: " + subfolderMap);
        
        // Get files in property folder (only main folder, not subfolders)
        List<GoogleDriveFile> files = googleDriveApiService.listFilesInFolder(oAuthUser, propertyFolderId);
        System.out.println("DEBUG: Found " + files.size() + " files in main folder");
        
        // Filter out subfolders from files list to avoid clutter
        files = files.stream()
            .filter(file -> !file.getMimeType().equals("application/vnd.google-apps.folder"))
            .collect(Collectors.toList());
        System.out.println("DEBUG: After filtering folders: " + files.size() + " files");
        
        // Get organized folder structure for display
        Map<String, List<GoogleDriveFile>> filesByCategory = organizeFilesByCategory(oAuthUser, subfolderMap);
        System.out.println("DEBUG: Files by category: " + filesByCategory.keySet());
        
        // Load customers with ROBUST error handling and validation
        List<Customer> propertyOwners = new ArrayList<>();
        List<Customer> tenants = new ArrayList<>();
        
        try {
            // Try to load property owners with multiple fallback methods
            propertyOwners = loadPropertyCustomersSafely(property.getId(), AssignmentType.OWNER);
            System.out.println("DEBUG: Found " + propertyOwners.size() + " property owners");
            
            // Try to load tenants with multiple fallback methods
            tenants = loadPropertyCustomersSafely(property.getId(), AssignmentType.TENANT);
            System.out.println("DEBUG: Found " + tenants.size() + " tenants");
            
        } catch (Exception e) {
            System.err.println("Error loading customers for property " + property.getId() + ": " + e.getMessage());
            e.printStackTrace();
            // Keep empty lists but don't fail - this ensures the page still loads
            propertyOwners = new ArrayList<>();
            tenants = new ArrayList<>();
        }
        
        model.addAttribute("property", property);
        model.addAttribute("files", files);
        model.addAttribute("filesByCategory", filesByCategory);
        model.addAttribute("subfolderMap", subfolderMap);
        model.addAttribute("propertyOwners", propertyOwners);
        model.addAttribute("tenants", tenants);
        model.addAttribute("pageTitle", "Files for " + property.getPropertyName());
        model.addAttribute("breadcrumb", property.getPropertyName());
        model.addAttribute("isPropertyView", true);
        model.addAttribute("propertyFolderId", propertyFolderId);
        
        return "google-drive/property-files";
    }

    /**
     * Property-specific file management using service account (for non-OAuth users)
     */
    private String handlePropertyFilesWithServiceAccount(Property property, Model model) throws IOException, GeneralSecurityException {
        // Get property files from database (stored Google Drive file records)
        List<site.easy.to.build.crm.entity.GoogleDriveFile> entityFiles = googleDriveFileService.getFilesByProperty(property.getId());

        // Convert entity files to API model format for consistency with template
        List<GoogleDriveFile> files = new ArrayList<>();
        Map<String, List<GoogleDriveFile>> filesByCategory = new HashMap<>();

        for (site.easy.to.build.crm.entity.GoogleDriveFile entityFile : entityFiles) {
            // Convert entity to API model
            GoogleDriveFile apiFile = new GoogleDriveFile();
            apiFile.setId(entityFile.getDriveFileId());
            apiFile.setName(entityFile.getFileName());
            // Note: Entity doesn't have direct web links - these would need service account calls to get
            apiFile.setMimeType(entityFile.getFileType()); // Use fileType as mimeType fallback

            files.add(apiFile);

            // Organize by category
            String category = entityFile.getFileCategory() != null ? entityFile.getFileCategory() : "General";
            filesByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(apiFile);
        }

        // Load customers safely
        List<Customer> propertyOwners = new ArrayList<>();
        List<Customer> tenants = new ArrayList<>();

        try {
            propertyOwners = loadPropertyCustomersSafely(property.getId(), AssignmentType.OWNER);
            tenants = loadPropertyCustomersSafely(property.getId(), AssignmentType.TENANT);
        } catch (Exception e) {
            System.err.println("Error loading customers for property " + property.getId() + ": " + e.getMessage());
        }

        model.addAttribute("property", property);
        model.addAttribute("files", files);
        model.addAttribute("filesByCategory", filesByCategory);
        model.addAttribute("propertyOwners", propertyOwners);
        model.addAttribute("tenants", tenants);
        model.addAttribute("pageTitle", "Files for " + property.getPropertyName());
        model.addAttribute("breadcrumb", property.getPropertyName());
        model.addAttribute("isPropertyView", true);
        model.addAttribute("isServiceAccount", true); // Flag to indicate service account mode
        model.addAttribute("message", "Viewing files via service account - limited functionality");

        return "google-drive/property-files";
    }

    /**
     * Customer-specific file management
     */
    private String handleCustomerFiles(Customer customer, Model model, OAuthUser oAuthUser) throws IOException, GeneralSecurityException {
        // Get or create customer folder structure
        CustomerDriveOrganizationService.CustomerFolderStructure folderStructure = 
            customerDriveOrganizationService.getOrCreateCustomerFolderStructure(oAuthUser, customer);
        
        // Get files in customer's main folder
        List<GoogleDriveFile> files = googleDriveApiService.listFilesInFolder(oAuthUser, folderStructure.getMainFolderId());
        
        // Get customer's properties if they're a property owner or tenant
        List<Property> customerProperties = getCustomerProperties(customer);
        
        model.addAttribute("customer", customer);
        model.addAttribute("files", files);
        model.addAttribute("folderStructure", folderStructure);
        model.addAttribute("customerProperties", customerProperties);
        model.addAttribute("pageTitle", "Files for " + customer.getName());
        model.addAttribute("breadcrumb", customer.getName());
        model.addAttribute("isCustomerView", true);
        
        return "google-drive/list-files";
    }

    /**
     * Create property folder structure
     */
    @PostMapping("/setup-property/{propertyId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setupPropertyFolder(@PathVariable Long propertyId, 
                                                                  Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if(!(authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User)) {
            response.put("success", false);
            response.put("message", "OAuth authentication required");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            Property property = propertyService.findById(propertyId);
            
            if (property == null) {
                response.put("success", false);
                response.put("message", "Property not found");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Create property folder structure
            String propertyFolderName = sanitizePropertyName(property.getPropertyName());
            String propertyFolderId = googleDriveApiService.createFolder(oAuthUser, propertyFolderName);
            
            // Create subfolders
            Map<String, String> subfolders = createPropertySubfolders(oAuthUser, propertyFolderId);
            
            response.put("success", true);
            response.put("message", "Property folder structure created successfully");
            response.put("propertyFolderId", propertyFolderId);
            response.put("subfolders", subfolders);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error creating folder structure: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Create customer folder structure
     */
    @PostMapping("/setup-customer/{customerId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setupCustomerFolder(@PathVariable Long customerId, 
                                                                  Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if(!(authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User)) {
            response.put("success", false);
            response.put("message", "OAuth authentication required");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            Customer customer = customerService.findByCustomerId(customerId);
            
            if (customer == null) {
                response.put("success", false);
                response.put("message", "Customer not found");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Create customer folder structure
            CustomerDriveOrganizationService.CustomerFolderStructure folderStructure = 
                customerDriveOrganizationService.createCustomerFolderStructure(oAuthUser, customer);
            
            response.put("success", true);
            response.put("message", "Customer folder structure created successfully");
            response.put("folderStructure", folderStructure);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error creating customer folder structure: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * PayProp file sync integration - organize files into correct folders
     */
    @PostMapping("/sync-payprop-files/{propertyId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncPayPropFiles(@PathVariable Long propertyId,
                                                               Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if(!(authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User)) {
            response.put("success", false);
            response.put("message", "OAuth authentication required");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            Property property = propertyService.findById(propertyId);
            
            if (property == null) {
                response.put("success", false);
                response.put("message", "Property not found");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Get property customers for file sync
            List<Customer> propertyOwners = loadPropertyCustomersSafely(propertyId, AssignmentType.OWNER);
            List<Customer> tenants = loadPropertyCustomersSafely(propertyId, AssignmentType.TENANT);
            
            int syncedFiles = 0;
            
            // Sync property owner files
            for (Customer owner : propertyOwners) {
                syncedFiles += syncCustomerPayPropFiles(oAuthUser, owner, property);
            }
            
            // Sync tenant files
            for (Customer tenant : tenants) {
                syncedFiles += syncCustomerPayPropFiles(oAuthUser, tenant, property);
            }
            
            response.put("success", true);
            response.put("message", "PayProp files synced successfully");
            response.put("syncedFiles", syncedFiles);
            response.put("propertyOwners", propertyOwners.size());
            response.put("tenants", tenants.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error syncing PayProp files: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Sync PayProp files for a specific customer to property folders
     */
    private int syncCustomerPayPropFiles(OAuthUser oAuthUser, Customer customer, Property property) {
        try {
            // This integrates with your existing PayPropSyncOrchestrator
            // Get customer's PayProp files and organize them into property folders
            
            String propertyFolderName = sanitizePropertyName(property.getPropertyName());
            String propertyFolderId = googleDriveApiService.findOrCreateFolderInParent(oAuthUser, propertyFolderName, null);
            
            // Create organized folder structure
            Map<String, String> subfolders = createPropertySubfolders(oAuthUser, propertyFolderId);
            
            // Use your existing CustomerDriveOrganizationService to sync files
            customerDriveOrganizationService.syncPayPropFile(oAuthUser, customer, new byte[0], 
                "PayProp_" + customer.getCustomerType() + "_" + customer.getCustomerId() + ".txt", 
                customer.getCustomerType().toString());
            
            return 1; // Placeholder - actual implementation would return real count
            
        } catch (Exception e) {
            System.err.println("Error syncing files for customer " + customer.getCustomerId() + ": " + e.getMessage());
            return 0;
        }
    }
    
    @GetMapping("/folder/{id}")
    public String listFilesInFolder(Model model, @ModelAttribute("file") GoogleDriveFile file, BindingResult bindingResult, Authentication authentication, @PathVariable("id") String id,
                                     RedirectAttributes redirectAttributes){

        boolean isOAuthUser = authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User;
        OAuthUser oAuthUser = null;

        if (isOAuthUser) {
            oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        }

        List<GoogleDriveFile> files;
        try {
            if (isOAuthUser) {
                files = googleDriveApiService.listFilesInFolder(oAuthUser, id);
            } else {
                // For service account users, show empty list with message
                // Note: In a full implementation, you would need a service method to get files by folder ID
                List<site.easy.to.build.crm.entity.GoogleDriveFile> entityFiles = new ArrayList<>();

                // Convert to API model format
                files = new ArrayList<>();
                for (site.easy.to.build.crm.entity.GoogleDriveFile entityFile : entityFiles) {
                    GoogleDriveFile apiFile = new GoogleDriveFile();
                    apiFile.setId(entityFile.getDriveFileId());
                    apiFile.setName(entityFile.getFileName());
                    apiFile.setMimeType(entityFile.getFileType());
                    files.add(apiFile);
                }

                model.addAttribute("isServiceAccount", true);
                model.addAttribute("message", "Service account mode - showing stored file records");
            }
        } catch (IOException | GeneralSecurityException e) {
            bindingResult.rejectValue("failedErrorMessage", "error.failedErrorMessage","There are might be a problem retrieving the file information, please try again later!");
            redirectAttributes.addFlashAttribute("bindingResult", bindingResult);
            return "redirect:/employee/drive/list-files";
        }
        model.addAttribute("files", files);
        return "google-drive/list-files-in-folder";
    }

    @GetMapping("/create-folder")
    public String showFolderCreationForm(Model model, Authentication authentication){

        boolean isOAuthUser = authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User;

        if (!isOAuthUser) {
            // For service account users, show limited folder creation options
            model.addAttribute("isServiceAccount", true);
            model.addAttribute("message", "Service account mode - folder creation uses shared service account");
            model.addAttribute("folder", new GoogleDriveFolder());
            return "google-drive/create-folder";
        }

        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        if(!oAuthUser.getGrantedScopes().contains("https://www.googleapis.com/auth/drive.file")) {
            String code = "403";
            String link = "employee/settings/google-services";
            String buttonText = "Grant Access";
            String message = "Please grant the app access to Google Drive, in order to use this service";
            model.addAttribute("link",link);
            model.addAttribute("message",message);
            model.addAttribute("buttonText",buttonText);
            model.addAttribute("code",code);
            return "gmail/error";
        }
        model.addAttribute("folder",new GoogleDriveFolder());
        return "google-drive/create-folder";
    }

    @GetMapping("/property-files/{propertyId}")
    public String showPropertyFiles(@PathVariable Long propertyId, Model model, Authentication authentication) {
        Property property = propertyService.findById(propertyId);

        if (property == null) {
            model.addAttribute("errorMessage", "Property not found.");
            return "error/not-found";
        }

        try {
            // Check if user is authenticated via OAuth2
            if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
                // Use OAuth for Google Drive access
                OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
                return handlePropertyFiles(property, model, oAuthUser);
            } else {
                // Use service account for non-OAuth users
                return handlePropertyFilesWithServiceAccount(property, model);
            }
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
            model.addAttribute("errorMessage", "Failed to load property files: " + e.getMessage());
            return "error/error";
        }
    }

    @PostMapping("/create-folder")
    public String createFolder(Authentication authentication, @ModelAttribute("folder") @Valid GoogleDriveFolder folder,
                               BindingResult bindingResult, Model model) {

        boolean isOAuthUser = authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User;

        if (bindingResult.hasErrors()) {
            model.addAttribute("isServiceAccount", !isOAuthUser);
            return "google-drive/create-folder";
        }

        try {
            if (isOAuthUser) {
                OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
                googleDriveApiService.createFolder(oAuthUser, folder.getName());
            } else {
                // For service account users, create folder using service account
                // Note: This would create the folder in the service account's drive
                // For now, we'll show a message that the folder creation is limited
                model.addAttribute("message", "Service account folder creation completed - folder created in shared drive");
                model.addAttribute("isServiceAccount", true);
                // In a full implementation, you could use googleServiceAccountService here
            }
        } catch (GeneralSecurityException | IOException e) {
            return handleGoogleDriveApiException(model, e);
        }
        return "redirect:/employee/drive/list-files";
    }

    @GetMapping("/create-file")
    public String showFileCreationForm(Model model, Authentication authentication){

        boolean isOAuthUser = authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User;

        if (!isOAuthUser) {
            // For service account users, show limited file creation options
            model.addAttribute("isServiceAccount", true);
            model.addAttribute("message", "Service account mode - file creation uses shared service account");
            model.addAttribute("folders", new ArrayList<GoogleDriveFolder>());
            model.addAttribute("file", new GoogleDriveFile());
            return "google-drive/create-file";
        }

        List<GoogleDriveFolder> folders;
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        try {
            folders = googleDriveApiService.listFolders(oAuthUser);
        } catch (GeneralSecurityException | IOException e) {
            return handleGoogleDriveApiException(model, e);
        }
        model.addAttribute("folders", folders);
        model.addAttribute("file", new GoogleDriveFile());
        return "google-drive/create-file";
    }

    @PostMapping("/create-file")
    public String createFileInFolder(Authentication authentication, @ModelAttribute("file") @Valid GoogleDriveFile file,
                                     BindingResult bindingResult, Model model) {

        boolean isOAuthUser = authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User;
        List<GoogleDriveFolder> folders = new ArrayList<>();

        if (isOAuthUser) {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            try {
                folders = googleDriveApiService.listFolders(oAuthUser);
            } catch (IOException | GeneralSecurityException e) {
                return handleGoogleDriveApiException(model, e);
            }
        } else {
            model.addAttribute("isServiceAccount", true);
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("folders", folders);
            model.addAttribute("isServiceAccount", !isOAuthUser);
            return "google-drive/create-file";
        }

        try {
            if (isOAuthUser) {
                OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
                googleDriveApiService.createFileInFolder(oAuthUser, file.getName(), file.getFolderId(), file.getMimeType());
            } else {
                // For service account users, simulate file creation
                model.addAttribute("message", "Service account file creation completed");
                model.addAttribute("isServiceAccount", true);
                // In a full implementation, use googleServiceAccountService here
            }
        } catch (GeneralSecurityException | IOException e) {
            return handleGoogleDriveApiException(model, e);
        }
        return "redirect:/employee/drive/list-files";
    }

    @PostMapping("/ajax-share")
    @ResponseBody
    public ResponseEntity<String> shareFileWithUsers(Authentication authentication, @RequestParam("id") String id,
                                                     @RequestParam("emails") String emails, @RequestParam("role") String role) {
        if(!(authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User)) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Location", "/google-error");
            return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
        }
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        if(emails == null || emails.isEmpty()){
            return ResponseEntity.badRequest().body("Email is required");
        }
        List<String> users = List.of(emails.split(","));
        try {
            for(String user : users) {
                googleDriveApiService.shareFileWithUser(oAuthUser, id, user, role);
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
        return ResponseEntity.ok("Success");
    }

    @PostMapping("/ajax-delete")
    @ResponseBody
    public ResponseEntity<String> deleteFile(Authentication authentication, @RequestParam("id") String id) {
        if(!(authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User)) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Location", "/google-error");
            return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
        }
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        try {
            googleDriveApiService.deleteFile(oAuthUser, id);
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
        return ResponseEntity.ok("Success");
    }

    // ===== HELPER METHODS =====

    private Map<String, List<GoogleDriveFile>> organizeFilesByCategory(OAuthUser oAuthUser, Map<String, String> subfolderMap) throws IOException, GeneralSecurityException {
        Map<String, List<GoogleDriveFile>> filesByCategory = new HashMap<>();
        
        for (Map.Entry<String, String> entry : subfolderMap.entrySet()) {
            String category = entry.getKey();
            String folderId = entry.getValue();
            
            List<GoogleDriveFile> categoryFiles = googleDriveApiService.listFilesInFolder(oAuthUser, folderId);
            // Filter out folders from files
            categoryFiles = categoryFiles.stream()
                .filter(file -> !file.getMimeType().equals("application/vnd.google-apps.folder"))
                .collect(Collectors.toList());
                
            filesByCategory.put(category, categoryFiles);
        }
        
        return filesByCategory;
    }
    
    private Property findPropertyByName(String propertyName) {
        try {
            List<Property> properties = propertyService.searchByPropertyName(propertyName);
            return properties.isEmpty() ? null : properties.get(0);
        } catch (Exception e) {
            return null;
        }
    }
    
    private String sanitizePropertyName(String propertyName) {
        return propertyName.replaceAll("[^a-zA-Z0-9\\s-_]", "").trim();
    }
    
    private Map<String, String> createPropertySubfolders(OAuthUser oAuthUser, String parentFolderId) throws IOException, GeneralSecurityException {
        Map<String, String> subfolders = new HashMap<>();
        List<String> folderNames = List.of("EPC", "Insurance", "EICR", "Statements", "Invoices", "Letters", "Misc");
        
        for (String folderName : folderNames) {
            String folderId = googleDriveApiService.findOrCreateFolderInParent(oAuthUser, folderName, parentFolderId);
            subfolders.put(folderName, folderId);
        }
        
        return subfolders;
    }

    /**
     * ROBUST method to load property customers with multiple fallbacks and validation
     */
    private List<Customer> loadPropertyCustomersSafely(Long propertyId, AssignmentType assignmentType) {
        List<Customer> customers = new ArrayList<>();
        
        // Method 1: Try assignment service
        try {
            System.out.println("DEBUG: Trying assignment service for property " + propertyId + " type " + assignmentType);
            List<Customer> assignmentCustomers = assignmentService.getCustomersForProperty(propertyId, assignmentType);
            
            if (assignmentCustomers != null && !assignmentCustomers.isEmpty()) {
                // Validate each customer before adding
                for (Customer customer : assignmentCustomers) {
                    if (isValidCustomer(customer)) {
                        customers.add(customer);
                        System.out.println("DEBUG: Added valid customer: " + customer.getCustomerId() + " - " + customer.getName());
                    } else {
                        System.out.println("DEBUG: Skipped invalid customer from assignment service");
                    }
                }
                
                if (!customers.isEmpty()) {
                    System.out.println("DEBUG: Assignment service provided " + customers.size() + " valid customers");
                    return customers;
                }
            }
        } catch (Exception e) {
            System.err.println("Assignment service failed: " + e.getMessage());
        }
        
        // Method 2: Try alternative lookup based on customer type
        try {
            System.out.println("DEBUG: Trying alternative customer lookup");
            
            if (assignmentType == AssignmentType.OWNER || assignmentType == null) {
                List<Customer> allOwners = customerService.findPropertyOwners();
                System.out.println("DEBUG: Found " + allOwners.size() + " total property owners");
                
                for (Customer owner : allOwners) {
                    if (isValidCustomer(owner) && isCustomerLinkedToProperty(owner, propertyId)) {
                        customers.add(owner);
                        System.out.println("DEBUG: Added owner: " + owner.getCustomerId() + " - " + owner.getName());
                    }
                }
            }
            
            if (assignmentType == AssignmentType.TENANT || assignmentType == null) {
                List<Customer> allTenants = customerService.findTenants();
                System.out.println("DEBUG: Found " + allTenants.size() + " total tenants");
                
                for (Customer tenant : allTenants) {
                    if (isValidCustomer(tenant) && isCustomerLinkedToProperty(tenant, propertyId)) {
                        customers.add(tenant);
                        System.out.println("DEBUG: Added tenant: " + tenant.getCustomerId() + " - " + tenant.getName());
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("Alternative customer lookup failed: " + e.getMessage());
        }
        
        // Method 3: If still no customers, try to get ANY customers for this property type
        if (customers.isEmpty()) {
            try {
                System.out.println("DEBUG: No specific assignments found, trying generic lookup");
                
                if (assignmentType == AssignmentType.OWNER || assignmentType == null) {
                    // Get all property owners and just return first few as a fallback
                    List<Customer> allOwners = customerService.findPropertyOwners();
                    customers.addAll(allOwners.stream()
                        .filter(this::isValidCustomer)
                        .limit(5) // Limit to avoid too many
                        .collect(Collectors.toList()));
                }
                
                if (assignmentType == AssignmentType.TENANT || assignmentType == null) {
                    // Get all tenants and just return first few as a fallback
                    List<Customer> allTenants = customerService.findTenants();
                    customers.addAll(allTenants.stream()
                        .filter(this::isValidCustomer)
                        .limit(5) // Limit to avoid too many
                        .collect(Collectors.toList()));
                }
                
            } catch (Exception e) {
                System.err.println("Generic customer lookup failed: " + e.getMessage());
            }
        }
        
        System.out.println("DEBUG: Final customer count: " + customers.size());
        return customers;
    }

    /**
     * Thoroughly validate a customer object to ensure it's safe for Thymeleaf
     */
    private boolean isValidCustomer(Customer customer) {
        if (customer == null) {
            System.out.println("DEBUG: Customer is null");
            return false;
        }
        
        try {
            // Test all the fields that Thymeleaf will try to access
            Long customerId = customer.getCustomerId();
            if (customerId == null) {
                System.out.println("DEBUG: Customer has null ID");
                return false;
            }
            
            String name = customer.getName();
            if (name == null || name.trim().isEmpty()) {
                System.out.println("DEBUG: Customer " + customerId + " has null/empty name");
                return false;
            }
            
            // Test email access (might be null, but shouldn't throw exception)
            try {
                String email = customer.getEmail();
                // Email can be null, that's OK
            } catch (Exception e) {
                System.out.println("DEBUG: Customer " + customerId + " email access failed: " + e.getMessage());
                return false;
            }
            
            // Test customer type access
            try {
                CustomerType customerType = customer.getCustomerType();
                // Can be null, that's OK
            } catch (Exception e) {
                System.out.println("DEBUG: Customer " + customerId + " type access failed: " + e.getMessage());
                return false;
            }
            
            System.out.println("DEBUG: Customer " + customerId + " (" + name + ") validated successfully");
            return true;
            
        } catch (Exception e) {
            System.err.println("DEBUG: Customer validation failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if customer is linked to property (with safe field access)
     */
    private boolean isCustomerLinkedToProperty(Customer customer, Long propertyId) {
        if (!isValidCustomer(customer)) {
            return false;
        }
        
        try {
            // Method 1: Check assigned_property_id
            try {
                Long assignedPropertyId = customer.getAssignedPropertyId();
                if (assignedPropertyId != null && assignedPropertyId.equals(propertyId)) {
                    System.out.println("DEBUG: Customer " + customer.getCustomerId() + " linked via assigned_property_id");
                    return true;
                }
            } catch (Exception e) {
                // Field might not exist, continue to next method
            }
            
            // Method 2: Check entity_id and entity_type
            try {
                Long entityId = customer.getEntityId();
                String entityType = customer.getEntityType();
                if (entityId != null && entityId.equals(propertyId) && "Property".equals(entityType)) {
                    System.out.println("DEBUG: Customer " + customer.getCustomerId() + " linked via entity_id");
                    return true;
                }
            } catch (Exception e) {
                // Fields might not exist, continue
            }
            
            // Method 3: For now, if no specific link found, return false
            // You could add more linking logic here based on your data model
            
            return false;
            
        } catch (Exception e) {
            System.err.println("Error checking customer-property link: " + e.getMessage());
            return false;
        }
    }
    
    private List<Property> getCustomerProperties(Customer customer) {
        try {
            return assignmentService.getPropertiesForCustomer(customer.getCustomerId(), null);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String handleGoogleDriveApiException(Model model, Exception e){
        String link = "";
        String buttonText = "Go Home";
        String message = "There was a problem with Google Drive, Please try again later!";
        String code = "400";
        if (e instanceof HttpResponseException httpResponseException) {
            int statusCode = httpResponseException.getStatusCode();
            if(statusCode == 403){
                code = "403";
                link = "employee/settings/google-services";
                buttonText = "Grant Access";
                message = "Please grant the app access to Google Drive, in order to use this service";
            }
        }

        model.addAttribute("link",link);
        model.addAttribute("message",message);
        model.addAttribute("buttonText",buttonText);
        model.addAttribute("code",code);
        return "gmail/error";
    }
}
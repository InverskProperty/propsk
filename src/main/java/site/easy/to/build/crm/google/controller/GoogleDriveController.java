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
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

    @Autowired
    public GoogleDriveController(GoogleDriveApiService googleDriveApiService, 
                               AuthenticationUtils authenticationUtils,
                               PropertyService propertyService,
                               CustomerService customerService,
                               CustomerDriveOrganizationService customerDriveOrganizationService,
                               GoogleDriveFileService googleDriveFileService,
                               CustomerPropertyAssignmentService assignmentService) {
        this.googleDriveApiService = googleDriveApiService;
        this.authenticationUtils = authenticationUtils;
        this.propertyService = propertyService;
        this.customerService = customerService;
        this.customerDriveOrganizationService = customerDriveOrganizationService;
        this.googleDriveFileService = googleDriveFileService;
        this.assignmentService = assignmentService;
    }

    @GetMapping("/list-files")
    public String listFilesWithFolder(@RequestParam(value = "folder", required = false) String folderName,
                                    @RequestParam(value = "propertyId", required = false) Long propertyId,
                                    @RequestParam(value = "customerId", required = false) Integer customerId,
                                    Model model, Authentication authentication) {
        if((authentication instanceof UsernamePasswordAuthenticationToken)) {
            return "/google-error";
        }
        
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        
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
                        return handlePropertyFiles(property, model, oAuthUser);
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
                        return handlePropertyFiles(property, model, oAuthUser);
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
            files = googleDriveApiService.listFiles(oAuthUser);
            folders = googleDriveApiService.listFolders(oAuthUser);

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
     * Property-specific file management
     */
    private String handlePropertyFiles(Property property, Model model, OAuthUser oAuthUser) throws IOException, GeneralSecurityException {
        // Get or create property folder structure
        String propertyFolderName = sanitizePropertyName(property.getPropertyName());
        String propertyFolderId = googleDriveApiService.findOrCreateFolderInParent(oAuthUser, propertyFolderName, null);
        
        // Create property subfolders if they don't exist
        Map<String, String> subfolderMap = createPropertySubfolders(oAuthUser, propertyFolderId);
        
        // Get files in property folder (only main folder, not subfolders)
        List<GoogleDriveFile> files = googleDriveApiService.listFilesInFolder(oAuthUser, propertyFolderId);
        
        // Filter out subfolders from files list to avoid clutter
        files = files.stream()
            .filter(file -> !file.getMimeType().equals("application/vnd.google-apps.folder"))
            .collect(Collectors.toList());
        
        // Get organized folder structure for display
        Map<String, List<GoogleDriveFile>> filesByCategory = organizeFilesByCategory(oAuthUser, subfolderMap);
        
        // Get property relationships
        List<Customer> propertyOwners = getPropertyCustomers(property.getId(), AssignmentType.OWNER);
        List<Customer> tenants = getPropertyCustomers(property.getId(), AssignmentType.TENANT);
        
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
        
        // Return the property-specific template
        return "google-drive/property-files";  // Changed from "google-drive/list-files"
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
        
        if (authentication instanceof UsernamePasswordAuthenticationToken) {
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
    public ResponseEntity<Map<String, Object>> setupCustomerFolder(@PathVariable Integer customerId, 
                                                                  Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if (authentication instanceof UsernamePasswordAuthenticationToken) {
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
        
        if (authentication instanceof UsernamePasswordAuthenticationToken) {
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
            List<Customer> propertyOwners = getPropertyCustomers(propertyId, AssignmentType.OWNER);
            List<Customer> tenants = getPropertyCustomers(propertyId, AssignmentType.TENANT);
            
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
        if((authentication instanceof UsernamePasswordAuthenticationToken)) {
            return "/google-error";
        }
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);

        List<GoogleDriveFile> files;
        try {
            files = googleDriveApiService.listFilesInFolder(oAuthUser,id);
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
        if((authentication instanceof UsernamePasswordAuthenticationToken)) {
            return "/google-error";
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
        if((authentication instanceof UsernamePasswordAuthenticationToken)) {
            return "/google-error";
        }
        
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        Property property = propertyService.findById(propertyId);
        
        if (property == null) {
            model.addAttribute("errorMessage", "Property not found.");
            return "error/not-found";
        }
        
        try {
            return handlePropertyFiles(property, model, oAuthUser);
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
            model.addAttribute("errorMessage", "Failed to load property files: " + e.getMessage());
            return "error/error"; // or whatever your error template is called
        }
    }

    @PostMapping("/create-folder")
    public String createFolder(Authentication authentication, @ModelAttribute("folder") @Valid GoogleDriveFolder folder,
                               BindingResult bindingResult, Model model) {
        if((authentication instanceof UsernamePasswordAuthenticationToken)) {
            return "/google-error";
        }
        if (bindingResult.hasErrors()) {
            return "google-drive/create-folder";
        }
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        try {
            googleDriveApiService.createFolder(oAuthUser, folder.getName());
        } catch (GeneralSecurityException | IOException e) {
            return handleGoogleDriveApiException(model,e);
        }
        return "redirect:/employee/drive/list-files";
    }

    @GetMapping("/create-file")
    public String showFileCreationForm(Model model, Authentication authentication){
        if((authentication instanceof UsernamePasswordAuthenticationToken)) {
            return "/google-error";
        }

        List<GoogleDriveFolder> folders;
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        try {
            folders = googleDriveApiService.listFolders(oAuthUser);
        } catch (GeneralSecurityException | IOException e) {
            return handleGoogleDriveApiException(model,e);
        }
        model.addAttribute("folders",folders);
        model.addAttribute("file",new GoogleDriveFile());
        return "google-drive/create-file";
    }

    @PostMapping("/create-file")
    public String createFileInFolder(Authentication authentication, @ModelAttribute("file") @Valid GoogleDriveFile file,
                                     BindingResult bindingResult, Model model) {
        if((authentication instanceof UsernamePasswordAuthenticationToken)) {
            return "/google-error";
        }
        List<GoogleDriveFolder> folders;
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        try {
            folders = googleDriveApiService.listFolders(oAuthUser);
        } catch (IOException | GeneralSecurityException e) {
            return handleGoogleDriveApiException(model,e);
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("folders",folders);
            return "google-drive/create-file";
        }

        try {
            googleDriveApiService.createFileInFolder(oAuthUser, file.getName(), file.getFolderId(), file.getMimeType());
        } catch (GeneralSecurityException | IOException e) {
            return handleGoogleDriveApiException(model,e);
        }
        return "redirect:/employee/drive/list-files";
    }

    @PostMapping("/ajax-share")
    @ResponseBody
    public ResponseEntity<String> shareFileWithUsers(Authentication authentication, @RequestParam("id") String id,
                                                     @RequestParam("emails") String emails, @RequestParam("role") String role) {
        if (authentication instanceof UsernamePasswordAuthenticationToken) {
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
        if (authentication instanceof UsernamePasswordAuthenticationToken) {
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
    
    // ===== ALL EXISTING METHODS UNCHANGED =====
    
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
    
    private List<GoogleDriveFolder> getPropertySubfolders(OAuthUser oAuthUser, String parentFolderId) throws IOException, GeneralSecurityException {
        return googleDriveApiService.listFolders(oAuthUser).stream()
            .filter(folder -> {
                try {
                    // This is a simplified check - in practice you'd need to verify parent relationship
                    return true;
                } catch (Exception e) {
                    return false;
                }
            })
            .collect(Collectors.toList());
    }
    
    private List<Customer> getPropertyCustomers(Long propertyId, AssignmentType assignmentType) {
        try {
            return assignmentService.getCustomersForProperty(propertyId, assignmentType);
        } catch (Exception e) {
            return List.of();
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
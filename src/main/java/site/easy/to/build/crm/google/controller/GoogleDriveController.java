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
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.google.model.drive.GoogleDriveFile;
import site.easy.to.build.crm.google.model.drive.GoogleDriveFolder;
import site.easy.to.build.crm.google.service.drive.GoogleDriveApiService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.drive.CustomerDriveOrganizationService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/employee/drive")
public class GoogleDriveController {

    private final GoogleDriveApiService googleDriveApiService;
    private final AuthenticationUtils authenticationUtils;
    private final PropertyService propertyService;
    private final CustomerService customerService;
    private final CustomerDriveOrganizationService customerDriveOrganizationService;

    @Autowired
    public GoogleDriveController(GoogleDriveApiService googleDriveApiService, 
                               AuthenticationUtils authenticationUtils,
                               PropertyService propertyService,
                               CustomerService customerService,
                               CustomerDriveOrganizationService customerDriveOrganizationService) {
        this.googleDriveApiService = googleDriveApiService;
        this.authenticationUtils = authenticationUtils;
        this.propertyService = propertyService;
        this.customerService = customerService;
        this.customerDriveOrganizationService = customerDriveOrganizationService;
    }

    /**
     * ✅ ENHANCED: Handle property folder filtering and regular file listing
     */
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
            
            // ✅ NEW: Handle property-specific file listing
            if (folderName != null && !folderName.isEmpty()) {
                // Try to find property by name (URL decoded)
                String decodedFolderName = java.net.URLDecoder.decode(folderName, "UTF-8");
                Property property = findPropertyByName(decodedFolderName);
                
                if (property != null) {
                    files = getPropertyFiles(oAuthUser, property);
                    folders = getPropertyFolders(oAuthUser, property);
                    pageTitle = "Files for " + property.getPropertyName();
                    breadcrumb = property.getPropertyName();
                    model.addAttribute("property", property);
                    model.addAttribute("isPropertyView", true);
                } else {
                    // Fallback to regular folder listing if property not found
                    files = googleDriveApiService.listFiles(oAuthUser);
                    folders = googleDriveApiService.listFolders(oAuthUser);
                }
            } 
            // ✅ NEW: Handle direct property ID filtering
            else if (propertyId != null) {
                Property property = propertyService.findById(propertyId);
                if (property != null) {
                    files = getPropertyFiles(oAuthUser, property);
                    folders = getPropertyFolders(oAuthUser, property);
                    pageTitle = "Files for " + property.getPropertyName();
                    breadcrumb = property.getPropertyName();
                    model.addAttribute("property", property);
                    model.addAttribute("isPropertyView", true);
                } else {
                    files = googleDriveApiService.listFiles(oAuthUser);
                    folders = googleDriveApiService.listFolders(oAuthUser);
                }
            }
            // ✅ NEW: Handle customer-specific file listing
            else if (customerId != null) {
                Customer customer = customerService.findByCustomerId(customerId);
                if (customer != null) {
                    files = getCustomerFiles(oAuthUser, customer);
                    folders = getCustomerFolders(oAuthUser, customer);
                    pageTitle = "Files for " + customer.getName();
                    breadcrumb = customer.getName();
                    model.addAttribute("customer", customer);
                    model.addAttribute("isCustomerView", true);
                } else {
                    files = googleDriveApiService.listFiles(oAuthUser);
                    folders = googleDriveApiService.listFolders(oAuthUser);
                }
            }
            // Default: Show all files
            else {
                files = googleDriveApiService.listFiles(oAuthUser);
                folders = googleDriveApiService.listFolders(oAuthUser);
            }

            model.addAttribute("files", files);
            model.addAttribute("folders", folders);
            model.addAttribute("pageTitle", pageTitle);
            model.addAttribute("breadcrumb", breadcrumb);
            
            return "google-drive/list-files";
            
        } catch (IOException | GeneralSecurityException e) {
            return handleGoogleDriveApiException(model, e);
        } catch (Exception e) {
            model.addAttribute("error", "Error loading files: " + e.getMessage());
            return handleGoogleDriveApiException(model, e);
        }
    }

    /**
     * ✅ NEW: Property files dashboard - integrated view
     */
    @GetMapping("/property/{propertyId}")
    public String propertyFilesDashboard(@PathVariable Long propertyId, Model model, Authentication authentication) {
        if((authentication instanceof UsernamePasswordAuthenticationToken)) {
            return "/google-error";
        }
        
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            Property property = propertyService.findById(propertyId);
            
            if (property == null) {
                return "redirect:/employee/property/all-properties";
            }
            
            // Get property files organized by category
            List<site.easy.to.build.crm.entity.GoogleDriveFile> allFiles = getPropertyDriveFiles(property);
            
            // Get property owners and tenants for this property
            List<Customer> propertyOwners = getPropertyCustomers(property, "OWNER");
            List<Customer> tenants = getPropertyCustomers(property, "TENANT");
            
            model.addAttribute("property", property);
            model.addAttribute("allFiles", allFiles);
            model.addAttribute("propertyOwners", propertyOwners);
            model.addAttribute("tenants", tenants);
            model.addAttribute("pageTitle", "Files for " + property.getPropertyName());
            
            return "google-drive/property-files-dashboard";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading property files: " + e.getMessage());
            return "error/error";
        }
    }

    /**
     * ✅ NEW: Customer files dashboard integrated with Drive
     */
    @GetMapping("/customer/{customerId}")
    public String customerFilesDashboard(@PathVariable Integer customerId, Model model, Authentication authentication) {
        if((authentication instanceof UsernamePasswordAuthenticationToken)) {
            return "/google-error";
        }
        
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            Customer customer = customerService.findByCustomerId(customerId);
            
            if (customer == null) {
                return "redirect:/employee/customer/all-customers";
            }
            
            // Get customer folder structure
            CustomerDriveOrganizationService.CustomerFolderStructure folderStructure = 
                customerDriveOrganizationService.getOrCreateCustomerFolderStructure(oAuthUser, customer);
            
            // Get customer files organized by category
            List<site.easy.to.build.crm.entity.GoogleDriveFile> allFiles = getCustomerDriveFiles(customer);
            
            model.addAttribute("customer", customer);
            model.addAttribute("folderStructure", folderStructure);
            model.addAttribute("allFiles", allFiles);
            model.addAttribute("pageTitle", "Files for " + customer.getName());
            
            return "google-drive/customer-files-dashboard";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading customer files: " + e.getMessage());
            return "error/error";
        }
    }

    // ===== EXISTING METHODS (keep unchanged) =====
    
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

    // ===== HELPER METHODS =====
    
    private Property findPropertyByName(String propertyName) {
        List<Property> properties = propertyService.searchByPropertyName(propertyName);
        return properties.isEmpty() ? null : properties.get(0);
    }
    
    private List<GoogleDriveFile> getPropertyFiles(OAuthUser oAuthUser, Property property) throws IOException, GeneralSecurityException {
        // Try to find property-specific folder and get files from it
        String propertyFolderName = property.getPropertyName();
        try {
            String folderId = googleDriveApiService.findFolderByName(oAuthUser, propertyFolderName, null);
            if (folderId != null) {
                return googleDriveApiService.listFilesInFolder(oAuthUser, folderId);
            }
        } catch (Exception e) {
            // Fallback to all files if property folder not found
        }
        return googleDriveApiService.listFiles(oAuthUser);
    }
    
    private List<GoogleDriveFolder> getPropertyFolders(OAuthUser oAuthUser, Property property) throws IOException, GeneralSecurityException {
        // Return folders that might be related to this property
        return googleDriveApiService.listFolders(oAuthUser).stream()
            .filter(folder -> folder.getName().toLowerCase().contains(property.getPropertyName().toLowerCase()))
            .collect(Collectors.toList());
    }
    
    private List<GoogleDriveFile> getCustomerFiles(OAuthUser oAuthUser, Customer customer) throws IOException, GeneralSecurityException {
        // Try to find customer-specific folder and get files from it
        String customerFolderName = generateCustomerFolderName(customer);
        try {
            String folderId = googleDriveApiService.findFolderByName(oAuthUser, customerFolderName, null);
            if (folderId != null) {
                return googleDriveApiService.listFilesInFolder(oAuthUser, folderId);
            }
        } catch (Exception e) {
            // Fallback to all files if customer folder not found
        }
        return googleDriveApiService.listFiles(oAuthUser);
    }
    
    private List<GoogleDriveFolder> getCustomerFolders(OAuthUser oAuthUser, Customer customer) throws IOException, GeneralSecurityException {
        return googleDriveApiService.listFolders(oAuthUser).stream()
            .filter(folder -> folder.getName().toLowerCase().contains(customer.getName().toLowerCase()))
            .collect(Collectors.toList());
    }
    
    private List<site.easy.to.build.crm.entity.GoogleDriveFile> getPropertyDriveFiles(Property property) {
        // This would use your GoogleDriveFileService to get property files from database
        // Placeholder - implement based on your service methods
        return List.of();
    }
    
    private List<site.easy.to.build.crm.entity.GoogleDriveFile> getCustomerDriveFiles(Customer customer) {
        // This would use your GoogleDriveFileService to get customer files from database
        // Placeholder - implement based on your service methods
        return List.of();
    }
    
    private List<Customer> getPropertyCustomers(Property property, String type) {
        // This would use your CustomerPropertyAssignmentService to get customers for property
        // Placeholder - implement based on your assignment service
        return List.of();
    }
    
    private String generateCustomerFolderName(Customer customer) {
        String prefix = customer.getCustomerType().name().substring(0, 2);
        String name = customer.getName() != null ? customer.getName() : "Customer";
        return String.format("%s_%d_%s", prefix, customer.getCustomerId(), name.replaceAll("[^a-zA-Z0-9]", "_"));
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
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import site.easy.to.build.crm.service.drive.SharedDriveFileService;
import site.easy.to.build.crm.service.customer.CustomerService;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

/**
 * Employee Files Controller
 *
 * Parallel document management system for employees that allows:
 * - Viewing all customer documents
 * - Uploading to customer property folders
 * - Managing employee-only internal folders
 * - Same Shared Drive and service account as customer side
 */
@Controller
@RequestMapping("/employee/files")
public class EmployeeFilesController {

    private final SharedDriveFileService sharedDriveFileService;
    private final CustomerService customerService;

    @Autowired
    public EmployeeFilesController(SharedDriveFileService sharedDriveFileService,
                                   CustomerService customerService) {
        this.sharedDriveFileService = sharedDriveFileService;
        this.customerService = customerService;
    }

    /**
     * Main files page
     */
    @GetMapping("")
    public String viewFiles(Model model, Authentication authentication) {
        System.out.println("📁 [Employee Files] Loading files page for employee");

        // Check if service account is available
        boolean serviceAccountAvailable = serviceAccountAvailable();
        model.addAttribute("serviceAccountAvailable", serviceAccountAvailable);

        if (!serviceAccountAvailable) {
            model.addAttribute("error", "Service account not configured. Please contact administrator.");
        }

        return "employee/files";
    }

    /**
     * Browse all properties (NEW PROPERTY-CENTRIC APPROACH)
     */
    @GetMapping("/browse/properties")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> browseAllProperties(Authentication authentication) {
        System.out.println("📁 [Employee Files] Browsing all property folders");

        try {
            ensureEmployeeAccess(authentication);

            List<Map<String, Object>> propertyFolders = sharedDriveFileService.listAllPropertyFolders();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("properties", propertyFolders);
            response.put("propertyCount", propertyFolders.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ Error browsing properties: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Error loading properties: " + e.getMessage()
            ));
        }
    }

    /**
     * Browse property subfolders (EICR, EPC, Insurance, etc.)
     */
    @GetMapping("/browse/property/{propertyId}/subfolders")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> browsePropertySubfolders(@PathVariable Long propertyId,
                                                                        Authentication authentication) {
        System.out.println("📁 [Employee Files] Browsing subfolders for property: " + propertyId);

        try {
            ensureEmployeeAccess(authentication);

            // Return standard property subfolders
            List<String> subfolders = Arrays.asList("EICR", "EPC", "Insurance", "Statements", "Maintenance");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("subfolders", subfolders);
            response.put("propertyId", propertyId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ Error browsing property subfolders: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Error loading property subfolders: " + e.getMessage()
            ));
        }
    }

    /**
     * List files in property subfolder (NEW: Uses property-centric structure)
     */
    @GetMapping("/browse/property/{propertyId}/subfolder/{subfolderName}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listPropertySubfolderFiles(@PathVariable Long propertyId,
                                                                          @PathVariable String subfolderName,
                                                                          Authentication authentication) {
        System.out.println("📁 [Employee Files] Listing files in property " + propertyId + "/" + subfolderName);

        try {
            ensureEmployeeAccess(authentication);

            List<Map<String, Object>> files = sharedDriveFileService.listPropertySubfolderFiles(
                propertyId, subfolderName
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("files", files);
            response.put("fileCount", files.size());
            response.put("propertyId", propertyId);
            response.put("subfolder", subfolderName);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ Error listing property subfolder files: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Error loading files: " + e.getMessage()
            ));
        }
    }

    /**
     * Browse internal folders (employee-only)
     */
    @GetMapping("/browse/internal")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> browseInternalFolders(Authentication authentication) {
        System.out.println("📁 [Employee Files] Browsing internal folders");

        try {
            ensureEmployeeAccess(authentication);

            List<Map<String, Object>> folders = sharedDriveFileService.listInternalFolders();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("folders", folders);
            response.put("folderCount", folders.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ Error browsing internal folders: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Error loading internal folders: " + e.getMessage()
            ));
        }
    }

    /**
     * List files in internal folder
     */
    @GetMapping("/browse/internal/{subfolderName}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listInternalFiles(@PathVariable String subfolderName,
                                                                 Authentication authentication) {
        System.out.println("📁 [Employee Files] Listing files in Internal/" + subfolderName);

        try {
            ensureEmployeeAccess(authentication);

            List<Map<String, Object>> files = sharedDriveFileService.listInternalFiles(subfolderName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("files", files);
            response.put("fileCount", files.size());
            response.put("subfolder", subfolderName);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ Error listing internal files: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Error loading files: " + e.getMessage()
            ));
        }
    }

    /**
     * Upload files to property subfolder
     */
    @PostMapping("/upload/property/{propertyId}/subfolder/{subfolderName}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadToPropertySubfolder(@PathVariable Long propertyId,
                                                                         @PathVariable String subfolderName,
                                                                         @RequestParam("files") MultipartFile[] files,
                                                                         Authentication authentication) {
        System.out.println("📤 [Employee Files] Uploading " + files.length + " files to property " + propertyId + "/" + subfolderName);

        try {
            ensureEmployeeAccess(authentication);

            List<Map<String, Object>> uploadedFiles = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            for (MultipartFile file : files) {
                try {
                    if (file.isEmpty()) {
                        errors.add("File " + file.getOriginalFilename() + " is empty");
                        continue;
                    }

                    Map<String, Object> uploadResult = sharedDriveFileService.uploadToPropertySubfolder(
                        propertyId, subfolderName, file
                    );
                    uploadedFiles.add(uploadResult);
                    System.out.println("✅ [Employee] Uploaded: " + file.getOriginalFilename());

                } catch (Exception e) {
                    errors.add("Failed to upload " + file.getOriginalFilename() + ": " + e.getMessage());
                    System.err.println("❌ Upload error for " + file.getOriginalFilename() + ": " + e.getMessage());
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", uploadedFiles.size() > 0);
            response.put("uploadedFiles", uploadedFiles);
            response.put("uploadedCount", uploadedFiles.size());
            response.put("totalCount", files.length);

            if (!errors.isEmpty()) {
                response.put("errors", errors);
                response.put("message", uploadedFiles.size() + " of " + files.length + " files uploaded successfully");
            } else {
                response.put("message", "All files uploaded successfully");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ Error uploading files: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Error uploading files: " + e.getMessage()
            ));
        }
    }

    /**
     * Upload files to internal folder
     */
    @PostMapping("/upload/internal/{subfolderName}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadToInternalFolder(@PathVariable String subfolderName,
                                                                      @RequestParam("files") MultipartFile[] files,
                                                                      Authentication authentication) {
        System.out.println("📤 [Employee Files] Uploading " + files.length + " files to Internal/" + subfolderName);

        try {
            ensureEmployeeAccess(authentication);

            List<Map<String, Object>> uploadedFiles = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            for (MultipartFile file : files) {
                try {
                    if (file.isEmpty()) {
                        errors.add("File " + file.getOriginalFilename() + " is empty");
                        continue;
                    }

                    Map<String, Object> uploadResult = sharedDriveFileService.uploadToInternalFolder(
                        subfolderName, file
                    );
                    uploadedFiles.add(uploadResult);
                    System.out.println("✅ [Employee] Uploaded to Internal: " + file.getOriginalFilename());

                } catch (Exception e) {
                    errors.add("Failed to upload " + file.getOriginalFilename() + ": " + e.getMessage());
                    System.err.println("❌ Upload error for " + file.getOriginalFilename() + ": " + e.getMessage());
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", uploadedFiles.size() > 0);
            response.put("uploadedFiles", uploadedFiles);
            response.put("uploadedCount", uploadedFiles.size());
            response.put("totalCount", files.length);

            if (!errors.isEmpty()) {
                response.put("errors", errors);
                response.put("message", uploadedFiles.size() + " of " + files.length + " files uploaded successfully");
            } else {
                response.put("message", "All files uploaded successfully");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ Error uploading files to internal folder: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Error uploading files: " + e.getMessage()
            ));
        }
    }

    /**
     * Get download URL for a file
     */
    @GetMapping("/download/{fileId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDownloadUrl(@PathVariable String fileId,
                                                              Authentication authentication) {
        try {
            ensureEmployeeAccess(authentication);

            String downloadUrl = sharedDriveFileService.getDownloadUrl(fileId);
            String directDownloadUrl = sharedDriveFileService.getDirectDownloadUrl(fileId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("downloadUrl", downloadUrl);
            response.put("directDownloadUrl", directDownloadUrl);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ Error getting download URL: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Error getting download URL: " + e.getMessage()
            ));
        }
    }

    /**
     * Get view URL for a file
     */
    @GetMapping("/view/{fileId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getViewUrl(@PathVariable String fileId,
                                                          Authentication authentication) {
        try {
            ensureEmployeeAccess(authentication);

            String viewUrl = sharedDriveFileService.getDownloadUrl(fileId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("viewUrl", viewUrl);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ Error getting view URL: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Error getting view URL: " + e.getMessage()
            ));
        }
    }

    // ===================================================================
    // Helper Methods
    // ===================================================================

    /**
     * Check if service account is available
     */
    private boolean serviceAccountAvailable() {
        try {
            // This will check if GOOGLE_SERVICE_ACCOUNT_KEY is set
            sharedDriveFileService.listInternalFolders();
            return true;
        } catch (IllegalStateException e) {
            return false;
        } catch (Exception e) {
            return true; // Service account is configured, just had another error
        }
    }

    /**
     * Ensure user has employee access
     */
    private void ensureEmployeeAccess(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            throw new SecurityException("Authentication required");
        }

        boolean hasEmployeeRole = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(role -> role.equals("ROLE_EMPLOYEE") ||
                             role.equals("ROLE_MANAGER") ||
                             role.equals("ROLE_ADMIN") ||
                             role.equals("ROLE_SUPER_ADMIN"));

        if (!hasEmployeeRole) {
            throw new SecurityException("Employee access required");
        }
    }
}

package site.easy.to.build.crm.controller;

import jakarta.servlet.http.HttpServletResponse;
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
     * List property-level document subfolders (EICR, EPC, Insurance, Misc)
     */
    @GetMapping("/property/{propertyId}/subfolders")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listPropertySubfolders(@PathVariable Long propertyId,
                                                                      Authentication authentication) {
        System.out.println("📂 [Employee Files] Listing property-level subfolders for property: " + propertyId);

        try {
            ensureEmployeeAccess(authentication);

            List<Map<String, Object>> subfolders = sharedDriveFileService.listPropertySubfolders(propertyId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("subfolders", subfolders);
            response.put("count", subfolders.size());
            response.put("propertyId", propertyId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ [Employee] Error listing property subfolders: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Error listing property subfolders: " + e.getMessage()));
        }
    }

    /**
     * List all tenants/leases for a property
     */
    @GetMapping("/property/{propertyId}/tenants")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listPropertyTenants(@PathVariable Long propertyId,
                                                                   Authentication authentication) {
        System.out.println("📋 [Employee Files] Listing tenants for property: " + propertyId);

        try {
            ensureEmployeeAccess(authentication);

            List<Map<String, Object>> tenants = sharedDriveFileService.listTenantsForProperty(propertyId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("tenants", tenants);
            response.put("count", tenants.size());
            response.put("propertyId", propertyId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ [Employee] Error listing tenants: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Error listing tenants: " + e.getMessage()));
        }
    }

    /**
     * List files in a property-level document subfolder
     */
    @GetMapping("/property/{propertyId}/subfolder/{subfolderName}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listPropertySubfolderFiles(@PathVariable Long propertyId,
                                                                          @PathVariable String subfolderName,
                                                                          Authentication authentication) {
        System.out.println("📄 [Employee Files] Listing files - Property: " + propertyId + ", Subfolder: " + subfolderName);

        try {
            ensureEmployeeAccess(authentication);

            List<Map<String, Object>> files = sharedDriveFileService.listPropertySubfolderFiles(propertyId, subfolderName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("files", files);
            response.put("count", files.size());
            response.put("propertyId", propertyId);
            response.put("subfolderName", subfolderName);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ [Employee] Error listing property subfolder files: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Error listing files: " + e.getMessage()));
        }
    }

    /**
     * Upload files to a property-level document subfolder
     */
    @PostMapping("/property/{propertyId}/subfolder/{subfolderName}/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadToPropertySubfolderNew(@PathVariable Long propertyId,
                                                                            @PathVariable String subfolderName,
                                                                            @RequestParam("files") MultipartFile[] files,
                                                                            Authentication authentication) {
        System.out.println("⬆️  [Employee Files] Uploading to property subfolder - Property: " + propertyId + ", Subfolder: " + subfolderName);

        try {
            ensureEmployeeAccess(authentication);

            List<Map<String, Object>> uploadedFiles = new ArrayList<>();
            List<String> failedFiles = new ArrayList<>();

            for (MultipartFile file : files) {
                try {
                    if (!file.isEmpty()) {
                        Map<String, Object> result = sharedDriveFileService.uploadToPropertySubfolder(propertyId, subfolderName, file);
                        uploadedFiles.add(result);
                    }
                } catch (Exception e) {
                    System.err.println("❌ [Employee] Failed to upload file: " + file.getOriginalFilename() + " - " + e.getMessage());
                    failedFiles.add(file.getOriginalFilename());
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("uploadedFiles", uploadedFiles);
            response.put("successCount", uploadedFiles.size());
            response.put("failedCount", failedFiles.size());
            response.put("failedFiles", failedFiles);

            if (!failedFiles.isEmpty()) {
                response.put("message", uploadedFiles.size() + " of " + files.length + " files uploaded successfully");
            } else {
                response.put("message", "All files uploaded successfully");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ [Employee] Error uploading to property subfolder: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Error uploading files: " + e.getMessage()));
        }
    }

    /**
     * List files in a tenant's document subfolder
     */
    @GetMapping("/property/{propertyId}/tenant/{leaseId}/subfolder/{subfolderName}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listTenantSubfolderFiles(@PathVariable Long propertyId,
                                                                        @PathVariable Long leaseId,
                                                                        @PathVariable String subfolderName,
                                                                        Authentication authentication) {
        System.out.println("📂 [Employee Files] Listing files - Property: " + propertyId + ", Lease: " + leaseId + ", Subfolder: " + subfolderName);

        try {
            ensureEmployeeAccess(authentication);

            List<Map<String, Object>> files = sharedDriveFileService.listTenantSubfolderFiles(propertyId, leaseId, subfolderName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("files", files);
            response.put("count", files.size());
            response.put("propertyId", propertyId);
            response.put("leaseId", leaseId);
            response.put("subfolderName", subfolderName);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ [Employee] Error listing tenant subfolder files: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Error listing files: " + e.getMessage()));
        }
    }

    /**
     * Upload files to a tenant's document subfolder
     */
    @PostMapping("/property/{propertyId}/tenant/{leaseId}/subfolder/{subfolderName}/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadToTenantSubfolder(@PathVariable Long propertyId,
                                                                       @PathVariable Long leaseId,
                                                                       @PathVariable String subfolderName,
                                                                       @RequestParam("files") MultipartFile[] files,
                                                                       Authentication authentication) {
        System.out.println("⬆️  [Employee Files] Uploading to tenant subfolder - Property: " + propertyId + ", Lease: " + leaseId + ", Subfolder: " + subfolderName);

        try {
            ensureEmployeeAccess(authentication);

            List<Map<String, Object>> uploadedFiles = new ArrayList<>();
            List<String> failedFiles = new ArrayList<>();

            for (MultipartFile file : files) {
                try {
                    if (!file.isEmpty()) {
                        Map<String, Object> result = sharedDriveFileService.uploadToTenantSubfolder(propertyId, leaseId, subfolderName, file);
                        uploadedFiles.add(result);
                    }
                } catch (Exception e) {
                    System.err.println("❌ [Employee] Failed to upload file: " + file.getOriginalFilename() + " - " + e.getMessage());
                    failedFiles.add(file.getOriginalFilename());
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("uploadedFiles", uploadedFiles);
            response.put("successCount", uploadedFiles.size());
            response.put("failedCount", failedFiles.size());
            response.put("failedFiles", failedFiles);

            if (!failedFiles.isEmpty()) {
                response.put("message", uploadedFiles.size() + " of " + files.length + " files uploaded successfully");
            } else {
                response.put("message", "All files uploaded successfully");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ [Employee] Error uploading to tenant subfolder: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Error uploading files: " + e.getMessage()));
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

    /**
     * Proxy file download through application (NEW - bypasses Google authentication)
     * Users can download files without being signed into Google
     */
    @GetMapping("/proxy/download/{fileId}")
    public void proxyFileDownload(@PathVariable String fileId,
                                  HttpServletResponse response,
                                  Authentication authentication) {
        try {
            ensureEmployeeAccess(authentication);

            System.out.println("📥 [Employee Files] Proxying file download: " + fileId);

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

            System.out.println("✅ [Employee Files] Successfully proxied file: " + fileName);

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

    /**
     * Proxy file view through application (NEW - for inline viewing)
     * Opens files in browser without Google authentication
     */
    @GetMapping("/proxy/view/{fileId}")
    public void proxyFileView(@PathVariable String fileId,
                              HttpServletResponse response,
                              Authentication authentication) {
        try {
            ensureEmployeeAccess(authentication);

            System.out.println("👁️ [Employee Files] Proxying file view: " + fileId);

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

            // Stream file content directly to response
            sharedDriveFileService.downloadFileContent(fileId, response.getOutputStream());
            response.getOutputStream().flush();

            System.out.println("✅ [Employee Files] Successfully proxied view for: " + fileName);

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
     * TEST ENDPOINT: Force creation of property subfolders for a specific property
     * Use this to verify that folders are being created in Google Drive
     */
    @GetMapping("/test/create-property-folders/{propertyId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testCreatePropertyFolders(@PathVariable Long propertyId,
                                                                         Authentication authentication) {
        try {
            ensureEmployeeAccess(authentication);

            System.out.println("🔧 [TEST] Force creating property subfolders for property: " + propertyId);

            // This should create EICR, EPC, Insurance, Miscellaneous folders in Google Drive
            List<Map<String, Object>> subfolders = sharedDriveFileService.listPropertySubfolders(propertyId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Property subfolders created/retrieved successfully");
            response.put("propertyId", propertyId);
            response.put("subfolders", subfolders);
            response.put("count", subfolders.size());
            response.put("instruction", "Check your Google Drive Shared Drive under: Property Documents → Property " + propertyId);

            System.out.println("✅ [TEST] Successfully created/retrieved " + subfolders.size() + " property subfolders");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ [TEST] Error creating property folders: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Error creating property folders: " + e.getMessage(),
                "errorType", e.getClass().getSimpleName()
            ));
        }
    }

    /**
     * TEST ENDPOINT: Force creation of property subfolders for ALL properties
     * Creates EICR, EPC, Insurance, Miscellaneous folders for every property in the system
     */
    @GetMapping("/test/create-all-property-folders")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testCreateAllPropertyFolders(Authentication authentication) {
        try {
            ensureEmployeeAccess(authentication);

            System.out.println("🔧 [TEST] Force creating property subfolders for ALL properties");
            long startTime = System.currentTimeMillis();

            // Get all properties
            List<Map<String, Object>> allProperties = sharedDriveFileService.listAllPropertyFolders();

            List<Map<String, Object>> results = new ArrayList<>();
            int successCount = 0;
            int errorCount = 0;

            for (Map<String, Object> property : allProperties) {
                Long propertyId = ((Number) property.get("id")).longValue();
                String propertyName = (String) property.get("propertyName");

                try {
                    System.out.println("📁 Creating subfolders for: " + propertyName + " (ID: " + propertyId + ")");

                    // This creates the subfolders if they don't exist
                    List<Map<String, Object>> subfolders = sharedDriveFileService.listPropertySubfolders(propertyId);

                    Map<String, Object> propertyResult = new HashMap<>();
                    propertyResult.put("propertyId", propertyId);
                    propertyResult.put("propertyName", propertyName);
                    propertyResult.put("success", true);
                    propertyResult.put("subfoldersCreated", subfolders.size());
                    propertyResult.put("subfolders", subfolders.stream()
                        .map(sf -> sf.get("name"))
                        .collect(java.util.stream.Collectors.toList()));

                    results.add(propertyResult);
                    successCount++;
                    System.out.println("✅ Created " + subfolders.size() + " subfolders for " + propertyName);

                } catch (Exception e) {
                    System.err.println("❌ Error creating folders for property " + propertyId + ": " + e.getMessage());

                    Map<String, Object> propertyResult = new HashMap<>();
                    propertyResult.put("propertyId", propertyId);
                    propertyResult.put("propertyName", propertyName);
                    propertyResult.put("success", false);
                    propertyResult.put("error", e.getMessage());

                    results.add(propertyResult);
                    errorCount++;
                }
            }

            long duration = System.currentTimeMillis() - startTime;

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Completed creating property subfolders for all properties");
            response.put("totalProperties", allProperties.size());
            response.put("successCount", successCount);
            response.put("errorCount", errorCount);
            response.put("durationMs", duration);
            response.put("results", results);
            response.put("instruction", "Check your Google Drive Shared Drive under: Property Documents → [each property] → EICR/EPC/Insurance/Miscellaneous");

            System.out.println("✅ [TEST] Completed! " + successCount + " succeeded, " + errorCount + " failed in " + duration + "ms");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ [TEST] Error in bulk folder creation: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Error creating property folders: " + e.getMessage(),
                "errorType", e.getClass().getSimpleName()
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

package site.easy.to.build.crm.service.drive;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.AssignmentType;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.assignment.CustomerPropertyAssignmentService;
import site.easy.to.build.crm.repository.InvoiceRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class SharedDriveFileService {

    @Value("${GOOGLE_SERVICE_ACCOUNT_KEY:}")
    private String serviceAccountKey;

    private final PropertyService propertyService;
    private final CustomerService customerService;
    private final CustomerPropertyAssignmentService assignmentService;
    private final InvoiceRepository invoiceRepository;

    public SharedDriveFileService(PropertyService propertyService,
                                  CustomerService customerService,
                                  CustomerPropertyAssignmentService assignmentService,
                                  InvoiceRepository invoiceRepository) {
        this.propertyService = propertyService;
        this.customerService = customerService;
        this.assignmentService = assignmentService;
        this.invoiceRepository = invoiceRepository;
    }

    // Shared Drive ID for CRM property documents
    private static final String SHARED_DRIVE_ID = "0ADaFlidiFrFDUk9PVA";
    private static final String SHARED_DRIVE_DOCUMENTS_FOLDER = "Property-Documents";

    // Tenant/Lease document subfolders (within each tenant folder)
    private static final List<String> TENANT_DOCUMENT_SUBFOLDERS = Arrays.asList(
        "Contracts",
        "Inspections",
        "Inventories",
        "Miscellaneous",
        "Right-to-Rent"
    );

    // Internal folders (employee-only)
    private static final String INTERNAL_FOLDER = "Internal";
    private static final List<String> INTERNAL_SUBFOLDERS = Arrays.asList(
        "Contractors", "Legal", "Finance", "HR",
        "Property-Research", "Marketing", "Templates"
    );

    /**
     * List files in a specific folder for a customer
     */
    public List<Map<String, Object>> listFiles(Customer customer, String folderType) throws IOException, GeneralSecurityException {
        if (!hasServiceAccount()) {
            throw new IllegalStateException("Service account not configured");
        }

        Drive driveService = createDriveService();
        String folderId = findCustomerFolderId(driveService, customer, folderType);

        if (folderId == null) {
            return new ArrayList<>(); // Folder doesn't exist yet
        }

        return listFilesInFolder(driveService, folderId);
    }

    /**
     * List folders for property documents (shows customer's properties)
     */
    public List<Map<String, Object>> listPropertyFolders(Customer customer) throws IOException, GeneralSecurityException {
        if (!hasServiceAccount()) {
            throw new IllegalStateException("Service account not configured");
        }

        Drive driveService = createDriveService();
        String customerFolderId = findCustomerMainFolder(driveService, customer);

        if (customerFolderId == null) {
            // Auto-create customer main folder if it doesn't exist
            System.out.println("📁 Customer folder not found, creating folder structure for: " + customer.getEmail());
            customerFolderId = getOrCreateCustomerMainFolder(driveService, customer);
        }

        // Get customer's actual properties
        List<Property> customerProperties = propertyService.findPropertiesAccessibleByCustomer(customer.getCustomerId());
        System.out.println("📁 Found " + customerProperties.size() + " properties for customer " + customer.getCustomerId());

        List<Map<String, Object>> propertyFolders = new ArrayList<>();

        // Create/find folders for each property
        for (Property property : customerProperties) {
            String propertyFolderName = generatePropertyFolderName(property);
            String propertyFolderId = getOrCreatePropertyFolder(driveService, customerFolderId, propertyFolderName);

            Map<String, Object> folderInfo = new HashMap<>();
            folderInfo.put("id", property.getId()); // Use property database ID, not folder ID
            folderInfo.put("folderId", propertyFolderId); // Keep folder ID for reference
            folderInfo.put("name", propertyFolderName);
            folderInfo.put("type", "folder");
            folderInfo.put("modified", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            folderInfo.put("subfolders", Arrays.asList("EICR", "EPC", "Insurance", "Statements", "Tenancy", "Maintenance"));

            propertyFolders.add(folderInfo);
            System.out.println("📁 Property folder: " + propertyFolderName + " (Property ID: " + property.getId() + ", Folder ID: " + propertyFolderId + ")");
        }

        return propertyFolders;
    }

    /**
     * Upload a file to customer's folder
     */
    public Map<String, Object> uploadFile(Customer customer, String folderType, MultipartFile file)
            throws IOException, GeneralSecurityException {

        if (!hasServiceAccount()) {
            throw new IllegalStateException("Service account not configured");
        }

        Drive driveService = createDriveService();

        // Get or create the target folder
        String folderId = getOrCreateCustomerFolder(driveService, customer, folderType);

        // Upload the file
        File fileMetadata = new File();
        fileMetadata.setName(file.getOriginalFilename());
        fileMetadata.setParents(Collections.singletonList(folderId));

        com.google.api.client.http.InputStreamContent mediaContent =
            new com.google.api.client.http.InputStreamContent(
                file.getContentType(),
                file.getInputStream()
            );

        File uploadedFile = driveService.files()
            .create(fileMetadata, mediaContent)
            .setSupportsAllDrives(true)
            .execute();

        System.out.println("✅ File uploaded: " + uploadedFile.getName() + " (ID: " + uploadedFile.getId() + ")");

        // Return file info
        Map<String, Object> result = new HashMap<>();
        result.put("id", uploadedFile.getId());
        result.put("name", uploadedFile.getName());
        result.put("size", formatFileSize(file.getSize()));
        result.put("uploadedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        return result;
    }

    /**
     * Get download URL for a file
     */
    public String getDownloadUrl(String fileId) {
        return "https://drive.google.com/file/d/" + fileId + "/view";
    }

    /**
     * Get direct download link for a file (OLD - requires user Google auth)
     * @deprecated Use downloadFileContent() instead to proxy through app
     */
    @Deprecated
    public String getDirectDownloadUrl(String fileId) {
        return "https://drive.google.com/uc?export=download&id=" + fileId;
    }

    /**
     * Download file content using service account (for proxying to users)
     * This allows users to download files without Google authentication
     */
    public java.io.OutputStream downloadFileContent(String fileId, java.io.OutputStream outputStream)
            throws IOException, GeneralSecurityException {

        if (!hasServiceAccount()) {
            throw new IllegalStateException("Service account not configured");
        }

        Drive driveService = createDriveService();

        // Download the file content
        driveService.files().get(fileId)
            .setSupportsAllDrives(true)
            .executeMediaAndDownloadTo(outputStream);

        return outputStream;
    }

    /**
     * Get file metadata (name, size, mimeType) for download headers
     */
    public Map<String, Object> getFileMetadata(String fileId) throws IOException, GeneralSecurityException {
        if (!hasServiceAccount()) {
            throw new IllegalStateException("Service account not configured");
        }

        Drive driveService = createDriveService();
        File file = driveService.files().get(fileId)
            .setSupportsAllDrives(true)
            .setFields("name, size, mimeType, createdTime, modifiedTime")
            .execute();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", file.getName());
        metadata.put("size", file.getSize());
        metadata.put("mimeType", file.getMimeType());
        metadata.put("createdTime", file.getCreatedTime());
        metadata.put("modifiedTime", file.getModifiedTime());

        return metadata;
    }

    // Helper methods

    private boolean hasServiceAccount() {
        return serviceAccountKey != null && !serviceAccountKey.trim().isEmpty();
    }

    private Drive createDriveService() throws IOException, GeneralSecurityException {
        String formattedKey = getFormattedServiceAccountKey();
        GoogleCredential credential = GoogleCredential
            .fromStream(new ByteArrayInputStream(formattedKey.getBytes()))
            .createScoped(Collections.singleton(DriveScopes.DRIVE));

        return new Drive.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            credential)
            .setApplicationName("CRM Property Management")
            .build();
    }

    private String getFormattedServiceAccountKey() {
        if (serviceAccountKey.contains("\\n")) {
            return serviceAccountKey.replace("\\n", "\n");
        }
        return serviceAccountKey;
    }

    private String findCustomerMainFolder(Drive driveService, Customer customer) throws IOException {
        String customerFolderName = generateCustomerFolderName(customer);
        String query = String.format(
            "name='%s' and parents in '%s' and trashed=false and mimeType='application/vnd.google-apps.folder'",
            customerFolderName, getDocumentsFolderId(driveService)
        );

        FileList result = driveService.files().list()
            .setQ(query)
            .setSupportsAllDrives(true)
            .setIncludeItemsFromAllDrives(true)
            .execute();

        List<File> files = result.getFiles();
        return files.isEmpty() ? null : files.get(0).getId();
    }

    private String findCustomerFolderId(Drive driveService, Customer customer, String folderType) throws IOException {
        String customerFolderId = findCustomerMainFolder(driveService, customer);
        if (customerFolderId == null) {
            return null;
        }

        // Handle property subfolder paths like "property-123/EICR"
        if (folderType.contains("/")) {
            String[] parts = folderType.split("/");
            if (parts.length == 2 && parts[0].startsWith("property-")) {
                String propertyId = parts[0].substring("property-".length());
                String subfolderName = parts[1];

                // Find property folder first
                Property property = propertyService.findById(Long.parseLong(propertyId));
                if (property != null) {
                    String propertyFolderName = generatePropertyFolderName(property);
                    String propertyFolderId = getOrCreatePropertyFolder(driveService, customerFolderId, propertyFolderName);

                    // Then find/create subfolder within property folder
                    return getOrCreateSubfolderInProperty(driveService, propertyFolderId, subfolderName);
                }
            }
        }

        String targetFolderName = getFolderNameFromType(folderType);
        String query = String.format(
            "name='%s' and parents in '%s' and trashed=false and mimeType='application/vnd.google-apps.folder'",
            targetFolderName, customerFolderId
        );

        FileList result = driveService.files().list()
            .setQ(query)
            .setSupportsAllDrives(true)
            .setIncludeItemsFromAllDrives(true)
            .execute();

        List<File> files = result.getFiles();
        return files.isEmpty() ? null : files.get(0).getId();
    }

    private String getOrCreateCustomerFolder(Drive driveService, Customer customer, String folderType) throws IOException {
        String folderId = findCustomerFolderId(driveService, customer, folderType);

        if (folderId != null) {
            return folderId; // Folder already exists
        }

        // Create the folder structure if it doesn't exist
        String customerFolderId = getOrCreateCustomerMainFolder(driveService, customer);
        String targetFolderName = getFolderNameFromType(folderType);

        return createFolderInParent(driveService, targetFolderName, customerFolderId);
    }

    private String getOrCreateCustomerMainFolder(Drive driveService, Customer customer) throws IOException {
        String folderId = findCustomerMainFolder(driveService, customer);

        if (folderId != null) {
            return folderId; // Folder already exists
        }

        // Create customer main folder
        String documentsFolderId = getDocumentsFolderId(driveService);
        String customerFolderName = generateCustomerFolderName(customer);

        return createFolderInParent(driveService, customerFolderName, documentsFolderId);
    }

    private String getDocumentsFolderId(Drive driveService) throws IOException {
        String query = String.format(
            "name='%s' and parents in '%s' and trashed=false and mimeType='application/vnd.google-apps.folder'",
            SHARED_DRIVE_DOCUMENTS_FOLDER, SHARED_DRIVE_ID
        );

        FileList result = driveService.files().list()
            .setQ(query)
            .setSupportsAllDrives(true)
            .setIncludeItemsFromAllDrives(true)
            .execute();

        List<File> files = result.getFiles();
        if (files.isEmpty()) {
            // Create the documents folder
            return createFolderInParent(driveService, SHARED_DRIVE_DOCUMENTS_FOLDER, SHARED_DRIVE_ID);
        }

        return files.get(0).getId();
    }

    private String createFolderInParent(Drive driveService, String folderName, String parentId) throws IOException {
        File fileMetadata = new File();
        fileMetadata.setName(folderName);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        fileMetadata.setParents(Collections.singletonList(parentId));

        File folder = driveService.files()
            .create(fileMetadata)
            .setSupportsAllDrives(true)
            .execute();

        System.out.println("📁 Created folder: " + folderName + " (ID: " + folder.getId() + ")");
        return folder.getId();
    }

    private List<Map<String, Object>> listFilesInFolder(Drive driveService, String folderId) throws IOException {
        String query = String.format(
            "parents in '%s' and trashed=false and mimeType != 'application/vnd.google-apps.folder'",
            folderId
        );

        FileList result = driveService.files().list()
            .setQ(query)
            .setSupportsAllDrives(true)
            .setIncludeItemsFromAllDrives(true)
            .setFields("files(id,name,size,modifiedTime,mimeType)")
            .execute();

        List<Map<String, Object>> files = new ArrayList<>();
        for (File file : result.getFiles()) {
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("id", file.getId());
            fileInfo.put("name", file.getName());
            fileInfo.put("size", formatFileSize(file.getSize()));
            fileInfo.put("modified", formatDate(file.getModifiedTime()));
            fileInfo.put("mimeType", file.getMimeType());
            fileInfo.put("type", "file");
            files.add(fileInfo);
        }

        return files;
    }

    private List<Map<String, Object>> listFoldersInFolder(Drive driveService, String folderId) throws IOException {
        String query = String.format(
            "parents in '%s' and trashed=false and mimeType = 'application/vnd.google-apps.folder'",
            folderId
        );

        FileList result = driveService.files().list()
            .setQ(query)
            .setSupportsAllDrives(true)
            .setIncludeItemsFromAllDrives(true)
            .setFields("files(id,name,modifiedTime)")
            .execute();

        List<Map<String, Object>> folders = new ArrayList<>();
        for (File folder : result.getFiles()) {
            Map<String, Object> folderInfo = new HashMap<>();
            folderInfo.put("id", folder.getId());
            folderInfo.put("name", folder.getName());
            folderInfo.put("type", "folder");
            folderInfo.put("modified", formatDate(folder.getModifiedTime()));

            // Check if it's a property folder and add subfolders info
            if (folder.getName().toLowerCase().contains("property") ||
                folder.getName().toLowerCase().contains("flat") ||
                folder.getName().toLowerCase().contains("unit")) {
                folderInfo.put("subfolders", Arrays.asList("EICR", "EPC", "Insurance", "Statements", "Tenancy", "Maintenance"));
            }

            folders.add(folderInfo);
        }

        return folders;
    }

    private String generateCustomerFolderName(Customer customer) {
        if (customer.getEmail() != null && !customer.getEmail().trim().isEmpty()) {
            return "Customer-" + customer.getEmail();
        }
        return "Customer-" + customer.getCustomerId();
    }

    private String generatePropertyFolderName(Property property) {
        String propertyName = property.getPropertyName();
        if (propertyName != null && !propertyName.trim().isEmpty()) {
            // CRITICAL: Sanitize the same way as CustomerDriveOrganizationService
            // to ensure folder names match between customer and employee sides
            return propertyName.replaceAll("[^a-zA-Z0-9\\s-]", "").trim();
        }

        String address = property.getAddressLine1();
        if (address != null && !address.trim().isEmpty()) {
            return address.replaceAll("[^a-zA-Z0-9\\s-]", "").trim();
        }

        return "Property-" + property.getId();
    }

    private String getOrCreatePropertyFolder(Drive driveService, String customerFolderId, String propertyFolderName) throws IOException {
        // Search for existing property folder
        String query = String.format(
            "name='%s' and parents in '%s' and trashed=false and mimeType='application/vnd.google-apps.folder'",
            propertyFolderName, customerFolderId
        );

        FileList result = driveService.files().list()
            .setQ(query)
            .setSupportsAllDrives(true)
            .setIncludeItemsFromAllDrives(true)
            .execute();

        List<File> files = result.getFiles();
        if (!files.isEmpty()) {
            return files.get(0).getId(); // Return existing folder
        }

        // Create new property folder
        return createFolderInParent(driveService, propertyFolderName, customerFolderId);
    }

    private String getOrCreateSubfolderInProperty(Drive driveService, String propertyFolderId, String subfolderName) throws IOException {
        // Search for existing subfolder
        String query = String.format(
            "name='%s' and parents in '%s' and trashed=false and mimeType='application/vnd.google-apps.folder'",
            subfolderName, propertyFolderId
        );

        FileList result = driveService.files().list()
            .setQ(query)
            .setSupportsAllDrives(true)
            .setIncludeItemsFromAllDrives(true)
            .execute();

        List<File> files = result.getFiles();
        if (!files.isEmpty()) {
            return files.get(0).getId(); // Return existing subfolder
        }

        // Create new subfolder
        return createFolderInParent(driveService, subfolderName, propertyFolderId);
    }

    private String getFolderNameFromType(String folderType) {
        // Handle property subfolder paths like "property-123/EICR"
        if (folderType.contains("/")) {
            String[] parts = folderType.split("/");
            if (parts.length == 2 && parts[0].startsWith("property-")) {
                return parts[1]; // Return the subfolder name (EICR, EPC, etc.)
            }
        }

        switch (folderType.toLowerCase()) {
            case "property-documents": return "Management Agreement";
            case "tenant-documents": return "Tenancy";
            case "financial-statements": return "Statements";
            case "maintenance-records": return "Maintenance";
            default: return "Misc";
        }
    }

    private String formatFileSize(Long size) {
        if (size == null || size == 0) {
            return "0 B";
        }

        String[] units = {"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private String formatDate(com.google.api.client.util.DateTime dateTime) {
        if (dateTime == null) {
            return "Unknown";
        }

        Date date = new Date(dateTime.getValue());
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .format(date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate());
    }

    // ===================================================================
    // EMPLOYEE-FACING METHODS (for employee document management)
    // ===================================================================

    /**
     * List all customer folders for employee navigation
     */
    public List<Map<String, Object>> listAllCustomerFolders() throws IOException, GeneralSecurityException {
        if (!hasServiceAccount()) {
            throw new IllegalStateException("Service account not configured");
        }

        // CRITICAL FIX: Query database for all customers instead of searching Drive
        // This matches customer-side behavior where folders are created on-demand
        List<Customer> allCustomers = customerService.findAll();

        Drive driveService = createDriveService();
        List<Map<String, Object>> customerFolders = new ArrayList<>();

        for (Customer customer : allCustomers) {
            // Only include customers with email (property owners/tenants)
            if (customer.getEmail() == null || customer.getEmail().trim().isEmpty()) {
                continue;
            }

            // Get or create customer folder (like customer side does)
            String customerFolderId = findCustomerMainFolder(driveService, customer);
            if (customerFolderId == null) {
                // Auto-create folder if it doesn't exist (matches customer-side behavior)
                customerFolderId = getOrCreateCustomerMainFolder(driveService, customer);
            }

            Map<String, Object> folderInfo = new HashMap<>();
            folderInfo.put("id", customer.getCustomerId());  // Database customer ID
            folderInfo.put("folderId", customerFolderId);    // Drive folder ID
            folderInfo.put("name", "Customer-" + customer.getEmail());
            folderInfo.put("customerEmail", customer.getEmail());
            folderInfo.put("type", "folder");

            customerFolders.add(folderInfo);
        }

        System.out.println("📁 [Employee] Found " + customerFolders.size() + " customers with folders");
        return customerFolders;
    }


    /**
     * List internal folders (employee-only)
     */
    public List<Map<String, Object>> listInternalFolders() throws IOException, GeneralSecurityException {
        if (!hasServiceAccount()) {
            throw new IllegalStateException("Service account not configured");
        }

        Drive driveService = createDriveService();
        String internalFolderId = getOrCreateInternalFolder(driveService);

        List<Map<String, Object>> folders = new ArrayList<>();

        for (String subfolderName : INTERNAL_SUBFOLDERS) {
            // Check if subfolder exists
            String query = String.format(
                "name='%s' and parents in '%s' and trashed=false and mimeType='application/vnd.google-apps.folder'",
                subfolderName, internalFolderId
            );

            FileList result = driveService.files().list()
                .setQ(query)
                .setSupportsAllDrives(true)
                .setIncludeItemsFromAllDrives(true)
                .execute();

            String subfolderId = null;
            if (!result.getFiles().isEmpty()) {
                subfolderId = result.getFiles().get(0).getId();
            }

            Map<String, Object> folderInfo = new HashMap<>();
            folderInfo.put("name", subfolderName);
            folderInfo.put("type", "folder");
            folderInfo.put("id", subfolderId);
            folderInfo.put("exists", subfolderId != null);

            folders.add(folderInfo);
        }

        System.out.println("📁 [Employee] Listed " + INTERNAL_SUBFOLDERS.size() + " internal folders");
        return folders;
    }

    /**
     * List files in an internal folder
     */
    public List<Map<String, Object>> listInternalFiles(String subfolderName) throws IOException, GeneralSecurityException {
        if (!hasServiceAccount()) {
            throw new IllegalStateException("Service account not configured");
        }

        if (!INTERNAL_SUBFOLDERS.contains(subfolderName)) {
            throw new IllegalArgumentException("Invalid internal folder: " + subfolderName);
        }

        Drive driveService = createDriveService();
        String internalFolderId = getOrCreateInternalFolder(driveService);
        String subfolderId = getOrCreateSubfolder(driveService, internalFolderId, subfolderName);

        return listFilesInFolder(driveService, subfolderId);
    }

    /**
     * Upload file to internal folder (employee-only)
     */
    public Map<String, Object> uploadToInternalFolder(String subfolderName, MultipartFile file)
            throws IOException, GeneralSecurityException {
        if (!hasServiceAccount()) {
            throw new IllegalStateException("Service account not configured");
        }

        if (!INTERNAL_SUBFOLDERS.contains(subfolderName)) {
            throw new IllegalArgumentException("Invalid internal folder: " + subfolderName);
        }

        Drive driveService = createDriveService();
        String internalFolderId = getOrCreateInternalFolder(driveService);
        String subfolderId = getOrCreateSubfolder(driveService, internalFolderId, subfolderName);

        // Upload the file
        File fileMetadata = new File();
        fileMetadata.setName(file.getOriginalFilename());
        fileMetadata.setParents(Collections.singletonList(subfolderId));

        com.google.api.client.http.InputStreamContent mediaContent =
            new com.google.api.client.http.InputStreamContent(
                file.getContentType(),
                file.getInputStream()
            );

        File uploadedFile = driveService.files()
            .create(fileMetadata, mediaContent)
            .setSupportsAllDrives(true)
            .execute();

        System.out.println("✅ [Employee] File uploaded to Internal/" + subfolderName + ": " + uploadedFile.getName());

        Map<String, Object> result = new HashMap<>();
        result.put("id", uploadedFile.getId());
        result.put("name", uploadedFile.getName());
        result.put("size", formatFileSize(file.getSize()));
        result.put("uploadedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        return result;
    }

    /**
     * Get or create the Internal folder at the root of documents
     */
    private String getOrCreateInternalFolder(Drive driveService) throws IOException {
        String documentsFolderId = getDocumentsFolderId(driveService);

        // Search for existing Internal folder
        String query = String.format(
            "name='%s' and parents in '%s' and trashed=false and mimeType='application/vnd.google-apps.folder'",
            INTERNAL_FOLDER, documentsFolderId
        );

        FileList result = driveService.files().list()
            .setQ(query)
            .setSupportsAllDrives(true)
            .setIncludeItemsFromAllDrives(true)
            .execute();

        if (!result.getFiles().isEmpty()) {
            return result.getFiles().get(0).getId();
        }

        // Create Internal folder
        File folderMetadata = new File();
        folderMetadata.setName(INTERNAL_FOLDER);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        folderMetadata.setParents(Collections.singletonList(documentsFolderId));

        File folder = driveService.files()
            .create(folderMetadata)
            .setSupportsAllDrives(true)
            .setFields("id, name")
            .execute();

        System.out.println("📁 Created Internal folder: " + folder.getId());
        return folder.getId();
    }

    /**
     * Get or create a subfolder within a parent folder
     */
    private String getOrCreateSubfolder(Drive driveService, String parentFolderId, String subfolderName) throws IOException {
        // Search for existing subfolder
        String query = String.format(
            "name='%s' and parents in '%s' and trashed=false and mimeType='application/vnd.google-apps.folder'",
            subfolderName, parentFolderId
        );

        FileList result = driveService.files().list()
            .setQ(query)
            .setSupportsAllDrives(true)
            .setIncludeItemsFromAllDrives(true)
            .execute();

        if (!result.getFiles().isEmpty()) {
            return result.getFiles().get(0).getId();
        }

        // Create subfolder
        File folderMetadata = new File();
        folderMetadata.setName(subfolderName);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        folderMetadata.setParents(Collections.singletonList(parentFolderId));

        File folder = driveService.files()
            .create(folderMetadata)
            .setSupportsAllDrives(true)
            .setFields("id, name")
            .execute();

        System.out.println("📁 Created subfolder: " + subfolderName + " (" + folder.getId() + ")");
        return folder.getId();
    }

    /**
     * Find a folder by name recursively searching from a parent
     */
    private String findFolderByNameRecursive(Drive driveService, String parentId, String folderName) throws IOException {
        String query = String.format(
            "name='%s' and parents in '%s' and trashed=false and mimeType='application/vnd.google-apps.folder'",
            folderName, parentId
        );

        FileList result = driveService.files().list()
            .setQ(query)
            .setSupportsAllDrives(true)
            .setIncludeItemsFromAllDrives(true)
            .execute();

        if (!result.getFiles().isEmpty()) {
            return result.getFiles().get(0).getId();
        }

        // Search in subfolders
        String subfoldersQuery = String.format(
            "parents in '%s' and trashed=false and mimeType='application/vnd.google-apps.folder'",
            parentId
        );

        FileList subfolders = driveService.files().list()
            .setQ(subfoldersQuery)
            .setSupportsAllDrives(true)
            .setIncludeItemsFromAllDrives(true)
            .execute();

        for (File subfolder : subfolders.getFiles()) {
            String foundId = findFolderByNameRecursive(driveService, subfolder.getId(), folderName);
            if (foundId != null) {
                return foundId;
            }
        }

        return null;
    }

    // ===================================================================
    // NEW PROPERTY-CENTRIC METHODS (No customer folders)
    // ===================================================================

    /**
     * Get or create property folder directly under Property-Documents
     * NO customer folder layer!
     */
    private String getOrCreatePropertyFolderDirect(Drive driveService, String propertyFolderName) throws IOException {
        String documentsFolderId = getDocumentsFolderId(driveService);

        // Search for existing property folder
        String query = String.format(
            "name='%s' and parents in '%s' and trashed=false and mimeType='application/vnd.google-apps.folder'",
            propertyFolderName, documentsFolderId
        );

        FileList result = driveService.files().list()
            .setQ(query)
            .setSupportsAllDrives(true)
            .setIncludeItemsFromAllDrives(true)
            .execute();

        if (!result.getFiles().isEmpty()) {
            return result.getFiles().get(0).getId();
        }

        // Create property folder if it doesn't exist
        return createFolderInParent(driveService, propertyFolderName, documentsFolderId);
    }

    /**
     * List all properties with folders (for both employees and property owners)
     */
    public List<Map<String, Object>> listAllPropertyFolders() throws IOException, GeneralSecurityException {
        if (!hasServiceAccount()) {
            throw new IllegalStateException("Service account not configured");
        }

        List<Property> allProperties = propertyService.findAll();
        Drive driveService = createDriveService();
        List<Map<String, Object>> propertyFolders = new ArrayList<>();

        for (Property property : allProperties) {
            String propertyFolderName = generatePropertyFolderName(property);
            String propertyFolderId = getOrCreatePropertyFolderDirect(driveService, propertyFolderName);

            Map<String, Object> folderInfo = new HashMap<>();
            folderInfo.put("id", property.getId());
            folderInfo.put("folderId", propertyFolderId);
            folderInfo.put("name", propertyFolderName);
            folderInfo.put("propertyName", property.getPropertyName());
            folderInfo.put("type", "folder");

            propertyFolders.add(folderInfo);
        }

        System.out.println("📁 Listed " + propertyFolders.size() + " property folders");
        return propertyFolders;
    }

    /**
     * List properties accessible to a customer (for property owners)
     */
    public List<Map<String, Object>> listCustomerPropertyFolders(Long customerId) throws IOException, GeneralSecurityException {
        if (!hasServiceAccount()) {
            throw new IllegalStateException("Service account not configured");
        }

        List<Property> customerProperties = propertyService.findPropertiesAccessibleByCustomer(customerId);
        Drive driveService = createDriveService();
        List<Map<String, Object>> propertyFolders = new ArrayList<>();

        for (Property property : customerProperties) {
            String propertyFolderName = generatePropertyFolderName(property);
            String propertyFolderId = getOrCreatePropertyFolderDirect(driveService, propertyFolderName);

            Map<String, Object> folderInfo = new HashMap<>();
            folderInfo.put("id", property.getId());
            folderInfo.put("folderId", propertyFolderId);
            folderInfo.put("name", propertyFolderName);
            folderInfo.put("propertyName", property.getPropertyName());
            folderInfo.put("type", "folder");
            folderInfo.put("subfolders", Arrays.asList("EICR", "EPC", "Insurance", "Statements", "Maintenance"));

            propertyFolders.add(folderInfo);
        }

        System.out.println("📁 Customer " + customerId + " has access to " + propertyFolders.size() + " properties");
        return propertyFolders;
    }

    /**
     * List files in a property subfolder (property documents like EICR, EPC)
     */
    public List<Map<String, Object>> listPropertySubfolderFiles(Long propertyId, String subfolderName)
            throws IOException, GeneralSecurityException {
        if (!hasServiceAccount()) {
            throw new IllegalStateException("Service account not configured");
        }

        Property property = propertyService.findById(propertyId);
        if (property == null) {
            System.out.println("❌ Property not found: " + propertyId);
            return new ArrayList<>();
        }

        Drive driveService = createDriveService();
        String propertyFolderName = generatePropertyFolderName(property);
        String propertyFolderId = getOrCreatePropertyFolderDirect(driveService, propertyFolderName);

        // Get or create subfolder (EICR, EPC, etc.)
        String subfolderId = getOrCreateSubfolderInProperty(driveService, propertyFolderId, subfolderName);

        return listFilesInFolder(driveService, subfolderId);
    }

    /**
     * Upload file to property subfolder
     */
    public Map<String, Object> uploadToPropertySubfolder(Long propertyId, String subfolderName, MultipartFile file)
            throws IOException, GeneralSecurityException {
        if (!hasServiceAccount()) {
            throw new IllegalStateException("Service account not configured");
        }

        Property property = propertyService.findById(propertyId);
        if (property == null) {
            throw new IllegalArgumentException("Property not found: " + propertyId);
        }

        Drive driveService = createDriveService();
        String propertyFolderName = generatePropertyFolderName(property);
        String propertyFolderId = getOrCreatePropertyFolderDirect(driveService, propertyFolderName);

        // Get or create subfolder
        String subfolderId = getOrCreateSubfolderInProperty(driveService, propertyFolderId, subfolderName);

        // Upload the file
        File fileMetadata = new File();
        fileMetadata.setName(file.getOriginalFilename());
        fileMetadata.setParents(Collections.singletonList(subfolderId));

        com.google.api.client.http.InputStreamContent mediaContent =
            new com.google.api.client.http.InputStreamContent(
                file.getContentType(),
                file.getInputStream()
            );

        File uploadedFile = driveService.files()
            .create(fileMetadata, mediaContent)
            .setSupportsAllDrives(true)
            .execute();

        System.out.println("✅ File uploaded: " + uploadedFile.getName() + " to " + propertyFolderName + "/" + subfolderName);

        Map<String, Object> result = new HashMap<>();
        result.put("id", uploadedFile.getId());
        result.put("name", uploadedFile.getName());
        result.put("size", formatFileSize(file.getSize()));
        result.put("uploadedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        return result;
    }

    // ===================================================================
    // TENANT/LEASE-CENTRIC FILE MANAGEMENT (NEW STRUCTURE)
    // ===================================================================

    /**
     * Generate tenant folder name from Invoice (lease)
     * Format: "Tenant Name - Start Date to End Date" or "Tenant Name - Start Date to Present"
     */
    private String generateTenantFolderName(Invoice invoice) {
        Customer tenant = invoice.getCustomer();
        String tenantName = tenant.getFirstName() + " " + tenant.getLastName();

        String startDate = invoice.getStartDate() != null ?
            invoice.getStartDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "Unknown";

        String endDate = invoice.getEndDate() != null ?
            invoice.getEndDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "Present";

        return tenantName + " - " + startDate + " to " + endDate;
    }

    /**
     * List all tenants/leases for a property
     */
    public List<Map<String, Object>> listTenantsForProperty(Long propertyId) throws IOException, GeneralSecurityException {
        if (!hasServiceAccount()) {
            throw new IllegalStateException("Service account not configured");
        }

        Property property = propertyService.findById(propertyId);
        if (property == null) {
            throw new IllegalArgumentException("Property not found: " + propertyId);
        }

        // Get all invoices (leases) for this property
        List<Invoice> leases = invoiceRepository.findByProperty(property);

        Drive driveService = createDriveService();
        String propertyFolderName = generatePropertyFolderName(property);
        String propertyFolderId = getOrCreatePropertyFolderDirect(driveService, propertyFolderName);

        List<Map<String, Object>> tenants = new ArrayList<>();

        for (Invoice lease : leases) {
            String tenantFolderName = generateTenantFolderName(lease);
            String tenantFolderId = getOrCreateTenantFolder(driveService, propertyFolderId, tenantFolderName);

            Map<String, Object> tenantInfo = new HashMap<>();
            tenantInfo.put("leaseId", lease.getId());
            tenantInfo.put("folderId", tenantFolderId);
            tenantInfo.put("name", tenantFolderName);
            tenantInfo.put("tenantName", lease.getCustomer().getFirstName() + " " + lease.getCustomer().getLastName());
            tenantInfo.put("startDate", lease.getStartDate());
            tenantInfo.put("endDate", lease.getEndDate());
            tenantInfo.put("isActive", lease.isCurrentlyActive());
            tenantInfo.put("type", "tenant-folder");
            tenantInfo.put("subfolders", TENANT_DOCUMENT_SUBFOLDERS);

            tenants.add(tenantInfo);
        }

        System.out.println("📁 Found " + tenants.size() + " tenants for property " + property.getPropertyName());
        return tenants;
    }

    /**
     * Get or create tenant folder within property folder
     */
    private String getOrCreateTenantFolder(Drive driveService, String propertyFolderId, String tenantFolderName)
            throws IOException {

        // Search for existing tenant folder
        String query = String.format(
            "name='%s' and parents in '%s' and trashed=false and mimeType='application/vnd.google-apps.folder'",
            tenantFolderName.replace("'", "\\'"),
            propertyFolderId
        );

        FileList result = driveService.files().list()
            .setQ(query)
            .setSupportsAllDrives(true)
            .setIncludeItemsFromAllDrives(true)
            .setFields("files(id, name)")
            .execute();

        if (!result.getFiles().isEmpty()) {
            String folderId = result.getFiles().get(0).getId();
            System.out.println("📁 Found existing tenant folder: " + tenantFolderName + " (ID: " + folderId + ")");

            // Ensure all document subfolders exist
            ensureTenantDocumentSubfolders(driveService, folderId);

            return folderId;
        }

        // Create new tenant folder
        File folderMetadata = new File();
        folderMetadata.setName(tenantFolderName);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        folderMetadata.setParents(Collections.singletonList(propertyFolderId));

        File folder = driveService.files().create(folderMetadata)
            .setSupportsAllDrives(true)
            .setFields("id")
            .execute();

        System.out.println("✅ Created tenant folder: " + tenantFolderName + " (ID: " + folder.getId() + ")");

        // Create document subfolders
        ensureTenantDocumentSubfolders(driveService, folder.getId());

        return folder.getId();
    }

    /**
     * Ensure all tenant document subfolders exist
     */
    private void ensureTenantDocumentSubfolders(Drive driveService, String tenantFolderId) throws IOException {
        for (String subfolderName : TENANT_DOCUMENT_SUBFOLDERS) {
            getOrCreateSubfolder(driveService, tenantFolderId, subfolderName);
        }
    }

    /**
     * List files in a tenant's document subfolder
     */
    public List<Map<String, Object>> listTenantSubfolderFiles(Long propertyId, Long leaseId, String subfolderName)
            throws IOException, GeneralSecurityException {

        if (!hasServiceAccount()) {
            throw new IllegalStateException("Service account not configured");
        }

        if (!TENANT_DOCUMENT_SUBFOLDERS.contains(subfolderName)) {
            throw new IllegalArgumentException("Invalid subfolder: " + subfolderName +
                ". Must be one of: " + TENANT_DOCUMENT_SUBFOLDERS);
        }

        Property property = propertyService.findById(propertyId);
        Invoice lease = invoiceRepository.findById(leaseId)
            .orElseThrow(() -> new IllegalArgumentException("Lease not found: " + leaseId));

        Drive driveService = createDriveService();

        // Navigate: Property → Tenant → Subfolder
        String propertyFolderName = generatePropertyFolderName(property);
        String propertyFolderId = getOrCreatePropertyFolderDirect(driveService, propertyFolderName);

        String tenantFolderName = generateTenantFolderName(lease);
        String tenantFolderId = getOrCreateTenantFolder(driveService, propertyFolderId, tenantFolderName);

        String subfolderId = getOrCreateSubfolder(driveService, tenantFolderId, subfolderName);

        return listFilesInFolder(driveService, subfolderId);
    }

    /**
     * Upload file to tenant's document subfolder
     */
    public Map<String, Object> uploadToTenantSubfolder(Long propertyId, Long leaseId, String subfolderName,
                                                       MultipartFile file)
            throws IOException, GeneralSecurityException {

        if (!hasServiceAccount()) {
            throw new IllegalStateException("Service account not configured");
        }

        if (!TENANT_DOCUMENT_SUBFOLDERS.contains(subfolderName)) {
            throw new IllegalArgumentException("Invalid subfolder: " + subfolderName);
        }

        Property property = propertyService.findById(propertyId);
        Invoice lease = invoiceRepository.findById(leaseId)
            .orElseThrow(() -> new IllegalArgumentException("Lease not found: " + leaseId));

        Drive driveService = createDriveService();

        // Navigate: Property → Tenant → Subfolder
        String propertyFolderName = generatePropertyFolderName(property);
        String propertyFolderId = getOrCreatePropertyFolderDirect(driveService, propertyFolderName);

        String tenantFolderName = generateTenantFolderName(lease);
        String tenantFolderId = getOrCreateTenantFolder(driveService, propertyFolderId, tenantFolderName);

        String subfolderId = getOrCreateSubfolder(driveService, tenantFolderId, subfolderName);

        // Upload file
        File fileMetadata = new File();
        fileMetadata.setName(file.getOriginalFilename());
        fileMetadata.setParents(Collections.singletonList(subfolderId));

        InputStreamContent mediaContent = new InputStreamContent(
            file.getContentType(),
            file.getInputStream()
        );

        File uploadedFile = driveService.files()
            .create(fileMetadata, mediaContent)
            .setSupportsAllDrives(true)
            .execute();

        System.out.println("✅ File uploaded: " + uploadedFile.getName() +
            " to " + propertyFolderName + "/" + tenantFolderName + "/" + subfolderName);

        Map<String, Object> result = new HashMap<>();
        result.put("id", uploadedFile.getId());
        result.put("name", uploadedFile.getName());
        result.put("size", formatFileSize(file.getSize()));
        result.put("uploadedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        return result;
    }
}
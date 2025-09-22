package site.easy.to.build.crm.service.drive;

import com.google.api.services.drive.model.File;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.GoogleDriveFile;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.google.service.drive.GoogleDriveApiService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.*;

// Shared drive imports
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import java.io.ByteArrayInputStream;
import java.util.Collections;

@Service
public class CustomerDriveOrganizationService {

    private final GoogleDriveApiService googleDriveApiService;
    private final GoogleDriveFileService googleDriveFileService;
    private final CustomerService customerService;
    private final PropertyService propertyService;

    @Value("${GOOGLE_SERVICE_ACCOUNT_KEY:}")
    private String serviceAccountKey;

    // Shared Drive ID for CRM property documents
    private static final String SHARED_DRIVE_ID = "0ADaFlidiFrFDUk9PVA";
    private static final String SHARED_DRIVE_DOCUMENTS_FOLDER = "Property-Documents";

    // Folder structure definitions
    private static final Map<CustomerType, List<String>> FOLDER_STRUCTURES = Map.of(
        CustomerType.TENANT, Arrays.asList("Tenancy", "Right to Rent", "ID", "Deposit Details", "Misc", "Letters"),
        CustomerType.PROPERTY_OWNER, Arrays.asList("Management Agreement", "Misc"),
        CustomerType.CONTRACTOR, Arrays.asList("Contracts", "Certifications", "Insurance", "Invoices", "Misc")
    );

    private static final List<String> PROPERTY_FOLDERS = Arrays.asList(
        "EPC", "Insurance", "EICR", "Misc", "Statements", "Invoices", "Letters"
    );

    @Autowired
    public CustomerDriveOrganizationService(GoogleDriveApiService googleDriveApiService,
                                          GoogleDriveFileService googleDriveFileService,
                                          CustomerService customerService,
                                          PropertyService propertyService) {
        this.googleDriveApiService = googleDriveApiService;
        this.googleDriveFileService = googleDriveFileService;
        this.customerService = customerService;
        this.propertyService = propertyService;
    }

    /**
     * Creates complete folder structure for a customer
     */
    public CustomerFolderStructure createCustomerFolderStructure(OAuthUser oAuthUser, Customer customer) 
            throws IOException, GeneralSecurityException {
        
        // Create main customer folder
        String customerFolderName = generateCustomerFolderName(customer);
        String customerFolderId = googleDriveApiService.createFolder(oAuthUser, customerFolderName);
        
        CustomerFolderStructure structure = new CustomerFolderStructure();
        structure.setCustomerId(customer.getCustomerId());
        structure.setMainFolderId(customerFolderId);
        structure.setMainFolderName(customerFolderName);
        
        Map<String, String> subFolders = new HashMap<>();
        
        // Create customer-type specific folders
        List<String> folderNames = FOLDER_STRUCTURES.get(customer.getCustomerType());
        if (folderNames != null) {
            for (String folderName : folderNames) {
                String subFolderId = googleDriveApiService.createFolderInParent(oAuthUser, folderName, customerFolderId);
                subFolders.put(folderName, subFolderId);
            }
        }
        
        // For property owners, also create property-specific folders
        if (customer.getCustomerType() == CustomerType.PROPERTY_OWNER) {
            List<Long> propertyIds = getCustomerPropertyIds(customer);
            for (Long propertyId : propertyIds) {
                String propertyFolderName = "Property_" + propertyId;
                String propertyFolderId = googleDriveApiService.createFolderInParent(oAuthUser, propertyFolderName, customerFolderId);
                
                // Create property subfolders
                for (String propertyFolder : PROPERTY_FOLDERS) {
                    String propSubFolderId = googleDriveApiService.createFolderInParent(oAuthUser, propertyFolder, propertyFolderId);
                    subFolders.put(propertyFolderName + "/" + propertyFolder, propSubFolderId);
                }
            }
        }
        
        structure.setSubFolders(subFolders);
        
        // Save folder structure to database
        saveFolderStructureToDatabase(structure);
        
        return structure;
    }

    /**
     * Organizes existing file into appropriate customer folder
     */
    public void organizeFileForCustomer(OAuthUser oAuthUser, String driveFileId, Customer customer, 
                                       String category, String description) throws IOException, GeneralSecurityException {
        
        CustomerFolderStructure structure = getOrCreateCustomerFolderStructure(oAuthUser, customer);
        
        // Determine target folder based on category
        String targetFolderId = determineTargetFolder(structure, category, customer);
        
        // Move file to appropriate folder
        googleDriveApiService.moveFileToFolder(oAuthUser, driveFileId, targetFolderId);
        
        // Update database record
        GoogleDriveFile googleDriveFile = new GoogleDriveFile();
        googleDriveFile.setDriveFileId(driveFileId);
        googleDriveFile.setGoogleDriveFolderId(targetFolderId);
        googleDriveFile.setCustomerId(customer.getCustomerId().intValue());
        googleDriveFile.setFileCategory(category);
        googleDriveFile.setFileDescription(description);
        googleDriveFile.setCreatedAt(LocalDateTime.now());
        
        googleDriveFileService.save(googleDriveFile);
    }

    /**
     * Handles PayProp file synchronization
     */
    public void syncPayPropFile(OAuthUser oAuthUser, Customer customer, byte[] fileData, 
                               String fileName, String payPropEntityType) throws IOException, GeneralSecurityException {
        
        CustomerFolderStructure structure = getOrCreateCustomerFolderStructure(oAuthUser, customer);
        
        // Create file in appropriate folder
        String targetFolderId = determinePayPropTargetFolder(structure, payPropEntityType, customer);
        String driveFileId = googleDriveApiService.uploadFile(oAuthUser, fileName, fileData, targetFolderId);
        
        // Save to database with PayProp flag
        GoogleDriveFile googleDriveFile = new GoogleDriveFile();
        googleDriveFile.setDriveFileId(driveFileId);
        googleDriveFile.setGoogleDriveFolderId(targetFolderId);
        googleDriveFile.setCustomerId(customer.getCustomerId().intValue());
        googleDriveFile.setFileName(fileName);
        googleDriveFile.setFileCategory("payprop");
        googleDriveFile.setFileDescription("PayProp sync: " + payPropEntityType);
        googleDriveFile.setIsPayPropFile(true);
        googleDriveFile.setPayPropSyncDate(LocalDateTime.now());
        googleDriveFile.setCreatedAt(LocalDateTime.now());
        
        googleDriveFileService.save(googleDriveFile);
    }

    /**
     * Gets customer folder structure, creating if it doesn't exist
     */
    public CustomerFolderStructure getOrCreateCustomerFolderStructure(OAuthUser oAuthUser, Customer customer) 
            throws IOException, GeneralSecurityException {
        
        CustomerFolderStructure existing = getCustomerFolderStructure(customer.getCustomerId());
        if (existing != null) {
            return existing;
        }
        
        return createCustomerFolderStructure(oAuthUser, customer);
    }

    /**
     * Retrieves customer files by category with access control
     */
    public List<GoogleDriveFile> getCustomerFiles(Customer customer, String category, CustomerType accessLevel) {
        List<GoogleDriveFile> files = new ArrayList<>();
        
        switch (accessLevel) {
            case TENANT:
                // Tenants can only see their own files
                if (customer.getCustomerType() == CustomerType.TENANT) {
                    files = googleDriveFileService.getFilesByCustomerAndCategory(customer.getCustomerId().intValue(), category);
                }
                break;
                
            case PROPERTY_OWNER:
                // Property owners can see property and tenancy files, but not property owner files
                if (customer.getCustomerType() == CustomerType.PROPERTY_OWNER) {
                    files = googleDriveFileService.getFilesByCustomerAndCategory(customer.getCustomerId().intValue(), category);
                    
                    // Also get property-related files
                    List<Long> propertyIds = getCustomerPropertyIds(customer);
                    for (Long propertyId : propertyIds) {
                        files.addAll(googleDriveFileService.getFilesByPropertyAndCategory(propertyId, category));
                    }
                }
                break;
                
            default:
                // Employees can see all files
                files = googleDriveFileService.getFilesByCustomerAndCategory(customer.getCustomerId().intValue(), category);
                break;
        }
        
        return files;
    }

    // Helper methods
    private String generateCustomerFolderName(Customer customer) {
        String prefix = customer.getCustomerType().name().substring(0, 2);
        String name = customer.getName() != null ? customer.getName() : "Customer";
        return String.format("%s_%d_%s", prefix, customer.getCustomerId(), name.replaceAll("[^a-zA-Z0-9]", "_"));
    }

    private String determineTargetFolder(CustomerFolderStructure structure, String category, Customer customer) {
        if (category == null) category = "Misc";
        
        // Check if category exists in subfolders
        String folderId = structure.getSubFolders().get(category);
        if (folderId != null) {
            return folderId;
        }
        
        // Default to Misc folder
        return structure.getSubFolders().get("Misc");
    }

    private String determinePayPropTargetFolder(CustomerFolderStructure structure, String payPropEntityType, Customer customer) {
        // Map PayProp entity types to folder categories
        Map<String, String> payPropToFolderMap = Map.of(
            "tenant", "Tenancy",
            "beneficiary", "Statements",
            "property", "Statements",
            "maintenance-ticket", "Misc",
            "invoice", "Invoices"
        );
        
        String category = payPropToFolderMap.getOrDefault(payPropEntityType, "Misc");
        return determineTargetFolder(structure, category, customer);
    }

    private List<Long> getCustomerPropertyIds(Customer customer) {
        // Implementation depends on your property assignment logic
        // This is a placeholder - you'll need to implement based on your relationships
        return Arrays.asList(); // Replace with actual property lookup
    }

    private CustomerFolderStructure getCustomerFolderStructure(Long customerId) {
        // This would retrieve from a new table or cache
        // For now, return null to trigger creation
        return null;
    }

    private void saveFolderStructureToDatabase(CustomerFolderStructure structure) {
        // Save folder structure to database for future reference
        // You might want to create a customer_folder_structures table
    }

    /**
     * Create customer folder structure in shared drive (service account approach)
     * This allows property owners to access their documents without connecting Google accounts
     */
    public CustomerFolderStructure createCustomerFolderStructureInSharedDrive(Customer customer)
            throws IOException, GeneralSecurityException {

        System.out.println("📁 Creating customer folder structure in shared drive for: " + customer.getName());

        if (!hasServiceAccount()) {
            throw new IllegalStateException("Service account not configured for shared drive access");
        }

        Drive driveService = createServiceAccountDriveService();

        // Create or find the main documents folder in shared drive
        String documentsFolderId = findOrCreateDocumentsFolder(driveService);

        // Create customer folder under documents
        String customerFolderName = generateCustomerFolderName(customer);
        String customerFolderId = createFolderInSharedDrive(driveService, customerFolderName, documentsFolderId);

        // Grant the customer access to their folder
        grantCustomerAccess(driveService, customerFolderId, customer.getEmail());

        CustomerFolderStructure structure = new CustomerFolderStructure();
        structure.setCustomerId(customer.getCustomerId());
        structure.setMainFolderId(customerFolderId);
        structure.setMainFolderName(customerFolderName);

        Map<String, String> subFolders = new HashMap<>();

        // Create customer-type specific folders
        List<String> folderNames = FOLDER_STRUCTURES.get(customer.getCustomerType());
        if (folderNames != null) {
            for (String folderName : folderNames) {
                String subFolderId = createFolderInSharedDrive(driveService, folderName, customerFolderId);
                subFolders.put(folderName, subFolderId);
            }
        }

        // For property owners, create property-specific folders
        if (customer.getCustomerType() == CustomerType.PROPERTY_OWNER) {
            List<Property> properties = getCustomerProperties(customer);
            for (Property property : properties) {
                String propertyFolderName = generatePropertyFolderName(property);
                String propertyFolderId = createFolderInSharedDrive(driveService, propertyFolderName, customerFolderId);

                // Create property subfolders (EICR, EPC, Insurance, etc.)
                for (String propertyFolder : PROPERTY_FOLDERS) {
                    String subFolderId = createFolderInSharedDrive(driveService, propertyFolder, propertyFolderId);
                    subFolders.put(propertyFolderName + "/" + propertyFolder, subFolderId);
                }
            }
        }

        structure.setSubFolders(subFolders);

        System.out.println("✅ Created shared drive folder structure with " + subFolders.size() + " subfolders");
        return structure;
    }

    /**
     * Hybrid method: Create folder structure using either OAuth or service account
     */
    public CustomerFolderStructure createCustomerFolderStructureHybrid(Customer customer, OAuthUser oAuthUser)
            throws IOException, GeneralSecurityException {

        // Prefer shared drive if available
        if (hasServiceAccount()) {
            System.out.println("📁 Using shared drive approach for customer folders");
            return createCustomerFolderStructureInSharedDrive(customer);
        } else if (oAuthUser != null) {
            System.out.println("📁 Using OAuth approach for customer folders");
            return createCustomerFolderStructure(oAuthUser, customer);
        } else {
            throw new IllegalStateException("Neither service account nor OAuth available for folder creation");
        }
    }

    // Helper methods for shared drive operations

    private boolean hasServiceAccount() {
        return serviceAccountKey != null && !serviceAccountKey.trim().isEmpty();
    }

    private Drive createServiceAccountDriveService() throws IOException, GeneralSecurityException {
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

    private String findOrCreateDocumentsFolder(Drive driveService) throws IOException {
        // Search for existing documents folder in shared drive
        String query = "name='" + SHARED_DRIVE_DOCUMENTS_FOLDER + "' and parents in '" + SHARED_DRIVE_ID + "' and trashed=false";

        com.google.api.services.drive.Drive.Files.List request = driveService.files().list()
            .setQ(query)
            .setSupportsAllDrives(true)
            .setIncludeItemsFromAllDrives(true);

        List<com.google.api.services.drive.model.File> files = request.execute().getFiles();

        if (!files.isEmpty()) {
            return files.get(0).getId();
        }

        // Create the documents folder if it doesn't exist
        return createFolderInSharedDrive(driveService, SHARED_DRIVE_DOCUMENTS_FOLDER, SHARED_DRIVE_ID);
    }

    private String createFolderInSharedDrive(Drive driveService, String folderName, String parentId) throws IOException {
        com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
        fileMetadata.setName(folderName);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        fileMetadata.setParents(Collections.singletonList(parentId));

        com.google.api.services.drive.model.File file = driveService.files()
            .create(fileMetadata)
            .setSupportsAllDrives(true)
            .execute();

        System.out.println("📁 Created folder: " + folderName + " (ID: " + file.getId() + ")");
        return file.getId();
    }

    private void grantCustomerAccess(Drive driveService, String folderId, String customerEmail) {
        if (customerEmail == null || customerEmail.trim().isEmpty()) {
            System.out.println("⚠️ Customer email not provided, skipping access grant");
            return;
        }

        try {
            com.google.api.services.drive.model.Permission permission = new com.google.api.services.drive.model.Permission();
            permission.setRole("writer"); // Can add/edit files in their folder
            permission.setType("user");
            permission.setEmailAddress(customerEmail);

            driveService.permissions().create(folderId, permission)
                .setSupportsAllDrives(true)
                .execute();

            System.out.println("✅ Granted access to " + customerEmail + " for folder: " + folderId);
        } catch (Exception e) {
            System.err.println("⚠️ Could not grant access to customer: " + e.getMessage());
        }
    }

    private List<Property> getCustomerProperties(Customer customer) {
        try {
            // Use the same method as PropertyOwnerController for consistency
            return propertyService.findPropertiesByCustomerAssignments(customer.getCustomerId());
        } catch (Exception e) {
            System.err.println("⚠️ Error getting customer properties: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private String generatePropertyFolderName(Property property) {
        if (property.getPropertyName() != null && !property.getPropertyName().trim().isEmpty()) {
            return property.getPropertyName().replaceAll("[^a-zA-Z0-9\\s-]", "").trim();
        }

        String address = property.getAddressLine1();
        if (address != null && !address.trim().isEmpty()) {
            return address.replaceAll("[^a-zA-Z0-9\\s-]", "").trim();
        }

        return "Property-" + property.getId();
    }

    // Inner class for folder structure
    public static class CustomerFolderStructure {
        private Long customerId;
        private String mainFolderId;
        private String mainFolderName;
        private Map<String, String> subFolders;

        // Getters and setters
        public Long getCustomerId() { return customerId; }
        public void setCustomerId(Long customerId) { this.customerId = customerId; }

        public String getMainFolderId() { return mainFolderId; }
        public void setMainFolderId(String mainFolderId) { this.mainFolderId = mainFolderId; }

        public String getMainFolderName() { return mainFolderName; }
        public void setMainFolderName(String mainFolderName) { this.mainFolderName = mainFolderName; }

        public Map<String, String> getSubFolders() { return subFolders; }
        public void setSubFolders(Map<String, String> subFolders) { this.subFolders = subFolders; }
    }
}
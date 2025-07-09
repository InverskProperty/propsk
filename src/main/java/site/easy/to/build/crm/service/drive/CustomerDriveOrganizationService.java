package site.easy.to.build.crm.service.drive;

import com.google.api.services.drive.model.File;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.GoogleDriveFile;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.google.service.drive.GoogleDriveApiService;
import site.easy.to.build.crm.service.customer.CustomerService;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class CustomerDriveOrganizationService {

    private final GoogleDriveApiService googleDriveApiService;
    private final GoogleDriveFileService googleDriveFileService;
    private final CustomerService customerService;

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
                                          CustomerService customerService) {
        this.googleDriveApiService = googleDriveApiService;
        this.googleDriveFileService = googleDriveFileService;
        this.customerService = customerService;
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
        googleDriveFile.setDriveFolderId(targetFolderId);
        googleDriveFile.setCustomerId(customer.getCustomerId());
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
        googleDriveFile.setDriveFolderId(targetFolderId);
        googleDriveFile.setCustomerId(customer.getCustomerId());
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
                    files = googleDriveFileService.getFilesByCustomerAndCategory(customer.getCustomerId(), category);
                }
                break;
                
            case PROPERTY_OWNER:
                // Property owners can see property and tenancy files, but not property owner files
                if (customer.getCustomerType() == CustomerType.PROPERTY_OWNER) {
                    files = googleDriveFileService.getFilesByCustomerAndCategory(customer.getCustomerId(), category);
                    
                    // Also get property-related files
                    List<Long> propertyIds = getCustomerPropertyIds(customer);
                    for (Long propertyId : propertyIds) {
                        files.addAll(googleDriveFileService.getFilesByPropertyAndCategory(propertyId, category));
                    }
                }
                break;
                
            default:
                // Employees can see all files
                files = googleDriveFileService.getFilesByCustomerAndCategory(customer.getCustomerId(), category);
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

    private CustomerFolderStructure getCustomerFolderStructure(int customerId) {
        // This would retrieve from a new table or cache
        // For now, return null to trigger creation
        return null;
    }

    private void saveFolderStructureToDatabase(CustomerFolderStructure structure) {
        // Save folder structure to database for future reference
        // You might want to create a customer_folder_structures table
    }

    // Inner class for folder structure
    public static class CustomerFolderStructure {
        private int customerId;
        private String mainFolderId;
        private String mainFolderName;
        private Map<String, String> subFolders;

        // Getters and setters
        public int getCustomerId() { return customerId; }
        public void setCustomerId(int customerId) { this.customerId = customerId; }

        public String getMainFolderId() { return mainFolderId; }
        public void setMainFolderId(String mainFolderId) { this.mainFolderId = mainFolderId; }

        public String getMainFolderName() { return mainFolderName; }
        public void setMainFolderName(String mainFolderName) { this.mainFolderName = mainFolderName; }

        public Map<String, String> getSubFolders() { return subFolders; }
        public void setSubFolders(Map<String, String> subFolders) { this.subFolders = subFolders; }
    }
}
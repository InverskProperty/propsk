package site.easy.to.build.crm.service.drive;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import site.easy.to.build.crm.entity.Customer;

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

    // Shared Drive ID for CRM property documents
    private static final String SHARED_DRIVE_ID = "0ADaFlidiFrFDUk9PVA";
    private static final String SHARED_DRIVE_DOCUMENTS_FOLDER = "Property-Documents";

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
            return new ArrayList<>(); // Customer folder doesn't exist yet
        }

        return listFoldersInFolder(driveService, customerFolderId);
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
     * Get direct download link for a file
     */
    public String getDirectDownloadUrl(String fileId) {
        return "https://drive.google.com/uc?export=download&id=" + fileId;
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

    private String getFolderNameFromType(String folderType) {
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
}
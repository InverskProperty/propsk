package site.easy.to.build.crm.service.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class GoogleServiceAccountService {

    private static final Logger log = LoggerFactory.getLogger(GoogleServiceAccountService.class);

    @Value("${GOOGLE_SERVICE_ACCOUNT_KEY}")
    private String serviceAccountKey;

    private static final String APPLICATION_NAME = "CRM Property Management";
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    // User to impersonate for creating files (admin account with proper permissions)
    private static final String IMPERSONATE_USER = "sajidkazmi@propsk.com";

    // Cached services to avoid recreating
    private Drive driveService;
    private Sheets sheetsService;
    private Drive impersonatedDriveService;
    private Sheets impersonatedSheetsService;

    /**
     * Test Google service account connectivity
     */
    public Map<String, Object> testConnectivity() {
        Map<String, Object> results = new HashMap<>();

        try {
            log.info("🔍 Testing Google Service Account connectivity...");

            // Test Drive API
            results.put("driveTest", testDriveAccess());

            // Test Sheets API
            results.put("sheetsTest", testSheetsAccess());

            results.put("overallStatus", "SUCCESS");
            results.put("timestamp", LocalDateTime.now().toString());

            log.info("✅ Google Service Account connectivity test completed successfully");

        } catch (Exception e) {
            log.error("❌ Google Service Account connectivity test failed: {}", e.getMessage(), e);
            results.put("overallStatus", "FAILED");
            results.put("error", e.getMessage());
        }

        return results;
    }

    private Map<String, String> testDriveAccess() throws Exception {
        Map<String, String> result = new HashMap<>();
        try {
            Drive drive = getDriveService();

            // Test by listing files (limited to 5)
            FileList fileList = drive.files().list()
                .setPageSize(5)
                .setFields("files(id,name)")
                .execute();

            result.put("status", "SUCCESS");
            result.put("filesCount", String.valueOf(fileList.getFiles().size()));
            result.put("message", "Drive API accessible");

        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
        }
        return result;
    }

    private Map<String, String> testSheetsAccess() throws Exception {
        Map<String, String> result = new HashMap<>();

        // First try the impersonated approach
        try {
            log.info("🔍 Testing Sheets access with domain-wide delegation (impersonation)...");

            String title = "CRM Impersonation Test - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            // Test direct spreadsheet creation using impersonated Sheets service
            Sheets impersonatedSheets = getImpersonatedSheetsService();

            Spreadsheet spreadsheet = new Spreadsheet()
                .setProperties(new SpreadsheetProperties().setTitle(title));

            Spreadsheet createdSheet = impersonatedSheets.spreadsheets().create(spreadsheet).execute();
            String spreadsheetId = createdSheet.getSpreadsheetId();

            result.put("status", "SUCCESS");
            result.put("testSheetId", spreadsheetId);
            result.put("message", "Sheets API accessible via domain-wide delegation");
            result.put("method", "Impersonated Sheets API direct create");
            result.put("impersonatedUser", IMPERSONATE_USER);

            log.info("✅ Impersonated Sheets test successful!");

        } catch (Exception impersonationError) {
            log.warn("⚠️ Impersonated Sheets test failed: {}", impersonationError.getMessage());

            // Fallback to original approach
            try {
                log.info("🔍 Falling back to original Drive API workaround...");

                String title = "CRM Fallback Test - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

                // Step 1: Create spreadsheet using Drive API (which works)
                Drive drive = getDriveService();

                com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
                fileMetadata.setName(title);
                fileMetadata.setMimeType("application/vnd.google-apps.spreadsheet");

                com.google.api.services.drive.model.File file = drive.files().create(fileMetadata).execute();
                String spreadsheetId = file.getId();

                // Step 2: Verify Sheets API access by reading the created sheet
                Sheets sheets = getSheetsService();
                Spreadsheet spreadsheet = sheets.spreadsheets().get(spreadsheetId).execute();

                result.put("status", "FALLBACK_SUCCESS");
                result.put("testSheetId", spreadsheetId);
                result.put("message", "Sheets API accessible via Drive API workaround (impersonation failed)");
                result.put("method", "Drive API create + Sheets API verify");
                result.put("impersonationError", impersonationError.getMessage());

            } catch (Exception fallbackError) {
                result.put("status", "FAILED");
                result.put("error", fallbackError.getMessage());
                result.put("impersonationError", impersonationError.getMessage());
            }
        }

        return result;
    }


    /**
     * Get or create Drive service
     */
    public Drive getDriveService() throws IOException, GeneralSecurityException {
        if (driveService == null) {
            GoogleCredential credential = GoogleCredential
                .fromStream(new ByteArrayInputStream(serviceAccountKey.getBytes()))
                .createScoped(Collections.singleton(DriveScopes.DRIVE));

            driveService = new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                credential)
                .setApplicationName(APPLICATION_NAME)
                .build();

            log.debug("✅ Drive service initialized");
        }
        return driveService;
    }

    /**
     * Get or create Sheets service
     */
    public Sheets getSheetsService() throws IOException, GeneralSecurityException {
        if (sheetsService == null) {
            GoogleCredential credential = GoogleCredential
                .fromStream(new ByteArrayInputStream(serviceAccountKey.getBytes()))
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));

            sheetsService = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                credential)
                .setApplicationName(APPLICATION_NAME)
                .build();

            log.debug("✅ Sheets service initialized");
        }
        return sheetsService;
    }

    /**
     * Get or create impersonated Drive service
     * Uses domain-wide delegation to impersonate admin user
     */
    public Drive getImpersonatedDriveService() throws IOException, GeneralSecurityException {
        if (impersonatedDriveService == null) {
            GoogleCredential credential = GoogleCredential
                .fromStream(new ByteArrayInputStream(serviceAccountKey.getBytes()))
                .createScoped(Collections.singleton(DriveScopes.DRIVE))
                .createDelegated(IMPERSONATE_USER);

            impersonatedDriveService = new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                credential)
                .setApplicationName(APPLICATION_NAME)
                .build();

            log.debug("✅ Impersonated Drive service initialized for user: {}", IMPERSONATE_USER);
        }
        return impersonatedDriveService;
    }

    /**
     * Get or create impersonated Sheets service
     * Uses domain-wide delegation to impersonate admin user
     */
    public Sheets getImpersonatedSheetsService() throws IOException, GeneralSecurityException {
        if (impersonatedSheetsService == null) {
            GoogleCredential credential = GoogleCredential
                .fromStream(new ByteArrayInputStream(serviceAccountKey.getBytes()))
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS))
                .createDelegated(IMPERSONATE_USER);

            impersonatedSheetsService = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                credential)
                .setApplicationName(APPLICATION_NAME)
                .build();

            log.debug("✅ Impersonated Sheets service initialized for user: {}", IMPERSONATE_USER);
        }
        return impersonatedSheetsService;
    }


    /**
     * Create a folder in Google Drive using impersonated user
     */
    public String createFolder(String folderName, String parentFolderId) throws Exception {
        log.info("📁 Creating folder '{}' using impersonated user: {}", folderName, IMPERSONATE_USER);

        try {
            Drive drive = getImpersonatedDriveService();

            File folder = new File();
            folder.setName(folderName);
            folder.setMimeType("application/vnd.google-apps.folder");

            if (parentFolderId != null) {
                folder.setParents(Collections.singletonList(parentFolderId));
                log.debug("📁 Setting parent folder ID: {}", parentFolderId);
            }

            File createdFolder = drive.files().create(folder).execute();
            log.info("✅ Successfully created folder '{}' with ID: {}", folderName, createdFolder.getId());

            return createdFolder.getId();

        } catch (Exception e) {
            log.error("❌ Failed to create folder '{}' using impersonation: {}", folderName, e.getMessage());
            throw new Exception("Failed to create impersonated folder: " + e.getMessage(), e);
        }
    }

    /**
     * Create a Google Sheets spreadsheet using impersonated user
     */
    public String createSpreadsheet(String title, List<List<Object>> data) throws Exception {
        log.info("📊 Creating spreadsheet '{}' using impersonated user: {}", title, IMPERSONATE_USER);

        try {
            // Use impersonated Sheets service
            Sheets sheets = getImpersonatedSheetsService();

            // Create spreadsheet
            Spreadsheet spreadsheet = new Spreadsheet()
                .setProperties(new SpreadsheetProperties().setTitle(title));

            Spreadsheet createdSheet = sheets.spreadsheets().create(spreadsheet).execute();
            String spreadsheetId = createdSheet.getSpreadsheetId();

            // Add data if provided
            if (data != null && !data.isEmpty()) {
                ValueRange valueRange = new ValueRange()
                    .setValues(data);

                sheets.spreadsheets().values()
                    .update(spreadsheetId, "A1", valueRange)
                    .setValueInputOption("RAW")
                    .execute();

                log.info("📊 Added {} rows of data to spreadsheet", data.size());
            }

            log.info("✅ Successfully created spreadsheet '{}' with ID: {}", title, spreadsheetId);
            return spreadsheetId;

        } catch (Exception e) {
            log.error("❌ Failed to create spreadsheet '{}' using impersonation: {}", title, e.getMessage());
            throw new Exception("Failed to create impersonated spreadsheet: " + e.getMessage(), e);
        }
    }

    /**
     * Get or create CRM root folder using impersonated user
     */
    public String getOrCreateCrmRootFolder() throws Exception {
        log.info("📁 Getting or creating CRM root folder using impersonated user: {}", IMPERSONATE_USER);

        try {
            Drive drive = getImpersonatedDriveService();

            // Search for existing CRM folder
            FileList result = drive.files().list()
                .setQ("name='CRM Property Management' and mimeType='application/vnd.google-apps.folder'")
                .setFields("files(id,name)")
                .execute();

            if (!result.getFiles().isEmpty()) {
                String folderId = result.getFiles().get(0).getId();
                log.info("✅ Found existing CRM root folder: {}", folderId);
                return folderId;
            }

            // Create new CRM folder
            log.info("📁 CRM root folder not found, creating new one...");
            return createFolder("CRM Property Management", null);

        } catch (Exception e) {
            log.error("❌ Failed to get or create CRM root folder using impersonation: {}", e.getMessage());
            throw new Exception("Failed to access CRM root folder with impersonation: " + e.getMessage(), e);
        }
    }

    /**
     * Get or create customer folder using impersonated user
     */
    public String getOrCreateCustomerFolder(String customerEmail, String customerName) throws Exception {
        log.info("📁 Getting or creating customer folder for {} using impersonated user: {}", customerEmail, IMPERSONATE_USER);

        try {
            String rootFolderId = getOrCreateCrmRootFolder();

            Drive drive = getImpersonatedDriveService();
            String folderName = "Customer-" + customerEmail.replaceAll("[^a-zA-Z0-9.-]", "_");

            // Search for existing customer folder
            FileList result = drive.files().list()
                .setQ("name='" + folderName + "' and mimeType='application/vnd.google-apps.folder' and '" + rootFolderId + "' in parents")
                .setFields("files(id,name)")
                .execute();

            if (!result.getFiles().isEmpty()) {
                String folderId = result.getFiles().get(0).getId();
                log.info("✅ Found existing customer folder for {}: {}", customerEmail, folderId);
                return folderId;
            }

            // Create new customer folder
            log.info("📁 Customer folder not found, creating new one...");
            return createFolder(folderName, rootFolderId);

        } catch (Exception e) {
            log.error("❌ Failed to get or create customer folder for {} using impersonation: {}", customerEmail, e.getMessage());
            throw new Exception("Failed to access customer folder with impersonation: " + e.getMessage(), e);
        }
    }
}
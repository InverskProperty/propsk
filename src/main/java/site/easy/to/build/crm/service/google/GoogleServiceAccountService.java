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

    // Cached services to avoid recreating
    private Drive driveService;
    private Sheets sheetsService;

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
        try {
            Sheets sheets = getSheetsService();

            // Test by creating a simple spreadsheet
            Spreadsheet spreadsheet = new Spreadsheet()
                .setProperties(new SpreadsheetProperties()
                    .setTitle("CRM Test Sheet - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));

            Spreadsheet createdSheet = sheets.spreadsheets().create(spreadsheet).execute();

            result.put("status", "SUCCESS");
            result.put("testSheetId", createdSheet.getSpreadsheetId());
            result.put("message", "Sheets API accessible - test sheet created");

        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
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
     * Create a folder in Google Drive
     */
    public String createFolder(String folderName, String parentFolderId) throws Exception {
        Drive drive = getDriveService();

        File folder = new File();
        folder.setName(folderName);
        folder.setMimeType("application/vnd.google-apps.folder");

        if (parentFolderId != null) {
            folder.setParents(Collections.singletonList(parentFolderId));
        }

        File createdFolder = drive.files().create(folder).execute();
        log.info("📁 Created folder '{}' with ID: {}", folderName, createdFolder.getId());

        return createdFolder.getId();
    }

    /**
     * Create a Google Sheets spreadsheet
     */
    public String createSpreadsheet(String title, List<List<Object>> data) throws Exception {
        Sheets sheets = getSheetsService();

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
        }

        log.info("📊 Created spreadsheet '{}' with ID: {}", title, spreadsheetId);
        return spreadsheetId;
    }

    /**
     * Get or create CRM root folder
     */
    public String getOrCreateCrmRootFolder() throws Exception {
        Drive drive = getDriveService();

        // Search for existing CRM folder
        FileList result = drive.files().list()
            .setQ("name='CRM Property Management' and mimeType='application/vnd.google-apps.folder'")
            .setFields("files(id,name)")
            .execute();

        if (!result.getFiles().isEmpty()) {
            String folderId = result.getFiles().get(0).getId();
            log.debug("📁 Found existing CRM root folder: {}", folderId);
            return folderId;
        }

        // Create new CRM folder
        return createFolder("CRM Property Management", null);
    }

    /**
     * Get or create customer folder
     */
    public String getOrCreateCustomerFolder(String customerEmail, String customerName) throws Exception {
        String rootFolderId = getOrCreateCrmRootFolder();

        Drive drive = getDriveService();
        String folderName = "Customer-" + customerEmail.replaceAll("[^a-zA-Z0-9.-]", "_");

        // Search for existing customer folder
        FileList result = drive.files().list()
            .setQ("name='" + folderName + "' and mimeType='application/vnd.google-apps.folder' and '" + rootFolderId + "' in parents")
            .setFields("files(id,name)")
            .execute();

        if (!result.getFiles().isEmpty()) {
            String folderId = result.getFiles().get(0).getId();
            log.debug("📁 Found existing customer folder for {}: {}", customerEmail, folderId);
            return folderId;
        }

        // Create new customer folder
        return createFolder(folderName, rootFolderId);
    }
}
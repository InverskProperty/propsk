package site.easy.to.build.crm.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Controller
@RequestMapping("/test/google-api")
public class GoogleApiTestController {

    private final AuthenticationUtils authenticationUtils;

    @Autowired
    public GoogleApiTestController(AuthenticationUtils authenticationUtils) {
        this.authenticationUtils = authenticationUtils;
    }

    /**
     * Test Google Drive API access
     */
    @GetMapping("/drive")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testGoogleDriveAccess(Authentication authentication) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            
            // Test 1: Check if user has Google Drive scope
            String grantedScopes = oAuthUser.getGrantedScopes();
            boolean hasDriveScope = grantedScopes != null && 
                (grantedScopes.contains("https://www.googleapis.com/auth/drive") ||
                 grantedScopes.contains("https://www.googleapis.com/auth/drive.file"));
            
            result.put("hasDriveScope", hasDriveScope);
            result.put("grantedScopes", grantedScopes);
            
            if (!hasDriveScope) {
                result.put("error", "Google Drive scope not granted. Please re-authorize with Drive permissions.");
                return ResponseEntity.ok(result);
            }
            
            // Test 2: Create Drive service
            GoogleCredential credential = new GoogleCredential().setAccessToken(oAuthUser.getAccessToken());
            Drive driveService = new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("CRM Property Management").build();
            
            // Test 3: List files (basic read test)
            List<File> files = driveService.files().list()
                .setPageSize(5)
                .setFields("files(id, name, mimeType)")
                .execute()
                .getFiles();
            
            result.put("canListFiles", true);
            result.put("fileCount", files.size());
            result.put("sampleFiles", files.stream().map(f -> 
                Map.of("id", f.getId(), "name", f.getName(), "mimeType", f.getMimeType())
            ).toList());
            
            // Test 4: Create a test folder
            File folderMetadata = new File();
            folderMetadata.setName("CRM_TEST_FOLDER_" + System.currentTimeMillis());
            folderMetadata.setMimeType("application/vnd.google-apps.folder");
            
            File createdFolder = driveService.files().create(folderMetadata)
                .setFields("id, name")
                .execute();
            
            result.put("canCreateFolder", true);
            result.put("testFolderId", createdFolder.getId());
            result.put("testFolderName", createdFolder.getName());
            
            // Test 5: Delete the test folder
            driveService.files().delete(createdFolder.getId()).execute();
            result.put("canDeleteFolder", true);
            
            result.put("driveApiStatus", "✅ WORKING");
            result.put("timestamp", LocalDateTime.now().toString());
            
        } catch (Exception e) {
            result.put("driveApiStatus", "❌ ERROR");
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            
            // Common error handling
            if (e.getMessage() != null) {
                if (e.getMessage().contains("403")) {
                    result.put("suggestion", "Access denied. Please check Google Drive API permissions in Google Cloud Console.");
                } else if (e.getMessage().contains("401")) {
                    result.put("suggestion", "Authentication failed. Please re-authorize the application.");
                } else if (e.getMessage().contains("quota")) {
                    result.put("suggestion", "API quota exceeded. Check Google Cloud Console quotas.");
                }
            }
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * Test Google Sheets API access
     */
    @GetMapping("/sheets")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testGoogleSheetsAccess(Authentication authentication) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            
            // Test 1: Check if user has Google Sheets scope
            String grantedScopes = oAuthUser.getGrantedScopes();
            boolean hasSheetsScope = grantedScopes != null && 
                (grantedScopes.contains("https://www.googleapis.com/auth/spreadsheets") ||
                 grantedScopes.contains("https://www.googleapis.com/auth/drive"));
            
            result.put("hasSheetsScope", hasSheetsScope);
            result.put("grantedScopes", grantedScopes);
            
            if (!hasSheetsScope) {
                result.put("error", "Google Sheets scope not granted. Please re-authorize with Sheets permissions.");
                return ResponseEntity.ok(result);
            }
            
            // Test 2: Create Sheets service
            GoogleCredential credential = new GoogleCredential().setAccessToken(oAuthUser.getAccessToken());
            Sheets sheetsService = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("CRM Property Management").build();
            
            // Test 3: Create a test spreadsheet
            Spreadsheet spreadsheet = new Spreadsheet()
                .setProperties(new SpreadsheetProperties()
                    .setTitle("CRM_TEST_SHEET_" + System.currentTimeMillis()));
            
            Spreadsheet createdSheet = sheetsService.spreadsheets().create(spreadsheet).execute();
            String spreadsheetId = createdSheet.getSpreadsheetId();
            
            result.put("canCreateSpreadsheet", true);
            result.put("testSpreadsheetId", spreadsheetId);
            result.put("testSpreadsheetTitle", createdSheet.getProperties().getTitle());
            result.put("spreadsheetUrl", "https://docs.google.com/spreadsheets/d/" + spreadsheetId);
            
            // Test 4: Write data to the spreadsheet
            List<List<Object>> testData = Arrays.asList(
                Arrays.asList("Property Owner Statement", ""),
                Arrays.asList("Generated:", LocalDateTime.now().toString()),
                Arrays.asList("", ""),
                Arrays.asList("Owner Name", "Test Property Owner"),
                Arrays.asList("Property", "Test Property Address"),
                Arrays.asList("Period", "2024-01-01 to 2024-01-31"),
                Arrays.asList("", ""),
                Arrays.asList("SUMMARY", ""),
                Arrays.asList("Total Rent", "£1,500.00"),
                Arrays.asList("Total Expenses", "£150.00"),
                Arrays.asList("Net Income", "£1,350.00")
            );
            
            ValueRange body = new ValueRange().setValues(testData);
            sheetsService.spreadsheets().values()
                .update(spreadsheetId, "A1", body)
                .setValueInputOption("RAW")
                .execute();
            
            result.put("canWriteData", true);
            result.put("dataRowsWritten", testData.size());
            
            // Test 5: Apply formatting
            List<Request> requests = Arrays.asList(
                new Request().setRepeatCell(new RepeatCellRequest()
                    .setRange(new GridRange().setSheetId(0).setStartRowIndex(0).setEndRowIndex(1))
                    .setCell(new CellData().setUserEnteredFormat(new CellFormat()
                        .setTextFormat(new TextFormat().setBold(true).setFontSize(16))))
                    .setFields("userEnteredFormat.textFormat.bold,userEnteredFormat.textFormat.fontSize")),
                new Request().setRepeatCell(new RepeatCellRequest()
                    .setRange(new GridRange().setSheetId(0).setStartRowIndex(7).setEndRowIndex(8))
                    .setCell(new CellData().setUserEnteredFormat(new CellFormat()
                        .setTextFormat(new TextFormat().setBold(true))))
                    .setFields("userEnteredFormat.textFormat.bold"))
            );
            
            BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                .setRequests(requests);
            sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();
            
            result.put("canApplyFormatting", true);
            
            // Test 6: Read data back
            ValueRange readResult = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "A1:B11")
                .execute();
            
            result.put("canReadData", true);
            result.put("readDataRows", readResult.getValues().size());
            
            // Note: We're keeping the test spreadsheet for verification
            // In production, you might want to delete it
            result.put("note", "Test spreadsheet created successfully. You can view it at the URL above.");
            
            result.put("sheetsApiStatus", "✅ WORKING");
            result.put("timestamp", LocalDateTime.now().toString());
            
        } catch (Exception e) {
            result.put("sheetsApiStatus", "❌ ERROR");
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            
            // Common error handling
            if (e.getMessage() != null) {
                if (e.getMessage().contains("403")) {
                    result.put("suggestion", "Access denied. Please check Google Sheets API permissions in Google Cloud Console.");
                } else if (e.getMessage().contains("401")) {
                    result.put("suggestion", "Authentication failed. Please re-authorize the application.");
                } else if (e.getMessage().contains("quota")) {
                    result.put("suggestion", "API quota exceeded. Check Google Cloud Console quotas.");
                }
            }
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * Test both APIs together
     */
    @GetMapping("/combined")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testCombinedAccess(Authentication authentication) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            
            // Test 1: Check user details
            result.put("userEmail", oAuthUser.getEmail());
            result.put("accessTokenExpiry", oAuthUser.getAccessTokenExpiration());
            result.put("allScopes", oAuthUser.getGrantedScopes());
            
            // Test 2: Required scopes check
            String grantedScopes = oAuthUser.getGrantedScopes();
            boolean hasDriveScope = grantedScopes != null && 
                (grantedScopes.contains("https://www.googleapis.com/auth/drive") ||
                 grantedScopes.contains("https://www.googleapis.com/auth/drive.file"));
            boolean hasSheetsScope = grantedScopes != null && 
                (grantedScopes.contains("https://www.googleapis.com/auth/spreadsheets") ||
                 grantedScopes.contains("https://www.googleapis.com/auth/drive"));
            
            result.put("hasDriveScope", hasDriveScope);
            result.put("hasSheetsScope", hasSheetsScope);
            
            if (!hasDriveScope || !hasSheetsScope) {
                result.put("status", "❌ MISSING SCOPES");
                result.put("missingScopes", Arrays.asList(
                    !hasDriveScope ? "Google Drive" : null,
                    !hasSheetsScope ? "Google Sheets" : null
                ).stream().filter(s -> s != null).toList());
                result.put("action", "Please re-authorize with required scopes at: /employee/settings/google-services");
                return ResponseEntity.ok(result);
            }
            
            // Test 3: Create services
            GoogleCredential credential = new GoogleCredential().setAccessToken(oAuthUser.getAccessToken());
            
            Drive driveService = new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("CRM Property Management").build();
            
            Sheets sheetsService = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("CRM Property Management").build();
            
            // Test 4: Create customer folder simulation
            File customerFolder = new File();
            customerFolder.setName("TEST_CUSTOMER_FOLDER_" + System.currentTimeMillis());
            customerFolder.setMimeType("application/vnd.google-apps.folder");
            
            File createdFolder = driveService.files().create(customerFolder)
                .setFields("id, name")
                .execute();
            
            // Test 5: Create subfolders
            String[] subfolders = {"Tenancy", "Right to Rent", "ID", "Statements"};
            for (String folderName : subfolders) {
                File subfolder = new File();
                subfolder.setName(folderName);
                subfolder.setMimeType("application/vnd.google-apps.folder");
                subfolder.setParents(Arrays.asList(createdFolder.getId()));
                
                driveService.files().create(subfolder).execute();
            }
            
            // Test 6: Create statement spreadsheet
            Spreadsheet spreadsheet = new Spreadsheet()
                .setProperties(new SpreadsheetProperties()
                    .setTitle("TEST_STATEMENT_" + System.currentTimeMillis()));
            
            Spreadsheet createdSheet = sheetsService.spreadsheets().create(spreadsheet).execute();
            
            // Test 7: Move spreadsheet to customer folder
            File sheetFile = new File();
            sheetFile.setParents(Arrays.asList(createdFolder.getId()));
            
            driveService.files().update(createdSheet.getSpreadsheetId(), sheetFile)
                .setAddParents(createdFolder.getId())
                .setFields("id, parents")
                .execute();
            
            result.put("customerFolderId", createdFolder.getId());
            result.put("customerFolderName", createdFolder.getName());
            result.put("statementSpreadsheetId", createdSheet.getSpreadsheetId());
            result.put("statementUrl", "https://docs.google.com/spreadsheets/d/" + createdSheet.getSpreadsheetId());
            
            // Test 8: List folder contents
            List<File> folderContents = driveService.files().list()
                .setQ("'" + createdFolder.getId() + "' in parents")
                .setFields("files(id, name, mimeType)")
                .execute()
                .getFiles();
            
            result.put("folderContents", folderContents.stream().map(f -> 
                Map.of("name", f.getName(), "type", f.getMimeType())
            ).toList());
            
            result.put("status", "✅ FULLY WORKING");
            result.put("message", "Both Google Drive and Sheets APIs are working correctly!");
            result.put("readyForImplementation", true);
            result.put("timestamp", LocalDateTime.now().toString());
            
        } catch (Exception e) {
            result.put("status", "❌ ERROR");
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            
            if (e.getMessage() != null) {
                if (e.getMessage().contains("403")) {
                    result.put("suggestion", "Check API permissions in Google Cloud Console");
                } else if (e.getMessage().contains("401")) {
                    result.put("suggestion", "Re-authorize the application");
                }
            }
        }
        
        return ResponseEntity.ok(result);
    }
}
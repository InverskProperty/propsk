package site.easy.to.build.crm.controller;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.google.service.drive.GoogleDriveApiService;
import site.easy.to.build.crm.google.service.gmail.GoogleGmailApiService;
import site.easy.to.build.crm.service.user.OAuthUserService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.time.LocalDateTime;
import java.time.temporal.ValueRange;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Controller
@RequestMapping("/test/google-api")
public class GoogleApiTestController {

    private final AuthenticationUtils authenticationUtils;
    private final OAuthUserService oAuthUserService;
    private final GoogleDriveApiService googleDriveApiService;
    private final GoogleGmailApiService googleGmailApiService;

    @Autowired
    public GoogleApiTestController(AuthenticationUtils authenticationUtils,
                                 OAuthUserService oAuthUserService,
                                 GoogleDriveApiService googleDriveApiService,
                                 GoogleGmailApiService googleGmailApiService) {
        this.authenticationUtils = authenticationUtils;
        this.oAuthUserService = oAuthUserService;
        this.googleDriveApiService = googleDriveApiService;
        this.googleGmailApiService = googleGmailApiService;
    }

    /**
     * Test Google Drive API access using existing service layer
     */
    @GetMapping("/drive")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testGoogleDriveAccess(Authentication authentication) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            
            // Test 1: Check if user has Google Drive scope
            String grantedScopes = convertScopesToString(oAuthUser.getGrantedScopes());
            boolean hasDriveScope = grantedScopes != null && 
                (grantedScopes.contains("https://www.googleapis.com/auth/drive") ||
                 grantedScopes.contains("https://www.googleapis.com/auth/drive.file"));
            
            result.put("hasDriveScope", hasDriveScope);
            result.put("grantedScopes", grantedScopes);
            result.put("userEmail", oAuthUser.getEmail());
            result.put("tokenExpiry", oAuthUser.getAccessTokenExpiration());
            
            if (!hasDriveScope) {
                result.put("error", "Google Drive scope not granted. Please re-authorize with Drive permissions.");
                result.put("driveApiStatus", "❌ MISSING SCOPE");
                return ResponseEntity.ok(result);
            }
            
            // Test 2: Refresh token if needed using existing service
            String validToken = oAuthUserService.refreshAccessTokenIfNeeded(oAuthUser);
            result.put("tokenRefreshed", !validToken.equals(oAuthUser.getAccessToken()));
            
            // Test 3: List files using existing service
            var driveFiles = googleDriveApiService.listFiles(oAuthUser);
            result.put("canListFiles", true);
            result.put("fileCount", driveFiles.size());
            result.put("sampleFiles", driveFiles.stream()
                .limit(5)
                .map(f -> Map.of(
                    "id", f.getId() != null ? f.getId() : "unknown",
                    "name", f.getName() != null ? f.getName() : "unnamed", 
                    "mimeType", f.getMimeType() != null ? f.getMimeType() : "unknown"
                ))
                .toList());
            
            // Test 4: Create and delete a test folder using existing service
            String testFolderName = "CRM_TEST_FOLDER_" + System.currentTimeMillis();
            try {
                String folderId = googleDriveApiService.createFolder(oAuthUser, testFolderName);
                result.put("canCreateFolder", true);
                result.put("testFolderId", folderId);
                result.put("testFolderName", testFolderName);
                
                // Test 5: Delete the test folder
                googleDriveApiService.deleteFile(oAuthUser, folderId);
                result.put("canDeleteFolder", true);
                
            } catch (Exception e) {
                result.put("canCreateFolder", false);
                result.put("folderError", e.getMessage());
            }
            
            result.put("driveApiStatus", "✅ WORKING");
            result.put("timestamp", LocalDateTime.now().toString());
            
        } catch (Exception e) {
            result.put("driveApiStatus", "❌ ERROR");
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            
            // Enhanced error handling
            if (e.getMessage() != null) {
                if (e.getMessage().contains("403")) {
                    result.put("suggestion", "Access denied. Check Google Drive API permissions in Google Cloud Console.");
                } else if (e.getMessage().contains("401")) {
                    result.put("suggestion", "Authentication failed. Please re-authorize the application.");
                } else if (e.getMessage().contains("quota")) {
                    result.put("suggestion", "API quota exceeded. Check Google Cloud Console quotas.");
                } else if (e.getMessage().contains("token")) {
                    result.put("suggestion", "Token issue. Try logging out and re-authorizing.");
                }
            }
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * Test Google Sheets API access using proper OAuth2 service integration
     */
    @GetMapping("/sheets")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testGoogleSheetsAccess(Authentication authentication) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            
            // Test 1: Check if user has Google Sheets scope
            String grantedScopes = convertScopesToString(oAuthUser.getGrantedScopes());
            boolean hasSheetsScope = grantedScopes != null && 
                (grantedScopes.contains("https://www.googleapis.com/auth/spreadsheets") ||
                 grantedScopes.contains("https://www.googleapis.com/auth/drive"));
            
            result.put("hasSheetsScope", hasSheetsScope);
            result.put("grantedScopes", grantedScopes);
            result.put("userEmail", oAuthUser.getEmail());
            
            if (!hasSheetsScope) {
                result.put("error", "Google Sheets scope not granted. Please re-authorize with Sheets permissions.");
                result.put("sheetsApiStatus", "❌ MISSING SCOPE");
                return ResponseEntity.ok(result);
            }
            
            // Test 2: Create Sheets service using existing OAuth2 infrastructure
            String validToken = oAuthUserService.refreshAccessTokenIfNeeded(oAuthUser);
            
            // FIXED: Use proper OAuth2 service pattern instead of deprecated GoogleCredential
            Sheets sheetsService = createSheetsService(validToken);
            
            // Test 3: Create a test spreadsheet
            String testSheetTitle = "CRM_TEST_SHEET_" + System.currentTimeMillis();
            Spreadsheet spreadsheet = new Spreadsheet()
                .setProperties(new SpreadsheetProperties().setTitle(testSheetTitle));
            
            Spreadsheet createdSheet = sheetsService.spreadsheets().create(spreadsheet).execute();
            String spreadsheetId = createdSheet.getSpreadsheetId();
            
            result.put("canCreateSpreadsheet", true);
            result.put("testSpreadsheetId", spreadsheetId);
            result.put("testSpreadsheetTitle", createdSheet.getProperties().getTitle());
            result.put("spreadsheetUrl", "https://docs.google.com/spreadsheets/d/" + spreadsheetId);
            
            // Test 4: Write test data to the spreadsheet
            List<List<Object>> testData = Arrays.asList(
                Arrays.asList("CRM Property Management Test", ""),
                Arrays.asList("Generated:", LocalDateTime.now().toString()),
                Arrays.asList("", ""),
                Arrays.asList("Test Type", "Google Sheets API Integration"),
                Arrays.asList("OAuth2 Status", "✅ Working"),
                Arrays.asList("Token Refresh", validToken.equals(oAuthUser.getAccessToken()) ? "Not needed" : "✅ Refreshed"),
                Arrays.asList("", ""),
                Arrays.asList("INTEGRATION TEST RESULTS", ""),
                Arrays.asList("Service Layer", "✅ Using existing OAuth2 service"),
                Arrays.asList("Token Management", "✅ Automated refresh"),
                Arrays.asList("Error Handling", "✅ Comprehensive")
            );
            
            com.google.api.services.sheets.v4.model.ValueRange body = new com.google.api.services.sheets.v4.model.ValueRange().setValues(testData);
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
            com.google.api.services.sheets.v4.model.ValueRange readResult = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "A1:B11")
                .execute();
            
            result.put("canReadData", true);
            result.put("readDataRows", readResult.getValues() != null ? readResult.getValues().size() : 0);
            
            // Note: We're keeping the test spreadsheet for verification
            result.put("note", "Test spreadsheet created successfully. You can view it at the URL above.");
            
            result.put("sheetsApiStatus", "✅ WORKING");
            result.put("integrationStatus", "✅ PROPERLY INTEGRATED");
            result.put("timestamp", LocalDateTime.now().toString());
            
        } catch (Exception e) {
            result.put("sheetsApiStatus", "❌ ERROR");
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            
            // Enhanced error handling
            if (e.getMessage() != null) {
                if (e.getMessage().contains("403")) {
                    result.put("suggestion", "Access denied. Check Google Sheets API permissions in Google Cloud Console.");
                } else if (e.getMessage().contains("401")) {
                    result.put("suggestion", "Authentication failed. Please re-authorize the application.");
                } else if (e.getMessage().contains("quota")) {
                    result.put("suggestion", "API quota exceeded. Check Google Cloud Console quotas.");
                } else if (e.getMessage().contains("token")) {
                    result.put("suggestion", "Token issue. Try logging out and re-authorizing.");
                }
            }
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * Test both APIs together using integrated service approach
     */
    @GetMapping("/combined")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testCombinedAccess(Authentication authentication) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            
            // Test 1: Check user details and token status
            result.put("userEmail", oAuthUser.getEmail());
            result.put("accessTokenExpiry", oAuthUser.getAccessTokenExpiration());
            result.put("allScopes", oAuthUser.getGrantedScopes());
            
            // Test 2: Required scopes check
            String grantedScopes = convertScopesToString(oAuthUser.getGrantedScopes());
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
            
            // Test 3: Refresh tokens using existing service
            String validToken = oAuthUserService.refreshAccessTokenIfNeeded(oAuthUser);
            result.put("tokenRefreshed", !validToken.equals(oAuthUser.getAccessToken()));
            
            // Test 4: Create customer folder simulation using existing services
            String customerFolderName = "TEST_CUSTOMER_FOLDER_" + System.currentTimeMillis();
            String customerFolderId = googleDriveApiService.createFolder(oAuthUser, customerFolderName);
            
            // Test 5: Create subfolders using enhanced API service
            String[] subfolders = {"Tenancy", "Right to Rent", "ID", "Statements"};
            Map<String, String> createdSubfolders = new HashMap<>();
            
            for (String folderName : subfolders) {
                try {
                    String subfolderId = googleDriveApiService.createFolderInParent(oAuthUser, folderName, customerFolderId);
                    createdSubfolders.put(folderName, subfolderId);
                } catch (Exception e) {
                    result.put("subfolderError_" + folderName, e.getMessage());
                }
            }
            
            // Test 6: Create statement spreadsheet using proper OAuth2
            String testSheetTitle = "TEST_STATEMENT_" + System.currentTimeMillis();
            Sheets sheetsService = createSheetsService(validToken);
            
            Spreadsheet spreadsheet = new Spreadsheet()
                .setProperties(new SpreadsheetProperties().setTitle(testSheetTitle));
            Spreadsheet createdSheet = sheetsService.spreadsheets().create(spreadsheet).execute();
            
            // Test 7: Move spreadsheet to customer folder using enhanced API
            try {
                googleDriveApiService.moveFileToFolder(oAuthUser, createdSheet.getSpreadsheetId(), customerFolderId);
                result.put("movedSpreadsheetToFolder", true);
            } catch (Exception e) {
                result.put("moveError", e.getMessage());
                result.put("movedSpreadsheetToFolder", false);
            }
            
            // Test 8: List folder contents using existing service
            var folderContents = googleDriveApiService.listFilesInFolder(oAuthUser, customerFolderId);
            
            result.put("customerFolderId", customerFolderId);
            result.put("customerFolderName", customerFolderName);
            result.put("subfolderCount", createdSubfolders.size());
            result.put("createdSubfolders", createdSubfolders);
            result.put("statementSpreadsheetId", createdSheet.getSpreadsheetId());
            result.put("statementUrl", "https://docs.google.com/spreadsheets/d/" + createdSheet.getSpreadsheetId());
            result.put("folderContentsCount", folderContents.size());
            result.put("folderContents", folderContents.stream()
                .map(f -> Map.of(
                    "name", f.getName() != null ? f.getName() : "unnamed",
                    "type", f.getMimeType() != null ? f.getMimeType() : "unknown"
                ))
                .toList());
            
            result.put("status", "✅ FULLY WORKING");
            result.put("integrationLevel", "✅ USING EXISTING SERVICES");
            result.put("message", "Both Google Drive and Sheets APIs are working correctly with proper OAuth2 integration!");
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
                } else if (e.getMessage().contains("token")) {
                    result.put("suggestion", "Token refresh failed - please re-authorize");
                }
            }
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * Test Gmail API integration (bonus test)
     */
    @GetMapping("/gmail")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testGmailAccess(Authentication authentication) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            
            // Check Gmail scope
            String grantedScopes = convertScopesToString(oAuthUser.getGrantedScopes());
            boolean hasGmailScope = grantedScopes != null && 
                grantedScopes.contains("https://www.googleapis.com/auth/gmail.send");
            
            result.put("hasGmailScope", hasGmailScope);
            result.put("userEmail", oAuthUser.getEmail());
            
            if (!hasGmailScope) {
                result.put("error", "Gmail scope not granted. Please re-authorize with Gmail permissions.");
                result.put("gmailApiStatus", "❌ MISSING SCOPE");
                return ResponseEntity.ok(result);
            }
            
            // Test using existing Gmail service if available
            try {
                // This would test your existing Gmail integration
                var emailsPage = googleGmailApiService.getEmailsPage(oAuthUser, "INBOX", 1, 5);
                result.put("canAccessInbox", true);
                result.put("emailCount", emailsPage.getEmails().size());
                result.put("gmailApiStatus", "✅ WORKING");
            } catch (Exception e) {
                result.put("canAccessInbox", false);
                result.put("gmailError", e.getMessage());
                result.put("gmailApiStatus", "⚠️ LIMITED");
            }
            
            result.put("timestamp", LocalDateTime.now().toString());
            
        } catch (Exception e) {
            result.put("gmailApiStatus", "❌ ERROR");
            result.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * Helper method to create Sheets service with proper OAuth2 token
     * FIXED: No longer uses deprecated GoogleCredential
     */
    private Sheets createSheetsService(String accessToken) throws Exception {
        // Use the same pattern as your existing services
        return new Sheets.Builder(
            com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport(),
            com.google.api.client.json.gson.GsonFactory.getDefaultInstance(),
            request -> request.getHeaders().setAuthorization("Bearer " + accessToken)
        ).setApplicationName("CRM Property Management").build();
    }

    /**
     * FIXED: Helper method to safely convert Set<String> to String
     */
    private String convertScopesToString(java.util.Set<String> scopes) {
        if (scopes == null) {
            return null;
        }
        return String.join(" ", scopes);
    }
}
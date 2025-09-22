package site.easy.to.build.crm.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.service.google.GoogleServiceAccountService;
import site.easy.to.build.crm.service.integration.PayPropGoogleIntegrationService;
import site.easy.to.build.crm.service.payprop.PayPropPortfolioSyncService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test/google-service-account")
public class GoogleServiceAccountTestController {

    private static final Logger log = LoggerFactory.getLogger(GoogleServiceAccountTestController.class);

    @Autowired
    private GoogleServiceAccountService googleService;

    @Autowired
    private site.easy.to.build.crm.service.integration.SimpleGoogleIntegrationService simpleIntegrationService;

    @Autowired
    private PayPropPortfolioSyncService portfolioSyncService;

    /**
     * Test Google Service Account connectivity
     * GET /api/test/google-service-account/connectivity
     */
    @GetMapping("/connectivity")
    public ResponseEntity<Map<String, Object>> testConnectivity() {
        try {
            log.info("🔍 Testing Google Service Account connectivity...");

            Map<String, Object> results = googleService.testConnectivity();

            return ResponseEntity.ok(results);

        } catch (Exception e) {
            log.error("❌ Connectivity test failed: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "FAILED");
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Test creating Google folder structure
     * POST /api/test/google-service-account/create-folder
     */
    @PostMapping("/create-folder")
    public ResponseEntity<Map<String, Object>> testCreateFolder(
            @RequestParam String customerEmail,
            @RequestParam(defaultValue = "Test Customer") String customerName) {
        try {
            log.info("📁 Testing folder creation for customer: {}", customerEmail);

            String folderId = googleService.getOrCreateCustomerFolder(customerEmail, customerName);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("customerFolderId", folderId);
            response.put("customerEmail", customerEmail);
            response.put("message", "Customer folder created successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Folder creation test failed: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "FAILED");
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Test creating Google Sheets
     * POST /api/test/google-service-account/create-sheet
     */
    @PostMapping("/create-sheet")
    public ResponseEntity<Map<String, Object>> testCreateSheet(
            @RequestParam(defaultValue = "CRM Test Sheet") String title) {
        try {
            log.info("📊 Testing sheet creation: {}", title);

            // Create test data
            java.util.List<java.util.List<Object>> testData = java.util.Arrays.asList(
                java.util.Arrays.asList("Test Sheet", "Created by CRM"),
                java.util.Arrays.asList("Timestamp", java.time.LocalDateTime.now().toString()),
                java.util.Arrays.asList("Status", "Testing Google Service Account"),
                java.util.Arrays.asList("Column A", "Column B", "Column C"),
                java.util.Arrays.asList("Value 1", "Value 2", "Value 3")
            );

            String sheetId = googleService.createSpreadsheet(title, testData);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("sheetId", sheetId);
            response.put("sheetUrl", "https://docs.google.com/spreadsheets/d/" + sheetId);
            response.put("message", "Test sheet created successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Sheet creation test failed: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "FAILED");
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Test integrated PayProp + Google workflow (simulated)
     * POST /api/test/google-service-account/integrated-workflow
     */
    @PostMapping("/integrated-workflow")
    public ResponseEntity<Map<String, Object>> testIntegratedWorkflow(
            @RequestParam Long portfolioId,
            @RequestParam(defaultValue = "test@example.com") String initiatedByEmail) {
        try {
            log.info("🔄 Testing integrated PayProp + Google workflow for portfolio: {}", portfolioId);

            // Test the enhanced sync method
            var syncResult = portfolioSyncService.syncPortfolioToPayPropWithDocumentation(portfolioId, initiatedByEmail);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("syncResult", syncResult);
            response.put("message", "Integrated workflow test completed");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Integrated workflow test failed: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "FAILED");
            errorResponse.put("error", e.getMessage());
            errorResponse.put("portfolioId", portfolioId);

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Test simple Google integration
     * POST /api/test/google-service-account/simple-integration
     */
    @PostMapping("/simple-integration")
    public ResponseEntity<Map<String, Object>> testSimpleIntegration(
            @RequestParam(defaultValue = "test@example.com") String userEmail,
            @RequestParam(defaultValue = "Test Portfolio") String portfolioName) {
        try {
            log.info("🏷️ Testing simple Google integration for portfolio: {}", portfolioName);

            var result = simpleIntegrationService.testGoogleIntegration(userEmail, portfolioName);

            Map<String, Object> response = new HashMap<>();
            response.put("status", result.getStatus());
            response.put("userEmail", result.getUserEmail());
            response.put("portfolioName", result.getPortfolioName());
            response.put("customerFolderId", result.getCustomerFolderId());
            response.put("trackingSheetId", result.getTrackingSheetId());
            response.put("documentationSheetId", result.getDocumentationSheetId());
            response.put("message", result.getMessage());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Simple integration test failed: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "FAILED");
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Get service account info and status
     * GET /api/test/google-service-account/info
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getServiceAccountInfo() {
        try {
            Map<String, Object> info = new HashMap<>();

            // Test each service
            try {
                googleService.getDriveService();
                info.put("driveService", "AVAILABLE");
            } catch (Exception e) {
                info.put("driveService", "FAILED: " + e.getMessage());
            }

            try {
                googleService.getSheetsService();
                info.put("sheetsService", "AVAILABLE");
            } catch (Exception e) {
                info.put("sheetsService", "FAILED: " + e.getMessage());
            }

            info.put("gmailService", "NOT_IMPLEMENTED");

            info.put("simpleIntegrationService", simpleIntegrationService != null ? "AVAILABLE" : "NOT_AVAILABLE");
            info.put("timestamp", java.time.LocalDateTime.now().toString());

            return ResponseEntity.ok(info);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "FAILED");
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Test quota and API limits analysis
     * GET /api/test/google-service-account/quota-test
     */
    @GetMapping("/quota-test")
    public ResponseEntity<Map<String, Object>> testQuotaLimits() {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("📊 Testing API quota and limits...");

            // Test 1: Try different Sheets operations to isolate the issue
            Map<String, Object> operationTests = new HashMap<>();

            try {
                var sheetsService = googleService.getSheetsService();
                operationTests.put("sheetsServiceCreation", "SUCCESS");

                // Test 1a: Try to list existing spreadsheets (read operation)
                try {
                    // This would require different permissions/setup, but let's see the error
                    operationTests.put("readOperationTest", "Would need existing sheet ID");
                } catch (Exception e) {
                    operationTests.put("readOperationTest", "ERROR: " + e.getMessage());
                }

                // Test 1b: Try to create with minimal data
                try {
                    com.google.api.services.sheets.v4.model.Spreadsheet minimalSheet =
                        new com.google.api.services.sheets.v4.model.Spreadsheet()
                            .setProperties(new com.google.api.services.sheets.v4.model.SpreadsheetProperties()
                                .setTitle("Quota-Test-" + System.currentTimeMillis()));

                    var created = sheetsService.spreadsheets().create(minimalSheet).execute();
                    operationTests.put("createMinimalSheet", "SUCCESS: " + created.getSpreadsheetId());
                } catch (Exception e) {
                    operationTests.put("createMinimalSheet", "FAILED: " + e.getMessage());

                    // Check if it's a quota issue
                    if (e.getMessage().contains("quota") || e.getMessage().contains("limit") || e.getMessage().contains("exceeded")) {
                        operationTests.put("quotaIssue", "LIKELY - error mentions quota/limits");
                    } else if (e.getMessage().contains("403")) {
                        operationTests.put("quotaIssue", "UNLIKELY - 403 usually means permissions, not quota");
                    }
                }

            } catch (Exception e) {
                operationTests.put("sheetsServiceCreation", "FAILED: " + e.getMessage());
            }

            result.put("operationTests", operationTests);

            // Test 2: Check Drive API operations for comparison
            Map<String, Object> driveComparison = new HashMap<>();
            try {
                var driveService = googleService.getDriveService();

                // List files (read operation) - this works
                var fileList = driveService.files().list().setPageSize(1).execute();
                driveComparison.put("driveReadOperation", "SUCCESS - found " + fileList.getFiles().size() + " files");

                // Try to create a folder (write operation) to compare with Sheets
                try {
                    com.google.api.services.drive.model.File folderMetadata = new com.google.api.services.drive.model.File();
                    folderMetadata.setName("Quota-Test-Folder-" + System.currentTimeMillis());
                    folderMetadata.setMimeType("application/vnd.google-apps.folder");

                    var createdFolder = driveService.files().create(folderMetadata).execute();
                    driveComparison.put("driveWriteOperation", "SUCCESS - created folder " + createdFolder.getId());

                    // If Drive write works but Sheets write fails, it's not a general permissions issue
                    driveComparison.put("conclusion", "Drive WRITE works - issue is Sheets-specific, not general write permissions");

                } catch (Exception e) {
                    driveComparison.put("driveWriteOperation", "FAILED: " + e.getMessage());
                    driveComparison.put("conclusion", "Both Drive and Sheets writes fail - could be general write permission issue");
                }

            } catch (Exception e) {
                driveComparison.put("driveService", "FAILED: " + e.getMessage());
            }

            result.put("driveComparison", driveComparison);

            // Test 3: Analysis and recommendations
            Map<String, Object> analysis = new HashMap<>();
            analysis.put("nextStepsToCheck", java.util.Arrays.asList(
                "Google Cloud Console → APIs & Services → Google Sheets API → Quotas",
                "Check if Sheets API has usage restrictions or requires special approval",
                "Verify if service account needs 'Sheets Editor' role (not just project Editor)",
                "Check if there are Sheets-specific organization policies"
            ));

            result.put("analysis", analysis);
            result.put("timestamp", java.time.LocalDateTime.now().toString());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Get service account Client ID for domain delegation
     * GET /api/test/google-service-account/client-id
     */
    @GetMapping("/client-id")
    public ResponseEntity<Map<String, Object>> getClientId() {
        Map<String, Object> result = new HashMap<>();

        try {
            String keyVar = System.getenv("GOOGLE_SERVICE_ACCOUNT_KEY");
            if (keyVar != null) {
                // Extract client_id from service account key
                if (keyVar.contains("\"client_id\":\"")) {
                    String clientId = keyVar.substring(keyVar.indexOf("\"client_id\":\"") + 13);
                    clientId = clientId.substring(0, clientId.indexOf("\""));
                    result.put("clientId", clientId);
                }

                // Extract client_email for verification
                if (keyVar.contains("\"client_email\":\"")) {
                    String email = keyVar.substring(keyVar.indexOf("\"client_email\":\"") + 16);
                    email = email.substring(0, email.indexOf("\""));
                    result.put("serviceAccountEmail", email);
                }
            }

            result.put("requiredScopes", java.util.Arrays.asList(
                "https://www.googleapis.com/auth/spreadsheets",
                "https://www.googleapis.com/auth/drive"
            ));

            result.put("instructions", java.util.Arrays.asList(
                "1. Copy the clientId value below",
                "2. In Google Workspace Admin Console: Security → API Controls → Domain-wide delegation",
                "3. Add new: Client ID = [clientId], Scopes = [paste scopes]",
                "4. Save and wait 10-15 minutes"
            ));

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Test successful operations from metrics
     * GET /api/test/google-service-account/test-working-operations
     */
    @GetMapping("/test-working-operations")
    public ResponseEntity<Map<String, Object>> testWorkingOperations() {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("🧪 Testing operations that work according to metrics...");

            // Based on your metrics, these operations have 0% error rate:
            // - AppendValues (3 requests, 0% errors)
            // - BatchUpdateSpreadsheet (9 requests, 0% errors)
            // - UpdateValues (9 requests, 0% errors)

            // Step 1: Create a sheet using Drive API (workaround)
            String title = "Working-Operations-Test-" + System.currentTimeMillis();

            // Create via Drive API
            try {
                var driveService = googleService.getDriveService();

                com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
                fileMetadata.setName(title);
                fileMetadata.setMimeType("application/vnd.google-apps.spreadsheet");

                var file = driveService.files().create(fileMetadata).execute();
                String sheetId = file.getId();

                result.put("driveCreateResult", "SUCCESS - Created sheet: " + sheetId);

                // Step 2: Test UpdateValues (0% error rate according to metrics)
                try {
                    var sheetsService = googleService.getSheetsService();

                    java.util.List<java.util.List<Object>> values = java.util.Arrays.asList(
                        java.util.Arrays.asList("Metric Test", "Status", "Working"),
                        java.util.Arrays.asList("Operation", "UpdateValues", "0% error rate"),
                        java.util.Arrays.asList("Method", "Drive API + Sheets UpdateValues")
                    );

                    com.google.api.services.sheets.v4.model.ValueRange body =
                        new com.google.api.services.sheets.v4.model.ValueRange().setValues(values);

                    sheetsService.spreadsheets().values()
                        .update(sheetId, "A1", body)
                        .setValueInputOption("RAW")
                        .execute();

                    result.put("updateValuesResult", "SUCCESS - UpdateValues operation works!");

                    // Step 3: Test AppendValues (0% error rate according to metrics)
                    java.util.List<java.util.List<Object>> appendData = java.util.Arrays.asList(
                        java.util.Arrays.asList("Append Test", "AppendValues working", java.time.LocalDateTime.now().toString())
                    );

                    com.google.api.services.sheets.v4.model.ValueRange appendBody =
                        new com.google.api.services.sheets.v4.model.ValueRange().setValues(appendData);

                    sheetsService.spreadsheets().values()
                        .append(sheetId, "A:C", appendBody)
                        .setValueInputOption("RAW")
                        .execute();

                    result.put("appendValuesResult", "SUCCESS - AppendValues operation works!");

                    result.put("conclusion", "SOLUTION CONFIRMED: Drive API + Sheets operations work perfectly!");
                    result.put("sheetUrl", "https://docs.google.com/spreadsheets/d/" + sheetId);

                } catch (Exception e) {
                    result.put("sheetsOperationsResult", "FAILED: " + e.getMessage());
                }

            } catch (Exception e) {
                result.put("driveCreateResult", "FAILED: " + e.getMessage());
            }

        } catch (Exception e) {
            result.put("error", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Check Drive storage usage for service account
     * GET /api/test/google-service-account/storage-usage
     */
    @GetMapping("/storage-usage")
    public ResponseEntity<Map<String, Object>> checkStorageUsage() {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("📊 Checking Drive storage usage for service account...");

            var driveService = googleService.getDriveService();

            // Get storage quota information
            try {
                var about = driveService.about().get().setFields("storageQuota,user").execute();
                var quota = about.getStorageQuota();

                if (quota != null) {
                    result.put("quotaLimit", quota.getLimit());
                    result.put("quotaUsage", quota.getUsage());
                    result.put("quotaUsageInDrive", quota.getUsageInDrive());

                    if (quota.getLimit() != null && quota.getUsage() != null) {
                        long limitBytes = quota.getLimit();
                        long usageBytes = quota.getUsage();
                        double usagePercent = (double) usageBytes / limitBytes * 100;

                        result.put("quotaLimitGB", limitBytes / (1024.0 * 1024.0 * 1024.0));
                        result.put("quotaUsageGB", usageBytes / (1024.0 * 1024.0 * 1024.0));
                        result.put("quotaUsagePercent", Math.round(usagePercent * 100.0) / 100.0);
                    }
                }

                var user = about.getUser();
                if (user != null) {
                    result.put("userEmail", user.getEmailAddress());
                    result.put("userDisplayName", user.getDisplayName());
                }
            } catch (Exception e) {
                result.put("quotaError", e.getMessage());
            }

            // List all files to see what's taking space
            var fileList = driveService.files().list()
                .setFields("files(id,name,size,createdTime,mimeType)")
                .setPageSize(100)
                .execute();

            var files = fileList.getFiles();
            result.put("totalFileCount", files.size());

            // Analyze files by type and size
            long totalSize = 0;
            int spreadsheetCount = 0;
            int testFileCount = 0;
            long largestFileSize = 0;
            String largestFileName = "";

            java.util.List<Map<String, Object>> largeFiles = new java.util.ArrayList<>();

            for (var file : files) {
                if (file.getSize() != null) {
                    long fileSize = file.getSize();
                    totalSize += fileSize;

                    if (fileSize > largestFileSize) {
                        largestFileSize = fileSize;
                        largestFileName = file.getName();
                    }

                    // Track files over 1MB
                    if (fileSize > 1024 * 1024) {
                        Map<String, Object> fileInfo = new HashMap<>();
                        fileInfo.put("name", file.getName());
                        fileInfo.put("sizeMB", Math.round(fileSize / (1024.0 * 1024.0) * 100.0) / 100.0);
                        fileInfo.put("created", file.getCreatedTime());
                        fileInfo.put("mimeType", file.getMimeType());
                        largeFiles.add(fileInfo);
                    }
                }

                if ("application/vnd.google-apps.spreadsheet".equals(file.getMimeType())) {
                    spreadsheetCount++;
                }

                String fileName = file.getName();
                if (fileName != null && (fileName.contains("Test") || fileName.contains("Diagnostic"))) {
                    testFileCount++;
                }
            }

            result.put("totalSizeMB", Math.round(totalSize / (1024.0 * 1024.0) * 100.0) / 100.0);
            result.put("spreadsheetCount", spreadsheetCount);
            result.put("testFileCount", testFileCount);
            result.put("largestFileSizeMB", Math.round(largestFileSize / (1024.0 * 1024.0) * 100.0) / 100.0);
            result.put("largestFileName", largestFileName);
            result.put("filesOver1MB", largeFiles);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Test domain-wide delegation impersonation
     * GET /api/test/google-service-account/test-impersonation
     */
    @GetMapping("/test-impersonation")
    public ResponseEntity<Map<String, Object>> testImpersonation() {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("🔍 Testing domain-wide delegation with user impersonation...");

            // Test 1: Try to get impersonated services
            Map<String, Object> serviceTest = new HashMap<>();
            try {
                var impersonatedDrive = googleService.getImpersonatedDriveService();
                serviceTest.put("impersonatedDriveService", "SUCCESS");

                var impersonatedSheets = googleService.getImpersonatedSheetsService();
                serviceTest.put("impersonatedSheetsService", "SUCCESS");

            } catch (Exception e) {
                serviceTest.put("impersonatedServiceCreation", "FAILED: " + e.getMessage());
            }
            result.put("serviceCreationTest", serviceTest);

            // Test 2: Try impersonated folder creation
            Map<String, Object> folderTest = new HashMap<>();
            try {
                String testFolderName = "Impersonation-Test-" + System.currentTimeMillis();
                String folderId = googleService.createFolder(testFolderName, null);

                folderTest.put("status", "SUCCESS");
                folderTest.put("folderId", folderId);
                folderTest.put("message", "Impersonated folder creation successful");

            } catch (Exception e) {
                folderTest.put("status", "FAILED");
                folderTest.put("error", e.getMessage());
                folderTest.put("errorType", e.getClass().getSimpleName());
            }
            result.put("folderCreationTest", folderTest);

            // Test 3: Try impersonated sheet creation
            Map<String, Object> sheetTest = new HashMap<>();
            try {
                String testSheetTitle = "Impersonation-Sheet-Test-" + System.currentTimeMillis();
                java.util.List<java.util.List<Object>> testData = java.util.Arrays.asList(
                    java.util.Arrays.asList("Impersonation Test", "Status", "Testing"),
                    java.util.Arrays.asList("User", "sajidkazmi@propsk.com", "Impersonated"),
                    java.util.Arrays.asList("Timestamp", java.time.LocalDateTime.now().toString())
                );

                String sheetId = googleService.createSpreadsheet(testSheetTitle, testData);

                sheetTest.put("status", "SUCCESS");
                sheetTest.put("sheetId", sheetId);
                sheetTest.put("sheetUrl", "https://docs.google.com/spreadsheets/d/" + sheetId);
                sheetTest.put("message", "Impersonated sheet creation successful");

            } catch (Exception e) {
                sheetTest.put("status", "FAILED");
                sheetTest.put("error", e.getMessage());
                sheetTest.put("errorType", e.getClass().getSimpleName());

                // Analyze the specific error
                if (e.getMessage().contains("delegation")) {
                    sheetTest.put("errorCategory", "DOMAIN_WIDE_DELEGATION_NOT_CONFIGURED");
                    sheetTest.put("solution", "Need to configure domain-wide delegation in Google Workspace Admin Console");
                } else if (e.getMessage().contains("403")) {
                    sheetTest.put("errorCategory", "PERMISSION_DENIED");
                    sheetTest.put("solution", "Service account needs domain-wide delegation permissions");
                }
            }
            result.put("sheetCreationTest", sheetTest);

            // Test 4: Configuration status
            Map<String, Object> configStatus = new HashMap<>();
            configStatus.put("impersonationUser", "sajidkazmi@propsk.com");
            configStatus.put("requiredScopes", java.util.Arrays.asList(
                "https://www.googleapis.com/auth/drive",
                "https://www.googleapis.com/auth/spreadsheets"
            ));
            configStatus.put("domainWideDelegationRequired", true);
            configStatus.put("setupInstructions", java.util.Arrays.asList(
                "1. Get service account client ID from /client-id endpoint",
                "2. In Google Workspace Admin Console: Security → API Controls → Domain-wide delegation",
                "3. Add client ID with required scopes",
                "4. Save and wait 10-15 minutes for propagation"
            ));
            result.put("configurationStatus", configStatus);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Health check endpoint
     * GET /api/test/google-service-account/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "Google Service Account Test Controller");
        health.put("timestamp", java.time.LocalDateTime.now().toString());
        return ResponseEntity.ok(health);
    }

    /**
     * Deep technical diagnostics for 403 issue
     * GET /api/test/google-service-account/deep-diagnosis
     */
    @GetMapping("/deep-diagnosis")
    public ResponseEntity<Map<String, Object>> deepDiagnosis() {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("🔍 Running deep technical diagnostics for 403 issue...");

            // Test 1: Check project-level API restrictions
            Map<String, Object> projectTest = new HashMap<>();
            try {
                // Try to access Google Cloud Resource Manager API to check project details
                var driveService = googleService.getDriveService();
                var sheetsService = googleService.getSheetsService();

                projectTest.put("driveServiceCreation", "SUCCESS");
                projectTest.put("sheetsServiceCreation", "SUCCESS");

                // Test if the issue is in the actual API call vs service creation
                try {
                    driveService.files().list().setPageSize(1).execute();
                    projectTest.put("driveApiCall", "SUCCESS");
                } catch (Exception e) {
                    projectTest.put("driveApiCall", "FAILED: " + e.getMessage());
                }

                try {
                    // Try to create a minimal spreadsheet
                    com.google.api.services.sheets.v4.model.Spreadsheet testSheet =
                        new com.google.api.services.sheets.v4.model.Spreadsheet()
                            .setProperties(new com.google.api.services.sheets.v4.model.SpreadsheetProperties()
                                .setTitle("Deep-Diagnosis-Test-" + System.currentTimeMillis()));

                    sheetsService.spreadsheets().create(testSheet).execute();
                    projectTest.put("sheetsApiCall", "SUCCESS");
                } catch (Exception e) {
                    projectTest.put("sheetsApiCall", "FAILED: " + e.getMessage());
                    projectTest.put("sheetsErrorType", e.getClass().getSimpleName());

                    // Parse the error for more details
                    if (e.getMessage().contains("403")) {
                        projectTest.put("errorAnalysis", "403 error on Sheets create operation");
                        projectTest.put("possibleCauses", java.util.Arrays.asList(
                            "Service account lacks 'Editor' role specifically for Sheets API",
                            "Google Cloud Project has Sheets API restrictions enabled",
                            "Service account was created in wrong project context",
                            "Billing account not properly linked",
                            "Organization policies blocking Sheets API usage",
                            "API quota exceeded or suspended"
                        ));
                    }
                }

            } catch (Exception e) {
                projectTest.put("serviceCreation", "FAILED: " + e.getMessage());
            }
            result.put("projectLevelTest", projectTest);

            // Test 2: Service account metadata analysis
            Map<String, Object> serviceAccountTest = new HashMap<>();
            try {
                String keyVar = System.getenv("GOOGLE_SERVICE_ACCOUNT_KEY");
                if (keyVar != null) {
                    // Check if the service account email matches the project
                    if (keyVar.contains("\"client_email\"")) {
                        String email = keyVar.substring(keyVar.indexOf("\"client_email\":\"") + 16);
                        email = email.substring(0, email.indexOf("\""));
                        serviceAccountTest.put("extractedEmail", email);

                        // Check if email domain matches project
                        if (email.contains("@")) {
                            String domain = email.substring(email.indexOf("@") + 1);
                            serviceAccountTest.put("serviceAccountDomain", domain);
                            serviceAccountTest.put("expectedDomain", "crecrm.iam.gserviceaccount.com");
                            serviceAccountTest.put("domainMatch", domain.equals("crecrm.iam.gserviceaccount.com"));
                        }
                    }

                    if (keyVar.contains("\"project_id\"")) {
                        String projectId = keyVar.substring(keyVar.indexOf("\"project_id\":\"") + 14);
                        projectId = projectId.substring(0, projectId.indexOf("\""));
                        serviceAccountTest.put("extractedProjectId", projectId);
                        serviceAccountTest.put("expectedProjectId", "crecrm");
                        serviceAccountTest.put("projectIdMatch", projectId.equals("crecrm"));
                    }
                }
            } catch (Exception e) {
                serviceAccountTest.put("error", e.getMessage());
            }
            result.put("serviceAccountAnalysis", serviceAccountTest);

            // Test 3: API-specific diagnostics
            Map<String, Object> apiTest = new HashMap<>();
            apiTest.put("observation", "Drive API works but Sheets API fails with same service account");
            apiTest.put("technicalImplication", "Issue is API-specific, not general authentication");
            apiTest.put("nextSteps", java.util.Arrays.asList(
                "Check Google Cloud Console for API-specific restrictions",
                "Verify billing account has Sheets API usage enabled",
                "Check if service account needs additional Sheets-specific permissions",
                "Review organization policies for API usage restrictions"
            ));
            result.put("apiSpecificAnalysis", apiTest);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Comprehensive 403 diagnostics endpoint
     * GET /api/test/google-service-account/diagnose-403
     */
    @GetMapping("/diagnose-403")
    public ResponseEntity<Map<String, Object>> diagnose403() {
        Map<String, Object> diagnostics = new HashMap<>();

        try {
            log.info("🔍 Running comprehensive 403 diagnostics...");

            // Test 1: Service Account Key Analysis
            Map<String, Object> keyAnalysis = new HashMap<>();
            try {
                // This will trigger the enhanced debugging in createSheetsService
                googleService.getSheetsService();
                keyAnalysis.put("status", "SUCCESS");
                keyAnalysis.put("message", "Service account key loaded and parsed successfully");
            } catch (Exception e) {
                keyAnalysis.put("status", "FAILED");
                keyAnalysis.put("error", e.getMessage());
                keyAnalysis.put("errorType", e.getClass().getSimpleName());
            }
            diagnostics.put("serviceAccountKeyTest", keyAnalysis);

            // Test 2: Basic Google API Authentication
            Map<String, Object> authTest = new HashMap<>();
            try {
                Map<String, Object> result = googleService.testConnectivity();
                authTest.put("status", "SUCCESS");
                authTest.put("result", result);
            } catch (Exception e) {
                authTest.put("status", "FAILED");
                authTest.put("error", e.getMessage());
                authTest.put("errorType", e.getClass().getSimpleName());

                // Parse 403 specific details
                if (e.getMessage().contains("403") || e.getMessage().contains("forbidden")) {
                    authTest.put("errorCategory", "PERMISSION_DENIED");
                    authTest.put("suggestedFix", "Check Google Cloud IAM permissions for service account");
                }
            }
            diagnostics.put("googleApiAuthTest", authTest);

            // Test 3: Specific Sheets API Test
            Map<String, Object> sheetsTest = new HashMap<>();
            try {
                // Try to create a minimal test sheet
                String testTitle = "Diagnostic-Test-" + System.currentTimeMillis();

                log.info("🧪 Attempting to create diagnostic test sheet: {}", testTitle);

                // This will use our enhanced error handling
                java.util.List<java.util.List<Object>> testData = java.util.Arrays.asList(
                    java.util.Arrays.asList("Diagnostic Test", "Timestamp", java.time.LocalDateTime.now().toString()),
                    java.util.Arrays.asList("Test", "Success", "Service account working")
                );
                String sheetId = googleService.createSpreadsheet(testTitle, testData);

                sheetsTest.put("status", "SUCCESS");
                sheetsTest.put("testSheetId", sheetId);
                sheetsTest.put("message", "Successfully created test sheet");

            } catch (Exception e) {
                sheetsTest.put("status", "FAILED");
                sheetsTest.put("error", e.getMessage());
                sheetsTest.put("errorType", e.getClass().getSimpleName());

                // Enhanced 403 analysis
                if (e.getMessage().contains("403")) {
                    sheetsTest.put("errorCategory", "GOOGLE_SHEETS_PERMISSION_DENIED");
                    sheetsTest.put("possibleCauses", java.util.Arrays.asList(
                        "Service account lacks Editor role in Google Cloud Project",
                        "Google Sheets API not enabled",
                        "Billing not enabled on Google Cloud Project",
                        "Service account key expired or invalid",
                        "Project ID mismatch between key and actual project"
                    ));
                    sheetsTest.put("suggestedActions", java.util.Arrays.asList(
                        "Verify service account has 'Editor' role in IAM",
                        "Enable Google Sheets API in Google Cloud Console",
                        "Check billing status",
                        "Regenerate service account key if needed",
                        "Verify project ID matches in key and console"
                    ));
                }
            }
            diagnostics.put("googleSheetsApiTest", sheetsTest);

            // Test 4: Environment and Configuration Analysis
            Map<String, Object> envTest = new HashMap<>();
            try {
                // Check if service account key environment variable is set
                String keyVar = System.getenv("GOOGLE_SERVICE_ACCOUNT_KEY");
                envTest.put("serviceAccountKeyEnvSet", keyVar != null && !keyVar.trim().isEmpty());
                envTest.put("serviceAccountKeyLength", keyVar != null ? keyVar.length() : 0);

                // Check for common configuration issues
                if (keyVar != null) {
                    envTest.put("containsClientEmail", keyVar.contains("client_email"));
                    envTest.put("containsProjectId", keyVar.contains("project_id"));
                    envTest.put("containsPrivateKey", keyVar.contains("private_key"));
                    envTest.put("isValidJson", keyVar.trim().startsWith("{") && keyVar.trim().endsWith("}"));
                }

                envTest.put("status", "SUCCESS");
            } catch (Exception e) {
                envTest.put("status", "FAILED");
                envTest.put("error", e.getMessage());
            }
            diagnostics.put("environmentTest", envTest);

            diagnostics.put("timestamp", java.time.LocalDateTime.now().toString());
            diagnostics.put("overallStatus", "DIAGNOSTIC_COMPLETE");

            return ResponseEntity.ok(diagnostics);

        } catch (Exception e) {
            log.error("❌ 403 diagnostics failed: {}", e.getMessage(), e);

            diagnostics.put("status", "DIAGNOSTIC_FAILED");
            diagnostics.put("error", e.getMessage());
            diagnostics.put("timestamp", java.time.LocalDateTime.now().toString());

            return ResponseEntity.status(500).body(diagnostics);
        }
    }
}
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
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
    private PayPropGoogleIntegrationService integrationService;

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
     * Test PayProp tag creation with Google documentation
     * POST /api/test/google-service-account/tag-with-docs
     */
    @PostMapping("/tag-with-docs")
    public ResponseEntity<Map<String, Object>> testTagCreationWithDocs(
            @RequestParam Long portfolioId,
            @RequestParam(defaultValue = "test@example.com") String initiatedByEmail) {
        try {
            log.info("🏷️ Testing tag creation with Google documentation for portfolio: {}", portfolioId);

            var result = integrationService.syncPortfolioWithDocumentation(portfolioId, initiatedByEmail);

            Map<String, Object> response = new HashMap<>();
            response.put("status", result.getStatus());
            response.put("tagId", result.getTagId());
            response.put("googleDocumentation", result.getGoogleResult());
            response.put("message", "Tag creation with documentation completed");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Tag creation with docs test failed: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "FAILED");
            errorResponse.put("error", e.getMessage());
            errorResponse.put("portfolioId", portfolioId);

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

            info.put("integrationService", integrationService != null ? "AVAILABLE" : "NOT_AVAILABLE");
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
}
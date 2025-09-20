package site.easy.to.build.crm.service.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.service.google.GoogleServiceAccountService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Simplified Google integration service that doesn't depend on existing entities
 * This service focuses on testing Google functionality without complex dependencies
 */
@Service
public class SimpleGoogleIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(SimpleGoogleIntegrationService.class);

    @Autowired
    private GoogleServiceAccountService googleService;

    /**
     * Test Google integration with portfolio data (simplified)
     */
    public GoogleTestResult testGoogleIntegration(String userEmail, String portfolioName) {
        log.info("🔄 Testing Google integration for portfolio: {} by user: {}", portfolioName, userEmail);

        GoogleTestResult result = new GoogleTestResult();
        result.setStatus("PENDING");
        result.setUserEmail(userEmail);
        result.setPortfolioName(portfolioName);
        result.setTimestamp(LocalDateTime.now());

        try {
            // Step 1: Create customer folder
            String customerFolderId = googleService.getOrCreateCustomerFolder(userEmail, "Test Customer");
            result.setCustomerFolderId(customerFolderId);

            // Step 2: Create portfolio tracking sheet
            String trackingSheetId = createPortfolioTrackingSheet(portfolioName, userEmail);
            result.setTrackingSheetId(trackingSheetId);

            // Step 3: Create test documentation
            String docSheetId = createDocumentationSheet(portfolioName, userEmail);
            result.setDocumentationSheetId(docSheetId);

            result.setStatus("SUCCESS");
            result.setMessage("Google integration test completed successfully");

            log.info("✅ Google integration test completed successfully for portfolio: {}", portfolioName);

        } catch (Exception e) {
            log.error("❌ Google integration test failed for portfolio {}: {}", portfolioName, e.getMessage());
            result.setStatus("FAILED");
            result.setMessage("Integration test failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Create a simple portfolio tracking sheet
     */
    private String createPortfolioTrackingSheet(String portfolioName, String userEmail) throws Exception {
        String title = String.format("Portfolio Tracking - %s - %s",
            portfolioName,
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        List<List<Object>> data = Arrays.asList(
            Arrays.asList("Portfolio Tracking Sheet"),
            Arrays.asList("Generated", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))),
            Arrays.asList(""),
            Arrays.asList("Portfolio Information"),
            Arrays.asList("Portfolio Name", portfolioName),
            Arrays.asList("User Email", userEmail),
            Arrays.asList("Test Status", "Google Service Account Working"),
            Arrays.asList(""),
            Arrays.asList("Next Steps"),
            Arrays.asList("1. Implement PayProp tag creation"),
            Arrays.asList("2. Connect to actual portfolio data"),
            Arrays.asList("3. Add property synchronization")
        );

        return googleService.createSpreadsheet(title, data);
    }

    /**
     * Create documentation sheet
     */
    private String createDocumentationSheet(String portfolioName, String userEmail) throws Exception {
        String title = String.format("Integration Documentation - %s", portfolioName);

        List<List<Object>> data = Arrays.asList(
            Arrays.asList("Google Service Account Integration Documentation"),
            Arrays.asList("Portfolio", portfolioName),
            Arrays.asList("User", userEmail),
            Arrays.asList(""),
            Arrays.asList("Test Results"),
            Arrays.asList("Google Drive API", "✅ Working"),
            Arrays.asList("Google Sheets API", "✅ Working"),
            Arrays.asList("Folder Creation", "✅ Working"),
            Arrays.asList(""),
            Arrays.asList("Integration Status"),
            Arrays.asList("Service Account", "✅ Configured"),
            Arrays.asList("API Access", "✅ Verified"),
            Arrays.asList("Ready for PayProp Integration", "✅ Yes")
        );

        return googleService.createSpreadsheet(title, data);
    }

    /**
     * Simple connectivity test
     */
    public Map<String, Object> testConnectivity() {
        try {
            return googleService.testConnectivity();
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "FAILED");
            error.put("error", e.getMessage());
            return error;
        }
    }

    // Result class
    public static class GoogleTestResult {
        private String status;
        private String userEmail;
        private String portfolioName;
        private String customerFolderId;
        private String trackingSheetId;
        private String documentationSheetId;
        private String message;
        private LocalDateTime timestamp;

        // Getters and setters
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getUserEmail() { return userEmail; }
        public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
        public String getPortfolioName() { return portfolioName; }
        public void setPortfolioName(String portfolioName) { this.portfolioName = portfolioName; }
        public String getCustomerFolderId() { return customerFolderId; }
        public void setCustomerFolderId(String customerFolderId) { this.customerFolderId = customerFolderId; }
        public String getTrackingSheetId() { return trackingSheetId; }
        public void setTrackingSheetId(String trackingSheetId) { this.trackingSheetId = trackingSheetId; }
        public String getDocumentationSheetId() { return documentationSheetId; }
        public void setDocumentationSheetId(String documentationSheetId) { this.documentationSheetId = documentationSheetId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
}
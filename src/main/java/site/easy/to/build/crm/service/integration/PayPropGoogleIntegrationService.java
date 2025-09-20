package site.easy.to.build.crm.service.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.google.GoogleServiceAccountService;
import site.easy.to.build.crm.service.payprop.PayPropApiClient;
import site.easy.to.build.crm.service.payprop.PayPropOAuth2Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class PayPropGoogleIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(PayPropGoogleIntegrationService.class);

    @Autowired
    private PayPropOAuth2Service payPropOAuth2Service;

    @Autowired
    private PayPropApiClient payPropApiClient;

    @Autowired
    private GoogleServiceAccountService googleService;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private PortfolioSyncLogRepository syncLogRepository;

    /**
     * Complete portfolio sync workflow: PayProp operations + Google documentation
     */
    @Transactional
    public IntegratedSyncResult syncPortfolioWithDocumentation(Long portfolioId, String initiatedByEmail) {
        log.info("🔄 Starting integrated portfolio sync for portfolio {} by {}", portfolioId, initiatedByEmail);

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
            .orElseThrow(() -> new RuntimeException("Portfolio not found: " + portfolioId));

        // Create a placeholder customer for testing - avoid complex lookups for now
        Customer customer = new Customer();
        customer.setEmail(initiatedByEmail);
        customer.setName("Portfolio Owner");

        PortfolioSyncLog syncLog = createSyncLog(portfolioId, initiatedByEmail);

        try {
            // Step 1: PayProp tag creation
            String tagId = createPayPropTag(portfolio, customer);
            if (syncLog != null) {
                syncLog.setPayPropTagId(tagId);
            }

            // Step 2: Google documentation
            GoogleDocumentationResult googleResult = createGoogleDocumentation(portfolio, customer, tagId);

            // Step 3: Update local records
            updatePortfolioRecords(portfolio, tagId, googleResult);

            // Step 4: Complete sync log
            completeSyncLog(syncLog, "SUCCESS", null, googleResult);

            log.info("✅ Integrated portfolio sync completed successfully for portfolio {}", portfolioId);

            return IntegratedSyncResult.success(tagId, googleResult);

        } catch (PayPropException e) {
            log.error("❌ PayProp operation failed for portfolio {}: {}", portfolioId, e.getMessage());
            completeSyncLog(syncLog, "PAYPROP_FAILED", e.getMessage(), null);
            throw e;

        } catch (Exception e) {
            log.error("❌ Google documentation failed for portfolio {}: {}", portfolioId, e.getMessage());
            // PayProp succeeded, Google failed - partial success
            completeSyncLog(syncLog, "PAYPROP_SUCCESS_GOOGLE_FAILED", e.getMessage(), null);
            String tagId = (syncLog != null) ? syncLog.getPayPropTagId() : null;
            return IntegratedSyncResult.partialSuccess(tagId, e.getMessage());
        }
    }

    /**
     * Create PayProp tag (simulated for now - implement actual API call later)
     */
    private String createPayPropTag(Portfolio portfolio, Customer customer) throws Exception {
        log.info("🏷️ Creating PayProp tag for portfolio: {}", portfolio.getName());

        // Generate tag name
        String tagName = generateTagName(portfolio, customer);

        // TODO: Implement actual PayProp tag creation
        // String accessToken = payPropOAuth2Service.getValidAccessToken();
        // String tagId = payPropApiClient.createTag(accessToken, tagName);

        // For now, simulate tag creation
        String tagId = "SIM_" + System.currentTimeMillis();

        log.info("✅ PayProp tag simulated successfully: {} (ID: {})", tagName, tagId);
        return tagId;
    }

    /**
     * Create comprehensive Google documentation
     */
    private GoogleDocumentationResult createGoogleDocumentation(Portfolio portfolio, Customer customer, String tagId) throws Exception {
        log.info("📊 Creating Google documentation for portfolio: {}", portfolio.getName());

        GoogleDocumentationResult result = new GoogleDocumentationResult();

        try {
            // 1. Create or get customer folder
            String customerFolderId = googleService.getOrCreateCustomerFolder(customer.getEmail(), customer.getName());
            result.setCustomerFolderId(customerFolderId);

            // 2. Create portfolio tracking sheet
            String portfolioSheetId = createPortfolioTrackingSheet(portfolio, customer, tagId, customerFolderId);
            result.setPortfolioSheetId(portfolioSheetId);

            // 3. Create tag operation log sheet
            String tagLogSheetId = createTagOperationLog(portfolio, customer, tagId, customerFolderId);
            result.setTagLogSheetId(tagLogSheetId);

            // 4. Create property summary sheet
            String propertySummaryId = createPropertySummarySheet(portfolio, customer, customerFolderId);
            result.setPropertySummarySheetId(propertySummaryId);

            result.setStatus("SUCCESS");
            log.info("✅ Google documentation created successfully for portfolio: {}", portfolio.getName());

        } catch (Exception e) {
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
            throw e;
        }

        return result;
    }

    private String createPortfolioTrackingSheet(Portfolio portfolio, Customer customer, String tagId, String parentFolderId) throws Exception {
        String title = String.format("Portfolio Tracking - %s - %s", customer.getName(), portfolio.getName());

        List<List<Object>> data = Arrays.asList(
            Arrays.asList("Portfolio Tracking Sheet", "", "", ""),
            Arrays.asList("Generated", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))),
            Arrays.asList(""),
            Arrays.asList("Portfolio Information"),
            Arrays.asList("Portfolio ID", portfolio.getId()),
            Arrays.asList("Portfolio Name", portfolio.getName()),
            Arrays.asList("Customer", customer.getName()),
            Arrays.asList("Customer Email", customer.getEmail()),
            Arrays.asList("PayProp Tag ID", tagId),
            Arrays.asList("Sync Status", portfolio.getSyncStatus()),
            Arrays.asList(""),
            Arrays.asList("Property Count", getPropertyCount(portfolio.getId())),
            Arrays.asList("Last Updated", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
        );

        return googleService.createSpreadsheet(title, data);
    }

    private String createTagOperationLog(Portfolio portfolio, Customer customer, String tagId, String parentFolderId) throws Exception {
        String title = String.format("Tag Operations Log - %s", portfolio.getName());

        List<List<Object>> data = Arrays.asList(
            Arrays.asList("PayProp Tag Operations Log"),
            Arrays.asList("Portfolio", portfolio.getName()),
            Arrays.asList("Tag ID", tagId),
            Arrays.asList(""),
            Arrays.asList("Timestamp", "Operation", "Status", "Details"),
            Arrays.asList(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                "CREATE_TAG",
                "SUCCESS",
                "Tag created via CRM integration"
            )
        );

        return googleService.createSpreadsheet(title, data);
    }

    private String createPropertySummarySheet(Portfolio portfolio, Customer customer, String parentFolderId) throws Exception {
        String title = String.format("Property Summary - %s", portfolio.getName());

        List<Property> properties = getPropertiesForPortfolio(portfolio.getId());

        List<List<Object>> data = new ArrayList<>();
        data.add(Arrays.asList("Property Summary"));
        data.add(Arrays.asList("Portfolio", portfolio.getName()));
        data.add(Arrays.asList(""));
        data.add(Arrays.asList("Property ID", "Address", "Type", "Status", "Monthly Rent"));

        for (Property property : properties) {
            data.add(Arrays.asList(
                property.getId(),
                property.getPropertyName() != null ? property.getPropertyName() : "N/A",
                property.getPropertyType() != null ? property.getPropertyType() : "N/A",
                property.getIsArchived() != null ? property.getIsArchived() : "N/A",
                property.getMonthlyPayment() != null ? property.getMonthlyPayment() : "N/A"
            ));
        }

        return googleService.createSpreadsheet(title, data);
    }

    /**
     * Tag deletion workflow
     */
    @Transactional
    public IntegratedSyncResult deleteTagWithDocumentation(String tagId, String portfolioName, String initiatedByEmail) {
        log.info("🗑️ Starting tag deletion workflow for tag {} by {}", tagId, initiatedByEmail);

        try {
            // Step 1: Delete PayProp tag
            deletePayPropTag(tagId);

            // Step 2: Update Google documentation
            updateGoogleDocumentationForDeletion(tagId, portfolioName);

            log.info("✅ Tag deletion completed successfully: {}", tagId);
            return IntegratedSyncResult.success(tagId, null);

        } catch (Exception e) {
            log.error("❌ Tag deletion failed for {}: {}", tagId, e.getMessage());
            throw new RuntimeException("Tag deletion failed: " + e.getMessage(), e);
        }
    }

    private void deletePayPropTag(String tagId) throws Exception {
        // TODO: Implement actual PayProp tag deletion
        // String accessToken = payPropOAuth2Service.getValidAccessToken();
        // payPropApiClient.deleteTag(accessToken, tagId);

        // For now, simulate tag deletion
        log.info("✅ PayProp tag simulated deletion: {}", tagId);
    }

    private void updateGoogleDocumentationForDeletion(String tagId, String portfolioName) throws Exception {
        // This could update existing sheets or create a deletion log
        String title = String.format("Tag Deletion Log - %s", portfolioName);

        List<List<Object>> data = Arrays.asList(
            Arrays.asList("PayProp Tag Deletion Log"),
            Arrays.asList("Tag ID", tagId),
            Arrays.asList("Portfolio", portfolioName),
            Arrays.asList("Deleted At", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
        );

        googleService.createSpreadsheet(title, data);
        log.info("📊 Google documentation updated for tag deletion: {}", tagId);
    }

    // Helper methods
    private String generateTagName(Portfolio portfolio, Customer customer) {
        String customerRef = customer.getEmail() != null ? customer.getEmail().split("@")[0] : "system";
        return String.format("CRM-%s-%s", customerRef, portfolio.getName().replaceAll("[^a-zA-Z0-9]", ""));
    }

    private PortfolioSyncLog createSyncLog(Long portfolioId, String initiatedByEmail) {
        PortfolioSyncLog syncLog = new PortfolioSyncLog();
        syncLog.setPortfolioId(portfolioId);
        syncLog.setSyncType("PORTFOLIO_TO_PAYPROP_WITH_GOOGLE");
        syncLog.setOperation("CREATE_WITH_DOCUMENTATION");
        syncLog.setStatus("PENDING");
        syncLog.setSyncStartedAt(LocalDateTime.now());
        // Note: initiatedBy handling improved from previous fixes
        return syncLogRepository.save(syncLog);
    }

    private void completeSyncLog(PortfolioSyncLog syncLog, String status, String errorMessage, GoogleDocumentationResult googleResult) {
        syncLog.setStatus(status);
        syncLog.setErrorMessage(errorMessage);
        syncLog.setSyncCompletedAt(LocalDateTime.now());

        if (googleResult != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("customerFolderId", googleResult.getCustomerFolderId());
            payload.put("portfolioSheetId", googleResult.getPortfolioSheetId());
            payload.put("tagLogSheetId", googleResult.getTagLogSheetId());
            syncLog.setPayloadReceived(payload);
        }

        syncLogRepository.save(syncLog);
    }

    private void updatePortfolioRecords(Portfolio portfolio, String tagId, GoogleDocumentationResult googleResult) {
        // Update portfolio with tag info (use existing fields)
        portfolio.setPayPropTags(tagId); // Store tag ID in existing field
        portfolio.setSyncStatus(SyncStatus.synced);
        portfolio.setLastSyncAt(LocalDateTime.now()); // Use existing field name
        portfolioRepository.save(portfolio);
    }

    private int getPropertyCount(Long portfolioId) {
        return (int) propertyRepository.countByPortfolioId(portfolioId);
    }

    private List<Property> getPropertiesForPortfolio(Long portfolioId) {
        return propertyRepository.findByPortfolioId(portfolioId);
    }

    // Result classes
    public static class IntegratedSyncResult {
        private String status;
        private String tagId;
        private GoogleDocumentationResult googleResult;
        private String errorMessage;

        public static IntegratedSyncResult success(String tagId, GoogleDocumentationResult googleResult) {
            IntegratedSyncResult result = new IntegratedSyncResult();
            result.status = "SUCCESS";
            result.tagId = tagId;
            result.googleResult = googleResult;
            return result;
        }

        public static IntegratedSyncResult partialSuccess(String tagId, String errorMessage) {
            IntegratedSyncResult result = new IntegratedSyncResult();
            result.status = "PARTIAL_SUCCESS";
            result.tagId = tagId;
            result.errorMessage = errorMessage;
            return result;
        }

        // Getters and setters
        public String getStatus() { return status; }
        public String getTagId() { return tagId; }
        public GoogleDocumentationResult getGoogleResult() { return googleResult; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static class GoogleDocumentationResult {
        private String status;
        private String customerFolderId;
        private String portfolioSheetId;
        private String tagLogSheetId;
        private String propertySummarySheetId;
        private String errorMessage;

        // Getters and setters
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getCustomerFolderId() { return customerFolderId; }
        public void setCustomerFolderId(String customerFolderId) { this.customerFolderId = customerFolderId; }
        public String getPortfolioSheetId() { return portfolioSheetId; }
        public void setPortfolioSheetId(String portfolioSheetId) { this.portfolioSheetId = portfolioSheetId; }
        public String getTagLogSheetId() { return tagLogSheetId; }
        public void setTagLogSheetId(String tagLogSheetId) { this.tagLogSheetId = tagLogSheetId; }
        public String getPropertySummarySheetId() { return propertySummarySheetId; }
        public void setPropertySummarySheetId(String propertySummarySheetId) { this.propertySummarySheetId = propertySummarySheetId; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    public static class PayPropException extends RuntimeException {
        public PayPropException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
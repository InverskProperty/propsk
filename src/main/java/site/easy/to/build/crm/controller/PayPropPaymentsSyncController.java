// PayPropPaymentsSyncController.java - Complete corrected version
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.service.payprop.PayPropOAuth2Service;
import site.easy.to.build.crm.service.payprop.PayPropSyncService;
import site.easy.to.build.crm.service.payprop.PayPropSyncOrchestrator;
import site.easy.to.build.crm.service.payprop.SyncResult;
import site.easy.to.build.crm.util.AuthorizationUtil;
import site.easy.to.build.crm.repository.PaymentCategoryRepository;
import site.easy.to.build.crm.repository.PaymentRepository;
import site.easy.to.build.crm.repository.BeneficiaryBalanceRepository;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.service.property.PropertyService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Controller
@RequestMapping("/admin/payprop/payments")
public class PayPropPaymentsSyncController {

    private static final Logger log = LoggerFactory.getLogger(PayPropPaymentsSyncController.class);

    private final PayPropOAuth2Service oAuth2Service;
    private final PayPropSyncService syncService;
    private final PayPropSyncOrchestrator syncOrchestrator;
    private final PaymentCategoryRepository paymentCategoryRepository;
    private final PaymentRepository paymentRepository;
    private final BeneficiaryBalanceRepository beneficiaryBalanceRepository;
    private final RestTemplate restTemplate;
    private final PropertyService propertyService;

    @Autowired
    public PayPropPaymentsSyncController(PayPropOAuth2Service oAuth2Service,
                                        PayPropSyncService syncService,
                                        PayPropSyncOrchestrator syncOrchestrator,
                                        PaymentCategoryRepository paymentCategoryRepository,
                                        PaymentRepository paymentRepository,
                                        BeneficiaryBalanceRepository beneficiaryBalanceRepository,
                                        RestTemplate restTemplate,
                                        PropertyService propertyService) {
        this.oAuth2Service = oAuth2Service;
        this.syncService = syncService;
        this.syncOrchestrator = syncOrchestrator;
        this.paymentCategoryRepository = paymentCategoryRepository;
        this.paymentRepository = paymentRepository;
        this.beneficiaryBalanceRepository = beneficiaryBalanceRepository;
        this.restTemplate = restTemplate;
        this.propertyService = propertyService;
    }

    /**
     * Helper method to get current user ID from authentication
     */
    private Long getCurrentUserId(Authentication authentication) {
        if (authentication != null && authentication.getName() != null) {
            return 1L; // Default fallback - adjust based on your system
        }
        return 1L; // Default fallback
    }

    /**
     * Payments sync dashboard
     */
    @GetMapping("/dashboard")
    public String paymentsDashboard(Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return "redirect:/access-denied";
        }

        // OAuth2 status
        model.addAttribute("hasTokens", oAuth2Service.hasValidTokens());
        
        // Payment sync statistics
        try {
            long totalCategories = paymentCategoryRepository.count();
            long syncedCategories = paymentCategoryRepository.findByPayPropCategoryIdIsNotNull().size();
            long totalPayments = paymentRepository.count();
            long totalBalances = beneficiaryBalanceRepository.count();
            
            model.addAttribute("totalCategories", totalCategories);
            model.addAttribute("syncedCategories", syncedCategories);
            model.addAttribute("totalPayments", totalPayments);
            model.addAttribute("totalBalances", totalBalances);
            
        } catch (Exception e) {
            log.error("Error loading payments dashboard statistics: {}", e.getMessage());
            model.addAttribute("dashboardError", "Error loading statistics: " + e.getMessage());
            model.addAttribute("totalCategories", 0);
            model.addAttribute("syncedCategories", 0);
            model.addAttribute("totalPayments", 0);
            model.addAttribute("totalBalances", 0);
        }

        model.addAttribute("pageTitle", "PayProp Payments Sync Dashboard");
        return "admin/payprop-payments-dashboard";
    }

    /**
     * Sync payment categories only
     */
    @PostMapping("/sync/categories")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncPaymentCategories(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        if (!oAuth2Service.hasValidTokens()) {
            response.put("success", false);
            response.put("message", "PayProp not authorized. Please authorize first.");
            return ResponseEntity.ok(response);
        }

        try {
            Long initiatedBy = getCurrentUserId(authentication);
            SyncResult result = syncService.syncPaymentCategoriesFromPayProp();
            
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("details", result.getDetails());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error syncing payment categories: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Payment categories sync failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Sync payments only
     */
    @PostMapping("/sync/payments")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncPayments(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        if (!oAuth2Service.hasValidTokens()) {
            response.put("success", false);
            response.put("message", "PayProp not authorized. Please authorize first.");
            return ResponseEntity.ok(response);
        }

        try {
            Long initiatedBy = getCurrentUserId(authentication);
            SyncResult result = syncService.syncPaymentsToDatabase(initiatedBy);
            
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("details", result.getDetails());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error syncing payments: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Payments sync failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Sync beneficiary balances only
     */
    @PostMapping("/sync/balances")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncBeneficiaryBalances(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        if (!oAuth2Service.hasValidTokens()) {
            response.put("success", false);
            response.put("message", "PayProp not authorized. Please authorize first.");
            return ResponseEntity.ok(response);
        }

        try {
            Long initiatedBy = getCurrentUserId(authentication);
            SyncResult result = syncService.syncBeneficiaryBalancesToDatabase(initiatedBy);
            
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("details", result.getDetails());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error syncing beneficiary balances: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Beneficiary balances sync failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Sync all payment data (categories + payments + balances)
     */
    @PostMapping("/sync/all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncAllPaymentData(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        if (!oAuth2Service.hasValidTokens()) {
            response.put("success", false);
            response.put("message", "PayProp not authorized. Please authorize first.");
            return ResponseEntity.ok(response);
        }

        try {
            Long initiatedBy = getCurrentUserId(authentication);
            
            // Step 1: Sync payment categories
            SyncResult categoriesResult = syncService.syncPaymentCategoriesFromPayProp();
            
            // Step 2: Sync payments
            SyncResult paymentsResult = syncService.syncPaymentsToDatabase(initiatedBy);
            
            // Step 3: Sync beneficiary balances
            SyncResult balancesResult = syncService.syncBeneficiaryBalancesToDatabase(initiatedBy);
            
            // Combine results
            boolean overallSuccess = categoriesResult.isSuccess() && 
                                   paymentsResult.isSuccess() && 
                                   balancesResult.isSuccess();
            
            Map<String, Object> details = new HashMap<>();
            details.put("categories", categoriesResult.getDetails());
            details.put("payments", paymentsResult.getDetails());
            details.put("balances", balancesResult.getDetails());
            
            response.put("success", overallSuccess);
            response.put("message", overallSuccess ? 
                "All payment data synced successfully" : 
                "Payment data sync completed with some issues");
            response.put("details", details);
            response.put("categoriesResult", categoriesResult.getMessage());
            response.put("paymentsResult", paymentsResult.getMessage());
            response.put("balancesResult", balancesResult.getMessage());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error syncing all payment data: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Payment data sync failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Get payment sync statistics
     */
    @GetMapping("/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPaymentStats(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            Map<String, Object> stats = new HashMap<>();
            
            // Payment categories stats
            long totalCategories = paymentCategoryRepository.count();
            long syncedCategories = paymentCategoryRepository.findByPayPropCategoryIdIsNotNull().size();
            long activeCategories = paymentCategoryRepository.findByIsActive("Y").size();
            
            stats.put("totalCategories", totalCategories);
            stats.put("syncedCategories", syncedCategories);
            stats.put("activeCategories", activeCategories);
            stats.put("unsyncedCategories", totalCategories - syncedCategories);
            
            // Payment stats
            long totalPayments = paymentRepository.count();
            stats.put("totalPayments", totalPayments);
            
            // Balance stats
            long totalBalances = beneficiaryBalanceRepository.count();
            stats.put("totalBalances", totalBalances);
            
            response.put("success", true);
            response.put("stats", stats);
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting payment statistics: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Error loading statistics: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Test payment instructions API endpoint
     */
    @GetMapping("/test/categories")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testPaymentCategoriesApi(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        if (!oAuth2Service.hasValidTokens()) {
            response.put("success", false);
            response.put("message", "PayProp not authorized. Please authorize first.");
            return ResponseEntity.ok(response);
        }

        try {
            // Get payment instructions data to see the structure
            PayPropSyncService.PayPropExportResult result = syncService.exportPaymentsFromPayProp(1, 3);
            
            response.put("success", true);
            response.put("message", "Payment instructions API test successful");
            response.put("totalItems", result.getItems().size());
            
            // Show FULL payment instruction data structure
            if (!result.getItems().isEmpty()) {
                response.put("fullPaymentSample", result.getItems().get(0));
                response.put("allFieldNames", result.getItems().get(0).keySet());
                
                if (result.getItems().size() > 1) {
                    response.put("secondPaymentSample", result.getItems().get(1));
                }
            }
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error testing payment instructions API: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "API test failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Test ACTUAL payment transactions API endpoint
     */
    @GetMapping("/test/actual-payments")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testActualPaymentsApi(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        if (!oAuth2Service.hasValidTokens()) {
            response.put("success", false);
            response.put("message", "PayProp not authorized. Please authorize first.");
            return ResponseEntity.ok(response);
        }

        try {
            // Test the ACTUAL payments endpoint with recent date range
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            LocalDate fromDate = LocalDate.now().minusMonths(3); // Last 3 months
            String actualPaymentsUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1/export/payments" +
                    "?include_beneficiary_info=true" +
                    "&from_date=" + fromDate.toString() +
                    "&filter_by=reconciliation_date" +
                    "&page=1&rows=3";
            
            log.info("Testing actual payments endpoint: {}", actualPaymentsUrl);
            
            ResponseEntity<Map> apiResponse = restTemplate.exchange(actualPaymentsUrl, HttpMethod.GET, request, Map.class);
            
            if (apiResponse.getStatusCode().is2xxSuccessful() && apiResponse.getBody() != null) {
                Map<String, Object> responseBody = apiResponse.getBody();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> payments = (List<Map<String, Object>>) responseBody.get("items");
                
                response.put("success", true);
                response.put("message", "Actual payments API test successful");
                response.put("totalItems", payments != null ? payments.size() : 0);
                response.put("fromDate", fromDate.toString());
                
                if (payments != null && !payments.isEmpty()) {
                    response.put("fullPaymentSample", payments.get(0));
                    response.put("allPaymentFields", payments.get(0).keySet());
                    
                    if (payments.size() > 1) {
                        response.put("secondPaymentSample", payments.get(1));
                    }
                } else {
                    response.put("message", "No actual payment transactions found for date range " + fromDate + " to now. Try extending the date range.");
                }
            } else {
                response.put("success", false);
                response.put("message", "Actual payments API returned: " + apiResponse.getStatusCode());
            }
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error testing actual payments API: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Actual payments API test failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Test ALL-PAYMENTS report API endpoint
     */
    @GetMapping("/test/all-payments-report")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testAllPaymentsReportApi(@RequestParam(required = false) String propertyId, 
                                                                        Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        if (!oAuth2Service.hasValidTokens()) {
            response.put("success", false);
            response.put("message", "PayProp not authorized. Please authorize first.");
            return ResponseEntity.ok(response);
        }

        try {
            // If no property ID provided, get one from our database
            if (propertyId == null || propertyId.isEmpty()) {
                List<Property> properties = propertyService.findAll().stream()
                    .filter(p -> p.getPayPropId() != null)
                    .limit(1)
                    .collect(Collectors.toList());
                
                if (!properties.isEmpty()) {
                    propertyId = properties.get(0).getPayPropId();
                } else {
                    response.put("success", false);
                    response.put("message", "No properties with PayProp IDs found for testing");
                    return ResponseEntity.ok(response);
                }
            }
            
            // Test the ALL-PAYMENTS report endpoint
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            LocalDate fromDate = LocalDate.now().minusMonths(6); // Last 6 months
            LocalDate toDate = LocalDate.now();
            String allPaymentsUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1/report/all-payments" +
                    "?property_id=" + propertyId +
                    "&from_date=" + fromDate.toString() +
                    "&to_date=" + toDate.toString() +
                    "&filter_by=reconciliation_date" +
                    "&include_beneficiary_info=true";
            
            log.info("Testing all-payments report endpoint: {}", allPaymentsUrl);
            
            ResponseEntity<Map> apiResponse = restTemplate.exchange(allPaymentsUrl, HttpMethod.GET, request, Map.class);
            
            if (apiResponse.getStatusCode().is2xxSuccessful() && apiResponse.getBody() != null) {
                Map<String, Object> responseBody = apiResponse.getBody();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> payments = (List<Map<String, Object>>) responseBody.get("items");
                
                response.put("success", true);
                response.put("message", "All-payments report API test successful");
                response.put("totalItems", payments != null ? payments.size() : 0);
                response.put("propertyId", propertyId);
                response.put("dateRange", fromDate + " to " + toDate);
                
                if (payments != null && !payments.isEmpty()) {
                    response.put("fullPaymentSample", payments.get(0));
                    response.put("allPaymentFields", payments.get(0).keySet());
                    
                    if (payments.size() > 1) {
                        response.put("secondPaymentSample", payments.get(1));
                    }
                } else {
                    response.put("message", "No payment data found for property " + propertyId + " in date range " + fromDate + " to " + toDate);
                }
            } else {
                response.put("success", false);
                response.put("message", "All-payments report API returned: " + apiResponse.getStatusCode());
            }
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error testing all-payments report API: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "All-payments report API test failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
}
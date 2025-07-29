// PayPropOAuth2Controller.java - OAuth2 Authorization Flow with Test Endpoints
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.easy.to.build.crm.service.payprop.PayPropOAuth2Service;
import site.easy.to.build.crm.service.payprop.PayPropOAuth2Service.PayPropTokens;
import site.easy.to.build.crm.service.payprop.PayPropSyncService;
import site.easy.to.build.crm.service.payprop.PayPropFinancialSyncService;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.repository.BatchPaymentRepository;
import site.easy.to.build.crm.repository.BeneficiaryRepository;
import site.easy.to.build.crm.repository.FinancialTransactionRepository;
import site.easy.to.build.crm.util.AuthorizationUtil;
import site.easy.to.build.crm.repository.PaymentRepository;
import site.easy.to.build.crm.repository.PaymentCategoryRepository;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.entity.Payment;
import site.easy.to.build.crm.entity.Property;  // ✅ ADDED: Missing import fix
import site.easy.to.build.crm.entity.FinancialTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.HashSet;
import site.easy.to.build.crm.service.payprop.PayPropSyncService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.List;

@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Controller
@RequestMapping("/api/payprop/oauth")
public class PayPropOAuth2Controller {

    private static final Logger logger = LoggerFactory.getLogger(PayPropOAuth2Controller.class);
    private static final Logger log = LoggerFactory.getLogger(PayPropOAuth2Controller.class);
    
    private final PayPropOAuth2Service oAuth2Service;
    private final RestTemplate restTemplate;
    private final String payPropApiBase = "https://ukapi.staging.payprop.com/api/agency/v1.1";
    
    // NEW: Financial sync service injection
    @Autowired
    private PayPropFinancialSyncService payPropFinancialSyncService;
    
    @Autowired
    private PropertyRepository propertyRepository;
    
    @Autowired
    private BeneficiaryRepository beneficiaryRepository;
    
    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PayPropSyncService payPropSyncService;
    
    @Autowired
    private PaymentCategoryRepository paymentCategoryRepository;

    @Autowired
    private BatchPaymentRepository batchPaymentRepository;
    
    @Autowired
    private PropertyService propertyService;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    public PayPropOAuth2Controller(PayPropOAuth2Service oAuth2Service, RestTemplate restTemplate) {
        this.oAuth2Service = oAuth2Service;
        this.restTemplate = restTemplate;
        this.payPropSyncService = payPropSyncService; // Add this line
    }

    /**
     * Show PayProp OAuth2 status and initiate authorization
     */
    @GetMapping("/status")
    public String showOAuthStatus(Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return "redirect:/access-denied";
        }

        PayPropTokens tokens = oAuth2Service.getCurrentTokens();
        model.addAttribute("hasTokens", tokens != null);
        model.addAttribute("tokens", tokens);
        model.addAttribute("pageTitle", "PayProp Integration Setup");

        return "payprop/oauth-status";
    }

    /**
     * Initiate OAuth2 authorization flow
     */
    @GetMapping("/authorize")
    public String initiateAuthorization(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return "redirect:/access-denied";
        }

        // Generate state parameter for security
        String state = UUID.randomUUID().toString();
        
        // In production, store state in session for verification
        // session.setAttribute("oauth_state", state);
        
        String authorizationUrl = oAuth2Service.getAuthorizationUrl(state);
        
        System.out.println("🔐 Redirecting to PayProp authorization: " + authorizationUrl);
        
        return "redirect:" + authorizationUrl;
    }

    /**
     * Handle OAuth2 callback from PayProp
     */
    @GetMapping("/callback")
    public String handleCallback(@RequestParam(required = false) String code,
                                @RequestParam(required = false) String error,
                                @RequestParam(required = false) String error_description,
                                @RequestParam(required = false) String state,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        
        System.out.println("📞 PayProp OAuth2 callback received");
        System.out.println("Code: " + (code != null ? code.substring(0, Math.min(20, code.length())) + "..." : "null"));
        System.out.println("Error: " + error);
        System.out.println("State: " + state);

        if (error != null) {
            System.err.println("❌ OAuth2 authorization failed: " + error);
            redirectAttributes.addFlashAttribute("error", 
                "PayProp authorization failed: " + (error_description != null ? error_description : error));
            return "redirect:/api/payprop/oauth/status";
        }

        if (code == null) {
            System.err.println("❌ No authorization code received");
            redirectAttributes.addFlashAttribute("error", "No authorization code received from PayProp");
            return "redirect:/api/payprop/oauth/status";
        }

        try {
            // Exchange code for tokens
            PayPropTokens tokens = oAuth2Service.exchangeCodeForToken(code);
            
            System.out.println("✅ PayProp OAuth2 setup completed successfully!");
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "PayProp integration authorized successfully! You can now sync data with PayProp.");
            
            return "redirect:/api/payprop/oauth/status";
            
        } catch (Exception e) {
            System.err.println("❌ Failed to exchange authorization code: " + e.getMessage());
            e.printStackTrace();
            
            redirectAttributes.addFlashAttribute("error", 
                "Failed to complete PayProp authorization: " + e.getMessage());
            return "redirect:/api/payprop/oauth/status";
        }
    }

    /**
     * ✅ ADD THIS METHOD to your PayPropOAuth2Controller.java
     * Test export payments endpoint to see actual PayProp data structure
     * Also ADD this logger at the top of your class:
     * private static final Logger log = LoggerFactory.getLogger(PayPropOAuth2Controller.class);
     */
    @PostMapping("/test-export-payments-detailed")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testExportPaymentsDetailed(
            @RequestBody Map<String, Object> request, 
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            if (!oAuth2Service.hasValidTokens()) {
                response.put("success", false);
                response.put("message", "PayProp not authorized. Please authorize first.");
                return ResponseEntity.ok(response);
            }

            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> httpRequest = new HttpEntity<>(headers);
            
            // Build URL with optional property filter
            StringBuilder urlBuilder = new StringBuilder(payPropApiBase + "/export/payments");
            List<String> params = new ArrayList<>();
            
            String propertyId = (String) request.get("propertyId");
            if (propertyId != null && !propertyId.trim().isEmpty()) {
                params.add("property_id=" + propertyId);
            }
            
            Boolean includeBeneficiaryInfo = (Boolean) request.getOrDefault("includeBeneficiaryInfo", true);
            if (includeBeneficiaryInfo) {
                params.add("include_beneficiary_info=true");
            }
            
            params.add("rows=10"); // Limit for testing
            
            if (!params.isEmpty()) {
                urlBuilder.append("?").append(String.join("&", params));
            }
            
            String url = urlBuilder.toString();
            log.info("🔍 Testing export payments: {}", url);
            
            ResponseEntity<Map> apiResponse = restTemplate.exchange(
                url, HttpMethod.GET, httpRequest, Map.class);
            
            if (apiResponse.getStatusCode().is2xxSuccessful() && apiResponse.getBody() != null) {
                Map<String, Object> paymentsData = apiResponse.getBody();
                List<Map<String, Object>> items = (List<Map<String, Object>>) paymentsData.get("items");
                
                response.put("success", true);
                response.put("url", url);
                response.put("total_items", items != null ? items.size() : 0);
                response.put("response", paymentsData);
                
                // Analyze the payment data structure
                if (items != null && !items.isEmpty()) {
                    Map<String, Object> samplePayment = items.get(0);
                    Set<String> fields = samplePayment.keySet();
                    
                    response.put("sample_payment_fields", fields);
                    response.put("sample_payment", samplePayment);
                    
                    // Check for key fields we need
                    Map<String, Object> fieldAnalysis = new HashMap<>();
                    fieldAnalysis.put("has_id", samplePayment.containsKey("id"));
                    fieldAnalysis.put("has_amount", samplePayment.containsKey("amount"));
                    fieldAnalysis.put("has_payment_date", samplePayment.containsKey("payment_date"));
                    fieldAnalysis.put("has_batch_id", samplePayment.containsKey("batch_id"));
                    fieldAnalysis.put("has_reconciliation_date", samplePayment.containsKey("reconciliation_date"));
                    fieldAnalysis.put("has_beneficiary_info", samplePayment.containsKey("beneficiary_info"));
                    fieldAnalysis.put("has_property", samplePayment.containsKey("property"));
                    fieldAnalysis.put("has_property_id", samplePayment.containsKey("property_id"));
                    fieldAnalysis.put("has_category", samplePayment.containsKey("category"));
                    fieldAnalysis.put("has_status", samplePayment.containsKey("status"));
                    
                    response.put("field_analysis", fieldAnalysis);
                    
                    log.info("✅ Export payments data retrieved:");
                    log.info("   Total items: {}", items.size());
                    log.info("   Sample payment ID: {}", samplePayment.get("id"));
                    log.info("   Available fields: {}", fields);
                    log.info("   Field analysis: {}", fieldAnalysis);
                } else {
                    log.warn("⚠️ No payment items found in export/payments response");
                }
                
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Failed to retrieve payments data");
                response.put("status", apiResponse.getStatusCode().value());
                return ResponseEntity.ok(response);
            }
            
        } catch (HttpClientErrorException e) {
            log.error("❌ PayProp API error for export payments: {} - {}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            
            response.put("success", false);
            response.put("message", "PayProp API error");
            response.put("status", e.getStatusCode().value());
            response.put("error", e.getResponseBodyAsString());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error testing export payments: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/test-all-payments-report")
    public ResponseEntity<Map<String, Object>> testAllPaymentsReport() {
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            // Get last 30 days of actual payments
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(30);
            
            String url = "https://ukapi.staging.payprop.com/api/agency/v1.1/report/all-payments" +
                "?from_date=" + startDate +
                "&to_date=" + endDate +
                "&filter_by=reconciliation_date";
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            Map<String, Object> result = new HashMap<>();
            result.put("url", url);
            result.put("response", response.getBody());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/test-property-payments")
    public ResponseEntity<Map<String, Object>> testPropertyPayments(@RequestBody Map<String, String> request) {
        try {
            String propertyId = request.get("propertyId");
            
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> httpRequest = new HttpEntity<>(headers);
            
            // Get last 90 days of payments for this property
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(90);
            
            String url = "https://ukapi.staging.payprop.com/api/agency/v1.1/export/payments" +
                "?property_id=" + propertyId +
                "&from_date=" + startDate +
                "&to_date=" + endDate +
                "&include_beneficiary_info=true" +
                "&rows=100";
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, httpRequest, Map.class);
            
            Map<String, Object> result = new HashMap<>();
            result.put("url", url);
            result.put("property_id", propertyId);
            result.put("response", response.getBody());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Add this to PayPropOAuth2Controller.java to test batch payments
     * No database dependencies needed for this test
     */
    @PostMapping("/test-batch-payments-api-only")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testBatchPaymentsApiOnly(Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            if (!oAuth2Service.hasValidTokens()) {
                response.put("success", false);
                response.put("message", "PayProp not authorized");
                return ResponseEntity.ok(response);
            }

            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            // Test with last 30 days
            LocalDate fromDate = LocalDate.now().minusDays(30);
            LocalDate toDate = LocalDate.now();
            
            String url = "https://ukapi.staging.payprop.com/api/agency/v1.1/report/all-payments" +
                "?from_date=" + fromDate +
                "&to_date=" + toDate +
                "&filter_by=reconciliation_date" +
                "&include_beneficiary_info=true" +
                "&rows=5";
            
            log.info("🔍 Testing: {}", url);
            
            ResponseEntity<Map> apiResponse = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (apiResponse.getStatusCode().is2xxSuccessful() && apiResponse.getBody() != null) {
                Map<String, Object> responseBody = apiResponse.getBody();
                List<Map<String, Object>> payments = (List<Map<String, Object>>) responseBody.get("items");
                
                response.put("success", true);
                response.put("total_items", payments != null ? payments.size() : 0);
                response.put("date_range", fromDate + " to " + toDate);
                response.put("response_keys", responseBody.keySet());
                
                if (payments != null && !payments.isEmpty()) {
                    // Check first payment for batch structure
                    Map<String, Object> firstPayment = payments.get(0);
                    log.info("📊 First payment keys: {}", firstPayment.keySet());
                    
                    Object paymentBatch = firstPayment.get("payment_batch");
                    response.put("has_payment_batch_field", paymentBatch != null);
                    response.put("payment_batch_type", paymentBatch != null ? paymentBatch.getClass().getSimpleName() : "null");
                    
                    if (paymentBatch instanceof Map) {
                        Map<String, Object> batchMap = (Map<String, Object>) paymentBatch;
                        response.put("batch_keys", batchMap.keySet());
                        response.put("batch_id", batchMap.get("id"));
                        response.put("batch_sample", batchMap);
                        
                        log.info("✅ Found batch data: ID={}, Keys={}", batchMap.get("id"), batchMap.keySet());
                    } else {
                        log.warn("⚠️ payment_batch is not a Map: {}", paymentBatch);
                    }
                    
                    response.put("sample_payment", firstPayment);
                    
                } else {
                    response.put("message", "No payments found in date range");
                    
                    // Try longer date range
                    LocalDate longerFromDate = LocalDate.now().minusDays(90);
                    String longerUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1/report/all-payments" +
                        "?from_date=" + longerFromDate +
                        "&to_date=" + toDate +
                        "&filter_by=reconciliation_date" +
                        "&rows=1";
                    
                    try {
                        ResponseEntity<Map> longerResponse = restTemplate.exchange(longerUrl, HttpMethod.GET, request, Map.class);
                        List<Map<String, Object>> longerPayments = (List<Map<String, Object>>) longerResponse.getBody().get("items");
                        response.put("longer_range_count", longerPayments != null ? longerPayments.size() : 0);
                        response.put("longer_range", longerFromDate + " to " + toDate);
                        
                        if (longerPayments != null && !longerPayments.isEmpty()) {
                            Map<String, Object> longerSample = longerPayments.get(0);
                            Object longerBatch = longerSample.get("payment_batch");
                            response.put("longer_range_has_batch", longerBatch != null);
                            
                            if (longerBatch instanceof Map) {
                                Map<String, Object> longerBatchMap = (Map<String, Object>) longerBatch;
                                response.put("longer_range_batch_sample", longerBatchMap);
                            }
                        }
                        
                    } catch (Exception e) {
                        response.put("longer_range_error", e.getMessage());
                    }
                }
                
            } else {
                response.put("success", false);
                response.put("http_status", apiResponse.getStatusCode().value());
                response.put("message", "API call failed");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (HttpClientErrorException e) {
            log.error("❌ PayProp API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            response.put("success", false);
            response.put("api_error", e.getResponseBodyAsString());
            response.put("status_code", e.getStatusCode().value());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Batch test failed: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    /**
     * Add this to PayPropOAuth2Controller.java to test batch payments
     */
    @PostMapping("/test-simple-batch-payments")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testSimpleBatchPayments(Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            if (!oAuth2Service.hasValidTokens()) {
                response.put("success", false);
                response.put("message", "PayProp not authorized");
                return ResponseEntity.ok(response);
            }

            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            // Test with last 30 days
            LocalDate fromDate = LocalDate.now().minusDays(30);
            LocalDate toDate = LocalDate.now();
            
            String url = "https://ukapi.staging.payprop.com/api/agency/v1.1/report/all-payments" +
                "?from_date=" + fromDate +
                "&to_date=" + toDate +
                "&filter_by=reconciliation_date" +
                "&include_beneficiary_info=true" +
                "&rows=5";
            
            log.info("🔍 Testing: {}", url);
            
            ResponseEntity<Map> apiResponse = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (apiResponse.getStatusCode().is2xxSuccessful() && apiResponse.getBody() != null) {
                Map<String, Object> responseBody = apiResponse.getBody();
                List<Map<String, Object>> payments = (List<Map<String, Object>>) responseBody.get("items");
                
                response.put("success", true);
                response.put("total_items", payments != null ? payments.size() : 0);
                response.put("date_range", fromDate + " to " + toDate);
                
                if (payments != null && !payments.isEmpty()) {
                    // Check first payment for batch structure
                    Map<String, Object> firstPayment = payments.get(0);
                    log.info("📊 First payment keys: {}", firstPayment.keySet());
                    
                    Object paymentBatch = firstPayment.get("payment_batch");
                    response.put("has_payment_batch_field", paymentBatch != null);
                    response.put("payment_batch_type", paymentBatch != null ? paymentBatch.getClass().getSimpleName() : "null");
                    
                    if (paymentBatch instanceof Map) {
                        Map<String, Object> batchMap = (Map<String, Object>) paymentBatch;
                        response.put("batch_keys", batchMap.keySet());
                        response.put("batch_id", batchMap.get("id"));
                        response.put("batch_sample", batchMap);
                    }
                    
                    response.put("sample_payment", firstPayment);
                    
                    // Check database
                    long currentBatchCount = batchPaymentRepository.count();
                    response.put("current_db_batch_count", currentBatchCount);
                    
                } else {
                    response.put("message", "No payments found in date range");
                    
                    // Try longer date range
                    LocalDate longerFromDate = LocalDate.now().minusDays(90);
                    String longerUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1/report/all-payments" +
                        "?from_date=" + longerFromDate +
                        "&to_date=" + toDate +
                        "&filter_by=reconciliation_date" +
                        "&rows=1";
                    
                    try {
                        ResponseEntity<Map> longerResponse = restTemplate.exchange(longerUrl, HttpMethod.GET, request, Map.class);
                        List<Map<String, Object>> longerPayments = (List<Map<String, Object>>) longerResponse.getBody().get("items");
                        response.put("longer_range_count", longerPayments != null ? longerPayments.size() : 0);
                        response.put("longer_range", longerFromDate + " to " + toDate);
                    } catch (Exception e) {
                        response.put("longer_range_error", e.getMessage());
                    }
                }
                
            } else {
                response.put("success", false);
                response.put("http_status", apiResponse.getStatusCode().value());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Batch test failed: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Test available payment endpoints with current scopes
     */
    @PostMapping("/test-available-payment-endpoints")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testAvailablePaymentEndpoints(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String baseUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1";
            Map<String, Object> results = new HashMap<>();
            
            // Test different payment-related endpoints
            String[] endpoints = {
                "/export/payments",
                "/export/invoices", 
                "/report/icdn",
                "/report/tenant/balances",
                "/report/processing-summary",
                "/export/invoice-instructions"
            };
            
            for (String endpoint : endpoints) {
                try {
                    ResponseEntity<Map> apiResponse = restTemplate.exchange(
                        baseUrl + endpoint + "?page=1&rows=5", 
                        HttpMethod.GET, request, Map.class);
                    
                    results.put(endpoint, Map.of(
                        "status", "✅ WORKS",
                        "statusCode", apiResponse.getStatusCode().value(),
                        "hasData", apiResponse.getBody() != null,
                        "itemCount", getItemCount(apiResponse.getBody())
                    ));
                } catch (HttpClientErrorException e) {
                    results.put(endpoint, Map.of(
                        "status", "❌ " + e.getStatusCode(),
                        "error", e.getResponseBodyAsString()
                    ));
                } catch (Exception e) {
                    results.put(endpoint, Map.of(
                        "status", "❌ ERROR", 
                        "error", e.getMessage()
                    ));
                }
            }
            
            response.put("success", true);
            response.put("endpointTests", results);
            response.put("message", "Endpoint availability test completed");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/test-field-locations")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testFieldLocations(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }
        
        Map<String, Object> results = new HashMap<>();
        
        try {
            // Test 1: Get a property and check field locations
            PayPropSyncService.PayPropExportResult propertyResult = 
                payPropSyncService.exportPropertiesFromPayProp(1, 1);
            
            if (!propertyResult.getItems().isEmpty()) {
                Map<String, Object> property = propertyResult.getItems().get(0);
                
                // Check commission location
                results.put("commission_structure", Map.of(
                    "has_commission_object", property.containsKey("commission"),
                    "has_commission_percentage_field", property.containsKey("commission_percentage"),
                    "commission_value", property.get("commission"),
                    "commission_percentage_value", property.get("commission_percentage")
                ));
                
                // Check monthly payment location
                results.put("monthly_payment_location", Map.of(
                    "has_monthly_payment", property.containsKey("monthly_payment"),
                    "has_monthly_payment_required", property.containsKey("monthly_payment_required"),
                    "monthly_payment_value", property.get("monthly_payment"),
                    "monthly_payment_required_value", property.get("monthly_payment_required")
                ));
                
                // Check if settings exists
                if (property.containsKey("settings")) {
                    Map<String, Object> settings = (Map<String, Object>) property.get("settings");
                    results.put("settings_fields", settings != null ? settings.keySet() : "null");
                }
                
                // Show all root fields
                results.put("all_property_fields", property.keySet());
            }
            
            // Test 2: Test ICDN date ranges
            LocalDate today = LocalDate.now();
            Map<String, String> dateRangeTests = new HashMap<>();
            
            // Test 30 days
            try {
                HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
                HttpEntity<String> request = new HttpEntity<>(headers);
                String url30 = payPropApiBase + "/report/icdn?from_date=" + today.minusDays(30) + "&to_date=" + today + "&rows=1";
                restTemplate.exchange(url30, HttpMethod.GET, request, Map.class);
                dateRangeTests.put("30_days", "✅ SUCCESS");
            } catch (Exception e) {
                dateRangeTests.put("30_days", "❌ FAILED: " + e.getMessage());
            }
            
            // Test 90 days
            try {
                HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
                HttpEntity<String> request = new HttpEntity<>(headers);
                String url90 = payPropApiBase + "/report/icdn?from_date=" + today.minusDays(90) + "&to_date=" + today + "&rows=1";
                restTemplate.exchange(url90, HttpMethod.GET, request, Map.class);
                dateRangeTests.put("90_days", "✅ SUCCESS");
            } catch (Exception e) {
                dateRangeTests.put("90_days", "❌ FAILED: " + e.getMessage());
            }
            
            // Test 120 days (should fail)
            try {
                HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
                HttpEntity<String> request = new HttpEntity<>(headers);
                String url120 = payPropApiBase + "/report/icdn?from_date=" + today.minusDays(120) + "&to_date=" + today + "&rows=1";
                restTemplate.exchange(url120, HttpMethod.GET, request, Map.class);
                dateRangeTests.put("120_days", "✅ SUCCESS (unexpected!)");
            } catch (Exception e) {
                dateRangeTests.put("120_days", "❌ FAILED: " + e.getMessage());
            }
            
            results.put("date_range_tests", dateRangeTests);
            
            // Test 3: Check payment categories endpoint
            Map<String, String> categoryEndpoints = new HashMap<>();
            
            // Try direct categories endpoint
            try {
                HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
                HttpEntity<String> request = new HttpEntity<>(headers);
                String catUrl = payPropApiBase + "/payments/categories";
                ResponseEntity<Map> response = restTemplate.exchange(catUrl, HttpMethod.GET, request, Map.class);
                categoryEndpoints.put("/payments/categories", "✅ EXISTS - Status: " + response.getStatusCode());
            } catch (Exception e) {
                categoryEndpoints.put("/payments/categories", "❌ NOT FOUND: " + e.getMessage());
            }
            
            // Extract from payments
            try {
                PayPropSyncService.PayPropExportResult payments = payPropSyncService.exportPaymentsFromPayProp(1, 5);
                Set<String> categories = new HashSet<>();
                for (Map<String, Object> payment : payments.getItems()) {
                    Object category = payment.get("category");
                    if (category != null) categories.add(category.toString());
                }
                categoryEndpoints.put("categories_from_payments", "✅ Found " + categories.size() + " categories: " + categories);
            } catch (Exception e) {
                categoryEndpoints.put("categories_from_payments", "❌ FAILED: " + e.getMessage());
            }
            
            results.put("category_endpoints", categoryEndpoints);
            
            return ResponseEntity.ok(results);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Test failed: " + e.getMessage(),
                "stackTrace", Arrays.toString(e.getStackTrace())
            ));
        }
    }

    /**
     * Helper method to count items in API response
     */
    private int getItemCount(Map<String, Object> responseBody) {
        if (responseBody == null) return 0;
        
        Object items = responseBody.get("items");
        if (items instanceof java.util.List) {
            return ((java.util.List<?>) items).size();
        }
        
        Object data = responseBody.get("data");
        if (data instanceof java.util.List) {
            return ((java.util.List<?>) data).size();
        }
        
        return 0;
    }

    /**
     * Test API connection with current tokens
     */
    @PostMapping("/test-connection")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testConnection(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            if (!oAuth2Service.hasValidTokens()) {
                response.put("success", false);
                response.put("message", "No valid OAuth2 tokens. Please authorize first.");
                return ResponseEntity.ok(response);
            }

            // Test API call to PayProp
            String accessToken = oAuth2Service.getValidAccessToken();
            
            // You can add a simple API test here, like fetching properties count
            // For now, just verify we have a valid token
            
            response.put("success", true);
            response.put("message", "PayProp API connection successful!");
            response.put("tokenStatus", "Valid");
            
            PayPropTokens tokens = oAuth2Service.getCurrentTokens();
            if (tokens != null) {
                response.put("expiresAt", tokens.getExpiresAt());
                response.put("scopes", tokens.getScopes());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("❌ PayProp API test failed: " + e.getMessage());
            
            response.put("success", false);
            response.put("message", "API test failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Refresh OAuth2 tokens
     */
    @PostMapping("/refresh")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> refreshTokens(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            PayPropTokens tokens = oAuth2Service.refreshToken();
            
            response.put("success", true);
            response.put("message", "Tokens refreshed successfully");
            response.put("expiresAt", tokens.getExpiresAt());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to refresh tokens: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Clear stored tokens (logout)
     */
    @PostMapping("/disconnect")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> disconnect(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        oAuth2Service.clearTokens();
        
        response.put("success", true);
        response.put("message", "PayProp integration disconnected");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get current token status
     */
    @GetMapping("/token-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTokenStatus(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        PayPropTokens tokens = oAuth2Service.getCurrentTokens();
        
        response.put("hasTokens", tokens != null);
        response.put("isValid", oAuth2Service.hasValidTokens());
        
        if (tokens != null) {
            response.put("expiresAt", tokens.getExpiresAt());
            response.put("isExpired", tokens.isExpired());
            response.put("isExpiringSoon", tokens.isExpiringSoon());
            response.put("scopes", tokens.getScopes());
            response.put("obtainedAt", tokens.getObtainedAt());
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Simple OAuth test endpoint for JavaScript testing
     */
    @GetMapping("/test-oauth")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testOAuth(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Check authorization
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                response.put("success", false);
                response.put("message", "Access denied - MANAGER role required");
                response.put("tokenValid", false);
                return ResponseEntity.status(403).body(response);
            }

            // Check if we have tokens
            PayPropTokens tokens = oAuth2Service.getCurrentTokens();
            boolean hasTokens = tokens != null;
            boolean isValid = oAuth2Service.hasValidTokens();
            
            response.put("success", true);
            response.put("hasTokens", hasTokens);
            response.put("tokenValid", isValid);
            
            if (hasTokens) {
                response.put("tokenType", tokens.getTokenType());
                response.put("scopes", tokens.getScopes());
                response.put("expiresAt", tokens.getExpiresAt());
                response.put("isExpired", tokens.isExpired());
                response.put("isExpiringSoon", tokens.isExpiringSoon());
                
                if (isValid) {
                    response.put("message", "OAuth2 tokens are valid and ready for use");
                    
                    // Test a simple API call
                    try {
                        String accessToken = oAuth2Service.getValidAccessToken();
                        response.put("accessTokenLength", accessToken.length());
                        response.put("apiCallTest", "Token retrieved successfully");
                    } catch (Exception e) {
                        response.put("apiCallTest", "Failed to get access token: " + e.getMessage());
                    }
                } else {
                    response.put("message", "OAuth2 tokens exist but are invalid/expired");
                }
            } else {
                response.put("message", "No OAuth2 tokens found - authorization required");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "OAuth test failed: " + e.getMessage());
            response.put("tokenValid", false);
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/test-financial-data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testFinancialData(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        Map<String, Object> results = new HashMap<>();
        
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            String baseUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1";
            
            // Test 1: Export Payments (actual transactions?)
            String paymentsUrl = baseUrl + "/export/payments?rows=5&include_beneficiary_info=true";
            ResponseEntity<Map> paymentsResponse = restTemplate.exchange(paymentsUrl, HttpMethod.GET, request, Map.class);
            results.put("export_payments", Map.of(
                "status", "SUCCESS",
                "count", getItemCount(paymentsResponse.getBody()),
                "sample_data", getSampleData(paymentsResponse.getBody(), 2)
            ));
            
            // Test 2: Export Invoices
            String invoicesUrl = baseUrl + "/export/invoices?rows=5";
            ResponseEntity<Map> invoicesResponse = restTemplate.exchange(invoicesUrl, HttpMethod.GET, request, Map.class);
            results.put("export_invoices", Map.of(
                "status", "SUCCESS", 
                "count", getItemCount(invoicesResponse.getBody()),
                "sample_data", getSampleData(invoicesResponse.getBody(), 2)
            ));
            
            // Test 3: ICDN Report (financial transactions)
            String icdnUrl = baseUrl + "/report/icdn?rows=5&from_date=2024-01-01";
            ResponseEntity<Map> icdnResponse = restTemplate.exchange(icdnUrl, HttpMethod.GET, request, Map.class);
            results.put("report_icdn", Map.of(
                "status", "SUCCESS",
                "count", getItemCount(icdnResponse.getBody()),
                "sample_data", getSampleData(icdnResponse.getBody(), 2)
            ));
            
            // Test 4: Specific property payments (using your most active property)
            String propPaymentsUrl = baseUrl + "/export/payments?property_id=116&rows=5&include_beneficiary_info=true";
            ResponseEntity<Map> propResponse = restTemplate.exchange(propPaymentsUrl, HttpMethod.GET, request, Map.class);
            results.put("property_116_payments", Map.of(
                "status", "SUCCESS",
                "property_name", "Havelock Place 87, Hartley",
                "count", getItemCount(propResponse.getBody()),
                "sample_data", getSampleData(propResponse.getBody(), 2)
            ));
            
        } catch (Exception e) {
            results.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(results);
    }

    @PostMapping("/test-icdn-financial-types")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testICDNFinancialTypes(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        Map<String, Object> results = new HashMap<>();
        
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            String baseUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1/report/icdn";
            
            // Test different transaction types
            String[] types = {"invoice", "credit_note", "debit_note"};
            
            for (String type : types) {
                String url = baseUrl + "?type=" + type + "&rows=10&from_date=2024-01-01";
                
                try {
                    ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
                    List<?> items = (List<?>) response.getBody().get("items");
                    
                    results.put(type, Map.of(
                        "count", items.size(),
                        "sample_data", items.stream().limit(3).collect(Collectors.toList())
                    ));
                } catch (Exception e) {
                    results.put(type, Map.of("error", e.getMessage()));
                }
            }
            
            // Test date range summary
            String summaryUrl = baseUrl + "?from_date=2024-01-01&to_date=2024-12-31&rows=100";
            ResponseEntity<Map> summaryResponse = restTemplate.exchange(summaryUrl, HttpMethod.GET, request, Map.class);
            List<?> allItems = (List<?>) summaryResponse.getBody().get("items");
            
            // Calculate totals by type
            Map<String, Double> totals = new HashMap<>();
            for (Object item : allItems) {
                Map<String, Object> transaction = (Map<String, Object>) item;
                String transactionType = (String) transaction.get("type");
                Double amount = Double.parseDouble((String) transaction.get("amount"));
                totals.put(transactionType, totals.getOrDefault(transactionType, 0.0) + amount);
            }
            
            results.put("summary_2024", Map.of(
                "total_transactions", allItems.size(),
                "totals_by_type", totals
            ));
            
        } catch (Exception e) {
            results.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(results);
    }

    @PostMapping("/test-enhanced-payment-data")
    @ResponseBody  
    public ResponseEntity<Map<String, Object>> testEnhancedPaymentData(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        Map<String, Object> results = new HashMap<>();
        
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            String baseUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1";
            
            // Test 1: Enhanced payments export with more parameters
            String enhancedPaymentsUrl = baseUrl + "/export/payments?include_beneficiary_info=true&rows=10&modified_from_time=2024-01-01T00:00:00";
            ResponseEntity<Map> enhancedPayments = restTemplate.exchange(enhancedPaymentsUrl, HttpMethod.GET, request, Map.class);
            results.put("enhanced_payments", Map.of(
                "count", getItemCount(enhancedPayments.getBody()),
                "sample_data", getSampleData(enhancedPayments.getBody(), 3)
            ));
            
            // Test 2: Properties with commission data
            String propertiesCommissionUrl = baseUrl + "/export/properties?include_commission=true&rows=5";
            ResponseEntity<Map> propertiesCommission = restTemplate.exchange(propertiesCommissionUrl, HttpMethod.GET, request, Map.class);
            results.put("properties_commission", Map.of(
                "count", getItemCount(propertiesCommission.getBody()),
                "sample_data", getSampleData(propertiesCommission.getBody(), 2)
            ));
            
            // Test 3: Beneficiaries export (owners who receive payments)
            String beneficiariesUrl = baseUrl + "/export/beneficiaries?owners=true&rows=5";
            ResponseEntity<Map> beneficiaries = restTemplate.exchange(beneficiariesUrl, HttpMethod.GET, request, Map.class);
            results.put("owner_beneficiaries", Map.of(
                "count", getItemCount(beneficiaries.getBody()),
                "sample_data", getSampleData(beneficiaries.getBody(), 2)
            ));
            
            // Test 4: ICDN with more recent data and larger range (within 93 days)
            String recentICDNUrl = baseUrl + "/report/icdn?from_date=2024-06-01&to_date=2024-08-31&rows=20";
            ResponseEntity<Map> recentICDN = restTemplate.exchange(recentICDNUrl, HttpMethod.GET, request, Map.class);
            results.put("recent_icdn", Map.of(
                "count", getItemCount(recentICDN.getBody()),
                "sample_data", getSampleData(recentICDN.getBody(), 3)
            ));
            
        } catch (Exception e) {
            results.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(results);
    }

    @PostMapping("/test-agency-income")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testAgencyIncome(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        Map<String, Object> results = new HashMap<>();
        
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            String baseUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1";
            
            // Test agency income report (commission/fees)
            String agencyIncomeUrl = baseUrl + "/report/agency/income?year=2024&month=01";
            ResponseEntity<Map> agencyResponse = restTemplate.exchange(agencyIncomeUrl, HttpMethod.GET, request, Map.class);
            results.put("agency_income", agencyResponse.getBody());
            
            // Test enhanced properties export with commission
            String propertiesUrl = baseUrl + "/export/properties?include_commission=true&rows=3";
            ResponseEntity<Map> propertiesResponse = restTemplate.exchange(propertiesUrl, HttpMethod.GET, request, Map.class);
            results.put("properties_with_commission", propertiesResponse.getBody());
            
        } catch (Exception e) {
            results.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(results);
    }

    private Object getSampleData(Map<String, Object> response, int maxItems) {
        if (response != null && response.containsKey("items")) {
            List<?> items = (List<?>) response.get("items");
            return items.stream().limit(maxItems).collect(Collectors.toList());
        }
        return "No data";
    }

    /**
     * Test endpoint for tags functionality
     */
    @GetMapping("/test-tags")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testTags(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            response.put("success", false);
            response.put("message", "Access denied");
            return ResponseEntity.status(403).body(response);
        }

        try {
            if (!oAuth2Service.hasValidTokens()) {
                response.put("success", false);
                response.put("message", "No valid OAuth2 tokens. Please authorize first.");
                return ResponseEntity.ok(response);
            }

            // Note: Your system uses PayPropPortfolioSyncService for tag operations
            // This is just a test endpoint to verify the OAuth connection for tags
            
            response.put("success", true);
            response.put("message", "OAuth ready for tag operations");
            response.put("note", "Tag operations are handled via PayPropPortfolioSyncService");
            response.put("availableOperations", Arrays.asList(
                "getAllPayPropTags()",
                "createPayPropTag()",
                "syncPortfolioToPayProp()",
                "handlePayPropTagChange()"
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Tag test failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    // ===== NEW FINANCIAL SYNC ENDPOINTS =====

    /**
     * Comprehensive financial data sync - syncs all financial data from PayProp
     */
    @PostMapping("/sync-comprehensive-financial-data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncComprehensiveFinancialData(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        try {
            Map<String, Object> syncResults = payPropFinancialSyncService.syncComprehensiveFinancialData();
            return ResponseEntity.ok(syncResults);
            
        } catch (Exception e) {
            logger.error("❌ Comprehensive financial sync failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "FAILED",
                "error", e.getMessage()
            ));
        }
    }

    /**
     * NEW: Sync dual financial data (instructions vs actuals)
     */
    @PostMapping("/sync-dual-financial-data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncDualFinancialData(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        try {
            Map<String, Object> syncResults = payPropFinancialSyncService.syncDualFinancialData();
            return ResponseEntity.ok(syncResults);
            
        } catch (Exception e) {
            logger.error("❌ Dual financial sync failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "FAILED",
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Get financial summary from stored local data
     */
    @GetMapping("/financial-summary")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getFinancialSummary(
        @RequestParam(required = false) String propertyId,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
        Authentication authentication
    ) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        try {
            Map<String, Object> summary = payPropFinancialSyncService.getStoredFinancialSummary(
                propertyId, fromDate, toDate);
            return ResponseEntity.ok(summary);
            
        } catch (Exception e) {
            logger.error("❌ Financial summary failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "ERROR",
                "error", e.getMessage()
            ));
        }
    }

    /**
     * NEW: Get dashboard financial comparison (instructions vs actuals)
     */
    @GetMapping("/dashboard-financial-comparison")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDashboardFinancialComparison(
        @RequestParam(required = false) String propertyId,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
        Authentication authentication
    ) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        try {
            // Default to last 30 days if no dates provided
            if (fromDate == null) fromDate = LocalDate.now().minusDays(30);
            if (toDate == null) toDate = LocalDate.now();
            
            Map<String, Object> comparison = payPropFinancialSyncService.getDashboardFinancialComparison(
                propertyId, fromDate, toDate);
            return ResponseEntity.ok(comparison);
            
        } catch (Exception e) {
            logger.error("❌ Dashboard financial comparison failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "ERROR",
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Test financial sync on a small dataset
     */
    @PostMapping("/test-financial-sync")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testFinancialSync(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        try {
            Map<String, Object> testResults = new HashMap<>();
            
            // Test 1: Check OAuth connection
            testResults.put("oauth_status", oAuth2Service.hasValidTokens() ? "VALID" : "INVALID");
            
            // Test 2: Check existing database counts
            long totalProperties = propertyService.findAll().size();
            long totalBeneficiaries = beneficiaryRepository.count();
            long totalPayments = paymentRepository.count();
            long totalCategories = paymentCategoryRepository.count();
            
            testResults.put("database_status", Map.of(
                "total_properties", totalProperties,
                "total_beneficiaries", totalBeneficiaries,
                "total_payments", totalPayments,
                "total_categories", totalCategories
            ));
            
            // Test 3: Test PayProp API access with small data
            if (oAuth2Service.hasValidTokens()) {
                HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
                HttpEntity<String> request = new HttpEntity<>(headers);
                
                // Test properties endpoint
                String propertiesUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1/export/properties?include_commission=true&rows=2";
                ResponseEntity<Map> propertiesResponse = restTemplate.exchange(propertiesUrl, HttpMethod.GET, request, Map.class);
                
                List<Map<String, Object>> properties = (List<Map<String, Object>>) propertiesResponse.getBody().get("items");
                testResults.put("payprop_properties_test", Map.of(
                    "count", properties.size(),
                    "status", "SUCCESS"
                ));
                
                // Test ICDN endpoint
                LocalDate testDate = LocalDate.now().minusDays(30);
                String icdnUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1/report/icdn?from_date=" + testDate + "&rows=2";
                ResponseEntity<Map> icdnResponse = restTemplate.exchange(icdnUrl, HttpMethod.GET, request, Map.class);
                
                List<Map<String, Object>> transactions = (List<Map<String, Object>>) icdnResponse.getBody().get("items");
                testResults.put("payprop_transactions_test", Map.of(
                    "count", transactions.size(),
                    "status", "SUCCESS"
                ));
            } else {
                testResults.put("payprop_api_test", "SKIPPED - No valid OAuth tokens");
            }
            
            testResults.put("status", "SUCCESS");
            testResults.put("test_time", LocalDateTime.now());
            
            return ResponseEntity.ok(testResults);
            
        } catch (Exception e) {
            logger.error("❌ Financial sync test failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "FAILED",
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Get financial sync status and statistics
     */
    @GetMapping("/financial-sync-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getFinancialSyncStatus(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        try {
            Map<String, Object> status = new HashMap<>();
            
            // Check database counts using existing repositories
            long totalProperties = propertyService.findAll().size();
            long totalBeneficiaries = beneficiaryRepository.count();
            long totalPayments = paymentRepository.count();
            long totalCategories = paymentCategoryRepository.count();
            
            // Count PayProp synced entities
            long payPropProperties = propertyRepository.findByPayPropIdIsNotNull().size();
            long payPropBeneficiaries = beneficiaryRepository.findByPayPropBeneficiaryIdIsNotNull().size();
            long payPropPayments = paymentRepository.findByPayPropPaymentIdIsNotNull().size();
            long payPropCategories = paymentCategoryRepository.findByPayPropCategoryIdIsNotNull().size();
            
            status.put("database_status", Map.of(
                "total_properties", totalProperties,
                "payprop_synced_properties", payPropProperties,
                "property_sync_coverage", totalProperties > 0 ? (payPropProperties * 100.0 / totalProperties) : 0,
                "total_beneficiaries", totalBeneficiaries,
                "payprop_synced_beneficiaries", payPropBeneficiaries,
                "total_payments", totalPayments,
                "payprop_synced_payments", payPropPayments,
                "total_categories", totalCategories,
                "payprop_synced_categories", payPropCategories
            ));
            
            // Check recent payment activity (last 30 days)
            LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
            List<Payment> recentPayments = paymentRepository.findByPaymentDateBetween(thirtyDaysAgo, LocalDate.now());
            
            status.put("recent_activity", Map.of(
                "payments_last_30_days", recentPayments.size(),
                "date_range", thirtyDaysAgo + " to " + LocalDate.now()
            ));
            
            // Check OAuth status
            status.put("oauth_status", Map.of(
                "has_valid_tokens", oAuth2Service.hasValidTokens(),
                "ready_for_sync", oAuth2Service.hasValidTokens()
            ));
            
            status.put("status", "SUCCESS");
            status.put("checked_at", LocalDateTime.now());
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            logger.error("❌ Financial sync status check failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "ERROR",
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Quick test of PayProp financial data access
     */
    @PostMapping("/test-payprop-financial-access")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testPayPropFinancialAccess(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        try {
            Map<String, Object> testResults = new HashMap<>();
            
            if (!oAuth2Service.hasValidTokens()) {
                return ResponseEntity.ok(Map.of(
                    "status", "NO_TOKENS",
                    "message", "Please authorize PayProp first"
                ));
            }
            
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            String baseUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1";
            
            // Test 1: Properties with commission
            String propertiesUrl = baseUrl + "/export/properties?include_commission=true&rows=3";
            ResponseEntity<Map> propertiesResponse = restTemplate.exchange(propertiesUrl, HttpMethod.GET, request, Map.class);
            List<Map<String, Object>> properties = (List<Map<String, Object>>) propertiesResponse.getBody().get("items");
            
            testResults.put("properties_with_commission", Map.of(
                "count", properties.size(),
                "sample", properties.stream().limit(1).collect(Collectors.toList())
            ));
            
            // Test 2: Owner beneficiaries
            String beneficiariesUrl = baseUrl + "/export/beneficiaries?owners=true&rows=3";
            ResponseEntity<Map> beneficiariesResponse = restTemplate.exchange(beneficiariesUrl, HttpMethod.GET, request, Map.class);
            List<Map<String, Object>> beneficiaries = (List<Map<String, Object>>) beneficiariesResponse.getBody().get("items");
            
            testResults.put("owner_beneficiaries", Map.of(
                "count", beneficiaries.size(),
                "sample", beneficiaries.stream().limit(1).collect(Collectors.toList())
            ));
            
            // Test 3: Financial transactions (ICDN)
            LocalDate fromDate = LocalDate.now().minusDays(30);
            String icdnUrl = baseUrl + "/report/icdn?from_date=" + fromDate + "&rows=5";
            ResponseEntity<Map> icdnResponse = restTemplate.exchange(icdnUrl, HttpMethod.GET, request, Map.class);
            List<Map<String, Object>> transactions = (List<Map<String, Object>>) icdnResponse.getBody().get("items");
            
            testResults.put("financial_transactions", Map.of(
                "count", transactions.size(),
                "sample", transactions.stream().limit(1).collect(Collectors.toList())
            ));
            
            testResults.put("status", "SUCCESS");
            testResults.put("message", "PayProp financial data access working correctly");
            
            return ResponseEntity.ok(testResults);
            
        } catch (Exception e) {
            logger.error("❌ PayProp financial access test failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "FAILED",
                "error", e.getMessage()
            ));
        }
    }


    /**
     * 🔍 DIAGNOSTIC: Test different PayProp endpoints to find correct data source
     * FIXED: Automatically finds valid property ID
     */
    @PostMapping("/diagnose-payment-data-sources")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> diagnosePaymentDataSources(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        Map<String, Object> diagnosis = new HashMap<>();
        
        try {
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            String baseUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1";
            
            // STEP 1: Find a valid property ID from your database
            String testPropertyId = null;
            try {
                List<Property> properties = propertyService.findAll().stream()
                    .filter(p -> p.getPayPropId() != null)
                    .limit(1)
                    .collect(Collectors.toList());
                
                if (!properties.isEmpty()) {
                    testPropertyId = properties.get(0).getPayPropId();
                    diagnosis.put("test_property_info", Map.of(
                        "property_id", testPropertyId,
                        "property_name", properties.get(0).getPropertyName(),
                        "source", "Local database"
                    ));
                }
            } catch (Exception e) {
                logger.warn("Could not get property from database: {}", e.getMessage());
            }
            
            // STEP 2: If no property in database, get one from PayProp
            if (testPropertyId == null) {
                try {
                    String propertiesUrl = baseUrl + "/export/properties?rows=1";
                    ResponseEntity<Map> propResponse = restTemplate.exchange(propertiesUrl, HttpMethod.GET, request, Map.class);
                    List<Map<String, Object>> properties = (List<Map<String, Object>>) propResponse.getBody().get("items");
                    
                    if (!properties.isEmpty()) {
                        testPropertyId = (String) properties.get(0).get("id");
                        diagnosis.put("test_property_info", Map.of(
                            "property_id", testPropertyId,
                            "property_name", properties.get(0).get("property_name"),
                            "source", "PayProp API"
                        ));
                    }
                } catch (Exception e) {
                    logger.warn("Could not get property from PayProp: {}", e.getMessage());
                }
            }
            
            if (testPropertyId == null) {
                diagnosis.put("error", "Could not find any valid property ID to test with");
                return ResponseEntity.ok(diagnosis);
            }
            
            logger.info("Using test property ID: {} for diagnostics", testPropertyId);
            
            // 1. TEST: Payment Instructions Export (general)
            String paymentsUrl = baseUrl + "/export/payments?rows=10&include_beneficiary_info=true";
            ResponseEntity<Map> paymentsResponse = restTemplate.exchange(paymentsUrl, HttpMethod.GET, request, Map.class);
            
            diagnosis.put("1_payment_instructions", Map.of(
                "endpoint", "/export/payments",
                "description", "Payment instructions (what SHOULD be paid)",
                "count", getItemCount(paymentsResponse.getBody()),
                "sample_data", getSampleData(paymentsResponse.getBody(), 3)
            ));
            
            // 2. TEST: Reconciled Payments (filtered by reconciliation date)
            String reconciledUrl = baseUrl + "/export/payments?filter_by=reconciliation_date&rows=10&include_beneficiary_info=true";
            try {
                ResponseEntity<Map> reconciledResponse = restTemplate.exchange(reconciledUrl, HttpMethod.GET, request, Map.class);
                
                diagnosis.put("2_reconciled_payments", Map.of(
                    "endpoint", "/export/payments?filter_by=reconciliation_date",
                    "description", "ACTUAL reconciled payments (what WAS paid)",
                    "count", getItemCount(reconciledResponse.getBody()),
                    "sample_data", getSampleData(reconciledResponse.getBody(), 3)
                ));
            } catch (Exception e) {
                diagnosis.put("2_reconciled_payments", Map.of("error", "Endpoint failed: " + e.getMessage()));
            }
            
            // 3. TEST: ICDN Report (financial transactions)
            LocalDate fromDate = LocalDate.now().minusMonths(6);
            String icdnUrl = baseUrl + "/report/icdn?from_date=" + fromDate + "&rows=10";
            try {
                ResponseEntity<Map> icdnResponse = restTemplate.exchange(icdnUrl, HttpMethod.GET, request, Map.class);
                
                diagnosis.put("3_icdn_transactions", Map.of(
                    "endpoint", "/report/icdn",
                    "description", "Financial transaction records",
                    "count", getItemCount(icdnResponse.getBody()),
                    "sample_data", getSampleData(icdnResponse.getBody(), 3)
                ));
            } catch (Exception e) {
                diagnosis.put("3_icdn_transactions", Map.of("error", "Endpoint failed: " + e.getMessage()));
            }
            
            // 4. TEST: Property-specific payments (using valid property ID)
            String propPaymentsUrl = baseUrl + "/export/payments?property_id=" + testPropertyId + "&rows=10&include_beneficiary_info=true";
            try {
                ResponseEntity<Map> propResponse = restTemplate.exchange(propPaymentsUrl, HttpMethod.GET, request, Map.class);
                diagnosis.put("4_property_specific_payments", Map.of(
                    "endpoint", "/export/payments?property_id=" + testPropertyId,
                    "description", "Property-specific payment instructions",
                    "count", getItemCount(propResponse.getBody()),
                    "sample_data", getSampleData(propResponse.getBody(), 3)
                ));
            } catch (Exception e) {
                diagnosis.put("4_property_specific_payments", Map.of("error", "Endpoint failed: " + e.getMessage()));
            }
            
            // 5. TEST: Property-specific reconciled payments
            String propReconciledUrl = baseUrl + "/export/payments?property_id=" + testPropertyId + "&filter_by=reconciliation_date&rows=10&include_beneficiary_info=true";
            try {
                ResponseEntity<Map> propReconResponse = restTemplate.exchange(propReconciledUrl, HttpMethod.GET, request, Map.class);
                diagnosis.put("5_property_reconciled_payments", Map.of(
                    "endpoint", "/export/payments?property_id=" + testPropertyId + "&filter_by=reconciliation_date",
                    "description", "Property-specific ACTUAL reconciled payments",
                    "count", getItemCount(propReconResponse.getBody()),
                    "sample_data", getSampleData(propReconResponse.getBody(), 3)
                ));
            } catch (Exception e) {
                diagnosis.put("5_property_reconciled_payments", Map.of("error", "Endpoint failed: " + e.getMessage()));
            }
            
            diagnosis.put("analysis", Map.of(
                "recommendation", "Compare amounts and dates between endpoints 1 vs 2 and 4 vs 5",
                "look_for", "Endpoint with correct amounts (e.g., £1,100.00 vs £1,075.00) and dates",
                "test_property_id", testPropertyId
            ));
            
        } catch (Exception e) {
            diagnosis.put("error", e.getMessage());
            logger.error("Diagnostic failed: {}", e.getMessage(), e);
        }
        
        return ResponseEntity.ok(diagnosis);
    }

    /**
     * Check current PayProp OAuth2 scopes and token status
     */
    @GetMapping("/payprop/check-scopes")
    public ResponseEntity<?> checkPayPropScopes() {
        if (oAuth2Service.hasValidTokens()) {
            PayPropOAuth2Service.PayPropTokens tokens = oAuth2Service.getCurrentTokens();
            return ResponseEntity.ok(Map.of(
                "scopes", tokens.getScopes(),
                "expiresAt", tokens.getExpiresAt()
            ));
        }
        return ResponseEntity.ok("No valid tokens");
    }
}
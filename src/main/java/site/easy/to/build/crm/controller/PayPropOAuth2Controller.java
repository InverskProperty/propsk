// PayPropOAuth2Controller.java - Enhanced Testing & Investigation Dashboard - FIXED VERSION
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
import site.easy.to.build.crm.service.payprop.PayPropRealTimeSyncService;
import site.easy.to.build.crm.service.payprop.PayPropSyncMonitoringService;
import site.easy.to.build.crm.service.ticket.TicketService;
import site.easy.to.build.crm.entity.Ticket;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.repository.BatchPaymentRepository;
import site.easy.to.build.crm.repository.BeneficiaryRepository;
import site.easy.to.build.crm.repository.FinancialTransactionRepository;
import site.easy.to.build.crm.util.AuthorizationUtil;
import site.easy.to.build.crm.repository.PaymentRepository;
import site.easy.to.build.crm.repository.PaymentCategoryRepository;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.entity.Payment;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.FinancialTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import site.easy.to.build.crm.util.AuthenticationUtils;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

// Update your class to include AuthenticationUtils:
@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Controller
@RequestMapping("/api/payprop/oauth")
public class PayPropOAuth2Controller {

    private static final Logger logger = LoggerFactory.getLogger(PayPropOAuth2Controller.class);
    
    private final PayPropOAuth2Service oAuth2Service;
    private final RestTemplate restTemplate;
    private final AuthenticationUtils authenticationUtils;  // ADD THIS
    private final String payPropApiBase = "https://ukapi.staging.payprop.com/api/agency/v1.1";
    
    // Services
    @Autowired
    private PayPropFinancialSyncService payPropFinancialSyncService;
    @Autowired
    private PayPropSyncService payPropSyncService;
    @Autowired(required = false)
    @SuppressWarnings("unused")
    private PayPropRealTimeSyncService realTimeSyncService;
    @Autowired(required = false)
    private PayPropSyncMonitoringService payPropSyncMonitoringService;
    @Autowired
    @SuppressWarnings("unused")
    private TicketService ticketService;
    
    // Repositories
    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private BeneficiaryRepository beneficiaryRepository;
    @Autowired
    @SuppressWarnings("unused")
    private PaymentRepository paymentRepository;
    @Autowired
    private PaymentCategoryRepository paymentCategoryRepository;
    @Autowired
    private BatchPaymentRepository batchPaymentRepository;
    @Autowired
    @SuppressWarnings("unused")
    private PropertyService propertyService;
    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    // UPDATE YOUR CONSTRUCTOR to include AuthenticationUtils:
    @Autowired
    public PayPropOAuth2Controller(PayPropOAuth2Service oAuth2Service, 
                                  RestTemplate restTemplate,
                                  AuthenticationUtils authenticationUtils) {  // ADD THIS PARAMETER
        this.oAuth2Service = oAuth2Service;
        this.restTemplate = restTemplate;
        this.authenticationUtils = authenticationUtils;  // ADD THIS
    }

    // ===== BASIC OAUTH FLOW (Unchanged) =====
    
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

    @GetMapping("/authorize")
    public String initiateAuthorization(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return "redirect:/access-denied";
        }

        String state = UUID.randomUUID().toString();
        String authorizationUrl = oAuth2Service.getAuthorizationUrl(state);
        logger.info("🔐 Redirecting to PayProp authorization: {}", authorizationUrl);
        return "redirect:" + authorizationUrl;
    }

    @GetMapping("/callback")
    public String handleCallback(@RequestParam(required = false) String code,
                                @RequestParam(required = false) String error,
                                @RequestParam(required = false) String error_description,
                                @RequestParam(required = false) String state,
                                Model model,
                                RedirectAttributes redirectAttributes,
                                Authentication authentication) {  // ADD Authentication parameter
        
        logger.info("📞 PayProp OAuth2 callback received - Code: {}, Error: {}", 
            code != null ? code.substring(0, Math.min(20, code.length())) + "..." : "null", error);

        if (error != null) {
            logger.error("❌ OAuth2 authorization failed: {}", error);
            redirectAttributes.addFlashAttribute("error", 
                "PayProp authorization failed: " + (error_description != null ? error_description : error));
            return "redirect:/api/payprop/oauth/status";
        }

        if (code == null) {
            logger.error("❌ No authorization code received");
            redirectAttributes.addFlashAttribute("error", "No authorization code received from PayProp");
            return "redirect:/api/payprop/oauth/status";
        }

        try {
            // Get the current user ID using AuthenticationUtils
            Long userId = 1L; // Default to system user
            
            if (authentication != null && authentication.isAuthenticated()) {
                try {
                    int userIdInt = authenticationUtils.getLoggedInUserId(authentication);
                    logger.info("Got user ID from authentication: {}", userIdInt);
                    
                    if (userIdInt > 0) {
                        userId = (long) userIdInt;
                    } else {
                        logger.warn("AuthenticationUtils returned invalid user ID: {}, using default", userIdInt);
                    }
                } catch (Exception e) {
                    logger.warn("Could not get user ID from authentication, using default: {}", e.getMessage());
                }
            } else {
                logger.info("No authentication available, using default system user ID");
            }
            
            logger.info("Exchanging PayProp authorization code for user ID: {}", userId);
            
            // Now call with both parameters
            PayPropOAuth2Service.PayPropTokens tokens = oAuth2Service.exchangeCodeForToken(code, userId);
            
            logger.info("✅ PayProp OAuth2 setup completed successfully for user ID: {}", userId);
            logger.info("   Access token obtained: {}", tokens.getAccessToken() != null);
            logger.info("   Refresh token obtained: {}", tokens.getRefreshToken() != null);
            logger.info("   Token expires at: {}", tokens.getExpiresAt());
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "PayProp integration authorized successfully! You can now sync data with PayProp.");
            return "redirect:/api/payprop/oauth/status";
            
        } catch (Exception e) {
            logger.error("❌ Failed to exchange authorization code: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", 
                "Failed to complete PayProp authorization: " + e.getMessage());
            return "redirect:/api/payprop/oauth/status";
        }
    }

    // ===== ENHANCED TESTING ENDPOINTS =====

    /**
     * 🔍 PAYMENT INVESTIGATION - Find specific payments with flexible search
     */
    @PostMapping("/investigate-payment")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> investigatePayment(
            @RequestParam(required = false) String amount,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
            @RequestParam(required = false) String propertyId,
            @RequestParam(required = false) String beneficiaryName,
            @RequestParam(required = false) String batchId,
            @RequestParam(required = false) String transactionId,
            Authentication authentication) {
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        Map<String, Object> investigation = new HashMap<>();
        
        try {
            // Default date range if not provided
            if (fromDate == null) fromDate = LocalDate.now().minusDays(30);
            if (toDate == null) toDate = LocalDate.now();
            
            investigation.put("search_criteria", Map.of(
                "amount", amount != null ? amount : "Any",
                "date_range", fromDate + " to " + toDate,
                "property_id", propertyId != null ? propertyId : "Any",
                "beneficiary_name", beneficiaryName != null ? beneficiaryName : "Any",
                "batch_id", batchId != null ? batchId : "Any",
                "transaction_id", transactionId != null ? transactionId : "Any"
            ));
            
            // 1. Search in multiple PayProp endpoints
            Map<String, Object> apiResults = searchPayPropEndpoints(amount, fromDate, toDate, propertyId, beneficiaryName, batchId, transactionId);
            investigation.put("payprop_api_results", apiResults);
            
            // 2. Search in local database
            Map<String, Object> databaseResults = searchLocalDatabase(amount, fromDate, toDate, propertyId, beneficiaryName, batchId, transactionId);
            investigation.put("local_database_results", databaseResults);
            
            // 3. Compare and identify discrepancies
            Map<String, Object> comparison = compareApiVsDatabase(apiResults, databaseResults);
            investigation.put("comparison_analysis", comparison);
            
            investigation.put("status", "SUCCESS");
            investigation.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(investigation);
            
        } catch (Exception e) {
            logger.error("❌ Payment investigation failed: {}", e.getMessage(), e);
            investigation.put("status", "ERROR");
            investigation.put("error", e.getMessage());
            return ResponseEntity.ok(investigation);
        }
    }

    /**
     * 📊 ENDPOINT COMPARISON - Test multiple endpoints with same criteria
     */
    @PostMapping("/compare-endpoints")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> compareEndpoints(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
            @RequestParam(required = false) String propertyId,
            @RequestParam(required = false) String filterBy,
            @RequestParam(required = false, defaultValue = "10") int maxRows,
            Authentication authentication) {
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        Map<String, Object> comparison = new HashMap<>();
        
        try {
            if (fromDate == null) fromDate = LocalDate.now().minusDays(30);
            if (toDate == null) toDate = LocalDate.now();
            
            comparison.put("test_parameters", Map.of(
                "from_date", fromDate,
                "to_date", toDate,
                "property_id", propertyId != null ? propertyId : "ALL",
                "filter_by", filterBy != null ? filterBy : "DEFAULT",
                "max_rows", maxRows
            ));
            
            HttpHeaders headers = createAuthorizedHeadersSafe();
            
            // Test different endpoint combinations
            Map<String, Map<String, Object>> endpointResults = new HashMap<>();
            
            // 1. Export Payments (Payment Instructions)
            endpointResults.put("export_payments_instructions", 
                testSingleEndpoint("/export/payments", fromDate, toDate, propertyId, null, maxRows, headers));
            
            // 2. Export Payments with Reconciliation Filter
            endpointResults.put("export_payments_reconciled", 
                testSingleEndpoint("/export/payments", fromDate, toDate, propertyId, "reconciliation_date", maxRows, headers));
            
            // 3. Report All-Payments (Actual Payments)
            endpointResults.put("report_all_payments", 
                testSingleEndpoint("/report/all-payments", fromDate, toDate, propertyId, "reconciliation_date", maxRows, headers));
            
            // 4. ICDN Transactions
            endpointResults.put("report_icdn", 
                testSingleEndpoint("/report/icdn", fromDate, toDate, propertyId, null, maxRows, headers));
            
            // 5. Export Invoices
            endpointResults.put("export_invoices", 
                testSingleEndpoint("/export/invoices", fromDate, toDate, propertyId, null, maxRows, headers));
            
            comparison.put("endpoint_results", endpointResults);
            
            // Analyze results
            Map<String, Object> analysis = analyzeEndpointComparison(endpointResults);
            comparison.put("analysis", analysis);
            
            comparison.put("status", "SUCCESS");
            
            return ResponseEntity.ok(comparison);
            
        } catch (Exception e) {
            logger.error("❌ Endpoint comparison failed: {}", e.getMessage(), e);
            comparison.put("status", "ERROR");
            comparison.put("error", e.getMessage());
            return ResponseEntity.ok(comparison);
        }
    }

    /**
     * 🔬 DETAILED TRANSACTION ANALYSIS - Deep dive into specific transaction
     */
    @PostMapping("/analyze-transaction")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> analyzeTransaction(
            @RequestParam String transactionId,
            @RequestParam(required = false) String endpoint,
            Authentication authentication) {
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        Map<String, Object> analysis = new HashMap<>();
        
        try {
            analysis.put("transaction_id", transactionId);
            analysis.put("search_endpoint", endpoint != null ? endpoint : "ALL");
            
            // 1. Find in PayProp API
            Map<String, Object> payPropData = findTransactionInPayProp(transactionId, endpoint);
            analysis.put("payprop_data", payPropData);
            
            // 2. Find in local database
            Map<String, Object> databaseData = findTransactionInDatabase(transactionId);
            analysis.put("database_data", databaseData);
            
            // 3. Analyze all date fields from PayProp
            if (payPropData.containsKey("found") && (Boolean) payPropData.get("found")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> transactionData = (Map<String, Object>) payPropData.get("transaction_data");
                Map<String, Object> dateAnalysis = analyzeAllDateFields(transactionData);
                analysis.put("date_field_analysis", dateAnalysis);
            }
            
            // 4. Compare API vs Database
            Map<String, Object> comparison = detailedComparison(payPropData, databaseData);
            analysis.put("detailed_comparison", comparison);
            
            analysis.put("status", "SUCCESS");
            return ResponseEntity.ok(analysis);
            
        } catch (Exception e) {
            logger.error("❌ Transaction analysis failed: {}", e.getMessage(), e);
            analysis.put("status", "ERROR");
            analysis.put("error", e.getMessage());
            return ResponseEntity.ok(analysis);
        }
    }

    /**
     * 🚀 TRIGGER EXISTING SYNC - Test your existing sync services
     */
    @PostMapping("/trigger-sync")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> triggerSync(
            @RequestParam String syncType,
            @RequestParam(required = false, defaultValue = "false") boolean dryRun,
            Authentication authentication) {
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        Map<String, Object> result = new HashMap<>();
        
        try {
            result.put("sync_type", syncType);
            result.put("dry_run", dryRun);
            result.put("started_at", LocalDateTime.now());
            
            Map<String, Object> syncResult;
            
            switch (syncType.toUpperCase()) {
                case "COMPREHENSIVE_FINANCIAL":
                    logger.info("🚀 Triggering comprehensive financial sync...");
                    syncResult = payPropFinancialSyncService.syncComprehensiveFinancialData();
                    break;
                    
                case "DUAL_FINANCIAL":
                    logger.info("🚀 Triggering dual financial sync...");
                    syncResult = payPropFinancialSyncService.syncDualFinancialData();
                    break;
                    
                case "PROPERTIES_ONLY":
                    logger.info("🚀 Triggering properties sync...");
                    List<Map<String, Object>> properties = payPropSyncService.exportAllProperties();
                    syncResult = Map.of(
                        "properties_found", properties.size(),
                        "status", "SUCCESS",
                        "note", "Properties exported but not processed (dry run mode)"
                    );
                    break;
                    
                case "BENEFICIARIES_ONLY":
                    logger.info("🚀 Triggering beneficiaries sync...");
                    List<Map<String, Object>> beneficiaries = payPropSyncService.exportAllBeneficiaries();
                    syncResult = Map.of(
                        "beneficiaries_found", beneficiaries.size(),
                        "status", "SUCCESS",
                        "note", "Beneficiaries exported but not processed (dry run mode)"
                    );
                    break;
                    
                default:
                    syncResult = Map.of(
                        "status", "ERROR",
                        "error", "Unknown sync type: " + syncType + ". Available: COMPREHENSIVE_FINANCIAL, DUAL_FINANCIAL, PROPERTIES_ONLY, BENEFICIARIES_ONLY"
                    );
            }
            
            result.put("sync_result", syncResult);
            result.put("completed_at", LocalDateTime.now());
            result.put("status", "SUCCESS");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("❌ Sync trigger failed: {}", e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
            result.put("completed_at", LocalDateTime.now());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 📋 ENDPOINT DISCOVERY - Test all available endpoints with current scopes
     */
    @PostMapping("/discover-endpoints")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> discoverEndpoints(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        Map<String, Object> discovery = new HashMap<>();
        
        try {
            HttpHeaders headers = createAuthorizedHeadersSafe();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            // Test endpoints based on your known scopes
            String[] testEndpoints = {
                "/export/payments",
                "/export/invoices", 
                "/export/properties",
                "/export/beneficiaries",
                "/export/tenants",
                "/report/all-payments",
                "/report/icdn",
                "/report/beneficiary/balances",
                "/report/tenant/balances",
                "/report/processing-summary",
                "/report/agency/income",
                "/meta/me",
                "/payments/categories",
                "/maintenance/tickets",
                "/maintenance/categories"
            };
            
            Map<String, Map<String, Object>> endpointResults = new HashMap<>();
            int workingEndpoints = 0;
            
            for (String endpoint : testEndpoints) {
                Map<String, Object> endpointResult = new HashMap<>();
                
                try {
                    String testUrl = payPropApiBase + endpoint + "?rows=1&page=1";
                    @SuppressWarnings("unchecked")
                                    ResponseEntity<Map> response = restTemplate.exchange(testUrl, HttpMethod.GET, request, Map.class);
                    
                    endpointResult.put("status", "✅ WORKS");
                    endpointResult.put("status_code", response.getStatusCode().value());
                    endpointResult.put("has_data", response.getBody() != null);
                    endpointResult.put("item_count", getItemCount(response.getBody()));
                    endpointResult.put("response_keys", response.getBody() != null ? response.getBody().keySet() : Collections.emptySet());
                    workingEndpoints++;
                    
                } catch (HttpClientErrorException e) {
                    endpointResult.put("status", "❌ " + e.getStatusCode());
                    endpointResult.put("error", e.getResponseBodyAsString());
                    if (e.getStatusCode().value() == 403) {
                        endpointResult.put("reason", "Insufficient OAuth scope");
                    }
                } catch (Exception e) {
                    endpointResult.put("status", "❌ ERROR");
                    endpointResult.put("error", e.getMessage());
                }
                
                endpointResults.put(endpoint, endpointResult);
            }
            
            discovery.put("endpoints_tested", testEndpoints.length);
            discovery.put("working_endpoints", workingEndpoints);
            discovery.put("success_rate", String.format("%.1f%%", (workingEndpoints * 100.0 / testEndpoints.length)));
            discovery.put("endpoint_results", endpointResults);
            
            // Get current token info
            PayPropTokens tokens = oAuth2Service.getCurrentTokens();
            if (tokens != null) {
                discovery.put("current_scopes", tokens.getScopes());
                discovery.put("token_expires", tokens.getExpiresAt());
            }
            
            discovery.put("status", "SUCCESS");
            return ResponseEntity.ok(discovery);
            
        } catch (Exception e) {
            logger.error("❌ Endpoint discovery failed: {}", e.getMessage(), e);
            discovery.put("status", "ERROR");
            discovery.put("error", e.getMessage());
            return ResponseEntity.ok(discovery);
        }
    }

    /**
     * 🎯 SMART SEARCH - Multi-criteria search across PayProp and database
     */
    @PostMapping("/smart-search")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> smartSearch(
            @RequestBody Map<String, Object> searchCriteria,
            Authentication authentication) {
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        Map<String, Object> searchResults = new HashMap<>();
        
        try {
            // Extract search criteria
            String amount = (String) searchCriteria.get("amount");
            String fromDateStr = (String) searchCriteria.get("fromDate");
            String toDateStr = (String) searchCriteria.get("toDate");
            String propertyId = (String) searchCriteria.get("propertyId");
            @SuppressWarnings("unused")
            String beneficiaryName = (String) searchCriteria.get("beneficiaryName");
            String batchId = (String) searchCriteria.get("batchId");
            String endpoint = (String) searchCriteria.get("endpoint");
            Boolean includeBeneficiaryInfo = (Boolean) searchCriteria.getOrDefault("includeBeneficiaryInfo", true);
            
            LocalDate fromDate = fromDateStr != null ? LocalDate.parse(fromDateStr) : LocalDate.now().minusDays(30);
            LocalDate toDate = toDateStr != null ? LocalDate.parse(toDateStr) : LocalDate.now();
            
            searchResults.put("search_criteria", searchCriteria);
            
            HttpHeaders headers = createAuthorizedHeadersSafe();
            
            // If specific endpoint requested, test only that one
            if (endpoint != null && !endpoint.trim().isEmpty()) {
                Map<String, Object> specificResult = testSpecificEndpointWithCriteria(
                    endpoint, fromDate, toDate, propertyId, batchId, amount, includeBeneficiaryInfo, headers);
                searchResults.put("specific_endpoint_result", specificResult);
            } else {
                // Test all relevant endpoints
                Map<String, Object> allEndpointResults = searchAllRelevantEndpoints(
                    fromDate, toDate, propertyId, batchId, amount, includeBeneficiaryInfo, headers);
                searchResults.put("all_endpoint_results", allEndpointResults);
            }
            
            // Search local database with same criteria
            Map<String, Object> databaseResults = searchDatabaseWithCriteria(searchCriteria);
            searchResults.put("database_results", databaseResults);
            
            searchResults.put("status", "SUCCESS");
            return ResponseEntity.ok(searchResults);
            
        } catch (Exception e) {
            logger.error("❌ Smart search failed: {}", e.getMessage(), e);
            searchResults.put("status", "ERROR");
            searchResults.put("error", e.getMessage());
            return ResponseEntity.ok(searchResults);
        }
    }

    /**
     * 📈 DATABASE VS API COMPARISON - Compare specific records
     */
    @PostMapping("/compare-specific-record")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> compareSpecificRecord(
            @RequestParam String payPropTransactionId,
            Authentication authentication) {
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        Map<String, Object> comparison = new HashMap<>();
        
        try {
            // 1. Find in database
            FinancialTransaction dbTransaction = financialTransactionRepository
                .findByPayPropTransactionId(payPropTransactionId);
            
            Map<String, Object> databaseRecord = new HashMap<>();
            if (dbTransaction != null) {
                databaseRecord.put("found", true);
                databaseRecord.put("amount", dbTransaction.getAmount());
                databaseRecord.put("transaction_date", dbTransaction.getTransactionDate());
                databaseRecord.put("reconciliation_date", dbTransaction.getReconciliationDate());
                databaseRecord.put("property_name", dbTransaction.getPropertyName());
                databaseRecord.put("property_id", dbTransaction.getPropertyId());
                databaseRecord.put("data_source", dbTransaction.getDataSource());
                databaseRecord.put("transaction_type", dbTransaction.getTransactionType());
                databaseRecord.put("batch_id", dbTransaction.getPayPropBatchId());
                databaseRecord.put("created_at", dbTransaction.getCreatedAt());
                databaseRecord.put("full_record", dbTransaction);
            } else {
                databaseRecord.put("found", false);
                databaseRecord.put("message", "Transaction not found in database");
            }
            
            // 2. Search in PayProp API (multiple endpoints)
            Map<String, Object> apiSearch = findInMultiplePayPropEndpoints(payPropTransactionId);
            
            comparison.put("database_record", databaseRecord);
            comparison.put("payprop_search", apiSearch);
            
            // 3. Field-by-field comparison if found in both
            if (databaseRecord.containsKey("found") && (Boolean) databaseRecord.get("found") && 
                apiSearch.containsKey("found") && (Boolean) apiSearch.get("found")) {
                
                @SuppressWarnings("unchecked")
                Map<String, Object> apiTransactionData = (Map<String, Object>) apiSearch.get("transaction_data");
                Map<String, Object> fieldComparison = compareTransactionFields(apiTransactionData, dbTransaction);
                comparison.put("field_comparison", fieldComparison);
            }
            
            comparison.put("status", "SUCCESS");
            return ResponseEntity.ok(comparison);
            
        } catch (Exception e) {
            logger.error("❌ Record comparison failed: {}", e.getMessage(), e);
            comparison.put("status", "ERROR");
            comparison.put("error", e.getMessage());
            return ResponseEntity.ok(comparison);
        }
    }

    /**
     * ⚡ BATCH ANALYSIS - Analyze batch payments and groupings
     */
    @PostMapping("/analyze-batch")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> analyzeBatch(
            @RequestParam(required = false) String batchId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
            @RequestParam(required = false) String propertyId,
            Authentication authentication) {
        
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        Map<String, Object> batchAnalysis = new HashMap<>();
        
        try {
            if (fromDate == null) fromDate = LocalDate.now().minusDays(30);
            if (toDate == null) toDate = LocalDate.now();
            
            batchAnalysis.put("analysis_criteria", Map.of(
                "batch_id", batchId != null ? batchId : "ALL",
                "date_range", fromDate + " to " + toDate,
                "property_id", propertyId != null ? propertyId : "ALL"
            ));
            
            // 1. Search PayProp for batch data
            Map<String, Object> payPropBatches = searchPayPropBatches(batchId, fromDate, toDate, propertyId);
            batchAnalysis.put("payprop_batches", payPropBatches);
            
            // 2. Search database for batch data
            Map<String, Object> databaseBatches = searchDatabaseBatches(batchId, fromDate, toDate, propertyId);
            batchAnalysis.put("database_batches", databaseBatches);
            
            // 3. Compare batch integrity
            Map<String, Object> batchIntegrity = analyzeBatchIntegrity(payPropBatches, databaseBatches);
            batchAnalysis.put("batch_integrity", batchIntegrity);
            
            batchAnalysis.put("status", "SUCCESS");
            return ResponseEntity.ok(batchAnalysis);
            
        } catch (Exception e) {
            logger.error("❌ Batch analysis failed: {}", e.getMessage(), e);
            batchAnalysis.put("status", "ERROR");
            batchAnalysis.put("error", e.getMessage());
            return ResponseEntity.ok(batchAnalysis);
        }
    }

    @PostMapping("/raw-api-call")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> rawApiCall(
            @RequestParam String endpoint,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String parameters,
            @RequestParam(required = false) String requestBody,
            Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        Map<String, Object> result = new HashMap<>();
        
        try {
            HttpMethod httpMethod = HttpMethod.valueOf(method != null ? method.toUpperCase() : "GET");
            String fullUrl = payPropApiBase + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);
            
            if (parameters != null && !parameters.trim().isEmpty()) {
                fullUrl += (endpoint.contains("?") ? "&" : "?") + parameters;
            }
            
            // ✅ MOVE THIS BEFORE request_details
            HttpHeaders headers = createAuthorizedHeadersSafe();
            
            // Handle request body
            HttpEntity<?> request;
            if (requestBody != null && !requestBody.trim().isEmpty() && 
                (httpMethod == HttpMethod.POST || httpMethod == HttpMethod.PUT || httpMethod == HttpMethod.PATCH)) {
                
                // ✅ Special handling for tag entities endpoints
                if (endpoint.contains("/tags/entities/") && httpMethod == HttpMethod.POST) {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    
                    try {
                        // Convert simple array format to PayProp expected format if needed
                        if (requestBody.trim().startsWith("[") && !requestBody.contains("names")) {
                            // Parse the array and convert to proper format
                            String tagArray = requestBody.trim();
                            if (tagArray.equals("[\"7QZGNBQ19Y\"]") || tagArray.matches("\\[\"[A-Za-z0-9]+\"\\]")) {
                                String tagId = tagArray.replaceAll("[\\[\\]\"']", "");
                                Map<String, Object> properFormat = new HashMap<>();
                                properFormat.put("tags", List.of(tagId));
                                
                                // Try to get tag name for better API compatibility
                                try {
                                    String tagName = "Unknown Tag"; // Default
                                    if ("7QZGNBQ19Y".equals(tagId)) tagName = "Owner-858-Saturday Test";
                                    else if ("lwZ7a2wXDq".equals(tagId)) tagName = "Owner-1015-Dawn Naylor Test";
                                    else if ("agXVv8oX3R".equals(tagId)) tagName = "Owner-1015-DDDDD Tes 2";
                                    else if ("KAXNreDXkg".equals(tagId)) tagName = "Owner-1200-Olivia 1";
                                    else if ("z2JkK3xJbg".equals(tagId)) tagName = "Owner-1015-Dawn 2";
                                    
                                    properFormat.put("names", List.of(tagName));
                                } catch (Exception e) {
                                    log.warn("Could not add tag name for {}", tagId);
                                }
                                
                                ObjectMapper mapper = new ObjectMapper();
                                requestBody = mapper.writeValueAsString(properFormat);
                                log.info("✅ Converted tag request format to: {}", requestBody);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Could not convert tag request format, using as-is: {}", e.getMessage());
                    }
                } else if (endpoint.contains("/tags/entities/")) {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                }
                
                request = new HttpEntity<>(requestBody, headers);
            } else {
                request = new HttpEntity<>(headers);
            }
            
            // ✅ NOW request_details can access headers
            result.put("request_details", Map.of(
                "method", httpMethod.toString(),
                "full_url", fullUrl,
                "endpoint", endpoint,
                "parameters", parameters != null ? parameters : "None",
                "request_body", requestBody != null ? requestBody : "None",
                "content_type", headers.getContentType() != null ? headers.getContentType().toString() : "None"
            ));
            
            ResponseEntity<Map> response = restTemplate.exchange(fullUrl, httpMethod, request, Map.class);
            
            result.put("response", Map.of(
                "status_code", response.getStatusCode().value(),
                "headers", response.getHeaders(),
                "body", response.getBody(),
                "item_count", getItemCount(response.getBody())
            ));
            
            result.put("status", "SUCCESS");
            return ResponseEntity.ok(result);
            
        } catch (HttpClientErrorException e) {
            logger.error("❌ Raw API call failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            result.put("status", "API_ERROR");
            result.put("http_status", e.getStatusCode().value());
            result.put("error_body", e.getResponseBodyAsString());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("❌ Raw API call failed: {}", e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 📊 SYNC STATUS DASHBOARD - Overall sync health and statistics
     */
    @GetMapping("/sync-dashboard")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSyncDashboard(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        Map<String, Object> dashboard = new HashMap<>();
        
        try {
            // 1. OAuth Status
            PayPropTokens tokens = oAuth2Service.getCurrentTokens();
            dashboard.put("oauth_status", Map.of(
                "has_tokens", tokens != null,
                "is_valid", oAuth2Service.hasValidTokens(),
                "expires_at", tokens != null ? tokens.getExpiresAt() : null,
                "scopes", tokens != null ? tokens.getScopes() : null
            ));
            
            // 2. Database Statistics
            dashboard.put("database_stats", Map.of(
                "total_properties", propertyRepository.count(),
                "payprop_synced_properties", propertyRepository.findByPayPropIdIsNotNull().size(),
                "total_beneficiaries", beneficiaryRepository.count(),
                "payprop_synced_beneficiaries", beneficiaryRepository.findByPayPropBeneficiaryIdIsNotNull().size(),
                "total_financial_transactions", financialTransactionRepository.count(),
                "batch_payments", batchPaymentRepository.count(),
                "payment_categories", paymentCategoryRepository.count()
            ));
            
            // 3. Recent Sync Activity (if sync monitoring is available)
            if (payPropSyncMonitoringService != null) {
                try {
                    PayPropSyncMonitoringService.RealTimeSyncReport syncReport = 
                        payPropSyncMonitoringService.generateRealTimeSyncReport();
                    dashboard.put("sync_monitoring", Map.of(
                        "health_status", syncReport.getHealthStatus(),
                        "realtime_sync_rate", syncReport.getRealtimeSyncRate(),
                        "batch_fallback_rate", syncReport.getBatchFallbackRate(),
                        "recent_updates", syncReport.getRecentTicketUpdates()
                    ));
                } catch (Exception e) {
                    dashboard.put("sync_monitoring", Map.of("error", e.getMessage()));
                }
            }
            
            // 4. Financial Data Summary
            LocalDate last30Days = LocalDate.now().minusDays(30);
            List<FinancialTransaction> recentTransactions = financialTransactionRepository
                .findByTransactionDateBetween(last30Days, LocalDate.now());
            
            Map<String, Long> transactionsByType = recentTransactions.stream()
                .collect(Collectors.groupingBy(
                    FinancialTransaction::getTransactionType,
                    Collectors.counting()
                ));
            
            Map<String, Long> transactionsBySource = recentTransactions.stream()
                .collect(Collectors.groupingBy(
                    FinancialTransaction::getDataSource,
                    Collectors.counting()
                ));
            
            dashboard.put("recent_financial_activity", Map.of(
                "last_30_days_transactions", recentTransactions.size(),
                "by_transaction_type", transactionsByType,
                "by_data_source", transactionsBySource
            ));
            
            dashboard.put("status", "SUCCESS");
            dashboard.put("generated_at", LocalDateTime.now());
            
            return ResponseEntity.ok(dashboard);
            
        } catch (Exception e) {
            logger.error("❌ Sync dashboard failed: {}", e.getMessage(), e);
            dashboard.put("status", "ERROR");
            dashboard.put("error", e.getMessage());
            return ResponseEntity.ok(dashboard);
        }
    }

    // ===== HELPER METHODS =====

    /**
     * Create authorized HTTP headers - SAFE VERSION
     */
    private HttpHeaders createAuthorizedHeadersSafe() {
        try {
            return oAuth2Service.createAuthorizedHeaders();
        } catch (Exception e) {
            logger.error("Failed to create authorized headers: {}", e.getMessage());
            throw new RuntimeException("OAuth header creation error: " + e.getMessage(), e);
        }
    }

    /**
     * Find transaction in multiple PayProp endpoints - FIXED VERSION
     */
    private Map<String, Object> findTransactionInPayProp(String transactionId, String endpoint) {
        Map<String, Object> searchResults = new HashMap<>();
        
        try {
            HttpHeaders headers = createAuthorizedHeadersSafe();
            
            if (endpoint != null && !endpoint.trim().isEmpty()) {
                // Search specific endpoint
                searchResults = searchSpecificEndpointForTransaction(endpoint, transactionId, headers);
            } else {
                // Search all endpoints
                String[] endpoints = {"/export/payments", "/report/all-payments", "/report/icdn", "/export/invoices"};
                
                for (String ep : endpoints) {
                    try {
                        Map<String, Object> result = searchSpecificEndpointForTransaction(ep, transactionId, headers);
                        if (result.containsKey("found") && (Boolean) result.get("found")) {
                            searchResults = result;
                            searchResults.put("found_in_endpoint", ep);
                            break;
                        }
                    } catch (Exception e) {
                        logger.debug("Could not search endpoint {}: {}", ep, e.getMessage());
                    }
                }
                
                if (!searchResults.containsKey("found")) {
                    searchResults.put("found", false);
                    searchResults.put("searched_endpoints", Arrays.asList(endpoints));
                }
            }
            
            return searchResults;
            
        } catch (Exception e) {
            logger.error("Error finding transaction in PayProp: {}", e.getMessage(), e);
            searchResults.put("found", false);
            searchResults.put("error", e.getMessage());
            return searchResults;
        }
    }

    /**
     * Search specific endpoint for transaction
     */
    private Map<String, Object> searchSpecificEndpointForTransaction(String endpoint, String transactionId, HttpHeaders headers) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String url = payPropApiBase + endpoint + "?rows=1000&include_beneficiary_info=true";
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
            
            for (Map<String, Object> item : items) {
                if (transactionId.equals(item.get("id"))) {
                    result.put("found", true);
                    result.put("transaction_data", item);
                    result.put("all_date_fields", extractAllDateFields(item));
                    return result;
                }
            }
            
            result.put("found", false);
            return result;
            
        } catch (Exception e) {
            result.put("found", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Detailed comparison between API and database data - FIXED VERSION
     */
    private Map<String, Object> detailedComparison(Map<String, Object> payPropData, Map<String, Object> databaseData) {
        Map<String, Object> comparison = new HashMap<>();
        
        try {
            comparison.put("payprop_found", payPropData.containsKey("found") && (Boolean) payPropData.get("found"));
            comparison.put("database_found", databaseData.containsKey("found") && (Boolean) databaseData.get("found"));
            
            if (comparison.get("payprop_found").equals(true) && comparison.get("database_found").equals(true)) {
                // Both found - do detailed field comparison
                @SuppressWarnings("unchecked")
                Map<String, Object> apiTransactionData = (Map<String, Object>) payPropData.get("transaction_data");
                @SuppressWarnings("unchecked")
                Map<String, Object> dbTransactionData = (Map<String, Object>) databaseData.get("transaction_data");
                
                comparison.put("field_comparison", compareTransactionFieldsDetailed(apiTransactionData, dbTransactionData));
                comparison.put("sync_status", "BOTH_FOUND");
                
            } else if (comparison.get("payprop_found").equals(true)) {
                comparison.put("sync_status", "MISSING_FROM_DATABASE");
                comparison.put("recommendation", "Transaction exists in PayProp but not in database - sync needed");
                
            } else if (comparison.get("database_found").equals(true)) {
                comparison.put("sync_status", "MISSING_FROM_PAYPROP");
                comparison.put("recommendation", "Transaction exists in database but not in PayProp - data inconsistency");
                
            } else {
                comparison.put("sync_status", "NOT_FOUND_ANYWHERE");
                comparison.put("recommendation", "Transaction ID not found in either system");
            }
            
            return comparison;
            
        } catch (Exception e) {
            logger.error("Error in detailed comparison: {}", e.getMessage(), e);
            comparison.put("error", e.getMessage());
            return comparison;
        }
    }

    /**
     * Compare transaction fields in detail
     */
    private Map<String, Object> compareTransactionFieldsDetailed(Map<String, Object> apiData, Map<String, Object> dbData) {
        Map<String, Object> fieldComparison = new HashMap<>();
        
        // Amount comparison
        Object apiAmount = apiData.get("amount");
        Object dbAmount = dbData.get("amount");
        fieldComparison.put("amount", Map.of(
            "api_value", apiAmount,
            "database_value", dbAmount,
            "matches", Objects.equals(apiAmount, dbAmount)
        ));
        
        // Date comparison
        Map<String, Object> apiDates = extractAllDateFields(apiData);
        Object dbTransactionDate = dbData.get("transaction_date");
        Object dbReconciliationDate = dbData.get("reconciliation_date");
        
        fieldComparison.put("dates", Map.of(
            "api_dates", apiDates,
            "database_transaction_date", dbTransactionDate,
            "database_reconciliation_date", dbReconciliationDate
        ));
        
        // Property comparison
        @SuppressWarnings("unchecked")
        Map<String, Object> apiProperty = (Map<String, Object>) apiData.get("property");
        Object dbPropertyId = dbData.get("property_id");
        Object dbPropertyName = dbData.get("property_name");
        
        fieldComparison.put("property", Map.of(
            "api_property_id", apiProperty != null ? apiProperty.get("id") : null,
            "api_property_name", apiProperty != null ? apiProperty.get("name") : null,
            "database_property_id", dbPropertyId,
            "database_property_name", dbPropertyName,
            "property_id_matches", apiProperty != null && Objects.equals(apiProperty.get("id"), dbPropertyId)
        ));
        
        return fieldComparison;
    }

    /**
     * Search PayProp endpoints for transaction data
     */
    private Map<String, Object> searchPayPropEndpoints(String amount, LocalDate fromDate, LocalDate toDate, 
            String propertyId, String beneficiaryName, String batchId, String transactionId) {
        
        Map<String, Object> results = new HashMap<>();
        HttpHeaders headers = createAuthorizedHeadersSafe();
        
        try {
            // Search in export/payments
            Map<String, Object> exportPayments = searchInExportPayments(
                amount, fromDate, toDate, propertyId, beneficiaryName, transactionId, headers);
            results.put("export_payments", exportPayments);
            
            // Search in report/all-payments
            Map<String, Object> reportPayments = searchInReportAllPayments(
                amount, fromDate, toDate, propertyId, batchId, transactionId, headers);
            results.put("report_all_payments", reportPayments);
            
            // Search in ICDN
            Map<String, Object> icdnResults = searchInICDN(
                amount, fromDate, toDate, propertyId, transactionId, headers);
            results.put("report_icdn", icdnResults);
            
        } catch (Exception e) {
            results.put("error", e.getMessage());
        }
        
        return results;
    }

    /**
     * Search in export/payments endpoint
     */
    private Map<String, Object> searchInExportPayments(String amount, LocalDate fromDate, LocalDate toDate,
            String propertyId, String beneficiaryName, String transactionId, HttpHeaders headers) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            StringBuilder url = new StringBuilder(payPropApiBase + "/export/payments?include_beneficiary_info=true&rows=100");
            
            if (propertyId != null && !propertyId.trim().isEmpty()) {
                url.append("&property_id=").append(propertyId);
            }
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url.toString(), HttpMethod.GET, request, Map.class);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> payments = (List<Map<String, Object>>) response.getBody().get("items");
            List<Map<String, Object>> matches = new ArrayList<>();
            
            for (Map<String, Object> payment : payments) {
                if (matchesSearchCriteria(payment, amount, fromDate, toDate, beneficiaryName, transactionId)) {
                    // Add all date fields for analysis
                    Map<String, Object> enrichedPayment = new HashMap<>(payment);
                    enrichedPayment.put("all_date_fields", extractAllDateFields(payment));
                    matches.add(enrichedPayment);
                }
            }
            
            result.put("endpoint", "/export/payments");
            result.put("total_items_returned", payments.size());
            result.put("matches_found", matches.size());
            result.put("matching_payments", matches);
            result.put("status", "SUCCESS");
            
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Search in report/all-payments endpoint
     */
    private Map<String, Object> searchInReportAllPayments(String amount, LocalDate fromDate, LocalDate toDate,
            String propertyId, String batchId, String transactionId, HttpHeaders headers) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            StringBuilder url = new StringBuilder(payPropApiBase + "/report/all-payments");
            url.append("?from_date=").append(fromDate);
            url.append("&to_date=").append(toDate);
            url.append("&filter_by=reconciliation_date");
            url.append("&include_beneficiary_info=true");
            url.append("&rows=1000");
            
            if (propertyId != null && !propertyId.trim().isEmpty()) {
                url.append("&property_id=").append(propertyId);
            }
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url.toString(), HttpMethod.GET, request, Map.class);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> payments = (List<Map<String, Object>>) response.getBody().get("items");
            List<Map<String, Object>> matches = new ArrayList<>();
            
            for (Map<String, Object> payment : payments) {
                if (matchesSearchCriteria(payment, amount, fromDate, toDate, null, transactionId) ||
                    matchesBatchCriteria(payment, batchId)) {
                    
                    Map<String, Object> enrichedPayment = new HashMap<>(payment);
                    enrichedPayment.put("all_date_fields", extractAllDateFields(payment));
                    enrichedPayment.put("batch_info", extractBatchInfo(payment));
                    matches.add(enrichedPayment);
                }
            }
            
            result.put("endpoint", "/report/all-payments");
            result.put("url_used", url.toString());
            result.put("total_items_returned", payments.size());
            result.put("matches_found", matches.size());
            result.put("matching_payments", matches);
            result.put("status", "SUCCESS");
            
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Search in ICDN endpoint
     */
    private Map<String, Object> searchInICDN(String amount, LocalDate fromDate, LocalDate toDate,
            String propertyId, String transactionId, HttpHeaders headers) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            StringBuilder url = new StringBuilder(payPropApiBase + "/report/icdn");
            url.append("?from_date=").append(fromDate);
            url.append("&to_date=").append(toDate);
            url.append("&rows=1000");
            
            if (propertyId != null && !propertyId.trim().isEmpty()) {
                url.append("&property_id=").append(propertyId);
            }
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url.toString(), HttpMethod.GET, request, Map.class);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> transactions = (List<Map<String, Object>>) response.getBody().get("items");
            List<Map<String, Object>> matches = new ArrayList<>();
            
            for (Map<String, Object> transaction : transactions) {
                if (matchesSearchCriteria(transaction, amount, fromDate, toDate, null, transactionId)) {
                    Map<String, Object> enrichedTransaction = new HashMap<>(transaction);
                    enrichedTransaction.put("all_date_fields", extractAllDateFields(transaction));
                    matches.add(enrichedTransaction);
                }
            }
            
            result.put("endpoint", "/report/icdn");
            result.put("url_used", url.toString());
            result.put("total_items_returned", transactions.size());
            result.put("matches_found", matches.size());
            result.put("matching_transactions", matches);
            result.put("status", "SUCCESS");
            
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Search local database with criteria
     */
    private Map<String, Object> searchLocalDatabase(String amount, LocalDate fromDate, LocalDate toDate,
            String propertyId, String beneficiaryName, String batchId, String transactionId) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<FinancialTransaction> transactions = financialTransactionRepository.findAll().stream()
                .filter(t -> matchesDatabaseCriteria(t, amount, fromDate, toDate, propertyId, beneficiaryName, batchId, transactionId))
                .collect(Collectors.toList());
            
            result.put("matches_found", transactions.size());
            result.put("matching_transactions", transactions.stream()
                .map(this::transactionToMap)
                .collect(Collectors.toList()));
            
            // Group by data source
            Map<String, Long> byDataSource = transactions.stream()
                .collect(Collectors.groupingBy(FinancialTransaction::getDataSource, Collectors.counting()));
            result.put("matches_by_data_source", byDataSource);
            
            result.put("status", "SUCCESS");
            
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Find transaction in database - FIXED VERSION
     */
    private Map<String, Object> findTransactionInDatabase(String transactionId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            FinancialTransaction transaction = financialTransactionRepository.findByPayPropTransactionId(transactionId);
            
            if (transaction != null) {
                result.put("found", true);
                result.put("transaction_data", transactionToMap(transaction));
                result.put("database_dates", Map.of(
                    "transaction_date", transaction.getTransactionDate(),
                    "reconciliation_date", transaction.getReconciliationDate(),
                    "created_at", transaction.getCreatedAt(),
                    "updated_at", transaction.getUpdatedAt()
                ));
            } else {
                result.put("found", false);
                result.put("message", "Transaction not found in database");
            }
            
        } catch (Exception e) {
            result.put("found", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Find in multiple PayProp endpoints
     */
    private Map<String, Object> findInMultiplePayPropEndpoints(String transactionId) {
        Map<String, Object> searchResults = new HashMap<>();
        
        HttpHeaders headers = createAuthorizedHeadersSafe();
        String[] endpoints = {"/export/payments", "/report/all-payments", "/report/icdn", "/export/invoices"};
        
        for (String endpoint : endpoints) {
            try {
                String url = payPropApiBase + endpoint + "?rows=1000&include_beneficiary_info=true";
                
                HttpEntity<String> request = new HttpEntity<>(headers);
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
                
                for (Map<String, Object> item : items) {
                    if (transactionId.equals(item.get("id"))) {
                        searchResults.put("found", true);
                        searchResults.put("found_in_endpoint", endpoint);
                        searchResults.put("transaction_data", item);
                        searchResults.put("all_date_fields", extractAllDateFields(item));
                        return searchResults;
                    }
                }
                
            } catch (Exception e) {
                logger.debug("Could not search endpoint {}: {}", endpoint, e.getMessage());
            }
        }
        
        searchResults.put("found", false);
        searchResults.put("searched_endpoints", Arrays.asList(endpoints));
        return searchResults;
    }

    /**
     * Test single endpoint with parameters
     */
    private Map<String, Object> testSingleEndpoint(String endpoint, LocalDate fromDate, LocalDate toDate,
            String propertyId, String filterBy, int maxRows, HttpHeaders headers) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            StringBuilder url = new StringBuilder(payPropApiBase + endpoint);
            List<String> params = new ArrayList<>();
            
            // Add date parameters based on endpoint
            if (endpoint.contains("/report/")) {
                params.add("from_date=" + fromDate);
                params.add("to_date=" + toDate);
            }
            
            if (filterBy != null) {
                params.add("filter_by=" + filterBy);
            }
            
            if (propertyId != null && !propertyId.trim().isEmpty()) {
                params.add("property_id=" + propertyId);
            }
            
            params.add("rows=" + maxRows);
            params.add("include_beneficiary_info=true");
            
            if (!params.isEmpty()) {
                url.append("?").append(String.join("&", params));
            }
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url.toString(), HttpMethod.GET, request, Map.class);
            
            result.put("status", "SUCCESS");
            result.put("url", url.toString());
            result.put("status_code", response.getStatusCode().value());
            result.put("item_count", getItemCount(response.getBody()));
            result.put("response_body", response.getBody());
            
        } catch (HttpClientErrorException e) {
            result.put("status", "HTTP_ERROR");
            result.put("status_code", e.getStatusCode().value());
            result.put("error", e.getResponseBodyAsString());
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Test all relevant endpoints
     */
    private Map<String, Object> searchAllRelevantEndpoints(LocalDate fromDate, LocalDate toDate, String propertyId,
            String batchId, String amount, Boolean includeBeneficiaryInfo, HttpHeaders headers) {
        
        Map<String, Object> results = new HashMap<>();
        
        String[] endpoints = {
            "/export/payments",
            "/report/all-payments", 
            "/report/icdn",
            "/export/invoices"
        };
        
        for (String endpoint : endpoints) {
            Map<String, Object> endpointResult = testSpecificEndpointWithCriteria(
                endpoint, fromDate, toDate, propertyId, batchId, amount, includeBeneficiaryInfo, headers);
            results.put(endpoint.replace("/", "_"), endpointResult);
        }
        
        return results;
    }

    /**
     * Test specific endpoint with search criteria
     */
    private Map<String, Object> testSpecificEndpointWithCriteria(String endpoint, LocalDate fromDate, LocalDate toDate,
            String propertyId, String batchId, String amount, Boolean includeBeneficiaryInfo, HttpHeaders headers) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            StringBuilder url = new StringBuilder(payPropApiBase + endpoint);
            List<String> params = new ArrayList<>();
            
            // Handle different endpoint patterns
            if (endpoint.startsWith("/report/")) {
                params.add("from_date=" + fromDate);
                params.add("to_date=" + toDate);
                if (endpoint.contains("/all-payments")) {
                    params.add("filter_by=reconciliation_date");
                }
            }
            
            if (propertyId != null && !propertyId.trim().isEmpty()) {
                params.add("property_id=" + propertyId);
            }
            
            if (includeBeneficiaryInfo) {
                params.add("include_beneficiary_info=true");
            }
            
            params.add("rows=1000");
            
            if (!params.isEmpty()) {
                url.append("?").append(String.join("&", params));
            }
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url.toString(), HttpMethod.GET, request, Map.class);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
            List<Map<String, Object>> matches = new ArrayList<>();
            
            // Filter results based on criteria
            for (Map<String, Object> item : items) {
                if (matchesAdvancedCriteria(item, amount, batchId)) {
                    Map<String, Object> enrichedItem = new HashMap<>(item);
                    enrichedItem.put("all_date_fields", extractAllDateFields(item));
                    enrichedItem.put("batch_info", extractBatchInfo(item));
                    matches.add(enrichedItem);
                }
            }
            
            result.put("endpoint", endpoint);
            result.put("url_used", url.toString());
            result.put("total_items", items.size());
            result.put("matches_found", matches.size());
            result.put("matching_items", matches);
            result.put("status", "SUCCESS");
            
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Search database with comprehensive criteria
     */
    private Map<String, Object> searchDatabaseWithCriteria(Map<String, Object> searchCriteria) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String amount = (String) searchCriteria.get("amount");
            String fromDateStr = (String) searchCriteria.get("fromDate");
            String toDateStr = (String) searchCriteria.get("toDate");
            String propertyId = (String) searchCriteria.get("propertyId");
            String batchId = (String) searchCriteria.get("batchId");
            String transactionId = (String) searchCriteria.get("transactionId");
            String beneficiaryName = (String) searchCriteria.get("beneficiaryName");
            
            LocalDate fromDate = fromDateStr != null ? LocalDate.parse(fromDateStr) : LocalDate.now().minusDays(30);
            LocalDate toDate = toDateStr != null ? LocalDate.parse(toDateStr) : LocalDate.now();
            
            List<FinancialTransaction> allTransactions = financialTransactionRepository.findAll();
            List<FinancialTransaction> matches = allTransactions.stream()
                .filter(t -> matchesDatabaseCriteria(t, amount, fromDate, toDate, propertyId, beneficiaryName, batchId, transactionId))
                .collect(Collectors.toList());
            
            // Group by data source
            Map<String, Long> byDataSource = matches.stream()
                .collect(Collectors.groupingBy(FinancialTransaction::getDataSource, Collectors.counting()));
            
            // Group by transaction type
            Map<String, Long> byTransactionType = matches.stream()
                .collect(Collectors.groupingBy(FinancialTransaction::getTransactionType, Collectors.counting()));
            
            result.put("total_matches", matches.size());
            result.put("matches_by_data_source", byDataSource);
            result.put("matches_by_transaction_type", byTransactionType);
            result.put("matching_transactions", matches.stream()
                .limit(50) // Limit to prevent huge responses
                .map(this::transactionToMap)
                .collect(Collectors.toList()));
            result.put("status", "SUCCESS");
            
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Search PayProp batches
     */
    private Map<String, Object> searchPayPropBatches(String batchId, LocalDate fromDate, LocalDate toDate, String propertyId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            HttpHeaders headers = createAuthorizedHeadersSafe();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            StringBuilder url = new StringBuilder(payPropApiBase + "/report/all-payments");
            url.append("?from_date=").append(fromDate);
            url.append("&to_date=").append(toDate);
            url.append("&filter_by=reconciliation_date");
            url.append("&include_beneficiary_info=true");
            url.append("&rows=1000");
            
            if (propertyId != null && !propertyId.trim().isEmpty()) {
                url.append("&property_id=").append(propertyId);
            }
            
            ResponseEntity<Map> response = restTemplate.exchange(url.toString(), HttpMethod.GET, request, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> payments = (List<Map<String, Object>>) response.getBody().get("items");
            
            // Group by batch ID
            Map<String, List<Map<String, Object>>> batchGroups = new HashMap<>();
            int paymentsWithBatches = 0;
            
            for (Map<String, Object> payment : payments) {
                @SuppressWarnings("unchecked")
                Map<String, Object> paymentBatch = (Map<String, Object>) payment.get("payment_batch");
                if (paymentBatch != null) {
                    String paymentBatchId = (String) paymentBatch.get("id");
                    if (paymentBatchId != null) {
                        if (batchId == null || batchId.equals(paymentBatchId)) {
                            batchGroups.computeIfAbsent(paymentBatchId, k -> new ArrayList<>()).add(payment);
                            paymentsWithBatches++;
                        }
                    }
                }
            }
            
            result.put("total_payments_searched", payments.size());
            result.put("payments_with_batches", paymentsWithBatches);
            result.put("unique_batches_found", batchGroups.size());
            result.put("batch_groups", batchGroups);
            result.put("status", "SUCCESS");
            
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Search database batches
     */
    private Map<String, Object> searchDatabaseBatches(String batchId, LocalDate fromDate, LocalDate toDate, String propertyId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<FinancialTransaction> transactions = financialTransactionRepository
                .findByTransactionDateBetween(fromDate, toDate)
                .stream()
                .filter(t -> batchId == null || batchId.equals(t.getPayPropBatchId()))
                .filter(t -> propertyId == null || propertyId.equals(t.getPropertyId()))
                .collect(Collectors.toList());
            
            // Group by batch ID
            Map<String, List<FinancialTransaction>> batchGroups = transactions.stream()
                .filter(t -> t.getPayPropBatchId() != null)
                .collect(Collectors.groupingBy(FinancialTransaction::getPayPropBatchId));
            
            result.put("total_transactions_searched", transactions.size());
            result.put("transactions_with_batches", batchGroups.values().stream().mapToInt(List::size).sum());
            result.put("unique_batches_found", batchGroups.size());
            result.put("batch_groups", batchGroups.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().stream().map(this::transactionToMap).collect(Collectors.toList())
                )));
            result.put("status", "SUCCESS");
            
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Analyze batch integrity
     */
    private Map<String, Object> analyzeBatchIntegrity(Map<String, Object> payPropBatches, Map<String, Object> databaseBatches) {
        Map<String, Object> integrity = new HashMap<>();
        
        Integer payPropBatchCount = (Integer) payPropBatches.get("unique_batches_found");
        Integer dbBatchCount = (Integer) databaseBatches.get("unique_batches_found");
        
        integrity.put("batch_count_comparison", Map.of(
            "payprop_batches", payPropBatchCount != null ? payPropBatchCount : 0,
            "database_batches", dbBatchCount != null ? dbBatchCount : 0,
            "difference", Math.abs((payPropBatchCount != null ? payPropBatchCount : 0) - 
                                 (dbBatchCount != null ? dbBatchCount : 0))
        ));
        
        // Compare specific batches
        @SuppressWarnings("unchecked")
        Map<String, Object> payPropBatchGroups = (Map<String, Object>) payPropBatches.get("batch_groups");
        @SuppressWarnings("unchecked")
        Map<String, Object> dbBatchGroups = (Map<String, Object>) databaseBatches.get("batch_groups");
        
        if (payPropBatchGroups != null && dbBatchGroups != null) {
            Set<String> payPropBatchIds = payPropBatchGroups.keySet();
            Set<String> dbBatchIds = dbBatchGroups.keySet();
            
            Set<String> missingInDb = new HashSet<>(payPropBatchIds);
            missingInDb.removeAll(dbBatchIds);
            
            Set<String> extraInDb = new HashSet<>(dbBatchIds);
            extraInDb.removeAll(payPropBatchIds);
            
            integrity.put("batch_id_analysis", Map.of(
                "missing_in_database", missingInDb,
                "extra_in_database", extraInDb,
                "common_batches", payPropBatchIds.stream()
                    .filter(dbBatchIds::contains)
                    .collect(Collectors.toSet())
            ));
        }
        
        return integrity;
    }

    /**
     * Analyze endpoint comparison results
     */
    private Map<String, Object> analyzeEndpointComparison(Map<String, Map<String, Object>> endpointResults) {
        Map<String, Object> analysis = new HashMap<>();
        
        int totalWorkingEndpoints = 0;
        int totalFailedEndpoints = 0;
        Map<String, Integer> itemCounts = new HashMap<>();
        
        for (Map.Entry<String, Map<String, Object>> entry : endpointResults.entrySet()) {
            String endpointName = entry.getKey();
            Map<String, Object> endpointResult = entry.getValue();
            
            String status = (String) endpointResult.get("status");
            if ("SUCCESS".equals(status)) {
                totalWorkingEndpoints++;
                Integer itemCount = (Integer) endpointResult.get("item_count");
                if (itemCount != null) {
                    itemCounts.put(endpointName, itemCount);
                }
            } else {
                totalFailedEndpoints++;
            }
        }
        
        analysis.put("endpoint_health", Map.of(
            "working_endpoints", totalWorkingEndpoints,
            "failed_endpoints", totalFailedEndpoints,
            "success_rate", String.format("%.1f%%", 
                totalWorkingEndpoints * 100.0 / (totalWorkingEndpoints + totalFailedEndpoints))
        ));
        
        analysis.put("data_availability", itemCounts);
        
        // Recommend best endpoint for data
        String bestEndpoint = itemCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("None");
        
        analysis.put("recommendations", Map.of(
            "best_endpoint_for_data", bestEndpoint,
            "highest_item_count", itemCounts.getOrDefault(bestEndpoint, 0)
        ));
        
        return analysis;
    }

    /**
     * Compare API results vs Database results
     */
    private Map<String, Object> compareApiVsDatabase(Map<String, Object> apiResults, Map<String, Object> databaseResults) {
        Map<String, Object> comparison = new HashMap<>();
        
        // Count matches in each source
        int apiMatches = 0;
        int dbMatches = 0;
        
        if (apiResults.containsKey("export_payments")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> exportResults = (Map<String, Object>) apiResults.get("export_payments");
            if (exportResults.containsKey("matches_found")) {
                apiMatches += (Integer) exportResults.get("matches_found");
            }
        }
        
        if (apiResults.containsKey("report_all_payments")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> reportResults = (Map<String, Object>) apiResults.get("report_all_payments");
            if (reportResults.containsKey("matches_found")) {
                apiMatches += (Integer) reportResults.get("matches_found");
            }
        }
        
        if (databaseResults.containsKey("matches_found")) {
            dbMatches = (Integer) databaseResults.get("matches_found");
        }
        
        comparison.put("summary", Map.of(
            "api_matches_total", apiMatches,
            "database_matches", dbMatches,
            "difference", Math.abs(apiMatches - dbMatches),
            "sync_health", apiMatches == dbMatches ? "PERFECT" : "DISCREPANCY_DETECTED"
        ));
        
        // Detailed analysis
        List<String> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        
        if (apiMatches > dbMatches) {
            issues.add("PayProp has more records than database - possible sync gap");
            recommendations.add("Run a targeted sync for the missing records");
        } else if (dbMatches > apiMatches) {
            issues.add("Database has more records than PayProp - possible duplicate entries");
            recommendations.add("Check for duplicate transaction IDs in database");
        }
        
        if (apiMatches == 0 && dbMatches == 0) {
            issues.add("No records found in either source");
            recommendations.add("Check search criteria - dates, amounts, property IDs");
        }
        
        comparison.put("issues_identified", issues);
        comparison.put("recommendations", recommendations);
        
        return comparison;
    }

    /**
     * Compare transaction fields between API and database
     */
    private Map<String, Object> compareTransactionFields(Map<String, Object> apiData, FinancialTransaction dbTransaction) {
        Map<String, Object> fieldComparison = new HashMap<>();
        
        // Amount comparison
        Object apiAmount = apiData.get("amount");
        BigDecimal dbAmount = dbTransaction.getAmount();
        fieldComparison.put("amount", Map.of(
            "api_value", apiAmount,
            "database_value", dbAmount,
            "matches", apiAmount != null && dbAmount != null && 
                new BigDecimal(apiAmount.toString()).compareTo(dbAmount) == 0
        ));
        
        // Date comparison
        Map<String, Object> apiDates = extractAllDateFields(apiData);
        Map<String, Object> dbDates = Map.of(
            "transaction_date", dbTransaction.getTransactionDate(),
            "reconciliation_date", dbTransaction.getReconciliationDate(),
            "instruction_date", dbTransaction.getInstructionDate()
        );
        fieldComparison.put("dates", Map.of(
            "api_dates", apiDates,
            "database_dates", dbDates,
            "date_mapping_analysis", analyzeDateMapping(apiDates, dbDates)
        ));
        
        // Property comparison
        Map<String, Object> apiProperty = null;
        if (apiData.get("property") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> propertyMap = (Map<String, Object>) apiData.get("property");
            apiProperty = propertyMap;
        }
        
        if (apiProperty == null && apiData.get("incoming_transaction") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> incomingTransaction = (Map<String, Object>) apiData.get("incoming_transaction");
            if (incomingTransaction.get("property") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> propertyMap = (Map<String, Object>) incomingTransaction.get("property");
                apiProperty = propertyMap;
            }
        }
        
        fieldComparison.put("property", Map.of(
            "api_property_id", apiProperty != null ? apiProperty.get("id") : null,
            "api_property_name", apiProperty != null ? apiProperty.get("name") : null,
            "database_property_id", dbTransaction.getPropertyId(),
            "database_property_name", dbTransaction.getPropertyName(),
            "property_id_matches", apiProperty != null && 
                Objects.equals(apiProperty.get("id"), dbTransaction.getPropertyId()),
            "property_name_matches", apiProperty != null && 
                Objects.equals(apiProperty.get("name"), dbTransaction.getPropertyName())
        ));
        
        // Batch comparison
        Map<String, Object> batchInfo = extractBatchInfo(apiData);
        fieldComparison.put("batch", Map.of(
            "api_batch_id", batchInfo.get("batch_id"),
            "database_batch_id", dbTransaction.getPayPropBatchId(),
            "batch_id_matches", Objects.equals(batchInfo.get("batch_id"), dbTransaction.getPayPropBatchId())
        ));
        
        return fieldComparison;
    }

    /**
     * Analyze date mapping between API and database
     */
    private Map<String, Object> analyzeDateMapping(Map<String, Object> apiDates, Map<String, Object> dbDates) {
        Map<String, Object> mapping = new HashMap<>();
        
        LocalDate dbTransactionDate = (LocalDate) dbDates.get("transaction_date");
        LocalDate dbReconciliationDate = (LocalDate) dbDates.get("reconciliation_date");
        
        if (dbTransactionDate != null) {
            for (Map.Entry<String, Object> apiDate : apiDates.entrySet()) {
                if (apiDate.getValue() instanceof String) {
                    try {
                        LocalDate apiDateParsed = LocalDate.parse((String) apiDate.getValue());
                        if (apiDateParsed.equals(dbTransactionDate)) {
                            mapping.put("transaction_date_source", apiDate.getKey());
                        }
                        if (dbReconciliationDate != null && apiDateParsed.equals(dbReconciliationDate)) {
                            mapping.put("reconciliation_date_source", apiDate.getKey());
                        }
                    } catch (Exception e) {
                        // Ignore invalid dates
                    }
                }
            }
        }
        
        return mapping;
    }

    /**
     * Analyze all date fields in a transaction
     */
    private Map<String, Object> analyzeAllDateFields(Map<String, Object> transactionData) {
        Map<String, Object> analysis = new HashMap<>();
        
        Map<String, Object> allDates = extractAllDateFields(transactionData);
        analysis.put("all_date_fields", allDates);
        
        // Parse dates and analyze differences
        Map<String, LocalDate> parsedDates = new HashMap<>();
        for (Map.Entry<String, Object> entry : allDates.entrySet()) {
            if (entry.getValue() instanceof String) {
                try {
                    LocalDate date = LocalDate.parse((String) entry.getValue());
                    parsedDates.put(entry.getKey(), date);
                } catch (Exception e) {
                    // Ignore unparseable dates
                }
            }
        }
        
        analysis.put("parsed_dates", parsedDates);
        
        if (parsedDates.size() > 1) {
            LocalDate earliest = parsedDates.values().stream().min(LocalDate::compareTo).orElse(null);
            LocalDate latest = parsedDates.values().stream().max(LocalDate::compareTo).orElse(null);
            
            analysis.put("date_range_span", Map.of(
                "earliest_date", earliest,
                "latest_date", latest,
                "days_difference", earliest != null && latest != null ? 
                    java.time.temporal.ChronoUnit.DAYS.between(earliest, latest) : 0
            ));
        }
        
        return analysis;
    }

    /**
     * Extract all date fields from PayProp response
     */
    private Map<String, Object> extractAllDateFields(Map<String, Object> paymentData) {
        Map<String, Object> dates = new HashMap<>();
        
        // Direct date fields
        dates.put("due_date", paymentData.get("due_date"));
        dates.put("payment_date", paymentData.get("payment_date"));
        dates.put("reconciliation_date", paymentData.get("reconciliation_date"));
        dates.put("remittance_date", paymentData.get("remittance_date"));
        dates.put("date", paymentData.get("date"));
        dates.put("from_date", paymentData.get("from_date"));
        dates.put("to_date", paymentData.get("to_date"));
        
        // Nested date fields
        @SuppressWarnings("unchecked")
        Map<String, Object> incomingTransaction = (Map<String, Object>) paymentData.get("incoming_transaction");
        if (incomingTransaction != null) {
            dates.put("incoming_transaction.reconciliation_date", incomingTransaction.get("reconciliation_date"));
            dates.put("incoming_transaction.date", incomingTransaction.get("date"));
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> paymentBatch = (Map<String, Object>) paymentData.get("payment_batch");
        if (paymentBatch != null) {
            dates.put("payment_batch.transfer_date", paymentBatch.get("transfer_date"));
            dates.put("payment_batch.created_date", paymentBatch.get("created_date"));
        }
        
        // Remove null values
        dates.entrySet().removeIf(entry -> entry.getValue() == null || 
            (entry.getValue() instanceof String && ((String) entry.getValue()).trim().isEmpty()));
        
        return dates;
    }

    /**
     * Extract batch information from PayProp response
     */
    private Map<String, Object> extractBatchInfo(Map<String, Object> paymentData) {
        Map<String, Object> batchInfo = new HashMap<>();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> paymentBatch = (Map<String, Object>) paymentData.get("payment_batch");
        if (paymentBatch != null) {
            batchInfo.put("batch_id", paymentBatch.get("id"));
            batchInfo.put("batch_amount", paymentBatch.get("amount"));
            batchInfo.put("transfer_date", paymentBatch.get("transfer_date"));
            batchInfo.put("payment_count", paymentBatch.get("payment_count"));
            batchInfo.put("status", paymentBatch.get("status"));
        } else {
            batchInfo.put("has_batch", false);
        }
        
        return batchInfo;
    }

    /**
     * Check if PayProp payment matches search criteria
     */
    private boolean matchesSearchCriteria(Map<String, Object> payment, String amount, LocalDate fromDate, LocalDate toDate,
            String beneficiaryName, String transactionId) {
        
        // Check transaction ID
        if (transactionId != null && !transactionId.trim().isEmpty()) {
            String paymentId = (String) payment.get("id");
            return transactionId.equals(paymentId);
        }
        
        // Check amount
        if (amount != null && !amount.trim().isEmpty()) {
            try {
                BigDecimal searchAmount = new BigDecimal(amount);
                Object paymentAmount = payment.get("amount");
                if (paymentAmount != null) {
                    BigDecimal paymentAmountBD = new BigDecimal(paymentAmount.toString());
                    if (paymentAmountBD.compareTo(searchAmount) != 0) {
                        return false;
                    }
                } else {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        
        // Check beneficiary name
        if (beneficiaryName != null && !beneficiaryName.trim().isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> beneficiary = (Map<String, Object>) payment.get("beneficiary");
            if (beneficiary != null) {
                String name = (String) beneficiary.get("name");
                if (name == null || !name.toLowerCase().contains(beneficiaryName.toLowerCase())) {
                    return false;
                }
            } else {
                return false;
            }
        }
        
        // Check date range (try multiple date fields)
        if (fromDate != null && toDate != null) {
            Map<String, Object> allDates = extractAllDateFields(payment);
            boolean dateMatches = false;
            
            for (Object dateValue : allDates.values()) {
                if (dateValue instanceof String) {
                    try {
                        LocalDate date = LocalDate.parse((String) dateValue);
                        if (!date.isBefore(fromDate) && !date.isAfter(toDate)) {
                            dateMatches = true;
                            break;
                        }
                    } catch (Exception e) {
                        // Continue trying other dates
                    }
                }
            }
            
            if (!dateMatches) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Check if payment matches batch criteria
     */
    private boolean matchesBatchCriteria(Map<String, Object> payment, String batchId) {
        if (batchId == null || batchId.trim().isEmpty()) {
            return false;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> paymentBatch = (Map<String, Object>) payment.get("payment_batch");
        if (paymentBatch != null) {
            String paymentBatchId = (String) paymentBatch.get("id");
            return batchId.equals(paymentBatchId);
        }
        
        return false;
    }

    /**
     * Check advanced matching criteria
     */
    private boolean matchesAdvancedCriteria(Map<String, Object> item, String amount, String batchId) {
        // Check amount
        if (amount != null && !amount.trim().isEmpty()) {
            try {
                BigDecimal searchAmount = new BigDecimal(amount);
                Object itemAmount = item.get("amount");
                if (itemAmount != null) {
                    BigDecimal itemAmountBD = new BigDecimal(itemAmount.toString());
                    if (itemAmountBD.compareTo(searchAmount) != 0) {
                        return false;
                    }
                } else {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        
        // Check batch ID
        if (batchId != null && !batchId.trim().isEmpty()) {
            return matchesBatchCriteria(item, batchId);
        }
        
        return true;
    }

    /**
     * Check if database transaction matches criteria
     */
    private boolean matchesDatabaseCriteria(FinancialTransaction transaction, String amount, LocalDate fromDate, LocalDate toDate,
            String propertyId, String beneficiaryName, String batchId, String transactionId) {
        
        // Check transaction ID
        if (transactionId != null && !transactionId.trim().isEmpty()) {
            return transactionId.equals(transaction.getPayPropTransactionId());
        }
        
        // Check amount
        if (amount != null && !amount.trim().isEmpty()) {
            try {
                BigDecimal searchAmount = new BigDecimal(amount);
                if (transaction.getAmount() == null || transaction.getAmount().compareTo(searchAmount) != 0) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        
        // Check date range
        if (fromDate != null && toDate != null && transaction.getTransactionDate() != null) {
            if (transaction.getTransactionDate().isBefore(fromDate) || transaction.getTransactionDate().isAfter(toDate)) {
                return false;
            }
        }
        
        // Check property ID
        if (propertyId != null && !propertyId.trim().isEmpty()) {
            if (!propertyId.equals(transaction.getPropertyId())) {
                return false;
            }
        }
        
        // Check batch ID - handle both null cases properly
        if (batchId != null && !batchId.trim().isEmpty()) {
            String transactionBatchId = transaction.getPayPropBatchId();
            if (!batchId.equals(transactionBatchId)) {
                return false;
            }
        }
        
        // Check beneficiary name (in description)
        if (beneficiaryName != null && !beneficiaryName.trim().isEmpty()) {
            String description = transaction.getDescription();
            if (description == null || !description.toLowerCase().contains(beneficiaryName.toLowerCase())) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Convert FinancialTransaction to Map for JSON response
     */
    private Map<String, Object> transactionToMap(FinancialTransaction transaction) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", transaction.getId());
        map.put("payprop_transaction_id", transaction.getPayPropTransactionId());
        map.put("amount", transaction.getAmount());
        map.put("transaction_date", transaction.getTransactionDate());
        map.put("reconciliation_date", transaction.getReconciliationDate());
        map.put("property_id", transaction.getPropertyId());
        map.put("property_name", transaction.getPropertyName());
        map.put("transaction_type", transaction.getTransactionType());
        map.put("data_source", transaction.getDataSource());
        map.put("batch_id", transaction.getPayPropBatchId());
        map.put("description", transaction.getDescription());
        map.put("created_at", transaction.getCreatedAt());
        map.put("updated_at", transaction.getUpdatedAt());
        
        // Add optional fields if they exist in your entity
        try {
            map.put("is_actual_transaction", transaction.getIsActualTransaction());
        } catch (Exception e) {
            // Field might not exist in your entity
        }
        
        try {
            map.put("is_instruction", transaction.getIsInstruction());
        } catch (Exception e) {
            // Field might not exist in your entity
        }
        
        try {
            map.put("commission_amount", transaction.getCommissionAmount());
        } catch (Exception e) {
            // Field might not exist in your entity
        }
        
        try {
            map.put("service_fee_amount", transaction.getServiceFeeAmount());
        } catch (Exception e) {
            // Field might not exist in your entity
        }
        
        return map;
    }

    /**
     * Helper: Get item count from API response
     */
    private int getItemCount(Map<String, Object> responseBody) {
        if (responseBody == null) return 0;
        
        Object items = responseBody.get("items");
        if (items instanceof List) {
            return ((List<?>) items).size();
        }
        
        Object data = responseBody.get("data");
        if (data instanceof List) {
            return ((List<?>) data).size();
        }
        
        return 0;
    }

    // ===== PAGE ROUTES =====

    @GetMapping("/maintenance")
    public String showMaintenanceDashboard(Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return "redirect:/access-denied";
        }
        model.addAttribute("pageTitle", "PayProp Maintenance Dashboard");
        return "payprop/maintenance-dashboard";
    }

    @GetMapping("/test")
    public String showTestPage(Model model, Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return "redirect:/access-denied";
        }
        model.addAttribute("pageTitle", "PayProp Enhanced Test Dashboard");
        return "payprop/test";
    }

    // ===== BASIC OAUTH UTILITIES =====

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
            logger.error("❌ PayProp API test failed: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "API test failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

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
}
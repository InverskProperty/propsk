package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.service.payprop.PayPropApiClient;
import site.easy.to.build.crm.service.payprop.PayPropFinancialSyncService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced PayProp Raw Import Controller
 * 
 * Leverages proven PayPropApiClient and PayPropFinancialSyncService patterns
 * to import ALL data from ALL working PayProp endpoints with proper pagination.
 * 
 * Based on successful endpoint testing results showing 9 working endpoints.
 */
@Controller
@RequestMapping("/api/payprop/raw-import")
public class PayPropRawImportSimpleController {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawImportSimpleController.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired 
    private PayPropFinancialSyncService financialSyncService;
    
    @Autowired
    private AuthenticationUtils authenticationUtils;
    
    // List of working endpoints from our test results
    private static final Map<String, EndpointConfig> WORKING_ENDPOINTS = new HashMap<>();
    
    static {
        // Core export endpoints - CRITICAL missing data
        WORKING_ENDPOINTS.put("export-invoices", new EndpointConfig(
            "/export/invoices", 
            "Invoice instructions - THE MISSING DATA",
            Map.of("rows", "25")  // Fixed: removed invalid include_categories
        ));
        
        WORKING_ENDPOINTS.put("export-payments", new EndpointConfig(
            "/export/payments", 
            "Payment distribution rules",
            Map.of("rows", "25")  // Fixed: removed invalid include_beneficiary_info
        ));
        
        WORKING_ENDPOINTS.put("export-properties", new EndpointConfig(
            "/export/properties", 
            "Property settings and metadata",
            Map.of("include_commission", "true", "rows", "25")
        ));
        
        WORKING_ENDPOINTS.put("export-beneficiaries", new EndpointConfig(
            "/export/beneficiaries", 
            "Owner/beneficiary information",
            Map.of("owners", "true", "rows", "25")
        ));
        
        WORKING_ENDPOINTS.put("export-tenants", new EndpointConfig(
            "/export/tenants", 
            "Tenant information",
            Map.of("rows", "25")
        ));
        
        // Category endpoints
        WORKING_ENDPOINTS.put("invoices-categories", new EndpointConfig(
            "/invoices/categories", 
            "Invoice category reference data",
            Map.of()
        ));
        
        WORKING_ENDPOINTS.put("payments-categories", new EndpointConfig(
            "/payments/categories", 
            "Payment category reference data", 
            Map.of()
        ));
        
        // Report endpoints - with proper date filtering for full data
        WORKING_ENDPOINTS.put("report-all-payments", new EndpointConfig(
            "/report/all-payments", 
            "ALL payment transactions (FIXED PAGINATION)",
            createDateRangeParams("reconciliation_date")
        ));
        
        WORKING_ENDPOINTS.put("report-icdn", new EndpointConfig(
            "/report/icdn", 
            "ICDN financial transactions",
            createDateRangeParams(null)
        ));
    }
    
    private static Map<String, String> createDateRangeParams(String filterBy) {
        Map<String, String> params = new HashMap<>();
        params.put("from_date", LocalDate.now().minusYears(2).toString());
        params.put("to_date", LocalDate.now().toString());
        params.put("rows", "25");
        if (filterBy != null) {
            params.put("filter_by", filterBy);
        }
        return params;
    }
    
    /**
     * Main status and endpoint listing
     */
    @GetMapping("/status")
    @ResponseBody
    public Map<String, Object> getStatus() {
        log.info("🔍 PayProp raw import enhanced controller status check");
        
        Map<String, Object> status = new HashMap<>();
        status.put("ready", true);
        status.put("message", "Enhanced PayProp raw import system - leverages proven PayPropApiClient");
        status.put("working_endpoints", WORKING_ENDPOINTS.size());
        status.put("critical_endpoints", Arrays.asList("export-invoices", "export-payments", "report-all-payments"));
        
        Map<String, Object> endpoints = new HashMap<>();
        endpoints.put("sync_all", "/api/payprop/raw-import/sync-all-endpoints");
        endpoints.put("sync_critical", "/api/payprop/raw-import/sync-critical-missing");
        endpoints.put("test_single", "/api/payprop/raw-import/test-single/{endpoint}");
        endpoints.put("sample_data", "/api/payprop/raw-import/sample-data/{endpoint}");
        endpoints.put("fix_all_payments", "/api/payprop/raw-import/fix-all-payments-pagination");
        
        status.put("available_endpoints", endpoints);
        
        return status;
    }
    
    /**
     * Sync ALL working endpoints with proper pagination
     */
    @PostMapping("/sync-all-endpoints")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncAllWorkingEndpoints() {
        log.info("🚀 Starting sync of ALL {} working PayProp endpoints", WORKING_ENDPOINTS.size());
        
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> endpointResults = new HashMap<>();
        
        int successCount = 0;
        int errorCount = 0;
        long startTime = System.currentTimeMillis();
        
        try {
            for (Map.Entry<String, EndpointConfig> entry : WORKING_ENDPOINTS.entrySet()) {
                String endpointKey = entry.getKey();
                EndpointConfig config = entry.getValue();
                
                log.info("🔄 Syncing {} - {}", endpointKey, config.description);
                
                try {
                    Map<String, Object> result = syncSingleEndpoint(config);
                    endpointResults.put(endpointKey, result);
                    
                    if (Boolean.TRUE.equals(result.get("success"))) {
                        successCount++;
                        log.info("✅ {} sync completed: {} items", endpointKey, result.get("total_items"));
                    } else {
                        errorCount++;
                        log.error("❌ {} sync failed: {}", endpointKey, result.get("error"));
                    }
                    
                    // Rate limiting between endpoints
                    Thread.sleep(500);
                    
                } catch (Exception e) {
                    errorCount++;
                    Map<String, Object> errorResult = Map.of(
                        "success", false,
                        "error", e.getMessage(),
                        "endpoint", config.path
                    );
                    endpointResults.put(endpointKey, errorResult);
                    log.error("❌ {} sync exception: {}", endpointKey, e.getMessage());
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            response.put("success", errorCount == 0);
            response.put("message", String.format("Sync completed: %d successful, %d errors", successCount, errorCount));
            response.put("summary", Map.of(
                "total_endpoints", WORKING_ENDPOINTS.size(),
                "successful", successCount,
                "errors", errorCount,
                "duration_ms", duration
            ));
            response.put("endpoint_results", endpointResults);
            
            log.info("🎯 ALL ENDPOINTS SYNC COMPLETE: {} successful, {} errors in {}ms", 
                     successCount, errorCount, duration);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ CRITICAL: All endpoints sync failed", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Sync only the critical missing data endpoints
     */
    @PostMapping("/sync-critical-missing")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncCriticalMissingData() {
        log.info("🚨 Starting sync of CRITICAL missing data endpoints");
        
        String[] criticalEndpoints = {"export-invoices", "export-payments", "report-all-payments"};
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> results = new HashMap<>();
        
        try {
            for (String endpointKey : criticalEndpoints) {
                EndpointConfig config = WORKING_ENDPOINTS.get(endpointKey);
                if (config != null) {
                    log.info("🔄 Syncing CRITICAL: {} - {}", endpointKey, config.description);
                    Map<String, Object> result = syncSingleEndpoint(config);
                    results.put(endpointKey, result);
                    Thread.sleep(500); // Rate limiting
                }
            }
            
            response.put("success", true);
            response.put("message", "Critical missing data sync completed");
            response.put("critical_results", results);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Critical sync failed", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * FIXED: Get ALL payments with proper pagination (not just 60)
     * Uses fetchHistoricalPages to handle 93-day limit properly
     */
    @PostMapping("/fix-all-payments-pagination")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> fixAllPaymentsPagination() {
        log.info("🔧 FIXING: All-payments pagination to get ALL payments, not just 60");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("📄 Starting historical fetch of ALL payments using 93-day chunking");
            
            // FIXED: Use fetchHistoricalPages to handle 93-day limit
            // This will automatically chunk 2 years into 93-day periods
            List<Map<String, Object>> allPayments = apiClient.fetchHistoricalPages(
                "/report/all-payments?filter_by=reconciliation_date&rows=25", 
                2, // 2 years back
                (Map<String, Object> payment) -> {
                    // Simple data processing - just return the payment data
                    return Map.of(
                        "id", payment.get("id"),
                        "amount", payment.get("amount"),
                        "description", payment.get("description"),
                        "payment_batch_id", payment.get("payment_batch_id"),
                        "beneficiary_name", payment.get("beneficiary_name"),
                        "property_name", payment.getOrDefault("incoming_property_name", "N/A")
                    );
                });
            
            Map<String, Object> result = Map.of(
                "totalProcessed", allPayments.size(),
                "items", allPayments
            );
            
            response.put("success", true);
            response.put("message", "ALL payments fetched with FIXED 93-day chunking");
            response.put("all_payments_result", result);
            response.put("note", "Uses fetchHistoricalPages to properly handle PayProp's 93-day limit");
            
            log.info("🎯 ALL PAYMENTS FIXED: Retrieved {} payments total using historical chunking", 
                     result.getOrDefault("totalProcessed", 0));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ All-payments fix failed", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Get sample data from any endpoint (for testing)
     */
    @GetMapping("/sample-data/{endpointKey}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSampleData(@PathVariable String endpointKey) {
        log.info("📊 Getting sample data from endpoint: {}", endpointKey);
        
        EndpointConfig config = WORKING_ENDPOINTS.get(endpointKey);
        if (config == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Unknown endpoint: " + endpointKey,
                "available", WORKING_ENDPOINTS.keySet()
            ));
        }
        
        try {
            List<Map<String, Object>> items;
            
            // Handle report endpoints with 93-day limit
            if (config.path.startsWith("/report/") && config.parameters.containsKey("from_date")) {
                log.info("📅 Using fetchHistoricalPages for sample from report endpoint: {}", config.path);
                
                // Build base endpoint for sample - just get recent data (30 days)
                String baseEndpoint = config.path;
                if (config.parameters.containsKey("filter_by")) {
                    baseEndpoint += "?filter_by=" + config.parameters.get("filter_by");
                    baseEndpoint += "&rows=5"; // Just 5 samples
                } else {
                    baseEndpoint += "?rows=5";
                }
                
                // For samples, just get recent 93 days (minimum fetchHistoricalPages period)
                items = apiClient.fetchHistoricalPages(baseEndpoint, 1, // 1 year but will be limited by sample size
                    (Map<String, Object> item) -> item // Return raw item
                );
                
                // Limit to first 10 items for sample
                if (items.size() > 10) {
                    items = items.subList(0, 10);
                }
                
            } else {
                // Regular endpoint - get just first page for sample
                Map<String, String> sampleParams = new HashMap<>(config.parameters);
                sampleParams.put("rows", "5"); // Just 5 samples
                
                String endpointUrl = config.path;
                if (!sampleParams.isEmpty()) {
                    endpointUrl += "?" + sampleParams.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .reduce((a, b) -> a + "&" + b)
                        .orElse("");
                }
                
                items = apiClient.fetchAllPages(endpointUrl,
                    (Map<String, Object> item) -> item // Return raw item
                );
            }
            
            Map<String, Object> result = Map.of(
                "totalProcessed", items.size(),
                "items", items
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("endpoint", endpointKey);
            response.put("description", config.description);
            response.put("sample_data", result);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Sample data failed for {}: {}", endpointKey, e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage(),
                "endpoint", endpointKey
            ));
        }
    }
    
    /**
     * Test single endpoint without storing data
     */
    @PostMapping("/test-single/{endpointKey}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testSingleEndpoint(@PathVariable String endpointKey) {
        log.info("🧪 Testing single endpoint: {}", endpointKey);
        
        EndpointConfig config = WORKING_ENDPOINTS.get(endpointKey);
        if (config == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Unknown endpoint: " + endpointKey,
                "available", WORKING_ENDPOINTS.keySet()
            ));
        }
        
        try {
            Map<String, Object> result = syncSingleEndpoint(config);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("❌ Single endpoint test failed: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Use existing financial sync service (shows current behavior)
     */
    @PostMapping("/test-current-financial-sync")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testCurrentFinancialSync() {
        log.info("🔄 Testing current PayProp financial sync service");
        
        try {
            Map<String, Object> result = financialSyncService.syncComprehensiveFinancialData();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Current financial sync completed successfully");
            response.put("financial_sync_result", result);
            response.put("note", "This uses your existing working PayPropFinancialSyncService");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Current financial sync failed", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage(),
                "note", "Current financial sync failed - may indicate configuration issues"
            ));
        }
    }
    
    /**
     * Private helper to sync a single endpoint using proven patterns
     * FIXED: Uses fetchHistoricalPages for report endpoints with 93-day limit
     */
    private Map<String, Object> syncSingleEndpoint(EndpointConfig config) {
        log.debug("🔄 Syncing endpoint: {} with params: {}", config.path, config.parameters);
        
        try {
            List<Map<String, Object>> items;
            
            // FIXED: Use fetchHistoricalPages for report endpoints with date ranges
            if (config.path.startsWith("/report/") && config.parameters.containsKey("from_date")) {
                log.info("📅 Using fetchHistoricalPages for report endpoint: {}", config.path);
                
                // Build base endpoint with filter_by parameter
                String baseEndpoint = config.path;
                if (config.parameters.containsKey("filter_by")) {
                    baseEndpoint += "?filter_by=" + config.parameters.get("filter_by");
                    baseEndpoint += "&rows=" + config.parameters.getOrDefault("rows", "25");
                } else {
                    baseEndpoint += "?rows=" + config.parameters.getOrDefault("rows", "25");
                }
                
                // Use fetchHistoricalPages with 2 years back (automatically chunks into 93-day periods)
                items = apiClient.fetchHistoricalPages(baseEndpoint, 2, 
                    (Map<String, Object> item) -> {
                        // Return raw item for now
                        return item;
                    });
                    
            } else {
                // Use regular fetchAllPages for non-report endpoints
                String endpointUrl = config.path;
                if (!config.parameters.isEmpty()) {
                    endpointUrl += "?" + config.parameters.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .reduce((a, b) -> a + "&" + b)
                        .orElse("");
                }
                
                items = apiClient.fetchAllPages(endpointUrl,
                    (Map<String, Object> item) -> {
                        // Return raw item for now
                        return item;
                    });
            }
            
            Map<String, Object> result = Map.of(
                "totalProcessed", items.size(),
                "items", items
            );
            
            return Map.of(
                "success", true,
                "endpoint", config.path,
                "description", config.description,
                "total_items", items.size(),
                "summary", result
            );
            
        } catch (Exception e) {
            log.error("❌ Endpoint sync failed for {}: {}", config.path, e.getMessage());
            return Map.of(
                "success", false,
                "endpoint", config.path,
                "error", e.getMessage()
            );
        }
    }
    
    /**
     * Helper class for endpoint configuration
     */
    private static class EndpointConfig {
        final String path;
        final String description;
        final Map<String, String> parameters;
        
        EndpointConfig(String path, String description, Map<String, String> parameters) {
            this.path = path;
            this.description = description;
            this.parameters = parameters;
        }
    }
}
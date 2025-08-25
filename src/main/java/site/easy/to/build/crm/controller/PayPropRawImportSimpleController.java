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
import java.time.LocalDateTime;
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
    
    // Import cancellation control
    private volatile boolean cancelImport = false;
    private volatile String currentImportStatus = "idle";
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired 
    private PayPropFinancialSyncService financialSyncService;
    
    @Autowired
    private AuthenticationUtils authenticationUtils;
    
    @Autowired
    private site.easy.to.build.crm.service.payprop.raw.PayPropRawBeneficiariesCompleteImportService beneficiariesCompleteImportService;
    
    @Autowired
    private site.easy.to.build.crm.service.payprop.raw.PayPropRawTenantsCompleteImportService tenantsCompleteImportService;
    
    @Autowired
    private site.easy.to.build.crm.service.payprop.raw.PayPropRawPropertiesCompleteImportService propertiesCompleteImportService;
    
    @Autowired
    private site.easy.to.build.crm.service.payprop.raw.PayPropRawInvoicesCompleteImportService invoicesCompleteImportService;
    
    @Autowired
    private site.easy.to.build.crm.service.payprop.raw.PayPropRawPaymentsCompleteImportService paymentsCompleteImportService;
    
    @Autowired
    private site.easy.to.build.crm.service.payprop.raw.PayPropRawAllPaymentsImportService allPaymentsImportService;
    
    @Autowired
    private site.easy.to.build.crm.service.payprop.raw.PayPropRawIcdnImportService icdnImportService;
    
    @Autowired
    private site.easy.to.build.crm.service.payprop.raw.PayPropRawInvoiceInstructionsImportService invoiceInstructionsImportService;
    
    @Autowired
    private site.easy.to.build.crm.service.payprop.raw.PayPropRawInvoiceCategoriesImportService invoiceCategoriesImportService;
    
    @Autowired
    private site.easy.to.build.crm.service.payprop.raw.PayPropRawMaintenanceCategoriesImportService maintenanceCategoriesImportService;
    
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
        
        WORKING_ENDPOINTS.put("export-properties-archived", new EndpointConfig(
            "/export/properties", 
            "ARCHIVED Property settings and metadata",
            Map.of("include_commission", "true", "rows", "25", "is_archived", "true")
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
        
        WORKING_ENDPOINTS.put("maintenance-categories", new EndpointConfig(
            "/maintenance-categories", 
            "Maintenance category reference data", 
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
        endpoints.put("cancel_import", "/api/payprop/raw-import/cancel-import");
        endpoints.put("reset_import", "/api/payprop/raw-import/reset-import");
        
        status.put("available_endpoints", endpoints);
        status.put("import_status", currentImportStatus);
        status.put("cancel_available", !currentImportStatus.equals("idle"));
        
        return status;
    }
    
    /**
     * Cancel running import
     */
    @PostMapping("/cancel-import")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cancelImport() {
        log.warn("🛑 CANCEL REQUESTED - Stopping import");
        
        this.cancelImport = true;
        this.currentImportStatus = "cancelling";
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Import cancellation requested - will stop after current endpoint");
        response.put("status", "cancelling");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Reset import state
     */
    @PostMapping("/reset-import")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resetImport() {
        log.info("🔄 Resetting import state");
        
        this.cancelImport = false;
        this.currentImportStatus = "idle";
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Import state reset");
        response.put("status", "idle");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Sync ALL working endpoints with proper pagination
     */
    @PostMapping("/sync-all-endpoints")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncAllWorkingEndpoints() {
        log.info("🚀 Starting sync of ALL {} working PayProp endpoints", WORKING_ENDPOINTS.size());
        
        // Reset cancellation state and set status
        this.cancelImport = false;
        this.currentImportStatus = "running";
        
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> endpointResults = new HashMap<>();
        
        int successCount = 0;
        int errorCount = 0;
        int cancelledCount = 0;
        long startTime = System.currentTimeMillis();
        
        try {
            for (Map.Entry<String, EndpointConfig> entry : WORKING_ENDPOINTS.entrySet()) {
                String endpointKey = entry.getKey();
                EndpointConfig config = entry.getValue();
                
                // Check for cancellation before processing each endpoint
                if (this.cancelImport) {
                    log.warn("🛑 Import cancelled - stopping at endpoint: {}", endpointKey);
                    endpointResults.put(endpointKey, Map.of(
                        "success", false,
                        "cancelled", true,
                        "message", "Import cancelled by user"
                    ));
                    cancelledCount++;
                    break;
                }
                
                this.currentImportStatus = "running: " + endpointKey;
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
            
            boolean wasCancelled = cancelledCount > 0;
            response.put("success", errorCount == 0 && !wasCancelled);
            response.put("cancelled", wasCancelled);
            response.put("message", String.format("Sync %s: %d successful, %d errors, %d cancelled", 
                wasCancelled ? "CANCELLED" : "completed", successCount, errorCount, cancelledCount));
            response.put("summary", Map.of(
                "total_endpoints", WORKING_ENDPOINTS.size(),
                "successful", successCount,
                "errors", errorCount,
                "cancelled", cancelledCount,
                "duration_ms", duration
            ));
            response.put("endpoint_results", endpointResults);
            
            if (wasCancelled) {
                log.warn("🛑 SYNC CANCELLED: {} successful, {} errors, {} cancelled in {}ms", 
                         successCount, errorCount, cancelledCount, duration);
            } else {
                log.info("🎯 ALL ENDPOINTS SYNC COMPLETE: {} successful, {} errors in {}ms", 
                         successCount, errorCount, duration);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ CRITICAL: All endpoints sync failed", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        } finally {
            // Reset status when done
            this.currentImportStatus = "idle";
            this.cancelImport = false;
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
                    // Return raw payment data - no processing for now
                    return payment;
                });
            
            Map<String, Object> result = Map.of(
                "totalProcessed", allPayments.size(),
                "items", allPayments
            );
            
            response.put("success", true);
            response.put("message", "ALL payments fetched with FIXED 93-day chunking");
            response.put("all_payments_result", result);
            response.put("note", "Uses fetchHistoricalPages to properly handle PayProp's 93-day limit");
            
            // Add sample data structure for examination (limit response size)
            if (!allPayments.isEmpty()) {
                Map<String, Object> sampleRecord = allPayments.get(0);
                response.put("sample_record_structure", sampleRecord);
                response.put("total_fields_in_record", sampleRecord.size());
                
                // Return sample records instead of all records to prevent timeout
                int sampleSize = Math.min(10, allPayments.size());
                response.put("sample_records", allPayments.subList(0, sampleSize));
                response.put("note_about_data", "Showing " + sampleSize + " sample records. Total: " + allPayments.size());
                
                // Remove the massive items array to prevent timeout
                result = Map.of(
                    "totalProcessed", allPayments.size(),
                    "message", "Data successfully retrieved - showing samples to prevent timeout"
                );
                response.put("all_payments_result", result);
            }
            
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
     * Test all-payments with limited records to examine structure without timeout
     */
    @PostMapping("/test-limited-all-payments")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testLimitedAllPayments() {
        log.info("🧪 Testing all-payments with limited records for structure inspection");
        
        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            // Get the all-payments config
            EndpointConfig config = WORKING_ENDPOINTS.get("report-all-payments");
            if (config == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "All-payments endpoint not configured"
                ));
            }
            
            // Build endpoint with limited pages to avoid timeout
            String baseEndpoint = config.path;
            if (config.parameters.containsKey("filter_by")) {
                baseEndpoint += "?filter_by=" + config.parameters.get("filter_by");
            }
            baseEndpoint += "&rows=25"; // Standard page size
            
            log.info("📡 Fetching limited all-payments data from: {}", baseEndpoint);
            
            // Use fetchHistoricalPages but limit to just recent data (1 month back)
            // and stop after a few records to examine structure
            List<Map<String, Object>> items = new ArrayList<>();
            boolean foundData = false;
            final int maxRecords = 10; // Limit to 10 records for structure inspection
            
            // Simple approach: Test a specific 3-month period from 2024 (safe 90-day range)
            try {
                // Use Q2 2024: April 1 to June 30, 2024 (exactly 90 days - under the 93-day limit)
                String startDate = "2024-04-01";
                String endDate = "2024-06-30";
                
                String endpoint = baseEndpoint + 
                    "&from_date=" + startDate +
                    "&to_date=" + endDate;
                
                log.info("🔍 Limited fetch from safe 90-day period: {} to {}", startDate, endDate);
                log.info("🔍 Testing endpoint: {}", endpoint);
                
                // Use fetchAllPages with a safe date range (90 days < 93 day limit)
                List<Map<String, Object>> allItems = apiClient.fetchAllPages(endpoint,
                    (Map<String, Object> item) -> item // Return raw item
                );
                
                log.info("📊 Found {} total items from Q2 2024", allItems.size());
                
                // Limit to maxRecords for structure inspection
                items = allItems.stream()
                    .limit(maxRecords)
                    .collect(Collectors.toList());
                
                foundData = !items.isEmpty();
                
                if (foundData) {
                    response.put("totalItemsAvailable", allItems.size());
                    response.put("testPeriod", "Q2 2024 (April-June)");
                    response.put("dateRange", startDate + " to " + endDate);
                    response.put("daysCovered", "90 days (under 93-day limit)");
                } else {
                    response.put("dataAvailabilityWarning", "No payment records found in Q2 2024 period");
                    response.put("testPeriod", "Q2 2024 (April-June)");
                    response.put("dateRange", startDate + " to " + endDate);
                }
                
            } catch (Exception e) {
                log.warn("⚠️ Q2 2024 fetch failed: {}", e.getMessage());
                response.put("fetchError", e.getMessage());
            }
            
            long endTime = System.currentTimeMillis();
            long processingTime = endTime - startTime;
            
            // Analyze the structure
            Map<String, Object> fieldAnalysis = analyzeRecordStructure(items);
            String nestedWarning = checkForNestedStructures(items);
            
            response.put("success", true);
            response.put("totalRecords", "Limited test - not full count");
            response.put("limitedCount", maxRecords);
            response.put("actualRetrieved", items.size());
            response.put("processingTime", processingTime);
            response.put("fieldsPerRecord", items.isEmpty() ? 0 : items.get(0).size());
            response.put("sampleRecords", items.size() > 3 ? items.subList(0, 3) : items); // Show max 3 for display
            response.put("fieldAnalysis", fieldAnalysis);
            
            // Add diagnostic information
            response.put("endpointTested", baseEndpoint);
            response.put("dataFound", foundData);
            
            if (!foundData) {
                response.put("troubleshooting", Map.of(
                    "issue", "No payment data found in staging environment",
                    "possibleCauses", List.of(
                        "PayProp staging environment may not have recent payment data",
                        "Different date field might be needed (try 'payment_date' instead of 'reconciliation_date')",
                        "Staging data might be older than tested date ranges",
                        "API permissions might not include payment data access"
                    ),
                    "suggestedActions", List.of(
                        "Check if production environment has data",
                        "Try the regular 'Fix All-Payments' button to test historical chunking",
                        "Contact PayProp support about staging data availability"
                    )
                ));
            }
            
            if (nestedWarning != null) {
                response.put("nestedStructureWarning", nestedWarning);
            }
            
            log.info("🎯 LIMITED ALL-PAYMENTS TEST: Retrieved {} records in {}ms for structure inspection", 
                     items.size(), processingTime);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("❌ Limited all-payments test failed", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("details", "Failed during limited structure test");
            response.put("processingTime", endTime - startTime);
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Analyze record structure for nested objects and field types
     */
    private Map<String, Object> analyzeRecordStructure(List<Map<String, Object>> items) {
        if (items.isEmpty()) {
            return Map.of("status", "No records to analyze");
        }
        
        Map<String, Object> analysis = new HashMap<>();
        Map<String, Object> firstRecord = items.get(0);
        
        Map<String, String> fieldTypes = new HashMap<>();
        Map<String, Boolean> hasNestedStructure = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : firstRecord.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();
            
            if (value == null) {
                fieldTypes.put(fieldName, "null");
                hasNestedStructure.put(fieldName, false);
            } else if (value instanceof Map) {
                fieldTypes.put(fieldName, "nested_object");
                hasNestedStructure.put(fieldName, true);
            } else if (value instanceof List) {
                fieldTypes.put(fieldName, "array");
                hasNestedStructure.put(fieldName, true);
            } else {
                fieldTypes.put(fieldName, value.getClass().getSimpleName());
                hasNestedStructure.put(fieldName, false);
            }
        }
        
        analysis.put("totalFields", firstRecord.size());
        analysis.put("fieldTypes", fieldTypes);
        analysis.put("nestedFields", hasNestedStructure.entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList()));
        
        return analysis;
    }
    
    /**
     * Check for complex nested structures that might need flattening
     */
    private String checkForNestedStructures(List<Map<String, Object>> items) {
        if (items.isEmpty()) return null;
        
        Map<String, Object> firstRecord = items.get(0);
        List<String> nestedFields = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : firstRecord.entrySet()) {
            if (entry.getValue() instanceof Map || entry.getValue() instanceof List) {
                nestedFields.add(entry.getKey());
            }
        }
        
        if (!nestedFields.isEmpty()) {
            return String.format("Found %d nested fields: %s. These may need flattening for database storage.", 
                nestedFields.size(), String.join(", ", nestedFields));
        }
        
        return null;
    }
    
    /**
     * Test complete beneficiaries import with new table structure - PROOF OF CONCEPT
     */
    @PostMapping("/test-complete-beneficiaries")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testCompleteBeneficiariesImport() {
        log.info("🏦 Testing complete beneficiaries import - PROOF OF CONCEPT");
        
        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            // Call the new complete import service
            site.easy.to.build.crm.service.payprop.raw.PayPropRawImportResult result = 
                beneficiariesCompleteImportService.importBeneficiariesComplete();
            
            long endTime = System.currentTimeMillis();
            long processingTime = endTime - startTime;
            
            response.put("success", result.isSuccess());
            response.put("endpoint", result.getEndpoint());
            response.put("totalFetched", result.getTotalFetched());
            response.put("totalImported", result.getTotalImported());
            response.put("processingTime", processingTime);
            response.put("details", result.getDetails());
            
            if (!result.isSuccess()) {
                response.put("error", result.getErrorMessage());
            }
            
            log.info("🎯 PROOF OF CONCEPT COMPLETE: {} beneficiaries imported to complete table in {}ms", 
                     result.getTotalImported(), processingTime);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("❌ Complete beneficiaries import test failed", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("processingTime", endTime - startTime);
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Test complete tenants import with new table structure
     */
    @PostMapping("/test-complete-tenants")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testCompleteTenantsImport() {
        log.info("👥 Testing complete tenants import");
        
        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            // Call the new complete import service
            site.easy.to.build.crm.service.payprop.raw.PayPropRawImportResult result = 
                tenantsCompleteImportService.importTenantsComplete();
            
            long endTime = System.currentTimeMillis();
            long processingTime = endTime - startTime;
            
            response.put("success", result.isSuccess());
            response.put("endpoint", result.getEndpoint());
            response.put("totalFetched", result.getTotalFetched());
            response.put("totalImported", result.getTotalImported());
            response.put("processingTime", processingTime);
            response.put("details", result.getDetails());
            
            if (!result.isSuccess()) {
                response.put("error", result.getErrorMessage());
            }
            
            log.info("🎯 TENANTS COMPLETE: {} tenants imported to complete table in {}ms", 
                     result.getTotalImported(), processingTime);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("❌ Complete tenants import test failed", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("processingTime", endTime - startTime);
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Test complete properties import - NEW ENDPOINT 
     * Uses the proven pattern to import properties and resolve tenant foreign key constraints
     */
    @PostMapping("/test-complete-properties")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testCompletePropertiesImport() {
        log.info("🏠 Testing complete properties import - resolves tenant foreign key constraints");
        
        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            // Call the new complete import service
            site.easy.to.build.crm.service.payprop.raw.PayPropRawImportResult result = 
                propertiesCompleteImportService.importPropertiesComplete();
            
            long endTime = System.currentTimeMillis();
            long processingTime = endTime - startTime;
            
            response.put("success", result.isSuccess());
            response.put("endpoint", result.getEndpoint());
            response.put("totalFetched", result.getTotalFetched());
            response.put("totalImported", result.getTotalImported());
            response.put("processingTime", processingTime);
            response.put("details", result.getDetails());
            
            if (!result.isSuccess()) {
                response.put("error", result.getErrorMessage());
            }
            
            log.info("🎯 PROPERTIES COMPLETE: {} properties imported to complete table in {}ms", 
                     result.getTotalImported(), processingTime);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("❌ Complete properties import test failed", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("processingTime", endTime - startTime);
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Test complete invoices import - CRITICAL MISSING DATA 
     * Source of £1,075 rent amount - resolves business logic discrepancies
     */
    @PostMapping("/test-complete-invoices")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testCompleteInvoicesImport() {
        log.info("💰 Testing complete invoices import - CRITICAL £1,075 rent data");
        
        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            // Call the new complete import service
            site.easy.to.build.crm.service.payprop.raw.PayPropRawImportResult result = 
                invoicesCompleteImportService.importInvoicesComplete();
            
            long endTime = System.currentTimeMillis();
            long processingTime = endTime - startTime;
            
            response.put("success", result.isSuccess());
            response.put("endpoint", result.getEndpoint());
            response.put("totalFetched", result.getTotalFetched());
            response.put("totalImported", result.getTotalImported());
            response.put("processingTime", processingTime);
            response.put("details", result.getDetails());
            
            if (!result.isSuccess()) {
                response.put("error", result.getErrorMessage());
            }
            
            log.info("🎯 INVOICES COMPLETE: {} invoices imported to complete table in {}ms", 
                     result.getTotalImported(), processingTime);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("❌ Complete invoices import test failed", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("processingTime", endTime - startTime);
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Test complete payments import - Transaction data
     * Source of actual payment amounts and transaction history
     */
    @PostMapping("/test-complete-payments")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testCompletePaymentsImport() {
        log.info("💳 Testing complete payments import - Transaction data");
        
        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            // Call the new complete import service
            site.easy.to.build.crm.service.payprop.raw.PayPropRawImportResult result = 
                paymentsCompleteImportService.importPaymentsComplete();
            
            long endTime = System.currentTimeMillis();
            long processingTime = endTime - startTime;
            
            // Build response
            response.put("success", result.isSuccess());
            response.put("endpoint", result.getEndpoint());
            response.put("totalFetched", result.getTotalFetched());
            response.put("totalImported", result.getTotalImported());
            response.put("details", result.getDetails());
            response.put("processingTime", processingTime);
            response.put("startTime", result.getStartTime());
            response.put("endTime", result.getEndTime());
            
            if (!result.isSuccess()) {
                response.put("error", result.getErrorMessage());
                return ResponseEntity.status(500).body(response);
            }
            
            log.info("✅ Complete payments import test completed successfully in {}ms", processingTime);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("❌ Complete payments import test failed", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("processingTime", endTime - startTime);
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Test complete all-payments import - 7,414+ transaction records
     * Source of historical payment transaction data with 93-day chunking
     */
    @PostMapping("/test-complete-all-payments")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testCompleteAllPaymentsImport() {
        log.info("💰 Testing complete all-payments import - 7,414+ transaction records with historical chunking");
        
        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            // Call the all payments import service
            site.easy.to.build.crm.service.payprop.raw.PayPropRawImportResult result = 
                allPaymentsImportService.importAllPayments();
            
            long endTime = System.currentTimeMillis();
            long processingTime = endTime - startTime;
            
            // Build response
            response.put("success", result.isSuccess());
            response.put("endpoint", result.getEndpoint());
            response.put("totalFetched", result.getTotalFetched());
            response.put("totalImported", result.getTotalImported());
            response.put("details", result.getDetails());
            response.put("processingTime", processingTime);
            response.put("startTime", result.getStartTime());
            response.put("endTime", result.getEndTime());
            
            if (!result.isSuccess()) {
                response.put("error", result.getErrorMessage());
                return ResponseEntity.status(500).body(response);
            }
            
            log.info("✅ Complete all-payments import test completed: {} transactions imported in {}ms", 
                     result.getTotalImported(), processingTime);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("❌ Complete all-payments import test failed", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("processingTime", endTime - startTime);
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Test complete ICDN import - Invoice, Credit, Debit Notes data
     * Source of missing payment_instruction_id references for payment transactions
     */
    @PostMapping("/test-complete-icdn")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testCompleteIcdnImport() {
        log.info("📄 Testing complete ICDN import - Invoice, Credit, Debit Notes data");
        
        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            // Call the ICDN import service
            site.easy.to.build.crm.service.payprop.raw.PayPropRawImportResult result = 
                icdnImportService.importIcdnComplete();
            
            long endTime = System.currentTimeMillis();
            long processingTime = endTime - startTime;
            
            // Build response
            response.put("success", result.isSuccess());
            response.put("endpoint", result.getEndpoint());
            response.put("totalFetched", result.getTotalFetched());
            response.put("totalImported", result.getTotalImported());
            response.put("details", result.getDetails());
            response.put("processingTime", processingTime);
            response.put("startTime", result.getStartTime());
            response.put("endTime", result.getEndTime());
            
            if (!result.isSuccess()) {
                response.put("error", result.getErrorMessage());
                return ResponseEntity.status(500).body(response);
            }
            
            log.info("✅ Complete ICDN import test completed: {} records imported in {}ms", 
                     result.getTotalImported(), processingTime);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("❌ Complete ICDN import test failed", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("processingTime", endTime - startTime);
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Test complete invoice instructions import - THE MISSING LINK for payment references
     */
    @PostMapping("/test-complete-invoice-instructions")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testCompleteInvoiceInstructionsImport() {
        log.info("🔗 Testing complete invoice instructions import - THE MISSING PAYMENT INSTRUCTION IDs!");
        
        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            site.easy.to.build.crm.service.payprop.raw.PayPropRawImportResult result = 
                invoiceInstructionsImportService.importAllInvoiceInstructions();
            
            long endTime = System.currentTimeMillis();
            
            response.put("success", result.isSuccess());
            response.put("endpoint", result.getEndpoint());
            response.put("totalFetched", result.getTotalFetched());
            response.put("totalImported", result.getTotalImported());
            response.put("details", result.getDetails());
            response.put("startTime", result.getStartTime());
            response.put("endTime", result.getEndTime());
            response.put("processingTime", endTime - startTime);
            
            if (!result.isSuccess()) {
                response.put("error", result.getErrorMessage());
                return ResponseEntity.status(500).body(response);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("❌ Complete invoice instructions import test failed", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("processingTime", endTime - startTime);
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Test complete invoice categories import - Reference data for invoice categorization
     */
    @PostMapping("/test-complete-invoice-categories")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testCompleteInvoiceCategoriesImport() {
        log.info("📋 Testing complete invoice categories import - Reference data for invoice categorization");
        
        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            site.easy.to.build.crm.service.payprop.raw.PayPropRawImportResult result = 
                invoiceCategoriesImportService.importAllInvoiceCategories();
            
            long endTime = System.currentTimeMillis();
            
            response.put("success", result.isSuccess());
            response.put("endpoint", result.getEndpoint());
            response.put("totalFetched", result.getTotalFetched());
            response.put("totalImported", result.getTotalImported());
            response.put("details", result.getDetails());
            response.put("startTime", result.getStartTime());
            response.put("endTime", result.getEndTime());
            response.put("processingTime", endTime - startTime);
            
            if (!result.isSuccess()) {
                response.put("error", result.getErrorMessage());
                return ResponseEntity.status(500).body(response);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("❌ Complete invoice categories import test failed", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("processingTime", endTime - startTime);
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Test complete maintenance categories import - Reference data for maintenance categorization
     */
    @PostMapping("/test-complete-maintenance-categories")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testCompleteMaintenanceCategoriesImport() {
        log.info("🔧 Testing complete maintenance categories import - Reference data for maintenance categorization");
        
        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            site.easy.to.build.crm.service.payprop.raw.PayPropRawImportResult result = 
                maintenanceCategoriesImportService.importAllMaintenanceCategories();
            
            long endTime = System.currentTimeMillis();
            
            response.put("success", result.isSuccess());
            response.put("endpoint", result.getEndpoint());
            response.put("totalFetched", result.getTotalFetched());
            response.put("totalImported", result.getTotalImported());
            response.put("details", result.getDetails());
            response.put("startTime", result.getStartTime());
            response.put("endTime", result.getEndTime());
            response.put("processingTime", endTime - startTime);
            
            if (!result.isSuccess()) {
                response.put("error", result.getErrorMessage());
                return ResponseEntity.status(500).body(response);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("❌ Complete maintenance categories import test failed", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("processingTime", endTime - startTime);
            return ResponseEntity.status(500).body(response);
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
// BatchPaymentTestController.java - Complete enhanced version for batch payment investigation
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.service.payprop.PayPropSyncService;
import site.easy.to.build.crm.service.payprop.PayPropSyncService.PayPropExportResult;
import site.easy.to.build.crm.service.payprop.PayPropOAuth2Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Enhanced test controller to investigate PayProp batch payment functionality
 */
@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@RestController
@RequestMapping("/api/payprop/test")
public class BatchPaymentTestController {

    private static final Logger log = LoggerFactory.getLogger(BatchPaymentTestController.class);

    @Autowired
    private PayPropSyncService payPropSyncService;
    
    @Autowired
    private PayPropOAuth2Service oAuth2Service;

    @Autowired 
    private RestTemplate restTemplate;

    /**
     * Test endpoint to check all payments for a specific date range
     * This will help us see if batch IDs are included in the response
     */
    @GetMapping("/payments")
    public ResponseEntity<Map<String, Object>> testPaymentsEndpoint(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {
        
        try {
            // Default to recent dates if not provided
            if (fromDate == null) {
                fromDate = LocalDate.now().minusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE);
            }
            if (toDate == null) {
                toDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            }

            log.info("Testing PayProp payments endpoint for date range: {} to {}", fromDate, toDate);

            // Use the existing exportPaymentsFromPayProp method
            PayPropExportResult result = payPropSyncService.exportPaymentsFromPayProp(1, 25);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "dateRange", fromDate + " to " + toDate,
                "payPropResponse", Map.of(
                    "items", result.getItems(),
                    "pagination", result.getPagination()
                ),
                "note", "Check the response structure for batch payment information"
            ));

        } catch (Exception e) {
            log.error("Error testing PayProp payments endpoint: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage(),
                "suggestion", "Check PayProp API connection and permissions"
            ));
        }
    }

    /**
     * Test endpoint to check actual payments for batch information
     */
    @GetMapping("/actual-payments")
    public ResponseEntity<Map<String, Object>> testActualPaymentsEndpoint(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer rows) {
        
        try {
            if (page == null) page = 1;
            if (rows == null) rows = 25;

            log.info("Testing PayProp actual payments endpoint - page {}, rows {}", page, rows);

            // Use the existing exportActualPaymentsFromPayProp method
            PayPropExportResult result = payPropSyncService.exportActualPaymentsFromPayProp(page, rows);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "page", page,
                "rows", rows,
                "payPropResponse", Map.of(
                    "items", result.getItems(),
                    "pagination", result.getPagination(),
                    "itemCount", result.getItems().size()
                ),
                "note", "These are actual payment transactions with amounts and dates"
            ));

        } catch (Exception e) {
            log.error("Error testing PayProp actual payments endpoint: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage(),
                "suggestion", "Check PayProp API connection and permissions"
            ));
        }
    }

    /**
     * NEW: Test the /report/all-payments endpoint directly - this should contain actual transactions
     */
    @GetMapping("/report-all-payments")
    public ResponseEntity<Map<String, Object>> testReportAllPaymentsEndpoint(
            @RequestParam(required = false) String propertyId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String paymentBatchId) {
        
        try {
            // Default dates if not provided
            LocalDate from = fromDate != null ? LocalDate.parse(fromDate) : LocalDate.now().minusMonths(6);
            LocalDate to = toDate != null ? LocalDate.parse(toDate) : LocalDate.now();

            log.info("Testing PayProp /report/all-payments endpoint for property {} ({} to {})", propertyId, from, to);

            // Build URL for direct API call
            String baseUrl = "https://ukapi.staging.payprop.com/api/agency/v1.1/report/all-payments";
            StringBuilder urlBuilder = new StringBuilder(baseUrl);
            urlBuilder.append("?from_date=").append(from.toString());
            urlBuilder.append("&to_date=").append(to.toString());
            urlBuilder.append("&filter_by=reconciliation_date");
            urlBuilder.append("&include_beneficiary_info=true");
            
            if (propertyId != null && !propertyId.isEmpty()) {
                urlBuilder.append("&property_id=").append(propertyId);
            }
            
            if (paymentBatchId != null && !paymentBatchId.isEmpty()) {
                urlBuilder.append("&payment_batch_id=").append(paymentBatchId);
            }

            // Make direct API call using OAuth2 service
            if (!oAuth2Service.hasValidTokens()) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "PayProp not authorized. Please authorize first.",
                    "endpoint", "/report/all-payments"
                ));
            }

            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String finalUrl = urlBuilder.toString();
            log.info("Calling PayProp API: {}", finalUrl);
            
            ResponseEntity<Map> apiResponse = restTemplate.exchange(finalUrl, HttpMethod.GET, request, Map.class);
            
            if (apiResponse.getStatusCode().is2xxSuccessful() && apiResponse.getBody() != null) {
                Map<String, Object> responseBody = apiResponse.getBody();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> payments = (List<Map<String, Object>>) responseBody.get("items");
                
                Map<String, Object> analysis = analyzeBatchDataInPayments(payments);
                
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "endpoint", "/report/all-payments",
                    "url", finalUrl,
                    "totalItems", payments != null ? payments.size() : 0,
                    "dateRange", from + " to " + to,
                    "propertyId", propertyId != null ? propertyId : "all",
                    "paymentBatchId", paymentBatchId != null ? paymentBatchId : "all",
                    "payPropResponse", responseBody,
                    "batchAnalysis", analysis,
                    "note", "This should contain actual payment transactions with batch information"
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "API returned status: " + apiResponse.getStatusCode(),
                    "endpoint", "/report/all-payments",
                    "url", finalUrl,
                    "note", "This endpoint may require additional permissions"
                ));
            }

        } catch (Exception e) {
            log.error("Error testing /report/all-payments endpoint: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage(),
                "endpoint", "/report/all-payments",
                "suggestion", "Check if you have 'Read: All payments report' permission in PayProp"
            ));
        }
    }

    /**
     * NEW: Test various API versions and endpoints to find the correct payment data
     */
    @GetMapping("/discover-endpoints")
    public ResponseEntity<Map<String, Object>> discoverPaymentEndpoints() {
        Map<String, Object> results = new HashMap<>();
        
        if (!oAuth2Service.hasValidTokens()) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", "PayProp not authorized. Please authorize first."
            ));
        }
        
        String[] endpointsToTest = {
            "/report/all-payments",
            "/export/payments?filter_by=reconciliation_date",
            "/export/actual-payments", 
            "/payments/transactions",
            "/payments/actual",
            "/export/payment-transactions",
            "/report/payment-transactions"
        };
        
        String[] apiVersions = {"v1.0", "v1.1", "v1.2"};
        
        for (String version : apiVersions) {
            Map<String, Object> versionResults = new HashMap<>();
            
            for (String endpoint : endpointsToTest) {
                try {
                    String testUrl = "https://ukapi.staging.payprop.com/api/agency/" + version + endpoint;
                    if (!endpoint.contains("?")) {
                        testUrl += "?page=1&rows=1"; // Minimal test
                    } else {
                        testUrl += "&page=1&rows=1";
                    }
                    
                    // Quick test call
                    HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
                    HttpEntity<String> request = new HttpEntity<>(headers);
                    
                    ResponseEntity<Map> response = restTemplate.exchange(testUrl, HttpMethod.GET, request, Map.class);
                    
                    Map<String, Object> endpointResult = new HashMap<>();
                    endpointResult.put("status", response.getStatusCode().value());
                    endpointResult.put("accessible", response.getStatusCode().is2xxSuccessful());
                    
                    if (response.getBody() != null) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
                        endpointResult.put("hasData", items != null && !items.isEmpty());
                        endpointResult.put("itemCount", items != null ? items.size() : 0);
                        
                        if (items != null && !items.isEmpty()) {
                            endpointResult.put("sampleFields", items.get(0).keySet());
                            endpointResult.put("hasBatchFields", hasBatchFields(items.get(0)));
                        }
                    }
                    
                    versionResults.put(endpoint, endpointResult);
                    
                } catch (Exception e) {
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("accessible", false);
                    errorResult.put("error", e.getMessage());
                    versionResults.put(endpoint, errorResult);
                }
            }
            
            results.put(version, versionResults);
        }
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Endpoint discovery completed",
            "results", results,
            "note", "Check which endpoints return 200 status and have data"
        ));
    }

    /**
     * NEW: Test specific batch payment ID if we have one
     */
    @GetMapping("/test-batch-id")
    public ResponseEntity<Map<String, Object>> testSpecificBatchId(@RequestParam String batchId) {
        try {
            if (!oAuth2Service.hasValidTokens()) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "PayProp not authorized. Please authorize first.",
                    "batchId", batchId
                ));
            }

            String url = "https://ukapi.staging.payprop.com/api/agency/v1.1/report/all-payments" +
                    "?payment_batch_id=" + batchId +
                    "&include_beneficiary_info=true";
            
            HttpHeaders headers = oAuth2Service.createAuthorizedHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "batchId", batchId,
                "url", url,
                "status", response.getStatusCode().value(),
                "response", response.getBody() != null ? response.getBody() : "No response body"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "batchId", batchId,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Test endpoint to get all payments report for a property
     */
    @GetMapping("/property-payments")
    public ResponseEntity<Map<String, Object>> testPropertyPaymentsReport(
            @RequestParam String propertyId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {
        
        try {
            // Default dates if not provided
            LocalDate from = fromDate != null ? LocalDate.parse(fromDate) : LocalDate.now().minusMonths(3);
            LocalDate to = toDate != null ? LocalDate.parse(toDate) : LocalDate.now();

            log.info("Testing PayProp all-payments report for property {} ({} to {})", propertyId, from, to);

            // Use the existing exportAllPaymentsReportFromPayProp method
            PayPropExportResult result = payPropSyncService.exportAllPaymentsReportFromPayProp(propertyId, from, to);

            Map<String, Object> analysis = analyzePaymentData(result);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "propertyId", propertyId,
                "dateRange", from + " to " + to,
                "payPropResponse", Map.of(
                    "items", result.getItems(),
                    "pagination", result.getPagination(),
                    "itemCount", result.getItems().size()
                ),
                "analysis", analysis,
                "note", "This is the comprehensive payments report for the property"
            ));

        } catch (Exception e) {
            log.error("Error testing property payments report: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage(),
                "propertyId", propertyId,
                "suggestion", "Check if the property ID exists and you have permission to access it"
            ));
        }
    }

    /**
     * Test endpoint to check payment categories
     */
    @GetMapping("/payment-categories")
    public ResponseEntity<Map<String, Object>> testPaymentCategories() {
        try {
            log.info("Testing PayProp payment categories sync");

            // Use the existing syncPaymentCategoriesFromPayProp method
            var result = payPropSyncService.syncPaymentCategoriesFromPayProp();

            return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage(),
                "details", result.getDetails(),
                "note", "Payment categories have been synced to your database"
            ));

        } catch (Exception e) {
            log.error("Error testing payment categories: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage(),
                "suggestion", "Check PayProp API connection and permissions"
            ));
        }
    }

    /**
     * Test property statistics
     */
    @GetMapping("/property-stats")
    public ResponseEntity<Map<String, Object>> testPropertyStatistics() {
        try {
            log.info("Getting property statistics from PayProp");

            Map<String, Object> stats = payPropSyncService.getPropertyStatistics();

            return ResponseEntity.ok(Map.of(
                "success", true,
                "statistics", stats,
                "note", "Comprehensive property statistics including rent and occupancy data"
            ));

        } catch (Exception e) {
            log.error("Error getting property statistics: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * NEW: Analyze payment data specifically for batch information
     */
    private Map<String, Object> analyzeBatchDataInPayments(List<Map<String, Object>> payments) {
        Map<String, Object> analysis = new HashMap<>();
        
        if (payments == null || payments.isEmpty()) {
            analysis.put("hasData", false);
            analysis.put("message", "No payment data to analyze");
            return analysis;
        }

        analysis.put("hasData", true);
        analysis.put("totalRecords", payments.size());

        // Look for batch-related fields
        Set<String> batchFields = new HashSet<>();
        Set<String> allBatchIds = new HashSet<>();
        Map<String, Integer> batchCounts = new HashMap<>();
        
        for (Map<String, Object> payment : payments) {
            // Check for various batch field names
            String[] possibleBatchFields = {
                "batch_id", "payment_batch_id", "batchId", "batch", 
                "group_id", "groupId", "payment_group_id",
                "transaction_batch_id", "remittance_batch_id"
            };
            
            for (String fieldName : possibleBatchFields) {
                if (payment.containsKey(fieldName)) {
                    Object value = payment.get(fieldName);
                    if (value != null && !"null".equals(String.valueOf(value))) {
                        batchFields.add(fieldName);
                        String batchId = String.valueOf(value);
                        allBatchIds.add(batchId);
                        batchCounts.put(batchId, batchCounts.getOrDefault(batchId, 0) + 1);
                    }
                }
            }
        }
        
        analysis.put("batchFieldsFound", batchFields);
        analysis.put("uniqueBatchIds", allBatchIds);
        analysis.put("batchCounts", batchCounts);
        analysis.put("hasBatchData", !batchFields.isEmpty());
        
        // Show all available fields from first payment
        if (!payments.isEmpty()) {
            analysis.put("allAvailableFields", payments.get(0).keySet());
            analysis.put("samplePayment", payments.get(0));
        }
        
        return analysis;
    }

    /**
     * Analyze payment data to find batch information (original method)
     */
    private Map<String, Object> analyzePaymentData(PayPropExportResult result) {
        Map<String, Object> analysis = new HashMap<>();
        
        if (result.getItems() == null || result.getItems().isEmpty()) {
            analysis.put("hasData", false);
            analysis.put("message", "No payment data found");
            return analysis;
        }

        analysis.put("hasData", true);
        analysis.put("totalRecords", result.getItems().size());

        // Check for batch-related fields
        Map<String, Object> firstItem = result.getItems().get(0);
        boolean hasBatchId = firstItem.containsKey("batch_id") || 
                            firstItem.containsKey("payment_batch_id") ||
                            firstItem.containsKey("batch");
        
        analysis.put("hasBatchField", hasBatchId);
        
        if (hasBatchId) {
            // Count unique batch IDs
            Map<String, Integer> batchCounts = new HashMap<>();
            for (Map<String, Object> item : result.getItems()) {
                String batchId = null;
                if (item.containsKey("batch_id")) {
                    batchId = String.valueOf(item.get("batch_id"));
                } else if (item.containsKey("payment_batch_id")) {
                    batchId = String.valueOf(item.get("payment_batch_id"));
                } else if (item.containsKey("batch")) {
                    batchId = String.valueOf(item.get("batch"));
                }
                
                if (batchId != null && !"null".equals(batchId)) {
                    batchCounts.put(batchId, batchCounts.getOrDefault(batchId, 0) + 1);
                }
            }
            
            analysis.put("uniqueBatchIds", batchCounts.size());
            analysis.put("batchCounts", batchCounts);
        }

        // List all fields from first item
        analysis.put("availableFields", firstItem.keySet());

        return analysis;
    }

    /**
     * NEW: Helper method to check if a payment object has batch-related fields
     */
    private boolean hasBatchFields(Map<String, Object> payment) {
        String[] batchFieldNames = {
            "batch_id", "payment_batch_id", "batchId", "batch", 
            "group_id", "groupId", "payment_group_id"
        };
        
        for (String fieldName : batchFieldNames) {
            if (payment.containsKey(fieldName)) {
                Object value = payment.get(fieldName);
                if (value != null && !"null".equals(String.valueOf(value))) {
                    return true;
                }
            }
        }
        return false;
    }
}
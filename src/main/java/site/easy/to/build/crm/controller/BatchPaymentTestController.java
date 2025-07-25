// BatchPaymentTestController.java - Test controller to investigate batch payments
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.service.payprop.PayPropSyncService;
import site.easy.to.build.crm.service.payprop.PayPropSyncService.PayPropExportResult;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.HashMap;

/**
 * Test controller to investigate PayProp batch payment functionality
 */
@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@RestController
@RequestMapping("/api/payprop/test")
public class BatchPaymentTestController {

    private static final Logger log = LoggerFactory.getLogger(BatchPaymentTestController.class);

    @Autowired
    private PayPropSyncService payPropSyncService;

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
     * Analyze payment data to find batch information
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
}
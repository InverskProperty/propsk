// BatchPaymentTestController.java - Test controller to investigate batch payments
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.service.payprop.PayPropSyncService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

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

            log.info("Testing PayProp all-payments endpoint for date range: {} to {}", fromDate, toDate);

            // Call PayProp all-payments report endpoint
            String url = "/report/all-payments?from_date=" + fromDate + "&to_date=" + toDate;
            Map<String, Object> response = payPropSyncService.makePayPropApiCall(url, Map.class);

            log.info("PayProp all-payments response structure: {}", 
                response.keySet().toString());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "dateRange", fromDate + " to " + toDate,
                "payPropResponse", response,
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
     * Test endpoint to check payments export for batch information
     */
    @GetMapping("/payments-export")
    public ResponseEntity<Map<String, Object>> testPaymentsExportEndpoint(
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

            log.info("Testing PayProp payments export endpoint for date range: {} to {}", fromDate, toDate);

            // Call PayProp payments export endpoint
            String url = "/export/payments?from_date=" + fromDate + "&to_date=" + toDate + "&rows=50";
            Map<String, Object> response = payPropSyncService.makePayPropApiCall(url, Map.class);

            log.info("PayProp payments export response structure: {}", 
                response.keySet().toString());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "dateRange", fromDate + " to " + toDate,
                "payPropResponse", response,
                "note", "Check the 'items' array for individual payment records and look for batch_id fields"
            ));

        } catch (Exception e) {
            log.error("Error testing PayProp payments export endpoint: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage(),
                "suggestion", "Check PayProp API connection and permissions"
            ));
        }
    }

    /**
     * Test endpoint to check if we can filter by a specific batch ID
     */
    @GetMapping("/payments-by-batch")
    public ResponseEntity<Map<String, Object>> testPaymentsByBatchId(@RequestParam String batchId) {
        
        try {
            log.info("Testing PayProp payments by batch ID: {}", batchId);

            // Try to call PayProp with payment_batch_id parameter
            String url = "/report/all-payments?payment_batch_id=" + batchId;
            Map<String, Object> response = payPropSyncService.makePayPropApiCall(url, Map.class);

            log.info("PayProp batch payments response structure: {}", 
                response.keySet().toString());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "batchId", batchId,
                "payPropResponse", response,
                "note", "This shows payments for the specific batch ID"
            ));

        } catch (Exception e) {
            log.error("Error testing PayProp payments by batch ID: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage(),
                "batchId", batchId,
                "suggestion", "The batch ID might not exist or might have a different format"
            ));
        }
    }

    /**
     * Analyze sample batch data from your staging environment
     */
    @GetMapping("/analyze-staging-batch")
    public ResponseEntity<Map<String, Object>> analyzeStagingBatch() {
        try {
            log.info("Analyzing staging batch data from 2025-04-01 to 2025-04-15");

            // Test the exact date range from your staging batch
            String url = "/report/all-payments?from_date=2025-04-01&to_date=2025-04-15&filter_by=reconciliation_date";
            Map<String, Object> response = payPropSyncService.makePayPropApiCall(url, Map.class);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "note", "Staging batch analysis - 7 records expected",
                "stagingExpected", Map.of(
                    "totalRecords", 7,
                    "totalIn", "8,396.47",
                    "totalOut", "8,043.82",
                    "beneficiary", "Christine Hunt"
                ),
                "payPropResponse", response
            ));

        } catch (Exception e) {
            log.error("Error analyzing staging batch: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}
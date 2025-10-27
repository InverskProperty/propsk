package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.service.payprop.PayPropIncomingPaymentImportService;
import site.easy.to.build.crm.service.payprop.raw.PayPropIncomingPaymentExtractorService;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for PayProp incoming payment extraction and import
 *
 * Workflow:
 * 1. Extract: Deduplicate payprop_report_all_payments -> payprop_incoming_payments
 * 2. Import: Transform payprop_incoming_payments -> historical_transactions (with splits)
 */
@RestController
@RequestMapping("/api/payprop/incoming-payments")
@PreAuthorize("hasRole('ADMIN')")
public class PayPropIncomingPaymentController {

    @Autowired
    private PayPropIncomingPaymentExtractorService extractorService;

    @Autowired
    private PayPropIncomingPaymentImportService importService;

    /**
     * Step 1: Extract unique incoming payments from raw all-payments data
     * GET /api/payprop/incoming-payments/extract
     */
    @GetMapping("/extract")
    public ResponseEntity<?> extractIncomingPayments() {
        try {
            var result = extractorService.extractIncomingPayments();

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("totalFound", result.getTotalFound());
            response.put("totalExtracted", result.getTotalExtracted());
            response.put("stats", result.getStats());

            if (!result.isSuccess()) {
                response.put("error", result.getErrorMessage());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Step 2: Import incoming payments to historical_transactions with commission splits
     * GET /api/payprop/incoming-payments/import
     */
    @GetMapping("/import")
    public ResponseEntity<?> importIncomingPayments() {
        try {
            // Get current user - for now use null, service should handle it
            var result = importService.importIncomingPayments(null);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.success);
            response.put("totalFound", result.totalFound);
            response.put("successfulImports", result.successfulImports);
            response.put("failedImports", result.failedImports);
            response.put("errors", result.errors);

            if (!result.success) {
                response.put("errorMessage", result.errorMessage);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Combined operation: Extract AND Import in one call
     * GET /api/payprop/incoming-payments/sync-all
     */
    @GetMapping("/sync-all")
    public ResponseEntity<?> syncAll() {
        try {
            Map<String, Object> response = new HashMap<>();

            // Step 1: Extract
            var extractResult = extractorService.extractIncomingPayments();
            response.put("extraction", Map.of(
                "success", extractResult.isSuccess(),
                "totalFound", extractResult.getTotalFound(),
                "totalExtracted", extractResult.getTotalExtracted(),
                "stats", extractResult.getStats()
            ));

            if (!extractResult.isSuccess()) {
                return ResponseEntity.ok(response);
            }

            // Step 2: Import
            var importResult = importService.importIncomingPayments(null);
            response.put("import", Map.of(
                "success", importResult.success,
                "totalFound", importResult.totalFound,
                "successfulImports", importResult.successfulImports,
                "failedImports", importResult.failedImports,
                "errors", importResult.errors
            ));

            response.put("overallSuccess", extractResult.isSuccess() && importResult.success);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Get status of incoming payments processing
     * GET /api/payprop/incoming-payments/status
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        try {
            // This would query payprop_incoming_payments for stats
            // For now, return a simple status
            return ResponseEntity.ok(Map.of(
                "message", "Use /extract to populate payprop_incoming_payments, then /import to create historical_transactions"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}

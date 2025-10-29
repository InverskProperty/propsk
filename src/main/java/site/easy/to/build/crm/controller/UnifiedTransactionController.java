package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.service.transaction.UnifiedTransactionRebuildService;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * REST API for unified transaction rebuild operations
 *
 * Provides endpoints to:
 * - Rebuild unified_transactions from source tables
 * - Incremental updates after PayProp sync
 * - View rebuild statistics
 *
 * Security: Only accessible by SUPER_ADMIN and MANAGER roles
 */
@RestController
@RequestMapping("/api/unified-transactions")
@PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'MANAGER')")
public class UnifiedTransactionController {

    @Autowired
    private UnifiedTransactionRebuildService rebuildService;

    /**
     * Complete rebuild of unified_transactions table
     *
     * DELETE + rebuild from historical_transactions and financial_transactions
     *
     * @return Rebuild statistics including record counts and duration
     */
    @PostMapping("/rebuild/full")
    public ResponseEntity<Map<String, Object>> rebuildFull() {
        Map<String, Object> result = rebuildService.rebuildComplete();
        return ResponseEntity.ok(result);
    }

    /**
     * Incremental rebuild - only update records changed since timestamp
     *
     * Used after:
     * - PayProp sync completes
     * - Historical CSV import
     * - Manual data corrections
     *
     * @param since ISO datetime (e.g., "2025-10-29T12:00:00")
     * @return Incremental update statistics
     */
    @PostMapping("/rebuild/incremental")
    public ResponseEntity<Map<String, Object>> rebuildIncremental(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since
    ) {
        Map<String, Object> result = rebuildService.rebuildIncremental(since);
        return ResponseEntity.ok(result);
    }

    /**
     * Get current unified_transactions statistics
     *
     * Returns:
     * - Total record count
     * - Counts by source system (HISTORICAL, PAYPROP)
     * - Date ranges per source
     * - Last rebuild timestamp
     *
     * @return Statistics map
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> stats = rebuildService.getStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "OK",
            "service", "UnifiedTransactionRebuildService",
            "message", "Service is ready to rebuild unified transactions"
        ));
    }
}

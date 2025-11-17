package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.UnifiedTransaction;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.repository.UnifiedTransactionRepository;
import site.easy.to.build.crm.service.transaction.UnifiedTransactionRebuildService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

    @Autowired
    private UnifiedTransactionRepository unifiedTransactionRepository;

    @Autowired
    private PropertyRepository propertyRepository;

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

    /**
     * TEMPORARY: Query rent received for a specific property
     */
    @GetMapping("/property/{propertyId}/rent-received")
    public ResponseEntity<Map<String, Object>> getPropertyRentReceived(@PathVariable Long propertyId) {
        Property property = propertyRepository.findById(propertyId).orElse(null);

        if (property == null) {
            return ResponseEntity.notFound().build();
        }

        // Get all transactions for this property
        List<UnifiedTransaction> transactions = unifiedTransactionRepository.findByPropertyId(propertyId);

        // Filter for rent received (INCOMING, type = RENT or RENT_PAYMENT)
        List<UnifiedTransaction> rentTransactions = transactions.stream()
            .filter(t -> t.getFlowDirection() == UnifiedTransaction.FlowDirection.INCOMING)
            .filter(t -> {
                String type = t.getTransactionType();
                return type != null && (
                    type.equals("RENT") ||
                    type.equals("RENT_PAYMENT") ||
                    type.contains("Rent")
                );
            })
            .sorted((a, b) -> a.getTransactionDate().compareTo(b.getTransactionDate()))
            .collect(Collectors.toList());

        // Calculate total by source
        Map<String, Object> totalBySource = new LinkedHashMap<>();
        for (UnifiedTransaction.SourceSystem source : UnifiedTransaction.SourceSystem.values()) {
            BigDecimal total = rentTransactions.stream()
                .filter(t -> t.getSourceSystem() == source)
                .map(UnifiedTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            long count = rentTransactions.stream()
                .filter(t -> t.getSourceSystem() == source)
                .count();

            if (count > 0) {
                Map<String, Object> sourceData = new HashMap<>();
                sourceData.put("total", total);
                sourceData.put("count", count);
                totalBySource.put(source.name(), sourceData);
            }
        }

        BigDecimal grandTotal = rentTransactions.stream()
            .map(UnifiedTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Build transaction list
        List<Map<String, Object>> transactionList = new ArrayList<>();
        for (UnifiedTransaction t : rentTransactions) {
            Map<String, Object> txData = new HashMap<>();
            txData.put("date", t.getTransactionDate().toString());
            txData.put("source", t.getSourceSystem().name());
            txData.put("type", t.getTransactionType());
            txData.put("amount", t.getAmount());
            txData.put("description", t.getDescription());
            transactionList.add(txData);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("property", property.getPropertyName());
        result.put("propertyId", propertyId);
        result.put("totalTransactions", transactions.size());
        result.put("rentTransactions", rentTransactions.size());
        result.put("bySource", totalBySource);
        result.put("grandTotal", grandTotal);
        result.put("transactions", transactionList);

        return ResponseEntity.ok(result);
    }
}

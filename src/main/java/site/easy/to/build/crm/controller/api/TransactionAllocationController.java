package site.easy.to.build.crm.controller.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.TransactionBatchAllocation;
import site.easy.to.build.crm.service.payment.TransactionBatchAllocationService;
import site.easy.to.build.crm.service.payment.TransactionBatchAllocationService.UnallocatedTransactionDTO;
import site.easy.to.build.crm.service.payment.TransactionBatchAllocationService.BatchSummaryDTO;
import site.easy.to.build.crm.util.AuthenticationUtils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import site.easy.to.build.crm.entity.HistoricalTransaction;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.repository.HistoricalTransactionRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for transaction-to-batch allocations
 *
 * This is the SIMPLIFIED allocation system that:
 * - Uses net_to_owner_amount directly from transactions
 * - Supports split allocations (one transaction to multiple batches)
 * - Replaces the complex UnifiedAllocation approach
 *
 * Base URL: /api/transaction-allocations
 */
@RestController
@RequestMapping("/api/transaction-allocations")
public class TransactionAllocationController {

    private static final Logger log = LoggerFactory.getLogger(TransactionAllocationController.class);

    @Autowired
    private TransactionBatchAllocationService allocationService;

    @Autowired
    private AuthenticationUtils authenticationUtils;

    @Autowired
    private HistoricalTransactionRepository transactionRepository;

    // ===== UNALLOCATED TRANSACTIONS =====

    /**
     * Get unallocated transactions for a property
     * GET /api/transaction-allocations/unallocated/property/{propertyId}
     */
    @GetMapping("/unallocated/property/{propertyId}")
    public ResponseEntity<Map<String, Object>> getUnallocatedForProperty(@PathVariable Long propertyId) {
        List<UnallocatedTransactionDTO> transactions = allocationService.getUnallocatedTransactionsForProperty(propertyId);
        BigDecimal total = allocationService.getTotalUnallocatedForProperty(propertyId);

        Map<String, Object> response = new HashMap<>();
        response.put("propertyId", propertyId);
        response.put("transactions", transactions);
        response.put("totalUnallocated", total);
        response.put("transactionCount", transactions.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Get unallocated transactions for an owner (across all their properties)
     * GET /api/transaction-allocations/unallocated/owner/{ownerId}
     */
    @GetMapping("/unallocated/owner/{ownerId}")
    public ResponseEntity<Map<String, Object>> getUnallocatedForOwner(@PathVariable Long ownerId) {
        List<UnallocatedTransactionDTO> transactions = allocationService.getUnallocatedTransactionsForOwner(ownerId);
        BigDecimal total = allocationService.getTotalUnallocatedForOwner(ownerId);

        Map<String, Object> response = new HashMap<>();
        response.put("ownerId", ownerId);
        response.put("transactions", transactions);
        response.put("totalUnallocated", total);
        response.put("transactionCount", transactions.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Get remaining unallocated amount for a specific transaction
     * GET /api/transaction-allocations/unallocated/transaction/{transactionId}
     */
    @GetMapping("/unallocated/transaction/{transactionId}")
    public ResponseEntity<Map<String, Object>> getRemainingForTransaction(@PathVariable Long transactionId) {
        BigDecimal remaining = allocationService.getRemainingUnallocated(transactionId);
        BigDecimal allocated = allocationService.getTotalAllocated(transactionId);
        boolean canAllocate = allocationService.canAllocate(transactionId);

        Map<String, Object> response = new HashMap<>();
        response.put("transactionId", transactionId);
        response.put("remainingUnallocated", remaining);
        response.put("totalAllocated", allocated);
        response.put("canAllocate", canAllocate);

        return ResponseEntity.ok(response);
    }

    // ===== ALLOCATION CREATION =====

    /**
     * Allocate full transaction to a batch
     * POST /api/transaction-allocations/allocate/full
     * Body: { "transactionId": 123, "batchReference": "OWNER-20251201-0001" }
     */
    @PostMapping("/allocate/full")
    public ResponseEntity<Map<String, Object>> allocateFull(@RequestBody AllocationRequest request) {
        try {
            Long userId = getCurrentUserId();
            TransactionBatchAllocation allocation = allocationService.allocateFullTransaction(
                    request.getTransactionId(),
                    request.getBatchReference(),
                    userId
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("allocation", mapAllocation(allocation));
            response.put("message", "Transaction fully allocated to batch " + request.getBatchReference());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to allocate transaction: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Allocate partial amount from a transaction to a batch
     * POST /api/transaction-allocations/allocate/partial
     * Body: { "transactionId": 123, "batchReference": "OWNER-20251201-0001", "amount": 500.00 }
     */
    @PostMapping("/allocate/partial")
    public ResponseEntity<Map<String, Object>> allocatePartial(@RequestBody AllocationRequest request) {
        try {
            if (request.getAmount() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Amount is required for partial allocation"
                ));
            }

            Long userId = getCurrentUserId();
            TransactionBatchAllocation allocation = allocationService.allocatePartialTransaction(
                    request.getTransactionId(),
                    request.getBatchReference(),
                    request.getAmount(),
                    userId
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("allocation", mapAllocation(allocation));
            response.put("remainingUnallocated", allocationService.getRemainingUnallocated(request.getTransactionId()));
            response.put("message", "Allocated " + request.getAmount() + " to batch " + request.getBatchReference());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to allocate partial transaction: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Allocate remaining amount from a transaction to a batch
     * POST /api/transaction-allocations/allocate/remaining
     * Body: { "transactionId": 123, "batchReference": "OWNER-20251201-0001" }
     */
    @PostMapping("/allocate/remaining")
    public ResponseEntity<Map<String, Object>> allocateRemaining(@RequestBody AllocationRequest request) {
        try {
            Long userId = getCurrentUserId();
            TransactionBatchAllocation allocation = allocationService.allocateRemainingToTransaction(
                    request.getTransactionId(),
                    request.getBatchReference(),
                    userId
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("allocation", mapAllocation(allocation));
            response.put("message", "Remaining amount allocated to batch " + request.getBatchReference());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to allocate remaining: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Bulk allocate multiple transactions to a batch
     * POST /api/transaction-allocations/allocate/bulk
     * Body: { "transactionIds": [1,2,3], "batchReference": "OWNER-20251201-0001" }
     */
    @PostMapping("/allocate/bulk")
    public ResponseEntity<Map<String, Object>> allocateBulk(@RequestBody BulkAllocationRequest request) {
        try {
            Long userId = getCurrentUserId();
            List<TransactionBatchAllocation> allocations = allocationService.allocateTransactionsToBatch(
                    request.getTransactionIds(),
                    request.getBatchReference(),
                    userId
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("allocatedCount", allocations.size());
            response.put("requestedCount", request.getTransactionIds().size());
            response.put("batchReference", request.getBatchReference());
            response.put("batchTotal", allocationService.getBatchTotal(request.getBatchReference()));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to bulk allocate: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    // ===== BATCH MANAGEMENT =====

    /**
     * Get all batches/payments for an owner
     * GET /api/transaction-allocations/batches/owner/{ownerId}
     */
    @GetMapping("/batches/owner/{ownerId}")
    public ResponseEntity<List<Map<String, Object>>> getBatchesForOwner(@PathVariable Long ownerId) {
        List<TransactionBatchAllocationService.BatchSummaryDTO> summaries =
            allocationService.getBatchSummariesForOwner(ownerId);

        List<Map<String, Object>> result = summaries.stream()
            .map(this::mapBatchSummary)
            .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * Get all batches/payments
     * GET /api/transaction-allocations/batches
     */
    @GetMapping("/batches")
    public ResponseEntity<List<Map<String, Object>>> getAllBatches() {
        List<TransactionBatchAllocationService.BatchSummaryDTO> summaries =
            allocationService.getAllBatchSummaries();

        List<Map<String, Object>> result = summaries.stream()
            .map(this::mapBatchSummary)
            .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * Generate a new batch reference
     * GET /api/transaction-allocations/batch/generate-reference?prefix=OWNER
     */
    @GetMapping("/batch/generate-reference")
    public ResponseEntity<Map<String, Object>> generateBatchReference(
            @RequestParam(defaultValue = "OWNER") String prefix) {
        String batchReference = allocationService.generateBatchReference(prefix);

        Map<String, Object> response = new HashMap<>();
        response.put("batchReference", batchReference);
        response.put("prefix", prefix);

        return ResponseEntity.ok(response);
    }

    /**
     * Get batch summary
     * GET /api/transaction-allocations/batch/{batchReference}
     */
    @GetMapping("/batch/{batchReference}")
    public ResponseEntity<Map<String, Object>> getBatchSummary(@PathVariable String batchReference) {
        BatchSummaryDTO summary = allocationService.getBatchSummary(batchReference);

        if (summary == null) {
            return ResponseEntity.notFound().build();
        }

        List<TransactionBatchAllocation> allocations = allocationService.getAllocationsForBatch(batchReference);

        Map<String, Object> response = new HashMap<>();
        response.put("summary", summary);
        response.put("allocations", allocations.stream().map(this::mapAllocation).toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get allocations for a batch
     * GET /api/transaction-allocations/batch/{batchReference}/allocations
     */
    @GetMapping("/batch/{batchReference}/allocations")
    public ResponseEntity<List<Map<String, Object>>> getBatchAllocations(@PathVariable String batchReference) {
        List<TransactionBatchAllocation> allocations = allocationService.getAllocationsForBatch(batchReference);
        return ResponseEntity.ok(allocations.stream().map(this::mapAllocation).toList());
    }

    /**
     * Remove all allocations for a batch
     * DELETE /api/transaction-allocations/batch/{batchReference}
     */
    @DeleteMapping("/batch/{batchReference}")
    public ResponseEntity<Map<String, Object>> removeBatch(@PathVariable String batchReference) {
        int deleted = allocationService.removeBatchAllocations(batchReference);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("deletedCount", deleted);
        response.put("batchReference", batchReference);

        return ResponseEntity.ok(response);
    }

    // ===== TRANSACTION ALLOCATION QUERIES =====

    /**
     * Get allocations for a specific transaction
     * GET /api/transaction-allocations/transaction/{transactionId}
     */
    @GetMapping("/transaction/{transactionId}")
    public ResponseEntity<Map<String, Object>> getTransactionAllocations(@PathVariable Long transactionId) {
        List<TransactionBatchAllocation> allocations = allocationService.getAllocationsForTransaction(transactionId);
        BigDecimal remaining = allocationService.getRemainingUnallocated(transactionId);
        BigDecimal allocated = allocationService.getTotalAllocated(transactionId);

        Map<String, Object> response = new HashMap<>();
        response.put("transactionId", transactionId);
        response.put("allocations", allocations.stream().map(this::mapAllocation).toList());
        response.put("totalAllocated", allocated);
        response.put("remainingUnallocated", remaining);

        return ResponseEntity.ok(response);
    }

    /**
     * Remove all allocations for a transaction
     * DELETE /api/transaction-allocations/transaction/{transactionId}
     */
    @DeleteMapping("/transaction/{transactionId}")
    public ResponseEntity<Map<String, Object>> removeTransactionAllocations(@PathVariable Long transactionId) {
        int deleted = allocationService.removeTransactionAllocations(transactionId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("deletedCount", deleted);
        response.put("transactionId", transactionId);

        return ResponseEntity.ok(response);
    }

    /**
     * Remove a single allocation by ID
     * DELETE /api/transaction-allocations/allocation/{allocationId}
     */
    @DeleteMapping("/allocation/{allocationId}")
    public ResponseEntity<Map<String, Object>> removeAllocation(@PathVariable Long allocationId) {
        try {
            boolean deleted = allocationService.removeAllocation(allocationId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", deleted);
            response.put("allocationId", allocationId);

            if (deleted) {
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Allocation not found");
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Failed to remove allocation: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    // ===== DEBUG ENDPOINT =====

    /**
     * Debug endpoint to check transaction data
     * GET /api/transaction-allocations/debug/owner/{ownerId}
     */
    @GetMapping("/debug/owner/{ownerId}")
    public ResponseEntity<Map<String, Object>> debugOwnerTransactions(@PathVariable Long ownerId) {
        Map<String, Object> debug = new HashMap<>();

        // Count all transactions
        List<HistoricalTransaction> allTxns = transactionRepository.findAll();
        debug.put("totalTransactionsInSystem", allTxns.size());

        // Count with net_to_owner
        long withNetToOwner = allTxns.stream()
            .filter(t -> t.getNetToOwnerAmount() != null)
            .count();
        debug.put("transactionsWithNetToOwner", withNetToOwner);

        // Check transactions for this owner using beneficiary
        List<HistoricalTransaction> ownerTxnsByBeneficiary = transactionRepository.findAllByOwnerId(ownerId);
        debug.put("transactionsForOwnerByBeneficiary", ownerTxnsByBeneficiary.size());

        // Check how many have net_to_owner
        long ownerWithNet = ownerTxnsByBeneficiary.stream()
            .filter(t -> t.getNetToOwnerAmount() != null)
            .count();
        debug.put("ownerTransactionsWithNetToOwner", ownerWithNet);

        // Sample: show first 5 transactions with their beneficiary info
        List<Map<String, Object>> samples = allTxns.stream()
            .limit(5)
            .map(t -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", t.getId());
                m.put("category", t.getCategory());
                m.put("amount", t.getAmount());
                m.put("netToOwner", t.getNetToOwnerAmount());
                m.put("status", t.getStatus());
                m.put("beneficiaryId", t.getBeneficiary() != null ? t.getBeneficiary().getCustomerId() : null);
                m.put("beneficiaryName", t.getBeneficiary() != null ? t.getBeneficiary().getName() : null);
                m.put("propertyId", t.getProperty() != null ? t.getProperty().getId() : null);
                return m;
            })
            .toList();
        debug.put("sampleTransactions", samples);

        // Check if any transactions have this owner as beneficiary
        long matchingBeneficiary = allTxns.stream()
            .filter(t -> t.getBeneficiary() != null && t.getBeneficiary().getCustomerId() == ownerId.intValue())
            .count();
        debug.put("transactionsWithMatchingBeneficiaryId", matchingBeneficiary);

        // Check owner field
        long matchingOwner = allTxns.stream()
            .filter(t -> t.getOwner() != null && t.getOwner().getCustomerId() == ownerId.intValue())
            .count();
        debug.put("transactionsWithMatchingOwnerId", matchingOwner);

        // Check via property ownership
        List<HistoricalTransaction> viaProperty = transactionRepository.findByPropertyOwnerIdWithNetToOwner(ownerId);
        debug.put("transactionsViaPropertyOwnership", viaProperty.size());

        // Final check - what the service will return
        List<TransactionBatchAllocationService.UnallocatedTransactionDTO> unallocated =
            allocationService.getUnallocatedTransactionsForOwner(ownerId);
        debug.put("unallocatedTransactionsReturned", unallocated.size());

        return ResponseEntity.ok(debug);
    }

    // ===== BACKFILL NET TO OWNER =====

    /**
     * Recalculate net_to_owner_amount for all transactions of an owner
     * POST /api/transaction-allocations/backfill/owner/{ownerId}
     */
    @PostMapping("/backfill/owner/{ownerId}")
    public ResponseEntity<Map<String, Object>> backfillNetToOwnerForOwner(@PathVariable Long ownerId) {
        try {
            List<HistoricalTransaction> transactions = transactionRepository.findAllByOwnerId(ownerId);
            int updated = 0;

            for (HistoricalTransaction txn : transactions) {
                if (txn.getNetToOwnerAmount() == null && txn.getAmount() != null) {
                    calculateAndSetNetToOwner(txn);
                    if (txn.getNetToOwnerAmount() != null) {
                        transactionRepository.save(txn);
                        updated++;
                    }
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalTransactions", transactions.size());
            response.put("updated", updated);
            response.put("message", "Updated " + updated + " transactions with net_to_owner_amount");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to backfill net_to_owner: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Recalculate net_to_owner_amount for ALL transactions
     * POST /api/transaction-allocations/backfill/all
     */
    @PostMapping("/backfill/all")
    public ResponseEntity<Map<String, Object>> backfillNetToOwnerAll() {
        try {
            List<HistoricalTransaction> transactions = transactionRepository.findAll();
            int updated = 0;

            for (HistoricalTransaction txn : transactions) {
                if (txn.getNetToOwnerAmount() == null && txn.getAmount() != null) {
                    calculateAndSetNetToOwner(txn);
                    if (txn.getNetToOwnerAmount() != null) {
                        transactionRepository.save(txn);
                        updated++;
                    }
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalTransactions", transactions.size());
            response.put("updated", updated);
            response.put("message", "Updated " + updated + " transactions with net_to_owner_amount");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to backfill net_to_owner: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    private void calculateAndSetNetToOwner(HistoricalTransaction txn) {
        String category = txn.getCategory();
        if (category == null) return;
        category = category.toLowerCase().trim();

        BigDecimal amount = txn.getAmount();
        if (amount == null) return;

        // Skip owner_payment category
        if (category.equals("owner_payment")) {
            return;
        }

        // RENT: Calculate commission and net
        if (category.equals("rent") && amount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal commissionRate = BigDecimal.ZERO;
            Property property = txn.getProperty();
            if (property != null && property.getCommissionPercentage() != null) {
                commissionRate = property.getCommissionPercentage();
            }

            BigDecimal commissionAmount = amount.multiply(commissionRate).divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);
            BigDecimal netToOwner = amount.subtract(commissionAmount);

            txn.setCommissionRate(commissionRate);
            txn.setCommissionAmount(commissionAmount);
            txn.setNetToOwnerAmount(netToOwner);
            return;
        }

        // EXPENSES: Full amount as negative
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            txn.setCommissionRate(BigDecimal.ZERO);
            txn.setCommissionAmount(BigDecimal.ZERO);
            txn.setNetToOwnerAmount(amount);
        }
    }

    // ===== HELPER METHODS =====

    private Long getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                return (long) authenticationUtils.getLoggedInUserId(auth);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> mapAllocation(TransactionBatchAllocation allocation) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", allocation.getId());
        map.put("transactionId", allocation.getTransactionId());
        map.put("batchReference", allocation.getBatchReference());
        map.put("allocatedAmount", allocation.getAllocatedAmount());
        map.put("propertyId", allocation.getPropertyId());
        map.put("propertyName", allocation.getPropertyName());
        map.put("beneficiaryId", allocation.getBeneficiaryId());
        map.put("beneficiaryName", allocation.getBeneficiaryName());
        map.put("createdAt", allocation.getCreatedAt());
        return map;
    }

    private Map<String, Object> mapBatchSummary(TransactionBatchAllocationService.BatchSummaryDTO summary) {
        Map<String, Object> map = new HashMap<>();
        map.put("batchReference", summary.getBatchReference());
        map.put("allocationCount", summary.getAllocationCount());
        map.put("transactionCount", summary.getTransactionCount());
        map.put("propertyCount", summary.getPropertyCount());
        map.put("totalIncome", summary.getTotalIncome());
        map.put("totalExpenses", summary.getTotalExpenses());
        map.put("netTotal", summary.getNetTotal());
        map.put("status", summary.getStatus());
        map.put("paymentDate", summary.getPaymentDate());
        return map;
    }

    // ===== REQUEST DTOs =====

    public static class AllocationRequest {
        private Long transactionId;
        private String batchReference;
        private BigDecimal amount;

        public Long getTransactionId() { return transactionId; }
        public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

        public String getBatchReference() { return batchReference; }
        public void setBatchReference(String batchReference) { this.batchReference = batchReference; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }

    public static class BulkAllocationRequest {
        private List<Long> transactionIds;
        private String batchReference;

        public List<Long> getTransactionIds() { return transactionIds; }
        public void setTransactionIds(List<Long> transactionIds) { this.transactionIds = transactionIds; }

        public String getBatchReference() { return batchReference; }
        public void setBatchReference(String batchReference) { this.batchReference = batchReference; }
    }
}

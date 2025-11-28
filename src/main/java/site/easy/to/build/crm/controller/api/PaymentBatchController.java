package site.easy.to.build.crm.controller.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.PaymentBatch;
import site.easy.to.build.crm.entity.PaymentBatch.*;
import site.easy.to.build.crm.entity.UnifiedAllocation;
import site.easy.to.build.crm.service.payment.PaymentBatchService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for payment batch management
 *
 * Provides endpoints for:
 * - Viewing pending allocations
 * - Creating payment batches
 * - Managing batch status (pending, paid)
 * - Adding balance adjustments
 */
@RestController
@RequestMapping("/api/payment-batches")
public class PaymentBatchController {

    @Autowired
    private PaymentBatchService paymentBatchService;

    // ===== ALLOCATION QUERIES =====

    /**
     * Get all pending owner allocations
     * GET /api/payment-batches/pending-allocations
     */
    @GetMapping("/pending-allocations")
    public ResponseEntity<List<UnifiedAllocation>> getPendingAllocations() {
        List<UnifiedAllocation> allocations = paymentBatchService.getAllPendingOwnerAllocations();
        return ResponseEntity.ok(allocations);
    }

    /**
     * Get pending allocations for a specific property
     * GET /api/payment-batches/pending-allocations/property/{propertyId}
     */
    @GetMapping("/pending-allocations/property/{propertyId}")
    public ResponseEntity<List<UnifiedAllocation>> getPendingAllocationsForProperty(
            @PathVariable Long propertyId) {
        List<UnifiedAllocation> allocations = paymentBatchService.getPendingAllocationsForProperty(propertyId);
        return ResponseEntity.ok(allocations);
    }

    /**
     * Get total pending owner payments amount
     * GET /api/payment-batches/pending-total
     */
    @GetMapping("/pending-total")
    public ResponseEntity<Map<String, Object>> getPendingTotal() {
        BigDecimal total = paymentBatchService.getTotalPendingOwnerPayments();
        Map<String, Object> response = new HashMap<>();
        response.put("totalPending", total);
        return ResponseEntity.ok(response);
    }

    // ===== BATCH QUERIES =====

    /**
     * Get all pending batches
     * GET /api/payment-batches/pending
     */
    @GetMapping("/pending")
    public ResponseEntity<List<PaymentBatch>> getPendingBatches() {
        List<PaymentBatch> batches = paymentBatchService.getPendingBatches();
        return ResponseEntity.ok(batches);
    }

    /**
     * Get batch details by ID
     * GET /api/payment-batches/{batchId}
     */
    @GetMapping("/{batchId}")
    public ResponseEntity<Map<String, Object>> getBatchDetails(@PathVariable String batchId) {
        return paymentBatchService.getBatchById(batchId)
            .map(batch -> {
                List<UnifiedAllocation> allocations = paymentBatchService.getAllocationsForBatch(batchId);
                Map<String, Object> response = new HashMap<>();
                response.put("batch", batch);
                response.put("allocations", allocations);
                return ResponseEntity.ok(response);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ===== BATCH CREATION =====

    /**
     * Create a new payment batch from selected allocations
     * POST /api/payment-batches/create
     *
     * Request body:
     * {
     *   "allocationIds": [1, 2, 3],
     *   "batchType": "OWNER_PAYMENT",
     *   "paymentDate": "2025-03-15",
     *   "beneficiaryId": 123,
     *   "beneficiaryName": "John Smith"
     * }
     */
    @PostMapping("/create")
    public ResponseEntity<PaymentBatch> createBatch(@RequestBody CreateBatchRequest request) {
        PaymentBatch batch = paymentBatchService.createBatch(
            request.allocationIds,
            request.batchType,
            request.paymentDate,
            request.beneficiaryId,
            request.beneficiaryName
        );
        return ResponseEntity.ok(batch);
    }

    /**
     * Create a quick batch from a single allocation
     * POST /api/payment-batches/create-single/{allocationId}
     */
    @PostMapping("/create-single/{allocationId}")
    public ResponseEntity<PaymentBatch> createSingleAllocationBatch(
            @PathVariable Long allocationId,
            @RequestParam(required = false) LocalDate paymentDate) {
        LocalDate date = paymentDate != null ? paymentDate : LocalDate.now();
        PaymentBatch batch = paymentBatchService.createSingleAllocationBatch(allocationId, date);
        return ResponseEntity.ok(batch);
    }

    // ===== BALANCE ADJUSTMENTS =====

    /**
     * Add a balance adjustment to a batch
     * POST /api/payment-batches/{batchId}/adjust
     *
     * Request body:
     * {
     *   "amount": 100.00,
     *   "source": "BLOCK",
     *   "notes": "Contribution from block account"
     * }
     */
    @PostMapping("/{batchId}/adjust")
    public ResponseEntity<PaymentBatch> addBalanceAdjustment(
            @PathVariable String batchId,
            @RequestBody AdjustmentRequest request) {
        PaymentBatch batch = paymentBatchService.addBalanceAdjustment(
            batchId,
            request.amount,
            request.source,
            request.notes
        );
        return ResponseEntity.ok(batch);
    }

    // ===== STATUS MANAGEMENT =====

    /**
     * Mark batch as pending (ready for payment)
     * POST /api/payment-batches/{batchId}/pending
     */
    @PostMapping("/{batchId}/pending")
    public ResponseEntity<PaymentBatch> markAsPending(@PathVariable String batchId) {
        PaymentBatch batch = paymentBatchService.markBatchAsPending(batchId);
        return ResponseEntity.ok(batch);
    }

    /**
     * Mark batch as paid
     * POST /api/payment-batches/{batchId}/paid
     *
     * Request body:
     * {
     *   "paidDate": "2025-03-15",
     *   "paymentReference": "BACS-123456"
     * }
     */
    @PostMapping("/{batchId}/paid")
    public ResponseEntity<PaymentBatch> markAsPaid(
            @PathVariable String batchId,
            @RequestBody MarkPaidRequest request) {
        PaymentBatch batch = paymentBatchService.markBatchAsPaid(
            batchId,
            request.paidDate != null ? request.paidDate : LocalDate.now(),
            request.paymentReference
        );
        return ResponseEntity.ok(batch);
    }

    // ===== REQUEST DTOs =====

    public static class CreateBatchRequest {
        public List<Long> allocationIds;
        public BatchType batchType;
        public LocalDate paymentDate;
        public Long beneficiaryId;
        public String beneficiaryName;
    }

    public static class AdjustmentRequest {
        public BigDecimal amount;
        public AdjustmentSource source;
        public String notes;
    }

    public static class MarkPaidRequest {
        public LocalDate paidDate;
        public String paymentReference;
    }
}

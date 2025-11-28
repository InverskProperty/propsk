package site.easy.to.build.crm.service.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.PaymentBatch;
import site.easy.to.build.crm.entity.PaymentBatch.*;
import site.easy.to.build.crm.entity.UnifiedAllocation;
import site.easy.to.build.crm.entity.UnifiedAllocation.AllocationType;
import site.easy.to.build.crm.entity.UnifiedAllocation.PaymentStatus;
import site.easy.to.build.crm.repository.PaymentBatchRepository;
import site.easy.to.build.crm.repository.UnifiedAllocationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing payment batches
 *
 * Handles:
 * - Creating payment batches from selected allocations
 * - Adding balance adjustments (from block or owner balance)
 * - Marking batches and their allocations as paid
 * - Generating batch IDs
 */
@Service
public class PaymentBatchService {

    private static final Logger log = LoggerFactory.getLogger(PaymentBatchService.class);

    @Autowired
    private PaymentBatchRepository paymentBatchRepository;

    @Autowired
    private UnifiedAllocationRepository unifiedAllocationRepository;

    // ===== BATCH CREATION =====

    /**
     * Create a new payment batch from selected allocations
     *
     * @param allocationIds List of allocation IDs to include in the batch
     * @param batchType Type of batch (OWNER_PAYMENT, EXPENSE_PAYMENT, etc.)
     * @param paymentDate Date of payment
     * @param beneficiaryId Optional beneficiary ID
     * @param beneficiaryName Optional beneficiary name
     * @return Created PaymentBatch
     */
    @Transactional
    public PaymentBatch createBatch(
            List<Long> allocationIds,
            BatchType batchType,
            LocalDate paymentDate,
            Long beneficiaryId,
            String beneficiaryName
    ) {
        log.info("Creating payment batch for {} allocations, type: {}", allocationIds.size(), batchType);

        // Fetch allocations
        List<UnifiedAllocation> allocations = unifiedAllocationRepository.findAllById(allocationIds);

        if (allocations.isEmpty()) {
            throw new IllegalArgumentException("No valid allocations found for the provided IDs");
        }

        // Verify all allocations are PENDING
        for (UnifiedAllocation allocation : allocations) {
            if (allocation.getPaymentStatus() != PaymentStatus.PENDING) {
                throw new IllegalStateException(
                    "Allocation " + allocation.getId() + " is not pending (status: " + allocation.getPaymentStatus() + ")");
            }
        }

        // Calculate total
        BigDecimal totalAllocations = allocations.stream()
            .map(UnifiedAllocation::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Generate batch ID
        String batchId = generateBatchId(batchType);

        // Create batch
        PaymentBatch batch = new PaymentBatch();
        batch.setBatchId(batchId);
        batch.setBatchType(batchType);
        batch.setPaymentDate(paymentDate);
        batch.setBeneficiaryId(beneficiaryId);
        batch.setBeneficiaryName(beneficiaryName);
        batch.setTotalAllocations(totalAllocations);
        batch.setTotalPayment(totalAllocations); // Initially same as allocations
        batch.setStatus(BatchStatus.DRAFT);
        batch.setSource(BatchSource.MANUAL);

        batch = paymentBatchRepository.save(batch);

        // Update allocations with batch ID
        unifiedAllocationRepository.assignToBatch(allocationIds, batchId, PaymentStatus.BATCHED);

        log.info("Created batch {} with {} allocations totaling {}", batchId, allocations.size(), totalAllocations);

        return batch;
    }

    /**
     * Create a batch from a single allocation (quick batch)
     */
    @Transactional
    public PaymentBatch createSingleAllocationBatch(
            Long allocationId,
            LocalDate paymentDate
    ) {
        UnifiedAllocation allocation = unifiedAllocationRepository.findById(allocationId)
            .orElseThrow(() -> new IllegalArgumentException("Allocation not found: " + allocationId));

        BatchType batchType = mapAllocationTypeToBatchType(allocation.getAllocationType());

        return createBatch(
            List.of(allocationId),
            batchType,
            paymentDate,
            allocation.getBeneficiaryId(),
            allocation.getBeneficiaryName()
        );
    }

    // ===== BALANCE ADJUSTMENTS =====

    /**
     * Add a balance adjustment to a batch
     *
     * @param batchId Batch ID
     * @param adjustmentAmount Amount to adjust (positive = add to payment, negative = subtract)
     * @param adjustmentSource Source of adjustment (BLOCK or OWNER_BALANCE)
     * @param notes Optional notes explaining the adjustment
     */
    @Transactional
    public PaymentBatch addBalanceAdjustment(
            String batchId,
            BigDecimal adjustmentAmount,
            AdjustmentSource adjustmentSource,
            String notes
    ) {
        PaymentBatch batch = paymentBatchRepository.findByBatchId(batchId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        if (batch.getStatus() == BatchStatus.PAID) {
            throw new IllegalStateException("Cannot modify a paid batch");
        }

        batch.setBalanceAdjustment(adjustmentAmount);
        batch.setAdjustmentSource(adjustmentSource);
        batch.setAdjustmentNotes(notes);

        // Recalculate total payment
        BigDecimal totalPayment = batch.getTotalAllocations().add(adjustmentAmount);
        batch.setTotalPayment(totalPayment);

        log.info("Added balance adjustment of {} ({}) to batch {}, new total: {}",
            adjustmentAmount, adjustmentSource, batchId, totalPayment);

        return paymentBatchRepository.save(batch);
    }

    // ===== BATCH STATUS MANAGEMENT =====

    /**
     * Mark a batch as pending (ready for payment)
     */
    @Transactional
    public PaymentBatch markBatchAsPending(String batchId) {
        PaymentBatch batch = paymentBatchRepository.findByBatchId(batchId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        if (batch.getStatus() != BatchStatus.DRAFT) {
            throw new IllegalStateException("Only draft batches can be marked as pending");
        }

        batch.setStatus(BatchStatus.PENDING);
        return paymentBatchRepository.save(batch);
    }

    /**
     * Mark a batch and all its allocations as paid
     */
    @Transactional
    public PaymentBatch markBatchAsPaid(String batchId, LocalDate paidDate, String paymentReference) {
        PaymentBatch batch = paymentBatchRepository.findByBatchId(batchId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        if (batch.getStatus() == BatchStatus.PAID) {
            throw new IllegalStateException("Batch is already marked as paid");
        }

        // Update batch
        batch.setStatus(BatchStatus.PAID);
        batch.setPaymentDate(paidDate);
        batch.setPaymentReference(paymentReference);
        batch = paymentBatchRepository.save(batch);

        // Update all allocations in this batch
        unifiedAllocationRepository.markBatchAsPaid(batchId, paidDate);

        log.info("Marked batch {} as paid on {}, reference: {}", batchId, paidDate, paymentReference);

        return batch;
    }

    // ===== QUERIES =====

    /**
     * Get pending allocations for a property
     */
    public List<UnifiedAllocation> getPendingAllocationsForProperty(Long propertyId) {
        return unifiedAllocationRepository.findPendingOwnerAllocationsForProperty(propertyId);
    }

    /**
     * Get all pending owner allocations
     */
    public List<UnifiedAllocation> getAllPendingOwnerAllocations() {
        return unifiedAllocationRepository.findPendingAllocationsByType(AllocationType.OWNER);
    }

    /**
     * Get pending batches
     */
    public List<PaymentBatch> getPendingBatches() {
        return paymentBatchRepository.findBatchesNeedingPayment();
    }

    /**
     * Get batch by ID
     */
    public Optional<PaymentBatch> getBatchById(String batchId) {
        return paymentBatchRepository.findByBatchId(batchId);
    }

    /**
     * Get allocations for a batch
     */
    public List<UnifiedAllocation> getAllocationsForBatch(String batchId) {
        return unifiedAllocationRepository.findByPaymentBatchId(batchId);
    }

    /**
     * Get total pending amount for owner payments
     */
    public BigDecimal getTotalPendingOwnerPayments() {
        BigDecimal total = unifiedAllocationRepository.sumPendingAmountByType(AllocationType.OWNER);
        return total != null ? total : BigDecimal.ZERO;
    }

    // ===== HELPER METHODS =====

    /**
     * Generate a unique batch ID
     */
    private String generateBatchId(BatchType batchType) {
        String prefix = switch (batchType) {
            case OWNER_PAYMENT -> "OWNER";
            case EXPENSE_PAYMENT -> "EXP";
            case COMMISSION -> "COMM";
            case DISBURSEMENT -> "DISB";
        };

        // Get next sequence number
        Integer latestNumber = paymentBatchRepository.getLatestOwnerBatchNumber();
        int nextNumber = (latestNumber != null ? latestNumber : 0) + 1;

        // Format: PREFIX-YYYYMMDD-SEQ
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("%s-%s-%04d", prefix, dateStr, nextNumber);
    }

    /**
     * Map allocation type to batch type
     */
    private BatchType mapAllocationTypeToBatchType(AllocationType allocationType) {
        return switch (allocationType) {
            case OWNER -> BatchType.OWNER_PAYMENT;
            case EXPENSE -> BatchType.EXPENSE_PAYMENT;
            case COMMISSION -> BatchType.COMMISSION;
            case DISBURSEMENT -> BatchType.DISBURSEMENT;
            case OTHER -> BatchType.EXPENSE_PAYMENT;
        };
    }
}

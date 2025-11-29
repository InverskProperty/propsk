package site.easy.to.build.crm.service.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.PaymentBatch;
import site.easy.to.build.crm.entity.PaymentBatch.*;
import site.easy.to.build.crm.entity.PropertyBalanceLedger;
import site.easy.to.build.crm.entity.UnifiedAllocation;
import site.easy.to.build.crm.entity.UnifiedAllocation.AllocationType;
import site.easy.to.build.crm.entity.UnifiedAllocation.PaymentStatus;
import site.easy.to.build.crm.repository.PaymentBatchRepository;
import site.easy.to.build.crm.repository.UnifiedAllocationRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    @Autowired
    private PropertyBalanceService propertyBalanceService;

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

    /**
     * Create a batch with automatic balance adjustment based on actual payment amount
     *
     * If actualPaymentAmount < totalAllocations: DEPOSIT difference to property balances
     * If actualPaymentAmount > totalAllocations: WITHDRAW difference from property balances
     *
     * @param allocationIds List of allocation IDs to include
     * @param actualPaymentAmount The actual payment amount being made
     * @param paymentDate Date of payment
     * @param beneficiaryId Beneficiary ID
     * @param beneficiaryName Beneficiary name
     * @param notes Notes for the adjustment
     * @param createdBy User ID creating the batch
     * @return Created PaymentBatch with balance adjustments recorded
     */
    @Transactional
    public PaymentBatch createBatchWithBalanceAdjustment(
            List<Long> allocationIds,
            BigDecimal actualPaymentAmount,
            LocalDate paymentDate,
            Long beneficiaryId,
            String beneficiaryName,
            String notes,
            Long createdBy
    ) {
        log.info("Creating payment batch with balance adjustment for {} allocations, actual payment: {}",
                allocationIds.size(), actualPaymentAmount);

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

        // Calculate total from allocations
        BigDecimal totalAllocations = allocations.stream()
            .map(UnifiedAllocation::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate adjustment
        BigDecimal adjustment = actualPaymentAmount.subtract(totalAllocations);

        // Generate batch ID
        String batchId = generateBatchId(BatchType.OWNER_PAYMENT);

        // Create batch
        PaymentBatch batch = new PaymentBatch();
        batch.setBatchId(batchId);
        batch.setBatchType(BatchType.OWNER_PAYMENT);
        batch.setPaymentDate(paymentDate);
        batch.setBeneficiaryId(beneficiaryId);
        batch.setBeneficiaryName(beneficiaryName);
        batch.setTotalAllocations(totalAllocations);
        batch.setTotalPayment(actualPaymentAmount);
        batch.setStatus(BatchStatus.DRAFT);
        batch.setSource(BatchSource.MANUAL);
        batch.setNotes(notes);
        batch.setCreatedBy(createdBy);

        // Handle balance adjustment if needed
        if (adjustment.compareTo(BigDecimal.ZERO) != 0) {
            batch.setBalanceAdjustment(adjustment.abs());
            batch.setAdjustmentSource(AdjustmentSource.OWNER_BALANCE);

            if (adjustment.compareTo(BigDecimal.ZERO) < 0) {
                // Paying LESS than owed - deposit to property balances
                batch.setAdjustmentNotes(notes != null ? notes : "Held from payment - paying less than owed");
                distributeBalanceDeposit(allocations, adjustment.abs(), batchId, createdBy);
            } else {
                // Paying MORE than owed - withdraw from property balances
                batch.setAdjustmentNotes(notes != null ? notes : "Added to payment from balance");
                distributeBalanceWithdrawal(allocations, adjustment, batchId, createdBy);
            }
        }

        batch = paymentBatchRepository.save(batch);

        // Update allocations with batch ID
        unifiedAllocationRepository.assignToBatch(allocationIds, batchId, PaymentStatus.BATCHED);

        log.info("Created batch {} with {} allocations, total: {}, actual payment: {}, adjustment: {}",
                batchId, allocations.size(), totalAllocations, actualPaymentAmount, adjustment);

        return batch;
    }

    /**
     * Distribute balance deposit proportionally across properties
     * Called when paying LESS than owed (difference deposited to property balances)
     */
    private void distributeBalanceDeposit(List<UnifiedAllocation> allocations, BigDecimal totalDeposit,
                                          String batchId, Long createdBy) {
        // Group allocations by property
        Map<Long, List<UnifiedAllocation>> allocationsByProperty = allocations.stream()
                .filter(a -> a.getPropertyId() != null)
                .collect(Collectors.groupingBy(UnifiedAllocation::getPropertyId));

        if (allocationsByProperty.isEmpty()) {
            log.warn("No allocations with property IDs found - cannot distribute balance deposit");
            return;
        }

        // Calculate total amount across all properties
        BigDecimal totalAmount = allocations.stream()
                .map(UnifiedAllocation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Distribute deposit proportionally
        BigDecimal depositedSoFar = BigDecimal.ZERO;
        int propertyCount = allocationsByProperty.size();
        int currentProperty = 0;

        for (Map.Entry<Long, List<UnifiedAllocation>> entry : allocationsByProperty.entrySet()) {
            Long propertyId = entry.getKey();
            List<UnifiedAllocation> propertyAllocations = entry.getValue();
            currentProperty++;

            BigDecimal propertyTotal = propertyAllocations.stream()
                    .map(UnifiedAllocation::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal propertyDeposit;
            if (currentProperty == propertyCount) {
                // Last property gets remaining to avoid rounding issues
                propertyDeposit = totalDeposit.subtract(depositedSoFar);
            } else {
                // Proportional split
                BigDecimal proportion = propertyTotal.divide(totalAmount, 6, RoundingMode.HALF_UP);
                propertyDeposit = totalDeposit.multiply(proportion).setScale(2, RoundingMode.HALF_UP);
            }

            if (propertyDeposit.compareTo(BigDecimal.ZERO) > 0) {
                String propertyName = propertyAllocations.get(0).getPropertyName();
                propertyBalanceService.deposit(propertyId, propertyDeposit, batchId,
                        String.format("Held from batch %s (%s owed, paid less)", batchId, propertyTotal),
                        PropertyBalanceLedger.Source.PAYMENT_BATCH, createdBy);
                depositedSoFar = depositedSoFar.add(propertyDeposit);
                log.debug("Deposited {} to property {} balance from batch {}",
                        propertyDeposit, propertyId, batchId);
            }
        }
    }

    /**
     * Distribute balance withdrawal proportionally across properties
     * Called when paying MORE than owed (difference withdrawn from property balances)
     */
    private void distributeBalanceWithdrawal(List<UnifiedAllocation> allocations, BigDecimal totalWithdrawal,
                                             String batchId, Long createdBy) {
        // Group allocations by property
        Map<Long, List<UnifiedAllocation>> allocationsByProperty = allocations.stream()
                .filter(a -> a.getPropertyId() != null)
                .collect(Collectors.groupingBy(UnifiedAllocation::getPropertyId));

        if (allocationsByProperty.isEmpty()) {
            log.warn("No allocations with property IDs found - cannot distribute balance withdrawal");
            return;
        }

        // Calculate total amount across all properties
        BigDecimal totalAmount = allocations.stream()
                .map(UnifiedAllocation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Check total available balance
        BigDecimal totalAvailable = BigDecimal.ZERO;
        Map<Long, BigDecimal> availableByProperty = new HashMap<>();

        for (Long propertyId : allocationsByProperty.keySet()) {
            BigDecimal available = propertyBalanceService.getAvailableBalance(propertyId);
            availableByProperty.put(propertyId, available);
            totalAvailable = totalAvailable.add(available);
        }

        if (totalAvailable.compareTo(totalWithdrawal) < 0) {
            throw new IllegalStateException(
                    String.format("Insufficient property balance. Need %s but only %s available across properties",
                            totalWithdrawal, totalAvailable));
        }

        // Distribute withdrawal proportionally
        BigDecimal withdrawnSoFar = BigDecimal.ZERO;
        int propertyCount = allocationsByProperty.size();
        int currentProperty = 0;

        for (Map.Entry<Long, List<UnifiedAllocation>> entry : allocationsByProperty.entrySet()) {
            Long propertyId = entry.getKey();
            List<UnifiedAllocation> propertyAllocations = entry.getValue();
            currentProperty++;

            BigDecimal propertyTotal = propertyAllocations.stream()
                    .map(UnifiedAllocation::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal propertyWithdrawal;
            if (currentProperty == propertyCount) {
                // Last property gets remaining to avoid rounding issues
                propertyWithdrawal = totalWithdrawal.subtract(withdrawnSoFar);
            } else {
                // Proportional split
                BigDecimal proportion = propertyTotal.divide(totalAmount, 6, RoundingMode.HALF_UP);
                propertyWithdrawal = totalWithdrawal.multiply(proportion).setScale(2, RoundingMode.HALF_UP);
            }

            // Cap at available balance for this property
            BigDecimal available = availableByProperty.get(propertyId);
            if (propertyWithdrawal.compareTo(available) > 0) {
                propertyWithdrawal = available;
            }

            if (propertyWithdrawal.compareTo(BigDecimal.ZERO) > 0) {
                propertyBalanceService.withdraw(propertyId, propertyWithdrawal, batchId,
                        String.format("Added to batch %s payment from balance", batchId),
                        PropertyBalanceLedger.Source.PAYMENT_BATCH, createdBy);
                withdrawnSoFar = withdrawnSoFar.add(propertyWithdrawal);
                log.debug("Withdrew {} from property {} balance for batch {}",
                        propertyWithdrawal, propertyId, batchId);
            }
        }
    }

    /**
     * Get property balance information for allocations
     * Returns map of propertyId -> available balance
     */
    public Map<Long, BigDecimal> getPropertyBalancesForAllocations(List<Long> allocationIds) {
        List<UnifiedAllocation> allocations = unifiedAllocationRepository.findAllById(allocationIds);

        return allocations.stream()
                .filter(a -> a.getPropertyId() != null)
                .map(UnifiedAllocation::getPropertyId)
                .distinct()
                .collect(Collectors.toMap(
                        propertyId -> propertyId,
                        propertyId -> propertyBalanceService.getAvailableBalance(propertyId)
                ));
    }

    /**
     * Calculate total available balance for properties in the given allocations
     */
    public BigDecimal getTotalAvailableBalanceForAllocations(List<Long> allocationIds) {
        return getPropertyBalancesForAllocations(allocationIds).values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

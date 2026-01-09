package site.easy.to.build.crm.service.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.entity.UnifiedAllocation.AllocationType;
import site.easy.to.build.crm.entity.UnifiedAllocation.AllocationSource;
import site.easy.to.build.crm.entity.UnifiedAllocation.PaymentStatus;
import site.easy.to.build.crm.repository.UnifiedAllocationRepository;
import site.easy.to.build.crm.repository.UnifiedTransactionRepository;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.repository.PaymentBatchRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing UnifiedAllocations
 *
 * Provides methods to:
 * - Create allocations from various sources (PayProp, CSV, Historical, Manual)
 * - Query allocations by property, beneficiary, or status
 * - Support batch payment operations
 */
@Service
@Transactional
public class UnifiedAllocationService {

    private static final Logger log = LoggerFactory.getLogger(UnifiedAllocationService.class);

    @Autowired
    private UnifiedAllocationRepository allocationRepository;

    @Autowired
    private UnifiedTransactionRepository unifiedTransactionRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PaymentBatchRepository paymentBatchRepository;

    @Autowired
    private site.easy.to.build.crm.repository.BlockRepository blockRepository;

    // ===== ALLOCATION CREATION =====

    /**
     * Create an owner allocation from a historical transaction
     */
    public UnifiedAllocation createOwnerAllocationFromTransaction(
            HistoricalTransaction transaction,
            Customer owner,
            AllocationSource source
    ) {
        if (transaction == null || owner == null) {
            throw new IllegalArgumentException("Transaction and owner are required");
        }

        // Note: No duplicate check - historical transactions don't have a link column in unified_allocations
        // The sourceRecordId is used to track the original transaction

        UnifiedAllocation allocation = new UnifiedAllocation();

        // Set allocation details
        allocation.setAllocationType(AllocationType.OWNER);
        allocation.setAmount(transaction.getAmount().abs()); // Always positive for allocations
        allocation.setCategory(transaction.getCategory());
        allocation.setDescription(transaction.getDescription());

        // Property info
        if (transaction.getProperty() != null) {
            allocation.setPropertyId(transaction.getProperty().getId());
            allocation.setPropertyName(transaction.getProperty().getPropertyName());
        }

        // Invoice/Lease info - link allocation to specific lease
        if (transaction.getInvoice() != null) {
            allocation.setInvoiceId(transaction.getInvoice().getId());
        }

        // Beneficiary info
        allocation.setBeneficiaryType("OWNER");
        allocation.setBeneficiaryId(owner.getCustomerId());
        allocation.setBeneficiaryName(owner.getName());

        // Payment tracking - start as PENDING
        allocation.setPaymentStatus(PaymentStatus.PENDING);

        // Check if transaction already has batch info (from PayProp)
        if (transaction.getPaypropBatchId() != null) {
            allocation.setPaymentBatchId(transaction.getPaypropBatchId());
            allocation.setPaypropBatchId(transaction.getPaypropBatchId());
            allocation.setPaymentStatus(PaymentStatus.PAID);
            allocation.setPaidDate(transaction.getTransactionDate());
        }

        // Source tracking
        allocation.setSource(source);
        allocation.setSourceRecordId(transaction.getId());

        // PayProp integration
        if (transaction.getPaypropTransactionId() != null) {
            allocation.setPaypropPaymentId(transaction.getPaypropTransactionId());
        }

        UnifiedAllocation saved = allocationRepository.save(allocation);
        log.info("Created owner allocation {} for property {} amount {}",
                saved.getId(), allocation.getPropertyName(), allocation.getAmount());

        return saved;
    }

    /**
     * Create a manual allocation (not linked to a specific transaction)
     */
    public UnifiedAllocation createManualOwnerAllocation(
            Property property,
            Customer owner,
            BigDecimal amount,
            String description,
            LocalDate allocationDate
    ) {
        UnifiedAllocation allocation = new UnifiedAllocation();

        allocation.setAllocationType(AllocationType.OWNER);
        allocation.setAmount(amount.abs());
        allocation.setDescription(description);

        allocation.setPropertyId(property.getId());
        allocation.setPropertyName(property.getPropertyName());

        allocation.setBeneficiaryType("OWNER");
        allocation.setBeneficiaryId(owner.getCustomerId());
        allocation.setBeneficiaryName(owner.getName());

        allocation.setPaymentStatus(PaymentStatus.PENDING);
        allocation.setSource(AllocationSource.MANUAL);

        UnifiedAllocation saved = allocationRepository.save(allocation);
        log.info("Created manual owner allocation {} for property {} amount {}",
                saved.getId(), property.getPropertyName(), amount);

        return saved;
    }

    /**
     * Create allocations for multiple transactions (batch creation)
     */
    public List<UnifiedAllocation> createAllocationsFromTransactions(
            List<HistoricalTransaction> transactions,
            AllocationSource source
    ) {
        return transactions.stream()
            .filter(t -> t.getCustomer() != null)
            .map(t -> createOwnerAllocationFromTransaction(t, t.getCustomer(), source))
            .toList();
    }

    // ===== ALLOCATION QUERIES =====

    /**
     * Get pending allocations for a property
     */
    public List<UnifiedAllocation> getPendingAllocationsForProperty(Long propertyId) {
        return allocationRepository.findPendingOwnerAllocationsForProperty(propertyId);
    }

    /**
     * Get all pending owner allocations
     */
    public List<UnifiedAllocation> getAllPendingOwnerAllocations() {
        return allocationRepository.findPendingAllocationsByType(AllocationType.OWNER);
    }

    /**
     * Get allocations for a beneficiary
     */
    public List<UnifiedAllocation> getAllocationsForBeneficiary(Long beneficiaryId) {
        return allocationRepository.findByBeneficiaryId(beneficiaryId);
    }

    /**
     * Get pending allocations for a beneficiary (owner)
     */
    public List<UnifiedAllocation> getPendingAllocationsForBeneficiary(Long beneficiaryId) {
        return allocationRepository.findByBeneficiaryIdAndPaymentStatus(
            beneficiaryId, PaymentStatus.PENDING);
    }

    /**
     * Get allocation by ID
     */
    public Optional<UnifiedAllocation> getAllocationById(Long id) {
        return allocationRepository.findById(id);
    }

    /**
     * Get allocations for a batch
     */
    public List<UnifiedAllocation> getAllocationsForBatch(String batchId) {
        return allocationRepository.findByPaymentBatchId(batchId);
    }

    // ===== BATCH OPERATIONS =====

    /**
     * Assign allocations to a batch
     */
    public void assignToBatch(List<Long> allocationIds, String batchId) {
        allocationRepository.assignToBatch(allocationIds, batchId, PaymentStatus.BATCHED);
        log.info("Assigned {} allocations to batch {}", allocationIds.size(), batchId);
    }

    /**
     * Mark batch allocations as paid
     */
    public void markBatchAsPaid(String batchId, LocalDate paidDate) {
        allocationRepository.markBatchAsPaid(batchId, paidDate);
        log.info("Marked batch {} as paid on {}", batchId, paidDate);
    }

    /**
     * Mark a single allocation as paid
     */
    public UnifiedAllocation markAsPaid(Long allocationId, String batchId, LocalDate paidDate) {
        UnifiedAllocation allocation = allocationRepository.findById(allocationId)
            .orElseThrow(() -> new IllegalArgumentException("Allocation not found: " + allocationId));

        allocation.setPaymentBatchId(batchId);
        allocation.setPaymentStatus(PaymentStatus.PAID);
        allocation.setPaidDate(paidDate);

        return allocationRepository.save(allocation);
    }

    // ===== SUMMARY METHODS =====

    /**
     * Get total pending amount for owner payments
     */
    public BigDecimal getTotalPendingOwnerPayments() {
        BigDecimal total = allocationRepository.sumPendingAmountByType(AllocationType.OWNER);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Get count of pending allocations
     */
    public long getPendingAllocationCount() {
        return allocationRepository.countPendingAllocations();
    }

    /**
     * Get pending amount for a specific property
     */
    public BigDecimal getPendingAmountForProperty(Long propertyId) {
        BigDecimal total = allocationRepository.sumPendingAmountByPropertyId(propertyId);
        return total != null ? total : BigDecimal.ZERO;
    }

    // ===== UNIFIED TRANSACTION-BASED ALLOCATIONS (consolidated from TransactionBatchAllocationService) =====

    /**
     * Allocate a full unified transaction to a batch (100% of net_to_owner_amount)
     * Creates a single OWNER allocation with gross/commission/net breakdown stored in the record.
     *
     * @return The OWNER allocation with full breakdown
     */
    public UnifiedAllocation allocateFullUnifiedTransaction(Long unifiedTransactionId, String batchReference, Long userId) {
        UnifiedTransaction transaction = getUnifiedTransaction(unifiedTransactionId);

        BigDecimal netToOwner = transaction.getNetToOwnerAmount();
        if (netToOwner == null) {
            throw new IllegalArgumentException("UnifiedTransaction " + unifiedTransactionId + " has no net_to_owner_amount calculated");
        }

        BigDecimal remaining = getRemainingUnallocatedForUnified(unifiedTransactionId);
        if (remaining.compareTo(netToOwner) != 0) {
            throw new IllegalStateException("UnifiedTransaction " + unifiedTransactionId + " is already partially allocated. " +
                    "Remaining: " + remaining + ", Total: " + netToOwner);
        }

        // Create OWNER allocation with gross/commission/net breakdown
        return createUnifiedAllocation(transaction, batchReference, netToOwner, userId, AllocationType.OWNER);
    }

    /**
     * Allocate a specific amount from a unified transaction to a batch (partial allocation)
     * Creates a single OWNER allocation with proportional gross/commission/net breakdown.
     *
     * @param amount The net-to-owner amount to allocate
     * @return The OWNER allocation with proportional breakdown
     */
    public UnifiedAllocation allocatePartialUnifiedTransaction(Long unifiedTransactionId, String batchReference,
                                                                BigDecimal amount, Long userId) {
        UnifiedTransaction transaction = getUnifiedTransaction(unifiedTransactionId);
        validateUnifiedAllocation(transaction, amount);

        // Create OWNER allocation with proportional gross/commission/net breakdown
        return createUnifiedAllocation(transaction, batchReference, amount, userId, AllocationType.OWNER);
    }

    /**
     * Allocate remaining unallocated amount from a unified transaction to a batch
     * Creates a single OWNER allocation with proportional gross/commission/net breakdown.
     *
     * @return The OWNER allocation with proportional breakdown
     */
    public UnifiedAllocation allocateRemainingToUnifiedTransaction(Long unifiedTransactionId, String batchReference, Long userId) {
        UnifiedTransaction transaction = getUnifiedTransaction(unifiedTransactionId);

        BigDecimal remaining = getRemainingUnallocatedForUnified(unifiedTransactionId);
        if (remaining.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalStateException("UnifiedTransaction " + unifiedTransactionId + " is fully allocated");
        }

        // Create OWNER allocation with proportional gross/commission/net breakdown
        return createUnifiedAllocation(transaction, batchReference, remaining, userId, AllocationType.OWNER);
    }

    /**
     * Allocate multiple unified transactions fully to a single batch
     */
    public List<UnifiedAllocation> allocateUnifiedTransactionsToBatch(List<Long> unifiedTransactionIds,
                                                                       String batchReference, Long userId) {
        List<UnifiedAllocation> allocations = new ArrayList<>();

        for (Long transactionId : unifiedTransactionIds) {
            try {
                UnifiedAllocation allocation = allocateFullUnifiedTransaction(transactionId, batchReference, userId);
                allocations.add(allocation);
            } catch (Exception e) {
                log.warn("Failed to allocate unified transaction {} to batch {}: {}",
                        transactionId, batchReference, e.getMessage());
            }
        }

        log.info("Allocated {} of {} unified transactions to batch {}",
                allocations.size(), unifiedTransactionIds.size(), batchReference);
        return allocations;
    }

    /**
     * Create an allocation from a unified transaction (auto-determines type from flow direction)
     */
    private UnifiedAllocation createUnifiedAllocation(UnifiedTransaction transaction,
                                                       String batchReference,
                                                       BigDecimal amount,
                                                       Long userId) {
        // Determine allocation type based on transaction's flow direction
        AllocationType allocationType;
        if (transaction.getFlowDirection() == UnifiedTransaction.FlowDirection.INCOMING) {
            allocationType = AllocationType.OWNER;
        } else {
            allocationType = AllocationType.EXPENSE;
        }
        return createUnifiedAllocation(transaction, batchReference, amount, userId, allocationType);
    }

    /**
     * Create an allocation from a unified transaction with explicit allocation type
     *
     * For OWNER allocations (rent), populates the full gross/commission/net breakdown.
     * For EXPENSE/DISBURSEMENT allocations, only the amount is populated.
     *
     * @param transaction The unified transaction
     * @param batchReference The batch reference
     * @param amount The allocation amount (for OWNER: the net amount being allocated)
     * @param userId The user creating the allocation
     * @param allocationType The explicit allocation type (OWNER, EXPENSE, DISBURSEMENT, etc.)
     */
    private UnifiedAllocation createUnifiedAllocation(UnifiedTransaction transaction,
                                                       String batchReference,
                                                       BigDecimal amount,
                                                       Long userId,
                                                       AllocationType allocationType) {
        UnifiedAllocation allocation = new UnifiedAllocation();

        // Link to unified transaction
        allocation.setUnifiedTransactionId(transaction.getId());

        // Also link to historical transaction if available (for backwards compatibility)
        if ("historical_transactions".equals(transaction.getSourceTable()) && transaction.getSourceRecordId() != null) {
            allocation.setHistoricalTransactionId(transaction.getSourceRecordId());
        }

        // Set the explicit allocation type
        allocation.setAllocationType(allocationType);
        allocation.setAmount(amount.abs()); // The allocated amount (could be partial)
        allocation.setCategory(transaction.getCategory());
        allocation.setDescription(transaction.getDescription());

        // For OWNER allocations (rent), populate gross/commission/net breakdown
        if (allocationType == AllocationType.OWNER) {
            // Get transaction amounts - handle full vs partial allocation
            BigDecimal transactionGross = transaction.getAmount();
            BigDecimal transactionNet = transaction.getNetToOwnerAmount();
            BigDecimal transactionCommission = transaction.getCommissionAmount();
            BigDecimal transactionCommissionRate = transaction.getCommissionRate();

            if (transactionNet != null && transactionNet.compareTo(BigDecimal.ZERO) > 0) {
                // Calculate what proportion of the transaction is being allocated
                BigDecimal allocationProportion = amount.abs().divide(transactionNet.abs(), 10, java.math.RoundingMode.HALF_UP);

                // For full allocation, use actual values; for partial, calculate proportionally
                if (allocationProportion.compareTo(BigDecimal.ONE) >= 0) {
                    // Full allocation - use actual transaction values
                    allocation.setGrossAmount(transactionGross != null ? transactionGross.abs() : null);
                    allocation.setCommissionRate(transactionCommissionRate);
                    allocation.setCommissionAmount(transactionCommission != null ? transactionCommission.abs() : null);
                    allocation.setNetToOwnerAmount(transactionNet.abs());
                } else {
                    // Partial allocation - calculate proportional amounts
                    allocation.setGrossAmount(transactionGross != null ?
                        transactionGross.abs().multiply(allocationProportion).setScale(2, java.math.RoundingMode.HALF_UP) : null);
                    allocation.setCommissionRate(transactionCommissionRate); // Rate stays the same
                    allocation.setCommissionAmount(transactionCommission != null ?
                        transactionCommission.abs().multiply(allocationProportion).setScale(2, java.math.RoundingMode.HALF_UP) : null);
                    allocation.setNetToOwnerAmount(amount.abs());
                }
            }
        }
        // For EXPENSE/DISBURSEMENT, gross/commission/net fields remain null

        // Property info
        allocation.setPropertyId(transaction.getPropertyId());
        allocation.setPropertyName(transaction.getPropertyName());

        // Invoice/Lease info - link allocation to specific lease
        if (transaction.getInvoiceId() != null) {
            allocation.setInvoiceId(transaction.getInvoiceId());
        }

        // Beneficiary info - depends on allocation type
        if (allocationType == AllocationType.DISBURSEMENT) {
            // For DISBURSEMENT (service charge to block), beneficiary is the block property
            allocation.setBeneficiaryType("BLOCK_PROPERTY");
            if (transaction.getPropertyId() != null) {
                propertyRepository.findById(transaction.getPropertyId()).ifPresent(property -> {
                    String blockPropertyName = getBlockPropertyNameForProperty(property);
                    if (blockPropertyName != null) {
                        allocation.setBeneficiaryName(blockPropertyName);
                    } else if (property.getPropertyOwnerId() != null) {
                        // Fallback to owner if no block property found
                        allocation.setBeneficiaryId(property.getPropertyOwnerId());
                        customerRepository.findById(property.getPropertyOwnerId()).ifPresent(owner -> {
                            allocation.setBeneficiaryName(owner.getName());
                        });
                    }
                });
            }
        } else {
            // For other allocation types, beneficiary is the property owner
            allocation.setBeneficiaryType("OWNER");
            if (transaction.getPropertyId() != null) {
                propertyRepository.findById(transaction.getPropertyId()).ifPresent(property -> {
                    if (property.getPropertyOwnerId() != null) {
                        allocation.setBeneficiaryId(property.getPropertyOwnerId());
                        customerRepository.findById(property.getPropertyOwnerId()).ifPresent(owner -> {
                            allocation.setBeneficiaryName(owner.getName());
                        });
                    }
                });
            }
        }

        // Payment tracking
        allocation.setPaymentBatchId(batchReference);
        allocation.setPaymentStatus(PaymentStatus.BATCHED);
        allocation.setSource(AllocationSource.MANUAL);
        allocation.setCreatedBy(userId);

        UnifiedAllocation saved = allocationRepository.save(allocation);

        log.debug("Created unified allocation: unifiedTransactionId={}, batch={}, amount={}, type={}, gross={}, commission={}, net={}",
                transaction.getId(), batchReference, amount, allocationType,
                allocation.getGrossAmount(), allocation.getCommissionAmount(), allocation.getNetToOwnerAmount());

        return saved;
    }

    /**
     * Validate that an allocation amount is valid for a unified transaction
     * All amounts are stored as positive - allocation type determines income vs expense
     */
    public void validateUnifiedAllocation(UnifiedTransaction transaction, BigDecimal amount) {
        BigDecimal netToOwner = transaction.getNetToOwnerAmount();
        if (netToOwner == null) {
            throw new IllegalArgumentException("UnifiedTransaction has no net_to_owner_amount calculated");
        }

        BigDecimal remaining = getRemainingUnallocatedForUnified(transaction.getId());

        // Amount should always be positive (absolute value)
        BigDecimal absAmount = amount.abs();
        BigDecimal absRemaining = remaining.abs();

        if (absAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Allocation amount must be positive");
        }
        if (absAmount.compareTo(absRemaining) > 0) {
            throw new IllegalArgumentException("Allocation amount (" + absAmount +
                    ") exceeds remaining unallocated (" + absRemaining + ")");
        }
    }

    /**
     * Check if a unified transaction can be allocated
     */
    public boolean canAllocateUnified(Long unifiedTransactionId) {
        UnifiedTransaction transaction = unifiedTransactionRepository.findById(unifiedTransactionId).orElse(null);
        if (transaction == null || transaction.getNetToOwnerAmount() == null) {
            return false;
        }

        BigDecimal remaining = getRemainingUnallocatedForUnified(unifiedTransactionId);
        return remaining.compareTo(BigDecimal.ZERO) != 0;
    }

    // ===== UNIFIED TRANSACTION QUERIES =====

    /**
     * Get remaining unallocated amount for a unified transaction
     */
    public BigDecimal getRemainingUnallocatedForUnified(Long unifiedTransactionId) {
        BigDecimal remaining = allocationRepository.getRemainingUnallocatedForUnified(unifiedTransactionId);
        return remaining != null ? remaining : BigDecimal.ZERO;
    }

    /**
     * Get total already allocated for a unified transaction
     */
    public BigDecimal getTotalAllocatedForUnified(Long unifiedTransactionId) {
        return allocationRepository.getTotalAllocatedForUnifiedTransaction(unifiedTransactionId);
    }

    /**
     * Get all allocations for a unified transaction
     */
    public List<UnifiedAllocation> getAllocationsForUnifiedTransaction(Long unifiedTransactionId) {
        return allocationRepository.findByUnifiedTransactionId(unifiedTransactionId);
    }

    // ===== UNALLOCATED TRANSACTIONS =====

    /**
     * Find unified transactions with net_to_owner that haven't been fully allocated for a property
     */
    public List<UnallocatedTransactionDTO> getUnallocatedUnifiedTransactionsForProperty(Long propertyId) {
        List<UnifiedTransaction> transactions = unifiedTransactionRepository.findByPropertyIdWithNetToOwner(propertyId);

        return transactions.stream()
                .map(t -> {
                    BigDecimal remaining = getRemainingUnallocatedForUnified(t.getId());
                    if (remaining.compareTo(BigDecimal.ZERO) != 0) {
                        return new UnallocatedTransactionDTO(
                                t.getId(),
                                t.getTransactionDate(),
                                t.getCategory(),
                                t.getDescription(),
                                t.getAmount(),
                                t.getNetToOwnerAmount(),
                                remaining,
                                t.getPropertyId(),
                                t.getPropertyName()
                        );
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Find all unallocated unified transactions for an owner (across all their properties)
     */
    public List<UnallocatedTransactionDTO> getUnallocatedUnifiedTransactionsForOwner(Long ownerId) {
        List<UnifiedTransaction> transactions = unifiedTransactionRepository.findByOwnerIdWithNetToOwner(ownerId);

        log.debug("Found {} unified transactions for owner {}", transactions.size(), ownerId);

        return transactions.stream()
                .map(t -> {
                    BigDecimal remaining = getRemainingUnallocatedForUnified(t.getId());
                    if (remaining.compareTo(BigDecimal.ZERO) != 0) {
                        return new UnallocatedTransactionDTO(
                                t.getId(),
                                t.getTransactionDate(),
                                t.getCategory(),
                                t.getDescription(),
                                t.getAmount(),
                                t.getNetToOwnerAmount(),
                                remaining,
                                t.getPropertyId(),
                                t.getPropertyName()
                        );
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get total unallocated amount for a property (unified transactions)
     */
    public BigDecimal getTotalUnallocatedForPropertyUnified(Long propertyId) {
        return getUnallocatedUnifiedTransactionsForProperty(propertyId).stream()
                .map(UnallocatedTransactionDTO::getRemainingUnallocated)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get total unallocated amount for an owner (unified transactions)
     */
    public BigDecimal getTotalUnallocatedForOwnerUnified(Long ownerId) {
        return getUnallocatedUnifiedTransactionsForOwner(ownerId).stream()
                .map(UnallocatedTransactionDTO::getRemainingUnallocated)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ===== BATCH MANAGEMENT =====

    /**
     * Generate a new batch reference
     * Format: OWNER-YYYYMMDD-XXXX (where XXXX is sequential)
     */
    public String generateBatchReference(String prefix) {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String baseRef = prefix + "-" + dateStr + "-";

        // Find highest existing sequence for today
        List<String> todaysBatches = allocationRepository.findAllDistinctBatchReferences().stream()
                .filter(ref -> ref != null && ref.startsWith(baseRef))
                .collect(Collectors.toList());

        int sequence = 1;
        if (!todaysBatches.isEmpty()) {
            sequence = todaysBatches.stream()
                    .map(ref -> {
                        try {
                            return Integer.parseInt(ref.substring(baseRef.length()));
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .max(Integer::compare)
                    .orElse(0) + 1;
        }

        return baseRef + String.format("%04d", sequence);
    }

    /**
     * Get batch total (sum of all allocations)
     */
    public BigDecimal getBatchTotal(String batchReference) {
        return allocationRepository.getTotalForBatch(batchReference);
    }

    /**
     * Remove all allocations for a batch
     */
    public int removeBatchAllocations(String batchReference) {
        int deleted = allocationRepository.deleteByBatchReference(batchReference);
        log.info("Removed {} allocations for batch {}", deleted, batchReference);
        return deleted;
    }

    /**
     * Remove all allocations for a unified transaction
     */
    public int removeUnifiedTransactionAllocations(Long unifiedTransactionId) {
        int deleted = allocationRepository.deleteByUnifiedTransactionId(unifiedTransactionId);
        log.info("Removed {} allocations for unified transaction {}", deleted, unifiedTransactionId);
        return deleted;
    }

    /**
     * Remove a single allocation by ID
     */
    public boolean removeAllocation(Long allocationId) {
        if (allocationRepository.existsById(allocationId)) {
            allocationRepository.deleteById(allocationId);
            log.info("Removed allocation {}", allocationId);
            return true;
        }
        log.warn("Allocation {} not found for removal", allocationId);
        return false;
    }

    // ===== BATCH SUMMARY =====

    /**
     * Get summary of a batch (checks both UnifiedAllocation and PaymentBatch)
     */
    public BatchSummaryDTO getBatchSummary(String batchReference) {
        List<UnifiedAllocation> allocations = allocationRepository.findByPaymentBatchId(batchReference);

        // Try to get the PaymentBatch for original payment amount
        Optional<PaymentBatch> paymentBatchOpt = paymentBatchRepository.findByBatchId(batchReference);
        PaymentBatch pb = paymentBatchOpt.orElse(null);

        // Calculate allocation totals
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        Set<Long> propertyIds = new HashSet<>();
        Set<Long> transactionIds = new HashSet<>();

        for (UnifiedAllocation alloc : allocations) {
            if (alloc.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                totalIncome = totalIncome.add(alloc.getAmount());
            } else {
                totalExpenses = totalExpenses.add(alloc.getAmount());
            }
            if (alloc.getPropertyId() != null) {
                propertyIds.add(alloc.getPropertyId());
            }
            if (alloc.getUnifiedTransactionId() != null) {
                transactionIds.add(alloc.getUnifiedTransactionId());
            } else if (alloc.getHistoricalTransactionId() != null) {
                transactionIds.add(alloc.getHistoricalTransactionId());
            }
        }

        BigDecimal allocatedTotal = totalIncome.add(totalExpenses);

        // If we have a PaymentBatch, use its payment amount as original
        if (pb != null) {
            BigDecimal originalPayment = pb.getTotalPayment() != null
                ? pb.getTotalPayment().abs()
                : BigDecimal.ZERO;

            return new BatchSummaryDTO(
                    batchReference,
                    allocations.size(),
                    transactionIds.size(),
                    propertyIds.size(),
                    totalIncome,
                    totalExpenses,
                    allocatedTotal,
                    originalPayment,
                    pb.getStatus() != null ? pb.getStatus().name().toLowerCase() : "draft",
                    pb.getPaymentDate()
            );
        }

        // No PaymentBatch found
        if (!allocations.isEmpty()) {
            return new BatchSummaryDTO(
                    batchReference,
                    allocations.size(),
                    transactionIds.size(),
                    propertyIds.size(),
                    totalIncome,
                    totalExpenses,
                    allocatedTotal
            );
        }

        return null;
    }

    /**
     * Get all batch summaries for an owner
     */
    public List<BatchSummaryDTO> getBatchSummariesForOwner(Long ownerId) {
        Map<String, BatchSummaryDTO> batchMap = new LinkedHashMap<>();

        // First, get existing PaymentBatch records for this owner (excluding PayProp)
        List<PaymentBatch> paymentBatches = paymentBatchRepository.findByBeneficiaryIdAndSourceNotOrderByPaymentDateDesc(
                ownerId, PaymentBatch.BatchSource.PAYPROP);
        for (PaymentBatch pb : paymentBatches) {
            BatchSummaryDTO dto = new BatchSummaryDTO(
                    pb.getBatchId(),
                    0,
                    0,
                    0,
                    pb.getTotalAllocations() != null ? pb.getTotalAllocations() : BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    pb.getTotalPayment() != null ? pb.getTotalPayment() : BigDecimal.ZERO,
                    pb.getStatus() != null ? pb.getStatus().name().toLowerCase() : "draft",
                    pb.getPaymentDate()
            );
            batchMap.put(pb.getBatchId(), dto);
        }

        // Then, get batches from UnifiedAllocation
        List<String> batchRefs = allocationRepository.findDistinctBatchReferencesByBeneficiaryId(ownerId);
        for (String batchRef : batchRefs) {
            if (!batchMap.containsKey(batchRef)) {
                BatchSummaryDTO summary = getBatchSummary(batchRef);
                if (summary != null) {
                    batchMap.put(batchRef, summary);
                }
            }
        }

        return new ArrayList<>(batchMap.values());
    }

    /**
     * Get all batch summaries
     */
    public List<BatchSummaryDTO> getAllBatchSummaries() {
        Map<String, BatchSummaryDTO> batchMap = new LinkedHashMap<>();

        // First, get all PaymentBatch records
        List<PaymentBatch> paymentBatches = paymentBatchRepository.findAll();
        for (PaymentBatch pb : paymentBatches) {
            BatchSummaryDTO dto = new BatchSummaryDTO(
                    pb.getBatchId(),
                    0,
                    0,
                    0,
                    pb.getTotalAllocations() != null ? pb.getTotalAllocations() : BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    pb.getTotalPayment() != null ? pb.getTotalPayment() : BigDecimal.ZERO,
                    pb.getStatus() != null ? pb.getStatus().name().toLowerCase() : "draft",
                    pb.getPaymentDate()
            );
            batchMap.put(pb.getBatchId(), dto);
        }

        // Then add any from UnifiedAllocation not already in map
        List<String> batchRefs = allocationRepository.findAllDistinctBatchReferences();
        for (String batchRef : batchRefs) {
            if (!batchMap.containsKey(batchRef)) {
                BatchSummaryDTO summary = getBatchSummary(batchRef);
                if (summary != null) {
                    batchMap.put(batchRef, summary);
                }
            }
        }

        return new ArrayList<>(batchMap.values());
    }

    // ===== HELPER METHODS =====

    private UnifiedTransaction getUnifiedTransaction(Long unifiedTransactionId) {
        return unifiedTransactionRepository.findById(unifiedTransactionId)
                .orElseThrow(() -> new IllegalArgumentException("UnifiedTransaction not found: " + unifiedTransactionId));
    }

    // ===== DTOs =====

    /**
     * DTO for unallocated transaction info
     */
    public static class UnallocatedTransactionDTO {
        private final Long transactionId;
        private final LocalDate transactionDate;
        private final String category;
        private final String description;
        private final BigDecimal amount;
        private final BigDecimal netToOwner;
        private final BigDecimal remainingUnallocated;
        private final Long propertyId;
        private final String propertyName;

        public UnallocatedTransactionDTO(Long transactionId, LocalDate transactionDate, String category,
                                         String description, BigDecimal amount, BigDecimal netToOwner,
                                         BigDecimal remainingUnallocated, Long propertyId, String propertyName) {
            this.transactionId = transactionId;
            this.transactionDate = transactionDate;
            this.category = category;
            this.description = description;
            this.amount = amount;
            this.netToOwner = netToOwner;
            this.remainingUnallocated = remainingUnallocated;
            this.propertyId = propertyId;
            this.propertyName = propertyName;
        }

        public Long getTransactionId() { return transactionId; }
        public LocalDate getTransactionDate() { return transactionDate; }
        public String getCategory() { return category; }
        public String getDescription() { return description; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getNetToOwner() { return netToOwner; }
        public BigDecimal getRemainingUnallocated() { return remainingUnallocated; }
        public Long getPropertyId() { return propertyId; }
        public String getPropertyName() { return propertyName; }
    }

    /**
     * DTO for batch summary
     */
    public static class BatchSummaryDTO {
        private final String batchReference;
        private final int allocationCount;
        private final int transactionCount;
        private final int propertyCount;
        private final BigDecimal totalIncome;
        private final BigDecimal totalExpenses;
        private final BigDecimal netTotal;
        private final BigDecimal originalPaymentAmount;
        private final String status;
        private final LocalDate paymentDate;

        public BatchSummaryDTO(String batchReference, int allocationCount, int transactionCount,
                               int propertyCount, BigDecimal totalIncome, BigDecimal totalExpenses,
                               BigDecimal netTotal) {
            this(batchReference, allocationCount, transactionCount, propertyCount,
                 totalIncome, totalExpenses, netTotal, null, "draft", null);
        }

        public BatchSummaryDTO(String batchReference, int allocationCount, int transactionCount,
                               int propertyCount, BigDecimal totalIncome, BigDecimal totalExpenses,
                               BigDecimal netTotal, String status, LocalDate paymentDate) {
            this(batchReference, allocationCount, transactionCount, propertyCount,
                 totalIncome, totalExpenses, netTotal, null, status, paymentDate);
        }

        public BatchSummaryDTO(String batchReference, int allocationCount, int transactionCount,
                               int propertyCount, BigDecimal totalIncome, BigDecimal totalExpenses,
                               BigDecimal netTotal, BigDecimal originalPaymentAmount,
                               String status, LocalDate paymentDate) {
            this.batchReference = batchReference;
            this.allocationCount = allocationCount;
            this.transactionCount = transactionCount;
            this.propertyCount = propertyCount;
            this.totalIncome = totalIncome;
            this.totalExpenses = totalExpenses;
            this.netTotal = netTotal;
            this.originalPaymentAmount = originalPaymentAmount;
            this.status = status;
            this.paymentDate = paymentDate;
        }

        public String getBatchReference() { return batchReference; }
        public int getAllocationCount() { return allocationCount; }
        public int getTransactionCount() { return transactionCount; }
        public int getPropertyCount() { return propertyCount; }
        public BigDecimal getTotalIncome() { return totalIncome; }
        public BigDecimal getTotalExpenses() { return totalExpenses; }
        public BigDecimal getNetTotal() { return netTotal; }
        public BigDecimal getOriginalPaymentAmount() { return originalPaymentAmount; }
        public String getStatus() { return status; }
        public LocalDate getPaymentDate() { return paymentDate; }
    }

    // ===== HELPER: Block Property Name =====

    /**
     * Get the block property name for a property that belongs to a block.
     * For service charge disbursements, this is the beneficiary (the block property receives the funds).
     *
     * @param property The flat/unit property
     * @return The block property name, or null if property doesn't belong to a block
     */
    private String getBlockPropertyNameForProperty(Property property) {
        if (property == null) {
            return null;
        }

        // Check if property has a block assigned
        if (property.getBlock() != null) {
            Property blockProperty = property.getBlock().getBlockProperty();
            if (blockProperty != null) {
                return blockProperty.getPropertyName();
            }
        }

        // Fallback: Try to find block by property name pattern (e.g., "Flat X - 3 West Gate" -> "Boden House Block")
        String propertyName = property.getPropertyName();
        if (propertyName != null && propertyName.contains(" - ")) {
            // Extract the address part (e.g., "3 West Gate" from "Flat 1 - 3 West Gate")
            String addressPart = propertyName.substring(propertyName.lastIndexOf(" - ") + 3);

            // Find blocks where properties have matching address patterns
            for (Block block : blockRepository.findAll()) {
                if (block.getBlockProperty() != null) {
                    String blockPropertyName = block.getBlockProperty().getPropertyName();
                    if (blockPropertyName != null && blockPropertyName.contains(addressPart)) {
                        return blockPropertyName;
                    }
                }
            }
        }

        return null;
    }
}

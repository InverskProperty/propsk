package site.easy.to.build.crm.service.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.HistoricalTransaction;
import site.easy.to.build.crm.entity.PaymentBatch;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.TransactionBatchAllocation;
import site.easy.to.build.crm.repository.HistoricalTransactionRepository;
import site.easy.to.build.crm.repository.PaymentBatchRepository;
import site.easy.to.build.crm.repository.TransactionBatchAllocationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TransactionBatchAllocationService - Manages allocation of transactions to payment batches
 *
 * This service handles:
 * - Full allocation of a transaction to a single batch
 * - Split allocation of a transaction across multiple batches
 * - Validation that allocations don't exceed net_to_owner_amount
 * - Finding unallocated transactions for a property/owner
 * - Batch creation and management
 *
 * Example scenarios:
 *   Transaction A (£1000 net) → 100% to Batch LMN
 *   Transaction B (£1000 net) → 100% to Batch OPQ
 *   Transaction C (£1000 net) → £500 to Batch LMN, £500 to Batch OPQ
 */
@Service
@Transactional
public class TransactionBatchAllocationService {

    private static final Logger log = LoggerFactory.getLogger(TransactionBatchAllocationService.class);

    @Autowired
    private TransactionBatchAllocationRepository allocationRepository;

    @Autowired
    private HistoricalTransactionRepository transactionRepository;

    @Autowired
    private PaymentBatchRepository paymentBatchRepository;

    // ===== ALLOCATION CREATION =====

    /**
     * Allocate a full transaction to a batch (100% of net_to_owner_amount)
     */
    public TransactionBatchAllocation allocateFullTransaction(Long transactionId, String batchReference, Long userId) {
        HistoricalTransaction transaction = getTransaction(transactionId);

        BigDecimal netToOwner = transaction.getNetToOwnerAmount();
        if (netToOwner == null) {
            throw new IllegalArgumentException("Transaction " + transactionId + " has no net_to_owner_amount calculated");
        }

        BigDecimal remaining = getRemainingUnallocated(transactionId);
        if (remaining.compareTo(netToOwner) != 0) {
            throw new IllegalStateException("Transaction " + transactionId + " is already partially allocated. " +
                    "Remaining: " + remaining + ", Total: " + netToOwner);
        }

        return createAllocation(transaction, batchReference, netToOwner, userId);
    }

    /**
     * Allocate a specific amount from a transaction to a batch (partial allocation)
     */
    public TransactionBatchAllocation allocatePartialTransaction(Long transactionId, String batchReference,
                                                                  BigDecimal amount, Long userId) {
        HistoricalTransaction transaction = getTransaction(transactionId);

        // Validate the allocation
        validateAllocation(transaction, amount);

        return createAllocation(transaction, batchReference, amount, userId);
    }

    /**
     * Allocate remaining unallocated amount from a transaction to a batch
     */
    public TransactionBatchAllocation allocateRemainingToTransaction(Long transactionId, String batchReference, Long userId) {
        HistoricalTransaction transaction = getTransaction(transactionId);

        BigDecimal remaining = getRemainingUnallocated(transactionId);
        if (remaining.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalStateException("Transaction " + transactionId + " is fully allocated");
        }

        return createAllocation(transaction, batchReference, remaining, userId);
    }

    /**
     * Allocate multiple transactions fully to a single batch
     */
    public List<TransactionBatchAllocation> allocateTransactionsToBatch(List<Long> transactionIds,
                                                                         String batchReference, Long userId) {
        List<TransactionBatchAllocation> allocations = new ArrayList<>();

        for (Long transactionId : transactionIds) {
            try {
                TransactionBatchAllocation allocation = allocateFullTransaction(transactionId, batchReference, userId);
                allocations.add(allocation);
            } catch (Exception e) {
                log.warn("Failed to allocate transaction {} to batch {}: {}",
                        transactionId, batchReference, e.getMessage());
                // Continue with other transactions
            }
        }

        log.info("Allocated {} of {} transactions to batch {}",
                allocations.size(), transactionIds.size(), batchReference);
        return allocations;
    }

    // ===== HELPER: CREATE ALLOCATION =====

    private TransactionBatchAllocation createAllocation(HistoricalTransaction transaction,
                                                        String batchReference,
                                                        BigDecimal amount,
                                                        Long userId) {
        TransactionBatchAllocation allocation = new TransactionBatchAllocation(transaction, batchReference, amount);

        // Set beneficiary info if available
        Customer owner = getOwnerFromTransaction(transaction);
        if (owner != null) {
            allocation.setBeneficiaryId((long) owner.getCustomerId());
            allocation.setBeneficiaryName(owner.getName());
        }

        allocation.setCreatedBy(userId);

        TransactionBatchAllocation saved = allocationRepository.save(allocation);

        log.debug("Created allocation: transaction={}, batch={}, amount={}",
                transaction.getId(), batchReference, amount);

        return saved;
    }

    // ===== VALIDATION =====

    /**
     * Validate that an allocation amount is valid for a transaction
     */
    public void validateAllocation(HistoricalTransaction transaction, BigDecimal amount) {
        BigDecimal netToOwner = transaction.getNetToOwnerAmount();
        if (netToOwner == null) {
            throw new IllegalArgumentException("Transaction has no net_to_owner_amount calculated");
        }

        BigDecimal remaining = getRemainingUnallocated(transaction.getId());

        // For positive net (income), amount should be positive and not exceed remaining
        if (netToOwner.compareTo(BigDecimal.ZERO) >= 0) {
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Allocation amount must be positive for income transaction");
            }
            if (amount.compareTo(remaining) > 0) {
                throw new IllegalArgumentException("Allocation amount (" + amount +
                        ") exceeds remaining unallocated (" + remaining + ")");
            }
        }
        // For negative net (expense), amount should be negative and not exceed remaining (more negative)
        else {
            if (amount.compareTo(BigDecimal.ZERO) >= 0) {
                throw new IllegalArgumentException("Allocation amount must be negative for expense transaction");
            }
            if (amount.compareTo(remaining) < 0) {
                throw new IllegalArgumentException("Allocation amount (" + amount +
                        ") exceeds remaining unallocated (" + remaining + ")");
            }
        }
    }

    /**
     * Check if a transaction can be allocated (has net_to_owner and not fully allocated)
     */
    public boolean canAllocate(Long transactionId) {
        HistoricalTransaction transaction = transactionRepository.findById(transactionId).orElse(null);
        if (transaction == null || transaction.getNetToOwnerAmount() == null) {
            return false;
        }

        BigDecimal remaining = getRemainingUnallocated(transactionId);
        return remaining.compareTo(BigDecimal.ZERO) != 0;
    }

    // ===== QUERIES =====

    /**
     * Get remaining unallocated amount for a transaction
     */
    public BigDecimal getRemainingUnallocated(Long transactionId) {
        BigDecimal remaining = allocationRepository.getRemainingUnallocated(transactionId);
        return remaining != null ? remaining : BigDecimal.ZERO;
    }

    /**
     * Get total already allocated for a transaction
     */
    public BigDecimal getTotalAllocated(Long transactionId) {
        return allocationRepository.getTotalAllocatedForTransaction(transactionId);
    }

    /**
     * Get all allocations for a transaction
     */
    public List<TransactionBatchAllocation> getAllocationsForTransaction(Long transactionId) {
        return allocationRepository.findByTransactionId(transactionId);
    }

    /**
     * Get all allocations for a batch
     */
    public List<TransactionBatchAllocation> getAllocationsForBatch(String batchReference) {
        return allocationRepository.findByBatchReference(batchReference);
    }

    /**
     * Get batch total (sum of all allocations)
     */
    public BigDecimal getBatchTotal(String batchReference) {
        return allocationRepository.getTotalForBatch(batchReference);
    }

    // ===== UNALLOCATED TRANSACTIONS =====

    /**
     * Find transactions with net_to_owner that haven't been fully allocated for a property
     */
    public List<UnallocatedTransactionDTO> getUnallocatedTransactionsForProperty(Long propertyId) {
        List<HistoricalTransaction> transactions = transactionRepository.findByPropertyIdWithNetToOwner(propertyId);

        return transactions.stream()
                .map(t -> {
                    BigDecimal remaining = getRemainingUnallocated(t.getId());
                    if (remaining.compareTo(BigDecimal.ZERO) != 0) {
                        return new UnallocatedTransactionDTO(
                                t.getId(),
                                t.getTransactionDate(),
                                t.getCategory(),
                                t.getDescription(),
                                t.getAmount(),
                                t.getNetToOwnerAmount(),
                                remaining
                        );
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Find all unallocated transactions for an owner (across all their properties)
     */
    public List<UnallocatedTransactionDTO> getUnallocatedTransactionsForOwner(Long ownerId) {
        List<HistoricalTransaction> transactions = transactionRepository.findByOwnerIdWithNetToOwner(ownerId);

        return transactions.stream()
                .map(t -> {
                    BigDecimal remaining = getRemainingUnallocated(t.getId());
                    if (remaining.compareTo(BigDecimal.ZERO) != 0) {
                        return new UnallocatedTransactionDTO(
                                t.getId(),
                                t.getTransactionDate(),
                                t.getCategory(),
                                t.getDescription(),
                                t.getAmount(),
                                t.getNetToOwnerAmount(),
                                remaining,
                                t.getProperty() != null ? t.getProperty().getId() : null,
                                t.getProperty() != null ? t.getProperty().getPropertyName() : null
                        );
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get total unallocated amount for a property
     */
    public BigDecimal getTotalUnallocatedForProperty(Long propertyId) {
        return getUnallocatedTransactionsForProperty(propertyId).stream()
                .map(UnallocatedTransactionDTO::getRemainingUnallocated)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get total unallocated amount for an owner
     */
    public BigDecimal getTotalUnallocatedForOwner(Long ownerId) {
        return getUnallocatedTransactionsForOwner(ownerId).stream()
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
        int sequence = 1;
        List<TransactionBatchAllocation> todaysAllocations = allocationRepository.findAll().stream()
                .filter(a -> a.getBatchReference() != null && a.getBatchReference().startsWith(baseRef))
                .collect(Collectors.toList());

        if (!todaysAllocations.isEmpty()) {
            sequence = todaysAllocations.stream()
                    .map(a -> {
                        try {
                            return Integer.parseInt(a.getBatchReference().substring(baseRef.length()));
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
     * Remove all allocations for a batch
     */
    public int removeBatchAllocations(String batchReference) {
        int deleted = allocationRepository.deleteByBatchReference(batchReference);
        log.info("Removed {} allocations for batch {}", deleted, batchReference);
        return deleted;
    }

    /**
     * Remove all allocations for a transaction
     */
    public int removeTransactionAllocations(Long transactionId) {
        int deleted = allocationRepository.deleteByTransactionId(transactionId);
        log.info("Removed {} allocations for transaction {}", deleted, transactionId);
        return deleted;
    }

    // ===== BATCH SUMMARY =====

    /**
     * Get summary of a batch
     */
    public BatchSummaryDTO getBatchSummary(String batchReference) {
        List<TransactionBatchAllocation> allocations = allocationRepository.findByBatchReference(batchReference);

        if (allocations.isEmpty()) {
            return null;
        }

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        Set<Long> propertyIds = new HashSet<>();
        Set<Long> transactionIds = new HashSet<>();

        for (TransactionBatchAllocation alloc : allocations) {
            if (alloc.getAllocatedAmount().compareTo(BigDecimal.ZERO) > 0) {
                totalIncome = totalIncome.add(alloc.getAllocatedAmount());
            } else {
                totalExpenses = totalExpenses.add(alloc.getAllocatedAmount());
            }
            if (alloc.getPropertyId() != null) {
                propertyIds.add(alloc.getPropertyId());
            }
            if (alloc.getTransactionId() != null) {
                transactionIds.add(alloc.getTransactionId());
            }
        }

        return new BatchSummaryDTO(
                batchReference,
                allocations.size(),
                transactionIds.size(),
                propertyIds.size(),
                totalIncome,
                totalExpenses,
                totalIncome.add(totalExpenses)  // net total
        );
    }

    // ===== LIST BATCHES =====

    /**
     * Get all batch summaries for an owner (combines PaymentBatch and TransactionBatchAllocation)
     */
    public List<BatchSummaryDTO> getBatchSummariesForOwner(Long ownerId) {
        Map<String, BatchSummaryDTO> batchMap = new LinkedHashMap<>();

        // First, get existing PaymentBatch records for this owner
        List<PaymentBatch> paymentBatches = paymentBatchRepository.findByBeneficiaryId(ownerId);
        for (PaymentBatch pb : paymentBatches) {
            BatchSummaryDTO dto = new BatchSummaryDTO(
                    pb.getBatchId(),
                    0, // Will update if allocations exist
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

        // Then, get batches from TransactionBatchAllocation (may overlap or be new)
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

        // Then add any from TransactionBatchAllocation not already in map
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

    private HistoricalTransaction getTransaction(Long transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
    }

    private Customer getOwnerFromTransaction(HistoricalTransaction transaction) {
        // Try to get owner from transaction's beneficiary field first
        if (transaction.getBeneficiary() != null) {
            return transaction.getBeneficiary();
        }
        // Otherwise try to get from property owner assignment
        if (transaction.getProperty() != null) {
            // This would need PropertyOwnerRepository - for now return null
            return null;
        }
        return null;
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
                                         BigDecimal remainingUnallocated) {
            this(transactionId, transactionDate, category, description, amount, netToOwner,
                 remainingUnallocated, null, null);
        }

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
        private final String status;
        private final LocalDate paymentDate;

        public BatchSummaryDTO(String batchReference, int allocationCount, int transactionCount,
                               int propertyCount, BigDecimal totalIncome, BigDecimal totalExpenses,
                               BigDecimal netTotal) {
            this(batchReference, allocationCount, transactionCount, propertyCount,
                 totalIncome, totalExpenses, netTotal, "draft", null);
        }

        public BatchSummaryDTO(String batchReference, int allocationCount, int transactionCount,
                               int propertyCount, BigDecimal totalIncome, BigDecimal totalExpenses,
                               BigDecimal netTotal, String status, LocalDate paymentDate) {
            this.batchReference = batchReference;
            this.allocationCount = allocationCount;
            this.transactionCount = transactionCount;
            this.propertyCount = propertyCount;
            this.totalIncome = totalIncome;
            this.totalExpenses = totalExpenses;
            this.netTotal = netTotal;
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
        public String getStatus() { return status; }
        public LocalDate getPaymentDate() { return paymentDate; }
    }
}

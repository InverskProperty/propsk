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
import site.easy.to.build.crm.entity.PropertyBalanceLedger;
import site.easy.to.build.crm.entity.TransactionBatchAllocation;
import site.easy.to.build.crm.entity.UnifiedAllocation;
import site.easy.to.build.crm.entity.AssignmentType;
import site.easy.to.build.crm.entity.CustomerPropertyAssignment;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.repository.HistoricalTransactionRepository;
import site.easy.to.build.crm.repository.PaymentBatchRepository;
import site.easy.to.build.crm.repository.PropertyBalanceLedgerRepository;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.repository.TransactionBatchAllocationRepository;
import site.easy.to.build.crm.repository.UnifiedAllocationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Optional;
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
 *
 * @deprecated Use {@link UnifiedAllocationService} instead. This service works with
 * {@link TransactionBatchAllocation} which is being phased out in favor of
 * {@link site.easy.to.build.crm.entity.UnifiedAllocation}.
 *
 * Migration guide:
 * - allocateFullTransaction() → allocateFullUnifiedTransaction()
 * - allocatePartialTransaction() → allocatePartialUnifiedTransaction()
 * - getUnallocatedTransactionsForOwner() → getUnallocatedUnifiedTransactionsForOwner()
 * - getBatchSummary() → UnifiedAllocationService.getBatchSummary()
 *
 * The data has been migrated from transaction_batch_allocations to unified_allocations.
 * This service continues to work for backwards compatibility during the transition period.
 */
@Deprecated
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

    @Autowired
    private PropertyBalanceLedgerRepository ledgerRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private UnifiedAllocationRepository unifiedAllocationRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerPropertyAssignmentRepository customerPropertyAssignmentRepository;

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

        // Also create a UnifiedAllocation record for immediate statement visibility
        createUnifiedAllocationFromHistorical(transaction, batchReference, amount, userId, saved.getId(), owner);

        return saved;
    }

    /**
     * Create a UnifiedAllocation record from a HistoricalTransaction allocation.
     * This ensures the allocation is immediately visible in statements without waiting for rebuild.
     */
    private void createUnifiedAllocationFromHistorical(HistoricalTransaction transaction,
                                                        String batchReference,
                                                        BigDecimal amount,
                                                        Long userId,
                                                        Long sourceRecordId,
                                                        Customer owner) {
        try {
            UnifiedAllocation unified = new UnifiedAllocation();

            // Link to historical transaction
            unified.setHistoricalTransactionId(transaction.getId());

            // Determine allocation type based on category and transaction attributes
            UnifiedAllocation.AllocationType allocationType = determineAllocationType(transaction);
            unified.setAllocationType(allocationType);

            // Amount - keep the sign! Negative for expenses, positive for income
            // This is critical for correct balance calculations
            unified.setAmount(amount);

            // For OWNER allocations (rent), populate gross/commission/net breakdown
            if (allocationType == UnifiedAllocation.AllocationType.OWNER) {
                BigDecimal transactionGross = transaction.getAmount();
                BigDecimal transactionNet = transaction.getNetToOwnerAmount();
                BigDecimal transactionCommission = transaction.getCommissionAmount();
                BigDecimal transactionCommissionRate = transaction.getCommissionRate();

                if (transactionNet != null && transactionNet.abs().compareTo(BigDecimal.ZERO) > 0) {
                    // Calculate proportion of transaction being allocated
                    BigDecimal allocationProportion = amount.abs().divide(
                        transactionNet.abs(), 10, java.math.RoundingMode.HALF_UP);

                    if (allocationProportion.compareTo(BigDecimal.ONE) >= 0) {
                        // Full allocation - use actual values
                        unified.setGrossAmount(transactionGross != null ? transactionGross.abs() : null);
                        unified.setCommissionRate(transactionCommissionRate);
                        unified.setCommissionAmount(transactionCommission != null ? transactionCommission.abs() : null);
                        unified.setNetToOwnerAmount(transactionNet.abs());
                    } else {
                        // Partial allocation - calculate proportional amounts
                        unified.setGrossAmount(transactionGross != null ?
                            transactionGross.abs().multiply(allocationProportion).setScale(2, java.math.RoundingMode.HALF_UP) : null);
                        unified.setCommissionRate(transactionCommissionRate);
                        unified.setCommissionAmount(transactionCommission != null ?
                            transactionCommission.abs().multiply(allocationProportion).setScale(2, java.math.RoundingMode.HALF_UP) : null);
                        unified.setNetToOwnerAmount(amount.abs());
                    }
                }
            }

            // Category and description
            unified.setCategory(transaction.getCategory());
            unified.setDescription(transaction.getDescription());

            // Property info - try entity first, then fall back to direct ID lookup
            if (transaction.getProperty() != null) {
                unified.setPropertyId(transaction.getProperty().getId());
                unified.setPropertyName(transaction.getProperty().getPropertyName());
            } else if (transaction.getPropertyId() != null) {
                // Fallback: use the property_id directly if the entity wasn't loaded
                unified.setPropertyId(transaction.getPropertyId());
                // Try to get property name from repository
                Property property = propertyRepository.findById(transaction.getPropertyId()).orElse(null);
                if (property != null) {
                    unified.setPropertyName(property.getPropertyName());
                }
            }

            // Invoice/lease info
            if (transaction.getInvoice() != null) {
                unified.setInvoiceId(transaction.getInvoice().getId());
            }

            // Beneficiary info
            unified.setBeneficiaryType("OWNER");
            if (owner != null) {
                unified.setBeneficiaryId((long) owner.getCustomerId());
                unified.setBeneficiaryName(owner.getName());
            }

            // Payment tracking
            unified.setPaymentBatchId(batchReference);
            unified.setPaymentStatus(UnifiedAllocation.PaymentStatus.BATCHED);
            unified.setSource(UnifiedAllocation.AllocationSource.MANUAL);
            unified.setSourceRecordId(sourceRecordId);
            unified.setCreatedBy(userId);

            unifiedAllocationRepository.save(unified);

            log.debug("Created unified allocation for historical transaction: txn={}, batch={}, amount={}, type={}, propertyId={}, gross={}, commission={}, net={}",
                    transaction.getId(), batchReference, amount, allocationType, unified.getPropertyId(),
                    unified.getGrossAmount(), unified.getCommissionAmount(), unified.getNetToOwnerAmount());

        } catch (Exception e) {
            log.warn("Failed to create unified allocation for historical transaction {}: {}",
                    transaction.getId(), e.getMessage());
            // Don't fail the main allocation - this is a sync operation
        }
    }

    /**
     * Determine allocation type from transaction attributes.
     *
     * Classification logic:
     * - COMMISSION: Transactions that represent percentage-based agency/management fees
     *   (identified by having a non-zero commission rate OR category explicitly "commission"/"agency_fee")
     * - OWNER: Rent income and tenant payments
     * - EXPENSE: Fixed-cost items (management expenses like inventory, clearance, repairs)
     * - DISBURSEMENT: Block property or explicit disbursement items
     *
     * NOTE: The old logic classified "management" category as COMMISSION, but "management expenses"
     * (like Inventory, Clearance) are EXPENSES, not commission. True commission has a commission_rate.
     */
    private UnifiedAllocation.AllocationType determineAllocationType(HistoricalTransaction transaction) {
        String category = transaction.getCategory();
        BigDecimal commissionRate = transaction.getCommissionRate();
        BigDecimal commissionAmount = transaction.getCommissionAmount();
        String description = transaction.getDescription();

        if (category == null) {
            return UnifiedAllocation.AllocationType.EXPENSE;
        }

        String lowerCategory = category.toLowerCase();
        String lowerDescription = description != null ? description.toLowerCase() : "";

        // OWNER: Rent income and tenant payments (check first as these are the most common)
        if (lowerCategory.contains("rent") || lowerCategory.contains("income") ||
            lowerCategory.contains("tenant")) {
            return UnifiedAllocation.AllocationType.OWNER;
        }

        // DISBURSEMENT: Block property or explicit disbursement items
        if (lowerCategory.contains("disbursement") || lowerCategory.contains("block property")) {
            return UnifiedAllocation.AllocationType.DISBURSEMENT;
        }

        // COMMISSION: Only if it's explicitly commission/agency fee OR has a commission rate
        // "Management" category alone does NOT make it commission - that's for management EXPENSES
        boolean hasCommissionRate = commissionRate != null && commissionRate.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCommissionAmount = commissionAmount != null && commissionAmount.compareTo(BigDecimal.ZERO) != 0;
        boolean isExplicitCommission = lowerCategory.contains("commission") || lowerCategory.contains("agency_fee");

        // Only treat as COMMISSION if explicitly commission-type OR has a commission rate
        // Management category items without commission rate are EXPENSES (e.g., Inventory, Clearance)
        if (isExplicitCommission || hasCommissionRate) {
            return UnifiedAllocation.AllocationType.COMMISSION;
        }

        // Default to EXPENSE for unrecognized categories (including "management" expenses)
        return UnifiedAllocation.AllocationType.EXPENSE;
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
     * Tries multiple approaches: beneficiary/owner field first, then property ownership
     */
    public List<UnallocatedTransactionDTO> getUnallocatedTransactionsForOwner(Long ownerId) {
        // First try direct owner/beneficiary link
        List<HistoricalTransaction> transactions = transactionRepository.findByOwnerIdWithNetToOwner(ownerId);

        // If no results, try via property ownership
        if (transactions.isEmpty()) {
            log.debug("No transactions found via beneficiary/owner for owner {}, trying property ownership", ownerId);
            transactions = transactionRepository.findByPropertyOwnerIdWithNetToOwner(ownerId);
        }

        log.debug("Found {} transactions for owner {}", transactions.size(), ownerId);

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

    // ===== PAYMENT STATUS MANAGEMENT =====

    /**
     * Mark a payment batch as paid
     */
    public boolean markBatchAsPaid(String batchReference) {
        Optional<PaymentBatch> batchOpt = paymentBatchRepository.findByBatchId(batchReference);
        if (batchOpt.isPresent()) {
            PaymentBatch batch = batchOpt.get();
            batch.setStatus(PaymentBatch.BatchStatus.PAID);
            paymentBatchRepository.save(batch);
            log.info("Marked batch {} as paid", batchReference);
            return true;
        }
        log.warn("Payment batch {} not found", batchReference);
        return false;
    }

    /**
     * Allocate remaining balance to a property account
     * Creates a ledger entry to record the deposit to the property's balance
     */
    public void allocateRemainingToPropertyAccount(String batchReference, Long propertyId, BigDecimal amount, Long userId) {
        // Validate property exists
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found: " + propertyId));

        // Get current balance
        BigDecimal currentBalance = property.getAccountBalance() != null ? property.getAccountBalance() : BigDecimal.ZERO;
        BigDecimal newBalance = currentBalance.add(amount);

        // Create ledger entry
        PropertyBalanceLedger ledgerEntry = new PropertyBalanceLedger();
        ledgerEntry.setPropertyId(propertyId);
        ledgerEntry.setPropertyName(property.getPropertyName());
        ledgerEntry.setEntryType(PropertyBalanceLedger.EntryType.DEPOSIT);
        ledgerEntry.setAmount(amount);
        ledgerEntry.setRunningBalance(newBalance);
        ledgerEntry.setDescription("Remaining balance from payment batch " + batchReference);
        ledgerEntry.setPaymentBatchId(batchReference);
        ledgerEntry.setSource(PropertyBalanceLedger.Source.PAYMENT_BATCH);
        ledgerEntry.setEntryDate(LocalDate.now());
        ledgerEntry.setCreatedBy(userId);

        // Set owner info if available
        if (property.getPropertyOwnerId() != null) {
            ledgerEntry.setOwnerId(property.getPropertyOwnerId());
            // Owner name would require a lookup - leave blank for now
        }

        // Save ledger entry
        ledgerRepository.save(ledgerEntry);

        // Update property balance
        property.setAccountBalance(newBalance);
        propertyRepository.save(property);

        log.info("Allocated £{} to property {} account from batch {}. New balance: £{}",
                amount, property.getPropertyName(), batchReference, newBalance);
    }

    // ===== BATCH SUMMARY =====

    /**
     * Get summary of a batch (checks both TransactionBatchAllocation and PaymentBatch)
     */
    public BatchSummaryDTO getBatchSummary(String batchReference) {
        List<TransactionBatchAllocation> allocations = allocationRepository.findByBatchReference(batchReference);

        // Always try to get the PaymentBatch for original payment amount
        Optional<PaymentBatch> paymentBatchOpt = paymentBatchRepository.findByBatchId(batchReference);
        PaymentBatch pb = paymentBatchOpt.orElse(null);

        // Calculate allocation totals
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

        BigDecimal allocatedTotal = totalIncome.add(totalExpenses);

        // If we have a PaymentBatch, use its payment amount as original
        if (pb != null) {
            // Payment amounts in PaymentBatch are stored as negative (payment to owner)
            // We want to show the absolute value as the original payment
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

        // No PaymentBatch found - this might be a new batch being created
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

    // ===== LIST BATCHES =====

    /**
     * Get all batch summaries for an owner (combines PaymentBatch and TransactionBatchAllocation)
     * Excludes PayProp batches as those are managed separately
     */
    public List<BatchSummaryDTO> getBatchSummariesForOwner(Long ownerId) {
        Map<String, BatchSummaryDTO> batchMap = new LinkedHashMap<>();

        // First, get existing PaymentBatch records for this owner (excluding PayProp)
        List<PaymentBatch> paymentBatches = paymentBatchRepository.findByBeneficiaryIdAndSourceNotOrderByPaymentDateDesc(
                ownerId, PaymentBatch.BatchSource.PAYPROP);
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
        // Try to get from transaction's owner field (owner_id in database)
        if (transaction.getOwner() != null) {
            return transaction.getOwner();
        }
        // Otherwise try to get from property owner assignment
        if (transaction.getProperty() != null) {
            Long propertyId = transaction.getProperty().getId();
            // Lookup OWNER from customer_property_assignments
            List<CustomerPropertyAssignment> ownerAssignments =
                customerPropertyAssignmentRepository.findByPropertyIdAndAssignmentType(propertyId, AssignmentType.OWNER);
            if (!ownerAssignments.isEmpty()) {
                CustomerPropertyAssignment ownerAssignment = ownerAssignments.get(0);
                return ownerAssignment.getCustomer();
            }
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
        private final BigDecimal originalPaymentAmount;  // Actual payment from PaymentBatch
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
}

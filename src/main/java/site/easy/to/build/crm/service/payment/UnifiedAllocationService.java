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
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.repository.CustomerRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
    private PropertyRepository propertyRepository;

    @Autowired
    private CustomerRepository customerRepository;

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

        // Check if allocation already exists for this transaction
        Optional<UnifiedAllocation> existing = allocationRepository
            .findByHistoricalTransactionIdAndAllocationType(
                transaction.getId(), AllocationType.OWNER);

        if (existing.isPresent()) {
            log.debug("Allocation already exists for transaction {}", transaction.getId());
            return existing.get();
        }

        UnifiedAllocation allocation = new UnifiedAllocation();

        // Link to transaction
        allocation.setHistoricalTransactionId(transaction.getId());

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
}

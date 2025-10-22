package site.easy.to.build.crm.service.transaction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.HistoricalTransaction;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerPropertyAssignment;
import site.easy.to.build.crm.entity.AssignmentType;
import site.easy.to.build.crm.entity.HistoricalTransaction.TransactionType;
import site.easy.to.build.crm.repository.HistoricalTransactionRepository;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;
import site.easy.to.build.crm.service.customer.CustomerService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Service for retroactively splitting historical rent payments
 * into owner allocations and management fees
 */
@Service
public class HistoricalTransactionSplitService {

    private static final Logger log = LoggerFactory.getLogger(HistoricalTransactionSplitService.class);

    @Autowired
    private HistoricalTransactionRepository historicalTransactionRepository;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerPropertyAssignmentRepository assignmentRepository;

    /**
     * Find all rent payments that need splitting
     * (rent payments without incoming_transaction_amount set)
     */
    public List<HistoricalTransaction> findUnsplitRentPayments() {
        log.info("🔍 Finding unsplit rent payments...");

        // Find rent payments that haven't been split yet
        List<HistoricalTransaction> unsplit = historicalTransactionRepository.findAll().stream()
            .filter(tx -> isRentPayment(tx))
            .filter(tx -> tx.getIncomingTransactionAmount() == null ||
                         tx.getIncomingTransactionAmount().compareTo(BigDecimal.ZERO) == 0)
            .filter(tx -> tx.getAmount() != null && tx.getAmount().compareTo(BigDecimal.ZERO) > 0)
            .toList();

        log.info("Found {} unsplit rent payments", unsplit.size());
        return unsplit;
    }

    /**
     * Check if transaction is a rent payment
     */
    private boolean isRentPayment(HistoricalTransaction tx) {
        if (tx.getTransactionType() == null) return false;

        // Check if it's a payment type transaction
        boolean isPaymentType = tx.getTransactionType() == TransactionType.payment ||
                                tx.getTransactionType() == TransactionType.invoice;

        // Check category
        boolean isRentCategory = tx.getCategory() != null &&
                                (tx.getCategory().equalsIgnoreCase("rent") ||
                                 tx.getCategory().equalsIgnoreCase("rental_payment"));

        return isPaymentType && isRentCategory;
    }

    /**
     * Retroactively split existing rent payments
     *
     * @return Map with results: processed, succeeded, failed, skipped
     */
    @Transactional
    public Map<String, Object> splitExistingRentPayments(boolean dryRun) {
        log.info("🚀 Starting retroactive rent payment split (dryRun={})", dryRun);

        Map<String, Object> results = new HashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> successes = new ArrayList<>();
        int processed = 0;
        int succeeded = 0;
        int failed = 0;
        int skipped = 0;

        List<HistoricalTransaction> unsplitPayments = findUnsplitRentPayments();
        results.put("totalFound", unsplitPayments.size());

        for (HistoricalTransaction rentPayment : unsplitPayments) {
            processed++;

            try {
                log.debug("Processing transaction #{}: {} - £{}",
                    rentPayment.getId(), rentPayment.getDescription(), rentPayment.getAmount());

                // Validate transaction
                if (rentPayment.getProperty() == null) {
                    skipped++;
                    String msg = String.format("Skipped #%d: No property linked", rentPayment.getId());
                    log.warn(msg);
                    errors.add(msg);
                    continue;
                }

                if (rentPayment.getAmount() == null || rentPayment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    skipped++;
                    String msg = String.format("Skipped #%d: Invalid amount", rentPayment.getId());
                    log.warn(msg);
                    errors.add(msg);
                    continue;
                }

                // Check if already split (safety check)
                if (isAlreadySplit(rentPayment)) {
                    skipped++;
                    String msg = String.format("Skipped #%d: Already has split transactions", rentPayment.getId());
                    log.debug(msg);
                    continue;
                }

                if (!dryRun) {
                    // Set incoming_transaction_amount to trigger splitting
                    rentPayment.setIncomingTransactionAmount(rentPayment.getAmount());
                    HistoricalTransaction saved = historicalTransactionRepository.save(rentPayment);

                    // Create the split transactions
                    createBeneficiaryAllocationsFromIncomingInternal(saved);
                }

                succeeded++;
                String msg = String.format("✅ Split #%d: %s - £%s",
                    rentPayment.getId(),
                    rentPayment.getProperty().getPropertyName(),
                    rentPayment.getAmount());
                log.info(msg);
                successes.add(msg);

            } catch (Exception e) {
                failed++;
                String msg = String.format("❌ Failed #%d: %s", rentPayment.getId(), e.getMessage());
                log.error(msg, e);
                errors.add(msg);
            }
        }

        results.put("processed", processed);
        results.put("succeeded", succeeded);
        results.put("failed", failed);
        results.put("skipped", skipped);
        results.put("errors", errors);
        results.put("successes", successes);
        results.put("dryRun", dryRun);

        log.info("📊 Retroactive split complete: {} processed, {} succeeded, {} failed, {} skipped",
            processed, succeeded, failed, skipped);

        return results;
    }

    /**
     * Check if transaction already has split child transactions
     */
    private boolean isAlreadySplit(HistoricalTransaction rentPayment) {
        String incomingTxId = rentPayment.getId().toString();

        // Check if there are already split transactions referencing this as incoming
        List<HistoricalTransaction> existingSplits = historicalTransactionRepository.findAll().stream()
            .filter(tx -> incomingTxId.equals(tx.getIncomingTransactionId()))
            .toList();

        return !existingSplits.isEmpty();
    }

    /**
     * PUBLIC method to create beneficiary allocation and agency fee from incoming rent payment
     * Called by PayPropIncomingPaymentImportService
     */
    public void createBeneficiaryAllocationsFromIncoming(HistoricalTransaction incomingTransaction) {
        createBeneficiaryAllocationsFromIncomingInternal(incomingTransaction);
    }

    /**
     * Create beneficiary allocation and agency fee from rent payment
     * (Duplicated from HistoricalTransactionImportService to avoid circular dependency)
     */
    private void createBeneficiaryAllocationsFromIncomingInternal(HistoricalTransaction incomingTransaction) {
        BigDecimal incomingAmount = incomingTransaction.getIncomingTransactionAmount();
        Property property = incomingTransaction.getProperty();

        if (property == null) {
            log.warn("Cannot create beneficiary allocation - no property linked to transaction {}",
                    incomingTransaction.getId());
            return;
        }

        // Get property owner (beneficiary)
        Customer owner = getPropertyOwner(property);
        if (owner == null) {
            log.warn("Cannot create beneficiary allocation - no owner found for property {}",
                    property.getPropertyName());
            return;
        }

        // Calculate commission (default 15% = 10% management + 5% service)
        BigDecimal commissionRate = property.getCommissionPercentage() != null ?
                                    property.getCommissionPercentage() :
                                    new BigDecimal("15.00");

        BigDecimal commissionAmount = incomingAmount
                .multiply(commissionRate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        // Calculate net due to owner
        BigDecimal netDueToOwner = incomingAmount.subtract(commissionAmount);

        // 1. Create OWNER ALLOCATION transaction (increases owner balance)
        HistoricalTransaction ownerAllocation = new HistoricalTransaction();
        ownerAllocation.setTransactionDate(incomingTransaction.getTransactionDate());
        ownerAllocation.setAmount(netDueToOwner.negate()); // Negative = allocation to owner
        ownerAllocation.setDescription("Owner Allocation - " + property.getPropertyName());
        ownerAllocation.setTransactionType(TransactionType.payment);
        ownerAllocation.setCategory("owner_allocation");
        ownerAllocation.setSource(incomingTransaction.getSource());
        ownerAllocation.setSourceReference(incomingTransaction.getSourceReference() + "-ALLOC");

        // Beneficiary tracking
        ownerAllocation.setBeneficiaryType("beneficiary");
        ownerAllocation.setBeneficiaryName(owner.getName());

        // Link to property and customer
        ownerAllocation.setProperty(property);
        ownerAllocation.setCustomer(owner);
        ownerAllocation.setOwner(owner);
        ownerAllocation.setCreatedByUser(incomingTransaction.getCreatedByUser());

        // Link to incoming transaction
        ownerAllocation.setIncomingTransactionId(incomingTransaction.getId().toString());
        ownerAllocation.setIncomingTransactionAmount(incomingAmount);
        ownerAllocation.setImportBatchId(incomingTransaction.getImportBatchId());
        ownerAllocation.setAccountSource(incomingTransaction.getAccountSource());
        ownerAllocation.setPaymentSource(incomingTransaction.getPaymentSource());

        // Commission tracking
        ownerAllocation.setCommissionRate(commissionRate);
        ownerAllocation.setCommissionAmount(commissionAmount);
        ownerAllocation.setNetToOwnerAmount(netDueToOwner);

        historicalTransactionRepository.save(ownerAllocation);
        log.debug("Created owner allocation: {} - £{}", owner.getName(), netDueToOwner);

        // 2. Create AGENCY FEE transaction (agency income)
        HistoricalTransaction agencyFee = new HistoricalTransaction();
        agencyFee.setTransactionDate(incomingTransaction.getTransactionDate());
        agencyFee.setAmount(commissionAmount.negate()); // Negative = fee collected
        agencyFee.setDescription("Management Fee - " + commissionRate + "% - " + property.getPropertyName());
        agencyFee.setTransactionType(TransactionType.fee);
        agencyFee.setCategory("management_fee");
        agencyFee.setSource(incomingTransaction.getSource());
        agencyFee.setSourceReference(incomingTransaction.getSourceReference() + "-FEE");

        // Beneficiary tracking
        agencyFee.setBeneficiaryType("agency");

        // Link to property (not to customer - this is agency income)
        agencyFee.setProperty(property);
        agencyFee.setCreatedByUser(incomingTransaction.getCreatedByUser());

        // Link to incoming transaction
        agencyFee.setIncomingTransactionId(incomingTransaction.getId().toString());
        agencyFee.setIncomingTransactionAmount(incomingAmount);
        agencyFee.setImportBatchId(incomingTransaction.getImportBatchId());
        agencyFee.setAccountSource(incomingTransaction.getAccountSource());
        agencyFee.setPaymentSource(incomingTransaction.getPaymentSource());

        // Commission tracking
        agencyFee.setCommissionRate(commissionRate);
        agencyFee.setCommissionAmount(commissionAmount);

        historicalTransactionRepository.save(agencyFee);
        log.debug("Created agency fee: £{} ({}%)", commissionAmount, commissionRate);

        log.info("✅ Split incoming £{} → Owner allocation £{} + Agency fee £{}",
                incomingAmount, netDueToOwner, commissionAmount);
    }

    /**
     * Get property owner from property entity or customer_property_assignments table
     */
    private Customer getPropertyOwner(Property property) {
        // Try old direct property_owner_id field first (legacy)
        if (property.getPropertyOwnerId() != null) {
            Customer owner = customerService.findByCustomerId(property.getPropertyOwnerId());
            if (owner != null) {
                log.debug("Found owner via property_owner_id: {}", owner.getName());
                return owner;
            }
        }

        // Check customer_property_assignments junction table (modern approach)
        List<CustomerPropertyAssignment> ownerAssignments =
            assignmentRepository.findByPropertyIdAndAssignmentType(property.getId(), AssignmentType.OWNER);

        if (!ownerAssignments.isEmpty()) {
            // Get the primary owner or the first owner if no primary
            CustomerPropertyAssignment primaryOwner = ownerAssignments.stream()
                .filter(a -> a.getIsPrimary() != null && a.getIsPrimary())
                .findFirst()
                .orElse(ownerAssignments.get(0));

            Customer owner = primaryOwner.getCustomer();
            log.debug("Found owner via customer_property_assignments: {} (ownership: {}%)",
                owner.getName(), primaryOwner.getOwnershipPercentage());
            return owner;
        }

        log.warn("Property {} has no owner assigned (checked both property_owner_id and assignments)",
            property.getPropertyName());
        return null;
    }
}

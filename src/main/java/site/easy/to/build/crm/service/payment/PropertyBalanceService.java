package site.easy.to.build.crm.service.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.PropertyBalanceLedger;
import site.easy.to.build.crm.entity.PropertyBalanceLedger.EntryType;
import site.easy.to.build.crm.entity.PropertyBalanceLedger.Source;
import site.easy.to.build.crm.entity.PropertyOwner;
import site.easy.to.build.crm.repository.PropertyBalanceLedgerRepository;
import site.easy.to.build.crm.repository.PropertyOwnerRepository;
import site.easy.to.build.crm.repository.PropertyRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PropertyBalanceService - Manages property balance ledger operations
 *
 * This service provides methods for:
 * - Depositing to property balance (when paying less than owed)
 * - Withdrawing from property balance (when paying more than owed)
 * - Transferring between properties (for block property workflows)
 * - Querying balance history and summaries
 *
 * All operations maintain an audit trail via PropertyBalanceLedger entries
 * and keep Property.accountBalance in sync.
 */
@Service
public class PropertyBalanceService {

    private static final Logger log = LoggerFactory.getLogger(PropertyBalanceService.class);

    @Autowired
    private PropertyBalanceLedgerRepository ledgerRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private PropertyOwnerRepository propertyOwnerRepository;

    // ===== BALANCE QUERIES =====

    /**
     * Get current balance for a property
     * Uses Property.accountBalance as the source of truth
     */
    public BigDecimal getCurrentBalance(Long propertyId) {
        return propertyRepository.findById(propertyId)
                .map(p -> p.getAccountBalance() != null ? p.getAccountBalance() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Get available balance (current balance minus minimum threshold)
     */
    public BigDecimal getAvailableBalance(Long propertyId) {
        return propertyRepository.findById(propertyId)
                .map(p -> {
                    BigDecimal current = p.getAccountBalance() != null ? p.getAccountBalance() : BigDecimal.ZERO;
                    BigDecimal minimum = p.getPropertyAccountMinimumBalance() != null
                            ? p.getPropertyAccountMinimumBalance() : BigDecimal.ZERO;
                    BigDecimal available = current.subtract(minimum);
                    return available.compareTo(BigDecimal.ZERO) > 0 ? available : BigDecimal.ZERO;
                })
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Get total balance across all properties for an owner
     */
    public BigDecimal getTotalBalanceForOwner(Long ownerId) {
        // Get all PropertyOwner records for this customer
        List<PropertyOwner> propertyOwners = propertyOwnerRepository.findByCustomerIdFk(ownerId.intValue());

        return propertyOwners.stream()
                .filter(po -> po.getProperty() != null)
                .map(po -> {
                    Property p = po.getProperty();
                    return p.getAccountBalance() != null ? p.getAccountBalance() : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get balance as of a specific date (from ledger history)
     */
    public BigDecimal getBalanceAsOfDate(Long propertyId, LocalDate asOfDate) {
        return ledgerRepository.findLatestEntryAsOfDate(propertyId, asOfDate)
                .map(PropertyBalanceLedger::getRunningBalance)
                .orElse(BigDecimal.ZERO);
    }

    // ===== BALANCE OPERATIONS =====

    /**
     * Deposit to property balance (paid less than owed)
     *
     * @param propertyId The property to deposit to
     * @param amount The amount to deposit (must be positive)
     * @param batchId The payment batch ID (for linking)
     * @param description Description of the deposit
     * @return The created ledger entry
     */
    @Transactional
    public PropertyBalanceLedger deposit(Long propertyId, BigDecimal amount,
                                         String batchId, String description) {
        return deposit(propertyId, amount, batchId, description, Source.PAYMENT_BATCH, null);
    }

    /**
     * Deposit with full parameters
     */
    @Transactional
    public PropertyBalanceLedger deposit(Long propertyId, BigDecimal amount,
                                         String batchId, String description,
                                         Source source, Long createdBy) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }

        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found: " + propertyId));

        BigDecimal currentBalance = property.getAccountBalance() != null
                ? property.getAccountBalance() : BigDecimal.ZERO;
        BigDecimal newBalance = currentBalance.add(amount);

        // Create ledger entry
        PropertyBalanceLedger entry = createLedgerEntry(
                property, EntryType.DEPOSIT, amount, newBalance,
                description, batchId, source, createdBy);

        // Update property balance
        property.setAccountBalance(newBalance);
        propertyRepository.save(property);

        log.info("Deposited {} to property {} balance. New balance: {}",
                amount, propertyId, newBalance);

        return ledgerRepository.save(entry);
    }

    /**
     * Withdraw from property balance (paid more than owed, or expense paid)
     *
     * @param propertyId The property to withdraw from
     * @param amount The amount to withdraw (must be positive)
     * @param batchId The payment batch ID (for linking)
     * @param description Description of the withdrawal
     * @return The created ledger entry
     */
    @Transactional
    public PropertyBalanceLedger withdraw(Long propertyId, BigDecimal amount,
                                          String batchId, String description) {
        return withdraw(propertyId, amount, batchId, description, Source.PAYMENT_BATCH, null);
    }

    /**
     * Withdraw with full parameters
     */
    @Transactional
    public PropertyBalanceLedger withdraw(Long propertyId, BigDecimal amount,
                                          String batchId, String description,
                                          Source source, Long createdBy) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }

        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found: " + propertyId));

        BigDecimal currentBalance = property.getAccountBalance() != null
                ? property.getAccountBalance() : BigDecimal.ZERO;

        // Check if withdrawal is allowed
        if (!canWithdraw(propertyId, amount)) {
            throw new IllegalStateException(
                    String.format("Cannot withdraw %s from property %s. Available balance: %s",
                            amount, propertyId, getAvailableBalance(propertyId)));
        }

        BigDecimal newBalance = currentBalance.subtract(amount);

        // Create ledger entry
        PropertyBalanceLedger entry = createLedgerEntry(
                property, EntryType.WITHDRAWAL, amount, newBalance,
                description, batchId, source, createdBy);

        // Update property balance
        property.setAccountBalance(newBalance);
        propertyRepository.save(property);

        log.info("Withdrew {} from property {} balance. New balance: {}",
                amount, propertyId, newBalance);

        return ledgerRepository.save(entry);
    }

    /**
     * Transfer between properties (for block property workflow)
     *
     * @param fromPropertyId Source property
     * @param toPropertyId Destination property (usually block property)
     * @param amount Amount to transfer
     * @param description Description of the transfer
     */
    @Transactional
    public void transfer(Long fromPropertyId, Long toPropertyId,
                         BigDecimal amount, String description) {
        transfer(fromPropertyId, toPropertyId, amount, description, null);
    }

    /**
     * Transfer with creator tracking
     */
    @Transactional
    public void transfer(Long fromPropertyId, Long toPropertyId,
                         BigDecimal amount, String description, Long createdBy) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }

        Property fromProperty = propertyRepository.findById(fromPropertyId)
                .orElseThrow(() -> new IllegalArgumentException("Source property not found: " + fromPropertyId));

        Property toProperty = propertyRepository.findById(toPropertyId)
                .orElseThrow(() -> new IllegalArgumentException("Destination property not found: " + toPropertyId));

        // Calculate balances
        BigDecimal fromCurrentBalance = fromProperty.getAccountBalance() != null
                ? fromProperty.getAccountBalance() : BigDecimal.ZERO;
        BigDecimal toCurrentBalance = toProperty.getAccountBalance() != null
                ? toProperty.getAccountBalance() : BigDecimal.ZERO;

        BigDecimal fromNewBalance = fromCurrentBalance.subtract(amount);
        BigDecimal toNewBalance = toCurrentBalance.add(amount);

        // Create TRANSFER_OUT entry for source property
        PropertyBalanceLedger outEntry = new PropertyBalanceLedger(
                fromPropertyId, EntryType.TRANSFER_OUT, amount, fromNewBalance);
        outEntry.setPropertyName(fromProperty.getPropertyName());
        outEntry.setOwnerId(getOwnerId(fromPropertyId));
        outEntry.setOwnerName(getOwnerName(fromPropertyId));
        outEntry.setDescription(description);
        outEntry.setRelatedPropertyId(toPropertyId);
        outEntry.setRelatedPropertyName(toProperty.getPropertyName());
        outEntry.setSource(Source.BLOCK_TRANSFER);
        outEntry.setCreatedBy(createdBy);
        outEntry.setEntryDate(LocalDate.now());

        // Create TRANSFER_IN entry for destination property
        PropertyBalanceLedger inEntry = new PropertyBalanceLedger(
                toPropertyId, EntryType.TRANSFER_IN, amount, toNewBalance);
        inEntry.setPropertyName(toProperty.getPropertyName());
        inEntry.setOwnerId(getOwnerId(toPropertyId));
        inEntry.setOwnerName(getOwnerName(toPropertyId));
        inEntry.setDescription(description);
        inEntry.setRelatedPropertyId(fromPropertyId);
        inEntry.setRelatedPropertyName(fromProperty.getPropertyName());
        inEntry.setSource(Source.BLOCK_TRANSFER);
        inEntry.setCreatedBy(createdBy);
        inEntry.setEntryDate(LocalDate.now());

        // Update property balances
        fromProperty.setAccountBalance(fromNewBalance);
        toProperty.setAccountBalance(toNewBalance);

        propertyRepository.save(fromProperty);
        propertyRepository.save(toProperty);
        ledgerRepository.save(outEntry);
        ledgerRepository.save(inEntry);

        log.info("Transferred {} from property {} to property {}",
                amount, fromPropertyId, toPropertyId);
    }

    /**
     * Manual adjustment (correction)
     *
     * @param propertyId The property to adjust
     * @param amount The adjustment amount (positive to increase, negative to decrease)
     * @param description Description of the adjustment
     * @param notes Additional notes
     * @return The created ledger entry
     */
    @Transactional
    public PropertyBalanceLedger adjust(Long propertyId, BigDecimal amount,
                                        String description, String notes, Long createdBy) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Adjustment amount cannot be zero");
        }

        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found: " + propertyId));

        BigDecimal currentBalance = property.getAccountBalance() != null
                ? property.getAccountBalance() : BigDecimal.ZERO;
        BigDecimal newBalance = currentBalance.add(amount);

        // Create ledger entry
        PropertyBalanceLedger entry = createLedgerEntry(
                property, EntryType.ADJUSTMENT, amount.abs(), newBalance,
                description, null, Source.MANUAL, createdBy);
        entry.setNotes(notes);

        // Update property balance
        property.setAccountBalance(newBalance);
        propertyRepository.save(property);

        log.info("Adjusted property {} balance by {}. New balance: {}",
                propertyId, amount, newBalance);

        return ledgerRepository.save(entry);
    }

    /**
     * Set opening balance (for initial setup or reconciliation)
     *
     * @param propertyId The property
     * @param amount The opening balance amount
     * @param asOfDate The date of the opening balance
     * @param notes Notes explaining the opening balance
     * @return The created ledger entry
     */
    @Transactional
    public PropertyBalanceLedger setOpeningBalance(Long propertyId, BigDecimal amount,
                                                   LocalDate asOfDate, String notes, Long createdBy) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found: " + propertyId));

        // Check if property already has ledger entries
        if (ledgerRepository.hasAnyEntries(propertyId)) {
            throw new IllegalStateException(
                    "Property " + propertyId + " already has ledger entries. Use adjustment instead.");
        }

        BigDecimal balanceAmount = amount != null ? amount : BigDecimal.ZERO;

        // Create opening balance entry
        PropertyBalanceLedger entry = new PropertyBalanceLedger(
                propertyId, EntryType.OPENING_BALANCE, balanceAmount, balanceAmount);
        entry.setPropertyName(property.getPropertyName());
        entry.setOwnerId(getOwnerId(propertyId));
        entry.setOwnerName(getOwnerName(propertyId));
        entry.setDescription("Opening balance");
        entry.setNotes(notes);
        entry.setSource(Source.MANUAL);
        entry.setCreatedBy(createdBy);
        entry.setEntryDate(asOfDate != null ? asOfDate : LocalDate.now());

        // Update property balance
        property.setAccountBalance(balanceAmount);
        propertyRepository.save(property);

        log.info("Set opening balance {} for property {} as of {}",
                balanceAmount, propertyId, entry.getEntryDate());

        return ledgerRepository.save(entry);
    }

    // ===== HISTORY & REPORTING =====

    /**
     * Get ledger history for a property
     */
    public List<PropertyBalanceLedger> getLedgerHistory(Long propertyId) {
        return ledgerRepository.findByPropertyIdOrderByEntryDateDescCreatedAtDesc(propertyId);
    }

    /**
     * Get ledger history for a property within a date range
     */
    public List<PropertyBalanceLedger> getLedgerHistory(Long propertyId,
                                                         LocalDate fromDate, LocalDate toDate) {
        return ledgerRepository.findByPropertyIdAndEntryDateBetweenOrderByEntryDateAscCreatedAtAsc(
                propertyId, fromDate, toDate);
    }

    /**
     * Get ledger history for an owner across all properties
     */
    public List<PropertyBalanceLedger> getLedgerHistoryForOwner(Long ownerId) {
        return ledgerRepository.findByOwnerIdOrderByEntryDateDescCreatedAtDesc(ownerId);
    }

    /**
     * Get ledger history for an owner within a date range
     */
    public List<PropertyBalanceLedger> getLedgerHistoryForOwner(Long ownerId,
                                                                 LocalDate fromDate, LocalDate toDate) {
        return ledgerRepository.findByOwnerIdAndEntryDateBetweenOrderByEntryDateAscCreatedAtAsc(
                ownerId, fromDate, toDate);
    }

    /**
     * Get entries linked to a payment batch
     */
    public List<PropertyBalanceLedger> getEntriesForBatch(String batchId) {
        return ledgerRepository.findByPaymentBatchId(batchId);
    }

    /**
     * Get unit contribution summary for a block property
     */
    public Map<Long, BigDecimal> getUnitContributionSummary(Long blockPropertyId,
                                                             LocalDate fromDate, LocalDate toDate) {
        List<Object[]> results = ledgerRepository.getUnitContributionSummary(
                blockPropertyId, fromDate, toDate);

        Map<Long, BigDecimal> summary = new HashMap<>();
        for (Object[] row : results) {
            Long unitPropertyId = (Long) row[0];
            BigDecimal totalContribution = (BigDecimal) row[2];
            if (unitPropertyId != null) {
                summary.put(unitPropertyId, totalContribution);
            }
        }
        return summary;
    }

    // ===== VALIDATION =====

    /**
     * Check if withdrawal is allowed (respects minimum balance threshold)
     */
    public boolean canWithdraw(Long propertyId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal available = getAvailableBalance(propertyId);
        return available.compareTo(amount) >= 0;
    }

    /**
     * Recalculate Property.accountBalance from ledger
     * Used for data fixes and reconciliation
     */
    @Transactional
    public BigDecimal recalculateBalance(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found: " + propertyId));

        // Get latest ledger entry
        Optional<PropertyBalanceLedger> latestEntry =
                ledgerRepository.findFirstByPropertyIdOrderByEntryDateDescCreatedAtDesc(propertyId);

        BigDecimal calculatedBalance = latestEntry
                .map(PropertyBalanceLedger::getRunningBalance)
                .orElse(BigDecimal.ZERO);

        // Update property if different
        BigDecimal currentBalance = property.getAccountBalance() != null
                ? property.getAccountBalance() : BigDecimal.ZERO;

        if (currentBalance.compareTo(calculatedBalance) != 0) {
            log.warn("Property {} balance mismatch. Current: {}, Calculated: {}. Updating.",
                    propertyId, currentBalance, calculatedBalance);
            property.setAccountBalance(calculatedBalance);
            propertyRepository.save(property);
        }

        return calculatedBalance;
    }

    // ===== HELPER METHODS =====

    private PropertyBalanceLedger createLedgerEntry(Property property, EntryType entryType,
                                                    BigDecimal amount, BigDecimal runningBalance,
                                                    String description, String batchId,
                                                    Source source, Long createdBy) {
        PropertyBalanceLedger entry = new PropertyBalanceLedger(
                property.getId(), entryType, amount, runningBalance);

        entry.setPropertyName(property.getPropertyName());

        // Get owner info from PropertyOwner relationship
        Long ownerId = getOwnerId(property.getId());
        String ownerName = getOwnerName(property.getId());
        entry.setOwnerId(ownerId);
        entry.setOwnerName(ownerName);

        entry.setDescription(description);
        entry.setPaymentBatchId(batchId);
        entry.setSource(source);
        entry.setCreatedBy(createdBy);
        entry.setEntryDate(LocalDate.now());

        return entry;
    }

    private Long getOwnerId(Long propertyId) {
        // Get primary owner from PropertyOwner relationship
        List<PropertyOwner> owners = propertyOwnerRepository.findByPropertyId(propertyId);
        if (owners != null && !owners.isEmpty()) {
            return owners.stream()
                    .filter(po -> "Y".equals(po.getIsPrimaryOwner()))
                    .findFirst()
                    .or(() -> owners.stream().findFirst())
                    .map(po -> po.getCustomer() != null ? po.getCustomer().getCustomerId().longValue() : null)
                    .orElse(null);
        }
        return null;
    }

    private String getOwnerName(Long propertyId) {
        List<PropertyOwner> owners = propertyOwnerRepository.findByPropertyId(propertyId);
        if (owners != null && !owners.isEmpty()) {
            return owners.stream()
                    .filter(po -> "Y".equals(po.getIsPrimaryOwner()))
                    .findFirst()
                    .or(() -> owners.stream().findFirst())
                    .map(po -> po.getCustomer() != null ? po.getCustomer().getName() : null)
                    .orElse(null);
        }
        return null;
    }
}

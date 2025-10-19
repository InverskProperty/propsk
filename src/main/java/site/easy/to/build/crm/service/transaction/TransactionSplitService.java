package site.easy.to.build.crm.service.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import site.easy.to.build.crm.entity.HistoricalTransaction;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.entity.BeneficiaryBalance;
import site.easy.to.build.crm.entity.HistoricalTransaction.TransactionType;
import site.easy.to.build.crm.repository.HistoricalTransactionRepository;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.repository.BeneficiaryBalanceRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service to automatically split incoming rent payments into:
 * 1. Agency commission payment
 * 2. Owner payment
 * 3. (Optional) Expense payments
 *
 * Mirrors PayProp's split transaction model
 */
@Service
@Transactional
public class TransactionSplitService {

    private static final Logger log = LoggerFactory.getLogger(TransactionSplitService.class);

    @Autowired
    private HistoricalTransactionRepository historicalTransactionRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private BeneficiaryBalanceRepository beneficiaryBalanceRepository;

    /**
     * Split an incoming rent payment into commission and owner payments
     *
     * @param incomingTransaction The original rent payment transaction
     * @param property The property the rent is for
     * @param lease The lease/invoice this payment relates to
     * @param tenant The tenant who paid
     * @param owner The property owner who receives payment
     * @param currentUser The user creating the transactions
     * @return List of created transactions (incoming + commission + owner)
     */
    public List<HistoricalTransaction> splitRentPayment(
            HistoricalTransaction incomingTransaction,
            Property property,
            Invoice lease,
            Customer tenant,
            Customer owner,
            User currentUser) {

        List<HistoricalTransaction> transactions = new ArrayList<>();

        // Get commission rate from property
        BigDecimal commissionRate = property.getCommissionPercentage();
        if (commissionRate == null) {
            commissionRate = BigDecimal.ZERO;
            log.warn("Property {} has no commission rate set, using 0%", property.getPropertyName());
        }

        BigDecimal rentAmount = incomingTransaction.getAmount();
        String incomingTxnId = "RENT-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Save the incoming rent transaction
        incomingTransaction.setIncomingTransactionId(incomingTxnId);
        incomingTransaction.setIncomingTransactionAmount(rentAmount);
        incomingTransaction.setBeneficiaryType("incoming");
        incomingTransaction.setProperty(property);
        incomingTransaction.setCustomer(tenant);
        incomingTransaction.setInvoice(lease);
        incomingTransaction.setCreatedByUser(currentUser);

        if (lease != null) {
            incomingTransaction.setLeaseStartDate(lease.getStartDate());
            incomingTransaction.setLeaseEndDate(lease.getEndDate());
            incomingTransaction.setRentAmountAtTransaction(lease.getAmount());
        }

        HistoricalTransaction savedIncoming = historicalTransactionRepository.save(incomingTransaction);
        transactions.add(savedIncoming);

        log.info("✅ Created incoming transaction: £{} for {}", rentAmount, property.getPropertyName());

        // 2. Create commission payment (if commission rate > 0)
        if (commissionRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal commissionAmount = rentAmount
                    .multiply(commissionRate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            HistoricalTransaction commissionTxn = new HistoricalTransaction();
            commissionTxn.setTransactionDate(incomingTransaction.getTransactionDate());
            commissionTxn.setAmount(commissionAmount.negate()); // Negative = expense
            commissionTxn.setDescription("Commission - " + property.getPropertyName() + " (" + commissionRate + "%)");
            commissionTxn.setTransactionType(TransactionType.fee);
            commissionTxn.setCategory("commission");
            commissionTxn.setSource(incomingTransaction.getSource());
            commissionTxn.setImportBatchId(incomingTransaction.getImportBatchId());

            // Beneficiary tracking
            commissionTxn.setBeneficiaryType("agency");
            commissionTxn.setBeneficiaryName("Agency Commission");

            // Link to incoming transaction
            commissionTxn.setIncomingTransactionId(incomingTxnId);
            commissionTxn.setIncomingTransactionAmount(rentAmount);

            // Commission tracking
            commissionTxn.setCommissionRate(commissionRate);
            commissionTxn.setCommissionAmount(commissionAmount);

            // Property/lease linking
            commissionTxn.setProperty(property);
            commissionTxn.setInvoice(lease);
            commissionTxn.setCreatedByUser(currentUser);

            if (lease != null) {
                commissionTxn.setLeaseStartDate(lease.getStartDate());
                commissionTxn.setLeaseEndDate(lease.getEndDate());
                commissionTxn.setRentAmountAtTransaction(lease.getAmount());
            }

            HistoricalTransaction savedCommission = historicalTransactionRepository.save(commissionTxn);
            transactions.add(savedCommission);

            log.info("✅ Created commission payment: -£{} ({}%)", commissionAmount, commissionRate);
        }

        // 3. Create owner payment
        BigDecimal ownerAmount = rentAmount;
        if (commissionRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal commissionAmount = rentAmount
                    .multiply(commissionRate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            ownerAmount = rentAmount.subtract(commissionAmount);
        }

        HistoricalTransaction ownerTxn = new HistoricalTransaction();
        ownerTxn.setTransactionDate(incomingTransaction.getTransactionDate());
        ownerTxn.setAmount(ownerAmount.negate()); // Negative = payment out
        ownerTxn.setDescription("Owner Payment - " + property.getPropertyName());
        ownerTxn.setTransactionType(TransactionType.payment);
        ownerTxn.setCategory("owner_payment");
        ownerTxn.setSource(incomingTransaction.getSource());
        ownerTxn.setImportBatchId(incomingTransaction.getImportBatchId());

        // Beneficiary tracking
        ownerTxn.setBeneficiaryType("beneficiary");
        if (owner != null) {
            ownerTxn.setBeneficiaryName(owner.getName());
            ownerTxn.setCustomer(owner);
        }

        // Link to incoming transaction
        ownerTxn.setIncomingTransactionId(incomingTxnId);
        ownerTxn.setIncomingTransactionAmount(rentAmount);
        ownerTxn.setNetToOwnerAmount(ownerAmount);

        // Property/lease linking
        ownerTxn.setProperty(property);
        ownerTxn.setInvoice(lease);
        ownerTxn.setCreatedByUser(currentUser);

        if (lease != null) {
            ownerTxn.setLeaseStartDate(lease.getStartDate());
            ownerTxn.setLeaseEndDate(lease.getEndDate());
            ownerTxn.setRentAmountAtTransaction(lease.getAmount());
        }

        HistoricalTransaction savedOwner = historicalTransactionRepository.save(ownerTxn);
        transactions.add(savedOwner);

        log.info("✅ Created owner payment: -£{} to {}", ownerAmount, owner != null ? owner.getName() : "Owner");

        // Update beneficiary balance
        updateBeneficiaryBalance(savedOwner, property, owner);

        return transactions;
    }

    /**
     * Create a simple expense payment transaction
     * This reduces the net amount due to owner
     *
     * @param expenseAmount The expense amount (positive number)
     * @param description Expense description
     * @param property The property to charge the expense to
     * @param lease Optional lease reference
     * @param beneficiaryName Who receives the payment (e.g., "ABC Plumbing")
     * @param currentUser The user creating the transaction
     * @return The created expense transaction
     */
    public HistoricalTransaction createExpense(
            BigDecimal expenseAmount,
            String description,
            Property property,
            Invoice lease,
            String beneficiaryName,
            String beneficiaryType,
            User currentUser) {

        HistoricalTransaction expense = new HistoricalTransaction();
        expense.setTransactionDate(java.time.LocalDate.now());
        expense.setAmount(expenseAmount.negate()); // Negative = expense
        expense.setDescription(description);
        expense.setTransactionType(TransactionType.expense);
        expense.setCategory("maintenance");
        expense.setSource(HistoricalTransaction.TransactionSource.manual_entry);

        // Beneficiary tracking
        expense.setBeneficiaryType(beneficiaryType != null ? beneficiaryType : "contractor");
        expense.setBeneficiaryName(beneficiaryName);

        // Property/lease linking
        expense.setProperty(property);
        expense.setInvoice(lease);
        expense.setCreatedByUser(currentUser);

        if (lease != null) {
            expense.setLeaseStartDate(lease.getStartDate());
            expense.setLeaseEndDate(lease.getEndDate());
            expense.setRentAmountAtTransaction(lease.getAmount());
        }

        HistoricalTransaction savedExpense = historicalTransactionRepository.save(expense);

        log.info("✅ Created expense: -£{} for {} to {}", expenseAmount, property.getPropertyName(), beneficiaryName);

        // Update beneficiary balance (expenses reduce what we owe the owner)
        // Get the owner for this property
        if (property.getPropertyOwnerId() != null) {
            Customer owner = customerRepository.findById(property.getPropertyOwnerId()).orElse(null);
            if (owner != null) {
                updateBeneficiaryBalance(savedExpense, property, owner);
            }
        }

        return savedExpense;
    }

    /**
     * Calculate net amount due to owner for a property/period
     * Net = Owner Payments - Expenses
     *
     * @param property The property
     * @param startDate Period start date
     * @param endDate Period end date
     * @return Net amount due to owner
     */
    public BigDecimal calculateNetDueToOwner(Property property,
                                            java.time.LocalDate startDate,
                                            java.time.LocalDate endDate) {

        List<HistoricalTransaction> transactions = historicalTransactionRepository
                .findByPropertyAndTransactionDateBetween(property.getId(), startDate, endDate);

        BigDecimal ownerPayments = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;

        for (HistoricalTransaction txn : transactions) {
            if ("beneficiary".equals(txn.getBeneficiaryType())) {
                // Owner payment (already negative, so negate to get positive)
                ownerPayments = ownerPayments.add(txn.getAmount().negate());
            } else if ("contractor".equals(txn.getBeneficiaryType())
                    || TransactionType.expense.equals(txn.getTransactionType())) {
                // Expense (already negative, so negate to get positive)
                expenses = expenses.add(txn.getAmount().negate());
            }
        }

        return ownerPayments.subtract(expenses);
    }

    // ===== BENEFICIARY BALANCE TRACKING =====

    /**
     * Update beneficiary balance for a transaction
     * Called automatically when transactions are created
     *
     * @param transaction The transaction that affects the balance
     * @param property The property
     * @param owner The owner (beneficiary)
     */
    private void updateBeneficiaryBalance(HistoricalTransaction transaction,
                                          Property property,
                                          Customer owner) {
        if (owner == null || property == null) {
            return;
        }

        // Get or create current balance for this owner/property
        LocalDate today = java.time.LocalDate.now();
        BeneficiaryBalance balance = beneficiaryBalanceRepository
                .findByCustomerAndPropertyAndBalanceDate(owner, property, today)
                .orElseGet(() -> {
                    BeneficiaryBalance newBalance = new BeneficiaryBalance(owner, property, today);
                    newBalance.setPeriodStart(today.withDayOfMonth(1)); // First day of month
                    newBalance.setPeriodEnd(today.withDayOfMonth(today.lengthOfMonth())); // Last day of month
                    return newBalance;
                });

        // Update balance based on transaction type
        BigDecimal amount = transaction.getAmount();
        String beneficiaryType = transaction.getBeneficiaryType();

        if ("beneficiary".equals(beneficiaryType)) {
            // Owner allocation - INCREASES balance
            // Transaction is negative (-£255), so negate to get positive (£255)
            BigDecimal ownerAllocation = amount.negate();
            balance.addRentAllocation(ownerAllocation);
            log.debug("Added rent allocation of £{} to owner balance", ownerAllocation);

        } else if ("contractor".equals(beneficiaryType)) {
            // Expense - DECREASES balance
            // Transaction is negative (-£100), so negate to get positive (£100)
            BigDecimal expenseAmount = amount.negate();
            balance.deductExpense(expenseAmount);
            log.debug("Deducted expense of £{} from owner balance", expenseAmount);

        } else if ("beneficiary_payment".equals(beneficiaryType)) {
            // Payment to owner - DECREASES balance
            // Transaction is negative (-£500), so negate to get positive (£500)
            BigDecimal paymentAmount = amount.negate();
            balance.deductPayment(paymentAmount);
            log.debug("Deducted payment of £{} from owner balance", paymentAmount);
        }

        // Track last transaction
        balance.setLastTransactionId(transaction.getId());
        balance.setPaypropBeneficiaryId(transaction.getPaypropBeneficiaryId());

        // Save balance
        beneficiaryBalanceRepository.save(balance);

        log.info("📊 Updated balance for {} at {}: £{} (was £{})",
                owner.getName(), property.getPropertyName(),
                balance.getBalanceAmount(), balance.getOpeningBalance());

        // Warn if overdrawn
        if (balance.isOverdrawn()) {
            log.warn("⚠️  Owner balance is NEGATIVE (£{}): {} owes agency money",
                    balance.getBalanceAmount(), owner.getName());
        }
    }

    /**
     * Get or create monthly balance for owner/property
     *
     * @param owner The owner
     * @param property The property
     * @param date The date to get balance for
     * @return The balance record
     */
    public BeneficiaryBalance getOrCreateMonthlyBalance(Customer owner, Property property, LocalDate date) {
        return beneficiaryBalanceRepository
                .findByCustomerAndPropertyAndBalanceDate(owner, property, date)
                .orElseGet(() -> {
                    // Get previous month's closing balance as opening balance
                    LocalDate previousMonth = date.minusMonths(1);
                    BigDecimal openingBalance = beneficiaryBalanceRepository
                            .findByCustomerAndPropertyAndBalanceDate(owner, property, previousMonth)
                            .map(BeneficiaryBalance::getBalanceAmount)
                            .orElse(BigDecimal.ZERO);

                    BeneficiaryBalance newBalance = new BeneficiaryBalance(owner, property, date);
                    newBalance.setOpeningBalance(openingBalance);
                    newBalance.setBalanceAmount(openingBalance);
                    newBalance.setPeriodStart(date.withDayOfMonth(1));
                    newBalance.setPeriodEnd(date.withDayOfMonth(date.lengthOfMonth()));
                    return beneficiaryBalanceRepository.save(newBalance);
                });
    }

    /**
     * Get current balance for owner at property
     *
     * @param owner The owner
     * @param property The property
     * @return Current balance (what agency owes owner)
     */
    public BigDecimal getCurrentBalance(Customer owner, Property property) {
        return beneficiaryBalanceRepository
                .findCurrentBalance(owner, property)
                .map(BeneficiaryBalance::getBalanceAmount)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Check if owner balance is overdrawn (negative)
     *
     * @param owner The owner
     * @param property The property
     * @return true if balance is negative
     */
    public boolean isOwnerOverdrawn(Customer owner, Property property) {
        return getCurrentBalance(owner, property).compareTo(BigDecimal.ZERO) < 0;
    }

    // ===== PAYMENT RECORDING =====

    /**
     * Record a payment to an owner (landlord)
     * This DECREASES the beneficiary balance (we owe them less)
     *
     * @param owner The owner receiving the payment
     * @param property The property this payment relates to
     * @param paymentAmount The amount paid (positive number)
     * @param paymentDate The date of payment
     * @param paymentMethod Payment method (e.g., "Bank Transfer", "Check")
     * @param paymentReference Payment reference/check number
     * @param notes Optional notes about the payment
     * @param currentUser The user recording the payment
     * @return The created payment transaction
     */
    public HistoricalTransaction recordOwnerPayment(
            Customer owner,
            Property property,
            BigDecimal paymentAmount,
            LocalDate paymentDate,
            String paymentMethod,
            String paymentReference,
            String notes,
            User currentUser) {

        if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }

        // Check current balance
        BigDecimal currentBalance = getCurrentBalance(owner, property);

        log.info("💷 Recording payment to {}: £{} (Current balance: £{})",
                owner.getName(), paymentAmount, currentBalance);

        // Create payment transaction
        HistoricalTransaction payment = new HistoricalTransaction();
        payment.setTransactionDate(paymentDate);
        payment.setAmount(paymentAmount.negate()); // Negative = money paid out
        payment.setDescription("Payment to " + owner.getName() + " - " + property.getPropertyName());
        payment.setTransactionType(TransactionType.payment);
        payment.setCategory("owner_payment");
        payment.setSource(HistoricalTransaction.TransactionSource.manual_entry);

        // Payment details
        payment.setPaymentMethod(paymentMethod);
        payment.setBankReference(paymentReference);
        payment.setCounterpartyName(owner.getName());

        // Beneficiary tracking
        payment.setBeneficiaryType("beneficiary_payment");
        payment.setBeneficiaryName(owner.getName());

        // Property/customer linking
        payment.setProperty(property);
        payment.setCustomer(owner);
        payment.setCreatedByUser(currentUser);

        // Notes
        payment.setNotes(notes);

        // Save payment transaction
        HistoricalTransaction savedPayment = historicalTransactionRepository.save(payment);

        log.info("✅ Created payment transaction: -£{} to {}", paymentAmount, owner.getName());

        // Update beneficiary balance
        updateBeneficiaryBalance(savedPayment, property, owner);

        // Check if balance went negative
        BigDecimal newBalance = getCurrentBalance(owner, property);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("⚠️  Payment of £{} resulted in NEGATIVE balance (£{}) for {}",
                    paymentAmount, newBalance, owner.getName());
            log.warn("    Previous balance was £{}, payment was £{}",
                    currentBalance, paymentAmount);
        } else {
            log.info("📊 New balance for {} at {}: £{}",
                    owner.getName(), property.getPropertyName(), newBalance);
        }

        return savedPayment;
    }

    /**
     * Record a batch payment to multiple owners
     *
     * @param payments List of payment details
     * @param currentUser The user recording the payments
     * @return List of created payment transactions
     */
    public List<HistoricalTransaction> recordBatchOwnerPayments(
            List<OwnerPaymentRequest> payments,
            User currentUser) {

        List<HistoricalTransaction> transactions = new ArrayList<>();

        for (OwnerPaymentRequest request : payments) {
            try {
                HistoricalTransaction payment = recordOwnerPayment(
                        request.owner,
                        request.property,
                        request.paymentAmount,
                        request.paymentDate,
                        request.paymentMethod,
                        request.paymentReference,
                        request.notes,
                        currentUser
                );
                transactions.add(payment);
            } catch (Exception e) {
                log.error("Failed to record payment to {}: {}",
                        request.owner.getName(), e.getMessage());
                // Continue with other payments
            }
        }

        log.info("💷 Batch payment complete: {} of {} payments recorded",
                transactions.size(), payments.size());

        return transactions;
    }

    /**
     * Get recommended payment amount for owner
     * (Current balance, but not more than what they're owed)
     *
     * @param owner The owner
     * @param property The property
     * @return Recommended payment amount (0 if balance is negative)
     */
    public BigDecimal getRecommendedPaymentAmount(Customer owner, Property property) {
        BigDecimal currentBalance = getCurrentBalance(owner, property);

        // Only recommend payment if balance is positive
        if (currentBalance.compareTo(BigDecimal.ZERO) > 0) {
            return currentBalance;
        }

        return BigDecimal.ZERO;
    }

    // ===== PAYMENT REQUEST DTO =====

    /**
     * Data class for owner payment requests
     */
    public static class OwnerPaymentRequest {
        public Customer owner;
        public Property property;
        public BigDecimal paymentAmount;
        public LocalDate paymentDate;
        public String paymentMethod;
        public String paymentReference;
        public String notes;

        public OwnerPaymentRequest(Customer owner, Property property, BigDecimal paymentAmount,
                                   LocalDate paymentDate, String paymentMethod,
                                   String paymentReference, String notes) {
            this.owner = owner;
            this.property = property;
            this.paymentAmount = paymentAmount;
            this.paymentDate = paymentDate;
            this.paymentMethod = paymentMethod;
            this.paymentReference = paymentReference;
            this.notes = notes;
        }
    }
}

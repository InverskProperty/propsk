package site.easy.to.build.crm.service.payprop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.HistoricalTransaction;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.entity.HistoricalTransaction.TransactionSource;
import site.easy.to.build.crm.entity.HistoricalTransaction.TransactionType;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.repository.HistoricalTransactionRepository;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.service.transaction.TransactionSplitService;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to import PayProp payment batches into historical transactions
 *
 * Imports TWO types of transactions:
 * 1. Owner allocations (net due to owner per property) - INCREASES balance
 * 2. Batch payments (actual money transferred to bank) - DECREASES balance
 *
 * This mirrors how PayProp works:
 * - Multiple property payments are grouped into a batch
 * - Each property allocation is tracked individually (what they earned)
 * - One lump sum payment is made to the owner's bank (what they received)
 */
@Service
@Transactional
public class PayPropPaymentImportService {

    private static final Logger log = LoggerFactory.getLogger(PayPropPaymentImportService.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private HistoricalTransactionRepository historicalTransactionRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private TransactionSplitService transactionSplitService;

    @Autowired
    private PayPropInvoiceLinkingService payPropInvoiceLinkingService;

    /**
     * Import all PayProp batch payments
     * Creates both owner allocations and actual payment transactions
     */
    public PaymentImportResult importAllBatchPayments(User currentUser) {
        log.info("🔄 Starting PayProp batch payment import");

        PaymentImportResult result = new PaymentImportResult();
        result.startTime = LocalDateTime.now();

        try {
            // Get all payment batches with status = 'paid'
            List<PaymentBatchData> batches = fetchPaidBatches();
            log.info("Found {} paid batches to import", batches.size());

            result.totalBatches = batches.size();

            for (PaymentBatchData batch : batches) {
                try {
                    importBatch(batch, currentUser);
                    result.successfulBatches++;
                    result.allocationsCreated += batch.allocations.size();
                    result.paymentsCreated++; // One batch payment per batch
                } catch (Exception e) {
                    log.error("Failed to import batch {}: {}", batch.batchId, e.getMessage(), e);
                    result.failedBatches++;
                    result.errors.add("Batch " + batch.batchId + ": " + e.getMessage());
                }
            }

            result.success = true;
            result.endTime = LocalDateTime.now();

            log.info("✅ PayProp payment import complete: {} batches, {} allocations, {} payments",
                    result.successfulBatches, result.allocationsCreated, result.paymentsCreated);

        } catch (Exception e) {
            log.error("❌ PayProp payment import failed", e);
            result.success = false;
            result.errorMessage = e.getMessage();
            result.endTime = LocalDateTime.now();
        }

        return result;
    }

    /**
     * Import a single payment batch
     */
    private void importBatch(PaymentBatchData batch, User currentUser) {
        log.info("📦 Importing batch {}: {} ({} allocations, £{})",
                batch.batchId, batch.ownerName, batch.allocations.size(), batch.totalAmount);

        // Get owner customer record
        Customer owner = customerRepository.findByPayPropEntityId(batch.ownerPayPropId);
        if (owner == null) {
            throw new RuntimeException("Owner not found: " + batch.ownerPayPropId);
        }

        // 1. Import individual owner allocations (what they earned per property)
        for (AllocationData allocation : batch.allocations) {
            importOwnerAllocation(allocation, owner, batch, currentUser);
        }

        // 2. Import the actual batch payment (what hit their bank)
        importBatchPayment(batch, owner, currentUser);

        log.info("✅ Imported batch {}: {} allocations + 1 payment", batch.batchId, batch.allocations.size());
    }

    /**
     * Import individual owner allocation (net due to owner for one property)
     * This INCREASES the beneficiary balance
     */
    private void importOwnerAllocation(AllocationData allocation, Customer owner,
                                       PaymentBatchData batch, User currentUser) {

        // Check if already imported
        String txnRef = "PAYPROP-ALLOC-" + allocation.paypropId;
        if (historicalTransactionRepository.findBySourceReference(txnRef).isPresent()) {
            log.debug("Allocation {} already imported, skipping", allocation.paypropId);
            return;
        }

        // Find property
        Property property = propertyRepository.findByPayPropId(allocation.propertyPayPropId).orElse(null);
        if (property == null) {
            log.warn("Property not found: {}, skipping allocation", allocation.propertyPayPropId);
            return;
        }

        // Create owner allocation transaction
        HistoricalTransaction txn = new HistoricalTransaction();
        txn.setTransactionDate(allocation.dueDate);
        txn.setAmount(allocation.amount.negate()); // Negative = allocation to owner
        txn.setDescription("Owner allocation - " + allocation.propertyName);
        txn.setTransactionType(TransactionType.payment);
        txn.setCategory("owner_allocation");
        txn.setSource(TransactionSource.api_sync);
        txn.setSourceReference(txnRef);

        // Beneficiary tracking
        txn.setBeneficiaryType("beneficiary");
        txn.setBeneficiaryName(owner.getName());

        // Link to property and customer
        txn.setProperty(property);
        txn.setCustomer(owner);
        txn.setCreatedByUser(currentUser);

        // Link to invoice (lease) if available
        // Owner allocations are property-specific, so we can link them to the active lease
        site.easy.to.build.crm.entity.Invoice invoice = payPropInvoiceLinkingService.findInvoiceForTransaction(
            property,
            owner.getPayPropEntityId(),  // Owner's PayProp ID for matching
            allocation.dueDate
        );

        if (invoice != null) {
            txn.setInvoice(invoice);
            txn.setLeaseStartDate(invoice.getStartDate());
            txn.setLeaseEndDate(invoice.getEndDate());
            txn.setRentAmountAtTransaction(invoice.getAmount());

            log.debug("  ✓ Linked allocation to invoice {} (lease: {})",
                invoice.getId(), invoice.getLeaseReference());
        } else {
            log.debug("  ⚠ No invoice found for allocation on property {} dated {}",
                allocation.propertyName, allocation.dueDate);
        }

        // PayProp tracking
        txn.setPaypropTransactionId(allocation.paypropId);
        txn.setPaypropPropertyId(allocation.propertyPayPropId);
        txn.setPaypropBeneficiaryId(batch.ownerPayPropId);
        txn.setPaypropBatchId(batch.batchId);

        // Batch linking - allows grouping allocations by batch
        txn.setIncomingTransactionId(batch.batchId);
        txn.setIncomingTransactionAmount(batch.totalAmount);

        // Save
        HistoricalTransaction saved = historicalTransactionRepository.save(txn);

        log.debug("  ✓ Created allocation: {} - £{}", allocation.propertyName, allocation.amount);
    }

    /**
     * Import batch payment transaction (actual money transferred to bank)
     * This DECREASES the beneficiary balance
     */
    private void importBatchPayment(PaymentBatchData batch, Customer owner, User currentUser) {

        // Check if already imported
        String txnRef = "PAYPROP-BATCH-" + batch.batchId;
        if (historicalTransactionRepository.findBySourceReference(txnRef).isPresent()) {
            log.debug("Batch payment {} already imported, skipping", batch.batchId);
            return;
        }

        // Find a property for this owner (just for linking, can be any)
        Property property = null;
        if (!batch.allocations.isEmpty()) {
            String propertyPayPropId = batch.allocations.get(0).propertyPayPropId;
            property = propertyRepository.findByPayPropId(propertyPayPropId).orElse(null);
        }

        // Create batch payment transaction
        HistoricalTransaction txn = new HistoricalTransaction();
        txn.setTransactionDate(batch.transferDate);
        txn.setAmount(batch.totalAmount.negate()); // Negative = payment out
        txn.setDescription("Payment to " + owner.getName() + " - PayProp Batch " + batch.batchId);
        txn.setTransactionType(TransactionType.payment);
        txn.setCategory("owner_payment");
        txn.setSource(TransactionSource.api_sync);
        txn.setSourceReference(txnRef);

        // Payment details
        txn.setPaymentMethod("Bank Transfer");
        txn.setBankReference(batch.batchId);
        txn.setCounterpartyName(owner.getName());

        // Beneficiary tracking
        txn.setBeneficiaryType("beneficiary_payment");
        txn.setBeneficiaryName(owner.getName());

        // Link to customer (and property if found)
        txn.setCustomer(owner);
        if (property != null) {
            txn.setProperty(property);
        }
        txn.setCreatedByUser(currentUser);

        // PayProp tracking
        txn.setPaypropBatchId(batch.batchId);
        txn.setPaypropBeneficiaryId(batch.ownerPayPropId);

        // Notes about what this payment covers
        txn.setNotes(String.format("Batch payment covering %d property allocations from %s to %s",
                batch.allocations.size(),
                batch.allocations.stream().map(a -> a.dueDate).min(LocalDate::compareTo).orElse(null),
                batch.allocations.stream().map(a -> a.dueDate).max(LocalDate::compareTo).orElse(null)));

        // Save
        HistoricalTransaction saved = historicalTransactionRepository.save(txn);

        log.info("  💷 Created batch payment: £{} on {}", batch.totalAmount, batch.transferDate);
    }

    /**
     * Fetch all paid batches from PayProp data
     */
    private List<PaymentBatchData> fetchPaidBatches() throws SQLException {
        String sql = """
            SELECT
                payment_batch_id,
                payment_batch_status,
                payment_batch_transfer_date,
                beneficiary_payprop_id,
                beneficiary_name,
                SUM(amount) as total_amount,
                COUNT(*) as allocation_count
            FROM payprop_report_all_payments
            WHERE beneficiary_type = 'beneficiary'
            AND payment_batch_status = 'paid'
            AND payment_batch_id IS NOT NULL
            GROUP BY payment_batch_id, payment_batch_status, payment_batch_transfer_date,
                     beneficiary_payprop_id, beneficiary_name
            ORDER BY payment_batch_transfer_date
            """;

        List<PaymentBatchData> batches = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                PaymentBatchData batch = new PaymentBatchData();
                batch.batchId = rs.getString("payment_batch_id");
                batch.status = rs.getString("payment_batch_status");

                java.sql.Date transferDate = rs.getDate("payment_batch_transfer_date");
                batch.transferDate = transferDate != null ? transferDate.toLocalDate() : LocalDate.now();

                batch.ownerPayPropId = rs.getString("beneficiary_payprop_id");
                batch.ownerName = rs.getString("beneficiary_name");
                batch.totalAmount = rs.getBigDecimal("total_amount");

                // Fetch individual allocations for this batch
                batch.allocations = fetchBatchAllocations(batch.batchId);

                batches.add(batch);
            }
        }

        return batches;
    }

    /**
     * Fetch individual property allocations for a batch
     */
    private List<AllocationData> fetchBatchAllocations(String batchId) throws SQLException {
        String sql = """
            SELECT
                payprop_id,
                incoming_property_payprop_id,
                incoming_property_name,
                amount,
                due_date
            FROM payprop_report_all_payments
            WHERE payment_batch_id = ?
            AND beneficiary_type = 'beneficiary'
            ORDER BY due_date, incoming_property_name
            """;

        List<AllocationData> allocations = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, batchId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    AllocationData allocation = new AllocationData();
                    allocation.paypropId = rs.getString("payprop_id");
                    allocation.propertyPayPropId = rs.getString("incoming_property_payprop_id");
                    allocation.propertyName = rs.getString("incoming_property_name");
                    allocation.amount = rs.getBigDecimal("amount");

                    java.sql.Date dueDate = rs.getDate("due_date");
                    allocation.dueDate = dueDate != null ? dueDate.toLocalDate() : LocalDate.now();

                    allocations.add(allocation);
                }
            }
        }

        return allocations;
    }

    // ===== DATA CLASSES =====

    private static class PaymentBatchData {
        String batchId;
        String status;
        LocalDate transferDate;
        String ownerPayPropId;
        String ownerName;
        BigDecimal totalAmount;
        List<AllocationData> allocations;
    }

    private static class AllocationData {
        String paypropId;
        String propertyPayPropId;
        String propertyName;
        BigDecimal amount;
        LocalDate dueDate;
    }

    public static class PaymentImportResult {
        public boolean success;
        public LocalDateTime startTime;
        public LocalDateTime endTime;
        public int totalBatches;
        public int successfulBatches;
        public int failedBatches;
        public int allocationsCreated;
        public int paymentsCreated;
        public String errorMessage;
        public List<String> errors = new ArrayList<>();

        public long getDurationSeconds() {
            if (startTime != null && endTime != null) {
                return java.time.Duration.between(startTime, endTime).getSeconds();
            }
            return 0;
        }
    }
}

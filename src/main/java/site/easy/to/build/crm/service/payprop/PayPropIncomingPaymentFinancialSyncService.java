package site.easy.to.build.crm.service.payprop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.FinancialTransaction;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.repository.FinancialTransactionRepository;
import site.easy.to.build.crm.repository.PropertyRepository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service to import incoming tenant payments from payprop_export_incoming_payments
 * to financial_transactions table.
 *
 * Purpose:
 * - Read incoming tenant rent payments from raw export table
 * - Create financial_transaction records with data_source = "INCOMING_PAYMENT"
 * - Track actual cash received from tenants (not just allocations)
 *
 * This replaces the incorrect approach of using ICDN invoices to track incoming money.
 * ICDN = billing records (what tenants owe), not actual payments received.
 */
@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropIncomingPaymentFinancialSyncService {

    private static final Logger log = LoggerFactory.getLogger(PayPropIncomingPaymentFinancialSyncService.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PayPropInvoiceLinkingService invoiceLinkingService;

    /**
     * Import incoming tenant payments to financial_transactions
     *
     * @return Result summary with counts
     */
    @Transactional
    public IncomingPaymentSyncResult syncIncomingPaymentsToFinancialTransactions() {
        log.info("💰 Starting sync of incoming payments to financial_transactions");

        IncomingPaymentSyncResult result = new IncomingPaymentSyncResult();

        try {
            // 1. Read all incoming payments from export table
            List<IncomingPaymentRecord> incomingPayments = readIncomingPaymentsFromExport();
            result.totalIncomingPayments = incomingPayments.size();
            log.info("📥 Found {} incoming payment records to process", incomingPayments.size());

            // 2. Process each payment
            int imported = 0;
            int skipped = 0;
            int errors = 0;

            for (IncomingPaymentRecord payment : incomingPayments) {
                try {
                    // Check if already imported
                    boolean exists = financialTransactionRepository
                        .existsByPayPropTransactionIdAndDataSource(
                            payment.paypropId,
                            "INCOMING_PAYMENT"
                        );

                    if (exists) {
                        log.debug("⏭️ Skipping already imported: {}", payment.paypropId);
                        skipped++;
                        continue;
                    }

                    // Find property in our system
                    Property property = propertyRepository.findByPayPropId(payment.propertyPaypropId)
                        .orElse(null);
                    if (property == null) {
                        log.warn("⚠️ Property not found for PayProp ID: {} (payment: {})",
                            payment.propertyPaypropId, payment.paypropId);
                        errors++;
                        result.addError(payment.paypropId, "Property not found: " + payment.propertyPaypropId);
                        continue;
                    }

                    // Find tenant/customer in our system (optional - payment still valid without)
                    Customer tenant = null;
                    if (payment.tenantPaypropId != null && !payment.tenantPaypropId.isEmpty()) {
                        tenant = customerRepository.findByPayPropEntityId(payment.tenantPaypropId);
                        if (tenant == null) {
                            log.debug("ℹ️ Tenant not found for PayProp ID: {} (payment still imported)",
                                payment.tenantPaypropId);
                        }
                    }

                    // Create financial transaction
                    FinancialTransaction transaction = createFinancialTransaction(payment, property, tenant);
                    financialTransactionRepository.save(transaction);

                    imported++;
                    log.debug("✅ Imported incoming payment: {} - £{} - {}",
                        payment.paypropId, payment.amount, payment.propertyName);

                } catch (Exception e) {
                    log.error("❌ Failed to import payment {}: {}", payment.paypropId, e.getMessage());
                    errors++;
                    result.addError(payment.paypropId, e.getMessage());
                }
            }

            result.imported = imported;
            result.skipped = skipped;
            result.errors = errors;
            result.success = (errors == 0 || imported > 0);

            log.info("✅ Incoming payment sync complete: {} imported, {} skipped, {} errors",
                imported, skipped, errors);

        } catch (Exception e) {
            log.error("❌ Incoming payment sync failed", e);
            result.success = false;
            result.errorMessage = e.getMessage();
        }

        return result;
    }

    /**
     * Read incoming payments from payprop_export_incoming_payments table
     */
    private List<IncomingPaymentRecord> readIncomingPaymentsFromExport() throws Exception {
        List<IncomingPaymentRecord> payments = new ArrayList<>();

        String sql = """
            SELECT
                payprop_id,
                amount,
                reconciliation_date,
                transaction_type,
                transaction_status,
                tenant_payprop_id,
                tenant_name,
                property_payprop_id,
                property_name,
                deposit_id
            FROM payprop_export_incoming_payments
            ORDER BY reconciliation_date ASC
        """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                IncomingPaymentRecord payment = new IncomingPaymentRecord();
                payment.paypropId = rs.getString("payprop_id");
                payment.amount = rs.getBigDecimal("amount");

                java.sql.Date sqlDate = rs.getDate("reconciliation_date");
                payment.reconciliationDate = sqlDate != null ? sqlDate.toLocalDate() : null;

                payment.transactionType = rs.getString("transaction_type");
                payment.transactionStatus = rs.getString("transaction_status");
                payment.tenantPaypropId = rs.getString("tenant_payprop_id");
                payment.tenantName = rs.getString("tenant_name");
                payment.propertyPaypropId = rs.getString("property_payprop_id");
                payment.propertyName = rs.getString("property_name");
                payment.depositId = rs.getString("deposit_id");

                payments.add(payment);
            }
        }

        return payments;
    }

    /**
     * Create FinancialTransaction from incoming payment record
     */
    private FinancialTransaction createFinancialTransaction(
            IncomingPaymentRecord payment,
            Property property,
            Customer tenant) {

        FinancialTransaction transaction = new FinancialTransaction();

        // PayProp integration fields
        transaction.setPayPropTransactionId(payment.paypropId);
        transaction.setDataSource("INCOMING_PAYMENT");
        transaction.setIsActualTransaction(true); // This IS an actual payment received
        transaction.setIsInstruction(false); // Not a payment instruction

        // Transaction details
        transaction.setTransactionType("incoming_payment"); // New type for tenant payments
        transaction.setAmount(payment.amount);
        transaction.setTransactionDate(payment.reconciliationDate);

        // Property linkage
        transaction.setPropertyId(property.getId().toString());
        transaction.setPropertyName(payment.propertyName);

        // Tenant linkage (if found)
        if (tenant != null) {
            transaction.setTenantId(tenant.getCustomerId().toString());
            transaction.setTenantName(tenant.getName());
        } else if (payment.tenantName != null) {
            // Store PayProp tenant name even if not matched to local customer
            transaction.setTenantName(payment.tenantName);
        }

        // Description
        String description = String.format("Tenant Payment - %s - %s",
            payment.tenantName != null ? payment.tenantName : "Unknown Tenant",
            payment.propertyName);
        transaction.setDescription(description);

        // Category - use "Rent" as default category for incoming payments
        transaction.setCategoryName("Rent");

        // Tenant info - store in description
        if (payment.tenantPaypropId != null) {
            String fullDescription = String.format("%s (Tenant ID: %s, Deposit: %s)",
                description,
                payment.tenantPaypropId,
                payment.depositId != null ? payment.depositId : "N/A");
            transaction.setDescription(fullDescription);
        }

        // 🔗 CRITICAL FIX: Link to invoice (lease) for statement generation
        Invoice invoice = invoiceLinkingService.findInvoiceForTransaction(
            property,
            tenant,
            null, // Incoming payments don't have PayProp invoice ID
            payment.reconciliationDate
        );

        if (invoice != null) {
            transaction.setInvoice(invoice);
            log.info("✅ Linked incoming payment {} to invoice {} (lease: {})",
                payment.paypropId, invoice.getId(), invoice.getLeaseReference());
        } else {
            log.warn("⚠️ No invoice found for incoming payment {} - property: {}, tenant: {}",
                payment.paypropId,
                property.getId(),
                tenant != null ? tenant.getCustomerId() : "NULL");
        }

        return transaction;
    }

    /**
     * Result object for sync operation
     */
    public static class IncomingPaymentSyncResult {
        public boolean success;
        public int totalIncomingPayments;
        public int imported;
        public int skipped;
        public int errors;
        public String errorMessage;
        public List<String> errorDetails = new ArrayList<>();

        public void addError(String paymentId, String error) {
            errorDetails.add(String.format("%s: %s", paymentId, error));
        }

        @Override
        public String toString() {
            return String.format("IncomingPaymentSyncResult{success=%s, total=%d, imported=%d, skipped=%d, errors=%d}",
                success, totalIncomingPayments, imported, skipped, errors);
        }
    }

    /**
     * Internal record structure for incoming payment data
     */
    private static class IncomingPaymentRecord {
        String paypropId;
        BigDecimal amount;
        LocalDate reconciliationDate;
        String transactionType;
        String transactionStatus;
        String tenantPaypropId;
        String tenantName;
        String propertyPaypropId;
        String propertyName;
        String depositId;
    }
}

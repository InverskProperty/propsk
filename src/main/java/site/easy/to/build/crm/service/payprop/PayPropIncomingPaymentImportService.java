package site.easy.to.build.crm.service.payprop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import site.easy.to.build.crm.entity.HistoricalTransaction;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.entity.HistoricalTransaction.TransactionSource;
import site.easy.to.build.crm.entity.HistoricalTransaction.TransactionType;
import site.easy.to.build.crm.repository.HistoricalTransactionRepository;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.service.transaction.HistoricalTransactionSplitService;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports incoming tenant payments from payprop_incoming_payments to historical_transactions
 *
 * This service:
 * 1. Reads unsynced incoming payments from payprop_incoming_payments
 * 2. Creates HistoricalTransaction records (category: rent)
 * 3. Sets incoming_transaction_amount to trigger commission/owner split
 * 4. Marks records as synced_to_historical = TRUE
 *
 * RUNS AFTER: PayPropIncomingPaymentExtractorService
 *
 * TRIGGERS: TransactionSplitService (via HistoricalTransactionImportService logic)
 *   - Creates management_fee transaction (commission)
 *   - Creates owner_allocation transaction (net to owner)
 */
@Service
@Transactional
public class PayPropIncomingPaymentImportService {

    private static final Logger log = LoggerFactory.getLogger(PayPropIncomingPaymentImportService.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private HistoricalTransactionRepository historicalTransactionRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private HistoricalTransactionSplitService transactionSplitService;

    /**
     * Import all unsynced incoming payments to historical transactions
     */
    public ImportResult importIncomingPayments(User currentUser) {
        log.info("🔄 Starting import of incoming payments to historical transactions");

        ImportResult result = new ImportResult();
        result.startTime = LocalDateTime.now();

        try {
            // Fetch unsynced incoming payments
            List<IncomingPaymentRecord> payments = fetchUnsyncedPayments();
            result.totalFound = payments.size();

            log.info("📦 Found {} unsynced incoming payments", payments.size());

            if (payments.isEmpty()) {
                log.info("✅ No incoming payments to sync");
                result.success = true;
                result.endTime = LocalDateTime.now();
                return result;
            }

            // Import each payment
            for (IncomingPaymentRecord payment : payments) {
                try {
                    importSinglePayment(payment, currentUser);
                    result.successfulImports++;

                } catch (Exception e) {
                    log.error("Failed to import incoming payment {}: {}",
                        payment.incomingTransactionId, e.getMessage(), e);
                    result.failedImports++;
                    result.errors.add(payment.incomingTransactionId + ": " + e.getMessage());

                    // Mark as failed in payprop_incoming_payments
                    markSyncFailed(payment.id, e.getMessage());
                }
            }

            result.success = true;
            result.endTime = LocalDateTime.now();

            log.info("✅ Import complete: {} succeeded, {} failed",
                result.successfulImports, result.failedImports);

        } catch (Exception e) {
            log.error("❌ Incoming payment import failed", e);
            result.success = false;
            result.errorMessage = e.getMessage();
            result.endTime = LocalDateTime.now();
        }

        return result;
    }

    /**
     * Fetch unsynced incoming payments from payprop_incoming_payments
     */
    private List<IncomingPaymentRecord> fetchUnsyncedPayments() throws SQLException {
        List<IncomingPaymentRecord> payments = new ArrayList<>();

        String sql = """
            SELECT
                id,
                incoming_transaction_id,
                amount,
                reconciliation_date,
                transaction_type,
                transaction_status,
                tenant_payprop_id,
                tenant_name,
                property_payprop_id,
                property_name,
                deposit_id
            FROM payprop_incoming_payments
            WHERE synced_to_historical = FALSE
            ORDER BY reconciliation_date ASC
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                IncomingPaymentRecord payment = new IncomingPaymentRecord();
                payment.id = rs.getLong("id");
                payment.incomingTransactionId = rs.getString("incoming_transaction_id");
                payment.amount = rs.getBigDecimal("amount");
                payment.reconciliationDate = rs.getDate("reconciliation_date").toLocalDate();
                payment.transactionType = rs.getString("transaction_type");
                payment.transactionStatus = rs.getString("transaction_status");
                payment.tenantPayPropId = rs.getString("tenant_payprop_id");
                payment.tenantName = rs.getString("tenant_name");
                payment.propertyPayPropId = rs.getString("property_payprop_id");
                payment.propertyName = rs.getString("property_name");
                payment.depositId = rs.getString("deposit_id");

                payments.add(payment);
            }
        }

        return payments;
    }

    /**
     * Import a single incoming payment to historical_transactions
     */
    private void importSinglePayment(IncomingPaymentRecord payment, User currentUser) throws Exception {
        log.debug("📥 Importing: {} - {} - £{}",
            payment.reconciliationDate, payment.propertyName, payment.amount);

        // Find property
        Property property = propertyRepository.findByPayPropId(payment.propertyPayPropId)
            .orElseThrow(() -> new RuntimeException("Property not found: " + payment.propertyPayPropId));

        // Check for duplicate (source_reference based)
        String sourceRef = "PAYPROP-INCOMING-" + payment.incomingTransactionId;
        if (historicalTransactionRepository.findBySourceReference(sourceRef).isPresent()) {
            log.warn("⚠️  Incoming payment {} already imported, marking as synced",
                payment.incomingTransactionId);
            markAsSynced(payment.id, null); // Mark synced but don't link (duplicate)
            return;
        }

        // Create incoming rent payment transaction
        HistoricalTransaction txn = new HistoricalTransaction();
        txn.setTransactionDate(payment.reconciliationDate);
        txn.setAmount(payment.amount); // Positive = money received
        txn.setDescription(buildDescription(payment));
        txn.setTransactionType(TransactionType.payment);
        txn.setCategory("rent");
        txn.setSource(TransactionSource.api_sync);
        txn.setSourceReference(sourceRef);

        // Link to property
        txn.setProperty(property);
        txn.setCreatedByUser(currentUser);

        // PayProp tracking
        txn.setPaypropTransactionId(payment.incomingTransactionId);
        txn.setPaypropPropertyId(payment.propertyPayPropId);
        txn.setPaypropTenantId(payment.tenantPayPropId);

        // CRITICAL: Set incoming_transaction_amount to trigger split logic
        // This tells HistoricalTransactionImportService to auto-create:
        //   - Management fee (commission)
        //   - Owner allocation (net to owner)
        txn.setIncomingTransactionAmount(payment.amount);

        // Save
        HistoricalTransaction saved = historicalTransactionRepository.save(txn);

        log.debug("  ✓ Created historical transaction ID: {}", saved.getId());

        // Trigger commission/owner split
        triggerTransactionSplit(saved);

        // Mark as synced in payprop_incoming_payments
        markAsSynced(payment.id, saved.getId());

        log.debug("  ✓ Marked as synced in payprop_incoming_payments");
    }

    /**
     * Build description for incoming payment transaction
     */
    private String buildDescription(IncomingPaymentRecord payment) {
        StringBuilder desc = new StringBuilder();
        desc.append("Tenant Rent Payment");

        if (payment.tenantName != null && !payment.tenantName.isEmpty()) {
            desc.append(" - ").append(payment.tenantName);
        }

        if (payment.propertyName != null && !payment.propertyName.isEmpty()) {
            desc.append(" - ").append(payment.propertyName);
        }

        return desc.toString();
    }

    /**
     * Trigger transaction split to create commission and owner allocation
     * This replicates the logic in HistoricalTransactionImportService:461-466
     */
    private void triggerTransactionSplit(HistoricalTransaction incoming) {
        try {
            // Check if split should be created (same logic as HistoricalTransactionImportService)
            if (incoming.getIncomingTransactionAmount() != null &&
                incoming.getIncomingTransactionAmount().compareTo(BigDecimal.ZERO) != 0) {

                log.debug("  🔀 Creating commission/owner split for £{}",
                    incoming.getIncomingTransactionAmount());

                // Use HistoricalTransactionSplitService to create allocations
                transactionSplitService.createBeneficiaryAllocationsFromIncoming(incoming);

                log.debug("  ✓ Commission and owner allocation created");
            }

        } catch (Exception e) {
            log.error("Failed to create transaction split for {}: {}",
                incoming.getId(), e.getMessage(), e);
            // Don't fail the entire import, just log the error
        }
    }

    /**
     * Mark incoming payment as synced in payprop_incoming_payments
     */
    private void markAsSynced(Long paymentId, Long historicalTransactionId) throws SQLException {
        String sql = """
            UPDATE payprop_incoming_payments
            SET synced_to_historical = TRUE,
                historical_transaction_id = ?,
                sync_attempted_at = ?,
                sync_error = NULL
            WHERE id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (historicalTransactionId != null) {
                stmt.setLong(1, historicalTransactionId);
            } else {
                stmt.setNull(1, java.sql.Types.BIGINT);
            }
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(3, paymentId);

            stmt.executeUpdate();
        }
    }

    /**
     * Mark incoming payment sync as failed
     */
    private void markSyncFailed(Long paymentId, String errorMessage) {
        String sql = """
            UPDATE payprop_incoming_payments
            SET sync_attempted_at = ?,
                sync_error = ?
            WHERE id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(2, errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage);
            stmt.setLong(3, paymentId);

            stmt.executeUpdate();

        } catch (SQLException e) {
            log.error("Failed to mark payment {} as failed: {}", paymentId, e.getMessage());
        }
    }

    // ==================== Data Classes ====================

    /**
     * Record from payprop_incoming_payments table
     */
    private static class IncomingPaymentRecord {
        Long id;
        String incomingTransactionId;
        BigDecimal amount;
        LocalDate reconciliationDate;
        String transactionType;
        String transactionStatus;
        String tenantPayPropId;
        String tenantName;
        String propertyPayPropId;
        String propertyName;
        String depositId;
    }

    /**
     * Result of import operation
     */
    public static class ImportResult {
        public LocalDateTime startTime;
        public LocalDateTime endTime;
        public boolean success;
        public String errorMessage;
        public int totalFound;
        public int successfulImports;
        public int failedImports;
        public List<String> errors = new ArrayList<>();

        @Override
        public String toString() {
            return String.format("ImportResult{found=%d, success=%d, failed=%d, errors=%d}",
                totalFound, successfulImports, failedImports, errors.size());
        }
    }
}

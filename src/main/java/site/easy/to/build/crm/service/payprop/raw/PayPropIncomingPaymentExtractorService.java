package site.easy.to.build.crm.service.payprop.raw;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Extracts unique incoming tenant payments from payprop_report_all_payments
 * and stores them in payprop_incoming_payments table.
 *
 * WHY THIS SERVICE EXISTS:
 * - One tenant payment (£700) appears in MULTIPLE allocation records (commission + owner)
 * - This service deduplicates and extracts the unique incoming payments
 * - Enables tracking which tenant paid, when, and how much
 *
 * RUNS AFTER: PayPropRawAllPaymentsImportService
 * RUNS BEFORE: PayPropIncomingPaymentImportService
 */
@Service
public class PayPropIncomingPaymentExtractorService {

    private static final Logger log = LoggerFactory.getLogger(PayPropIncomingPaymentExtractorService.class);

    @Autowired
    private DataSource dataSource;

    /**
     * Extract all unique incoming payments from raw all-payments data
     */
    @Transactional
    public ExtractionResult extractIncomingPayments() {
        log.info("🔄 Starting incoming payment extraction from payprop_report_all_payments");

        ExtractionResult result = new ExtractionResult();
        result.setStartTime(LocalDateTime.now());

        try {
            // Step 1: Get distinct incoming transactions from raw table
            List<IncomingPaymentData> incomingPayments = fetchDistinctIncomingPayments();
            result.setTotalFound(incomingPayments.size());

            log.info("📦 Found {} unique incoming payments in raw data", incomingPayments.size());

            // Step 2: Upsert to payprop_incoming_payments table
            int extracted = upsertIncomingPayments(incomingPayments);
            result.setTotalExtracted(extracted);

            // Step 3: Get statistics
            ExtractionStats stats = getExtractionStats();
            result.setStats(stats);

            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());

            log.info("✅ Extraction complete: {} found, {} extracted/updated",
                incomingPayments.size(), extracted);
            log.info("📊 Stats: {} total in table, {} synced to historical, {} pending sync",
                stats.totalInTable, stats.syncedCount, stats.pendingSyncCount);

        } catch (Exception e) {
            log.error("❌ Incoming payment extraction failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }

        return result;
    }

    /**
     * Fetch distinct incoming payments from payprop_report_all_payments
     * Each incoming payment may appear in multiple allocation records, so we SELECT DISTINCT
     */
    private List<IncomingPaymentData> fetchDistinctIncomingPayments() throws SQLException {
        List<IncomingPaymentData> payments = new ArrayList<>();

        String sql = """
            SELECT DISTINCT
                incoming_transaction_id,
                incoming_transaction_amount,
                incoming_transaction_reconciliation_date,
                incoming_transaction_type,
                incoming_transaction_status,
                incoming_tenant_payprop_id,
                incoming_tenant_name,
                incoming_property_payprop_id,
                incoming_property_name,
                incoming_transaction_deposit_id
            FROM payprop_report_all_payments
            WHERE incoming_transaction_id IS NOT NULL
              AND incoming_transaction_amount IS NOT NULL
              AND incoming_transaction_reconciliation_date IS NOT NULL
            ORDER BY incoming_transaction_reconciliation_date DESC
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                IncomingPaymentData payment = new IncomingPaymentData();
                payment.incomingTransactionId = rs.getString("incoming_transaction_id");
                payment.amount = rs.getBigDecimal("incoming_transaction_amount");
                payment.reconciliationDate = rs.getDate("incoming_transaction_reconciliation_date").toLocalDate();
                payment.transactionType = rs.getString("incoming_transaction_type");
                payment.transactionStatus = rs.getString("incoming_transaction_status");
                payment.tenantPayPropId = rs.getString("incoming_tenant_payprop_id");
                payment.tenantName = rs.getString("incoming_tenant_name");
                payment.propertyPayPropId = rs.getString("incoming_property_payprop_id");
                payment.propertyName = rs.getString("incoming_property_name");
                payment.depositId = rs.getString("incoming_transaction_deposit_id");

                payments.add(payment);
            }
        }

        return payments;
    }

    /**
     * Upsert incoming payments to payprop_incoming_payments table
     * Uses INSERT ... ON DUPLICATE KEY UPDATE to handle re-runs gracefully
     */
    private int upsertIncomingPayments(List<IncomingPaymentData> payments) throws SQLException {
        if (payments.isEmpty()) {
            log.warn("No incoming payments to extract");
            return 0;
        }

        String insertSql = """
            INSERT INTO payprop_incoming_payments (
                incoming_transaction_id,
                amount,
                reconciliation_date,
                transaction_type,
                transaction_status,
                tenant_payprop_id,
                tenant_name,
                property_payprop_id,
                property_name,
                deposit_id,
                extracted_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                amount = VALUES(amount),
                reconciliation_date = VALUES(reconciliation_date),
                transaction_type = VALUES(transaction_type),
                transaction_status = VALUES(transaction_status),
                tenant_payprop_id = VALUES(tenant_payprop_id),
                tenant_name = VALUES(tenant_name),
                property_payprop_id = VALUES(property_payprop_id),
                property_name = VALUES(property_name),
                deposit_id = VALUES(deposit_id),
                extracted_at = VALUES(extracted_at)
            """;

        int upsertedCount = 0;
        int newRecords = 0;
        int updatedRecords = 0;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {

            for (IncomingPaymentData payment : payments) {
                // Check if this is a new record or update
                boolean isNew = !paymentExists(conn, payment.incomingTransactionId);

                stmt.setString(1, payment.incomingTransactionId);
                stmt.setBigDecimal(2, payment.amount);
                stmt.setDate(3, java.sql.Date.valueOf(payment.reconciliationDate));
                stmt.setString(4, payment.transactionType);
                stmt.setString(5, payment.transactionStatus);
                stmt.setString(6, payment.tenantPayPropId);
                stmt.setString(7, payment.tenantName);
                stmt.setString(8, payment.propertyPayPropId);
                stmt.setString(9, payment.propertyName);
                stmt.setString(10, payment.depositId);
                stmt.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now()));

                stmt.addBatch();

                if (isNew) {
                    newRecords++;
                } else {
                    updatedRecords++;
                }

                // Execute batch every 50 items
                if ((upsertedCount + 1) % 50 == 0) {
                    stmt.executeBatch();
                    upsertedCount += 50;
                }
            }

            // Execute remaining batch
            if (payments.size() % 50 != 0) {
                stmt.executeBatch();
                upsertedCount += payments.size() % 50;
            }

            log.info("📊 Extraction summary:");
            log.info("   New incoming payments: {}", newRecords);
            log.info("   Updated existing: {}", updatedRecords);
            log.info("   Total upserted: {}", upsertedCount);
        }

        return upsertedCount;
    }

    /**
     * Check if incoming payment already exists in table
     */
    private boolean paymentExists(Connection conn, String incomingTransactionId) throws SQLException {
        String sql = "SELECT 1 FROM payprop_incoming_payments WHERE incoming_transaction_id = ? LIMIT 1";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, incomingTransactionId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Get extraction statistics
     */
    private ExtractionStats getExtractionStats() throws SQLException {
        ExtractionStats stats = new ExtractionStats();

        String sql = """
            SELECT
                COUNT(*) as total_count,
                SUM(CASE WHEN synced_to_historical = TRUE THEN 1 ELSE 0 END) as synced_count,
                SUM(CASE WHEN synced_to_historical = FALSE THEN 1 ELSE 0 END) as pending_count,
                SUM(amount) as total_amount,
                MIN(reconciliation_date) as earliest_date,
                MAX(reconciliation_date) as latest_date
            FROM payprop_incoming_payments
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                stats.totalInTable = rs.getInt("total_count");
                stats.syncedCount = rs.getInt("synced_count");
                stats.pendingSyncCount = rs.getInt("pending_count");
                stats.totalAmount = rs.getBigDecimal("total_amount");

                java.sql.Date earliest = rs.getDate("earliest_date");
                java.sql.Date latest = rs.getDate("latest_date");

                stats.earliestDate = earliest != null ? earliest.toLocalDate() : null;
                stats.latestDate = latest != null ? latest.toLocalDate() : null;
            }
        }

        return stats;
    }

    // ==================== Data Classes ====================

    /**
     * Data class for incoming payment
     */
    private static class IncomingPaymentData {
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
     * Result of extraction operation
     */
    public static class ExtractionResult {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private boolean success;
        private String errorMessage;
        private int totalFound;
        private int totalExtracted;
        private ExtractionStats stats;

        // Getters and setters
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public int getTotalFound() { return totalFound; }
        public void setTotalFound(int totalFound) { this.totalFound = totalFound; }

        public int getTotalExtracted() { return totalExtracted; }
        public void setTotalExtracted(int totalExtracted) { this.totalExtracted = totalExtracted; }

        public ExtractionStats getStats() { return stats; }
        public void setStats(ExtractionStats stats) { this.stats = stats; }
    }

    /**
     * Statistics about extracted payments
     */
    public static class ExtractionStats {
        private int totalInTable;
        private int syncedCount;
        private int pendingSyncCount;
        private BigDecimal totalAmount;
        private LocalDate earliestDate;
        private LocalDate latestDate;

        // Getters
        public int getTotalInTable() { return totalInTable; }
        public int getSyncedCount() { return syncedCount; }
        public int getPendingSyncCount() { return pendingSyncCount; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public LocalDate getEarliestDate() { return earliestDate; }
        public LocalDate getLatestDate() { return latestDate; }
    }
}

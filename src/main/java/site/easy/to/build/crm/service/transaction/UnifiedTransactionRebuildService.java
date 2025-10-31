package site.easy.to.build.crm.service.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.UnifiedTransaction;
import site.easy.to.build.crm.repository.UnifiedTransactionRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for rebuilding the unified_transactions table from source systems
 *
 * This service provides the ability to completely rebuild or incrementally update
 * the unified transaction view from:
 * - historical_transactions (CSV imports)
 * - financial_transactions (PayProp sync)
 *
 * KEY PRINCIPLE: unified_transactions is a MATERIALIZED VIEW that can be
 * deleted and rebuilt anytime without data loss.
 */
@Service
public class UnifiedTransactionRebuildService {

    private static final Logger log = LoggerFactory.getLogger(UnifiedTransactionRebuildService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UnifiedTransactionRepository unifiedTransactionRepository;

    /**
     * Complete rebuild of unified_transactions table
     * Deletes all records and rebuilds from source tables
     *
     * @return Rebuild statistics
     */
    @Transactional
    public Map<String, Object> rebuildComplete() {
        String batchId = "REBUILD-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

        log.info("🔄 Starting complete unified transactions rebuild - Batch ID: {}", batchId);

        Map<String, Object> result = new HashMap<>();
        result.put("batchId", batchId);
        result.put("startTime", LocalDateTime.now());

        try {
            // Step 1: Truncate unified_transactions
            log.info("📋 Step 1: Truncating unified_transactions...");
            jdbcTemplate.execute("TRUNCATE TABLE unified_transactions");
            log.info("✅ Table truncated");

            // Step 2: Insert from historical_transactions
            log.info("📋 Step 2: Inserting from historical_transactions...");
            int historicalCount = insertFromHistoricalTransactions(batchId);
            result.put("historicalRecordsInserted", historicalCount);
            log.info("✅ Inserted {} records from historical_transactions", historicalCount);

            // Step 3: Insert from financial_transactions
            log.info("📋 Step 3: Inserting from financial_transactions...");
            int paypropCount = insertFromFinancialTransactions(batchId);
            result.put("paypropRecordsInserted", paypropCount);
            log.info("✅ Inserted {} records from financial_transactions", paypropCount);

            // Step 4: Verify rebuild
            log.info("📋 Step 4: Verifying rebuild...");
            Map<String, Object> verification = verifyRebuild();
            result.put("verification", verification);

            result.put("status", "SUCCESS");
            result.put("endTime", LocalDateTime.now());

            LocalDateTime startTime = (LocalDateTime) result.get("startTime");
            long durationSeconds = ChronoUnit.SECONDS.between(startTime, (LocalDateTime) result.get("endTime"));
            result.put("durationSeconds", durationSeconds);

            int totalRecords = historicalCount + paypropCount;
            log.info("🎉 Rebuild complete! Historical: {}, PayProp: {}, Total: {}, Duration: {}s",
                historicalCount, paypropCount, totalRecords, durationSeconds);

            return result;

        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("errorMessage", e.getMessage());
            result.put("endTime", LocalDateTime.now());
            log.error("❌ Rebuild failed: {}", e.getMessage(), e);
            throw new RuntimeException("Unified transaction rebuild failed", e);
        }
    }

    /**
     * Insert transactions from historical_transactions table
     */
    private int insertFromHistoricalTransactions(String batchId) {
        String sql = """
            INSERT INTO unified_transactions (
                source_system, source_table, source_record_id,
                transaction_date, amount, description, category,
                invoice_id, property_id, customer_id,
                lease_reference, lease_start_date, lease_end_date,
                rent_amount_at_transaction, property_name,
                rebuilt_at, rebuild_batch_id
            )
            SELECT
                'HISTORICAL' as source_system,
                'historical_transactions' as source_table,
                ht.id as source_record_id,
                ht.transaction_date,
                ht.amount,
                ht.description,
                ht.category,
                ht.invoice_id,
                ht.property_id,
                ht.customer_id,
                i.lease_reference,
                ht.lease_start_date,
                ht.lease_end_date,
                ht.rent_amount_at_transaction,
                p.property_name,
                NOW() as rebuilt_at,
                ? as rebuild_batch_id
            FROM historical_transactions ht
            LEFT JOIN properties p ON ht.property_id = p.id
            LEFT JOIN invoices i ON ht.invoice_id = i.id
            WHERE ht.invoice_id IS NOT NULL
        """;

        return jdbcTemplate.update(sql, batchId);
    }

    /**
     * Insert transactions from financial_transactions table
     */
    private int insertFromFinancialTransactions(String batchId) {
        String sql = """
            INSERT INTO unified_transactions (
                source_system, source_table, source_record_id,
                transaction_date, amount, description, category,
                invoice_id, property_id,
                lease_reference, property_name,
                payprop_transaction_id, payprop_data_source,
                rebuilt_at, rebuild_batch_id
            )
            SELECT
                'PAYPROP' as source_system,
                'financial_transactions' as source_table,
                ft.id as source_record_id,
                ft.transaction_date,
                ft.amount,
                ft.description,
                ft.category_name as category,
                ft.invoice_id,
                p.id as property_id,
                i.lease_reference,
                ft.property_name,
                ft.pay_prop_transaction_id as payprop_transaction_id,
                ft.data_source as payprop_data_source,
                NOW() as rebuilt_at,
                ? as rebuild_batch_id
            FROM financial_transactions ft
            LEFT JOIN properties p ON ft.property_id = p.payprop_id
            LEFT JOIN invoices i ON ft.invoice_id = i.id
            WHERE ft.invoice_id IS NOT NULL
              AND ft.data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV', 'ICDN_ACTUAL')
        """;

        return jdbcTemplate.update(sql, batchId);
    }

    /**
     * Verify rebuild by counting records per source system
     */
    private Map<String, Object> verifyRebuild() {
        String sql = """
            SELECT
                source_system,
                COUNT(*) as count,
                MIN(transaction_date) as earliest,
                MAX(transaction_date) as latest,
                SUM(amount) as total_amount
            FROM unified_transactions
            GROUP BY source_system
        """;

        Map<String, Object> stats = new HashMap<>();

        jdbcTemplate.query(sql, rs -> {
            String sourceSystem = rs.getString("source_system");
            Map<String, Object> sourceStats = new HashMap<>();
            sourceStats.put("count", rs.getInt("count"));
            sourceStats.put("earliest", rs.getDate("earliest") != null ? rs.getDate("earliest").toLocalDate() : null);
            sourceStats.put("latest", rs.getDate("latest") != null ? rs.getDate("latest").toLocalDate() : null);
            sourceStats.put("totalAmount", rs.getBigDecimal("total_amount"));
            stats.put(sourceSystem, sourceStats);
        });

        return stats;
    }

    /**
     * Incremental update - only rebuild transactions changed since last rebuild
     * Use this after PayProp sync or historical import
     *
     * @param since Only update records modified after this timestamp
     */
    @Transactional
    public Map<String, Object> rebuildIncremental(LocalDateTime since) {
        String batchId = "INCREMENTAL-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

        log.info("🔄 Starting incremental rebuild since {} - Batch ID: {}", since, batchId);

        Map<String, Object> result = new HashMap<>();
        result.put("batchId", batchId);
        result.put("since", since);
        result.put("startTime", LocalDateTime.now());

        try {
            // Step 1: Delete records from changed source transactions
            log.info("📋 Step 1: Deleting changed records...");

            String deleteHistoricalSql = """
                DELETE FROM unified_transactions
                WHERE source_system = 'HISTORICAL'
                  AND source_record_id IN (
                    SELECT id FROM historical_transactions WHERE updated_at > ?
                  )
            """;
            int deletedHistorical = jdbcTemplate.update(deleteHistoricalSql, since);

            String deletePaypropSql = """
                DELETE FROM unified_transactions
                WHERE source_system = 'PAYPROP'
                  AND source_record_id IN (
                    SELECT id FROM financial_transactions WHERE updated_at > ?
                  )
            """;
            int deletedPayprop = jdbcTemplate.update(deletePaypropSql, since);

            result.put("deletedHistorical", deletedHistorical);
            result.put("deletedPayprop", deletedPayprop);
            log.info("✅ Deleted {} historical + {} payprop records", deletedHistorical, deletedPayprop);

            // Step 2: Re-insert updated historical records
            log.info("📋 Step 2: Re-inserting updated historical transactions...");
            int insertedHistorical = insertUpdatedHistoricalTransactions(since, batchId);
            result.put("insertedHistorical", insertedHistorical);
            log.info("✅ Inserted {} historical records", insertedHistorical);

            // Step 3: Re-insert updated financial records
            log.info("📋 Step 3: Re-inserting updated financial transactions...");
            int insertedPayprop = insertUpdatedFinancialTransactions(since, batchId);
            result.put("insertedPayprop", insertedPayprop);
            log.info("✅ Inserted {} payprop records", insertedPayprop);

            result.put("status", "SUCCESS");
            result.put("endTime", LocalDateTime.now());

            log.info("🎉 Incremental rebuild complete! Deleted: {}, Inserted: {}",
                deletedHistorical + deletedPayprop, insertedHistorical + insertedPayprop);

            return result;

        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("errorMessage", e.getMessage());
            result.put("endTime", LocalDateTime.now());
            log.error("❌ Incremental rebuild failed: {}", e.getMessage(), e);
            throw new RuntimeException("Incremental rebuild failed", e);
        }
    }

    private int insertUpdatedHistoricalTransactions(LocalDateTime since, String batchId) {
        String sql = """
            INSERT INTO unified_transactions (
                source_system, source_table, source_record_id,
                transaction_date, amount, description, category,
                invoice_id, property_id, customer_id,
                lease_reference, lease_start_date, lease_end_date,
                rent_amount_at_transaction, property_name,
                rebuilt_at, rebuild_batch_id
            )
            SELECT
                'HISTORICAL', 'historical_transactions', ht.id,
                ht.transaction_date, ht.amount, ht.description, ht.category,
                ht.invoice_id, ht.property_id, ht.customer_id,
                i.lease_reference, ht.lease_start_date, ht.lease_end_date,
                ht.rent_amount_at_transaction, p.property_name,
                NOW(), ?
            FROM historical_transactions ht
            LEFT JOIN properties p ON ht.property_id = p.id
            LEFT JOIN invoices i ON ht.invoice_id = i.id
            WHERE ht.invoice_id IS NOT NULL
              AND ht.updated_at > ?
        """;

        return jdbcTemplate.update(sql, batchId, since);
    }

    private int insertUpdatedFinancialTransactions(LocalDateTime since, String batchId) {
        String sql = """
            INSERT INTO unified_transactions (
                source_system, source_table, source_record_id,
                transaction_date, amount, description, category,
                invoice_id, property_id,
                lease_reference, property_name,
                payprop_transaction_id, payprop_data_source,
                rebuilt_at, rebuild_batch_id
            )
            SELECT
                'PAYPROP', 'financial_transactions', ft.id,
                ft.transaction_date, ft.amount, ft.description, ft.category_name,
                ft.invoice_id, p.id,
                i.lease_reference, ft.property_name,
                ft.pay_prop_transaction_id, ft.data_source,
                NOW(), ?
            FROM financial_transactions ft
            LEFT JOIN properties p ON ft.property_id = p.payprop_id
            LEFT JOIN invoices i ON ft.invoice_id = i.id
            WHERE ft.invoice_id IS NOT NULL
              AND ft.data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV', 'ICDN_ACTUAL')
              AND ft.updated_at > ?
        """;

        return jdbcTemplate.update(sql, batchId, since);
    }

    /**
     * Get rebuild statistics (for admin dashboard)
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // Get total count
        long totalCount = unifiedTransactionRepository.count();
        stats.put("totalRecords", totalCount);

        // Get counts by source system
        List<Object[]> sourceStats = unifiedTransactionRepository.getRebuildStatistics();
        Map<String, Map<String, Object>> bySource = new HashMap<>();

        for (Object[] row : sourceStats) {
            String sourceSystem = row[0].toString();
            Map<String, Object> sourceInfo = new HashMap<>();
            sourceInfo.put("count", row[1]);
            sourceInfo.put("earliest", row[2]);
            sourceInfo.put("latest", row[3]);
            sourceInfo.put("totalAmount", row[4]);
            bySource.put(sourceSystem, sourceInfo);
        }

        stats.put("bySource", bySource);

        // Get last rebuild timestamp
        String lastRebuildSql = "SELECT MAX(rebuilt_at) FROM unified_transactions";
        LocalDateTime lastRebuild = jdbcTemplate.queryForObject(lastRebuildSql, LocalDateTime.class);
        stats.put("lastRebuiltAt", lastRebuild);

        return stats;
    }
}

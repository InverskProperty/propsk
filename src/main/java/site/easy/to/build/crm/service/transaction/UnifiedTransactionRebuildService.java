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

            // Step 4: Migrate allocations to unified layer (link unified_transaction_id)
            log.info("📋 Step 4: Migrating allocations to unified_transactions...");
            int migratedAllocations = migrateAllocationsToUnified();
            result.put("migratedAllocations", migratedAllocations);
            log.info("✅ Migrated {} allocations to unified_transaction_id", migratedAllocations);

            // Step 5: Sync allocations to unified_allocations table
            log.info("📋 Step 5: Syncing allocations to unified_allocations...");
            int syncedAllocations = syncAllocationsToUnifiedAllocations(batchId);
            result.put("syncedAllocations", syncedAllocations);
            log.info("✅ Synced {} allocations to unified_allocations", syncedAllocations);

            // Step 6: Verify rebuild
            log.info("📋 Step 6: Verifying rebuild...");
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
                transaction_date, amount, net_to_owner_amount, commission_rate, commission_amount,
                description, category,
                invoice_id, property_id, customer_id,
                lease_reference, lease_start_date, lease_end_date,
                rent_amount_at_transaction, property_name,
                transaction_type, flow_direction,
                rebuilt_at, rebuild_batch_id
            )
            SELECT
                'HISTORICAL' as source_system,
                'historical_transactions' as source_table,
                ht.id as source_record_id,
                ht.transaction_date,
                CASE
                    WHEN ht.category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee')
                        OR ht.category LIKE '%expense%' OR ht.category LIKE '%Expense%'
                    THEN ABS(ht.amount)
                    ELSE ht.amount
                END as amount,
                ht.net_to_owner_amount,
                ht.commission_rate,
                ht.commission_amount,
                ht.description,
                ht.category,
                COALESCE(ht.invoice_id, active_lease.id) as invoice_id,
                ht.property_id,
                ht.customer_id,
                COALESCE(i.lease_reference, active_lease.lease_reference) as lease_reference,
                ht.lease_start_date,
                ht.lease_end_date,
                ht.rent_amount_at_transaction,
                p.property_name,
                CASE
                    WHEN ht.category LIKE '%rent%' OR ht.category LIKE '%Rent%' THEN 'rent_received'
                    WHEN ht.category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee')
                        OR ht.category LIKE '%expense%' OR ht.category LIKE '%Expense%' THEN 'expense'
                    WHEN ht.category = 'owner_payment' THEN 'payment_to_beneficiary'
                    ELSE 'other'
                END as transaction_type,
                CASE
                    WHEN ht.category LIKE '%rent%' OR ht.category LIKE '%Rent%' THEN 'INCOMING'
                    WHEN ht.category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee')
                        OR ht.category LIKE '%expense%' OR ht.category LIKE '%Expense%' THEN 'OUTGOING'
                    WHEN ht.category = 'owner_payment' THEN 'OUTGOING'
                    ELSE 'OUTGOING'
                END as flow_direction,
                NOW() as rebuilt_at,
                ? as rebuild_batch_id
            FROM historical_transactions ht
            LEFT JOIN properties p ON ht.property_id = p.id
            LEFT JOIN invoices i ON ht.invoice_id = i.id
            LEFT JOIN invoices active_lease ON ht.property_id = active_lease.property_id
                AND ht.transaction_date >= active_lease.start_date
                AND (active_lease.end_date IS NULL OR ht.transaction_date <= active_lease.end_date)
                AND ht.invoice_id IS NULL
            WHERE ht.invoice_id IS NOT NULL
               OR active_lease.id IS NOT NULL
               OR (p.property_type = 'BLOCK' AND ht.property_id IS NOT NULL)
               OR ht.category = 'owner_payment'
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
                transaction_date, amount, net_to_owner_amount, commission_rate, commission_amount,
                description, category,
                invoice_id, property_id, customer_id,
                lease_reference, property_name,
                payprop_transaction_id, payprop_data_source,
                transaction_type, flow_direction,
                rebuilt_at, rebuild_batch_id
            )
            SELECT
                'PAYPROP' as source_system,
                'financial_transactions' as source_table,
                ft.id as source_record_id,
                ft.transaction_date,
                ft.amount,
                ft.net_to_owner_amount,
                ft.commission_rate,
                ft.commission_amount,
                ft.description,
                CASE
                    WHEN ft.description LIKE '%global_beneficiary%' THEN 'PROPERTY_ACCOUNT_ALLOCATION'
                    ELSE ft.category_name
                END as category,
                ft.invoice_id,
                CASE
                    WHEN ft.data_source = 'INCOMING_PAYMENT' THEN CAST(ft.property_id AS UNSIGNED)
                    ELSE p.id
                END as property_id,
                CASE
                    WHEN ft.data_source = 'INCOMING_PAYMENT' THEN CAST(ft.tenant_id AS UNSIGNED)
                    ELSE NULL
                END as customer_id,
                i.lease_reference,
                ft.property_name,
                ft.pay_prop_transaction_id as payprop_transaction_id,
                ft.data_source as payprop_data_source,
                ft.transaction_type as transaction_type,
                CASE
                    -- First check data_source (PayProp specific)
                    WHEN ft.data_source = 'INCOMING_PAYMENT' THEN 'INCOMING'
                    WHEN ft.data_source IN ('BATCH_PAYMENT', 'COMMISSION_PAYMENT', 'EXPENSE_PAYMENT') THEN 'OUTGOING'
                    -- Then check category_name (like historical logic)
                    WHEN ft.category_name LIKE '%rent%' OR ft.category_name LIKE '%Rent%' THEN 'INCOMING'
                    -- PayProp expense categories: Council, Disbursement, Contractor, Other (NOT Owner, NOT Commission)
                    WHEN ft.category_name IN ('Council', 'Disbursement', 'Contractor', 'Other', 'cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee')
                        OR ft.category_name LIKE '%expense%' OR ft.category_name LIKE '%Expense%' THEN 'OUTGOING'
                    ELSE 'OUTGOING'
                END as flow_direction,
                NOW() as rebuilt_at,
                ? as rebuild_batch_id
            FROM financial_transactions ft
            LEFT JOIN properties p ON ft.property_id = p.payprop_id
            LEFT JOIN invoices i ON ft.invoice_id = i.id
            WHERE (ft.invoice_id IS NOT NULL OR ft.data_source IN ('INCOMING_PAYMENT', 'EXPENSE_PAYMENT'))
              AND ft.data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV', 'ICDN_ACTUAL')
        """;

        return jdbcTemplate.update(sql, batchId);
    }

    /**
     * Migrate existing allocations from historical_transactions to unified_transactions
     * Links allocations to their corresponding unified_transaction record via source_record_id
     */
    private int migrateAllocationsToUnified() {
        String sql = """
            UPDATE transaction_batch_allocations tba
            SET unified_transaction_id = (
                SELECT ut.id
                FROM unified_transactions ut
                WHERE ut.source_table = 'historical_transactions'
                  AND ut.source_record_id = tba.transaction_id
                LIMIT 1
            )
            WHERE tba.unified_transaction_id IS NULL
              AND tba.transaction_id IS NOT NULL
        """;

        return jdbcTemplate.update(sql);
    }

    /**
     * Sync allocations from transaction_batch_allocations to unified_allocations
     * Maps the legacy allocation table to the new unified allocation structure
     */
    private int syncAllocationsToUnifiedAllocations(String batchId) {
        // Step 5a: Truncate unified_allocations
        log.info("  📋 Step 5a: Truncating unified_allocations...");
        jdbcTemplate.execute("TRUNCATE TABLE unified_allocations");

        // Step 5b: Insert from transaction_batch_allocations with proper mapping
        log.info("  📋 Step 5b: Inserting from transaction_batch_allocations...");

        String sql = """
            INSERT INTO unified_allocations (
                incoming_transaction_id,
                unified_transaction_id,
                historical_transaction_id,
                invoice_id,
                allocation_type,
                amount,
                category,
                description,
                property_id,
                property_name,
                beneficiary_type,
                beneficiary_id,
                beneficiary_name,
                payment_status,
                payment_batch_id,
                paid_date,
                source,
                source_record_id,
                created_at,
                updated_at,
                created_by
            )
            SELECT
                -- incoming_transaction_id: link to unified_incoming_transactions via unified_transactions match
                uit.id as incoming_transaction_id,
                tba.unified_transaction_id,
                tba.transaction_id as historical_transaction_id,
                -- invoice_id: link to specific lease for per-lease allocation tracking
                COALESCE(ht.invoice_id, ut.invoice_id) as invoice_id,
                -- allocation_type based on category
                -- IMPORTANT: Default to EXPENSE for unrecognized categories (safer than defaulting to OWNER)
                -- DISBURSEMENT = transfers to block property account (separate from regular EXPENSE)
                CASE
                    WHEN ht.category IN ('management', 'agency_fee', 'commission')
                         OR ht.category LIKE '%commission%' OR ht.category LIKE '%Commission%'
                    THEN 'COMMISSION'
                    WHEN ht.category IN ('rent', 'rental', 'income', 'payment', 'tenant_payment')
                         OR ht.category LIKE '%rent%' OR ht.category LIKE '%income%'
                    THEN 'OWNER'
                    WHEN ht.category IN ('disbursement', 'Disbursement')
                         OR ht.category LIKE '%disbursement%' OR ht.category LIKE '%Disbursement%'
                         OR ht.description LIKE '%BLOCK PROPERTY%'
                    THEN 'DISBURSEMENT'
                    WHEN ht.category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance',
                                         'other', 'Other', 'council_tax', 'insurance', 'repairs', 'legal',
                                         'service_charge', 'ground_rent', 'professional_fees',
                                         'contractor', 'supplier', 'vendor')
                         OR ht.category LIKE '%expense%' OR ht.category LIKE '%Expense%'
                    THEN 'EXPENSE'
                    ELSE 'EXPENSE'  -- Default to EXPENSE for safety (unrecognized = likely expense)
                END as allocation_type,
                ABS(tba.allocated_amount) as amount,
                ht.category,
                ht.description,
                tba.property_id,
                COALESCE(tba.property_name, p.property_name) as property_name,
                -- beneficiary_type: DISBURSEMENT goes to block property, others go to owner
                CASE
                    WHEN ht.category IN ('disbursement', 'Disbursement')
                         OR ht.category LIKE '%disbursement%' OR ht.description LIKE '%BLOCK PROPERTY%'
                    THEN 'BLOCK_PROPERTY'
                    ELSE 'OWNER'
                END as beneficiary_type,
                tba.beneficiary_id,
                -- beneficiary_name: For DISBURSEMENT, extract block name from description; otherwise use owner
                CASE
                    WHEN ht.description LIKE '%Beneficiary:%BLOCK PROPERTY%'
                    THEN TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(ht.description, 'Beneficiary: ', -1), ' (', 1))
                    WHEN ht.category IN ('disbursement', 'Disbursement') OR ht.category LIKE '%disbursement%'
                    THEN COALESCE(ht.beneficiary_name, ht.description)
                    ELSE COALESCE(tba.beneficiary_name, c.name)
                END as beneficiary_name,
                -- payment_status from PaymentBatch
                CASE
                    WHEN pb.status = 'PAID' THEN 'PAID'
                    WHEN pb.status IN ('PENDING', 'DRAFT') THEN 'PENDING'
                    ELSE 'PENDING'
                END as payment_status,
                tba.batch_reference as payment_batch_id,
                -- paid_date only if PAID
                CASE WHEN pb.status = 'PAID' THEN pb.payment_date ELSE NULL END as paid_date,
                'MANUAL' as source,
                tba.id as source_record_id,
                tba.created_at,
                NOW() as updated_at,
                tba.created_by
            FROM transaction_batch_allocations tba
            LEFT JOIN historical_transactions ht ON tba.transaction_id = ht.id
            LEFT JOIN unified_transactions ut ON tba.unified_transaction_id = ut.id
            LEFT JOIN unified_incoming_transactions uit
                ON ut.property_id = uit.property_id
                AND ut.transaction_date = uit.transaction_date
                AND ut.amount = uit.amount
            LEFT JOIN properties p ON tba.property_id = p.id
            LEFT JOIN customers c ON tba.beneficiary_id = c.customer_id
            LEFT JOIN payment_batches pb ON tba.batch_reference COLLATE utf8mb4_unicode_ci = pb.batch_id COLLATE utf8mb4_unicode_ci
        """;

        int historicalCount = jdbcTemplate.update(sql);
        log.info("  ✓ Inserted {} allocations from transaction_batch_allocations (historical)", historicalCount);

        // Step 5c: Insert from payprop_report_all_payments (PayProp data)
        log.info("  📋 Step 5c: Inserting from payprop_report_all_payments (PayProp)...");

        String paypropSql = """
            INSERT INTO unified_allocations (
                incoming_transaction_id,
                unified_transaction_id,
                historical_transaction_id,
                invoice_id,
                allocation_type,
                amount,
                category,
                description,
                property_id,
                property_name,
                beneficiary_type,
                beneficiary_id,
                beneficiary_name,
                payment_status,
                payment_batch_id,
                paid_date,
                source,
                source_record_id,
                payprop_payment_id,
                payprop_batch_id,
                created_at,
                updated_at
            )
            SELECT
                -- incoming_transaction_id: link via unified_incoming_transactions using peip date/amount
                uit.id as incoming_transaction_id,
                -- unified_transaction_id: link via unified_transactions using peip date/amount
                ut.id as unified_transaction_id,
                -- historical_transaction_id: not directly available for PayProp data
                NULL as historical_transaction_id,
                -- invoice_id: link to specific lease for per-lease allocation tracking
                -- Find active lease for property at the time of the transaction
                COALESCE(ut.invoice_id, active_lease.id) as invoice_id,
                -- allocation_type based on category_name and beneficiary_type
                -- NOTE: This query filters WHERE beneficiary_type = 'beneficiary' (owner payments only)
                -- So the default should be OWNER for unrecognized categories in this context
                -- DISBURSEMENT = transfers to block property account (separate from regular EXPENSE)
                CASE
                    WHEN prap.category_name IN ('Agency Fee', 'Commission', 'Management Fee')
                         OR prap.category_name LIKE '%commission%' OR prap.category_name LIKE '%Commission%'
                    THEN 'COMMISSION'
                    WHEN prap.category_name IN ('Disbursement')
                         OR prap.category_name LIKE '%disbursement%' OR prap.category_name LIKE '%Disbursement%'
                         OR prap.description LIKE '%BLOCK PROPERTY%'
                    THEN 'DISBURSEMENT'
                    WHEN prap.category_name LIKE '%expense%' OR prap.category_name LIKE '%Expense%'
                         OR prap.category_name IN ('Other', 'Council Tax', 'Insurance',
                                                   'Repairs', 'Legal', 'Service Charge', 'Ground Rent',
                                                   'Professional Fees', 'Utilities', 'Maintenance')
                    THEN 'EXPENSE'
                    ELSE 'OWNER'  -- Default to OWNER for beneficiary_type='beneficiary' (owner distributions)
                END as allocation_type,
                ABS(prap.amount) as amount,
                prap.category_name as category,
                prap.description,
                prop.id as property_id,
                prap.incoming_property_name as property_name,
                prap.beneficiary_type,
                cust.customer_id as beneficiary_id,
                prap.beneficiary_name,
                -- payment_status from payment_batch_status
                CASE
                    WHEN prap.payment_batch_status = 'processed' THEN 'PAID'
                    WHEN prap.payment_batch_status = 'pending' THEN 'PENDING'
                    ELSE 'PAID'
                END as payment_status,
                prap.payment_batch_id,
                prap.payment_batch_transfer_date as paid_date,
                'PAYPROP' as source,
                NULL as source_record_id,
                prap.payprop_id as payprop_payment_id,
                prap.payment_batch_id as payprop_batch_id,
                prap.imported_at as created_at,
                NOW() as updated_at
            FROM payprop_report_all_payments prap
            LEFT JOIN properties prop ON prap.incoming_property_payprop_id COLLATE utf8mb4_unicode_ci = prop.payprop_id COLLATE utf8mb4_unicode_ci
            LEFT JOIN customers cust ON prap.beneficiary_payprop_id COLLATE utf8mb4_unicode_ci = cust.payprop_entity_id COLLATE utf8mb4_unicode_ci
            -- Join to payprop_export_incoming_payments to get reconciliation date and amount
            LEFT JOIN payprop_export_incoming_payments peip
                ON prap.incoming_transaction_id COLLATE utf8mb4_unicode_ci = peip.payprop_id COLLATE utf8mb4_unicode_ci
            -- Link to unified_incoming_transactions using peip data
            LEFT JOIN unified_incoming_transactions uit
                ON prop.id = uit.property_id
                AND peip.reconciliation_date = uit.transaction_date
                AND peip.amount = uit.amount
            -- Link to unified_transactions using peip data
            LEFT JOIN unified_transactions ut
                ON prop.id = ut.property_id
                AND peip.reconciliation_date = ut.transaction_date
                AND peip.amount = ut.amount
            -- Find active lease for property at transaction date (fallback if ut.invoice_id is null)
            LEFT JOIN invoices active_lease
                ON prop.id = active_lease.property_id
                AND peip.reconciliation_date >= active_lease.start_date
                AND (active_lease.end_date IS NULL OR peip.reconciliation_date <= active_lease.end_date)
            WHERE prap.payment_batch_id IS NOT NULL
              AND prap.beneficiary_type = 'beneficiary'
        """;

        int paypropCount = jdbcTemplate.update(paypropSql);
        log.info("  ✓ Inserted {} allocations from payprop_report_all_payments (PayProp)", paypropCount);

        return historicalCount + paypropCount;
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
                transaction_type, flow_direction,
                rebuilt_at, rebuild_batch_id
            )
            SELECT
                'HISTORICAL', 'historical_transactions', ht.id,
                ht.transaction_date,
                CASE
                    WHEN ht.category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee')
                        OR ht.category LIKE '%expense%' OR ht.category LIKE '%Expense%'
                    THEN ABS(ht.amount)
                    ELSE ht.amount
                END,
                ht.description, ht.category,
                COALESCE(ht.invoice_id, active_lease.id), ht.property_id, ht.customer_id,
                COALESCE(i.lease_reference, active_lease.lease_reference), ht.lease_start_date, ht.lease_end_date,
                ht.rent_amount_at_transaction, p.property_name,
                CASE
                    WHEN ht.category LIKE '%rent%' OR ht.category LIKE '%Rent%' THEN 'rent_received'
                    WHEN ht.category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee')
                        OR ht.category LIKE '%expense%' OR ht.category LIKE '%Expense%' THEN 'expense'
                    WHEN ht.category = 'owner_payment' THEN 'payment_to_beneficiary'
                    ELSE 'other'
                END,
                CASE
                    WHEN ht.category LIKE '%rent%' OR ht.category LIKE '%Rent%' THEN 'INCOMING'
                    WHEN ht.category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee')
                        OR ht.category LIKE '%expense%' OR ht.category LIKE '%Expense%' THEN 'OUTGOING'
                    WHEN ht.category = 'owner_payment' THEN 'OUTGOING'
                    ELSE 'OUTGOING'
                END,
                NOW(), ?
            FROM historical_transactions ht
            LEFT JOIN properties p ON ht.property_id = p.id
            LEFT JOIN invoices i ON ht.invoice_id = i.id
            LEFT JOIN invoices active_lease ON ht.property_id = active_lease.property_id
                AND ht.transaction_date >= active_lease.start_date
                AND (active_lease.end_date IS NULL OR ht.transaction_date <= active_lease.end_date)
                AND ht.invoice_id IS NULL
            WHERE (ht.invoice_id IS NOT NULL
               OR active_lease.id IS NOT NULL
               OR (p.property_type = 'BLOCK' AND ht.property_id IS NOT NULL)
               OR ht.category = 'owner_payment')
              AND ht.updated_at > ?
        """;

        return jdbcTemplate.update(sql, batchId, since);
    }

    private int insertUpdatedFinancialTransactions(LocalDateTime since, String batchId) {
        String sql = """
            INSERT INTO unified_transactions (
                source_system, source_table, source_record_id,
                transaction_date, amount, description, category,
                invoice_id, property_id, customer_id,
                lease_reference, property_name,
                payprop_transaction_id, payprop_data_source,
                transaction_type, flow_direction,
                rebuilt_at, rebuild_batch_id
            )
            SELECT
                'PAYPROP', 'financial_transactions', ft.id,
                ft.transaction_date, ft.amount, ft.description, ft.category_name,
                ft.invoice_id,
                CASE
                    WHEN ft.data_source = 'INCOMING_PAYMENT' THEN CAST(ft.property_id AS UNSIGNED)
                    ELSE p.id
                END,
                CASE
                    WHEN ft.data_source = 'INCOMING_PAYMENT' THEN CAST(ft.tenant_id AS UNSIGNED)
                    ELSE NULL
                END,
                i.lease_reference, ft.property_name,
                ft.pay_prop_transaction_id, ft.data_source,
                ft.transaction_type,
                CASE
                    WHEN ft.data_source = 'INCOMING_PAYMENT' THEN 'INCOMING'
                    WHEN ft.data_source IN ('BATCH_PAYMENT', 'COMMISSION_PAYMENT', 'EXPENSE_PAYMENT') THEN 'OUTGOING'
                    -- PayProp expense categories
                    WHEN ft.category_name IN ('Council', 'Disbursement', 'Contractor', 'Other') THEN 'OUTGOING'
                    ELSE 'OUTGOING'
                END,
                NOW(), ?
            FROM financial_transactions ft
            LEFT JOIN properties p ON ft.property_id = p.payprop_id
            LEFT JOIN invoices i ON ft.invoice_id = i.id
            WHERE (ft.invoice_id IS NOT NULL OR ft.data_source IN ('INCOMING_PAYMENT', 'EXPENSE_PAYMENT'))
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

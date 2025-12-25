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
            // Step 1: Rebuild unified_incoming_transactions (optional - for allocation linking)
            log.info("📋 Step 1: Rebuilding unified_incoming_transactions...");
            try {
                int incomingCount = rebuildUnifiedIncomingTransactions();
                result.put("incomingTransactionsRebuilt", incomingCount);
                log.info("✅ Rebuilt {} incoming transactions with lease linkage", incomingCount);
            } catch (Exception e) {
                log.warn("⚠️ Step 1 failed (non-critical): {}. Continuing with main rebuild...", e.getMessage());
                result.put("incomingTransactionsRebuilt", "SKIPPED: " + e.getMessage());
            }

            // Step 2: Truncate unified_transactions (disable FK checks for safety)
            log.info("📋 Step 2: Truncating unified_transactions...");
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
            jdbcTemplate.execute("TRUNCATE TABLE unified_transactions");
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
            log.info("✅ Table truncated");

            // Step 3: Insert from historical_transactions
            log.info("📋 Step 3: Inserting from historical_transactions...");
            int historicalCount = insertFromHistoricalTransactions(batchId);
            result.put("historicalRecordsInserted", historicalCount);
            log.info("✅ Inserted {} records from historical_transactions", historicalCount);

            // Step 4: Insert from financial_transactions
            log.info("📋 Step 4: Inserting from financial_transactions...");
            int paypropCount = insertFromFinancialTransactions(batchId);
            result.put("paypropRecordsInserted", paypropCount);
            log.info("✅ Inserted {} records from financial_transactions", paypropCount);

            // Step 5: Migrate allocations to unified layer (optional - for allocation tracking)
            log.info("📋 Step 5: Migrating allocations to unified_transactions...");
            try {
                int migratedAllocations = migrateAllocationsToUnified();
                result.put("migratedAllocations", migratedAllocations);
                log.info("✅ Migrated {} allocations to unified_transaction_id", migratedAllocations);
            } catch (Exception e) {
                log.warn("⚠️ Step 5 failed (non-critical): {}. Continuing...", e.getMessage());
                result.put("migratedAllocations", "SKIPPED: " + e.getMessage());
            }

            // Step 6: Sync allocations to unified_allocations table (optional)
            log.info("📋 Step 6: Syncing allocations to unified_allocations...");
            try {
                int syncedAllocations = syncAllocationsToUnifiedAllocations(batchId);
                result.put("syncedAllocations", syncedAllocations);
                log.info("✅ Synced {} allocations to unified_allocations", syncedAllocations);
            } catch (Exception e) {
                log.warn("⚠️ Step 6 failed (non-critical): {}. Continuing...", e.getMessage());
                result.put("syncedAllocations", "SKIPPED: " + e.getMessage());
            }

            // Step 7: Verify rebuild
            log.info("📋 Step 7: Verifying rebuild...");
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
                -- PROPERTY_ACCOUNT_ALLOCATION: Store as POSITIVE (actual deposit amount)
                -- These are deposits INTO the property account from Prestvale
                ft.amount,
                -- Calculate net_to_owner_amount for BATCH_PAYMENT expenses when NULL
                CASE
                    WHEN ft.net_to_owner_amount IS NOT NULL THEN ft.net_to_owner_amount
                    -- BLOCK PROPERTY: Income stays in block account, not owed to owner
                    WHEN (p.is_block_property = 1 OR p.property_type = 'BLOCK')
                        AND ft.data_source = 'INCOMING_PAYMENT'
                    THEN 0
                    -- BATCH_PAYMENT to beneficiary/agency are expenses (negative impact on owner)
                    WHEN ft.data_source = 'BATCH_PAYMENT'
                        AND ft.transaction_type IN ('payment_to_beneficiary', 'payment_to_agency')
                        AND ft.category_name NOT IN ('Owner', 'owner_payment')
                    THEN -ABS(ft.amount)
                    -- INCOMING_PAYMENT is income (positive impact on owner after commission)
                    WHEN ft.data_source = 'INCOMING_PAYMENT' THEN ft.amount * 0.85
                    ELSE ft.net_to_owner_amount
                END as net_to_owner_amount,
                ft.commission_rate,
                ft.commission_amount,
                ft.description,
                CASE
                    WHEN ft.description LIKE '%global_beneficiary%' THEN 'PROPERTY_ACCOUNT_ALLOCATION'
                    -- BLOCK PROPERTY: Real tenant income is block fund contribution
                    WHEN (p.is_block_property = 1 OR p.property_type = 'BLOCK')
                        AND ft.data_source = 'INCOMING_PAYMENT'
                    THEN 'block_fund_contribution'
                    -- Categorize utility payments properly
                    WHEN ft.description LIKE '%EON%' OR ft.description LIKE '%Scottish Power%'
                        OR ft.description LIKE '%Utility%' OR ft.description LIKE '%Electric%'
                        OR ft.description LIKE '%Gas%' OR ft.description LIKE '%Water%'
                    THEN 'utilities'
                    ELSE ft.category_name
                END as category,
                ft.invoice_id,
                CASE
                    -- PROPERTY_ACCOUNT_ALLOCATION: Link to the BLOCK PROPERTY (not the flat)
                    -- This allows the block fund to track its balance correctly
                    WHEN ft.description LIKE '%global_beneficiary%'
                        AND b.block_property_id IS NOT NULL
                    THEN b.block_property_id
                    WHEN ft.data_source = 'INCOMING_PAYMENT' THEN CAST(ft.property_id AS UNSIGNED)
                    ELSE p.id
                END as property_id,
                CASE
                    WHEN ft.data_source = 'INCOMING_PAYMENT' THEN CAST(ft.tenant_id AS UNSIGNED)
                    ELSE NULL
                END as customer_id,
                i.lease_reference,
                -- PROPERTY_ACCOUNT_ALLOCATION: Use block property name so it shows in PROPERTY_ACCOUNT sheet
                CASE
                    WHEN ft.description LIKE '%global_beneficiary%'
                        AND bp.property_name IS NOT NULL
                    THEN bp.property_name
                    ELSE ft.property_name
                END as property_name,
                ft.pay_prop_transaction_id as payprop_transaction_id,
                ft.data_source as payprop_data_source,
                ft.transaction_type as transaction_type,
                CASE
                    -- PROPERTY_ACCOUNT_ALLOCATION: Make INCOMING so it appears in RENT_RECEIVED
                    WHEN ft.description LIKE '%global_beneficiary%'
                    THEN 'INCOMING'
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
            -- Join to blocks and block property for PROPERTY_ACCOUNT_ALLOCATION linkage
            LEFT JOIN blocks b ON p.block_id = b.id
            LEFT JOIN properties bp ON b.block_property_id = bp.id
            WHERE (ft.invoice_id IS NOT NULL OR ft.data_source IN ('INCOMING_PAYMENT', 'EXPENSE_PAYMENT'))
              AND ft.data_source NOT IN ('HISTORICAL_IMPORT', 'HISTORICAL_CSV', 'ICDN_ACTUAL')
        """;

        return jdbcTemplate.update(sql, batchId);
    }

    /**
     * Rebuild unified_incoming_transactions table with proper lease linkage.
     * This table tracks all incoming payments (rent received) and links them to leases.
     *
     * The lease_id is populated by joining to the source tables:
     * - HISTORICAL: historical_transactions.invoice_id
     * - PAYPROP: financial_transactions.invoice_id (via pay_prop_transaction_id)
     *
     * @return Number of records rebuilt
     */
    private int rebuildUnifiedIncomingTransactions() {
        // Step 0: Ensure table exists (may not have been created by Hibernate DDL)
        log.info("  📋 Ensuring unified_incoming_transactions table exists...");
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS unified_incoming_transactions (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    source VARCHAR(20) NOT NULL,
                    source_id VARCHAR(100),
                    transaction_date DATE NOT NULL,
                    amount DECIMAL(12,2) NOT NULL,
                    description VARCHAR(500),
                    property_id BIGINT NOT NULL,
                    tenant_id BIGINT,
                    tenant_name VARCHAR(255),
                    lease_id BIGINT,
                    lease_reference VARCHAR(100),
                    payprop_transaction_id VARCHAR(100),
                    reconciliation_date DATE,
                    bank_statement_date DATE,
                    created_at DATETIME,
                    updated_at DATETIME,
                    INDEX idx_property_id (property_id),
                    INDEX idx_lease_id (lease_id),
                    INDEX idx_transaction_date (transaction_date),
                    INDEX idx_payprop_transaction_id (payprop_transaction_id)
                )
            """);
            log.info("  ✓ Table unified_incoming_transactions verified/created");
        } catch (Exception e) {
            log.warn("  ⚠️ Could not create unified_incoming_transactions table: {}", e.getMessage());
        }

        // Step 1: Truncate the table (disable FK checks due to unified_allocations reference)
        log.info("  📋 Truncating unified_incoming_transactions...");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("TRUNCATE TABLE unified_incoming_transactions");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        // Step 2: Insert from historical_transactions (rent payments)
        log.info("  📋 Inserting from historical_transactions...");
        String historicalSql = """
            INSERT INTO unified_incoming_transactions (
                source, source_id, transaction_date, amount, description,
                property_id, tenant_id, tenant_name, lease_id, lease_reference,
                created_at, updated_at
            )
            SELECT
                'HISTORICAL' as source,
                CAST(ht.id AS CHAR) as source_id,
                ht.transaction_date,
                ht.amount,
                ht.description,
                ht.property_id,
                ht.tenant_id,
                c.name as tenant_name,
                ht.invoice_id as lease_id,
                i.lease_reference,
                COALESCE(ht.created_at, NOW()) as created_at,
                NOW() as updated_at
            FROM historical_transactions ht
            LEFT JOIN invoices i ON ht.invoice_id = i.id
            LEFT JOIN customers c ON ht.tenant_id = c.customer_id
            WHERE (ht.category LIKE '%rent%' OR ht.category LIKE '%Rent%' OR ht.category = 'income')
              AND ht.amount > 0
        """;
        int historicalCount = jdbcTemplate.update(historicalSql);
        log.info("  ✓ Inserted {} historical incoming transactions", historicalCount);

        // Step 3: Insert from financial_transactions (PayProp incoming payments)
        log.info("  📋 Inserting from financial_transactions (PayProp)...");
        String paypropSql = """
            INSERT INTO unified_incoming_transactions (
                source, source_id, transaction_date, amount, description,
                property_id, tenant_id, tenant_name, lease_id, lease_reference,
                payprop_transaction_id, reconciliation_date,
                created_at, updated_at
            )
            SELECT
                'PAYPROP' as source,
                ft.pay_prop_transaction_id as source_id,
                ft.transaction_date,
                ft.amount,
                ft.description,
                CAST(ft.property_id AS UNSIGNED) as property_id,
                CAST(ft.tenant_id AS UNSIGNED) as tenant_id,
                ft.tenant_name,
                ft.invoice_id as lease_id,
                i.lease_reference,
                ft.pay_prop_transaction_id as payprop_transaction_id,
                ft.transaction_date as reconciliation_date,
                COALESCE(ft.created_at, NOW()) as created_at,
                NOW() as updated_at
            FROM financial_transactions ft
            LEFT JOIN invoices i ON ft.invoice_id = i.id
            WHERE ft.data_source = 'INCOMING_PAYMENT'
              AND ft.amount > 0
        """;
        int paypropCount = jdbcTemplate.update(paypropSql);
        log.info("  ✓ Inserted {} PayProp incoming transactions", paypropCount);

        // Step 4: Backfill any remaining NULL lease_ids using date-range matching
        log.info("  📋 Backfilling any remaining NULL lease_ids...");
        String backfillSql = """
            UPDATE unified_incoming_transactions uit
            JOIN invoices i ON uit.property_id = i.property_id
                AND uit.transaction_date >= i.start_date
                AND (i.end_date IS NULL OR uit.transaction_date <= i.end_date)
            SET uit.lease_id = i.id,
                uit.lease_reference = i.lease_reference
            WHERE uit.lease_id IS NULL
        """;
        int backfilledCount = jdbcTemplate.update(backfillSql);
        if (backfilledCount > 0) {
            log.info("  ✓ Backfilled lease_id for {} records using date-range matching", backfilledCount);
        }

        return historicalCount + paypropCount;
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
        // Step 5a-0: Ensure table exists
        log.info("  📋 Ensuring unified_allocations table exists...");
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS unified_allocations (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    incoming_transaction_id BIGINT,
                    unified_transaction_id BIGINT,
                    historical_transaction_id BIGINT,
                    invoice_id BIGINT,
                    allocation_type VARCHAR(50),
                    amount DECIMAL(12,2),
                    category VARCHAR(100),
                    description TEXT,
                    property_id BIGINT,
                    property_name VARCHAR(255),
                    beneficiary_type VARCHAR(50),
                    beneficiary_id BIGINT,
                    beneficiary_name VARCHAR(255),
                    payment_status VARCHAR(20),
                    payment_batch_id VARCHAR(100),
                    paid_date DATE,
                    source VARCHAR(20),
                    source_record_id BIGINT,
                    payprop_payment_id VARCHAR(100),
                    payprop_batch_id VARCHAR(100),
                    created_at DATETIME,
                    updated_at DATETIME,
                    created_by BIGINT,
                    INDEX idx_incoming_transaction_id (incoming_transaction_id),
                    INDEX idx_unified_transaction_id (unified_transaction_id),
                    INDEX idx_invoice_id (invoice_id),
                    INDEX idx_property_id (property_id),
                    INDEX idx_payment_status (payment_status)
                )
            """);
            log.info("  ✓ Table unified_allocations verified/created");
        } catch (Exception e) {
            log.warn("  ⚠️ Could not create unified_allocations table: {}", e.getMessage());
        }

        // Step 5a: Truncate unified_allocations (disable FK checks for safety)
        log.info("  📋 Step 5a: Truncating unified_allocations...");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("TRUNCATE TABLE unified_allocations");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

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
                gross_amount,
                commission_rate,
                commission_amount,
                net_to_owner_amount,
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
                -- gross_amount: For OWNER allocations (rent), use the transaction's gross amount
                -- Calculate proportionally if this is a partial allocation
                CASE
                    WHEN ht.category IN ('rent', 'rental', 'income', 'payment', 'tenant_payment')
                         OR ht.category LIKE '%rent%' OR ht.category LIKE '%income%'
                    THEN CASE
                        -- If full allocation (allocated_amount >= net_to_owner), use full gross
                        WHEN ABS(tba.allocated_amount) >= ABS(COALESCE(ht.net_to_owner_amount, ht.amount))
                        THEN ABS(ht.amount)
                        -- Partial allocation: calculate proportional gross
                        WHEN ht.net_to_owner_amount IS NOT NULL AND ht.net_to_owner_amount != 0
                        THEN ROUND(ABS(ht.amount) * (ABS(tba.allocated_amount) / ABS(ht.net_to_owner_amount)), 2)
                        ELSE ABS(ht.amount)
                    END
                    ELSE NULL
                END as gross_amount,
                -- commission_rate: Use the transaction's commission rate for OWNER allocations
                CASE
                    WHEN ht.category IN ('rent', 'rental', 'income', 'payment', 'tenant_payment')
                         OR ht.category LIKE '%rent%' OR ht.category LIKE '%income%'
                    THEN ht.commission_rate
                    ELSE NULL
                END as commission_rate,
                -- commission_amount: Calculate proportionally for OWNER allocations
                CASE
                    WHEN ht.category IN ('rent', 'rental', 'income', 'payment', 'tenant_payment')
                         OR ht.category LIKE '%rent%' OR ht.category LIKE '%income%'
                    THEN CASE
                        -- If full allocation, use full commission
                        WHEN ABS(tba.allocated_amount) >= ABS(COALESCE(ht.net_to_owner_amount, ht.amount))
                        THEN ABS(ht.commission_amount)
                        -- Partial allocation: calculate proportional commission
                        WHEN ht.net_to_owner_amount IS NOT NULL AND ht.net_to_owner_amount != 0
                             AND ht.commission_amount IS NOT NULL
                        THEN ROUND(ABS(ht.commission_amount) * (ABS(tba.allocated_amount) / ABS(ht.net_to_owner_amount)), 2)
                        ELSE NULL
                    END
                    ELSE NULL
                END as commission_amount,
                -- net_to_owner_amount: For OWNER allocations, this is the allocated_amount itself
                CASE
                    WHEN ht.category IN ('rent', 'rental', 'income', 'payment', 'tenant_payment')
                         OR ht.category LIKE '%rent%' OR ht.category LIKE '%income%'
                    THEN ABS(tba.allocated_amount)
                    ELSE NULL
                END as net_to_owner_amount,
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
                -- beneficiary_id: Use tba.beneficiary_id if set, otherwise lookup OWNER from customer_property_assignments
                COALESCE(tba.beneficiary_id, owner_assign.customer_id) as beneficiary_id,
                -- beneficiary_name: For DISBURSEMENT, extract block name from description; otherwise use owner
                CASE
                    WHEN ht.description LIKE '%Beneficiary:%BLOCK PROPERTY%'
                    THEN TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(ht.description, 'Beneficiary: ', -1), ' (', 1))
                    WHEN ht.category IN ('disbursement', 'Disbursement') OR ht.category LIKE '%disbursement%'
                    THEN COALESCE(ht.beneficiary_name, ht.description)
                    ELSE COALESCE(tba.beneficiary_name, c.name, owner_cust.name)
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
            -- Lookup OWNER from customer_property_assignments if beneficiary_id is NULL
            LEFT JOIN customer_property_assignments owner_assign
                ON owner_assign.property_id = tba.property_id
                AND owner_assign.assignment_type = 'OWNER'
            LEFT JOIN customers owner_cust ON owner_assign.customer_id = owner_cust.customer_id
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
                gross_amount,
                commission_rate,
                commission_amount,
                net_to_owner_amount,
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
                -- Use unified_transaction's invoice_id, or find one active lease via subquery (avoids cartesian product)
                COALESCE(ut.invoice_id, (
                    SELECT inv.id FROM invoices inv
                    WHERE inv.property_id = prop.id
                      AND peip.reconciliation_date >= inv.start_date
                      AND (inv.end_date IS NULL OR peip.reconciliation_date <= inv.end_date)
                    ORDER BY inv.start_date DESC
                    LIMIT 1
                )) as invoice_id,
                -- allocation_type based on category_name and beneficiary_type
                -- Now includes both 'beneficiary' (owner payments) and 'agency' (commission/reimbursements)
                -- DISBURSEMENT = transfers to block property account (separate from regular EXPENSE)
                CASE
                    -- Agency commission only (beneficiary_type='agency' AND category='Commission')
                    WHEN prap.beneficiary_type = 'agency' AND prap.category_name = 'Commission'
                    THEN 'COMMISSION'
                    -- Agency reimbursements (Council, Contractor, Other) are EXPENSE
                    WHEN prap.beneficiary_type = 'agency'
                    THEN 'EXPENSE'
                    -- Regular commission categories for non-agency (legacy handling)
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
                -- gross_amount: For OWNER allocations, use the incoming_transaction_amount (full rent)
                -- For other allocation types (COMMISSION, EXPENSE, DISBURSEMENT), set to NULL
                CASE
                    WHEN prap.category_name = 'Owner' AND prap.beneficiary_type = 'beneficiary'
                    THEN ABS(prap.incoming_transaction_amount)
                    ELSE NULL
                END as gross_amount,
                -- commission_rate: Calculate from gross and commission for OWNER allocations
                -- Formula: (commission / gross) * 100
                CASE
                    WHEN prap.category_name = 'Owner' AND prap.beneficiary_type = 'beneficiary'
                         AND prap.incoming_transaction_amount IS NOT NULL
                         AND prap.incoming_transaction_amount > 0
                    THEN ROUND(
                        (COALESCE((
                            SELECT ABS(comm.amount)
                            FROM payprop_report_all_payments comm
                            WHERE comm.incoming_transaction_id = prap.incoming_transaction_id
                              AND comm.category_name = 'Commission'
                              AND comm.beneficiary_type = 'agency'
                            LIMIT 1
                        ), 0) / ABS(prap.incoming_transaction_amount)) * 100, 2)
                    ELSE NULL
                END as commission_rate,
                -- commission_amount: For OWNER allocations, get the commission from the same incoming transaction
                CASE
                    WHEN prap.category_name = 'Owner' AND prap.beneficiary_type = 'beneficiary'
                    THEN (
                        SELECT ABS(comm.amount)
                        FROM payprop_report_all_payments comm
                        WHERE comm.incoming_transaction_id = prap.incoming_transaction_id
                          AND comm.category_name = 'Commission'
                          AND comm.beneficiary_type = 'agency'
                        LIMIT 1
                    )
                    ELSE NULL
                END as commission_amount,
                -- net_to_owner_amount: For OWNER allocations, this is the allocation amount itself
                CASE
                    WHEN prap.category_name = 'Owner' AND prap.beneficiary_type = 'beneficiary'
                    THEN ABS(prap.amount)
                    ELSE NULL
                END as net_to_owner_amount,
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
            -- Link to unified_incoming_transactions using payprop_id (NOT property/date/amount to avoid cartesian product)
            LEFT JOIN unified_incoming_transactions uit
                ON uit.payprop_transaction_id COLLATE utf8mb4_unicode_ci = peip.payprop_id COLLATE utf8mb4_unicode_ci
            -- Link to unified_transactions using payprop_transaction_id (NOT property/date/amount to avoid cartesian product)
            LEFT JOIN unified_transactions ut
                ON ut.payprop_transaction_id COLLATE utf8mb4_unicode_ci = peip.payprop_id COLLATE utf8mb4_unicode_ci
            -- NOTE: Removed active_lease JOIN - was causing cartesian product (duplicates) when properties have multiple leases
            -- invoice_id is now handled via subquery with LIMIT 1 in the SELECT clause
            WHERE prap.payment_batch_id IS NOT NULL
              AND prap.beneficiary_type IN ('beneficiary', 'agency')
        """;

        int paypropCount = jdbcTemplate.update(paypropSql);
        log.info("  ✓ Inserted {} allocations from payprop_report_all_payments (PayProp)", paypropCount);

        // NOTE: Step 5d REMOVED - was creating synthetic negative OWNER allocations for block expenses
        // This caused DUPLICATION because:
        // 1. EXPENSE allocations already track the expenses correctly
        // 2. The negative OWNER allocations were double-counting the same expenses
        // Block property expenses are shown via EXPENSE allocations - no synthetic OWNER allocation needed

        // Step 5e: Create £0 OWNER allocations for incoming transactions with no OWNER allocation
        // When net_to_owner = 0 (expenses + commission consume full rent), PayProp doesn't create an Owner record.
        // We need to create a synthetic £0 OWNER allocation so the rent income appears in reconciliation.
        //
        // IMPORTANT: Block properties are EXCLUDED - they have funding transactions, not rent income.
        // Block property income/expense tracking is handled separately.
        log.info("  📋 Step 5e: Creating £0 OWNER allocations for fully-consumed rent payments...");
        String zeroOwnerSql = """
            INSERT INTO unified_allocations (
                incoming_transaction_id,
                unified_transaction_id,
                historical_transaction_id,
                invoice_id,
                allocation_type,
                amount,
                gross_amount,
                commission_rate,
                commission_amount,
                net_to_owner_amount,
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
                ua_existing.incoming_transaction_id,
                ua_existing.unified_transaction_id,
                NULL as historical_transaction_id,
                ua_existing.invoice_id,
                'OWNER' as allocation_type,
                0.00 as amount,  -- Net to owner is £0
                uit.amount as gross_amount,  -- Full rent amount from incoming transaction
                -- Calculate commission rate from existing COMMISSION allocation
                CASE
                    WHEN uit.amount > 0 THEN ROUND((COALESCE(comm_alloc.amount, 0) / uit.amount) * 100, 2)
                    ELSE NULL
                END as commission_rate,
                COALESCE(comm_alloc.amount, 0) as commission_amount,
                0.00 as net_to_owner_amount,
                'Owner' as category,
                CONCAT('Landlord Rental Payment - ', uit.description) as description,
                ua_existing.property_id,
                ua_existing.property_name,
                'OWNER' as beneficiary_type,
                owner_assign.customer_id as beneficiary_id,
                owner_cust.name as beneficiary_name,
                ua_existing.payment_status,
                ua_existing.payment_batch_id,
                ua_existing.paid_date,
                'PAYPROP' as source,
                NULL as source_record_id,
                CONCAT('SYNTHETIC-', ua_existing.incoming_transaction_id) as payprop_payment_id,
                ua_existing.payprop_batch_id,
                NOW() as created_at,
                NOW() as updated_at
            FROM (
                -- Find incoming transactions that have allocations but NO OWNER allocation
                -- Use GROUP BY to ensure only ONE synthetic OWNER per incoming transaction
                -- (prevents duplicates when same incoming txn has allocations in multiple batches)
                SELECT
                    ua.incoming_transaction_id,
                    MIN(ua.unified_transaction_id) as unified_transaction_id,
                    MIN(ua.invoice_id) as invoice_id,
                    MIN(ua.property_id) as property_id,
                    MIN(ua.property_name) as property_name,
                    MIN(ua.payment_status) as payment_status,
                    MIN(ua.payment_batch_id) as payment_batch_id,
                    MIN(ua.paid_date) as paid_date,
                    MIN(ua.payprop_batch_id) as payprop_batch_id
                FROM unified_allocations ua
                -- Join to properties early to filter out block properties
                JOIN properties prop_filter ON ua.property_id = prop_filter.id
                WHERE ua.incoming_transaction_id IS NOT NULL
                  AND ua.source = 'PAYPROP'
                  AND ua.allocation_type IN ('EXPENSE', 'COMMISSION', 'DISBURSEMENT')
                  -- Exclude block properties - they don't have rent income, only funding transactions
                  AND COALESCE(prop_filter.is_block_property, 0) = 0
                  AND NOT EXISTS (
                      SELECT 1 FROM unified_allocations owner_ua
                      WHERE owner_ua.incoming_transaction_id = ua.incoming_transaction_id
                        AND owner_ua.allocation_type = 'OWNER'
                  )
                GROUP BY ua.incoming_transaction_id
            ) ua_existing
            JOIN unified_incoming_transactions uit ON ua_existing.incoming_transaction_id = uit.id
            JOIN properties prop ON ua_existing.property_id = prop.id
            -- Get commission amount for this incoming transaction
            LEFT JOIN (
                SELECT incoming_transaction_id, SUM(amount) as amount
                FROM unified_allocations
                WHERE allocation_type = 'COMMISSION'
                GROUP BY incoming_transaction_id
            ) comm_alloc ON comm_alloc.incoming_transaction_id = ua_existing.incoming_transaction_id
            -- Lookup OWNER from customer_property_assignments
            LEFT JOIN customer_property_assignments owner_assign
                ON owner_assign.property_id = ua_existing.property_id
                AND owner_assign.assignment_type = 'OWNER'
            LEFT JOIN customers owner_cust ON owner_assign.customer_id = owner_cust.customer_id
        """;

        int zeroOwnerCount = jdbcTemplate.update(zeroOwnerSql);
        log.info("  ✓ Created {} synthetic £0 OWNER allocations for fully-consumed rent payments", zeroOwnerCount);

        return historicalCount + paypropCount + zeroOwnerCount;
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

    // ===== BENEFICIARY LINKAGE FIX =====

    /**
     * Fix beneficiary linkage for unified_allocations and payment_batches.
     *
     * This method should be called after a rebuild if beneficiary_id values are NULL.
     * It attempts to link allocations to customers through multiple paths:
     * 1. Via payprop_report_all_payments.beneficiary_payprop_id -> customers.payprop_entity_id
     * 2. Via property owner (properties.property_owner_id)
     * 3. Via payment_batches.beneficiary_id
     *
     * @return Map containing counts of updates made
     */
    @Transactional
    public Map<String, Object> fixBeneficiaryLinkage() {
        log.info("🔗 Starting beneficiary linkage fix...");
        Map<String, Object> result = new HashMap<>();

        // Step 1: Update unified_allocations via payprop_report_all_payments
        String updateViaPrapSql = """
            UPDATE unified_allocations ua
            JOIN payprop_report_all_payments prap
                ON ua.payprop_payment_id COLLATE utf8mb4_unicode_ci = prap.payprop_id COLLATE utf8mb4_unicode_ci
            JOIN customers c
                ON prap.beneficiary_payprop_id COLLATE utf8mb4_unicode_ci = c.payprop_entity_id COLLATE utf8mb4_unicode_ci
            SET ua.beneficiary_id = c.customer_id,
                ua.beneficiary_name = c.name,
                ua.updated_at = NOW()
            WHERE ua.beneficiary_id IS NULL
              AND ua.source = 'PAYPROP'
            """;

        int updatedViaPrap = jdbcTemplate.update(updateViaPrapSql);
        log.info("  ✓ Updated {} allocations via PayProp beneficiary link", updatedViaPrap);
        result.put("allocationsUpdatedViaPrap", updatedViaPrap);

        // Step 2: Update unified_allocations via property owner
        String updateViaPropertySql = """
            UPDATE unified_allocations ua
            JOIN properties p ON ua.property_id = p.id
            JOIN customers c ON p.property_owner_id = c.customer_id
            SET ua.beneficiary_id = c.customer_id,
                ua.beneficiary_name = c.name,
                ua.updated_at = NOW()
            WHERE ua.beneficiary_id IS NULL
              AND ua.allocation_type = 'OWNER'
            """;

        int updatedViaProperty = jdbcTemplate.update(updateViaPropertySql);
        log.info("  ✓ Updated {} allocations via property owner", updatedViaProperty);
        result.put("allocationsUpdatedViaProperty", updatedViaProperty);

        // Step 2b: Update unified_allocations via historical_transactions.owner_id
        String updateViaHistOwnerSql = """
            UPDATE unified_allocations ua
            JOIN historical_transactions ht ON ua.historical_transaction_id = ht.id
            JOIN customers c ON ht.owner_id = c.customer_id
            SET ua.beneficiary_id = c.customer_id,
                ua.beneficiary_name = c.name,
                ua.updated_at = NOW()
            WHERE ua.beneficiary_id IS NULL
              AND ht.owner_id IS NOT NULL
            """;

        int updatedViaHistOwner = jdbcTemplate.update(updateViaHistOwnerSql);
        log.info("  ✓ Updated {} allocations via historical_transactions.owner_id", updatedViaHistOwner);
        result.put("allocationsUpdatedViaHistOwner", updatedViaHistOwner);

        // Step 3: Update unified_allocations via payment_batch
        String updateViaBatchSql = """
            UPDATE unified_allocations ua
            JOIN payment_batches pb
                ON ua.payment_batch_id COLLATE utf8mb4_unicode_ci = pb.batch_id COLLATE utf8mb4_unicode_ci
            SET ua.beneficiary_id = pb.beneficiary_id,
                ua.updated_at = NOW()
            WHERE ua.beneficiary_id IS NULL
              AND pb.beneficiary_id IS NOT NULL
            """;

        int updatedViaBatch = jdbcTemplate.update(updateViaBatchSql);
        log.info("  ✓ Updated {} allocations via payment_batch link", updatedViaBatch);
        result.put("allocationsUpdatedViaBatch", updatedViaBatch);

        // Step 4: Update payment_batches.beneficiary_id from payprop_report_all_payments
        String updateBatchesSql = """
            UPDATE payment_batches pb
            JOIN (
                SELECT DISTINCT
                    prap.payment_batch_id,
                    c.customer_id
                FROM payprop_report_all_payments prap
                JOIN customers c
                    ON prap.beneficiary_payprop_id COLLATE utf8mb4_unicode_ci = c.payprop_entity_id COLLATE utf8mb4_unicode_ci
                WHERE prap.beneficiary_type = 'beneficiary'
                  AND prap.payment_batch_id IS NOT NULL
            ) matched
                ON pb.batch_id COLLATE utf8mb4_unicode_ci = matched.payment_batch_id COLLATE utf8mb4_unicode_ci
            SET pb.beneficiary_id = matched.customer_id,
                pb.updated_at = NOW()
            WHERE pb.beneficiary_id IS NULL
            """;

        int batchesUpdated = jdbcTemplate.update(updateBatchesSql);
        log.info("  ✓ Updated {} payment_batches with beneficiary_id", batchesUpdated);
        result.put("batchesUpdated", batchesUpdated);

        // Get remaining counts
        String remainingAllocSql = "SELECT COUNT(*) FROM unified_allocations WHERE beneficiary_id IS NULL";
        Integer remainingAllocs = jdbcTemplate.queryForObject(remainingAllocSql, Integer.class);
        result.put("remainingAllocationsWithoutBeneficiary", remainingAllocs);

        String remainingBatchSql = "SELECT COUNT(*) FROM payment_batches WHERE beneficiary_id IS NULL";
        Integer remainingBatches = jdbcTemplate.queryForObject(remainingBatchSql, Integer.class);
        result.put("remainingBatchesWithoutBeneficiary", remainingBatches);

        int totalUpdated = updatedViaPrap + updatedViaProperty + updatedViaHistOwner + updatedViaBatch + batchesUpdated;
        result.put("totalUpdated", totalUpdated);

        log.info("✅ Beneficiary linkage fix complete: {} total updates, {} allocations and {} batches still without beneficiary",
                totalUpdated, remainingAllocs, remainingBatches);

        return result;
    }

    /**
     * Link customers to PayProp beneficiaries by matching names.
     *
     * This updates customer.payprop_entity_id for customers that exist but aren't linked to PayProp.
     * Should be run before fixBeneficiaryLinkage() if customers were created before PayProp sync.
     *
     * @return Map containing counts of links made
     */
    @Transactional
    public Map<String, Object> linkCustomersToPayPropBeneficiaries() {
        log.info("🔗 Linking customers to PayProp beneficiaries by name...");
        Map<String, Object> result = new HashMap<>();

        // Link customers by exact name match
        String linkByNameSql = """
            UPDATE customers c
            JOIN (
                SELECT DISTINCT
                    prap.beneficiary_payprop_id,
                    prap.beneficiary_name
                FROM payprop_report_all_payments prap
                LEFT JOIN customers c_existing
                    ON prap.beneficiary_payprop_id COLLATE utf8mb4_unicode_ci = c_existing.payprop_entity_id COLLATE utf8mb4_unicode_ci
                WHERE prap.beneficiary_type = 'beneficiary'
                  AND prap.beneficiary_payprop_id IS NOT NULL
                  AND c_existing.customer_id IS NULL
            ) unlinked ON LOWER(TRIM(c.name)) = LOWER(TRIM(unlinked.beneficiary_name))
            SET c.payprop_entity_id = unlinked.beneficiary_payprop_id,
                c.payprop_synced = 1,
                c.payprop_last_sync = NOW()
            WHERE c.payprop_entity_id IS NULL OR c.payprop_entity_id = ''
            """;

        int linkedByName = jdbcTemplate.update(linkByNameSql);
        log.info("  ✓ Linked {} customers by exact name match", linkedByName);
        result.put("linkedByExactName", linkedByName);

        // Get remaining unlinked beneficiaries count
        String remainingSql = """
            SELECT COUNT(DISTINCT prap.beneficiary_payprop_id)
            FROM payprop_report_all_payments prap
            LEFT JOIN customers c
                ON prap.beneficiary_payprop_id COLLATE utf8mb4_unicode_ci = c.payprop_entity_id COLLATE utf8mb4_unicode_ci
            WHERE prap.beneficiary_type = 'beneficiary'
              AND prap.beneficiary_payprop_id IS NOT NULL
              AND c.customer_id IS NULL
            """;

        Integer remaining = jdbcTemplate.queryForObject(remainingSql, Integer.class);
        result.put("remainingUnlinkedBeneficiaries", remaining);

        log.info("✅ Customer linking complete: {} linked, {} still unlinked", linkedByName, remaining);

        return result;
    }
}

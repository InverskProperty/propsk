package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.UnifiedAllocation;
import site.easy.to.build.crm.entity.UnifiedAllocation.AllocationType;
import site.easy.to.build.crm.entity.UnifiedAllocation.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for UnifiedAllocation entity
 */
@Repository
public interface UnifiedAllocationRepository extends JpaRepository<UnifiedAllocation, Long> {

    // ===== BASIC FINDERS =====

    /**
     * Find allocations by payment status
     */
    List<UnifiedAllocation> findByPaymentStatus(PaymentStatus paymentStatus);

    /**
     * Find allocations by allocation type
     */
    List<UnifiedAllocation> findByAllocationType(AllocationType allocationType);

    /**
     * Find allocations by batch ID
     */
    List<UnifiedAllocation> findByPaymentBatchId(String paymentBatchId);

    /**
     * Find allocations for a property
     */
    List<UnifiedAllocation> findByPropertyId(Long propertyId);

    /**
     * Find allocations for a beneficiary
     */
    List<UnifiedAllocation> findByBeneficiaryId(Long beneficiaryId);

    /**
     * Find allocations for a beneficiary by payment status
     */
    List<UnifiedAllocation> findByBeneficiaryIdAndPaymentStatus(Long beneficiaryId, PaymentStatus paymentStatus);


    // ===== PENDING ALLOCATIONS =====

    /**
     * Find pending allocations (not yet batched)
     */
    @Query("SELECT ua FROM UnifiedAllocation ua WHERE ua.paymentStatus = 'PENDING'")
    List<UnifiedAllocation> findPendingAllocations();

    /**
     * Find pending owner allocations for a specific property
     */
    @Query("SELECT ua FROM UnifiedAllocation ua WHERE ua.propertyId = :propertyId " +
           "AND ua.allocationType = 'OWNER' AND ua.paymentStatus = 'PENDING'")
    List<UnifiedAllocation> findPendingOwnerAllocationsForProperty(@Param("propertyId") Long propertyId);

    /**
     * Find pending allocations by type
     */
    @Query("SELECT ua FROM UnifiedAllocation ua WHERE ua.allocationType = :type " +
           "AND ua.paymentStatus = 'PENDING'")
    List<UnifiedAllocation> findPendingAllocationsByType(@Param("type") AllocationType type);

    // ===== DATE RANGE QUERIES =====

    /**
     * Find allocations for a property within a date range
     */
    @Query("SELECT ua FROM UnifiedAllocation ua " +
           "INNER JOIN site.easy.to.build.crm.entity.UnifiedIncomingTransaction uit " +
           "ON ua.incomingTransactionId = uit.id " +
           "WHERE ua.propertyId = :propertyId " +
           "AND uit.transactionDate BETWEEN :startDate AND :endDate")
    List<UnifiedAllocation> findByPropertyIdAndDateRange(
        @Param("propertyId") Long propertyId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find owner allocation for a property in date range (for statement display)
     */
    @Query(value = "SELECT ua.* FROM unified_allocations ua " +
           "INNER JOIN unified_incoming_transactions uit ON ua.incoming_transaction_id = uit.id " +
           "WHERE ua.property_id = :propertyId " +
           "AND ua.allocation_type = 'OWNER' " +
           "AND uit.transaction_date BETWEEN :startDate AND :endDate " +
           "ORDER BY uit.transaction_date DESC LIMIT 1", nativeQuery = true)
    Optional<UnifiedAllocation> findLatestOwnerAllocationForPropertyInPeriod(
        @Param("propertyId") Long propertyId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    // ===== BATCH OPERATIONS =====

    /**
     * Update allocations with batch ID and status
     */
    @Modifying
    @Query("UPDATE UnifiedAllocation ua SET ua.paymentBatchId = :batchId, " +
           "ua.paymentStatus = :status, ua.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE ua.id IN :allocationIds")
    int assignToBatch(
        @Param("allocationIds") List<Long> allocationIds,
        @Param("batchId") String batchId,
        @Param("status") PaymentStatus status
    );

    /**
     * Mark allocations as paid
     */
    @Modifying
    @Query("UPDATE UnifiedAllocation ua SET ua.paymentStatus = 'PAID', " +
           "ua.paidDate = :paidDate, ua.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE ua.paymentBatchId = :batchId")
    int markBatchAsPaid(@Param("batchId") String batchId, @Param("paidDate") LocalDate paidDate);

    // ===== STATISTICS =====

    /**
     * Count pending allocations
     */
    @Query("SELECT COUNT(ua) FROM UnifiedAllocation ua WHERE ua.paymentStatus = 'PENDING'")
    long countPendingAllocations();

    /**
     * Sum pending amounts by allocation type
     */
    @Query("SELECT SUM(ua.amount) FROM UnifiedAllocation ua " +
           "WHERE ua.allocationType = :type AND ua.paymentStatus = 'PENDING'")
    BigDecimal sumPendingAmountByType(@Param("type") AllocationType type);

    /**
     * Sum pending owner amounts for a beneficiary
     */
    @Query("SELECT SUM(ua.amount) FROM UnifiedAllocation ua " +
           "WHERE ua.beneficiaryId = :beneficiaryId " +
           "AND ua.allocationType = 'OWNER' AND ua.paymentStatus = 'PENDING'")
    BigDecimal sumPendingOwnerAmountForBeneficiary(@Param("beneficiaryId") Long beneficiaryId);

    /**
     * Sum pending amounts for a property
     */
    @Query("SELECT SUM(ua.amount) FROM UnifiedAllocation ua " +
           "WHERE ua.propertyId = :propertyId AND ua.paymentStatus = 'PENDING'")
    BigDecimal sumPendingAmountByPropertyId(@Param("propertyId") Long propertyId);

    // ===== XLSX STATEMENT QUERIES =====

    /**
     * Find all OWNER allocations for a beneficiary (for income allocations sheet)
     */
    @Query("SELECT ua FROM UnifiedAllocation ua WHERE ua.beneficiaryId = :beneficiaryId AND ua.allocationType = 'OWNER' ORDER BY ua.createdAt DESC")
    List<UnifiedAllocation> findOwnerAllocationsByBeneficiaryId(@Param("beneficiaryId") Long beneficiaryId);

    /**
     * Find allocations for a beneficiary by type
     */
    List<UnifiedAllocation> findByBeneficiaryIdAndAllocationType(Long beneficiaryId, AllocationType allocationType);

    // ===== UNIFIED TRANSACTION QUERIES (consolidated from TransactionBatchAllocationRepository) =====

    /**
     * Find all allocations for a unified transaction
     */
    List<UnifiedAllocation> findByUnifiedTransactionId(Long unifiedTransactionId);

    /**
     * Find all allocations for a historical transaction
     */
    List<UnifiedAllocation> findByHistoricalTransactionId(Long historicalTransactionId);

    /**
     * Get total allocated amount for a unified transaction
     */
    @Query("SELECT COALESCE(SUM(ua.amount), 0) FROM UnifiedAllocation ua WHERE ua.unifiedTransactionId = :unifiedTransactionId")
    BigDecimal getTotalAllocatedForUnifiedTransaction(@Param("unifiedTransactionId") Long unifiedTransactionId);

    /**
     * Get total allocated amount for a historical transaction
     */
    @Query("SELECT COALESCE(SUM(ua.amount), 0) FROM UnifiedAllocation ua WHERE ua.historicalTransactionId = :historicalTransactionId")
    BigDecimal getTotalAllocatedForHistoricalTransaction(@Param("historicalTransactionId") Long historicalTransactionId);

    /**
     * Check if a unified transaction has any allocations
     */
    boolean existsByUnifiedTransactionId(Long unifiedTransactionId);

    /**
     * Check if a historical transaction has any allocations
     */
    boolean existsByHistoricalTransactionId(Long historicalTransactionId);

    /**
     * Get batch info (batch_id, status, paid_date) for multiple unified transactions.
     * Used for populating batch columns in RENT_RECEIVED sheets.
     * Returns: [unifiedTransactionId, paymentBatchId, paymentStatus, paidDate]
     * Only returns OWNER allocations (for rent income).
     */
    @Query("SELECT ua.unifiedTransactionId, ua.paymentBatchId, ua.paymentStatus, ua.paidDate " +
           "FROM UnifiedAllocation ua WHERE ua.unifiedTransactionId IN :transactionIds " +
           "AND ua.allocationType = 'OWNER'")
    List<Object[]> getBatchInfoForTransactions(@Param("transactionIds") List<Long> transactionIds);

    /**
     * Get batch info (batch_id, status, paid_date) for multiple unified transactions.
     * Used for populating batch columns in EXPENSES sheets.
     * Returns: [unifiedTransactionId, paymentBatchId, paymentStatus, paidDate]
     * Returns EXPENSE, DISBURSEMENT, and COMMISSION allocations.
     */
    @Query("SELECT ua.unifiedTransactionId, ua.paymentBatchId, ua.paymentStatus, ua.paidDate " +
           "FROM UnifiedAllocation ua WHERE ua.unifiedTransactionId IN :transactionIds " +
           "AND ua.allocationType IN ('EXPENSE', 'DISBURSEMENT', 'COMMISSION')")
    List<Object[]> getBatchInfoForExpenseTransactions(@Param("transactionIds") List<Long> transactionIds);

    /**
     * Get remaining unallocated amount for a unified transaction
     */
    @Query("""
        SELECT ut.netToOwnerAmount - COALESCE(SUM(ua.amount), 0)
        FROM UnifiedTransaction ut
        LEFT JOIN UnifiedAllocation ua ON ua.unifiedTransactionId = ut.id
        WHERE ut.id = :unifiedTransactionId
        GROUP BY ut.id, ut.netToOwnerAmount
    """)
    BigDecimal getRemainingUnallocatedForUnified(@Param("unifiedTransactionId") Long unifiedTransactionId);

    /**
     * Delete all allocations for a unified transaction
     */
    @Modifying
    @Query("DELETE FROM UnifiedAllocation ua WHERE ua.unifiedTransactionId = :unifiedTransactionId")
    int deleteByUnifiedTransactionId(@Param("unifiedTransactionId") Long unifiedTransactionId);

    /**
     * Delete all allocations for a historical transaction
     */
    @Modifying
    @Query("DELETE FROM UnifiedAllocation ua WHERE ua.historicalTransactionId = :historicalTransactionId")
    int deleteByHistoricalTransactionId(@Param("historicalTransactionId") Long historicalTransactionId);

    /**
     * Find allocations by source
     */
    List<UnifiedAllocation> findBySource(UnifiedAllocation.AllocationSource source);

    /**
     * Find distinct batch references for an owner
     * Uses payment_batches join since beneficiaryId in allocations is often NULL for historical data
     */
    @Query(value = """
        SELECT DISTINCT ua.payment_batch_id
        FROM unified_allocations ua
        JOIN payment_batches pb ON ua.payment_batch_id COLLATE utf8mb4_unicode_ci = pb.batch_id COLLATE utf8mb4_unicode_ci
        WHERE pb.beneficiary_id = :beneficiaryId
        AND ua.payment_batch_id IS NOT NULL
        ORDER BY ua.payment_batch_id DESC
    """, nativeQuery = true)
    List<String> findDistinctBatchReferencesByBeneficiaryId(@Param("beneficiaryId") Long beneficiaryId);

    /**
     * Find all distinct batch references
     */
    @Query("SELECT DISTINCT ua.paymentBatchId FROM UnifiedAllocation ua WHERE ua.paymentBatchId IS NOT NULL ORDER BY ua.paymentBatchId DESC")
    List<String> findAllDistinctBatchReferences();

    /**
     * Get income allocations for an owner (OWNER type = money going TO owner)
     * Returns OWNER allocations ordered by date
     */
    @Query(value = """
        SELECT ua.id, ua.unified_transaction_id, ua.historical_transaction_id,
               ua.property_name, ua.category, ua.amount, ua.payment_batch_id,
               ua.description, ua.created_at, ua.source
        FROM unified_allocations ua
        JOIN payment_batches pb ON ua.payment_batch_id COLLATE utf8mb4_unicode_ci = pb.batch_id COLLATE utf8mb4_unicode_ci
        WHERE pb.beneficiary_id = :ownerId
        AND ua.allocation_type = 'OWNER'
        ORDER BY ua.created_at, ua.property_name
    """, nativeQuery = true)
    List<Object[]> getIncomeAllocationsForOwner(@Param("ownerId") Long ownerId);

    /**
     * Get expense/deduction allocations for an owner
     * Returns EXPENSE, COMMISSION, DISBURSEMENT allocations (money deducted from owner payment)
     *
     * Links expenses to owner through multiple paths (OR condition):
     * 1. payment_batch_id -> payment_batches.beneficiary_id (for manual batched allocations)
     * 2. property_id -> properties.property_owner_id (for property-linked expenses)
     * 3. PayProp link: expense.payprop_payment_id -> raw.incoming_transaction_id -> owner.beneficiary_payprop_id -> customer
     *
     * Returns the OWNER's batch reference (not the agency batch) so owner can see which payment the expense was deducted from
     */
    @Query(value = """
        SELECT ua.id, ua.unified_transaction_id, ua.historical_transaction_id,
               ua.property_name, ua.category, ua.amount,
               COALESCE(owner_raw.payment_batch_id, ua.payment_batch_id) as owner_payment_batch,
               ua.description, ua.created_at, ua.source
        FROM unified_allocations ua
        LEFT JOIN payment_batches pb ON ua.payment_batch_id COLLATE utf8mb4_unicode_ci = pb.batch_id COLLATE utf8mb4_unicode_ci
        LEFT JOIN properties p ON ua.property_id = p.id
        LEFT JOIN payprop_report_all_payments expense_raw ON ua.payprop_payment_id = expense_raw.payprop_id
        LEFT JOIN payprop_report_all_payments owner_raw ON expense_raw.incoming_transaction_id = owner_raw.incoming_transaction_id
            AND owner_raw.beneficiary_type = 'beneficiary'
        LEFT JOIN customers c ON owner_raw.beneficiary_payprop_id = c.payprop_entity_id
        WHERE ua.allocation_type IN ('EXPENSE', 'COMMISSION')
        AND (pb.beneficiary_id = :ownerId OR p.property_owner_id = :ownerId OR c.customer_id = :ownerId)
        ORDER BY ua.created_at, ua.property_name
    """, nativeQuery = true)
    List<Object[]> getExpenseAllocationsForOwner(@Param("ownerId") Long ownerId);

    /**
     * Get all allocations for a batch with full details
     */
    @Query("SELECT ua FROM UnifiedAllocation ua WHERE ua.paymentBatchId = :batchReference ORDER BY CASE WHEN ua.allocationType = 'OWNER' THEN 0 ELSE 1 END, ua.createdAt, ua.propertyName")
    List<UnifiedAllocation> getAllocationsForBatchWithDetails(@Param("batchReference") String batchReference);

    /**
     * Get batch summary - count and totals
     * Uses allocation type to distinguish income vs expenses (not amount sign)
     * Net = Income - Expenses (expenses are stored as positive amounts)
     */
    @Query("""
        SELECT ua.paymentBatchId,
               COUNT(ua),
               SUM(CASE WHEN ua.allocationType = 'OWNER' THEN ua.amount ELSE 0 END) as totalIncome,
               SUM(CASE WHEN ua.allocationType IN ('EXPENSE', 'COMMISSION', 'DISBURSEMENT', 'OTHER') THEN ua.amount ELSE 0 END) as totalExpenses,
               SUM(CASE WHEN ua.allocationType = 'OWNER' THEN ua.amount ELSE 0 END) - SUM(CASE WHEN ua.allocationType IN ('EXPENSE', 'COMMISSION', 'DISBURSEMENT', 'OTHER') THEN ua.amount ELSE 0 END) as netTotal
        FROM UnifiedAllocation ua
        WHERE ua.paymentBatchId IS NOT NULL
        GROUP BY ua.paymentBatchId
        ORDER BY ua.paymentBatchId
    """)
    List<Object[]> getBatchSummaries();

    /**
     * Get property summary for a specific batch
     */
    @Query("""
        SELECT ua.propertyId, ua.propertyName,
               SUM(ua.amount) as total
        FROM UnifiedAllocation ua
        WHERE ua.paymentBatchId = :batchReference
        GROUP BY ua.propertyId, ua.propertyName
        ORDER BY ua.propertyName
    """)
    List<Object[]> getPropertySummaryForBatch(@Param("batchReference") String batchReference);

    /**
     * Get total allocated to a beneficiary
     */
    @Query("SELECT COALESCE(SUM(ua.amount), 0) FROM UnifiedAllocation ua WHERE ua.beneficiaryId = :beneficiaryId")
    BigDecimal getTotalForBeneficiary(@Param("beneficiaryId") Long beneficiaryId);

    /**
     * Get total for a batch
     */
    @Query("SELECT COALESCE(SUM(ua.amount), 0) FROM UnifiedAllocation ua WHERE ua.paymentBatchId = :batchReference")
    BigDecimal getTotalForBatch(@Param("batchReference") String batchReference);

    /**
     * Get count of allocations in a batch
     */
    long countByPaymentBatchId(String paymentBatchId);

    /**
     * Delete all allocations for a batch
     */
    @Modifying
    @Query("DELETE FROM UnifiedAllocation ua WHERE ua.paymentBatchId = :batchReference")
    int deleteByBatchReference(@Param("batchReference") String batchReference);

    // ===== PROPERTY ACCOUNT BALANCE QUERIES (Block Property Support) =====

    /**
     * Get total disbursements INTO a block property account within a date range
     * DISBURSEMENT allocations where beneficiary_name is the block property name
     *
     * @param blockPropertyName The name of the block property (e.g., "BODEN HOUSE BLOCK PROPERTY")
     * @param startDate Period start date
     * @param endDate Period end date
     * @return Sum of disbursements into the property account
     */
    @Query(value = """
        SELECT COALESCE(SUM(ua.amount), 0)
        FROM unified_allocations ua
        WHERE ua.allocation_type = 'DISBURSEMENT'
        AND UPPER(ua.beneficiary_name) = UPPER(:blockPropertyName)
        AND ua.created_at >= :startDateTime
        AND ua.created_at < :endDateTime
    """, nativeQuery = true)
    BigDecimal getPropertyAccountInflows(
        @Param("blockPropertyName") String blockPropertyName,
        @Param("startDateTime") LocalDateTime startDateTime,
        @Param("endDateTime") LocalDateTime endDateTime
    );

    /**
     * Get total expenses paid FROM a block property account within a date range
     * EXPENSE allocations where the property is the block property
     *
     * @param blockPropertyId The ID of the block property
     * @param startDate Period start date
     * @param endDate Period end date
     * @return Sum of expenses paid from property account (returned as positive)
     */
    @Query(value = """
        SELECT COALESCE(SUM(ABS(ua.amount)), 0)
        FROM unified_allocations ua
        WHERE ua.allocation_type = 'EXPENSE'
        AND ua.property_id = :blockPropertyId
        AND ua.created_at >= :startDateTime
        AND ua.created_at < :endDateTime
    """, nativeQuery = true)
    BigDecimal getPropertyAccountOutflows(
        @Param("blockPropertyId") Long blockPropertyId,
        @Param("startDateTime") LocalDateTime startDateTime,
        @Param("endDateTime") LocalDateTime endDateTime
    );

    /**
     * Get all disbursements to a block property account (for detailed listing)
     */
    @Query(value = """
        SELECT ua.id, ua.property_name, ua.amount, ua.description, ua.created_at, ua.payment_batch_id
        FROM unified_allocations ua
        WHERE ua.allocation_type = 'DISBURSEMENT'
        AND UPPER(ua.beneficiary_name) = UPPER(:blockPropertyName)
        ORDER BY ua.created_at DESC
    """, nativeQuery = true)
    List<Object[]> getPropertyAccountDisbursements(@Param("blockPropertyName") String blockPropertyName);

    /**
     * Get sum of all historical disbursements to a block property (for opening balance calculation)
     * Returns total of all DISBURSEMENT allocations before the given date
     */
    @Query(value = """
        SELECT COALESCE(SUM(ua.amount), 0)
        FROM unified_allocations ua
        WHERE ua.allocation_type = 'DISBURSEMENT'
        AND UPPER(ua.beneficiary_name) = UPPER(:blockPropertyName)
        AND ua.created_at < :beforeDateTime
    """, nativeQuery = true)
    BigDecimal getPropertyAccountInflowsBefore(
        @Param("blockPropertyName") String blockPropertyName,
        @Param("beforeDateTime") LocalDateTime beforeDateTime
    );

    /**
     * Get sum of all historical expenses from a block property (for opening balance calculation)
     */
    @Query(value = """
        SELECT COALESCE(SUM(ABS(ua.amount)), 0)
        FROM unified_allocations ua
        WHERE ua.allocation_type = 'EXPENSE'
        AND ua.property_id = :blockPropertyId
        AND ua.created_at < :beforeDateTime
    """, nativeQuery = true)
    BigDecimal getPropertyAccountOutflowsBefore(
        @Param("blockPropertyId") Long blockPropertyId,
        @Param("beforeDateTime") LocalDateTime beforeDateTime
    );

    /**
     * Get total outflows (expenses) from a block property account by block name
     * Joins to properties table to find the block property ID
     */
    @Query(value = """
        SELECT COALESCE(SUM(ABS(ua.amount)), 0)
        FROM unified_allocations ua
        JOIN properties p ON ua.property_id = p.id
        WHERE ua.allocation_type = 'EXPENSE'
        AND (p.is_block_property = 1 OR p.property_type = 'BLOCK')
        AND p.property_name LIKE CONCAT('%', :blockName, '%')
        AND ua.created_at >= :startDateTime
        AND ua.created_at < :endDateTime
    """, nativeQuery = true)
    BigDecimal getPropertyAccountOutflowsByBlockName(
        @Param("blockName") String blockName,
        @Param("startDateTime") LocalDateTime startDateTime,
        @Param("endDateTime") LocalDateTime endDateTime
    );

    /**
     * Get total outflows before a date from a block property account by block name
     */
    @Query(value = """
        SELECT COALESCE(SUM(ABS(ua.amount)), 0)
        FROM unified_allocations ua
        JOIN properties p ON ua.property_id = p.id
        WHERE ua.allocation_type = 'EXPENSE'
        AND (p.is_block_property = 1 OR p.property_type = 'BLOCK')
        AND p.property_name LIKE CONCAT('%', :blockName, '%')
        AND ua.created_at < :beforeDateTime
    """, nativeQuery = true)
    BigDecimal getPropertyAccountOutflowsBeforeByBlockName(
        @Param("blockName") String blockName,
        @Param("beforeDateTime") LocalDateTime beforeDateTime
    );

    // ===== RELATED PAYMENTS QUERIES (for Excel Statement Period Sheets) =====

    /**
     * Find all allocations for properties within a date range.
     * Used to show RELATED PAYMENTS section on monthly statement tabs.
     *
     * Tries multiple join paths since allocations can be linked via:
     * 1. unified_transaction_id -> unified_transactions (primary for PayProp data)
     * 2. historical_transaction_id -> historical_transactions (legacy data)
     * 3. property_id directly on allocations (fallback)
     *
     * @param propertyIds List of property IDs from the statement's lease master
     * @param startDate Period start date
     * @param endDate Period end date
     * @return Allocations for transactions within the period that have been assigned to payment batches
     */
    @Query(value = """
        SELECT DISTINCT ua.* FROM unified_allocations ua
        LEFT JOIN unified_transactions ut ON ua.unified_transaction_id = ut.id
        LEFT JOIN historical_transactions ht ON ua.historical_transaction_id = ht.id
        WHERE ua.payment_batch_id IS NOT NULL
          AND (
            (ut.property_id IN :propertyIds AND ut.transaction_date BETWEEN :startDate AND :endDate)
            OR (ht.property_id IN :propertyIds AND ht.transaction_date BETWEEN :startDate AND :endDate)
            OR (ua.property_id IN :propertyIds AND DATE(ua.created_at) BETWEEN :startDate AND :endDate)
          )
        ORDER BY ua.payment_batch_id, ua.property_name, ua.allocation_type
    """, nativeQuery = true)
    List<UnifiedAllocation> findAllocationsForPropertiesInPeriod(
        @Param("propertyIds") List<Long> propertyIds,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find all allocations (including unbatched) for properties within a date range.
     * Used to show both batched and pending allocations in RELATED PAYMENTS section.
     *
     * Joins to ALL three possible transaction sources:
     * - unified_incoming_transactions via incoming_transaction_id (primary link)
     * - unified_transactions via unified_transaction_id
     * - historical_transactions via historical_transaction_id
     */
    @Query(value = """
        SELECT DISTINCT ua.* FROM unified_allocations ua
        LEFT JOIN unified_incoming_transactions uit ON ua.incoming_transaction_id = uit.id
        LEFT JOIN unified_transactions ut ON ua.unified_transaction_id = ut.id
        LEFT JOIN historical_transactions ht ON ua.historical_transaction_id = ht.id
        WHERE (
            (uit.property_id IN :propertyIds AND uit.transaction_date BETWEEN :startDate AND :endDate)
            OR (ut.property_id IN :propertyIds AND ut.transaction_date BETWEEN :startDate AND :endDate)
            OR (ht.property_id IN :propertyIds AND ht.transaction_date BETWEEN :startDate AND :endDate)
            OR (ua.property_id IN :propertyIds AND DATE(ua.created_at) BETWEEN :startDate AND :endDate)
        )
        ORDER BY ua.payment_batch_id, ua.property_name, ua.allocation_type
    """, nativeQuery = true)
    List<UnifiedAllocation> findAllAllocationsForPropertiesInPeriod(
        @Param("propertyIds") List<Long> propertyIds,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Get allocation summary per property for reconciliation columns.
     * Groups allocations by property and calculates:
     * - Total OWNER allocations (money going to owner)
     * - Payment status (highest status: PAID > BATCHED > PENDING)
     * - Batch IDs (concatenated if multiple)
     * - Latest paid date
     * - Allocation count
     */
    @Query(value = """
        SELECT
            ua.property_id,
            SUM(CASE WHEN ua.allocation_type = 'OWNER' THEN ua.amount ELSE 0 END) as total_owner_allocated,
            MAX(ua.payment_status) as max_payment_status,
            GROUP_CONCAT(DISTINCT ua.payment_batch_id SEPARATOR ', ') as batch_ids,
            MAX(ua.paid_date) as latest_paid_date,
            COUNT(*) as allocation_count
        FROM unified_allocations ua
        LEFT JOIN unified_incoming_transactions uit ON ua.incoming_transaction_id = uit.id
        LEFT JOIN unified_transactions ut ON ua.unified_transaction_id = ut.id
        LEFT JOIN historical_transactions ht ON ua.historical_transaction_id = ht.id
        WHERE ua.property_id IN :propertyIds
          AND (
              (uit.transaction_date BETWEEN :startDate AND :endDate)
              OR (ut.transaction_date BETWEEN :startDate AND :endDate)
              OR (ht.transaction_date BETWEEN :startDate AND :endDate)
              OR (DATE(ua.created_at) BETWEEN :startDate AND :endDate)
          )
        GROUP BY ua.property_id
    """, nativeQuery = true)
    List<Object[]> getLeaseAllocationSummaryForPeriod(
        @Param("propertyIds") List<Long> propertyIds,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Get allocation summary per invoice/lease for reconciliation columns.
     * Groups allocations by invoice_id and calculates:
     * - Total OWNER allocations (money going to owner)
     * - Payment status (highest status: PAID > BATCHED > PENDING)
     * - Batch IDs (concatenated if multiple)
     * - Latest paid date
     * - Allocation count
     *
     * IMPORTANT: Date filtering ALWAYS applies to ensure allocations are shown in the correct
     * period sheet. The invoice_id is used for grouping accuracy but does not bypass date filtering.
     * This prevents showing cumulative allocations across all periods instead of period-specific allocations.
     *
     * NOTE: Filters by transaction_date (when payment was received), NOT by ua.paid_date
     * (when allocation was processed). This ensures allocated_amount aligns with rent_received,
     * using the linked incoming_transaction_id. Both use the same date criteria.
     */
    @Query(value = """
        SELECT
            COALESCE(ua.invoice_id, 0) as invoice_id,
            ua.property_id,
            SUM(CASE WHEN ua.allocation_type = 'OWNER' THEN ua.amount ELSE 0 END) as total_owner_allocated,
            MAX(ua.payment_status) as max_payment_status,
            GROUP_CONCAT(DISTINCT ua.payment_batch_id SEPARATOR ', ') as batch_ids,
            MAX(ua.paid_date) as latest_paid_date,
            COUNT(*) as allocation_count
        FROM unified_allocations ua
        LEFT JOIN unified_incoming_transactions uit ON ua.incoming_transaction_id = uit.id
        LEFT JOIN unified_transactions ut ON ua.unified_transaction_id = ut.id
        LEFT JOIN historical_transactions ht ON ua.historical_transaction_id = ht.id
        WHERE ua.property_id IN :propertyIds
          AND (ua.invoice_id IN :invoiceIds OR ua.invoice_id IS NULL)
          AND (
              (uit.transaction_date BETWEEN :startDate AND :endDate)
              OR (ut.transaction_date BETWEEN :startDate AND :endDate)
              OR (ht.transaction_date BETWEEN :startDate AND :endDate)
          )
        GROUP BY COALESCE(ua.invoice_id, 0), ua.property_id
    """, nativeQuery = true)
    List<Object[]> getLeaseAllocationSummaryByInvoice(
        @Param("propertyIds") List<Long> propertyIds,
        @Param("invoiceIds") List<Long> invoiceIds,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Get batch references by invoice/lease and allocation type for period.
     * Returns invoice_id, property_id, allocation_type, batch_ids (comma-separated).
     * Used to populate batch reference columns on monthly statement rows.
     *
     * IMPORTANT: Groups by invoice_id (lease) to ensure batch refs match the specific lease,
     * not all leases on the same property. This aligns with how rent_received is calculated
     * per lease_reference in the Excel statement.
     *
     * NOTE: Filters by transaction_date (when payment was received), NOT by ua.paid_date
     * (when allocation was processed). This ensures batch refs appear only when the
     * corresponding rent_received appears, using the linked incoming_transaction_id.
     */
    @Query(value = """
        SELECT
            COALESCE(ua.invoice_id, 0) as invoice_id,
            ua.property_id,
            ua.allocation_type,
            GROUP_CONCAT(DISTINCT ua.payment_batch_id SEPARATOR ', ') as batch_ids
        FROM unified_allocations ua
        LEFT JOIN unified_incoming_transactions uit ON ua.incoming_transaction_id = uit.id
        LEFT JOIN unified_transactions ut ON ua.unified_transaction_id = ut.id
        LEFT JOIN historical_transactions ht ON ua.historical_transaction_id = ht.id
        WHERE ua.property_id IN :propertyIds
          AND ua.payment_batch_id IS NOT NULL
          AND (ua.invoice_id IN :invoiceIds OR ua.invoice_id IS NULL)
          AND (
              (uit.transaction_date BETWEEN :startDate AND :endDate)
              OR (ut.transaction_date BETWEEN :startDate AND :endDate)
              OR (ht.transaction_date BETWEEN :startDate AND :endDate)
          )
        GROUP BY COALESCE(ua.invoice_id, 0), ua.property_id, ua.allocation_type
    """, nativeQuery = true)
    List<Object[]> getBatchRefsByInvoiceAndType(
        @Param("propertyIds") List<Long> propertyIds,
        @Param("invoiceIds") List<Long> invoiceIds,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Get total payments made to owner within a period.
     * Sums the net payment (OWNER - EXPENSE - COMMISSION) from payment batches.
     * Used for Payment Reconciliation section.
     */
    @Query(value = """
        SELECT
            pb.batch_id,
            pb.payment_date,
            pb.status,
            SUM(CASE WHEN ua.allocation_type = 'OWNER' THEN ua.amount ELSE 0 END) as total_owner,
            SUM(CASE WHEN ua.allocation_type = 'EXPENSE' THEN ua.amount ELSE 0 END) as total_expense,
            SUM(CASE WHEN ua.allocation_type = 'COMMISSION' THEN ua.amount ELSE 0 END) as total_commission,
            SUM(CASE WHEN ua.allocation_type = 'OWNER' THEN ua.amount ELSE 0 END) -
            SUM(CASE WHEN ua.allocation_type IN ('EXPENSE', 'COMMISSION') THEN ua.amount ELSE 0 END) as net_payment
        FROM unified_allocations ua
        JOIN payment_batches pb ON ua.payment_batch_id COLLATE utf8mb4_unicode_ci = pb.batch_id COLLATE utf8mb4_unicode_ci
        LEFT JOIN unified_incoming_transactions uit ON ua.incoming_transaction_id = uit.id
        LEFT JOIN unified_transactions ut ON ua.unified_transaction_id = ut.id
        LEFT JOIN historical_transactions ht ON ua.historical_transaction_id = ht.id
        WHERE ua.property_id IN :propertyIds
          AND (
              (uit.transaction_date BETWEEN :startDate AND :endDate)
              OR (ut.transaction_date BETWEEN :startDate AND :endDate)
              OR (ht.transaction_date BETWEEN :startDate AND :endDate)
              OR (DATE(ua.created_at) BETWEEN :startDate AND :endDate)
          )
        GROUP BY pb.batch_id, pb.payment_date, pb.status
        ORDER BY pb.payment_date
    """, nativeQuery = true)
    List<Object[]> getPaymentBatchSummariesForPeriod(
        @Param("propertyIds") List<Long> propertyIds,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}

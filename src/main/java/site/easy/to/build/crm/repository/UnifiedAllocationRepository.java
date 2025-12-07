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
     */
    @Query(value = """
        SELECT ua.id, ua.unified_transaction_id, ua.historical_transaction_id,
               ua.property_name, ua.category, ua.amount, ua.payment_batch_id,
               ua.description, ua.created_at, ua.source
        FROM unified_allocations ua
        JOIN payment_batches pb ON ua.payment_batch_id COLLATE utf8mb4_unicode_ci = pb.batch_id COLLATE utf8mb4_unicode_ci
        WHERE pb.beneficiary_id = :ownerId
        AND ua.allocation_type IN ('EXPENSE', 'COMMISSION', 'DISBURSEMENT')
        ORDER BY ua.created_at, ua.property_name
    """, nativeQuery = true)
    List<Object[]> getExpenseAllocationsForOwner(@Param("ownerId") Long ownerId);

    /**
     * Get all allocations for a batch with full details
     */
    @Query("SELECT ua FROM UnifiedAllocation ua WHERE ua.paymentBatchId = :batchReference ORDER BY CASE WHEN ua.amount > 0 THEN 0 ELSE 1 END, ua.createdAt, ua.propertyName")
    List<UnifiedAllocation> getAllocationsForBatchWithDetails(@Param("batchReference") String batchReference);

    /**
     * Get batch summary - count and totals
     */
    @Query("""
        SELECT ua.paymentBatchId,
               COUNT(ua),
               SUM(CASE WHEN ua.amount > 0 THEN ua.amount ELSE 0 END) as totalIncome,
               SUM(CASE WHEN ua.amount < 0 THEN ua.amount ELSE 0 END) as totalExpenses,
               SUM(ua.amount) as netTotal
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
}

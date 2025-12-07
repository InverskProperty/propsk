package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.TransactionBatchAllocation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Repository for TransactionBatchAllocation entities.
 * Provides methods for managing transaction-to-batch allocations with split support.
 *
 * @deprecated Use {@link UnifiedAllocationRepository} instead. The allocation data has been
 * migrated to the unified_allocations table, which now serves as the single source of truth
 * for all allocations (both from PayProp and manual).
 *
 * This repository is kept for backwards compatibility during the transition period.
 * New code should use UnifiedAllocationRepository.
 */
@Deprecated
@Repository
public interface TransactionBatchAllocationRepository extends JpaRepository<TransactionBatchAllocation, Long> {

    // ===== FIND BY TRANSACTION =====

    /**
     * Find all allocations for a specific transaction
     */
    List<TransactionBatchAllocation> findByTransactionId(Long transactionId);

    /**
     * Get total allocated amount for a transaction
     */
    @Query("SELECT COALESCE(SUM(a.allocatedAmount), 0) FROM TransactionBatchAllocation a WHERE a.transactionId = :transactionId")
    BigDecimal getTotalAllocatedForTransaction(@Param("transactionId") Long transactionId);

    /**
     * Check if a transaction has any allocations
     */
    boolean existsByTransactionId(Long transactionId);

    // ===== FIND BY BATCH =====

    /**
     * Find all allocations for a specific batch
     */
    List<TransactionBatchAllocation> findByBatchReference(String batchReference);

    /**
     * Get total allocated amount for a batch
     */
    @Query("SELECT COALESCE(SUM(a.allocatedAmount), 0) FROM TransactionBatchAllocation a WHERE a.batchReference = :batchReference")
    BigDecimal getTotalForBatch(@Param("batchReference") String batchReference);

    /**
     * Get count of allocations in a batch
     */
    long countByBatchReference(String batchReference);

    // ===== FIND BY PROPERTY =====

    /**
     * Find all allocations for a property
     */
    List<TransactionBatchAllocation> findByPropertyId(Long propertyId);

    /**
     * Find unallocated transactions for a property (transactions with net_to_owner but no allocations)
     */
    @Query("""
        SELECT t.id, t.transactionDate, t.amount, t.netToOwnerAmount, t.category, t.description,
               COALESCE(SUM(a.allocatedAmount), 0) as totalAllocated
        FROM HistoricalTransaction t
        LEFT JOIN TransactionBatchAllocation a ON t.id = a.transactionId
        WHERE t.property.id = :propertyId
        AND t.netToOwnerAmount IS NOT NULL
        GROUP BY t.id, t.transactionDate, t.amount, t.netToOwnerAmount, t.category, t.description
        HAVING COALESCE(SUM(a.allocatedAmount), 0) < t.netToOwnerAmount
        OR (t.netToOwnerAmount < 0 AND COALESCE(SUM(a.allocatedAmount), 0) > t.netToOwnerAmount)
        ORDER BY t.transactionDate
    """)
    List<Object[]> findUnallocatedTransactionsForProperty(@Param("propertyId") Long propertyId);

    // ===== FIND BY BENEFICIARY =====

    /**
     * Find all allocations for a beneficiary (owner)
     */
    List<TransactionBatchAllocation> findByBeneficiaryId(Long beneficiaryId);

    /**
     * Get total allocated to a beneficiary
     */
    @Query("SELECT COALESCE(SUM(a.allocatedAmount), 0) FROM TransactionBatchAllocation a WHERE a.beneficiaryId = :beneficiaryId")
    BigDecimal getTotalForBeneficiary(@Param("beneficiaryId") Long beneficiaryId);

    // ===== BATCH OPERATIONS =====

    /**
     * Delete all allocations for a batch (when removing/recreating a batch)
     */
    @Modifying
    @Query("DELETE FROM TransactionBatchAllocation a WHERE a.batchReference = :batchReference")
    int deleteByBatchReference(@Param("batchReference") String batchReference);

    /**
     * Delete all allocations for a transaction
     */
    @Modifying
    @Query("DELETE FROM TransactionBatchAllocation a WHERE a.transactionId = :transactionId")
    int deleteByTransactionId(@Param("transactionId") Long transactionId);

    // ===== VALIDATION QUERIES =====

    /**
     * Check if allocating an amount would exceed the transaction's net_to_owner_amount
     * Returns true if the allocation would be valid
     */
    @Query("""
        SELECT CASE
            WHEN t.netToOwnerAmount >= 0 THEN
                (COALESCE(SUM(a.allocatedAmount), 0) + :newAmount) <= t.netToOwnerAmount
            ELSE
                (COALESCE(SUM(a.allocatedAmount), 0) + :newAmount) >= t.netToOwnerAmount
        END
        FROM HistoricalTransaction t
        LEFT JOIN TransactionBatchAllocation a ON t.id = a.transactionId
        WHERE t.id = :transactionId
        GROUP BY t.id, t.netToOwnerAmount
    """)
    Boolean isAllocationValid(@Param("transactionId") Long transactionId, @Param("newAmount") BigDecimal newAmount);

    /**
     * Get remaining unallocated amount for a transaction
     */
    @Query("""
        SELECT t.netToOwnerAmount - COALESCE(SUM(a.allocatedAmount), 0)
        FROM HistoricalTransaction t
        LEFT JOIN TransactionBatchAllocation a ON t.id = a.transactionId
        WHERE t.id = :transactionId
        GROUP BY t.id, t.netToOwnerAmount
    """)
    BigDecimal getRemainingUnallocated(@Param("transactionId") Long transactionId);

    // ===== REPORTING =====

    /**
     * Get allocation summary by batch
     */
    @Query("""
        SELECT a.batchReference,
               COUNT(a),
               SUM(CASE WHEN a.allocatedAmount > 0 THEN a.allocatedAmount ELSE 0 END) as totalIncome,
               SUM(CASE WHEN a.allocatedAmount < 0 THEN a.allocatedAmount ELSE 0 END) as totalExpenses,
               SUM(a.allocatedAmount) as netTotal
        FROM TransactionBatchAllocation a
        GROUP BY a.batchReference
        ORDER BY a.batchReference
    """)
    List<Object[]> getBatchSummaries();

    /**
     * Get allocation summary by property for a specific batch
     */
    @Query("""
        SELECT a.propertyId, a.propertyName,
               SUM(a.allocatedAmount) as total
        FROM TransactionBatchAllocation a
        WHERE a.batchReference = :batchReference
        GROUP BY a.propertyId, a.propertyName
        ORDER BY a.propertyName
    """)
    List<Object[]> getPropertySummaryForBatch(@Param("batchReference") String batchReference);

    /**
     * Find distinct batch references for an owner
     * Note: Uses payment_batches join since beneficiaryId in allocations is often NULL
     * Uses native query with COLLATE to handle collation mismatch between tables
     */
    @Query(value = """
        SELECT DISTINCT a.batch_reference
        FROM transaction_batch_allocations a
        JOIN payment_batches pb ON a.batch_reference COLLATE utf8mb4_unicode_ci = pb.batch_id COLLATE utf8mb4_unicode_ci
        WHERE pb.beneficiary_id = :beneficiaryId
        ORDER BY a.batch_reference DESC
    """, nativeQuery = true)
    List<String> findDistinctBatchReferencesByBeneficiaryId(@Param("beneficiaryId") Long beneficiaryId);

    /**
     * Find all distinct batch references
     */
    @Query("SELECT DISTINCT a.batchReference FROM TransactionBatchAllocation a ORDER BY a.batchReference DESC")
    List<String> findAllDistinctBatchReferences();

    // ===== XLSX STATEMENT QUERIES =====

    /**
     * Get income allocations for an owner with transaction details
     * Note: Owner is determined via payment_batches join, NOT via beneficiaryId (which is often NULL)
     * Uses native query with COLLATE to handle collation mismatch between tables
     * Returns: transactionId, transactionDate, propertyName, category, amount, netToOwner, commission, batchReference, allocatedAmount, description, tenantName
     */
    @Query(value = """
        SELECT a.transaction_id, t.transaction_date, a.property_name, t.category,
               t.amount, t.net_to_owner_amount, t.commission_amount,
               a.batch_reference, a.allocated_amount,
               t.description,
               (SELECT c.name FROM customers c WHERE c.customer_id = t.tenant_id) as tenant_name
        FROM transaction_batch_allocations a
        JOIN historical_transactions t ON a.transaction_id = t.id
        JOIN payment_batches pb ON a.batch_reference COLLATE utf8mb4_unicode_ci = pb.batch_id COLLATE utf8mb4_unicode_ci
        WHERE pb.beneficiary_id = :ownerId
        AND a.allocated_amount > 0
        ORDER BY t.transaction_date, a.property_name
    """, nativeQuery = true)
    List<Object[]> getIncomeAllocationsForOwner(@Param("ownerId") Long ownerId);

    /**
     * Get expense allocations for an owner with transaction details
     * Note: Owner is determined via payment_batches join, NOT via beneficiaryId (which is often NULL)
     * Uses native query with COLLATE to handle collation mismatch between tables
     * Returns: transactionId, transactionDate, propertyName, category, amount, batchReference, allocatedAmount, description
     */
    @Query(value = """
        SELECT a.transaction_id, t.transaction_date, a.property_name, t.category,
               t.amount, a.batch_reference, a.allocated_amount,
               t.description
        FROM transaction_batch_allocations a
        JOIN historical_transactions t ON a.transaction_id = t.id
        JOIN payment_batches pb ON a.batch_reference COLLATE utf8mb4_unicode_ci = pb.batch_id COLLATE utf8mb4_unicode_ci
        WHERE pb.beneficiary_id = :ownerId
        AND a.allocated_amount < 0
        ORDER BY t.transaction_date, a.property_name
    """, nativeQuery = true)
    List<Object[]> getExpenseAllocationsForOwner(@Param("ownerId") Long ownerId);

    /**
     * Get all allocations for a batch with full details for Owner Payments Summary
     * Returns: transactionId, transactionDate, propertyName, category, allocatedAmount, description, isIncome
     */
    @Query("""
        SELECT a.transactionId, t.transactionDate, a.propertyName, t.category,
               a.allocatedAmount, t.description,
               CASE WHEN a.allocatedAmount > 0 THEN true ELSE false END as isIncome
        FROM TransactionBatchAllocation a
        JOIN HistoricalTransaction t ON a.transactionId = t.id
        WHERE a.batchReference = :batchReference
        ORDER BY CASE WHEN a.allocatedAmount > 0 THEN 0 ELSE 1 END, t.transactionDate, a.propertyName
    """)
    List<Object[]> getAllocationsForBatchWithDetails(@Param("batchReference") String batchReference);

    /**
     * Get split allocation info - transactions allocated to multiple batches
     * Returns: transactionId, netToOwnerAmount, batchCount, batchReferences (comma-separated)
     */
    @Query("""
        SELECT a.transactionId, t.netToOwnerAmount, COUNT(DISTINCT a.batchReference) as batchCount
        FROM TransactionBatchAllocation a
        JOIN HistoricalTransaction t ON a.transactionId = t.id
        WHERE a.beneficiaryId = :ownerId
        GROUP BY a.transactionId, t.netToOwnerAmount
        HAVING COUNT(DISTINCT a.batchReference) > 1
    """)
    List<Object[]> getSplitAllocationsForOwner(@Param("ownerId") Long ownerId);

    /**
     * Get all batch references for a specific transaction (for split display)
     */
    @Query("SELECT a.batchReference, a.allocatedAmount FROM TransactionBatchAllocation a WHERE a.transactionId = :transactionId ORDER BY a.batchReference")
    List<Object[]> getBatchReferencesForTransaction(@Param("transactionId") Long transactionId);

    // ===== UNIFIED TRANSACTION QUERIES (NEW - for migration to unified layer) =====

    /**
     * Find all allocations for a unified transaction
     */
    List<TransactionBatchAllocation> findByUnifiedTransactionId(Long unifiedTransactionId);

    /**
     * Get total allocated amount for a unified transaction
     */
    @Query("SELECT COALESCE(SUM(a.allocatedAmount), 0) FROM TransactionBatchAllocation a WHERE a.unifiedTransactionId = :unifiedTransactionId")
    BigDecimal getTotalAllocatedForUnifiedTransaction(@Param("unifiedTransactionId") Long unifiedTransactionId);

    /**
     * Get remaining unallocated amount for a unified transaction
     */
    @Query("""
        SELECT ut.netToOwnerAmount - COALESCE(SUM(a.allocatedAmount), 0)
        FROM UnifiedTransaction ut
        LEFT JOIN TransactionBatchAllocation a ON a.unifiedTransactionId = ut.id
        WHERE ut.id = :unifiedTransactionId
        GROUP BY ut.id, ut.netToOwnerAmount
    """)
    BigDecimal getRemainingUnallocatedForUnified(@Param("unifiedTransactionId") Long unifiedTransactionId);

    /**
     * Check if a unified transaction has any allocations
     */
    boolean existsByUnifiedTransactionId(Long unifiedTransactionId);

    /**
     * Delete all allocations for a unified transaction
     */
    @Modifying
    @Query("DELETE FROM TransactionBatchAllocation a WHERE a.unifiedTransactionId = :unifiedTransactionId")
    int deleteByUnifiedTransactionId(@Param("unifiedTransactionId") Long unifiedTransactionId);
}

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
 */
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
     * Find distinct batch references for a beneficiary (owner)
     */
    @Query("SELECT DISTINCT a.batchReference FROM TransactionBatchAllocation a WHERE a.beneficiaryId = :beneficiaryId ORDER BY a.batchReference DESC")
    List<String> findDistinctBatchReferencesByBeneficiaryId(@Param("beneficiaryId") Long beneficiaryId);

    /**
     * Find all distinct batch references
     */
    @Query("SELECT DISTINCT a.batchReference FROM TransactionBatchAllocation a ORDER BY a.batchReference DESC")
    List<String> findAllDistinctBatchReferences();
}

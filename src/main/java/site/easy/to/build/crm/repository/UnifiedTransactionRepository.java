package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.UnifiedTransaction;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface UnifiedTransactionRepository extends JpaRepository<UnifiedTransaction, Long> {

    /**
     * Find all transactions with invoice_id (lease-linked transactions)
     * This is the primary query for statement generation
     */
    List<UnifiedTransaction> findByInvoiceIdIsNotNull();

    /**
     * Find transactions by invoice ID (lease)
     */
    List<UnifiedTransaction> findByInvoiceId(Long invoiceId);

    /**
     * Find transactions by property ID
     */
    List<UnifiedTransaction> findByPropertyId(Long propertyId);

    /**
     * Find transactions by customer ID
     */
    List<UnifiedTransaction> findByCustomerId(Long customerId);

    /**
     * Find transactions by date range
     */
    List<UnifiedTransaction> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Find transactions by invoice ID and date range (for statement generation)
     */
    List<UnifiedTransaction> findByInvoiceIdAndTransactionDateBetween(
        Long invoiceId,
        LocalDate startDate,
        LocalDate endDate
    );

    /**
     * Find transactions for properties owned by customer (for statement generation)
     */
    @Query("""
        SELECT ut FROM UnifiedTransaction ut
        WHERE ut.propertyId IN (
            SELECT cpa.property.id FROM CustomerPropertyAssignment cpa
            WHERE cpa.customer.customerId = :customerId
              AND cpa.assignmentType IN ('OWNER', 'MANAGER')
        )
        AND ut.invoiceId IS NOT NULL
        AND ut.transactionDate BETWEEN :startDate AND :endDate
        ORDER BY ut.transactionDate, ut.id
    """)
    List<UnifiedTransaction> findByCustomerOwnedPropertiesAndDateRange(
        @Param("customerId") Long customerId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Get transaction count by source system (for rebuild verification)
     */
    @Query("""
        SELECT ut.sourceSystem, COUNT(ut)
        FROM UnifiedTransaction ut
        GROUP BY ut.sourceSystem
    """)
    List<Object[]> countBySourceSystem();

    /**
     * Get statistics for rebuild verification
     */
    @Query("""
        SELECT
            ut.sourceSystem,
            COUNT(ut) as count,
            MIN(ut.transactionDate) as earliest,
            MAX(ut.transactionDate) as latest,
            SUM(ut.amount) as total
        FROM UnifiedTransaction ut
        GROUP BY ut.sourceSystem
    """)
    List<Object[]> getRebuildStatistics();

    /**
     * Check if a source record already exists (for incremental updates)
     */
    @Query("""
        SELECT COUNT(ut) > 0
        FROM UnifiedTransaction ut
        WHERE ut.sourceSystem = :sourceSystem
          AND ut.sourceTable = :sourceTable
          AND ut.sourceRecordId = :sourceRecordId
    """)
    boolean existsBySourceRecord(
        @Param("sourceSystem") UnifiedTransaction.SourceSystem sourceSystem,
        @Param("sourceTable") String sourceTable,
        @Param("sourceRecordId") Long sourceRecordId
    );

    /**
     * Delete by source system (for selective rebuild)
     */
    void deleteBySourceSystem(UnifiedTransaction.SourceSystem sourceSystem);

    /**
     * Delete by rebuild batch ID (for rollback)
     */
    void deleteByRebuildBatchId(String rebuildBatchId);
}

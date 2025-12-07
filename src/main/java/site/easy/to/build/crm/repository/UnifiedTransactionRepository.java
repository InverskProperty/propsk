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
     * Find transactions by date range and flow direction
     * Use this to get only INCOMING (rent received) or OUTGOING (payments, expenses) transactions
     */
    List<UnifiedTransaction> findByTransactionDateBetweenAndFlowDirection(
        LocalDate startDate,
        LocalDate endDate,
        UnifiedTransaction.FlowDirection flowDirection
    );

    /**
     * Find transactions by invoice ID, date range, and flow direction
     * Use this for statement generation to separate rent received (INCOMING) from payments (OUTGOING)
     */
    List<UnifiedTransaction> findByInvoiceIdAndTransactionDateBetweenAndFlowDirection(
        Long invoiceId,
        LocalDate startDate,
        LocalDate endDate,
        UnifiedTransaction.FlowDirection flowDirection
    );

    /**
     * Find transactions by date range, flow direction, and transaction type
     * Use this for specific transaction filtering (e.g., only agency fees, only expenses)
     */
    List<UnifiedTransaction> findByTransactionDateBetweenAndFlowDirectionAndTransactionType(
        LocalDate startDate,
        LocalDate endDate,
        UnifiedTransaction.FlowDirection flowDirection,
        String transactionType
    );

    /**
     * Find transactions by invoice ID, date range, flow direction, and transaction type
     * Use this for extracting specific transaction types for a lease (e.g., expenses only)
     */
    List<UnifiedTransaction> findByInvoiceIdAndTransactionDateBetweenAndFlowDirectionAndTransactionType(
        Long invoiceId,
        LocalDate startDate,
        LocalDate endDate,
        UnifiedTransaction.FlowDirection flowDirection,
        String transactionType
    );

    /**
     * Find transactions for properties owned by customer (for statement generation)
     * Includes INCOMING_PAYMENT records (which have invoiceId=NULL and propertyId=NULL)
     * Matches INCOMING_PAYMENT by property_name instead of propertyId
     */
    @Query("""
        SELECT ut FROM UnifiedTransaction ut
        WHERE (
            ut.propertyId IN (
                SELECT cpa.property.id FROM CustomerPropertyAssignment cpa
                WHERE cpa.customer.customerId = :customerId
                  AND cpa.assignmentType IN ('OWNER', 'MANAGER')
            )
            OR (
                ut.paypropDataSource = 'INCOMING_PAYMENT'
                AND ut.propertyName IN (
                    SELECT p.propertyName FROM CustomerPropertyAssignment cpa
                    JOIN cpa.property p
                    WHERE cpa.customer.customerId = :customerId
                      AND cpa.assignmentType IN ('OWNER', 'MANAGER')
                )
            )
        )
        AND ut.transactionDate BETWEEN :startDate AND :endDate
        ORDER BY ut.transactionDate, ut.id
    """)
    List<UnifiedTransaction> findByCustomerOwnedPropertiesAndDateRange(
        @Param("customerId") Long customerId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find transactions for properties owned by customer, filtered by flow direction
     * Use this to get only INCOMING (rent received) or OUTGOING (payments, expenses) for a customer's properties
     */
    @Query("""
        SELECT ut FROM UnifiedTransaction ut
        WHERE (
            ut.propertyId IN (
                SELECT cpa.property.id FROM CustomerPropertyAssignment cpa
                WHERE cpa.customer.customerId = :customerId
                  AND cpa.assignmentType IN ('OWNER', 'MANAGER')
            )
            OR (
                ut.paypropDataSource = 'INCOMING_PAYMENT'
                AND ut.propertyName IN (
                    SELECT p.propertyName FROM CustomerPropertyAssignment cpa
                    JOIN cpa.property p
                    WHERE cpa.customer.customerId = :customerId
                      AND cpa.assignmentType IN ('OWNER', 'MANAGER')
                )
            )
        )
        AND ut.transactionDate BETWEEN :startDate AND :endDate
        AND ut.flowDirection = :flowDirection
        ORDER BY ut.transactionDate, ut.id
    """)
    List<UnifiedTransaction> findByCustomerOwnedPropertiesAndDateRangeAndFlowDirection(
        @Param("customerId") Long customerId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("flowDirection") UnifiedTransaction.FlowDirection flowDirection
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

    /**
     * Find OUTGOING transactions (expenses) for a property within a date range
     * Use this to get expenses for statement generation
     */
    List<UnifiedTransaction> findByPropertyIdAndTransactionDateBetweenAndFlowDirection(
        Long propertyId,
        LocalDate startDate,
        LocalDate endDate,
        UnifiedTransaction.FlowDirection flowDirection
    );

    // ===== ALLOCATION SUPPORT QUERIES =====

    /**
     * Find transactions with net_to_owner_amount for a property (for allocation UI)
     */
    @Query("""
        SELECT ut FROM UnifiedTransaction ut
        WHERE ut.propertyId = :propertyId
          AND ut.netToOwnerAmount IS NOT NULL
        ORDER BY ut.transactionDate DESC
    """)
    List<UnifiedTransaction> findByPropertyIdWithNetToOwner(@Param("propertyId") Long propertyId);

    /**
     * Find transactions with net_to_owner_amount for properties owned by customer (for allocation UI)
     */
    @Query("""
        SELECT ut FROM UnifiedTransaction ut
        WHERE ut.propertyId IN (
            SELECT cpa.property.id FROM CustomerPropertyAssignment cpa
            WHERE cpa.customer.customerId = :ownerId
              AND cpa.assignmentType IN ('OWNER', 'MANAGER')
        )
        AND ut.netToOwnerAmount IS NOT NULL
        ORDER BY ut.transactionDate DESC
    """)
    List<UnifiedTransaction> findByOwnerIdWithNetToOwner(@Param("ownerId") Long ownerId);

    /**
     * Find by ID
     */
    @Query("SELECT ut FROM UnifiedTransaction ut WHERE ut.id = :id")
    java.util.Optional<UnifiedTransaction> findByIdWithDetails(@Param("id") Long id);
}

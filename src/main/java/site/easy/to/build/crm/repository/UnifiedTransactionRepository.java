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
     * Find transactions by property ID and flow direction (for expense document service)
     */
    List<UnifiedTransaction> findByPropertyIdAndFlowDirection(Long propertyId, UnifiedTransaction.FlowDirection flowDirection);

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
     * Find ALL transactions by flow direction (no date filter)
     * Used for SXSSF streaming statements where opening balance is calculated via Excel formulas
     */
    List<UnifiedTransaction> findByFlowDirection(UnifiedTransaction.FlowDirection flowDirection);

    /**
     * Find ALL transactions for properties owned by customer, filtered by flow direction (no date filter)
     * Used for SXSSF streaming statements where opening balance is calculated via Excel formulas
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
        AND ut.flowDirection = :flowDirection
        ORDER BY ut.transactionDate, ut.id
    """)
    List<UnifiedTransaction> findByCustomerOwnedPropertiesAndFlowDirection(
        @Param("customerId") Long customerId,
        @Param("flowDirection") UnifiedTransaction.FlowDirection flowDirection
    );

    /**
     * Find transactions by invoice ID and flow direction (no date filter)
     * Used for batch-based statement generation where filtering is done by paidDate
     */
    List<UnifiedTransaction> findByInvoiceIdAndFlowDirection(
        Long invoiceId,
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
     * Find transactions by invoice ID, date range, flow direction, and paypropDataSource
     * Use this for service charge income extraction - filters to only INCOMING_PAYMENT (actual tenant payments)
     * This matches how regular properties filter income and avoids picking up internal allocations
     */
    List<UnifiedTransaction> findByInvoiceIdAndTransactionDateBetweenAndFlowDirectionAndPaypropDataSource(
        Long invoiceId,
        LocalDate startDate,
        LocalDate endDate,
        UnifiedTransaction.FlowDirection flowDirection,
        String paypropDataSource
    );

    /**
     * Find transactions by invoice ID, flow direction, and paypropDataSource (no date filter)
     * Use this for service charge opening balance calculations - filters to only INCOMING_PAYMENT
     */
    List<UnifiedTransaction> findByInvoiceIdAndFlowDirectionAndPaypropDataSource(
        Long invoiceId,
        UnifiedTransaction.FlowDirection flowDirection,
        String paypropDataSource
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

    // ===== BLOCK PROPERTY QUERIES =====

    /**
     * Get total OUTGOING expenses for a block property within a date range.
     * This queries unified_transactions directly (where PayProp expenses are stored).
     *
     * @param blockPropertyId The ID of the block property
     * @param startDate Period start date
     * @param endDate Period end date
     * @return Sum of expenses (negative amounts converted to positive)
     */
    @Query(value = """
        SELECT COALESCE(SUM(ABS(ut.amount)), 0)
        FROM unified_transactions ut
        WHERE ut.property_id = :blockPropertyId
          AND ut.flow_direction = 'OUTGOING'
          AND ut.transaction_date >= :startDate
          AND ut.transaction_date <= :endDate
    """, nativeQuery = true)
    java.math.BigDecimal getBlockPropertyExpenses(
        @Param("blockPropertyId") Long blockPropertyId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Get total OUTGOING expenses for a block property by name within a date range.
     * Matches block property by name pattern (e.g., "Boden House" matches "Boden House Block - Block Property").
     *
     * @param blockName The name pattern of the block (e.g., "Boden House")
     * @param startDate Period start date
     * @param endDate Period end date
     * @return Sum of expenses (negative amounts converted to positive)
     */
    @Query(value = """
        SELECT COALESCE(SUM(ABS(ut.amount)), 0)
        FROM unified_transactions ut
        JOIN properties p ON ut.property_id = p.id
        WHERE (p.is_block_property = 1 OR p.property_type = 'BLOCK')
          AND p.property_name LIKE CONCAT('%', :blockName, '%')
          AND ut.flow_direction = 'OUTGOING'
          AND ut.transaction_date >= :startDate
          AND ut.transaction_date <= :endDate
    """, nativeQuery = true)
    java.math.BigDecimal getBlockPropertyExpensesByName(
        @Param("blockName") String blockName,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Get total INCOMING income for a block property within a date range.
     * This includes contributions from flats (money received into the block account).
     *
     * @param blockPropertyId The ID of the block property
     * @param startDate Period start date
     * @param endDate Period end date
     * @return Sum of income
     */
    @Query(value = """
        SELECT COALESCE(SUM(ut.amount), 0)
        FROM unified_transactions ut
        WHERE ut.property_id = :blockPropertyId
          AND ut.flow_direction = 'INCOMING'
          AND ut.transaction_date >= :startDate
          AND ut.transaction_date <= :endDate
    """, nativeQuery = true)
    java.math.BigDecimal getBlockPropertyIncome(
        @Param("blockPropertyId") Long blockPropertyId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Get total INCOMING income for a block property by name within a date range.
     *
     * @param blockName The name pattern of the block
     * @param startDate Period start date
     * @param endDate Period end date
     * @return Sum of income
     */
    @Query(value = """
        SELECT COALESCE(SUM(ut.amount), 0)
        FROM unified_transactions ut
        JOIN properties p ON ut.property_id = p.id
        WHERE (p.is_block_property = 1 OR p.property_type = 'BLOCK')
          AND p.property_name LIKE CONCAT('%', :blockName, '%')
          AND ut.flow_direction = 'INCOMING'
          AND ut.transaction_date >= :startDate
          AND ut.transaction_date <= :endDate
    """, nativeQuery = true)
    java.math.BigDecimal getBlockPropertyIncomeByName(
        @Param("blockName") String blockName,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Get list of OUTGOING transactions for a block property (for detailed expense breakdown).
     */
    @Query(value = """
        SELECT ut.*
        FROM unified_transactions ut
        JOIN properties p ON ut.property_id = p.id
        WHERE (p.is_block_property = 1 OR p.property_type = 'BLOCK')
          AND p.property_name LIKE CONCAT('%', :blockName, '%')
          AND ut.flow_direction = 'OUTGOING'
          AND ut.transaction_date >= :startDate
          AND ut.transaction_date <= :endDate
        ORDER BY ut.transaction_date
    """, nativeQuery = true)
    List<UnifiedTransaction> getBlockPropertyExpenseTransactions(
        @Param("blockName") String blockName,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    // ===== PROPERTY ACCOUNT SHEET QUERIES (FLAT STRUCTURE) =====

    /**
     * Get all PROPERTY_ACCOUNT_ALLOCATION transactions (money allocated to property account).
     * These are stored with negative amounts and INCOMING flow direction.
     * The sheet will flip the sign to show as positive IN.
     *
     * NOTE: The rebuild service sets category='PROPERTY_ACCOUNT_ALLOCATION' for global_beneficiary payments,
     * while keeping the original transaction_type='payment_to_beneficiary'. So we match on category.
     *
     * @return List of transactions where rent was allocated to property account
     */
    @Query("""
        SELECT ut FROM UnifiedTransaction ut
        WHERE ut.category = 'PROPERTY_ACCOUNT_ALLOCATION'
        ORDER BY ut.transactionDate, ut.id
    """)
    List<UnifiedTransaction> findPropertyAccountAllocations();

    /**
     * Get PROPERTY_ACCOUNT_ALLOCATION transactions within a date range.
     */
    @Query("""
        SELECT ut FROM UnifiedTransaction ut
        WHERE ut.category = 'PROPERTY_ACCOUNT_ALLOCATION'
          AND ut.transactionDate >= :startDate
          AND ut.transactionDate <= :endDate
        ORDER BY ut.transactionDate, ut.id
    """)
    List<UnifiedTransaction> findPropertyAccountAllocationsByDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Get ALL OUTGOING transactions for block properties (expenses paid from property accounts).
     * Used for PROPERTY_ACCOUNT sheet flat structure.
     * Excludes £0 amounts (agency fee placeholders with no actual value).
     */
    @Query(value = """
        SELECT ut.*
        FROM unified_transactions ut
        JOIN properties p ON ut.property_id = p.id
        WHERE (p.is_block_property = 1 OR p.property_type = 'BLOCK')
          AND ut.flow_direction = 'OUTGOING'
          AND ut.amount <> 0
        ORDER BY ut.transaction_date, ut.id
    """, nativeQuery = true)
    List<UnifiedTransaction> findAllBlockPropertyExpenses();

    /**
     * Get OUTGOING transactions for block properties within a date range.
     * Excludes £0 amounts (agency fee placeholders with no actual value).
     */
    @Query(value = """
        SELECT ut.*
        FROM unified_transactions ut
        JOIN properties p ON ut.property_id = p.id
        WHERE (p.is_block_property = 1 OR p.property_type = 'BLOCK')
          AND ut.flow_direction = 'OUTGOING'
          AND ut.amount <> 0
          AND ut.transaction_date >= :startDate
          AND ut.transaction_date <= :endDate
        ORDER BY ut.transaction_date, ut.id
    """, nativeQuery = true)
    List<UnifiedTransaction> findBlockPropertyExpensesByDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Get disbursements TO block property accounts (transfers from individual flats).
     * These are OUTGOING from the flat but represent money going INTO the block property account.
     * Used to show the "IN" side of block property contributions on the PROPERTY_ACCOUNT sheet.
     *
     * @param blockPropertyName Pattern to match block property name (e.g., '%BODEN HOUSE BLOCK%')
     * @return List of transactions representing disbursements to block property
     */
    @Query(value = """
        SELECT ut.*
        FROM unified_transactions ut
        WHERE ut.flow_direction = 'OUTGOING'
          AND ut.description LIKE :blockPropertyName
          AND ut.amount > 0
        ORDER BY ut.transaction_date, ut.id
    """, nativeQuery = true)
    List<UnifiedTransaction> findDisbursementsToBlockProperty(
        @Param("blockPropertyName") String blockPropertyName
    );

    /**
     * Get all disbursements TO any block property accounts.
     * These are payments from individual flats to their associated block property accounts.
     * The description contains the block property name (e.g., "BODEN HOUSE BLOCK PROPERTY").
     */
    @Query(value = """
        SELECT ut.*
        FROM unified_transactions ut
        JOIN properties p ON ut.property_id = p.id
        JOIN blocks b ON p.block_id = b.id
        JOIN properties bp ON b.block_property_id = bp.id
        WHERE ut.flow_direction = 'OUTGOING'
          AND ut.transaction_type = 'payment_to_beneficiary'
          AND UPPER(ut.description) LIKE CONCAT('%', UPPER(bp.property_name), '%')
          AND ut.amount > 0
        ORDER BY ut.transaction_date, ut.id
    """, nativeQuery = true)
    List<UnifiedTransaction> findAllDisbursementsToBlockProperties();
}

// FinancialTransactionRepository.java - Repository for financial transactions
package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.FinancialTransaction;
import site.easy.to.build.crm.entity.Payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {
    
    // ===== PAYPROP INTEGRATION QUERIES =====
    
    /**
     * Find transaction by PayProp transaction ID (for sync deduplication)
     */
    FinancialTransaction findByPayPropTransactionId(String payPropTransactionId);
    
    /**
     * Check if transaction exists by PayProp ID
     */
    boolean existsByPayPropTransactionId(String payPropTransactionId);
    
    /**
     * Find all transactions with PayProp IDs (synced transactions)
     */
    List<FinancialTransaction> findByPayPropTransactionIdIsNotNull();
    
    /**
     * Find transactions missing PayProp IDs (need sync)
     */
    List<FinancialTransaction> findByPayPropTransactionIdIsNull();
    
    // ===== DATE-BASED QUERIES =====
    
    /**
     * Find transactions between dates (for period analysis)
     */
    List<FinancialTransaction> findByTransactionDateBetween(LocalDate fromDate, LocalDate toDate);
    
    /**
     * Find transactions by specific date
     */
    List<FinancialTransaction> findByTransactionDate(LocalDate transactionDate);
    
    /**
     * Find transactions from a specific date onwards
     */
    List<FinancialTransaction> findByTransactionDateGreaterThanEqual(LocalDate fromDate);
    
    /**
     * Find recent transactions (last N days)
     */
    @Query("SELECT ft FROM FinancialTransaction ft WHERE ft.transactionDate >= :cutoffDate ORDER BY ft.transactionDate DESC")
    List<FinancialTransaction> findRecentTransactions(@Param("cutoffDate") LocalDate cutoffDate);
    
    /**
     * Find transactions after a date with specific type
     */
    List<FinancialTransaction> findByTransactionDateAfterAndTransactionType(LocalDate date, String transactionType);
    
    // ===== PROPERTY-BASED QUERIES =====
    
    /**
     * Find all transactions for a specific property
     */
    List<FinancialTransaction> findByPropertyId(String propertyId);
    
    /**
     * Find transactions for property within date range
     */
    List<FinancialTransaction> findByPropertyIdAndTransactionDateBetween(String propertyId, LocalDate fromDate, LocalDate toDate);
    
    /**
     * Find transactions for multiple properties
     */
    List<FinancialTransaction> findByPropertyIdIn(List<String> propertyIds);
    
    /**
     * Find transactions by property and type
     */
    List<FinancialTransaction> findByPropertyIdAndTransactionType(String propertyId, String transactionType);
    
    // ===== TENANT-BASED QUERIES =====
    
    /**
     * Find all transactions for a specific tenant
     */
    List<FinancialTransaction> findByTenantId(String tenantId);
    
    /**
     * Find tenant transactions within date range
     */
    List<FinancialTransaction> findByTenantIdAndTransactionDateBetween(String tenantId, LocalDate fromDate, LocalDate toDate);
    
    /**
     * Find all rent payments (transactions with tenant)
     */
    List<FinancialTransaction> findByTenantIdIsNotNull();
    
    // ===== TRANSACTION TYPE QUERIES =====
    
    /**
     * Find transactions by type
     */
    List<FinancialTransaction> findByTransactionType(String transactionType);
    
    /**
     * Find invoice transactions (rent payments)
     */
    @Query("SELECT ft FROM FinancialTransaction ft WHERE ft.transactionType = 'invoice'")
    List<FinancialTransaction> findInvoiceTransactions();
    
    /**
     * Find credit note transactions
     */
    @Query("SELECT ft FROM FinancialTransaction ft WHERE ft.transactionType = 'credit_note'")
    List<FinancialTransaction> findCreditNoteTransactions();
    
    /**
     * Find debit note transactions
     */
    @Query("SELECT ft FROM FinancialTransaction ft WHERE ft.transactionType = 'debit_note'")
    List<FinancialTransaction> findDebitNoteTransactions();
    
    // ===== AMOUNT-BASED QUERIES =====
    
    /**
     * Find transactions above a certain amount
     */
    List<FinancialTransaction> findByAmountGreaterThan(BigDecimal amount);
    
    /**
     * Find transactions within amount range
     */
    List<FinancialTransaction> findByAmountBetween(BigDecimal minAmount, BigDecimal maxAmount);
    
    /**
     * Find large transactions (for audit purposes)
     */
    @Query("SELECT ft FROM FinancialTransaction ft WHERE ft.amount >= :threshold ORDER BY ft.amount DESC")
    List<FinancialTransaction> findLargeTransactions(@Param("threshold") BigDecimal threshold);
    
    /**
     * Find transactions with outstanding amounts
     */
    @Query("SELECT ft FROM FinancialTransaction ft WHERE ft.amount > COALESCE(ft.matchedAmount, 0)")
    List<FinancialTransaction> findTransactionsWithOutstanding();
    
    // ===== COMMISSION-BASED QUERIES =====
    
    /**
     * Find transactions with calculated commissions
     */
    List<FinancialTransaction> findByCommissionAmountIsNotNull();
    
    /**
     * Find transactions without calculated commissions
     */
    List<FinancialTransaction> findByCommissionAmountIsNull();
    
    /**
     * Find transactions with specific commission rate
     */
    List<FinancialTransaction> findByCommissionRate(BigDecimal commissionRate);
    
    // ===== ANALYTICAL QUERIES =====
    
    /**
     * Sum total transaction amounts for date range
     */
    @Query("SELECT COALESCE(SUM(ft.amount), 0) FROM FinancialTransaction ft WHERE ft.transactionDate BETWEEN :fromDate AND :toDate")
    BigDecimal sumAmountByDateRange(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
    
    /**
     * Sum amounts by date range and transaction type
     */
    @Query("SELECT COALESCE(SUM(ft.amount), 0) FROM FinancialTransaction ft WHERE ft.transactionDate BETWEEN :fromDate AND :toDate AND ft.transactionType = :type")
    BigDecimal sumAmountByDateRangeAndType(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate, @Param("type") String type);
    
    /**
     * Sum commission amounts for date range
     */
    @Query("SELECT COALESCE(SUM(ft.commissionAmount), 0) FROM FinancialTransaction ft WHERE ft.transactionDate BETWEEN :fromDate AND :toDate")
    BigDecimal sumCommissionByDateRange(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
    
    /**
     * Sum service fees for date range
     */
    @Query("SELECT COALESCE(SUM(ft.serviceFeeAmount), 0) FROM FinancialTransaction ft WHERE ft.transactionDate BETWEEN :fromDate AND :toDate")
    BigDecimal sumServiceFeesByDateRange(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
    
    /**
     * Sum net amounts to owners for date range
     */
    @Query("SELECT COALESCE(SUM(ft.netToOwnerAmount), 0) FROM FinancialTransaction ft WHERE ft.transactionDate BETWEEN :fromDate AND :toDate")
    BigDecimal sumNetToOwnersByDateRange(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
    
    /**
     * Sum transactions by property and date range
     */
    @Query("SELECT COALESCE(SUM(ft.amount), 0) FROM FinancialTransaction ft WHERE ft.propertyId = :propertyId AND ft.transactionDate BETWEEN :fromDate AND :toDate")
    BigDecimal sumAmountByPropertyAndDateRange(@Param("propertyId") String propertyId, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
    
    /**
     * Sum commissions by property and date range
     */
    @Query("SELECT COALESCE(SUM(ft.commissionAmount), 0) FROM FinancialTransaction ft WHERE ft.propertyId = :propertyId AND ft.transactionDate BETWEEN :fromDate AND :toDate")
    BigDecimal sumCommissionByPropertyAndDateRange(@Param("propertyId") String propertyId, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
    
    // ===== REPORTING QUERIES =====
    
    /**
     * Get transaction summary by property for date range
     */
    @Query("SELECT ft.propertyId, ft.propertyName, COUNT(ft), SUM(ft.amount), SUM(ft.commissionAmount) " +
           "FROM FinancialTransaction ft WHERE ft.transactionDate BETWEEN :fromDate AND :toDate " +
           "GROUP BY ft.propertyId, ft.propertyName")
    List<Object[]> getTransactionSummaryByProperty(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
    
    /**
     * Get transaction summary by tenant for date range
     */
    @Query("SELECT ft.tenantId, ft.tenantName, COUNT(ft), SUM(ft.amount) " +
           "FROM FinancialTransaction ft WHERE ft.transactionDate BETWEEN :fromDate AND :toDate AND ft.tenantId IS NOT NULL " +
           "GROUP BY ft.tenantId, ft.tenantName")
    List<Object[]> getTransactionSummaryByTenant(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
    
    /**
     * Get monthly transaction totals
     */
    @Query("SELECT YEAR(ft.transactionDate), MONTH(ft.transactionDate), SUM(ft.amount), SUM(ft.commissionAmount) " +
           "FROM FinancialTransaction ft WHERE ft.transactionDate BETWEEN :fromDate AND :toDate " +
           "GROUP BY YEAR(ft.transactionDate), MONTH(ft.transactionDate) " +
           "ORDER BY YEAR(ft.transactionDate), MONTH(ft.transactionDate)")
    List<Object[]> getMonthlyTransactionTotals(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
    
    /**
     * Get transaction count by type
     */
    @Query("SELECT ft.transactionType, COUNT(ft) FROM FinancialTransaction ft GROUP BY ft.transactionType")
    List<Object[]> getTransactionCountByType();
    
    // ===== SEARCH AND FILTERING =====
    
    /**
     * Search transactions by description
     */
    @Query("SELECT ft FROM FinancialTransaction ft WHERE LOWER(ft.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<FinancialTransaction> searchByDescription(@Param("searchTerm") String searchTerm);
    
    /**
     * Search transactions by property or tenant name
     */
    @Query("SELECT ft FROM FinancialTransaction ft WHERE " +
           "LOWER(ft.propertyName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(ft.tenantName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<FinancialTransaction> searchByPropertyOrTenant(@Param("searchTerm") String searchTerm);
    
    /**
     * Complex transaction search with multiple filters
     */
    @Query("SELECT ft FROM FinancialTransaction ft WHERE " +
           "(:propertyId IS NULL OR ft.propertyId = :propertyId) AND " +
           "(:tenantId IS NULL OR ft.tenantId = :tenantId) AND " +
           "(:transactionType IS NULL OR ft.transactionType = :transactionType) AND " +
           "(:startDate IS NULL OR ft.transactionDate >= :startDate) AND " +
           "(:endDate IS NULL OR ft.transactionDate <= :endDate) AND " +
           "(:minAmount IS NULL OR ft.amount >= :minAmount) AND " +
           "(:maxAmount IS NULL OR ft.amount <= :maxAmount)")
    List<FinancialTransaction> searchTransactions(@Param("propertyId") String propertyId,
                                                 @Param("tenantId") String tenantId,
                                                 @Param("transactionType") String transactionType,
                                                 @Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate,
                                                 @Param("minAmount") BigDecimal minAmount,
                                                 @Param("maxAmount") BigDecimal maxAmount,
                                                 Pageable pageable);
    
    // ===== PAGINATION SUPPORT =====
    
    /**
     * Find recent transactions with pagination
     */
    List<FinancialTransaction> findByOrderByTransactionDateDesc(Pageable pageable);
    
    /**
     * Find transactions by property with pagination - FIXED METHOD SIGNATURE
     */
    List<FinancialTransaction> findByPropertyIdOrderByTransactionDateDesc(String propertyId, Pageable pageable);

    /**
     * Find transactions by tenant with pagination
     */
    List<FinancialTransaction> findByTenantIdOrderByTransactionDateDesc(String tenantId, Pageable pageable);
    
    // ===== MAINTENANCE QUERIES =====
    
    /**
     * Find duplicate transactions (same PayProp ID)
     */
    @Query("SELECT ft1 FROM FinancialTransaction ft1 WHERE EXISTS " +
           "(SELECT ft2 FROM FinancialTransaction ft2 WHERE ft2.payPropTransactionId = ft1.payPropTransactionId " +
           "AND ft2.id != ft1.id)")
    List<FinancialTransaction> findDuplicateTransactions();
    
    /**
     * Count total transactions
     */
    @Query("SELECT COUNT(ft) FROM FinancialTransaction ft")
    long countTotalTransactions();
    
    /**
     * Count transactions by date range
     */
    @Query("SELECT COUNT(ft) FROM FinancialTransaction ft WHERE ft.transactionDate BETWEEN :fromDate AND :toDate")
    long countTransactionsByDateRange(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
    
    // ===== NEW DATA SOURCE QUERIES =====
    
    /**
     * Check if transaction exists by PayProp ID and data source
     */
    boolean existsByPayPropTransactionIdAndDataSource(String payPropTransactionId, String dataSource);
    
    /**
     * Find transactions by data source
     */
    List<FinancialTransaction> findByDataSource(String dataSource);
    
    /**
     * Find transactions by property and data source
     */
    List<FinancialTransaction> findByPropertyIdAndDataSource(String propertyId, String dataSource);
    
    /**
     * Find transactions by property, data source and date range
     */
    @Query("SELECT ft FROM FinancialTransaction ft WHERE ft.propertyId = :propertyId AND ft.dataSource = :dataSource AND ft.transactionDate BETWEEN :fromDate AND :toDate")
    List<FinancialTransaction> findByPropertyIdAndDataSourceAndDateRange(@Param("propertyId") String propertyId, @Param("dataSource") String dataSource, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
    
    // ===== INSTRUCTION VS ACTUAL QUERIES =====
    
    /**
     * Find all instruction transactions
     */
    List<FinancialTransaction> findByIsInstructionTrue();
    
    /**
     * Find all actual transactions
     */
    List<FinancialTransaction> findByIsActualTransactionTrue();
    
    /**
     * Find transactions linked to an instruction
     */
    @Query("SELECT ft FROM FinancialTransaction ft WHERE ft.instructionId = :instructionId")
    List<FinancialTransaction> findByInstructionId(@Param("instructionId") String instructionId);
    
    /**
     * Find instruction transactions by property and date range
     */
    @Query("SELECT ft FROM FinancialTransaction ft WHERE ft.propertyId = :propertyId AND ft.isInstruction = true AND ft.transactionDate BETWEEN :fromDate AND :toDate")
    List<FinancialTransaction> findInstructionsByPropertyAndDateRange(@Param("propertyId") String propertyId, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
    
    /**
     * Find actual transactions by property and date range
     */
    @Query("SELECT ft FROM FinancialTransaction ft WHERE ft.propertyId = :propertyId AND ft.isActualTransaction = true AND ft.transactionDate BETWEEN :fromDate AND :toDate")
    List<FinancialTransaction> findActualsByPropertyAndDateRange(@Param("propertyId") String propertyId, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
    
    // ===== COMPARISON QUERIES =====
    
    /**
     * Get instructed vs actual summary by property
     */
    @Query("SELECT i.propertyId, i.propertyName, " +
           "SUM(CASE WHEN i.isInstruction = true THEN i.amount ELSE 0 END) as instructed_total, " +
           "SUM(CASE WHEN i.isActualTransaction = true THEN i.amount ELSE 0 END) as actual_total, " +
           "SUM(CASE WHEN i.isInstruction = true THEN i.calculatedCommissionAmount ELSE 0 END) as instructed_commission, " +
           "SUM(CASE WHEN i.isActualTransaction = true THEN i.actualCommissionAmount ELSE 0 END) as actual_commission " +
           "FROM FinancialTransaction i WHERE i.transactionDate BETWEEN :fromDate AND :toDate " +
           "GROUP BY i.propertyId, i.propertyName")
    List<Object[]> getInstructedVsActualSummary(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
    
    /**
     * Get deposit transactions (should have no commission)
     */
    @Query("SELECT ft FROM FinancialTransaction ft WHERE ft.depositId IS NOT NULL OR LOWER(ft.categoryName) LIKE '%deposit%'")
    List<FinancialTransaction> findDepositTransactions();
    
    /**
     * Find commission payments only
     */
    @Query("SELECT ft FROM FinancialTransaction ft WHERE ft.dataSource = 'COMMISSION_PAYMENT'")
    List<FinancialTransaction> findCommissionPayments();
    
    /**
     * Find ICDN actual transactions
     */
    @Query("SELECT ft FROM FinancialTransaction ft WHERE ft.dataSource = 'ICDN_ACTUAL'")
    List<FinancialTransaction> findICDNTransactions();
    
    /**
     * Find payment instructions
     */
    @Query("SELECT ft FROM FinancialTransaction ft WHERE ft.dataSource = 'PAYMENT_INSTRUCTION'")
    List<FinancialTransaction> findPaymentInstructions();

    // ===== STATEMENT GENERATION QUERIES =====
    
    /**
     * Sum outstanding amounts for a property in date range
     */
    @Query("SELECT COALESCE(SUM(ft.amount), 0) FROM FinancialTransaction ft " +
           "WHERE ft.propertyId = :propertyId " +
           "AND ft.transactionDate BETWEEN :fromDate AND :toDate " +
           "AND (ft.transactionType = 'OUTSTANDING' OR ft.amount > COALESCE(ft.matchedAmount, 0))")
    BigDecimal sumOutstandingForProperty(@Param("propertyId") String propertyId, 
                                       @Param("fromDate") LocalDate fromDate, 
                                       @Param("toDate") LocalDate toDate);

    /**
     * Sum expenses for a property in date range
     */
    @Query("SELECT COALESCE(SUM(ft.amount), 0) FROM FinancialTransaction ft " +
           "WHERE ft.propertyId = :propertyId " +
           "AND ft.transactionDate BETWEEN :fromDate AND :toDate " +
           "AND (ft.transactionType LIKE '%EXPENSE%' OR ft.categoryName LIKE '%Maintenance%' OR ft.categoryName LIKE '%Repair%')")
    BigDecimal sumExpensesForProperty(@Param("propertyId") String propertyId, 
                                    @Param("fromDate") LocalDate fromDate, 
                                    @Param("toDate") LocalDate toDate);

    /**
     * Sum payments by tenant in date range
     */
    @Query("SELECT COALESCE(SUM(ft.amount), 0) FROM FinancialTransaction ft " +
           "WHERE ft.tenantId = :tenantId " +
           "AND ft.transactionDate BETWEEN :fromDate AND :toDate " +
           "AND (ft.transactionType = 'PAYMENT' OR ft.transactionType = 'invoice')")
    BigDecimal sumPaymentsByTenant(@Param("tenantId") String tenantId, 
                                 @Param("fromDate") LocalDate fromDate, 
                                 @Param("toDate") LocalDate toDate);

    /**
     * Find latest payment date for a property
     */
    @Query("SELECT MAX(ft.transactionDate) FROM FinancialTransaction ft " +
           "WHERE ft.propertyId = :propertyId " +
           "AND ft.transactionDate BETWEEN :fromDate AND :toDate " +
           "AND (ft.transactionType = 'PAYMENT' OR ft.transactionType = 'invoice')")
    LocalDate findLatestPaymentDateForProperty(@Param("propertyId") String propertyId, 
                                             @Param("fromDate") LocalDate fromDate, 
                                             @Param("toDate") LocalDate toDate);

    /**
     * Find financial transactions for property in date range (for statement generation)
     */
    @Query("SELECT ft FROM FinancialTransaction ft " +
           "WHERE ft.propertyId = :propertyId " +
           "AND ft.transactionDate BETWEEN :fromDate AND :toDate " +
           "ORDER BY ft.transactionDate DESC")
    List<FinancialTransaction> findByPropertyAndDateRange(@Param("propertyId") String propertyId, 
                                                        @Param("fromDate") LocalDate fromDate, 
                                                        @Param("toDate") LocalDate toDate);

    /**
     * Find transactions by data source and transaction type
     */
    List<FinancialTransaction> findByDataSourceAndTransactionType(String dataSource, String transactionType);

    /**
     * Find transaction by PayProp ID and data source
     */
    FinancialTransaction findByPayPropTransactionIdAndDataSource(String payPropTransactionId, String dataSource);

    
    /**
    * ✅ NEW: Find transactions by property, transaction type and date range
    */
    @Query("SELECT ft FROM FinancialTransaction ft WHERE ft.propertyId = :propertyId AND ft.transactionType = :transactionType AND ft.transactionDate BETWEEN :startDate AND :endDate")
    List<FinancialTransaction> findByPropertyIdAndTransactionTypeAndTransactionDateBetween(@Param("propertyId") String propertyId, @Param("transactionType") String transactionType, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    /**
    * ✅ NEW: Find transactions by property, transaction type and specific date
    */
    List<FinancialTransaction> findByPropertyIdAndTransactionTypeAndTransactionDate(String propertyId, String transactionType, LocalDate transactionDate);

    // ===== PROPERTY OWNER SPECIFIC QUERIES =====
    
    /**
     * Get financial summary for property owner's properties via portfolio assignments
     */
    @Query(value = "SELECT " +
           "SUM(CASE WHEN fs.total_rent IS NOT NULL THEN fs.total_rent ELSE 0 END), " +
           "SUM(CASE WHEN fs.total_commission IS NOT NULL THEN fs.total_commission ELSE 0 END), " +
           "SUM(CASE WHEN fs.total_net_to_owner IS NOT NULL THEN fs.total_net_to_owner ELSE 0 END), " +
           "SUM(CASE WHEN fs.transaction_count IS NOT NULL THEN fs.transaction_count ELSE 0 END) " +
           "FROM customers c " +
           "JOIN portfolios po ON c.customer_id = po.property_owner_id " +
           "JOIN property_portfolio_assignments ppa ON po.id = ppa.portfolio_id " +
           "JOIN properties pr ON ppa.property_id = pr.id " +
           "JOIN financial_summary_by_property fs ON pr.payprop_id = fs.property_id " +
           "WHERE c.customer_id = :customerId AND ppa.is_active = 1",
           nativeQuery = true)
    Object[] getPropertyOwnerFinancialSummary(@Param("customerId") Long customerId);
    
    /**
     * Get recent transactions for property owner's properties - ENHANCED: Includes incoming tenant payments
     * Combines financial_transactions (outgoing) with payprop_report_all_payments (incoming)
     */
    @Query(value = "SELECT ft.* FROM financial_transactions ft " +
           "WHERE ft.property_id IN (" +
           "  SELECT pr.payprop_id FROM properties pr " +
           "  JOIN property_portfolio_assignments ppa ON pr.id = ppa.property_id " +
           "  JOIN portfolios po ON ppa.portfolio_id = po.id " +
           "  WHERE po.property_owner_id = :customerId AND ppa.is_active = 1" +
           ") AND ft.transaction_date >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH) " +
           "ORDER BY ft.transaction_date DESC",
           nativeQuery = true)
    List<Object[]> getPropertyOwnerRecentTransactionsRaw(@Param("customerId") Long customerId, Pageable pageable);
    
    /**
     * Get recent transactions for property owner's properties - FIXED: Using native query to avoid entity mapping issues
     */
    @Query(value = "SELECT ft.* FROM financial_transactions ft " +
           "WHERE ft.property_id IN (" +
           "  SELECT pr.payprop_id FROM properties pr " +
           "  JOIN property_portfolio_assignments ppa ON pr.id = ppa.property_id " +
           "  JOIN portfolios po ON ppa.portfolio_id = po.id " +
           "  WHERE po.property_owner_id = :customerId AND ppa.is_active = 1" +
           ") AND ft.transaction_date >= :dateLimit " +
           "ORDER BY ft.transaction_date DESC",
           nativeQuery = true)
    List<FinancialTransaction> getPropertyOwnerRecentTransactions(@Param("customerId") Long customerId, 
                                                                @Param("dateLimit") LocalDate dateLimit,
                                                                Pageable pageable);
    
    /**
     * Get all transactions for property owner statements - ENHANCED: Includes incoming payments with batch IDs
     * Used by GoogleSheetsStatementService for complete transaction history with batch reconciliation
     */
    @Query(value = "SELECT ft.* FROM financial_transactions ft " +
           "WHERE ft.property_id = :propertyId " +
           "  AND ft.transaction_date BETWEEN :fromDate AND :toDate " +
           "ORDER BY ft.transaction_date DESC",
           nativeQuery = true)
    List<FinancialTransaction> findPropertyTransactionsForStatement(@Param("propertyId") String propertyId, 
                                                                   @Param("fromDate") LocalDate fromDate, 
                                                                   @Param("toDate") LocalDate toDate);
    
    /**
     * Get property-level financial breakdown for property owner
     */
    @Query(value = "SELECT pr.property_name, pr.payprop_id, " +
           "COALESCE(fs.total_rent, 0), COALESCE(fs.total_commission, 0), " +
           "COALESCE(fs.total_net_to_owner, 0), COALESCE(fs.transaction_count, 0) " +
           "FROM customers c " +
           "JOIN portfolios po ON c.customer_id = po.property_owner_id " +
           "JOIN property_portfolio_assignments ppa ON po.id = ppa.portfolio_id " +
           "JOIN properties pr ON ppa.property_id = pr.id " +
           "LEFT JOIN financial_summary_by_property fs ON pr.payprop_id = fs.property_id " +
           "WHERE c.customer_id = :customerId AND ppa.is_active = 1 " +
           "ORDER BY pr.property_name",
           nativeQuery = true)
    List<Object[]> getPropertyOwnerPropertyBreakdown(@Param("customerId") Long customerId);

    /**
     * Get financial summary for a specific portfolio
     */
    @Query(value = "SELECT " +
           "SUM(CASE WHEN fs.total_rent IS NOT NULL THEN fs.total_rent ELSE 0 END), " +
           "SUM(CASE WHEN fs.total_commission IS NOT NULL THEN fs.total_commission ELSE 0 END), " +
           "SUM(CASE WHEN fs.total_net_to_owner IS NOT NULL THEN fs.total_net_to_owner ELSE 0 END), " +
           "SUM(CASE WHEN fs.transaction_count IS NOT NULL THEN fs.transaction_count ELSE 0 END) " +
           "FROM property_portfolio_assignments ppa " +
           "JOIN properties pr ON ppa.property_id = pr.id " +
           "JOIN financial_summary_by_property fs ON pr.payprop_id = fs.property_id " +
           "WHERE ppa.portfolio_id = :portfolioId AND ppa.is_active = 1",
           nativeQuery = true)
    Object[] getPortfolioFinancialSummary(@Param("portfolioId") Long portfolioId);

    /**
     * Get property breakdown for a specific portfolio
     */
    @Query(value = "SELECT pr.property_name, pr.payprop_id, " +
           "COALESCE(fs.total_rent, 0), COALESCE(fs.total_commission, 0), " +
           "COALESCE(fs.total_net_to_owner, 0), COALESCE(fs.transaction_count, 0) " +
           "FROM property_portfolio_assignments ppa " +
           "JOIN properties pr ON ppa.property_id = pr.id " +
           "LEFT JOIN financial_summary_by_property fs ON pr.payprop_id = fs.property_id " +
           "WHERE ppa.portfolio_id = :portfolioId AND ppa.is_active = 1 " +
           "ORDER BY pr.property_name",
           nativeQuery = true)
    List<Object[]> getPortfolioPropertyBreakdown(@Param("portfolioId") Long portfolioId);

    /**
     * FALLBACK: Get financial summary for property owner directly (without portfolio requirement)
     * Used when portfolio-based query returns no results
     */
    @Query(value = "SELECT " +
           "SUM(CASE WHEN fs.total_rent IS NOT NULL THEN fs.total_rent ELSE 0 END), " +
           "SUM(CASE WHEN fs.total_commission IS NOT NULL THEN fs.total_commission ELSE 0 END), " +
           "SUM(CASE WHEN fs.total_net_to_owner IS NOT NULL THEN fs.total_net_to_owner ELSE 0 END), " +
           "SUM(CASE WHEN fs.transaction_count IS NOT NULL THEN fs.transaction_count ELSE 0 END) " +
           "FROM customer_property_assignments cpa " +
           "JOIN properties pr ON cpa.property_id = pr.id " +
           "JOIN financial_summary_by_property fs ON pr.payprop_id = fs.property_id " +
           "WHERE cpa.customer_id = :customerId AND (cpa.end_date IS NULL OR cpa.end_date > CURDATE())",
           nativeQuery = true)
    Object[] getPropertyOwnerFinancialSummaryDirect(@Param("customerId") Long customerId);

    /**
     * FALLBACK: Get property breakdown for property owner directly (without portfolio requirement)
     * Used when portfolio-based query returns no results
     */
    @Query(value = "SELECT pr.property_name, pr.payprop_id, " +
           "COALESCE(fs.total_rent, 0), COALESCE(fs.total_commission, 0), " +
           "COALESCE(fs.total_net_to_owner, 0), COALESCE(fs.transaction_count, 0) " +
           "FROM customer_property_assignments cpa " +
           "JOIN properties pr ON cpa.property_id = pr.id " +
           "LEFT JOIN financial_summary_by_property fs ON pr.payprop_id = fs.property_id " +
           "WHERE cpa.customer_id = :customerId AND (cpa.end_date IS NULL OR cpa.end_date > CURDATE()) " +
           "ORDER BY pr.property_name",
           nativeQuery = true)
    List<Object[]> getPropertyOwnerPropertyBreakdownDirect(@Param("customerId") Long customerId);
}
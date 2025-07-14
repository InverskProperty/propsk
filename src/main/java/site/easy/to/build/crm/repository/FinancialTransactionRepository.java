// FinancialTransactionRepository.java - Repository for financial transactions
package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.FinancialTransaction;

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
    Optional<FinancialTransaction> findByPayPropTransactionId(String payPropTransactionId);
    
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
     * Find transactions by property with pagination
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
}
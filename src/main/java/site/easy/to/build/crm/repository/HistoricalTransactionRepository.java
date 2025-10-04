package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.HistoricalTransaction;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.HistoricalTransaction.TransactionType;
import site.easy.to.build.crm.entity.HistoricalTransaction.TransactionSource;
import site.easy.to.build.crm.entity.HistoricalTransaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Historical Transaction Repository - Data access layer for historical financial data
 */
@Repository
public interface HistoricalTransactionRepository extends JpaRepository<HistoricalTransaction, Long> {
    
    // ===== BASIC FINDERS =====
    
    /**
     * Find transactions by property
     */
    List<HistoricalTransaction> findByProperty(Property property);
    
    /**
     * Find transactions by customer
     */
    List<HistoricalTransaction> findByCustomer(Customer customer);
    
    /**
     * Find transactions by property and customer combination
     */
    List<HistoricalTransaction> findByPropertyAndCustomer(Property property, Customer customer);
    
    /**
     * Find transactions by import batch ID
     */
    List<HistoricalTransaction> findByImportBatchId(String batchId);
    
    // ===== DATE RANGE FINDERS =====
    
    /**
     * Find transactions within date range
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE ht.transactionDate BETWEEN :startDate AND :endDate " +
           "AND ht.status = 'active' ORDER BY ht.transactionDate DESC")
    List<HistoricalTransaction> findByDateRange(@Param("startDate") LocalDate startDate, 
                                              @Param("endDate") LocalDate endDate);
    
    /**
     * Find transactions for a property within date range
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE ht.property = :property " +
           "AND ht.transactionDate BETWEEN :startDate AND :endDate " +
           "AND ht.status = 'active' ORDER BY ht.transactionDate DESC")
    List<HistoricalTransaction> findByPropertyAndDateRange(@Param("property") Property property,
                                                         @Param("startDate") LocalDate startDate,
                                                         @Param("endDate") LocalDate endDate);
    
    /**
     * Find transactions for a customer within date range
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE ht.customer = :customer " +
           "AND ht.transactionDate BETWEEN :startDate AND :endDate " +
           "AND ht.status = 'active' ORDER BY ht.transactionDate DESC")
    List<HistoricalTransaction> findByCustomerAndDateRange(@Param("customer") Customer customer,
                                                         @Param("startDate") LocalDate startDate,
                                                         @Param("endDate") LocalDate endDate);
    
    /**
     * Find transactions for a financial year
     */
    List<HistoricalTransaction> findByFinancialYear(String financialYear);
    
    // ===== TYPE AND CATEGORY FINDERS =====
    
    /**
     * Find transactions by type
     */
    List<HistoricalTransaction> findByTransactionType(TransactionType transactionType);
    
    /**
     * Find transactions by category
     */
    List<HistoricalTransaction> findByCategory(String category);
    
    /**
     * Find transactions by category and subcategory
     */
    List<HistoricalTransaction> findByCategoryAndSubcategory(String category, String subcategory);
    
    /**
     * Find rent-related transactions
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE " +
           "(LOWER(ht.category) LIKE '%rent%' OR LOWER(ht.description) LIKE '%rent%') " +
           "AND ht.status = 'active' ORDER BY ht.transactionDate DESC")
    List<HistoricalTransaction> findRentTransactions();
    
    /**
     * Find maintenance-related transactions
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE " +
           "(LOWER(ht.category) LIKE '%maintenance%' OR LOWER(ht.category) LIKE '%repair%' " +
           "OR LOWER(ht.description) LIKE '%maintenance%' OR LOWER(ht.description) LIKE '%repair%') " +
           "AND ht.status = 'active' ORDER BY ht.transactionDate DESC")
    List<HistoricalTransaction> findMaintenanceTransactions();
    
    // ===== SOURCE AND STATUS FINDERS =====
    
    /**
     * Find transactions by source
     */
    List<HistoricalTransaction> findBySource(TransactionSource source);
    
    /**
     * Find transactions by status
     */
    List<HistoricalTransaction> findByStatus(TransactionStatus status);
    
    /**
     * Find active transactions
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE ht.status = 'active' " +
           "ORDER BY ht.transactionDate DESC")
    List<HistoricalTransaction> findActiveTransactions();
    
    /**
     * Find transactions requiring review
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE ht.status = 'pending_review' " +
           "OR ht.validated = false ORDER BY ht.createdAt DESC")
    List<HistoricalTransaction> findTransactionsRequiringReview();
    
    // ===== RECONCILIATION FINDERS =====
    
    /**
     * Find unreconciled transactions
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE ht.reconciled = false " +
           "AND ht.status = 'active' ORDER BY ht.transactionDate DESC")
    List<HistoricalTransaction> findUnreconciledTransactions();
    
    /**
     * Find unreconciled transactions for a property
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE ht.property = :property " +
           "AND ht.reconciled = false AND ht.status = 'active' " +
           "ORDER BY ht.transactionDate DESC")
    List<HistoricalTransaction> findUnreconciledTransactionsByProperty(@Param("property") Property property);
    
    /**
     * Find transactions reconciled on a specific date
     */
    List<HistoricalTransaction> findByReconciledDate(LocalDate reconciledDate);
    
    // ===== AMOUNT AND BALANCE QUERIES =====
    
    /**
     * Find transactions with amount greater than specified value
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE ABS(ht.amount) > :amount " +
           "AND ht.status = 'active' ORDER BY ABS(ht.amount) DESC")
    List<HistoricalTransaction> findTransactionsAboveAmount(@Param("amount") BigDecimal amount);
    
    /**
     * Find credit transactions (positive amounts)
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE ht.amount > 0 " +
           "AND ht.status = 'active' ORDER BY ht.transactionDate DESC")
    List<HistoricalTransaction> findCreditTransactions();
    
    /**
     * Find debit transactions (negative amounts)
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE ht.amount < 0 " +
           "AND ht.status = 'active' ORDER BY ht.transactionDate DESC")
    List<HistoricalTransaction> findDebitTransactions();
    
    // ===== SEARCH AND PAGINATION =====
    
    /**
     * Search transactions by keyword in description, counterparty, or reference
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE " +
           "(LOWER(ht.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(ht.counterpartyName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(ht.bankReference) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(ht.sourceReference) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND ht.status = 'active'")
    Page<HistoricalTransaction> searchTransactions(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * Find transactions with pagination
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE ht.status = 'active' " +
           "ORDER BY ht.transactionDate DESC, ht.createdAt DESC")
    Page<HistoricalTransaction> findActiveTransactionsWithPagination(Pageable pageable);
    
    /**
     * Find transactions by user with pagination
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE ht.createdByUser.id = :userId " +
           "ORDER BY ht.createdAt DESC")
    Page<HistoricalTransaction> findByCreatedByUserId(@Param("userId") Long userId, Pageable pageable);
    
    // ===== STATISTICAL QUERIES =====
    
    /**
     * Count transactions by type
     */
    long countByTransactionType(TransactionType transactionType);
    
    /**
     * Count transactions by source
     */
    long countBySource(TransactionSource source);
    
    /**
     * Count unreconciled transactions
     */
    @Query("SELECT COUNT(ht) FROM HistoricalTransaction ht WHERE ht.reconciled = false " +
           "AND ht.status = 'active'")
    long countUnreconciledTransactions();
    
    /**
     * Count transactions for a property
     */
    long countByProperty(Property property);
    
    /**
     * Count transactions for a customer
     */
    long countByCustomer(Customer customer);
    
    /**
     * Get total amount by transaction type
     */
    @Query("SELECT SUM(ht.amount) FROM HistoricalTransaction ht WHERE ht.transactionType = :transactionType " +
           "AND ht.status = 'active'")
    BigDecimal getTotalAmountByTransactionType(@Param("transactionType") TransactionType transactionType);
    
    /**
     * Get total amount for a property
     */
    @Query("SELECT SUM(ht.amount) FROM HistoricalTransaction ht WHERE ht.property = :property " +
           "AND ht.status = 'active'")
    BigDecimal getTotalAmountByProperty(@Param("property") Property property);
    
    /**
     * Get total amount for a customer
     */
    @Query("SELECT SUM(ht.amount) FROM HistoricalTransaction ht WHERE ht.customer = :customer " +
           "AND ht.status = 'active'")
    BigDecimal getTotalAmountByCustomer(@Param("customer") Customer customer);
    
    /**
     * Get total credits and debits within date range
     */
    @Query("SELECT " +
           "SUM(CASE WHEN ht.amount > 0 THEN ht.amount ELSE 0 END) as totalCredits, " +
           "SUM(CASE WHEN ht.amount < 0 THEN ABS(ht.amount) ELSE 0 END) as totalDebits " +
           "FROM HistoricalTransaction ht WHERE ht.transactionDate BETWEEN :startDate AND :endDate " +
           "AND ht.status = 'active'")
    Object[] getTotalCreditsAndDebits(@Param("startDate") LocalDate startDate, 
                                    @Param("endDate") LocalDate endDate);
    
    // ===== DUPLICATE DETECTION =====
    
    /**
     * Find potential duplicate transactions
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE EXISTS (" +
           "SELECT ht2 FROM HistoricalTransaction ht2 WHERE ht2.id != ht.id " +
           "AND ht2.transactionDate = ht.transactionDate " +
           "AND ht2.amount = ht.amount " +
           "AND ht2.bankReference = ht.bankReference " +
           "AND ht2.status = 'active') " +
           "AND ht.status = 'active' ORDER BY ht.transactionDate DESC, ht.amount DESC")
    List<HistoricalTransaction> findPotentialDuplicates();
    
    /**
     * Find transactions with same bank reference
     */
    List<HistoricalTransaction> findByBankReference(String bankReference);
    
    /**
     * Check if transaction exists with same key details
     */
    @Query("SELECT COUNT(ht) > 0 FROM HistoricalTransaction ht WHERE " +
           "ht.transactionDate = :transactionDate " +
           "AND ht.amount = :amount " +
           "AND ht.bankReference = :bankReference " +
           "AND ht.status = 'active'")
    boolean existsDuplicateTransaction(@Param("transactionDate") LocalDate transactionDate,
                                     @Param("amount") BigDecimal amount,
                                     @Param("bankReference") String bankReference);

    /**
     * Find duplicate transaction by comprehensive matching
     * Matches: date, amount, description, type, property (if set), customer (if set)
     * Used for import deduplication
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE " +
           "ht.transactionDate = :transactionDate " +
           "AND ht.amount = :amount " +
           "AND ht.description = :description " +
           "AND ht.transactionType = :transactionType " +
           "AND (:propertyId IS NULL OR ht.property.id = :propertyId) " +
           "AND (:customerId IS NULL OR ht.customer.id = :customerId) " +
           "ORDER BY ht.createdAt ASC")
    List<HistoricalTransaction> findDuplicateTransaction(
            @Param("transactionDate") LocalDate transactionDate,
            @Param("amount") BigDecimal amount,
            @Param("description") String description,
            @Param("transactionType") TransactionType transactionType,
            @Param("propertyId") Long propertyId,
            @Param("customerId") Long customerId);

    /**
     * Find duplicate transactions within a specific batch
     * Used for multi-paste deduplication within same batch session
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE " +
           "ht.importBatchId = :batchId " +
           "AND ht.transactionDate = :transactionDate " +
           "AND ht.amount = :amount " +
           "AND ht.description = :description " +
           "AND ht.transactionType = :transactionType " +
           "AND (:propertyId IS NULL OR ht.property.id = :propertyId) " +
           "AND (:customerId IS NULL OR ht.customer.id = :customerId)")
    List<HistoricalTransaction> findDuplicateInBatch(
            @Param("batchId") String batchId,
            @Param("transactionDate") LocalDate transactionDate,
            @Param("amount") BigDecimal amount,
            @Param("description") String description,
            @Param("transactionType") TransactionType transactionType,
            @Param("propertyId") Long propertyId,
            @Param("customerId") Long customerId);
    
    // ===== BATCH OPERATIONS =====
    
    /**
     * Find all transactions in import batches
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE ht.importBatchId IN :batchIds " +
           "ORDER BY ht.importBatchId, ht.transactionDate")
    List<HistoricalTransaction> findByImportBatchIds(@Param("batchIds") List<String> batchIds);
    
    /**
     * Count transactions by batch ID
     */
    long countByImportBatchId(String batchId);
    
    /**
     * Get distinct batch IDs
     */
    @Query("SELECT DISTINCT ht.importBatchId FROM HistoricalTransaction ht " +
           "WHERE ht.importBatchId IS NOT NULL ORDER BY ht.importBatchId DESC")
    List<String> findDistinctBatchIds();

    /**
     * Get recent batch IDs with summary info
     * Returns: batchId, count, earliest date, latest date, created timestamp
     */
    @Query("SELECT ht.importBatchId, " +
           "COUNT(ht), " +
           "MIN(ht.transactionDate), " +
           "MAX(ht.transactionDate), " +
           "MIN(ht.createdAt) " +
           "FROM HistoricalTransaction ht " +
           "WHERE ht.importBatchId IS NOT NULL " +
           "GROUP BY ht.importBatchId " +
           "ORDER BY MIN(ht.createdAt) DESC")
    List<Object[]> findRecentBatchSummaries(Pageable pageable);

    /**
     * Delete all transactions in a batch
     * Used for "Delete & Replace" functionality
     */
    void deleteByImportBatchId(String batchId);
    
    // ===== REPORTING QUERIES =====
    
    /**
     * Get monthly summary data
     */
    @Query("SELECT " +
           "YEAR(ht.transactionDate) as year, " +
           "MONTH(ht.transactionDate) as month, " +
           "ht.transactionType as transactionType, " +
           "COUNT(ht) as transactionCount, " +
           "SUM(CASE WHEN ht.amount > 0 THEN ht.amount ELSE 0 END) as totalCredits, " +
           "SUM(CASE WHEN ht.amount < 0 THEN ABS(ht.amount) ELSE 0 END) as totalDebits " +
           "FROM HistoricalTransaction ht " +
           "WHERE ht.transactionDate BETWEEN :startDate AND :endDate " +
           "AND ht.status = 'active' " +
           "GROUP BY YEAR(ht.transactionDate), MONTH(ht.transactionDate), ht.transactionType " +
           "ORDER BY year DESC, month DESC, transactionType")
    List<Object[]> getMonthlySummary(@Param("startDate") LocalDate startDate, 
                                   @Param("endDate") LocalDate endDate);
    
    /**
     * Get category breakdown
     */
    @Query("SELECT " +
           "ht.category, " +
           "COUNT(ht) as transactionCount, " +
           "SUM(ABS(ht.amount)) as totalAmount " +
           "FROM HistoricalTransaction ht " +
           "WHERE ht.status = 'active' AND ht.category IS NOT NULL " +
           "GROUP BY ht.category " +
           "ORDER BY totalAmount DESC")
    List<Object[]> getCategoryBreakdown();
    
    // ===== VALIDATION AND CLEANUP =====
    
    /**
     * Find orphaned transactions (no property or customer link)
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE ht.property IS NULL " +
           "AND ht.customer IS NULL AND ht.status = 'active'")
    List<HistoricalTransaction> findOrphanedTransactions();
    
    /**
     * Find transactions without categories
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE " +
           "(ht.category IS NULL OR ht.category = '') AND ht.status = 'active'")
    List<HistoricalTransaction> findTransactionsWithoutCategory();

    // ===== PAYPROP INTEGRATION QUERIES =====

    /**
     * Check if transaction exists by PayProp transaction ID (for deduplication)
     */
    boolean existsByPaypropTransactionId(String paypropTransactionId);

    /**
     * Find transaction by PayProp transaction ID
     */
    HistoricalTransaction findByPaypropTransactionId(String paypropTransactionId);

    /**
     * Find transactions by PayProp property ID
     */
    List<HistoricalTransaction> findByPaypropPropertyId(String paypropPropertyId);

    /**
     * Find transactions for property statement generation (by PayProp ID and date range)
     * UPDATED: Now uses JOIN to match both payprop_property_id AND property table link
     */
    @Query("SELECT ht FROM HistoricalTransaction ht " +
           "LEFT JOIN Property p ON ht.property = p " +
           "WHERE (ht.paypropPropertyId = :paypropPropertyId OR p.payPropId = :paypropPropertyId) " +
           "AND ht.transactionDate BETWEEN :fromDate AND :toDate " +
           "ORDER BY ht.transactionDate DESC")
    List<HistoricalTransaction> findPropertyTransactionsForStatement(@Param("paypropPropertyId") String paypropPropertyId,
                                                                     @Param("fromDate") LocalDate fromDate,
                                                                     @Param("toDate") LocalDate toDate);

    /**
     * Find transactions by account source
     */
    List<HistoricalTransaction> findByAccountSource(String accountSource);

    /**
     * Find transactions by account source and date range
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE ht.accountSource = :accountSource " +
           "AND ht.transactionDate BETWEEN :fromDate AND :toDate " +
           "AND ht.status = 'active' ORDER BY ht.transactionDate DESC")
    List<HistoricalTransaction> findByAccountSourceAndDateRange(@Param("accountSource") String accountSource,
                                                               @Param("fromDate") LocalDate fromDate,
                                                               @Param("toDate") LocalDate toDate);

    // ===== PAYMENT SOURCE QUERIES (Source-Scoped Deduplication) =====

    /**
     * Find transactions by payment source
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE ht.paymentSource.id = :paymentSourceId " +
           "ORDER BY ht.transactionDate DESC")
    List<HistoricalTransaction> findByPaymentSourceId(@Param("paymentSourceId") Long paymentSourceId);

    /**
     * Find transactions by payment source and date range
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE ht.paymentSource.id = :paymentSourceId " +
           "AND ht.transactionDate BETWEEN :fromDate AND :toDate " +
           "ORDER BY ht.transactionDate DESC")
    List<HistoricalTransaction> findByPaymentSourceAndDateRange(
            @Param("paymentSourceId") Long paymentSourceId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    /**
     * Find potential duplicates WITHIN a payment source
     * Checks for same date, amount, description, type, property, and customer
     * This is source-scoped deduplication - only checks within the same payment source
     */
    @Query("SELECT ht FROM HistoricalTransaction ht WHERE " +
           "ht.paymentSource.id = :paymentSourceId " +
           "AND ht.transactionDate = :transactionDate " +
           "AND ht.amount = :amount " +
           "AND (:propertyId IS NULL OR ht.property.id = :propertyId) " +
           "AND ht.status = 'active' " +
           "ORDER BY ht.createdAt ASC")
    List<HistoricalTransaction> findPotentialDuplicatesInSource(
            @Param("paymentSourceId") Long paymentSourceId,
            @Param("transactionDate") LocalDate transactionDate,
            @Param("amount") BigDecimal amount,
            @Param("propertyId") Long propertyId);

    /**
     * Count transactions in a payment source
     */
    @Query("SELECT COUNT(ht) FROM HistoricalTransaction ht WHERE ht.paymentSource.id = :paymentSourceId " +
           "AND ht.status = 'active'")
    long countByPaymentSourceId(@Param("paymentSourceId") Long paymentSourceId);

    /**
     * Get total amount for payment source
     */
    @Query("SELECT SUM(ht.amount) FROM HistoricalTransaction ht WHERE ht.paymentSource.id = :paymentSourceId " +
           "AND ht.status = 'active'")
    BigDecimal getTotalAmountByPaymentSource(@Param("paymentSourceId") Long paymentSourceId);

    /**
     * Get payment source statistics - date range and transaction count
     */
    @Query("SELECT " +
           "ps.id, " +
           "ps.name, " +
           "COUNT(ht), " +
           "MIN(ht.transactionDate), " +
           "MAX(ht.transactionDate) " +
           "FROM HistoricalTransaction ht " +
           "JOIN ht.paymentSource ps " +
           "WHERE ht.status = 'active' " +
           "GROUP BY ps.id, ps.name " +
           "ORDER BY ps.name")
    List<Object[]> getPaymentSourceStatistics();
}
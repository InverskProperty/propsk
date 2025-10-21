// PaymentRepository.java - Repository for payment transactions
package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.Payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    
    // ===== PAYPROP INTEGRATION QUERIES =====
    
    /**
     * Find payment by PayProp payment ID (for sync deduplication)
     */
    Payment findByPayPropPaymentId(String payPropPaymentId);
    
    /**
     * Check if payment exists by PayProp ID
     */
    boolean existsByPayPropPaymentId(String payPropPaymentId);
    
    /**
     * Find all payments with PayProp IDs (synced payments)
     */
    List<Payment> findByPayPropPaymentIdIsNotNull();
    
    /**
     * Find payments by batch ID (PayProp batch processing)
     */
    List<Payment> findByBatchId(String batchId);
    
    /**
     * Find child payments by parent payment ID
     */
    List<Payment> findByParentPaymentId(String parentPaymentId);

    // ===== DATE-BASED QUERIES =====
    
    /**
     * Find payments between dates (for period analysis)
     */
    List<Payment> findByPaymentDateBetween(LocalDate startDate, LocalDate endDate);
    
    /**
     * Find payments by specific date
     */
    List<Payment> findByPaymentDate(LocalDate paymentDate);
    
    /**
     * Find payments from a specific date onwards
     */
    List<Payment> findByPaymentDateGreaterThanEqual(LocalDate fromDate);
    
    /**
     * Find recent payments (last N days)
     */
    @Query("SELECT p FROM Payment p WHERE p.paymentDate >= :cutoffDate ORDER BY p.paymentDate DESC")
    List<Payment> findRecentPayments(@Param("cutoffDate") LocalDate cutoffDate);
    
    /**
     * Find payments by reconciliation date range
     */
    List<Payment> findByReconciliationDateBetween(LocalDate startDate, LocalDate endDate);

    // ===== PROPERTY-BASED QUERIES =====
    
    /**
     * Find all payments for a specific property
     */
    List<Payment> findByPropertyId(Long propertyId);
    
    /**
     * Find payments for property within date range
     */
    List<Payment> findByPropertyIdAndPaymentDateBetween(Long propertyId, LocalDate startDate, LocalDate endDate);
    
    /**
     * Find payments for multiple properties
     */
    List<Payment> findByPropertyIdIn(List<Long> propertyIds);

    // ===== TENANT-BASED QUERIES (RENT PAYMENTS) =====

    /**
     * Find all rent payments from a specific tenant
     */
    List<Payment> findByTenantId(Long tenantId);

    /**
     * Find tenant payments within date range
     */
    List<Payment> findByTenantIdAndPaymentDateBetween(Long tenantId, LocalDate startDate, LocalDate endDate);

    // ===== INVOICE/LEASE-BASED QUERIES =====

    /**
     * Find payments by invoice (lease) ID and date range
     * Used for lease-based statement generation
     */
    @Query("SELECT p FROM Payment p WHERE p.invoiceId = :invoiceId AND p.paymentDate BETWEEN :startDate AND :endDate ORDER BY p.paymentDate")
    List<Payment> findByInvoiceIdAndPaymentDateBetween(@Param("invoiceId") Long invoiceId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    /**
     * Find all rent payments (payments from tenants)
     */
    List<Payment> findByTenantIdIsNotNull();
    
    /**
     * Find rent payments within date range
     */
    @Query("SELECT p FROM Payment p WHERE p.tenantId IS NOT NULL AND p.paymentDate BETWEEN :startDate AND :endDate")
    List<Payment> findRentPaymentsBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // ===== BENEFICIARY-BASED QUERIES (OUTGOING PAYMENTS) =====
    
    /**
     * Find all payments to a specific beneficiary
     */
    List<Payment> findByBeneficiaryId(Long beneficiaryId);
    
    /**
     * Find beneficiary payments within date range
     */
    List<Payment> findByBeneficiaryIdAndPaymentDateBetween(Long beneficiaryId, LocalDate startDate, LocalDate endDate);
    
    /**
     * Find all outgoing payments (payments to beneficiaries)
     */
    List<Payment> findByBeneficiaryIdIsNotNull();
    
    /**
     * Find outgoing payments within date range
     */
    @Query("SELECT p FROM Payment p WHERE p.beneficiaryId IS NOT NULL AND p.paymentDate BETWEEN :startDate AND :endDate")
    List<Payment> findOutgoingPaymentsBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // ===== CATEGORY-BASED QUERIES =====
    
    /**
     * Find payments by category
     */
    List<Payment> findByCategoryId(String categoryId);
    
    /**
     * Find payments by category within date range
     */
    List<Payment> findByCategoryIdAndPaymentDateBetween(String categoryId, LocalDate startDate, LocalDate endDate);
    
    /**
     * Find payments by multiple categories
     */
    List<Payment> findByCategoryIdIn(List<String> categoryIds);

    // ===== STATUS-BASED QUERIES =====
    
    /**
     * Find payments by status
     */
    List<Payment> findByStatus(String status);
    
    /**
     * Find pending/processing payments
     */
    @Query("SELECT p FROM Payment p WHERE p.status IN ('PENDING', 'PROCESSING', 'SUBMITTED')")
    List<Payment> findPendingPayments();
    
    /**
     * Find completed payments
     */
    @Query("SELECT p FROM Payment p WHERE p.status IN ('COMPLETED', 'CLEARED', 'RECONCILED')")
    List<Payment> findCompletedPayments();

    // ===== AMOUNT-BASED QUERIES =====
    
    /**
     * Find payments above a certain amount
     */
    List<Payment> findByAmountGreaterThan(BigDecimal amount);
    
    /**
     * Find payments within amount range
     */
    List<Payment> findByAmountBetween(BigDecimal minAmount, BigDecimal maxAmount);
    
    /**
     * Find large payments (for audit purposes)
     */
    @Query("SELECT p FROM Payment p WHERE p.amount >= :threshold ORDER BY p.amount DESC")
    List<Payment> findLargePayments(@Param("threshold") BigDecimal threshold);

    // ===== ANALYTICAL QUERIES =====
    
    /**
     * Sum total payments for a property within date range
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.propertyId = :propertyId AND p.paymentDate BETWEEN :startDate AND :endDate")
    BigDecimal sumPaymentsByPropertyAndDateRange(@Param("propertyId") Long propertyId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    /**
     * Sum rent payments for a property within date range
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.propertyId = :propertyId AND p.tenantId IS NOT NULL AND p.paymentDate BETWEEN :startDate AND :endDate")
    BigDecimal sumRentPaymentsByPropertyAndDateRange(@Param("propertyId") Long propertyId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    /**
     * Sum outgoing payments for a property within date range
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.propertyId = :propertyId AND p.beneficiaryId IS NOT NULL AND p.paymentDate BETWEEN :startDate AND :endDate")
    BigDecimal sumOutgoingPaymentsByPropertyAndDateRange(@Param("propertyId") Long propertyId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    /**
     * Sum total payments by beneficiary within date range
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.beneficiaryId = :beneficiaryId AND p.paymentDate BETWEEN :startDate AND :endDate")
    BigDecimal sumPaymentsByBeneficiaryAndDateRange(@Param("beneficiaryId") Long beneficiaryId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // ===== SEARCH AND FILTERING =====
    
    /**
     * Search payments by description or reference
     */
    @Query("SELECT p FROM Payment p WHERE LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR LOWER(p.reference) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Payment> searchByDescriptionOrReference(@Param("searchTerm") String searchTerm);
    
    /**
     * Complex payment search with multiple filters
     */
    @Query("SELECT p FROM Payment p WHERE " +
           "(:propertyId IS NULL OR p.propertyId = :propertyId) AND " +
           "(:beneficiaryId IS NULL OR p.beneficiaryId = :beneficiaryId) AND " +
           "(:tenantId IS NULL OR p.tenantId = :tenantId) AND " +
           "(:categoryId IS NULL OR p.categoryId = :categoryId) AND " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(:startDate IS NULL OR p.paymentDate >= :startDate) AND " +
           "(:endDate IS NULL OR p.paymentDate <= :endDate) AND " +
           "(:minAmount IS NULL OR p.amount >= :minAmount) AND " +
           "(:maxAmount IS NULL OR p.amount <= :maxAmount)")
    List<Payment> searchPayments(@Param("propertyId") Long propertyId,
                                @Param("beneficiaryId") Long beneficiaryId,
                                @Param("tenantId") Long tenantId,
                                @Param("categoryId") String categoryId,
                                @Param("status") String status,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate,
                                @Param("minAmount") BigDecimal minAmount,
                                @Param("maxAmount") BigDecimal maxAmount,
                                Pageable pageable);

    // ===== REPORTING QUERIES =====
    
    /**
     * Get payment summary by category for date range
     */
    @Query("SELECT p.categoryId, COUNT(p), SUM(p.amount) FROM Payment p WHERE p.paymentDate BETWEEN :startDate AND :endDate GROUP BY p.categoryId")
    List<Object[]> getPaymentSummaryByCategory(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    /**
     * Get monthly payment totals
     */
    @Query("SELECT YEAR(p.paymentDate), MONTH(p.paymentDate), SUM(p.amount) FROM Payment p WHERE p.paymentDate BETWEEN :startDate AND :endDate GROUP BY YEAR(p.paymentDate), MONTH(p.paymentDate) ORDER BY YEAR(p.paymentDate), MONTH(p.paymentDate)")
    List<Object[]> getMonthlyPaymentTotals(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    /**
     * Get payment count by status
     */
    @Query("SELECT p.status, COUNT(p) FROM Payment p GROUP BY p.status")
    List<Object[]> getPaymentCountByStatus();

    // ===== PAGINATION SUPPORT =====
    
    /**
     * Find recent payments with pagination
     */
    List<Payment> findByOrderByPaymentDateDesc(Pageable pageable);
    
    /**
     * Find payments by property with pagination
     */
    List<Payment> findByPropertyIdOrderByPaymentDateDesc(Long propertyId, Pageable pageable);
    
    /**
     * Find payments by beneficiary with pagination
     */
    List<Payment> findByBeneficiaryIdOrderByPaymentDateDesc(Long beneficiaryId, Pageable pageable);
}
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
}

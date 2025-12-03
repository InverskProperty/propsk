package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.PaymentBatch;
import site.easy.to.build.crm.entity.PaymentBatch.BatchStatus;
import site.easy.to.build.crm.entity.PaymentBatch.BatchType;
import site.easy.to.build.crm.entity.PaymentBatch.BatchSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for PaymentBatch entity
 */
@Repository
public interface PaymentBatchRepository extends JpaRepository<PaymentBatch, Long> {

    // ===== BASIC FINDERS =====

    /**
     * Find batch by batch ID
     */
    Optional<PaymentBatch> findByBatchId(String batchId);

    /**
     * Check if batch ID exists
     */
    boolean existsByBatchId(String batchId);

    /**
     * Find batches by status
     */
    List<PaymentBatch> findByStatus(BatchStatus status);

    /**
     * Find batches by type
     */
    List<PaymentBatch> findByBatchType(BatchType batchType);

    /**
     * Find batches by source
     */
    List<PaymentBatch> findBySource(BatchSource source);

    /**
     * Find batches for a beneficiary
     */
    List<PaymentBatch> findByBeneficiaryId(Long beneficiaryId);

    /**
     * Find batches for a beneficiary excluding PayProp source
     * Used for the allocations UI which only shows historical/manual transactions
     */
    List<PaymentBatch> findByBeneficiaryIdAndSourceNotOrderByPaymentDateDesc(Long beneficiaryId, BatchSource source);

    // ===== DATE RANGE QUERIES =====

    /**
     * Find batches by payment date range
     */
    List<PaymentBatch> findByPaymentDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Find batches by status and date range
     */
    @Query("SELECT pb FROM PaymentBatch pb WHERE pb.status = :status " +
           "AND pb.paymentDate BETWEEN :startDate AND :endDate " +
           "ORDER BY pb.paymentDate DESC")
    List<PaymentBatch> findByStatusAndDateRange(
        @Param("status") BatchStatus status,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    // ===== PENDING BATCHES =====

    /**
     * Find pending/draft batches that need payment
     */
    @Query("SELECT pb FROM PaymentBatch pb WHERE pb.status IN ('DRAFT', 'PENDING') " +
           "ORDER BY pb.paymentDate ASC")
    List<PaymentBatch> findBatchesNeedingPayment();

    /**
     * Find pending owner payment batches
     */
    @Query("SELECT pb FROM PaymentBatch pb WHERE pb.batchType = 'OWNER_PAYMENT' " +
           "AND pb.status IN ('DRAFT', 'PENDING') " +
           "ORDER BY pb.paymentDate ASC")
    List<PaymentBatch> findPendingOwnerPaymentBatches();

    // ===== PAYPROP INTEGRATION =====

    /**
     * Find batch by PayProp batch ID
     */
    Optional<PaymentBatch> findByPaypropBatchId(String paypropBatchId);

    /**
     * Check if PayProp batch ID exists
     */
    boolean existsByPaypropBatchId(String paypropBatchId);

    // ===== STATISTICS =====

    /**
     * Count batches by status
     */
    long countByStatus(BatchStatus status);

    /**
     * Sum total payments by status
     */
    @Query("SELECT SUM(pb.totalPayment) FROM PaymentBatch pb WHERE pb.status = :status")
    BigDecimal sumTotalPaymentByStatus(@Param("status") BatchStatus status);

    /**
     * Sum total payments for beneficiary
     */
    @Query("SELECT SUM(pb.totalPayment) FROM PaymentBatch pb " +
           "WHERE pb.beneficiaryId = :beneficiaryId AND pb.status = 'PAID'")
    BigDecimal sumPaidAmountForBeneficiary(@Param("beneficiaryId") Long beneficiaryId);

    // ===== BATCH ID GENERATION =====

    /**
     * Get the latest batch number for manual batches (for generating next batch ID)
     */
    @Query(value = "SELECT MAX(CAST(SUBSTRING(batch_id, 8) AS UNSIGNED)) " +
           "FROM payment_batches WHERE batch_id LIKE 'MANUAL-%'", nativeQuery = true)
    Integer getLatestManualBatchNumber();

    /**
     * Get the latest batch number for owner payment batches
     */
    @Query(value = "SELECT MAX(CAST(SUBSTRING(batch_id, 7) AS UNSIGNED)) " +
           "FROM payment_batches WHERE batch_id LIKE 'OWNER-%'", nativeQuery = true)
    Integer getLatestOwnerBatchNumber();
}

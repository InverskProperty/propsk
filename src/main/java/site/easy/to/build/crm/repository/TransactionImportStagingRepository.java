package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.TransactionImportStaging;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionImportStagingRepository extends JpaRepository<TransactionImportStaging, Long> {

    /**
     * Find all staging records for a batch
     */
    List<TransactionImportStaging> findByBatchIdOrderByLineNumberAsc(String batchId);

    /**
     * Find staging records by status
     */
    List<TransactionImportStaging> findByBatchIdAndStatusOrderByLineNumberAsc(String batchId, String status);

    /**
     * Find duplicates in batch
     */
    List<TransactionImportStaging> findByBatchIdAndIsDuplicateTrueOrderByLineNumberAsc(String batchId);

    /**
     * Find staging records for a payment source
     */
    List<TransactionImportStaging> findByPaymentSourceIdOrderByCreatedAtDesc(Long paymentSourceId);

    /**
     * Count records in batch by status
     */
    long countByBatchIdAndStatus(String batchId, String status);

    /**
     * Count total records in batch
     */
    long countByBatchId(String batchId);

    /**
     * Delete all records for a batch
     */
    @Modifying
    @Query("DELETE FROM TransactionImportStaging t WHERE t.batchId = :batchId")
    void deleteByBatchId(@Param("batchId") String batchId);

    /**
     * Find potential duplicates within payment source
     * Checks for same date, amount, and property within the same payment source
     */
    @Query("SELECT t FROM TransactionImportStaging t WHERE t.paymentSource.id = :paymentSourceId " +
           "AND t.transactionDate = :transactionDate " +
           "AND t.amount = :amount " +
           "AND (t.property.id = :propertyId OR (:propertyId IS NULL AND t.property IS NULL))")
    List<TransactionImportStaging> findPotentialDuplicatesInSource(
            @Param("paymentSourceId") Long paymentSourceId,
            @Param("transactionDate") LocalDate transactionDate,
            @Param("amount") java.math.BigDecimal amount,
            @Param("propertyId") Long propertyId);

    /**
     * Get summary statistics for a batch
     */
    @Query("SELECT t.status, COUNT(t) FROM TransactionImportStaging t " +
           "WHERE t.batchId = :batchId GROUP BY t.status")
    List<Object[]> getBatchStatusSummary(@Param("batchId") String batchId);
}

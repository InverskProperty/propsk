// BatchPaymentRepository.java
package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.BatchPayment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for BatchPayment entity
 * Handles database operations for PayProp batch payments
 */
@Repository
public interface BatchPaymentRepository extends JpaRepository<BatchPayment, Long> {
    
    /**
     * Find a batch payment by its PayProp batch ID
     * @param payPropBatchId The PayProp external batch ID
     * @return The batch payment if found
     */
    BatchPayment findByPayPropBatchId(String payPropBatchId);
    
    /**
     * Find batch payments by status
     * @param status The batch status
     * @return List of batch payments with the given status
     */
    List<BatchPayment> findByStatus(String status);
    
    /**
     * Find batch payments by date range
     * @param startDate Start date
     * @param endDate End date
     * @return List of batch payments within the date range
     */
    List<BatchPayment> findByBatchDateBetween(LocalDate startDate, LocalDate endDate);
    
    /**
     * Find batch payments that have been synced with PayProp
     * @param synced Whether the batch has been synced
     * @return List of synced/unsynced batch payments
     */
    List<BatchPayment> findByPayPropSynced(Boolean synced);
    
    /**
     * Find batch payments processed after a certain date
     * @param processedDate The cutoff date
     * @return List of batch payments processed after the date
     */
    List<BatchPayment> findByProcessedDateAfter(LocalDateTime processedDate);
    
    /**
     * Find batch payments with total amount greater than a specified value
     * @param amount The minimum amount
     * @return List of batch payments above the amount
     */
    List<BatchPayment> findByTotalAmountGreaterThan(BigDecimal amount);
    
    /**
     * Find the most recent batch payments
     * @param limit Number of batches to return
     * @return List of most recent batch payments
     */
    List<BatchPayment> findTopByOrderByCreatedAtDesc(int limit);
    
    /**
     * Check if a batch with the given PayProp ID exists
     * @param payPropBatchId The PayProp batch ID
     * @return true if exists
     */
    boolean existsByPayPropBatchId(String payPropBatchId);
    
    /**
     * Find batch payments that received webhooks within a time range
     * @param startTime Start time
     * @param endTime End time
     * @return List of batch payments that received webhooks in the range
     */
    List<BatchPayment> findByPayPropWebhookReceivedBetween(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * Count batch payments by status
     * @param status The batch status
     * @return Count of batches with that status
     */
    long countByStatus(String status);
}
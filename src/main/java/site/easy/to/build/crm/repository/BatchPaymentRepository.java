package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.BatchPayment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BatchPaymentRepository extends JpaRepository<BatchPayment, Long> {
    
    // Use only the Optional version
    Optional<BatchPayment> findByPayPropBatchId(String payPropBatchId);
    
    List<BatchPayment> findByStatus(String status);
    
    List<BatchPayment> findByProcessingDateBetween(LocalDateTime start, LocalDateTime end);
    
    @Query("SELECT b FROM BatchPayment b WHERE b.status = :status AND b.processingDate < :cutoffDate")
    List<BatchPayment> findPendingBatchesOlderThan(@Param("status") String status, 
                                                   @Param("cutoffDate") LocalDateTime cutoffDate);
    
    @Query("SELECT COUNT(b) FROM BatchPayment b WHERE b.status = :status")
    long countByStatus(@Param("status") String status);
    
    @Query("SELECT SUM(b.totalAmount) FROM BatchPayment b WHERE b.status = :status")
    BigDecimal sumTotalAmountByStatus(@Param("status") String status);
    
    // Fixed: Use Pageable to limit results instead of int parameter
    List<BatchPayment> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    // Alternative: If you want the most recent batch payment
    Optional<BatchPayment> findFirstByOrderByCreatedAtDesc();
    
    // Alternative: If you want to find recent batches by status
    List<BatchPayment> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
}
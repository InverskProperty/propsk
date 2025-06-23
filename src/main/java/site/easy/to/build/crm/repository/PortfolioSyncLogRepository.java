// PortfolioSyncLogRepository.java - Repository for tracking PayProp synchronization
package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.PortfolioSyncLog;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PortfolioSyncLogRepository extends JpaRepository<PortfolioSyncLog, Long> {
    
    // Find logs by entity
    List<PortfolioSyncLog> findByPortfolioId(Long portfolioId);
    List<PortfolioSyncLog> findByBlockId(Long blockId);
    List<PortfolioSyncLog> findByPropertyId(Long propertyId);
    
    // Find logs by sync type and status
    List<PortfolioSyncLog> findBySyncTypeAndStatus(String syncType, String status);
    List<PortfolioSyncLog> findByStatus(String status);
    
    // Recent sync activities
    List<PortfolioSyncLog> findByOrderBySyncStartedAtDesc(Pageable pageable);
    
    @Query("SELECT psl FROM PortfolioSyncLog psl WHERE psl.portfolioId = :portfolioId ORDER BY psl.syncStartedAt DESC")
    List<PortfolioSyncLog> findRecentSyncsByPortfolio(@Param("portfolioId") Long portfolioId, Pageable pageable);
    
    // Failed syncs that need attention
    @Query("SELECT psl FROM PortfolioSyncLog psl WHERE psl.status = 'FAILED' OR psl.status = 'CONFLICT' ORDER BY psl.syncStartedAt DESC")
    List<PortfolioSyncLog> findFailedSyncs();
    
    // PayProp tag tracking
    List<PortfolioSyncLog> findByPayPropTagId(String payPropTagId);
    
    @Query("SELECT psl FROM PortfolioSyncLog psl WHERE psl.payPropTagId = :tagId AND psl.status = 'SUCCESS' ORDER BY psl.syncStartedAt DESC LIMIT 1")
    PortfolioSyncLog findLatestSuccessfulSyncByTagId(@Param("tagId") String tagId);
    
    // Cleanup old logs
    @Query("DELETE FROM PortfolioSyncLog psl WHERE psl.syncStartedAt < :cutoffDate")
    void deleteSyncLogsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // Statistics
    @Query("SELECT COUNT(psl) FROM PortfolioSyncLog psl WHERE psl.syncStartedAt >= :startDate AND psl.status = 'SUCCESS'")
    long countSuccessfulSyncsSince(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT COUNT(psl) FROM PortfolioSyncLog psl WHERE psl.syncStartedAt >= :startDate AND psl.status = 'FAILED'")
    long countFailedSyncsSince(@Param("startDate") LocalDateTime startDate);
}
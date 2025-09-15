package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.HistoricalDataUpload;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface HistoricalDataUploadRepository extends JpaRepository<HistoricalDataUpload, Long> {

    /**
     * Find uploads by user, ordered by upload date descending
     */
    List<HistoricalDataUpload> findByUploadedByOrderByUploadDateDesc(Long uploadedBy);

    /**
     * Find uploads by status
     */
    List<HistoricalDataUpload> findByProcessingStatus(HistoricalDataUpload.ProcessingStatus status);

    /**
     * Find uploads by file type
     */
    List<HistoricalDataUpload> findByFileType(HistoricalDataUpload.UploadFileType fileType);

    /**
     * Check if file hash already exists (to prevent duplicate uploads)
     */
    boolean existsByFileHashAndProcessingStatusNot(String fileHash, HistoricalDataUpload.ProcessingStatus excludeStatus);

    /**
     * Find uploads within date range
     */
    @Query("SELECT u FROM HistoricalDataUpload u WHERE u.uploadDate BETWEEN :startDate AND :endDate ORDER BY u.uploadDate DESC")
    List<HistoricalDataUpload> findByUploadDateBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Find uploads that are currently processing (for monitoring)
     */
    @Query("SELECT u FROM HistoricalDataUpload u WHERE u.processingStatus = 'PROCESSING' AND u.processingStartedAt < :timeThreshold")
    List<HistoricalDataUpload> findStuckProcessingUploads(@Param("timeThreshold") LocalDateTime timeThreshold);

    /**
     * Get upload statistics
     */
    @Query("SELECT " +
           "COUNT(u) as totalUploads, " +
           "SUM(CASE WHEN u.processingStatus = 'COMPLETED' THEN 1 ELSE 0 END) as completedUploads, " +
           "SUM(CASE WHEN u.processingStatus = 'FAILED' THEN 1 ELSE 0 END) as failedUploads, " +
           "SUM(u.recordsCreated) as totalRecordsCreated, " +
           "SUM(u.recordsUpdated) as totalRecordsUpdated " +
           "FROM HistoricalDataUpload u WHERE u.uploadedBy = :userId")
    Object[] getUploadStatsByUser(@Param("userId") Long userId);

    /**
     * Find recent uploads for a user
     */
    @Query("SELECT u FROM HistoricalDataUpload u WHERE u.uploadedBy = :userId AND u.uploadDate >= :since ORDER BY u.uploadDate DESC")
    List<HistoricalDataUpload> findRecentUploadsByUser(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
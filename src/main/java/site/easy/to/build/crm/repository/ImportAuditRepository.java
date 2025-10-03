package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.ImportAudit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ImportAuditRepository extends JpaRepository<ImportAudit, Long> {

    /**
     * Find audit record by batch ID
     */
    Optional<ImportAudit> findByBatchId(String batchId);

    /**
     * Find all audits for a user
     */
    List<ImportAudit> findByUserIdOrderByImportedAtDesc(Long userId);

    /**
     * Find recent audits with pagination
     */
    List<ImportAudit> findAllByOrderByImportedAtDesc(Pageable pageable);

    /**
     * Find by verification status
     */
    List<ImportAudit> findByVerificationStatus(String status);

    /**
     * Find audits within date range
     */
    @Query("SELECT ia FROM ImportAudit ia WHERE ia.importedAt BETWEEN :startDate AND :endDate ORDER BY ia.importedAt DESC")
    List<ImportAudit> findByDateRange(@Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate);

    /**
     * Get import statistics
     */
    @Query("SELECT " +
           "COUNT(ia), " +
           "SUM(ia.totalRows), " +
           "SUM(ia.importedRows), " +
           "SUM(ia.skippedRows), " +
           "SUM(ia.errorRows) " +
           "FROM ImportAudit ia WHERE ia.userId = :userId")
    Object[] getImportStatsByUser(@Param("userId") Long userId);

    /**
     * Count imports by user
     */
    long countByUserId(Long userId);
}

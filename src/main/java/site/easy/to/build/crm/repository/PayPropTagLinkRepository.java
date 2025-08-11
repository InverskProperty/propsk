package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.PayPropTagLink;
import site.easy.to.build.crm.entity.Portfolio;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PayPropTagLinkRepository extends JpaRepository<PayPropTagLink, Long> {
    
    // Find by portfolio
    List<PayPropTagLink> findByPortfolio(Portfolio portfolio);
    
    List<PayPropTagLink> findByPortfolioId(Long portfolioId);
    
    // Find by tag
    List<PayPropTagLink> findByTagId(String tagId);
    
    Optional<PayPropTagLink> findByPortfolioAndTagId(Portfolio portfolio, String tagId);
    
    Optional<PayPropTagLink> findByPortfolioIdAndTagId(Long portfolioId, String tagId);
    
    // Find by sync status
    List<PayPropTagLink> findBySyncStatus(String syncStatus);
    
    @Query("SELECT ptl FROM PayPropTagLink ptl WHERE ptl.syncStatus = 'PENDING'")
    List<PayPropTagLink> findPendingSync();
    
    @Query("SELECT ptl FROM PayPropTagLink ptl WHERE ptl.syncStatus = 'SYNCED'")
    List<PayPropTagLink> findSynced();
    
    @Query("SELECT ptl FROM PayPropTagLink ptl WHERE ptl.syncStatus = 'FAILED'")
    List<PayPropTagLink> findFailed();
    
    // Find by tag name
    List<PayPropTagLink> findByTagName(String tagName);
    
    List<PayPropTagLink> findByTagNameContainingIgnoreCase(String tagName);
    
    // Sync-related queries
    @Query("SELECT ptl FROM PayPropTagLink ptl WHERE ptl.syncedAt IS NULL")
    List<PayPropTagLink> findNeverSynced();
    
    @Query("SELECT ptl FROM PayPropTagLink ptl WHERE ptl.syncedAt < :before")
    List<PayPropTagLink> findSyncedBefore(@Param("before") LocalDateTime before);
    
    @Query("SELECT ptl FROM PayPropTagLink ptl WHERE ptl.updatedAt > ptl.syncedAt OR ptl.syncedAt IS NULL")
    List<PayPropTagLink> findNeedingSync();
    
    // Statistics queries
    @Query("SELECT COUNT(ptl) FROM PayPropTagLink ptl WHERE ptl.syncStatus = :status")
    long countBySyncStatus(@Param("status") String status);
    
    @Query("SELECT COUNT(ptl) FROM PayPropTagLink ptl WHERE ptl.portfolio.id = :portfolioId")
    long countByPortfolioId(@Param("portfolioId") Long portfolioId);
    
    @Query("SELECT ptl.tagName, COUNT(ptl) FROM PayPropTagLink ptl GROUP BY ptl.tagName")
    List<Object[]> countByTagName();
    
    // Portfolio-specific queries
    @Query("SELECT ptl FROM PayPropTagLink ptl WHERE ptl.portfolio.isShared = 'Y'")
    List<PayPropTagLink> findBySharedPortfolios();
    
    @Query("SELECT ptl FROM PayPropTagLink ptl WHERE ptl.portfolio.isShared = 'N'")
    List<PayPropTagLink> findByOwnerSpecificPortfolios();
    
    @Query("SELECT ptl FROM PayPropTagLink ptl WHERE ptl.portfolio.propertyOwnerId = :ownerId")
    List<PayPropTagLink> findByPropertyOwnerId(@Param("ownerId") Long ownerId);
    
    // Cleanup queries
    @Query("SELECT ptl FROM PayPropTagLink ptl WHERE ptl.createdAt < :before AND ptl.syncStatus = 'FAILED'")
    List<PayPropTagLink> findOldFailedLinks(@Param("before") LocalDateTime before);
    
    void deleteByPortfolio(Portfolio portfolio);
    
    void deleteByPortfolioId(Long portfolioId);
    
    void deleteByTagId(String tagId);
    
    // Existence checks
    boolean existsByPortfolioAndTagId(Portfolio portfolio, String tagId);
    
    boolean existsByPortfolioIdAndTagId(Long portfolioId, String tagId);
    
    // Advanced queries for sync optimization
    @Query("SELECT DISTINCT ptl.tagId FROM PayPropTagLink ptl WHERE ptl.syncStatus = 'SYNCED'")
    List<String> findAllSyncedTagIds();
    
    // OPTION 1: Query based on existing Portfolio fields
    @Query("SELECT ptl FROM PayPropTagLink ptl WHERE ptl.portfolio.payPropTags IS NOT NULL AND ptl.syncStatus != 'SYNCED'")
    List<PayPropTagLink> findUnsyncedPayPropEnabledPortfolios();
    
    @Query("SELECT ptl FROM PayPropTagLink ptl JOIN ptl.portfolio p WHERE p.portfolioType = :type")
    List<PayPropTagLink> findByPortfolioType(@Param("type") String portfolioType);
    
    // Maintenance queries
    @Query("SELECT ptl FROM PayPropTagLink ptl WHERE ptl.tagId IS NULL OR ptl.tagName IS NULL OR ptl.portfolio IS NULL")
    List<PayPropTagLink> findIncompleteLinks();
    
    @Query("SELECT COUNT(*) FROM PayPropTagLink ptl WHERE ptl.syncStatus = 'SYNCED' AND ptl.syncedAt > :since")
    long countRecentlySynced(@Param("since") LocalDateTime since);
}
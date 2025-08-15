// PortfolioRepository.java - Repository for Portfolio management
package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.Portfolio;
import site.easy.to.build.crm.entity.PortfolioType;
import site.easy.to.build.crm.entity.SyncStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    
    // Basic finders
    List<Portfolio> findByPropertyOwnerId(Integer propertyOwnerId);
    List<Portfolio> findByPropertyOwnerIdIsNull(); // Shared/employee portfolios
    List<Portfolio> findByCreatedBy(Long userId);
    List<Portfolio> findByPortfolioType(PortfolioType portfolioType);
    List<Portfolio> findByIsActive(String isActive);
    List<Portfolio> findByIsShared(String isShared);
    
    // Active portfolios
    @Query("SELECT p FROM Portfolio p WHERE p.isActive = 'Y' ORDER BY p.displayOrder, p.name")
    List<Portfolio> findActivePortfolios();
    
    // Find portfolios for a specific user (role-based)
    @Query("SELECT p FROM Portfolio p WHERE " +
           "(p.propertyOwnerId = :propertyOwnerId) OR " +
           "(p.isShared = 'Y' AND :isEmployee = true) OR " +
           "(p.createdBy = :userId) " +
           "AND p.isActive = 'Y' " +
           "ORDER BY p.displayOrder, p.name")
    List<Portfolio> findPortfoliosForUser(@Param("propertyOwnerId") Integer propertyOwnerId,
                                         @Param("userId") Long userId,
                                         @Param("isEmployee") Boolean isEmployee);
    
    // PayProp synchronization queries
    List<Portfolio> findBySyncStatus(SyncStatus syncStatus);
    List<Portfolio> findByPayPropTagsIsNotNull();
    List<Portfolio> findByPayPropTagsIsNull();
    
    @Query("SELECT p FROM Portfolio p WHERE p.syncStatus = 'PENDING' OR p.syncStatus = 'FAILED'")
    List<Portfolio> findPortfoliosNeedingSync();
    
    @Query("SELECT p FROM Portfolio p WHERE p.payPropTags LIKE CONCAT('%', :tagId, '%')")
    List<Portfolio> findByPayPropTag(@Param("tagId") String tagId);
    
    @Query("SELECT p FROM Portfolio p WHERE p.lastSyncAt IS NULL OR p.lastSyncAt < :cutoffTime")
    List<Portfolio> findPortfoliosNotSyncedSince(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Search portfolios with filters (my version had 7 parameters, yours has 6 + Pageable)
     */
    @Query("SELECT p FROM Portfolio p WHERE " +
           "(:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
           "(:description IS NULL OR LOWER(p.description) LIKE LOWER(CONCAT('%', :description, '%'))) AND " +
           "(:portfolioType IS NULL OR p.portfolioType = :portfolioType) AND " +
           "(:propertyOwnerId IS NULL OR p.propertyOwnerId = :propertyOwnerId) AND " +
           "(:isShared IS NULL OR p.isShared = :isShared) AND " +
           "(:isActive IS NULL OR p.isActive = :isActive) AND " +
           "(:createdBy IS NULL OR p.createdBy = :createdBy)")
    List<Portfolio> searchPortfolios(@Param("name") String name,
                                   @Param("description") String description,
                                   @Param("portfolioType") PortfolioType portfolioType,
                                   @Param("propertyOwnerId") Integer propertyOwnerId,
                                   @Param("isShared") String isShared,
                                   @Param("isActive") String isActive,
                                   @Param("createdBy") Long createdBy);
    
    // Count methods
    long countByPropertyOwnerId(Integer propertyOwnerId);
    long countByCreatedBy(Long userId);
    long countByPortfolioType(PortfolioType portfolioType);
    long countByIsActive(String isActive);
    long countBySyncStatus(SyncStatus syncStatus);
    
    // Property count queries - UPDATED to use PropertyPortfolioAssignment junction table
    @Query("SELECT COUNT(ppa) FROM PropertyPortfolioAssignment ppa WHERE ppa.portfolio.id = :portfolioId AND ppa.isActive = true")
    long countPropertiesByPortfolioId(@Param("portfolioId") Long portfolioId);
    
    @Query("SELECT COUNT(ppa) FROM PropertyPortfolioAssignment ppa WHERE ppa.portfolio.id = :portfolioId AND ppa.isActive = true AND ppa.property.isArchived = 'N'")
    long countActivePropertiesByPortfolioId(@Param("portfolioId") Long portfolioId);
    
    
    @Query("SELECT p FROM Portfolio p JOIN FETCH p.blocks WHERE p.id = :portfolioId")
    Optional<Portfolio> findByIdWithBlocks(@Param("portfolioId") Long portfolioId);
    
    // Portfolio property count analytics - UPDATED to use PropertyPortfolioAssignment junction table
    @Query("SELECT p, COUNT(ppa) as propertyCount FROM Portfolio p " +
           "LEFT JOIN PropertyPortfolioAssignment ppa ON ppa.portfolio.id = p.id AND ppa.isActive = true " +
           "WHERE p.isActive = 'Y' " +
           "GROUP BY p.id " +
           "ORDER BY propertyCount DESC")
    List<Object[]> findPortfoliosWithPropertyCounts();
    
    // Duplicate prevention
    boolean existsByNameAndPropertyOwnerId(String name, Integer propertyOwnerId);
    boolean existsByNameAndPropertyOwnerIdIsNull(String name);
    
    Optional<Portfolio> findByNameAndPropertyOwnerId(String name, Integer propertyOwnerId);
    Optional<Portfolio> findByNameAndPropertyOwnerIdIsNull(String name);
    
    // Recent activity
    List<Portfolio> findByCreatedByOrderByCreatedAtDesc(Long userId, Pageable pageable);
    List<Portfolio> findByUpdatedAtAfterOrderByUpdatedAtDesc(LocalDateTime after, Pageable pageable);
    
    // Auto-assignment queries
    @Query("SELECT p FROM Portfolio p WHERE p.autoAssignNewProperties = 'Y' AND p.isActive = 'Y'")
    List<Portfolio> findPortfoliosWithAutoAssignment();
    
    @Query("SELECT p FROM Portfolio p WHERE p.autoAssignNewProperties = 'Y' AND " +
           "p.isActive = 'Y' AND " +
           "(p.propertyOwnerId = :propertyOwnerId OR p.isShared = 'Y')")
    List<Portfolio> findAutoAssignPortfoliosForOwner(@Param("propertyOwnerId") Integer propertyOwnerId);
}
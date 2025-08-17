// PropertyPortfolioAssignmentRepository.java - Repository for Property-Portfolio assignments
package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.PropertyPortfolioAssignment;
import site.easy.to.build.crm.entity.PortfolioAssignmentType;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.Portfolio;
import site.easy.to.build.crm.entity.SyncStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyPortfolioAssignmentRepository extends JpaRepository<PropertyPortfolioAssignment, Long> {
    
    // ===== BASIC FINDERS =====
    
    // Find all assignments for a property
    List<PropertyPortfolioAssignment> findByPropertyId(Long propertyId);
    List<PropertyPortfolioAssignment> findByPropertyIdAndIsActive(Long propertyId, Boolean isActive);
    
    // Find all assignments for a portfolio
    List<PropertyPortfolioAssignment> findByPortfolioId(Long portfolioId);
    List<PropertyPortfolioAssignment> findByPortfolioIdAndIsActive(Long portfolioId, Boolean isActive);
    
    // Find by assignment type
    List<PropertyPortfolioAssignment> findByAssignmentType(PortfolioAssignmentType assignmentType);
    List<PropertyPortfolioAssignment> findByAssignmentTypeAndIsActive(PortfolioAssignmentType assignmentType, Boolean isActive);
    
    // ===== SPECIFIC ASSIGNMENT QUERIES =====
    
    // Find specific assignment
    Optional<PropertyPortfolioAssignment> findByPropertyIdAndPortfolioIdAndAssignmentType(
        Long propertyId, Long portfolioId, PortfolioAssignmentType assignmentType);
    
    Optional<PropertyPortfolioAssignment> findByPropertyIdAndPortfolioIdAndAssignmentTypeAndIsActive(
        Long propertyId, Long portfolioId, PortfolioAssignmentType assignmentType, Boolean isActive);
    
    // Check if assignment exists
    boolean existsByPropertyIdAndPortfolioIdAndAssignmentType(
        Long propertyId, Long portfolioId, PortfolioAssignmentType assignmentType);
    
    boolean existsByPropertyIdAndPortfolioIdAndAssignmentTypeAndIsActive(
        Long propertyId, Long portfolioId, PortfolioAssignmentType assignmentType, Boolean isActive);
    
    // ===== BUSINESS LOGIC QUERIES =====
    
    // Get properties for a portfolio (THE KEY QUERY for fixing your issue)
    @Query("SELECT ppa.property FROM PropertyPortfolioAssignment ppa " +
           "WHERE ppa.portfolio.id = :portfolioId AND ppa.isActive = true " +
           "ORDER BY ppa.assignmentType, ppa.displayOrder, ppa.property.propertyName")
    List<Property> findPropertiesForPortfolio(@Param("portfolioId") Long portfolioId);
    
    // Get properties for a portfolio by assignment type
    @Query("SELECT ppa.property FROM PropertyPortfolioAssignment ppa " +
           "WHERE ppa.portfolio.id = :portfolioId AND ppa.assignmentType = :assignmentType AND ppa.isActive = true " +
           "ORDER BY ppa.displayOrder, ppa.property.propertyName")
    List<Property> findPropertiesForPortfolioByType(@Param("portfolioId") Long portfolioId, 
                                                   @Param("assignmentType") PortfolioAssignmentType assignmentType);
    
    // Get portfolios for a property
    @Query("SELECT ppa.portfolio FROM PropertyPortfolioAssignment ppa " +
           "WHERE ppa.property.id = :propertyId AND ppa.isActive = true " +
           "ORDER BY ppa.assignmentType, ppa.displayOrder, ppa.portfolio.name")
    List<Portfolio> findPortfoliosForProperty(@Param("propertyId") Long propertyId);
    
    // Get portfolios for a property by assignment type
    @Query("SELECT ppa.portfolio FROM PropertyPortfolioAssignment ppa " +
           "WHERE ppa.property.id = :propertyId AND ppa.assignmentType = :assignmentType AND ppa.isActive = true " +
           "ORDER BY ppa.displayOrder, ppa.portfolio.name")
    List<Portfolio> findPortfoliosForPropertyByType(@Param("propertyId") Long propertyId, 
                                                   @Param("assignmentType") PortfolioAssignmentType assignmentType);
    
    // Get primary portfolio for a property
    @Query("SELECT ppa.portfolio FROM PropertyPortfolioAssignment ppa " +
           "WHERE ppa.property.id = :propertyId AND ppa.assignmentType = 'PRIMARY' AND ppa.isActive = true")
    Optional<Portfolio> findPrimaryPortfolioForProperty(@Param("propertyId") Long propertyId);
    
    // ===== COUNT QUERIES =====
    
    // Count properties in portfolio
    @Query("SELECT COUNT(ppa) FROM PropertyPortfolioAssignment ppa " +
           "WHERE ppa.portfolio.id = :portfolioId AND ppa.isActive = true")
    long countPropertiesInPortfolio(@Param("portfolioId") Long portfolioId);
    
    // Count properties in portfolio by type
    @Query("SELECT COUNT(ppa) FROM PropertyPortfolioAssignment ppa " +
           "WHERE ppa.portfolio.id = :portfolioId AND ppa.assignmentType = :assignmentType AND ppa.isActive = true")
    long countPropertiesInPortfolioByType(@Param("portfolioId") Long portfolioId, 
                                         @Param("assignmentType") PortfolioAssignmentType assignmentType);
    
    // Count portfolios for property
    @Query("SELECT COUNT(ppa) FROM PropertyPortfolioAssignment ppa " +
           "WHERE ppa.property.id = :propertyId AND ppa.isActive = true")
    long countPortfoliosForProperty(@Param("propertyId") Long propertyId);
    
    // ===== SYNC QUERIES =====
    
    // Find assignments needing sync
    List<PropertyPortfolioAssignment> findBySyncStatusAndIsActive(SyncStatus syncStatus, Boolean isActive);
    
    // Find assignments by portfolio and sync status
    List<PropertyPortfolioAssignment> findByPortfolioAndSyncStatus(Portfolio portfolio, SyncStatus syncStatus);
    
    @Query("SELECT ppa FROM PropertyPortfolioAssignment ppa " +
           "WHERE ppa.syncStatus IN ('PENDING', 'FAILED') AND ppa.isActive = true " +
           "ORDER BY ppa.assignedAt")
    List<PropertyPortfolioAssignment> findAssignmentsNeedingSync();
    
    // Find assignments not synced since
    @Query("SELECT ppa FROM PropertyPortfolioAssignment ppa " +
           "WHERE ppa.isActive = true AND " +
           "(ppa.lastSyncAt IS NULL OR ppa.lastSyncAt < :cutoffTime)")
    List<PropertyPortfolioAssignment> findAssignmentsNotSyncedSince(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    // ===== ANALYTICS QUERIES =====
    
    // Get portfolio statistics
    @Query("SELECT ppa.portfolio.id, ppa.portfolio.name, COUNT(ppa) as propertyCount " +
           "FROM PropertyPortfolioAssignment ppa " +
           "WHERE ppa.isActive = true " +
           "GROUP BY ppa.portfolio.id, ppa.portfolio.name " +
           "ORDER BY propertyCount DESC")
    List<Object[]> getPortfolioPropertyCounts();
    
    // Get assignment type distribution
    @Query("SELECT ppa.assignmentType, COUNT(ppa) as assignmentCount " +
           "FROM PropertyPortfolioAssignment ppa " +
           "WHERE ppa.isActive = true " +
           "GROUP BY ppa.assignmentType " +
           "ORDER BY assignmentCount DESC")
    List<Object[]> getAssignmentTypeDistribution();
    
    // Get properties with multiple portfolio assignments
    @Query("SELECT ppa.property.id, ppa.property.propertyName, COUNT(ppa) as portfolioCount " +
           "FROM PropertyPortfolioAssignment ppa " +
           "WHERE ppa.isActive = true " +
           "GROUP BY ppa.property.id, ppa.property.propertyName " +
           "HAVING COUNT(ppa) > 1 " +
           "ORDER BY portfolioCount DESC")
    List<Object[]> getPropertiesWithMultiplePortfolios();
    
    // ===== MAINTENANCE QUERIES =====
    
    // Find assignments by user who assigned them
    List<PropertyPortfolioAssignment> findByAssignedByAndIsActive(Long assignedBy, Boolean isActive);
    
    // Find recent assignments
    @Query("SELECT ppa FROM PropertyPortfolioAssignment ppa " +
           "WHERE ppa.assignedAt >= :since AND ppa.isActive = true " +
           "ORDER BY ppa.assignedAt DESC")
    List<PropertyPortfolioAssignment> findRecentAssignments(@Param("since") LocalDateTime since);
    
    // Find assignments for cleanup (inactive or archived)
    @Query("SELECT ppa FROM PropertyPortfolioAssignment ppa " +
           "WHERE ppa.isActive = false OR ppa.assignmentType = 'ARCHIVED' " +
           "ORDER BY ppa.updatedAt")
    List<PropertyPortfolioAssignment> findAssignmentsForCleanup();
}
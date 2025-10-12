package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.Block;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.PropertyBlockAssignment;

import java.util.List;
import java.util.Optional;

/**
 * Repository for PropertyBlockAssignment entity
 * Manages the many-to-many relationship between Properties and Blocks
 * Note: This relationship is INDEPENDENT of portfolio assignments
 */
@Repository
public interface PropertyBlockAssignmentRepository extends JpaRepository<PropertyBlockAssignment, Long> {

    /**
     * Find all active assignments for a specific property
     */
    List<PropertyBlockAssignment> findByPropertyIdAndIsActive(Long propertyId, Boolean isActive);

    /**
     * Find all active assignments for a specific block
     */
    List<PropertyBlockAssignment> findByBlockIdAndIsActive(Long blockId, Boolean isActive);

    /**
     * Find all active assignments for a specific block, ordered by display_order
     */
    List<PropertyBlockAssignment> findByBlockIdAndIsActiveOrderByDisplayOrder(Long blockId, Boolean isActive);

    /**
     * Find a specific property-block assignment
     */
    Optional<PropertyBlockAssignment> findByPropertyIdAndBlockIdAndIsActive(
            Long propertyId, Long blockId, Boolean isActive);

    /**
     * Find all properties in a block
     */
    @Query("SELECT pba.property FROM PropertyBlockAssignment pba WHERE pba.block.id = :blockId AND pba.isActive = true")
    List<Property> findPropertiesByBlockId(@Param("blockId") Long blockId);

    /**
     * Find all properties in a block ordered by display_order
     */
    @Query("SELECT pba.property FROM PropertyBlockAssignment pba WHERE pba.block.id = :blockId AND pba.isActive = true ORDER BY pba.displayOrder, pba.property.propertyName")
    List<Property> findPropertiesByBlockIdOrdered(@Param("blockId") Long blockId);

    /**
     * Find block for a property (typically only one active block per property)
     */
    @Query("SELECT pba.block FROM PropertyBlockAssignment pba WHERE pba.property.id = :propertyId AND pba.isActive = true")
    Optional<Block> findBlockByPropertyId(@Param("propertyId") Long propertyId);

    /**
     * Find unassigned properties (not in any block)
     * Excludes test/block properties
     */
    @Query("SELECT p FROM Property p WHERE p.id NOT IN (SELECT pba.property.id FROM PropertyBlockAssignment pba WHERE pba.isActive = true) " +
           "AND (p.propertyType IS NULL OR p.propertyType <> 'BLOCK') " +
           "AND (p.propertyName IS NULL OR (p.propertyName NOT LIKE '%Block Property%' AND p.propertyName NOT LIKE '%Test%'))")
    List<Property> findUnassignedProperties();

    /**
     * Find standalone properties (not in any block) - Simple version
     */
    @Query("SELECT p FROM Property p WHERE p.id NOT IN (SELECT pba.property.id FROM PropertyBlockAssignment pba WHERE pba.isActive = true) " +
           "AND p.isArchived = 'N'")
    List<Property> findStandaloneProperties();

    /**
     * Find properties in a block that are also in a specific portfolio
     */
    @Query("SELECT DISTINCT pba.property FROM PropertyBlockAssignment pba " +
           "JOIN PropertyPortfolioAssignment ppa ON pba.property.id = ppa.property.id " +
           "WHERE pba.block.id = :blockId AND ppa.portfolio.id = :portfolioId " +
           "AND pba.isActive = true AND ppa.isActive = true")
    List<Property> findPropertiesByBlockIdAndPortfolioId(
            @Param("blockId") Long blockId, @Param("portfolioId") Long portfolioId);

    /**
     * Count properties in a block
     */
    @Query("SELECT COUNT(pba) FROM PropertyBlockAssignment pba WHERE pba.block.id = :blockId AND pba.isActive = true")
    Long countPropertiesByBlockId(@Param("blockId") Long blockId);

    /**
     * Check if a property is assigned to a specific block
     */
    @Query("SELECT CASE WHEN COUNT(pba) > 0 THEN true ELSE false END FROM PropertyBlockAssignment pba " +
           "WHERE pba.property.id = :propertyId AND pba.block.id = :blockId AND pba.isActive = true")
    boolean isPropertyAssignedToBlock(@Param("propertyId") Long propertyId, @Param("blockId") Long blockId);

    /**
     * Find all assignments for a property (including inactive)
     */
    List<PropertyBlockAssignment> findByPropertyId(Long propertyId);

    /**
     * Find all assignments for a block (including inactive)
     */
    List<PropertyBlockAssignment> findByBlockId(Long blockId);

    /**
     * Find properties in a block with specific property type
     */
    @Query("SELECT pba.property FROM PropertyBlockAssignment pba " +
           "WHERE pba.block.id = :blockId AND pba.property.propertyType = :propertyType AND pba.isActive = true")
    List<Property> findPropertiesByBlockIdAndType(
            @Param("blockId") Long blockId, @Param("propertyType") String propertyType);

    /**
     * Get the maximum display_order for a block (for adding new properties)
     */
    @Query("SELECT COALESCE(MAX(pba.displayOrder), 0) FROM PropertyBlockAssignment pba WHERE pba.block.id = :blockId AND pba.isActive = true")
    Integer getMaxDisplayOrderForBlock(@Param("blockId") Long blockId);
}

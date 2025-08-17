// BlockRepository.java - Repository for Block management
package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.Block;
import site.easy.to.build.crm.entity.BlockType;
import site.easy.to.build.crm.entity.SyncStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockRepository extends JpaRepository<Block, Long> {
    
    // Basic finders
    List<Block> findByPortfolioId(Long portfolioId);
    List<Block> findByPropertyOwnerId(Integer propertyOwnerId);
    List<Block> findByBlockType(BlockType blockType);
    List<Block> findByIsActive(String isActive);
    
    // Location-based queries
    List<Block> findByCity(String city);
    List<Block> findByCityIgnoreCase(String city);
    List<Block> findByPostcode(String postcode);
    List<Block> findByCountyIgnoreCase(String county);
    
    // Active blocks
    @Query("SELECT b FROM Block b WHERE b.isActive = 'Y' ORDER BY b.displayOrder, b.name")
    List<Block> findActiveBlocks();
    
    // Blocks for a specific portfolio
    @Query("SELECT b FROM Block b WHERE b.portfolio.id = :portfolioId AND b.isActive = 'Y' ORDER BY b.displayOrder, b.name")
    List<Block> findActiveBlocksByPortfolioId(@Param("portfolioId") Long portfolioId);
    
    // Property capacity queries
    @Query("SELECT b FROM Block b WHERE b.maxProperties IS NOT NULL AND " +
           "(SELECT COUNT(p) FROM Property p WHERE p.block.id = b.id) >= b.maxProperties")
    List<Block> findFullBlocks();
    
    @Query("SELECT b FROM Block b WHERE b.maxProperties IS NOT NULL AND " +
           "(SELECT COUNT(p) FROM Property p WHERE p.block.id = b.id) < b.maxProperties")
    List<Block> findBlocksWithAvailableCapacity();
    
    @Query("SELECT COUNT(p) FROM Property p WHERE p.block.id = :blockId")
    long countPropertiesByBlockId(@Param("blockId") Long blockId);
    
    @Query("SELECT COUNT(p) FROM Property p WHERE p.block.id = :blockId AND p.isArchived = 'N'")
    long countActivePropertiesByBlockId(@Param("blockId") Long blockId);
    
    // Search and filtering
    @Query("SELECT b FROM Block b WHERE " +
           "(:name IS NULL OR LOWER(b.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
           "(:city IS NULL OR LOWER(b.city) LIKE LOWER(CONCAT('%', :city, '%'))) AND " +
           "(:postcode IS NULL OR b.postcode = :postcode) AND " +
           "(:blockType IS NULL OR b.blockType = :blockType) AND " +
           "(:portfolioId IS NULL OR b.portfolio.id = :portfolioId) AND " +
           "(:propertyOwnerId IS NULL OR b.propertyOwnerId = :propertyOwnerId) AND " +
           "(:isActive IS NULL OR b.isActive = :isActive)")
    List<Block> searchBlocks(@Param("name") String name,
                           @Param("city") String city,
                           @Param("postcode") String postcode,
                           @Param("blockType") BlockType blockType,
                           @Param("portfolioId") Long portfolioId,
                           @Param("propertyOwnerId") Integer propertyOwnerId,
                           @Param("isActive") String isActive,
                           Pageable pageable);
    
    // PayProp synchronization
    List<Block> findBySyncStatus(SyncStatus syncStatus);
    List<Block> findByPayPropTagsIsNotNull();
    List<Block> findByPayPropTagsIsNull();
    
    @Query("SELECT b FROM Block b WHERE b.syncStatus = 'PENDING' OR b.syncStatus = 'FAILED'")
    List<Block> findBlocksNeedingSync();
    
    // Analytics with property relationships
    @Query("SELECT b FROM Block b JOIN FETCH b.properties WHERE b.id = :blockId")
    Optional<Block> findByIdWithProperties(@Param("blockId") Long blockId);
    
    @Query("SELECT b, COUNT(p) as propertyCount FROM Block b " +
           "LEFT JOIN b.properties p " +
           "WHERE b.portfolio.id = :portfolioId AND b.isActive = 'Y' " +
           "GROUP BY b.id " +
           "ORDER BY propertyCount DESC")
    List<Object[]> findBlocksWithPropertyCountsByPortfolio(@Param("portfolioId") Long portfolioId);
    
    // Geographic clustering
    @Query("SELECT b.city, COUNT(b) FROM Block b WHERE b.isActive = 'Y' GROUP BY b.city ORDER BY COUNT(b) DESC")
    List<Object[]> findBlockCountsByCity();
    
    @Query("SELECT b FROM Block b WHERE b.city = :city AND b.isActive = 'Y' ORDER BY b.name")
    List<Block> findActiveBlocksByCity(@Param("city") String city);
    
    // Count methods
    long countByPortfolioId(Long portfolioId);
    long countByPropertyOwnerId(Integer propertyOwnerId);
    long countByBlockType(BlockType blockType);
    long countByCity(String city);
    long countByIsActive(String isActive);
    
    // Duplicate prevention
    boolean existsByNameAndPortfolioId(String name, Long portfolioId);
    Optional<Block> findByNameAndPortfolioId(String name, Long portfolioId);
    
    // Recent activity
    List<Block> findByCreatedByOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    // ===== HIERARCHICAL BLOCK-ASSIGNMENT QUERIES =====
    
    // Find blocks with missing PayProp external IDs (for migration)
    @Query("SELECT b FROM Block b " +
           "WHERE b.isActive = 'Y' " +
           "AND (b.payPropTagNames IS NOT NULL AND b.payPropTagNames != '') " +
           "AND (b.payPropTags IS NULL OR b.payPropTags = '') " +
           "ORDER BY b.portfolio.displayOrder, b.displayOrder")
    List<Block> findBlocksWithMissingPayPropTags();
    
    // Count properties in block via assignment table (more accurate)
    @Query("SELECT COUNT(ppa) FROM PropertyPortfolioAssignment ppa " +
           "WHERE ppa.block.id = :blockId AND ppa.isActive = true")
    long countPropertiesInBlockViaAssignment(@Param("blockId") Long blockId);
    
    // Check if block name is unique within portfolio
    @Query("SELECT COUNT(b) > 0 FROM Block b " +
           "WHERE b.portfolio.id = :portfolioId " +
           "AND UPPER(b.name) = UPPER(:name) " +
           "AND b.isActive = 'Y' " +
           "AND (:excludeId IS NULL OR b.id <> :excludeId)")
    boolean existsByPortfolioAndNameIgnoreCase(@Param("portfolioId") Long portfolioId, 
                                              @Param("name") String name, 
                                              @Param("excludeId") Long excludeId);
    
    // Get next display order for portfolio
    @Query("SELECT COALESCE(MAX(b.displayOrder), 0) + 1 FROM Block b " +
           "WHERE b.portfolio.id = :portfolioId")
    Integer getNextDisplayOrderForPortfolio(@Param("portfolioId") Long portfolioId);
    
    // Generate unified Owner- format tag name
    @Query("SELECT CONCAT('Owner-', :portfolioId, '-', " +
           "REPLACE(REPLACE(REPLACE(:blockName, ' ', ''), '.', ''), '/', '')) " +
           "FROM Portfolio p WHERE p.id = :portfolioId")
    Optional<String> generateBlockTagName(@Param("portfolioId") Long portfolioId, 
                                         @Param("blockName") String blockName);
    
    // Find empty blocks (no property assignments)
    @Query("SELECT b FROM Block b " +
           "WHERE b.isActive = 'Y' AND b.id NOT IN (" +
           "    SELECT DISTINCT ppa.block.id FROM PropertyPortfolioAssignment ppa " +
           "    WHERE ppa.block.id IS NOT NULL AND ppa.isActive = true" +
           ") " +
           "ORDER BY b.portfolio.displayOrder, b.displayOrder")
    List<Block> findEmptyBlocks();
    
    // Find blocks by portfolio ordered by display order
    @Query("SELECT b FROM Block b " +
           "WHERE b.portfolio.id = :portfolioId AND b.isActive = 'Y' " +
           "ORDER BY b.displayOrder, b.name")
    List<Block> findByPortfolioIdOrderByDisplayOrder(@Param("portfolioId") Long portfolioId);
}
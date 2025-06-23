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
}
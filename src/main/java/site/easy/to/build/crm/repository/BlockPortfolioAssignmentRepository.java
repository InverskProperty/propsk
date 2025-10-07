package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.Block;
import site.easy.to.build.crm.entity.BlockPortfolioAssignment;
import site.easy.to.build.crm.entity.Portfolio;
import site.easy.to.build.crm.entity.PortfolioAssignmentType;

import java.util.List;
import java.util.Optional;

/**
 * Repository for BlockPortfolioAssignment entity
 * Manages the many-to-many relationship between Blocks and Portfolios
 */
@Repository
public interface BlockPortfolioAssignmentRepository extends JpaRepository<BlockPortfolioAssignment, Long> {

    /**
     * Find all active assignments for a specific block
     */
    List<BlockPortfolioAssignment> findByBlockIdAndIsActive(Long blockId, Boolean isActive);

    /**
     * Find all active assignments for a specific portfolio
     */
    List<BlockPortfolioAssignment> findByPortfolioIdAndIsActive(Long portfolioId, Boolean isActive);

    /**
     * Find a specific block-portfolio assignment
     */
    Optional<BlockPortfolioAssignment> findByBlockIdAndPortfolioIdAndAssignmentTypeAndIsActive(
            Long blockId, Long portfolioId, PortfolioAssignmentType assignmentType, Boolean isActive);

    /**
     * Find primary assignment for a block
     */
    Optional<BlockPortfolioAssignment> findByBlockIdAndAssignmentTypeAndIsActive(
            Long blockId, PortfolioAssignmentType assignmentType, Boolean isActive);

    /**
     * Find all blocks assigned to a portfolio
     */
    @Query("SELECT bpa.block FROM BlockPortfolioAssignment bpa WHERE bpa.portfolio.id = :portfolioId AND bpa.isActive = true")
    List<Block> findBlocksByPortfolioId(@Param("portfolioId") Long portfolioId);

    /**
     * Find all portfolios a block is assigned to
     */
    @Query("SELECT bpa.portfolio FROM BlockPortfolioAssignment bpa WHERE bpa.block.id = :blockId AND bpa.isActive = true")
    List<Portfolio> findPortfoliosByBlockId(@Param("blockId") Long blockId);

    /**
     * Find unassigned blocks (not in any portfolio)
     */
    @Query("SELECT b FROM Block b WHERE b.id NOT IN (SELECT bpa.block.id FROM BlockPortfolioAssignment bpa WHERE bpa.isActive = true)")
    List<Block> findUnassignedBlocks();

    /**
     * Count active assignments for a block
     */
    @Query("SELECT COUNT(bpa) FROM BlockPortfolioAssignment bpa WHERE bpa.block.id = :blockId AND bpa.isActive = true")
    Long countActiveAssignmentsByBlockId(@Param("blockId") Long blockId);

    /**
     * Count active assignments for a portfolio
     */
    @Query("SELECT COUNT(bpa) FROM BlockPortfolioAssignment bpa WHERE bpa.portfolio.id = :portfolioId AND bpa.isActive = true")
    Long countActiveAssignmentsByPortfolioId(@Param("portfolioId") Long portfolioId);

    /**
     * Check if a block is assigned to a specific portfolio
     */
    @Query("SELECT CASE WHEN COUNT(bpa) > 0 THEN true ELSE false END FROM BlockPortfolioAssignment bpa " +
           "WHERE bpa.block.id = :blockId AND bpa.portfolio.id = :portfolioId AND bpa.isActive = true")
    boolean isBlockAssignedToPortfolio(@Param("blockId") Long blockId, @Param("portfolioId") Long portfolioId);

    /**
     * Find all assignments for a block (including inactive)
     */
    List<BlockPortfolioAssignment> findByBlockId(Long blockId);

    /**
     * Find all assignments for a portfolio (including inactive)
     */
    List<BlockPortfolioAssignment> findByPortfolioId(Long portfolioId);
}

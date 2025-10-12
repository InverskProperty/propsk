// PortfolioBlockService.java - Service interface for Portfolio Block management
package site.easy.to.build.crm.service.portfolio;

import site.easy.to.build.crm.entity.Block;
import site.easy.to.build.crm.entity.BlockType;
import site.easy.to.build.crm.entity.Portfolio;
import site.easy.to.build.crm.entity.SyncStatus;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing Portfolio Blocks with hierarchical structure
 * and PayProp integration support
 */
public interface PortfolioBlockService {
    
    // ===== BLOCK CREATION & MANAGEMENT =====
    
    /**
     * Create a new block within a portfolio
     * @param portfolioId Portfolio to create block in
     * @param blockName Unique name for the block within portfolio
     * @param description Optional description
     * @param blockType Type of block (BUILDING, ESTATE, etc.)
     * @param createdBy User ID creating the block
     * @return Created block with generated PayProp tag name
     * @throws IllegalArgumentException if block name already exists in portfolio
     * @throws IllegalStateException if portfolio doesn't exist or is inactive
     */
    Block createBlock(Long portfolioId, String blockName, String description, 
                     BlockType blockType, Long createdBy);
    
    /**
     * Create a simple block with minimal parameters
     * @param portfolioId Portfolio to create block in
     * @param blockName Unique name for the block
     * @param createdBy User ID creating the block
     * @return Created block with default settings
     */
    Block createBlock(Long portfolioId, String blockName, Long createdBy);
    
    /**
     * Update an existing block
     * @param blockId Block to update
     * @param blockName New name (must be unique within portfolio)
     * @param description New description
     * @param blockType New block type
     * @param updatedBy User ID making the update
     * @return Updated block
     * @throws IllegalArgumentException if new name conflicts with existing block
     */
    Block updateBlock(Long blockId, String blockName, String description, 
                     BlockType blockType, Long updatedBy);
    
    /**
     * Delete a block (soft delete - marks as inactive)
     * @param blockId Block to delete
     * @param deletedBy User ID performing deletion
     * @param reassignmentOption How to handle assigned properties
     * @return Deletion result with property reassignment details
     */
    BlockDeletionResult deleteBlock(Long blockId, Long deletedBy, 
                                   PropertyReassignmentOption reassignmentOption);
    
    // ===== BLOCK RETRIEVAL =====
    
    /**
     * Find block by ID
     * @param blockId Block ID
     * @return Block if found and active
     */
    Optional<Block> findById(Long blockId);
    
    /**
     * Get all active blocks for a portfolio, ordered by display order
     * @param portfolioId Portfolio ID
     * @return List of blocks ordered by display order
     */
    List<Block> getBlocksByPortfolio(Long portfolioId);
    
    /**
     * Get blocks with property counts for portfolio dashboard
     * @param portfolioId Portfolio ID
     * @return List of blocks with property count information
     */
    List<BlockWithPropertyCount> getBlocksWithPropertyCounts(Long portfolioId);
    
    /**
     * Search blocks by criteria
     * @param criteria Search criteria
     * @return Matching blocks
     */
    List<Block> searchBlocks(BlockSearchCriteria criteria);
    
    // ===== BLOCK VALIDATION =====
    
    /**
     * Check if block name is unique within portfolio
     * @param portfolioId Portfolio ID
     * @param blockName Block name to check
     * @param excludeBlockId Block ID to exclude from check (for updates)
     * @return true if name is unique
     */
    boolean isBlockNameUnique(Long portfolioId, String blockName, Long excludeBlockId);
    
    /**
     * Validate block can be created in portfolio
     * @param portfolioId Portfolio ID
     * @param blockName Proposed block name
     * @return Validation result with any errors
     */
    BlockValidationResult validateBlockCreation(Long portfolioId, String blockName);
    
    /**
     * Check if block is at capacity
     * @param blockId Block ID
     * @return true if block has reached maximum properties
     */
    boolean isBlockAtCapacity(Long blockId);
    
    // ===== BLOCK ORDERING =====
    
    /**
     * Reorder blocks within a portfolio
     * @param portfolioId Portfolio ID
     * @param blockOrderMap Map of block ID to new display order
     * @param updatedBy User ID making the change
     */
    void reorderBlocks(Long portfolioId, java.util.Map<Long, Integer> blockOrderMap, Long updatedBy);
    
    /**
     * Move block up in display order
     * @param blockId Block to move up
     * @param updatedBy User ID making the change
     */
    void moveBlockUp(Long blockId, Long updatedBy);
    
    /**
     * Move block down in display order
     * @param blockId Block to move down
     * @param updatedBy User ID making the change
     */
    void moveBlockDown(Long blockId, Long updatedBy);
    
    // ===== PAYPROP INTEGRATION =====
    
    /**
     * Generate PayProp tag name for block (legacy method)
     * @param portfolioId Portfolio ID
     * @param blockName Block name
     * @return Generated tag name in format: PF-{PORTFOLIO}-BL-{BLOCK}
     * @deprecated Use generatePayPropTagName(Long blockId) instead
     */
    @Deprecated
    String generatePayPropTagName(Long portfolioId, String blockName);
    
    /**
     * Generate PayProp tag name for block using simplified format
     * @param blockId Block ID
     * @return Generated tag name in format: Owner-{BLOCK_ID}
     */
    String generatePayPropTagName(Long blockId);
    
    /**
     * Check if PayProp tag name would be unique
     * @param tagName Tag name to check
     * @param excludeBlockId Block ID to exclude from check
     * @return true if tag name is unique
     */
    boolean isPayPropTagNameUnique(String tagName, Long excludeBlockId);
    
    /**
     * Get blocks that need PayProp sync
     * @return List of blocks needing sync
     */
    List<Block> getBlocksNeedingSync();
    
    /**
     * Get blocks with missing PayProp external IDs
     * @return List of blocks needing migration
     */
    List<Block> getBlocksWithMissingPayPropTags();
    
    // ===== ANALYTICS & REPORTING =====
    
    /**
     * Get block statistics for portfolio
     * @param portfolioId Portfolio ID
     * @return Block statistics
     */
    BlockStatistics getBlockStatistics(Long portfolioId);
    
    /**
     * Get empty blocks (no properties assigned)
     * @return List of blocks with no property assignments
     */
    List<Block> getEmptyBlocks();
    
    /**
     * Get blocks nearing capacity
     * @param thresholdPercentage Capacity threshold (e.g., 80 for 80%)
     * @return List of blocks nearing capacity
     */
    List<Block> getBlocksNearingCapacity(double thresholdPercentage);
    
    // ===== PROPERTY ORDERING WITHIN BLOCKS =====

    /**
     * Reorder properties within a block
     * @param blockId Block ID
     * @param propertyOrderMap Map of property ID to new display order
     * @param updatedBy User ID making the change
     */
    void reorderPropertiesInBlock(Long blockId, java.util.Map<Long, Integer> propertyOrderMap, Long updatedBy);

    /**
     * Move property up in block display order
     * @param blockId Block ID
     * @param propertyId Property to move up
     * @param updatedBy User ID making the change
     */
    void movePropertyUp(Long blockId, Long propertyId, Long updatedBy);

    /**
     * Move property down in block display order
     * @param blockId Block ID
     * @param propertyId Property to move down
     * @param updatedBy User ID making the change
     */
    void movePropertyDown(Long blockId, Long propertyId, Long updatedBy);

    // ===== BLOCK CAPACITY MANAGEMENT =====

    /**
     * Set maximum properties for a block
     * @param blockId Block ID
     * @param maxProperties Maximum number of properties (null for unlimited)
     * @param updatedBy User ID making the change
     */
    void setBlockCapacity(Long blockId, Integer maxProperties, Long updatedBy);
    
    /**
     * Get current property count for block
     * @param blockId Block ID
     * @return Number of properties currently assigned to block
     */
    long getPropertyCount(Long blockId);
    
    /**
     * Get available capacity for block
     * @param blockId Block ID
     * @return Available capacity (null if unlimited)
     */
    Integer getAvailableCapacity(Long blockId);
    
    // ===== HELPER CLASSES =====
    
    /**
     * Result of block deletion operation
     */
    class BlockDeletionResult {
        private final boolean success;
        private final String message;
        private final int propertiesReassigned;
        private final Long targetBlockId;
        
        public BlockDeletionResult(boolean success, String message, int propertiesReassigned, Long targetBlockId) {
            this.success = success;
            this.message = message;
            this.propertiesReassigned = propertiesReassigned;
            this.targetBlockId = targetBlockId;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getPropertiesReassigned() { return propertiesReassigned; }
        public Long getTargetBlockId() { return targetBlockId; }
    }
    
    /**
     * Options for handling properties when deleting a block
     */
    enum PropertyReassignmentOption {
        MOVE_TO_PORTFOLIO_ONLY, // Remove block assignment, keep portfolio assignment
        MOVE_TO_SPECIFIC_BLOCK,  // Move to another block in same portfolio
        DELETE_ASSIGNMENTS       // Remove all assignments (archive properties)
    }
    
    /**
     * Block with property count information
     */
    class BlockWithPropertyCount {
        private final Block block;
        private final long propertyCount;
        private final Integer maxProperties;
        
        public BlockWithPropertyCount(Block block, long propertyCount, Integer maxProperties) {
            this.block = block;
            this.propertyCount = propertyCount;
            this.maxProperties = maxProperties;
        }
        
        // Getters
        public Block getBlock() { return block; }
        public long getPropertyCount() { return propertyCount; }
        public Integer getMaxProperties() { return maxProperties; }
        public boolean isAtCapacity() { 
            return maxProperties != null && propertyCount >= maxProperties; 
        }
        public double getCapacityPercentage() {
            return maxProperties != null ? (propertyCount * 100.0 / maxProperties) : 0.0;
        }
    }
    
    /**
     * Search criteria for blocks
     */
    class BlockSearchCriteria {
        private String name;
        private String city;
        private String postcode;
        private BlockType blockType;
        private Long portfolioId;
        private Integer propertyOwnerId;
        private SyncStatus syncStatus;
        private Boolean hasProperties;
        
        // Constructors
        public BlockSearchCriteria() {}
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        
        public String getPostcode() { return postcode; }
        public void setPostcode(String postcode) { this.postcode = postcode; }
        
        public BlockType getBlockType() { return blockType; }
        public void setBlockType(BlockType blockType) { this.blockType = blockType; }
        
        public Long getPortfolioId() { return portfolioId; }
        public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }
        
        public Integer getPropertyOwnerId() { return propertyOwnerId; }
        public void setPropertyOwnerId(Integer propertyOwnerId) { this.propertyOwnerId = propertyOwnerId; }
        
        public SyncStatus getSyncStatus() { return syncStatus; }
        public void setSyncStatus(SyncStatus syncStatus) { this.syncStatus = syncStatus; }
        
        public Boolean getHasProperties() { return hasProperties; }
        public void setHasProperties(Boolean hasProperties) { this.hasProperties = hasProperties; }
    }
    
    /**
     * Block validation result
     */
    class BlockValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        public BlockValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static BlockValidationResult success() {
            return new BlockValidationResult(true, null);
        }
        
        public static BlockValidationResult failure(String errorMessage) {
            return new BlockValidationResult(false, errorMessage);
        }
        
        // Getters
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * Block statistics for reporting
     */
    class BlockStatistics {
        private final long totalBlocks;
        private final long activeBlocks;
        private final long blocksWithProperties;
        private final long emptyBlocks;
        private final long totalProperties;
        private final double averagePropertiesPerBlock;
        
        public BlockStatistics(long totalBlocks, long activeBlocks, long blocksWithProperties, 
                              long emptyBlocks, long totalProperties, double averagePropertiesPerBlock) {
            this.totalBlocks = totalBlocks;
            this.activeBlocks = activeBlocks;
            this.blocksWithProperties = blocksWithProperties;
            this.emptyBlocks = emptyBlocks;
            this.totalProperties = totalProperties;
            this.averagePropertiesPerBlock = averagePropertiesPerBlock;
        }
        
        // Getters
        public long getTotalBlocks() { return totalBlocks; }
        public long getActiveBlocks() { return activeBlocks; }
        public long getBlocksWithProperties() { return blocksWithProperties; }
        public long getEmptyBlocks() { return emptyBlocks; }
        public long getTotalProperties() { return totalProperties; }
        public double getAveragePropertiesPerBlock() { return averagePropertiesPerBlock; }
    }
}
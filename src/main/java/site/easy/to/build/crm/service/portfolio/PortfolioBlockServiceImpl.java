// PortfolioBlockServiceImpl.java - Implementation of Portfolio Block management service
package site.easy.to.build.crm.service.portfolio;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.util.PayPropTagGenerator;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class PortfolioBlockServiceImpl implements PortfolioBlockService {
    
    private static final Logger log = LoggerFactory.getLogger(PortfolioBlockServiceImpl.class);
    
    @Autowired
    private BlockRepository blockRepository;
    
    @Autowired
    private PortfolioRepository portfolioRepository;
    
    @Autowired
    private PropertyPortfolioAssignmentRepository assignmentRepository;
    
    @Autowired
    private PortfolioAssignmentService assignmentService;

    @Autowired
    private PropertyBlockAssignmentRepository propertyBlockAssignmentRepository;

    // AuthorizationUtil is a static utility class, no injection needed
    
    // ===== BLOCK CREATION & MANAGEMENT =====
    
    @Override
    public Block createBlock(Long portfolioId, String blockName, String description, 
                           BlockType blockType, Long createdBy) {
        log.info("🏗️ Creating block '{}' in portfolio {} by user {}", blockName, portfolioId, createdBy);
        
        // Validate inputs
        if (portfolioId == null || blockName == null || blockName.trim().isEmpty() || createdBy == null) {
            throw new IllegalArgumentException("Portfolio ID, block name, and creator are required");
        }
        
        // Validate portfolio exists and is active
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
            .orElseThrow(() -> new IllegalStateException("Portfolio not found: " + portfolioId));
            
        if (!"Y".equals(portfolio.getIsActive())) {
            throw new IllegalStateException("Cannot create block in inactive portfolio: " + portfolioId);
        }
        
        // Check authorization (simplified for now - can be enhanced later)
        // TODO: Implement proper portfolio-level authorization
        if (createdBy == null) {
            throw new SecurityException("User authorization required");
        }
        
        // Validate block name is unique within portfolio
        BlockValidationResult validation = validateBlockCreation(portfolioId, blockName);
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getErrorMessage());
        }
        
        // Get next display order
        Integer displayOrder = blockRepository.getNextDisplayOrderForPortfolio(portfolioId);
        
        // Create block without PayProp tag first (need ID for simplified tag generation)
        Block block = new Block();
        block.setName(blockName.trim());
        block.setDescription(description);
        block.setBlockType(blockType != null ? blockType : BlockType.BUILDING);
        block.setPortfolio(portfolio);
        block.setSyncStatus(SyncStatus.pending);
        block.setPropertyOwnerId(portfolio.getPropertyOwnerId() != null ? portfolio.getPropertyOwnerId().intValue() : null);
        block.setIsActive("Y");
        block.setDisplayOrder(displayOrder);
        block.setCreatedBy(createdBy);
        block.setCreatedAt(LocalDateTime.now());
        block.setUpdatedAt(LocalDateTime.now());
        block.setUpdatedBy(createdBy);
        
        // Save block to get ID
        Block savedBlock = blockRepository.save(block);
        
        // Generate simplified PayProp tag name using block ID
        String payPropTagName = PayPropTagGenerator.generateBlockTag(savedBlock.getId());
        savedBlock.setPayPropTagNames(payPropTagName);
        
        // Save again with PayProp tag
        savedBlock = blockRepository.save(savedBlock);
        
        log.info("✅ Created block {} with simplified PayProp tag: {}", savedBlock.getId(), payPropTagName);
        return savedBlock;
    }
    
    @Override
    public Block createBlock(Long portfolioId, String blockName, Long createdBy) {
        return createBlock(portfolioId, blockName, null, BlockType.BUILDING, createdBy);
    }
    
    @Override
    public Block updateBlock(Long blockId, String blockName, String description, 
                           BlockType blockType, Long updatedBy) {
        log.info("🔄 Updating block {} by user {}", blockId, updatedBy);
        
        // Validate inputs
        if (blockId == null || blockName == null || blockName.trim().isEmpty() || updatedBy == null) {
            throw new IllegalArgumentException("Block ID, block name, and updater are required");
        }
        
        // Find existing block
        Block block = findById(blockId)
            .orElseThrow(() -> new IllegalStateException("Block not found: " + blockId));
        
        // Check authorization (simplified for now - can be enhanced later)  
        // TODO: Implement proper portfolio-level authorization
        if (updatedBy == null) {
            throw new SecurityException("User authorization required");
        }
        
        // Validate new name is unique (excluding current block)
        if (!isBlockNameUnique(block.getPortfolio().getId(), blockName, blockId)) {
            throw new IllegalArgumentException("Block name '" + blockName + "' already exists in this portfolio");
        }
        
        // Update fields
        block.setName(blockName.trim());
        block.setDescription(description);
        block.setBlockType(blockType);
        block.setUpdatedBy(updatedBy);
        block.setUpdatedAt(LocalDateTime.now());
        
        // With simplified Owner-{block_id} tags, the tag never changes
        // Only ensure the tag is set correctly if missing
        if (block.getPayPropTagNames() == null || block.getPayPropTagNames().trim().isEmpty()) {
            String payPropTagName = PayPropTagGenerator.generateBlockTag(block.getId());
            block.setPayPropTagNames(payPropTagName);
            block.setSyncStatus(SyncStatus.pending);
            block.setLastSyncAt(null);
            
            log.info("📝 Set missing PayProp tag: {}", payPropTagName);
        }
        
        // Save block
        Block savedBlock = blockRepository.save(block);
        
        log.info("✅ Updated block {}", savedBlock.getId());
        return savedBlock;
    }
    
    @Override
    public BlockDeletionResult deleteBlock(Long blockId, Long deletedBy, 
                                         PropertyReassignmentOption reassignmentOption) {
        log.info("🗑️ Deleting block {} by user {} with option {}", blockId, deletedBy, reassignmentOption);
        
        // Validate inputs
        if (blockId == null || deletedBy == null || reassignmentOption == null) {
            throw new IllegalArgumentException("Block ID, deleter, and reassignment option are required");
        }
        
        // Find existing block
        Block block = findById(blockId)
            .orElseThrow(() -> new IllegalStateException("Block not found: " + blockId));
        
        // Check authorization (simplified for now - can be enhanced later)
        // TODO: Implement proper portfolio-level authorization  
        if (deletedBy == null) {
            throw new SecurityException("User authorization required");
        }
        
        // Get current property assignments
        List<PropertyPortfolioAssignment> assignments = assignmentRepository
            .findByPortfolioAndSyncStatus(block.getPortfolio(), SyncStatus.synced)
            .stream()
            .filter(a -> blockId.equals(a.getBlock() != null ? a.getBlock().getId() : null))
            .collect(Collectors.toList());
        
        int propertiesReassigned = 0;
        Long targetBlockId = null;
        
        // Handle property reassignments
        switch (reassignmentOption) {
            case MOVE_TO_PORTFOLIO_ONLY:
                // Remove block assignment, keep portfolio assignment
                for (PropertyPortfolioAssignment assignment : assignments) {
                    assignment.setBlock(null);
                    assignment.setSyncStatus(SyncStatus.pending);
                    assignment.setUpdatedBy(deletedBy);
                    assignment.setUpdatedAt(LocalDateTime.now());
                    assignmentRepository.save(assignment);
                    propertiesReassigned++;
                }
                log.info("📦 Moved {} properties to portfolio-only assignments", propertiesReassigned);
                break;
                
            case DELETE_ASSIGNMENTS:
                // Remove all assignments (soft delete)
                for (PropertyPortfolioAssignment assignment : assignments) {
                    assignment.setIsActive(false);
                    assignment.setUpdatedBy(deletedBy);
                    assignment.setUpdatedAt(LocalDateTime.now());
                    assignmentRepository.save(assignment);
                    propertiesReassigned++;
                }
                log.info("🗑️ Deactivated {} property assignments", propertiesReassigned);
                break;
                
            case MOVE_TO_SPECIFIC_BLOCK:
                // This would require additional parameter for target block
                // For now, treat as MOVE_TO_PORTFOLIO_ONLY
                log.warn("⚠️ MOVE_TO_SPECIFIC_BLOCK not fully implemented, falling back to portfolio-only");
                for (PropertyPortfolioAssignment assignment : assignments) {
                    assignment.setBlock(null);
                    assignment.setSyncStatus(SyncStatus.pending);
                    assignment.setUpdatedBy(deletedBy);
                    assignment.setUpdatedAt(LocalDateTime.now());
                    assignmentRepository.save(assignment);
                    propertiesReassigned++;
                }
                break;
        }
        
        // Soft delete the block
        block.setIsActive("N");
        block.setUpdatedBy(deletedBy);
        block.setUpdatedAt(LocalDateTime.now());
        blockRepository.save(block);
        
        String message = String.format("Block '%s' deleted successfully. %d properties reassigned.", 
                                      block.getName(), propertiesReassigned);
        
        log.info("✅ {}", message);
        return new BlockDeletionResult(true, message, propertiesReassigned, targetBlockId);
    }
    
    // ===== BLOCK RETRIEVAL =====
    
    @Override
    @Transactional(readOnly = true)
    public Optional<Block> findById(Long blockId) {
        if (blockId == null) return Optional.empty();
        
        return blockRepository.findById(blockId)
            .filter(block -> "Y".equals(block.getIsActive()));
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Block> getBlocksByPortfolio(Long portfolioId) {
        if (portfolioId == null) return new ArrayList<>();
        
        return blockRepository.findByPortfolioIdOrderByDisplayOrder(portfolioId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<BlockWithPropertyCount> getBlocksWithPropertyCounts(Long portfolioId) {
        if (portfolioId == null) return new ArrayList<>();
        
        List<Object[]> results = blockRepository.findBlocksWithPropertyCountsByPortfolio(portfolioId);
        
        return results.stream()
            .map(row -> {
                Block block = (Block) row[0];
                long propertyCount = ((Number) row[1]).longValue();
                return new BlockWithPropertyCount(block, propertyCount, block.getMaxProperties());
            })
            .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Block> searchBlocks(BlockSearchCriteria criteria) {
        if (criteria == null) return blockRepository.findActiveBlocks();
        
        // Use existing search method from repository
        return blockRepository.searchBlocks(
            criteria.getName(),
            criteria.getCity(),
            criteria.getPostcode(),
            criteria.getBlockType(),
            criteria.getPortfolioId(),
            criteria.getPropertyOwnerId(),
            "Y", // isActive
            org.springframework.data.domain.Pageable.unpaged()
        );
    }
    
    // ===== BLOCK VALIDATION =====
    
    @Override
    @Transactional(readOnly = true)
    public boolean isBlockNameUnique(Long portfolioId, String blockName, Long excludeBlockId) {
        if (portfolioId == null || blockName == null || blockName.trim().isEmpty()) {
            return false;
        }
        
        return !blockRepository.existsByPortfolioAndNameIgnoreCase(portfolioId, blockName.trim(), excludeBlockId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public BlockValidationResult validateBlockCreation(Long portfolioId, String blockName) {
        if (portfolioId == null) {
            return BlockValidationResult.failure("Portfolio ID is required");
        }
        
        if (blockName == null || blockName.trim().isEmpty()) {
            return BlockValidationResult.failure("Block name is required");
        }
        
        if (blockName.trim().length() < 2) {
            return BlockValidationResult.failure("Block name must be at least 2 characters");
        }
        
        if (blockName.trim().length() > 255) {
            return BlockValidationResult.failure("Block name must be less than 255 characters");
        }
        
        // Check portfolio exists
        Optional<Portfolio> portfolio = portfolioRepository.findById(portfolioId);
        if (!portfolio.isPresent()) {
            return BlockValidationResult.failure("Portfolio not found");
        }
        
        if (!"Y".equals(portfolio.get().getIsActive())) {
            return BlockValidationResult.failure("Cannot create block in inactive portfolio");
        }
        
        // Check name uniqueness
        if (!isBlockNameUnique(portfolioId, blockName, null)) {
            return BlockValidationResult.failure("Block name '" + blockName.trim() + "' already exists in this portfolio");
        }
        
        return BlockValidationResult.success();
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean isBlockAtCapacity(Long blockId) {
        if (blockId == null) return false;
        
        Block block = findById(blockId).orElse(null);
        if (block == null || block.getMaxProperties() == null) {
            return false; // No capacity limit
        }
        
        long currentCount = getPropertyCount(blockId);
        return currentCount >= block.getMaxProperties();
    }
    
    // ===== BLOCK ORDERING =====
    
    @Override
    public void reorderBlocks(Long portfolioId, Map<Long, Integer> blockOrderMap, Long updatedBy) {
        log.info("🔄 Reordering {} blocks in portfolio {} by user {}", 
                blockOrderMap.size(), portfolioId, updatedBy);
        
        List<Block> blocks = getBlocksByPortfolio(portfolioId);
        
        for (Block block : blocks) {
            Integer newOrder = blockOrderMap.get(block.getId());
            if (newOrder != null && !newOrder.equals(block.getDisplayOrder())) {
                block.setDisplayOrder(newOrder);
                block.setUpdatedBy(updatedBy);
                block.setUpdatedAt(LocalDateTime.now());
                blockRepository.save(block);
            }
        }
        
        log.info("✅ Reordered blocks in portfolio {}", portfolioId);
    }
    
    @Override
    public void moveBlockUp(Long blockId, Long updatedBy) {
        Block block = findById(blockId)
            .orElseThrow(() -> new IllegalStateException("Block not found: " + blockId));
        
        List<Block> blocks = getBlocksByPortfolio(block.getPortfolio().getId());
        
        // Find current position and swap with previous
        for (int i = 1; i < blocks.size(); i++) {
            if (blocks.get(i).getId().equals(blockId)) {
                Block previousBlock = blocks.get(i - 1);
                Integer temp = block.getDisplayOrder();
                block.setDisplayOrder(previousBlock.getDisplayOrder());
                previousBlock.setDisplayOrder(temp);
                
                block.setUpdatedBy(updatedBy);
                block.setUpdatedAt(LocalDateTime.now());
                previousBlock.setUpdatedBy(updatedBy);
                previousBlock.setUpdatedAt(LocalDateTime.now());
                
                blockRepository.save(block);
                blockRepository.save(previousBlock);
                break;
            }
        }
    }
    
    @Override
    public void moveBlockDown(Long blockId, Long updatedBy) {
        Block block = findById(blockId)
            .orElseThrow(() -> new IllegalStateException("Block not found: " + blockId));
        
        List<Block> blocks = getBlocksByPortfolio(block.getPortfolio().getId());
        
        // Find current position and swap with next
        for (int i = 0; i < blocks.size() - 1; i++) {
            if (blocks.get(i).getId().equals(blockId)) {
                Block nextBlock = blocks.get(i + 1);
                Integer temp = block.getDisplayOrder();
                block.setDisplayOrder(nextBlock.getDisplayOrder());
                nextBlock.setDisplayOrder(temp);
                
                block.setUpdatedBy(updatedBy);
                block.setUpdatedAt(LocalDateTime.now());
                nextBlock.setUpdatedBy(updatedBy);
                nextBlock.setUpdatedAt(LocalDateTime.now());
                
                blockRepository.save(block);
                blockRepository.save(nextBlock);
                break;
            }
        }
    }
    
    // ===== PAYPROP INTEGRATION =====
    
    @Override
    @Transactional(readOnly = true)
    @Deprecated
    public String generatePayPropTagName(Long portfolioId, String blockName) {
        // This method is deprecated since we now use simplified Owner-{block_id} tags
        // Block must be created first to get an ID for tag generation
        throw new UnsupportedOperationException("Use generatePayPropTagName(Long blockId) instead - blocks must be created first to get ID for simplified tags");
    }
    
    @Override
    @Transactional(readOnly = true)
    public String generatePayPropTagName(Long blockId) {
        if (blockId == null) {
            return null;
        }
        
        try {
            return PayPropTagGenerator.generateBlockTag(blockId);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to generate PayProp tag for block {}: {}", blockId, e.getMessage());
            return null;
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean isPayPropTagNameUnique(String tagName, Long excludeBlockId) {
        if (tagName == null || tagName.trim().isEmpty()) {
            return false;
        }
        
        // Simple implementation - check if any block has this tag name
        List<Block> allBlocks = blockRepository.findAll();
        return allBlocks.stream()
            .filter(b -> excludeBlockId == null || !excludeBlockId.equals(b.getId()))
            .noneMatch(b -> tagName.trim().equalsIgnoreCase(b.getPayPropTagNames()));
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Block> getBlocksNeedingSync() {
        return blockRepository.findBlocksNeedingSync();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Block> getBlocksWithMissingPayPropTags() {
        return blockRepository.findBlocksWithMissingPayPropTags();
    }
    
    // ===== ANALYTICS & REPORTING =====
    
    @Override
    @Transactional(readOnly = true)
    public BlockStatistics getBlockStatistics(Long portfolioId) {
        if (portfolioId == null) {
            return new BlockStatistics(0, 0, 0, 0, 0, 0.0);
        }
        
        List<Block> allBlocks = blockRepository.findByPortfolioId(portfolioId);
        List<Block> activeBlocks = allBlocks.stream()
            .filter(b -> "Y".equals(b.getIsActive()))
            .collect(Collectors.toList());
        
        long totalBlocks = allBlocks.size();
        long activeBlockCount = activeBlocks.size();
        
        long blocksWithProperties = 0;
        long totalProperties = 0;
        
        for (Block block : activeBlocks) {
            long propertyCount = blockRepository.countPropertiesInBlockViaAssignment(block.getId());
            if (propertyCount > 0) {
                blocksWithProperties++;
                totalProperties += propertyCount;
            }
        }
        
        long emptyBlocks = activeBlockCount - blocksWithProperties;
        double averagePropertiesPerBlock = activeBlockCount > 0 ? 
            (double) totalProperties / activeBlockCount : 0.0;
        
        return new BlockStatistics(totalBlocks, activeBlockCount, blocksWithProperties, 
                                 emptyBlocks, totalProperties, averagePropertiesPerBlock);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Block> getEmptyBlocks() {
        return blockRepository.findEmptyBlocks();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Block> getBlocksNearingCapacity(double thresholdPercentage) {
        // This would need custom implementation based on block capacity data
        List<Block> activeBlocks = blockRepository.findActiveBlocks();
        
        return activeBlocks.stream()
            .filter(block -> block.getMaxProperties() != null)
            .filter(block -> {
                long currentCount = blockRepository.countPropertiesInBlockViaAssignment(block.getId());
                double percentage = (currentCount * 100.0) / block.getMaxProperties();
                return percentage >= thresholdPercentage;
            })
            .collect(Collectors.toList());
    }
    
    // ===== BLOCK CAPACITY MANAGEMENT =====
    
    @Override
    public void setBlockCapacity(Long blockId, Integer maxProperties, Long updatedBy) {
        Block block = findById(blockId)
            .orElseThrow(() -> new IllegalStateException("Block not found: " + blockId));
        
        block.setMaxProperties(maxProperties);
        block.setUpdatedBy(updatedBy);
        block.setUpdatedAt(LocalDateTime.now());
        
        blockRepository.save(block);
        
        log.info("🏗️ Set capacity for block {} to {}", blockId, maxProperties);
    }
    
    @Override
    @Transactional(readOnly = true)
    public long getPropertyCount(Long blockId) {
        if (blockId == null) return 0;
        
        return blockRepository.countPropertiesInBlockViaAssignment(blockId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Integer getAvailableCapacity(Long blockId) {
        Block block = findById(blockId).orElse(null);
        if (block == null || block.getMaxProperties() == null) {
            return null; // Unlimited capacity
        }

        long currentCount = getPropertyCount(blockId);
        return Math.max(0, block.getMaxProperties() - (int) currentCount);
    }

    // ===== PROPERTY ORDERING WITHIN BLOCKS =====

    @Override
    public void reorderPropertiesInBlock(Long blockId, Map<Long, Integer> propertyOrderMap, Long updatedBy) {
        log.info("🔄 Reordering {} properties in block {} by user {}",
                propertyOrderMap.size(), blockId, updatedBy);

        // Validate block exists
        findById(blockId).orElseThrow(() -> new IllegalStateException("Block not found: " + blockId));

        // Get all active assignments for this block
        List<PropertyBlockAssignment> assignments = propertyBlockAssignmentRepository
            .findByBlockIdAndIsActive(blockId, true);

        // Update display order for each assignment
        for (PropertyBlockAssignment assignment : assignments) {
            Integer newOrder = propertyOrderMap.get(assignment.getProperty().getId());
            if (newOrder != null && !newOrder.equals(assignment.getDisplayOrder())) {
                assignment.setDisplayOrder(newOrder);
                assignment.setUpdatedBy(updatedBy);
                assignment.setUpdatedAt(LocalDateTime.now());
                propertyBlockAssignmentRepository.save(assignment);
            }
        }

        log.info("✅ Reordered properties in block {}", blockId);
    }

    @Override
    public void movePropertyUp(Long blockId, Long propertyId, Long updatedBy) {
        log.info("⬆️ Moving property {} up in block {}", propertyId, blockId);

        // Get all assignments ordered by display_order
        List<PropertyBlockAssignment> assignments = propertyBlockAssignmentRepository
            .findByBlockIdAndIsActiveOrderByDisplayOrder(blockId, true);

        // Find current property and swap with previous
        for (int i = 1; i < assignments.size(); i++) {
            PropertyBlockAssignment current = assignments.get(i);
            if (current.getProperty().getId().equals(propertyId)) {
                PropertyBlockAssignment previous = assignments.get(i - 1);

                // Swap display orders
                Integer temp = current.getDisplayOrder();
                current.setDisplayOrder(previous.getDisplayOrder());
                previous.setDisplayOrder(temp);

                // Update metadata
                current.setUpdatedBy(updatedBy);
                current.setUpdatedAt(LocalDateTime.now());
                previous.setUpdatedBy(updatedBy);
                previous.setUpdatedAt(LocalDateTime.now());

                // Save both
                propertyBlockAssignmentRepository.save(current);
                propertyBlockAssignmentRepository.save(previous);

                log.info("✅ Moved property {} up in block {}", propertyId, blockId);
                return;
            }
        }

        log.warn("⚠️ Property {} is already at the top of block {}", propertyId, blockId);
    }

    @Override
    public void movePropertyDown(Long blockId, Long propertyId, Long updatedBy) {
        log.info("⬇️ Moving property {} down in block {}", propertyId, blockId);

        // Get all assignments ordered by display_order
        List<PropertyBlockAssignment> assignments = propertyBlockAssignmentRepository
            .findByBlockIdAndIsActiveOrderByDisplayOrder(blockId, true);

        // Find current property and swap with next
        for (int i = 0; i < assignments.size() - 1; i++) {
            PropertyBlockAssignment current = assignments.get(i);
            if (current.getProperty().getId().equals(propertyId)) {
                PropertyBlockAssignment next = assignments.get(i + 1);

                // Swap display orders
                Integer temp = current.getDisplayOrder();
                current.setDisplayOrder(next.getDisplayOrder());
                next.setDisplayOrder(temp);

                // Update metadata
                current.setUpdatedBy(updatedBy);
                current.setUpdatedAt(LocalDateTime.now());
                next.setUpdatedBy(updatedBy);
                next.setUpdatedAt(LocalDateTime.now());

                // Save both
                propertyBlockAssignmentRepository.save(current);
                propertyBlockAssignmentRepository.save(next);

                log.info("✅ Moved property {} down in block {}", propertyId, blockId);
                return;
            }
        }

        log.warn("⚠️ Property {} is already at the bottom of block {}", propertyId, blockId);
    }
}
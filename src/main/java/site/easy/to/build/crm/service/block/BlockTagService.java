package site.easy.to.build.crm.service.block;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.BlockRepository;
import site.easy.to.build.crm.service.tag.TagNamespaceService;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for managing block tags with proper namespace conventions
 * Handles block creation, tag assignment, and PayProp synchronization
 */
@Service
@Transactional
public class BlockTagService {
    
    private static final Logger log = LoggerFactory.getLogger(BlockTagService.class);
    
    @Autowired
    private BlockRepository blockRepository;
    
    @Autowired
    private TagNamespaceService tagNamespaceService;
    
    /**
     * Create a new block with proper namespaced tag
     * @param blockName The name of the block
     * @param portfolio The portfolio this block belongs to
     * @param createdBy User ID creating the block
     * @return Created block with namespaced tag
     */
    public Block createBlock(String blockName, Portfolio portfolio, Long createdBy) {
        // Create namespaced block tag
        String namespacedTag = tagNamespaceService.createBlockTag(portfolio.getId(), blockName);
        
        Block block = new Block();
        block.setName(blockName);
        block.setPortfolio(portfolio);
        block.setCreatedBy(createdBy);
        block.setCreatedAt(LocalDateTime.now());
        block.setUpdatedAt(LocalDateTime.now());
        
        // Set PayProp tags with namespace
        block.setPayPropTags(namespacedTag);
        block.setSyncStatus(SyncStatus.pending);
        
        Block savedBlock = blockRepository.save(block);
        log.info("Created block '{}' with namespaced tag: {}", blockName, namespacedTag);
        
        return savedBlock;
    }
    
    /**
     * Update block tags when block name changes
     * @param block The block to update
     * @param newName The new name for the block
     * @param updatedBy User ID updating the block
     * @return Updated block
     */
    public Block updateBlockName(Block block, String newName, Long updatedBy) {
        if (block == null || newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("Block and new name cannot be null or empty");
        }
        
        // Create new namespaced tag with updated name
        String newNamespacedTag = tagNamespaceService.createBlockTag(block.getPortfolio().getId(), newName);
        
        String oldTag = block.getPayPropTags();
        block.setName(newName);
        block.setPayPropTags(newNamespacedTag);
        block.setUpdatedBy(updatedBy);
        block.setUpdatedAt(LocalDateTime.now());
        block.setSyncStatus(SyncStatus.pending); // Mark for re-sync
        
        Block updatedBlock = blockRepository.save(block);
        log.info("Updated block tag from '{}' to '{}' for block ID: {}", 
            oldTag, newNamespacedTag, block.getId());
        
        return updatedBlock;
    }
    
    /**
     * Get all blocks for a portfolio with their namespaced tags
     * @param portfolio The portfolio to get blocks for
     * @return List of blocks with proper tags
     */
    public List<Block> getBlocksForPortfolio(Portfolio portfolio) {
        if (portfolio == null) {
            return new ArrayList<>();
        }
        
        List<Block> blocks = blockRepository.findActiveBlocksByPortfolioId(portfolio.getId());
        
        // Ensure all blocks have proper namespaced tags
        boolean needsUpdate = false;
        for (Block block : blocks) {
            if (block.getPayPropTags() == null || !isValidBlockTag(block.getPayPropTags())) {
                String correctTag = tagNamespaceService.createBlockTag(portfolio.getId(), block.getName());
                block.setPayPropTags(correctTag);
                block.setSyncStatus(SyncStatus.pending);
                needsUpdate = true;
                
                log.info("Fixed block tag for block '{}': {}", block.getName(), correctTag);
            }
        }
        
        if (needsUpdate) {
            blockRepository.saveAll(blocks);
        }
        
        return blocks;
    }
    
    /**
     * Convert legacy block tags to namespaced format
     * @param portfolio The portfolio containing blocks to migrate
     * @return Number of blocks updated
     */
    public int migrateLegacyBlockTags(Portfolio portfolio) {
        if (portfolio == null) {
            return 0;
        }
        
        List<Block> allBlocks = blockRepository.findByPortfolioId(portfolio.getId());
        int updatedCount = 0;
        
        for (Block block : allBlocks) {
            String currentTag = block.getPayPropTags();
            
            // Check if tag needs migration (not already namespaced)
            if (currentTag == null || !isValidBlockTag(currentTag)) {
                String namespacedTag = tagNamespaceService.createBlockTag(portfolio.getId(), block.getName());
                block.setPayPropTags(namespacedTag);
                block.setSyncStatus(SyncStatus.pending);
                updatedCount++;
                
                log.info("Migrated block '{}' from tag '{}' to '{}'", 
                    block.getName(), currentTag, namespacedTag);
            }
        }
        
        if (updatedCount > 0) {
            blockRepository.saveAll(allBlocks);
            log.info("Migrated {} block tags to namespaced format for portfolio: {}", 
                updatedCount, portfolio.getName());
        }
        
        return updatedCount;
    }
    
    /**
     * Get blocks by namespaced tag
     * @param namespacedTag The namespaced block tag to search for
     * @return List of blocks with matching tag
     */
    public List<Block> getBlocksByTag(String namespacedTag) {
        if (namespacedTag == null || !isValidBlockTag(namespacedTag)) {
            return new ArrayList<>();
        }
        
        // Use available method to find by PayProp tags and filter manually
        List<Block> allBlocks = blockRepository.findByPayPropTagsIsNotNull();
        return allBlocks.stream()
            .filter(block -> "Y".equals(block.getIsActive()))
            .filter(block -> block.getPayPropTags() != null && block.getPayPropTags().contains(namespacedTag))
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Validate if a tag is a properly formatted block tag
     * @param tag The tag to validate
     * @return true if tag follows block namespace conventions
     */
    public boolean isValidBlockTag(String tag) {
        return tagNamespaceService.filterTagsByNamespace(Arrays.asList(tag), TagNamespace.BLOCK).size() == 1;
    }
    
    /**
     * Get all unique block tags for a portfolio
     * @param portfolio The portfolio to get tags for
     * @return Set of unique block tags
     */
    public Set<String> getUniqueBlockTags(Portfolio portfolio) {
        if (portfolio == null) {
            return new HashSet<>();
        }
        
        List<Block> blocks = getBlocksForPortfolio(portfolio);
        Set<String> tags = new HashSet<>();
        
        for (Block block : blocks) {
            if (block.getPayPropTags() != null && !block.getPayPropTags().trim().isEmpty()) {
                tags.add(block.getPayPropTags());
            }
        }
        
        return tags;
    }
    
    /**
     * Generate suggested block names based on existing blocks
     * @param portfolio The portfolio to analyze
     * @return List of suggested block names
     */
    public List<String> generateSuggestedBlockNames(Portfolio portfolio) {
        if (portfolio == null) {
            return Arrays.asList("Building-A", "Building-B", "East-Wing", "West-Wing", "Tower-1");
        }
        
        List<Block> existingBlocks = getBlocksForPortfolio(portfolio);
        Set<String> existingNames = new HashSet<>();
        
        for (Block block : existingBlocks) {
            existingNames.add(block.getName().toUpperCase());
        }
        
        List<String> suggestions = new ArrayList<>();
        String[] patterns = {"Building-", "Block-", "Tower-", "Wing-", "Section-"};
        
        for (String pattern : patterns) {
            for (int i = 1; i <= 10; i++) {
                String suggestion = pattern + i;
                if (!existingNames.contains(suggestion.toUpperCase())) {
                    suggestions.add(suggestion);
                    if (suggestions.size() >= 5) {
                        break;
                    }
                }
            }
            if (suggestions.size() >= 5) {
                break;
            }
        }
        
        return suggestions;
    }
    
    /**
     * Validate block tag conflicts within a portfolio
     * @param portfolio The portfolio to check
     * @return Validation result with any conflicts found
     */
    public BlockTagValidationResult validateBlockTags(Portfolio portfolio) {
        if (portfolio == null) {
            return new BlockTagValidationResult(true, new ArrayList<>());
        }
        
        List<Block> blocks = getBlocksForPortfolio(portfolio);
        Set<String> tags = new HashSet<>();
        List<String> conflicts = new ArrayList<>();
        
        for (Block block : blocks) {
            String tag = block.getPayPropTags();
            if (tag != null && !tag.trim().isEmpty()) {
                if (tags.contains(tag)) {
                    conflicts.add("Duplicate block tag: " + tag + " (Block: " + block.getName() + ")");
                } else {
                    tags.add(tag);
                }
                
                if (!isValidBlockTag(tag)) {
                    conflicts.add("Invalid block tag format: " + tag + " (Block: " + block.getName() + ")");
                }
            }
        }
        
        return new BlockTagValidationResult(conflicts.isEmpty(), conflicts);
    }
    
    // ===== RESULT CLASSES =====
    
    public static class BlockTagValidationResult {
        private final boolean isValid;
        private final List<String> conflicts;
        
        public BlockTagValidationResult(boolean isValid, List<String> conflicts) {
            this.isValid = isValid;
            this.conflicts = conflicts;
        }
        
        public boolean isValid() { return isValid; }
        public List<String> getConflicts() { return conflicts; }
    }
}
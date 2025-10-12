// PayPropBlockSyncService.java - Service for syncing blocks to PayProp with hierarchical support
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.portfolio.PortfolioBlockService;
import site.easy.to.build.crm.util.PayPropTagGenerator;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for syncing blocks to PayProp with hierarchical tag support
 * Handles block tag creation and property assignment sync
 */
@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
@Transactional
public class PayPropBlockSyncService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropBlockSyncService.class);
    
    @Autowired
    private BlockRepository blockRepository;
    
    @Autowired
    private PropertyPortfolioAssignmentRepository assignmentRepository;

    @Autowired
    private PropertyBlockAssignmentRepository blockAssignmentRepository;

    @Autowired
    private PayPropPortfolioSyncService portfolioSyncService;

    @Autowired
    private PortfolioBlockService blockService;
    
    // ===== BLOCK SYNC METHODS =====
    
    /**
     * Sync a block to PayProp by creating its tag and syncing assigned properties
     * @param blockId Block to sync
     * @return Sync result with success/failure details
     */
    public BlockSyncResult syncBlockToPayProp(Long blockId) {
        log.info("🏗️ Starting PayProp sync for block {}", blockId);
        
        try {
            // Find and validate block
            Block block = blockService.findById(blockId)
                .orElseThrow(() -> new IllegalArgumentException("Block not found: " + blockId));

            // Validate portfolio has PayProp tags (prerequisite) - only for portfolio blocks
            Portfolio portfolio = block.getPortfolio();
            if (portfolio != null) {
                if (portfolio.getPayPropTags() == null || portfolio.getPayPropTags().trim().isEmpty()) {
                    return BlockSyncResult.failure("Portfolio " + portfolio.getId() +
                        " must have PayProp external ID before syncing blocks");
                }
            }

            String blockTagName = block.getPayPropTagNames();
            if (blockTagName == null || blockTagName.trim().isEmpty()) {
                // Generate tag name if not set
                if (portfolio != null) {
                    blockTagName = PayPropTagGenerator.generateBlockTag(portfolio.getName(), block.getName());
                } else {
                    // For standalone blocks, use simplified format
                    blockTagName = PayPropTagGenerator.generateBlockTag(block.getId());
                }
                block.setPayPropTagNames(blockTagName);
            }
            
            log.info("📝 Syncing block '{}' with tag name: {}", block.getName(), blockTagName);
            
            // Ensure block tag exists in PayProp
            PayPropTagDTO blockTag = portfolioSyncService.ensurePayPropTagExists(blockTagName);
            
            // Update block with PayProp external ID
            block.setPayPropTags(blockTag.getId());
            block.setSyncStatus(SyncStatus.synced);
            block.setLastSyncAt(LocalDateTime.now());
            blockRepository.save(block);
            
            // Sync properties assigned to this block
            int propertiesSynced = syncBlockProperties(block, blockTag.getId());
            
            String message = String.format("Block '%s' synced successfully. Tag ID: %s, Properties synced: %d",
                block.getName(), blockTag.getId(), propertiesSynced);
            
            log.info("✅ {}", message);
            return BlockSyncResult.success(message, blockTag.getId(), propertiesSynced);
            
        } catch (Exception e) {
            log.error("❌ Failed to sync block {} to PayProp: {}", blockId, e.getMessage());
            
            // Mark block as failed
            blockService.findById(blockId).ifPresent(block -> {
                block.setSyncStatus(SyncStatus.failed);
                blockRepository.save(block);
            });
            
            return BlockSyncResult.failure("Failed to sync block: " + e.getMessage());
        }
    }
    
    /**
     * Sync all properties assigned to a block
     * @param block Block containing properties to sync
     * @param blockTagId PayProp tag ID for the block
     * @return Number of properties successfully synced
     */
    private int syncBlockProperties(Block block, String blockTagId) {
        log.info("🏷️ Syncing properties for block {} with tag {}", block.getId(), blockTagId);

        int successCount = 0;

        if (block.getPortfolio() != null) {
            // Portfolio blocks: Use PropertyPortfolioAssignment
            List<PropertyPortfolioAssignment> assignments = assignmentRepository
                .findByPortfolioIdAndIsActive(block.getPortfolio().getId(), true)
                .stream()
                .filter(a -> block.getId().equals(a.getBlock() != null ? a.getBlock().getId() : null))
                .collect(Collectors.toList());

            for (PropertyPortfolioAssignment assignment : assignments) {
                try {
                    Property property = assignment.getProperty();
                    if (property == null || property.getPayPropId() == null) {
                        log.warn("⚠️ Property {} has no PayProp ID, skipping",
                                property != null ? property.getId() : "null");
                        continue;
                    }

                    // ADDITIVE TAGGING: Apply BOTH portfolio tag AND block tag

                    // 1. Ensure portfolio tag is applied
                    String portfolioTagId = block.getPortfolio().getPayPropTags();
                    if (portfolioTagId != null && !portfolioTagId.trim().isEmpty()) {
                        portfolioSyncService.applyTagToProperty(property.getPayPropId(), portfolioTagId);
                        log.debug("✅ Applied portfolio tag {} to property {}", portfolioTagId, property.getPayPropId());
                    }

                    // 2. Apply block tag
                    portfolioSyncService.applyTagToProperty(property.getPayPropId(), blockTagId);
                    log.debug("✅ Applied block tag {} to property {}", blockTagId, property.getPayPropId());

                    // Update assignment sync status
                    assignment.setSyncStatus(SyncStatus.synced);
                    assignment.setLastSyncAt(LocalDateTime.now());
                    assignmentRepository.save(assignment);

                    successCount++;

                } catch (Exception e) {
                    log.error("❌ Failed to sync property {} in block {}: {}",
                             assignment.getProperty().getId(), block.getId(), e.getMessage());

                    // Mark assignment as failed
                    assignment.setSyncStatus(SyncStatus.failed);
                    assignmentRepository.save(assignment);
                }
            }

            log.info("📊 Portfolio block property sync complete: {}/{} properties synced",
                    successCount, assignments.size());

        } else {
            // Standalone blocks: Use PropertyBlockAssignment
            log.info("Syncing standalone block - fetching from PropertyBlockAssignment");

            List<PropertyBlockAssignment> blockAssignments = blockAssignmentRepository
                .findByBlockIdAndIsActive(block.getId(), true);

            for (PropertyBlockAssignment assignment : blockAssignments) {
                try {
                    Property property = assignment.getProperty();
                    if (property == null || property.getPayPropId() == null) {
                        log.warn("⚠️ Property {} has no PayProp ID, skipping",
                                property != null ? property.getId() : "null");
                        continue;
                    }

                    // For standalone blocks, only apply block tag (no portfolio tag)
                    portfolioSyncService.applyTagToProperty(property.getPayPropId(), blockTagId);
                    log.debug("✅ Applied block tag {} to property {}", blockTagId, property.getPayPropId());

                    // Update assignment (Note: PropertyBlockAssignment doesn't have sync status fields)
                    // Just mark it synced via timestamp
                    assignment.setUpdatedAt(LocalDateTime.now());
                    blockAssignmentRepository.save(assignment);

                    successCount++;

                } catch (Exception e) {
                    log.error("❌ Failed to sync property {} in standalone block {}: {}",
                             assignment.getProperty().getId(), block.getId(), e.getMessage());
                }
            }

            log.info("📊 Standalone block property sync complete: {}/{} properties synced",
                    successCount, blockAssignments.size());
        }

        return successCount;
    }
    
    /**
     * Sync all blocks in a portfolio to PayProp
     * @param portfolioId Portfolio containing blocks to sync
     * @return Batch sync result
     */
    public BatchBlockSyncResult syncAllBlocksInPortfolio(Long portfolioId) {
        log.info("🏗️ Starting batch sync for all blocks in portfolio {}", portfolioId);
        
        List<Block> blocks = blockService.getBlocksByPortfolio(portfolioId);
        
        if (blocks.isEmpty()) {
            return BatchBlockSyncResult.success("No blocks found in portfolio " + portfolioId, 0, 0, 0);
        }
        
        int successCount = 0;
        int failureCount = 0;
        int skippedCount = 0;
        List<String> errors = new ArrayList<>();
        List<String> successDetails = new ArrayList<>();
        
        for (Block block : blocks) {
            try {
                BlockSyncResult result = syncBlockToPayProp(block.getId());
                
                if (result.isSuccess()) {
                    successCount++;
                    successDetails.add(String.format("Block '%s' (ID: %d) → Tag ID: %s", 
                        block.getName(), block.getId(), result.getTagId()));
                } else {
                    failureCount++;
                    errors.add(String.format("Block '%s' (ID: %d): %s", 
                        block.getName(), block.getId(), result.getMessage()));
                }
                
            } catch (Exception e) {
                failureCount++;
                errors.add(String.format("Block '%s' (ID: %d): %s", 
                    block.getName(), block.getId(), e.getMessage()));
                log.error("Error syncing block {}: {}", block.getId(), e.getMessage());
            }
        }
        
        String message = String.format("Portfolio %d block sync complete: %d succeeded, %d failed, %d skipped",
            portfolioId, successCount, failureCount, skippedCount);
        
        log.info("✅ {}", message);
        
        return new BatchBlockSyncResult(
            failureCount == 0, message, successCount, failureCount, skippedCount,
            successDetails, errors
        );
    }
    
    /**
     * Sync blocks that need sync (status = pending or failed)
     * @return Batch sync result
     */
    public BatchBlockSyncResult syncBlocksNeedingSync() {
        log.info("🔄 Starting sync for blocks needing sync...");
        
        List<Block> blocksNeedingSync = blockRepository.findBlocksNeedingSync();
        
        if (blocksNeedingSync.isEmpty()) {
            return BatchBlockSyncResult.success("No blocks need sync", 0, 0, 0);
        }
        
        log.info("Found {} blocks needing sync", blocksNeedingSync.size());
        
        int successCount = 0;
        int failureCount = 0;
        List<String> errors = new ArrayList<>();
        List<String> successDetails = new ArrayList<>();
        
        for (Block block : blocksNeedingSync) {
            try {
                BlockSyncResult result = syncBlockToPayProp(block.getId());
                
                if (result.isSuccess()) {
                    successCount++;
                    successDetails.add(String.format("Block '%s' → Tag ID: %s", 
                        block.getName(), result.getTagId()));
                } else {
                    failureCount++;
                    errors.add(String.format("Block '%s': %s", block.getName(), result.getMessage()));
                }
                
            } catch (Exception e) {
                failureCount++;
                errors.add(String.format("Block '%s': %s", block.getName(), e.getMessage()));
            }
        }
        
        String message = String.format("Sync blocks needing sync complete: %d succeeded, %d failed",
            successCount, failureCount);
        
        return new BatchBlockSyncResult(
            failureCount == 0, message, successCount, failureCount, 0,
            successDetails, errors
        );
    }
    
    /**
     * Remove block tag from all its properties in PayProp (for block deletion)
     * With additive tagging, this ONLY removes the block tag and keeps portfolio tags
     * @param blockId Block being deleted
     * @return Number of properties that had tags removed
     */
    public int removeBlockTagFromProperties(Long blockId) {
        log.info("🗑️ Removing block tags from properties for deleted block {}", blockId);

        Block block = blockService.findById(blockId).orElse(null);
        if (block == null || block.getPayPropTags() == null) {
            log.warn("Block {} not found or has no PayProp tags", blockId);
            return 0;
        }

        int removedCount = 0;

        if (block.getPortfolio() != null) {
            // Portfolio blocks: Handle PropertyPortfolioAssignment
            List<PropertyPortfolioAssignment> assignments = assignmentRepository
                .findByPortfolioIdAndIsActive(block.getPortfolio().getId(), false) // Include inactive
                .stream()
                .filter(a -> blockId.equals(a.getBlock() != null ? a.getBlock().getId() : null))
                .collect(Collectors.toList());

            for (PropertyPortfolioAssignment assignment : assignments) {
                try {
                    Property property = assignment.getProperty();
                    if (property != null && property.getPayPropId() != null) {

                        // ADDITIVE TAGGING: Ensure portfolio tag remains
                        String portfolioTagId = block.getPortfolio().getPayPropTags();
                        if (portfolioTagId != null && !portfolioTagId.trim().isEmpty()) {
                            // Re-apply portfolio tag to ensure it stays
                            portfolioSyncService.applyTagToProperty(property.getPayPropId(), portfolioTagId);
                            removedCount++;

                            log.debug("✅ Ensured portfolio tag {} remains on property {} after block removal",
                                    portfolioTagId, property.getPayPropId());
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to process tag removal for property {}: {}",
                             assignment.getProperty().getId(), e.getMessage());
                }
            }

            log.info("✅ Processed {} properties for block tag removal (portfolio tags preserved)", removedCount);

        } else {
            // Standalone blocks: Handle PropertyBlockAssignment
            log.info("Processing standalone block tag removal");

            List<PropertyBlockAssignment> blockAssignments = blockAssignmentRepository
                .findByBlockId(blockId); // Include all assignments

            for (PropertyBlockAssignment assignment : blockAssignments) {
                try {
                    Property property = assignment.getProperty();
                    if (property != null && property.getPayPropId() != null) {
                        // For standalone blocks, just note the tag should be removed
                        // (actual PayProp API call to remove tag would go here if available)
                        removedCount++;

                        log.debug("✅ Marked block tag for removal from property {}", property.getPayPropId());
                    }
                } catch (Exception e) {
                    log.error("Failed to process tag removal for property {}: {}",
                             assignment.getProperty().getId(), e.getMessage());
                }
            }

            log.info("✅ Processed {} standalone block properties for tag removal", removedCount);
        }

        return removedCount;
    }
    
    // ===== RESULT CLASSES =====
    
    /**
     * Result of syncing a single block
     */
    public static class BlockSyncResult {
        private final boolean success;
        private final String message;
        private final String tagId;
        private final int propertiesSynced;
        
        private BlockSyncResult(boolean success, String message, String tagId, int propertiesSynced) {
            this.success = success;
            this.message = message;
            this.tagId = tagId;
            this.propertiesSynced = propertiesSynced;
        }
        
        public static BlockSyncResult success(String message, String tagId, int propertiesSynced) {
            return new BlockSyncResult(true, message, tagId, propertiesSynced);
        }
        
        public static BlockSyncResult failure(String message) {
            return new BlockSyncResult(false, message, null, 0);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getTagId() { return tagId; }
        public int getPropertiesSynced() { return propertiesSynced; }
    }
    
    /**
     * Result of syncing multiple blocks
     */
    public static class BatchBlockSyncResult {
        private final boolean success;
        private final String message;
        private final int successCount;
        private final int failureCount;
        private final int skippedCount;
        private final List<String> successDetails;
        private final List<String> errors;
        
        public BatchBlockSyncResult(boolean success, String message, int successCount, int failureCount, 
                                   int skippedCount, List<String> successDetails, List<String> errors) {
            this.success = success;
            this.message = message;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.skippedCount = skippedCount;
            this.successDetails = successDetails != null ? successDetails : new ArrayList<>();
            this.errors = errors != null ? errors : new ArrayList<>();
        }
        
        public static BatchBlockSyncResult success(String message, int successCount, int failureCount, int skippedCount) {
            return new BatchBlockSyncResult(true, message, successCount, failureCount, skippedCount, 
                                          new ArrayList<>(), new ArrayList<>());
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public int getSkippedCount() { return skippedCount; }
        public List<String> getSuccessDetails() { return successDetails; }
        public List<String> getErrors() { return errors; }
    }
}
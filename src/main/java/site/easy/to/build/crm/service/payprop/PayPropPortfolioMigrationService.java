package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.portfolio.PortfolioAssignmentService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for migrating portfolios with missing PayProp external IDs
 * This fixes the root cause where portfolios have tag names but not external IDs
 */
@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropPortfolioMigrationService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropPortfolioMigrationService.class);
    
    @Autowired
    private PortfolioRepository portfolioRepository;
    
    @Autowired
    private PropertyPortfolioAssignmentRepository assignmentRepository;
    
    @Autowired
    private PayPropPortfolioSyncService payPropPortfolioSyncService;
    
    @Autowired
    private PortfolioAssignmentService portfolioAssignmentService;
    
    @Autowired
    private BlockRepository blockRepository;
    
    @Autowired(required = false)
    private PayPropBlockSyncService blockSyncService;
    
    /**
     * Fix all portfolios with missing PayProp external IDs
     * This addresses the root cause: payprop_tags = NULL but payprop_tag_names populated
     */
    @Transactional
    public MigrationResult fixBrokenPortfolios() {
        log.info("🔄 Starting migration of portfolios with missing PayProp tags...");
        
        // Find portfolios with missing external IDs
        List<Portfolio> brokenPortfolios = findBrokenPortfolios();
        
        log.info("Found {} portfolios needing PayProp tag fix", brokenPortfolios.size());
        
        if (brokenPortfolios.isEmpty()) {
            return MigrationResult.success("No broken portfolios found", 0, 0, 0);
        }
        
        int fixed = 0;
        int failed = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        List<String> fixedPortfolios = new ArrayList<>();
        
        for (Portfolio portfolio : brokenPortfolios) {
            try {
                log.info("🔧 Fixing portfolio {}: '{}'", portfolio.getId(), portfolio.getName());
                
                String tagName = portfolio.getPayPropTagNames();
                if (tagName == null || tagName.trim().isEmpty()) {
                    log.warn("⚠️ Portfolio {} has no tag name, skipping", portfolio.getId());
                    skipped++;
                    continue;
                }
                
                // Use enhanced tag creation method
                PayPropTagDTO tagResult = payPropPortfolioSyncService.ensurePayPropTagExists(tagName);
                
                // Update portfolio with PayProp external ID
                portfolio.setPayPropTags(tagResult.getId());
                portfolio.setSyncStatus(SyncStatus.synced);
                portfolio.setLastSyncAt(LocalDateTime.now());
                portfolioRepository.save(portfolio);
                
                // Sync pending property assignments
                int syncedAssignments = syncPendingAssignments(portfolio);
                
                fixed++;
                fixedPortfolios.add(String.format("Portfolio %d ('%s') -> Tag ID: %s, Synced %d assignments", 
                    portfolio.getId(), portfolio.getName(), tagResult.getId(), syncedAssignments));
                
                log.info("✅ Fixed portfolio {} - Tag ID: {}, Synced {} assignments", 
                    portfolio.getId(), tagResult.getId(), syncedAssignments);
                
            } catch (Exception e) {
                failed++;
                String errorMsg = String.format("Portfolio %d ('%s'): %s", 
                    portfolio.getId(), portfolio.getName(), e.getMessage());
                errors.add(errorMsg);
                log.error("❌ Failed to fix portfolio {}: {}", portfolio.getId(), e.getMessage());
            }
        }
        
        log.info("✅ Migration complete: {} fixed, {} failed, {} skipped", fixed, failed, skipped);
        
        return new MigrationResult(
            failed == 0,
            String.format("Migration completed: %d fixed, %d failed, %d skipped", fixed, failed, skipped),
            fixed, failed, skipped, fixedPortfolios, errors
        );
    }
    
    /**
     * Sync pending assignments for a fixed portfolio
     */
    private int syncPendingAssignments(Portfolio portfolio) {
        List<PropertyPortfolioAssignment> pendingAssignments = 
            assignmentRepository.findByPortfolioAndSyncStatus(portfolio, SyncStatus.pending);
        
        int syncedCount = 0;
        
        for (PropertyPortfolioAssignment assignment : pendingAssignments) {
            try {
                // Call the assignment service to sync this specific assignment
                Property property = assignment.getProperty();
                if (property != null && property.getPayPropId() != null) {
                    
                    // Apply PayProp tag to property
                    payPropPortfolioSyncService.applyTagToProperty(
                        property.getPayPropId(), 
                        portfolio.getPayPropTags()
                    );
                    
                    // Update assignment status
                    assignment.setSyncStatus(SyncStatus.synced);
                    assignment.setLastSyncAt(LocalDateTime.now());
                    assignmentRepository.save(assignment);
                    
                    syncedCount++;
                    log.debug("✅ Synced assignment {} to PayProp", assignment.getId());
                }
            } catch (Exception e) {
                log.error("❌ Failed to sync assignment {}: {}", assignment.getId(), e.getMessage());
                // Continue with other assignments
            }
        }
        
        return syncedCount;
    }
    
    /**
     * Find portfolios that have tag names but missing external IDs
     */
    public List<Portfolio> findBrokenPortfolios() {
        return portfolioRepository.findAll().stream()
            .filter(p -> p.getPayPropTagNames() != null && 
                        !p.getPayPropTagNames().trim().isEmpty() &&
                        (p.getPayPropTags() == null || p.getPayPropTags().trim().isEmpty()))
            .collect(Collectors.toList());
    }
    
    /**
     * Get summary of portfolios needing migration
     */
    public MigrationSummary getMigrationSummary() {
        List<Portfolio> brokenPortfolios = findBrokenPortfolios();
        
        List<PropertyPortfolioAssignment> pendingAssignments = 
            assignmentRepository.findAll().stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsActive()) && 
                           a.getSyncStatus() == SyncStatus.pending)
                .collect(Collectors.toList());
        
        return new MigrationSummary(
            brokenPortfolios.size(),
            pendingAssignments.size(),
            brokenPortfolios.stream()
                .map(p -> String.format("Portfolio %d: '%s' (Tag: %s)", 
                    p.getId(), p.getName(), p.getPayPropTagNames()))
                .collect(Collectors.toList())
        );
    }
    
    /**
     * Migration result class
     */
    public static class MigrationResult {
        private final boolean success;
        private final String message;
        private final int fixedCount;
        private final int failedCount;
        private final int skippedCount;
        private final List<String> fixedPortfolios;
        private final List<String> errors;
        
        public MigrationResult(boolean success, String message, int fixedCount, int failedCount, 
                             int skippedCount, List<String> fixedPortfolios, List<String> errors) {
            this.success = success;
            this.message = message;
            this.fixedCount = fixedCount;
            this.failedCount = failedCount;
            this.skippedCount = skippedCount;
            this.fixedPortfolios = fixedPortfolios != null ? fixedPortfolios : new ArrayList<>();
            this.errors = errors != null ? errors : new ArrayList<>();
        }
        
        public static MigrationResult success(String message, int fixedCount, int failedCount, int skippedCount) {
            return new MigrationResult(true, message, fixedCount, failedCount, skippedCount, new ArrayList<>(), new ArrayList<>());
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getFixedCount() { return fixedCount; }
        public int getFailedCount() { return failedCount; }
        public int getSkippedCount() { return skippedCount; }
        public List<String> getFixedPortfolios() { return fixedPortfolios; }
        public List<String> getErrors() { return errors; }
    }
    
    /**
     * Migration summary class
     */
    public static class MigrationSummary {
        private final int brokenPortfoliosCount;
        private final int pendingAssignmentsCount;
        private final List<String> brokenPortfolioDetails;
        
        public MigrationSummary(int brokenPortfoliosCount, int pendingAssignmentsCount, List<String> brokenPortfolioDetails) {
            this.brokenPortfoliosCount = brokenPortfoliosCount;
            this.pendingAssignmentsCount = pendingAssignmentsCount;
            this.brokenPortfolioDetails = brokenPortfolioDetails;
        }
        
        // Getters
        public int getBrokenPortfoliosCount() { return brokenPortfoliosCount; }
        public int getPendingAssignmentsCount() { return pendingAssignmentsCount; }
        public List<String> getBrokenPortfolioDetails() { return brokenPortfolioDetails; }
        
        public boolean needsMigration() {
            return brokenPortfoliosCount > 0 || pendingAssignmentsCount > 0;
        }
    }
    
    // ===== ENHANCED MIGRATION METHODS FOR BLOCKS (Task 3.3) =====
    
    /**
     * Enhanced migration that includes both portfolios and blocks
     */
    @Transactional
    public EnhancedMigrationResult fixBrokenPortfoliosAndBlocks() {
        log.info("🏗️ Starting enhanced migration for portfolios and blocks with missing PayProp tags...");
        
        // Step 1: Fix portfolios first
        MigrationResult portfolioResult = fixBrokenPortfolios();
        
        // Step 2: Fix blocks (if block sync service is available)
        BlockMigrationResult blockResult = new BlockMigrationResult(0, 0, 0, new ArrayList<>());
        
        if (blockSyncService != null) {
            blockResult = fixBrokenBlocks();
        } else {
            log.warn("⚠️ PayPropBlockSyncService not available, skipping block migration");
        }
        
        // Combine results
        String combinedMessage = String.format(
            "Enhanced migration completed. Portfolios: %d fixed, %d failed. Blocks: %d fixed, %d failed.",
            portfolioResult.getFixedCount(), portfolioResult.getFailedCount(),
            blockResult.getFixedCount(), blockResult.getFailedCount()
        );
        
        boolean overallSuccess = portfolioResult.isSuccess() && blockResult.isSuccess();
        
        return new EnhancedMigrationResult(
            overallSuccess, combinedMessage, portfolioResult, blockResult
        );
    }
    
    /**
     * Fix blocks with missing PayProp external IDs
     */
    @Transactional
    public BlockMigrationResult fixBrokenBlocks() {
        log.info("🏗️ Starting migration of blocks with missing PayProp tags...");
        
        if (blockSyncService == null) {
            return new BlockMigrationResult(0, 0, 0, 
                Arrays.asList("PayPropBlockSyncService not available"));
        }
        
        // Find blocks with missing external IDs
        List<Block> brokenBlocks = findBrokenBlocks();
        
        if (brokenBlocks.isEmpty()) {
            log.info("✅ No blocks need migration");
            return new BlockMigrationResult(0, 0, 0, new ArrayList<>());
        }
        
        log.info("🔍 Found {} blocks needing migration", brokenBlocks.size());
        
        int fixedCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        List<String> errors = new ArrayList<>();
        
        for (Block block : brokenBlocks) {
            try {
                // Ensure the parent portfolio is synced first
                Portfolio portfolio = block.getPortfolio();
                if (portfolio.getPayPropTags() == null || portfolio.getPayPropTags().trim().isEmpty()) {
                    log.warn("⚠️ Skipping block {} - parent portfolio {} not synced", 
                            block.getId(), portfolio.getId());
                    skippedCount++;
                    continue;
                }
                
                log.info("🔄 Migrating block {}: '{}' in portfolio '{}'", 
                        block.getId(), block.getName(), portfolio.getName());
                
                // Sync the block using PayPropBlockSyncService
                PayPropBlockSyncService.BlockSyncResult syncResult = 
                    blockSyncService.syncBlockToPayProp(block.getId());
                
                if (syncResult.isSuccess()) {
                    fixedCount++;
                    log.info("✅ Successfully migrated block {}: {}", block.getId(), syncResult.getMessage());
                } else {
                    failedCount++;
                    String error = String.format("Block '%s' (ID: %d): %s", 
                        block.getName(), block.getId(), syncResult.getMessage());
                    errors.add(error);
                    log.error("❌ Failed to migrate block {}: {}", block.getId(), syncResult.getMessage());
                }
                
            } catch (Exception e) {
                failedCount++;
                String error = String.format("Block '%s' (ID: %d): %s", 
                    block.getName(), block.getId(), e.getMessage());
                errors.add(error);
                log.error("❌ Exception during block {} migration: {}", block.getId(), e.getMessage());
            }
        }
        
        String message = String.format("Block migration completed: %d fixed, %d failed, %d skipped", 
                                      fixedCount, failedCount, skippedCount);
        
        log.info("📊 {}", message);
        
        return new BlockMigrationResult(fixedCount, failedCount, skippedCount, errors);
    }
    
    /**
     * Find blocks that have tag names but missing external IDs
     */
    public List<Block> findBrokenBlocks() {
        return blockRepository.findAll().stream()
            .filter(block -> "Y".equals(block.getIsActive()))
            .filter(block -> block.getPayPropTagNames() != null && 
                           !block.getPayPropTagNames().trim().isEmpty() &&
                           (block.getPayPropTags() == null || block.getPayPropTags().trim().isEmpty()))
            .collect(Collectors.toList());
    }
    
    /**
     * Get enhanced summary including both portfolios and blocks
     */
    public EnhancedMigrationSummary getEnhancedMigrationSummary() {
        // Get existing portfolio summary
        MigrationSummary portfolioSummary = getMigrationSummary();
        
        // Get block summary
        List<Block> brokenBlocks = findBrokenBlocks();
        
        List<String> brokenBlockDetails = brokenBlocks.stream()
            .map(b -> String.format("Block %d: '%s' in Portfolio '%s' (Tag: %s)", 
                b.getId(), b.getName(), 
                b.getPortfolio() != null ? b.getPortfolio().getName() : "Unknown",
                b.getPayPropTagNames()))
            .collect(Collectors.toList());
        
        // Count blocks needing sync (those with external IDs but pending status)
        long blocksNeedingSync = blockRepository.findAll().stream()
            .filter(b -> "Y".equals(b.getIsActive()))
            .filter(b -> b.getPayPropTags() != null && !b.getPayPropTags().trim().isEmpty())
            .filter(b -> b.getSyncStatus() == SyncStatus.pending)
            .count();
        
        return new EnhancedMigrationSummary(
            portfolioSummary.getBrokenPortfoliosCount(),
            portfolioSummary.getPendingAssignmentsCount(),
            portfolioSummary.getBrokenPortfolioDetails(),
            brokenBlocks.size(),
            (int) blocksNeedingSync,
            brokenBlockDetails,
            blockSyncService != null
        );
    }
    
    /**
     * Migrate blocks for specific portfolio
     */
    @Transactional 
    public BlockMigrationResult fixBlocksInPortfolio(Long portfolioId) {
        log.info("🏗️ Starting block migration for portfolio {}", portfolioId);
        
        if (blockSyncService == null) {
            return new BlockMigrationResult(0, 0, 0, 
                Arrays.asList("PayPropBlockSyncService not available"));
        }
        
        // Find broken blocks in this portfolio
        List<Block> brokenBlocks = findBrokenBlocks().stream()
            .filter(b -> portfolioId.equals(b.getPortfolio().getId()))
            .collect(Collectors.toList());
        
        if (brokenBlocks.isEmpty()) {
            log.info("✅ No blocks in portfolio {} need migration", portfolioId);
            return new BlockMigrationResult(0, 0, 0, new ArrayList<>());
        }
        
        // Use the block sync service to sync all blocks in portfolio
        try {
            PayPropBlockSyncService.BatchBlockSyncResult batchResult = 
                blockSyncService.syncAllBlocksInPortfolio(portfolioId);
            
            return new BlockMigrationResult(
                batchResult.getSuccessCount(),
                batchResult.getFailureCount(),
                batchResult.getSkippedCount(),
                batchResult.getErrors()
            );
            
        } catch (Exception e) {
            log.error("❌ Failed to migrate blocks in portfolio {}: {}", portfolioId, e.getMessage());
            return new BlockMigrationResult(0, brokenBlocks.size(), 0, 
                Arrays.asList("Portfolio " + portfolioId + ": " + e.getMessage()));
        }
    }
    
    // ===== ENHANCED RESULT CLASSES =====
    
    /**
     * Result of enhanced migration (portfolios + blocks)
     */
    public static class EnhancedMigrationResult {
        private final boolean success;
        private final String message;
        private final MigrationResult portfolioResult;
        private final BlockMigrationResult blockResult;
        
        public EnhancedMigrationResult(boolean success, String message, 
                                     MigrationResult portfolioResult, BlockMigrationResult blockResult) {
            this.success = success;
            this.message = message;
            this.portfolioResult = portfolioResult;
            this.blockResult = blockResult;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public MigrationResult getPortfolioResult() { return portfolioResult; }
        public BlockMigrationResult getBlockResult() { return blockResult; }
        
        public int getTotalFixed() {
            return portfolioResult.getFixedCount() + blockResult.getFixedCount();
        }
        
        public int getTotalFailed() {
            return portfolioResult.getFailedCount() + blockResult.getFailedCount();
        }
    }
    
    /**
     * Result of block migration
     */
    public static class BlockMigrationResult {
        private final int fixedCount;
        private final int failedCount;
        private final int skippedCount;
        private final List<String> errors;
        
        public BlockMigrationResult(int fixedCount, int failedCount, int skippedCount, List<String> errors) {
            this.fixedCount = fixedCount;
            this.failedCount = failedCount;
            this.skippedCount = skippedCount;
            this.errors = errors != null ? errors : new ArrayList<>();
        }
        
        // Getters
        public int getFixedCount() { return fixedCount; }
        public int getFailedCount() { return failedCount; }
        public int getSkippedCount() { return skippedCount; }
        public List<String> getErrors() { return errors; }
        
        public boolean isSuccess() { return failedCount == 0; }
        
        public String getMessage() {
            return String.format("Block migration: %d fixed, %d failed, %d skipped", 
                                fixedCount, failedCount, skippedCount);
        }
    }
    
    /**
     * Enhanced migration summary including blocks
     */
    public static class EnhancedMigrationSummary {
        private final int brokenPortfoliosCount;
        private final int pendingAssignmentsCount;
        private final List<String> brokenPortfolioDetails;
        private final int brokenBlocksCount;
        private final int blocksNeedingSyncCount;
        private final List<String> brokenBlockDetails;
        private final boolean blockSyncServiceAvailable;
        
        public EnhancedMigrationSummary(int brokenPortfoliosCount, int pendingAssignmentsCount, 
                                      List<String> brokenPortfolioDetails, int brokenBlocksCount,
                                      int blocksNeedingSyncCount, List<String> brokenBlockDetails,
                                      boolean blockSyncServiceAvailable) {
            this.brokenPortfoliosCount = brokenPortfoliosCount;
            this.pendingAssignmentsCount = pendingAssignmentsCount;
            this.brokenPortfolioDetails = brokenPortfolioDetails;
            this.brokenBlocksCount = brokenBlocksCount;
            this.blocksNeedingSyncCount = blocksNeedingSyncCount;
            this.brokenBlockDetails = brokenBlockDetails;
            this.blockSyncServiceAvailable = blockSyncServiceAvailable;
        }
        
        // Getters
        public int getBrokenPortfoliosCount() { return brokenPortfoliosCount; }
        public int getPendingAssignmentsCount() { return pendingAssignmentsCount; }
        public List<String> getBrokenPortfolioDetails() { return brokenPortfolioDetails; }
        public int getBrokenBlocksCount() { return brokenBlocksCount; }
        public int getBlocksNeedingSyncCount() { return blocksNeedingSyncCount; }
        public List<String> getBrokenBlockDetails() { return brokenBlockDetails; }
        public boolean isBlockSyncServiceAvailable() { return blockSyncServiceAvailable; }
        
        public boolean needsMigration() {
            return brokenPortfoliosCount > 0 || pendingAssignmentsCount > 0 || 
                   brokenBlocksCount > 0 || blocksNeedingSyncCount > 0;
        }
        
        public boolean needsBlockMigration() {
            return brokenBlocksCount > 0 || blocksNeedingSyncCount > 0;
        }
    }
}
package site.easy.to.build.crm.service.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.tag.TagNamespaceService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for migrating existing tags to namespace format
 * Fixes the issue where existing portfolios lost property assignments after namespace implementation
 */
@Service
@Transactional
public class TagNamespaceMigrationService {
    
    private static final Logger log = LoggerFactory.getLogger(TagNamespaceMigrationService.class);
    
    @Autowired
    private PortfolioRepository portfolioRepository;
    
    @Autowired
    private BlockRepository blockRepository;
    
    @Autowired
    private TagNamespaceService tagNamespaceService;
    
    /**
     * Migrate all existing portfolio PayProp tags to namespaced format
     * This fixes the issue where existing portfolios don't show properties
     */
    public MigrationResult migratePortfolioTags() {
        log.info("Starting migration of portfolio PayProp tags to namespaced format");
        
        int updated = 0;
        int skipped = 0;
        int errors = 0;
        
        try {
            List<Portfolio> allPortfolios = portfolioRepository.findAll();
            log.info("Found {} portfolios to check for migration", allPortfolios.size());
            
            for (Portfolio portfolio : allPortfolios) {
                try {
                    String currentTag = portfolio.getPayPropTags();
                    
                    // Skip if no PayProp tag or already namespaced
                    if (currentTag == null || currentTag.trim().isEmpty()) {
                        skipped++;
                        continue;
                    }
                    
                    if (TagNamespace.isValidNamespacedTag(currentTag)) {
                        log.debug("Portfolio '{}' already has namespaced tag: {}", portfolio.getName(), currentTag);
                        skipped++;
                        continue;
                    }
                    
                    // Convert to namespaced format
                    String namespacedTag = tagNamespaceService.createPortfolioTag(portfolio.getName());
                    String oldTag = portfolio.getPayPropTags();
                    
                    portfolio.setPayPropTags(namespacedTag);
                    portfolio.setSyncStatus(SyncStatus.pending); // Mark for re-sync with PayProp
                    portfolio.setUpdatedAt(LocalDateTime.now());
                    
                    portfolioRepository.save(portfolio);
                    updated++;
                    
                    log.info("Migrated portfolio '{}' tag from '{}' to '{}'", 
                        portfolio.getName(), oldTag, namespacedTag);
                        
                } catch (Exception e) {
                    errors++;
                    log.error("Error migrating portfolio '{}': {}", portfolio.getName(), e.getMessage());
                }
            }
            
            log.info("Portfolio tag migration completed: {} updated, {} skipped, {} errors", updated, skipped, errors);
            return new MigrationResult(updated, skipped, errors, "Portfolio tags migrated successfully");
            
        } catch (Exception e) {
            log.error("Fatal error during portfolio tag migration: {}", e.getMessage());
            return new MigrationResult(updated, skipped, errors + 1, "Migration failed: " + e.getMessage());
        }
    }
    
    /**
     * Migrate all existing block PayProp tags to namespaced format
     */
    public MigrationResult migrateBlockTags() {
        log.info("Starting migration of block PayProp tags to namespaced format");
        
        int updated = 0;
        int skipped = 0;
        int errors = 0;
        
        try {
            List<Block> allBlocks = blockRepository.findAll();
            log.info("Found {} blocks to check for migration", allBlocks.size());
            
            for (Block block : allBlocks) {
                try {
                    String currentTag = block.getPayPropTags();
                    
                    // Skip if no PayProp tag or already namespaced
                    if (currentTag == null || currentTag.trim().isEmpty()) {
                        skipped++;
                        continue;
                    }
                    
                    if (TagNamespace.isValidNamespacedTag(currentTag)) {
                        log.debug("Block '{}' already has namespaced tag: {}", block.getName(), currentTag);
                        skipped++;
                        continue;
                    }
                    
                    // Convert to namespaced format
                    String namespacedTag = tagNamespaceService.createBlockTag(block.getPortfolio().getId(), block.getName());
                    String oldTag = block.getPayPropTags();
                    
                    block.setPayPropTags(namespacedTag);
                    block.setSyncStatus(SyncStatus.pending); // Mark for re-sync with PayProp
                    block.setUpdatedAt(LocalDateTime.now());
                    
                    blockRepository.save(block);
                    updated++;
                    
                    log.info("Migrated block '{}' tag from '{}' to '{}'", 
                        block.getName(), oldTag, namespacedTag);
                        
                } catch (Exception e) {
                    errors++;
                    log.error("Error migrating block '{}': {}", block.getName(), e.getMessage());
                }
            }
            
            log.info("Block tag migration completed: {} updated, {} skipped, {} errors", updated, skipped, errors);
            return new MigrationResult(updated, skipped, errors, "Block tags migrated successfully");
            
        } catch (Exception e) {
            log.error("Fatal error during block tag migration: {}", e.getMessage());
            return new MigrationResult(updated, skipped, errors + 1, "Migration failed: " + e.getMessage());
        }
    }
    
    /**
     * Migrate all tags (portfolios and blocks) to namespaced format
     */
    public MigrationSummary migrateAllTags() {
        log.info("Starting complete tag namespace migration");
        
        MigrationResult portfolioResult = migratePortfolioTags();
        MigrationResult blockResult = migrateBlockTags();
        
        MigrationSummary summary = new MigrationSummary(portfolioResult, blockResult);
        
        log.info("Complete tag migration finished. Portfolios: {} updated, Blocks: {} updated", 
            portfolioResult.getUpdated(), blockResult.getUpdated());
            
        return summary;
    }
    
    /**
     * Check migration status - how many entities need migration
     */
    public MigrationStatus checkMigrationStatus() {
        int portfoliosNeedingMigration = 0;
        int blocksNeedingMigration = 0;
        
        try {
            List<Portfolio> allPortfolios = portfolioRepository.findAll();
            for (Portfolio portfolio : allPortfolios) {
                String tag = portfolio.getPayPropTags();
                if (tag != null && !tag.trim().isEmpty() && !TagNamespace.isValidNamespacedTag(tag)) {
                    portfoliosNeedingMigration++;
                }
            }
            
            List<Block> allBlocks = blockRepository.findAll();
            for (Block block : allBlocks) {
                String tag = block.getPayPropTags();
                if (tag != null && !tag.trim().isEmpty() && !TagNamespace.isValidNamespacedTag(tag)) {
                    blocksNeedingMigration++;
                }
            }
            
        } catch (Exception e) {
            log.error("Error checking migration status: {}", e.getMessage());
        }
        
        return new MigrationStatus(portfoliosNeedingMigration, blocksNeedingMigration);
    }
    
    // ===== RESULT CLASSES =====
    
    public static class MigrationResult {
        private final int updated;
        private final int skipped;
        private final int errors;
        private final String message;
        
        public MigrationResult(int updated, int skipped, int errors, String message) {
            this.updated = updated;
            this.skipped = skipped;
            this.errors = errors;
            this.message = message;
        }
        
        public int getUpdated() { return updated; }
        public int getSkipped() { return skipped; }
        public int getErrors() { return errors; }
        public String getMessage() { return message; }
        public boolean isSuccessful() { return errors == 0; }
    }
    
    public static class MigrationSummary {
        private final MigrationResult portfolioResult;
        private final MigrationResult blockResult;
        
        public MigrationSummary(MigrationResult portfolioResult, MigrationResult blockResult) {
            this.portfolioResult = portfolioResult;
            this.blockResult = blockResult;
        }
        
        public MigrationResult getPortfolioResult() { return portfolioResult; }
        public MigrationResult getBlockResult() { return blockResult; }
        
        public int getTotalUpdated() { 
            return portfolioResult.getUpdated() + blockResult.getUpdated(); 
        }
        
        public int getTotalSkipped() { 
            return portfolioResult.getSkipped() + blockResult.getSkipped(); 
        }
        
        public int getTotalErrors() { 
            return portfolioResult.getErrors() + blockResult.getErrors(); 
        }
        
        public boolean isSuccessful() { 
            return portfolioResult.isSuccessful() && blockResult.isSuccessful(); 
        }
    }
    
    public static class MigrationStatus {
        private final int portfoliosNeedingMigration;
        private final int blocksNeedingMigration;
        
        public MigrationStatus(int portfoliosNeedingMigration, int blocksNeedingMigration) {
            this.portfoliosNeedingMigration = portfoliosNeedingMigration;
            this.blocksNeedingMigration = blocksNeedingMigration;
        }
        
        public int getPortfoliosNeedingMigration() { return portfoliosNeedingMigration; }
        public int getBlocksNeedingMigration() { return blocksNeedingMigration; }
        public int getTotalNeedingMigration() { 
            return portfoliosNeedingMigration + blocksNeedingMigration; 
        }
        public boolean isMigrationNeeded() { 
            return getTotalNeedingMigration() > 0; 
        }
    }
}
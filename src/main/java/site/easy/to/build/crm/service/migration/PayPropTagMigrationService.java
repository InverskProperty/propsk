package site.easy.to.build.crm.service.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.payprop.PayPropPortfolioSyncService;
import site.easy.to.build.crm.service.payprop.PayPropTagDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

/**
 * CRITICAL MIGRATION: Fix portfolios that have tag names instead of PayProp external IDs
 * This addresses the fundamental issue where manual portfolios stored "PF-1105-JoeWeeks" 
 * instead of PayProp external IDs like "aB3dE5fG8h"
 */
@Service
@Transactional
public class PayPropTagMigrationService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropTagMigrationService.class);
    
    @Autowired
    private PortfolioRepository portfolioRepository;
    
    @Autowired
    private BlockRepository blockRepository;
    
    @Autowired(required = false)
    private PayPropPortfolioSyncService payPropSyncService;
    
    /**
     * Migrate portfolios that have tag names instead of external IDs
     * This is the CRITICAL fix for the PayProp integration issue
     */
    public MigrationResult migratePortfolioTagsToExternalIds() {
        log.info("🔧 Starting CRITICAL migration: Portfolio tag names → PayProp external IDs");
        
        if (payPropSyncService == null) {
            return new MigrationResult(0, 0, 1, "PayProp service not available - migration cannot proceed");
        }
        
        int updated = 0;
        int skipped = 0;
        int errors = 0;
        List<String> errorDetails = new ArrayList<>();
        
        try {
            List<Portfolio> allPortfolios = portfolioRepository.findAll();
            log.info("Found {} portfolios to check for tag migration", allPortfolios.size());
            
            for (Portfolio portfolio : allPortfolios) {
                try {
                    String currentTagValue = portfolio.getPayPropTags();
                    
                    // Skip if no tag value
                    if (currentTagValue == null || currentTagValue.trim().isEmpty()) {
                        skipped++;
                        continue;
                    }
                    
                    // Check if it's already an external ID (alphanumeric, 10-32 chars)
                    if (currentTagValue.matches("^[a-zA-Z0-9]{10,32}$")) {
                        log.debug("Portfolio '{}' already has external ID: {}", portfolio.getName(), currentTagValue);
                        skipped++;
                        continue;
                    }
                    
                    // Check if it looks like a tag name (has namespace prefix)
                    if (currentTagValue.startsWith("PF-") || currentTagValue.startsWith("BL-")) {
                        log.info("🔄 Migrating portfolio '{}' tag: {} → external ID", portfolio.getName(), currentTagValue);
                        
                        // Use the tag name from payPropTagNames if available, otherwise use current value
                        String tagName = portfolio.getPayPropTagNames();
                        if (tagName == null || tagName.trim().isEmpty()) {
                            tagName = currentTagValue; // Fallback
                        }
                        
                        try {
                            // Get or create the PayProp tag and get external ID
                            PayPropTagDTO payPropTag = payPropSyncService.ensurePayPropTagExists(tagName);
                            
                            // Update portfolio with correct values
                            portfolio.setPayPropTags(payPropTag.getId()); // External ID
                            portfolio.setPayPropTagNames(tagName); // Tag name for display
                            portfolio.setSyncStatus(SyncStatus.synced); // Mark as synced
                            portfolio.setLastSyncAt(LocalDateTime.now());
                            portfolio.setUpdatedAt(LocalDateTime.now());
                            
                            portfolioRepository.save(portfolio);
                            updated++;
                            
                            log.info("✅ Migrated portfolio '{}': {} → {}", 
                                portfolio.getName(), tagName, payPropTag.getId());
                                
                        } catch (Exception e) {
                            errors++;
                            String error = "Portfolio '" + portfolio.getName() + "': " + e.getMessage();
                            errorDetails.add(error);
                            log.error("❌ Failed to migrate portfolio '{}': {}", portfolio.getName(), e.getMessage());
                            
                            // Mark portfolio as failed so it can be retried later
                            portfolio.setSyncStatus(SyncStatus.failed);
                            portfolioRepository.save(portfolio);
                        }
                    } else {
                        // Unknown format - log but don't change
                        log.warn("Portfolio '{}' has unrecognized tag format: {}", portfolio.getName(), currentTagValue);
                        skipped++;
                    }
                    
                } catch (Exception e) {
                    errors++;
                    String error = "Portfolio '" + portfolio.getName() + "': " + e.getMessage();
                    errorDetails.add(error);
                    log.error("❌ Error processing portfolio '{}': {}", portfolio.getName(), e.getMessage());
                }
            }
            
            log.info("Portfolio tag migration completed: {} updated, {} skipped, {} errors", updated, skipped, errors);
            return new MigrationResult(updated, skipped, errors, "Portfolio tags migrated successfully", errorDetails);
            
        } catch (Exception e) {
            log.error("Fatal error during portfolio tag migration: {}", e.getMessage());
            return new MigrationResult(updated, skipped, errors + 1, "Migration failed: " + e.getMessage(), errorDetails);
        }
    }
    
    /**
     * Migrate blocks that have tag names instead of external IDs
     */
    public MigrationResult migrateBlockTagsToExternalIds() {
        log.info("🔧 Starting block tag migration: Block tag names → PayProp external IDs");
        
        if (payPropSyncService == null) {
            return new MigrationResult(0, 0, 1, "PayProp service not available - migration cannot proceed");
        }
        
        int updated = 0;
        int skipped = 0;
        int errors = 0;
        List<String> errorDetails = new ArrayList<>();
        
        try {
            List<Block> allBlocks = blockRepository.findAll();
            log.info("Found {} blocks to check for tag migration", allBlocks.size());
            
            for (Block block : allBlocks) {
                try {
                    String currentTagValue = block.getPayPropTags();
                    
                    // Skip if no tag value
                    if (currentTagValue == null || currentTagValue.trim().isEmpty()) {
                        skipped++;
                        continue;
                    }
                    
                    // Check if it's already an external ID
                    if (currentTagValue.matches("^[a-zA-Z0-9]{10,32}$")) {
                        log.debug("Block '{}' already has external ID: {}", block.getName(), currentTagValue);
                        skipped++;
                        continue;
                    }
                    
                    // Check if it looks like a block tag name
                    if (currentTagValue.startsWith("PF-") && currentTagValue.contains("-BL-")) {
                        log.info("🔄 Migrating block '{}' tag: {} → external ID", block.getName(), currentTagValue);
                        
                        String tagName = block.getPayPropTagNames();
                        if (tagName == null || tagName.trim().isEmpty()) {
                            tagName = currentTagValue; // Fallback
                        }
                        
                        try {
                            PayPropTagDTO payPropTag = payPropSyncService.ensurePayPropTagExists(tagName);
                            
                            block.setPayPropTags(payPropTag.getId()); // External ID
                            block.setPayPropTagNames(tagName); // Tag name for display
                            block.setSyncStatus(SyncStatus.synced);
                            block.setLastSyncAt(LocalDateTime.now());
                            block.setUpdatedAt(LocalDateTime.now());
                            
                            blockRepository.save(block);
                            updated++;
                            
                            log.info("✅ Migrated block '{}': {} → {}", 
                                block.getName(), tagName, payPropTag.getId());
                                
                        } catch (Exception e) {
                            errors++;
                            String error = "Block '" + block.getName() + "': " + e.getMessage();
                            errorDetails.add(error);
                            log.error("❌ Failed to migrate block '{}': {}", block.getName(), e.getMessage());
                            
                            block.setSyncStatus(SyncStatus.failed);
                            blockRepository.save(block);
                        }
                    } else {
                        log.warn("Block '{}' has unrecognized tag format: {}", block.getName(), currentTagValue);
                        skipped++;
                    }
                    
                } catch (Exception e) {
                    errors++;
                    String error = "Block '" + block.getName() + "': " + e.getMessage();
                    errorDetails.add(error);
                    log.error("❌ Error processing block '{}': {}", block.getName(), e.getMessage());
                }
            }
            
            log.info("Block tag migration completed: {} updated, {} skipped, {} errors", updated, skipped, errors);
            return new MigrationResult(updated, skipped, errors, "Block tags migrated successfully", errorDetails);
            
        } catch (Exception e) {
            log.error("Fatal error during block tag migration: {}", e.getMessage());
            return new MigrationResult(updated, skipped, errors + 1, "Migration failed: " + e.getMessage(), errorDetails);
        }
    }
    
    /**
     * Migrate both portfolios and blocks in one operation
     */
    public CompleteMigrationResult migrateAllTagsToExternalIds() {
        log.info("🚀 Starting COMPLETE tag migration to external IDs");
        
        MigrationResult portfolioResult = migratePortfolioTagsToExternalIds();
        MigrationResult blockResult = migrateBlockTagsToExternalIds();
        
        CompleteMigrationResult summary = new CompleteMigrationResult(portfolioResult, blockResult);
        
        log.info("Complete tag migration finished. Portfolios: {} updated, Blocks: {} updated", 
            portfolioResult.getUpdated(), blockResult.getUpdated());
            
        return summary;
    }
    
    /**
     * Check how many entities need migration
     */
    public MigrationStatus checkMigrationStatus() {
        int portfoliosNeedingMigration = 0;
        int blocksNeedingMigration = 0;
        
        try {
            // Check portfolios
            List<Portfolio> allPortfolios = portfolioRepository.findAll();
            for (Portfolio portfolio : allPortfolios) {
                String tag = portfolio.getPayPropTags();
                if (tag != null && !tag.trim().isEmpty() && 
                    !tag.matches("^[a-zA-Z0-9]{10,32}$") && 
                    (tag.startsWith("PF-") || tag.startsWith("BL-"))) {
                    portfoliosNeedingMigration++;
                }
            }
            
            // Check blocks  
            List<Block> allBlocks = blockRepository.findAll();
            for (Block block : allBlocks) {
                String tag = block.getPayPropTags();
                if (tag != null && !tag.trim().isEmpty() && 
                    !tag.matches("^[a-zA-Z0-9]{10,32}$") && 
                    tag.startsWith("PF-") && tag.contains("-BL-")) {
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
        private final List<String> errorDetails;
        
        public MigrationResult(int updated, int skipped, int errors, String message) {
            this(updated, skipped, errors, message, new ArrayList<>());
        }
        
        public MigrationResult(int updated, int skipped, int errors, String message, List<String> errorDetails) {
            this.updated = updated;
            this.skipped = skipped;
            this.errors = errors;
            this.message = message;
            this.errorDetails = errorDetails;
        }
        
        public int getUpdated() { return updated; }
        public int getSkipped() { return skipped; }
        public int getErrors() { return errors; }
        public String getMessage() { return message; }
        public List<String> getErrorDetails() { return errorDetails; }
        public boolean isSuccessful() { return errors == 0; }
    }
    
    public static class CompleteMigrationResult {
        private final MigrationResult portfolioResult;
        private final MigrationResult blockResult;
        
        public CompleteMigrationResult(MigrationResult portfolioResult, MigrationResult blockResult) {
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
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
}
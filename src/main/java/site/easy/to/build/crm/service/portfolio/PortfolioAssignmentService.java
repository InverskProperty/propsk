package site.easy.to.build.crm.service.portfolio;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.payprop.PayPropPortfolioSyncService;
import site.easy.to.build.crm.service.payprop.PayPropTagDTO;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service to handle portfolio-property assignments with PayProp integration
 * This replaces direct FK assignments and ensures PayProp tags are applied
 */
@Service
@Transactional
public class PortfolioAssignmentService {
    
    private static final Logger log = LoggerFactory.getLogger(PortfolioAssignmentService.class);
    
    @Autowired
    private PropertyPortfolioAssignmentRepository assignmentRepository;
    
    @Autowired
    private PortfolioRepository portfolioRepository;
    
    @Autowired
    private PropertyService propertyService;
    
    @Autowired(required = false) // Make optional for non-PayProp environments
    @Lazy // Break circular dependency with PayPropPortfolioSyncService
    private PayPropPortfolioSyncService payPropSyncService;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    // ==================== MAIN ASSIGNMENT METHODS ====================
    
    /**
     * Assign properties to portfolio WITH PayProp sync
     * This is the PRIMARY method that should be called from controllers
     */
    public AssignmentResult assignPropertiesToPortfolio(Long portfolioId, List<Long> propertyIds, Long userId) {
        log.info("🎯 Starting assignment of {} properties to portfolio {}", propertyIds.size(), portfolioId);
        
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
            .orElseThrow(() -> new IllegalArgumentException("Portfolio not found: " + portfolioId));
        
        AssignmentResult result = new AssignmentResult();
        
        for (Long propertyId : propertyIds) {
            try {
                Property property = propertyService.findById(propertyId);
                if (property == null) {
                    result.addError("Property not found: " + propertyId);
                    continue;
                }
                
                // Step 1: Check if assignment already exists
                if (isPropertyAssignedToPortfolio(propertyId, portfolioId)) {
                    log.info("Property {} already assigned to portfolio {}, skipping", propertyId, portfolioId);
                    result.incrementSkipped();
                    continue;
                }
                
                // Step 1.5: Check if INACTIVE assignment exists that we can reactivate  
                log.info("🔍 Checking for existing assignment: Property {} → Portfolio {}", propertyId, portfolioId);
                Optional<PropertyPortfolioAssignment> inactiveAssignment = 
                    assignmentRepository.findByPropertyIdAndPortfolioIdAndAssignmentType(
                        propertyId, portfolioId, PortfolioAssignmentType.PRIMARY);
                
                log.info("🔍 Found existing assignment: {}", inactiveAssignment.isPresent());
                if (inactiveAssignment.isPresent()) {
                    log.info("🔍 Existing assignment active status: {}", inactiveAssignment.get().getIsActive());
                }
                
                PropertyPortfolioAssignment assignment;
                if (inactiveAssignment.isPresent() && !inactiveAssignment.get().getIsActive()) {
                    // Reactivate existing assignment
                    assignment = inactiveAssignment.get();
                    assignment.setIsActive(true);
                    assignment.setAssignedAt(LocalDateTime.now());
                    assignment.setAssignedBy(userId);
                    assignment.setUpdatedAt(LocalDateTime.now());
                    assignment.setUpdatedBy(userId);
                    assignment.setSyncStatus(SyncStatus.pending);
                    assignment.setNotes("Reactivated via assignment page");
                    
                    assignmentRepository.save(assignment);
                    log.info("✅ Reactivated existing assignment: Property {} → Portfolio {}", propertyId, portfolioId);
                } else {
                    // Step 2: Create new junction table assignment
                    assignment = createJunctionTableAssignment(property, portfolio, userId);
                    log.info("✅ Created new assignment: Property {} → Portfolio {}", propertyId, portfolioId);
                }
                result.incrementAssigned();
                
                // Step 3: Apply PayProp tag if applicable
                if (shouldSyncToPayProp(portfolio, property)) {
                    try {
                        String tagValue = portfolio.getPayPropTags();
                        
                        // ✅ CRITICAL FIX: Auto-create PayProp tag if portfolio doesn't have one
                        if (tagValue == null || tagValue.trim().isEmpty()) {
                            log.info("🏷️ Portfolio {} has no PayProp tag - creating one automatically", portfolio.getId());
                            try {
                                // Use the sync service to create/get the PayProp tag
                                var syncResult = payPropSyncService.syncPortfolioToPayProp(portfolio.getId(), userId);
                                if (syncResult.isSuccess()) {
                                    // Reload portfolio to get the updated PayProp tags
                                    portfolio = portfolioRepository.findById(portfolioId).orElse(portfolio);
                                    tagValue = portfolio.getPayPropTags();
                                    log.info("✅ Auto-created PayProp tag for portfolio {}: {}", portfolio.getId(), tagValue);
                                } else {
                                    log.error("❌ Failed to auto-create PayProp tag for portfolio {}: {}", 
                                        portfolio.getId(), syncResult.getMessage());
                                    assignment.setSyncStatus(SyncStatus.failed);
                                    assignmentRepository.save(assignment);
                                    result.addError("Failed to create PayProp tag for portfolio " + portfolio.getId() + ": " + syncResult.getMessage());
                                    continue;
                                }
                            } catch (Exception e) {
                                log.error("❌ Exception during auto-creation of PayProp tag for portfolio {}: {}", 
                                    portfolio.getId(), e.getMessage());
                                assignment.setSyncStatus(SyncStatus.failed);
                                assignmentRepository.save(assignment);
                                result.addError("Exception creating PayProp tag for portfolio " + portfolio.getId() + ": " + e.getMessage());
                                continue;
                            }
                        }
                        
                        // CRITICAL FIX: Check if tag value looks like a tag name, not external ID
                        if (tagValue != null && (tagValue.startsWith("PF-") || tagValue.startsWith("BL-"))) {
                            log.warn("Portfolio {} has tag name instead of external ID, resolving: {}", portfolio.getId(), tagValue);
                            tagValue = ensurePortfolioHasExternalId(portfolio);
                            if (tagValue == null) {
                                log.error("❌ Cannot resolve PayProp external ID for portfolio {}", portfolio.getId());
                                assignment.setSyncStatus(SyncStatus.failed);
                                assignmentRepository.save(assignment);
                                result.addError("Failed to resolve PayProp external ID for portfolio " + portfolio.getId());
                                continue;
                            }
                        }
                        
                        log.info("🔄 Applying PayProp tag {} to property {}", 
                            tagValue, property.getPayPropId());
                        
                        payPropSyncService.applyTagToProperty(
                            property.getPayPropId(), 
                            tagValue
                        );
                        
                        // Update sync status
                        assignment.setSyncStatus(SyncStatus.synced);
                        assignment.setLastSyncAt(LocalDateTime.now());
                        assignmentRepository.save(assignment);
                        result.incrementSynced();
                        
                        log.info("✅ PayProp sync successful for property {}", property.getPayPropId());
                        
                    } catch (Exception e) {
                        log.error("❌ PayProp sync failed for property {}: {}", 
                            property.getPayPropId(), e.getMessage());
                        
                        assignment.setSyncStatus(SyncStatus.failed);
                        assignment.setLastSyncAt(LocalDateTime.now());
                        assignmentRepository.save(assignment);
                        result.addError("PayProp sync failed for property " + propertyId + ": " + e.getMessage());
                    }
                } else {
                    log.info("⏭️ Skipping PayProp sync - not configured for portfolio {} or property {}", 
                        portfolioId, propertyId);
                }
                
                // Step 4: REMOVE direct FK assignment to prevent conflicts
                // FIXED: Check through assignment service instead of deprecated property.getPortfolio()
                List<Portfolio> currentPortfolios = getPortfoliosForProperty(propertyId);
                Portfolio currentPortfolio = currentPortfolios.isEmpty() ? null : currentPortfolios.get(0);
                if (currentPortfolio != null && currentPortfolio.getId().equals(portfolioId)) {
                    log.info("🔧 Clearing direct FK for property {} to prevent conflicts", propertyId);
                    property.setPortfolio(null);
                    property.setPortfolioAssignmentDate(null);
                    propertyService.save(property);
                }
                
            } catch (Exception e) {
                log.error("❌ Failed to assign property {}: {}", propertyId, e.getMessage(), e);
                result.addError("Failed to assign property " + propertyId + ": " + e.getMessage());
            }
        }
        
        log.info("📊 Assignment complete: {}", result.getSummary());
        return result;
    }
    
    /**
     * Remove property from portfolio WITH PayProp sync
     */
    public void removePropertyFromPortfolio(Long propertyId, Long portfolioId, Long userId) {
        log.info("🗑️ Removing property {} from portfolio {}", propertyId, portfolioId);
        
        Property property = propertyService.findById(propertyId);
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
            .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));
        
        // Remove junction table assignment
        Optional<PropertyPortfolioAssignment> assignmentOpt = 
            assignmentRepository.findByPropertyIdAndPortfolioIdAndAssignmentTypeAndIsActive(
                propertyId, portfolioId, PortfolioAssignmentType.PRIMARY, Boolean.TRUE);
        
        if (assignmentOpt.isPresent()) {
            PropertyPortfolioAssignment assignment = assignmentOpt.get();
            assignment.setIsActive(Boolean.FALSE);
            assignment.setUpdatedAt(LocalDateTime.now());
            assignment.setUpdatedBy(userId);
            assignmentRepository.save(assignment);
            log.info("✅ Junction table assignment removed");
        } else {
            log.warn("⚠️ No active assignment found for property {} in portfolio {} - may already be removed", propertyId, portfolioId);
        }
        
        // Remove PayProp tag
        if (shouldSyncToPayProp(portfolio, property)) {
            try {
                String tagValue = portfolio.getPayPropTags();
                
                // CRITICAL FIX: Check if tag value looks like a tag name, not external ID
                if (tagValue.startsWith("PF-") || tagValue.startsWith("BL-")) {
                    log.warn("Portfolio {} has tag name instead of external ID for removal, resolving: {}", portfolio.getId(), tagValue);
                    tagValue = ensurePortfolioHasExternalId(portfolio);
                    if (tagValue == null) {
                        log.error("❌ Cannot resolve PayProp external ID for portfolio {} removal", portfolio.getId());
                        throw new RuntimeException("Failed to resolve PayProp external ID for tag removal");
                    }
                }
                
                log.info("🔄 Removing PayProp tag {} from property {}", 
                    tagValue, property.getPayPropId());
                
                payPropSyncService.removeTagFromProperty(
                    property.getPayPropId(), 
                    tagValue
                );
                
                log.info("✅ PayProp tag removed successfully");
            } catch (Exception e) {
                log.error("❌ Failed to remove PayProp tag: {}", e.getMessage());
                // ENHANCED: Throw exception to surface error to user instead of silent failure
                throw new RuntimeException("Local removal succeeded, but PayProp tag removal failed: " + e.getMessage(), e);
            }
        } else {
            log.info("⏭️ Skipping PayProp tag removal - not configured for portfolio {} or property {}", 
                portfolioId, propertyId);
        }
        
        // Clear direct FK if it matches
        // FIXED: Check through assignment service instead of deprecated property.getPortfolio()
        List<Portfolio> currentPortfolios = getPortfoliosForProperty(propertyId);
                Portfolio currentPortfolio = currentPortfolios.isEmpty() ? null : currentPortfolios.get(0);
        if (currentPortfolio != null && currentPortfolio.getId().equals(portfolioId)) {
            property.setPortfolio(null);
            property.setPortfolioAssignmentDate(null);
            propertyService.save(property);
            log.info("✅ Direct FK cleared");
        }
    }
    
    // ==================== BULK OPERATIONS ====================
    
    /**
     * Migrate all direct FK assignments to junction table
     * This is a one-time migration to fix the current state
     */
    @Transactional
    public MigrationResult migrateDirectFKToJunctionTable() {
        log.info("🔄 Starting migration of direct FK assignments to junction table");
        
        MigrationResult result = new MigrationResult();
        
        // Find all properties with direct FK assignments
        // FIXED: This method is for migration - keep deprecated call but add comment
        List<Property> propertiesWithFK = propertyService.findAll().stream()
            .filter(p -> p.getPortfolio() != null) // Migration method - checking deprecated field
            .collect(Collectors.toList());
        
        log.info("Found {} properties with direct FK assignments", propertiesWithFK.size());
        
        for (Property property : propertiesWithFK) {
            try {
                Portfolio portfolio = property.getPortfolio(); // Migration method - using deprecated field
                
                // Check if junction table assignment already exists
                if (isPropertyAssignedToPortfolio(property.getId(), portfolio.getId())) {
                    log.info("Property {} already in junction table for portfolio {}, clearing FK only", 
                        property.getId(), portfolio.getId());
                    
                    // Just clear the FK
                    property.setPortfolio(null);
                    propertyService.save(property);
                    result.incrementSkipped();
                } else {
                    // Create junction table entry
                    PropertyPortfolioAssignment assignment = new PropertyPortfolioAssignment();
                    assignment.setProperty(property);
                    assignment.setPortfolio(portfolio);
                    assignment.setAssignmentType(PortfolioAssignmentType.PRIMARY);
                    assignment.setAssignedBy(1L); // System user
                    assignment.setAssignedAt(property.getPortfolioAssignmentDate() != null ? 
                        property.getPortfolioAssignmentDate() : LocalDateTime.now());
                    assignment.setSyncStatus(SyncStatus.pending); // Will need PayProp sync
                    assignment.setIsActive(Boolean.TRUE);
                    assignment.setNotes("Migrated from direct FK assignment");
                    
                    assignmentRepository.save(assignment);
                    
                    // Clear the direct FK
                    property.setPortfolio(null);
                    propertyService.save(property);
                    
                    result.incrementMigrated();
                    log.info("✅ Migrated property {} to junction table", property.getId());
                }
            } catch (Exception e) {
                log.error("❌ Failed to migrate property {}: {}", property.getId(), e.getMessage());
                result.addError("Property " + property.getId() + ": " + e.getMessage());
            }
        }
        
        log.info("📊 Migration complete: {}", result.getSummary());
        return result;
    }
    
    /**
     * Sync all pending PayProp tags for portfolio assignments
     */
    public SyncResult syncPendingPayPropTags() {
        log.info("🔄 Starting sync of pending PayProp tags");
        
        if (payPropSyncService == null) {
            return new SyncResult(false, "PayProp integration not available");
        }
        
        // Use findAll and filter for pending status
        List<PropertyPortfolioAssignment> pendingAssignments = 
            assignmentRepository.findAll().stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsActive()) && a.getSyncStatus() == SyncStatus.pending)
                .collect(Collectors.toList());
        
        log.info("Found {} assignments pending PayProp sync", pendingAssignments.size());
        
        int syncedCount = 0;
        int failedCount = 0;
        List<String> errors = new ArrayList<>();
        
        for (PropertyPortfolioAssignment assignment : pendingAssignments) {
            try {
                Property property = assignment.getProperty();
                Portfolio portfolio = assignment.getPortfolio(); // Valid - this is from junction table
                
                if (shouldSyncToPayProp(portfolio, property)) {
                    String tagValue = portfolio.getPayPropTags();
                    
                    // CRITICAL FIX: Check if tag value looks like a tag name, not external ID
                    if (tagValue.startsWith("PF-") || tagValue.startsWith("BL-")) {
                        log.warn("Portfolio {} has tag name instead of external ID for sync, resolving: {}", portfolio.getId(), tagValue);
                        tagValue = ensurePortfolioHasExternalId(portfolio);
                        if (tagValue == null) {
                            throw new RuntimeException("Cannot resolve PayProp external ID for portfolio " + portfolio.getId());
                        }
                    }
                    
                    payPropSyncService.applyTagToProperty(
                        property.getPayPropId(),
                        tagValue
                    );
                    
                    assignment.setSyncStatus(SyncStatus.synced);
                    assignment.setLastSyncAt(LocalDateTime.now());
                    assignmentRepository.save(assignment);
                    syncedCount++;
                    
                    log.info("✅ Synced property {} to PayProp", property.getId());
                }
            } catch (Exception e) {
                assignment.setSyncStatus(SyncStatus.failed);
                assignment.setLastSyncAt(LocalDateTime.now());
                assignmentRepository.save(assignment);
                failedCount++;
                errors.add("Assignment " + assignment.getId() + ": " + e.getMessage());
                log.error("❌ Failed to sync assignment {}: {}", assignment.getId(), e.getMessage());
            }
        }
        
        String message = String.format("Synced %d assignments, %d failed", syncedCount, failedCount);
        return new SyncResult(failedCount == 0, message, errors);
    }
    
    // ==================== QUERY METHODS ====================
    
    /**
     * Get all properties assigned to a portfolio (using junction table)
     */
    public List<Property> getPropertiesForPortfolio(Long portfolioId) {
        // Use the existing repository method that returns Properties directly
        return assignmentRepository.findPropertiesForPortfolio(portfolioId);
    }
    
    /**
     * Get all portfolios a property is assigned to
     */
    public List<Portfolio> getPortfoliosForProperty(Long propertyId) {
        // Use the existing repository method that returns Portfolios directly
        return assignmentRepository.findPortfoliosForProperty(propertyId);
    }
    
    /**
     * Check if property is assigned to portfolio
     */
    public boolean isPropertyAssignedToPortfolio(Long propertyId, Long portfolioId) {
        // Use existing repository method with filter
        return assignmentRepository
            .findByPropertyIdAndPortfolioIdAndAssignmentTypeAndIsActive(
                propertyId, portfolioId, PortfolioAssignmentType.PRIMARY, Boolean.TRUE)
            .isPresent();
    }
    
    /**
     * Get assignment statistics
     */
    public Map<String, Object> getAssignmentStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // Get all assignments and count by status
        List<PropertyPortfolioAssignment> allAssignments = assignmentRepository.findAll();
        
        stats.put("totalAssignments", allAssignments.size());
        
        // Count by sync status
        long syncedCount = allAssignments.stream()
            .filter(a -> a.getSyncStatus() == SyncStatus.synced)
            .count();
        long pendingCount = allAssignments.stream()
            .filter(a -> a.getSyncStatus() == SyncStatus.pending)
            .count();
        long failedCount = allAssignments.stream()
            .filter(a -> a.getSyncStatus() == SyncStatus.failed)
            .count();
        
        stats.put("syncedAssignments", syncedCount);
        stats.put("pendingAssignments", pendingCount);
        stats.put("failedAssignments", failedCount);
        
        // Count properties with multiple portfolios
        Map<Long, Long> propertyPortfolioCount = allAssignments.stream()
            .filter(a -> a.getIsActive())
            .collect(Collectors.groupingBy(
                a -> a.getProperty().getId(),
                Collectors.counting()
            ));
        
        long multiPortfolioCount = propertyPortfolioCount.values().stream()
            .filter(count -> count > 1)
            .count();
        
        stats.put("propertiesWithMultiplePortfolios", multiPortfolioCount);
        
        return stats;
    }
    
    // ==================== PRIVATE HELPER METHODS ====================
    
    private PropertyPortfolioAssignment createJunctionTableAssignment(
            Property property, Portfolio portfolio, Long userId) {
        
        PropertyPortfolioAssignment assignment = new PropertyPortfolioAssignment();
        assignment.setProperty(property);
        assignment.setPortfolio(portfolio);
        assignment.setAssignmentType(PortfolioAssignmentType.PRIMARY);
        assignment.setAssignedBy(userId);
        assignment.setAssignedAt(LocalDateTime.now());
        assignment.setSyncStatus(SyncStatus.pending);
        assignment.setIsActive(Boolean.TRUE);
        assignment.setNotes("Assigned via PortfolioAssignmentService");
        assignment.setCreatedAt(LocalDateTime.now());
        assignment.setUpdatedAt(LocalDateTime.now());
        
        return assignmentRepository.save(assignment);
    }
    
    private boolean shouldSyncToPayProp(Portfolio portfolio, Property property) {
        // ✅ ENHANCED: Allow sync even if portfolio doesn't have tags yet (we'll create them)
        boolean serviceAvailable = payPropSyncService != null;
        boolean propertyHasPayPropId = property.getPayPropId() != null;
        boolean propertyPayPropIdNotEmpty = propertyHasPayPropId && !property.getPayPropId().trim().isEmpty();
        
        // NEW: Check if portfolio already has tags (for logging purposes)
        boolean portfolioHasTags = portfolio.getPayPropTags() != null && !portfolio.getPayPropTags().trim().isEmpty();
        
        // ✅ CRITICAL CHANGE: Only require service + property PayProp ID
        // Portfolio tags will be created automatically if missing
        boolean shouldSync = serviceAvailable && propertyHasPayPropId && propertyPayPropIdNotEmpty;
        
        // DETAILED LOGGING to identify the exact failure point
        log.info("🔍 PayProp Sync Check for Portfolio {} → Property {}:", portfolio.getId(), property.getId());
        log.info("  Service available: {}", serviceAvailable);
        log.info("  Portfolio has existing tags: {} (value: '{}')", portfolioHasTags, portfolio.getPayPropTags());
        log.info("  Property has PayProp ID: {} (value: '{}')", propertyHasPayPropId, property.getPayPropId());
        log.info("  Property PayProp ID not empty: {}", propertyPayPropIdNotEmpty);
        log.info("  🎯 RESULT: shouldSync = {}", shouldSync);
        
        if (!shouldSync) {
            log.warn("❌ PayProp sync SKIPPED due to failed conditions above");
        } else if (!portfolioHasTags) {
            log.info("🏷️ PayProp sync will proceed with automatic tag creation for portfolio {}", portfolio.getId());
        }
        
        return shouldSync;
    }
    
    /**
     * CRITICAL FIX: Ensure portfolio has PayProp external ID, not just tag name
     * This fixes cases where portfolios have tag names instead of external IDs
     */
    private String ensurePortfolioHasExternalId(Portfolio portfolio) {
        String currentTagValue = portfolio.getPayPropTags();
        
        // If empty or null, return null
        if (currentTagValue == null || currentTagValue.trim().isEmpty()) {
            return null;
        }
        
        // If it looks like an external ID (alphanumeric, 10-32 chars), use it
        if (currentTagValue.matches("^[a-zA-Z0-9]{10,32}$")) {
            log.debug("Portfolio {} already has external ID: {}", portfolio.getId(), currentTagValue);
            return currentTagValue;
        }
        
        // If it looks like a tag name (has namespace prefix), try to get external ID
        if (currentTagValue.startsWith("PF-") || currentTagValue.startsWith("BL-")) {
            log.warn("Portfolio {} has tag name instead of external ID: {}", portfolio.getId(), currentTagValue);
            
            // Try to resolve it to external ID using PayProp API
            if (payPropSyncService != null) {
                try {
                    String tagName = portfolio.getPayPropTagNames();
                    if (tagName == null || tagName.trim().isEmpty()) {
                        tagName = currentTagValue; // Fallback to current value
                    }
                    
                    log.info("🔄 Attempting to resolve tag name to external ID: {}", tagName);
                    PayPropTagDTO resolvedTag = payPropSyncService.ensurePayPropTagExists(tagName);
                    
                    // Update the portfolio with the correct external ID
                    portfolio.setPayPropTags(resolvedTag.getId());
                    portfolio.setPayPropTagNames(tagName);
                    portfolioRepository.save(portfolio);
                    
                    log.info("✅ Resolved portfolio {} tag: {} -> {}", portfolio.getId(), tagName, resolvedTag.getId());
                    return resolvedTag.getId();
                    
                } catch (Exception e) {
                    log.error("❌ Failed to resolve tag name to external ID for portfolio {}: {}", 
                        portfolio.getId(), e.getMessage());
                    return null;
                }
            }
        }
        
        // Unknown format, log warning and return null
        log.warn("Portfolio {} has unrecognized tag format: {}", portfolio.getId(), currentTagValue);
        return null;
    }
    
    // ==================== RESULT CLASSES ====================
    
    public static class AssignmentResult {
        private int assignedCount = 0;
        private int syncedCount = 0;
        private int skippedCount = 0;
        private List<String> errors = new ArrayList<>();
        
        public void incrementAssigned() { assignedCount++; }
        public void incrementSynced() { syncedCount++; }
        public void incrementSkipped() { skippedCount++; }
        public void addError(String error) { errors.add(error); }
        
        public boolean isSuccess() { return errors.isEmpty(); }
        public String getSummary() {
            return String.format("Assigned: %d, Synced: %d, Skipped: %d, Errors: %d", 
                assignedCount, syncedCount, skippedCount, errors.size());
        }
        
        // Getters
        public int getAssignedCount() { return assignedCount; }
        public int getSyncedCount() { return syncedCount; }
        public int getSkippedCount() { return skippedCount; }
        public List<String> getErrors() { return errors; }
    }
    
    public static class MigrationResult {
        private int migratedCount = 0;
        private int skippedCount = 0;
        private List<String> errors = new ArrayList<>();
        
        public void incrementMigrated() { migratedCount++; }
        public void incrementSkipped() { skippedCount++; }
        public void addError(String error) { errors.add(error); }
        
        public String getSummary() {
            return String.format("Migrated: %d, Skipped: %d, Errors: %d", 
                migratedCount, skippedCount, errors.size());
        }
        
        // Getters
        public int getMigratedCount() { return migratedCount; }
        public int getSkippedCount() { return skippedCount; }
        public List<String> getErrors() { return errors; }
    }
    
    public static class SyncResult {
        private final boolean success;
        private final String message;
        private final List<String> errors;
        
        public SyncResult(boolean success, String message) {
            this(success, message, new ArrayList<>());
        }
        
        public SyncResult(boolean success, String message, List<String> errors) {
            this.success = success;
            this.message = message;
            this.errors = errors;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public List<String> getErrors() { return errors; }
    }
    
    // ==================== BLOCK ASSIGNMENT METHODS ====================
    
    /**
     * Assign properties to a specific block within a portfolio WITH PayProp sync
     * This creates hierarchical Portfolio → Block → Property assignments
     */
    public AssignmentResult assignPropertiesToBlock(Long portfolioId, Long blockId, List<Long> propertyIds, Long userId) {
        log.info("🏗️ Starting assignment of {} properties to block {} in portfolio {}", 
                propertyIds.size(), blockId, portfolioId);
        
        // Validate inputs
        if (portfolioId == null || blockId == null || propertyIds == null || propertyIds.isEmpty() || userId == null) {
            throw new IllegalArgumentException("Portfolio ID, Block ID, property IDs, and user ID are required");
        }
        
        // Validate portfolio exists
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
            .orElseThrow(() -> new IllegalArgumentException("Portfolio not found: " + portfolioId));
        
        // Validate block exists and belongs to portfolio
        Block block = validateBlockBelongsToPortfolio(blockId, portfolioId);
        
        AssignmentResult result = new AssignmentResult();
        LocalDateTime now = LocalDateTime.now();
        
        for (Long propertyId : propertyIds) {
            try {
                Property property = propertyService.findById(propertyId);
                if (property == null) {
                    result.addError("Property not found: " + propertyId);
                    continue;
                }
                
                // Check if assignment already exists
                Optional<PropertyPortfolioAssignment> existing = assignmentRepository
                    .findByPropertyIdAndPortfolioIdAndAssignmentTypeAndIsActive(
                        propertyId, portfolioId, PortfolioAssignmentType.PRIMARY, true);
                
                PropertyPortfolioAssignment assignment;
                if (existing.isPresent()) {
                    // Update existing assignment to include block
                    assignment = existing.get();
                    Block oldBlock = assignment.getBlock();
                    assignment.setBlock(block);
                    assignment.setUpdatedAt(now);
                    assignment.setUpdatedBy(userId);
                    
                    // If block changed, mark for re-sync
                    if (oldBlock == null || !oldBlock.getId().equals(blockId)) {
                        assignment.setSyncStatus(SyncStatus.pending);
                        assignment.setLastSyncAt(null);
                        log.info("🔄 Moved property {} from {} to block {}", 
                                propertyId, oldBlock != null ? "block " + oldBlock.getId() : "portfolio-only", blockId);
                    }
                } else {
                    // Create new assignment with block
                    assignment = new PropertyPortfolioAssignment();
                    assignment.setProperty(property);
                    assignment.setPortfolio(portfolio);
                    assignment.setBlock(block);
                    assignment.setAssignmentType(PortfolioAssignmentType.PRIMARY);
                    assignment.setAssignedBy(userId);
                    assignment.setAssignedAt(now);
                    assignment.setIsActive(true);
                    assignment.setSyncStatus(SyncStatus.pending);
                    assignment.setCreatedAt(now);
                    assignment.setUpdatedAt(now);
                    assignment.setUpdatedBy(userId);
                    
                    log.info("➕ Created new assignment: property {} to block {} in portfolio {}", 
                            propertyId, blockId, portfolioId);
                }
                
                // Clear any conflicting direct FK assignment
                // FIXED: Check through assignment service instead of deprecated property.getPortfolio()
                List<Portfolio> currentPortfolios = getPortfoliosForProperty(propertyId);
                Portfolio currentPortfolio = currentPortfolios.isEmpty() ? null : currentPortfolios.get(0);
                if (currentPortfolio != null && currentPortfolio.getId().equals(portfolioId)) {
                    log.info("🔧 Clearing direct FK for property {} to prevent conflicts", propertyId);
                    property.setPortfolio(null);
                    property.setPortfolioAssignmentDate(null);
                    propertyService.save(property);
                }
                
                // Save assignment
                assignmentRepository.save(assignment);
                result.incrementAssigned();
                
                // Sync to PayProp if configured and assignment should sync
                if (payPropSyncService != null && assignment.shouldSyncToPayProp()) {
                    try {
                        boolean syncSuccess = syncAssignmentToPayProp(assignment);
                        if (syncSuccess) {
                            result.incrementSynced();
                        } else {
                            result.addError("PayProp sync failed for property " + propertyId);
                        }
                    } catch (Exception e) {
                        log.error("PayProp sync error for property {}: {}", propertyId, e.getMessage());
                        result.addError("PayProp sync error for property " + propertyId + ": " + e.getMessage());
                    }
                } else {
                    result.incrementSkipped();
                    log.debug("Skipped PayProp sync for property {} (sync not configured or assignment doesn't meet sync criteria)", propertyId);
                }
                
            } catch (Exception e) {
                log.error("Error assigning property {} to block {}: {}", propertyId, blockId, e.getMessage());
                result.addError("Property " + propertyId + ": " + e.getMessage());
            }
        }
        
        log.info("✅ Block assignment complete. Assigned: {}, Synced: {}, Skipped: {}, Errors: {}", 
                result.getAssignedCount(), result.getSyncedCount(), result.getSkippedCount(), result.getErrors().size());
        
        return result;
    }
    
    /**
     * Move properties from one block to another within the same portfolio
     */
    public AssignmentResult movePropertiesBetweenBlocks(Long portfolioId, Long fromBlockId, Long toBlockId, 
                                                       List<Long> propertyIds, Long userId) {
        log.info("🔄 Moving {} properties from block {} to block {} in portfolio {}", 
                propertyIds.size(), fromBlockId, toBlockId, portfolioId);
        
        // Validate inputs
        if (portfolioId == null || propertyIds == null || propertyIds.isEmpty() || userId == null) {
            throw new IllegalArgumentException("Portfolio ID, property IDs, and user ID are required");
        }
        
        // Validate blocks (null values allowed for portfolio-only assignments)
        if (fromBlockId != null) validateBlockBelongsToPortfolio(fromBlockId, portfolioId);
        if (toBlockId != null) validateBlockBelongsToPortfolio(toBlockId, portfolioId);
        
        if (Objects.equals(fromBlockId, toBlockId)) {
            throw new IllegalArgumentException("Source and target blocks cannot be the same");
        }
        
        Block toBlock = toBlockId != null ? 
            blockRepository.findById(toBlockId).orElse(null) : null;
        
        AssignmentResult result = new AssignmentResult();
        LocalDateTime now = LocalDateTime.now();
        
        for (Long propertyId : propertyIds) {
            try {
                // Find existing assignment
                List<PropertyPortfolioAssignment> assignments = assignmentRepository
                    .findByPropertyIdAndIsActive(propertyId, true)
                    .stream()
                    .filter(a -> portfolioId.equals(a.getPortfolio().getId())) // Valid - from junction table
                    .collect(Collectors.toList());
                
                PropertyPortfolioAssignment targetAssignment = assignments.stream()
                    .filter(a -> Objects.equals(
                        a.getBlock() != null ? a.getBlock().getId() : null, fromBlockId))
                    .findFirst()
                    .orElse(null);
                
                if (targetAssignment == null) {
                    result.addError("Property " + propertyId + " not found in specified source block");
                    continue;
                }
                
                // Update assignment
                targetAssignment.setBlock(toBlock);
                targetAssignment.setSyncStatus(SyncStatus.pending);
                targetAssignment.setLastSyncAt(null);
                targetAssignment.setUpdatedAt(now);
                targetAssignment.setUpdatedBy(userId);
                
                assignmentRepository.save(targetAssignment);
                result.incrementAssigned();
                
                // Sync to PayProp
                if (payPropSyncService != null && targetAssignment.shouldSyncToPayProp()) {
                    try {
                        boolean syncSuccess = syncAssignmentToPayProp(targetAssignment);
                        if (syncSuccess) {
                            result.incrementSynced();
                        }
                    } catch (Exception e) {
                        log.error("PayProp sync error moving property {}: {}", propertyId, e.getMessage());
                        result.addError("PayProp sync error for property " + propertyId);
                    }
                } else {
                    result.incrementSkipped();
                }
                
                log.info("✅ Moved property {} from block {} to block {}", 
                        propertyId, fromBlockId, toBlockId);
                
            } catch (Exception e) {
                log.error("Error moving property {} between blocks: {}", propertyId, e.getMessage());
                result.addError("Property " + propertyId + ": " + e.getMessage());
            }
        }
        
        log.info("✅ Block move complete. Moved: {}, Synced: {}, Errors: {}", 
                result.getAssignedCount(), result.getSyncedCount(), result.getErrors().size());
        
        return result;
    }
    
    /**
     * Remove properties from a block (move to portfolio-only assignment)
     */
    public AssignmentResult removePropertiesFromBlock(Long portfolioId, Long blockId, 
                                                     List<Long> propertyIds, Long userId) {
        log.info("📦 Removing {} properties from block {} (moving to portfolio-only)", 
                propertyIds.size(), blockId);
        
        return movePropertiesBetweenBlocks(portfolioId, blockId, null, propertyIds, userId);
    }
    
    /**
     * Get properties assigned to a specific block
     */
    @Transactional(readOnly = true)
    public List<Property> getPropertiesInBlock(Long blockId) {
        if (blockId == null) return new ArrayList<>();
        
        List<PropertyPortfolioAssignment> assignments = assignmentRepository
            .findByPortfolioAndSyncStatus(null, null) // Get all assignments
            .stream()
            .filter(a -> a.getBlock() != null && blockId.equals(a.getBlock().getId()) && a.getIsActive())
            .collect(Collectors.toList());
        
        return assignments.stream()
            .map(PropertyPortfolioAssignment::getProperty)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Get all properties in a portfolio organized by blocks
     */
    @Transactional(readOnly = true)
    public Map<Block, List<Property>> getPropertiesByBlocksInPortfolio(Long portfolioId) {
        if (portfolioId == null) return new HashMap<>();
        
        List<PropertyPortfolioAssignment> assignments = assignmentRepository
            .findByPortfolioIdAndIsActive(portfolioId, true);
        
        Map<Block, List<Property>> result = new HashMap<>();
        
        for (PropertyPortfolioAssignment assignment : assignments) {
            Block block = assignment.getBlock(); // Can be null for portfolio-only assignments
            
            result.computeIfAbsent(block, k -> new ArrayList<>())
                  .add(assignment.getProperty());
        }
        
        return result;
    }
    
    /**
     * Validate that a block belongs to the specified portfolio
     */
    private Block validateBlockBelongsToPortfolio(Long blockId, Long portfolioId) {
        Block block = blockRepository.findById(blockId)
            .orElseThrow(() -> new IllegalArgumentException("Block not found: " + blockId));
        
        if (!portfolioId.equals(block.getPortfolio().getId())) { // Valid - Block has portfolio FK
            throw new IllegalArgumentException("Block " + blockId + " does not belong to portfolio " + portfolioId);
        }
        
        if (!"Y".equals(block.getIsActive())) {
            throw new IllegalArgumentException("Block " + blockId + " is not active");
        }
        
        return block;
    }
    
    /**
     * Sync a property assignment to PayProp (hierarchical version)
     * This handles both portfolio-only and block assignments
     */
    private boolean syncAssignmentToPayProp(PropertyPortfolioAssignment assignment) {
        if (payPropSyncService == null) {
            log.warn("PayProp sync service not available");
            return false;
        }
        
        try {
            Property property = assignment.getProperty();
            if (property == null || property.getPayPropId() == null) {
                log.warn("Property {} has no PayProp ID, skipping sync", 
                        property != null ? property.getId() : "null");
                return false;
            }
            
            String targetTagId = null;
            
            // Determine which tag to use based on assignment hierarchy
            if (assignment.getBlock() != null) {
                // Block assignment - use block's PayProp tag
                Block block = assignment.getBlock();
                if (block.getPayPropTags() == null || block.getPayPropTags().trim().isEmpty()) {
                    log.warn("Block {} has no PayProp external ID, cannot sync property {}", 
                            block.getId(), property.getId());
                    return false;
                }
                targetTagId = block.getPayPropTags();
                
            } else {
                // Portfolio-only assignment - use portfolio's PayProp tag
                Portfolio portfolio = assignment.getPortfolio(); // Valid - from junction table
                if (portfolio.getPayPropTags() == null || portfolio.getPayPropTags().trim().isEmpty()) {
                    log.warn("Portfolio {} has no PayProp external ID, cannot sync property {}", 
                            portfolio.getId(), property.getId());
                    return false;
                }
                targetTagId = portfolio.getPayPropTags();
            }
            
            // Apply tag to property in PayProp
            payPropSyncService.applyTagToProperty(property.getPayPropId(), targetTagId);
            
            // Update assignment sync status
            assignment.setSyncStatus(SyncStatus.synced);
            assignment.setLastSyncAt(LocalDateTime.now());
            assignmentRepository.save(assignment);
            
            log.debug("✅ Synced property {} to PayProp tag {}", property.getId(), targetTagId);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to sync assignment {} to PayProp: {}", assignment.getId(), e.getMessage());
            
            // Mark as failed
            assignment.setSyncStatus(SyncStatus.failed);
            assignmentRepository.save(assignment);
            
            return false;
        }
    }
    
    // ==================== MAINTENANCE METHODS ====================
    
    /**
     * Clear all existing portfolio assignments from the database
     * WARNING: This will remove ALL property-portfolio assignments and cannot be undone
     */
    @Transactional
    public ClearResult clearAllPortfolioAssignments(Long userId) {
        log.warn("🚨 CLEARING ALL PORTFOLIO ASSIGNMENTS - This cannot be undone!");
        
        ClearResult result = new ClearResult();
        
        try {
            // Get count before clearing
            List<PropertyPortfolioAssignment> allAssignments = assignmentRepository.findAll();
            int totalCount = allAssignments.size();
            
            log.info("Found {} total portfolio assignments to clear", totalCount);
            
            // Clear all assignments
            assignmentRepository.deleteAll();
            
            // Also clear any direct FK assignments in properties
            List<Property> propertiesWithDirectAssignment = propertyService.findAll()
                .stream()
                .filter(p -> p.getPortfolio() != null) // Clear operation - using deprecated field
                .collect(Collectors.toList());
            
            log.info("Found {} properties with direct portfolio FK assignments", propertiesWithDirectAssignment.size());
            
            for (Property property : propertiesWithDirectAssignment) {
                property.setPortfolio(null);
                property.setPortfolioAssignmentDate(null);
                propertyService.save(property);
                result.incrementDirectFkCleared();
            }
            
            result.setTotalCleared(totalCount);
            result.setSuccess(true);
            result.setMessage(String.format("Successfully cleared %d portfolio assignments and %d direct FK assignments", 
                totalCount, result.getDirectFkCleared()));
            
            log.warn("🗑️ COMPLETED: Cleared {} portfolio assignments and {} direct FK assignments", 
                totalCount, result.getDirectFkCleared());
                
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("Failed to clear portfolio assignments: " + e.getMessage());
            log.error("❌ Failed to clear portfolio assignments", e);
        }
        
        return result;
    }
    
    /**
     * Delete ALL portfolios and their assignments - complete cleanup
     * WARNING: This will delete ALL portfolios, assignments, tag links, and sync logs
     */
    @Transactional
    public ClearResult deleteAllPortfoliosAndAssignments(Long userId) {
        log.warn("🚨 DELETING ALL PORTFOLIOS AND ASSIGNMENTS - This cannot be undone!");
        
        ClearResult result = new ClearResult();
        int totalPortfolios = 0;
        int totalAssignments = 0;
        int tagLinksDeleted = 0;
        int syncLogsDeleted = 0;
        int directFkCleared = 0;
        
        try {
            // Step 1: Count and delete all portfolio assignments
            List<PropertyPortfolioAssignment> allAssignments = assignmentRepository.findAll();
            totalAssignments = allAssignments.size();
            log.info("Found {} portfolio assignments to delete", totalAssignments);
            
            if (totalAssignments > 0) {
                assignmentRepository.deleteAll();
                log.info("✅ Deleted {} portfolio assignments", totalAssignments);
            }
            
            // Step 2: Delete PayProp tag links
            List<Portfolio> allPortfolios = portfolioRepository.findAll();
            totalPortfolios = allPortfolios.size();
            log.info("Found {} portfolios to delete", totalPortfolios);
            
            for (Portfolio portfolio : allPortfolios) {
                // Delete tag links for this portfolio
                if (!portfolio.getPayPropTagLinks().isEmpty()) {
                    tagLinksDeleted += portfolio.getPayPropTagLinks().size();
                    portfolio.getPayPropTagLinks().clear();
                    portfolioRepository.save(portfolio);
                }
            }
            
            // Step 3: Delete sync logs (if repository exists)
            try {
                // Use JdbcTemplate to delete sync logs directly
                jdbcTemplate.execute("DELETE FROM portfolio_sync_logs");
                List<Map<String, Object>> syncLogCount = jdbcTemplate.queryForList("SELECT ROW_COUNT() as deleted_count");
                if (!syncLogCount.isEmpty()) {
                    Object countObj = syncLogCount.get(0).get("deleted_count");
                    if (countObj != null) {
                        syncLogsDeleted = Integer.parseInt(countObj.toString());
                    }
                }
                log.info("✅ Deleted {} portfolio sync logs", syncLogsDeleted);
            } catch (Exception e) {
                log.warn("Could not delete sync logs (table may not exist): {}", e.getMessage());
            }
            
            // Step 4: Clear direct FK assignments in properties
            List<Property> propertiesWithDirectAssignment = propertyService.findAll()
                .stream()
                .filter(p -> p.getPortfolio() != null) // Clear operation - using deprecated field
                .collect(Collectors.toList());
            
            directFkCleared = propertiesWithDirectAssignment.size();
            log.info("Found {} properties with direct portfolio FK assignments", directFkCleared);
            
            for (Property property : propertiesWithDirectAssignment) {
                property.setPortfolio(null);
                property.setPortfolioAssignmentDate(null);
                propertyService.save(property);
            }
            
            // Step 5: Delete all portfolios
            if (totalPortfolios > 0) {
                portfolioRepository.deleteAll();
                log.info("✅ Deleted {} portfolios", totalPortfolios);
            }
            
            result.setSuccess(true);
            result.setTotalCleared(totalAssignments);
            result.setDirectFkCleared(directFkCleared);
            result.setMessage(String.format(
                "Successfully deleted %d portfolios, %d assignments, %d tag links, %d sync logs, and cleared %d direct FK assignments", 
                totalPortfolios, totalAssignments, tagLinksDeleted, syncLogsDeleted, directFkCleared));
            
            log.warn("🗑️ COMPLETED FULL CLEANUP: {} portfolios, {} assignments, {} tag links, {} sync logs, {} direct FKs", 
                totalPortfolios, totalAssignments, tagLinksDeleted, syncLogsDeleted, directFkCleared);
                
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("Failed to delete portfolios and assignments: " + e.getMessage());
            log.error("❌ Failed to delete portfolios and assignments", e);
        }
        
        return result;
    }
    
    /**
     * Result class for clear operations
     */
    public static class ClearResult {
        private boolean success = false;
        private String message;
        private int totalCleared = 0;
        private int directFkCleared = 0;
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public int getTotalCleared() { return totalCleared; }
        public void setTotalCleared(int totalCleared) { this.totalCleared = totalCleared; }
        
        public int getDirectFkCleared() { return directFkCleared; }
        public void setDirectFkCleared(int directFkCleared) { this.directFkCleared = directFkCleared; }
        public void incrementDirectFkCleared() { this.directFkCleared++; }
        
        public String getSummary() {
            return String.format("Success: %s, Total cleared: %d, Direct FK cleared: %d, Message: %s", 
                success, totalCleared, directFkCleared, message);
        }
    }
    
    @Autowired
    private BlockRepository blockRepository;
}
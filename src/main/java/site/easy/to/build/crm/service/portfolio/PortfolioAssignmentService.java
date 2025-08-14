package site.easy.to.build.crm.service.portfolio;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.payprop.PayPropPortfolioSyncService;

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
    private PayPropPortfolioSyncService payPropSyncService;
    
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
                
                // Step 2: Create junction table assignment
                PropertyPortfolioAssignment assignment = createJunctionTableAssignment(
                    property, portfolio, userId);
                result.incrementAssigned();
                
                // Step 3: Apply PayProp tag if applicable
                if (shouldSyncToPayProp(portfolio, property)) {
                    try {
                        log.info("🔄 Applying PayProp tag {} to property {}", 
                            portfolio.getPayPropTags(), property.getPayPropId());
                        
                        payPropSyncService.applyTagToProperty(
                            property.getPayPropId(), 
                            portfolio.getPayPropTags()
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
                if (property.getPortfolio() != null && property.getPortfolio().getId().equals(portfolioId)) {
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
            assignmentRepository.save(assignment);
            log.info("✅ Junction table assignment removed");
        }
        
        // Remove PayProp tag
        if (shouldSyncToPayProp(portfolio, property)) {
            try {
                log.info("🔄 Removing PayProp tag {} from property {}", 
                    portfolio.getPayPropTags(), property.getPayPropId());
                
                payPropSyncService.removeTagFromProperty(
                    property.getPayPropId(), 
                    portfolio.getPayPropTags()
                );
                
                log.info("✅ PayProp tag removed successfully");
            } catch (Exception e) {
                log.error("❌ Failed to remove PayProp tag: {}", e.getMessage());
                // Continue anyway - local removal is more important
            }
        }
        
        // Clear direct FK if it matches
        if (property.getPortfolio() != null && property.getPortfolio().getId().equals(portfolioId)) {
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
        List<Property> propertiesWithFK = propertyService.findAll().stream()
            .filter(p -> p.getPortfolio() != null)
            .collect(Collectors.toList());
        
        log.info("Found {} properties with direct FK assignments", propertiesWithFK.size());
        
        for (Property property : propertiesWithFK) {
            try {
                Portfolio portfolio = property.getPortfolio();
                
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
                Portfolio portfolio = assignment.getPortfolio();
                
                if (shouldSyncToPayProp(portfolio, property)) {
                    payPropSyncService.applyTagToProperty(
                        property.getPayPropId(),
                        portfolio.getPayPropTags()
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
        boolean shouldSync = payPropSyncService != null &&
               portfolio.getPayPropTags() != null && 
               !portfolio.getPayPropTags().trim().isEmpty() &&
               property.getPayPropId() != null &&
               !property.getPayPropId().trim().isEmpty();
        
        log.debug("Should sync to PayProp? Portfolio has tags: {}, Property has PayProp ID: {}, Service available: {}", 
            portfolio.getPayPropTags() != null,
            property.getPayPropId() != null,
            payPropSyncService != null);
        
        return shouldSync;
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
}
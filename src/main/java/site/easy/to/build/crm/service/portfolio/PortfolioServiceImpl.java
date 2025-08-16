// PortfolioServiceImpl.java - COMPLETE REPLACEMENT with Junction Table Support
package site.easy.to.build.crm.service.portfolio;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.property.TenantService;
import site.easy.to.build.crm.service.payprop.PayPropPortfolioSyncService;
import site.easy.to.build.crm.service.payprop.PayPropOAuth2Service;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.util.AuthorizationUtil;
import site.easy.to.build.crm.service.payprop.PayPropTagDTO;
import site.easy.to.build.crm.service.tag.TagNamespaceService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
@Transactional
public class PortfolioServiceImpl implements PortfolioService {
    
    private final PortfolioRepository portfolioRepository;
    private final BlockRepository blockRepository;
    private final PortfolioAnalyticsRepository analyticsRepository;
    private final PropertyService propertyService;
    private final TenantService tenantService;
    private final AuthenticationUtils authenticationUtils;
    
    // ===== ADD JUNCTION TABLE REPOSITORY =====
    @Autowired
    private PropertyPortfolioAssignmentRepository propertyPortfolioAssignmentRepository;
    
    // FIXED: Add PortfolioAssignmentService for proper PayProp integration
    @Autowired(required = false)
    private PortfolioAssignmentService portfolioAssignmentService;
    
    // Make PayProp services optional
    @Autowired(required = false)
    private PayPropPortfolioSyncService payPropSyncService;
    
    @Autowired(required = false)
    private PayPropOAuth2Service payPropOAuth2Service;
    
    @Autowired
    private TagNamespaceService tagNamespaceService;
    
    @Value("${payprop.enabled:false}")
    private boolean payPropEnabled;
    
    @Autowired
    public PortfolioServiceImpl(PortfolioRepository portfolioRepository,
                               BlockRepository blockRepository,
                               PortfolioAnalyticsRepository analyticsRepository,
                               PropertyService propertyService,
                               TenantService tenantService,
                               AuthenticationUtils authenticationUtils) {
        this.portfolioRepository = portfolioRepository;
        this.blockRepository = blockRepository;
        this.analyticsRepository = analyticsRepository;
        this.propertyService = propertyService;
        this.tenantService = tenantService;
        this.authenticationUtils = authenticationUtils;
    }
    
    @Override
    public Portfolio findById(Long id) {
        return portfolioRepository.findById(id).orElse(null);
    }
    
    @Override
    public List<Portfolio> findAll() {
        return portfolioRepository.findActivePortfolios();
    }
    
    @Override
    public Portfolio save(Portfolio portfolio) {
        return portfolioRepository.save(portfolio);
    }
    
    @Override
    public void delete(Portfolio portfolio) {
        portfolio.setIsActive("N");
        portfolioRepository.save(portfolio);
    }

    @Override
    public List<Portfolio> findByPayPropTag(String tagId) {
        return portfolioRepository.findByPayPropTag(tagId);
    }

    @Override
    public List<Property> getPropertiesForPortfolio(Long portfolioId) {
        try {
            System.out.println("🔍 Attempting to get properties for portfolio " + portfolioId + " using junction table");
            
            // Try junction table first
            List<Property> properties = propertyPortfolioAssignmentRepository.findPropertiesForPortfolio(portfolioId);
            System.out.println("✅ Junction table: Found " + properties.size() + " properties for portfolio " + portfolioId);
            return properties;
            
        } catch (Exception e) {
            System.out.println("⚠️ Junction table failed, trying direct FK fallback: " + e.getMessage());
            
            try {
                // Fallback to direct FK method
                List<Property> properties = propertyService.findActivePropertiesByPortfolio(portfolioId);
                System.out.println("📝 Direct FK: Found " + properties.size() + " properties for portfolio " + portfolioId);
                return properties;
                
            } catch (Exception e2) {
                System.err.println("❌ Both methods failed for portfolio " + portfolioId);
                System.err.println("Junction table error: " + e.getMessage());
                System.err.println("Direct FK error: " + e2.getMessage());
                return new ArrayList<>();
            }
        }
    }


    @Override
    public List<Property> getPropertiesForPortfolioByType(Long portfolioId, PortfolioAssignmentType assignmentType) {
        try {
            return propertyPortfolioAssignmentRepository.findPropertiesForPortfolioByType(portfolioId, assignmentType);
        } catch (Exception e) {
            System.err.println("Error getting properties for portfolio " + portfolioId + " by type " + assignmentType + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<Portfolio> getPortfoliosForProperty(Long propertyId) {
        try {
            return propertyPortfolioAssignmentRepository.findPortfoliosForProperty(propertyId);
        } catch (Exception e) {
            System.err.println("Error getting portfolios for property " + propertyId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public Optional<Portfolio> getPrimaryPortfolioForProperty(Long propertyId) {
        try {
            return propertyPortfolioAssignmentRepository.findPrimaryPortfolioForProperty(propertyId);
        } catch (Exception e) {
            System.err.println("Error getting primary portfolio for property " + propertyId + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    // ===== ASSIGNMENT MANAGEMENT METHODS =====

    @Transactional
    public void testPortfolioAssignments() {
        System.out.println("=== TESTING PORTFOLIO ASSIGNMENTS ===");
        
        try {
            // Test 1: Check if junction table exists and has data
            long totalAssignments = propertyPortfolioAssignmentRepository.count();
            System.out.println("Total assignments in junction table: " + totalAssignments);
            
            // Test 2: Check a specific portfolio
            List<Portfolio> allPortfolios = portfolioRepository.findAll();
            for (Portfolio portfolio : allPortfolios.stream().limit(3).collect(Collectors.toList())) {
                List<Property> properties = getPropertiesForPortfolio(portfolio.getId());
                System.out.println("Portfolio '" + portfolio.getName() + "' has " + properties.size() + " properties");
                
                // Show first few properties
                for (Property prop : properties.stream().limit(3).collect(Collectors.toList())) {
                    System.out.println("  - " + prop.getPropertyName());
                }
            }
            
            // Test 3: Check direct FK assignments still in properties table
            List<Property> propertiesWithDirectFK = propertyService.findAll()
                .stream()
                .filter(p -> p.getPortfolio() != null)
                .collect(Collectors.toList());
            System.out.println("Properties with direct FK: " + propertiesWithDirectFK.size());
            
        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("=== END TEST ===");
    }

    @Override
    @Transactional
    public void assignPropertyToPortfolio(Long propertyId, Long portfolioId, PortfolioAssignmentType assignmentType, 
                                        Long assignedBy, String notes) {
        try {
            // Check if ACTIVE assignment already exists
            if (propertyPortfolioAssignmentRepository.existsByPropertyIdAndPortfolioIdAndAssignmentTypeAndIsActive(
                    propertyId, portfolioId, assignmentType, true)) {
                System.out.println("⚠️ Active assignment already exists: Property " + propertyId + " → Portfolio " + portfolioId + " (" + assignmentType + ")");
                return;
            }
            
            // Check if INACTIVE assignment exists that we can reactivate
            Optional<PropertyPortfolioAssignment> existingAssignment = 
                propertyPortfolioAssignmentRepository.findByPropertyIdAndPortfolioIdAndAssignmentType(
                    propertyId, portfolioId, assignmentType);
            
            if (existingAssignment.isPresent()) {
                // Reactivate existing assignment
                PropertyPortfolioAssignment assignment = existingAssignment.get();
                assignment.setIsActive(true);
                assignment.setAssignedAt(LocalDateTime.now());
                assignment.setAssignedBy(assignedBy);
                assignment.setUpdatedAt(LocalDateTime.now());
                assignment.setUpdatedBy(assignedBy);
                assignment.setNotes(notes);
                assignment.setSyncStatus(SyncStatus.pending);
                
                propertyPortfolioAssignmentRepository.save(assignment);
                System.out.println("✅ Reactivated existing assignment: Property " + propertyId + " → Portfolio " + portfolioId + " (" + assignmentType + ")");
                return;
            }
            
            Property property = propertyService.findById(propertyId);
            Portfolio portfolio = findById(portfolioId);
            
            if (property == null) {
                throw new IllegalArgumentException("Property not found: " + propertyId);
            }
            if (portfolio == null) {
                throw new IllegalArgumentException("Portfolio not found: " + portfolioId);
            }
            
            // Create new assignment (first time)
            PropertyPortfolioAssignment assignment = new PropertyPortfolioAssignment(
                property, portfolio, assignmentType, assignedBy);
            assignment.setNotes(notes);
            
            propertyPortfolioAssignmentRepository.save(assignment);
            
            // If this is a PRIMARY assignment, ensure only one exists
            if (assignmentType == PortfolioAssignmentType.PRIMARY) {
                ensureOnlyOnePrimaryAssignment(propertyId, portfolioId, assignedBy);
                
                // Update direct FK for backwards compatibility
                property.setPortfolio(portfolio);
                property.setPortfolioAssignmentDate(LocalDateTime.now());
                propertyService.save(property);
            }
            
            // Track for PayProp sync if enabled
            if (payPropEnabled && !hasActivePayPropConnection()) {
                trackPortfolioChange(portfolioId, "PROPERTY_ASSIGNED", 
                    "Property " + property.getPropertyName() + " assigned as " + assignmentType);
            }
            
            System.out.println("✅ Property " + propertyId + " assigned to portfolio " + portfolioId + " as " + assignmentType);
            
        } catch (Exception e) {
            System.err.println("❌ Failed to assign property " + propertyId + " to portfolio " + portfolioId + ": " + e.getMessage());
            throw new RuntimeException("Assignment failed", e);
        }
    }

    @Override
    @Transactional
    public void assignPropertyToPortfolio(Long propertyId, Long portfolioId, Long assignedBy) {
        assignPropertyToPortfolio(propertyId, portfolioId, PortfolioAssignmentType.PRIMARY, assignedBy, null);
    }

    @Override
    @Transactional
    public void removePropertyFromPortfolio(Long propertyId, Long portfolioId, Long removedBy) {
        try {
            // FIXED: Use PortfolioAssignmentService for proper PayProp integration
            if (portfolioAssignmentService != null) {
                portfolioAssignmentService.removePropertyFromPortfolio(propertyId, portfolioId, removedBy);
                System.out.println("✅ Removed property " + propertyId + " from portfolio " + portfolioId + " (with PayProp sync)");
            } else {
                // Fallback to old logic if PortfolioAssignmentService not available
                System.out.println("⚠️ Using fallback removal logic - no PayProp sync");
                List<PropertyPortfolioAssignment> assignments = propertyPortfolioAssignmentRepository
                    .findByPropertyIdAndIsActive(propertyId, true)
                    .stream()
                    .filter(a -> a.getPortfolio().getId().equals(portfolioId))
                    .collect(Collectors.toList());
                
                for (PropertyPortfolioAssignment assignment : assignments) {
                    assignment.setIsActive(false);
                    assignment.setUpdatedBy(removedBy);
                    propertyPortfolioAssignmentRepository.save(assignment);
                    
                    System.out.println("✅ Removed property " + propertyId + " from portfolio " + portfolioId + " (" + assignment.getAssignmentType() + ") - NO PayProp sync");
                    
                    // Clear direct FK if removing PRIMARY assignment
                    if (assignment.getAssignmentType() == PortfolioAssignmentType.PRIMARY) {
                        Property property = propertyService.findById(propertyId);
                        if (property != null) {
                            property.setPortfolio(null);
                            property.setPortfolioAssignmentDate(null);
                            propertyService.save(property);
                            System.out.println("✅ Cleared direct FK for property " + propertyId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to remove property " + propertyId + " from portfolio " + portfolioId + ": " + e.getMessage());
            throw new RuntimeException("Failed to remove property from portfolio", e);
        }
    }

    @Override
    @Transactional
    public void updateAssignmentType(Long propertyId, Long portfolioId, PortfolioAssignmentType newType, Long updatedBy) {
        try {
            Optional<PropertyPortfolioAssignment> assignmentOpt = propertyPortfolioAssignmentRepository
                .findByPropertyIdAndPortfolioIdAndAssignmentTypeAndIsActive(propertyId, portfolioId, newType, true);
            
            if (assignmentOpt.isPresent()) {
                PropertyPortfolioAssignment assignment = assignmentOpt.get();
                assignment.setAssignmentType(newType);
                assignment.setUpdatedBy(updatedBy);
                propertyPortfolioAssignmentRepository.save(assignment);
                
                // Handle PRIMARY assignment uniqueness
                if (newType == PortfolioAssignmentType.PRIMARY) {
                    ensureOnlyOnePrimaryAssignment(propertyId, portfolioId, updatedBy);
                }
                
                System.out.println("✅ Updated assignment type for property " + propertyId + " in portfolio " + portfolioId + " to " + newType);
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to update assignment type: " + e.getMessage());
            throw new RuntimeException("Update assignment type failed", e);
        }
    }

    @Override
    @Transactional
    public void setPrimaryPortfolio(Long propertyId, Long portfolioId, Long assignedBy) {
        try {
            // Remove any existing PRIMARY assignments for this property
            List<PropertyPortfolioAssignment> existingPrimary = propertyPortfolioAssignmentRepository
                .findByPropertyIdAndIsActive(propertyId, true)
                .stream()
                .filter(a -> a.getAssignmentType() == PortfolioAssignmentType.PRIMARY)
                .collect(Collectors.toList());
            
            for (PropertyPortfolioAssignment existing : existingPrimary) {
                if (!existing.getPortfolio().getId().equals(portfolioId)) {
                    // Convert to SECONDARY
                    existing.setAssignmentType(PortfolioAssignmentType.SECONDARY);
                    existing.setUpdatedBy(assignedBy);
                    propertyPortfolioAssignmentRepository.save(existing);
                    System.out.println("📝 Converted existing PRIMARY assignment to SECONDARY: Portfolio " + existing.getPortfolio().getId());
                }
            }
            
            // Create or update the new PRIMARY assignment
            assignPropertyToPortfolio(propertyId, portfolioId, PortfolioAssignmentType.PRIMARY, assignedBy, "Set as primary portfolio");
            
        } catch (Exception e) {
            System.err.println("❌ Failed to set primary portfolio for property " + propertyId + ": " + e.getMessage());
            throw new RuntimeException("Set primary failed", e);
        }
    }

    // ===== BULK OPERATIONS =====

    @Override
    @Transactional
    public void assignPropertiesToPortfolio(Long portfolioId, List<Long> propertyIds, 
                                           PortfolioAssignmentType assignmentType, Long assignedBy) {
        int successCount = 0;
        int failureCount = 0;
        
        for (Long propertyId : propertyIds) {
            try {
                assignPropertyToPortfolio(propertyId, portfolioId, assignmentType, assignedBy, 
                    "Bulk assignment of " + propertyIds.size() + " properties");
                successCount++;
            } catch (Exception e) {
                System.err.println("❌ Failed to assign property " + propertyId + " in bulk operation: " + e.getMessage());
                failureCount++;
            }
        }
        
        System.out.println("📊 Bulk assignment complete: " + successCount + " success, " + failureCount + " failures");
    }

    @Override
    @Transactional
    public void removePropertiesFromPortfolio(Long portfolioId, List<Long> propertyIds, Long removedBy) {
        int successCount = 0;
        int failureCount = 0;
        
        for (Long propertyId : propertyIds) {
            try {
                removePropertyFromPortfolio(propertyId, portfolioId, removedBy);
                successCount++;
            } catch (Exception e) {
                System.err.println("❌ Failed to remove property " + propertyId + " in bulk operation: " + e.getMessage());
                failureCount++;
            }
        }
        
        System.out.println("📊 Bulk removal complete: " + successCount + " success, " + failureCount + " failures");
    }

    @Override
    @Transactional
    public void copyPropertiesBetweenPortfolios(Long sourcePortfolioId, Long targetPortfolioId, 
                                              PortfolioAssignmentType assignmentType, Long assignedBy) {
        List<Property> sourceProperties = getPropertiesForPortfolio(sourcePortfolioId);
        List<Long> propertyIds = sourceProperties.stream().map(Property::getId).collect(Collectors.toList());
        
        assignPropertiesToPortfolio(targetPortfolioId, propertyIds, assignmentType, assignedBy);
        
        System.out.println("📊 Copied " + propertyIds.size() + " properties from portfolio " + sourcePortfolioId + " to " + targetPortfolioId);
    }

    // ===== MIGRATION SUPPORT =====

    @Override
    @Transactional
    public void migrateDirectAssignmentsToJunctionTable(Long migratedBy) {
        System.out.println("🔄 Starting migration of direct FK assignments to junction table...");
        
        List<Property> propertiesWithPortfolio = propertyService.findAll()
            .stream()
            .filter(p -> p.getPortfolio() != null)
            .collect(Collectors.toList());
        
        int migrated = 0;
        int skipped = 0;
        
        for (Property property : propertiesWithPortfolio) {
            try {
                Long propertyId = property.getId();
                Long portfolioId = property.getPortfolio().getId();
                
                // Check if assignment already exists in junction table
                if (!propertyPortfolioAssignmentRepository.existsByPropertyIdAndPortfolioIdAndAssignmentTypeAndIsActive(
                        propertyId, portfolioId, PortfolioAssignmentType.PRIMARY, true)) {
                    
                    // Create junction table assignment
                    PropertyPortfolioAssignment assignment = new PropertyPortfolioAssignment(
                        property, property.getPortfolio(), PortfolioAssignmentType.PRIMARY, migratedBy);
                    assignment.setNotes("Migrated from direct FK assignment");
                    assignment.setSyncStatus(SyncStatus.synced); // Mark as already synced
                    propertyPortfolioAssignmentRepository.save(assignment);
                    
                    migrated++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                System.err.println("❌ Failed to migrate property " + property.getId() + ": " + e.getMessage());
            }
        }
        
        System.out.println("✅ Migration complete: " + migrated + " migrated, " + skipped + " skipped");
    }

    @Override
    @Transactional
    public void syncJunctionTableToDirectFK() {
        System.out.println("🔄 Syncing PRIMARY assignments back to direct FK for backwards compatibility...");
        
        List<PropertyPortfolioAssignment> primaryAssignments = propertyPortfolioAssignmentRepository
            .findByAssignmentTypeAndIsActive(PortfolioAssignmentType.PRIMARY, true);
        
        int synced = 0;
        
        for (PropertyPortfolioAssignment assignment : primaryAssignments) {
            try {
                Property property = assignment.getProperty();
                Portfolio portfolio = assignment.getPortfolio();
                
                if (property.getPortfolio() == null || !property.getPortfolio().getId().equals(portfolio.getId())) {
                    property.setPortfolio(portfolio);
                    property.setPortfolioAssignmentDate(assignment.getAssignedAt());
                    propertyService.save(property);
                    synced++;
                }
            } catch (Exception e) {
                System.err.println("❌ Failed to sync assignment " + assignment.getId() + ": " + e.getMessage());
            }
        }
        
        System.out.println("✅ Sync complete: " + synced + " properties updated");
    }

    @Override
    public List<String> validateAssignmentConsistency() {
        List<String> issues = new ArrayList<>();
        
        // Check for properties with PRIMARY assignment but no direct FK
        List<PropertyPortfolioAssignment> primaryAssignments = propertyPortfolioAssignmentRepository
            .findByAssignmentTypeAndIsActive(PortfolioAssignmentType.PRIMARY, true);
        
        for (PropertyPortfolioAssignment assignment : primaryAssignments) {
            Property property = assignment.getProperty();
            if (property.getPortfolio() == null || 
                !property.getPortfolio().getId().equals(assignment.getPortfolio().getId())) {
                issues.add("Property " + property.getId() + " has PRIMARY assignment to portfolio " + 
                          assignment.getPortfolio().getId() + " but direct FK doesn't match");
            }
        }
        
        // Check for properties with direct FK but no PRIMARY assignment
        List<Property> propertiesWithPortfolio = propertyService.findAll()
            .stream()
            .filter(p -> p.getPortfolio() != null)
            .collect(Collectors.toList());
        
        for (Property property : propertiesWithPortfolio) {
            if (!propertyPortfolioAssignmentRepository.existsByPropertyIdAndPortfolioIdAndAssignmentTypeAndIsActive(
                    property.getId(), property.getPortfolio().getId(), PortfolioAssignmentType.PRIMARY, true)) {
                issues.add("Property " + property.getId() + " has direct FK to portfolio " + 
                          property.getPortfolio().getId() + " but no PRIMARY assignment in junction table");
            }
        }
        
        return issues;
    }

    // ===== ANALYTICS & REPORTING =====

    @Override
    public Map<String, Object> getPortfolioAssignmentStatistics(Long portfolioId) {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalProperties", propertyPortfolioAssignmentRepository.countPropertiesInPortfolio(portfolioId));
        stats.put("primaryAssignments", propertyPortfolioAssignmentRepository.countPropertiesInPortfolioByType(
            portfolioId, PortfolioAssignmentType.PRIMARY));
        stats.put("secondaryAssignments", propertyPortfolioAssignmentRepository.countPropertiesInPortfolioByType(
            portfolioId, PortfolioAssignmentType.SECONDARY));
        stats.put("tagAssignments", propertyPortfolioAssignmentRepository.countPropertiesInPortfolioByType(
            portfolioId, PortfolioAssignmentType.TAG));
        
        return stats;
    }

    @Override
    public Map<String, Object> getPropertyAssignmentStatistics(Long propertyId) {
        Map<String, Object> stats = new HashMap<>();
        
        List<Portfolio> portfolios = getPortfoliosForProperty(propertyId);
        Optional<Portfolio> primaryPortfolio = getPrimaryPortfolioForProperty(propertyId);
        
        stats.put("totalPortfolios", portfolios.size());
        stats.put("primaryPortfolio", primaryPortfolio.map(Portfolio::getName).orElse("None"));
        stats.put("portfolioNames", portfolios.stream().map(Portfolio::getName).collect(Collectors.toList()));
        
        return stats;
    }

    @Override
    public List<Property> findPropertiesWithMultiplePortfolios() {
        List<Object[]> results = propertyPortfolioAssignmentRepository.getPropertiesWithMultiplePortfolios();
        return results.stream()
            .map(row -> propertyService.findById((Long) row[0]))
            .filter(property -> property != null)
            .collect(Collectors.toList());
    }

    @Override
    public Map<PortfolioAssignmentType, Long> getAssignmentCountsByType() {
        List<Object[]> results = propertyPortfolioAssignmentRepository.getAssignmentTypeDistribution();
        return results.stream()
            .collect(Collectors.toMap(
                row -> (PortfolioAssignmentType) row[0],
                row -> (Long) row[1]
            ));
    }

    // ===== UPDATED ANALYTICS METHOD (CRITICAL FIX) =====

    /**
     * CRITICAL FIX: Update this method to use junction table instead of direct FK
     */
    @Override
    public PortfolioAnalytics calculatePortfolioAnalytics(Long portfolioId, LocalDate calculationDate) {
        Portfolio portfolio = findById(portfolioId);
        if (portfolio == null) {
            return null;
        }
        
        // Get or create analytics record
        PortfolioAnalytics analytics = analyticsRepository
            .findByPortfolioIdAndCalculationDate(portfolioId, calculationDate)
            .orElse(new PortfolioAnalytics(portfolioId, calculationDate));
        
        // 🔧 CRITICAL FIX: Use junction table instead of direct FK
        List<Property> properties = getPropertiesForPortfolio(portfolioId);  // ← This is the key change!
        
        System.out.println("📊 Calculating analytics for portfolio " + portfolioId + " with " + properties.size() + " properties");
        
        // Property counts
        analytics.setTotalProperties(properties.size());
        
        int occupiedCount = 0;
        int vacantCount = 0;
        BigDecimal totalRent = BigDecimal.ZERO;
        BigDecimal actualIncome = BigDecimal.ZERO;
        int totalTenants = 0;
        int syncedProperties = 0;
        
        for (Property property : properties) {
            if (property.isActive()) {
                // Check occupancy using your existing tenant service
                List<Tenant> activeTenantsForProperty = tenantService.findActiveTenantsForProperty(property.getId());
                if (!activeTenantsForProperty.isEmpty()) {
                    occupiedCount++;
                    actualIncome = actualIncome.add(property.getMonthlyPayment() != null ? 
                        property.getMonthlyPayment() : BigDecimal.ZERO);
                    totalTenants += activeTenantsForProperty.size();
                } else {
                    vacantCount++;
                }
                
                // Calculate total rent potential
                if (property.getMonthlyPayment() != null) {
                    totalRent = totalRent.add(property.getMonthlyPayment());
                }
                
                // PayProp sync status (only check if PayProp is enabled)
                if (payPropEnabled && property.isPayPropSynced()) {
                    syncedProperties++;
                }
            }
        }
        
        analytics.setOccupiedProperties(occupiedCount);
        analytics.setVacantProperties(vacantCount);
        analytics.setTotalMonthlyRent(totalRent);
        analytics.setActualMonthlyIncome(actualIncome);
        analytics.setLostMonthlyIncome(totalRent.subtract(actualIncome));
        analytics.setTotalTenants(totalTenants);
        
        // Only set sync stats if PayProp is enabled
        if (payPropEnabled) {
            analytics.setPropertiesSynced(syncedProperties);
            analytics.setPropertiesPendingSync(properties.size() - syncedProperties);
        } else {
            analytics.setPropertiesSynced(0);
            analytics.setPropertiesPendingSync(0);
        }
        
        // Calculate occupancy rate
        if (analytics.getTotalProperties() > 0) {
            BigDecimal occupancyRate = BigDecimal.valueOf(occupiedCount)
                .divide(BigDecimal.valueOf(analytics.getTotalProperties()), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            analytics.setOccupancyRate(occupancyRate);
        }
        
        // Calculate variances against targets
        if (portfolio.getTargetMonthlyIncome() != null) {
            analytics.setTargetMonthlyIncome(portfolio.getTargetMonthlyIncome());
            analytics.setIncomeVariance(actualIncome.subtract(portfolio.getTargetMonthlyIncome()));
        }
        
        if (portfolio.getTargetOccupancyRate() != null) {
            analytics.setTargetOccupancyRate(portfolio.getTargetOccupancyRate());
            analytics.setOccupancyVariance(analytics.getOccupancyRate().subtract(portfolio.getTargetOccupancyRate()));
        }
        
        analytics.setLastSyncCheck(LocalDateTime.now());
        
        return analyticsRepository.save(analytics);
    }

    // ===== LEGACY METHODS (KEPT FOR BACKWARDS COMPATIBILITY) =====

    @Override
    @Deprecated
    public void assignPropertiesToPortfolio(Long portfolioId, List<Long> propertyIds, Long assignedBy) {
        // Legacy method - use new junction table method
        assignPropertiesToPortfolio(portfolioId, propertyIds, PortfolioAssignmentType.PRIMARY, assignedBy);
    }

    // ===== REMAINING EXISTING METHODS (UNCHANGED) =====

    @Override
    public Portfolio createPortfolioFromPayPropTag(String tagId, PayPropTagDTO tagData, Long createdBy, Integer propertyOwnerId) {
        // Check if tag is already adopted
        if (isPayPropTagAdopted(tagId)) {
            throw new IllegalArgumentException("PayProp tag is already adopted as a portfolio");
        }
        
        Portfolio portfolio = new Portfolio();
        portfolio.setName(tagData.getName());
        portfolio.setDescription("Adopted from PayProp tag: " + tagData.getName());
        portfolio.setPortfolioType(PortfolioType.CUSTOM);
        portfolio.setColorCode(tagData.getColor());
        portfolio.setCreatedBy(createdBy);
        portfolio.setPropertyOwnerId(propertyOwnerId);
        
        // Set sharing based on ownership
        portfolio.setIsShared(propertyOwnerId == null ? "Y" : "N");
        
        // CRITICAL FIX: Only set sync status if we can actually connect to PayProp
        if (payPropEnabled && payPropSyncService != null) {
            try {
                // Test actual PayProp connection before claiming sync
                if (hasActivePayPropConnection()) {
                    portfolio.setPayPropTags(tagId);
                    portfolio.setPayPropTagNames(tagData.getName());
                    portfolio.setSyncStatus(SyncStatus.synced);
                    portfolio.setLastSyncAt(LocalDateTime.now());
                    System.out.println("✅ Portfolio created and synced with PayProp");
                } else {
                    // PayProp enabled but not connected
                    portfolio.setPayPropTags(tagId);
                    portfolio.setPayPropTagNames(tagData.getName());
                    portfolio.setSyncStatus(SyncStatus.pending);
                    portfolio.setLastSyncAt(null);
                    System.out.println("⚠️ Portfolio created but PayProp sync pending (not connected)");
                }
            } catch (Exception e) {
                // Connection test failed
                portfolio.setPayPropTags(tagId);
                portfolio.setPayPropTagNames(tagData.getName());
                portfolio.setSyncStatus(SyncStatus.failed);
                portfolio.setLastSyncAt(null);
                System.err.println("❌ Portfolio created but PayProp sync failed: " + e.getMessage());
            }
        } else {
            // PayProp disabled - no sync status
            portfolio.setSyncStatus(null);
            portfolio.setLastSyncAt(null);
        }
        
        Portfolio savedPortfolio = portfolioRepository.save(portfolio);
        
        // Track change if PayProp is disconnected
        if (payPropEnabled && !hasActivePayPropConnection()) {
            trackPortfolioChange(savedPortfolio.getId(), "CREATED", "Portfolio created from PayProp tag: " + tagData.getName());
        }
        
        // Initialize analytics
        calculatePortfolioAnalytics(savedPortfolio.getId(), LocalDate.now());
        
        return savedPortfolio;
    }

    @Override
    public boolean isPayPropTagAdopted(String tagId) {
        if (!payPropEnabled) {
            return false;
        }
        List<Portfolio> existingPortfolios = findByPayPropTag(tagId);
        return !existingPortfolios.isEmpty();
    }
    
    @Override
    public void deleteById(Long id) {
        Portfolio portfolio = findById(id);
        if (portfolio != null) {
            delete(portfolio);
        }
    }
    
    @Override
    public List<Portfolio> findPortfoliosForUser(Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        Integer propertyOwnerId = null;
        boolean isEmployee = false;
        
        if (AuthorizationUtil.hasRole(authentication, "ROLE_CUSTOMER")) {
            // For property owners, get their customer ID
            propertyOwnerId = userId;
        } else {
            // For employees/managers, they can see shared portfolios
            isEmployee = true;
        }
        
        return portfolioRepository.findPortfoliosForUser(propertyOwnerId, (long) userId, isEmployee);
    }
    
    @Override
    public List<Portfolio> findPortfoliosForPropertyOwner(Integer propertyOwnerId) {
        return portfolioRepository.findByPropertyOwnerId(propertyOwnerId);
    }
    
    @Override
    public List<Portfolio> findSharedPortfolios() {
        return portfolioRepository.findByIsShared("Y");
    }
    
    @Override
    public Portfolio createPortfolio(String name, String description, PortfolioType type, 
                                   Integer propertyOwnerId, Long createdBy) {
        // Check for duplicate names
        if (!isPortfolioNameUnique(name, propertyOwnerId, null)) {
            throw new IllegalArgumentException("Portfolio name already exists for this owner");
        }
        
        Portfolio portfolio = new Portfolio(name, createdBy, propertyOwnerId);
        portfolio.setDescription(description);
        portfolio.setPortfolioType(type);
        portfolio.setIsShared(propertyOwnerId == null ? "Y" : "N");
        
        // NEW: Create namespaced PayProp tag for manual portfolio creation
        String namespacedTag = tagNamespaceService.createPortfolioTag(name);
        portfolio.setPayPropTags(namespacedTag);
        portfolio.setPayPropTagNames(name);
        System.out.println("✅ Created portfolio with namespaced tag: " + namespacedTag);
        
        // CRITICAL FIX: Don't claim sync until actually synced
        if (payPropEnabled && payPropSyncService != null) {
            try {
                if (hasActivePayPropConnection()) {
                    portfolio.setSyncStatus(SyncStatus.pending);
                    System.out.println("📝 Portfolio created, ready for PayProp sync");
                } else {
                    portfolio.setSyncStatus(SyncStatus.pending);
                    System.out.println("⚠️ Portfolio created but PayProp not connected");
                }
            } catch (Exception e) {
                portfolio.setSyncStatus(SyncStatus.failed);
                System.err.println("❌ Portfolio created but PayProp connection failed: " + e.getMessage());
            }
        } else {
            portfolio.setSyncStatus(null); // PayProp disabled
        }
        
        Portfolio savedPortfolio = portfolioRepository.save(portfolio);
        
        // Track change if PayProp is disconnected
        if (payPropEnabled && !hasActivePayPropConnection()) {
            trackPortfolioChange(savedPortfolio.getId(), "CREATED", "Portfolio created: " + name);
        }
        
        // Initialize analytics
        calculatePortfolioAnalytics(savedPortfolio.getId(), LocalDate.now());
        
        return savedPortfolio;
    }
    
    @Override
    public Block createBlock(String name, String description, BlockType type, 
                           Long portfolioId, Long createdBy) {
        Portfolio portfolio = findById(portfolioId);
        if (portfolio == null) {
            throw new IllegalArgumentException("Portfolio not found");
        }
        
        // Check for duplicate block names in this portfolio
        if (blockRepository.existsByNameAndPortfolioId(name, portfolioId)) {
            throw new IllegalArgumentException("Block name already exists in this portfolio");
        }
        
        Block block = new Block(name, portfolio, createdBy);
        block.setDescription(description);
        block.setBlockType(type);
        block.setPropertyOwnerId(portfolio.getPropertyOwnerId());
        
        return blockRepository.save(block);
    }
    
    @Override
    public void assignPropertiesToBlock(Long blockId, List<Long> propertyIds, Long assignedBy) {
        Block block = blockRepository.findById(blockId).orElse(null);
        if (block == null) {
            throw new IllegalArgumentException("Block not found");
        }
        
        // Check capacity
        if (block.getMaxProperties() != null) {
            long currentCount = blockRepository.countPropertiesByBlockId(blockId);
            if (currentCount + propertyIds.size() > block.getMaxProperties()) {
                throw new IllegalArgumentException("Block capacity exceeded");
            }
        }
        
        for (Long propertyId : propertyIds) {
            Property property = propertyService.findById(propertyId);
            if (property != null) {
                property.setBlock(block);
                property.setBlockAssignmentDate(LocalDateTime.now());
                
                // Assign to block's portfolio as SECONDARY if not already assigned
                Portfolio blockPortfolio = block.getPortfolio();
                if (blockPortfolio != null) {
                    List<Portfolio> existingPortfolios = getPortfoliosForProperty(propertyId);
                    boolean alreadyInPortfolio = existingPortfolios.stream()
                        .anyMatch(p -> p.getId().equals(blockPortfolio.getId()));
                    
                    if (!alreadyInPortfolio) {
                        assignPropertyToPortfolio(propertyId, blockPortfolio.getId(), 
                            PortfolioAssignmentType.SECONDARY, assignedBy, "Assigned via block: " + block.getName());
                    }
                }
                
                propertyService.save(property);
            }
        }
        
        // Recalculate portfolio analytics
        if (block.getPortfolio() != null) {
            calculatePortfolioAnalytics(block.getPortfolio().getId(), LocalDate.now());
        }
    }
    
    @Override
    public List<Block> findBlocksByPortfolio(Long portfolioId) {
        return blockRepository.findActiveBlocksByPortfolioId(portfolioId);
    }
    
    @Override
    public List<PortfolioAnalytics> getPortfolioAnalyticsHistory(Long portfolioId, LocalDate startDate, LocalDate endDate) {
        return analyticsRepository.findByPortfolioIdAndDateRange(portfolioId, startDate, endDate);
    }
    
    @Override
    public PortfolioAnalytics getLatestPortfolioAnalytics(Long portfolioId) {
        return analyticsRepository.findLatestByPortfolioId(portfolioId).orElse(null);
    }
    
    @Override
    public void syncPortfolioWithPayProp(Long portfolioId, Long initiatedBy) {
        if (!payPropEnabled || payPropSyncService == null) {
            throw new IllegalStateException("PayProp integration is not enabled");
        }
        
        // ENHANCED: Validate connection before attempting sync
        if (!hasActivePayPropConnection()) {
            throw new IllegalStateException("PayProp is not connected. Please reauthorize.");
        }
        
        try {
            payPropSyncService.syncPortfolioToPayProp(portfolioId, initiatedBy);
            
            // Update portfolio sync status on success
            Portfolio portfolio = findById(portfolioId);
            if (portfolio != null) {
                portfolio.setSyncStatus(SyncStatus.synced);
                portfolio.setLastSyncAt(LocalDateTime.now());
                portfolioRepository.save(portfolio);
                
                System.out.println("✅ Portfolio " + portfolio.getName() + " successfully synced with PayProp");
            }
            
        } catch (Exception e) {
            // Update portfolio sync status on failure
            Portfolio portfolio = findById(portfolioId);
            if (portfolio != null) {
                portfolio.setSyncStatus(SyncStatus.failed);
                portfolioRepository.save(portfolio);
            }
            
            System.err.println("❌ PayProp sync failed for portfolio " + portfolioId + ": " + e.getMessage());
            throw new RuntimeException("PayProp sync failed", e);
        }
    }
    
    @Override
    public void syncAllPortfoliosWithPayProp(Long initiatedBy) {
        if (!payPropEnabled || payPropSyncService == null) {
            throw new IllegalStateException("PayProp integration is not enabled");
        }
        
        // ENHANCED: Validate connection before attempting bulk sync
        if (!hasActivePayPropConnection()) {
            throw new IllegalStateException("PayProp is not connected. Please reauthorize.");
        }
        
        try {
            payPropSyncService.syncAllPortfolios(initiatedBy);
            System.out.println("✅ All portfolios synced with PayProp");
        } catch (Exception e) {
            System.err.println("❌ PayProp bulk sync failed: " + e.getMessage());
            throw new RuntimeException("PayProp bulk sync failed", e);
        }
    }
    
    @Override
    public List<Portfolio> findPortfoliosNeedingSync() {
        if (payPropEnabled) {
            return portfolioRepository.findPortfoliosNeedingSync();
        } else {
            return List.of();
        }
    }
    
    @Override
    public List<Portfolio> searchPortfolios(String name, PortfolioType type, Integer propertyOwnerId, 
                                          Boolean isShared, Boolean isActive) {
        String sharedString = isShared != null ? (isShared ? "Y" : "N") : null;
        String activeString = isActive != null ? (isActive ? "Y" : "N") : null;
        
        return portfolioRepository.searchPortfolios(name, null, type, propertyOwnerId, 
            sharedString, activeString, null);
    }
    
    @Override
    public void configureAutoAssignment(Long portfolioId, String assignmentRules, Long updatedBy) {
        Portfolio portfolio = findById(portfolioId);
        if (portfolio != null) {
            portfolio.setAutoAssignNewProperties("Y");
            portfolio.setAssignmentRules(assignmentRules);
            portfolio.setUpdatedBy(updatedBy);
            portfolioRepository.save(portfolio);
        }
    }
    
    @Override
    public void processAutoAssignment(Property property) {
        // Get auto-assignment portfolios for this property owner
        List<Portfolio> autoAssignPortfolios = portfolioRepository
            .findAutoAssignPortfoliosForOwner(property.getPropertyOwnerId().intValue());
        
        for (Portfolio portfolio : autoAssignPortfolios) {
            if (shouldAutoAssignToPortfolio(property, portfolio)) {
                assignPropertyToPortfolio(property.getId(), portfolio.getId(), PortfolioAssignmentType.SECONDARY, 1L, 
                    "Auto-assigned based on rules: " + portfolio.getAssignmentRules());
                break; // Assign to first matching portfolio only
            }
        }
    }
    
    private boolean shouldAutoAssignToPortfolio(Property property, Portfolio portfolio) {
        // Simple rule-based assignment logic
        String rules = portfolio.getAssignmentRules();
        if (rules == null || rules.trim().isEmpty()) {
            return false;
        }
        
        // Example rule parsing (you can make this more sophisticated)
        if (rules.contains("city:" + property.getCity())) {
            return true;
        }
        if (rules.contains("type:" + property.getPropertyType())) {
            return true;
        }
        
        return false;
    }
    
    @Override
    public boolean canUserAccessPortfolio(Long portfolioId, Authentication authentication) {
        Portfolio portfolio = findById(portfolioId);
        if (portfolio == null) {
            return false;
        }
        
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        
        // Managers can access all portfolios
        if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return true;
        }
        
        // Employees can access shared portfolios and ones they created
        if (AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")) {
            return portfolio.isSharedPortfolio() || portfolio.getCreatedBy().equals((long) userId);
        }
        
        // Property owners can access their own portfolios
        if (AuthorizationUtil.hasRole(authentication, "ROLE_CUSTOMER")) {
            return portfolio.getPropertyOwnerId() != null && 
                   portfolio.getPropertyOwnerId().equals(userId);
        }
        
        return false;
    }
    
    @Override
    public boolean isPortfolioNameUnique(String name, Integer propertyOwnerId, Long excludeId) {
        boolean exists;
        if (propertyOwnerId != null) {
            exists = portfolioRepository.existsByNameAndPropertyOwnerId(name, propertyOwnerId);
        } else {
            exists = portfolioRepository.existsByNameAndPropertyOwnerIdIsNull(name);
        }
        
        // If we're updating an existing portfolio, exclude it from the check
        if (exists && excludeId != null) {
            Optional<Portfolio> existing = propertyOwnerId != null ?
                portfolioRepository.findByNameAndPropertyOwnerId(name, propertyOwnerId) :
                portfolioRepository.findByNameAndPropertyOwnerIdIsNull(name);
            
            return existing.isEmpty() || existing.get().getId().equals(excludeId);
        }
        
        return !exists;
    }

    // ===== HELPER METHODS =====

    private void ensureOnlyOnePrimaryAssignment(Long propertyId, Long portfolioId, Long updatedBy) {
        // Find any other PRIMARY assignments for this property
        List<PropertyPortfolioAssignment> otherPrimary = propertyPortfolioAssignmentRepository
            .findByPropertyIdAndIsActive(propertyId, true)
            .stream()
            .filter(a -> a.getAssignmentType() == PortfolioAssignmentType.PRIMARY)
            .filter(a -> !a.getPortfolio().getId().equals(portfolioId))
            .collect(Collectors.toList());
        
        // Convert them to SECONDARY
        for (PropertyPortfolioAssignment assignment : otherPrimary) {
            assignment.setAssignmentType(PortfolioAssignmentType.SECONDARY);
            assignment.setUpdatedBy(updatedBy);
            propertyPortfolioAssignmentRepository.save(assignment);
            System.out.println("📝 Converted PRIMARY to SECONDARY: Property " + propertyId + " → Portfolio " + assignment.getPortfolio().getId());
        }
    }

    /**
     * CRITICAL FIX: Test actual PayProp connection, not just configuration
     */
    private boolean hasActivePayPropConnection() {
        if (!payPropEnabled || payPropOAuth2Service == null) {
            return false;
        }
        
        try {
            // Use the enhanced OAuth service with real API validation
            return payPropOAuth2Service.hasValidTokens();
        } catch (Exception e) {
            System.err.println("PayProp connection test failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Track changes made while PayProp was disconnected (simplified version)
     */
    private void trackPortfolioChange(Long portfolioId, String changeType, String changeDetails) {
        // Only track if PayProp is enabled but not connected
        if (payPropEnabled && !hasActivePayPropConnection()) {
            Portfolio portfolio = findById(portfolioId);
            if (portfolio != null) {
                // Mark as needing sync
                portfolio.setSyncStatus(SyncStatus.pending);
                portfolioRepository.save(portfolio);
                System.out.println("📝 Tracked change for portfolio " + portfolioId + ": " + changeType);
            }
        }
    }

    /**
     * Sync all pending changes when PayProp connection is restored
     */
    public void syncPendingChanges() {
        if (!payPropEnabled || !hasActivePayPropConnection()) {
            System.out.println("⚠️ Cannot sync pending changes - PayProp not connected");
            return;
        }
        
        System.out.println("🔄 Starting sync of pending changes...");
        
        List<Portfolio> portfoliosWithPendingChanges = portfolioRepository.findBySyncStatus(SyncStatus.pending);
        
        int successCount = 0;
        int failureCount = 0;
        
        for (Portfolio portfolio : portfoliosWithPendingChanges) {
            try {
                // Attempt to sync the portfolio
                syncPortfolioWithPayProp(portfolio.getId(), 1L); // Use system user ID
                successCount++;
                
            } catch (Exception e) {
                portfolio.setSyncStatus(SyncStatus.failed);
                portfolioRepository.save(portfolio);
                failureCount++;
                System.err.println("❌ Failed to sync portfolio " + portfolio.getName() + ": " + e.getMessage());
            }
        }
        
        System.out.println("🔄 Pending changes sync complete:");
        System.out.println("   ✅ Successful: " + successCount);
        System.out.println("   ❌ Failed: " + failureCount);
    }

    /**
     * Get summary of pending changes for admin dashboard
     */
    public Map<String, Object> getPendingChangesSummary() {
        List<Portfolio> pendingPortfolios = portfolioRepository.findBySyncStatus(SyncStatus.pending);
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalPendingPortfolios", pendingPortfolios.size());
        summary.put("payPropConnectionStatus", hasActivePayPropConnection() ? "CONNECTED" : "DISCONNECTED");
        summary.put("pendingPortfolios", pendingPortfolios.stream()
            .map(p -> Map.of(
                "id", p.getId(),
                "name", p.getName(),
                "lastSyncAt", p.getLastSyncAt() != null ? p.getLastSyncAt() : "Never",
                "syncStatus", p.getSyncStatus().getDisplayName()
            ))
            .collect(Collectors.toList()));
        
        return summary;
    }

    /**
     * Get detailed PayProp connection status for debugging
     */
    public Map<String, Object> getPayPropConnectionStatus() {
        if (!payPropEnabled || payPropOAuth2Service == null) {
            return Map.of(
                "enabled", false,
                "status", "DISABLED",
                "message", "PayProp integration is disabled"
            );
        }
        
        return payPropOAuth2Service.getConnectionInfo();
    }
}
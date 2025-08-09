// PortfolioServiceImpl.java - COMPLETE REPLACEMENT with PayProp Connection Validation
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
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
    
    // Make PayProp services optional
    @Autowired(required = false)
    private PayPropPortfolioSyncService payPropSyncService;
    
    @Autowired(required = false)
    private PayPropOAuth2Service payPropOAuth2Service;
    
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
    public void assignPropertiesToPortfolio(Long portfolioId, List<Long> propertyIds, Long assignedBy) {
        Portfolio portfolio = findById(portfolioId);
        if (portfolio == null) {
            throw new IllegalArgumentException("Portfolio not found");
        }
        
        for (Long propertyId : propertyIds) {
            Property property = propertyService.findById(propertyId);
            if (property != null) {
                property.setPortfolio(portfolio);
                property.setPortfolioAssignmentDate(LocalDateTime.now());
                propertyService.save(property);
            }
        }
        
        // ENHANCED: Update sync status when properties are assigned
        if (payPropEnabled) {
            try {
                if (hasActivePayPropConnection() && portfolio.isSyncedWithPayProp()) {
                    // Trigger real PayProp sync for property assignment
                    payPropSyncService.syncPortfolioToPayProp(portfolioId, assignedBy);
                    
                    // Update sync status on success
                    portfolio.setSyncStatus(SyncStatus.synced);
                    portfolio.setLastSyncAt(LocalDateTime.now());
                    portfolioRepository.save(portfolio);
                    
                    System.out.println("✅ Portfolio properties synced with PayProp");
                } else {
                    // Track the change for later sync
                    trackPortfolioChange(portfolioId, "PROPERTIES_ASSIGNED", 
                        "Assigned " + propertyIds.size() + " properties");
                    
                    portfolio.setSyncStatus(SyncStatus.pending);
                    portfolioRepository.save(portfolio);
                    
                    System.out.println("📝 Property assignment tracked for PayProp sync");
                }
            } catch (Exception e) {
                // Mark sync as failed but don't fail the assignment
                portfolio.setSyncStatus(SyncStatus.failed);
                portfolioRepository.save(portfolio);
                
                System.err.println("❌ PayProp sync failed for property assignment: " + e.getMessage());
            }
        }
        
        // Recalculate analytics
        calculatePortfolioAnalytics(portfolioId, LocalDate.now());
    }
    
    @Override
    public void removePropertiesFromPortfolio(Long portfolioId, List<Long> propertyIds, Long removedBy) {
        for (Long propertyId : propertyIds) {
            Property property = propertyService.findById(propertyId);
            if (property != null && property.getPortfolio() != null && 
                property.getPortfolio().getId().equals(portfolioId)) {
                property.setPortfolio(null);
                property.setPortfolioAssignmentDate(null);
                propertyService.save(property);
            }
        }
        
        // Recalculate analytics
        calculatePortfolioAnalytics(portfolioId, LocalDate.now());
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
                
                // If property isn't already in the block's portfolio, assign it
                if (property.getPortfolio() == null || 
                    !property.getPortfolio().getId().equals(block.getPortfolio().getId())) {
                    property.setPortfolio(block.getPortfolio());
                    property.setPortfolioAssignmentDate(LocalDateTime.now());
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
    public PortfolioAnalytics calculatePortfolioAnalytics(Long portfolioId, LocalDate calculationDate) {
        Portfolio portfolio = portfolioRepository.findByIdWithProperties(portfolioId).orElse(null);
        if (portfolio == null) {
            return null;
        }
        
        // Get or create analytics record
        PortfolioAnalytics analytics = analyticsRepository
            .findByPortfolioIdAndCalculationDate(portfolioId, calculationDate)
            .orElse(new PortfolioAnalytics(portfolioId, calculationDate));
        
        List<Property> properties = portfolio.getProperties();
        
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
                // Check occupancy
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
                // Clear any pending changes (if you have these fields)
                // portfolio.setPendingChanges(null);
                // portfolio.setLastModifiedWhileDisconnected(null);
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
            // Return empty list if PayProp is disabled
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
                property.setPortfolio(portfolio);
                property.setPortfolioAssignmentDate(LocalDateTime.now());
                propertyService.save(property);
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
                // Note: Your entity doesn't have lastModifiedWhileDisconnected or pendingChanges fields
                // So we'll just mark it as pending for now
                
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
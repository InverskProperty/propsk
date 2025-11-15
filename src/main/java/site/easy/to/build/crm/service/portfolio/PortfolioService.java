// PortfolioService.java - Interface for portfolio management with Junction Table Support
package site.easy.to.build.crm.service.portfolio;

import org.springframework.security.core.Authentication;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.service.payprop.PayPropTagDTO;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Map;

public interface PortfolioService {
    
    // Core CRUD operations
    Portfolio findById(Long id);
    List<Portfolio> findAll();
    Portfolio save(Portfolio portfolio);
    void delete(Portfolio portfolio); // Soft delete (sets isActive to 'N')
    void deleteById(Long id); // Soft delete by ID
    void hardDeletePortfolio(Long id); // Hard delete - permanently removes portfolio and PayProp tags
    
    // User-based portfolio access
    List<Portfolio> findPortfoliosForUser(Authentication authentication);
    List<Portfolio> findPortfoliosForPropertyOwner(Long propertyOwnerId);
    List<Portfolio> findPortfoliosForPropertyOwnerWithBlocks(Long propertyOwnerId);
    List<Portfolio> findPortfoliosForCustomerWithAssignments(Long customerId);
    List<Portfolio> findSharedPortfolios();

    /**
     * Find portfolios by PayProp tag ID
     */
    List<Portfolio> findByPayPropTag(String tagId);

    /**
     * Create portfolio from PayProp tag (for adoption)
     */
    Portfolio createPortfolioFromPayPropTag(String tagId, PayPropTagDTO tagData, Long createdBy, Long propertyOwnerId);

    /**
     * Check if a PayProp tag is already adopted
     */
    boolean isPayPropTagAdopted(String tagId);
    
    // Portfolio management
    Portfolio createPortfolio(String name, String description, PortfolioType type,
                             Long propertyOwnerId, Long createdBy);
    
    // ===== LEGACY METHODS (DEPRECATED - Use junction table methods instead) =====
    @Deprecated
    void assignPropertiesToPortfolio(Long portfolioId, List<Long> propertyIds, Long assignedBy);

    // ===== NEW JUNCTION TABLE METHODS =====
    
    /**
     * Get properties for portfolio using junction table (FIXES your main issue)
     */
    List<Property> getPropertiesForPortfolio(Long portfolioId);
    
    /**
     * Get properties for portfolio by assignment type
     */
    List<Property> getPropertiesForPortfolioByType(Long portfolioId, PortfolioAssignmentType assignmentType);
    
    /**
     * Get portfolios containing a specific property
     */
    List<Portfolio> getPortfoliosForProperty(Long propertyId);
    
    /**
     * Get primary portfolio for a property
     */
    Optional<Portfolio> getPrimaryPortfolioForProperty(Long propertyId);
    
    // ===== ASSIGNMENT MANAGEMENT =====
    
    /**
     * Assign property to portfolio with specific assignment type
     */
    void assignPropertyToPortfolio(Long propertyId, Long portfolioId, PortfolioAssignmentType assignmentType, 
                                  Long assignedBy, String notes);
    
    /**
     * Assign property to portfolio as PRIMARY (default)
     */
    void assignPropertyToPortfolio(Long propertyId, Long portfolioId, Long assignedBy);
    
    /**
     * Remove property from portfolio
     */
    void removePropertyFromPortfolio(Long propertyId, Long portfolioId, Long removedBy);
    
    /**
     * Update assignment type
     */
    void updateAssignmentType(Long propertyId, Long portfolioId, PortfolioAssignmentType newType, Long updatedBy);
    
    /**
     * Set primary portfolio for property (ensures only one PRIMARY assignment)
     */
    void setPrimaryPortfolio(Long propertyId, Long portfolioId, Long assignedBy);
    
    // ===== BULK OPERATIONS =====
    
    /**
     * Assign multiple properties to portfolio
     */
    void assignPropertiesToPortfolio(Long portfolioId, List<Long> propertyIds, 
                                    PortfolioAssignmentType assignmentType, Long assignedBy);
    
    /**
     * Remove multiple properties from portfolio
     */
    void removePropertiesFromPortfolio(Long portfolioId, List<Long> propertyIds, Long removedBy);
    
    /**
     * Copy properties from one portfolio to another
     */
    void copyPropertiesBetweenPortfolios(Long sourcePortfolioId, Long targetPortfolioId, 
                                       PortfolioAssignmentType assignmentType, Long assignedBy);
    
    // ===== MIGRATION SUPPORT =====
    
    /**
     * Migrate existing direct FK assignments to junction table
     */
    void migrateDirectAssignmentsToJunctionTable(Long migratedBy);
    
    /**
     * Sync junction table assignments back to direct FK (for backwards compatibility)
     */
    void syncJunctionTableToDirectFK();
    
    /**
     * Validate assignment consistency between direct FK and junction table
     */
    List<String> validateAssignmentConsistency();
    
    // ===== ANALYTICS & REPORTING =====
    
    /**
     * Get portfolio assignment statistics
     */
    Map<String, Object> getPortfolioAssignmentStatistics(Long portfolioId);
    
    /**
     * Get property assignment statistics  
     */
    Map<String, Object> getPropertyAssignmentStatistics(Long propertyId);
    
    /**
     * Find properties with multiple portfolio assignments
     */
    List<Property> findPropertiesWithMultiplePortfolios();
    
    /**
     * Get assignment count by type
     */
    Map<PortfolioAssignmentType, Long> getAssignmentCountsByType();
    
    // Block management
    Block createBlock(String name, String description, BlockType type, 
                     Long portfolioId, Long createdBy);
    void assignPropertiesToBlock(Long blockId, List<Long> propertyIds, Long assignedBy);
    List<Block> findBlocksByPortfolio(Long portfolioId);
    
    // Analytics and reporting
    PortfolioAnalytics calculatePortfolioAnalytics(Long portfolioId, LocalDate calculationDate);
    PortfolioAnalytics calculatePortfolioAnalyticsWithDateRange(Long portfolioId, LocalDate startDate, LocalDate endDate);
    List<PortfolioAnalytics> getPortfolioAnalyticsHistory(Long portfolioId, LocalDate startDate, LocalDate endDate);
    PortfolioAnalytics getLatestPortfolioAnalytics(Long portfolioId);
    
    // PayProp integration
    void syncPortfolioWithPayProp(Long portfolioId, Long initiatedBy);
    void syncAllPortfoliosWithPayProp(Long initiatedBy);
    List<Portfolio> findPortfoliosNeedingSync();
    
    // Search and filtering
    List<Portfolio> searchPortfolios(String name, PortfolioType type, Long propertyOwnerId,
                                   Boolean isShared, Boolean isActive);
    
    // Auto-assignment
    void configureAutoAssignment(Long portfolioId, String assignmentRules, Long updatedBy);
    void processAutoAssignment(Property property);
    
    // Validation
    boolean canUserAccessPortfolio(Long portfolioId, Authentication authentication);
    boolean isPortfolioNameUnique(String name, Long propertyOwnerId, Long excludeId);
}
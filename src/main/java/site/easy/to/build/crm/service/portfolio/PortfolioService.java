// PortfolioService.java - Interface for portfolio management
package site.easy.to.build.crm.service.portfolio;

import org.springframework.security.core.Authentication;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.service.payprop.PayPropTagDTO;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PortfolioService {
    
    // Core CRUD operations
    Portfolio findById(Long id);
    List<Portfolio> findAll();
    Portfolio save(Portfolio portfolio);
    void delete(Portfolio portfolio);
    void deleteById(Long id);
    
    // User-based portfolio access
    List<Portfolio> findPortfoliosForUser(Authentication authentication);
    List<Portfolio> findPortfoliosForPropertyOwner(Integer propertyOwnerId);
    List<Portfolio> findSharedPortfolios();

    /**
     * Find portfolios by PayProp tag ID
     */
    List<Portfolio> findByPayPropTag(String tagId);

    /**
     * Create portfolio from PayProp tag (for adoption)
     */
    Portfolio createPortfolioFromPayPropTag(String tagId, PayPropTagDTO tagData, Long createdBy, Integer propertyOwnerId);

    /**
     * Check if a PayProp tag is already adopted
     */
    boolean isPayPropTagAdopted(String tagId);
    
    // Portfolio management
    Portfolio createPortfolio(String name, String description, PortfolioType type, 
                             Integer propertyOwnerId, Long createdBy);
    void assignPropertiesToPortfolio(Long portfolioId, List<Long> propertyIds, Long assignedBy);
    void removePropertiesFromPortfolio(Long portfolioId, List<Long> propertyIds, Long removedBy);
    
    // Block management
    Block createBlock(String name, String description, BlockType type, 
                     Long portfolioId, Long createdBy);
    void assignPropertiesToBlock(Long blockId, List<Long> propertyIds, Long assignedBy);
    List<Block> findBlocksByPortfolio(Long portfolioId);
    
    // Analytics and reporting
    PortfolioAnalytics calculatePortfolioAnalytics(Long portfolioId, LocalDate calculationDate);
    List<PortfolioAnalytics> getPortfolioAnalyticsHistory(Long portfolioId, LocalDate startDate, LocalDate endDate);
    PortfolioAnalytics getLatestPortfolioAnalytics(Long portfolioId);
    
    // PayProp integration
    void syncPortfolioWithPayProp(Long portfolioId, Long initiatedBy);
    void syncAllPortfoliosWithPayProp(Long initiatedBy);
    List<Portfolio> findPortfoliosNeedingSync();
    
    // Search and filtering
    List<Portfolio> searchPortfolios(String name, PortfolioType type, Integer propertyOwnerId, 
                                   Boolean isShared, Boolean isActive);
    
    // Auto-assignment
    void configureAutoAssignment(Long portfolioId, String assignmentRules, Long updatedBy);
    void processAutoAssignment(Property property);
    
    // Validation
    boolean canUserAccessPortfolio(Long portfolioId, Authentication authentication);
    boolean isPortfolioNameUnique(String name, Integer propertyOwnerId, Long excludeId);
}
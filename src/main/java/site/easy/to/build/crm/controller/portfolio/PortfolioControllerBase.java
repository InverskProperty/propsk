package site.easy.to.build.crm.controller.portfolio;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.portfolio.*;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.payprop.*;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.util.AuthorizationUtil;

/**
 * Base class for all portfolio controllers containing shared dependencies and utility methods
 */
@Component
public class PortfolioControllerBase {
    
    // ===== SHARED SERVICES =====
    
    @Autowired
    protected PortfolioService portfolioService;
    
    @Autowired
    protected PropertyService propertyService;
    
    @Autowired
    protected CustomerService customerService;
    
    @Autowired
    protected PortfolioAssignmentService portfolioAssignmentService;
    
    @Autowired
    protected PortfolioRepository portfolioRepository;
    
    @Autowired
    protected PropertyRepository propertyRepository;
    
    @Autowired
    protected PropertyPortfolioAssignmentRepository propertyPortfolioAssignmentRepository;
    
    @Autowired
    protected AuthenticationUtils authenticationUtils;
    
    @Autowired
    protected ApplicationContext applicationContext;
    
    // ===== PAYPROP SERVICES (OPTIONAL) =====
    
    @Autowired(required = false)
    protected PayPropPortfolioSyncService payPropSyncService;
    
    @Autowired(required = false)
    protected PayPropOAuth2Service payPropOAuth2Service;
    
    @Value("${payprop.enabled:false}")
    protected boolean payPropEnabled;
    
    // ===== SHARED UTILITY METHODS =====
    
    /**
     * Check if user can edit a specific portfolio
     */
    protected boolean canUserEditPortfolio(Long portfolioId, Authentication authentication) {
        if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") || 
            AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")) {
            return true;
        }
        
        try {
            Portfolio portfolio = portfolioService.findById(portfolioId);
            if (portfolio == null) return false;
            
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            return portfolio.getPropertyOwnerId() != null && 
                   portfolio.getPropertyOwnerId().equals(userId);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if user can create portfolios
     */
    protected boolean canUserCreatePortfolio(Authentication authentication) {
        return AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") || 
               AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE") ||
               AuthorizationUtil.hasRole(authentication, "ROLE_PROPERTY_OWNER");
    }
    
    /**
     * Check if user can view portfolio dashboard
     */
    protected boolean canUserViewDashboard(Authentication authentication) {
        return AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") || 
               AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE") ||
               AuthorizationUtil.hasRole(authentication, "ROLE_PROPERTY_OWNER");
    }
    
    /**
     * Check if user can assign properties to portfolios
     */
    protected boolean canUserAssignProperties(Authentication authentication) {
        return AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") || 
               AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE");
    }
    
    /**
     * Check if user can perform PayProp operations
     */
    protected boolean canUserUsePayProp(Authentication authentication) {
        return payPropEnabled && (
            AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") || 
            AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")
        );
    }
    
    /**
     * Check if user can perform admin operations
     */
    protected boolean canUserPerformAdmin(Authentication authentication) {
        return AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER");
    }
    
    /**
     * Get logged in user ID safely
     */
    protected Integer getLoggedInUserId(Authentication authentication) {
        try {
            return authenticationUtils.getLoggedInUserId(authentication);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Check if PayProp integration is available
     */
    protected boolean isPayPropAvailable() {
        return payPropEnabled && payPropSyncService != null;
    }
}
package site.easy.to.build.crm.controller.portfolio;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.portfolio.*;
import site.easy.to.build.crm.service.portfolio.PortfolioBlockService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.payprop.*;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.util.AuthorizationUtil;

import org.springframework.security.oauth2.core.user.OAuth2User;

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
    
    @Autowired(required = false)
    protected PortfolioBlockService portfolioBlockService;
    
    @Autowired
    protected PortfolioRepository portfolioRepository;
    
    @Autowired
    protected PropertyRepository propertyRepository;
    
    @Autowired
    protected PropertyPortfolioAssignmentRepository propertyPortfolioAssignmentRepository;

    @Autowired
    protected BlockRepository blockRepository;

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
     * Check if user has access to view a portfolio (supports delegated users)
     */
    protected boolean hasPortfolioAccess(Long portfolioId, Authentication authentication) {
        // Managers and employees always have access
        if (AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") ||
            AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")) {
            return true;
        }

        try {
            Portfolio portfolio = portfolioService.findById(portfolioId);
            if (portfolio == null) {
                System.out.println("🔍 hasPortfolioAccess: Portfolio " + portfolioId + " not found");
                return false;
            }

            System.out.println("🔍 hasPortfolioAccess: Portfolio " + portfolioId + " owner: " + portfolio.getPropertyOwnerId());

            // Handle customer authentication
            if (AuthorizationUtil.hasRole(authentication, "ROLE_CUSTOMER") ||
                AuthorizationUtil.hasRole(authentication, "ROLE_PROPERTY_OWNER")) {

                String email = null;
                if (authentication.getPrincipal() instanceof OAuth2User) {
                    email = ((OAuth2User) authentication.getPrincipal()).getAttribute("email");
                } else {
                    email = authentication.getName();
                }

                System.out.println("🔍 hasPortfolioAccess: Customer email: " + email);

                if (email != null) {
                    Customer customer = customerService.findByEmail(email);
                    if (customer != null) {
                        System.out.println("🔍 hasPortfolioAccess: Customer ID: " + customer.getCustomerId() + ", Type: " + customer.getCustomerType());

                        // Direct owner check - FIXED: Compare Long to Integer properly
                        if (portfolio.getPropertyOwnerId() != null &&
                            portfolio.getPropertyOwnerId().longValue() == customer.getCustomerId().longValue()) {
                            System.out.println("✅ hasPortfolioAccess: Direct owner match!");
                            return true;
                        }

                        // Delegated user check
                        if (customer.getCustomerType() == CustomerType.DELEGATED_USER) {
                            // Get properties assigned to this delegated user
                            java.util.List<Property> delegatedProperties =
                                propertyService.findPropertiesByCustomerAssignments(customer.getCustomerId());

                            System.out.println("🔍 hasPortfolioAccess: Delegated user has " + delegatedProperties.size() + " assigned properties");

                            // Get properties in the portfolio
                            java.util.List<Property> portfolioProperties =
                                portfolioService.getPropertiesForPortfolio(portfolioId);

                            System.out.println("🔍 hasPortfolioAccess: Portfolio has " + portfolioProperties.size() + " properties");

                            // Check if delegated user has ANY property that's in this portfolio
                            java.util.Set<Long> delegatedPropertyIds = delegatedProperties.stream()
                                .map(Property::getId)
                                .collect(java.util.stream.Collectors.toSet());

                            boolean hasPortfolioProperties = portfolioProperties.stream()
                                .anyMatch(p -> {
                                    boolean matches = delegatedPropertyIds.contains(p.getId());
                                    if (matches) {
                                        System.out.println("✅ hasPortfolioAccess: Delegated user has property " + p.getId() + " in portfolio");
                                    }
                                    return matches;
                                });

                            System.out.println("🔍 hasPortfolioAccess: Delegated user portfolio access result: " + hasPortfolioProperties);
                            return hasPortfolioProperties;
                        } else {
                            System.out.println("❌ hasPortfolioAccess: Customer is not delegated user and doesn't own portfolio");
                        }
                    } else {
                        System.out.println("❌ hasPortfolioAccess: Customer not found by email");
                    }
                } else {
                    System.out.println("❌ hasPortfolioAccess: Email is null");
                }
            } else {
                System.out.println("❌ hasPortfolioAccess: User doesn't have CUSTOMER or PROPERTY_OWNER role");
            }

            return false;
        } catch (Exception e) {
            System.err.println("❌ hasPortfolioAccess: Exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

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

            // Handle customer authentication
            if (AuthorizationUtil.hasRole(authentication, "ROLE_CUSTOMER") ||
                AuthorizationUtil.hasRole(authentication, "ROLE_PROPERTY_OWNER")) {
                String email = ((OAuth2User) authentication.getPrincipal()).getAttribute("email");
                if (email != null) {
                    Customer customer = customerService.findByEmail(email);
                    if (customer != null) {
                        Long customerId = Long.valueOf(customer.getCustomerId());
                        return portfolio.getPropertyOwnerId() != null &&
                               portfolio.getPropertyOwnerId().equals(customerId.intValue());
                    }
                }
            }

            // Fallback for User entities
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
     * Get logged in user ID safely - supports both Users and Customers
     */
    protected Integer getLoggedInUserId(Authentication authentication) {
        try {
            // Try regular User first (Employee/Manager)
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            if (userId > 0) {
                return userId;
            }
            
            // For customers, get customer ID instead
            if (AuthorizationUtil.hasRole(authentication, "ROLE_CUSTOMER") || 
                AuthorizationUtil.hasRole(authentication, "ROLE_PROPERTY_OWNER")) {
                String email = ((OAuth2User) authentication.getPrincipal()).getAttribute("email");
                if (email != null) {
                    Customer customer = customerService.findByEmail(email);
                    if (customer != null) {
                        return customer.getCustomerId().intValue();
                    }
                }
            }
            
            return null;
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
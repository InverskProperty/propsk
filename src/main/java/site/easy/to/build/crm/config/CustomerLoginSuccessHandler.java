package site.easy.to.build.crm.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerLoginInfo;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.service.customer.CustomerLoginInfoService;
import site.easy.to.build.crm.service.user.OAuthUserService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.io.IOException;

/**
 * Custom success handler for customer login
 * Handles both form-based authentication and OAuth2 authentication
 * Redirects users to appropriate dashboards based on their customer type
 */
@Component
public class CustomerLoginSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private CustomerLoginInfoService customerLoginInfoService;

    @Autowired
    private CustomerRepository customerRepository;
    
    @Autowired
    private OAuthUserService oAuthUserService;
    
    @Autowired
    private AuthenticationUtils authenticationUtils;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, 
                                      HttpServletResponse response, 
                                      Authentication authentication) throws IOException, ServletException {
        
        System.out.println("=== DEBUG: CustomerLoginSuccessHandler.onAuthenticationSuccess ===");
        
        String email = null;
        boolean isOAuthUser = false;
        
        // Handle both form-based login and OAuth login
        if (authentication.getPrincipal() instanceof OAuth2User) {
            // OAuth2 login
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            email = oauth2User.getAttribute("email");
            isOAuthUser = true;
            System.out.println("DEBUG: OAuth2 authenticated user email: " + email);
            System.out.println("DEBUG: OAuth2 user authorities: " + authentication.getAuthorities());
        } else if (authentication.getPrincipal() instanceof UserDetails) {
            // Form-based login
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            email = userDetails.getUsername();
            isOAuthUser = false;
            System.out.println("DEBUG: Form authenticated user email: " + email);
            System.out.println("DEBUG: Form user authorities: " + userDetails.getAuthorities());
        } else {
            System.out.println("ERROR: Unknown authentication principal type: " + authentication.getPrincipal().getClass());
            response.sendRedirect("/customer-login?error=system_error");
            return;
        }
        
        try {
            // For OAuth users, we might not have CustomerLoginInfo, so try direct customer lookup first
            Customer customer = null;
            CustomerLoginInfo loginInfo = null;
            
            if (isOAuthUser) {
                System.out.println("DEBUG: OAuth user - trying direct customer lookup by email");
                customer = customerRepository.findByEmail(email);
                
                if (customer != null) {
                    System.out.println("DEBUG: Found customer via OAuth: " + customer.getCustomerId());
                    // For OAuth users, we may not have separate login info
                    loginInfo = customer.getCustomerLoginInfo();
                } else {
                    System.out.println("DEBUG: OAuth user not found in customers, redirecting to user_not_found");
                    response.sendRedirect("/customer-login?error=user_not_found&message=OAuth user not registered as customer");
                    return;
                }
            } else {
                // Form-based login requires CustomerLoginInfo
                loginInfo = customerLoginInfoService.findByEmail(email);
                System.out.println("DEBUG: Found CustomerLoginInfo: " + (loginInfo != null ? loginInfo.getId() : "null"));
                
                if (loginInfo == null) {
                    System.out.println("DEBUG: LoginInfo is null, redirecting to user_not_found");
                    response.sendRedirect("/customer-login?error=user_not_found");
                    return;
                }
            }
            
            // For form-based login, get customer via relationship or email lookup
            if (!isOAuthUser && customer == null) {
                // First try the relationship
                try {
                    customer = loginInfo.getCustomer();
                    System.out.println("DEBUG: Customer from relationship: " + (customer != null ? customer.getCustomerId() : "null"));
                } catch (Exception e) {
                    System.out.println("DEBUG: Relationship lookup failed: " + e.getMessage());
                }
                
                // If relationship failed, use direct email lookup
                if (customer == null) {
                    System.out.println("DEBUG: Using direct email lookup for customer");
                    customer = customerRepository.findByEmail(email);
                    System.out.println("DEBUG: Customer from email lookup: " + (customer != null ? customer.getCustomerId() : "null"));
                }
            }
            
            System.out.println("DEBUG: Final Customer: " + (customer != null ? customer.getCustomerId() : "null"));
            System.out.println("DEBUG: Customer Type: " + (customer != null ? customer.getCustomerType() : "null"));
            System.out.println("DEBUG: Is Property Owner: " + (customer != null ? customer.getIsPropertyOwner() : "null"));
            
            if (customer == null) {
                System.out.println("DEBUG: Customer is null, redirecting to user_not_found");
                response.sendRedirect("/customer-login?error=user_not_found");
                return;
            }
            
            // Reset login attempts on successful login (only for form-based login)
            if (loginInfo != null) {
                loginInfo.resetLoginAttempts();
                customerLoginInfoService.save(loginInfo);
                System.out.println("DEBUG: Reset login attempts for user");
            } else {
                System.out.println("DEBUG: OAuth user - no login attempts to reset");
            }
            
            // Determine redirect URL based on customer type
            String redirectUrl = determineRedirectUrl(customer);
            System.out.println("DEBUG: Determined redirect URL: " + redirectUrl);
            
            response.sendRedirect(redirectUrl);
            System.out.println("DEBUG: Redirect sent successfully to: " + redirectUrl);
            
        } catch (Exception e) {
            System.out.println("ERROR: Exception in CustomerLoginSuccessHandler: " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect("/customer-login?error=system_error");
        }
    }
    
    /**
     * Determine the correct redirect URL based on customer type
     */
    private String determineRedirectUrl(Customer customer) {
        System.out.println("=== DEBUG: determineRedirectUrl ===");
        
        // Check CustomerType enum first
        if (customer.getCustomerType() != null) {
            CustomerType type = customer.getCustomerType();
            System.out.println("DEBUG: Customer has CustomerType: " + type);
            
            switch (type) {
                case PROPERTY_OWNER:
                    System.out.println("DEBUG: Redirecting PROPERTY_OWNER to /property-owner/dashboard");
                    return "/property-owner/dashboard";
                case DELEGATED_USER:
                    System.out.println("DEBUG: Redirecting DELEGATED_USER to /property-owner/dashboard");
                    return "/property-owner/dashboard";
                case TENANT:
                    System.out.println("DEBUG: Redirecting TENANT to /tenant/dashboard");
                    return "/tenant/dashboard";
                case CONTRACTOR:
                    System.out.println("DEBUG: Redirecting CONTRACTOR to /contractor/dashboard");
                    return "/contractor/dashboard";
                case ADMIN:
                case SUPER_ADMIN:
                    System.out.println("DEBUG: Redirecting ADMIN to admin dashboard");
                    return "/admin/dashboard";
                case EMPLOYEE:
                    System.out.println("DEBUG: Redirecting EMPLOYEE to employee dashboard");
                    return "/employee/dashboard";
                case REGULAR_CUSTOMER:
                default:
                    System.out.println("DEBUG: Redirecting REGULAR_CUSTOMER to customer dashboard");
                    return "/customer/dashboard";
            }
        }
        
        // Fallback to legacy boolean flags
        System.out.println("DEBUG: No CustomerType set, checking boolean flags");
        System.out.println("DEBUG: isPropertyOwner: " + customer.getIsPropertyOwner());
        System.out.println("DEBUG: isTenant: " + customer.getIsTenant());
        System.out.println("DEBUG: isContractor: " + customer.getIsContractor());
        
        if (Boolean.TRUE.equals(customer.getIsPropertyOwner())) {
            System.out.println("DEBUG: Boolean flag isPropertyOwner=true, redirecting to /property-owner/dashboard");
            return "/property-owner/dashboard";
        }
        
        if (Boolean.TRUE.equals(customer.getIsTenant())) {
            System.out.println("DEBUG: Boolean flag isTenant=true, redirecting to /tenant/dashboard");
            return "/tenant/dashboard";
        }
        
        if (Boolean.TRUE.equals(customer.getIsContractor())) {
            System.out.println("DEBUG: Boolean flag isContractor=true, redirecting to /contractor/dashboard");
            return "/contractor/dashboard";
        }
        
        // Default fallback
        System.out.println("DEBUG: No specific type found, defaulting to /customer/dashboard");
        return "/customer/dashboard";
    }
}
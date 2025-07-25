package site.easy.to.build.crm.util;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import site.easy.to.build.crm.entity.CustomerLoginInfo;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.service.customer.CustomerLoginInfoService;
import site.easy.to.build.crm.service.user.OAuthUserService;
import site.easy.to.build.crm.service.user.UserService;


@Component
public class AuthenticationUtils {

    private final UserService userService;
    private final OAuthUserService oAuthUserService;
    private final CustomerLoginInfoService customerLoginInfoService;
    private final UserDetailsService crmUserDetails;
    private final UserDetailsService customerUserDetails;

    @Autowired
    public AuthenticationUtils(UserService userService, OAuthUserService oAuthUserService, CustomerLoginInfoService customerLoginInfoService,
                               UserDetailsService crmUserDetails, UserDetailsService customerUserDetails) {
        this.userService = userService;
        this.oAuthUserService = oAuthUserService;
        this.customerLoginInfoService = customerLoginInfoService;
        this.crmUserDetails = crmUserDetails;
        this.customerUserDetails = customerUserDetails;
    }

    public OAuthUser getOAuthUserFromAuthentication(Authentication authentication) {
        System.out.println("🔍 DEBUG: Getting OAuth user from authentication...");
        
        if(oAuthUserService == null){
            System.out.println("❌ OAuth user service is null");
            return null;
        }
        
        try {
            String email = ((OAuth2User)authentication.getPrincipal()).getAttribute("email");
            System.out.println("   Extracted email from OAuth principal: " + email);
            
            OAuthUser oAuthUser = oAuthUserService.findBtEmail(email);
            System.out.println("   OAuth user found by email: " + (oAuthUser != null));
            
            if (oAuthUser != null) {
                System.out.println("   OAuth user ID: " + oAuthUser.getId());
                System.out.println("   OAuth user email: " + oAuthUser.getEmail());
                System.out.println("   OAuth user has access token: " + (oAuthUser.getAccessToken() != null));
                System.out.println("   OAuth user has refresh token: " + (oAuthUser.getRefreshToken() != null));
            }
            
            return oAuthUser;
        } catch (Exception e) {
            System.err.println("❌ Error extracting OAuth user: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public int getLoggedInUserId(Authentication authentication) {
        System.out.println("🔐 DEBUG: AuthenticationUtils.getLoggedInUserId() called");
        System.out.println("   Authentication type: " + authentication.getClass().getSimpleName());
        System.out.println("   Authentication name: " + authentication.getName());
        System.out.println("   Is authenticated: " + authentication.isAuthenticated());
        
        User user;
        CustomerLoginInfo customerLoginInfo;
        if (authentication instanceof UsernamePasswordAuthenticationToken) {
            System.out.println("   Processing UsernamePasswordAuthenticationToken...");
            UserDetailsService authenticatedUserDetailsService = getAuthenticatedUserDetailsService(authentication);
            String userName = authentication.getName();
            if (authenticatedUserDetailsService == crmUserDetails) {
                user = userService.findByUsername(userName).get(0);
                if (user == null) {
                    return -1;
                }
                return user.getId();
            } else if (authenticatedUserDetailsService == customerUserDetails) {
                customerLoginInfo = customerLoginInfoService.findByEmail(userName);
                if (customerLoginInfo == null) {
                    return -1;
                }
                return customerLoginInfo.getId();
            }
        } else {
            System.out.println("   Processing OAuth authentication...");
            OAuthUser oAuthUser = getOAuthUserFromAuthentication(authentication);
            System.out.println("   OAuth user found: " + (oAuthUser != null));
            if (oAuthUser == null) {
                System.out.println("❌ No OAuth user found, returning -1");
                return -1;
            }
            user = oAuthUser.getUser();
            System.out.println("   OAuth user ID: " + (user != null ? user.getId() : "null"));
            return user != null ? user.getId() : -1;
        }
        System.out.println("❌ Failed to determine user ID, returning -1");
        return -1;
    }
    public boolean checkIfAppHasAccess(String serviceAccessUrl, OAuthUser oAuthUser) {
        return oAuthUser.getGrantedScopes().contains(serviceAccessUrl);
    }

    public UserDetailsService getAuthenticatedUserDetailsService(Authentication authentication) {
        UserDetailsService authenticatedUserDetailsService = null;

        if (authentication != null && authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User authenticatedUser) {
            String authenticatedUsername = authenticatedUser.getUsername();

            if (authenticatedUsername != null) {
                try {
                    if (crmUserDetails != null && authenticatedUsername.equals(crmUserDetails.loadUserByUsername(authenticatedUsername).getUsername())) {
                        authenticatedUserDetailsService = crmUserDetails;
                    }
                } catch (UsernameNotFoundException e) {
                    // Swallow the exception and continue to the next condition
                }

                if (authenticatedUserDetailsService == null && customerUserDetails != null) {
                    try {
                        if (authenticatedUsername.equals(customerUserDetails.loadUserByUsername(authenticatedUsername).getUsername())) {
                            authenticatedUserDetailsService = customerUserDetails;
                        }
                    } catch (UsernameNotFoundException e) {
                        // Swallow the exception and continue to the next steps
                    }
                }
            }
        }

        return authenticatedUserDetailsService;
    }
}
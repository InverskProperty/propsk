package site.easy.to.build.crm.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerLoginInfo;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.service.customer.CustomerLoginInfoService;
import site.easy.to.build.crm.service.user.OAuthUserService;
import site.easy.to.build.crm.service.user.UserService;
import java.util.List;

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
        System.out.println("   Authentication type: " + authentication.getClass().getSimpleName());
        System.out.println("   Principal type: " + authentication.getPrincipal().getClass().getSimpleName());

        if(oAuthUserService == null){
            System.out.println("❌ OAuth user service is null");
            return null;
        }

        // Check if this is actually an OAuth authentication
        if (!(authentication.getPrincipal() instanceof OAuth2User)) {
            System.out.println("ℹ️ Not an OAuth authentication, principal is: " + authentication.getPrincipal().getClass().getSimpleName());
            return null;
        }

        try {
            OAuth2User oAuth2Principal = (OAuth2User) authentication.getPrincipal();
            String email = oAuth2Principal.getAttribute("email");
            System.out.println("   Extracted email from OAuth principal: " + email);

            if (email == null || email.trim().isEmpty()) {
                System.out.println("❌ No email found in OAuth principal attributes");
                return null;
            }

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

    public Integer getOAuthUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2User)) {
            return null;
        }
        
        OAuthUser oAuthUser = getOAuthUserFromAuthentication(authentication);
        if (oAuthUser != null) {
            return oAuthUser.getUserId() != null ? oAuthUser.getUserId() : null;
        }
        
        return null;
    }

    /**
     * LEGACY METHOD: Keep returning int for backward compatibility
     * This prevents breaking 100+ existing usages throughout the codebase
     */
    public int getLoggedInUserId(Authentication authentication) {
        System.out.println("🔐 DEBUG: AuthenticationUtils.getLoggedInUserId() called (LEGACY INT VERSION)");
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
                // FIXED: Add null safety check for findByUsername result
                try {
                    List<User> users = userService.findByUsername(userName);
                    if (users == null || users.isEmpty()) {
                        System.out.println("❌ No users found by username: " + userName);
                        return -1;
                    }
                    user = users.get(0);
                    if (user == null) {
                        System.out.println("❌ User is null after retrieval: " + userName);
                        return -1;
                    }
                    System.out.println("✅ Found user by username: " + user.getId());
                    return user.getId(); // Return int directly
                } catch (Exception e) {
                    System.out.println("❌ Error finding user by username " + userName + ": " + e.getMessage());
                    e.printStackTrace();
                    return -1;
                }
            } else if (authenticatedUserDetailsService == customerUserDetails) {
                try {
                    System.out.println("🔍 Looking up customer login info for: " + userName);
                    customerLoginInfo = customerLoginInfoService.findByEmail(userName);
                    if (customerLoginInfo == null) {
                        System.out.println("❌ Customer login info not found by email: " + userName);
                        return -1;
                    }

                    System.out.println("✅ Found CustomerLoginInfo - ID: " + customerLoginInfo.getId());

                    // FIXED: Return the actual customer_id, not the login_info id (which is profile_id)
                    Customer customer = customerLoginInfo.getCustomer();
                    if (customer == null) {
                        System.out.println("❌ DELEGATED USER ISSUE: Customer not linked to login info for email: " + userName);
                        System.out.println("   CustomerLoginInfo.id (profile_id): " + customerLoginInfo.getId());
                        System.out.println("   This user might be a delegated user without a Customer record");
                        System.out.println("   Returning profile_id as fallback: " + customerLoginInfo.getId());
                        // For delegated users, return the login_info.id as the customer identifier
                        return customerLoginInfo.getId();
                    }

                    Long customerId = customer.getCustomerId();
                    if (customerId == null) {
                        System.out.println("❌ Customer ID is null for email: " + userName);
                        System.out.println("   Falling back to profile_id: " + customerLoginInfo.getId());
                        return customerLoginInfo.getId();
                    }

                    System.out.println("✅ Found customer ID: " + customerId + " (profile_id: " + customerLoginInfo.getId() + ", customer_type: " + customer.getCustomerType() + ")");
                    return customerId.intValue(); // Return actual customer_id as int
                } catch (Exception e) {
                    System.out.println("❌ Error finding customer login info by email " + userName + ": " + e.getMessage());
                    e.printStackTrace();
                    return -1;
                }
            }
        } else {
            System.out.println("   Processing OAuth authentication...");
            OAuthUser oAuthUser = getOAuthUserFromAuthentication(authentication);
            System.out.println("   OAuth user found: " + (oAuthUser != null));
            
            if (oAuthUser == null) {
                System.out.println("❌ No OAuth user found, returning -1");
                return -1;
            }
            
            System.out.println("   OAuth user table ID: " + oAuthUser.getId());
            System.out.println("   OAuth user's user_id field: " + oAuthUser.getUserId());
            
            Integer linkedUserId = oAuthUser.getUserId();
            if (linkedUserId != null) {
                System.out.println("✅ Returning linked user ID: " + linkedUserId);
                return linkedUserId; // Return int directly
            } else {
                System.out.println("❌ OAuth user has no linked User record, attempting auto-link...");
                
                // EMERGENCY FIX: Try to auto-link OAuth user to User record by email
                try {
                    if (authentication.getPrincipal() instanceof OAuth2User) {
                        String email = ((OAuth2User) authentication.getPrincipal()).getAttribute("email");
                        if (email != null && !email.trim().isEmpty()) {
                            User matchingUser = userService.findByEmail(email);
                            if (matchingUser != null) {
                                System.out.println("🔗 EMERGENCY: Auto-linking OAuth user to User: " + matchingUser.getId());
                                oAuthUser.setUser(matchingUser);
                                matchingUser.setOauthUser(oAuthUser);
                                oAuthUserService.save(oAuthUser);
                                System.out.println("✅ EMERGENCY: Auto-link successful, returning: " + matchingUser.getId());
                                return matchingUser.getId();
                            } else {
                                System.out.println("❌ EMERGENCY: No matching User found for auto-link");
                            }
                        } else {
                            System.out.println("❌ EMERGENCY: No email found in OAuth principal for auto-link");
                        }
                    } else {
                        System.out.println("❌ EMERGENCY: Cannot auto-link, not an OAuth authentication");
                    }
                } catch (Exception e) {
                    System.err.println("❌ EMERGENCY: Auto-link failed: " + e.getMessage());
                }
                
                System.out.println("❌ OAuth user has no linked User record, returning -1");
                return -1;
            }
        }
        
        System.out.println("❌ Failed to determine user ID, returning -1");
        return -1;
    }

    /**
     * NEW SECURE METHOD: Returns Long and eliminates hardcoded fallbacks
     * Use this for new PayProp integration code
     */
    public Long getLoggedInUserIdSecure(Authentication authentication) {
        System.out.println("🔐 DEBUG: AuthenticationUtils.getLoggedInUserIdSecure() called (NEW SECURE VERSION)");
        
        if (authentication == null) {
            System.err.println("❌ No authentication provided");
            return null;
        }
        
        // Use the existing logic but return Long and validate
        int userId = getLoggedInUserId(authentication);
        
        if (userId <= 0) {
            System.err.println("🚨 SECURITY: No valid user found for authentication");
            return null;
        }
        
        // SECURITY: Validate the user exists and is active
        try {
            User user = userService.findById(Long.valueOf(userId));
            if (user == null) {
                System.err.println("🚨 SECURITY: User ID " + userId + " not found in database");
                return null;
            }
            
            if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                System.err.println("🚨 SECURITY: User ID " + userId + " is not active (Status: " + user.getStatus() + ")");
                return null;
            }
            
            System.out.println("✅ Validated secure user ID: " + userId);
            return Long.valueOf(userId);
            
        } catch (Exception e) {
            System.err.println("❌ Error validating user ID " + userId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * SECURE: Get user ID by email with proper error handling
     */
    public Long getUserIdByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            System.err.println("❌ Email is null or empty");
            return null;
        }
        
        try {
            User user = userService.findByEmail(email.trim());
            if (user != null) {
                System.out.println("✅ Found user by email: " + user.getId() + " - " + user.getEmail());
                return Long.valueOf(user.getId());
            } else {
                System.out.println("❌ No user found for email: " + email);
                return null;
            }
        } catch (Exception e) {
            System.err.println("❌ Error finding user by email: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * SECURE: Validate that a user ID exists and is active
     */
    public boolean isValidActiveUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return false;
        }
        
        try {
            User user = userService.findById(userId);
            if (user == null) {
                System.out.println("❌ User not found for ID: " + userId);
                return false;
            }
            
            boolean isActive = "ACTIVE".equalsIgnoreCase(user.getStatus());
            if (!isActive) {
                System.out.println("⚠️ User " + userId + " is not active (Status: " + user.getStatus() + ")");
            }
            
            return isActive;
        } catch (Exception e) {
            System.err.println("❌ Error validating user ID " + userId + ": " + e.getMessage());
            return false;
        }
    }
    
    public boolean checkIfAppHasAccess(String serviceAccessUrl, OAuthUser oAuthUser) {
        return oAuthUser.getGrantedScopes().contains(serviceAccessUrl);
    }

    public UserDetailsService getAuthenticatedUserDetailsService(Authentication authentication) {
        System.out.println("🔍 Determining which UserDetailsService authenticated the user...");

        if (authentication == null) {
            System.out.println("❌ Authentication is null");
            return null;
        }

        if (!(authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User)) {
            System.out.println("❌ Principal is not a User instance: " + authentication.getPrincipal().getClass().getName());
            return null;
        }

        // CRITICAL FIX: Determine based on authorities, with priority to customer roles
        // This handles users who exist in BOTH systems (e.g., managers who are also delegated users)

        boolean hasCustomerRole = authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_PROPERTY_OWNER")
                           || auth.getAuthority().equals("ROLE_TENANT")
                           || auth.getAuthority().equals("ROLE_CONTRACTOR")
                           || auth.getAuthority().equals("ROLE_DELEGATED_USER"));

        boolean hasCrmRole = authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_MANAGER")
                           || auth.getAuthority().equals("ROLE_EMPLOYEE")
                           || auth.getAuthority().equals("ROLE_ADMIN")
                           || auth.getAuthority().equals("ROLE_SUPER_ADMIN"));

        System.out.println("   Has customer role: " + hasCustomerRole);
        System.out.println("   Has CRM role: " + hasCrmRole);

        // If they have customer roles (even if they also have CRM roles), they logged in via customer portal
        if (hasCustomerRole) {
            System.out.println("✅ Has customer role -> using customerUserDetails");
            return customerUserDetails;
        } else if (hasCrmRole) {
            System.out.println("✅ Only has CRM role -> using crmUserDetails");
            return crmUserDetails;
        } else {
            System.out.println("⚠️ No matching roles, falling back to CRM");
            return crmUserDetails;
        }
    }
}
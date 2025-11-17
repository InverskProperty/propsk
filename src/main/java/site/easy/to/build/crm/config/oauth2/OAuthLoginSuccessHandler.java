package site.easy.to.build.crm.config.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.entity.Role;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.entity.UserProfile;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.service.role.RoleService;
import site.easy.to.build.crm.service.user.OAuthUserService;
import site.easy.to.build.crm.service.user.UserProfileService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class OAuthLoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    public final OAuthUserService oAuthUserService;
    public final UserService userService;
    public final UserProfileService userProfileService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    public final AuthenticationUtils authenticationUtils;
    public final RoleService roleService;
    private final Environment environment;
    private final CustomerRepository customerRepository;
    private final site.easy.to.build.crm.service.customer.CustomerService customerService;

    @Autowired
    public OAuthLoginSuccessHandler(OAuthUserService oAuthUserService, UserService userService, UserProfileService userProfileService,
                                    OAuth2AuthorizedClientService authorizedClientService, AuthenticationUtils authenticationUtils, RoleService roleService, Environment environment, CustomerRepository customerRepository,
                                    site.easy.to.build.crm.service.customer.CustomerService customerService) {
        this.oAuthUserService = oAuthUserService;
        this.userService = userService;
        this.userProfileService = userProfileService;
        this.authorizedClientService = authorizedClientService;
        this.authenticationUtils = authenticationUtils;
        this.roleService = roleService;
        this.environment = environment;
        this.customerRepository = customerRepository;
        this.customerService = customerService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        System.out.println("🔐 OAuth Login Success Handler - Starting authentication process");
        
        // Get the registration ID of the OAuth2 provider
        String googleClientId = environment.getProperty("spring.security.oauth2.client.registration.google.client-id");
        String googleClientSecret = environment.getProperty("spring.security.oauth2.client.registration.google.client-secret");
        
        if (StringUtils.isEmpty(googleClientId) || StringUtils.isEmpty(googleClientSecret)) {
            System.err.println("❌ Google OAuth2 configuration missing");
            response.sendRedirect("/error-page");
            return;
        }
        
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();

        if (registrationId == null) {
            throw new ServletException("Failed to find the registrationId from the authorities");
        }
        
        // Obtain the OAuth2AuthorizedClient
        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(registrationId, authentication.getName());

        // Get the access and the refresh token from the OAuth2AuthorizedClient
        OAuth2AccessToken oAuth2AccessToken = authorizedClient.getAccessToken();
        OAuth2RefreshToken oAuth2RefreshToken = authorizedClient.getRefreshToken();
        
        System.out.println("🔐 DEBUG: OAuth tokens received from Google:");
        System.out.println("   Access token present: " + (oAuth2AccessToken != null));
        if (oAuth2AccessToken != null) {
            System.out.println("   Access token expires at: " + oAuth2AccessToken.getExpiresAt());
            System.out.println("   Access token scopes: " + oAuth2AccessToken.getScopes());
            System.out.println("   Access token value (first 20 chars): " + 
                (oAuth2AccessToken.getTokenValue() != null ? oAuth2AccessToken.getTokenValue().substring(0, Math.min(20, oAuth2AccessToken.getTokenValue().length())) + "..." : "null"));
        }
        System.out.println("   Refresh token present: " + (oAuth2RefreshToken != null));
        if (oAuth2RefreshToken != null) {
            System.out.println("   Refresh token expires at: " + oAuth2RefreshToken.getExpiresAt());
            System.out.println("   Refresh token value (first 20 chars): " + 
                (oAuth2RefreshToken.getTokenValue() != null ? oAuth2RefreshToken.getTokenValue().substring(0, Math.min(20, oAuth2RefreshToken.getTokenValue().length())) + "..." : "null"));
        } else {
            System.out.println("   ⚠️ NO REFRESH TOKEN - This means:");
            System.out.println("      1. User previously authorized and Google didn't provide new refresh token");
            System.out.println("      2. OAuth request didn't include access_type=offline");
            System.out.println("      3. OAuth request didn't include prompt=consent");
            System.out.println("      4. User needs to revoke app access in Google and re-authenticate");
        }

        HttpSession session = request.getSession();
        boolean previouslyUsedRegularAccount = session.getAttribute("loggedInUserId") != null;
        int userId = (previouslyUsedRegularAccount) ? (int) session.getAttribute("loggedInUserId") : -1;
        
        System.out.println("🔍 DEBUG: Session analysis:");
        System.out.println("   Previous regular account login: " + previouslyUsedRegularAccount);
        System.out.println("   Session user ID: " + userId);
        
        User loggedUser = null;
        if (userId != -1) {
            System.out.println("   Attempting to find user by ID: " + userId);
            loggedUser = userService.findById(Long.valueOf(userId));
            System.out.println("   User found by session ID: " + (loggedUser != null));
            if (loggedUser != null) {
                System.out.println("   User email: " + loggedUser.getEmail());
                System.out.println("   User has OAuth user: " + (loggedUser.getOauthUser() != null));
            }
        }
        
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        System.out.println("🔍 DEBUG: OAuth user from authentication: " + (oAuthUser != null));
        if (oAuthUser != null) {
            System.out.println("   OAuth user email: " + oAuthUser.getEmail());
            System.out.println("   OAuth user scopes: " + oAuthUser.getGrantedScopes());
            System.out.println("   OAuth user has access token: " + (oAuthUser.getAccessToken() != null));
            System.out.println("   OAuth user has refresh token: " + (oAuthUser.getRefreshToken() != null));
        }
        if (loggedUser != null && loggedUser.getOauthUser() == null && oAuthUser == null) {
            oAuthUser = new OAuthUser();
            oAuthUser.getGrantedScopes().add("openid");
            oAuthUser.getGrantedScopes().add("email");
            oAuthUser.getGrantedScopes().add("profile");
            oAuthUser.getGrantedScopes().add("https://www.googleapis.com/auth/gmail.send");  
            String email = ((DefaultOidcUser) authentication.getPrincipal()).getEmail();
            oAuthUser.setEmail(email);
            oAuthUserService.updateOAuthUserTokens(oAuthUser, oAuth2AccessToken, oAuth2RefreshToken);
            oAuthUserService.save(oAuthUser);
            response.sendRedirect("/connect-accounts");
        } else {

            String email = ((DefaultOidcUser) authentication.getPrincipal()).getEmail();
            String img = ((DefaultOidcUser) authentication.getPrincipal()).getPicture();
            String firstName = ((DefaultOidcUser) authentication.getPrincipal()).getGivenName();
            String lastName = ((DefaultOidcUser) authentication.getPrincipal()).getFamilyName();
            String username = email.split("@")[0];

            System.out.println("📧 Processing OAuth login for: " + email);

            // FIXED: Use new Long-based method
            Long currUserId = authenticationUtils.getLoggedInUserIdSecure(authentication);
            System.out.println("🔍 DEBUG: Authentication analysis:");
            System.out.println("   Current user ID from auth: " + currUserId);
            
            User user = null;
            if (currUserId != null && currUserId > 0) {
                user = userService.findById(currUserId);
                System.out.println("   User found by ID: " + (user != null));
            }
            
            // CRITICAL FIX: If user not found by OAuth method, try finding by email
            if (user == null) {
                System.out.println("🔍 User not found via OAuth method, searching by email: " + email);
                user = userService.findByEmail(email);
                if (user != null) {
                    System.out.println("✅ Found existing user by email: " + user.getId() + " - " + user.getEmail());
                    System.out.println("   User status: " + user.getStatus());
                    System.out.println("   User roles: " + user.getRoles().stream().map(r -> r.getName()).toList());
                    System.out.println("   User has OAuth user: " + (user.getOauthUser() != null));
                } else {
                    System.out.println("❌ No existing user found by email: " + email);
                    System.out.println("   Will create new user account");
                }
            } else {
                System.out.println("✅ User found via OAuth method: " + user.getId() + " - " + user.getEmail());
                System.out.println("   User status: " + user.getStatus());
                System.out.println("   User has OAuth user: " + (user.getOauthUser() != null));
            }
            
            OAuthUser loggedOAuthUser;

            // CUSTOMER OAUTH SUPPORT: Check for customer account if no employee user found
            if (user == null) {
                System.out.println("🔍 No employee user found, checking for customer account...");
                Customer customer = customerRepository.findByEmail(email);
                if (customer != null) {
                    System.out.println("✅ Found customer account: " + customer.getCustomerId() + " - " + customer.getEmail());
                    System.out.println("   Customer Type: " + customer.getCustomerType());
                    System.out.println("   Is Property Owner: " + customer.getIsPropertyOwner());
                    
                    // CRITICAL FIX: Create OAuth user for customer to store Google tokens
                    System.out.println("🔗 Creating OAuth user for customer to store Google tokens...");
                    OAuthUser customerOAuthUser = createOAuthUserForCustomer(customer, email, oAuth2AccessToken, oAuth2RefreshToken);
                    
                    // CRITICAL FIX: Set up proper Spring Security authentication for customer
                    System.out.println("🔐 Setting up customer authentication with proper roles...");
                    setupCustomerAuthentication(request, response, authentication, customer);
                    
                    // Redirect customer to appropriate dashboard
                    String redirectUrl = determineCustomerRedirectUrl(customer);
                    System.out.println("🏠 Redirecting customer to: " + redirectUrl);
                    response.sendRedirect(redirectUrl);
                    return;
                } else {
                    System.out.println("❌ No customer account found either for email: " + email);
                }
            }

            if (user == null) {
                System.out.println("🆕 Creating new user for: " + email);
                user = new User();
                UserProfile userProfile = new UserProfile();
                userProfile.setFirstName(firstName);
                userProfile.setLastName(lastName);
                userProfile.setOathUserImageLink(img);
                user.setEmail(email);
                user.setUsername(username);
                user.setPasswordSet(true);

                long countUsers = userService.countAllUsers();
                Role role;
                
                if (countUsers == 0) {
                    // First user gets MANAGER role
                    role = roleService.findByName("ROLE_MANAGER");
                    user.setStatus("active");
                    userProfile.setStatus("active");
                    System.out.println("👑 First user - assigning MANAGER role and active status");
                } else {
                    // FIXED: Determine role based on email domain/patterns instead of automatic MANAGER
                    role = determineRoleForNewUser(email);
                    user.setStatus("active");
                    userProfile.setStatus("active");
                    System.out.println("👤 New user - assigning " + role.getName() + " role based on email analysis");
                }

                user.setRoles(List.of(role));
                user.setCreatedAt(LocalDateTime.now());
                User createdUser = userService.save(user);
                userProfile.setUser(createdUser);
                userProfileService.save(userProfile);

                loggedOAuthUser = new OAuthUser();
                loggedOAuthUser.setEmail(email);
                // Initial scopes - will be replaced by actual scopes from OAuth2AccessToken in updateOAuthUserTokens
                loggedOAuthUser.getGrantedScopes().addAll(List.of(
                    "openid",
                    "email",
                    "profile",
                    "https://www.googleapis.com/auth/gmail.send",
                    "https://www.googleapis.com/auth/gmail.modify",
                    "https://www.googleapis.com/auth/drive",
                    "https://www.googleapis.com/auth/spreadsheets",
                    "https://www.googleapis.com/auth/calendar"
                ));
                oAuthUserService.updateOAuthUserTokens(loggedOAuthUser, oAuth2AccessToken, oAuth2RefreshToken);
            } else {
                System.out.println("♻️ Using existing user: " + user.getId() + " - " + user.getEmail());
                
                // SECURITY CHECK: Validate user account status
                if (!isUserAccountValid(user)) {
                    System.err.println("🚨 SECURITY: Invalid user account attempting OAuth login: " + user.getEmail());
                    System.err.println("   Status: " + user.getStatus());
                    
                    if ("INACTIVE".equalsIgnoreCase(user.getStatus())) {
                        response.sendRedirect("/account-inactive");
                        return;
                    } else if ("SUSPENDED".equalsIgnoreCase(user.getStatus())) {
                        response.sendRedirect("/account-suspended");
                        return;
                    } else {
                        response.sendRedirect("/access-denied");
                        return;
                    }
                }
                
                // FIXED: Check if OAuth user exists for this user
                if (user.getOauthUser() != null) {
                    loggedOAuthUser = user.getOauthUser();
                    System.out.println("✅ Using existing OAuth user");
                    // CRITICAL FIX: Update tokens for existing OAuth user
                    System.out.println("🔄 Updating tokens for existing OAuth user");
                    oAuthUserService.updateOAuthUserTokens(loggedOAuthUser, oAuth2AccessToken, oAuth2RefreshToken);
                } else {
                    // Create new OAuth user for existing user
                    System.out.println("🔧 Creating new OAuth user for existing user");
                    loggedOAuthUser = new OAuthUser();
                    loggedOAuthUser.setEmail(email);
                    // Initial scopes - will be replaced by actual scopes from OAuth2AccessToken in updateOAuthUserTokens
                    loggedOAuthUser.getGrantedScopes().addAll(List.of(
                        "openid",
                        "email",
                        "profile",
                        "https://www.googleapis.com/auth/gmail.send",
                        "https://www.googleapis.com/auth/gmail.modify",
                        "https://www.googleapis.com/auth/drive",
                        "https://www.googleapis.com/auth/spreadsheets",
                        "https://www.googleapis.com/auth/calendar"
                    ));
                    oAuthUserService.updateOAuthUserTokens(loggedOAuthUser, oAuth2AccessToken, oAuth2RefreshToken);
                }
            }

            oAuthUserService.save(loggedOAuthUser, user);
            
            // CRITICAL DEBUG: Check user roles before converting to authorities
            System.out.println("🔍 DEBUG: User ID: " + user.getId());
            System.out.println("🔍 DEBUG: User email: " + user.getEmail());
            System.out.println("🔍 DEBUG: User status: " + user.getStatus());
            System.out.println("🔍 DEBUG: User roles from DB: " + user.getRoles().stream()
                .map(Role::getName).collect(Collectors.toList()));
            
            // Convert database roles to Spring Security authorities
            List<GrantedAuthority> authorities = user.getRoles().stream()
                    .map(role -> new SimpleGrantedAuthority(role.getName()))
                    .collect(Collectors.toList());
            
            System.out.println("🔍 DEBUG: Converted authorities: " + authorities.stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toList()));

            // Get existing OAuth authorities and add database roles
            List<GrantedAuthority> updatedAuthorities = new ArrayList<>(authentication.getAuthorities());
            updatedAuthorities.addAll(authorities);
            
            System.out.println("🔍 DEBUG: Original OAuth authorities: " + authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toList()));
            System.out.println("🔍 DEBUG: Final combined authorities: " + updatedAuthorities.stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toList()));

            OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

            // Create new authentication with combined authorities
            Authentication updatedAuthentication = new OAuth2AuthenticationToken(
                    oauthUser,
                    updatedAuthorities,
                    registrationId
            );

            // CRITICAL: Update the SecurityContext
            SecurityContextHolder.getContext().setAuthentication(updatedAuthentication);
            
            System.out.println("✅ SecurityContext updated with new authentication");
            System.out.println("🔍 DEBUG: New authentication authorities: " + updatedAuthentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toList()));
            
            // Check user status and redirect accordingly
            if ("INACTIVE".equalsIgnoreCase(user.getStatus())) {
                System.out.println("⚠️ User is inactive, redirecting to inactive page");
                response.sendRedirect("/account-inactive");
            } else if ("SUSPENDED".equalsIgnoreCase(user.getStatus())) {
                System.out.println("⚠️ User is suspended, redirecting to suspended page");
                response.sendRedirect("/account-suspended");
            } else {
                System.out.println("✅ User is active, redirecting to home page");
                response.sendRedirect("/");
            }
        }
    }

    /**
     * FIXED: Secure role determination based on email analysis
     * No longer automatically assigns MANAGER role to all OAuth users
     */
    private Role determineRoleForNewUser(String email) {
        System.out.println("🔍 Determining role for new user: " + email);
        
        if (email == null || email.trim().isEmpty()) {
            System.out.println("   → EMPLOYEE role (no email provided)");
            return roleService.findByName("ROLE_EMPLOYEE");
        }
        
        email = email.trim().toLowerCase();
        
        // SECURITY: Management emails get MANAGER role
        if (email.equals("management@propsk.com") || 
            email.equals("sajidkazmi@propsk.com") ||
            email.equals("admin@localhost.com") ||
            email.equals("admin@propsk.com")) {
            System.out.println("   → MANAGER role (management email)");
            return roleService.findByName("ROLE_MANAGER");
        }
        
        // Company domain emails get EMPLOYEE role
        if (email.endsWith("@propsk.com")) {
            System.out.println("   → EMPLOYEE role (company domain)");
            return roleService.findByName("ROLE_EMPLOYEE");
        }
        
        // Check if this is a known property owner email
        if (isKnownPropertyOwnerEmail(email)) {
            System.out.println("   → PROPERTY_OWNER role (known property owner)");
            return roleService.findByName("ROLE_PROPERTY_OWNER");
        }
        
        // Check if this is a known tenant email
        if (isKnownTenantEmail(email)) {
            System.out.println("   → TENANT role (known tenant)");
            return roleService.findByName("ROLE_TENANT");
        }
        
        // SECURITY: Default for external users - LIMITED ROLE
        System.out.println("   → EMPLOYEE role (default for external users - can be upgraded manually)");
        return roleService.findByName("ROLE_EMPLOYEE");
    }

    /**
     * Check if email belongs to a known property owner
     * This should query your Customer table or other data sources
     */
    private boolean isKnownPropertyOwnerEmail(String email) {
        try {
            System.out.println("🔍 ROLE CHECK: Checking if email is property owner: " + email);
            
            // Look up customer in database to check if they are a property owner
            Customer customer = customerService.findByEmail(email);
            if (customer != null) {
                boolean isPropertyOwnerByType = customer.getCustomerType() == CustomerType.PROPERTY_OWNER;
                boolean isPropertyOwnerByFlag = Boolean.TRUE.equals(customer.getIsPropertyOwner());
                
                System.out.println("🔍 ROLE CHECK: Customer found - ID: " + customer.getCustomerId());
                System.out.println("🔍 ROLE CHECK: Customer type: " + customer.getCustomerType());
                System.out.println("🔍 ROLE CHECK: Is property owner flag: " + customer.getIsPropertyOwner());
                System.out.println("🔍 ROLE CHECK: Is property owner by type: " + isPropertyOwnerByType);
                System.out.println("🔍 ROLE CHECK: Is property owner by flag: " + isPropertyOwnerByFlag);
                
                boolean result = isPropertyOwnerByType || isPropertyOwnerByFlag;
                System.out.println("🔍 ROLE CHECK: Final result: " + result + " (will assign ROLE_PROPERTY_OWNER: " + result + ")");
                return result;
            } else {
                System.out.println("🔍 ROLE CHECK: No customer found for email: " + email);
                return false;
            }
        } catch (Exception e) {
            System.err.println("❌ ROLE CHECK: Error checking property owner email: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Check if email belongs to a known tenant
     * This should query your Customer table or other data sources
     */
    private boolean isKnownTenantEmail(String email) {
        try {
            // TODO: Implement actual lookup against Customer table
            // Example:
            // Customer customer = customerService.findByEmailAndType(email, CustomerType.TENANT);
            // return customer != null;
            
            // For now, return false and handle role assignment manually through admin
            return false;
        } catch (Exception e) {
            System.err.println("❌ Error checking tenant email: " + e.getMessage());
            return false;
        }
    }

    /**
     * SECURITY: Validate user account status
     */
    private boolean isUserAccountValid(User user) {
        if (user == null) {
            System.err.println("🚨 SECURITY: Null user in validation");
            return false;
        }
        
        String status = user.getStatus();
        if (status == null) {
            System.err.println("🚨 SECURITY: User " + user.getId() + " has null status");
            return false;
        }
        
        // Only allow ACTIVE users to login via OAuth
        boolean isActive = "ACTIVE".equalsIgnoreCase(status.trim());
        
        if (!isActive) {
            System.err.println("🚨 SECURITY: User " + user.getId() + " (" + user.getEmail() + 
                ") attempted OAuth login with status: " + status);
        }
        
        return isActive;
    }

    /**
     * AUDIT: Log security events
     */
    private void logSecurityEvent(String event, User user, String details) {
        System.out.println("🔐 SECURITY EVENT: " + event);
        if (user != null) {
            System.out.println("   User: " + user.getId() + " (" + user.getEmail() + ")");
            System.out.println("   Status: " + user.getStatus());
        }
        System.out.println("   Details: " + details);
        System.out.println("   Timestamp: " + LocalDateTime.now());
        
        // TODO: Implement proper audit logging to database
        // auditService.logSecurityEvent(event, user, details);
    }

    /**
     * Determine the correct redirect URL for customer OAuth users based on customer type
     */
    private String determineCustomerRedirectUrl(Customer customer) {
        System.out.println("=== DEBUG: determineCustomerRedirectUrl ===");
        
        // Check CustomerType enum first
        if (customer.getCustomerType() != null) {
            switch (customer.getCustomerType()) {
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
    
    /**
     * Set up proper Spring Security authentication for customer OAuth users
     * This ensures customers have the correct roles assigned for authorization
     */
    private void setupCustomerAuthentication(HttpServletRequest request, HttpServletResponse response, 
                                           Authentication originalAuth, Customer customer) {
        try {
            System.out.println("🔐 SETUP_AUTH: Setting up customer authentication...");
            System.out.println("🔐 SETUP_AUTH: Customer ID: " + customer.getCustomerId());
            System.out.println("🔐 SETUP_AUTH: Customer Type: " + customer.getCustomerType());
            System.out.println("🔐 SETUP_AUTH: Is Property Owner: " + customer.getIsPropertyOwner());
            
            // Determine the correct role for this customer
            String role = determineCustomerRole(customer);
            System.out.println("🔐 SETUP_AUTH: Determined role: " + role);
            
            // Create a new authentication token with the correct role
            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority(role));
            
            // Create new OAuth2 authentication token with customer authorities
            OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) originalAuth;
            OAuth2AuthenticationToken customerToken = new OAuth2AuthenticationToken(
                oauth2Token.getPrincipal(),
                authorities,
                oauth2Token.getAuthorizedClientRegistrationId()
            );
            
            // Update the security context
            SecurityContextHolder.getContext().setAuthentication(customerToken);
            
            // Also update the session
            HttpSession session = request.getSession();
            session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            
            System.out.println("🔐 SETUP_AUTH: ✅ Customer authentication updated with role: " + role);
            System.out.println("🔐 SETUP_AUTH: Authorities: " + customerToken.getAuthorities());
            
        } catch (Exception e) {
            System.err.println("🚨 SETUP_AUTH: Error setting up customer authentication: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Determine the appropriate role for a customer based on their type and flags
     */
    private String determineCustomerRole(Customer customer) {
        if (customer == null) {
            return "ROLE_CUSTOMER";
        }
        
        CustomerType customerType = customer.getCustomerType();
        
        // Check customer type first
        if (customerType != null) {
            switch (customerType) {
                case PROPERTY_OWNER:
                    System.out.println("🔐 ROLE: CustomerType is PROPERTY_OWNER, returning ROLE_PROPERTY_OWNER");
                    return "ROLE_PROPERTY_OWNER";
                case DELEGATED_USER:
                    System.out.println("🔐 ROLE: CustomerType is DELEGATED_USER, returning ROLE_PROPERTY_OWNER");
                    return "ROLE_PROPERTY_OWNER";
                case TENANT:
                    return "ROLE_TENANT";
                case CONTRACTOR:
                    return "ROLE_CONTRACTOR";
                case EMPLOYEE:
                    return "ROLE_EMPLOYEE";
                case ADMIN:
                    return "ROLE_ADMIN";
                case SUPER_ADMIN:
                    return "ROLE_SUPER_ADMIN";
                default:
                    break;
            }
        }
        
        // Check boolean flags as backup
        if (Boolean.TRUE.equals(customer.getIsPropertyOwner())) {
            System.out.println("🔐 ROLE: Boolean flag isPropertyOwner=true, returning ROLE_PROPERTY_OWNER");
            return "ROLE_PROPERTY_OWNER";
        }
        
        // Default fallback
        return "ROLE_CUSTOMER";
    }
    
    /**
     * Create and save OAuth user for customer to store Google tokens
     * This fixes the statement center issue where customers couldn't connect Google accounts
     */
    private OAuthUser createOAuthUserForCustomer(Customer customer, String email, 
                                                OAuth2AccessToken oAuth2AccessToken, 
                                                OAuth2RefreshToken oAuth2RefreshToken) {
        try {
            System.out.println("🔗 CUSTOMER_OAUTH: Creating OAuth user for customer...");
            System.out.println("   Customer ID: " + customer.getCustomerId());
            System.out.println("   Customer email: " + email);
            
            // Check if OAuth user already exists for this email
            OAuthUser existingOAuthUser = oAuthUserService.findBtEmail(email);
            if (existingOAuthUser != null) {
                System.out.println("♻️ CUSTOMER_OAUTH: Found existing OAuth user, updating tokens...");
                oAuthUserService.updateOAuthUserTokens(existingOAuthUser, oAuth2AccessToken, oAuth2RefreshToken);
                
                // CRITICAL FIX: Check if OAuth user is properly linked to a User record
                if (existingOAuthUser.getUser() == null) {
                    System.out.println("🔗 CRITICAL FIX: OAuth user not linked to User record, searching for User by email...");
                    try {
                        User matchingUser = userService.findByEmail(email);
                        if (matchingUser != null) {
                            System.out.println("✅ Found matching User record: " + matchingUser.getId() + " - " + matchingUser.getEmail());
                            existingOAuthUser.setUser(matchingUser);
                            matchingUser.setOauthUser(existingOAuthUser);
                            System.out.println("🔗 Linked OAuth user to User record");
                        } else {
                            System.out.println("⚠️ No matching User record found for email: " + email);
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Error linking OAuth user to User record: " + e.getMessage());
                    }
                }
                
                oAuthUserService.save(existingOAuthUser);
                
                System.out.println("✅ CUSTOMER_OAUTH: Updated existing OAuth user tokens with proper User linking");
                return existingOAuthUser;
            }
            
            // Create new OAuth user for customer
            OAuthUser customerOAuthUser = new OAuthUser();
            customerOAuthUser.setEmail(email);
            
            // CRITICAL: Add Google Sheets scopes for statement generation
            customerOAuthUser.getGrantedScopes().clear();
            customerOAuthUser.getGrantedScopes().add("openid");
            customerOAuthUser.getGrantedScopes().add("email"); 
            customerOAuthUser.getGrantedScopes().add("profile");
            customerOAuthUser.getGrantedScopes().add("https://www.googleapis.com/auth/spreadsheets");
            customerOAuthUser.getGrantedScopes().add("https://www.googleapis.com/auth/drive.file");
            customerOAuthUser.getGrantedScopes().add("https://www.googleapis.com/auth/gmail.send");
            
            System.out.println("🔐 CUSTOMER_OAUTH: Added Google API scopes: " + customerOAuthUser.getGrantedScopes());
            
            // Store the Google tokens
            oAuthUserService.updateOAuthUserTokens(customerOAuthUser, oAuth2AccessToken, oAuth2RefreshToken);
            
            // Save without User link (customers don't have User entities)
            oAuthUserService.save(customerOAuthUser);
            
            System.out.println("✅ CUSTOMER_OAUTH: Successfully created OAuth user for customer");
            System.out.println("   OAuth user ID: " + customerOAuthUser.getId());
            System.out.println("   Has access token: " + (customerOAuthUser.getAccessToken() != null));
            System.out.println("   Has refresh token: " + (customerOAuthUser.getRefreshToken() != null));
            
            return customerOAuthUser;
            
        } catch (Exception e) {
            System.err.println("❌ CUSTOMER_OAUTH: Error creating OAuth user for customer: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
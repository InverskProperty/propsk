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
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.entity.Role;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.entity.UserProfile;
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

    @Autowired
    public OAuthLoginSuccessHandler(OAuthUserService oAuthUserService, UserService userService, UserProfileService userProfileService,
                                    OAuth2AuthorizedClientService authorizedClientService, AuthenticationUtils authenticationUtils, RoleService roleService, Environment environment) {
        this.oAuthUserService = oAuthUserService;
        this.userService = userService;
        this.userProfileService = userProfileService;
        this.authorizedClientService = authorizedClientService;
        this.authenticationUtils = authenticationUtils;
        this.roleService = roleService;
        this.environment = environment;
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
                    user.setStatus("ACTIVE");
                    userProfile.setStatus("ACTIVE");
                    System.out.println("👑 First user - assigning MANAGER role and ACTIVE status");
                } else {
                    // FIXED: Determine role based on email domain/patterns instead of automatic MANAGER
                    role = determineRoleForNewUser(email);
                    user.setStatus("ACTIVE");
                    userProfile.setStatus("ACTIVE");
                    System.out.println("👤 New user - assigning " + role.getName() + " role based on email analysis");
                }

                user.setRoles(List.of(role));
                user.setCreatedAt(LocalDateTime.now());
                User createdUser = userService.save(user);
                userProfile.setUser(createdUser);
                userProfileService.save(userProfile);

                loggedOAuthUser = new OAuthUser();
                loggedOAuthUser.setEmail(email);
                loggedOAuthUser.getGrantedScopes().addAll(List.of("openid", "email", "profile"));
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
                    loggedOAuthUser.getGrantedScopes().addAll(List.of("openid", "email", "profile"));
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
            // TODO: Implement actual lookup against Customer table
            // Example:
            // Customer customer = customerService.findByEmailAndType(email, CustomerType.PROPERTY_OWNER);
            // return customer != null;
            
            // For now, return false and handle role assignment manually through admin
            return false;
        } catch (Exception e) {
            System.err.println("❌ Error checking property owner email: " + e.getMessage());
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
}
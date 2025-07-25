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

        HttpSession session = request.getSession();
        boolean previouslyUsedRegularAccount = session.getAttribute("loggedInUserId") != null;
        int userId = (previouslyUsedRegularAccount) ? (int) session.getAttribute("loggedInUserId") : -1;
        User loggedUser = null;
        if (userId != -1) {
            loggedUser = userService.findById(Long.valueOf(userId));
        }
        
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
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

            int currUserId = authenticationUtils.getLoggedInUserId(authentication);
            User user = userService.findById(Long.valueOf(currUserId));
            
            // CRITICAL FIX: If user not found by OAuth method, try finding by email
            if (user == null) {
                System.out.println("🔍 User not found via OAuth method, searching by email: " + email);
                user = userService.findByEmail(email);
                if (user != null) {
                    System.out.println("✅ Found existing user by email: " + user.getId() + " - " + user.getEmail());
                } else {
                    System.out.println("❌ No existing user found by email");
                }
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
                    role = roleService.findByName("ROLE_MANAGER");
                    user.setStatus("ACTIVE");  // FIXED: Use consistent casing
                    userProfile.setStatus("ACTIVE");
                    System.out.println("👑 First user - assigning MANAGER role and ACTIVE status");
                } else {
                    // FIXED: Make all OAuth users active managers for now
                    role = roleService.findByName("ROLE_MANAGER");
                    user.setStatus("ACTIVE");
                    userProfile.setStatus("ACTIVE");
                    System.out.println("👤 Subsequent user - assigning MANAGER role and ACTIVE status");
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
                
                // FIXED: Check if OAuth user exists for this user
                if (user.getOauthUser() != null) {
                    loggedOAuthUser = user.getOauthUser();
                    System.out.println("✅ Using existing OAuth user");
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
}
package site.easy.to.build.crm.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import site.easy.to.build.crm.config.oauth2.CustomOAuth2UserService;
import site.easy.to.build.crm.config.oauth2.OAuthLoginSuccessHandler;
import site.easy.to.build.crm.util.MemoryDiagnostics;

// ADD THESE IMPORTS FOR THE DEBUG FILTER
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Configuration
public class SecurityConfig {

    private final OAuthLoginSuccessHandler oAuth2LoginSuccessHandler;
    private final CustomOAuth2UserService oauthUserService;
    private final CrmUserDetails crmUserDetails;
    private final CustomerUserDetails customerUserDetails;
    private final Environment environment;
    private final CustomerLoginSuccessHandler customerLoginSuccessHandler;
    private final CustomerLoginFailureHandler customerLoginFailureHandler;

    @Autowired
    public SecurityConfig(OAuthLoginSuccessHandler oAuth2LoginSuccessHandler, 
                          CustomOAuth2UserService oauthUserService, 
                          CrmUserDetails crmUserDetails,
                          CustomerUserDetails customerUserDetails, 
                          Environment environment,
                          CustomerLoginSuccessHandler customerLoginSuccessHandler,
                          CustomerLoginFailureHandler customerLoginFailureHandler) {
        MemoryDiagnostics.logMemoryUsage("SecurityConfig Constructor Start");
        
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
        this.oauthUserService = oauthUserService;
        this.crmUserDetails = crmUserDetails;
        this.customerUserDetails = customerUserDetails;
        this.environment = environment;
        this.customerLoginSuccessHandler = customerLoginSuccessHandler;
        this.customerLoginFailureHandler = customerLoginFailureHandler;
    }

    
    // FIXED: Customer security filter chain - ONLY handles customer-specific routes
    @Bean
    @Order(1)
    public SecurityFilterChain customerSecurityFilterChain(HttpSecurity http) throws Exception {
        System.out.println("=== FIXED: Customer security filter chain - NO /portfolio/** interference ===");

        HttpSessionCsrfTokenRepository httpSessionCsrfTokenRepository = new HttpSessionCsrfTokenRepository();
        httpSessionCsrfTokenRepository.setParameterName("csrf");

        http.csrf((csrf) -> csrf
                .csrfTokenRepository(httpSessionCsrfTokenRepository)
                .ignoringRequestMatchers("/api/payprop/**", "/portfolio/**", "/property-owner/files/upload/**", "/customer-login/blocks/api/**")
        );
        
        // CRITICAL FIX: Only match specific customer routes - NO wildcards that could catch /portfolio/**
        http.securityMatcher(
                "/customer-login",
                "/customer-login/**",
                "/customer-logout",
                "/set-password/**",
                "/property-owner/**",
                "/tenant/**",
                "/contractor/**"
                // REMOVED: Any patterns that might interfere with /portfolio/**
            )
            .authorizeHttpRequests((authorize) -> authorize
                    // Login page and processing - public access
                    .requestMatchers("/customer-login").permitAll()
                    .requestMatchers("/set-password/**").permitAll()

                    // Customer portal routes - require authentication
                    .requestMatchers("/customer-login/**").hasAnyAuthority("ROLE_PROPERTY_OWNER", "ROLE_TENANT", "ROLE_CONTRACTOR", "ROLE_DELEGATED_USER", "ROLE_MANAGER", "ROLE_ADMIN")
                    .requestMatchers("/property-owner/**").hasAnyAuthority("ROLE_PROPERTY_OWNER", "ROLE_DELEGATED_USER", "ROLE_MANAGER", "ROLE_ADMIN")
                    .requestMatchers("/tenant/**").hasAuthority("ROLE_TENANT")
                    .requestMatchers("/contractor/**").hasAuthority("ROLE_CONTRACTOR")
                    // REMOVED: debug-sync from here - it belongs in main chain
                    .anyRequest().authenticated()
            )
            .formLogin((form) -> {
                form.loginPage("/customer-login")
                    .loginProcessingUrl("/customer-login")
                    .usernameParameter("username")
                    .passwordParameter("password")
                    .successHandler(customerLoginSuccessHandler)
                    .failureHandler(customerLoginFailureHandler)
                    .permitAll();
            }).authenticationProvider(customerAuthenticationProvider())
            .logout((logout) -> logout
                    .logoutUrl("/customer-logout")
                    .logoutSuccessUrl("/customer-login")
                    .permitAll());

        return http.build();
    }

    // FIXED: Main security filter chain - PROPERLY handles /portfolio/** and PayProp routes
    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        System.out.println("=== FIXED: Main security filter chain - HANDLES /portfolio/** routes ===");
        
        HttpSessionCsrfTokenRepository httpSessionCsrfTokenRepository = new HttpSessionCsrfTokenRepository();
        httpSessionCsrfTokenRepository.setParameterName("csrf");

        http.csrf((csrf) -> csrf
                .csrfTokenRepository(httpSessionCsrfTokenRepository)
                .ignoringRequestMatchers("/api/payprop/**", "/portfolio/**", "/property-owner/files/upload/**")
        );

        // ADD DEBUG FILTER - ENHANCED FOR PAYPROP
        http.addFilterBefore(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
                    throws ServletException, IOException {
                
                String path = request.getServletPath();
                
                // Debug PayProp routes specifically
                if (path.startsWith("/api/payprop/")) {
                    System.out.println("🔍 PAYPROP DEBUG: Processing " + path);
                    System.out.println("🔍 Request method: " + request.getMethod());
                    
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth != null) {
                        System.out.println("🔍 Auth type: " + auth.getClass().getSimpleName());
                        System.out.println("🔍 Auth authorities: " + auth.getAuthorities());
                        System.out.println("🔍 Is authenticated: " + auth.isAuthenticated());
                        System.out.println("🔍 Principal: " + auth.getPrincipal());
                    } else {
                        System.out.println("🔍 No authentication found!");
                    }
                }
                
                filterChain.doFilter(request, response);
            }
        }, UsernamePasswordAuthenticationFilter.class);

        // Add debug logging for security decisions
        http.addFilterBefore((request, response, chain) -> {
            jakarta.servlet.http.HttpServletRequest httpRequest = (jakarta.servlet.http.HttpServletRequest) request;

            // Debug maintenance request CSRF
            if (httpRequest.getRequestURI().contains("/property-owner/maintenance/create")) {
                System.out.println("🎯 CSRF PRE-FILTER DEBUG: " + httpRequest.getMethod() + " " + httpRequest.getRequestURI());
                System.out.println("🎯 CSRF parameter 'csrf': " + httpRequest.getParameter("csrf"));
                System.out.println("🎯 CSRF parameter '_csrf': " + httpRequest.getParameter("_csrf"));
                System.out.println("🎯 Content-Type: " + httpRequest.getContentType());
                System.out.println("🎯 User Principal: " + httpRequest.getUserPrincipal());
                System.out.println("🎯 All parameter names: " + java.util.Collections.list(httpRequest.getParameterNames()));
            }

            if (httpRequest.getRequestURI().contains("/employee/transaction")) {
                System.out.println("🔍 SECURITY DEBUG: " + httpRequest.getMethod() + " " + httpRequest.getRequestURI());
                System.out.println("🔍 User Principal: " + httpRequest.getUserPrincipal());
                System.out.println("🔍 Remote User: " + httpRequest.getRemoteUser());
                if (httpRequest.getUserPrincipal() != null) {
                    System.out.println("🔍 Principal Name: " + httpRequest.getUserPrincipal().getName());
                }
            }
            chain.doFilter(request, response);
        }, UsernamePasswordAuthenticationFilter.class);

        http.authorizeHttpRequests((authorize) -> authorize
                        // Public access routes
                        .requestMatchers("/register/**").permitAll()
                        .requestMatchers("/set-employee-password/**").permitAll()
                        .requestMatchers("/change-password/**").permitAll()
                        .requestMatchers("/login", "/login/**").permitAll()
                        .requestMatchers("/test-password").permitAll()
                        .requestMatchers("/privacy-policy").permitAll()
                        .requestMatchers("/terms-of-service").permitAll()
                        .requestMatchers("/google*.html").permitAll() // Google verification files

                        // Static resources
                        .requestMatchers("/font-awesome/**").permitAll()
                        .requestMatchers("/fonts/**").permitAll()
                        .requestMatchers("/images/**").permitAll()
                        .requestMatchers("/js/**").permitAll()
                        .requestMatchers("/css/**").permitAll()
                        .requestMatchers("/save").permitAll()

                        // Actuator endpoints for health checks (Render needs this)
                        .requestMatchers("/actuator/health", "/hub/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").authenticated()

                        // Debug routes (temporary)
                        .requestMatchers("/debug/**").permitAll()
                        .requestMatchers("/test-password").permitAll()
                        .requestMatchers("/set-test-password").permitAll()

                        // PayProp Raw Import Test Routes (temporary - remove after testing)
                        .requestMatchers("/test/payprop-raw/**").hasAnyAuthority("ROLE_MANAGER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                        .requestMatchers("/debug/payprop-raw/**").permitAll()

                        // CRITICAL: Statement generation routes - MUST be early to avoid conflicts
                        .requestMatchers("/statements/**").hasAnyAuthority("ROLE_MANAGER", "ROLE_EMPLOYEE", "ROLE_PROPERTY_OWNER", "OIDC_USER")

                        // CRITICAL FIX: Transaction import routes - MUST come BEFORE manager and employee patterns
                        .requestMatchers("/employee/transaction/**").hasAnyAuthority("ROLE_MANAGER", "ROLE_EMPLOYEE", "OIDC_USER")

                        // Role-based access - Manager routes (moved after specific transaction routes)
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/**/manager/**")).hasAuthority("ROLE_MANAGER")

                        // CUSTOMER MANAGEMENT FIX: Specific employee customer routes FIRST
                        .requestMatchers("/employee/customer/add-customer").hasAnyAuthority("ROLE_MANAGER", "ROLE_EMPLOYEE", "ROLE_SUPER_ADMIN", "OIDC_USER")
                        .requestMatchers("/employee/customer/**").hasAnyAuthority("ROLE_MANAGER", "ROLE_EMPLOYEE", "ROLE_SUPER_ADMIN", "OIDC_USER")

                        // PROPERTY OWNER ACCESS FIX: Allow property owners to access admin property pages
                        .requestMatchers("/employee/property/**").hasAnyAuthority("ROLE_MANAGER", "ROLE_EMPLOYEE", "ROLE_PROPERTY_OWNER", "OIDC_USER")

                        // EMPLOYEE FILES: Document management for employees
                        .requestMatchers("/employee/files/**").hasAnyAuthority("ROLE_MANAGER", "ROLE_EMPLOYEE", "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "OIDC_USER")

                        // Employee and Manager routes (general - comes AFTER specific)
                        .requestMatchers("/employee/**").hasAnyAuthority("ROLE_MANAGER", "ROLE_EMPLOYEE", "OIDC_USER")

                        // Customer routes (handled by main chain for consistency)
                        .requestMatchers("/customer/**").hasRole("CUSTOMER")

                        // CRITICAL FIX: PayProp routes - MUST come BEFORE /portfolio/**
                        .requestMatchers("/api/test/**").permitAll() // Allow test endpoints without auth for debugging
                        .requestMatchers("/api/payprop/sync/**").permitAll() // Allow sync endpoints without auth
                        .requestMatchers("/admin/payprop/**").hasAnyRole("MANAGER", "OIDC_USER")
                        .requestMatchers("/payprop/**").hasAnyRole("MANAGER", "ADMIN", "SUPER_ADMIN", "OIDC_USER") // PayProp import pages
                        .requestMatchers("/api/payprop/oauth/**").hasAnyRole("MANAGER", "OIDC_USER")
                        .requestMatchers("/api/payprop/webhook/**").permitAll() // Webhooks need public access
                        .requestMatchers("/api/payprop/**").hasAnyRole("MANAGER", "EMPLOYEE", "OIDC_USER")

                        // CRITICAL FIX: Portfolio specific routes - MUST come BEFORE general /portfolio/**
                        .requestMatchers("/portfolio/actions/pull-payprop-tags").hasAnyAuthority("OIDC_USER", "ROLE_MANAGER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_EMPLOYEE")
                        .requestMatchers("/portfolio/payprop-tags").hasAnyAuthority("OIDC_USER", "ROLE_MANAGER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_PROPERTY_OWNER")
                        .requestMatchers("/portfolio/adopt-payprop-tag").hasAnyAuthority("OIDC_USER", "ROLE_MANAGER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_PROPERTY_OWNER") 
                        .requestMatchers("/portfolio/sync-all").hasAnyAuthority("OIDC_USER", "ROLE_MANAGER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                        .requestMatchers("/portfolio/bulk-assign").hasAnyAuthority("OIDC_USER", "ROLE_MANAGER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_EMPLOYEE")
                        .requestMatchers("/portfolio/create").hasAnyAuthority("OIDC_USER", "ROLE_MANAGER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_EMPLOYEE", "ROLE_PROPERTY_OWNER", "ROLE_CUSTOMER")
                        .requestMatchers("/portfolio/all").hasAnyAuthority("OIDC_USER", "ROLE_MANAGER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_EMPLOYEE")
                        .requestMatchers("/portfolio/assign-properties").hasAnyAuthority("OIDC_USER", "ROLE_MANAGER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_EMPLOYEE", "ROLE_PROPERTY_OWNER", "ROLE_CUSTOMER")
                        .requestMatchers("/portfolio/dashboard").hasAnyAuthority("OIDC_USER", "ROLE_MANAGER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_EMPLOYEE", "ROLE_PROPERTY_OWNER", "ROLE_CUSTOMER")
                        .requestMatchers("/portfolio/test/**").permitAll() // Allow test routes
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/portfolio/*/debug-sync")).hasAnyAuthority("OIDC_USER", "ROLE_MANAGER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_EMPLOYEE")
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/portfolio/*/sync")).hasAnyAuthority("OIDC_USER", "ROLE_MANAGER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_EMPLOYEE")
                        
                        // FIXED: Add specific assign-properties-v2 route with correct authorities including customers
                        .requestMatchers(HttpMethod.POST, "/portfolio/*/assign-properties-v2").hasAnyAuthority("OIDC_USER", "ROLE_MANAGER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_PROPERTY_OWNER", "ROLE_CUSTOMER")
                        
                        // CRITICAL FIX: General portfolio routes - NOW properly handled by main chain
                        .requestMatchers("/portfolio/**").hasAnyAuthority("OIDC_USER", "ROLE_MANAGER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_EMPLOYEE", "ROLE_PROPERTY_OWNER", "ROLE_CUSTOMER")

                        
                        // Property API routes (for address copy functionality)
                        .requestMatchers("/employee/property/api/**").hasAnyRole("MANAGER", "EMPLOYEE", "PROPERTY_OWNER", "OIDC_USER")
                        // Property routes
                        .requestMatchers("/property/**").hasAnyRole("MANAGER", "EMPLOYEE", "PROPERTY_OWNER", "OIDC_USER")

                        // Default - require authentication
                        .anyRequest().authenticated()
                )
                .formLogin((form) -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error=true")
                        .permitAll()
                ).authenticationProvider(crmAuthenticationProvider())
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oauthUserService))
                        .successHandler(oAuth2LoginSuccessHandler)
                ).logout((logout) -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login")
                        .permitAll())
                .exceptionHandling(exception -> {
                    exception.accessDeniedHandler(accessDeniedHandler());
                });
    
        return http.build();
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return new CustomAccessDeniedHandler();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider customerAuthenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customerUserDetails);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public DaoAuthenticationProvider crmAuthenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(crmUserDetails);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }
}
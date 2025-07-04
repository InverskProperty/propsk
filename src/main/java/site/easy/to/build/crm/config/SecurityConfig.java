package site.easy.to.build.crm.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import site.easy.to.build.crm.config.oauth2.CustomOAuth2UserService;
import site.easy.to.build.crm.config.oauth2.OAuthLoginSuccessHandler;

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
                    .requestMatchers("/customer-login", "/customer-login/**").permitAll()
                    .requestMatchers("/set-password/**").permitAll()
                    .requestMatchers("/property-owner/**").hasRole("PROPERTY_OWNER")
                    .requestMatchers("/tenant/**").hasRole("TENANT")
                    .requestMatchers("/contractor/**").hasRole("CONTRACTOR")
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
            }).userDetailsService(customerUserDetails)
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

        http.authorizeHttpRequests((authorize) -> authorize
                        // Public access routes
                        .requestMatchers("/register/**").permitAll()
                        .requestMatchers("/set-employee-password/**").permitAll()
                        .requestMatchers("/change-password/**").permitAll()
                        .requestMatchers("/login", "/login/**").permitAll() 
                        .requestMatchers("/test-password").permitAll()
                        
                        // Static resources
                        .requestMatchers("/font-awesome/**").permitAll()
                        .requestMatchers("/fonts/**").permitAll()
                        .requestMatchers("/images/**").permitAll()
                        .requestMatchers("/js/**").permitAll()
                        .requestMatchers("/css/**").permitAll()
                        .requestMatchers("/save").permitAll()
                        
                        // Debug routes (temporary)
                        .requestMatchers("/debug/**").permitAll()
                        
                        // Role-based access - Manager routes
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/**/manager/**")).hasRole("MANAGER")
                        
                        // CUSTOMER MANAGEMENT FIX: Specific employee customer routes FIRST
                        .requestMatchers("/employee/customer/add-customer").hasAnyRole("MANAGER", "EMPLOYEE", "OIDC_USER")
                        .requestMatchers("/employee/customer/**").hasAnyRole("MANAGER", "EMPLOYEE", "OIDC_USER")

                        // Employee and Manager routes (general - comes AFTER specific)
                        .requestMatchers("/employee/**").hasAnyRole("MANAGER", "EMPLOYEE", "OIDC_USER")

                        // Customer routes (handled by main chain for consistency)
                        .requestMatchers("/customer/**").hasRole("CUSTOMER")
                        
                        // CRITICAL FIX: PayProp routes - MUST come BEFORE /portfolio/**
                        .requestMatchers("/admin/payprop/**").hasAnyRole("MANAGER", "OIDC_USER")
                        .requestMatchers("/api/payprop/oauth/**").hasAnyRole("MANAGER", "OIDC_USER")
                        .requestMatchers("/api/payprop/webhook/**").permitAll() // Webhooks need public access
                        .requestMatchers("/api/payprop/**").hasAnyRole("MANAGER", "EMPLOYEE", "OIDC_USER")
                        
                        // CRITICAL FIX: Portfolio specific routes - MUST come BEFORE general /portfolio/**
                        .requestMatchers("/portfolio/actions/pull-payprop-tags").hasAnyRole("MANAGER", "EMPLOYEE", "OIDC_USER")
                        .requestMatchers("/portfolio/payprop-tags").hasAnyRole("MANAGER", "PROPERTY_OWNER", "OIDC_USER")
                        .requestMatchers("/portfolio/adopt-payprop-tag").hasAnyRole("MANAGER", "PROPERTY_OWNER", "OIDC_USER") 
                        .requestMatchers("/portfolio/sync-all").hasAnyRole("MANAGER", "OIDC_USER")
                        .requestMatchers("/portfolio/bulk-assign").hasAnyRole("MANAGER", "EMPLOYEE", "OIDC_USER")
                        .requestMatchers("/portfolio/create").hasAnyRole("MANAGER", "EMPLOYEE", "PROPERTY_OWNER", "CUSTOMER", "OIDC_USER")
                        .requestMatchers("/portfolio/all").hasAnyRole("MANAGER", "EMPLOYEE", "OIDC_USER")
                        .requestMatchers("/portfolio/assign-properties").hasAnyRole("MANAGER", "EMPLOYEE", "OIDC_USER")
                        .requestMatchers("/portfolio/dashboard").hasAnyRole("MANAGER", "EMPLOYEE", "PROPERTY_OWNER", "CUSTOMER", "OIDC_USER")
                        .requestMatchers("/portfolio/test/**").permitAll() // Allow test routes
                        
                        // CRITICAL FIX: General portfolio routes - NOW properly handled by main chain
                        .requestMatchers("/portfolio/**").hasAnyRole("MANAGER", "EMPLOYEE", "PROPERTY_OWNER", "CUSTOMER", "OIDC_USER")
                        
                        // Property API routes (for address copy functionality)
                        .requestMatchers("/employee/property/api/**").hasAnyRole("MANAGER", "EMPLOYEE", "OIDC_USER")
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
                ).userDetailsService(crmUserDetails)
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
}
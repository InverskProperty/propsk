
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

    // Customer security filter chain - handles customer-specific routes ONLY
    @Bean
    @Order(1) 
    public SecurityFilterChain customerSecurityFilterChain(HttpSecurity http) throws Exception {
        System.out.println("=== DEBUG: Configuring customer security filter chain ===");
    
        HttpSessionCsrfTokenRepository httpSessionCsrfTokenRepository = new HttpSessionCsrfTokenRepository();
        httpSessionCsrfTokenRepository.setParameterName("csrf");

        http.csrf((csrf) -> csrf
                .csrfTokenRepository(httpSessionCsrfTokenRepository)
        );

        // FIXED: Removed /portfolio/** from customer security matcher
        // This allows /portfolio/** routes to be handled by the main security filter chain
        http.securityMatcher("/customer-login/**", "/customer-logout", "/set-password/**", 
                "/property-owner/**", "/tenant/**", "/contractor/**")
                .authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers("/customer-login/**").permitAll()
                        .requestMatchers("/set-password/**").permitAll()
                        .requestMatchers("/property-owner/**").hasRole("PROPERTY_OWNER")
                        .requestMatchers("/tenant/**").hasRole("TENANT")
                        .requestMatchers("/contractor/**").hasRole("CONTRACTOR")
                        // REMOVED: .requestMatchers("/portfolio/**").hasAnyRole("CUSTOMER", "MANAGER", "EMPLOYEE", "PROPERTY_OWNER")
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

    // Main security filter chain - handles employee/admin routes INCLUDING /portfolio/**
    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        HttpSessionCsrfTokenRepository httpSessionCsrfTokenRepository = new HttpSessionCsrfTokenRepository();
        httpSessionCsrfTokenRepository.setParameterName("csrf");

        http.csrf((csrf) -> csrf
                .csrfTokenRepository(httpSessionCsrfTokenRepository)
        );

        http.authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers("/register/**").permitAll()
                        .requestMatchers("/set-employee-password/**").permitAll()
                        .requestMatchers("/change-password/**").permitAll()
                        .requestMatchers("/login/**").permitAll() // Make sure login page is accessible
                        .requestMatchers("/test-password").permitAll() // Allow test password URL
                        .requestMatchers("/font-awesome/**").permitAll()
                        .requestMatchers("/fonts/**").permitAll()
                        .requestMatchers("/images/**").permitAll()
                        .requestMatchers("/save").permitAll()
                        .requestMatchers("/js/**").permitAll()
                        .requestMatchers("/css/**").permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/**/manager/**")).hasRole("MANAGER")
                        .requestMatchers("/employee/**").hasAnyRole("MANAGER", "EMPLOYEE")
                        .requestMatchers("/customer/**").hasRole("CUSTOMER")
                        // FIXED: Add missing admin/payprop routes for PayProp integration
                        .requestMatchers("/admin/payprop/**").hasRole("MANAGER")
                        .requestMatchers("/api/payprop/**").hasAnyRole("MANAGER", "EMPLOYEE")
                        // Portfolio access for employees and managers (now properly handled here)
                        .requestMatchers("/portfolio/**").hasAnyRole("MANAGER", "EMPLOYEE", "PROPERTY_OWNER", "CUSTOMER")
                        .requestMatchers("/property/**").hasAnyRole("MANAGER", "EMPLOYEE", "PROPERTY_OWNER")
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
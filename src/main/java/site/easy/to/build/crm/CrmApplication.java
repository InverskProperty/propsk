// CrmApplication.java - Updated Main Application Class with Scheduling
package site.easy.to.build.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Autowired;
import site.easy.to.build.crm.google.service.GoogleTokenRefreshScheduler;

@SpringBootApplication
@EnableScheduling  // CRITICAL: Enable scheduled tasks for token refresh
public class CrmApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(CrmApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(CrmApplication.class, args);
    }
    
    /**
     * Run startup checks and fixes
     */
    @Bean
    CommandLineRunner init(@Autowired(required = false) GoogleTokenRefreshScheduler tokenRefreshScheduler) {
        return args -> {
            System.out.println("========================================");
            System.out.println("🚀 CRM Application Started Successfully");
            System.out.println("========================================");
            
            // Check Google token status on startup
            if (tokenRefreshScheduler != null) {
                System.out.println("🔍 Checking Google OAuth token status...");
                tokenRefreshScheduler.checkTokenStatus();
                
                // Refresh any expiring tokens immediately
                System.out.println("🔄 Refreshing expiring tokens...");
                tokenRefreshScheduler.refreshExpiringTokens();
            }
            
            System.out.println("✅ Startup checks completed");
            System.out.println("========================================");
        };
    }
}
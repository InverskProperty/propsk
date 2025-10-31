// CrmApplication.java - Updated Main Application Class with Scheduling
package site.easy.to.build.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Autowired;
import site.easy.to.build.crm.google.service.GoogleTokenRefreshScheduler;
import site.easy.to.build.crm.util.MemoryDiagnostics;

@SpringBootApplication
@EnableScheduling  // CRITICAL: Enable scheduled tasks for token refresh
@EnableAsync      // CRITICAL: Enable async event listeners for unified dataset rebuild
public class CrmApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(CrmApplication.class);
    }

    public static void main(String[] args) {
        MemoryDiagnostics.logMemoryUsage("Application Startup - Before SpringApplication.run");
        
        SpringApplication.run(CrmApplication.class, args);
        
        MemoryDiagnostics.logMemoryUsage("Application Startup - After SpringApplication.run");
    }
    
    /**
     * Run startup checks and fixes
     */
    @Bean
    CommandLineRunner init(@Autowired(required = false) GoogleTokenRefreshScheduler tokenRefreshScheduler) {
        return args -> {
            MemoryDiagnostics.logMemoryUsage("CommandLineRunner init - Start");
            
            System.out.println("========================================");
            System.out.println("🚀 CRM Application Started Successfully");
            System.out.println("========================================");
            
            MemoryDiagnostics.logMemoryUsage("Before Google Token Status Check");
            
            // Check Google token status on startup
            if (tokenRefreshScheduler != null) {
                System.out.println("🔍 Checking Google OAuth token status...");
                tokenRefreshScheduler.checkTokenStatus();
                
                MemoryDiagnostics.logMemoryUsage("After Google Token Status Check");
                
                // Refresh any expiring tokens immediately
                System.out.println("🔄 Refreshing expiring tokens...");
                tokenRefreshScheduler.refreshExpiringTokens();
                
                MemoryDiagnostics.logMemoryUsage("After Google Token Refresh");
            }
            
            System.out.println("✅ Startup checks completed");
            System.out.println("========================================");
            
            MemoryDiagnostics.forceGCAndLog("Startup Complete - Final Memory Check");
        };
    }
}
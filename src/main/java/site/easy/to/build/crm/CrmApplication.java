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

import site.easy.to.build.crm.service.transaction.UnifiedTransactionRebuildService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

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
    CommandLineRunner init(@Autowired(required = false) GoogleTokenRefreshScheduler tokenRefreshScheduler,
                           @Autowired DataSource dataSource,
                           @Autowired UnifiedTransactionRebuildService rebuildService) {
        return args -> {
            MemoryDiagnostics.logMemoryUsage("CommandLineRunner init - Start");

            System.out.println("========================================");
            System.out.println("CRM Application Started Successfully");
            System.out.println("========================================");

            // DIAGNOSTIC: Log which database we're connected to
            try (Connection conn = dataSource.getConnection()) {
                String dbUrl = conn.getMetaData().getURL();
                System.out.println("DIAGNOSTIC: Connected to database URL: " + dbUrl);
                System.out.println("DIAGNOSTIC: Database product: " + conn.getMetaData().getDatabaseProductName() + " " + conn.getMetaData().getDatabaseProductVersion());

                try (Statement stmt = conn.createStatement()) {
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as cnt FROM customer_login_info");
                    if (rs.next()) {
                        System.out.println("DIAGNOSTIC: customer_login_info row count: " + rs.getInt("cnt"));
                    }
                    rs.close();

                    rs = stmt.executeQuery("SELECT id, username FROM customer_login_info LIMIT 10");
                    while (rs.next()) {
                        System.out.println("DIAGNOSTIC: customer_login_info row - id=" + rs.getInt("id") + ", username=" + rs.getString("username"));
                    }
                    rs.close();
                }
            } catch (Exception e) {
                System.out.println("DIAGNOSTIC ERROR: " + e.getMessage());
            }

            // ONE-TIME: Rebuild unified_allocations (table is empty, causing zero rent in Option C statements)
            System.out.println("========================================");
            System.out.println("REBUILDING unified_transactions + unified_allocations...");
            System.out.println("========================================");
            try {
                java.util.Map<String, Object> rebuildResult = rebuildService.rebuildComplete();
                System.out.println("REBUILD RESULT: " + rebuildResult);
                System.out.println("REBUILD STATUS: " + rebuildResult.get("status"));
                System.out.println("Historical records: " + rebuildResult.get("historicalRecordsInserted"));
                System.out.println("PayProp records: " + rebuildResult.get("paypropRecordsInserted"));
                System.out.println("Synced allocations: " + rebuildResult.get("syncedAllocations"));
            } catch (Exception e) {
                System.out.println("REBUILD FAILED: " + e.getMessage());
                e.printStackTrace();
            }
            System.out.println("========================================");

            MemoryDiagnostics.logMemoryUsage("Before Google Token Status Check");

            // Check Google token status on startup
            if (tokenRefreshScheduler != null) {
                System.out.println("Checking Google OAuth token status...");
                tokenRefreshScheduler.checkTokenStatus();

                MemoryDiagnostics.logMemoryUsage("After Google Token Status Check");

                // Refresh any expiring tokens immediately
                System.out.println("Refreshing expiring tokens...");
                tokenRefreshScheduler.refreshExpiringTokens();

                MemoryDiagnostics.logMemoryUsage("After Google Token Refresh");
            }

            System.out.println("Startup checks completed");
            System.out.println("========================================");

            MemoryDiagnostics.forceGCAndLog("Startup Complete - Final Memory Check");
        };
    }
}
// PortfolioAnalyticsScheduler.java - Automatically calculates portfolio analytics
package site.easy.to.build.crm.scheduler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.easy.to.build.crm.entity.Portfolio;
import site.easy.to.build.crm.service.portfolio.PortfolioService;

import java.time.LocalDate;
import java.util.List;

@Component
public class PortfolioAnalyticsScheduler {

    private final PortfolioService portfolioService;

    @Autowired
    public PortfolioAnalyticsScheduler(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    /**
     * Calculate analytics for all active portfolios daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void calculateDailyPortfolioAnalytics() {
        try {
            System.out.println("Starting daily portfolio analytics calculation...");
            
            List<Portfolio> activePortfolios = portfolioService.findAll();
            LocalDate today = LocalDate.now();
            
            int successCount = 0;
            int errorCount = 0;
            
            for (Portfolio portfolio : activePortfolios) {
                try {
                    portfolioService.calculatePortfolioAnalytics(portfolio.getId(), today);
                    successCount++;
                } catch (Exception e) {
                    System.err.println("Failed to calculate analytics for portfolio " + 
                        portfolio.getId() + ": " + e.getMessage());
                    errorCount++;
                }
            }
            
            System.out.println("Portfolio analytics calculation completed. Success: " + 
                successCount + ", Errors: " + errorCount);
                
        } catch (Exception e) {
            System.err.println("Error in portfolio analytics scheduler: " + e.getMessage());
        }
    }

    /**
     * Sync portfolios with PayProp every hour (if auto-sync is enabled)
     */
    @Scheduled(fixedRateString = "${portfolio.auto-sync.interval:3600000}")
    public void autoSyncPortfolios() {
        try {
            List<Portfolio> portfoliosNeedingSync = portfolioService.findPortfoliosNeedingSync();
            
            if (!portfoliosNeedingSync.isEmpty()) {
                System.out.println("Auto-syncing " + portfoliosNeedingSync.size() + " portfolios with PayProp...");
                portfolioService.syncAllPortfoliosWithPayProp(1L); // System user
            }
            
        } catch (Exception e) {
            System.err.println("Error in portfolio auto-sync: " + e.getMessage());
        }
    }
}
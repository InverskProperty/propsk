// PayPropSyncScheduler.java
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(name = "payprop.scheduler.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropSyncScheduler {

    private final PayPropSyncOrchestrator syncOrchestrator;
    private final PayPropOAuth2Service oAuth2Service;

    @Autowired
    public PayPropSyncScheduler(PayPropSyncOrchestrator syncOrchestrator,
                               PayPropOAuth2Service oAuth2Service) {
        this.syncOrchestrator = syncOrchestrator;
        this.oAuth2Service = oAuth2Service;
    }

    /**
     * Scheduled full sync (daily at 2 AM)
     */
    @Scheduled(cron = "${payprop.scheduler.full-sync-cron:0 0 2 * * ?}")
    public void performScheduledFullSync() {
        if (!oAuth2Service.hasValidTokens()) {
            System.out.println("⏰ Skipping scheduled sync - PayProp not authorized");
            return;
        }

        System.out.println("⏰ Starting scheduled full sync...");
        try {
            syncOrchestrator.performFullSync(1L); // System user
            System.out.println("✅ Scheduled full sync completed successfully");
        } catch (Exception e) {
            System.err.println("❌ Scheduled full sync failed: " + e.getMessage());
        }
    }

    /**
     * Scheduled intelligent sync (every 30 minutes)  
     */
    @Scheduled(cron = "${payprop.scheduler.intelligent-sync-cron:0 */30 * * * ?}")
    public void performScheduledIntelligentSync() {
        if (!oAuth2Service.hasValidTokens()) {
            return; // Skip silently for frequent sync
        }

        try {
            syncOrchestrator.performIntelligentSync(1L); // System user
        } catch (Exception e) {
            System.err.println("❌ Scheduled intelligent sync failed: " + e.getMessage());
        }
    }
}
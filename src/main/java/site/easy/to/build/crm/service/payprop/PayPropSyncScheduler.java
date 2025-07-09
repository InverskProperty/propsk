// PayPropSyncScheduler.java - FIXED
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.service.user.OAuthUserService;

// TEMPORARILY DISABLED: Scheduler needs proper OAuth user management
// @ConditionalOnProperty(name = "payprop.scheduler.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropSyncScheduler {

    private final PayPropSyncOrchestrator syncOrchestrator;
    private final PayPropOAuth2Service oAuth2Service;
    private final OAuthUserService oAuthUserService;

    @Autowired
    public PayPropSyncScheduler(PayPropSyncOrchestrator syncOrchestrator,
                               PayPropOAuth2Service oAuth2Service,
                               OAuthUserService oAuthUserService) {
        this.syncOrchestrator = syncOrchestrator;
        this.oAuth2Service = oAuth2Service;
        this.oAuthUserService = oAuthUserService;
    }

    /**
     * TEMPORARILY DISABLED: Scheduled full sync (daily at 2 AM)
     * 
     * The scheduler is disabled because it needs proper OAuth user management.
     * To enable scheduling:
     * 1. Implement system user OAuth token management
     * 2. Add payprop.scheduler.enabled=true to application.properties
     * 3. Uncomment the @Scheduled annotations below
     */
    // @Scheduled(cron = "${payprop.scheduler.full-sync-cron:0 0 2 * * ?}")
    public void performScheduledFullSync() {
        if (!oAuth2Service.hasValidTokens()) {
            System.out.println("⏰ Skipping scheduled sync - PayProp not authorized");
            return;
        }

        System.out.println("⏰ Starting scheduled full sync...");
        try {
            // TODO: Implement system user OAuth token retrieval
            // OAuthUser systemUser = getSystemOAuthUser();
            // syncOrchestrator.performUnifiedSync(systemUser, 1L);
            System.out.println("⚠️ Scheduled sync disabled - requires OAuth user implementation");
        } catch (Exception e) {
            System.err.println("❌ Scheduled full sync failed: " + e.getMessage());
        }
    }

    /**
     * TEMPORARILY DISABLED: Scheduled intelligent sync (every 30 minutes)
     */
    // @Scheduled(cron = "${payprop.scheduler.intelligent-sync-cron:0 */30 * * * ?}")
    public void performScheduledIntelligentSync() {
        if (!oAuth2Service.hasValidTokens()) {
            return; // Skip silently for frequent sync
        }

        try {
            // TODO: Implement system user OAuth token retrieval
            // OAuthUser systemUser = getSystemOAuthUser();
            // syncOrchestrator.performUnifiedSync(systemUser, 1L);
            System.out.println("⚠️ Scheduled intelligent sync disabled - requires OAuth user implementation");
        } catch (Exception e) {
            System.err.println("❌ Scheduled intelligent sync failed: " + e.getMessage());
        }
    }

    /**
     * Manual sync trigger for testing (can be called from controllers)
     * This method can be used when you have an OAuth user available
     */
    public void performManualSync(OAuthUser oAuthUser, Long userId) {
        if (!oAuth2Service.hasValidTokens()) {
            throw new IllegalStateException("PayProp not authorized");
        }

        try {
            // FIXED: Call with correct signature (OAuthUser, Long)
            syncOrchestrator.performUnifiedSync(oAuthUser, userId);
            System.out.println("✅ Manual sync completed successfully");
        } catch (Exception e) {
            System.err.println("❌ Manual sync failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * TODO: Implement this method to get a system OAuth user for scheduling
     * 
     * This would need to:
     * 1. Store system-level OAuth tokens in database
     * 2. Refresh tokens as needed
     * 3. Return a valid OAuthUser for system operations
     */
    private OAuthUser getSystemOAuthUser() {
        // Implementation needed:
        // 1. Get stored system OAuth tokens from database
        // 2. Refresh if needed using oAuthUserService
        // 3. Return valid OAuthUser
        
        throw new UnsupportedOperationException(
            "System OAuth user not implemented. " +
            "Scheduled sync requires system-level OAuth token management."
        );
    }
}
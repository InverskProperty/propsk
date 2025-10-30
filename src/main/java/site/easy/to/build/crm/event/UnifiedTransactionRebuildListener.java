package site.easy.to.build.crm.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import site.easy.to.build.crm.service.transaction.UnifiedTransactionRebuildService;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Event listener that automatically triggers unified_transactions rebuild
 * when historical data is imported or PayProp data is synced.
 *
 * This ensures the unified view stays synchronized with source tables.
 */
@Component
public class UnifiedTransactionRebuildListener {

    private static final Logger log = LoggerFactory.getLogger(UnifiedTransactionRebuildListener.class);

    @Autowired
    private UnifiedTransactionRebuildService rebuildService;

    /**
     * Listen for historical data import events and trigger incremental rebuild.
     * Uses incremental rebuild to only process recent changes for performance.
     */
    @EventListener
    @Async
    public void handleHistoricalDataImported(HistoricalDataImportedEvent event) {
        log.info("📥 Historical data import detected: {} records from {}",
                event.getRecordsImported(), event.getDataSource());

        try {
            // Trigger incremental rebuild starting from 5 minutes before import
            // (buffer to catch any edge cases with timestamps)
            LocalDateTime since = event.getImportTime().minusMinutes(5);

            log.info("🔄 Triggering automatic unified_transactions incremental rebuild (since: {})", since);
            Map<String, Object> result = rebuildService.rebuildIncremental(since);

            log.info("✅ Automatic unified rebuild completed after historical import: {} records processed",
                    result.get("recordsProcessed"));

        } catch (Exception e) {
            log.error("❌ Failed to auto-rebuild unified_transactions after historical import", e);
            // Don't throw - we don't want to fail the import if rebuild fails
        }
    }

    /**
     * Listen for PayProp sync events and trigger incremental rebuild.
     * Only rebuilds if sync was successful.
     */
    @EventListener
    @Async
    public void handlePayPropDataSynced(PayPropDataSyncedEvent event) {
        if (!event.isSuccess()) {
            log.warn("⚠️  PayProp sync was not successful, skipping unified rebuild");
            return;
        }

        log.info("📥 PayProp data sync detected: {} - {} records",
                event.getSyncType(), event.getRecordsProcessed());

        try {
            // Trigger incremental rebuild starting from 5 minutes before sync
            LocalDateTime since = event.getSyncTime().minusMinutes(5);

            log.info("🔄 Triggering automatic unified_transactions incremental rebuild (since: {})", since);
            Map<String, Object> result = rebuildService.rebuildIncremental(since);

            log.info("✅ Automatic unified rebuild completed after PayProp sync: {} records processed",
                    result.get("recordsProcessed"));

        } catch (Exception e) {
            log.error("❌ Failed to auto-rebuild unified_transactions after PayProp sync", e);
            // Don't throw - we don't want to fail the sync if rebuild fails
        }
    }
}

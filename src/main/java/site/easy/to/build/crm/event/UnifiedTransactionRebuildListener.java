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
     * Listen for PayProp sync events and trigger rebuild.
     * Only rebuilds if sync was successful.
     *
     * For comprehensive/full syncs, triggers FULL rebuild to ensure all historical
     * data is included (e.g., newly linked invoices, manual corrections).
     * For incremental syncs, triggers incremental rebuild for performance.
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
            // Check if this is a comprehensive sync that should trigger full rebuild
            String syncType = event.getSyncType();
            boolean isComprehensiveSync = syncType != null &&
                (syncType.contains("COMPREHENSIVE") ||
                 syncType.contains("FULL") ||
                 syncType.contains("COMPLETE"));

            if (isComprehensiveSync) {
                // Trigger FULL rebuild for comprehensive syncs
                // This ensures historical data changes (like newly linked invoices) are included
                log.info("🔄 Triggering automatic FULL unified_transactions rebuild (comprehensive sync detected)");
                Map<String, Object> result = rebuildService.rebuildComplete();

                int totalRecords = (int) result.getOrDefault("historicalRecordsInserted", 0) +
                                   (int) result.getOrDefault("paypropRecordsInserted", 0);
                log.info("✅ Automatic FULL unified rebuild completed after PayProp sync: {} total records, duration: {}s",
                        totalRecords, result.get("durationSeconds"));
            } else {
                // Trigger incremental rebuild for partial syncs
                LocalDateTime since = event.getSyncTime().minusMinutes(5);

                log.info("🔄 Triggering automatic unified_transactions incremental rebuild (since: {})", since);
                Map<String, Object> result = rebuildService.rebuildIncremental(since);

                log.info("✅ Automatic incremental rebuild completed after PayProp sync: {} records processed",
                        result.get("recordsProcessed"));
            }

        } catch (Exception e) {
            log.error("❌ Failed to auto-rebuild unified_transactions after PayProp sync", e);
            // Don't throw - we don't want to fail the sync if rebuild fails
        }
    }
}

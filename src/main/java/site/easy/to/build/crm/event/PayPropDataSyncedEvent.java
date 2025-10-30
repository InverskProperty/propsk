package site.easy.to.build.crm.event;

import org.springframework.context.ApplicationEvent;
import java.time.LocalDateTime;

/**
 * Event fired when PayProp data synchronization has completed.
 * Triggers automatic rebuild of unified_transactions table.
 */
public class PayPropDataSyncedEvent extends ApplicationEvent {

    private final int recordsProcessed;
    private final LocalDateTime syncTime;
    private final String syncType;
    private final boolean success;

    public PayPropDataSyncedEvent(Object source, int recordsProcessed, LocalDateTime syncTime, String syncType, boolean success) {
        super(source);
        this.recordsProcessed = recordsProcessed;
        this.syncTime = syncTime;
        this.syncType = syncType;
        this.success = success;
    }

    public int getRecordsProcessed() {
        return recordsProcessed;
    }

    public LocalDateTime getSyncTime() {
        return syncTime;
    }

    public String getSyncType() {
        return syncType;
    }

    public boolean isSuccess() {
        return success;
    }

    @Override
    public String toString() {
        return String.format("PayPropDataSyncedEvent[type=%s, records=%d, time=%s, success=%b]",
                syncType, recordsProcessed, syncTime, success);
    }
}

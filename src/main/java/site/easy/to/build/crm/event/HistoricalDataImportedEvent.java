package site.easy.to.build.crm.event;

import org.springframework.context.ApplicationEvent;
import java.time.LocalDateTime;

/**
 * Event fired when historical transaction data has been imported.
 * Triggers automatic rebuild of unified_transactions table.
 */
public class HistoricalDataImportedEvent extends ApplicationEvent {

    private final int recordsImported;
    private final LocalDateTime importTime;
    private final String dataSource;

    public HistoricalDataImportedEvent(Object source, int recordsImported, LocalDateTime importTime, String dataSource) {
        super(source);
        this.recordsImported = recordsImported;
        this.importTime = importTime;
        this.dataSource = dataSource;
    }

    public int getRecordsImported() {
        return recordsImported;
    }

    public LocalDateTime getImportTime() {
        return importTime;
    }

    public String getDataSource() {
        return dataSource;
    }

    @Override
    public String toString() {
        return String.format("HistoricalDataImportedEvent[source=%s, records=%d, time=%s]",
                dataSource, recordsImported, importTime);
    }
}

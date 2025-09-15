package site.easy.to.build.crm.entity;

/**
 * Enum to distinguish between PayProp-synced data and manually uploaded historical data
 * Ensures both data sources are treated equally in relationship mapping and business logic
 */
public enum DataSource {
    PAYPROP("PayProp API Sync"),
    UPLOADED("Historical Upload"),
    MANUAL("Manual Entry");

    private final String description;

    DataSource(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPayProp() {
        return this == PAYPROP;
    }

    public boolean isUploaded() {
        return this == UPLOADED;
    }

    public boolean isManual() {
        return this == MANUAL;
    }
}
package site.easy.to.build.crm.enums;

/**
 * Enum representing different payment account sources for statement generation
 * SIMPLIFIED: Only 3 options - Unified (recommended), Historical, PayProp
 */
public enum StatementDataSource {

    UNIFIED("Unified (Recommended)", "UNIFIED", "Combined Historical + PayProp data (last 2 years)", "unified"),
    HISTORICAL("Historical Only", "HISTORICAL", "Historical transactions only (pre-PayProp era)", "historical"),
    PAYPROP("PayProp Only", "PAYPROP", "PayProp transactions only (current)", "payprop");

    private final String displayName;
    private final String dataSourceKey;
    private final String description;
    private final String accountSourceValue; // Maps to account_source field in database

    StatementDataSource(String displayName, String dataSourceKey, String description, String accountSourceValue) {
        this.displayName = displayName;
        this.dataSourceKey = dataSourceKey;
        this.description = description;
        this.accountSourceValue = accountSourceValue;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDataSourceKey() {
        return dataSourceKey;
    }

    public String getDescription() {
        return description;
    }

    public String getAccountSourceValue() {
        return accountSourceValue;
    }

    /**
     * Check if this is unified source (combines both Historical + PayProp)
     */
    public boolean isUnified() {
        return this == UNIFIED;
    }

    /**
     * Check if this is historical data source only
     */
    public boolean isHistorical() {
        return this == HISTORICAL;
    }

    /**
     * Check if this is PayProp data source only
     */
    public boolean isPayProp() {
        return this == PAYPROP;
    }
}
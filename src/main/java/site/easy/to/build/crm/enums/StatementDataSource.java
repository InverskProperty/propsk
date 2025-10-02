package site.easy.to.build.crm.enums;

/**
 * Enum representing different payment account sources for statement generation
 * Maps to the account_source field in historical_transactions table
 */
public enum StatementDataSource {

    PROPSK_OLD_ACCOUNT("Propsk Old Account", "PROPSK_OLD_ACCOUNT", "Historical Propsk bank account (pre-PayProp)", "propsk_old"),
    PROPSK_PAYPROP_ACCOUNT("Propsk PayProp Account", "PROPSK_PAYPROP_ACCOUNT", "Current PayProp managed account", "propsk_payprop"),
    PAYPROP_API_SYNC("PayProp API Live Data", "PAYPROP_API_SYNC", "Live PayProp API synced transactions", "api_sync"),
    LOCAL_CRM_MANUAL("Local CRM Manual Entry", "LOCAL_CRM_MANUAL", "Manually entered in CRM system", "manual"),
    CSV_IMPORT("CSV Import", "CSV_IMPORT", "Imported from CSV file", "csv_import"),
    ROBERT_ELLIS("Robert Ellis Historical", "ROBERT_ELLIS", "Robert Ellis management period data", "robert_ellis");

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
     * Check if this data source matches a transaction's account_source field
     * Now uses direct comparison with account_source values
     */
    public boolean matchesAccountSource(String accountSource) {
        if (accountSource == null) {
            return false;
        }

        // Direct match or contains check for flexibility
        return accountSource.equals(this.accountSourceValue) ||
               accountSource.contains(this.accountSourceValue);
    }

    /**
     * Get StatementDataSource from account_source value
     */
    public static StatementDataSource fromAccountSource(String accountSource) {
        if (accountSource == null) {
            return null;
        }

        for (StatementDataSource source : values()) {
            if (source.matchesAccountSource(accountSource)) {
                return source;
            }
        }

        return null;
    }

    /**
     * Check if this is a historical data source
     */
    public boolean isHistorical() {
        return this == PROPSK_OLD_ACCOUNT ||
               this == ROBERT_ELLIS ||
               this == CSV_IMPORT;
    }

    /**
     * Check if this is a live/current data source
     */
    public boolean isLive() {
        return this == PROPSK_PAYPROP_ACCOUNT ||
               this == PAYPROP_API_SYNC ||
               this == LOCAL_CRM_MANUAL;
    }
}
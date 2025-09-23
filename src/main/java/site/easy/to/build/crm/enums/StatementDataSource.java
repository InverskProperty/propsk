package site.easy.to.build.crm.enums;

/**
 * Enum representing different data sources for statement generation
 */
public enum StatementDataSource {

    HISTORICAL_PAYPROP("Historical PayProp Data", "HISTORICAL_PAYPROP", "Data imported from historical PayProp records"),
    HISTORICAL_OLD_BANK("Historical Old Bank Data", "HISTORICAL_OLD_BANK", "Data from old Propsk bank account records"),
    HISTORICAL_ROBERT_ELLIS("Historical Robert Ellis Data", "HISTORICAL_ROBERT_ELLIS", "Data from Robert Ellis management period"),
    LIVE_PAYPROP("Live PayProp Data", "LIVE_PAYPROP", "Current PayProp API data"),
    LOCAL_CRM("Local CRM Data", "LOCAL_CRM", "Data entered directly in the CRM system");

    private final String displayName;
    private final String dataSourceKey;
    private final String description;

    StatementDataSource(String displayName, String dataSourceKey, String description) {
        this.displayName = displayName;
        this.dataSourceKey = dataSourceKey;
        this.description = description;
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

    /**
     * Check if this data source matches a transaction's data source field
     */
    public boolean matchesTransaction(String transactionDataSource) {
        if (transactionDataSource == null) {
            return this == LOCAL_CRM;
        }

        return switch (this) {
            case HISTORICAL_PAYPROP -> transactionDataSource.contains("HISTORICAL_PAYPROP") ||
                                     transactionDataSource.contains("PAYPROP_HISTORICAL");
            case HISTORICAL_OLD_BANK -> transactionDataSource.contains("PROPSK_OLD") ||
                                      transactionDataSource.contains("OLD_BANK");
            case HISTORICAL_ROBERT_ELLIS -> transactionDataSource.contains("ROBERT_ELLIS") ||
                                          transactionDataSource.contains("ELLIS_HISTORICAL");
            case LIVE_PAYPROP -> transactionDataSource.contains("ICDN_ACTUAL") ||
                               transactionDataSource.contains("PAYMENT_INSTRUCTION") ||
                               transactionDataSource.contains("COMMISSION_PAYMENT") ||
                               transactionDataSource.equals("PAYPROP");
            case LOCAL_CRM -> transactionDataSource.contains("LOCAL") ||
                            transactionDataSource.contains("CRM") ||
                            transactionDataSource.equals("MANUAL");
        };
    }
}
package site.easy.to.build.crm.entity;

/**
 * Enum representing the occupancy status of a property.
 * Used to track the property's current state in the letting lifecycle.
 */
public enum OccupancyStatus {
    OCCUPIED("Currently Occupied", "Property has tenants living in it"),
    NOTICE_GIVEN("Notice Given", "Tenant has given notice to vacate"),
    ADVERTISING("Being Advertised", "Property is actively being marketed for new tenants"),
    AVAILABLE("Available Now", "Property is vacant and ready for immediate occupation"),
    MAINTENANCE("Under Maintenance", "Property is undergoing repairs or refurbishment"),
    OFF_MARKET("Off Market", "Property is not available for letting");

    private final String displayName;
    private final String description;

    OccupancyStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return this.name(); // Returns uppercase: "OCCUPIED", "NOTICE_GIVEN", etc.
    }

    /**
     * Convert from string value to enum
     * @param value String representation of status
     * @return OccupancyStatus enum value or OCCUPIED as default
     */
    public static OccupancyStatus fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return OCCUPIED;
        }

        for (OccupancyStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }

        return OCCUPIED; // Default fallback
    }

    /**
     * Check if property is available for new tenants
     * @return true if property can accept new tenant enquiries
     */
    public boolean isAvailableForLetting() {
        return this == ADVERTISING || this == AVAILABLE;
    }

    /**
     * Check if property requires marketing attention
     * @return true if property is approaching vacancy or needs advertising
     */
    public boolean requiresMarketingAttention() {
        return this == NOTICE_GIVEN || this == ADVERTISING;
    }
}

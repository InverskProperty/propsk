package site.easy.to.build.crm.entity;

/**
 * Enum representing the type of lead in the system.
 * Distinguishes between business development leads and property rental enquiries.
 */
public enum LeadType {
    BUSINESS("Business Lead", "General business development lead (sales, partnerships, etc.)"),
    PROPERTY_RENTAL("Property Rental Lead", "Prospective tenant enquiring about a rental property");

    private final String displayName;
    private final String description;

    LeadType(String displayName, String description) {
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
        return this.name(); // Returns uppercase: "BUSINESS", "PROPERTY_RENTAL"
    }

    /**
     * Convert from string value to enum
     * @param value String representation of lead type
     * @return LeadType enum value or BUSINESS as default
     */
    public static LeadType fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BUSINESS;
        }

        for (LeadType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }

        return BUSINESS; // Default fallback
    }

    /**
     * Check if this is a property-related lead
     * @return true if lead type is PROPERTY_RENTAL
     */
    public boolean isPropertyLead() {
        return this == PROPERTY_RENTAL;
    }
}

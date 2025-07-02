package site.easy.to.build.crm.entity;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Account type for PayProp integration
 * Handles database values: 'individual', 'business'
 * Maps to Java enum: INDIVIDUAL, BUSINESS
 */
public enum AccountType {
    INDIVIDUAL("individual", "Individual Account"),
    BUSINESS("business", "Business Account");

    private final String value;
    private final String displayName;

    AccountType(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return value;  // Returns lowercase for database compatibility
    }

    /**
     * Convert database string to enum
     */
    public static AccountType fromValue(String value) {
        if (value == null) return INDIVIDUAL;
        
        for (AccountType type : AccountType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return INDIVIDUAL; // Default fallback
    }

    /**
     * Check if this account type is for PayProp entity creation
     */
    public boolean isPayPropEntity() {
        return true; // Both individual and business are valid PayProp entities
    }

    /**
     * Check if this is an individual account
     */
    public boolean isIndividual() {
        return this == INDIVIDUAL;
    }

    /**
     * Check if this is a business account
     */
    public boolean isBusiness() {
        return this == BUSINESS;
    }
}
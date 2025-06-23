package site.easy.to.build.crm.entity;

/**
 * Enhanced CustomerType enum for property management with PayProp integration
 * Supports both the existing Customer entity and separate Tenant/PropertyOwner entities
 */
public enum CustomerType {
    REGULAR_CUSTOMER("Regular Customer", "customer"),
    PROPERTY_OWNER("Property Owner", "property_owner"), 
    TENANT("Tenant", "tenant"),
    CONTRACTOR("Contractor", "contractor"),
    EMPLOYEE("Employee", "employee"),
    ADMIN("Admin", "admin"),
    SUPER_ADMIN("Super Admin", "super_admin");
    
    private final String displayName;
    private final String value;
    
    CustomerType(String displayName, String value) {
        this.displayName = displayName;
        this.value = value;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getValue() {
        return value;
    }
    
    // PayProp integration helper - only tenants and property owners sync with PayProp
    public boolean isPayPropEntity() {
        return this == TENANT || this == PROPERTY_OWNER;
    }
    
    // Check if this type requires additional entity table
    public boolean hasSeperateEntity() {
        return this == TENANT || this == PROPERTY_OWNER || this == CONTRACTOR;
    }
    
    // Get the corresponding entity class name
    public String getEntityClassName() {
        switch (this) {
            case TENANT:
                return "Tenant";
            case PROPERTY_OWNER:
                return "PropertyOwner";
            case CONTRACTOR:
                return "Contractor";
            default:
                return "Customer";
        }
    }
    
    // Helper for URL routing
    public String getUrlPath() {
        return value.replace('_', '-');
    }
    
    // Parse from string (for URL parameters)
    public static CustomerType fromValue(String value) {
        for (CustomerType type : values()) {
            if (type.value.equals(value) || type.name().equals(value.toUpperCase())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown customer type: " + value);
    }
}
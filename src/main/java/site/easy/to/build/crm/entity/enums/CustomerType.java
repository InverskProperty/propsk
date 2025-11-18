package site.easy.to.build.crm.entity.enums;

/**
 * Types of customers in the CRM system
 * Used to categorize customers and determine their access levels and available features
 */
public enum CustomerType {
    PROPERTY_OWNER("Property Owner", true),
    TENANT("Tenant", true),
    CONTRACTOR("Contractor", false),
    DELEGATED_USER("Delegated User", true),
    MANAGER("Manager", false),
    ADMIN("Admin", false),
    SUPER_ADMIN("Super Admin", false),
    REGULAR_CUSTOMER("Regular Customer", false);

    private final String displayName;
    private final boolean isPayPropEntity;

    CustomerType(String displayName, boolean isPayPropEntity) {
        this.displayName = displayName;
        this.isPayPropEntity = isPayPropEntity;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isPayPropEntity() {
        return isPayPropEntity;
    }
}

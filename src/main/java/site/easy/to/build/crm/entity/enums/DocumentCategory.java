package site.easy.to.build.crm.entity.enums;

/**
 * Categories for document templates used in lettings management
 */
public enum DocumentCategory {
    TENANCY_NOTICE("Tenancy Notice"),
    TENANCY_AGREEMENT("Tenancy Agreement"),
    SAFETY_CERTIFICATE("Safety Certificate"),
    INVENTORY("Inventory"),
    RENT_NOTICE("Rent Notice"),
    DEPOSIT_PROTECTION("Deposit Protection"),
    RIGHT_TO_RENT("Right to Rent"),
    PROPERTY_INSPECTION("Property Inspection"),
    MAINTENANCE_REPORT("Maintenance Report"),
    GENERAL_CORRESPONDENCE("General Correspondence"),
    OTHER("Other");

    private final String displayName;

    DocumentCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

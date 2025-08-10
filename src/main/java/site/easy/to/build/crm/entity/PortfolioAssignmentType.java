// PortfolioAssignmentType.java - Enum for property-portfolio assignment types
package site.easy.to.build.crm.entity;

public enum PortfolioAssignmentType {
    PRIMARY("Primary", "Main portfolio assignment for this property"),
    SECONDARY("Secondary", "Additional portfolio for organization/reporting"),
    TAG("Tag", "Categorization assignment (e.g., 'London Properties', 'Multi-Family')"),
    TEMPORARY("Temporary", "Temporary assignment for specific analysis"),
    ARCHIVED("Archived", "Previous assignment kept for history");
    
    private final String displayName;
    private final String description;
    
    PortfolioAssignmentType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    
    // Business logic helpers
    public boolean isPrimary() { return this == PRIMARY; }
    public boolean isSecondary() { return this == SECONDARY; }
    public boolean isTag() { return this == TAG; }
    public boolean isActive() { return this != ARCHIVED; }
}
package site.easy.to.build.crm.entity;

// PortfolioType.java - Enum for portfolio categorization
public enum PortfolioType {
    GEOGRAPHIC("Geographic", "Organized by location/area"),
    PROPERTY_TYPE("Property Type", "Organized by property characteristics"),
    INVESTMENT_CLASS("Investment Class", "Organized by investment strategy"),
    TENANT_TYPE("Tenant Type", "Organized by tenant demographics"),
    CUSTOM("Custom", "User-defined organization");
    
    private final String displayName;
    private final String description;
    
    PortfolioType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
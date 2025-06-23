package site.easy.to.build.crm.entity;

public enum BlockType {
    BUILDING("Building", "Physical building with multiple units"),
    ESTATE("Estate", "Collection of buildings in same development"),
    STREET("Street", "Properties on same street"),
    AREA("Area", "Properties in same geographic area"),
    COMPLEX("Complex", "Managed complex with shared facilities");
    
    private final String displayName;
    private final String description;
    
    BlockType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
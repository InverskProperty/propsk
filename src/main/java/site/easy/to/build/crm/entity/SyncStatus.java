package site.easy.to.build.crm.entity;

public enum SyncStatus {
    pending("Pending Sync"),
    synced("Successfully Synced"),
    error("Sync Error");
    
    private final String displayName;
    
    SyncStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() { 
        return displayName; 
    }
    
    public String getValue() {
        return this.name();  // Returns lowercase: "pending", "synced", "error"
    }
    
    // Helper method for PayProp compatibility
    public static SyncStatus fromCode(String code) {
        for (SyncStatus status : values()) {
            if (status.name().equalsIgnoreCase(code)) {
                return status;
            }
        }
        return pending; // Default fallback (now lowercase)
    }
}
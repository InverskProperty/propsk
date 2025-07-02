package site.easy.to.build.crm.entity;

public enum SyncStatus {
    PENDING("Pending Sync"),
    SYNCING("Sync in Progress"), 
    SYNCED("Successfully Synced"),
    ERROR("Sync Error"),        // Changed from FAILED to ERROR for PayProp compatibility
    FAILED("Sync Failed"),      // Keep FAILED for backward compatibility
    CONFLICT("Sync Conflict - Manual Resolution Required");
    
    private final String displayName;
    
    SyncStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() { 
        return displayName; 
    }
    
    // Helper method for PayProp compatibility
    public static SyncStatus fromCode(String code) {
        for (SyncStatus status : values()) {
            if (status.name().equalsIgnoreCase(code)) {
                return status;
            }
        }
        return PENDING; // Default fallback
    }
}
package site.easy.to.build.crm.entity;

public enum SyncStatus {
    pending("Pending Sync"),
    syncing("Currently Syncing"),    // ADDED - was missing
    synced("Successfully Synced"),
    failed("Sync Failed"),            // CHANGED from 'error' to 'failed' to match DB
    conflict("Sync Conflict");        // ADDED - was missing
    
    private final String displayName;
    
    SyncStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() { 
        return displayName; 
    }
    
    public String getValue() {
        return this.name();  // Returns lowercase: "pending", "syncing", "synced", "failed", "conflict"
    }
    
    // Helper method for PayProp compatibility
    public static SyncStatus fromCode(String code) {
        if (code == null) {
            return pending;
        }
        
        // Handle legacy 'error' value from old code
        if ("error".equalsIgnoreCase(code)) {
            return failed;
        }
        
        for (SyncStatus status : values()) {
            if (status.name().equalsIgnoreCase(code)) {
                return status;
            }
        }
        return pending; // Default fallback
    }
}
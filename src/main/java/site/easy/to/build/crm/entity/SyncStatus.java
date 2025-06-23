package site.easy.to.build.crm.entity;
public enum SyncStatus {
    PENDING("Pending Sync"),
    SYNCING("Sync in Progress"), 
    SYNCED("Successfully Synced"),
    FAILED("Sync Failed"),
    CONFLICT("Sync Conflict - Manual Resolution Required");
    
    private final String displayName;
    
    SyncStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() { return displayName; }
}

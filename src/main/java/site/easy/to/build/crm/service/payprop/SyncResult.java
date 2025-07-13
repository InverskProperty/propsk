package site.easy.to.build.crm.service.payprop;

import java.util.HashMap;
import java.util.Map;

public class SyncResult {
    private boolean success;
    private String message;
    private Map<String, Object> details;
    private SyncResultType type;
    
    private SyncResult(boolean success, String message, Map<String, Object> details, SyncResultType type) {
        this.success = success;
        this.message = message;
        this.details = details != null ? details : new HashMap<>();
        this.type = type;
    }
    
    public static SyncResult success(String message) {
        return new SyncResult(true, message, null, SyncResultType.SUCCESS);
    }
    
    public static SyncResult success(String message, Map<String, Object> details) {
        return new SyncResult(true, message, details, SyncResultType.SUCCESS);
    }
    
    public static SyncResult failure(String message) {
        return new SyncResult(false, message, null, SyncResultType.FAILURE);
    }
    
    public static SyncResult failure(String message, Map<String, Object> details) {
        return new SyncResult(false, message, details, SyncResultType.FAILURE);
    }
    
    public static SyncResult partial(String message, Map<String, Object> details) {
        return new SyncResult(true, message, details, SyncResultType.PARTIAL); // Note: success=true for partial
    }
    
    // Getters
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Map<String, Object> getDetails() { return details; }
    public SyncResultType getType() { return type; }
    
    // Helper methods for backwards compatibility
    public boolean isFailure() { return !success; }
    public boolean isPartial() { return type == SyncResultType.PARTIAL; }
}
package site.easy.to.build.crm.service.customer;

/**
 * Result class for migration operations
 */
public class BridgeMigrationResult {
    private int successCount = 0;
    private int errorCount = 0;
    private final StringBuilder log = new StringBuilder();

    public void addSuccess(String entityType, Long entityId, Integer customerId) {
        successCount++;
        log.append(String.format("✓ %s ID %d -> Customer ID %d\n", entityType, entityId, customerId));
    }

    public void addError(String entityType, Long entityId, String error) {
        errorCount++;
        log.append(String.format("✗ %s ID %d: %s\n", entityType, entityId, error));
    }

    public String getSummary() {
        return String.format("Migration completed: %d successes, %d errors\n\n%s", 
                           successCount, errorCount, log.toString());
    }

    public int getSuccessCount() { 
        return successCount; 
    }
    
    public int getErrorCount() { 
        return errorCount; 
    }
}
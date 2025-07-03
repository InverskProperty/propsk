// PayPropSyncException.java
package site.easy.to.build.crm.service.payprop;

public class PayPropSyncException extends RuntimeException {
    private final String entityType;
    private final String entityId;
    private final String payPropId;
    private final String operation;

    public PayPropSyncException(String message, String entityType, String entityId, String operation) {
        super(message);
        this.entityType = entityType;
        this.entityId = entityId;
        this.payPropId = null;
        this.operation = operation;
    }

    public PayPropSyncException(String message, String entityType, String entityId, String payPropId, String operation, Throwable cause) {
        super(message, cause);
        this.entityType = entityType;
        this.entityId = entityId;
        this.payPropId = payPropId;
        this.operation = operation;
    }

    // Getters
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getPayPropId() { return payPropId; }
    public String getOperation() { return operation; }
}
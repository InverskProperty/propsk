// PortfolioSyncLog.java - Entity for tracking PayProp synchronization (FIXED)
package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "portfolio_sync_log")
public class PortfolioSyncLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "portfolio_id")
    private Long portfolioId;
    
    @Column(name = "block_id")
    private Long blockId;
    
    @Column(name = "property_id")
    private Long propertyId;
    
    @Column(name = "sync_type", nullable = false)
    private String syncType; // PORTFOLIO_TO_PAYPROP, PAYPROP_TO_PORTFOLIO, etc.
    
    @Column(name = "operation", nullable = false)
    private String operation; // CREATE, UPDATE, DELETE, TAG_ADD, TAG_REMOVE
    
    @Column(name = "payprop_tag_id", length = 100)
    private String payPropTagId;
    
    @Column(name = "payprop_tag_name")
    private String payPropTagName;
    
    @Column(name = "status", nullable = false)
    private String status; // SUCCESS, FAILED, PENDING, CONFLICT
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    // 🔧 FIXED: Convert Map to String for database storage
    @Column(name = "payload_sent", columnDefinition = "TEXT")
    private String payloadSentJson;
    
    @Column(name = "payload_received", columnDefinition = "TEXT") 
    private String payloadReceivedJson;
    
    @Column(name = "sync_started_at")
    private LocalDateTime syncStartedAt;
    
    @Column(name = "sync_completed_at")
    private LocalDateTime syncCompletedAt;
    
    @Column(name = "initiated_by")
    private Long initiatedBy;
    
    // 🔧 FIXED: ObjectMapper for JSON conversion
    @Transient
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Constructors
    public PortfolioSyncLog() {}
    
    // Getters and setters for basic fields
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getPortfolioId() { return portfolioId; }
    public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }
    
    public Long getBlockId() { return blockId; }
    public void setBlockId(Long blockId) { this.blockId = blockId; }
    
    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }
    
    public String getSyncType() { return syncType; }
    public void setSyncType(String syncType) { this.syncType = syncType; }
    
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    
    public String getPayPropTagId() { return payPropTagId; }
    public void setPayPropTagId(String payPropTagId) { this.payPropTagId = payPropTagId; }
    
    public String getPayPropTagName() { return payPropTagName; }
    public void setPayPropTagName(String payPropTagName) { this.payPropTagName = payPropTagName; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    // 🔧 FIXED: Map conversion methods for payloadSent
    public Map<String, Object> getPayloadSent() {
        if (payloadSentJson == null || payloadSentJson.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(payloadSentJson, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }
    
    public void setPayloadSent(Map<String, Object> payloadSent) {
        if (payloadSent == null) {
            this.payloadSentJson = null;
        } else {
            try {
                this.payloadSentJson = objectMapper.writeValueAsString(payloadSent);
            } catch (JsonProcessingException e) {
                this.payloadSentJson = "{}";
            }
        }
    }
    
    // 🔧 FIXED: Map conversion methods for payloadReceived
    public Map<String, Object> getPayloadReceived() {
        if (payloadReceivedJson == null || payloadReceivedJson.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(payloadReceivedJson, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }
    
    public void setPayloadReceived(Map<String, Object> payloadReceived) {
        if (payloadReceived == null) {
            this.payloadReceivedJson = null;
        } else {
            try {
                this.payloadReceivedJson = objectMapper.writeValueAsString(payloadReceived);
            } catch (JsonProcessingException e) {
                this.payloadReceivedJson = "{}";
            }
        }
    }
    
    public LocalDateTime getSyncStartedAt() { return syncStartedAt; }
    public void setSyncStartedAt(LocalDateTime syncStartedAt) { this.syncStartedAt = syncStartedAt; }
    
    public LocalDateTime getSyncCompletedAt() { return syncCompletedAt; }
    public void setSyncCompletedAt(LocalDateTime syncCompletedAt) { this.syncCompletedAt = syncCompletedAt; }
    
    public Long getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(Long initiatedBy) { this.initiatedBy = initiatedBy; }
}
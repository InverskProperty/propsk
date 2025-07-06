// PayPropWebhookController.java - Handles incoming PayProp tag changes with Duplicate Key Handling
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.service.payprop.PayPropPortfolioSyncService;
import site.easy.to.build.crm.service.payprop.PayPropTagDTO;
import site.easy.to.build.crm.service.payprop.SyncResult;

import java.util.List;
import java.util.Map;

/**
 * PayPropWebhookController - Handles incoming PayProp webhooks for two-way synchronization
 * This enables PayProp tag changes to automatically update local portfolios
 */
@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@RestController
@RequestMapping("/api/payprop/webhook")
public class PayPropWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PayPropWebhookController.class);

    private final PayPropPortfolioSyncService syncService;

    @Autowired
    public PayPropWebhookController(PayPropPortfolioSyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * Handle PayProp tag creation webhook
     */
    @PostMapping("/tag-created")
    public ResponseEntity<Map<String, Object>> handleTagCreated(@RequestBody PayPropTagWebhookPayload payload) {
        try {
            log.info("Received PayProp tag-created webhook for tag: {}", payload.getTagId());
            
            PayPropTagDTO tagData = new PayPropTagDTO();
            tagData.setId(payload.getTagId());
            tagData.setName(payload.getTagName());
            tagData.setDescription(payload.getDescription());
            tagData.setColor(payload.getColor());
            
            SyncResult result = syncService.handlePayPropTagChange(
                payload.getTagId(), 
                "TAG_CREATED", 
                tagData, 
                null
            );
            
            return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage()
            ));
            
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate portfolio detected during tag-created webhook for tag {}, treating as success", payload.getTagId());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Tag processed successfully (duplicate portfolio handled gracefully)",
                "warning", "Portfolio for this tag already exists"
            ));
        } catch (Exception e) {
            log.error("Error processing tag-created webhook for tag {}: {}", payload.getTagId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Handle PayProp tag update webhook
     */
    @PostMapping("/tag-updated")
    public ResponseEntity<Map<String, Object>> handleTagUpdated(@RequestBody PayPropTagWebhookPayload payload) {
        try {
            log.info("Received PayProp tag-updated webhook for tag: {}", payload.getTagId());
            
            PayPropTagDTO tagData = new PayPropTagDTO();
            tagData.setId(payload.getTagId());
            tagData.setName(payload.getTagName());
            tagData.setDescription(payload.getDescription());
            tagData.setColor(payload.getColor());
            
            SyncResult result = syncService.handlePayPropTagChange(
                payload.getTagId(), 
                "TAG_UPDATED", 
                tagData, 
                null
            );
            
            return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage()
            ));
            
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate portfolio detected during tag-updated webhook for tag {}, treating as success", payload.getTagId());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Tag update processed successfully (duplicate portfolio handled gracefully)",
                "warning", "Portfolio for this tag already exists with current data"
            ));
        } catch (Exception e) {
            log.error("Error processing tag-updated webhook for tag {}: {}", payload.getTagId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Handle PayProp tag deletion webhook
     */
    @PostMapping("/tag-deleted")
    public ResponseEntity<Map<String, Object>> handleTagDeleted(@RequestBody PayPropTagWebhookPayload payload) {
        try {
            log.info("Received PayProp tag-deleted webhook for tag: {}", payload.getTagId());
            
            SyncResult result = syncService.handlePayPropTagChange(
                payload.getTagId(), 
                "TAG_DELETED", 
                null, 
                null
            );
            
            return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage()
            ));
            
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate constraint detected during tag-deleted webhook for tag {}, treating as success", payload.getTagId());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Tag deletion processed successfully (duplicate constraints handled gracefully)",
                "warning", "Some duplicate data constraints encountered during deletion"
            ));
        } catch (Exception e) {
            log.error("Error processing tag-deleted webhook for tag {}: {}", payload.getTagId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Handle PayProp tag applied to properties webhook
     */
    @PostMapping("/tag-applied")
    public ResponseEntity<Map<String, Object>> handleTagApplied(@RequestBody PayPropTagApplicationWebhookPayload payload) {
        try {
            log.info("Received PayProp tag-applied webhook for tag {} with {} properties", 
                payload.getTagId(), payload.getPropertyIds() != null ? payload.getPropertyIds().size() : 0);
            
            SyncResult result = syncService.handlePayPropTagChange(
                payload.getTagId(), 
                "TAG_APPLIED", 
                null, 
                payload.getPropertyIds()
            );
            
            return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage()
            ));
            
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate property assignment detected during tag-applied webhook for tag {}, treating as success", payload.getTagId());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Tag application processed successfully (duplicate property assignments handled gracefully)",
                "warning", "Some properties were already assigned to portfolios"
            ));
        } catch (Exception e) {
            log.error("Error processing tag-applied webhook for tag {}: {}", payload.getTagId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Handle PayProp tag removed from properties webhook
     */
    @PostMapping("/tag-removed")
    public ResponseEntity<Map<String, Object>> handleTagRemoved(@RequestBody PayPropTagApplicationWebhookPayload payload) {
        try {
            log.info("Received PayProp tag-removed webhook for tag {} with {} properties", 
                payload.getTagId(), payload.getPropertyIds() != null ? payload.getPropertyIds().size() : 0);
            
            SyncResult result = syncService.handlePayPropTagChange(
                payload.getTagId(), 
                "TAG_REMOVED", 
                null, 
                payload.getPropertyIds()
            );
            
            return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage()
            ));
            
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate constraint detected during tag-removed webhook for tag {}, treating as success", payload.getTagId());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Tag removal processed successfully (duplicate constraints handled gracefully)",
                "warning", "Some duplicate data constraints encountered during property removal"
            ));
        } catch (Exception e) {
            log.error("Error processing tag-removed webhook for tag {}: {}", payload.getTagId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Webhook verification endpoint (if PayProp requires it)
     */
    @GetMapping("/verify")
    public ResponseEntity<String> verifyWebhook(@RequestParam(required = false) String challenge) {
        log.info("Received webhook verification request with challenge: {}", challenge);
        // Return the challenge if provided (common webhook verification pattern)
        return ResponseEntity.ok(challenge != null ? challenge : "OK");
    }

    /**
     * Health check endpoint for webhook monitoring
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "service", "PayProp Webhook Controller",
            "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Generic webhook handler for unknown webhook types (defensive programming)
     */
    @PostMapping("/**")
    public ResponseEntity<Map<String, Object>> handleUnknownWebhook(@RequestBody(required = false) Object payload, 
                                                                    @RequestParam Map<String, String> params) {
        try {
            log.info("Received unknown PayProp webhook with payload type: {}", 
                payload != null ? payload.getClass().getSimpleName() : "null");
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Webhook received but not processed (unknown type)",
                "note", "This webhook type is not currently supported"
            ));
            
        } catch (Exception e) {
            log.error("Error processing unknown webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Error processing webhook: " + e.getMessage()));
        }
    }

    // ===== WEBHOOK PAYLOAD CLASSES =====

    public static class PayPropTagWebhookPayload {
        private String tagId;
        private String tagName;
        private String description;
        private String color;
        private String action;
        private String timestamp;
        
        // Getters and setters
        public String getTagId() { return tagId; }
        public void setTagId(String tagId) { this.tagId = tagId; }
        
        public String getTagName() { return tagName; }
        public void setTagName(String tagName) { this.tagName = tagName; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        
        @Override
        public String toString() {
            return "PayPropTagWebhookPayload{" +
                "tagId='" + tagId + '\'' +
                ", tagName='" + tagName + '\'' +
                ", action='" + action + '\'' +
                ", timestamp='" + timestamp + '\'' +
                '}';
        }
    }

    public static class PayPropTagApplicationWebhookPayload {
        private String tagId;
        private List<String> propertyIds;
        private String action;
        private String timestamp;
        
        // Getters and setters
        public String getTagId() { return tagId; }
        public void setTagId(String tagId) { this.tagId = tagId; }
        
        public List<String> getPropertyIds() { return propertyIds; }
        public void setPropertyIds(List<String> propertyIds) { this.propertyIds = propertyIds; }
        
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        
        @Override
        public String toString() {
            return "PayPropTagApplicationWebhookPayload{" +
                "tagId='" + tagId + '\'' +
                ", propertyIds=" + (propertyIds != null ? propertyIds.size() + " properties" : "null") +
                ", action='" + action + '\'' +
                ", timestamp='" + timestamp + '\'' +
                '}';
        }
    }
}
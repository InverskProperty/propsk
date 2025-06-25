// PayPropWebhookController.java - Handles incoming PayProp tag changes
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
            
        } catch (Exception e) {
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
            
        } catch (Exception e) {
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
            
        } catch (Exception e) {
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
            
        } catch (Exception e) {
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
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Webhook verification endpoint (if PayProp requires it)
     */
    @GetMapping("/verify")
    public ResponseEntity<String> verifyWebhook(@RequestParam(required = false) String challenge) {
        // Return the challenge if provided (common webhook verification pattern)
        return ResponseEntity.ok(challenge != null ? challenge : "OK");
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
    }
}
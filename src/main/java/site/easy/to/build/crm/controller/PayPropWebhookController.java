// PayPropWebhookController.java - Complete webhook controller with all handlers
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.BatchPaymentRepository;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.email.EmailService;
import site.easy.to.build.crm.service.payprop.PayPropPortfolioSyncService;
import site.easy.to.build.crm.service.payprop.PayPropTagDTO;
import site.easy.to.build.crm.service.payprop.SyncResult;
import site.easy.to.build.crm.service.ticket.TicketService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.service.property.PropertyService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PayPropWebhookController - Handles all incoming PayProp webhooks
 * Includes tag sync, maintenance tickets, batch payments, and discovery mode
 */
@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@RestController
@RequestMapping("/api/payprop/webhook")
public class PayPropWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PayPropWebhookController.class);

    private final PayPropPortfolioSyncService syncService;
    private final TicketService ticketService;
    private final CustomerService customerService;
    private final UserService userService;
    private final EmailService emailService;
    private final PropertyService propertyService;
    private final BatchPaymentRepository batchPaymentRepository;

    @Autowired
    public PayPropWebhookController(PayPropPortfolioSyncService syncService,
                                   TicketService ticketService,
                                   CustomerService customerService,
                                   UserService userService,
                                   EmailService emailService,
                                   PropertyService propertyService,
                                   BatchPaymentRepository batchPaymentRepository) {
        this.syncService = syncService;
        this.ticketService = ticketService;
        this.customerService = customerService;
        this.userService = userService;
        this.emailService = emailService;
        this.propertyService = propertyService;
        this.batchPaymentRepository = batchPaymentRepository;
    }

    // ===== DISCOVERY MODE WEBHOOK HANDLER =====

    /**
     * Generic webhook handler that logs the complete payload structure
     * Use this endpoint to discover undocumented webhook fields
     */
    @PostMapping("/discovery")
    public ResponseEntity<Map<String, Object>> handleDiscoveryWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader Map<String, String> headers) {
        
        log.info("=== PAYPROP WEBHOOK DISCOVERY ===");
        log.info("Headers: {}", headers);
        log.info("Full Payload: {}", payload);
        
        // Extract agency info if present
        Map<String, Object> agency = (Map<String, Object>) payload.get("agency");
        if (agency != null) {
            log.info("Agency: id={}, name={}", agency.get("id"), agency.get("name"));
        }
        
        // Extract events
        List<Map<String, Object>> events = (List<Map<String, Object>>) payload.get("events");
        if (events != null) {
            log.info("Number of events: {}", events.size());
            
            for (int i = 0; i < events.size(); i++) {
                Map<String, Object> event = events.get(i);
                String type = (String) event.get("type");
                String action = (String) event.get("action");
                Map<String, Object> data = (Map<String, Object>) event.get("data");
                
                log.info("Event[{}]: type={}, action={}", i, type, action);
                log.info("Event[{}] data fields: {}", i, data != null ? data.keySet() : "null");
                
                if (data != null) {
                    logDataStructure(type, data);
                }
            }
        }
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Webhook logged for discovery - check logs for structure"
        ));
    }

    /**
     * Log data structure for discovery
     */
    private void logDataStructure(String type, Map<String, Object> data) {
        log.info("=== {} DATA STRUCTURE ===", type.toUpperCase());
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String valueType = value != null ? value.getClass().getSimpleName() : "null";
            
            if (value instanceof Map) {
                Map<String, Object> nested = (Map<String, Object>) value;
                log.info("  {} [{}]: {} fields: {}", key, valueType, nested.size(), nested.keySet());
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                log.info("  {} [{}]: {} items", key, valueType, list.size());
            } else {
                log.info("  {} [{}]: {}", key, valueType, value);
            }
        }
        
        log.info("=== END {} DATA ===", type.toUpperCase());
    }

    // ===== TAG WEBHOOK HANDLERS =====

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

    // ===== MAINTENANCE TICKET WEBHOOK HANDLERS =====

    @PostMapping("/maintenance-ticket-created")
    public ResponseEntity<Map<String, Object>> handleMaintenanceTicketCreated(@RequestBody Map<String, Object> webhookData) {
        try {
            log.info("Received PayProp maintenance-ticket-created webhook");
            
            List<Map<String, Object>> events = (List<Map<String, Object>>) webhookData.get("events");
            if (events == null || events.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "No events in webhook data"));
            }
            
            for (Map<String, Object> event : events) {
                if ("maintenance_ticket".equals(event.get("type")) && "create".equals(event.get("action"))) {
                    Map<String, Object> ticketData = (Map<String, Object>) event.get("data");
                    
                    if (ticketData != null) {
                        Ticket ticket = createTicketFromPayPropData(ticketData);
                        sendMaintenanceTicketAlerts(ticket);
                        
                        return ResponseEntity.ok(Map.of(
                            "success", true,
                            "message", "Maintenance ticket created successfully",
                            "ticketId", ticket.getTicketId()
                        ));
                    }
                }
            }
            
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", "No valid maintenance ticket data found"));
            
        } catch (Exception e) {
            log.error("Error processing maintenance-ticket-created webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/maintenance-ticket-updated")
    public ResponseEntity<Map<String, Object>> handleMaintenanceTicketUpdated(@RequestBody Map<String, Object> webhookData) {
        try {
            log.info("Received PayProp maintenance-ticket-updated webhook");
            
            List<Map<String, Object>> events = (List<Map<String, Object>>) webhookData.get("events");
            if (events == null || events.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "No events in webhook data"));
            }
            
            for (Map<String, Object> event : events) {
                if ("maintenance_ticket".equals(event.get("type")) && "update".equals(event.get("action"))) {
                    Map<String, Object> ticketData = (Map<String, Object>) event.get("data");
                    
                    if (ticketData != null) {
                        updateTicketFromPayPropData(ticketData);
                        
                        return ResponseEntity.ok(Map.of(
                            "success", true,
                            "message", "Maintenance ticket updated successfully"
                        ));
                    }
                }
            }
            
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", "No valid maintenance ticket data found"));
            
        } catch (Exception e) {
            log.error("Error processing maintenance-ticket-updated webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/maintenance-message")
    public ResponseEntity<Map<String, Object>> handleMaintenanceMessage(@RequestBody Map<String, Object> webhookData) {
        try {
            log.info("Received PayProp maintenance-message webhook");
            
            List<Map<String, Object>> events = (List<Map<String, Object>>) webhookData.get("events");
            if (events == null || events.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "No events in webhook data"));
            }
            
            for (Map<String, Object> event : events) {
                if ("maintenance_message".equals(event.get("type"))) {
                    Map<String, Object> messageData = (Map<String, Object>) event.get("data");
                    
                    if (messageData != null) {
                        processMaintenanceMessage(messageData);
                        
                        return ResponseEntity.ok(Map.of(
                            "success", true,
                            "message", "Maintenance message processed successfully"
                        ));
                    }
                }
            }
            
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", "No valid maintenance message data found"));
            
        } catch (Exception e) {
            log.error("Error processing maintenance-message webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ===== BATCH PAYMENT WEBHOOK HANDLERS =====

    /**
     * Handle PayProp outgoing payment batch webhook
     * This webhook is triggered when a batch of payments is processed
     */
    @PostMapping("/outgoing-payment-batch")
    public ResponseEntity<Map<String, Object>> handleOutgoingPaymentBatch(@RequestBody Map<String, Object> webhookData) {
        try {
            log.info("Received PayProp outgoing-payment-batch webhook");
            log.info("Full webhook data: {}", webhookData);
            
            List<Map<String, Object>> events = (List<Map<String, Object>>) webhookData.get("events");
            if (events == null || events.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "No events in webhook data"));
            }
            
            for (Map<String, Object> event : events) {
                if ("outgoing_payment_batch".equals(event.get("type"))) {
                    String action = (String) event.get("action");
                    Map<String, Object> batchData = (Map<String, Object>) event.get("data");
                    
                    if (batchData != null) {
                        String batchId = (String) batchData.get("id");
                        log.info("Processing payment batch: ID={}, Action={}", batchId, action);
                        log.info("Batch data fields: {}", batchData.keySet());
                        
                        // Process and store batch information
                        BatchPayment batchPayment = processBatchPaymentWebhook(batchId, action, batchData);
                        
                        return ResponseEntity.ok(Map.of(
                            "success", true,
                            "message", "Batch payment processed successfully",
                            "batchId", batchId,
                            "action", action
                        ));
                    }
                }
            }
            
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", "No valid batch payment data found"));
            
        } catch (Exception e) {
            log.error("Error processing outgoing-payment-batch webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Handle PayProp payment created webhook
     * Individual payments might include batch ID reference
     */
    @PostMapping("/payment-created")
    public ResponseEntity<Map<String, Object>> handlePaymentCreated(@RequestBody Map<String, Object> webhookData) {
        try {
            log.info("Received PayProp payment-created webhook");
            
            List<Map<String, Object>> events = (List<Map<String, Object>>) webhookData.get("events");
            if (events == null || events.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "No events in webhook data"));
            }
            
            List<String> processedPayments = new ArrayList<>();
            
            for (Map<String, Object> event : events) {
                if ("payment".equals(event.get("type")) && "create".equals(event.get("action"))) {
                    Map<String, Object> paymentData = (Map<String, Object>) event.get("data");
                    
                    if (paymentData != null) {
                        String paymentId = (String) paymentData.get("id");
                        String batchId = extractBatchId(paymentData);
                        
                        log.info("Processing payment: ID={}, BatchID={}", paymentId, batchId);
                        
                        // Store payment information
                        processPaymentNotification(paymentData);
                        processedPayments.add(paymentId);
                    }
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Payments processed successfully",
                "paymentIds", processedPayments
            ));
            
        } catch (Exception e) {
            log.error("Error processing payment-created webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Handle PayProp payment updated webhook
     */
    @PostMapping("/payment-updated")
    public ResponseEntity<Map<String, Object>> handlePaymentUpdated(@RequestBody Map<String, Object> webhookData) {
        try {
            log.info("Received PayProp payment-updated webhook");
            
            List<Map<String, Object>> events = (List<Map<String, Object>>) webhookData.get("events");
            if (events == null || events.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "No events in webhook data"));
            }
            
            List<String> updatedPayments = new ArrayList<>();
            
            for (Map<String, Object> event : events) {
                if ("payment".equals(event.get("type")) && "update".equals(event.get("action"))) {
                    Map<String, Object> paymentData = (Map<String, Object>) event.get("data");
                    
                    if (paymentData != null) {
                        String paymentId = (String) paymentData.get("id");
                        String batchId = extractBatchId(paymentData);
                        String status = (String) paymentData.get("status");
                        
                        log.info("Updating payment: ID={}, BatchID={}, Status={}", paymentId, batchId, status);
                        
                        // Update payment information
                        updatePaymentNotification(paymentData);
                        updatedPayments.add(paymentId);
                    }
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Payments updated successfully",
                "paymentIds", updatedPayments
            ));
            
        } catch (Exception e) {
            log.error("Error processing payment-updated webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Handle payment instruction webhooks (these become batch payments)
     */
    @PostMapping("/payment-instruction")
    public ResponseEntity<Map<String, Object>> handlePaymentInstruction(@RequestBody Map<String, Object> webhookData) {
        try {
            log.info("Received PayProp payment-instruction webhook");
            
            List<Map<String, Object>> events = (List<Map<String, Object>>) webhookData.get("events");
            if (events == null || events.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "No events in webhook data"));
            }
            
            for (Map<String, Object> event : events) {
                if ("payment_instruction".equals(event.get("type"))) {
                    String action = (String) event.get("action");
                    Map<String, Object> instructionData = (Map<String, Object>) event.get("data");
                    
                    log.info("Payment instruction action: {}", action);
                    log.info("Instruction data fields: {}", instructionData != null ? instructionData.keySet() : "null");
                    
                    // Log for discovery - payment instructions may become batched payments
                    if (instructionData != null) {
                        logDataStructure("payment_instruction", instructionData);
                    }
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Payment instruction webhook processed"
            ));
            
        } catch (Exception e) {
            log.error("Error processing payment-instruction webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ===== UTILITY ENDPOINTS =====

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
            "timestamp", System.currentTimeMillis(),
            "handlers", List.of(
                "tag-created", "tag-updated", "tag-deleted", "tag-applied", "tag-removed",
                "maintenance-ticket-created", "maintenance-ticket-updated", "maintenance-message",
                "outgoing-payment-batch", "payment-created", "payment-updated", "payment-instruction",
                "discovery"
            )
        ));
    }

    @GetMapping("/batch-payments")
    public ResponseEntity<Map<String, Object>> listBatchPayments(
            @RequestParam(required = false) Integer limit) {
        
        if (limit == null) limit = 10;
        
        try {
            List<BatchPayment> recentBatches = batchPaymentRepository.findAll().stream()
                .sorted((a, b) -> {
                    LocalDateTime aTime = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();
                    LocalDateTime bTime = b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getCreatedAt();
                    return bTime.compareTo(aTime);
                })
                .limit(limit)
                .collect(Collectors.toList());
            
            List<Map<String, Object>> batchSummaries = recentBatches.stream()
                .map(batch -> {
                    Map<String, Object> summary = new HashMap<>();
                    summary.put("id", batch.getId());
                    summary.put("payPropBatchId", batch.getPayPropBatchId());
                    summary.put("status", batch.getStatus());
                    summary.put("totalAmount", batch.getTotalAmount());
                    summary.put("recordCount", batch.getRecordCount());
                    summary.put("batchDate", batch.getBatchDate());
                    summary.put("lastWebhook", batch.getPayPropWebhookReceived());
                    return summary;
                })
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", batchSummaries.size(),
                "batches", batchSummaries
            ));

        } catch (Exception e) {
            log.error("Error listing batch payments: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Generic webhook handler for unknown webhook types (defensive programming)
     */
    @PostMapping("/**")
    public ResponseEntity<Map<String, Object>> handleUnknownWebhook(
            @RequestBody(required = false) Map<String, Object> payload,
            @RequestParam Map<String, String> params,
            @RequestHeader Map<String, String> headers) {
        
        try {
            String path = headers.get("x-original-uri");
            log.info("Received unknown PayProp webhook at path: {}", path);
            log.info("Payload type: {}", payload != null ? payload.getClass().getSimpleName() : "null");
            
            // Log for discovery
            if (payload != null) {
                log.info("Unknown webhook payload: {}", payload);
                
                // Try to extract event type
                List<Map<String, Object>> events = (List<Map<String, Object>>) payload.get("events");
                if (events != null && !events.isEmpty()) {
                    Set<String> eventTypes = events.stream()
                        .map(e -> (String) e.get("type"))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                    
                    log.info("Event types in unknown webhook: {}", eventTypes);
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Webhook received but not processed (unknown type)",
                "note", "Check logs for webhook details"
            ));
            
        } catch (Exception e) {
            log.error("Error processing unknown webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Error processing webhook: " + e.getMessage()));
        }
    }

    private BatchPayment processBatchPaymentWebhook(String batchId, String action, Map<String, Object> data) {
        // Find existing or create new
        BatchPayment batchPayment = batchPaymentRepository.findByPayPropBatchId(batchId)
            .orElseGet(() -> {
                BatchPayment newBatch = new BatchPayment();
                newBatch.setPayPropBatchId(batchId);
                newBatch.setCreatedAt(LocalDateTime.now());
                log.info("Creating new batch payment record for ID: {}", batchId);
                return newBatch;
            });
        
        if (batchPayment.getId() != null) {
            log.info("Updating existing batch payment record for ID: {}", batchId);
        }
        
        // Update with whatever fields PayProp sends
        // Status
        String status = extractString(data, "status", "batch_status", "state");
        if (status != null) {
            batchPayment.setStatus(status.toUpperCase());
        }
        
        // Total amount
        BigDecimal totalAmount = extractBigDecimal(data, "total_amount", "amount", "total");
        if (totalAmount != null) {
            batchPayment.setTotalAmount(totalAmount);
        }
        
        // Financial breakdown
        BigDecimal totalIn = extractBigDecimal(data, "total_in", "inflow", "credits");
        if (totalIn != null) {
            batchPayment.setTotalIn(totalIn);
        }
        
        BigDecimal totalOut = extractBigDecimal(data, "total_out", "outflow", "debits");
        if (totalOut != null) {
            batchPayment.setTotalOut(totalOut);
        }
        
        BigDecimal commission = extractBigDecimal(data, "total_commission", "commission", "fees");
        if (commission != null) {
            batchPayment.setTotalCommission(commission);
        }
        
        // Dates - try various field names
        LocalDate batchDate = extractDate(data, "batch_date", "date", "processing_date");
        if (batchDate != null) {
            batchPayment.setBatchDate(batchDate);
        }
        
        LocalDateTime processedDate = extractDateTime(data, "processed_date", "processed_at", "completed_at");
        if (processedDate != null) {
            batchPayment.setProcessedDate(processedDate);
        }
        
        // Counts
        Integer recordCount = extractInteger(data, "record_count", "payment_count", "count", "total_payments");
        if (recordCount != null) {
            batchPayment.setRecordCount(recordCount);
        }
        
        // Description
        String description = extractString(data, "description", "notes", "comment");
        if (description != null) {
            batchPayment.setDescription(description);
        }
        
        // Store raw webhook data as JSON string for debugging
        try {
            String rawData = data.toString();
            if (rawData.length() > 255) {
                rawData = rawData.substring(0, 252) + "...";
            }
            batchPayment.setDescription("Webhook " + action + ": " + rawData);
        } catch (Exception e) {
            log.warn("Could not store raw webhook data: {}", e.getMessage());
        }
        
        batchPayment.setPayPropWebhookReceived(LocalDateTime.now());
        batchPayment.setUpdatedAt(LocalDateTime.now());
        
        BatchPayment saved = batchPaymentRepository.save(batchPayment);
        log.info("Batch payment saved: ID={}, Status={}, Amount={}", 
            saved.getPayPropBatchId(), saved.getStatus(), saved.getTotalAmount());
        
        return saved;
    }

    /**
     * Extract batch ID from payment data (try multiple field names)
     */
    private String extractBatchId(Map<String, Object> paymentData) {
        // Try different possible field names
        String[] possibleFields = {"batch_id", "payment_batch_id", "batch", "batch_reference"};
        
        for (String field : possibleFields) {
            Object value = paymentData.get(field);
            if (value != null && !value.toString().isEmpty()) {
                return value.toString();
            }
        }
        
        return null;
    }

    /**
     * Extract BigDecimal from data, trying multiple field names
     */
    private BigDecimal extractBigDecimal(Map<String, Object> data, String... fieldNames) {
        for (String field : fieldNames) {
            Object value = data.get(field);
            if (value != null) {
                try {
                    if (value instanceof Number) {
                        return new BigDecimal(value.toString());
                    } else if (value instanceof String) {
                        // Remove currency symbols and commas
                        String cleanValue = value.toString().replaceAll("[^0-9.-]", "");
                        return new BigDecimal(cleanValue);
                    }
                } catch (Exception e) {
                    log.warn("Could not parse BigDecimal from field {}: {}", field, value);
                }
            }
        }
        return null;
    }

    /**
     * Extract LocalDate from data, trying multiple field names
     */
    private LocalDate extractDate(Map<String, Object> data, String... fieldNames) {
        for (String field : fieldNames) {
            Object value = data.get(field);
            if (value != null && value instanceof String) {
                try {
                    return LocalDate.parse((String) value);
                } catch (Exception e) {
                    try {
                        // Try with custom formatter if needed
                        return LocalDate.parse((String) value, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    } catch (Exception e2) {
                        log.warn("Could not parse LocalDate from field {}: {}", field, value);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extract LocalDateTime from data, trying multiple field names
     */
    private LocalDateTime extractDateTime(Map<String, Object> data, String... fieldNames) {
        for (String field : fieldNames) {
            Object value = data.get(field);
            if (value != null && value instanceof String) {
                try {
                    return LocalDateTime.parse((String) value);
                } catch (Exception e) {
                    try {
                        // Try parsing as date only
                        LocalDate date = LocalDate.parse((String) value);
                        return date.atStartOfDay();
                    } catch (Exception e2) {
                        log.warn("Could not parse LocalDateTime from field {}: {}", field, value);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extract Integer from data, trying multiple field names
     */
    private Integer extractInteger(Map<String, Object> data, String... fieldNames) {
        for (String field : fieldNames) {
            Object value = data.get(field);
            if (value != null) {
                try {
                    if (value instanceof Number) {
                        return ((Number) value).intValue();
                    } else if (value instanceof String) {
                        return Integer.parseInt(value.toString());
                    }
                } catch (Exception e) {
                    log.warn("Could not parse Integer from field {}: {}", field, value);
                }
            }
        }
        return null;
    }

    /**
     * Extract String from data, trying multiple field names
     */
    private String extractString(Map<String, Object> data, String... fieldNames) {
        for (String field : fieldNames) {
            Object value = data.get(field);
            if (value != null && !value.toString().isEmpty()) {
                return value.toString();
            }
        }
        return null;
    }

    /**
     * Process payment notification (store batch ID if present)
     */
    private void processPaymentNotification(Map<String, Object> paymentData) {
        try {
            String paymentId = (String) paymentData.get("id");
            String batchId = extractBatchId(paymentData);
            
            // Log discovered batch relationship
            if (batchId != null) {
                log.info("Payment {} is part of batch {}", paymentId, batchId);
                
                // Ensure batch exists
                BatchPayment batch = batchPaymentRepository.findByPayPropBatchId(batchId).orElse(null);
                if (batch == null) {
                    log.info("Creating batch record for newly discovered batch ID: {}", batchId);
                    batch = new BatchPayment();
                    batch.setPayPropBatchId(batchId);
                    batch.setCreatedAt(LocalDateTime.now());
                    batch.setUpdatedAt(LocalDateTime.now());
                    batch.setDescription("Created from payment webhook");
                    batchPaymentRepository.save(batch);
                }
            }
            
            // TODO: Create/update Payment entity when available
            log.info("Would process payment notification: Payment={}, Batch={}", paymentId, batchId);
            
        } catch (Exception e) {
            log.error("Error processing payment notification: {}", e.getMessage());
        }
    }

    /**
     * Update payment notification
     */
    private void updatePaymentNotification(Map<String, Object> paymentData) {
        try {
            String paymentId = (String) paymentData.get("id");
            String status = (String) paymentData.get("status");
            
            // TODO: Update Payment entity when available
            log.info("Would update payment: Payment={}, Status={}", paymentId, status);
            
        } catch (Exception e) {
            log.error("Error updating payment notification: {}", e.getMessage());
        }
    }

    // ===== MAINTENANCE TICKET HELPER METHODS =====

    private Ticket createTicketFromPayPropData(Map<String, Object> data) {
        String payPropTicketId = (String) data.get("id");
        String payPropPropertyId = (String) data.get("property_id");
        String payPropTenantId = (String) data.get("tenant_id");
        String description = (String) data.get("description");
        String category = (String) data.get("category");
        Boolean isEmergency = (Boolean) data.get("is_emergency");
        String subject = (String) data.get("subject");

        // Check if ticket already exists
        List<Ticket> existingTickets = ticketService.findAll().stream()
            .filter(t -> payPropTicketId.equals(t.getPayPropTicketId()))
            .toList();
        
        if (!existingTickets.isEmpty()) {
            log.info("Ticket with PayProp ID {} already exists", payPropTicketId);
            return existingTickets.get(0);
        }

        // Find customer for this property
        Customer customer = findCustomerForProperty(payPropPropertyId, payPropTenantId);
        if (customer == null) {
            log.warn("No customer found for PayProp property: {}, creating placeholder", payPropPropertyId);
            customer = createPlaceholderCustomer(payPropPropertyId, payPropTenantId);
        }

        // Create new ticket
        Ticket ticket = new Ticket();
        ticket.setSubject(subject != null ? subject : "Maintenance Request - PayProp #" + payPropTicketId);
        ticket.setDescription(description);
        ticket.setType("maintenance");
        ticket.setStatus("open");
        ticket.setPriority(Boolean.TRUE.equals(isEmergency) ? "emergency" : "medium");
        ticket.setUrgencyLevel(Boolean.TRUE.equals(isEmergency) ? "emergency" : "routine");
        
        // PayProp integration fields
        ticket.setPayPropTicketId(payPropTicketId);
        ticket.setPayPropPropertyId(payPropPropertyId);
        ticket.setPayPropTenantId(payPropTenantId);
        ticket.setPayPropCategoryId(category);
        ticket.setPayPropSynced(true);
        ticket.setPayPropLastSync(LocalDateTime.now());
        
        // Set maintenance category
        ticket.setMaintenanceCategory(mapPayPropCategory(category));
        
        // Set customer relationship
        ticket.setCustomer(customer);
        ticket.setCreatedAt(LocalDateTime.now());
        
        // Auto-assign based on emergency status or category
        assignTicketToEmployee(ticket, customer, isEmergency);

        return ticketService.save(ticket);
    }

    private void updateTicketFromPayPropData(Map<String, Object> data) {
        String payPropTicketId = (String) data.get("id");
        String status = (String) data.get("status");
        String description = (String) data.get("description");

        List<Ticket> tickets = ticketService.findAll().stream()
            .filter(t -> payPropTicketId.equals(t.getPayPropTicketId()))
            .toList();
            
        if (tickets.isEmpty()) {
            log.warn("Ticket with PayProp ID {} not found for update", payPropTicketId);
            return;
        }
        
        Ticket ticket = tickets.get(0);

        // Update fields
        if (description != null && !description.equals(ticket.getDescription())) {
            ticket.setDescription(description);
        }

        // Map PayProp status to our status
        if (status != null) {
            String mappedStatus = mapPayPropStatus(status);
            if (!mappedStatus.equals(ticket.getStatus())) {
                ticket.setStatus(mappedStatus);
            }
        }

        ticket.setPayPropLastSync(LocalDateTime.now());
        ticketService.save(ticket);

        log.info("Updated ticket {} from PayProp", ticket.getTicketId());
    }

    private void processMaintenanceMessage(Map<String, Object> data) {
        String payPropTicketId = (String) data.get("maintenance_ticket_id");
        String payPropMessageId = (String) data.get("id");
        String authorType = (String) data.get("author_type"); // tenant, agency
        String message = (String) data.get("message");
        Boolean isPrivate = (Boolean) data.get("is_private");

        List<Ticket> tickets = ticketService.findAll().stream()
            .filter(t -> payPropTicketId.equals(t.getPayPropTicketId()))
            .toList();
            
        if (tickets.isEmpty()) {
            log.warn("Ticket with PayProp ID {} not found for message", payPropTicketId);
            return;
        }
        
        Ticket ticket = tickets.get(0);

        // TODO: Create TicketMessage entity when implemented
        // For now, append to ticket description
        String messagePrefix = "tenant".equals(authorType) ? "[Tenant Message]" : "[PayProp Message]";
        String formattedMessage = String.format("\n\n%s %s: %s", 
            messagePrefix, LocalDateTime.now(), message);
        
        ticket.setDescription(ticket.getDescription() + formattedMessage);
        ticketService.save(ticket);

        log.info("Added message to ticket {} from PayProp", ticket.getTicketId());
    }

    private Customer findCustomerForProperty(String payPropPropertyId, String payPropTenantId) {
        // Try to find customer by PayProp entity ID (property or tenant)
        if (payPropTenantId != null) {
            List<Customer> tenantCustomers = customerService.findAll().stream()
                .filter(c -> payPropTenantId.equals(c.getPayPropEntityId()))
                .filter(c -> Boolean.TRUE.equals(c.getIsTenant()))
                .toList();
            if (!tenantCustomers.isEmpty()) {
                return tenantCustomers.get(0);
            }
        }
        
        if (payPropPropertyId != null) {
            List<Customer> propertyCustomers = customerService.findAll().stream()
                .filter(c -> payPropPropertyId.equals(c.getPayPropEntityId()))
                .filter(c -> Boolean.TRUE.equals(c.getIsPropertyOwner()))
                .toList();
            if (!propertyCustomers.isEmpty()) {
                return propertyCustomers.get(0);
            }
        }
        
        return null;
    }

    private Customer createPlaceholderCustomer(String payPropPropertyId, String payPropTenantId) {
        // Create a placeholder customer for unknown properties
        Customer customer = new Customer();
        customer.setName("PayProp Property " + payPropPropertyId);
        customer.setEmail("noreply+" + payPropPropertyId + "@yourcompany.com");
        customer.setCustomerType(CustomerType.PROPERTY_OWNER);
        customer.setIsPropertyOwner(true);
        customer.setPayPropEntityId(payPropPropertyId);
        customer.setDescription("Auto-created from PayProp maintenance request");
        customer.setCreatedAt(LocalDateTime.now());
        
        // Set a default user (you'll need to implement this based on your system)
        List<User> users = userService.findAll();
        if (!users.isEmpty()) {
            customer.setUser(users.get(0)); // Assign to first available user
        }
        
        return customerService.save(customer);
    }

    private void assignTicketToEmployee(Ticket ticket, Customer customer, Boolean isEmergency) {
        User assignedUser = null;
        
        // Assignment logic based on:
        // 1. Emergency status
        // 2. Customer's assigned account manager
        // 3. Default assignment
        
        if (Boolean.TRUE.equals(isEmergency)) {
            // Try to find manager for emergency handling
            List<User> managers = userService.findAll().stream()
                .filter(u -> u.getRoles() != null && !u.getRoles().isEmpty() && 
                            u.getRoles().stream().anyMatch(role -> 
                                role != null && "ROLE_MANAGER".equals(role.getName())))
                .toList();
            if (!managers.isEmpty()) {
                assignedUser = managers.get(0);
            }
        }
        
        // Assign to customer's user if available
        if (assignedUser == null && customer.getUser() != null) {
            assignedUser = customer.getUser();
        }
        
        // Fallback to first available user
        if (assignedUser == null) {
            List<User> allUsers = userService.findAll();
            if (!allUsers.isEmpty()) {
                assignedUser = allUsers.get(0);
            }
        }
        
        if (assignedUser != null) {
            ticket.setEmployee(assignedUser);
            ticket.setManager(assignedUser);
        }
    }

    private void sendMaintenanceTicketAlerts(Ticket ticket) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            
            // Alert property owner
            Customer propertyOwner = findPropertyOwnerForTicket(ticket);
            if (propertyOwner != null) {
                emailService.sendMaintenanceTicketAlert(propertyOwner, ticket, auth);
            }
            
            // Alert assigned employee if they have email
            if (ticket.getEmployee() != null) {
                // Create a customer record for the employee to send email
                Customer employeeAsCustomer = createCustomerForUser(ticket.getEmployee());
                if (employeeAsCustomer != null) {
                    emailService.sendNotificationEmail(
                        employeeAsCustomer,
                        "New Maintenance Ticket Assigned - #" + ticket.getTicketId(),
                        String.format("A new maintenance ticket has been assigned to you.\n\n" +
                                    "Ticket #: %d\nDescription: %s\nPriority: %s\n\n" +
                                    "Please review and take appropriate action.",
                                    ticket.getTicketId(), ticket.getDescription(), ticket.getPriorityDisplayName()),
                        auth
                    );
                }
            }
            
        } catch (Exception e) {
            log.error("Error sending maintenance ticket alerts: {}", e.getMessage());
        }
    }

    private Customer findPropertyOwnerForTicket(Ticket ticket) {
        try {
            // First, try to find property by PayProp property ID
            if (ticket.getPayPropPropertyId() != null) {
                Optional<Property> propertyOpt = propertyService.findByPayPropId(ticket.getPayPropPropertyId());
                if (propertyOpt.isPresent()) {
                    Property property = propertyOpt.get();
                    
                    // Method 1: Use direct property owner ID if available
                    if (property.getPropertyOwnerId() != null) {
                        Customer owner = customerService.findByCustomerId(property.getPropertyOwnerId());
                        if (owner != null && Boolean.TRUE.equals(owner.getIsPropertyOwner())) {
                            return owner;
                        }
                    }
                    
                    // Method 2: Use junction table to find property owner
                    List<Customer> owners = customerService.findByEntityTypeAndEntityId("Property", property.getId());
                    Optional<Customer> propertyOwner = owners.stream()
                        .filter(customer -> Boolean.TRUE.equals(customer.getIsPropertyOwner()))
                        .findFirst();
                    if (propertyOwner.isPresent()) {
                        return propertyOwner.get();
                    }
                }
            }
            
            // Method 3: Check if customer associated with ticket is a property owner
            if (ticket.getCustomer() != null && Boolean.TRUE.equals(ticket.getCustomer().getIsPropertyOwner())) {
                return ticket.getCustomer();
            }
            
            // Method 4: Find by PayProp tenant ID -> property -> owner
            if (ticket.getPayPropTenantId() != null) {
                Customer tenant = customerService.findByPayPropEntityId(ticket.getPayPropTenantId());
                if (tenant != null && tenant.getAssignedPropertyId() != null) {
                    // Find property owner for this property
                    List<Customer> owners = customerService.findByEntityTypeAndEntityId("Property", tenant.getAssignedPropertyId());
                    Optional<Customer> propertyOwner = owners.stream()
                        .filter(customer -> Boolean.TRUE.equals(customer.getIsPropertyOwner()))
                        .findFirst();
                    if (propertyOwner.isPresent()) {
                        return propertyOwner.get();
                    }
                }
            }
            
            log.warn("No property owner found for ticket {} with PayProp property ID {}", 
                    ticket.getTicketId(), ticket.getPayPropPropertyId());
            return null;
            
        } catch (Exception e) {
            log.error("Error finding property owner for ticket {}: {}", ticket.getTicketId(), e.getMessage());
            return null;
        }
    }

    private Customer createCustomerForUser(User user) {
        // Create a temporary customer object for email purposes
        // This is for sending emails to employees
        Customer customer = new Customer();
        customer.setName(user.getName());
        customer.setEmail(user.getEmail());
        customer.setCustomerType(CustomerType.REGULAR_CUSTOMER);
        return customer;
    }

    private String mapPayPropCategory(String category) {
        if (category == null) return "general";
        
        switch (category.toLowerCase()) {
            case "plumbing": return "plumbing";
            case "electrical": return "electrical";
            case "heating": return "heating";
            case "appliance": return "appliance";
            case "external": return "external";
            case "internal": return "internal";
            default: return "general";
        }
    }

    private String mapPayPropStatus(String payPropStatus) {
        if (payPropStatus == null) return "open";
        
        switch (payPropStatus.toLowerCase()) {
            case "new": return "open";
            case "in_progress": return "work-in-progress";
            case "on_hold": return "on-hold";
            case "resolved": return "resolved";
            case "rejected": return "closed";
            default: return "open";
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
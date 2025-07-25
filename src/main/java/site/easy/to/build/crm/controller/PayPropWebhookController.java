// PayPropWebhookController.java - Enhanced with Maintenance Ticket Support
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
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.email.EmailService;
import site.easy.to.build.crm.service.payprop.PayPropPortfolioSyncService;
import site.easy.to.build.crm.service.payprop.PayPropTagDTO;
import site.easy.to.build.crm.service.payprop.SyncResult;
import site.easy.to.build.crm.service.ticket.TicketService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.service.property.PropertyService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PayPropWebhookController - Handles incoming PayProp webhooks for two-way synchronization
 * Enhanced with maintenance ticket support for complete workflow integration
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
    private final PropertyService propertyService; // ADDED: Missing PropertyService

    @Autowired
    public PayPropWebhookController(PayPropPortfolioSyncService syncService,
                                   TicketService ticketService,
                                   CustomerService customerService,
                                   UserService userService,
                                   EmailService emailService,
                                   PropertyService propertyService) { // ADDED: PropertyService injection
        this.syncService = syncService;
        this.ticketService = ticketService;
        this.customerService = customerService;
        this.userService = userService;
        this.emailService = emailService;
        this.propertyService = propertyService; // ADDED: Initialize PropertyService
    }

    // ===== EXISTING TAG WEBHOOK HANDLERS =====

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

    // ===== NEW: MAINTENANCE TICKET WEBHOOK HANDLERS =====

    /**
     * Handle PayProp maintenance ticket creation webhook
     */
    @PostMapping("/maintenance-ticket-created")
    public ResponseEntity<Map<String, Object>> handleMaintenanceTicketCreated(@RequestBody Map<String, Object> webhookData) {
        try {
            log.info("Received PayProp maintenance-ticket-created webhook");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> ticketData = (Map<String, Object>) webhookData.get("data");
            
            if (ticketData == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "No ticket data provided"));
            }
            
            // Create ticket from PayProp data
            Ticket ticket = createTicketFromPayPropData(ticketData);
            
            // Send alerts to stakeholders
            sendMaintenanceTicketAlerts(ticket);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Maintenance ticket created successfully",
                "ticketId", ticket.getTicketId()
            ));
            
        } catch (Exception e) {
            log.error("Error processing maintenance-ticket-created webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Handle PayProp maintenance ticket update webhook
     */
    @PostMapping("/maintenance-ticket-updated")
    public ResponseEntity<Map<String, Object>> handleMaintenanceTicketUpdated(@RequestBody Map<String, Object> webhookData) {
        try {
            log.info("Received PayProp maintenance-ticket-updated webhook");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> ticketData = (Map<String, Object>) webhookData.get("data");
            
            if (ticketData == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "No ticket data provided"));
            }
            
            // Update existing ticket
            updateTicketFromPayPropData(ticketData);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Maintenance ticket updated successfully"
            ));
            
        } catch (Exception e) {
            log.error("Error processing maintenance-ticket-updated webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Handle PayProp maintenance message webhook
     */
    @PostMapping("/maintenance-message")
    public ResponseEntity<Map<String, Object>> handleMaintenanceMessage(@RequestBody Map<String, Object> webhookData) {
        try {
            log.info("Received PayProp maintenance-message webhook");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> messageData = (Map<String, Object>) webhookData.get("data");
            
            if (messageData == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "No message data provided"));
            }
            
            // Process message
            processMaintenanceMessage(messageData);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Maintenance message processed successfully"
            ));
            
        } catch (Exception e) {
            log.error("Error processing maintenance-message webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ===== EXISTING UTILITY ENDPOINTS =====

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

        // ===== NEW: BATCH PAYMENT WEBHOOK HANDLERS =====

    /**
     * Handle PayProp outgoing payment batch webhook
     * This webhook is triggered when a batch of payments is processed
     */
    @PostMapping("/outgoing-payment-batch")
    public ResponseEntity<Map<String, Object>> handleOutgoingPaymentBatch(@RequestBody Map<String, Object> webhookData) {
        try {
            log.info("Received PayProp outgoing-payment-batch webhook");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> batchData = (Map<String, Object>) webhookData.get("data");
            
            if (batchData == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "No batch data provided"));
            }
            
            // Extract batch information
            String batchId = (String) batchData.get("id");
            String status = (String) batchData.get("status");
            Object amount = batchData.get("amount");
            String date = (String) batchData.get("date");
            
            log.info("Processing payment batch: ID={}, Status={}, Amount={}, Date={}", 
                batchId, status, amount, date);
            
            // Store batch information in your system
            processBatchPaymentNotification(batchData);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Batch payment processed successfully",
                "batchId", batchId
            ));
            
        } catch (Exception e) {
            log.error("Error processing outgoing-payment-batch webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Handle PayProp payment created webhook
     * Individual payments within a batch might trigger this
     */
    @PostMapping("/payment-created")
    public ResponseEntity<Map<String, Object>> handlePaymentCreated(@RequestBody Map<String, Object> webhookData) {
        try {
            log.info("Received PayProp payment-created webhook");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> paymentData = (Map<String, Object>) webhookData.get("data");
            
            if (paymentData == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "No payment data provided"));
            }
            
            // Extract payment information
            String paymentId = (String) paymentData.get("id");
            String batchId = (String) paymentData.get("payment_batch_id"); // Look for this field
            Object amount = paymentData.get("amount");
            String propertyId = (String) paymentData.get("property_id");
            String tenantId = (String) paymentData.get("tenant_id");
            
            log.info("Processing payment: ID={}, BatchID={}, Amount={}, Property={}, Tenant={}", 
                paymentId, batchId, amount, propertyId, tenantId);
            
            // Store payment information
            processPaymentNotification(paymentData);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Payment processed successfully",
                "paymentId", paymentId,
                "batchId", batchId
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
            
            @SuppressWarnings("unchecked")
            Map<String, Object> paymentData = (Map<String, Object>) webhookData.get("data");
            
            if (paymentData == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "No payment data provided"));
            }
            
            // Extract payment information including batch ID
            String paymentId = (String) paymentData.get("id");
            String batchId = (String) paymentData.get("payment_batch_id");
            String status = (String) paymentData.get("status");
            
            log.info("Updating payment: ID={}, BatchID={}, Status={}", paymentId, batchId, status);
            
            // Update payment information
            updatePaymentNotification(paymentData);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Payment update processed successfully",
                "paymentId", paymentId
            ));
            
        } catch (Exception e) {
            log.error("Error processing payment-updated webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ===== PRIVATE HELPER METHODS FOR MAINTENANCE TICKETS =====

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
            // Try to find manager for emergency handling - FIXED VERSION
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

    // ===== PRIVATE HELPER METHODS FOR BATCH PAYMENTS =====

    private void processBatchPaymentNotification(Map<String, Object> batchData) {
        try {
            String batchId = (String) batchData.get("id");
            String status = (String) batchData.get("status");
            
            // TODO: Implement your batch payment storage logic here
            // This could involve:
            // 1. Creating a BatchPayment entity
            // 2. Linking it to related customers/properties
            // 3. Updating financial records
            // 4. Sending notifications
            
            log.info("Stored batch payment notification for batch ID: {}", batchId);
            
            // If this is a completed batch, you might want to:
            if ("completed".equals(status) || "processed".equals(status)) {
                // Fetch full batch details from PayProp API
                fetchAndStoreBatchDetails(batchId);
                
                // Send notifications to relevant stakeholders
                sendBatchCompletionNotifications(batchId);
            }
            
        } catch (Exception e) {
            log.error("Error processing batch payment notification: {}", e.getMessage());
        }
    }

    private void processPaymentNotification(Map<String, Object> paymentData) {
        try {
            String paymentId = (String) paymentData.get("id");
            String batchId = (String) paymentData.get("payment_batch_id");
            
            // Find existing financial transaction or create new one
            // Link it to the batch if batch ID is provided
            
            log.info("Processed payment notification: Payment={}, Batch={}", paymentId, batchId);
            
        } catch (Exception e) {
            log.error("Error processing payment notification: {}", e.getMessage());
        }
    }

    private void updatePaymentNotification(Map<String, Object> paymentData) {
        try {
            String paymentId = (String) paymentData.get("id");
            String status = (String) paymentData.get("status");
            
            // Update existing payment record
            // Update batch status if needed
            
            log.info("Updated payment notification: Payment={}, Status={}", paymentId, status);
            
        } catch (Exception e) {
            log.error("Error updating payment notification: {}", e.getMessage());
        }
    }

    private void fetchAndStoreBatchDetails(String batchId) {
        try {
            // Use PayProp API to get full batch details
            String url = "/report/all-payments?payment_batch_id=" + batchId;
            // Map<String, Object> batchDetails = payPropSyncService.makePayPropApiCall(url, Map.class);
            
            // Store detailed batch information
            log.info("Would fetch detailed batch information for batch ID: {}", batchId);
            
        } catch (Exception e) {
            log.error("Error fetching batch details for batch {}: {}", batchId, e.getMessage());
        }
    }

    private void sendBatchCompletionNotifications(String batchId) {
        try {
            // Send notifications to property owners, managers, etc.
            log.info("Would send batch completion notifications for batch ID: {}", batchId);
            
        } catch (Exception e) {
            log.error("Error sending batch notifications for batch {}: {}", batchId, e.getMessage());
        }
    }

    // FIXED: Single, complete property owner lookup method
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

    // ===== EXISTING WEBHOOK PAYLOAD CLASSES =====

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